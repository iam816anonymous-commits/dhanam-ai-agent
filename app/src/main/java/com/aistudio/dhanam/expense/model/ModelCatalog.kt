package com.aistudio.dhanam.expense.model

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

enum class ModelTier(val title: String, val description: String) {
    COMPACT("Compact · low memory", "Fast downloads and lower memory use for entry-level phones."),
    BALANCED("Balanced · everyday", "Stronger general-purpose models for capable mid-range and flagship phones."),
    ADVANCED("Advanced · high capacity", "Larger models; offered only when Android reports enough physical and currently free memory.")
}

data class ModelSpec(
    val id: String,
    val displayName: String,
    val tier: ModelTier,
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val sha256: String,
    val license: String,
    val contextTokens: Int,
    val minTotalRamBytes: Long,
    val minAvailableRamBytes: Long,
    val minFreeStorageBytes: Long,
    val runtimeRequirement: String,
    val sourceUrl: String,
    val maxProcessPeakBytes: Long
)

data class DeviceEligibility(
    val allowed: Boolean,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val reasons: List<String>
)

object ModelCatalog {
    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB

    // These are selectable LiteRT-LM bundles, not arbitrary checkpoint downloads.
    // Each category intentionally contains two distinct model families.
    val models = listOf(
        ModelSpec(
            id = "smollm2-360m-instruct",
            displayName = "SmolLM2 360M · Instruct",
            tier = ModelTier.COMPACT,
            fileName = "SmolLM2_360M_instruct.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/resolve/507c99cfe6541ba2bcd84818786f7b025935e5e1/SmolLM2_360M_instruct.litertlm",
            expectedBytes = 373_719_040L,
            sha256 = "8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e",
            license = "Apache-2.0",
            contextTokens = 2048,
            minTotalRamBytes = 3L * GIB,
            minAvailableRamBytes = 768L * MIB,
            minFreeStorageBytes = 1024L * MIB,
            runtimeRequirement = "CPU; compact 360M instruction model. Conservative app-process peak budget: 1.5 GiB.",
            sourceUrl = "https://huggingface.co/litert-community/SmolLM2-360M-Instruct",
            maxProcessPeakBytes = 1536L * MIB
        ),
        ModelSpec(
            id = "qwen3-0.6b-int4-nothink-1280",
            displayName = "Qwen3 0.6B · INT4 No-Think",
            tier = ModelTier.COMPACT,
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/6aa2daf8aba4aa456797fb8040b36a3948bcfda7/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            expectedBytes = 347_251_840L,
            sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
            license = "Apache-2.0",
            contextTokens = 1280,
            minTotalRamBytes = 3500L * MIB,
            minAvailableRamBytes = 1536L * MIB,
            minFreeStorageBytes = 1280L * MIB,
            runtimeRequirement = "CPU; 1,280-token KV cache; process peak RSS must remain below 2 GiB.",
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-0.6B",
            maxProcessPeakBytes = 2L * GIB
        ),
        ModelSpec(
            id = "qwen3-1.7b-int4-4096",
            displayName = "Qwen3 1.7B · INT4",
            tier = ModelTier.BALANCED,
            fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/87603f0ff3fe74b9b77ed0477edb773b4b596f32/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            expectedBytes = 977_184_032L,
            sha256 = "2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8",
            license = "Apache-2.0",
            contextTokens = 4096,
            minTotalRamBytes = 8L * GIB,
            minAvailableRamBytes = 4L * GIB,
            minFreeStorageBytes = 3L * GIB,
            runtimeRequirement = "CPU; 4,096-token context. Community CPU benchmark reports about 2.5 GiB peak RSS; device-specific testing is still required.",
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-1.7B",
            maxProcessPeakBytes = 3L * GIB
        ),
        ModelSpec(
            id = "qwen25-1.5b-instruct-q8-4096",
            displayName = "Qwen2.5 1.5B · INT8",
            tier = ModelTier.BALANCED,
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/4526a9ff32b96063c7b07f0d681af7dc6c8ec342/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            expectedBytes = 1_598_603_264L,
            sha256 = "2ac49902133f729199bbd23ad6714df32b20585c786f2546d2293cc371f35278",
            license = "Apache-2.0",
            contextTokens = 4096,
            minTotalRamBytes = 8L * GIB,
            minAvailableRamBytes = 4L * GIB,
            minFreeStorageBytes = 4L * GIB,
            runtimeRequirement = "CPU INT8; 4,096-token context. Published Android CPU benchmark reports about 2.2 GiB peak RSS.",
            sourceUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
            maxProcessPeakBytes = 3L * GIB
        ),
        ModelSpec(
            id = "qwen3-4b-mixed-int4-2048",
            displayName = "Qwen3 4B · Mixed INT4",
            tier = ModelTier.ADVANCED,
            fileName = "qwen3_4b_mixed_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-4B/resolve/2f7415b4e3a149c16b929659d146a6eaf6d99a34/qwen3_4b_mixed_int4.litertlm",
            expectedBytes = 2_659_053_760L,
            sha256 = "569fe029f51e55b6377f98ebd7f6d26431bc73e1d632034f5389f2fd720da701",
            license = "Apache-2.0",
            contextTokens = 2048,
            minTotalRamBytes = 16L * GIB,
            minAvailableRamBytes = 8L * GIB,
            minFreeStorageBytes = 7L * GIB,
            runtimeRequirement = "CPU only in this app; 2,048-token context; high-memory tier. Published Android results are GPU/device-specific and do not guarantee CPU performance.",
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-4B",
            maxProcessPeakBytes = 6L * GIB
        ),
        ModelSpec(
            id = "phi4-mini-reasoning-int4-4096",
            displayName = "Phi-4 Mini Reasoning · INT4",
            tier = ModelTier.ADVANCED,
            fileName = "model.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-reasoning/resolve/62a183af7b3d3d46f8df17dde9bf8381988bbf68/model.litertlm",
            expectedBytes = 2_784_187_376L,
            sha256 = "d507262ca3d01d93d59c573a82e6f2eeb3aec21d9a67c31fb4c0f93d17225b8f",
            license = "MIT",
            contextTokens = 4096,
            minTotalRamBytes = 16L * GIB,
            minAvailableRamBytes = 8L * GIB,
            minFreeStorageBytes = 7L * GIB,
            runtimeRequirement = "CPU/GPU bundle; 4,096-token context; large reasoning model. Conservative 5 GiB process peak limit; validate on the target device before regular use.",
            sourceUrl = "https://huggingface.co/litert-community/Phi-4-mini-reasoning",
            maxProcessPeakBytes = 5L * GIB
        )
    )

    fun modelsFor(tier: ModelTier): List<ModelSpec> = models.filter { it.tier == tier }

    /**
     * Download eligibility is based on physical RAM and storage only.
     * Temporary RAM pressure must not hide a model the user can download for later.
     */
    fun eligibility(context: Context, model: ModelSpec): DeviceEligibility {
        val memory = readMemory(context)
        val freeStorage = StatFs(context.filesDir.absolutePath).availableBytes
        val reasons = buildList {
            if (memory.totalMem < model.minTotalRamBytes) add("Android-reported physical RAM is below this model's minimum.")
            if (freeStorage < model.minFreeStorageBytes) add("Free more app storage before downloading this model.")
        }
        return DeviceEligibility(reasons.isEmpty(), memory.totalMem, memory.availMem, freeStorage, reasons)
    }

    /** Runtime eligibility also checks current free RAM immediately before engine creation. */
    fun runtimeEligibility(context: Context, model: ModelSpec): DeviceEligibility {
        val memory = readMemory(context)
        val freeStorage = StatFs(context.filesDir.absolutePath).availableBytes
        val reasons = buildList {
            if (memory.totalMem < model.minTotalRamBytes) add("Android-reported physical RAM is below this model's minimum.")
            if (memory.availMem < model.minAvailableRamBytes) add("Currently available system RAM is below this model's runtime requirement. Close other apps and recheck.")
            if (freeStorage < model.minFreeStorageBytes) add("Free app storage is below this model's runtime reserve of ${model.minFreeStorageBytes / MIB} MiB.")
        }
        return DeviceEligibility(reasons.isEmpty(), memory.totalMem, memory.availMem, freeStorage, reasons)
    }

    private fun readMemory(context: Context): ActivityManager.MemoryInfo {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    }

    /** Remove only the superseded, fixed catalog artifact from the previous app version. */
    fun removeLegacyArtifact(context: Context) {
        val modelsDir = File(context.filesDir, "models")
        File(modelsDir, "qwen3_0_6b_mixed_int4.litertlm").delete()
        File(modelsDir, "qwen3_0_6b_mixed_int4.litertlm.part").delete()
    }

    fun modelFile(context: Context, model: ModelSpec): File =
        File(File(context.filesDir, "models"), model.fileName)

    fun partialFile(context: Context, model: ModelSpec): File =
        File(File(context.filesDir, "models"), "${model.fileName}.part")
}
