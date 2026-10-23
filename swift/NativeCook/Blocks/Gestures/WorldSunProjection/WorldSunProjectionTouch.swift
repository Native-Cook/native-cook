import SwiftUI
import UIKit

enum WorldSunProjectionTouchPhase { case began, moved, ended, cancelled }
struct WorldSunProjectionTouchSample {
    let id: UUID
    let phase: WorldSunProjectionTouchPhase
    let point: CGPoint
    let date: Date
}
struct WorldSunProjectionTouch: UIViewRepresentable {
    let enabled: Bool
    let generation: UUID
    let onSample: (WorldSunProjectionTouchSample) -> Void
    func makeUIView(context: Context) -> WorldSunProjectionTouchView {
        let view = WorldSunProjectionTouchView()
        view.isOpaque = false; view.backgroundColor = .clear; view.isMultipleTouchEnabled = true
        view.isAccessibilityElement = false; view.accessibilityElementsHidden = true
        return view
    }
    func updateUIView(_ view: WorldSunProjectionTouchView, context: Context) {
        if view.generation != generation || !enabled { view.invalidate(notify: false) }
        view.generation = generation; view.onSample = onSample; view.isUserInteractionEnabled = enabled
    }
    static func dismantleUIView(_ view: WorldSunProjectionTouchView, coordinator: ()) {
        view.invalidate(notify: true); view.onSample = nil
    }
}
final class WorldSunProjectionTouchView: UIView {
    var generation: UUID?
    var onSample: ((WorldSunProjectionTouchSample) -> Void)?
    private var owner: UITouch?
    private var id: UUID?
    private var ignored: Set<ObjectIdentifier> = []
    private var last = CGPoint.zero
    private func world(_ touch: UITouch) -> CGPoint {
        let point = touch.location(in: self)
        return CGPoint(x: point.x, y: point.y + 114)
    }
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        super.point(inside: point, with: event) &&
            CGRect(x: 0, y: 0, width: 393, height: 621).contains(point)
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard owner == nil else { for touch in touches { ignored.insert(ObjectIdentifier(touch)) }; return }
        guard let touch = touches.filter({ !ignored.contains(ObjectIdentifier($0)) }).min(by: { $0.timestamp < $1.timestamp }) else { return }
        owner = touch; id = UUID(); last = world(touch)
        for other in touches where other !== touch { ignored.insert(ObjectIdentifier(other)) }
        emit(.began, last)
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let owner, touches.contains(where: { $0 === owner }) else { return }
        let point = world(owner); guard point != last else { return }
        last = point; emit(.moved, point)
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { end(touches, cancelled: false) }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { end(touches, cancelled: true) }
    private func end(_ touches: Set<UITouch>, cancelled: Bool) {
        if let owner, touches.contains(where: { $0 === owner }) {
            emit(cancelled ? .cancelled : .ended, cancelled ? last : world(owner))
            self.owner = nil; id = nil
        }
        for touch in touches { ignored.remove(ObjectIdentifier(touch)) }
    }
    private func emit(_ phase: WorldSunProjectionTouchPhase, _ point: CGPoint) {
        guard let id else { return }; onSample?(.init(id: id, phase: phase, point: point, date: Date()))
    }
    func invalidate(notify: Bool) {
        if notify { emit(.cancelled, last) }; owner = nil; id = nil; ignored.removeAll()
    }
    override func willMove(toWindow newWindow: UIWindow?) {
        if newWindow == nil { invalidate(notify: true) }; super.willMove(toWindow: newWindow)
    }
}
