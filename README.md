# Dhanam AI Agent

A local-first Android financial assistant. The original [Dhanam AI repository](https://github.com/iam816anonymous-commits/dhanam-ai) remains the behavioral and visual reference.

## Project status

**Current phase: Phase 1 — Android foundation and navigation.**

- [Phase 0 audit and repository findings](docs/phase-0/README.md)
- [Architecture decisions](docs/phase-0/ARCHITECTURE_DECISIONS.md)
- [Feature parity and source mapping](docs/phase-0/FEATURE_PARITY_MATRIX.md)
- [Security, privacy and threat model](docs/phase-0/SECURITY_PRIVACY.md)
- [Implementation phases and acceptance criteria](docs/phase-0/IMPLEMENTATION_PLAN.md)
- [Phase 1 implementation notes](docs/phase-1/README.md)

## Current foundation

- Native Kotlin + Jetpack Compose + Material 3.
- Five working in-app destinations: Agent, Expenses, Khata, Insights and Settings.
- Dark emerald / warm-gold visual system inspired by the original app.
- Android API 27 minimum; Gradle wrapper and CI build pipeline.
- No sample financial data, no fabricated balances and no fake agent success responses.
- No network, microphone or other sensitive permissions in Phase 1.

## Product constraints

- Finance-only scope; no general-purpose Android control or AccessibilityService automation.
- No cloud inference or uploading financial records.
- Model downloads will be explicit, optional and verified; model inference must work offline.
- A model may propose a tool call, but trusted finance code validates, authorizes, executes and verifies financial actions.
- Expense storage, full finance parity, agent execution and model runtime are not claimed complete yet; they belong to later phases.

## Build

Requires JDK 17 and Android SDK platform/build tools for API 35.

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows:

```bat
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs the same checks and uploads the APK as a workflow artifact.
