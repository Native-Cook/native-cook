import UIKit

final class GoldEmblemMembershipTouchSurface: UIView {
    var onEvent: ((GoldEmblemMembershipTouch.Event, Double, Double, Double) -> Void)?
    private var finalized = true
    override init(frame: CGRect) {
        super.init(frame: frame); isOpaque = false; backgroundColor = .clear
        let pan = GoldEmblemMembershipPan(target: self, action: #selector(handle(_:)))
        pan.onBegin = { [weak self] in
            self?.finalized = false; self?.onEvent?(.begin, 0, 0, 0)
        }
        pan.onInactiveEnd = { [weak self, weak pan] in
            if let pan { self?.finishGesture(pan) }
        }
        addGestureRecognizer(pan)
    }
    required init?(coder: NSCoder) { nil }
    private func finishGesture(_ pan: UIPanGestureRecognizer) {
        guard !finalized else { return }; finalized = true
        let p = pan.translation(in: window), v = pan.velocity(in: window)
        onEvent?(.finalize, p.x, p.y, v.x)
    }
    @objc private func handle(_ pan: UIPanGestureRecognizer) {
        let p = pan.translation(in: window), v = pan.velocity(in: window)
        switch pan.state {
        case .began: onEvent?(.start, p.x, p.y, v.x)
        case .changed: onEvent?(.update, p.x, p.y, v.x)
        case .ended: onEvent?(.end, p.x, p.y, v.x); finishGesture(pan)
        case .failed, .cancelled: finishGesture(pan)
        default: break
        }
    }
}
