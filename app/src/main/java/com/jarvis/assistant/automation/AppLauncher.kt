package com.jarvis.assistant.automation

import android.content.Context
import android.content.Intent

data class AppInfo(val label: String, val pkg: String)

object AppLauncher {

    sealed class Result {
        data class Opened(val label: String) : Result()
        data class Many(val labels: List<String>) : Result()
        object NotFound : Result()
        object Failed : Result()
    }

    private val aliases = mapOf(
        "यूट्यूब" to "youtube",
        "व्हाट्सएप" to "whatsapp",
        "वाट्सएप" to "whatsapp",
        "क्रोम" to "chrome",
        "कैमरा" to "camera",
        "कैनवा" to "canva",
        "गैलरी" to "gallery",
        "सेटिंग्स" to "settings",
        "इंस्टाग्राम" to "instagram",
        "मैप्स" to "maps",
        "स्पॉटिफाई" to "spotify",
        "स्पोटिफाई" to "spotify",
        "टेलीग्राम" to "telegram",
        "स्नैपचैट" to "snapchat",
        "नेटफ्लिक्स" to "netflix",
        "अमेज़न" to "amazon",
        "अमेजन" to "amazon",
        "फ्लिपकार्ट" to "flipkart",
        "पेटीएम" to "paytm",
        "फोनपे" to "phonepe",
        "जीमेल" to "gmail",
    )

    private fun normalize(s: String): String {
        val t = s.trim().lowercase()
        val a = aliases[t] ?: t
        return a.filter { !it.isWhitespace() && it != '.' && it != '-' && it != '_' }
    }

    fun installed(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    fun open(context: Context, spoken: String): Result {
        val q = normalize(spoken)
        if (q.isEmpty()) return Result.NotFound
        val apps = installed(context)
        val exact = apps.filter { normalize(it.label) == q }
        val starts = apps.filter { normalize(it.label).startsWith(q) }
        val contains = apps.filter { normalize(it.label).contains(q) }
        val pick = when {
            exact.isNotEmpty() -> exact
            starts.isNotEmpty() -> starts
            else -> contains
        }
        if (pick.isEmpty()) return Result.NotFound
        if (pick.size > 1) return Result.Many(pick.map { it.label }.take(4))
        val app = pick[0]
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(app.pkg)
                ?: return Result.Failed
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            Result.Opened(app.label)
        } catch (e: Exception) {
            Result.Failed
        }
    }
}
