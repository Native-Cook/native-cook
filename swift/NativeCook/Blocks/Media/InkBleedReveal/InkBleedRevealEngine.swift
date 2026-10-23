import SwiftUI
import UIKit

enum InkBleedRevealMetrics {
    // The print: the wet area of the paper that takes the ink, inset from the screen.
    static let printSide: CGFloat = 18
    static let printTop: CGFloat = 56 // below the top inset
    static let printHeight: CGFloat = 0.58 // share of the screen's height
    static let cropMark: CGFloat = 12 // length of the crop marks at the print's corners
    static let cropGap: CGFloat = 7

    // The ink drop: a bead hangs where it will fall, then drops.
    static let beadRadius = 7.0 // pt, a light drop; a fully charged one is twice as wide
    static let beadLift = 46.0 // a held bead rises this far above the finger, so the finger does not hide it
    static let liftTime = 0.35 // s; a tap lets the bead fall right under the finger
    static let beadFollow = 22.0 // 1/s, how quickly the hanging bead catches up with the finger
    static let holdFull = 1.2 // s held for a full, heavy drop
    static let beadAppear = 0.22 // s
    static let fallTime = 0.26 // s from release to contact, falling under gravity
    static let fallDrift = 0.08 // s of the finger's release speed the drop carries as it falls
    static let fallDriftMax = 48.0 // pt
    // Satellite droplets thrown at contact, then soaking into the paper.
    static let splatCount = 9
    static let splatOut = 0.28 // s
    static let splatSoak = 1.5 // s

    // The bleed: the ink front spreads from the drop, fast at first and slowing like ink in paper.
    static let bleedTime = 2.3 // s for a light drop; a heavy one spreads up to 35% faster
    static let band = 40.0 // pt of dark wet ink behind the front before the photo comes through
    static let lobes = 0.16 // lobe depth as a share of the front's radius

    // Reduced motion: the next print crossfades in over this long.
    static let fadeTime = 0.6
    // The prints dry back to paper at the end of the loop.
    static let dryTime = 1.1

    // autoPlay (seconds): from bare paper, a drop every 3.55 s at these spots (shares of the print),
    // then the prints dry away and the paper rests.
    static let loop = 16.0
    static let drops: [(at: Double, x: Double, y: Double, weight: Double)] = [
        (0.35, 0.34, 0.32, 0.45),
        (3.9, 0.7, 0.66, 0.8),
        (7.45, 0.28, 0.74, 0.35),
        (11.0, 0.64, 0.24, 1),
    ]
    static let dropHang = 0.38 // s the auto bead hangs before it falls
    static let dryAt = 14.4
    // A touch pauses the loop this long; it then dries the paper and starts over.
    static let userHold = 7.0

    // Captions rise in once their ink has landed and leave quickly when the next one lands.
    static let captionDelay = 0.16
    static let captionIn = 0.52
    static let captionRise = 0.6
    static let captionOut = 0.22
}

struct InkBleedRevealStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double

    /// The print, inset from the sides, below the top inset.
    var print: CGRect {
        CGRect(
            x: InkBleedRevealMetrics.printSide,
            y: top + InkBleedRevealMetrics.printTop,
            width: width - InkBleedRevealMetrics.printSide * 2,
            height: height * InkBleedRevealMetrics.printHeight
        )
    }
}

/// A droplet thrown at contact, where it is now.
struct InkBleedRevealDroplet {
    var x: Double
    var y: Double
    var r: Double
}

/// Everything one frame draws.
struct InkBleedRevealFrame {
    var stage: InkBleedRevealStage
    var base: Int
    var top: Int
    var drop: CGPoint
    var front: Double
    var seed: Double
    var dry: Double
    var blend: Double
    // The bead and its shadow.
    var beadOpacity: Double
    var beadX: Double
    var beadY: Double
    var beadZ: Double
    var beadRadius: Double
    var beadScale: Double
    var beadStretch: Double
    var droplets: [InkBleedRevealDroplet]
    var dropletOpacity: Double
    // Each caption's opacity and lift, the intro first.
    var captions: [(opacity: Double, offset: Double)]
}

/// A number moved by Reanimated-style timings. A delayed timing lets the one under way carry on
/// through the delay, as `withDelay` does, then starts from wherever that left the value.
struct InkBleedRevealTimed {
    private(set) var value: Double
    private var from = 0.0
    private var to = 0.0
    private var time = 0.0
    private var duration = 0.0
    private var easing: UnitCurve = .linear
    private var running = false
    private var pending: (to: Double, duration: Double, easing: UnitCurve)?
    private var wait = 0.0

    init(_ value: Double) { self.value = value }

    mutating func animate(to target: Double, duration: Double, easing: UnitCurve, delay: Double = 0) {
        if delay > 0 {
            pending = (target, duration, easing)
            wait = delay
            return
        }
        pending = nil
        start(target, duration, easing)
    }

    private mutating func start(_ target: Double, _ duration: Double, _ easing: UnitCurve) {
        from = value
        to = target
        time = 0
        self.duration = duration
        self.easing = easing
        running = true
    }

    mutating func step(_ dt: Double) {
        if let next = pending {
            wait -= dt
            if wait <= 0 {
                pending = nil
                start(next.to, next.duration, next.easing)
            }
        }
        guard running else { return }
        time += dt
        let p = min(1, time / max(duration, 0.0001))
        value = from + (to - from) * easing.value(at: p)
        if p >= 1 { running = false }
    }
}

struct InkBleedRevealSettings {
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    var onReveal: ((Int) -> Void)?
}

/// The ink's state, stepped once a frame like the React Native frame callback, and the touch.
@MainActor
final class InkBleedRevealEngine {
    private typealias M = InkBleedRevealMetrics

    // The bead: 0 none, 1 hanging, 2 falling.
    private var beadPhase = 0
    private var beadX = 0.0
    private var beadY = 0.0
    private var aim = CGPoint.zero
    private var beadShow = 0.0
    private var beadShowTime = 10.0
    private var beadWeight = 0.0
    private var beadHeld = 0.0
    private var beadZ = 1.0
    private var fallClock = 0.0
    private var fallFrom = (x: 0.0, y: 0.0, dx: 0.0, dy: 0.0)
    private var holding = false
    private var byUser = false
    private var touching = false

    // The prints and the ink.
    private var base = -1
    private var top = -1
    private var dropX = 0.0
    private var dropY = 0.0
    private var progress = 0.0
    private var rate = 1 / M.bleedTime
    private var reach = 1.0
    private var seed = 0.0
    private var dry = 1.0
    private var drying = false
    private var drops = 0

    // Droplets thrown at contact.
    private var splatAge = 10.0
    private var splatAt = (x: 0.0, y: 0.0, dx: 0.0, dy: 0.0, weight: 0.0, seed: 0.0)

    // 0 shows the intro, k + 1 the caption of plate k.
    private var caption = 0
    private var shownCaption = -1
    private var captions: [(opacity: InkBleedRevealTimed, offset: InkBleedRevealTimed)] = []

    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private(set) var ready = false
    private var stage = InkBleedRevealStage(width: 0, height: 0, top: 0, bottom: 0)
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var settings = InkBleedRevealSettings(count: 4, autoPlay: false, reducedMotion: false, onReveal: nil)

    private let impact = UIImpactFeedbackGenerator(style: .light)

    func photosArrived() { ready = true }

    // MARK: Frame

    func step(to date: Date, stage: InkBleedRevealStage, settings: InkBleedRevealSettings) -> InkBleedRevealFrame {
        self.stage = stage
        self.settings = settings
        if captions.count != settings.count + 1 {
            captions = Array(repeating: (InkBleedRevealTimed(0), InkBleedRevealTimed(12)), count: settings.count + 1)
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
        return frame()
    }

    private func advance(_ dt: Double) {
        let box = stage.print

        // The script, which waits for the photos.
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                clock = 0
                if base >= 0 || top >= 0 { drying = true }
            }
        } else if looping && ready {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for drop in M.drops {
                if Self.crossed(before, t, drop.at) && beadPhase == 0 {
                    byUser = false
                    hang(box.minX + box.width * drop.x, box.minY + box.height * drop.y, weight: drop.weight)
                }
                if Self.crossed(before, t, drop.at + M.dropHang) && !byUser { release(0, 0) }
            }
            if Self.crossed(before, t, M.dryAt) && base >= 0 { drying = true }
        }

        if beadPhase == 1 {
            beadShowTime += dt
            beadShow = Ease.out.value(at: min(1, beadShowTime / M.beadAppear))
            // The hanging bead follows the finger and swells while it is held; under a finger it
            // rises clear of it once the press turns into a hold.
            if holding {
                beadHeld += dt
                beadWeight = min(1, beadHeld / M.holdFull)
            }
            let lift = holding ? M.beadLift * min(1, beadHeld / M.liftTime) : 0
            let k = 1 - exp(-dt * M.beadFollow)
            beadX += (aim.x - beadX) * k
            beadY += (aim.y - lift - beadY) * k
        } else if beadPhase == 2 {
            beadShowTime += dt
            beadShow = Ease.out.value(at: min(1, beadShowTime / M.beadAppear))
            // Falling: height drops with the square of time, as under gravity.
            fallClock += dt
            let u = min(1, fallClock / M.fallTime)
            beadZ = 1 - u * u
            beadX = fallFrom.x + fallFrom.dx * u
            beadY = fallFrom.y + fallFrom.dy * u
            if u >= 1 { contact(fallFrom.x + fallFrom.dx, fallFrom.y + fallFrom.dy, user: byUser) }
        }

        if top >= 0 {
            let p = min(1, progress + rate * dt)
            progress = p
            if p >= 1 {
                // A print that is still drying away gives way to the new one.
                drying = false
                dry = 1
                base = top
                top = -1
                progress = 0
                settings.onReveal?(base)
            }
        }

        if drying {
            let d = max(0, dry - dt / M.dryTime)
            dry = d
            if d <= 0 {
                drying = false
                base = -1
                dry = 1
                if top < 0 { caption = 0 }
            }
        }
        if drying && top < 0 && dry < 0.6 { caption = 0 }

        splatAge += dt
        stepCaptions(dt)
    }

    /// Restarts the captions' timings when another one is shown, as the animated style does.
    private func stepCaptions(_ dt: Double) {
        if caption != shownCaption {
            shownCaption = caption
            let reduced = settings.reducedMotion
            for i in captions.indices {
                if i == caption {
                    captions[i].opacity.animate(to: 1, duration: M.captionIn, easing: Ease.out, delay: M.captionDelay)
                    if reduced {
                        captions[i].offset.animate(to: 0, duration: 0.0001, easing: .linear)
                    } else {
                        captions[i].offset.animate(to: 0, duration: M.captionRise, easing: Ease.out, delay: M.captionDelay)
                    }
                } else {
                    captions[i].opacity.animate(to: 0, duration: M.captionOut, easing: Ease.out)
                    captions[i].offset.animate(to: reduced ? 0 : -6, duration: reduced ? 0.0001 : M.captionOut, easing: Ease.out)
                }
            }
        }
        for i in captions.indices {
            captions[i].opacity.step(dt)
            captions[i].offset.step(dt)
        }
    }

    private func frame() -> InkBleedRevealFrame {
        let reduced = settings.reducedMotion
        let p = progress
        let breathe = beadPhase == 1 ? 1 + 0.04 * sin(clock * 9 + beadHeld * 7) : 1
        let radius = M.beadRadius * (1 + beadWeight) * (0.7 + 0.3 * beadShow)

        // The droplets, flung out further in the direction the drop was moving.
        var droplets: [InkBleedRevealDroplet] = []
        let s = splatAt
        let u = min(1, splatAge / M.splatOut)
        let out = 1 - (1 - u) * (1 - u) * (1 - u)
        for index in 0..<M.splatCount {
            let a = Double(index) / Double(M.splatCount) * Double.pi * 2 + (Self.scatter(s.seed * 13 + Double(index)) - 0.5) * 2.2
            let ca = cos(a)
            let sa = sin(a)
            let lean = 1 + 0.9 * max(0, ca * s.dx + sa * s.dy)
            let spread = Self.scatter(s.seed * 7 + Double(index) * 3)
            let far = (10 + 46 * spread * spread) * (0.8 + 0.6 * s.weight) * lean
            droplets.append(InkBleedRevealDroplet(
                x: s.x + ca * far * out,
                y: s.y + sa * far * out,
                r: (1.1 + 2.6 * Self.scatter(s.seed * 5 + Double(index) * 11)) * (0.7 + 0.5 * s.weight)
            ))
        }
        let dropletOpacity = splatAge > M.splatOut + M.splatSoak ? 0 : 0.92 * (1 - max(0, (splatAge - M.splatOut) / M.splatSoak))

        return InkBleedRevealFrame(
            stage: stage,
            base: base,
            top: top,
            drop: CGPoint(x: dropX, y: dropY),
            front: reduced ? reach : reach * Self.spread(p),
            seed: seed,
            dry: dry,
            blend: reduced ? p : 1,
            beadOpacity: beadPhase == 0 ? 0 : beadShow,
            beadX: beadX,
            beadY: beadY,
            beadZ: beadZ,
            beadRadius: radius,
            beadScale: (1 + 0.45 * beadZ) * breathe,
            // A falling drop stretches along its fall.
            beadStretch: beadPhase == 2 ? 1 + 0.18 * sin(Double.pi * (1 - beadZ)) : 1,
            droplets: droplets,
            dropletOpacity: dropletOpacity,
            captions: captions.map { ($0.opacity.value, $0.offset.value) }
        )
    }

    // MARK: Ink

    /// Hangs a bead of ink over `x, y`, ready to fall.
    private func hang(_ x: Double, _ y: Double, weight: Double) {
        beadPhase = 1
        beadX = x
        beadY = y
        aim = CGPoint(x: x, y: y)
        beadWeight = weight
        beadZ = 1
        beadShow = 0
        beadShowTime = 0
    }

    /// Lets the bead go, carrying a little of the finger's speed.
    private func release(_ vx: Double, _ vy: Double) {
        guard beadPhase == 1 else { return }
        fallFrom = (beadX, beadY, Self.clampDrift(vx), Self.clampDrift(vy))
        fallClock = 0
        beadPhase = 2
        // An unfinished bleed hurries to finish as this drop lands.
        if top >= 0 { rate = max(rate, (1 - progress) / M.fallTime) }
    }

    /// The drop hits the paper: the next photo starts to bleed in from here.
    private func contact(_ x: Double, _ y: Double, user: Bool) {
        let count = settings.count
        if top >= 0 { base = top }
        let from = top >= 0 ? top : base
        let next = (from + 1 + count) % count
        let weight = beadWeight
        drops += 1
        top = next
        dropX = x
        dropY = y
        progress = 0
        rate = settings.reducedMotion ? 1 / M.fadeTime : (0.85 + 0.35 * weight) / M.bleedTime
        reach = Self.reachFrom(x, y, stage.print)
        seed = Self.scatter(Double(drops)) * 40
        let len = hypot(fallFrom.dx, fallFrom.dy)
        splatAt = (x, y, len > 1 ? fallFrom.dx / len : 0, len > 1 ? fallFrom.dy / len : 0, weight, Double(drops))
        splatAge = 0
        caption = next + 1
        beadPhase = 0
        if user { impact.impactOccurred() }
    }

    // MARK: Touch

    private func touched() {
        hold = M.userHold
        byUser = true
    }

    /// A press anywhere: the first change of a drag that starts with no distance.
    func touchChanged(start: CGPoint, location: CGPoint) {
        if !touching {
            touching = true
            touchBegan(at: start)
        }
        touchMoved(to: location)
    }

    private func touchBegan(at point: CGPoint) {
        guard beadPhase != 2, ready else { return }
        touched()
        holding = true
        beadHeld = 0
        impact.prepare()
        hang(point.x, point.y, weight: 0)
    }

    private func touchMoved(to point: CGPoint) {
        guard holding else { return }
        aim = point
    }

    func touchEnded(velocity: CGSize) {
        touching = false
        guard holding else { return }
        holding = false
        hold = M.userHold
        release(velocity.width, velocity.height)
    }

    /// Drops a medium bead in the middle of the print, for VoiceOver.
    func dropInMiddle() {
        guard ready, beadPhase != 2 else { return }
        let box = stage.print
        hold = M.userHold
        byUser = true
        beadPhase = 1
        beadX = box.midX
        beadY = box.midY
        aim = CGPoint(x: box.midX, y: box.midY)
        beadWeight = 0.5
        beadShow = 1
        beadShowTime = M.beadAppear
        release(0, 0)
    }

    // MARK: Helpers

    /// Ease-out for the ink front: fast as the drop lands, slowing as the paper drinks it.
    static func spread(_ u: Double) -> Double {
        let k = min(1, max(0, u))
        return 1 - (1 - k) * (1 - k) * (1 - k)
    }

    /// Whether a clock that went from `before` to `now`, wrapping at the loop, passed `at`.
    static func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    static func clampDrift(_ v: Double) -> Double {
        max(-M.fallDriftMax, min(M.fallDriftMax, v * M.fallDrift))
    }

    /// Random in [0, 1) from a number, the same on every run (and as the React Native block's).
    static func scatter(_ n: Double) -> Double {
        let s = sin(n * 91.345 + 47.853) * 43758.5453
        return s - floor(s)
    }

    /// How far the ink front must travel from `x, y` to have soaked every corner of the print.
    static func reachFrom(_ x: Double, _ y: Double, _ print: CGRect) -> Double {
        var far = 0.0
        for cx in [print.minX, print.maxX] {
            for cy in [print.minY, print.maxY] { far = max(far, hypot(cx - x, cy - y)) }
        }
        return (far + M.band + 60) / (1 - M.lobes * 0.6)
    }
}
