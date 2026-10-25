package com.ngocdevv.nativecook.blocks.pickers.dicerollpicker

import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.HALF
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.THROW_END
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TY1
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.axisAngle
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.easeInOut
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.easeOut
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.faceUp
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.mix
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.mul
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.seg
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/*
 Dice Roll Picker — the autoPlay timeline (LOOP = 8 s):
   0.0 – 1.2   rest: the dice show 3 and 4 (sum 7), the line reads "Flick the dice to pick who starts".
   1.2 – 1.6   pick up: both dice lift 34 pt and come toward the player, tipping back (ease-out).
   1.6 – 3.8   the throw (die B 0.05 s behind): first landing, a bounce off the far rim, hops of
               96, 34 and 10 pt, two and three whole turns, then a rock on the face. They land on 5 and 6.
   3.75 – 4.05 the line rolls to "11 · Priya starts"; Priya's avatar pops and wears a "Starts" tag.
   3.8 – 5.6   hold.
   5.6 – 7.2   the dice slide home, tipping over as they turn back to 3 and 4; the line and the
               avatar settle back (5.6 – 6.0).
   7.2 – 8.0   rest.
 */

/** The autoPlay loop, seconds (the manifest's `recording.loop`). */
const val LOOP = 8.0

internal object DiceMotion {
    val RIM_Y = TY1 - HALF - 6

    private val REST_FACE = intArrayOf(3, 4)
    private val REST_YAW = doubleArrayOf(0.34, -0.24)
    private val REST_X = doubleArrayOf(-50.0, 50.0)
    private val REST_Y = doubleArrayOf(-6.0, 8.0)
    private val PICK_X = doubleArrayOf(-36.0, 36.0)
    private val PICK_Y = doubleArrayOf(-76.0, -70.0)
    private val FINAL_FACE = intArrayOf(5, 6)
    private val FINAL_YAW = doubleArrayOf(-0.46, 0.38)
    private val FINAL_X = doubleArrayOf(-38.0, 42.0)
    private val FINAL_Y = doubleArrayOf(44.0, 26.0)
    private val TURNS = intArrayOf(2, 3)
    private val DELAY = doubleArrayOf(0.0, 0.05)

    private val REST_M = Array(2) { faceUp(REST_FACE[it], REST_YAW[it]) }
    private val FINAL_M = Array(2) { faceUp(FINAL_FACE[it], FINAL_YAW[it]) }
    private val THROWS = Array(2) {
        DiceMath.makeThrow(REST_M[it], FINAL_M[it], PICK_X[it], PICK_Y[it], FINAL_X[it], FINAL_Y[it], 34.0, TURNS[it], RIM_Y)
    }
    private val REST_POSE = Array(2) { DicePose(REST_M[it], REST_X[it], REST_Y[it], 0.0) }
    private val PICKED = Array(2) { DicePose(REST_M[it], PICK_X[it], PICK_Y[it], 34.0) }

    val AUTO_SUM = FINAL_FACE[0] + FINAL_FACE[1]

    fun restPose(i: Int) = REST_POSE[i]

    /** A die's pose at loop time t. */
    fun autoPose(i: Int, t: Double): DicePose {
        if (t < 1.2 || t >= 7.2) return REST_POSE[i]
        if (t < 1.6) {
            val u = seg(t, 1.2, 1.6)
            val e = easeOut(u)
            val tip = axisAngle(1.0, 0.0, 0.0, -0.18 * sin(PI * u))
            return DicePose(mul(tip, REST_M[i]), mix(REST_X[i], PICK_X[i], easeInOut(u)), mix(REST_Y[i], PICK_Y[i], easeInOut(u)), 34 * e)
        }
        val s = t - 1.6 - DELAY[i]
        if (t < 5.6) {
            if (s <= 0) return PICKED[i]
            return DiceMath.throwPose(THROWS[i], min(s, THROW_END))
        }
        return DiceMath.slidePose(FINAL_M[i], REST_M[i], FINAL_X[i], FINAL_Y[i], REST_X[i], REST_Y[i], seg(t, 5.6, 7.2))
    }

    /** A spring-like pop from 0 to 1, ~20 % overshoot, after time x ≥ 0. */
    fun springIn(x: Double): Double = if (x <= 0) 0.0 else 1 - exp(-6 * x) * cos(12 * x)

    /** The result line's roll 0 … 1 at loop time t. */
    fun autoRoll(t: Double): Double {
        val out = easeInOut(seg(t, 5.6, 6.0))
        return easeInOut(seg(t, 3.75, 4.05)) * (1 - out)
    }

    /** The chosen avatar's pop at loop time t. */
    fun autoPop(t: Double): Double = springIn(t - 3.85) * (1 - easeInOut(seg(t, 5.6, 6.0)))
}
