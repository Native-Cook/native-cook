package com.ngocdevv.nativecook.blocks.text.splitflapboard

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// A split-flap departures board: 4 rows of 7 black flap cells with magenta letters. To change
// message every cell flips through the alphabet towards its new character: the top half of the
// old character folds down over the seam while the new one's top shows behind it, then the new
// bottom half falls into place. A cell takes at most 3 to 8 flips (fixed per cell), each 0.13 s,
// and cells start 60 ms apart left to right and 50 ms apart top to bottom. A port of the SwiftUI
// block, number for number.
//
// autoPlay timeline (LOOP = 9 s):
//   0.0-0.6   rest on the first message (also the first frame on screen)
//   0.6       flips to the second message (all cells settled by about 2.2 s)
//   3.6       flips to the third message (settled by about 5.2 s)
//   6.6       flips back to the first message (settled by about 8.2 s); rest until 9.0
// A flip's top flap folds down over 0.065 s and the bottom flap falls over the next 0.065 s,
// both as progress squared (a hinged flap starting slowly), around the seam with a 260 dp
// perspective, darkening up to 55% as they turn edge-on.
// A tap shows the next message at once; 2 s after the last tap the board flips back to the first
// message and the loop starts over.

/** Seconds. */
private const val LOOP = 9f
private val SWITCHES = listOf(0.6f to 1, 3.6f to 2, 6.6f to 0)
private const val FLIP = 0.13f
private const val COLUMN_DELAY = 0.06f
private const val ROW_DELAY = 0.05f
private const val USER_HOLD = 2f
/** Under reduced motion a message cross-fades over this long instead. */
private const val FADE = 0.25f

private const val ALPHABET = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:"

/** The most flips a cell makes, 3 to 8, fixed per cell so neighbours never move in step. */
private fun cap(row: Int, column: Int) = 3 + (row * 7 + column * 3) % 6

/** The characters a cell shows on its way from [a] to [b]: [a], then the last few letters before [b], ending on [b]. */
private fun sequence(a: Char, b: Char, cap: Int): List<Char> {
    val ia = ALPHABET.indexOf(a)
    val ib = ALPHABET.indexOf(b)
    if (a == b || ia < 0 || ib < 0) return listOf(b)
    val n = ALPHABET.length
    val distance = (ib - ia + n) % n
    val flips = min(distance, cap)
    return listOf(a) + (0 until flips).map { ALPHABET[(ib - flips + 1 + it + n) % n] }
}

private fun delayOf(row: Int, column: Int) = column * COLUMN_DELAY + row * ROW_DELAY

// Palette.
private val BACKDROP_CENTRE = Color(0xFF2B2B2D)
private val BACKDROP_EDGE = Color(0xFF141415)
private val PANEL = Color(0xFF1A1A1B)
private val PANEL_EDGE = Color(0xFF303032)
private val CELL_TOP = listOf(Color(0xFF313133), Color(0xFF282829))
private val CELL_BOTTOM = listOf(Color(0xFF252526), Color(0xFF1D1D1E))
private val SEAM = Color(0xFF0B0B0C)
private val HINGE = Color(0xFF1F1F20)
private val HINGE_EDGE = Color(0xFF3A3A3C)
private val LETTER = Color(0xFFD4146A)
private val LABEL = Color(0xFF8A8A8F)
private val HEADING = Color(0xFFEDEDEF)
private val DIM = Color(0xFF3A3A3D)

// Metrics, dp.
private const val COLUMNS = 7
private const val ROWS = 4
private val SIDE = 16.dp
private val PANEL_PADDING = 10.dp
private val COLUMN_GAP = 6.dp
private val ROW_GAP = 12.dp
/** Cell height over width. */
private const val ASPECT = 1.5f
/** Letter size over cell height. */
private const val LETTER_SCALE = 0.66f
private val RADIUS = 5.dp
/** The gap between a cell's two halves. */
private val SEAM_GAP = 1.5.dp
/** Perspective distance of the flaps. */
private const val PERSPECTIVE = 260f

private class Transition(val id: Int, val sequences: List<List<List<Char>>>, val start: Long, val duration: Float)

/**
 * @param messages Up to 4 lines of up to 7 characters (A–Z, 0–9, colon, space); each line is centred.
 * @param autoPlay Flips through the messages by itself, every [LOOP] seconds.
 */
@Composable
fun SplitFlapBoard(
    modifier: Modifier = Modifier,
    messages: List<List<String>> = listOf(
        listOf("GATE 4", "OPENS", "AT", "09:41"),
        listOf("NEXT", "TRAIN", "HARBOR", "09:55"),
        listOf("HAVE A", "GOOD", "TRIP", ""),
    ),
    title: String = "Departures",
    heading: String = "Platform 2",
    hint: String = "Tap the board for the next message",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val currentMessages by rememberUpdatedState(messages)

    val typeface = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "splitflapboard/splitflapboard_Barlow-SemiBold.ttf") }
            .getOrDefault(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
    }
    val family = remember(typeface) { FontFamily(androidx.compose.ui.text.font.Typeface(typeface)) }

    var shown by remember { mutableIntStateOf(0) }
    var transition by remember { mutableStateOf<Transition?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    // The frame clock while cells flip, in nanoseconds; read in layers and derived states only.
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    var touches by remember { mutableIntStateOf(0) }

    /** A message as a grid of characters, each line centred. */
    fun grid(index: Int): List<List<Char>> {
        val lines = currentMessages[index]
        return (0 until ROWS).map { r ->
            val line = (if (r < lines.size) lines[r] else "").uppercase().take(COLUMNS)
            val left = (COLUMNS - line.length) / 2
            (0 until COLUMNS).map { c ->
                val i = c - left
                val ch = if (i >= 0 && i < line.length) line[i] else ' '
                if (ALPHABET.indexOf(ch) >= 0) ch else ' '
            }
        }
    }

    /** The character a cell shows now (the one being revealed, mid-flip). */
    fun showing(r: Int, c: Int, now: Long): Char {
        val t = transition ?: return grid(shown)[r][c]
        val seq = t.sequences[r][c]
        if (reduced) return seq.last()
        val e = (now - t.start) / 1e9f - delayOf(r, c)
        if (e <= 0f) return seq[0]
        return seq[min(seq.size - 1, floor(e / FLIP).toInt() + 1)]
    }

    fun show(index: Int) {
        val now = System.nanoTime()
        val target = grid(index)
        var longest = 0f
        val sequences = (0 until ROWS).map { r ->
            (0 until COLUMNS).map { c ->
                val seq = sequence(showing(r, c, now), target[r][c], cap(r, c))
                longest = max(longest, delayOf(r, c) + (seq.size - 1) * FLIP)
                seq
            }
        }
        serial++
        clock.longValue = now
        transition = Transition(serial, sequences, now, if (reduced) FADE else longest)
        shown = index
    }

    // The clock runs while a transition is on, then the transition is dropped.
    LaunchedEffect(transition) {
        val t = transition ?: return@LaunchedEffect
        while (true) {
            val now = withFrameNanos { System.nanoTime() }
            clock.longValue = now
            if ((now - t.start) / 1e9f >= t.duration + 0.05f) break
        }
        if (transition?.id == t.id) transition = null
    }

    LaunchedEffect(autoPlay && !reducedMotion, touches) {
        if (!autoPlay || reducedMotion) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            if (shown != 0) {
                show(0)
                delay((((transition?.duration ?: 0f) + 0.6f) * 1000).toLong())
            }
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((at, to) in SWITCHES) {
                if (to >= currentMessages.size) continue
                until(at)
                show(to)
            }
            origin += (LOOP * 1e9f).toLong()
            until(0f)
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val noRipple = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(BACKDROP_CENTRE, BACKDROP_EDGE), center = center, radius = size.height * 0.62f))
            },
    ) {
        val cellW = with(density) {
            val margins = 2 * SIDE.toPx() + 2 * PANEL_PADDING.toPx()
            val gaps = (COLUMNS - 1) * COLUMN_GAP.toPx()
            floor((constraints.maxWidth - margins - gaps) / COLUMNS / density.density).dp
        }
        val cellH = (cellW.value * ASPECT).roundToInt().dp
        val paint = remember(typeface, cellH) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = with(density) { (cellH.value * LETTER_SCALE).roundToInt().dp.toPx() }
                color = LETTER.toArgb()
            }
        }
        val rest = grid(shown)

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title.uppercase(),
                modifier = Modifier.padding(top = insets.calculateTopPadding() + 18.dp),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp, color = LABEL),
            )
            Text(heading, modifier = Modifier.padding(top = 6.dp), style = TextStyle(fontSize = 34.sp, fontFamily = family, color = HEADING))

            Spacer(Modifier.weight(1f))

            // The board.
            Column(
                Modifier
                    .dropShadow(RoundedCornerShape(18.dp), Shadow(radius = 24.dp, color = Color.Black, offset = DpOffset(0.dp, 14.dp), alpha = 0.5f))
                    .background(PANEL, RoundedCornerShape(18.dp))
                    .border(1.dp, PANEL_EDGE, RoundedCornerShape(18.dp))
                    .semantics { contentDescription = currentMessages[shown].filter { it.isNotEmpty() }.joinToString(" ") }
                    .clickable(interactionSource = noRipple, indication = null, role = Role.Button) {
                        touches++
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        show((shown + 1) % currentMessages.size)
                    }
                    .padding(PANEL_PADDING),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                for (r in 0 until ROWS) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
                            for (c in 0 until COLUMNS) {
                                val t = transition
                                SplitFlapBoardCell(
                                    sequence = t?.sequences?.get(r)?.get(c) ?: listOf(rest[r][c]),
                                    start = t?.start,
                                    delay = delayOf(r, c),
                                    clock = clock,
                                    reduced = reducedMotion,
                                    w = cellW,
                                    h = cellH,
                                    paint = paint,
                                )
                            }
                        }
                        SplitFlapBoardSeam(cellW)
                    }
                }
            }

            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.indices.forEach { i ->
                    val on = i == shown
                    val width by animateDpAsState(if (on) 18.dp else 6.dp, if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 322f), label = "dot")
                    val color by animateColorAsState(if (on) LETTER else DIM, if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 322f), label = "dot")
                    Box(Modifier.width(width).height(6.dp).background(color, CircleShape))
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                hint,
                modifier = Modifier.padding(bottom = insets.calculateBottomPadding() + 28.dp),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LABEL),
            )
        }
    }
}

/** The seam line across a row, with a hinge block at both ends and between cells. */
@Composable
private fun SplitFlapBoardSeam(cellW: Dp) {
    val width = cellW * COLUMNS + COLUMN_GAP * (COLUMNS - 1)
    Box(Modifier.width(width + 8.dp).height(9.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SEAM))
        for (i in 0..COLUMNS) {
            val x = when (i) {
                0 -> -width / 2 - 1.dp
                COLUMNS -> width / 2 + 1.dp
                else -> -width / 2 + (cellW + COLUMN_GAP) * i - COLUMN_GAP / 2
            }
            Box(
                Modifier
                    .offset(x = x)
                    .size(5.dp, 9.dp)
                    .background(HINGE, RoundedCornerShape(1.5.dp))
                    .border(0.75.dp, HINGE_EDGE, RoundedCornerShape(1.5.dp)),
            )
        }
    }
}

/**
 * One flap cell: the static top and bottom halves, and while it flips, the falling flap. The
 * cell recomposes only when its flip step changes; the flap's angle and shade are read in its layer.
 */
@Composable
private fun SplitFlapBoardCell(
    sequence: List<Char>,
    start: Long?,
    delay: Float,
    clock: LongState,
    reduced: Boolean,
    w: Dp,
    h: Dp,
    paint: Paint,
) {
    fun elapsed() = if (start == null) Float.MAX_VALUE else (clock.longValue - start) / 1e9f - delay

    if (reduced && start != null && sequence.size > 1) {
        Box(Modifier.size(w, h)) {
            Box(Modifier.graphicsLayer { alpha = 1 - ((clock.longValue - start) / 1e9f / FADE).coerceIn(0f, 1f) }) {
                SplitFlapBoardStill(sequence.first(), w, h, paint)
            }
            Box(Modifier.graphicsLayer { alpha = ((clock.longValue - start) / 1e9f / FADE).coerceIn(0f, 1f) }) {
                SplitFlapBoardStill(sequence.last(), w, h, paint)
            }
        }
        return
    }

    // -1 before the cell starts, then the flip under way, then size - 1 when it has landed.
    val step by remember(sequence, start) {
        derivedStateOf {
            val e = elapsed()
            if (e <= 0f) -1 else min(sequence.size - 1, floor(e / FLIP).toInt())
        }
    }
    if (step < 0 || step >= sequence.size - 1) {
        SplitFlapBoardStill(if (step < 0) sequence[0] else sequence.last(), w, h, paint)
        return
    }
    val k = step
    val halfH = (h - SEAM_GAP) / 2
    val cameraDp = PERSPECTIVE
    fun progress() = ((elapsed() - k * FLIP) / FLIP).coerceIn(0f, 1f)
    Box(Modifier.size(w, h)) {
        Column(verticalArrangement = Arrangement.spacedBy(SEAM_GAP)) {
            SplitFlapBoardHalf(sequence[k + 1], true, w, h, paint)
            SplitFlapBoardHalf(sequence[k], false, w, h, paint)
        }
        // The top half of the old character, folding down over the seam.
        Box(
            Modifier.graphicsLayer {
                val p = progress()
                val q = (p / 0.5f).coerceIn(0f, 1f)
                alpha = if (p < 0.5f) 1f else 0f
                rotationX = -90f * q * q
                transformOrigin = TransformOrigin(0.5f, 1f)
                cameraDistance = cameraDp.dp.toPx() / 72f
            },
        ) {
            SplitFlapBoardHalf(sequence[k], true, w, h, paint) { val q = (progress() / 0.5f).coerceIn(0f, 1f); 0.55f * q }
        }
        // The bottom half of the new character, falling into place.
        Box(
            Modifier
                .offset(y = h - halfH)
                .graphicsLayer {
                    val p = progress()
                    val q = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    alpha = if (p >= 0.5f) 1f else 0f
                    rotationX = 90f * (1 - q * q)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = cameraDp.dp.toPx() / 72f
                },
        ) {
            SplitFlapBoardHalf(sequence[k + 1], false, w, h, paint) { val q = ((progress() - 0.5f) / 0.5f).coerceIn(0f, 1f); 0.55f * (1 - q * q) }
        }
    }
}

@Composable
private fun SplitFlapBoardStill(char: Char, w: Dp, h: Dp, paint: Paint) {
    Column(Modifier.size(w, h), verticalArrangement = Arrangement.spacedBy(SEAM_GAP)) {
        SplitFlapBoardHalf(char, true, w, h, paint)
        SplitFlapBoardHalf(char, false, w, h, paint)
    }
}

/**
 * Half a cell: its own piece of the rounded card and the matching half of the letter, set with
 * the middle of its capitals (0.35 em above the baseline) on the seam. [shade] darkens it, read at
 * draw time.
 */
@Composable
private fun SplitFlapBoardHalf(char: Char, isTop: Boolean, w: Dp, h: Dp, paint: Paint, shade: (() -> Float)? = null) {
    val halfH = (h - SEAM_GAP) / 2
    val shape = if (isTop) RoundedCornerShape(RADIUS, RADIUS, 1.dp, 1.dp) else RoundedCornerShape(1.dp, 1.dp, RADIUS, RADIUS)
    val text = char.toString()
    Box(
        Modifier
            .size(w, halfH)
            .clip(shape)
            .background(Brush.verticalGradient(if (isTop) CELL_TOP else CELL_BOTTOM))
            .then(if (isTop) Modifier.border(1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.Transparent), shape) else Modifier)
            .drawWithContent {
                drawContent()
                // The cell's centre sits half the seam gap past this half's inner edge.
                val centreY = if (isTop) size.height + SEAM_GAP.toPx() / 2 else -SEAM_GAP.toPx() / 2
                val baseline = centreY + 0.35f * paint.textSize
                val x = (size.width - paint.measureText(text)) / 2
                drawContext.canvas.nativeCanvas.drawText(text, x, baseline, paint)
                val s = shade?.invoke() ?: 0f
                if (s > 0f) drawRect(Color.Black.copy(alpha = s))
            },
    )
}
