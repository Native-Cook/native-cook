import SwiftUI
import UIKit
import QuartzCore

struct DayOneMedalTouchSurface: UIViewRepresentable {
    let model: DayOneMedalModel
    let header: Bool
    let originY: Double

    func makeUIView(context: Context) -> DayOneMedalTouchView {
        let view = DayOneMedalTouchView()
        view.model = model
        view.header = header
        view.originY = originY
        return view
    }

    func updateUIView(_ view: DayOneMedalTouchView, context: Context) {
        view.model = model
        view.originY = originY
    }
}

final class DayOneMedalTouchView: UIView, UIGestureRecognizerDelegate {
    weak var model: DayOneMedalModel?
    var header = false
    var originY = 0.0
    private var previous = CGPoint.zero
    private var start = CGPoint.zero
    private var previousTime = 0.0
    private var velocity = CGPoint.zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(changed(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView = false
        gesture.delegate = self
        addGestureRecognizer(gesture)
    }

    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func changed(_ gesture: UILongPressGestureRecognizer) {
        let local = gesture.location(in: self)
        let point = CGPoint(x: local.x, y: local.y + originY)
        let time = CACurrentMediaTime()
        switch gesture.state {
        case .began:
            previous = point
            start = point
            previousTime = time
            velocity = .zero
            if header { model?.header(0, ended: false, velocity: 0) }
            else { model?.touch(point) }
        case .changed:
            let dt = max(0.001, time - previousTime)
            let delta = CGPoint(x: point.x - previous.x, y: point.y - previous.y)
            velocity = CGPoint(x: delta.x / dt, y: delta.y / dt)
            if header { model?.header(point.y - start.y, ended: false, velocity: velocity.y) }
            else { model?.move(point, delta: delta) }
            previous = point
            previousTime = time
        case .ended, .cancelled, .failed:
            if time - previousTime > 0.1 { velocity = .zero }
            if header { model?.header(point.y - start.y, ended: true, velocity: velocity.y) }
            else { model?.release(velocity, cancelled: gesture.state != .ended) }
        default: break
        }
    }
}
