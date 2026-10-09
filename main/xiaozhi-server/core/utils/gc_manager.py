"""
Global GC management module
Runs garbage collection periodically to avoid GIL contention caused by frequent GC
"""

import gc
import asyncio
import threading
from config.logger import setup_logging

TAG = __name__
logger = setup_logging()


class GlobalGCManager:
    """Global garbage collection manager"""

    def __init__(self, interval_seconds=300):
        """
        Initialize the GC manager

        Args:
            interval_seconds: GC interval in seconds, default 300 (5 minutes)
        """
        self.interval_seconds = interval_seconds
        self._task = None
        self._stop_event = asyncio.Event()
        self._lock = threading.Lock()

    async def start(self):
        """Start the periodic GC task"""
        if self._task is not None:
            logger.bind(tag=TAG).warning("GC manager is already running")
            return

        logger.bind(tag=TAG).info(f"Starting global GC manager, interval {self.interval_seconds}s")
        self._stop_event.clear()
        self._task = asyncio.create_task(self._gc_loop())

    async def stop(self):
        """Stop the periodic GC task"""
        if self._task is None:
            return

        logger.bind(tag=TAG).info("Stopped global GC manager")
        self._stop_event.set()

        if self._task and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass

        self._task = None

    async def _gc_loop(self):
        """GC loop task"""
        try:
            while not self._stop_event.is_set():
                # Wait for the configured interval
                try:
                    await asyncio.wait_for(
                        self._stop_event.wait(), timeout=self.interval_seconds
                    )
                    # Exit the loop if stop_event is set
                    break
                except asyncio.TimeoutError:
                    # A timeout means it is time to run GC
                    pass

                # Run GC
                await self._run_gc()

        except asyncio.CancelledError:
            logger.bind(tag=TAG).info("GC loop task cancelled")
            raise
        except Exception as e:
            logger.bind(tag=TAG).error(f"GC loop task error: {e}")
        finally:
            logger.bind(tag=TAG).info("GC loop task exited")

    async def _run_gc(self):
        """Run garbage collection"""
        try:
            # Run GC in a thread pool to avoid blocking the event loop
            loop = asyncio.get_running_loop()

            def do_gc():
                with self._lock:
                    before = len(gc.get_objects())
                    collected = gc.collect()
                    after = len(gc.get_objects())
                    return before, collected, after

            before, collected, after = await loop.run_in_executor(None, do_gc)
            logger.bind(tag=TAG).debug(
                f"Global GC complete - collected objects: {collected}, "
                f"object count: {before} -> {after}"
            )
        except Exception as e:
            logger.bind(tag=TAG).error(f"Error running GC: {e}")


# Global singleton
_gc_manager_instance = None


def get_gc_manager(interval_seconds=300):
    """
    Get the global GC manager instance (singleton)

    Args:
        interval_seconds: GC interval in seconds, default 300 (5 minutes)

    Returns:
        GlobalGCManager instance
    """
    global _gc_manager_instance
    if _gc_manager_instance is None:
        _gc_manager_instance = GlobalGCManager(interval_seconds)
    return _gc_manager_instance
