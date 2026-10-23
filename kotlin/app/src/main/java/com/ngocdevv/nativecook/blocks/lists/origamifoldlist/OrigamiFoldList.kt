package com.ngocdevv.nativecook.blocks.lists.origamifoldlist

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * @param depth Maximum depth in metres.
 * @param temperature Water temperature in °C.
 * @param season Months of the season, 0 = January.
 * @param entry A line under the photo strip in the last panel.
 * @param thumb An Unsplash photo id for the square thumbnail; [strip] the wide photo in the last panel.
 */
data class DiveSpot(
    val name: String,
    val place: String,
    val depth: Int,
    val temperature: Int,
    val visibility: Int,
    val current: String,
    val level: String,
    val season: List<Int>,
    val entry: String,
    val thumb: String,
    val strip: String,
)

private val Spots = listOf(
    DiveSpot("Blue Corner", "Koror · Palau", 30, 28, 30, "Strong drift", "Advanced", listOf(11, 0, 1, 2, 3), "Reef hook on the wall · 45 min by boat", "photo-1583212292454-1fe6229603b7", "photo-1494879540385-bc170b0878a7"),
    DiveSpot("Barracuda Point", "Sipadan · Malaysia", 25, 29, 20, "Moderate", "Intermediate", listOf(3, 4, 5, 6, 7, 8, 9, 10), "Negative entry off the drop-off · 10 min out", "photo-1637308107080-82cf40953742", "photo-1507295386538-ddd5e86cd597"),
    DiveSpot("Tubbataha North", "Sulu Sea · Philippines", 40, 27, 35, "Mild", "Advanced", listOf(2, 3, 4, 5), "Liveaboard only · twelve hours offshore", "photo-1514907283155-ea5f4094c70c", "photo-1508624217470-5ef0f947d8be"),
    DiveSpot("Crystal Bay", "Nusa Penida · Indonesia", 18, 22, 25, "Surge", "Intermediate", listOf(6, 7, 8, 9), "Sheltered cove · cold upwellings bring the sunfish", "photo-1701673505069-45a4078044ba", "photo-1517219039361-66f283bce5db"),
    DiveSpot("Ras Mohammed", "Red Sea · Egypt", 35, 25, 30, "Mild drift", "Advanced", listOf(3, 4, 5, 6, 7, 8, 9, 10), "Shark and Yolanda reefs · day boat from the marina", "photo-1637308109237-4ea1a7dd22f5", "photo-1470217407524-b1e77afc6ec5"),
    DiveSpot("Cod Hole", "Coral Sea · Australia", 24, 26, 30, "Gentle", "Beginner", listOf(5, 6, 7, 8, 9, 10), "Ribbon reefs · potato cod come to say hello", "photo-1694661641480-d4e76d6bead3", "photo-1466094899371-97b327dff551"),
)

private val Months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

private fun photoUrl(id: String, width: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$width&q=80"

private val Ink = Color(0xFF071519)
private val Sea = Color(0xFF0F232A)
private val Paper = Color(0xFFF1EADC)
private val PaperInk = Color(0xFF0C2A31)
private val Muted = Color(0xFF7C9EA6)
private val Coral = Color(0xFFFF7D5C)
private val Sans = FontFamily.SansSerif

// Rows, in dp.
private const val SIDE = 18f
private const val ROW_HEIGHT = 78f
private const val ROW_GAP = 10f
private const val THUMB = 58f
// The fold under a row: three panels of PANEL_HEIGHT dp, and a FOLD_GAP between the row and the paper.
private const val PANELS = 3
private const val PANEL_HEIGHT = 76f
private const val FOLD_GAP = 6f
private const val FOLD_HEIGHT = PANELS * PANEL_HEIGHT
// Each panel starts to open STAGGER (of the whole opening) after the one above it, so the paper
// unfolds one panel after another. Panels hinge on their top edges, seen through PERSPECTIVE.
private const val STAGGER = 0.24f
private const val PERSPECTIVE = 700f
// How dark a fold's shadow is while it is shut.
private const val SHADE = 0.78f
// Past fully open, the paper stretches with a quarter of the finger's travel.
private const val RUBBER = 0.25f

// The spring that opens and closes a fold, in fold-lengths: stiffness, damping (mass 1).
private const val STIFFNESS = 130f
private const val DAMPING = 20f
// A release opens the fold when the projected amount (now + velocity × THROW) passes half.
private const val THROW = 0.2f
// Reduced motion: the fold eases to its target at this rate per second, flat and fading.
private const val REDUCED_RATE = 9f
// The pan starts after 8 dp of vertical travel and gives up after 24 dp of sideways travel.
private const val PAN_SLOP = 8f
private const val PAN_FAIL = 24f
private const val TAP_MS = 300L

// autoPlay (seconds): unfold a row, unfold another (the first folds back), a third, fold all.
private const val LOOP = 11f
private val Cues = listOf(0.8f to 1, 3.4f to 3, 6.0f to 0, 8.6f to -1)
private const val USER_HOLD = 4f
// Entering the screen: the script waits ENTRY_DELAY s before its first cue, the header fades in and
// the rows fade up LIST_RISE dp over LIST_IN s once the header has been measured, and the folded
// paper under the rows is built one row per frame from FOLDS_AFTER s, after the screen has settled,
// so the first frames only carry what is visible.
private const val ENTRY_DELAY = 1.2f
private const val LIST_IN = 0.32f
private const val LIST_RISE = 10f
private const val FOLDS_AFTER = 0.38f
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
// The header is gone once the list has slid up this far, in dp.
private const val HEADER_FADE = 90f

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** How far panel `k` is open, 0 … 1, for a fold opened by `open`. */
private fun panelOpen(open: Float, k: Int) = clamp01((open - k * STAGGER) / (1f - (PANELS - 1) * STAGGER))

/** The height a panel takes on screen while it is `p` open. */
private fun shownHeight(p: Float, reduced: Boolean) = PANEL_HEIGHT * if (reduced) p else sin(p * PI.toFloat() / 2f)

/** The height a fold takes on screen: each panel shows its height × sin of how far it has turned. */
private fun foldHeight(open: Float, reduced: Boolean): Float {
    var h = 0f
    for (k in 0 until PANELS) h += shownHeight(panelOpen(open, k), reduced)
    val gap = FOLD_GAP * clamp01(open * 4f)
    return h + gap + max(0f, open - 1f) * FOLD_HEIGHT
}

/** Where panel `k` hangs under its row. */
private fun panelTop(k: Int, open: Float, reduced: Boolean): Float {
    var top = FOLD_GAP * clamp01(open * 4f)
    for (j in 0 until k) top += shownHeight(panelOpen(open, j), reduced)
    // Stretched past open, the panels part a little.
    return top + max(0f, open - 1f) * FOLD_HEIGHT * (k.toFloat() / PANELS)
}

/** Every row's fold, stepped once a frame like the RN frame callback, and the gestures that move them. */
private class FoldMotion(val count: Int) {
    // Bumped every frame; the layers that move read it, so they are drawn again.
    var tick by mutableLongStateOf(0L)
    // How many rows have their folded paper built (see FOLDS_AFTER).
    var built by mutableIntStateOf(0)
    var elapsed = 0f
    // When the list was first placed (the header measured), for its fade-up.
    var listInAt = -1f
    // Per row: how far its fold is open (1 = open), its speed, and where it is going.
    val opens = FloatArray(count)
    val speeds = FloatArray(count)
    val targets = FloatArray(count)
    var dragRow = -1
    var dragFrom = 0f
    var clock = 0f
    var looping = false
    var hold = 0f
    var reduced = false
    // Where the first row sits, and the lowest an open fold may reach.
    var top = 0f
    var bottom = 0f
    // Filled by `layout`.
    val tops = FloatArray(count)
    val folds = FloatArray(count)
    var shift = 0f
    var onOpen: (Int) -> Unit = {}
    var onHaptic: () -> Unit = {}

    fun step(dt: Float) {
        elapsed += dt
        if (listInAt < 0f && top > 0f) listInAt = elapsed
        if (elapsed >= FOLDS_AFTER && built < count) built += 1
        if (dragRow < 0) {
            if (hold > 0f) {
                hold -= dt
                if (hold <= 0f) clock = 0f
            } else if (looping) {
                val before = clock
                val t = (before + dt) % LOOP
                clock = t
                for ((at, row) in Cues) {
                    if ((before < at && t >= at) || (t < before && (at > before || at <= t))) openOnly(row)
                }
            }
        }
        for (k in 0 until count) {
            if (k == dragRow) continue
            if (abs(targets[k] - opens[k]) < 0.0005f && abs(speeds[k]) < 0.001f) {
                opens[k] = targets[k]
                speeds[k] = 0f
                continue
            }
            if (reduced) {
                opens[k] += (targets[k] - opens[k]) * (1f - exp(-REDUCED_RATE * dt))
                speeds[k] = 0f
            } else {
                speeds[k] += (STIFFNESS * (targets[k] - opens[k]) - DAMPING * speeds[k]) * dt
                opens[k] += speeds[k] * dt
            }
        }
        layout()
        tick += 1
    }

    /**
     * Where each row sits: below the rows above it and their folds. When an open fold would run off
     * the bottom, the whole list slides up to keep it in view.
     */
    fun layout() {
        var y = top
        var s = 0f
        for (k in 0 until count) {
            tops[k] = y
            val f = foldHeight(opens[k], reduced)
            folds[k] = f
            if (opens[k] > 0.001f) s = max(s, y + ROW_HEIGHT + f - bottom)
            y += ROW_HEIGHT + f + ROW_GAP
        }
        shift = max(0f, s)
    }

    /** Sends one row's fold open (or `-1` for none) and every other one shut. */
    fun openOnly(row: Int) {
        for (k in 0 until count) targets[k] = if (k == row) 1f else 0f
        onOpen(row)
    }

    /** The row under a point of the screen, folds included, or -1. */
    fun rowAt(y: Float): Int {
        for (k in 0 until count) {
            val t = tops[k] - shift
            if (y >= t - ROW_GAP / 2f && y <= t + ROW_HEIGHT + folds[k] + ROW_GAP / 2f) return k
        }
        return -1
    }

    fun panStart(y: Float) {
        val row = rowAt(y)
        if (row !in 0 until count) return
        built = count
        hold = USER_HOLD
        dragRow = row
        dragFrom = opens[row]
        // Only one fold open at a time: the others start folding away.
        for (k in 0 until count) if (k != row) targets[k] = 0f
    }

    fun panUpdate(translationY: Float) {
        if (dragRow !in 0 until count) return
        val raw = dragFrom + translationY / FOLD_HEIGHT
        opens[dragRow] = if (raw > 1f) 1f + (raw - 1f) * RUBBER else max(0f, raw)
    }

    fun panEnd(velocityY: Float) {
        val row = dragRow
        dragRow = -1
        if (row < 0) return
        if (row >= count) return
        // A velocity the tracker could not measure counts as none.
        val velocity = if (velocityY.isFinite()) velocityY / FOLD_HEIGHT else 0f
        val open = opens[row] + velocity * THROW > 0.5f
        speeds[row] = if (reduced) 0f else velocity
        if (open != dragFrom > 0.5f) onHaptic()
        if (open) {
            openOnly(row)
        } else {
            targets[row] = 0f
            onOpen(-1)
        }
    }

    /** How far the list has faded up, 0 … 1 (eased); 0 until the header has been measured. */
    fun shown(): Float {
        if (listInAt < 0f) return 0f
        return EaseOut.transform(clamp01((elapsed - listInAt) / if (reduced) 0.2f else LIST_IN))
    }

    fun tap(y: Float) {
        val row = rowAt(y)
        if (row < 0) return
        hold = USER_HOLD
        built = count
        onHaptic()
        openOnly(if (targets[row] > 0.5f) -1 else row)
    }
}

/**
 * A logbook of dive sites. Tapping a row unfolds its details under it like folded paper, one
 * panel after another, pushing the rows below down; tapping it again folds it back. Dragging a
 * row down opens the fold under the finger, and letting go finishes it with a spring.
 *
 * @param autoPlay Opens and closes a few rows on a loop of 11 seconds.
 * @param onOpen Called with the index of the row that opens, or -1 when all are folded.
 */
@Composable
fun OrigamiFoldList(
    modifier: Modifier = Modifier,
    spots: List<DiveSpot> = Spots,
    eyebrow: String = "Logbook · 2026",
    title: String = "Where the reef\nopens up",
    autoPlay: Boolean = false,
    onOpen: ((Int) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val opened by rememberUpdatedState(onOpen)
    val motion = remember(spots) { FoldMotion(spots.size) }
    motion.reduced = reduced
    motion.onOpen = { opened?.invoke(it) }
    motion.onHaptic = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    var headerHeight by remember { mutableFloatStateOf(0f) }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, autoPlay) {
        motion.looping = autoPlay
        // The first cue waits for the screen to settle; later loops run from 0.
        motion.clock = -ENTRY_DELAY
        motion.hold = 0f
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.048f)
                last = now
                if (dt > 0f) motion.step(dt)
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    // 0 undecided, 1 a pan, 2 a sideways move the list lets go.
                    var state = 0
                    var released = false
                    try {
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val dx = (change.position.x - start.x) / density.density
                            val dy = (change.position.y - start.y) / density.density
                            if (!change.pressed) {
                                if (state == 1) {
                                    motion.panEnd(tracker.calculateVelocity().y / density.density)
                                    state = 3
                                } else if (state == 0 && change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    motion.tap(change.position.y / density.density)
                                }
                                released = true
                                continue
                            }
                            if (state == 0) {
                                if (abs(dx) >= PAN_FAIL) {
                                    state = 2
                                } else if (abs(dy) >= PAN_SLOP) {
                                    state = 1
                                    motion.panStart(start.y / density.density)
                                }
                            }
                            if (state == 1) {
                                change.consume()
                                motion.panUpdate(dy)
                            }
                        }
                    } finally {
                        // A pan that never saw its finger lift (the pointer left, or the gesture was
                        // cancelled) still lets go of its row.
                        if (state == 1) motion.panEnd(0f)
                    }
                }
            },
    ) {
        val width = maxWidth.value
        motion.top = if (headerHeight > 0f) headerHeight + 6f else 0f
        motion.bottom = maxHeight.value - safeBottom - 20f
        if (motion.tick == 0L) motion.layout()

        // When the list slides up for a fold near the bottom, the header slides with it and fades
        // before it reaches the status bar.
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeight = it.height / density.density }
                .graphicsLayer {
                    motion.tick
                    translationY = -motion.shift.dp.toPx()
                    alpha = (1f - clamp01(motion.shift / HEADER_FADE)) * motion.shown()
                }
                .padding(start = (SIDE + 4f).dp, end = (SIDE + 4f).dp, top = (safeTop + 22f).dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(
                eyebrow.uppercase(),
                style = TextStyle(color = Coral, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.2.sp),
            )
            BasicText(
                title,
                modifier = Modifier.semantics { heading() },
                style = TextStyle(
                    color = Paper,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 39.sp,
                    letterSpacing = (-0.8).sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                ),
            )
            BasicText(
                "Tap a site to unfold it · drag down to peek",
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(color = Muted, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp),
            )
        }

        if (headerHeight > 0f) {
            // The rows fade up together once, when they know where they sit.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        motion.tick
                        val shown = motion.shown()
                        alpha = shown
                        translationY = if (motion.reduced) 0f else ((1f - shown) * LIST_RISE).dp.toPx()
                    },
            ) {
                spots.forEachIndexed { index, spot ->
                    SpotRow(spot, index, motion, max(0f, width - SIDE * 2f), folds = index < motion.built)
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height((safeBottom + 56f).dp)
                .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0f), Ink))),
        )
    }
}

/** A dive site: its row and, under it, the folded paper with the details. */
@Composable
private fun SpotRow(spot: DiveSpot, index: Int, motion: FoldMotion, width: Float, folds: Boolean) {
    val context = LocalContext.current
    Box(
        Modifier
            .offset(x = SIDE.dp)
            .width(width.dp)
            .height(ROW_HEIGHT.dp)
            .graphicsLayer {
                motion.tick
                translationY = (motion.tops[index] - motion.shift).dp.toPx()
            },
    ) {
        // The folded paper hangs under the row, behind it; it is built a moment after the screen
        // opens (see FOLDS_AFTER).
        if (folds) {
            Box(Modifier.offset(x = 8.dp, y = ROW_HEIGHT.dp).width(max(0f, width - 16f).dp)) {
                for (k in 0 until PANELS) {
                    FoldPanel(k, index, motion) { PanelContent(k, spot) }
                }
            }
        }

        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Sea)
                .border(0.5.dp, Color(160, 210, 220).copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                .padding(horizontal = 10.dp)
                .clearAndSetSemantics {
                    contentDescription = "${spot.name}, ${spot.place}, ${spot.depth} metres, ${spot.temperature} degrees"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val thumb = remember(spot.thumb, context) { ImageRequest.Builder(context).data(photoUrl(spot.thumb, 200)).crossfade(200).build() }
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMB.dp).clip(RoundedCornerShape(15.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    spot.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Paper, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.2).sp),
                )
                BasicText(
                    spot.place,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Muted, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                BasicText(
                    buildAnnotatedString {
                        append("${spot.depth}")
                        withStyle(SpanStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)) { append(" m") }
                    },
                    style = TextStyle(color = Paper, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
                )
                BasicText(
                    "${spot.temperature}°C",
                    style = TextStyle(color = Muted, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFeatureSettings = "tnum"),
                )
            }
            // Two short strokes meeting in a point, turned with the fold.
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .size(22.dp)
                    .graphicsLayer {
                        motion.tick
                        rotationZ = clamp01(motion.opens[index]) * 180f
                    },
            ) {
                Box(Modifier.offset(x = 4.6.dp, y = 11.dp).size(8.dp, 1.8.dp).graphicsLayer { rotationZ = 40f }.clip(RoundedCornerShape(1.dp)).background(Muted))
                Box(Modifier.offset(x = (22f - 4.6f - 8f).dp, y = 11.dp).size(8.dp, 1.8.dp).graphicsLayer { rotationZ = -40f }.clip(RoundedCornerShape(1.dp)).background(Muted))
            }
        }
    }
}

/** One panel of the fold, hinged at its top edge on the bottom of the panel above. */
@Composable
private fun FoldPanel(k: Int, row: Int, motion: FoldMotion, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val last = k == PANELS - 1
    val shape = if (last) RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp) else RoundedCornerShape(0.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(PANEL_HEIGHT.dp)
            .graphicsLayer {
                motion.tick
                val open = motion.opens[row]
                val p = panelOpen(open, k)
                // The paper is folded like an accordion: every other panel leans the other way.
                val angle = if (motion.reduced) 0f else (if (k % 2 == 0) -1f else 1f) * 90f * (1f - p)
                transformOrigin = TransformOrigin(0.5f, 0f)
                translationY = panelTop(k, open, motion.reduced).dp.toPx()
                // The camera sits in "inches" of 72 px: this puts it PERSPECTIVE dp away.
                rotationX = angle
                cameraDistance = PERSPECTIVE * density.density / 72f
                alpha = if (motion.reduced) p else if (p > 0.002f) 1f else 0f
            }
            .clip(shape)
            .background(Paper),
    ) {
        content()
        // The fold's shadow: deepest along the crease the panel turns about, gone once it is flat.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    motion.tick
                    alpha = SHADE * (1f - panelOpen(motion.opens[row], k))
                }
                .background(
                    Brush.verticalGradient(
                        if (k % 2 == 0) {
                            listOf(Color(4, 14, 18).copy(alpha = 0.25f), Color(4, 14, 18))
                        } else {
                            listOf(Color(4, 14, 18), Color(4, 14, 18).copy(alpha = 0.35f))
                        },
                    ),
                ),
        )
        if (k > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(12, 42, 49).copy(alpha = 0.28f)))
    }
}

@Composable
private fun PanelContent(k: Int, spot: DiveSpot) {
    when (k) {
        0 -> Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Stat("Visibility", "${spot.visibility} m", Modifier.weight(1f))
            Stat("Current", spot.current, Modifier.weight(1f))
            Stat("Level", spot.level, Modifier.weight(1f))
        }
        1 -> Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
        ) {
            Label("Best months")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Months.forEachIndexed { i, m ->
                    val on = i in spot.season
                    Column(Modifier.width(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(14.dp, 5.dp).clip(RoundedCornerShape(2.5.dp)).background(if (on) Coral else Color(12, 42, 49).copy(alpha = 0.14f)))
                        BasicText(
                            m,
                            style = TextStyle(
                                color = if (on) PaperInk else Color(12, 42, 49).copy(alpha = 0.4f),
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                            ),
                        )
                    }
                }
            }
        }
        else -> {
            val context = LocalContext.current
            val strip = remember(spot.strip, context) { ImageRequest.Builder(context).data(photoUrl(spot.strip, 900)).crossfade(200).build() }
            Box(Modifier.fillMaxSize()) {
                AsyncImage(model = strip, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.25f to Color(4, 14, 18).copy(alpha = 0f), 1f to Color(4, 14, 18).copy(alpha = 0.72f)),
                    ),
                )
                BasicText(
                    spot.entry,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    style = TextStyle(color = Color.White, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    BasicText(
        text.uppercase(),
        style = TextStyle(color = Color(12, 42, 49).copy(alpha = 0.55f), fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 1.4.sp),
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        BasicText(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = PaperInk, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        )
    }
}
