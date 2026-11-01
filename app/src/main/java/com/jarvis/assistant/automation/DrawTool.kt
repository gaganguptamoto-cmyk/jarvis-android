package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DrawTool {
    fun draw(json: String): Boolean {
        val sv = JarvisAccessibilityService.instance ?: return false
        val strokes = try { JSONArray(json.trim()) } catch (e: Exception) { return false }
        val dm = sv.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        var any = false
        for (i in 0 until strokes.length()) {
            val pts = strokes.optJSONArray(i) ?: continue
            if (pts.length() < 2) continue
            val path = Path()
            for (j in 0 until pts.length()) {
                val pt = pts.optJSONArray(j) ?: continue
                val x = (pt.optDouble(0, 0.5).coerceIn(0.0, 1.0) * w).toFloat()
                val y = (pt.optDouble(1, 0.5).coerceIn(0.0, 1.0) * h).toFloat()
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val dur = (pts.length() * 50L).coerceIn(250L, 1500L)
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build()
            val latch = CountDownLatch(1)
            var ok = false
            Handler(Looper.getMainLooper()).post {
                val accepted = sv.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
                    override fun onCancelled(d: GestureDescription?) { latch.countDown() }
                }, null)
                if (!accepted) latch.countDown()
            }
            latch.await(4, TimeUnit.SECONDS)
            if (ok) any = true
            Thread.sleep(120)
        }
        return any
    }
}
