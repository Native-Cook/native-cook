import SwiftUI

struct TiltSwipeActionsSnapshot {
    var pull: CGFloat = 0
    var splitCount = 0
    var splitCommitted = false
    var rowOffsets: [String: CGFloat] = [:]
    var toast: CGFloat = 0
    var dim: CGFloat = 0
    var ghostPoint = CGPoint.zero
    var ghostOpacity: CGFloat = 0
    var ghostPress: CGFloat = 0
}

enum TiltSwipeActionsMotion {
    static func snapshot(at rawTime: TimeInterval) -> TiltSwipeActionsSnapshot {
        let t = rawTime.truncatingRemainder(dividingBy: TiltSwipeActionsTokens.loop)
        var state = TiltSwipeActionsSnapshot()

        if t >= 0.7, t <= 2.9 {
            let keys: [(Double, CGFloat)] = [(0.7, 0), (1.25, 100), (1.6, 100), (2.1, 170), (2.9, 170)]
            state.pull = keyed(t, keys)
            state.splitCount = detent(for: state.pull)
            state.dim = TiltSwipeActionsCurve.interpolate(state.pull, from: 24, to: 52) * 0.72
            state.ghostPoint = CGPoint(x: 16 + 193 + state.pull, y: 24)
            state.ghostOpacity = fade(t, in: 0.7...0.85, out: 2.75...2.9)
        }
        if t > 2.9, t < 5.75 {
            state.splitCommitted = true
            state.splitCount = 3
            state.toast = fade(t, in: 3.06...3.34, out: 5.60...5.74)
        }
        if t >= 3.7, t < 7.22 {
            if t < 4.25 {
                state.rowOffsets["metro"] = -150 * TiltSwipeActionsCurve.smooth(CGFloat((t - 3.7) / 0.55))
                state.ghostPoint = CGPoint(x: 16 + 300 + (state.rowOffsets["metro"] ?? 0), y: 0)
                state.ghostOpacity = fade(t, in: 3.7...3.85, out: 4.10...4.25)
            } else if t < 7.1 {
                state.rowOffsets["metro"] = -144
            } else {
                let p = TiltSwipeActionsCurve.smooth(CGFloat((t - 7.1) / 0.12))
                state.rowOffsets["metro"] = -144 * (1 - p)
                state.ghostPoint = CGPoint(x: 16 + 120, y: 0)
                state.ghostOpacity = fade(t, in: 7.1...7.16, out: 7.16...7.22)
                state.ghostPress = sin(p * .pi)
            }
        }
        if t >= 5.6, t <= 5.75 {
            state.ghostOpacity = fade(t, in: 5.6...5.66, out: 5.69...5.75)
            state.ghostPress = sin(CGFloat((t - 5.6) / 0.15) * .pi)
        }
        return state
    }

    static func detent(for pull: CGFloat) -> Int {
        guard pull >= TiltSwipeActionsTokens.Split.detentStart else { return 1 }
        return min(5, 2 + Int((pull - TiltSwipeActionsTokens.Split.detentStart) / TiltSwipeActionsTokens.Split.detentStep))
    }

    private static func keyed(_ time: TimeInterval, _ keys: [(Double, CGFloat)]) -> CGFloat {
        for index in 0..<(keys.count - 1) where time <= keys[index + 1].0 {
            let a = keys[index]
            let b = keys[index + 1]
            let p = TiltSwipeActionsCurve.smooth(CGFloat((time - a.0) / (b.0 - a.0)))
            return a.1 + (b.1 - a.1) * p
        }
        return keys.last?.1 ?? 0
    }

    private static func fade(_ time: TimeInterval, in enter: ClosedRange<Double>, out exit: ClosedRange<Double>) -> CGFloat {
        if enter.contains(time) { return TiltSwipeActionsCurve.interpolate(CGFloat(time), from: CGFloat(enter.lowerBound), to: CGFloat(enter.upperBound)) }
        if exit.contains(time) { return 1 - TiltSwipeActionsCurve.interpolate(CGFloat(time), from: CGFloat(exit.lowerBound), to: CGFloat(exit.upperBound)) }
        return time > enter.upperBound && time < exit.lowerBound ? 1 : 0
    }
}
