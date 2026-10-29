# Phases 5–7: safe actions, conversations and release qualification

## Phase 5 — Safe local finance actions

Implemented:
- Deterministic English commands for expenses and monthly category budgets, plus common Telugu expense/budget phrases. Ambiguous or unrecognized requests do not create writes.
- When the verified local model is explicitly downloaded, selected as preferred, and loaded, it can propose a structured expense/budget intent for natural phrasing. The output is parsed against a strict allowlist and validated again; the model cannot propose repayments or directly execute a write.
- Repayment command requires an exact person name and exactly one open loan in the “I lent” direction; ambiguous matches are rejected.
- Every write is staged as a preview and requires explicit confirmation. Dismiss/cancel writes nothing.
- Amounts are parsed with the existing exact rupee-to-paise parser. Existing expense/category and budget validations are reused.
- Repayment writes use the DAO transaction and unique request ID. Before committing, the target loan and outstanding balance are rechecked.
- The app reads the record back from Room before reporting success. Errors do not report success.

Supported examples:
- I spent ₹250 on lunch
- set food budget to 3000
- record repayment 500 from Ravi

Current limitations: the repayment syntax supports only one unambiguous “I lent” loan match. Editing/deleting records and broader finance commands are not exposed as agent actions. Model-generated proposals are advisory and always pass deterministic validation plus user confirmation.

## Phase 6 — Natural conversations

Implemented foundation:
- In-memory chat transcript while the Agent screen's ViewModel remains alive.
- Conversational phrasing for the supported command families, help/examples, validation feedback and action confirmation/cancellation.
- Corrections to a staged draft support changing amount, category, and description. The confirmation preview is inline so a user can correct the draft before saving.
- The transcript and pending action stay in the in-memory ViewModel and are not written to Room or uploaded; they are not durable across process death.
- Offline voice input has English (India) and Telugu locale choices. It calls Android's on-device recognizer only and never falls back to an online recognizer. It requires Android 12+ and an installed on-device recognizer; older phones (including Android 8.1) remain text-only.
- A persistent in-app floating assistant orb animates/pulses and moves while indicating thinking, approval or execution status. Tapping it navigates to the Agent screen.

Remaining limitations:
- The local model supports only schema-constrained expense/budget proposals; quality varies by model and wording. It is not allowed to execute actions, access records, or propose repayments.
- Telugu coverage is deterministic starter coverage, not broad language understanding; unrecognized Telugu commands ask for clarification.
- Voice recognition depends on Android's installed on-device language packs and must be validated on each target device. No offline recognizer is available on Android 8.1.

## Phase 7 — Verification and release readiness

Automated tests cover action parsing, model-proposal allowlisting, amount/category mapping, staged corrections, Telugu starter commands, required clarification and rejecting unsupported repayment intents. Existing repository tests cover exact paise conversion, expense validation, Khata validation, repayment idempotency/overpayment checks, and migration/data-transfer logic.

Release gates still required:
- GitHub Actions must pass for the commit containing this implementation.
- Run the UI and database flows on a physical Android device, including process restart and upgrade/migration checks.
- Test low available RAM/storage, cancellation, app background/foreground, interrupted writes, and verify no duplicate repayments.
- Verify model inference on the exact Tecno target device before claiming local-model compatibility.
- Verify the 2 GiB peak-RSS requirement on-device; a pre-load free-RAM threshold is not a guarantee against system-wide pressure.
- Do not declare Phase 5–7 complete until the remaining conversational features and physical-device acceptance tests pass.

## Privacy and safety boundary

The floating orb is an in-app Compose overlay; it does not request Android's system-wide overlay permission, draw over other apps, or use an AccessibilityService. Financial writes remain inside the app and require user confirmation. No cloud API or network action is used by the command parser.
