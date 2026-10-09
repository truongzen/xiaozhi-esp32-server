from abc import abstractmethod, ABC
from typing import Dict, Any

from core.handle.textMessageType import TextMessageType

TAG = __name__


class TextMessageHandler(ABC):
    """Abstract base class for message handlers"""

    @abstractmethod
    async def handle(self, conn, msg_json: Dict[str, Any]) -> None:
        """Abstract method that handles a message"""
        pass

    @property
    @abstractmethod
    def message_type(self) -> TextMessageType:
        """Return the handled message type"""
        pass
