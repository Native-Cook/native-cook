package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val CardWidth = 280f
private const val CardHeight = 174f
private val Sparks = listOf(0f to -30f, -28f to 4f, 30f to 6f, 22f to -22f)

/**
 * Step 5: a luminous blue results card (280 × 174, tilted 4.5°) resting on a frosted card and a
 * dark tray with Risk Overview / Configure Bot, above "Your Trading Co-pilot". The card leaves its
 * tray in two moves: it rises flat with the tray (0 – 0.6 s), then keeps climbing as it tips into
 * its pose.
 */
@Composable
internal fun ResultsScene(reduced: Boolean, configure: () -> Unit, overview: () -> Unit) {
    val tray by rememberEntrance(reduced, spring = 0.6f to 0.92f)
    val lift by rememberEntrance(reduced, spring = 0.55f to 0.95f)
    val settled = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) { settled.snapTo(1f); return@LaunchedEffect }
        delay(600)
        settled.animateTo(1f, tween(1000, easing = SettleCurve))
    }
    val amount = rememberSettle(1_022.11, 1_284.5, 0.97, 0.1, 0.4, 2.0, reduced, 0.05)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.at(200f, 401f).graphicsLayer {
            alpha = tray.coerceAtMost(1f)
            translationY = if (reduced) 0f else (1 - tray) * 24.dp.toPx()
        }) { Tray(configure, overview) }

        Box(Modifier.at(204f, 344f).graphicsLayer {
            alpha = settled.value
            translationY = (1 - settled.value) * 40.dp.toPx()
            rotationZ = -5f * settled.value
        }) { GhostCard() }

        Box(Modifier.at(195f, 303f).graphicsLayer {
            val s = settled.value
            val on = if (reduced) 1f else lift
            alpha = lift.coerceAtMost(1f)
            translationY = (150 - 72 * on) * (1 - s) * density
            val scale = (0.84f + 0.11f * on) * (1 - s) + s
            scaleX = scale
            scaleY = scale
            rotationZ = 4.5f * s
            rotationX = 6.8f * s
            rotationY = 1.7f * s
            cameraDistance = 10f * density
        }) { ResultCard(amount, overview) }

        Column(Modifier.at(201f, 603.5f), horizontalAlignment = Alignment.CenterHorizontally) {
            HStack(7f, Modifier.height(39.dp)) {
                HeadingText("Your")
                PopIn(reduced, delay = 0.12f, modifier = Modifier.offset(y = 2.dp)) { Mark(size = 33f) }
                Reveal(reduced, delay = 0.2f, y = 4f) { HeadingText("Trading") }
            }
            Reveal(reduced, delay = 0.36f, y = 10f, blur = 10f, modifier = Modifier.offset(y = (-3).dp)) { HeadingText("Co-pilot") }
        }
        Box(Modifier.at(201f, 681.7f)) {
            Reveal(reduced, delay = 0.42f, y = 6f, blur = 4f) { Caption("Analyze the market, develop smarter strategies, and\ncontrol every trade.") }
        }
    }
}

/** The dark base (300 × 278 at x 50, y 262): a dotted tray and two controls. */
@Composable
private fun Tray(configure: () -> Unit, overview: () -> Unit) {
    Panel(300f, 278f, 34f, fill = listOf(hex(0x0e0e0e)), stroke = listOf(white(0.07f), white(0.07f))) {
        Box(
            Modifier.offset(8.dp, 6.dp).size(284.dp, 200.dp)
                .clip(RoundedCornerShape(28.dp)).background(hex(0x080808))
                .border(1.dp, white(0.04f), RoundedCornerShape(28.dp)),
        ) { DotField(spacing = 5f, opacity = 0.05f) }
        Box(Modifier.offset(y = 220.dp).width(300.dp), contentAlignment = Alignment.TopCenter) {
            DashboardControls(configure, overview, width = 278f, height = 46f, fontSize = 9.4f)
        }
    }
}

@Composable
private fun GhostCard() {
    Panel(282f, 152f, 26f, fill = listOf(hex(0x5a6070, 0.55f), hex(0x2a2c32, 0.75f)), stroke = listOf(white(0.12f), white(0.12f)), modifier = Modifier.clip(RoundedCornerShape(26.dp))) {
        Box(
            Modifier.at(141f, 76f + 30f)
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(3.dp) else Modifier),
        ) { TBText("+$1,284.50", 38f, color = white(0.25f), weight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ResultCard(amount: Double, overview: () -> Unit) {
    Box(
        Modifier.size(CardWidth.dp, CardHeight.dp)
            .drawBehind { dropShadow(Color.Black.copy(alpha = 0.5f), 16.dp.toPx(), 12.dp.toPx(), 26.dp.toPx()) }
            .clip(RoundedCornerShape(26.dp))
            .drawBehind {
                drawRect(Brush.verticalGradient(0f to hex(0x2a62e0), 0.35f to hex(0x2f66e2), 0.62f to hex(0x4f78d9), 1f to hex(0x7690c8)))
                drawRect(Brush.radialGradient(listOf(hex(0xc8d2ea, 0.28f), hex(0xc8d2ea, 0f)), center = Offset(size.width * 0.15f, size.height), radius = 150.dp.toPx()))
                drawRect(Brush.radialGradient(listOf(hex(0x1d4fff, 0.45f), hex(0x1d4fff, 0f)), center = Offset(size.width * 0.52f, size.height * 0.3f), radius = 60.dp.toPx()))
            }
            .drawWithContent {
                drawContent()
                val w = 1.dp.toPx()
                drawRoundRect(Brush.verticalGradient(listOf(white(0.3f), white(0.05f), white(0.4f))), Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(26.dp.toPx() - w / 2), style = Stroke(w))
            },
    ) {
        for ((dx, dy) in Sparks) Box(Modifier.at(140f + dx, 40f + dy).size(2.dp).background(white(0.75f), CircleShape))
        Box(Modifier.at(140f, 40f).drawBehind { dropShadow(white(0.35f), 10.dp.toPx(), 0f, size.width / 2) }) { Mark(MarkStyle.White, 38f) }
        PressScale(overview, label = "View simulated results", modifier = Modifier.at(250f, 30f)) {
            Box(Modifier.size(33.dp).background(hex(0x0b0b0b), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Glyph(Icons.Filled.IosShare, 15f, Color.White)
            }
        }
        Box(Modifier.at(140f, 83f)) { TBText("Simulated Trading Results", 12.4f, color = white(0.88f), tracking = -0.15f) }
        Row(Modifier.at(140f, 113f)) {
            TBText("+", 31f, color = white(0.45f), weight = FontWeight.SemiBold, tracking = -0.3f, modifier = Modifier.alignByBaseline())
            TBText("$${formatAmount(amount, 2)}", 31f, color = Color.White, weight = FontWeight.SemiBold, tracking = -0.3f, tabular = true, modifier = Modifier.alignByBaseline().offset(x = 1.dp))
        }
    }
}
