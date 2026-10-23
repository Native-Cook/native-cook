package com.ngocdevv.nativecook.blocks.media.orbitphotoring

import android.graphics.Canvas
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlinx.coroutines.launch

/**
 * One camera body: its name, a short spec line, its proportions and colours, and the prints it
 * deals into the ring. Every number is in dp of a 402 dp wide screen.
 *
 * @param body Body width, height and depth.
 * @param hump The prism hump (or hood) on top: width, height, depth. Zero height for none.
 * @param lens Lens radius and how far it stands out of the body.
 * @param lensAt Where the lens sits on the front, from the middle of the body.
 * @param windows 1 for a rangefinder with two finder windows on the front plate.
 * @param plate Share of the body's height covered by the metal top plate.
 * @param photos Unsplash photo ids for its prints.
 * @param toyShell The Tumble style's toy camera: its glossy shell, its metal colour when null.
 * @param toyBody The toy's satin body, its leather colour when null.
 */
class OrbitCamera(
    val name: String,
    val spec: String,
    val body: FloatArray,
    val hump: FloatArray,
    val lens: FloatArray,
    val lensAt: FloatArray,
    val windows: Float,
    val plate: Float,
    val leather: Long,
    val metal: Long,
    val barrel: Long,
    val accent: Long,
    val glass: Long,
    val photos: List<String>,
    val toyShell: Long? = null,
    val toyBody: Long? = null,
)

// Length of the self-playing loop, in seconds. It starts and ends on the first camera, with the
// ring turned by exactly one whole turn, so one period records as a seamless loop.
private const val LOOP = 16f
// Script of the loop: two flicks (time in s, release strength in radians of throw) and three
// camera changes (time in s, cameras forward from the one the loop started on).
private val FLICKS = arrayOf(1.8f to 2.2f, 10.4f to -1.3f)
private val SWITCHES = arrayOf(4.6f to 1, 8.2f to 2, 12.6f to 0)
// A scripted flick: the finger speeds up over FLICK_DRAG s, lets go, and the ring coasts down
// with a time constant of COAST s. A user's throw coasts the same way.
private const val FLICK_DRAG = 0.28f
private const val COAST = 0.7f
// The idle spin, in rad/s, chosen so that the loop adds up to exactly one turn.
private val IDLE_SPIN = run {
    var thrown = 0f
    for ((_, a) in FLICKS) thrown += a + a * FLICK_DRAG / (2f * COAST)
    (TWO_PI - thrown) / LOOP
}
// After a touch the loop waits this long, in seconds, before it plays again.
private const val USER_HOLD = 3.5f

// The scene, designed on a 402 dp wide screen and scaled to the real width. A pinhole camera with
// a focal length of FOCAL dp looks at the body standing DEPTH dp away.
private const val DESIGN_WIDTH = 402f
private const val FOCAL = 700f
private const val DEPTH = 700f
// The middle of the body, as a share of the height between the safe areas.
private const val BODY_AT = 0.41f
// The body rests turned a little to show its right side, and seen a little from above (degrees).
private const val REST_YAW = 28f
private const val REST_PITCH = 15f
private const val REST_ROLL = -2f
// How far the body turns with the ring's speed (degrees) and the speed that turns it ~76% of that.
private const val YAW_GAIN = 26f
private const val YAW_SPEED = 2.4f
// Springs on the body's turn and tilt: stiffness and damping per second, unit mass.
private const val TURN_STIFFNESS = 90f
private const val TURN_DAMPING = 11f
// Vertical drags tilt the body and the ring, rubber-banded: degrees per dp, and the travel at
// which the band has taken half.
private const val TILT_PER_PT = 0.11f
private const val TILT_BAND = 140f
// The body bobs by BOB dp, four times a loop.
private const val BOB = 3f

/** How the prints orbit: one tilted ring, a close ring around a tumbling camera, or two crossed rings. */
enum class OrbitRingStyle(val label: String) {
    Ring("Ring"),
    Tumble("Tumble"),
    Gyro("Gyro"),
}

// The ring styles, each a vector of numbers so that one morphs into another: the ring's radius,
// how far below the body's middle its centre sits, its tilt towards the viewer and its roll in the
// picture plane (degrees); the prints' width and how far they turn towards the outside at the
// sides (degrees); the body's scale; how much the body tumbles (0…1); how far the prints split
// into two rings rolled ±GYRO_ROLL° (0…1); which way the second ring spins; how far the prints
// lean with the ring's roll instead of each at its own angle (0…1); and how far the body and its
// ring sit above the usual place (dp).
private const val S_RADIUS = 0
private const val S_DROP = 1
private const val S_TILT = 2
private const val S_ROLL = 3
private const val S_PRINT = 4
private const val S_TURN = 5
private const val S_BODY = 6
private const val S_TUMBLE = 7
private const val S_SPLIT = 8
private const val S_DIR = 9
private const val S_LEAN = 10
private const val S_RISE = 11
private val STYLE_SHAPES = arrayOf(
    // Ring: a wide, gently tilted halo around a camera at rest.
    floatArrayOf(206f, 14f, 21f, -9f, 74f, 28f, 1f, 0f, 0f, 1f, 0f, 0f),
    // Tumble: a glossy toy camera turning round with a wide ring of polaroids. The ring is sunk below
    // the body and its front edge raised, so the front prints sweep across the lens while the back
    // ones pass low under the body; it is rolled so its left side hangs lower than its right, and
    // every print leans with it. The whole scene sits higher to leave the ring room below.
    floatArrayOf(200f, 155f, -20f, -16f, 76f, 40f, 1.18f, 1f, 0f, 1f, 1f, 72f),
    // Gyro: two rings of five crossing like a gyroscope, spinning against each other.
    floatArrayOf(188f, 6f, 64f, 0f, 66f, 18f, 0.88f, 0f, 1f, -1f, 0f, 0f),
)
private const val TUMBLE_STYLE = 1
private const val GYRO_ROLL = 34f
// A change of style morphs every number over STYLE_TIME s on an ease-in-out curve.
private const val STYLE_TIME = 0.9f
// Tumbling, the camera and its ring turn round together, the front of both travelling left to
// right: whole turns over these spans of the loop (start s, end s) on an ease-in-out curve, between
// the camera changes; between them the ring rests and the body only sways TUMBLE_SWAY° four times a
// loop. The body turns about an axis leaning back and to the side, so halfway round it shows its
// base. The turns are locked to the clock, so a period records as a seamless loop.
private val TUMBLE_TURNS = arrayOf(0.4f to 4.2f, 9.7f to 12.5f)
private val TUMBLE_AXIS = floatArrayOf(0.1f, 1f, 0.24f)
// After a touch the body comes back round to face the viewer at this rate (1/s).
private const val TUMBLE_HOME = 2.6f
private const val TUMBLE_SWAY = 5f
// The toy faces the viewer more squarely than the box camera: its rest turn is this much less (°).
private const val TOY_YAW = 20f
// Each print floats by PRINT_FLOAT dp, twice a loop, and has its own roll.
private const val PRINT_FLOAT = 4f
private val PRINT_ROLLS = floatArrayOf(-8f, 6f, -12f, 4f, 10f, -5f, 8f, -10f, 3f, -6f, 9f, -3f)
// Prints behind the body darken up to BACK_SHADE and blur up to BACK_BLUR px.
private const val BACK_SHADE = 0.62f
private const val BACK_BLUR = 3.2f
// Finger travel follows the front prints. Throws are capped, in rad/s, and a throw faster than
// FLICK_TICK rad/s ends with a light haptic.
private const val MAX_SPIN = 12f
private const val FLICK_TICK = 2f
private const val DRAG_SLOP = 6f
private const val TAP_MS = 400L

// A camera change: the body spins once (SPIN_TIME s), morphing into the next between 30% and 70%
// of the turn while it dips; the prints fly off (outward and down, staggered) and the new ones
// are shot out of the lens to their places, one after another.
private const val SPIN_TIME = 1.1f
private const val SPIN_DIP = 0.14f
private const val SPIN_LIFT = 18f
private const val OUT_TIME = 0.42f
private const val OUT_STAGGER = 0.025f
private const val IN_DELAY = 0.35f
private const val IN_STAGGER = 0.07f
private const val IN_TIME = 0.75f
// A new change waits until the deal is done.
private const val DEAL_TIME = IN_DELAY + IN_STAGGER * 9 + IN_TIME
// Tapping the body fires the shutter: the glass flashes for FLASH_TIME s and the body jumps back.
private const val FLASH_TIME = 0.45f
private const val SHUTTER_PUNCH = 0.035f

// The bar of camera thumbnails: spacing, thumbnail width, and its middle above the safe area.
private const val BAR_SPACING = 82f
private const val THUMB_WIDTH = 46f
private const val BAR_ABOVE = 64f
private const val BAR_STIFFNESS = 200f
private const val BAR_DAMPING = 24f
// A drag that starts this close to the bar's middle line scrolls the bar instead of spinning the ring (dp).
private const val BAR_TOUCH = 44f
// The bar scrolls freely: a flick coasts down with a time constant of BAR_COAST s, flicks are capped
// at BAR_MAX_SPEED cameras per second, and it may be pulled BAR_RUBBER cameras past either end before
// it springs back.
private const val BAR_COAST = 0.33f
private const val BAR_MAX_SPEED = 30f
private const val BAR_RUBBER = 0.45f

internal const val LEATHER_GRAIN = 0.42f
internal const val IMAGE_FADE = 0.35f
// Light comes from the upper left, in front.
private val LIGHT = run {
    val l = floatArrayOf(-0.45f, -0.75f, -0.55f)
    val n = sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2])
    floatArrayOf(l[0] / n, l[1] / n, l[2] / n)
}

private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=300&h=340&q=80"

private val Cameras = listOf(
    OrbitCamera(
        "KDX", "SLR · 35 MM · F/1.8 · 1/1000",
        floatArrayOf(224f, 124f, 70f), floatArrayOf(92f, 34f, 52f), floatArrayOf(40f, 38f), floatArrayOf(0f, 10f), 0f, 0.3f,
        0x7a2e1c, 0xcbc9c3, 0x19191b, 0xdc3428, 0x33386b,
        listOf(
            "photo-1590486803833-1c5dc8ddd4c8", "photo-1569346276519-709519eeaa51", "photo-1568190002605-b51fa38eac46",
            "photo-1518676590629-3dcbd9c5a5c9", "photo-1597157153515-028fa3d4bd69", "photo-1561871733-40a3338b8cb4",
            "photo-1643138874737-33babf17f0a8", "photo-1598461336473-b0cc035d4e35", "photo-1603118258032-aea76c546616",
            "photo-1452587925148-ce544e77e70d",
        ),
        toyShell = 0x8a2e14,
        toyBody = 0x252238,
    ),
    OrbitCamera(
        "ORT4", "RANGEFINDER · F/2 · 1/500",
        floatArrayOf(238f, 110f, 62f), floatArrayOf(0f, 0f, 40f), floatArrayOf(34f, 30f), floatArrayOf(-16f, 12f), 1f, 0.34f,
        0x9c5a2c, 0x2b2b2e, 0x1d1d20, 0xec8a2e, 0x28505a,
        listOf(
            "photo-1623475329493-889804e377f8", "photo-1613749742342-5abdaa0d0af3", "photo-1649366735070-8e78a4f86f31",
            "photo-1617618482053-36e9280bbf5a", "photo-1622297118224-2a816821a174", "photo-1570385404967-fe4e1b48454b",
            "photo-1542388481-52d5a10ae242", "photo-1714201623945-0079e2150240", "photo-1690023430523-a298e78ab6fd",
            "photo-1602230168052-741c2b79ea91",
        ),
        toyShell = 0x1f1f22,
        toyBody = 0x7b4426,
    ),
    OrbitCamera(
        "ADR3", "HALF FRAME · F/2.8 · 1/250",
        floatArrayOf(206f, 128f, 74f), floatArrayOf(80f, 30f, 50f), floatArrayOf(42f, 50f), floatArrayOf(0f, 12f), 0f, 0.28f,
        0x424b31, 0xc8c3b6, 0x202020, 0xf0bd40, 0x4f2d60,
        listOf(
            "photo-1573455494060-c5595004fb6c", "photo-1549693578-d683be217e58", "photo-1480796927426-f609979314bd",
            "photo-1564284369929-026ba231f89b", "photo-1542931287-023b922fa89b", "photo-1561503972-839d0c56de17",
            "photo-1525230071276-4a87f42f469e", "photo-1601042879364-f3947d3f9c16", "photo-1498036882173-b41c28a8ba34",
            "photo-1573455494057-12684d151bf4",
        ),
        toyShell = 0xbdb9ae,
        toyBody = 0x3c4530,
    ),
    OrbitCamera(
        "MRV6", "6×6 · F/3.5 · 1/400",
        floatArrayOf(196f, 146f, 84f), floatArrayOf(96f, 18f, 70f), floatArrayOf(46f, 40f), floatArrayOf(0f, 16f), 0f, 0.22f,
        0x1c1c1f, 0xbcbdc2, 0x141416, 0xd8343c, 0x2c3c66,
        listOf(
            "photo-1605437241278-c1806d14a4d9", "photo-1620584899131-a5ff5f8fbb03", "photo-1633014041037-f5446fb4ce99",
            "photo-1620584898989-d39f7f9ed1b7", "photo-1694678505383-676d78ea3b96", "photo-1515569067071-ec3b51335dd0",
            "photo-1708805282695-ef186db20192", "photo-1732357624591-f2137085659b", "photo-1708805283017-c662be2c7a44",
            "photo-1520340356584-f9917d1eea6f",
        ),
        toyShell = 0xa82429,
        toyBody = 0x1c1c20,
    ),
)

// A camera flattened to numbers, so two of them can be morphed into each other. Indices into it:
internal const val V_W = 0
internal const val V_H = 1
internal const val V_D = 2
internal const val V_HUMP_W = 3
internal const val V_HUMP_H = 4
internal const val V_HUMP_D = 5
internal const val V_LENS_R = 6
internal const val V_LENS_L = 7
internal const val V_LENS_X = 8
internal const val V_LENS_Y = 9
internal const val V_WINDOWS = 10
internal const val V_PLATE = 11
internal const val V_LEATHER = 12
internal const val V_METAL = 15
internal const val V_BARREL = 18
internal const val V_ACCENT = 21
internal const val V_GLASS = 24
internal const val V_SHELL = 27
internal const val V_TOY_BODY = 30

private fun rgb(hex: Long) = floatArrayOf(((hex shr 16) and 255) / 255f, ((hex shr 8) and 255) / 255f, (hex and 255) / 255f)

private fun toVector(c: OrbitCamera) =
    c.body + c.hump + c.lens + c.lensAt + floatArrayOf(c.windows, c.plate) + rgb(c.leather) + rgb(c.metal) + rgb(c.barrel) + rgb(c.accent) + rgb(c.glass) +
        rgb(c.toyShell ?: c.metal) + rgb(c.toyBody ?: c.leather)

private fun mixVector(a: FloatArray, b: FloatArray, t: Float) = FloatArray(a.size) { a[it] + (b[it] - a[it]) * t }

// MARK: math

internal class View3(val f: Float, val cx: Float, val cy: Float)

internal class Pose(val x: Float, val y: Float, val z: Float, val s: Float, val r: FloatArray)

internal class FaceInfo(val m: FloatArray?, val visible: Boolean, val light: Float)

internal class PrintState(
    val set: Int,
    val index: Int,
    val z: Float,
    val m: FloatArray,
    val facing: Boolean,
    val alpha: Float,
    val shade: Float,
    val blur: Float,
) {
    val key get() = set * 1000 + index
}

internal val U_X = floatArrayOf(1f, 0f, 0f)
internal val V_Y = floatArrayOf(0f, 1f, 0f)
internal val V_BACK = floatArrayOf(0f, 0f, -1f)
internal val N_FRONT = floatArrayOf(0f, 0f, -1f)
internal val N_UP = floatArrayOf(0f, -1f, 0f)

internal fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeInOut(p: Float) = if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f
private fun easeOut(p: Float) = 1f - (1f - p).pow(3)
private fun easeIn(p: Float) = p * p * p
private fun backOut(p: Float): Float {
    val c = 1.5f
    val q = p - 1f
    return 1f + (c + 1f) * q * q * q + c * q * q
}
private fun smooth(p: Float) = p * p * (3f - 2f * p)

/** JavaScript's Math.round: halves go up. */
internal fun jsRound(x: Float) = floor(x + 0.5f).toInt()

/** Rubber band: follows `x` at first, then gives less and less, never passing `band`. */
private fun rubber(x: Float, band: Float) = Math.signum(x) * band * (1f - 1f / (abs(x) / band + 1f))

/** The loop's turn of the ring at `t` seconds into it, beyond where it started. */
private fun flickTurn(t: Float, at: Float, throwBy: Float): Float {
    if (t <= at) return 0f
    val drag = throwBy * FLICK_DRAG / (2f * COAST)
    if (t < at + FLICK_DRAG) {
        val s = (t - at) / FLICK_DRAG
        return drag * s * s
    }
    return drag + throwBy * (1f - exp(-(t - at - FLICK_DRAG) / COAST))
}

private fun scriptTurn(t: Float, reduced: Boolean, style: Int): Float {
    if (reduced) return 0f
    if (style == TUMBLE_STYLE) {
        var turned = 0f
        for ((start, end) in TUMBLE_TURNS) turned += TWO_PI * easeInOut(clamp01((t - start) / (end - start)))
        return turned
    }
    var turn = IDLE_SPIN * t
    for ((at, by) in FLICKS) turn += flickTurn(t, at, by)
    return turn
}

/** 3×3 row-major product. */
private fun mul3(a: FloatArray, b: FloatArray) = FloatArray(9) {
    val r = it / 3
    val c = it % 3
    a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
}

/**
 * Rotation of an object: rolled in the picture plane, turned about the vertical (positive shows its
 * right side) and tipped about the horizontal (positive shows its top). Angles in radians; the
 * screen's y points down and z away from the viewer.
 */
private fun rotation(yaw: Float, pitch: Float, roll: Float): FloatArray {
    val cr = cos(roll)
    val sr = sin(roll)
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    val rz = floatArrayOf(cr, -sr, 0f, sr, cr, 0f, 0f, 0f, 1f)
    val ry = floatArrayOf(cy, 0f, sy, 0f, 1f, 0f, -sy, 0f, cy)
    val rx = floatArrayOf(1f, 0f, 0f, 0f, cp, -sp, 0f, sp, cp)
    return mul3(rx, mul3(ry, rz))
}

private fun turn(m: FloatArray, x: Float, y: Float, z: Float) =
    floatArrayOf(m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z)

/** A point of an object, from its own coordinates to the eye's. */
private fun place(pose: Pose, x: Float, y: Float, z: Float): FloatArray {
    val p = turn(pose.r, x * pose.s, y * pose.s, z * pose.s)
    return floatArrayOf(pose.x + p[0], pose.y + p[1], pose.z + p[2])
}

/**
 * The perspective matrix that draws a flat face: a point (u, v) of the face's own drawing lands
 * where the eye sees O + u·U + v·V. null when the face is behind the eye.
 */
private fun faceMatrix(view: View3, o: FloatArray, u: FloatArray, v: FloatArray): FloatArray? {
    val z = o[2]
    if (z <= 1f) return null
    val f = view.f
    val cx = view.cx
    val cy = view.cy
    return floatArrayOf(
        (f * u[0] + cx * u[2]) / z,
        (f * v[0] + cx * v[2]) / z,
        (f * o[0] + cx * o[2]) / z,
        (f * u[1] + cy * u[2]) / z,
        (f * v[1] + cy * v[2]) / z,
        (f * o[1] + cy * o[2]) / z,
        u[2] / z,
        v[2] / z,
        1f,
    )
}

/** A face of an object: centre c, its drawing's axes u and v, its outward normal n. */
internal fun face(view: View3, pose: Pose, c: FloatArray, u: FloatArray, v: FloatArray, n: FloatArray): FaceInfo {
    val o = place(pose, c[0], c[1], c[2])
    val uu = turn(pose.r, u[0] * pose.s, u[1] * pose.s, u[2] * pose.s)
    val vv = turn(pose.r, v[0] * pose.s, v[1] * pose.s, v[2] * pose.s)
    val nn = turn(pose.r, n[0], n[1], n[2])
    val facing = nn[0] * o[0] + nn[1] * o[1] + nn[2] * o[2]
    val lit = max(0f, nn[0] * LIGHT[0] + nn[1] * LIGHT[1] + nn[2] * LIGHT[2])
    return FaceInfo(faceMatrix(view, o, uu, vv), facing < 0f, 0.3f + 0.7f * lit)
}

/** Where slot `theta` of a ring (radius, tilt and roll in degrees, drop) is, around the body's middle. */
private fun ringPoint(theta: Float, radius: Float, tilt: Float, roll: Float, drop: Float): FloatArray {
    val x = radius * sin(theta)
    val z = -radius * cos(theta)
    val t = tilt * PI.toFloat() / 180f
    val y1 = -z * sin(t)
    val z1 = z * cos(t)
    val r = roll * PI.toFloat() / 180f
    return floatArrayOf(x * cos(r) - y1 * sin(r), x * sin(r) + y1 * cos(r) + drop, z1)
}

/** The style vector `t` of the way from `from` to style `to`. */
private fun styleMix(from: FloatArray, to: Int, t: Float): FloatArray {
    val shape = STYLE_SHAPES[to.coerceIn(0, STYLE_SHAPES.size - 1)]
    val e = easeInOut(clamp01(t))
    return FloatArray(shape.size) {
        val a = if (it < from.size) from[it] else shape[it]
        a + (shape[it] - a) * e
    }
}

/** An angle brought into (−π, π]. */
private fun wrapPi(a: Double): Double {
    val t = PI * 2
    return a - t * floor(a / t + 0.5)
}

/** Rotation by `a` radians about the axis (x, y, z), normalised here (row-major). */
private fun axisRotation(x: Float, y: Float, z: Float, a: Float): FloatArray {
    val n = sqrt(x * x + y * y + z * z)
    val len = if (n > 0f) n else 1f
    val ux = x / len
    val uy = y / len
    val uz = z / len
    val c = cos(a)
    val s = sin(a)
    val k = 1f - c
    return floatArrayOf(
        c + ux * ux * k, ux * uy * k - uz * s, ux * uz * k + uy * s,
        uy * ux * k + uz * s, c + uy * uy * k, uy * uz * k - ux * s,
        uz * ux * k - uy * s, uz * uy * k + ux * s, c + uz * uz * k,
    )
}

/**
 * The tumble at `t` seconds into the loop, with the body turned `turned` radians round with its
 * ring, scaled by `weight` (0 none, 1 all of it). The turn is taken the short way round, so leaving
 * the style never spins the body back.
 */
private fun tumbleMatrix(t: Float, turned: Double, weight: Float): FloatArray {
    val sway = TUMBLE_SWAY * RAD * sin(t / LOOP * PI.toFloat() * 8f) * weight
    val m = axisRotation(0.25f, 1f, 0.1f, sway)
    return mul3(axisRotation(TUMBLE_AXIS[0], TUMBLE_AXIS[1], TUMBLE_AXIS[2], -wrapPi(turned).toFloat() * weight), m)
}

/**
 * The camera as the style wants it: the box camera, the toy, or both cross-fading while the style
 * morphs (`toy` 0…1). Below Android 13 the box camera takes the toy's colours instead.
 */
private fun drawAnyCamera(canvas: Canvas, p: OrbitPaints, view: View3, pose: Pose, v: FloatArray, toy: Float, detail: Boolean, label: String, flash: Float, l: Float, t: Float, r: Float, b: Float) {
    val toyPainter = p.toy
    if (toyPainter == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        drawCamera(canvas, p, view, pose, if (toy > 0.001f) toyTinted(v, toy) else v, detail, label, flash)
        return
    }
    if (toy < 0.99f) {
        if (toy > 0.01f) canvas.saveLayerAlpha(l, t, r, b, ((1f - toy) * 255f).roundToInt())
        drawCamera(canvas, p, view, pose, v, detail, label, flash)
        if (toy > 0.01f) canvas.restore()
    }
    if (toy > 0.01f) toyPainter.draw(canvas, view, pose, OrbitToyShape(v), toy, flash)
}

/** Finger travel per radian of turn: the speed of the front prints on screen. */
private fun travelPerRadian(radius: Float, width: Float) =
    max(1f, radius * FOCAL / max(1f, DEPTH - radius) * (width / DESIGN_WIDTH))

private const val RAD = (PI / 180).toFloat()

// MARK: state

/** Everything the frame loop, the gestures and the picture share, kept off Compose state. */
private class OrbitMotion(cameras: List<OrbitCamera>, firstStyle: Int) {
    // The ring's turn (rad, kept in Double so hours of idle spin stay precise) and spin (rad/s).
    var angle = 0.0
    var spin = 0f
    var dragging = false
    var dragFrom = 0.0
    var dragX = 0f
    var dragY = 0f
    // A drag along the bar scrolls it: whether the drag is on the bar, and where the bar was when it began.
    var barDrag = false
    var barFrom = 0f
    // The body's turn and tilt, on springs (degrees, degrees per second).
    var yaw = REST_YAW
    var yawSpeed = 0f
    var tilt = 0f
    var tiltSpeed = 0f
    // The camera shown, the one before it, and seconds since the change.
    var from = 0
    var to = 0
    var since = 100f
    var shutter = 100f
    // The bar's scroll position, in cameras, and its speed. It coasts where a flick leaves it, or
    // glides to a chosen camera while `barFollow` is set; the highlight follows the chosen camera on
    // its own spring (scrolling never chooses).
    var bar = 0f
    var barSpeed = 0f
    var barTarget = 0
    var barFollow = true
    var pick = 0f
    var pickSpeed = 0f
    // The loop: its clock, the turn it started from, the camera it started on, the style whose turn it
    // plays; and the pause a touch makes.
    var clock = 0f
    var script = 0f
    var scriptFrom = 0.0
    var scriptCamera = 0
    var scriptStyle = firstStyle
    var hold = 0f
    // Tumbling, the body turns round with the ring: by the ring's turn beyond `camHome` (rad).
    var camHome = 0.0
    // The ring style: the numbers it is morphing from, the style it is morphing to, and seconds since.
    var styleFrom = STYLE_SHAPES[firstStyle]
    var styleTo = firstStyle
    var styleSince = STYLE_TIME
    // The second ring's own turn (rad).
    var angleB = 0.0
    var radiusNow = STYLE_SHAPES[firstStyle][S_RADIUS]
    // Seconds since the block appeared. The picture reads it, so it is drawn again every frame.
    var elapsed by mutableFloatStateOf(0f)
    val loaded: Array<Array<LoadedPhoto?>> = Array(cameras.size) { arrayOfNulls(cameras[it].photos.size) }
    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f

    /** Morphs to style `index` from wherever the current morph is. */
    fun chooseStyle(index: Int) {
        if (index == styleTo || index < 0 || index >= STYLE_SHAPES.size) return
        styleFrom = styleMix(styleFrom, styleTo, styleSince / STYLE_TIME)
        styleTo = index
        styleSince = 0f
    }
}

/**
 * A retro camera hangs in the dark, drawn in true perspective, with ten polaroids orbiting it on a
 * tilted ring: the prints behind it shrink, darken and blur. Dragging sideways spins the ring, a
 * throw coasts, and the body turns with the spin; dragging up or down tilts the scene on a rubber
 * band. A bar of thumbnails swaps the body: it spins once while it morphs into the next model, the
 * prints fly off, and new ones are shot out of the lens. Tapping the body fires the shutter. A
 * switcher at the top morphs the ring between three styles.
 *
 * @param initialRingStyle The style the block opens with; the switcher at the top changes it, morphing smoothly.
 * @param showStylePicker Shows the Ring · Tumble · Gyro switcher at the top.
 * @param onStyleChange Called with the style chosen in the switcher.
 * @param autoPlay Plays a 16 s loop: idle spin, a flick, camera changes, back to the first.
 * @param onSelect Called with the index of a camera when it is chosen.
 */
@Composable
fun OrbitPhotoRing(
    modifier: Modifier = Modifier,
    cameras: List<OrbitCamera> = Cameras,
    initialRingStyle: OrbitRingStyle = OrbitRingStyle.Ring,
    showStylePicker: Boolean = true,
    onStyleChange: ((OrbitRingStyle) -> Unit)? = null,
    autoPlay: Boolean = false,
    background: Color = Color(0xFF050506),
    textColor: Color = Color(0xFFF3F1EA),
    onSelect: ((Int) -> Unit)? = null,
) {
    // Every camera needs a print; a list without cameras falls back to the four built in.
    val shown = if (cameras.isNotEmpty() && cameras.all { it.photos.isNotEmpty() }) cameras else Cameras
    val reducedMotion = rememberReducedMotion()
    val count = shown.size
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val selecting by rememberUpdatedState(onSelect)
    // The style chosen in the switcher; a new camera list keeps it.
    var styleIndex by remember { mutableIntStateOf(initialRingStyle.ordinal) }
    val motion = remember(shown) { OrbitMotion(shown, styleIndex) }
    val styleChanged by rememberUpdatedState(onStyleChange)
    val vectors = remember(shown) { shown.map(::toVector) }
    val paints = remember { OrbitPaints() }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        motion.top = safeTop
        motion.bottom = safeBottom
    }

    LaunchedEffect(motion, autoPlay) {
        motion.hold = 0f
        motion.script = 0f
        motion.scriptFrom = motion.angle
        motion.scriptCamera = motion.to
    }

    // Every print of every camera is fetched up front; each fades in once it arrives.
    LaunchedEffect(motion, context) {
        val loader = SingletonImageLoader.get(context)
        shown.forEachIndexed { ci, camera ->
            camera.photos.forEachIndexed { pi, id ->
                launch {
                    val request = ImageRequest.Builder(context)
                        .data(photo(id))
                        .size(Size.ORIGINAL)
                        // A software bitmap can carry mipmaps, which keep the small prints from shimmering.
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request) as? SuccessResult ?: return@launch
                    val bitmap = result.image.toBitmap()
                    bitmap.setHasMipMap(true)
                    motion.loaded[ci][pi] = LoadedPhoto(bitmap, motion.elapsed)
                }
            }
        }
    }

    /** Starts a camera change, unless one is still being dealt. */
    fun choose(index: Int, byUser: Boolean) {
        if (index < 0 || index >= count) return
        if (index == motion.to) {
            // Tapping the chosen camera only brings it back to the middle of the bar.
            motion.barTarget = index
            motion.barFollow = true
            return
        }
        if (motion.since < DEAL_TIME) return
        motion.from = motion.to
        motion.to = index
        motion.barTarget = index
        motion.barFollow = true
        motion.since = 0f
        if (byUser) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            selecting?.invoke(index)
        }
    }

    fun touched() {
        motion.hold = USER_HOLD
    }

    LaunchedEffect(motion, reducedMotion, autoPlay) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) advance(motion, dt, reducedMotion, autoPlay, ::choose)
            }
        }
    }

    fun pickStyle(index: Int) {
        if (index == styleIndex) return
        styleIndex = index
        motion.chooseStyle(index)
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
        styleChanged?.invoke(OrbitRingStyle.entries[index])
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "A ${shown.joinToString(", ") { it.name }} camera with ten photo prints orbiting it. Drag sideways to spin the prints; swipe the bar of cameras below to browse them and tap one to change cameras."
                }
                .pointerInput(motion, reducedMotion) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        // A drag that starts on the bar scrolls it, and a finger on the bar catches it.
                        val onBar = abs(start.y / density.density - (motion.height - motion.bottom - BAR_ABOVE)) < BAR_TOUCH
                        motion.barDrag = onBar
                        if (onBar) {
                            motion.barFollow = false
                            motion.barSpeed = 0f
                        }
                        var panning = false
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val dx = (change.position.x - start.x) / density.density
                            val dy = (change.position.y - start.y) / density.density
                            if (!change.pressed) {
                                if (panning) {
                                    change.consume()
                                    touched()
                                    val velocityX = tracker.calculateVelocity().x / density.density
                                    if (motion.barDrag) {
                                        // The bar coasts on from the flick and stops wherever it runs out.
                                        // Scrolling only moves the bar: a camera is chosen by tapping it.
                                        val speed = -velocityX / (BAR_SPACING * motion.width / DESIGN_WIDTH)
                                        motion.barSpeed = if (reducedMotion) 0f else speed.coerceIn(-BAR_MAX_SPEED, BAR_MAX_SPEED)
                                        motion.barFollow = false
                                    } else {
                                        val thrown = if (reducedMotion) 0f else (velocityX / travelPerRadian(motion.radiusNow, motion.width)).coerceIn(-MAX_SPIN, MAX_SPIN)
                                        motion.spin = thrown
                                        if (abs(thrown) > FLICK_TICK) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    motion.dragging = false
                                } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    touched()
                                    tapAt(motion, change.position.x / density.density, change.position.y / density.density, ::choose) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    }
                                }
                                released = true
                                continue
                            }
                            if (!panning && hypot(dx, dy) >= DRAG_SLOP) {
                                panning = true
                                touched()
                                motion.barFrom = motion.bar
                                motion.dragFrom = motion.angle
                                motion.dragging = true
                            }
                            if (panning) {
                                change.consume()
                                motion.dragX = dx
                                motion.dragY = dy
                            }
                        }
                        // A finger lost to the system still lets go of the ring and the bar.
                        if (!released) motion.dragging = false
                        motion.barDrag = false
                    }
                },
        ) {
            motion.width = maxWidth.value
            motion.height = maxHeight.value
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                scale(density.density, density.density, pivot = Offset.Zero) {
                    drawIntoCanvas {
                        drawOrbit(it.nativeCanvas, motion, shown, vectors, paints, reducedMotion, background, textColor, density.density)
                    }
                }
            }
        }
        // Over the canvas as a sibling, so taps on the switcher never reach the ring's gestures.
        if (showStylePicker) {
            OrbitStylePicker(
                selected = styleIndex,
                onPick = ::pickStyle,
                ink = textColor,
                reduced = reducedMotion,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = (safeTop + 10f).dp),
            )
        }
    }
}

/** One frame: the drag, the self-playing script or the coast, and the springs on the body and the bar. */
private fun advance(m: OrbitMotion, dt: Float, reduced: Boolean, auto: Boolean, choose: (Int, Boolean) -> Unit) {
    m.elapsed += dt
    m.clock = (m.clock + dt) % LOOP
    m.since += dt
    m.shutter += dt
    m.styleSince += dt
    val shape = styleMix(m.styleFrom, m.styleTo, m.styleSince / STYLE_TIME)
    m.radiusNow = shape[S_RADIUS]
    val perRadian = travelPerRadian(shape[S_RADIUS], m.width)
    // Tumbling, the ring rests between its turns instead of drifting.
    val idle = if (reduced) 0f else IDLE_SPIN * (1f - shape[S_TUMBLE])
    val before = m.angle

    if (m.dragging && !m.barDrag) {
        m.angle = m.dragFrom + m.dragX / perRadian
        m.spin += (((m.angle - before) / dt).toFloat() - m.spin) * 0.35f
    } else {
        if (m.hold > 0f) {
            m.hold -= dt
            if (m.hold <= 0f) {
                // The loop starts again from wherever the ring and the camera are.
                m.script = 0f
                m.scriptFrom = m.angle
                m.scriptCamera = m.to
            }
        }
        if (m.scriptStyle != m.styleTo) {
            // The loop's turn changes with the style; the ring carries on from where it is.
            val at = m.script
            m.scriptFrom += scriptTurn(at, reduced, m.scriptStyle) - scriptTurn(at, reduced, m.styleTo)
            m.scriptStyle = m.styleTo
        }
        if (auto && m.hold <= 0f) {
            val was = m.script
            var now = was + dt
            if (now >= LOOP) {
                m.scriptFrom += scriptTurn(LOOP, reduced, m.scriptStyle)
                now -= LOOP
            }
            for ((at, ahead) in SWITCHES) {
                val crossed = if (now >= was) was < at && now >= at else was < at || now >= at
                if (crossed) choose((m.scriptCamera + ahead) % m.loaded.size, false)
            }
            m.script = now
            m.angle = m.scriptFrom + scriptTurn(now, reduced, m.scriptStyle)
            m.spin = ((m.angle - before) / dt).toFloat()
        } else if (reduced) {
            m.spin = 0f
        } else {
            // A throw coasts down to the idle spin.
            val s = idle + (m.spin - idle) * exp(-dt / COAST)
            m.spin = s
            m.angle = before + s * dt
        }
    }

    // The second ring follows the first, the way its style spins it, and is pulled onto its place:
    // the first ring's turn when the prints make one ring, its mirror image when they make two
    // (mirrored, a print of one ring never meets a print of the other where the rings cross).
    val moved = m.angle - before
    // Tumbling, the body turns with the ring while the loop plays or a finger holds it; otherwise it
    // comes back round to face the viewer (in the other styles it is always home).
    val ringHeld = m.dragging && !m.barDrag
    val scripted = auto && m.hold <= 0f
    if (m.styleTo != TUMBLE_STYLE) m.camHome += moved
    if (m.styleTo != TUMBLE_STYLE || (!scripted && !ringHeld)) {
        m.camHome += wrapPi(m.angle - m.camHome) * (1f - exp(-dt * TUMBLE_HOME))
    }
    val mirror = if (STYLE_SHAPES[m.styleTo][S_DIR] < 0f) -1.0 else 1.0
    var b = m.angleB + moved * shape[S_DIR]
    b += wrapPi(mirror * m.angle - b) * min(1f, dt * 4f)
    m.angleB = b

    // The body turns with the ring's spin and tilts with a vertical drag, both on springs.
    val turnTo = REST_YAW + if (reduced) 0f else (1f - shape[S_TUMBLE]) * YAW_GAIN * tanh((m.spin - idle) / YAW_SPEED)
    m.yawSpeed += (TURN_STIFFNESS * (turnTo - m.yaw) - TURN_DAMPING * m.yawSpeed) * dt
    m.yaw += m.yawSpeed * dt
    val tiltTo = if (m.dragging && !m.barDrag) rubber(m.dragY * TILT_PER_PT, TILT_BAND * TILT_PER_PT) else 0f
    m.tiltSpeed += (TURN_STIFFNESS * (tiltTo - m.tilt) - TURN_DAMPING * m.tiltSpeed) * dt
    m.tilt += m.tiltSpeed * dt
    val last = m.loaded.size - 1f
    if (m.barDrag && m.dragging) {
        // The bar follows the finger, rubber-banding past its first and last camera.
        val raw = m.barFrom - m.dragX / (BAR_SPACING * m.width / DESIGN_WIDTH)
        val at = if (raw < 0f) rubber(raw, BAR_RUBBER) else if (raw > last) last + rubber(raw - last, BAR_RUBBER) else raw
        m.barSpeed = (at - m.bar) / dt
        m.bar = at
    } else if (m.barFollow) {
        m.barSpeed += (BAR_STIFFNESS * (m.barTarget - m.bar) - BAR_DAMPING * m.barSpeed) * dt
        m.bar += m.barSpeed * dt
    } else {
        // Left alone, the bar coasts down; past an end it springs back to it.
        val edge = m.bar.coerceIn(0f, last)
        if (edge != m.bar) {
            m.barSpeed += (BAR_STIFFNESS * (edge - m.bar) - BAR_DAMPING * m.barSpeed) * dt
        } else {
            m.barSpeed *= exp(-dt / BAR_COAST)
        }
        m.bar += m.barSpeed * dt
    }
    m.pickSpeed += (BAR_STIFFNESS * (m.to - m.pick) - BAR_DAMPING * m.pickSpeed) * dt
    m.pick += m.pickSpeed * dt
}

/** A tap on the bar picks that camera; a tap on the body fires the shutter. */
private fun tapAt(m: OrbitMotion, x: Float, y: Float, choose: (Int, Boolean) -> Unit, shutter: () -> Unit) {
    val k = m.width / DESIGN_WIDTH
    val barY = m.height - m.bottom - BAR_ABOVE
    if (abs(y - barY) < 44f) {
        choose(jsRound(m.bar + (x - m.width / 2f) / (BAR_SPACING * k)), true)
        return
    }
    val bodyY = m.top + (m.height - m.top - m.bottom) * BODY_AT - STYLE_SHAPES[m.styleTo][S_RISE] * k
    if (hypot(x - m.width / 2f, y - bodyY) < 120f * k * STYLE_SHAPES[m.styleTo][S_BODY]) {
        m.shutter = 0f
        shutter()
    }
}

private fun Color.at(alpha: Float) = copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()

private fun drawOrbit(
    canvas: Canvas,
    m: OrbitMotion,
    cameras: List<OrbitCamera>,
    vectors: List<FloatArray>,
    p: OrbitPaints,
    reduced: Boolean,
    background: Color,
    textColor: Color,
    density: Float,
) {
    val width = m.width
    val height = m.height
    if (width <= 0f) return
    val k = width / DESIGN_WIDTH
    val now = m.elapsed
    val loop = m.clock
    val shape = styleMix(m.styleFrom, m.styleTo, m.styleSince / STYLE_TIME)
    // How much of the toy camera shows, and how much it tumbles (none under reduced motion).
    val toy = shape[S_TUMBLE]
    val tumbling = if (reduced) 0f else toy
    val cy = m.top + (height - m.top - m.bottom) * BODY_AT - shape[S_RISE] * k
    val view = View3(FOCAL * k, width / 2f, cy)
    val fill = p.fill

    fill.shader = null
    fill.color = background.toArgb()
    canvas.drawRect(0f, 0f, width, height, fill)
    // A dim warm pool of light behind the body.
    fill.shading = android.graphics.RadialGradient(
        width / 2f, cy + 20f * k, width * 0.75f,
        intArrayOf(0xFF221D1A.toInt(), 0xFF0D0C0C.toInt(), background.toArgb()),
        floatArrayOf(0f, 0.5f, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width, height, fill)
    fill.shader = null

    // The camera: the morph between the last body and this one, the spin and the dip.
    val t = m.since
    val change = clamp01(t / SPIN_TIME)
    val fromIndex = m.from
    val toIndex = m.to
    val morph = smooth(clamp01((change - 0.3f) / 0.4f))
    val body = mixVector(vectors[fromIndex], vectors[toIndex], morph)
    val dip = sin(PI.toFloat() * change)
    // Tumbling, the change spins the body the way its ring turns.
    val spinWay = if (STYLE_SHAPES[m.styleTo][S_TUMBLE] > 0.5f) -1f else 1f
    val turning = if (reduced) 0f else spinWay * 360f * easeInOut(change)
    val flashAge = m.shutter / FLASH_TIME
    val flash = if (flashAge < 1f) 1f - flashAge else 0f
    val punch = if (flashAge < 1f) SHUTTER_PUNCH * sin(PI.toFloat() * min(1f, flashAge * 2f)) else 0f
    val bob = if (reduced) 0f else BOB * sin(loop / LOOP * PI.toFloat() * 8f)
    val pitch = REST_PITCH + m.tilt
    val pose = Pose(
        0f,
        bob - if (reduced) 0f else SPIN_LIFT * dip,
        DEPTH,
        shape[S_BODY] * (1f - (if (reduced) 0f else SPIN_DIP) * dip) * (1f - punch),
        mul3(tumbleMatrix(loop, m.angle - m.camHome, tumbling), rotation((m.yaw - TOY_YAW * toy + turning) * RAD, pitch * RAD, REST_ROLL * RAD)),
    )
    val lensTip = place(pose, body[V_LENS_X], body[V_LENS_Y], -body[V_D] / 2f - body[V_LENS_L])

    // The prints: the ones flying off and the ones being dealt, then the ring at rest.
    val prints = ArrayList<PrintState>(20)
    val radius = shape[S_RADIUS]
    val ringTilt = shape[S_TILT] + m.tilt * 0.6f
    val split = shape[S_SPLIT]
    val turnNow = m.angle
    val turnB = m.angleB
    val sets = if (fromIndex != toIndex && t < DEAL_TIME) intArrayOf(fromIndex, toIndex) else intArrayOf(toIndex)
    for (set in sets) {
        val outgoing = set != toIndex
        val n = cameras[set].photos.size
        for (i in 0 until n) {
            // Odd prints make the second ring when the style splits them.
            val second = i % 2 == 1
            val theta = ((if (second) turnB else turnNow) + i.toDouble() / n * PI * 2).toFloat()
            val ringRoll = shape[S_ROLL] + (if (second) -1f else 1f) * split * GYRO_ROLL
            val slot = ringPoint(theta, radius, ringTilt, ringRoll, shape[S_DROP])
            val float = if (reduced) 0f else PRINT_FLOAT * sin(loop / LOOP * PI.toFloat() * 4f + i * 1.7f)
            var x = slot[0]
            var y = slot[1] + float + pose.y
            var z = slot[2] + DEPTH
            var s = shape[S_PRINT]
            var alpha = 1f
            val lean = shape[S_LEAN]
            var roll = (PRINT_ROLLS[i % PRINT_ROLLS.size] + sin(theta) * 6f) * (1f - 0.75f * lean) + ringRoll * (split * 0.5f + lean * 1.15f)
            var yawP = -sin(theta) * shape[S_TURN]
            if (outgoing) {
                val q = clamp01((t - i * OUT_STAGGER) / OUT_TIME)
                if (q >= 1f) continue
                val e = if (reduced) 0f else easeIn(q)
                x += slot[0] * 0.9f * e
                y += 170f * e
                z += (if (slot[2] > 0f) 0.4f else 0.9f) * slot[2] * e
                roll += 40f * e * if (i % 2 == 0) 1f else -1f
                s *= 1f + 0.15f * e
                alpha = 1f - if (reduced) q else smooth(q)
            } else if (t < DEAL_TIME && sets.size == 2) {
                val q = clamp01((t - IN_DELAY - i * IN_STAGGER) / IN_TIME)
                if (q <= 0f) continue
                if (reduced) {
                    alpha = smooth(q)
                } else {
                    val e = easeOut(q)
                    x = lensTip[0] + (x - lensTip[0]) * e
                    y = lensTip[1] + (y - lensTip[1]) * e
                    z = lensTip[2] - 30f * (1f - e) + (z - lensTip[2]) * e
                    s *= 0.25f + 0.75f * backOut(q)
                    roll -= 160f * (1f - e)
                    yawP *= e
                    alpha = clamp01(q * 4f)
                }
            }
            val depth = clamp01((z - (DEPTH - radius * 0.3f)) / (radius * 1.3f))
            val r = rotation(yawP * RAD, (min(ringTilt, 34f) * 0.5f + pitch * 0.2f) * RAD, roll * RAD)
            val unit = s / 100f
            val o = floatArrayOf(x, y, z)
            val u = turn(r, unit, 0f, 0f)
            val v = turn(r, 0f, unit, 0f)
            val normal = turn(r, 0f, 0f, -1f)
            val mat = faceMatrix(view, o, u, v) ?: continue
            prints.add(
                PrintState(
                    set, i, z, mat,
                    normal[0] * o[0] + normal[1] * o[1] + normal[2] * o[2] < 0f,
                    alpha,
                    // Leaning prints pass low under the body in sight, so they stay sharper there.
                    depth * BACK_SHADE * (1f - 0.3f * lean),
                    if (reduced) 0f else depth * BACK_BLUR * (1f - 0.6f * lean),
                ),
            )
        }
    }
    prints.sortByDescending { it.z }
    // Tumbling, a print passes behind the body once it is beyond the body's front face rather than its
    // middle, so the prints coming round the sides slip behind its edges instead of over them.
    val bodyHalf = (body[V_D] + (max(body[V_D], body[V_W] * 0.42f) - body[V_D]) * toy) / 2f * pose.s
    val bodyLine = DEPTH - bodyHalf * 0.8f * toy
    var bodyDrawn = false
    val label = cameras[if (morph < 0.5f) fromIndex else toIndex].name
    for (print in prints) {
        if (!bodyDrawn && print.z < bodyLine) {
            drawAnyCamera(canvas, p, view, pose, body, toy, true, label, flash, 0f, 0f, width, height)
            bodyDrawn = true
        }
        val caption = "${cameras[print.set].name} · ${(print.index + 1).toString().padStart(2, '0')}/${cameras[print.set].photos.size.toString().padStart(2, '0')}"
        drawPrint(canvas, p, print, m.loaded[print.set][print.index], caption, now, density)
    }
    if (!bodyDrawn) drawAnyCamera(canvas, p, view, pose, body, toy, true, label, flash, 0f, 0f, width, height)

    // A faint white flash over the whole scene when the shutter fires.
    if (flash > 0.01f) {
        fill.color = Color.White.at(flash * flash * 0.12f)
        canvas.drawRect(0f, 0f, width, height, fill)
    }

    // The name rolls letter by letter: the old one up and out, the new one up from below.
    val barY = height - m.bottom - BAR_ABOVE
    val nameY = barY - 92f
    canvas.save()
    canvas.clipRect(0f, nameY - 34f, width, nameY + 8f)
    val changing = fromIndex != toIndex && t < 1.2f
    rollName(canvas, p, cameras[fromIndex].name, width / 2f, nameY, if (changing) t else -1f, false, reduced, textColor)
    rollName(canvas, p, cameras[toIndex].name, width / 2f, nameY, if (changing) t else -1f, true, reduced, textColor)
    canvas.restore()
    val specOut = if (fromIndex != toIndex) 1f - clamp01((t - 0.05f) / 0.25f) else 0f
    val specIn = if (fromIndex != toIndex) clamp01((t - 0.35f) / 0.3f) else 1f
    if (specOut > 0.01f) drawTracked(canvas, p, cameras[fromIndex].spec, p.spec, width / 2f, nameY + 22f, 1.4f, 0.5f, textColor.at(0.5f * specOut))
    if (specIn > 0.01f) drawTracked(canvas, p, cameras[toIndex].spec, p.spec, width / 2f, nameY + 22f, 1.4f, 0.5f, textColor.at(0.5f * specIn))

    // The bar: the chosen camera in colour, the others smaller and grey; it scrolls on its own.
    val barAt = m.bar
    val pickAt = m.pick
    for (i in cameras.indices) {
        val x = width / 2f + (i - barAt) * BAR_SPACING * k
        if (x < -BAR_SPACING || x > width + BAR_SPACING) continue
        val e = clamp01(1f - abs(i - pickAt))
        canvas.saveLayer(x - 50f * k, barY - 40f * k, x + 50f * k, barY + 30f * k, p.thumbLayer(0.15f + 0.85f * e, 0.42f + 0.58f * e))
        val v = vectors[i]
        val thumb = Pose(0f, 0f, DEPTH, THUMB_WIDTH / v[V_W] * (0.82f + 0.3f * e), rotation(24f * RAD, 16f * RAD, 0f))
        drawAnyCamera(canvas, p, View3(FOCAL * k, x, barY - 6f * k), thumb, v, toy, false, cameras[i].name, 0f, x - 50f * k, barY - 40f * k, x + 50f * k, barY + 30f * k)
        canvas.restore()
        drawTracked(canvas, p, cameras[i].name, p.label, x, barY + 30f * k, 1.2f, 0.5f, textColor.at(0.3f + 0.7f * e))
    }

}

/**
 * The camera's name, letter by letter. While a change is under way (`t` its age in seconds, −1
 * for none) the old name's letters leave upwards and the new one's rise in from below, each a
 * little after the one before.
 */
private fun rollName(canvas: Canvas, p: OrbitPaints, text: String, cx: Float, y: Float, t: Float, incoming: Boolean, reduced: Boolean, ink: Color) {
    val tracking = 3f
    val paint = p.name
    var total = 0f
    for (i in text.indices) total += p.width(paint, text[i]) + if (i < text.length - 1) tracking else 0f
    var at = cx - total / 2f
    for (i in text.indices) {
        val w = p.width(paint, text[i])
        var dy = 0f
        var a = if (incoming) 1f else 0f
        if (t >= 0f) {
            if (incoming) {
                val q = clamp01((t - 0.22f - i * 0.045f) / 0.38f)
                dy = 30f * (1f - easeOut(q))
                a = q
            } else {
                val q = clamp01((t - i * 0.045f) / 0.28f)
                dy = -30f * easeIn(q)
                a = 1f - q
            }
        }
        if (reduced) dy = 0f
        if (a > 0.01f) {
            paint.color = ink.at(a)
            canvas.drawText(text[i].toString(), at, y + dy, paint)
        }
        at += w + tracking
    }
}

/** True when the user turned animations off in system settings ("Remove animations"). */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
