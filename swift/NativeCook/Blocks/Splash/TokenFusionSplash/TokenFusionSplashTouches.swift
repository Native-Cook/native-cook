import SwiftUI
import UIKit

/// Raw UIKit touches preserve RN's manual gesture: several coins and the core can be held independently.
struct TokenFusionSplashTouches: UIViewRepresentable {
    enum TokenFusionSplashTouchPhase { case began, moved, ended, cancelled }
    var scene: TokenFusionSplashMotion.TokenFusionSplashScene
    var geometry: TokenFusionSplashFrame.TokenFusionSplashGeometry
    var onTouch: (TokenFusionSplashTouchPhase, Int, CGPoint) -> Void
    var onActivate: (Int) -> Void

    func makeUIView(context: Context) -> TokenFusionSplashTouchSurface { TokenFusionSplashTouchSurface() }
    func updateUIView(_ view: TokenFusionSplashTouchSurface, context: Context) {
        view.geometry = geometry; view.onTouch = onTouch; view.onActivate = onActivate
        view.updateAccessibility(scene)
    }
    final class TokenFusionSplashAccessibilityElement: UIAccessibilityElement {
        var activate: (() -> Void)?
        override func accessibilityActivate() -> Bool { activate?(); return true }
    }
    final class TokenFusionSplashTouchSurface: UIView {
        var geometry = TokenFusionSplashFrame.TokenFusionSplashGeometry(width: 402, height: 874, top: 62, bottom: 34)
        var onTouch: ((TokenFusionSplashTouchPhase, Int, CGPoint) -> Void)?
        var onActivate: ((Int) -> Void)?
        private var pointers: [ObjectIdentifier: Int] = [:]
        private var serial = 0
        private var elements: [TokenFusionSplashAccessibilityElement] = []
        override init(frame: CGRect) {
            super.init(frame: frame)
            isMultipleTouchEnabled = true; backgroundColor = .clear; isOpaque = false; isAccessibilityElement = false
            for i in -1..<4 {
                let element = TokenFusionSplashAccessibilityElement(accessibilityContainer: self)
                element.accessibilityTraits = .button
                element.accessibilityLabel = i < 0 ? "Korra core. Drag to collect tokens." : "Add \(TokenFusionSplashResources.labels[i])"
                element.accessibilityHint = i < 0 ? "Activate to finish linking accounts" : "Drag toward the core, or activate to merge"
                element.activate = { [weak self] in self?.onActivate?(i) }
                elements.append(element)
            }
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
            point.x >= 20 && point.y < geometry.height - geometry.bottom && super.point(inside: point, with: event)
        }
        func updateAccessibility(_ scene: TokenFusionSplashMotion.TokenFusionSplashScene) {
            let g = geometry
            elements[0].accessibilityFrameInContainerSpace = CGRect(x: scene.x * g.scale + g.offsetX - 60,
                                                                   y: scene.y * g.scale + g.offsetY - 60, width: 120, height: 120)
            var shown: [UIAccessibilityElement] = [elements[0]]
            for i in scene.coins.indices {
                let c = scene.coins[i], element = elements[i + 1]
                element.accessibilityFrameInContainerSpace = CGRect(x: c.x * g.scale + g.offsetX - 24,
                                                                    y: c.y * g.scale + g.offsetY - 24, width: 48, height: 48)
                if c.r > 1 { shown.append(element) }
            }
            accessibilityElements = shown
        }
        override func accessibilityPerformEscape() -> Bool { onActivate?(-1); return true }
        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            for touch in touches {
                let key = ObjectIdentifier(touch); serial += 1; pointers[key] = serial
                onTouch?(.began, serial, touch.location(in: self))
            }
        }
        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
            for touch in touches { if let id = pointers[ObjectIdentifier(touch)] { onTouch?(.moved, id, touch.location(in: self)) } }
        }
        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { finish(touches, cancelled: false) }
        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { finish(touches, cancelled: true) }
        private func finish(_ touches: Set<UITouch>, cancelled: Bool) {
            for touch in touches {
                if let id = pointers.removeValue(forKey: ObjectIdentifier(touch)) {
                    onTouch?(cancelled ? .cancelled : .ended, id, touch.location(in: self))
                }
            }
        }
    }
}
