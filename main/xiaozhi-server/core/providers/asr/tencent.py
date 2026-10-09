import base64
import hashlib
import hmac
import json
import time
from datetime import datetime, timezone
import os
from typing import Optional, Tuple, List
from core.providers.asr.dto.dto import InterfaceType
import requests
from core.providers.asr.base import ASRProviderBase
from config.logger import setup_logging

TAG = __name__
logger = setup_logging()


class ASRProvider(ASRProviderBase):
    API_URL = "https://asr.tencentcloudapi.com"
    API_VERSION = "2019-06-14"
    FORMAT = "pcm"  # Supported audio formats: pcm, wav, mp3

    def __init__(self, config: dict, delete_audio_file: bool = True):
        super().__init__()
        self.interface_type = InterfaceType.NON_STREAM
        self.secret_id = config.get("secret_id")
        self.secret_key = config.get("secret_key")
        self.output_dir = config.get("output_dir")
        self.delete_audio_file = delete_audio_file

        # Ensure the output directory exists
        os.makedirs(self.output_dir, exist_ok=True)

    async def speech_to_text(
        self, opus_data: List[bytes], session_id: str, artifacts=None
    ) -> Tuple[Optional[str], Optional[str]]:
        """Convert speech data to text"""
        if not opus_data:
            logger.bind(tag=TAG).warning("Audio data is empty!")
            return None, None

        try:
            # Check whether the configuration is set
            if not self.secret_id or not self.secret_key:
                logger.bind(tag=TAG).error("Tencent Cloud speech recognition is not configured, cannot perform recognition")
                return None, None

            if artifacts is None:
                return "", None

            # Encode audio data as Base64
            base64_audio = base64.b64encode(artifacts.pcm_bytes).decode("utf-8")

            # Build request body
            request_body = self._build_request_body(base64_audio)

            # Get authentication headers
            timestamp, authorization = self._get_auth_headers(request_body)

            # Send request
            start_time = time.time()
            result = self._send_request(request_body, timestamp, authorization)

            if result:
                logger.bind(tag=TAG).debug(
                    f"Tencent Cloud speech recognition time: {time.time() - start_time:.3f}s | result: {result}"
                )

            return result, artifacts.file_path

        except Exception as e:
            logger.bind(tag=TAG).error(f"Error while processing audio! {e}", exc_info=True)
            return None, None

    def _build_request_body(self, base64_audio: str) -> str:
        """Build request body"""
        request_map = {
            "ProjectId": 0,
            "SubServiceType": 2,  # Single-sentence recognition
            "EngSerViceType": "16k_zh",  # Mandarin Chinese, general
            "SourceType": 1,  # Audio source is a voice file
            "VoiceFormat": self.FORMAT,  # Audio format
            "Data": base64_audio,  # Base64-encoded audio data
            "DataLen": len(base64_audio),  # Data length
        }
        return json.dumps(request_map)

    def _get_auth_headers(self, request_body: str) -> Tuple[str, str]:
        """Get authentication headers"""
        try:
            # Get the current UTC timestamp
            now = datetime.now(timezone.utc)
            timestamp = str(int(now.timestamp()))
            date = now.strftime("%Y-%m-%d")

            # Service name must be "asr"
            service = "asr"

            # Build the credential scope
            credential_scope = f"{date}/{service}/tc3_request"

            # Use the TC3-HMAC-SHA256 signature method
            algorithm = "TC3-HMAC-SHA256"

            # Build the canonical request string
            http_request_method = "POST"
            canonical_uri = "/"
            canonical_query_string = ""

            # Note: headers must be sorted in ascending ASCII order, with keys and values lowercased
            # Must include the content-type and host headers
            content_type = "application/json; charset=utf-8"
            host = "asr.tencentcloudapi.com"
            action = "SentenceRecognition"  # API action name

            # Build canonical headers; mind the order and format
            canonical_headers = (
                f"content-type:{content_type.lower()}\n"
                + f"host:{host.lower()}\n"
                + f"x-tc-action:{action.lower()}\n"
            )

            signed_headers = "content-type;host;x-tc-action"

            # Request body hash
            payload_hash = self._sha256_hex(request_body)

            # Build the canonical request string
            canonical_request = (
                f"{http_request_method}\n"
                + f"{canonical_uri}\n"
                + f"{canonical_query_string}\n"
                + f"{canonical_headers}\n"
                + f"{signed_headers}\n"
                + f"{payload_hash}"
            )

            # Compute the hash of the canonical request
            hashed_canonical_request = self._sha256_hex(canonical_request)

            # Build the string to sign
            string_to_sign = (
                f"{algorithm}\n"
                + f"{timestamp}\n"
                + f"{credential_scope}\n"
                + f"{hashed_canonical_request}"
            )

            # Compute the signing key
            secret_date = self._hmac_sha256(f"TC3{self.secret_key}", date)
            secret_service = self._hmac_sha256(secret_date, service)
            secret_signing = self._hmac_sha256(secret_service, "tc3_request")

            # Compute the signature
            signature = self._bytes_to_hex(
                self._hmac_sha256(secret_signing, string_to_sign)
            )

            # Build the Authorization header
            authorization = (
                f"{algorithm} "
                + f"Credential={self.secret_id}/{credential_scope}, "
                + f"SignedHeaders={signed_headers}, "
                + f"Signature={signature}"
            )

            return timestamp, authorization

        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to generate authentication headers: {e}", exc_info=True)
            raise RuntimeError(f"Failed to generate authentication headers: {e}")

    def _send_request(
        self, request_body: str, timestamp: str, authorization: str
    ) -> Optional[str]:
        """Send the request to the Tencent Cloud API"""
        headers = {
            "Content-Type": "application/json; charset=utf-8",
            "Host": "asr.tencentcloudapi.com",
            "Authorization": authorization,
            "X-TC-Action": "SentenceRecognition",
            "X-TC-Version": self.API_VERSION,
            "X-TC-Timestamp": timestamp,
            "X-TC-Region": "ap-shanghai",
        }

        try:
            response = requests.post(self.API_URL, headers=headers, data=request_body)

            if not response.ok:
                raise IOError(f"request failed: {response.status_code} {response.reason}")

            response_json = response.json()

            # Check for errors
            if "Response" in response_json and "Error" in response_json["Response"]:
                error = response_json["Response"]["Error"]
                error_code = error["Code"]
                error_message = error["Message"]
                raise IOError(f"API returned error: {error_code}: {error_message}")

            # Extract the recognition result
            if "Response" in response_json and "Result" in response_json["Response"]:
                return response_json["Response"]["Result"]
            else:
                logger.bind(tag=TAG).warning(f"No recognition result in response: {response_json}")
                return ""

        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to send request: {e}", exc_info=True)
            return None

    def _sha256_hex(self, data: str) -> str:
        """Compute the SHA256 hash of a string"""
        digest = hashlib.sha256(data.encode("utf-8")).digest()
        return self._bytes_to_hex(digest)

    def _hmac_sha256(self, key, data: str) -> bytes:
        """Compute HMAC-SHA256"""
        if isinstance(key, str):
            key = key.encode("utf-8")

        return hmac.new(key, data.encode("utf-8"), hashlib.sha256).digest()

    def _bytes_to_hex(self, bytes_data: bytes) -> str:
        """Convert a byte array to a hex string"""
        return "".join(f"{b:02x}" for b in bytes_data)
