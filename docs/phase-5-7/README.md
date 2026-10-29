# Phases 5–7: safe actions, conversations and release qualification

## Phase 5 — Safe local finance actions

Implemented foundation:
- Deterministic command parsing for supported expense and monthly category-budget requests. Ambiguous/unrecognized requests do not create writes.
- Repayment command requires an exact person name and exactly one open loan in the “I lent” direction; ambiguous matches are rejected.
- Every write is staged as a preview and requires explicit confirmation. Dismiss/cancel writes nothing.
- Amounts are parsed with the existing exact rupee-to-paise parser. Existing expense/category and budget validations are reused.
- Repayment writes use the DAO transaction and unique request ID. Before committing, the target loan and outstanding balance are rechecked.
- The app reads the record back from Room before reporting success. Errors do not report success.

Supported examples:
- I spent ₹250 on lunch
- set food budget to 3000
- record repayment 500 from Ravi

Current limitation: the first pass uses a conservative deterministic command parser rather than trusting free-form model output to invoke tools. The local model is not yet the intent planner for this action flow. The repayment syntax supports only one unambiguous “I lent” loan match. Editing/deleting records and broader commands are not exposed as agent actions.

## Phase 6 — Natural conversations

Implemented foundation:
- In-memory chat transcript while the Agent screen's ViewModel remains alive.
- Natural-ish phrasing for the three supported command families, help/examples, validation feedback and action confirmation/cancellation.
- The transcript and pending action are not written to Room or uploaded.
- A persistent in-app floating assistant orb animates/pulses and moves while indicating thinking, approval or execution status. Tapping it navigates to the Agent screen.

Not yet complete:
- Multi-turn reference resolution and corrections such as “actually change that to 300”.
- Connecting local-model generation to a structured, schema-validated action proposal.
- Voice input and reliable English/Telugu intent coverage. Android speech recognition is not enabled because the standard service may use network recognition; an offline-only implementation must be separately qualified.

## Phase 7 — Verification and release readiness

Automated tests added for action parsing, amount/category mapping, required clarification and rejecting unsupported repayment intents. Existing repository tests cover exact paise conversion, expense validation, Khata validation, repayment idempotency/overpayment checks, and migration/data-transfer logic.

Release gates still required:
- GitHub Actions must pass for the commit containing this implementation.
- Run the UI and database flows on a physical Android device, including process restart and upgrade/migration checks.
- Test low available RAM/storage, cancellation, app background/foreground, interrupted writes, and verify no duplicate repayments.
- Verify model inference on the exact Tecno target device before claiming local-model compatibility.
- Verify the 2 GiB peak-RSS requirement on-device; a pre-load free-RAM threshold is not a guarantee against system-wide pressure.
- Do not declare Phase 5–7 complete until the remaining conversational features and physical-device acceptance tests pass.

## Privacy and safety boundary

The floating orb is an in-app Compose overlay; it does not request Android's system-wide overlay permission, draw over other apps, or use an AccessibilityService. Financial writes remain inside the app and require a user confirmation. No cloud API or network action is used by the command parser.
