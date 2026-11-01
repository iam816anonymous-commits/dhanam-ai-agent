package com.aistudio.dhanam.expense.voice

import java.util.concurrent.atomic.AtomicReference

enum class WakeWordLifecycleState {
    STOPPED,
    STARTING,
    LISTENING,
    TRANSCRIBING,
    RECOVERING,
    FAILED
}

/**
 * Atomic state machine for the microphone service. It rejects duplicate wake callbacks and
 * prevents a restarted Porcupine engine from racing an active speech-recognition session.
 */
class WakeWordLifecycleController {
    private val state = AtomicReference(WakeWordLifecycleState.STOPPED)

    fun current(): WakeWordLifecycleState = state.get()

    fun beginStart() {
        state.updateAndGet { current ->
            if (current == WakeWordLifecycleState.STOPPED || current == WakeWordLifecycleState.FAILED) {
                WakeWordLifecycleState.STARTING
            } else current
        }
    }

    fun beginListening(): Boolean {
        while (true) {
            val current = state.get()
            if (current != WakeWordLifecycleState.STARTING && current != WakeWordLifecycleState.RECOVERING) return false
            if (state.compareAndSet(current, WakeWordLifecycleState.LISTENING)) return true
        }
    }

    fun beginTranscription(): Boolean =
        state.compareAndSet(WakeWordLifecycleState.LISTENING, WakeWordLifecycleState.TRANSCRIBING)

    fun beginRecovery(): Boolean =
        state.compareAndSet(WakeWordLifecycleState.TRANSCRIBING, WakeWordLifecycleState.RECOVERING)

    fun fail() {
        state.set(WakeWordLifecycleState.FAILED)
    }

    fun stop() {
        state.set(WakeWordLifecycleState.STOPPED)
    }
}
