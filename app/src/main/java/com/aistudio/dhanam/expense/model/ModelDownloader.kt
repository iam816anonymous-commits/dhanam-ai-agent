package com.aistudio.dhanam.expense.model

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class ModelDownloader(private val context: Context) {
    suspend fun download(
        model: ModelSpec,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(model.downloadUrl.startsWith("https://")) { "Model downloads must use HTTPS." }
        val destination = ModelCatalog.modelFile(context, model)
        val partial = ModelCatalog.partialFile(context, model)
        destination.parentFile?.mkdirs()
        var offset = partial.takeIf { it.isFile }?.length() ?: 0L
        if (offset > model.expectedBytes) { partial.delete(); offset = 0L }
        val digest = MessageDigest.getInstance("SHA-256")
        if (offset > 0L) {
            FileInputStream(partial).use { existing ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = existing.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
        }
        onProgress(offset, model.expectedBytes)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 30_000
                requestMethod = "GET"
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "DhanamAI-ModelManager/1")
                if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
            }
            connection.connect()
            check(connection.url.protocol.equals("https", ignoreCase = true)) { "The model source redirected away from HTTPS; download rejected." }
            val response = connection.responseCode
            if (response == 416 &&
                offset == model.expectedBytes && offset > 0L
            ) {
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                check(actualSha.equals(model.sha256, ignoreCase = true)) {
                    partial.delete()
                    "The saved model download failed SHA-256 verification and was removed."
                }
                try {
                    Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                return@withContext destination
            }
            val append = offset > 0L && response == HttpURLConnection.HTTP_PARTIAL
            if (append) {
                val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                check(contentRange.startsWith("bytes $offset-", ignoreCase = true)) {
                    "The server returned an invalid resume range. Your partial download is preserved; retry to restart safely."
                }
            } else {
                check(response == HttpURLConnection.HTTP_OK) { "Download server returned HTTP $response." }
                if (offset > 0L) {
                    partial.delete()
                    offset = 0L
                    digest.reset()
                    onProgress(0L, model.expectedBytes)
                }
            }
            val responseLength = connection.contentLengthLong
            val expectedResponseBytes = model.expectedBytes - offset
            check(responseLength == -1L || responseLength == expectedResponseBytes) {
                "The server's response size does not match the remaining reviewed model artifact."
            }
            var received = offset
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(partial, append)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        received += count
                        check(received <= model.expectedBytes) { "Download exceeded the reviewed model size." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(received, model.expectedBytes)
                    }
                    output.flush()
                }
            }
            check(received == model.expectedBytes) { "Download paused at $received of ${model.expectedBytes} bytes. Reopen Download to resume." }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha.equals(model.sha256, ignoreCase = true)) {
                partial.delete()
                throw IllegalStateException("SHA-256 verification failed. The incomplete or corrupt model was removed.")
            }
            try {
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            destination
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            connection?.disconnect()
        }
    }
    suspend fun delete(model: ModelSpec) = withContext(Dispatchers.IO) {
        ModelCatalog.partialFile(context, model).delete()
        ModelCatalog.modelFile(context, model).delete()
    }
}
