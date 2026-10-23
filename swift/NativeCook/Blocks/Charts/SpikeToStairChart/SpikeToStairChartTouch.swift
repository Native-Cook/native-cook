import SwiftUI
import UIKit

/// A zero-delay long press survives the gallery's simultaneous edge-pan recognizer.
struct SpikeToStairChartTouch: UIViewRepresentable {
    let motion: SpikeToStairChartMotion
    let scale: Double
    let origin: CGPoint
    let selected: Int
    let value: String

    func makeUIView(context: Context) -> Surface { Surface(motion: motion) }

    func updateUIView(_ view: Surface, context: Context) {
        view.scale = scale
        view.origin = origin
        view.scrubber.accessibilityValue = value
        for i in 0...1 {
            view.buttons[i].accessibilityTraits = i == selected ? [.button,.selected] : [.button]
        }
        view.setNeedsLayout()
    }

    final class Scrubber: UIView {
        var adjust: ((Int) -> Void)?
        override func accessibilityIncrement() { adjust?(1) }
        override func accessibilityDecrement() { adjust?(-1) }
    }

    final class Surface: UIView, UIGestureRecognizerDelegate {
        let motion: SpikeToStairChartMotion
        let scrubber = Scrubber()
        let buttons = [UIButton(type: .custom), UIButton(type: .custom)]
        var scale = 1.0
        var origin = CGPoint.zero
        private var previousX = 0.0
        private var previousTime = 0.0
        private var velocity = 0.0

        init(motion: SpikeToStairChartMotion) {
            self.motion = motion
            super.init(frame: .zero)
            backgroundColor = .clear
            scrubber.backgroundColor = .clear
            scrubber.isAccessibilityElement = true
            scrubber.accessibilityLabel = "Chart scrubber"
            scrubber.accessibilityTraits = .adjustable
            scrubber.adjust = { [weak motion] direction in motion?.adjust(direction) }
            addSubview(scrubber)
            let hold = UILongPressGestureRecognizer(target: self,action: #selector(scrub(_:)))
            hold.minimumPressDuration = 0
            hold.allowableMovement = .greatestFiniteMagnitude
            hold.cancelsTouchesInView = false
            hold.delegate = self
            scrubber.addGestureRecognizer(hold)
            for i in 0...1 {
                let button = buttons[i]
                button.tag = i
                button.accessibilityLabel = i == 1 ? "Show discipline" : "Show motivation"
                button.addTarget(self,action: #selector(pressDown(_:)),for: .touchDown)
                button.addTarget(self,action: #selector(pressUp(_:)),for: .touchUpInside)
                button.addTarget(self,action: #selector(cancelPress(_:)),for: [.touchCancel,.touchUpOutside])
                addSubview(button)
            }
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        override func layoutSubviews() {
            super.layoutSubviews()
            scrubber.frame = CGRect(x: origin.x+40*scale,y: origin.y+180*scale,width: 322*scale,height: 380*scale)
            for i in 0...1 {
                let height = max(44,44*scale)
                buttons[i].frame = CGRect(x: origin.x+Double(133+68*i)*scale,y: origin.y+792*scale-height/2,width: 68*scale,height: height)
            }
        }

        override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
            scrubber.frame.contains(point) || buttons.contains { $0.frame.contains(point) }
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        @objc private func pressDown(_ button: UIButton) { motion.press(button.tag,on: true) }
        @objc private func pressUp(_ button: UIButton) {
            motion.press(button.tag,on: false)
            motion.go(button.tag)
        }
        @objc private func cancelPress(_ button: UIButton) { motion.press(button.tag,on: false) }

        @objc private func scrub(_ recognizer: UILongPressGestureRecognizer) {
            let x = (recognizer.location(in: self).x-origin.x)/scale
            let now = ProcessInfo.processInfo.systemUptime
            switch recognizer.state {
            case .began:
                previousX = x
                previousTime = now
                velocity = 0
                motion.scrubBegan(x: x)
            case .changed:
                if now > previousTime { velocity = (x-previousX)/(now-previousTime) }
                previousX = x
                previousTime = now
                motion.scrubMoved(x: x)
            case .ended, .cancelled, .failed:
                motion.scrubEnded(velocity: now-previousTime > 0.1 ? 0 : velocity)
            default: break
            }
        }
    }
}
