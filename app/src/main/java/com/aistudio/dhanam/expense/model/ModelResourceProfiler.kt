package com.aistudio.dhanam.expense.model

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock

data class DeviceMemoryProfile(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowMemory: Boolean,
    val systemLowMemoryThresholdBytes: Long,
    val process: ProcessMemorySnapshot?,
    val nativeHeapAllocatedBytes: Long,
    val capturedAtElapsedRealtimeMs: Long
)

data class ModelRuntimeMeasurement(
    val modelId: String,
    val loadDurationMs: Long,
    val processResidentBytesAfterLoad: Long?,
    val processHighWaterBytesAfterLoad: Long?,
    val nativeHeapAllocatedBytesAfterLoad: Long,
    val systemAvailableRamBytesAfterLoad: Long
)

object ModelResourceProfiler {
    const val MIN_SYSTEM_RESERVE_BYTES = 256L * 1024L * 1024L

    fun capture(context: Context): DeviceMemoryProfile {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return DeviceMemoryProfile(
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            lowMemory = memory.lowMemory,
            systemLowMemoryThresholdBytes = memory.threshold,
            process = ProcessMemoryBudget.readSnapshot(),
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
            capturedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
        )
    }

    fun requiredAvailableRamBytes(model: ModelSpec, profile: DeviceMemoryProfile): Long =
        model.minAvailableRamBytes + maxOf(profile.systemLowMemoryThresholdBytes, MIN_SYSTEM_RESERVE_BYTES)

    fun runtimeBlockReasons(model: ModelSpec, profile: DeviceMemoryProfile, freeStorageBytes: Long): List<String> = buildList {
        if (profile.totalRamBytes < model.minTotalRamBytes) add("Android-reported physical RAM is below this model's minimum.")
        if (profile.lowMemory) add("Android currently reports system-wide low memory. Wait for memory pressure to clear and recheck.")
        val required = requiredAvailableRamBytes(model, profile)
        if (profile.availableRamBytes < required) {
            add("Available system RAM is below the model requirement plus Android's safety reserve (" +
                (required / (1024L * 1024L)) + " MiB required). Close other apps and recheck.")
        }
        if (freeStorageBytes < model.minFreeStorageBytes) {
            add("Free app storage is below this model's runtime reserve of " +
                (model.minFreeStorageBytes / (1024L * 1024L)) + " MiB.")
        }
    }
}
