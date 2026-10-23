import QuartzCore
import SwiftUI
import UIKit

/// A zero-delay UIKit recognizer keeps the handset drag alive while the
/// gallery's parent swipe recognizer observes the same touch stream.
struct SlideToAnswerCordTouchView: UIViewRepresentable {
    let simulation: SlideToAnswerCordSimulation

    func makeUIView(context: Context) -> Surface {
        Surface(simulation: simulation)
    }

    func updateUIView(_ view: Surface, context: Context) {
        view.update(simulation: simulation)
    }

    static func dismantleUIView(_ view: Surface, coordinator: Void) {
        view.stopDisplayLink()
    }

    @MainActor
    final class Surface: UIView, UIGestureRecognizerDelegate {
        weak var simulation: SlideToAnswerCordSimulation?

        private let selectionFeedback = UISelectionFeedbackGenerator()
        private let answerFeedback = UINotificationFeedbackGenerator()
        private let declineFeedback = UIImpactFeedbackGenerator(style: .medium)
        private let displayTarget = DisplayTarget()
        private var displayLink: CADisplayLink?
        private var reducedTimer: Timer?
        private var previousPoint = CGPoint.zero
        private var previousTime: CFTimeInterval = 0
        private var velocity = CGVector.zero
        private var ownsHandsetTouch = false
        private var handsetElement: HandsetElement?
        private var observedPhase: SlideToAnswerCordSimulation.Phase?

        init(simulation: SlideToAnswerCordSimulation) {
            self.simulation = simulation
            super.init(frame: .zero)
            backgroundColor = .clear
            isOpaque = false
            isAccessibilityElement = false

            let handsetElement = HandsetElement(container: self, simulation: simulation)
            self.handsetElement = handsetElement
            accessibilityElements = [handsetElement]

            let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
            touch.minimumPressDuration = 0
            touch.allowableMovement = .greatestFiniteMagnitude
            touch.numberOfTouchesRequired = 1
            touch.cancelsTouchesInView = false
            touch.delaysTouchesBegan = false
            touch.delegate = self
            addGestureRecognizer(touch)

            prepareFeedback()
            displayTarget.owner = self
            updateAccessibility()
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) is unavailable")
        }

        deinit {
            displayLink?.invalidate()
            reducedTimer?.invalidate()
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window == nil {
                stopDisplayLink()
            } else {
                startDisplayLink()
                updateClockMode()
            }
        }

        /// Let buttons and the gallery receive touches outside the moving
        /// handset even though this representable fills the stage.
        override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
            guard let simulation else { return false }
            return simulation.hitTestHandset(point)
        }

        func update(simulation: SlideToAnswerCordSimulation) {
            self.simulation = simulation
            handsetElement?.simulation = simulation
            play(simulation.drainHapticEvents())
            if observedPhase != simulation.frame.phase {
                observedPhase = simulation.frame.phase
                // Align the one-second semantic clock with answer/end/decline,
                // rather than with the view's original appearance time.
                reducedTimer?.invalidate()
                reducedTimer = nil
            }
            updateClockMode()
            updateAccessibility()
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
            guard let simulation else { return }
            let point = recognizer.location(in: self)
            let now = CACurrentMediaTime()

            switch recognizer.state {
            case .began:
                previousPoint = point
                previousTime = now
                velocity = .zero
                ownsHandsetTouch = simulation.touchBegan(at: point)

            case .changed where ownsHandsetTouch:
                let elapsed = max(now - previousTime, 1.0 / 240.0)
                velocity = CGVector(
                    dx: (point.x - previousPoint.x) / elapsed,
                    dy: (point.y - previousPoint.y) / elapsed
                )
                previousPoint = point
                previousTime = now
                simulation.touchMoved(to: point, velocity: velocity)

            case .ended where ownsHandsetTouch:
                updateFinalVelocity(point: point, time: now)
                simulation.touchEnded(velocity: velocity, cancelled: false)
                ownsHandsetTouch = false

            case .cancelled where ownsHandsetTouch,
                 .failed where ownsHandsetTouch:
                simulation.touchEnded(velocity: .zero, cancelled: true)
                ownsHandsetTouch = false

            default:
                break
            }
        }

        private func updateFinalVelocity(point: CGPoint, time: CFTimeInterval) {
            let elapsed = time - previousTime
            guard elapsed > 1.0 / 240.0, point != previousPoint else { return }
            velocity = CGVector(
                dx: (point.x - previousPoint.x) / elapsed,
                dy: (point.y - previousPoint.y) / elapsed
            )
        }

        private func startDisplayLink() {
            guard displayLink == nil else { return }
            let displayLink = CADisplayLink(
                target: displayTarget,
                selector: #selector(DisplayTarget.displayFrame(_:))
            )
            displayLink.preferredFrameRateRange = CAFrameRateRange(
                minimum: 60,
                maximum: 120,
                preferred: 120
            )
            displayLink.add(to: .main, forMode: .common)
            self.displayLink = displayLink
            updateClockMode()
        }

        func stopDisplayLink() {
            displayLink?.invalidate()
            displayLink = nil
            reducedTimer?.invalidate()
            reducedTimer = nil
        }

        fileprivate func displayFrame(_ displayLink: CADisplayLink) {
            guard let simulation else { return }
            simulation.advance(to: displayLink.targetTimestamp)
            play(simulation.drainHapticEvents())
            updateAccessibility()
        }

        fileprivate func reducedTimerDidFire() {
            guard let simulation else { return }
            let previousPhase = simulation.frame.phase
            simulation.advanceReducedClock(
                by: SlideToAnswerCordMotion.State.resetAfter / 2
            )
            if simulation.frame.phase != previousPhase {
                observedPhase = simulation.frame.phase
                reducedTimer?.invalidate()
                reducedTimer = nil
                updateClockMode()
            }
            play(simulation.drainHapticEvents())
            updateAccessibility()
        }

        private func updateClockMode() {
            displayLink?.isPaused = false
            reducedTimer?.invalidate()
            reducedTimer = nil
        }

        private func play(_ events: [SlideToAnswerCordSimulation.HapticEvent]) {
            for event in events {
                switch event {
                case .grab, .tick:
                    selectionFeedback.selectionChanged()
                    selectionFeedback.prepare()
                case .answer:
                    answerFeedback.notificationOccurred(.success)
                    answerFeedback.prepare()
                case .decline:
                    declineFeedback.impactOccurred()
                    declineFeedback.prepare()
                }
            }
        }

        private func prepareFeedback() {
            selectionFeedback.prepare()
            answerFeedback.prepare()
            declineFeedback.prepare()
        }

        private func updateAccessibility() {
            guard let simulation, let handsetElement else { return }
            let frame = simulation.frame
            handsetElement.accessibilityFrameInContainerSpace = CGRect(
                x: frame.geometry.width / 2 - 75,
                y: frame.geometry.restY - 10,
                width: 150,
                height: 200
            )
            handsetElement.accessibilityValue = switch frame.phase {
            case .ringing:
                SlideToAnswerCordTheme.Copy.ringing
            case .connected:
                "Connected"
            case .declined:
                SlideToAnswerCordTheme.Copy.declined
            case .ended:
                SlideToAnswerCordTheme.Copy.ended
            }
        }

        @MainActor
        private final class HandsetElement: UIAccessibilityElement {
            weak var simulation: SlideToAnswerCordSimulation?

            init(container: Any, simulation: SlideToAnswerCordSimulation) {
                self.simulation = simulation
                super.init(accessibilityContainer: container)
                isAccessibilityElement = true
                accessibilityTraits = [.adjustable, .button]
                accessibilityLabel = SlideToAnswerCordTheme.Copy.a11yHandset
                accessibilityHint = SlideToAnswerCordTheme.Copy.a11yHint
            }

            override func accessibilityActivate() -> Bool {
                guard let simulation else { return false }
                simulation.command(simulation.frame.phase == .connected ? .end : .answer)
                return true
            }

            override func accessibilityIncrement() {
                simulation?.command(.answer)
            }

            override func accessibilityDecrement() {
                guard let simulation else { return }
                simulation.command(simulation.frame.phase == .connected ? .end : .decline)
            }
        }

        @MainActor
        private final class DisplayTarget: NSObject {
            weak var owner: Surface?

            @objc func displayFrame(_ displayLink: CADisplayLink) {
                owner?.displayFrame(displayLink)
            }

            @objc func reducedTimerDidFire(_ timer: Timer) {
                owner?.reducedTimerDidFire()
            }
        }
    }
}
