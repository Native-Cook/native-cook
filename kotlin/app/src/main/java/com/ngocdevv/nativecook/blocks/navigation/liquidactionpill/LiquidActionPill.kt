package com.ngocdevv.nativecook.blocks.navigation.liquidactionpill

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private val BarShadow = Color(0x335E4676)

// The shadows' offsets, and their blur as a standard deviation (half a CSS blur radius).
private const val BAR_SHADOW_Y = 8f
private const val BAR_SHADOW_BLUR = 10f
private const val PILL_SHADOW_Y = 6f
private const val PILL_SHADOW_BLUR = 6f
private const val PILL_SHADOW_ALPHA = 0.35f
private val Frost = Color.White.copy(alpha = 0.22f)
private val Line = Color.White.copy(alpha = 0.5f)

// How strongly the chosen tab's icon is filled.
private const val TAB_FILL = 0.15f
private const val TAB_STROKE = 1.8f
private const val ICON_STROKE = 1.6f
private const val PLUS_STROKE = 2f
private const val PRESSED_SCALE = 0.9f
private const val PRESS_IN_MS = 80
private const val PRESS_OUT_MS = 160

// An icon starts this small and its dot ends this small while one turns into the other.
private const val ICON_FROM_SCALE = 0.6f

private const val TITLE_TOP = 24f
private const val TITLE_LINE = 41f
private const val CARD_GAP = 16f
private const val CARDS_TOP = TITLE_TOP + TITLE_LINE + 20f
private const val TAB_HIT = 72f
private const val BUTTON_HIT = 64f
private const val AVATAR = 72f
private val Ease = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** A card of a page and its placeholder lines: left, top, width, height inside the card. */
private class Card(val height: Float, val lines: List<List<Float>>)

private val HomeCards = listOf(
    Card(170f, listOf(listOf(20f, 24f, 96f, 10f), listOf(20f, 48f, 200f, 18f), listOf(20f, 136f, 140f, 10f))),
    Card(110f, listOf(listOf(20f, 24f, 120f, 10f), listOf(20f, 50f, 220f, 10f), listOf(20f, 76f, 160f, 10f))),
)
private val ProfileCards = listOf(
    Card(170f, listOf(listOf(112f, 58f, 140f, 14f), listOf(112f, 86f, 180f, 10f), listOf(112f, 108f, 110f, 10f))),
    Card(110f, listOf(listOf(20f, 24f, 90f, 10f), listOf(20f, 50f, 250f, 10f), listOf(20f, 76f, 190f, 10f))),
)

private fun frame() = Path().apply { addRoundRect(RoundRect(Rect(2.8f, 2.8f, 17.2f, 17.2f), CornerRadius(3.2f))) }

private fun circle(x: Float, y: Float, r: Float) = Path().apply { addOval(Rect(x - r, y - r, x + r, y + r)) }

private fun polyline(vararg points: Float) = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
}

// The pill's icons in a 20 dp box, one path for each stroke so that each can be drawn in.
private val Icons = listOf(
    listOf(
        Path().apply {
            moveTo(2.5f, 7.2f)
            quadraticTo(2.5f, 5.5f, 4.2f, 5.5f)
            lineTo(6.4f, 5.5f)
            lineTo(7.6f, 3.6f)
            lineTo(12.4f, 3.6f)
            lineTo(13.6f, 5.5f)
            lineTo(15.8f, 5.5f)
            quadraticTo(17.5f, 5.5f, 17.5f, 7.2f)
            lineTo(17.5f, 14.6f)
            quadraticTo(17.5f, 16.3f, 15.8f, 16.3f)
            lineTo(4.2f, 16.3f)
            quadraticTo(2.5f, 16.3f, 2.5f, 14.6f)
            close()
        },
        circle(10f, 10.8f, 2.9f),
    ),
    listOf(frame(), circle(7.3f, 7.4f, 1.3f), polyline(3.2f, 14.2f, 7.6f, 10.4f, 10.6f, 13f, 13f, 11f, 16.8f, 14.4f)),
    listOf(frame(), polyline(5.8f, 12.8f, 8.6f, 9.8f, 10.9f, 11.6f, 14.2f, 7.6f), polyline(11.6f, 7.4f, 14.4f, 7.4f, 14.4f, 10.2f)),
    listOf(polyline(5f, 5f, 15f, 15f), polyline(15f, 5f, 5f, 15f)),
)

// The tabs' icons in a 24 dp box: the shape that is stroked and filled, and what is only stroked.
private val House = Path().apply {
    moveTo(4f, 10.4f)
    lineTo(12f, 3.6f)
    lineTo(20f, 10.4f)
    lineTo(20f, 18.3f)
    quadraticTo(20f, 20.4f, 17.9f, 20.4f)
    lineTo(6.1f, 20.4f)
    quadraticTo(4f, 20.4f, 4f, 18.3f)
    close()
}
private val Door = Path().apply {
    moveTo(9.6f, 20.4f)
    lineTo(9.6f, 15.4f)
    quadraticTo(9.6f, 13.4f, 12f, 13.4f)
    quadraticTo(14.4f, 13.4f, 14.4f, 15.4f)
    lineTo(14.4f, 20.4f)
}
private val Person = Path().apply {
    addOval(Rect(12f - 3.9f, 8.2f - 3.9f, 12f + 3.9f, 8.2f + 3.9f))
    addOval(Rect(5.6f, 15f, 18.4f, 20.6f))
}

private val TitleStyle = TextStyle(
    color = Color.White,
    fontSize = 34.sp,
    lineHeight = TITLE_LINE.sp,
    fontWeight = FontWeight.Bold,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** The paths and paints the canvas draws with, made once and written over on every frame. */
private class PillPaint {
    val bar = Path()
    val body = Path()
    val necks = Path()
    val drawn = Path()
    val measure = PathMeasure()
    val barShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BarShadow.toArgb()
        maskFilter = BlurMaskFilter(sigmaToRadius(BAR_SHADOW_BLUR), BlurMaskFilter.Blur.NORMAL)
    }
    var pillShadowColor = Color.Unspecified
    var pillShadow: SoftRoundRect? = null
}

/**
 * A two-tab bar whose top edge dips into a bowl that holds a round button. Pressing the button
 * throws it out of the bowl, which flattens behind it, and stretches it into a pill; the white dot
 * the plus shrank into divides into four, which turn into the pill's icons. Closing runs it back.
 *
 * @param titles The titles of the two pages, the house's first.
 * @param actionLabels What the four icons of the pill are called: camera, picture, chart and the cross.
 * @param background The page behind everything, from its top left corner to its bottom right.
 * @param accent The button and the pill.
 * @param tint The two tab icons.
 * @param autoPlay Opens and closes the pill and changes the page by itself, every [LOOP] seconds.
 * @param onTabChange Called with 0 or 1 when a tab is chosen.
 * @param onAction Called with 0, 1 or 2 when one of the pill's first three icons is pressed.
 */
@Composable
fun LiquidActionPill(
    titles: List<String> = listOf("Home", "Profile"),
    actionLabels: List<String> = listOf("Camera", "Photos", "Insights", "Close"),
    background: List<Color> = listOf(Color(0xFFB3A3B9), Color(0xFFCDBACB)),
    accent: Color = Color(0xFF7B16E8),
    tint: Color = Color(0xFF7A2BE2),
    barColor: Color = Color.White,
    autoPlay: Boolean = false,
    onTabChange: ((index: Int) -> Unit)? = null,
    onAction: ((index: Int) -> Unit)? = null,
    onOpenChange: ((open: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val tabChanged by rememberUpdatedState(onTabChange)
    val acted by rememberUpdatedState(onAction)
    val openChanged by rememberUpdatedState(onOpenChange)

    var open by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // 0 with the button in its bowl, 1 with the pill open; it moves at a constant speed.
    val way = remember { Animatable(0f) }
    // 1 while the pill opens, 0 while it closes: the bowl behaves differently each way.
    val opening = remember { Animatable(1f) }
    // 0 on the first page, 1 on the second.
    val page = remember { Animatable(0f) }
    // The pill icon under a finger, and how far it is pressed in.
    var held by remember { mutableIntStateOf(-1) }
    val depth = remember { Animatable(0f) }
    val paint = remember { PillPaint() }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun move(next: Boolean) {
        open = next
        openChanged?.invoke(next)
        val target = if (next) 1f else 0f
        val at = way.value
        // Turned round half way, the bowl changes over smoothly; from either end there is nothing to smooth.
        val settled = at <= 0f || at >= 1f
        scope.launch {
            if (settled || reducedMotion) opening.snapTo(target) else opening.animateTo(target, tween((TURN * 1000).toInt(), easing = LinearEasing))
        }
        val seconds = abs(target - at) * (if (reducedMotion) REDUCED_DURATION else DURATION)
        scope.launch { way.animateTo(target, tween((seconds * 1000).toInt(), easing = LinearEasing)) }
    }

    fun choose(index: Int) {
        tab = index
        tabChanged?.invoke(index)
        scope.launch { page.animateTo(index.toFloat(), tween((TAB_FADE * 1000).toInt(), easing = Ease)) }
    }

    fun act(index: Int) {
        touched()
        tap()
        if (index < 3) acted?.invoke(index)
        // The icon's button leaves with the pill, so it is let go of here.
        scope.launch { depth.animateTo(0f, tween(PRESS_OUT_MS)) }
        move(false)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to rest; the loop's first second covers the way there.
            move(false)
            choose(0)
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(OPEN_AT)
            move(true)
            until(CLOSE_AT)
            move(false)
            until(PROFILE_AT)
            choose(1)
            until(HOME_AT)
            choose(0)
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Brush.linearGradient(background))) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val width = maxWidth.value
        val barWidth = width - BAR_MARGIN * 2f
        val barTop = maxHeight.value - max(insets.calculateBottomPadding().value, BAR_BOTTOM) - BAR_HEIGHT
        val tabX = barWidth / 2f - TAB_INSET

        for (index in 0..1) {
            Column(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (index == 0) 1f - page.value else page.value }
                    .then(if (tab == index) Modifier else Modifier.clearAndSetSemantics {})
                    .padding(start = BAR_MARGIN.dp, end = BAR_MARGIN.dp, top = (top + TITLE_TOP).dp),
            ) {
                BasicText(titles[index], style = TitleStyle, maxLines = 1, modifier = Modifier.height(TITLE_LINE.dp).semantics { heading() })
                (if (index == 0) HomeCards else ProfileCards).forEachIndexed { n, card ->
                    Spacer(Modifier.height((if (n == 0) CARDS_TOP - TITLE_TOP - TITLE_LINE else CARD_GAP).dp))
                    Box(Modifier.fillMaxWidth().height(card.height.dp).background(Frost, RoundedCornerShape(24.dp))) {
                        if (index == 1 && n == 0) Box(Modifier.offset(20.dp, 49.dp).size(AVATAR.dp).background(Line, CircleShape))
                        for (line in card.lines) {
                            Box(Modifier.offset(line[0].dp, line[1].dp).size(line[2].dp, line[3].dp).background(Line, CircleShape))
                        }
                    }
                }
            }
        }

        if (open) {
            // A touch on the page closes the pill.
            Box(
                Modifier.matchParentSize().clearAndSetSemantics {}.clickable(remember { MutableInteractionSource() }, indication = null) {
                    touched()
                    move(false)
                },
            )
        }

        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, w, _ ->
                translate(w / 2f, barTop) {
                    val travelled = way.value
                    // Under reduced motion nothing travels: the button fades out, and the pill fades in where it belongs.
                    val p = if (reducedMotion) (if (travelled < 0.5f) 0f else 1f) else travelled
                    val fade = if (reducedMotion) abs(travelled * 2f - 1f) else 1f
                    val turn = page.value

                    // The bowl stays under reduced motion.
                    barOutline(
                        paint.bar,
                        barWidth,
                        depth = if (reducedMotion) BOWL_DEPTH else bowlDepth(p, opening.value),
                        half = if (reducedMotion) BOWL_HALF else bowlHalf(p, opening.value),
                    )
                    // Hardware canvases only honour mask filters from API 28.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        translate(0f, BAR_SHADOW_Y) { drawIntoCanvas { it.nativeCanvas.drawPath(paint.bar.asAndroidPath(), paint.barShadow) } }
                    }
                    drawPath(paint.bar, barColor)

                    val tabStroke = Stroke(TAB_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    translate(-tabX - TAB_ICON / 2f, (BAR_HEIGHT - TAB_ICON) / 2f) {
                        drawPath(House, tint, alpha = TAB_FILL * (1f - turn))
                        drawPath(House, tint, style = tabStroke)
                        drawPath(Door, tint, style = tabStroke)
                    }
                    translate(tabX - TAB_ICON / 2f, (BAR_HEIGHT - TAB_ICON) / 2f) {
                        drawPath(Person, tint, alpha = TAB_FILL * turn)
                        drawPath(Person, tint, style = tabStroke)
                    }

                    translate(0f, -BUTTON_RAISE + lift(p)) {
                        drawPill(paint, p, fade, accent, if (held >= 0) depth.value else 0f, held)
                    }
                }
            }
        }

        for (index in 0..1) {
            Box(
                Modifier
                    .offset((width / 2f + (if (index == 0) -tabX else tabX) - TAB_HIT / 2f).dp, barTop.dp)
                    .size(TAB_HIT.dp, BAR_HEIGHT.dp)
                    .semantics {
                        contentDescription = titles[index]
                        selected = tab == index
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                        touched()
                        if (tab != index) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            choose(index)
                        }
                    },
            )
        }

        if (open) {
            for (index in 0..3) {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                LaunchedEffect(pressed) {
                    if (pressed) {
                        held = index
                        depth.animateTo(1f, tween(PRESS_IN_MS))
                    } else if (held == index) {
                        depth.animateTo(0f, tween(PRESS_OUT_MS))
                    }
                }
                Box(
                    Modifier
                        .offset((width / 2f + (index - 1.5f) * ICON_GAP - ICON_GAP / 2f).dp, (barTop - BUTTON_RAISE - RISE - BUTTON / 2f).dp)
                        .size(ICON_GAP.dp, BUTTON.dp)
                        .semantics { contentDescription = actionLabels[index] }
                        .clickable(interaction, indication = null, role = Role.Button) { act(index) },
                )
            }
        } else {
            Box(
                Modifier
                    .offset(((width - BUTTON_HIT) / 2f).dp, (barTop - BUTTON_RAISE - BUTTON_HIT / 2f).dp)
                    .size(BUTTON_HIT.dp)
                    .semantics { contentDescription = "Actions" }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        touched()
                        tap()
                        move(true)
                    },
            )
        }
    }
}

/** The button or the pill around its own middle, with what is on it: the plus, the dots and their necks, the icons. */
private fun DrawScope.drawPill(paint: PillPaint, p: Float, fade: Float, accent: Color, pressed: Float, held: Int) {
    val width = pillWidth(p)
    val height = pillHeight(p)
    if (paint.pillShadowColor != accent) {
        paint.pillShadowColor = accent
        paint.pillShadow = SoftRoundRect(accent.copy(alpha = PILL_SHADOW_ALPHA), sigma = PILL_SHADOW_BLUR)
    }
    drawIntoCanvas { paint.pillShadow?.draw(it.nativeCanvas, -width / 2f, -height / 2f + PILL_SHADOW_Y, width, height, height / 2f, fade) }
    paint.body.rewind()
    paint.body.addRoundRect(RoundRect(Rect(-width / 2f, -height / 2f, width / 2f, height / 2f), CornerRadius(height / 2f)))
    drawPath(paint.body, accent, alpha = fade)

    val plus = plusScale(p)
    if (plus > 0f) {
        val arm = PLUS_ARM * plus
        drawLine(Color.White, Offset(-arm, 0f), Offset(arm, 0f), PLUS_STROKE, StrokeCap.Round, alpha = fade)
        drawLine(Color.White, Offset(0f, -arm), Offset(0f, arm), PLUS_STROKE, StrokeCap.Round, alpha = fade)
    }
    necks(paint.necks, p)
    drawPath(paint.necks, Color.White, alpha = fade)

    val radius = dotRadius(p)
    val iconStroke = Stroke(ICON_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (index in 0..3) {
        val x = dotX(p, index)
        val shown = iconIn(p, index)
        // The dot shrinks and fades as its icon grows out of it, its strokes being drawn.
        val r = radius * (1f - (1f - ICON_FROM_SCALE) * shown)
        if (r > 0f && shown < 1f) drawCircle(Color.White, r, Offset(x, 0f), alpha = (1f - shown) * fade)
        if (shown > 0f) {
            val size = (ICON_FROM_SCALE + (1f - ICON_FROM_SCALE) * shown) * (if (held == index) 1f - (1f - PRESSED_SCALE) * pressed else 1f)
            translate(x, 0f) {
                scale(size, size, pivot = Offset.Zero) {
                    translate(-ICON / 2f, -ICON / 2f) {
                        for (stroke in Icons[index]) {
                            val whole = shown >= 1f
                            if (!whole) {
                                paint.drawn.rewind()
                                paint.measure.setPath(stroke, false)
                                paint.measure.getSegment(0f, paint.measure.length * shown, paint.drawn, true)
                            }
                            drawPath(if (whole) stroke else paint.drawn, Color.White, alpha = shown * fade, style = iconStroke)
                        }
                    }
                }
            }
        }
    }
}
