package com.ngocdevv.nativecook.blocks.cards.petrockcarousel

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val Paper = Color(0xFFEDEAE4)
private val PaperTop = Color(0xFFF2F0EB)
private val PaperBottom = Color(0xFFE6E2DB)
private val Ink = Color(0xFF141414)
private val Muted = Color(0xFF8A857D)

private val TitleStyle = TextStyle(color = Ink, fontSize = TITLE_SIZE.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp, fontFamily = FontFamily.SansSerif)

/**
 * The title's letters: PET, a gap the little rock sits in, ROCK. Each leaves to its side, the one
 * nearest the rock (rank 0) first.
 */
private class TitleLetter(val char: String, val side: Float, val rank: Int)

private val Letters = listOf(
    TitleLetter("P", -1f, 2), TitleLetter("E", -1f, 1), TitleLetter("T", -1f, 0),
    TitleLetter("R", 1f, 0), TitleLetter("O", 1f, 1), TitleLetter("C", 1f, 2), TitleLetter("K", 1f, 3),
)

/**
 * A carousel of painted pet rocks on paper. It opens on the title "PET ROCK" with a tiny rock for
 * the gap between the words; Enter grows that rock into the carousel as the letters split away.
 * The rock in front is large, its neighbours smaller above and below; dragging scrolls them with
 * the finger and a release snaps to a rock. When a rock comes to rest its face blurs into the next
 * one and its googly eyes jiggle; tapping it squashes it and makes it wobble.
 *
 * @param autoPlay Plays the intro, scrolls the rocks, morphs their faces and starts over, on a loop of 14 s.
 * @param onFocus Called with a rock's name whenever one comes to rest in front.
 */
@Composable
fun PetRockCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onFocus: ((String) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusing by rememberUpdatedState(onFocus)
    val engine = remember {
        PetRockEngine(object : PetRockHost {
            override fun selection() {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }

            override fun light() {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            override fun medium() {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }

            override fun soft() {
                view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
            }

            override fun focused(name: String) {
                focusing?.invoke(name)
            }
        })
    }
    SideEffect { engine.reducedMotion = reducedMotion }
    val art = remember { PetRockArt(PetRocks.size) }
    // Each frame bumps `tick`; drawing and layers read it, so only they run again.
    var tick by remember { mutableLongStateOf(0L) }
    var inCarousel by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf(engine.caption) }
    var oldCaption by remember { mutableStateOf(engine.oldCaption) }
    var focus by remember { mutableIntStateOf(0) }

    LaunchedEffect(engine, auto) { engine.setAutoPlay(auto) }
    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                engine.step(dt)
                tick = now
                if (inCarousel != engine.inCarousel) inCarousel = engine.inCarousel
                if (caption !== engine.caption) caption = engine.caption
                if (oldCaption !== engine.oldCaption) oldCaption = engine.oldCaption
                if (focus != engine.focus) focus = engine.focus
            }
        }
    }

    // Everything that never moves on a rock is drawn once, off the main thread; each photo is
    // turned grey as it arrives.
    LaunchedEffect(art, density) {
        val width = context.resources.displayMetrics.widthPixels / density.density
        val pixels = density.density * min(BIG_MAX, width * BIG_SHARE) / 200f
        launch(Dispatchers.Default) { art.prepare(PetRocks, pixels) }
        val loader = SingletonImageLoader.get(context)
        PetRocks.forEachIndexed { i, rock ->
            launch {
                val request = ImageRequest.Builder(context)
                    .data("https://images.unsplash.com/${rock.photo}?auto=format&fit=crop&w=$PHOTO_WIDTH&q=80")
                    .size(Size.ORIGINAL)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                val grey = withContext(Dispatchers.Default) { PetRockArt.stonePhoto(result.image.toBitmap()) }
                art.photos = art.photos.copyOf().also { it[i] = grey }
            }
        }
    }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val measurer = rememberTextMeasurer()
    val letterWidths = remember(measurer, density) {
        Letters.map { with(density) { measurer.measure(it.char, TitleStyle).size.width.toDp().value } }
    }
    val paints = remember { PetRockPaints() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Paper)
            .semantics { contentDescription = "Pet rocks: ${PetRocks.joinToString { it.name }}" },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        // The row of letters and the gap, centred; the title rock sits in the middle of the gap.
        val gap = TITLE_ROCK + 6f
        val rowLeft = (width - (letterWidths.sum() + gap)) / 2f
        val centres = FloatArray(Letters.size)
        var x = rowLeft
        var titleX = 0f
        letterWidths.forEachIndexed { k, w ->
            if (k == 3) {
                titleX = x + gap / 2f
                x += gap
            }
            centres[k] = x + w / 2f
            x += w
        }
        val layout = remember(width, height, titleX) { PetRockLayout(width, height, titleX) }
        SideEffect { engine.layout = layout }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        engine.touched()
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        var panning = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                val dx = (change.position.x - start.x) / density.density
                                val dy = (change.position.y - start.y) / density.density
                                if (!change.pressed) {
                                    if (panning) {
                                        panning = false
                                        change.consume()
                                        engine.panEnded(tracker.calculateVelocity().y / density.density)
                                    } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                        engine.tapped(change.position.x / density.density, change.position.y / density.density)
                                    }
                                    break
                                }
                                if (!panning && sqrt(dx * dx + dy * dy) >= DRAG_SLOP) {
                                    panning = true
                                    engine.panStarted()
                                }
                                if (panning) {
                                    change.consume()
                                    engine.panMoved(dy)
                                }
                            }
                        } finally {
                            // A finger lost to the system (or a cancelled gesture) still lets go of the carousel.
                            if (panning) engine.panEnded(0f)
                        }
                    }
                },
        ) {
            tick
            drawRect(Brush.verticalGradient(listOf(PaperTop, PaperBottom)))
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                for (index in PetRocks.indices) drawRock(nc, index, engine, art, layout, paints, reducedMotion)
                nc.restore()
            }
            // The paper fades over the rocks at the top and bottom, behind the header and caption.
            val d = density.density
            drawRect(
                Brush.verticalGradient(listOf(PaperTop, PaperTop.copy(alpha = 0f)), startY = (safeTop + 34f) * d, endY = (safeTop + 130f) * d),
                size = androidx.compose.ui.geometry.Size(size.width, (safeTop + 130f) * d),
            )
            val bottom = height - safeBottom
            drawRect(
                Brush.verticalGradient(listOf(PaperBottom.copy(alpha = 0f), PaperBottom), startY = (bottom - 130f) * d, endY = (bottom - 40f) * d),
                topLeft = androidx.compose.ui.geometry.Offset(0f, (bottom - 130f) * d),
                size = androidx.compose.ui.geometry.Size(size.width, (safeBottom + 130f) * d),
            )
        }

        // The title: PET, a gap the little rock sits in, ROCK. Each letter leaves sideways, the one
        // nearest the rock first, and fades.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    tick
                    alpha = 1f - clamp(engine.intro.value * 2f, 0f, 1f)
                },
        ) {
            Letters.forEachIndexed { k, letter ->
                val rank = letter.rank
                BasicText(
                    letter.char,
                    Modifier
                        .centredAt(centres[k], layout.titleY)
                        .graphicsLayer {
                            tick
                            val p = clamp((engine.intro.value - rank * SPLIT_LAG) / 0.62f, 0f, 1f)
                            alpha = 1f - clamp(p * 1.5f, 0f, 1f)
                            translationX = if (reducedMotion) 0f else (letter.side * p * p * (SPLIT + rank * 18f)).dp.toPx()
                        },
                    style = TitleStyle,
                )
            }
        }

        val pressSource = remember { MutableInteractionSource() }
        val pressed by pressSource.collectIsPressedAsState()
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 22.dp, end = 22.dp, bottom = (safeBottom + 14f).dp)
                .fillMaxWidth()
                .height(50.dp)
                .graphicsLayer {
                    tick
                    val e = clamp(engine.intro.value, 0f, 1f)
                    alpha = 1f - clamp(e * 2.2f, 0f, 1f)
                    translationY = if (reducedMotion) 0f else (e * 120f).dp.toPx()
                    val s = engine.press.value * if (pressed) 0.97f else 1f
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(25.dp))
                .background(Ink)
                .then(
                    if (inCarousel) {
                        Modifier
                    } else {
                        Modifier.clickable(pressSource, indication = null, role = Role.Button) { engine.enterPressed() }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Enter", style = TextStyle(color = Color(0xFFF4F1EA), fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif))
        }

        // The wordmark at the top takes the carousel back to the title.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = (safeTop + 8f).dp)
                .graphicsLayer {
                    tick
                    alpha = clamp((engine.intro.value - 0.5f) * 2f, 0f, 1f)
                }
                .then(
                    if (inCarousel) {
                        Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { engine.titlePressed() }
                    } else {
                        Modifier
                    },
                ),
        ) {
            BasicText(
                "PET ROCK",
                style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, fontFamily = FontFamily.SansSerif),
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 22f).dp)
                .graphicsLayer {
                    tick
                    alpha = clamp((engine.intro.value - 0.5f) * 2f, 0f, 1f)
                },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.BottomStart) {
                oldCaption?.let { old ->
                    CaptionLine(old.focus, old.face) {
                        tick
                        1f - inOutQuad(clamp((engine.elapsed - old.at) / CAPTION_OUT, 0f, 1f))
                    }
                }
                val now = caption
                CaptionLine(now.focus, now.face) {
                    tick
                    if (now.at < 0f) 1f else inOutQuad(clamp((engine.elapsed - now.at) / CAPTION_IN, 0f, 1f))
                }
            }
            BasicText(
                "%02d / %02d".format(focus + 1, PetRocks.size),
                style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum"),
            )
        }
    }
}

/** The rock's name and its mood, faded by [alpha]. */
@Composable
private fun CaptionLine(focus: Int, face: Int, alpha: () -> Float) {
    Column(Modifier.graphicsLayer { this.alpha = alpha() }) {
        BasicText(
            PetRocks[focus].name,
            style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, fontFamily = FontFamily.SansSerif),
        )
        BasicText(
            PetRockFaces.Moods[face],
            Modifier.padding(top = 2.dp),
            style = TextStyle(color = Muted, fontSize = 14.sp, fontFamily = FontFamily.SansSerif),
        )
    }
}

/** Fills the parent and puts the content's centre at ([x], [y]) dp. */
private fun Modifier.centredAt(x: Float, y: Float) = layout { measurable, constraints ->
    val p = measurable.measure(Constraints())
    layout(constraints.maxWidth, constraints.maxHeight) {
        p.place((x.dp.toPx() - p.width / 2f).roundToInt(), (y.dp.toPx() - p.height / 2f).roundToInt())
    }
}

/** Paints reused every frame. */
private class PetRockPaints {
    val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val tone = Paint(Paint.ANTI_ALIAS_FLAG)
    val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.SOFT_LIGHT else xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
    val grain = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.OVERLAY else xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
    val pupil = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF()
}

// roundToInt throws on NaN, which coerceIn passes straight through.
private fun alphaOf(a: Float) = if (a.isNaN()) 0 else (a.coerceIn(0f, 1f) * 255f).roundToInt()

/** One painted rock: its ground shadow, the stone, its face's paint, grain and shading, and the googly eyes on top. */
private fun drawRock(
    canvas: android.graphics.Canvas,
    index: Int,
    engine: PetRockEngine,
    art: PetRockArt,
    layout: PetRockLayout,
    paints: PetRockPaints,
    reducedMotion: Boolean,
) {
    val layers = art.rocks[index] ?: return
    val rock = PetRocks[index]
    val p = index - engine.scroll.value
    val e = engine.intro.value
    val opacity = if (index == 0) 1f else clamp(e, 0f, 1f) * clamp(4f - abs(p), 0f, 1f)
    if (opacity <= 0.001f) return

    var x = layout.width / 2f
    var y = layout.slotY(p)
    var scale = layout.slotScale(p)
    val titleScale = TITLE_ROCK / layout.big
    if (index == 0) {
        // The first rock is the gap in the title: it grows out of it into the carousel.
        x = layout.titleX + (x - layout.titleX) * e
        y = layout.titleY + (y - layout.titleY) * e
        scale = titleScale + (scale - titleScale) * e
    } else if (!reducedMotion) {
        // The others come in from below as the carousel opens.
        y += (1f - clamp(e, 0f, 1f)) * layout.height * 0.7f
    }
    // The tap's squash and wobble belong to the rock in front.
    val front = max(0f, 1f - abs(p) * 2f)
    val sq = engine.squash.value * front
    val morph = engine.morphs[index]
    val m = morph.mix.value
    val pop = if (reducedMotion) 1f else 1f + MORPH_POP * 4f * m * (1f - m) * (if (morph.morphing) 1f else 0f)
    val swing = if (reducedMotion) 0f else clamp(p, -1.5f, 1.5f) * 0.12f

    canvas.save()
    canvas.translate(x, y)
    canvas.rotate(Math.toDegrees((rock.tilt + swing + engine.wobble.value * front * WOBBLE).toDouble()).toFloat())
    val s = layout.big / 200f * scale
    canvas.scale(s, s)
    // Squash about the base, so the rock stays standing where it is.
    canvas.translate(0f, 90f * rock.aspect)
    canvas.scale((1f + SQUASH_X * sq) * pop, (1f - SQUASH_Y * sq) * pop)
    canvas.translate(0f, -90f * rock.aspect)

    val image = paints.image
    image.alpha = alphaOf(opacity)
    canvas.drawBitmap(layers.shadow, null, PetRockArt.ShadowBox, image)

    canvas.save()
    canvas.clipPath(layers.path)
    val box = PetRockArt.Box
    paints.tone.color = rock.tone
    paints.tone.alpha = alphaOf(opacity)
    canvas.drawRect(box, paints.tone)
    art.photos[index]?.let { photo ->
        // The photo covers a square PHOTO_ZOOM times the rock box, laid over the tone in soft light.
        val side = 200f * PHOTO_ZOOM
        val fill = max(side / photo.width, side / photo.height)
        val w = photo.width * fill
        val h = photo.height * fill
        paints.rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        paints.photo.alpha = alphaOf(opacity * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 0.75f else 0.4f)
        canvas.drawBitmap(photo, null, paints.rect, paints.photo)
    }
    faceSlot(morph, false, opacity) { face, alpha, sigma -> drawLevels(canvas, art.paint[face], sigma, alpha, image) }
    faceSlot(morph, true, opacity) { face, alpha, sigma -> drawLevels(canvas, art.paint[face], sigma, alpha, image) }
    // Grain over stone and paint alike, so the paint looks sprayed on. Each rock samples its own
    // part of the shared texture.
    art.grain?.let { grain ->
        val side = PetRockArt.GRAIN_SIDE
        val shift = ((rock.seed * 37) % 100).toFloat()
        paints.rect.set(-side / 2f + shift - 50f, -side / 2f + 50f - shift, side / 2f + shift - 50f, side / 2f + 50f - shift)
        paints.grain.alpha = alphaOf(opacity * GRAIN_ALPHA)
        canvas.drawBitmap(grain, null, paints.rect, paints.grain)
    }
    canvas.drawBitmap(layers.shade, null, box, image)
    canvas.restore()

    val px = engine.px
    val py = engine.py
    val stickers = { face: Int, alpha: Float, sigma: Float ->
        if (PetRockFaces.Eyes[face].isNotEmpty()) {
            drawLevels(canvas, art.domes[face], sigma, alpha, image)
            drawPupils(canvas, face, px, py, alpha, sigma, paints.pupil)
        }
    }
    faceSlot(morph, false, opacity, stickers)
    faceSlot(morph, true, opacity, stickers)
    canvas.restore()
}

/**
 * One of a rock's two face slots: alone and sharp when it is the one showing, or, during a morph,
 * faded and blurred by how far the crossfade has gone.
 */
private inline fun faceSlot(morph: PetRockMorph, slotB: Boolean, opacity: Float, draw: (face: Int, alpha: Float, sigma: Float) -> Unit) {
    val face = if (slotB) morph.b else morph.a
    if (!morph.morphing) {
        if (morph.showB == slotB) draw(face, opacity, 0f)
        return
    }
    val m = morph.mix.value
    val alpha = if (slotB) m else 1f - m
    if (alpha <= 0.002f) return
    draw(face, opacity * alpha, (if (slotB) 1f - m else m) * MORPH_BLUR)
}

/**
 * A layer drawn ahead of time at a few blur levels, shown at [sigma] by crossfading the two levels
 * either side of it.
 */
private fun drawLevels(canvas: android.graphics.Canvas, levels: Array<Bitmap?>, sigma: Float, alpha: Float, paint: Paint) {
    val steps = PetRockArt.LEVELS
    val at = clamp(sigma / steps[1], 0f, (steps.size - 1).toFloat())
    val lo = at.toInt().coerceAtMost(steps.size - 1)
    val hi = min(lo + 1, steps.size - 1)
    val f = at - lo
    // Levels not drawn yet fall back to the sharp one.
    val low = levels[lo] ?: levels[0] ?: return
    val high = levels[hi] ?: low
    paint.alpha = alphaOf(alpha * (1f - f))
    if (paint.alpha > 0) canvas.drawBitmap(low, null, PetRockArt.Box, paint)
    paint.alpha = alphaOf(alpha * f)
    if (paint.alpha > 0) canvas.drawBitmap(high, null, PetRockArt.Box, paint)
}

/** The loose pupils (at [px], [py], −1…1 each way in their eye) and the glint over them. */
private fun drawPupils(canvas: android.graphics.Canvas, face: Int, px: Float, py: Float, alpha: Float, sigma: Float, paint: Paint) {
    paint.maskFilter = if (sigma > 0.35f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    } else {
        null
    }
    for (eye in PetRockFaces.Eyes[face]) {
        val r = eye.r
        paint.color = 0xFF0C0C0D.toInt()
        paint.alpha = alphaOf(alpha)
        canvas.drawCircle(eye.x + px * r * PetRockFaces.PUPIL_TRAVEL, eye.y + py * r * PetRockFaces.PUPIL_TRAVEL, r * PetRockFaces.PUPIL, paint)
        paint.color = android.graphics.Color.WHITE
        paint.alpha = alphaOf(alpha * 0.8f)
        canvas.drawOval(eye.x - r * 0.62f, eye.y - r * 0.72f, eye.x - r * 0.62f + r * 0.55f, eye.y - r * 0.72f + r * 0.32f, paint)
    }
}
