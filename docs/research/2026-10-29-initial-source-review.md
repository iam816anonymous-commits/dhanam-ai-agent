# Initial open-source source review — 2026-10-29

This is the first evidence record after the research gate was added. It is deliberately limited to files actually inspected in this pass. It does not mark any Dhanam feature implemented or any dependency approved.

## 1. Finance UI and persistence candidates

### Kaasu — `vanterx/kaasu`

- Repository: https://github.com/vanterx/kaasu
- Inspected branch commit: [`63c31fa97e491e93fedaaa112614ad7c4fc7a269`](https://github.com/vanterx/kaasu/commit/63c31fa97e491e93fedaaa112614ad7c4fc7a269)
- Files inspected:
  - [README.md](https://github.com/vanterx/kaasu/blob/63c31fa97e491e93fedaaa112614ad7c4fc7a269/README.md)
  - [app/build.gradle.kts](https://github.com/vanterx/kaasu/blob/63c31fa97e491e93fedaaa112614ad7c4fc7a269/app/build.gradle.kts)
  - [ExpenseRepositoryImpl.kt](https://github.com/vanterx/kaasu/blob/63c31fa97e491e93fedaaa112614ad7c4fc7a269/core/data/src/main/java/com/example/expense/core/data/repository/ExpenseRepositoryImpl.kt)
- Relevant patterns worth investigating further: Room-backed repository boundary, Kotlin Flow observation, date-range query API, app container wiring, Android Storage Access Framework export, and UI architecture.
- **Critical limitation found in inspected code:** repository methods expose and persist amounts using Kotlin `Double` (`Flow<Double?>`, `amount: Double`). This is incompatible with Dhanam's approved canonical integer-paise money invariant. Do not copy its money model or financial calculations. The date range code uses `ZoneId.systemDefault()`; Dhanam must define and test timezone/month-boundary semantics rather than copy blindly.
- **License gate:** GitHub repository metadata reported no detected license, and a root `LICENSE` path lookup returned 404 in this pass. No code may be copied until the actual license is located and compatibility is confirmed.
- Decision: reference only for architecture/UI ideas pending deeper review; not approved as a code source or money implementation.

### Expense-Tracker — `airmon1101/Expense-Tracker`

- Repository: https://github.com/airmon1101/Expense-Tracker
- Inspected branch commit metadata: [`5c3e4d19d2b7e093d22e6a420b40799b2f8985b0`](https://github.com/airmon1101/Expense-Tracker/commit/5c3e4d19d2b7e093d22e6a420b40799b2f8985b0)
- Repository metadata identifies the license as MIT and describes an offline-first Kotlin/Compose/Room application.
- Source files, schema, tests, and money representation were not yet reviewed in this pass.
- Decision: candidate for the next finance-domain source audit; not yet an implementation reference.

### Expenso — `darkvortex144/Expenso`

- Repository: https://github.com/darkvortex144/Expenso
- Inspected branch commit metadata: [`0b495c9758c45c5807f7467a3b43ebf705fe5890`](https://github.com/darkvortex144/Expenso/commit/0b495c9758c45c5807f7467a3b43ebf705fe5890)
- Repository metadata identifies Apache-2.0. Its README advertises offline transaction/account/category/budget features.
- Source files, tests, and financial calculations were not yet reviewed in this pass.
- Decision: secondary candidate for comparison, not yet an implementation reference.

## 2. On-device LLM runtime candidates

### LiteRT-LM — `google-ai-edge/LiteRT-LM`

- Repository: https://github.com/google-ai-edge/LiteRT-LM
- Inspected branch commit: [`2c1c9a67cbd3a12e20fdf1d750855e6afd5649c4`](https://github.com/google-ai-edge/LiteRT-LM/commit/2c1c9a67cbd3a12e20fdf1d750855e6afd5649c4)
- Files inspected: [README.md](https://github.com/google-ai-edge/LiteRT-LM/blob/2c1c9a67cbd3a12e20fdf1d750855e6afd5649c4/README.md) and [official LiteRT-LM Android/overview documentation](https://developers.google.com/edge/litert-lm/overview).
- Repository metadata identifies Apache-2.0. The project states that Kotlin is a stable API and advertises tool use and hardware acceleration. This makes it a serious evaluation candidate for the native Android app.
- Still unproven for Dhanam: exact supported model artifact and license, compatibility with minSdk 27 and target ABI, artifact size, runtime/dependency footprint, function-call reliability for finance schemas, available memory, startup latency, sustained thermal behavior, and performance on the actual target phone.
- Decision: evaluate before integration; do not add a dependency or show model downloads until the target-device gate passes.

### llama.cpp — `ggml-org/llama.cpp`

- Repository: https://github.com/ggml-org/llama.cpp
- Inspected branch commit: [`7df8b876b91208a86a6d792e1140b3deec14535b`](https://github.com/ggml-org/llama.cpp/commit/7df8b876b91208a86a6d792e1140b3deec14535b)
- Files inspected: [docs/android.md](https://github.com/ggml-org/llama.cpp/blob/7df8b876b91208a86a6d792e1140b3deec14535b/docs/android.md).
- Repository metadata identifies MIT. The Android example uses a native inference engine, GGUF metadata parsing, app-private model files, and Kotlin Flow token output. The docs explicitly caution that context size affects memory and can cause process termination if excessive.
- Trade-off: a native C/C++ integration may provide broad GGUF support, but introduces NDK/CMake/ABI and native-library maintenance costs. It must be compared against LiteRT-LM using the same small model and target device.
- Decision: keep as a benchmark alternative; no runtime selected yet.

## 3. Current decisions and next research work

1. Do not reuse the Kaasu money model: it uses `Double`, contrary to Dhanam's paise invariant.
2. Do not copy code from any repository until license and exact source revision are verified. A repository's metadata or README is not enough.
3. Compare at least two finance implementations at source level for entities/DAOs/migrations, money arithmetic, CRUD validation, search/filter, report totals, and tests before implementing F01–F09.
4. Compare LiteRT-LM and llama.cpp with the same model/task/device criteria before selecting a runtime.
5. The next feature implementation batch must attach a completed research record and acceptance tests; this note is not a substitute for those feature-specific reviews.

## Sources checked

- [Kaasu README](https://github.com/vanterx/kaasu/blob/63c31fa97e491e93fedaaa112614ad7c4fc7a269/README.md)
- [Kaasu ExpenseRepositoryImpl](https://github.com/vanterx/kaasu/blob/63c31fa97e491e93fedaaa112614ad7c4fc7a269/core/data/src/main/java/com/example/expense/core/data/repository/ExpenseRepositoryImpl.kt)
- [Kaasu app Gradle file](https://github.com/vanterx/kaasu/blob/63c31fa97e491e93fedaaa112614ad7c4fc7a269/app/build.gradle.kts)
- [LiteRT-LM README](https://github.com/google-ai-edge/LiteRT-LM/blob/2c1c9a67cbd3a12e20fdf1d750855e6afd5649c4/README.md)
- [LiteRT-LM official documentation](https://developers.google.com/edge/litert-lm/overview)
- [llama.cpp Android guide](https://github.com/ggml-org/llama.cpp/blob/7df8b876b91208a86a6d792e1140b3deec14535b/docs/android.md)
