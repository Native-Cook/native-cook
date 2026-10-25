package com.ngocdevv.nativecook.blocks.buttons.foilstampbutton

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.PathMeasure
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Foil Stamp Button — a stationery shop's checkout, "Paper & Press — Order #2047". A cream cotton
 card waits for its monogram; the big round brass stamp at the bottom is the button. Pressing it
 sends the stamp down (scale 1 → 0.94, the shadows tighten); on release it springs back and the
 card shows the seal embossed in the paper, then a sweep lays champagne foil over it, and the
 label becomes a drawn check and "Sealed". The timeline is in FoilStampButtonMotion.kt (LOOP = 8 s).

 Touch: hold the stamp; the longer the hold, the deeper the press (full at 0.55 s). A release
 before 0.35 s leaves a faint blind emboss with no foil, a full hold a crisp foil seal. Dragging
 the card sideways swings the lamp, so the emboss and the foil's highlights move (rubber-banded,
 sprung back on release). 2 s after the last touch the seal dissolves (0.8 s) and the loop starts
 again from t = 0. Reduced motion: the sealed card, still; a tap on the stamp fades the seal out
 or in (0.3 s).
 */

private val Ink = Color(0xFF2B2219)
private val Mute = Color(0xFF7D6D5C)
private val Faint = Color(0xFF9C8A72)
private val Label = Color(0xFFF3E3BF)
private val Foot = Color(0xFF6F604F)
internal val FoilTable = Color(0xFFE6DDCF)

// Holding the stamp: full depth after HOLD_FULL s; a hold under HOLD_WEAK s is a weak strike.
private const val HOLD_FULL = 0.55f
private const val HOLD_WEAK = 0.35f
// After the last touch the screen waits IDLE s, then eases back to rest over RESET s.
private const val IDLE = 2f
private const val RESET = 0.8f
// Dragging the card sideways swings the light by at most LIGHT_DRAG radians.
private const val LIGHT_DRAG = 0.9f

private val ROWS = listOf(
    "Cotton card, 600 gsm × 120" to "$216.00",
    "Champagne foil seal" to "$132.00",
    "Hand-deckled envelopes" to "$36.00",
)

// Seconds from a fixed origin, so a float keeps millisecond precision however long the device has run.
private val FoilOrigin = System.nanoTime()

private fun nowSeconds() = (System.nanoTime() - FoilOrigin) / 1e9f

/** Who owns the screen (the loop or the finger), the finger's animations and the frame's pose. */
private class FoilEngine(val reduced: Boolean) {
    var clock = 0f
    var user = reduced
    var lastTouch = 0f
    var resetAt = -1f
    var holding = false
    var holdStart = 0f
    var reachedFull = false
    var dragging = false
    var lightAtGrab = 0f
    var contactAt = Float.NaN
    var flakeStart = if (reduced) -100f else Float.NaN
    val press = FoilTrack(0f)
    val depth = FoilTrack(if (reduced) 1f else 0f)
    val foil = FoilTrack(if (reduced) 1f else 0f)
    val dissolve = FoilTrack(0f)
    val light = FoilTrack(0f)
    val check = FoilTrack(if (reduced) 1f else 0f)
    /** The frame loop is running (it stops at rest outside autoPlay and under reduced motion). */
    var running = false

    private val pose = FoilPose()
    private var poseAt = Float.NaN
    private var poseClock = Float.NaN

    fun busy(now: Float): Boolean {
        if (holding || dragging) return true
        if (reduced) return !(dissolve.done(now) && depth.done(now) && check.done(now))
        return user
    }

    private fun pressNow(now: Float) =
        if (holding) foilEaseOut(min(1f, (now - holdStart) / HOLD_FULL)) else press.value(now)

    fun pose(now: Float): FoilPose {
        if (now == poseAt && clock == poseClock) return pose
        poseAt = now
        poseClock = clock
        if (!user) {
            pose.setLoop(clock)
            return pose
        }
        val p = pressNow(now)
        pose.press = p
        pose.lift = FOIL_DESCENT * p
        val u = now - contactAt
        pose.squash = when {
            contactAt.isNaN() || u < 0f -> 0f
            u < 0.07f -> FoilEaseInOutQuad(u / 0.07f)
            u < 0.18f -> 1f - FoilEaseInOutQuad((u - 0.07f) / 0.11f)
            else -> 0f
        }
        pose.ring = when {
            contactAt.isNaN() || u < 0f -> 0f
            u < 0.12f -> FoilEaseInOutQuad(u / 0.12f)
            u < 0.46f -> 1f - FoilEaseInOutQuad((u - 0.12f) / 0.34f)
            else -> 0f
        }
        pose.depth = depth.value(now)
        pose.foil = foil.value(now)
        pose.dissolve = dissolve.value(now)
        pose.light = FOIL_LIGHT_AZ + light.value(now)
        pose.check = check.value(now)
        pose.flakeAge = if (flakeStart.isNaN()) -1f else now - flakeStart
        return pose
    }

    /** Takes over from the loop without a jump: the finger's pose starts as the loop's frame. */
    fun touched(now: Float) {
        lastTouch = now
        resetAt = -1f
        if (user) return
        val p = FoilPose().apply { setLoop(clock) }
        press.set(p.press)
        contactAt = Float.NaN
        depth.set(p.depth)
        foil.set(p.foil)
        dissolve.set(p.dissolve)
        light.set(p.light - FOIL_LIGHT_AZ)
        check.set(p.check)
        flakeStart = if (p.flakeAge >= 0f) now - p.flakeAge else Float.NaN
        user = true
    }

    /** One frame; returns true when the stamp just reached full depth under a held finger. */
    fun step(now: Float, dt: Float, auto: Boolean): Boolean {
        if (!user) {
            if (auto) clock = (clock + dt) % LOOP
            return false
        }
        if (reduced) return false
        if (holding) {
            val held = now - holdStart
            if (held >= HOLD_FULL && !reachedFull) {
                reachedFull = true
                contactAt = now
                return true
            }
            return false
        }
        if (dragging) return false
        if (resetAt < 0f && now - lastTouch > IDLE) {
            if (depth.value(now) > 0.001f) dissolve.timing(now, 1f, RESET, ease = FoilEaseInOutCurve)
            check.timing(now, 0f, 0.45f, ease = FoilEaseOutCurve)
            press.timing(now, 0f, RESET, ease = FoilEaseInOutCurve)
            light.timing(now, 0f, RESET, ease = FoilEaseInOutCurve)
            resetAt = now + RESET + 0.05f
        } else if (resetAt > 0f && now >= resetAt) {
            resetAt = -1f
            clock = 0f
            user = false
        }
        return false
    }

    fun stampDown(now: Float) {
        touched(now)
        if (reduced) {
            // A tap fades the seal out, or back in.
            val on = depth.value(now) > 0.5f && dissolve.value(now) < 0.5f
            if (on) {
                dissolve.timing(now, 1f, 0.3f, ease = FoilEaseInOutCurve)
                check.timing(now, 0f, 0.3f, ease = FoilEaseInOutCurve)
            } else {
                dissolve.set(0f)
                depth.timing(now, 1f, 0.3f, ease = FoilEaseInOutCurve)
                foil.set(1f)
                check.timing(now, 1f, 0.3f, ease = FoilEaseInOutCurve)
            }
            return
        }
        holding = true
        holdStart = now
        reachedFull = false
        // The old seal makes way for the new strike.
        depth.timing(now, 0f, 0.16f)
        foil.timing(now, 0f, 0.16f)
        check.timing(now, 0f, 0.18f, ease = FoilEaseOutCurve)
    }

    /** The finger left the stamp; returns true when the strike needs its contact haptic. */
    fun stampUp(now: Float): Boolean {
        if (!holding) return false
        val held = now - holdStart
        val pressed = foilEaseOut(min(1f, held / HOLD_FULL))
        holding = false
        lastTouch = now
        val strength = if (held < HOLD_WEAK) {
            0.3f + held / HOLD_WEAK * 0.25f
        } else {
            min(1f, 0.55f + (held - HOLD_WEAK) / (HOLD_FULL - HOLD_WEAK) * 0.45f)
        }
        val needsHaptic = !reachedFull
        if (needsHaptic) contactAt = now
        press.set(pressed)
        press.spring(now, 0f, 0.55f, 0.45f)
        dissolve.set(0f)
        depth.timing(now, strength, 0.3f, ease = FoilEaseOutCurve)
        val crisp = strength > 0.6f
        flakeStart = if (crisp) now else Float.NaN
        foil.set(0f)
        if (crisp) {
            foil.timing(now, 1f, 1.8f, delay = 0.5f, ease = FoilEaseInOutCurve)
            check.timing(now, 1f, 0.6f, delay = 1.1f, ease = FoilEaseOutCurve)
        }
        return needsHaptic
    }
}

/** Per-layout art: the baked paper, the seal's mask (or its alpha below Android 13) and the shader. */
private class FoilArt(val paper: ImageBitmap?, val mask: Bitmap?, val alpha: Bitmap?)

/**
 * A stationery checkout whose button is a brass stamp: hold it to press an "E & V" monogram into
 * cotton paper; on release the seal is embossed, a sweep lays champagne foil over it and the
 * label becomes "Sealed". Drag the card sideways to swing the lamp across the foil.
 *
 * @param autoPlay strikes, foils and dissolves the seal by itself, on a loop of [LOOP] s.
 */
@Composable
fun FoilStampButton(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(FoilTable)) {
        val layout = remember(maxWidth, maxHeight, top, bottom) {
            foilLayout(maxWidth.value, maxHeight.value, top, bottom)
        }
        FoilScene(layout, auto, reduced)
    }
}

@Composable
private fun FoilScene(layout: FoilLayout, auto: Boolean, reduced: Boolean) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scale = min(density.density, 3f)
    val engine = remember(reduced) { FoilEngine(reduced) }
    val tick = remember { mutableFloatStateOf(nowSeconds()) }
    val wake = remember { mutableIntStateOf(0) }
    val art by produceState<FoilArt?>(null, layout, scale) {
        value = withContext(Dispatchers.Default) {
            val shaderOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            FoilArt(
                foilBakePaper(layout, density.density)?.asImageBitmap(),
                if (shaderOk) foilBuildSealMask(layout.sealR, scale) else null,
                if (shaderOk) null else foilBuildSealAlpha(layout.sealR, scale),
            )
        }
    }
    val seal = remember(art) {
        val mask = art?.mask
        if (mask != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                RuntimeShader(FOIL_SEAL_AGSL).apply {
                    setInputShader("mask", BitmapShader(mask, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP))
                }
            }.getOrNull()
        } else {
            null
        }
    }
    val sealBrush = remember(seal) { seal?.let { ShaderBrush(it) } }

    val r = layout.sealR
    val br = layout.buttonR
    val bx = layout.buttonX
    val by = layout.buttonY
    val checkFull = remember(bx, by) { foilCheckPath(bx, by - 9f) }
    val checkMeasure = remember(checkFull) { PathMeasure(checkFull, false) }
    val checkPart = remember { android.graphics.Path() }
    val flakes = remember { androidx.compose.ui.graphics.Path() }
    val flakeOut = remember { FloatArray(4) }
    val flakeBrush = remember(layout) {
        Brush.linearGradient(
            listOf(Color(0xFF8A6A2C), Color(0xFFF3DC9A), Color(0xFFB8913F), Color(0xFFFFF0C4), Color(0xFF9A7635)),
            start = Offset(layout.sealX - r, layout.sealY - r),
            end = Offset(layout.sealX + r, layout.sealY + r),
        )
    }
    val brass = remember(bx, by) {
        Brush.sweepGradient(
            listOf(
                Color(0xFF7A5523), Color(0xFFE2C27A), Color(0xFFFFF1C9), Color(0xFFB48638), Color(0xFF5E3F18),
                Color(0xFFC9A253), Color(0xFFF4DC9C), Color(0xFF9A6F2C), Color(0xFF7A5523),
            ),
            center = Offset(bx, by),
        )
    }
    val rim = remember(bx, by) {
        Brush.linearGradient(
            listOf(Color(255, 248, 226, 191), Color(255, 248, 226, 0), Color(70, 44, 12, 128)),
            start = Offset(bx, by - br), end = Offset(bx, by + br),
        )
    }
    val face = remember(bx, by) {
        Brush.radialGradient(
            listOf(Color(0xFF5D4130), Color(0xFF3A281B), Color(0xFF24170F), Color(0xFF1B110B)),
            center = Offset(bx - 12f, by - 16f), radius = br * 1.3f,
        )
    }
    val faceRim = remember(bx, by) {
        Brush.linearGradient(
            listOf(Color(255, 240, 210, 56), Color(255, 240, 210, 0), Color(0, 0, 0, 77)),
            start = Offset(bx, by - br), end = Offset(bx, by + br),
        )
    }
    val fallbackPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }

    LaunchedEffect(engine, auto, wake.intValue) {
        engine.running = true
        var last = -1L
        try {
            while (true) {
                val keepGoing = withFrameNanos { nanos ->
                    val now = (nanos - FoilOrigin) / 1e9f
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    if (engine.step(now, dt, auto)) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    tick.floatValue = now
                    auto || engine.busy(now)
                }
                if (!keepGoing) break
            }
        } finally {
            engine.running = false
        }
    }

    fun wakeUp() {
        tick.floatValue = nowSeconds()
        if (!engine.running) wake.intValue += 1
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val now = tick.floatValue
            val p = engine.pose(now)
            art?.paper?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            scale(density.density, density.density, pivot = Offset.Zero) {
                val side = (r + FOIL_MASK_PAD) * 2f
                if (seal != null && sealBrush != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    seal.setFloatUniform("origin", layout.sealX, layout.sealY)
                    seal.setFloatUniform("scale", scale)
                    seal.setFloatUniform("R", r)
                    seal.setFloatUniform("depth", p.depth)
                    seal.setFloatUniform("foil", p.foil)
                    seal.setFloatUniform("dissolve", p.dissolve)
                    seal.setFloatUniform("ring", p.ring)
                    seal.setFloatUniform("light", p.light)
                    seal.setFloatUniform("elevation", FOIL_LIGHT_EL)
                    drawRect(sealBrush, Offset(layout.sealX - side / 2f, layout.sealY - side / 2f), Size(side, side))
                } else {
                    art?.alpha?.let { foilDrawSealFallback(it, layout, scale, p, fallbackPaint) }
                }

                // Flakes of foil round the seal.
                if (p.flakeAge >= 0f) {
                    flakes.rewind()
                    for (i in 0 until FOIL_FLAKES) {
                        foilFlake(i, p.flakeAge, r, flakeOut)
                        val cx = layout.sealX + flakeOut[0]
                        val cy = layout.sealY + flakeOut[1]
                        for (k in 0 until 4) {
                            val ang = flakeOut[2] + k * PI.toFloat() / 2f
                            val rr = flakeOut[3] * (if (k % 2 == 0) 1f else 0.55f)
                            val px = cx + cos(ang) * rr
                            val py = cy + sin(ang) * rr
                            if (k == 0) flakes.moveTo(px, py) else flakes.lineTo(px, py)
                        }
                        flakes.close()
                    }
                    val a = min(1f, p.flakeAge / 0.08f) * (1f - p.dissolve) * min(1f, p.depth / 0.6f)
                    if (a > 0.002f) drawPath(flakes, flakeBrush, alpha = a.coerceIn(0f, 1f))
                }

                // The stamp's two shadows on the linen.
                val pr = max(0f, p.press)
                foilSoftCircle(bx, by + 12f - 9f * p.press, br - 4f, 16f - 9f * pr, Color(0xFF3C2810), 0.34f - 0.18f * pr)
                foilSoftCircle(bx, by + 2f, br - 1f, 3f, Color(0xFF2A1A08), 0.28f + 0.14f * pr)

                val s = 1f - FOIL_PRESS_SCALE * p.press - FOIL_SQUASH * p.squash
                withTransform({
                    translate(0f, p.lift)
                    scale(s, s, pivot = Offset(bx, by))
                }) {
                    // Brass ring, a dark groove, and the walnut face.
                    drawCircle(brass, br, Offset(bx, by))
                    drawCircle(rim, br - 0.75f, Offset(bx, by), style = Stroke(1.5f))
                    drawCircle(Color(0xFF3A2711), br - 7f, Offset(bx, by))
                    drawCircle(face, br - 9f, Offset(bx, by))
                    drawCircle(faceRim, br - 9.5f, Offset(bx, by), style = Stroke(1f))
                    if (p.check > 0.001f) {
                        checkPart.rewind()
                        checkMeasure.getSegment(0f, checkMeasure.length * p.check.coerceAtMost(1f), checkPart, true)
                        drawPath(
                            checkPart.asComposePath(), Label,
                            style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
        }

        // The header.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp)
                .offset { IntOffset(0, (layout.headerTop * density.density).roundToInt()) },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("PAPER & PRESS", style = TextStyle(color = Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
            BasicText(
                "Order #2047",
                style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum"),
            )
            BasicText("Wedding suite · ships in 6 days", style = TextStyle(color = Mute, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }

        // The card: drag it sideways to move the light.
        val card = layout.card
        Box(
            Modifier
                .offset { IntOffset((card.x * density.density).roundToInt(), (card.y * density.density).roundToInt()) }
                .size(card.w.dp, card.h.dp)
                .semantics { contentDescription = "Monogram preview card. Drag sideways to move the light." }
                .pointerInput(engine) {
                    if (engine.reduced) return@pointerInput
                    val tracker = VelocityTracker()
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            val now = nowSeconds()
                            engine.touched(now)
                            engine.dragging = true
                            engine.lightAtGrab = engine.light.value(now)
                            tracker.resetTracking()
                            dx = 0f
                            wakeUp()
                        },
                        onDragEnd = {
                            val now = nowSeconds()
                            engine.dragging = false
                            engine.lastTouch = now
                            val v = tracker.calculateVelocity().x / density.density
                            engine.light.spring(now, 0f, 0.7f, 0.7f, velocity = v / 140f * LIGHT_DRAG * 0.4f)
                            wakeUp()
                        },
                        onDragCancel = {
                            val now = nowSeconds()
                            engine.dragging = false
                            engine.lastTouch = now
                            engine.light.spring(now, 0f, 0.7f, 0.7f)
                            wakeUp()
                        },
                    ) { change, amount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dx += amount / density.density
                        val now = nowSeconds()
                        engine.lastTouch = now
                        engine.light.set(engine.lightAtGrab + foilRubber(dx, 140f) / 140f * LIGHT_DRAG)
                        change.consume()
                    }
                },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText("MONOGRAM PREVIEW", style = TextStyle(color = Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
                BasicText("No. 1 / 120", style = TextStyle(color = Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }
            BasicText(
                "Elena & Vic · 14 June 2027",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                style = TextStyle(color = Foot, fontSize = 14.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center),
            )
        }

        // The order's lines (they add up to the total).
        Column(
            Modifier
                .offset { IntOffset((card.x * density.density).roundToInt(), (layout.rowsTop * density.density).roundToInt()) }
                .width(card.w.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val name = TextStyle(color = Mute, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val price = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            for ((n, v) in ROWS) {
                Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(n, style = name)
                    BasicText(v, style = price)
                }
            }
            Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height((1f / density.density).dp).background(Color(80, 60, 35, 64)))
            Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText("Total", style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                BasicText("$384.00", style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
        }

        // The stamp's label and hit area.
        val hit = br * 2f + 16f
        Box(
            Modifier
                .offset { IntOffset(((bx - br - 8f) * density.density).roundToInt(), ((by - br - 8f) * density.density).roundToInt()) }
                .size(hit.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Seal order. Hold to press the stamp."
                }
                .pointerInput(engine) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        engine.stampDown(nowSeconds())
                        wakeUp()
                        waitForUpOrCancellation()
                        if (engine.stampUp(nowSeconds())) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        wakeUp()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val label = TextStyle(color = Label, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
            BasicText(
                "Seal order",
                modifier = Modifier.graphicsLayer {
                    val p = engine.pose(tick.floatValue)
                    alpha = (1f - p.check).coerceIn(0f, 1f)
                    translationY = (p.lift - p.check * 6f) * density.density
                    val s = 1f - FOIL_PRESS_SCALE * p.press
                    scaleX = s
                    scaleY = s
                },
                style = label,
            )
            BasicText(
                "Sealed",
                modifier = Modifier
                    .padding(top = 22.dp)
                    .graphicsLayer {
                        val p = engine.pose(tick.floatValue)
                        alpha = p.check.coerceIn(0f, 1f)
                        translationY = (p.lift + (1f - p.check) * 6f) * density.density
                        val s = 1f - FOIL_PRESS_SCALE * p.press
                        scaleX = s
                        scaleY = s
                    },
                style = label.copy(fontSize = 13.sp),
            )
        }
        BasicText(
            "Hold to press · drag the card to move the light",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset { IntOffset(0, (layout.hintTop * density.density).roundToInt()) },
            style = TextStyle(color = Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

/** A disc with a Gaussian edge of `sigma` dp, like Skia's blur mask on a circle. */
private fun DrawScope.foilSoftCircle(cx: Float, cy: Float, r: Float, sigma: Float, color: Color, alpha: Float) {
    if (alpha <= 0.002f) return
    val outer = r + 2.5f * sigma
    fun stop(d: Float) = (d / outer).coerceIn(0f, 1f)
    drawCircle(
        Brush.radialGradient(
            0f to color,
            stop(r - 2f * sigma) to color,
            stop(r - sigma) to color.copy(alpha = 0.84f),
            stop(r) to color.copy(alpha = 0.5f),
            stop(r + sigma) to color.copy(alpha = 0.16f),
            stop(r + 2f * sigma) to color.copy(alpha = 0.02f),
            1f to color.copy(alpha = 0f),
            center = Offset(cx, cy),
            radius = outer,
        ),
        outer, Offset(cx, cy), alpha = alpha.coerceIn(0f, 1f),
    )
}

/**
 * Below Android 13: the seal from its alpha mask. The emboss is the monogram twice, offset toward
 * and away from the lamp in light and brown; the foil is the monogram in a gold ramp, clipped by
 * the diagonal sweep; the dissolve is a fade.
 */
private fun DrawScope.foilDrawSealFallback(alpha: Bitmap, l: FoilLayout, scale: Float, p: FoilPose, paint: android.graphics.Paint) {
    val amount = p.depth * (1f - p.dissolve)
    if (amount < 0.002f) return
    val side = (l.sealR + FOIL_MASK_PAD) * 2f
    val x0 = l.sealX - side / 2f
    val y0 = l.sealY - side / 2f
    val lx = cos(p.light)
    val ly = sin(p.light)
    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        n.save()
        n.translate(x0, y0)
        n.scale(1f / scale, 1f / scale)
        val o = 0.8f * amount * scale
        paint.shader = null
        paint.color = android.graphics.Color.argb((amount * 0.55f * 255).roundToInt(), 92, 69, 43)
        n.drawBitmap(alpha, -lx * o, -ly * o, paint)
        paint.color = android.graphics.Color.argb((amount * 0.75f * 255).roundToInt(), 255, 251, 242)
        n.drawBitmap(alpha, lx * o, ly * o, paint)
        val foilA = foilClamp01((p.depth - 0.55f) / 0.3f) * (1f - p.dissolve)
        if (foilA > 0.002f && p.foil > 0.001f) {
            n.save()
            // The sweep runs along (0.8, 0.6) from −1.2 to 1.2 seal radii.
            val front = (p.foil * 2.4f - 1.2f) * l.sealR * 1.1f
            val c0 = (side / 2f) * scale
            n.rotate((Math.toDegrees(kotlin.math.atan2(0.6, 0.8))).toFloat(), c0, c0)
            n.clipRect(-side * scale, -side * scale, c0 + front * scale, side * 2f * scale)
            n.rotate(-(Math.toDegrees(kotlin.math.atan2(0.6, 0.8))).toFloat(), c0, c0)
            val shift = (p.light - FOIL_LIGHT_AZ) * 40f * scale
            paint.color = android.graphics.Color.argb((foilA * 255).roundToInt(), 255, 255, 255)
            paint.shader = android.graphics.LinearGradient(
                shift, 0f, side * scale + shift, side * scale,
                intArrayOf(0xFF6B4A1C.toInt(), 0xFFC79445.toInt(), 0xFFF5DB94.toInt(), 0xFFB8913F.toInt(), 0xFFFFF0C4.toInt(), 0xFF8A6A2C.toInt()),
                null, android.graphics.Shader.TileMode.MIRROR,
            )
            n.drawBitmap(alpha, 0f, 0f, paint)
            paint.shader = null
            n.restore()
        }
        n.restore()
    }
}
