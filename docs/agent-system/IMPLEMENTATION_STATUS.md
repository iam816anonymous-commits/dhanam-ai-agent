# Dhanam AI Agent — implementation status

This document distinguishes implemented behaviour from planned work. A feature is not considered complete merely because a screen or model prompt exists.

## Product boundary

Dhanam is a finance-only personal finance application. Its agents may work with expenses, income, budgets, lending, borrowing, repayments, recurring payments, financial reports, finance-specific skills and financial education. Generic device control, arbitrary code execution, general web browsing and unrelated assistant tasks are out of scope.

The existing Compose UI and emerald/gold theme remain the application UI. Agent activity and nightly reports are added inside Settings; they do not replace the existing navigation or finance screens.

## Three specialist roles

| Agent | Implemented responsibility | Guardrail |
|---|---|---|
| Finance Operations | Deterministic expense/income/budget/Khata command planner, validated previews and confirmed writes; read-only chat analysis for current/previous-month spending, category-specific reduction and outstanding lending/borrowing; nightly ledger integrity review | Never commits a model proposal without app validation and user confirmation |
| Financial Intelligence | Deterministic local-ledger analysis for month-to-date category movement, budget overruns, income/expense cash flow when income is recorded, lending/borrowing outstanding, recurring expense candidates and month-scoped user recommendation feedback; local model may explain the evidence | Calculations and evidence come from Kotlin/Room, not LLM arithmetic |
| Skill Improvement | Learns exact-description-to-category rules from repeated local expense history using leave-one-out validation; local model can suggest safer tests | A proposal is not active until the user approves it; no generated code or executable tools |

## One shared model

- `LocalInferenceRuntimeRegistry` owns one process-wide `LocalInferenceViewModel`.
- The Compose UI and WorkManager worker obtain the same model owner.
- That owner creates one LiteRT-LM Engine and one `SharedAgentCoordinator`.
- The coordinator serializes specialist inference, bounds queued requests, caps background inference duration and rejects background tasks outside the device-local 01:00–06:00 window.
- The nightly worker runs Finance Operations, Financial Intelligence and Skill Improvement sequentially through that same coordinator. **All model inference by all three agents is restricted to 01:00–06:00 local time.** During the day, Finance Operations still handles supported deterministic expense, income, budget, lending and repayment commands plus read-only spending/Khata analysis without invoking the model. The Settings model test console follows the same window.
- The worker may load only the user's already-downloaded preferred model. The existing model resource-eligibility and file SHA-256 checks still run before Engine initialization. A worker-loaded model is unloaded afterward if it is idle; a model already loaded by the app is not unloaded by the worker.

A user-facing request is not gated by the nightly window. Background agent tasks are.

## Nightly schedule and report

- The user enables/disables the schedule in Settings.
- WorkManager schedules a one-time run for 01:00 local time, with battery-not-low and device-idle constraints.
- The worker rechecks the exact time window before each model task. Each background model call is bounded by the remaining time and a 120-second per-task cap. A completed nightly report is saved after the three specialist stages.
- WorkManager is best-effort Android background execution, not a guarantee that the phone will execute for five continuous hours. Doze, battery state, OEM restrictions and force-stop can delay or prevent execution. A cancelled or failed run is retried and the next date is rescheduled after the retry limit.
- The worker first performs deterministic analysis and skill validation. If the preferred model is unavailable, incompatible, not selected or there is insufficient time, the deterministic work still runs and the report says model inference was skipped. WorkManager retries the whole nightly session after process death; persistence is idempotent for daily reports, role memories, handoffs and versioned skills, but model inference may be repeated because there is no per-agent task checkpoint table.
- Reports are stored locally in Room. The report shows operations checks, financial findings, skill proposals, model status and next improvements.
- Room v7 stores one bounded latest memory per role and a 90-day structured inter-agent handoff history. Room v11 makes handoff rows idempotent across retries. The Settings screen exposes both and provides a confirmed action to clear only specialist memories and handoffs while preserving financial records, skills, feedback and reports. Previous memories are passed back as untrusted historical context, never as current financial evidence.
- Report rows store summaries, not raw model prompts. Skill rules are data-only exact-match mappings.

## Skill governance

A skill candidate is proposed only when the same normalized expense description occurs on at least three distinct dates, maps consistently to a supported category, and passes at least 90% leave-one-out validation.

The candidate is stored as a versioned data-only rule. It cannot contain code, regex, URLs, permissions or arbitrary tool definitions. A user can approve or reject it in Settings. Only approved rules are used by the expense command planner, and matching uses Unicode-aware word boundaries to avoid substring collisions.

## Database changes

- Room schema version 5 adds `agent_learning_reports`.
- Room schema version 6 adds versioned `agent_skills`.
- Room schema version 7 adds bounded role-specific `agent_memories` and structured `agent_messages` handoff history.
- Migrations 1→2 through 10→11 are additive. Existing expense, budget, Khata and repayment records are preserved.
- Room schema version 8 adds a separate `income_records` ledger; version 9 adds unique import source keys for repeatable CSV imports. Income is included in the monthly cash-flow dashboard, nightly aggregate context and full-ledger CSV export; a dedicated income CSV import/export path validates and previews rows before transactional idempotent import.
- Room schema version 10 adds month-scoped recommendation feedback; the worker retains the latest 12 months and supplies aggregate helpful/not-useful feedback to Financial Intelligence. Version 11 normalizes old active skill versions and makes nightly inter-agent handoff rows idempotent across WorkManager retries. Skill approvals supersede the previous active version and Settings exposes transactional rollback to the prior validated version. The chat agent answers read-only current/previous-month spending, category-specific reduction and Khata outstanding queries from deterministic local analysis.
- Financial amounts remain integer paise. LLM outputs are never used for financial arithmetic or direct writes.

## Model catalogue and device eligibility

The model manager now offers six pinned LiteRT-LM artifacts in three tiers (two choices per tier): Compact (SmolLM2 360M, Qwen3 0.6B INT8), Balanced (Qwen3 1.7B INT4, Qwen2.5 1.5B INT8), and Advanced (Qwen3 4B Mixed INT4, Phi-4 Mini Reasoning INT4). Every artifact has a pinned revision, expected byte length and SHA-256.

Download eligibility now checks Android-reported physical total RAM and free app storage only. Run-now eligibility additionally checks currently available system RAM. This prevents temporary RAM pressure from hiding a model that can be downloaded for later. The settings UI shows all six entries, each model's independent download/run status, and the exact Android-reported total RAM, available RAM and free app storage. After engine creation, process peak RSS is checked against the selected model's own budget.

The app explicitly disables unbounded thinking and caps generation to 512 output tokens. Phi-4 Mini Reasoning is reset to a fresh conversation after each response because its publisher documents a LiteRT-LM 0.18.x multi-turn cache issue. None of the six models is yet physically qualified on the user's handset; download, initialization, output correctness, peak RSS, heat and battery still require device tests.

See [feature parity audit](FEATURE_PARITY_AUDIT.md) for the original Dhanam and earlier-agent comparison.

## Latest CI verification

On 2026-10-30, Android CI run #277 completed its build job successfully in 2m57s. It passed `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:assembleRelease`; the workflow also verified all three APK files and uploaded the `dhanam-ai-apk-artifacts` archive. This validates compilation and unit tests, not execution of instrumentation tests on a physical device or emulator.

## Current limitations / next phases

1. **Physical-device qualification remains mandatory.** CI cannot prove model loading, peak memory, thermal behaviour, battery impact, WorkManager timing or Android 8.1 OEM background behaviour on the user's handset.
2. **WorkManager is not a five-hour guaranteed reservation.** It runs eligible work when Android permits. A process-death retry restarts the nightly sequence; persistent writes are idempotent, but inference can repeat because per-agent task checkpoints are not implemented.
3. **No neural-weight training occurs.** "Self-improvement" currently means validated, versioned local rules and model-generated review suggestions. It does not fine-tune Qwen or rewrite source code.
4. **The specialist queue is serialized, not preemptive.** A currently running inference is allowed to finish or time out; a user request cannot interrupt a token already being generated.
5. **Learning is bounded and auditable.** Memory keeps the latest summary per role, handoffs are retained for 90 days, recommendation feedback for 12 months, and approved skill versions can be rolled back. Longitudinal outcome evaluation and automatic measurement of whether recommendations actually reduced spending remain to be implemented.
6. **Broader financial planning** (income forecasting, cash-flow runway, debt payoff scenarios, user-defined savings targets and multi-month trend confidence) remains to be implemented and tested.
7. **Release qualification** still requires the final CI result, APK artifact verification, migration testing on a copy of existing user data, and real-device smoke tests.

## Verification policy

A phase is complete only when its code, tests, database migration (if any), UI path and CI checks are present. Device-specific claims require physical-device evidence. Do not mark an agent as having learned a skill unless its deterministic tests pass and the resulting proposal is visible to the user.
