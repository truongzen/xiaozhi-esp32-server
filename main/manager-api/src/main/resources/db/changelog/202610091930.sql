-- Add a local Vietnamese speech recognition model (sherpa-onnx zipformer transducer).
-- SenseVoice (FunASR / SherpaASR) does not support Vietnamese; SileroVAD is language independent,
-- so VAD_SileroVAD + ASR_SherpaVietnameseASR gives a fully local Vietnamese pipeline.
-- See AGENT.md in the repository root.

-- Expose the extra sherpa_onnx_local options so the web form keeps them when a config is edited.
UPDATE `ai_model_provider`
SET `fields` = JSON_ARRAY_APPEND(`fields`, '$', JSON_OBJECT('key', 'model_type', 'label', 'Model type (sense_voice / paraformer / transducer)', 'type', 'string'))
WHERE `id` = 'SYSTEM_ASR_SherpaASR' AND JSON_SEARCH(`fields`, 'one', 'model_type', NULL, '$[*].key') IS NULL;

UPDATE `ai_model_provider`
SET `fields` = JSON_ARRAY_APPEND(`fields`, '$', JSON_OBJECT('key', 'num_threads', 'label', 'Number of threads', 'type', 'number'))
WHERE `id` = 'SYSTEM_ASR_SherpaASR' AND JSON_SEARCH(`fields`, 'one', 'num_threads', NULL, '$[*].key') IS NULL;

UPDATE `ai_model_provider`
SET `fields` = JSON_ARRAY_APPEND(`fields`, '$', JSON_OBJECT('key', 'lowercase', 'label', 'Lower-case the recognized text', 'type', 'boolean'))
WHERE `id` = 'SYSTEM_ASR_SherpaASR' AND JSON_SEARCH(`fields`, 'one', 'lowercase', NULL, '$[*].key') IS NULL;

-- Vietnamese ASR model configuration
delete from `ai_model_config` where id = 'ASR_SherpaVietnameseASR';
INSERT INTO `ai_model_config` VALUES ('ASR_SherpaVietnameseASR', 'ASR', 'SherpaVietnameseASR', 'Sherpa Vietnamese ASR (local)', 0, 1, '{
  "type": "sherpa_onnx_local",
  "model_dir": "models/sherpa-onnx-zipformer-vi-int8-2025-04-20",
  "output_dir": "tmp/",
  "model_type": "transducer",
  "num_threads": 2,
  "lowercase": true
}', 'https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models', 'Local Vietnamese speech recognition (sherpa-onnx zipformer transducer, runs on CPU).
1. Download https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-vi-int8-2025-04-20.tar.bz2 (about 60 MB)
2. Extract it into the xiaozhi-server models/ directory, so that models/sherpa-onnx-zipformer-vi-int8-2025-04-20 contains encoder-*.onnx, decoder-*.onnx, joiner-*.onnx and tokens.txt
3. Use it together with VAD_SileroVAD (language independent)
Output files are saved in the tmp/ directory', 11, NULL, NULL, NULL, NULL);
