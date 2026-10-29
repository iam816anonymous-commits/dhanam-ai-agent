# Architecture decisions and agent tool contract

These decisions govern implementation unless a later documented ADR is approved. They deliberately separate probabilistic language interpretation from deterministic financial truth.

## ADR-01 — Native Android/Kotlin application

**Decision:** Build a native Android application using Kotlin, Jetpack Compose and Material 3, targeting API 27+ unless a documented dependency/runtime blocker requires reconsideration.

**Rationale:** The reference is an Android app, and core features include local Room persistence, notifications, biometric APIs, voice, file sharing, and constrained-device support. Preserve the original dark emerald / warm-gold visual direction and recognizable navigation.

**Gate:** Resolve Gradle/AGP/Kotlin/KSP compatibility and prove clean builds from a fresh checkout before treating the toolchain as stable.

## ADR-02 — Room/SQLite is the source of truth

All finance records and user-selected budgets are read from and written to the local database through repositories/domain services. UI state, model context, conversation history, and model output are not authoritative financial state. Migrations must preserve user data or stop safely.

## ADR-03 — Exact money representation

Canonical money is integer paise in `Long` (or a separately reviewed exact-decimal type if a domain case cannot fit), with validated conversion at input/output boundaries. Reject NaN/infinity, overflow, negative values where prohibited, and ambiguous currency/scale. Formatting to rupees is a presentation concern only.

## ADR-04 — Model is planner, never executor

The optional local model can classify intent, extract candidate fields, choose from registered tools, and produce an explanation. It cannot call SQL, access arbitrary files, invoke arbitrary code, directly mutate state, invent record IDs, or authorize itself. A deterministic parser/fallback remains available when model runtime is unavailable or output is invalid.

## ADR-05 — Bounded observe/plan/act loop

Each user request follows:

`RECEIVED → CONTEXT_BUILT → PLANNED → VALIDATED → AWAITING_CONFIRMATION (if required) → EXECUTING → VERIFYING → SUCCEEDED | FAILED | CANCELLED`

- Bound tool-call count, retries, execution duration, and context size.
- Persist the minimum state needed to recover or explain an interrupted operation.
- Cancellation before a write prevents the write; cancellation during a transaction follows transaction semantics and must not report success until verified.
- No unbounded self-replanning. On repeated failure, stop and ask the user or fall back.

## ADR-06 — Typed allow-listed tool registry

Every agent tool has a stable ID, typed input schema, domain validator, read/write classification, authorization policy, confirmation rule, timeout, and typed result. Unknown tool IDs and unknown/extra arguments fail closed unless the schema explicitly permits them. The registry is the only route from the agent to domain operations.

Initial illustrative inventory:
- Read: `expense.list`, `expense.get`, `insights.spending_summary`, `budget.get`, `khata.list`, `khata.get`.
- Write: `expense.create`, `expense.update`, `expense.delete`, `budget.update`, `khata.create`, `khata.repay`, `khata.update`, `khata.delete`, `data.clear_all`.
- Navigation: `navigation.open_screen`, restricted to registered in-app screen IDs.
- Import/export: dedicated tools with preview and validation; bulk import requires explicit approval.

Names are proposed IDs, not a claim that handlers already exist. Final schemas must be versioned and tested.

## ADR-07 — Example structured tool call

Illustrative plan only:

```json
{
  "schema_version": 1,
  "tool": "expense.create",
  "arguments": {
    "amount_paise": 50000,
    "category_id": "food",
    "title": "Groceries",
    "occurred_on": "2026-10-28"
  },
  "requires_confirmation": false
}
```

The executor must not trust `requires_confirmation` supplied by the model. Confirmation requirements are recomputed from trusted tool metadata and the validated action. Record IDs, dates, amounts and categories must be validated against domain rules; an absent required field triggers clarification rather than guessing. The date is an example, not a default date rule.

## ADR-08 — Financial action policy

- **Read-only:** may execute after schema/domain validation and local privacy checks.
- **Ordinary reversible write:** show a clear preview/result and execute only when all required fields are unambiguous. Reuse the same domain service as manual UI actions.
- **Consequential/destructive write:** bind explicit confirmation to the exact action, target record IDs, material values and expiry. Includes deletes, loan repayment/settlement, bulk imports, and clear-all.
- **Bulk operations:** preview count and effect, require explicit confirmation, execute transactionally where feasible, and report rollback/partial outcome truthfully.
- **Retries:** use idempotency/request keys where possible; never repeat a non-idempotent write merely because the UI timed out.
- **Postcondition:** read back the persisted record(s) and verify expected values before reporting success.

A generic “yes” must never confirm a different or stale action. The UI and model cannot weaken the policy.

## ADR-09 — Local inference and explicit model downloads

No cloud inference or financial-data uploads. Model artifacts are optional and downloaded only after the user explicitly chooses Download. Each catalog entry must pin an official source, exact artifact/version, license, expected byte size and SHA-256. Verify HTTPS, redirect targets, size, hash and available storage; download to a temporary file and activate atomically only after verification. Cancellation/failure must not corrupt the currently active model.

Model download is the only planned network use. Android's INTERNET permission is app-wide, so this is an architectural restriction with tests and audit, not a platform-enforced per-component permission boundary. No analytics/telemetry endpoints, no model auto-download, no remote fallback.

## ADR-10 — Model resource eligibility

One shared policy evaluates API level, total RAM, available RAM, free storage, artifact size, estimated peak memory, model/runtime compatibility and device profile. Check both when showing a model option and immediately before download/activation. Thresholds must be derived from the candidate runtime's documented needs and physical-device measurements, with a safety margin. Do not show a large model option when the device fails the policy. If a model crashes or exceeds limits, disable it safely and fall back to deterministic behavior.

A prior research candidate (Qwen3 0.6B, roughly 331 MiB artifact) is not approved for release until exact official artifact metadata is reverified and tested on the target phone. RAM heuristics alone do not prove runtime reliability.

## ADR-11 — No general Android control

This app may navigate its own registered screens and invoke its own finance tools. It will not use AccessibilityService or another mechanism to click/type into arbitrary apps, launch arbitrary actions on the user's phone, or act as a system-wide autonomous agent. That is a separate project and threat model.

## ADR-12 — Minimal, privacy-safe local audit

Record tool ID, validated target identifiers, timestamps, state transitions, outcome code, and verification status only as needed for recovery and troubleshooting. Do not log raw financial prompts, full expense history, PINs, biometric data, model secrets, or unnecessary personal data. Logs must remain local and be bounded/clearable under documented policy.

## ADR-13 — UI and accessibility

Keep the original emerald/gold design direction while ensuring readable contrast, scalable text, semantic labels, keyboard/IME behavior, TalkBack support and reduced motion. Animation is decorative and must not block core workflows or continue consuming resources unnecessarily.

## ADR-14 — Migration and app identity

Planned default: preserve `applicationId = com.aistudio.dhanam.expense` and `dhanam_expense_database` to make an in-place upgrade possible. Do not release until the signing certificate matches the existing installed app and migration fixtures prove all rows/preferences survive. If those prerequisites cannot be met, use a new application ID and provide a documented user-controlled export/import path; never silently wipe the old DB.

## ADR-15 — Dependency selection

Use the smallest compatible dependency set. Review serialization libraries and avoid duplicate JSON stacks. Candidate components include Compose/Material 3, Room, KSP, coroutines/Flow, Navigation Compose, WorkManager, Android biometric/Keystore APIs, JUnit, coroutine test utilities, Room/Robolectric and instrumentation/UI tests. LiteRT-LM 0.18.0 is an evaluation candidate from the reference project, not an unconditional dependency decision. Exact versions are a Phase 1 build gate, not guessed here.

## Minimum safety invariants

1. No write from unvalidated model output.
2. No destructive action without confirmation bound to the exact target and action.
3. No duplicate write on retries in the defined regression suite.
4. No success response without read-back verification.
5. No financial data sent to remote services.
6. No inference network dependency after the model is installed.
7. No fabricated records, budgets, tool results, or history.
8. No model eligibility based solely on file size or total RAM.
