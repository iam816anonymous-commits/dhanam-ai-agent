# Phase A validation matrix

This checklist is for physical-device qualification. A green compile/lint workflow is not proof that microphone capture, wake-word detection, SQLCipher migration or speech accuracy works on a handset.

## 1. Database encryption and upgrade safety

- [ ] Install the previous plaintext-database release and create representative expenses, income, budgets, categories, Khata entries and repayments.
- [ ] Upgrade in place to the Phase A build. Confirm the first launch shows the database-startup gate while migration runs off the main thread, then completes without losing rows or changing paise totals.
- [ ] Confirm Room opens the encrypted database and the stored user_version remains the supported schema version.
- [ ] Force-stop during a migration test build at each rename boundary. On next launch, confirm the original database is restored or the verified encrypted database is opened; never accept an empty replacement database.
- [ ] Reboot and relaunch. Confirm the Keystore-wrapped key still opens the database.
- [ ] Confirm a missing/invalid key produces a clear fail-closed error and does not delete/recreate the finance database.
- [ ] Confirm no plaintext migration backup or temporary database remains after successful migration.
- [ ] Verify encrypted database and WAL files are unreadable through the standard Android SQLite driver.
- [ ] Do not uninstall/clear app data during the migration test; there is no user-controlled encrypted backup/restore yet.

## 2. Offline speech model installation

- [ ] Download only the required language model from Voice settings; verify progress, completion and installed state.
- [ ] Test general English, Indian English and Hindi independently. Switch between English models and confirm the selected model is the one loaded.
- [ ] After download, enable airplane mode and transcribe several commands. Confirm the Vosk route remains local.
- [ ] Interrupt a download, retry, and confirm the partial archive is not marked installed.
- [ ] Test low storage. Installation must stop before extraction if free storage is below the calculated safety margin.
- [ ] Test a corrupt or malformed archive in a test build. The installer must reject path traversal, unexpected archive roots, oversized archives and missing model files.
- [ ] Test with less than 512 MB available RAM and on a device below 2 GB physical RAM. Vosk loading must be refused before native model initialization.
- [ ] Record peak RSS, model-load time, command latency, CPU use, battery and thermal behaviour on each target device.

### Language quality boundary

- General English and Hindi are usable starting points, not financial-accuracy guarantees.
- The Indian-English small model has a weak published benchmark score and is labelled experimental.
- The small Telugu Vosk model is intentionally not offered because its published 87.9 WER is unsuitable for financial command entry. Telugu must use a device-provided dedicated on-device recognizer or the explicitly enabled online provider until a better multilingual offline model is benchmarked.

## 3. Speech privacy routing

- [ ] Fresh install: online speech fallback is off.
- [ ] With no Vosk model and no dedicated on-device recognizer, confirm voice input is blocked and explains why; it must not silently call the default system provider.
- [ ] With a Vosk model installed, confirm it is selected before Android's system provider.
- [ ] On API 31+, confirm the dedicated on-device recognizer is selected when available.
- [ ] Enable online fallback explicitly and confirm the system provider can be used when local recognition is unavailable.
- [ ] Disable online fallback again and confirm the remote-capable system provider is blocked.
- [ ] Use Android network inspection/logging in a test build to verify the Vosk path does not make network requests during transcription.
- [ ] Confirm notification text never displays the recognized financial command on the lock screen.

## 4. Hey Jarvis service lifecycle

- [ ] Start the microphone foreground service only from the visible app after microphone permission is granted.
- [ ] On Android 14+, verify the microphone foreground-service permission/type and while-in-use restrictions. Starting from a background state must fail visibly and reset the setting.
- [ ] Lock the screen while the already-started service is listening. Confirm the persistent notification remains and Porcupine continues to listen where OEM policy permits.
- [ ] Say “Hey Jarvis” repeatedly. One wake event must produce one speech session; duplicate callbacks must not create concurrent recorders.
- [ ] Confirm Porcupine releases the microphone before Vosk/Android speech recognition starts.
- [ ] Test no speech, very long speech, recognizer timeout, unsupported language, microphone busy, permission revocation, Porcupine audio error and service stop during transcription.
- [ ] Confirm the service releases AudioRecord, SpeechService, Recognizer, Model, PorcupineManager and ToneGenerator resources on every exit path.
- [ ] Confirm it rearms only after the previous speech session releases the microphone.
- [ ] Confirm a Porcupine error updates the notification, disables the persisted listening state and stops the service rather than leaving a dead “enabled” switch.
- [ ] When Dhanam is visible, confirm the command broadcast is delivered directly to the app. When Dhanam is backgrounded, confirm the notification opens the review screen after a user tap.
- [ ] Test screen lock, app process death, service restart, force-stop, OEM battery management and notification permission denial.

## 5. Financial command safety

Use a fixed set of English, Telugu and Hindi utterances. Include:

- [ ] Expense and income commands with decimal and whole-rupee amounts.
- [ ] Category, merchant/payee and date variations.
- [ ] Ambiguous amount/date/category; app asks a clarification instead of guessing.
- [ ] Duplicate wake events and repeated identical commands; no duplicate transaction is saved.
- [ ] Khata lending, borrowing and repayment commands.
- [ ] Corrections such as “not 500, 1500” and “change category to food”.
- [ ] Low-confidence or garbled transcripts; app requires review/correction.
- [ ] Every write is previewed and explicitly confirmed; transcription alone never writes a financial record.

## 6. Required device matrix

| Device class | Required coverage |
|---|---|
| Android 8.1 / API 27 / 4 GB RAM | Vosk offline English/Hindi, online-consent path, microphone foreground service, database migration |
| Android 12 / API 31 | Dedicated on-device recognizer availability and language fallback |
| Android 14+ / API 34+ | Microphone FGS while-in-use restrictions, notification and permission behaviour |
| 8 GB RAM device | Model load, peak RSS and thermal qualification |
| 16 GB+ device | Verify physical RAM reporting; do not count RAM expansion/swap as physical memory |

## 7. Build and test commands

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

The standard CI compiles the instrumented-test APK but does not run it on a physical device. Execute the SQLCipher instrumentation test and this checklist on real supported devices before declaring Phase A release-ready.
