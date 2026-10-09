"""TTS (text-to-speech) pipeline tests.

Mock mode (default): builds the real TTS provider from data/.config.yaml config;
all external HTTP/SDK calls go through the shims injected by conftest, no real requests are sent.
Live mode (``RUN_LIVE_API_TESTS=1``): calls the real TTS provider.

Interface contract (core/providers/tts/base.py):
    text_to_speak(text, output_file) -> async, writes the synthesized result to output_file

Notes:
- The 3 construction tests do not depend on cloud services; only the 4th, test_tts_text_to_speak_writes_nonempty_file,
  is a real pipeline test that sends real HTTP.
- In mock mode the 4th test is lenient - the file existing is enough (the shim creates an empty file),
  size > 0 is not required. Live mode strictly checks size > 0.
"""
from __future__ import annotations

import asyncio
import os
import sys
import threading
from pathlib import Path
from types import SimpleNamespace

import pytest

from tests.conftest import (
    LIVE_API_TESTS,
    get_provider_config,
    get_provider_type,
    get_selected_provider_name,
)

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


def _build_provider():
    name = get_selected_provider_name("TTS")
    if not name:
        pytest.skip("selected_module.TTS is not set")
    cfg = get_provider_config("TTS", name)
    if not cfg:
        pytest.skip(f"TTS provider '{name}' config is missing")
    ptype = get_provider_type("TTS", name)
    if not ptype:
        pytest.skip(f"TTS '{name}' is missing the type field")
    from core.utils.tts import create_instance

    return create_instance(ptype, cfg, True)


def test_tts_provider_constructs_from_config() -> None:
    """Config should be able to instantiate a TTSProviderBase subclass."""
    provider = _build_provider()
    from core.providers.tts.base import TTSProviderBase

    assert isinstance(provider, TTSProviderBase), (
        f"Wrong TTS provider type: {type(provider).__name__}"
    )


def test_tts_provider_exposes_to_tts() -> None:
    """The provider must implement to_tts or to_tts_stream."""
    provider = _build_provider()
    has_sync = hasattr(provider, "to_tts") and callable(provider.to_tts)
    has_stream = hasattr(provider, "to_tts_stream") and callable(
        provider.to_tts_stream
    )
    assert has_sync or has_stream, (
        f"{type(provider).__name__} is missing the to_tts / to_tts_stream method"
    )


def test_tts_provider_has_output_dir() -> None:
    """The provider should have an output_file attribute (where synthesis results are written)."""
    provider = _build_provider()
    assert hasattr(provider, "output_file"), (
        f"{type(provider).__name__} is missing the output_file attribute"
    )
    assert isinstance(provider.output_file, str)
    assert provider.output_file, "output_file should not be empty"


def test_tts_text_to_speak_writes_file() -> None:
    """Mock mode: only assert the file exists. Live mode: assert size > 0.

    Based on the performance_tester_tts.py implementation:
    - mock conn (sample_rate/audio_format/stop_event/client_abort/headers)
      and opus_encoder (a SimpleNamespace placeholder is enough)
    - generate_filename() gives the output path
    - text_to_speak() calls the cloud service asynchronously and writes the file
    - 10s timeout
    - delete the test file afterwards so tmp/ does not pile up
    """
    provider = _build_provider()

    # As in performance_tester_tts.py: some providers access self.conn.sample_rate etc. internally
    if not hasattr(provider, "conn") or provider.conn is None:
        provider.conn = SimpleNamespace(
            sample_rate=16000,
            audio_format="pcm",
            stop_event=threading.Event(),
            client_abort=False,
            headers={},
        )
    if not hasattr(provider, "opus_encoder") or provider.opus_encoder is None:
        provider.opus_encoder = SimpleNamespace()

    out_file = provider.generate_filename()
    try:
        async def run():
            await asyncio.wait_for(
                provider.text_to_speak("测试一下", out_file),
                timeout=10.0,
            )

        asyncio.run(run())

        assert os.path.exists(out_file), f"Output file does not exist: {out_file}"
        if LIVE_API_TESTS:
            assert os.path.getsize(out_file) > 0, f"Output file is empty: {out_file}"
    finally:
        if os.path.exists(out_file):
            try:
                os.remove(out_file)
            except OSError:
                pass
