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

The repository changes are committed, but no local Android SDK/Gradle build was executed in the authoring environment. The GitHub Actions workflow is the next build verification gate. Do not mark Phase 1 accepted until CI is green and the app has been launched on an API 27-compatible environment and a current Android device/emulator.

## Known limitation

The current workflow uses the pinned Gradle 8.9 distribution via the Gradle setup action; the wrapper scripts are included for Windows and POSIX use. No production signing configuration or secrets are committed.
