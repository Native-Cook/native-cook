package com.ngocdevv.nativecook.blocks.sheets.widgetguidesheet

import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal val PHONE_HEIGHT = 520.dp
private val PHONE_COLORS = listOf(Color(0xFF3446B4), Color(0xFF4A5CC4), Color(0xFF6A7FDC))
private const val NOTCH_WIDTH = 96f
private const val NOTCH_HEIGHT = 24f

private const val ICON = 58f
private const val ICON_LEFT = 22f
private const val ICON_TOP = 54f
private const val ROW_PITCH = 92f
private const val SLOT_TOP = 246f
private const val SLOT_HEIGHT = 138f
private const val SLOT_INSET = 14f
private const val DOCK_HEIGHT = 72f
private const val DOCK_ICON = 50f
private const val PHONE_HEIGHT_VALUE = 520f
private const val DASH_PERIOD = 8f
private val DASH = floatArrayOf(6f, 4f)

private class AppIconSpec(val color: Color, val glyph: ImageVector, val label: String = "")

private val ICONS = listOf(
    AppIconSpec(Color(0xFFE84C6A), Icons.Filled.Favorite, "Health"),
    AppIconSpec(Color(0xFFF2634F), Icons.Filled.Cloud, "Weather"),
    AppIconSpec(Color(0xFFF4B23E), Icons.Filled.Eco, "Garden"),
    AppIconSpec(Color(0xFF4BD964), Icons.Filled.Flag, "Goals"),
    AppIconSpec(Color(0xFF3CCFA8), Icons.Filled.ChangeHistory, "Trails"),
    AppIconSpec(Color(0xFF3AA9E0), Icons.Filled.Star, "Stars"),
    AppIconSpec(Color(0xFF5D6CC8), Icons.Filled.WbSunny, "Sun"),
    AppIconSpec(Color(0xFF7A55D9), Icons.Filled.DarkMode, "Sleep"),
)
private val DOCK = listOf(
    AppIconSpec(Color(0xFF34C759), Icons.Filled.Call),
    AppIconSpec(Color(0xFF0A84FF), Icons.Filled.Explore),
    AppIconSpec(Color(0xFF30D158), Icons.Filled.ChatBubble),
    AppIconSpec(Color(0xFFFF9F0A), Icons.Filled.MusicNote),
)
// The widget covers the two icons at the left of each row.
private val COVERED = listOf(0, 1, 4, 5)

private val TILES = listOf(Color(0xFF3FD6A8), Color(0xFFF0476B), Color(0xFFF4B23E), Color(0xFF3CC4E0))
private val TILE_DAYS = listOf("21", "7", "1", "9")
private val GREEN = Color(0xFF2FCF8A)
private val GREY = Color(0xFFE9E9EC)
private val INK = Color(0xFF14141F)
private val MUTE = Color(0xFF8A8A96)
private val PINK = Color(0xFFF0476B)
private val AMBER = Color(0xFFF4A000)
private val CYAN = Color(0xFF1FB3D6)

private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

/** Smooth 0 → 1 between [from] and [to]. */
private fun ramp(value: Float, from: Float, to: Float): Float {
    val t = min(1f, max(0f, (value - from) / (to - from)))
    return t * t * (3f - 2f * t)
}

/** 0 → 1 with a small overshoot, for a pop. */
private fun pop(t: Float): Float {
    val c = 1.9f
    return 1f + (c + 1f) * (t - 1f).pow(3) + c * (t - 1f).pow(2)
}

internal class Labels(
    val search: String = "Search",
    val countdown: String = "Daybreak",
    val until: String = "days until",
    val event: String = "Mia's birthday",
    val trip: String = "Road trip",
    val tomorrow: String = "tomorrow",
    val firstName: String = "Graduation day",
    val firstDate: String = "in 7 days",
    val secondName: String = "Lena's birthday",
    val secondDate: String = "in 9 days",
    val done: String = "Done",
    val edit: String = "Edit Widget",
)

/**
 * The miniature home screen inside the sheet. Everything here is a function of [step], read while
 * drawing, so the slides cross-fade into each other, run backwards, and stop wherever a swipe
 * leaves them. [dash] is 0 → 8 every second, the march of the dashes round the empty slot;
 * [shimmer] is 0 → 1, a light that crosses the picker's tiles once.
 */
@Composable
internal fun Scene(width: Dp, step: () -> Float, dash: () -> Float, shimmer: () -> Float, labels: Labels, modifier: Modifier = Modifier) {
    val slotWidth = width.value - SLOT_INSET * 2f
    val pitch = (width.value - ICON_LEFT * 2f - ICON) / 3f
    val widgetWidth = pitch + ICON
    val widgetHeight = ROW_PITCH + ICON
    val pickerTop = 70f
    val tileWidth = (width.value - 12f - 28f - 12f) / 2f

    Box(modifier.size(width, PHONE_HEIGHT).clip(RoundedCornerShape(30.dp))) {
        // Under the blur: the home screen and its icons, the empty slot and the list widget.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = 6f * density * ramp(step(), 3.1f, 3.9f)
                        renderEffect = if (radius > 0.01f) BlurEffect(radius, radius, TileMode.Decal) else null
                    }
                },
        ) {
            Wallpaper(width, labels)

            ICONS.forEachIndexed { index, icon ->
                Column(
                    Modifier
                        .offset(x = (ICON_LEFT - 6f + (index % 4) * pitch).dp, y = (ICON_TOP + (index / 4) * ROW_PITCH).dp)
                        .width((ICON + 12f).dp)
                        .graphicsLayer {
                            val s = step()
                            val dim = lerp(1f, 0.75f, ramp(s, 0.4f, 1f))
                            alpha = if (index in COVERED) dim * (1f - ramp(s, 2.55f, 2.95f)) else dim
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AppIcon(icon, ICON, 15f, 27f, ICON_LIGHT)
                    Text(icon.label, color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }

            Canvas(
                Modifier
                    .offset(x = SLOT_INSET.dp, y = SLOT_TOP.dp)
                    .size(slotWidth.dp, SLOT_HEIGHT.dp)
                    .graphicsLayer { alpha = 1f - ramp(step(), 2.3f, 2.7f) },
            ) {
                val d = density
                drawRoundRect(
                    Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(1f * d, 1f * d),
                    size = Size(size.width - 2f * d, size.height - 2f * d),
                    cornerRadius = CornerRadius(18f * d),
                    style = Stroke(1.5f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH[0] * d, DASH[1] * d), (DASH_PERIOD - dash()) * d)),
                )
            }

            Column(
                Modifier
                    .offset(x = SLOT_INSET.dp, y = SLOT_TOP.dp)
                    .size(slotWidth.dp, SLOT_HEIGHT.dp)
                    .graphicsLayer {
                        val t = ramp(step(), 2.65f, 3f)
                        alpha = t
                        translationY = lerp(24f, 0f, t) * density
                    }
                    .dropShadow(LIST_SHAPE, Shadow(radius = 12.dp, color = Color.Black, offset = DpOffset(0.dp, 6.dp), alpha = 0.18f))
                    .background(Color.White, LIST_SHAPE)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(labels.trip, color = AMBER, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Text(labels.tomorrow, color = INK, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(28.dp).background(Color(0xFFFFF2D6), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Star, null, tint = AMBER, modifier = Modifier.size(12.dp))
                    }
                }
                ListRow(labels.firstName, labels.firstDate, PINK)
                ListRow(labels.secondName, labels.secondDate, CYAN)
            }
        }

        // Before Android 12 there is no blur: the picture fades towards white instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.4f * ramp(step(), 3.1f, 3.9f) }.background(Color.White))
        }

        Column(
            Modifier
                .offset(x = ICON_LEFT.dp, y = ICON_TOP.dp)
                .size(widgetWidth.dp, widgetHeight.dp)
                .graphicsLayer {
                    val t = ramp(step(), 2.55f, 2.95f)
                    alpha = t
                    scaleX = lerp(1.15f, 1f, t)
                    scaleY = lerp(1.15f, 1f, t)
                }
                .dropShadow(GREEN_SHAPE, Shadow(radius = 14.dp, color = Color(0xFF0B3B26), offset = DpOffset(0.dp, 8.dp), alpha = 0.35f))
                .background(GREEN, GREEN_SHAPE)
                .padding(14.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Column {
                    Text("21", color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Text(labels.until, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(labels.event, Modifier.padding(top = 2.dp), color = Color.White, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                Box(Modifier.align(Alignment.TopEnd).size(26.dp).background(Color.White, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CardGiftcard, null, tint = GREEN, modifier = Modifier.size(14.dp))
                }
            }
        }

        Column(
            Modifier
                .offset(x = ICON_LEFT.dp, y = (ICON_TOP + widgetHeight + 8f).dp)
                .width(196.dp)
                .graphicsLayer {
                    val t = ramp(step(), 3.4f, 3.95f)
                    alpha = min(1f, t * 3f)
                    scaleX = pop(t)
                    scaleY = pop(t)
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .dropShadow(MENU_SHAPE, Shadow(radius = 18.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.25f))
                .background(Color.White, MENU_SHAPE)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(labels.edit, color = INK, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold)
                Icon(Icons.Outlined.Info, null, tint = MUTE, modifier = Modifier.size(16.dp))
            }
            // The rule runs to the menu's edges, past the padding.
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val bleed = 12.dp.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + bleed * 2, (1f / 3f).dp.roundToPx().coerceAtLeast(1)))
                        layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
                    }
                    .background(Color(0xFFE2E2E8)),
            )
            MenuRow("Event") { Text(labels.event, color = PINK, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold) }
            MenuRow("Show days") {
                Box(Modifier.size(36.dp, 22.dp).background(PINK, CircleShape).padding(2.dp), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.size(18.dp).background(Color.White, CircleShape))
                }
            }
        }

        Box(
            Modifier
                .offset(x = 14.dp, y = 10.dp)
                .size(42.dp, 26.dp)
                .graphicsLayer {
                    val s = step()
                    val fade = 1f - ramp(s, 2.6f, 2.9f)
                    val press = 1f - 0.1f * max(0f, 1f - abs(s - 1.45f) * 6f)
                    val scale = max(0f, pop(ramp(s, 0.35f, 1f)) * fade * press)
                    alpha = ramp(s, 0.35f, 0.6f) * fade
                    scaleX = scale
                    scaleY = scale
                }
                .dropShadow(CircleShape, BUTTON_SHADOW)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, null, tint = INK, modifier = Modifier.size(18.dp))
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-14).dp, y = 10.dp)
                .height(26.dp)
                .graphicsLayer {
                    val s = step()
                    alpha = ramp(s, 2.7f, 3f) * (1f - ramp(s, 3.2f, 3.5f))
                }
                .dropShadow(CircleShape, BUTTON_SHADOW)
                .background(Color.White, CircleShape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(labels.done, color = INK, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold)
        }

        // The picker rises inside the picture, in a clipped container of its own.
        Box(
            Modifier
                .offset(x = 6.dp, y = pickerTop.dp)
                .size(width - 12.dp, PHONE_HEIGHT)
                .graphicsLayer {
                    val s = step()
                    val up = 1f - (1f - ramp(s, 1.45f, 2.05f)).pow(3)
                    val down = ramp(s, 2.55f, 3f)
                    translationY = (lerp(PHONE_HEIGHT_VALUE - pickerTop, 0f, up) + down * (PHONE_HEIGHT_VALUE - pickerTop)) * density
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF6F6F8))
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(38.dp).background(GREY, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Search, null, tint = MUTE, modifier = Modifier.size(15.dp))
                    Text(labels.countdown, color = MUTE, fontSize = 15.sp, lineHeight = 19.sp)
                }
                for (rowStart in 0..2 step 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (index in rowStart..rowStart + 1) {
                            Column(
                                Modifier.size(tileWidth.dp, 132.dp).background(TILES[index], RoundedCornerShape(20.dp)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(TILE_DAYS[index], Modifier.padding(bottom = 4.dp), color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
                                for (fraction in listOf(0.7f, 0.5f)) {
                                    Box(Modifier.fillMaxWidth(fraction).height(7.dp).background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(3.5.dp)))
                                }
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .width(140.dp)
                    .fillMaxSize()
                    .graphicsLayer { translationX = lerp(-width.value, width.value, shimmer()) * density }
                    .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)))),
            )
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(NOTCH_WIDTH.dp, NOTCH_HEIGHT.dp)
                .background(Color.White, RoundedCornerShape(bottomStart = (NOTCH_HEIGHT / 2f).dp, bottomEnd = (NOTCH_HEIGHT / 2f).dp)),
        )
    }
}

/** The home screen's wallpaper, with soft light and shade so it reads as a picture and not a fill, and its search pill and dock. */
@Composable
private fun Wallpaper(width: Dp, labels: Labels) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(PHONE_COLORS))) {
        Box(Modifier.offset((-80).dp, (-60).dp).size(260.dp).background(Color(0xFF8FA2FF).copy(alpha = 0.28f), CircleShape))
        Box(Modifier.offset(width + 90.dp - 280.dp, 150.dp).size(280.dp).background(Color(0xFF25319A).copy(alpha = 0.35f), CircleShape))
        Box(Modifier.offset(60.dp, (PHONE_HEIGHT_VALUE + 120f - 240f).dp).size(240.dp).background(Color(0xFFA7B4FF).copy(alpha = 0.2f), CircleShape))

        Row(
            Modifier
                .offset(x = (width - 92.dp) / 2, y = (PHONE_HEIGHT_VALUE - 12f - DOCK_HEIGHT - 12f - 28f).dp)
                .size(92.dp, 28.dp)
                .background(Color.White.copy(alpha = 0.22f), CircleShape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Text(labels.search, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier
                .offset(x = SLOT_INSET.dp, y = (PHONE_HEIGHT_VALUE - 12f - DOCK_HEIGHT).dp)
                .size(width - (SLOT_INSET * 2f).dp, DOCK_HEIGHT.dp)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            for (item in DOCK) AppIcon(item, DOCK_ICON, 13f, 24f, DOCK_LIGHT)
        }
    }
}

/** A rounded square app icon with a white glyph and a light across its top. */
@Composable
private fun AppIcon(icon: AppIconSpec, size: Float, radius: Float, glyph: Float, light: Brush) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(icon.color)
            .background(light),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon.glyph, null, tint = Color.White, modifier = Modifier.size(glyph.dp))
    }
}

@Composable
private fun ListRow(name: String, date: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), color = color, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(date, color = INK, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MenuRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MUTE, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
        value()
    }
}

private val LIST_SHAPE = RoundedCornerShape(24.dp)
private val GREEN_SHAPE = RoundedCornerShape(26.dp)
private val MENU_SHAPE = RoundedCornerShape(16.dp)
private val BUTTON_SHADOW = Shadow(radius = 4.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.15f)
private val ICON_LIGHT = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f), 0.6f to Color.White.copy(alpha = 0f))
private val DOCK_LIGHT = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)))
