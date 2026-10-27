package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max

/**
 * The key presses of the 6.1 s loop (`PRESSES` in the React Native file) and the pressure each one
 * gives a key: 0 at rest, 1 with the cap fully sunk.
 *
 * A beat sinks its cap in five frames of 60 fps on a smoothstep, holds it until the release, and lets
 * it come back as a damped oscillation (`exp(-13 t) * cos(7 t)`, clamped at 0 so it never lifts above
 * the rest height). The oscillation is over 0.3 s after the release.
 */
internal object TactileCalculatorWelcomePressure {
    private class Beat(
        val operation: TactileCalculatorWelcomeOperation,
        val down: Double,
        val release: Double,
    )

    /** Divide at 0.17 s, plus 1.17, multiply 2.2, equals 3.18, divide 4.18 and plus 5.18 (frames of 60 fps). */
    private val beats = listOf(
        Beat(TactileCalculatorWelcomeOperation.Divide, 10.0 / 60.0, 40.0 / 60.0),
        Beat(TactileCalculatorWelcomeOperation.Add, 70.0 / 60.0, 100.0 / 60.0),
        Beat(TactileCalculatorWelcomeOperation.Multiply, 132.0 / 60.0, 160.0 / 60.0),
        Beat(TactileCalculatorWelcomeOperation.Equals, 191.0 / 60.0, 220.0 / 60.0),
        Beat(TactileCalculatorWelcomeOperation.Divide, 251.0 / 60.0, 280.0 / 60.0),
        Beat(TactileCalculatorWelcomeOperation.Add, 311.0 / 60.0, 340.0 / 60.0),
    )

    /** The pressure the loop gives [operation]'s key at loop time [t] (seconds). */
    fun demo(operation: TactileCalculatorWelcomeOperation, t: Double): Double {
        var demo = 0.0
        for (beat in beats) {
            if (beat.operation != operation || t < beat.down || t > beat.release + 0.3) continue
            if (t < beat.down + 5.0 / 60.0) {
                val p = (t - beat.down) / (5.0 / 60.0)
                demo = p * p * (3.0 - 2.0 * p)
            } else if (t < beat.release) {
                demo = 1.0
            } else {
                val elapsed = t - beat.release
                demo = max(0.0, exp(-13.0 * elapsed) * cos(7.0 * elapsed))
            }
        }
        return demo
    }
}
