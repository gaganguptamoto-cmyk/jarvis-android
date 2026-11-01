package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper

object CommandRouter {

    private class Pending(val run: () -> String, val at: Long)

    private var pending: Pending? = null
    private const val CONFIRM_MS = 30_000L

    private const val NEED_A11Y =
        "Accessibility अनुमति चालू नहीं है। JARVIS Settings में Open accessibility settings दबाओ।"
    private const val NO_SCREEN = "कोई दूसरे app की स्क्रीन नहीं मिली। पहले कोई app खोलो।"

    var notifyOverlay: ((String) -> Unit)? = null
    private val mainH = Handler(Looper.getMainLooper())
    private var awaitQuery = 0L

    private val YES = setOf(
        "haan", "han", "ha", "yes", "y", "ok", "confirm", "kar do", "kardo",
        "हाँ", "हां", "हा", "कर दो", "ठीक है", "ओके",
    )
    private val NO = setOf(
        "nahi", "nahin", "no", "cancel", "ruko",
        "नहीं", "नही", "रुको", "कैंसल",
    )
    private val READ = setOf(
        "read screen", "screen padho", "screen padh", "screen pe kya hai",
        "स्क्रीन पढ़ो", "स्क्रीन पर क्या है",
    )
    private val DOWN = setOf("down", "neeche", "niche", "नीचे")
    private val UP = setOf("up", "upar", "ऊपर")

    private val SENSITIVE = listOf(
        "send", "pay", "buy", "order", "delete", "remove", "transfer",
        "purchase", "uninstall", "call", "post", "submit",
        "भेज", "खरीद", "हटा", "डिलीट", "ऑर्डर", "सेंड", "पेमेंट", "कॉल",
    )

    private val FILLER = setOf(
        "jarvis", "please", "plz", "mujhe", "ab", "ko", "par", "pe", "per", "the",
        "go", "take", "me", "jao", "jaao", "ja", "chalo", "karo", "kar", "do", "de",
        "lo", "le", "bhai", "yaar", "screen",
        "पर", "पे", "को", "जरा", "ज़रा", "अब", "मुझे", "दो", "जाओ", "जा", "चलो",
        "करो", "कर", "ले", "चल", "जाइए", "चलिए", "भाई", "स्क्रीन",
    )
    private val HOME_CORE = setOf("home", "होम", "ghar", "घर")
    private val BACK_CORE = setOf("back", "peeche", "पीछे", "wapas", "वापस")
    private val RECENT_CORE = setOf("recents", "recent", "रीसेंट्स", "रीसेंट")

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
    private val TYPE_SUFFIX = Regex("^(.+?)\\s+(?:type karo|type kar do|type kar|likho|likh do|टाइप करो|लिखो)$", RegexOption.IGNORE_CASE)
    private val TYPE_PREFIX = Regex("^(?:type|likho|likh|लिखो|लिख)\\s+(.+)$", RegexOption.IGNORE_CASE)

    private val SEARCH_A = Regex(
        "^(?:search|dhundo|dhoondo|khojo|सर्च|खोजो|ढूंढो|ढूँढो)\\s+(?:for\\s+)?(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val SEARCH_B = Regex(
        "^(.+?)\\s+(?:search karo|search kar do|search kar|search laga do|search lagao|सर्च करो|सर्च कर)$",
        RegexOption.IGNORE_CASE,
    )
    private val APP_PREP = Regex("^\\S+\\s+(?:me|mein|pe|par|per|में|पर|पे)\\s+", RegexOption.IGNORE_CASE)
    private val QUESTION = Regex("\\b(kya|kaise|kyun|kyu|what|how|why)\\b|क्या|कैसे|क्यों")
    private val VERBS = setOf("karo", "kar", "do", "करो", "कर", "दो", "kholo", "खोलो")
    private val BARE_SEARCH = setOf(
        "search", "search karo", "search kar", "search kar do", "search kholo",
        "सर्च", "सर्च करो", "सर्च कर", "सर्च खोलो", "खोजो", "खोजें", "dhundo",
    )
    private val CHAIN = Regex(
        "\\s+(?:aur|phir|fir|then|and then|and|और|फिर|उसके बाद|uske baad|uske bad)\\s+",
        RegexOption.IGNORE_CASE,
    )

    private fun core(t: String): String =
        t.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in FILLER }.joinToString(" ")

    private fun scrollWords(t: String): Int {
        val w = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (w.size > 3) return 0
        val sc = w.any { it == "scroll" || it == "स्क्रॉल" }
        if (!sc && w.size != 1) return 0
        if (w.any { it in DOWN }) return 1
        if (w.any { it in UP }) return 2
        return 0
    }

    private fun short(s: String) = s.trim().split(Regex("\\s+")).size <= 3

    private fun isSensitive(label: String): Boolean {
        val l = label.lowercase()
        return SENSITIVE.any { l.contains(it) }
    }

    private fun service() = JarvisAccessibilityService.instance

    private fun late(msg: String) {
        notifyOverlay?.invoke(msg)
    }

    private fun searchQuery(text: String): String? {
        if (QUESTION.containsMatchIn(text)) return null
        val m = SEARCH_B.matchEntire(text) ?: SEARCH_A.matchEntire(text) ?: return null
        val q = m.groupValues[1].trim().replace(APP_PREP, "").trim()
        if (q.isEmpty() || q.lowercase() in VERBS) return null
        return if (q.split(Regex("\\s+")).size > 8) null else q
    }

    private fun bareSearch(t: String) = t in BARE_SEARCH

    private fun startSearch(q: String): String {
        val s = service() ?: return NEED_A11Y
        if (!s.hasTarget()) return NO_SCREEN
        SearchFlow.run(q) { late(it) }
        return "ठीक है, “$q” ढूँढते हैं।"
    }

    private fun startBareSearch(): String {
        val s = service() ?: return NEED_A11Y
        if (!s.hasTarget()) return NO_SCREEN
        SearchFlow.openOnly { late(it) }
        awaitQuery = System.currentTimeMillis()
        return "क्या ढूँढूँ?"
    }

    private fun restNote(rest: String): String {
        val r = rest.trim()
        if (r.isEmpty()) return ""
        val cleaned = r.replace(Regex("^(?:aur|phir|and|then|और|फिर)\\s+", RegexOption.IGNORE_CASE), "").trim()
        if (bareSearch(cleaned.lowercase())) {
            mainH.postDelayed({ late(startBareSearch()) }, 2500)
            return " फिर सर्च खोलूँगा।"
        }
        val q = searchQuery(cleaned)
        if (q != null) {
            mainH.postDelayed({ SearchFlow.run(q) { late(it) } }, 2500)
            return " फिर “$q” भी देखता हूँ।"
        }
        return " बाकी काम ($r) अभी ख़ुद नहीं कर सकता।"
    }

    private fun isCommand(part: String): Boolean {
        val clean = part.trim().trimEnd('.', '!', '?', '।').trim()
        val t = clean.lowercase()
        if (t.isEmpty()) return false
        val c = core(t)
        return PhoneControl.matches(clean, t) || ChatCommands.matches(t, clean) || c in HOME_CORE || c in BACK_CORE || c in RECENT_CORE || t in READ ||
            scrollWords(t) != 0 || OPEN_PREFIX.matches(clean) || OPEN_SUFFIX.matches(clean) ||
            CLICK_PREFIX.matches(clean) || CLICK_SUFFIX.matches(clean) ||
            TYPE_PREFIX.matches(clean) || bareSearch(t) || searchQuery(clean) != null
    }

    /** Returns a reply if this was an automation command, or null to let the AI handle it. */
    fun handle(context: Context, raw: String): String? {
        Agent.intercept(raw)?.let { return it }
        if (System.currentTimeMillis() - chainAt < 15000L) {
            val w = raw.trim().lowercase().trimEnd('.', '!', '?', '।').split(Regex("\\s+"))
            if (w.size <= 2 && w.any { it in setOf("ruko", "stop", "bas", "रुको", "बस") }) {
                chainStop = true
                return "रुक गया।"
            }
        }
        val parts = raw.trim().split(CHAIN).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2 && parts.all { isCommand(it) }) {
            step(context.applicationContext, parts, 0, mutableListOf())
            return "ठीक है, ${parts.size} काम एक-एक करके करता हूँ।"
        }
        return handleSingle(context, raw) ?: Agent.maybeStart(context, raw)
    }

    @Volatile private var chainAt = 0L
    @Volatile private var chainStop = false

    private fun step(context: Context, parts: List<String>, i: Int, log: MutableList<String>) {
        chainAt = System.currentTimeMillis()
        if (i == 0) chainStop = false
        if (chainStop) return
        if (i >= parts.size) {
            late(log.joinToString(" "))
            return
        }
        val part = parts[i].trim()
        log.add(handleSingle(context, part) ?: "“$part” नहीं हो पाया।")
        if (pending != null) {
            late(log.joinToString(" ") + " बाकी काम रोक दिया।")
            return
        }
        val isOpen = OPEN_PREFIX.matches(part) || OPEN_SUFFIX.matches(part)
        mainH.postDelayed({ step(context, parts, i + 1, log) }, if (isOpen) 3000L else 1500L)
    }

    private fun handleSingle(context: Context, raw: String): String? {
        val clean = raw.trim().trimEnd('.', '!', '?', '।').trim()
        val t = clean.lowercase()
        if (t.isEmpty()) return null

        val p = pending
        if (p != null) {
            pending = null
            if (System.currentTimeMillis() - p.at <= CONFIRM_MS) {
                if (t in YES) return p.run()
                if (t in NO) return "चलो, छोड़ देते हैं।"
            }
        }

        if (awaitQuery != 0L) {
            val fresh = System.currentTimeMillis() - awaitQuery <= CONFIRM_MS
            awaitQuery = 0L
            if (fresh) {
                if (t in NO) return "चलो, छोड़ देते हैं।"
                return startSearch(clean)
            }
        }

        val c = core(t)
        Extras.handle(context, clean, t, { s -> late(s) })?.let { return it }
        PhoneControl.handle(context, clean, t)?.let { return it }
        ChatCommands.handle(clean, t)?.let { return it }
        if (c in HOME_CORE) return global(AccessibilityService.GLOBAL_ACTION_HOME, "होम पर आ गया।")
        if (c in BACK_CORE) return global(AccessibilityService.GLOBAL_ACTION_BACK, "पीछे आ गया।")
        if (c in RECENT_CORE) return global(AccessibilityService.GLOBAL_ACTION_RECENTS, "रीसेंट्स खोल दिए।")
        if (t in READ) return readScreen()
        val sw = scrollWords(t)
        if (sw == 1) return scroll(true)
        if (sw == 2) return scroll(false)

        if (bareSearch(t)) return startBareSearch()

        OPEN_PREFIX.matchEntire(clean)?.let { m ->
            return openApp(context, m.groupValues[1], "", true)
        }
        OPEN_SUFFIX.matchEntire(clean)?.let { m ->
            return openApp(context, m.groupValues[1], m.groupValues[2], false)
        }
        searchQuery(clean)?.let { q -> return startSearch(q) }
        CLICK_PREFIX.matchEntire(clean)?.let { m ->
            return clickLabel(m.groupValues[1].trim())
        }
        CLICK_SUFFIX.matchEntire(clean)?.let { m ->
            return clickLabel(m.groupValues[1].trim())
        }
        TYPE_SUFFIX.matchEntire(clean)?.let { m ->
            return typeInto(m.groupValues[1])
        }
        TYPE_PREFIX.matchEntire(clean)?.let { m ->
            return typeInto(m.groupValues[1])
        }
        return null
    }

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
        return if (s.scroll(forward)) "हो गया।" else "स्क्रॉल होने लायक कुछ नहीं मिला।"
    }

    private fun openApp(context: Context, app: String, rest: String, explicit: Boolean): String? {
        val name = app.trim().removePrefix("the ").removeSuffix(" app").trim()
        if (name.isEmpty() || !short(name)) return null
        return when (val r = AppLauncher.open(context, name)) {
            is AppLauncher.Result.Opened -> "लो, ${r.label} खुल गया।" + restNote(rest)
            is AppLauncher.Result.Many ->
                "कई apps मिले: ${r.labels.joinToString(", ")}। पूरा नाम बोलो।"
            AppLauncher.Result.NotFound ->
                if (explicit) "“$name” नाम का app नहीं मिला।" else null
            AppLauncher.Result.Failed -> "खोल नहीं पाया।"
        }
    }

    internal fun clickLabel(label: String): String? {
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
        return if (s.typeText(Hinglish.convert(text))) "लिख दिया।" else "यहाँ नहीं लिख पाया।"
    }
}
