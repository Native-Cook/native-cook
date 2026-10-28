package com.ngocdevv.nativecook.blocks.cards.auricredeempoints

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private object AuricReferenceFit {
    const val COIN_SCALE = 0.8021340693
    const val COIN_OFFSET_X = -0.192986
    const val COIN_OFFSET_Y = 0.831597
    const val HEADPHONES_SCALE = 0.9448726501
    const val HEADPHONES_OFFSET_X = 0.2
    const val HEADPHONES_OFFSET_Y = 1.8963235294
    const val SNEAKERS_SCALE = 1.0373679775
    const val SNEAKERS_OFFSET_X = -3.802741573
    const val SNEAKERS_OFFSET_Y = -4.887213483
}

private object AuricMotion {
    const val LOOP = 8.0

    data class Pose(
        val offsetX: Double = 0.0,
        val lift: Double = 0.0,
        val yaw: Double = 0.0,
        val pitch: Double = 0.0,
        val roll: Double = 0.0,
        val scaleX: Double = 1.0,
        val scaleY: Double = 1.0,
        val sweep: Double = -1.0,
        val light: Double = 0.0,
        val morph: Double = 0.0,
        val streak: Double = 0.0,
        val eventAge: Double = -1.0,
    )

    data class Contact(val origin: Pose, val startedAt: Double, val x: Double = 0.0, val y: Double = 0.0)
    data class Velocity(val offsetX: Double = 0.0, val lift: Double = 0.0, val yaw: Double = 0.0, val pitch: Double = 0.0, val roll: Double = 0.0, val scaleX: Double = 0.0, val scaleY: Double = 0.0)
    data class Release(val origin: Pose, val startedAt: Double, val velocity: Velocity, val celebrate: Boolean)

    fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0) = value.coerceIn(low, high)
    fun smooth(value: Double): Double { val t = clamp(value); return t * t * (3.0 - 2.0 * t) }
    fun pulse(time: Double, start: Double, duration: Double): Double {
        val t = (time - start) / duration
        return if (t > 0.0 && t < 1.0) sin(PI * t) * sin(PI * t) else 0.0
    }
    fun rubber(value: Double, limit: Double): Double {
        val amount = abs(value)
        val excess = max(0.0, amount - limit)
        return (if (value < 0.0) -1.0 else 1.0) * (min(amount, limit) + 4.0 * excess / (excess + 20.0))
    }
    fun rubberDerivative(value: Double, limit: Double): Double {
        val excess = abs(value) - limit
        return if (excess <= 0.0) 1.0 else 80.0 / ((excess + 20.0) * (excess + 20.0))
    }
    fun dragAngle(origin: Double, displacement: Double, limit: Double, hardLimit: Double): Double = clamp(origin + rubber(origin + displacement, limit) - rubber(origin, limit), -hardLimit, hardLimit)
    fun dragVelocity(origin: Double, displacement: Double, velocity: Double, limit: Double, hardLimit: Double): Double {
        val raw = origin + rubber(origin + displacement, limit) - rubber(origin, limit)
        return if (abs(raw) >= hardLimit) 0.0 else velocity * rubberDerivative(origin + displacement, limit)
    }
    fun spring(time: Double, from: Double, velocity: Double = 0.0): Double {
        if (time >= 1.2) return 0.0
        val omega = 16.0; val damping = 0.8; val decay = omega * damping; val frequency = omega * sqrt(1.0 - damping * damping)
        return exp(-decay * time) * (from * cos(frequency * time) + (velocity + decay * from) / frequency * sin(frequency * time))
    }
    fun released(time: Double, origin: Pose, velocity: Velocity, celebrate: Boolean): Pose {
        val t = max(0.0, time)
        val flash = if (celebrate) pulse(t, 0.04, 0.38) else 0.0
        val liftPulse = if (celebrate) pulse(t, 0.0, 0.66) else 0.0
        val expansion = if (celebrate) 0.055 * pulse(t, 0.04, 0.48) else 0.0
        return Pose(
            offsetX = spring(t, origin.offsetX, clamp(velocity.offsetX, -240.0, 240.0)),
            lift = spring(t, origin.lift, clamp(velocity.lift, -160.0, 160.0)) - 16.0 * liftPulse,
            yaw = clamp(spring(t, origin.yaw, clamp(velocity.yaw, -280.0, 280.0)), -24.0, 24.0),
            pitch = clamp(spring(t, origin.pitch, clamp(velocity.pitch, -160.0, 160.0)), -14.0, 14.0),
            roll = spring(t, origin.roll, clamp(velocity.roll, -160.0, 160.0)),
            scaleX = 1.0 + spring(t, origin.scaleX - 1.0, velocity.scaleX) + expansion,
            scaleY = 1.0 + spring(t, origin.scaleY - 1.0, velocity.scaleY) + expansion,
            sweep = origin.sweep + (1.0 - origin.sweep) * smooth(t / 0.38),
            light = max(origin.light * (1.0 - smooth(t / 0.42)), 0.45 * flash),
            morph = origin.morph * (1.0 - smooth(t / 0.42)) + 0.65 * flash,
            streak = origin.streak * (1.0 - smooth(t / 0.48)) + 0.8 * flash,
            eventAge = if (celebrate) t else -1.0,
        )
    }
    fun preview(time: Double): Pose {
        val t = ((max(0.0, time) % LOOP) + LOOP) % LOOP
        if (t < 0.8) return Pose()
        val compressed = Pose(offsetX = -4.0, lift = 2.0, yaw = -10.0, roll = -8.0, scaleX = 1.045, scaleY = 0.9, morph = 0.7, streak = 0.4)
        if (t < 0.96) {
            val p = smooth((t - 0.8) / 0.16)
            return compressed.copy(offsetX = -4.0 * p, lift = 2.0 * p, yaw = -10.0 * p, roll = -8.0 * p, scaleX = 1.0 + 0.045 * p, scaleY = 1.0 - 0.1 * p, morph = 0.7 * p, streak = 0.4 * p)
        }
        if (t < 2.16) return released(t - 0.96, compressed, Velocity(offsetX = 120.0, yaw = 240.0, roll = 120.0), true)
        if (t < 6.8) {
            val u = (t - 2.16) / 4.64; val envelope = sin(PI * u).let { it * it }; val turn = sin(2.0 * PI * u) * envelope
            return Pose(offsetX = 18.0 * turn, lift = -10.0 * envelope, yaw = 24.0 * turn, pitch = 5.0 * envelope, roll = 12.0 * turn, scaleX = 1.0 + 0.025 * envelope, scaleY = 1.0 + 0.025 * envelope, sweep = turn, light = 0.34 * envelope, morph = 0.65 * envelope, streak = 0.35 * envelope)
        }
        return Pose()
    }
    fun pressed(contact: Contact, now: Double): Pose {
        val progress = 1.0 - (1.0 - clamp((now - contact.startedAt) / 0.1)).let { it * it * it }
        val p = contact.origin.copy(
            scaleX = contact.origin.scaleX + (1.045 - contact.origin.scaleX) * progress,
            scaleY = contact.origin.scaleY + (0.9 - contact.origin.scaleY) * progress,
        )
        val offsetX = dragAngle(contact.origin.offsetX, contact.x * 0.28, 20.0, 24.0)
        val lift = dragAngle(contact.origin.lift, contact.y * 0.18, 14.0, 18.0)
        val yaw = dragAngle(contact.origin.yaw, contact.x * 0.32, 20.0, 24.0)
        val pitch = dragAngle(contact.origin.pitch, -contact.y * 0.13, 10.0, 14.0)
        return p.copy(
            offsetX = offsetX,
            lift = lift,
            yaw = yaw,
            pitch = pitch,
            roll = dragAngle(contact.origin.roll, contact.x * 0.08, 8.0, 12.0),
            sweep = p.sweep + (yaw / 24.0 - p.sweep) * progress,
            light = p.light + (0.3 - p.light) * progress,
            morph = p.morph + (0.65 - p.morph) * progress,
            streak = p.streak + (0.4 - p.streak) * progress,
            eventAge = if (contact.origin.eventAge >= 0.0) contact.origin.eventAge + now - contact.startedAt else -1.0,
        )
    }
    fun pressedVelocity(contact: Contact, now: Double, velocityX: Double, velocityY: Double): Velocity {
        val u = clamp((now - contact.startedAt) / 0.1)
        val compressionSpeed = if (u < 1.0) 30.0 * (1.0 - u) * (1.0 - u) else 0.0
        return Velocity(
            offsetX = dragVelocity(contact.origin.offsetX, contact.x * 0.28, velocityX * 0.28, 20.0, 24.0),
            lift = dragVelocity(contact.origin.lift, contact.y * 0.18, velocityY * 0.18, 14.0, 18.0),
            yaw = dragVelocity(contact.origin.yaw, contact.x * 0.32, velocityX * 0.32, 20.0, 24.0),
            pitch = dragVelocity(contact.origin.pitch, -contact.y * 0.13, -velocityY * 0.13, 10.0, 14.0),
            roll = dragVelocity(contact.origin.roll, contact.x * 0.08, velocityX * 0.08, 8.0, 12.0),
            scaleX = (1.045 - contact.origin.scaleX) * compressionSpeed,
            scaleY = (0.9 - contact.origin.scaleY) * compressionSpeed,
        )
    }
    fun morphPath(x: Float, y: Float, width: Float, height: Float, amount: Double, rotation: Double = 0.0, scale: Double = 1.0): Path {
        val path = Path(); val cx = x + width / 2f; val cy = y + height / 2f
        val dx = width * 0.5f * clamp(amount).toFloat(); val dy = height * 0.5f * clamp(amount).toFloat()
        val radians = Math.toRadians(rotation); val c = cos(radians).toFloat(); val s = sin(radians).toFloat()
        fun point(px: Float, py: Float): Offset {
            val sx = cx + (px - cx) * scale.toFloat(); val sy = cy + (py - cy) * scale.toFloat()
            val dxp = sx - cx; val dyp = sy - cy
            return Offset(cx + dxp * c - dyp * s, cy + dxp * s + dyp * c)
        }
        val top = point(cx, y); val right = point(x + width, cy); val bottom = point(cx, y + height); val left = point(x, cy)
        val ctrls = listOf(point(cx + dx, cy - dy), point(cx + dx, cy + dy), point(cx - dx, cy + dy), point(cx - dx, cy - dy))
        path.moveTo(top.x, top.y)
        path.quadraticTo(ctrls[0].x, ctrls[0].y, right.x, right.y)
        path.quadraticTo(ctrls[1].x, ctrls[1].y, bottom.x, bottom.y)
        path.quadraticTo(ctrls[2].x, ctrls[2].y, left.x, left.y)
        path.quadraticTo(ctrls[3].x, ctrls[3].y, top.x, top.y)
        path.close()
        return path
    }
}

private suspend fun loadAuricArt(context: android.content.Context): Map<String, ImageBitmap> = withContext(Dispatchers.IO) {
    val sizes = listOf("coin" to 1140, "headphones" to 960, "sneakers" to 960)
    sizes.mapNotNull { (name, maxEdge) -> decodeAuricAsset(context, name, maxEdge)?.let { name to it } }.toMap()
}

private fun decodeAuricAsset(context: android.content.Context, name: String, maxEdge: Int): ImageBitmap? {
    val path = "auricredeempoints/auricredeempoints_$name.png"
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.assets, path)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val factor = min(1f, maxEdge.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * factor).toInt(), (info.size.height * factor).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        val factor = min(1f, maxEdge.toFloat() / maxOf(bounds.outWidth, bounds.outHeight))
        val targetWidth = (bounds.outWidth * factor).toInt(); val targetHeight = (bounds.outHeight * factor).toInt()
        var sample = 1
        while (maxOf(bounds.outWidth / (sample * 2), bounds.outHeight / (sample * 2)).toFloat() >= maxOf(targetWidth, targetHeight) * 0.75f) sample *= 2
        val decoded = context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        if (decoded.width == targetWidth && decoded.height == targetHeight) decoded else Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also { decoded.recycle() }
    }
    return bitmap.asImageBitmap()
}

@Composable
fun AuricRedeemPoints(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
    previewDetailProgress: Double? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val reduced = rememberLiveReducedMotion()
    val lifecycleOwner = LocalLifecycleOwner.current
    val art by produceState(initialValue = emptyMap<String, ImageBitmap>(), context) { value = loadAuricArt(context) }
    var active by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var message by remember { mutableStateOf<String?>(null) }
    var detailsVisible by remember { mutableStateOf(previewDetailProgress != null) }
    val isObscured = detailsVisible || message != null
    val currentObscured by rememberUpdatedState(isObscured)
    var pausedPose by remember { mutableStateOf<AuricMotion.Pose?>(null) }
    var wasObscured by remember { mutableStateOf(false) }
    val currentReduced by rememberUpdatedState(reduced)
    val detailProgress = remember { Animatable((previewDetailProgress ?: 0.0).toFloat()) }
    val coroutineScope = rememberCoroutineScope()
    var detailGeneration by remember { mutableIntStateOf(0) }
    val detailCloseFocus = remember { FocusRequester() }
    val redeemFocus = remember { FocusRequester() }
    var interruption by remember { mutableIntStateOf(0) }
    val pose = remember { mutableStateOf(AuricMotion.Pose()) }
    var contact by remember { mutableStateOf<AuricMotion.Contact?>(null) }
    var release by remember { mutableStateOf<AuricMotion.Release?>(null) }
    var clock by remember { mutableDoubleStateOf(0.0) }
    var frameSeconds by remember { mutableDoubleStateOf(0.0) }
    var revision by remember { mutableIntStateOf(0) }
    var reducedFeedback by remember { mutableStateOf(false) }

    fun resetMotion() {
        contact = null; release = null; pausedPose = null; reducedFeedback = false; clock = 0.0; frameSeconds = 0.0
        pose.value = if (currentReduced) AuricMotion.Pose() else if (previewTime == null) AuricMotion.Pose() else AuricMotion.preview(previewTime)
        revision++
    }
    fun notify(messageText: String) {
        message = messageText
        interruption++
    }
    fun openDetails() {
        if (detailsVisible) return
        val generation = ++detailGeneration
        interruption++
        detailsVisible = true
        coroutineScope.launch {
            detailProgress.snapTo(0f)
            detailProgress.animateTo(1f, if (reduced) tween(150) else spring(dampingRatio = 0.85f, stiffness = 438.649f))
            if (detailGeneration == generation && detailsVisible && previewDetailProgress == null) {
                runCatching { detailCloseFocus.requestFocus() }
            }
        }
    }
    fun closeDetails(instant: Boolean = false) {
        if (!detailsVisible) return
        val generation = ++detailGeneration
        coroutineScope.launch {
            if (instant) detailProgress.snapTo(0f)
            else if (reduced) detailProgress.animateTo(0f, tween(120, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)))
            else detailProgress.animateTo(0f, tween(200, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
            detailsVisible = false
            interruption++
            kotlinx.coroutines.delay(50)
            if (!detailsVisible && detailGeneration == generation) runCatching { redeemFocus.requestFocus() }
        }
    }
    LaunchedEffect(previewDetailProgress) {
        if (previewDetailProgress != null) {
            detailProgress.snapTo(previewDetailProgress.toFloat().coerceIn(0f, 1f))
            detailsVisible = true
        }
    }
    fun startRelease(translationX: Double, translationY: Double, velocityX: Double, velocityY: Double, celebrate: Boolean, emitHaptic: Boolean = celebrate) {
        if (currentObscured) return
        val current = contact ?: return
        val finalContact = current.copy(x = translationX, y = translationY)
        val origin = AuricMotion.pressed(finalContact, frameSeconds)
        val mappedVelocity = AuricMotion.pressedVelocity(finalContact, frameSeconds, velocityX, velocityY)
        val releaseVelocity = if (celebrate) mappedVelocity else AuricMotion.pressedVelocity(finalContact, frameSeconds, 0.0, 0.0)
        contact = null
        release = AuricMotion.Release(origin, frameSeconds, releaseVelocity, celebrate)
        revision++
        if (celebrate && emitHaptic && !reduced) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun replay() {
        if (currentObscured) return
        if (reduced) { reducedFeedback = true; return }
        contact = AuricMotion.Contact(pose.value, frameSeconds)
        startRelease(0.0, 0.0, 0.0, 0.0, true, emitHaptic = false)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> { active = false; resetMotion() }
                Lifecycle.Event.ON_RESUME -> { active = true; resetMotion() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isObscured) {
        if (isObscured) {
            pausedPose = pose.value
            contact = null; release = null; reducedFeedback = false
            revision++
        } else if (wasObscured) {
            val origin = pausedPose ?: AuricMotion.Pose()
            pausedPose = null
            if (!reduced) release = AuricMotion.Release(origin, frameSeconds, AuricMotion.Velocity(), false)
            revision++
        }
        wasObscured = isObscured
    }
    LaunchedEffect(reduced) { resetMotion() }

    LaunchedEffect(autoPlay, previewTime, reduced, active, revision, isObscured) {
        if (reduced) {
            pose.value = AuricMotion.Pose(); contact = null; release = null; pausedPose = null; clock = 0.0; frameSeconds = 0.0
            reducedFeedback = false
            return@LaunchedEffect
        }
        if (!active || isObscured) return@LaunchedEffect
        if (previewTime != null && contact == null && release == null) {
            clock = ((previewTime % AuricMotion.LOOP) + AuricMotion.LOOP) % AuricMotion.LOOP
            pose.value = AuricMotion.preview(previewTime)
            return@LaunchedEffect
        }
        if (!autoPlay && contact == null && release == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (active && !reduced && !currentObscured) {
            val now = withFrameNanos { it }
            if (currentObscured || !active || currentReduced) break
            val dt = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0)
            previous = now
            frameSeconds += dt
            val touch = contact
            if (touch != null) {
                pose.value = AuricMotion.pressed(touch, frameSeconds)
                continue
            }
            val released = release
            if (released != null) {
                val age = frameSeconds - released.startedAt
                pose.value = AuricMotion.released(age, released.origin, released.velocity, released.celebrate)
                if (age >= 2.0) {
                    release = null; clock = 0.0; pose.value = AuricMotion.Pose(); revision++
                    if (!autoPlay || previewTime != null) break
                }
                continue
            }
            if (previewTime != null) { pose.value = AuricMotion.preview(previewTime); break }
            if (autoPlay && !currentObscured) { clock = (clock + dt) % AuricMotion.LOOP; pose.value = AuricMotion.preview(clock) }
            else break
        }
    }

    LaunchedEffect(interruption) {
        if (interruption == 0 || reduced || currentObscured) return@LaunchedEffect
        val origin = pose.value
        contact = null
        release = AuricMotion.Release(origin, frameSeconds, AuricMotion.Velocity(), false)
        revision++
    }
    LaunchedEffect(reducedFeedback, interruption) {
        if (!reducedFeedback) return@LaunchedEffect
        kotlinx.coroutines.delay(150)
        reducedFeedback = false
    }

    val feedbackAlpha by animateFloatAsState(
        targetValue = if (reduced && reducedFeedback) 0.84f else 1f,
        animationSpec = tween(if (reducedFeedback) 120 else 150),
        label = "auric coin reduced motion feedback",
    )

    BackHandler(enabled = detailsVisible && previewDetailProgress == null) { closeDetails(instant = true) }

    BlockSystemBars(darkIcons = false)
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(LocalDensity.current.density, 1f)) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF20032F)).onPreviewKeyEvent { event ->
        if (detailsVisible && event.key == Key.Escape && event.type == KeyEventType.KeyDown) { closeDetails(instant = true); true } else false
    }) {
        val scale = minOf(maxWidth.value / 393f, maxHeight.value / 852f)
        val viewportWidthDp = maxWidth.value
        val viewportHeightDp = maxHeight.value
        Canvas(Modifier.fillMaxSize()) {
            val px = density
            drawRect(Color(0xFF20032F))
            drawRect(Brush.radialGradient(colorStops = arrayOf(0f to Color(0xFFAA0C62), 0.46f to Color(0xFF57054C), 1f to Color(0xFF20032F)), center = Offset(size.width * 0.49f, size.height * 0.51f), radius = 410f * px))
            drawRect(Brush.radialGradient(listOf(Color(0x593D38B9), Color.Transparent), Offset(size.width * 1.03f, size.height * 0.59f), 360f * px))
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.26f))))
            for (i in 0 until 320) {
                var hash = i * 0x45D9F3B
                hash = (hash xor (hash ushr 16)) * 0x45D9F3B
                val value = (hash xor (hash ushr 16)) and 0x7FFFFFFF
                val x = (value % 393) * px
                val y = ((value / 393) % 852) * px
                drawCircle(Color.White.copy(alpha = 0.035f), 0.35f * px, Offset(x, y))
            }
        }
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 852.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
          Box(Modifier.fillMaxSize().then(if (detailsVisible) Modifier.clearAndSetSemantics {} else Modifier)) {
            val hero = Modifier.offset(x = 86.dp, y = 31.dp).size(220.dp, 240.dp)
            val reflectedAuraBrush = remember(density) {
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFFD35A).copy(alpha = 0f),
                        0.55f to Color(0xFFFFD35A).copy(alpha = 0.24f),
                        0.80f to Color(0xFFF29724).copy(alpha = 0.12f),
                        1f to Color.Transparent,
                    ),
                    center = Offset(105f * density, 105f * density), radius = 86f * density,
                )
            }
            Canvas(Modifier.offset(x = 91.dp, y = 46.dp).size(210.dp).graphicsLayer {
                val p = pose.value
                val charge = AuricMotion.smooth(p.light / 0.45).toFloat()
                alpha = charge; scaleX = 1f + 0.04f * charge; scaleY = scaleX
                translationX = (p.offsetX * 0.15 * density).toFloat()
                translationY = ((7 + p.lift * 0.2 - 4) * density).toFloat()
            }) {
                // The moving radius-86 gradient stays entirely inside the reference 210 circle.
                translate(left = (16.8 * pose.value.yaw / 24 * density).toFloat()) {
                    drawCircle(reflectedAuraBrush, radius = 86.dp.toPx(), center = Offset(105.dp.toPx(), 105.dp.toPx()))
                }
            }
            Canvas(Modifier.offset(x = 106.dp, y = 56.dp).size(180.dp, 190.dp)
                .graphicsLayer {
                    val p = pose.value
                    compositingStrategy = CompositingStrategy.Offscreen
                    scaleX = p.scaleX.toFloat(); scaleY = p.scaleY.toFloat()
                    rotationX = p.pitch.toFloat(); rotationY = p.yaw.toFloat(); rotationZ = p.roll.toFloat()
                    translationX = (p.offsetX * density).toFloat()
                    translationY = ((p.lift - 4.0) * density).toFloat()
                    cameraDistance = 760f * density
                    alpha = feedbackAlpha
                }) {
                val px = this.density
                val coin = art["coin"]
                if (coin != null) {
                    val fit = minOf(180f * px / coin.width, 190f * px / coin.height)
                    val referenceScale = AuricReferenceFit.COIN_SCALE.toFloat()
                    val drawWidth = coin.width * fit * referenceScale; val drawHeight = coin.height * fit * referenceScale
                    val left = (180f * px - drawWidth) / 2f + AuricReferenceFit.COIN_OFFSET_X.toFloat() * px
                    val top = (190f * px - drawHeight) / 2f + AuricReferenceFit.COIN_OFFSET_Y.toFloat() * px
                    drawImage(coin, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()))
                    val p = pose.value
                    val reflection = Brush.linearGradient(
                        colorStops = arrayOf(0.455f to Color.Transparent, 0.5f to Color(0xAAFFF0B8).copy(alpha = p.light.toFloat()), 0.545f to Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(180f * px, 190f * px),
                    )
                    translate(left = (p.sweep * 105.0 * px).toFloat()) {
                        drawRect(reflection, Offset.Zero, Size(180f * px, 190f * px), blendMode = BlendMode.SrcAtop)
                    }
                    val highlightWidth = (9.0 + 3.0 * p.streak).toFloat() * px
                    val highlightHeight = (16.0 + 12.0 * p.streak).toFloat() * px
                    val highlight = AuricMotion.morphPath(
                        (129f * px - highlightWidth / 2f), (44f * px - highlightHeight / 2f), highlightWidth, highlightHeight,
                        p.morph, 22.0 + 10.0 * p.streak,
                    )
                    drawPath(highlight, Color(0xB3FFE96C).copy(alpha = p.streak.toFloat()), style = Stroke(3f * px))
                    drawPath(highlight, Color.White)
                }
            }
            Canvas(hero) {
                val px = this.density
                val p = pose.value
                fun sparkle(x: Float, y: Float, width: Float, height: Float, ageStart: Double? = null, angle: Double = 0.0) {
                    val pulse = if (ageStart == null) 0.0 else AuricMotion.pulse(p.eventAge, ageStart, 0.3)
                    val path = AuricMotion.morphPath(x * px, y * px, width * px, height * px, 0.08 + pulse * 0.14, angle * pulse * 10.0, 1.0 + pulse * 0.22)
                    drawPath(path, Brush.linearGradient(listOf(Color(0xFFFBE078), Color(0xFFFBA534)), Offset(x * px, y * px), Offset((x + width) * px, (y + height) * px)))
                }
                sparkle(19f, 74.8f, 26f, 36.4f, 0.15, -1.0)
                sparkle(174f, 103f, 30f, 42f, 0.19, 1.0)
                sparkle(29.5f, 142.5f, 5f, 7f)
                sparkle(70.5f, 195.5f, 5f, 7f)
                sparkle(164.5f, 182.5f, 5f, 7f)
                for (index in 0 until 6) {
                    val life = (p.eventAge - (0.17 + index * 0.018)) / 0.28
                    if (life > 0.0 && life < 1.0) {
                        val angle = Math.toRadians(-140.0 + index * 29.0)
                        val distance = 54.0 + 17.0 * AuricMotion.smooth(life)
                        val radius = (0.7 + (index % 3) * 0.3) * sin(PI * life)
                        val point = Offset((110.0 + cos(angle) * distance).toFloat() * px, (106.0 + sin(angle) * distance).toFloat() * px)
                        drawCircle(Color(0xFFFFE6A0).copy(alpha = (0.55 * sin(PI * life)).toFloat()), radius.toFloat() * px, point)
                    }
                }
            }
            Box(Modifier.offset(x = 106.dp, y = 56.dp).size(180.dp, 190.dp)
                .pointerInput(reduced, density, isObscured) {
                    var dragX = 0.0; var dragY = 0.0; var velocityX = 0.0; var velocityY = 0.0
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (currentObscured) return@awaitEachGesture
                        if (reduced) {
                            reducedFeedback = true
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                pressed = change.pressed
                            }
                            return@awaitEachGesture
                        }

                        dragX = 0.0; dragY = 0.0; velocityX = 0.0; velocityY = 0.0
                        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        contact = AuricMotion.Contact(pose.value, frameSeconds)
                        release = null
                        revision++
                        var completed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                dragX = ((change.position.x - down.position.x) / density).toDouble()
                                dragY = ((change.position.y - down.position.y) / density).toDouble()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (change.pressed) {
                                    change.consume()
                                    val current = contact ?: continue
                                    val next = current.copy(x = dragX, y = dragY)
                                    contact = next
                                    pose.value = AuricMotion.pressed(next, frameSeconds)
                                } else {
                                    val tracked = tracker.calculateVelocity()
                                    velocityX = tracked.x.toDouble() / density
                                    velocityY = tracked.y.toDouble() / density
                                    change.consume()
                                    startRelease(dragX, dragY, velocityX, velocityY, true)
                                    completed = true
                                    break
                                }
                            }
                        } finally {
                            if (!completed && !reduced && contact != null) startRelease(dragX, dragY, 0.0, 0.0, false)
                        }
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "Gold rewards coin"
                    stateDescription = if (reduced) "Gold coin" else "Minted gold finish preview"
                    onClick(label = "Preview gold finish") { replay(); true }
                    customActions = listOf(CustomAccessibilityAction("Preview gold finish") { replay(); true })
                }
            )
            Row(Modifier.offset(y = 237.dp).width(393.dp).height(60.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("120", color = Color.White, fontSize = 53.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp)
                Text("pts", Modifier.offset(y = (-9).dp).padding(start = 5.dp), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Available to Redeem", Modifier.offset(y = 304.dp).width(393.dp), color = Color(0xFFC28DBB), fontSize = 16.sp, textAlign = TextAlign.Center)
            Box(Modifier.offset(x = 17.dp, y = 352.dp).size(359.dp, 38.dp)) {
                Box(Modifier.offset(y = 3.dp).size(359.dp, 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)))
                Box(Modifier.offset(y = 3.dp).size(127.dp, 6.dp).clip(CircleShape).background(Color(0xFFF29724)))
                Box(Modifier.offset(x = 120.dp, y = (-1).dp).size(14.dp).clip(CircleShape).background(Color(0xFFF29724)).border(1.5.dp, Color.White, CircleShape))
                Text("675pts", Modifier.offset(y = 19.dp), color = Color.White, fontSize = 8.sp)
                AuricTier("Bronze", Color(0xFFD7811E), false, 187.dp)
                AuricTier("Silver", Color(0xFFADB7D2), false, 260.dp)
                AuricTier("Gold", Color(0xFFFFCE09), true, 330.dp)
            }
            Row(Modifier.offset(x = 17.dp, y = 406.dp).size(359.dp, 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Redeem Rewards", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Canvas(Modifier.size(18.dp)) { drawPath(AuricChevronPath(size.width, size.height), Color.White, style = Stroke(width = 1.7.dp.toPx())) }
            }
            AuricRewardCard(
                modifier = Modifier.offset(x = 17.5.dp, y = 447.5.dp), expired = false, image = art["headphones"],
                onRedeem = { openDetails() }, reduced = reduced, focusRequester = redeemFocus,
            )
            AuricRewardCard(
                modifier = Modifier.offset(x = 200.5.dp, y = 447.5.dp), expired = true, image = art["sneakers"],
                onRedeem = {}, reduced = reduced,
            )
            Text("Please note that offers are updated weekly", Modifier.offset(y = 674.dp).width(393.dp), color = Color(0xFFC28DBB).copy(alpha = 0.8f), fontSize = 11.5.sp, textAlign = TextAlign.Center)
            AuricPill("Claim Offer", Modifier.offset(x = 17.dp, y = 694.dp).size(359.dp, 50.dp), reduced = reduced, onClick = { notify("Keep earning points to unlock your next offer.") }, outlined = true)
            AuricPill("Not Now", Modifier.offset(x = 17.dp, y = 755.dp).size(359.dp, 50.dp), reduced = reduced, onClick = { notify("Your offers will be here when you return.") }, color = Color(0xFFBAAFC2).copy(alpha = 0.6f))
          }
            if (detailsVisible) AuricDetailMorph(
                art["headphones"], detailProgress, reduced, { closeDetails() }, detailCloseFocus,
                viewportWidth = viewportWidthDp, viewportHeight = viewportHeightDp, stageScale = scale,
            )
        }
    }
    if (message != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Rewards") },
            text = { Text(message.orEmpty()) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
    }
}

@Composable
private fun AuricDetailMorph(
    image: ImageBitmap?,
    progress: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
    onClose: () -> Unit,
    closeFocus: FocusRequester,
    viewportWidth: Float,
    viewportHeight: Float,
    stageScale: Float,
) {
    val density = LocalDensity.current.density
    val actionEnabled by remember(progress, reduced) {
        derivedStateOf { reduced || AuricMotion.smooth(((progress.value - 0.55f) / 0.35f).toDouble()) > 0.5 }
    }
    val actionAccessible by remember(progress, reduced) {
        derivedStateOf { reduced || AuricMotion.smooth(((progress.value - 0.55f) / 0.35f).toDouble()) > 0.8 }
    }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (reduced) progress.value else 1f }
        .semantics { contentDescription = "Headphones reward details"; isTraversalGroup = true }) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (reduced) 1f else progress.value }
            .background(Color.Black.copy(alpha = 0.36f)).clickable(onClick = onClose))
        Canvas(Modifier.fillMaxSize()) {
            val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
            val sourceAlpha = 1f - AuricMotion.smooth(g.toDouble() / 0.4).toFloat()
            val targetAlpha = AuricMotion.smooth(((g - 0.55f) / 0.35f).toDouble()).toFloat()
            val centerX = 106f + (196.5f - 106f) * g
            val px = this.density
            val left = (17.5f + (17f - 17.5f) * g) * px
            val top = (447.5f + (442f - 447.5f) * g) * px
            val width = (177f + (359f - 177f) * g) * px
            val height = (193f + (348f - 193f) * g) * px
            val radius = (26f + (32f - 26f) * g) * px
            val shape = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + width, top + height, androidx.compose.ui.geometry.CornerRadius(radius, radius))) }
            val viewportWidthPx = viewportWidth * px
            val viewportHeightPx = viewportHeight * px
            val originX = (viewportWidth - 393f * stageScale) * px / 2f
            val originY = (viewportHeight - 852f * stageScale) * px / 2f
            clipPath(shape) {
                drawRect(Color(0xFF20032F), Offset.Zero, Size(393f * px, 852f * px))
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(0f to Color(0xFFAA0C62), 0.46f to Color(0xFF57054C), 1f to Color(0xFF20032F)),
                        center = Offset((viewportWidthPx * 0.49f - originX) / stageScale, (viewportHeightPx * 0.51f - originY) / stageScale),
                        radius = 410f * px / stageScale,
                    ), Offset.Zero, Size(393f * px, 852f * px),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x593D38B9), Color.Transparent),
                        Offset((viewportWidthPx * 1.03f - originX) / stageScale, (viewportHeightPx * 0.59f - originY) / stageScale),
                        360f * px / stageScale,
                    ), Offset.Zero, Size(393f * px, 852f * px),
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.26f)),
                        startY = -originY / stageScale, endY = (viewportHeightPx - originY) / stageScale,
                    ), Offset.Zero, Size(393f * px, 852f * px),
                )
                for (i in 0 until 320) {
                    var hash = i * 0x45D9F3B
                    hash = (hash xor (hash ushr 16)) * 0x45D9F3B
                    val value = (hash xor (hash ushr 16)) and 0x7FFFFFFF
                    val x = (value % 393) * px
                    val y = ((value / 393) % 852) * px
                    drawCircle(Color.White.copy(alpha = 0.035f), 0.35f * px, Offset(x, y))
                }
            }
            drawPath(shape, androidx.compose.ui.graphics.lerp(Color(0x66893C58), Color(0xFF371044), g))
            clipPath(shape) {
                val glowEnd = (125f + (220f - 125f) * g) * px
                drawRect(Brush.radialGradient(listOf(Color(0xFFC88629).copy(alpha = 0.7f + (0.45f - 0.7f) * g), Color.Transparent), Offset(left + width * 0.49f, top + height * 0.28f), glowEnd), Offset(left, top), Size(width, height))
            }
            drawPath(shape, Color(0xFFFFC43B).copy(alpha = 0.65f), style = Stroke(0.75f * px))
            if (image != null) {
                val imageScale = 1f / 1.4f + (1f - 1f / 1.4f) * g
                val targetWidth = 135.8f * px * imageScale * AuricReferenceFit.HEADPHONES_SCALE.toFloat()
                val targetHeight = 168f * px * imageScale * AuricReferenceFit.HEADPHONES_SCALE.toFloat()
                val centerY = 497.5f + (538f - 497.5f) * g
                val fit = minOf(targetWidth / image.width, targetHeight / image.height)
                val w = image.width * fit; val h = image.height * fit
                val correctionX = AuricReferenceFit.HEADPHONES_OFFSET_X.toFloat() * 1.4f * imageScale
                val correctionY = AuricReferenceFit.HEADPHONES_OFFSET_Y.toFloat() * 1.4f * imageScale
                val x = (centerX + correctionX) * px - w / 2f; val y = (centerY + correctionY) * px - h / 2f
                drawImage(image, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
            }
        }
        // Match the live source rectangle while keeping the hit target's transform anchored at its top-left.
        Box(Modifier.offset(x = 17.dp, y = 442.dp).size(359.dp, 348.dp).graphicsLayer {
            val p = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            translationX = 0.5f * (1f - p) * density
            translationY = 5.5f * (1f - p) * density
            scaleX = (177f + (359f - 177f) * p) / 359f
            scaleY = (193f + (348f - 193f) * p) / 348f
        }.clickable(onClick = {}))
        Text("Aural Headphones", Modifier.offset(x = 108.dp, y = 596.dp).width(177.dp).height(22.dp).graphicsLayer {
            val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            translationX = -90.5f * (1f - g) * density
            translationY = -53.5f * (1f - g) * density
            scaleX = 1f + 0.6f * g; scaleY = scaleX
        }.clearAndSetSemantics { if (actionAccessible) contentDescription = "Aural Headphones" else hideFromAccessibility() }, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
        Row(Modifier.offset(x = 108.dp, y = 632.dp).width(177.dp).height(40.dp).graphicsLayer {
            val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
            translationX = -90.5f * (1f - g) * density
            translationY = -69.5f * (1f - g) * density
        }.clearAndSetSemantics { if (actionAccessible) contentDescription = "600 points required" else hideFromAccessibility() }, horizontalArrangement = Arrangement.Center) {
            Text("600", Modifier.alignByBaseline(), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("pts", Modifier.alignByBaseline().offset(y = (-4).dp).padding(start = 3.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("You need 480 more points.", Modifier.offset(x = 39.dp, y = 670.dp).width(315.dp).graphicsLayer {
            val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
            alpha = AuricMotion.smooth(((g - 0.55f) / 0.35f).toDouble()).toFloat()
        }.clearAndSetSemantics { if (actionAccessible) contentDescription = "You need 480 more points" else hideFromAccessibility() }, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
        Box(Modifier.offset(x = 39.dp, y = 716.dp).size(315.dp, 44.dp).graphicsLayer {
            val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            translationX = -90.5f * (1f - g) * density
            translationY = -127f * (1f - g) * density
            scaleX = (132f + (315f - 132f) * g) / 315f
            scaleY = (29f + (44f - 29f) * g) / 44f
        }.clip(CircleShape).background(Brush.verticalGradient(colorStops = arrayOf(0f to Color(0xFFF3A82F), 0.6f to Color(0xFFFAC672), 1f to Color(0xFFFED99B))))
            .clickable(enabled = actionEnabled, role = Role.Button, onClick = onClose)
            .semantics { if (actionAccessible) contentDescription = "Keep earning points" else hideFromAccessibility() })
        Box(Modifier.offset(x = 39.dp, y = 716.dp).size(315.dp, 44.dp).graphicsLayer {
            val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
            translationX = -90.5f * (1f - g) * density
            translationY = -127f * (1f - g) * density
        }, contentAlignment = Alignment.Center) {
            Text("Redeem", Modifier.graphicsLayer {
                val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
                alpha = 1f - AuricMotion.smooth(((g - 0.15f) / 0.30f).toDouble()).toFloat()
            }.clearAndSetSemantics {}, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Keep earning", Modifier.graphicsLayer {
                val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
                alpha = AuricMotion.smooth(((g - 0.15f) / 0.30f).toDouble()).toFloat()
            }.clearAndSetSemantics {}, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.offset(x = 323.dp, y = 446.dp).size(44.dp).graphicsLayer {
            val g = if (reduced) 1f else progress.value.coerceIn(0f, 1f)
            alpha = AuricMotion.smooth(((g - 0.55f) / 0.35f).toDouble()).toFloat()
        }.focusRequester(closeFocus).focusable(enabled = actionAccessible)
            .clickable(enabled = actionEnabled, role = Role.Button, onClick = onClose)
            .semantics { if (actionAccessible) contentDescription = "Close reward details" else hideFromAccessibility() }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(13.dp)) {
                    val stroke = 1.7.dp.toPx()
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), stroke)
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.2f, size.height * 0.8f), stroke)
                }
            }
        }
    }
}

@Composable
private fun AuricTier(label: String, color: Color, trophy: Boolean, left: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current.density
    val star = remember(density) {
        val center = 9f * density
        val outer = 4f * density
        val inner = 1.8f * density
        Path().apply {
            for (point in 0 until 10) {
                val radius = if (point % 2 == 0) outer else inner
                val angle = -PI / 2.0 + point * PI / 5.0
                val x = center + (kotlin.math.cos(angle) * radius).toFloat()
                val y = center + (kotlin.math.sin(angle) * radius).toFloat()
                if (point == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
    Column(Modifier.offset(x = left, y = (-3).dp).width(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(18.dp)) {
            val px = density
            drawCircle(Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f)), Offset.Zero, Offset(size.width, size.height)))
            drawCircle(Color.White.copy(alpha = 0.45f), radius = size.minDimension / 2f, style = Stroke(1.dp.toPx()))
            if (trophy) {
                val path = Path().apply { moveTo(size.width * 0.3f, size.height * 0.2f); lineTo(size.width * 0.7f, size.height * 0.2f); lineTo(size.width * 0.66f, size.height * 0.56f); quadraticTo(size.width * 0.5f, size.height * 0.78f, size.width * 0.34f, size.height * 0.56f); close() }
                drawPath(path, Color.White.copy(alpha = 0.65f))
                drawLine(Color.White.copy(alpha = 0.65f), Offset(size.width * 0.5f, size.height * 0.62f), Offset(size.width * 0.5f, size.height * 0.8f), 0.8f * px)
                drawLine(Color.White.copy(alpha = 0.65f), Offset(size.width * 0.36f, size.height * 0.82f), Offset(size.width * 0.64f, size.height * 0.82f), 0.8f * px)
            } else {
                drawPath(star, Color.White.copy(alpha = 0.65f))
            }
        }
        Text(label, color = Color(0xFFC28DBB), fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun AuricRewardCard(modifier: Modifier, expired: Boolean, image: ImageBitmap?, onRedeem: () -> Unit, reduced: Boolean, focusRequester: FocusRequester? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val feedbackAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(if (pressed) 120 else 100, easing = LinearOutSlowInEasing),
        label = "auric reward button opacity",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.975f else 1f,
        animationSpec = tween(if (pressed) 120 else 100, easing = LinearOutSlowInEasing),
        label = "auric reward button scale",
    )
    Box(modifier.size(177.dp, 193.dp).clip(RoundedCornerShape(26.dp))
        .background(if (expired) Color(0xFF3B2B73).copy(alpha = 0.45f) else Color(0xFF893C58).copy(alpha = 0.4f))
        .border(0.75.dp, if (expired) Color.White.copy(alpha = 0.07f) else Color(0xFFFFC43B).copy(alpha = 0.65f), RoundedCornerShape(26.dp))) {
        if (!expired) Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFFC88629).copy(alpha = 0.7f), Color.Transparent), Offset(size.width * 0.49f, size.height * 0.28f), 125.dp.toPx()))
        }
        if (image != null) {
            val imageWidth = if (expired) 124.dp else 97.dp
            val imageHeight = if (expired) 104.dp else 120.dp
            val imageTop = if (expired) AuricReferenceFit.SNEAKERS_OFFSET_Y.dp else (-10.0 + AuricReferenceFit.HEADPHONES_OFFSET_Y).dp
            val offsetX = if (expired) (26.5 + AuricReferenceFit.SNEAKERS_OFFSET_X).dp else (40.0 + AuricReferenceFit.HEADPHONES_OFFSET_X).dp
            val imageScale = if (expired) AuricReferenceFit.SNEAKERS_SCALE.toFloat() else AuricReferenceFit.HEADPHONES_SCALE.toFloat()
            val imageModifier = Modifier.offset(x = offsetX, y = imageTop).size(imageWidth, imageHeight)
                .graphicsLayer { scaleX = imageScale; scaleY = imageScale }
            Canvas(imageModifier) {
                val imageScale = min(size.width / image.width, size.height / image.height)
                val w = image.width * imageScale; val h = image.height * imageScale
                drawImage(image, dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), alpha = if (expired) 0.85f else 1f)
            }
        }
            Text(if (expired) "Aster Sneakers" else "Aural Headphones", Modifier.offset(y = 95.dp).fillMaxWidth(), color = if (expired) Color(0xFFC28DBB).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp, textAlign = TextAlign.Center, maxLines = 1)
        Row(Modifier.offset(y = 115.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(if (expired) "450" else "600", color = if (expired) Color(0xFFC28DBB).copy(alpha = 0.35f) else Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            Text("pts", Modifier.offset(y = (-4).dp).padding(start = 3.dp), color = if (expired) Color(0xFFC28DBB).copy(alpha = 0.35f) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        val pillFill = if (expired) Modifier.background(Color(0xFFBD884B).copy(alpha = 0.25f))
            else Modifier.background(Brush.verticalGradient(colorStops = arrayOf(0f to Color(0xFFF3A82F), 0.6f to Color(0xFFFAC672), 1f to Color(0xFFFED99B))))
        Box(Modifier.offset(x = 22.5.dp, y = 141.5.dp).size(132.dp, 44.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .clickable(enabled = !expired, interactionSource = interaction, indication = null, role = Role.Button, onClick = onRedeem)
            .semantics { contentDescription = if (expired) "Sneaker reward expired" else "Redeem headphones for 600 points"; role = Role.Button }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(132.dp, 29.dp).graphicsLayer { scaleX = pressScale; scaleY = pressScale; alpha = feedbackAlpha }
                .clip(CircleShape).then(pillFill), contentAlignment = Alignment.Center) {
                Text(if (expired) "Expired" else "Redeem", color = if (expired) Color.Black.copy(alpha = 0.4f) else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AuricPill(label: String, modifier: Modifier, reduced: Boolean, onClick: () -> Unit, color: Color? = null, outlined: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val feedbackAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(if (pressed) 120 else 100, easing = LinearOutSlowInEasing),
        label = "auric pill opacity",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.975f else 1f,
        animationSpec = tween(if (pressed) 120 else 100, easing = LinearOutSlowInEasing),
        label = "auric pill scale",
    )
    val fill = color?.let { Modifier.background(it) } ?: Modifier.background(Brush.verticalGradient(colorStops = arrayOf(0f to Color(0xFFF3A82F), 0.6f to Color(0xFFFAC672), 1f to Color(0xFFFED99B))))
    Box(modifier
        .graphicsLayer { scaleX = pressScale; scaleY = pressScale; alpha = feedbackAlpha }
        .clip(CircleShape)
        .then(fill)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; role = Role.Button }, contentAlignment = Alignment.Center) {
        Text(label, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (outlined) Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape))
    }
}

private fun AuricChevronPath(width: Float, height: Float): Path = Path().apply {
    moveTo(width * 0.35f, height * 0.2f); lineTo(width * 0.65f, height * 0.5f); lineTo(width * 0.35f, height * 0.8f)
}

