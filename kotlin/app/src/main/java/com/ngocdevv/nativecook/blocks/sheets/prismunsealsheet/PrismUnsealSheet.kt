package com.ngocdevv.nativecook.blocks.sheets.prismunsealsheet

import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path as NativePath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Seconds for one exact rest-to-rest loop, shared with the Swift reference. */
const val LOOP = 8f

/** A silver mailer hinges open to its rainbow liner and delivers an everyday card. */
@Composable
fun PrismUnsealSheet(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = prismReducedMotion()
    val motion = remember { PrismUnsealSheetMotion() }
    val latestAuto by rememberUpdatedState(autoPlay)
    val latestReduced by rememberUpdatedState(reduced)
    val latestPreview by rememberUpdatedState(previewTime)
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                motion.epoch = prismNow()
                active = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                active = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    PrismUnsealSheetSystemBars()
    val serial = motion.serial.intValue
    LaunchedEffect(autoPlay, reduced, previewTime, active, serial) {
        motion.now.doubleValue = prismNow()
        if (reduced || previewTime != null || !active) return@LaunchedEffect
        while (autoPlay || motion.dragFrame != null || motion.isSettling()) {
            withFrameNanos { motion.now.doubleValue = it / 1e9 }
        }
    }
    fun frame() = motion.frame(latestAuto, latestReduced, latestPreview)
    fun haptic() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun manual(target: Double) {
        motion.release(frame(), target)
        haptic()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF111216))) {
        val density = LocalDensity.current
        val fit = min(maxWidth.value / 393f, maxHeight.value / 852f)
        val left = (maxWidth.value - 393f * fit) / 2f
        val top = (maxHeight.value - 852f * fit) / 2f
        val inset = WindowInsets.statusBars.getTop(density) / density.density / fit
        val artwork = remember(inset) { PrismUnsealSheetArtwork(max(0f, inset + 12f - 70f)) }
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                canvas.save()
                canvas.translate(left * density.density, top * density.density)
                canvas.scale(fit * density.density, fit * density.density)
                artwork.draw(canvas, frame())
                canvas.restore()
            }
        }
        // Semantics change at meaningful thresholds; the 120 Hz clock is read only in drawing.
        val accessible by remember {
            derivedStateOf {
                val pose = frame()
                val state = if (pose.ready > 0.9) "Open" else if (pose.progress > 0.01) "Partly open" else "Closed"
                val clearance = 1 - PrismUnsealSheetMotion.smooth(PrismUnsealSheetMotion.segment(pose.lid, 0.25, 0.65))
                PrismUnsealSheetAccessibility(
                    state,
                    pose.progress > 0.5,
                    if (pose.ready >= 0.5) "Your card is ready. Made for wherever you go. Ready when you are."
                    else if (clearance >= 0.5) "A little more possibility. A new everyday card. Unseal something good."
                    else "",
                )
            }
        }
        Box(Modifier.offset((left + 26f * fit).dp, (top + 80f * fit).dp)
            .size((280f * fit).dp, (35f * fit).dp)
            .semantics { contentDescription = "Prism wallet"; heading() })
        if (accessible.copy.isNotEmpty()) {
            Box(Modifier.offset((left + 14f * fit).dp, (top + 429f * fit).dp)
                .size((365f * fit).dp, (80f * fit).dp)
                .semantics { contentDescription = accessible.copy; heading() })
        }
        Box(Modifier.offset((left + 336f * fit).dp, (top + 378f * fit).dp)
            .size((44f * fit).dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { manual(0.0) }
            .semantics { contentDescription = "Close package" })
        Box(Modifier.offset((left + 26f * fit).dp, (top + 748f * fit).dp)
            .size((341f * fit).dp, (50f * fit).dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                manual(if (frame().progress > 0.5) 0.0 else 1.0)
            }
            .semantics { contentDescription = if (accessible.openAction) "Close card sleeve" else "Unseal card" })
        Box(Modifier.offset((left + 70f * fit).dp, (top + 509f * fit).dp)
            .size((253f * fit).dp, (196f * fit).dp)
            .semantics {
                contentDescription = "Unseal package"
                stateDescription = accessible.state
                progressBarRangeInfo = ProgressBarRangeInfo(if (accessible.state == "Open") 1f else if (accessible.state == "Closed") 0f else 0.5f, 0f..1f)
                setProgress { manual(if (it >= 0.5f) 1.0 else 0.0); true }
                customActions = listOf(
                    CustomAccessibilityAction("Unseal package") { manual(1.0); true },
                    CustomAccessibilityAction("Close package") { manual(0.0); true },
                )
            }
            .pointerInput(motion, fit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val origin = frame()
                    motion.beginDrag(origin)
                    var previousY = down.position.y
                    var previousTime = down.uptimeMillis
                    var crossed = false
                    var dragged = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!latestReduced) {
                            val delta = -(change.position.y - down.position.y) / (density.density * fit * 180.0)
                            val target = PrismUnsealSheetMotion.scrub(if (delta >= 0) 1.0 else 0.0)
                            val distance = max(0.001, if (delta >= 0) 1 - origin.progress else origin.progress)
                            val elapsed = max(1L, change.uptimeMillis - previousTime)
                            val velocity = (change.position.y - previousY) / (density.density * fit) * 1000 / elapsed
                            val pose = PrismUnsealSheetMotion.blend(origin, target, PrismUnsealSheetMotion.clamp(abs(delta) / distance))
                                .copy(tilt = PrismUnsealSheetMotion.clamp(velocity / 1400.0, -1.0, 1.0) * 4)
                            motion.dragFrame = pose
                            motion.now.doubleValue = prismNow()
                            if (pose.progress >= 0.7 && !crossed) { crossed = true; haptic() }
                            previousY = change.position.y
                            previousTime = change.uptimeMillis
                            dragged = true
                        }
                        change.consume()
                    } while (event.changes.any { it.id == down.id && it.pressed })
                    if (latestReduced) manual(1.0)
                    else {
                        val pose = if (dragged) motion.dragFrame ?: origin else origin
                        motion.release(pose, if (pose.progress >= 0.5) 1.0 else 0.0)
                    }
                }
            })
    }
}

private data class PrismUnsealSheetAccessibility(val state: String, val openAction: Boolean, val copy: String)

private fun prismNow() = System.nanoTime() / 1e9

private class PrismUnsealSheetMotion {
    data class Frame(val lid: Double, val spread: Double, val extraction: Double, val ready: Double, val tilt: Double = 0.0, val progress: Double = 0.0)
    val now = mutableDoubleStateOf(prismNow())
    val serial = mutableIntStateOf(0)
    var epoch = now.doubleValue
    var releasedAt: Double? = null
    var releaseFrame = scrub(0.0)
    var releaseTo = 0.0
    var dragFrame: Frame? = null

    fun frame(autoPlay: Boolean, reduced: Boolean, preview: Double?): Frame {
        // Keep drawing subscribed to the clock while a plain drag pose is active.
        val sampleTime = now.doubleValue
        if (reduced) return scrub(if (releasedAt == null) 1.0 else releaseTo)
        if (preview != null) return timeline(preview)
        dragFrame?.let { return it }
        releasedAt?.let { release ->
            val elapsed = max(0.0, sampleTime - release)
            if (elapsed < 2) return blend(releaseFrame, scrub(releaseTo), spring(elapsed))
            if (elapsed < 2.6) return blend(scrub(releaseTo), scrub(0.0), smooth(segment(elapsed, 2.0, 2.6)))
            return if (autoPlay) timeline(elapsed - 2.6) else scrub(0.0)
        }
        return if (autoPlay) timeline(sampleTime - epoch) else scrub(0.0)
    }

    fun isSettling() = releasedAt?.let { now.doubleValue - it < 2.6 } ?: false
    fun beginDrag(origin: Frame) {
        dragFrame = origin
        now.doubleValue = prismNow()
        serial.intValue++
    }
    fun release(origin: Frame, target: Double) {
        releaseFrame = origin
        releaseTo = target
        releasedAt = prismNow()
        dragFrame = null
        now.doubleValue = releasedAt!!
        serial.intValue++
    }

    companion object {
        fun timeline(time: Double): Frame {
            val t = max(0.0, time) % LOOP
            val refold = 1 - smooth(segment(t, 5.9, 6.9))
            val returning = 1 - smooth(segment(t, 5.0, 5.9))
            return Frame(
                easeOut(segment(t, 1.4, 2.6)) * refold,
                smooth(segment(t, 1.55, 2.8)) * refold,
                easeOut(segment(t, 2.5, 3.55)) * returning,
                smooth(segment(t, 3.1, 3.65)) * returning,
                progress = clamp((t - 1.4) / 2.25) * (1 - smooth(segment(t, 5.0, 6.9))),
            )
        }
        fun scrub(progress: Double): Frame {
            val p = clamp(progress)
            return Frame(p, smooth(p), clamp((p - 0.45) / 0.55), smooth(segment(p, 0.7, 0.98)), progress = p)
        }
        fun blend(a: Frame, b: Frame, q: Double): Frame {
            fun mix(x: Double, y: Double) = x + (y - x) * q
            return Frame(mix(a.lid, b.lid), mix(a.spread, b.spread), mix(a.extraction, b.extraction), clamp(mix(a.ready, b.ready)), mix(a.tilt, b.tilt), clamp(mix(a.progress, b.progress)))
        }
        fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = min(hi, max(lo, v))
        fun segment(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
        fun smooth(q: Double) = q * q * (3 - 2 * q)
        fun easeOut(q: Double) = 1 - (1 - q).pow(3)
        fun spring(time: Double): Double {
            if (time >= 0.65) return 1.0
            val omega = 2 * PI / 0.42
            val damping = 0.82
            val damped = omega * sqrt(1 - damping * damping)
            fun response(t: Double) = 1 - exp(-damping * omega * t) * (cos(damped * t) + damping * omega / damped * sin(damped * t))
            return response(max(0.0, time)) / response(0.65)
        }
    }
}

/** Static surfaces, typography, grain and soft shadows are rasterized once at 3×. */
private class PrismUnsealSheetArtwork(topShift: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val grain = prismRaster(180f, 140f) { c ->
        c.drawColor(NativeColor.WHITE)
        val p = Paint().apply { color = prismColor(0x1F1F1F) }
        repeat(1800) { i ->
            p.alpha = ((i * 17) % 70 * 2.55f).toInt()
            val x = ((i * 73 + 19) % 180).toFloat()
            val y = ((i * 43 + i / 11) % 140).toFloat()
            c.drawRect(x, y, x + 1, y + 1, p)
        }
    }
    private val background = prismRaster(393f, 852f) { c ->
        c.drawColor(prismColor(0x111216))
        prismText(c, "Prism", 26f, 80f + topShift, 28f, 600, 0xFAF9FC)
        prismFill(c, 331f, 80f + topShift, 36f, 36f, 18f, 0x2C2A34)
        prismText(c, "P", 349f, 98f + topShift, 14f, 500, 0xE6E3EC, centered = true)
        prismText(c, "EVERYDAY WALLET", 26f, 144f + topShift, 11f, 500, 0x9B9DA6, tracking = 2f)
        prismText(c, "$2,840.50", 26f, 171f + topShift, 42f, 500, 0xF4F2F6)
        prismFill(c, 26f, 239f + topShift, 341f, 90f, 20f, 0x22242B)
        val iris = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(61.5f, 284f + topShift, intArrayOf(prismColor(0xCADC76), prismColor(0xF3CB84), prismColor(0xE25277), prismColor(0x7860AB), prismColor(0xCADC76)), null)
        }
        c.drawCircle(61.5f, 284f + topShift, 17.5f, iris)
        c.drawCircle(61.5f, 284f + topShift, 7.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = prismColor(0x22242B) })
        prismText(c, "Your next everyday card", 93f, 266.5f + topShift, 13f, 500, 0xE8E6ED)
        prismText(c, "Made for the way you move", 93f, 288f + topShift, 11f, 400, 0x9797A1)
        val arrow = NativePath().apply { moveTo(338f, 288f + topShift); lineTo(345f, 281f + topShift); moveTo(339f, 281f + topShift); lineTo(345f, 281f + topShift); lineTo(345f, 287f + topShift) }
        c.drawPath(arrow, prismStroke(0x86848F, 1.2f))
        c.drawRect(0f, 0f, 393f, 364f, Paint().apply { color = NativeColor.BLACK; alpha = 66 })
        val sheet = NativePath().apply {
            moveTo(0f, 396f); quadTo(0f, 364f, 32f, 364f); lineTo(361f, 364f)
            quadTo(393f, 364f, 393f, 396f); lineTo(393f, 852f); lineTo(0f, 852f); close()
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = prismColor(0xFAF9FC) }
        p.setShadowLayer(32f, 0f, 16f, prismColor(0x000000, 0.18f)); c.drawPath(sheet, p)
        p.setShadowLayer(4f, 0f, 2f, prismColor(0x000000, 0.16f)); c.drawPath(sheet, p)
        p.clearShadowLayer(); c.drawPath(sheet, p)
        prismFill(c, 177f, 375f, 39f, 4f, 2f, 0xD9D8DF)
        prismFill(c, 343f, 385f, 30f, 30f, 15f, 0xEEEAF0)
        c.drawLine(355f, 397f, 361f, 403f, prismStroke(0x77747F, 1.1f))
        c.drawLine(361f, 397f, 355f, 403f, prismStroke(0x77747F, 1.1f))
        prismText(c, "PRISM / CARD DELIVERY", 196.5f, 409f, 10f, 500, 0x93909A, 2f, true)
        prismFill(c, 26f, 748f, 341f, 50f, 14f, 0xEEEAF2)
        c.drawRoundRect(26.5f, 748.5f, 366.5f, 797.5f, 13.5f, 13.5f, prismStroke(0xFFFFFF, 1f, 0.85f))
        prismText(c, "A small reveal. A new beginning.", 196.5f, 805f, 10f, 400, 0xAAA6B0, centered = true)
    }
    private val closedTitle = prismLabel("A little more possibility.", 365f, 34f, 25f, 600, 0x25242A)
    private val readyTitle = prismLabel("Your card is ready", 365f, 34f, 25f, 600, 0x25242A)
    private val closedCopy = prismLabel("A new everyday card.\nUnseal something good.", 330f, 40f, 13f, 400, 0x77717F, lineSpacing = 3f)
    private val readyCopy = prismLabel("Made for wherever you go.\nReady when you are.", 330f, 40f, 13f, 400, 0x77717F, lineSpacing = 3f)
    private val closedHint = prismLabel("Drag up to unseal", 330f, 18f, 11f, 400, 0x77717F)
    private val readyHint = prismLabel("A fresh start, in your hands", 330f, 18f, 11f, 400, 0x77717F)
    private val closedButton = prismLabel("Unseal card", 341f, 50f, 13f, 600, 0x3A3542)
    private val readyButton = prismLabel("All set", 341f, 50f, 13f, 600, 0x3A3542)
    private val rear = prismRaster(174f, 132f) { c ->
        val shape = prismRoundPath(174f, 132f, 8f)
        c.save(); c.clipPath(shape); rainbow(c, 174f, 132f); c.restore()
        c.drawPath(shape, prismStroke(0xFFFFFF, 0.8f, 0.5f))
    }
    private val rearShadow = prismShadow(174f, 132f, 8f, 10f, 6f, 0x554065, 0.12f)
    private val lid = prismRaster(174f, 118f) { c ->
        val shape = prismRoundPath(174f, 118f, 8f)
        c.save(); c.clipPath(shape)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 174f, 118f, intArrayOf(prismColor(0xF1F0F3), prismColor(0xE5E4E9), prismColor(0xF3F2F5), prismColor(0xDAD9DF)), floatArrayOf(0f, 0.28f, 0.54f, 1f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, 174f, 118f, p)
        grain(c, 174f, 118f, 0.04f)
        p.shader = LinearGradient(0f, 0f, 0f, 118f, intArrayOf(prismColor(0xFFFFFF, 0.95f), prismColor(0xB8B6C0, 0.55f)), null, Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.8f
        c.drawRoundRect(0.4f, 0.4f, 173.6f, 117.6f, 7.6f, 7.6f, p)
        val crease = NativePath().apply { moveTo(2f, 2f); lineTo(87f, 65f); lineTo(172f, 2f) }
        c.drawPath(crease, prismStroke(0xC3C0CC, 0.65f, 0.35f)); c.restore()
    }
    private val lidLabel = prismLabel("prism", 174f, 28f, 11f, 500, 0x92909A, tracking = 0.3f)
    private val lidHighlight = prismRaster(74f, 220f) { c ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(15f, 0f, 59f, 0f, intArrayOf(NativeColor.TRANSPARENT, prismColor(0xFFFFFF, 0.85f), NativeColor.TRANSPARENT), null, Shader.TileMode.CLAMP)
            maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRect(15f, 20f, 59f, 200f, p)
    }
    private val lidShadow = prismShadow(174f, 118f, 8f, 3f, 2f, 0x5D5367, 0.12f)
    private val card = prismRaster(150f, 94f) { c ->
        val shape = prismRoundPath(150f, 94f, 9f)
        c.save(); c.clipPath(shape)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 150f, 94f, intArrayOf(prismColor(0xFEFDFE), prismColor(0xE3E1E8), prismColor(0xF5F4F7), prismColor(0xD7D5DE)), floatArrayOf(0f, 0.48f, 0.7f, 1f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, 150f, 94f, p)
        grain(c, 150f, 94f, 0.025f)
        prismText(c, "prism", 13f, 12f, 12f, 600, 0x78777D)
        prismText(c, "EVERYDAY", 13f, 31f, 7f, 500, 0x78777D, 1.6f)
        prismText(c, "NOVA REED", 13f, 72f, 8f, 500, 0x78777D, 0.5f)
        c.drawRoundRect(112f, 65f, 130f, 78f, 3f, 3f, prismStroke(0xA6A0AD, 0.7f))
        val chip = NativePath().apply { moveTo(118f, 65f); lineTo(118f, 78f); moveTo(124f, 65f); lineTo(124f, 78f); moveTo(112f, 71.5f); lineTo(130f, 71.5f) }
        c.drawPath(chip, prismStroke(0xB6B0BF, 0.55f))
        repeat(3) { i ->
            val radius = 3f + i * 3f
            c.drawArc(RectF(131f - radius, 40f - radius, 131f + radius, 40f + radius), -48f, 96f, false, prismStroke(0x92909A, 0.75f))
        }
        c.drawRoundRect(0.4f, 0.4f, 149.6f, 93.6f, 8.6f, 8.6f, prismStroke(0xFFFFFF, 0.8f, 0.85f)); c.restore()
    }
    private val cardShadow = prismShadow(150f, 94f, 9f, 8f, 6f, 0x4A3F5A, 0.17f)
    private val pocketPath = NativePath().apply {
        moveTo(0f, 0f); lineTo(87f, 48f * 0.58f); lineTo(174f, 0f); lineTo(174f, 41f)
        quadTo(174f, 48f, 167f, 48f); lineTo(7f, 48f); quadTo(0f, 48f, 0f, 41f); close()
    }
    private val pocketRainbow = prismRaster(174f, 48f) { c -> rainbow(c, 174f, 48f) }
    private val pocketSilver = prismRaster(174f, 48f) { c ->
        c.drawRect(0f, 0f, 174f, 48f, Paint().apply { shader = LinearGradient(0f, 0f, 174f, 48f, intArrayOf(prismColor(0xF0EFF3), prismColor(0xDEDBE3)), null, Shader.TileMode.CLAMP) })
    }
    private val pocketDetails = prismRaster(174f, 48f) { c ->
        val v = NativePath().apply { moveTo(0f, 1f); lineTo(87f, 29f); lineTo(174f, 1f) }
        c.drawPath(v, prismStroke(0xFFFFFF, 0.8f, 0.6f))
        c.drawRect(0f, 43f, 174f, 48f, Paint().apply { color = prismColor(0xD75D99) })
        c.drawRect(0f, 0f, 174f, 48f, Paint().apply { shader = LinearGradient(0f, 0f, 174f, 48f, intArrayOf(prismColor(0xFFFFFF, 0.35f), NativeColor.TRANSPARENT, prismColor(0xA54585, 0.2f)), null, Shader.TileMode.CLAMP) })
    }
    private val pocketShadow = prismRaster(194f, 68f, -10f, -10f) { c ->
        c.translate(10f, 10f)
        c.drawPath(pocketPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = prismColor(0x66445E, 0.1f); setShadowLayer(2f, 0f, 2f, prismColor(0x66445E, 0.1f)) })
    }
    private val floorShadow = prismRaster(250f, 90f, -125f, -45f) { c ->
        c.drawOval(37f, 36.5f, 213f, 53.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = prismColor(0x54475E); maskFilter = BlurMaskFilter(11f, BlurMaskFilter.Blur.NORMAL) })
    }

    fun draw(c: NativeCanvas, pose: PrismUnsealSheetMotion.Frame) {
        val lid = pose.lid.toFloat()
        val spread = pose.spread.toFloat()
        val extracted = pose.extraction.toFloat()
        val ready = pose.ready.toFloat()
        val sleeveOpacity = (1 - PrismUnsealSheetMotion.smooth(PrismUnsealSheetMotion.segment(pose.extraction, 0.72, 1.0))).toFloat()
        drawRaster(c, background)
        val clearance = 1 - PrismUnsealSheetMotion.smooth(PrismUnsealSheetMotion.segment(pose.lid, 0.25, 0.65)).toFloat()
        drawCentered(c, closedTitle, 196.5f, 446f - 6f * ready, (1 - ready) * clearance)
        drawCentered(c, readyTitle, 196.5f, 452f - 6f * ready, ready)
        drawCentered(c, closedCopy, 196.5f, 488f - 6f * ready, (1 - ready) * clearance)
        drawCentered(c, readyCopy, 196.5f, 494f - 6f * ready, ready)

        c.save(); c.translate(196.5f, 671f + 18f * spread)
        c.scale((176f + 26f * spread) / 176f, (11f + 5f * spread) / 11f)
        drawRaster(c, floorShadow, alpha = (0.13f - 0.04f * spread) * sleeveOpacity); c.restore()
        c.save(); c.translate(109.5f, 538f - 22f * spread)
        drawRaster(c, rearShadow, alpha = spread * sleeveOpacity); drawRaster(c, rear, alpha = spread * sleeveOpacity); c.restore()
        if (lid >= 0.55f) drawLid(c, lid)
        c.save(); c.translate(196.5f, 616f - 44f * extracted)
        c.rotate(-4f * (1 - extracted) + pose.tilt.toFloat())
        val cardScale = 1 + 0.12f * extracted
        c.scale(cardScale, cardScale); c.translate(-75f, -47f)
        val cardAlpha = PrismUnsealSheetMotion.smooth(PrismUnsealSheetMotion.segment(pose.lid, 0.1, 0.4)).toFloat()
        drawRaster(c, cardShadow, alpha = extracted * cardAlpha)
        drawRaster(c, card, alpha = cardAlpha); c.restore()
        if (lid < 0.55f) drawLid(c, lid)
        c.save(); c.translate(109.5f, 615f + 18f * spread); c.rotate(3f * spread, 87f, 0f)
        // Fade the assembled pocket as a group, preserving its internal opaque blending.
        val pocketLayer = c.saveLayerAlpha(-12f, -12f, 186f, 60f, (sleeveOpacity * 255).toInt())
        drawRaster(c, pocketShadow)
        c.save(); c.clipPath(pocketPath)
        drawRaster(c, pocketRainbow); drawRaster(c, pocketSilver, alpha = 1f - spread); drawRaster(c, pocketDetails)
        c.restore(); c.drawPath(pocketPath, prismStroke(0xFFFFFF, 0.8f, 0.5f))
        c.restoreToCount(pocketLayer); c.restore()
        val oldLabel = max(0f, 1f - 2f * ready)
        val newLabel = max(0f, 2f * ready - 1f)
        drawCentered(c, closedHint, 196.5f, 715f, oldLabel)
        drawCentered(c, readyHint, 196.5f, 715f, newLabel)
        drawRaster(c, closedButton, 26f, 748f, oldLabel)
        drawRaster(c, readyButton, 26f, 748f, newLabel)
    }

    private fun drawLid(c: NativeCanvas, progress: Float) {
        val opacity = (1 - PrismUnsealSheetMotion.smooth(PrismUnsealSheetMotion.segment(progress.toDouble(), 0.72, 1.0))).toFloat()
        if (opacity <= 0f) return
        // Swift's perspective 0.55, projected around the sleeve's top hinge in design points.
        val angle = -155f * progress * PI / 180
        if (abs(cos(angle)) < 0.001) return
        val eye = 174f / 0.55f
        val factor = (eye / (eye - 118 * sin(angle))).toFloat()
        val lowerY = (118 * cos(angle) * factor).toFloat()
        val side = 87f * factor
        matrix.setPolyToPoly(floatArrayOf(0f, 0f, 174f, 0f, 174f, 118f, 0f, 118f), 0,
            floatArrayOf(0f, 0f, 174f, 0f, 87f + side, lowerY, 87f - side, lowerY), 0, 4)
        c.save(); c.translate(109.5f, 545f); c.concat(matrix)
        drawRaster(c, lidShadow, alpha = opacity)
        drawRaster(c, lid, alpha = opacity)
        c.save(); c.clipPath(prismRoundPath(174f, 118f, 8f))
        c.translate(87f - 65f + 135f * progress, 59f); c.rotate(22f)
        drawRaster(c, lidHighlight, -37f, -110f, opacity); c.restore()
        val labelAlpha = (1 - PrismUnsealSheetMotion.smooth(PrismUnsealSheetMotion.segment(progress.toDouble(), 0.35, 0.55))).toFloat()
        drawRaster(c, lidLabel, 0f, 31f, labelAlpha * opacity)
        c.restore()
    }

    private fun rainbow(c: NativeCanvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, w, h, intArrayOf(prismColor(0xCADC76), prismColor(0xF3CB84), prismColor(0xE25277), prismColor(0x7860AB)), floatArrayOf(0f, 0.32f, 0.66f, 1f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w, 0f, 145f, intArrayOf(prismColor(0xB5CFED, 0.9f), NativeColor.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(prismColor(0xFFFFFF, 0.32f), NativeColor.TRANSPARENT, prismColor(0xB04C9D, 0.18f)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        grain(c, w, h, 0.035f)
    }

    private fun grain(c: NativeCanvas, w: Float, h: Float, alpha: Float) {
        // Porter-Duff multiply also multiplies alpha. Keep this texture opaque and mix its
        // RGB with white, so subtle grain never makes the underlying metal translucent.
        val white = 255f * (1f - alpha)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(floatArrayOf(
                alpha, 0f, 0f, 0f, white,
                0f, alpha, 0f, 0f, white,
                0f, 0f, alpha, 0f, white,
                0f, 0f, 0f, 1f, 0f,
            ))
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
        c.drawBitmap(grain.bitmap, null, RectF(0f, 0f, w, h), p)
    }
    private fun drawCentered(c: NativeCanvas, raster: PrismUnsealSheetRaster, x: Float, y: Float, alpha: Float) = drawRaster(c, raster, x - raster.width / 2, y - raster.height / 2, alpha)
    private fun drawRaster(c: NativeCanvas, raster: PrismUnsealSheetRaster, x: Float = 0f, y: Float = 0f, alpha: Float = 1f) {
        if (alpha <= 0f) return
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        c.save(); c.translate(x + raster.originX, y + raster.originY); c.scale(1f / 3, 1f / 3)
        c.drawBitmap(raster.bitmap, 0f, 0f, paint); c.restore()
    }
}

private data class PrismUnsealSheetRaster(val bitmap: Bitmap, val width: Float, val height: Float, val originX: Float = 0f, val originY: Float = 0f)

private fun prismRaster(width: Float, height: Float, originX: Float = 0f, originY: Float = 0f, draw: (NativeCanvas) -> Unit): PrismUnsealSheetRaster {
    val bitmap = Bitmap.createBitmap(ceil(width * 3).toInt(), ceil(height * 3).toInt(), Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = NativeCanvas(bitmap)
    canvas.scale(3f, 3f)
    draw(canvas)
    return PrismUnsealSheetRaster(bitmap, width, height, originX, originY)
}

private fun prismLabel(text: String, width: Float, height: Float, size: Float, weight: Int, color: Int, tracking: Float = 0f, lineSpacing: Float = 0f) = prismRaster(width, height) { c ->
    val lines = text.split('\n')
    val p = prismTextPaint(size, weight, color)
    val lineHeight = p.fontMetrics.descent - p.fontMetrics.ascent
    val firstY = height / 2 - (lines.size - 1) * (lineHeight + lineSpacing) / 2
    lines.forEachIndexed { index, line -> prismText(c, line, width / 2, firstY + index * (lineHeight + lineSpacing), size, weight, color, tracking, true) }
}

private fun prismTextPaint(size: Float, weight: Int, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    textSize = size
    this.color = prismColor(color)
    typeface = if (Build.VERSION.SDK_INT >= 28) {
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
    } else {
        Typeface.create(if (weight >= 500) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }
}

private fun prismText(c: NativeCanvas, text: String, x: Float, y: Float, size: Float, weight: Int, color: Int, tracking: Float = 0f, centered: Boolean = false) {
    val p = prismTextPaint(size, weight, color)
    val baseline = if (centered) y - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2 else y - p.fontMetrics.ascent
    val width = p.measureText(text) + max(0, text.length - 1) * tracking
    var cursor = if (centered) x - width / 2 else x
    if (tracking == 0f) c.drawText(text, cursor, baseline, p)
    else text.forEach { char ->
        val glyph = char.toString()
        c.drawText(glyph, cursor, baseline, p)
        cursor += p.measureText(glyph) + tracking
    }
}

private fun prismColor(hex: Int, alpha: Float = 1f) = NativeColor.argb((alpha * 255).toInt().coerceIn(0, 255), (hex shr 16) and 255, (hex shr 8) and 255, hex and 255)
private fun prismStroke(hex: Int, width: Float, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = prismColor(hex, alpha); style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
}
private fun prismFill(c: NativeCanvas, x: Float, y: Float, width: Float, height: Float, radius: Float, hex: Int) {
    c.drawRoundRect(x, y, x + width, y + height, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = prismColor(hex) })
}
private fun prismRoundPath(w: Float, h: Float, r: Float) = NativePath().apply { addRoundRect(0f, 0f, w, h, r, r, NativePath.Direction.CW) }
private fun prismShadow(w: Float, h: Float, r: Float, blur: Float, y: Float, color: Int, alpha: Float): PrismUnsealSheetRaster {
    val pad = blur * 3 + abs(y) + 2
    return prismRaster(w + pad * 2, h + pad * 2, -pad, -pad) { c ->
        c.drawRoundRect(pad, pad + y, pad + w, pad + h + y, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = prismColor(color, alpha); maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        })
    }
}

/** Observe runtime changes too, so Remove animations stops the clock without reopening the block. */
@Composable
private fun prismReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun PrismUnsealSheetSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = true
        onDispose { controller.isAppearanceLightStatusBars = status; controller.isAppearanceLightNavigationBars = navigation }
    }
}
