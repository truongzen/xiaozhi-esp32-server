import json
import copy
from aiohttp import web
from config.logger import setup_logging
from core.api.base_handler import BaseHandler
from core.utils.util import get_vision_url, is_valid_image_file
from core.utils.vllm import create_instance
from config.config_loader import get_private_config_from_api
from core.utils.auth import AuthToken
import base64
from typing import Tuple, Optional
from plugins_func.register import Action

TAG = __name__

# Maximum file size is 5MB
MAX_FILE_SIZE = 5 * 1024 * 1024


class VisionHandler(BaseHandler):
    def __init__(self, config: dict):
        super().__init__(config)
        # Initialize the authentication helper
        self.auth = AuthToken(config["server"]["auth_key"])

    def _create_error_response(self, message: str) -> dict:
        """Create a unified error response"""
        return {"success": False, "message": message}

    def _verify_auth_token(self, request) -> Tuple[bool, Optional[str]]:
        """Verify the authentication token"""
        # Test mode: allow specific test tokens or skip verification
        auth_header = request.headers.get("Authorization", "")
        client_id = request.headers.get("Client-Id", "")

        # Allow test clients to skip authentication
        if client_id == "web_test_client":
            device_id = request.headers.get("Device-Id", "test_device")
            return True, device_id

        if not auth_header.startswith("Bearer "):
            return False, None

        token = auth_header[7:]  # Strip the "Bearer " prefix
        return self.auth.verify_token(token)

    async def handle_post(self, request):
        """Handle an MCP Vision POST request"""
        response = None  # Initialize the response variable
        try:
            # Verify the token
            is_valid, token_device_id = self._verify_auth_token(request)
            if not is_valid:
                response = web.Response(
                    text=json.dumps(
                        self._create_error_response("Invalid or expired authentication token")
                    ),
                    content_type="application/json",
                    status=401,
                )
                return response

            # Get the request headers
            device_id = request.headers.get("Device-Id", "")
            client_id = request.headers.get("Client-Id", "")
            if device_id != token_device_id:
                raise ValueError("Device ID does not match the token")
            # Parse the multipart/form-data request
            reader = await request.multipart()

            # Read the question field
            question_field = await reader.next()
            if question_field is None:
                raise ValueError("Missing question field")
            question = await question_field.text()
            self.logger.bind(tag=TAG).debug(f"Question: {question}")

            # Read the image file
            image_field = await reader.next()
            if image_field is None:
                raise ValueError("Missing image file")

            # Read the image data
            image_data = await image_field.read()
            if not image_data:
                raise ValueError("Image data is empty")

            # Check the file size
            if len(image_data) > MAX_FILE_SIZE:
                raise ValueError(
                    f"Image size exceeds the limit; maximum allowed is {MAX_FILE_SIZE/1024/1024}MB"
                )

            # Check the file format
            if not is_valid_image_file(image_data):
                raise ValueError(
                    "Unsupported file format. Please upload a valid image file (JPEG, PNG, GIF, BMP, TIFF and WEBP are supported)"
                )

            # Convert the image to base64
            image_base64 = base64.b64encode(image_data).decode("utf-8")

            # If the control console is enabled, get the model configuration from it
            current_config = copy.deepcopy(self.config)
            read_config_from_api = current_config.get("read_config_from_api", False)
            if read_config_from_api:
                current_config = await get_private_config_from_api(
                    current_config,
                    device_id,
                    client_id,
                )

            select_vllm_module = current_config["selected_module"].get("VLLM")
            if not select_vllm_module:
                raise ValueError("You have not set a default vision analysis module yet")

            vllm_type = (
                select_vllm_module
                if "type" not in current_config["VLLM"][select_vllm_module]
                else current_config["VLLM"][select_vllm_module]["type"]
            )

            if not vllm_type:
                raise ValueError(f"Unable to find the provider for VLLM module {vllm_type}")

            vllm = create_instance(
                vllm_type, current_config["VLLM"][select_vllm_module]
            )

            result = vllm.response(question, image_base64)

            return_json = {
                "success": True,
                "action": Action.RESPONSE.name,
                "response": result,
            }

            response = web.Response(
                text=json.dumps(return_json, separators=(",", ":")),
                content_type="application/json",
            )
        except ValueError as e:
            self.logger.bind(tag=TAG).error(f"MCP Vision POST request error: {e}")
            return_json = self._create_error_response(str(e))
            response = web.Response(
                text=json.dumps(return_json, separators=(",", ":")),
                content_type="application/json",
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"MCP Vision POST request error: {e}")
            return_json = self._create_error_response("An error occurred while processing the request")
            response = web.Response(
                text=json.dumps(return_json, separators=(",", ":")),
                content_type="application/json",
            )
        finally:
            if response:
                self._add_cors_headers(response)
            return response

    async def handle_get(self, request):
        """Handle an MCP Vision GET request"""
        try:
            vision_explain = get_vision_url(self.config)
            if vision_explain and len(vision_explain) > 0 and "null" != vision_explain:
                message = (
                    f"MCP Vision interface is running normally; the vision explain interface address is: {vision_explain}"
                )
            else:
                message = "MCP Vision interface is not working properly. Open the .config.yaml file in the data directory, find [server.vision_explain] and set the address"

            response = web.Response(text=message, content_type="text/plain")
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"MCP Vision GET request error: {e}")
            return_json = self._create_error_response("Internal server error")
            response = web.Response(
                text=json.dumps(return_json, separators=(",", ":")),
                content_type="application/json",
            )
        finally:
            self._add_cors_headers(response)
            return response
