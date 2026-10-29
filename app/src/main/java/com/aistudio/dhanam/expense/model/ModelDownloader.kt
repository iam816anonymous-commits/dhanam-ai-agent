package com.aistudio.dhanam.expense.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
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
        partial.delete()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 30_000
                requestMethod = "GET"
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "DhanamAI-ModelManager/1")
            }
            connection.connect()
            check(connection.url.protocol.equals("https", ignoreCase = true)) {
                "The model source redirected away from HTTPS; download rejected."
            }
            check(connection.responseCode in 200..299) { "Download server returned HTTP ${connection.responseCode}." }
            val remoteLength = connection.contentLengthLong
            check(remoteLength == -1L || remoteLength == model.expectedBytes) {
                "The server's file size does not match the reviewed model artifact."
            }

            var received = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(partial)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        received += count
                        check(received <= model.expectedBytes) { "Download exceeded the reviewed model size; partial data was rejected." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(received, model.expectedBytes)
                    }
                    output.flush()
                }
            }
            check(received == model.expectedBytes) { "Download is incomplete. Retry the download." }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualSha.equals(model.sha256, ignoreCase = true)) { "SHA-256 verification failed. The model was rejected." }
            try {
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                check(partial.renameTo(destination)) { "Could not safely finalize the verified model file." }
            }
            destination
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun delete(model: ModelSpec) = withContext(Dispatchers.IO) {
        ModelCatalog.partialFile(context, model).delete()
        ModelCatalog.modelFile(context, model).delete()
    }
}
