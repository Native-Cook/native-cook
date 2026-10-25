package com.ngocdevv.nativecook.blocks.progress.breadrisingprogress

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.BAKE_END
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.BOULE_CY
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.BOWL_RY
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.LOOP
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.PROOF_START
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.STOPS
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 Bread Rising Progress — a baking app, "Crumb — Country Sourdough", whose progress is a loaf of
 dough: it rises in a tinted glass bowl on a linen cloth, pops out as a boule on a baking stone, an
 oven glows up behind it while the crust browns, the score blooms and steam curls off, then a green
 check marks it ready. A port of BreadRisingProgress.tsx (and the SwiftUI block); the numbers are
 the same.

 autoPlay timeline (LOOP = 9 s, BreadRisingProgressMotion.kt): rest 0–1.3 s; proof 1.3–4.0 s
 (the dough's level climbs to the rim and its dome grows, 12 bubbles swell and pop, the timer
 counts 1:30:00 down); shape 4.0–4.8 s (the bowl drops and fades, the outline morphs into a boule
 with an ease-out-back pop, the bake timer rolls in to 0:45:00); bake 4.8–7.0 s (oven, crust,
 score, steam 5.4–6.3 s); ready 7.0–7.8 s (the check pops in); reset 7.8–8.6 s.

 Touch: a vertical drag scrubs the timeline (320 dp for the whole bake, up = forward) with a
 rubber band past raw dough and the baked loaf and a tick at each stage; on release it springs
 (about 500 ms, damping ratio 0.85, carrying the finger's speed) to the nearest stage, aimed 0.18 s
 ahead. 2 s after the last touch it eases back to raw dough (900 ms) and the loop restarts at 0.
 Reduced motion: the loop does not play and the screen rests on the raw dough; scrubbing works.
 */

private val Ink = Color(0xFF3A2A1C)
private val Mute = Color(58 / 255f, 42 / 255f, 28 / 255f, 0.58f)
private val Accent = Color(0xFFB4642A)
private val Root = Color(0xFFF0E5D3)

private val statuses = listOf("Proofing at 26 °C", "Shaping the boule", "Baking at 240 °C", "Ready to cool", "Proofing at 26 °C")
private val stageNames = listOf("Proof", "Shape", "Bake")
private const val DIGIT_H = 62f

/** The timeline: auto-play runs it, a finger scrubs it. Plain fields; only [tl] is published to the screen. */
private class BreadController(private val haptic: () -> Unit) {
    companion object {
        /** Seconds after the last touch before the loop starts again. */
        const val IDLE = 2.0
        /** Finger travel (dp) that scrubs from raw dough to the baked loaf. */
        const val SCRUB = 320.0
        val back = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    }

    var tl = 0.0
    var user = false
    var dragActive = false
    private var grabbing = false
    private var idle = 0.0
    private var startTl = 0.0
    private var lastStop = 0
    private var springTo: Double? = null
    private var springV = 0.0
    private var returnFrom: Double? = null
    private var returnT = 0.0

    val resting get() = !user && !dragActive

    fun step(rawDt: Double, looping: Boolean) {
        // A slow frame pauses the clock instead of skipping ahead.
        val dt = min(max(rawDt, 0.0), 1.0 / 30)
        if (user) {
            val target = springTo
            if (target != null) {
                val k = 160.0
                val c = 2 * 0.85 * sqrt(k)
                springV += (-k * (tl - target) - c * springV) * dt
                tl += springV * dt
                if (abs(tl - target) < 0.0005 && abs(springV) < 0.01) {
                    tl = target
                    springTo = null
                }
            }
            val from = returnFrom
            if (from != null) {
                // Back to raw dough (the scrub runs backwards), then the loop restarts from t = 0.
                returnT += dt
                val x = min(1.0, returnT / 0.9)
                tl = from + (PROOF_START - from) * back.transform(x.toFloat())
                if (x >= 1) {
                    returnFrom = null
                    tl = 0.0
                    user = false
                }
            } else if (!grabbing) {
                idle += dt
                if (idle > IDLE) {
                    springTo = null
                    returnFrom = tl
                    returnT = 0.0
                }
            }
            return
        }
        if (looping) tl = (tl + dt) % LOOP
    }

    private fun stopIndex(t: Double): Int {
        var k = 0
        for (i in STOPS.indices) if (t >= STOPS[i] - 0.01) k = i
        return k
    }

    fun begin() {
        dragActive = true
        if (returnFrom != null) return
        user = true
        grabbing = true
        idle = 0.0
        springTo = null
        var t = tl
        if (t > BAKE_END + 0.6 || t < PROOF_START) t = PROOF_START
        if (t > BAKE_END) t = BAKE_END
        tl = t
        startTl = t
        lastStop = stopIndex(t)
    }

    fun update(translationY: Double) {
        if (!grabbing) return
        idle = 0.0
        val span = BAKE_END - PROOF_START
        var t = startTl - (translationY / SCRUB) * span
        // Rubber band past raw dough and past the baked loaf.
        if (t < PROOF_START) t = PROOF_START - (PROOF_START - t) / (1 + (PROOF_START - t) * 4)
        if (t > BAKE_END) t = BAKE_END + (t - BAKE_END) / (1 + (t - BAKE_END) * 4)
        tl = min(t, BAKE_END + 0.25)
        val k = stopIndex(t)
        if (k != lastStop) {
            lastStop = k
            haptic()
        }
    }

    fun end(velocityY: Double) {
        dragActive = false
        if (!grabbing) return
        grabbing = false
        idle = 0.0
        val span = BAKE_END - PROOF_START
        val vel = (-velocityY / SCRUB) * span
        val aim = tl + vel * 0.18
        var best = STOPS[0]
        for (i in 1 until STOPS.size) if (abs(STOPS[i] - aim) < abs(best - aim)) best = STOPS[i]
        springTo = best
        springV = vel
    }
}

@Composable
fun BreadRisingProgress(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val looping = autoPlay && !reduced
    val c = remember(looping) { BreadController { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
    /** The loop time, read only in draw and graphicsLayer lambdas. */
    val time = remember(c) { mutableFloatStateOf(0f) }
    var awake by remember(c) { mutableStateOf(false) }
    val painter = remember { BreadPainter() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Root)
            .clipToBounds(),
    ) {
        val density = LocalDensity.current.density
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        val w = maxWidth.value
        val h = maxHeight.value

        // Layout (dp). The hero sits between the timer and the stage control, on the counter.
        val heroTop = top + 236
        val controlsTop = h - bottom - 150
        val avail = controlsTop - heroTop - 12
        val heroScale = minOf(1.08f, max(0.6f, avail / 330), (w - 24) / 350)
        val heroY = heroTop + 168 * heroScale
        val counterY = heroY + (BOWL_RY + 8) * heroScale
        val trackW = max(0f, w - 40)

        var art by remember { mutableStateOf<BreadPictures?>(null) }
        LaunchedEffect(w, h, counterY, heroScale, density) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to
            // settle (cancellable) so a bake does not restart on every inset frame.
            delay(120)
            art = withContext(Dispatchers.Default) { BreadBake.bake(w, h, counterY, heroScale, min(2f, density)) }
        }
        val ready = art != null

        // One frame loop, from the first frame that has the art; it sleeps at rest outside the loop.
        LaunchedEffect(c, ready, looping, awake) {
            if (!ready || !(looping || awake)) return@LaunchedEffect
            var last = -1L
            while (true) {
                val stop = withFrameNanos { now ->
                    val dt = if (last < 0) 0.0 else (now - last) / 1e9
                    last = now
                    c.step(dt, looping)
                    time.floatValue = c.tl.toFloat()
                    !looping && c.resting
                }
                if (stop) {
                    awake = false
                    break
                }
            }
        }

        val blit = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
        val dst = remember { RectF() }
        val src = remember { Rect() }
        val full = remember { Rect() }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Proofing dough. Drag up to proof, shape and bake the loaf." }
                .pointerInput(c, density) {
                    val tracker = VelocityTracker()
                    var dy = 0.0
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            dy = 0.0
                            if (!c.dragActive) {
                                if (!looping) awake = true
                                c.begin()
                            }
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dy += amount / density
                            c.update(dy)
                        },
                        onDragEnd = { c.end(tracker.calculateVelocity().y / density.toDouble()) },
                        onDragCancel = { c.end(0.0) },
                    )
                },
        ) {
            val pics = art ?: return@Canvas
            val b = BreadRisingProgressMotion.breadAt(time.floatValue.toDouble())
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                full.set(0, 0, size.width.toInt(), size.height.toInt())
                nc.drawBitmap(pics.backdrop, null, full, blit.apply { alpha = 255 })
                val save = nc.save()
                nc.translate(w / 2 * density, heroY * density)
                nc.scale(density * heroScale, density * heroScale)
                val bowlY = b.bowlY.toFloat()
                val bowlA = b.bowlA.toFloat()
                nc.drawBreadLayer(pics.oven, b.oven.toFloat(), 0f, blit, dst, src)
                nc.drawBreadLayer(pics.cloth, bowlA, 0f, blit, dst, src)
                nc.drawBreadLayer(pics.stone, b.stone.toFloat(), 0f, blit, dst, src)
                nc.drawBreadLayer(pics.bowlBack, bowlA, bowlY, blit, dst, src)
                painter.dough(nc, b)
                nc.drawBreadLayer(pics.bowlFront, bowlA, bowlY, blit, dst, src)
                painter.steam(nc, b)
                if (b.ready > 0.001) {
                    nc.save()
                    nc.translate(104f, BOULE_CY - 100)
                    val s = max(0.001f, b.ready.toFloat())
                    nc.scale(s, s)
                    nc.drawBreadLayer(pics.badge, min(1f, b.ready.toFloat() * 2), 0f, blit, dst, src)
                    nc.restore()
                }
                nc.restoreToCount(save)
            }
        }

        Header(top, w)

        // The timer and the rolling status line.
        Column(Modifier.offset(x = 20.dp, y = (top + 124).dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.height(DIGIT_H.dp), verticalAlignment = Alignment.CenterVertically) {
                Wheel(time, 3600.0, 10)
                Colon()
                Wheel(time, 600.0, 6)
                Wheel(time, 60.0, 10)
                Colon()
                Wheel(time, 10.0, 6)
                Wheel(time, 1.0, 10)
            }
            Box(Modifier.width(trackW.dp).height(22.dp).clipToBounds()) {
                statuses.forEachIndexed { i, label ->
                    BasicText(
                        label,
                        Modifier
                            .height(22.dp)
                            .graphicsLayer {
                                val d = i - BreadRisingProgressMotion.statusAt(time.floatValue.toDouble())
                                alpha = max(0.0, 1 - abs(d) * 1.6).toFloat()
                                translationY = (d * 20).toFloat() * density
                            },
                        style = TextStyle(color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
                        maxLines = 1,
                    )
                }
            }
        }

        // Proof · Shape · Bake, the progress bar and the captions.
        Column(
            Modifier.offset(x = 20.dp, y = controlsTop.dp).width(trackW.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Stages(time, trackW)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(90 / 255f, 60 / 255f, 30 / 255f, 0.14f)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = -(1 - BreadRisingProgressMotion.progressAt(time.floatValue.toDouble())).toFloat() * size.width }
                        .clip(RoundedCornerShape(3.dp))
                        .background(Accent),
                )
            }
            Row(Modifier.fillMaxWidth().offset(y = (-6).dp)) {
                BasicText("Started 6:10 PM", style = CaptionStyle)
                Spacer(Modifier.weight(1f))
                BasicText("Out of the oven 8:55 PM", style = CaptionStyle)
            }
        }
    }
}

private val CaptionStyle = TextStyle(color = Mute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

@Composable
private fun Header(top: Float, w: Float) {
    Row(
        Modifier.offset(x = 20.dp, y = (top + 12).dp).width((w - 40).dp).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .cssBoxShadow(Color(80 / 255f, 40 / 255f, 10 / 255f, 0.3f), 1.dp, 2.dp, 9.dp)
                .clip(CircleShape)
                .background(Accent)
                .drawBehind {
                    // inset 0 -3px 4px: the mark is darker towards its bottom edge.
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color(80 / 255f, 30 / 255f, 5 / 255f, 0.35f)),
                            center = Offset(size.width / 2, size.height * 0.25f),
                            radius = 11.dp.toPx(),
                        ),
                    )
                },
        )
        Spacer(Modifier.width(8.dp))
        BasicText("Crumb", style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp))
        Spacer(Modifier.weight(1f))
        BasicText("Loaf 2 of 3", style = TextStyle(color = Mute, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
    }
    Column(Modifier.offset(x = 20.dp, y = (top + 56).dp).width((w - 40).dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText("Country Sourdough", style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp), maxLines = 1)
        BasicText("78% hydration · 900 g · Dutch oven", style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

private val DigitStyle = TextStyle(
    color = Ink, fontSize = 54.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
    fontFeatureSettings = "tnum", letterSpacing = (-1).sp,
)

/** One odometer wheel of the timer; a wheel spinning fast dims a little, like a blur. */
@Composable
private fun Wheel(time: androidx.compose.runtime.MutableFloatState, unit: Double, base: Int) {
    Box(Modifier.width(34.dp).height(DIGIT_H.dp).clipToBounds()) {
        // The strip keeps its full height inside the one-digit window that clips it; measured to
        // the window, every digit after the first would get no height and not draw.
        Column(
            Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true).graphicsLayer {
                val t = time.floatValue.toDouble()
                val pos = BreadRisingProgressMotion.column(BreadRisingProgressMotion.timerAt(t), unit, base)
                val before = BreadRisingProgressMotion.column(BreadRisingProgressMotion.timerAt(max(0.0, t - 1.0 / 60)), unit, base)
                var speed = abs(pos - before)
                if (speed > base / 2.0) speed = base - speed
                val blur = ((speed - 0.12) / 0.5).coerceIn(0.0, 1.0)
                translationY = (-pos * DIGIT_H * density).toFloat()
                alpha = (1 - 0.55 * blur).toFloat()
            },
        ) {
            for (d in 0..base) {
                Box(Modifier.width(34.dp).height(DIGIT_H.dp), contentAlignment = Alignment.Center) {
                    BasicText("${d % base}", style = DigitStyle, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun Colon() {
    BasicText(
        ":",
        Modifier.padding(horizontal = 1.dp).offset(y = (-3).dp),
        style = TextStyle(color = Color(58 / 255f, 42 / 255f, 28 / 255f, 0.45f), fontSize = 46.sp, fontWeight = FontWeight.SemiBold),
    )
}

/** Proof · Shape · Bake, with a thumb that slides with the stage (and back to Proof on reset). */
@Composable
private fun Stages(time: androidx.compose.runtime.MutableFloatState, width: Float) {
    val segW = (width - 8) / 3
    fun at(): Double {
        val s = BreadRisingProgressMotion.stageAt(time.floatValue.toDouble())
        return if (s <= 2) s else 2 * (3 - s)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(1f, 250 / 255f, 240 / 255f, 0.55f), RoundedCornerShape(22.dp))
            .drawBehind {
                // inset 0 1px 2px rgba(90,60,30,0.18): a soft shade inside the top edge.
                val r = CornerRadius(size.height / 2)
                val shape = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r))
                }
                clipPath(shape) {
                    drawRoundRect(
                        Color(90 / 255f, 60 / 255f, 30 / 255f, 0.12f),
                        topLeft = Offset(0f, 1.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = r,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .width(segW.dp)
                .fillMaxHeight()
                .graphicsLayer { translationX = (at() * segW * density).toFloat() }
                .cssBoxShadow(Color(40 / 255f, 20 / 255f, 5 / 255f, 0.18f), 6.dp, 14.dp, 18.dp)
                .cssBoxShadow(Color(40 / 255f, 20 / 255f, 5 / 255f, 0.35f), 1.dp, 2.dp, 18.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ink),
        )
        Row(Modifier.fillMaxSize()) {
            stageNames.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    BasicText(label, style = SegmentStyle.copy(color = Mute))
                    BasicText(
                        label,
                        Modifier.graphicsLayer { alpha = max(0.0, 1 - abs(at() - i)).toFloat() },
                        style = SegmentStyle.copy(color = Color(0xFFFBF3E6)),
                    )
                }
            }
        }
    }
}

private val SegmentStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
