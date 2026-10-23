package com.ngocdevv.nativecook.blocks.cards.bentoexpandgrid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

private val Muted = Color(0xFF8B8173)
private val StatPaper = Color(0xFF1F1C18)
private val QuotePaper = Color(0xFFE5DBC9)
private val MapPaper = Color(0xFFE9E1D1)
private val PhotoPaper = Color(0xFFDDD3C2)
private val Cream = Color(0xFFF1EBE0)
private val Shade = Color(0xFF14110D)
// The drag on the page starts after this much travel.
private const val PAGE_SLOP = 5f

private fun paperOf(face: BentoFace) = when (face) {
    is BentoFace.Photo -> PhotoPaper
    is BentoFace.Stat -> StatPaper
    is BentoFace.Quote -> QuotePaper
    is BentoFace.Map -> MapPaper
}

/**
 * A bento of seven field notes on warm paper: photos, a reading stat, a quote and a little map.
 * Tapping a tile grows it in place into its page while the other tiles are pushed away; the photo
 * becomes the page's header and slides under the text as it scrolls. Dragging the page down
 * shrinks it under the finger; letting go puts it back in its tile.
 *
 * @param notes The seven notes on the grid, each with its place on a 4 × 4 grid.
 * @param autoPlay Opens a tile, scrolls its page a little, closes it, opens another, and starts over.
 * @param background The paper behind the grid and the pages.
 * @param accent The one accent: kickers, the map's pin, the stat's dot and the pull quote's rule.
 */
@Composable
fun BentoExpandGrid(
    modifier: Modifier = Modifier,
    notes: List<BentoNote> = BentoNotes,
    autoPlay: Boolean = false,
    background: Color = Color(0xFFF1EBE0),
    ink: Color = Color(0xFF1F1C18),
    accent: Color = Color(0xFFC4582F),
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val motion = remember(notes) { BentoMotion(notes) }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        motion.reduced = reducedMotion
        motion.haptic = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    }

    LaunchedEffect(motion, auto) { motion.restartLoop(auto) }

    // Header photos are shared by the tile and the page; the pages' pairs are fetched ahead, so a
    // page never opens onto empty frames.
    LaunchedEffect(motion, context) {
        val urls = notes.mapNotNull { (it.face as? BentoFace.Photo)?.let { face -> bentoPhotoUrl(face.photo, HEADER_PX) } } +
            notes.flatMap { n -> n.pair.map { bentoPhotoUrl(it, PAIR_PX) } }
        urls.forEach { url ->
            launch {
                val image = loadBentoPhoto(context, url) ?: return@launch
                motion.images[url] = Loaded(image, motion.elapsed)
            }
        }
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.064f)
                last = now
                motion.step(dt)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(background).clipToBounds()) {
        motion.layout(maxWidth.value, maxHeight.value, safeTop, safeBottom)
        val active = motion.active

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (MARGIN + 4f).dp, end = (MARGIN + 4f).dp, top = (motion.top + 14f).dp)
                .graphicsLayer {
                    motion.elapsed
                    alpha = motion.titleAlpha
                },
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText("FIELD NOTES · NO. 07", style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
                BasicText(
                    "Slow crafts",
                    Modifier.padding(top = 6.dp),
                    style = TextStyle(color = ink, fontSize = 36.sp, fontFamily = FontFamily.Serif, letterSpacing = (-0.8).sp),
                )
            }
            BasicText(
                "Autumn\n2026",
                Modifier.padding(bottom = 6.dp),
                style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp, textAlign = TextAlign.End),
            )
        }

        motion.notes.forEachIndexed { index, note ->
            if (index < motion.rects.size) BentoTile(motion, index, note, ink, accent)
        }

        if (active != null) BentoPage(motion, active, background, ink, accent)
    }
}

private suspend fun loadBentoPhoto(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context).data(url).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    return result.image.toBitmap()
}

private val CoverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
private val CoverSrc = Rect()
private val CoverDest = RectF()

/** A photo drawn to cover the whole draw area, fading in over [fade] seconds once it has arrived. */
private fun DrawScope.drawCover(loaded: Loaded?, now: Float, fade: Float) {
    loaded ?: return
    // A box of no size (a header squeezed to 0) would make the crop NaN, and roundToInt throws on it.
    if (size.width <= 0f || size.height <= 0f || loaded.image.width <= 0 || loaded.image.height <= 0) return
    val alpha = if (fade == 0f) 1f else clamp01((now - loaded.at) / fade)
    if (alpha <= 0f) return
    val iw = loaded.image.width.toFloat()
    val ih = loaded.image.height.toFloat()
    val scale = max(size.width / iw, size.height / ih)
    val sw = size.width / scale
    val sh = size.height / scale
    val l = (iw - sw) / 2f
    val t = (ih - sh) / 2f
    CoverSrc.set(l.roundToInt(), t.roundToInt(), (l + sw).roundToInt(), (t + sh).roundToInt())
    CoverDest.set(0f, 0f, size.width, size.height)
    CoverPaint.alpha = (alpha * 255).roundToInt()
    drawIntoCanvas { it.nativeCanvas.drawBitmap(loaded.image, CoverSrc, CoverDest, CoverPaint) }
}

// MARK: - Tiles

/** One tile on the grid: pushed away, shrunk and faded while another tile is open. */
@Composable
private fun BentoTile(m: BentoMotion, index: Int, note: BentoNote, ink: Color, accent: Color) {
    val rect = m.rects[index]
    val state = remember { FloatArray(4) }
    val label = if (note.face is BentoFace.Photo) "${note.kicker}: ${note.title}" else note.title
    Box(
        Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.w.dp, rect.h.dp)
            .graphicsLayer {
                m.elapsed
                m.tileState(index, state)
                alpha = state[0]
                translationX = state[1].dp.toPx()
                translationY = state[2].dp.toPx()
                scaleX = state[3]
                scaleY = state[3]
            }
            .graphicsLayer {
                m.elapsed
                scaleX = m.pressScale(index)
                scaleY = scaleX
                shape = RoundedCornerShape(TILE_RADIUS.dp)
                clip = true
            }
            .background(paperOf(note.face))
            .semantics { contentDescription = label }
            .pointerInput(m, index) {
                detectTapGestures(
                    onPress = {
                        m.press(index, true)
                        tryAwaitRelease()
                        m.press(index, false)
                    },
                    onTap = { m.tapTile(index) },
                )
            },
    ) {
        BentoFaceView(m, index, note, ink, accent)
    }
}

/** What a tile shows at rest: the same drawing the page's header grows from. */
@Composable
private fun BoxScope.BentoFaceView(m: BentoMotion, index: Int, note: BentoNote, ink: Color, accent: Color) {
    val rect = m.rects[index]
    when (val face = note.face) {
        is BentoFace.Photo -> {
            val url = bentoPhotoUrl(face.photo, HEADER_PX)
            Canvas(Modifier.fillMaxSize()) {
                drawCover(m.images[url], m.elapsed, 0.3f)
            }
            BentoPhotoLabel(note, rect.w < NARROW, Modifier.align(Alignment.BottomStart))
        }
        is BentoFace.Map -> {
            Canvas(Modifier.fillMaxSize()) { drawBentoMap(size.width, size.height, ink, accent) }
            BentoMapCaption(face, ink, Modifier.align(Alignment.BottomStart))
        }
        else -> {
            val grow = m.grow(index)
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize((rect.w * grow).dp, (rect.h * grow).dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = 1f / grow
                        scaleY = 1f / grow
                    },
            ) {
                BentoTypeFace(face, grow, ink, accent)
            }
        }
    }
}

/** A photo tile's caption, on a soft shade at its foot; a narrow tile sets it smaller, on two lines. */
@Composable
private fun BentoPhotoLabel(note: BentoNote, narrow: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Shade.copy(alpha = 0f), Shade.copy(alpha = 0.55f))))
            .padding(start = if (narrow) 11.dp else 14.dp, end = if (narrow) 11.dp else 14.dp, top = 26.dp, bottom = if (narrow) 10.dp else 12.dp),
    ) {
        BasicText(
            note.kicker.uppercase(),
            style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
        )
        BasicText(
            note.title,
            Modifier.padding(top = 5.dp),
            style = TextStyle(
                color = Color.White,
                fontSize = if (narrow) 13.sp else 15.sp,
                lineHeight = if (narrow) 17.sp else TextStyle.Default.lineHeight,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun BentoMapCaption(face: BentoFace.Map, ink: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 10.dp, bottom = 9.dp)) {
        BasicText(face.place, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        BasicText("${face.stops} stops", style = TextStyle(color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium))
    }
}

/** The stat and the quote, set at [grow] times their tile's size. */
@Composable
private fun BentoTypeFace(face: BentoFace, grow: Float, ink: Color, accent: Color) {
    val g = grow
    Column(Modifier.fillMaxSize().padding((14f * g).dp), verticalArrangement = Arrangement.SpaceBetween) {
        when (face) {
            is BentoFace.Stat -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        face.label.uppercase(),
                        Modifier.weight(1f),
                        style = TextStyle(color = Cream.copy(alpha = 0.6f), fontSize = (10f * g).sp, fontWeight = FontWeight.SemiBold, letterSpacing = (1.4f * g).sp),
                    )
                    Box(Modifier.size((7f * g).dp).background(accent, CircleShape))
                }
                Row(horizontalArrangement = Arrangement.spacedBy((6f * g).dp)) {
                    BasicText(
                        face.value,
                        Modifier.alignByBaseline(),
                        style = TextStyle(color = Cream, fontSize = (40f * g).sp, fontFamily = FontFamily.Serif, letterSpacing = (-1f * g).sp, fontFeatureSettings = "tnum"),
                    )
                    BasicText(
                        face.unit,
                        Modifier.alignByBaseline(),
                        style = TextStyle(color = Cream.copy(alpha = 0.7f), fontSize = (13f * g).sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
            is BentoFace.Quote -> {
                BasicText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = accent)) { append("“") }
                        append(face.text)
                    },
                    style = TextStyle(color = ink, fontSize = (18f * g).sp, lineHeight = (23f * g).sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic),
                )
                BasicText(
                    face.by.uppercase(),
                    style = TextStyle(color = Muted, fontSize = (10f * g).sp, fontWeight = FontWeight.SemiBold, letterSpacing = (1.2f * g).sp),
                )
            }
            else -> Unit
        }
    }
}

/**
 * The little map: streets on a grid, a river, a dashed walk joining four stops, a pin. It is drawn
 * on a 400 × 300 board, scaled to cover the box; lines keep their width.
 */
private fun DrawScope.drawBentoMap(w: Float, h: Float, ink: Color, accent: Color) {
    val k = max(w / 400f, h / 300f)
    val ox = (w - 400f * k) / 2f
    val oy = (h - 300f * k) / 2f
    val d = density
    fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)
    var x = -40f
    while (x <= 440f) {
        drawLine(ink.copy(alpha = 0.12f), p(x, -20f), p(x + 46f, 320f), strokeWidth = 1f * d)
        x += 34f
    }
    var y = -20f
    while (y <= 320f) {
        drawLine(ink.copy(alpha = 0.12f), p(-20f, y), p(420f, y - 18f), strokeWidth = 1f * d)
        y += 30f
    }
    // Two main roads.
    drawLine(ink.copy(alpha = 0.28f), p(-20f, 96f), p(420f, 70f), strokeWidth = 2.2f * d)
    drawLine(ink.copy(alpha = 0.28f), p(250f, -20f), p(290f, 320f), strokeWidth = 2.2f * d)
    // The river.
    val river = Path().apply {
        val a = p(-20f, 250f)
        moveTo(a.x, a.y)
        val c1 = p(90f, 210f)
        val c2 = p(120f, 120f)
        val e1 = p(210f, 150f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        val c3 = p(300f, 180f)
        val c4 = p(330f, 40f)
        val e2 = p(420f, 20f)
        cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
    }
    drawPath(river, Color(0xFF8FB2BF).copy(alpha = 0.7f), style = Stroke(width = 9f * d, cap = StrokeCap.Round))
    // The walk, dashed, with its stops.
    val stops = listOf(p(70f, 70f), p(150f, 120f), p(235f, 205f), p(330f, 130f))
    val walk = Path().apply {
        moveTo(stops[0].x, stops[0].y)
        for (i in 1 until stops.size) lineTo(stops[i].x, stops[i].y)
    }
    drawPath(walk, ink.copy(alpha = 0.8f), style = Stroke(width = 1.6f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * d, 4f * d))))
    for (i in 0 until stops.size - 1) {
        drawCircle(MapPaper, 5f * d, stops[i])
        drawCircle(ink, 3f * d, stops[i])
    }
    // The pin on the last stop, with a soft halo.
    val pin = stops.last()
    drawCircle(accent.copy(alpha = 0.18f), 14f * d, pin)
    drawCircle(accent, 6f * d, pin)
    drawCircle(Color.White, 2.2f * d, pin)
}

// MARK: - Page

/** The header's height: the tile's at first, the header's (shrunk with the frame) once open. */
private fun BentoMotion.headerFor(f: PageFrame): Float {
    // The face fills the whole frame for the first part of the flight, then gives way to the sheet.
    return mix(f.h, headerHeight * f.s, smooth(HEADER_GIVE_FROM, HEADER_GIVE_TO, open.value))
}

/** Lays the content out at the page's frame for this frame, in a parent the size of the screen. */
private fun Modifier.pageFrame(m: BentoMotion, index: Int) = layout { measurable, constraints ->
    m.elapsed
    val f = m.frame(index)
    val placeable = measurable.measure(Constraints.fixed(f.w.dp.roundToPx(), f.h.dp.roundToPx()))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(f.x.dp.roundToPx(), f.y.dp.roundToPx()) }
}

/**
 * The open page. Its frame is one clipped box between the tile and the screen; inside it the
 * header (photo, map or type) fills the top and the sheet of text scrolls over it.
 */
@Composable
private fun BentoPage(m: BentoMotion, index: Int, background: Color, ink: Color, accent: Color) {
    val note = m.notes[index]
    val tile = m.rects[index]
    val paper = paperOf(note.face)
    val density = LocalDensity.current.density

    // The page lifts off the paper on a soft shadow while it flies or is dragged.
    Box(
        Modifier
            .pageFrame(m, index)
            .graphicsLayer {
                m.elapsed
                val f = m.frame(index)
                val p = clamp01(m.open.value)
                alpha = if (m.reduced) 0f else clamp01(sin(PI * p).toFloat() * 0.8f + m.pull() * 4f)
                shape = RoundedCornerShape(f.r.dp)
                shadowElevation = 22.dp.toPx()
                ambientShadowColor = Color(0xFF1F1C18)
                spotShadowColor = Color(0xFF1F1C18)
            }
            .drawBehind {
                val f = m.frame(index)
                drawRoundRect(paper, cornerRadius = CornerRadius(f.r * density))
            },
    )

    Box(
        Modifier
            .pageFrame(m, index)
            .graphicsLayer {
                m.elapsed
                val f = m.frame(index)
                shape = RoundedCornerShape(f.r.dp)
                clip = true
                alpha = if (m.reduced) clamp01(m.open.value) else 1f
            }
            .background(paper)
            .pointerInput(m) {
                val tracker = VelocityTracker()
                // VelocityTracker can report NaN or infinity, which would reach the page's frame.
                fun finite(v: Float) = if (v.isFinite()) v else 0f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var started = false
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val dx = (change.position.x - down.position.x) / density
                            val dy = (change.position.y - down.position.y) / density
                            if (!change.pressed) {
                                if (started) {
                                    released = true
                                    val v = tracker.calculateVelocity()
                                    m.pageRelease(dy, finite(v.x / density), finite(v.y / density))
                                }
                                break
                            }
                            if (!started && sqrt(dx * dx + dy * dy) >= PAGE_SLOP) started = true
                            if (started) {
                                change.consume()
                                m.pageDrag(dx, dy)
                            }
                        }
                    } finally {
                        // A finger lost to the system (or a cancelled gesture) still lets go of the page.
                        if (started && !released) m.pageRelease(0f, 0f, 0f)
                    }
                }
            },
    ) {
        // The header, sliding up at a share of the scroll.
        Box(
            Modifier
                .layout { measurable, _ ->
                    m.elapsed
                    val f = m.frame(index)
                    val header = m.headerFor(f)
                    val placeable = measurable.measure(Constraints.fixed(f.w.dp.roundToPx(), max(0f, header).dp.roundToPx()))
                    val parallax = if (m.reduced) 0f else -max(0f, m.scrollY) * PARALLAX * clamp01(m.open.value)
                    layout(placeable.width, placeable.height) { placeable.place(0, parallax.dp.roundToPx()) }
                }
                .clipToBounds(),
        ) {
            BentoHeader(m, index, note, tile, ink, accent)
        }

        // The sheet keeps its page width and is scaled with the frame, so its lines never re-wrap.
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(m.width.dp, m.height.dp)
                .graphicsLayer {
                    m.elapsed
                    val f = m.frame(index)
                    val scale = f.w / m.width
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationY = (m.headerFor(f) - m.headerHeight * scale).dp.toPx()
                    alpha = if (m.reduced) 1f else smooth(SHEET_FROM, SHEET_TO, m.open.value)
                    clip = true
                },
        ) {
            BentoSheet(
                m,
                index,
                note,
                background,
                ink,
                accent,
                Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer {
                        m.elapsed
                        translationY = (m.headerHeight - OVERLAP - m.scrollY).dp.toPx()
                    }
                    .onSizeChanged { m.sheetMeasured(it.height / density) },
            )
        }

        BentoClose(
            m,
            ink,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = (m.top + 8f).dp, end = 16.dp)
                .graphicsLayer {
                    m.elapsed
                    alpha = smooth(0.6f, 1f, m.open.value) * (1f - clamp01(m.pull() * 3f))
                },
        )
    }
}

@Composable
private fun BentoHeader(m: BentoMotion, index: Int, note: BentoNote, tile: Box4, ink: Color, accent: Color) {
    when (val face = note.face) {
        is BentoFace.Photo -> {
            val url = bentoPhotoUrl(face.photo, HEADER_PX)
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCover(m.images[url], m.elapsed, 0f)
                }
                BentoPhotoLabel(
                    note,
                    tile.w < NARROW,
                    Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer {
                            m.elapsed
                            alpha = 1f - smooth(0f, 0.3f, m.open.value)
                        },
                )
            }
        }
        is BentoFace.Map -> {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    m.elapsed
                    drawBentoMap(size.width, size.height, ink, accent)
                }
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(tile.w.dp, tile.h.dp)
                        .graphicsLayer {
                            m.elapsed
                            alpha = 1f - smooth(0f, 0.3f, m.open.value)
                            translationY = (m.headerFor(m.frame(index)) - tile.h).dp.toPx()
                        },
                ) {
                    BentoMapCaption(face, ink, Modifier.align(Alignment.BottomStart))
                }
            }
        }
        else -> {
            // The stat and quote grow from their tile's size, anchored at the header's foot, lifted
            // clear of the sheet's rounded top once open.
            val grow = m.grow(index)
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize((tile.w * grow).dp, (tile.h * grow).dp)
                    .graphicsLayer {
                        m.elapsed
                        val f = m.frame(index)
                        val p = min(1f, m.open.value)
                        val scale = mix(1f / grow, f.s, p)
                        val lift = OVERLAP * 0.8f * smooth(HEADER_GIVE_FROM, HEADER_GIVE_TO, m.open.value)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationY = (m.headerFor(f) - tile.h * grow * scale - lift).dp.toPx()
                    },
            ) {
                BentoTypeFace(face, grow, ink, accent)
            }
        }
    }
}

@Composable
private fun BentoSheet(m: BentoMotion, index: Int, note: BentoNote, background: Color, ink: Color, accent: Color, modifier: Modifier) {
    val serif = FontFamily.Serif
    fun Modifier.section(order: Int) = graphicsLayer {
        m.elapsed
        val shown = revealOf(m.open.value, order)
        alpha = shown
        translationY = if (m.reduced) 0f else ((1f - shown) * REVEAL_RISE).dp.toPx()
    }
    val body = TextStyle(color = ink, fontSize = 16.sp, lineHeight = 25.sp)
    Column(
        modifier
            .width(m.width.dp)
            .background(background, RoundedCornerShape(topStart = OVERLAP.dp, topEnd = OVERLAP.dp))
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = (m.bottom + 40f).dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // The grabber takes no room of its own, so the first block sits one gap under the sheet's top.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, 0) { placeable.place(0, 0) }
                }
                .padding(bottom = 0.dp)
                .size(36.dp, 5.dp)
                .background(Color(0xFF1F1C18).copy(alpha = 0.14f), RoundedCornerShape(3.dp)),
        )
        Column(Modifier.section(0)) {
            val number = "${(index + 1).toString().padStart(2, '0')} / ${m.notes.size.toString().padStart(2, '0')}"
            BasicText(
                "${note.kicker.uppercase()} · $number",
                style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
            )
            BasicText(
                note.title,
                Modifier.padding(top = 10.dp),
                style = TextStyle(color = ink, fontSize = 32.sp, lineHeight = 37.sp, fontFamily = serif, letterSpacing = (-0.6).sp),
            )
            BasicText(
                note.byline,
                Modifier.padding(top = 8.dp),
                style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
        BasicText(
            note.lead,
            Modifier.section(1),
            style = TextStyle(color = ink, fontSize = 19.sp, lineHeight = 28.sp, fontFamily = serif),
        )
        Column(Modifier.section(2)) {
            note.body.forEach { BasicText(it, Modifier.padding(bottom = 12.dp), style = body) }
        }
        Box(
            Modifier
                .section(3)
                .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            BasicText(note.pull, style = TextStyle(color = ink, fontSize = 22.sp, lineHeight = 29.sp, fontFamily = serif, fontStyle = FontStyle.Italic))
        }
        Column(Modifier.section(4)) {
            Row(Modifier.padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                note.pair.forEach { id ->
                    val url = bentoPhotoUrl(id, PAIR_PX)
                    Canvas(
                        Modifier
                            .weight(1f)
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PhotoPaper),
                    ) {
                        drawCover(m.images[url], m.elapsed, 0.25f)
                    }
                }
            }
            BasicText(note.outro, Modifier.padding(bottom = 12.dp), style = body)
            BasicText(
                "END OF NOTE",
                Modifier.fillMaxWidth().padding(top = 6.dp),
                style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
private fun BentoClose(m: BentoMotion, ink: Color, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .graphicsLayer {
                alpha = if (pressed) 0.7f else 1f
                scaleX = if (pressed) 0.95f else 1f
                scaleY = scaleX
            }
            .size(38.dp)
            .background(Color(0xFFFAF7F1).copy(alpha = 0.88f), CircleShape)
            .semantics { contentDescription = "Close" }
            .clickable(interactionSource = source, indication = null, onClick = m::close),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val k = size.width / 24f
            val stroke = 2.6f * k
            drawLine(ink, Offset(5f * k, 5f * k), Offset(19f * k, 19f * k), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(ink, Offset(19f * k, 5f * k), Offset(5f * k, 19f * k), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
