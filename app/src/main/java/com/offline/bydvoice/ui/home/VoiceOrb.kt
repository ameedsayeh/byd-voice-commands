package com.offline.bydvoice.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.offline.bydvoice.state.Phase
import com.offline.bydvoice.ui.theme.Palette
import kotlin.math.max

fun phaseColor(phase: Phase) = when (phase) {
    Phase.IDLE, Phase.LISTENING, Phase.LOADING -> Palette.Accent
    Phase.PROCESSING -> Palette.Think
    Phase.SUCCESS -> Palette.Success
    Phase.ERROR -> Palette.Warn
}

@Composable
fun VoiceOrb(phase: Phase, level: Float, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(phaseColor(phase), tween(350), label = "orbColor")
    val lvl by animateFloatAsState(if (phase == Phase.LISTENING) level else 0f, spring(stiffness = 600f), label = "lvl")
    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "breathe")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ripple")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")

    Box(
        modifier
            .size(size)
            .semantics { contentDescription = "Speak a command" }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val c = center
            val r = this.size.minDimension * 0.24f
            val active = phase == Phase.LISTENING

            // expanding rings (always faint, stronger with your voice while listening)
            for (k in 0 until 3) {
                val p = (ripple + k / 3f) % 1f
                val strength = if (active) 0.25f + 0.75f * lvl else if (phase == Phase.IDLE) 0.18f else 0.1f
                drawCircle(color.copy(alpha = (1 - p) * 0.35f * strength), radius = r * (1.15f + 0.95f * p), center = c, style = Stroke(width = r * 0.045f))
            }
            // halo
            val haloR = r * (1.45f + 0.10f * breathe + 0.65f * lvl)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0f)), c, haloR), haloR, c)
            // core
            val coreR = r * (1f + 0.06f * breathe + 0.10f * lvl)
            drawCircle(
                Brush.radialGradient(listOf(lerpWhite(color, 0.28f), color, darken(color)), Offset(c.x - r * 0.35f, c.y - r * 0.45f), coreR * 1.7f),
                coreR, c,
            )
            drawCircle(Color.White.copy(alpha = 0.10f), coreR, c, style = Stroke(width = 1.5.dp.toPx()))
            if (phase == Phase.PROCESSING || phase == Phase.LOADING) {
                drawArc(Color.White.copy(alpha = 0.9f), spin, 80f, false,
                    topLeft = Offset(c.x - coreR * 1.22f, c.y - coreR * 1.22f),
                    size = androidx.compose.ui.geometry.Size(coreR * 2.44f, coreR * 2.44f),
                    style = Stroke(width = r * 0.07f, cap = StrokeCap.Round))
            }
        }
        val icon = when (phase) {
            Phase.SUCCESS -> Icons.Rounded.Check
            Phase.ERROR -> Icons.Rounded.PriorityHigh
            else -> Icons.Rounded.Mic
        }
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.17f))
    }
}

private fun lerpWhite(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, c.alpha)
private fun darken(c: Color) = Color(c.red * 0.6f, c.green * 0.6f, c.blue * 0.6f, c.alpha)

/** Small live waveform used in the assistant card. */
@Composable
fun LevelBars(level: Float, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ph")
    val lvl by animateFloatAsState(level, spring(stiffness = 500f), label = "lv")
    Canvas(modifier) {
        val n = 24
        val gap = size.width / n
        for (i in 0 until n) {
            val wobble = 0.5f + 0.5f * kotlin.math.sin(phase + i * 0.7f)
            val taper = kotlin.math.sin(Math.PI * (i + 0.5) / n).toFloat()
            val h = max(gap * 0.5f, size.height * (0.08f + 0.85f * lvl * wobble) * (0.35f + 0.65f * taper))
            val x = gap * i + gap / 2
            drawLine(color.copy(alpha = 0.35f + 0.65f * taper), Offset(x, center.y - h / 2), Offset(x, center.y + h / 2), strokeWidth = gap * 0.5f, cap = StrokeCap.Round)
        }
    }
}
