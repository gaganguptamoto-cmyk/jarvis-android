package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.ai.GeminiProvider
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Agent {

    var trace: ((String) -> Unit)? = null
    var providerGetter: (() -> GeminiProvider?)? = null

    private const val MAX_STEPS = 12
    private const val MAX_EL = 70
    private const val CONFIRM_MS = 30_000L

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    @Volatile private var cancelled = false
    private var ctx: Context? = null
    private var goal = ""
    private var steps = 0
    private val log = mutableListOf<String>()
    private var lastKey = ""
    private var repeats = 0
    private var pending: Pending? = null
    private var askedGoal: String? = null
    private var askedAt = 0L

    private class Pending(val node: AccessibilityNodeInfo, val label: String, val at: Long)
    private class El(val node: AccessibilityNodeInfo, val kind: String, val label: String)
    private class Snap(val pkg: String, val els: List<El>)
    private class Reply(val json: JSONObject?, val error: String?)

    private val YES = setOf(
        "haan", "han", "ha", "yes", "y", "ok", "confirm", "kar do", "kardo",
        "हाँ", "हां", "हा", "कर दो", "ठीक है", "ओके",
    )
    private val NO = setOf(
        "nahi", "nahin", "no", "cancel", "ruko",
        "नहीं", "नही", "रुको", "कैंसल",
    )
    private val STOP = setOf(
        "stop", "ruko", "ruk jao", "bas", "bas karo", "band karo", "cancel",
        "रुको", "रुक जाओ", "बस", "बस करो", "बंद करो", "कैंसल",
    )
    private val SENSITIVE = listOf(
        "send", "pay", "buy", "order", "delete", "remove", "transfer", "purchase",
        "uninstall", "call", "post", "submit", "checkout", "confirm",
        "भेज", "खरीद", "हटा", "डिलीट", "ऑर्डर", "सेंड", "पेमेंट", "कॉल",
    )
    private val QUESTION = Regex(
        "\\b(kya|kaise|kyun|kyu|what|how|why|kaun|kab|kitna|kitne|kahan|who|when|where)\\b" +
            "|क्या|कैसे|क्यों|कौन|कब|कितना|कितने|कहाँ",
    )

    private const val SYSTEM =
        "You operate an Android phone for the user through the accessibility service. " +
            "Every turn you get the user's GOAL, the STEPS DONE SO FAR and the CURRENT SCREEN as numbered elements. " +
            "Reply with ONE JSON object and nothing else, with these keys: action, target, text, app, dir, say. " +
            "Allowed actions: open_app (set app), tap (set target), type (set text, optionally target of a text box), " +
            "scroll (dir is up or down), enter (press the keyboard search or enter key), back, home, wait, " +
            "done (goal finished), reply (answer a question or chat), ask (you need one missing detail). " +
            "Rules: do exactly one step per turn. Use only element numbers that appear on the current screen. " +
            "To start an app use open_app with its name, never hunt for icons. " +
            "Contacts and app names may be written in English on screen while the user speaks Hindi, so match by sound " +
            "(for example Mummy and मम्मी are the same). " +
            "Write any text to be typed in Roman script Hinglish or English, never Devanagari, unless the user asked for Devanagari. " +
            "To send a chat message: tap the message box, type the text, then tap the Send button, then use done. " +
            "To search: tap the search icon or box, type the query, then use enter or tap the search suggestion. " +
            "Only use done when the screen or your steps prove the goal is complete. Never claim something you did not do. " +
            "If you cannot make progress after a few tries, use done and honestly say it failed. " +
            "If the goal is only a question or chat, use reply with a short answer. " +
            "say is one short sentence in the same language the user used (Hindi in Devanagari, or English). " +
            "Never touch password fields and never do payments, purchases or deletions unless the goal clearly asks for it."

    // ---------- entry points ----------

    /** Handles confirm (haan/nahi), stop, and busy replies while an agent task is alive. */
    fun intercept(raw: String): String? {
        val t = raw.trim().lowercase().trimEnd('.', '!', '?', '।').trim()
        if (t.isEmpty()) return null
        val pd = pending
        if (pd != null) {
            pending = null
            if (System.currentTimeMillis() - pd.at <= CONFIRM_MS) {
                if (t in YES) {
                    resume(pd)
                    return "ठीक है, दबा रहा हूँ।"
                }
                if (t in NO) return "ठीक है, रद्द कर दिया।"
            }
        }
        if (running) {
            if (t in STOP) {
                cancelled = true
                running = false
                return "रुक गया।"
            }
            return "अभी एक काम चल रहा है। रोकना हो तो 'ruko' बोलो।"
        }
        return null
    }

    /** Called when the router did not understand the text. Returns a reply if the agent took over. */
    fun maybeStart(context: Context, raw: String): String? {
        if (running) return null
        val getter = providerGetter ?: return null
        if (getter() == null) return null
        if (JarvisAccessibilityService.instance == null) return null
        val text = raw.trim()
        if (text.length < 3) return null
        if (QUESTION.containsMatchIn(text.lowercase())) return null
        if (text.split(Regex("\\s+")).size > 25) return null

        val now = System.currentTimeMillis()
        val prev = askedGoal
        goal = if (prev != null && now - askedAt < 90_000L) {
            prev + "\nUser's answer to your question: " + text
        } else {
            text
        }
        askedGoal = null
        ctx = context.applicationContext
        log.clear()
        steps = 0
        lastKey = ""
        repeats = 0
        cancelled = false
        pending = null
        running = true
        val p = getter()
        Thread { loop(p) }.start()
        return "ठीक है, कोशिश करता हूँ।"
    }

    private fun resume(pd: Pending) {
        running = true
        cancelled = false
        Thread {
            val sv = JarvisAccessibilityService.instance
            val ok = onMain { sv != null && pd.node.refresh() && sv.click(pd.node) } ?: false
            note(if (ok) "user confirmed, tapped '${pd.label}'" else "user confirmed but tap on '${pd.label}' failed")
            nap(1500)
            loop(providerGetter?.invoke())
        }.start()
    }

    // ---------- main loop ----------

    private fun loop(p: GeminiProvider?) {
        val c = ctx
        if (p == null || c == null) {
            finish("Gemini तैयार नहीं है।")
            return
        }
        try {
            loop@ while (!cancelled) {
                if (steps >= MAX_STEPS) {
                    finish("$MAX_STEPS step में काम पूरा नहीं हुआ, यहीं रुक गया।")
                    return
                }
                val sv = JarvisAccessibilityService.instance
                if (sv == null) {
                    finish("Accessibility बंद हो गई।")
                    return
                }
                val snap = onMain { snapshot(sv) } ?: Snap("", emptyList())
                val user = "GOAL: " + goal +
                    "\n\nSTEPS DONE SO FAR:\n" + (if (log.isEmpty()) "(none)" else log.joinToString("\n")) +
                    "\n\nCURRENT SCREEN:\n" + screenText(snap)

                val r = callGemini(p, user)
                if (cancelled) return
                val j = r.json
                if (j == null) {
                    finish(r.error ?: "Gemini से जवाब नहीं मिला।")
                    return
                }
                steps++
                val action = j.optString("action").trim().lowercase()
                val msg = j.optString("say").trim()
                val target = j.optInt("target", -1)
                val text = j.optString("text")
                val app = j.optString("app")
                val dir = j.optString("dir").trim().lowercase()
                tr(
                    "step $steps: $action" +
                        (if (target >= 0) " #$target" else "") +
                        (if (text.isNotEmpty()) " \"$text\"" else "") +
                        (if (app.isNotEmpty()) " $app" else ""),
                )

                val key = "$action|$target|$text|$app|$dir"
                if (key == lastKey) repeats++ else {
                    lastKey = key
                    repeats = 1
                }
                val limit = if (action == "scroll" || action == "wait") 5 else 3
                if (repeats >= limit) {
                    finish("एक ही काम बार-बार हो रहा था, इसलिए रुक गया।")
                    return
                }

                when (action) {
                    "done", "reply" -> {
                        finish(msg.ifBlank { "हो गया।" })
                        return
                    }
                    "ask" -> {
                        askedGoal = goal
                        askedAt = System.currentTimeMillis()
                        finish(msg.ifBlank { "कुछ और बताओ।" })
                        return
                    }
                    "open_app" -> {
                        val res = onMain { AppLauncher.open(c, app) }
                        when (res) {
                            is AppLauncher.Result.Opened -> note("opened ${res.label}")
                            is AppLauncher.Result.Many ->
                                note("open_app '$app' failed, several matches: ${res.labels.joinToString(", ")}")
                            else -> note("open_app '$app' failed, app not found")
                        }
                        nap(3000)
                    }
                    "tap" -> {
                        val el = snap.els.getOrNull(target)
                        if (el == null) {
                            note("tap failed: there is no element $target on the screen")
                            nap(800)
                            continue@loop
                        }
                        if (isSensitive(el.label)) {
                            pending = Pending(el.node, el.label, System.currentTimeMillis())
                            finish("“${el.label}” संवेदनशील लग रहा है। दबाऊँ? हाँ या नहीं बोलो।")
                            return
                        }
                        val ok = onMain { el.node.refresh() && sv.click(el.node) } ?: false
                        note(if (ok) "tapped '${el.label}'" else "tap on '${el.label}' failed")
                        nap(1500)
                    }
                    "type" -> {
                        val el = snap.els.getOrNull(target)
                        val out = Hinglish.convert(text)
                        val ok = onMain { typeInto(sv, el, out) } ?: false
                        note(if (ok) "typed '$out'" else "typing failed (tap a text box first)")
                        nap(800)
                    }
                    "scroll" -> {
                        val ok = onMain { sv.scroll(dir != "up") } ?: false
                        note(if (ok) "scrolled $dir" else "nothing to scroll")
                        nap(1000)
                    }
                    "enter" -> {
                        val ok = onMain { sv.pressEnter() } ?: false
                        note(if (ok) "pressed enter" else "enter failed (needs Android 11+ and a focused box)")
                        nap(1500)
                    }
                    "back" -> {
                        onMain { sv.go(AccessibilityService.GLOBAL_ACTION_BACK) }
                        note("went back")
                        nap(1200)
                    }
                    "home" -> {
                        onMain { sv.go(AccessibilityService.GLOBAL_ACTION_HOME) }
                        note("went home")
                        nap(1200)
                    }
                    "wait" -> {
                        note("waited")
                        nap(2000)
                    }
                    else -> {
                        note("unknown action '$action'")
                        nap(500)
                    }
                }
            }
        } catch (e: Exception) {
            finish("Agent में दिक्कत आई (${e.javaClass.simpleName})।")
        }
    }

    // ---------- helpers ----------

    private fun finish(msg: String?) {
        running = false
        if (msg != null && !cancelled) speak(msg)
    }

    private fun speak(msg: String) {
        main.post { CommandRouter.notifyOverlay?.invoke(msg) }
    }

    private fun tr(msg: String) {
        val t = trace ?: return
        main.post { t(msg) }
    }

    private fun note(s: String) {
        log.add("$steps. $s")
        tr("  -> $s")
    }

    private fun nap(ms: Long) {
        var left = ms
        while (left > 0 && !cancelled) {
            val s = minOf(200L, left)
            Thread.sleep(s)
            left -= s
        }
    }

    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var out: T? = null
        val latch = CountDownLatch(1)
        main.post {
            try {
                out = block()
            } finally {
                latch.countDown()
            }
        }
        latch.await(8, TimeUnit.SECONDS)
        return out
    }

    private fun isSensitive(label: String): Boolean {
        val l = label.lowercase()
        return SENSITIVE.any { l.contains(it) }
    }

    private fun snapshot(sv: JarvisAccessibilityService): Snap {
        val root = sv.targetRoot() ?: return Snap("", emptyList())
        val els = ArrayList<El>()
        walk(root, els, 0)
        return Snap(root.packageName?.toString().orEmpty(), els)
    }

    private fun walk(n: AccessibilityNodeInfo, out: MutableList<El>, depth: Int) {
        if (out.size >= MAX_EL || depth > 30) return
        if (n.isVisibleToUser && !n.isPassword) {
            val text = n.text?.toString()?.trim().orEmpty()
            val label = text.ifEmpty { n.contentDescription?.toString()?.trim().orEmpty() }
            val kind = when {
                n.isEditable -> "input"
                n.isClickable -> "button"
                else -> "text"
            }
            if (kind == "input") {
                out.add(El(n, kind, if (label.isEmpty()) "(text box)" else label.take(60)))
            } else if (label.isNotEmpty()) {
                out.add(El(n, kind, label.take(60)))
            }
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            walk(c, out, depth + 1)
        }
    }

    private fun screenText(s: Snap): String {
        if (s.els.isEmpty()) return "(no other app screen is open right now)"
        val sb = StringBuilder("app: ").append(s.pkg).append("\n")
        s.els.forEachIndexed { i, e ->
            sb.append(i).append(" [").append(e.kind).append("] ")
                .append(e.label.replace("\n", " ")).append("\n")
        }
        return sb.toString()
    }

    private fun typeInto(sv: JarvisAccessibilityService, el: El?, text: String): Boolean {
        if (el != null && el.node.refresh()) {
            if (el.node.isPassword) return false
            if (el.node.isEditable) {
                val b = Bundle()
                b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                if (el.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) return true
            } else {
                sv.click(el.node)
            }
        }
        return sv.typeText(text)
    }

    private fun callGemini(p: GeminiProvider, userText: String): Reply {
        val key = p.apiKey()
        if (key.isNullOrBlank()) return Reply(null, "Gemini key सेट नहीं है। Settings में डालो।")
        for (attempt in 1..2) {
            var conn: HttpURLConnection? = null
            try {
                val body = JSONObject()
                    .put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))),
                    )
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", userText))),
                        ),
                    )
                    .put(
                        "generationConfig",
                        JSONObject().put("temperature", 0.2).put("responseMimeType", "application/json"),
                    )
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${p.model().trim()}:generateContent")
                val c = url.openConnection() as HttpURLConnection
                conn = c
                c.requestMethod = "POST"
                c.connectTimeout = 15000
                c.readTimeout = 60000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                if (code in 200..299) {
                    return parseReply(c.inputStream.bufferedReader().use { it.readText() })
                }
                if ((code == 429 || code >= 500) && attempt < 2) {
                    Thread.sleep(2500)
                    continue
                }
                return Reply(
                    null,
                    when {
                        code == 429 -> "Gemini पर अभी भीड़ या limit है। थोड़ी देर बाद try करो।"
                        code >= 500 -> "Gemini अभी busy है (high demand)। थोड़ी देर बाद try करो या दूसरा model चुनो।"
                        code == 404 -> "Gemini model का नाम सही नहीं है।"
                        code == 401 || code == 403 || code == 400 -> "Gemini key या request सही नहीं है (HTTP $code)।"
                        else -> "Gemini error HTTP $code।"
                    },
                )
            } catch (e: Exception) {
                return Reply(null, "Gemini से बात नहीं हो पाई (${e.javaClass.simpleName})।")
            } finally {
                conn?.disconnect()
            }
        }
        return Reply(null, "Gemini से जवाब नहीं मिला।")
    }

    private fun parseReply(raw: String): Reply {
        return try {
            val parts = JSONObject(raw)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            }
            var t = sb.toString().trim()
            if (t.startsWith("```")) {
                t = t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            }
            if (t.isEmpty()) Reply(null, "Gemini ने खाली जवाब दिया।") else Reply(JSONObject(t), null)
        } catch (e: Exception) {
            Reply(null, "Gemini का जवाब समझ नहीं आया।")
        }
    }
}
