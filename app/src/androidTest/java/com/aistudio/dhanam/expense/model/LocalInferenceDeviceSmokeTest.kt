package com.aistudio.dhanam.expense.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Manual/device smoke test; never downloads a model itself. */
@RunWith(AndroidJUnit4::class)
class LocalInferenceDeviceSmokeTest {
    @Test(timeout = 180_000)
    fun verifiedModelLoadsAndGeneratesWithinTwoGiBProcessPeak() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = ModelCatalog.models.single()
        val file = ModelCatalog.modelFile(context, model)
        assumeTrue("Download the model in Dhanam Settings before running this device test.", file.isFile)
        assumeTrue("The model file is not the reviewed artifact.", ModelFileVerifier.isValid(file, model.expectedBytes, model.sha256))
        val eligibility = ModelCatalog.runtimeEligibility(context, model)
        assumeTrue("The device does not meet runtime resource thresholds.", eligibility.allowed)
        val initialPeak = ProcessMemoryBudget.readPeakResidentBytes()
        assumeTrue("Cannot read this process's peak RSS; refusing to run the memory-qualified test.", initialPeak != null)
        assumeTrue("Process has already reached the 2 GiB memory budget.", initialPeak!! < ModelCatalog.MAX_PROCESS_PEAK_BYTES)

        val engine = Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        )
        // Do not close the engine if initialize() throws; this is LiteRT-LM's documented lifecycle rule.
        engine.initialize()
        try {
            val peakAfterLoad = ProcessMemoryBudget.readPeakResidentBytes()
            assertTrue("Could not measure process peak RSS after load.", peakAfterLoad != null)
            assertTrue("Model load exceeded the 2 GiB process peak budget.", peakAfterLoad!! < ModelCatalog.MAX_PROCESS_PEAK_BYTES)
            val conversation = engine.createConversation(
                ConversationConfig(systemInstruction = Contents.of("Answer briefly and clearly."))
            )
            try {
                val reply = conversation.sendMessage("Reply with one short sentence saying the local model is ready.")
                assertTrue("The model returned an empty response.", reply.toString().isNotBlank())
                val peakAfterGeneration = ProcessMemoryBudget.readPeakResidentBytes()
                assertTrue("Could not measure process peak RSS after generation.", peakAfterGeneration != null)
                assertTrue("Inference exceeded the 2 GiB process peak budget.", peakAfterGeneration!! < ModelCatalog.MAX_PROCESS_PEAK_BYTES)
            } finally {
                conversation.close()
            }
        } finally {
            engine.close()
        }
    }
}
