# Feature parity matrix

This matrix is the Phase 1–7 source of truth for parity. Status means **audit classification**, not implementation status in the empty target repository.

Legend: **Reuse as reference** = behavior exists in legacy source; **Rebuild/adapt** = new architecture required; **Verify** = inspect remaining implementation and add tests before claiming parity.

| ID | Feature area | Legacy evidence / likely source | Target implementation decision | Required acceptance evidence |
|---|---|---|---|---|
| F01 | Dashboard and monthly overview | `MainViewModel`, `AgentFinancialBrain`, dashboard UI | Rebuild Compose screen; derive every number from repositories | Totals match fixture DB for month boundaries, empty history, refunds/negative corrections if supported |
| F02 | Expense CRUD | `ExpenseAgentEngine`, `MainViewModel`, Expense DAO/entity, expense screens | Typed domain service and repository; all manual and agent paths share validation | Create/read/update/delete tests; invalid amount rejected; changes survive restart |
| F03 | Expense list/search/filter | Expense DAO, expenses screen | Rebuild with real paging/filtering or bounded queries appropriate for device | Search/category/date filters match fixture records; no hardcoded production rows |
| F04 | Category taxonomy and normalization | `ExpenseCategory`, `ConversationalFuzzyExtractor` | Preserve canonical categories and alias mapping; unknown category is clarified or safely categorized per explicit policy | English/Telugu aliases, misspellings, unknown labels and canonical persistence tested |
| F05 | Overall/category budgets | PreferenceManager, `CategoryBudget`, `AgentFinancialBrain`, settings | Store money in paise; distinguish unset budget from zero; disclose defaults on fresh install | No budget is invented by insights; remaining/safe-to-spend math exact and month-aware |
| F06 | Lending Khata and people | `Loan`, `ExpenseAgentEngine`, Khata screen | Typed loan service; explicit principal, paid, outstanding and due-date semantics | Partial/full repayments, multiple loans per person, due dates and edits tested |
| F07 | Repayment workflow | `ExpenseAgentEngine` integration tests and `MainViewModel.recordRepayment` | Atomic repayment write with stable loan ID, idempotency key and read-back | No repayment if amount/target ambiguous; over-repayment rejected; retry cannot double-apply |
| F08 | Insights/history | `AgentFinancialBrain`, historical analysis in `ExpenseAgentEngine`, insights UI | Read-only query tools; date range/timezone explicit; never fabricate missing history | Today/month/custom period and up-to-one-year analysis match deterministic fixture calculations |
| F09 | Daily safe-to-spend/projections | `AgentFinancialBrain.calculateLocalFinancialHealth` | Deterministic calculation service; define inclusive/exclusive remaining-day semantics | Month start/end, leap year, zero budget, overspend and timezone tests |
| F10 | CSV export/import | `AgentCapabilityRegistry`, `MainViewModel` export/import methods | Rebuild using SAF/URI handling; validate and preview imports before commit | Escaping, locale, malformed rows, duplicate policy, partial failure and rollback tested |
| F11 | PDF reports/sharing | insights/report code and FileProvider | Rebuild local generation and narrowly scoped content URIs | Correct totals, no broad file URI, safe share targets, export failure recovery |
| F12 | Budget/repayment reminders | workers/notification code, legacy README | Rebuild with WorkManager and deduplication; do not promise exact timing under OEM battery controls | Scheduled/dedup behavior tested; permission denied path is safe; OEM delays documented |
| F13 | Agent chat/history | `AgentScreen`, `MainViewModel` agent state | Rebuild visible lifecycle states, errors, clarification, result and retry | No false success; loading/cancel/error/recovery tested; history persistence policy explicit |
| F14 | Navigation commands | `AgentCapabilityRegistry`, `MainViewModel.setCurrentScreen` | Typed allow-listed in-app navigation only | Every supported screen has a route and UI test; invalid route rejected |
| F15 | Undo/correction/delete confirmation | `ExpenseAgentEngine`, pending deletion TTL, integration tests | Explicit state machine; confirmation bound to exact target and expires | No target drift; expired confirmation rejected; destructive action cannot execute on ambiguous “yes” |
| F16 | Voice input/output | VoiceAgentManager, `AgentScreen`, permissions/manifest | Optional adapters; text remains fully functional | Permission denied, recognizer missing, Telugu TTS unavailable, cancellation and lifecycle tested |
| F17 | Languages | English/Telugu messages and Telugu parser; extractor also contains some Hindi/Kannada aliases | First release guarantees English/Telugu only; other languages gated by evaluation | Intent/entity benchmarks by language; no unsupported-language claim |
| F18 | PIN/biometric/auto-lock | security manager, preference state, lifecycle handling, README | Audit and rebuild after security review; Android Keystore where appropriate | PIN migration/lockout, biometric unavailable, background lock, process death and reset tested |
| F19 | Screenshot/recents privacy | optional `FLAG_SECURE` setting described in legacy README | Preserve as explicit setting if verified; default choice documented | Setting survives restart; secure window behavior tested on supported API levels |
| F20 | Settings/preferences | `PreferenceManager`, `SettingsScreen` | Rebuild real controls with persisted state | Each control affects actual behavior and survives process restart |
| F21 | Local model management | Meeting Summarizer model catalog/downloader/runtime patterns | Adapt only verified-download/runtime abstraction; separate from finance services | Explicit download, progress, hash/size/license verification, cancel/retry/cleanup and restart activation |
| F22 | Model resource gate | Meeting Summarizer docs show inconsistent conservative thresholds in different surfaces | One shared eligibility policy based on API, total/available RAM, free storage and measured profile | Boundary tests; no download option for ineligible models; recheck at download/activation |
| F23 | Model planning/tool selection | Not implemented as a proven safe planner in legacy Dhanam | Rebuild with strict JSON/schema, domain validation and deterministic fallback | Invalid JSON, unknown tools, extra fields, hallucinated IDs/amounts and out-of-scope requests fail closed |
| F24 | Typed finance tool registry | `AgentCapabilityRegistry` is the initial inventory | Rebuild as typed registry with read/write class, schema, policy and confirmation metadata | Registry coverage test; every tool has handler, input validator and authorization policy |
| F25 | Audit and read-back verification | Existing result types/DAOs provide partial evidence; no complete agent audit guarantee established | Add privacy-safe local execution events and post-write read-back | Each write has request/action/result state; no raw sensitive prompt logging; mismatch is surfaced |
| F26 | Offline operation | Legacy has local DB/parser; README also references Gemini, creating ambiguity | Core CRUD, calculations, history, and deterministic fallback work offline; model inference offline once installed | Airplane-mode tests; no remote request during finance workflows |
| F27 | Data continuity/migrations | legacy DB `dhanam_expense_database`, Room version 1; nextgen DB differs | Explicit schema/data migration or user-controlled export/import, not implicit destructive recreation | Upgrade fixture created from legacy schema; row counts, money values, loans, budgets and PIN prefs verified |
| F28 | Accessibility, responsive UI and low-resource devices | legacy targets API 27 and budget/low-RAM devices | Preserve usable UI; reduce animation/model pressure under memory constraints | API 27 and current API smoke tests; TalkBack labels; low-memory model disable/fallback |
| F29 | Data clear/reset | `AgentCapabilityRegistry` includes `data.clear_all` | Explicit preview and confirmation; clear related files/preferences and DB transactionally where possible | Cannot be triggered by model alone; tests cover stale confirmation and partial failure |
| F30 | Build, CI and release artifacts | legacy CI and Gradle setup; no target project yet | Create reproducible workflow for lint, unit tests, instrumentation where possible, APK checks | Clean checkout builds; workflow fails on test/build/APK absence; signing secrets never committed |

## Audit rule

A feature is complete only when (1) it exists in the parity matrix, (2) the UI/action is connected to a real handler, (3) persistence/business behavior is tested, and (4) the relevant acceptance evidence is recorded. A screenshot, placeholder button, mock response, or compile-only check is not proof of feature parity.

## Money and data invariants

- Store currency as integer paise (`Long`) or a reviewed exact-decimal representation; never use `Double` for canonical money or totals.
- Convert legacy rupee `Double` fields only through an explicit migration converter with defined rounding, finite-value checks and overflow detection.
- Use transaction IDs, not titles or “last item” guesses, to target edits, deletes and repayments.
- Every analytical answer is computed from actual local records and must state the period used. Missing data is not zero unless the domain explicitly defines it as zero.
- Preserve original timestamps/dates and define timezone handling before date-range queries.


## Target implementation snapshot (2026-10-29)

| Feature | Current target status | Remaining acceptance work |
|---|---|---|
| 2A Expense CRUD | Implemented; prior CI passed | Device persistence/restart smoke test |
| 2B Dashboard/search/filter | Monthly dashboard, category totals, text search and category filter implemented | Date-range filters, large-history query bounds and device checks |
| 2C Budgets/safe-to-spend | Overall monthly budget and deterministic daily allowance implemented | Per-category budget editor and edge-case UI review |
| 2D Khata/repayments | Local loans, direction, due date, outstanding balance and transactional repayments implemented | Edit workflow and visible repayment history |
| 2E Import/export/reports | Expense CSV import/export with metadata, full Khata/repayment/budget ledger CSV, monthly PDF report and previewed legacy DB import implemented | Device URI-provider checks and larger-file tests |
| 2F Reminders/security/settings/migration | Budget/due-date reminders, optional PIN lock, legacy PIN continuity and previewed legacy DB migration implemented | Biometrics, encrypted-database protection, remaining preferences and device verification remain |

This status snapshot describes code coverage, not a release-complete Phase 2. See the feature research log for sources and CI evidence.


### Phase 2F implementation note

The app uses WorkManager for deferrable periodic reminders; Android/OEM battery policies can delay execution, so this is not an exact-time alarm. On Android 13+, notification permission is requested only when a user enables reminders. The optional PIN gate is a privacy barrier for casual access, not database encryption. Legacy database migration is implemented as a previewed, explicit, transactional import; biometric unlock and device verification remain.


Legacy migration reads `dhanam_expense_database` read-only and only runs after preview/explicit confirmation. It imports expenses, loans, and category budgets in one transaction, with explicit HALF_UP conversion of legacy Double rupees to paise. It does not delete the old database. See the feature research log for source commit and limitations.


## Coordinated finance-agent parity (added 2026-10-30)

| ID | Feature | Target implementation | Acceptance evidence / remaining work |
|---|---|---|---|
| A01 | Finance-only agent roles | Finance Operations, Financial Intelligence and Skill Improvement roles with finance-specific system policies | Role tests and negative tests for generic/out-of-domain requests; expand policy evaluation set |
| A02 | One shared local model | Process-wide model owner, one LiteRT-LM Engine, serialized coordinator shared by UI and WorkManager | CI/unit tests; physical concurrency, memory and lifecycle qualification still required |
| A03 | Deterministic financial intelligence | Local Room-derived category movement, budget overruns, recurring candidates, lending/borrowing outstanding and savings-review opportunities | FinancialAnalysisEngine fixture tests; add more multi-month, refunds and sparse-history fixtures |
| A04 | Nightly agent schedule | User-enabled WorkManager one-time chain, 01:00 inclusive / 06:00 exclusive, battery-not-low and device-idle constraints | Window boundary tests; physical Android 8.1 Doze/reboot/OEM timing remains |
| A05 | Skill learning and governance | Exact-description category candidates, leave-one-out validation, versioned data-only rules, explicit approval/rejection | SkillLearningEngine and planner tests; add active-skill rollback/deactivation and migration fixture tests |
| A06 | Daily learning report | Room-persisted per-day operations, intelligence, skill and next-improvement summaries shown in Settings | Entity/DAO integration and CI; add process-death and interrupted-worker persistence tests |
| A07 | Cross-agent coordination | Finance Operations review is passed as untrusted context to Financial Intelligence; its advisory findings are passed to Skill Improvement. All stages share the same ledger, skill registry and model runtime. | Add persistent agent memories, structured task/message history and evaluation feedback in Phase 10 |

Agent work is not generic device automation. No agent can perform arbitrary shell, filesystem, AccessibilityService or non-financial actions. Model output is advisory; the deterministic finance layer remains authoritative.
