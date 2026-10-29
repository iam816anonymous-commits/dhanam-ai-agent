# Feature research log

This index records research and implementation evidence for each Dhanam feature batch. See OPEN_SOURCE_RESEARCH_PROTOCOL.md for the mandatory process.

## Status

| Feature IDs | Status | Notes |
|---|---|---|
| F01 | Pending | Dashboard must derive from real DB data; planned after CRUD foundation |
| F02 | Phase 2A code added; CI passed | Expense CRUD uses integer paise, validated input, Room and repository boundary |
| F03 | Partial foundation | Live list exists; search/filter and bounded query strategy remain |
| F04 | Partial foundation | Initial canonical category set exists; aliases/normalization remain |
| F05–F20 | Research/implementation pending per feature | Each requires source review, acceptance tests and evidence |
| F21–F23 | Phase 3 model catalog/download implemented; runtime qualification pending | LiteRT-LM candidate; no runtime dependency until compatibility and target-device smoke tests pass |
| F24–F25 | Research pending per feature | Typed tools, policy, idempotency and read-back evidence required |
| F26–F30 | Research/implementation pending per feature | Offline, migration, low-resource, privacy and CI evidence required |

## Phase 2A research — 2026-10-29
- Official Room 2.8.5 release notes: https://developer.android.com/jetpack/androidx/releases/room
- Official migration guide: https://developer.android.com/training/data-storage/room/migrating-db-versions
- Expense-Tracker source at commit 5c3e4d19d2b7e093d22e6a420b40799b2f8985b0: TransactionEntity.kt, TransactionDao.kt, AppDatabase.kt, TransactionRepositoryImpl.kt and LICENSE. MIT. Double money representation rejected; no code copied.
- Expenso source at commit 0b495c9758c45c5807f7467a3b43ebf705fe5890: AppDatabase.kt, Daos.kt, Repository.kt and LICENSE. Apache-2.0. Double money and destructive migration fallback rejected; no code copied.
- Decision: Room, explicit schema export, repository boundary, Flow observation, integer-paise storage and epoch-day dates. No destructive migration fallback. CI will verify compatibility with the pinned Kotlin/KSP/AGP stack.
- Implementation paths: app/src/main/java/com/aistudio/dhanam/expense/data/, domain/MoneyParser.kt, domain/ExpenseInputValidator.kt, ui/ExpensesViewModel.kt and ui/screens/ExpensesScreen.kt.
- Tests: MoneyParserTest and ExpenseInputValidatorTest passed in CI run https://github.com/iam816anonymous-commits/dhanam-ai-agent/actions/runs/40857428895.
- Physical-device verification: pending.

## Initial source review — 2026-10-29
See 2026-10-29-initial-source-review.md for exact commits, inspected paths, license status and caveats.

## Required entry template
- Feature IDs and user behavior
- Research date
- Repositories and exact commits/tags
- Source paths inspected
- License/SPDX and copying decision
- Official documentation
- Options compared and evidence
- Decision and rejected alternatives
- Dhanam paths changed and ADRs
- Tests and actual results
- CI run and artifact
- Physical-device results
- Known limitations/follow-ups


## Phase 3 research — 2026-10-29
- Official Android LiteRT-LM Kotlin skill: https://developer.android.com/agents/skills/device-ai/litert/litert-lm/skill. Model files belong in app-private/app-specific storage rather than APK assets; initialization must be off-main-thread; engine/conversation ownership must be serialized. No runtime dependency added until compatibility is verified.
- LiteRT-LM source repository: https://github.com/google-ai-edge/LiteRT-LM, Apache-2.0. Compared with llama.cpp; selected LiteRT-LM as the initial runtime candidate because the reviewed artifact is a .litertlm bundle. llama.cpp remains an alternative but is not added.
- Model repository: https://huggingface.co/litert-community/Qwen3-0.6B, Apache-2.0. Selected mixed INT4 artifact at immutable revision 154c5de0fc0fc5002a7da570ba8c67edffa32ca2; SHA-256 7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1; size 497516544 bytes; 2048-token context.
- Implementation: app/src/main/java/com/aistudio/dhanam/expense/model/ and ui/components/ModelManagerSection.kt.
- Safety gate: require at least 3500 MiB total RAM, 2304 MiB currently available RAM, and 1280 MiB free app-private storage. Published performance data is from a different TECNO model, so it is not treated as target-device validation.
- Tests: model catalog pinning test added; CI pending.
- Runtime activation and physical-device smoke test remain blocked on real target-device measurements and successful dependency/API compatibility checks.
