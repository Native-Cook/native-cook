package com.ngocdevv.nativecook.blocks.sheets.dockexpandsheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlin.math.max
import kotlin.math.min

private val NIGHT = Color(0xFF05060A)
private val SKY = arrayOf(
    0f to Color(0xFF0C1D45),
    0.38f to Color(0xFF173A72),
    0.72f to Color(0xFF2F6FA8),
    0.94f to Color(0xFF6FB7D2),
    1f to Color(0xFFA9D3DC),
)
private val GLOW = listOf(Color(0x004FA9C9), Color(0xFF4FA9C9), Color(0xFFE8B56A), Color(0x00D9A25B))
private val FIRST_LIGHT = Color(0xFFE8B56A)
private val TICK = Color(0xFF2C3850)
private val MAJOR = Color(0xFF3B4A68)
private val TICK_LIT = Color(0xFF5AA9FF)
private val LABEL = Color.White.copy(alpha = 0.42f)
private val VALUE = Color.White.copy(alpha = 0.62f)
private const val TICKS = 41
private const val SUN = 16f
private val ROW = 52.dp
private val HOURS = listOf("6 AM", "Noon", "6 PM", "12 AM", "6 AM")
private val ICONS = listOf(Icons.Outlined.WbSunny, Icons.Outlined.Camera, Icons.Outlined.FilterDrama, Icons.Outlined.DarkMode)

/**
 * The page behind the dock: a dawn sky with the sun on the arc it travels, a glow along the
 * horizon, a ruler of hours and the day's times under it. [horizon] is where the sky ends and the
 * horizon glows, and [rulerTop] the top of the ruler, both from the top of the screen; [ruler] is
 * read while drawing, 0 → 1 lights the ticks from left to right. Each row of [times] is a label
 * and a time.
 */
@Composable
internal fun SkyPage(width: Dp, horizon: Dp, rulerTop: Dp, ruler: () -> Float, times: List<List<String>>, modifier: Modifier = Modifier) {
    val pixels = LocalDensity.current.density
    // The sun's glow: React Native's shadow radius 10, a blur of sigma 5.
    val sunGlow = remember(pixels) { SoftRoundRect(Color.White.copy(alpha = 0.9f), sigma = 5f * pixels) }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            val w = width.toPx()
            val h = horizon.toPx()
            // The sun's path: a bell from below the horizon on the left, over the sun, down again on the right.
            val peak = h - 118f * d
            val low = h + 70f * d
            drawRect(NIGHT)
            drawRect(Brush.verticalGradient(colorStops = SKY, startY = 0f, endY = h), size = Size(w, h))
            // The first light, held just over the horizon.
            drawRect(
                Brush.verticalGradient(listOf(FIRST_LIGHT.copy(alpha = 0f), FIRST_LIGHT.copy(alpha = 0.28f)), startY = h - 60f * d, endY = h),
                topLeft = Offset(0f, h - 60f * d),
                size = Size(w, 60f * d),
            )
            drawRect(Brush.horizontalGradient(GLOW, startX = 0f, endX = w), topLeft = Offset(0f, h - d), size = Size(w, 2f * d))
            drawRect(
                Brush.verticalGradient(listOf(Color(0x2E4FA9C9), Color(0x0005060A)), startY = h + d, endY = h + 71f * d),
                topLeft = Offset(0f, h + d),
                size = Size(w, 70f * d),
            )

            val arc = Path().apply {
                moveTo(-30f * d, low)
                cubicTo(w * 0.12f, low, w * 0.26f, peak, w / 2f, peak)
                cubicTo(w * 0.74f, peak, w * 0.88f, low, w + 30f * d, low)
            }
            // The gradient spans the arc's own box, from its peak to its feet, as SVG's does.
            drawPath(
                arc,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.4f),
                    (118f / 208f) to Color.White.copy(alpha = 0.2f),
                    1f to Color.White.copy(alpha = 0.04f),
                    startY = peak,
                    endY = low,
                ),
                style = Stroke(d),
            )

            val center = Offset(w / 2f, peak)
            drawCircle(Color.White.copy(alpha = 0.08f), 36f * d, center)
            drawCircle(Color.White.copy(alpha = 0.22f), 20f * d, center)
            drawIntoCanvas { sunGlow.draw(it.nativeCanvas, center.x - SUN / 2f * d, center.y - SUN / 2f * d, SUN * d, SUN * d, SUN / 2f * d) }
            drawCircle(Color.White, SUN / 2f * d, center)

            val span = w - 40f * d
            val bottom = rulerTop.toPx() + 20f * d
            for (index in 0 until TICKS) {
                val x = 20f * d + index * (span - 2f * d) / (TICKS - 1)
                val tall = (if (index % 10 == 0) 20f else 12f) * d
                drawRoundRect(if (index % 10 == 0) MAJOR else TICK, Offset(x, bottom - tall), Size(2f * d, tall), CornerRadius(d))
                val lit = min(1f, max(0f, ruler() * (TICKS + 4) - index))
                if (lit > 0f) drawRoundRect(TICK_LIT, Offset(x, bottom - tall), Size(2f * d, tall), CornerRadius(d), alpha = lit)
            }
        }

        Row(
            Modifier.offset(x = 20.dp, y = rulerTop + 26.dp).width(width - 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (hour in HOURS) {
                Text(hour, style = HOUR)
            }
        }

        Column(Modifier.offset(x = 20.dp, y = rulerTop + 58.dp).width(width - 40.dp)) {
            times.forEachIndexed { index, (label, time) ->
                if (index > 0) Box(Modifier.fillMaxWidth().height((1f / 3f).dp).background(Color.White.copy(alpha = 0.08f)))
                Row(
                    Modifier.fillMaxWidth().height(if (index > 0) ROW - (1f / 3f).dp else ROW),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(ICONS[index % ICONS.size], null, tint = LABEL, modifier = Modifier.size(16.dp))
                        Text(label, color = LABEL, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(time, style = TIME)
                }
            }
        }
    }
}

private val HOUR = TextStyle(color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
private val TIME = TextStyle(
    color = VALUE,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.4).sp,
    fontFeatureSettings = "tnum",
)
