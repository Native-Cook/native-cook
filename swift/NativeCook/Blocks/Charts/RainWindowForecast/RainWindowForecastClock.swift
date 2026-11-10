import SwiftUI
import UIKit

final class RainWindowForecastClock: ObservableObject {
    typealias M = RainWindowForecastMotion
    struct Sample {
        var time: Double
        var frame: M.Frame
    }
    @Published var clearing = false
    private var date: Date?
    private var now = 0.0
    private var rain = 0.0
    private var manual = false
    private var lastTouch = -100.0
    private var points = [M.Point](repeating: M.Point(), count: M.points)
    private var latest = M.Frame()
    private var fingerAt = -100.0
    private var fingerFrom = 0.0
    private var began = false
    private let soft = UIImpactFeedbackGenerator(style: .soft)

    func reset(_ auto: Bool) {
        date = nil
        now = 0
        rain = 0
        manual = !auto
        points = [M.Point](repeating: M.Point(), count: M.points)
        latest = M.Frame()
        clearing = false
        began = false
    }
    func configure(_ auto: Bool) {
        manual = !auto
        if !auto {
            points = [M.Point](repeating: M.Point(), count: M.points)
            latest = M.Frame()
            fingerFrom = 0
            clearing = false
            began = false
        }
        date = nil
    }
    func suspend() { date = nil }
    func sample(_ at: Date, size: CGSize, auto: Bool, reduced: Bool, pinned: Double?) -> Sample {
        if let pinned {
            return Sample(time: pinned, frame: M.frameAt(pinned, size.width, size.height))
        }
        // The recording clock follows wall time even on slow, GPU-less Cloud simulators.
        let dt = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0))
        date = at
        now += dt
        let previous = rain
        if !reduced { rain = (rain + dt).truncatingRemainder(dividingBy: M.loop) }
        if !manual {
            latest = M.frameAt(rain, size.width, size.height)
        } else {
            latest.wipe = points.map { p in
                M.Point(x: p.x, y: p.y, age: p.age < 0 ? -1 : now - p.age)
            }
            // Reanimated withTiming's default quadratic in-out easing, 150 ms.
            let p = M.seg(now - fingerAt, 0, 0.15)
            let q = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
            latest.finger = fingerFrom * (1 - q)
            latest.fingerPress = 0
            if auto && now - lastTouch > M.regrow && rain < previous {
                points = [M.Point](repeating: M.Point(), count: M.points)
                manual = false
            }
        }
        if clearing && !began && now - lastTouch > 6 {
            DispatchQueue.main.async { [weak self] in self?.clearing = false }
        }
        return Sample(time: rain, frame: latest)
    }
    private func push(_ p: M.Point) {
        points.removeFirst()
        points.append(p)
    }
    func touch(_ point: CGPoint, ended: Bool = false, reduced: Bool = false) {
        lastTouch = now
        if ended {
            began = false
            return
        }
        if !began {
            if reduced && !clearing { date = nil }
            began = true
            if !manual {
                points = latest.wipe.map { p in
                    M.Point(x: p.x, y: p.y, age: p.age < 0 ? -1 : now - p.age)
                }
                manual = true
                fingerFrom = latest.finger
                fingerAt = now
                latest.fingerPress = 0
            }
            push(M.Point())
            push(M.Point(x: point.x, y: point.y, age: now))
            soft.impactOccurred()
        } else if let p = points.last, hypot(point.x - p.x, point.y - p.y) >= 16 {
            push(M.Point(x: point.x, y: point.y, age: now))
        }
        clearing = true
    }
}
