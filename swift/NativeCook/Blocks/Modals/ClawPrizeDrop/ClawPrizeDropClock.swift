import SwiftUI
import UIKit

final class ClawPrizeDropClock: ObservableObject {
    typealias M = ClawPrizeDropMotion
    @Published var running = false
    @Published var pressed = false
    @Published var rewardVisibleForAccessibility = false
    private var date: Date?
    private var now = 0.0, loopT = 0.0, x = 0.0, sw = 0.0, swV = 0.0
    private var lastTouch = -100.0
    private var grabState = M.Grab()
    private var drag = false, scripted = false, initialized = false
    private var x0 = 0.0
    private var rewardVisible = false
    var won = 2
    func resetDate() { date = nil }
    func sample(_ at: Date, _ g: M.Geo, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
        if let pinned { return M.scriptAt(g, pinned.truncatingRemainder(dividingBy:M.loop)) }
        if !initialized { x = g.homeX; initialized = true }
        // Advance the script by wall time, including slow recording frames; only physics is clamped.
        let wall = min(0.5,max(0,date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at; now += wall
        let dt = min(wall,0.05), busy = now-grabState.at < 3.2
        var r: M.Frame
        if auto && !reduced && !drag && !busy && now-lastTouch > 3.5 && (scripted || abs(x-g.homeX)<1) {
            if !scripted { scripted = true; loopT = 0 }
            let prevX = x
            loopT = (loopT+wall).truncatingRemainder(dividingBy:M.loop)
            r = M.scriptAt(g,loopT); x = r.x
            integrate((x-prevX)/max(dt,0.001),dt)
        } else {
            scripted = false
            let u = now-grabState.at
            r = M.grabAt(g,grabState,u)
            let prevX = x
            if u >= 0 && u < 3.2 { x = r.x }
            else if u >= 3.2 && u < 4.2 { x = M.lerp(g.chuteX,g.homeX,M.smoother(u-3.2)) }
            integrate((x-prevX)/max(dt,0.001),dt)
            r.x = x
            r.respawn = grabState.prize >= 0 ? M.smoother((u-4.6)/0.8) : 1
            r.coupon = grabState.prize >= 0 && u >= 2.8 && u < 4.6 ? M.smoother((u-2.8)/0.3)*(1-M.smoother((u-4.2)/0.4)) : 0
        }
        r.swing = reduced ? 0 : sw
        let visible = r.coupon > 0.5 && r.prize >= 0
        if visible && !rewardVisible {
            won = r.prize
            DispatchQueue.main.async { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        }
        if visible != rewardVisible {
            let prize = won
            DispatchQueue.main.async { [weak self] in
                self?.rewardVisibleForAccessibility = visible
                if visible { UIAccessibility.post(notification:.announcement,argument:"\(M.names[prize]). \(M.rewards[prize])") }
            }
        }
        rewardVisible = visible
        if running && !drag && now-lastTouch > 5.6 && now-grabState.at > 5.4 && abs(sw)<0.001 && abs(swV)<0.001 {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return r
    }
    private func integrate(_ vx: Double, _ dt: Double) {
        swV += (-90*sw-7*swV-vx*0.9)*dt
        sw += swV*dt
    }
    func pan(_ translation: Double, _ g: M.Geo) {
        if !drag {
            if now-grabState.at < 4.2 { return }
            drag = true; scripted = false; x0 = initialized ? x : g.homeX
            date = Date(); running = true
        }
        x = max(g.x+40,min(g.x+g.w-30,x0+translation)); lastTouch = now
    }
    func endPan() { drag = false; lastTouch = now }
    func grab(_ g: M.Geo) {
        UIImpactFeedbackGenerator(style:.medium).impactOccurred()
        if now-grabState.at < 4.2 { return }
        if !initialized { x = g.homeX; initialized = true }
        var best = -1, bd = g.ps*0.45
        for i in 0..<6 {
            let d = abs(x-g.px[i]); if d < bd { bd = d; best = i }
        }
        grabState = M.Grab(at:now,fromX:x,prize:best); scripted = false; lastTouch = now
        date = Date(); running = true
    }
}
