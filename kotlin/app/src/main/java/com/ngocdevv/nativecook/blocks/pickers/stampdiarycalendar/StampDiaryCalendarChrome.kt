package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar.StampDiaryCalendarMotion as M
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * The pin's smoked glass: a dark blur of what is behind (API 31+: the recorded stamp layer, blurred),
 * under a dark tint. Below API 31 only the tint.
 */
@Composable
internal fun StampDiaryCalendarGlass(
    layer: GraphicsLayer,
    left: Float,
    top: Float,
    space: StampDiaryCalendarSpace,
    fill: Color,
    modifier: Modifier = Modifier,
) {
    val blurPx = with(LocalDensity.current) { StampDiaryCalendarTheme.GLASS_BLUR_DP.toFloat().dp.toPx() }
    Box(modifier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                        clip = true
                    }
                    .drawBehind {
                        translate(-left, -top) {
                            drawLayer(layer)
                        }
                    },
            )
        }
        Box(Modifier.fillMaxSize().background(fill))
    }
}

/** The two header circles: close (x) at the left, month menu (...) at the right. */
@Composable
internal fun StampDiaryCalendarHeader(
    space: StampDiaryCalendarSpace,
    layer: GraphicsLayer,
    onClose: () -> Unit,
    onMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = StampDiaryCalendarTheme
    val s = space.s
    val topPx = space.y(t.HEADER_CENTER_Y - t.HEADER_SIZE / 2).toFloat()
    val sizePx = (t.HEADER_SIZE * s).toFloat()
    Box(modifier.fillMaxSize()) {
        HeaderCircle(space, layer, ((t.HEADER_CENTER_INSET - t.HEADER_SIZE / 2) * s).toFloat(), topPx, "Close", onClose) {
            val g = (t.CLOSE_GLYPH / 2 * s).toFloat()
            val c = sizePx / 2
            val stroke = Stroke((t.CLOSE_STROKE * s).toFloat(), cap = StrokeCap.Round)
            drawLine(StampDiaryCalendarColors.headerIcon, Offset(c - g, c - g), Offset(c + g, c + g), stroke.width, StrokeCap.Round)
            drawLine(StampDiaryCalendarColors.headerIcon, Offset(c + g, c - g), Offset(c - g, c + g), stroke.width, StrokeCap.Round)
        }
        HeaderCircle(space, layer, ((t.REF_WIDTH - t.HEADER_CENTER_INSET - t.HEADER_SIZE / 2) * s).toFloat(), topPx, "Choose month", onMonth) {
            val r = (t.DOT / 2 * s).toFloat()
            for (i in -1..1) {
                drawCircle(StampDiaryCalendarColors.headerIcon, r, Offset(((22 + i * t.DOT_GAP) * s).toFloat(), (21.5 * s).toFloat()))
            }
        }
    }
}

@Composable
private fun HeaderCircle(
    space: StampDiaryCalendarSpace,
    layer: GraphicsLayer,
    left: Float,
    top: Float,
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val t = StampDiaryCalendarTheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(100), label = "press")
    val size = pxToDp(t.HEADER_SIZE * space.s)
    Box(
        Modifier
            .offset { IntOffset(left.toInt(), top.toInt()) }
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(pxToDp(t.HEADER_RADIUS * space.s)))
            .clickable(interaction, null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        StampDiaryCalendarGlass(layer, left, top, space, StampDiaryCalendarColors.headerFill, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) { icon() }
    }
}

/** Year and month, right aligned on the pin's baselines. Under the stamps: a stamp may fly over it. */
@Composable
internal fun StampDiaryCalendarTitle(
    space: StampDiaryCalendarSpace,
    month: M.Month,
    onMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = StampDiaryCalendarTheme
    val s = space.s
    val name = t.MONTH_NAMES[month.month - 1]
    val regular = remember { Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
    val bold = remember { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    Canvas(modifier.fillMaxSize()) {
        val right = size.width - (t.TITLE_INSET * s).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = regular
        paint.textSize = (t.YEAR_SIZE * s).toFloat()
        paint.color = StampDiaryCalendarColors.year.toArgbInt()
        drawIntoCanvas { it.nativeCanvas.drawText(month.year.toString(), right, space.y(t.YEAR_BASELINE).toFloat(), paint) }
        paint.typeface = bold
        paint.textSize = (t.MONTH_SIZE * s).toFloat()
        paint.color = StampDiaryCalendarColors.month.toArgbInt()
        drawIntoCanvas { it.nativeCanvas.drawText(name, right, space.y(t.MONTH_BASELINE).toFloat(), paint) }
    }
    Box(
        Modifier
            .offset { IntOffset((space.widthPx - (t.TITLE_INSET + 150) * s).toInt(), space.y(t.YEAR_BASELINE - t.YEAR_SIZE).toInt()) }
            .size(pxToDp(150 * s), pxToDp((t.MONTH_BASELINE + 10 - (t.YEAR_BASELINE - t.YEAR_SIZE)) * s))
            .clickable(role = Role.Button, onClick = onMonth)
            .semantics { contentDescription = "$name ${month.year}. Choose month." },
    )
}

private fun Color.toArgbInt(): Int = this.toArgb()

/**
 * The month menu (title or ...): a small smoked-glass card under the ... circle with the
 * neighbouring months and, away from May 2026, a way back. Reduce Motion: a fade only.
 */
@Composable
internal fun StampDiaryCalendarMenu(
    space: StampDiaryCalendarSpace,
    layer: GraphicsLayer,
    month: M.Month,
    open: Boolean,
    reduceMotion: Boolean,
    onClose: () -> Unit,
    onPick: (M.Month) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = StampDiaryCalendarTheme
    val s = space.s
    val shown by animateFloatAsState(
        if (open) 1f else 0f,
        tween(if (open) t.MENU_IN_MS else t.MENU_OUT_MS, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "menu",
    )
    if (!open && shown <= 0.001f) return
    fun name(m: M.Month) = t.MONTH_NAMES[m.month - 1] + " ${m.year}"
    val prev = M.addMonths(month, -1)
    val next = M.addMonths(month, 1)
    val items = buildList {
        add(Triple("Previous month", "Previous month, " + name(prev)) { onPick(prev) })
        add(Triple("Next month", "Next month, " + name(next)) { onPick(next) })
        if (!M.isMay2026(month)) add(Triple("Back to May 2026", "Back to May 2026") { onPick(M.MAY_2026) })
        add(Triple("Cancel", "Cancel", onClose))
    }
    val widthPx = (t.MENU_WIDTH * s).toFloat()
    val leftPx = space.widthPx - widthPx - ((t.HEADER_CENTER_INSET - t.HEADER_SIZE / 2) * s).toFloat()
    val topPx = space.y(t.HEADER_CENTER_Y + t.HEADER_SIZE / 2 + t.MENU_GAP).toFloat()
    Box(
        modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close menu", onClick = onClose),
    ) {
        Column(
            Modifier
                .offset { IntOffset(leftPx.toInt(), topPx.toInt()) }
                .width(pxToDp(widthPx.toDouble()))
                .graphicsLayer {
                    alpha = shown
                    val k = if (reduceMotion) 1f else t.MENU_SCALE_FROM.toFloat() + (1 - t.MENU_SCALE_FROM.toFloat()) * shown
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .clip(RoundedCornerShape(pxToDp(t.MENU_RADIUS * s)))
                .semantics { paneTitle = "Month menu" },
        ) {
            Box(Modifier.fillMaxWidth()) {
                StampDiaryCalendarGlass(layer, leftPx, topPx, space, StampDiaryCalendarColors.menuFill, Modifier.matchParentSize())
                Column {
                    items.forEachIndexed { i, (label, description, action) ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(pxToDp(t.MENU_ROW * s))
                                .drawBehind {
                                    if (i > 0) drawLine(StampDiaryCalendarColors.menuDivider, Offset.Zero, Offset(size.width, 0f), 1f)
                                }
                                .clickable(role = Role.Button, onClick = action)
                                .padding(horizontal = pxToDp(t.MENU_PAD_X * s))
                                .semantics { contentDescription = description },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                label,
                                color = Color.White,
                                fontSize = pxToSp(t.MENU_TEXT_SIZE * s),
                                fontWeight = if (label == "Cancel") FontWeight.Normal else FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
