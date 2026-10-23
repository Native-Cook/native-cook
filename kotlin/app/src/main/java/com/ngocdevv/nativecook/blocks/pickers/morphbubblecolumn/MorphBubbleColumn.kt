package com.ngocdevv.nativecook.blocks.pickers.morphbubblecolumn

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * One survey site down the column.
 *
 * @param id Unsplash photo id (a portrait photo reads best, as it opens to the whole card).
 * @param name The site's name beside its bubble.
 * @param coords Its position, shown under the name while the site is selected.
 * @param heading The heading that types itself once the photo is open, already broken into lines.
 * @param value The reading that counts up in big dot-matrix figures.
 * @param caption Two short lines under the reading; the last one gets trailing dots.
 * @param tag A short readout under the target ring.
 * @param ringX Where the target ring sits on the open photo, as a share of the card's width.
 * @param ringY The same, as a share of the card's height.
 * @param ink Text colour over the open photo.
 * @param tint Flat colour shown until the photo arrives.
 */
class MorphBubbleColumnSite(
    val id: String,
    val name: String,
    val coords: String,
    val heading: List<String>,
    val value: Int,
    val caption: List<String>,
    val tag: String,
    val ringX: Float,
    val ringY: Float,
    val ink: Int,
    val tint: Int,
)

private const val WHITE = 0xFFFFFFFF.toInt()

private val SITES = listOf(
    MorphBubbleColumnSite("photo-1618336174403-79bfae755448", "GLACIER FRONT", "64.02°N 16.21°W", listOf("MEASURE", "ICE FRONT:", "74 M"), 74, listOf("TRACKING_CALVING_", "FACE"), "FRONT SKAFT-1", 0.6f, 0.42f, WHITE, 0xFF9FB9CC.toInt()),
    MorphBubbleColumnSite("photo-1520811288788-6f2e3da049e0", "RED STRATA", "36.86°N 111.37°W", listOf("SAMPLE", "STRATA", "LAYER: 07"), 36, listOf("MAPPING_RIDGE_", "LINES"), "GRID K-12", 0.62f, 0.44f, WHITE, 0xFF9A5A3A.toInt()),
    MorphBubbleColumnSite("photo-1524799526615-766a9833dec0", "DUNE FIELD", "23.40°N 12.85°E", listOf("CALCULATE", "AZIMUTH:", "345°"), 80, listOf("ANALYZING_SURFACE_", "BIOME"), "TARGET LUNA-2", 0.51f, 0.4f, WHITE, 0xFF8FA9B8.toInt()),
    MorphBubbleColumnSite("photo-1686861453864-9b2a22a97fc9", "CRATER RIM", "0.67°N 23.47°E", listOf("MEASURE", "CRATER", "DEPTH: 2.4KM"), 12, listOf("SCANNING_CRATER_", "FIELD"), "RIM MARE-09", 0.58f, 0.46f, WHITE, 0xFF6A6A68.toInt()),
    MorphBubbleColumnSite("photo-1576059620175-9b608f96797b", "ICE VAULT", "64.40°N 16.80°W", listOf("TRACE", "MELT RATE:", "0.8 M/DAY"), 24, listOf("SOUNDING_ICE_", "CAVE"), "VAULT SKAFT-3", 0.5f, 0.5f, WHITE, 0xFF6F9CC0.toInt()),
)

private const val AVATAR = "photo-1641901960200-1e878f0cbf63"

private fun unsplash(id: String, size: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&$size&q=80"

// The other bubbles sink back a little while one opens.
private const val SINK = 0.08f
// Once open: the heading types TYPE_STEP s a character after TYPE_DELAY s, the reading counts for
// COUNT_TIME s after COUNT_DELAY s, and the cursor blinks at CURSOR_HZ.
private const val TYPE_DELAY = 0.7f
private const val TYPE_STEP = 0.06f
private const val COUNT_DELAY = 1.6f
private const val COUNT_TIME = 1.6f
private const val CURSOR_HZ = 1.6f
// The reading is set in dot-matrix figures: pixels PIXEL dp apart, with this share of each left as
// a gap.
private const val PIXEL = 8f
private const val PIXEL_GAP = 0.24f
// The open photo zooms in with the slider: 1 + ZOOM_PHOTO × slider.
private const val ZOOM_PHOTO = 0.2f
// Photos fade in over IMAGE_FADE s; thumbnails are portrait crops, the open site gets a sharp copy.
private const val IMAGE_FADE = 0.35f
private const val THUMB_SIZE = "w=400&h=880"
private const val SHARP_SIZE = "w=828&h=1820"
// A pan starts after DRAG_SLOP dp; a touch lifted within TAP_MS that never got that far is a tap.
private const val DRAG_SLOP = 4f
private const val TAP_MS = 400L

/**
 * A full-screen field-survey page: white, edge to edge, holding a column of round photo bubbles, one
 * per site, each with its name beside it. The selected site is a big rounded square; drag along the
 * column to move the selection and the bubbles morph between circle and square as it passes. Tap the
 * selected site to grow it into the whole card, where a heading types itself, a target ring locks on
 * and a big pixel reading counts up; the slider zooms into the photo, and dragging down puts it back.
 *
 * @param sites The sites down the column, top to bottom (five fit any phone).
 * @param avatar Small photo in the pill at the top right.
 * @param greeting The name in the pill.
 * @param date Lines at the top left of the card.
 * @param clock The clock in pixels: digits, ':', 'P' and 'M'.
 * @param autoPlay Walks the selection down the column, opens a site, reads it, closes it and walks back.
 * @param onOpen Called with a site's index when it opens.
 */
@Composable
fun MorphBubbleColumn(
    modifier: Modifier = Modifier,
    sites: List<MorphBubbleColumnSite> = SITES,
    avatar: String = AVATAR,
    greeting: String = "HI MARA",
    date: List<String> = listOf("THURSDAY", "15", "OCT"),
    clock: String = "9:PM",
    autoPlay: Boolean = false,
    onOpen: ((Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val opening by rememberUpdatedState(onOpen)
    val motion = remember(sites) { MorphBubbleColumnMotion(sites.size) }
    val paints = remember { MorphBubbleColumnPaints() }
    val scope = rememberCoroutineScope()
    // Bumped once a frame, so the canvas draws again.
    var frame by remember { mutableLongStateOf(0L) }

    // The block draws under the system bars and keeps its content clear of them itself. Read during
    // composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        motion.top = safeTop
        motion.bottom = safeBottom
        motion.reducedMotion = reducedMotion
    }

    LaunchedEffect(motion, auto) { motion.resetLoop(auto) }

    suspend fun load(address: String): Bitmap? {
        val request = ImageRequest.Builder(context).data(address).size(Size.ORIGINAL).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap()
    }

    // A sharp copy for the full card, fetched once per site; the thumbnail stands in until it arrives.
    fun loadSharp(index: Int) {
        if (index !in sites.indices || motion.sharps[index] != null) return
        scope.launch {
            val bitmap = load(unsplash(sites[index].id, SHARP_SIZE)) ?: return@launch
            if (motion.sharps[index] == null) motion.sharps[index] = Loaded(bitmap, motion.elapsed)
        }
    }

    // Thumbnails for the column, and the avatar; each fades in as it arrives. With autoPlay the loop's
    // site gets its sharp copy up front, so the first open is sharp.
    LaunchedEffect(motion, avatar, auto) {
        if (auto) loadSharp(AUTO_SITE)
        sites.forEachIndexed { i, site ->
            launch { load(unsplash(site.id, THUMB_SIZE))?.let { motion.thumbs[i] = Loaded(it, motion.elapsed) } }
        }
        launch { load(unsplash(avatar, "w=120&h=120"))?.let { motion.face = Loaded(it, motion.elapsed) } }
    }

    SideEffect {
        motion.onOpened = { index, user ->
            loadSharp(index)
            if (user) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                opening?.invoke(index)
            }
        }
        motion.onCloseByHand = {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
        motion.onTick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) motion.advance(dt)
                frame = now
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Survey sites in a column of photo bubbles. Drag to move the selection; tap the selected site to open it; drag down to close it."
            }
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dx = (change.position.x - start.x) / density.density
                        val dy = (change.position.y - start.y) / density.density
                        if (!change.pressed) {
                            if (panning) {
                                change.consume()
                                motion.panEnd(tracker.calculateVelocity().y / density.density)
                            } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                motion.tap(change.position.x / density.density, change.position.y / density.density)
                            }
                            released = true
                            continue
                        }
                        if (!panning && dx * dx + dy * dy >= DRAG_SLOP * DRAG_SLOP) {
                            panning = true
                            motion.panStart(start.x / density.density, start.y / density.density)
                        }
                        if (panning) {
                            change.consume()
                            motion.panUpdate(change.position.x / density.density, dy)
                        }
                    }
                    // A finger the system took away lets go where it is.
                    if (panning && !released) motion.panCancel()
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        motion.width = width
        motion.height = height
        val statics = remember(width, height, safeTop, clock) { MorphBubbleColumnStatics(width, height, safeTop, clock) }

        Box(
            Modifier.fillMaxSize().drawBehind {
                // Reading the frame counter redraws the canvas every frame.
                if (frame < 0L) return@drawBehind
                scale(density.density, density.density, pivot = Offset.Zero) {
                    drawIntoCanvas {
                        MorphBubbleColumnPainter(it.nativeCanvas, motion, sites, greeting, date, statics, paints).draw()
                    }
                }
            },
        )
    }
}

/** The card's left, top, right and bottom. */
internal class CardBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

internal fun cardOf(width: Float, height: Float) =
    CardBox(CARD_INSET, CARD_INSET, CARD_INSET + max(0f, width - 2f * CARD_INSET), CARD_INSET + max(0f, height - 2f * CARD_INSET))

/**
 * The card's outline, the notch cut into it, its dot grid and the pixel clock, built once per size.
 * The grid is a list of points, drawn in one batch as 1.2 dp squares; a path of hundreds of tiny
 * rectangles cost the emulator a lot of frames.
 */
internal class MorphBubbleColumnStatics(width: Float, height: Float, top: Float, clock: String) {
    val card = cardPath(cardOf(width, height), notchOnly = false)
    val notch = cardPath(cardOf(width, height), notchOnly = true)
    val grid: FloatArray = run {
        val points = ArrayList<Float>()
        var gy = GRID_STEP / 2f
        while (gy < height) {
            var gx = GRID_STEP / 2f
            while (gx < width) {
                points.add(gx)
                points.add(gy)
                gx += GRID_STEP
            }
            gy += GRID_STEP
        }
        points.toFloatArray()
    }
    val clock = pixelBlockPath(pixelGlyphs(clock), CARD_INSET + 22f, max(top, 50f) + 50f, 3.2f, 0.7f)

    companion object {
        /**
         * The card's outline: a rectangle (rounded by CARD_RADIUS) with a soft-cornered notch in its
         * right edge. Corners are drawn as tangent arcs, like Skia's arcToTangent. With `notchOnly`,
         * just the notch: the page showing through the card's right edge.
         */
        fun cardPath(card: CardBox, notchOnly: Boolean): Path {
            val l = card.left
            val t = card.top
            val r = card.right
            val b = card.bottom
            val n0 = t + card.height * NOTCH_AT - NOTCH_HEIGHT / 2f
            val n1 = n0 + NOTCH_HEIGHT
            val inner = r - NOTCH_DEPTH
            val p = Path()
            var px = l + CARD_RADIUS
            var py = t
            // A rounded corner at (x1, y1) between the current point and (x2, y2).
            fun corner(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float) {
                val ax = px - x1
                val ay = py - y1
                val bx = x2 - x1
                val by = y2 - y1
                val la = sqrt(ax * ax + ay * ay)
                val lb = sqrt(bx * bx + by * by)
                if (radius <= 0f || la < 1e-3f || lb < 1e-3f) {
                    p.lineTo(x1, y1)
                    px = x1
                    py = y1
                    return
                }
                // Right-angle corners only: the arc's ends sit `radius` along each edge from the corner,
                // and its centre `radius` in from both.
                val sx = x1 + ax / la * radius
                val sy = y1 + ay / la * radius
                val ex = x1 + bx / lb * radius
                val ey = y1 + by / lb * radius
                val ox = x1 + (ax / la + bx / lb) * radius
                val oy = y1 + (ay / la + by / lb) * radius
                val start = Math.toDegrees(atan2((sy - oy).toDouble(), (sx - ox).toDouble())).toFloat()
                val end = Math.toDegrees(atan2((ey - oy).toDouble(), (ex - ox).toDouble())).toFloat()
                var sweep = end - start
                while (sweep > 180f) sweep -= 360f
                while (sweep <= -180f) sweep += 360f
                p.lineTo(sx, sy)
                p.arcTo(RectF(ox - radius, oy - radius, ox + radius, oy + radius), start, sweep, false)
                px = ex
                py = ey
            }
            if (notchOnly) {
                px = r
                py = n0 - NOTCH_RADIUS
                p.moveTo(px, py)
            } else {
                p.moveTo(px, py)
                corner(r, t, r, b, CARD_RADIUS)
            }
            corner(r, n0, inner, n0, NOTCH_RADIUS)
            corner(inner, n0, inner, n1, NOTCH_RADIUS)
            corner(inner, n1, r, n1, NOTCH_RADIUS)
            corner(r, n1, r, b, NOTCH_RADIUS)
            if (notchOnly) {
                p.close()
                return p
            }
            corner(r, b, l, b, CARD_RADIUS)
            corner(l, b, l, t, CARD_RADIUS)
            corner(l, t, r, t, CARD_RADIUS)
            p.close()
            return p
        }
    }
}

/** Each dot-matrix figure as one path of square pixels, one pixel to a unit, and how many pixels wide it is. */
private class Figure(val path: Path, val cols: Int)

private val FIGURES: Map<Char, Figure> by lazy {
    "0123456789+".associateWith { ch ->
        val rows = PIXEL_GLYPHS.getValue(ch)
        Figure(pixelBlockPath(listOf(rows), 0f, 0f, 1f, PIXEL_GAP), rows[0].length)
    }
}

internal class MorphBubbleColumnPaints {
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private fun font(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = mono; textSize = size }
    val label = font(9f)
    val tiny = font(8f)
    val heading = font(25f)
    val caption = font(10f)
    val headingAdvance = heading.measureText("M")
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.2f
        strokeCap = Paint.Cap.SQUARE
    }
    val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val shape = Path()
    val scratch = Path()
    val matrix = Matrix()
    val rect = RectF()
    val shadeTop = Paint()
    val shadeLow = Paint()
    var shadeFor = Float.NaN
}

/** Draws one frame on an Android canvas, in dp: page, card, header, column, open photo, then the chrome. */
private class MorphBubbleColumnPainter(
    val canvas: Canvas,
    val m: MorphBubbleColumnMotion,
    val sites: List<MorphBubbleColumnSite>,
    val greeting: String,
    val date: List<String>,
    val statics: MorphBubbleColumnStatics,
    val p: MorphBubbleColumnPaints,
) {
    fun draw() {
        val w = m.width
        val h = m.height
        if (w <= 0f || h <= 0f) return
        val card = cardOf(w, h)
        val now = m.elapsed
        val o = m.open
        val which = m.openSite
        val column = m.boxes
        val still = m.reducedMotion

        canvas.drawColor(PAGE)
        canvas.drawPath(statics.card, fill(CARD, 1f))
        // Everything on the card stays inside it. While the card fills the screen, only its notch can be
        // overdrawn, so the page is painted back over the notch afterwards instead of clipping every
        // frame to the card's curved outline (much cheaper on Android).
        val clipped = CARD_INSET > 0f || CARD_RADIUS > 0f
        canvas.save()
        if (clipped) canvas.clipPath(statics.card)
        p.dots.color = GRID_DOT
        canvas.drawPoints(statics.grid, p.dots)

        // The header and the site labels fade as a photo opens over them.
        val chrome = clamp01(1f - o * 1.6f)
        if (chrome > 0f) {
            date.forEachIndexed { i, line -> text(line, p.tiny, card.left + 30f, max(22f, m.top - 30f) + i * 11f, INK, chrome) }
            canvas.drawPath(statics.clock, fill(INK, chrome))
            for (s in column.indices) {
                val site = sites.getOrNull(s) ?: continue
                val enter = if (still) 1f else clamp01((now - ENTER_DELAY - s * ENTER_STAGGER - 0.15f) / 0.35f)
                siteLabel(site, s, sites.size, column[s], card, chrome * enter)
            }
        }

        for (s in column.indices) {
            if (s == which) continue
            val site = sites.getOrNull(s) ?: continue
            val box = column[s]
            // The rest sink back a little and fade while one opens.
            val sink = if (still) 1f else 1f - SINK * clamp01(o)
            val sunk = Box(box.x + box.w * (1f - sink) / 2f, box.y + box.h * (1f - sink) / 2f, box.w * sink, box.h * sink, box.r, box.n, box.scale, box.big)
            val appear = if (still) clamp01(now / 0.3f) else clamp01((box.scale - DOT) / 0.3f)
            bubble(sunk, site, m.thumbs.getOrNull(s), now, appear * clamp01(1f - o))
        }

        if (which >= 0 && which < column.size && which < sites.size) openPhoto(column[which], sites[which], which, card, now, o, still)
        canvas.restore()
        if (!clipped) canvas.drawPath(statics.notch, fill(PAGE, 1f))

        rail(card)
        pill(now)
        bar(m.slider)
    }

    /** The open site: its bubble grown into the card, with the photo and what shows over it. */
    private fun openPhoto(from: Box, site: MorphBubbleColumnSite, which: Int, card: CardBox, now: Float, o: Float, still: Boolean) {
        val full = if (still) 1f else clamp01(o)
        // The bubble grows into the card: its box, corners and squircle shoulder all morph to the card's
        // own rectangle, and the notch bites into it as it arrives.
        val w = mix(from.w, card.width, full)
        val h = mix(from.h, card.height, full)
        val half = max(1f, min(w, h) / 2f)
        val radius = mix(from.r * min(from.w, from.h) / 2f, CARD_RADIUS, full)
        val box = Box(mix(from.x, card.left, full), mix(from.y, card.top, full), w, h, min(1f, radius / half), mix(from.n, 2f, full), 1f, 1f)
        val zoomed = 1f + ZOOM_PHOTO * m.slider * full
        shapePath(p.shape, box.x, box.y, box.w, box.h, box.r, box.n)
        // The photo is filled through a shader on the bubble's own outline, so its curved edge stays
        // anti-aliased; what shows over it is clipped to the same outline. The card's clip (and so its
        // notch) is still in force around all of it.
        if (still) {
            p.rect.set(card.left, card.top, card.right, card.bottom)
            canvas.saveLayerAlpha(p.rect, (clamp01(o) * 255f).roundToInt())
        }
        canvas.drawPath(p.shape, fill(site.tint, 1f))
        m.thumbs.getOrNull(which)?.let { cover(p.shape, it, box.x, box.y, box.w, box.h, zoomed, clamp01((now - it.at) / IMAGE_FADE)) }
        m.sharps.getOrNull(which)?.let { cover(p.shape, it, box.x, box.y, box.w, box.h, zoomed, clamp01((now - it.at) / IMAGE_FADE)) }
        val alpha = clamp01((o - 0.72f) / 0.28f)
        if (alpha > 0.002f) {
            canvas.save()
            canvas.clipPath(p.shape)
            details(card, site, alpha, m.shownFor, m.slider, now, still)
            canvas.restore()
        }
        if (still) canvas.restore()
    }

    private fun fill(color: Int, alpha: Float): Paint {
        p.fill.shader = null
        p.fill.color = color
        p.fill.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        return p.fill
    }

    private fun stroke(color: Int, width: Float, alpha: Float): Paint {
        p.stroke.color = color
        p.stroke.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        p.stroke.strokeWidth = width
        return p.stroke
    }

    /** Draws `text` with its baseline at `baseline`, starting at `x`. */
    private fun text(text: String, paint: Paint, x: Float, baseline: Float, color: Int, alpha: Float) {
        if (alpha <= 0.002f) return
        paint.color = color
        paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawText(text, x, baseline, paint)
    }

    // Bubbles

    /**
     * A rounded rectangle whose corners are quarter superellipses: radius `rf` × half the shorter
     * side, exponent `n`. With rf = 1 and n = 2 a square becomes a circle, with n ≈ 4 a squircle.
     */
    private fun shapePath(path: Path, x: Float, y: Float, w: Float, h: Float, rf: Float, n: Float) {
        path.rewind()
        val rc = max(0f, min(rf, 1f)) * min(w, h) * 0.5f
        val e = 2f / max(2f, n)
        val quarter = (PI / 2).toFloat()
        val cxs = floatArrayOf(x + w - rc, x + w - rc, x + rc, x + rc)
        val cys = floatArrayOf(y + rc, y + h - rc, y + h - rc, y + rc)
        val starts = floatArrayOf(-quarter, 0f, quarter, PI.toFloat())
        val steps = 14
        for (k in 0 until 4) {
            for (i in 0..steps) {
                val a = starts[k] + i.toFloat() / steps * quarter
                val c = cos(a)
                val s = sin(a)
                val px = cxs[k] + rc * Math.signum(c) * abs(c).pow(e)
                val py = cys[k] + rc * Math.signum(s) * abs(s).pow(e)
                if (k == 0 && i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
        }
        path.close()
    }

    /** Fills `path` with `image` scaled to cover the box (and `zoom` more), centred, at `alpha`. */
    private fun cover(path: Path, image: Loaded, x: Float, y: Float, w: Float, h: Float, zoom: Float, alpha: Float) {
        val iw = image.bitmap.width.toFloat()
        val ih = image.bitmap.height.toFloat()
        if (alpha <= 0f || iw <= 0f || ih <= 0f || w <= 0f || h <= 0f) return
        val fit = max(w / iw, h / ih) * zoom
        p.matrix.setScale(fit, fit)
        p.matrix.postTranslate(x + (w - iw * fit) / 2f, y + (h - ih * fit) / 2f)
        image.shader.setLocalMatrix(p.matrix)
        p.image.shader = image.shader
        p.image.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawPath(path, p.image)
        p.image.shader = null
    }

    /** One bubble: the site's tint, then its photo fading in, in the bubble's shape. */
    private fun bubble(box: Box, site: MorphBubbleColumnSite, loaded: Loaded?, now: Float, alpha: Float) {
        if (alpha <= 0.002f || box.w < 0.5f) return
        val layered = alpha < 0.995f
        if (layered) {
            p.rect.set(box.x - 2f, box.y - 2f, box.x + box.w + 2f, box.y + box.h + 2f)
            canvas.saveLayerAlpha(p.rect, (alpha * 255f).roundToInt())
        }
        shapePath(p.shape, box.x, box.y, box.w, box.h, box.r, box.n)
        canvas.drawPath(p.shape, fill(site.tint, 1f))
        if (loaded != null) cover(p.shape, loaded, box.x, box.y, box.w, box.h, 1f, clamp01((now - loaded.at) / IMAGE_FADE))
        if (layered) canvas.restore()
    }

    /**
     * A site's label, left of its bubble and centred on it: its number, its name and, while selected,
     * its position and a hairline that runs to the bubble.
     */
    private fun siteLabel(site: MorphBubbleColumnSite, index: Int, count: Int, box: Box, card: CardBox, alpha: Float) {
        if (alpha <= 0.002f) return
        val x = card.left + 22f
        val cy = box.y + box.h / 2f
        val on = box.big
        val number = "${(index + 1).toString().padStart(2, '0')}/${count.toString().padStart(2, '0')}"
        text(number, p.tiny, x, cy - 9f, MUTED, alpha * mix(0.7f, 1f, on))
        text(site.name, p.label, x, cy + 3f, INK, alpha * mix(0.42f, 1f, on))
        if (on <= 0.01f) return
        text(site.coords, p.tiny, x, cy + 15f, MUTED, alpha * on)
        // The leader: from just past the name to just short of the bubble, drawn out as it is selected.
        val from = x + p.label.measureText(site.name) + 8f
        val to = box.x - 8f
        if (to - from <= 6f) return
        val end = mix(from, to, EASE_OUT.transform(on))
        canvas.drawLine(from, cy - 0.5f, end, cy - 0.5f, stroke(INK, 0.8f, alpha * on))
        canvas.drawCircle(end, cy - 0.5f, 1.8f, fill(INK, alpha * on))
    }

    // Chrome

    /** The pill at the top right: the avatar, a greeting and a round bolt button. */
    private fun pill(now: Float) {
        val w = 48f
        val x = m.width - CARD_INSET - 14f - w
        val y = max(14f, m.top - 40f)
        canvas.drawRoundRect(x, y, x + w, y + 122f, w / 2f, w / 2f, fill(INK, 1f))
        p.scratch.rewind()
        p.scratch.addCircle(x + 7f + 17f, y + 7f + 17f, 17f, Path.Direction.CW)
        canvas.drawPath(p.scratch, fill(0xFF555555.toInt(), 1f))
        m.face?.let { cover(p.scratch, it, x + 7f, y + 7f, 34f, 34f, 1f, clamp01((now - it.at) / IMAGE_FADE)) }
        val width = p.tiny.measureText(greeting)
        text(greeting, p.tiny, x + w / 2f - width / 2f, y + 58f, CARD, 1f)
        val cx = x + w / 2f
        val cy = y + 93f
        canvas.drawCircle(cx, cy, 16f, fill(CARD, 1f))
        // A bolt, drawn: two offset triangles.
        p.scratch.rewind()
        p.scratch.moveTo(cx + 1.6f, cy - 8f)
        p.scratch.lineTo(cx - 5f, cy + 1.4f)
        p.scratch.lineTo(cx - 0.4f, cy + 1.4f)
        p.scratch.lineTo(cx - 1.6f, cy + 8f)
        p.scratch.lineTo(cx + 5f, cy - 1.4f)
        p.scratch.lineTo(cx + 0.4f, cy - 1.4f)
        p.scratch.close()
        canvas.drawPath(p.scratch, fill(INK, 1f))
    }

    /** The tool rail in the card's notch: a viewfinder, a card and an exit icon, drawn in light lines. */
    private fun rail(card: CardBox) {
        val cx = card.right - NOTCH_DEPTH / 2f
        val top = card.top + card.height * NOTCH_AT - NOTCH_HEIGHT / 2f
        val line = stroke(ON_PAGE, 1.4f, 1f)
        // Viewfinder: four corners.
        val a = 6.5f
        val l = 3.2f
        val vy = top + 30f
        val v = p.scratch
        v.rewind()
        v.moveTo(cx - a, vy - a + l); v.lineTo(cx - a, vy - a); v.lineTo(cx - a + l, vy - a)
        v.moveTo(cx + a - l, vy - a); v.lineTo(cx + a, vy - a); v.lineTo(cx + a, vy - a + l)
        v.moveTo(cx + a, vy + a - l); v.lineTo(cx + a, vy + a); v.lineTo(cx + a - l, vy + a)
        v.moveTo(cx - a + l, vy + a); v.lineTo(cx - a, vy + a); v.lineTo(cx - a, vy + a - l)
        canvas.drawPath(v, line)
        // A card.
        val my = top + NOTCH_HEIGHT / 2f
        canvas.drawRoundRect(cx - 6f, my - 5f, cx + 6f, my + 5f, 2f, 2f, line)
        canvas.drawLine(cx - 6f, my - 1.5f, cx + 6f, my - 1.5f, line)
        // Exit: a door with an arrow.
        val ey = top + NOTCH_HEIGHT - 30f
        v.rewind()
        v.moveTo(cx + 1f, ey - 6f); v.lineTo(cx + 6f, ey - 6f); v.lineTo(cx + 6f, ey + 6f); v.lineTo(cx + 1f, ey + 6f)
        v.moveTo(cx - 6f, ey); v.lineTo(cx + 2f, ey)
        v.moveTo(cx - 1f, ey - 3f); v.lineTo(cx + 2f, ey); v.lineTo(cx - 1f, ey + 3f)
        canvas.drawPath(v, line)
    }

    /** The white bar at the bottom of the card: menu, EXPLORE, the zoom slider and its reading. */
    private fun bar(zoom: Float) {
        val top = barTop(m.height, m.bottom)
        val left = CARD_INSET + 10f
        val right = m.width - left
        canvas.drawRoundRect(left, top, right, top + 56f, 18f, 18f, fill(0xFFFBFBF8.toInt(), 1f))
        canvas.drawRoundRect(left, top, right, top + 56f, 18f, 18f, stroke(0xFFDCDCD5.toInt(), 0.8f, 1f))
        val line = stroke(INK, 1.4f, 1f)
        canvas.drawLine(left + 18f, top + 24f, left + 33f, top + 24f, line)
        canvas.drawLine(left + 18f, top + 30f, left + 33f, top + 30f, line)
        text("EXPLORE", p.label, left + 44f, top + 30.5f, INK, 1f)
        val t = track(m.width, m.height, m.bottom)
        canvas.drawLine(t.x0, t.y, t.x1, t.y, stroke(INK, 0.9f, 1f))
        val at = mix(t.x0, t.x1, zoom)
        canvas.drawLine(t.x0, t.y, at, t.y, stroke(INK, 2f, 1f))
        val ink = fill(INK, 1f)
        // End stops, each with a pixel above and below, like a survey instrument's scale.
        for (ex in floatArrayOf(t.x0, t.x1)) {
            canvas.drawRect(ex - 1f, t.y - 5f, ex + 1f, t.y + 5f, ink)
            canvas.drawRect(ex - 1.5f, t.y - 11f, ex + 1.5f, t.y - 8f, ink)
            canvas.drawRect(ex - 1.5f, t.y + 8f, ex + 1.5f, t.y + 11f, ink)
        }
        canvas.drawRoundRect(at - 1.5f, t.y - 10f, at + 1.5f, t.y + 10f, 1.5f, 1.5f, ink)
        val reading = "${jsRound(10f + clamp01(zoom) * 80f).toInt()} x"
        text(reading, p.label, t.x1 - p.label.measureText(reading), top + 46f, INK, 1f)
        text("ZOOM", p.tiny, t.x0, top + 46f, MUTED, 1f)
    }

    // Open photo

    /** `text` in dot-matrix figures, PIXEL dp to a pixel, its top left at (x, y). */
    private fun figures(text: String, x: Float, y: Float, paint: Paint) {
        var left = x
        for (ch in text) {
            val figure = FIGURES[ch] ?: continue
            canvas.save()
            canvas.translate(left, y)
            canvas.scale(PIXEL, PIXEL)
            canvas.drawPath(figure.path, paint)
            canvas.restore()
            left += (figure.cols + 1) * PIXEL
        }
    }

    /** What shows over an open photo: the typed heading, the target ring, the counted reading. */
    private fun details(card: CardBox, site: MorphBubbleColumnSite, alpha: Float, shown: Float, zoom: Float, now: Float, still: Boolean) {
        val low = barTop(m.height, m.bottom) - 230f
        // A light shade at the top and the bottom keeps white type legible on a bright sky or ice.
        val key = card.top * 7919f + card.bottom * 31f + low
        if (p.shadeFor != key) {
            p.shadeFor = key
            p.shadeTop.shader = LinearGradient(0f, card.top, 0f, card.top + 240f, 0x4D000000, 0x00000000, Shader.TileMode.CLAMP)
            p.shadeLow.shader = LinearGradient(0f, low, 0f, card.bottom, 0x00000000, 0x57000000, Shader.TileMode.CLAMP)
        }
        p.shadeTop.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        p.shadeLow.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRect(card.left, card.top, card.right, card.top + 240f, p.shadeTop)
        canvas.drawRect(card.left, low, card.right, card.bottom, p.shadeLow)
        val ink = site.ink

        // The heading types itself a character at a time behind a block cursor.
        val total = site.heading.sumOf { it.length }
        val typed = if (still) total else max(0, min(total, floor((shown - TYPE_DELAY) / TYPE_STEP).toInt()))
        val x = card.left + 22f
        val lead = 29f
        val first = max(m.top, 50f) + 76f
        var left = typed
        var cursorLine = 0
        var cursorCol = 0
        for ((i, line) in site.heading.withIndex()) {
            val n = min(line.length, left)
            if (n > 0) text(line.substring(0, n), p.heading, x, first + i * lead, ink, alpha)
            cursorLine = i
            cursorCol = n
            if (n < line.length) break
            left -= n
        }
        val done = typed >= total
        val blink = if (done) floor(now * CURSOR_HZ * 2f).toInt() % 2 == 0 else true
        val advance = p.headingAdvance
        if (shown > TYPE_DELAY - 0.2f && blink) {
            val cx = x + cursorCol * advance + 2f
            val cy = first + cursorLine * lead - 19f
            canvas.drawRect(cx, cy, cx + advance * 0.72f, cy + 22f, fill(ink, alpha))
        }

        // A target ring and its readout, pinned to the photo: it moves with the slider's zoom.
        val ring = clamp01((shown - 1f) / 0.6f)
        if (ring > 0f) {
            val ccx = card.left + card.width / 2f
            val ccy = card.top + card.height / 2f
            val scale = 1f + ZOOM_PHOTO * zoom
            val rx = ccx + (card.left + card.width * site.ringX - ccx) * scale
            val ry = ccy + (card.top + card.height * site.ringY - ccy) * scale
            val grow = if (still) 1f else EASE_OUT.transform(ring)
            val line = stroke(ink, 1.2f, alpha * ring)
            canvas.drawCircle(rx, ry, 12f + 8f * grow, line)
            canvas.save()
            canvas.translate(rx, ry)
            canvas.rotate(if (still) 0f else now * 14f)
            for (k in 0 until 4) {
                val a = k * PI.toFloat() / 2f
                canvas.drawLine(cos(a) * 25f * grow, sin(a) * 25f * grow, cos(a) * 31f * grow, sin(a) * 31f * grow, line)
            }
            canvas.restore()
            canvas.drawCircle(rx, ry, 1.8f, fill(ink, alpha * ring))
            val tagWidth = p.caption.measureText(site.tag)
            text(site.tag, p.caption, rx - tagWidth / 2f, ry + 52f, ink, alpha * ring)
        }

        // The reading counts up in big dot-matrix figures, with its caption below.
        if (shown > COUNT_DELAY - 0.3f) {
            val count = if (still) 1f else EASE_OUT.transform(clamp01((shown - COUNT_DELAY) / COUNT_TIME))
            val value = jsRound(site.value * count).toInt()
            val fade = alpha * clamp01((shown - COUNT_DELAY + 0.3f) / 0.3f)
            val top = barTop(m.height, m.bottom) - 124f
            figures("+$value", x, top, fill(ink, fade))
            val dots = ".".repeat(floor(now * 2.5f).toInt() % 4)
            site.caption.forEachIndexed { i, line ->
                val shownLine = if (i == site.caption.lastIndex) line + dots else line
                text(shownLine, p.caption, x, top + 7f * PIXEL + 22f + i * 13f, ink, fade)
            }
        }
        val hint = "DRAG DOWN TO CLOSE"
        text(hint, p.label, card.right - 22f - p.label.measureText(hint), barTop(m.height, m.bottom) - 16f, ink, alpha * 0.85f)
    }
}
