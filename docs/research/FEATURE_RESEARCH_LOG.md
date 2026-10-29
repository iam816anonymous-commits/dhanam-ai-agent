# Feature research log

This index records research and implementation evidence for each Dhanam feature batch. See OPEN_SOURCE_RESEARCH_PROTOCOL.md for the mandatory process.

## Status

| Feature IDs | Status | Notes |
|---|---|---|
| F01 | Implemented locally; CI pending for current batch | Monthly dashboard and deterministic month/category totals derive from Room expense records |
| F02 | Phase 2A code added; CI passed | Expense CRUD uses integer paise, validated input, Room and repository boundary |
| F03 | Basic search/category filtering implemented; date filtering and bounded query remain | Expense screen filters the observed local expense list by description, note and category |
| F04 | Partial foundation | Initial canonical category set exists; aliases/normalization remain |
| F05 | Overall monthly budget and safe-to-spend calculation implemented; category budget editor remains | Integer-paise budget, month-aware spending, no fabricated allowance when no budget exists |
| F06–F07 | Khata loan records and atomic repayments implemented; CI/device evidence required | Repayment ledger, request idempotency and overpayment guard are in place; loan editing/history UI remains |
| F10 | CSV expense export/import implemented; CI/device evidence required | SAF picker, RFC-4180-style parser, validation preview, exact duplicate skipping, 10 MB/50k-row caps |
| F11 | Monthly PDF report export implemented; CI/device evidence required | Generated locally from deterministic summary, saved only to user-selected URI |
| F08–F09, F12–F20 | Pending | Insights beyond monthly totals, reminders, security lock/biometrics, migration of legacy data, and remaining preferences need dedicated work |
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


## Phase 2B–2E research and implementation — 2026-10-29

- Official Room guidance: https://developer.android.com/training/data-storage/room and migration guidance: https://developer.android.com/training/data-storage/room/migrating-db-versions. Decision: additive Room 1→2 migration only; no destructive fallback; store amounts as integer paise and keep monthly calculations deterministic.
- Official Android Storage Access Framework guidance: https://developer.android.com/guide/topics/providers/document-provider. Decision: user-selected document URIs for CSV import/export and PDF report export; no broad storage permission and no silent file writes to shared storage.
- F01/F05: monthly summary, category totals, overall monthly budget, remaining budget and daily safe-to-spend are computed from actual expense rows. Safe-to-spend is unavailable when a budget is unset or exhausted; no implied income/balance is invented.
- F03: text search across description/note/category and category filtering are client-side over the current local Flow. A bounded/paged query remains a scalability follow-up for very large histories.
- F06/F07: Khata loans record direction, principal, amount repaid and outstanding balance. Repayments are written transactionally with a unique request ID and rejected if they exceed outstanding principal. Records with repayment history cannot be deleted; full repayment-history UI is still pending.
- F10: CSV parser validates the complete file before allowing import, supports quoted commas/quotes/newlines, limits files to 10 MiB and 50,000 rows, and skips exact existing expense duplicates. Any invalid row blocks commit. Export includes a separate typed ledger CSV for Khata loans, repayments and current-month budgets; text fields are protected against spreadsheet formula injection.
- F11: PDF report is generated on-device and written only to a URI chosen by the user.
- Implementation files: FinanceDatabase.kt, FinanceDao.kt, BudgetEntity.kt, KhataLoanEntity.kt, RepaymentEntity.kt, FinanceCalculations.kt, KhataValidator.kt, FinanceCsvTransfer.kt, FinancePdfReportExporter.kt, InsightsScreen.kt, KhataViewModel.kt, KhataScreen.kt, FinanceDataSection.kt and ExpensesScreen.kt.
- Tests added: monthly boundary/paise/budget tests and CSV quoted-field/invalid-row tests. The database/dashboard/Khata batch CI passed at https://github.com/iam816anonymous-commits/dhanam-ai-agent/actions/runs/40870757231. CSV/PDF export batch CI is running separately; do not treat the earlier run as validation for those files.
- Physical-device validation: pending.


## Phase 2F reminders and app-lock implementation — 2026-10-29

- WorkManager official guidance: https://developer.android.com/develop/background-work/background-tasks/persistent and https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work. Decision: one uniquely named periodic worker; check preferences at execution time, cancel work when both reminder types are disabled, and do not promise exact-time execution.
- Android notification permission guidance: https://developer.android.com/develop/ui/views/notifications/notification-permission. Decision: request POST_NOTIFICATIONS on Android 13+ only after a user enables a reminder; permission denial leaves that reminder disabled.
- Reminders: daily periodic worker checks actual local expenses/budget and Khata due dates. Notifications deliberately avoid exposing amounts or people’s names on the lock screen.
- Security: optional 4–12 digit app PIN is stored as a salted PBKDF2-HMAC-SHA256 hash (150,000 iterations), compared in constant time, and temporarily rate-limited after repeated failures. The app locks when it leaves the foreground if a PIN is configured.
- Security limitation: the PIN gate does not encrypt the SQLite database against device-level forensic access. Biometrics and an audited encrypted-database design are not claimed as implemented.
- Legacy-data migration is implemented as an explicit preview/confirm workflow: it opens the prior app database read-only, converts Double rupees to paise with decimal HALF_UP rounding, imports expenses/loans/category budgets transactionally, skips exact duplicates, and never deletes the old database. The new target Room 2→3 migration also preserves metadata columns.


## Legacy data migration source audit — 2026-10-29

- Legacy repository: https://github.com/iam816anonymous-commits/dhanam-ai/tree/817cacc3baefc41d3a8a41cff0ad9d0bcddd8962
- Inspected exact source paths at that commit: `app/src/main/java/com/example/data/local/AppDatabase.kt`, `app/src/main/java/com/example/data/model/Expense.kt`, `Loan.kt`, `CategoryBudget.kt`, `app/src/main/java/com/example/data/preferences/PreferenceManager.kt`, and `app/schemas/com.example.data.local.AppDatabase/1.json`.
- Legacy application ID is `com.aistudio.dhanam.expense`, matching the new target app ID; the legacy Room file is `dhanam_expense_database`. The target database is `dhanam_finance.db`, so the explicit importer can read the old database from app-private storage after a compatible in-place update.
- Legacy fields: expense title/amount/category/timestamp/notes/paymentMode/detectedLanguage; loan person/amount/amountPaid/dateGiven/dueDate/notes/phoneNumber/isSettled; category budget monthlyLimit. Legacy money used `Double`, so migration uses `BigDecimal.valueOf(value).setScale(2, HALF_UP)`, checks finiteness, positivity and overflow, and records row errors in preview.
- Migration opens the legacy database with `SQLiteDatabase.OPEN_READONLY`, displays row counts and invalid rows, requires explicit user confirmation, runs destination writes in one Room transaction, skips exact duplicate expenses/loans and existing category-month budgets, and does not delete/modify the legacy file.
- PIN continuity: the target app recognizes the previous `dhanam_secure_pin` / `dhanam_prefs` credential locations. After successful verification, it re-hashes the PIN with the target KDF and removes legacy PIN copies.
- Remaining migration limitation: this flow requires the legacy private database to be present in the same app sandbox (normally a compatible update over the old app). If the app was installed as a separate package or the old database is absent, use the validated CSV import path for expenses; loan/budget CSV migration is not yet available.
