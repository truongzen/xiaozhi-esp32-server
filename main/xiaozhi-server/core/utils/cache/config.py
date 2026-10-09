"""
Cache configuration management
"""

from enum import Enum
from typing import Dict, Any, Optional
from dataclasses import dataclass
from .strategies import CacheStrategy


class CacheType(Enum):
    """Cache type enum"""

    LOCATION = "location"
    WEATHER = "weather"
    LUNAR = "lunar"
    INTENT = "intent"
    IP_INFO = "ip_info"
    CONFIG = "config"
    DEVICE_PROMPT = "device_prompt"
    VOICEPRINT_HEALTH = "voiceprint_health"  # voiceprint recognition health check
    AUDIO_DATA = "audio_data"  # audio data cache


@dataclass
class CacheConfig:
    """Cache configuration class"""

    strategy: CacheStrategy = CacheStrategy.TTL
    ttl: Optional[float] = 300  # default 5 minutes
    max_size: Optional[int] = 1000  # default max 1000 entries
    cleanup_interval: float = 60  # cleanup interval (seconds)

    @classmethod
    def for_type(cls, cache_type: CacheType) -> "CacheConfig":
        """Return the preset configuration for a cache type"""
        configs = {
            CacheType.LOCATION: cls(
                strategy=CacheStrategy.TTL, ttl=None, max_size=1000  # manual invalidation
            ),
            CacheType.IP_INFO: cls(
                strategy=CacheStrategy.TTL, ttl=86400, max_size=1000  # 24 hours
            ),
            CacheType.WEATHER: cls(
                strategy=CacheStrategy.TTL, ttl=28800, max_size=1000  # 8 hours
            ),
            CacheType.LUNAR: cls(
                strategy=CacheStrategy.TTL, ttl=2592000, max_size=365  # expires in 30 days
            ),
            CacheType.INTENT: cls(
                strategy=CacheStrategy.TTL_LRU, ttl=600, max_size=1000  # 10 minutes
            ),
            CacheType.CONFIG: cls(
                strategy=CacheStrategy.FIXED_SIZE, ttl=None, max_size=20  # manual invalidation
            ),
            CacheType.DEVICE_PROMPT: cls(
                strategy=CacheStrategy.TTL, ttl=None, max_size=1000  # manual invalidation
            ),
            CacheType.VOICEPRINT_HEALTH: cls(
                strategy=CacheStrategy.TTL, ttl=600, max_size=100  # expires in 10 minutes
            ),
            CacheType.AUDIO_DATA: cls(
                strategy=CacheStrategy.TTL, ttl=600, max_size=100  # expires in 10 minutes
            ),
        }
        return configs.get(cache_type, cls())
