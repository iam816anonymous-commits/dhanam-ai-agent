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
    fun verifiedInstalledModelLoadsAndGeneratesWithinItsDeclaredProcessPeakBudget() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // The catalogue now intentionally contains multiple models. Select a model
        // the user has already downloaded and verified instead of assuming one entry.
        val model = ModelCatalog.models.firstOrNull { candidate ->
            val candidateFile = ModelCatalog.modelFile(context, candidate)
            candidateFile.isFile &&
                ModelFileVerifier.isValid(candidateFile, candidate.expectedBytes, candidate.sha256)
        }
        assumeTrue("Download a catalogue model in Dhanam Settings before running this device test.", model != null)
        val selectedModel = model ?: return
        val file = ModelCatalog.modelFile(context, selectedModel)

        val eligibility = ModelCatalog.runtimeEligibility(context, selectedModel)
        assumeTrue("The device does not meet runtime resource thresholds.", eligibility.allowed)

        val initialPeak = ProcessMemoryBudget.readPeakResidentBytes()
        assumeTrue("Cannot read this process's peak RSS; refusing to run the memory-qualified test.", initialPeak != null)
        assumeTrue(
            "Process has already reached this model's declared peak-memory budget.",
            initialPeak!! < selectedModel.maxProcessPeakBytes
        )

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
            assertTrue(
                "Model load exceeded its declared process peak budget.",
                peakAfterLoad!! < selectedModel.maxProcessPeakBytes
            )
            val conversation = engine.createConversation(
                ConversationConfig(systemInstruction = Contents.of("Answer briefly and clearly."))
            )
            try {
                val reply = conversation.sendMessage("Reply with one short sentence saying the local model is ready.")
                assertTrue("The model returned an empty response.", reply.toString().isNotBlank())
                val peakAfterGeneration = ProcessMemoryBudget.readPeakResidentBytes()
                assertTrue("Could not measure process peak RSS after generation.", peakAfterGeneration != null)
                assertTrue(
                    "Inference exceeded its declared process peak budget.",
                    peakAfterGeneration!! < selectedModel.maxProcessPeakBytes
                )
            } finally {
                conversation.close()
            }
        } finally {
            engine.close()
        }
    }
}
