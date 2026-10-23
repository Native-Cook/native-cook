import UIKit

/// Tracks one primary contact for movement and every live contact for pause ownership. Autoplay
/// resumes only after the last contact ends, never when pan recognition fails mid-drag.
final class DonationFanCarouselGestureRecognizer: UIGestureRecognizer {
    /// `activeOffsetX([-8, 8])`, in points.
    private static let activeOffsetX = 8.0
    /// `failOffsetY([-18, 18])`, in points.
    private static let failOffsetY = 18.0
    /// How far back the release velocity looks.
    private static let velocityWindow = 0.1

    var onBegan: (() -> Void)?
    var onChanged: ((Double) -> Void)?
    var onEnded: ((Double) -> Void)?
    var onCancelled: (() -> Void)?
    var onFinalize: (() -> Void)?

    private struct Sample {
        let time: TimeInterval
        let x: CGFloat
    }

    private var tracking = false
    private var start = CGPoint.zero
    private var samples: [Sample] = []
    private var panActive = false
    private var panFailed = false
    private var panFinished = false
    private var activeTouches: Set<ObjectIdentifier> = []
    private var primaryTouch: ObjectIdentifier?
    private var sessionCancelled = false

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        touches.forEach { activeTouches.insert(ObjectIdentifier($0)) }
        if tracking { return }
        guard let touch = touches.first, let view else {
            state = .failed
            return
        }
        tracking = true
        start = touch.location(in: view)
        samples = [Sample(time: touch.timestamp, x: start.x)]
        panActive = false
        panFailed = false
        panFinished = false
        primaryTouch = ObjectIdentifier(touch)
        sessionCancelled = false
        onBegan?()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard tracking, !panFailed, let primaryTouch,
              let touch = touches.first(where: { ObjectIdentifier($0) == primaryTouch }),
              let view else { return }
        let location = touch.location(in: view)
        let translationX = Double(location.x - start.x)
        let translationY = Double(location.y - start.y)
        record(touch, x: location.x)

        if !panActive {
            if abs(translationY) > Self.failOffsetY {
                // Continue tracking the contact so its eventual release ends the pause.
                panFailed = true
                return
            }
            guard abs(translationX) > Self.activeOffsetX else { return }
            panActive = true
            state = .began
        } else {
            state = .changed
        }
        onChanged?(translationX)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        guard tracking else { return }
        if let primaryTouch,
           let touch = touches.first(where: { ObjectIdentifier($0) == primaryTouch }),
           let view {
            record(touch, x: touch.location(in: view).x)
            if panActive && !panFinished {
                onEnded?(releaseVelocity())
                panFinished = true
            }
            self.primaryTouch = nil
        }
        touches.forEach { activeTouches.remove(ObjectIdentifier($0)) }
        if activeTouches.isEmpty { finishSession() }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        guard tracking else { return }
        if let primaryTouch, touches.contains(where: { ObjectIdentifier($0) == primaryTouch }) {
            onCancelled?()
            panFinished = true
            self.primaryTouch = nil
            sessionCancelled = true
        }
        touches.forEach { activeTouches.remove(ObjectIdentifier($0)) }
        if activeTouches.isEmpty { finishSession() }
    }

    override func reset() {
        tracking = false
        start = .zero
        samples = []
        panActive = false
        panFailed = false
        panFinished = false
        activeTouches = []
        primaryTouch = nil
        sessionCancelled = false
    }

    /// Extra fingers do not move the fan and keep the session alive until the last one lifts.
    private func finishSession() {
        onFinalize?()
        if sessionCancelled {
            state = panActive ? .cancelled : .failed
        } else {
            state = panActive ? .ended : .failed
        }
        tracking = false
    }

    private func record(_ touch: UITouch, x: CGFloat) {
        samples.append(Sample(time: touch.timestamp, x: x))
    }

    /// Points per second over the last 100 ms before the finger lifted.
    private func releaseVelocity() -> Double {
        guard let last = samples.last else { return 0 }
        let recent = samples.filter { last.time - $0.time <= Self.velocityWindow }
        guard let first = recent.first, last.time > first.time else { return 0 }
        return Double(last.x - first.x) / (last.time - first.time)
    }
}
