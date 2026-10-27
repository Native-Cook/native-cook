package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val FaceWidth = 306f
private const val FaceHeight = 136f
private const val MeterWidth = 286f

internal class AllocationState(
    val allocation: Float,
    val setAllocation: (Float) -> Unit,
    val risk: String,
    val setRisk: (String) -> Unit,
    val autoTrading: Boolean,
    val setAutoTrading: (Boolean) -> Unit,
    val maxLoss: Boolean,
    val setMaxLoss: (Boolean) -> Unit,
)

/**
 * Step 4: the orange TrendHunter card, tilted −5°, over a dark Risk Per Trade card tilted
 * +4.5°, above "Your Bots. / Your Control.".
 */
@Composable
internal fun AllocationScene(state: AllocationState, reduced: Boolean) {
    val riskIn by rememberEntrance(reduced, spring = 0.6f to 0.9f)
    val cardIn by rememberEntrance(reduced, delay = 0.22f, spring = 0.72f to 0.82f)
    val reveal = rememberSettle(0.49, 1.0, 0.7, 0.12, 0.6, 2.0, reduced, 0.35)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.at(200f, 466f).graphicsLayer {
            alpha = riskIn.coerceAtMost(1f)
            translationY = if (reduced) 0f else (1 - riskIn) * 26.dp.toPx()
            rotationZ = 4.5f
        }) { RiskCard(state.risk, state.setRisk) }

        Box(Modifier.at(198f, 301f).graphicsLayer {
            val p = if (reduced) 1f else cardIn
            alpha = cardIn.coerceAtMost(1f)
            translationY = (1 - p) * -46.dp.toPx()
            val scale = 1.06f - 0.06f * p
            scaleX = scale
            scaleY = scale
            rotationX = (1 - p) * 24f
            rotationZ = -11f + 6f * p
            cameraDistance = 12f * density
        }) { AllocationCard(state, (state.allocation * reveal).toFloat()) }

        Heading("Your Bots.", "Your Control.", reduced)
        Box(Modifier.at(200f, 681.7f)) {
            Reveal(reduced, delay = 0.38f, y = 6f, blur = 4f) { Caption("Monitor, configure, and manage your\ntrading agents.") }
        }
    }
}

/** Orange card: 318 × 184 frame, 306 × 136 face, two check pills under it. */
@Composable
private fun AllocationCard(state: AllocationState, shown: Float) {
    val setAllocation by rememberUpdatedState(state.setAllocation)
    Panel(
        318f, 184f, 31f,
        fill = listOf(hex(0x2e2e2e, 0.92f), hex(0x161616, 0.96f)),
        stroke = listOf(white(0.22f), white(0.06f), white(0.14f)), strokeWidth = 1.2f, diagonalStroke = true,
        shadow = Triple(Color.Black.copy(alpha = 0.85f), 22f, 18f),
    ) {
        Box(
            Modifier.offset(((318f - FaceWidth) / 2).dp, 5.dp).size(FaceWidth.dp, FaceHeight.dp)
                .clip(RoundedCornerShape(26.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(0f to hex(0xff5c00), 0.45f to hex(0xff7a06), 0.75f to hex(0xff9a24), 1f to hex(0xffc568)))
                    drawRect(Brush.radialGradient(listOf(hex(0xff4a00, 0.55f), hex(0xff4a00, 0f)), center = Offset(size.width, 0f), radius = 190.dp.toPx()))
                    drawRect(Brush.radialGradient(listOf(hex(0xffe3a8, 0.5f), hex(0xffe3a8, 0f)), center = Offset(size.width * 0.3f, size.height * 1.1f), radius = 150.dp.toPx()))
                }
                .drawWithContent {
                    drawContent()
                    val w = 1.dp.toPx()
                    drawRoundRect(Brush.verticalGradient(listOf(white(0.35f), white(0.05f), white(0.25f))), Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(26.dp.toPx() - w / 2), style = Stroke(w))
                },
        ) {
            Box(Modifier.at(37f, 38f).size(58.dp, 56.dp).background(hex(0x0d0d0d), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Mark(MarkStyle.White, 38f, rotation = -8f)
            }
            Column(Modifier.leading(82f, 36f)) {
                TBText("TrendHunter", 15.5f, color = Color.White, weight = FontWeight.Medium, align = TextAlign.Start)
                Spacer(Modifier.height(9.dp))
                TBText("BTC/USDT · Trend Analysis", 10.7f, color = white(0.55f), align = TextAlign.Start)
            }
            Box(Modifier.at(275f, 26f).size(33.dp, 31.dp).background(hex(0x0d0d0d), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { HexNut(15f, 16f) }
            Box(Modifier.leading(15f, 86f)) { TBText("Capital Allocation", 15f, color = Color.White, weight = FontWeight.Medium) }
            Box(Modifier.at(276f, 84f).size(33.dp, 18.dp).background(white(0.2f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                TBText("${shown.roundToInt()}%", 12.5f, color = Color.White, weight = FontWeight.SemiBold, tabular = true)
            }
            Meter(
                shown,
                Modifier.at(16f + 143f, 110f)
                    .semantics {
                        contentDescription = "Capital allocation"
                        progressBarRangeInfo = ProgressBarRangeInfo(state.allocation, 0f..100f)
                        setProgress { setAllocation(it); true }
                    }
                    .pointerInput(Unit) {
                        val width = MeterWidth.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            setAllocation(down.position.x / width * 100f)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                setAllocation(change.position.x / width * 100f)
                                change.consume()
                            }
                        }
                    },
            )
        }
        Box(Modifier.offset(y = 150.dp).width(318.dp), contentAlignment = Alignment.TopCenter) {
            HStack(8f, Modifier.offset(x = (-4).dp)) {
                CheckPill("Auto-Trading", 98f, state.autoTrading) { state.setAutoTrading(!state.autoTrading) }
                CheckPill("Max Daily Loss", 104f, state.maxLoss) { state.setMaxLoss(!state.maxLoss) }
            }
        }
    }
}

/** White bars up to the allocation, darker amber bars after it, over a soft white glow. */
@Composable
private fun Meter(value: Float, modifier: Modifier) {
    Canvas(modifier.size(MeterWidth.dp, 25.dp)) {
        val count = 83
        val pitch = size.width / count
        val filled = (value / 100f * count).roundToInt()
        drawIntoCanvas { canvas ->
            val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = white(0.35f).toArgb()
                maskFilter = BlurMaskFilter(4.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
            val r = 11.dp.toPx()
            canvas.nativeCanvas.drawRoundRect(0f, 1.5.dp.toPx(), size.width * value / 100f, 23.5.dp.toPx(), r, r, glow)
        }
        for (index in 0 until count) {
            val width = pitch * 0.68f
            drawRoundRect(
                if (index < filled) Color.White else hex(0x8a3a00, 0.55f),
                topLeft = Offset(index * pitch, 0f), size = Size(width, size.height), cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}

@Composable
private fun CheckPill(title: String, width: Float, selected: Boolean, toggle: () -> Unit) {
    PressScale(toggle, label = title, selected = selected) {
        HStack(
            7f,
            Modifier.size(width.dp, 27.dp).background(hex(0x1a1a1a), RoundedCornerShape(50)).border(1.dp, white(0.07f), RoundedCornerShape(50)),
            horizontalCenter = true,
        ) {
            Glyph(Icons.Filled.Check, 11f, hex(0xb4b4b4), Modifier.graphicsLayer { alpha = if (selected) 1f else 0.15f })
            TBText(title, 12f, color = hex(0xe6e6e6), tracking = -0.15f)
        }
    }
}

/** Risk card (304 × 130). */
@Composable
private fun RiskCard(risk: String, setRisk: (String) -> Unit) {
    Panel(
        304f, 130f, 28f,
        fill = listOf(hex(0x040404), hex(0x040404)),
        stroke = listOf(hex(0x3a3a3a), hex(0x262626)), strokeWidth = 3f,
        shadow = Triple(Color.Black.copy(alpha = 0.6f), 14f, 10f),
    ) {
        Box(Modifier.leading(14f, 21f)) { TBText("Risk Per Trade", 13.5f, color = hex(0x6c6c6c)) }
        Box(Modifier.leading(14f, 41f)) { TBText("Maximum Planned Risk Per Position", 10.4f, color = hex(0x2c2c2c)) }
        HStack(9f, Modifier.at(152f, 84f)) {
            for (choice in TradingBotsRisks) {
                val selected = risk == choice
                PressScale({ setRisk(choice) }, label = "Risk per trade $choice", selected = selected) {
                    Box(
                        Modifier.size(87.dp, 38.dp)
                            .background(hex(if (selected) 0x121212 else 0x050505), RoundedCornerShape(12.dp))
                            .border(1.dp, white(if (selected) 0.12f else 0.06f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { TBText(choice, 10.5f, color = Color.White, weight = FontWeight.Medium) }
                }
            }
        }
    }
}
