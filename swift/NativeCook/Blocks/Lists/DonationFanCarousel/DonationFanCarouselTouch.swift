import SwiftUI
import UIKit

/// A whole-screen contact surface with the React Native block's pan thresholds.
///
/// - The pan has one finger, becomes active once the finger is more than 8 pt away sideways
///   (`activeOffsetX([-8, 8])`) and fails once it is more than 18 pt away vertically first
///   (`failOffsetY([-18, 18])`). Its translation is measured from the touch-down point.
/// - Stationary presses pause autoplay too; only release or cancellation calls `onFinalize`.
///
/// The recognizers are the arrangement the other blocks' touch surfaces use: a zero-delay contact
/// recognizer next to the custom one, both allowed to recognise at the same time, so the gallery's
/// back-swipe `DragGesture` does not take the drag away.
struct DonationFanCarouselTouchSurface: UIViewRepresentable {
    /// The finger went down.
    let onBegan: () -> Void
    /// The pan's `onUpdate`: points of drag since the touch went down.
    let onPanChanged: (_ translationX: Double) -> Void
    /// The pan's `onEnd`: the release velocity in points per second.
    let onPanEnded: (_ velocityX: Double) -> Void
    /// The primary contact was cancelled; settle without release velocity.
    let onCancelled: () -> Void
    /// The touch ended or was cancelled.
    let onFinalize: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isAccessibilityElement = false

        let contact = UILongPressGestureRecognizer(target: nil, action: nil)
        contact.minimumPressDuration = 0
        contact.allowableMovement = .greatestFiniteMagnitude
        contact.cancelsTouchesInView = false
        contact.delegate = context.coordinator
        view.addGestureRecognizer(contact)

        let recognizer = DonationFanCarouselGestureRecognizer()
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = context.coordinator
        recognizer.onBegan = { [weak coordinator = context.coordinator] in
            coordinator?.parent.onBegan()
        }
        recognizer.onChanged = { [weak coordinator = context.coordinator] translation in
            coordinator?.parent.onPanChanged(translation)
        }
        recognizer.onEnded = { [weak coordinator = context.coordinator] velocity in
            coordinator?.parent.onPanEnded(velocity)
        }
        recognizer.onCancelled = { [weak coordinator = context.coordinator] in
            coordinator?.parent.onCancelled()
        }
        recognizer.onFinalize = { [weak coordinator = context.coordinator] in
            coordinator?.parent.onFinalize()
        }
        view.addGestureRecognizer(recognizer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: DonationFanCarouselTouchSurface

        init(parent: DonationFanCarouselTouchSurface) {
            self.parent = parent
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }
}
