package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val EASE_OUT = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** When the hint shows and for how long. */
private const val SHOW_AFTER = 1600L
private const val SHOW_FOR = 4200L

private enum class Phase { Waiting, Shown, Gone }

/**
 * The two hidden gestures, named once: a quiet pill under the stage that appears after the first
 * look, leaves on its own, and never comes back once the person has touched the specimen.
 */
@Composable
internal fun HabitatHint(top: Float, scale: Float, reduced: Boolean, touched: Boolean) {
    val density = LocalDensity.current.density
    var phase by remember { mutableStateOf(Phase.Waiting) }
    LaunchedEffect(phase) {
        if (phase == Phase.Gone) return@LaunchedEffect
        delay(if (phase == Phase.Waiting) SHOW_AFTER else SHOW_FOR)
        phase = if (phase == Phase.Waiting) Phase.Shown else Phase.Gone
    }
    LaunchedEffect(touched) { if (touched) phase = Phase.Gone }
    val shown = phase == Phase.Shown
    val duration = if (shown) 300 else 200
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(duration, easing = EASE_OUT), label = "hintAlpha")
    val lift by animateFloatAsState(if (shown || reduced) 0f else 4 * scale, tween(duration, easing = EASE_OUT), label = "hintLift")
    val pop by animateFloatAsState(if (shown || reduced) 1f else 0.96f, tween(duration, easing = EASE_OUT), label = "hintPop")
    Box(Modifier.offset(0.dp, top.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .graphicsLayer { this.alpha = alpha; translationY = lift * density; scaleX = pop; scaleY = pop }
                .height((22 * scale).dp)
                .background(Color(0x99_0B1411), RoundedCornerShape((11 * scale).dp))
                .border(Dp.Hairline, Color(0x2E_D4E4DA), RoundedCornerShape((11 * scale).dp))
                .padding(horizontal = (10 * scale).dp),
            horizontalArrangement = Arrangement.spacedBy((6 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MdiIcon(MdiPath.GESTURE_TAP, 12 * scale, Color(0xFFD7E3D2))
            HText("Tap for its move", 10 * scale, Color(0xFFD7E3D2), weight = FontWeight.Medium, letterSpacing = 0.2f)
            Box(Modifier.padding(horizontal = 2.dp).size((3 * scale).dp).background(Color(0x66_D7E3D2), CircleShape))
            MdiIcon(MdiPath.GESTURE_TAP_HOLD, 12 * scale, Color(0xFFD7E3D2))
            HText("Hold to pet", 10 * scale, Color(0xFFD7E3D2), weight = FontWeight.Medium, letterSpacing = 0.2f)
        }
    }
}
