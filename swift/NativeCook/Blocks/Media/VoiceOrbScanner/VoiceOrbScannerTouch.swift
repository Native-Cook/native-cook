import SwiftUI
import UIKit

struct VoiceOrbScannerTouch: UIViewRepresentable {
    var onEvent: (Bool) -> Void
    func makeUIView(context: Context) -> VoiceOrbScannerTouchSurface {
        let view = VoiceOrbScannerTouchSurface()
        view.onEvent = onEvent
        return view
    }
    func updateUIView(_ view: VoiceOrbScannerTouchSurface, context: Context) { view.onEvent = onEvent }
}

// Like Pan(minDistance: 0), begin listening on contact and finalize even on cancellation.
final class VoiceOrbScannerTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onEvent: ((Bool) -> Void)?
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isAccessibilityElement = false
        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView = false
        gesture.delegate = self
        addGestureRecognizer(gesture)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
    @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
        if gesture.state == .began { onEvent?(true) }
        if gesture.state == .ended || gesture.state == .cancelled || gesture.state == .failed { onEvent?(false) }
    }
}
