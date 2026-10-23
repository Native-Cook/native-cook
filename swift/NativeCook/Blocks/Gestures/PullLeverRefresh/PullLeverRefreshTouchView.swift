import SwiftUI
import UIKit

/// A zero-delay recognizer remains simultaneous with the gallery's pan gesture.
struct PullLeverRefreshTouchView<Content: View>: View {
    let model: PullLeverRefreshModel
    private let content: () -> Content

    init(model: PullLeverRefreshModel, @ViewBuilder content: @escaping () -> Content) {
        self.model = model
        self.content = content
    }

    init(model: PullLeverRefreshModel) where Content == EmptyView {
        self.model = model
        content = { EmptyView() }
    }

    var body: some View {
        ZStack {
            content()
            Bridge(model: model, label: model.label)
        }
    }

    private struct Bridge: UIViewRepresentable {
        let model: PullLeverRefreshModel
        let label: PullLeverRefreshModel.Label

        func makeUIView(context: Context) -> Surface { Surface(model: model) }

        func updateUIView(_ view: Surface, context: Context) {
            view.updateAccessibility(label)
        }
    }

    @MainActor
    final class Surface: UIView, UIGestureRecognizerDelegate {
        private let model: PullLeverRefreshModel
        private var origin = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocityY = 0.0

        init(model: PullLeverRefreshModel) {
            self.model = model
            super.init(frame: .zero)
            backgroundColor = .clear
            isAccessibilityElement = true
            accessibilityTraits = .button
            accessibilityLabel = "Refresh"
            accessibilityHint = "Pull the lever down, or double tap, to refresh"

            let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            press.minimumPressDuration = 0
            press.allowableMovement = .greatestFiniteMagnitude
            press.cancelsTouchesInView = false
            press.delegate = self
            addGestureRecognizer(press)
        }

        required init?(coder: NSCoder) { nil }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool { true }

        override func accessibilityActivate() -> Bool {
            model.refreshNow()
            return true
        }

        func updateAccessibility(_ label: PullLeverRefreshModel.Label) {
            switch label {
            case .none: accessibilityValue = nil
            case .refreshing: accessibilityValue = "Checking…"
            case .updated: accessibilityValue = "Updated just now"
            }
        }

        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: self)
            let now = ProcessInfo.processInfo.systemUptime
            switch recognizer.state {
            case .began:
                origin = point
                previous = point
                previousTime = now
                velocityY = 0
                model.pointerDown()
            case .changed:
                let dt = now - previousTime
                if dt > 0 { velocityY = (point.y - previous.y) / dt }
                previous = point
                previousTime = now
                model.pointerMoved(drop: point.y - origin.y)
            case .ended, .cancelled, .failed:
                model.pointerMoved(drop: point.y - origin.y)
                if now - previousTime > PullLeverRefreshMotion.Interaction.staleVelocityAfter {
                    velocityY = 0
                }
                model.pointerEnded(cancelled: recognizer.state != .ended, velocityY: velocityY)
            default:
                break
            }
        }
    }
}
