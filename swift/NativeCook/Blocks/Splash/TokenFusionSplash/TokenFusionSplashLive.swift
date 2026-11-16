import Foundation

/// The source's live state machine, including interruptible capture and independent pointer ownership.
final class TokenFusionSplashLive {
    typealias M = TokenFusionSplashMotion
    final class TokenFusionSplashLiveCoin {
        var x: Double, y: Double, r: Double, reach: Double, dye: Double, lean: Double, heat: Double
        var state: Int, vx: Double, vy: Double, age: Double, speed = 0.0, fromX: Double, fromY: Double
        var pointer = -1, ox = 0.0, oy = 0.0, lastX: Double, lastY: Double, lastT = 0.0
        var snapped = false, neckFormed = false
        init(_ c: TokenFusionSplashMotion.TokenFusionSplashCoin, _ next: TokenFusionSplashMotion.TokenFusionSplashCoin?, _ cx: Double, _ cy: Double) {
            x = c.x; y = c.y; r = c.r; reach = c.reach; dye = c.dye; lean = c.lean; heat = c.heat
            let fused = c.r < 1, shrinking = !fused && c.r < 21.9 && (next == nil || next!.r < c.r)
            var low = 0.0, high = 1.0
            let progress = M.clamp(1 - c.r * c.r / (22 * 22))
            for _ in 0..<12 { let mid = (low + high) / 2; if M.smooth(mid) < progress { low = mid } else { high = mid } }
            let resolvedAge = shrinking ? (low + high) / 2 * 0.32 : 0
            age = resolvedAge
            state = fused ? 4 : (shrinking ? 3 : 0)
            vx = next.map { ($0.x - c.x) * 120 } ?? 0; vy = next.map { ($0.y - c.y) * 120 } ?? 0
            let left = max(0.001, 1 - M.clamp(M.spring(resolvedAge, 16, 200)))
            fromX = cx + (c.x - cx) / left; fromY = cy + (c.y - cy) / left
            lastX = c.x; lastY = c.y
        }
        var scene: TokenFusionSplashMotion.TokenFusionSplashCoin { .init(x: x, y: y, r: r, reach: reach, dye: dye, lean: lean, heat: heat) }
    }
    var coins: [TokenFusionSplashLiveCoin]
    var now = 0.0, angle = 0.0, gather: Double, mass = 0.0, massV = 0.0
    var x: Double, y: Double, vx: Double, vy: Double
    var corePointer = -1, coreOX = 0.0, coreOY = 0.0, lastTouch = 0.0, coreLastT = 0.0, corePull = 0.0
    var mark: Double, flood: Double, floodV = 0.0, completed = false
    var recoilX = 0.0, recoilY = 0.0, recoilVX = 0.0, recoilVY = 0.0
    var ringTime = 10.0, ringSpeed = 0.0, ringAxis: Double, mergedMask = 0, mergeEvents = 0, releaseEvents = 0, cue = 0
    var stretch: Double, snapTime = 10.0, carryStretch: Double, carryStretchV: Double
    var markSize: Double, cut: Double, word: Double, wordY: Double

    init(_ scene: TokenFusionSplashMotion.TokenFusionSplashScene, next: TokenFusionSplashMotion.TokenFusionSplashScene? = nil) {
        coins = scene.coins.enumerated().map { TokenFusionSplashLiveCoin($0.element, next?.coins[$0.offset], scene.x, scene.y) }
        gather = M.clamp((1 - scene.sphere) / 0.6); x = scene.x; y = scene.y
        vx = next.map { ($0.x - scene.x) * 120 } ?? 0; vy = next.map { ($0.y - scene.y) * 120 } ?? 0
        mark = scene.mark; flood = scene.flood; ringAxis = scene.axis; stretch = scene.stretch
        carryStretch = scene.stretch - 1; carryStretchV = next.map { ($0.stretch - scene.stretch) * 120 } ?? 0
        markSize = scene.markSize; cut = scene.cut; word = scene.word; wordY = scene.wordY
        for i in coins.indices where coins[i].r < 1 { mergedMask |= 1 << i }
    }
    var radius: Double {
        var area = M.mix(120 * 120, 55 * 55, gather)
        for c in coins { area += max(0, 22 * 22 - c.r * c.r) }
        return sqrt(max(55 * 55, area))
    }
    var held: Bool { corePointer >= 0 || coins.contains { $0.pointer >= 0 } }
    func capture(_ index: Int, speed: Double = 0) {
        let c = coins[index]
        if c.state == 3 || c.state == 4 { return }
        c.state = 3; c.age = 0; c.speed = speed; c.fromX = c.x; c.fromY = c.y; c.pointer = -1
        mergeEvents |= 1 << index; mergedMask |= 1 << index; ringTime = 0; ringSpeed = speed
        ringAxis = atan2(c.y - y, c.x - x)
        recoilVX -= cos(ringAxis) * 14 * 25; recoilVY -= sin(ringAxis) * 14 * 25
        cue = speed > 1500 ? 4 : 3
    }
    @discardableResult func grab(_ pointer: Int, _ px: Double, _ py: Double, touchCore: Bool = true) -> Bool {
        var best = -1, dist = 36.0
        for i in coins.indices {
            let c = coins[i]
            if c.pointer >= 0 || c.state == 4 || (c.state == 3 && (c.age > 0.16 || c.r < 9)) { continue }
            let d = hypot(px - c.x, py - c.y)
            if d < dist { best = i; dist = d }
        }
        if best >= 0 {
            let c = coins[best]
            if c.state == 3 { mergedMask &= ~(1 << best); releaseEvents |= 1 << best }
            c.state = 1
            if c.r >= 21.9 { c.r = 22 * 1.04 }
            c.pointer = pointer; c.ox = px - c.x; c.oy = py - c.y; c.lastT = now; c.snapped = false
            c.neckFormed = c.reach > 0.2; c.lastX = c.x; c.lastY = c.y; c.age = 0; cue = 1; lastTouch = now
            return true
        }
        if touchCore && corePointer < 0 && hypot(px - x, py - y) < min(radius, 120) {
            corePointer = pointer; coreOX = px - x; coreOY = py - y; lastTouch = now; coreLastT = now; cue = 1
            return true
        }
        return false
    }
    func move(_ pointer: Int, _ px: Double, _ py: Double, bounds: (Double, Double, Double, Double)) {
        if pointer == corePointer {
            var dx = px - coreOX - 201, dy = py - coreOY - 400
            let d = hypot(dx, dy)
            corePull = max(0, d - 90) / (120 + max(0, d - 90)); ringAxis = atan2(dy, dx)
            if d > 90 { dx *= 90 / d; dy *= 90 / d }
            let dt = max(1 / 120.0, now - coreLastT)
            vx = (201 + dx - x) / dt; vy = (400 + dy - y) / dt; x = 201 + dx; y = 400 + dy; coreLastT = now
        }
        for i in coins.indices {
            let c = coins[i]
            if c.pointer != pointer { continue }
            let nx = M.clamp(px - c.ox, bounds.0, bounds.1), ny = M.clamp(py - c.oy, bounds.2, bounds.3)
            let dt = max(1 / 120.0, now - c.lastT)
            c.vx = M.mix(c.vx, (nx - c.x) / dt, 0.7); c.vy = M.mix(c.vy, (ny - c.y) / dt, 0.7)
            c.x = nx; c.y = ny; c.lastT = now
            let gap = hypot(c.x - x, c.y - y) - radius - c.r
            if gap > 110 && c.neckFormed && !c.snapped { c.snapped = true; snapTime = 0; cue = 2 }
            if gap < 6 { capture(i, speed: hypot(c.vx, c.vy)) }
        }
        lastTouch = now
    }
    func release(_ pointer: Int, cancel: Bool = false) {
        if corePointer == pointer { corePointer = -1 }
        for i in coins.indices {
            let c = coins[i]
            if c.pointer != pointer { continue }
            c.pointer = -1; c.age = 0
            let gap = hypot(c.x - x, c.y - y) - radius - c.r
            if !cancel && gap < 40 { capture(i, speed: hypot(c.vx, c.vy)) }
            else { c.state = cancel ? 5 : 2; c.fromX = c.x; c.fromY = c.y }
        }
        lastTouch = now
    }
    func step(_ dt: Double, reduced: Bool = false, allowAutoFlood: Bool = true) {
        let steps = max(1, Int(ceil(dt * 120))), h = min(dt, 0.05) / Double(steps)
        for _ in 0..<steps {
            now += h; ringTime += h; snapTime += h
            carryStretchV += (-160 * carryStretch - 12 * carryStretchV) * h; carryStretch += carryStretchV * h
            let captured = coins.filter { ($0.state == 3 && $0.age > 0.32) || $0.state == 4 }.count
            let target = ringTime > 0.18 + M.firstRingPeak + 0.12 ? Double(captured) : mass
            massV += (90 * (target - mass) - 18 * massV) * h; mass += massV * h
            angle += M.speeds[min(6, Int(floor(M.clamp(mass, 0, 6) + 0.5)))] * h
            if corePointer < 0 {
                corePull *= exp(-h * 10)
                vx += (110 * (201 - x) - 16 * vx) / 1.2 * h; x += vx * h
                vy += (110 * (400 - y) - 16 * vy) / 1.2 * h; y += vy * h
            }
            recoilVX += (-160 * recoilX - 12 * recoilVX) * h; recoilX += recoilVX * h
            recoilVY += (-160 * recoilY - 12 * recoilVY) * h; recoilY += recoilVY * h
            let r = radius
            for i in coins.indices {
                let c = coins[i]; c.age += h
                let slot = M.orbit(i, coins.count, angle, gather, mass)
                let dx = x - c.x, dy = y - c.y, d = max(1, hypot(dx, dy)), gap = d - r - c.r
                if c.state == 0 || c.state == 5 {
                    c.vx += (120 * (slot.x - c.x) - 14 * c.vx) * h; c.vy += (120 * (slot.y - c.y) - 14 * c.vy) * h
                    c.x += c.vx * h; c.y += c.vy * h; c.r += (22 - c.r) * min(1, h * 18)
                    if c.state == 5 && hypot(slot.x - c.x, slot.y - c.y) < 1 { c.state = 0 }
                    if corePointer >= 0 && gap < 6 { capture(i, speed: hypot(vx, vy)) }
                } else if c.state == 2 {
                    let pull = gap < 160 ? 9000 / max(gap, 20) : 0
                    c.vx = (c.vx + dx / d * pull * h) * pow(0.996, h * 1000)
                    c.vy = (c.vy + dy / d * pull * h) * pow(0.996, h * 1000)
                    c.x += c.vx * h; c.y += c.vy * h
                    if gap < 40 { capture(i, speed: hypot(c.vx, c.vy)) }
                    else if c.age > 0.8 { c.state = 6; c.age = 0; c.fromX = c.x; c.fromY = c.y }
                } else if c.state == 6 {
                    let p = M.seg(c.age, 0, 0.8), startAngle = atan2(c.fromY - y, c.fromX - x), endAngle = atan2(slot.y - y, slot.x - x)
                    let tau = 2 * Double.pi
                    let turn = ((endAngle - startAngle).truncatingRemainder(dividingBy: tau) + tau).truncatingRemainder(dividingBy: tau) + tau
                    let a = startAngle + turn * p, radius = M.mix(hypot(c.fromX - x, c.fromY - y), hypot(slot.x - x, slot.y - y), p)
                    let nx = x + cos(a) * radius, ny = y + sin(a) * radius
                    c.vx = (nx - c.x) / h; c.vy = (ny - c.y) / h; c.x = nx; c.y = ny
                    if p >= 1 { c.state = 5; c.age = 0 }
                } else if c.state == 3 {
                    let p = M.clamp(M.spring(c.age, 16, 200))
                    c.x = M.mix(c.fromX, x, p); c.y = M.mix(c.fromY, y, p); c.r = 22 * sqrt(max(0, 1 - M.seg(c.age, 0, 0.32)))
                    if c.age > 0.32 { c.state = 4; c.r = 0 }
                } else if c.state == 1 { c.r += (22 * 1.12 - c.r) * min(1, h * 22) }
                c.reach = reduced || ((c.state == 0 || c.state == 5) && corePointer < 0) ? 0 : M.reach(c.x, c.y, c.r, x, y, r)
                if c.reach > 0.2 && !c.neckFormed { c.neckFormed = true; if c.state == 1 || c.state == 2 { cue = 2 } }
                c.dye = c.state == 3 || c.state == 4 ? 1 - M.seg(c.age, 0, 2.4) : c.reach
                c.heat = c.reach; c.lean = M.sign(x - c.x) * min(1, mass) * 6 * .pi / 180
            }
            mark += ((flood > 0.65 || !coins.contains { $0.reach > 0.2 } ? 1 : 0) - mark) * min(1, h * 14)
            if (coins.allSatisfy { $0.state == 4 } && allowAutoFlood) || flood > 0 {
                floodV += (48.7 * (1 - flood) - 14 * floodV) * h; flood = M.clamp(flood + floodV * h)
            }
            let follow = min(1, h * 14)
            markSize = M.mix(markSize, M.mix(M.mix(60, 40, gather), 60, flood), follow)
            cut = M.mix(cut, M.seg(flood, 0.4, 0.65) * (1 - M.seg(flood, 0.65, 0.85)), follow)
            word = M.mix(word, M.seg(flood, 0.88, 0.98), follow)
            wordY = M.mix(wordY, 470 + 8 * (1 - M.seg(flood, 0.88, 0.98)), follow)
            stretch = reduced ? 1 : min(1.25, 1 + hypot(vx, vy) / 6000 + 0.25 * corePull) + M.ring(ringTime - 0.18, ringSpeed) + carryStretch
        }
    }
    func scene(reduced: Bool = false) -> TokenFusionSplashMotion.TokenFusionSplashScene {
        let r = radius
        return M.TokenFusionSplashScene(x: x + (reduced ? 0 : recoilX), y: y + (reduced ? 0 : recoilY), r: M.mix(r, 980, flood),
                       sphere: 1 - gather * 0.6, stretch: reduced ? 1 : stretch + 0.05 * M.ring(snapTime) / 0.09,
                       axis: corePointer >= 0 && hypot(vx, vy) > 1 ? atan2(vy, vx) : ringAxis,
                       ringScale: 1 - gather * 0.324, mark: reduced && flood > 0 ? 1 : mark,
                       markSize: reduced && flood > 0 ? 60 : markSize, cut: reduced ? 0 : cut,
                       word: reduced ? (flood > 0 ? 1 : 0) : word, wordY: reduced ? 470 : wordY, flood: flood,
                       coins: coins.map { c in
                           var s = c.scene
                           s.r *= 1 + (c.snapped && !reduced ? 0.03 * M.ring(snapTime) / 0.09 : 0)
                           return s
                       })
    }
}
