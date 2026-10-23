import QuartzCore
import SwiftUI
import UIKit

struct TiltPosterParallaxTouchView: UIViewRepresentable {
    let model: TiltPosterParallaxModel

    func makeUIView(context: Context) -> Surface {
        Surface(model: model)
    }

    func updateUIView(_ view: Surface, context: Context) {
        view.model = model
    }

    static func dismantleUIView(_ view: Surface, coordinator: Void) {
        view.stopDisplayLink()
    }

    @MainActor
    final class Surface: UIView, UIGestureRecognizerDelegate {
        weak var model: TiltPosterParallaxModel?

        private let displayTarget = DisplayTarget()
        private var displayLink: CADisplayLink?
        private var previousPoint = CGPoint.zero
        private var previousTime: CFTimeInterval = 0
        private var velocity = CGVector.zero

        init(model: TiltPosterParallaxModel) {
            self.model = model
            super.init(frame: .zero)
            backgroundColor = .clear
            isOpaque = false
            isAccessibilityElement = true
            accessibilityTraits = [.image, .button]
            accessibilityLabel = TiltPosterParallaxTokens.Copy.posterLabel
            accessibilityHint = TiltPosterParallaxTokens.Copy.posterHint

            let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
            touch.minimumPressDuration = 0
            touch.allowableMovement = .greatestFiniteMagnitude
            touch.numberOfTouchesRequired = 1
            touch.cancelsTouchesInView = false
            touch.delaysTouchesBegan = false
            touch.delegate = self
            addGestureRecognizer(touch)
            displayTarget.owner = self
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) is unavailable")
        }

        deinit {
            displayLink?.invalidate()
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window == nil {
                stopDisplayLink()
            } else {
                startDisplayLink()
            }
        }

        override func accessibilityActivate() -> Bool {
            model?.toggleFlip()
            return model != nil
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
            guard let model else { return }
            let point = recognizer.location(in: self)
            let now = CACurrentMediaTime()

            switch recognizer.state {
            case .began:
                previousPoint = point
                previousTime = now
                velocity = .zero
                model.touchBegan(at: point, timestamp: now)
            case .changed:
                let elapsed = max(now - previousTime, 1 / 240)
                velocity = CGVector(
                    dx: (point.x - previousPoint.x) / elapsed,
                    dy: (point.y - previousPoint.y) / elapsed
                )
                previousPoint = point
                previousTime = now
                model.touchMoved(to: point, timestamp: now)
            case .ended:
                updateFinalVelocity(point: point, time: now)
                model.touchEnded(velocity: velocity, timestamp: now, cancelled: false)
            case .cancelled, .failed:
                model.touchEnded(velocity: .zero, timestamp: now, cancelled: true)
            default:
                break
            }
        }

        private func updateFinalVelocity(point: CGPoint, time: CFTimeInterval) {
            let elapsed = time - previousTime
            guard elapsed > 1 / 240, point != previousPoint else { return }
            velocity = CGVector(
                dx: (point.x - previousPoint.x) / elapsed,
                dy: (point.y - previousPoint.y) / elapsed
            )
        }

        private func startDisplayLink() {
            guard displayLink == nil else { return }
            let displayLink = CADisplayLink(target: displayTarget, selector: #selector(DisplayTarget.displayFrame(_:)))
            displayLink.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
            displayLink.add(to: .main, forMode: .common)
            self.displayLink = displayLink
        }

        func stopDisplayLink() {
            displayLink?.invalidate()
            displayLink = nil
        }

        fileprivate func displayFrame(_ displayLink: CADisplayLink) {
            model?.advance(to: displayLink.targetTimestamp)
        }

        @MainActor
        private final class DisplayTarget: NSObject {
            weak var owner: Surface?

            @objc func displayFrame(_ displayLink: CADisplayLink) {
                owner?.displayFrame(displayLink)
            }
        }
    }
}
