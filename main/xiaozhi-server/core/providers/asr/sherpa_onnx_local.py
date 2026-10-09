import time
import wave
import os
import glob
import sys
import io
from config.logger import setup_logging
from typing import Optional, Tuple, List
from core.providers.asr.dto.dto import InterfaceType
from core.providers.asr.base import ASRProviderBase

import numpy as np
import sherpa_onnx

from modelscope.hub.file_download import model_file_download

TAG = __name__
logger = setup_logging()


# Capture standard output
class CaptureOutput:
    def __enter__(self):
        self._output = io.StringIO()
        self._original_stdout = sys.stdout
        sys.stdout = self._output

    def __exit__(self, exc_type, exc_value, traceback):
        sys.stdout = self._original_stdout
        self.output = self._output.getvalue()
        self._output.close()

        # Emit the captured content through the logger
        if self.output:
            logger.bind(tag=TAG).info(self.output.strip())


class ASRProvider(ASRProviderBase):
    def __init__(self, config: dict, delete_audio_file: bool):
        super().__init__()
        self.interface_type = InterfaceType.LOCAL
        self.model_dir = config.get("model_dir")
        self.output_dir = config.get("output_dir")
        # sense_voice (default), paraformer, or transducer (zipformer models, e.g. Vietnamese)
        self.model_type = config.get("model_type", "sense_voice")
        self.delete_audio_file = delete_audio_file
        self.num_threads = int(config.get("num_threads", 2))
        # Some models (e.g. the Vietnamese zipformer) output upper-case text; optionally lower-case it
        self.lowercase = str(config.get("lowercase", False)).lower() in ("true", "1", "yes")

        # Ensure the output directory exists
        os.makedirs(self.output_dir, exist_ok=True)

        if self.model_type == "transducer":
            self._init_transducer()
            return

        # Initialize model file paths
        model_files = {
            "model.int8.onnx": os.path.join(self.model_dir, "model.int8.onnx"),
            "tokens.txt": os.path.join(self.model_dir, "tokens.txt"),
        }

        # Download and check model files
        try:
            for file_name, file_path in model_files.items():
                if not os.path.isfile(file_path):
                    logger.bind(tag=TAG).info(f"Downloading model file: {file_name}")
                    model_file_download(
                        model_id="pengzhendong/sherpa-onnx-sense-voice-zh-en-ja-ko-yue",
                        file_path=file_name,
                        local_dir=self.model_dir,
                    )

                    if not os.path.isfile(file_path):
                        raise FileNotFoundError(f"Model file download failed: {file_path}")

            self.model_path = model_files["model.int8.onnx"]
            self.tokens_path = model_files["tokens.txt"]

        except Exception as e:
            logger.bind(tag=TAG).error(f"Model file processing failed: {str(e)}")
            raise

        with CaptureOutput():
            if self.model_type == "paraformer":
                self.model = sherpa_onnx.OfflineRecognizer.from_paraformer(
                    paraformer=self.model_path,
                    tokens=self.tokens_path,
                    num_threads=self.num_threads,
                    sample_rate=16000,
                    feature_dim=80,
                    decoding_method="greedy_search",
                    debug=False,
                )
            else:  # sense_voice
                self.model = sherpa_onnx.OfflineRecognizer.from_sense_voice(
                    model=self.model_path,
                    tokens=self.tokens_path,
                    num_threads=self.num_threads,
                    sample_rate=16000,
                    feature_dim=80,
                    decoding_method="greedy_search",
                    debug=False,
                    use_itn=True,
                )

    @staticmethod
    def _find_model_file(model_dir: str, prefix: str) -> Optional[str]:
        """Find "<prefix>*.onnx" in model_dir, preferring the int8-quantized file when present."""
        candidates = sorted(glob.glob(os.path.join(model_dir, f"{prefix}*.onnx")))
        if not candidates:
            return None
        int8 = [c for c in candidates if c.endswith(".int8.onnx")]
        return int8[0] if int8 else candidates[0]

    def _init_transducer(self):
        """Load an offline zipformer transducer model (encoder/decoder/joiner + tokens.txt).

        Used for languages that SenseVoice does not cover, for example Vietnamese with
        sherpa-onnx-zipformer-vi-int8-2025-04-20. These models are not downloaded
        automatically; download and extract them into model_dir first.
        """
        files = {
            "encoder": self._find_model_file(self.model_dir, "encoder"),
            "decoder": self._find_model_file(self.model_dir, "decoder"),
            "joiner": self._find_model_file(self.model_dir, "joiner"),
        }
        tokens = os.path.join(self.model_dir, "tokens.txt")
        missing = [name for name, path in files.items() if not path]
        if not os.path.isfile(tokens):
            missing.append("tokens.txt")
        if missing:
            message = (
                f"Transducer model files missing in {self.model_dir}: {', '.join(missing)}. "
                "Download a sherpa-onnx transducer model (for Vietnamese: "
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
                "sherpa-onnx-zipformer-vi-int8-2025-04-20.tar.bz2) and extract it into model_dir."
            )
            logger.bind(tag=TAG).error(message)
            raise FileNotFoundError(message)

        logger.bind(tag=TAG).info(
            f"Loading transducer model: encoder={os.path.basename(files['encoder'])}, "
            f"decoder={os.path.basename(files['decoder'])}, joiner={os.path.basename(files['joiner'])}"
        )
        with CaptureOutput():
            self.model = sherpa_onnx.OfflineRecognizer.from_transducer(
                encoder=files["encoder"],
                decoder=files["decoder"],
                joiner=files["joiner"],
                tokens=tokens,
                num_threads=self.num_threads,
                sample_rate=16000,
                feature_dim=80,
                decoding_method="greedy_search",
                debug=False,
            )

    def read_wave(self, wave_filename: str) -> Tuple[np.ndarray, int]:
        """
        Args:
        wave_filename:
            Path to a wave file. It should be single channel and each sample should
            be 16-bit. Its sample rate does not need to be 16kHz.
        Returns:
        Return a tuple containing:
        - A 1-D array of dtype np.float32 containing the samples, which are
        normalized to the range [-1, 1].
        - sample rate of the wave file
        """

        with wave.open(wave_filename) as f:
            assert f.getnchannels() == 1, f.getnchannels()
            assert f.getsampwidth() == 2, f.getsampwidth()  # it is in bytes
            num_samples = f.getnframes()
            samples = f.readframes(num_samples)
            samples_int16 = np.frombuffer(samples, dtype=np.int16)
            samples_float32 = samples_int16.astype(np.float32)

            samples_float32 = samples_float32 / 32768
            return samples_float32, f.getframerate()

    def requires_file(self) -> bool:
        return True

    async def speech_to_text(
        self, opus_data: List[bytes], session_id: str, artifacts=None
    ) -> Tuple[Optional[str], Optional[str]]:
        """Main speech-to-text processing logic"""
        file_path = None
        try:
            if artifacts is None:
                return "", None
            file_path = artifacts.file_path

            start_time = time.time()
            s = self.model.create_stream()
            samples, sample_rate = self.read_wave(file_path)
            s.accept_waveform(sample_rate, samples)
            self.model.decode_stream(s)
            text = s.result.text
            if self.lowercase:
                text = text.lower()
            logger.bind(tag=TAG).debug(
                f"Speech recognition time: {time.time() - start_time:.3f}s | result: {text}"
            )

            return text, file_path

        except Exception as e:
            logger.bind(tag=TAG).error(f"Speech recognition failed: {e}", exc_info=True)
            return "", file_path
