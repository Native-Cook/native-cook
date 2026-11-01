import SwiftUI
import UIKit

final class SproutStatsDriver: ObservableObject {
    @Published var wake = 0
    @Published private(set) var ticking = true
    private var pendingTicking: Bool?
    private var previous: Date?
    private var now = 0.0
    private var loopT = 0.0
    private var m = 0.0, v = 0.0, target = 0.0, m0 = 0.0
    private var scrub = -1.0, lastTouch = -100.0
    private var scripted = false, dragging = false, horizontal = false
    private var lastDay = -1
    private let haptic = UISelectionFeedbackGenerator()
    private(set) var moving = false
    func resume() { previous = nil }
    func wakeUp() {
        if !ticking, let previous {
            let date = Date()
            // Nothing was drawn while the chart rested, but the RN sway clock still elapsed.
            now += max(0, date.timeIntervalSince(previous))
            self.previous = date
        }
        pendingTicking = nil; ticking = true; wake += 1
    }
    func sample(_ date: Date, auto: Bool, reduced: Bool, pinned: Double?) -> SproutStatsMotion.Frame {
        if let pinned { return SproutStatsMotion.at(pinned) }
        // The visual clock uses wall time. Only the spring integrator clamps to 50 ms, as RN does.
        let wall = min(0.5, max(0, previous.map { date.timeIntervalSince($0) } ?? 0.016))
        previous = date
        now += wall
        if auto && !reduced && !dragging && now-lastTouch > 3.5 && (scripted || (m < 0.001 && target == 0)) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT+wall).truncatingRemainder(dividingBy: SproutStatsMotion.loop)
            let sc = SproutStatsMotion.at(loopT)
            m = sc.m; target = m > 0.5 ? 1 : 0; scrub = sc.scrub
            return .init(m: m, scrub: scrub, t: now)
        }
        scripted = false
        if !dragging || horizontal {
            let dt = min(wall, 0.05), k = reduced ? 600.0 : 110.0
            v += (k*(target-m)-2*0.85*sqrt(k)*v)*dt
            m += v*dt
        }
        let day = scrub >= 0 ? Int(floor(scrub+0.5)) : -1
        if day != lastDay { lastDay = day; if day >= 0 { haptic.selectionChanged() } }
        moving = abs(target-m) > 0.00001 || abs(v) > 0.00001 || dragging
        let needsFrames = moving || (!reduced && (m < 0.99999 || (auto && target == 0)))
        if ticking != needsFrames && pendingTicking != needsFrames {
            pendingTicking = needsFrames
            DispatchQueue.main.async { [weak self] in
                guard let self, let pending = self.pendingTicking else { return }
                self.pendingTicking = nil; self.ticking = pending
            }
        }
        return .init(m: m, scrub: scrub, t: now)
    }
    func mode(_ c: Double) {
        wakeUp()
        target = c; scripted = false; lastTouch = now
        haptic.selectionChanged(); moving = true
    }
    func pan(_ state: UIGestureRecognizer.State, translation: CGPoint, point: CGPoint, velocity: CGPoint, step: Double) {
        switch state {
        case .began:
            dragging = true; scripted = false
            horizontal = abs(translation.y) <= abs(translation.x)
            m0 = m; v = 0; moving = true; wakeUp()
        case .changed:
            if horizontal { scrub = max(0, min(6, (point.x-30)/step)) }
            else { m = SproutStatsMotion.clamp(m0-translation.y/180) }
            lastTouch = now
        case .ended, .cancelled, .failed:
            guard dragging else { return }
            dragging = false
            if horizontal { scrub = -1 }
            else {
                target = velocity.y < -300 ? 1 : velocity.y > 300 ? 0 : m > 0.5 ? 1 : 0
                v = -velocity.y/180
            }
            lastTouch = now
        default: break
        }
    }
}

struct SproutStatsTouchSurface: UIViewRepresentable {
    var action: (UIGestureRecognizer.State, CGPoint, CGPoint, CGPoint) -> Void
    func makeCoordinator() -> SproutStatsTouchCoordinator { SproutStatsTouchCoordinator(action) }
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.addGestureRecognizer(UIPanGestureRecognizer(target: context.coordinator, action: #selector(SproutStatsTouchCoordinator.pan(_:))))
        return view
    }
    func updateUIView(_ view: UIView, context: Context) { context.coordinator.action = action }
}
final class SproutStatsTouchCoordinator: NSObject {
    var action: (UIGestureRecognizer.State, CGPoint, CGPoint, CGPoint) -> Void
    init(_ action: @escaping (UIGestureRecognizer.State, CGPoint, CGPoint, CGPoint) -> Void) { self.action = action }
    @objc func pan(_ g: UIPanGestureRecognizer) { action(g.state, g.translation(in: g.view), g.location(in: g.view), g.velocity(in: g.view)) }
}
