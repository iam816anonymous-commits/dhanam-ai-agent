# Dhanam future-phase research reference — 2026-11-01

## Purpose and provenance

This document preserves the engineering review and roadmap supplied during the Phase A CI repair. It is a planning reference for future Dhanam phases, not proof that the proposed work is implemented, independently revalidated, or physically tested. Before implementing a phase, re-check current official documentation, repository revisions, dependency licenses, Android API behavior, and device-specific constraints. Record exact evidence and test results in the feature research log.

The review's overall assessment: Dhanam has a useful finance foundation and promising agent/model architecture, but offline reliability, financial data security, command accuracy, device qualification, and production readiness remain unproven or incomplete.

## Current architecture and critical gaps identified

### Voice and speech privacy

The reviewed architecture is:
- Porcupine: local wake-word detection.
- Android `SpeechRecognizer`: speech-to-text.
- ML Kit: Telugu/Hindi translation.
- Finance agent: interprets and handles the resulting command.

Critical limitation: Android's offline preference is not a guarantee that speech stays on-device. `EXTRA_PREFER_OFFLINE` may be ignored by the installed provider. Android 12+ exposes a dedicated on-device recognizer, but provider availability is device-dependent. On older supported devices, use a genuine bundled offline speech engine for API 27+ where feasible; treat Android system recognition as a separately disclosed, explicit-consent fallback. Do not describe system recognition as offline unless the selected provider guarantees it.

Required lifecycle tests: repeated wake-word detections, microphone handoff/contention, recognition timeout, permission revocation, screen lock/unlock, service termination/recovery, false activations, and multi-hour battery use. Porcupine integration must release resources correctly. Android 14+ microphone foreground-service permissions and background-start restrictions need explicit coverage.

### Financial database security

Existing controls listed in the review: PBKDF2 PIN protection, biometric unlock, Android Keystore protection for the Picovoice key, and disabled Android backup. Critical gap: the Room database still uses standard SQLite; a PIN gate does not encrypt the database file.

Future work: audited database encryption, safe migration of existing user data, recovery behavior, encrypted backup/restore, and tests proving financial records survive interrupted or failed migrations. Never imply database encryption merely because app access is PIN/biometric protected.

### Financial command accuracy and safety

Measure actual extraction quality for:
- Amounts, decimal values, and date/relative-date expressions.
- Expense categories and category aliases.
- Corrections to pending transactions.
- Lending, repayments, and outstanding balances.
- Telugu, Hindi, and mixed-language utterances.

Create a representative benchmark of financial utterances. Report field-level accuracy, clarification rate, false-commit rate, and incorrect transaction proposals. An LLM must never write financial records directly: use validated structured proposals, deterministic domain rules, explicit confirmation where required, idempotent execution, and read-back evidence.

### Model runtime and resource intelligence

The reviewed six-model catalogue has file-size verification, SHA-256 checks, resumable downloads, and separate download/run eligibility. Configured process budgets were:

| Model | Configured process budget |
|---|---:|
| SmolLM2 360M | 1.5 GiB |
| Qwen3 0.6B | 2 GiB |
| Qwen3 1.7B | 3 GiB |
| Qwen2.5 1.5B | 3 GiB |
| Qwen3 4B | 6 GiB |
| Phi-4 Mini | 5 GiB |

These are configured limits, not measured guarantees. Profile actual peak RSS, native memory, KV cache, tokens/second, load latency, thermal throttling, and battery consumption. Detect memory pressure before inference and re-evaluate eligibility when available resources change. Use Qwen3 0.6B as a baseline qualification target, not as a claim of universal compatibility. Do not offer a model for download/run on a device unless its requirements and safety margins are qualified.

### Database migration and device acceptance

The reviewed Room schema was version 11 and inherits historical finance data. Build migration tests for every supported schema version and preserve expenses, Khata loans/repayments, budgets, income, and import identity. Test duplicate imports and interrupted migrations. No destructive fallback.

The current CI compiles the instrumented-test APK but does not execute instrumented tests on physical devices. A green CI run therefore does not prove device behavior. Maintain a device acceptance matrix:
- Android 8.1: speech compatibility and offline limitations.
- Android 12: dedicated on-device speech availability.
- Android 14+: microphone foreground-service behavior.
- 4 GB RAM: compact-model stability.
- 8–12 GB RAM: balanced-model performance.
- 16 GB+ RAM: advanced-model qualification.

Record exact device model, Android build, RAM conditions, model artifact/hash, test duration, and measured peak memory. The user's Tecno Camon i click (Android 8.1, 4 GB RAM) is an important compatibility target, but no pass should be claimed without an actual test.

## Future phase roadmap

### Phase A — Security and voice reliability
- Genuine offline speech architecture for supported devices.
- Explicit speech privacy controls and consent-gated network fallback.
- Database encryption design and migration/recovery tests.
- Hey Jarvis microphone, wake-word, timeout, permission, lifecycle, and battery tests.

### Phase B — Model runtime intelligence
- Dynamic resource profiling rather than static RAM assumptions.
- Per-model performance and stability benchmarks.
- Safe model switching and lifecycle ownership.
- OOM prevention, controlled unload/recovery, and telemetry that avoids sensitive content.

### Phase C — Financial agent accuracy
- Structured intent and slot extraction.
- Better clarification and correction flows.
- Multilingual utterance evaluation.
- Transaction validation, confirmation, idempotency, and read-back.

### Phase D — Autonomous specialist architecture
- Structured agent-to-agent messages rather than large generated text blocks.
- Explicit task ownership, shared evidence references, timeouts, cancellation, and execution history.
- Failure recovery and duplicate-inference prevention.
- Nightly learning evaluated against a fixed dataset; rejected suggestions should not recur without new evidence.
- Proposed rules must be evaluated and rolled back when they regress.
- Learning must never mutate financial records or weaken safety policies.

### Phase E — Advanced financial intelligence
Prefer deterministic calculations for numeric results; use the LLM to explain evidence, not invent values.
- Cash-flow forecasts from recorded income, recurring expenses, and spending history.
- Suggested category-budget allocations based on observed spending, requiring user approval.
- Subscription and renewal detection.
- Spending anomaly and duplicate-entry detection.
- Financial health reports: savings rate, debt exposure, spending distribution, and month-over-month change.

### Phase F — Production readiness
- Encrypted backup and restore.
- Full Room migration matrix.
- Security and privacy assessment.
- Physical-device qualification across supported API/RAM classes.
- Signed release and release verification.

## Architecture boundary

Keep Dhanam a finance-specialist agent. Build reusable autonomous-agent infrastructure separately and let Dhanam consume that infrastructure as a finance application. Do not turn Dhanam into a general-purpose mobile Jarvis by adding unrelated capabilities. Preserve the existing finance UI and workflows; avoid unnecessary rewrites.

## Research references supplied with the review

These are starting points, not substitutes for checking current versions during each future phase:
- Android RecognizerIntent: https://developer.android.com/reference/android/speech/RecognizerIntent
- Android SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Picovoice Porcupine Android quick start: https://picovoice.ai/quick-start/wake-word-android/
- Android foreground-service types and requirements: https://developer.android.com/develop/background-work/services/fgs/service-types
- Google ML Kit translation model management: https://developers.google.com/ml-kit/language/translation/android

## Implementation gate for every future phase

Before coding:
1. Inspect the current Dhanam branch and identify existing implementation; do not assume this review is still current.
2. Research official platform/API documentation and at least two credible implementation references where relevant.
3. Record exact repository URLs, immutable commit/tag, inspected paths, license/SPDX, and what was or was not copied.
4. Compare alternatives and document rejected approaches and trade-offs.
5. Define acceptance criteria, negative cases, privacy constraints, migration safety, and device/resource constraints.
6. Implement in a focused branch and run targeted tests before the full CI workflow.
7. Record exact CI run, commit SHA, artifacts, and test outcomes. Distinguish compilation from execution.
8. Record physical-device results separately; never infer device success from CI.
9. Update this document or the feature research log when evidence changes.

## Status discipline

The assessments and budgets above describe the review's findings at the time it was supplied. They are not guarantees and must not be copied into user-facing feature claims without fresh verification. Mark each roadmap item as `researched`, `implemented`, `CI-verified`, or `device-verified` separately.
