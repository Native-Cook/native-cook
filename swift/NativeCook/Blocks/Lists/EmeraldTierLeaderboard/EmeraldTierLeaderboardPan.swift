import SwiftUI

/// A one-pointer pan recogniser with the activation rules of the RN `Gesture.Pan()` configs:
/// `activeOffset` along the axis starts it, `failOffset` across the axis rejects it first.
/// Translation and velocity are along the axis, in screen points (pass the global translation).
/// A touch that never activates and never fails ends as a tap (the RectButton's press).
final class EmeraldTierPan {
    enum Axis { case horizontal, vertical }
    enum Event {
        case none
        /// Activation: the total travel along the axis so far.
        case began(Double)
        case updated(Double)
        case ended(velocity: Double)
        /// A release that never became a pan, with the touch-down location in the view's own space.
        case tap(CGPoint)
    }
    private enum Phase { case idle, possible, active, failed }

    let axis: Axis
    let activeOffset: Double
    let failOffset: Double
    /// A press that moved less than this still counts as a tap (RectButton keeps the press inside its bounds).
    let tapSlop = 10.0

    private var phase = Phase.idle
    private var samples: [(time: Double, value: Double)] = []
    private(set) var downLocation = CGPoint.zero

    init(axis: Axis, activeOffset: Double, failOffset: Double) {
        self.axis = axis
        self.activeOffset = activeOffset
        self.failOffset = failOffset
    }

    var isActive: Bool { phase == .active }
    var isFailed: Bool { phase == .failed }
    /// True between touch-down and the end of a touch that has not turned into a pan or failed.
    var isPossible: Bool { phase == .possible }

    func changed(globalTranslation: CGSize, location: CGPoint, localTranslation: CGSize, time: Date) -> Event {
        let t = time.timeIntervalSinceReferenceDate
        let main = axis == .vertical ? Double(globalTranslation.height) : Double(globalTranslation.width)
        let cross = axis == .vertical ? Double(globalTranslation.width) : Double(globalTranslation.height)
        if phase == .idle {
            phase = .possible
            samples = []
            // `location` is the current point; the touch-down point is that minus the (local) travel.
            downLocation = CGPoint(x: location.x - localTranslation.width, y: location.y - localTranslation.height)
        }
        samples.append((t, main))
        samples.removeAll { t - $0.time > 0.12 }
        switch phase {
        case .possible:
            if abs(cross) >= failOffset { phase = .failed; return .none }
            if abs(main) >= activeOffset { phase = .active; return .began(main) }
            return .none
        case .active:
            return .updated(main)
        default:
            return .none
        }
    }

    func ended(globalTranslation: CGSize, time: Date) -> Event {
        defer { phase = .idle; samples = [] }
        switch phase {
        case .active:
            let t = time.timeIntervalSinceReferenceDate
            let recent = samples.filter { t - $0.time <= 0.1 }
            guard let first = recent.first, let last = recent.last, last.time - first.time > 0.001 else { return .ended(velocity: 0) }
            return .ended(velocity: (last.value - first.value) / (last.time - first.time))
        case .possible:
            let dx = Double(globalTranslation.width), dy = Double(globalTranslation.height)
            return (dx * dx + dy * dy).squareRoot() <= tapSlop ? .tap(downLocation) : .none
        default:
            return .none
        }
    }
}
