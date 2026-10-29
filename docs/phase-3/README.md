# Phase 3 — Local model catalog and controlled downloads

## Research gate — 2026-10-29

### Runtime comparison
- LiteRT-LM is the preferred candidate for Android-native .litertlm models. Google's Android guide requires model files to live outside the APK/assets, initialization off the main thread, and single-thread ownership of the engine/conversation. Official guide: https://developer.android.com/agents/skills/device-ai/litert/litert-lm/skill
- llama.cpp remains an alternative for GGUF models, but introduces a different model format and native integration surface. It is not added in this slice because Dhanam's reviewed artifact is a LiteRT-LM bundle and adding both runtimes would expand the APK/testing surface before device qualification.
- LiteRT-LM 0.16.1 is pinned because it carries Kotlin metadata 2.3.0 and is compatible with Kotlin 2.2.x; LiteRT-LM 0.17.x–0.18.x require Kotlin 2.3 or newer. The app currently uses AGP 8.7.3 and Gradle 8.9, so this runtime version limits unrelated toolchain changes. Compatibility notes: https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md.

### Candidate artifact
- Repository: https://huggingface.co/litert-community/Qwen3-0.6B-int4
- Model license: Apache-2.0, as stated on the model card.
- Artifact: qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm, dynamic INT4 block-32 weights, no-thinking mode, 1,280-token KV-cache, 347,251,840 bytes (331.14 MiB).
- Immutable upstream file revision: 6aa2daf (artifact commit); download URL pins that revision rather than mutable main.
- Expected SHA-256: 2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139.
- Artifact evidence: https://huggingface.co/litert-community/Qwen3-0.6B-int4/blob/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm.
- Upstream reports about 1,032–1,036 MB peak RSS on a Samsung Galaxy S26 using CPU/XNNPACK. This is a different device and does not establish safe operation on Dhanam's 4 GB Tecno phone.
## Implemented
- One reviewed catalog item with a revision-pinned HTTPS URL, exact byte count, SHA-256, license and context metadata.
- Runtime resource checks for total RAM, currently available RAM and app-private free storage. Download is disabled until all thresholds pass.
- Explicit foreground download action; no automatic downloads, model bundling, cloud inference, or background model fetching.
- Download is written to app-private storage, has progress/cancel/retry behavior, bounds bytes, rejects size/hash mismatches, deletes partial files on failure/cancel, checks HTTPS after redirects, and atomically finalizes verified files.
- A model may be marked preferred only after re-verifying the full file. Preferred selection is separate from runtime state.
- Added LiteRT-LM CPU runtime with a single-thread executor, background initialization, verified-path recheck, local streaming chat, stop/reset/unload actions and resource cleanup. After cancellation, the conversation is closed and recreated rather than reused, because LiteRT-LM documents conversation reuse after CancelProcess as unsupported. Model loading is cancelled when its ViewModel is cleared. Model loading requires an explicit preferred-model choice and a fresh runtime resource gate.
- Runtime smoke-test prompts are capped at 4,000 characters and are not written to the database or sent to a network service. The local test chat is not connected to finance records or write tools.
- Model files can be removed from app-private storage.

## Acceptance status
- CI run https://github.com/iam816anonymous-commits/dhanam-ai-agent/actions/runs/40876779039 — success: unit tests, lint, debug APK assembly, instrumented-test APK assembly, APK existence checks, and artifact upload.
- Catalog pinning, file-integrity and prompt-validation unit tests passed in the successful CI run above; the device-only inference smoke test compiled into the Android test APK. It was not run on a physical device.
- Download cancellation/retry, corrupted response and low-storage behavior: physical-device verification pending.
- Physical-device benchmark and inference smoke test: pending. Runtime code is implemented and enabled after model verification/resource checks, but successful generation on the target phone is not claimed until installed and tested there.
- The resource gate is intentionally conservative. If it rejects the device, the app offers no download action; do not bypass the gate just to test the model.

## Security and privacy
- INTERNET permission is added solely for explicit model downloads.
- No accounts, financial records, prompts, or transactions are uploaded.
- Only the fixed catalog URL is downloaded; users cannot supply arbitrary model URLs.
- No external/shared-storage permission is requested.


## Runtime API compatibility note
- Official Kotlin API guide: https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- LiteRT-LM 0.17.0/0.17.1/0.18.0/0.18.1 carry Kotlin metadata 2.4.0 and require Kotlin 2.3 or newer; 0.16.1 carries metadata 2.3.0 and builds with Kotlin 2.2 or newer. This repository uses AGP 8.7.3 and Gradle 8.9, so it uses LiteRT-LM 0.16.1, Kotlin 2.2.21, and KSP 2.2.21-2.0.5 to limit unrelated build-stack changes.
- Backend: CPU only for initial qualification. GPU requires optional native-library declarations and device qualification; NPU is not enabled.
- Runtime state is in-memory only. The test chat does not query Room, read expense/Khata records, execute tools, or write prompt history.


## Running the on-device inference smoke test
1. Install the updated debug APK on the target phone. The app removes the superseded 475 MiB model artifact from its private model directory.
2. Open **Settings → Local AI model**, tap **Download model**, wait for size/SHA-256 verification, then tap **Set preferred**.
3. Close other apps. Wait for the runtime resource check to pass and tap **Load verified model**. Loading is refused if available RAM is below 1,536 MiB or process peak RSS cannot be measured/has reached 2 GiB.
4. Send a short prompt in the local inference chat and confirm a response appears. If process peak RSS reaches 2 GiB, generation is stopped and the model is unloaded.
5. For the instrumented test, install the `app-debug-androidTest.apk` from the same CI artifact and run:
   `adb shell am instrument -w -e class com.aistudio.dhanam.expense.model.LocalInferenceDeviceSmokeTest com.aistudio.dhanam.expense.test/androidx.test.runner.AndroidJUnitRunner`
   The test intentionally skips if the reviewed model is absent/invalid or the device does not meet the runtime resource gate. It never downloads the model itself.

The CI pipeline compiles the instrumented-test APK but does not run it on a physical device. Successful target-phone inference remains unverified until the above steps complete.

## Expanded model tiers — 2026-10-29

The catalog now contains three fixed, revision-pinned LiteRT-LM artifacts. The runtime was upgraded to LiteRT-LM 0.18.0 / Kotlin 2.3.0 / AGP 8.13.2 / Gradle 8.13 to support the larger Qwen3 bundles; model compatibility must still be verified on physical devices.

| Model | Artifact size | Context | Download gate | Peak RSS guard |
|---|---:|---:|---|---:|
| Qwen3 0.6B INT4 No-Think | 331.1 MiB | 1,280 tokens | 3.5 GiB total RAM, 1.5 GiB available RAM, 1.25 GiB free storage | 2 GiB |
| Qwen3 1.7B INT4 | 931.8 MiB | 4,096 tokens | 8 GiB total RAM, 4 GiB available RAM, 3 GiB free storage | 3.5 GiB |
| Qwen3 4B Mixed INT4 | 2.5 GiB | 2,048 tokens | 12 GiB total RAM, 7 GiB available RAM, 7 GiB free storage | 6 GiB |

Artifact sources and licenses:
- Qwen3 0.6B INT4 No-Think: https://huggingface.co/litert-community/Qwen3-0.6B-int4 (Apache-2.0; pinned revision 6aa2daf).
- Qwen3 1.7B INT4: https://huggingface.co/litert-community/Qwen3-1.7B (Apache-2.0; pinned revision d9b8a91; SHA-256 2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b).
- Qwen3 4B Mixed INT4: https://huggingface.co/litert-community/Qwen3-4B (Apache-2.0; pinned revision 2f7415b4e3a149c16b929659d146a6eaf6d99a34; SHA-256 569fe029f51e55b6377f98ebd7f6d26431bc73e1d632034f5389f2fd720da701).

These gates are deliberately conservative product safeguards, not a promise that every phone meeting the thresholds will run without thermal throttling or memory pressure. The app displays the models but disables download when current device checks fail. Dynamic RAM is not treated as physical RAM. Only one model is loaded at a time, and switching catalog entries is disabled while inference is loaded or running.

The 1.7B and 4B models are newly catalogued and have not yet been qualified on the user's target phone. CI can validate code, metadata, lint, and APK packaging; it cannot establish physical-device inference stability. The 4B CPU runtime is especially memory-intensive, so its 12 GiB physical-RAM / 7 GiB currently-available-RAM / 6 GiB process-peak gate must not be relaxed without measured device tests.
