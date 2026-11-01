package com.aistudio.dhanam.expense.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRecognitionPolicyTest {
    @Test
    fun installedVoskModelAlwaysWinsAndStaysOffline() {
        assertEquals(
            SpeechRecognitionRoute.OFFLINE_VOSK_MODEL,
            SpeechRecognitionPolicy.route(
                sdkInt = 27,
                onDeviceRecognizerAvailable = false,
                allowNetworkFallback = true,
                offlineModelInstalled = true
            )
        )
    }

    @Test
    fun api27WithoutNetworkConsentIsBlocked() {
        assertEquals(
            SpeechRecognitionRoute.BLOCKED,
            SpeechRecognitionPolicy.route(27, onDeviceRecognizerAvailable = false, allowNetworkFallback = false)
        )
    }

    @Test
    fun api27WithExplicitNetworkConsentUsesSystemProvider() {
        assertEquals(
            SpeechRecognitionRoute.SYSTEM_PROVIDER_NETWORK_CONSENT,
            SpeechRecognitionPolicy.route(27, onDeviceRecognizerAvailable = false, allowNetworkFallback = true)
        )
    }

    @Test
    fun api30DoesNotSelectApi31OnDeviceRecognizer() {
        assertEquals(
            SpeechRecognitionRoute.BLOCKED,
            SpeechRecognitionPolicy.route(30, onDeviceRecognizerAvailable = true, allowNetworkFallback = false)
        )
    }

    @Test
    fun api31UsesDedicatedOnDeviceRecognizerWhenAvailable() {
        assertEquals(
            SpeechRecognitionRoute.ON_DEVICE,
            SpeechRecognitionPolicy.route(31, onDeviceRecognizerAvailable = true, allowNetworkFallback = true)
        )
    }

    @Test
    fun api31WithoutOnDeviceProviderRequiresConsent() {
        assertEquals(
            SpeechRecognitionRoute.BLOCKED,
            SpeechRecognitionPolicy.route(31, onDeviceRecognizerAvailable = false, allowNetworkFallback = false)
        )
    }

    @Test
    fun api31CanUseSystemProviderOnlyAfterConsent() {
        assertEquals(
            SpeechRecognitionRoute.SYSTEM_PROVIDER_NETWORK_CONSENT,
            SpeechRecognitionPolicy.route(31, onDeviceRecognizerAvailable = false, allowNetworkFallback = true)
        )
    }
}
