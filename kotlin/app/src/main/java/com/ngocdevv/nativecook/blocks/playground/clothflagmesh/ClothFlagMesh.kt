package com.ngocdevv.nativecook.blocks.playground.clothflagmesh

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// One fixed clock drives the cloth and the autoPlay script: ticks of 1/60 s, at most MAX_TICKS a
// frame. Every frame draws the cloth between its last two ticks, so it moves evenly at any refresh
// rate; if a frame runs late the cloth slows for a moment rather than jumping.
private const val TICK = 1.0 / 60
private const val MAX_TICKS = 2
// Steps of still air run once at the start to hang the banner at rest; in the last STILL_STEPS of
// them every point is stopped after each step, so it comes to a dead stop.
private const val SETTLE_STEPS = 420
private const val STILL_STEPS = 180

// Layout (dp): the pole's centre from the left, the banner's top below the safe area.
private const val POLE_X = 40f
private const val POLE_W = 6f
// The pole fades over POLE_FADE dp and is gone CAPTION_CLEAR dp above the bottom inset.
private const val POLE_FADE = 110f
private const val CAPTION_CLEAR = 196f
private const val FLAG_TOP = 100f
// The cloth's hoist sits just right of the pole's centre, on its rings.
private const val HOIST_GAP = 4f
// Perspective: depth (dp towards the viewer) seen from FOCAL dp away.
private const val FOCAL = 1100.0
// The horizon, as a share of the screen's height.
private const val HORIZON = 0.6f

// Light from the upper left and in front; the sheen is a soft satin highlight.
private const val LIGHT_X = -0.4
private const val LIGHT_Y = -0.5
private const val LIGHT_Z = 0.77
private const val SHEEN_POWER = 40.0
private const val SHEEN = 0.5

// The wind settings and their strength; the wind eases between them at WIND_RATE /s.
private val Modes = listOf("Calm", "Breeze", "Gale")
private val ModeLevel = doubleArrayOf(0.0, 0.55, 1.0)
private const val WIND_RATE = 1.6
// A gust fades with this time constant (s). The script gusts by SCRIPT_GUST; a swipe this fast
// (dp/s) that misses the cloth, while the wind is up, gusts by GUST_PER_SPEED per dp/s; together
// never past GUST_MAX.
private const val GUST_FADE = 1.1
private const val SCRIPT_GUST = 0.85
private const val GUST_SWIPE = 450f
private const val GUST_PER_SPEED = 1 / 2200.0
private const val GUST_MAX = 1.5
// A drag picks the nearest point of the cloth within this reach (dp). The cloth itself is the
// feedback: the held point follows the finger and the folds pull out from it, with no marker.
private const val GRAB_REACH = 52.0
// Gesture thresholds standing in for the React Native recognisers: a pan starts after 4 dp, a tap
// is a touch lifted within 300 ms.
private const val PAN_SLOP = 4f
private const val TAP_MS = 300L

// autoPlay (seconds): still at first; a breeze rises, then a gale, a gust, a hand pulls the
// lower fly corner down and lets go, the wind drops and the banner falls still again.
private const val LOOP = 14.0
private const val BREEZE_AT = 0.4
private const val GALE_AT = 2.4
private const val GUST_AT = 4.0
private const val GRAB_AT = 5.6
private const val PULL_TIME = 1.1
private const val RELEASE_AT = 7.4
private const val CALM_AT = 8.6
// For the last LAND_TIME of the loop the hanging banner is eased onto the exact resting state it
// started from, so the loop joins without a jump.
private const val LAND_TIME = 1.8
// Where the scripted hand pulls the corner, as a share of the banner from its hoist and top.
private const val PULL_TO_X = 0.58
private const val PULL_TO_Y = 1.55
// After a touch the loop waits this long before it starts again.
private const val USER_HOLD = 4.0
// The banner fades in over this long once its weave is rendered (ms).
private const val WEAVE_FADE_MS = 300

private val Night = Color(0xFF0F1626)
private val Ink = Color(0xFFF3ECE0)
private val Muted = Color(0xFFB5A79A)

private fun clamp01(v: Double) = min(1.0, max(0.0, v))

/** Whether a clock that went from `before` to `t` passed the moment `at`. */
private fun crossed(before: Double, t: Double, at: Double) = before < at && t >= at

private fun easeInOut(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

// The weave, as the React Native shader draws it, evaluated once per texel into a bitmap.
private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}
private fun fill(d: Float) = 1f - smoothstep(-0.6f, 0.6f, d)
private fun fract(x: Float) = x - floor(x)
private fun glslMod(x: Float, y: Float) = x - y * floor(x / y)
private fun hash(n: Float) = fract(sin(n * 91.345f) * 47453.5453f)
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The banner's cloth at `p` (dp in the flat banner): a plain weave of fine threads under an
 * invented device — a gold sun of sixteen rays in a ring over three waves, on an oxblood field with
 * a gold border and an ivory hoist. Every thread's crossing is shaded so the weave catches the light.
 */
private fun weave(px: Float, py: Float, out: FloatArray) {
    val w = FLAG_W.toFloat()
    val h = FLAG_H.toFloat()
    val hoist = 50f
    val deep = smoothstep(0f, h, py) * 0.6f
    var r = mix(0.47f, 0.36f, deep)
    var g = mix(0.09f, 0.06f, deep)
    var b = mix(0.13f, 0.10f, deep)
    fun toward(cr: Float, cg: Float, cb: Float, t: Float) {
        r = mix(r, cr, t)
        g = mix(g, cg, t)
        b = mix(b, cb, t)
    }
    // Gold border inside the field.
    val qx = px - hoist
    val edge = min(min(py, h - py), (w - hoist) - qx)
    toward(0.93f, 0.74f, 0.38f, fill(kotlin.math.abs(edge - 9f) - 1.6f))

    // The device: a sun in a ring over three waves.
    val cx = hoist + (w - hoist) * 0.5f
    val cy = h * 0.42f
    val dx = px - cx
    val dy = py - cy
    val rad = sqrt(dx * dx + dy * dy)
    val a = atan2(dy, dx)
    val disc = fill(rad - 19f)
    val ring = fill(kotlin.math.abs(rad - 27f) - 1.4f)
    val sector = 6.2831853f / 16f
    val k = floor(a / sector + 0.5f)
    val local = a - k * sector
    val longRay = if (glslMod(k, 2f) == 0f) 1f else 0f
    val tip = 34f + mix(9f, 17f, longRay)
    val t = ((rad - 33f) / (tip - 33f)).coerceIn(0f, 1f)
    val halfWidth = mix(3.2f, 0.3f, t)
    val rays = fill(kotlin.math.abs(local) * rad - halfWidth) * fill(33f - rad) * fill(rad - tip)
    var waves = 0f
    for (i in 0 until 3) {
        val y0 = cy + 58f + i * 11f
        val wy = y0 + 3.2f * sin((px - cx) * 0.16f + i * 1.1f)
        val span = fill(kotlin.math.abs(px - cx) - (60f - i * 12f))
        waves = max(waves, fill(kotlin.math.abs(py - wy) - 1.7f) * span)
    }
    toward(0.93f, 0.74f, 0.38f, max(max(disc, ring), max(rays, waves)))

    // The hoist: ivory with two oxblood stripes, and a gold seam where it meets the field.
    val sleeve = fill(px - hoist)
    val stripes = max(fill(kotlin.math.abs(px - 14f) - 2f), fill(kotlin.math.abs(px - 36f) - 2f))
    toward(mix(0.94f, 0.47f, stripes), mix(0.90f, 0.09f, stripes), mix(0.81f, 0.13f, stripes), sleeve)
    toward(0.93f, 0.74f, 0.38f, fill(kotlin.math.abs(px - hoist) - 1.2f))

    // Plain weave: threads 2 dp apart, warp and weft over and under in turn.
    val gx = px / 2f
    val gy = py / 2f
    val cellX = floor(gx)
    val cellY = floor(gy)
    val fx = gx - cellX - 0.5f
    val fy = gy - cellY - 0.5f
    val warpUp = glslMod(cellX + cellY, 2f) < 1f
    val across = if (warpUp) fx else fy
    val along = if (warpUp) fy else fx
    val rounded = 1f - 4f * across * across
    val bend = 1f - 1.6f * along * along
    val slub = if (warpUp) hash(cellX) else hash(cellY + 31f)
    var shade = 0.72f + 0.3f * rounded * bend + 0.06f * (slub - 0.5f)
    // The warp is a touch darker than the weft, like shot silk.
    shade *= if (warpUp) 0.95f else 1.03f
    out[0] = r * shade
    out[1] = g * shade
    out[2] = b * shade
}

/** The woven banner as a bitmap, `scale` pixels per dp. */
private fun renderWeave(scale: Float): Bitmap {
    val w = ceil(FLAG_W * scale).toInt()
    val h = ceil(FLAG_H * scale).toInt()
    val pixels = IntArray(w * h)
    val rgb = FloatArray(3)
    for (j in 0 until h) {
        for (i in 0 until w) {
            weave((i + 0.5f) / w * FLAG_W.toFloat(), (j + 0.5f) / h * FLAG_H.toFloat(), rgb)
            val r = (rgb[0].coerceIn(0f, 1f) * 255 + 0.5f).toInt()
            val g = (rgb[1].coerceIn(0f, 1f) * 255 + 0.5f).toInt()
            val b = (rgb[2].coerceIn(0f, 1f) * 255 + 0.5f).toInt()
            pixels[j * w + i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

/** A float colour channel (0…1) as a byte. */
private fun channel(v: Double) = (v.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()

/** The cloth, the air and the script, shared by the frame loop, the gestures and the drawing. */
private class FlagScene(val top: Float, autoPlay: Boolean, reducedMotion: Boolean) {
    val originX = (POLE_X + HOIST_GAP).toDouble()
    // Perspective centre: the middle of the banner at rest.
    val vpx = originX + FLAG_W / 2
    val vpy = top + FLAG_H / 2
    val rest = ClothFlagMeshCloth(originX, top.toDouble())
    val cloth = ClothFlagMeshCloth(originX, top.toDouble())
    // The cloth as drawn this frame (between its last two steps) and its normals.
    private val rx = DoubleArray(MESH_COUNT)
    private val ry = DoubleArray(MESH_COUNT)
    private val rz = DoubleArray(MESH_COUNT)
    private val nx = DoubleArray(MESH_COUNT)
    private val ny = DoubleArray(MESH_COUNT)
    private val nz = DoubleArray(MESH_COUNT)
    // The cloth's points on screen (dp): for drawing and for picking the one under a finger.
    val verts = FloatArray(MESH_COUNT * 2)
    val colors = IntArray(MESH_COUNT)
    val sheen = IntArray(MESH_COUNT)
    var acc = 0.0
    // The air: 0…1 strength easing towards the chosen mode's, and a fading gust.
    var level = 0.0
    var gust = 0.0
    var mode = if (autoPlay) 0 else 1
    var knots = 0.0
    // The point being dragged (-1 for none), where the finger holds it, and whether the script holds it.
    var grab = -1
    var fingerX = 0.0
    var fingerY = 0.0
    var scripted = false
    // The wind a tap brings back after stilling it: the last setting that was not calm.
    var lastWind = 1
    // The grabbed point's offset from the finger, so it does not jump to it.
    var grabDX = 0.0
    var grabDY = 0.0
    var pullFromX = 0.0
    var pullFromY = 0.0
    var clock = 0.0
    var hold = 0.0
    var looping = false
    // The loop has run untouched since the cloth last hung at rest: at its end it may snap back exactly.
    var pristine = true
    var reducedMotion = reducedMotion
    var onMode: (Int) -> Unit = {}

    init {
        for (i in 0 until SETTLE_STEPS) {
            rest.step(0.0, 0.0, TICK, -1, 0.0, 0.0)
            if (i >= SETTLE_STEPS - STILL_STEPS) rest.still()
        }
        cloth.copyFrom(rest)
        // A user starts in a breeze: let it fill out before the first frame.
        if (!autoPlay) {
            val calm = if (reducedMotion) 0.35 else 1.0
            repeat(150) { cloth.step(ModeLevel[1] * calm, 0.0, TICK, -1, 0.0, 0.0) }
            level = ModeLevel[1]
        }
        project(1.0)
    }

    fun setWind(m: Int) {
        if (m !in ModeLevel.indices || m == mode) return
        mode = m
        if (m > 0) lastWind = m
        onMode(m)
    }

    fun blow(amount: Double) {
        gust = min(GUST_MAX, gust + amount)
    }

    /** The autoPlay script at time `t` after a tick from `before`. */
    private fun script(before: Double, t: Double) {
        val corner = MESH_COUNT - 1
        if (crossed(before, t, BREEZE_AT)) setWind(1)
        if (crossed(before, t, GALE_AT)) setWind(2)
        if (crossed(before, t, GUST_AT)) blow(SCRIPT_GUST)
        if (crossed(before, t, GRAB_AT)) {
            grab = corner
            scripted = true
            pullFromX = cloth.x[corner]
            pullFromY = cloth.y[corner]
        }
        if (scripted && t >= GRAB_AT && t < RELEASE_AT) {
            val k = easeInOut(clamp01((t - GRAB_AT) / PULL_TIME))
            fingerX = pullFromX + (originX + FLAG_W * PULL_TO_X - pullFromX) * k
            fingerY = pullFromY + (top + FLAG_H * PULL_TO_Y - pullFromY) * k
        }
        if (crossed(before, t, RELEASE_AT) && scripted) {
            grab = -1
            scripted = false
        }
        if (crossed(before, t, CALM_AT)) setWind(0)
    }

    /** One tick: the script, the air, then the cloth. */
    fun tick() {
        if (grab < 0 || scripted) {
            if (hold > 0) {
                hold -= TICK
                // Pick the loop up where the air already is, rather than dropping the cloth first.
                if (hold <= 0) clock = if (mode == 2) GALE_AT + 0.5 else if (mode == 1) BREEZE_AT + 0.5 else 0.0
            } else if (looping) {
                var before = clock
                var t = before + TICK
                if (t >= LOOP) {
                    before = 0.0
                    t -= LOOP
                    // Untouched, the banner has hung still again: start the next loop from exactly the same state.
                    if (pristine) cloth.copyFrom(rest)
                    pristine = true
                    level = 0.0
                    gust = 0.0
                    setWind(0)
                }
                clock = t
                script(before, t)
                if (pristine && mode == 0 && t > LOOP - LAND_TIME) {
                    val k = (t - (LOOP - LAND_TIME)) / LAND_TIME
                    cloth.blendTo(rest, k * k * k * 0.2)
                }
            }
        }
        val goal = ModeLevel[mode]
        level += (goal - level) * (1 - exp(-TICK * WIND_RATE))
        if (kotlin.math.abs(goal - level) < 1e-4) level = goal
        gust *= exp(-TICK / GUST_FADE)
        if (gust < 1e-4) gust = 0.0
        val calm = if (reducedMotion) 0.35 else 1.0
        cloth.step(level * calm, gust * calm, TICK, grab, fingerX, fingerY)
        knots += (windKnots(level, gust, cloth.time) - knots) * 0.08
    }

    /** The cloth as it stands `alpha` of the way from its last step to this one, projected and shaded. */
    fun project(alpha: Double) {
        for (i in 0 until MESH_COUNT) {
            rx[i] = cloth.ox[i] + (cloth.x[i] - cloth.ox[i]) * alpha
            ry[i] = cloth.oy[i] + (cloth.y[i] - cloth.oy[i]) * alpha
            rz[i] = cloth.oz[i] + (cloth.z[i] - cloth.oz[i]) * alpha
        }
        computeNormals(rx, ry, rz, nx, ny, nz)
        val hx = LIGHT_X
        val hy = LIGHT_Y
        val hz = LIGHT_Z + 1
        val hl = sqrt(hx * hx + hy * hy + hz * hz)
        for (i in 0 until MESH_COUNT) {
            val scale = FOCAL / (FOCAL - min(rz[i], FOCAL * 0.6))
            verts[i * 2] = (vpx + (rx[i] - vpx) * scale).toFloat()
            verts[i * 2 + 1] = (vpy + (ry[i] - vpy) * scale).toFloat()
            val diffuse = max(0.0, nx[i] * LIGHT_X + ny[i] * LIGHT_Y + nz[i] * LIGHT_Z)
            val shade = min(1.0, 0.3 + 0.86 * diffuse)
            colors[i] = (0xff shl 24) or (channel(shade) shl 16) or (channel(shade * 0.98) shl 8) or channel(shade * 0.95)
            val h = max(0.0, (nx[i] * hx + ny[i] * hy + nz[i] * hz) / hl)
            val spec = h.pow(SHEEN_POWER) * SHEEN
            sheen[i] = (0xff shl 24) or (channel(spec) shl 16) or (channel(spec * 0.94) shl 8) or channel(spec * 0.82)
        }
    }

    fun touched() {
        hold = USER_HOLD
        pristine = false
        if (scripted) {
            scripted = false
            grab = -1
        }
    }

    /** A screen point (dp) as a point at the cloth's depth `z`, undoing the perspective. */
    fun toWorldX(x: Double, z: Double) = vpx + (x - vpx) * (FOCAL - min(z, FOCAL * 0.6)) / FOCAL
    fun toWorldY(y: Double, z: Double) = vpy + (y - vpy) * (FOCAL - min(z, FOCAL * 0.6)) / FOCAL

    /** A drag starts: hold the nearest point within reach where it is and let the finger carry it. */
    fun panStart(x: Double, y: Double): Boolean {
        touched()
        var best = -1
        var bestD = GRAB_REACH
        for (i in 0 until MESH_COUNT) {
            if (i % MESH_COLS == 0) continue
            val d = hypot(verts[i * 2] - x, verts[i * 2 + 1] - y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        if (best < 0) return false
        grabDX = cloth.x[best] - toWorldX(x, cloth.z[best])
        grabDY = cloth.y[best] - toWorldY(y, cloth.z[best])
        fingerX = cloth.x[best]
        fingerY = cloth.y[best]
        grab = best
        return true
    }

    fun panMove(x: Double, y: Double) {
        val i = grab
        if (i < 0 || scripted) return
        fingerX = toWorldX(x, cloth.z[i]) + grabDX
        fingerY = toWorldY(y, cloth.z[i]) + grabDY
    }

    /**
     * A drag ends at `speed` dp/s: lets go of the cloth, or, if it missed it, gusts the wind that is
     * already blowing (in still air there is nothing to gust).
     */
    fun panEnd(speed: Double): Boolean {
        if (grab >= 0 && !scripted) {
            grab = -1
            hold = USER_HOLD
            return false
        }
        if (!(speed >= GUST_SWIPE) || (mode == 0 && level < 0.05)) return false
        blow(speed * GUST_PER_SPEED)
        hold = USER_HOLD
        return true
    }

    /** A tap stills the wind, or brings back the last wind it stilled; true if it raised it. */
    fun tap(): Boolean {
        touched()
        val on = mode == 0
        setWind(if (on) lastWind else 0)
        return on
    }

    /** The switch: sets the wind. */
    fun pick(m: Int) {
        if (m !in ModeLevel.indices) return
        hold = USER_HOLD
        pristine = false
        mode = m
        if (m > 0) lastWind = m
    }
}

/** Paints and the woven bitmap, made once. */
private class FlagPaints(top: Float, height: Float, width: Float) {
    val horizon = height * HORIZON
    val sky = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(0xFF0F1626.toInt(), 0xFF1D2A46.toInt(), 0xFF4A4A64.toInt(), 0xFF9A7062.toInt()),
            floatArrayOf(0f, 0.45f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val sea = Paint().apply {
        shader = LinearGradient(
            0f, horizon, 0f, height,
            intArrayOf(0xFF5D5262.toInt(), 0xFF262C40.toInt(), 0xFF0C101B.toInt()),
            floatArrayOf(0f, 0.22f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    // The headland: a long low hill on the right, with a lower spit before it.
    val land = Path().apply {
        moveTo(width * 0.42f, horizon)
        cubicTo(width * 0.55f, horizon - 4, width * 0.6f, horizon - 15, width * 0.74f, horizon - 17)
        cubicTo(width * 0.86f, horizon - 19, width * 0.93f, horizon - 11, width + 2, horizon - 12)
        lineTo(width + 2, horizon)
        close()
    }
    val hill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2F2C3C.toInt() }
    val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    val shaft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            POLE_X - POLE_W / 2, 0f, POLE_X + POLE_W / 2, 0f,
            intArrayOf(0xFF22262E.toInt(), 0xFFDFE3EA.toInt(), 0xFF7D8490.toInt(), 0xFF1C2027.toInt()),
            floatArrayOf(0f, 0.32f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val ball = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            POLE_X - 2.6f, top - 28, 10f,
            intArrayOf(0xFFFFF1C4.toInt(), 0xFFD7A64C.toInt(), 0xFF6B4815.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val halyard = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = 0xFFE8E0D4.toInt()
        alpha = (0.35f * 255).toInt()
    }
    // The pole's foot, fading into the water: the shaft and halyard under a vertical fade.
    val shaftFade = Paint(Paint.ANTI_ALIAS_FLAG)
    val halyardFade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }
    private var fadeKey = Float.NaN

    fun fade(from: Float, end: Float) {
        if (fadeKey == from + end * 7919f) return
        fadeKey = from + end * 7919f
        val across = LinearGradient(
            POLE_X - POLE_W / 2, 0f, POLE_X + POLE_W / 2, 0f,
            intArrayOf(0xFF22262E.toInt(), 0xFFDFE3EA.toInt(), 0xFF7D8490.toInt(), 0xFF1C2027.toInt()),
            floatArrayOf(0f, 0.32f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        val down = LinearGradient(0f, from, 0f, end, 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        shaftFade.shader = ComposeShader(across, down, PorterDuff.Mode.DST_IN)
        halyardFade.shader = LinearGradient(0f, from, 0f, end, 0x59E8E0D4, 0x00E8E0D4, Shader.TileMode.CLAMP)
    }

    // The soft shade the banner casts on the sky (a 12 dp drop shadow, down and right).
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(5, 7, 12).copy(alpha = 0.3f).toArgb()
        maskFilter = BlurMaskFilter(sigmaToRadius(12f), BlurMaskFilter.Blur.NORMAL)
    }
    val outline = Path()
    val cloth = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    // The sheen is screened over the cloth: a white bitmap tinted by each corner's sheen.
    val white: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
    val satin = Paint().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.SCREEN else xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = 0xFFD9B36A.toInt()
    }
    private fun face(weight: Int, fallback: Int) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, fallback)
    }
    val knots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ink.toArgb()
        textSize = 40f
        typeface = face(600, Typeface.BOLD)
    }
    val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ink.toArgb()
        textSize = 15f
        typeface = face(600, Typeface.BOLD)
    }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Muted.toArgb()
        textSize = 11f
        typeface = face(700, Typeface.BOLD)
    }
    val rect = RectF()
}

/** The sky at dusk over a still sea, with a low headland on the horizon. */
private fun drawScenery(c: NativeCanvas, p: FlagPaints, width: Float, height: Float) {
    c.drawRect(0f, 0f, width, p.horizon, p.sky)
    c.drawPath(p.land, p.hill)
    c.drawRect(0f, p.horizon, width, height, p.sea)
    // A few long, faint glints of the last light on the water.
    val lines = arrayOf(
        floatArrayOf(0.18f, 0.012f, 0.3f, 0.22f),
        floatArrayOf(0.52f, 0.02f, 0.22f, 0.16f),
        floatArrayOf(0.08f, 0.04f, 0.2f, 0.1f),
        floatArrayOf(0.66f, 0.055f, 0.18f, 0.08f),
    )
    for (l in lines) {
        p.glint.color = Color(236, 196, 170).copy(alpha = l[3]).toArgb()
        val y = p.horizon + height * l[1]
        c.drawLine(width * l[0], y, width * (l[0] + l[2]), y, p.glint)
    }
}

/**
 * The pole: a brushed metal shaft with a gold ball on top and a halyard down its side. Its foot
 * fades into the dark water above the caption, so the shaft never runs through the text.
 */
private fun drawPole(c: NativeCanvas, p: FlagPaints, top: Float, end: Float) {
    val from = maxOf(top + 40, end - POLE_FADE)
    p.fade(from, end)
    c.drawRect(POLE_X - POLE_W / 2, top - 14, POLE_X + POLE_W / 2, from, p.shaft)
    c.drawRect(POLE_X - POLE_W / 2, from, POLE_X + POLE_W / 2, end, p.shaftFade)
    // The truck under the ball.
    p.rect.set(POLE_X - 6, top - 17, POLE_X + 6, top - 12)
    c.drawRoundRect(p.rect, 2f, 2f, p.shaft)
    c.drawCircle(POLE_X, top - 25, 7.5f, p.ball)
    val hx = POLE_X + POLE_W / 2 + 1.5f
    c.drawLine(hx, top - 17, hx, from, p.halyard)
    c.drawLine(hx, from, hx, end, p.halyardFade)
}

/** The banner's outline on screen: along the top, down the fly, back along the foot, up the hoist. */
private fun traceOutline(path: Path, verts: FloatArray) {
    path.rewind()
    path.moveTo(verts[0], verts[1])
    for (c in 1 until MESH_COLS) path.lineTo(verts[c * 2], verts[c * 2 + 1])
    for (r in 1 until MESH_ROWS) {
        val i = r * MESH_COLS + MESH_COLS - 1
        path.lineTo(verts[i * 2], verts[i * 2 + 1])
    }
    for (c in MESH_COLS - 2 downTo 0) {
        val i = (MESH_ROWS - 1) * MESH_COLS + c
        path.lineTo(verts[i * 2], verts[i * 2 + 1])
    }
    for (r in MESH_ROWS - 2 downTo 1) {
        val i = r * MESH_COLS
        path.lineTo(verts[i * 2], verts[i * 2 + 1])
    }
    path.close()
}

/**
 * A fabric banner flying from a pole over the sea at dusk, simulated as a verlet cloth with wind.
 * Folds catch the light from their normals and the weave shows in the cloth. A switch sets the wind
 * (calm, breeze, gale); dragging grabs the cloth at the nearest point and pulls it, letting go lets
 * it billow; a fast swipe past the cloth gusts the wind, and a tap stills it or brings it back.
 *
 * @param autoPlay Raises the wind, gusts, pulls a corner down and lets it fly, then stills, on a loop.
 * @param label The lines under the banner, with [title] and [subtitle].
 */
@Composable
fun ClothFlagMesh(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    label: String = "House banner · No. 7",
    title: String = "Saltmarsh Regatta",
    subtitle: String = "Flown at the harbour mouth since 1874",
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val top = safeTop + FLAG_TOP
    val scene = remember(top) { FlagScene(top, auto, reducedMotion) }
    var frame by remember { mutableIntStateOf(0) }
    var mode by remember(scene) { mutableIntStateOf(scene.mode) }
    // The woven cloth, rendered once off the main thread.
    var weaveBitmap by remember { mutableStateOf<Bitmap?>(null) }

    scene.reducedMotion = reducedMotion
    scene.onMode = { mode = it }

    // The banner fades in once its weave is ready (on a slow device that takes a moment), never pops in.
    val weaveIn = remember { Animatable(0f) }
    LaunchedEffect(density) {
        weaveBitmap = withContext(Dispatchers.Default) { renderWeave(density.density) }
        weaveIn.animateTo(1f, tween(WEAVE_FADE_MS, easing = LinearOutSlowInEasing))
    }

    LaunchedEffect(scene, auto) {
        scene.looping = auto
        scene.clock = 0.0
        scene.hold = 0.0
    }

    LaunchedEffect(scene) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016 else min((now - last) / 1e9, MAX_TICKS * TICK)
                last = now
                var pending = scene.acc + max(0.0, dt)
                var ticks = 0
                while (pending >= TICK && ticks < MAX_TICKS) {
                    scene.tick()
                    pending -= TICK
                    ticks += 1
                }
                pending = min(pending, TICK)
                scene.acc = pending
                scene.project(clamp01(pending / TICK))
                frame += 1
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Night),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val paints = remember(top, width, height) { FlagPaints(top, height, width) }
        // The readout sits on the right, level with the caption's title.
        val readoutBase = height - safeBottom - 140

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "$title banner. Drag the cloth to pull it, swipe past it to gust the wind, tap to still or raise the wind."
                    onClick {
                        windToggled(view, scene.tap())
                        true
                    }
                }
                .pointerInput(scene) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        var panning = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            change.consume()
                            val x = change.position.x / density.density
                            val y = change.position.y / density.density
                            if (!change.pressed) {
                                if (panning) {
                                    val v = tracker.calculateVelocity()
                                    val speed = hypot(v.x, v.y) / density.density
                                    if (scene.panEnd(speed.toDouble())) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    windToggled(view, scene.tap())
                                }
                                break
                            }
                            if (!panning && hypot(x - start.x / density.density, y - start.y / density.density) >= PAN_SLOP) {
                                panning = true
                                if (scene.panStart(x.toDouble(), y.toDouble())) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            if (panning) scene.panMove(x.toDouble(), y.toDouble())
                        }
                        // A finger lost to the system still lets go of the cloth.
                        if (scene.grab >= 0 && !scene.scripted) scene.grab = -1
                    }
                },
        ) {
            // Read so the canvas redraws every frame.
            if (frame < 0) return@Canvas
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density.density, density.density)
                drawScenery(c, paints, width, height)
                drawPole(c, paints, top, height - safeBottom - CAPTION_CLEAR)

                val bitmap = weaveBitmap
                val shown = weaveIn.value
                if (bitmap != null && shown > 0f) {
                    if (shown < 1f) c.saveLayerAlpha(0f, 0f, width, height, (shown * 255).roundToInt())
                    // The banner, with a soft shade under it so it sits off the sky.
                    traceOutline(paints.outline, scene.verts)
                    c.save()
                    c.translate(10f, 18f)
                    c.drawPath(paints.outline, paints.shadow)
                    c.restore()
                    // Each corner's shade multiplies the weave; the satin sheen is screened on top.
                    c.drawBitmapMesh(bitmap, MESH_COLS - 1, MESH_ROWS - 1, scene.verts, 0, scene.colors, 0, paints.cloth)
                    c.drawBitmapMesh(paints.white, MESH_COLS - 1, MESH_ROWS - 1, scene.verts, 0, scene.sheen, 0, paints.satin)
                    if (shown < 1f) c.restore()
                }

                // Rings holding the hoist to the pole, over the cloth.
                for (r in intArrayOf(0, 4, 9, MESH_ROWS - 1)) {
                    val y = scene.verts[r * MESH_COLS * 2 + 1]
                    paints.rect.set(POLE_X - 6, y - 2.5f, POLE_X + 6, y + 2.5f)
                    c.drawOval(paints.rect, paints.ring)
                }

                // The wind readout, on the right of the caption: "WIND", or "CALM" once a tap has stilled it.
                val right = width - 24
                val value = max(0.0, scene.knots).roundToInt().toString()
                val unitW = paints.unit.measureText("kn")
                c.drawText("kn", right - unitW, readoutBase, paints.unit)
                c.drawText(value, right - unitW - 5 - paints.knots.measureText(value), readoutBase, paints.knots)
                val word = if (scene.mode == 0) "CALM" else "WIND"
                c.drawText(word, right - paints.small.measureText(word), readoutBase - 44, paints.small)
                c.restore()
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 130.dp, bottom = (safeBottom + 108).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                label.uppercase(),
                style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp, fontFamily = FontFamily.SansSerif),
            )
            BasicText(
                title,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 30.sp),
                style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
            )
            BasicText(
                subtitle,
                maxLines = 2,
                style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = (safeBottom + 14).dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                "Drag the cloth · Swipe for a gust · Tap to still the wind",
                Modifier.fillMaxWidth(),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
                style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, fontFamily = FontFamily.SansSerif),
            )
            FlagSwitch(selected = mode, reducedMotion = reducedMotion) { m ->
                if (m != scene.mode) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                scene.pick(m)
                mode = m
            }
        }
    }
}

/** The haptic as a tap raises the wind (a firm tick) or stills it (a soft one). */
private fun windToggled(view: View, raised: Boolean) {
    view.performHapticFeedback(
        if (raised) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        },
    )
}

/** The wind switch: Calm, Breeze, Gale, with a pill that slides to the chosen one on a spring. */
@Composable
private fun FlagSwitch(selected: Int, reducedMotion: Boolean, pick: (Int) -> Unit) {
    val place by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.82f, stiffness = 273f),
        label = "switch",
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(Dp.Hairline, Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(4.dp),
    ) {
        val segment = maxWidth / Modes.size
        Box(
            Modifier
                .offset { IntOffset((segment.toPx() * place).roundToInt(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Color(243, 236, 224).copy(alpha = 0.94f)),
        )
        Row(Modifier.fillMaxSize()) {
            Modes.forEachIndexed { i, name ->
                val isOn = i == selected
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = if (pressed && !isOn) 0.7f else 1f }
                        .semantics {
                            contentDescription = "$name wind"
                            this.selected = isOn
                        }
                        .clickable(interactionSource = source, indication = null, role = Role.RadioButton) { pick(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        name,
                        style = TextStyle(
                            color = if (isOn) Color(0xFF1A2030) else Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                        ),
                    )
                }
            }
        }
    }
}
