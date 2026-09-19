package com.sevenaction.astra.ai

import android.content.Context
import android.os.SystemClock
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class LocalAiEngine(private val context: Context) {
    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private var ready = false

    val modelFile: File
        get() = File(context.filesDir, "models/qwen3-0.6b-q4_0.gguf")

    suspend fun initialize(
        systemPrompt: String,
        onProgress: (String) -> Unit = {}
    ) {
        if (ready) return

        onProgress("Initialisation du moteur local…")
        require(modelFile.exists()) { "Le modèle local n'est pas encore installé." }
        require(modelFile.length() > 100L * 1024L * 1024L) {
            "Le fichier du modèle local semble incomplet. Relance l'installation des modèles."
        }

        val initialState = engine.state.first { state ->
            state is InferenceEngine.State.Initialized || state is InferenceEngine.State.Error
        }
        if (initialState is InferenceEngine.State.Error) throw initialState.exception

        val started = SystemClock.elapsedRealtime()
        onProgress("Ouverture du modèle local (429 Mo)…")

        try {
            coroutineScope {
                val monitor = launch(Dispatchers.Default) {
                    while (isActive) {
                        val elapsed = (SystemClock.elapsedRealtime() - started) / 1000L
                        val nativeLine = runCatching {
                            engine.lastNativeLog()
                                .lineSequence()
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .lastOrNull()
                                .orEmpty()
                        }.getOrDefault("")

                        val stateLabel = when (engine.state.value) {
                            is InferenceEngine.State.Initializing -> "Initialisation native"
                            is InferenceEngine.State.LoadingModel -> "Chargement des poids / contexte"
                            is InferenceEngine.State.ProcessingSystemPrompt -> "Préparation de l'assistant"
                            is InferenceEngine.State.ModelReady -> "Modèle prêt"
                            else -> "Préparation"
                        }
                        val detail = if (nativeLine.isBlank()) "" else "\n" + nativeLine.take(180)
                        onProgress(stateLabel + " — " + elapsed + " s" + detail)
                        delay(1000)
                    }
                }

                try {
                    engine.loadModel(modelFile.absolutePath)
                } finally {
                    monitor.cancel()
                }
            }
        } catch (e: Exception) {
            val nativeLog = runCatching { engine.lastNativeLog().takeLast(8000) }.getOrDefault("")
            throw RuntimeException(
                "Échec natif du chargement du modèle.\n\n" +
                    (if (nativeLog.isBlank()) "Aucun journal natif disponible." else nativeLog),
                e
            )
        }

        onProgress("Modèle chargé. Préparation d'Astra…")
        engine.setSystemPrompt(systemPrompt)
        ready = true
        onProgress("Prête — 100% local")
    }

    fun respond(prompt: String): Flow<String> {
        check(ready) { "Le modèle local n'est pas prêt." }
        val nonThinkingPrompt = buildString {
            append(prompt.trim())
            append("\n\n/no_think")
        }
        return engine.sendUserPrompt(nonThinkingPrompt, predictLength = 500)
    }

    fun isReady() = ready
}
