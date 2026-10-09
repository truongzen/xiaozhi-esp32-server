# AGENT.md

Notes for people and coding agents working on this fork.

## Vietnamese voice pipeline (local VAD + ASR)

### Can the default local modules handle Vietnamese?

| Module | Instance | Vietnamese | Notes |
|---|---|---|---|
| VAD | `VAD_SileroVAD` (`type: silero`, `models/snakers4_silero-vad`) | **Yes** | Silero VAD is language independent: it detects human speech, not words. Tested on Vietnamese recordings (see below). No extra VAD model is needed. |
| ASR | `ASR_FunASR` (`type: fun_local`, `models/SenseVoiceSmall`) | **No** | SenseVoiceSmall only supports `zh`, `en`, `ja`, `ko`, `yue` (`auto` picks among those). Vietnamese is not one of its languages, so it cannot transcribe Vietnamese. |
| ASR | `ASR_SherpaASR` (`sherpa-onnx-sense-voice-zh-en-ja-ko-yue`) | **No** | Same SenseVoice model, same language list. |
| ASR | `ASR_SherpaVietnameseASR` (**new**, `type: sherpa_onnx_local`, `model_type: transducer`) | **Yes** | Local, offline, CPU. sherpa-onnx zipformer transducer trained for Vietnamese (int8, about 60 MB). |

So a fully local Vietnamese pipeline is **`VAD_SileroVAD` + `ASR_SherpaVietnameseASR`**.

### What was added

- `main/xiaozhi-server/core/providers/asr/sherpa_onnx_local.py`: new `model_type: transducer`
  (encoder / decoder / joiner + `tokens.txt`, int8 files preferred), new options `num_threads`
  and `lowercase`. The existing `sense_voice` and `paraformer` types are unchanged.
- `main/xiaozhi-server/config.yaml`: new `ASR.SherpaVietnameseASR` entry.
- `main/manager-api/src/main/resources/db/changelog/202610091930.sql` (+ entry in
  `db.changelog-master.yaml`): registers `ASR_SherpaVietnameseASR` in the control panel and adds the
  `model_type`, `num_threads`, `lowercase` fields to the Sherpa provider form, so they are not lost
  when the config is edited in the web UI.

### Setup

1. Download and extract the model into `main/xiaozhi-server/models/`:
   ```bash
   cd main/xiaozhi-server/models
   curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-vi-int8-2025-04-20.tar.bz2
   tar xjf sherpa-onnx-zipformer-vi-int8-2025-04-20.tar.bz2
   ```
   The folder must contain `encoder-*.onnx`, `decoder-*.onnx`, `joiner-*.onnx` and `tokens.txt`.
   (The non-quantized model `sherpa-onnx-zipformer-vi-2025-04-20`, about 250 MB, also works; point
   `model_dir` at it.)
2. Select the modules:
   - Standalone server (`data/.config.yaml`):
     ```yaml
     selected_module:
       VAD: SileroVAD
       ASR: SherpaVietnameseASR
     ```
   - With the control panel: in the agent's role configuration choose **Sherpa Vietnamese ASR (local)**
     for speech recognition and **SileroVAD** for VAD.
3. `sherpa-onnx` is already in `requirements.txt`; nothing else needs installing.

### Test results (2026-10-09)

The real `VADProvider` (SileroVAD, threshold 0.5 / 0.3, 200 ms silence) and the new transducer
`ASRProvider` were run on the three Vietnamese test recordings shipped with the model, each padded
with 0.5 s of silence and fed in 60 ms frames like a device does:

| Clip | VAD: first speech detected | ASR output |
|---|---|---|
| 0.wav (3.7 s) | 0.66 s | rồi cũng hỗ trợ cho lâu lâu cũng cho gạo cho này kia |
| 1.wav (2.4 s) | 0.84 s | những nơi đã khống chế được căn bệnh |
| 2.wav (1.6 s) | 0.78 s | âm lượng tivi giảm |

VAD ignored the leading silence and fired on the Vietnamese speech; ASR returned correct Vietnamese
with diacritics.

### Remaining Vietnamese caveats

- The LLM and TTS must also support Vietnamese (for example an OpenAI-compatible LLM and
  `EdgeTTS` with a `vi-VN-*Neural` voice). Default prompts in `config.yaml` are Chinese; set a
  Vietnamese or English role prompt for the agent.
- Exit commands and wake words in `config.yaml` are Chinese phrases; add Vietnamese ones if needed.

## English translation of this fork

- `manager-web`: every UI string goes through vue-i18n (`src/i18n/*.js`, six locales with identical
  keys, checked by `npm run check:i18n`). `fallbackLocale` is `en`. Phrases translated in this pass
  are listed in `web-vocabulary.txt` (repo root); backend translations use the same English for the
  same phrase.
- `digital-human`, `manager-api`, `xiaozhi-server`: logs, comments, docstrings, exception messages
  and API descriptions are English.
- Chinese that is still in the code on purpose (functional data, do not "fix" it):
  - LLM prompts, tool/function descriptions sent to the LLM, and text spoken to users through TTS.
  - Regexes and punctuation sets that match Chinese text; wake words and exit-command lists.
  - Placeholder detection: values starting with `你` / `你的` (and now also `your_`) mean
    "not configured" (`core/utils/util.py` `check_model_key`, `app.py`, `RAGFlowAdapter`). The web
    model editor writes `你的…` in Chinese UIs and `your_<field>` in other languages.
  - Chinese locale files (`zh_CN.js`, `zh_TW.js`, `messages_zh_*.properties`) and the Chinese default
    backend bundle `messages.properties`.
  - Liquibase `*.sql` changesets were not edited (checksums). Seed data in the database (model names,
    dictionary labels, provider field labels) is therefore still Chinese and can appear in the web UI
    in any language until it is edited in the control panel.
- `docs/` was not changed.
