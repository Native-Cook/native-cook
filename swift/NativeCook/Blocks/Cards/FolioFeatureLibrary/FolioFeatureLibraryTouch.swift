import SwiftUI
import UIKit

/// The root's `onTouchStart`, `onTouchEnd` and `onTouchCancel` (FolioFeatureLibrary.tsx:490): every touch on the block
/// interrupts the loop, whatever view it lands on, and the loop comes back 2 s after the last finger lifts.
///
/// The finger is tracked by a zero-delay press recogniser on the window that allows every other recogniser beside it
/// and cancels no touch, so the gallery's back swipe, the scroll views and the buttons keep working and none of
/// them ends the hold (see EmberTouchView). Only touches that land inside this view count. The view also tells the
/// engine which UIView is the root, for `measureInWindow`.
struct FolioFeatureLibraryTouchLayer: UIViewRepresentable {
    let engine: FolioFeatureLibraryEngine

    func makeUIView(context: Context) -> TouchView {
        let view = TouchView()
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        view.engine = engine
        engine.rootView = view
        return view
    }

    func updateUIView(_ view: TouchView, context: Context) {
        view.engine = engine
        engine.rootView = view
    }

    static func dismantleUIView(_ view: TouchView, coordinator: ()) {
        view.detach()
    }

    final class TouchView: UIView, UIGestureRecognizerDelegate {
        weak var engine: FolioFeatureLibraryEngine?
        private var press: UILongPressGestureRecognizer?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            detach()
            guard let window = window else { return }
            let recogniser = UILongPressGestureRecognizer(target: self, action: #selector(track(_:)))
            recogniser.minimumPressDuration = 0
            recogniser.allowableMovement = .greatestFiniteMagnitude
            recogniser.cancelsTouchesInView = false
            recogniser.delaysTouchesBegan = false
            recogniser.delaysTouchesEnded = false
            recogniser.delegate = self
            window.addGestureRecognizer(recogniser)
            press = recogniser
        }

        func detach() {
            if let recogniser = press {
                recogniser.view?.removeGestureRecognizer(recogniser)
            }
            press = nil
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            bounds.contains(touch.location(in: self))
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func track(_ recogniser: UILongPressGestureRecognizer) {
            switch recogniser.state {
            case .began:
                engine?.touchBegan()
            case .ended, .cancelled, .failed:
                engine?.touchEnded()
            default:
                break
            }
        }

        deinit {
            if let recogniser = press {
                recogniser.view?.removeGestureRecognizer(recogniser)
            }
        }
    }
}
