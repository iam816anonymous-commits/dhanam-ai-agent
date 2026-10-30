# Dhanam AI Agent

A finance-only, local-first Android personal-finance application. The original [Dhanam AI application](https://github.com/iam816anonymous-commits/dhanam-ai) is the behavioural and visual reference; this repository is the separate agent-enabled application.

## Current implementation status

The existing five-destination Compose UI (Agent, Expenses, Khata, Insights, Settings) and dark emerald / warm-gold design are retained. The finance foundation is extended with a coordinated three-agent system and nightly learning workflow.

The UI also restores the original app's dark/light preference (dark by default) and screenshot privacy control. Security includes a PBKDF2 PIN and optional strong-biometric unlock with PIN fallback. Voice capture supports persisted English, Telugu and Hindi selection; recognized text stays in the composer for review before it is sent.

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

## Privacy and safety

- Financial records and analysis remain local; the Internet permission is used for explicit model downloads only.
- No cloud inference or automatic financial transfers.
- No generic device-control or AccessibilityService automation.
- Model output cannot directly write financial records or alter permissions.
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

## Remaining acceptance work

- Physical-device smoke tests on Android 8.1, including expense/Khata workflows, migrations, offline mode and nightly scheduling.
- Measure model load peak RAM, thermal behaviour, battery impact and actual inference duration on the target handset.
- Verify WorkManager execution across reboot, Doze, force-stop and battery restrictions.
- Complete remaining feature-parity, security, backup/restore and release-signing work.
- Expand persistent per-agent memory from latest verified summaries into evaluated trend memory, and add broader recommendation feedback and rollback workflows.
