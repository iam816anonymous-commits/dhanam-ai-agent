package com.aistudio.dhanam.expense.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object OfflineSpeechModelInstaller {
    private const val MAX_DOWNLOAD_BYTES = 150L * 1024L * 1024L
    private const val MAX_EXTRACTED_BYTES = 300L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES = 10_000
    private const val BUFFER_SIZE = 32 * 1024

    suspend fun install(
        context: Context,
        spec: OfflineSpeechModelSpec,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(spec.downloadUrl.startsWith("https://alphacephei.com/")) {
            "Speech model source is not an approved HTTPS host."
        }
        val root = File(context.filesDir, "speech/models").apply { mkdirs() }
        val finalDirectory = File(root, spec.id)
        val backup = File(root, spec.id + ".previous")
        if (OfflineSpeechModelCatalog.isValidModelDirectory(finalDirectory)) return@withContext finalDirectory
        if (OfflineSpeechModelCatalog.isValidModelDirectory(backup)) {
            if (finalDirectory.exists()) deleteRecursively(finalDirectory)
            check(backup.renameTo(finalDirectory)) {
                "Could not recover the previous offline speech model installation."
            }
            return@withContext finalDirectory
        }

        val requiredBytes = spec.approximateDownloadBytes * 3L + 64L * 1024L * 1024L
        check(OfflineSpeechModelCatalog.availableBytes(context) >= requiredBytes) {
            "Not enough free storage. Keep at least ${requiredBytes / (1024L * 1024L)} MB free and retry."
        }

        val archive = File(root, spec.id + ".zip.part")
        val staging = File(root, spec.id + ".installing")
        archive.delete()
        deleteRecursively(staging)
        if (backup.exists()) deleteRecursively(backup)

        try {
            download(spec, archive, onProgress)
            extractSafely(archive, staging, spec.archiveRoot)
            check(OfflineSpeechModelCatalog.isValidModelDirectory(staging)) {
                "The downloaded package is missing required Vosk model files."
            }

            if (finalDirectory.exists()) {
                check(finalDirectory.renameTo(backup)) {
                    "Could not stage the previous speech model for replacement."
                }
            }
            if (!staging.renameTo(finalDirectory)) {
                if (backup.exists()) backup.renameTo(finalDirectory)
                throw IllegalStateException("Could not install the speech model. The previous model was restored.")
            }
            deleteRecursively(backup)
            archive.delete()
            finalDirectory
        } catch (error: Exception) {
            deleteRecursively(staging)
            archive.delete()
            if (!finalDirectory.exists() && backup.exists()) backup.renameTo(finalDirectory)
            throw error
        }
    }

    private suspend fun download(spec: OfflineSpeechModelSpec, destination: File, onProgress: (Int) -> Unit) {
        val coroutineContext = currentCoroutineContext()
        val connection = (URL(spec.downloadUrl).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DhanamAndroid/1.0")
        }
        try {
            val response = connection.responseCode
            check(connection.url.protocol == "https") { "Speech model download attempted to leave the secure HTTPS connection." }
            check(response in 200..299) { "Speech model download failed (HTTP $response)." }
            val contentLength = connection.contentLengthLong
            check(contentLength <= MAX_DOWNLOAD_BYTES) { "Speech model archive is larger than the allowed download limit." }
            var downloaded = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination).use { fileOutput ->
                    BufferedOutputStream(fileOutput).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            check(downloaded <= MAX_DOWNLOAD_BYTES) {
                                "Speech model archive exceeded the allowed download limit."
                            }
                            output.write(buffer, 0, count)
                            if (contentLength > 0L) {
                                val percent = ((downloaded * 100L) / contentLength).toInt().coerceIn(0, 99)
                                Handler(Looper.getMainLooper()).post { onProgress(percent) }
                            }
                        }
                        output.flush()
                        fileOutput.fd.sync()
                    }
                }
            }
            check(destination.length() >= 1L * 1024L * 1024L) { "The downloaded speech model archive is incomplete." }
            if (contentLength > 0L) {
                check(destination.length() == contentLength) { "The speech model download was interrupted. Retry the download." }
            }
            Handler(Looper.getMainLooper()).post { onProgress(100) }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun extractSafely(archive: File, destination: File, archiveRoot: String) {
        val coroutineContext = currentCoroutineContext()
        val canonicalRoot = destination.canonicalFile
        val canonicalPrefix = canonicalRoot.path + File.separator
        val expectedPrefix = archiveRoot + "/"
        var totalBytes = 0L
        var entries = 0

        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = zip.nextEntry ?: break
                entries += 1
                check(entries <= MAX_ZIP_ENTRIES) { "Speech model archive contains too many files." }
                check(!entry.name.contains(92.toChar())) { "Speech model archive contains an unsafe path separator." }
                val entryName = entry.name
                check(!entryName.startsWith("/") && !entryName.split('/').any { it == ".." }) {
                    "Speech model archive contains an unsafe file path."
                }
                if (entryName == archiveRoot || entryName == archiveRoot + "/") {
                    zip.closeEntry()
                    continue
                }
                check(entryName.startsWith(expectedPrefix)) {
                    "Speech model archive has an unexpected folder layout."
                }
                val relativePath = entryName.removePrefix(expectedPrefix)
                check(relativePath.isNotBlank()) { "Speech model archive contains an empty file path." }
                val output = File(destination, relativePath)
                check(output.canonicalPath.startsWith(canonicalPrefix)) {
                    "Speech model archive attempted to write outside its installation directory."
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { fileOutput ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            check(totalBytes <= MAX_EXTRACTED_BYTES) {
                                "Unpacked speech model exceeds the allowed storage limit."
                            }
                            fileOutput.write(buffer, 0, count)
                        }
                        fileOutput.fd.sync()
                    }
                }
                zip.closeEntry()
            }
        }
        check(entries > 0) { "Speech model archive is empty." }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.delete()
    }
}
