package com.aistudio.dhanam.expense.voice

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class WakeCommand(
    val transcript: String,
    val languageTag: String
)

/** Process-local queue used to deliver commands from the wake-word service to the app UI. */
object WakeCommandBus {
    private val channel = Channel<WakeCommand>(Channel.UNLIMITED)
    val commands = channel.receiveAsFlow()

    fun offer(transcript: String, languageTag: String) {
        if (transcript.isNotBlank()) channel.trySend(WakeCommand(transcript.trim(), languageTag))
    }
}
