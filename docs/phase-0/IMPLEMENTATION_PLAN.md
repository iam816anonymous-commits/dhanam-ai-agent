# Implementation phases, dependencies and acceptance gates

This is the approved-work proposal for the new repository. Each phase is one coherent, reviewable batch; related changes are grouped instead of committing file-by-file. No later phase begins automatically.

## Phase 0 — Audit/specification (current)

**Deliverables:** repository findings, feature matrix, architecture/tool contract, privacy/threat model, dependency/runtime plan, acceptance tests and explicit unresolved decisions.

**Acceptance:** all feature areas mapped to reuse/adapt/rebuild, critical trust boundaries documented, open questions and release blockers explicit, user accepts specification. This phase is documentation-only; no app implementation and no build claim.

## Phase 1 — Foundation, navigation and visual system

**Scope:** initialize the Android project, Gradle wrapper/version catalog, CI, application identity, Compose theme, navigation/state architecture, reusable components, empty/error/loading states and real screen skeletons matching the original emerald/gold UI. No fake financial records or mock “successful” agent actions in production UI.

**Acceptance:**
- Clean checkout assembles a debug APK in CI.
- Android lint and unit tests pass.
- App launches and navigation works on API 27 and a current API emulator/device.
- No production demo data, hardcoded totals, dead buttons or claims of implemented features.
- Build configuration has one coherent pinned dependency catalog and no committed signing secrets.
- Package ID/database continuity decision is verified or the alternative import plan is documented.

## Phase 2 — Finance parity and data integrity

**Scope:** local schema/entities/DAOs/repositories, expense CRUD, categories, overall/category budgets, Khata, repayments, insights, historical queries, CSV/PDF, reminders, settings/security and data migrations. Rebuild features against the matrix; do not defer whole parity areas silently.

**Acceptance:**
- Every F01–F20 and F26–F30 item has a test or an explicitly approved exception.
- All money calculations use exact values and are tested at boundaries.
- Room migrations preserve records/preferences and do not silently clear data.
- Writes are transactional where required; import has validation/preview and defined duplicate policy.
- No remote network calls are required for finance workflows.
- CSV/PDF files use scoped URIs and notification permissions/errors are handled.

## Phase 3 — Model catalog, download and runtime

**Scope:** model metadata/catalog, shared resource eligibility policy, explicit download UI, verified downloader, cancellation/retry/cleanup, atomic activation, runtime abstraction and diagnostics. Start with the smallest candidate only after verifying official artifact/license/hash. Model remains optional.

**Acceptance:**
- No download button for ineligible devices; eligibility rechecked before download/activation.
- Artifact size/hash/license/source verified before activation; interrupted/corrupt download cannot replace the working model.
- UI stays responsive and shows progress/errors accurately.
- Installed inference works in Airplane mode; no cloud fallback.
- Physical-device report records API, RAM/storage, startup, latency, peak memory, thermal/battery observations and failure recovery.
- If candidate fails, app remains usable through deterministic fallback.

## Phase 4 — Agent brain and read-only tools

**Scope:** context builder, deterministic fallback integration, strict structured planner, tool registry, read-only tools for expense/loan/budget/history and safe in-app navigation, bounded orchestration state machine.

**Acceptance:**
- Answers are grounded in actual DB query results and identify the date range used.
- Malformed output, unknown tool, unknown fields, invalid date/amount and out-of-scope request fail closed or ask for clarification.
- No write-capable tool is reachable from this phase.
- Benchmark includes English and Telugu; unsupported language is not falsely claimed.
- Timeout/cancel/runtime failure returns a clear result without blocking manual app workflows.

## Phase 5 — Validated write tools and authorization

**Scope:** expense create/update/delete, budget updates, loan create/update/repayment, imports and destructive operations; confirmation tokens, idempotency, transactions, read-back and recovery.

**Acceptance:**
- Zero unauthorized writes and zero unconfirmed destructive actions in the defined regression suite.
- Zero duplicate writes in defined retry/timeout/crash tests.
- Every write validates required fields and resolves targets from the DB.
- Over-repayment, negative/overflow amounts, ambiguous target and stale confirmation are rejected.
- No success is reported before read-back verification.
- Rollback or explicit recovery behavior is tested for failure injection.

## Phase 6 — Conversational quality, history and voice

**Scope:** multi-step clarifications, correction/undo workflows, conversation state, voice adapters, English/Telugu polish and model evaluation. Do not make other languages generally available without passing tests.

**Acceptance:**
- Numerical model quality thresholds are set before the benchmark and reported with sample size, device, language and error categories.
- Ambiguous fields prompt questions rather than guessed writes.
- Text fallback remains available when speech recognition/TTS is absent, denied or online-only.
- Voice lifecycle and cancellation are tested; no claim of offline ASR without device proof.

## Phase 7 — Hardening and release readiness

**Scope:** full regression, security/privacy review, low-memory/device testing, accessibility, API 27 compatibility, performance, APK/package verification and release documentation.

**Acceptance:**
- No critical finance-integrity or privacy defect remains open.
- All matrix features have passing evidence or an explicit user-approved exception.
- CI proves lint/tests/build/APK artifact; physical-device testing separately proves model/runtime and OEM behavior.
- Signing continuity and real legacy-data migration are verified before claiming upgrade safety.
- Final report lists what was tested, where, results, limitations and known issues.

## Candidate dependency plan (not yet pinned)

Prefer native Kotlin and a small dependency set:
- Android Gradle Plugin + Kotlin/Compose compiler/plugin + KSP versions proven compatible together.
- Jetpack Compose BOM + Material 3, Activity Compose, Lifecycle ViewModel/Runtime, Navigation Compose.
- Room Runtime/KTX/compiler with schema export; KSP rather than mixed annotation processing.
- Kotlin coroutines/Flow.
- WorkManager for resilient reminders/download work where appropriate.
- AndroidX Biometric and Keystore APIs for security features.
- One JSON/schema stack (Moshi or kotlinx.serialization after compatibility review), not overlapping serializers without reason.
- HTTPS downloader/client only for explicit model artifacts; no general-purpose API integration.
- JUnit, coroutine test utilities, Room/Robolectric, Android instrumentation and Compose UI tests.
- LiteRT-LM 0.18.0 is a **candidate to evaluate**, not an approved dependency. Verify runtime support, API level, ABI, artifact license, memory, and tool-call quality before integration.

The legacy project uses AGP 9.1.1/Kotlin 2.2.10, while its and nextgen's version catalogs differ for Compose BOM, Room and Lifecycle. Do not blindly copy either catalog. Verify dependency resolution and clean build first; pin the validated set in Phase 1. Review transitive dependencies and licenses.

## Test strategy

### Unit/domain tests
- Currency conversion, paise overflow, rounding, formatting and invalid values.
- Category alias/canonicalization; empty/unset budgets versus zero budgets.
- Month boundaries, timezone, leap years, day-count semantics, projections and safe-to-spend.
- Loan principal/repayment/outstanding calculations, partial/full settlement, overpayment rejection.
- Parser behavior, English/Telugu intent/entity extraction, ambiguity and out-of-scope.
- Schema validation, unknown tools/fields, invalid IDs, date parsing and risk policy.

### Database/integration tests
- Room CRUD, transactions, migrations and data fixture round trips.
- Legacy version-1 database fixture migration including expenses, loans, category budgets and related preferences.
- Idempotency under retries; process death between write and verification; rollback/failure injection.
- CSV import duplicates/malformed rows/encoding and export escaping.
- Reminder deduplication and settings persistence.

### Model evaluation
- Exact artifact hash/license and offline startup.
- English/Telugu intent classification, required-field extraction, tool selection, valid argument generation and refusal/clarification.
- Invalid JSON and semantically invalid but syntactically valid output.
- Latency percentiles, peak memory, crash rate, storage, temperature/battery observation on actual target device.
- Predefine numerical acceptance thresholds before evaluation; report sample size and failure categories. No arbitrary “model works” claim from one successful prompt.

### End-to-end/privacy tests
- All core finance workflows in Airplane mode.
- Network allow-list/redirect/hash/size tests and inspection that no finance context leaves the device.
- Exact confirmation binding for delete/repay/bulk import/clear-all.
- API 27 and current API launch/navigation, UI state and accessibility tests.
- APK exists, package metadata correct, and CI does not pass when build/test/artifact steps fail.

## Open-source research gate — mandatory for every feature

Before implementing any F01–F30 feature, follow [the open-source research protocol](../research/OPEN_SOURCE_RESEARCH_PROTOCOL.md) and add an evidence entry to [the feature research log](../research/FEATURE_RESEARCH_LOG.md). Inspect current source code, tests, release/activity status, license, dependency tree, and official platform documentation; record exact commits/tags and source paths. For critical paths, compare multiple credible implementations where available. Candidate repositories are not pre-approved dependencies, and README claims alone are not implementation evidence.

Every implementation batch must include the chosen pattern, alternatives rejected and why, Dhanam-specific changes, tests and actual results, CI evidence, and physical-device checks where needed. If source research conflicts with an existing ADR, update the ADR and explain the impact before implementation. A feature is not complete until both the feature parity matrix acceptance evidence and its research record are satisfied.

## Execution and change control

- Work in coherent phase batches and update tests and documentation together.
- Report exact commands and actual results. Never claim physical-device validation from CI alone.
- Stop at phase acceptance gates and ask for approval before starting the next phase.
- If source audit contradicts this plan, update the ADR/matrix and explain the impact before implementation.
