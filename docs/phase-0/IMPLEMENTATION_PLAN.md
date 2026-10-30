# Implementation phases, dependencies and acceptance gates

Each phase is delivered as a coherent, reviewable batch with its own tests and acceptance gate. The user has requested continued phase-by-phase implementation through release readiness; do not stop for routine feature approval.

## Phase 0 — Audit/specification
Repository findings, feature matrix, architecture/tool contract, privacy/threat model, dependency/runtime plan and acceptance tests.

## Phase 1 — Foundation, navigation and visual system
Android project, CI, Compose theme, navigation, reusable components and honest empty states. Foundation is committed and CI passed; physical-device installation/navigation are not yet verified.

## Phase 2 — Finance parity and data integrity

Each feature requires current open-source research and official documentation review under ../research/OPEN_SOURCE_RESEARCH_PROTOCOL.md, with evidence in ../research/FEATURE_RESEARCH_LOG.md.

### Phase 2A — Local persistence and expense CRUD (current)
- Room/SQLite schema and repository boundary.
- Exact integer-paise money representation and validated rupee input.
- Expense create/read/update/delete UI connected to persistent storage.
- Domain tests and CI verification.
- No seeded sample records, destructive migration fallback or remote calls.

Acceptance: CI build/test/lint pass; unit tests cover invalid values/overflow; device checks verify CRUD and restart persistence.

### Phase 2B — Dashboard, search/filter, category rules and deterministic insights
- Monthly dashboard derived from real records.
- Search/category/date filtering and deterministic period calculations.
- Category taxonomy/aliases with English/Telugu fixtures.
- Explicit timezone, month-boundary, correction and empty-history semantics.

### Phase 2C — Budgets and safe-to-spend
Overall/category budgets, unset-vs-zero distinction, exact arithmetic and boundary tests.

### Phase 2D — Khata and repayments
People/loan records, principal/outstanding, repayment history, atomic writes, stable IDs, idempotency, over-repayment rejection and read-back verification.

### Phase 2E — Import/export and reports
CSV import preview/validation/duplicate policy/transactional commit, CSV export and PDF reports via scoped URIs.

### Phase 2F — Reminders, settings/security and legacy-data migration
WorkManager reminders, settings persistence, researched security controls, and explicit legacy import/migration. No silent data loss.

Phase 2 acceptance: every F01–F20 and F26–F30 feature has implementation evidence or an approved exception, exact money tests pass, migration fixtures prove preservation, and core finance workflows work offline. Phase 2A alone does not claim full parity.

## Phase 3 — Model catalog, download and runtime
- Phase 3 first slice: pinned model catalog, conservative resource gate, explicit foreground download, SHA-256/size verification and app-private file management.
- Runtime dependency and inference remain gated on toolchain compatibility plus target-device memory/stability measurements.
- No cloud fallback; a saved preferred model does not imply the runtime is loaded.
- Acceptance: CI/lint/tests/APK verification pass; corruption, cancellation, retry and low-resource cases are tested; device download and inference smoke tests are recorded separately.

## Phase 4 — Agent brain and read-only tools
Context builder, deterministic fallback, structured planner, typed tool registry and read-only tools.

## Phase 5 — Validated write tools and authorization
Write tools with exact-target confirmation, idempotency, transactions, read-back and failure recovery.

## Phase 6 — Conversational quality, history and voice
Clarifications, correction/undo, conversation state, optional voice, English/Telugu evaluation and lifecycle handling.

## Phase 7 — Hardening and release readiness
Security/privacy review, API 27/low-resource tests, accessibility, performance, migration validation and release documentation.

## Research and change-control rules
- Research current repositories and official docs before every feature.
- Inspect implementation, tests, license, dependency tree and maintenance status; pin exact revisions.
- Compare multiple credible implementations for critical paths where available.
- Record adopted/rejected patterns, tests, CI and device evidence.
- Update ADRs before implementation if research changes architecture/security.
- CI does not imply physical-device validation.
- Continue phase-by-phase implementation through release readiness without waiting for routine user approval; preserve acceptance gates and never claim unverified completion.


## Phase 8 — Finance-only coordinated agents

- Finance Operations, Financial Intelligence and Skill Improvement have separate responsibilities and share one process-wide LiteRT-LM model owner.
- Serialize every model interaction, including interactive chat, against the shared Engine.
- Keep deterministic paise arithmetic and validated repository operations outside the LLM.
- Add local-ledger spending analysis, recurring candidates, budget-overrun checks and savings recommendations.
- Add exact-description skill proposals with holdout validation and explicit user approval.

Acceptance: role-boundary tests, deterministic finance-analysis tests, skill-learning tests, no generic tools, no concurrent Engine access and no direct LLM financial writes.

## Phase 9 — Nightly agent work and learning history

- Schedule best-effort WorkManager work between 01:00 and 06:00 local time with battery/idle constraints.
- Run all three specialists sequentially through the same selected, verified model when device resources and time permit.
- Fall back to deterministic local analysis if the model is not selected, downloaded, compatible or available.
- Persist daily agent reports and proposed improvements locally; expose them in the existing Settings UI.
- Version learned rules and require explicit approval before activation.

Acceptance: exact time-window boundary tests, report persistence/migration tests, model load/unload recovery, idempotent retries, and physical-device overnight tests. Android does not guarantee exact execution timing.

## Phase 10 — Persistent agent memory, evaluation and release qualification

- Add bounded, role-specific local memories and structured inter-agent task/event history. (Implemented in Room v7: one latest bounded memory per role and 90-day handoff history, displayed in Settings.)
- Use previous verified reports as context without treating model-generated text as trusted instructions. (Implemented for bounded prior role summaries; longitudinal evaluation remains.)
- Add feedback on recommendations, regression evaluation and rollback for skill versions. (Month-scoped helpful/not-useful feedback and transactional skill-version rollback are implemented; automated recommendation outcome evaluation remains.)
- Complete remaining feature parity, backup/restore, security review, APK/release signing and Android 8.1 acceptance.
- Measure real-device RAM, thermal, battery and model inference behaviour.

Acceptance: memory isolation tests, privacy/retention tests, recommendation evaluation, complete migration fixtures, CI green and documented physical-device evidence.
