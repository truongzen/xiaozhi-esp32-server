"""
Opus encoding utility
Encode PCM audio data to Opus
"""

import logging
import traceback
import numpy as np
from opuslib_next import Encoder
from opuslib_next import constants
from typing import Optional, Callable, Any

class OpusEncoderUtils:
    """PCM-to-Opus encoder"""

    def __init__(self, sample_rate: int, channels: int, frame_size_ms: int):
        """
        Initialize the Opus encoder

        Args:
            sample_rate: sample rate (Hz)
            channels: number of channels (1=mono, 2=stereo)
            frame_size_ms: frame size (ms)
        """
        self.sample_rate = sample_rate
        self.channels = channels
        self.frame_size_ms = frame_size_ms
        # Samples per frame = sample rate * frame size (ms) / 1000
        self.frame_size = (sample_rate * frame_size_ms) // 1000
        # Total frame size = samples per frame * channels
        self.total_frame_size = self.frame_size * channels

        # Bitrate and complexity settings
        self.bitrate = 24000  # bps
        self.complexity = 10  # highest quality

        # Initialize the buffer as empty
        self.buffer = np.array([], dtype=np.int16)

        try:
            # Create the Opus encoder
            self.encoder = Encoder(
                sample_rate, channels, constants.APPLICATION_AUDIO  # audio-optimized mode
            )
            self.encoder.bitrate = self.bitrate
            self.encoder.complexity = self.complexity
            self.encoder.signal = constants.SIGNAL_VOICE  # voice signal optimization
        except Exception as e:
            logging.error(f"Failed to initialize Opus encoder: {e}")
            raise RuntimeError("Initialization failed") from e

    def reset_state(self):
        """Reset encoder state"""
        self.encoder.reset_state()
        self.buffer = np.array([], dtype=np.int16)

    def encode_pcm_to_opus_stream(self, pcm_data: bytes, end_of_stream: bool, callback: Callable[[Any], Any]):
        """
        Encode PCM data to Opus in a streaming fashion

        Args:
            pcm_data: PCM bytes
            end_of_stream: whether this is the end of the stream,
            callback: Opus handler callback

        Returns:
            List of Opus packets
        """
        # Convert bytes to a short array
        new_samples = self._convert_bytes_to_shorts(pcm_data)

        # Validate PCM data
        self._validate_pcm_data(new_samples)

        # Append new data to the buffer
        self.buffer = np.append(self.buffer, new_samples)

        offset = 0

        # Process all complete frames
        while offset <= len(self.buffer) - self.total_frame_size:
            frame = self.buffer[offset : offset + self.total_frame_size]
            output = self._encode(frame)
            if output:
                callback(output)
            offset += self.total_frame_size

        # Keep unprocessed samples
        self.buffer = self.buffer[offset:]

        # Process remaining data at end of stream
        if end_of_stream and len(self.buffer) > 0:
            # Create the last frame and pad with zeros
            last_frame = np.zeros(self.total_frame_size, dtype=np.int16)
            last_frame[: len(self.buffer)] = self.buffer

            output = self._encode(last_frame)
            if output:
                callback(output)
            self.buffer = np.array([], dtype=np.int16)

    def _encode(self, frame: np.ndarray) -> Optional[bytes]:
        """Encode one frame of audio"""
        try:
            # Encoder released, skip encoding
            if not hasattr(self, 'encoder') or self.encoder is None:
                return None
            # Convert the numpy array to bytes
            frame_bytes = frame.tobytes()
            # opuslib requires the input byte count to be a multiple of channels*2
            encoded = self.encoder.encode(frame_bytes, self.frame_size)
            return encoded
        except Exception as e:
            logging.error(f"Opus encoding failed: {e}")
            traceback.print_exc()
            return None

    def _convert_bytes_to_shorts(self, bytes_data: bytes) -> np.ndarray:
        """Convert a byte array to a short array (16-bit PCM)"""
        # Assume input is little-endian 16-bit PCM
        return np.frombuffer(bytes_data, dtype=np.int16)

    def _validate_pcm_data(self, pcm_shorts: np.ndarray) -> None:
        """Check whether PCM data is valid"""
        # 16-bit PCM range is -32768 to 32767
        if np.any((pcm_shorts < -32768) | (pcm_shorts > 32767)):
            invalid_samples = pcm_shorts[(pcm_shorts < -32768) | (pcm_shorts > 32767)]
            logging.warning(f"Found invalid PCM samples: {invalid_samples[:5]}...")
            # In practice you may clip instead of raising
            # np.clip(pcm_shorts, -32768, 32767, out=pcm_shorts)

    def close(self):
        """Close the encoder and release resources"""
        if hasattr(self, 'encoder') and self.encoder:
            try:
                del self.encoder
                self.encoder = None
            except Exception as e:
                logging.error(f"Error releasing Opus encoder: {e}")