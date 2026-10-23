import Observation
import QuartzCore
import UIKit

@MainActor
@Observable
final class PullLeverRefreshModel: NSObject {
    private typealias Motion = PullLeverRefreshMotion
    enum Label { case none, refreshing, updated }

    private enum Phase { case idle, pulling, armed, refreshing, done }
    private enum Curve { case linear, quadInOut, sinOut, sinInOut, cubic(Double, Double, Double, Double) }
    private enum Segment {
        case delay(Double)
        case timing(Double, Double, Curve)
        case spring(Double, Double, Double, Double, Double)
    }

    private struct Track {
        var value: Double
        var segments: [Segment] = []
        var repeats: [Segment] = []
        var elapsed = 0.0
        var start = 0.0
        var velocity = 0.0
        var initialEnergy = 0.0
        var initialized = false

        mutating func set(_ value: Double) {
            self.value = value
            segments.removeAll()
            repeats.removeAll()
            resetSegment()
        }

        mutating func run(_ segments: [Segment], repeating repeats: [Segment] = []) {
            self.segments = segments
            self.repeats = repeats
            resetSegment()
        }

        mutating func advance(_ frameDelta: Double) {
            if segments.isEmpty {
                guard !repeats.isEmpty else { return }
                segments = repeats
                resetSegment()
            }
            guard let segment = segments.first else { return }
            if !initialized { initialize(segment) }
            switch segment {
            case let .delay(duration):
                elapsed += frameDelta
                if elapsed + 1e-12 >= duration { finishSegment() }
            case let .timing(target, duration, curve):
                guard duration > 0 else {
                    value = target
                    finishSegment()
                    return
                }
                elapsed += frameDelta
                value = start + (target - start) * Self.ease(elapsed / duration, curve)
                if elapsed + 1e-12 >= duration {
                    value = target
                    finishSegment()
                }
            case let .spring(target, damping, stiffness, mass, _):
                    let step = min(frameDelta, Motion.Clock.maximumDelta)
                    let x = value - target
                    let omega0 = sqrt(stiffness / mass)
                    let zeta = damping / (2 * sqrt(stiffness * mass))
                    if zeta < 1 {
                        let omega1 = omega0 * sqrt(1 - zeta * zeta)
                        let envelope = exp(-zeta * omega0 * step)
                        let sine = sin(omega1 * step)
                        let cosine = cos(omega1 * step)
                        let fragment = envelope * (sine * ((velocity + zeta * omega0 * x) / omega1) + x * cosine)
                        let nextVelocity = -zeta * omega0 * fragment + envelope * (cosine * (velocity + zeta * omega0 * x) - omega1 * x * sine)
                        value = target + fragment
                        velocity = nextVelocity
                    } else {
                        let envelope = exp(-omega0 * step)
                        let fragment = x + (velocity + omega0 * x) * step
                        value = target + envelope * fragment
                        velocity = envelope * (-omega0 * fragment + velocity + omega0 * x)
                    }
                    let energy = 0.5 * stiffness * pow(target - value, 2) + 0.5 * mass * velocity * velocity
                    if initialEnergy == 0 || energy / initialEnergy <= Motion.Clock.springEnergyThreshold {
                        value = target
                        velocity = 0
                        finishSegment()
                    }
            }
        }

        private mutating func initialize(_ segment: Segment) {
            initialized = true
            elapsed = 0
            start = value
            velocity = 0
            initialEnergy = 0
            if case let .spring(target, _, stiffness, mass, requestedVelocity) = segment {
                velocity = requestedVelocity
                if (target > value && velocity < 0) || (target < value && velocity > 0) { velocity = 0 }
                let x = value - target
                // Reanimated clips the running velocity but computes this denominator from config.velocity.
                initialEnergy = 0.5 * stiffness * x * x + 0.5 * mass * requestedVelocity * requestedVelocity
            }
        }

        private mutating func finishSegment() {
            segments.removeFirst()
            resetSegment()
        }

        private mutating func resetSegment() {
            elapsed = 0
            start = value
            velocity = 0
            initialEnergy = 0
            initialized = false
        }

        private static func ease(_ raw: Double, _ curve: Curve) -> Double {
            let t = min(1, max(0, raw))
            switch curve {
            case .linear: return t
            case .quadInOut: return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
            case .sinOut: return sin(t * .pi / 2)
            case .sinInOut: return -(cos(.pi * t) - 1) / 2
            case let .cubic(x1, y1, x2, y2):
                func sample(_ u: Double, _ a: Double, _ b: Double) -> Double {
                    let inv = 1 - u
                    return 3 * inv * inv * u * a + 3 * inv * u * u * b + u * u * u
                }
                var low = 0.0
                var high = 1.0
                for _ in 0..<14 {
                    let mid = (low + high) / 2
                    if sample(mid, x1, x2) < t { low = mid } else { high = mid }
                }
                return sample((low + high) / 2, y1, y2)
            }
        }
    }

    private struct Event {
        enum Kind { case relight(Int), pulseLanded, clearLabel(Int) }
        let at: Double
        let kind: Kind
    }

    private static let restAngle = Motion.Pull.restAngle
    private static let maxAngle = Motion.Pull.maxAngle
    private static let leverPull = Motion.Pull.leverPull
    private static let dots = Double(PullLeverRefreshTheme.Lever.dots)
    private static func seconds(_ milliseconds: Double) -> Double { milliseconds / 1000 }

    private(set) var angle = restAngle
    private(set) var sway = 0.0
    private(set) var idle = 0.0
    private(set) var shake = 0.0
    private(set) var fill = 0.0
    private(set) var lit = 1.0
    private(set) var bloom = 1.0
    private(set) var glow = 0.0
    private(set) var breath = 0.0
    private(set) var pulse = -1.0
    private(set) var ripple = 0.0
    private(set) var knobLit = 0.0
    private(set) var arcLit = 0.0
    private(set) var label = Label.none

    var turn: Double { angle + sway + idle + shake }
    var isRefreshing: Bool { phase == .refreshing }

    private var angleTrack = Track(value: restAngle)
    private var swayTrack = Track(value: 0)
    private var shakeTrack = Track(value: 0)
    private var fillTrack = Track(value: 0)
    private var litTrack = Track(value: 1)
    private var bloomTrack = Track(value: 1)
    private var glowTrack = Track(value: 0)
    private var pulseTrack = Track(value: -1)
    private var rippleTrack = Track(value: 0)
    private var knobTrack = Track(value: 0)
    private var phase = Phase.idle
    private var ticks = 0
    private var grabFrom = 0.0
    private var stretchTicks = 0
    private var refreshID = 0
    private var choreographyID = 0
    private var labelID = 0
    private var events: [Event] = []
    private var time = 0.0
    private var clock = 0.0
    private var demoTime = 0.0
    private var demoPaused: Bool
    private var realDown = false
    private var quiet = 0.0
    private var scriptPulling = false
    private var scriptedRefresh = false
    private var gestureActivated = false
    private var autoPlay: Bool
    private var reduceMotion: Bool
    private var onRefresh: (@MainActor () async throws -> Void)?
    private var displayLink: CADisplayLink?
    private var previousTimestamp: CFTimeInterval?
    private let selection = UISelectionFeedbackGenerator()
    private let notification = UINotificationFeedbackGenerator()

    init(
        autoPlay: Bool = false,
        reduceMotion: Bool = false,
        onRefresh: (@MainActor () async throws -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        self.onRefresh = onRefresh
        demoPaused = !autoPlay
        super.init()
    }

    func configure(autoPlay: Bool, reduceMotion: Bool, onRefresh: (@MainActor () async throws -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        self.onRefresh = onRefresh
    }

    func startClock() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(frame(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stopClock() {
        displayLink?.invalidate()
        displayLink = nil
        previousTimestamp = nil
    }

    func pointerDown() {
        realDown = true
        quiet = 0
        gestureActivated = false
        guard !demoPaused else { return }
        demoPaused = true
        if scriptPulling {
            scriptPulling = false
            pullEnd(silent: true, cancelled: true, velocity: 0)
        }
        if phase == .refreshing, scriptedRefresh {
            scriptedRefresh = false
            runFetch(id: refreshID)
        }
    }

    func pointerMoved(drop: Double) {
        if !gestureActivated, drop > Motion.Interaction.activationDrop {
            gestureActivated = true
            pullBegin(silent: false)
        }
        if gestureActivated { pullMove(drop: drop, silent: false) }
    }

    func pointerEnded(cancelled: Bool, velocityY: Double) {
        realDown = false
        quiet = 0
        if gestureActivated { pullEnd(silent: false, cancelled: cancelled, velocity: velocityY) }
        gestureActivated = false
    }

    func refreshNow() {
        pointerDown()
        realDown = false
        quiet = 0
        guard phase == .idle else { return }
        fillTrack.set(1)
        fill = 1
        startRefresh(silent: false, velocity: 0)
    }

    @objc private func frame(_ link: CADisplayLink) {
        let timestamp = link.targetTimestamp
        let wallDelta = previousTimestamp.map { max(0, timestamp - $0) }
            ?? Motion.Clock.nominalDelta
        previousTimestamp = timestamp
        let delta = min(wallDelta, Motion.Clock.maximumDelta)
        advance(frameDelta: delta)
    }

    private func advance(frameDelta: Double) {
        defer { publishValues() }
        time += frameDelta
        angleTrack.advance(frameDelta)
        swayTrack.advance(frameDelta)
        shakeTrack.advance(frameDelta)
        fillTrack.advance(frameDelta)
        litTrack.advance(frameDelta)
        bloomTrack.advance(frameDelta)
        glowTrack.advance(frameDelta)
        pulseTrack.advance(frameDelta)
        rippleTrack.advance(frameDelta)
        knobTrack.advance(frameDelta)
        clock = autoPlay && !demoPaused ? demoTime : clock + frameDelta
        let idleTarget = phase == .idle
            && !realDown
            && !scriptPulling
            && !reduceMotion
            && !Motion.replicaOnly ? 1.0 : 0.0
        idleAmplitude += (idleTarget - idleAmplitude)
            * min(frameDelta * Motion.Clock.idleAmplitudeResponse, 1)
        idle = Motion.Timing.idleAmplitude * idleAmplitude * sin(2 * .pi * clock / Motion.Timing.idlePeriod)
        breath = reduceMotion ? 0 : sin(2 * .pi * clock / Motion.Light.haloBreath)

        processEvents()
        guard autoPlay else { return }
        if demoPaused {
            if !realDown { quiet += frameDelta }
            if quiet >= Self.seconds(Motion.resumeMS), phase == .idle {
                demoTime = 0
                demoPaused = false
            }
            return
        }
        let t0 = demoTime
        let t1 = t0 + frameDelta
        let full = Motion.Demo.full
        scriptLever(t0, t1, down: full.down, moveEnd: full.moveEnd, up: full.up, depth: full.depth)
        if t0 < Motion.Demo.resolve, t1 >= Motion.Demo.resolve { finishRefresh(silent: true, id: refreshID) }
        let partial = Motion.Demo.partial
        scriptLever(t0, t1, down: partial.down, moveEnd: partial.moveEnd, up: partial.up, depth: partial.depth)
        let loop = Self.seconds(Motion.loopMS)
        if t1 >= loop {
            demoTime = t1 - loop
            resetAutoplayLoop()
        } else {
            demoTime = t1
        }
    }

    private func publishValues() {
        angle = angleTrack.value
        sway = swayTrack.value
        shake = shakeTrack.value
        fill = fillTrack.value
        lit = litTrack.value
        bloom = bloomTrack.value
        glow = glowTrack.value
        pulse = pulseTrack.value
        ripple = rippleTrack.value
        knobLit = knobTrack.value
        arcLit = (
            Double(Int(max(fill, 0) * Self.dots + Motion.Interaction.quantizationEpsilon))
                * Double(Motion.Light.arc.dots)
                / Self.dots
        ).rounded()
    }

    private var idleAmplitude = 1.0

    private func pullBegin(silent: Bool) {
        if phase == .refreshing || phase == .done {
            refreshID += 1
            scriptedRefresh = false
            haptic(.cancel, silent: silent)
            label = .none
            let easeOut = Motion.Timing.easeOut
            let curve = Curve.cubic(easeOut.x1, easeOut.y1, easeOut.x2, easeOut.y2)
            swayTrack.run([.timing(0, Self.seconds(Motion.Timing.swayFade), curve)])
            glowTrack.run([.timing(0, Self.seconds(Motion.Timing.swayFade), curve)])
            litTrack.set(1)
            choreographyID += 1
        }
        angleTrack.segments.removeAll()
        angleTrack.repeats.removeAll()
        fillTrack.segments.removeAll()
        fillTrack.repeats.removeAll()
        litTrack.set(1)
        let p = min(1, max(0, (angleTrack.value - Self.restAngle) / (Self.maxAngle - Self.restAngle)))
        grabFrom = p * Self.leverPull
        fillTrack.set(p)
        ticks = Int(p * Self.dots + Motion.Interaction.quantizationEpsilon)
        stretchTicks = 0
        phase = .pulling
    }

    private func pullMove(drop: Double, silent: Bool) {
        guard phase == .pulling || phase == .armed else { return }
        let progress = max(drop + grabFrom, 0) / Self.leverPull
        let p = min(progress, 1)
        let over = max(progress - 1, 0) * Self.leverPull
        let stretch = Motion.replicaOnly
            ? 0
            : Motion.Pull.overAngle * over / (over + Motion.Pull.overSoftness)
        angleTrack.set(Self.restAngle + (Self.maxAngle - Self.restAngle) * p + stretch)
        let nextStretchTicks = Int(floor(stretch / Motion.Pull.overTick))
        if nextStretchTicks > stretchTicks { haptic(.stretch, silent: silent) }
        stretchTicks = nextStretchTicks
        fillTrack.set(p)
        let covered = Int(floor(p * Self.dots + Motion.Interaction.quantizationEpsilon))
        if covered > ticks, p < 1 { haptic(.tick, silent: silent) }
        ticks = covered
        if p >= 1, phase == .pulling {
            phase = .armed
            haptic(.armed, silent: silent)
        } else if p < 1, phase == .armed {
            phase = .pulling
        }
    }

    private func pullEnd(silent: Bool, cancelled: Bool, velocity: Double) {
        if phase == .armed, !cancelled {
            startRefresh(silent: silent, velocity: velocity)
            return
        }
        guard phase == .pulling || phase == .armed else { return }
        phase = .idle
        let home = Motion.Spring.home
        angleTrack.run(reduceMotion
            ? [.timing(Self.restAngle, Self.seconds(Motion.Timing.reduced), .quadInOut)]
            : [.spring(Self.restAngle, home.damping, home.stiffness, home.mass, velocity * Motion.Pull.degPerPt)])
        let drain = max(fillTrack.value * Self.dots, 1) * (Self.seconds(Motion.Timing.dotMs) / 3)
        fillTrack.run([.timing(0, drain, .linear)])
    }

    private func startRefresh(silent: Bool, velocity: Double) {
        phase = .refreshing
        refreshID += 1
        stretchTicks = 0
        label = .refreshing
        haptic(.release, silent: silent)
        let angularVelocity = -min(
            abs(velocity) * Motion.Pull.degPerPt * Motion.Pull.throwBoost,
            Motion.Pull.maxThrow
        )
        let throwUp = Motion.Spring.throwUp
        angleTrack.run(reduceMotion
            ? [.timing(Motion.Timing.upAngle, Self.seconds(Motion.Timing.reduced), .quadInOut)]
            : [.spring(Motion.Timing.upAngle, throwUp.damping, throwUp.stiffness, throwUp.mass, angularVelocity)])
        let easeOut = Motion.Timing.easeOut
        let glowDuration = reduceMotion ? Motion.Timing.reduced : Motion.Timing.glowRise
        glowTrack.run([.timing(1, Self.seconds(glowDuration), .cubic(easeOut.x1, easeOut.y1, easeOut.x2, easeOut.y2))])
        let mainDrain = Double(PullLeverRefreshTheme.Lever.dots - 1) * Self.seconds(Motion.Timing.dotMs)
        fillTrack.run([.timing(
            1 / Self.dots + Motion.Interaction.quantizationEpsilon,
            mainDrain,
            .linear
        )])
        if !reduceMotion, !Motion.replicaOnly {
            let amplitude = Motion.Timing.swayAmplitude
            let half = Self.seconds(Motion.Timing.swayHalfPeriod)
            let cycle: [Segment] = [.timing(-amplitude, half, .sinInOut), .timing(amplitude, half, .sinInOut)]
            swayTrack.run([.delay(half), .timing(amplitude, half / 2, .sinOut)] + cycle, repeating: cycle)
        }
        scriptedRefresh = silent
        if !silent { runFetch(id: refreshID) }
    }

    private func finishRefresh(silent: Bool, id: Int) {
        guard phase == .refreshing, id == refreshID else { return }
        scriptedRefresh = false
        phase = .done
        let easeOut = Motion.Timing.easeOut
        let easeOutCurve = Curve.cubic(easeOut.x1, easeOut.y1, easeOut.x2, easeOut.y2)
        swayTrack.run([.timing(0, Self.seconds(Motion.Timing.swayFade), easeOutCurve)])
        fillTrack.run([.timing(0, Self.seconds(Motion.Timing.dotMs), .linear)])
        let fall = Motion.Spring.fall
        let relax = Self.seconds(Motion.Timing.relaxMs)
        angleTrack.run(reduceMotion
            ? [.timing(Self.restAngle, relax, .quadInOut), .timing(Self.restAngle, Self.seconds(Motion.Timing.reduced), .quadInOut)]
            : [
                .timing(
                    Motion.Timing.relaxAngle,
                    relax,
                    .cubic(
                        Motion.Timing.easeInOut.x1,
                        Motion.Timing.easeInOut.y1,
                        Motion.Timing.easeInOut.x2,
                        Motion.Timing.easeInOut.y2
                    )
                ),
                .spring(Self.restAngle, fall.damping, fall.stiffness, fall.mass, 0)
            ])
        let light = Self.seconds(Motion.Timing.lightMs)
        let dim = Self.seconds(Motion.Timing.dimMs)
        litTrack.run([
            .delay(relax),
            .timing(0, light, easeOutCurve),
            .delay(dim),
            .timing(1, light, easeOutCurve)
        ])
        choreographyID += 1
        events.append(Event(at: time + relax + 2 * light + dim, kind: .relight(choreographyID)))
        silentRelight = silent
    }

    private var silentRelight = false

    private func relight() {
        let easeOut = Motion.Timing.easeOut
        let easeOutCurve = Curve.cubic(easeOut.x1, easeOut.y1, easeOut.x2, easeOut.y2)
        glowTrack.run([.timing(0, Self.seconds(Motion.Timing.pulse) / 2, easeOutCurve)])
        if reduceMotion {
            knobTrack.run([
                .timing(1, Self.seconds(Motion.Timing.knobFlash), easeOutCurve),
                .timing(0, Self.seconds(Motion.Timing.knobFade), easeOutCurve)
            ])
        } else {
            pulseTrack.set(0)
            let pulseDuration = Self.seconds(Motion.Timing.pulse)
            let easeInOut = Motion.Timing.easeInOut
            pulseTrack.run([.timing(1, pulseDuration, .cubic(easeInOut.x1, easeInOut.y1, easeInOut.x2, easeInOut.y2))])
            events.append(Event(at: time + pulseDuration, kind: .pulseLanded))
            let bloom = Motion.Spring.bloom
            bloomTrack.run([
                .timing(Motion.Timing.bloomScale, Self.seconds(Motion.Timing.bloomRise), easeOutCurve),
                .spring(1, bloom.damping, bloom.stiffness, bloom.mass, 0)
            ])
            shakeTrack.run(Motion.Timing.shake.map {
                .timing($0, Self.seconds(Motion.Timing.shakeStep), .quadInOut)
            })
        }
        label = .updated
        haptic(.success, silent: silentRelight)
        labelID += 1
        events.append(Event(at: time + Self.seconds(Motion.Timing.doneLabel), kind: .clearLabel(labelID)))
        phase = .idle
    }

    private func processEvents() {
        let due = events.filter { $0.at <= time }
        events.removeAll { $0.at <= time }
        for event in due {
            switch event.kind {
            case let .relight(id):
                if id != choreographyID { continue }
                if litTrack.segments.isEmpty { relight() } else { events.append(event) }
            case .pulseLanded:
                guard pulseTrack.segments.isEmpty else {
                    events.append(event)
                    continue
                }
                let easeOut = Motion.Timing.easeOut
                let easeOutCurve = Curve.cubic(easeOut.x1, easeOut.y1, easeOut.x2, easeOut.y2)
                pulseTrack.set(-1)
                knobTrack.run([
                    .timing(1, Self.seconds(Motion.Timing.knobFlash), easeOutCurve),
                    .timing(0, Self.seconds(Motion.Timing.knobFade), easeOutCurve)
                ])
                rippleTrack.set(0)
                rippleTrack.run([.timing(
                    1,
                    Self.seconds(Motion.Light.rippleMs),
                    .cubic(easeOut.x1, easeOut.y1, easeOut.x2, easeOut.y2)
                )])
            case let .clearLabel(id): if id == labelID { label = .none }
            }
        }
    }

    private func runFetch(id: Int) {
        let refresh = onRefresh
        Task { [weak self] in
            if let refresh { try? await refresh() }
            else {
                try? await Task.sleep(nanoseconds: UInt64(Motion.fetchMS * 1_000_000))
            }
            self?.finishRefresh(silent: false, id: id)
        }
    }

    private func scriptLever(
        _ t0: Double, _ t1: Double,
        down: Double, moveEnd: Double, up: Double, depth: Double
    ) {
        if t0 < down, t1 >= down {
            scriptPulling = true
            pullBegin(silent: true)
        }
        func drop(at t: Double) -> Double {
            let u = min(1, max(0, (t - down) / (moveEnd - down)))
            return Self.leverPull * depth * ((1 - cos(.pi * u)) / 2)
        }
        if scriptPulling, t1 >= down, t1 < up { pullMove(drop: drop(at: t1), silent: true) }
        if scriptPulling, t0 < up, t1 >= up {
            scriptPulling = false
            let sample = Motion.Interaction.scriptedVelocitySample
            let velocity = (drop(at: moveEnd) - drop(at: moveEnd - sample)) / sample
                + Motion.Interaction.scriptedReleaseBoost
            pullEnd(silent: true, cancelled: false, velocity: velocity)
        }
    }

    private func resetAutoplayLoop() {
        guard !demoPaused else { return }
        angleTrack.set(Self.restAngle)
        swayTrack.set(0)
        shakeTrack.set(0)
        fillTrack.set(0)
        litTrack.set(1)
        bloomTrack.set(1)
        glowTrack.set(0)
        pulseTrack.set(-1)
        rippleTrack.set(0)
        knobTrack.set(0)
        phase = .idle
        label = .none
        ticks = 0
        stretchTicks = 0
        grabFrom = 0
        scriptPulling = false
        scriptedRefresh = false
        events.removeAll()
        idleAmplitude = 1
        clock = demoTime
        idle = Motion.Timing.idleAmplitude
            * sin(2 * .pi * demoTime / Motion.Timing.idlePeriod)
        breath = reduceMotion ? 0 : sin(2 * .pi * demoTime / Motion.Light.haloBreath)
    }

    private enum Haptic { case tick, armed, release, success, nudge, stretch, cancel }

    private func haptic(_ kind: Haptic, silent: Bool) {
        guard !silent else { return }
        switch kind {
        case .tick: selection.selectionChanged()
        case .armed: UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .release: UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        case .stretch: UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        case .cancel: notification.notificationOccurred(.warning)
        case .nudge: UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        case .success: notification.notificationOccurred(.success)
        }
    }
}
