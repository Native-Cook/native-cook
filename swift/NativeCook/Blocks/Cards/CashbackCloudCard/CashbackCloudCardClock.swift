import SwiftUI
import UIKit

final class CashbackCloudCardClock: ObservableObject {
    @Published var running = false
    @Published var resting = false
    private var wake: DispatchWorkItem?
    // Resting autoplay frames need no display refresh. Wake at most 0.48 s later,
    // preserving the recording clock's 0.5 s wall-time cap.
    private func rest(_ seconds: Double) {
        guard wake == nil, seconds > 0.001 else { return }
        let work = DispatchWorkItem { [weak self] in
            self?.wake = nil
            self?.resting = false
        }
        wake = work
        DispatchQueue.main.async { [weak self] in
            guard let self, self.wake != nil else { return }
            self.resting = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + min(seconds, 0.48), execute: work)
    }
    private var date: Date?
    private var now = 0.0
    private var hold = false
    private var squeeze = 0.0
    private var velocity = 0.0
    private var rainAt = -100.0
    private var lastTouch = -100.0
    private var scripted = false
    private var loopT = 0.0
    private var landed = 0
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()
    func resetDate() { date = nil }
    func touch(_ down: Bool) {
        wake?.cancel(); wake = nil; resting = false
        if !running { date = nil }
        hold = down
        lastTouch = now
        if down { scripted = false }
        running = true
    }
    func sample(_ at: Date, auto: Bool, pinned: Double?) -> CashbackCloudCardMotion.Frame {
        if let pinned { return CashbackCloudCardMotion.at(pinned) }
        let wall = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at
        now += wall
        if auto && !hold && now - lastTouch > 3.5 && (scripted || now - rainAt > 9) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT + wall).truncatingRemainder(dividingBy: 9)
            if loopT < 1 { rest(1 - loopT) }
            return CashbackCloudCardMotion.at(loopT)
        }
        scripted = false
        let dt = min(wall, 0.05)
        velocity += (300 * ((hold ? 1.0 : 0) - squeeze) - 12 * velocity) * dt
        squeeze += velocity * dt
        if hold && squeeze > 0.8 && now - rainAt > 9 { rainAt = now }
        let rainU = now - rainAt < 9 ? now - rainAt : -1
        let n = (0..<6).filter { rainU >= 0 && CashbackCloudCardMotion.coinK(rainU, $0) >= 1 }.count
        if n != landed {
            landed = n
            if n == 6 { success.notificationOccurred(.success) }
            else if n > 0 { selection.selectionChanged() }
        }
        let refill = rainU >= 7 ? CashbackCloudCardMotion.smoother((rainU - 7) / 1) : 0
        if !auto && !hold && rainU < 0 && abs(squeeze) < 0.00001 && abs(velocity) < 0.00001 && running {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return .init(squeeze: max(0, squeeze), rainU: rainU >= 8 ? -1 : rainU, refill: refill)
    }
}
