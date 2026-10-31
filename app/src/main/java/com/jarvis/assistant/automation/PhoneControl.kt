package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo

fun JarvisAccessibilityService.swipe(dir: String): Boolean {
    val dm = resources.displayMetrics
    val w = dm.widthPixels.toFloat()
    val h = dm.heightPixels.toFloat()
    val p = Path()
    when (dir) {
        "up" -> { p.moveTo(w / 2, h * 0.75f); p.lineTo(w / 2, h * 0.25f) }
        "down" -> { p.moveTo(w / 2, h * 0.25f); p.lineTo(w / 2, h * 0.75f) }
        "left" -> { p.moveTo(w * 0.8f, h / 2); p.lineTo(w * 0.2f, h / 2) }
        else -> { p.moveTo(w * 0.2f, h / 2); p.lineTo(w * 0.8f, h / 2) }
    }
    val g = GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(p, 0, 350))
        .build()
    return dispatchGesture(g, null, null)
}

fun JarvisAccessibilityService.findAnywhere(labels: List<String>): AccessibilityNodeInfo? {
    val roots = windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow)
    for (r in roots) {
        for (l in labels) {
            val n = r.findAccessibilityNodeInfosByText(l).firstOrNull { it.isVisibleToUser }
            if (n != null) return n
        }
    }
    return null
}

object PhoneControl {

    private val main = Handler(Looper.getMainLooper())
    private var torchOn = false
    private const val NEED = "Accessibility चालू नहीं है।"

    private val Q = Regex("\\b(kya|kaise|kyun|kyu|what|how|why|kaun|kab)\\b|क्या|कैसे|क्यों")
    private val LONG = Regex("^(?:long press|long tap|long click)\\s+(?:on\\s+)?(.+)$", RegexOption.IGNORE_CASE)
    private val DIAL = Regex("^(?:call|dial|phone laga|phone lagao)\\s+(\\+?[0-9][0-9 ]{4,})$", RegexOption.IGNORE_CASE)
    private val URL = Regex(
        "^(?:open|kholo|khol|go to|visit)\\s+((?:https?://)?[\\w-]+(?:\\.[\\w-]+)*\\.(?:com|in|org|net|co|io)(?:/\\S*)?)$",
        RegexOption.IGNORE_CASE,
    )
    private val CLEAR_LABELS = listOf(
        "Clear all", "Close all", "Clear All", "सभी साफ़ करें", "सब साफ़ करें",
        "सभी बंद करें", "सब बंद करें", "सभी हटाएं", "सभी क्लियर", "साफ़ करें",
    )

    private fun late(m: String) {
        CommandRouter.notifyOverlay?.invoke(m)
    }

    private fun svc() = JarvisAccessibilityService.instance

    private fun launch(ctx: Context, i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val starter: Context = JarvisAccessibilityService.instance ?: ctx
        starter.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    fun matches(clean: String, t: String): Boolean = route(t, clean) != null

    fun handle(context: Context, clean: String, t: String): String? =
        route(t, clean)?.invoke(context)

    private fun route(t: String, clean: String): ((Context) -> String)? {
        if (t.isEmpty()) return null
        val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        if (words.size > 7 || Q.containsMatchIn(t)) return null
        fun w(vararg k: String) = k.any { it in words }
        fun s(vararg k: String) = k.any { t.contains(it) }

        if (s("recent", "रीसेंट", "सभी ऐप", "सारे ऐप", "sab app", "all apps", "saare app") &&
            s("clear", "band", "saaf", "close", "क्लियर", "बंद", "साफ", "हटा")
        ) return { _ -> clearRecents() }

        if (s("notification", "नोटिफ")) {
            return if (s("clear", "saaf", "साफ", "हटा", "band", "dismiss")) {
                { _ -> clearNotifs() }
            } else {
                { _ -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "नोटिफिकेशन खोल दिए।") }
            }
        }
        if (s("quick settings", "क्विक सेटिंग", "control panel")) {
            return { _ -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "क्विक सेटिंग्स खोल दीं।") }
        }
        if (s("screenshot", "स्क्रीनशॉट", "screen shot")) {
            return { _ ->
                if (Build.VERSION.SDK_INT < 28) "इस Android पर screenshot नहीं हो सकता।"
                else global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "स्क्रीनशॉट ले लिया।")
            }
        }
        if (s("power menu", "power off", "switch off", "restart", "reboot", "shutdown", "पावर मेन्यू")) {
            return { _ -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, "पावर मेन्यू खोल दिया।") }
        }
        if (w("lock", "लॉक") && !s("unlock") && words.size <= 4) {
            return { _ ->
                if (Build.VERSION.SDK_INT < 28) "इस Android पर lock नहीं हो सकता।"
                else global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "फ़ोन लॉक कर दिया।")
            }
        }

        if (s("volume", "आवाज", "awaaz", "awaz", "वॉल्यूम")) {
            val mute = s("mute", "silent", "म्यूट", "बंद") || w("band", "off")
            val up = w("up") || s("badha", "zyada", "jyada", "increase", "बढ़", "ज्यादा", "तेज")
            val down = w("down", "low") || s("kam", "ghata", "decrease", "कम", "घट", "धीम")
            if (mute) return { c -> volume(c, AudioManager.ADJUST_MUTE, "आवाज़ म्यूट कर दी।") }
            if (up) return { c -> volume(c, AudioManager.ADJUST_RAISE, "आवाज़ बढ़ा दी।") }
            if (down) return { c -> volume(c, AudioManager.ADJUST_LOWER, "आवाज़ कम कर दी।") }
        }

        if (s("torch", "flashlight", "flash light", "टॉर्च", "फ्लैशलाइट", "टार्च")) {
            val off = w("off") || s("band", "bujhao", "बंद", "बुझा")
            val on = w("on") || s("chalu", "jalao", "चालू", "जला")
            return { c -> torch(c, if (off) false else if (on) true else null) }
        }

        if (s("wifi", "wi-fi", "वाईफाई", "वाई-फाई", "वाई फाई")) {
            return { c ->
                val i = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                else Intent(Settings.ACTION_WIFI_SETTINGS)
                if (launch(c, i)) "Wi-Fi पैनल खोल दिया, वहाँ से चालू/बंद करो।" else "Wi-Fi खोल नहीं पाया।"
            }
        }
        if (s("bluetooth", "ब्लूटूथ")) {
            return { c -> if (launch(c, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) "Bluetooth सेटिंग खोल दी।" else "खोल नहीं पाया।" }
        }
        if (s("airplane", "flight mode", "एयरप्लेन", "फ्लाइट मोड")) {
            return { c -> if (launch(c, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))) "Airplane mode की सेटिंग खोल दी।" else "खोल नहीं पाया।" }
        }
        if (s("hotspot", "हॉटस्पॉट")) {
            return { c -> if (launch(c, Intent(Settings.ACTION_WIRELESS_SETTINGS))) "नेटवर्क सेटिंग खोल दी।" else "खोल नहीं पाया।" }
        }
        if (s("brightness", "ब्राइटनेस")) {
            return { c -> if (launch(c, Intent(Settings.ACTION_DISPLAY_SETTINGS))) "डिस्प्ले सेटिंग खोल दी।" else "खोल नहीं पाया।" }
        }

        if (s("swipe", "स्वाइप")) {
            val d = when {
                w("up") || s("upar", "ऊपर") -> "up"
                w("down") || s("neeche", "नीचे") -> "down"
                w("left") || s("baayen", "bayen", "बाएं") -> "left"
                w("right") || s("daayen", "dayen", "दाएं") -> "right"
                else -> null
            }
            if (d != null) return { _ -> doSwipe(d) }
        }
        if (s("refresh", "रिफ्रेश", "reload")) return { _ -> doSwipe("down") }
        if (s("next video", "agla video", "अगला वीडियो", "next short")) return { _ -> doSwipe("up") }
        if (s("previous video", "pichla video", "पिछला वीडियो")) return { _ -> doSwipe("down") }

        if (s("enter dabao", "enter press", "press enter", "एंटर") && words.size <= 3) {
            return { _ ->
                val sv = svc()
                if (sv == null) NEED else if (sv.pressEnter()) "Enter दबा दिया।" else "Enter नहीं दबा पाया।"
            }
        }
        if (s("select all", "सेलेक्ट ऑल", "सब चुनो") && words.size <= 4) return { _ -> editAction("selectall") }
        if (w("copy", "कॉपी") && words.size <= 3) return { _ -> editAction("copy") }
        if (w("paste", "पेस्ट") && words.size <= 3) return { _ -> editAction("paste") }
        if (w("cut", "कट") && words.size <= 3) return { _ -> editAction("cut") }
        if (s("text") && s("clear", "hatao", "delete", "मिटा", "हटा") && words.size <= 4) {
            return { _ ->
                val sv = svc()
                if (sv == null) NEED else if (sv.typeText("")) "टेक्स्ट हटा दिया।" else "यहाँ टेक्स्ट बॉक्स नहीं मिला।"
            }
        }

        LONG.matchEntire(clean)?.let { m ->
            val label = m.groupValues[1].trim()
            return { _ -> longPress(label) }
        }

        val mediaCtx = t in setOf("pause", "resume", "next", "previous", "skip", "play") ||
            s("song", "gana", "gaana", "music", "track", "गाना", "गाने", "म्यूजिक", "संगीत")
        if (mediaCtx) {
            if (s("next", "agla", "अगला", "skip")) return { c -> media(c, KeyEvent.KEYCODE_MEDIA_NEXT, "अगला गाना।") }
            if (s("previous", "pichla", "पिछला")) return { c -> media(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "पिछला गाना।") }
            if (s("pause", "band", "रोक", "बंद", "stop")) return { c -> media(c, KeyEvent.KEYCODE_MEDIA_PAUSE, "रोक दिया।") }
            if (s("play", "chalao", "chalu", "चला", "चालू", "resume")) return { c -> media(c, KeyEvent.KEYCODE_MEDIA_PLAY, "चला दिया।") }
        }

        DIAL.matchEntire(clean)?.let { m ->
            val num = m.groupValues[1].replace(" ", "")
            return { c ->
                if (launch(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))) "$num डायलर में खोल दिया, कॉल बटन खुद दबाओ।"
                else "डायलर नहीं खुला।"
            }
        }

        if (s("alarm", "अलार्म") && Regex("\\d").containsMatchIn(t)) return { c -> alarm(c, t) }
        if (s("timer", "टाइमर") && Regex("\\d").containsMatchIn(t)) return { c -> timer(c, t) }

        URL.matchEntire(clean)?.let { m ->
            var u = m.groupValues[1]
            if (!u.startsWith("http")) u = "https://$u"
            return { c -> if (launch(c, Intent(Intent.ACTION_VIEW, Uri.parse(u)))) "वेबसाइट खोल दी।" else "खोल नहीं पाया।" }
        }
        return null
    }

    private fun global(action: Int, ok: String): String {
        val sv = svc() ?: return NEED
        return if (sv.go(action)) ok else "ये नहीं हो पाया।"
    }

    private fun clearRecents(): String {
        val sv = svc() ?: return NEED
        if (!sv.go(AccessibilityService.GLOBAL_ACTION_RECENTS)) return "रीसेंट्स नहीं खुले।"
        main.postDelayed({
            val n = sv.findAnywhere(CLEAR_LABELS)
            late(if (n != null && sv.click(n)) "रीसेंट्स साफ़ कर दिए।" else "Clear all बटन नहीं मिला। रीसेंट्स खुले हैं, खुद साफ़ कर लो।")
        }, 1500)
        return "रीसेंट्स खोल रहा हूँ, साफ़ करने की कोशिश करूँगा।"
    }

    private fun clearNotifs(): String {
        val sv = svc() ?: return NEED
        if (!sv.go(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) return "नोटिफिकेशन नहीं खुले।"
        main.postDelayed({
            val n = sv.findAnywhere(CLEAR_LABELS + listOf("Clear"))
            late(if (n != null && sv.click(n)) "नोटिफिकेशन साफ़ कर दिए।" else "Clear बटन नहीं मिला। नोटिफिकेशन खुले हैं।")
        }, 1200)
        return "नोटिफिकेशन खोल रहा हूँ, साफ़ करने की कोशिश करूँगा।"
    }

    private fun volume(ctx: Context, dir: Int, ok: String): String = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val times = if (dir == AudioManager.ADJUST_MUTE) 1 else 3
        for (i in 0 until times) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, if (i == 0) AudioManager.FLAG_SHOW_UI else 0)
        ok
    } catch (e: Exception) {
        "आवाज़ बदल नहीं पाया।"
    }

    private fun torch(ctx: Context, on: Boolean?): String = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) {
            "इस फ़ोन में टॉर्च नहीं मिली।"
        } else {
            val target = on ?: !torchOn
            cm.setTorchMode(id, target)
            torchOn = target
            if (target) "टॉर्च चालू।" else "टॉर्च बंद।"
        }
    } catch (e: Exception) {
        "टॉर्च नहीं चल पाई।"
    }

    private fun media(ctx: Context, code: Int, ok: String): String = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        ok
    } catch (e: Exception) {
        "म्यूज़िक कंट्रोल नहीं हो पाया।"
    }

    private fun doSwipe(dir: String): String {
        val sv = svc() ?: return NEED
        return if (sv.swipe(dir)) "स्वाइप कर दिया।" else "स्वाइप नहीं हो पाया।"
    }

    private fun editAction(kind: String): String {
        val sv = svc() ?: return NEED
        val field = sv.targetRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "यहाँ कोई टेक्स्ट बॉक्स चुना हुआ नहीं है।"
        if (field.isPassword) return "पासवर्ड बॉक्स में ये नहीं करूँगा।"
        val ok = when (kind) {
            "copy" -> field.performAction(AccessibilityNodeInfo.ACTION_COPY)
            "paste" -> field.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            "cut" -> field.performAction(AccessibilityNodeInfo.ACTION_CUT)
            else -> {
                val b = Bundle()
                b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, field.text?.length ?: 0)
                field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, b)
            }
        }
        return if (ok) "हो गया।" else "ये नहीं हो पाया।"
    }

    private fun longPress(label: String): String {
        val sv = svc() ?: return NEED
        if (!sv.hasTarget()) return "कोई दूसरे app की स्क्रीन नहीं मिली।"
        var n: AccessibilityNodeInfo? = sv.findByText(label) ?: return "“$label” स्क्रीन पर नहीं दिखा।"
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isLongClickable) {
                return if (n.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) "“$label” पर long press किया।" else "नहीं हो पाया।"
            }
            n = n.parent
            depth++
        }
        return "“$label” long press नहीं हो सकता।"
    }

    private fun alarm(ctx: Context, t: String): String {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(t) ?: return "समय समझ नहीं आया। जैसे: alarm 7 baje"
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3]
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        if (h > 23 || min > 59) return "समय सही नहीं लगा।"
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, min)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(ctx, i)) "अलार्म $h:${min.toString().padStart(2, '0')} का लगा दिया।" else "अलार्म नहीं लग पाया।"
    }

    private fun timer(ctx: Context, t: String): String {
        val n = Regex("(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: return "समय समझ नहीं आया।"
        val mult = when {
            Regex("sec|second|सेकंड").containsMatchIn(t) -> 1
            Regex("hour|ghanta|ghante|घंटा|घंटे").containsMatchIn(t) -> 3600
            else -> 60
        }
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, n * mult)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(ctx, i)) "टाइमर लगा दिया।" else "टाइमर नहीं लग पाया।"
    }
}
