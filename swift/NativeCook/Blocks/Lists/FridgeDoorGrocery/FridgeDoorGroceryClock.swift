import SwiftUI
import UIKit

final class FridgeDoorGroceryClock: ObservableObject {
    typealias M = FridgeDoorGroceryMotion
    @Published var running = false
    @Published var basket = 0
    private var date: Date?
    private var now = 0.0
    private var a = 0.0, v = 0.0, target = 0.0, a0 = 0.0
    private var drag = false, scripted = false, snapped = true
    private var loopT = 0.0, lastTouch = -100.0
    private var fly = [Double](repeating: 0, count: 8)
    private var flyOn = [Bool](repeating: false, count: 8)
    private var bag = 0
    private var panStarted = false
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    func active() { date = nil }
    func reset() {
        date = nil; now = 0; a = 0; v = 0; target = 0; a0 = 0
        drag = false; scripted = false; snapped = true; loopT = 0; lastTouch = -100
        fly = Array(repeating:0,count:8); flyOn = Array(repeating:false,count:8); bag = 0
        basket = 0; running = false
    }
    func sample(_ time: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
        if let pinned { return M.at(pinned) }
        let wall = min(0.5, max(0, date.map { time.timeIntervalSince($0) } ?? 0.016))
        let dt = min(wall,0.05)
        date = time; now += wall
        if auto && !drag && now-lastTouch > 3.5 && (scripted || (a < 0.001 && target == 0)) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT+wall).truncatingRemainder(dividingBy:M.loop)
            var f = M.at(loopT); a = f.a; target = a > 0.5 ? 1:0; f.t = now
            publish(f.bag, true)
            return f
        }
        scripted = false
        if !drag {
            let k = 120.0, c = 2*0.62*sqrt(120.0)
            v += (k*(target-a)-c*v)*dt
            a += v*dt
            if a < 0 {
                a = 0
                if !snapped { snapped = true; v = abs(v)*0.2; medium.impactOccurred() }
            }
            if a > 0.05 { snapped = false }
        }
        for i in 0..<8 where flyOn[i] && fly[i] < 1 {
            fly[i] = min(1,fly[i]+dt/0.7)
            if fly[i] >= 1 { bag += 1; light.impactOccurred() }
        }
        let moving = drag || abs(v) > 0.00001 || abs(a-target) > 0.00001 || fly.contains { $0 > 0 && $0 < 1 } || (!reduced && a > 0.6 && (!flyOn[0] || !flyOn[2])) || (auto && now-lastTouch <= 3.5)
        publish(bag,moving)
        return .init(a:a,fly:fly,t:now,bag:bag)
    }
    private func publish(_ count: Int, _ moving: Bool) {
        guard count != basket || moving != running else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            if basket != count { basket = count }
            if running != moving { running = moving }
        }
    }
    func touch(_ state: UIGestureRecognizer.State, _ point: CGPoint, _ translation: CGSize, _ velocity: CGVector, _ g: M.Geo) {
        switch state {
        case .began: panStarted = false
        case .changed:
            if !panStarted && abs(translation.width) > 8 {
                panStarted = true; drag = true; scripted = false; a0 = a; v = 0
            }
            if panStarted { a = M.clamp(a0-translation.width/(g.w*1.1)); lastTouch = now; running = true }
        case .ended, .cancelled, .failed:
            if panStarted {
                drag = false
                target = velocity.dx < -400 ? 1 : velocity.dx > 400 ? 0 : a > 0.35 ? 1:0
                v = -velocity.dx/(g.w*1.1); lastTouch = now
            } else if state == .ended && hypot(translation.width,translation.height) <= 10 {
                tap(point,g)
            }
            panStarted = false; running = true; date = nil
        default: break
        }
    }
    func tap(_ point: CGPoint, _ g: M.Geo) {
        lastTouch = now; scripted = false
        if a < 0.6 { target = 1; running = true; date = nil; return }
        let ix = g.x+14, iy = g.y+14, iw = g.w-28, sh = (g.h-28)/4
        for (i,it) in M.items.enumerated() {
            let bx = ix+iw*it.x, by = iy+sh*Double(it.shelf+1)-10-sh*0.3
            if abs(point.x-bx) < sh*0.45 && abs(point.y-by) < sh*0.45 && !flyOn[i] {
                flyOn[i] = true; selection.selectionChanged()
            }
        }
        running = true; date = nil
    }
}

struct FridgeDoorGroceryTouchView: UIViewRepresentable {
    var onEvent: (UIGestureRecognizer.State,CGPoint,CGSize,CGVector)->Void
    func makeUIView(context: Context) -> FridgeDoorGroceryTouchSurface {
        let v = FridgeDoorGroceryTouchSurface(); v.onEvent = onEvent; return v
    }
    func updateUIView(_ view: FridgeDoorGroceryTouchSurface, context: Context) { view.onEvent = onEvent }
}
final class FridgeDoorGroceryTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onEvent: ((UIGestureRecognizer.State,CGPoint,CGSize,CGVector)->Void)?
    private var start = CGPoint.zero, last = CGPoint.zero
    private var lastTime = 0.0, beganAt = 0.0, excursion = 0.0
    private var velocity = CGVector.zero
    override init(frame:CGRect) {
        super.init(frame:frame); backgroundColor = .clear
        let gesture = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        gesture.minimumPressDuration = 0; gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView = false; gesture.delegate = self; addGestureRecognizer(gesture)
    }
    @available(*,unavailable) required init?(coder:NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool { true }
    @objc private func handle(_ gesture:UILongPressGestureRecognizer) {
        let p = gesture.location(in:self), t = ProcessInfo.processInfo.systemUptime
        if gesture.state == .began { start = p; last = p; lastTime = t; beganAt = t; excursion = 0; velocity = .zero }
        else if gesture.state == .changed {
            excursion = max(excursion,hypot(p.x-start.x,p.y-start.y))
            let dt = max(0.001,t-lastTime)
            velocity = CGVector(dx:(p.x-last.x)/dt,dy:(p.y-last.y)/dt); last = p; lastTime = t
        }
        let state: UIGestureRecognizer.State = gesture.state == .ended && (t-beganAt > 0.5 || excursion > 10) ? .failed : gesture.state
        onEvent?(state,p,CGSize(width:p.x-start.x,height:p.y-start.y),velocity)
    }
}
