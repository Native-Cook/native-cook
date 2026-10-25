package com.ngocdevv.nativecook.blocks.buttons.fuseburnbutton

import android.graphics.BlurMaskFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Fuse Burn Button — an event's ticket screen, "Launch Night — Rooftop 27, 21:00". A braided fuse
 is wrapped round the "Light it up" capsule. In the loop (FuseBurnButtonMotion.kt, LOOP = 9 s) a
 match flares at the capsule's left end, a spark runs clockwise round it (sputtering once at 40 %),
 the cord behind it turns to crinkled charcoal under rising smoke, and when the spark meets the
 start the capsule bursts into 30 sparks, lights up amber, reads "You're in", and the count of
 people going rolls from 211 to 212; then the cord re-weaves itself and the screen rests.

 Touch: hold the capsule to burn (a full lap takes 4.2 s); let go early and the spark sputters
 and dies where it is, the next hold relights it there. Drag a finger along the cord just ahead
 of the spark to pull the burn along (the finger is the match). 2 s after the last touch the cord
 re-weaves (0.85 s) and the loop starts again. Reduced motion: no spark or smoke; a tap on the
 capsule fades it between "Light it up" and "You're in".
 */

private val Cream = Color(0xFFFFE3B8)
private val Ink = Color(0xFFEEF0FA)
private val Mute = Color(214, 220, 245, 158)
private val InText = Color(0xFF3A1606)
internal val FuseNight = Color(0xFF0D1226)

/** The count's line height (it rolls by one line). */
private const val COUNT_H = 22f
private val AVATARS = listOf("JR" to Color(0xFFC46B4A), "AM" to Color(0xFF5F7FBF), "SK" to Color(0xFF8C6BB8))

// The fuse sits FUSE_OFFSET dp outside the capsule's outline.
private const val FUSE_OFFSET = 8f
// Burning under a held finger: a whole lap in 1 / BURN_RATE s.
private const val BURN_RATE = 0.24f
// A drag pulls the burn along when it lands within SCRUB_REACH dp of the cord, ahead of the
// spark by less than SCRUB_AHEAD of a lap.
private const val SCRUB_REACH = 34f
private const val SCRUB_AHEAD = 0.12f
private const val IDLE = 2f
private const val RESET = 0.85f
// Touch slop round the capsule.
private const val SLOP = 22f

// Seconds from a fixed origin, so a float keeps millisecond precision however long the device has run.
private val FuseOrigin = System.nanoTime()

private fun nowSeconds() = (System.nanoTime() - FuseOrigin) / 1e9f

private class FuseEngine(val reduced: Boolean, val fuse: FuseLine) {
    var clock = 0f
    var user = reduced
    var lastTouch = 0f
    var resetAt = -1f
    var holding = false
    var fired = false
    var b = 0f
    val w = FuseTrack(0f)
    val spark = FuseTrack(0f)
    var flareAt = Float.NaN
    val flareFrom = FuseTrack(0f)
    var speed = 0f
    var burstAt = Float.NaN
    val lit = FuseTrack(0f)
    val label = FuseTrack(0f)
    val count = FuseTrack(0f)
    var running = false
    /** Haptics the frame loop owes: 1 = success (the burst). */
    var pendingBurst = false

    private val pose = FusePose()
    private var poseAt = Float.NaN
    private var poseClock = Float.NaN
    private val near = FloatArray(2)

    fun busy(now: Float): Boolean {
        if (holding) return true
        if (reduced) return !(lit.done(now) && label.done(now) && count.done(now))
        return user
    }

    private fun flare(now: Float): Float {
        val u = now - flareAt
        return when {
            flareAt.isNaN() -> flareFrom.value(now)
            u < 0.11f -> fuseEaseInOut2(u / 0.11f)
            u < 0.43f -> 1f - FuseEaseOutCurve((u - 0.11f) / 0.32f)
            else -> 0f
        }
    }

    fun pose(now: Float): FusePose {
        if (now == poseAt && clock == poseClock) return pose
        poseAt = now
        poseClock = clock
        if (!user) {
            pose.setLoop(clock, fuse.length)
            return pose
        }
        pose.b = b
        pose.w = w.value(now)
        pose.spark = if (reduced) 0f else spark.value(now)
        pose.flare = flare(now)
        pose.speed = speed
        pose.burstAge = if (burstAt.isNaN()) -1f else now - burstAt
        pose.lit = lit.value(now)
        pose.label = label.value(now)
        pose.count = count.value(now)
        pose.time = now
        return pose
    }

    fun touched(now: Float) {
        lastTouch = now
        resetAt = -1f
        if (user) return
        val p = FusePose().apply { setLoop(clock, fuse.length) }
        b = p.b
        w.set(p.w)
        spark.set(p.spark)
        flareAt = Float.NaN
        flareFrom.set(p.flare)
        flareFrom.timing(now, 0f, 0.32f, FuseEaseOutCurve)
        speed = 0f
        burstAt = if (p.burstAge >= 0f) now - p.burstAge else Float.NaN
        lit.set(p.lit)
        label.set(p.label)
        count.set(p.count)
        fired = p.b >= 0.999f
        user = true
    }

    private fun burst(now: Float) {
        fired = true
        burstAt = now
        lit.timing(now, 1f, 0.3f, FuseEaseOutCurve)
        label.timing(now, 1f, 0.5f, FuseEaseOutCurve)
        count.timing(now, 1f, 0.55f, FuseEaseInOutCurve)
        pendingBurst = true
    }

    fun step(now: Float, dt: Float, auto: Boolean) {
        if (!user) {
            if (auto) clock = (clock + dt) % LOOP
            return
        }
        if (reduced) return
        if (holding && !fired) {
            // Burning: the spark brightens quickly and runs at a slightly uneven pace.
            val pace = BURN_RATE * (0.85f + 0.3f * sin(now * 3.1f) * sin(now * 1.7f))
            b = min(1f, b + dt * pace)
            speed = pace * fuse.length
            val s = spark.value(now)
            spark.set(s + (1f - s) * min(1f, dt * 12f))
            if (b >= 1f) burst(now)
        } else if (spark.done(now) && spark.value(now) > 0.001f) {
            // Let go: the spark sputters and dies where it is.
            speed = 0f
            spark.set(spark.value(now) * exp(-dt * 3.2f) * (0.92f + 0.08f * sin(now * 40f)))
        }
        if (holding) return
        if (resetAt < 0f && now - lastTouch > IDLE) {
            w.timing(now, 1f, RESET, FuseEaseInOutCurve)
            spark.timing(now, 0f, 0.25f)
            lit.timing(now, 0f, RESET, FuseEaseInOutCurve)
            label.timing(now, 0f, RESET, FuseEaseInOutCurve)
            count.timing(now, 0f, RESET, FuseEaseInOutCurve)
            resetAt = now + RESET + 0.05f
        } else if (resetAt > 0f && now >= resetAt) {
            resetAt = -1f
            clock = 0f
            user = false
        }
    }

    /** A finger landed on the capsule; returns true for a fresh light (the match's haptic). */
    fun down(now: Float): Boolean {
        touched(now)
        if (reduced) {
            val on = label.value(now) > 0.5f
            val to = if (on) 0f else 1f
            lit.timing(now, to, 0.3f, FuseEaseInOutCurve)
            label.timing(now, to, 0.3f, FuseEaseInOutCurve)
            count.timing(now, to, 0.3f, FuseEaseInOutCurve)
            return false
        }
        holding = true
        if (!fired && spark.value(now) < 0.2f) {
            // A fresh light: the match flares.
            flareAt = now
            return true
        }
        return false
    }

    /** The finger moved to (x, y) in screen dp: it pulls the burn along when just ahead of the spark. */
    fun move(now: Float, x: Float, y: Float) {
        if (reduced || fired) return
        lastTouch = now
        fuseNearest(fuse, x, y, near)
        val u = near[0]
        val d = near[1]
        if (d < SCRUB_REACH && u > b && u - b < SCRUB_AHEAD) {
            speed = (u - b) * fuse.length / 0.016f
            spark.set(max(spark.value(now), 0.85f))
            b = u
            if (u >= 1f) burst(now)
        }
    }

    fun up(now: Float) {
        holding = false
        lastTouch = now
    }
}

private class FuseArt(val still: ImageBitmap?)

/**
 * An event ticket whose button is wrapped in a braided fuse: hold it to light the fuse, and a
 * spark runs round the capsule, burning the cord to charcoal under smoke, until the capsule bursts
 * into sparks and reads "You're in". Drag along the cord ahead of the spark to pull the burn along.
 *
 * @param autoPlay lights, burns, bursts and re-weaves the fuse by itself, on a loop of [LOOP] s.
 */
@Composable
fun FuseBurnButton(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density
    BoxWithConstraints(modifier.fillMaxSize().background(FuseNight)) {
        val layout = remember(maxWidth, maxHeight, top, bottom) { fuseLayout(maxWidth.value, maxHeight.value, top, bottom) }
        FuseScene(layout, autoPlay && !reduced, reduced)
    }
}

@Composable
private fun FuseScene(layout: FuseLayout, auto: Boolean, reduced: Boolean) {
    val density = LocalDensity.current
    val d = density.density
    val view = LocalView.current
    val cap = layout.capsule
    val fuse = remember(cap) { fuseMake(cap.x, cap.y, cap.w, cap.h, FUSE_OFFSET) }
    val engine = remember(reduced, fuse) { FuseEngine(reduced, fuse) }
    val tick = remember { mutableFloatStateOf(nowSeconds()) }
    val wake = remember { mutableIntStateOf(0) }
    val art by produceState<FuseArt?>(null, layout, d) {
        value = withContext(Dispatchers.Default) { FuseArt(fuseBakeStatic(layout, d)?.asImageBitmap()) }
    }
    val cx = cap.x + cap.w / 2f
    val cy = cap.y + cap.h / 2f
    val radius = cap.h / 2f
    val paths = remember { FusePaths() }
    val paints = remember { FusePaints() }

    LaunchedEffect(engine, auto, wake.intValue) {
        engine.running = true
        var last = -1L
        try {
            while (true) {
                val keepGoing = withFrameNanos { nanos ->
                    val now = (nanos - FuseOrigin) / 1e9f
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    engine.step(now, dt, auto)
                    if (engine.pendingBurst) {
                        engine.pendingBurst = false
                        val kind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                        view.performHapticFeedback(kind)
                    }
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

    val litBrush = remember(cap) {
        Brush.linearGradient(
            listOf(Color(0xFFFFC06A), Color(0xFFFF9444), Color(0xFFF2672C), Color(0xFFFF8C3E)),
            start = Offset(cap.x, cap.y), end = Offset(cap.x + cap.w, cap.y + cap.h),
        )
    }
    val rimBrush = remember(cap) {
        Brush.verticalGradient(
            listOf(Color(255, 255, 255, 71), Color(255, 255, 255, 5), Color(0, 0, 0, 64)),
            startY = cap.y, endY = cap.y + cap.h,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val now = tick.floatValue
            val p = engine.pose(now)
            art?.still?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            scale(d, d, pivot = Offset.Zero) {
                fuseAt(fuse, p.b, paths.at)
                val sx = paths.at[0]
                val sy = paths.at[1]
                val corner = CornerRadius(radius, radius)
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    // The spark's warmth inside the capsule, before it lights.
                    val inside = p.spark * 0.45f * (1f - p.lit)
                    if (inside > 0.002f) {
                        paints.inside.alpha = (inside * 255f).roundToInt().coerceIn(0, 255)
                        paints.matrix.setScale(160f, 160f)
                        paints.matrix.postTranslate(sx, sy)
                        paints.insideShader.setLocalMatrix(paints.matrix)
                        n.drawRoundRect(cap.x, cap.y, cap.x + cap.w, cap.y + cap.h, radius, radius, paints.inside)
                    }
                }
                if (p.lit > 0.002f) drawRoundRect(litBrush, Offset(cap.x, cap.y), Size(cap.w, cap.h), corner, alpha = p.lit.coerceIn(0f, 1f))
                val flash = if (p.burstAge < 0f) 0f else exp(-p.burstAge * 5f) * 0.8f
                if (flash > 0.002f) drawRoundRect(Color(0xFFFFF4DC), Offset(cap.x, cap.y), Size(cap.w, cap.h), corner, alpha = flash)
                drawRoundRect(
                    rimBrush, Offset(cap.x + 0.5f, cap.y + 0.5f), Size(cap.w - 1f, cap.h - 1f),
                    CornerRadius(radius - 0.5f, radius - 0.5f), style = Stroke(1f),
                )

                fuseCordPaths(paths, fuse, p.b, p.w)
                fuseSparkPaths(paths, fuse, p, cx, cy)
                fuseSmokePath(paths, fuse, p)
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    // The fuse: a dark core, the braid, its twist highlights; charcoal and embers behind the spark.
                    n.drawPath(paths.cord, paints.cordCore)
                    n.drawPath(paths.cord, paints.cordBraid)
                    n.drawPath(paths.ticks, paints.ticks)
                    val charA = 1f - p.w
                    if (charA > 0.002f) {
                        paints.charShadow.alpha = (0.7f * charA * 255f).roundToInt()
                        paints.charBody.alpha = (charA * 255f).roundToInt()
                        paints.charDots.alpha = (0.55f * charA * 255f).roundToInt()
                        n.drawPath(paths.charcoal, paints.charShadow)
                        n.drawPath(paths.charcoal, paints.charBody)
                        n.drawPath(paths.charcoal, paints.charDots)
                    }
                    val ember = min(1f, p.spark * 1.2f) * (1f - p.w)
                    if (ember > 0.002f) {
                        paints.ember.alpha = (ember * 255f).roundToInt().coerceIn(0, 255)
                        n.drawPath(paths.ember, paints.ember)
                    }
                    // Smoke, then the spark: a glow, its core and the thrown sparks (added light).
                    val smoke = fuseSmokeOpacity(p)
                    if (smoke > 0.002f) {
                        paints.smoke.alpha = (smoke * 255f).roundToInt().coerceIn(0, 255)
                        n.drawPath(paths.smoke, paints.smoke)
                    }
                    val glowR = 20f + p.flare * 34f
                    val glowA = min(1f, p.spark + p.flare)
                    if (glowA > 0.002f) {
                        paints.glow.alpha = (glowA * 255f).roundToInt().coerceIn(0, 255)
                        paints.matrix.setScale(glowR, glowR)
                        paints.matrix.postTranslate(sx, sy)
                        paints.glowShader.setLocalMatrix(paints.matrix)
                        n.drawCircle(sx, sy, glowR, paints.glow)
                    }
                    n.drawPath(paths.warm, paints.warm)
                    n.drawPath(paths.hot, paints.hot)
                    if (p.spark > 0.002f) {
                        paints.core.alpha = (p.spark * 255f).roundToInt().coerceIn(0, 255)
                        n.drawCircle(sx, sy, 2.6f, paints.core)
                    }
                }
            }
        }

        // The header.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset { IntOffset(0, (layout.headerTop * d).roundToInt()) },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("LAUNCH NIGHT", style = TextStyle(color = Color(0xFFFFB46B), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
            BasicText("Rooftop 27", style = TextStyle(color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp))
            BasicText(
                "Sat 22 Nov · 21:00 · Pier District",
                style = TextStyle(color = Mute, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
        }

        // The poster's lineup.
        val poster = layout.poster
        Column(
            Modifier
                .offset { IntOffset(((poster.x + 16f) * d).roundToInt(), ((poster.y + poster.h - 64f) * d).roundToInt()) }
                .width((poster.w - 32f).dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.height(20.dp).background(Color(10, 10, 30, 140), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("LINEUP", style = TextStyle(color = Color(0xFFFFD9A8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
            }
            BasicText(
                "Mara Quell · The Low Tides · Okoro",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color(0xFFFBF3EA), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color(10, 6, 30, 153), Offset(0f, 1f * d), 8f * d),
                ),
            )
        }

        // Who is going, and the ticket.
        Column(
            Modifier.offset { IntOffset((cap.x * d).roundToInt(), (layout.rowsTop * d).roundToInt()) }.width(cap.w.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                // Three 28 dp avatars, each overlapping the one before by 8 dp.
                Box(Modifier.padding(end = 10.dp).size(68.dp, 28.dp)) {
                    AVATARS.forEachIndexed { i, (initials, color) ->
                        Box(
                            Modifier
                                .offset(x = (20 * i).dp)
                                .size(28.dp)
                                .background(color, CircleShape)
                                .border(2.dp, Color(0xFF111833), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(initials, style = TextStyle(color = Color(0xFFFFF6EE), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
                val countStyle = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                Box(Modifier.padding(end = 5.dp).height(COUNT_H.dp).clipToBounds()) {
                    Column(Modifier.graphicsLayer { translationY = -engine.pose(tick.floatValue).count * COUNT_H * d }) {
                        BasicText("211", Modifier.height(COUNT_H.dp), style = countStyle)
                        BasicText("212", Modifier.height(COUNT_H.dp), style = countStyle)
                    }
                }
                BasicText("going · 12 friends", style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            }
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("General admission", Modifier.weight(1f), style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                BasicText("$24.00", style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
        }

        // The capsule's labels and hit area.
        Box(
            Modifier
                .offset { IntOffset(((cap.x - SLOP) * d).roundToInt(), ((cap.y - SLOP) * d).roundToInt()) }
                .size((cap.w + SLOP * 2).dp, (cap.h + SLOP * 2).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Light it up. Hold to burn the fuse."
                }
                .pointerInput(engine) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (engine.down(nowSeconds())) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        wakeUp()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.pressed } ?: break
                            if (change.position != change.previousPosition) {
                                // The hit area starts SLOP dp above and left of the capsule.
                                engine.move(nowSeconds(), change.position.x / d + cap.x - SLOP, change.position.y / d + cap.y - SLOP)
                            }
                        }
                        engine.up(nowSeconds())
                        wakeUp()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val labelStyle = TextStyle(color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
            Row(
                Modifier.graphicsLayer {
                    val l = engine.pose(tick.floatValue).label
                    alpha = (1f - l).coerceIn(0f, 1f)
                    translationY = -l * 8f * d
                    scaleX = 1f - l * 0.04f
                    scaleY = 1f - l * 0.04f
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FuseFlame()
                BasicText("Light it up", style = labelStyle)
            }
            Row(
                Modifier.graphicsLayer {
                    val l = engine.pose(tick.floatValue).label
                    alpha = l.coerceIn(0f, 1f)
                    translationY = (1f - l) * 8f * d
                    scaleX = 0.96f + l * 0.04f
                    scaleY = 0.96f + l * 0.04f
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FuseCheck()
                BasicText("You're in", style = labelStyle.copy(color = InText))
            }
        }
        BasicText(
            "Hold to light · drag along the fuse",
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset { IntOffset(0, (layout.hintTop * d).roundToInt()) },
            style = TextStyle(color = Color(214, 220, 245, 115), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

/** The paints of the moving parts, made once. Coordinates are dp. */
private class FusePaints {
    val matrix = Matrix()
    private fun stroke(width: Float, color: Int, cap: Paint.Cap = Paint.Cap.ROUND) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = cap
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)

    val cordCore = stroke(6.2f, 0xFF3B2812.toInt())
    val cordBraid = stroke(4.4f, 0xFFB98D55.toInt())
    val ticks = stroke(1.2f, 0xD9F0D7A2.toInt())
    val charShadow = stroke(3.4f, 0xFF0B0A0C.toInt())
    val charBody = stroke(2.4f, 0xFF3A3230.toInt())
    val charDots = stroke(0.8f, 0xFF9A8E84.toInt(), Paint.Cap.BUTT).apply { pathEffect = DashPathEffect(floatArrayOf(1.5f, 3.5f), 0f) }
    val ember = stroke(3.4f, 0xFFFF6A2E.toInt()).apply {
        xfermode = add
        maskFilter = BlurMaskFilter(fuseSigmaToRadius(2f), BlurMaskFilter.Blur.SOLID)
    }
    val smoke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA3C4.toInt()
        maskFilter = BlurMaskFilter(fuseSigmaToRadius(7f), BlurMaskFilter.Blur.NORMAL)
    }
    // Unit-radius gradients placed per frame with a local matrix.
    val glowShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xFFFFF6E0.toInt(), 0xFFFFD27A.toInt(), 0x73FF6A2E, 0x00FF6A2E),
        floatArrayOf(0f, 0.18f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = glowShader
        xfermode = add
    }
    val insideShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xE6FF8C3C.toInt(), 0x40FF6E32, 0x00FF6E32), null, Shader.TileMode.CLAMP,
    )
    val inside = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = insideShader }
    val warm = stroke(1.3f, 0xFFFF9A3C.toInt()).apply { xfermode = add }
    val hot = stroke(1.7f, 0xFFFFF0C2.toInt()).apply { xfermode = add }
    val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFAF0.toInt() }
}

private val FlameBody = PathParser().parsePathString(
    "M8 1.2c.6 2.4 3.9 4.3 3.9 8.4A3.9 3.9 0 0 1 8 13.5a3.9 3.9 0 0 1-3.9-3.9c0-1.7.8-2.9 1.7-3.8.2 1.1.8 1.9 1.6 2.2C7 6 6.9 3.4 8 1.2z",
).toPath()
private val CheckMark = PathParser().parsePathString("M3.5 9.5l3.6 3.6 7.4-8").toPath()

@Composable
private fun FuseFlame() {
    Canvas(Modifier.size(16.dp, 18.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            drawPath(FlameBody, Cream)
            drawLine(Cream, Offset(3f, 16.2f), Offset(13f, 16.2f), strokeWidth = 1.6f, cap = StrokeCap.Round, alpha = 0.6f)
        }
    }
}

@Composable
private fun FuseCheck() {
    Canvas(Modifier.size(18.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            drawPath(CheckMark, InText, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
