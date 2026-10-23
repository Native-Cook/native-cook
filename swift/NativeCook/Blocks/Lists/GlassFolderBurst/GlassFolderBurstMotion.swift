import Foundation
import CoreGraphics

struct GlassFolderBurstLayout {
    let width: Double
    let height: Double
    let headerTop: Double
    let listTop: Double
    let restY: Double
    let dockY: Double
    let count: Int

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        headerTop = max(top, 20) + 12
        listTop = headerTop + 56
        let floor = height - max(bottom, 16) - 24
        dockY = floor - 52
        restY = (height * 0.5).rounded()
        count = max(5, min(9, Int(floor - 104 - 32 - listTop + 8) / 60))
    }

    func slot(_ index: Int) -> CGRect {
        CGRect(x: 24, y: listTop + Double(index) * 60, width: width - 48, height: 52)
    }
}

struct GlassFolderBurstFolder {
    var cx: Double
    var cy: Double
    var scale: Double
    var open: Double
    var press: Double
    var squash: Double
    var flow: Double

    var placement: (x: Double, y: Double, sx: Double, sy: Double) {
        let squeeze = max(-1, min(1, squash)) * 0.06
        let size = max(0.001, scale) * (1 - 0.03 * press)
        return (cx, cy + 52 * squeeze * size, size * (1 + squeeze), size * (1 - squeeze))
    }
}

struct GlassFolderBurstOrb {
    var x: Double
    var y: Double
    var radius: Double
    var glow: Double
    var mark: Double
    var beat: Double
}

enum GlassFolderBurstMath {
    static func clamp(_ x: Double) -> Double { max(0, min(1, x)) }
    static func smooth(_ x: Double) -> Double {
        let t = clamp(x)
        return t * t * (3 - 2 * t)
    }
    static func bezier(_ t: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(t)
        var lo = 0.0
        var hi = 1.0
        var u = x
        for _ in 0..<14 {
            let q = 1 - u
            let at = 3 * q * q * u * x1 + 3 * q * u * u * x2 + u * u * u
            if at < x { lo = u } else { hi = u }
            u = (lo + hi) / 2
        }
        return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
    }
    static func rowCenter(_ p: Double, _ m: Double, _ i: Int, _ layout: GlassFolderBurstLayout, _ folder: GlassFolderBurstFolder) -> CGPoint {
        let t = clamp(p)
        let split = 0.45 - m * 0.05
        let jet = min(1, t / split)
        let jitter = (Double((i * 7 + 3) % 11) / 10 - 0.5) * 52
        let x = folder.cx + 80 * min(1, layout.width / 402) * smooth(jet) + jitter * folder.scale
        let peel = smooth((t - split) / (0.82 - split))
        let start = folder.cy + 10 * folder.scale
        let jetTop = folder.cy - 22 * folder.scale - 200
        let end = layout.slot(i).midY
        let clearance = (i == 0 ? 12 : 24 + Double(i) * 2) * (1 - m)
        let peak = end - clearance
        let y: Double
        if t <= split {
            y = start + (jetTop - start) * t / split
        } else if t < 0.85 {
            y = jetTop + (peak - jetTop) * smooth((t - split) / (0.85 - split))
        } else {
            y = peak + clearance * smooth((t - 0.85) / 0.15) + max(0, p - 1) * 90
        }
        return CGPoint(x: x + (layout.width / 2 - x) * peel, y: y)
    }
}

struct GlassFolderBurstRowState {
    var p = 0.0
    var v = 0.0
    var mode = 0.0
    var trigger = 0.0
    var active = false
    var landed = false
    var ejected = false
    var fade = 0.0
    var modeFrom = 0.0
    var modeAt = 0.0
    var flash = 0.0
}

/// All release times, forces, fades and springs use the same 240 Hz clock as the source.
struct GlassFolderBurstSimulation {
    var rows: [GlassFolderBurstRowState]
    var time = 0.0
    var target = false
    var completed = true
    var flow = 0.0
    var bloomX = 0.0
    var bloomY = 0.0
    var bloomStrength = 0.0

    init(count: Int) {
        rows = Array(repeating: GlassFolderBurstRowState(), count: count)
    }

    mutating func command(_ open: Bool, reduced: Bool) {
        guard target != open else { return }
        target = open
        completed = false
        for i in rows.indices {
            rows[i].trigger = time + (reduced ? 0.030 : open ? 0.045 : 0.050) * Double(open ? i : rows.count - 1 - i)
            rows[i].active = false
            rows[i].landed = rows[i].p >= 1
            rows[i].ejected = rows[i].p >= 0.06
        }
    }

    mutating func step(_ h: Double, layout: GlassFolderBurstLayout, folder: GlassFolderBurstFolder, reduced: Bool) -> Int {
        time += h
        var events = 0
        var near = 0.0
        var flying = 0.0
        var weight = 0.0
        var sumX = 0.0
        var sumY = 0.0
        var allOut = true
        var allIn = true
        for i in rows.indices {
            var row = rows[i]
            var p = row.p
            var v = row.v
            let rate = 1 / (1 / 0.70 + (1 / 0.98 - 1 / 0.70) * Double(i) / 8)
            row.flash = max(0, row.flash - h / 0.140)
            if !completed && time + 1e-10 >= row.trigger {
                if !row.active {
                    row.active = true
                    row.modeFrom = row.mode
                    row.modeAt = time
                    if !reduced && target && p <= 0 { v = max(v, 0.8 * rate) }
                }
                let blend = GlassFolderBurstMath.smooth((time - row.modeAt) / 0.180)
                row.mode = row.modeFrom + ((target ? 0.0 : 1.0) - row.modeFrom) * blend
                if reduced {
                    p = target ? 1 : 0
                    v = 0
                    row.fade = target ? min(1, row.fade + h / 0.200) : max(0, row.fade - h / 0.200)
                    if target && !row.landed {
                        events |= 2
                        row.landed = true
                    }
                    if !target && row.p > 0 { events |= 4 }
                } else if target {
                    v += (38 * rate * rate * (1 - p) - 10 * rate * v) * h
                    p += v * h
                    if p < 0 {
                        p = 0
                        v = 0.8
                    }
                    if !row.ejected && p >= 0.06 {
                        events |= 1
                        row.ejected = true
                    }
                    if !row.landed && p >= 1 {
                        events |= 2
                        row.landed = true
                        row.flash = 1
                    }
                    if abs(p - 1) < 0.003 && abs(v) < 0.02 {
                        p = 1
                        v = 0
                    }
                } else if p > 0 || v > 0 {
                    v += (-6.3 / (max(0, p) + 0.14) - 1.2 * v) * h
                    p += v * h
                    if p <= 0 {
                        p = 0
                        v = 0
                        events |= 4
                    }
                }
            }
            row.p = p
            row.v = v
            if abs(p - 1) >= 0.003 || abs(v) >= 0.02 || (reduced && row.fade < 1) { allOut = false }
            if p != 0 || v != 0 || (reduced && row.fade > 0) { allIn = false }
            if !reduced && p > 0 && p < 0.22 && abs(v) > 0.02 { near += 1 }
            if !reduced && p > 0.015 && (abs(p - 1) > 0.003 || abs(v) > 0.02) {
                flying += 1
                let brightness = 0.12 + sin(.pi * min(1, p)) * 0.88
                weight += brightness
                let point = GlassFolderBurstMath.rowCenter(p, row.mode, i, layout, folder)
                sumX += point.x * brightness
                sumY += point.y * brightness
            }
            rows[i] = row
        }
        flow += (min(1, near / 3) - flow) * (1 - exp(-18 * h))
        bloomStrength += (flying / Double(rows.count) - bloomStrength) * (1 - exp(-12 * h))
        if weight > 0 {
            bloomX = sumX / weight
            bloomY = sumY / weight
        }
        if !completed && ((target && allOut) || (!target && allIn)) {
            events |= target ? 8 : 16
            completed = true
        }
        return events
    }
}

enum GlassFolderBurstInput {
    case touchStart, touchEnd, folderDown, folderUp, toggle, rowDown(Int), rowUp(Int), select(Int)
}

final class GlassFolderBurstMotion {
    var sim: GlassFolderBurstSimulation
    let autoPlay: Bool
    var reduced: Bool
    var time = 0.0
    var accumulator = 0.0
    var loop = 0.0
    var idleTime = 0.0
    var idleGain: Double
    var paused = false
    var resumeAt = 0.0
    var recovering = false
    var held = false
    var touching = false
    var expanded = false
    var age = 0.0
    var rowsStarted = false
    var launched = false
    var returning = false
    var home = true
    var dock = 0.0
    var dockFrom = 0.0
    var dockTarget = 0.0
    var dockAge = 1.0
    var springs = Array(repeating: 0.0, count: 14)
    var pressFrom = 0.0
    var pressAge = 1.0
    var rowFrom = 0.0
    var rowAge = 1.0
    var pressed = -1
    var selected = -1
    var selectedMix: [Double]
    var landed = 0
    var events = 0
    var sounded = false
    var shutSounded = true
    var fade = 1.0
    var reducedFrom = 1.0
    var reducedSnapped = true
    var hasInput = false
    private var lastDate: Date?

    init(count: Int, reduced: Bool = false, autoPlay: Bool = false) {
        self.sim = GlassFolderBurstSimulation(count: count)
        self.reduced = reduced
        self.autoPlay = autoPlay
        self.idleGain = autoPlay ? 0 : 1
        self.selectedMix = Array(repeating: 0, count: count)
    }

    func folder(_ layout: GlassFolderBurstLayout) -> GlassFolderBurstFolder {
        GlassFolderBurstFolder(cx: layout.width / 2,
            cy: layout.restY + (layout.dockY - layout.restY) * dock + springs[6]
                + (reduced ? 0 : 2 * sin(idleTime * .pi * 2 / 5) * (1 - dock) * idleGain),
            scale: 1.5 - 0.5 * dock, open: springs[0], press: springs[8], squash: springs[4],
            flow: !expanded && !home && !(sim.completed && !sim.target) ? max(0.5, sim.flow) : sim.flow)
    }

    func orb(_ layout: GlassFolderBurstLayout) -> GlassFolderBurstOrb {
        let f = folder(layout)
        let p = GlassFolderBurstMath.clamp(springs[2])
        let rise = springs[12]
        let storedY = f.cy + (2 - rise) * f.scale
        let rail = GlassFolderBurstMath.smooth(p / 0.65)
        let bow = p < 0.65 ? sin(.pi * p / 0.65) : 0
        return GlassFolderBurstOrb(x: f.cx + (44 - f.cx) * rail - 36 * bow,
            y: storedY + (layout.headerTop + 20 - storedY) * p - 60 * sin(.pi * p),
            radius: 24 * f.scale + (20 - 24 * f.scale) * p,
            glow: reduced ? 0.55 : p > 0 && p < 0.98 ? 1 : p >= 0.98 ? 0.7 : 0.56 + 0.06 * sin(idleTime * .pi * 2 / 5) * idleGain,
            mark: max(GlassFolderBurstMath.smooth((p - 0.05) / 0.30), GlassFolderBurstMath.smooth(rise / 16)),
            beat: reduced ? 0 : selected >= 0 ? 1 : 0.3)
    }

    func send(_ input: GlassFolderBurstInput) {
        if autoPlay && !paused { held = false }
        hasInput = true
        paused = true
        recovering = false
        resumeAt = time + 3.5
        switch input {
        case .touchStart: touching = true
        case .touchEnd: touching = false
        case .folderDown:
            press(true)
            events |= 2
        case .folderUp: press(false)
        case .toggle: toggle()
        case .rowDown(let index): press(true, row: index)
        case .rowUp(let index): press(false, row: index)
        case .select(let index):
            if expanded && landed & (1 << index) != 0 {
                select(selected == index ? -1 : index)
                events |= 8
            }
        }
    }

    func press(_ down: Bool, row: Int = -1) {
        held = down
        if row < 0 {
            pressed = -1
            pressFrom = springs[8]
            pressAge = 0
        } else {
            pressed = row
            rowFrom = springs[10]
            rowAge = 0
        }
    }

    func select(_ index: Int) { selected = index }

    func toggle() {
        expanded.toggle()
        age = 0
        rowsStarted = false
        sounded = false
        returning = false
        home = false
        shutSounded = false
        if !expanded {
            select(-1)
            landed = 0
        }
        if reduced {
            reducedFrom = fade
            reducedSnapped = false
        } else if expanded {
            dockTo(1)
            if springs[2] > 0.001 { launched = true }
        }
    }

    private func dockTo(_ goal: Double) {
        dockFrom = dock
        dockTarget = goal
        dockAge = 0
    }

    private func follow(_ at: Int, _ goal: Double, _ k: Double, _ c: Double, _ h: Double) {
        springs[at + 1] += (k * (goal - springs[at]) - c * springs[at + 1]) * h
        springs[at] += springs[at + 1] * h
        if abs(springs[at] - goal) < 0.0001 && abs(springs[at + 1]) < 0.001 {
            springs[at] = goal
            springs[at + 1] = 0
        }
    }

    private func reset() {
        sim = GlassFolderBurstSimulation(count: selectedMix.count)
        loop = 0
        idleTime = 0
        idleGain = 0
        paused = false
        recovering = false
        held = false
        touching = false
        expanded = false
        age = 0
        rowsStarted = false
        launched = false
        returning = false
        home = true
        dock = 0
        dockFrom = 0
        dockTarget = 0
        dockAge = 1
        springs = Array(repeating: 0, count: 14)
        pressFrom = 0
        pressAge = 1
        rowFrom = 0
        rowAge = 1
        pressed = -1
        selected = -1
        selectedMix = Array(repeating: 0, count: selectedMix.count)
        landed = 0
        sounded = false
        shutSounded = true
        fade = 1
        reducedFrom = 1
        reducedSnapped = true
    }

    func advance(to date: Date, layout: GlassFolderBurstLayout) {
        defer { lastDate = date }
        guard let lastDate else { return }
        // Preserve wall time on missed display frames without changing the bounded integrator.
        var elapsed = max(0, date.timeIntervalSince(lastDate))
        while elapsed > 1e-10 {
            let subframe = min(0.05, elapsed)
            step(subframe, layout: layout)
            elapsed -= subframe
        }
    }

    func pauseClock() { lastDate = nil }

    func step(_ dt: Double, layout: GlassFolderBurstLayout) {
        accumulator += max(0, min(0.05, dt))
        let h = 1.0 / 240
        while accumulator + 1e-10 >= h {
            accumulator = max(0, accumulator - h)
            time += h
            age += h
            if autoPlay && !reduced {
                if paused && !held && !touching && time >= resumeAt {
                    recovering = true
                    if expanded { toggle() }
                    if home && dock == 0 { reset() }
                }
                if !paused {
                    let before = loop
                    loop += h
                    if (before < 0.86 && loop >= 0.86) || (before < 6.06 && loop >= 6.06) { press(true) }
                    if (before < 1 && loop >= 1) || (before < 6.2 && loop >= 6.2) {
                        press(false)
                        toggle()
                    }
                    if before < 4.3 && loop >= 4.3 { press(true, row: 2) }
                    if before < 4.44 && loop >= 4.44 {
                        press(false, row: 2)
                        select(2)
                    }
                    if loop >= 10 - 1e-9 { reset() }
                }
            }
            if autoPlay && !paused {
                idleTime = loop
                idleGain = GlassFolderBurstMath.smooth(loop / 0.6) * (1 - GlassFolderBurstMath.smooth((loop - 9.4) / 0.6))
            } else {
                idleTime += h
                idleGain += ((recovering ? 0.0 : 1.0) - idleGain) * (1 - exp(-8 * h))
            }
            pressAge += h
            rowAge += h
            for i in selectedMix.indices {
                let goal = selected == i ? 1.0 : 0.0
                selectedMix[i] += max(-h / 0.16, min(h / 0.16, goal - selectedMix[i]))
            }
            if held && pressed < 0 {
                springs[8] = pressFrom + (1 - pressFrom) * GlassFolderBurstMath.bezier(pressAge / 0.12, 0.23, 1, 0.32, 1)
            } else { follow(8, 0, 300, 20, h) }
            if held && pressed >= 0 {
                springs[10] = rowFrom + (1 - rowFrom) * GlassFolderBurstMath.bezier(rowAge / 0.12, 0.23, 1, 0.32, 1)
            } else {
                follow(10, 0, 300, 20, h)
                if abs(springs[10]) < 0.0001 { pressed = -1 }
            }
            if reduced {
                if !reducedSnapped && age >= 0.12 {
                    reducedSnapped = true
                    dock = expanded ? 1 : 0
                    springs[0] = expanded ? 0.72 : 0
                    springs[2] = dock
                    sim = GlassFolderBurstSimulation(count: layout.count)
                    if expanded { sim.command(true, reduced: true) }
                    home = !expanded
                }
                fade = reducedSnapped ? GlassFolderBurstMath.clamp((age - 0.12) / 0.22) : reducedFrom * (1 - GlassFolderBurstMath.clamp(age / 0.12))
                if home && (!hasInput || age > 0.34) { fade = 1 }
            } else {
                if expanded {
                    if age >= 0.56 && !launched {
                        launched = true
                        springs[4] += 0.6
                    }
                    if age >= 0.76 && !rowsStarted {
                        sim.command(true, reduced: false)
                        rowsStarted = true
                    }
                } else if !home {
                    if age >= 0.09 && !rowsStarted {
                        sim.command(false, reduced: false)
                        rowsStarted = true
                    }
                    if age >= 0.09 + Double(layout.count - 1) * 0.05 {
                        returning = true
                        launched = false
                    }
                    if returning && springs[2] < 0.04 && sim.completed && !sim.target {
                        home = true
                        dockTo(0)
                    }
                }
                let lean = home ? 0.0 : expanded ? age < 0.12 ? springs[0] : rowsStarted && sim.completed ? 0.72 : 1 : 1
                follow(0, lean, home ? 220 : lean == 0.72 ? 120 : 170, home ? 22 : lean == 0.72 ? 18 : 16, h)
                if !shutSounded && home && springs[0] < 0.1 {
                    shutSounded = true
                    events |= 16
                }
                follow(2, launched ? 1 : 0, launched ? 90 : 25, launched ? 14 : 9, h)
                follow(12, expanded ? 24 : 0, 70, 16, h)
                follow(4, 0, 260, 14, h)
                follow(6, 0, 260, 14, h)
                dockAge += h
                dock = dockAge >= 0.56 ? dockTarget : dockFrom + (dockTarget - dockFrom) * GlassFolderBurstMath.bezier(dockAge / 0.56, 0.77, 0, 0.175, 1)
            }
            let burstEvents = sim.step(h, layout: layout, folder: folder(layout), reduced: reduced)
            if !reduced {
                if burstEvents & 1 != 0 {
                    springs[4] += 0.35
                    springs[6] += 1.5
                    if !sounded {
                        events |= 4
                        sounded = true
                    }
                }
                if burstEvents & 4 != 0 {
                    springs[4] -= 0.3
                    springs[6] += 1
                }
            }
            landed = 0
            if expanded {
                for i in sim.rows.indices where sim.rows[i].p == 1 && sim.rows[i].v == 0 && (!reduced || sim.rows[i].fade == 1) {
                    landed |= 1 << i
                }
            }
        }
    }
}
