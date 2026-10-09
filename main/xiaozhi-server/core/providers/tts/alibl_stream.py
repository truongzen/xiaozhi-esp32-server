import os
import uuid
import json
import time
import queue
import asyncio
import traceback
import websockets

from typing import Callable, Any
from config.logger import setup_logging
from core.utils.tts import MarkdownCleaner
from core.utils.alibl_endpoint import build_ws_connect_options, resolve_ws_url
from core.providers.tts.base import TTSProviderBase
from core.providers.tts.dto.dto import SentenceType, ContentType, InterfaceType

TAG = __name__
logger = setup_logging()


class TTSProvider(TTSProviderBase):
    TTS_PARAM_CONFIG = [
        ("ttsVolume", "volume", 0, 100, 50, int),
        ("ttsRate", "rate", 0.5, 2.0, 1.0, lambda v: round(v, 1)),
        ("ttsPitch", "pitch", 0.5, 2.0, 1.0, lambda v: round(v, 1)),
    ]

    def __init__(self, config, delete_audio_file):
        super().__init__(config, delete_audio_file)

        self.interface_type = InterfaceType.DUAL_STREAM
        # Basic configuration
        self.api_key = config.get("api_key")
        if not self.api_key:
            raise ValueError("api_key is required for CosyVoice TTS")

        # WebSocket configuration
        self.ws_url = resolve_ws_url(config.get("ws_url"))
        self.ws_connect_options = build_ws_connect_options(self.tts_timeout)
        self.ws = None
        self._monitor_task = None
        self.activate_session = False
        self.last_active_time = None

        # Model and voice configuration
        self.model = config.get("model", "cosyvoice-v2")
        self.voice = config.get("voice", "longxiaochun_v2")  # default voice
        if config.get("private_voice"):
            self.voice = config.get("private_voice")

        # Audio parameter configuration
        self.format = config.get("format", "pcm")

        volume = config.get("volume", "50")
        self.volume = int(volume) if volume else 50

        rate = config.get("rate", "1.0")
        self.rate = float(rate) if rate else 1.0

        pitch = config.get("pitch", "1.0")
        self.pitch = float(pitch) if pitch else 1.0

        # Apply percentage adjustments (if present), otherwise use the public configuration
        self._apply_percentage_params(config)

        self.header = {
            "Authorization": f"Bearer {self.api_key}",
            # "user-agent": "your_platform_info", // optional
            # "X-DashScope-WorkSpace": workspace, // optional, Alibaba Cloud Bailian workspace ID
            "X-DashScope-DataInspection": "enable",
        }

    async def _ensure_connection(self):
        """Ensure the WebSocket connection is available; supports connection reuse within 60 seconds"""
        try:
            current_time = time.time()
            if self.ws and current_time - self.last_active_time < 60:
                # The connection can only be reused for continuous dialogue within one minute
                logger.bind(tag=TAG).debug(f"Using existing connection...")
                return self.ws
            logger.bind(tag=TAG).debug("Establishing new connection...")

            # Cancel the old listener task before establishing a new connection
            await self._cancel_monitor_task()

            self.ws = await websockets.connect(
                self.ws_url,
                additional_headers=self.header,
                ping_interval=30,
                ping_timeout=10,
                close_timeout=10,
                **self.ws_connect_options,
            )

            logger.bind(tag=TAG).debug("WebSocket connection established successfully")
            self.last_active_time = current_time
            return self.ws
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to establish connection: {str(e)}")
            self.ws = None
            self.last_active_time = None
            raise

    def tts_text_priority_thread(self):
        """Streaming TTS text processing thread"""
        while not self.conn.stop_event.is_set():
            try:
                message = self.tts_text_queue.get(timeout=1)

                if self.conn.client_abort:
                    try:
                        logger.bind(tag=TAG).info("Received interrupt, terminating TTS text processing thread")
                        asyncio.run_coroutine_threadsafe(
                            self.finish_session(self.conn.sentence_id),
                            loop=self.conn.loop,
                        )
                        continue
                    except Exception as e:
                        logger.bind(tag=TAG).error(f"Failed to cancel TTS session: {str(e)}")
                        continue

                # Filter stale messages: check whether sentence_id matches
                if message.sentence_id != self.conn.sentence_id:
                    continue

                logger.bind(tag=TAG).debug(
                    f"Received TTS task | {message.sentence_type.name} | {message.content_type.name} | Session ID: {message.sentence_id}"
                )

                if message.sentence_type == SentenceType.FIRST:
                    # Reset streaming state
                    self.reset_stream_state()
                    # Initialize session
                    try:
                        if not getattr(self.conn, "sentence_id", None): 
                            self.conn.sentence_id = uuid.uuid4().hex
                            logger.bind(tag=TAG).debug(f"Auto-generated new session ID: {self.conn.sentence_id}")

                        logger.bind(tag=TAG).debug("Starting TTS session...")
                        future = asyncio.run_coroutine_threadsafe(
                            self.start_session(self.conn.sentence_id),
                            loop=self.conn.loop,
                        )
                        future.result(timeout=self.tts_timeout)
                        self.before_stop_play_files.clear()
                        logger.bind(tag=TAG).debug("TTS session started successfully")
                    except Exception as e:
                        logger.bind(tag=TAG).error(f"Failed to start TTS session: {str(e)}")
                        continue

                elif ContentType.TEXT == message.content_type:
                    if message.content_detail:
                        try:
                            logger.bind(tag=TAG).debug(
                                f"Start sending TTS text: {message.content_detail}"
                            )
                            future = asyncio.run_coroutine_threadsafe(
                                self.text_to_speak(message.content_detail, None),
                                loop=self.conn.loop,
                            )
                            future.result(timeout=self.tts_timeout)
                        except Exception as e:
                            logger.bind(tag=TAG).error(f"Failed to send TTS text: {str(e)}")
                            continue

                elif ContentType.FILE == message.content_type:
                    logger.bind(tag=TAG).info(
                        f"Adding audio file to playback queue: {message.content_file}"
                    )
                    if message.content_file and os.path.exists(message.content_file):
                        # Process the file audio data first
                        self._process_audio_file_stream(message.content_file, callback=lambda audio_data: self.handle_audio_file(audio_data, message.content_detail))

                if message.sentence_type == SentenceType.LAST:
                    try:
                        logger.bind(tag=TAG).debug("Ending TTS session...")
                        future = asyncio.run_coroutine_threadsafe(
                            self.finish_session(self.conn.sentence_id),
                            loop=self.conn.loop,
                        )
                        future.result()
                    except Exception as e:
                        logger.bind(tag=TAG).error(f"Failed to end TTS session: {str(e)}")
                        continue

            except queue.Empty:
                continue
            except Exception as e:
                logger.bind(tag=TAG).error(
                    f"Failed to process TTS text: {str(e)}, type: {type(e).__name__}, stack: {traceback.format_exc()}"
                )
                continue

    async def text_to_speak(self, text, _):
        """Send text to the TTS service for synthesis"""
        try:
            if self.ws is None:
                logger.bind(tag=TAG).warning("WebSocket connection does not exist, aborting text sending")
                return

            # Filter Markdown
            filtered_text = MarkdownCleaner.clean_markdown(text)

            if filtered_text:
                # Use sliding-window matching to handle replacement words spanning chunks
                confirmed_texts, self._pending_prefix = self._match_stream_text(filtered_text)

                # Send each finalized text segment
                for txt in confirmed_texts:
                    if txt and self.ws:
                        continue_task_message = {
                            "header": {
                                "action": "continue-task",
                                "task_id": self.conn.sentence_id,
                                "streaming": "duplex",
                            },
                            "payload": {"input": {"text": txt}},
                        }
                        await self.ws.send(json.dumps(continue_task_message))
                        self.last_active_time = time.time()
            return
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to send TTS text: {str(e)}")
            if self.ws:
                try:
                    await self.ws.close()
                except:
                    pass
                self.ws = None
            raise

    async def start_session(self, session_id):
        """Start TTS session"""
        logger.bind(tag=TAG).debug(f"Starting session~~{session_id}")
        try:
            # If the previous session is still active, close the previous connection and create a new one
            if self.activate_session:
                await self.close()

            # Set session active flag
            self.activate_session = True

            # Ensure the connection is available
            await self._ensure_connection()

            # Starting listener task
            if self._monitor_task is None or self._monitor_task.done():
                logger.bind(tag=TAG).debug("Starting listener task...")
                self._monitor_task = asyncio.create_task(self._start_monitor_tts_response())

            # Send run-task message to start the session
            run_task_message = {
                "header": {
                    "action": "run-task",
                    "task_id": session_id,
                    "streaming": "duplex",
                },
                "payload": {
                    "task_group": "audio",
                    "task": "tts",
                    "function": "SpeechSynthesizer",
                    "model": self.model,
                    "parameters": {
                        "text_type": "PlainText",
                        "voice": self.voice,
                        "format": self.format,
                        "sample_rate": self.conn.sample_rate,
                        "volume": self.volume,
                        "rate": self.rate,
                        "pitch": self.pitch,
                    },
                    "input": {}
                },
            }

            await self.ws.send(json.dumps(run_task_message))
            self.last_active_time = time.time()
            logger.bind(tag=TAG).debug("Session start request sent")
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to start session: {str(e)}")
            await self.close()
            raise

    async def finish_session(self, session_id):
        """End TTS session"""
        logger.bind(tag=TAG).debug(f"Closing session~~{session_id}")
        try:
            if self.ws and session_id:
                # Send finish-task message
                finish_task_message = {
                    "header": {
                        "action": "finish-task",
                        "task_id": session_id,
                        "streaming": "duplex",
                    },
                    "payload": {
                        "input": {}
                    }
                }

                await self.ws.send(json.dumps(finish_task_message))
                self.last_active_time = time.time()

        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to close session: {str(e)}")
            await self.close()
            raise

    async def close(self):
        """Clean up resources"""
        await super().close()
        self.activate_session = False
        await self._cancel_monitor_task()

        # Close WebSocket connection
        if self.ws:
            try:
                await self.ws.close()
            except:
                pass
            self.ws = None
            self.last_active_time = None
    
    async def _cancel_monitor_task(self):
        """Cancel the listener task"""
        if self._monitor_task and not self._monitor_task.done():
            self._monitor_task.cancel()
            try:
                await self._monitor_task
            except asyncio.CancelledError:
                pass
            except Exception as e:
                logger.bind(tag=TAG).warning(f"Error cancelling listener task: {e}")
        self._monitor_task = None

    async def _start_monitor_tts_response(self):
        """Listen for TTS responses - long-running"""
        try:
            while not self.conn.stop_event.is_set():
                try:
                    msg = await self.ws.recv()
                    self.last_active_time = time.time()

                    if isinstance(msg, str):  # JSON control message
                        try:
                            data = json.loads(msg)
                            header = data.get("header", {})
                            event = header.get("event")
                            task_id = header.get("task_id")

                            # Only handle responses for the currently active session
                            if task_id and self.conn.sentence_id != task_id:
                                if event in ["task-finished", "task-failed"]:
                                    logger.bind(tag=TAG).debug(f"Received residual downstream end response, resetting session state~~")
                                    self.activate_session = False
                                continue

                            if event == "task-started":
                                logger.bind(tag=TAG).debug("TTS task started successfully~")
                            elif event == "result-generated":
                                output = data.get("payload", {}).get("output", {})
                                if output.get("type") == "sentence-begin":
                                    original_text = output.get("original_text")
                                    self.tts_text = self._restore_original_text(original_text)
                                    logger.bind(tag=TAG).debug(f"Sentence speech generation started: {self.tts_text}")
                                    self.tts_audio_queue.put((SentenceType.FIRST, [], self.tts_text))
                                elif output.get("type") == "sentence-end":
                                    logger.bind(tag=TAG).info(f"Sentence speech generated successfully: {self.tts_text}")
                            elif event == "task-finished":
                                logger.bind(tag=TAG).debug("TTS task completed~")
                                self.activate_session = False
                                self._process_before_stop_play_files()
                            elif event == "task-failed":
                                error_code = header.get("error_code", "unknown")
                                error_message = header.get("error_message", "Unknown error")
                                logger.bind(tag=TAG).error(
                                    f"TTS task failed: {error_code} - {error_message}"
                                )
                                break
                        except json.JSONDecodeError:
                            logger.bind(tag=TAG).warning("Received invalid JSON message")
                    elif isinstance(msg, (bytes, bytearray)):
                        self.opus_encoder.encode_pcm_to_opus_stream(
                            msg, False, callback=self.handle_opus
                        )
                except websockets.ConnectionClosed:
                    logger.bind(tag=TAG).warning("WebSocket connection closed")
                    break
                except Exception as e:
                    logger.bind(tag=TAG).error(
                        f"Error processing TTS response: {e}\n{traceback.format_exc()}"
                    )
                    break

            # On connection error, close WebSocket
            if self.ws:
                try:
                    await self.ws.close()
                except:
                    pass
                self.ws = None
        # Clear the reference when the listener task exits
        finally:
            self.activate_session = False
            self._monitor_task = None

    def audio_to_opus_data_stream(
        self, audio_file_path, callback: Callable[[Any], Any] = None
    ):
        """Override parent method: use a separate temporary encoder for audio files to avoid concurrency conflicts with the TTS streaming encoder.
        In dual-stream TTS, the monitor task receives TTS audio on the event loop thread and encodes it with self.opus_encoder,
        while tts_text_priority_thread also uses self.opus_encoder when processing music files;
        the shared encoder.buffer is not thread-safe, and concurrent access causes SILK resampler assertion failures.
        """
        from core.utils.util import audio_to_data_stream

        return audio_to_data_stream(
            audio_file_path,
            is_opus=True,
            callback=callback,
            sample_rate=self.conn.sample_rate,
            opus_encoder=None,
        )

    def to_tts(self, text: str) -> list:
        """Generate audio data non-streaming, for audio generation and testing"""
        try:
            # Create event loop
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)

            # Generate session ID
            session_id = uuid.uuid4().hex
            # Store audio data
            audio_data = []

            async def _generate_audio():
                ws = await websockets.connect(
                    self.ws_url,
                    additional_headers=self.header,
                    ping_interval=30,
                    ping_timeout=10,
                    close_timeout=10,
                    max_size=10 * 1024 * 1024,
                    **self.ws_connect_options,
                )

                try:
                    # Send run-task message to start the session
                    run_task_message = {
                        "header": {
                            "action": "run-task",
                            "task_id": session_id,
                            "streaming": "duplex",
                        },
                        "payload": {
                            "task_group": "audio",
                            "task": "tts",
                            "function": "SpeechSynthesizer",
                            "model": self.model,
                            "parameters": {
                                "text_type": "PlainText",
                                "voice": self.voice,
                                "format": self.format,
                                "sample_rate": self.conn.sample_rate,
                                "volume": self.volume,
                                "rate": self.rate,
                                "pitch": self.pitch,
                            },
                            "input": {}
                        },
                    }
                    await ws.send(json.dumps(run_task_message))

                    # Wait for the task to start
                    task_started = False
                    while not task_started:
                        msg = await ws.recv()
                        if isinstance(msg, str):
                            data = json.loads(msg)
                            header = data.get("header", {})
                            if header.get("event") == "task-started":
                                task_started = True
                                logger.bind(tag=TAG).debug("TTS task started")
                            elif header.get("event") == "task-failed":
                                error_code = header.get("error_code", "unknown")
                                error_message = header.get("error_message", "Unknown error")
                                raise Exception(
                                    f"Failed to start task: {error_code} - {error_message}"
                                )

                    # Send text
                    filtered_text = MarkdownCleaner.clean_markdown(text)
                    if self._correct_words_pattern:
                        filtered_text = self._correct_words_pattern.sub(lambda m: self.correct_words[m.group(0)], filtered_text)
                    # Send continue-task message
                    continue_task_message = {
                        "header": {
                            "action": "continue-task",
                            "task_id": session_id,
                            "streaming": "duplex",
                        },
                        "payload": {"input": {"text": filtered_text}},
                    }
                    await ws.send(json.dumps(continue_task_message))

                    # Send finish-task message
                    finish_task_message = {
                        "header": {
                            "action": "finish-task",
                            "task_id": session_id,
                            "streaming": "duplex",
                        },
                        "payload": {
                            "input": {}
                        }
                    }
                    await ws.send(json.dumps(finish_task_message))

                    # Receive audio data
                    task_finished = False
                    while not task_finished:
                        msg = await ws.recv()
                        if isinstance(msg, (bytes, bytearray)):
                            self.opus_encoder.encode_pcm_to_opus_stream(
                                msg,
                                end_of_stream=False,
                                callback=lambda opus: audio_data.append(opus)
                            )
                        elif isinstance(msg, str):
                            data = json.loads(msg)
                            header = data.get("header", {})
                            if header.get("event") == "task-finished":
                                task_finished = True
                                logger.bind(tag=TAG).debug("TTS task completed")
                            elif header.get("event") == "task-failed":
                                error_code = header.get("error_code", "unknown")
                                error_message = header.get("error_message", "Unknown error")
                                raise Exception(
                                    f"Synthesis failed: {error_code} - {error_message}"
                                )

                finally:
                    # Clean up resources
                    try:
                        await ws.close()
                    except:
                        pass

            # Run async task
            loop.run_until_complete(_generate_audio())
            loop.close()

            return audio_data

        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to generate audio data: {str(e)}")
            return []
