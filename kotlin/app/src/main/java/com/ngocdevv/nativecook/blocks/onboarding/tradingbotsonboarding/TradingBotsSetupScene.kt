package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R

/** Blurred dashboard words behind the phone: text, centre, rotation, pill. */
private data class FloatingLabel(val text: String, val x: Float, val y: Float, val angle: Float, val pill: Boolean)

private val Labels = listOf(
    FloatingLabel("Risk Settings", 204f, 70f, 0f, false),
    FloatingLabel("Overview", 330f, 86f, 5f, true),
    FloatingLabel("Configuration", 80f, 104f, -6f, false),
    FloatingLabel("Win Rate", 205f, 143f, 5f, false),
    FloatingLabel("Risk Settings", 328f, 151f, 5f, true),
    FloatingLabel("Risk Settings", 78f, 167f, -5f, false),
    FloatingLabel("Protected", 204f, 196f, 3f, false),
    FloatingLabel("Risk Settings", 335f, 205f, 4f, false),
    FloatingLabel("Overview", 64f, 225f, -4f, false),
)

/**
 * Step 3: a miniature phone (316.7 pt wide at x 41.7, y 196) rises over a field of blurred
 * dashboard labels, above "Ai Bots, / Ready To Go".
 */
@Composable
internal fun SetupScene(reduced: Boolean, configure: () -> Unit, overview: () -> Unit) {
    val labels by rememberEntrance(reduced, delay = 0.15f, duration = 0.9f, easing = EaseOut)
    val phone by rememberEntrance(reduced, spring = 0.8f to 0.92f)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = labels }
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(1.dp) else Modifier),
        ) {
            for (label in Labels) {
                HStack(
                    7f,
                    Modifier.at(label.x, label.y).rotate(label.angle)
                        .then(if (label.pill) Modifier.background(white(0.025f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 9.dp) else Modifier),
                ) {
                    if (label.pill) Glyph(Icons.Filled.Contrast, 15f, white(0.19f))
                    TBText(label.text, 15f, color = white(0.19f), weight = FontWeight.Medium)
                }
            }
        }

        Box(Modifier.at(200f, 196f + 190f).graphicsLayer {
            alpha = phone.coerceAtMost(1f)
            translationY = if (reduced) 0f else (1 - phone) * 70.dp.toPx()
        }) { MiniPhone(configure, overview) }

        Column(Modifier.at(200f, 603.5f), horizontalAlignment = Alignment.CenterHorizontally) {
            HStack(12f, Modifier.height(39.dp).offset(x = (-2.5).dp)) {
                PopIn(reduced, delay = 0.05f, from = 0.5f, damping = 0.62f, modifier = Modifier.offset(y = 1.dp)) {
                    Image(painterResource(R.drawable.tradingbotsonboarding_toggle_tile), null, Modifier.size(45.dp))
                }
                Reveal(reduced, delay = 0.15f, y = 6f) { HeadingText("Ai Bots,") }
            }
            Reveal(reduced, delay = 0.32f, y = 10f, blur = 10f, modifier = Modifier.offset(y = (-3).dp)) { HeadingText("Ready To Go") }
        }

        Box(Modifier.at(200f, 681f)) {
            Reveal(reduced, delay = 0.4f, y = 6f, blur = 4f) { Caption("Fine-tune your bots, build strategies, and manage trades\neffortlessly") }
        }
    }
}

/** A phone outline at 316.7 × 380 holding a bot dashboard; it fades out downward. */
@Composable
private fun MiniPhone(configure: () -> Unit, overview: () -> Unit) {
    Box(
        Modifier.size(316.7.dp, 380.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val r = CornerRadius(54.dp.toPx())
                drawRoundRect(Color.Black, cornerRadius = r)
                val w = 2.4.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(listOf(hex(0x4a4a4a), hex(0x2c2c2c))),
                    topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(54.dp.toPx() - w / 2), style = Stroke(w),
                )
            }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.White, 0.74f to Color.White, 0.95f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
    ) {
        Box(Modifier.at(56.6f, 33f)) { TBText("9:41", 13f, color = Color.White, weight = FontWeight.SemiBold) }
        Box(Modifier.at(157f, 33.3f).size(90.7.dp, 20.dp).background(Color.Black, RoundedCornerShape(50)).border(1.6.dp, hex(0x3a3a3a), RoundedCornerShape(50)))
        HStack(5f, Modifier.at(256f, 33f)) {
            Glyph(Icons.Filled.SignalCellularAlt, 14f, Color.White)
            Glyph(Icons.Filled.Wifi, 14f, Color.White)
            Glyph(Icons.Filled.BatteryFull, 14f, Color.White, Modifier.rotate(90f))
        }
        Column(Modifier.offset(22.3.dp, 59.dp)) {
            Column(
                Modifier.offset(x = (-4).dp).background(hex(0x101010), RoundedCornerShape(28.dp)).padding(4.dp),
            ) {
                PressScale(configure, label = "Configure AlphaMind") {
                    HStack(
                        8.3f,
                        Modifier.size(259.3.dp, 75.dp)
                            .background(hex(0x090909), RoundedCornerShape(24.dp))
                            .border(1.6.dp, hex(0x2a2a2a), RoundedCornerShape(24.dp))
                            .padding(horizontal = 11.dp),
                    ) {
                        Box(Modifier.size(49.7.dp, 50.7.dp).background(hex(0x1e1e1e), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Character(BotKind.Avatar, 35f)
                        }
                        Column(Modifier.weight(1f)) {
                            TBText("AlphaMind", 11.8f, color = Color.White, weight = FontWeight.Medium, align = TextAlign.Start)
                            Spacer(Modifier.height(6.dp))
                            TBText("Scalping · BTC/USDT", 8.9f, color = hex(0x404040), align = TextAlign.Start)
                        }
                        Glyph(Icons.AutoMirrored.Filled.ArrowForward, 17f, Color.White, Modifier.padding(end = 6.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                DashboardControls(configure, overview, width = 259.3f, fontSize = 8.8f)
            }
            Spacer(Modifier.height(10.5.dp))
            DashboardRow("Live Execution Monitor", Icons.Filled.MonitorHeart, overview)
            Spacer(Modifier.height(10.5.dp))
            DashboardRow("Performance Overview", Icons.Filled.Assessment, overview)
            Spacer(Modifier.height(10.5.dp))
            DashboardRow("View Trading Logs", Icons.Filled.Description, overview)
        }
    }
}

@Composable
private fun DashboardRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    PressScale(onClick, label = title, modifier = Modifier.offset(x = (-4).dp)) {
        HStack(
            8f,
            Modifier.size(261.dp, 56.7.dp)
                .background(hex(0x0e0e0e), RoundedCornerShape(20.dp))
                .border(1.dp, white(0.03f), RoundedCornerShape(20.dp))
                .padding(start = 14.dp),
        ) {
            Box(Modifier.width(15.dp), contentAlignment = Alignment.Center) { Glyph(icon, 12f, hex(0x8a8a8a)) }
            TBText(title, 10.3f, color = white(0.82f), weight = FontWeight.SemiBold, tracking = -0.15f)
        }
    }
}

/** Risk Overview and Configure Bot, shared by the setup phone and the results stack. */
@Composable
internal fun DashboardControls(configure: () -> Unit, overview: () -> Unit, width: Float = 278f, height: Float = 46f, fontSize: Float = 10f) {
    Row(Modifier.width(width.dp)) {
        Control("Risk Overview", overview, height, fontSize, Modifier.weight(1f)) { BellCurve(fontSize * 1.45f, fontSize * 1.45f, Color.White, 1.2f) }
        Spacer(Modifier.width(7.dp))
        Control("Configure Bot", configure, height, fontSize, Modifier.weight(1f)) { SliderGlyph(fontSize * 1.6f, fontSize * 0.75f) }
    }
}

@Composable
private fun Control(title: String, onClick: () -> Unit, height: Float, fontSize: Float, modifier: Modifier, icon: @Composable () -> Unit) {
    PressScale(onClick, label = title, modifier = modifier) {
        HStack(
            8f,
            Modifier.height(height.dp).fillMaxWidth()
                .background(hex(0x050505), RoundedCornerShape(17.dp))
                .border(1.dp, white(0.05f), RoundedCornerShape(17.dp)),
            horizontalCenter = true,
        ) {
            icon()
            TBText(title, fontSize, color = hex(0xe2e2e2), weight = FontWeight.Medium, tracking = -0.2f)
        }
    }
}
