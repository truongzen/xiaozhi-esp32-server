import http.client
import json
import asyncio
from typing import Optional, Tuple, List
import os
import uuid
import hmac
import hashlib
import base64
import requests
from urllib import parse
import time
from datetime import datetime
from config.logger import setup_logging
from core.providers.asr.base import ASRProviderBase
from core.providers.asr.dto.dto import InterfaceType

TAG = __name__
logger = setup_logging()


class AccessToken:
    @staticmethod
    def _encode_text(text):
        encoded_text = parse.quote_plus(text)
        return encoded_text.replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    @staticmethod
    def _encode_dict(dic):
        keys = dic.keys()
        dic_sorted = [(key, dic[key]) for key in sorted(keys)]
        encoded_text = parse.urlencode(dic_sorted)
        return encoded_text.replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    @staticmethod
    def create_token(access_key_id, access_key_secret):
        parameters = {
            "AccessKeyId": access_key_id,
            "Action": "CreateToken",
            "Format": "JSON",
            "RegionId": "cn-shanghai",
            "SignatureMethod": "HMAC-SHA1",
            "SignatureNonce": str(uuid.uuid1()),
            "SignatureVersion": "1.0",
            "Timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "Version": "2019-02-28",
        }
        # Build the canonicalized query string
        query_string = AccessToken._encode_dict(parameters)
        # print('Canonicalized query string: %s' % query_string)
        # Build the string to sign
        string_to_sign = (
            "GET"
            + "&"
            + AccessToken._encode_text("/")
            + "&"
            + AccessToken._encode_text(query_string)
        )
        # print('String to sign: %s' % string_to_sign)
        # Compute the signature
        secreted_string = hmac.new(
            bytes(access_key_secret + "&", encoding="utf-8"),
            bytes(string_to_sign, encoding="utf-8"),
            hashlib.sha1,
        ).digest()
        signature = base64.b64encode(secreted_string)
        # print('Signature: %s' % signature)
        # URL-encode
        signature = AccessToken._encode_text(signature)
        # print('URL-encoded signature: %s' % signature)
        # Call the service
        full_url = "http://nls-meta.cn-shanghai.aliyuncs.com/?Signature=%s&%s" % (
            signature,
            query_string,
        )
        # print('url: %s' % full_url)
        # Submit HTTP GET request
        response = requests.get(full_url)
        if response.ok:
            root_obj = response.json()
            key = "Token"
            if key in root_obj:
                token = root_obj[key]["Id"]
                expire_time = root_obj[key]["ExpireTime"]
                return token, expire_time
        # print(response.text)
        return None, None


class ASRProvider(ASRProviderBase):
    def __init__(self, config: dict, delete_audio_file: bool):
        super().__init__()
        self.interface_type = InterfaceType.NON_STREAM
        """Alibaba Cloud ASR initialization"""
        # Added null-check logic
        self.access_key_id = config.get("access_key_id")
        self.access_key_secret = config.get("access_key_secret")

        self.app_key = config.get("appkey")
        self.host = "nls-gateway-cn-shanghai.aliyuncs.com"
        self.base_url = f"https://{self.host}/stream/v1/asr"
        self.sample_rate = 16000
        self.format = "wav"
        self.output_dir = config.get("output_dir", "./audio_output")
        self.delete_audio_file = delete_audio_file

        if self.access_key_id and self.access_key_secret:
            # Use the key pair to generate a temporary token
            self._refresh_token()
        else:
            # Use the pre-generated long-term token
            self.token = config.get("token")
            self.expire_time = None

        # Ensure the output directory exists
        os.makedirs(self.output_dir, exist_ok=True)

    def _refresh_token(self):
        """Refresh the token and record its expiry time"""
        if self.access_key_id and self.access_key_secret:
            self.token, expire_time_str = AccessToken.create_token(
                self.access_key_id, self.access_key_secret
            )
            if not expire_time_str:
                raise ValueError("Unable to obtain a valid token expiry time")

            try:
                # Convert to string uniformly
                expire_str = str(expire_time_str).strip()

                if expire_str.isdigit():
                    expire_time = datetime.fromtimestamp(int(expire_str))
                else:
                    expire_time = datetime.strptime(expire_str, "%Y-%m-%dT%H:%M:%SZ")
                self.expire_time = expire_time.timestamp() - 60
            except Exception as e:
                raise ValueError(f"Invalid expiry time format: {expire_str}") from e

        else:
            self.expire_time = None

        if not self.token:
            raise ValueError("Unable to obtain a valid access Token")

    def _is_token_expired(self):
        """Check whether the token has expired"""
        if not self.expire_time:
            return False  # Long-term tokens do not expire
        # Added debug logging
        # current_time = time.time()
        # remaining = self.expire_time - current_time
        # print(f"Token expiry check: current time {datetime.fromtimestamp(current_time)} | "
        #              f"Expiry time {datetime.fromtimestamp(self.expire_time)} | "
        #              f"remaining {remaining:.2f}s")
        return time.time() > self.expire_time

    def _construct_request_url(self) -> str:
        """Build the request URL with parameters"""
        request = f"{self.base_url}?appkey={self.app_key}"
        request += f"&format={self.format}"
        request += f"&sample_rate={self.sample_rate}"
        request += "&enable_punctuation_prediction=true"
        request += "&enable_inverse_text_normalization=true"
        request += "&enable_voice_detection=false"
        return request

    async def _send_request(self, pcm_data: bytes) -> Optional[str]:
        """Send the request to the Alibaba Cloud ASR service"""
        try:
            # Set HTTP headers
            headers = {
                "X-NLS-Token": self.token,
                "Content-type": "application/octet-stream",
                "Content-Length": str(len(pcm_data)),
            }

            # Create the connection and send the request
            conn = http.client.HTTPSConnection(self.host)
            request_url = self._construct_request_url()

            loop = asyncio.get_event_loop()
            await loop.run_in_executor(
                None,
                lambda: conn.request(
                    method="POST", url=request_url, body=pcm_data, headers=headers
                ),
            )

            # Get the response
            response = await loop.run_in_executor(None, conn.getresponse)
            body = await loop.run_in_executor(None, response.read)
            conn.close()

            # Parse the response
            try:
                body_json = json.loads(body)
                status = body_json.get("status")

                if status == 20000000:
                    result = body_json.get("result", "")
                    logger.bind(tag=TAG).debug(f"ASR result: {result}")
                    return result
                else:
                    logger.bind(tag=TAG).error(f"ASR failed, status code: {status}")
                    return None

            except ValueError:
                logger.bind(tag=TAG).error("Response is not in JSON format")
                return None

        except Exception as e:
            logger.bind(tag=TAG).error(f"ASR request failed: {e}", exc_info=True)
            return None

    async def speech_to_text(
        self, opus_data: List[bytes], session_id: str, artifacts=None
    ) -> Tuple[Optional[str], Optional[str]]:
        """Convert speech data to text"""
        if self._is_token_expired():
            logger.warning("Token expired, refreshing automatically...")
            self._refresh_token()

        try:
            if artifacts is None:
                return "", None
            # Send the request and get the text
            text = await self._send_request(artifacts.pcm_bytes)

            if text:
                return text, artifacts.file_path

            return "", artifacts.file_path

        except Exception as e:
            logger.bind(tag=TAG).error(f"Speech recognition failed: {e}", exc_info=True)
            return "", None
