package com.ngocdevv.nativecook.blocks.navigation.doticontabbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The period of the autoPlay loop, in seconds, and the tab it selects at each moment of it. */
internal const val LOOP = 9f
private val Steps = listOf(1.2f to 1, 3.2f to 2, 5.0f to 1, 6.6f to 0)

// After a touch the loop waits this long, returns to the first tab and starts over.
private const val USER_HOLD = 3f

// The circle and the line on a settings row.
private val Detail = Color(0xFFCFCFCF)

private const val MARGIN = 24f

// The title's top edge below the status bar, and the gap under it.
private const val TITLE_TOP = 22f
private const val TITLE_GAP = 20f
private const val BAR_WIDTH = 262f
private const val BAR_HEIGHT = 88f

// The bar's bottom edge above the bottom of the screen, and at least this far above the navigation bar.
private const val BAR_BOTTOM = 40f
private const val BAR_CLEARANCE = 6f

// The bar's shadow: its drop and the sigma of its blur (a 14 dp blur).
private const val BAR_SHADOW_Y = 4f
private const val BAR_SHADOW_SIGMA = 7f

// Each icon is drawn in a 40 dp box; these are the centres from the bar's left edge.
private const val ICON = 40f
private val IconX = listOf(52f, 131f, 210f)
private const val TAP_WIDTH = 79f

private const val STROKE = 2f
private const val STROKE_ACTIVE = 3f
private const val DOT = 14f

// The house's dot starts at this share of its size, under the roof, and lands in the lower middle.
private const val DOT_SMALL = 0.4f
private const val HOME_DOT_FROM = 14f
private const val HOME_DOT_Y = 24f

// The picture's small circle (8 dp) and the gear's ring (10 dp) grow into the dot.
private val PhotoDot = Offset(25f, 16.5f)
private const val PHOTO_DOT_RADIUS = 4f
private const val GEAR_RING = 5f

// Seconds. The stroke and the dot each ease towards their new state; a tab that is deselected
// while it still moves turns round from where it is.
private const val STROKE_ON = 0.15f
private const val STROKE_OFF = 0.25f
private const val FILL_ON = 0.3f
private const val FILL_OFF = 0.25f
private const val CONTENT = 0.3f
private const val CONTENT_SCALE = 0.97f

// How long the clock of a selected icon runs: the longest of the three effects, settled.
private const val MOTION_FOR = 1f

// The house's dot drops on a spring that overshoots once: mass 1, stiffness 400, damping 14.
private const val DROP_STIFFNESS = 400f
private const val DROP_DAMPING = 14f

// The picture rocks: a swing of this period that is over after ROCK_FOR, about -10°, +6°, -2°.
private const val ROCK_DEGREES = 12f
private const val ROCK_PERIOD = 0.36f
private const val ROCK_FOR = 0.6f
private const val ROCK_FALLOFF = 1.17f

// The gear turns one lobe on, which maps it onto itself.
private const val SPIN_DEGREES = 60f
private const val SPIN_FOR = 0.4f

// The clock's value while nothing moves: later than the end of every change.
private const val AT_REST = Double.MAX_VALUE

private fun easeOut(x: Float): Float {
    val rest = 1f - x.coerceIn(0f, 1f)
    return 1f - rest * rest * rest
}

/** Where a spring released at 0 towards 1 is after [t] seconds. */
private fun drop(t: Float): Float {
    if (t >= MOTION_FOR) return 1f
    val omega = sqrt(DROP_STIFFNESS)
    val zeta = DROP_DAMPING / (2f * omega)
    val damped = omega * sqrt(1f - zeta * zeta)
    return 1f - exp(-zeta * omega * t) * (cos(damped * t) + zeta * omega / damped * sin(damped * t))
}

/** The picture's angle [t] seconds after it was selected, in degrees. */
private fun rock(t: Float): Float {
    if (t >= ROCK_FOR) return 0f
    return -ROCK_DEGREES * sin(2f * PI.toFloat() * t / ROCK_PERIOD) * (1f - t / ROCK_FOR).pow(ROCK_FALLOFF)
}

/** A closed outline through [points] whose corners are rounded off [cuts] dp before and after each. */
private fun rounded(points: List<Offset>, cuts: List<Float>) = Path().apply {
    fun toward(from: Offset, to: Offset, distance: Float) = from + (to - from) * (distance / (to - from).getDistance())
    points.forEachIndexed { i, point ->
        val before = toward(point, points[(i + points.size - 1) % points.size], cuts[i])
        val after = toward(point, points[(i + 1) % points.size], cuts[i])
        if (i == 0) moveTo(before.x, before.y) else lineTo(before.x, before.y)
        quadraticTo(point.x, point.y, after.x, after.y)
    }
    close()
}

// The house: a roof peak, two shoulders and a flat bottom.
private val House = rounded(
    listOf(Offset(20f, 4f), Offset(35.5f, 16.5f), Offset(35.5f, 36f), Offset(4.5f, 36f), Offset(4.5f, 16.5f)),
    listOf(3.5f, 5f, 7f, 7f, 5f),
)

// The picture: a rounded square, and two hills that start on its left edge and on the first hill.
private const val FRAME_INSET = 4.5f
private const val FRAME_SIZE = 31f
private const val FRAME_RADIUS = 11f
private val Hills = Path().apply {
    moveTo(4.5f, 21f)
    quadraticTo(19f, 21f, 23.5f, 35.5f)
    moveTo(19.9f, 28.1f)
    quadraticTo(27f, 24.5f, 35.5f, 25.5f)
}

// The gear: a circle whose radius swells six times on the way round, with a lobe at the top.
private const val GEAR_RADIUS = 15f
private const val GEAR_WAVE = 1.6f
private const val GEAR_LOBES = 6
private const val GEAR_STEPS = 120
private val Gear = Path().apply {
    for (step in 0 until GEAR_STEPS) {
        val angle = step.toFloat() / GEAR_STEPS * 2f * PI.toFloat()
        val radius = GEAR_RADIUS + GEAR_WAVE * cos(GEAR_LOBES * angle)
        val x = ICON / 2f + radius * sin(angle)
        val y = ICON / 2f - radius * cos(angle)
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val Centre = Offset(ICON / 2f, ICON / 2f)

private val TitleStyle = TextStyle(
    fontSize = 34.sp,
    lineHeight = 41.sp,
    fontWeight = FontWeight.Bold,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun seconds() = System.nanoTime() / 1e9

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** A number easing out towards a target, sampled against the block's clock. */
private class Ramp(value: Float) {
    private var from = value
    private var to = value
    private var start = 0.0
    private var duration = 0f

    fun value(now: Double): Float {
        if (duration <= 0f) return to
        return from + (to - from) * easeOut(((now - start) / duration).coerceIn(0.0, 1.0).toFloat())
    }

    fun retarget(target: Float, now: Double, duration: Float) {
        from = value(now)
        to = target
        start = now
        this.duration = duration
    }
}

/** What moves one tab. Read while drawing only: the clock is what invalidates. */
private class TabMotion(selected: Boolean) {
    var active = selected
        private set

    /** The stroke, from thin and grey (0) to thick and dark (1). */
    val on = Ramp(if (selected) 1f else 0f)

    /** The dot, from absent (0) to full (1). */
    val fill = Ramp(if (selected) 1f else 0f)

    /** The tab's page, from hidden (0) to shown (1). */
    val shown = Ramp(if (selected) 1f else 0f)
    private var selectedAt = -MOTION_FOR.toDouble()

    fun move(selected: Boolean, now: Double) {
        active = selected
        on.retarget(if (selected) 1f else 0f, now, if (selected) STROKE_ON else STROKE_OFF)
        fill.retarget(if (selected) 1f else 0f, now, if (selected) FILL_ON else FILL_OFF)
        shown.retarget(if (selected) 1f else 0f, now, CONTENT)
        if (selected) selectedAt = now
    }

    /** Seconds since the tab was last selected, up to [MOTION_FOR]. */
    fun t(now: Double) = (now - selectedAt).coerceIn(0.0, MOTION_FOR.toDouble()).toFloat()
}

/** What one icon is drawn with at this moment. */
private class IconLook(
    /** The dot, from absent (0) to full (1). */
    val fill: Float,
    /** Seconds since the tab was last selected. */
    val t: Float,
    val active: Boolean,
    val still: Boolean,
    val width: Float,
    val stroke: Color,
    val accent: Color,
) {
    val style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

private fun DrawScope.drawHome(look: IconLook) {
    drawPath(House, look.stroke, style = look.style)
    // Selected, the dot drops from under the roof on the spring; deselected, it rises back as it fades.
    val fall = if (look.active) drop(look.t) else look.fill
    val y = if (look.still) HOME_DOT_Y else HOME_DOT_FROM + (HOME_DOT_Y - HOME_DOT_FROM) * fall
    val radius = DOT / 2f * if (look.still) 1f else DOT_SMALL + (1f - DOT_SMALL) * look.fill
    drawCircle(look.accent, radius, Offset(ICON / 2f, y), alpha = look.fill)
}

private fun DrawScope.drawPhotos(look: IconLook) {
    rotate(if (look.still) 0f else rock(look.t), Centre) {
        drawRoundRect(look.stroke, Offset(FRAME_INSET, FRAME_INSET), Size(FRAME_SIZE, FRAME_SIZE), CornerRadius(FRAME_RADIUS), style = look.style)
        drawPath(Hills, look.stroke, style = look.style)
        drawMorph(look, PhotoDot, PHOTO_DOT_RADIUS)
    }
}

private fun DrawScope.drawSettings(look: IconLook) {
    rotate(if (look.still) 0f else SPIN_DEGREES * easeOut(look.t / SPIN_FOR), Centre) {
        drawPath(Gear, look.stroke, style = look.style)
    }
    drawMorph(look, Centre, GEAR_RING)
}

/** An outlined circle of radius [small] that fills with the accent and grows into the dot. */
private fun DrawScope.drawMorph(look: IconLook, centre: Offset, small: Float) {
    val grown = small + (DOT / 2f - small) * look.fill
    // Without motion the two only cross-fade, each at its own size.
    drawCircle(look.stroke, (if (look.still) small else grown) - look.width / 2f, centre, alpha = 1f - look.fill, style = Stroke(look.width))
    drawCircle(look.accent, if (look.still) DOT / 2f else grown, centre, alpha = look.fill)
}

/**
 * A screen with a floating pill of three outlined icons. Selecting one thickens and darkens its
 * stroke and brings a dot into it, each icon in its own way: the house's drops in and bounces,
 * the picture rocks while its small circle fills, the gear turns while its ring fills. The page
 * above cross-fades to the tab's own.
 *
 * @param labels The titles of the three tabs, in the order of the icons: house, picture, gear.
 * @param initialTab The tab that is selected at first, and the one autoPlay comes back to.
 * @param cardColor The placeholder cards of the sample pages.
 * @param ink The selected icon's stroke, and the titles.
 * @param mute The stroke of an icon that is not selected.
 * @param accent The dot inside the selected icon.
 * @param autoPlay Goes through the tabs by itself every [LOOP] seconds.
 */
@Composable
fun DotIconTabBar(
    labels: List<String> = listOf("Home", "Photos", "Settings"),
    initialTab: Int = 0,
    background: Color = Color(0xFFD6D6D6),
    barColor: Color = Color(0xFFEFEFEF),
    cardColor: Color = Color(0xFFE4E4E4),
    ink: Color = Color(0xFF0B0B0F),
    mute: Color = Color(0xFF55555A),
    accent: Color = Color(0xFF5546F2),
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val home by rememberUpdatedState(initialTab)
    val chosen by rememberUpdatedState(onSelect)

    var selected by remember { mutableIntStateOf(initialTab) }
    val motions = remember { List(3) { TabMotion(selected = it == initialTab) } }
    // Seconds on the block's clock while something moves, AT_REST otherwise. Read while drawing only.
    val clock = remember { mutableDoubleStateOf(AT_REST) }
    // Counts the changes, so the clock stops after the last one has settled.
    var moves by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    // The screen is light: dark icons on the status bar while it shows.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    fun select(index: Int, byHand: Boolean) {
        if (index == selected) return
        // The loop that plays by itself is silent.
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val now = seconds()
        motions[selected].move(selected = false, now)
        motions[index].move(selected = true, now)
        clock.doubleValue = now
        selected = index
        moves++
        chosen?.invoke(index)
    }

    // The frame clock ignores the system's animation scale, so reduced motion still gets its fades.
    LaunchedEffect(moves) {
        if (moves == 0) return@LaunchedEffect
        val until = seconds() + MOTION_FOR + 0.1
        while (true) {
            val now = withFrameNanos { seconds() }
            if (now >= until) break
            clock.doubleValue = now
        }
        clock.doubleValue = AT_REST
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            select(home, byHand = false)
        }
        // Every step is timed from the loop's origin, so a late wake does not push the next one back.
        var origin = System.nanoTime()
        suspend fun until(at: Float) = delay(max(0L, (origin + (at * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((at, tab) in Steps) {
                until(at)
                select(tab, byHand = false)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.06f), BAR_SHADOW_SIGMA) }

    Box(modifier.fillMaxSize().background(background)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = max(BAR_BOTTOM, insets.calculateBottomPadding().value + BAR_CLEARANCE)

        // Sample pages, one per tab: grey placeholders in three different layouts.
        for (index in 0..2) {
            Column(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val shown = motions[index].shown.value(clock.doubleValue)
                        val scale = if (still) 1f else CONTENT_SCALE + (1f - CONTENT_SCALE) * shown
                        alpha = shown
                        scaleX = scale
                        scaleY = scale
                    }
                    .then(if (index != selected) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(start = MARGIN.dp, end = MARGIN.dp, top = top + TITLE_TOP.dp),
            ) {
                BasicText(
                    labels[index],
                    style = TitleStyle.copy(color = ink),
                    maxLines = 1,
                    modifier = Modifier.height(41.dp).semantics { heading() },
                )
                Spacer(Modifier.height(TITLE_GAP.dp))
                when (index) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth().height(150.dp).background(cardColor, RoundedCornerShape(24.dp)))
                        Box(Modifier.fillMaxWidth().height(96.dp).background(cardColor, RoundedCornerShape(24.dp)))
                    }
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        repeat(3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                Box(Modifier.weight(1f).aspectRatio(1f).background(cardColor, RoundedCornerShape(18.dp)))
                                Box(Modifier.weight(1f).aspectRatio(1f).background(cardColor, RoundedCornerShape(18.dp)))
                            }
                        }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(5) {
                            Row(
                                Modifier.fillMaxWidth().height(56.dp).background(cardColor, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(24.dp).background(Detail, CircleShape))
                                Box(Modifier.size(90.dp, 10.dp).background(Detail, CircleShape))
                            }
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom.dp)
                .size(BAR_WIDTH.dp, BAR_HEIGHT.dp)
                .drawBehind {
                    scale(density, density, pivot = Offset.Zero) {
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, BAR_SHADOW_Y, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT / 2f) }
                    }
                    drawRoundRect(barColor, cornerRadius = CornerRadius(size.height / 2f))
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val now = clock.doubleValue
                // Drawn in dp, like the other two stacks draw in points.
                scale(density, density, pivot = Offset.Zero) {
                    for (index in 0..2) {
                        val tab = motions[index]
                        val on = tab.on.value(now)
                        val look = IconLook(
                            fill = tab.fill.value(now),
                            t = tab.t(now),
                            active = tab.active,
                            still = still,
                            width = STROKE + (STROKE_ACTIVE - STROKE) * on,
                            stroke = lerp(mute, ink, on),
                            accent = accent,
                        )
                        translate(IconX[index] - ICON / 2f, (BAR_HEIGHT - ICON) / 2f) {
                            when (index) {
                                0 -> drawHome(look)
                                1 -> drawPhotos(look)
                                else -> drawSettings(look)
                            }
                        }
                    }
                }
            }

            Row(Modifier.align(Alignment.Center)) {
                for (index in 0..2) {
                    val current = index == selected
                    Box(
                        Modifier
                            .size(TAP_WIDTH.dp, BAR_HEIGHT.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                                touches++
                                select(index, byHand = true)
                            }
                            .semantics {
                                contentDescription = labels[index]
                                this.selected = current
                            },
                    )
                }
            }
        }
    }
}
