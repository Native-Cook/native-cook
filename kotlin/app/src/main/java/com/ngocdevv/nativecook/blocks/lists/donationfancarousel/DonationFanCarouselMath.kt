package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import kotlin.math.floor

/** The few Reanimated and JavaScript helpers the React Native block calls. */
internal object DonationFanCarouselMath {
    private val FanInput = doubleArrayOf(0.0, 1.0, 2.0, 3.0)

    /** `clamp01` in `motion.ts`. */
    fun clamp01(value: Double): Double {
        return if (value < 0) 0.0 else if (value > 1) 1.0 else value
    }

    /** JavaScript's `Math.round`: halves go up, also for negative numbers. */
    fun jsRound(value: Double): Double {
        return floor(value + 0.5)
    }

    /**
     * Reanimated's `interpolate`: piecewise linear between the [input] stops. Outside them the value
     * is held (`Extrapolation.CLAMP`) or the first or last segment carries on (the default, `extend`).
     */
    fun interpolate(x: Double, input: DoubleArray, output: DoubleArray, clamp: Boolean = false): Double {
        val length = input.size
        var leftInput = input[0]
        var rightInput = input[1]
        var leftOutput = output[0]
        var rightOutput = output[1]
        if (length > 2) {
            if (x > input[length - 1]) {
                leftInput = input[length - 2]
                rightInput = input[length - 1]
                leftOutput = output[length - 2]
                rightOutput = output[length - 1]
            } else {
                for (index in 1 until length) {
                    if (x <= input[index]) {
                        leftInput = input[index - 1]
                        rightInput = input[index]
                        leftOutput = output[index - 1]
                        rightOutput = output[index]
                        break
                    }
                }
            }
        }
        if (x < leftInput) {
            if (clamp) return leftOutput
            return leftOutput + (x - leftInput) / (rightInput - leftInput) * (rightOutput - leftOutput)
        }
        if (x > rightInput) {
            if (clamp) return rightOutput
            return leftOutput + (x - leftInput) / (rightInput - leftInput) * (rightOutput - leftOutput)
        }
        val progress = (x - leftInput) / (rightInput - leftInput)
        return leftOutput + progress * (rightOutput - leftOutput)
    }

    /** `interpolate(absolute, [0, 1, 2, 3], values)` for the fan tables. */
    fun fan(absolute: Double, values: DoubleArray, clamp: Boolean): Double {
        return interpolate(absolute, FanInput, values, clamp)
    }
}
