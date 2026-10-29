# Security, privacy and threat model

## 1. Data classification

**Highly sensitive local data:** expense descriptions, amounts, categories, transaction dates, loan/borrower names, repayment history, budgets, spending insights, chat prompts that contain financial details, PIN-related material, exported CSV/PDF files.

**Operational metadata:** model artifact version/hash, download progress, non-sensitive error codes, tool state and timestamps. Even operational metadata should remain local unless a later separately approved policy says otherwise.

## 2. Trust boundaries

1. **Untrusted user/model text:** prompts, speech transcripts, model completions and generated tool arguments.
2. **Trusted policy layer:** strict parser, typed schemas, domain validation, authorization, confirmation and execution limits.
3. **Trusted finance domain:** repositories and services that enforce amount/date/category/loan rules.
4. **Persistent store:** Room/SQLite and user preferences.
5. **Network boundary:** explicit model-artifact downloader only. It is not an inference API and must not receive financial context.
6. **Export boundary:** user-selected content shared through scoped Android content URIs; the recipient app is outside Dhanam's trust boundary.

## 3. Threat register

| ID | Threat / severity | Failure mode | Required controls and evidence |
|---|---|---|---|
| T01 | Model invents a transaction or field — Critical | Hallucinated amount/category/date/person becomes money data | Strict schema, missing-field clarification, domain validation, deterministic read-back, negative tests |
| T02 | Unauthorized/destructive write — Critical | Delete, repayment or clear-all runs without consent | Trusted risk tier, exact-target confirmation token, expiry, UI preview, confirmation cannot be model-controlled |
| T03 | Duplicate/partial write — Critical | Retry records an expense or repayment twice | Idempotency keys, DB transactions, persisted execution state, failure injection and retry tests |
| T04 | Financial data exfiltration — Critical | Prompt/history/log uploaded to model host, analytics or crash service | No cloud inference, no analytics SDK/endpoints, network inspection tests, no financial payload in downloader requests |
| T05 | Downloader supply-chain compromise — High | Redirect or replaced artifact installs untrusted model | HTTPS, allowed-host/redirect policy, pinned version/size/hash/license, atomic activation, corrupt-file tests |
| T06 | Resource exhaustion/device crash — High | Model download or inference consumes storage/RAM and kills app | Shared eligibility gate, free-space margin, peak-memory measurements, cancellable download/inference, deterministic fallback |
| T07 | Invalid but syntactically valid plan — High | Valid JSON names wrong person, record or amount | Semantic validation, actual-record resolution, no fuzzy target selection for writes, exact confirmation |
| T08 | Migration/data loss — High | Existing database is dropped or monetary conversion changes values | Export backup fixture, tested Room migration, paise conversion rules, no destructive fallback migration |
| T09 | Voice privacy misunderstanding — High | System speech service sends audio remotely despite app's local-first claim | Explicit voice disclosure, text fallback, device-specific tests; do not claim offline speech without evidence |
| T10 | Log/export leakage — High | Sensitive prompts/IDs or broad files appear in logs/share intents | Redacted bounded local events, scoped FileProvider/SAF URIs, no secrets in logs, export tests |
| T11 | Stale confirmation / target drift — High | A prior “yes” confirms a changed record or later request | Confirmation bound to action fingerprint/target IDs/values/expiry; stale token rejection |
| T12 | Unregistered tool or code injection — Critical | Model requests arbitrary function/SQL/shell | Closed tool registry, no dynamic reflection dispatch, no code execution, unknown tool rejection |
| T13 | Unavailable model/host — Medium | Download or runtime failure blocks finance tasks | Model optional; no cloud fallback; deterministic parser and manual UI remain available |
| T14 | Reminder delay due to OEM power controls — Medium | Repayment/budget notification arrives late | WorkManager, deduplication, user-visible scheduling state, realistic timing disclaimer and device tests |
| T15 | Toolchain/dependency incompatibility — Medium-high | Build instability or API 27 regression | Pinned catalog, dependency verification, clean CI build, min-API test, dependency/license review |

## 4. Network policy

- The user must explicitly tap a download action for a specific model.
- Download only the pinned artifact over HTTPS from the catalog's approved host(s); redirects must be validated and must not silently change trust domain.
- The downloader receives artifact metadata only. Never append prompt, chat history, expense/loan records, device identifiers beyond what the platform inherently exposes, or app database content.
- No cloud inference, financial synchronization, telemetry upload, analytics, automatic model downloads, or remote crash reporting that captures prompts.
- Core finance CRUD, reports, calculations, and installed-model inference must function in Airplane mode.
- Android's `INTERNET` permission is app-wide. This cannot technically confine network access to the downloader. Mitigate with code separation, no general HTTP client in domain/agent packages, static manifest/dependency checks, network inspection and code review. Record this as a residual risk, not a guarantee.
- Revisit the manifest and every transitive dependency before release. The legacy README's Gemini description conflicts with the inspected manifest's lack of INTERNET permission; do not inherit cloud behavior accidentally.

## 5. Confirmation and transaction protocol

For every write:
1. Parse model/deterministic output into a versioned typed request.
2. Reject unknown fields/tools and validate all values.
3. Resolve record targets from the actual local database; never accept invented IDs.
4. Recompute risk/confirmation requirements from trusted metadata.
5. If required, display the exact operation, amount, category/person, and target record to the user.
6. On confirmation, verify the confirmation token still matches the exact action and has not expired.
7. Execute via a domain service and a Room transaction where appropriate.
8. Read the result back and compare with expected postconditions.
9. Persist a bounded local outcome event and report success only after verification.

For imports, deletes and clear-all, preview the affected count and require explicit approval. Where atomic rollback cannot be guaranteed, disclose partial-failure behavior and design a recovery path before release.

## 6. Model artifact validation

Catalog entries must pin:
- Stable model identifier and exact revision/version.
- Official download URL and approved host.
- License and redistribution/usage obligations.
- Expected size in bytes and SHA-256.
- Supported runtime/API/device profile and language capability claims.
- Measured peak RAM, startup latency, generation latency and quality results on named test devices.

Download into a temporary location, enforce maximum expected size and available-storage margin, verify byte count and hash, then atomically activate. On cancel/failure, remove partial files without damaging the active model. Revalidate eligibility before inference and fall back safely if runtime initialization fails. Do not treat catalog thresholds as proof that a model works on a specific device.

## 7. Privacy/security release tests

- Inspect merged manifest and dependency graph for network-capable SDKs/analytics.
- Instrument HTTP calls and verify only approved model artifact requests occur; no request includes finance records or prompt text.
- Run expense, budget, loan, insight, import/export and installed-model flows in Airplane mode.
- Inject malformed JSON, unknown tools, extra fields, fabricated IDs, negative/overflow values, ambiguous dates and model timeouts.
- Verify delete/repayment/clear-all cannot execute without a matching fresh confirmation.
- Kill process at each write state; confirm no duplicate or partial finance result.
- Corrupt/truncate model download, redirect to disallowed host, simulate low storage and cancel mid-download.
- Test data migration with real-schema fixtures and compare row counts/amounts/IDs.
- Review logcat, crash output and exported files for sensitive information.
- Verify PIN/biometric/auto-lock/screenshot settings after a full source audit.
