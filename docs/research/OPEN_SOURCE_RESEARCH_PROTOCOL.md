# Open-source research and implementation protocol

**Effective date:** 2026-10-29  
**Applies to:** every Dhanam AI Agent feature, F01–F30, including revisions and dependencies.

## Mandatory rule

Do not implement a feature from intuition, a README alone, or a single copied snippet. Before implementation, research the current open-source implementations and relevant official platform documentation. Record the decision and evidence in this repository. The goal is to adopt proven design patterns while preserving Dhanam's finance-integrity and local-first requirements—not to clone another app wholesale.

## Per-feature research workflow

1. **Define the feature and acceptance criteria.** Link the feature ID(s) in `docs/phase-0/FEATURE_PARITY_MATRIX.md`. State user-visible behavior, data invariants, failure modes, and what evidence will count as done.
2. **Find candidates.** Inspect relevant maintained open-source repositories and official Android/library documentation. For critical paths, compare at least two credible implementations when available. Search current issues, releases, tests, migration notes, and known limitations.
3. **Inspect source, not only descriptions.** Read implementation files, tests, dependency declarations, schema/migrations, permissions, and CI. Pin the exact commit/tag and source paths used as evidence.
4. **Check licensing and provenance.** Record the SPDX/license identifier and relevant obligations before copying code. Prefer adapting architecture/patterns over copying implementation. Do not copy code from a repository with unclear/incompatible licensing.
5. **Compare options.** Record the candidate approaches, evidence, compatibility with minSdk 27, maintenance and dependency cost, device/storage/RAM impact, offline behavior, privacy/security implications, and why alternatives were rejected.
6. **Make a Dhanam-specific decision.** Document what is adopted, changed, or deliberately not adopted. Update an ADR if the decision changes the approved architecture, security boundary, data model, or dependency strategy.
7. **Implement the complete vertical slice.** Connect UI → validated domain operation → repository/storage or runtime → observable result and error/recovery state. Do not leave dead buttons, fake success, or production mock data.
8. **Test the failure cases.** Add unit/domain tests, persistence/integration tests, and UI/instrumentation tests appropriate to the feature. Include invalid input, empty state, cancellation, retry, process death, low-resource behavior, and privacy boundaries where applicable.
9. **Verify and record.** Run the available checks and report their actual results. CI success is not physical-device validation. Record device-only checks separately.
10. **Revisit stale research.** Re-check the selected repository's current state, releases, APIs, and license before introducing or upgrading a dependency. Do not treat this initial source list as permanently current.

## Required feature research record

For each feature or tightly coupled feature batch, record:

- Feature IDs and user behavior.
- Research date.
- Repository/project name and canonical URL.
- Exact tag/commit SHA and inspected file paths.
- License/SPDX identifier and copying decision.
- Official platform/library documentation consulted.
- Comparison of alternatives and evidence for the chosen design.
- Dhanam implementation paths and ADRs changed.
- Tests added, commands/results, CI run URL, and any physical-device results.
- Known limitations, unresolved questions, and follow-up actions.

Use `docs/research/FEATURE_RESEARCH_LOG.md` as the index and add focused notes under `docs/research/` for substantive feature batches. No feature is complete merely because a reference implementation has it.

## Initial research candidates (not pre-approved dependencies)

| Area | Candidate | What to inspect | Important caveat |
|---|---|---|---|
| Offline finance UI and persistence | https://github.com/vanterx/kaasu | Room entities/DAOs, repositories, migrations, money types, CSV export, tests, license | Verify the current default branch, license, and exact implementation before reuse |
| Finance CRUD, search and reports | https://github.com/airmon1101/Expense-Tracker | domain/repository boundaries, filters, chart math, validation, import/export, tests | Project maturity and implementation quality must be assessed from source |
| Alternative finance behavior | https://github.com/darkvortex144/Expenso | account/category/budget/transaction flows, reports, schema, license, tests | Feature claims in README are not acceptance evidence |
| Native on-device LLM runtime | https://github.com/google-ai-edge/LiteRT-LM | Kotlin API, supported artifacts, tool use, model metadata, memory/runtime constraints, release notes | Candidate only; prove compatibility and target-device behavior before adding dependency |
| Alternative native inference | https://github.com/ggml-org/llama.cpp | Android example, GGUF metadata, JNI/native build, context/memory settings, license | Compare APK/native size, ABI support, performance, model compatibility and maintenance cost |
| Android app architecture | https://developer.android.com/topic/architecture | lifecycle, repositories, UI state, offline-first guidance | Prefer official platform guidance where it conflicts with sample-app shortcuts |
| Database persistence | https://developer.android.com/training/data-storage/room | schema, migrations, transactions, testing | Migration must preserve legacy records and exact money |
| Background work | https://developer.android.com/topic/libraries/architecture/workmanager | scheduling, constraints, retries, unique work | Do not promise exact reminder timing across OEM battery policies |
| Secure local authentication | https://developer.android.com/identity/sign-in/biometric-auth | biometric flows and fallback | Keep financial data and authorization independent of model output |

This list is a starting point for research, not a claim that all sources have already passed the review.

## Research tracks mapped to the feature matrix

- **Finance domain and persistence:** F01–F09, F20, F27. Compare schema design, exact money representation, transactions, queries, migrations, and deterministic calculation tests.
- **Imports, exports and background work:** F10–F12. Compare SAF/URI handling, CSV edge cases, report generation, WorkManager constraints, retries, and duplicate prevention.
- **Agent UX and policy:** F13–F15, F23–F25, F29. Research typed tool registries, structured output validation, state machines, exact-target confirmations, idempotency, read-back verification, and privacy-safe audit events.
- **Voice and language:** F16–F17. Inspect offline capability claims, Android recognizer/TTS behavior, permission/lifecycle handling, and language-specific evaluation; do not claim offline speech without device evidence.
- **Security and preferences:** F18–F20, F29. Compare Android Keystore/biometric patterns, lock lifecycle, screenshot privacy, reset behavior, and persisted settings.
- **Model catalog, downloads and runtime:** F21–F23, F26, F28. Compare runtime options and model formats; verify source/license/hash/size, redirects, storage and RAM gates, cancellation, atomic activation, offline inference, fallback and thermal behavior.
- **Navigation, accessibility and release:** F14, F28, F30. Inspect Compose navigation/state, semantic labels, API 27 compatibility, clean-checkout CI, APK metadata and dependency licensing.

Every feature must still have its own research record; grouping above only helps organize related work.

## Non-negotiable Dhanam constraints

- Local finance records are the source of truth. Model output is untrusted input.
- No cloud inference, financial-data uploads, analytics, or remote crash reporting that captures prompts.
- Model downloads are explicit, pinned, verified, cancellable, and optional.
- No model-selected SQL, arbitrary code execution, or system-wide Android automation.
- Store canonical money exactly (integer paise in `Long`, with checked conversions).
- No successful-write message before persisted read-back verification.
- No fabricated balances, budgets, transactions, people, or historical records.
- Preserve existing data or stop safely; never silently recreate/drop the database.
- No physical-device or offline guarantee may be claimed from a CI build alone.
