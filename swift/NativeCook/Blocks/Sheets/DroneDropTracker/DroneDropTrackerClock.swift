import SwiftUI
import UIKit

// The autoplay clock uses wall time; only the RN Euler spring and manual flight step are clamped.
final class DroneDropTrackerClock: ObservableObject {
    @Published private(set) var revision = 0
    var frame = DroneDropTrackerFrame()
    private(set) var lastSheetVelocity = 0.0
    private var previous: Date?
    private var now = 0.0, loopT = 0.0, lastTouch = -100.0
    private var sv = 0.0, target = 0.0, s0 = 0.0, f0 = 0.0
    private var dragging = false, scrubbing = false, scripted = false, delivered = false
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()
    func resume() { previous = nil }
    func sample(_ date: Date, auto: Bool, reduced: Bool, pinned: Double?) -> DroneDropTrackerFrame {
        if let pinned { return DroneDropTrackerMotion.at(pinned) }
        let wall = min(0.5, max(0, previous.map { date.timeIntervalSince($0) } ?? 0.016))
        previous = date; now += wall
        let dt = min(wall, 0.05)
        if auto && !reduced && !dragging && !scrubbing && now - lastTouch > 3.5 && (scripted || (frame.f < 0.001 && frame.back == 0)) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT + wall).truncatingRemainder(dividingBy: DroneDropTrackerMotion.loop)
            frame = DroneDropTrackerMotion.at(loopT)
            frame.t = now
            target = frame.sheet > 0.5 ? 1 : 0
            return frame
        }
        scripted = false
        if !dragging {
            sv += (180 * (target - frame.sheet) - 2 * 0.9 * sqrt(180) * sv) * dt
            frame.sheet += sv * dt
        }
        if !scrubbing && frame.back == 0 && frame.f < 1 { frame.f = min(1, frame.f + dt / 30) }
        if frame.f >= 1 && frame.drop < 1 && !scrubbing {
            frame.drop = min(1, frame.drop + dt / 1.1)
            if frame.drop >= 1 && !delivered {
                delivered = true; frame.carried = 0
                DispatchQueue.main.async { [weak self] in self?.success.notificationOccurred(.success) }
            }
        }
        // RN reduced motion still advances the delivery; it only removes zoom, bob and sway.
        frame.t = now
        return frame
    }
    func sheetStart() { dragging = true; scripted = false; s0 = frame.sheet; sv = 0; lastSheetVelocity = 0 }
    func sheetUpdate(_ dy: Double, _ travel: Double, _ velocity: Double) {
        lastSheetVelocity = velocity
        frame.sheet = DroneDropTrackerMotion.clamp(s0 - dy / travel); lastTouch = now; revision += 1
    }
    func sheetEnd(_ vy: Double, _ travel: Double) {
        dragging = false
        target = vy < -500 ? 1 : vy > 500 ? 0 : frame.sheet > 0.5 ? 1 : 0
        sv = -vy / travel; lastTouch = now; selection.selectionChanged(); revision += 1
    }
    func mapStart() {
        scrubbing = true; scripted = false; f0 = frame.f; frame.back = 0; frame.carried = 1; frame.drop = 0; delivered = false
    }
    func mapUpdate(_ dx: Double, _ width: Double) {
        frame.f = DroneDropTrackerMotion.clamp(f0 + dx / (width * 1.2)); lastTouch = now; revision += 1
    }
    func mapEnd() { scrubbing = false; lastTouch = now; revision += 1 }
}
