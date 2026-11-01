import SwiftUI
import UIKit

final class QuestMapUnrollClock: ObservableObject {
    typealias M = QuestMapUnrollMotion
    @Published var running = false
    @Published var pressed = -1
    private var date: Date?
    private var now = 0.0
    private var u = 0.0, v = 0.0, target = 0.0, u0 = 0.0
    private var dragging = false
    private var wisp = M.current, wispTo = M.current
    private var lastTouch = -100.0
    private var scripted = false
    private var loopT = 0.0
    private var cardT = -10.0
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style:.light)
    func resetDate() { date = nil }
    func wake() { date = nil; running = true }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
        if let pinned { return M.at(pinned) }
        // Wall time drives autoplay. Only the RN spring integrator clamps at 50 ms.
        let wall = min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at
        now += wall
        let dt = min(wall,0.05)
        if auto && !dragging && now-lastTouch > 3.5 && (scripted || (u < 0.001 && target == 0 && wisp == M.current)) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT+wall).truncatingRemainder(dividingBy:M.loop)
            var f = M.at(loopT)
            u = f.u; target = f.u > 0.5 ? 1 : 0
            wisp = f.wisp; wispTo = f.wisp
            f.t = now
            return f
        }
        scripted = false
        if !dragging {
            let k = reduced ? 400.0 : 70.0
            let c = 2*0.95*sqrt(k)
            v += (k*(target-u)-c*v)*dt
            u = M.clamp(u+v*dt)
        }
        if wisp != wispTo {
            let dir = wispTo > wisp ? 1.0 : -1.0
            wisp += dir*dt*(reduced ? 20 : 1.1)
            if (dir > 0 && wisp >= wispTo) || (dir < 0 && wisp <= wispTo) {
                wisp = wispTo; cardT = now
                light.impactOccurred()
            }
        }
        let cu = now-cardT
        let card = cu >= 0 && cu < 2.2 ? M.smoother(cu/0.35)*(1-M.smoother((cu-1.8)/0.4)) : 0
        let f = M.Frame(u:u,wisp:wisp,t:now,card:card,level:Int(wispTo.rounded()))
        let still = !dragging && abs(v) < 0.000001 && abs(u-target) < 0.000001 && wisp == wispTo && card == 0 && (reduced || u < 0.001)
        if still && running {
            DispatchQueue.main.async { [weak self] in self?.running = false; self?.date = nil }
        }
        return f
    }
    func pan(_ state: UIGestureRecognizer.State, _ translation: Double, _ velocity: Double, _ g: QuestMapUnrollGeometry) {
        if state == .began {
            dragging = true; scripted = false; u0 = u
        }
        if state == .began || state == .changed {
            u = M.clamp(u0+translation/g.rect.height)
            lastTouch = now
        } else if dragging {
            dragging = false
            target = velocity > 500 ? 1 : velocity < -500 ? 0 : u > 0.5 ? 1 : 0
            v = velocity/g.rect.height; lastTouch = now
            selection.selectionChanged()
        }
        running = true
    }
    func tap(_ point: CGPoint, _ g: QuestMapUnrollGeometry) {
        lastTouch = now; scripted = false
        if u < 0.9 { target = 1 }
        else {
            for (i,n) in g.nodes.enumerated() where hypot(point.x-n.x,point.y-n.y) < 34 {
                wispTo = Double(i); selection.selectionChanged()
            }
        }
        date = nil; running = true
    }
}
