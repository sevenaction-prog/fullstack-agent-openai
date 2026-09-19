package com.sevenaction.astra.memory

class SessionMemory(
    private val maxTurns: Int = 8,
    private val maxChars: Int = 1600
) {
    private data class Turn(val role: String, val text: String)

    private val turns = ArrayDeque<Turn>()

    fun addUser(text: String) = add("Utilisateur", text)
    fun addAssistant(text: String) = add("Astra", text)

    fun clear() {
        turns.clear()
    }

    fun context(): String {
        if (turns.isEmpty()) return ""
        val selected = mutableListOf<String>()
        var total = 0
        for (turn in turns.toList().asReversed()) {
            val line = "${turn.role}: ${turn.text.trim()}"
            if (selected.isNotEmpty() && total + line.length > maxChars) break
            selected += line
            total += line.length
        }
        return selected.asReversed().joinToString("\n")
    }

    private fun add(role: String, text: String) {
        val cleaned = text.trim().take(600)
        if (cleaned.isBlank()) return
        turns.addLast(Turn(role, cleaned))
        while (turns.size > maxTurns) turns.removeFirst()
    }
}
