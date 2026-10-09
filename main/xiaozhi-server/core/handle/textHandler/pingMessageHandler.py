import json
import time
from typing import Dict, Any

from core.handle.textMessageHandler import TextMessageHandler
from core.handle.textMessageType import TextMessageType

TAG = __name__


class PingMessageHandler(TextMessageHandler):
    """Ping message handler, used to keep the WebSocket connection alive"""

    @property
    def message_type(self) -> TextMessageType:
        return TextMessageType.PING

    async def handle(self, conn, msg_json: Dict[str, Any]) -> None:
        """
        Handle a PING message and send a PONG response
        Message format: {"type": "ping"}
        Args:
            conn: WebSocket connection object
            msg_json: JSON data of the PING message
        """
        # Check whether the WebSocket heartbeat is enabled
        enable_websocket_ping = conn.config.get("enable_websocket_ping", False)
        if not enable_websocket_ping:
            conn.logger.debug(f"WebSocket heartbeat not enabled, ignoring PING message")
            return

        try:
            conn.logger.debug(f"Received PING message, sending PONG response")
            conn.last_activity_time = time.time() * 1000
            # Build the PONG response message
            pong_message = {
                "type": "pong",
                "timestamp": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
            }

            # Send the PONG response
            await conn.websocket.send(json.dumps(pong_message))

        except Exception as e:
            conn.logger.error(f"Error while handling PING message: {e}")
