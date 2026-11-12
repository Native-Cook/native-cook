import SwiftUI
import UIKit

/// Touch owns the original live driver. The automatic clock comes from the TimelineView wall date.
@MainActor
final class PinataSmashRewardEngine {
    typealias M = PinataSmashRewardMotion
    private var driver = M.makeDriver()
    private var epoch: Date?
    private var previous: Date?
    private var inactiveAt: Date?
    private var geo: M.Geo?
    private var startX = 0.0, startY = 0.0, startAt = Date.distantPast
    private var startOn = false, dragging = false
    var fingerDown = false
    private var rmN = 0, rmAt = Date.distantPast, rmBurstAt: Date?
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private let notification = UINotificationFeedbackGenerator()

    func reset(at date: Date) { driver = M.makeDriver(); epoch = date; previous = nil }
    func setActive(_ active: Bool, at date: Date) {
        if active {
            if let inactiveAt {
                let pause = date.timeIntervalSince(inactiveAt)
                if let epoch { self.epoch = epoch.addingTimeInterval(pause) }
                rmAt = rmAt.addingTimeInterval(pause)
                if let rmBurstAt { self.rmBurstAt = rmBurstAt.addingTimeInterval(pause) }
                startAt = startAt.addingTimeInterval(pause)
            }
            inactiveAt = nil; previous = nil
        } else if inactiveAt == nil { inactiveAt = date }
    }
    func frame(at date: Date, geo: M.Geo, auto: Bool, reduced: Bool, pin: Double?) -> M.Frame {
        self.geo = geo
        if epoch == nil { epoch = date }
        if reduced {
            var f = M.frameAt(0,.init(hits: [],clear: -1,clearKind: 0),geo,0)
            let n = min(rmN,12), done = rmBurstAt != nil
            f.fill = Double(n)/12; f.wL = n == 0 ? 1 : 0; f.wB = n > 0 && !done ? 1 : 0; f.wD = done ? 1 : 0
            f.pillW = done ? 172.5 : n > 0 ? 120 : 186
            f.bloom = 0.25*(1-M.clamp01(date.timeIntervalSince(rmAt)/0.12)); f.bloomX = geo.body.x; f.bloomY = geo.body.y
            if let at = rmBurstAt {
                let k = M.easeOutCubic(M.clamp01(date.timeIntervalSince(at)/0.25))
                f.burst = k; f.doneText = k
                for (kind,x,y,rot) in [(0,120.0,820.0,0.3),(4,170,826,-0.2),(1,214,818,-0.4),(3,256,828,0),(2,292,812,0.5),(5,330,828,0)] {
                    f.loot.append(.init(k:kind,x:x,y:y,rot:rot,s:1,a:k,sx:1))
                }
            }
            driver.lastAx = f.ax; driver.lastAy = f.ay
            previous = date
            return f
        }
        let dt = min(max(previous.map { date.timeIntervalSince($0) } ?? 0.016,0),0.05)
        previous = date
        if let pin { driver.mode = 0; driver.loopT = pin }
        else {
            let oldMode = driver.mode
            _ = M.stepDriver(&driver,dt,auto)
            if oldMode == 1 && driver.mode == 0 { epoch = date.addingTimeInterval(-driver.loopT) }
            if driver.mode == 0 {
                driver.loopT = auto ? date.timeIntervalSince(epoch!).truncatingRemainder(dividingBy: M.loop) : 0
            }
        }
        let live = driver.mode == 1, t = live ? driver.t : driver.loopT
        var f = M.frameAt(t,live ? driver.story : M.scriptStory,geo,t)
        if driver.blendPending != 0 {
            driver.blendRoll = driver.lastRoll-f.roll; driver.blendX = driver.lastAx-f.ax; driver.blendY = driver.lastAy-f.ay
            driver.blendPending = 0
        }
        M.applyDriver(&f,driver)
        driver.lastAx = f.ax; driver.lastAy = f.ay; driver.lastRoll = f.roll
        return f
    }
    func begin(_ point: CGPoint, reduced: Bool, at date: Date) {
        fingerDown = true; startX = point.x; startY = point.y; startAt = date; dragging = false
        guard let geo else { startOn = false; return }
        let drawn = driver.lastAx != 0 || driver.lastAy != 0
        let ox = drawn ? driver.lastAx-geo.attach.x : 0, oy = drawn ? driver.lastAy-geo.attach.y : 0
        startOn = point.x >= 49+ox-16 && point.x <= 49+286+ox+16 && point.y >= 283+oy-16 && point.y <= 283+303+oy+16
        if reduced || !startOn { return }
        M.takeOver(&driver); driver.last = driver.t; driver.pressAim = 1
    }
    func move(_ point: CGPoint, reduced: Bool) {
        guard !reduced, startOn else { return }
        driver.last = driver.t
        if !dragging && hypot(point.x-startX,point.y-startY) > 8 {
            dragging = true; driver.drag = 1; driver.pressAim = 0; driver.gx = driver.ox-startX; driver.gy = driver.oy-startY
        }
        if driver.drag != 0 { driver.fx = point.x; driver.fy = point.y }
    }
    @discardableResult
    func end(_ velocity: CGSize, reduced: Bool, at date: Date) -> Bool {
        fingerDown = false; driver.pressAim = 0
        guard startOn else { return false }
        startOn = false
        if dragging {
            let v = hypot(velocity.width,velocity.height), k = v > 520 ? 520/v : 1
            driver.drag = 0; driver.vx = velocity.width*k; driver.vy = velocity.height*k; driver.last = driver.t
            return false
        }
        guard date.timeIntervalSince(startAt) <= 0.35 else { return false }
        if reduced {
            if rmBurstAt != nil { return false }
            rmN += 1; rmAt = date
            if rmN >= 12 { rmBurstAt = date }
            buzz(rmN >= 12 ? 3 : 1)
            return true
        }
        if let geo { buzz(M.tapAt(&driver,startX,startY,geo)) }
        return false
    }
    func accessibilityHit() {
        guard let geo else { return }
        // RN's accessibility callback appends a hit without invoking its touch haptic callback.
        _ = M.tapAt(&driver,geo.body.x,geo.body.y,geo)
    }
    private func buzz(_ kind: Int) {
        switch kind {
        case 1: light.impactOccurred()
        case 2: medium.impactOccurred()
        case 3:
            heavy.impactOccurred()
            Task { @MainActor in
                try? await Task.sleep(for:.milliseconds(120))
                notification.notificationOccurred(.success)
            }
        case 4: notification.notificationOccurred(.warning)
        default: break
        }
    }
}
