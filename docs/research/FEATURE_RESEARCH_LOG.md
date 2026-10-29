# Feature research log

This index records research and implementation evidence for each Dhanam feature batch. See OPEN_SOURCE_RESEARCH_PROTOCOL.md for the mandatory process.

## Status

| Feature IDs | Status | Notes |
|---|---|---|
| F01 | Pending | Dashboard must derive from real DB data; planned after CRUD foundation |
| F02 | Phase 2A code added; CI verification pending | Expense CRUD uses integer paise, validated input, Room and repository boundary |
| F03 | Partial foundation | Live list exists; search/filter and bounded query strategy remain |
| F04 | Partial foundation | Initial canonical category set exists; aliases/normalization remain |
| F05–F20 | Research/implementation pending per feature | Each requires source review, acceptance tests and evidence |
| F21–F23 | Runtime comparison pending | LiteRT-LM and llama.cpp are candidates, not approved dependencies |
| F24–F25 | Research pending per feature | Typed tools, policy, idempotency and read-back evidence required |
| F26–F30 | Research/implementation pending per feature | Offline, migration, low-resource, privacy and CI evidence required |

## Phase 2A research — 2026-10-29
- Official Room 2.8.5 release notes: https://developer.android.com/jetpack/androidx/releases/room
- Official migration guide: https://developer.android.com/training/data-storage/room/migrating-db-versions
- Expense-Tracker source at commit 5c3e4d19d2b7e093d22e6a420b40799b2f8985b0: TransactionEntity.kt, TransactionDao.kt, AppDatabase.kt, TransactionRepositoryImpl.kt and LICENSE. MIT. Double money representation rejected; no code copied.
- Expenso source at commit 0b495c9758c45c5807f7467a3b43ebf705fe5890: AppDatabase.kt, Daos.kt, Repository.kt and LICENSE. Apache-2.0. Double money and destructive migration fallback rejected; no code copied.
- Decision: Room, explicit schema export, repository boundary, Flow observation, integer-paise storage and epoch-day dates. No destructive migration fallback. CI will verify compatibility with the pinned Kotlin/KSP/AGP stack.
- Implementation paths: app/src/main/java/com/aistudio/dhanam/expense/data/, domain/MoneyParser.kt, domain/ExpenseInputValidator.kt, ui/ExpensesViewModel.kt and ui/screens/ExpensesScreen.kt.
- Tests: MoneyParserTest and ExpenseInputValidatorTest; results pending CI.
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
