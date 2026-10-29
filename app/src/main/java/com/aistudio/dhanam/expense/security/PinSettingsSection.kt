package com.aistudio.dhanam.expense.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PinSettingsSection() {
    val context = LocalContext.current
    var configured by remember { mutableStateOf(PinSecurity.isConfigured(context)) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("App lock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (configured) "PIN lock is enabled. The app locks when it leaves the foreground." else "Protect financial screens with a local PIN.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (configured) {
                OutlinedTextField(currentPin, { currentPin = it.filter(Char::isDigit).take(12) },
                    label = { Text("Current PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit).take(12) },
                label = { Text(if (configured) "New PIN (4–12 digits)" else "PIN (4–12 digits)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) },
                label = { Text("Confirm PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                try {
                    if (configured && !PinSecurity.verifyPin(context, currentPin)) {
                        error("Current PIN is incorrect.")
                    }
                    require(newPin == confirmPin) { "PIN confirmation does not match." }
                    PinSecurity.setPin(context, newPin)
                    configured = true
                    currentPin = ""; newPin = ""; confirmPin = ""
                    message = "PIN saved. The app will lock when you leave it."
                } catch (e: Exception) { message = e.message ?: "Could not save PIN." }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (configured) "Change PIN" else "Enable PIN lock") }
            if (configured) {
                TextButton(onClick = {
                    if (PinSecurity.clearPin(context, currentPin)) {
                        configured = false; currentPin = ""; newPin = ""; confirmPin = ""
                        message = "PIN lock disabled."
                    } else message = "Current PIN is incorrect or temporarily locked."
                }) { Text("Disable PIN lock") }
            }
            if (message != null) Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
            Text("The PIN is stored as a salted PBKDF2 hash. This app lock does not encrypt the finance database against device-level forensic access.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
