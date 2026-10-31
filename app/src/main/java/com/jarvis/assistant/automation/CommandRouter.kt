package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context

object CommandRouter {

    private class Pending(val run: () -> String, val at: Long)

    private var pending: Pending? = null
    private const val CONFIRM_MS = 30_000L

    private const val NEED_A11Y =
        "Accessibility अनुमति चालू नहीं है। JARVIS Settings में Open accessibility settings दबाओ।"
    private const val NO_SCREEN = "कोई दूसरे app की स्क्रीन नहीं मिली। पहले कोई app खोलो।"

    private val YES = setOf(
        "haan", "han", "ha", "yes", "y", "ok", "confirm", "kar do", "kardo",
        "हाँ", "हां", "हा", "कर दो", "ठीक है", "ओके",
    )
    private val NO = setOf(
        "nahi", "nahin", "no", "cancel", "ruko",
        "नहीं", "नही", "रुको", "कैंसल",
    )
    private val HOME = setOf("home", "go home", "home jao", "ghar jao", "होम", "होम जाओ")
    private val BACK = setOf("back", "go back", "peeche jao", "wapas jao", "पीछे", "वापस जाओ")
    private val RECENTS = setOf("recents", "recent apps", "रीसेंट्स")
    private val READ = setOf(
        "read screen", "screen padho", "screen padh", "screen pe kya hai",
        "स्क्रीन पढ़ो", "स्क्रीन पर क्या है",
    )
    private val DOWN = setOf(
        "scroll down", "scroll neeche", "neeche", "neeche scroll karo",
        "नीचे", "नीचे स्क्रॉल करो",
    )
    private val UP = setOf(
        "scroll up", "scroll upar", "upar", "upar scroll karo",
        "ऊपर", "ऊपर स्क्रॉल करो",
    )

    private val SENSITIVE = listOf(
        "send", "pay", "buy", "order", "delete", "remove", "transfer",
        "purchase", "uninstall", "call", "post", "submit",
        "भेज", "खरीद", "हटा", "डिलीट", "ऑर्डर", "सेंड", "पेमेंट", "कॉल",
    )

    private val OPEN_PREFIX = Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val OPEN_SUFFIX = Regex(
        "^(.+?)\\s+(?:khol do|kholo|khol|chalu karo|chalu kar|chalao|खोल दो|खोलो|खोल|चालू करो|चालू कर|चलाओ)(?:\\s+(.*))?$",
        RegexOption.IGNORE_CASE,
    )
    private val CLICK_PREFIX = Regex("^(?:click|tap|press)\\s+(?:on\\s+)?(.+)$", RegexOption.IGNORE_CASE)
    private val CLICK_SUFFIX = Regex(
        "^(.+?)\\s+(?:dabao|daba do|daba|click karo|tap karo|दबाओ|दबा दो|दबा|क्लिक करो|टैप करो)$",
        RegexOption.IGNORE_CASE,
    )
    private val TYPE_PREFIX = Regex("^(?:type|likho|likh|लिखो|लिख)\\s+(.+)$", RegexOption.IGNORE_CASE)

    /** Returns a reply if this was an automation command, or null to let the AI handle it. */
    fun handle(context: Context, raw: String): String? {
        val clean = raw.trim().trimEnd('.', '!', '?', '।').trim()
        val t = clean.lowercase()
        if (t.isEmpty()) return null

        val p = pending
        if (p != null) {
            pending = null
            if (System.currentTimeMillis() - p.at <= CONFIRM_MS) {
                if (t in YES) return p.run()
                if (t in NO) return "ठीक है, रद्द कर दिया।"
            }
        }

        if (t in HOME) return global(AccessibilityService.GLOBAL_ACTION_HOME, "होम पर आ गया।")
        if (t in BACK) return global(AccessibilityService.GLOBAL_ACTION_BACK, "पीछे आ गया।")
        if (t in RECENTS) return global(AccessibilityService.GLOBAL_ACTION_RECENTS, "रीसेंट्स खोल दिए।")
        if (t in READ) return readScreen()
        if (t in DOWN) return scroll(true)
        if (t in UP) return scroll(false)

        OPEN_PREFIX.matchEntire(clean)?.let { m ->
            return openApp(context, m.groupValues[1], "", true)
        }
        OPEN_SUFFIX.matchEntire(clean)?.let { m ->
            return openApp(context, m.groupValues[1], m.groupValues[2], false)
        }
        CLICK_PREFIX.matchEntire(clean)?.let { m ->
            return clickLabel(m.groupValues[1].trim())
        }
        CLICK_SUFFIX.matchEntire(clean)?.let { m ->
            return clickLabel(m.groupValues[1].trim())
        }
        TYPE_PREFIX.matchEntire(clean)?.let { m ->
            return typeInto(m.groupValues[1])
        }
        return null
    }

    private fun short(s: String) = s.trim().split(Regex("\\s+")).size <= 3

    private fun isSensitive(label: String): Boolean {
        val l = label.lowercase()
        return SENSITIVE.any { l.contains(it) }
    }

    private fun service() = JarvisAccessibilityService.instance

    private fun global(action: Int, ok: String): String {
        val s = service() ?: return NEED_A11Y
        return if (s.go(action)) ok else "ये नहीं हो पाया।"
    }

    private fun readScreen(): String {
        val s = service() ?: return NEED_A11Y
        if (!s.hasTarget()) return NO_SCREEN
        val text = s.readScreen()
        return if (text.isEmpty()) "कुछ पढ़ने लायक नहीं मिला।" else "स्क्रीन पर: $text"
    }

    private fun scroll(forward: Boolean): String {
        val s = service() ?: return NEED_A11Y
        if (!s.hasTarget()) return NO_SCREEN
        return if (s.scroll(forward)) "स्क्रॉल कर दिया।" else "स्क्रॉल होने लायक कुछ नहीं मिला।"
    }

    private fun openApp(context: Context, app: String, rest: String, explicit: Boolean): String? {
        val name = app.trim().removePrefix("the ").removeSuffix(" app").trim()
        if (name.isEmpty() || !short(name)) return null
        return when (val r = AppLauncher.open(context, name)) {
            is AppLauncher.Result.Opened -> {
                val extra = if (rest.isBlank()) "" else
                    " बाकी काम (${rest.trim()}) अभी ख़ुद नहीं कर सकता, वो अगला हिस्सा है।"
                "${r.label} खोल दिया।$extra"
            }
            is AppLauncher.Result.Many ->
                "कई apps मिले: ${r.labels.joinToString(", ")}। पूरा नाम बोलो।"
            AppLauncher.Result.NotFound ->
                if (explicit) "“$name” नाम का app नहीं मिला।" else null
            AppLauncher.Result.Failed -> "खोल नहीं पाया।"
        }
    }

    private fun clickLabel(label: String): String? {
        if (label.isEmpty()) return null
        val s = service() ?: return if (short(label)) NEED_A11Y else null
        if (!s.hasTarget()) return if (short(label)) NO_SCREEN else null
        val node = s.findByText(label)
            ?: return if (short(label)) "“$label” स्क्रीन पर नहीं दिखा।" else null
        val shown = s.labelOf(node).ifBlank { label }
        val run = {
            val n = s.findByText(label)
            when {
                n == null -> "“$shown” अब स्क्रीन पर नहीं दिख रहा।"
                s.click(n) -> "“$shown” दबा दिया।"
                else -> "“$shown” दबा नहीं पाया।"
            }
        }
        if (isSensitive(shown) || isSensitive(label)) {
            pending = Pending(run, System.currentTimeMillis())
            return "“$shown” संवेदनशील लग रहा है। दबाऊँ? हाँ या नहीं बोलो।"
        }
        return run()
    }

    private fun typeInto(text: String): String? {
        val s = service() ?: return null
        if (!s.hasInputFocus()) return null
        return if (s.typeText(text)) "लिख दिया।" else "यहाँ नहीं लिख पाया।"
    }
}
