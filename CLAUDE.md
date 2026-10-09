# CLAUDE.md

Project notes for Claude Code sessions on this fork are in AGENT.md:

@AGENT.md

Working rules for this fork:
- Write code, comments, logs and commit messages in English only.
- Do not edit docs/ or existing Liquibase *.sql changesets (add a new changeset instead).
- When a manager-web UI phrase is translated, add it to web-vocabulary.txt and reuse the same English in the backend.
- manager-web locale files must keep identical keys: run `npm run check:i18n` in main/manager-web.
