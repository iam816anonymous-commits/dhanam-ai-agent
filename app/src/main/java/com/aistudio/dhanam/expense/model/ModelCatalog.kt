package com.aistudio.dhanam.expense.model

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

data class ModelSpec(
    val id: String, val displayName: String, val fileName: String, val downloadUrl: String,
    val expectedBytes: Long, val sha256: String, val license: String, val contextTokens: Int,
    val minTotalRamBytes: Long, val minAvailableRamBytes: Long, val minFreeStorageBytes: Long,
    val runtimeRequirement: String, val sourceUrl: String,
    val maxProcessPeakBytes: Long
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
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4",
            maxProcessPeakBytes = 2L * 1024L * MIB
        ),
        ModelSpec(
            id = "qwen3-1.7b-int4-4096",
            displayName = "Qwen3 1.7B · INT4",
            fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/d9b8a91/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            expectedBytes = 977_184_032L,
            sha256 = "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
            license = "Apache-2.0",
            contextTokens = 4096,
            minTotalRamBytes = 8L * 1024L * MIB,
            minAvailableRamBytes = 4L * 1024L * MIB,
            minFreeStorageBytes = 3L * 1024L * MIB,
            runtimeRequirement = "CPU only; 4,096-token context; 3.5 GiB process peak RSS budget. Larger-model tier; not qualified on 4 GiB phones.",
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-1.7B",
            maxProcessPeakBytes = 3584L * MIB
        ),
        ModelSpec(
            id = "qwen3-4b-mixed-int4-2048",
            displayName = "Qwen3 4B · Mixed INT4",
            fileName = "qwen3_4b_mixed_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-4B/resolve/2f7415b4e3a149c16b929659d146a6eaf6d99a34/qwen3_4b_mixed_int4.litertlm",
            expectedBytes = 2_659_053_760L,
            sha256 = "569fe029f51e55b6377f98ebd7f6d26431bc73e1d632034f5389f2fd720da701",
            license = "Apache-2.0",
            contextTokens = 2048,
            minTotalRamBytes = 12L * 1024L * MIB,
            minAvailableRamBytes = 7L * 1024L * MIB,
            minFreeStorageBytes = 7L * 1024L * MIB,
            runtimeRequirement = "CPU only; 2,048-token context; 6 GiB process peak RSS budget. High-memory tier; not qualified on mid-range devices.",
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-4B",
            maxProcessPeakBytes = 6L * 1024L * MIB
        )
    )

    fun eligibility(context: Context, model: ModelSpec): DeviceEligibility {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val freeStorage = StatFs(context.filesDir.absolutePath).availableBytes
        val reasons = buildList {
            if (memory.totalMem < model.minTotalRamBytes) add("Device RAM is below the model's minimum total-memory requirement.")
            if (memory.availMem < model.minAvailableRamBytes) add("Currently available RAM is below this model’s requirement. Close other apps and recheck.")
            if (freeStorage < model.minFreeStorageBytes) add("Free more app storage before downloading this model.")
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
            if (memory.availMem < model.minAvailableRamBytes) add("Currently available RAM is below this model’s requirement before loading. Close other apps and recheck.")
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
