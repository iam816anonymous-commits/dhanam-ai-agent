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
- Offline voice input has English (India) and Telugu locale choices. Android 12+ uses Android's dedicated on-device recognizer when available. Android 8–11 use the installed system speech service with `EXTRA_PREFER_OFFLINE`; Android does not guarantee that a legacy speech service honours this preference. The UI labels this honestly. For strict offline privacy on older phones, use text input unless the installed recognizer is known to run offline.
- The Agent screen now follows the original Dhanam palette and chat layout: teal/amber on slate, animated assistant core, quick-command chips, status pill, clear-chat control, auto-scrolling transcript, inline approval preview, and English/Telugu voice controls.

Remaining limitations:
- The local model supports only schema-constrained expense/budget proposals; quality varies by model and wording. It is not allowed to execute actions, access records, or propose repayments.
- Telugu coverage is deterministic starter coverage, not broad language understanding; unrecognized Telugu commands ask for clarification.
- Voice recognition depends on Android's installed on-device language packs and must be validated on each target device. No offline recognizer is available on Android 8.1.

## Phase 7 — Verification and release readiness

Automated unit tests cover action parsing, model-proposal allowlisting, amount/category mapping, staged corrections, Telugu starter commands, required clarification and rejecting unsupported repayment intents. CI runs unit tests, lint, debug APK assembly and Android instrumentation APK assembly. It does not run `connectedAndroidTest` or the physical-device model smoke test; those require an attached emulator/device and a manually installed, verified model. The optional model smoke test skips unless the exact verified artifact is already present; CI never downloads it. Existing repository tests cover exact paise conversion, expense validation, Khata validation, repayment idempotency/overpayment checks, and migration/data-transfer logic.

Release gates still required:
- GitHub Actions must pass for the commit containing this implementation. A green CI result verifies compilation and automated unit tests; it does not prove native inference stability on the Tecno phone.
- Run the UI and database flows on a physical Android device, including process restart and upgrade/migration checks.
- Test low available RAM/storage, cancellation, app background/foreground, interrupted writes, and verify no duplicate repayments.
- Verify model inference and offline speech on the exact Tecno target device before claiming device compatibility.
- Verify the 2 GiB peak-RSS requirement on-device; a pre-load free-RAM threshold is not a guarantee against system-wide pressure.
- Do not declare Phase 5–7 complete until the remaining conversational features and physical-device acceptance tests pass.

## Privacy and safety boundary

The floating orb is an in-app Compose overlay; it does not request Android's system-wide overlay permission, draw over other apps, or use an AccessibilityService. Financial writes remain inside the app and require user confirmation. No cloud API or network action is used by the command parser.
