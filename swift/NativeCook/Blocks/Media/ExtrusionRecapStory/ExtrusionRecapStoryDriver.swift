import CoreMotion
import Observation
import UIKit

/// The RN frame callback and gesture state machine; all events are touch-only.
@MainActor @Observable
final class ExtrusionRecapStoryDriver {
    typealias M = ExtrusionRecapStoryMotion
    var scene = ExtrusionRecapStoryMotion.ExtrusionRecapStoryScene()
    var paused = false
    private(set) var fingerDown = false
    @ObservationIgnored private var displayLink: CADisplayLink?
    @ObservationIgnored private var target: ExtrusionRecapStoryDisplayTarget?
    @ObservationIgnored private var lastFrame: Double?
    @ObservationIgnored private var clock = 0.0
    @ObservationIgnored private var mode = 0
    @ObservationIgnored private var lastTouch = -1e9
    @ObservationIgnored private var landing = -1e9
    @ObservationIgnored private var origin = 0.0
    @ObservationIgnored private var downAt = 0.0
    @ObservationIgnored private var intent = false
    @ObservationIgnored private var wallCue = false
    @ObservationIgnored private var landedPage = -1
    @ObservationIgnored private var wasLanded = true
    @ObservationIgnored private var autoPlay = false
    @ObservationIgnored private var active = true
    @ObservationIgnored private var voiceOver = false
    @ObservationIgnored private var pin: Double?
    @ObservationIgnored private var pageSpring: ExtrusionRecapStorySpring?
    @ObservationIgnored private var xSpring: ExtrusionRecapStorySpring?
    @ObservationIgnored private var ySpring: ExtrusionRecapStorySpring?
    @ObservationIgnored private var stretchSpring: ExtrusionRecapStorySpring?
    @ObservationIgnored private var tiltXSpring: ExtrusionRecapStorySpring?
    @ObservationIgnored private var tiltYSpring: ExtrusionRecapStorySpring?
    @ObservationIgnored private var steerX = 0.0
    @ObservationIgnored private var steerY = 0.0
    @ObservationIgnored private var tiltX = 0.0
    @ObservationIgnored private var tiltY = 0.0
    @ObservationIgnored private var returning = false
    @ObservationIgnored private var replaying = false
    @ObservationIgnored private var motion = CMMotionManager()
    @ObservationIgnored private var baseline: (roll: Double, pitch: Double)?
    @ObservationIgnored private var light = UIImpactFeedbackGenerator(style: .light)
    @ObservationIgnored private var medium = UIImpactFeedbackGenerator(style: .medium)

    func configure(auto: Bool, reduced: Bool, voiceOver: Bool, active: Bool, pin: Double?) {
        autoPlay = auto
        self.active = active
        self.voiceOver = voiceOver
        if scene.reduced != reduced {
            pageSpring = nil
            scene.progress = floor(scene.progress + 0.5)
            scene.age = 10
        }
        scene.reduced = reduced
        self.pin = pin
        if let pin { scene = M.scene(pin); scene.reduced = reduced }
        if displayLink == nil {
            let target = ExtrusionRecapStoryDisplayTarget(self)
            self.target = target
            let link = CADisplayLink(target: target, selector: #selector(ExtrusionRecapStoryDisplayTarget.tick(_:)))
            link.add(to: .main, forMode: .common)
            displayLink = link
        }
        refreshActivity()
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        target = nil
        motion.stopDeviceMotionUpdates()
        baseline = nil
    }

    func togglePause() { paused.toggle(); refreshActivity() }

    private func refreshActivity() {
        let springs = pageSpring != nil || xSpring != nil || ySpring != nil || stretchSpring != nil || tiltXSpring != nil || tiltYSpring != nil
        let film = autoPlay && !scene.reduced && !voiceOver && !paused
        displayLink?.isPaused = !active || pin != nil || (!springs && !film && !fingerDown)
        lastFrame = nil
        if !active { motion.stopDeviceMotionUpdates() }
    }

    fileprivate func tick(_ timestamp: Double) {
        let delta = max(0, timestamp - (lastFrame ?? (timestamp - 0.01667)))
        let dt = min(delta, 0.05)
        lastFrame = timestamp
        let animationEnabled = !scene.reduced && !voiceOver && active && !paused
        if animationEnabled { clock += dt }
        // Shared-value springs run independently of the RN frame callback, including the 250 ms Reduce Motion timing.
        advanceSprings(min(delta, 0.064))
        if animationEnabled {
            if mode == 0 && autoPlay {
                scene.elapsed += dt
                scene.progress = M.timeline(scene.elapsed)
                scene.age = M.landingAt(scene.elapsed)
            } else {
                scene.age = clock - landing
                if !fingerDown && clock - lastTouch > 2 && autoPlay && mode == 1 {
                    mode = 3
                    returning = true
                    pageSpring = .init(from: scene.progress, to: ceil(scene.progress / 3) * 3, damping: 22.1, stiffness: 157.9,
                                       previousVelocity: pageSpring?.currentVelocity ?? 0)
                }
            }
        }
        scene.steerX = steerX + (scene.reduced ? 0 : tiltX)
        scene.steerY = steerY + (scene.reduced ? 0 : tiltY)
        let isLanded = abs(scene.progress - floor(scene.progress + 0.5)) < 0.002 && !fingerDown
        let page = Int(floor(scene.progress + 0.5))
        if isLanded && !wasLanded && mode == 1 && landedPage != page {
            landedPage = page
            landing = clock
            light.impactOccurred()
        }
        wasLanded = isLanded
        if !fingerDown && !autoPlay && clock - lastTouch > 2 { motion.stopDeviceMotionUpdates() }
        if (scene.reduced || voiceOver || paused || !autoPlay) && !fingerDown && pageSpring == nil && xSpring == nil && ySpring == nil && stretchSpring == nil && tiltXSpring == nil && tiltYSpring == nil {
            displayLink?.isPaused = true
        }
    }

    private func advanceSprings(_ dt: Double) {
        if var spring = pageSpring {
            scene.progress = spring.step(dt)
            pageSpring = spring.done ? nil : spring
            if spring.done && returning {
                returning = false
                mode = 0
                scene.elapsed = 0
                scene.progress = 0
                scene.age = 10
                motion.stopDeviceMotionUpdates()
                baseline = nil
            } else if spring.done && replaying { replaying = false; scene.progress = 0 }
        }
        if var s = xSpring { steerX = s.step(dt); xSpring = s.done ? nil : s }
        if var s = ySpring { steerY = s.step(dt); ySpring = s.done ? nil : s }
        if var s = stretchSpring { scene.stretch = s.step(dt); stretchSpring = s.done ? nil : s }
        if var s = tiltXSpring { tiltX = s.step(dt); tiltXSpring = s.done ? nil : s }
        if var s = tiltYSpring { tiltY = s.step(dt); tiltYSpring = s.done ? nil : s }
    }

    func begin() {
        pageSpring = nil; xSpring = nil; ySpring = nil; tiltXSpring = nil; tiltYSpring = nil
        pin = nil
        mode = 1
        returning = false; replaying = false
        fingerDown = true
        downAt = CACurrentMediaTime()
        lastTouch = clock
        scene.progress = M.wrap(scene.progress)
        origin = scene.progress
        intent = false
        wallCue = false
        landedPage = Int(floor(origin + 0.5))
        light.impactOccurred()
        if !scene.reduced && active { startMotion() }
        refreshActivity()
    }

    func move(dx: Double, dy: Double, velocityX: Double) {
        if abs(dx) >= 10 { intent = true }
        if intent {
            let raw = origin - dx / 300, clamped = M.clamp(raw, 0, 2), over = abs(raw - clamped)
            scene.stretch = 0.3 * 0.25 * over / (1 + 0.25 * over)
            stretchSpring = nil
            if !scene.reduced { scene.progress = clamped }
            scene.speed = abs(velocityX / 300)
            if raw.truncatingRemainder(dividingBy: 1) > 0.55 && !wallCue { wallCue = true; medium.impactOccurred() }
        } else if !scene.reduced {
            steerX = M.clamp(dx / 40, -1, 1) * M.steerAngle
            steerY = M.clamp(dy / 40, -1, 1) * M.steerAngle
        }
        scene.steerX = steerX + tiltX
        scene.steerY = steerY + tiltY
    }

    func end(dx: Double, velocityX: Double, rightHalf: Bool, cancelled: Bool = false) {
        guard fingerDown else { return }
        if !cancelled {
            let velocity = -velocityX / 300
            var target = floor(scene.progress + 0.5)
            if intent {
                let delta = -dx / 300, commit = abs(delta) > 0.4 || abs(velocityX) > 600
                if scene.reduced { target = floor(origin + 0.5) + (commit ? sign(delta) : 0) }
                else {
                    target = floor(scene.progress + velocity * 0.12 + 0.5)
                    if commit { target = floor(origin + 0.5) + (abs(velocityX) > 600 ? sign(velocity) : sign(delta)) }
                }
            } else if CACurrentMediaTime() - downAt < 0.2 { target = floor(origin + 0.5) + (rightHalf ? 1 : -1) }
            pageSpring = .init(from: scene.progress, to: M.clamp(target, 0, 2), velocity: velocity, reduced: scene.reduced)
        }
        fingerDown = false
        lastTouch = clock
        scene.speed = 0
        xSpring = .init(from: steerX, to: 0, damping: 14, stiffness: 120)
        ySpring = .init(from: steerY, to: 0, damping: 14, stiffness: 120)
        tiltXSpring = .init(from: tiltX, to: 0, damping: 14, stiffness: 120)
        tiltYSpring = .init(from: tiltY, to: 0, damping: 14, stiffness: 120)
        stretchSpring = .init(from: scene.stretch, to: 0)
        refreshActivity()
    }

    func changePage(_ delta: Int) {
        pin = nil; mode = 1; lastTouch = clock; returning = false
        let current = floor(M.wrap(scene.progress) + 0.5), target = M.clamp(current + Double(delta), 0, 2)
        if target == current && delta > 0 { return }
        pageSpring = .init(from: scene.progress, to: target, reduced: scene.reduced,
                           previousVelocity: pageSpring?.currentVelocity ?? 0)
        light.impactOccurred()
        refreshActivity()
    }
    func advance() {
        let current = floor(M.wrap(scene.progress) + 0.5)
        if current < 2 { changePage(1); return }
        pin = nil; mode = 1; lastTouch = clock; returning = false
        light.impactOccurred()
        if scene.reduced { pageSpring = .init(from: scene.progress, to: 0, reduced: true) }
        else {
            scene.progress = current
            pageSpring = .init(from: current, to: 3, damping: 22.1, stiffness: 157.9)
            replaying = true
        }
        refreshActivity()
    }
    private func sign(_ value: Double) -> Double { value == 0 ? 0 : value < 0 ? -1 : 1 }

    private func startMotion() {
        guard motion.isDeviceMotionAvailable, !motion.isDeviceMotionActive else { return }
        motion.deviceMotionUpdateInterval = 0.032
        motion.startDeviceMotionUpdates(to: .main) { [weak self] sample, _ in
            guard let sample else { return }
            MainActor.assumeIsolated {
                guard let self, self.fingerDown else { return }
                let b = self.baseline ?? (roll: sample.attitude.roll, pitch: sample.attitude.pitch)
                self.baseline = b
                self.tiltX = M.clamp(sample.attitude.roll - b.roll, -1, 1) * M.steerAngle
                self.tiltY = M.clamp(sample.attitude.pitch - b.pitch, -1, 1) * M.steerAngle
            }
        }
    }
}

@MainActor private final class ExtrusionRecapStoryDisplayTarget: NSObject {
    weak var driver: ExtrusionRecapStoryDriver?
    init(_ driver: ExtrusionRecapStoryDriver) { self.driver = driver }
    @objc func tick(_ link: CADisplayLink) { driver?.tick(link.timestamp) }
}

/// Analytic damped oscillator, mass 1, with Reanimated's relative-energy stop condition.
private struct ExtrusionRecapStorySpring {
    let from: Double
    let to: Double
    var velocity = 0.0
    var damping = 24.0
    var stiffness = 200.0
    var reduced = false
    var elapsed = 0.0
    var done = false
    private var configuredVelocity = 0.0
    private(set) var currentVelocity = 0.0

    init(from: Double, to: Double, velocity: Double = 0, damping: Double = 24, stiffness: Double = 200,
         reduced: Bool = false, previousVelocity: Double = 0) {
        self.from = from
        self.to = to
        self.damping = damping
        self.stiffness = stiffness
        self.reduced = reduced
        configuredVelocity = velocity
        let v = velocity + previousVelocity
        // Reanimated clips inertia that points away from the new target onStart.
        self.velocity = (to > from && v < 0) || (to < from && v > 0) ? 0 : v
        currentVelocity = self.velocity
    }
    mutating func step(_ dt: Double) -> Double {
        elapsed += dt
        if reduced {
            let t = min(1, elapsed / 0.25)
            let k = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
            done = t == 1
            return from + (to - from) * k
        }
        let a = damping / 2, w = sqrt(max(0.0001, stiffness - a * a)), x0 = from - to
        let b = (velocity + a * x0) / w, envelope = exp(-a * elapsed)
        let wave = x0 * cos(w * elapsed) + b * sin(w * elapsed)
        let x = envelope * wave
        let v = envelope * (-a * wave - x0 * w * sin(w * elapsed) + b * w * cos(w * elapsed))
        currentVelocity = v
        let initial = stiffness * x0 * x0 + configuredVelocity * configuredVelocity
        done = initial == 0 || (stiffness * x * x + v * v) / initial <= 6e-9
        if done { currentVelocity = 0 }
        return done ? to : to + x
    }
}
