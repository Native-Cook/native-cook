package com.ngocdevv.nativecook.blocks.animations.radarfriendfinder

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Beacon's nearby-friends screen. This ports the executable RN reference, including its
// bundled fonts, rather than the older layout numbers in that file's header comment.
// Scope radius 168 dp, centre (width / 2, height * .47), title 34 / 40 sp, card box 84 dp.
// LOOP = 6 s: two linear 3 s sweeps with a 60 degree wake; blips decay exp(-2.2 * age).
// Maya locks from 3.4–3.9 s, holds to 5.4 s, and unlocks from 5.4–5.9 s. The avatar uses
// ease-out-back (k = 1.6); the dashed route and the card's 24 dp lift use ease-out-cubic.
// A drag steers immediately. Release within .52 rad of a friend gives one light haptic,
// locks over .4 s, holds until 2 s after release, then fades over .4 s. The beam drifts at
// quarter speed during that hold and rejoins autoplay without an angular jump.
// Reduced motion, like RN, freezes the initial upward beam with scanning copy and no lock.

/** Seconds. */
const val LOOP = 6f
private const val TURN = 3f
private const val SCOPE = 168f
private const val HOLD = 2f
private const val TAU = 2 * PI
private val BACKGROUND = Color(0xFF06110C)
private val GREEN = Color(0xFF2BFF88)
private val INK = Color(0xFFE8FFF2)

private data class RadarFriend(
    val initials: String,
    val name: String,
    val bearing: Double,
    val range: Float,
    val meters: Int,
    val color: Color,
)

private val FRIENDS = listOf(
    RadarFriend("MA", "Maya", .95, .55f, 120, Color(0xFFFF7AB6)),
    RadarFriend("JO", "Jonah", 2.4, .78f, 260, Color(0xFF7CC8FF)),
    RadarFriend("RI", "Rina", 3.3, .4f, 90, Color(0xFFFFD45C)),
    RadarFriend("TE", "Teo", 4.25, .86f, 310, Color(0xFFB79CFF)),
    RadarFriend("SA", "Sami", 5.2, .62f, 180, Color(0xFF7CFFB2)),
    RadarFriend("LU", "Luz", .15, .9f, 340, Color(0xFFFF9E6B)),
)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeOutBack(x: Float): Float = 1 + 2.6f * (x - 1).pow(3) + 1.6f * (x - 1).pow(2)
private fun easeOutCubic(x: Float): Float = 1 - (1 - x).pow(3)
private fun lockAt(t: Float) = seg(t, 3.4f, 3.9f) * (1 - seg(t, 5.4f, 5.9f))

/** Only draw/layer blocks read these animated states; the screen never recomposes per frame. */
private class RadarClock {
    var beam by mutableFloatStateOf((-PI / 2).toFloat())
    var lock by mutableFloatStateOf(0f)
    var locked by mutableIntStateOf(0)
    var touching = false
    var manual = false
    private var elapsed = 0.0
    private var origin = 0.0
    private var released = -1.0
    private var lastNear = -1

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (touching) {
            lock = (lock - dt.toFloat() * 4).coerceAtLeast(0f)
            return
        }
        if (manual) {
            val since = (elapsed - released).toFloat()
            lock = if (lastNear >= 0) {
                if (since < HOLD) clamp01(since / .4f) else 1 - clamp01((since - HOLD) / .4f)
            } else 0f
            beam += (dt / TURN * TAU * .25).toFloat()
            if (since > HOLD + .4f) {
                manual = false
                locked = 0
                // Kotlin's signed remainder matches JS here, including negative bearings.
                origin = elapsed - (((beam + PI / 2) / TAU) % 1) * TURN
            }
            return
        }
        val t = ((elapsed - origin) % LOOP).toFloat()
        beam = if (autoPlay) (-PI / 2 + TAU * t / TURN).toFloat() else (-PI / 2).toFloat()
        lock = if (autoPlay) lockAt(t) else 0f
    }

    fun steer(x: Float, y: Float, cx: Float, cy: Float) {
        beam = atan2(y - cy, x - cx)
    }

    fun release(): Boolean {
        touching = false
        var best = -1
        var bestD = .52
        FRIENDS.forEachIndexed { i, friend ->
            var d = abs((((beam - friend.bearing) % TAU) + PI * 3) % TAU - PI)
            d = min(d, TAU - d)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        lastNear = best
        if (best >= 0) locked = best
        released = elapsed
        return best >= 0
    }
}

private fun radarPaint(color: Color, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    this.alpha = (clamp01(alpha) * 255).roundToInt()
}

/** Static face and solid/blurred halo are rasterized once, including on Android 8. */
private class RadarScope(density: Float, bold: Typeface) {
    private val padding = SCOPE + 2f
    private val face = Bitmap.createBitmap(ceil(padding * 2 * density).toInt(), ceil(padding * 2 * density).toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
        val c = android.graphics.Canvas(bitmap)
        c.scale(density, density)
        c.translate(padding, padding)
        val fill = radarPaint(GREEN).apply {
            shader = RadialGradient(0f, 0f, SCOPE, intArrayOf(0xFF0E2A1C.toInt(), 0xFF081A11.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawCircle(0f, 0f, SCOPE, fill)
        val ring = radarPaint(GREEN, .18f).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
        for (r in 1..4) c.drawCircle(0f, 0f, SCOPE * r / 4, ring)
        c.drawLine(-SCOPE, 0f, SCOPE, 0f, ring)
        c.drawLine(0f, -SCOPE, 0f, SCOPE, ring)
    }
    private val faceBounds = RectF(-padding, -padding, padding, padding)
    private val halo = Bitmap.createBitmap(ceil(88 * density).toInt(), ceil(88 * density).toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
        val c = android.graphics.Canvas(bitmap)
        c.scale(density, density)
        val p = radarPaint(GREEN).apply {
            // Android's blur radius maps to Skia's sigma; RN uses sigma 8, SOLID style.
            maskFilter = BlurMaskFilter((8f - .5f) / .57735f, BlurMaskFilter.Blur.SOLID)
        }
        c.drawCircle(44f, 44f, 10f, p)
    }
    private val haloBounds = RectF()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val wakeGradient = SweepGradient(0f, 0f, intArrayOf(0x002BFF88, 0x002BFF88, 0x6B2BFF88), floatArrayOf(0f, .833f, 1f))
    private val wake = radarPaint(GREEN).apply { shader = wakeGradient }
    private val rotation = Matrix()
    private val edge = radarPaint(GREEN, .95f).apply { strokeWidth = 2f }
    private val dot = radarPaint(GREEN)
    private val small = radarPaint(GREEN).apply { typeface = bold; textSize = 13f; isSubpixelText = true }
    private val route = radarPaint(GREEN).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }
    private val line = Path()
    private val ring = radarPaint(GREEN).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val avatar = radarPaint(GREEN)
    private val initials = radarPaint(BACKGROUND).apply { typeface = bold; textSize = 13f; isSubpixelText = true }
    private val positions = FRIENDS.map { Pair((cos(it.bearing) * it.range * SCOPE).toFloat(), (sin(it.bearing) * it.range * SCOPE).toFloat()) }

    fun draw(c: android.graphics.Canvas, beam: Float, lock: Float, locked: Int) {
        c.drawBitmap(face, null, faceBounds, bitmapPaint)
        rotation.setRotate((beam * 180 / PI).toFloat())
        wakeGradient.setLocalMatrix(rotation)
        c.drawCircle(0f, 0f, SCOPE, wake)
        c.drawLine(0f, 0f, cos(beam) * SCOPE, sin(beam) * SCOPE, edge)
        FRIENDS.forEachIndexed { i, f ->
            val (x, y) = positions[i]
            val age = (((beam - f.bearing) % TAU + TAU) % TAU) / TAU * TURN
            val glow = exp(-2.2 * age).toFloat()
            val isLock = if (i == locked) lock else 0f
            glowPaint.alpha = (.35f * glow * 255).roundToInt()
            haloBounds.set(x - 44, y - 44, x + 44, y + 44)
            c.drawBitmap(halo, null, haloBounds, glowPaint)
            dot.alpha = ((.35f + .65f * glow) * 255).roundToInt()
            c.drawCircle(x, y, 4 + 2 * glow, dot)
            if (isLock > 0f) {
                val s = easeOutBack(clamp01(isLock))
                route.color = f.color.toArgb()
                route.alpha = (.8f * isLock * 255).roundToInt()
                line.rewind()
                line.moveTo(0f, 0f)
                line.lineTo(x * easeOutCubic(isLock), y * easeOutCubic(isLock))
                c.drawPath(line, route)
                ring.color = f.color.toArgb()
                ring.alpha = (isLock * 255).roundToInt()
                c.drawCircle(x, y, 26 * s + 6 * sin(beam * 4) * isLock, ring)
                avatar.color = f.color.toArgb()
                c.drawCircle(x, y, 20 * s, avatar)
                c.save()
                c.translate(x - 9 * s, y + 4.5f * s)
                c.scale(s, s)
                c.drawText(f.initials, 0f, 0f, initials)
                c.restore()
            } else {
                small.alpha = ((.4f + .6f * glow) * 255).roundToInt()
                c.drawText(f.initials, x + 9, y - 6, small)
            }
        }
        dot.alpha = 255
        c.drawCircle(0f, 0f, 5f, dot)
    }
}

private fun radarText(font: FontFamily, size: Float, color: Color, lineHeight: Float = size * 1.32f) = TextStyle(
    fontFamily = font,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
fun RadarFriendFinder(
    modifier: Modifier = Modifier,
    brand: String = "beacon",
    title: String = "Find your\ncrew",
    action: String = "Walk me there",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val boldFace = remember(context) { Typeface.createFromAsset(context.assets, "radarfriendfinder/radarfriendfinder_JetBrainsMono-Bold.ttf") }
    val mediumFace = remember(context) { Typeface.createFromAsset(context.assets, "radarfriendfinder/radarfriendfinder_JetBrainsMono-Medium.ttf") }
    val bold = remember(boldFace) { FontFamily(androidx.compose.ui.text.font.Typeface(boldFace)) }
    val medium = remember(mediumFace) { FontFamily(androidx.compose.ui.text.font.Typeface(mediumFace)) }
    val scope = remember(density, boldFace) { RadarScope(density, boldFace) }
    val clock = remember { RadarClock() }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BlockSystemBars(darkIcons = false)

    LaunchedEffect(lifecycle, reduced) {
        if (reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                clock.advance(dt, auto)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val cx = maxWidth.value / 2
        val cy = maxHeight.value * .47f
        Box(
            Modifier.fillMaxSize().pointerInput(reduced, cx, cy) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    clock.touching = true
                    clock.manual = true
                    clock.steer(down.position.x / density, down.position.y / density, cx, cy)
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            clock.steer(change.position.x / density, change.position.y / density, cx, cy)
                            change.consume()
                        }
                    } finally {
                        if (clock.release()) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                }
            },
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = insets.calculateTopPadding() + 8.dp)) {
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(brand, style = radarText(bold, 20f, GREEN))
                    Spacer(Modifier.weight(1f))
                    Text("● LIVE · 6 friends", style = radarText(medium, 12f, GREEN.copy(alpha = .7f)))
                }
                Text(title, Modifier.padding(top = 14.dp), style = radarText(bold, 34f, INK, 40f).copy(letterSpacing = (-1).sp))
            }
            Canvas(
                Modifier.fillMaxSize().semantics {
                    contentDescription = "Radar of nearby friends. Drag round the scope to steer the beam."
                },
            ) {
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    c.save()
                    c.scale(density, density)
                    c.translate(cx, cy)
                    scope.draw(c, clock.beam, clock.lock, clock.locked)
                    c.restore()
                }
            }
        }

        // RN always shows Maya's card, even when touch selected another friend on the scope.
        val target = FRIENDS[0]
        Column(
            Modifier.align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = insets.calculateBottomPadding() + 14.dp)
                .pointerInput(Unit) {
                    // The footer is outside RN's gesture detector; it does not steer the beam.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Scanning 400 m around you…",
                    Modifier.graphicsLayer { alpha = 1 - clamp01(clock.lock * 2) },
                    style = radarText(medium, 14f, GREEN.copy(alpha = .75f)),
                )
                Row(
                    Modifier.fillMaxWidth().graphicsLayer {
                        val k = easeOutCubic(clamp01(clock.lock))
                        alpha = k
                        translationY = 24 * (1 - k) * density
                    }.background(Color(0xFF0F2A1D), RoundedCornerShape(22.dp))
                        .border(1.dp, GREEN.copy(alpha = .25f), RoundedCornerShape(22.dp))
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).background(target.color, CircleShape), contentAlignment = Alignment.Center) {
                        Text(target.initials, style = radarText(bold, 15f, BACKGROUND))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${target.name} is near the main stage", style = radarText(bold, 15f, INK))
                        Text("${target.meters} m · south-east · 2 min walk", Modifier.padding(top = 3.dp), style = radarText(medium, 13f, INK.copy(alpha = .6f)))
                    }
                }
            }
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(58.dp).background(GREEN, CircleShape).semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = radarText(bold, 16f, BACKGROUND))
            }
        }
    }
}
