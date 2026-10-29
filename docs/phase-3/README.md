# Phase 3 — Local model catalog and controlled downloads

## Research gate — 2026-10-29

### Runtime comparison
- LiteRT-LM is the preferred candidate for Android-native .litertlm models. Google's Android guide requires model files to live outside the APK/assets, initialization off the main thread, and single-thread ownership of the engine/conversation. Official guide: https://developer.android.com/agents/skills/device-ai/litert/litert-lm/skill
- llama.cpp remains an alternative for GGUF models, but introduces a different model format and native integration surface. It is not added in this slice because Dhanam's reviewed artifact is a LiteRT-LM bundle and adding both runtimes would expand the APK/testing surface before device qualification.
- No runtime dependency is added yet. The exact target device has not been benchmarked and LiteRT-LM's current API/dependency must be compiled and smoke-tested against this project's Kotlin/AGP stack before inference is enabled.

### Candidate artifact
- Repository: https://huggingface.co/litert-community/Qwen3-0.6B
- Model license: Apache-2.0, as stated on the model card.
- Artifact: qwen3_0_6b_mixed_int4.litertlm, mixed INT4 weights, 2,048-token context, 497,516,544 bytes.
- Immutable upstream revision: 154c5de0fc0fc5002a7da570ba8c67edffa32ca2.
- Expected SHA-256: 7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1.
- Artifact evidence: https://huggingface.co/litert-community/Qwen3-0.6B/commit/154c5de0fc0fc5002a7da570ba8c67edffa32ca2 and https://huggingface.co/litert-community/Qwen3-0.6B/blob/refs%2Fpr%2F6/qwen3_0_6b_mixed_int4.litertlm
- The upstream card includes measurements from a TECNO LJ9, not Dhanam's exact target phone. Those numbers are indicative only and do not prove compatibility or safe runtime memory on the target device.

## Implemented
- One reviewed catalog item with a revision-pinned HTTPS URL, exact byte count, SHA-256, license and context metadata.
- Runtime resource checks for total RAM, currently available RAM and app-private free storage. Download is disabled until all thresholds pass.
- Explicit foreground download action; no automatic downloads, model bundling, cloud inference, or background model fetching.
- Download is written to app-private storage, has progress/cancel/retry behavior, bounds bytes, rejects size/hash mismatches, deletes partial files on failure/cancel, checks HTTPS after redirects, and atomically finalizes verified files.
- A model may be marked preferred only after re-verifying the full file. The preference is metadata only; it does not claim the model is loaded or running.
- Model files can be removed from app-private storage.

## Acceptance status
- CI and APK artifact verification: pending for this commit.
- Catalog pinning and file-integrity unit tests added; CI pending.
- Download cancellation/retry, corrupted response and low-storage behavior: physical-device verification pending.
- Physical-device benchmark and inference smoke test: pending. Inference remains disabled until a compatible LiteRT-LM version is proven against this app's toolchain and the target phone.
- The resource gate is intentionally conservative. If it rejects the device, the app offers no download action; do not bypass the gate just to test the model.

## Security and privacy
- INTERNET permission is added solely for explicit model downloads.
- No accounts, financial records, prompts, or transactions are uploaded.
- Only the fixed catalog URL is downloaded; users cannot supply arbitrary model URLs.
- No external/shared-storage permission is requested.
