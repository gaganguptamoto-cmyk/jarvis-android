package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.jarvis.assistant.ai.GeminiProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Taps by looking at a screenshot. For games and apps whose buttons have no labels. */
object VisualTap {

    sealed class Spot {
        data class Found(val x: Float, val y: Float, val label: String) : Spot()
        object NotFound : Spot()
        data class Error(val message: String) : Spot()
    }

    private class Shot(val bmp: Bitmap?, val error: String?)
    private class PendingTap(val x: Float, val y: Float, val label: String, val at: Long)

    private val main = Handler(Looper.getMainLooper())
    private const val CONFIRM_MS = 30_000L

    @Volatile
    private var pending: PendingTap? = null

    private val YES = setOf(
        "haan", "han", "ha", "yes", "y", "ok", "confirm", "kar do", "kardo",
        "हाँ", "हां", "हा", "कर दो", "ठीक है", "ओके",
    )
    private val NO = setOf(
        "nahi", "nahin", "no", "cancel", "ruko",
        "नहीं", "नही", "रुको", "कैंसल",
    )

    // ---------- confirmation for sensitive taps ----------

    fun askConfirm(x: Float, y: Float, label: String) {
        pending = PendingTap(x, y, label, System.currentTimeMillis())
    }

    /** Returns a reply if this text answered a pending visual tap, otherwise null. */
    fun confirm(t: String): String? {
        val pd = pending ?: return null
        pending = null
        if (System.currentTimeMillis() - pd.at > CONFIRM_MS) return null
        if (t in YES) {
            val sv = JarvisAccessibilityService.instance ?: return "Accessibility बंद हो गई।"
            Thread { tapWithMarker(sv, pd.x, pd.y) }.start()
            return "ठीक है।"
        }
        if (t in NO) return "चलो, छोड़ देते हैं।"
        return null
    }

    // ---------- find the spot ----------

    fun locate(p: GeminiProvider, sv: JarvisAccessibilityService, desc: String): Spot {
        val shot = capture(sv)
        val bmp = shot.bmp ?: return Spot.Error(shot.error ?: "Screenshot नहीं मिला।")
        val w = bmp.width
        val h = bmp.height
        val b64 = encode(bmp)
        bmp.recycle()
        return ask(p, b64, desc, w, h)
    }

    private fun capture(sv: JarvisAccessibilityService): Shot {
        if (Build.VERSION.SDK_INT < 30) {
            return Shot(null, "इस Android पर screenshot लेना संभव नहीं है।")
        }
        var lastCode = 0
        for (attempt in 1..2) {
            val latch = CountDownLatch(1)
            var bmp: Bitmap? = null
            var err = 0
            val exec = Executors.newSingleThreadExecutor()
            main.post {
                sv.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    exec,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            try {
                                val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                                hw?.recycle()
                            } finally {
                                result.hardwareBuffer.close()
                                latch.countDown()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            err = errorCode
                            latch.countDown()
                        }
                    },
                )
            }
            latch.await(6, TimeUnit.SECONDS)
            exec.shutdown()
            if (bmp != null) return Shot(bmp, null)
            lastCode = err
            if (err == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && attempt == 1) {
                Thread.sleep(1300)
                continue
            }
            break
        }
        return Shot(
            null,
            "Screenshot नहीं मिला (code $lastCode)। अगर यह banking जैसा protected ऐप है तो यह नहीं चलेगा। " +
                "Accessibility को एक बार बंद करके फिर चालू करके देखो।",
        )
    }

    private fun encode(bmp: Bitmap): String {
        val longest = max(bmp.width, bmp.height).toFloat()
        val scale = min(1f, 1024f / longest)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).roundToInt(),
                (bmp.height * scale).roundToInt(),
                true,
            )
        } else {
            bmp
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (scaled !== bmp) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun ask(p: GeminiProvider, b64: String, desc: String, w: Int, h: Int): Spot {
        val key = p.apiKey()
        if (key.isNullOrBlank()) return Spot.Error("Gemini key सेट नहीं है। Settings में डालो।")
        val prompt =
            "This is a screenshot of an Android phone screen. Find the ONE on-screen element the user wants to tap: \"" +
                desc + "\". Reply with ONE JSON object only: " +
                "{\"found\": true or false, \"label\": short name of what you found, " +
                "\"box_2d\": [ymin, xmin, ymax, xmax]} " +
                "with the box in 0 to 1000 coordinates relative to the image (0,0 is the top-left corner). " +
                "If the element is not clearly visible, set found to false. Never guess."
        for (attempt in 1..2) {
            var conn: HttpURLConnection? = null
            try {
                val parts = JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject().put("mimeType", "image/jpeg").put("data", b64),
                        ),
                    )
                val body = JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(JSONObject().put("role", "user").put("parts", parts)),
                    )
                    .put(
                        "generationConfig",
                        JSONObject().put("temperature", 0.1).put("responseMimeType", "application/json"),
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
                    return parse(c.inputStream.bufferedReader().use { it.readText() }, w, h)
                }
                if ((code == 429 || code >= 500) && attempt < 2) {
                    Thread.sleep(2500)
                    continue
                }
                return Spot.Error(
                    when {
                        code == 429 -> "Gemini पर अभी भीड़ या limit है। थोड़ी देर बाद try करो।"
                        code >= 500 -> "Gemini अभी busy है। थोड़ी देर बाद try करो।"
                        code == 404 -> "Gemini model का नाम सही नहीं है।"
                        else -> "Gemini ने screenshot वाला request नहीं माना (HTTP $code)।"
                    },
                )
            } catch (e: Exception) {
                return Spot.Error("Gemini से बात नहीं हो पाई (${e.javaClass.simpleName})।")
            } finally {
                conn?.disconnect()
            }
        }
        return Spot.Error("Gemini से जवाब नहीं मिला।")
    }

    private fun parse(raw: String, w: Int, h: Int): Spot {
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
            val fence = "\u0060\u0060\u0060"
            if (t.startsWith(fence)) {
                t = t.removePrefix(fence + "json").removePrefix(fence).removeSuffix(fence).trim()
            }
            if (t.isEmpty()) return Spot.Error("Gemini ने खाली जवाब दिया।")
            val j = JSONObject(t)
            if (!j.optBoolean("found", false)) return Spot.NotFound
            val box = j.optJSONArray("box_2d") ?: return Spot.NotFound
            if (box.length() < 4) return Spot.NotFound
            val ymin = box.optDouble(0)
            val xmin = box.optDouble(1)
            val ymax = box.optDouble(2)
            val xmax = box.optDouble(3)
            val all = listOf(ymin, xmin, ymax, xmax)
            if (all.any { it.isNaN() || it < 0.0 || it > 1000.0 }) return Spot.NotFound
            if (ymax <= ymin || xmax <= xmin) return Spot.NotFound
            val x = ((xmin + xmax) / 2.0 / 1000.0 * w).toFloat()
            val y = ((ymin + ymax) / 2.0 / 1000.0 * h).toFloat()
            Spot.Found(x, y, j.optString("label"))
        } catch (e: Exception) {
            Spot.Error("Gemini का जवाब समझ नहीं आया।")
        }
    }

    // ---------- marker and tap ----------

    /** Shows a marker for about a second so the user can see (and stop) what will be tapped, then taps. */
    fun tapWithMarker(sv: JarvisAccessibilityService, x: Float, y: Float): Boolean {
        var view: View? = null
        onMain { view = showMarker(sv, x, y) }
        Thread.sleep(900)
        onMain { view?.let { hideMarker(sv, it) } }
        Thread.sleep(120)
        return tapAt(sv, x, y)
    }

    private fun tapAt(sv: JarvisAccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val latch = CountDownLatch(1)
        var ok = false
        main.post {
            val accepted = sv.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        ok = true
                        latch.countDown()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        latch.countDown()
                    }
                },
                null,
            )
            if (!accepted) latch.countDown()
        }
        latch.await(4, TimeUnit.SECONDS)
        return ok
    }

    private fun onMain(block: () -> Unit) {
        val latch = CountDownLatch(1)
        main.post {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
    }

    private class MarkerView(context: Context, private val px: Float, private val py: Float) : View(context) {
        private val density = resources.displayMetrics.density
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * density
            color = 0xFF00E5FF.toInt()
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B30.toInt() }

        override fun onDraw(canvas: Canvas) {
            val r = 30f * density
            canvas.drawCircle(px, py, r, ring)
            canvas.drawLine(px - r * 1.5f, py, px + r * 1.5f, py, ring)
            canvas.drawLine(px, py - r * 1.5f, px, py + r * 1.5f, ring)
            canvas.drawCircle(px, py, 5f * density, dot)
        }
    }

    private fun showMarker(sv: JarvisAccessibilityService, x: Float, y: Float): View? {
        return try {
            val wm = sv.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = MarkerView(sv, x, y)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            lp.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            wm.addView(v, lp)
            v
        } catch (e: Exception) {
            null
        }
    }

    private fun hideMarker(sv: JarvisAccessibilityService, v: View) {
        try {
            (sv.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
            // already removed
        }
    }
}
