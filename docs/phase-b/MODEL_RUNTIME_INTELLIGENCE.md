# Phase B — Model Runtime Intelligence

Branch: phase-b/model-runtime-intelligence  
Status: in progress; implementation and CI verification pending.

## Scope
- Dynamic resource profiling: distinguish Android physical RAM, available RAM, low-memory state/threshold, current process RSS, lifetime high-water RSS, and native heap allocation.
- Admission control: require each model's configured available-RAM requirement plus Android's low-memory threshold, with a 256 MiB minimum reserve; block when Android reports low memory.
- Preserve the single process-wide LiteRT-LM engine owner and serialized inference.
- Collect non-sensitive load/runtime measurements. Never record prompts, transcripts, financial data, or generated content.
- Benchmark model load duration, current/high-water RSS, native heap, inference latency and throughput on physical devices. Do not claim measured performance from static catalogue values.
- Keep the six existing model options until artifact metadata, licences, runtime compatibility, and physical-device benchmarks justify changes. Qwen3 0.6B remains a qualification baseline, not a universal recommendation.

## Audit findings
- Existing admission used ActivityManager.MemoryInfo.availMem but ignored lowMemory and threshold.
- Existing process monitoring read VmRSS only. maxProcessPeakBytes was compared to a current RSS sample, not a measured lifetime peak.
- Existing engine ownership is serialized on one dedicated executor with one conversation/KV cache at a time. Preserve this invariant.
- Existing OOM paths unload the engine, but there was no complete per-load diagnostic snapshot.
- Existing six-model catalogue pins artifact URLs, byte lengths and SHA-256 digests. Keep artifact identity stable during the first safety increment.

## Research evidence
- Android ActivityManager.MemoryInfo: availMem is an estimate including reclaimable memory; threshold is the low-memory threshold; lowMemory indicates system pressure. https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
- Android ComponentCallbacks2: documents memory-pressure callbacks and recommends releasing rebuildable allocations at appropriate trim levels. https://developer.android.com/reference/android/content/ComponentCallbacks2
- Google LiteRT-LM Android guide: engine initialization and message generation stay off the main thread; close conversation before engine, exactly once; cancel streaming before closing; do not close an engine whose initialization threw. https://developers.google.com/edge/litert-lm/android
- These official sources support the runtime design, not model-specific RAM guarantees. Those require reproducible device measurements.

## Acceptance criteria
- Download eligibility remains distinct from run-now eligibility.
- Runtime admission fails closed on low-memory state, insufficient total RAM, insufficient available RAM after reserve, or insufficient storage.
- Procfs parsing reports current RSS separately from optional high-water/anonymous/file-backed/shared RSS; missing current RSS is not zero.
- Unit tests cover low-memory flags, reserve arithmetic, missing procfs values, and independent storage/physical-memory checks.
- Telemetry contains only numeric resource/timing measurements and model identifier.
- All inference remains serialized through the existing owner and single-thread executor.
- CI runs JVM tests, lint, debug/release assembly and APK verification. Device stability remains unverified until actual hardware tests.

## Device qualification matrix
| Device class | Required evidence |
|---|---|
| Android 8.1 / 4 GB | Compact admission, load stability, repeated inference, unload/reload, OOM recovery |
| Android 12 | Compact baseline and memory-pressure recovery |
| Android 14+ | Background/foreground transitions and runtime lifecycle |
| 8–12 GB | Balanced-tier load/inference benchmarks |
| 16 GB+ | Advanced-tier benchmark before broader eligibility claims |

For every run record device/build, physical RAM, available RAM, model id and artifact SHA-256, load time, current and high-water RSS, native heap, inference duration, thermal state, battery impact, and outcome. CI compilation is not a device test.
