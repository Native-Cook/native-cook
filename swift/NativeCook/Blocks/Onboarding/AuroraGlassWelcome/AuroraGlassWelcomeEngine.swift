import SwiftUI
import UIKit

enum AuroraGlassWelcomeMetrics {
    // The horizon: mountains over a black beach, keyed out of their bright sky so the aurora shows
    // through, and reflected in the wet sand.
    static let horizon = "https://images.unsplash.com/photo-1511300636408-a63a89df3482?auto=format&fit=crop&w=1200&h=720&q=80"
    // The photo is drawn horizonWidth times the screen's width, its top horizonTop of the height down,
    // so the peaks rise behind the card; below it the ground is night.
    static let horizonWidth = 1.5
    static let horizonTop = 0.31
    static let horizonRise = 26.0 // pt the horizon rises into place on entrance
    static let horizonAspect = 1200.0 / 720.0
    static let groundFade = 110.0 // pt over which the photo's foot melts into the night ground
    static let night = Color(hex: 0x040815)
    static let ink = Color(hex: 0xf3f6ff)
    static let green = Color(hex: 0x6dffbf)
    static let soft = Color(hex: 0xf3f6ff, opacity: 0.62)
    static let faint = Color(hex: 0xf3f6ff, opacity: 0.45)

    // Tonight's activity, hourly from 18:00 to 04:00; the marker sits on the peak.
    static let activity: [Double] = [1.6, 2.2, 3.0, 4.4, 6.3, 5.6, 4.7, 5.1, 3.8, 2.7, 2.0]
    static let nowIndex = 4
    static let hours = ["18:00", "21:00", "00:00", "03:00"]
    static let chartHeight = 92.0
    static let stats: [(value: String, label: String)] = [("12%", "Cloud cover"), ("18%", "Moonlight"), ("05:40", "Dark until")]
    static let pulses = 5.0 // the "now" marker pulses this many times a loop

    // The self-playing loop: the card tilts left, then right, LOOP s round, while the aurora's flow
    // comes back to where it started on the same period.
    static let loop = 10.0
    static let autoYaw = 9.0 // deg
    static let autoPitch = 4.0 // deg
    static let autoResume = 3.0 // s after a touch
    static let autoFade = 0.9 // s for the autoPlay tilt to come back in

    // Dragging: the card tilts tiltRate deg per pt, freely up to maxTilt, then rubber-bands; let go,
    // it springs flat carrying the finger's speed. The aurora bends towards the finger meanwhile.
    static let tiltRate = 0.09
    static let maxTilt = 16.0
    static let rubber = 0.5
    static let release = Spring(duration: 0.7, bounce: 0.45) // damping ratio 0.55
    static let releaseReduced = Spring(duration: 0.3, bounce: 0)
    static let perspective = 900.0
    static let panSlop = 2.0

    // Entrance, s from appearing: the sky lights up, the horizon rises, the headline climbs out of
    // its lines, the card lifts in and its curve draws on, then the buttons.
    static let skyIn = 1.6
    static let barAt = 0.15
    static let lineAt = [0.26, 0.36]
    static let lineIn = 0.76
    static let subAt = 0.52
    static let cardAt = 0.6
    static let cardIn = 0.85
    static let cardRise = 40.0
    static let curveAt = 0.95
    static let curveIn = 1.15
    static let statsAt = 1.15
    static let statStagger = 0.08
    static let ctaAt = 1.0
    static let itemIn = 0.6

    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    static let easeInOutQuad = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))

    static func rubberBand(_ value: Double) -> Double {
        let over = abs(value) - maxTilt
        if over <= 0 { return value }
        return (value < 0 ? -1 : 1) * (maxTilt + (rubber * maxTilt * over) / (maxTilt + rubber * over))
    }
}

/// A tilt angle that follows the finger, then springs back to flat carrying the finger's speed.
struct AuroraGlassWelcomeTilt {
    private var held = 0.0
    private var from = 0.0
    private var velocity = 0.0
    private var start: Date?
    private var spring = AuroraGlassWelcomeMetrics.release

    func value(at date: Date) -> Double {
        guard let start else { return held }
        let t = max(0, date.timeIntervalSince(start))
        // The spring's travel from `from` to 0 at time t.
        return from + spring.value(target: -from, initialVelocity: velocity, time: t)
    }

    mutating func hold(_ angle: Double) {
        held = angle
        start = nil
    }

    mutating func release(at date: Date, velocity: Double, spring: Spring) {
        from = value(at: date)
        held = 0
        self.velocity = velocity
        self.spring = spring
        start = date
    }
}

/// The aurora's clock, the self-playing tilt and the finger's tilt, read once a frame.
@MainActor
final class AuroraGlassWelcomeEngine {
    private typealias M = AuroraGlassWelcomeMetrics

    private(set) var clock = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var yaw = AuroraGlassWelcomeTilt()
    private var pitch = AuroraGlassWelcomeTilt()
    // How much of the self-playing tilt shows (it fades out on a touch), how much the finger pulls
    // the aurora, and how far the card is lifted under the finger.
    private var gain = Tween(0)
    private var pull = Tween(0)
    private var pressed = Tween(0)
    private var touch = CGPoint(x: 0.5, y: 0.4)
    private var yawStart = 0.0
    private var pitchStart = 0.0
    private var dragging = false
    private var auto = false
    private var reduced = false

    private let soft = UIImpactFeedbackGenerator(style: .soft)

    struct Frame {
        var clock: Double
        var yaw: Double
        var pitch: Double
        var pressed: Double
        var pointer: CGPoint
        var pull: Double
    }

    func step(to date: Date, autoPlay: Bool, reducedMotion: Bool) -> Frame {
        reduced = reducedMotion
        let isAuto = autoPlay && !reducedMotion
        if lastAuto != isAuto {
            gain.retarget(isAuto ? 1 : 0, at: date, duration: lastAuto == nil ? 0 : M.autoFade, curve: .linear)
            lastAuto = isAuto
            auto = isAuto
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(max(raw, 0), 0.04)
        // Under reduced motion the sky still moves, at half speed.
        clock = (clock + dt * (reducedMotion ? 0.5 : 1)).truncatingRemainder(dividingBy: M.loop)

        let t = clock / M.loop * tau
        let k = gain.value(at: date)
        let pullNow = pull.value(at: date)
        // Under autoPlay the light leans with the card; a finger takes over from it.
        let ax = 0.5 + 0.32 * sin(t)
        return Frame(
            clock: clock,
            yaw: yaw.value(at: date) + M.autoYaw * sin(t) * k,
            pitch: pitch.value(at: date) + M.autoPitch * sin(2 * t) * k,
            pressed: pressed.value(at: date),
            pointer: CGPoint(x: ax + (touch.x - ax) * pullNow, y: 0.3 + (touch.y - 0.3) * pullNow),
            pull: max(pullNow, 0.5 * k)
        )
    }

    func touchChanged(_ value: DragGesture.Value, size: CGSize) {
        let now = Date()
        if !dragging {
            dragging = true
            // The self-playing tilt hands over to the finger where it is.
            let t = clock / M.loop * tau
            let k = gain.value(at: now)
            yawStart = yaw.value(at: now) + M.autoYaw * sin(t) * k
            pitchStart = pitch.value(at: now) + M.autoPitch * sin(2 * t) * k
            gain.retarget(0, at: now, duration: 0, curve: .linear)
            pull.retarget(1, at: now, duration: 0.25, curve: M.easeOut)
            pressed.retarget(1, at: now, duration: 0.16, curve: M.easeOut)
            soft.impactOccurred()
        }
        yaw.hold(M.rubberBand(value.translation.width * M.tiltRate + yawStart))
        pitch.hold(M.rubberBand(-value.translation.height * M.tiltRate + pitchStart))
        touch = CGPoint(x: value.location.x / max(1, size.width), y: value.location.y / max(1, size.height))
    }

    func touchEnded(_ value: DragGesture.Value) {
        guard dragging else { return }
        dragging = false
        let now = Date()
        let spring = reduced ? M.releaseReduced : M.release
        yaw.release(at: now, velocity: value.velocity.width * M.tiltRate, spring: spring)
        pitch.release(at: now, velocity: -value.velocity.height * M.tiltRate, spring: spring)
        pull.retarget(0, at: now, duration: 1.2, curve: M.easeOut)
        pressed.retarget(0, at: now, duration: 0.22, curve: M.easeOut)
        if auto {
            gain.retarget(1, at: now.addingTimeInterval(M.autoResume), duration: M.autoFade, curve: M.easeInOutQuad)
        }
    }
}

/// Downloads the horizon photo once and keeps it decoded.
enum AuroraGlassWelcomePhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}
