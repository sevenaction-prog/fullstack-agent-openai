package com.sevenaction.astra.meeting

import android.content.Context
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MeetingTranscriber(private val context: Context) {
    suspend fun transcribe(folder: File, onProgress: (Int, Int) -> Unit): String = withContext(Dispatchers.IO) {
        val model = File(context.filesDir, "models/ggml-base-q5_1.bin")
        require(model.exists()) { "Modèle Whisper non installé." }
        val segments = folder.listFiles { f -> f.extension == "pcm" }?.sortedBy { it.name } ?: emptyList()
        require(segments.isNotEmpty()) { "Aucun segment audio." }

        val whisper = WhisperContext.createContextFromFile(model.absolutePath)
        val transcript = StringBuilder()
        try {
            segments.forEachIndexed { index, file ->
                val audio = pcm16ToFloat(file.readBytes())
                val text = whisper.transcribeData(audio, printTimestamp = true)
                transcript.append("\n## Segment ").append(index + 1).append("\n").append(text).append("\n")
                File(folder, "transcript.partial.md").writeText(transcript.toString())
                onProgress(index + 1, segments.size)
            }
        } finally {
            whisper.release()
        }
        File(folder, "transcript.md").writeText(transcript.toString())
        transcript.toString()
    }

    private fun pcm16ToFloat(bytes: ByteArray): FloatArray {
        val shorts = bytes.size / 2
        val out = FloatArray(shorts)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shorts) out[i] = bb.short / 32768f
        return out
    }
}
