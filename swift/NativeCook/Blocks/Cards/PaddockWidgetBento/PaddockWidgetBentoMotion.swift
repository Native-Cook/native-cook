import Foundation

/// Stable identity for the only cards that participate in reorder motion.
enum PaddockWidgetBentoCardID: String, CaseIterable, Hashable {
    case laps
    case reflex
    case homeCards

    var index: Int { PaddockWidgetBentoCardID.allCases.firstIndex(of: self) ?? 0 }
}

/// Pure geometry and the analytic 9-second demo (motion.ts): pit stop, sticker peel, quick-card rearrangement.
enum PaddockWidgetBentoMotion {
    static let loopDuration = 9.0
    /// Tops of the three quick-card slots on the 402-point screen.
    static let slotY: [Double] = [54.5, 128.5, 202.5]
    static let longPressDuration = 0.320
    static let targetHysteresis = 8.0
    static let outsideCancelDistance = 44.0
    static let defaultOrder: [PaddockWidgetBentoCardID] = [.laps, .reflex, .homeCards]
    static let reflowStiffness = 360.0
    static let reflowDamping = 28.0
    static let reflowMass = 0.9
    static let settleStiffness = 420.0
    static let settleDamping = 30.0
    static let settleMass = 0.8
    static let springSettleDuration = 0.60
    static let quietReflowDuration = 0.12
    static let liftScale = 1.025
    static let liftShadow = 14.0
    static let restShadow = 4.0
    static let maxRotation = 0.7
    static let rotationPerPoint = 0.012
    static let restartDelay = 2.0

    enum Timeline {
        static let editStart = 4.75, editEnd = 5.0, reflowEnd = 5.8, settleEnd = 6.1
        static let holdEnd = 6.3, returnEnd = 7.1, restEnd = 7.45
        static let curve = (0.32, 0.72, 0.0, 1.0)
    }

    /// Pit-stop tiles in reading order; the quick cards keep their stable ids 0-2.
    enum Tile {
        static let violet = 0, orange = 1, yellow = 2, pitcast = 3, stats = 4, glossary = 5, education = 6, hero = 7, guide = 8, banner = 9
        static let count = 10
    }

    enum Pit {
        static let loopStart = 0.55
        static let brace = 0.14
        static let braceScale = 0.965
        static let exit = 0.24
        static let exitPower = 2.2
        static let gap = 0.1
        static let omega = 17.0
        static let zeta = 0.85
        static let launch = 0.9
        static let settle = 0.62
        static let margin = 46.0
        static let width: [Double] = [186, 186, 186, 186, 382, 186, 186, 382, 382, 382]
        static let delay: [Double] = [0, 0.07, 0.14, 0.03, 0.1, 0.19, 0.21, 0.27, 0.33, 0.39]
        static let side: [Double] = [-1, -1, -1, 1, 1, -1, 1, -1, 1, -1]
        static let rise: [Double] = [-26, 0, 22, -34, 10, 16, -16, 24, -20, 40]
        static let tilt: [Double] = [-6, -5, -6, 5, 3, -5, 5, -3, 3, -2.5]
        static let stretchPerSpeed = 1.0 / 22000
        static let maxStretch = 0.18
        static let squashPerAccel = 1.0 / 700000
        static let maxSquash = 0.08
        static let ghosts = 4.0
        static let ghostStep = 0.016
        static let ghostAlpha = 0.55
        static let ghostSpeed = 2400.0
        static let arrive = brace + exit + gap
        static let duration = arrive + (delay.max() ?? 0) + settle
        static let window = (loopStart, loopStart + duration)
    }

    enum Peel {
        static let start = 2.45, tease = 2.62, full = 3.22, hold = 3.55, snap = 3.8, end = 4.35
        static let cornerX = 324.0, cornerY = 124.0, angle = -153.4
        static let teaseDistance = 26.0, maxDistance = 236.0, touchMaxDistance = 290.0
        static let flutter = 7.0, flutterHz = 2.6, wobble = 4.0
        static let release = 0.24
        static let slapPress = 0.03, slapPressTime = 0.16
        static let shake = 2.2, shakeHz = 16.0, shakeDecay = 0.07, slapEnd = 0.55
        static let lines = 7, lineInner = 10.0, lineReach = 26.0, lineLength = 12.0, lineTime = 0.3
        static let foldShade = 34.0
        static let border = 6.0
        static let shadowBlur = 5.0, shadowOffset = 5.0
        static let hint = 16.0
    }

    enum Jiggle { static let amplitude = 1.2, hz = 5.6, phase = 2.1 }
    enum Goo { static let blur = 6.5, gain = 18.0, bias = 8.0, pad = 28.0 }
    enum Pull { static let trigger = 72.0, flick = 900.0, flickShare = 0.5 }

    /// Time ranges (seconds) of the loop in which the demo needs 60 Hz frames.
    static let activeRanges: [(Double, Double)] = [Pit.window, (Peel.start, Peel.end), (Timeline.editStart, Timeline.restEnd)]

    enum Phase: Equatable {
        case rest, pitStop, peel, editCue, reflow, previewHold, returnReflow, settle
    }

    struct Frame: Equatable {
        var phase: Phase
        var order: [PaddockWidgetBentoCardID]
        var cardY: [PaddockWidgetBentoCardID: Double]
        var cardOpacity: [PaddockWidgetBentoCardID: Double]
        var handlesOpacity: Double
        var selectedID: PaddockWidgetBentoCardID?
        var selectedScale: Double
        var shadowRadius: Double
        var rotationDegrees: Double
        var isDemo: Bool
        /// Seconds into the loop while the demo plays, -1 otherwise.
        var loopTime: Double
    }

    static let validOrders: [[PaddockWidgetBentoCardID]] = [
        [.laps, .reflex, .homeCards], [.laps, .homeCards, .reflex],
        [.reflex, .laps, .homeCards], [.reflex, .homeCards, .laps],
        [.homeCards, .laps, .reflex], [.homeCards, .reflex, .laps],
    ]

    static func isValid(_ order: [PaddockWidgetBentoCardID]) -> Bool {
        order.count == 3 && Set(order).count == 3 && Set(order) == Set(defaultOrder)
    }

    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ value: Double) -> Double {
        let t = clamp(value)
        return t * t * (3 - 2 * t)
    }
    static func easeOut(_ value: Double) -> Double {
        let t = clamp(value)
        return 1 - (1 - t) * (1 - t) * (1 - t)
    }
    static func cubicBezier(_ value: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(value)
        func coordinate(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let u = 1 - t
            return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t
        }
        var low = 0.0
        var high = 1.0
        var t = x
        for _ in 0..<14 {
            let current = coordinate(t, x1, x2)
            if abs(current - x) < 0.00001 { break }
            if current < x { low = t } else { high = t }
            t = (low + high) * 0.5
        }
        return coordinate(t, y1, y2)
    }

    static func previewOrder(from order: [PaddockWidgetBentoCardID]) -> [PaddockWidgetBentoCardID] {
        guard isValid(order), let selected = order.last else { return defaultOrder }
        return [selected] + order.dropLast()
    }

    static func slotPositions(for order: [PaddockWidgetBentoCardID]) -> [PaddockWidgetBentoCardID: Double] {
        Dictionary(uniqueKeysWithValues: order.enumerated().map { ($0.element, slotY[$0.offset]) })
    }

    static func sample(elapsed: TimeInterval, order: [PaddockWidgetBentoCardID]) -> Frame {
        let validOrder = isValid(order) ? order : defaultOrder
        let t = elapsed.truncatingRemainder(dividingBy: loopDuration)
        let selected = validOrder.last!
        let preview = previewOrder(from: validOrder)
        let initialY = slotPositions(for: validOrder)
        let previewY = slotPositions(for: preview)
        let k = Timeline.self
        var phase: Phase = .rest
        var positions = initialY
        var handles = 0.0
        var selectedScale = 1.0
        var shadow = restShadow
        var rotation = 0.0

        if t >= Pit.window.0 && t < Pit.window.1 {
            phase = .pitStop
        } else if t >= Peel.start && t < Peel.end {
            phase = .peel
        } else if t >= k.editStart && t < k.editEnd {
            phase = .editCue
            let p = smooth((t - k.editStart) / (k.editEnd - k.editStart))
            handles = p
            selectedScale = 1 + (liftScale - 1) * p
            shadow = restShadow + (liftShadow - restShadow) * p
            rotation = maxRotation * p
        } else if t >= k.editEnd && t < k.reflowEnd {
            phase = .reflow
            let p = cubicBezier((t - k.editEnd) / (k.reflowEnd - k.editEnd), k.curve.0, k.curve.1, k.curve.2, k.curve.3)
            positions = interpolate(from: initialY, to: previewY, progress: p)
            handles = 1
            selectedScale = liftScale
            shadow = liftShadow
            rotation = maxRotation * (1 - p)
        } else if t >= k.reflowEnd && t < k.settleEnd {
            phase = .settle
            positions = previewY
            let p = smooth((t - k.reflowEnd) / (k.settleEnd - k.reflowEnd))
            handles = 1
            selectedScale = liftScale - (liftScale - 1) * p
            shadow = liftShadow - (liftShadow - restShadow) * p
        } else if t >= k.settleEnd && t < k.holdEnd {
            phase = .previewHold
            positions = previewY
            handles = 1
        } else if t >= k.holdEnd && t < k.returnEnd {
            phase = .returnReflow
            let p = cubicBezier((t - k.holdEnd) / (k.returnEnd - k.holdEnd), k.curve.0, k.curve.1, k.curve.2, k.curve.3)
            positions = interpolate(from: previewY, to: initialY, progress: p)
            handles = 1
            selectedScale = liftScale - (liftScale - 1) * p
            shadow = liftShadow - (liftShadow - restShadow) * p
        } else if t >= k.returnEnd && t < k.restEnd {
            phase = .settle
            positions = initialY
            let p = smooth((t - k.returnEnd) / (k.restEnd - k.returnEnd))
            handles = 1 - p
        }

        let showsPreview = phase == .reflow || (phase == .settle && t < k.settleEnd) || phase == .previewHold || phase == .returnReflow
        let holdsCard = phase == .editCue || showsPreview
        return Frame(phase: phase,
                     order: showsPreview ? preview : validOrder,
                     cardY: positions,
                     cardOpacity: Dictionary(uniqueKeysWithValues: validOrder.map { ($0, 1) }),
                     handlesOpacity: handles,
                     selectedID: holdsCard ? selected : nil,
                     selectedScale: selectedScale,
                     shadowRadius: shadow,
                     rotationDegrees: rotation,
                     isDemo: true,
                     loopTime: t)
    }

    static func restFrame(order: [PaddockWidgetBentoCardID]) -> Frame {
        let validOrder = isValid(order) ? order : defaultOrder
        return Frame(phase: .rest,
                     order: validOrder,
                     cardY: slotPositions(for: validOrder),
                     cardOpacity: Dictionary(uniqueKeysWithValues: validOrder.map { ($0, 1) }),
                     handlesOpacity: 0,
                     selectedID: nil, selectedScale: 1, shadowRadius: restShadow,
                     rotationDegrees: 0, isDemo: false, loopTime: -1)
    }

    private static func interpolate(from: [PaddockWidgetBentoCardID: Double], to: [PaddockWidgetBentoCardID: Double], progress: Double) -> [PaddockWidgetBentoCardID: Double] {
        Dictionary(uniqueKeysWithValues: PaddockWidgetBentoCardID.allCases.map { id in
            let start = from[id] ?? slotY[0]
            let end = to[id] ?? start
            return (id, start + (end - start) * progress)
        })
    }

    /// Rotation of the held card: leans with the finger, at most 0.7 degrees.
    static func dragRotation(dragY: Double, candidate: Int, pointerOffset: Double, quiet: Bool) -> Double {
        if quiet { return 0 }
        return min(maxRotation, max(-maxRotation, (dragY - (slotY[candidate] + pointerOffset)) * rotationPerPoint))
    }

    // MARK: Pit stop

    struct TilePose {
        var x = 0.0, y = 0.0, sx = 1.0, sy = 1.0, rotate = 0.0, speed = 0.0
    }

    /// Launched spring from 1 to 0 (normalised displacement): position, velocity, acceleration per second.
    private static func launchedSpring(_ tau: Double) -> (Double, Double, Double) {
        let w = Pit.omega, z = Pit.zeta
        let wd = w * (1 - z * z).squareRoot()
        let b = (-Pit.launch * w + z * w) / wd
        let e = exp(-z * w * tau)
        let c = cos(wd * tau), s = sin(wd * tau)
        let f = c + b * s
        let df = wd * (-s + b * c)
        return (e * f, e * (df - z * w * f), e * ((z * z * w * w - wd * wd) * f - 2 * z * w * df))
    }

    /// Pose of pit-stop tile `index` at `t` seconds into the pit stop (rest outside 0...duration).
    static func pitTile(_ index: Int, _ t: Double) -> TilePose {
        if t < 0 || t >= Pit.duration { return TilePose() }
        let dx = Pit.side[index] * (Pit.width[index] + Pit.margin)
        let dy = Pit.rise[index]
        let tilt = Pit.tilt[index]
        if t < Pit.brace {
            let b = 1 - (1 - Pit.braceScale) * easeOut(t / Pit.brace)
            return TilePose(x: 0, y: 0, sx: b, sy: b, rotate: 0, speed: 0)
        }
        if t < Pit.brace + Pit.exit {
            let e = (t - Pit.brace) / Pit.exit
            let f = pow(e, Pit.exitPower)
            let speed = abs(dx) * Pit.exitPower * pow(e, Pit.exitPower - 1) / Pit.exit
            let stretch = 1 + min(Pit.maxStretch, speed * Pit.stretchPerSpeed)
            return TilePose(x: dx * f, y: dy * f, sx: Pit.braceScale * stretch, sy: Pit.braceScale / stretch.squareRoot(), rotate: tilt * f, speed: speed)
        }
        let tau = t - Pit.arrive - Pit.delay[index]
        if tau < 0 { return TilePose(x: dx, y: dy, sx: 1, sy: 1, rotate: tilt, speed: 0) }
        let state = launchedSpring(tau)
        let speed = abs(dx * state.1)
        let stretch = min(Pit.maxStretch, speed * Pit.stretchPerSpeed)
        let squash = state.0 < 0 ? min(Pit.maxSquash, abs(dx * state.2) * Pit.squashPerAccel) : 0
        let along = 1 + stretch - squash
        return TilePose(x: dx * state.0, y: dy * state.0, sx: along, sy: 1 / along.squareRoot(), rotate: tilt * state.0, speed: speed)
    }

    /// Opacity at the head of the smear behind a tile moving at `speed` points per second.
    static func smearAlpha(_ speed: Double) -> Double { Pit.ghostAlpha * clamp(speed / Pit.ghostSpeed) }

    // MARK: Sticker

    struct PeelState {
        var distance: Double
        var angle: Double
        var slap: Double
    }

    static func peelDemo(_ t: Double) -> PeelState {
        let k = Peel.self
        if t < k.start || t >= k.end { return PeelState(distance: 0, angle: k.angle, slap: -1) }
        if t < k.tease { return PeelState(distance: k.teaseDistance * easeOut((t - k.start) / (k.tease - k.start)), angle: k.angle, slap: -1) }
        if t < k.full {
            let p = cubicBezier((t - k.tease) / (k.full - k.tease), 0.77, 0, 0.175, 1)
            return PeelState(distance: k.teaseDistance + (k.maxDistance - k.teaseDistance) * p, angle: k.angle, slap: -1)
        }
        if t < k.hold {
            let local = t - k.full
            let fade = 1 - local / (k.hold - k.full)
            let wave = sin(2 * .pi * k.flutterHz * local)
            return PeelState(distance: k.maxDistance + k.flutter * wave * fade,
                             angle: k.angle + k.wobble * sin(2 * .pi * k.flutterHz * local + 0.6) * fade, slap: -1)
        }
        if t < k.snap {
            let e = (t - k.hold) / (k.snap - k.hold)
            return PeelState(distance: k.maxDistance * (1 - e * e * e), angle: k.angle, slap: -1)
        }
        return PeelState(distance: 0, angle: k.angle, slap: t - k.snap)
    }

    /// A released finger: the corner falls back from `distance` over `Peel.release` (accelerating), then slaps.
    static func peelRelease(distance: Double, angle: Double, since: Double) -> PeelState {
        if since < Peel.release {
            let e = since / Peel.release
            return PeelState(distance: distance * (1 - e * e * e), angle: angle, slap: -1)
        }
        let slap = since - Peel.release
        return PeelState(distance: 0, angle: angle, slap: slap < Peel.slapEnd ? slap : -1)
    }

    /// The resting curl: the corner never lies flatter than `Peel.hint`.
    static func withHint(_ p: PeelState) -> PeelState {
        p.distance >= Peel.hint ? p : PeelState(distance: Peel.hint, angle: p.distance > 0.5 ? p.angle : Peel.angle, slap: p.slap)
    }

    /// The slap after a peel: lettering press (scale), card shake (points) and impact-line progress (0...1, -1 = none).
    static func slapPose(_ slap: Double) -> (press: Double, shake: Double, lines: Double) {
        if slap < 0 { return (1, 0, -1) }
        let press = 1 - Peel.slapPress * sin(.pi * clamp(slap / Peel.slapPressTime))
        let shake = Peel.shake * exp(-slap / Peel.shakeDecay) * sin(2 * .pi * Peel.shakeHz * slap)
        return (press, shake, slap < Peel.lineTime ? slap / Peel.lineTime : -1)
    }

    /// Rotation (degrees) of a quick card that is not held while the cards are being arranged.
    static func jiggle(id: Int, time: Double, handles: Double) -> Double {
        let direction = id % 2 == 0 ? 1.0 : -1.0
        return handles * Jiggle.amplitude * direction * sin(2 * .pi * Jiggle.hz * time + Double(id) * Jiggle.phase)
    }
}
