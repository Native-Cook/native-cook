import SwiftUI
import UIKit

/// A zero-delay recognizer remains simultaneous with the gallery's edge-back gesture.
struct IconMorphSheetTouch: UIViewRepresentable {
    let motion: IconMorphSheetMotion

    func makeUIView(context: Context) -> Surface { Surface(motion: motion) }
    func updateUIView(_ view: Surface, context: Context) { view.updateSemantics() }

    final class Element: UIAccessibilityElement {
        var activate: (() -> Void)?
        var adjust: ((Int) -> Void)?
        override func accessibilityActivate() -> Bool {
            activate?()
            return activate != nil
        }
        override func accessibilityIncrement() { adjust?(1) }
        override func accessibilityDecrement() { adjust?(-1) }
    }

    final class Surface: UIView, UIGestureRecognizerDelegate {
        let motion: IconMorphSheetMotion
        var origin = CGPoint.zero
        var previous = CGPoint.zero
        var previousTime = 0.0
        var velocity = CGPoint.zero
        var target = -1
        var elements: [Element] = []

        init(motion: IconMorphSheetMotion) {
            self.motion = motion
            super.init(frame: .zero)
            backgroundColor = .clear
            let press = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            press.minimumPressDuration = 0
            press.allowableMovement = .greatestFiniteMagnitude
            press.cancelsTouchesInView = false
            press.delegate = self
            addGestureRecognizer(press)
            for _ in 0..<8 { elements.append(Element(accessibilityContainer: self)) }
            isAccessibilityElement = false
        }

        required init?(coder: NSCoder) { nil }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        func closeRect() -> CGRect {
            let l = motion.layout
            let top = l.height - l.sheetHeight(motion.frame.position) + l.travel(motion.frame, reduced: motion.reduced)
            return CGRect(x: l.width - 54, y: top + 6, width: 44, height: 44)
        }

        @objc func handle(_ recognizer: UILongPressGestureRecognizer) {
            guard motion.visible else { return }
            motion.advance(to: .now)
            let p = recognizer.location(in: self)
            let l = motion.layout
            switch recognizer.state {
            case .began:
                origin = p
                previous = p
                previousTime = motion.clock
                velocity = .zero
                motion.ambientDown()
                if l.button.contains(p) {
                    target = 3
                    motion.buttonDown()
                } else if motion.frame.open > 0 && closeRect().contains(p) {
                    target = 0
                    motion.puckDown(0)
                } else if CGRect(x: 12, y: l.headerY - 22, width: 44, height: 44).contains(p) {
                    target = 1
                    motion.puckDown(1)
                } else if CGRect(x: l.width - 56, y: l.headerY - 22, width: 44, height: 44).contains(p) {
                    target = 2
                    motion.puckDown(2)
                } else {
                    target = 4
                    motion.panBegin(x: p.x, y: p.y)
                }
            case .changed:
                let dt = max(1 / 240, motion.clock - previousTime)
                let weight = 1 - exp(-dt / 0.035)
                velocity.x += ((p.x - previous.x) / dt - velocity.x) * weight
                velocity.y += ((p.y - previous.y) / dt - velocity.y) * weight
                previous = p
                previousTime = motion.clock
                if target == 4 { motion.panChange(x: p.x - origin.x, y: p.y - origin.y) }
            case .ended, .cancelled, .failed:
                let success = recognizer.state == .ended
                if motion.clock - previousTime > 0.1 { velocity = .zero }
                if target == 3 { motion.buttonUp(inside: success && l.button.insetBy(dx: -28, dy: -28).contains(p)) }
                else if target >= 0 && target <= 2 {
                    let hit: CGRect = target == 0 ? closeRect() : CGRect(x: target == 1 ? 12 : l.width - 56, y: l.headerY - 22, width: 44, height: 44)
                    motion.puckUp(inside: success && hit.insetBy(dx: -16, dy: -16).contains(p), puck: target)
                } else if target == 4 {
                    if success {
                        motion.panChange(x: p.x - origin.x, y: p.y - origin.y)
                        motion.iconUp()
                        motion.panFinalize(velocityX: velocity.x, velocityY: velocity.y)
                    } else { motion.panCancel() }
                }
                motion.ambientUp()
                target = -1
            default: break
            }
        }

        func updateSemantics() {
            guard motion.visible else {
                accessibilityElements = []
                return
            }
            let l = motion.layout
            let f = motion.frame
            let step = max(0, min(3, Int(f.position.rounded())))
            let open = f.open > 0.001
            let label = f.labelOpen < 0.5 ? 0 : f.position < 2.5 ? 1 : 2
            func configure(_ index: Int, _ label: String, _ rect: CGRect, _ traits: UIAccessibilityTraits, _ action: (() -> Void)? = nil) {
                let element = elements[index]
                element.accessibilityLabel = label
                element.accessibilityFrameInContainerSpace = rect
                element.accessibilityTraits = traits
                element.activate = action
            }
            configure(0, IconMorphSheetAssets.labels[label], l.button, .button) { [weak self] in
                guard let self else { return }
                motion.ambientDown()
                motion.buttonDown()
                motion.buttonUp(inside: true)
                motion.ambientUp()
            }
            configure(1, "Close tips", closeRect(), .button) { [weak self] in self?.motion.closeTap() }
            configure(2, IconMorphSheetAssets.titles[step] + ". " + IconMorphSheetAssets.subtitles[step].joined(separator: " "), CGRect(x: 16, y: l.stageTop - 88, width: l.width - 32, height: 80), .header)
            configure(3, IconMorphSheetAssets.descriptions[step], CGRect(x: 0, y: l.stageTop, width: l.width, height: l.stageHeight), [.image, .button]) { [weak self] in
                self?.motion.iconDown()
                self?.motion.iconUp()
            }
            elements[3].accessibilityHint = "Press and hold to squash the clay icon."
            configure(4, "Tips", CGRect(x: l.width / 2 - 30, y: l.buttonTop - 36, width: 60, height: 18), .adjustable)
            elements[4].accessibilityValue = "Step \(step + 1) of 4"
            elements[4].adjust = { [weak self] direction in
                guard let self else { return }
                motion.ambientDown()
                motion.panBegin(x: l.width / 2, y: l.stageTop + l.stageHeight / 2)
                motion.panChange(x: Double(-direction) * 140, y: 0)
                motion.panFinalize(velocityX: Double(-direction) * 600, velocityY: 0)
                motion.ambientUp()
            }
            configure(5, "Show tips", CGRect(x: l.width - 56, y: l.headerY - 22, width: 44, height: 44), .button) { [weak self] in self?.motion.infoTap() }
            configure(6, "To Mina Park. $24.00. Send it now? From noku balance, $182.40 available.", CGRect(x: 16, y: l.avatarY, width: l.width - 32, height: l.balanceTop + 48 - l.avatarY), .staticText)
            configure(7, "Back", CGRect(x: 12, y: l.headerY - 22, width: 44, height: 44), .button) { [weak self] in
                self?.motion.puckDown(1)
                self?.motion.puckUp(inside: true, puck: 1)
            }
            accessibilityElements = open ? [elements[1], elements[2], elements[3], elements[4], elements[0]] : [elements[7], elements[5], elements[6], elements[0]]
        }
    }
}
