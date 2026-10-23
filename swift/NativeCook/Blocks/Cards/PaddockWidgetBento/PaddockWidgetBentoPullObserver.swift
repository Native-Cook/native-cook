import SwiftUI
import UIKit

/// Watches the enclosing scroll view's own pan (no competing recogniser): reports where the feed was when a drag began
/// and where it was, and how fast the finger moved, when it lifted. The pit stop is "pulled past the top and let go".
struct PaddockWidgetBentoPullObserver: UIViewRepresentable {
    /// Distance scrolled from the top in points (negative while overscrolled), at the start of a drag.
    var onBegan: (Double) -> Void
    /// Distance from the top and the finger's vertical velocity (points/s, positive down) at the lift.
    var onEnded: (Double, Double) -> Void

    func makeUIView(context: Context) -> PullView {
        let view = PullView()
        view.isUserInteractionEnabled = false
        update(view)
        return view
    }

    func updateUIView(_ view: PullView, context: Context) { update(view) }

    private func update(_ view: PullView) {
        view.onBegan = onBegan
        view.onEnded = onEnded
    }

    final class PullView: UIView {
        var onBegan: ((Double) -> Void)?
        var onEnded: ((Double, Double) -> Void)?
        private weak var scrollView: UIScrollView?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            detach()
            guard window != nil else { return }
            var ancestor = superview
            while let view = ancestor {
                if let found = view as? UIScrollView {
                    found.panGestureRecognizer.addTarget(self, action: #selector(pan(_:)))
                    scrollView = found
                    return
                }
                ancestor = view.superview
            }
        }

        private func detach() {
            scrollView?.panGestureRecognizer.removeTarget(self, action: #selector(pan(_:)))
            scrollView = nil
        }

        private func distance(_ scroll: UIScrollView) -> Double { Double(scroll.contentOffset.y + scroll.adjustedContentInset.top) }

        @objc private func pan(_ recognizer: UIPanGestureRecognizer) {
            guard let scroll = scrollView else { return }
            switch recognizer.state {
            case .began: onBegan?(distance(scroll))
            case .ended: onEnded?(distance(scroll), Double(recognizer.velocity(in: scroll).y))
            case .cancelled, .failed: onEnded?(distance(scroll), 0)
            default: break
            }
        }
    }
}

/// A one-finger pan over a small spot (the sticker's corner): translation in points since the touch moved, with the
/// enclosing scroll view told to wait for it, so the drag peels instead of scrolling.
struct PaddockWidgetBentoPanSurface: UIViewRepresentable {
    var onBegan: () -> Void
    var onChanged: (CGSize) -> Void
    var onEnded: () -> Void
    var onTap: () -> Void

    func makeUIView(context: Context) -> PanView {
        let view = PanView()
        update(view)
        return view
    }

    func updateUIView(_ view: PanView, context: Context) { update(view) }

    private func update(_ view: PanView) {
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
        view.onTap = onTap
    }

    final class PanView: UIView {
        var onBegan: (() -> Void)?
        var onChanged: ((CGSize) -> Void)?
        var onEnded: (() -> Void)?
        var onTap: (() -> Void)?
        private weak var coordinated: UIScrollView?
        private lazy var recognizer: UIPanGestureRecognizer = {
            let pan = UIPanGestureRecognizer(target: self, action: #selector(handle(_:)))
            pan.maximumNumberOfTouches = 1
            return pan
        }()

        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .clear
            isAccessibilityElement = false
            addGestureRecognizer(recognizer)
            addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapped)))
        }

        required init?(coder: NSCoder) { nil }

        override func layoutSubviews() {
            super.layoutSubviews()
            var ancestor = superview
            while let view = ancestor {
                if let scroll = view as? UIScrollView {
                    if coordinated !== scroll {
                        scroll.panGestureRecognizer.require(toFail: recognizer)
                        coordinated = scroll
                    }
                    return
                }
                ancestor = view.superview
            }
        }

        @objc private func tapped() { onTap?() }

        @objc private func handle(_ pan: UIPanGestureRecognizer) {
            switch pan.state {
            case .began: onBegan?()
            case .changed:
                let t = pan.translation(in: self)
                onChanged?(CGSize(width: t.x, height: t.y))
            case .ended, .cancelled, .failed: onEnded?()
            default: break
            }
        }
    }
}
