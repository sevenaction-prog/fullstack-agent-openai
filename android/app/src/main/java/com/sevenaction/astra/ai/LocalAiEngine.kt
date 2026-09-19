package com.sevenaction.astra.ai

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

class LocalAiEngine(private val context: Context) {
    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private var ready = false

    val modelFile: File
        get() = File(context.filesDir, "models/qwen3-1.7b-q4_k_m.gguf")

    suspend fun initialize(systemPrompt: String) {
        if (ready) return
        require(modelFile.exists()) { "Le modèle local n'est pas encore installé." }
        require(modelFile.length() > 100L * 1024L * 1024L) {
            "Le fichier du modèle local semble incomplet. Relance l'installation des modèles."
        }

        val initialState = engine.state.first { state ->
            state is InferenceEngine.State.Initialized || state is InferenceEngine.State.Error
        }

        if (initialState is InferenceEngine.State.Error) {
            throw initialState.exception
        }

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
