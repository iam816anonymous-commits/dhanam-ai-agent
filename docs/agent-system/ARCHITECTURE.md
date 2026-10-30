# Dhanam Financial Intelligence System

Status: architecture baseline for the next implementation phases.
Target device: Android 8.1+ (minSdk 27), with the first qualification target being a 4 GB RAM / 64 GB storage phone.
Inference policy: one local model, one engine, one active conversation, one inference task at a time.

## Product boundary

Dhanam is a personal financial agent, not a general-purpose assistant. It may explain its own finance features and help the user operate them. It must not accept unrelated generic tasks, control the device, launch arbitrary apps, execute shell commands, or use AccessibilityService.

All financial data remains in the local Room database. No cloud inference, financial-data upload, telemetry containing financial content, or automatic network research is permitted. Model downloads remain an explicit user action from a pinned source with artifact size, SHA-256, license, device-resource and storage checks.

Money is represented as integer paise. Financial writes require deterministic validation, a reviewable preview, explicit user confirmation, a database transaction/idempotency key where applicable, and read-back verification.

## Three specialised agents

### 1. Finance Operations Agent

Purpose:
- Record, correct and categorise expenses.
- Maintain monthly and category budgets.
- Track money lent, money borrowed, due dates and repayments.
- Answer questions about recorded transactions and cash flow.
- Detect duplicate entries, unusual spending, budget overruns and recurring expense candidates.
- Generate practical, quantified expense-reduction recommendations.

It may read financial records through approved repository methods. It may stage writes, but cannot commit a write without explicit confirmation. It must never invent missing transactions or balances.

### 2. Financial Intelligence Agent

Purpose:
- Analyse deterministic summaries and time-series derived from the local ledger.
- Compare this month with prior months and comparable periods.
- Identify category drift, recurring spend, avoidable fees, subscriptions, impulse-spend patterns and cash-flow risks when the data supports those claims.
- Produce ranked savings opportunities with estimated monthly/annual impact, evidence, confidence, trade-offs and a suggested experiment.
- Maintain a curated, source-labelled set of financial principles and explain which principles informed a recommendation.

It must separate observed facts, estimates, hypotheses and general educational guidance. It must not give investment, tax or legal instructions as personalised professional advice. It cannot change the ledger or budgets without user confirmation.

### 3. Skill and Improvement Agent

Purpose:
- Learn how to use Dhanam's own approved tools and workflows.
- Review anonymised/local task outcomes, validation failures and user-approved corrections.
- Propose better intent patterns, workflow playbooks, help content and test cases.
- Run deterministic tests against proposed skill changes.
- Present a daily learning report: what it learned, evidence, tests passed/failed, what it proposes to improve and what requires user approval.

It cannot modify production source code, database schema, financial policy, model weights, permissions or its own guardrails. It can propose a versioned skill/playbook update; the app validates and tests it, and the user approves activation. “Self-improvement” means verified skill and workflow improvement, not autonomous model retraining.

## Shared model runtime and task broker

There is exactly one ModelRuntime owning the LiteRT-LM Engine. Agents never instantiate an Engine directly.

The runtime contract:
- One Engine per process.
- One active Conversation/KV cache at a time.
- A single FIFO/priority task queue with bounded queue length and per-task deadline/token budget.
- Mutual exclusion around every inference request.
- Agent role instructions are supplied per task; no agent keeps its own resident conversation.
- Each task receives only the minimum required context. Finance data is summarised by deterministic Kotlin/SQL logic before any model prompt is assembled.
- Prompt/output lengths are bounded. Output is treated as untrusted and parsed into strict typed schemas.
- Check process memory before load, before generation and between output chunks. On limit breach or OOM: cancel generation, close the conversation, unload the engine, persist a failure event without sensitive prompt text, and do not retry-loop.
- User-initiated operations have priority over background learning. Background tasks yield immediately when the app needs the model for an interactive operation.
- No parallel model execution and no second KV cache.

## Persistent specialist memory and handoff history

Room schema version 7 adds two local-only tables:
- `agent_memories`: one bounded latest summary per role/key, with source report date and confidence. Each role reads only its own prior memory before its next nightly task.
- `agent_messages`: structured, bounded handoff summaries between different specialist roles. Records are retained for 90 days and displayed in Settings.

Memory is historical context, not a source of current balances or transaction facts. Each task must re-read current aggregates from the financial ledger. Model-generated summaries are explicitly marked untrusted when supplied to a later agent. Raw prompts and raw transaction descriptions are not persisted in these tables.

## Nightly learning window

Background agent inference is allowed only from 01:00 inclusive to 06:00 exclusive in the device's local time.

Suggested nightly order:
1. Finance Operations Agent creates a deterministic ledger snapshot and checks data integrity.
2. Financial Intelligence Agent analyses the snapshot and drafts recommendations.
3. Skill and Improvement Agent reviews safe, local task metrics and proposes tested workflow improvements.
4. Report generator stores one daily report and schedules a user-visible notification.

The broker checks the time window before every task, not just when the worker starts. At 06:00 it stops accepting new background tasks, cancels or safely checkpoints the active task, releases the model, and records the run as partial if incomplete.

Android WorkManager is inexact and may be delayed by Doze, battery optimisation, force-stop or vendor background restrictions. The app must not claim guaranteed execution at 01:00. It should enqueue a unique, constrained nightly job, re-check the time window on entry, and safely defer to the next eligible night when execution is not possible. No exact-alarm permission is required for the first implementation.

The schedule is local-only and user-configurable only by enabling/disabling the nightly learning feature; the allowed window remains 01:00–06:00.

## Daily learning report

Persist a local report with:
- report date and run status (complete, partial, skipped, failed);
- which agents ran and their durations/token counts;
- facts learned from local data, with references to local aggregate IDs/date ranges rather than raw transaction text;
- financial observations and recommendations, each with evidence, confidence and estimated savings range;
- new or revised skill proposals, test results and version;
- what the system proposes to improve next;
- approvals required and explicit actions taken by the user.

The report UI must distinguish “learned/observed”, “recommended”, “proposed”, “tested” and “activated”. A proposed change is not described as learned or active until validation and approval are complete.

## Financial analysis method

Use deterministic analysis first; use the model for explanation and prioritisation, not arithmetic.

Minimum analysis:
- current month and previous month category totals;
- comparable month-to-date windows;
- rolling 3-month and 6-month category averages when sufficient data exists;
- recurring merchant/title candidates only when repeated records support the inference;
- budget utilisation and projected month-end spend, clearly labelled as estimates;
- outstanding lent/borrowed principal and upcoming due dates;
- data coverage warnings (missing months, few transactions, imported-only records).

Every saving recommendation must include:
- observed evidence and period;
- proposed change;
- estimated monthly and annual savings, or “not enough data”;
- confidence and assumptions;
- a safe next step or reversible experiment.

Never recommend a cut that would imply a user's essential medical, housing, food or other critical spend is wasteful solely because it is high.

## Delivery phases and acceptance gates

### Phase A — Stabilise current runtime and finance agent
- Reproduce and classify the reported native crash on the actual device.
- Keep Android 8.1 text-based offline expense entry working independently of voice.
- Add bounded inference queue, cancellation, single-conversation lifecycle and crash-safe unload/reload.
- Add tests for memory guards, queue serialization, cancellation and model-unavailable fallback.
- Confirm expense, budget and Khata flows remain deterministic and confirmation-gated.

Acceptance: CI green; unit tests, lint and debug APK pass; no unbounded generation; actual device smoke test records model load, inference, peak RSS, latency and crash outcome.

### Phase B — Finance analysis and savings recommendations
- Add repository-backed aggregate queries and deterministic analysis.
- Add evidence-backed savings insight models and UI.
- Add tests for month boundaries, sparse data, refunds/negative values if supported, duplicates and integer-paise arithmetic.

Acceptance: every recommendation can be traced to ledger evidence and no model-generated arithmetic is trusted.

### Phase C — Shared agent orchestration
- Introduce typed agent IDs, permissions, task envelopes, queue persistence and one ModelRuntime.
- Add role-specific prompts, strict output schemas and priority scheduling.
- Add cross-agent coordination through shared task records and immutable analysis snapshots.

Acceptance: instrumentation proves no concurrent inference and no second Engine/KV cache is created by any agent.

### Phase D — Nightly learning
- Add WorkManager unique nightly work and 01:00–06:00 local window checks.
- Add resumable checkpoints, battery/charging/idle constraints, cancellation at 06:00 and retry/defer policy.
- Add daily report persistence, UI and notification.

Acceptance: tests cover early/late execution, daylight-saving/time-zone changes, Doze deferral, process death, duplicate work and partial runs.

### Phase E — Skill governance and controlled improvement
- Add versioned local skill/playbook proposals, deterministic test harness and user approval.
- Add rollback and audit history.
- Add curated finance knowledge with provenance and review dates.

Acceptance: no agent can activate an untested or unapproved skill or alter production policy/model files.

### Phase F — Device qualification and release
- Test on the Tecno Android 8.1 / 4 GB target and a newer Android device.
- Measure cold/warm load, inference latency, peak RSS, battery impact, thermal behaviour, storage use and background scheduling.
- Run database migration/restart, low-memory, interrupted-write and recovery tests.
- Publish known limitations and release checklist.

Acceptance: CI and APK build green, instrumentation tests pass where supported, and physical-device evidence is recorded. CI cannot substitute for the physical-device smoke test.

## Non-goals

- Three separate model copies or parallel model inference.
- Autonomous model fine-tuning on the phone.
- Autonomous source-code modification or deployment.
- Generic personal assistant behaviour unrelated to finance.
- Cloud processing of the user's financial records.
- Guaranteed exact background execution under Android power-management restrictions.
