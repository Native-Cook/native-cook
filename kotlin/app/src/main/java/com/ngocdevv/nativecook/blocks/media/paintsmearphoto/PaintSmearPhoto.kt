package com.ngocdevv.nativecook.blocks.media.paintsmearphoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Paint Smear Photo — "Atelier", a photo editor's oil mode: the finger drags the photo like wet
 paint. Port of PaintSmearPhoto.tsx. One AGSL shader displaces the photo by a drag field of up to
 24 trail points, pulls colour along the stroke, scores bristle lines and lights the moved paint's
 ridges. Under the card: brush size chips (S/M/L), a Wet–Dry slider and Reset.

 autoPlay (LOOP = 9 s): 0–1.4 rest (the clean photo), three curved strokes 1.4–2.4, 2.6–3.6 and
 3.8–4.8, 4.8–6.2 hold, 6.2–8.2 the paint flows back, 8.2–9 rest.

 Touch: drag on the photo to smear it (a point every 16 dp); double-tap or Reset: the paint flows
 back (0.85 s). 2 s after the last touch it flows back the same way and the loop restarts at 0.
 Reduced motion: no loop, the clean photo; smearing still works.
 */

internal val PaintSmearBackground = Color(0xFF1A1613)
private val Ink = Color(0xFFF3ECE2)
private val Mute = Ink.copy(alpha = 0.6f)
private val Faint = Ink.copy(alpha = 0.36f)
private val Accent = Color(0xFFE9A24B)
private val Cream = Color(0xFFF1E7D8)
private val Umber = Color(0xFF2B2118)
private const val SLIDER_W = 168f
private const val THUMB = 26f
private const val TOOLS_H = 96f
private const val CARD_RADIUS = 22f
private const val PHOTO_ASSET = "paintsmearphoto/paintsmearphoto_still-life.jpg"
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseSpin = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

private class PaintSmearHolder(auto: Boolean) {
    val s = PaintSmearState(auto)
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    val dry = mutableFloatStateOf(0.35f)
}

private fun loadPhoto(context: Context): Bitmap? = runCatching {
    context.assets.open(PHOTO_ASSET).use { BitmapFactory.decodeStream(it) }
}.getOrNull()

@Composable
fun PaintSmearPhoto(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val holder = remember(auto) { PaintSmearHolder(auto) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(PAINT_SMEAR_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val photo by produceState<Bitmap?>(null) { value = withContext(Dispatchers.IO) { loadPhoto(context) } }
    var brushIndex by remember { mutableIntStateOf(1) }
    val sigma = remember { Animatable(PaintBrushes[1]) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(brushIndex) { sigma.animateTo(PaintBrushes[brushIndex], tween(180, easing = EaseOut)) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PaintSmearBackground)
            .background(Brush.verticalGradient(0f to Color(0xFF231D18), 0.5f to Color(0xFF1A1613), 1f to Color(0xFF120F0D))),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cardW = max(0f, width - 32)
        val cardTop = safeTop + 68
        val hintBottom = safeBottom + 12
        val toolsBottom = hintBottom + 18 + 16
        val cardH = max(0f, (height - cardTop - toolsBottom - TOOLS_H - 20).roundToInt().toFloat())

        // The photo as a cover-fit shader in card dp, shared by the AGSL pass and the fallback.
        val photoShader = remember(photo, cardW, cardH) {
            val bmp = photo ?: return@remember null
            val k = max(cardW / bmp.width, cardH / bmp.height)
            BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    Matrix().apply {
                        setScale(k, k)
                        postTranslate((cardW - bmp.width * k) / 2, (cardH - bmp.height * k) / 2)
                    },
                )
            }
        }
        remember(shader, photoShader) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && photoShader != null) {
                shader.setInputShader("photo", photoShader)
            }
            Unit
        }

        LaunchedEffect(holder, cardW, cardH, holder.wake.intValue) {
            if (cardH <= 0) return@LaunchedEffect
            val s = holder.s
            var last = -1L
            while (true) {
                val idle = withFrameNanos { now ->
                    val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 1.0 / 20)
                    last = now
                    s.now += dt
                    if (s.mode == PAINT_AUTO) {
                        if (auto) {
                            s.t = (s.t + dt) % LOOP
                            s.scriptFrame(s.t, cardW.toDouble(), cardH.toDouble())
                        }
                    } else {
                        if (s.resetFrame() && s.resume) {
                            s.resume = false
                            s.mode = PAINT_AUTO
                            s.t = 0.0
                        }
                        if (auto && !s.painting && s.resetAt < 0 && s.now - s.lastTouch > PAINT_IDLE_BEFORE_BACK) {
                            if (s.count > 0) {
                                s.startReset(true)
                            } else {
                                s.mode = PAINT_AUTO
                                s.t = 0.0
                            }
                        }
                    }
                    holder.tick.intValue += 1
                    // Idle: a finger-driven photo that is neither painting nor flowing back.
                    s.mode == PAINT_LIVE && !auto && !s.painting && s.resetAt < 0
                }
                if (idle) break
            }
        }

        fun touched() {
            val s = holder.s
            s.lastTouch = s.now
            if (s.mode != PAINT_LIVE) s.takeOver()
            holder.wake.intValue += 1
        }

        fun reset() {
            touched()
            holder.s.startReset(false)
            scope.launch { spin.animateTo(spin.targetValue - 360f, tween(700, easing = EaseSpin)) }
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Top bar: title, mode tag, Export.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = (safeTop + 12).dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("Atelier", style = TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp))
                Box(
                    Modifier.height(26.dp).background(Color(0x29E9A24B), RoundedCornerShape(13.dp)).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Oil mode", style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp))
                }
            }
            Box(
                Modifier
                    .height(36.dp)
                    .shadow(1.dp, RoundedCornerShape(18.dp))
                    .background(Cream, RoundedCornerShape(18.dp))
                    .drawBehind {
                        val r = 18.dp.toPx()
                        drawLine(Color.White.copy(alpha = 0.7f), Offset(r, 0.5.dp.toPx()), Offset(size.width - r, 0.5.dp.toPx()), 1.dp.toPx())
                    }
                    .semantics { contentDescription = "Export" }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Export", style = TextStyle(color = Umber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }

        if (cardH > 0) {
            Box(
                Modifier
                    .offset(x = 16.dp, y = cardTop.dp)
                    .size(cardW.dp, cardH.dp)
                    .shadow(22.dp, RoundedCornerShape(CARD_RADIUS.dp), ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.5f))
                    .background(Color(0xFF0F0D0C), RoundedCornerShape(CARD_RADIUS.dp))
                    .semantics { contentDescription = "Still life photo. Drag to smear it like wet oil paint; double-tap to reset." }
                    .pointerInput(holder) { detectTapGestures(onDoubleTap = { reset() }) }
                    .pointerInput(holder) {
                        val slop = 4.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d = screen.density
                            val s = holder.s
                            touched()
                            s.lx = (down.position.x / d).toDouble()
                            s.ly = (down.position.y / d).toDouble()
                            var started = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                s.lastTouch = s.now
                                if (!started && (change.position - down.position).getDistance() >= slop) {
                                    started = true
                                    s.painting = true
                                    // A stroke stops a flow-back that was under way where it is.
                                    if (s.resetAt >= 0) s.takeOver()
                                    holder.wake.intValue += 1
                                }
                                if (started && change.positionChange() != Offset.Zero) {
                                    change.consume()
                                    val x = (change.position.x / d).toDouble()
                                    val y = (change.position.y / d).toDouble()
                                    if (hypot(x - s.lx, y - s.ly) >= PAINT_STEP) s.lay(x, y)
                                }
                            }
                            s.painting = false
                            s.lastTouch = s.now
                        }
                    },
            ) {
                val image = photoShader
                if (image != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        holder.tick.intValue
                        val s = holder.s
                        val d = density
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                            val trail = FloatArray(PAINT_TRAIL * 4)
                            for (i in 0 until s.count * 4) trail[i] = s.trail[i].toFloat()
                            shader.setFloatUniform("size", max(cardW, 1f), max(cardH, 1f))
                            shader.setFloatUniform("trail", trail)
                            shader.setFloatUniform("sigma", sigma.value)
                            shader.setFloatUniform("dry", holder.dry.floatValue)
                            shader.setFloatUniform("flow", s.flow.toFloat())
                            shader.setFloatUniform("radius", CARD_RADIUS)
                            scale(d, d, pivot = Offset.Zero) { drawRect(brush, Offset.Zero, Size(cardW, cardH)) }
                        } else {
                            paintSmearFallback(s, image, cardW, cardH, sigma.value, holder.dry.floatValue)
                        }
                    }
                }
            }

            // Tools: brush chips and Reset, then the Wet–Dry slider.
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = toolsBottom.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("Brush", Modifier.width(44.dp), style = TextStyle(color = Faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("S", "M", "L").forEachIndexed { i, label ->
                            val on = brushIndex == i
                            Row(
                                Modifier
                                    .height(36.dp)
                                    .defaultMinSize(minWidth = 52.dp)
                                    .background(if (on) Cream else Ink.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                                    .border(0.5.dp, if (on) Cream else Ink.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                    .semantics {
                                        contentDescription = "Brush size $label"
                                        selected = on
                                    }
                                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                                        brushIndex = i
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            ) {
                                Box(Modifier.size((6 + i * 4).dp).background(if (on) Umber else Mute, CircleShape))
                                BasicText(label, style = TextStyle(color = if (on) Umber else Mute, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                    Row(
                        Modifier
                            .height(36.dp)
                            .background(Ink.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                            .border(0.5.dp, Ink.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                            .semantics { contentDescription = "Reset the photo" }
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { reset() }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Canvas(Modifier.size(16.dp).graphicsLayer { rotationZ = spin.value }) {
                            val k = size.width / 16f
                            scale(k, k, pivot = Offset.Zero) {
                                val arc = Path().apply {
                                    // "M13 8 A5 5 0 1 1 11.2 4.2": most of a circle around (8, 8), open at the top right.
                                    arcTo(androidx.compose.ui.geometry.Rect(Offset(8f, 8f), 5f), 0f, 310f, true)
                                }
                                val head = Path().apply { moveTo(11.6f, 1.6f); lineTo(11.4f, 4.6f); lineTo(8.4f, 4.3f) }
                                val st = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                drawPath(arc, Ink, style = st)
                                drawPath(head, Ink, style = st)
                            }
                        }
                        BasicText("Reset", style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("Paint", Modifier.width(44.dp), style = TextStyle(color = Faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BasicText("Wet", style = TextStyle(color = Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        PaintSmearSlider(holder) { touched() }
                        BasicText("Dry", style = TextStyle(color = Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }

        BasicText(
            "Drag to smear · double-tap to reset",
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = hintBottom.dp),
            style = TextStyle(color = Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

/** The Wet–Dry slider: a 4 dp track, an amber fill to the thumb, a cream thumb with two shadows. */
@Composable
private fun PaintSmearSlider(holder: PaintSmearHolder, touched: () -> Unit) {
    Box(
        Modifier
            .width(SLIDER_W.dp)
            .height(40.dp)
            .semantics { contentDescription = "Paint wetness" }
            .pointerInput(holder) {
                fun set(x: Float) {
                    holder.dry.floatValue = ((x / density - THUMB / 2) / (SLIDER_W - THUMB)).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touched()
                    set(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        touched()
                        set(change.position.x)
                    }
                }
            }
            .drawBehind {
                val d = density
                val cy = size.height / 2
                val dry = holder.dry.floatValue
                val trackH = 4 * d
                drawRoundRect(Ink.copy(alpha = 0.14f), Offset(0f, cy - trackH / 2), Size(size.width, trackH), CornerRadius(2 * d))
                drawRoundRect(Accent, Offset(0f, cy - trackH / 2), Size(size.width * dry, trackH), CornerRadius(2 * d))
                val r = THUMB / 2 * d
                val tx = dry * (SLIDER_W - THUMB) * d + r
                drawCircle(Color.Black.copy(alpha = 0.18f), r + 3 * d, Offset(tx, cy + 6 * d))
                drawCircle(Color.Black.copy(alpha = 0.3f), r + 0.5f * d, Offset(tx, cy + 1 * d))
                drawCircle(Color(0xFFF6EEE2), r, Offset(tx, cy))
                drawCircle(Color.White.copy(alpha = 0.9f), r - 1.5f * d, Offset(tx, cy - 1 * d), style = Stroke(0.8f * d))
            },
    )
}

/**
 * Below Android 13 (no AGSL): the photo, then each trail point as a soft disc of the photo
 * shifted downstream by its drag (scaled by `flow`), so strokes still pull the picture along.
 * No bristles or impasto lighting. Drawn in px; trail positions are card dp.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.paintSmearFallback(
    s: PaintSmearState,
    photo: BitmapShader,
    cardW: Float,
    cardH: Float,
    sigma: Float,
    dry: Float,
) {
    val d = density
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        val save = c.save()
        c.scale(d, d)
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = photo }
        c.drawRoundRect(0f, 0f, cardW, cardH, CARD_RADIUS, CARD_RADIUS, base)
        val clip = android.graphics.Path().apply { addRoundRect(0f, 0f, cardW, cardH, CARD_RADIUS, CARD_RADIUS, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        val dab = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = photo
            maskFilter = BlurMaskFilter(sigma * 0.6f * d, BlurMaskFilter.Blur.NORMAL)
        }
        val k = (1f - 0.28f * dry) * s.flow.toFloat()
        for (i in 0 until s.count) {
            val x = s.trail[i * 4].toFloat()
            val y = s.trail[i * 4 + 1].toFloat()
            val ax = s.trail[i * 4 + 2].toFloat() * k
            val ay = s.trail[i * 4 + 3].toFloat() * k
            if (kotlin.math.abs(ax) + kotlin.math.abs(ay) < 0.5f) continue
            c.save()
            // Shift the canvas by the drag: the disc at (x, y) then shows the photo from (x, y) − drag.
            c.translate(ax, ay)
            c.drawCircle(x - ax, y - ay, sigma * 1.1f, dab)
            c.restore()
        }
        c.restoreToCount(save)
    }
}
