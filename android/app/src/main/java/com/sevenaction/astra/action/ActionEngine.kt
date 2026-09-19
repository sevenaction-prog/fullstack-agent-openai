package com.sevenaction.astra.action

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

class ActionEngine(private val context: Context) {

    sealed interface Result {
        data object NotHandled : Result
        data class Reply(val message: String) : Result
        data class NeedMore(val message: String) : Result
        data class Launch(val intent: Intent, val confirmation: String) : Result
        data class RequestContactsPermission(val message: String) : Result
    }

    private sealed interface PendingAction {
        data class MessageBody(val recipient: String) : PendingAction
        data class MessageContactsPermission(val recipient: String, val body: String) : PendingAction
    }

    private var pending: PendingAction? = null
    private var lastRepeatableCommand: String? = null

    fun cancelPending() {
        pending = null
    }

    fun resumeAfterContactsPermission(): Result {
        val action = pending
        if (action !is PendingAction.MessageContactsPermission) return Result.NotHandled
        pending = null
        return prepareMessage(action.recipient, action.body)
    }

    fun handle(raw: String): Result {
        val text = raw.trim()
        if (text.isBlank()) return Result.NotHandled

        when (val action = pending) {
            is PendingAction.MessageBody -> {
                pending = null
                return prepareMessage(action.recipient, text)
            }
            is PendingAction.MessageContactsPermission -> {
                return Result.RequestContactsPermission("J’ai besoin de l’accès aux contacts pour retrouver ${action.recipient}.")
            }
            null -> Unit
        }

        val normalized = normalize(text)
        if (normalized in setOf("refais le", "refais ca", "repeat that", "do it again")) {
            val previous = lastRepeatableCommand
                ?: return Result.Reply("Je n’ai pas encore d’action à répéter.")
            return handleFresh(previous, remember = false)
        }

        return handleFresh(text, remember = true)
    }

    private fun handleFresh(text: String, remember: Boolean): Result {
        parseMessage(text)?.let { (recipient, body) ->
            if (body.isNullOrBlank()) {
                pending = PendingAction.MessageBody(recipient)
                return Result.NeedMore("Que veux-tu envoyer à $recipient ?")
            }
            val result = prepareMessage(recipient, body)
            if (remember && result is Result.Launch) lastRepeatableCommand = text
            return result
        }

        parseSearch(text)?.let { query ->
            val encoded = Uri.encode(query)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
            if (remember) lastRepeatableCommand = text
            return Result.Launch(intent, "J’ouvre la recherche pour « $query ».")
        }

        parseOpenApp(text)?.let { appName ->
            val launchIntent = findLaunchIntent(appName)
                ?: return Result.Reply("Je ne trouve pas d’application installée appelée « $appName ».")
            if (remember) lastRepeatableCommand = text
            return Result.Launch(launchIntent, "J’ouvre $appName.")
        }

        return Result.NotHandled
    }

    private fun parseMessage(text: String): Pair<String, String?>? {
        val withBody = listOf(
            Regex("""^(?:envoie|envoyer|send)(?:\s+un)?\s+(?:message|sms)(?:\s+à|\s+to)\s+(.+?)\s+(?:disant|pour\s+dire|saying)\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(?:écris|ecris|write)(?:\s+un\s+message)?(?:\s+à|\s+to)\s+(.+?)\s*[:,-]\s*(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(?:envoie|envoyer|send)(?:\s+un)?\s+(?:message|sms)(?:\s+à|\s+to)\s+(.+?)\s*[:,-]\s*(.+)$""", RegexOption.IGNORE_CASE)
        )
        for (regex in withBody) {
            val match = regex.find(text) ?: continue
            return match.groupValues[1].trim() to match.groupValues[2].trim()
        }

        val recipientOnly = Regex(
            """^(?:envoie|envoyer|send)(?:\s+un)?\s+(?:message|sms)(?:\s+à|\s+to)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text) ?: return null

        return recipientOnly.groupValues[1].trim() to null
    }

    private fun parseSearch(text: String): String? {
        val regex = Regex(
            """^(?:cherche|recherche|search|look\s+up)(?:\s+(?:sur\s+le\s+web|sur\s+internet|on\s+the\s+web|online))?\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseOpenApp(text: String): String? {
        val regex = Regex(
            """^(?:ouvre|lance|démarre|demarre|open|launch|start)\s+(?:l['’]application\s+|l['’]app\s+|app\s+|l['’]|le\s+|la\s+)?(.+)$""",
            RegexOption.IGNORE_CASE
        )
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun prepareMessage(recipient: String, body: String): Result {
        val directNumber = sanitizePhone(recipient)
        if (directNumber != null) {
            return smsIntent(directNumber, body, recipient)
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pending = PendingAction.MessageContactsPermission(recipient, body)
            return Result.RequestContactsPermission("Autorise l’accès aux contacts pour que je retrouve $recipient.")
        }

        return when (val contact = findContactNumber(recipient)) {
            is ContactResult.Found -> smsIntent(contact.number, body, contact.displayName)
            ContactResult.NotFound -> Result.Reply("Je ne trouve pas « $recipient » dans tes contacts. Donne-moi son numéro ou un nom plus précis.")
            ContactResult.Ambiguous -> Result.Reply("Je trouve plusieurs contacts correspondant à « $recipient ». Précise le nom complet.")
        }
    }

    private fun smsIntent(number: String, body: String, displayName: String): Result {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", body)
        }
        return Result.Launch(
            intent,
            "Message préparé pour $displayName. Vérifie-le puis appuie sur Envoyer dans ta messagerie."
        )
    }

    private fun findLaunchIntent(requestedName: String): Intent? {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
        val target = normalize(requestedName)

        val candidates = apps.mapNotNull { info ->
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            if (label.isBlank()) null else Triple(label, normalize(label), info.activityInfo.packageName)
        }

        val exact = candidates.firstOrNull { (_, normalized, _) -> normalized == target }
        val contains = candidates.firstOrNull { (_, normalized, _) ->
            normalized.contains(target) || target.contains(normalized)
        }
        val chosen = exact ?: contains ?: return null
        return pm.getLaunchIntentForPackage(chosen.third)
    }

    private sealed interface ContactResult {
        data class Found(val displayName: String, val number: String) : ContactResult
        data object NotFound : ContactResult
        data object Ambiguous : ContactResult
    }

    private fun findContactNumber(requestedName: String): ContactResult {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$requestedName%"),
            null
        ) ?: return ContactResult.NotFound

        data class Match(val name: String, val number: String, val type: Int)
        val matches = mutableListOf<Match>()
        cursor.use {
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            while (it.moveToNext() && matches.size < 20) {
                matches += Match(
                    it.getString(nameIndex).orEmpty(),
                    it.getString(numberIndex).orEmpty(),
                    it.getInt(typeIndex)
                )
            }
        }

        if (matches.isEmpty()) return ContactResult.NotFound
        val target = normalize(requestedName)
        val exact = matches.filter { normalize(it.name) == target }
        val pool = if (exact.isNotEmpty()) exact else matches

        val distinctNames = pool.map { normalize(it.name) }.distinct()
        if (exact.isEmpty() && distinctNames.size > 1) return ContactResult.Ambiguous

        val best = pool.sortedByDescending {
            if (it.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) 1 else 0
        }.firstOrNull() ?: return ContactResult.NotFound

        return ContactResult.Found(best.name, best.number)
    }

    private fun sanitizePhone(value: String): String? {
        val trimmed = value.trim()
        if (!trimmed.matches(Regex("""[+()\d .-]{6,}"""))) return null
        val sanitized = trimmed.replace(Regex("""[^+\d]"""), "")
        return sanitized.takeIf { it.count(Char::isDigit) >= 6 }
    }

    private fun normalize(value: String): String {
        val noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9+]+"""), " ")
            .trim()
    }
}
