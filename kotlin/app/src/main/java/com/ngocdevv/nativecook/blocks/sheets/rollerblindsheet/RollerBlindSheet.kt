package com.ngocdevv.nativecook.blocks.sheets.rollerblindsheet

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Roller Blind Sheet — a smart-home room ("Nook — Bedroom") whose bottom sheet is a linen roller
 blind. Pull the bead on its cord down: the blind unrolls from the roller (the roll gets thinner),
 the morning light through the window dims the room and glows through the weave, and the scene
 presets printed on the fabric come down with it. Let go and the hem springs to the nearest detent
 (rolled up, the window's sill, or covering the room's controls) with a bounce, carrying the
 release velocity; a short tug from a detent lets the spring roller wind it back up. The cord
 swings as a pendulum driven by the hem's acceleration.

 The room is one AGSL pass under the controls, the blind a second Canvas over them: an opaque
 rectangle painted with the fabric shader from the roller to the hem, the roll, hem bar and
 brackets as gradient shapes, blurred shadows, the cord and the bead (drawn stand-ins below
 Android 13, RollerBlindFallback.kt). The printed scenes are Compose text in a box whose clip
 follows the hem in a draw lambda (no relayout per frame). Two seconds after the last touch the
 blind eases back to the sill (0.9 s) and the loop restarts from t = 0.
 Reduced motion: the blind sits at the sill, still; dragging still works, with no bounce.
 */

internal val RollerBlindBackground = Color(0xFFE6DED2)
private val Ink = Color(0xFF2B2622)
private val Mute = Color(0x992B2622)
private val Print = Color(0xFF4A3626)
private val PrintMute = Color(0xFF6E5844)
private val Accent = Color(0xFFC9793A)
private const val IDLE = 2.0
private const val RETURN = 0.9
private const val MODE_AUTO = 0
private const val MODE_HAND = 1
private const val MODE_RETURN = 2
private const val K_HEM = 158.0
private const val C_HEM = 13.8
private const val W_CORD = PI * 2 * 1.4
private const val CORD_LEN = 64.0
private const val CONTENT_H = 292f

private class BlindScene(val title: String, val detail: String, val glyph: RollerBlindGlyph)

private val Scenes = listOf(
    BlindScene("Wind down", "Lamps at 20 %, 2700 K", RollerBlindGlyph.Moon),
    BlindScene("Reading", "Bedside lamp, warm white", RollerBlindGlyph.Book),
    BlindScene("Sleep", "Everything off, blind closed", RollerBlindGlyph.Sleep),
    BlindScene("Wake gently", "Sunrise light from 06:45", RollerBlindGlyph.Sunrise),
)

private class BlindState {
    var now = -1.0
    var mode = MODE_AUTO
    var autoStart = 0.0
    var lastTouch = 0.0
    var touching = false
    // hem [y, velocity, target], cord [angle (rad), angular velocity], drag [start hem, start at a detent]
    var hemY = 0.0
    var hemV = 0.0
    var hemTarget = 0.0
    var cordA = 0.0
    var cordV = 0.0
    var dragStart = 0.0
    var dragDetent = false
    var landed = true
    var returnHem = 0.0
    var returnCord = 0.0
    var returnStart = 0.0
    var landedNow = false
    var lastSide = -1

    // the frame on screen: hem y, cord angle (degrees), finger alpha
    var hem = 0.0
    var cordDeg = 0.0
    var finger = 0.0
    var ready = false
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)

    fun settled() = mode == MODE_HAND && !touching && abs(hemV) < 0.5 && abs(hemY - hemTarget) < 0.3 && abs(cordA) < 0.002 && abs(cordV) < 0.01
}

private fun BlindState.step(g: BlindGeo, now: Double, auto: Boolean, autoPlay: Boolean, reduced: Boolean) {
    val prev = this.now
    val dt = if (prev < 0) 0.0 else min(0.033, now - prev)
    if (prev < 0) autoStart = now
    this.now = now
    landedNow = false
    if (mode == MODE_HAND) {
        if (!touching) {
            // the hem springs to its detent carrying the release velocity
            val before = hemY - hemTarget
            val acc = -K_HEM * (hemY - hemTarget) - (if (reduced) 2 * sqrt(K_HEM) else C_HEM) * hemV
            hemV += acc * dt
            hemY += hemV * dt
            if (hemY < g.up - 18 * g.unit) {
                hemY = g.up - 18 * g.unit
                hemV = 0.0
            }
            if (!landed && before * (hemY - hemTarget) <= 0) {
                landed = true
                landedNow = true
            }
            // the cord swings on (a lightly damped pendulum), kicked when the hem stops hard
            cordV += (-W_CORD * W_CORD * cordA - 1.2 * cordV) * dt
            cordV += max(-3000.0, min(3000.0, acc)) / (CORD_LEN * g.unit) * dt * 0.02
            cordA += cordV * dt
            if (now - lastTouch > IDLE && abs(hemV) < 5 && autoPlay) {
                mode = MODE_RETURN
                returnStart = now
                returnHem = hemY
                returnCord = cordA
            }
        } else {
            cordA *= exp(-dt * 12)
            cordV = 0.0
        }
    } else if (mode == MODE_RETURN && now - returnStart >= RETURN) {
        mode = MODE_AUTO
        autoStart = now
    }
    when {
        mode == MODE_HAND -> {
            hem = hemY
            cordDeg = cordA * 180 / PI
            finger = if (touching) 1.0 else 0.0
        }
        mode == MODE_RETURN -> {
            val k = blindEaseInOut(min(1.0, (now - returnStart) / RETURN))
            hem = returnHem + (g.half - returnHem) * k
            cordDeg = returnCord * 180 / PI * (1 - k)
            finger = 0.0
        }
        !auto -> {
            hem = g.half
            cordDeg = 0.0
            finger = 0.0
        }
        else -> {
            val t = ((now - autoStart) % LOOP + LOOP) % LOOP
            hem = blindHemAt(g, t)
            cordDeg = blindCordAt(t)
            finger = blindFingerAt(t)
        }
    }
    ready = true
}

private fun blindBlurPaint(color: Color, sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

/**
 * A bedroom whose bottom sheet is a linen roller blind. Pull the bead down to lower it; a short tug
 * rolls it up.
 *
 * @param autoPlay pulls the blind down over the controls, tugs it up and lowers it to the sill, on a 9 s loop.
 */
@Composable
fun RollerBlindSheet(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember(reduced) { BlindState() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val shaders = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(BLIND_ROOM_AGSL) to RuntimeShader(BLIND_FABRIC_AGSL) }.getOrNull()
        } else {
            null
        }
    }
    val roomBrush = remember(shaders) { shaders?.let { ShaderBrush(it.first) } }
    val fabricBrush = remember(shaders) { shaders?.let { ShaderBrush(it.second) } }
    val fallback = remember { RollerBlindFallback() }

    BoxWithConstraints(modifier.fillMaxSize().background(RollerBlindBackground)) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val g = remember(w, h, safeTop, safeBottom) { BlindGeo(w, h, safeTop.toDouble(), safeBottom.toDouble()) }
        val u = g.unit.toFloat()
        val shadowPaint = remember(u) { blindBlurPaint(Color(0x38281C12), 6 * u) }
        val hemShadowPaint = remember(u) { blindBlurPaint(Color(0x40281C12), 5 * u) }

        LaunchedEffect(state, g, auto) {
            while (true) {
                withFrameNanos { n ->
                    state.step(g, n / 1e9, auto, autoPlay, reduced)
                    if (state.landedNow) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    if (state.mode == MODE_HAND && state.touching) {
                        val side = if (state.hem > g.half) 1 else 0
                        if (state.lastSide >= 0 && side != state.lastSide) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        state.lastSide = side
                    } else {
                        state.lastSide = -1
                    }
                    state.tick.intValue += 1
                }
                // Still at rest (no autoPlay, reduced motion, or a settled blind): wait for a finger.
                if ((state.mode == MODE_AUTO && !auto) || (!autoPlay && state.settled())) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                }
            }
        }

        // the room: wall, window, sunlight
        Canvas(Modifier.fillMaxSize()) {
            state.tick.intValue
            if (!state.ready) return@Canvas
            val d = density
            val dim = (blindCoverOf(g, state.hem) * 0.5).toFloat()
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shaders != null && roomBrush != null) {
                    val s = shaders.first
                    s.setFloatUniform("size", w.toFloat(), h.toFloat())
                    s.setFloatUniform("win", g.winX.toFloat(), g.winY.toFloat(), g.winW.toFloat(), g.winH.toFloat())
                    s.setFloatUniform("dim", dim)
                    s.setFloatUniform("unit", u)
                    drawRect(roomBrush, Offset.Zero, Size(w.toFloat(), h.toFloat()))
                } else {
                    with(fallback) { drawRoom(g, dim) }
                }
            }
        }

        val hair = (1f / screen.density).dp
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 14).dp),
        ) {
            BasicText("NOOK · BEDROOM", style = TextStyle(color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            BasicText(
                "Bedroom",
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
            )
            val meta = TextStyle(color = Mute, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("21.5 °C · 3 lights on · Blind ", style = meta, maxLines = 1)
                RollerBlindRollNumber(
                    value = {
                        state.tick.intValue
                        max(0.0, min(100.0, (state.hem - g.up) / (g.full - g.up) * 100)).toFloat()
                    },
                    digits = 3,
                    line = 20f,
                    style = meta,
                    suffix = "% closed",
                    still = reduced,
                )
            }
        }

        // the room's controls, dimmed as the blind closes
        Column(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = g.controlsY.dp, bottom = (safeBottom + 16).dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.weight(1f).heightIn(max = 112.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BlindControl("Ceiling light", "60 %", RollerBlindGlyph.Bulb, hair)
                BlindControl("Bedside lamp", "Warm · 30 %", RollerBlindGlyph.Lamp, hair)
            }
            Row(Modifier.weight(1f).heightIn(max = 112.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BlindControl("Heating", "21.5 °C", RollerBlindGlyph.Thermo, hair)
                BlindControl("Air purifier", "Auto · quiet", RollerBlindGlyph.Fan, hair)
            }
        }
        Box(
            Modifier
                .padding(top = (g.controlsY - 10 * g.unit).dp)
                .fillMaxSize()
                .graphicsLayer {
                    state.tick.intValue
                    alpha = (blindCoverOf(g, state.hem) * 0.22).toFloat()
                }
                .background(Color(0xFF2A221C)),
        )

        // the blind
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Roller blind. Pull the bead down to lower it; a short tug rolls it up." }
                .pointerInput(state, g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val y0 = (down.position.y / d).toDouble()
                        val near = state.ready && (abs(y0 - state.hem) < 60 * g.unit || abs(y0 - (state.hem + CORD_LEN * g.unit)) < 50 * g.unit)
                        if (!near) return@awaitEachGesture
                        val atDetent = abs(state.hem - g.half) < 6 || abs(state.hem - g.full) < 6
                        state.mode = MODE_HAND
                        state.touching = true
                        state.lastTouch = state.now
                        state.hemY = state.hem
                        state.hemV = 0.0
                        state.hemTarget = state.hem
                        state.cordA = state.cordDeg * PI / 180
                        state.cordV = 0.0
                        state.dragStart = state.hem
                        state.dragDetent = atDetent
                        state.wake.intValue += 1
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var ty = 0.0
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                tracker.addPosition(c.uptimeMillis, c.position)
                                ty = ((c.position.y - down.position.y) / d).toDouble()
                                state.hemY = blindRubberHem(g, state.dragStart + ty)
                                state.lastTouch = state.now
                            }
                        } finally {
                            val vy = (tracker.calculateVelocity().y / d).toDouble()
                            // a short tug from a detent lets the spring roller wind the blind up
                            val tug = state.dragDetent && ty > 4 && ty < 44 * g.unit && vy < 900
                            state.hemTarget = if (tug) g.up else blindDetentFor(g, state.hemY, vy)
                            state.hemV = vy
                            state.landed = false
                            state.touching = false
                            state.lastTouch = state.now
                            // the bead swings as the hand lets go
                            state.cordA = 0.0
                            state.cordV = -max(-6.0, min(6.0, vy / 400))
                        }
                    }
                },
        ) {
            state.tick.intValue
            if (!state.ready) return@Canvas
            val d = density
            val hem = state.hem.toFloat()
            val x0 = g.x0.toFloat()
            val x1 = g.x1.toFloat()
            val ry = g.rollerY.toFloat()
            val fabricH = max(0f, hem - ry)
            val rollR = blindRollRadius(g, state.hem).toFloat()
            scale(d, d, pivot = Offset.Zero) {
                drawIntoCanvas { cv ->
                    cv.nativeCanvas.drawRect(x0 - 3 * u, ry, x1 + 3 * u, ry + fabricH + 10 * u, shadowPaint)
                }
                // a plain linen backing under the shaded fabric, so the blind can never be see-through
                drawRect(Color(0xFFD9CDB8), Offset(x0, ry), Size(x1 - x0, fabricH))
                if (fabricH > 0f) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shaders != null && fabricBrush != null) {
                        val s = shaders.second
                        s.setFloatUniform("win", g.winX.toFloat(), g.winY.toFloat(), g.winW.toFloat(), g.winH.toFloat())
                        s.setFloatUniform("roll", x0, x1, ry, rollR)
                        s.setFloatUniform("hem", hem, 0f, (1 - blindCoverOf(g, state.hem) * 0.3).toFloat(), u)
                        drawRect(fabricBrush, Offset(x0, ry), Size(x1 - x0, fabricH))
                    } else {
                        with(fallback) { drawFabric(g, hem, rollR) }
                    }
                }
                drawIntoCanvas { cv ->
                    cv.nativeCanvas.drawRect(x0, hem + 6 * u, x1, hem + 16 * u, hemShadowPaint)
                }
                val barY = hem - 2 * u
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFFC49A6C), Color(0xFF93694A), Color(0xFF5E4029)), startY = barY, endY = barY + 11 * u),
                    Offset(x0 - 4 * u, barY),
                    Size(x1 - x0 + 8 * u, 11 * u),
                    CornerRadius(3 * u),
                )
                val rollY = ry - rollR
                drawRoundRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF9E9482),
                        0.32f to Color(0xFFEDE4D3),
                        0.7f to Color(0xFFD8CDB9),
                        1f to Color(0xFF8A806F),
                        startY = rollY,
                        endY = rollY + rollR * 2,
                    ),
                    Offset(x0 - 4 * u, rollY),
                    Size(x1 - x0 + 8 * u, rollR * 2),
                    CornerRadius(rollR),
                )
                val bracket = Brush.verticalGradient(listOf(Color(0xFFF1EFEC), Color(0xFFB9B5AF), Color(0xFF8E8A84)), startY = ry - 16 * u, endY = ry + 16 * u)
                drawRoundRect(bracket, Offset(x0 - 15 * u, ry - 16 * u), Size(13 * u, 32 * u), CornerRadius(3 * u))
                drawRoundRect(bracket, Offset(x1 + 2 * u, ry - 16 * u), Size(13 * u, 32 * u), CornerRadius(3 * u))

                val a = state.cordDeg * PI / 180
                val len = CORD_LEN * g.unit
                val topX = (g.w / 2).toFloat()
                val topY = hem + 8 * u
                val bx = (g.w / 2 + sin(a) * len).toFloat()
                val by = (state.hem + 8 * g.unit + cos(a) * len).toFloat()
                drawLine(Color(0xFF8C7B66), Offset(topX, topY), Offset(bx, by), 1.6f * u, StrokeCap.Round)
                val bc = Offset(bx, by + 9)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFD8A877), Color(0xFF9C6A41), Color(0xFF5E3C22)), Offset(bx - 3, by + 6), 12 * u),
                    9 * u,
                    bc,
                )
                val fa = (state.finger * 0.9).toFloat()
                if (fa > 0.001f) {
                    drawCircle(Color(0xD9FFFAF0), 20 * u, bc, alpha = fa, style = Stroke(width = 2f))
                    drawCircle(Color(0x2EFFFAF0), 20 * u, bc, alpha = fa)
                }
            }
        }

        // the scenes printed on the fabric, clipped from under the roll down to the hem
        val clipTop = (g.rollerY + 14 * g.unit).toFloat()
        Box(
            Modifier
                .offset(x = g.x0.dp, y = clipTop.dp)
                .width((g.x1 - g.x0).dp)
                .fillMaxHeight()
                .drawWithContent {
                    state.tick.intValue
                    val clipH = max(0f, (state.hem.toFloat() - clipTop) * density)
                    if (clipH > 0f) clipRect(0f, 0f, size.width, clipH) { this@drawWithContent.drawContent() }
                },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(CONTENT_H.dp)
                    .graphicsLayer {
                        state.tick.intValue
                        translationY = (state.hem.toFloat() - clipTop - 12 * u - CONTENT_H) * density
                    }
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                BasicText("SCENES", style = TextStyle(color = PrintMute, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
                BasicText(
                    "Set the mood",
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    style = TextStyle(color = Print, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
                )
                for (scene in Scenes) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .drawBehind { drawLine(Color(0x474E3C2C), Offset.Zero, Offset(size.width, 0f), 1f) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .border(1.2.dp, Color(0x664E3C2C), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            RollerBlindIcon(scene.glyph, Print, 20f)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(scene.title, style = TextStyle(color = Print, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                            BasicText(scene.detail, modifier = Modifier.padding(top = 1.dp), style = TextStyle(color = PrintMute, fontSize = 13.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BlindControl(label: String, value: String, glyph: RollerBlindGlyph, hair: Dp) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .cssBoxShadow(Color(0x1F3C2C1E), 1.dp, 2.dp, 20.dp)
            .cssBoxShadow(Color(0x1A3C2C1E), 10.dp, 24.dp, 20.dp)
            .background(Color(0xDBFCF9F4), shape)
            .border(hair, Color(0x1A2B2622), shape)
            .drawBehind {
                val r = 20.dp.toPx()
                drawLine(Color(0xE6FFFFFF), Offset(r, 1.dp.toPx()), Offset(size.width - r, 1.dp.toPx()), 1.dp.toPx())
            }
            .padding(14.dp),
    ) {
        RollerBlindIcon(glyph, Ink, 22f, Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(label, style = TextStyle(color = Mute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            BasicText(value, style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}
