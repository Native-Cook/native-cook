import SwiftUI
import UIKit

// MARK: - Page pan

/// `Gesture.Pan().enabled(!previewing).activeOffsetX([-10, 10]).failOffsetY([-18, 18]).maxPointers(1)`: the
/// horizontal drag that moves the pages.
///
/// A pan recogniser that allows every other recogniser beside it, so the gallery's back swipe and the page's
/// own scroll view neither cancel nor are cancelled by it (the approach of `EmberTouchView`). It begins only for
/// a drag that is more horizontal than vertical, which is what the offsets amount to.
struct QuietSignalOnboardingPan: UIViewRepresentable {
    var enabled: Bool
    var onBegan: () -> Void
    var onChanged: (Double) -> Void
    /// Translation and velocity along x, and whether the gesture ended rather than being cancelled.
    var onEnded: (Double, Double, Bool) -> Void

    func makeUIView(context: Context) -> QuietSignalOnboardingPanView {
        let view = QuietSignalOnboardingPanView()
        view.owner = self
        return view
    }

    func updateUIView(_ view: QuietSignalOnboardingPanView, context: Context) {
        view.owner = self
        view.pan.isEnabled = enabled
    }
}

final class QuietSignalOnboardingPanView: UIView, UIGestureRecognizerDelegate {
    var owner: QuietSignalOnboardingPan?
    let pan = UIPanGestureRecognizer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = false
        pan.addTarget(self, action: #selector(track(_:)))
        pan.maximumNumberOfTouches = 1
        pan.cancelsTouchesInView = false
        pan.delegate = self
        addGestureRecognizer(pan)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("QuietSignalOnboardingPanView is created in code")
    }

    func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
        guard recognizer === pan else { return true }
        let travelled = pan.translation(in: self)
        return abs(travelled.x) >= abs(travelled.y)
    }

    func gestureRecognizer(
        _ recognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func track(_ recognizer: UIPanGestureRecognizer) {
        let translation = Double(recognizer.translation(in: self).x)
        let velocity = Double(recognizer.velocity(in: self).x)
        switch recognizer.state {
        case .began:
            owner?.onBegan()
            owner?.onChanged(translation)
        case .changed:
            owner?.onChanged(translation)
        case .ended:
            owner?.onEnded(translation, velocity, true)
        case .cancelled, .failed:
            owner?.onEnded(translation, velocity, false)
        default:
            break
        }
    }
}

// MARK: - Reading the scroll view

/// The handlers of `readingHandlers` in usePlayback: a finger down or up anywhere on the scroll view
/// (`onTouchStart`, `onTouchEnd`, `onTouchCancel`) and its drag and momentum (`onScrollBeginDrag`,
/// `onScrollEndDrag`, `onMomentumScrollBegin`, `onMomentumScrollEnd`).
struct QuietSignalOnboardingScrollEvents {
    var touchBegan: () -> Void
    var touchEnded: () -> Void
    var dragBegan: () -> Void
    var dragEnded: () -> Void
    var momentumBegan: () -> Void
    var momentumEnded: () -> Void
}

/// Reports those events of the enclosing `UIScrollView` by listening to its own pan recogniser and by adding a
/// zero-delay press recogniser next to it, so SwiftUI's `ScrollView` keeps every behaviour it has. Both allow
/// the other recognisers to run beside them.
struct QuietSignalOnboardingScrollWatcher: UIViewRepresentable {
    var events: QuietSignalOnboardingScrollEvents

    func makeUIView(context: Context) -> QuietSignalOnboardingWatcherView {
        let view = QuietSignalOnboardingWatcherView()
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        view.events = events
        return view
    }

    func updateUIView(_ view: QuietSignalOnboardingWatcherView, context: Context) {
        view.events = events
        view.attachIfNeeded()
    }
}

final class QuietSignalOnboardingWatcherView: UIView, UIGestureRecognizerDelegate {
    var events: QuietSignalOnboardingScrollEvents?
    private weak var scroll: UIScrollView?
    private var press: UILongPressGestureRecognizer?
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
        found.panGestureRecognizer.addTarget(self, action: #selector(handlePan(_:)))
        let contact = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
        contact.minimumPressDuration = 0
        contact.allowableMovement = .greatestFiniteMagnitude
        contact.cancelsTouchesInView = false
        contact.delegate = self
        found.addGestureRecognizer(contact)
        press = contact
    }

    func gestureRecognizer(
        _ recognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
        switch recognizer.state {
        case .began:
            events?.touchBegan()
        case .ended, .cancelled, .failed:
            events?.touchEnded()
        default:
            break
        }
    }

    @objc private func handlePan(_ recognizer: UIPanGestureRecognizer) {
        switch recognizer.state {
        case .began:
            events?.dragBegan()
        case .ended, .cancelled, .failed:
            events?.dragEnded()
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
            events?.momentumEnded()
        }
    }

    @objc private func tick() {
        guard let scroll else {
            stopLink()
            return
        }
        if scroll.isDecelerating {
            if !decelerating {
                decelerating = true
                events?.momentumBegan()
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
        if let press, let scroll {
            scroll.removeGestureRecognizer(press)
        }
    }
}
