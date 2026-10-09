import os
import json
import time
import queue
import asyncio
import aiohttp
import requests
import traceback

from core.utils import textUtils
from config.logger import setup_logging
from core.utils.util import parse_string_to_list
from core.providers.tts.base import TTSProviderBase
from core.providers.tts.dto.dto import SentenceType, ContentType
from core.utils.tts import MarkdownCleaner, convert_percentage_to_range


TAG = __name__
logger = setup_logging()


class TTSProvider(TTSProviderBase):
    def __init__(self, config, delete_audio_file):
        super().__init__(config, delete_audio_file)
        self.group_id = config.get("group_id")
        self.api_key = config.get("api_key")
        self.model = config.get("model")
        if config.get("private_voice"):
            self.voice = config.get("private_voice")
        else:
            self.voice = config.get("voice_id")

        default_voice_setting = {
            "voice_id": "female-shaonv",
            "speed": 1,
            "vol": 1,
            "pitch": 0,
            "emotion": "happy",
        }
        default_pronunciation_dict = {"tone": ["处理/(chu3)(li3)", "危险/dangerous"]}
        defult_audio_setting = {
            "sample_rate": 24000,
            "bitrate": 128000,
            "format": "pcm",
            "channel": 1,
        }
        self.voice_setting = {
            **default_voice_setting,
            **config.get("voice_setting", {}),
        }
        self.pronunciation_dict = {
            **default_pronunciation_dict,
            **config.get("pronunciation_dict", {}),
        }
        self.audio_setting = {**defult_audio_setting, **config.get("audio_setting", {})}
        self.timber_weights = parse_string_to_list(config.get("timber_weights"))

        if self.voice:
            self.voice_setting["voice_id"] = self.voice

        # Apply percentage adjustments (if present), otherwise use the public configuration
        if "ttsVolume" in config:
            self.voice_setting["vol"] = round(convert_percentage_to_range(
                config["ttsVolume"], min_val=0.1, max_val=10, base_val=1.0
            ), 1)

        if "ttsRate" in config:
            self.voice_setting["speed"] = round(convert_percentage_to_range(
                config["ttsRate"], min_val=0.5, max_val=2, base_val=1.0
            ), 1)

        if "ttsPitch" in config:
            self.voice_setting["pitch"] = int(convert_percentage_to_range(
                config["ttsPitch"], min_val=-12, max_val=12, base_val=0
            ))

        self.host = "api.minimaxi.com"  # fallback address: api-bj.minimaxi.com
        self.api_url = f"https://{self.host}/v1/t2a_v2?GroupId={self.group_id}"
        self.header = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
        }
        self.audio_file_type = defult_audio_setting.get("format", "pcm")

        # PCM buffer
        self.pcm_buffer = bytearray()

    async def open_audio_channels(self, conn):
        """Initialize the audio channel and update the configuration based on conn.sample_rate"""
        # Call the parent method
        await super().open_audio_channels(conn)

        # Update the sample rate in audio_setting to the actual conn.sample_rate
        self.audio_setting["sample_rate"] = conn.sample_rate

    def tts_text_priority_thread(self):
        """Streaming text processing thread"""
        while not self.conn.stop_event.is_set():
            try:
                message = self.tts_text_queue.get(timeout=1)
                if message.sentence_type == SentenceType.FIRST:
                    # Initialize parameters
                    self.tts_stop_request = False
                    self.processed_chars = 0
                    self.tts_text_buff = []
                    self.before_stop_play_files.clear()
                elif ContentType.TEXT == message.content_type:
                    self.tts_text_buff.append(message.content_detail)
                    segment_text = self._get_segment_text()
                    if segment_text:
                        self.to_tts_single_stream(segment_text)

                elif ContentType.FILE == message.content_type:
                    logger.bind(tag=TAG).info(
                        f"Adding audio file to playback queue: {message.content_file}"
                    )
                    if message.content_file and os.path.exists(message.content_file):
                        # Process the file audio data first
                        self._process_audio_file_stream(message.content_file, callback=lambda audio_data: self.handle_audio_file(audio_data, message.content_detail))
                if message.sentence_type == SentenceType.LAST:
                    # Process the remaining text
                    self._process_remaining_text_stream(True)

            except queue.Empty:
                continue
            except Exception as e:
                logger.bind(tag=TAG).error(
                    f"Failed to process TTS text: {str(e)}, type: {type(e).__name__}, stack: {traceback.format_exc()}"
                )

    def _process_remaining_text_stream(self, is_last=False):
        """Process the remaining text and generate speech
        Returns:
            bool: whether the text was processed successfully
        """
        full_text = "".join(self.tts_text_buff)
        remaining_text = full_text[self.processed_chars :]
        if remaining_text:
            segment_text = textUtils.get_string_no_punctuation_or_emoji(remaining_text)
            if segment_text:
                self.to_tts_single_stream(segment_text, is_last)
                self.processed_chars += len(full_text)
            else:
                self._process_before_stop_play_files()
        else:
            self._process_before_stop_play_files()

    def to_tts_single_stream(self, text, is_last=False):
        try:
            max_repeat_time = 5
            original_text = text
            text = MarkdownCleaner.clean_markdown(text)
            if self._correct_words_pattern:
                text = self._correct_words_pattern.sub(lambda m: self.correct_words[m.group(0)], text)
            try:
                asyncio.run(self.text_to_speak(text, is_last))
            except Exception as e:
                logger.bind(tag=TAG).warning(
                    f"Speech generation failed (attempt {5 - max_repeat_time + 1}): {original_text}, error: {e}"
                )
                max_repeat_time -= 1

            if max_repeat_time > 0:
                logger.bind(tag=TAG).info(
                    f"Speech generated successfully: {original_text}, retries: {5 - max_repeat_time}"
                )
            else:
                logger.bind(tag=TAG).error(
                    f"Speech generation failed: {original_text}, please check the network or service status"
                )
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to generate TTS file: {e}")
        finally:
            return None

    async def text_to_speak(self, text, is_last):
        """Stream TTS audio; push the audio list only once per sentence"""
        payload = {
            "model": self.model,
            "text": text,
            "stream": True,
            "voice_setting": self.voice_setting,
            "pronunciation_dict": self.pronunciation_dict,
            "audio_setting": self.audio_setting,
        }

        if type(self.timber_weights) is list and len(self.timber_weights) > 0:
            payload["timber_weights"] = self.timber_weights
            payload["voice_setting"]["voice_id"] = ""

        frame_bytes = int(
            self.opus_encoder.sample_rate
            * self.opus_encoder.channels  # 1
            * self.opus_encoder.frame_size_ms
            / 1000
            * 2
        )  # 16-bit = 2 bytes
        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(
                    self.api_url,
                    headers=self.header,
                    data=json.dumps(payload),
                    timeout=self.tts_timeout,
                ) as resp:

                    if resp.status != 200:
                        logger.bind(tag=TAG).error(
                            f"TTS request failed: {resp.status}, {await resp.text()}"
                        )
                        self.tts_audio_queue.put((SentenceType.LAST, [], None))
                        return

                    self.pcm_buffer.clear()
                    self.tts_audio_queue.put((SentenceType.FIRST, [], text))

                    # Process audio stream data
                    buffer = b""
                    async for chunk in resp.content.iter_any():
                        if not chunk:
                            continue

                        buffer += chunk
                        while True:
                            # Find the chunk delimiter
                            header_pos = buffer.find(b"data: ")
                            if header_pos == -1:
                                break

                            end_pos = buffer.find(b"\n\n", header_pos)
                            if end_pos == -1:
                                break

                            # Extract a single complete JSON chunk
                            json_str = buffer[header_pos + 6 : end_pos].decode("utf-8")
                            buffer = buffer[end_pos + 2 :]

                            try:
                                data = json.loads(json_str)

                                # Check for business-level errors
                                base_resp = data.get("base_resp", {})
                                status_code = base_resp.get("status_code", 0)
                                if status_code != 0:
                                    status_msg = base_resp.get("status_msg", "Unknown error")
                                    logger.bind(tag=TAG).error(
                                        f"TTS request failed, error code:{status_code}, error message:{status_msg}"
                                    )
                                    self.tts_audio_queue.put((SentenceType.LAST, [], None))
                                    return

                                status = data.get("data", {}).get("status", 1)
                                audio_hex = data.get("data", {}).get("audio")

                                # Only handle valid audio chunks with status=1; ignore the status=2 final summary chunk
                                if status == 1 and audio_hex:
                                    pcm_data = bytes.fromhex(audio_hex)
                                    self.pcm_buffer.extend(pcm_data)

                            except json.JSONDecodeError as e:
                                logger.bind(tag=TAG).error(f"JSON parse failed: {e}")
                                continue

                        while len(self.pcm_buffer) >= frame_bytes:
                            frame = bytes(self.pcm_buffer[:frame_bytes])
                            del self.pcm_buffer[:frame_bytes]

                            self.opus_encoder.encode_pcm_to_opus_stream(
                                frame, end_of_stream=False, callback=self.handle_opus
                            )

                    # flush remaining data shorter than one frame
                    if self.pcm_buffer:
                        self.opus_encoder.encode_pcm_to_opus_stream(
                            bytes(self.pcm_buffer),
                            end_of_stream=True,
                            callback=self.handle_opus,
                        )
                        self.pcm_buffer.clear()

                    # If this is the last segment, signal that audio retrieval is complete
                    if is_last:
                        self._process_before_stop_play_files()

        except Exception as e:
            logger.bind(tag=TAG).error(f"TTS request exception: {e}")
            self.tts_audio_queue.put((SentenceType.LAST, [], None))

    async def close(self):
        """Resource cleanup"""
        await super().close()
        if hasattr(self, "opus_encoder"):
            self.opus_encoder.close()

    def to_tts(self, text: str) -> list:
        """Non-streaming TTS processing, for testing and saving audio files
        Args:
            text: text to convert
        Returns:
            list: list of Opus-encoded audio data
        """
        start_time = time.time()
        text = MarkdownCleaner.clean_markdown(text)
        if self._correct_words_pattern:
            text = self._correct_words_pattern.sub(lambda m: self.correct_words[m.group(0)], text)

        payload = {
            "model": self.model,
            "text": text,
            "stream": True,
            "voice_setting": self.voice_setting,
            "pronunciation_dict": self.pronunciation_dict,
            "audio_setting": self.audio_setting,
        }

        if type(self.timber_weights) is list and len(self.timber_weights) > 0:
            payload["timber_weights"] = self.timber_weights
            payload["voice_setting"]["voice_id"] = ""

        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
        }

        try:
            with requests.post(
                self.api_url,
                data=json.dumps(payload),
                headers=headers,
                timeout=self.tts_timeout,
            ) as response:
                if response.status_code != 200:
                    logger.bind(tag=TAG).error(
                        f"TTS request failed: {response.status_code}, {response.text}"
                    )
                    return []

                logger.info(f"TTS request succeeded: {text}, time taken: {time.time() - start_time}s")

                # Process PCM data with the Opus encoder
                opus_datas = []
                full_content = response.content.decode('utf-8')
                pcm_data = bytearray()
                for data_block in full_content.split('\n\n'):
                    if not data_block.startswith('data: '):
                        continue

                    try:
                        json_str = data_block[6:]  # Strip the 'data: ' prefix
                        data = json.loads(json_str)
                        if data.get('data', {}).get('status') == 1:
                            audio_hex = data['data']['audio']
                            pcm_data.extend(bytes.fromhex(audio_hex))
                    except (json.JSONDecodeError, KeyError) as e:
                        logger.bind(tag=TAG).warning(f"Invalid data chunk: {e}")
                        continue

                # Compute bytes per frame
                frame_bytes = int(
                    self.opus_encoder.sample_rate
                    * self.opus_encoder.channels
                    * self.opus_encoder.frame_size_ms
                    / 1000
                    * 2
                )

                # Process the merged PCM data frame by frame
                for i in range(0, len(pcm_data), frame_bytes):
                    frame = bytes(pcm_data[i:i+frame_bytes])
                    if len(frame) < frame_bytes:
                        frame += b"\x00" * (frame_bytes - len(frame))
 
                    self.opus_encoder.encode_pcm_to_opus_stream(
                        frame,
                        end_of_stream=(i + frame_bytes >= len(pcm_data)),
                        callback=lambda opus: opus_datas.append(opus)
                    )

                return opus_datas

        except Exception as e:
            logger.bind(tag=TAG).error(f"TTS request exception: {e}")
            return []
