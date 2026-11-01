import SwiftUI
import UIKit

final class SetupDomeLoaderClock: ObservableObject {
    typealias M = SetupDomeLoaderMotion
    @Published var wake = 0
    @Published var settled = false
    var output = M.Output()
    private var previous: Date?
    private var velocity = 0.0
    private var hopV = [Double](repeating:0,count:5)
    private var delay = [Double](repeating:-1,count:5)
    private var doneBuzz = false
    func resetDate() { previous = nil }
    func sample(_ date: Date, _ auto: Bool, _ pinned: Double?) -> M.Output {
        if let pinned { return M.Output(t:pinned,clock:pinned) }
        let wall = min(0.5,max(0,previous.map { date.timeIntervalSince($0) } ?? 0.016))
        previous = date
        let dt = min(wall,0.05)
        output.clock += wall
        output.t = auto ? (output.t+wall).truncatingRemainder(dividingBy:M.loop) : min(output.t+wall,8.3)
        if !auto && output.t >= 7.35 && !doneBuzz {
            doneBuzz = true
            DispatchQueue.main.async { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        }
        velocity *= exp(-1.4*dt)
        output.off += velocity*dt
        for i in 0..<5 {
            if delay[i] >= 0 {
                delay[i] -= dt
                if delay[i] < 0 { hopV[i] = 260; delay[i] = -1 }
            }
            hopV[i] -= 1400*dt
            output.hop[i] += hopV[i]*dt
            if output.hop[i] < 0 {
                output.hop[i] = 0
                hopV[i] = abs(hopV[i]) > 60 ? -hopV[i]*0.35 : 0
            }
        }
        if !auto && output.t >= 8.3 && !settled && abs(velocity) < 0.00001 && output.hop.allSatisfy({ $0 == 0 }) && delay.allSatisfy({ $0 < 0 }) {
            DispatchQueue.main.async { [weak self] in self?.settled = true }
        }
        return output
    }
    private func resume() {
        if settled {
            // The invisible item spin still uses RN's continuous clock after Continue.
            // Account for the idle interval without running a static screen every frame.
            let now = Date()
            if let previous { output.clock += max(0,now.timeIntervalSince(previous)) }
            previous = now
        }
        settled = false
    }
    // RN keeps its setup clock running through touches, including under reduced motion.
    func pan(_ vx: Double, ended: Bool) {
        resume()
        velocity = max(-9,min(9,ended ? vx*0.006 : velocity+vx*0.00012))
        wake &+= 1
    }
    func tap() {
        resume()
        for i in 0..<5 { delay[i] = M.items[i].delay }
        UIImpactFeedbackGenerator(style:.light).impactOccurred()
        wake &+= 1
    }
    func restart() {
        UIImpactFeedbackGenerator(style:.light).impactOccurred()
        resume()
        output.t = 0
        doneBuzz = false
        wake &+= 1
    }
}

struct SetupDomeLoaderTouch: UIViewRepresentable {
    var clock: SetupDomeLoaderClock
    func makeUIView(context: Context) -> SetupDomeLoaderTouchSurface {
        let view = SetupDomeLoaderTouchSurface()
        view.backgroundColor = .clear
        view.isOpaque = false
        view.clock = clock
        view.isMultipleTouchEnabled = true
        let pan = UIPanGestureRecognizer(target:context.coordinator,action:#selector(Coordinator.pan(_:)))
        pan.cancelsTouchesInView = false
        view.addGestureRecognizer(pan)
        view.isAccessibilityElement = true
        view.accessibilityTraits = .image
        view.accessibilityLabel = "Your wallet, card and money floating in a glass case while the account is set up"
        return view
    }
    func updateUIView(_ view: SetupDomeLoaderTouchSurface, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(clock) }
    final class Coordinator: NSObject {
        private var armed = false
        let clock: SetupDomeLoaderClock
        init(_ clock: SetupDomeLoaderClock) { self.clock = clock }
        @objc func pan(_ gesture: UIPanGestureRecognizer) {
            if gesture.state == .began { armed = false }
            if gesture.state == .changed || gesture.state == .began {
                if abs(gesture.translation(in:gesture.view).x) > 10 { armed = true }
                if armed { clock.pan(gesture.velocity(in:gesture.view).x,ended:false) }
            }
            if gesture.state == .ended && armed { clock.pan(gesture.velocity(in:gesture.view).x,ended:true) }
        }
    }
}

// RNGH Tap defaults: one pointer, <=500 ms, and no excursion beyond ten screen points.
final class SetupDomeLoaderTouchSurface: UIView {
    weak var clock: SetupDomeLoaderClock?
    private var tracked: UITouch?
    private var active: Set<UITouch> = []
    private var start = CGPoint.zero
    private var started = 0.0
    private var eligible = false
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        active.formUnion(touches)
        if tracked != nil || active.count != 1 {
            eligible = false
            return
        }
        guard let touch = touches.first else { return }
        tracked = touch; start = touch.location(in:self); started = touch.timestamp; eligible = true
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = tracked, touches.contains(touch) else { return }
        let point = touch.location(in:self)
        if hypot(point.x-start.x,point.y-start.y) > 10 { eligible = false }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        active.subtract(touches)
        guard let touch = tracked, touches.contains(touch) else { return }
        let point = touch.location(in:self)
        if eligible && touch.timestamp-started <= 0.5 && hypot(point.x-start.x,point.y-start.y) <= 10 { clock?.tap() }
        tracked = nil; eligible = false
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        active.subtract(touches)
        tracked = nil; eligible = false
    }
}
