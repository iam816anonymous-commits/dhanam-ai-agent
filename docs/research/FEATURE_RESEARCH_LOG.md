# Feature research log

This index records research and implementation evidence for each Dhanam feature batch. Detailed requirements are in [the open-source research protocol](OPEN_SOURCE_RESEARCH_PROTOCOL.md).

## Status

| Feature IDs | Status | Notes |
|---|---|---|
| F01–F20 | Research pending per feature | Initial source review found useful architecture ideas but also a critical money-model mismatch; feature-specific finance code/tests still need review |
| F21–F23 | Initial candidate comparison started; decision pending | LiteRT-LM and llama.cpp are both viable candidates to benchmark, not approved dependencies |
| F24–F25 | Research pending per feature | Must inspect agent/tool implementations and verify policy, idempotency, and read-back behavior |
| F26–F30 | Research pending per feature | Offline, migration, low-resource, privacy, and CI evidence required |

## Initial source review — 2026-10-29

See [the detailed source review](2026-10-29-initial-source-review.md) for exact commits, inspected paths, license status, decisions, and caveats.

Key outcomes:
- Kaasu has useful Room/repository/Flow patterns to investigate, but the inspected repository implementation uses `Double` for money and the repository's license was not detected in this pass. Do not copy its money model or code without resolving licensing.
- Expense-Tracker (MIT) and Expenso (Apache-2.0) are candidates, but their actual source/tests/money representations still need inspection.
- LiteRT-LM (Apache-2.0) and llama.cpp (MIT) are the runtime candidates for a controlled comparison. Neither is approved until exact model artifacts and target-device behavior are validated.

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
