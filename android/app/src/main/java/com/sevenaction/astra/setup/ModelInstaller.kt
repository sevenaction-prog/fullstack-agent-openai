package com.sevenaction.astra.setup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelInstaller(private val context: Context) {
    data class Model(val name: String, val fileName: String, val url: String)

    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }

    val llm = Model(
        "Astra AI (Qwen3.5 2B Q4_K_M)",
        "qwen3.5-2b-q4_k_m.gguf",
        "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf?download=true"
    )
    val whisper = Model(
        "Whisper Base Q5_1",
        "ggml-base-q5_1.bin",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin?download=true"
    )

    fun file(model: Model) = File(modelDir, model.fileName)
    fun allInstalled() = file(llm).exists() && file(whisper).exists()

    suspend fun installAll(progress: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        download(llm, progress)
        download(whisper, progress)
    }

    private fun download(model: Model, progress: (String, Int) -> Unit) {
        val target = file(model)
        if (target.exists() && target.length() > 1024 * 1024) {
            progress(model.name, 100)
            return
        }
        val temp = File(target.absolutePath + ".part")
        val connection = URL(model.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "Astra-Android/0.1")
        connection.connect()
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
                    if (total > 0) progress(model.name, ((done * 100) / total).toInt().coerceIn(0, 99))
                }
            }
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        progress(model.name, 100)
        connection.disconnect()
    }
}
