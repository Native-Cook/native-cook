import SwiftUI
import UIKit

// MARK: - The row's pan

/// The carousel's `Gesture.Pan().maxPointers(1).activeOffsetX([-8, 8]).failOffsetY([-16, 16])`.
///
/// The finger is tracked by a zero-delay press recogniser, not by a SwiftUI `DragGesture`: that is
/// cancelled when the gallery's back swipe begins, after about 24 points of travel, and the drag would
/// end with the finger still down. A recogniser that allows the others beside it keeps its finger
/// wherever it goes (the approach of `EmberTouchView`). The thresholds are applied here: the pan
/// starts once the finger is 8 pt away horizontally, unless it was 16 pt away vertically first, in
/// which case the touch is left to the page's scroll view.
struct SilverScreenCarouselTouchLayer: UIViewRepresentable {
    /// `onStart`.
    var onStart: () -> Void
    /// `onUpdate`, with `translationX` in points.
    var onMove: (CGFloat) -> Void
    /// `onEnd`, with the finger's horizontal velocity in points a second and whether the gesture ended
    /// normally (false when it was cancelled).
    var onEnd: (CGFloat, Bool) -> Void
    /// `onFinalize`: every touch that began ends here, whether or not the pan started.
    var onFinish: () -> Void

    func makeUIView(context: Context) -> SilverScreenCarouselTouchView {
        return SilverScreenCarouselTouchView()
    }

    func updateUIView(_ view: SilverScreenCarouselTouchView, context: Context) {
        view.onStart = onStart
        view.onMove = onMove
        view.onEnd = onEnd
        view.onFinish = onFinish
    }
}

final class SilverScreenCarouselTouchView: UIView, UIGestureRecognizerDelegate {
    var onStart: () -> Void = {}
    var onMove: (CGFloat) -> Void = { _ in }
    var onEnd: (CGFloat, Bool) -> Void = { _, _ in }
    var onFinish: () -> Void = {}

    /// `activeOffsetX([-8, 8])`.
    private static let activeOffsetX: CGFloat = 8
    /// `failOffsetY([-16, 16])`.
    private static let failOffsetY: CGFloat = 16

    private struct Sample {
        let time: CFTimeInterval
        let x: CGFloat
    }

    /// Where the finger came down, in window coordinates (the page may scroll under it).
    private var origin = CGPoint.zero
    private var activated = false
    private var failed = false
    private var samples: [Sample] = []
    private weak var lockedScrollView: UIScrollView?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = false
        let press = UILongPressGestureRecognizer(target: self, action: #selector(track))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = false
        press.delegate = self
        addGestureRecognizer(press)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("SilverScreenCarouselTouchView is created in code")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            unlockScroll()
        }
    }

    func gestureRecognizer(
        _ recognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        return true
    }

    @objc private func track(_ press: UILongPressGestureRecognizer) {
        let point = press.location(in: nil)
        let now = CACurrentMediaTime()
        switch press.state {
        case .began:
            origin = point
            activated = false
            failed = false
            samples = [Sample(time: now, x: point.x)]
        case .changed:
            record(point.x, at: now)
            guard !failed else { return }
            let dx = point.x - origin.x
            let dy = point.y - origin.y
            if !activated {
                if abs(dy) >= Self.failOffsetY {
                    failed = true
                    return
                }
                guard abs(dx) >= Self.activeOffsetX else { return }
                activated = true
                lockScroll()
                onStart()
            }
            onMove(dx)
        case .ended:
            record(point.x, at: now)
            finish(success: true, now: now)
        case .cancelled, .failed:
            finish(success: false, now: now)
        default:
            break
        }
    }

    private func record(_ x: CGFloat, at time: CFTimeInterval) {
        samples.append(Sample(time: time, x: x))
        if samples.count > 16 {
            samples.removeFirst()
        }
    }

    private func finish(success: Bool, now: CFTimeInterval) {
        let wasActive = activated
        activated = false
        failed = false
        unlockScroll()
        if wasActive {
            onEnd(success ? releaseVelocity(now: now) : 0, success)
        }
        onFinish()
    }

    /// The finger's horizontal speed over the last 100 ms before it lifted, in points a second; a finger
    /// that rested before lifting has none.
    private func releaseVelocity(now: CFTimeInterval) -> CGFloat {
        guard let last = samples.last, now - last.time <= 0.1 else { return 0 }
        guard let first = samples.first(where: { last.time - $0.time <= 0.1 }), last.time - first.time > 0.004 else {
            return 0
        }
        return (last.x - first.x) / CGFloat(last.time - first.time)
    }

    /// While the row follows the finger the page does not scroll with it: the scroll view's own pan
    /// is switched off until the touch ends.
    private func lockScroll() {
        var candidate = superview
        while let view = candidate {
            if let scrollView = view as? UIScrollView {
                scrollView.panGestureRecognizer.isEnabled = false
                lockedScrollView = scrollView
                return
            }
            candidate = view.superview
        }
    }

    private func unlockScroll() {
        lockedScrollView?.panGestureRecognizer.isEnabled = true
        lockedScrollView = nil
    }
}

// MARK: - The page's scroll

/// The page's `ScrollView` handlers: `onScrollBeginDrag`, `onScrollEndDrag`, `onMomentumScrollBegin` and
/// `onMomentumScrollEnd`. SwiftUI before iOS 18 does not report them, so the enclosing `UIScrollView`'s
/// pan recogniser is observed (a target added to it, its delegate untouched), and the deceleration
/// that follows a release is watched through `isDecelerating`.
struct SilverScreenCarouselScrollProbe: UIViewRepresentable {
    var onBeginDrag: () -> Void
    var onEndDrag: () -> Void
    var onMomentumBegin: () -> Void
    var onMomentumEnd: () -> Void

    func makeUIView(context: Context) -> SilverScreenCarouselScrollProbeView {
        return SilverScreenCarouselScrollProbeView()
    }

    func updateUIView(_ view: SilverScreenCarouselScrollProbeView, context: Context) {
        view.onBeginDrag = onBeginDrag
        view.onEndDrag = onEndDrag
        view.onMomentumBegin = onMomentumBegin
        view.onMomentumEnd = onMomentumEnd
    }
}

final class SilverScreenCarouselScrollProbeView: UIView {
    var onBeginDrag: () -> Void = {}
    var onEndDrag: () -> Void = {}
    var onMomentumBegin: () -> Void = {}
    var onMomentumEnd: () -> Void = {}

    private weak var scrollView: UIScrollView?
    /// The scroll view's pan began and has not ended yet.
    private var dragging = false
    private var watcher: Task<Void, Never>?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isUserInteractionEnabled = false
        isAccessibilityElement = false
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("SilverScreenCarouselScrollProbeView is created in code")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            detach()
        } else {
            attach()
        }
    }

    private func attach() {
        guard scrollView == nil else { return }
        var candidate = superview
        while let view = candidate {
            if let found = view as? UIScrollView {
                scrollView = found
                found.panGestureRecognizer.addTarget(self, action: #selector(scrollPan))
                return
            }
            candidate = view.superview
        }
    }

    private func detach() {
        scrollView?.panGestureRecognizer.removeTarget(self, action: #selector(scrollPan))
        scrollView = nil
        watcher?.cancel()
        watcher = nil
        dragging = false
    }

    @objc private func scrollPan(_ pan: UIPanGestureRecognizer) {
        switch pan.state {
        case .began:
            dragging = true
            watcher?.cancel()
            onBeginDrag()
        case .ended, .cancelled, .failed:
            guard dragging else { return }
            dragging = false
            onEndDrag()
            watchMomentum()
        default:
            break
        }
    }

    /// After the finger lifts: if the scroll view keeps moving (a fling, or the bounce back from an
    /// overscroll), the quiet timer is held until it stops.
    private func watchMomentum() {
        watcher?.cancel()
        watcher = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(40))
            guard let self, !Task.isCancelled, let scrollView = self.scrollView, scrollView.isDecelerating else { return }
            self.onMomentumBegin()
            while scrollView.isDecelerating {
                try? await Task.sleep(for: .milliseconds(50))
                if Task.isCancelled { return }
            }
            self.onMomentumEnd()
        }
    }
}
