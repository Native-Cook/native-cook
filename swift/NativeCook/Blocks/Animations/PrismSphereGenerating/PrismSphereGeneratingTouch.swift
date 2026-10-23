import SwiftUI
import UIKit

/// A zero-delay recognizer keeps trackball drags alive alongside the gallery's edge recognizer.
struct PrismSphereGeneratingTouch: UIViewRepresentable {
    let motion: PrismSphereGeneratingMotion
    let radius: Double
    let k: Double

    func makeUIView(context: Context) -> PrismSphereGeneratingTouchView {
        let view = PrismSphereGeneratingTouchView()
        view.motion = motion
        view.radius = radius
        view.k = k
        view.isAccessibilityElement = true
        view.accessibilityLabel = "Light sphere"
        view.accessibilityHint = "Double tap to send a pulse of light through it"
        view.accessibilityTraits = .button
        return view
    }

    func updateUIView(_ view: PrismSphereGeneratingTouchView, context: Context) {
        view.motion = motion
        view.radius = radius
        view.k = k
        view.accessibilityValue = motion.down ? "Gathering light" : "Ready"
    }
}

final class PrismSphereGeneratingTouchView: UIView, UIGestureRecognizerDelegate {
    var motion: PrismSphereGeneratingMotion?
    var radius = 124.0
    var k = 1.0
    private var origin = CGPoint.zero
    private var previousPoint = CGPoint.zero
    private var previousTime = 0.0
    private var velocity = SIMD2<Double>.zero
    private let impact = UIImpactFeedbackGenerator(style: .medium)

    override init(frame: CGRect) {
        super.init(frame: frame)
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(touch(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        backgroundColor = .clear
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        hypot(point.x - bounds.midX, point.y - bounds.midY) <= bounds.width / 2
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func touch(_ recognizer: UILongPressGestureRecognizer) {
        guard let motion else { return }
        let point = recognizer.location(in: self)
        let now = motion.elapsed(.now)
        let centred = SIMD2(Double(point.x - bounds.midX), Double(point.y - bounds.midY))
        motion.onImpact = { [weak self] in self?.impact.impactOccurred() }
        switch recognizer.state {
        case .began:
            origin = point
            previousPoint = point
            previousTime = now
            velocity = .zero
            impact.prepare()
            motion.begin(at: now, point: centred, radius: radius)
        case .changed:
            let dt = now - previousTime
            if dt > 0 {
                velocity = SIMD2(Double(point.x - previousPoint.x), Double(point.y - previousPoint.y)) / dt
            }
            previousPoint = point
            previousTime = now
            motion.move(at: now, point: centred,
                        translation: SIMD2(Double(point.x - origin.x), Double(point.y - origin.y)), radius: radius, k: k)
        case .ended, .cancelled, .failed:
            if now - previousTime > 0.1 { velocity = .zero }
            motion.end(at: now, velocity: velocity, radius: radius, success: recognizer.state == .ended)
        default:
            break
        }
    }

    override func accessibilityActivate() -> Bool {
        guard let motion else { return false }
        motion.onImpact = { [weak self] in self?.impact.impactOccurred() }
        motion.activate(at: motion.elapsed(.now))
        return true
    }
}
