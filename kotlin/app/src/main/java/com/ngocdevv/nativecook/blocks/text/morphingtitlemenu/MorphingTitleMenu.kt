package com.ngocdevv.nativecook.blocks.text.morphingtitlemenu

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as GeoRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// A podcast list whose page title is a menu. Tapping the big condensed title springs a small
// popover out of it; picking the other list morphs the title letter by letter: each old letter
// sinks, shrinks and blurs away while the new word's letter drops in from above, sharpening as it
// lands, one slot after another from the left. The title's width follows, the chevron with it,
// and the list underneath cross-fades. A port of the SwiftUI block, number for number.
//
// autoPlay timeline (LOOP = 6 s):
//   0.00        rest on "Queue", popover closed (also the first frame on screen)
//   0.60        the title is tapped: the popover springs open (response 0.42 s, damping 0.64)
//   1.45        "Favorites" is tapped: the highlight pill and its check slide down (spring 0.32 / 0.78)
//   1.68        the popover shrinks back into the title (ease-in, 0.2 s)
//   1.80        the title morphs "Queue" -> "Faves" (0.63 s); the list cross-fades (0.35 s, ease-out)
//   2.43-3.60   rest on "Faves"
//   3.60        open; 4.45 pick "Queue"; 4.68 close; 4.80 the title morphs back
//   5.43-6.00   rest on "Queue"

/** Seconds. */
private const val LOOP = 6f
private const val HALF = 3f
private const val OPEN_AT = 0.6f
private const val PICK_AT = 1.45f
private const val CLOSE_AFTER_PICK = 0.23f
private const val COMMIT_AFTER_CLOSE = 0.12f
private const val USER_HOLD = 2f

// The per-letter morph.
private const val STAGGER = 0.06f
private const val OUT_DURATION = 0.28f
private const val IN_DELAY = 0.05f
private const val IN_DURATION = 0.34f
/** How far a letter travels, how small it gets and how much it blurs, in units of the font size. */
private const val DROP = 0.48f
private const val MIN_SCALE = 0.55f
private const val BLUR = 0.09f
/** Under reduced motion the words only cross-fade, over this long. */
private const val FADE = 0.25f

private fun morphDuration(slots: Int) = (max(slots, 1) - 1) * STAGGER + max(OUT_DURATION, IN_DELAY + IN_DURATION)

private class Pose(val dy: Float, val scale: Float, val blur: Float, val opacity: Float)

private fun poseOut(p: Float, size: Float): Pose {
    val e = p * p
    return Pose(DROP * size * e, 1 - (1 - MIN_SCALE) * e, BLUR * size * p, 1 - p)
}

private fun poseIn(q: Float, size: Float): Pose {
    val e = 1 - (1 - q).pow(3)
    val soft = 1 - (1 - q) * (1 - q)
    return Pose(-DROP * size * (1 - e), MIN_SCALE + (1 - MIN_SCALE) * e, BLUR * size * (1 - soft), min(1f, q * 2))
}

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

// Springs given like SwiftUI's (response, damping fraction): stiffness = (2π / response)².
private fun stiffness(response: Float) = (2 * Math.PI / response).pow(2).toFloat()
private val EASE_IN = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)

// Metrics, dp (and sp for text).
private val SIDE = 30.dp
private val HEADER_TOP = 40.dp
private const val TITLE_SIZE = 46f
private val TITLE_BOX = 54.dp
private const val BASELINE = 47f
private val CHEVRON_GAP = 7.dp
private val CHEVRON_SIZE = 22.dp
private val CANVAS_PAD = 30.dp
private val CANVAS_WIDTH = 360.dp
private val SUBTITLE_TOP = 8.dp
private val LIST_TOP = 44.dp
private val EPISODE_GAP = 34.dp
private val MENU_WIDTH = 164.dp
private val MENU_INSET = 7.dp
private val MENU_ROW = 44.dp
private val MENU_RADIUS = 22.dp
private val MENU_X = SIDE - 12.dp
private val MENU_GAP = 6.dp

private val BACKGROUND = Color(0xFFF8F6F8)
private val INK = Color(0xFF0E0E10)
private val INK2 = Color(0xFF2C2C2E)
private val MUTE = Color(0xFF9C9AA0)
private val FAINT = Color(0xFFB6B4B9)
private val CHIP = Color(0xFFECEAED)
private val CHEVRON = Color(0xFFA6A4A9)
private val TRACK = Color(0xFFE6E4E7)
private val FILL = Color(0xFF5E5D62)
private val PILL = Color(0xFFF0EEF1)
private val ICON = Color(0xFF6E6D72)

data class MorphingTitleMenuEpisode(
    val show: String,
    val whenText: String,
    val title: String,
    /** What the play button says after its verb: time left or the whole length. */
    val length: String,
    /** How far it has been listened to, when it has been started. */
    val progress: Float? = null,
    val playing: Boolean = false,
    val art: List<Color>,
    val mark: String,
)

data class MorphingTitleMenuPage(
    /** The word in the title. */
    val title: String,
    /** Its row in the popover. */
    val label: String,
    val subtitle: String,
    val episodes: List<MorphingTitleMenuEpisode>,
)

private val PIXEL_HOURS = listOf(Color(0xFF1F2A44), Color(0xFF3B5BDB))
private val STUDIO_NOTES = listOf(Color(0xFFF59F00), Color(0xFFF76707))
private val LONG_BUILD = listOf(Color(0xFF2B8A3E), Color(0xFF94D82D))
private val FIELDWORK = listOf(Color(0xFFC2255C), Color(0xFFF783AC))

val MorphingTitleMenuSamples = listOf(
    MorphingTitleMenuPage(
        "Queue", "Queue", "Fri 4 Apr",
        listOf(
            MorphingTitleMenuEpisode("Pixel Hours", "2d ago", "Designing on expert mode", "5m", progress = 0.42f, playing = true, art = PIXEL_HOURS, mark = "P"),
            MorphingTitleMenuEpisode("Studio Notes", "4d ago", "Why every tool ends up a canvas", "48m", art = STUDIO_NOTES, mark = "S"),
            MorphingTitleMenuEpisode("The Long Build", "1w ago", "Shipping the boring parts first", "1h 12m", art = LONG_BUILD, mark = "L"),
        ),
    ),
    MorphingTitleMenuPage(
        "Faves", "Favorites", "2 Episodes",
        listOf(
            MorphingTitleMenuEpisode("Fieldwork FM", "Jul 11, 2024", "Building product at Lumen: craft, metrics and taste", "2h 35m", art = FIELDWORK, mark = "F"),
            MorphingTitleMenuEpisode("Studio Notes", "Mar 3, 2025", "Small teams, sharp tools", "54m", art = STUDIO_NOTES, mark = "S"),
        ),
    ),
)

/** A title word laid out in the display face: where each letter starts, its advance, the whole width. */
private class MorphingTitleMenuWord(val text: String, val xs: FloatArray, val advances: FloatArray, val width: Float)

private fun layoutWord(text: String, paint: Paint): MorphingTitleMenuWord {
    val n = text.length
    val width = paint.measureText(text)
    val xs = FloatArray(n) { i -> paint.getRunAdvance(text, 0, n, 0, n, false, i) }
    val advances = FloatArray(n) { i -> (if (i + 1 < n) xs[i + 1] else width) - xs[i] }
    return MorphingTitleMenuWord(text, xs, advances, width)
}

/** [start] is the System.nanoTime() of the pick, the time base of the frame clock. */
private class Morph(val from: Int, val to: Int, val start: Long)

/**
 * @param autoPlay Opens the popover and switches lists by itself, every [LOOP] seconds.
 * @param onSelect Called with the page's index when a list is picked.
 */
@Composable
fun MorphingTitleMenu(
    modifier: Modifier = Modifier,
    pages: List<MorphingTitleMenuPage> = MorphingTitleMenuSamples,
    autoPlay: Boolean = false,
    searchLabel: String = "Search",
    onSelect: (Int) -> Unit = {},
) {
    MorphingTitleMenuSystemBars()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val currentPages by rememberUpdatedState(pages)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val titlePx = with(density) { TITLE_SIZE.dp.toPx() }
    val paint = remember(context, titlePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { Typeface.createFromAsset(context.assets, "morphingtitlemenu/morphingtitlemenu_Anton-Regular.ttf") }
                .getOrDefault(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            textSize = titlePx
            color = INK.toArgb()
        }
    }
    val capHeight = remember(paint) { Rect().also { paint.getTextBounds("H", 0, 1, it) }.height().toFloat() }
    val words = remember(paint, pages) { pages.map { layoutWord(it.title, paint) } }

    var selected by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val menu = remember { Animatable(0f) }
    val highlight = remember { Animatable(0f) }
    var morph by remember { mutableStateOf<Morph?>(null) }
    // Seconds since the morph started; read only in layer and layout blocks, so frames don't recompose.
    val morphT = remember { mutableFloatStateOf(0f) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    var pickJob by remember { mutableStateOf<Job?>(null) }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    fun openMenu() {
        menuOpen = true
        scope.launch { highlight.snapTo(selected.toFloat()) }
        scope.launch {
            menu.animateTo(1f, if (reduced) tween(200, easing = EASE_OUT) else spring(dampingRatio = 0.64f, stiffness = stiffness(0.42f)))
        }
    }

    fun closeMenu() {
        menuOpen = false
        scope.launch { menu.animateTo(0f, if (reduced) tween(200, easing = EASE_OUT) else tween(200, easing = EASE_IN)) }
    }

    fun moveHighlight(i: Int) {
        scope.launch {
            if (reduced) highlight.snapTo(i.toFloat())
            else highlight.animateTo(i.toFloat(), spring(dampingRatio = 0.78f, stiffness = stiffness(0.32f)))
        }
    }

    fun commit(i: Int) {
        if (i == selected) return
        morphT.floatValue = 0f
        morph = Morph(selected, i, System.nanoTime())
        selected = i
        currentOnSelect(i)
    }

    /** A row of the popover was picked: the highlight moves to it, the popover closes and the title morphs. */
    fun pick(i: Int) {
        moveHighlight(i)
        pickJob?.cancel()
        pickJob = scope.launch {
            delay((CLOSE_AFTER_PICK * 1000).toLong())
            closeMenu()
            delay((COMMIT_AFTER_CLOSE * 1000).toLong())
            commit(i)
        }
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    // The morph's clock, frame by frame, until it is over.
    LaunchedEffect(morph) {
        val m = morph ?: return@LaunchedEffect
        val total = if (reduced) FADE else morphDuration(max(currentPages[m.from].title.length, currentPages[m.to].title.length))
        // Timed from the pick, as in the other stacks, not from the first frame after it.
        while (true) {
            val now = withFrameNanos { it }
            morphT.floatValue = max(0f, (now - m.start) / 1e9f)
            if (morphT.floatValue >= total) break
        }
        morph = null
    }

    LaunchedEffect(autoPlay && !reducedMotion, touches) {
        if (!autoPlay || reducedMotion) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            pickJob?.cancel()
            if (menuOpen) {
                closeMenu()
                delay(250)
            }
            if (selected != 0) {
                commit(0)
                delay(1000)
            }
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((offset, target) in listOf(0f to 1, HALF to 0)) {
                if (target >= currentPages.size) continue
                until(offset + OPEN_AT)
                openMenu()
                until(offset + PICK_AT)
                moveHighlight(target)
                until(offset + PICK_AT + CLOSE_AFTER_PICK)
                closeMenu()
                until(offset + PICK_AT + CLOSE_AFTER_PICK + COMMIT_AFTER_CLOSE)
                commit(target)
            }
            origin += (LOOP * 1e9f).toLong()
            until(0f)
        }
    }

    // The title's width as it is drawn now, in px.
    fun titleWidth(): Float {
        val m = morph ?: return words[selected].width
        val a = words[m.from]
        val b = words[m.to]
        val e = morphT.floatValue
        val t = if (reduced) clamp01(e / FADE) else {
            val total = morphDuration(max(a.text.length, b.text.length))
            easeInOutCubic(clamp01((e - 0.08f) / (total - 0.08f)))
        }
        return a.width + (b.width - a.width) * t
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val headerY = insets.calculateTopPadding() + HEADER_TOP
    val noRipple = remember { MutableInteractionSource() }

    Box(modifier.fillMaxSize().background(BACKGROUND)) {
        // The lists, cross-fading.
        Box(
            Modifier
                .padding(top = headerY + TITLE_BOX + SUBTITLE_TOP)
                .padding(horizontal = SIDE)
                .fillMaxWidth(),
        ) {
            pages.forEachIndexed { i, page ->
                val on by animateFloatAsState(
                    if (selected == i) 1f else 0f,
                    if (reducedMotion) tween((FADE * 1000).toInt(), easing = EASE_OUT) else tween(350, easing = EASE_OUT),
                    label = "page",
                )
                Box(
                    Modifier.graphicsLayer {
                        alpha = on
                        if (!reducedMotion) {
                            translationY = (1 - on) * 8.dp.toPx()
                            val b = (1 - on) * 6.dp.toPx()
                            renderEffect = if (b > 0.05f && Build.VERSION.SDK_INT >= 31) BlurEffect(sigmaToRadius(b), sigmaToRadius(b), TileMode.Decal) else null
                        }
                    },
                ) {
                    if (on > 0.001f) MorphingTitleMenuPageView(page)
                }
            }
        }

        // The header: the title (a menu) and the search pill.
        Row(
            Modifier
                .padding(top = headerY)
                .padding(horizontal = SIDE)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            val word = words[selected]
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val w = (titleWidth() + CHEVRON_GAP.toPx() + CHEVRON_SIZE.toPx()).roundToInt()
                        val h = TITLE_BOX.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.place(0, 0) }
                    }
                    .semantics { contentDescription = word.text }
                    .clickable(interactionSource = noRipple, indication = null, role = Role.Button) {
                        touched()
                        if (menuOpen) closeMenu() else {
                            tick()
                            openMenu()
                        }
                    },
            ) {
                // The letters, on a canvas larger than the title so they can fall, rise and blur.
                Box(
                    Modifier.layout { measurable, _ ->
                        val pad = CANVAS_PAD.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(CANVAS_WIDTH.roundToPx(), TITLE_BOX.roundToPx() + 2 * pad))
                        layout(0, 0) { placeable.place(-pad, -pad) }
                    },
                ) {
                    MorphingTitleMenuLetters(words, selected, morph, morphT, reducedMotion, paint, capHeight)
                }
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (titleWidth() + CHEVRON_GAP.toPx()).roundToInt(),
                                (BASELINE.dp.toPx() - capHeight / 2 - CHEVRON_SIZE.toPx() / 2).roundToInt(),
                            )
                        }
                        .size(CHEVRON_SIZE)
                        .drawBehind { icon(MorphingTitleMenuIcon.Chevrons, CHEVRON) },
                )
            }
            Spacer(Modifier.weight(1f))
            val pillTop = with(density) { (BASELINE.dp.toPx() - capHeight / 2).toDp() } - 23.dp
            Row(
                Modifier
                    .padding(top = pillTop.coerceAtLeast(0.dp))
                    .height(46.dp)
                    .dropShadow(CircleShape, Shadow(radius = 12.dp, color = Color.Black, offset = DpOffset(0.dp, 5.dp), alpha = 0.07f))
                    .dropShadow(CircleShape, Shadow(radius = 1.dp, color = Color.Black, offset = DpOffset(0.dp, 0.5.dp), alpha = 0.05f))
                    .background(Color(0xFFFAF9FA), CircleShape)
                    .drawBehind {
                        val s = 1.dp.toPx()
                        drawRoundRect(Color.White, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), CornerRadius(size.height / 2), style = Stroke(s))
                    }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(searchLabel, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ICON), maxLines = 1)
                Box(Modifier.size(19.dp).drawBehind { icon(MorphingTitleMenuIcon.Search, ICON) })
            }
        }

        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = noRipple, indication = null) {
                        touched()
                        closeMenu()
                    },
            )
        }

        // The popover.
        Box(
            Modifier
                .offset(x = MENU_X, y = headerY + TITLE_BOX + MENU_GAP)
                .width(MENU_WIDTH)
                .graphicsLayer {
                    val p = menu.value
                    if (reducedMotion) {
                        alpha = clamp01(p)
                    } else {
                        val s = 0.45f + 0.55f * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.12f, 0f)
                        alpha = clamp01(p * 1.8f)
                        val b = max(0f, 1 - p) * 10.dp.toPx()
                        renderEffect = if (b > 0.05f && Build.VERSION.SDK_INT >= 31) BlurEffect(sigmaToRadius(b), sigmaToRadius(b), TileMode.Decal) else null
                    }
                }
                .dropShadow(RoundedCornerShape(MENU_RADIUS), Shadow(radius = 34.dp, color = Color.Black, offset = DpOffset(0.dp, 16.dp), alpha = 0.13f))
                .dropShadow(RoundedCornerShape(MENU_RADIUS), Shadow(radius = 2.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.06f))
                .background(Color.White, RoundedCornerShape(MENU_RADIUS))
                .padding(MENU_INSET),
        ) {
            // The highlight and its check travel together from row to row.
            Box(
                Modifier
                    .offset { IntOffset(0, (highlight.value * MENU_ROW.toPx()).roundToInt()) }
                    .fillMaxWidth()
                    .height(MENU_ROW)
                    .background(PILL, RoundedCornerShape(MENU_RADIUS - MENU_INSET)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .padding(end = 11.dp)
                        .size(21.dp)
                        .background(INK, CircleShape)
                        .padding(3.5.dp)
                        .drawBehind { icon(MorphingTitleMenuIcon.Check, Color.White) },
                )
            }
            Column {
                pages.forEachIndexed { i, page ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(MENU_ROW)
                            .clickable(enabled = menuOpen, interactionSource = noRipple, indication = null, role = Role.Button) {
                                touched()
                                tick()
                                pick(i)
                            }
                            .padding(start = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(page.label, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = INK2), maxLines = 1)
                    }
                }
            }
        }

        // The mini player.
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = insets.calculateBottomPadding() + 8.dp), contentAlignment = Alignment.BottomCenter) {
            MorphingTitleMenuMiniPlayer(pages[0].episodes[0])
        }
    }
}

/** The title's letters: the word at rest, or both words mid-morph, each letter on its own layer. */
@Composable
private fun MorphingTitleMenuLetters(
    words: List<MorphingTitleMenuWord>,
    selected: Int,
    morph: Morph?,
    clock: androidx.compose.runtime.MutableFloatState,
    reduced: Boolean,
    paint: Paint,
    capHeight: Float,
) {
    Box(Modifier.fillMaxSize()) {
        val m = morph
        if (m == null) {
            val w = words[selected]
            for (i in w.text.indices) MorphingTitleMenuLetter(w, i, paint, capHeight) { _ -> Pose(0f, 1f, 0f, 1f) }
        } else {
            val a = words[m.from]
            val b = words[m.to]
            for (i in 0 until max(a.text.length, b.text.length)) {
                val start = i * STAGGER
                if (i < a.text.length) {
                    MorphingTitleMenuLetter(a, i, paint, capHeight) { size ->
                        val e = clock.floatValue
                        if (reduced) Pose(0f, 1f, 0f, 1 - clamp01(e / FADE))
                        else {
                            val p = clamp01((e - start) / OUT_DURATION)
                            if (p < 1f) poseOut(p, size) else Pose(0f, 1f, 0f, 0f)
                        }
                    }
                }
                if (i < b.text.length) {
                    MorphingTitleMenuLetter(b, i, paint, capHeight) { size ->
                        val e = clock.floatValue
                        if (reduced) Pose(0f, 1f, 0f, clamp01(e / FADE))
                        else {
                            val q = clamp01((e - start - IN_DELAY) / IN_DURATION)
                            if (q > 0f) poseIn(q, size) else Pose(0f, 1f, 0f, 0f)
                        }
                    }
                }
            }
        }
    }
}

/** One letter, moved, scaled about its centre (half its advance, half the cap height) and blurred. */
@Composable
private fun MorphingTitleMenuLetter(word: MorphingTitleMenuWord, i: Int, paint: Paint, capHeight: Float, pose: (size: Float) -> Pose) {
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val pad = CANVAS_PAD.toPx()
                val cx = pad + word.xs[i] + word.advances[i] / 2
                val cy = pad + BASELINE.dp.toPx() - capHeight / 2
                val p = pose(paint.textSize)
                alpha = p.opacity
                translationY = p.dy
                scaleX = p.scale
                scaleY = p.scale
                transformOrigin = TransformOrigin(cx / size.width, cy / size.height)
                renderEffect = if (p.blur > 0.05f && Build.VERSION.SDK_INT >= 31) BlurEffect(sigmaToRadius(p.blur), sigmaToRadius(p.blur), TileMode.Decal) else null
            }
            .drawBehind {
                val pad = CANVAS_PAD.toPx()
                drawContext.canvas.nativeCanvas.drawText(word.text, i, i + 1, pad + word.xs[i], pad + BASELINE.dp.toPx(), paint)
            },
    )
}

@Composable
private fun MorphingTitleMenuPageView(page: MorphingTitleMenuPage) {
    Column(Modifier.fillMaxWidth()) {
        Text(page.subtitle, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MUTE), maxLines = 1)
        Column(Modifier.padding(top = LIST_TOP), verticalArrangement = Arrangement.spacedBy(EPISODE_GAP)) {
            page.episodes.forEach { MorphingTitleMenuEpisodeRow(it) }
        }
    }
}

@Composable
private fun MorphingTitleMenuEpisodeRow(episode: MorphingTitleMenuEpisode) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MorphingTitleMenuArt(episode.art, episode.mark, 20, 5)
            Text(episode.show, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = INK2), maxLines = 1)
            Text(episode.whenText, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FAINT), maxLines = 1)
            Box(
                Modifier
                    .size(15.dp)
                    .background(Color(0xFFDDDBDF), RoundedCornerShape(4.dp))
                    .padding(2.dp)
                    .drawBehind { icon(MorphingTitleMenuIcon.Check, Color.White) },
            )
        }
        Text(
            episode.title,
            modifier = Modifier.padding(top = 9.dp),
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = INK),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        episode.progress?.let { progress ->
            Box(
                Modifier
                    .padding(top = 13.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(TRACK, CircleShape),
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(FILL, CircleShape))
            }
        }
        Row(Modifier.padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .height(36.dp)
                    .background(CHIP, CircleShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(15.dp).drawBehind { icon(if (episode.playing) MorphingTitleMenuIcon.Pause else MorphingTitleMenuIcon.Play, INK) })
                Text(if (episode.playing) "Pause" else "Play", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = INK), maxLines = 1)
                Text(episode.length, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MUTE), maxLines = 1)
            }
            Box(Modifier.size(36.dp).background(CHIP, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).drawBehind { icon(MorphingTitleMenuIcon.Archive, ICON) })
            }
            Box(Modifier.width(30.dp).height(36.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(20.dp).drawBehind { icon(MorphingTitleMenuIcon.Dots, FAINT) })
            }
        }
    }
}

@Composable
private fun MorphingTitleMenuMiniPlayer(now: MorphingTitleMenuEpisode) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .dropShadow(CircleShape, Shadow(radius = 22.dp, color = Color.Black, offset = DpOffset(0.dp, 10.dp), alpha = 0.10f))
            .dropShadow(CircleShape, Shadow(radius = 1.dp, color = Color.Black, offset = DpOffset(0.dp, 0.5.dp), alpha = 0.05f))
            .background(Color.White, CircleShape)
            .padding(start = 10.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MorphingTitleMenuArt(now.art, now.mark, 44, 12)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(now.title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = INK), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${now.show} · ${now.length} left", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MUTE), maxLines = 1)
        }
        Box(Modifier.size(42.dp).background(INK, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(16.dp).drawBehind { icon(MorphingTitleMenuIcon.Pause, Color.White) })
        }
    }
}

/** A show's cover: a two-colour square with its initial. */
@Composable
private fun MorphingTitleMenuArt(colors: List<Color>, mark: String, size: Int, radius: Int) {
    Box(
        Modifier
            .size(size.dp)
            .background(Brush.linearGradient(colors), RoundedCornerShape(radius.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(mark, style = TextStyle(fontSize = (size * 0.46f).sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.92f)))
    }
}

private enum class MorphingTitleMenuIcon { Chevrons, Search, Check, Pause, Play, Archive, Dots }

/** The few icons of the screen, drawn in a 24-unit box centred in the draw area, as in the other stacks. */
private fun DrawScope.icon(kind: MorphingTitleMenuIcon, color: Color) {
    val s = min(size.width, size.height) / 24f
    translate((size.width - 24 * s) / 2, (size.height - 24 * s) / 2) {
        scale(s, s, pivot = Offset.Zero) {
            fun stroke(path: Path, width: Float) = drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            when (kind) {
                MorphingTitleMenuIcon.Chevrons -> stroke(
                    Path().apply {
                        moveTo(6.5f, 9f); lineTo(12f, 3.5f); lineTo(17.5f, 9f)
                        moveTo(6.5f, 15f); lineTo(12f, 20.5f); lineTo(17.5f, 15f)
                    },
                    2.4f,
                )
                MorphingTitleMenuIcon.Search -> stroke(
                    Path().apply {
                        addOval(GeoRect(4.25f, 4.25f, 16.75f, 16.75f))
                        moveTo(15.3f, 15.3f); lineTo(20f, 20f)
                    },
                    2.3f,
                )
                MorphingTitleMenuIcon.Check -> stroke(
                    Path().apply { moveTo(6.5f, 12.5f); lineTo(10.2f, 16f); lineTo(17.5f, 8.5f) },
                    2.8f,
                )
                MorphingTitleMenuIcon.Pause -> {
                    drawRoundRect(color, Offset(6f, 4.5f), Size(4f, 15f), CornerRadius(1.3f))
                    drawRoundRect(color, Offset(14f, 4.5f), Size(4f, 15f), CornerRadius(1.3f))
                }
                MorphingTitleMenuIcon.Play -> {
                    val p = Path().apply { moveTo(7f, 4.8f); lineTo(19.2f, 12f); lineTo(7f, 19.2f); close() }
                    drawPath(p, color)
                    stroke(p, 1.5f)
                }
                MorphingTitleMenuIcon.Archive -> stroke(
                    Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(3.5f, 4.5f, 20.5f, 9f, CornerRadius(1.4f)))
                        moveTo(5f, 9f); lineTo(5f, 18f)
                        quadraticTo(5f, 19.5f, 6.5f, 19.5f)
                        lineTo(17.5f, 19.5f)
                        quadraticTo(19f, 19.5f, 19f, 18f)
                        lineTo(19f, 9f)
                        moveTo(10f, 12.8f); lineTo(14f, 12.8f)
                    },
                    1.8f,
                )
                MorphingTitleMenuIcon.Dots -> for (x in listOf(5f, 12f, 19f)) drawCircle(color, 1.7f, Offset(x, 12f))
            }
        }
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the
 * block is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun MorphingTitleMenuSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}
