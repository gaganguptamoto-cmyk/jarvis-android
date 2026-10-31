package com.jarvis.assistant.automation

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

fun JarvisAccessibilityService.pressEnter(): Boolean {
    if (Build.VERSION.SDK_INT < 30) return false
    val field = targetRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
    return field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
}

object SearchFlow {
    private val main = Handler(Looper.getMainLooper())
    private val LABELS = listOf("Search", "खोजें", "सर्च", "खोज")

    fun openOnly(done: (String) -> Unit) {
        val s = JarvisAccessibilityService.instance ?: return
        if (s.hasInputFocus()) return
        val btn = LABELS.firstNotNullOfOrNull { s.findByText(it) }
        if (btn == null || !s.click(btn)) done("इस स्क्रीन पर Search बटन नहीं मिला।")
    }

    fun run(query: String, done: (String) -> Unit) {
        val s = JarvisAccessibilityService.instance
        if (s == null) {
            done("Accessibility चालू नहीं है।")
            return
        }
        if (!s.hasTarget()) {
            done("कोई दूसरे app की स्क्रीन नहीं मिली।")
            return
        }
        if (!s.hasInputFocus()) {
            val btn = LABELS.firstNotNullOfOrNull { s.findByText(it) }
            if (btn == null || !s.click(btn)) {
                done("इस स्क्रीन पर Search बटन नहीं मिला। “$query” नहीं खोज पाया।")
                return
            }
        }
        main.postDelayed({ typeStep(s, query, 0, done) }, 900)
    }

    private fun typeStep(
        s: JarvisAccessibilityService,
        query: String,
        tries: Int,
        done: (String) -> Unit,
    ) {
        if (s.typeText(query)) {
            main.postDelayed({
                if (s.pressEnter()) {
                    done("“$query” लिखकर Enter दबा दिया।")
                } else {
                    done("“$query” लिख दिया, पर Enter नहीं दबा पाया। कीबोर्ड का search बटन दबा दो।")
                }
            }, 600)
        } else if (tries < 2) {
            main.postDelayed({ typeStep(s, query, tries + 1, done) }, 700)
        } else {
            done("Search box में “$query” नहीं लिख पाया।")
        }
    }
}
