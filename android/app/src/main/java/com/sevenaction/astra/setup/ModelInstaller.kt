package com.sevenaction.astra.setup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelInstaller(private val context: Context) {
    data class Model(
        val name: String,
        val fileName: String,
        val urls: List<String>
    )

    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }

    val llm = Model(
        "Astra AI (Qwen3 1.7B Q4_K_M)",
        "qwen3-1.7b-q4_k_m.gguf",
        listOf(
            "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            "https://huggingface.co/Antigma/Qwen3-1.7B-GGUF/resolve/main/qwen3-1.7b-q4_k_m.gguf"
        )
    )

    val whisper = Model(
        "Whisper Base Q5_1",
        "ggml-base-q5_1.bin",
        listOf(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
        )
    )

    fun file(model: Model) = File(modelDir, model.fileName)

    fun allInstalled() =
        file(llm).exists() && file(llm).length() > 500L * 1024L * 1024L &&
        file(whisper).exists() && file(whisper).length() > 10L * 1024L * 1024L

    suspend fun installAll(progress: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        downloadWithFallback(llm, progress)
        downloadWithFallback(whisper, progress)
    }

    private fun downloadWithFallback(model: Model, progress: (String, Int) -> Unit) {
        val target = file(model)
        if (target.exists() && target.length() > 1024 * 1024) {
            progress(model.name, 100)
            return
        }

        var lastError: Exception? = null
        for (url in model.urls) {
            try {
                download(model, url, progress)
                return
            } catch (e: Exception) {
                lastError = e
                File(target.absolutePath + ".part").delete()
            }
        }
        throw lastError ?: IllegalStateException("Aucune source de téléchargement disponible.")
    }

    private fun download(model: Model, url: String, progress: (String, Int) -> Unit) {
        val target = file(model)
        val temp = File(target.absolutePath + ".part")
        if (temp.exists()) temp.delete()

        val connection = openFollowingRedirects(url)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Téléchargement impossible (" + connection.responseCode + ")")
            }

            val total = connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            progress(model.name, ((done * 100) / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
            }

            if (temp.length() < 1024 * 1024) {
                throw IllegalStateException("Le fichier téléchargé est incomplet.")
            }

            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            progress(model.name, 100)
        } finally {
            connection.disconnect()
        }
    }

    private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
        var current = startUrl
        repeat(8) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Astra-Android/0.1.12")
            connection.setRequestProperty("Accept", "*/*")
            connection.connect()

            if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirection de téléchargement invalide.")
                connection.disconnect()
                current = URL(URL(current), location).toString()
            } else {
                return connection
            }
        }
        throw IllegalStateException("Trop de redirections pendant le téléchargement.")
    }
}
