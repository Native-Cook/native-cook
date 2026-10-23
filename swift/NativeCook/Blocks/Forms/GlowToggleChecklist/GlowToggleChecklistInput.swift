import SwiftUI
import UIKit

struct GlowToggleChecklistInput: UIViewRepresentable {
    let frame: GlowToggleChecklistSimulation
    let size: CGSize
    let onTouch: (GlowToggleChecklistTouch) -> Void

    func makeUIView(context: Context) -> GlowToggleChecklistInputView {
        GlowToggleChecklistInputView()
    }

    func updateUIView(_ view: GlowToggleChecklistInputView, context: Context) {
        view.update(frame: frame, size: size, onTouch: onTouch)
    }
}

final class GlowToggleChecklistInputView: UIView {
    private let controls = (-1..<4).map { GlowToggleChecklistControl(control: $0) }

    init() {
        super.init(frame: .zero)
        backgroundColor = .clear
        // The cap remains above the emerging rows, as in the source canvas.
        for control in controls.reversed() { addSubview(control) }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        controls.contains { !$0.isHidden && $0.frame.contains(point) }
    }

    func update(frame: GlowToggleChecklistSimulation, size: CGSize, onTouch: @escaping (GlowToggleChecklistTouch) -> Void) {
        let k = GlowToggleChecklistSurface.scale(size)
        let capY = size.height / 2 - 158 * k * frame.open
        let bottom = capY + (30 + 316 * frame.open) * k
        for control in controls {
            let index = control.control
            let cap = index == -1
            let width = (cap ? 288 : 284) * k
            let height = (cap ? 60 : 68) * k
            let y = cap ? capY : bottom - [278.0, 210, 142, 74][index] * k
            control.frame = CGRect(x: (size.width - width) / 2, y: y - height / 2, width: width, height: height)
            control.k = k
            control.onTouch = onTouch
            control.isHidden = frame.present < 1 || (!cap && frame.open < 0.98)
            control.accessibilityLabel = cap ? "Success checklist" : GlowToggleChecklistSurface.labels[index].capitalized
            control.accessibilityValue = cap ? (frame.openTarget > 0 ? "Expanded" : "Collapsed") : (frame.knobs[index].on ? "On" : "Off")
            control.accessibilityHint = cap ? (frame.openTarget > 0 ? "Closes the checklist" : "Opens the checklist") : "Double tap to toggle"
            control.accessibilityTraits = cap ? .button : [.button, .toggleButton]
        }
    }
}

/// Zero-delay long press survives the gallery's competing back gesture.
final class GlowToggleChecklistControl: UIView, UIGestureRecognizerDelegate {
    let control: Int
    var k = 1.0
    var onTouch: ((GlowToggleChecklistTouch) -> Void)?
    private var startX = 0.0
    private var lastX = 0.0
    private var lastTime = 0.0
    private var velocity = 0.0
    private var cancelled = false

    init(control: Int) {
        self.control = control
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityIdentifier = control == -1 ? "glow-checklist-cap" : "glow-checklist-row-\(control)"
        let touch = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        touch.minimumPressDuration = 0
        touch.allowableMovement = .greatestFiniteMagnitude
        touch.numberOfTouchesRequired = 1
        touch.delegate = self
        addGestureRecognizer(touch)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    override func accessibilityActivate() -> Bool {
        onTouch?(.init(control: control, down: false, k: k, activate: true))
        return true
    }

    @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
        let x = gesture.location(in: window).x
        let now = CACurrentMediaTime()
        let local = gesture.location(in: self)
        let outside = control == -1 && !bounds.insetBy(dx: -16, dy: -16).contains(local)
        switch gesture.state {
        case .began:
            startX = x
            lastX = x
            lastTime = now
            velocity = 0
            cancelled = false
            onTouch?(.init(control: control, down: true, k: k))
        case .changed:
            guard !cancelled else { return }
            velocity = (x - lastX) / max(1.0 / 240, now - lastTime)
            lastX = x
            lastTime = now
            cancelled = outside
            onTouch?(.init(control: control, down: true, dx: x - startX, velocity: velocity, k: k, cancel: cancelled))
        case .ended, .cancelled, .failed:
            let releaseVelocity = now - lastTime > 0.08 ? 0 : velocity
            onTouch?(.init(control: control, down: false, dx: x - startX, velocity: releaseVelocity,
                           k: k, cancel: cancelled || outside || gesture.state != .ended))
        default: break
        }
    }
}
