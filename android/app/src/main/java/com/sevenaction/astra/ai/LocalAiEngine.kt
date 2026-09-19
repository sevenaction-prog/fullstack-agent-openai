package com.sevenaction.astra.ai

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.Flow
import java.io.File

class LocalAiEngine(private val context: Context) {
    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private var ready = false

    val modelFile: File
        get() = File(context.filesDir, "models/qwen3.5-2b-q4_k_m.gguf")

    suspend fun initialize(systemPrompt: String) {
        if (ready) return
        require(modelFile.exists()) { "Le modèle local n'est pas encore installé." }
        engine.loadModel(modelFile.absolutePath)
        engine.setSystemPrompt(systemPrompt)
        ready = true
    }

    fun respond(prompt: String): Flow<String> {
        check(ready) { "Le modèle local n'est pas prêt." }
        return engine.sendUserPrompt(prompt, predictLength = 700)
    }

    fun isReady() = ready
}
