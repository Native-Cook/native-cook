import Foundation
import Observation
import UIKit

/// Live touch has the RN exponential followers and the same stir integrator as the deterministic script.
@MainActor @Observable
final class SparkSphereScoreDriver {
    private typealias M = SparkSphereScoreMotion
    private enum Phase { case auto, drag, returning, rest }
    var activeAnimation = true
    var accessibilityReading = 0.8
    @ObservationIgnored private var phase = Phase.rest
    @ObservationIgnored private var scene = SparkSphereScoreScene()
    @ObservationIgnored private var pace = SparkSphereScoreAnimation(0.8)
    @ObservationIgnored private var grab = SparkSphereScoreAnimation(0)
    @ObservationIgnored private var offset = SparkSphereScoreAnimation(0)
    @ObservationIgnored private var heat = SparkSphereScoreAnimation(0)
    @ObservationIgnored private var dent = SparkSphereScoreAnimation(0)
    @ObservationIgnored private var clock = 0.0
    @ObservationIgnored private var now = 0.0
    @ObservationIgnored private var lastFrame: Date?
    @ObservationIgnored private var touched = -99.0
    @ObservationIgnored private var pulseT0 = -99.0
    @ObservationIgnored private var tapT0 = -99.0
    @ObservationIgnored private var prevV = 0.8
    @ObservationIgnored private var stir = [0.0, 0, 0]
    @ObservationIgnored private var stirIn = 0.0
    @ObservationIgnored private var initialized = false
    @ObservationIgnored private var reduced = false
    @ObservationIgnored private var lastDetent = 0.0
    @ObservationIgnored private var edgeHit = false
    @ObservationIgnored private var scrubTick = 0.0
    @ObservationIgnored private var downAt = 0.0
    @ObservationIgnored private var pendingSettle = false
    @ObservationIgnored private var band = 1
    @ObservationIgnored private var bandFrom = [0.0, 1, 0, 0]
    @ObservationIgnored private var bandAt = 0.0
    @ObservationIgnored private var a11yReturn: Double?
    @ObservationIgnored private var a11yTarget = 0.8
    @ObservationIgnored private var didHint = false
    @ObservationIgnored private var hintStage = 0
    @ObservationIgnored private(set) var rulerDown = false
    @ObservationIgnored private(set) var probeDown = false
    @ObservationIgnored private var probeTranslation = CGSize.zero
    @ObservationIgnored private var rulerVelocity = 0.0
    @ObservationIgnored private let light = UIImpactFeedbackGenerator(style: .light)
    @ObservationIgnored private let soft = UIImpactFeedbackGenerator(style: .soft)
    @ObservationIgnored private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    @ObservationIgnored private let selection = UISelectionFeedbackGenerator()

    func reset(auto: Bool, reduce: Bool) {
        initialized = true; reduced = reduce
        phase = auto && !reduce ? .auto : .rest
        scene = SparkSphereScoreScene(); pace.set(0.8); grab.set(0); offset.set(0); heat.set(0); dent.set(0)
        clock = 0; now = 0; lastFrame = nil; touched = -99; prevV = 0.8
        pulseT0 = -99; tapT0 = -99; stir = [0, 0, 0]; stirIn = 0
        band = 1; bandFrom = [0, 1, 0, 0]; bandAt = 0
        rulerDown = false; probeDown = false; pendingSettle = false
        a11yReturn = nil; didHint = false; hintStage = 0
        accessibilityReading = 0.8; activeAnimation = !reduce
    }

    func suspend() { lastFrame = nil }
    func setReducedMotion(_ reduce: Bool) {
        // A preference change preserves the current reading, as the RN shared values do.
        reduced = reduce; activeAnimation = true; lastFrame = nil
    }

    func frame(at date: Date, auto: Bool, reduce: Bool, pin: Double?) -> SparkSphereScoreScene {
        if !initialized { reset(auto: auto, reduce: reduce) }
        if let pin {
            scene = .script(pin)
            pace.set(scene.v); grab.set(scene.grab)
            stir = [0, scene.swirl, scene.agit]; prevV = scene.v
            clock = M.wrap(pin)
            return scene
        }
        let dt = M.clamp(lastFrame.map { date.timeIntervalSince($0) } ?? 0.0167, 0.001, 0.05)
        lastFrame = date; now += dt; clock += dt
        let wrapped = clock >= 8
        if wrapped { clock -= 8 }
        if phase == .auto && auto && !reduce {
            let oldAlpha = scene.bandAlpha
            scene = .script(clock); scene.bandAlpha = oldAlpha
            pace.set(scene.v); grab.set(scene.grab)
            stir = [0, scene.swirl, scene.agit]; prevV = scene.v
        } else {
            if let at = a11yReturn, now >= at {
                a11yReturn = nil; pace.spring(0.8, 60, 14)
            }
            if !auto && !reduce && !didHint && now >= 1.4 {
                didHint = true; hintStage = 1; pace.timing(0.8 + 3 * 0.05, 0.42)
            }
            pace.step(dt, reduce: reduce); offset.step(dt, reduce: reduce)
            grab.step(dt, reduce: reduce); heat.step(dt, reduce: reduce); dent.step(dt, reduce: reduce)
            if hintStage == 1 && !pace.moving { hintStage = 2; pace.timing(0.8, 0.62) }
            let v = pace.value, vd = (v - prevV) / dt, oldV = prevV
            prevV = v; scene.v = v
            scene.rim += (v - scene.rim) * (1 - exp(-dt / M.lag[0]))
            scene.hollow += (v - scene.hollow) * (1 - exp(-dt / M.lag[1]))
            scene.film += (v - scene.film) * (1 - exp(-dt / M.lag[2]))
            scene.card += (v - scene.card) * (1 - exp(-dt / M.lag[3]))
            scene.score += (v - scene.score) * (1 - exp(-dt / M.lag[4]))
            let extra = stirIn
            stirIn *= exp(-dt / 0.9)
            M.stepStir(&stir, v, reduce ? 0 : vd, dt, reduce ? 0 : extra)
            scene.swirl = reduce ? 0 : stir[1]; scene.agit = reduce ? 0 : stir[2]
            scene.grab = grab.value; scene.probeHeat = heat.value; scene.dimple = dent.value
            if (oldV - 1) * (v - 1) < 0 { pulseT0 = now }
            scene.pulseAge = now - pulseT0; scene.tapAge = now - tapT0
            if auto && !reduce && phase != .drag && wrapped {
                let settled = abs(v - 0.8) < 0.004 && abs(vd) < 0.05 && abs(scene.swirl) < 0.03
                    && heat.value < 0.02 && grab.value < 0.02
                if now - touched > 2 && settled {
                    phase = .auto; clock = 0; stir = [0, 0, 0]; stirIn = 0
                }
            } else if phase == .returning && abs(v - 0.8) < 0.004 && abs(vd) < 0.05 {
                phase = .rest
            }
            if pendingSettle && !pace.moving {
                pendingSettle = false; light.impactOccurred()
            }
            if phase == .rest {
                let reading = M.jsRound(v * 20) / 20
                if accessibilityReading != reading { accessibilityReading = reading }
            }
        }
        scene.cyc = clock / 8
        updateBand(instant: reduce)
        if reduce {
            let lagDone = [scene.rim, scene.hollow, scene.film, scene.card, scene.score].allSatisfy { abs($0 - pace.value) < 1e-7 }
            let running = rulerDown || probeDown || pace.moving || heat.moving || dent.moving || a11yReturn != nil || !lagDone
            if activeAnimation != running { activeAnimation = running }
        }
        return scene
    }

    private func updateBand(instant: Bool) {
        let b = M.band(scene.card)
        if b != band { bandFrom = scene.bandAlpha; bandAt = now; band = b }
        let p = M.clamp((now - bandAt) / 0.16, 0, 1)
        let ease = instant ? 1 : (p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2)
        scene.bandAlpha = (0..<4).map { M.mix(bandFrom[$0], $0 == band ? 1 : 0, ease) }
    }

    private func toPace(_ x: Double, _ g: SparkSphereScoreLayout) -> Double {
        let x0 = g.rulerX, xMax = x0 + 80 * g.pitch
        let xc = x < x0 ? x0 - M.rubber(x0 - x) : x > xMax ? xMax + M.rubber(x - xMax) : x
        return -1 + (xc - x0) / g.pitch * 0.05
    }
    func beginRuler(x: Double, layout g: SparkSphereScoreLayout) {
        phase = .drag; rulerDown = true; rulerVelocity = 0; touched = now; didHint = true; hintStage = 0
        pace.set(scene.v); pendingSettle = false; a11yReturn = nil
        pulseT0 = now - scene.pulseAge; tapT0 = now - scene.tapAge
        offset.set(scene.v - toPace(x, g)); offset.spring(0, 322.3, 30.5)
        grab.spring(1, 320, 16)
        lastDetent = floor(scene.v / 0.1 + 1e-6); edgeHit = false; scrubTick = now
        activeAnimation = true; light.impactOccurred()
    }
    func moveRuler(x: Double, velocity: Double, layout g: SparkSphereScoreLayout) {
        rulerVelocity = velocity
        let v = toPace(x, g) + offset.value, old = pace.value
        pace.set(v)
        let d = floor(v / 0.1 + 1e-6)
        let crossed = (old - 1) * (v - 1) < 0 || old * v < 0
        if crossed { rigid.impactOccurred() }
        else if d != lastDetent && now - scrubTick > 0.06 { scrubTick = now; selection.selectionChanged() }
        lastDetent = d
        let out = v < -1 || v > 3
        if out && !edgeHit { edgeHit = true; rigid.impactOccurred() }
        else if !out { edgeHit = false }
        if now - touched > 0.1 { touched = now }
    }
    func endRuler(velocity: Double) {
        guard rulerDown else { return }
        rulerDown = false; touched = now; phase = .returning
        grab.spring(0, 320, 16)
        if reduced { pace.timing(0.8, 0.2) }
        else { pace.spring(0.8, 60, 14, velocity: velocity * (0.05 / 4.174)) }
        pendingSettle = true
    }
    func cancelRuler() { endRuler(velocity: rulerVelocity) }
    func beginProbe(point: CGPoint, layout g: SparkSphereScoreLayout) {
        downAt = now; touched = now; probeDown = true
        probeTranslation = .zero
        if phase == .auto {
            phase = .returning; pulseT0 = now - scene.pulseAge
            pace.spring(0.8, 60, 14)
        }
        scene.probeX = (point.x - g.discR) / g.discScale
        scene.probeY = (point.y - g.discR) / g.discScale
        heat.spring(1, 157.9, 22.1); activeAnimation = true; soft.impactOccurred()
    }
    func moveProbe(point: CGPoint, velocity: CGSize, translation: CGSize, layout g: SparkSphereScoreLayout) {
        probeTranslation = translation
        let x = point.x - g.discR, y = point.y - g.discR, r = hypot(x, y)
        scene.probeX = x / g.discScale; scene.probeY = y / g.discScale
        let near = g.discR - r < 24 * g.discScale && r > g.discR * 0.5
        dent.spring(near ? 6 / 163.6 : 0, 220, 14)
        if near { scene.dimpleAngle = atan2(y, x) }
        let vt = r > 8 ? (x * velocity.height - y * velocity.width) / r : 0
        stirIn = M.clamp(0.012 * vt, -4, 4)
    }
    func endProbe(translation: CGSize) {
        guard probeDown else { return }
        probeDown = false; touched = now
        heat.timing(0, 0.6); dent.spring(0, 220, 14)
        if abs(translation.width) < 8 && abs(translation.height) < 8 && now - downAt < 0.35 && M.cold(scene.rim) > 0.5 {
            tapT0 = now; rigid.impactOccurred()
        }
    }
    func cancelProbe() { endProbe(translation: probeTranslation) }
    func adjust(_ direction: Double) {
        a11yTarget = M.clamp(M.jsRound((accessibilityReading + direction * 0.1) / 0.05) * 0.05, -1, 3)
        accessibilityReading = a11yTarget; phase = .returning; touched = now
        pace.timing(a11yTarget, 0.2); a11yReturn = reduced ? now : now + 2
        activeAnimation = true
    }
}
