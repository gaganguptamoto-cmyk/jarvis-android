package com.jarvis.assistant.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import com.jarvis.assistant.ui.components.OrbState
import kotlin.math.min

class OrbView(context: Context) : View(context) {
    var orbState: OrbState = OrbState.Idle
        set(value) {
            if (field != value) {
                field = value
                restart()
            }
        }

    var animated: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                restart()
            }
        }

    private var angle = 0f
    private var pulse = 1f
    private var spin: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private val cyan = 0xFF00E5FF.toInt()
    private val blue = 0xFF2979FF.toInt()

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4000E5FF }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = cyan
    }
    private val arcBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        color = blue
    }
    private val arcCyan = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        color = cyan
    }
    private val rect = RectF()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onDetachedFromWindow() {
        stopAnimators()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glow.shader = RadialGradient(
            w / 2f,
            h / 2f,
            min(w, h) / 2f,
            intArrayOf(0x5900E5FF, 0x0000E5FF),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    private fun stopAnimators() {
        spin?.cancel()
        pulseAnim?.cancel()
        spin = null
        pulseAnim = null
    }

    private fun restart() {
        stopAnimators()
        if (!isAttachedToWindow) return
        if (!animated) {
            angle = 0f
            pulse = 1f
            invalidate()
            return
        }
        spin = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = orbState.spinMs.toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        pulseAnim = ValueAnimator.ofFloat(0.92f, 1.08f).apply {
            duration = orbState.pulseMs.toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { pulse = it.animatedValue as Float }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f

        canvas.save()
        canvas.scale(pulse, pulse, cx, cy)
        canvas.drawCircle(cx, cy, r * 0.9f, glow)
        canvas.drawCircle(cx, cy, r * 0.32f, fill)
        canvas.restore()

        canvas.drawCircle(cx, cy, r * 0.45f, ring)
        rect.set(cx - r * 0.65f, cy - r * 0.65f, cx + r * 0.65f, cy + r * 0.65f)
        canvas.drawArc(rect, angle, 100f, false, arcBlue)
        canvas.drawArc(rect, 180f - angle, 60f, false, arcCyan)
    }
}
