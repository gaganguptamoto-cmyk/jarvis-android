package com.jarvis.assistant.automation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.jarvis.assistant.PermissionActivity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppCtx { @Volatile var app: Context? = null }

object Memory {
    private const val P = "jarvis_memory"
    private const val K = "notes"
    fun all(c: Context): List<String> {
        val raw = c.getSharedPreferences(P, Context.MODE_PRIVATE).getString(K, "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) { emptyList() }
    }
    private fun save(c: Context, l: List<String>) {
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString(K, JSONArray(l).toString()).apply()
    }
    fun add(c: Context, s: String) = save(c, (all(c) + s).takeLast(100))
    fun remove(c: Context, n: Int): Boolean {
        val l = all(c).toMutableList()
        if (n < 1 || n > l.size) return false
        l.removeAt(n - 1); save(c, l); return true
    }
    fun clear(c: Context) = save(c, emptyList())
}

object DeviceInfo {
    @SuppressLint("MissingPermission")
    fun lastLocation(c: Context): Location? {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: Exception) { null }
    }

    fun snapshot(): String {
        val c = AppCtx.app ?: return ""
        return try {
            val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1
            val charging = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val pm = c.packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { pm.getApplicationLabel(it).toString() }.sorted().take(80)
            val loc = lastLocation(c)
            val notes = Memory.all(c)
            val sb = StringBuilder("\n\nPhone context (use only when relevant, never recite it unasked): ")
            sb.append("device ${Build.MODEL}, Android ${Build.VERSION.RELEASE}; ")
            sb.append("time ${SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}; ")
            if (pct >= 0) sb.append("battery $pct%${if (charging) " charging" else ""}; ")
            if (loc != null) sb.append("last known location ${loc.latitude},${loc.longitude}; ")
            if (apps.isNotEmpty()) sb.append("installed apps: ${apps.joinToString(", ")}; ")
            if (notes.isNotEmpty()) sb.append("\nThings the user asked you to remember: ${notes.joinToString(" | ")}")
            sb.toString()
        } catch (e: Exception) { "" }
    }
}

object Extras {
    private val REMEMBER = Regex("^(?:yaad rakho|yaad rakh|remember|याद रखो|याद रख)\\s*(?:ki|कि|that|:)?\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val FORGET_N = Regex("^(?:bhool jao|forget|भूल जाओ)\\s+(\\d+)$", RegexOption.IGNORE_CASE)
    private val LIST = setOf("kya yaad hai", "what do you remember", "क्या याद है", "meri yaad dikhao")
    private val CLEAR = setOf("sab bhool jao", "forget everything", "सब भूल जाओ")
    private val PERM = setOf("permission do", "permissions do", "permission", "permissions", "sab access", "all access", "परमिशन दो", "अनुमति दो")
    private val WHERE = listOf("kahan hoon", "kahan hu", "kahaan hu", "kahaan hoon", "where am i", "meri location", "mera location", "current location", "लोकेशन बताओ", "कहाँ हूँ", "कहां हूं", "कहाँ हूं")

    fun handle(context: Context, clean: String, t: String, later: (String) -> Unit): String? {
        val c = context.applicationContext
        AppCtx.app = c
        REMEMBER.matchEntire(clean)?.let { m ->
            Memory.add(c, m.groupValues[1].trim())
            return "ठीक है, याद रखूँगा।"
        }
        FORGET_N.matchEntire(clean)?.let { m ->
            return if (Memory.remove(c, m.groupValues[1].toInt())) "भूल गया।" else "इस नंबर की कोई बात नहीं है।"
        }
        if (t in LIST) {
            val l = Memory.all(c)
            return if (l.isEmpty()) "अभी कुछ याद नहीं है।"
            else l.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        }
        if (t in CLEAR) { Memory.clear(c); return "सब भूल गया।" }
        if (t in PERM) { openPermissions(c); return "ये लो, जो चाहिए उसकी अनुमति दे दो।" }
        if (WHERE.any { t.contains(it) }) return where(c, later)
        return null
    }

    private fun openPermissions(c: Context) {
        c.startActivity(Intent(c, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Suppress("DEPRECATION")
    private fun where(c: Context, later: (String) -> Unit): String {
        val loc = DeviceInfo.lastLocation(c)
        if (loc == null) {
            val has = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!has) {
                openPermissions(c)
                return "लोकेशन की इजाज़त चाहिए। खुली हुई विंडो में दे दो, फिर दोबारा पूछना।"
            }
            return "अभी लोकेशन नहीं मिल रही। GPS चालू करके एक बार Google Maps खोल लो, फिर पूछना।"
        }
        Thread {
            val addr = try {
                Geocoder(c, Locale("hi", "IN")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.getAddressLine(0)
            } catch (e: Exception) { null }
            val msg = if (addr != null) "आप यहाँ हो: $addr" else "आपकी लोकेशन: ${loc.latitude}, ${loc.longitude}"
            Handler(Looper.getMainLooper()).post { later(msg) }
        }.start()
        return "एक सेकंड।"
    }
}
