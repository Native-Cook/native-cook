import SwiftUI
import UIKit

final class IslandSleepDialClock: ObservableObject {
    typealias M = IslandSleepDialMotion
    @Published var revision = 0
    @Published var busy = false
    private var date: Date?
    private var now = 0.0
    private var h = 6.0
    private var cloud = 0.0
    private var drag = 0.0
    private var dragK = 0.0
    private var a0 = 0.0
    private var h0 = 6.0
    private var lastTouch = -100.0
    private var scripted = false
    private var loopT = 0.0
    private var lastStep = 24
    private var setAt = -10.0
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    // The reference still moves fireflies under reduced motion; only frames with none can pause.
    var canPauseReduced: Bool { h < 5 && !busy && drag == 0 && dragK < 0.001 }
    func resetDate() { date = nil }
    func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
        if let pinned { return M.at(pinned) }
        // Advance the script by wall time, including slow software-rendered recording frames.
        let wall = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at
        now += wall
        let dt = min(wall, 0.05)
        dragK += (drag - dragK) * (1 - exp(-12 * dt))
        if auto && !reduced && drag == 0 && now - lastTouch > 3.5
            && (scripted || (abs(h - 6) < 0.01 && cloud < 0.001)) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT + wall).truncatingRemainder(dividingBy: M.loop)
            let f = M.at(loopT)
            h = f.h; cloud = f.cloud
        } else {
            scripted = false
            let u = now - setAt
            cloud = u >= 0 && u < 2 ? M.smoother(u / 0.8) * (1 - M.smoother((u - 1.4) / 0.6)) : 0
            let step = M.quarter(h)
            if step != lastStep { lastStep = step; selection.selectionChanged() }
        }
        if busy && cloud == 0 && drag == 0 && dragK < 0.001 {
            DispatchQueue.main.async { [weak self] in self?.busy = false }
        }
        return M.Frame(h: h, cloud: cloud, t: now, drag: dragK)
    }
    func pan(_ point: CGPoint, center: CGPoint) {
        let a = atan2(point.y - center.y, point.x - center.x)
        if drag == 0 {
            drag = 1; scripted = false; a0 = a; h0 = h
        } else {
            var d = a - a0
            if d > .pi { d -= .pi * 2 }
            if d < -.pi { d += .pi * 2 }
            a0 = a
            h0 = max(3.7, min(11.3, h0 + d / (.pi * 2) * 12))
            h = max(4, min(11, Double(M.quarter(h0)) / 4))
            lastTouch = now
        }
        busy = true; revision += 1
    }
    func endPan() { drag = 0; lastTouch = now; revision += 1 }
    func setAlarm(resuming: Bool = false) {
        success.notificationOccurred(.success)
        setAt = now; scripted = false; lastTouch = now
        if resuming { date = nil }
        busy = true; revision += 1
    }
    func adjust(_ delta: Double) {
        h = max(4, min(11, h + delta)); scripted = false; lastTouch = now
        selection.selectionChanged(); lastStep = M.quarter(h); revision += 1
    }
}
