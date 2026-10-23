import Foundation
import simd

/// One clock owns both the exact scripted curves and the touch state.
final class PrismSphereGeneratingMotion {
    typealias Curves = PrismSphereGeneratingTimeline
    let epoch: Date
    var autoPlay: Bool
    var reduced = false
    private(set) var clock = 0.0
    private(set) var down = false
    private(set) var dragging = false
    private(set) var offset = Curves.identity
    private(set) var velocity = SIMD2<Double>.zero
    private(set) var lastTouch = -100.0
    private(set) var burstAt: Double?
    private(set) var scripted = false
    private(set) var gain = 1.0
    private var releaseCharge = 1.0
    private var heldAt = 0.0
    private var relaxing = false
    private var chargeFrom = 0.0
    private var chargeTo = 0.0
    private var chargeAt = 0.0
    private var chargeDuration = 0.55
    private var lastTranslation = SIMD2<Double>.zero
    private var lightOffset = SIMD2<Double>.zero
    private var lightReleasedAt = -100.0
    var onImpact: (() -> Void)?

    init(autoPlay: Bool, epoch: Date = .now) {
        self.autoPlay = autoPlay
        self.epoch = epoch
    }

    func elapsed(_ date: Date) -> Double { max(0, date.timeIntervalSince(epoch)) }

    private func heldCharge() -> Double {
        chargeFrom + (chargeTo - chargeFrom) * Curves.ease((clock - chargeAt) / chargeDuration)
    }

    private func charge(to target: Double, duration: Double) {
        chargeFrom = heldCharge()
        chargeTo = target
        chargeAt = clock
        chargeDuration = duration
    }

    func advance(to now: Double) {
        let previous = clock
        let dt = max(0, now - previous)
        clock = now
        if down { lastTouch = now }
        if let burstAt, !scripted, now - burstAt > 10 || dt > 10 { self.burstAt = nil }
        if scripted && floor(previous / 10) != floor(now / 10) {
            burstAt = nil
            scripted = false
        }
        let crossed = floor((previous - 6.75) / 10) != floor((now - 6.75) / 10)
        let anticipationAt = floor((now - 6.75) / 10) * 10 + 6.75
        if autoPlay && !reduced && crossed && !down && anticipationAt - lastTouch >= 3.5 {
            burstAt = anticipationAt + 0.55
            gain = 1
            releaseCharge = 1
            scripted = true
        }
        if !down {
            let speed = simd_length(velocity)
            if !reduced && speed >= 0.02 {
                let decay = exp(-dt / 0.85)
                let distance = 0.85 * (1 - decay)
                offset = Curves.trackball(offset, dx: velocity.x * distance, dy: velocity.y * distance, radius: 1)
                velocity = speed * decay < 0.02 ? .zero : velocity * decay
            }
            let sinceTouch = now - lastTouch
            if autoPlay && !reduced && sinceTouch >= 3.5 {
                velocity = .zero
                offset = Curves.returning(offset, dt: min(dt, sinceTouch - 3.5))
            }
            if relaxing && heldCharge() == 0 { relaxing = false }
        }
    }

    func frame(at now: Double) -> PrismSphereGeneratingFrame {
        advance(to: now)
        let pose = Curves.pose(reduced ? 0.58 : Curves.modulo(clock, 5) / 5)
        let values = Curves.values(clock, poseFan: pose.fan,
                                   surgeTime: burstAt.map { clock - $0 + 0.55 } ?? -1,
                                   gain: gain, reduced: reduced,
                                   heldCharge: down || relaxing ? heldCharge() : -1,
                                   heldTime: clock - heldAt, releaseCharge: releaseCharge)
        let rotation = offset * pose.rotation * Curves.axis(0, 1, 0, values.spin)
        let idle = reduced ? 0 : 0.18 * sin(2 * .pi * Curves.modulo(clock, 5) / 5)
        let lightAge = max(0, clock - lightReleasedAt)
        let frequency = sqrt(120.0 - 81.0)
        let lightMix = down ? 1 : exp(-9 * lightAge) * (cos(frequency * lightAge) + 9 / frequency * sin(frequency * lightAge))
        let light = SIMD2(idle * (1 - lightMix), 0) + lightOffset * lightMix
        return PrismSphereGeneratingFrame(clock: clock, sceneClock: reduced ? 0 : Curves.modulo(clock, 600),
                                          rotation: rotation, values: values, light: light,
                                          burstAge: reduced ? -1 : burstAt.map { clock - $0 } ?? -1,
                                          gain: gain, reduced: reduced)
    }

    func begin(at now: Double, point: SIMD2<Double>, radius: Double) {
        let current = frame(at: now)
        let pose = Curves.pose(reduced ? 0.58 : Curves.modulo(clock, 5) / 5)
        offset = current.rotation * pose.rotation.transpose
        velocity = .zero
        burstAt = nil
        scripted = false
        relaxing = false
        down = true
        dragging = false
        lastTranslation = .zero
        lastTouch = clock
        heldAt = clock
        chargeFrom = 0
        chargeTo = 1
        chargeAt = clock
        chargeDuration = 0.55
        lightOffset = simd_clamp(point / radius, SIMD2(repeating: -1), SIMD2(repeating: 1))
    }

    func move(at now: Double, point: SIMD2<Double>, translation: SIMD2<Double>, radius: Double, k: Double) {
        advance(to: now)
        guard down else { return }
        lastTouch = clock
        lightOffset = simd_clamp(point / radius, SIMD2(repeating: -1), SIMD2(repeating: 1))
        if !dragging && simd_length(translation) >= 8 * k {
            dragging = true
            charge(to: 0, duration: 0.2)
        }
        if dragging {
            let delta = translation - lastTranslation
            // Direct manipulation remains available with Reduce Motion; automatic motion does not.
            offset = Curves.trackball(offset, dx: delta.x, dy: delta.y, radius: radius)
            lastTranslation = translation
        }
    }

    func end(at now: Double, velocity releaseVelocity: SIMD2<Double>, radius: Double, success: Bool) {
        advance(to: now)
        guard down else { return }
        down = false
        lastTouch = clock
        if success && !dragging {
            fire(gain: 0.85 + 0.15 * heldCharge(), compression: heldCharge())
        } else {
            velocity = success && !reduced ? releaseVelocity / radius : .zero
            relaxing = true
            charge(to: 0, duration: 0.2)
        }
        lightReleasedAt = clock
    }

    func activate(at now: Double) {
        let current = frame(at: now)
        let pose = Curves.pose(reduced ? 0.58 : Curves.modulo(clock, 5) / 5)
        lastTouch = clock
        offset = current.rotation * pose.rotation.transpose
        velocity = .zero
        down = false
        fire(gain: 0.85, compression: 0)
        lightReleasedAt = clock
    }

    private func fire(gain: Double, compression: Double) {
        burstAt = clock
        scripted = false
        self.gain = reduced ? 0.5 : gain
        releaseCharge = compression
        relaxing = false
        chargeFrom = 0
        chargeTo = 0
        onImpact?()
    }
}

struct PrismSphereGeneratingFrame {
    let clock: Double
    let sceneClock: Double
    let rotation: simd_double3x3
    let values: PrismSphereGeneratingValues
    let light: SIMD2<Double>
    let burstAge: Double
    let gain: Double
    let reduced: Bool

    var scale: Double {
        values.scale * (reduced ? 1 : 0.94 + 0.06 * PrismSphereGeneratingTimeline.entrance(clock / 0.8))
    }
}
