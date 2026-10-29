package com.aistudio.dhanam.expense.model

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

data class ModelSpec(
    val id: String, val displayName: String, val fileName: String, val downloadUrl: String,
    val expectedBytes: Long, val sha256: String, val license: String, val contextTokens: Int,
    val minTotalRamBytes: Long, val minAvailableRamBytes: Long, val minFreeStorageBytes: Long,
    val runtimeRequirement: String, val sourceUrl: String
)

data class DeviceEligibility(
    val allowed: Boolean, val totalRamBytes: Long, val availableRamBytes: Long,
    val freeStorageBytes: Long, val reasons: List<String>
)

object ModelCatalog {
    private const val MIB = 1024L * 1024L
    const val MAX_PROCESS_PEAK_BYTES: Long = 2L * 1024L * MIB
    private const val MIN_AVAILABLE_RAM_MIB = 1536L
    private const val MIN_TOTAL_RAM_MIB = 3500L

    val models = listOf(
        ModelSpec(
            id = "qwen3-0.6b-int4-nothink-1280",
            displayName = "Qwen3 0.6B · INT4 No-Think",
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/6aa2daf/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            expectedBytes = 347_251_840L,
            sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
            license = "Apache-2.0",
            contextTokens = 1280,
            minTotalRamBytes = MIN_TOTAL_RAM_MIB * MIB,
            minAvailableRamBytes = MIN_AVAILABLE_RAM_MIB * MIB,
            minFreeStorageBytes = 1280L * MIB,
            runtimeRequirement = "CPU only; 1,280-token KV cache; process peak RSS must remain below 2 GiB.",
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4"
        )
    )

    fun eligibility(context: Context, model: ModelSpec): DeviceEligibility {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val freeStorage = StatFs(context.filesDir.absolutePath).availableBytes
        val reasons = buildList {
            if (memory.totalMem < model.minTotalRamBytes) add("Device RAM is below the model's minimum total-memory requirement.")
            if (memory.availMem < model.minAvailableRamBytes) add("At least 1,536 MiB of currently available RAM is required. Close other apps and recheck.")
            if (freeStorage < model.minFreeStorageBytes) add("Free at least 1.25 GiB of app storage before downloading.")
        }
        return DeviceEligibility(reasons.isEmpty(), memory.totalMem, memory.availMem, freeStorage, reasons)
    }

    fun runtimeEligibility(context: Context, model: ModelSpec): DeviceEligibility {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val freeStorage = StatFs(context.filesDir.absolutePath).availableBytes
        val reasons = buildList {
            if (memory.totalMem < model.minTotalRamBytes) add("Device RAM is below the model's minimum total-memory requirement.")
            if (memory.availMem < model.minAvailableRamBytes) add("At least 1,536 MiB of currently available RAM is required before loading. Close other apps and recheck.")
            if (freeStorage < 256L * MIB) add("At least 256 MiB of free app storage is required for runtime cache and temporary files.")
        }
        return DeviceEligibility(reasons.isEmpty(), memory.totalMem, memory.availMem, freeStorage, reasons)
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
