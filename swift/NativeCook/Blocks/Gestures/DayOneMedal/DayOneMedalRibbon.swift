import SwiftUI

struct DayOneMedalRibbonParticle: Codable {
    var x = 0.0
    var y = 0.0
    var px = 0.0
    var py = 0.0
}

struct DayOneMedalRibbonSample: Codable {
    var x = 0.0
    var y = 0.0
    var nx = 1.0
    var ny = 0.0
    var width = 13.0
    var light = 1.0
}

struct DayOneMedalRibbonBand: Codable {
    var points = Array(repeating: DayOneMedalRibbonParticle(), count: 15)
    var samples = Array(repeating: DayOneMedalRibbonSample(), count: 71)
    var length = 1.0
    var top = DayOneMedalPoint()
    var end = DayOneMedalPoint()
}

struct DayOneMedalRibbonState: Codable {
    var bands = [DayOneMedalRibbonBand(), DayOneMedalRibbonBand()]
    var tip = DayOneMedalPoint()
    var release = DayOneMedalPoint()
    var worn = 0.0
    var releaseWorn = 1.0
    var leaving = false
    var caught = false
    var active = false
    var opacity = 0.0
    var step = 1.0 / 120
}

enum DayOneMedalRibbonPhysics {
    static func seed(_ band: inout DayOneMedalRibbonBand, _ tx: Double, _ ty: Double, _ ex: Double, _ ey: Double, _ side: Double, _ straight: Bool) {
        let dx = ex - tx
        let dy = ey - ty
        let span = max(1, hypot(dx, dy))
        let belly = straight ? 0 : span * 0.115 * side
        for i in 0..<15 {
            let t = Double(i) / 14
            let bow = sin(.pi * t) * belly
            let x = tx + dx * t + dy / span * bow
            let y = ty + dy * t - dx / span * bow
            band.points[i] = DayOneMedalRibbonParticle(x: x, y: y, px: x, py: y)
        }
        band.top = DayOneMedalPoint(x: tx, y: ty)
        band.end = DayOneMedalPoint(x: ex, y: ey)
        band.length = span * (straight ? 1 : 1.033) / 14
    }

    static func constrain(_ band: inout DayOneMedalRibbonBand, _ tx: Double, _ ty: Double, _ ex: Double, _ ey: Double) {
        band.length = max(1, hypot(ex - tx, ey - ty)) * 1.033 / 14
        band.points[0].x = tx
        band.points[0].y = ty
        band.points[14].x = ex
        band.points[14].y = ey
        for pass in 0..<384 {
            for j in 0..<14 {
                let i = pass % 2 == 0 ? j : 13 - j
                let dx = band.points[i + 1].x - band.points[i].x
                let dy = band.points[i + 1].y - band.points[i].y
                let span = max(0.00001, hypot(dx, dy))
                let correction = (span - band.length) / span
                let wa = i == 0 ? 0.0 : i + 1 == 14 ? 1.0 : 0.5
                let wb = i + 1 == 14 ? 0.0 : i == 0 ? 1.0 : 0.5
                band.points[i].x += dx * correction * wa
                band.points[i].y += dy * correction * wa
                band.points[i + 1].x -= dx * correction * wb
                band.points[i + 1].y -= dy * correction * wb
            }
            if pass % 8 == 7 {
                var error = 0.0
                for i in 0..<14 {
                    error = max(error, abs(hypot(band.points[i + 1].x - band.points[i].x,
                        band.points[i + 1].y - band.points[i].y) / band.length - 1))
                }
                if error < 0.001 { break }
            }
        }
    }

    static func sample(_ band: inout DayOneMedalRibbonBand, _ side: Double, _ scale: Double, _ straight: Bool) {
        func catmull(_ a: Double, _ b: Double, _ c: Double, _ d: Double, _ f: Double) -> Double {
            0.5 * ((2 * b) + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (-a + 3 * b - 3 * c + d) * f * f * f)
        }
        for i in 0..<71 {
            let at = Double(i) / 70 * 14
            let j = min(13, Int(floor(at)))
            let f = at - Double(j)
            let a = band.points[max(0, j - 1)]
            let b = band.points[j]
            let c = band.points[j + 1]
            let d = band.points[min(14, j + 2)]
            band.samples[i].x = catmull(a.x, b.x, c.x, d.x, f)
            band.samples[i].y = catmull(a.y, b.y, c.y, d.y, f)
        }
        for i in 0..<71 {
            let a = band.samples[max(0, i - 1)]
            let b = band.samples[min(70, i + 1)]
            let span = max(0.001, hypot(b.x - a.x, b.y - a.y))
            band.samples[i].nx = (b.y - a.y) / span
            band.samples[i].ny = -(b.x - a.x) / span
            let t = Double(i) / 70
            let fold = straight ? 0 : exp(-pow((t - (side < 0 ? 0.73 : 0.84)) / 0.12, 2))
            band.samples[i].width = 13 * scale * (1 - 0.62 * fold)
            band.samples[i].light = DayOneMedalPhysics.clamp(0.86 - 0.4 * fold - 0.16 * band.samples[i].ny + 0.05 * side, 0.35, 1)
        }
    }

    static func step(_ value: DayOneMedalRibbonState, _ elapsed: Double, _ worn: Double, _ pivot: DayOneMedalPoint, _ attach: DayOneMedalPoint, _ scale: Double, _ reduced: Bool) -> DayOneMedalRibbonState {
        var state = value
        let amount = DayOneMedalPhysics.clamp(worn, 0, 1)
        if amount == 0 {
            state.active = false
            state.opacity = 0
            state.worn = 0
            state.leaving = false
            state.caught = false
            return state
        }
        let fresh = !state.active
        if amount < state.worn - 0.00001 && !state.leaving {
            state.leaving = true
            state.release = state.tip
            state.releaseWorn = state.worn
        } else if amount > state.worn + 0.00001 && state.leaving { state.leaving = false }
        let catchNow = !state.leaving && amount >= 0.55
        let catchKick = catchNow && !state.caught && !fresh
        var tipX = attach.x
        var tipY = attach.y
        if !reduced {
            if state.leaving {
                let retract = 1 - amount / max(0.0001, state.releaseWorn)
                let lift = pow(retract, 0.8) * max(64 * scale, state.release.y - pivot.y + 64 * scale)
                tipX = state.release.x
                tipY = state.release.y - lift
            } else {
                let drop = min(1, amount / 0.55)
                let lift = (1 - drop * drop) * max(64 * scale, attach.y - pivot.y + 64 * scale)
                tipY -= lift
            }
        }
        let travel = fresh ? 0 : hypot(tipX - state.tip.x, tipY - state.tip.y)
        state.tip = DayOneMedalPoint(x: tipX, y: tipY)
        state.opacity = reduced ? amount : min(1, amount * 12)
        let duration = DayOneMedalPhysics.clamp(elapsed, 0, 0.1)
        let steps = max(1, Int(ceil(duration * 120)), min(24, Int(ceil(travel / (4 * scale)))))
        let dt = duration > 0 ? duration / Double(steps) : 1 / 120
        for b in 0..<2 {
            var band = state.bands[b]
            let side = b == 0 ? -1.0 : 1.0
            let tx = pivot.x + side * 46 * scale
            let ty = pivot.y
            let ex = tipX - side * 4 * scale
            let ey = tipY - 13 * scale
            if fresh || reduced { seed(&band, tx, ty, ex, ey, side, reduced) }
            if !reduced && (duration > 0 || fresh || travel > 0) {
                let oldTop = band.top
                let oldEnd = band.end
                for sub in 0..<steps {
                    let blend = Double(sub + 1) / Double(steps)
                    for i in 1..<14 {
                        var p = band.points[i]
                        let ratio = dt / (sub == 0 ? state.step : dt)
                        let drag = exp(-3.8 * dt)
                        let vx = DayOneMedalPhysics.clamp((p.x - p.px) * ratio, -900 * scale * dt, 900 * scale * dt)
                        let vy = DayOneMedalPhysics.clamp((p.y - p.py) * ratio, -900 * scale * dt, 900 * scale * dt)
                        p.px = p.x
                        p.py = p.y
                        p.x += vx * drag
                        p.y += vy * drag + 430 * scale * dt * dt
                        if catchKick && sub == 0 { p.x += side * 38 * scale * dt * sin(Double(i) / 14 * .pi) }
                        band.points[i] = p
                    }
                    for i in 1..<14 {
                        let a = band.points[i - 1]
                        let c = band.points[i + 1]
                        let bend = 1 - exp(-5 * dt)
                        band.points[i].x += ((a.x + c.x) * 0.5 - band.points[i].x) * bend
                        band.points[i].y += ((a.y + c.y) * 0.5 - band.points[i].y) * bend
                    }
                    constrain(&band, oldTop.x + (tx - oldTop.x) * blend, oldTop.y + (ty - oldTop.y) * blend,
                              oldEnd.x + (ex - oldEnd.x) * blend, oldEnd.y + (ey - oldEnd.y) * blend)
                }
            }
            band.top = DayOneMedalPoint(x: tx, y: ty)
            band.end = DayOneMedalPoint(x: ex, y: ey)
            sample(&band, side, scale, reduced)
            state.bands[b] = band
        }
        state.step = dt
        state.active = true
        state.caught = catchNow
        state.worn = amount
        return state
    }
}

struct DayOneMedalRibbonView: View {
    let state: DayOneMedalRibbonState
    let scale: Double
    private let colours: [UInt32] = [0x111826, 0x182235, 0x202D42, 0x29374D, 0x2D3B50, 0x29384D, 0x233147, 0x1B283C, 0x121C2E]

    var body: some View {
        Canvas { context, _ in
            guard state.active else { return }
            var ink = context
            ink.opacity = state.opacity
            for (bandIndex, band) in state.bands.enumerated() {
                let samples = band.samples
                if bandIndex == 1 {
                    var overlap = ink
                    overlap.clip(to: strip(state.bands[0].samples, -1, 1))
                    overlap.translateBy(x: -2 * scale, y: 2 * scale)
                    overlap.addFilter(.blur(radius: 2.5))
                    overlap.fill(strip(samples, -1, 1), with: .color(.black.opacity(0.7)))
                }
                var shadow = ink
                shadow.translateBy(x: 2 * scale, y: 3 * scale)
                shadow.addFilter(.blur(radius: 2.5))
                shadow.fill(strip(samples, -1, 1), with: .color(.black.opacity(0.24)))
                for lane in 0..<9 {
                    ink.fill(strip(samples, -1 + Double(lane) * 2 / 9, min(1, -1 + Double(lane + 1) * 2 / 9 + 0.008)), with: .color(DayOneMedalColour.hex(colours[lane])))
                }
                for side in [-1.0, 1.0] {
                    ink.stroke(edge(samples, side * 0.8), with: .color(DayOneMedalColour.hex(0x73522E).opacity(0.9)), lineWidth: 1.9 * scale)
                    ink.stroke(edge(samples, side * 0.8), with: .color(DayOneMedalColour.hex(0xDDA85A).opacity(0.84)), lineWidth: 0.95 * scale)
                    ink.stroke(edge(samples, side * 0.975), with: .color(DayOneMedalColour.hex(0x526176).opacity(side < 0 ? 0.55 : 0.16)), lineWidth: 0.55 * scale)
                }
                var travel = 0.0
                var ribAt = 0.0
                var index = 0
                for i in 1..<samples.count {
                    let a = samples[i - 1]
                    let b = samples[i]
                    let span = hypot(b.x - a.x, b.y - a.y)
                    while ribAt <= travel + span {
                        let t = span > 0 ? (ribAt - travel) / span : 0
                        let x = a.x + (b.x - a.x) * t
                        let y = a.y + (b.y - a.y) * t
                        let nx = a.nx + (b.nx - a.nx) * t
                        let ny = a.ny + (b.ny - a.ny) * t
                        let half = (a.width + (b.width - a.width) * t) * 0.93
                        let p = line(x - nx * half, y - ny * half, x + nx * half, y + ny * half)
                        ink.stroke(p, with: .color(DayOneMedalColour.hex(0x8291A7).opacity(index % 3 == 0 ? 0.22 : 0.13)), lineWidth: 0.48 * scale)
                        ink.stroke(line(x - nx * half - ny * 0.5 * scale, y - ny * half + nx * 0.5 * scale,
                                        x + nx * half - ny * 0.5 * scale, y + ny * half + nx * 0.5 * scale),
                                   with: .color(DayOneMedalColour.hex(0x030610).opacity(0.24)), lineWidth: 0.5 * scale)
                        if index % 3 == 0 {
                            for side in [-1.0, 1.0] {
                                let sx = x + nx * half * 0.86 * side
                                let sy = y + ny * half * 0.86 * side
                                ink.stroke(line(sx, sy, sx - ny * 0.8 * scale, sy + nx * 0.8 * scale), with: .color(DayOneMedalColour.hex(0xEAC68D).opacity(0.48)), lineWidth: 0.42 * scale)
                            }
                        }
                        ribAt += 1.45 * scale
                        index += 1
                    }
                    travel += span
                    ink.fill(strip([a, b], -1, 1), with: .color(DayOneMedalColour.hex(0x000006).opacity(1 - (a.light + b.light) * 0.5)))
                }
                for lane in -3...3 {
                    ink.stroke(edge(samples, Double(lane) * 0.2), with: .color(DayOneMedalColour.hex(0x96A2B5).opacity(0.07)), lineWidth: 0.3 * scale)
                }
            }
            ink.translateBy(x: state.tip.x, y: state.tip.y)
            ink.scaleBy(x: scale, y: scale)
            let loop = Path(roundedRect: CGRect(x: -7, y: -17, width: 14, height: 22), cornerRadius: 5)
            ink.stroke(loop, with: .color(DayOneMedalColour.hex(0x08090C)), lineWidth: 4.2)
            ink.stroke(loop, with: .color(DayOneMedalColour.hex(0x343438)), lineWidth: 2.8)
            var highlight = Path()
            highlight.move(to: CGPoint(x: -6.5, y: -2))
            highlight.addLine(to: CGPoint(x: -6.5, y: -11))
            highlight.addQuadCurve(to: CGPoint(x: -2, y: -16), control: CGPoint(x: -6.5, y: -16))
            ink.stroke(highlight, with: .color(DayOneMedalColour.hex(0xB4A58B).opacity(0.85)), lineWidth: 0.85)
            let collar = Path(roundedRect: CGRect(x: -13, y: -19, width: 26, height: 7), cornerRadius: 2)
            ink.fill(collar, with: .color(DayOneMedalColour.hex(0x222329)))
            ink.stroke(collar, with: .color(DayOneMedalColour.hex(0x505056).opacity(0.8)), lineWidth: 0.7)
            ink.stroke(line(-10, -18, 3, -18), with: .color(DayOneMedalColour.hex(0xB4A58B).opacity(0.85)), lineWidth: 0.85)
            ink.stroke(line(8.5, -15, 10, -15), with: .color(DayOneMedalColour.hex(0xB4A58B).opacity(0.3)), lineWidth: 0.85)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func edge(_ samples: [DayOneMedalRibbonSample], _ edge: Double) -> Path {
        var path = Path()
        for (i, p) in samples.enumerated() {
            let point = CGPoint(x: p.x + p.nx * p.width * edge, y: p.y + p.ny * p.width * edge)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        return path
    }

    private func strip(_ samples: [DayOneMedalRibbonSample], _ left: Double, _ right: Double) -> Path {
        var path = edge(samples, left)
        for p in samples.reversed() { path.addLine(to: CGPoint(x: p.x + p.nx * p.width * right, y: p.y + p.ny * p.width * right)) }
        path.closeSubpath()
        return path
    }

    private func line(_ x: Double, _ y: Double, _ ex: Double, _ ey: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x, y: y))
        path.addLine(to: CGPoint(x: ex, y: ey))
        return path
    }
}
