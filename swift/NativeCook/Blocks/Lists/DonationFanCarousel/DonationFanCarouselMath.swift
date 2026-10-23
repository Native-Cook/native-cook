import Foundation

/// The few Reanimated and JavaScript helpers the React Native block calls.
enum DonationFanCarouselMath {
    /// `clamp01` in `motion.ts`.
    static func clamp01(_ value: Double) -> Double {
        value < 0 ? 0 : value > 1 ? 1 : value
    }

    /// JavaScript's `Math.round`: halves go up, also for negative numbers.
    static func jsRound(_ value: Double) -> Double {
        (value + 0.5).rounded(.down)
    }

    /// Reanimated's `interpolate`: piecewise linear between the `input` stops. Outside them the value
    /// is held (`Extrapolation.CLAMP`) or the first or last segment carries on (the default, `extend`).
    static func interpolate(_ x: Double, input: [Double], output: [Double], clamp: Bool = false) -> Double {
        let length = input.count
        var leftInput = input[0]
        var rightInput = input[1]
        var leftOutput = output[0]
        var rightOutput = output[1]
        if length > 2 {
            if x > input[length - 1] {
                leftInput = input[length - 2]
                rightInput = input[length - 1]
                leftOutput = output[length - 2]
                rightOutput = output[length - 1]
            } else {
                for index in 1..<length where x <= input[index] {
                    leftInput = input[index - 1]
                    rightInput = input[index]
                    leftOutput = output[index - 1]
                    rightOutput = output[index]
                    break
                }
            }
        }
        if x < leftInput {
            if clamp { return leftOutput }
            return leftOutput + (x - leftInput) / (rightInput - leftInput) * (rightOutput - leftOutput)
        }
        if x > rightInput {
            if clamp { return rightOutput }
            return leftOutput + (x - leftInput) / (rightInput - leftInput) * (rightOutput - leftOutput)
        }
        let progress = (x - leftInput) / (rightInput - leftInput)
        return leftOutput + progress * (rightOutput - leftOutput)
    }

    /// `interpolate(absolute, [0, 1, 2, 3], values)` for the fan tables.
    static func fan(_ absolute: Double, _ values: [Double], clamp: Bool) -> Double {
        interpolate(absolute, input: [0, 1, 2, 3], output: values, clamp: clamp)
    }
}
