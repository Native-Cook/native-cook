import SwiftUI
import UIKit

/// Raw multi-touch preserves Manual Gesture's two independent finger IDs, including cancellation.
struct ParticleRibbonVoiceTouches: UIViewRepresentable {
    let driver: ParticleRibbonVoiceDriver
    let layout: ParticleRibbonVoiceLayout
    func makeUIView(context: Context) -> ParticleRibbonVoiceTouchView {
        let view = ParticleRibbonVoiceTouchView()
        view.isMultipleTouchEnabled = true; view.isOpaque = false; view.backgroundColor = .clear
        view.isAccessibilityElement = false; view.driver = driver; view.layout = layout
        return view
    }
    func updateUIView(_ uiView: ParticleRibbonVoiceTouchView, context: Context) {
        uiView.driver = driver; uiView.layout = layout
    }
    static func dismantleUIView(_ uiView: ParticleRibbonVoiceTouchView, coordinator: ()) { uiView.cancelAll() }
}

final class ParticleRibbonVoiceTouchView: UIView {
    var driver: ParticleRibbonVoiceDriver?
    var layout: ParticleRibbonVoiceLayout?
    private var ids: [ObjectIdentifier:Int] = [:]
    private var nextID = 0
    private func point(_ touch: UITouch) -> CGPoint {
        guard let g = layout else { return .zero }
        let p = touch.location(in:self)
        return CGPoint(x:p.x/g.scale,y:(p.y-g.y0)/g.scale)
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let id = nextID; nextID += 1; ids[ObjectIdentifier(touch)] = id
            let p = point(touch); driver?.down(id,p.x,p.y)
        }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            guard let id = ids[ObjectIdentifier(touch)] else { continue }
            let p = point(touch); driver?.move(id,p.x,p.y)
        }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { finish(touches,cancelled:false) }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { finish(touches,cancelled:true) }
    private func finish(_ touches: Set<UITouch>, cancelled: Bool) {
        for touch in touches {
            if let id = ids.removeValue(forKey:ObjectIdentifier(touch)) { driver?.up(id,cancelled:cancelled) }
        }
        if ids.isEmpty { nextID = 0 }
    }
    func cancelAll() {
        for id in ids.values { driver?.up(id,cancelled:true) }
        ids.removeAll(); nextID = 0
    }
}
