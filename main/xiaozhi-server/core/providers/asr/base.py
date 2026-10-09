import os
import io
import wave
import uuid
import json
import time
import queue
import shutil
import asyncio
import tempfile
import traceback
import threading

from abc import ABC, abstractmethod
from config.logger import setup_logging
from core.providers.asr.dto.dto import InterfaceType
from core.handle.receiveAudioHandle import startToChat
from core.handle.reportHandle import enqueue_asr_report
from core.utils.util import remove_punctuation_and_length
from core.handle.receiveAudioHandle import handleAudioMessage
from typing import Optional, Tuple, List, NamedTuple, TYPE_CHECKING


if TYPE_CHECKING:
    from core.connection import ConnectionHandler

TAG = __name__
logger = setup_logging()


class ASRProviderBase(ABC):
    def __init__(self):
        pass

    # Open the audio channel
    async def open_audio_channels(self, conn: "ConnectionHandler"):
        conn.asr_priority_thread = threading.Thread(
            target=self.asr_text_priority_thread, args=(conn,), daemon=True
        )
        conn.asr_priority_thread.start()

    # Process ASR audio in order
    def asr_text_priority_thread(self, conn: "ConnectionHandler"):
        while not conn.stop_event.is_set():
            try:
                message = conn.asr_audio_queue.get(timeout=1)
                future = asyncio.run_coroutine_threadsafe(
                    handleAudioMessage(conn, message),
                    conn.loop,
                )
                future.result()
            except queue.Empty:
                continue
            except Exception as e:
                logger.bind(tag=TAG).error(
                    f"Failed to process ASR text: {str(e)}, type: {type(e).__name__}, stack: {traceback.format_exc()}"
                )
                continue

    # Receive audio
    async def receive_audio(self, conn: "ConnectionHandler", pcm_frame, audio_have_voice):
        if conn.client_listen_mode == "manual":
            # Manual mode: buffer audio for ASR recognition
            conn.asr_audio.append(pcm_frame)
        else:
            # Auto/realtime mode: use VAD detection
            conn.asr_audio.append(pcm_frame)

            # If there is no voice now and there was none before, buffer some audio
            if not audio_have_voice and not conn.client_have_voice:
                conn.asr_audio = conn.asr_audio[-10:]
                return

            # In auto mode, trigger recognition when VAD detects that speech has stopped
            if conn.asr.interface_type != InterfaceType.STREAM and conn.client_voice_stop:
                # Use the PCM data in asr_audio directly
                pcm_bytes = b"".join(conn.asr_audio)
                # Check whether there is enough audio data (1920 bytes per frame, 15 frames is about 28800 bytes)
                if len(pcm_bytes) > 1920 * 15:
                    await self.handle_voice_stop(conn, [pcm_bytes])
                conn.reset_audio_states()

    # Handle speech stop
    async def handle_voice_stop(self, conn: "ConnectionHandler", asr_audio_task: List[bytes]):
        """Run ASR and voiceprint recognition in parallel"""
        try:
            total_start_time = time.monotonic()

            # Data is already PCM, use it directly
            pcm_data = asr_audio_task
            combined_pcm_data = b"".join(pcm_data)

            # Prepare WAV data in advance
            wav_data = None
            if conn.voiceprint_provider and combined_pcm_data:
                wav_data = self._pcm_to_wav(combined_pcm_data)

            # Define the ASR task
            asr_task = self.speech_to_text_wrapper(
                asr_audio_task, conn.session_id
            )

            if conn.voiceprint_provider and wav_data:
                voiceprint_task = conn.voiceprint_provider.identify_speaker(
                    wav_data, conn.session_id
                )
                # Wait for both results concurrently
                asr_result, voiceprint_result = await asyncio.gather(
                    asr_task, voiceprint_task, return_exceptions=True
                )
            else:
                asr_result = await asr_task
                voiceprint_result = None

            # Record recognition results - check for exceptions
            if isinstance(asr_result, Exception):
                logger.bind(tag=TAG).error(f"ASR recognition failed: {asr_result}")
                raw_text = ""
            else:
                raw_text, _ = asr_result

            if isinstance(voiceprint_result, Exception):
                logger.bind(tag=TAG).error(f"Voiceprint recognition failed: {voiceprint_result}")
                speaker_name = ""
            else:
                speaker_name = voiceprint_result

            # Determine the ASR result type
            if isinstance(raw_text, dict):
                # FunASR returns a dict
                if speaker_name:
                    raw_text["speaker"] = speaker_name

                # Record recognition results
                if raw_text.get("language"):
                    logger.bind(tag=TAG).info(f"Recognized language: {raw_text['language']}")
                if raw_text.get("emotion"):
                    logger.bind(tag=TAG).info(f"Recognized emotion: {raw_text['emotion']}")
                if raw_text.get("content"):
                    logger.bind(tag=TAG).info(f"Recognized text: {raw_text['content']}")
                if speaker_name:
                    logger.bind(tag=TAG).info(f"Recognized speaker: {speaker_name}")

                # Convert to a JSON string for downstream use
                enhanced_text = json.dumps(raw_text, ensure_ascii=False)
                content_for_length_check = raw_text.get("content", "")
            else:
                # Plain text returned by other ASR providers
                if raw_text:
                    logger.bind(tag=TAG).info(f"Recognized text: {raw_text}")
                if speaker_name:
                    logger.bind(tag=TAG).info(f"Recognized speaker: {speaker_name}")

                # Build a JSON string including speaker information
                enhanced_text = self._build_enhanced_text(raw_text, speaker_name)
                content_for_length_check = raw_text

            # Performance monitoring
            total_time = time.monotonic() - total_start_time
            logger.bind(tag=TAG).debug(f"Total processing time: {total_time:.3f}s")

            # Check text length
            text_len, _ = remove_punctuation_and_length(content_for_length_check)
            self.stop_ws_connection()

            if text_len > 0:
                audio_snapshot = asr_audio_task.copy()
                enqueue_asr_report(conn, enhanced_text, audio_snapshot)
                # Report using the custom module
                await startToChat(conn, enhanced_text)
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to handle speech stop: {e}")
            import traceback

            logger.bind(tag=TAG).debug(f"Exception details: {traceback.format_exc()}")

    def _build_enhanced_text(self, text: str, speaker_name: Optional[str]) -> str:
        """Build text including speaker information (plain-text ASR only)"""
        if speaker_name and speaker_name.strip():
            return json.dumps(
                {"speaker": speaker_name, "content": text}, ensure_ascii=False
            )
        else:
            return text

    def _pcm_to_wav(self, pcm_data: bytes) -> bytes:
        """Convert PCM data to WAV format"""
        if len(pcm_data) == 0:
            logger.bind(tag=TAG).warning("PCM data is empty, cannot convert to WAV")
            return b""

        # Ensure the data length is even (16-bit audio)
        if len(pcm_data) % 2 != 0:
            pcm_data = pcm_data[:-1]

        # Create the WAV file header
        wav_buffer = io.BytesIO()
        try:
            with wave.open(wav_buffer, "wb") as wav_file:
                wav_file.setnchannels(1)  # Mono
                wav_file.setsampwidth(2)  # 16-bit
                wav_file.setframerate(16000)  # 16kHz sample rate
                wav_file.writeframes(pcm_data)

            wav_buffer.seek(0)
            wav_data = wav_buffer.read()

            return wav_data
        except Exception as e:
            logger.bind(tag=TAG).error(f"WAV conversion failed: {e}")
            return b""

    def stop_ws_connection(self):
        pass

    async def close(self):
        pass

    class AudioArtifacts(NamedTuple):
        pcm_frames: List[bytes]
        """PCM audio frame list"""
        pcm_bytes: bytes
        """Merged PCM audio bytes"""
        file_path: Optional[str]
        """WAV file path"""
        temp_path: Optional[str]
        """Temporary WAV file path"""

    def get_current_artifacts(self) -> Optional["ASRProviderBase.AudioArtifacts"]:
        return self._current_artifacts

    def requires_file(self) -> bool:
        """Whether file input is required"""
        return False

    def prefers_temp_file(self) -> bool:
        """Whether to prefer a temporary file"""
        return False

    def build_temp_file(self, pcm_bytes: bytes) -> Optional[str]:
        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_file:
                temp_path = temp_file.name
            with wave.open(temp_path, "wb") as wav_file:
                wav_file.setnchannels(1)
                wav_file.setsampwidth(2)
                wav_file.setframerate(16000)
                wav_file.writeframes(pcm_bytes)
            return temp_path
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to create temporary audio file: {e}")
            return None

    def save_audio_to_file(self, pcm_data: List[bytes], session_id: str) -> str:
        """PCM data saved as a WAV file"""
        module_name = __name__.split(".")[-1]
        file_name = f"asr_{module_name}_{session_id}_{uuid.uuid4()}.wav"
        file_path = os.path.join(self.output_dir, file_name)

        with wave.open(file_path, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)  # 2 bytes = 16-bit
            wf.setframerate(16000)
            wf.writeframes(b"".join(pcm_data))

        return file_path

    async def speech_to_text_wrapper(
        self, pcm_data: List[bytes], session_id: str
    ) -> Tuple[Optional[str], Optional[str]]:
        file_path = None
        temp_path = None
        try:
            combined_pcm_data = b"".join(pcm_data)

            free_space = shutil.disk_usage(self.output_dir).free
            if free_space < len(combined_pcm_data) * 2:
                raise OSError("Insufficient disk space")

            if self.requires_file() and self.prefers_temp_file():
                temp_path = self.build_temp_file(combined_pcm_data)

            if (hasattr(self, "delete_audio_file") and not self.delete_audio_file) or (
                self.requires_file() and not self.prefers_temp_file()
            ):
                file_path = self.save_audio_to_file(pcm_data, session_id)

            if len(combined_pcm_data) == 0:
                artifacts = None
            else:
                artifacts = ASRProviderBase.AudioArtifacts(
                    pcm_frames=pcm_data,
                    pcm_bytes=combined_pcm_data,
                    file_path=file_path,
                    temp_path=temp_path,
                )

            text, _ = await self.speech_to_text(
                pcm_data, session_id, artifacts
            )
            return text, file_path
        except OSError as e:
            logger.bind(tag=TAG).error(f"File operation error: {e}")
            return None, None
        except Exception as e:
            logger.bind(tag=TAG).error(f"Speech recognition failed: {e}")
            return None, None
        finally:
            try:
                if temp_path and os.path.exists(temp_path):
                    os.unlink(temp_path)
                if (
                    hasattr(self, "delete_audio_file")
                    and self.delete_audio_file
                    and file_path
                    and os.path.exists(file_path)
                ):
                    os.remove(file_path)
            except Exception as e:
                logger.bind(tag=TAG).error(f"File cleanup failed: {e}")

    @abstractmethod
    async def speech_to_text(
        self,
        opus_data: List[bytes],
        session_id: str,
        artifacts: Optional[AudioArtifacts] = None,
    ) -> Tuple[Optional[str], Optional[str]]:
        """Convert speech data to text

        :param opus_data: input Opus audio data
        :param session_id: session ID
        :param artifacts: audio artifacts, including PCM data, file paths, etc.
        :return: recognized text and file path (if any)
        """
        pass
