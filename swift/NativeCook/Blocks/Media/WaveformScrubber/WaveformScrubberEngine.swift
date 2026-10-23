import SwiftUI
import UIKit

enum WaveformScrubberMetrics {
    // The waveform: `bars` rounded bars across the screen less `side` pt each side. A bar is
    // `barFill` of its slot wide and at most `barHalf` pt either side of the axis at rest.
    static let bars = 60
    static let side = 28.0
    static let barFill = 0.56
    static let barHalf = 28.0
    // Under the finger the bars grow by up to `magnify` (as a share of their height) and `widen` (of
    // their width), falling off over `lensRadius` pt like a bell curve: a loupe dragged along the track.
    static let magnify = 1.15
    static let widen = 0.5
    static let lensRadius = 30.0
    // The waveform's canvas: the bubble rides above the bars, the times sit under them.
    static let axisY = 116.0
    static let canvasHeight: CGFloat = 206
    static let timesY = 196.0
    static let bubbleHeight = 28.0
    static let bubbleGap = 12.0
    static let tail = 6.0

    // While it plays each bar sways by up to `dance` of its height on two sines of its own, and the
    // bars around the playhead throb on every beat at `bpm`.
    static let dance = 0.14
    static let beat = 0.22
    static let bpm = 104.0

    // Dragging: the playhead follows the finger through a quick low-pass (`follow` per second). On
    // release it is thrown `throwTime` s ahead by the finger's speed and lands on the nearest bar's
    // start with a spring that overshoots a touch. Past either end it is held back with rubber banding.
    static let follow = 32.0
    static let throwTime = 0.12
    static let snapStiffness = 170.0
    static let snapDamping = 19.0
    static let rubber = 7.0
    static let seekStep = 10.0
    // A selection tick each time a scrub crosses a `tickEvery` s mark.
    static let tickEvery = 30.0

    // The play button and its icon morph.
    static let button: CGFloat = 76
    static let morphDuration = 0.42
    static let morphRatio = 0.68
    static let coverDuration = 0.56
    static let coverRatio = 0.74
    static let coverPaused = 0.86

    // autoPlay script (seconds into `loop`). It starts paused at `restAt` s into the track, plays,
    // scrubs forward to `scrubTo` and back to `scrubBack`, lets go (the playhead snaps to the start of
    // that bar, 44 s), plays on and pauses again exactly at `restAt`, so one period loops cleanly.
    static let loop = 12.0
    static let restAt = 47.8
    static let playAt = 0.5
    static let grabAt = 3.4
    static let forwardEnd = 5.3
    static let backStart = 5.7
    static let backEnd = 7.3
    static let releaseAt = 7.5
    static let pauseAt = 11.3
    static let scrubTo = 190.0
    static let scrubBack = 45.6
    // A touch stops the script for `userHold` s, then the player settles back and the loop restarts.
    static let userHold = 4.0
    static let settleLead = 0.9

    // The icon morph: a play triangle cut in two halves at its middle, and the two bars of pause,
    // each four corners in the same order (top left, top right, bottom right, bottom left), centred
    // on the button.
    static let playLeft: [Double] = [-7, -13, 3.5, -7.2, 3.5, 7.2, -7, 13]
    static let playRight: [Double] = [3.5, -7.2, 14, 0, 14, 0, 3.5, 7.2]
    static let pauseLeft: [Double] = [-10, -12, -3, -12, -3, 12, -10, 12]
    static let pauseRight: [Double] = [3, -12, 10, -12, 10, 12, 3, 12]
    static let iconCorner = 2.6

    static let imageFade = 0.3

    /// Loudness of each bar, 0…1: a quiet intro, two choruses around a dip, and an outro.
    static let levels: [Double] = (0..<bars).map { i in
        let t = Double(i) / Double(bars - 1)
        let shape = 0.32
            + 0.5 * exp(-pow((t - 0.36) / 0.16, 2))
            + 0.58 * exp(-pow((t - 0.74) / 0.14, 2))
            + 0.18 * sin(t * 9.2) * (1 - t)
        // A fixed hash, so the track looks the same every time.
        let grain = (sin(Double(i) * 127.1 + 311.7) * 43758.5453).truncatingRemainder(dividingBy: 1)
        let jitter = 0.62 + 0.38 * abs(grain)
        return min(1, max(0.14, shape * jitter * min(1, 0.35 + t * 5)))
    }

    /// 3:07 for 187 s.
    static func clock(_ seconds: Double) -> String {
        // NaN or ±Infinity would trap in Int().
        guard seconds.isFinite else { return "0:00" }
        let s = Int(max(0, floor(seconds)))
        return "\(s / 60):\(s % 60 < 10 ? "0" : "")\(s % 60)"
    }

    /// Distance past an edge, rubber-banded: it gives a little at first and less and less after.
    static func rubber(_ over: Double, _ limit: Double) -> Double {
        limit * (1 - 1 / (1 + over / limit))
    }

    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// cubic-bezier(0.65, 0, 0.35, 1), as the RN script eases its scrubs.
    static let easeInOut = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.65, y: 0),
        endControlPoint: UnitPoint(x: 0.35, y: 1)
    )

    /// Reanimated's default timing curve, ease-in-out quad.
    static func quadInOut(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }
}

/// The waveform's horizontal geometry, from the width of its canvas.
struct WaveformScrubberLayout: Equatable {
    var width: Double
    var duration: Double

    var left: Double { WaveformScrubberMetrics.side }
    var span: Double { max(1, width - WaveformScrubberMetrics.side * 2) }
    var slot: Double { span / Double(WaveformScrubberMetrics.bars) }

    /// Seconds under an x on the waveform, rubber-banded past either end.
    func timeAt(_ x: Double) -> Double {
        let raw = (x - left) / span * duration
        if raw < 0 { return -WaveformScrubberMetrics.rubber(-raw, WaveformScrubberMetrics.rubber) }
        if raw > duration { return duration + WaveformScrubberMetrics.rubber(raw - duration, WaveformScrubberMetrics.rubber) }
        return raw
    }

    func xOf(_ seconds: Double) -> Double { left + seconds / duration * span }
}

struct WaveformScrubberSettings {
    var layout: WaveformScrubberLayout
    var autoPlay: Bool
    var reducedMotion: Bool
    var onSeek: ((Double) -> Void)?
}

// MARK: - Springs

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct WaveformScrubberSpring {
    let target: Double
    private(set) var velocity: Double
    private let zeta: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, carried: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        var v = carried + velocity
        // Velocity that would carry the first frame away from the target is dropped.
        if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
        self.velocity = v
        let x0 = value - target
        let zeta = dampingRatio
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - Self.energyThreshold
        }
        // Bisection, as Reanimated solves it.
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > Self.energyThreshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        if !k.isFinite { k = 170 }
        stiffness = k
        self.zeta = zeta
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        // Reanimated measures the starting energy with the configured velocity alone.
        initialEnergy = energy(x0, velocity, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 {
            value = target
            velocity = 0
            return true
        }
        let x0 = value - target
        let v0 = velocity
        let t = dt
        if zeta < 1 {
            let sin1 = sin(omega1 * t)
            let cos1 = cos(omega1 * t)
            let envelope = exp(-zeta * omega0 * t)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * t)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * t)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
        }
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A value Reanimated would animate on its own: a spring, or a timing on the default ease-in-out.
struct WaveformScrubberDriven {
    private enum Motion {
        case spring(WaveformScrubberSpring)
        case timing(from: Double, to: Double, duration: Double, elapsed: Double)
    }

    private(set) var value: Double
    private var motion: Motion?

    init(_ value: Double) { self.value = value }

    mutating func spring(to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0) {
        var carried = 0.0
        if case let .spring(running) = motion { carried = running.velocity }
        motion = .spring(WaveformScrubberSpring(
            from: value, to: target, velocity: velocity, carried: carried, duration: duration, dampingRatio: dampingRatio
        ))
    }

    mutating func time(to target: Double, duration: Double) {
        motion = .timing(from: value, to: target, duration: duration, elapsed: 0)
    }

    mutating func step(dt: Double) {
        switch motion {
        case .spring(var spring):
            let done = spring.step(&value, dt: dt)
            motion = done ? nil : .spring(spring)
        case let .timing(from, to, duration, elapsed):
            let t = min(1, (elapsed + dt) / duration)
            value = from + (to - from) * WaveformScrubberMetrics.quadInOut(t)
            motion = t >= 1 ? nil : .timing(from: from, to: to, duration: duration, elapsed: elapsed + dt)
        case nil:
            break
        }
    }
}

// MARK: - The engine

/// One frame to draw.
struct WaveformScrubberFrame {
    var time: Double
    var progress: Double
    var energy: Double
    var lens: Double
    var lensX: Double
    var morph: Double
    var cover: Double
    var press: Double
    var nudge: Double
    var coverAlpha: Double
    var backdropAlpha: Double
}

/// The player's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class WaveformScrubberEngine {
    private typealias M = WaveformScrubberMetrics

    // Position in the track, seconds. `playing` is what the button shows.
    private var progress = M.restAt
    private(set) var playing = false
    // 0…1 eases toward playing, so the bars start and stop dancing softly.
    private var energy = 0.0
    private var time = 0.0
    // The finger on the waveform, in pt, and the loupe that follows it.
    private var dragging = false
    private var fingerX = 0.0
    private var lensX = 0.0
    private var lens = 0.0
    private var scrubSpeed = 0.0
    private var lastHead = M.restAt
    // The snap after a release: a spring on the playhead toward `snapTarget`.
    private var snapping = false
    private var snapTarget = M.restAt
    private var snapVelocity = 0.0
    private var morph = WaveformScrubberDriven(0)
    private var cover = WaveformScrubberDriven(0)
    private var press = WaveformScrubberDriven(0)
    private var nudge = WaveformScrubberDriven(0)
    private var byUser = false
    private var scripted = false
    private var loopClock = 0.0
    private var looping = false
    private var hold = 0.0
    private var grabbedAt = M.restAt
    private var lastTick = -1
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var settings = WaveformScrubberSettings(
        layout: WaveformScrubberLayout(width: 0, duration: 240), autoPlay: false, reducedMotion: false
    )
    // When each photo arrived, on the block's clock, for its fade.
    private var coverAt: Double?
    private var backdropAt: Double?
    // The play button's touch under way.
    private var buttonDown: Date?
    private var scrubTouch = false

    private let impact = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    func coverArrived() { coverAt = time }
    func backdropArrived() { backdropAt = time }

    func step(to date: Date, settings: WaveformScrubberSettings) -> WaveformScrubberFrame {
        self.settings = settings
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            loopClock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        if lastDate == nil || raw > 0 {
            lastDate = date
            let dt = min(raw, 0.064)
            if dt > 0 {
                // Reanimated's own animations run whatever the waveform is doing.
                morph.step(dt: dt)
                cover.step(dt: dt)
                press.step(dt: dt)
                nudge.step(dt: dt)
                if settings.layout.span > 1 { advance(dt: dt) }
            }
        }
        func fade(_ at: Double?) -> Double { at.map { min(1, max(0, (time - $0) / M.imageFade)) } ?? 0 }
        return WaveformScrubberFrame(
            time: time,
            progress: progress,
            energy: energy,
            lens: lens,
            lensX: lensX,
            morph: morph.value,
            cover: cover.value,
            press: press.value,
            nudge: nudge.value,
            coverAlpha: fade(coverAt),
            backdropAlpha: fade(backdropAt)
        )
    }

    private var duration: Double { settings.layout.duration }
    private var reducedMotion: Bool { settings.reducedMotion }

    private func setPlaying(_ on: Bool, user: Bool) {
        playing = on
        if reducedMotion {
            morph.time(to: on ? 1 : 0, duration: 0.16)
            cover.time(to: on ? 1 : 0, duration: 0.2)
        } else {
            morph.spring(to: on ? 1 : 0, duration: M.morphDuration, dampingRatio: M.morphRatio)
            cover.spring(to: on ? 1 : 0, duration: M.coverDuration, dampingRatio: M.coverRatio)
        }
        if user { impact.impactOccurred() }
    }

    private func snapTo(_ seconds: Double, velocity: Double) {
        snapTarget = min(duration, max(0, seconds))
        snapVelocity = velocity
        snapping = true
    }

    private func beginScrub(_ x: Double, user: Bool) {
        byUser = user
        scripted = !user
        snapping = false
        dragging = true
        fingerX = x
        // The loupe opens where the finger lands rather than sliding in from the last scrub.
        if lens < 0.05 { lensX = x }
        scrubSpeed = 0
        lastHead = progress
    }

    private func endScrub(velocityX: Double, user: Bool) {
        guard dragging else { return }
        dragging = false
        scripted = false
        let layout = settings.layout
        let throwBy = velocityX / layout.span * duration * M.throwTime
        let landing = min(duration, max(0, layout.timeAt(fingerX) + throwBy))
        // The start of the bar the playhead lands in.
        let barSeconds = duration / Double(M.bars)
        let target = (landing / barSeconds).rounded(.toNearestOrAwayFromZero) * barSeconds
        if reducedMotion {
            progress = target
            snapping = false
        } else {
            snapTo(target, velocity: scrubSpeed)
        }
        if user {
            impact.impactOccurred()
            settings.onSeek?(target)
        }
    }

    /// Where the scripted finger is, in seconds of the track, `t` s into the loop.
    private func scriptAt(_ t: Double, from: Double) -> Double {
        if t < M.forwardEnd {
            return M.lerp(from, M.scrubTo, M.easeInOut.value(at: (t - M.grabAt) / (M.forwardEnd - M.grabAt)))
        }
        if t < M.backStart {
            return M.scrubTo + 1.6 * sin((t - M.forwardEnd) / (M.backStart - M.forwardEnd) * .pi)
        }
        if t < M.backEnd {
            return M.lerp(M.scrubTo, M.scrubBack, M.easeInOut.value(at: (t - M.backStart) / (M.backEnd - M.backStart)))
        }
        return M.scrubBack
    }

    private func advance(dt: Double) {
        let layout = settings.layout
        time += dt

        // The script, while nobody is touching.
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                // Settle back to the resting state, then start the loop over.
                if playing { setPlaying(false, user: false) }
                snapTo(M.restAt, velocity: 0)
                loopClock = -M.settleLead
            }
        } else if looping {
            let before = loopClock
            var t = before + dt
            if t >= M.loop {
                t -= M.loop
                // At the seam the player is paused at `restAt`; any drift is cleared without a visible jump.
                if !playing && !dragging && !snapping { progress = M.restAt }
            }
            loopClock = t
            func crossed(_ at: Double) -> Bool { before < at && t >= at }
            if crossed(M.playAt) && !playing { setPlaying(true, user: false) }
            if crossed(M.grabAt) {
                grabbedAt = progress
                beginScrub(layout.xOf(progress), user: false)
            }
            if scripted && t >= M.grabAt && t < M.releaseAt { fingerX = layout.xOf(scriptAt(t, from: grabbedAt)) }
            if crossed(M.releaseAt) && scripted { endScrub(velocityX: 0, user: false) }
            if crossed(M.pauseAt) && playing { setPlaying(false, user: false) }
        }

        // The playhead.
        let head = progress
        if dragging {
            let target = layout.timeAt(fingerX)
            progress = head + (target - head) * (1 - exp(-dt * M.follow))
            // The playhead's own speed, in s/s, carried into the snap spring on release.
            let speed = (progress - lastHead) / dt
            scrubSpeed += (speed - scrubSpeed) * 0.4
            lastHead = progress
        } else if snapping {
            if playing { snapTarget = min(duration, snapTarget + dt) }
            let v = snapVelocity + (M.snapStiffness * (snapTarget - head) - M.snapDamping * snapVelocity) * dt
            snapVelocity = v
            progress = head + v * dt
            if abs(snapTarget - progress) < 0.01 && abs(v) < 0.4 {
                progress = snapTarget
                snapping = false
            }
        } else if playing {
            let next = head + dt
            if next >= duration {
                progress = duration
                setPlaying(false, user: false)
            } else {
                progress = next
            }
        }

        let wantEnergy = playing && !reducedMotion ? (dragging ? 0.35 : 1) : 0
        energy += (wantEnergy - energy) * (1 - exp(-dt * 4))
        let wantLens = dragging ? 1.0 : 0.0
        lens += (wantLens - lens) * (1 - exp(-dt * (dragging ? 16 : 9)))
        lensX += (fingerX - lensX) * (1 - exp(-dt * 26))

        // A tick each time a scrub by hand crosses a half-minute mark: detents on the track.
        let tick = dragging && byUser ? Int(floor(min(duration, max(0, progress)) / M.tickEvery)) : -1
        if tick >= 0 && lastTick >= 0 && tick != lastTick { selection.selectionChanged() }
        lastTick = tick
    }

    // MARK: Touches

    private func touched() {
        hold = M.userHold
        // A scripted scrub in flight is handed over to the finger.
        if scripted {
            scripted = false
            dragging = false
        }
    }

    /// The waveform: a pan that takes the finger on touch-down.
    func scrubChanged(_ value: DragGesture.Value) {
        if !scrubTouch {
            // The first event of the gesture is the touch-down.
            scrubTouch = true
            touched()
            beginScrub(value.startLocation.x, user: true)
        }
        if dragging && byUser { fingerX = value.location.x }
    }

    func scrubEnded(_ value: DragGesture.Value) {
        scrubTouch = false
        if byUser { endScrub(velocityX: value.velocity.width, user: true) }
    }

    func buttonChanged() {
        guard buttonDown == nil else { return }
        buttonDown = .now
        touched()
        press.time(to: 1, duration: 0.11)
    }

    /// The play button's tap: a touch lifted inside it within 600 ms toggles the player.
    func buttonEnded(_ value: DragGesture.Value) {
        let held = buttonDown.map { Date.now.timeIntervalSince($0) } ?? 0
        buttonDown = nil
        press.spring(to: 0, duration: 0.32, dampingRatio: 0.6)
        let side = Double(M.button)
        let inside = value.location.x >= 0 && value.location.y >= 0 && value.location.x <= side && value.location.y <= side
        if inside && held <= 0.6 { setPlaying(!playing, user: true) }
    }

    private var tapStarts: [Int: Date] = [:]

    func tapBegan(_ id: Int) {
        if tapStarts[id] == nil { tapStarts[id] = .now }
    }

    /// How long the tap was held, in seconds.
    func tapEnded(_ id: Int) -> Double {
        defer { tapStarts[id] = nil }
        return tapStarts[id].map { Date.now.timeIntervalSince($0) } ?? 0
    }

    /// The play button's accessibility action.
    func togglePlaying() {
        touched()
        setPlaying(!playing, user: true)
    }

    /// Rewind and fast-forward: a tap seeks by `step` seconds with the snap spring.
    func seek(by step: Double) {
        touched()
        let from = snapping ? snapTarget : progress
        if reducedMotion {
            progress = min(duration, max(0, from + step))
        } else {
            snapTo(from + step, velocity: 0)
        }
        nudge.spring(to: 0, duration: 0.38, dampingRatio: 0.5, velocity: step > 0 ? 260 : -260)
        impact.impactOccurred()
    }
}
