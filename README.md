# Dhanam AI Agent

A finance-only, local-first Android personal-finance application. The original [Dhanam AI application](https://github.com/iam816anonymous-commits/dhanam-ai) is the behavioural and visual reference; this repository is the separate agent-enabled application.

## Current implementation status

The existing five-destination Compose UI (Agent, Expenses, Khata, Insights, Settings) and dark emerald / warm-gold design are retained. The finance foundation is extended with a coordinated three-agent system and nightly learning workflow.

The UI also restores the original app's dark/light preference (dark by default) and screenshot privacy control. Security includes a PBKDF2 PIN and optional strong-biometric unlock with PIN fallback. Voice capture supports persisted English, Telugu and Hindi selection; recognized text stays in the composer for review before it is sent. An optional Hey Jarvis wake-word service provides hands-free speech-to-text and voice navigation without loading the finance LLM.

Implemented in the current codebase:
- Local expense, income, budget, lending/borrowing and repayment workflows with validated financial writes.
- Integer-paise calculations and deterministic ledger-backed insights.
- Monthly/category spending movement, income-versus-expense cash-flow summaries, budget-overrun checks, recurring-expense candidates and Khata outstanding analysis. The finance agent can answer read-only spending-reduction and lending-summary questions directly from the local ledger.
- One process-wide LiteRT-LM model owner shared by the UI and all three specialist agents.
- Six opt-in LiteRT-LM model artifacts across three tiers: Compact (SmolLM2 360M, Qwen3 0.6B), Balanced (Qwen3 1.7B, Qwen2.5 1.5B), and Advanced (Qwen3 4B, Phi-4 Mini Reasoning). Every entry pins an artifact revision, exact byte length and SHA-256.
- A serialized inference gate so the model is never used concurrently by separate agents or interactive chat.
- Finance Operations, Financial Intelligence and Skill Improvement specialist roles.
- Nightly WorkManager scheduling between 01:00 and 06:00 local time, subject to Android battery/idle policies.
- All three agents and the Settings model test console are restricted to model inference during 01:00–06:00 local time; deterministic daytime financial commands and local analysis remain available.
- Versioned exact-description category skill proposals, leave-one-out validation, explicit user approval and rollback to the previous validated version.
- Month-scoped helpful/not-useful recommendation feedback, reused as aggregate context in the next nightly intelligence review.
- Local daily agent reports, role-isolated bounded memories, auditable inter-agent handoffs, a confirmed memory-clear control and skill review in Settings.
- Income records are stored separately from expenses, included in monthly cash-flow summaries and nightly agent analysis, can be created through the finance agent, and support previewed, idempotent CSV import/export.

This is an implementation milestone, not a claim of release completion. Physical-device qualification, remaining finance-parity items and extended migration/security tests are still required.

## Model resource checks

The model manager shows every model in its tier, with independent download and run-now eligibility. Downloads require sufficient Android-reported physical RAM and free app storage; loading additionally requires enough currently available system RAM. RAM expansion/swap is not counted as physical RAM. The app reports Android's detected total RAM, current available RAM and free app storage. These are conservative estimates, not a substitute for device-specific peak-RSS and thermal testing. Phi-4 Mini Reasoning uses a fresh one-turn conversation after each answer because its publisher documents a LiteRT-LM 0.18.x multi-turn cache issue.

## Agent boundaries

| Agent | Responsibility |
|---|---|
| Finance Operations | Expenses, income/finance records where supported, budgets, Khata, repayments and validated actions |
| Financial Intelligence | Spending patterns, category changes, recurring candidates, budget overruns and savings recommendations |
| Skill Improvement | Learn and test finance-specific local rules; suggest improvements without executing arbitrary code |

All agents share the same local model runtime. The nightly worker invokes them sequentially; each task is restricted to the finance domain. The LLM is advisory: trusted Kotlin/Room code owns monetary arithmetic, validation and database writes.

## Nightly learning

1. Enable **Nightly agent preparation** in Settings.
2. Select and download a compatible model using the existing model manager. The worker only uses the user's verified preferred model and repeats device-resource checks before loading it.
3. WorkManager schedules eligible work for 01:00–06:00 with battery-not-low and device-idle constraints.
4. Deterministic analysis and skill validation run even if the model is unavailable. If the model is ready, all three agents use the same engine sequentially.
5. The daily report records completed work, model status, tested skill proposals and next improvements.

Android background execution is best-effort. Doze, OEM battery management, force-stop, low battery or insufficient time can delay or skip a run. The app does not promise an exact five-hour execution window.

## Hey Jarvis voice assistant

- Enable **Hey Jarvis · hands-free assistant** in Settings. A Picovoice AccessKey is required once to generate the custom English “Hey Jarvis” keyword model; the key is encrypted with Android Keystore and never committed to the repository.
- Keyword-model generation requires an Internet connection. Wake-word detection itself runs on-device. The finance LLM is not loaded for wake-word detection or speech recognition.
- The user explicitly enables/disables the microphone foreground service. While enabled, Android shows a persistent notification and the service can continue while the screen is locked. Start it from the visible app; Android 14+ restricts starting microphone foreground services from the background.
- After the wake phrase, wait for the listening beep and speak the command. Android SpeechRecognizer converts the utterance to text using the selected English, Telugu or Hindi language. Android 8.1 accepts an offline preference but cannot guarantee offline speech recognition; an installed provider and language pack are required.
- Commands such as “open expenses”, “open budget”, “show insights” and “open Khata” navigate to the corresponding screen. Finance commands are routed to the shared AgentViewModel and retain the existing validation, preview and explicit confirmation before writes.
- If Dhanam is not active, Android background-activity restrictions mean a recognized command is surfaced as a notification; tap it to review the transcript in Dhanam. Force-stop, OEM battery management and microphone permission revocation can stop background listening.
- Speech is not saved as an audio recording. The service only uses the live microphone stream for wake-word detection and speech recognition.

## Privacy and safety

- The finance ledger, deterministic calculations, and LiteRT-LM inference are local to the device. Dhanam does not send finance prompts or ledger records to Picovoice.
- **Speech-recognition privacy depends on the Android speech provider.** Dhanam requests offline recognition, but Android documents that recognizer implementations may ignore this preference. On Android 8.1 there is no public on-device-only `SpeechRecognizer` factory; the default provider may use remote servers. Do not assume voice commands are offline/private unless the selected provider and language pack have been verified. See the [Android offline preference](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE) and [SpeechRecognizer documentation](https://developer.android.com/reference/android/speech/SpeechRecognizer).
- Android 12+ can expose a dedicated on-device recognizer when the device provides one. Dhanam selects it when available; otherwise it falls back to the system provider. This fallback is a compatibility path, not an offline guarantee.
- The Internet permission is also used for explicit model downloads, ML Kit language/translation model downloads, and user-initiated Picovoice wake-word model creation/AccessKey validation.
- No cloud LLM inference or automatic financial transfers. This does **not** mean the Android speech provider cannot process audio remotely.
- The Picovoice AccessKey is encrypted with Android Keystore. Wake-word detection runs on-device after the custom keyword model has been generated.
- No generic device-control or AccessibilityService automation.
- Model output cannot directly write financial records or alter permissions. Financial mutations continue through deterministic validation, preview and explicit confirmation.
- Learned skills are data-only exact-description category rules, never executable code. They require successful validation and user approval before use.
- No neural-network weight fine-tuning is performed. Capability improvement is through validated local rules, persistent reports and advisory model suggestions.

## Platform and build

- Kotlin, Jetpack Compose, Material 3, Room/SQLite, WorkManager and LiteRT-LM.
- Minimum Android API 27 (Android 8.1).
- JDK 17 and Android SDK platform/build tools for API 35+.

From the repository root:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Windows:

```bat
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`; the unsigned release APK is generated at `app/build/outputs/apk/release/app-release-unsigned.apk`. GitHub Actions verifies and uploads both APKs plus the instrumented-test APK. Release signing remains a separate release-readiness task.

## Engineering assessment and improvement priorities

This is a code-level review of the current repository, not a claim that the following work has already been implemented or verified on a handset.

### P0 — Resolve before calling the app privacy-ready

1. **Make speech privacy explicit and enforceable.** The current Android speech fallback requests `EXTRA_PREFER_OFFLINE`, but Android says a recognizer implementation may ignore it. Add an explicit user-visible online-recognition consent path and, for a genuine offline guarantee on API 27+, evaluate a bundled/on-device STT engine with model download, storage, RAM and language support checks. Do not label the current fallback as guaranteed offline.
2. **Protect financial data at rest.** The Room database is opened with the standard Android SQLite driver; the PIN gate and PBKDF2 hash protect app access but do not encrypt the database file themselves. Evaluate SQLCipher/Room encryption or another well-reviewed encrypted-storage design, including key lifecycle, migration, export and recovery. `allowBackup=false` prevents ordinary Android backup, but a secure user-controlled encrypted backup/restore flow is still needed.
3. **Qualify financial command safety with real examples.** Build a reviewed test corpus for English, Telugu and Hindi voice transcripts, including ambiguous amounts, dates, categories, corrections, duplicate commands and Khata repayments. Require clarification for uncertain fields and preserve preview/confirmation for every write.

### P1 — Reliability and device qualification

4. **Test the voice lifecycle on real devices.** Validate API 27 / Android 8.1 and current Android versions for permission denial/revocation, notification denial, screen lock, service restart, repeated wake phrases, recognizer errors, no-speech timeouts, language availability, battery drain and thermal impact. Add a Porcupine error callback and observable recovery states for asynchronous microphone/engine failures. Android 14+ microphone foreground services must be started while the app is visible and satisfy the microphone permission/type requirements; see [Android microphone foreground-service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone).
5. **Add migration and ledger integration tests.** Room currently has additive migrations through schema version 11. Test upgrades from every supported historical schema using representative real legacy databases, verify row counts and monetary totals before/after, and test interruption/retry/duplicate import behaviour. Keep migration tests separate from unit tests.
6. **Benchmark each model on target hardware.** Catalogue RAM/storage thresholds are admission heuristics, not measured guarantees. Collect per-device load time, peak RSS, available RAM, token latency, OOM rate, thermal throttling and battery use for all six artifacts. Include low-memory 4 GB devices and devices advertising RAM expansion; never treat swap/virtual RAM as equivalent to physical RAM. Keep download eligibility separate from run eligibility.
7. **Expand automated voice and service coverage.** CI currently runs JVM unit tests, lint, and compiles the debug, release and instrumented-test APKs. It does not execute instrumented tests on a physical device or emulator. Add deterministic tests around service state transitions and command dispatch, then maintain a small physical-device release matrix for microphone and wake-word behaviour.

### P2 — Product quality and maintainability

8. **Measure agent usefulness, not just successful inference.** Track structured parse accuracy, clarification rate, false expense creation, correction success and recommendation usefulness against a fixed evaluation set. Deterministic Kotlin/Room calculations remain authoritative; LLM text must never become the source of financial totals.
9. **Make nightly learning observable and bounded.** Report each specialist's start/end time, skipped reason, timeout, model status and validated proposal count. Test reboot, Doze, force-stop, battery constraints and time-zone changes. WorkManager is best-effort, not a guaranteed 01:00–06:00 scheduler.
10. **Complete operational safeguards.** Add encrypted backup/restore, export verification, retention controls for agent reports/memories, a user-facing data deletion/export checklist, dependency/license review and a signed release pipeline. Remove duplicate dependencies and keep dependency versions centrally managed.

### Current verification boundary

The Android CI workflow runs `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` and `assembleRelease`, then checks and uploads the APK artifacts. A successful CI run proves compilation, JVM tests, lint and packaging; it does not prove wake-word accuracy, offline speech recognition, model performance, battery behaviour, or financial correctness on a real handset. Release signing is not configured in this workflow.

## Remaining acceptance work

- Physical-device smoke tests on Android 8.1, including expense/Khata workflows, migrations, offline mode and nightly scheduling.
- Measure model load peak RAM, thermal behaviour, battery impact and actual inference duration on the target handset.
- Verify WorkManager execution across reboot, Doze, force-stop and battery restrictions.
- Complete remaining feature-parity, security, backup/restore and release-signing work.
- Expand persistent per-agent memory from latest verified summaries into evaluated trend memory, and add broader recommendation feedback and rollback workflows.
