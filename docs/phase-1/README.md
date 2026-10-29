# Phase 1 — Android foundation

## Implemented in this phase batch

- Native Android project using Kotlin, Jetpack Compose and Material 3.
- Package/application ID: `com.aistudio.dhanam.expense`.
- Minimum SDK 27, compile/target SDK 35, JDK 17.
- Pinned build plugins: Android Gradle Plugin 8.7.3, Kotlin 2.0.21 and Compose compiler plugin 2.0.21.
- Gradle 8.9 wrapper configuration.
- Dark emerald / warm-gold theme and five registered bottom-navigation destinations: Agent, Expenses, Khata, Insights, Settings.
- Reusable empty-state component; screens explicitly state when finance storage or agent behavior is not yet connected.
- Basic unit tests for route uniqueness and destination labels.
- GitHub Actions workflow to run unit tests, lint, assemble the debug APK, verify artifact existence and upload the APK.
- Manifest does not request INTERNET, microphone, or notification permission in this foundation.

## Deliberately not implemented

Room persistence, expense/loan/budget CRUD, finance calculations, agent planner, tool execution, model runtime/download, voice, notifications, exports, PIN/biometrics and data migration are later-phase work. No sample transaction or fabricated balance is seeded.

## Verification status

- GitHub Actions run [40854252033](https://github.com/iam816anonymous-commits/dhanam-ai-agent/actions/runs/40854252033) completed successfully on 2026-10-29.
- The job passed unit tests, Android lint, debug APK assembly, APK existence verification, and debug APK artifact upload.
- This is CI build evidence only. It does not prove app installation, navigation, API 27 behavior, model performance, offline inference, or physical-device stability. Those checks remain pending.
- No local Android SDK/Gradle build was executed in the authoring environment.

## Research requirement

Before implementing each feature in the parity matrix, follow [the open-source research protocol](../research/OPEN_SOURCE_RESEARCH_PROTOCOL.md) and record source commits, licenses, implementation findings, decisions, tests, and limitations in [the feature research log](../research/FEATURE_RESEARCH_LOG.md). The repositories listed there are candidates, not pre-approved dependencies.

## Known limitation

The current workflow uses the pinned Gradle 8.9 distribution via the Gradle setup action; the wrapper scripts are included for Windows and POSIX use. No production signing configuration or secrets are committed.
