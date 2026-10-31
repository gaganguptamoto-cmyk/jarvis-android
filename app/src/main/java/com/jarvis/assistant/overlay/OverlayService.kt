package com.jarvis.assistant.overlay

import com.jarvis.assistant.automation.CommandRouter
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.ai.AiError
import com.jarvis.assistant.ai.AiMessage
import com.jarvis.assistant.ai.AiResult
import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.core.SecureKeyStore
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.ui.components.OrbState
import com.jarvis.assistant.voice.VoiceError
import com.jarvis.assistant.voice.VoiceHub
import com.jarvis.assistant.voice.voiceErrorRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

private val CYAN = 0xFF00E5FF.toInt()
private val GREEN = 0xFF39FF9C.toInt()
private val RED = 0xFFFF6B6B.toInt()
private val DIM = 0xFF7FDBCA.toInt()

private const val HINT_HI =
    "\n\n[Voice reply: keep it short and natural. Write Hindi words in Devanagari and " +
        "English words in Roman letters. No markdown, no emojis, no bullet symbols.]"
private const val HINT_EN =
    "\n\n[Voice reply: keep it short and natural, in simple English. " +
        "No markdown, no emojis, no bullet symbols.]"

private fun aiErrorRes(error: AiError): Int = when (error) {
    AiError.NoKey -> R.string.ai_err_no_key
    AiError.NoInternet -> R.string.ai_err_no_internet
    AiError.InvalidKey -> R.string.ai_err_invalid_key
    AiError.RateLimit -> R.string.ai_err_rate_limit
    AiError.Timeout -> R.string.ai_err_timeout
    AiError.Server -> R.string.ai_err_server
    AiError.BadModel -> R.string.ai_err_bad_model
    AiError.Empty -> R.string.ai_err_empty
    AiError.Unknown -> R.string.ai_err_unknown
}

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var store: SettingsStore
    private lateinit var hub: VoiceHub
    private lateinit var provider: GeminiProvider
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var built = false
    private lateinit var rootView: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var orb: OrbView
    private lateinit var title: TextView
    private lateinit var minimizeBtn: TextView
    private lateinit var subtitle: TextView
    private lateinit var panel: LinearLayout
    private lateinit var status: TextView
    private lateinit var scroll: ScrollView
    private lateinit var transcript: TextView
    private lateinit var input: EditText

    private val transcriptText = SpannableStringBuilder()
    private val session = mutableListOf<AiMessage>()
    private var expanded = false
    private var loading = false
    private var lastReply = ""
    private var notice = ""
    private var noticeAt = 0L
    private var lastShown = 0L
    private var shownError: VoiceError? = null
    private var savedX = 0
    private var savedY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        store = SettingsStore.get(applicationContext)
        hub = VoiceHub(applicationContext, store)
        val keyStore = SecureKeyStore(applicationContext)
        provider = GeminiProvider(
            apiKey = { keyStore.loadApiKey() },
            model = { store.geminiModel },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForegroundCompat()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!built) {
            buildUi()
            built = true
            running = true
            handler.post(poll)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        hub.shutdown()
        if (built) {
            try {
                wm.removeView(rootView)
            } catch (e: Exception) {
                // view was already removed
            }
        }
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.overlay_notif_stop),
                stopIntent,
            )
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun button(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(CYAN)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(0x3300E5FF, CYAN, 10)
            setOnClickListener { onClick() }
        }
    }

    private fun buildUi() {
        val mono = Typeface.MONOSPACE

        orb = OrbView(this)
        orb.animated = store.animations

        title = TextView(this).apply {
            text = "JARVIS"
            typeface = mono
            textSize = 16f
            setTextColor(CYAN)
            setPadding(dp(8), 0, 0, 0)
            visibility = View.GONE
        }
        minimizeBtn = TextView(this).apply {
            text = "[ _ ]"
            typeface = mono
            textSize = 16f
            setTextColor(CYAN)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
            setOnClickListener { collapse() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(orb, LinearLayout.LayoutParams(dp(64), dp(64)))
        header.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(minimizeBtn)

        subtitle = TextView(this).apply {
            typeface = mono
            textSize = 12f
            setTextColor(GREEN)
            maxWidth = dp(260)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(0xCC050B14.toInt(), CYAN, 12)
            visibility = View.GONE
        }

        status = TextView(this).apply {
            typeface = mono
            textSize = 11f
            setTextColor(DIM)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        transcript = TextView(this).apply {
            typeface = mono
            textSize = 13f
            setTextColor(GREEN)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        scroll = ScrollView(this).apply { addView(transcript) }

        input = EditText(this).apply {
            typeface = mono
            textSize = 14f
            setTextColor(GREEN)
            setHintTextColor(0xFF4F7F73.toInt())
            hint = getString(R.string.overlay_hint)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(0x33000000, CYAN, 10)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submitTyped()
                    true
                } else {
                    false
                }
            }
        }
        val sendBtn = button("SEND") { submitTyped() }
        val micBtn = button("MIC") { toggleVoice() }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val btnParams = {
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6) }
        }
        inputRow.addView(sendBtn, btnParams())
        inputRow.addView(micBtn, btnParams())

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        panel.addView(status)
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)),
        )
        panel.addView(
            inputRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) },
        )

        rootView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rootView.addView(header)
        rootView.addView(subtitle)
        rootView.addView(panel)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(76)
            y = dp(280)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val gesture = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (expanded) collapse() else expand()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    toggleVoice()
                }
            },
        )
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false
        orb.setOnTouchListener { _, e ->
            gesture.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downX = e.rawX
                    downY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        val maxX = (resources.displayMetrics.widthPixels - dp(64)).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - dp(96)).coerceAtLeast(0)
                        params.x = (startX + dx.toInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.toInt()).coerceIn(0, maxY)
                        wm.updateViewLayout(rootView, params)
                    }
                }
            }
            true
        }

        wm.addView(rootView, params)
        appendLine("jarvis> ", getString(R.string.overlay_welcome), GREEN)
    }

    private fun expand() {
        if (expanded) return
        savedX = params.x
        savedY = params.y
        expanded = true
        title.visibility = View.VISIBLE
        minimizeBtn.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        subtitle.visibility = View.GONE
        rootView.background = rounded(0xE6050B14.toInt(), CYAN, 16)
        rootView.setPadding(dp(8), dp(8), dp(8), dp(8))
        params.width = resources.displayMetrics.widthPixels - dp(24)
        params.x = dp(12)
        params.y = dp(48)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        wm.updateViewLayout(rootView, params)
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        val imm = getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        title.visibility = View.GONE
        minimizeBtn.visibility = View.GONE
        panel.visibility = View.GONE
        rootView.background = null
        rootView.setPadding(0, 0, 0, 0)
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.x = savedX
        params.y = savedY
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        wm.updateViewLayout(rootView, params)
    }

    private fun appendLine(prefix: String, text: String, color: Int) {
        val start = transcriptText.length
        transcriptText.append(prefix).append(text).append("\n\n")
        transcriptText.setSpan(
            ForegroundColorSpan(color),
            start,
            transcriptText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        transcript.text = transcriptText
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun submitTyped() {
        val text = input.text.toString()
        input.setText("")
        send(text, false)
    }

    private fun toggleVoice() {
        when (hub.state) {
            OrbState.Listening -> hub.stopListening()
            OrbState.Speaking -> hub.stopSpeaking()
            OrbState.Thinking -> {}
            else -> if (!loading) hub.listen { heard -> send(heard, true) }
        }
    }

    private fun send(text: String, spoken: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty() || loading) return
        appendLine("> ", clean, CYAN)
        session.add(AiMessage(true, clean))
    val local = CommandRouter.handle(applicationContext, clean)
    if (local != null) {
        session.add(AiMessage(false, local))
        lastReply = local
        appendLine("jarvis> ", local, GREEN)
        if (spoken && store.speakReplies) hub.speak(local)
        return
    }
        loading = true
        hub.setThinking(true)
        val base = session.takeLast(20).dropWhile { !it.fromUser }
        val history = if (spoken && base.isNotEmpty()) {
            val hint = if (store.voiceLanguage.startsWith("hi")) HINT_HI else HINT_EN
            base.dropLast(1) + AiMessage(true, base.last().text + hint)
        } else {
            base
        }
        scope.launch {
            when (val result = provider.chat(history)) {
                is AiResult.Success -> {
                    session.add(AiMessage(false, result.text))
                    lastReply = result.text
                    appendLine("jarvis> ", result.text, GREEN)
                    if (spoken && store.speakReplies) {
                        hub.speak(result.text)
                    } else {
                        hub.setThinking(false)
                    }
                }
                is AiResult.Failure -> {
                    val extra = if (result.detail.isBlank()) "" else " (" + result.detail + ")"
                    val message = getString(aiErrorRes(result.kind)) + extra
                    lastReply = ""
                    notice = message
                    noticeAt = System.currentTimeMillis()
                    appendLine("! ", message, RED)
                    hub.setThinking(false)
                }
            }
            loading = false
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val st = hub.state
            orb.orbState = st
            orb.animated = store.animations

            val err = hub.lastError
            if (err == null) {
                shownError = null
            } else if (err != shownError) {
                shownError = err
                notice = getString(voiceErrorRes(err))
                noticeAt = now
                appendLine("! ", notice, RED)
            }

            if (expanded) {
                subtitle.visibility = View.GONE
                val partial = hub.partialText
                status.text = if (st == OrbState.Listening && partial.isNotBlank()) {
                    getString(st.labelRes) + ": " + partial
                } else {
                    getString(st.labelRes)
                }
            } else {
                val text = when {
                    st == OrbState.Listening ->
                        hub.partialText.ifBlank { getString(R.string.chat_listening_wait) }
                    st == OrbState.Thinking -> getString(R.string.chat_thinking)
                    st == OrbState.Speaking -> lastReply.take(220)
                    notice.isNotBlank() && now - noticeAt < 6000 -> notice
                    else -> ""
                }
                if (text.isNotBlank()) {
                    subtitle.text = text
                    subtitle.visibility = View.VISIBLE
                    lastShown = now
                } else if(now - lastShown > 5000) {
                    subtitle.visibility = View.GONE
                }
            }
            handler.postDelayed(this, 150)
        }
    }

    companion object {
        @Volatile
        var running = false

        const val ACTION_STOP = "com.jarvis.assistant.overlay.STOP"
        private const val CHANNEL_ID = "jarvis_overlay"
        private const val NOTIF_ID = 7
    }
}
