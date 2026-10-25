package com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavArt.SHELF_BOX_H
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavArt.SHELF_BOX_W
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavArt.SHELF_BOX_X
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavArt.SHELF_BOX_Y
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavArt.SOFT_MARGIN
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavArt.SOFT_SIZE
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.BOARD_Y
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.BOOKS
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.BOOK_X
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.COVER_W
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.DEMO_BOOK
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.DESIGN_H
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.DESIGN_W
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.LOOP
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.OPEN
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.PAGE_H
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.PAGE_W
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.PAGE_X
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.PAGE_Y
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.autoTau
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.autoWobble
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.bookPose
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.coverRect
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.neighbourLean
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.seg
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.shade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/*
 Bookshelf Spine Nav — a port of bookshelf-spine-nav (React Native) and BookshelfSpineNav.swift.
 A reading app whose navigation is a shelf: each section is a book. Pull a spine (or tap it) and
 the book tips out, slides up, turns cover-out and opens, and its cover grows into the section's
 page; the neighbours lean into the gap and wobble when it is put back. LOOP = 9 s (open time τ):
 rest 0–1.2 s, "Essays" pulled and opened 1.2–3.8 s, held to 5.4 s, closed and put back by 7.2 s,
 the neighbours settle by 8.6 s. Touch: pull a spine up (past 0.7 of the way, or flicked, it opens),
 tap a spine to open it, tap the page or the shelf to put it back. 2 s after the last touch the
 book goes back (at most 0.9 s) and the loop restarts. Reduced motion: the home page and the shelf.
 */

private const val IDLE = 2f
private const val PERSPECTIVE = 1100f
/** Dragging a spine up this far (dp) slides it all the way out. */
private const val PULL = 150f
/** The shelf's touch area starts here (design dp), below the page. */
private const val SHELF_HIT_Y = 440f
private val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private class BookshelfState {
    var t = 0f
    var owned by mutableStateOf(false)
    var touching = false
    var returning = false
    var idle = 0f
    val held = Animatable(0f)
    var tau by mutableFloatStateOf(0f)
    var wobble by mutableFloatStateOf(0f)
    var active by mutableIntStateOf(DEMO_BOOK)
}

private fun CoroutineScope.now(block: suspend CoroutineScope.() -> Unit) = launch(start = CoroutineStart.UNDISPATCHED, block = block)

/** Which spine sits under x (design dp), or -1. */
private fun bookshelfBookAt(x: Float): Int {
    for (i in BOOKS.indices) if (x >= BOOK_X[i] - 1f && x <= BOOK_X[i] + BOOKS[i].w + 1f) return i
    return -1
}

/**
 * A reading app's library whose sections are the books on a shelf.
 *
 * @param autoPlay pulls "Essays" off the shelf, opens it into its page, holds, and puts it back, every [LOOP] seconds.
 */
@Composable
fun BookshelfSpineNav(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val screen = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val shelf = remember { BookshelfState() }
    var art by remember { mutableStateOf<BookshelfArtSet?>(null) }
    val appear = remember { Animatable(0f) }
    val images = remember(art) { art?.let { BookshelfImages(it.shelf.asImageBitmap(), it.soft.asImageBitmap(), it.grain.asImageBitmap()) } }

    LaunchedEffect(Unit) {
        art = withContext(Dispatchers.Default) { BookshelfSpineNavArt.bake(screen.density) }
        appear.animateTo(1f, tween(260))
    }

    LaunchedEffect(shelf, auto) {
        snapshotFlow { (auto && !shelf.owned && art != null) || shelf.owned }.collectLatest { run ->
            if (!run) {
                if (!shelf.owned) {
                    shelf.tau = 0f
                    shelf.wobble = 0f
                }
                return@collectLatest
            }
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0f else min((now - last) / 1e9f, 1f / 20f)
                    last = now
                    if (!shelf.owned) {
                        if (auto) shelf.t = (shelf.t + dt) % LOOP
                        val at = if (auto) shelf.t else 0f
                        shelf.tau = autoTau(at)
                        shelf.wobble = autoWobble(at)
                    } else {
                        shelf.tau = shelf.held.value
                        shelf.wobble = 0f
                        if (!shelf.touching && !shelf.returning) {
                            shelf.idle += dt
                            if (shelf.idle >= IDLE) {
                                // Put the book back quickly (at most 0.9 s), then let the demo start over.
                                shelf.returning = true
                                val from = shelf.held.value
                                scope.launch {
                                    shelf.held.animateTo(0f, tween(min(900f, max(1f, from * 360f)).roundToInt(), easing = InOut))
                                    shelf.returning = false
                                    shelf.t = 0f
                                    shelf.active = DEMO_BOOK
                                    shelf.owned = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // A light knock when a pulled book leaves the shelf, and when it is home again (finger only).
    LaunchedEffect(shelf) {
        var before: Boolean? = null
        snapshotFlow { shelf.tau > 0.45f }.collect { out ->
            if (before != null && out != before && shelf.owned) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            before = out
        }
    }

    fun takeOver() {
        shelf.touching = true
        shelf.idle = 0f
        if (!shelf.owned || shelf.returning) {
            val from = shelf.tau
            scope.now { shelf.held.snapTo(from) }
        }
        shelf.returning = false
        shelf.owned = true
    }

    fun letGo() {
        shelf.touching = false
        shelf.idle = 0f
    }

    /** Runs the open time to [to] at [pace] seconds of τ per second. */
    fun playTo(to: Float, pace: Float) {
        val from = shelf.held.value
        scope.launch { shelf.held.animateTo(to, tween(max(1f, abs(to - from) * 1000f / pace).roundToInt(), easing = LinearEasing)) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF17120F))
            .clipToBounds(),
    ) {
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(screen) / screen.density
        val bottom = insets.getBottom(screen) / screen.density
        val roomW = maxWidth.value
        val roomH = maxHeight.value - top - bottom
        val k = min(min(roomW / DESIGN_W, roomH / DESIGN_H), 1.15f)
        val ox = (roomW - DESIGN_W * k) / 2f
        val oy = top + max(0f, (roomH - DESIGN_H * k) / 2f)

        // A warm plaster wall with fine grain, a lamp's glow and a vignette.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val wall = Brush.verticalGradient(0f to Color(0xFF231B16), 0.5f to Color(0xFF1B1511), 1f to Color(0xFF120E0B))
                    val lamp = Brush.radialGradient(
                        0f to Color(0x29FFC482), 0.5f to Color(0x0DFFB46E), 1f to Color(0x00FFB46E),
                        center = Offset(w * 0.62f, (oy + 140f * k) * density), radius = w * 0.95f,
                    )
                    val vignette = Brush.radialGradient(
                        0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0x99060302),
                        center = Offset(w / 2f, h * 0.45f), radius = h * 0.75f,
                    )
                    val grain = images?.let { ShaderBrush(ImageShader(it.grain, TileMode.Repeated, TileMode.Repeated)) }
                    onDrawBehind {
                        drawRect(wall)
                        if (grain != null) drawRect(grain, alpha = appear.value)
                        drawRect(lamp)
                        drawRect(vignette)
                    }
                },
        )

        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(Constraints.fixed((DESIGN_W * density).toInt(), (DESIGN_H * density).toInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place((ox * density).toInt(), (oy * density).toInt()) }
                }
                .graphicsLayer {
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            // The shelf: back panel, the shelf above, the board and the uprights (a flat board until baked).
            Canvas(Modifier.fillMaxSize()) {
                scale(density, density, pivot = Offset.Zero) {
                    val a = appear.value
                    if (a < 1f) {
                        drawRect(Color(0xFF24170E), Offset(4f, 462f), Size(DESIGN_W - 8f, BOARD_Y - 462f), alpha = 1f - a)
                        drawRect(Color(0xFF7A5233), Offset(-4f, BOARD_Y), Size(DESIGN_W + 8f, 30f), alpha = 1f - a)
                    }
                    images?.let { drawImageAt(it.shelf, SHELF_BOX_X, SHELF_BOX_Y, SHELF_BOX_W, SHELF_BOX_H, a) }
                }
            }

            Column(
                Modifier
                    .offset(24.dp, 8.dp)
                    .width((DESIGN_W - 48f).dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText("SHELF", style = TextStyle(color = Color(0xFFE0B36C), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
                BasicText(
                    "Your library",
                    style = TextStyle(color = Color(0xFFF4EADB), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
                )
                BasicText(
                    "9 sections · 245 books",
                    style = TextStyle(color = Color(0x99F4EADB), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                )
            }

            BookshelfHomePage(
                Modifier
                    .offset(PAGE_X.dp, PAGE_Y.dp)
                    .size(PAGE_W.dp, PAGE_H.dp)
                    .graphicsLayer {
                        val away = seg(shelf.tau, 0.6f, 1.4f)
                        alpha = 1f - 0.55f * away
                        scaleX = 1f - 0.03f * away
                        scaleY = 1f - 0.03f * away
                    },
            )

            // The section page the open book becomes: it grows from the cover's rectangle.
            BookshelfSectionPage(
                BOOKS[shelf.active],
                text = { bookPose(shelf.tau, shelf.active).text },
                modifier = Modifier
                    .offset(PAGE_X.dp, PAGE_Y.dp)
                    .size(PAGE_W.dp, PAGE_H.dp)
                    .graphicsLayer {
                        val a = shelf.active
                        val g = bookPose(shelf.tau, a).grow
                        val r = coverRect(a)
                        alpha = if (shelf.tau > 1.62f) 1f else 0f
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = (r[0] - PAGE_X) * (1f - g) * density
                        translationY = (r[1] - PAGE_Y) * (1f - g) * density
                        scaleX = r[2] / PAGE_W + (1f - r[2] / PAGE_W) * g
                        scaleY = r[3] / PAGE_H + (1f - r[3] / PAGE_H) * g
                    }
                    .pointerInput(shelf) {
                        awaitEachGesture {
                            awaitFirstDown()
                            if (shelf.tau <= 2.2f) return@awaitEachGesture
                            takeOver()
                            var up = false
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.changes.none { it.pressed }) {
                                    up = true
                                    break
                                }
                            }
                            if (up) playTo(0f, 1.6f)
                            letGo()
                        }
                    },
            )

            for (i in BOOKS.indices) BookshelfBookView(i, shelf, images)

            // The shelf's touch area: pull a spine up, or tap one.
            Box(
                Modifier
                    .offset(0.dp, SHELF_HIT_Y.dp)
                    .size(DESIGN_W.dp, (BOARD_Y + 30f - SHELF_HIT_Y).dp)
                    .zIndex(6f)
                    .semantics { contentDescription = "Bookshelf. Tap a spine to open its section, or pull it up." }
                    .pointerInput(shelf) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val x = down.position.x / density
                            takeOver()
                            val canPull = shelf.held.value <= 0.01f && shelf.tau <= 0.01f
                            if (canPull) {
                                val i = bookshelfBookAt(x)
                                if (i >= 0) shelf.active = i
                            }
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var dragging = false
                            var moved = 0f
                            var up = false
                            while (true) {
                                val e = awaitPointerEvent()
                                val change = e.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    up = true
                                    break
                                }
                                val dy = (change.position.y - down.position.y) / density
                                moved = max(moved, (change.position - down.position).getDistance() / density)
                                if (!dragging && abs(dy) > 8f) dragging = true
                                if (dragging && canPull) {
                                    change.consume()
                                    // Up to τ 1.6 the drag tips the book and slides it out; past that the release decides.
                                    val pulled = max(0f, -dy / PULL)
                                    val v = min(1.6f, pulled * 1.6f) + max(0f, pulled - 1f) * 0.1f
                                    scope.now { shelf.held.snapTo(v) }
                                }
                            }
                            if (dragging) {
                                val vy = tracker.calculateVelocity().y / density
                                val out = shelf.held.value > 0.7f || vy < -600f
                                playTo(if (out) OPEN else 0f, if (out) 1.2f else 2f)
                            } else if (up && moved < 10f) {
                                if (shelf.held.value > 0.01f) {
                                    playTo(0f, 1.6f)
                                } else {
                                    val i = bookshelfBookAt(x)
                                    if (i >= 0) {
                                        shelf.active = i
                                        playTo(OPEN, 1f)
                                    }
                                }
                            }
                            letGo()
                        }
                    },
            )

            BasicText(
                "Tap or pull a spine to open its section",
                Modifier
                    .offset(24.dp, 740.dp)
                    .width((DESIGN_W - 48f).dp),
                style = TextStyle(color = Color(0x80F4EADB), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
        }
    }
}

private class BookshelfImages(val shelf: ImageBitmap, val soft: ImageBitmap, val grain: ImageBitmap)

private fun DrawScope.drawImageAt(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
    if (alpha <= 0f) return
    withTransform({
        translate(x, y)
        scale(w / img.width, h / img.height, pivot = Offset.Zero)
    }) {
        drawImage(img, alpha = alpha.coerceIn(0f, 1f))
    }
}

/**
 * One book: its spine and its front cover, hinged at their shared edge. On the shelf only the spine
 * shows; pulled, the book tips out about its bottom, slides up, turns cover-out and opens.
 */
@Composable
private fun BookshelfBookView(index: Int, shelf: BookshelfState, images: BookshelfImages?) {
    val book = BOOKS[index]
    val cw = COVER_W[index]
    val origin = TransformOrigin((book.w / 2f) / (book.w + cw), 1f)
    Box(
        Modifier
            .offset(BOOK_X[index].dp, (BOARD_Y - book.h).dp)
            .size((book.w + cw).dp, book.h.dp)
            .zIndex(if (index == shelf.active) 5f else 1f)
            .graphicsLayer {
                transformOrigin = origin
                cameraDistance = PERSPECTIVE * density / 72f
                val a = shelf.active
                if (a != index) {
                    val lean = neighbourLean(index, a, bookPose(shelf.tau, a).gap, shelf.wobble)
                    // Leaning about the bottom corner on the gap's far side: lift the centre by the corner's drop.
                    val rad = lean * PI.toFloat() / 180f
                    alpha = 1f
                    translationX = sign(lean) * book.w * (1f - cos(rad)) / 2f * density
                    translationY = -book.w / 2f * abs(sin(rad)) * density
                    rotationZ = lean
                    scaleX = 1f
                    scaleY = 1f
                    rotationX = 0f
                } else {
                    val p = bookPose(shelf.tau, index)
                    alpha = p.alpha
                    translationX = p.dx * density
                    translationY = p.dy * density
                    rotationZ = 0f
                    scaleX = p.scale
                    scaleY = p.scale
                    rotationX = p.tilt
                }
            },
    ) {
        // The spine turns away about its right edge as the book turns cover-out.
        Box(
            Modifier
                .size(book.w.dp, book.h.dp)
                .graphicsLayer {
                    val turn = if (shelf.active == index) bookPose(shelf.tau, index).turn else 0f
                    rotationY = -turn
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    cameraDistance = PERSPECTIVE * density / 72f
                },
        ) {
            BookshelfSpine(book)
        }
        // The cover: edge-on on the shelf, flat once turned, swinging open past -90° to the left.
        Box(
            Modifier
                .offset(book.w.dp, 0.dp)
                .size(cw.dp, book.h.dp)
                .graphicsLayer {
                    val on = shelf.active == index
                    val p = if (on) bookPose(shelf.tau, index) else null
                    val turn = p?.turn ?: 0f
                    alpha = if (turn > 0.5f) 1f else 0f
                    rotationY = 90f - turn - (p?.open ?: 0f) * 158f
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = PERSPECTIVE * density / 72f
                },
        ) {
            BookshelfCover(book, cw, images)
            // Seen past its edge, the cover shows its inside: a marbled endpaper.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = if (shelf.active == index) bookPose(shelf.tau, index) else null
                        alpha = if (p != null && p.open > 0.57f) 1f else 0f
                    }
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    .background(Brush.verticalGradient(0f to shade(book.color, 0.5f), 0.5f to shade(book.color, 0.25f), 1f to shade(book.color, 0.42f))),
            )
        }
    }
}

/** The spine: rounded binding, caps, bands or a label, and the foil title running top to bottom. */
@Composable
private fun BookshelfSpine(book: BookshelfBook) {
    val w = book.w
    val h = book.h
    val color = book.color
    val foil = Color(book.foil)
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(w.dp, h.dp)
            // Its shadows: a tight one under it and a soft one cast to the right (4 0 12), as gradients.
            .drawBehind {
                drawRect(
                    Brush.horizontalGradient(0f to Color(0x4D000000), 1f to Color.Transparent, startX = size.width, endX = size.width + 12.dp.toPx()),
                    Offset(size.width, 2.dp.toPx()), Size(12.dp.toPx(), size.height - 2.dp.toPx()),
                )
                drawRoundRect(
                    Color(0x8C000000), Offset(0f, 1.dp.toPx()), size,
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }
            .clip(shape)
            .background(
                Brush.horizontalGradient(0f to shade(color, -0.5f), 0.32f to shade(color, 0.14f), 0.62f to Color(color), 1f to shade(color, -0.42f)),
            ),
    ) {
        Box(Modifier.fillMaxWidth().size(w.dp, 5.dp).background(shade(color, -0.35f)))
        Box(Modifier.offset(0.dp, (h - 5f).dp).size(w.dp, 5.dp).background(shade(color, -0.45f)))
        if (book.binding == BookshelfBinding.Leather) {
            for (y in listOf(20f, 30f, h - 36f, h - 26f)) {
                Box(
                    Modifier
                        .offset(0.dp, y.dp)
                        .size(w.dp, 5.dp)
                        .background(Brush.verticalGradient(listOf(shade(color, 0.35f), Color(color), shade(color, -0.55f)))),
                )
            }
        }
        if (book.binding != BookshelfBinding.Linen) {
            for (y in listOf(14f, h - 16f)) {
                Box(Modifier.offset(4.dp, y.dp).size((w - 8f).dp, 1.5.dp).background(foil.copy(alpha = 0.85f)))
            }
        } else {
            Box(
                Modifier
                    .offset(5.dp, (h / 2f - (h - 70f) / 2f - 4f).dp)
                    .size((w - 10f).dp, (h - 62f).dp)
                    .background(Color(0xEBF4ECD6), RoundedCornerShape(2.dp))
                    .border(1.dp, Color(0x593C2814), RoundedCornerShape(2.dp)),
            )
        }
        Box(
            Modifier
                .offset((w / 2f - (h - 76f) / 2f).dp, (h / 2f - 10f).dp)
                .size((h - 76f).dp, 20.dp)
                .rotate(90f),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                book.section.uppercase(),
                style = TextStyle(
                    color = foil, fontFamily = FontFamily.Serif, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp,
                    shadow = Shadow(Color(0x8C000000), Offset(0f, 1f), 0.5f),
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
        Box(Modifier.offset((w * 0.28f).dp, 0.dp).size(3.dp, h.dp).background(Color(0x14FFFFFF)))
        Box(Modifier.size(1.dp, h.dp).background(Color(0x24FFFFFF)))
        Box(Modifier.offset((w - 1f).dp, 0.dp).size(1.dp, h.dp).background(Color(0x66000000)))
    }
}

/** The front cover: the same binding, a foil frame and the section's name. */
@Composable
private fun BookshelfCover(book: BookshelfBook, width: Float, images: BookshelfImages?) {
    val h = book.h
    val foil = Color(book.foil)
    val shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
    Box(
        Modifier
            .size(width.dp, h.dp)
            // Two shadows from one pre-blurred square, stretched: a tight one (0 2 4) and a soft one (0 18 40).
            .drawBehind {
                val soft = images?.soft ?: return@drawBehind
                scale(density, density, pivot = Offset.Zero) {
                    val m = SOFT_MARGIN
                    val sx = width / SOFT_SIZE
                    val sy = h / SOFT_SIZE
                    drawImageAt(soft, -m * sx, -m * sy + 18f, (SOFT_SIZE + 2 * m) * sx, (SOFT_SIZE + 2 * m) * sy, 0.35f)
                    drawRoundRect(Color(0x73000000), Offset(0f, 2f), Size(width, h), androidx.compose.ui.geometry.CornerRadius(4f))
                }
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to shade(book.color, 0.16f), 0.5f to Color(book.color), 1f to shade(book.color, -0.38f),
                    start = Offset.Zero, end = Offset.Infinite,
                ),
            ),
    ) {
        Box(Modifier.size(6.dp, h.dp).background(shade(book.color, -0.4f).copy(alpha = 0.8f)))
        Box(
            Modifier
                .offset(12.dp, 10.dp)
                .size((width - 20f).dp, (h - 20f).dp)
                .border(1.2.dp, foil.copy(alpha = 0.85f), RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .offset(16.dp, 14.dp)
                .size((width - 28f).dp, (h - 28f).dp)
                .border(0.6.dp, foil.copy(alpha = 0.6f), RoundedCornerShape(1.dp)),
        )
        BasicText(
            book.section.uppercase(),
            Modifier
                .offset(14.dp, (h * 0.38f).dp)
                .width((width - 24f).dp),
            style = TextStyle(color = foil, fontFamily = FontFamily.Serif, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
        )
        Box(Modifier.offset((width * 0.42f).dp, (h * 0.52f).dp).size((width * 0.16f).dp, 1.5.dp).background(foil.copy(alpha = 0.8f)))
        BasicText(
            "SHELF EDITIONS",
            Modifier
                .align(Alignment.BottomStart)
                .offset(14.dp, (-22).dp)
                .width((width - 24f).dp),
            style = TextStyle(color = foil.copy(alpha = 0.75f), fontSize = 6.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}
