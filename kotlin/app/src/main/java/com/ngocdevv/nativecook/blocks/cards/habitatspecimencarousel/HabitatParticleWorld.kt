package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One particle world for the habitat, in two layers: behind the creatures (depth < 0) and in front
 * of them (gpu/HabitatParticleShader.ts). The reference steps it with a compute pass and draws it as
 * instanced quads; on Android the same simulation runs on the CPU (520 particles are cheap) and each
 * live particle is one quad drawn with MOTE_AGSL, in the same two layers.
 *
 * - Ambient motes belong to whichever habitat they float in: embers in fire, bubbles in water,
 *   pollen in grass. When the habitat front passes one, it morphs into the new habitat's mote
 *   (look, size and physics) instead of fading out, so the world is continuous across a swipe.
 * - A finger stirs everything it passes (a wake that follows its velocity, a swirl, a push), a tap
 *   sends a ring of air out, a landing kicks a gust along the floor, and tilting the phone turns
 *   which way is up for everything buoyant.
 * - A signature move spawns its own particles from the posed body at the moment of their birth:
 *   embers off the flame's tip, spray off the cannons that lands and splashes, leaves and pollen
 *   off the bulb. Leaves flutter down, settle flat, can be blown up again by a finger, then fade.
 *
 * Units are screen points; the canvas covers the screen from its top to below the stage.
 */
internal const val PARTICLE_AMBIENT = 120
internal const val PARTICLE_COUNT = 520

// Signature slots, counted from AMBIENT.
private const val EMBERS = 96
private const val DROPS = 256
private const val LEAF_SLOTS = 280
private const val POLLEN = 400
private const val MOVE = 1.8f
// A leaf lives until it has settled and faded; this is only its upper bound.
private const val LEAF_SPAN = 9f
private const val TAU = 6.28318530718f
private const val PI_F = 3.14159265f

// Floats per particle (Particle in the WGSL).
private const val STRIDE = 18
private const val POS_X = 0
private const val POS_Y = 1
private const val VEL_X = 2
private const val VEL_Y = 3
private const val LIFE = 4
private const val SPAN = 5
private const val KIND = 6
private const val LOOK = 7
private const val PREV = 8
private const val MORPH = 9
private const val DEPTH = 10
private const val SEED = 11
private const val ANGLE = 12
private const val TURN = 13
private const val PHASE = 14
private const val GROUND = 15
private const val HEAT = 16
private const val SIZE = 17

/** The screen as the particle layers need it, in points. */
internal class HabitatLayout(
    val width: Float,
    val height: Float,
    val heroTop: Float,
    val heroHeight: Float,
    val screenHeight: Float,
    val scale: Float,
    val size: Float,
    val left: Float,
    val top: Float,
    val pad: Float,
)

private class Emitter(val a: FloatArray, val b: FloatArray, val aimA: FloatArray, val aimB: FloatArray)

/** Where each move leaves the posed body (source-art pixels) and where it aims; see HabitatSignature. */
private val EMITTERS = listOf(
    Emitter(floatArrayOf(452f, 402f), floatArrayOf(452f, 402f), floatArrayOf(0f, -1f), floatArrayOf(0f, -1f)),
    Emitter(floatArrayOf(99f, 154f), floatArrayOf(409f, 196f), floatArrayOf(-0.67f, -0.74f), floatArrayOf(1f, -0.06f)),
    Emitter(floatArrayOf(377f, 152f), floatArrayOf(352f, 215f), floatArrayOf(0f, -1f), floatArrayOf(0f, -1f)),
)
private const val ART = 512

internal class HabitatParticleWorld {
    private val p = FloatArray(PARTICLE_COUNT * STRIDE)

    // The frame uniform of the reference (Frame in the WGSL), by name.
    private var canvasW = 0f
    private var canvasH = 0f
    private var dt = 0f
    private var clockT = 0f
    private var clockPosition = 0f
    private var clockFrame = 0f
    private var clockPhase = 0f
    private var stageH = 0f
    private var stageTop = 0f
    private var stageBottom = 0f
    private var stageDrift = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var touchVX = 0f
    private var touchVY = 0f
    private var pressContact = 0f
    private var pressPet = 0f
    private var pressReach = 0f
    private var pressLive = 0f
    private var tapX = 0f
    private var tapY = 0f
    private var tapSince = -1f
    private var landX = 0f
    private var landY = 0f
    private var landSince = -1f
    private var downX = 0f
    private var downY = 1f
    private var fxLast = -1f
    private var fxNow = -1f
    private var fxPerformer = 0f
    private var fxUnit = 1f
    private val emitA = FloatArray(4)
    private val emitB = FloatArray(4)
    private var tipX = 0f
    private var tipY = 0f
    private var tipHalf = 0f
    private var tipFloor = 0f

    private var contact = 0f
    private var frameNumber = 0
    private var lastFx = -1f

    /** Every particle starts dead (span 0), so the first step spawns the ambient ones where they belong. */
    fun reset() {
        p.fill(0f)
        contact = 0f
        lastFx = -1f
    }

    // ---- Random (PCG), the same hash as the WGSL ----

    private fun pcg(v: Int): Int {
        val s = v * 747796405 + (2891336453L.toInt())
        val w = ((s ushr ((s ushr 28) + 4)) xor s) * 277803737
        return (w ushr 22) xor w
    }

    private fun rand(a: Int, b: Int): Float {
        val x = pcg(a * 1973 + pcg(b + 17))
        return (x.toLong() and 0xFFFFFFFFL).toFloat() / 4294967295.0f
    }

    /** Per-slot values that never change, for a move's schedule. */
    private fun fixed(slot: Int, k: Int): Float = rand(slot + 7919, k * 104729)

    /** Fresh values for a respawn. */
    private fun fresh(i: Int, k: Int): Float = rand(i, k + clockFrame.toInt() * 31)

    // ---- The front ----

    private fun seam(y: Float, f: Float, phase: Float, arriving: Float): Float {
        val bow = sin(PI_F * f)
        var seam = 1.08f - 1.16f * f + bow * (0.040f * sin(y * 5.5f + phase) + 0.018f * sin(y * 14.0f - phase * 2.0f + 1.3f))
        if (arriving > 1.5f) seam -= 0.011f * abs(sin(y * 46.0f + phase)) * bow
        return seam
    }

    private fun wrap3(x: Float): Float = x - 3.0f * floor(x / 3.0f)

    /** The habitat at a point: the leaving one on its side of the front, the arriving one beyond. */
    private fun elementAt(x: Float, y: Float): Float {
        val q = wrap3(clockPosition)
        val leaving = floor(q)
        val f = q - leaving
        if (f < 0.0005f) return leaving
        val arriving = wrap3(leaving + 1.0f)
        val s = seam(y / stageH, f, clockPhase, arriving)
        return if (x < s * canvasW) leaving else arriving
    }

    /** Which side of the front a world is on: side (1 shown, 0 wiped) and its parallax drift. */
    private fun worldSide(world: Float, x: Float, y: Float, out: FloatArray) {
        val q = wrap3(clockPosition)
        val leaving = floor(q)
        val f = q - leaving
        if (f < 0.0005f) { out[0] = if (abs(world - leaving) < 0.5f) 1f else 0f; out[1] = 0f; return }
        val arriving = wrap3(leaving + 1.0f)
        val edge = x - seam(y / stageH, f, clockPhase, arriving) * canvasW
        val side = smoothstep(-1.0f, 1.0f, edge)
        if (abs(world - leaving) < 0.5f) { out[0] = 1.0f - side; out[1] = -0.5f * f * f * stageDrift; return }
        if (abs(world - arriving) < 0.5f) { out[0] = side; out[1] = (1.0f - f) * (1.0f - f) * stageDrift; return }
        out[0] = 0f; out[1] = 0f
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = max(0f, min(1f, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    // ---- Forces ----

    /** A slow divergence-free current: the curl of a moving stream function. */
    private fun stream(x: Float, y: Float, t: Float): Float =
        38.0f * sin(x * 0.031f + t * 0.9f) * cos(y * 0.027f - t * 0.7f) + 17.0f * sin(x * 0.067f - t * 1.3f + y * 0.041f)

    private val curlOut = FloatArray(2)

    private fun curl(x: Float, y: Float, t: Float) {
        val e = 1.5f
        val a = stream(x, y + e, t) - stream(x, y - e, t)
        val b = stream(x + e, y, t) - stream(x - e, y, t)
        curlOut[0] = a / (2.0f * e)
        curlOut[1] = -b / (2.0f * e)
    }

    /** The finger, the tap ring and the landing gust, as an acceleration (x, y), plus how close the finger is (z). */
    private val stirOut = FloatArray(3)

    private fun stir(i: Int) {
        val o = i * STRIDE
        var accX = 0f
        var accY = 0f
        val reach = pressReach
        val dx = p[o + POS_X] - touchX
        val dy = p[o + POS_Y] - touchY
        val r2 = dx * dx + dy * dy
        val near = exp(-r2 / (reach * reach)) * pressContact
        val dist = sqrt(r2) + 0.001f
        val awayX = dx / dist
        val awayY = dy / dist
        // A wake: drawn along at the finger's velocity; a swirl around it; a soft push out of its way.
        accX += (touchVX - p[o + VEL_X]) * near * 7.0f
        accY += (touchVY - p[o + VEL_Y]) * near * 7.0f
        val speed = min(sqrt(touchVX * touchVX + touchVY * touchVY), 900.0f)
        accX += -awayY * speed * near * 1.6f
        accY += awayX * speed * near * 1.6f
        accX += awayX * 260.0f * near
        accY += awayY * 260.0f * near
        // Petting gathers the motes into a slow orbit round the finger.
        val held = pressPet * exp(-r2 / (reach * reach * 4.0f))
        val orbit = smoothstep(0.0f, reach, dist)
        accX += (-awayY * 70.0f - awayX * 50.0f * orbit) * held
        accY += (awayX * 70.0f - awayY * 50.0f * orbit) * held
        if (tapSince >= 0.0f && tapSince < 0.6f) {
            val ddx = p[o + POS_X] - tapX
            val ddy = p[o + POS_Y] - tapY
            val l = sqrt(ddx * ddx + ddy * ddy) + 0.001f
            val x = (l - tapSince * 520.0f) / 26.0f
            val ring = exp(-x * x) * (1.0f - tapSince / 0.6f)
            accX += ddx / l * 2400.0f * ring
            accY += ddy / l * 2400.0f * ring
        }
        if (landSince >= 0.0f && landSince < 0.5f) {
            val ddx = p[o + POS_X] - landX
            val ddy = p[o + POS_Y] - landY
            val l = sqrt(ddx * ddx + ddy * ddy) + 0.001f
            val x = (l - landSince * 380.0f) / 30.0f
            val ring = exp(-x * x) * (1.0f - landSince / 0.5f) * exp(-abs(ddy) / 60.0f)
            accX += (ddx / l) * 1500.0f * ring
            accY += (ddy / l + -0.6f) * 1500.0f * ring
        }
        stirOut[0] = accX; stirOut[1] = accY; stirOut[2] = near + held * 0.5f
    }

    // ---- Ambient motes ----

    private fun spawnAmbient(i: Int) {
        val o = i * STRIDE
        var px = fresh(i, 1) * canvasW
        var py = stageTop + (stageBottom - stageTop) * fresh(i, 2)
        val element = elementAt(px, py)
        val top = stageTop
        val bottom = stageBottom
        if (element < 0.5f) py = bottom - fresh(i, 3) * (bottom - top) * 0.35f
        else if (element < 1.5f) py = bottom - fresh(i, 3) * (bottom - top) * 0.3f
        p[o + POS_X] = px
        p[o + POS_Y] = py
        p[o + VEL_X] = 0f
        p[o + VEL_Y] = if (element < 0.5f) -40.0f else 0.0f
        p[o + LIFE] = 0f
        p[o + SPAN] = if (element < 0.5f) 1.3f + 1.6f * fresh(i, 4) else if (element < 1.5f) 3.0f + 3.0f * fresh(i, 4) else 4.0f + 4.0f * fresh(i, 4)
        p[o + KIND] = 0f
        p[o + LOOK] = element
        p[o + PREV] = element
        p[o + MORPH] = 1.0f
        p[o + DEPTH] = fresh(i, 5) * 2.0f - 1.0f
        p[o + SEED] = fresh(i, 6)
        p[o + ANGLE] = 0f; p[o + TURN] = 0f
        p[o + PHASE] = fresh(i, 7) * TAU
        p[o + GROUND] = 0f; p[o + HEAT] = 0f; p[o + SIZE] = 0f
    }

    private fun ambientStep(i: Int) {
        val o = i * STRIDE
        val out = p[o + POS_X] < -30.0f || p[o + POS_X] > canvasW + 30.0f || p[o + POS_Y] < stageTop - 90.0f || p[o + POS_Y] > stageBottom + 30.0f
        if (p[o + SPAN] <= 0.0f || p[o + LIFE] > p[o + SPAN] || out) { spawnAmbient(i); return }
        // The front passing over a mote morphs it into the new habitat's mote.
        val element = elementAt(p[o + POS_X], p[o + POS_Y])
        if (abs(element - p[o + LOOK]) > 0.5f) {
            p[o + PREV] = if (p[o + MORPH] < 0.5f) p[o + PREV] else p[o + LOOK]
            p[o + LOOK] = element
            p[o + MORPH] = 0.0f
            p[o + SPAN] = max(p[o + SPAN], p[o + LIFE] + (if (element < 0.5f) 1.6f else 3.0f))
        }
        p[o + MORPH] = min(1.0f, p[o + MORPH] + dt / 0.45f)
        val upX = -downX
        val upY = -downY
        val t = clockT
        var accX = 0f
        var accY = 0f
        var drag = 0.0f
        for (k in 0 until 2) {
            val e = if (k == 1) p[o + LOOK] else p[o + PREV]
            val w = if (k == 1) p[o + MORPH] else 1.0f - p[o + MORPH]
            if (e < 0.5f) {
                curl(p[o + POS_X], p[o + POS_Y], t)
                accX += (upX * 120.0f + curlOut[0] * 2.2f) * w
                accY += (upY * 120.0f + curlOut[1] * 2.2f) * w
                drag += 1.4f * w
            } else if (e < 1.5f) {
                accX += (upX * 95.0f + sin(t * 4.5f + p[o + SEED] * 20.0f) * 38.0f) * w
                accY += (upY * 95.0f) * w
                drag += 2.4f * w
            } else {
                curl(p[o + POS_X], p[o + POS_Y], t * 0.6f)
                accX += (34.0f + upX * 6.0f + curlOut[0] * 0.8f) * w
                accY += (sin(t * 1.7f + p[o + SEED] * 9.0f) * 14.0f + upY * 6.0f + curlOut[1] * 0.8f) * w
                drag += 1.7f * w
            }
        }
        stir(i)
        val depth = 0.7f + 0.3f * (p[o + DEPTH] * 0.5f + 0.5f)
        p[o + VEL_X] += (accX + stirOut[0]) * dt * depth
        p[o + VEL_Y] += (accY + stirOut[1]) * dt * depth
        val damp = exp(-drag * dt)
        p[o + VEL_X] *= damp
        p[o + VEL_Y] *= damp
        p[o + POS_X] += p[o + VEL_X] * dt
        p[o + POS_Y] += p[o + VEL_Y] * dt
        p[o + HEAT] = max(0.0f, p[o + HEAT] - dt * 1.8f) + stirOut[2] * dt * 6.0f
        p[o + LIFE] += dt
    }

    // ---- A move's particles ----

    /** A signature slot's birth time on the effect clock (x), and the habitat it belongs to (y). */
    private val birthOut = FloatArray(2)

    private fun birth(s: Int) {
        if (s < EMBERS) { birthOut[0] = (0.12f + 0.52f * fixed(s, 1)) * MOVE; birthOut[1] = 0f; return }
        if (s < DROPS) { birthOut[0] = (0.15f + 0.40f * fixed(s, 1)) * MOVE + 0.012f + 0.09f * fixed(s, 2); birthOut[1] = 1f; return }
        if (s < LEAF_SLOTS) { birthOut[0] = 0.56f + 0.022f * (s - DROPS) + 0.02f * fixed(s, 1); birthOut[1] = 2f; return }
        birthOut[0] = (0.36f + 0.30f * fixed(s, 1)) * MOVE; birthOut[1] = 2f
    }

    private fun spawnSignature(i: Int, s: Int, world: Float) {
        val o = i * STRIDE
        val u = fxUnit
        p[o + LIFE] = 0f; p[o + LOOK] = world; p[o + PREV] = world; p[o + MORPH] = 1f; p[o + SEED] = fixed(s, 3)
        p[o + ANGLE] = 0f; p[o + TURN] = 0f; p[o + PHASE] = fixed(s, 4) * TAU; p[o + GROUND] = 0f; p[o + HEAT] = 0f
        p[o + DEPTH] = fixed(s, 5) * 2.0f - 1.0f
        if (s < EMBERS) {
            p[o + KIND] = 1.0f
            p[o + POS_X] = tipX + (fixed(s, 6) - 0.5f) * tipHalf
            p[o + POS_Y] = tipY
            p[o + VEL_X] = (fixed(s, 7) - 0.62f) * 95.0f * u
            p[o + VEL_Y] = -(110.0f + 150.0f * fixed(s, 8)) * u
            p[o + SPAN] = 0.5f + 0.4f * fixed(s, 9)
            p[o + SIZE] = (1.1f + 1.5f * fixed(s, 10)) * u
            p[o + DEPTH] = abs(p[o + DEPTH])
        } else if (s < DROPS) {
            // Torn off the jet near the muzzle: part of its speed, sideways spread, then gravity.
            p[o + KIND] = 2.0f
            val fromB = (s and 1) == 1
            val emit = if (fromB) emitB else emitA
            val len = sqrt(emit[2] * emit[2] + emit[3] * emit[3])
            val aimX = emit[2] / len
            val aimY = emit[3] / len
            val speed = 640.0f * u
            val shed = 0.012f + 0.09f * fixed(s, 2)
            val g = 1500.0f * u
            val jetX = aimX * speed
            val jetY = aimY * speed + g * shed
            val spread = (fixed(s, 6) - 0.5f) * 1.2f
            val cs = cos(spread)
            val sn = sin(spread)
            p[o + POS_X] = emit[0] + aimX * speed * shed
            p[o + POS_Y] = emit[1] + aimY * speed * shed + 0.5f * g * shed * shed
            val keep = 0.06f + 0.34f * fixed(s, 7)
            p[o + VEL_X] = (jetX * cs - jetY * sn) * keep
            p[o + VEL_Y] = (jetX * sn + jetY * cs) * keep
            p[o + SPAN] = 3.0f
            p[o + SIZE] = (1.8f + 2.4f * fixed(s, 8)) * u
        } else if (s < LEAF_SLOTS) {
            p[o + KIND] = 4.0f
            val k = (s - DROPS).toFloat()
            val launch = -1.5708f + ((k + 0.5f) / 24.0f - 0.5f) * 2.6f + (fixed(s, 6) - 0.5f) * 0.24f
            p[o + POS_X] = emitA[0]
            p[o + POS_Y] = emitA[1]
            val speed = (220.0f + 90.0f * fixed(s, 7)) * u
            p[o + VEL_X] = cos(launch) * speed
            p[o + VEL_Y] = sin(launch) * speed
            p[o + SPAN] = LEAF_SPAN
            p[o + SIZE] = u
            p[o + ANGLE] = fixed(s, 8) * TAU
            p[o + TURN] = (fixed(s, 9) - 0.5f) * 24.0f
        } else {
            p[o + KIND] = 5.0f
            val angle = -1.5708f + (fixed(s, 6) - 0.5f) * 2.5f
            p[o + POS_X] = emitA[0]
            p[o + POS_Y] = emitA[1]
            val speed = (120.0f + 220.0f * fixed(s, 7)) * u
            p[o + VEL_X] = cos(angle) * speed
            p[o + VEL_Y] = sin(angle) * speed
            p[o + SPAN] = 1.45f
            p[o + SIZE] = (1.3f + 1.8f * fixed(s, 8)) * u
        }
    }

    /** Where a particle meets the floor: in front of the feet or, for the far ones, behind them. */
    private fun floorAt(o: Int): Float {
        val u = fxUnit
        val depth = p[o + DEPTH]
        return tipFloor + (if (depth >= 0.0f) 3.0f + 18.0f * depth else -(4.0f + 14.0f * abs(depth))) * u
    }

    private fun signatureStep(i: Int) {
        val o = i * STRIDE
        val s = i - PARTICLE_AMBIENT
        birth(s)
        if (abs(fxPerformer - birthOut[1]) < 0.5f && fxLast < birthOut[0] && birthOut[0] <= fxNow) { spawnSignature(i, s, birthOut[1]); return }
        if (p[o + SPAN] <= 0.0f || p[o + LIFE] > p[o + SPAN]) { p[o + SPAN] = 0.0f; return }
        val u = fxUnit
        stir(i)
        val stX = stirOut[0]
        val stY = stirOut[1]
        val stZ = stirOut[2]
        p[o + LIFE] += dt
        val kind = p[o + KIND]
        if (kind < 1.5f) {
            // Embers rise on the flame's buoyancy, curl and burn out.
            curl(p[o + POS_X], p[o + POS_Y], clockT)
            p[o + VEL_X] += (-downX * 120.0f * u + curlOut[0] * 1.5f + stX) * dt
            p[o + VEL_Y] += (-downY * 120.0f * u + curlOut[1] * 1.5f + stY) * dt
            val damp = exp(-0.6f * dt)
            p[o + VEL_X] *= damp
            p[o + VEL_Y] *= damp
            p[o + POS_X] += p[o + VEL_X] * dt
            p[o + POS_Y] += p[o + VEL_Y] * dt
            p[o + HEAT] = stZ
        } else if (kind < 2.5f) {
            p[o + VEL_X] += (downX * 1500.0f * u + stX * 0.6f) * dt
            p[o + VEL_Y] += (downY * 1500.0f * u + stY * 0.6f) * dt
            p[o + POS_X] += p[o + VEL_X] * dt
            p[o + POS_Y] += p[o + VEL_Y] * dt
            val floorY = floorAt(o)
            if (p[o + POS_Y] >= floorY) { p[o + POS_Y] = floorY; p[o + KIND] = 3.0f; p[o + LIFE] = 0.0f; p[o + SPAN] = 0.34f; p[o + VEL_X] = 0f; p[o + VEL_Y] = 0f }
            if (p[o + POS_X] < -40.0f || p[o + POS_X] > canvasW + 40.0f) p[o + SPAN] = 0.0f
        } else if (kind < 3.5f) {
            // A splash ring spreads on the floor and fades.
        } else if (kind < 4.5f) {
            val floorY = floorAt(o)
            if (p[o + GROUND] > 0.0f) {
                p[o + GROUND] += dt
                // A finger sweeping through can lift a settled leaf again.
                if (stZ > 0.35f) {
                    p[o + GROUND] = 0.0f
                    val kickX = touchVX * 0.35f
                    val kickY = touchVY * 0.35f + -160.0f * u
                    val scale = min(1.0f, 420.0f * u / max(sqrt(kickX * kickX + kickY * kickY), 1.0f))
                    p[o + VEL_X] = kickX * scale
                    p[o + VEL_Y] = kickY * scale
                    p[o + LIFE] = 0.21f
                }
                if (p[o + GROUND] > 1.05f) p[o + SPAN] = 0.0f
            } else {
                // Drag pulls the burst towards a steady fall (under a metre a second) on the breeze.
                val drag = 2.3f + 0.8f * p[o + SEED]
                val fall = (210.0f + 70.0f * fixed(s, 11)) * u
                val goalX = downX * fall + (14.0f + 14.0f * fixed(s, 12)) * u
                val goalY = downY * fall
                p[o + VEL_X] += ((goalX - p[o + VEL_X]) * drag + stX) * dt
                p[o + VEL_Y] += ((goalY - p[o + VEL_Y]) * drag + stY) * dt
                p[o + POS_X] += p[o + VEL_X] * dt
                p[o + POS_Y] += p[o + VEL_Y] * dt
                p[o + TURN] *= exp(-drag * dt)
                p[o + ANGLE] += p[o + TURN] * dt
                p[o + PHASE] += TAU / (0.85f + 0.45f * fixed(s, 13)) * dt
                if (p[o + POS_Y] >= floorY && p[o + LIFE] > 0.2f) { p[o + POS_Y] = floorY; p[o + GROUND] = 0.001f; p[o + VEL_X] = 0f; p[o + VEL_Y] = 0f }
            }
        } else {
            p[o + VEL_X] += (26.0f * u + stX) * dt
            p[o + VEL_Y] += (10.0f * u + stY) * dt
            val damp = exp(-2.6f * dt)
            p[o + VEL_X] *= damp
            p[o + VEL_Y] *= damp
            p[o + POS_X] += p[o + VEL_X] * dt
            p[o + POS_Y] += p[o + VEL_Y] * dt
        }
    }

    // ---- One frame ----

    /** Steps the world once: `dtMs` since the last frame, `nowMs` the frame clock. */
    fun step(motion: HabitatMotion, layout: HabitatLayout, dtMs: Float, nowMs: Double) {
        val width = layout.width
        val unit = layout.size / 344f
        val k = layout.size / ART
        val heroTop = layout.heroTop
        val scale = layout.scale
        val dtSeconds = min(dtMs, 50f) / 1000f
        val t = motion.time
        val index = motion.powerIndex
        val fx = motion.fx.value
        contact += ((if (motion.touch[4] > 0f) 1f else 0f) - contact) * min(1f, dtSeconds * 14f)
        frameNumber = (frameNumber + 1) % 1000000
        canvasW = width; canvasH = layout.height; dt = dtSeconds
        clockT = t * LOOP; clockPosition = motion.position; clockFrame = frameNumber.toFloat(); clockPhase = (PI * 2).toFloat() * (t % 1f)
        stageH = layout.screenHeight; stageTop = heroTop - 22 * scale; stageBottom = heroTop + layout.heroHeight + 8 * scale
        stageDrift = EXCHANGE_DRIFT * width
        touchX = motion.touch[0]; touchY = motion.touch[1] + heroTop; touchVX = motion.touch[2]; touchVY = motion.touch[3]
        pressContact = contact; pressPet = motion.pet.value; pressReach = 70 * scale; pressLive = 1f
        tapX = motion.tapAt[0].toFloat(); tapY = motion.tapAt[1].toFloat() + heroTop
        tapSince = if (motion.tapAt[2] > 0) ((nowMs - motion.tapAt[2]) / 1000.0).toFloat() else -1f
        val landing = motion.landing.value
        val landed = motion.landingIndex
        landX = width / 2; landY = heroTop + layout.top + RIGS[landed].feet * k
        landSince = if (landing < 1) landing * LAND_MS / 1000f else -1f
        // Down follows the phone's tilt, so everything buoyant rises away from the ground it feels.
        val gx = motion.tilt[0] * 0.8f
        val length = sqrt(gx * gx + 1f)
        downX = gx / length; downY = 1f / length
        fxLast = if (lastFx > fx) -1f else lastFx; fxNow = fx; fxPerformer = index.toFloat(); fxUnit = unit
        lastFx = fx
        // Particles are born from the posed body at the moment they leave it.
        val emitter = EMITTERS[index]
        val state = creaturePose(index, motion)
        val body = BodyFrame(
            index = index, size = layout.size, pad = layout.pad, left = layout.left, top = layout.top, heroTop = heroTop, width = width,
            screenHeight = layout.screenHeight, scale = scale, exchange = state.exchange, driftPx = EXCHANGE_DRIFT * width, pose = state.pose,
        )
        val a = bodyPoint(body, emitter.a[0], emitter.a[1])
        val b = bodyPoint(body, emitter.b[0], emitter.b[1])
        val ta = bodyPoint(body, emitter.a[0] + emitter.aimA[0] * 24, emitter.a[1] + emitter.aimA[1] * 24)
        val tb = bodyPoint(body, emitter.b[0] + emitter.aimB[0] * 24, emitter.b[1] + emitter.aimB[1] * 24)
        emitA[0] = a[0]; emitA[1] = a[1]; emitA[2] = ta[0] - a[0]; emitA[3] = ta[1] - a[1]
        emitB[0] = b[0]; emitB[1] = b[1]; emitB[2] = tb[0] - b[0]; emitB[3] = tb[1] - b[1]
        val flame = RIGS[0].flame
        val tip = if (flame != null && index == 0) {
            bodyPoint(body, flame.base[0], flame.base[1] - flame.height * (1 + 1.25f * state.pose.flameGrow) * 0.95f)
        } else a
        tipX = tip[0]; tipY = tip[1]; tipHalf = 15 * unit * (1 + 0.6f * state.pose.flameGrow); tipFloor = heroTop + layout.top + RIGS[index].feet * k

        val live = pressLive > 0.5f
        dt = dtSeconds * pressLive
        for (i in 0 until PARTICLE_COUNT) {
            if (i < PARTICLE_AMBIENT) ambientStep(i)
            else if (live) signatureStep(i)
        }
    }

    // ---- Drawing ----

    @RequiresApi(33)
    inner class Painter {
        private val shader = RuntimeShader(MOTE_AGSL)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val side = FloatArray(2)

        /**
         * Draws one layer (0 behind the creatures, 1 in front) onto a canvas whose unit is the point.
         * Each particle's quad is built as the reference's vertex stage builds it.
         */
        fun draw(canvas: Canvas, layer: Int) {
            shader.setFloatUniform("canvas", canvasW, stageH, stageTop, stageBottom)
            shader.setFloatUniform("clock", clockPosition, clockPhase, stageDrift, 0f)
            paint.shader = shader
            for (i in 0 until PARTICLE_COUNT) {
                val o = i * STRIDE
                val span = p[o + SPAN]
                val back = p[o + DEPTH] < 0.0f
                if (span <= 0.0f || back != (layer == 0)) continue
                val lf = p[o + LIFE] / span
                val depthScale = if (back) 0.86f else 1.0f
                var cx = p[o + POS_X]
                val cy0 = p[o + POS_Y]
                var cy = cy0
                var half = 1.0f
                var stretch = 1.0f
                var ax = 1.0f
                var ay = 0.0f
                var radius = 1.0f
                var roll = 0.0f
                val vx = p[o + VEL_X]
                val vy = p[o + VEL_Y]
                val speed = sqrt(vx * vx + vy * vy)
                if (speed > 1.0f) { ax = vx / speed; ay = vy / speed }
                val kind = p[o + KIND]
                if (kind < 0.5f) {
                    radius = (ambientSize(p[o + PREV], p[o + SEED]) * (1 - p[o + MORPH]) + ambientSize(p[o + LOOK], p[o + SEED]) * p[o + MORPH]) * depthScale * (1.0f + p[o + HEAT] * 0.4f)
                    // Fast embers streak along their path; bubbles and pollen stay round.
                    val fiery = (if (p[o + PREV] < 0.5f) 1.0f - p[o + MORPH] else 0.0f) + (if (p[o + LOOK] < 0.5f) p[o + MORPH] else 0.0f)
                    stretch = 1.0f + min(speed, 400.0f) * 0.004f * fiery
                    half = radius * 2.4f
                } else if (kind < 1.5f || kind > 4.5f) {
                    radius = p[o + SIZE] * depthScale
                    stretch = 1.0f + min(speed, 400.0f) * 0.003f
                    half = radius * 6.0f
                } else if (kind < 2.5f) {
                    radius = p[o + SIZE]
                    stretch = 1.0f + min(speed, 900.0f) * 0.0012f
                    half = radius * 3.0f
                } else if (kind < 3.5f) {
                    radius = p[o + SIZE] * (1.6f + 4.0f * lf)
                    ax = 1.0f; ay = 0.0f
                    half = radius * 2.2f
                } else {
                    // A leaf: physics carries its centre; its flutter swings it as a pendulum round that path.
                    val u = fxUnit
                    val ground = p[o + GROUND]
                    val rest = if (ground > 0.0f) 1.0f - (1.0f - min(1.0f, ground / 0.26f)).pow(3) else 0.0f
                    val flutter = (1.0f - exp(-2.5f * p[o + LIFE])).pow(2) * (1.0f - rest)
                    val swing = (11.0f + 11.0f * p[o + SEED]) * u
                    cx += swing * flutter * sin(p[o + PHASE])
                    cy += swing * 0.22f * flutter * cos(2.0f * p[o + PHASE])
                    val level = floor(p[o + ANGLE] / PI_F + 0.5f) * PI_F
                    val aMix = p[o + ANGLE] + (level - p[o + ANGLE]) * max(flutter, rest) + 0.55f * flutter * cos(p[o + PHASE]) + 0.14f * sin(rest * TAU) * (1.0f - rest)
                    ax = cos(aMix); ay = sin(aMix)
                    val rolling = (p[o + SEED] - 0.5f) * 18.0f * (1.0f - exp(-2.3f * p[o + LIFE])) / 2.3f + 0.75f * flutter * sin(p[o + PHASE])
                    roll = rolling + ((if (cos(rolling) > 0.0f) 1.05f else PI_F - 1.05f) - rolling) * rest
                    radius = u * (1.0f + p[o + DEPTH] * 20.0f / 260.0f) * (0.62f + 0.38f * smoothstep(0.0f, 0.14f, p[o + LIFE]))
                    half = 22.0f * radius
                }
                worldSide(p[o + LOOK], cx, cy, side)
                if (kind > 0.5f) cx += side[1]
                if (kind > 0.5f && side[0] <= 0.0f) continue
                shader.setFloatUniform("info", kind, p[o + LOOK], p[o + PREV], p[o + MORPH])
                shader.setFloatUniform("shape", radius, lf, p[o + HEAT], p[o + SEED])
                shader.setFloatUniform("pose", roll, p[o + GROUND], p[o + DEPTH], stretch)
                shader.setFloatUniform("place", cx, cy, ax, ay)
                paint.shader = shader
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(atan2(ay, ax) * 180f / PI_F)
                canvas.scale(stretch, 1f)
                canvas.drawRect(-half, -half, half, half, paint)
                canvas.restore()
            }
        }
    }

    private fun ambientSize(element: Float, seed: Float): Float {
        if (element < 0.5f) return 1.0f + 1.3f * seed
        if (element < 1.5f) return 1.8f + 3.4f * seed
        return 0.85f + 1.0f * seed
    }
}
