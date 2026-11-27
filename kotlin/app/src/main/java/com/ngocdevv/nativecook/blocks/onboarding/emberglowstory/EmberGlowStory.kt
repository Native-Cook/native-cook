package com.ngocdevv.nativecook.blocks.onboarding.emberglowstory

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class EmberGlyph { Clock, Bolt, Figure, Calendar }

/**
 * A word, or a glyph drawn at the height of a capital letter.
 *
 * @param key A key word lights up; the number is its turn among the key words of its page.
 * @param was It was lit on the page before, which this page continues.
 * @param glue No space before it.
 */
data class EmberWord(
    val text: String? = null,
    val glyph: EmberGlyph? = null,
    val key: Int? = null,
    val was: Boolean = false,
    val italic: Boolean = false,
    val glue: Boolean = false,
)

/**
 * @param lines The sentence, line by line: the lines are fixed, nothing wraps.
 * @param glow Which of the glows lights the page.
 * @param amber A streak of amber in the hot spot.
 * @param keeps How many words at the start are the page before, kept on screen.
 */
data class EmberPage(val lines: List<List<EmberWord>>, val glow: Int, val amber: Boolean = false, val keeps: Int = 0)

/**
 * @param tint The tint from where it starts down to the bottom edge.
 * @param stops Where each colour of the tint sits (0 top, 1 bottom).
 * @param hot The middle of the hot spot at the bottom edge; [rim] is its rim, [width] and [height] its radii.
 * @param mute Plain words under this light.
 */
data class EmberGlow(val tint: List<Color>, val stops: List<Float>, val hot: Color, val rim: Color, val width: Float, val height: Float, val mute: Color)

private fun word(text: String, key: Int? = null) = EmberWord(text = text, key = key)

private fun glyph(glyph: EmberGlyph, key: Int? = null) = EmberWord(glyph = glyph, key = key)

val SampleEmberPages = listOf(
    EmberPage(
        listOf(listOf(word("Industrial")), listOf(word("revolution"), word("made")), listOf(word("us"), word("plan"), word("around")), listOf(word("the"), glyph(EmberGlyph.Clock, 0), word("clock", 0))),
        glow = 0,
    ),
    EmberPage(
        listOf(
            listOf(word("But"), word("every"), word("person")),
            listOf(word("has"), word("their"), word("own")),
            listOf(word("unique"), glyph(EmberGlyph.Bolt, 0), word("energy", 0)),
            listOf(word("rhythm", 1)),
        ),
        glow = 1,
    ),
    EmberPage(
        listOf(
            listOf(word("But"), word("every"), word("person")),
            listOf(word("has"), word("their"), word("own")),
            listOf(word("unique"), EmberWord(glyph = EmberGlyph.Bolt, was = true), EmberWord("energy", was = true)),
            listOf(EmberWord("rhythm", was = true), EmberWord(",", key = 0, glue = true), word("and", 0), word("it’s", 0)),
            listOf(EmberWord("not", key = 1, italic = true), EmberWord("random", key = 1, italic = true), word("it’s", 2), word("a", 2)),
            listOf(word("predictable")),
            listOf(word("biology", 3)),
        ),
        glow = 1, amber = true, keeps = 10,
    ),
    EmberPage(
        listOf(
            listOf(word("Unlock"), word("your"), word("full")),
            listOf(word("potential"), word("by"), word("pairing")),
            listOf(word("your"), glyph(EmberGlyph.Figure, 0), word("biology", 0), word("and")),
            listOf(glyph(EmberGlyph.Calendar, 1), word("schedule", 1)),
        ),
        glow = 2,
    ),
)

private val Background = Color(0xFF010200)

val SampleEmberGlows = listOf(
    EmberGlow(listOf(Background, Color(0xFF0C0706), Color(0xFF160B0A), Color(0xFF24130F)), listOf(0.68f, 0.8f, 0.9f, 1f), Color(0xFFE4CCC4), Color(0xFF8A5A4C), 150f, 46f, Color(0xFF8E8A84)),
    EmberGlow(
        listOf(Background, Color(0xFF101A06), Color(0xFF1A3A13), Color(0xFF264C01), Color(0xFF3A6A08)), listOf(0.57f, 0.65f, 0.75f, 0.82f, 1f),
        Color(0xFF9BD12F), Color(0xFF508131), 210f, 120f, Color(0xFF8C936E),
    ),
    EmberGlow(listOf(Background, Color(0xFF2B0D12), Color(0xFF3C000D), Color(0xFF510013)), listOf(0.62f, 0.7f, 0.81f, 1f), Color(0xFFE8621A), Color(0xFFA3180F), 210f, 105f, Color(0xFF9C7F7C)),
)

private val Amber = Color(0xFFE0A21A)
private const val AMBER_WIDTH = 90f
private const val AMBER_HEIGHT = 46f
private const val AMBER_ASIDE = 40f
private val Lit = Color.White.copy(alpha = 0.95f)

// The type is set for a screen 393 dp wide and scaled down on a narrower one.
private const val DESIGN_WIDTH = 393f
private const val MARGIN = 40f
private const val FONT = 30f
private const val LINE = 43f
private const val SPACE = 7.5f
private const val GLYPH = 26f
private const val BUTTON_MARGIN = 28f
private const val BUTTON_HEIGHT = 54f
private const val TEXT_GAP = 38f

// The hot spot's middle: this far across the screen, and this share of its height below the edge.
private const val HOT_ACROSS = 0.6f
private const val HOT_BELOW = 0.15f

// A finger that moves further than this is not tapping; this far downwards, it is going back.
private const val SLOP = 12f
private const val BACK_SWIPE = 40f

private val Glyphs = mapOf(
    EmberGlyph.Clock to Icons.Filled.Schedule,
    EmberGlyph.Bolt to Icons.Filled.Bolt,
    EmberGlyph.Figure to Icons.Filled.Man,
    EmberGlyph.Calendar to Icons.Filled.CalendarMonth,
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** How a hot spot's light falls off from the middle to the rim. */
private fun falloff(middle: Color, rim: Color, radius: Float) = Brush.radialGradient(
    0f to middle,
    0.3f to middle.copy(alpha = 0.7f),
    0.6f to rim.copy(alpha = 0.35f),
    0.85f to rim.copy(alpha = 0.08f),
    1f to rim.copy(alpha = 0f),
    center = Offset.Zero,
    radius = radius,
)

/** An ellipse of light about (x, y): a circle of the brush's radius, flattened. */
private fun DrawScope.spot(brush: Brush, x: Float, y: Float, radius: Float, flat: Float, alpha: Float) {
    withTransform({
        translate(x, y)
        scale(1f, flat, pivot = Offset.Zero)
    }) {
        drawCircle(brush, radius, Offset.Zero, alpha = alpha.coerceIn(0f, 1f))
    }
}

/** What changed the page: a tap, the loop that plays by itself, or the return to rest. */
private enum class By { Tap, Auto, Rest }

/**
 * A short argument told one sentence a page on black, under a glow that rises from the bottom
 * edge and changes colour with each page. Sentences are written word by word and their key words
 * light up. A tap goes on, a swipe down goes back, and the glow comes under a finger on the button.
 *
 * @param autoPlay Goes through the pages by itself every [LOOP] seconds.
 * @param onDone Called when the last page's button is pressed.
 */
@Composable
fun EmberGlowStory(
    pages: List<EmberPage> = SampleEmberPages,
    glows: List<EmberGlow> = SampleEmberGlows,
    nextLabel: String = "Next",
    doneLabel: String = "Unlock",
    autoPlay: Boolean = false,
    onPageChange: ((page: Int) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    // The functions below outlive a recomposition: they read what the caller passes through these.
    val last by rememberUpdatedState(pages.size - 1)
    val pageChanged by rememberUpdatedState(onPageChange)
    val finished by rememberUpdatedState(onDone)

    // The clock's zero: every time below is in seconds since then.
    val origin = remember { System.nanoTime() }
    // The story opens as it is just after the last page's flood: the first page, whole.
    var scene by remember { mutableStateOf(Scene(page = 0, from = pages.size - 1, at = -RETURN, how = How.Flooded)) }
    var press by remember { mutableStateOf(Press()) }
    // Without motion nothing breathes, so the clock may stop.
    var settled by remember { mutableDoubleStateOf(if (still) 1.0 else Double.POSITIVE_INFINITY) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The button's upper edge and the screen's width in dp, for the touches and the loop.
    var buttonTop by remember { mutableFloatStateOf(0f) }
    var screenWidth by remember { mutableFloatStateOf(DESIGN_WIDTH) }
    // Seconds since `origin`, while something moves. Read while drawing only.
    val clock = remember { mutableDoubleStateOf(0.0) }

    fun now() = (System.nanoTime() - origin) / 1e9

    /** Shows a page. Taps, the loop that plays by itself and the return to rest all come through here. */
    fun goTo(page: Int, how: How, by: By) {
        val t = now()
        scene = Scene(page, from = scene.page, at = t, how = how)
        settled = if (still) t + 3 else Double.POSITIVE_INFINITY
        if (by == By.Tap) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        pageChanged?.invoke(page)
        if (how == How.Flooded) finished?.invoke()
    }

    fun advance(by: By) {
        if (scene.page < last) goTo(scene.page + 1, How.Forward, by) else goTo(0, How.Flooded, by)
    }

    /** Puts a finger on the button at x, or takes it off. */
    fun hold(down: Boolean, x: Double = 0.0) {
        if (press.down == down) return
        val t = now()
        press = Press(down, t, if (down) x else press.x)
        if (still) settled = max(settled, t + 1)
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val t = now()
            clock.doubleValue = min(t, settled)
            // At rest nothing is drawn again until a touch or the loop sets something moving.
            if (t >= settled) snapshotFlow { settled }.first { it > now() }
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        var start = System.nanoTime()
        suspend fun until(seconds: Double) = delay(max(0L, (start + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000))
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // The way back to the first page takes RETURN, and the loop starts from there.
            if (scene.page != 0) goTo(0, How.Whole, By.Rest)
            start = System.nanoTime() + (RETURN * 1e9).toLong()
        }
        while (true) {
            for (at in AUTO_TAPS) {
                until(at - PRESS_FOR)
                hold(true, screenWidth / 2.0)
                until(at)
                hold(false)
                advance(By.Auto)
            }
            start += (LOOP * 1e9).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Background)
            .clipToBounds()
            // One touch is a tap to go on, a swipe down to go back, or a finger resting on the button.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touches++
                    val x = down.position.x.toDp().value
                    val y = down.position.y.toDp().value
                    if (y >= buttonTop && y <= buttonTop + BUTTON_HEIGHT && x >= BUTTON_MARGIN && x <= screenWidth - BUTTON_MARGIN) hold(true, x.toDouble())
                    var moved = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val pull = change.position - down.position
                        if (!change.pressed) {
                            hold(false)
                            if (!moved) {
                                advance(By.Tap)
                            } else if (pull.y > BACK_SWIPE.dp.toPx() && pull.y > abs(pull.x) && scene.page > 0) {
                                goTo(scene.page - 1, How.Whole, By.Tap)
                            }
                            break
                        }
                        if (!moved && pull.getDistance() > SLOP.dp.toPx()) {
                            moved = true
                            hold(false)
                        }
                    }
                    hold(false)
                }
            },
    ) {
        val fit = min(1.1f, maxWidth.value / DESIGN_WIDTH)
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val buttonBottom = max(insets.calculateBottomPadding().value + 3f, 20f)
        val width = maxWidth.value
        val top = maxHeight.value - buttonBottom - BUTTON_HEIGHT
        SideEffect {
            buttonTop = top
            screenWidth = width
        }

        // The glow: for each colour a tint rising from the bottom edge and a hot spot half below it.
        Box(
            Modifier.fillMaxSize().clearAndSetSemantics {}.drawWithCache {
                val tints = glows.map { glow ->
                    val from = glow.stops[0] * size.height
                    Brush.verticalGradient(*Array(glow.tint.size) { (glow.stops[it] - glow.stops[0]) / (1f - glow.stops[0]) to glow.tint[it] }, startY = from, endY = size.height)
                }
                val spots = glows.map { falloff(it.hot, it.rim, it.width.dp.toPx()) }
                val streak = falloff(Amber, Amber, AMBER_WIDTH.dp.toPx())
                onDrawBehind {
                    val t = clock.doubleValue
                    val here = scene
                    val hot = hotAt((width * HOT_ACROSS).toDouble(), press, t, still)
                    var lift = 0f
                    glows.forEachIndexed { color, glow ->
                        val pose = glowPose(color, pages[here.page].glow, pages[here.from].glow, here, t, still)
                        if (color == pages[here.page].glow) lift = pose.lift
                        if (pose.opacity <= 0f) return@forEachIndexed
                        val from = glow.stops[0] * size.height
                        val lifted = pose.lift.dp.toPx()
                        // Stretched from the bottom edge, and lifted.
                        withTransform({
                            translate(0f, size.height)
                            scale(1f, pose.stretch, pivot = Offset.Zero)
                            translate(0f, -size.height - lifted)
                        }) {
                            // The tint runs on below the screen, so the surge leaves no gap under it.
                            drawRect(tints[color], Offset(0f, from), Size(size.width, size.height - from + (SURGE + 40).dp.toPx()), alpha = pose.opacity)
                            spot(spots[color], hot.x.dp.toPx(), size.height + (glow.height * HOT_BELOW).dp.toPx(), glow.width.dp.toPx(), glow.height / glow.width, pose.opacity * hot.light)
                        }
                    }
                    val amber = amberAt(pages[here.page].amber, pages[here.from].amber, here, t)
                    if (amber > 0f) {
                        spot(
                            streak, (hot.x + AMBER_ASIDE).dp.toPx(), size.height + (AMBER_HEIGHT * HOT_BELOW - lift).dp.toPx(),
                            AMBER_WIDTH.dp.toPx(), AMBER_HEIGHT / AMBER_WIDTH, amber * hot.light,
                        )
                    }
                }
            },
        )

        pages.forEachIndexed { index, page ->
            val count = page.lines.sumOf { it.size } - page.keeps
            // A page that continues the one before starts lower by the lines it adds.
            val rise = if (page.keeps > 0 && index > 0) (page.lines.size - pages[index - 1].lines.size) * LINE * fit else 0f
            val handsOver = index < pages.size - 1 && pages[index + 1].keeps > 0
            val glow = glows[page.glow]
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = (MARGIN * fit).dp, bottom = (buttonBottom + BUTTON_HEIGHT + TEXT_GAP).dp)
                    .graphicsLayer {
                        val here = scene
                        val pose = blockPose(index, if (here.from == index - 1) rise.toDouble() else 0.0, handsOver && here.page == index + 1, here, clock.doubleValue, still)
                        alpha = pose.opacity
                        translationY = pose.y.dp.toPx()
                    }
                    .clearAndSetSemantics {
                        if (index == scene.page) contentDescription = page.lines.joinToString(" ") { line -> line.mapNotNull { it.text }.joinToString(" ") }
                    },
            ) {
                var seen = 0
                for (line in page.lines) {
                    Row(Modifier.height((LINE * fit).dp), verticalAlignment = Alignment.CenterVertically) {
                        line.forEachIndexed { column, item ->
                            val order = seen++ - page.keeps
                            if (column > 0 && !item.glue) Spacer(Modifier.width((SPACE * fit).dp))
                            Word(item, glow, fit) {
                                val here = scene
                                if (here.page == index) wordPose(order, count, item.key ?: -1, item.was, here, clock.doubleValue, still) else wordAtRest(item.key ?: -1)
                            }
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = BUTTON_MARGIN.dp, end = BUTTON_MARGIN.dp, bottom = buttonBottom.dp)
                .fillMaxWidth()
                .height(BUTTON_HEIGHT.dp)
                .drawBehind {
                    val pressed = pressAt(press, clock.doubleValue).toFloat()
                    drawRoundRect(Color.White.copy(alpha = 0.06f + 0.085f * pressed), cornerRadius = CornerRadius(size.height / 2f))
                }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = if (scene.page == last) doneLabel else nextLabel
                    onClick {
                        touches++
                        advance(By.Tap)
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The last page's label takes the place of the others'.
            fun done(): Float {
                val here = scene
                val e = clock.doubleValue - here.at
                return (if (here.page == last) smooth(e, 0.2, 0.5) else if (here.from == last) 1 - smooth(e, 0.0, 0.25) else 0.0).toFloat()
            }
            BasicText(nextLabel, style = LabelStyle, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = 1f - done() })
            Row(Modifier.graphicsLayer { alpha = done() }, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp).graphicsLayer {
                        scaleX = if (still) 1f else 0.6f + 0.4f * done()
                        scaleY = scaleX
                    },
                )
                Spacer(Modifier.width(6.dp))
                BasicText(doneLabel, style = LabelStyle, maxLines = 1)
            }
        }
    }
}

/** One word or glyph: grey under the glow, white once it is lit, with a halo in the glow's colour as it lights up. */
@Composable
private fun Word(item: EmberWord, glow: EmberGlow, fit: Float, pose: () -> WordPose) {
    val lights = item.key != null || item.was
    val vector: ImageVector? = item.glyph?.let { Glyphs.getValue(it) }
    Box(
        Modifier.graphicsLayer {
            val now = pose()
            alpha = now.opacity
            translationY = now.y.dp.toPx()
            scaleX = if (vector == null) 1f else now.pop
            scaleY = scaleX
        },
    ) {
        if (vector != null) {
            Icon(vector, contentDescription = null, tint = glow.mute, modifier = Modifier.size((GLYPH * fit).dp))
            if (lights) Icon(vector, contentDescription = null, tint = Lit, modifier = Modifier.size((GLYPH * fit).dp).graphicsLayer { alpha = pose().lit })
            return@Box
        }
        val text = item.text ?: ""
        val halo = with(LocalDensity.current) { 12.dp.toPx() }
        val style = TextStyle(
            color = glow.mute,
            fontSize = (FONT * fit).sp,
            lineHeight = (LINE * fit).sp,
            fontWeight = FontWeight.Bold,
            fontStyle = if (item.italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = (-0.3f).sp,
            lineHeightStyle = Centered,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
        BasicText(text, style = style, maxLines = 1, softWrap = false)
        if (lights) BasicText(text, style = style.copy(color = Lit), maxLines = 1, softWrap = false, modifier = Modifier.graphicsLayer { alpha = pose().lit })
        // The flash as a word lights up: the same word with a halo in the colour of the glow.
        if (item.key != null) {
            BasicText(
                text,
                style = style.copy(color = Lit, shadow = Shadow(glow.hot, Offset.Zero, halo)),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { alpha = pose().flash },
            )
        }
    }
}
