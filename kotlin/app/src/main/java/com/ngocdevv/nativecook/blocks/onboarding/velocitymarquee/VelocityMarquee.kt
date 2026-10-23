package com.ngocdevv.nativecook.blocks.onboarding.velocitymarquee

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.launch

/** One onboarding page: a short headline and the line under it. */
data class MarqueeOnboardingPage(val title: String, val body: String)

private val Pages = listOf(
    MarqueeOnboardingPage("Photos that keep moving", "Afterimage strings your camera roll into reels that drift by on their own."),
    MarqueeOnboardingPage("Flick a reel to travel back", "Throw it to race through a year of days, then let it ease to a stop."),
    MarqueeOnboardingPage("Share the whole reel", "Send a moving set of frames to the people who were there with you."),
)

// A drag starts once the finger has travelled this far (dp).
private const val DRAG_SLOP = 2f
// Type: the headline and its line, each at most this many lines; the headline and the line grow
// with the system's font size up to TEXT_MAX_SCALE, as React Native's maxFontSizeMultiplier.
private const val TITLE_LINES = 2
private const val BODY_LINES = 3
private const val TEXT_MAX_SCALE = 1.15f
// The foot, top to bottom: the dots, the text, the button and the sign-in line (dp).
private const val DOTS_BELOW = 22f
private const val TEXT_BELOW = 32f
private const val BUTTON_H = 56f
private const val SIGN_IN_ABOVE = 4f
private const val SIGN_IN_H = 44f
// A press on the button squeezes it on a spring (stiffness, damping) and lets it spring back; a
// press that strays farther than this (dp) is no longer a tap.
private const val PRESS_SCALE = 0.97f
private const val PRESS_STIFFNESS = 420f
private const val PRESS_DAMPING = 26f
private const val BUTTON_SLOP = 40f
private const val SIGN_IN_SLOP = 30f

/** Paints reused every frame, so nothing is allocated while drawing. */
private class MarqueePaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
}

/**
 * An onboarding screen whose hero is three tilted rows of photo tiles running as endless
 * marquees, the middle one the other way. Swiping through the pages throws the rows with the
 * finger's speed: they race, the tiles lean into the motion and stretch, then ease back to the
 * idle drift while the headline under them changes. A "Get started" button and a sign-in line
 * sit at the foot.
 *
 * @param rows The rows, top to bottom; the middle one runs the other way. Ten to fourteen photos a row.
 * @param pages The pages, swiped through left and right; the dots under the rows follow them.
 * @param name The app's name, next to its mark at the top.
 * @param autoPlay Rests on each page, then swipes to the next one, throwing the rows, and starts over.
 * @param onPage Called with a page's index when a swipe lands on it.
 * @param background The paper.
 */
@Composable
fun VelocityMarquee(
    modifier: Modifier = Modifier,
    rows: List<List<MarqueePhoto>> = MarqueeRows,
    pages: List<MarqueeOnboardingPage> = Pages,
    name: String = "Afterimage",
    autoPlay: Boolean = false,
    onGetStarted: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onPage: ((Int) -> Unit)? = null,
    background: Color = Color(0xFFF2EFE8),
    ink: Color = Color(0xFF161512),
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val motion = remember(rows, pages.size) { MarqueeMotion(rows, pages.size) }
    val paints = remember { MarqueePaints() }
    val grain = remember { marqueeGrain() }
    val landed by rememberUpdatedState(onPage)
    val started by rememberUpdatedState(onGetStarted)
    val signedIn by rememberUpdatedState(onSignIn)

    val insets = WindowInsets.safeDrawing
    val safeTop = max(insets.getTop(density) / density.density, MIN_TOP)
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect { motion.reduced = reducedMotion }

    LaunchedEffect(motion, auto) { motion.setAuto(auto) }

    // The photos are drawn on the canvas, each fading in as it arrives; a photo that fails stays paper.
    LaunchedEffect(motion, context) {
        rows.flatten().forEachIndexed { i, photo ->
            launch {
                val image = loadMarqueePhoto(context, photo.url(if (photo.portrait) PORTRAIT_PX else LANDSCAPE_PX)) ?: return@launch
                if (i in motion.loaded.indices) motion.loaded[i] = Loaded(image, motion.elapsed)
            }
        }
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.064f)
                last = now
                // Everything has a place once the text block is laid out: the entrance starts.
                if (!motion.textTop.isNaN()) motion.enterOnce(EaseOutStrong)
                if (dt > 0f) motion.step(dt)
            }
        }
    }

    /** Turns the page `step` away, with a tick when it lands on a new one. */
    fun turn(step: Int) {
        val index = motion.turn(step)
        if (index >= 0) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            landed?.invoke(index)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background)
            .clipToBounds(),
    ) {
        val width = maxWidth.value

        // The paper, cached until the size changes and never drawn again per frame.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                        setLocalMatrix(Matrix().apply { setScale(GRAIN_TEXEL * density.density, GRAIN_TEXEL * density.density) })
                    }
                    val speck = Paint().apply { this.shader = shader }
                    onDrawBehind {
                        drawIntoCanvas {
                            it.nativeCanvas.drawColor(background.toArgb())
                            it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, speck)
                        }
                    }
                },
        )

        // The rows, rising and fading in once everything has a place. Dragging anywhere that is
        // not a button throws them; a sideways swipe turns the page.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    motion.elapsed
                    alpha = motion.hero.value
                    translationY = if (reducedMotion) 0f else (1f - motion.hero.value) * HERO_RISE * this.density
                }
                .semantics {
                    contentDescription = "$name introduction, ${pages.size} pages. Swipe left or right to turn the page."
                    customActions = listOf(
                        CustomAccessibilityAction("Next page") {
                            turn(1)
                            true
                        },
                        CustomAccessibilityAction("Previous page") {
                            turn(-1)
                            true
                        },
                    )
                }
                .pointerInput(motion) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        motion.begin()
                        var active = false
                        var last = down.position
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                if (!change.pressed) {
                                    if (active) {
                                        val v = tracker.calculateVelocity()
                                        val moved = change.position - down.position
                                        val index = motion.end(
                                            moved.x / density.density,
                                            moved.y / density.density,
                                            v.x / density.density,
                                            v.y / density.density,
                                            size.width / density.density,
                                        )
                                        if (index >= 0) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            landed?.invoke(index)
                                        }
                                    }
                                    break
                                }
                                if (!active) {
                                    val moved = change.position - down.position
                                    if (hypot(moved.x, moved.y) / density.density < DRAG_SLOP) continue
                                    active = true
                                }
                                change.consume()
                                val v = tracker.calculateVelocity()
                                motion.change(
                                    (change.position.x - last.x) / density.density,
                                    (change.position.y - last.y) / density.density,
                                    v.x / density.density,
                                    v.y / density.density,
                                )
                                last = change.position
                            }
                        } finally {
                            motion.finish()
                        }
                    }
                },
        ) {
            motion.elapsed
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas {
                    drawRows(it.nativeCanvas, motion, paints, size.width / density.density, safeTop)
                }
            }
        }

        // The app's mark and name, centred over the rows.
        Box(
            Modifier
                .padding(start = MARGIN.dp, end = MARGIN.dp, top = (safeTop + HEADER_TOP).dp)
                .fillMaxWidth()
                .height(HEADER_H.dp)
                .graphicsLayer { enterLayer(motion, 0, reducedMotion) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarqueeMark(ink, background)
                Spacer(Modifier.size(8.dp))
                BasicText(
                    name,
                    style = TextStyle(
                        color = ink,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        letterSpacing = (-0.2).sp,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                    ),
                    maxLines = 1,
                )
            }
        }

        // The dots, the page's text, the button and the sign-in line.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = (safeBottom + FOOT_GAP).dp)
                .fillMaxWidth()
                .onPlaced { motion.textTop = it.positionInParent().y / density.density },
        ) {
            MarqueeDots(
                motion,
                ink,
                Modifier
                    .fillMaxWidth()
                    .height(DOT.dp)
                    .graphicsLayer { enterLayer(motion, 1, reducedMotion) },
            )
            Spacer(Modifier.height(DOTS_BELOW.dp))
            // As tall as the tallest page, every page's text stacked in it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { enterLayer(motion, 2, reducedMotion) },
            ) {
                val fontScale = density.fontScale
                val capped = { value: Float -> (value * min(fontScale, TEXT_MAX_SCALE) / fontScale).sp }
                pages.forEachIndexed { i, page ->
                    MarqueePageText(page, i, motion, ink, reducedMotion, capped)
                }
            }
            Spacer(Modifier.height(TEXT_BELOW.dp))
            MarqueeButton(
                ink,
                background,
                Modifier.graphicsLayer { enterLayer(motion, 3, reducedMotion) },
            ) { started?.invoke() }
            Spacer(Modifier.height(SIGN_IN_ABOVE.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { enterLayer(motion, 4, reducedMotion) },
                contentAlignment = Alignment.Center,
            ) {
                MarqueeSignIn(ink) { signedIn?.invoke() }
            }
        }
    }
}

/** Fades and rises in as its entrance runs, read every frame. */
private fun GraphicsLayerScope.enterLayer(m: MarqueeMotion, i: Int, reduced: Boolean) {
    m.elapsed
    val v = m.enter[i].value
    alpha = v
    translationY = if (reduced) 0f else (1f - v) * ENTER_RISE * density
}

/** One page's headline and line, sliding with the page and gone before the next one arrives. */
@Composable
private fun MarqueePageText(
    page: MarqueeOnboardingPage,
    index: Int,
    motion: MarqueeMotion,
    ink: Color,
    reduced: Boolean,
    capped: (Float) -> TextUnit,
) {
    val lines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    // Read as one passage, and only for the page shown.
    val reading = if (index == motion.shown) Modifier.semantics(mergeDescendants = true) {} else Modifier.clearAndSetSemantics {}
    Column(
        Modifier
            .fillMaxWidth()
            .then(reading)
            .graphicsLayer {
                motion.elapsed
                val d = pageDistance(motion.page, index, motion.count).toFloat()
                alpha = clamp(1f - abs(d) / TEXT_GONE, 0f, 1f)
                translationX = if (reduced) 0f else -d * TEXT_SHIFT * density
            },
    ) {
        BasicText(
            page.title,
            style = TextStyle(
                color = ink,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = capped(30f),
                lineHeight = capped(35f),
                letterSpacing = capped(-0.4f),
                lineHeightStyle = lines,
            ),
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            page.body,
            style = TextStyle(
                color = ink.copy(alpha = ink.alpha * 0.6f),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = capped(16f),
                lineHeight = capped(22f),
                lineHeightStyle = lines,
            ),
            maxLines = BODY_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The page dots: wide and dark for the page shown, a small faint dot for the others. */
@Composable
private fun MarqueeDots(motion: MarqueeMotion, ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        motion.elapsed
        val u = density
        var left = 0f
        for (k in 0 until motion.count) {
            val near = clamp(1f - abs(pageDistance(motion.page, k, motion.count)).toFloat(), 0f, 1f)
            val w = DOT + (ACTIVE_W - DOT) * near
            drawRoundRect(
                ink.copy(alpha = ink.alpha * (0.2f + 0.8f * near)),
                topLeft = Offset(left * u, 0f),
                size = androidx.compose.ui.geometry.Size(w * u, DOT * u),
                cornerRadius = CornerRadius(DOT / 2f * u),
            )
            left += w + DOT_GAP
        }
    }
}

/** "Get started": squeezes on touch down with a light tap and springs back on release. */
@Composable
private fun MarqueeButton(ink: Color, paper: Color, modifier: Modifier, onTap: () -> Unit) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val tapped by rememberUpdatedState(onTap)
    val scale by animateFloatAsState(
        if (pressed) PRESS_SCALE else 1f,
        spring(dampingRatio = PRESS_DAMPING / (2f * kotlin.math.sqrt(PRESS_STIFFNESS)), stiffness = PRESS_STIFFNESS, visibilityThreshold = Spring.DefaultDisplacementThreshold),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .height(BUTTON_H.dp)
            .clip(RoundedCornerShape(BUTTON_H.dp / 2))
            .background(ink)
            .semantics {
                contentDescription = "Get started"
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (awaitTap(down.id, down.position, BUTTON_SLOP * density)) tapped()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Get started",
            style = TextStyle(color = paper, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.1.sp),
        )
    }
}

/** "Already have an account? Sign in", dimming while pressed. */
@Composable
private fun MarqueeSignIn(ink: Color, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val tapped by rememberUpdatedState(onTap)
    // Reanimated's default timing curve, an ease-in-out quad: 100 ms down, 200 ms back.
    val dim by animateFloatAsState(
        if (pressed) 0.45f else 1f,
        tween(if (pressed) 100 else 200, easing = InOutQuad),
        label = "dim",
    )
    Row(
        Modifier
            .graphicsLayer { alpha = dim }
            .height(SIGN_IN_H.dp)
            .semantics {
                contentDescription = "Sign in"
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    if (awaitTap(down.id, down.position, SIGN_IN_SLOP * density)) tapped()
                    pressed = false
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = TextStyle(color = ink, fontFamily = FontFamily.SansSerif, fontSize = 15.sp)
        BasicText("Already have an account?", style = text.copy(color = ink.copy(alpha = ink.alpha * 0.55f), fontWeight = FontWeight.Normal))
        Spacer(Modifier.size(5.dp))
        BasicText("Sign in", style = text.copy(fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Waits for the finger to lift; true for a tap, false once it strays farther than `slop` px from
 * where it went down or the touch is taken away.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitTap(
    id: androidx.compose.ui.input.pointer.PointerId,
    start: Offset,
    slop: Float,
): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        if (!change.pressed) {
            change.consume()
            return true
        }
        val moved = change.position - start
        if (hypot(moved.x, moved.y) > slop) return false
    }
}

/** The app's mark: a solid disc linked with a ring, the ring cut where it crosses the disc. */
@Composable
private fun MarqueeMark(ink: Color, paper: Color) {
    Canvas(Modifier.size(28.dp, 20.dp)) {
        val u = density
        val disc = ComposePath().apply { addOval(androidx.compose.ui.geometry.Rect(1f * u, 1f * u, 19f * u, 19f * u)) }
        val ringCenter = Offset(19f * u, 10f * u)
        drawCircle(ink, radius = 9f * u, center = Offset(10f * u, 10f * u))
        drawCircle(ink, radius = 7.5f * u, center = ringCenter, style = Stroke(width = 2f * u))
        clipPath(disc) {
            drawCircle(paper, radius = 7.5f * u, center = ringCenter, style = Stroke(width = 2f * u))
        }
    }
}

private suspend fun loadMarqueePhoto(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        // A software bitmap can carry mipmaps, which keep the tiles from shimmering as they shrink.
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    return result.image.toBitmap().also { it.setHasMipMap(true) }
}

/** The rows, each turned about its own centre line, filling the space between the brand line and the text. */
private fun drawRows(canvas: android.graphics.Canvas, m: MarqueeMotion, p: MarqueePaints, width: Float, top: Float) {
    if (width <= 0f || m.rows.isEmpty() || m.textTop.isNaN()) return
    val x = m.travel
    val lean = m.lean
    val reference = m.reference.toDouble()
    val angle = clamp(lean * LEAN_PER, -LEAN_MAX, LEAN_MAX)
    val skew = tan(angle * PI / 180).toFloat()
    val heroTop = top + HEADER_TOP + HEADER_H + HERO_GAP
    val heroBottom = max(heroTop + 1f, m.textTop - HERO_GAP)

    // The rows fill the hero, growing or shrinking together with the screen; the tilt lifts the
    // right end of each row and drops the left, so that is counted in the height they need.
    val rise = width * tan(abs(TILT) * PI / 180).toFloat()
    val need = (m.rows.size - 1) * ROW_PITCH + ROW_H + rise
    val scale = clamp((heroBottom - heroTop) / need, MIN_HERO_SCALE, MAX_HERO_SCALE)
    val midY = (heroTop + heroBottom) / 2f
    val span = width / 2f / scale + LANDSCAPE_W
    val stretch = 1f + min(STRETCH_MAX, abs(lean) * STRETCH_PER)
    val thin = 1f - (stretch - 1f) / 3f
    canvas.save()
    canvas.translate(width / 2f, midY)
    canvas.scale(scale, scale)
    m.rows.forEachIndexed { r, row ->
        val moved = row.dir * x * (row.length / reference)
        canvas.save()
        canvas.translate(0f, (r - (m.rows.size - 1) / 2f) * ROW_PITCH)
        canvas.rotate(TILT)
        for (tile in row.tiles) {
            val c = wrap(tile.offset + tile.width / 2.0 + moved + row.length / 2.0, row.length.toDouble()).toFloat() - row.length / 2f
            if (abs(c) - tile.width / 2f > span) continue
            canvas.save()
            canvas.translate(c, 0f)
            // A row moving right leans its tops to the right, like type set in italic.
            canvas.skew(-row.dir * skew, 0f)
            canvas.scale(stretch, thin)
            drawPhoto(canvas, p, m, tile.index, tile.width, ROW_H)
            canvas.restore()
        }
        canvas.restore()
    }
    canvas.restore()
}

/** One photo centred on the origin, in a rounded box: paper until it arrives, then fading in. */
private fun drawPhoto(canvas: android.graphics.Canvas, p: MarqueePaints, m: MarqueeMotion, index: Int, w: Float, h: Float) {
    val loaded = if (index in m.loaded.indices) m.loaded[index] else null
    val shown = if (loaded != null) clamp((m.elapsed - loaded.at) / PHOTO_FADE, 0f, 1f) else 0f
    if (shown < 1f) {
        p.fill.color = TILE_PAPER
        canvas.drawRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, TILE_RADIUS, TILE_RADIUS, p.fill)
    }
    if (loaded != null && shown > 0f) {
        val iw = max(1f, loaded.image.width.toFloat())
        val ih = max(1f, loaded.image.height.toFloat())
        // The photo covers the box: the canvas is scaled so the box is in the photo's pixels.
        val scale = max(w / iw, h / ih)
        if (!(scale > 0f) || !scale.isFinite()) return
        canvas.save()
        canvas.scale(scale, scale)
        p.art.shader = loaded.shader
        p.art.alpha = (shown * 255).roundToInt()
        canvas.drawRoundRect(-w / 2f / scale, -h / 2f / scale, w / 2f / scale, h / 2f / scale, TILE_RADIUS / scale, TILE_RADIUS / scale, p.art)
        p.art.shader = null
        canvas.restore()
    }
}
