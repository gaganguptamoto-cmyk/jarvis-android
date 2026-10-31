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

    // Devanagari to rough Latin sound, so "स्पॉटिफाई" and "Spotify" meet in the middle.
    private val MAP = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ए' to "e", 'ऐ' to "e", 'ओ' to "o", 'औ' to "o", 'ऑ' to "o",
        'क' to "k", 'ख' to "k", 'ग' to "g", 'घ' to "g", 'च' to "c", 'छ' to "c",
        'ज' to "j", 'झ' to "j", 'ट' to "t", 'ठ' to "t", 'ड' to "d", 'ढ' to "d",
        'त' to "t", 'थ' to "t", 'द' to "d", 'ध' to "d", 'न' to "n", 'ण' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "b", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "s", 'ष' to "s",
        'स' to "s", 'ह' to "h",
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u",
        'े' to "e", 'ै' to "e", 'ो' to "o", 'ौ' to "o", 'ॉ' to "o",
        'ं' to "n", 'ँ' to "n",
    )

    private fun phonetic(s: String): String {
        val sb = StringBuilder()
        for (ch in s.trim().lowercase()) {
            val m = MAP[ch]
            when {
                m != null -> sb.append(m)
                ch == '्' || ch == '\u093C' -> {}
                ch.isLetterOrDigit() -> sb.append(ch)
            }
        }
        // collapse sounds that differ only by spelling habit
        return sb.toString()
            .replace("ph", "f").replace("w", "v").replace("ck", "k")
            .replace("x", "ks").replace("q", "k").replace("z", "j")
            .replace("y", "i").replace("ee", "i").replace("oo", "u")
            .replace(Regex("[aeiou]+"), "a")
            .replace(Regex("(.)\\1+"), "$1")
    }

    private fun plain(s: String): String =
        s.trim().lowercase().filter { it.isLetterOrDigit() }

    fun installed(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun score(spoken: String, label: String): Int {
        val sp = plain(spoken)
        val lp = plain(label)
        if (sp.isEmpty() || lp.isEmpty()) return 0
        if (sp == lp) return 100
        val ps = phonetic(spoken)
        val pl = phonetic(label)
        if (ps.isEmpty() || pl.isEmpty()) return 0
        if (ps == pl) return 95
        if (lp.startsWith(sp) || pl.startsWith(ps)) return 80
        if (lp.contains(sp) || (ps.length >= 3 && pl.contains(ps))) return 70
        val d = distance(ps, pl)
        val limit = maxOf(1, minOf(ps.length, pl.length) / 4)
        return if (d <= limit) 60 - d else 0
    }

    fun open(context: Context, spoken: String): Result {
        if (plain(spoken).isEmpty() && phonetic(spoken).isEmpty()) return Result.NotFound
        val scored = installed(context)
            .map { it to score(spoken, it.label) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) return Result.NotFound
        val best = scored[0].second
        val top = scored.filter { it.second >= best - 5 }.map { it.first }
        if (top.size > 1 && best < 95) return Result.Many(top.map { it.label }.take(4))
        val app = top[0]
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(app.pkg)
                ?: return Result.Failed
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val starter: Context = JarvisAccessibilityService.instance ?: context
            starter.startActivity(launch)
            Result.Opened(app.label)
        } catch (e: Exception) {
            Result.Failed
        }
    }
}
