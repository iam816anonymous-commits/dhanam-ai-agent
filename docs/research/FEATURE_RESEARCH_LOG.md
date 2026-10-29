# Feature research log

This index records research and implementation evidence for each Dhanam feature batch. Detailed requirements are in `docs/research/OPEN_SOURCE_RESEARCH_PROTOCOL.md`.

## Status

| Feature IDs | Status | Notes |
|---|---|---|
| F01–F20 | Research pending per feature | Finance and UX references identified; source code, license, and tests must be reviewed before implementation |
| F21–F23 | Research pending per feature | LiteRT-LM and llama.cpp identified as runtime candidates, not approved dependencies |
| F24–F25 | Research pending per feature | Must inspect agent/tool implementations and verify policy, idempotency, and read-back behavior |
| F26–F30 | Research pending per feature | Offline, migration, low-resource, privacy, and CI evidence required |

## Initial candidate scan — 2026-10-29

This is a discovery pass only; it is not a completed source audit.

- [Kaasu](https://github.com/vanterx/kaasu) — candidate for offline-first Kotlin/Compose/Room finance patterns, repository boundaries, and export flow. Exact source commit, license, tests, migrations, and money representation still need verification.
- [Expense-Tracker](https://github.com/airmon1101/Expense-Tracker) — candidate for finance CRUD, filtering, reports, and validation patterns. Inspect implementation and licensing before adopting.
- [Expenso](https://github.com/darkvortex144/Expenso) — secondary finance workflow comparison. Verify source, license, and test evidence before relying on feature claims.
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) — official runtime candidate to evaluate for native Android on-device inference and tool use. Pin release, inspect Kotlin APIs, supported model artifacts, license, dependency footprint, and target-device measurements.
- [llama.cpp Android example](https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android) — alternative native runtime candidate. Compare ABI/build complexity, model format, memory/context controls, APK/native size, license, performance, and supported hardware.
- [Android app architecture](https://developer.android.com/topic/architecture), [Room](https://developer.android.com/training/data-storage/room), [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager), and [Biometric authentication](https://developer.android.com/identity/sign-in/biometric-auth) — official platform references for architecture, persistence, background work, and authentication.

No candidate has been approved solely from this discovery list. Do not copy code until its exact license and source revision are verified.

## Required entry template

For each feature or tightly coupled batch, add a section with:

- **Feature IDs / user behavior:**
- **Research date:**
- **Repositories and exact commits/tags:**
- **Source paths inspected:**
- **License/SPDX and copying decision:**
- **Official documentation:**
- **Options compared and evidence:**
- **Decision / rejected alternatives:**
- **Dhanam paths changed / ADRs:**
- **Tests and actual results:**
- **CI run / artifact:**
- **Physical-device results (if relevant):**
- **Known limitations / follow-ups:**
