package com.ngocdevv.nativecook.blocks.forms.donationsphereheart

import android.content.Context
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.isActive
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val LOOP = 10.0
private fun donationNow() = System.nanoTime() / 1_000_000_000.0
private fun donationSmooth(x: Double): Double { val p = x.coerceIn(0.0, 1.0); return p * p * (3.0 - 2.0 * p) }
private fun donationMix(a: Double, b: Double, p: Double) = a + (b - a) * p
private data class DonationSphereHeartPose(val medium: Double, val dense: Double, val sparse: Double, val scale: Double)
private data class DonationSphereHeartDrive(val time: Double, val yaw: Double)
private data class DonationSphereHeartSphere(val x: Double, val y: Double, val z: Double, val radius: Double, val material: Int)
private val donationPoseKeys = arrayOf(
    doubleArrayOf(0.0, 1.0, 0.0, 0.0, 1.0),
    doubleArrayOf(0.15, 1.0, 0.0, 0.0, 1.0),
    doubleArrayOf(1.3, 1.0, 0.0, 0.0, 0.82),
    doubleArrayOf(1.5, 0.0, 1.0, 0.0, 1.1),
    doubleArrayOf(3.08, 0.0, 1.0, 0.0, 0.82),
    doubleArrayOf(3.28, 0.0, 0.0, 1.0, 1.0),
    doubleArrayOf(4.56, 0.0, 0.0, 1.0, 0.76),
    doubleArrayOf(4.8, 1.0, 0.0, 0.0, 1.02),
    doubleArrayOf(6.26, 1.0, 0.0, 0.0, 0.82),
    doubleArrayOf(6.48, 0.0, 1.0, 0.0, 1.1),
    doubleArrayOf(7.95, 0.0, 1.0, 0.0, 0.82),
    doubleArrayOf(8.18, 0.0, 0.0, 1.0, 1.0),
    doubleArrayOf(9.17, 0.0, 0.0, 1.0, 0.85),
    doubleArrayOf(9.75, 1.0, 0.0, 0.0, 1.0),
    doubleArrayOf(10.0, 1.0, 0.0, 0.0, 1.0),
)
private fun donationPose(time: Double): DonationSphereHeartPose {
    val t = max(0.0, time) % LOOP
    for (i in 1 until donationPoseKeys.size) {
        if (t > donationPoseKeys[i][0]) continue
        val a = donationPoseKeys[i - 1]; val b = donationPoseKeys[i]
        val f = donationSmooth((t - a[0]) / (b[0] - a[0]))
        return DonationSphereHeartPose(donationMix(a[1],b[1],f),donationMix(a[2],b[2],f),donationMix(a[3],b[3],f),donationMix(a[4],b[4],f))
    }
    return DonationSphereHeartPose(1.0,0.0,0.0,1.0)
}
private class DonationSphereHeartMotion {
    var epoch = donationNow()
    var dragging = false
    var captured = 0.0
    var time = 0.0
    var yaw = 0.0
    var velocity = 0.0
    var released: Double? = null
    fun drive(now: Double, autoPlay: Boolean, reduced: Boolean, renderTime: Double?): DonationSphereHeartDrive {
        if (reduced) return DonationSphereHeartDrive(0.0, 0.0)
        renderTime?.let { return DonationSphereHeartDrive(it, 0.0) }
        if (dragging) return DonationSphereHeartDrive(time, yaw)
        released?.let { stamp ->
            val age = max(0.0, now - stamp)
            val carried = time + velocity * 0.14 * (1.0 - exp(-age / 0.14))
            if (age < 2.0) return DonationSphereHeartDrive(carried, yaw * exp(-5.0 * age))
            if (age < 2.65) return DonationSphereHeartDrive(carried * (1.0 - donationSmooth((age - 2.0) / 0.65)), 0.0)
            return DonationSphereHeartDrive(if (autoPlay) age - 2.65 else 0.0, 0.0)
        }
        return DonationSphereHeartDrive(if (autoPlay) max(0.0, now - epoch) else 0.0, 0.0)
    }
    fun reset() { dragging = false; released = null; time = 0.0; yaw = 0.0; epoch = donationNow() }
}

/** A faithful port of the approved Swift donation screen and its sphere-density sequence. */
@Composable
fun DonationSphereHeart(autoPlay: Boolean = false, renderTime: Double? = null, modifier: Modifier = Modifier) {
    val reduced = rememberDonationSphereHeartReducedMotion()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val motion = remember { DonationSphereHeartMotion() }
    val clock = remember { mutableDoubleStateOf(donationNow()) }
    var wake by remember { mutableIntStateOf(0) }
    val currentAuto = rememberUpdatedState(autoPlay)
    val currentReduced = rememberUpdatedState(reduced)
    val currentRenderTime = rememberUpdatedState(renderTime)
    val painter = remember(context) { DonationSphereHeartPainter(context) }
    DisposableEffect(painter) { onDispose { painter.close() } }
    DonationSphereHeartSystemBars()
    LaunchedEffect(reduced) { motion.reset(); clock.doubleValue = donationNow(); wake++ }
    LaunchedEffect(autoPlay, reduced, renderTime, wake) {
        clock.doubleValue = donationNow()
        if (reduced || renderTime != null || (!autoPlay && !motion.dragging && motion.released == null)) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { clock.doubleValue = it / 1_000_000_000.0 }
            val stamp = motion.released
            if (!motion.dragging && stamp != null && clock.doubleValue - stamp >= 2.65) {
                motion.epoch = stamp + 2.65; motion.released = null; motion.time = 0.0; motion.yaw = 0.0
                if (!autoPlay) break
            }
        }
    }
    val reset: () -> Unit = { motion.reset(); clock.doubleValue = donationNow(); wake++ }
    val demonstrate: () -> Unit = {
        if (currentRenderTime.value == null) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (!currentReduced.value) {
                motion.time = 1.55; motion.yaw = 0.0; motion.velocity = 0.0; motion.dragging = false
                motion.released = donationNow(); clock.doubleValue = donationNow(); wake++
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = maxWidth.value / 393f
        val pixelScale = scale * density.density
        val logicalHeight = maxHeight.value / scale
        val lowerShift = max(0f, logicalHeight - 852f)
        Box(Modifier.fillMaxSize().pointerInput(pixelScale, autoPlay, reduced, renderTime) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x = down.position.x / pixelScale
                val y = down.position.y / pixelScale
                if (currentReduced.value || currentRenderTime.value != null || x < 15 || x > 378 ||
                    y < 260 + lowerShift * 0.34 || y > 622 + lowerShift * 0.34) {
                    waitForUpOrCancellation(); return@awaitEachGesture
                }
                val now = donationNow()
                val initial = motion.drive(now, currentAuto.value, currentReduced.value, currentRenderTime.value)
                motion.captured = initial.time % LOOP; motion.time = motion.captured; motion.yaw = 0.0
                motion.dragging = true; motion.released = null
                val origin = down.position
                val tracker = VelocityTracker().also { it.addPosition(down.uptimeMillis, down.position) }
                var dx = 0.0; var dy = 0.0
                clock.doubleValue = now; wake++; down.consume()
                try {
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dx = ((change.position.x - origin.x) / pixelScale).toDouble()
                        dy = ((change.position.y - origin.y) / pixelScale).toDouble()
                        motion.time = motion.captured + min(3.3, hypot(dx, dy) / 43.0)
                        motion.yaw = (dx / 300.0).coerceIn(-0.55, 0.55)
                        clock.doubleValue = donationNow(); pressed = change.pressed; change.consume()
                    }
                } finally {
                    if (motion.dragging && !currentReduced.value && currentRenderTime.value == null) {
                        val velocity = tracker.calculateVelocity()
                        motion.velocity = min(3.0, hypot(velocity.x.toDouble(), velocity.y.toDouble()) * 0.2 / pixelScale / 43.0)
                        if (hypot(dx * scale, dy * scale) < 8) motion.time = 1.55
                        motion.dragging = false; motion.released = donationNow(); clock.doubleValue = donationNow(); wake++
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    } else motion.dragging = false
                }
            }
        }) {
            // The static screen never observes the frame clock.
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { out ->
                    val canvas = out.nativeCanvas
                    canvas.save(); canvas.scale(pixelScale, pixelScale)
                    painter.drawStatic(canvas, lowerShift.toDouble())
                    canvas.restore()
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val current = motion.drive(clock.doubleValue, currentAuto.value, currentReduced.value, currentRenderTime.value)
                drawIntoCanvas { out ->
                    val canvas = out.nativeCanvas
                    canvas.save(); canvas.scale(pixelScale, pixelScale)
                    painter.drawHeart(canvas, current.time, current.yaw, 440.0 + lowerShift * 0.34)
                    canvas.restore()
                }
            }
            Box(Modifier.offset((8 * scale).dp, (53 * scale).dp).size((40 * scale).dp)
                .semantics { contentDescription = "Reset donation heart" }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = reset))
            Box(Modifier.offset((26 * scale).dp, (116 * scale).dp).size((341 * scale).dp, (84 * scale).dp)
                .semantics { contentDescription = "Donate with a tap"; heading() })
            Box(Modifier.offset((15 * scale).dp, ((260 + lowerShift * 0.34f) * scale).dp).size((363 * scale).dp, (362 * scale).dp)
                .semantics { contentDescription = "Glass sphere heart. Drag to gather the spheres or spread them apart."; role = Role.Button; onClick { demonstrate(); true } })
            Box(Modifier.offset((26 * scale).dp, ((631 + lowerShift) * scale).dp).size((341 * scale).dp, (130 * scale).dp)
                .semantics { contentDescription = "Feeling generous? Send a one-time donation. Prefer a bit more planning? Set it up monthly. Give a little, but often? Chip in your change." })
            Box(Modifier.offset((16 * scale).dp, ((759 + lowerShift) * scale).dp).size((361 * scale).dp, (48 * scale).dp)
                .semantics { contentDescription = "Donate now. Play the sphere heart animation." }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = demonstrate))
        }
    }
}

@Composable
private fun rememberDonationSphereHeartReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
@Composable
private fun DonationSphereHeartSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true; controller.isAppearanceLightNavigationBars = true
        controller.show(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.isAppearanceLightStatusBars = oldStatus; controller.isAppearanceLightNavigationBars = oldNavigation }
    }
}

private class DonationSphereHeartPainter(context: Context) {
    private val atlas = requireNotNull(context.assets.open("donationsphereheart/donationsphereheart_spheres.png").use { BitmapFactory.decodeStream(it) })
    private val tables = JSONObject(context.assets.open("donationsphereheart/donationsphereheart_spheres.json").bufferedReader().use { it.readText() })
    private fun table(name: String): List<DonationSphereHeartSphere> {
        val data = tables.getJSONArray(name)
        return (0 until data.length()).map { i ->
            val o = data.getJSONObject(i)
            DonationSphereHeartSphere(o.getDouble("x"), o.getDouble("y"), o.getDouble("z"), o.getDouble("radius"), o.getInt("material"))
        }
    }
    private val medium = table("medium")
    private val dense = table("dense")
    private val sparse = table("sparse")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private val ink = NativeColor.rgb(14, 14, 15)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val black = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val mediumFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    fun close() { atlas.recycle() }
    fun drawHeart(canvas: NativeCanvas, time: Double, yaw: Double, centerY: Double) {
        val t = max(0.0, time) % LOOP
        val p = donationPose(t)
        val envelope = donationSmooth(t / 0.5) * (1.0 - donationSmooth((t - 9.17) / 0.58))
        val turn = 0.13 * sin(2 * PI * t / 4.8) * envelope + yaw
        val roll = 0.028 * sin(4 * PI * t / 4.8) * envelope
        val spheres = ArrayList<DonationSphereHeartSphere>(120)
        for (i in 0 until 120) {
            val m = medium[i % 48]; val d = dense[i]; val s = sparse[i % 12]
            val radius = ((if (i < 48) m.radius else 0.0) * p.medium + d.radius * p.dense + (if (i < 12) s.radius else 0.0) * p.sparse) * p.scale
            if (radius <= 0.25) continue
            val x = (m.x * p.medium + d.x * p.dense + s.x * p.sparse) * p.scale
            val y = (m.y * p.medium + d.y * p.dense + s.y * p.sparse) * p.scale
            val z = m.z * p.medium + d.z * p.dense + s.z * p.sparse
            val rx = x * cos(turn) + z * sin(turn); val rz = z * cos(turn) - x * sin(turn)
            val depth = 760.0 / (760.0 - rz)
            spheres += DonationSphereHeartSphere((rx * cos(roll) - y * sin(roll)) * depth, (rx * sin(roll) + y * cos(roll)) * depth,
                rz, radius * depth, if (i < 48) m.material else d.material)
        }
        paint.alpha = 255; paint.shader = null; paint.clearShadowLayer()
        for (sphere in spheres.sortedBy { it.z }) {
            val extent = sphere.radius * 256.0 / 112.0
            source.set(sphere.material * 256, 0, sphere.material * 256 + 256, 256)
            destination.set((196.5 + sphere.x - extent / 2).toFloat(), (centerY + sphere.y - extent / 2).toFloat(),
                (196.5 + sphere.x + extent / 2).toFloat(), (centerY + sphere.y + extent / 2).toFloat())
            canvas.drawBitmap(atlas, source, destination, paint)
        }
    }
    private fun text(canvas: NativeCanvas, value: String, x: Float, top: Float, size: Float, face: Typeface = normal, tracking: Float = 0f, centered: Boolean = false) {
        paint.color = ink; paint.alpha = 255; paint.shader = null; paint.clearShadowLayer(); paint.typeface = face; paint.textSize = size
        val widths = FloatArray(value.length); paint.getTextWidths(value, widths)
        val width = widths.sum() + tracking * max(0, value.length - 1)
        var cursor = if (centered) x - width / 2 else x
        val baseline = top - paint.fontMetrics.ascent
        if (tracking == 0f) canvas.drawText(value, cursor, baseline, paint)
        else for (i in value.indices) { canvas.drawText(value.substring(i, i + 1), cursor, baseline, paint); cursor += widths[i] + tracking }
    }
    fun drawStatic(canvas: NativeCanvas, shift: Double) {
        canvas.drawColor(NativeColor.rgb(248, 246, 250))
        paint.alpha = 255; paint.shader = null; paint.clearShadowLayer(); paint.style = Paint.Style.FILL; paint.color = NativeColor.WHITE
        canvas.drawRoundRect(14f, 94f, 379f, (604 + shift * .34).toFloat(), 16f, 16f, paint)
        canvas.drawRoundRect(14f, (620 + shift).toFloat(), 379f, (824 + shift).toFloat(), 16f, 16f, paint)
        paint.color = ink; paint.alpha = 255; paint.strokeWidth = 1.3f; paint.style = Paint.Style.STROKE
        canvas.drawLine(23f, 68f, 33f, 78f, paint); canvas.drawLine(33f, 68f, 23f, 78f, paint)
        paint.style = Paint.Style.FILL
        text(canvas, "DONATE WITH A", 26f, 116f, 35f, black, -0.8f)
        text(canvas, "TAP", 26f, 156f, 35f, black, -0.8f)
        val options = listOf("Feeling generous? Send a one-time donation", "Prefer a bit more planning? Set it up monthly", "Give a little, but often? Chip in your change")
        options.forEachIndexed { i, label ->
            val y = (645 + i * 51 + shift).toFloat()
            icon(canvas, i, 26f, y - 8.5f)
            text(canvas, label, 59f, y - 7.8f, 13.2f)
        }
        val y = (759 + shift).toFloat()
        paint.color = NativeColor.rgb(24, 27, 27); paint.setShadowLayer(13f, 0f, 9f, NativeColor.argb(26, 0, 0, 0))
        canvas.drawRoundRect(16f, y, 377f, y + 48f, 24f, 24f, paint)
        paint.setShadowLayer(3f, 0f, 2f, NativeColor.argb(38, 0, 0, 0)); canvas.drawRoundRect(16f, y, 377f, y + 48f, 24f, 24f, paint)
        paint.clearShadowLayer(); paint.color = NativeColor.WHITE; paint.typeface = mediumFont; paint.textSize = 13.5f
        canvas.drawText("Donate now", 196.5f - paint.measureText("Donate now") / 2, y + 24f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2, paint)
    }
    private fun icon(canvas: NativeCanvas, index: Int, x: Float, y: Float) {
        canvas.save(); canvas.translate(x, y); canvas.scale(17f / 18f, 17f / 18f)
        paint.color = ink; paint.alpha = 255; paint.clearShadowLayer(); paint.style = Paint.Style.FILL
        if (index == 0) {
            val p = Path(); p.moveTo(2f, 2.6f); p.lineTo(16.1f, 8f); p.lineTo(2f, 15.4f); p.lineTo(4.4f, 9.8f); p.lineTo(11.3f, 8f); p.lineTo(4.4f, 6.3f); p.close(); canvas.drawPath(p, paint)
        } else if (index == 1) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f
            canvas.drawRoundRect(2.2f, 3.5f, 15.8f, 15.8f, 1.5f, 1.5f, paint)
            canvas.drawLine(2.3f, 7f, 15.5f, 7f, paint); canvas.drawLine(5.3f, 1.8f, 5.3f, 5.2f, paint); canvas.drawLine(12.7f, 1.8f, 12.7f, 5.2f, paint)
            paint.style = Paint.Style.FILL

        } else for (column in 0..1) for (row in 0..2) {
            val left = 2f + column * 8f; val top = 2f + row * 4f + column * 4f
            canvas.drawOval(left, top, left + 6f, top + 3f, paint)
        }
        canvas.restore()
    }
}
