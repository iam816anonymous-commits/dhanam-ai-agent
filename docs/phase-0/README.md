# Phase 0 — Repository audit and specification

**Status:** Draft specification prepared from source inspection; implementation has not started.  
**Scope:** Documentation and decisions only.  
**Audited on:** 2026-10-29  
**Target repository:** `iam816anonymous-commits/dhanam-ai-agent`  
**Reference repositories:** `iam816anonymous-commits/dhanam-ai`, `iam816anonymous-commits/meeting-summarizer`

## 1. Executive decision

Build Dhanam AI Agent as a local-first Android finance application with the original Dhanam feature set and a similar dark emerald / warm-gold UI. The model is an optional interpretation/planning component, not the authority for money, permissions, database writes, or confirmation. A deterministic path must remain usable when no model is installed, inference fails, or the device is under memory pressure.

Phase 0 is complete only as a written specification. It is **not** evidence that the new app builds, that a model runs on the target phone, or that every legacy feature has already been reproduced.

## 2. Repository status and audit method

The target repository was empty when inspected (GitHub repository metadata reported size 0; no source tree existed). A minimal README was added to initialize the repository, then a dedicated `docs/phase-0-audit` branch was created for the specification. Consequently, findings about current implementation below refer to the legacy/reference repositories, not to code in this target repository.

The audit inspected representative source/configuration files in the legacy Dhanam repository: root README, app Gradle configuration, version catalog, manifest, Room database, `AgentFinancialBrain`, `ExpenseAgentEngine`, `AgentCapabilityRegistry`, `ConversationalFuzzyExtractor`, `MainViewModel`, and `AgentScreen`. The Meeting Summarizer README and its documented model-runtime/downloader design were reviewed as a model-management reference. This is a source-level audit, not a clean local build or full line-by-line audit of every source file.

## 3. Findings from the legacy Dhanam repository

### Reuse/adapt
- **Finance domain and behavior:** reuse as behavioral reference the expense/loan/budget concepts, category matching, historical analysis, repayment rules, conversational corrections, and bilingual messages. Do not copy legacy implementation blindly into the new architecture.
- **Capability inventory:** `AgentCapabilityRegistry` maps navigation, expense CRUD, CSV import/export, Khata operations, budget changes, clear-all, and insights to existing entry points. Use it as an inventory to verify against the complete UI and source tree; replace screen-coupled dispatch with typed domain tools.
- **Deterministic parser:** `ConversationalFuzzyExtractor` and `ExpenseAgentEngine` provide useful English/Telugu fallback behavior and regression-test examples. Keep deterministic parsing as fallback; isolate it from the future model planner.
- **UI direction:** preserve the existing dark emerald / warm-gold visual language and recognizable financial workflows, while rebuilding screens around a clear navigation/state model.
- **Voice/security/reporting:** preserve behavior only after dedicated audit and tests of permission handling, lifecycle, PIN/biometric lock, screenshot privacy, CSV/PDF sharing, notifications, and offline behavior.

### Must be redesigned or verified
- `AgentFinancialBrain` and `ExpenseAgentEngine` expose monetary values as `Double`; floating-point values must not be the canonical financial representation in the new domain layer.
- The legacy Room database `dhanam_expense_database` is version 1 and contains `Expense`, `Loan`, and `CategoryBudget`. A migration/upgrade path must be explicitly tested before any release that claims to preserve existing user data.
- The legacy database seeds category budgets on creation (Food ₹8,000; Dining ₹4,000; Transport ₹3,500; Shopping ₹5,000; Bills ₹4,500; Entertainment ₹2,500; Health ₹3,000; Education ₹5,000; Other ₹3,000). These are product defaults, not user-provided facts. New installation UX must clearly disclose any defaults; never invent a budget during analysis or silently overwrite a user's chosen limits.
- The legacy manifest shown in the audit has audio, biometric, notification, and vibration permissions but no `INTERNET` permission, while the README describes a Gemini/cloud client. Resolve this contradiction; the target product explicitly excludes cloud inference and financial uploads.
- The `nextgen/` rewrite is a useful reference for Compose/Room and paise storage, but its README explicitly says it is a foundation and not full feature parity. Its no-network manifest policy conflicts with the newly approved explicit model-download feature and must not be copied unchanged.
- Build/version catalogs differ across legacy and nextgen. Do not select dependency versions merely because they appear in one catalog; reconcile and prove a clean build in CI.
- README claims of successful historical builds are historical repository claims, not verification of this target repository.

## 4. Meeting Summarizer patterns to adapt

Adapt the pattern of an explicit model catalog, resource eligibility checks, verified download, activation, runtime abstraction, and deterministic fallback. Do not copy its meeting-specific UI or transcript domain. Model resource thresholds are heuristics, not proof of compatibility. The candidate Qwen3 0.6B artifact discussed in prior research is only an evaluation candidate: re-verify the exact official artifact, license, size, and SHA-256 before pinning it in code. Physical-device startup, memory, latency, heat, battery, and tool-selection quality remain release gates.

## 5. Architecture summary

```text
Compose UI
   -> Agent orchestrator / context builder
      -> deterministic parser OR optional local model planner
         -> strict structured-plan parser and domain validator
            -> policy + authorization + confirmation gate
               -> typed, registered finance tools
                  -> finance domain services / repositories
                     -> Room / SQLite (source of truth)
               -> read-back verification + audit event
   -> truthful result / clarification / recovery UI
```

The model may only propose a structured plan. It cannot run SQL, invoke arbitrary code, bypass policy, fabricate a successful result, or directly mutate the database. The app's finance services remain the only route to a write.

## 6. Binding product constraints

- Finance-only scope. No general-purpose assistant, arbitrary app launching, UI clicking/typing, AccessibilityService automation, or control of the rest of Android.
- On-device inference; no cloud fallback. No upload of financial data, prompts containing financial history, telemetry, or analytics to remote services.
- Explicit user-initiated HTTPS model downloads are allowed. No auto-download and no network requirement for core finance operations or inference after installation.
- Android's `INTERNET` permission is app-wide, not scoped to one class. Downloader-only architecture and tests reduce risk but cannot technically prove that no other code path can use the network. This residual risk must be documented and audited.
- Preserve feature parity and UI direction; a visible button is not feature parity unless it performs a real, tested action.
- Keep API 27 (Android 8.1) as the minimum target unless dependency/runtime verification finds a documented blocker.
- Default to English and Telugu. Other languages must not be advertised as supported until they pass defined tests.
- No app implementation is authorized by this Phase 0 documentation commit alone.

## 7. Phase 0 deliverables

- [Feature parity and source mapping](FEATURE_PARITY_MATRIX.md)
- [Architecture decisions and tool contract](ARCHITECTURE_DECISIONS.md)
- [Security, privacy, and threat model](SECURITY_PRIVACY.md)
- [Implementation plan and acceptance criteria](IMPLEMENTATION_PLAN.md)

## 8. Open decisions and release blockers

1. **App identity/data continuity:** planned default is to preserve application ID `com.aistudio.dhanam.expense` and database name `dhanam_expense_database` to enable a compatible upgrade path. This is not safe to ship until package-signing continuity, Room schema compatibility, and an upgrade test from a real legacy database are verified. If signing identity cannot be preserved, provide an explicit, user-controlled export/import path instead.
2. **Money migration:** define source-field-to-paise conversion, rounding, overflow checks, and migration fixture tests for every legacy expense, loan, repayment, and budget.
3. **PIN/security review:** inspect the complete legacy PIN migration, lockout, biometric enrollment, auto-lock, data clearing, export sharing, and screenshot-protection paths before deciding what to reuse.
4. **Voice privacy:** verify whether Android speech recognition sends audio off-device on each supported device. Voice must clearly report unavailable/offline behavior and retain text input as fallback; do not claim fully offline speech until proven.
5. **Model acceptance thresholds:** numerical quality/latency/memory thresholds must be set before evaluating model candidates and then measured on the actual target phone.
6. **Dependency baseline:** resolve AGP/Kotlin/KSP/Compose/Room/Lifecycle/WorkManager/runtime compatibility with a clean CI build before implementation is called stable.
7. **Network boundary:** approve the explicit downloader exception with the acknowledged app-wide INTERNET permission limitation.

## 9. Phase 0 exit checklist

- [x] Target repository inspected; found empty before initialization.
- [x] Legacy finance and UI architecture sampled and documented.
- [x] Meeting Summarizer model-management pattern reviewed as reference.
- [x] Product boundaries and security model written down.
- [x] Feature parity matrix and staged implementation gates documented.
- [ ] User reviews/accepts this specification.
- [ ] User explicitly authorizes Phase 1 implementation.

Until the final two items are complete, Phase 1 remains blocked.
