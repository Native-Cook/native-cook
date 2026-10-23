import SwiftUI
import UIKit

/// Sees every touch in the window without taking it: began on touch down, ended when it lifts or is cancelled.
/// (A raw SwiftUI DragGesture is cancelled by the gallery's back-swipe after a few points.)
struct ForYouInsightsTouchProbe: UIViewRepresentable {
    var onBegan: () -> Void
    var onEnded: () -> Void

    func makeUIView(context: Context) -> ProbeView {
        let view = ProbeView()
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: ProbeView, context: Context) {
        view.onBegan = onBegan
        view.onEnded = onEnded
    }

    final class ProbeView: UIView, UIGestureRecognizerDelegate {
        var onBegan: (() -> Void)?
        var onEnded: (() -> Void)?
        private lazy var recognizer: UILongPressGestureRecognizer = {
            let r = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            r.minimumPressDuration = 0
            r.allowableMovement = .greatestFiniteMagnitude
            r.cancelsTouchesInView = false
            r.delaysTouchesBegan = false
            r.delaysTouchesEnded = false
            r.delegate = self
            return r
        }()
        private weak var host: UIView?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            host?.removeGestureRecognizer(recognizer)
            host = window
            window?.addGestureRecognizer(recognizer)
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        @objc private func handle(_ r: UILongPressGestureRecognizer) {
            switch r.state {
            case .began: onBegan?()
            case .ended, .cancelled, .failed: onEnded?()
            default: break
            }
        }
    }
}

/// A zero-delay drag surface for the hero: translation from touch down (updates after 4 pt), release velocity.
struct ForYouInsightsHeroPan: UIViewRepresentable {
    var onChanged: (CGPoint) -> Void
    var onEnded: (CGPoint, CGFloat) -> Void
    var onCancelled: () -> Void

    func makeUIView(context: Context) -> PanView {
        let view = PanView()
        view.isAccessibilityElement = false
        return view
    }

    func updateUIView(_ view: PanView, context: Context) {
        view.onChanged = onChanged
        view.onEnded = onEnded
        view.onCancelled = onCancelled
    }

    final class PanView: UIView, UIGestureRecognizerDelegate {
        var onChanged: ((CGPoint) -> Void)?
        var onEnded: ((CGPoint, CGFloat) -> Void)?
        var onCancelled: (() -> Void)?
        private var startPoint = CGPoint.zero
        private var active = false
        private var samples: [(Double, CGFloat)] = []

        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .clear
            let r = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            r.minimumPressDuration = 0
            r.allowableMovement = .greatestFiniteMagnitude
            r.cancelsTouchesInView = false
            r.delegate = self
            addGestureRecognizer(r)
        }

        required init?(coder: NSCoder) { nil }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        @objc private func handle(_ r: UILongPressGestureRecognizer) {
            let p = r.location(in: nil)
            switch r.state {
            case .began:
                startPoint = p
                active = false
                samples = [(CACurrentMediaTime(), p.y)]
            case .changed:
                let d = CGPoint(x: p.x - startPoint.x, y: p.y - startPoint.y)
                if !active, hypot(d.x, d.y) >= 4 { active = true }
                samples.append((CACurrentMediaTime(), p.y))
                if samples.count > 8 { samples.removeFirst() }
                if active { onChanged?(d) }
            case .ended:
                let d = CGPoint(x: p.x - startPoint.x, y: p.y - startPoint.y)
                var v: CGFloat = 0
                let now = CACurrentMediaTime()
                if let first = samples.first(where: { now - $0.0 < 0.1 }), let last = samples.last, last.0 > first.0 {
                    v = (last.1 - first.1) / CGFloat(last.0 - first.0)
                }
                if active { onEnded?(d, v) } else { onCancelled?() }
                active = false
            case .cancelled, .failed:
                if active { onEnded?(CGPoint(x: p.x - startPoint.x, y: p.y - startPoint.y), 0) } else { onCancelled?() }
                active = false
            default: break
            }
        }
    }
}
