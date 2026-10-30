package com.aistudio.dhanam.expense.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun AppLockGate(
    content: @Composable (onLock: () -> Unit, canLock: Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var configured by remember { mutableStateOf(PinSecurity.isConfigured(context)) }
    var locked by remember { mutableStateOf(configured) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context, lifecycleOwner) {
        val prefs = context.getSharedPreferences(PinSecurity.PREFS, android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pin_hash") {
                configured = PinSecurity.isConfigured(context)
                if (!configured) locked = false
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (PinSecurity.isConfigured(context)) locked = true
                Lifecycle.Event.ON_START -> {
                    configured = PinSecurity.isConfigured(context)
                    if (configured) locked = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!configured || !locked) {
        content(
            { if (configured) { locked = true; pin = ""; error = null } },
            configured
        )
    } else {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("DHANAM", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("App locked", modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Enter your device-local PIN to view financial records.",
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp))
                Button(onClick = {
                    if (PinSecurity.verifyPin(context, pin)) {
                        locked = false
                        pin = ""
                        error = null
                    } else {
                        pin = ""
                        error = "Incorrect PIN or temporary lockout. Try again shortly."
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("Unlock") }
                if (PinSecurity.isBiometricEnabled(context)) {
                    TextButton(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity == null) {
                                biometricError = "Biometric unlock is unavailable here. Use your PIN."
                            } else {
                                val availability = BiometricManager.from(activity).canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                                )
                                if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                                    biometricError = "No enrolled strong biometric is available. Use your PIN."
                                } else {
                                    val prompt = BiometricPrompt(
                                        activity,
                                        ContextCompat.getMainExecutor(activity),
                                        object : BiometricPrompt.AuthenticationCallback() {
                                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                locked = false
                                                pin = ""
                                                error = null
                                                biometricError = null
                                            }
                                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                biometricError = errString.toString()
                                            }
                                            override fun onAuthenticationFailed() {
                                                biometricError = "Biometric not recognized. Try again or use your PIN."
                                            }
                                        }
                                    )
                                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                        .setTitle("Unlock Dhanam AI")
                                        .setSubtitle("Authenticate to view financial records")
                                        .setNegativeButtonText("Use PIN")
                                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                        .build()
                                    prompt.authenticate(promptInfo)
                                }
                            }
                        }
                    ) { Text("Unlock with fingerprint or face") }
                }
                if (biometricError != null) Text(
                    biometricError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
