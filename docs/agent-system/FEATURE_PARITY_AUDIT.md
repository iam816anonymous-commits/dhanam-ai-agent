# Dhanam AI Agent — legacy functionality and agent architecture parity

Audit date: 2026-10-30

This audit compares the current finance-only Dhanam AI Agent with the original Dhanam AI app, the user's earlier general Android agent repository, and the Meeting Summarizer model workflow. It separates deliberate product boundaries from missing legacy functionality.

## 1. Original Dhanam AI application

Repository: `iam816anonymous-commits/dhanam-ai`

| Capability in original Dhanam | Current Dhanam AI Agent | Parity assessment |
|---|---|---|
| Emerald/gold Compose UI and five main destinations | Retained five destinations and theme | Present; visual details still need side-by-side device review |
| Expense create/edit/delete, search and date filtering | Present with local Room records | Present; old screen has richer filter/sort interactions to compare |
| Budget management and spending insights | Present; deterministic calculations and monthly/category views | Present, with new ledger-backed analysis |
| Lending/borrowing and repayment ledger | Present; person-level balances and repayment validation | Present; original contact/call actions are not yet restored in the new Khata UI |
| PDF reports and CSV import/export | PDF export and finance CSV import/export are present | Core export/import present; verify old report options and historical migration on device |
| Voice agent with English/Telugu preference | New Agent screen is currently text-entry only | Missing parity; restore offline-preferred voice input and language selection |
| Floating Jarvis overlay | Not present | Missing legacy UX; evaluate whether it belongs in a finance-only product |
| Biometric unlock | New app has a PIN lock gate but no equivalent biometric prompt | Missing parity; add biometric unlock as an optional second factor, not a replacement for PIN recovery |
| Repayment/budget notifications | New finance reminder section and WorkManager scheduling exist | Partial parity; verify exact schedules, notification channels and OEM behaviour |
| Theme switching | Current app retains the emerald/gold visual system | Light/dark preference parity needs explicit audit |
| Security and privacy settings | PBKDF2-backed PIN, local-only ledger, explicit file destinations and backup restrictions | Newer security foundation in some areas; biometric and screenshot privacy controls still need parity review |

## 2. Earlier general Android agent

Repository: `iam816anonymous-commits/agent-created-by-gemini`

That project is a general Android automation agent, not a finance agent. Its architecture includes:
- Deterministic fast-path routing before model inference.
- Optional local sub-1B model through a native Llama bridge with grammar-constrained intent output.
- Explicit cloud fallback and honest degraded-offline states.
- Structured task approval for sensitive actions.
- Accessibility-based device interaction, emergency stop, and an authenticated local remote bridge.
- Explicit separation of local cached data from live web research.

**What should carry into Dhanam:** deterministic-first intent routing, typed action plans, grammar/structured output where useful, explicit confirmation, honest offline/model-unavailable status, memory limits and an emergency cancellation path.

**What should not be copied into Dhanam:** AccessibilityService device control, arbitrary web research, remote teleoperation, generic phone automation or unrestricted cloud fallback. Dhanam is intentionally a finance-only app; its agents must not control the phone or write financial data directly from model output.

## 3. Meeting Summarizer model-management workflow

Repository: `iam816anonymous-commits/meeting-summarizer`

The Meeting Summarizer research and Phase 10 work established the right reliability standard for model options:
- Catalog concrete deployable artifacts, not just model family names.
- Pin immutable repository revisions and record exact byte lengths and SHA-256 digests.
- Gate downloads on physical RAM and storage; gate inference separately on currently available RAM and model/runtime compatibility.
- Show byte-level progress, download stage, cancellation, retry and actionable failure details.
- Verify every downloaded file before activation.
- Keep model downloads opt-in and disclose that CI cannot prove real-device inference.

The Dhanam model manager now follows this approach for six LiteRT-LM artifacts. Physical-device model load, peak-RAM and inference acceptance remain open.

## 4. Current model options

| Tier | Model options | Download size | Runtime note |
|---|---|---:|---|
| Compact | SmolLM2 360M Instruct; Qwen3 0.6B INT4 No-Think | 374 MB; 347 MB | Intended for lower-memory phones; Qwen3 has a 1,280-token context |
| Balanced | Qwen3 1.7B INT4; Qwen2.5 1.5B INT8 | 977 MB; 1.60 GB | Higher memory reserve and larger context |
| Advanced | Qwen3 4B Mixed INT4; Phi-4 Mini Reasoning INT4 | 2.66 GB; 2.78 GB | Requires high physical and currently free RAM; Phi is reset to a fresh conversation after each answer because its publisher documents a LiteRT-LM 0.18.x multi-turn cache issue |

Each tier has two distinct model choices. All six entries use pinned HTTPS artifacts, exact byte lengths and SHA-256 verification. Download compatibility and run-now compatibility are displayed independently.

## 5. Resource eligibility semantics

- **Download eligibility:** Android-reported physical total RAM and free app storage. Temporary RAM pressure must not hide a model that can be downloaded for later.
- **Run-now eligibility:** physical total RAM, current available system RAM and free app storage.
- **After loading:** the process high-water mark is monitored against that model's own process budget. Reaching or failing to measure the budget stops inference and unloads the model.
- Android RAM expansion/swap is not treated as physical RAM. The model manager displays the values returned by Android so a reported 17 GB can be distinguished from physical total RAM and current available RAM.

## 6. Remaining parity work, ordered

1. Restore voice input to the Agent screen with an offline-preferred recognizer, explicit English/Telugu selection, permission handling and graceful Android 8.1 fallback.
2. Add optional biometric unlock alongside the existing PIN gate, preserving PIN recovery.
3. Restore useful Khata contact actions (call and share/reminder) with safe intent handling and user confirmation.
4. Audit and restore the original theme preference and any missing expense filters/sorting without changing the current emerald/gold visual identity.
5. Run old-versus-new workflows against copied test data: expense CRUD, CSV import/export, Khata repayment, reminders, PDF report, PIN lock and app restart.
6. Qualify every downloadable model on actual devices: download progress, checksum, engine initialization, a real prompt, peak RSS, thermal behaviour, battery and unload/reload. CI is not a substitute for this.

## Acceptance rule

Do not mark a legacy capability restored because a button or screen exists. The action must be wired, persist correctly, handle cancellation/errors, have a regression test where practical, and pass a real-device check when it depends on Android services or model runtime behaviour.
