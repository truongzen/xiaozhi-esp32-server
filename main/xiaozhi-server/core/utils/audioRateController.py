import time
import asyncio
from collections import deque
from config.logger import setup_logging

TAG = __name__
logger = setup_logging()


class AudioRateController:
    """
    Audio rate controller - precisely paces audio sending by 60 ms frame duration
    Avoids accumulated timing drift under high concurrency
    """

    def __init__(self, frame_duration=60, send_delay=0):
        """
        Args:
            frame_duration: duration of a single audio frame (ms), default 60 ms
            send_delay: custom send interval (ms).
                        0 = pace by frame_duration;
                        >0 = pace by send_delay (no enqueue delay on the main thread, no pre-buffering)
        """
        # Pacing interval (ms): the configured value if send_delay > 0, otherwise frame_duration
        self.interval_ms = send_delay if send_delay > 0 else frame_duration
        self.queue = deque()
        self.play_position = 0  # virtual playback position (ms)
        self.start_timestamp = None  # start timestamp (read-only)
        self.pending_send_task = None
        self.logger = logger
        self.queue_empty_event = asyncio.Event()  # queue-empty event
        self.queue_empty_event.set()  # initially empty
        self.queue_has_data_event = asyncio.Event()  # queue-has-data event
        self._last_queue_empty_time = 0  # time the queue last became empty (seconds)

    def reset(self):
        """Reset controller state"""
        if self.pending_send_task and not self.pending_send_task.done():
            self.pending_send_task.cancel()
            # A cancelled task is cleaned up on the next event loop iteration; no need to block

        self.queue.clear()
        self.play_position = 0
        self.start_timestamp = None  # set by the first audio packet
        self._last_queue_empty_time = 0  # reset time
        # Update related events
        self.queue_empty_event.set()
        self.queue_has_data_event.clear()

    def add_audio(self, opus_packet):
        """Add an audio packet to the queue"""
        # If the queue was empty, adjust the timestamp to keep playback time continuous
        # so audio added while waiting for a tool call is not played early
        # A very short gap (<1 frame) is normal streaming and needs no reset
        if len(self.queue) == 0 and self.play_position > 0:
            elapsed_since_empty = (time.monotonic() - self._last_queue_empty_time) * 1000
            # Only a gap longer than one frame counts as a real "pause and resume"
            if elapsed_since_empty >= self.interval_ms:
                self.start_timestamp = time.monotonic() - (self.play_position / 1000)
                self.logger.bind(tag=TAG).debug(
                    f"Queue resumed from empty, resetting timestamp, current play position: {self.play_position}ms, gap: {elapsed_since_empty:.0f}ms"
                )

        self.queue.append(("audio", opus_packet))
        # Update related events
        self.queue_empty_event.clear()
        self.queue_has_data_event.set()

    def add_message(self, message_callback):
        """
        Add a message to the queue (sent immediately, takes no playback time)

        Args:
            message_callback: message send callback async def()
        """
        if len(self.queue) == 0 and self.play_position > 0:
            elapsed_since_empty = (time.monotonic() - self._last_queue_empty_time) * 1000
            if elapsed_since_empty >= self.interval_ms:
                self.start_timestamp = time.monotonic() - (self.play_position / 1000)
                self.logger.bind(tag=TAG).debug(
                    f"Queue resumed from empty, resetting timestamp, current play position: {self.play_position}ms, gap: {elapsed_since_empty:.0f}ms"
                )

        self.queue.append(("message", message_callback))
        # Update related events
        self.queue_empty_event.clear()
        self.queue_has_data_event.set()

    def _get_elapsed_ms(self):
        """Get elapsed time (ms)"""
        if self.start_timestamp is None:
            return 0
        return (time.monotonic() - self.start_timestamp) * 1000

    async def check_queue(self, send_audio_callback):
        """
        Check the queue and send audio/messages on time

        Args:
            send_audio_callback: callback for sending audio async def(opus_packet)
        """
        while self.queue:
            item = self.queue[0]
            item_type = item[0]

            if item_type == "message":
                # Message type: send immediately, takes no playback time
                _, message_callback = item
                self.queue.popleft()
                try:
                    await message_callback()
                except Exception as e:
                    self.logger.bind(tag=TAG).error(f"Failed to send message: {e}")
                    raise

            elif item_type == "audio":
                if self.start_timestamp is None:
                    self.start_timestamp = time.monotonic()

                _, opus_packet = item

                # Loop until the send time arrives
                while True:
                    # Compute the time difference
                    elapsed_ms = self._get_elapsed_ms()
                    output_ms = self.play_position

                    if elapsed_ms < output_ms:
                        # Not yet time to send; compute the wait
                        wait_ms = output_ms - elapsed_ms

                        # Wait then check again (interruptible)
                        try:
                            await asyncio.sleep(wait_ms / 1000)
                        except asyncio.CancelledError:
                            self.logger.bind(tag=TAG).debug("Audio send task cancelled")
                            raise
                        # Re-check the time after waiting (back to while True)
                    else:
                        # Time reached, exit the wait loop
                        break

                # Time reached, dequeue and send
                self.queue.popleft()
                self.play_position += self.interval_ms
                try:
                    await send_audio_callback(opus_packet)
                except Exception as e:
                    self.logger.bind(tag=TAG).error(f"Failed to send audio: {e}")
                    raise

        # Clear the event after the queue is drained
        self.queue_empty_event.set()
        self.queue_has_data_event.clear()
        self._last_queue_empty_time = time.monotonic()  # record when the queue became empty

    def start_sending(self, send_audio_callback):
        """
        Start the async send task

        Args:
            send_audio_callback: callback for sending audio

        Returns:
            asyncio.Task: the send task
        """

        async def _send_loop():
            try:
                while True:
                    # Wait on the queue-data event instead of polling the CPU
                    await self.queue_has_data_event.wait()

                    await self.check_queue(send_audio_callback)
            except asyncio.CancelledError:
                self.logger.bind(tag=TAG).debug("Audio send loop stopped")
            except Exception as e:
                self.logger.bind(tag=TAG).error(f"Audio send loop error: {e}")

        self.pending_send_task = asyncio.create_task(_send_loop())
        return self.pending_send_task

    def stop_sending(self):
        """Stop the send task"""
        if self.pending_send_task and not self.pending_send_task.done():
            self.pending_send_task.cancel()
            self.logger.bind(tag=TAG).debug("Cancelled audio send task")
