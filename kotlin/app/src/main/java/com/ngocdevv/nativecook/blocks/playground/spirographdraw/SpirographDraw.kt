package com.ngocdevv.nativecook.blocks.playground.spirographdraw

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// One fixed clock drives rolling, the pen, the script and every transition: ticks of 1/60 s, at
// most MAX_TICKS a frame (a 50 ms cap).
private const val TICK = 1.0 / 60
private const val MAX_TICKS = 3

// The pen samples the curve every PEN_STEP rad of rolling; a stroke is cut into chunks of CHUNK
// points, each drawn once into the ink's bitmap, so however long the drawing runs it costs one
// bitmap and the live stroke.
private const val PEN_STEP = 0.016
private const val CHUNK = 96
// A fine gel-pen line, in model units.
private const val INK_WIDTH = 1.35f
// The faint pencil preview of the whole pattern.
private const val GHOST_STEPS_PER_TURN = 150

// A release keeps the gear rolling at the finger's speed (rad/s, capped), slowed by FRICTION (1/s).
private const val MAX_SPIN = 14.0
private const val FRICTION = 1.1
private const val MIN_SPIN = 0.04
// Gesture thresholds standing in for the React Native recognisers: a pan starts after 2 dp, a tap
// is a touch lifted within 300 ms.
private const val PAN_SLOP = 2f
private const val TAP_MS = 300L
// The pen moving to another hole: a critically damped spring (stiffness, damping).
private const val HOLE_STIFFNESS = 170.0
private const val HOLE_DAMPING = 26.0
// Changing gear: the old one fades and shrinks as the new one grows in, over SWAP_TIME s.
private const val SWAP_TIME = 0.45
// Clear: the drawn sheet slides off the pad, turning, over SHEET_TIME s; under it is a fresh one.
private const val SHEET_TIME = 1.05
private const val SHEET_TURN = -9f
// The coach arrow (shown while the sheet is empty and still): it sweeps COACH_SWEEP rad round the
// ring ahead of the gear, holds and fades, every COACH_PERIOD s; it fades in/out at COACH_RATE /s.
private const val COACH_PERIOD = 2.0
private const val COACH_SWEEP = 1.2
private const val COACH_RATE = 5.0
// The preview fades once its pattern is fully traced, at GHOST_RATE /s.
private const val GHOST_RATE = 4.0
// After a touch the loop waits this long before it starts again.
private const val USER_HOLD = 4.0

// Layout (dp): the paper's side margin and the room it keeps inside round the ring; the header and
// the controls take fixed heights.
private const val MARGIN = 16f
private const val PAPER_PAD = 12f
private const val HEADER_H = 66f
private const val CONTROLS_H = 3 * 48f
private const val HINT_H = 34f

private val B = BOUNDS.toFloat()
private val Desk = Color(0xFF232428)
private val TextColor = Color(0xFFF2EFE7)
private val Muted = Color(0xFF9E9B93)
private const val PAPER = 0xFFF6F2E8.toInt()

// Cotton drawing paper: warm white with a soft mottle and fine fibres, in paper coordinates.
private const val PAPER_AGSL = """
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float3 col = float3(0.965, 0.95, 0.912);
  float m = noise(p * 0.02) * 0.6 + noise(p * 0.07 + 5.0) * 0.4;
  col *= 0.985 + 0.025 * m;
  float fibre = noise(float2(p.x * 0.9 + p.y * 0.3, p.y * 0.08 - p.x * 0.05));
  col -= 0.018 * smoothstep(0.72, 0.95, fibre);
  col += (hash(floor(p * 2.0)) - 0.5) * 0.012;
  return half4(half3(col), 1.0);
}"""

private fun easeInOut(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

private fun easeOut(t: Double) = 1 - (1 - t.coerceIn(0.0, 1.0)).pow(3)

/** Whether a clock that went from `before` to `t` passed the moment `at`. */
private fun crossed(before: Double, t: Double, at: Double) = before < at && t >= at

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Double) = floor(x + 0.5)

private fun withAlpha(color: Int, alpha: Float) = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255 + 0.5f).toInt() shl 24)

private fun rgba(r: Int, g: Int, b: Int, a: Float) = withAlpha((r shl 16) or (g shl 8) or b, a)

/** A colour mixed toward black by `amount`. */
private fun darker(color: Int, amount: Float, alpha: Float): Int {
    val k = 1 - amount
    val r = (((color shr 16) and 255) * k).toInt()
    val g = (((color shr 8) and 255) * k).toInt()
    val b = ((color and 255) * k).toInt()
    return withAlpha((r shl 16) or (g shl 8) or b, alpha)
}

private fun strokePaint(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

/**
 * The ink on the sheet: one bitmap every finished chunk of stroke is drawn straight into, and the
 * live stroke on top of it. (Keeping the chunks as paths and redrawing them each frame left them
 * blank on a hardware canvas; the bitmap is also cheaper.)
 */
private class Ink(var gear: Int, var ink: Int, private val scale: Float) {
    private var flat: Bitmap? = null
    private var flatCanvas: NativeCanvas? = null
    private var hasInk = false
    /** The stroke being drawn (flat x, y pairs, model units), not yet drawn into the bitmap. */
    val live = FloatArray(CHUNK * 2 + 2)
    var liveCount = 0
    var down = false
    private val paint = strokePaint(INK_WIDTH)
    private val chunk = Path()

    val isEmpty get() = !hasInk && liveCount <= 1

    fun liveTo(path: Path): Boolean {
        if (liveCount < 2) return false
        path.rewind()
        path.moveTo(live[0], live[1])
        for (i in 1 until liveCount) path.lineTo(live[i * 2], live[i * 2 + 1])
        return true
    }

    /** The bitmap and a canvas on it in model units, made on the first stroke. */
    private fun sheet(): NativeCanvas? {
        flatCanvas?.let { return it }
        val side = ceil(2 * B * scale).toInt()
        if (side <= 0) return null
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = NativeCanvas(bitmap)
        c.scale(scale, scale)
        c.translate(B, B)
        flat = bitmap
        flatCanvas = c
        return c
    }

    /** Draws the live stroke into the bitmap and starts the next one from its last point. */
    fun commitLive() {
        if (liveCount >= 2 && liveTo(chunk)) {
            sheet()?.let { c ->
                paint.color = Inks[ink].color
                c.drawPath(chunk, paint)
                hasInk = true
            }
        }
        if (liveCount >= 1) {
            live[0] = live[(liveCount - 1) * 2]
            live[1] = live[(liveCount - 1) * 2 + 1]
            liveCount = 1
        }
    }

    fun add(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        live[liveCount * 2] = x
        live[liveCount * 2 + 1] = y
        liveCount += 1
        if (liveCount >= CHUNK) commitLive()
    }

    fun liftPen() {
        commitLive()
        liveCount = 0
        down = false
    }

    fun wipe() {
        flat?.eraseColor(android.graphics.Color.TRANSPARENT)
        hasInk = false
        liveCount = 0
        down = false
    }

    fun draw(c: NativeCanvas, scratch: Path, rect: RectF) {
        if (hasInk) flat?.let { c.drawBitmap(it, null, rect, null) }
        if (liveTo(scratch)) {
            paint.color = Inks[ink].color
            c.drawPath(scratch, paint)
        }
    }
}

/** The gear's rolling, the pen, the script and every transition. */
private class SpiroMotion(scale: Float) {
    val ink = Ink(START_GEAR, START_INK, scale)
    var acc = 0.0
    // Rolling: the gear's angle round the ring, its own turn, and its coasting speed (rad/s).
    var phi = 0.0
    var spin = 0.0
    var lastPhi = 0.0
    var omega = 0.0
    var dragging = false
    var fingerLast = 0.0
    // Gear and swap progress (1 = settled); the pen's hole, its target and its speed; the ink.
    var gear = START_GEAR
    var gearFrom = START_GEAR
    var swap = 1.0
    var hole = START_HOLE.toDouble()
    var holeTarget = START_HOLE
    var holeSpeed = 0.0
    var inkIndex = START_INK
    // How much of the current pattern the pen has covered (the span of φ it has drawn over).
    private var spanLo = 0.0
    private var spanHi = 0.0
    private var spanSet = false
    private var complete = false
    var ghost = 1.0
    // The sheet tearing off (0…1), or -1.
    var tear = -1.0
    // The coach arrow's strength and its own clock.
    var coach = 1.0
    private var coachClock = 0.0
    // Requests from the controls, handled on the next tick.
    var pendingGear = -1
    var pendingHole = -1
    var pendingInk = -1
    var pendingClear = false
    // The script: its clock, a pause after a touch, and a scripted roll in progress.
    var clock = 0.0
    var hold = 0.0
    var looping = false
    var rollFrom = 0.0
    var rollBy = 0.0
    var rollAt = 0.0
    var rollTime = 0.0
    var reducedMotion = false
    var onShow: (Int, Int, Int) -> Unit = { _, _, _ -> }
    var onComplete: () -> Unit = {}

    val coachPhase get() = if (looping && hold <= 0) clock % COACH_PERIOD else coachClock

    /** A new pattern starts: forget how much of the old one was covered. */
    private fun resetSpan() {
        spanSet = false
        complete = false
    }

    fun changeGear(index: Int) {
        if (index !in Gears.indices) return
        if (index == gear && swap >= 1) return
        gearFrom = gear
        gear = index
        swap = if (reducedMotion) 1.0 else 0.0
        resetSpan()
        onShow(gear, holeTarget, inkIndex)
    }

    fun changeHole(index: Int) {
        if (index !in Holes.indices || index == holeTarget) return
        holeTarget = index
        if (reducedMotion) hole = index.toDouble()
        resetSpan()
        onShow(gear, holeTarget, inkIndex)
    }

    fun changeInk(index: Int) {
        if (index !in Inks.indices || index == inkIndex) return
        inkIndex = index
        onShow(gear, holeTarget, inkIndex)
    }

    private fun startTear() {
        if (tear >= 0) return
        tear = 0.0
    }

    /** The autoPlay script, from `before` to `t` seconds. */
    private fun script(before: Double, t: Double) {
        for (cue in Script) {
            if (!crossed(before, t, cue.at)) continue
            if (cue.ink >= 0) changeInk(cue.ink)
            if (cue.gear >= 0) changeGear(cue.gear)
            if (cue.hole >= 0) changeHole(cue.hole)
            if (cue.clear) startTear()
            if (cue.roll > 0) {
                rollFrom = phi
                rollBy = cue.roll * 2 * PI
                rollAt = cue.at
                rollTime = cue.time
                omega = 0.0
            }
        }
    }

    fun radius(): Double {
        val sw = easeInOut(swap)
        return gearRadius(Gears[gearFrom].teeth) * (1 - sw) + gearRadius(Gears[gear].teeth) * sw
    }

    /** One tick: requests, the script, rolling, the pen's hole and the gear swap, then the pen. */
    fun step() {
        if (pendingGear >= 0) {
            changeGear(pendingGear)
            pendingGear = -1
        }
        if (pendingHole >= 0) {
            changeHole(pendingHole)
            pendingHole = -1
        }
        if (pendingInk >= 0) {
            changeInk(pendingInk)
            pendingInk = -1
        }
        if (pendingClear) {
            startTear()
            pendingClear = false
        }

        if (!dragging) {
            if (hold > 0) {
                hold -= TICK
                if (hold <= 0) clock = 0.0
            } else if (looping) {
                val before = clock
                var t = before + TICK
                if (t >= LOOP) {
                    t -= LOOP
                    // A whole turn of either angle looks the same: keep the numbers small.
                    val turnsPhi = jsRound(phi / (2 * PI)) * 2 * PI
                    phi -= turnsPhi
                    lastPhi -= turnsPhi
                    spin -= jsRound(spin / (2 * PI)) * 2 * PI
                    resetSpan()
                }
                clock = t
                script(if (t < before) 0.0 else before, t)
                if (rollTime > 0) {
                    val k = min(1.0, (t - rollAt) / rollTime)
                    phi = rollFrom + rollBy * easeInOut(max(0.0, k))
                    if (k >= 1) rollTime = 0.0
                }
            }
            if (rollTime <= 0 && abs(omega) > 0) {
                phi += omega * TICK
                omega *= exp(-FRICTION * TICK)
                if (abs(omega) < MIN_SPIN) omega = 0.0
            }
        }

        val h = hole
        val hv = holeSpeed + (HOLE_STIFFNESS * (holeTarget - h) - HOLE_DAMPING * holeSpeed) * TICK
        holeSpeed = hv
        hole = h + hv * TICK
        if (abs(holeTarget - hole) < 0.002 && abs(hv) < 0.01) {
            hole = holeTarget.toDouble()
            holeSpeed = 0.0
        }
        if (swap < 1) swap = min(1.0, swap + TICK / SWAP_TIME)
        if (tear >= 0) {
            val w = tear + TICK / (if (reducedMotion) 0.35 else SHEET_TIME)
            if (w >= 1) {
                ink.wipe()
                tear = -1.0
                resetSpan()
            } else {
                tear = w
            }
        }

        // Roll the gear by however far `phi` moved, and let the pen trace the way it went.
        val g = gear
        val radius = radius()
        val from = lastPhi
        val to = phi
        val delta = to - from
        val rate = spinRate(radius)
        val spinFrom = spin
        spin = spinFrom + delta * rate
        lastPhi = to

        // The coach shows while the sheet is empty and nothing moves; the preview fades once traced.
        val still = !dragging && omega == 0.0 && rollTime <= 0 && tear < 0
        val coachGoal = if (still && ink.isEmpty) 1.0 else 0.0
        coach += (coachGoal - coach) * (1 - exp(-TICK * COACH_RATE))
        coachClock = (coachClock + TICK) % COACH_PERIOD
        val ghostGoal = if (complete) 0.0 else 1.0
        ghost += (ghostGoal - ghost) * (1 - exp(-TICK * GHOST_RATE))

        val settled = swap >= 1 && hole == holeTarget.toDouble() && tear < 0
        if (!settled) {
            if (ink.down) ink.liftPen()
            return
        }
        val d = holeDistance(hole, radius)
        val psi = holeAngle(hole)
        if (!ink.down || ink.gear != g || ink.ink != inkIndex) {
            if (ink.down) ink.liftPen()
            ink.gear = g
            ink.ink = inkIndex
            ink.down = true
            ink.liveCount = 0
            ink.add(penX(to, spin, radius, d, psi).toFloat(), penY(to, spin, radius, d, psi).toFloat())
            return
        }
        if (delta == 0.0) return
        val n = min(400, ceil(abs(delta) / PEN_STEP).toInt())
        for (k in 1..n) {
            val f = k.toDouble() / n
            val p = from + delta * f
            val s = spinFrom + delta * rate * f
            ink.add(penX(p, s, radius, d, psi).toFloat(), penY(p, s, radius, d, psi).toFloat())
        }
        // How much of the closed pattern the pen has now been over.
        if (!spanSet) {
            spanLo = from
            spanHi = from
            spanSet = true
        }
        spanLo = min(spanLo, to)
        spanHi = max(spanHi, to)
        val full = patternTurns(Gears[g].teeth) * 2 * PI
        if (!complete && spanHi - spanLo >= full - 0.02) {
            complete = true
            if (!looping || hold > 0) onComplete()
        }
    }

    fun touched() {
        hold = USER_HOLD
        rollTime = 0.0
    }
}

/** The ring, the gears' outlines and every paint, made once. */
private class SpiroParts {
    val ring = Path()
    val ticks = Path()
    val gears = Gears.map { Path() }

    init {
        fun outline(path: Path, pts: FloatArray) {
            path.moveTo(pts[0], pts[1])
            for (i in 1 until pts.size / 2) path.lineTo(pts[i * 2], pts[i * 2 + 1])
            path.close()
        }
        // The ring's body: between its outer rim and its inward teeth (filled even-odd).
        outline(ring, toothOutline(RING_TEETH, RING_R, -1.0))
        ring.addCircle(0f, 0f, (RING_R + RING_BODY).toFloat(), Path.Direction.CW)
        ring.fillType = Path.FillType.EVEN_ODD
        for (k in 0 until RING_TEETH) {
            val a = k.toDouble() / RING_TEETH * 2 * PI
            val r0 = RING_R + RING_BODY - if (k % 8 == 0) 11 else 7
            val r1 = RING_R + RING_BODY - 4
            ticks.moveTo((r0 * cos(a)).toFloat(), (r0 * sin(a)).toFloat())
            ticks.lineTo((r1 * cos(a)).toFloat(), (r1 * sin(a)).toFloat())
        }
        Gears.forEachIndexed { i, g ->
            val radius = gearRadius(g.teeth)
            outline(gears[i], toothOutline(g.teeth, radius, 1.0))
            // The pen holes and the hub are cut through the plastic.
            for (k in Holes.indices) {
                val d = holeDistance(k.toDouble(), radius)
                val psi = holeAngle(k.toDouble())
                gears[i].addCircle((d * cos(psi)).toFloat(), (d * sin(psi)).toFloat(), 3.3f, Path.Direction.CW)
            }
            gears[i].addCircle(0f, 0f, 5f, Path.Direction.CW)
            gears[i].fillType = Path.FillType.EVEN_ODD
        }
    }

    val paperShader: Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(PAPER_AGSL) else null
    val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PAPER
        if (paperShader != null) {
            color = 0xFF000000.toInt()
            shader = paperShader
        }
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurs = HashMap<Float, BlurMaskFilter>()
    fun blur(sigma: Float): BlurMaskFilter = blurs.getOrPut(sigma) { BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }

    fun solid(color: Int, sigma: Float = 0f): Paint = fill.apply {
        reset()
        isAntiAlias = true
        this.color = color
        maskFilter = if (sigma > 0f) blur(sigma) else null
    }

    val line = strokePaint(1f)
    fun stroke(color: Int, width: Float): Paint = line.apply {
        shader = null
        maskFilter = null
        strokeWidth = width
        this.color = color
    }

    /** Ink soaks into the paper: its layer multiplies (Android 10+), so crossing colours darken. */
    val multiply = Paint().apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.MULTIPLY }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = rgba(46, 60, 76, 0.62f)
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.DEFAULT_BOLD
    }
    val ringBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -B, -B, B, B,
            intArrayOf(rgba(226, 236, 244, 0.62f), rgba(196, 212, 226, 0.4f), rgba(214, 226, 238, 0.55f)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val ghostPath = Path()
    val arc = Path()
    val scratch = Path()
    val rect = RectF(-B, -B, B, B)
    val sheet = RectF()
}

/** The clear plastic ring with its printed ticks and size. */
private fun drawRing(c: NativeCanvas, p: SpiroParts) {
    c.save()
    c.translate(1.5f, 4f)
    c.drawPath(p.ring, p.solid(rgba(0x1a, 0x14, 0x08, 0.2f), 3.5f))
    c.restore()
    c.drawPath(p.ring, p.ringBody)
    c.drawPath(p.ring, p.stroke(rgba(58, 76, 96, 0.5f), 0.9f))
    // A bright bevel just inside the outer rim.
    c.drawCircle(0f, 0f, (RING_R + RING_BODY - 1.4).toFloat(), p.stroke(withAlpha(-1, 0.75f), 1.2f))
    c.drawPath(p.ticks, p.stroke(withAlpha(0x2e3c4c, 0.5f), 0.7f))
    val text = "$RING_TEETH"
    val w = p.label.measureText(text)
    c.drawText(text, -w / 2, (-(RING_R + RING_BODY * 0.5) + 3.5).toFloat(), p.label)
}

/** One gear at its place on the ring, turned by `spin`, faded by `alpha`, scaled by `scale`. */
private fun drawGear(c: NativeCanvas, p: SpiroParts, index: Int, m: SpiroMotion, alpha: Float, scale: Float) {
    if (alpha <= 0.01f) return
    val gear = Gears[index]
    val radius = gearRadius(gear.teeth)
    val reach = RING_R - radius
    val spin = m.spin
    val outline = p.gears[index]
    val degrees = Math.toDegrees(spin).toFloat()
    c.save()
    c.translate((reach * cos(m.phi)).toFloat(), (reach * sin(m.phi)).toFloat())
    c.scale(scale, scale)
    // A soft shadow on the paper, cast down and to the right whatever the gear's turn.
    c.save()
    c.translate(1.2f, 3f)
    c.rotate(degrees)
    c.drawPath(outline, p.solid(rgba(0x1a, 0x14, 0x08, 0.2f * alpha), 2.6f))
    c.restore()
    c.save()
    c.rotate(degrees)
    // Tinted plastic, a little brighter toward the light on the upper left.
    c.drawPath(outline, p.solid(withAlpha(gear.tint, 0.5f * alpha)))
    val lx = (-cos(spin + PI / 4) * radius).toFloat()
    val ly = (sin(spin + PI / 4) * radius).toFloat()
    p.fill.apply {
        reset()
        isAntiAlias = true
        shader = LinearGradient(lx, -ly, -lx, ly, withAlpha(-1, 0.3f * alpha), withAlpha(-1, 0f), Shader.TileMode.CLAMP)
    }
    c.drawPath(outline, p.fill)
    p.fill.shader = null
    c.drawPath(outline, p.stroke(darker(gear.tint, 0.35f, 0.85f * alpha), 0.9f))
    c.restore()
    // The pen standing in its hole: a round barrel end in its ink, lit from the upper left.
    val d = holeDistance(m.hole, radius)
    val psi = holeAngle(m.hole)
    val px = (d * cos(psi + spin)).toFloat()
    val py = (d * sin(psi + spin)).toFloat()
    val ink = Inks[m.inkIndex].color
    c.drawCircle(px + 1.4f, py + 2.4f, 5.6f, p.solid(withAlpha(0, 0.28f * alpha), 2.2f))
    p.fill.apply {
        reset()
        isAntiAlias = true
        shader = RadialGradient(
            px - 1.8f, py - 1.8f, 7f,
            intArrayOf(withAlpha(-1, 0.55f * alpha), withAlpha(ink, alpha), darker(ink, 0.45f, alpha)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawCircle(px, py, 5.6f, p.fill)
    p.fill.shader = null
    c.drawCircle(px, py, 5.6f, p.stroke(darker(ink, 0.6f, 0.9f * alpha), 0.8f))
    c.restore()
}

/** The top sheet of the pad with its ink. */
private fun drawSheet(c: NativeCanvas, p: SpiroParts, m: SpiroMotion, cx: Float, cy: Float, scale: Float) {
    c.drawRoundRect(p.sheet, 5f, 5f, p.paper)
    c.save()
    c.clipRect(p.sheet)
    c.translate(cx, cy)
    c.scale(scale, scale)
    c.saveLayer(p.rect, p.multiply)
    m.ink.draw(c, p.scratch, p.rect)
    c.restore()
    c.restore()
}

/** Where the paper sits: a square under the header, as large as the width and the room above the controls allow. */
private class SpiroLayout(width: Float, height: Float, safeTop: Float, safeBottom: Float) {
    private val roomTop = safeTop + HEADER_H
    private val roomBottom = height - safeBottom - CONTROLS_H - HINT_H - 16
    val side = max(0f, min(width - 2 * MARGIN, roomBottom - roomTop - 8))
    val x = (width - side) / 2
    val y = roomTop + max(0f, (roomBottom - roomTop - side) / 2)
    val cx = x + side / 2
    val cy = y + side / 2
    val scale = if (side > 0) (side / 2 - PAPER_PAD) / (RING_R + RING_BODY).toFloat() else 1f

    fun onPaper(px: Float, py: Float) = side > 0 && px >= x && px <= x + side && py >= y && py <= y + side
}

/** The desk, the pad, the ink, the pencil preview, the ring, the coach arrow and the gear. */
private fun drawScene(c: NativeCanvas, p: SpiroParts, m: SpiroMotion, l: SpiroLayout, width: Float, height: Float, reducedMotion: Boolean) {
    val side = l.side
    if (side <= 0) return
    // The desk: dark, a little lighter under the pad.
    p.fill.apply {
        reset()
        shader = RadialGradient(
            l.cx, l.cy, max(width, height) * 0.75f,
            intArrayOf(0xFF303136.toInt(), 0xFF232428.toInt(), 0xFF18191C.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, width, height, p.fill)
    p.fill.shader = null

    // The pad: two sheets' edges under the top one, and its shadow on the desk.
    p.sheet.set(l.x, l.y + 12, l.x + side, l.y + side + 12)
    c.drawRoundRect(p.sheet, 5f, 5f, p.solid(withAlpha(0, 0.5f), 16f))
    p.sheet.set(l.x + 1, l.y + 5, l.x + side - 1, l.y + 5 + side)
    c.drawRoundRect(p.sheet, 5f, 5f, p.solid(0xFFC9C3B6.toInt()))
    p.sheet.set(l.x + 0.5f, l.y + 2.5f, l.x + side - 0.5f, l.y + 2.5f + side)
    c.drawRoundRect(p.sheet, 5f, 5f, p.solid(0xFFE1DBCE.toInt()))
    p.sheet.set(l.x, l.y, l.x + side, l.y + side)

    val w = m.tear
    if (w < 0) {
        drawSheet(c, p, m, l.cx, l.cy, l.scale)
    } else {
        // A fresh sheet waits under the one being torn off.
        c.drawRoundRect(p.sheet, 5f, 5f, p.paper)
        if (reducedMotion) {
            c.saveLayerAlpha(null, ((1 - w) * 255).toInt())
            drawSheet(c, p, m, l.cx, l.cy, l.scale)
            c.restore()
        } else {
            val e = easeInOut(w).toFloat()
            c.save()
            c.translate(-e * (side + 90), e * 46)
            c.rotate(SHEET_TURN * e, l.cx, l.y)
            val lift = (0.28 * sin(PI * min(1.0, w * 1.6))).toFloat()
            c.save()
            c.translate(4f, 10f)
            c.drawRoundRect(p.sheet, 5f, 5f, p.solid(withAlpha(0, lift), 12f))
            c.restore()
            drawSheet(c, p, m, l.cx, l.cy, l.scale)
            c.restore()
        }
    }

    c.save()
    c.translate(l.cx, l.cy)
    c.scale(l.scale, l.scale)

    // The whole pattern the pen is on, faint in pencil: it morphs as the hole or the gear changes.
    val radius = m.radius()
    val d = holeDistance(m.hole, radius)
    val psi = holeAngle(m.hole)
    if (m.ghost > 0.01) {
        val turns = patternTurns(Gears[m.gear].teeth)
        val steps = turns * GHOST_STEPS_PER_TURN
        val rate = spinRate(radius)
        val ghost = p.ghostPath
        ghost.rewind()
        for (k in 0..steps) {
            val a = k.toDouble() / steps * turns * 2 * PI
            val x = penX(m.phi + a, m.spin + a * rate, radius, d, psi).toFloat()
            val y = penY(m.phi + a, m.spin + a * rate, radius, d, psi).toFloat()
            if (k == 0) ghost.moveTo(x, y) else ghost.lineTo(x, y)
        }
        c.drawPath(ghost, p.stroke(withAlpha(0x4a4740, (0.2 * m.ghost).toFloat()), 0.7f))
    }

    drawRing(c, p)

    // The coach: an arrow sweeping round the ring ahead of the gear, while nothing is drawn yet.
    if (m.coach > 0.01) {
        val reach = RING_R - radius
        val rho = RING_R - 15
        val cosStart = (rho * rho + reach * reach - radius * radius) / max(1.0, 2 * rho * reach)
        val start = m.phi + acos(cosStart.coerceIn(-1.0, 1.0)) + 0.14
        val local = m.coachPhase
        val grow = easeOut(local / 1.1)
        val fade = if (local > 1.6) 1 - (local - 1.6) / 0.4 else 1.0
        val sweep = max(0.02, COACH_SWEEP * grow)
        val end = start + sweep
        val arc = p.arc
        arc.rewind()
        val segments = 32
        for (i in 0..segments) {
            val a = start + sweep * i / segments
            val x = (rho * cos(a)).toFloat()
            val y = (rho * sin(a)).toFloat()
            if (i == 0) arc.moveTo(x, y) else arc.lineTo(x, y)
        }
        val tx = -sin(end)
        val ty = cos(end)
        val hx = rho * cos(end)
        val hy = rho * sin(end)
        for (s in doubleArrayOf(-1.0, 1.0)) {
            val ax = -tx * 0.86 + s * -ty * 0.5
            val ay = -ty * 0.86 + s * tx * 0.5
            arc.moveTo(hx.toFloat(), hy.toFloat())
            arc.lineTo((hx + ax * 8).toFloat(), (hy + ay * 8).toFloat())
        }
        c.drawPath(arc, p.stroke(withAlpha(0x2b2a30, (0.5 * m.coach * max(0.0, fade)).toFloat()), 2.2f))
    }

    // The gear over the ink: while it changes, the old one fades out as the new one grows in.
    val g = m.gear
    val from = m.gearFrom
    val sw = easeInOut(m.swap).toFloat()
    val swapping = sw < 1 && from != g
    if (swapping) drawGear(c, p, from, m, 1 - sw, 1 - 0.08f * sw)
    drawGear(c, p, g, m, if (swapping) sw else 1f, if (swapping) 0.92f + 0.08f * sw else 1f)
    c.restore()
}

/** Dims and shrinks a little while pressed, like the React Native Pressable. */
@Composable
private fun SpiroPressable(
    modifier: Modifier,
    label: String,
    selected: Boolean = false,
    onPress: () -> Unit,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .graphicsLayer {
                alpha = if (pressed) 0.75f else 1f
                scaleX = if (pressed) 0.97f else 1f
                scaleY = scaleX
            }
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A small gear, sized by its place in the set, for the gear buttons. */
@Composable
private fun SpiroGearGlyph(index: Int, tint: Int) {
    val path = remember(index) {
        val pts = toothOutline(8 + index * 2, 5.4 + index * 1.25, 1.0, 2.2)
        Path().apply {
            moveTo(pts[0] + 12, pts[1] + 12)
            for (i in 1 until pts.size / 2) lineTo(pts[i * 2] + 12, pts[i * 2 + 1] + 12)
            close()
        }
    }
    val body = remember(tint) { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(tint, 0.75f) } }
    val edge = remember(tint) { strokePaint(0.8f).apply { color = tint } }
    val hub = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Desk.toArgb() } }
    Canvas(Modifier.size(24.dp)) {
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            c.save()
            c.scale(density, density)
            c.drawPath(path, body)
            c.drawPath(path, edge)
            c.drawCircle(12f, 12f, 1.6f, hub)
            c.restore()
        }
    }
}

/**
 * A spirograph on a pad of drawing paper: a clear plastic ring with inward teeth and a tinted gear
 * inside it. Dragging round the ring rolls the gear and its pen draws a hypotrochoid in real ink
 * that builds up into a pattern, while the rest of the pattern shows faintly in pencil; a release
 * keeps it rolling until friction stops it. Below, the gear, the pen hole and the ink are picked
 * from three rows (a tap on the paper also moves the pen to the next hole); Clear tears the sheet off.
 *
 * @param autoPlay Draws two layered patterns, tears the sheet off and starts again.
 */
@Composable
fun SpirographDraw(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val motion = remember { SpiroMotion(density.density) }
    val parts = remember { SpiroParts() }
    var frame by remember { mutableIntStateOf(0) }
    var shownGear by remember { mutableIntStateOf(START_GEAR) }
    var shownHole by remember { mutableIntStateOf(START_HOLE) }
    var shownInk by remember { mutableIntStateOf(START_INK) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    motion.reducedMotion = reducedMotion
    motion.onShow = { g, h, i ->
        shownGear = g
        shownHole = h
        shownInk = i
    }
    motion.onComplete = {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0.0
        motion.hold = 0.0
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016 else min((now - last) / 1e9, MAX_TICKS * TICK)
                last = now
                var pending = motion.acc + max(0.0, dt)
                var ticks = 0
                while (pending >= TICK && ticks < MAX_TICKS) {
                    motion.step()
                    pending -= TICK
                    ticks += 1
                }
                motion.acc = min(pending, TICK)
                if (ticks > 0) frame += 1
            }
        }
    }

    fun userPick() {
        motion.hold = USER_HOLD
        motion.rollTime = 0.0
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Desk)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val layout = SpiroLayout(width, height, safeTop, safeBottom)

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Spirograph. Drag round the ring to roll the gear and draw; tap the paper to move the pen to the next hole."
                    onClick {
                        motion.touched()
                        motion.changeHole((motion.holeTarget + 1) % Holes.size)
                        true
                    }
                }
                .pointerInput(motion, layout.cx, layout.cy, layout.side) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val onPaper = layout.onPaper(start.x / density.density, start.y / density.density)
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        // A finger on the canvas stops a coasting gear.
                        motion.omega = 0.0
                        motion.touched()
                        var panning = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            change.consume()
                            val x = (change.position.x / density.density - layout.cx).toDouble()
                            val y = (change.position.y / density.density - layout.cy).toDouble()
                            if (!change.pressed) {
                                if (panning) {
                                    if (motion.dragging) {
                                        val v = tracker.calculateVelocity()
                                        val vx = v.x / density.density
                                        val vy = v.y / density.density
                                        val w = (x * vy - y * vx) / max(x * x + y * y, 2500.0)
                                        motion.omega = if (reducedMotion || !w.isFinite()) 0.0 else w.coerceIn(-MAX_SPIN, MAX_SPIN)
                                    }
                                } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS && onPaper) {
                                    motion.touched()
                                    motion.changeHole((motion.holeTarget + 1) % Holes.size)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                break
                            }
                            if (!panning && hypot(change.position.x - start.x, change.position.y - start.y) / density.density >= PAN_SLOP) {
                                panning = true
                                if (onPaper) {
                                    motion.dragging = true
                                    motion.fingerLast = atan2(y, x)
                                }
                            }
                            if (panning && motion.dragging) {
                                val a = atan2(y, x)
                                var d = a - motion.fingerLast
                                if (d > PI) d -= 2 * PI
                                if (d < -PI) d += 2 * PI
                                motion.fingerLast = a
                                // Right over the centre the angle is meaningless: skip it rather than spin the gear.
                                if (hypot(x, y) >= 18) motion.phi += d
                            }
                        }
                        motion.dragging = false
                        motion.hold = USER_HOLD
                    }
                },
        ) {
            if (frame < 0) return@Canvas
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density.density, density.density)
                drawScene(c, parts, motion, layout, width, height, reducedMotion)
                c.restore()
            }
        }

        // The header: the title, the gear, hole and loops, and Clear.
        val current = Gears[shownGear]
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = (safeTop + 8).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    "Spirograph",
                    style = TextStyle(color = TextColor, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
                )
                BasicText(
                    "${current.teeth}-tooth gear · hole ${shownHole + 1} · ${patternPetals(current.teeth)} loops",
                    maxLines = 1,
                    style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum"),
                )
            }
            Spacer(Modifier.width(12.dp))
            SpiroPressable(
                Modifier
                    .height(38.dp)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(19.dp))
                    .border(Dp.Hairline, Color.White.copy(alpha = 0.18f), RoundedCornerShape(19.dp))
                    .padding(horizontal = 18.dp),
                label = "Clear the sheet",
                onPress = {
                    userPick()
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    motion.pendingClear = true
                },
            ) {
                BasicText("Clear", style = TextStyle(color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
            }
        }

        // The controls: the gear, the pen hole and the ink.
        val inkColor = Color(Inks[shownInk].color)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = (safeBottom + 10).dp),
        ) {
            BasicText(
                "Drag around the ring to draw · Tap the paper for the next hole",
                Modifier.fillMaxWidth().height(HINT_H.dp),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
                style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, fontFamily = FontFamily.SansSerif),
            )
            SpiroRow("GEAR") {
                Gears.forEachIndexed { i, g ->
                    val selected = i == shownGear
                    SpiroPressable(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(Color.White.copy(alpha = if (selected) 0.15f else 0.06f), RoundedCornerShape(10.dp))
                            .border(1.dp, if (selected) Color.White.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(10.dp)),
                        label = "${g.teeth} tooth gear",
                        selected = selected,
                        onPress = {
                            userPick()
                            if (i != motion.gear) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            motion.pendingGear = i
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SpiroGearGlyph(i, g.tint)
                            BasicText(
                                "${g.teeth}",
                                style = TextStyle(
                                    color = if (selected) Color.White else Muted,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    fontFeatureSettings = "tnum",
                                ),
                            )
                        }
                    }
                }
            }
            SpiroRow("HOLE") {
                Holes.indices.forEach { i ->
                    val selected = i == shownHole
                    SpiroPressable(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(if (selected) inkColor else Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .border(1.dp, if (selected) Color.White.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(10.dp)),
                        label = "Pen hole ${i + 1}",
                        selected = selected,
                        onPress = {
                            userPick()
                            if (i != motion.holeTarget) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            motion.pendingHole = i
                        },
                    ) {
                        BasicText(
                            "${i + 1}",
                            style = TextStyle(
                                color = if (selected) Color.White else Muted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                fontFeatureSettings = "tnum",
                            ),
                        )
                    }
                }
            }
            SpiroRow("INK") {
                Inks.forEachIndexed { i, k ->
                    val selected = i == shownInk
                    SpiroPressable(
                        Modifier.weight(1f).height(40.dp),
                        label = "${k.name} ink",
                        selected = selected,
                        onPress = {
                            userPick()
                            if (i != motion.inkIndex) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            motion.pendingInk = i
                        },
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .border(2.dp, if (selected) TextColor else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .background(Color(k.color), CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One row of the controls: a label, then its options sharing the width. */
@Composable
private fun SpiroRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .drawBehind { drawLine(Color.White.copy(alpha = 0.1f), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1f) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            label,
            Modifier.width(56.dp),
            style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, fontFamily = FontFamily.SansSerif),
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
