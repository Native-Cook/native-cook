import Foundation
import SwiftUI
import UIKit

enum BoxCatFeedMotion {
    static let loop = 9.0
    static let fly = 0.7
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    struct Cat { var lift = 0.0; var stretch = 0.0; var munch = 0.0; var caught = false; var hearts = -1.0 }
    struct Frame { var u = -10.0; var blink = false; var duck = 0.0; var count = 0; var dip = 0.0 }
    static func script(_ t: Double) -> Frame {
        let t = t.truncatingRemainder(dividingBy: loop)
        let at = t >= 5 ? 5.0 : t >= 1 ? 1.0 : -10.0
        // the day's counters dip out and back at 8.25–8.75 s and reset at the bottom of the dip
        let x = clamp((t - 8.25) / 0.5)
        return Frame(u: at > 0 ? t-at : -10, count: t >= 8.5 ? 0 : t >= 5.7 ? 2 : t >= 1.7 ? 1 : 0, dip: sin(x * .pi))
    }
    /// The fish on its button: fades as it is flicked, comes back when the cat has eaten.
    static func treatShown(_ u: Double) -> Double {
        if u < 0 { return 1 }
        if u < 0.12 { return 1 - u / 0.12 }
        if u < 1.25 { return 0 }
        return min(1, (u - 1.25) / 0.15)
    }
    static func cat(_ u: Double) -> Cat {
        if u < 0 || u > 2.6 { return Cat() }
        let up = clamp((u - (fly - 0.32)) / 0.32)
        let down = clamp((u - fly - 0.12) / 0.35)
        let stretch = up > 0 && up < 1 ? 0.18 * sin(.pi * up) : 0
        let land = u > fly + 0.47 && u < fly + 0.75 ? -0.14 * sin(((u-fly-0.47)/0.28) * .pi) : 0
        let munch = u > fly + 0.4 && u < fly + 1.6 ? abs(sin((u-fly)*14)) : 0
        return Cat(lift: sin(.pi/2*up)*(1-down*down), stretch: stretch+land, munch: munch, caught: u >= fly, hearts: u >= fly+0.3 ? u-fly-0.3 : -1)
    }
}

/// Wall time drives the script; only the duck spring uses RN's 50 ms integration cap.
final class BoxCatFeedClock: ObservableObject {
    @Published var revision = 0
    @Published var running = false
    private var stopping = false
    private var autoplaying = false
    var date: Date?
    var now = 0.0
    var at = -10.0
    var count = 0
    var lastTouch = -100.0
    var scripted = false
    var loopT = 0.0
    var duck = 0.0
    var duckV = 0.0
    func sample(_ date: Date, auto: Bool, pinned: Double?) -> BoxCatFeedMotion.Frame {
        if let pinned { return BoxCatFeedMotion.script(pinned) }
        autoplaying = auto
        let elapsed = max(0, self.date.map { date.timeIntervalSince($0) } ?? 0.016)
        let wall = auto || running ? min(0.5, elapsed) : elapsed
        self.date = date
        now += wall
        let dt = min(wall, 0.05)
        duckV += (-160*duck - 14*duckV)*dt
        duck += duckV*dt
        if auto && now-lastTouch > 3.5 && (scripted || now-at > 2.8) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT+wall).truncatingRemainder(dividingBy: 9)
            var frame = BoxCatFeedMotion.script(loopT)
            frame.blink = now.truncatingRemainder(dividingBy: 3.4) < 0.14
            frame.duck = duck
            return frame
        }
        scripted = false
        if running && !stopping && now-at > 2.8 && abs(duckV) < 0.0001 && abs(duck) < 0.0001 {
            stopping = true
            DispatchQueue.main.async { [weak self] in
                self?.running = false
                self?.stopping = false
            }
        }
        return .init(u: now-at, blink: now.truncatingRemainder(dividingBy: 3.4) < 0.14, duck: duck, count: now-at >= 0.7 ? count : max(0,count-1))
    }
    func pet() { synchronizeIdle(); duckV += 4; lastTouch = now; wake() }
    func flick() {
        synchronizeIdle()
        if now-at < 1.6 { return }
        at = now; count = count % 3 + 1; scripted = false; lastTouch = now; wake()
    }
    var blinkPhase: Double {
        max(0, now + (date.map { Date().timeIntervalSince($0) } ?? 0)).truncatingRemainder(dividingBy: 3.4)
    }
    private func synchronizeIdle() {
        if !running && !autoplaying {
            let current = Date()
            now += max(0, date.map { current.timeIntervalSince($0) } ?? 0)
            date = current
        }
    }
    private func wake() {
        date = Date()
        running = true
        revision += 1
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}
