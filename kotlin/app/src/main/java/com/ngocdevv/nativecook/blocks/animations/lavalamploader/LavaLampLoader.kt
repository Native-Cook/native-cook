package com.ngocdevv.nativecook.blocks.animations.lavalamploader

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Lava Lamp Loader — "Glowhaus", a music-sync screen whose loader is a lava lamp on a walnut shelf.
 Wax heats on the pool at the bottom, swells, necks off and rises; it merges with the blob under
 the cap, splits, and the cooled half sinks back. The tracks counter clicks up as it syncs.

 Drawing: one Canvas with one AGSL shader (LavaLampLoaderShader.kt) for the wall, shelf, chrome and
 the metaball wax (a drawn stand-in below Android 13, LavaLampLoaderFallback.kt). The blob uniforms
 come from one pose array written per frame by the frame loop; the odometer, the bar and the sync
 icon read it in graphicsLayer lambdas, so nothing recomposes while the loop runs or under a finger.

 Touch: a finger on the glass warms the wax there: a blob grows under it (about 1 s) and follows
 it; the other blobs are pushed away from the finger and dragged along its motion. Let go and the
 warmed blob rises to the cap. 2 s after the last touch the wax eases back to its resting pose
 (0.9 s) and the loop starts again. Reduced motion: the resting lamp, no loop.

 LOOP = 9 s:
   0.0 – 1.6  rest: A and B sit on the warm pool, C floats under the cap, D and E drift.
   1.6 – 2.4  A swells and stretches, dips 3 dp first (anticipation), and necks off the pool.
   2.4 – 4.6  A rises (ease-in-out) with a sideways sway, tall and thin while it climbs.
   4.6 – 5.4  A meets C under the cap and they merge.
   5.4 – 7.6  they split: C sinks to A's old seat (a small squash on landing, B nudged by it)
              while A shrinks into C's old place.
   7.6 – 9.0  rest: the frame at 9 s is the frame at 0 s.
*/

private const val LOOP = 9f
private const val POSE_LEN = 24
internal const val LAVA_CAP_END = 0.13f
internal const val LAVA_GLASS_END = 0.7f
private const val TRACKS_FROM = 1204f
private const val TRACKS_TO = 1238f
private const val TRACKS_ALL = 2310
private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
/** Seconds after the last touch before the lamp eases back, and how long that takes. */
private const val IDLE = 2f
private const val RETURN = 0.9f

private val Ink = Color(0xFFF6E7DA)
private val InkSoft = Color(0x9EF6E7DA)
private val InkFaint = Color(0x66F6E7DA)
private val Amber = Color(0xFFFF9A4A)
private const val DIGIT_H = 22

/** Where the lamp stands, in dp: its axis, its top, its height, its scale and the shelf's top. */
internal class LavaLampLayout(val cx: Float, val top: Float, val h: Float, val k: Float, val shelf: Float)

private fun lavaLayout(width: Float, height: Float, insetTop: Float, insetBottom: Float): LavaLampLayout {
    val top = insetTop + 136f
    val shelf = height - insetBottom - 150f
    val h = max(240f, shelf - top)
    val k = min(h / 480f, (width / 2f - 20f) / 116f)
    return LavaLampLayout(width / 2f, top, h, k, top + h)
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOut(x: Float): Float {
    val c = clamp01(x)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
}
private fun easeOut(x: Float): Float {
    val c = clamp01(x)
    return 1f - (1f - c).pow(3)
}
private fun mix(a: Float, b: Float, e: Float) = a + (b - a) * e
private val PIf = PI.toFloat()

/** Half-width of the glass at lamp-local y (the same profile the shader draws). */
internal fun lavaGlassHalfWidth(y: Float, h: Float, k: Float): Float {
    val capEnd = h * LAVA_CAP_END
    val glassEnd = h * LAVA_GLASS_END
    val s = clamp01((y - capEnd) / (glassEnd - capEnd))
    return (33f + 45f * s.pow(1.15f)) * k
}

/** Screen x (from the axis) to the liquid's x at height y: the inverse of the glass's lens. */
private fun toLiquidX(x: Float, y: Float, h: Float, k: Float): Float {
    val hw = lavaGlassHalfWidth(y, h, k)
    val u = max(-1f, min(1f, x / hw))
    return hw * u * (0.74f + 0.26f * u * u)
}

private fun put(out: FloatArray, i: Int, x: Float, y: Float, r: Float, w: Float) {
    out[i * 4] = x
    out[i * 4 + 1] = y
    out[i * 4 + 2] = r
    out[i * 4 + 3] = w
}

/** The pose at loop time t, written into out: six blobs × (x, y, r, stretch). */
private fun loopPose(t: Float, l: LavaLampLayout, out: FloatArray) {
    // The wax is drawn 1.2 × the lamp's scale.
    val k = l.k * 1.2f
    val capEnd = l.h * LAVA_CAP_END
    val glassEnd = l.h * LAVA_GLASS_END
    val ph = 2f * PIf * t / LOOP
    val ax0 = -16f * k
    val ay0 = glassEnd - 34f * k
    val cx0 = 7f * k
    val cy0 = capEnd + 58f * k

    var ax = ax0
    var ay: Float
    var ar: Float
    var aw: Float
    val sw = seg(t, 1.6f, 2.4f)
    val dip = sin(PIf * seg(t, 1.6f, 1.85f)) * 3f * k
    ar = mix(25f * k, 27f * k, easeOut(sw))
    aw = mix(0.85f, 1.15f, easeInOut(sw))
    ay = ay0 + dip - 12f * k * easeInOut(seg(t, 1.8f, 2.4f))
    val rise = seg(t, 2.4f, 4.6f)
    if (rise > 0f) {
        val e = easeInOut(rise)
        ay = mix(ay0 - 12f * k, capEnd + 84f * k, e)
        ax = mix(ax0, -2f * k, e) + 7f * k * sin(PIf * e)
        aw = 1.12f + 0.12f * sin(PIf * e) - 0.07f * e
    }
    val merge = easeInOut(seg(t, 4.6f, 5.4f))
    if (t >= 4.6f) {
        ax = mix(-2f * k, 3f * k, merge)
        ay = mix(capEnd + 84f * k, capEnd + 74f * k, merge)
        aw = 1.05f
    }
    val split = easeInOut(seg(t, 5.4f, 7.6f))
    if (t >= 5.4f) {
        ax = mix(3f * k, cx0, split)
        ay = mix(capEnd + 74f * k, cy0, split)
        ar = mix(27f * k, 23f * k, split)
        aw = mix(1.05f, 1.0f, split)
    }

    var cx = cx0 + 1.5f * k * sin(ph)
    var cy = cy0 + 2f * k * sin(ph + 1f)
    var cr = 23f * k
    var cw = 1f
    if (t >= 4.6f) {
        cx = mix(cx0, 5f * k, merge)
        cy = mix(cy0, capEnd + 66f * k, merge)
    }
    if (t >= 5.4f) {
        val fall = seg(t, 5.4f, 7.6f)
        val e = easeInOut(fall)
        cx = mix(5f * k, ax0, e) - 6f * k * sin(PIf * e)
        cy = mix(capEnd + 66f * k, ay0, e)
        cr = mix(23f * k, 25f * k, e)
        // Tall while it sinks, a small squash when it lands, then its resting shape.
        val land = seg(t, 7.0f, 7.6f)
        cw = mix(1f, 1.25f, sin(PIf * min(1f, fall * 1.15f))) * (1f - land) + land * (0.85f - 0.08f * sin(PIf * land))
    }

    val nudge = sin(PIf * seg(t, 7.1f, 7.9f))
    val bx = 21f * k + 2.5f * k * nudge
    val by = glassEnd - 24f * k + 0.8f * k * sin(2f * ph)
    val dx = -9f * k + 3f * k * sin(ph + 0.6f)
    val dy = l.h * 0.42f + 6f * k * sin(ph)
    val ex = 15f * k + 2f * k * sin(2f * ph + 2f)
    val ey = capEnd + 128f * k + 5f * k * sin(2f * ph + 0.4f)

    put(out, 0, ax, ay, ar, aw)
    put(out, 1, bx, by, 21f * k, 0.85f)
    put(out, 2, cx, cy, cr, cw)
    put(out, 3, dx, dy, 11f * k, 1.05f)
    put(out, 4, ex, ey, 9f * k, 1.0f)
    put(out, 5, 0f, glassEnd, 0f, 1f)
}

/** The tracks counter at loop time t: whole tracks, each one clicking in. */
private fun tracksAt(t: Float): Float {
    val n = TRACKS_TO - TRACKS_FROM
    if (t < 7.6f) {
        val x = seg(t, 1.6f, 7.4f) * n
        val whole = floor(x)
        val f = clamp01((x - whole) / 0.7f)
        return TRACKS_FROM + whole + easeOut(f)
    }
    return mix(TRACKS_TO, TRACKS_FROM, easeInOut(seg(t, 7.6f, 8.3f)))
}

/** The hand-driven wax: each blob's position and velocity, its anchor, and the finger (liquid coordinates). */
private class LavaSim(pose: FloatArray, var fx: Float, var fy: Float) {
    val pos = pose.copyOf()
    val vel = FloatArray(12)
    val anchor = pose.copyOf()
    var fvx = 0f
    var fvy = 0f
    var down = true
    /** 1 once the warmed blob reaches the cap (one haptic), 2 after the haptic. */
    var topped = 0

    init {
        pos[20] = fx
        pos[21] = fy
        pos[22] = 0f
        pos[23] = 1f
    }

    /** Springs home, pushed away from the finger and along its drag; the warmed blob grows or rises. */
    fun step(dt: Float, l: LavaLampLayout) {
        val k = l.k
        val capEnd = l.h * LAVA_CAP_END
        val glassEnd = l.h * LAVA_GLASS_END
        val sigma = 60f * k
        for (i in 0 until 5) {
            val x = pos[i * 4]
            val y = pos[i * 4 + 1]
            var vx = vel[i * 2]
            var vy = vel[i * 2 + 1]
            var ax = 5f * (anchor[i * 4] - x) - 3.2f * vx
            var ay = 5f * (anchor[i * 4 + 1] - y) - 3.2f * vy
            if (down) {
                val dx = x - fx
                val dy = y - fy
                val d = sqrt(dx * dx + dy * dy) + 1f
                val fall = exp(-(d * d) / (2f * sigma * sigma))
                ax += dx / d * 1500f * k * fall + (fvx - vx) * 5f * fall
                ay += dy / d * 1500f * k * fall + (fvy - vy) * 5f * fall
            }
            vx += ax * dt
            vy += ay * dt
            var nx = x + vx * dt
            var ny = y + vy * dt
            val r = pos[i * 4 + 2]
            ny = max(capEnd + 22f * k + r * 0.4f, min(glassEnd - 8f * k, ny))
            val lim = max(0f, lavaGlassHalfWidth(ny, l.h, k) * 0.74f - r * 0.45f)
            nx = max(-lim, min(lim, nx))
            pos[i * 4] = nx
            pos[i * 4 + 1] = ny
            vel[i * 2] = vx
            vel[i * 2 + 1] = vy
            pos[i * 4 + 3] = anchor[i * 4 + 3] * (1f + min(0.25f, abs(vy) / (400f * k)))
        }
        val bigR = 27f * k
        var bx = pos[20]
        var by = pos[21]
        var vx = vel[10]
        var vy = vel[11]
        if (down) {
            val nx = bx + (fx - bx) * min(1f, dt * 10f)
            val ny = by + (fy - by) * min(1f, dt * 10f)
            vx = (nx - bx) / max(dt, 1e-3f)
            vy = (ny - by) / max(dt, 1e-3f)
            bx = nx
            by = ny
            pos[22] += (bigR - pos[22]) * min(1f, dt * 2.6f)
        } else {
            vy += -130f * k * dt
            vy *= exp(-1.6f * dt)
            vx *= exp(-2.5f * dt)
            vx += -bx * 0.8f * dt
            bx += vx * dt
            by += vy * dt
            val ceil = capEnd + 18f * k + pos[22] * 0.7f
            if (by < ceil) {
                by = ceil
                vy = 0f
                if (topped == 0) topped = 1
            }
        }
        val lim = max(0f, lavaGlassHalfWidth(by, l.h, k) * 0.74f - pos[22] * 0.4f)
        pos[20] = max(-lim, min(lim, bx))
        pos[21] = max(capEnd + 14f * k, min(glassEnd + 10f * k, by))
        vel[10] = vx
        vel[11] = vy
        pos[23] = 1f + min(0.4f, max(0f, -vy) / (260f * k))
    }
}

/** Everything the frame loop writes and the draw lambdas read; `frame` ticks once a frame. */
private class LavaState {
    var layout = LavaLampLayout(0f, 0f, 240f, 0.5f, 240f)
    var mode = MODE_AUTO
    var clock = 0f
    var now = 0f
    var lastTouch = 0f
    var returnAt = 0f
    val pose = FloatArray(POSE_LEN)
    val from = FloatArray(POSE_LEN)
    val rest = FloatArray(POSE_LEN)
    var sim: LavaSim? = null
    var tracks = TRACKS_FROM
    var tracksFrom = TRACKS_FROM
    var warm = 0f
    var spin = 0f
    var posed = false
    val frame = mutableIntStateOf(0)
    /** False while the lamp rests without autoPlay: the frame loop sleeps until a touch. */
    val awake = mutableStateOf(true)
}

/**
 * "Glowhaus" syncing a music library, its loader a lava lamp drawn by one AGSL shader: wax swells
 * on the warm pool, necks off, rises, merges under the cap and sinks back, while a tracks odometer
 * clicks up. A finger on the glass warms the wax under it and pushes the other blobs.
 *
 * @param autoPlay plays the 9 s rise-merge-sink loop.
 */
@Composable
fun LavaLampLoader(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember { LavaState() }
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(screen) / screen.density
    val insetBottom = insets.getBottom(screen) / screen.density
    // A shader the driver cannot compile throws: fall back to the drawn lamp instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(LAVA_LAMP_LOADER_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF1A0D10))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val l = lavaLayout(width, height, insetTop, insetBottom)
        state.layout = l
        // The resting pose before the first frame (and the only one under reduced motion).
        if (!state.posed || (state.mode == MODE_AUTO && state.clock == 0f && !auto)) {
            loopPose(0f, l, state.pose)
            state.posed = true
        }

        LaunchedEffect(state, auto, reduced, state.awake.value) {
            if (reduced || !state.awake.value) return@LaunchedEffect
            var last = -1L
            while (true) {
                val stop = withFrameNanos { nanos ->
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    lavaFrame(state, dt, auto) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    state.frame.intValue += 1
                    // Without autoPlay the loop sleeps once the lamp is back at rest.
                    !auto && state.mode == MODE_AUTO && state.warm < 0.002f
                }
                if (stop) {
                    state.awake.value = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A lava lamp. Touch the glass to warm the wax; drag to push the blobs." }
                .pointerInput(state, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        if (lavaBegin(state, down.position.x / d, down.position.y / d)) {
                            state.awake.value = true
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (change.positionChanged()) {
                                val v = tracker.calculateVelocity()
                                lavaUpdate(state, change.position.x / d, change.position.y / d, v.x / d, v.y / d)
                            }
                        }
                        lavaFinalize(state)
                    }
                },
        ) {
            state.frame.intValue
            val d = this.density
            val pose = state.pose
            val glow = 1f + 0.18f * state.warm
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", width, height)
                    shader.setFloatUniform("lamp", l.cx, l.top, l.h, l.k)
                    shader.setFloatUniform("b0", pose[0], pose[1], pose[2], pose[3])
                    shader.setFloatUniform("b1", pose[4], pose[5], pose[6], pose[7])
                    shader.setFloatUniform("b2", pose[8], pose[9], pose[10], pose[11])
                    shader.setFloatUniform("b3", pose[12], pose[13], pose[14], pose[15])
                    shader.setFloatUniform("b4", pose[16], pose[17], pose[18], pose[19])
                    shader.setFloatUniform("bf", pose[20], pose[21], pose[22], pose[23])
                    shader.setFloatUniform("glow", glow)
                    shader.setFloatUniform("phase", 2f * PIf * state.clock / LOOP)
                    drawRect(brush, Offset.Zero, Size(width, height))
                } else {
                    lavaLampFallback(width, height, l, pose, glow)
                }
            }
        }

        // Header: the kicker, the title, the odometer and the bar.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (insetTop + 18f).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("GLOWHAUS", style = TextStyle(color = Amber, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp))
            BasicText(
                "Syncing your library",
                style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 2.dp).height(DIGIT_H.dp), verticalAlignment = Alignment.CenterVertically) {
                LavaOdometerColumn(state, 1000f)
                BasicText(",", style = digitStyle(Ink, FontWeight.SemiBold))
                LavaOdometerColumn(state, 100f)
                LavaOdometerColumn(state, 10f)
                LavaOdometerColumn(state, 1f)
                BasicText(" / 2,310 tracks", style = digitStyle(InkSoft, FontWeight.Medium))
            }
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x1FF6E7DA)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            state.frame.intValue
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = min(1f, state.tracks / TRACKS_ALL)
                        }
                        .clip(RoundedCornerShape(2.dp))
                        .background(Amber),
                )
            }
        }

        BasicText(
            "Touch the glass to warm the wax",
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (l.shelf + 26f).dp)
                .padding(horizontal = 24.dp),
            style = TextStyle(color = InkFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, textAlign = TextAlign.Center),
        )

        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = (insetBottom + 16f).dp), contentAlignment = Alignment.BottomCenter) {
            LavaNowSyncingCard(state)
        }
    }
}

private fun digitStyle(color: Color, weight: FontWeight) = TextStyle(
    color = color,
    fontSize = 16.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    lineHeight = DIGIT_H.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** One column of the odometer: the ones turn with the value, higher places as the one below passes 9. */
@Composable
private fun LavaOdometerColumn(state: LavaState, place: Float) {
    Box(Modifier.size(10.dp, DIGIT_H.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    state.frame.intValue
                    val v = max(0f, state.tracks)
                    val m = v / place
                    val base = floor(m)
                    val pos = if (place == 1f) m else base + max(0f, (m - base) * 10f - 9f)
                    translationY = -(pos % 10f) * DIGIT_H.dp.toPx()
                },
        ) {
            for (i in 0..10) {
                Box(Modifier.size(10.dp, DIGIT_H.dp), contentAlignment = Alignment.Center) {
                    BasicText(
                        "${i % 10}",
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        style = digitStyle(Ink, FontWeight.SemiBold).copy(textAlign = TextAlign.Center),
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** The album being synced, with a spinning sync icon. */
@Composable
private fun LavaNowSyncingCard(state: LavaState) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .cssBoxShadow(Color(0x59000000), 1.dp, 2.dp, 22.dp)
            .cssBoxShadow(Color(0x6B000000), 16.dp, 36.dp, 22.dp)
            .clip(shape)
            .background(Color(0xEB261416))
            .drawBehind {
                // The inset top highlight.
                drawLine(Color(0x14FFE6D2), Offset(22.dp.toPx(), 1.dp.toPx()), Offset(size.width - 22.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
            }
            .border(1.dp, Color(0x14FFC8AA), shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        0f to Color(0xFFFFB36B),
                        0.55f to Color(0xFFE0482F),
                        1f to Color(0xFF6E1838),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.padding(top = 10.dp).size(22.dp).clip(CircleShape).background(Color(0x8CFFECC8)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                "Low Tide Hymns",
                style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "Marlow Fenn · 14 of 22 tracks",
                style = TextStyle(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x1FFF9A4A)), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Sync,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        state.frame.intValue
                        rotationZ = state.spin * 180f / PIf
                    },
            )
        }
    }
}

/** One frame of the loop, the hand-driven wax or the ease back to rest. */
private inline fun lavaFrame(s: LavaState, dt: Float, auto: Boolean, topped: () -> Unit) {
    val l = s.layout
    val at = s.now + dt
    s.now = at
    val out = s.pose
    when (s.mode) {
        MODE_AUTO -> {
            val t = if (auto) (s.clock + dt) % LOOP else 0f
            s.clock = t
            loopPose(t, l, out)
            s.tracks = if (auto) tracksAt(t) else TRACKS_FROM
            s.warm *= exp(-dt * 3f)
        }
        MODE_USER -> {
            val sim = s.sim
            if (sim != null) {
                sim.step(dt, l)
                if (sim.topped == 1) {
                    sim.topped = 2
                    topped()
                }
                sim.pos.copyInto(out)
                s.warm += ((if (sim.down) 1f else 0f) - s.warm) * min(1f, dt * 3f)
                if (!sim.down && at - s.lastTouch > IDLE) {
                    out.copyInto(s.from)
                    s.tracksFrom = s.tracks
                    s.returnAt = at
                    s.mode = MODE_RETURN
                }
            }
        }
        else -> {
            val e = easeInOut((at - s.returnAt) / RETURN)
            loopPose(0f, l, s.rest)
            for (i in 0 until POSE_LEN) out[i] = s.from[i] + (s.rest[i] - s.from[i]) * e
            s.tracks = s.tracksFrom + (TRACKS_FROM - s.tracksFrom) * e
            s.warm *= exp(-dt * 3f)
            if (e >= 1f) {
                s.mode = MODE_AUTO
                s.clock = 0f
            }
        }
    }
    s.spin = (s.spin + dt * 2.4f) % (2f * PIf)
}

/** A screen point (dp) in the liquid's coordinates, or null outside the glass. */
private fun liquidPoint(l: LavaLampLayout, x: Float, y: Float): Pair<Float, Float>? {
    val ly = y - l.top
    if (ly < l.h * LAVA_CAP_END - 20f || ly > l.h * LAVA_GLASS_END + 30f) return null
    return toLiquidX(x - l.cx, max(l.h * LAVA_CAP_END, min(l.h * LAVA_GLASS_END, ly)), l.h, l.k) to ly
}

/** A finger lands: warms the wax there. False outside the glass. */
private fun lavaBegin(s: LavaState, x: Float, y: Float): Boolean {
    val p = liquidPoint(s.layout, x, y) ?: return false
    s.lastTouch = s.now
    val sim = s.sim
    if (s.mode == MODE_USER && sim != null) {
        sim.down = true
        sim.fx = p.first
        sim.fy = p.second
        // The warmed blob picks up again under the new touch.
        sim.pos[20] = p.first
        sim.pos[21] = p.second
    } else {
        s.sim = LavaSim(s.pose, p.first, p.second)
        s.mode = MODE_USER
    }
    return true
}

private fun lavaUpdate(s: LavaState, x: Float, y: Float, vx: Float, vy: Float) {
    val sim = s.sim ?: return
    if (s.mode != MODE_USER || !sim.down) return
    val l = s.layout
    val p = liquidPoint(l, x, max(l.top + l.h * LAVA_CAP_END, min(l.top + l.h * LAVA_GLASS_END, y))) ?: return
    sim.fx = p.first
    sim.fy = p.second
    sim.fvx = vx
    sim.fvy = vy
    s.lastTouch = s.now
}

private fun lavaFinalize(s: LavaState) {
    val sim = s.sim ?: return
    if (s.mode != MODE_USER) return
    sim.down = false
    sim.fvx = 0f
    sim.fvy = 0f
    sim.topped = 0
    s.lastTouch = s.now
}
