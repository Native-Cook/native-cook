import Foundation

/// The easing curves the block uses, as Reanimated defines them.
enum DragMultiSelectEasing {
    private static let outToken = DragMultiSelectTokens.Ease.out
    private static let inOutToken = DragMultiSelectTokens.Ease.inOut
    /// `Easing.inOut(Easing.quad)`: the default of `withTiming`.
    case inOutQuad
    /// The source `EASE.out`: cubic-bezier(0.23, 1, 0.32, 1).
    case out
    /// The source `EASE.inOut`: cubic-bezier(0.77, 0, 0.175, 1).
    case inOut

    func value(_ t: Double) -> Double {
        switch self {
        case .inOutQuad:
            return t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t)
        case .out:
            return cubicBezier(t, x1: Self.outToken.0, y1: Self.outToken.1, x2: Self.outToken.2, y2: Self.outToken.3)
        case .inOut:
            return cubicBezier(t, x1: Self.inOutToken.0, y1: Self.inOutToken.1, x2: Self.inOutToken.2, y2: Self.inOutToken.3)
        }
    }

    /// Reanimated's bezier takes animation progress on the x axis, so solve x(s) = progress before
    /// reading y(s). Newton iteration handles ordinary frames; bisection is the stable fallback.
    private func cubicBezier(_ progress: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        let progress = min(max(progress, 0), 1)
        if progress == 0 || progress == 1 { return progress }

        func coordinate(_ s: Double, _ p1: Double, _ p2: Double) -> Double {
            let u = 1 - s
            return 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s
        }

        func derivative(_ s: Double, _ p1: Double, _ p2: Double) -> Double {
            3 * (1 - s) * (1 - s) * p1
                + 6 * (1 - s) * s * (p2 - p1)
                + 3 * s * s * (1 - p2)
        }

        var parameter = progress
        for _ in 0..<8 {
            let error = coordinate(parameter, x1, x2) - progress
            if abs(error) < 1e-7 { return coordinate(parameter, y1, y2) }
            let slope = derivative(parameter, x1, x2)
            if abs(slope) < 1e-7 { break }
            let next = parameter - error / slope
            if next < 0 || next > 1 { break }
            parameter = next
        }

        var lower = 0.0
        var upper = 1.0
        for _ in 0..<24 {
            parameter = (lower + upper) / 2
            if coordinate(parameter, x1, x2) < progress {
                lower = parameter
            } else {
                upper = parameter
            }
        }
        return coordinate(parameter, y1, y2)
    }
}
