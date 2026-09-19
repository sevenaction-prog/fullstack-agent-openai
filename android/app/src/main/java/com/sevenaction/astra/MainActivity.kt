package com.sevenaction.astra

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sevenaction.astra.ai.LocalAiEngine
import com.sevenaction.astra.meeting.MeetingRecorderService
import com.sevenaction.astra.meeting.MeetingState
import com.sevenaction.astra.meeting.MeetingTranscriber
import com.sevenaction.astra.memory.MemoryStore
import com.sevenaction.astra.setup.ModelInstaller
import com.sevenaction.astra.speech.SpeechController
import com.sevenaction.astra.ui.CoreView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var conversationText: TextView
    private lateinit var promptInput: EditText
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button
    private lateinit var meetingButton: Button
    private lateinit var memoryButton: Button
    private lateinit var coreView: CoreView

    private lateinit var memory: MemoryStore
    private lateinit var installer: ModelInstaller
    private lateinit var ai: LocalAiEngine
    private lateinit var speech: SpeechController
    private var busy = false
    private var pendingMicAction: (() -> Unit)? = null
    private var meetingDialogVisible = false

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val mic = granted[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val action = pendingMicAction
        pendingMicAction = null
        if (mic) action?.invoke()
        else toast("Le microphone est nécessaire pour la voix et les réunions.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        memory = MemoryStore(this)
        installer = ModelInstaller(this)
        ai = LocalAiEngine(this)

        speech = SpeechController(
            this,
            onListening = { listening ->
                runOnUiThread {
                    coreView.setState(if (listening) CoreView.State.LISTENING else CoreView.State.IDLE)
                    statusText.text = if (listening) "Je t'écoute…" else "Prête"
                }
            },
            onText = { text -> runOnUiThread { promptInput.setText(text); submit(text) } },
            onError = { message -> runOnUiThread { toast(message) } }
        )

        sendButton.setOnClickListener { submit(promptInput.text.toString()) }
        voiceButton.setOnClickListener { ensureMic { speech.listen() } }
        meetingButton.setOnClickListener { toggleMeeting() }
        memoryButton.setOnClickListener { showMemory() }
        promptInput.setOnEditorActionListener { _, _, _ -> submit(promptInput.text.toString()); true }

        updateMeetingUi()
        if (installer.allInstalled()) initializeAi() else showFirstRunSetup()
    }

    private fun bindViews() {
        statusText = findViewById(R.id.statusText)
        conversationText = findViewById(R.id.conversationText)
        promptInput = findViewById(R.id.promptInput)
        sendButton = findViewById(R.id.sendButton)
        voiceButton = findViewById(R.id.voiceButton)
        meetingButton = findViewById(R.id.meetingButton)
        memoryButton = findViewById(R.id.memoryButton)
        coreView = findViewById(R.id.coreView)
    }

    private fun showFirstRunSetup() {
        AlertDialog.Builder(this)
            .setTitle("Installer l'intelligence locale")
            .setMessage("Astra fonctionne sans abonnement et sans compte. Le premier lancement télécharge environ 500 Mo de modèles gratuits. Ensuite, le cœur de l'assistant fonctionne localement.")
            .setCancelable(false)
            .setPositiveButton("Installer") { _, _ -> installModels() }
            .setNegativeButton("Plus tard") { _, _ -> statusText.text = "Modèles locaux non installés" }
            .show()
    }

    private fun installModels() {
        setBusy(true)
        coreView.setState(CoreView.State.THINKING)
        lifecycleScope.launch {
            try {
                installer.installAll { name, percent -> runOnUiThread { statusText.text = name + " — " + percent + "%" } }
                toast("Installation locale terminée.")
                initializeAi()
            } catch (e: Exception) {
                statusText.text = "Installation interrompue"
                toast(e.message ?: "Erreur de téléchargement")
                setBusy(false)
                coreView.setState(CoreView.State.IDLE)
            }
        }
    }

    private fun initializeAi() {
        setBusy(true)
        statusText.text = "Chargement de l'IA locale…"
        coreView.setState(CoreView.State.THINKING)
        lifecycleScope.launch {
            try {
                ai.initialize(
                    "Tu es Astra, un assistant personnel local, chaleureux, concis et fiable. " +
                    "Tu réponds en français par défaut. Tu aides à organiser, réfléchir, résumer et te souvenir. " +
                    "Ne prétends jamais avoir fait une action que tu n'as pas faite."
                ) { progress ->
                    runOnUiThread {
                        statusText.text = progress.lineSequence().firstOrNull() ?: progress
                        conversationText.text = progress
                    }
                }
                statusText.text = "Prête — 100% local"
                conversationText.text = "Astra est prête. Écris ou utilise le micro."
            } catch (e: Exception) {
                statusText.text = "IA locale indisponible"
                val details = e::class.java.simpleName + ": " + (e.message ?: "erreur inconnue")
                conversationText.text = "Diagnostic IA locale\n\n" + details +
                    "\n\nModèle: " + ai.modelFile.name +
                    "\nTaille: " + (ai.modelFile.length() / (1024L * 1024L)) + " Mo"
                toast(details)
            } finally {
                coreView.setState(CoreView.State.IDLE)
                setBusy(false)
            }
        }
    }

    private fun submit(raw: String) {
        val text = raw.trim()
        if (text.isBlank() || busy) return
        if (!ai.isReady()) { toast("L'intelligence locale n'est pas encore prête."); return }

        promptInput.text.clear()
        maybeRemember(text)
        appendConversation("\n\nTOI\n" + text + "\n\nASTRA\n")
        coreView.setState(CoreView.State.THINKING)
        statusText.text = "Réflexion locale…"
        setBusy(true)

        val memories = memory.context()
        val prompt = buildString {
            if (memories.isNotBlank()) {
                append("Mémoire personnelle disponible :\n")
                append(memories)
                append("\n\n")
            }
            append("Demande actuelle :\n")
            append(text)
        }

        lifecycleScope.launch {
            val response = StringBuilder()
            try {
                ai.respond(prompt).collect { token ->
                    response.append(token)
                    runOnUiThread { appendConversation(token) }
                }
                coreView.setState(CoreView.State.SPEAKING)
                statusText.text = "Réponse"
                speech.speak(response.toString())
                delay(700)
            } catch (e: Exception) {
                appendConversation("\n[Erreur locale : " + (e.message ?: "inconnue") + "]")
            } finally {
                coreView.setState(CoreView.State.IDLE)
                statusText.text = "Prête"
                setBusy(false)
            }
        }
    }

    private fun maybeRemember(text: String) {
        val normalized = text.lowercase()
        val prefixes = listOf("souviens-toi que ", "souviens toi que ", "mémorise que ", "memorise que ")
        val prefix = prefixes.firstOrNull { normalized.startsWith(it) } ?: return
        val fact = text.substring(prefix.length).trim()
        if (fact.isNotBlank()) { memory.add(fact); toast("Ajouté à ma mémoire locale.") }
    }

    private fun showMemory() {
        val items = memory.list()
        val lines = if (items.isEmpty()) "Aucun souvenir enregistré."
        else items.take(50).joinToString("\n\n") { "• " + it.text }
        AlertDialog.Builder(this)
            .setTitle("Mémoire d'Astra")
            .setMessage(lines)
            .setPositiveButton("Fermer", null)
            .setNeutralButton("Ajouter") { _, _ -> promptMemoryAdd() }
            .setNegativeButton("Tout effacer") { _, _ ->
                items.forEach { memory.delete(it.id) }
                toast("Mémoire locale effacée.")
            }
            .show()
    }

    private fun promptMemoryAdd() {
        val input = EditText(this).apply { hint = "Information à retenir" }
        AlertDialog.Builder(this)
            .setTitle("Ajouter un souvenir")
            .setView(input)
            .setPositiveButton("Mémoriser") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) memory.add(text)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun toggleMeeting() {
        ensureMic {
            val prefs = getSharedPreferences(MeetingState.PREFS, MODE_PRIVATE)
            val active = prefs.getBoolean(MeetingState.KEY_ACTIVE, false)
            if (!active) {
                AlertDialog.Builder(this)
                    .setTitle("Démarrer le mode Réunion ?")
                    .setMessage("L'enregistrement continuera écran éteint. Assure-toi d'avoir l'accord des participants lorsque cela est requis.")
                    .setPositiveButton("Enregistrer") { _, _ ->
                        val intent = Intent(this, MeetingRecorderService::class.java).setAction(MeetingState.ACTION_START)
                        ContextCompat.startForegroundService(this, intent)
                        coreView.setState(CoreView.State.RECORDING)
                        meetingButton.text = "■ Terminer"
                        statusText.text = "Réunion — enregistrement local"
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            } else stopMeetingAndOfferAnalysis()
        }
    }

    private fun stopMeetingAndOfferAnalysis() {
        startService(Intent(this, MeetingRecorderService::class.java).setAction(MeetingState.ACTION_STOP))
        coreView.setState(CoreView.State.IDLE)
        meetingButton.text = "● Réunion"
        statusText.text = "Réunion terminée"
        lifecycleScope.launch {
            delay(1800)
            offerPendingMeetingAnalysis()
        }
    }

    private fun offerPendingMeetingAnalysis() {
        if (meetingDialogVisible || busy) return
        val prefs = getSharedPreferences(MeetingState.PREFS, MODE_PRIVATE)
        val active = prefs.getBoolean(MeetingState.KEY_ACTIVE, false)
        val pending = prefs.getBoolean(MeetingState.KEY_ANALYSIS_PENDING, false)
        val path = prefs.getString(MeetingState.KEY_FOLDER, null)
        if (active || !pending || path.isNullOrBlank()) return

        meetingDialogVisible = true
        AlertDialog.Builder(this)
            .setTitle("Analyser la réunion ?")
            .setMessage("Astra peut transcrire l'audio localement puis préparer un résumé, les décisions et les actions.")
            .setPositiveButton("Transcrire") { _, _ ->
                prefs.edit().putBoolean(MeetingState.KEY_ANALYSIS_PENDING, false).apply()
                meetingDialogVisible = false
                transcribeLastMeeting()
            }
            .setNegativeButton("Plus tard") { _, _ ->
                meetingDialogVisible = false
                statusText.text = "Réunion sauvegardée — analyse en attente"
            }
            .setOnCancelListener { meetingDialogVisible = false }
            .show()
    }

    private fun transcribeLastMeeting() {
        val path = getSharedPreferences(MeetingState.PREFS, MODE_PRIVATE).getString(MeetingState.KEY_FOLDER, null)
        if (path == null) { toast("Réunion introuvable."); return }
        setBusy(true)
        coreView.setState(CoreView.State.THINKING)
        lifecycleScope.launch {
            try {
                statusText.text = "Transcription locale…"
                val transcript = MeetingTranscriber(this@MainActivity).transcribe(File(path)) { done, total ->
                    runOnUiThread { statusText.text = "Transcription " + done + "/" + total }
                }
                appendConversation("\n\nTRANSCRIPTION TERMINÉE\n" + transcript.takeLast(5000))
                summarizeMeeting(transcript, File(path))
            } catch (e: Exception) {
                toast(e.message ?: "Erreur de transcription")
            } finally {
                coreView.setState(CoreView.State.IDLE)
                statusText.text = "Prête"
                setBusy(false)
            }
        }
    }

    private suspend fun summarizeMeeting(transcript: String, folder: File) {
        if (!ai.isReady()) return
        statusText.text = "Résumé de la réunion…"
        val chunks = transcript.chunked(12000).take(20)
        val partials = mutableListOf<String>()
        for ((index, chunk) in chunks.withIndex()) {
            statusText.text = "Résumé " + (index + 1) + "/" + chunks.size
            val out = StringBuilder()
            ai.respond("Résume ce morceau de réunion en français. Garde décisions, actions, responsables, dates, questions ouvertes et faits importants.\n\n" + chunk)
                .collect { out.append(it) }
            partials += out.toString()
        }
        val final = StringBuilder()
        ai.respond(
            "Transforme ces résumés partiels en compte-rendu final structuré : Résumé, Décisions, Actions avec responsables si connus, Dates/échéances, Questions ouvertes, Points à mémoriser.\n\n" +
                partials.joinToString("\n\n---\n\n").take(24000)
        ).collect { final.append(it) }
        File(folder, "summary.md").writeText(final.toString())
        appendConversation("\n\nCOMPTE-RENDU\n" + final)
        speech.speak("Le compte-rendu de la réunion est prêt.")
    }

    private fun ensureMic(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMicAction = action
            permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun updateMeetingUi() {
        val active = getSharedPreferences(MeetingState.PREFS, MODE_PRIVATE).getBoolean(MeetingState.KEY_ACTIVE, false)
        if (active) {
            coreView.setState(CoreView.State.RECORDING)
            meetingButton.text = "■ Terminer"
            statusText.text = "Réunion — enregistrement local"
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        sendButton.isEnabled = !value
        voiceButton.isEnabled = !value
    }

    private fun appendConversation(text: String) {
        conversationText.append(text)
        findViewById<ScrollView>(R.id.conversationScroll).post {
            findViewById<ScrollView>(R.id.conversationScroll).fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        if (::coreView.isInitialized) {
            updateMeetingUi()
            lifecycleScope.launch {
                delay(350)
                offerPendingMeetingAnalysis()
            }
        }
    }

    override fun onDestroy() {
        if (::speech.isInitialized) speech.shutdown()
        super.onDestroy()
    }
}
