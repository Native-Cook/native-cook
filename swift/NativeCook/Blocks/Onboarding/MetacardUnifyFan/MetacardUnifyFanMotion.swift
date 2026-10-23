import Foundation
import CoreGraphics

/// A single display clock owns autoplay, direct manipulation and velocity handoff.
final class MetacardUnifyFanMotion {
    private typealias T = MetacardUnifyFanTimeline
    enum Hit { case none, hub, fan, hero }

    var autoPlay = false
    var reduced = false
    var haptic: ((Bool) -> Void)?
    private(set) var u = 0.0
    private(set) var forward = 1.0
    private(set) var spin = -49.0
    private(set) var anchor = -49.0
    private(set) var clock = 0.0
    private(set) var fanPress = 0.0
    private(set) var objectPress = 0.0
    private(set) var yaw = 0.0
    private(set) var pitch = 0.0
    private(set) var paused = false
    private(set) var hit = Hit.none
    private(set) var down = false
    private var epoch: Double?
    private var previous = 0.0
    private var now = 0.0
    private var lastTouch = -10000.0
    private var offset = 0.0
    private var velocity = 0.0
    private var transitionStart = 0.0
    private var transitionFrom = 0.0
    private var transitionDuration = 0.0
    private var transitionDelay = 0.0
    private var transitioning = false
    private var userTransition = false
    private var locked = false
    private var pressStart = 0.0
    private var pressFrom = SIMD2<Double>.zero
    private var pressTo = SIMD2<Double>.zero
    private var pressDuration = 0.12
    private var startPoint = CGPoint.zero
    private var lastAngle = 0.0
    private var initialYaw = 0.0
    private var initialPitch = 0.0
    private var maximumDistance = 0.0
    private var beganAt = 0.0
    private var returning = true
    private var springAt = -1000.0
    private var springPosition = SIMD2<Double>.zero
    private var springVelocity = SIMD2<Double>.zero

    func advance(_ stamp: Double) {
        if epoch == nil {
            epoch = stamp
            previous = stamp
        }
        now = stamp
        let dt = min(max(stamp - previous, 0), 0.1)
        previous = stamp
        if transitioning {
            let p = T.phase(stamp - transitionStart - transitionDelay, 0, transitionDuration)
            u = T.mix(transitionFrom, forward, p)
            if p >= 1 { transitioning = false }
        }
        if autoPlay && paused && !down && stamp - lastTouch > 3.5 && (u == 0 || u == 1) {
            let base = u > 0.5 ? 4.9 : 0
            epoch = stamp - base
            offset = spin - T.loop(base).spin
            paused = false
            userTransition = false
        }
        if autoPlay && !paused {
            let local = (stamp - (epoch ?? stamp)).truncatingRemainder(dividingBy: 12)
            let state = T.loop(local)
            anchor = T.loop(state.forward > 0 ? 3 : 9.4).spin + offset
            u = state.u
            forward = state.forward
            spin = reduced ? -49 : state.spin + offset
            clock = reduced ? 0 : local
            if reduced {
                let still = T.reducedLoop(local)
                u = still.u
                objectPress = still.press
            }
        } else {
            clock = reduced ? 0 : clock + dt
            if !reduced && !down && u == 0 {
                let decay = exp(-dt / 0.6)
                spin += 7.5 * dt + velocity * 0.6 * (1 - decay)
                velocity *= decay
            }
        }
        if !(autoPlay && !paused && reduced) {
            let p = T.phase(stamp - pressStart, 0, pressDuration)
            let amount = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
            fanPress = T.mix(pressFrom.x, pressTo.x, amount)
            objectPress = T.mix(pressFrom.y, pressTo.y, amount)
        }
        if returning {
            let t = max(0, stamp - springAt)
            let ring = sqrt(140.0 - 49.0)
            let value = exp(-7 * t) * (springPosition * cos(ring * t) + (springVelocity + 7 * springPosition) / ring * sin(ring * t))
            yaw = reduced ? 0 : value.x
            pitch = reduced ? 0 : value.y
        }
        if userTransition && forward == 1 && !locked && u * 1900 >= 1025 {
            locked = true
            haptic?(true)
        }
    }

    func toggle() {
        guard u <= 0.0001 || u >= 0.9999 else { return }
        paused = true
        lastTouch = now
        userTransition = true
        locked = false
        anchor = spin
        forward = u < 0.5 ? 1 : 0
        transitionFrom = u
        transitionStart = now
        transitionDuration = reduced ? 0.32 : forward == 1 ? 1.9 : 1.4
        transitionDelay = reduced ? 0.12 : 0
        transitioning = true
        haptic?(false)
    }

    func begin(_ point: CGPoint) {
        let dx = point.x - 201
        let dy = point.y - 356
        let fanHit = dx * dx + dy * dy < 226 * 226
        let hubHit = dx * dx + dy * dy < 66 * 66
        let heroHit = abs(point.x - 201) < 175 && abs(point.y - 522) < 140
        hit = u == 0 && fanHit ? (hubHit ? .hub : .fan) : u == 1 && heroHit ? .hero : .none
        down = true
        lastTouch = now
        if !paused && u > 0 && u < 1 {
            transitionFrom = u
            transitionStart = now
            transitionDuration = reduced ? 0.32 : forward == 1 ? 1.9 * (1 - u) : 1.4 * u
            transitionDelay = 0
            transitioning = true
        }
        paused = true
        velocity = 0
        setPress(SIMD2(fanHit ? 1 : 0, heroHit || hubHit ? 1 : 0), duration: 0.12)
        startPoint = point
        maximumDistance = 0
        beganAt = now
        lastAngle = atan2(dy, dx)
        initialYaw = yaw
        initialPitch = pitch
    }

    func move(_ point: CGPoint, velocity fingerVelocity: CGPoint) {
        maximumDistance = max(maximumDistance, hypot(point.x - startPoint.x, point.y - startPoint.y))
        guard !reduced else { return }
        if hit == .fan {
            let dx = point.x - 201
            let dy = point.y - 356
            guard dx * dx + dy * dy >= 900 else { return }
            let angle = atan2(dy, dx)
            let delta = atan2(sin(angle - lastAngle), cos(angle - lastAngle))
            spin += delta * 180 / .pi
            lastAngle = angle
            velocity = T.clamp((dx * fingerVelocity.y - dy * fingerVelocity.x) / max(900, dx * dx + dy * dy) * 180 / .pi, -600, 600)
        } else if hit == .hero {
            returning = false
            yaw = 26 * tanh((initialYaw + (point.x - startPoint.x) * 0.14) / 26)
            pitch = 26 * tanh((initialPitch - (point.y - startPoint.y) * 0.14) / 26)
        }
    }

    func end(velocity fingerVelocity: CGPoint = .zero, cancelled: Bool = false) {
        down = false
        lastTouch = now
        setPress(.zero, duration: 0.18)
        if hit == .hero {
            returning = true
            springAt = now
            springPosition = SIMD2(yaw, pitch)
            springVelocity = cancelled ? .zero : SIMD2(fingerVelocity.x * 0.14, -fingerVelocity.y * 0.14)
        }
        if !cancelled && maximumDistance <= 8 && now - beganAt < 0.5 && (hit == .hub || hit == .hero) {
            toggle()
        }
        hit = .none
    }

    private func setPress(_ value: SIMD2<Double>, duration: Double) {
        pressFrom = SIMD2(fanPress, objectPress)
        pressTo = value
        pressDuration = duration
        pressStart = now
    }
}
