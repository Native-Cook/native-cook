package com.ngocdevv.nativecook.blocks.media.tornphotodelete

import android.graphics.Matrix
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 The tear, as geometry (a port of TornPhotoDeleteModel.swift; the numbers are the same). Everything is in
 the card's own coordinates in dp (origin at its top-left corner).

 A tear starts at S on the card's edge and runs along the unit vector u for `length`; n is u turned a
 quarter turn (u = down gives n = left). The cut is a jagged line: a point every 6 dp along u, pushed
 sideways along n by a seeded noise (±3 dp grain over a ±5 dp wander), tapering to nothing at the tip T.

 The card is cut into three regions:
   side +1   the +n side of the cut, behind the line through T across u (the left flap when u points down);
   side −1   the −n side;
   rest      everything past that line (the part not yet torn), drawn unchanged.
 A flap is sheared away from the cut: a point p moves by side · open · n · (length − u·(p − S)), so it moves
 most at the card's edge and not at all on the line through T. Once the cut has crossed the card
 (commit), each flap also falls: a small pop, gravity, a drift outwards and a spin the same way.
 */

internal class TornPhotoDeleteCutPoint(val x: Double, val y: Double, val core: Double)

internal data class TornPhotoDeleteTear(
    /** Start on the card's edge and direction (degrees, 90 = down), in card dp. */
    val sx: Double,
    val sy: Double,
    val angle: Double,
    val length: Double,
    /** How far the flaps are sheared apart (0 = shut). */
    val open: Double,
    val seed: Int,
    /** Seconds since the tear crossed the card, while its pieces fall. */
    val fall: Double? = null,
) {
    val ux get() = cos(angle * PI / 180)
    val uy get() = sin(angle * PI / 180)
    val nx get() = -uy
    val ny get() = ux
    val tipX get() = sx + ux * length
    val tipY get() = sy + uy * length

    /** The cut's points from just outside the edge to the tip, with the width of the white paper core. */
    fun cut(): List<TornPhotoDeleteCutPoint> {
        val pts = ArrayList<TornPhotoDeleteCutPoint>()
        val ux = ux; val uy = uy; val nx = nx; val ny = ny
        pts.add(TornPhotoDeleteCutPoint(sx - ux * LEAD, sy - uy * LEAD, 0.0))
        var i = 0
        while (i * STEP < length) {
            val d = i * STEP
            val taper = min(1.0, (length - d) / 14)
            val off = TornPhotoDeleteNoise.offset(i, seed) * taper
            val core = (1.6 + 3.4 * TornPhotoDeleteNoise.hash(i + 500, seed)) * min(1.0, (length - d) / 30)
            pts.add(TornPhotoDeleteCutPoint(sx + ux * d + nx * off, sy + uy * d + ny * off, core))
            i++
        }
        pts.add(TornPhotoDeleteCutPoint(tipX, tipY, 0.0))
        return pts
    }

    /** The region of one side (+1 or −1) behind the tip. */
    fun region(side: Double, cut: List<TornPhotoDeleteCutPoint>): Path {
        val far = 2000.0
        val first = cut[0]
        val path = Path()
        path.moveTo(first.x.toFloat(), first.y.toFloat())
        for (k in 1 until cut.size) path.lineTo(cut[k].x.toFloat(), cut[k].y.toFloat())
        path.lineTo((tipX + nx * far * side).toFloat(), (tipY + ny * far * side).toFloat())
        path.lineTo((first.x - ux * far + nx * far * side).toFloat(), (first.y - uy * far + ny * far * side).toFloat())
        path.lineTo((first.x - ux * far).toFloat(), (first.y - uy * far).toFloat())
        path.close()
        return path
    }

    /** The part past the tip, not torn yet. */
    fun rest(): Path {
        val far = 2000.0
        val path = Path()
        path.moveTo((tipX - nx * far).toFloat(), (tipY - ny * far).toFloat())
        path.lineTo((tipX + nx * far).toFloat(), (tipY + ny * far).toFloat())
        path.lineTo((tipX + nx * far + ux * far).toFloat(), (tipY + ny * far + uy * far).toFloat())
        path.lineTo((tipX - nx * far + ux * far).toFloat(), (tipY - ny * far + uy * far).toFloat())
        path.close()
        return path
    }

    /** The white paper core along the cut on one side (the −n side shows 0.55 of it). */
    fun core(side: Double, cut: List<TornPhotoDeleteCutPoint>): Path {
        val k = if (side > 0) 1.0 else 0.55
        val path = Path()
        path.moveTo(cut[0].x.toFloat(), cut[0].y.toFloat())
        for (j in 1 until cut.size) path.lineTo(cut[j].x.toFloat(), cut[j].y.toFloat())
        for (j in cut.indices.reversed()) {
            val c = cut[j]
            path.lineTo((c.x + nx * c.core * k * side).toFloat(), (c.y + ny * c.core * k * side).toFloat())
        }
        path.close()
        return path
    }

    /** The shear of one side, then its fall (pivot: the middle of that half of the card). */
    fun transform(side: Double, cardW: Double, cardH: Double): Matrix {
        val m = side * open
        val c = length + ux * sx + uy * sy
        val a = 1 - m * nx * ux
        val b = -m * ny * ux
        val cc = -m * nx * uy
        val d = 1 - m * ny * uy
        val tx = m * nx * c
        val ty = m * ny * c
        val matrix = Matrix()
        // android.graphics.Matrix: [a c tx; b d ty].
        matrix.setValues(floatArrayOf(a.toFloat(), cc.toFloat(), tx.toFloat(), b.toFloat(), d.toFloat(), ty.toFloat(), 0f, 0f, 1f))
        val f = fall
        if (f != null) {
            val px = cardW / 2 + nx * side * cardW * 0.25
            val py = cardH / 2 + ny * side * cardW * 0.25
            val out = if (side * nx >= 0) 1.0 else -1.0
            val dx = out * 90 * f
            val dy = -150 * f + 1300 * f * f
            val spin = out * (38 * f + 30 * f * f)
            matrix.postRotate(spin.toFloat(), px.toFloat(), py.toFloat())
            matrix.postTranslate(dx.toFloat(), dy.toFloat())
        }
        return matrix
    }

    companion object {
        const val STEP = 6.0
        const val LEAD = 24.0

        /** How far the cut can run from the start along [angle] before it leaves the card. */
        fun span(sx: Double, sy: Double, angle: Double, w: Double, h: Double): Double {
            val ux = cos(angle * PI / 180)
            val uy = sin(angle * PI / 180)
            var best = Double.POSITIVE_INFINITY
            if (ux > 1e-6) best = min(best, (w - sx) / ux)
            if (ux < -1e-6) best = min(best, -sx / ux)
            if (uy > 1e-6) best = min(best, (h - sy) / uy)
            if (uy < -1e-6) best = min(best, -sy / uy)
            return if (best.isFinite()) max(0.0, best) else 0.0
        }

        /** The opening for a cut of this length: it grows over the first 60 dp. */
        fun opening(length: Double) = 0.07 * TornPhotoDeleteEase.smoothstep(0.0, 60.0, length)
    }
}

/** Seeded noise: the same numbers as the Swift and React Native versions (32-bit integer hash). */
internal object TornPhotoDeleteNoise {
    fun hash(i: Int, seed: Int): Double {
        var x = i * 374_761_393 + seed * 668_265_263
        x = (x xor (x ushr 13)) * 1_274_126_177
        x = x xor (x ushr 16)
        return (x.toLong() and 0xFFFFFFFFL).toDouble() / 4_294_967_296.0
    }

    /** The sideways offset of the cut's i-th point: ±3 dp grain over a ±5 dp wander that turns every 5 points. */
    fun offset(i: Int, seed: Int): Double {
        val j = i / 5
        val f = (i % 5) / 5.0
        val s = f * f * (3 - 2 * f)
        val a = hash(j + 9000, seed)
        val b = hash(j + 9001, seed)
        val wander = (a + (b - a) * s - 0.5) * 10
        return wander + (hash(i, seed) - 0.5) * 6
    }
}

internal object TornPhotoDeleteEase {
    fun clamp(x: Double) = min(1.0, max(0.0, x))
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun inOut(x: Double): Double {
        val t = clamp(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }
    fun out(x: Double) = 1 - (1 - clamp(x)).pow(3)
    fun outBack(x: Double): Double {
        val t = clamp(x)
        val c1 = 1.70158
        val c3 = c1 + 1
        return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
    }
    /** Critically damped return of an opening to 0 (ω = 16 /s). */
    fun heal(open: Double, t: Double): Double {
        if (t <= 0) return open
        val w = 16.0
        val v = open * (1 + w * t) * exp(-w * t)
        return if (v < 0.0005) 0.0 else v
    }
}

/** What the screen shows at one moment. Points are screen dp. */
internal class TornPhotoDeleteScene {
    var tear: TornPhotoDeleteTear? = null
    var cardAlpha = 1.0
    var cardScale = 1.0
    var cardLift = 0.0
    /** The scripted finger: where, how visible, how pressed. */
    var dotX = 0.0
    var dotY = 0.0
    var dotAlpha = 0.0
    var dotPress = 0.0
    var toast = 0.0
    var undoPress = 0.0
    var hint = 1.0
    var empty = 0.0
}

/*
 autoPlay timeline (LOOP = 9 s), the same as TornPhotoDeleteModel.swift:
   0.00–0.70  rest.
   0.70       the finger lands on the top edge; 0.85–1.75 tear A runs down 42 % of the card (ease in-out);
              1.95 the finger lifts and the tear heals (critically damped, ~0.35 s).
   2.55       the finger lands on the right edge; 2.70–3.55 tear B runs left and a little down, 58 % across;
              3.70 lift, heal.
   4.30       the finger lands on the top edge again; 4.45–5.45 tear C rips to the bottom, speeding up;
              5.45 commit. 5.45–6.45 the two halves fall away.
   5.45–5.75  the hint fades; 5.80–6.20 the toast rises (ease-out-back); 6.00–6.40 the empty note fades in.
   6.90       the finger lands on Undo; 7.10–7.28 Undo is pressed (released by 7.43); 7.43–7.68 the toast drops.
   7.50–7.70  the empty note fades; 7.68–8.48 the card comes back (fade, scale 0.92 → 1 with a small
              overshoot, 14 dp lift); 8.20–8.60 the hint returns. 8.60–9.00 rest, identical to t = 0.
 */
internal object TornPhotoDeleteTimeline {
    const val LOOP = 9.0

    class Script(
        val fx: Double, val fy: Double, val angle: Double, val reach: Double, val seed: Int,
        val land: Double, val from: Double, val to: Double, val lift: Double,
    )

    val tearA = Script(0.57, 0.0, 96.0, 0.42, 11, 0.70, 0.85, 1.75, 1.95)
    val tearB = Script(1.0, 0.30, 168.0, 0.58, 23, 2.55, 2.70, 3.55, 3.70)
    val tearC = Script(0.38, 0.0, 80.0, 1.06, 37, 4.30, 4.45, 5.45, 5.45)
    const val COMMIT = 5.45
    const val UNDO_LAND = 6.90
    const val UNDO_PRESS = 7.10
    const val UNDO_UP = 7.28
    const val TOAST_IN = 5.80
    const val TOAST_OUT = 7.43
    const val RESTORE = 7.68

    private val E = TornPhotoDeleteEase

    /** [cardX], [cardY]: the card's top-left on screen; [undoX], [undoY]: the middle of Undo. */
    fun scene(
        t: Double, cardX: Double, cardY: Double, cardW: Double, cardH: Double,
        undoX: Double, undoY: Double, quiet: Boolean = false,
    ): TornPhotoDeleteScene {
        val s = TornPhotoDeleteScene()

        fun run(sc: Script, curve: (Double) -> Double): TornPhotoDeleteTear {
            val sx = sc.fx * cardW
            val sy = sc.fy * cardH
            val span = TornPhotoDeleteTear.span(sx, sy, sc.angle, cardW, cardH)
            val len = sc.reach * span * curve((t - sc.from) / (sc.to - sc.from))
            return TornPhotoDeleteTear(sx, sy, sc.angle, len, TornPhotoDeleteTear.opening(len), sc.seed)
        }
        fun dot(sc: Script, tear: TornPhotoDeleteTear) {
            if (t < sc.land || t >= sc.lift + 0.2) return
            val a = E.smoothstep(sc.land, sc.land + 0.15, t) * (1 - E.smoothstep(sc.lift, sc.lift + 0.2, t))
            val px = if (tear.length > 0) tear.tipX else tear.sx - tear.ux * 6
            val py = if (tear.length > 0) tear.tipY else tear.sy - tear.uy * 6
            s.dotX = cardX + px; s.dotY = cardY + py
            s.dotAlpha = a
            s.dotPress = E.smoothstep(sc.land, sc.land + 0.15, t)
        }

        if (t < tearB.land) {
            val tear = run(tearA, E::inOut)
            var tr = tear
            if (t > tearA.lift) tr = tr.copy(open = E.heal(TornPhotoDeleteTear.opening(tear.length), t - tearA.lift))
            if (t >= tearA.from && tr.open > 0) s.tear = tr
            dot(tearA, tear)
        } else if (t < tearC.land) {
            val tear = run(tearB, E::inOut)
            var tr = tear
            if (t > tearB.lift) tr = tr.copy(open = E.heal(TornPhotoDeleteTear.opening(tear.length), t - tearB.lift))
            if (t >= tearB.from && tr.open > 0) s.tear = tr
            dot(tearB, tear)
        } else if (t < RESTORE) {
            // Tear C speeds up like a real rip: 0.35 x + 0.65 x².
            var tear = run(tearC) { x -> val c = E.clamp(x); 0.35 * c + 0.65 * c * c }
            if (t >= COMMIT) tear = tear.copy(fall = t - COMMIT)
            if (t >= tearC.from && (tear.fall ?: 0.0) < 1.2) s.tear = tear
            if (t >= COMMIT + 1.2) s.cardAlpha = 0.0
            dot(tearC, tear)
        }

        if (t >= TOAST_IN && t < TOAST_OUT + 0.3) {
            s.toast = E.outBack((t - TOAST_IN) / 0.4) * (1 - E.smoothstep(TOAST_OUT, TOAST_OUT + 0.25, t))
        }
        if (t >= UNDO_PRESS && t < UNDO_UP + 0.15) {
            s.undoPress = E.smoothstep(UNDO_PRESS, UNDO_PRESS + 0.08, t) * (1 - E.smoothstep(UNDO_UP, UNDO_UP + 0.15, t))
        }
        if (!quiet && t >= UNDO_LAND && t < UNDO_UP + 0.25) {
            s.dotX = undoX; s.dotY = undoY
            s.dotAlpha = E.smoothstep(UNDO_LAND, UNDO_LAND + 0.15, t) * (1 - E.smoothstep(UNDO_UP + 0.05, UNDO_UP + 0.25, t))
            s.dotPress = E.smoothstep(UNDO_LAND, UNDO_LAND + 0.15, t)
        }
        s.empty = E.smoothstep(6.0, 6.4, t) * (1 - E.smoothstep(7.5, 7.7, t))
        s.hint = 1 - E.smoothstep(5.45, 5.75, t) + E.smoothstep(8.2, 8.6, t)
        if (t >= RESTORE) {
            val r = t - RESTORE
            s.cardAlpha = E.out(r / 0.4)
            s.cardScale = 0.92 + 0.08 * E.outBack(r / 0.8)
            s.cardLift = 14 * (1 - E.out(r / 0.8))
        }
        return s
    }
}
