# Dhanam AI Agent — implementation status

This document distinguishes implemented behaviour from planned work. A feature is not considered complete merely because a screen or model prompt exists.

## Product boundary

Dhanam is a finance-only personal finance application. Its agents may work with expenses, income, budgets, lending, borrowing, repayments, recurring payments, financial reports, finance-specific skills and financial education. Generic device control, arbitrary code execution, general web browsing and unrelated assistant tasks are out of scope.

The existing Compose UI and emerald/gold theme remain the application UI. Agent activity and nightly reports are added inside Settings; they do not replace the existing navigation or finance screens.

## Three specialist roles

| Agent | Implemented responsibility | Guardrail |
|---|---|---|
| Finance Operations | Existing deterministic expense/budget/Khata command planner, validated previews, confirmed writes; nightly ledger integrity review | Never commits a model proposal without app validation and user confirmation |
| Financial Intelligence | Deterministic local-ledger analysis for month-to-date category movement, budget overruns, lending/borrowing outstanding and recurring expense candidates; local model may explain the evidence | Calculations and evidence come from Kotlin/Room, not LLM arithmetic |
| Skill Improvement | Learns exact-description-to-category rules from repeated local expense history using leave-one-out validation; local model can suggest safer tests | A proposal is not active until the user approves it; no generated code or executable tools |

## One shared model

- `LocalInferenceRuntimeRegistry` owns one process-wide `LocalInferenceViewModel`.
- The Compose UI and WorkManager worker obtain the same model owner.
- That owner creates one LiteRT-LM Engine and one `SharedAgentCoordinator`.
- The coordinator serializes specialist inference, bounds queued requests, caps background inference duration and rejects background tasks outside the device-local 01:00–06:00 window.
- The nightly worker runs Finance Operations, Financial Intelligence and Skill Improvement sequentially through that same coordinator.
- The worker may load only the user's already-downloaded preferred model. The existing model resource-eligibility and file SHA-256 checks still run before Engine initialization. A worker-loaded model is unloaded afterward if it is idle; a model already loaded by the app is not unloaded by the worker.

A user-facing request is not gated by the nightly window. Background agent tasks are.

## Nightly schedule and report

- The user enables/disables the schedule in Settings.
- WorkManager schedules a one-time run for 01:00 local time, with battery-not-low and device-idle constraints.
- The worker rechecks the exact time window before each model task. Each background model call is bounded by the remaining time and a 120-second per-task cap. A completed nightly report is saved after the three specialist stages.
- WorkManager is best-effort Android background execution, not a guarantee that the phone will execute for five continuous hours. Doze, battery state, OEM restrictions and force-stop can delay or prevent execution. A cancelled or failed run is retried and the next date is rescheduled after the retry limit.
- The worker first performs deterministic analysis and skill validation. If the preferred model is unavailable, incompatible, not selected or there is insufficient time, the deterministic work still runs and the report says model inference was skipped.
- Reports are stored locally in Room. The report shows operations checks, financial findings, skill proposals, model status and next improvements.
- Room v7 stores one bounded latest memory per role and a 90-day structured inter-agent handoff history. The Settings screen exposes both. Previous memories are passed back as untrusted historical context, never as current financial evidence.
- Report rows store summaries, not raw model prompts. Skill rules are data-only exact-match mappings.

## Skill governance

A skill candidate is proposed only when the same normalized expense description occurs on at least three distinct dates, maps consistently to a supported category, and passes at least 90% leave-one-out validation.

The candidate is stored as a versioned data-only rule. It cannot contain code, regex, URLs, permissions or arbitrary tool definitions. A user can approve or reject it in Settings. Only approved rules are used by the expense command planner, and matching uses Unicode-aware word boundaries to avoid substring collisions.

## Database changes

- Room schema version 5 adds `agent_learning_reports`.
- Room schema version 6 adds versioned `agent_skills`.
- Room schema version 7 adds bounded role-specific `agent_memories` and structured `agent_messages` handoff history.
- Migrations 4→5, 5→6 and 6→7 are additive. Existing expense, budget, Khata and repayment records are preserved.
- Room schema version 9 adds a separate `income_records` ledger with unique import source keys. Income is included in the monthly cash-flow dashboard, nightly aggregate context and full-ledger CSV export; a dedicated income CSV import/export path validates and previews rows before transactional idempotent import.
- Financial amounts remain integer paise. LLM outputs are never used for financial arithmetic or direct writes.

## Current limitations / next phases

1. **Physical-device qualification remains mandatory.** CI cannot prove model loading, peak memory, thermal behaviour, battery impact, WorkManager timing or Android 8.1 OEM background behaviour on the user's handset.
2. **WorkManager is not a five-hour guaranteed reservation.** It runs eligible work when Android permits; the worker is checkpoint-safe at task boundaries but cannot guarantee the full window.
3. **No neural-weight training occurs.** "Self-improvement" currently means validated, versioned local rules and model-generated review suggestions. It does not fine-tune Qwen or rewrite source code.
4. **The specialist queue is serialized, not preemptive.** A currently running inference is allowed to finish or time out; a user request cannot interrupt a token already being generated.
5. **Persistent memory is currently bounded to the latest verified summary per role plus 90 days of handoff history.** Recommendation feedback, longitudinal evaluation and rollback workflows remain to be implemented.
6. **Broader financial planning** (income forecasting, cash-flow runway, debt payoff scenarios, user-defined savings targets and multi-month trend confidence) remains to be implemented and tested.
7. **Release qualification** still requires the final CI result, APK artifact verification, migration testing on a copy of existing user data, and real-device smoke tests.

## Verification policy

A phase is complete only when its code, tests, database migration (if any), UI path and CI checks are present. Device-specific claims require physical-device evidence. Do not mark an agent as having learned a skill unless its deterministic tests pass and the resulting proposal is visible to the user.
