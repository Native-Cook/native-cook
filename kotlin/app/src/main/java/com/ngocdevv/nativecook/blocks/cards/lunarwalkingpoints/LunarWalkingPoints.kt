package com.ngocdevv.nativecook.blocks.cards.lunarwalkingpoints

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.*

private val LunarEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val LunarYellow = Color(0xFFFFE000)
private val LunarMuted = Color(0xFFBFB5D3)
private fun lunarNow() = System.nanoTime() / 1_000_000_000.0

/** Analytical physics copied from the approved Swift reference; no integrated frame deltas. */
private object LunarMotion {
    const val LOOP = 8.0
    data class Value(val q: Double = 0.0, val envelope: Double = 0.0)
    data class Contact(val origin: Value, val raw: Double, val rubber: Double,
        val translation: Double = 0.0, val velocity: Double = 0.0,
        val lastTranslation: Double = 0.0, val lastUpdate: Double)
    data class Settle(val origin: Value, val velocity: Double, val envelopeVelocity: Double, val startedAt: Double)
    fun loop(time: Double): Value {
        val t = ((time % LOOP) + LOOP) % LOOP
        if (t < 1.2 || t >= 5.8) return Value()
        val u = (t - 1.2) / 4.6
        val envelope = sin(PI * u).pow(2)
        return Value(0.10 * sin(2 * PI * u) * envelope, envelope)
    }
    fun rubber(value: Double): Double {
        if (abs(value) <= 0.045) return value
        val result = min(0.065, 0.045 + 0.020 * (1 - exp(-(abs(value) - 0.045) / 0.020)))
        return if (value < 0) -result else result
    }
    fun derivative(value: Double): Double {
        if (abs(value) <= 0.045) return 1.0
        val compressed = 0.045 + 0.020 * (1 - exp(-(abs(value) - 0.045) / 0.020))
        if (compressed >= 0.065 - 1e-12) return 0.0
        return exp(-(abs(value) - 0.045) / 0.020)
    }
    fun inverse(value: Double): Double {
        val magnitude = min(0.065 - 1e-10, abs(value))
        if (magnitude <= 0.045) return value
        val raw = 0.045 - 0.020 * ln(1 - (magnitude - 0.045) / 0.020)
        return if (value < 0) -raw else raw
    }
    fun spring(time: Double, origin: Double, velocity: Double = 0.0): Double {
        if (time >= 1.2) return 0.0
        val decay = 0.85 * 12.0
        val frequency = 12.0 * sqrt(1 - 0.85 * 0.85)
        return exp(-decay * time) * (origin * cos(frequency * time) + (velocity + decay * origin) / frequency * sin(frequency * time))
    }
    private fun velocityIsBounded(velocity: Double, origin: Double, limit: Double = 0.065, ceilingOnly: Boolean = false): Boolean {
        val decay = 0.85 * 12.0
        val frequency = 12.0 * sqrt(1 - 0.85 * 0.85)
        val coefficient = (velocity + decay * origin) / frequency
        val phase = atan2(-decay * coefficient - origin * frequency, velocity) + PI / 2
        fun exceedsLimit(time: Double): Boolean {
            val value = spring(time, origin, velocity)
            return (if (ceilingOnly) value else abs(value)) > limit + 1e-10
        }
        if (exceedsLimit(0.0) || exceedsLimit(1.2)) return false
        for (turn in -8..8) {
            val t = (phase + turn * PI) / frequency
            if (t > 0 && t < 1.2 && exceedsLimit(t)) return false
        }
        return true
    }
    fun boundedVelocity(velocity: Double, origin: Double, limit: Double = 0.065, ceilingOnly: Boolean = false): Double {
        if (velocityIsBounded(velocity, origin, limit, ceilingOnly)) return velocity
        val sign = if (velocity < 0) -1.0 else 1.0
        var low = 0.0; var high = abs(velocity)
        repeat(40) {
            val middle = (low + high) / 2
            if (velocityIsBounded(sign * middle, origin, limit, ceilingOnly)) low = middle else high = middle
        }
        return sign * low
    }
    fun envelopeReleaseVelocity(q: Double, envelope: Double, velocity: Double): Double {
        val direction = if (q == 0.0) { if (velocity < 0) -1.0 else 1.0 } else { if (q < 0) -1.0 else 1.0 }
        val requested = direction * velocity / 0.06495
        if ((envelope <= 0 && requested < 0) || (envelope >= 1 && requested > 0)) return 0.0
        return boundedVelocity(requested, envelope, limit = 1.0, ceilingOnly = true)
    }
    fun dragged(contact: Contact): Value {
        val q = (contact.origin.q + rubber(contact.raw + contact.translation / 420) - contact.rubber).coerceIn(-0.065, 0.065)
        return Value(q, (contact.origin.envelope + (abs(q) - abs(contact.origin.q)) / 0.06495).coerceIn(0.0, 1.0))
    }
    fun point(x: Float, y: Float, q: Double): Offset {
        val dx = x - 196.5; val dy = y - 496.0
        return Offset((196.5 + dx * cos(q) - dy / 0.72 * sin(q)).toFloat(),
            (496 + 0.72 * dx * sin(q) + dy * cos(q)).toFloat())
    }
}

private class LunarOrbitState {
    var autoPlay = false
    var previewTime: Double? = null
    var previewOrbit: Double? = null
    var reduced = false
    var active by mutableStateOf(true)
    var overlay by mutableStateOf(false)
    var contact by mutableStateOf<LunarMotion.Contact?>(null)
    var settle by mutableStateOf<LunarMotion.Settle?>(null)
    var paused: LunarMotion.Value? = null
    var generation by mutableIntStateOf(0)
    var epoch = lunarNow()
    var moved = false
    var ignoreTapUntil = 0.0
    val pose = mutableStateOf(LunarMotion.Value())
    val canDrag get() = !reduced && active && !overlay && previewTime == null && previewOrbit == null
    val ticking get() = canDrag && (autoPlay || contact != null || settle != null)
    fun value(now: Double = lunarNow()): LunarMotion.Value {
        if (reduced) return LunarMotion.Value()
        previewOrbit?.let { val q = it.coerceIn(-0.065, 0.065); return LunarMotion.Value(q, min(1.0, abs(q) / 0.06495)) }
        previewTime?.let { return LunarMotion.loop(it) }
        contact?.let { return LunarMotion.dragged(it) }
        settle?.let {
            val age = max(0.0, now - it.startedAt)
            return LunarMotion.Value(LunarMotion.spring(age, it.origin.q, it.velocity), LunarMotion.spring(age, it.origin.envelope, it.envelopeVelocity).coerceIn(0.0, 1.0))
        }
        paused?.let { return it }
        return if (autoPlay) LunarMotion.loop(now - epoch) else LunarMotion.Value()
    }
    fun reset() {
        generation++; contact = null; settle = null; paused = null; moved = false; epoch = lunarNow()
        pose.value = value()
    }
    fun beginSettle(value: LunarMotion.Value, velocity: Double = 0.0, carryEnvelopeVelocity: Boolean = false) {
        if (reduced || previewTime != null || previewOrbit != null) return
        generation++; paused = null; contact = null
        // Clamp q velocity once, then share the same analytically bounded value with lift/material.
        val boundedVelocity = LunarMotion.boundedVelocity(velocity.coerceIn(-1.0, 1.0), value.q)
        val envelopeVelocity = if (carryEnvelopeVelocity) LunarMotion.envelopeReleaseVelocity(value.q, value.envelope, boundedVelocity) else 0.0
        settle = LunarMotion.Settle(value, boundedVelocity, envelopeVelocity, lunarNow())
        pose.value = value
    }
    fun interrupt() { if (canDrag && paused == null) beginSettle(value()) }
    fun updateOverlay(visible: Boolean) {
        if (visible) {
            if (paused == null) paused = value()
            generation++; contact = null; settle = null; moved = false; overlay = true
            pose.value = value()
        } else {
            overlay = false
            if (active && !reduced) paused?.let { beginSettle(it) }
        }
    }
    fun drag(translation: Double) {
        if (!canDrag) return
        val now = lunarNow()
        if (contact == null) {
            val origin = value(now); val raw = LunarMotion.inverse(origin.q)
            generation++; settle = null; moved = false
            contact = LunarMotion.Contact(origin, raw, LunarMotion.rubber(raw), lastUpdate = now)
        }
        val old = contact ?: return
        val dt = now - old.lastUpdate
        contact = old.copy(translation = translation, velocity = if (dt > 0.001) (translation - old.lastTranslation) / dt else old.velocity,
            lastTranslation = translation, lastUpdate = now)
        if (abs(translation) > 5) moved = true
        pose.value = value(now)
    }
    fun finish(cancelled: Boolean): Boolean {
        val c = contact ?: run { moved = false; return false }
        val final = LunarMotion.dragged(c)
        val rawEnvelope = c.origin.envelope + (abs(final.q) - abs(c.origin.q)) / 0.06495
        val successfulMove = moved && !cancelled && !reduced
        val velocity = if (successfulMove) LunarMotion.derivative(c.raw + c.translation / 420) * c.velocity / 420 else 0.0
        contact = null
        // A saturated re-grab has no envelope derivative, even if its clamped value is in range.
        beginSettle(final, velocity, carryEnvelopeVelocity = successfulMove && rawEnvelope in 0.0..1.0)
        if (moved) ignoreTapUntil = lunarNow() + 0.25
        moved = false
        return successfulMove
    }
}

private data class LunarFriend(val id: Int, val name: String, val level: String, val color: Color, val diameter: Float, val center: Offset)
private val LunarFriends = listOf(
    LunarFriend(0, "Ari", "L3", Color(0xFF22A5E9), 61f, Offset(54f, 293f)),
    LunarFriend(1, "Mika", "L6", Color(0xFFEE45AC), 49f, Offset(344f, 405f)),
    LunarFriend(2, "Jules", "L4", Color(0xFFF7DC21), 33f, Offset(42f, 453f)),
)

private suspend fun loadLunarArt(context: android.content.Context): Map<String, ImageBitmap> = withContext(Dispatchers.IO) {
    val result = mutableMapOf<String, ImageBitmap>()
    for (name in listOf("moon", "runner", "avatars")) {
        val bitmap = context.assets.open("lunarwalkingpoints/lunarwalkingpoints_$name.png").use { BitmapFactory.decodeStream(it) }
        checkNotNull(bitmap) { "Missing Lunar Walking Points $name artwork" }
        if (name == "avatars") {
            val w = bitmap.width / 2; val h = bitmap.height / 2
            repeat(4) { i -> result["avatar-$i"] = Bitmap.createBitmap(bitmap, i % 2 * w, i / 2 * h, w, h).asImageBitmap() }
        } else result[name] = bitmap.asImageBitmap()
    }
    result
}

/** Full-screen, offline walking dashboard. Preview inputs use the same analytical motion as live playback. */
@Composable
fun LunarWalkingPoints(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    previewOrbit: Double? = null,
    previewReducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberLiveReducedMotion() || previewReducedMotion
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val orbit = remember { LunarOrbitState() }
    var message by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var selectedFriend by remember { mutableIntStateOf(0) }
    var displayedFriend by remember { mutableIntStateOf(0) }
    var previousReduced by remember { mutableStateOf(reduced) }
    val latestOverlay by rememberUpdatedState(searchFocused || message != null)
    var selectedTab by remember { mutableIntStateOf(1) }
    var selectedNav by remember { mutableIntStateOf(0) }
    val calloutOpacity = remember { Animatable(1f) }
    val art by produceState(emptyMap<String, ImageBitmap>(), context) { value = loadLunarArt(context) }
    val matching = remember(search) {
        val query = search.trim()
        LunarFriends.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) || it.level.contains(query, ignoreCase = true) }
    }
    SideEffect {
        orbit.autoPlay = autoPlay; orbit.previewTime = previewTime; orbit.previewOrbit = previewOrbit; orbit.reduced = reduced
    }
    LaunchedEffect(autoPlay, previewTime, previewOrbit, reduced) {
        orbit.autoPlay = autoPlay; orbit.previewTime = previewTime; orbit.previewOrbit = previewOrbit; orbit.reduced = reduced
        orbit.reset()
        if (searchFocused || message != null) orbit.updateOverlay(true)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            orbit.active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            orbit.reset()
            if (latestOverlay) orbit.updateOverlay(true)
        }
        orbit.active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); orbit.reset() }
    }
    LaunchedEffect(searchFocused, message != null) { orbit.updateOverlay(searchFocused || message != null) }
    LaunchedEffect(orbit.generation) {
        val generation = orbit.generation
        val settling = orbit.settle ?: return@LaunchedEffect
        delay(2_000)
        if (orbit.generation != generation || orbit.settle !== settling) return@LaunchedEffect
        orbit.settle = null; orbit.epoch = lunarNow(); orbit.pose.value = orbit.value()
    }
    LaunchedEffect(autoPlay, previewTime, previewOrbit, reduced, orbit.active, orbit.overlay, orbit.contact != null, orbit.settle != null) {
        orbit.pose.value = orbit.value()
        while (orbit.ticking) withFrameNanos { orbit.pose.value = orbit.value(it / 1_000_000_000.0) }
    }
    LaunchedEffect(selectedFriend, reduced) {
        if (previousReduced != reduced) {
            previousReduced = reduced
            displayedFriend = selectedFriend
            calloutOpacity.snapTo(1f)
            return@LaunchedEffect
        }
        if (displayedFriend == selectedFriend) {
            if (reduced) calloutOpacity.snapTo(1f) else calloutOpacity.animateTo(1f, tween(100, easing = LunarEaseOut))
        } else if (reduced) {
            displayedFriend = selectedFriend
            calloutOpacity.animateTo(0.84f, tween(120, easing = LunarEaseOut))
            calloutOpacity.animateTo(1f, tween(100, easing = LunarEaseOut))
        } else {
            calloutOpacity.animateTo(0f, tween(70, easing = LunarEaseOut))
            displayedFriend = selectedFriend
            calloutOpacity.animateTo(1f, tween(100, easing = LunarEaseOut))
        }
    }
    fun chooseFriend(id: Int) {
        if (orbit.moved || lunarNow() < orbit.ignoreTapUntil) return
        orbit.interrupt()
        if (selectedFriend == id) return
        selectedFriend = id
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
    }
    fun showMessage(value: String) { orbit.interrupt(); message = value }
    BlockSystemBars(darkIcons = false)
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
        BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF02013A))) {
            val scale = min(maxWidth.value / 393f, maxHeight.value / 852f)
            Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 852.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
                LunarBackground()
                // This parent sees contacts beginning on the field and on the existing avatar buttons.
                val density = LocalDensity.current.density
                Box(Modifier.offset(y = 216.dp).size(393.dp, 390.dp).pointerInput(orbit, reduced, previewTime, previewOrbit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val origin = down.position.x
                        var finished = false
                        orbit.drag(0.0)
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (orbit.finish(cancelled = false)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    finished = true; break
                                }
                                orbit.drag(((change.position.x - origin) / density).toDouble())
                                if (orbit.moved) change.consume()
                            } while (true)
                        } finally { if (!finished) orbit.finish(cancelled = true) }
                    }
                }) {
                    Box(Modifier.offset(y = (-216).dp).size(393.dp, 852.dp)) {
                        Canvas(Modifier.offset(x = 79.dp, y = 376.dp).size(240.dp)) {
                            drawCircle(Brush.radialGradient(colorStops = arrayOf(0f to Color(0x2EFF4400), 0.5f to Color(0x2EFF4400), 1f to Color.Transparent)))
                        }
                        val auraBrush = remember(density) {
                            Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to Color(0xFFFFD24A).copy(alpha = 0f),
                                    0.58f to Color(0xFFFFD24A).copy(alpha = 0.30f),
                                    0.78f to Color(0xFFFF6A00).copy(alpha = 0.16f),
                                    1f to Color.Transparent,
                                ),
                                center = Offset(120f * density, 120f * density),
                                radius = 120f * density,
                            )
                        }
                        Canvas(Modifier.offset(x = 79.dp, y = 376.dp).size(240.dp).graphicsLayer {
                            val p = orbit.pose.value
                            val e = if (reduced) 0.0 else p.envelope.coerceIn(0.0, 1.0)
                            val a = e * e * (3 - 2 * e)
                            alpha = a.toFloat()
                            translationX = (-45 * p.q * density).toFloat()
                            translationY = (-3 * e * density).toFloat()
                            scaleX = (1 + 0.04 * a).toFloat(); scaleY = scaleX
                        }.clearAndSetSemantics {}) {
                            drawCircle(auraBrush, radius = 120.dp.toPx(), center = center)
                        }
                        LunarArt(art["moon"], Modifier.offset(x = 109.dp, y = 406.dp).size(180.dp))
                        // Bounded native blur on Android 12+; fixed-width Gaussian feather below it.
                        val nativeShadowBlur = Build.VERSION.SDK_INT >= 31
                        Canvas(Modifier.offset(x = 180.dp, y = 429.dp).size(38.dp, 12.dp).graphicsLayer {
                            translationX = (-70 * orbit.pose.value.q * density).toFloat()
                        }.then(if (nativeShadowBlur) Modifier.blur(1.25.dp, BlurredEdgeTreatment.Unbounded) else Modifier)) {
                            val e = if (reduced) 0.0 else orbit.pose.value.envelope.coerceIn(0.0, 1.0)
                            val opacity = 0.16 * min(1.0, e / 0.12) * (1 - e)
                            if (opacity > 0) {
                                val width = (30 * (1 - 0.4 * e)).toFloat() * density
                                val height = (4 * (1 - 0.375 * e)).toFloat() * density
                                if (nativeShadowBlur) {
                                    drawOval(Color(0xFF02013A).copy(alpha = opacity.toFloat()),
                                        Offset(center.x - width / 2, center.y - height / 2), androidx.compose.ui.geometry.Size(width, height))
                                } else {
                                    // Approximate the same 1.25dp blur with nine nested, weighted ellipses.
                                    // Feather width stays fixed as the underlying 30×4 ellipse contracts.
                                    for (step in 4 downTo -4) {
                                        val spread = step * 0.625f * density
                                        val w = width + 2 * spread; val h = height + 2 * spread
                                        if (w > 0 && h > 0) {
                                            val weight = exp(-0.5 * (step / 2.0).pow(2)) / 4.8980306258
                                            val alpha = (1 - (1 - opacity).pow(weight)).toFloat()
                                            drawOval(Color(0xFF02013A).copy(alpha = alpha),
                                                Offset(center.x - w / 2, center.y - h / 2), androidx.compose.ui.geometry.Size(w, h))
                                        }
                                    }
                                }
                            }
                        }
                        LunarArt(art["runner"], Modifier.offset(x = 134.5.dp, y = 308.dp).size(129.dp, 142.dp).graphicsLayer {
                            val p = orbit.pose.value
                            val e = if (reduced) 0.0 else p.envelope.coerceIn(0.0, 1.0)
                            val roll = -2 * (p.q / 0.065).coerceIn(-1.0, 1.0) * e
                            val angle = roll * PI / 180
                            val x = -70 * p.q; val y = -16 * e
                            // Swift applies offset inside rotationEffect, so rotate that offset too.
                            translationX = ((x * cos(angle) - y * sin(angle)) * density).toFloat()
                            translationY = ((x * sin(angle) + y * cos(angle)) * density).toFloat()
                            rotationZ = roll.toFloat()
                        })
                        matching.forEach { friend ->
                            key(friend.id) {
                                LunarFriendButton(friend, art["avatar-${friend.id}"], selectedFriend == friend.id, reduced, orbit.pose, onClick = { chooseFriend(friend.id) })
                            }
                        }
                        LunarCallout(LunarFriends[displayedFriend], search.isNotEmpty() && matching.isEmpty(), orbit.pose, calloutOpacity)
                    }
                }
                Row(Modifier.offset(x = 17.dp, y = 50.dp).size(359.dp, 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    LunarButton(Modifier.size(44.dp), "Maya’s profile", reduced, onClick = { showMessage("Maya’s walking profile.") }) {
                        LunarArt(art["avatar-3"], Modifier.size(43.dp).background(Color(0xFF8D8E85), CircleShape).clip(CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) { drawArc(Color(0xFFD3BC00), 36f, 298.8f, false, style = Stroke(2.dp.toPx())) }
                        LunarLabel("L1", 10f, FontWeight.SemiBold, Color(0xFFEED934))
                    }
                    Spacer(Modifier.weight(1f))
                    LunarButton(Modifier.height(44.dp), "Point balance, 3745 points", reduced, onClick = { showMessage("Your point balance is 3745.") }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Default.MonetizationOn, null, Modifier.size(17.dp), tint = Color(0xFFF7D80B))
                            LunarLabel("3745", 13f, FontWeight.Medium)
                            Icon(Icons.Default.AddCircle, null, Modifier.size(17.dp), tint = Color(0xFF49405E))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    LunarButton(Modifier.size(44.dp), "Notifications", reduced, onClick = { showMessage("You’re all caught up.") }) {
                        Box {
                            Icon(Icons.Default.Notifications, null, Modifier.size(18.dp), tint = Color.White)
                            Box(Modifier.align(Alignment.TopEnd).size(4.dp).background(Color.Red, CircleShape))
                        }
                    }
                }
                listOf("We Walk", "Aster", "Nimbly", "Arc").forEachIndexed { i, name ->
                    val color by animateColorAsState(if (selectedTab == i) Color.White else Color(0xFFA49CB5), tween(if (reduced) 120 else 180, easing = LunarEaseOut))
                    LunarButton(Modifier.offset(x = (listOf(51f, 134f, 215f, 281f)[i] - 37.5f).dp, y = 110.dp).size(75.dp, 44.dp).semantics { selected = selectedTab == i }, name, reduced,
                        onClick = { orbit.interrupt(); selectedTab = i }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LunarLabel(name, 14f, if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal, color)
                            Box(Modifier.size(40.dp, 2.dp).background(if (selectedTab == i) Color(0xFFF1D633) else Color.Transparent))
                        }
                    }
                }
                Row(Modifier.offset(x = 17.dp, y = 166.dp).size(359.dp, 44.dp).background(Color(0xFF030020), RoundedCornerShape(4.dp)).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.Search, null, Modifier.size(14.dp), tint = Color(0xFF837A99))
                    BasicTextField(search, onValueChange = { search = it }, modifier = Modifier.weight(1f).onFocusChanged { searchFocused = it.isFocused }.semantics { contentDescription = "Search friends" },
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp), singleLine = true, cursorBrush = SolidColor(LunarYellow),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            matching.firstOrNull()?.let { chooseFriend(it.id) }
                            focusManager.clearFocus()
                        }),
                        decorationBox = { inner -> Box { if (search.isEmpty()) LunarLabel("Search Friends", 12f, color = Color(0xFF837A99)); inner() } })
                    if (search.isNotEmpty()) LunarButton(Modifier.size(44.dp), "Clear search", reduced, onClick = { orbit.interrupt(); search = "" }) {
                        Icon(Icons.Default.Cancel, null, Modifier.size(15.dp), tint = Color(0xFF9189A5))
                    }
                }
                listOf(Triple(111f, "233 km", "Distance"), Triple(201f, "127", "Points"), Triple(286f, "34", "My Rank")).forEach { (x, value, title) ->
                    Column(Modifier.offset(x = (x - 43).dp, y = 602.dp).size(86.dp, 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        LunarLabel(value, 17f, FontWeight.Bold)
                        Spacer(Modifier.height(6.dp)); LunarLabel(title, 11.5f, color = LunarMuted)
                    }
                }
                Box(Modifier.offset(x = 67.dp, y = 673.dp).size(88.dp, 10.dp).background(Color(0xFF342B5A), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { LunarLabel("50% Completed", 7f) }
                LunarPill("Activity", 111f, 113f, reduced, onClick = { showMessage("Walking summary: 233 km and 127 points.") })
                LunarPill("Next milestone", 196.5f, 37f, reduced, Icons.Default.Bolt, onClick = { showMessage("Keep walking to reach your next activity milestone.") })
                LunarPill("Leaderboard", 282f, 113f, reduced, onClick = { showMessage("Your current rank is 34.") })
                LunarStar(Modifier.offset(x = 9.dp, y = 664.dp).size(22.dp, 28.dp), true)
                LunarStar(Modifier.offset(x = 295.5.dp, y = 354.5.dp).size(7.dp, 9.dp), false)
                Row(Modifier.offset(x = 14.dp, y = 757.dp).size(365.dp, 74.dp).background(Color(0xFF32264E), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp)) {
                    listOf("Home", "Walkathons", "Feed", "Shop").forEachIndexed { i, name ->
                        val tint by animateColorAsState(if (selectedNav == i) Color(0xFFEBDC3D) else Color(0xFFA29AAE), tween(if (reduced) 120 else 180, easing = LunarEaseOut))
                        LunarButton(Modifier.weight(1f).fillMaxHeight().semantics { selected = selectedNav == i }, name, reduced, onClick = { orbit.interrupt(); selectedNav = i }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                    if (selectedNav == i) Canvas(Modifier.requiredSize(36.dp)) { drawCircle(Brush.radialGradient(listOf(LunarYellow.copy(alpha = 0.28f), Color.Transparent))) }
                                    if (i == 0) LunarShoeprints(tint)
                                    else Icon(listOf(Icons.Default.DirectionsWalk, Icons.Default.DirectionsWalk, Icons.Default.Newspaper, Icons.Default.ShoppingBag)[i], null, Modifier.size(18.dp), tint = tint)
                                }
                                LunarLabel(name, 10f, color = tint)
                            }
                        }
                    }
                }
            }
        }
    }
    if (message != null) AlertDialog(onDismissRequest = { message = null }, title = { Text("Walking Activity") }, text = { Text(message.orEmpty()) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
}

@Composable
private fun LunarArt(image: ImageBitmap?, modifier: Modifier) {
    if (image != null) Image(image, contentDescription = null, modifier = modifier) else Box(modifier)
}

@Composable
private fun LunarLabel(value: String, size: Float, weight: FontWeight = FontWeight.Normal, color: Color = Color.White) {
    Text(value, color = color, fontSize = size.sp, fontWeight = weight, maxLines = 1, lineHeight = (size * 1.2f).sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
}

@Composable
private fun LunarButton(modifier: Modifier, label: String, reduced: Boolean, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val opacity by animateFloatAsState(if (pressed) 0.88f else 1f, tween(if (pressed) 120 else 100, easing = LunarEaseOut))
    val scale by animateFloatAsState(if (pressed && !reduced) 0.975f else 1f, tween(if (pressed) 120 else 100, easing = LunarEaseOut))
    Box(modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = label }
        .graphicsLayer { alpha = opacity; scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun LunarPill(label: String, centerX: Float, width: Float, reduced: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val hitWidth = max(44f, width)
    LunarButton(Modifier.offset(x = (centerX - hitWidth / 2).dp, y = 679.dp).size(hitWidth.dp, 44.dp), label, reduced, onClick) {
        Box(Modifier.size(width.dp, 36.dp).background(LunarYellow, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = Color.Black) else LunarLabel(label, 12f, FontWeight.Bold, Color.Black)
        }
    }
}

@Composable
private fun LunarFriendButton(friend: LunarFriend, image: ImageBitmap?, selected: Boolean, reduced: Boolean, pose: State<LunarMotion.Value>, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    val emphasized = selected && friend.id != 0
    val border by animateColorAsState(if (emphasized) LunarYellow else Color.White, tween(if (reduced) 120 else 180, easing = LunarEaseOut))
    val badgeScale by animateFloatAsState(if (emphasized && !reduced) 1.06f else 1f, tween(if (reduced) 120 else 180, easing = LunarEaseOut))
    val hitWidth = listOf(86f, 60f, 48f)[friend.id]; val hitHeight = listOf(88f, 82f, 54f)[friend.id]
    LunarButton(Modifier.offset(x = (friend.center.x - hitWidth / 2).dp, y = (friend.center.y - hitHeight / 2).dp).size(hitWidth.dp, hitHeight.dp).graphicsLayer {
        val p = LunarMotion.point(friend.center.x, friend.center.y, pose.value.q)
        translationX = (p.x - friend.center.x) * density; translationY = (p.y - friend.center.y) * density
    }.semantics { this.selected = selected }, "${friend.name}, level ${friend.level}", reduced, onClick) {
        Box(Modifier.size(friend.diameter.dp), contentAlignment = Alignment.Center) {
            LunarArt(image, Modifier.fillMaxSize().background(Color(0xFF8D8E85), CircleShape).clip(CircleShape).border(if (emphasized) 1.5.dp else 1.dp, border, CircleShape))
            val levelSize = if (friend.diameter < 40) 12f else 17f
            Box(Modifier.offset(x = (friend.diameter * 0.24f).dp, y = (friend.diameter * 0.48f).dp).size(levelSize.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                LunarLabel(friend.level, if (friend.diameter < 40) 5f else 6f, FontWeight.Bold, Color.Black)
            }
            val bw = listOf(28f, 22f, 18f)[friend.id]; val bh = listOf(32f, 25f, 21f)[friend.id]
            Canvas(Modifier.offset(x = listOf(29f, 19f, 14f)[friend.id].dp, y = (if (friend.id == 2) -16 else -28).dp).size(bw.dp, bh.dp).graphicsLayer { scaleX = badgeScale; scaleY = badgeScale }) {
                val amount = if (selected) min(1.0, abs(pose.value.q) / 0.065).toFloat() else 0f
                val vertices = List(6) { i -> val a = i * PI / 3 - PI / 2; Offset(size.width / 2 + cos(a).toFloat() * size.width / 2, size.height / 2 + sin(a).toFloat() * size.height / 2) }
                val path = Path().apply {
                    moveTo(vertices[0].x, vertices[0].y)
                    repeat(6) { i ->
                        val start = vertices[i]; val end = vertices[(i + 1) % 6]; val midpoint = (start + end) / 2f
                        val control = midpoint + (midpoint - center) * (0.309401f * amount)
                        quadraticTo(control.x, control.y, end.x, end.y)
                    }
                    close()
                }
                drawPath(path, friend.color)
                drawPath(path, if (emphasized) border else Color.White.copy(alpha = 0.9f), style = Stroke((if (emphasized) 2f else 1.5f).dp.toPx()))
            }
        }
    }
}

@Composable
private fun LunarCallout(friend: LunarFriend, noMatch: Boolean, pose: State<LunarMotion.Value>, opacity: Animatable<Float, *>) {
    val density = LocalDensity.current.density
    val accessible by remember { derivedStateOf { opacity.value >= 0.8f } }
    Box(Modifier.offset(x = 107.dp, y = 243.dp).size(154.dp, 59.dp).graphicsLayer {
        val p = LunarMotion.point(54f, 293f, pose.value.q)
        translationX = (p.x - 54) * density; translationY = (p.y - 293) * density; alpha = opacity.value
    }.semantics(mergeDescendants = true) {
        if (!accessible) hideFromAccessibility()
        contentDescription = if (noMatch) "No friends found" else if (friend.id == 0) "Ari just scored 345 points by 5 kilometre walk" else "${friend.name}, level ${friend.level}, walking friend"
    }, contentAlignment = Alignment.Center) {
        if (!noMatch) Canvas(Modifier.align(Alignment.CenterStart).offset(x = (-5).dp).size(10.dp, 14.dp)) {
            val path = Path().apply { moveTo(size.width, 0f); lineTo(0f, size.height / 2); lineTo(size.width, size.height); close() }
            drawPath(path, Color(0xFF1D1B43)); drawPath(path, Color.White.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
        }
        Box(Modifier.fillMaxSize().background(Color(0xFF1D1B43), RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            if (noMatch) LunarLabel("No friends found", 12f, color = Color(0xFFA49CB5))
            else Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (friend.id == 0) BasicText(buildAnnotatedString {
                    append("Ari just scored ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("345 pts") }
                }, style = TextStyle(color = Color(0xFFE0DFEB), fontSize = 12.sp, lineHeight = 14.4.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)), maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 10.8.sp, maxFontSize = 12.sp, stepSize = 0.1.sp))
                else LunarLabel("${friend.name} · ${friend.level}", 12f, FontWeight.SemiBold)
                LunarLabel(if (friend.id == 0) "by 5 km walk" else "Walking friend", 12f, color = LunarMuted)
            }
        }
    }
}

@Composable
private fun LunarBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val px = density
        drawRect(Brush.verticalGradient(colorStops = arrayOf(0f to Color(0xFF02013A), 0.5f to Color(0xFF02013A), 1f to Color(0xFF110248))))
        drawRect(Brush.radialGradient(listOf(Color(0xFF3F0070).copy(alpha = 0.18f), Color.Transparent), Offset(196.5f * px, 800f * px), 230f * px))
        repeat(38) { i -> val d = if (i % 4 == 0) 2f else 1f; drawOval(Color.White.copy(alpha = 0.22f), Offset(((i * 97 + 31) % 390) * px, ((i * 61 + 120) % 680) * px), androidx.compose.ui.geometry.Size(d * px, d * px)) }
        val orbit = Path().apply { moveTo(-30f * px, 292f * px); cubicTo(165f * px, 258f * px, 330f * px, 355f * px, 440f * px, 520f * px) }
        drawPath(orbit, Color.White.copy(alpha = 0.045f), style = Stroke(px))
    }
}

@Composable
private fun LunarShoeprints(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val unit = size.width / 18f
        for (i in 0..1) {
            val x = (if (i == 0) 4.5f else 12f) * unit
            val y = (if (i == 0) 10.5f else 6f) * unit
            rotate(if (i == 0) -25f else 25f, Offset(x, y)) {
                val toe = Path().apply {
                    moveTo(x - 2.5f * unit, y + unit)
                    cubicTo(x - 4f * unit, y - 5f * unit, x + 3f * unit, y - 6f * unit, x + 2.5f * unit, y)
                    quadraticTo(x, y + 2f * unit, x - 2.5f * unit, y + unit); close()
                }
                drawPath(toe, color)
                drawRoundRect(color, Offset(x - 1.6f * unit, y + 2f * unit), androidx.compose.ui.geometry.Size(3.2f * unit, 3f * unit), androidx.compose.ui.geometry.CornerRadius(unit))
            }
        }
    }
}

@Composable
private fun LunarStar(modifier: Modifier, gradient: Boolean) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width / 2, 0f)
            quadraticTo(center.x, center.y, size.width, size.height / 2)
            quadraticTo(center.x, center.y, size.width / 2, size.height)
            quadraticTo(center.x, center.y, 0f, size.height / 2)
            quadraticTo(center.x, center.y, size.width / 2, 0f); close()
        }
        if (gradient) drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.02f))))
        else drawPath(path, Color.White.copy(alpha = 0.85f))
    }
}

