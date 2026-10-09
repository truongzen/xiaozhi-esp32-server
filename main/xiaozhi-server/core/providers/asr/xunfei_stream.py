import json
import hmac
import base64
import hashlib
import asyncio
import websockets
import gc
from time import mktime
from datetime import datetime
from urllib.parse import urlencode
from typing import List, TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler
from config.logger import setup_logging
from wsgiref.handlers import format_date_time
from core.providers.asr.base import ASRProviderBase
from core.providers.asr.dto.dto import InterfaceType

TAG = __name__
logger = setup_logging()

# Frame status constants
STATUS_FIRST_FRAME = 0  # First frame marker
STATUS_CONTINUE_FRAME = 1  # Intermediate frame marker
STATUS_LAST_FRAME = 2  # Last frame marker


class ASRProvider(ASRProviderBase):
    def __init__(self, config, delete_audio_file):
        super().__init__()
        self.interface_type = InterfaceType.STREAM
        self.config = config
        self.text = ""
        self.asr_ws = None
        self.forward_task = None
        self.is_processing = False
        self.server_ready = False

        # iFlytek configuration
        self.app_id = config.get("app_id")
        self.api_key = config.get("api_key")
        self.api_secret = config.get("api_secret")

        if not all([self.app_id, self.api_key, self.api_secret]):
            raise ValueError("Must provide app_id, api_key and api_secret")

        # Recognition parameters
        self.iat_params = {
            "domain": config.get("domain", "slm"),
            "language": config.get("language", "zh_cn"),
            "accent": config.get("accent", "mandarin"),
            "result": {"encoding": "utf8", "compress": "raw", "format": "plain"},
        }

        self.output_dir = config.get("output_dir", "tmp/")
        self.delete_audio_file = delete_audio_file

    def create_url(self) -> str:
        """Generate auth URL"""
        url = "ws://iat.cn-huabei-1.xf-yun.com/v1"
        # Generate an RFC1123 timestamp
        now = datetime.now()
        date = format_date_time(mktime(now.timetuple()))

        # Concatenate the string
        signature_origin = "host: " + "iat.cn-huabei-1.xf-yun.com" + "\n"
        signature_origin += "date: " + date + "\n"
        signature_origin += "GET " + "/v1 " + "HTTP/1.1"

        # Sign with hmac-sha256
        signature_sha = hmac.new(
            self.api_secret.encode("utf-8"),
            signature_origin.encode("utf-8"),
            digestmod=hashlib.sha256,
        ).digest()
        signature_sha = base64.b64encode(signature_sha).decode(encoding="utf-8")

        authorization_origin = (
            'api_key="%s", algorithm="%s", headers="%s", signature="%s"'
            % (self.api_key, "hmac-sha256", "host date request-line", signature_sha)
        )
        authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
            encoding="utf-8"
        )

        # Combine the request auth parameters into a dict
        v = {
            "authorization": authorization,
            "date": date,
            "host": "iat.cn-huabei-1.xf-yun.com",
        }

        # Append the auth parameters to build the url
        url = url + "?" + urlencode(v)
        return url

    async def open_audio_channels(self, conn: "ConnectionHandler"):
        await super().open_audio_channels(conn)

    async def receive_audio(self, conn: "ConnectionHandler", pcm_frame, audio_have_voice):
        # Call the parent method first to handle the base logic
        await super().receive_audio(conn, pcm_frame, audio_have_voice)

        # If there is voice now and no connection has been established yet
        if audio_have_voice and self.asr_ws is None and not self.is_processing:
            try:
                await self._start_recognition(conn)
            except Exception as e:
                logger.bind(tag=TAG).error(f"Failed to establish ASR connection: {str(e)}")
                await self._cleanup()
                return

        # Send the current audio data
        if self.asr_ws and self.is_processing and self.server_ready:
            try:
                await self._send_audio_frame(pcm_frame, STATUS_CONTINUE_FRAME)
            except Exception as e:
                logger.bind(tag=TAG).warning(f"Error while sending audio data: {e}")
                await self._cleanup()

    async def _start_recognition(self, conn: "ConnectionHandler"):
        """Start a recognition session"""
        try:
            self.is_processing = True
            # Establish WebSocket connection
            ws_url = self.create_url()
            logger.bind(tag=TAG).info(f"Connecting to ASR service: {ws_url[:50]}...")

            # In manual mode, set the timeout to one minute
            if conn.client_listen_mode == "manual":
                self.iat_params["eos"] = 60000

            self.asr_ws = await websockets.connect(
                ws_url,
                max_size=1000000000,
                ping_interval=None,
                ping_timeout=None,
                close_timeout=10,
            )

            logger.bind(tag=TAG).info("ASR WebSocket connection established")
            self.server_ready = False
            self.forward_task = asyncio.create_task(self._forward_results(conn))

            # Send the first audio frame
            if conn.asr_audio and len(conn.asr_audio) > 0:
                first_pcm = conn.asr_audio[-1] if conn.asr_audio else b""
                await self._send_audio_frame(first_pcm, STATUS_FIRST_FRAME)
                self.server_ready = True
                logger.bind(tag=TAG).info("First frame sent, recognition started")

                # Send buffered audio data
                for cached_pcm in conn.asr_audio[-10:]:
                    try:
                        await self._send_audio_frame(cached_pcm, STATUS_CONTINUE_FRAME)
                    except Exception as e:
                        logger.bind(tag=TAG).info(f"Error while sending buffered audio data: {e}")
                        break

        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to establish ASR connection: {str(e)}")
            if hasattr(e, "__cause__") and e.__cause__:
                logger.bind(tag=TAG).error(f"Error cause: {str(e.__cause__)}")
            if self.asr_ws:
                await self.asr_ws.close()
                self.asr_ws = None
            self.is_processing = False
            raise

    async def _send_audio_frame(self, audio_data: bytes, status: int):
        """Send an audio frame"""
        if not self.asr_ws:
            return

        audio_b64 = base64.b64encode(audio_data).decode("utf-8")

        frame_data = {
            "header": {"status": status, "app_id": self.app_id},
            "parameter": {"iat": self.iat_params},
            "payload": {
                "audio": {"audio": audio_b64, "sample_rate": 16000, "encoding": "raw"}
            },
        }

        await self.asr_ws.send(json.dumps(frame_data, ensure_ascii=False))

    async def _forward_results(self, conn: "ConnectionHandler"):
        """Forward recognition results"""
        try:
            while not conn.stop_event.is_set():
                try:
                    response = await asyncio.wait_for(self.asr_ws.recv(), timeout=60)
                    result = json.loads(response)
                    logger.bind(tag=TAG).debug(f"Received ASR result: {result}")

                    header = result.get("header", {})
                    payload = result.get("payload", {})
                    code = header.get("code", 0)
                    status = header.get("status", 0)

                    if code != 0:
                        logger.bind(tag=TAG).error(
                            f"Recognition error, error code: {code}, message: {header.get('message', '')}"
                        )
                        if code in [10114, 10160]:  # Connection issue
                            break
                        continue

                    # Handle recognition results
                    if payload and "result" in payload:
                        text_data = payload["result"]["text"]
                        if text_data:
                            # Decode base64 text
                            decoded_text = base64.b64decode(text_data).decode("utf-8")
                            text_json = json.loads(decoded_text)
                            # Extract text content
                            text_ws = text_json.get("ws", [])
                            for i in text_ws:
                                for j in i.get("cw", []):
                                    w = j.get("w", "")
                                    self.text += w

                    if status == 2:
                        logger.bind(tag=TAG).debug("Received final recognition result, triggering processing")
                        await self.handle_voice_stop(conn, conn.asr_audio)
                        break

                except asyncio.TimeoutError:
                    logger.bind(tag=TAG).error("Timed out receiving results")
                    break
                except websockets.ConnectionClosed:
                    logger.bind(tag=TAG).info("ASR service connection closed")
                    self.is_processing = False
                    break
                except Exception as e:
                    logger.bind(tag=TAG).error(f"Error while processing ASR result: {str(e)}")
                    if hasattr(e, "__cause__") and e.__cause__:
                        logger.bind(tag=TAG).error(f"Error cause: {str(e.__cause__)}")
                    self.is_processing = False
                    break

        except Exception as e:
            logger.bind(tag=TAG).error(f"ASR result forwarding task error: {str(e)}")
            if hasattr(e, "__cause__") and e.__cause__:
                logger.bind(tag=TAG).error(f"Error cause: {str(e.__cause__)}")
        finally:
            # Clean up connection resources
            await self._cleanup()
            conn.reset_audio_states()

    async def handle_voice_stop(
        self, conn: "ConnectionHandler", asr_audio_task: List[bytes]
    ):
        """Handle speech stop: send the last frame and process the recognition result"""
        try:
            # Send the last frame first to signal end of audio
            if self.asr_ws and self.is_processing:
                try:
                    await self._send_audio_frame(b"", STATUS_LAST_FRAME)
                    logger.bind(tag=TAG).debug(f"Stop request sent")

                    await asyncio.sleep(0.25)
                except Exception as e:
                    logger.bind(tag=TAG).error(f"Failed to send stop request: {e}")

            await super().handle_voice_stop(conn, asr_audio_task)
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to handle speech stop: {e}")
            import traceback

            logger.bind(tag=TAG).debug(f"Exception details: {traceback.format_exc()}")

    def stop_ws_connection(self):
        if self.asr_ws:
            asyncio.create_task(self.asr_ws.close())
            self.asr_ws = None
        self.is_processing = False

    async def _send_stop_request(self):
        """Send stop-recognition request (without closing the connection)"""
        if self.asr_ws:
            try:
                # Stop sending audio first
                self.is_processing = False
                await self._send_audio_frame(b"", STATUS_LAST_FRAME)
                logger.bind(tag=TAG).debug("Stop request sent")
            except Exception as e:
                logger.bind(tag=TAG).error(f"Failed to send stop request: {e}")

    async def _cleanup(self):
        """Clean up resources (close the connection)"""
        logger.bind(tag=TAG).debug(
            f"Starting ASR session cleanup | current state: processing={self.is_processing}, server_ready={self.server_ready}"
        )

        # Reset state
        self.is_processing = False
        self.server_ready = False
        logger.bind(tag=TAG).debug("ASR state reset")

        # Close the connection
        if self.asr_ws:
            try:
                logger.bind(tag=TAG).debug("Closing WebSocket connection")
                await asyncio.wait_for(self.asr_ws.close(), timeout=2.0)
                logger.bind(tag=TAG).debug("WebSocket connection closed")
            except Exception as e:
                logger.bind(tag=TAG).error(f"Failed to close WebSocket connection: {e}")
            finally:
                self.asr_ws = None

        # Clear task references
        self.forward_task = None

        logger.bind(tag=TAG).debug("ASR session cleanup completed")

    async def speech_to_text(self, opus_data, session_id, artifacts=None):
        """Get the recognition result"""
        result = self.text
        self.text = ""
        return result, None

    async def close(self):
        """Resource cleanup method"""
        if self.asr_ws:
            await self.asr_ws.close()
            self.asr_ws = None
        if self.forward_task:
            self.forward_task.cancel()
            try:
                await self.forward_task
            except asyncio.CancelledError:
                pass
            self.forward_task = None
        self.is_processing = False

