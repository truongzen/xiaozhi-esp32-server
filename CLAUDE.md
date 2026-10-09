# CLAUDE.md

Project notes for Claude Code sessions on this fork are in AGENT.md:

@AGENT.md

Working rules for this fork:
- Write code, comments, logs and commit messages in English only.
- Do not edit docs/ or existing Liquibase *.sql changesets (add a new changeset instead).
- When a manager-web UI phrase is translated, add it to web-vocabulary.txt and reuse the same English in the backend.
- manager-web locale files must keep identical keys: run `npm run check:i18n` in main/manager-web.

## Repository layout

| Path | Stack | What it is |
|---|---|---|
| `main/xiaozhi-server` | Python 3.10 | Device-facing voice server (WebSocket, VAD/ASR/LLM/TTS providers in `core/providers/`) |
| `main/manager-api` | Java 21, Spring Boot 3.4, Maven, Liquibase | Control panel backend (DB changelogs in `src/main/resources/db/changelog/`) |
| `main/manager-web` | Vue 2 (vue-cli), vitest | Control panel web UI (i18n in `src/i18n/`) |
| `main/manager-mobile` | uni-app | Mobile control panel |
| `main/digital-human` | Static web + Python (`start.py`) | Digital human kiosk front end with wake word runtime |
| `docs/` | | Upstream docs (do not edit) |

## Local dev and tests (Windows, Git Bash)

CI (`.github/workflows/test.yml`) runs Python 3.10, Java 21, Node 20. Match those versions.

- Python: project venv at `main/xiaozhi-server/.venv` (Python 3.10, created with `uv`).
  - Tests: `cd main/xiaozhi-server && .venv/Scripts/python.exe -m pytest -x -q`
  - CI only runs `tests/test_smoke.py`; run it before pushing.
  - The full suite needs `data/.config.yaml` to exist (a minimal one with a `log:` section is enough).
  - Add deps with `python -m uv pip install --python .venv/Scripts/python.exe <pkg>` and pin them in `requirements.txt`.
- Web: `cd main/manager-web && npm run test:unit` (plus `npm run check:i18n` after touching locale files).
- Java: `cd main/manager-api && mvn -B -q test -DfailIfNoTests=false` (needs JDK 21 + Maven on PATH).
- `make test` runs all three (uses `python` from PATH, so prefer the venv command above for Python).

Never commit secrets: real keys go in `main/xiaozhi-server/data/.config.yaml` (gitignored), not in `config.yaml`.

## Git workflow

- Remote: `origin` = https://github.com/truongzen/xiaozhi-esp32-server.git, default branch `main`.
- Commit small, focused changes with English messages. Run the relevant tests first.
- Push to `origin main` (or a feature branch + PR for larger work). Never force-push `main`.
- `.claude/settings.json` is shared; personal overrides go in `.claude/settings.local.json` (gitignored).
