package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private val ProNames = listOf("@ScalpX", "RiskGuard", "TrendHunter")

private data class Slot(val x: Float, val y: Float, val angle: Float, val scale: Float, val opacity: Float, val blur: Float, val z: Float)

/** Where each bot stands when it is in front (the art is not centred the same way in each). */
private val FrontAnchors = listOf(207f to 276f, 215f to 270f, 211f to 272f)
private val BackLeft = Slot(103f, 220f, -9f, 0.8f, 0.48f, 1.3f, 1f)
private val BackRight = Slot(304f, 323f, 10f, 0.8f, 0.5f, 1.3f, 2f)
private const val EntryX = 212f
private const val EntryY = 250f

private fun slotFor(bot: Int, focus: Int): Slot =
    if (bot == focus) Slot(FrontAnchors[bot].first, FrontAnchors[bot].second, 0f, 1f, 1f, 0f, 3f)
    else if (bot == (focus + 2) % 3) BackLeft else BackRight

/**
 * Step 6: three frosted bot cards (182 × 236). The front one stands at x 121…303, y 159; the
 * other two lean back to the upper left and lower right.
 */
@Composable
internal fun ProScene(focus: Int, focusResponse: Float, select: (Int) -> Unit, reduced: Boolean) {
    Box(Modifier.fillMaxSize()) {
        for (bot in 0..2) ProSlot(bot, focus, focusResponse, reduced) { select(bot) }

        Column(Modifier.at(200.5f, 523f), horizontalAlignment = Alignment.CenterHorizontally) {
            PopIn(reduced, delay = 0.08f, from = 0.6f, damping = 0.65f, modifier = Modifier.offset(x = (-0.5).dp)) {
                HStack(5f) {
                    ProBadge()
                    TBText("Bots", 12f, color = Color.White, weight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Wipe(reduced, delay = 0.18f, modifier = Modifier.semantics { contentDescription = "More Bots" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TitleText("More B")
                    Mark(MarkStyle.Outline, 20.5f, modifier = Modifier.padding(horizontal = 1.2.dp).offset(y = 3.2.dp))
                    TitleText("ts")
                }
            }
            Reveal(reduced, delay = 0.34f, y = 8f, blur = 10f) { TitleText("More Possibilities.") }
            Spacer(Modifier.height(8.dp))
            Reveal(reduced, delay = 0.45f, y = 4f, blur = 4f) { TBText("Access more bots and trading strategies.", 14.2f, color = hex(0x7c7c7c), tracking = -0.15f) }
        }

        Box(Modifier.at(200.5f, 668.8f)) {
            Reveal(reduced, y = 16f, blur = 0f, duration = 0.6f) { Plan() }
        }
    }
}

@Composable
private fun TitleText(text: String) = HeadingText(text, weight = FontWeight.Normal, size = 32.3f, tracking = -0.6f)

@Composable
private fun ProSlot(bot: Int, focus: Int, focusResponse: Float, reduced: Boolean, onClick: () -> Unit) {
    val slot = slotFor(bot, focus)
    val front = bot == focus
    val x = remember { Animatable(if (reduced) slot.x else EntryX) }
    val y = remember { Animatable(if (reduced) slot.y else EntryY) }
    val angle = remember { Animatable(if (reduced) slot.angle else 0f) }
    val scale = remember { Animatable(if (reduced) slot.scale else if (front) 0.32f else 0.5f) }
    val opacity = remember { Animatable(0f) }
    val blur = remember { Animatable(slot.blur) }
    val entered = remember { booleanArrayOf(false) }
    LaunchedEffect(slot, reduced) {
        val first = !entered[0]
        entered[0] = true
        if (reduced) {
            x.snapTo(slot.x); y.snapTo(slot.y); angle.snapTo(slot.angle); scale.snapTo(slot.scale); opacity.snapTo(slot.opacity); blur.snapTo(slot.blur)
            return@LaunchedEffect
        }
        // Entry: the front card first, the others 0.14 s later. Later focus changes move all three at once.
        if (first && !front) delay(140)
        val response = if (first) 0.7f else focusResponse
        val damping = if (first) 0.84f else 0.86f
        coroutineScope {
            listOf(x to slot.x, y to slot.y, angle to slot.angle, scale to slot.scale, opacity to slot.opacity, blur to slot.blur)
                .map { (value, target) -> async { value.animateTo(target, swiftSpring(response, damping)) } }
                .awaitAll()
        }
    }
    Box(
        Modifier
            .zIndex(slot.z)
            .at(x.value, y.value)
            .graphicsLayer {
                alpha = opacity.value.coerceIn(0f, 1f)
                rotationZ = angle.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .then(if (blur.value > 0.05f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blur.value.dp) else Modifier),
    ) {
        PressScale(onClick, label = "Focus ${ProNames[bot]}", selected = front) { ProCard(bot) }
    }
}

@Composable
private fun ProBadge() {
    HStack(
        2f,
        Modifier.size(46.dp, 19.3.dp).background(Brush.horizontalGradient(listOf(hex(0xeec6dc), hex(0xf4d9c6))), RoundedCornerShape(50)),
        horizontalCenter = true,
    ) {
        // the SF Symbol diamond.fill: a rhombus, a little wider than tall
        Box(Modifier.size(6.dp).graphicsLayer { rotationZ = 45f; scaleX = 1.25f }.background(hex(0x141414), RoundedCornerShape(1.dp)))
        TBText("PRO", 14f, color = hex(0x141414), weight = FontWeight.Medium, tracking = -0.3f)
    }
}

/** A frosted card. Compose cannot blur what is behind a view, so the frost is a translucent grey. */
@Composable
private fun ProCard(kind: Int) {
    Box(
        Modifier.size(182.dp, 236.dp)
            .drawBehind { dropShadow(Color.Black.copy(alpha = 0.6f), 20.dp.toPx(), 14.dp.toPx(), 30.dp.toPx()) }
            .clip(RoundedCornerShape(30.dp))
            .drawBehind {
                drawRect(hex(0x2a2a2a, 0.72f))
                drawRect(Brush.verticalGradient(listOf(white(0.07f), white(0.13f))))
            }
            .drawWithContent {
                drawContent()
                val w = 1.2.dp.toPx()
                drawRoundRect(Brush.verticalGradient(listOf(white(0.22f), white(0.06f), white(0.14f))), Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(30.dp.toPx() - w / 2), style = Stroke(w))
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.offset(y = 6.dp).size(172.dp, 160.dp).clip(RoundedCornerShape(26.dp)).background(hex(0x0b0b0b)),
            contentAlignment = Alignment.Center,
        ) {
            DotField(spacing = 5f, opacity = 0.05f)
            when (kind) {
                0 -> Character(BotKind.Purple, 100f, saturation = 0.82f, frost = 0.4f)
                1 -> Character(BotKind.Green, 101f, Modifier.offset(2.dp, (-2).dp), saturation = 0.8f, brightness = -0.04f)
                else -> Character(BotKind.Red, 90f)
            }
            if (kind == 0) {
                Box(
                    Modifier.align(Alignment.TopStart).offset(12.dp, 8.dp).size(31.dp, 32.dp)
                        .background(white(0.05f), RoundedCornerShape(9.dp)).border(1.dp, white(0.16f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) { Glyph(Icons.Outlined.Lock, 13f, Color.White) }
            }
        }
        TBText(ProNames[kind], if (kind == 0) 12.5f else 11.6f, color = Color.White, weight = FontWeight.SemiBold, modifier = Modifier.offset(y = 177.dp))
        TBText(
            if (kind == 0) "Fast Trade Detection" else "Analyzes short-term price\nmovements for quick trade setups.",
            if (kind == 0) 10.3f else 9.5f, color = hex(0x7a7a7a), tracking = -0.1f,
            lineHeight = if (kind == 0) null else 10.4f,
            modifier = Modifier.offset(y = if (kind == 0) 201.dp else 194.dp),
        )
    }
}

@Composable
private fun Plan() {
    Row(
        Modifier.size(342.4.dp, 72.3.dp)
            .background(hex(0x161616), RoundedCornerShape(22.dp))
            .border(1.dp, white(0.06f), RoundedCornerShape(22.dp))
            .padding(horizontal = 15.2.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Pro, access to all AI trading bots, $9.99 per month" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TBText("Pro", 15.2f, color = Color.White, weight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            TBText("Access to all AI trading bots", 11.4f, color = hex(0x6c6c6c), tracking = -0.1f)
        }
        Row {
            TBText("$9.99", 16.3f, color = Color.White, weight = FontWeight.Medium, tracking = -0.3f, modifier = Modifier.alignByBaseline())
            TBText(" /Mo", 16.3f, color = hex(0x6c6c6c), weight = FontWeight.Medium, tracking = -0.3f, modifier = Modifier.alignByBaseline())
        }
    }
}

