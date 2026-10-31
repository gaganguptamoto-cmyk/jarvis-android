package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.R

enum class OrbState(val labelRes: Int, val spinMs: Int, val pulseMs: Int) {
    Idle(R.string.orb_idle, 8000, 3000),
    Listening(R.string.orb_listening, 4000, 1200),
    Thinking(R.string.orb_thinking, 1500, 800),
    Speaking(R.string.orb_speaking, 3000, 500),
}

@Composable
fun JarvisOrb(state: OrbState, animated: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val transition = rememberInfiniteTransition(label = "orb")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(state.spinMs, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(state.pulseMs), RepeatMode.Reverse),
        label = "pulse",
    )
    val angle = if (animated) spin else 0f
    val scale = if (animated) pulse else 1f

    Canvas(modifier.size(220.dp)) {
        val c = center
        val r = size.minDimension / 2f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.35f), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r * scale,
            center = c,
        )
        drawCircle(
            color = primary.copy(alpha = 0.25f),
            radius = r * 0.40f * scale,
            center = c,
        )
        drawCircle(
            color = primary,
            radius = r * 0.55f,
            center = c,
            style = Stroke(width = 3.dp.toPx()),
        )
        val ringTopLeft = Offset(c.x - r * 0.75f, c.y - r * 0.75f)
        val ringSize = Size(r * 1.5f, r * 1.5f)
        drawArc(
            color = secondary,
            startAngle = angle,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = ringTopLeft,
            size = ringSize,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = primary,
            startAngle = 180f - angle,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = ringTopLeft,
            size = ringSize,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
