import SwiftUI
import UIKit

enum WaterRipplePhotoMetrics {
    // The shader holds six photos' worth of slots; fewer repeat, more are left out.
    static let slots = 6

    // Ripples: up to `ripples` rings at once, each a packet of waves running out from where it was
    // made, widening and fading as it goes (the shader holds the wave's own numbers).
    static let ripples = 16
    // What a touch leaves: a strong ring where it lands, a lighter one every `trailStep` pt it
    // drags, and one every `stillEvery` s while it rests on the water.
    static let touchStrength = 1.0
    static let trailStrength = 0.5
    static let trailStep = 26.0
    static let stillEvery = 0.6
    static let stillStrength = 0.55

    // The swell to the next photo: a big ring from where the swipe let go, its front carrying the
    // next photo. It runs on a critically damped spring toward a little past the farthest corner,
    // taking the swipe's speed as its own.
    static let swipeDistance = 50.0 // pt up
    static let swipeSpeed = 650.0 // pt/s up
    static let swellOmega = 2.3 // 1/s, natural frequency of the spring
    static let swellOver = 1.18 // spring target, as a multiple of the distance that covers the screen
    static let swellMinSpeed = 520.0 // pt/s, the swell's speed when the swipe was slow
    static let swellMaxSpeed = 1500.0
    static let crossfade = 0.5 // s, the change under reduced motion

    // autoPlay: six beats of loop / 6 s; in each, four raindrops, then a swipe up to the next photo.
    static let loop = 21.0
    static let beat = loop / Double(slots)
    static let rain = [0.25, 0.8, 1.35, 1.8]
    static let swipeAt = 2.15 // s into a beat: a short drag up that leaves four ripples, then the swell
    static let swipeTrail = 0.2
    static let userHold = 4.0

    // Captions cross over as the swell starts.
    static let captionDelay = 0.26
    static let captionIn = 0.65
    static let captionRise = 0.75
    static let captionOut = 0.22

    // The entrance, once the first photo has arrived: it fades in over `photoFade` s on a gentle
    // ease-out (a quad, so the fade reads as a fade) while settling from `photoScale` to full size
    // over `photoSettle` s on the strong ease-out; the header, the caption and the
    // hint follow it in, each rising `textRise` pt over `textFade` s, `textDelays` s after the photo
    // starts. The autoPlay script waits `entrance` s, so the first raindrop falls on a settled picture.
    static let photoFade = 0.7
    static let photoSettle = 1.4
    static let photoScale = 1.06
    static let textFade = 0.7
    static let textRise = 10.0
    static let textDelays = [0.35, 0.52, 0.7]
    static let entrance = 1.6
}

struct WaterRipplePhotoStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
}

struct WaterRipplePhotoSettings {
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    var onChange: ((Int) -> Void)?
}

/// Everything one frame draws.
struct WaterRipplePhotoFrame {
    var stage: WaterRipplePhotoStage
    var time: Double
    var ripples: [Float]
    var current: Int
    var next: Int
    var origin: CGPoint
    var swell: Double
    var mixing: Double
    // Each caption's opacity and lift.
    var captions: [(opacity: Double, offset: Double)]
    // The entrance: the photo's opacity and its settle (0 … 1), and the header's, the caption's
    // and the hint's progress.
    var photoIn: Double
    var photoSettle: Double
    var textIn: [Double]
}

/// Reanimated's default timing curve, an ease-in-out quad.
private func waterRipplePhotoInOutQuad(_ t: Double) -> Double {
    t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2
}

/// A number moved by Reanimated-style timings. A delayed timing lets the one under way carry on
/// through the delay, as `withDelay` does, then starts from wherever that left the value.
struct WaterRipplePhotoTimed {
    private(set) var value: Double
    private var from = 0.0
    private var to = 0.0
    private var time = 0.0
    private var duration = 0.0
    private var curve: (Double) -> Double = { $0 }
    private var running = false
    private var pending: (to: Double, duration: Double, curve: (Double) -> Double)?
    private var wait = 0.0

    init(_ value: Double) { self.value = value }

    mutating func animate(to target: Double, duration: Double, curve: @escaping (Double) -> Double, delay: Double = 0) {
        if delay > 0 {
            pending = (target, duration, curve)
            wait = delay
            return
        }
        pending = nil
        start(target, duration, curve)
    }

    private mutating func start(_ target: Double, _ duration: Double, _ curve: @escaping (Double) -> Double) {
        from = value
        to = target
        time = 0
        self.duration = duration
        self.curve = curve
        running = true
    }

    mutating func step(_ dt: Double) {
        if let next = pending {
            wait -= dt
            if wait <= 0 {
                pending = nil
                start(next.to, next.duration, next.curve)
            }
        }
        guard running else { return }
        time += dt
        let p = min(1, time / max(duration, 0.0001))
        value = from + (to - from) * curve(p)
        if p >= 1 { running = false }
    }
}

/// The water's state, stepped once a frame like the React Native frame callback, and the touch.
@MainActor
final class WaterRipplePhotoEngine {
    private typealias M = WaterRipplePhotoMetrics

    private var time = 0.0
    // x, y, birth time, strength for each ripple slot; `head` is the next slot to write.
    private var ripples = [Float](repeating: 0, count: M.ripples * 4)
    private var head = 0

    private var current = 0
    private var next = 1
    private var origin = CGPoint.zero
    private var swell = 0.0
    private var swellSpeed = 0.0
    private var swellCover = 1.0
    private var swelling = false
    private var mixing = 0.0
    private var caption = 0
    private var shownCaption = -1
    private var captions: [(opacity: WaterRipplePhotoTimed, offset: WaterRipplePhotoTimed)] = []

    private var touching = false
    private var finger = CGPoint.zero
    private var trail = CGPoint.zero
    private var stillFor = 0.0

    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private(set) var ready = false
    // Which photos have arrived, so the swell never carries in one that is not there yet.
    private var arrived = [Bool](repeating: false, count: M.slots)
    private var photoIn = WaterRipplePhotoTimed(0)
    private var photoSettle = WaterRipplePhotoTimed(0)
    private var textIn = M.textDelays.map { _ in WaterRipplePhotoTimed(0) }
    private var stage = WaterRipplePhotoStage(width: 0, height: 0, top: 0, bottom: 0)
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var settings = WaterRipplePhotoSettings(count: M.slots, autoPlay: false, reducedMotion: false, onChange: nil)

    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let light = UIImpactFeedbackGenerator(style: .light)

    init() {
        for i in 0..<M.ripples { ripples[i * 4 + 2] = -10 }
    }

    /// A new set of photos is on its way: nothing has arrived yet.
    func photosReset() {
        arrived = [Bool](repeating: false, count: M.slots)
    }

    /// Photo `slot` has arrived. The first one brings the screen in: the photo fades and settles,
    /// the type follows it, and the script waits for the entrance to finish.
    func photoArrived(_ slot: Int) {
        guard slot >= 0 && slot < arrived.count else { return }
        arrived[slot] = true
        guard slot == 0 && !ready else { return }
        ready = true
        let easeOut: (Double) -> Double = { Ease.out.value(at: $0) }
        photoIn.animate(to: 1, duration: M.photoFade, curve: { 1 - (1 - $0) * (1 - $0) })
        photoSettle.animate(to: 1, duration: M.photoSettle, curve: easeOut)
        for i in textIn.indices {
            textIn[i].animate(to: 1, duration: M.textFade, curve: easeOut, delay: M.textDelays[i])
        }
        hold = M.entrance
    }

    // MARK: Frame

    func step(to date: Date, stage: WaterRipplePhotoStage, settings: WaterRipplePhotoSettings) -> WaterRipplePhotoFrame {
        self.stage = stage
        self.settings = settings
        if captions.count != settings.count {
            // The first caption starts in place; the others wait below.
            captions = (0..<settings.count).map { i in (WaterRipplePhotoTimed(i == 0 ? 1 : 0), WaterRipplePhotoTimed(i == 0 ? 0 : 14)) }
            shownCaption = -1
        }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 { advance(dt) }
        return WaterRipplePhotoFrame(
            stage: stage,
            time: time,
            ripples: ripples,
            current: current,
            next: next,
            origin: origin,
            swell: swell,
            mixing: mixing,
            captions: captions.map { ($0.opacity.value, $0.offset.value) },
            photoIn: photoIn.value,
            photoSettle: photoSettle.value,
            textIn: textIn.map(\.value)
        )
    }

    private func advance(_ dt: Double) {
        time += dt
        let s = stage

        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = 0 }
        } else if looping && ready && s.width > 0 {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for beat in 0..<M.slots {
                let start = Double(beat) * M.beat
                for (k, at) in M.rain.enumerated() where Self.crossed(before, t, start + at) {
                    let n = Double(beat * 11 + k * 3)
                    let x = s.width * (0.14 + 0.72 * Self.scatter(n))
                    let y = s.height * (0.16 + 0.6 * Self.scatter(n + 0.5))
                    ripple(x, y, 0.6 + 0.4 * Self.scatter(n + 0.25))
                }
                // A drag up from low on the screen: four ripples, then the swell from where it let go.
                let x = s.width * (0.35 + 0.3 * Self.scatter(Double(beat * 5 + 2)))
                for k in 0..<4 where Self.crossed(before, t, start + M.swipeAt + Double(k) * M.swipeTrail / 3) {
                    ripple(x, s.height * (0.8 - 0.06 * Double(k)), M.trailStrength)
                }
                if Self.crossed(before, t, start + M.swipeAt + M.swipeTrail + 0.02) {
                    sweep(x, s.height * 0.6, speed: 760, user: false)
                }
            }
        }

        // A finger resting on the water keeps sending out rings.
        if touching {
            stillFor += dt
            if stillFor >= M.stillEvery {
                stillFor = 0
                ripple(finger.x, finger.y, M.stillStrength)
            }
        }

        if swelling {
            // Critically damped spring toward a little past the farthest corner.
            let target = swellCover * M.swellOver
            let w = M.swellOmega
            let v = swellSpeed + (w * w * (target - swell) - 2 * w * swellSpeed) * dt
            swellSpeed = v
            swell += v * dt
            if swell >= swellCover { settle() }
        } else if mixing > 0 {
            mixing = min(1, mixing + dt / M.crossfade)
            if mixing >= 1 { settle() }
        }

        stepCaptions(dt)
        photoIn.step(dt)
        photoSettle.step(dt)
        for i in textIn.indices { textIn[i].step(dt) }
    }

    /// Restarts the captions' timings when another one is shown, as the animated style does.
    private func stepCaptions(_ dt: Double) {
        if caption != shownCaption {
            shownCaption = caption
            let reduced = settings.reducedMotion
            let easeOut: (Double) -> Double = { Ease.out.value(at: $0) }
            let inOut: (Double) -> Double = waterRipplePhotoInOutQuad
            for i in captions.indices {
                if i == caption {
                    captions[i].opacity.animate(to: 1, duration: M.captionIn, curve: easeOut, delay: M.captionDelay)
                    if reduced {
                        captions[i].offset.animate(to: 0, duration: 0, curve: { _ in 1 })
                    } else {
                        captions[i].offset.animate(to: 0, duration: M.captionRise, curve: easeOut, delay: M.captionDelay)
                    }
                } else {
                    captions[i].opacity.animate(to: 0, duration: M.captionOut, curve: inOut)
                    if reduced {
                        captions[i].offset.animate(to: 0, duration: 0, curve: { _ in 1 })
                    } else {
                        captions[i].offset.animate(to: -8, duration: M.captionOut, curve: inOut)
                    }
                }
            }
        }
        for i in captions.indices {
            captions[i].opacity.step(dt)
            captions[i].offset.step(dt)
        }
    }

    // MARK: Water

    /// Starts a ring at `x, y`.
    private func ripple(_ x: Double, _ y: Double, _ strength: Double) {
        let slot = head
        ripples[slot * 4] = Float(x)
        ripples[slot * 4 + 1] = Float(y)
        ripples[slot * 4 + 2] = Float(time)
        ripples[slot * 4 + 3] = Float(strength)
        head = (slot + 1) % M.ripples
    }

    /// Sends the swell from `x, y` toward the next photo, starting at `speed` pt/s.
    private func sweep(_ x: Double, _ y: Double, speed: Double, user: Bool) {
        if swelling || mixing > 0 || settings.count <= 0 { return }
        let upcoming = (current + 1) % settings.count
        guard upcoming < arrived.count && arrived[upcoming] else { return }
        let s = stage
        next = upcoming
        caption = next
        if settings.reducedMotion {
            mixing = 0.001
        } else {
            origin = CGPoint(x: x, y: y)
            swell = 1
            swellSpeed = max(M.swellMinSpeed, min(M.swellMaxSpeed, speed))
            swellCover = Self.coverFrom(x, y, s.width, s.height)
            swelling = true
            ripple(x, y, 1.2)
        }
        if user { light.impactOccurred() }
    }

    /// The next photo has filled the screen: it becomes the current one.
    private func settle() {
        current = next
        swelling = false
        swell = 0
        mixing = 0
        // Reported on the next turn of the main loop: this runs while the timeline draws its frame.
        let onChange = settings.onChange
        let index = current
        DispatchQueue.main.async { onChange?(index) }
    }

    // MARK: Touch

    func touchChanged(start: CGPoint, location: CGPoint) {
        if !touching {
            guard ready else { return }
            hold = M.userHold
            touching = true
            stillFor = 0
            finger = start
            trail = start
            ripple(start.x, start.y, M.touchStrength)
            soft.impactOccurred()
        }
        finger = location
        let moved = hypot(location.x - trail.x, location.y - trail.y)
        if moved >= M.trailStep {
            trail = location
            stillFor = 0
            ripple(location.x, location.y, M.trailStrength)
        }
    }

    func touchEnded(location: CGPoint, translation: CGSize, velocity: CGSize) {
        guard touching else { return }
        touching = false
        hold = M.userHold
        let up = -translation.height
        let speed = -velocity.height
        if up > M.swipeDistance && speed > M.swipeSpeed { sweep(location.x, location.y, speed: speed * 0.8, user: true) }
    }

    /// The system took the touch away (onEnded never fires for that): let go without a swipe.
    func touchCancelled() {
        guard touching else { return }
        touching = false
        hold = M.userHold
    }

    /// The next photo, for VoiceOver.
    func nextPhoto() {
        sweep(stage.width / 2, stage.height / 2, speed: M.swellMinSpeed, user: true)
    }

    // MARK: Helpers

    /// Random in [0, 1) from a number, the same on every run (and as the React Native block's).
    static func scatter(_ n: Double) -> Double {
        let s = sin(n * 91.345 + 47.853) * 43758.5453
        return s - floor(s)
    }

    /// Whether a clock that went from `before` to `now`, wrapping at the loop, passed `at`.
    static func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    /// The swell's radius that reaches every corner of the screen from `x, y`.
    static func coverFrom(_ x: Double, _ y: Double, _ width: Double, _ height: Double) -> Double {
        hypot(max(x, width - x), max(y, height - y)) + 60
    }
}
