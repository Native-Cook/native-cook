import SwiftUI
import UIKit

enum LenticularEditionPassTouchPhase { case began, moved, ended, cancelled }
struct LenticularEditionPassTouchSample {
    let id: UUID
    let phase: LenticularEditionPassTouchPhase
    let location: CGPoint
    let date: Date
}

/// A stable transparent UIKit surface delivers Down without waiting for a drag.
struct LenticularEditionPassTouch: UIViewRepresentable {
    let enabled: Bool
    let generation: UUID
    let accepts: (CGPoint) -> Bool
    let onSample: (LenticularEditionPassTouchSample) -> Void

    func makeUIView(context: Context) -> LenticularEditionPassTouchView {
        let view = LenticularEditionPassTouchView()
        view.backgroundColor = .clear
        view.isOpaque = false
        view.isMultipleTouchEnabled = true
        view.isAccessibilityElement = false
        view.accessibilityElementsHidden = true
        return view
    }
    func updateUIView(_ view: LenticularEditionPassTouchView, context: Context) {
        if view.generation != generation || !enabled { view.invalidate(notify: false) }
        view.generation = generation
        view.accepts = accepts
        view.onSample = onSample
        view.isUserInteractionEnabled = enabled
    }
    static func dismantleUIView(_ view: LenticularEditionPassTouchView, coordinator: ()) {
        view.invalidate(notify: true)
        view.onSample = nil; view.accepts = nil
    }
}

final class LenticularEditionPassTouchView: UIView {
    var generation: UUID?
    var accepts: ((CGPoint) -> Bool)?
    var onSample: ((LenticularEditionPassTouchSample) -> Void)?
    private var owner: UITouch?
    private var ownerID: UUID?
    private var ignored: Set<ObjectIdentifier> = []
    private var lastPoint = CGPoint.zero
    private let origin = CGPoint(x: 21.5, y: 242.5)

    private func logical(_ touch: UITouch) -> CGPoint {
        let local = touch.location(in: self)
        return CGPoint(x: local.x + origin.x, y: local.y + origin.y)
    }
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        guard super.point(inside: point, with: event) else { return false }
        return accepts?(CGPoint(x: point.x + origin.x, y: point.y + origin.y)) ?? false
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard owner == nil else {
            for touch in touches { ignored.insert(ObjectIdentifier(touch)) }
            return
        }
        guard let touch = touches.filter({ !ignored.contains(ObjectIdentifier($0)) })
            .min(by: { $0.timestamp < $1.timestamp }) else { return }
        let point = logical(touch)
        guard accepts?(point) == true else {
            for touch in touches { ignored.insert(ObjectIdentifier(touch)) }
            return
        }
        owner = touch; ownerID = UUID(); lastPoint = point
        for other in touches where other !== touch { ignored.insert(ObjectIdentifier(other)) }
        emit(.began, point: point)
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let owner, touches.contains(where: { $0 === owner }) else { return }
        let point = logical(owner)
        guard point != lastPoint else { return }
        lastPoint = point; emit(.moved, point: point)
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        end(touches, cancelled: false)
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        end(touches, cancelled: true)
    }
    private func end(_ touches: Set<UITouch>, cancelled: Bool) {
        if let owner, touches.contains(where: { $0 === owner }) {
            let point = cancelled ? lastPoint : logical(owner)
            emit(cancelled ? .cancelled : .ended, point: point)
            self.owner = nil; ownerID = nil
        }
        for touch in touches { ignored.remove(ObjectIdentifier(touch)) }
    }
    private func emit(_ phase: LenticularEditionPassTouchPhase, point: CGPoint) {
        guard let ownerID else { return }
        onSample?(.init(id: ownerID, phase: phase, location: point, date: Date()))
    }
    func invalidate(notify: Bool) {
        if notify { emit(.cancelled, point: lastPoint) }
        owner = nil; ownerID = nil; ignored.removeAll()
    }
    override func willMove(toWindow newWindow: UIWindow?) {
        if newWindow == nil { invalidate(notify: true) }
        super.willMove(toWindow: newWindow)
    }
}
