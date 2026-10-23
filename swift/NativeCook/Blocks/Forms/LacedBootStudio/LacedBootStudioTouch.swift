import SwiftUI
import UIKit

enum LacedBootStudioTouchPhase { case began, moved, ended, cancelled }
struct LacedBootStudioTouchSample {
    let id: UUID
    let phase: LacedBootStudioTouchPhase
    let point: CGPoint
    let date: Date
}
struct LacedBootStudioTouch: UIViewRepresentable {
    let enabled: Bool
    let generation: UUID
    let onSample: (LacedBootStudioTouchSample) -> Void
    func makeUIView(context: Context) -> LacedBootStudioTouchView {
        let view = LacedBootStudioTouchView()
        view.isOpaque = false; view.backgroundColor = .clear; view.isMultipleTouchEnabled = true
        view.isAccessibilityElement = false; view.accessibilityElementsHidden = true
        return view
    }
    func updateUIView(_ view: LacedBootStudioTouchView, context: Context) {
        if view.generation != generation || !enabled { view.invalidate(notify: false) }
        view.generation = generation; view.onSample = onSample; view.isUserInteractionEnabled = enabled
    }
    static func dismantleUIView(_ view: LacedBootStudioTouchView, coordinator: ()) {
        view.invalidate(notify: true); view.onSample = nil
    }
}
final class LacedBootStudioTouchView: UIView {
    var generation: UUID?
    var onSample: ((LacedBootStudioTouchSample) -> Void)?
    private var owner: UITouch?
    private var id: UUID?
    private var ignored: Set<ObjectIdentifier> = []
    private var last = CGPoint.zero
    private func world(_ touch: UITouch) -> CGPoint {
        let point = touch.location(in: self)
        return CGPoint(x: point.x + 23, y: point.y + 114)
    }
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        super.point(inside: point, with: event) &&
            LacedBootStudioGeometry.accepts(CGPoint(x: point.x + 23, y: point.y + 114))
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
    private func emit(_ phase: LacedBootStudioTouchPhase, _ point: CGPoint) {
        guard let id else { return }; onSample?(.init(id: id, phase: phase, point: point, date: Date()))
    }
    func invalidate(notify: Bool) {
        if notify { emit(.cancelled, last) }; owner = nil; id = nil; ignored.removeAll()
    }
    override func willMove(toWindow newWindow: UIWindow?) {
        if newWindow == nil { invalidate(notify: true) }; super.willMove(toWindow: newWindow)
    }
}
