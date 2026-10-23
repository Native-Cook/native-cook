package com.ngocdevv.nativecook.blocks.gestures.scratchticket

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val Background = Color(0xFF13251B)
private const val PAPER = 0xFFF4ECD9.toInt()
private val Paper = Color(PAPER)
private val Ink = Color(0xFF1F2A22)
private val Muted = Color(0xFF857C69)
private val ButtonInk = Color(0xFF14231A)
private const val RADIUS = 20f
private const val SCRATCH_RADIUS = 14f
// The card's canvas reaches this far past the ticket on every side, so its shadow is not cut off.
private const val BLEED = 40f
// The photo sits this much zoomed in under the foil and settles to 1 as the foil dissolves.
private const val PHOTO_ZOOM = 0.1f
private const val BAR_W = 92f
private const val BAR_H = 28f

private fun heavy(family: String, weight: Int): Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, false)
} else {
    Typeface.create(family, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
}

private val Sans = FontFamily.SansSerif
private val Condensed = FontFamily(androidx.compose.ui.text.font.Typeface(heavy("sans-serif-condensed", 800)))
private val Caps = TextStyle(fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Muted)
private val PrizeStyle = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
private val DetailStyle = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Muted)

/**
 * A scratch-off lottery ticket on a dark green table. A finger rubs the holographic foil away
 * along a soft brush, tearing off little foil flakes that fall with gravity; once enough is
 * cleared the rest dissolves with a golden edge, the prize photo settles into view, the stub
 * prints the prize and confetti bursts. "New ticket" (or throwing the ticket left) deals the next.
 *
 * @param autoPlay Scratches, reveals and deals a new ticket on its own, on a loop of 9 s.
 * @param onReveal Called with the prize when a ticket is revealed.
 */
@Composable
fun ScratchTicket(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onReveal: ((String) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val revealing by rememberUpdatedState(onReveal)
    val engine = remember {
        ScratchEngine(object : ScratchHost {
            override fun success() {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
            }

            override fun light() {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            override fun revealed(prize: String) {
                revealing?.invoke(prize)
            }
        })
    }
    SideEffect { engine.reducedMotion = reducedMotion }
    // Each frame bumps `tick`; drawing and layers read it, so only they run again.
    var tick by remember { mutableLongStateOf(0L) }
    // What each card shows that needs composing again: its prize, and whether it is revealed.
    val tickets = remember { mutableStateListOf(0, 1) }
    val revealed = remember { mutableStateListOf(false, false) }
    val photos = remember { mutableStateListOf<Bitmap?>(null, null, null, null) }

    LaunchedEffect(engine, autoPlay) { engine.setAutoPlay(autoPlay) }
    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else clamp((now - last) / 1e9f, 0f, 0.05f)
                last = now
                engine.step(dt)
                tick = now
                engine.slots.forEachIndexed { k, s ->
                    if (tickets[k] != s.ticket) tickets[k] = s.ticket
                    if (revealed[k] != s.revealed) revealed[k] = s.revealed
                }
            }
        }
    }
    // Every prize photo is fetched up front, so a new ticket never waits for its picture.
    LaunchedEffect(context) {
        val loader = SingletonImageLoader.get(context)
        ScratchPrizes.forEachIndexed { k, prize ->
            launch {
                val request = ImageRequest.Builder(context).data(prize.url).size(Size.ORIGINAL).allowHardware(false).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                photos[k] = result.image.toBitmap()
            }
        }
    }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val paints = remember { ScratchPaints() }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val geo = remember(width, height, safeTop, safeBottom) { ScratchGeometry(width, height, safeTop, safeBottom) }
        SideEffect { engine.geo = geo }

        // The green baize: a soft pool of light and a fine grain.
        Canvas(Modifier.fillMaxSize()) {
            // RadialGradient throws on a radius of 0, which a zero first frame would give it.
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                val w = size.width
                val h = size.height
                paints.pool.shader = RadialGradient(
                    w / 2f, h * 0.42f, h * 0.7f,
                    intArrayOf(0xFF1D3A29.toInt(), 0xFF13251B.toInt(), 0xFF0A150F.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                nc.drawRect(0f, 0f, w, h, paints.pool)
                nc.drawRect(0f, 0f, w, h, paints.baize)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    val tracker = VelocityTracker()
                    // VelocityTracker can report NaN or infinity from a burst of events at one instant.
                    fun finite(v: Float) = if (v.isFinite()) v else 0f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.density
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        engine.touchBegan(down.position.x / d, down.position.y / d)
                        var released = false
                        var endTx = 0f
                        var endVx = 0f
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                val tx = (change.position.x - down.position.x) / d
                                if (!change.pressed) {
                                    released = true
                                    endTx = tx
                                    endVx = finite(tracker.calculateVelocity().x / d)
                                    break
                                }
                                change.consume()
                                val v = tracker.calculateVelocity()
                                engine.touchMoved(change.position.x / d, change.position.y / d, tx, finite(v.x / d), finite(v.y / d))
                            }
                        } finally {
                            // A finger lost to the system (or a cancelled gesture) still lets go of the ticket.
                            if (released) engine.touchEnded(endTx, endVx) else engine.touchEnded(0f, 0f)
                        }
                    }
                },
        ) {
            for (k in 0..1) {
                ScratchCard(
                    slot = engine.slots[k],
                    prize = ScratchPrizes[tickets[k]],
                    photo = photos[tickets[k]],
                    revealed = revealed[k],
                    geo = geo,
                    engine = engine,
                    reducedMotion = reducedMotion,
                    paints = paints,
                    tick = { tick },
                )
            }
        }

        NewTicketButton(engine, geo) { tick }

        // Foil flakes (drawn with the foil itself), confetti and autoPlay's virtual fingertip.
        Canvas(Modifier.fillMaxSize()) {
            tick
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                if (engine.flakes.isNotEmpty()) {
                    ScratchPieces.path(paints.pieces, engine.flakes, -1, 0.8f)
                    paints.foil(width, height, engine.phase, engine.tiltX, engine.tiltY, 0f)
                    nc.drawPath(paints.pieces.asAndroidPath(), paints.foilPaint)
                }
                if (engine.confetti.isNotEmpty()) {
                    ConfettiColours.forEachIndexed { k, colour ->
                        ScratchPieces.path(paints.pieces, engine.confetti, k, 1.7f)
                        paints.fill.color = colour
                        nc.drawPath(paints.pieces.asAndroidPath(), paints.fill)
                    }
                }
                // The virtual finger of autoPlay, so a recording shows what is scratching.
                val alpha = engine.fingerAlpha.value
                if (alpha > 0.001f) {
                    paints.fill.color = android.graphics.Color.WHITE
                    paints.fill.alpha = (alpha * 0.22f * 255).toInt()
                    nc.drawCircle(engine.fingerX, engine.fingerY, 22f, paints.fill)
                    paints.ring.alpha = (alpha * 0.6f * 255).toInt()
                    nc.drawCircle(engine.fingerX, engine.fingerY, 22f, paints.ring)
                }
                nc.restore()
            }
        }
    }
}

/** The pill under the ticket; it fills cream with an arrow once a prize is out. */
@Composable
private fun NewTicketButton(engine: ScratchEngine, geo: ScratchGeometry, tick: () -> Long) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(pressed) {
        if (first) first = false else engine.buttonPressing(pressed)
    }
    Box(
        Modifier
            .offset(geo.ticketX.dp, geo.buttonY.dp)
            .size(geo.ticketW.dp, BUTTON_HEIGHT.dp)
            .graphicsLayer {
                tick()
                scaleX = engine.press.value
                scaleY = engine.press.value
            }
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(244, 236, 217).copy(alpha = 0.28f), RoundedCornerShape(50))
            .clickable(source, indication = null, role = Role.Button) { engine.newTicketPressed() },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { tick(); alpha = engine.emphasis.value }.background(Paper))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "New ticket",
                style = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
                color = { tick(); lerp(Paper, ButtonInk, clamp(engine.emphasis.value, 0f, 1f)) },
            )
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(16.dp).graphicsLayer { tick(); alpha = engine.emphasis.value }) {
                val s = size.width / 16f
                val arrow = androidx.compose.ui.graphics.Path().apply {
                    moveTo(2f * s, 8f * s)
                    lineTo(13f * s, 8f * s)
                    moveTo(9f * s, 3.5f * s)
                    lineTo(13.5f * s, 8f * s)
                    lineTo(9f * s, 12.5f * s)
                }
                drawPath(
                    arrow, ButtonInk,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                )
            }
        }
    }
}

/**
 * One lottery ticket: cream card stock with a tear-off stub, a holographic foil window over the
 * prize photo, and the printed type laid over the canvas. Its foil, scratches and motion come from
 * its slot, so the engine can drive it with a finger or the autoPlay script alike.
 */
@Composable
private fun ScratchCard(
    slot: ScratchSlot,
    prize: ScratchPrize,
    photo: Bitmap?,
    revealed: Boolean,
    geo: ScratchGeometry,
    engine: ScratchEngine,
    reducedMotion: Boolean,
    paints: ScratchPaints,
    tick: () -> Long,
) {
    val w = geo.ticketW
    val h = geo.ticketH
    val shape = remember(w, h, geo.perfY) { ScratchArt.ticketShape(w, h, geo.perfY, RADIUS) }
    val strokes = remember(slot) { androidx.compose.ui.graphics.Path() }
    // The strokes are rebuilt only when the finger has added to them.
    val drawnVersion = remember(slot) { IntArray(1) { -1 } }
    Box(
        Modifier
            .offset(geo.ticketX.dp, geo.ticketY.dp)
            .size(w.dp, h.dp)
            .graphicsLayer {
                tick()
                val offset = slot.offset.value
                translationX = offset.dp.toPx()
                rotationZ = if (geo.width > 0f) offset / geo.width * 9f else 0f
                val s = 1f + slot.pop.value * 0.035f
                scaleX = s
                scaleY = s
                alpha = slot.shown.value.coerceIn(0f, 1f)
            },
    ) {
        Canvas(Modifier.fillMaxSize().bleed(BLEED.dp)) {
            tick()
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density, density)
                nc.translate(BLEED, BLEED)
                if (drawnVersion[0] != slot.version) {
                    slot.strokes(strokes)
                    drawnVersion[0] = slot.version
                }
                drawCard(nc, slot, photo, geo, shape, strokes, engine, reducedMotion, paints)
                nc.restore()
            }
        }

        Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("FIELD DAY LOTTERY", style = Caps)
            BasicText("Nº 07", Modifier.padding(end = 28.dp), style = Caps)
        }
        BasicText(
            "Lucky Bloom",
            Modifier.offset(21.dp, 38.dp),
            style = TextStyle(fontFamily = Condensed, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-0.4).sp, color = Ink),
        )
        BasicText(
            "Scratch the foil. Every ticket grows something.",
            Modifier.offset(22.dp, 86.dp).padding(end = 44.dp),
            style = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = Muted),
        )
        Column(Modifier.offset(24.dp, (geo.perfY + 26f).dp).width((w - 154f).dp)) {
            BasicText("YOUR PRIZE", style = Caps)
            Box(Modifier.padding(top = 5.dp).height(40.dp).fillMaxWidth()) {
                if (revealed) {
                    // The hidden line fades out in 120 ms while the prize springs up into place.
                    PrizeLines("Hidden under the foil", "Every ticket wins", Ink.copy(alpha = 0.28f)) {
                        tick()
                        val since = engine.elapsed - slot.revealedAt
                        1f - clamp(since / 0.12f, 0f, 1f) to 0f
                    }
                    PrizeLines(prize.prize, prize.detail, Ink) {
                        tick()
                        entrance(engine.elapsed - slot.revealedAt, reducedMotion)
                    }
                } else {
                    PrizeLines("Hidden under the foil", "Every ticket wins", Ink.copy(alpha = 0.28f)) { 1f to 0f }
                }
            }
        }
        BasicText(
            "07 · 0427 1186",
            Modifier.offset((w - 22f - BAR_W).dp, (geo.perfY + 30f + BAR_H + 6f).dp).width(BAR_W.dp),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, letterSpacing = 0.6.sp, color = Ink, textAlign = TextAlign.Center),
        )
    }
}

/**
 * Reanimated's FadeInDown sprung (stiffness 100, damping 14, mass 0.7): up 25 dp into place while
 * fading in; with reduced motion a 300 ms fade. Returns opacity and the distance still to rise.
 */
private fun entrance(t: Float, reducedMotion: Boolean): Pair<Float, Float> {
    if (reducedMotion) return inOutQuad(clamp(t / 0.3f, 0f, 1f)) to 0f
    val k = 100f
    val c = 14f
    val m = 0.7f
    val w0 = sqrt(k / m)
    val zeta = c / (2f * sqrt(k * m))
    val wd = w0 * sqrt(1f - zeta * zeta)
    // Share of the way still to go: 1 at the start, 0 at rest.
    val left = exp(-zeta * w0 * t) * (cos(wd * t) + zeta * w0 / wd * sin(wd * t))
    return clamp(1f - left, 0f, 1f) to 25f * left
}

/** The stub's prize line and its detail, faded and lifted by [motion] (opacity, dp below). */
@Composable
private fun PrizeLines(prize: String, detail: String, colour: Color, motion: () -> Pair<Float, Float>) {
    Column(
        Modifier.graphicsLayer {
            val (alpha, below) = motion()
            this.alpha = alpha
            translationY = below.dp.toPx()
        },
    ) {
        BasicText(prize, style = PrizeStyle.copy(color = colour), maxLines = 1, overflow = TextOverflow.Clip)
        BasicText(detail, style = DetailStyle, maxLines = 1)
    }
}

/** Paints and paths reused every frame. */
private class ScratchPaints {
    val pool = Paint(Paint.ANTI_ALIAS_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = android.graphics.Color.WHITE
    }
    val pieces = androidx.compose.ui.graphics.Path()
    // Grain tiles, one pixel to a dp: the baize in overlay, the paper multiplied in.
    val baize = Paint().apply {
        shader = BitmapShader(ScratchArt.grainTile(128, 0.33f, 0.34f, 5), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
    val paper = Paint().apply {
        shader = BitmapShader(ScratchArt.grainTile(128, 0.9f, 0.1f, 3), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    }
    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
    val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = android.graphics.Color.argb(56, 31, 42, 34)
        pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
    }
    val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BRUSH
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.BLACK
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        // A soft edge where the hardware canvas can blur a stroke.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(5f), BlurMaskFilter.Blur.NORMAL)
    }
    val rect = RectF()
    val barcode = ScratchArt.barcode(BAR_W, BAR_H, 11)
    val mark = ScratchArt.sunflower(11f)
    val emboss = ScratchArt.sunflower(24f)
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        typeface = heavy("sans-serif-condensed", 700)
    }

    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(ScratchArt.FOIL) else null
    val foilPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Sets [foilPaint] up for a foil `w` × `h` (dp) at this moment. */
    fun foil(w: Float, h: Float, time: Float, tiltX: Float, tiltY: Float, dissolve: Float) {
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("tilt", tiltX, tiltY)
            shader.setFloatUniform("dissolve", dissolve)
            foilPaint.shader = shader
            foilPaint.alpha = 255
        } else {
            // Below Android 13: brushed silver with soft rainbow bands that slide with the light,
            // fading out as it dissolves.
            val shift = (tiltX * 0.35f + tiltY * 0.25f + sin(time) * 0.1f) * w
            foilPaint.shader = LinearGradient(
                shift - w * 0.2f, 0f, shift + w * 1.2f, h,
                intArrayOf(0xFFC9CCD6.toInt(), 0xFFE7C9E4.toInt(), 0xFFC8E6E2.toInt(), 0xFFE9E4C4.toInt(), 0xFFC9CCD6.toInt()),
                null, Shader.TileMode.MIRROR,
            )
            foilPaint.alpha = ((1f - dissolve) * 255f).toInt().coerceIn(0, 255)
        }
    }
}

/** Card stock, print, the scratch window with its photo and foil, and the barcode. */
private fun drawCard(
    nc: android.graphics.Canvas,
    slot: ScratchSlot,
    photo: Bitmap?,
    geo: ScratchGeometry,
    shape: android.graphics.Path,
    strokes: androidx.compose.ui.graphics.Path,
    engine: ScratchEngine,
    reducedMotion: Boolean,
    paints: ScratchPaints,
) {
    val w = geo.ticketW
    val h = geo.ticketH
    // Card stock with a long soft shadow and a tight contact one.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        paints.card.setShadowLayer(sigmaToRadius(24f), 0f, 18f, android.graphics.Color.argb(107, 0, 0, 0))
        nc.drawPath(shape, paints.card)
        paints.card.setShadowLayer(sigmaToRadius(3f), 0f, 2f, android.graphics.Color.argb(64, 0, 0, 0))
        nc.drawPath(shape, paints.card)
        paints.card.clearShadowLayer()
    } else {
        nc.drawPath(shape, paints.card)
    }
    nc.save()
    nc.clipPath(shape)
    // Paper grain multiplied in, and a sheen from the top left.
    nc.drawRect(0f, 0f, w, h, paints.paper)
    paints.fill.color = android.graphics.Color.WHITE
    paints.fill.shader = RadialGradient(
        w * 0.3f, 0f, h,
        intArrayOf(android.graphics.Color.argb(89, 255, 255, 255), android.graphics.Color.argb(0, 255, 255, 255), android.graphics.Color.argb(26, 120, 96, 50)),
        floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
    )
    nc.drawRect(0f, 0f, w, h, paints.fill)
    paints.fill.shader = null
    nc.restore()

    // A printed frame round the face and round the stub.
    paints.rect.set(10f, 10f, w - 10f, geo.perfY - 16f)
    nc.drawRoundRect(paints.rect, 12f, 12f, paints.dashed)
    paints.rect.set(10f, geo.perfY + 16f, w - 10f, h - 10f)
    nc.drawRoundRect(paints.rect, 10f, 10f, paints.dashed)

    // The sunflower mark in the header.
    nc.save()
    nc.translate(w - 34f, 27f)
    paints.fill.color = 0xFFEEA41C.toInt()
    nc.drawPath(paints.mark.petals, paints.fill)
    paints.fill.color = 0xFF5A3A1C.toInt()
    nc.drawPath(paints.mark.head, paints.fill)
    nc.restore()

    drawWindow(nc, slot, photo, geo, strokes, engine, reducedMotion, paints)

    nc.save()
    nc.translate(w - 22f - BAR_W, geo.perfY + 30f)
    paints.fill.color = 0xFF1F2A22.toInt()
    nc.drawPath(paints.barcode, paints.fill)
    nc.restore()
}

/** The scratch window: the prize photo, and the foil over it with the scratches cut out. */
private fun drawWindow(
    nc: android.graphics.Canvas,
    slot: ScratchSlot,
    photo: Bitmap?,
    geo: ScratchGeometry,
    strokes: androidx.compose.ui.graphics.Path,
    engine: ScratchEngine,
    reducedMotion: Boolean,
    paints: ScratchPaints,
) {
    val sw = geo.scratchW
    val sh = geo.scratchH
    nc.save()
    nc.translate(geo.scratchX, geo.scratchY)
    nc.save()
    val window = android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, sw, sh), SCRATCH_RADIUS, SCRATCH_RADIUS, android.graphics.Path.Direction.CW) }
    nc.clipPath(window)
    paints.fill.color = 0xFF3B4A2C.toInt()
    nc.drawRect(0f, 0f, sw, sh, paints.fill)
    val dissolve = slot.dissolve.value
    if (photo != null) {
        // Cover the window, zoomed a little until the foil has gone.
        val zoom = 1f + if (reducedMotion) 0f else PHOTO_ZOOM * (1f - dissolve)
        val fill = max(sw / photo.width, sh / photo.height) * zoom
        val pw = photo.width * fill
        val ph = photo.height * fill
        paints.rect.set((sw - pw) / 2f, (sh - ph) / 2f, (sw + pw) / 2f, (sh + ph) / 2f)
        nc.drawBitmap(photo, null, paints.rect, paints.image)
    }
    paints.fill.color = android.graphics.Color.WHITE
    paints.fill.shader = RadialGradient(
        sw / 2f, sh / 2f, sw * 0.75f,
        intArrayOf(android.graphics.Color.argb(0, 0, 0, 0), android.graphics.Color.argb(82, 20, 14, 4)), null, Shader.TileMode.CLAMP,
    )
    nc.drawRect(0f, 0f, sw, sh, paints.fill)
    paints.fill.shader = null

    if (dissolve < 1f) {
        // The foil lives in its own layer, so the finger's strokes erase it and nothing under it.
        nc.saveLayer(0f, 0f, sw, sh, null)
        paints.foil(sw, sh, engine.phase, engine.tiltX, engine.tiltY, dissolve)
        nc.drawRect(0f, 0f, sw, sh, paints.foilPaint)
        val emboss = max(0f, 1f - dissolve * 3f)
        if (emboss > 0f) drawEmboss(nc, sw, sh, emboss, paints)
        // The finger rubs the foil away with a soft edge.
        nc.drawPath(strokes.asAndroidPath(), paints.eraser)
        nc.restore()
    }
    nc.restore()
    paints.ring.color = android.graphics.Color.argb(71, 31, 42, 34)
    paints.ring.strokeWidth = 1f
    paints.rect.set(0.5f, 0.5f, sw - 0.5f, sh - 0.5f)
    nc.drawRoundRect(paints.rect, SCRATCH_RADIUS, SCRATCH_RADIUS, paints.ring)
    paints.ring.color = android.graphics.Color.WHITE
    paints.ring.strokeWidth = 1.5f
    nc.restore()
}

/** Pressed into the foil: a sunflower and the instruction, light catching their upper edge. */
private fun drawEmboss(nc: android.graphics.Canvas, sw: Float, sh: Float, opacity: Float, paints: ScratchPaints) {
    fun colour(a: Float, r: Int, g: Int, b: Int) = android.graphics.Color.argb((a * opacity * 255f).toInt(), r, g, b)
    nc.save()
    nc.translate(sw / 2f - 1f, sh / 2f - 17f)
    paints.fill.color = colour(0.45f, 255, 255, 255)
    nc.drawPath(paints.emboss.petals, paints.fill)
    nc.restore()
    nc.save()
    nc.translate(sw / 2f, sh / 2f - 16f)
    paints.fill.color = colour(0.42f, 58, 62, 74)
    nc.drawPath(paints.emboss.petals, paints.fill)
    paints.fill.color = colour(0.5f, 58, 62, 74)
    nc.drawPath(paints.emboss.head, paints.fill)
    paints.fill.color = colour(0.35f, 255, 255, 255)
    nc.drawPath(paints.emboss.seeds, paints.fill)
    nc.restore()
    val label = "SCRATCH  TO  REVEAL"
    val width = paints.label.measureText(label)
    paints.label.color = colour(0.55f, 255, 255, 255)
    nc.drawText(label, (sw - width) / 2f - 0.8f, sh / 2f + 37f, paints.label)
    paints.label.color = colour(0.7f, 48, 52, 64)
    nc.drawText(label, (sw - width) / 2f, sh / 2f + 38f, paints.label)
}
