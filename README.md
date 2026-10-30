# Dhanam AI Agent

A finance-only, local-first Android personal-finance application. The original [Dhanam AI application](https://github.com/iam816anonymous-commits/dhanam-ai) is the behavioural and visual reference; this repository is the separate agent-enabled application.

## Current implementation status

The existing five-destination Compose UI (Agent, Expenses, Khata, Insights, Settings) and dark emerald / warm-gold design are retained. The finance foundation is extended with a coordinated three-agent system and nightly learning workflow.

Implemented in the current codebase:
- Local expense, budget, lending/borrowing and repayment workflows with validated financial writes.
- Integer-paise calculations and deterministic ledger-backed insights.
- Monthly/category spending movement, budget-overrun checks, recurring-expense candidates and Khata outstanding analysis.
- One process-wide LiteRT-LM model owner shared by the UI and all three specialist agents.
- A serialized inference gate so the model is never used concurrently by separate agents or interactive chat.
- Role-isolated deterministic agent memories, structured specialist handoff history with 90-day retention, and a user-controlled clear-memory action.
- User-controlled disable/restore for approved learned skills.
- Finance Operations, Financial Intelligence and Skill Improvement specialist roles.
- Nightly WorkManager scheduling between 01:00 and 06:00 local time, subject to Android battery/idle policies.
- Versioned exact-description category skill proposals, leave-one-out validation and explicit user approval.
- Local daily agent reports and skill review in Settings.

This is an implementation milestone, not a claim of release completion. Physical-device qualification, remaining finance-parity items and extended migration/security tests are still required.

## Agent boundaries

| Agent | Responsibility |
|---|---|
| Finance Operations | Expenses, budgets, Khata lending/borrowing, repayments and validated actions |
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

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions verifies the APK and uploads it as a workflow artifact.

## Remaining acceptance work

- Physical-device smoke tests on Android 8.1, including expense/Khata workflows, migrations, offline mode and nightly scheduling.
- Measure model load peak RAM, thermal behaviour, battery impact and actual inference duration on the target handset.
- Verify WorkManager execution across reboot, Doze, force-stop and battery restrictions.
- Complete remaining feature-parity, security, backup/restore and release-signing work.
- Add recommendation feedback, richer multi-month financial evaluation and regression metrics for long-term agent improvement.
