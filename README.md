# Dhanam AI Agent

A local-first Android financial assistant. The original Dhanam AI repository remains the reference for existing finance behavior and UI.

## Project status

**Current phase: Phase 0 — repository audit and implementation specification.** No application implementation is authorized until Phase 0 is reviewed and explicitly accepted.

- [Phase 0 audit and repository findings](docs/phase-0/README.md)
- [Architecture decisions](docs/phase-0/ARCHITECTURE_DECISIONS.md)
- [Feature parity and source mapping](docs/phase-0/FEATURE_PARITY_MATRIX.md)
- [Security, privacy and threat model](docs/phase-0/SECURITY_PRIVACY.md)
- [Implementation phases and acceptance criteria](docs/phase-0/IMPLEMENTATION_PLAN.md)

## Product constraints

- Finance-only scope; no general-purpose Android control or AccessibilityService-based automation.
- On-device inference and finance data remain local. No cloud inference or uploading financial records.
- Model downloads are explicit, verified, and optional; inference must work offline after installation.
- A model may propose a tool call, but trusted app code validates, authorizes, executes and verifies every financial action.
- Preserve the original dark emerald / warm gold visual direction and full existing finance feature parity.
- Android API 27 (Android 8.1) is the target minimum unless dependency validation proves a blocker.

Phase 0 is documentation only. The plan must record unresolved questions and release blockers rather than imply they have already been solved or tested.
