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

    // The conservative available-RAM gate reflects published measurements on a different TECNO model.
    // Never offer a download merely because the artifact's file size fits available storage.
    val models = listOf(
        ModelSpec(
            id = "qwen3-0.6b-mixed-int4",
            displayName = "Qwen3 0.6B · Mixed INT4",
            fileName = "qwen3_0_6b_mixed_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/154c5de0fc0fc5002a7da570ba8c67edffa32ca2/qwen3_0_6b_mixed_int4.litertlm",
            expectedBytes = 497_516_544L,
            sha256 = "7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1",
            license = "Apache-2.0",
            contextTokens = 2048,
            minTotalRamBytes = 3500L * MIB,
            minAvailableRamBytes = 2304L * MIB,
            minFreeStorageBytes = 1280L * MIB,
            runtimeRequirement = "LiteRT-LM Android runtime; inference remains disabled until device validation.",
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-0.6B"
        )
    )

    fun eligibility(context: Context, model: ModelSpec): DeviceEligibility {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val freeStorage = StatFs(context.filesDir.absolutePath).availableBytes
        val reasons = buildList {
            if (memory.totalMem < model.minTotalRamBytes) add("Device RAM is below the model's minimum total-memory requirement.")
            if (memory.availMem < model.minAvailableRamBytes) add("Currently available RAM is below the safe threshold. Close other apps and recheck.")
            if (freeStorage < model.minFreeStorageBytes) add("Free at least 1.25 GiB of app storage before downloading.")
        }
        return DeviceEligibility(reasons.isEmpty(), memory.totalMem, memory.availMem, freeStorage, reasons)
    }

    /** Recheck load-time memory and cache headroom after the model has already been downloaded. */
    fun runtimeEligibility(context: Context, model: ModelSpec): DeviceEligibility {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val freeStorage = StatFs(context.filesDir.absolutePath).availableBytes
        val reasons = buildList {
            if (memory.totalMem < model.minTotalRamBytes) add("Device RAM is below the model's minimum total-memory requirement.")
            if (memory.availMem < model.minAvailableRamBytes) add("Currently available RAM is below the safe inference threshold. Close other apps and recheck.")
            if (freeStorage < 256L * MIB) add("At least 256 MiB of free app storage is required for runtime cache and temporary files.")
        }
        return DeviceEligibility(reasons.isEmpty(), memory.totalMem, memory.availMem, freeStorage, reasons)
    }

    fun modelFile(context: Context, model: ModelSpec): File =
        File(File(context.filesDir, "models"), model.fileName)

    fun partialFile(context: Context, model: ModelSpec): File =
        File(File(context.filesDir, "models"), "${model.fileName}.part")
}
