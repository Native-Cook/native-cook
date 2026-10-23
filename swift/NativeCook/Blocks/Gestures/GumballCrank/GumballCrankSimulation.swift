import Foundation

enum GumballCrankPhase { case ready, dispensing, reward, collecting }
enum GumballCrankHaptic { case tick, strain, drop, open, collect }

struct GumballCrankCapsulePose {
    let x: Double, y: Double, scale: Double, rotation: Double, split: Double, alpha: Double
}

final class GumballCrankSimulation {
    private(set) var reduced: Bool
    private(set) var angle = 0.0, wobble = 0.0, phaseTime = 0.0, card = 0.0, scrim = 0.0, press = 0.0
    private(set) var wallet = 1240.0, walletPrevious = 1240.0, walletMix = 1.0, walletBump = 0.0
    private(set) var ghostX = 0.0, ghostY = 0.0, ghostAlpha = 0.0, ghostPress = 0.0
    private(set) var phase = GumballCrankPhase.ready
    private(set) var hint = 0, hintFrom = 0, detent = 0
    private(set) var hintMix = 1.0, moving = true
    private(set) var ballX: [Double], ballY: [Double], ballR: [Double], ballColor: [Int]

    private let homeX: [Double], homeY: [Double]
    private var ballVX: [Double], ballVY: [Double]
    private var base = 0.0, goal = 0.0, lastPointerAngle = 0.0, wobbleVelocity = 0.0, cardVelocity = 0.0, walletVelocity = 0.0
    private var walletMixDuration = 0.15, demoTime = 0.0, quietTime = 0.0
    private var grabbed = false, pressing = false, scriptDown = false
    private(set) var demoRunning = false
    private var events: [GumballCrankHaptic] = []

    init(reduced: Bool) {
        self.reduced = reduced
        let pile = Self.settlePile()
        ballX = pile.x; ballY = pile.y; ballR = pile.r; ballColor = pile.color
        homeX = pile.x; homeY = pile.y
        ballVX = .init(repeating: 0, count: pile.x.count); ballVY = ballVX
    }

    var accessibilityDescription: String {
        switch phase {
        case .reward: "Day 7 reward, 50 coins. Collect reward."
        case .collecting: "Collecting 50 coins."
        default: hint == 2 ? GumballCrankCopy.done : GumballCrankCopy.hint
        }
    }
    var accessibilityAction: String { phase == .reward ? "Collect reward" : "Turn the handle one quarter" }

    func start(autoPlay: Bool, reduceMotion: Bool) {
        reduced = reduceMotion; demoRunning = autoPlay && !reduceMotion; demoTime = 0; quietTime = 0; toRest()
    }

    func step(dt: Double, autoPlay: Bool) {
        if !demoRunning, autoPlay, !reduced {
            quietTime += dt
            if quietTime >= GumballCrankMotion.resume { toRest(); demoTime = 0; demoRunning = true }
        }
        if demoRunning, autoPlay, !reduced {
            let old = demoTime
            demoTime += dt
            if demoTime >= GumballCrankMotion.loop { toRest(); demoTime.formTruncatingRemainder(dividingBy: GumballCrankMotion.loop) }
            script(old: demoTime < old ? 0 : old, now: demoTime)
        }

        var busy = stepPile(dt: dt)
        if !grabbed, phase == .ready, angle != base {
            angle += (base - angle) * min(1, dt * (reduced ? 30 : 14))
            if abs(angle - base) < 0.002 { angle = base }
            busy = true
        }
        (wobble, wobbleVelocity) = gumballCrankSpring(wobble, wobbleVelocity, target: 0, dt: dt, duration: 0.18, damping: 0.45)
        if wobbleVelocity != 0 { busy = true }
        if phase != .ready { phaseTime += dt; busy = true }
        if phase == .dispensing {
            if crossed(0.25, dt) { events.append(.drop) }
            if crossed(1.32, dt) { events.append(.open) }
            if phaseTime >= 1.95 { phase = .reward; phaseTime = 0; angle = 0; base = 0; goal = 0; detent = 0 }
        } else if phase == .collecting {
            for i in 0 ..< 8 where crossed(Double(i) * 0.05 + 0.5, dt) {
                setWallet(min(1290, wallet + 6.25), 0.15)
                if !reduced { walletVelocity += 1.6 }
            }
            if phaseTime >= 0.95 { phase = .ready; phaseTime = 0; setHint(2) }
        }

        let cardGoal = phase == .reward || (phase == .dispensing && phaseTime >= 1.45) ? 1.0 : 0.0
        if phase != .collecting { (card, cardVelocity) = gumballCrankSpring(card, cardVelocity, target: cardGoal, dt: dt, duration: reduced ? 0.2 : 0.48, damping: reduced ? 1 : 0.82) }
        let scrimGoal = cardGoal > 0 || (phase == .collecting && phaseTime < 0.75) ? 1.0 : 0.0
        scrim = gumballCrankToward(scrim, scrimGoal, dt: dt, duration: scrimGoal > 0 ? 0.3 : 0.25)
        press = gumballCrankToward(press, pressing ? 1 : 0, dt: dt, duration: 0.12)
        (walletBump, walletVelocity) = gumballCrankSpring(walletBump, walletVelocity, target: 0, dt: dt, duration: 0.26, damping: 0.5)
        hintMix = min(1, hintMix + dt / 0.25); walletMix = min(1, walletMix + dt / walletMixDuration)
        ghostAlpha = gumballCrankToward(ghostAlpha, scriptDown ? 1 : 0, dt: dt, duration: scriptDown ? 0.15 : 0.12)
        ghostPress = gumballCrankToward(ghostPress, scriptDown || pressing ? 1 : 0, dt: dt, duration: 0.15)
        moving = busy || demoRunning || grabbed || cardVelocity != 0 || walletVelocity != 0 || hintMix < 1 || walletMix < 1
    }

    func pointerDown(x: Double, y: Double) {
        takeOver()
        if phase == .reward, insideButton(x, y) { pressing = true; moving = true } else { _ = grabAt(x, y) }
    }
    func pointerMove(x: Double, y: Double) { if grabbed { turnTo(x, y) }; if pressing, !insideButton(x, y) { pressing = false } }
    func pointerUp(x: Double, y: Double) { if pressing, insideButton(x, y) { collect() }; pressing = false; grabbed = false; goal = base; moving = true }
    func accessibilityActivate() {
        takeOver()
        if phase == .reward { collect() }
        else if phase == .ready {
            angle = min(GumballCrankMotion.tau, Double(detent + 1) * GumballCrankMotion.quarter + 0.02)
            while detent < 4, angle >= Double(detent + 1) * GumballCrankMotion.quarter { hitDetent() }
        }
    }
    func drainHaptics() -> [GumballCrankHaptic] { defer { events.removeAll(keepingCapacity: true) }; return events }

    func capsulePose() -> GumballCrankCapsulePose {
        let doorY = 561.0
        if phase == .ready { return .init(x: 201, y: 690, scale: 1, rotation: 0, split: 0, alpha: 0) }
        let t = phase == .dispensing ? phaseTime : 99
        let fade = phase == .collecting ? 1 - gumballCrankSpan(phaseTime, 0.45, 0.85) : 1
        if reduced { return .init(x: 201, y: 690, scale: 1, rotation: 0, split: gumballCrankSpan(t, 1.32, 1.7), alpha: gumballCrankSpan(t, 0.3, 0.95) * fade) }
        if t < 0.3 { return .init(x: 201, y: doorY, scale: 0.6, rotation: 0, split: 0, alpha: 0) }
        let u = gumballCrankSpan(t, 0.3, 0.95), eased = gumballCrankEaseOut(u)
        var rotation = -2.6 * (1 - eased)
        if t >= 0.95 { let wave = gumballCrankSpan(t, 0.95, 1.7); rotation = 0.16 * sin(wave * .pi * 3) * (1 - wave) }
        return .init(x: 201, y: doorY + 129 * eased, scale: 0.6 + 0.4 * eased, rotation: rotation, split: gumballCrankEaseOut(gumballCrankSpan(t, 1.32, 1.7)), alpha: min(1, u * 6) * fade)
    }

    private func takeOver() { demoRunning = false; quietTime = 0; scriptDown = false; ghostAlpha = 0; ghostPress = 0 }
    private func crossed(_ value: Double, _ dt: Double) -> Bool { phaseTime >= value && phaseTime - dt < value }
    private func insideButton(_ x: Double, _ y: Double) -> Bool { (66 ... 336).contains(x) && (580 ... 632).contains(y) }
    @discardableResult private func grabAt(_ x: Double, _ y: Double) -> Bool {
        let dx = x - 201, dy = y - 462
        guard dx * dx + dy * dy <= 4900, phase == .ready else { return false }
        grabbed = true; lastPointerAngle = atan2(dx, -dy); goal = angle; moving = true; return true
    }
    private func turnTo(_ x: Double, _ y: Double) {
        let pointer = atan2(x - 201, -(y - 462)); var delta = pointer - lastPointerAngle
        if delta > .pi { delta -= GumballCrankMotion.tau }; if delta < -.pi { delta += GumballCrankMotion.tau }
        lastPointerAngle = pointer; goal += delta
        let backwards = goal - base
        if backwards < 0 {
            let before = angle; angle = base - GumballCrankMotion.back * (1 - exp(backwards / GumballCrankMotion.back))
            if before >= base, angle < base - 0.05 { events.append(.strain) }
        } else { angle = min(goal, GumballCrankMotion.tau) }
        while detent < 4, angle >= Double(detent + 1) * GumballCrankMotion.quarter { hitDetent() }
        moving = true
    }
    private func hitDetent() {
        detent += 1; base = Double(detent) * GumballCrankMotion.quarter; wobbleVelocity += reduced ? 0 : 4; kickPile(detent); events.append(.tick)
        if detent == 4 { phase = .dispensing; phaseTime = 0; grabbed = false; setHint(1) }
    }
    private func collect() { guard phase == .reward else { return }; pressing = false; phase = .collecting; phaseTime = 0; events.append(.collect) }
    private func toRest() {
        grabbed = false; pressing = false; phase = .ready; phaseTime = 0; angle = 0; base = 0; goal = 0; detent = 0; card = 0; cardVelocity = 0; scrim = 0
        if wallet != 1240 { setWallet(1240, 0.5) }; setHint(0); moving = true
    }
    private func setHint(_ value: Int) { if hint != value { hintFrom = hint; hint = value; hintMix = 0 } }
    private func setWallet(_ value: Double, _ duration: Double) { walletPrevious = wallet; wallet = value; walletMix = 0; walletMixDuration = duration }
    private func script(old: Double, now: Double) {
        func crosses(_ value: Double) -> Bool { old < value && now >= value }
        if crosses(0.5) { (ghostX, ghostY) = gripAt(angle); scriptDown = true; _ = grabAt(ghostX, ghostY) }
        if scriptDown, now > 0.7, now <= 2.55 {
            let u = gumballCrankSpan(now, 0.7, 2.5) * 4, q = floor(min(u, 3.9999)), scripted = (q + gumballCrankEaseInOut(u - q)) * GumballCrankMotion.quarter + 0.02
            (ghostX, ghostY) = gripAt(scripted); turnTo(ghostX, ghostY)
        }
        if crosses(2.65) { scriptDown = false; grabbed = false; goal = base }
        if crosses(5.25) { ghostX = 228; ghostY = 606; scriptDown = true }
        if crosses(5.45) { pressing = true }
        if crosses(5.7) { collect(); pressing = false; scriptDown = false }
        if crosses(9) { setWallet(1240, 0.5); setHint(0) }
    }
    private func gripAt(_ value: Double) -> (Double, Double) { (201 + 34 * sin(value), 462 - 34 * cos(value)) }
    private func kickPile(_ kickNumber: Int) {
        guard !reduced else { return }
        for i in ballX.indices {
            let low = gumballCrankClamp((homeY[i] - 254) / 117), kick = GumballCrankMotion.kick * (0.3 + 0.7 * low), value = gumballCrankHash(i * 13 + kickNumber * 7) * GumballCrankMotion.tau
            ballVX[i] += cos(value) * kick * 0.6; ballVY[i] -= (0.5 + gumballCrankHash(i + kickNumber * 3) * 0.6) * kick
        }
    }
    private func stepPile(dt: Double) -> Bool {
        let frequency = GumballCrankMotion.tau / 0.52, stiffness = frequency * frequency, damping = 2 * 0.55 * frequency
        let steps = max(1, Int(ceil(dt / (1 / 240)))), h = dt / Double(steps)
        for _ in 0 ..< steps {
            for i in ballX.indices { ballVX[i] += (-stiffness * (ballX[i] - homeX[i]) - damping * ballVX[i]) * h; ballVY[i] += (-stiffness * (ballY[i] - homeY[i]) - damping * ballVY[i]) * h; ballX[i] += ballVX[i] * h; ballY[i] += ballVY[i] * h }
            Self.collide(&ballX, &ballY, ballR, &ballVX, &ballVY)
        }
        var busy = false
        for i in ballX.indices {
            if abs(ballX[i] - homeX[i]) > 0.05 || abs(ballY[i] - homeY[i]) > 0.05 || abs(ballVX[i]) > 0.5 || abs(ballVY[i]) > 0.5 { busy = true }
            else { ballX[i] = homeX[i]; ballY[i] = homeY[i]; ballVX[i] = 0; ballVY[i] = 0 }
        }
        return busy
    }

    private struct Pile { let x: [Double], y: [Double], r: [Double]; let color: [Int] }
    private static func settlePile() -> Pile {
        let count = 23; var x = [Double](repeating: 0, count: count), y = x, r = x, vx = x, vy = x; var color = [Int](repeating: 0, count: count)
        for i in 0 ..< count { r[i] = 15.5 + gumballCrankHash(i + 1) * 4; color[i] = Int(floor(gumballCrankHash(i * 7 + 3) * 9)); x[i] = 121 + Double(i % 7) * 27 + gumballCrankHash(i + 9) * 4; y[i] = 264 + floor(Double(i) / 7) * 26 }
        let h = 1 / 240.0
        for _ in 0 ..< 1600 { for i in 0 ..< count { vy[i] += GumballCrankMotion.gravity * h; vx[i] *= 0.985; vy[i] *= 0.985; x[i] += vx[i] * h; y[i] += vy[i] * h }; for _ in 0 ..< 3 { collide(&x, &y, r, &vx, &vy) } }
        return .init(x: x, y: y, r: r, color: color)
    }
    private static func collide(_ x: inout [Double], _ y: inout [Double], _ r: [Double], _ vx: inout [Double], _ vy: inout [Double]) {
        for i in x.indices where i + 1 < x.count { for j in (i + 1) ..< x.count { let dx = x[j] - x[i], dy = y[j] - y[i], distance = max(hypot(dx, dy), 1e-6), limit = r[i] + r[j]; if distance < limit { let push = (limit - distance) / distance / 2; x[i] -= dx * push; y[i] -= dy * push; x[j] += dx * push; y[j] += dy * push } } }
        for i in x.indices {
            let dx = x[i] - 201, dy = y[i] - 254, distance = max(hypot(dx, dy), 1e-6), limit = 117 - r[i]
            if distance > limit { x[i] = 201 + dx / distance * limit; y[i] = 254 + dy / distance * limit; let nx = dx / distance, ny = dy / distance, normal = vx[i] * nx + vy[i] * ny; if normal > 0 { vx[i] -= 1.4 * normal * nx; vy[i] -= 1.4 * normal * ny } }
            if y[i] > 364 - r[i] { y[i] = 364 - r[i]; if vy[i] > 0 { vy[i] *= -0.3 } }
        }
    }
}

private func gumballCrankClamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double { min(high, max(low, value)) }
private func gumballCrankSpan(_ value: Double, _ start: Double, _ end: Double) -> Double { gumballCrankClamp((value - start) / (end - start)) }
private func gumballCrankHash(_ n: Int) -> Double { let value = sin(Double(n) * 127.1 + 311.7) * 43758.5453; return value - floor(value) }
private func gumballCrankToward(_ value: Double, _ goal: Double, dt: Double, duration: Double) -> Double { let amount = dt / duration; return goal > value ? min(goal, value + amount) : max(goal, value - amount) }
private func gumballCrankBezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ u: Double) -> Double {
    let k = gumballCrankClamp(u), cx = 3 * x1, bx = 3 * (x2 - x1) - 3 * x1, ax = 1 - 3 * x1 - bx, cy = 3 * y1, by = 3 * (y2 - y1) - 3 * y1, ay = 1 - 3 * y1 - by
    var t = k
    for _ in 0 ..< 8 { let x = ((ax * t + bx) * t + cx) * t - k, dx = (3 * ax * t + 2 * bx) * t + cx; if abs(x) > 1e-5, abs(dx) > 1e-6 { t -= x / dx } }
    t = gumballCrankClamp(t); return ((ay * t + by) * t + cy) * t
}
private func gumballCrankEaseOut(_ u: Double) -> Double { gumballCrankBezier(0.23, 1, 0.32, 1, u) }
private func gumballCrankEaseInOut(_ u: Double) -> Double { gumballCrankBezier(0.77, 0, 0.175, 1, u) }
private func gumballCrankSpring(_ value: Double, _ velocity: Double, target: Double, dt: Double, duration: Double, damping: Double) -> (Double, Double) {
    let frequency = Double.pi * 2 / duration, stiffness = frequency * frequency, drag = 2 * damping * frequency, count = max(1, Int(ceil(dt / 0.004))), h = dt / Double(count); var x = value, v = velocity
    for _ in 0 ..< count { v += (-stiffness * (x - target) - drag * v) * h; x += v * h }
    return abs(x - target) < 0.0005 && abs(v) < 0.005 ? (target, 0) : (x, v)
}

func gumballCrankEase(_ u: Double) -> Double { gumballCrankEaseOut(u) }
func gumballCrankUnitSpan(_ value: Double, _ start: Double, _ end: Double) -> Double { gumballCrankSpan(value, start, end) }
