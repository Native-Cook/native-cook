import SwiftUI
import UIKit

/// Reports the drags of the enclosing `UIScrollView` (the `onScrollBeginDrag`, `onScrollEndDrag`,
/// `onMomentumScrollBegin` and `onMomentumScrollEnd` of React Native's ScrollView) by listening to its own
/// pan recogniser, so SwiftUI's `ScrollView` keeps every behaviour it has.
struct ConfettiGiftOfferScrollWatcher: UIViewRepresentable {
    var onDragBegan: () -> Void
    var onDragEnded: () -> Void = {}
    var onMomentumBegan: () -> Void = {}
    var onMomentumEnded: () -> Void = {}

    func makeUIView(context: Context) -> WatcherView {
        let view = WatcherView()
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        view.handlers = self
        return view
    }

    func updateUIView(_ view: WatcherView, context: Context) {
        view.handlers = self
        view.attachIfNeeded()
    }

    final class WatcherView: UIView {
        var handlers: ConfettiGiftOfferScrollWatcher?
        private weak var scroll: UIScrollView?
        private var link: CADisplayLink?
        private var decelerating = false
        private var graceFrames = 0

        override func didMoveToWindow() {
            super.didMoveToWindow()
            attachIfNeeded()
            if window == nil { stopLink() }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            attachIfNeeded()
        }

        func attachIfNeeded() {
            guard scroll == nil else { return }
            var ancestor = superview
            while let view = ancestor, !(view is UIScrollView) { ancestor = view.superview }
            guard let found = ancestor as? UIScrollView else { return }
            scroll = found
            found.panGestureRecognizer.addTarget(self, action: #selector(pan(_:)))
        }

        @objc private func pan(_ recognizer: UIPanGestureRecognizer) {
            switch recognizer.state {
            case .began:
                handlers?.onDragBegan()
            case .ended, .cancelled, .failed:
                handlers?.onDragEnded()
                // Momentum (or a bounce back) begins right after the finger lifts; watch for it.
                graceFrames = 0
                startLink()
            default:
                break
            }
        }

        private func startLink() {
            guard link == nil else { return }
            let displayLink = CADisplayLink(target: self, selector: #selector(tick))
            displayLink.add(to: .main, forMode: .common)
            link = displayLink
        }

        private func stopLink() {
            link?.invalidate()
            link = nil
            if decelerating {
                decelerating = false
                handlers?.onMomentumEnded()
            }
        }

        @objc private func tick() {
            guard let scroll else { stopLink(); return }
            if scroll.isDecelerating {
                if !decelerating {
                    decelerating = true
                    handlers?.onMomentumBegan()
                }
            } else if decelerating {
                stopLink()
            } else {
                graceFrames += 1
                if graceFrames > 6 { stopLink() }
            }
        }

        deinit {
            link?.invalidate()
        }
    }
}

/// The skip sheet's drag handle: `Gesture.Pan().maxPointers(1).activeOffsetY([-8, 8])` of the React Native block.
/// A pan recogniser that lets every other recogniser run beside it, so the gallery's back swipe and the sheet's
/// own scroll view neither cancel nor are cancelled by it (see EmberTouchView).
struct ConfettiGiftOfferHandlePan: UIViewRepresentable {
    var onBegan: () -> Void
    var onChanged: (Double) -> Void
    var onEnded: (Double, Bool) -> Void

    func makeUIView(context: Context) -> PanView {
        let view = PanView()
        view.backgroundColor = .clear
        view.isAccessibilityElement = false
        view.owner = self
        return view
    }

    func updateUIView(_ view: PanView, context: Context) {
        view.owner = self
    }

    final class PanView: UIView, UIGestureRecognizerDelegate {
        var owner: ConfettiGiftOfferHandlePan?

        override init(frame: CGRect) {
            super.init(frame: frame)
            let pan = UIPanGestureRecognizer(target: self, action: #selector(track(_:)))
            pan.maximumNumberOfTouches = 1
            pan.cancelsTouchesInView = false
            pan.delegate = self
            addGestureRecognizer(pan)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("ConfettiGiftOfferHandlePan.PanView is created in code")
        }

        func gestureRecognizer(
            _ recognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func track(_ pan: UIPanGestureRecognizer) {
            let translation = Double(pan.translation(in: self).y)
            switch pan.state {
            case .began:
                owner?.onBegan()
                owner?.onChanged(translation)
            case .changed:
                owner?.onChanged(translation)
            case .ended:
                owner?.onEnded(Double(pan.velocity(in: self).y), true)
            case .cancelled, .failed:
                owner?.onEnded(0, false)
            default:
                break
            }
        }
    }
}
