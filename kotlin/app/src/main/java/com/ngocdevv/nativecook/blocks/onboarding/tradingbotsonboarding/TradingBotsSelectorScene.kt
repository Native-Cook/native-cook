package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.min

private val BotNames = listOf("RiskGuard", "@ScalpX", "TrendHunter")
private val BotLabels = listOf("Market Analysis", "Trading", "AI Signals")

/** -1 left, 0 centre, 1 right, so the three cards wrap around the selection. */
private fun slotOf(bot: Int, selection: Int): Int {
    val raw = (bot - selection + 3) % 3
    return if (raw == 2) -1 else raw
}

/**
 * Step 2: "Choose Your Trading" with strategy chips and a carousel of bot cards (241.7 × 300 at
 * x 79, y 328; neighbours 230 pt away at 72 %).
 */
@Composable
internal fun SelectorScene(selection: Int, choose: (Int) -> Unit, reduced: Boolean) {
    val carousel by rememberEntrance(reduced, spring = 0.7f to 0.92f)
    val currentSelection by rememberUpdatedState(selection)
    val currentChoose by rememberUpdatedState(choose)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.leading(19.3f, 181.2f)) {
            Wipe(reduced) { Box(Modifier.height(39.dp), contentAlignment = Alignment.CenterStart) { HeadingText("Choose Your") } }
            Wipe(reduced, delay = 0.16f) {
                HStack(7.5f, Modifier.height(39.dp)) {
                    HeadingText("Trading")
                    PopIn(reduced, delay = 0.32f, modifier = Modifier.offset(y = 3.dp)) { Mark(size = 33f) }
                }
            }
        }

        Box(Modifier.at(19.3f + 177f, 256.7f)) {
            Reveal(reduced, delay = 0.32f, y = 0f, blur = 4f) {
                HStack(8.3f) {
                    Chip("Trading", 94.7f, selection == 1, { choose(1) }) { Candles(16f, 15f) }
                    Chip("Market Analysis", 137.7f, selection == 0, { choose(0) }) { BellCurve(15f, 14f) }
                    Chip("AI Signals", 104f, selection == 2, { choose(2) }) { Bars(14f, 14f) }
                }
            }
        }

        Box(
            Modifier
                .offset(0.dp, (478f - 160f).dp)
                .size(402.dp, 320.dp)
                .graphicsLayer {
                    alpha = carousel.coerceAtMost(1f)
                    translationX = if (reduced) 0f else (1 - carousel) * 58.dp.toPx()
                }
                .pointerInput(Unit) {
                    var travel = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travel = 0f },
                        onHorizontalDrag = { _, amount -> travel += amount },
                        onDragEnd = {
                            val distance = travel / density
                            if (abs(distance) > 30f) currentChoose((currentSelection + if (distance < 0) 1 else 2) % 3)
                        },
                    )
                },
        ) {
            for (bot in 0..2) CarouselCard(bot, selection, reduced) { choose(bot) }
        }

        Box(Modifier.at(200f, 650.7f)) {
            Reveal(reduced, delay = 0.45f, y = 4f, blur = 4f) { TBText(BotNames[selection], 15.2f, color = hex(0xb4b4b4), tracking = -0.2f) }
        }
        Box(Modifier.at(200.2f, 690.3f)) {
            Reveal(reduced, delay = 0.5f, y = 0f, blur = 0f) {
                HStack(4f) { for (bot in 0..2) PageDot(bot == selection, reduced) }
            }
        }
        Box(Modifier.at(200f, 739.3f)) {
            Reveal(reduced, delay = 0.5f, y = 4f, blur = 4f) { TBText("Pick your strategy. Let AI handle the analysis", 14.4f, color = hex(0x7a7a7a), tracking = -0.15f) }
        }
    }
}

@Composable
private fun Chip(title: String, width: Float, active: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    PressScale(onClick, label = title, selected = active) {
        HStack(9f, Modifier.size(width.dp, 34.7.dp).background(hex(if (active) 0x151515 else 0x101010), RoundedCornerShape(50)), horizontalCenter = true) {
            Box(Modifier.graphicsLayer { scaleX = 0.85f; scaleY = 0.85f }) { icon() }
            TBText(title, 13f, color = hex(if (active) 0xb0b0b0 else 0x9a9a9a), tracking = -0.2f)
        }
    }
}

@Composable
private fun PageDot(active: Boolean, reduced: Boolean) {
    val width by animateDpAsState(if (active) 14.dp else 8.dp, if (reduced) snap() else swiftSpring(0.4f, 0.85f), label = "dot")
    Box(Modifier.size(width, 5.dp).background(hex(if (active) 0xdcdcdc else 0x343434), RoundedCornerShape(50)))
}

@Composable
private fun CarouselCard(bot: Int, selection: Int, reduced: Boolean, onClick: () -> Unit) {
    val slot = slotOf(bot, selection)
    val active = slot == 0
    val place = remember { Animatable(slot.toFloat()) }
    LaunchedEffect(slot, reduced) {
        if (reduced) place.snapTo(slot.toFloat()) else place.animateTo(slot.toFloat(), swiftSpring(0.5f, 0.86f))
    }
    Box(
        Modifier
            .zIndex(if (active) 2f else 1f)
            .offset((201f - 241.7f / 2).dp, 10.dp)
            .graphicsLayer {
                val away = min(1f, abs(place.value))
                translationX = place.value * 230.dp.toPx()
                translationY = away * 3.7f.dp.toPx()
                val scale = 1 - 0.28f * away
                scaleX = scale
                scaleY = scale
            },
    ) {
        PressScale(onClick, label = "Select ${BotNames[bot]}", selected = active) { SelectorCard(bot, active, reduced) }
    }
}

/** One bot in the carousel: an outer 241.7 × 300 shell, a dotted screen with the character, and a label pill. */
@Composable
private fun SelectorCard(bot: Int, active: Boolean, reduced: Boolean) {
    Panel(
        241.7f, 300f, 40f,
        fill = listOf(hex(0x1a1a1a), hex(0x141414)),
        stroke = listOf(white(0.07f), white(0.02f)),
        shadow = Triple(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), 18f, 10f),
    ) {
        Box(
            Modifier.offset(((241.7f - 223.3f) / 2).dp, 9.dp).size(223.3.dp, 213.dp)
                .clip(RoundedCornerShape(31.dp)).background(hex(0x0e0e0e))
                .border(1.dp, white(0.03f), RoundedCornerShape(31.dp)),
            contentAlignment = Alignment.Center,
        ) {
            DotField(spacing = 5f, opacity = 0.045f)
            Box(Modifier.graphicsLayer { alpha = if (active) 1f else 0.3f }) {
                if (bot == 1) {
                    Box(Modifier.offset(1.5.dp, (-2).dp)) { LivingOrb(reduced) }
                } else {
                    Character(if (bot == 0) BotKind.Green else BotKind.Red, if (bot == 0) 128f else 112f, saturation = if (active) 1f else 0.25f)
                }
            }
        }
        Box(Modifier.offset(y = 238.7.dp).width(241.7.dp), contentAlignment = Alignment.TopCenter) {
            Reveal(reduced, delay = 0.5f, y = 4f, blur = 4f) {
                HStack(
                    10f,
                    Modifier.height(37.3.dp)
                        .background(hex(0x0b0b0b), RoundedCornerShape(13.dp))
                        .border(1.dp, white(0.06f), RoundedCornerShape(13.dp))
                        .padding(start = 2.dp, end = 11.dp),
                ) {
                    Box(Modifier.size(32.7.dp, 31.3.dp).background(hex(0x1a1a1a), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Candles(18f, 17f) }
                    TBText(BotLabels[bot], 12.5f, color = hex(0x8a8a8a), tracking = -0.15f, align = TextAlign.Start,
                        modifier = Modifier.graphicsLayer { alpha = if (active) 1f else 0f })
                }
            }
        }
    }
}

