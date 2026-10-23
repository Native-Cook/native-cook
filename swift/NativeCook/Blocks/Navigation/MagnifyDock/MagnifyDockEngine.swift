import SwiftUI
import UIKit

enum MagnifyDockMetrics {
    // The dock. Icons are `icon` pt at rest, `gap` apart, inside `pad` of glass that sits
    // `dockLift` pt above the home indicator. Icons are vector art, redrawn at every size.
    static let icon = 40.0
    static let gap = 7.0
    static let pad = 8.0
    static let pitch = icon + gap
    static let dockLift = 8.0
    static let dockRadius = 18.0
    // Magnification: an icon grows with a cosine bell of its resting centre's distance from the
    // focus, up to `maxScale` under it and nothing past `reach` pt. Grown icons push the others
    // out from the dock's centre; on a screen too narrow for that the whole dock is scaled to fit
    // within `edge` pt of its sides.
    static let maxScale = 1.55
    static let reach = 110.0
    static let edge = 6.0
    // The focus follows the finger on a critically damped spring of natural frequency `focusOmega`
    // (rad/s, ~40 ms behind a moving finger), and the magnification eases in and out on another
    // (`magOmega`), letting go when the finger rises more than `leave` pt above the dock. Both
    // springs are stepped with their exact solution, so they stay smooth at any frame rate.
    static let focusOmega = 50.0
    static let magOmega = 15.0
    static let leave = 120.0
    // The name popover: one pill that glides between icons on a critically damped spring
    // (`labelOmega`), resizing to each name while the old name fades out (`nameOut` s) and the new
    // one in (`nameFade` s, once the old is below `nameGate`); it appears and leaves with a fade, a
    // 4 pt rise and a 0.94 → 1 scale on its own spring (`showOmega`). It sits `labelGap` pt above
    // the icon under the focus.
    static let labelOmega = 26.0
    static let showOmega = 18.0
    static let nameFade = 0.07
    static let nameOut = 0.035
    static let nameGate = 0.25
    static let labelGap = 12.0
    static let labelHeight = 28.0
    static let labelPad = 12.0
    static let labelFont = UIFont.systemFont(ofSize: 13, weight: .semibold)
    // Launching: the icon jumps `bounces` times, each `bounceTime` s and `bounceDecay` as high as
    // the one before, then its running dot fades in.
    static let bounceHeight = 22.0
    static let bounceTime = 0.42
    static let bounces = 2
    static let bounceDecay = 0.5
    static let dotAt = 0.35
    // Editing: a press held `longPress` s without moving more than `slop` pt wiggles every icon by
    // ±`wiggleDeg` at `wiggleHz` and lifts the pressed one to `liftScale`. Moving it past half a slot reorders
    // the others, which spring to their new places (stiffness and damping per unit mass).
    static let longPress = 0.5
    static let slop = 8.0
    static let tapTime = 0.4
    static let wiggleDeg = 2.4
    static let wiggleHz = 4.2
    static let liftScale = 1.25
    static let slotStiffness = 320.0
    static let slotDamping = 30.0

    // autoPlay (seconds): the finger lands on the first icon, slides to the last and back to Hum,
    // lifts and taps it to launch it. Then it holds Wayfare until the dock starts wiggling,
    // carries it two places right, puts it down, carries it back and taps Done; Hum quits before
    // the loop starts over.
    static let loop = 12.0
    static let sweep: [(Double, Double)] = [(0.8, 0), (3.0, 6), (4.2, 2)]
    static let liftAt = 4.35
    static let tapAt = 4.95
    static let holdAt = 6.3
    static let holdSlot = 3
    // start, end, from slot, to slot
    static let carry: [(Double, Double, Int, Int)] = [(6.95, 7.75, 3, 5), (8.45, 9.25, 5, 3)]
    static let dropLag = 0.15
    static let doneAt = 9.95
    static let quitAt = 10.4
    static let userHold = 4.0
}

enum MagnifyDockMath {
    private typealias M = MagnifyDockMetrics

    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, x)) }

    static func smooth(_ u: Double) -> Double {
        let t = clamp(u, 0, 1)
        return t * t * (3 - 2 * t)
    }

    static func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    /// 1 while a virtual touch from t0 to t1 is down, fading in and out over 0.2 s around it.
    static func touchWindow(_ c: Double, _ t0: Double, _ t1: Double) -> Double {
        clamp(min((c - t0 + 0.25) / 0.2, (t1 + 0.2 - c) / 0.2), 0, 1)
    }

    /// Width of the dock at rest, glass included.
    static func restingWidth(_ count: Int) -> Double {
        Double(count) * M.icon + Double(count - 1) * M.gap + 2 * M.pad
    }

    /// Centre of slot `slot` at rest, for a dock centred on a screen `width` wide.
    static func slotCentre(_ slot: Double, _ count: Int, _ width: Double) -> Double {
        width / 2 - restingWidth(count) / 2 + M.pad + M.icon / 2 + slot * M.pitch
    }

    /// One frame of a critically damped spring of natural frequency `omega`, solved exactly: the
    /// value and its speed after `dt` s, heading for `target`.
    static func critical(_ x: Double, _ v: Double, _ target: Double, _ omega: Double, _ dt: Double) -> (Double, Double) {
        let d = x - target
        let e = exp(-omega * dt)
        let k = (v + omega * d) * dt
        return (target + (d + k) * e, (v - omega * k) * e)
    }

    /// JavaScript's Math.round: halves go up.
    static func jsRound(_ x: Double) -> Double { floor(x + 0.5) }

    /// Height of a launch bounce `age` s after the tap: arches, each lower than the last.
    static func bounceAt(_ age: Double) -> Double {
        if age < 0 || age >= Double(M.bounces) * M.bounceTime { return 0 }
        let k = floor(age / M.bounceTime)
        let u = age / M.bounceTime - k
        return M.bounceHeight * pow(M.bounceDecay, k) * 4 * u * (1 - u)
    }
}

struct MagnifyDockLayout {
    var cx: [Double]
    var scale: [Double]
    var dockWidth: Double
    var fit: Double

    private typealias M = MagnifyDockMetrics
    private typealias X = MagnifyDockMath

    /// Where every icon sits and how big it is, for icons resting at `slots` and the focus at `focusX`.
    init(slots: [Double], focusX: Double, mag: Double, width: Double) {
        let count = slots.count
        var base: [Double] = []
        var extra: [Double] = []
        var total = 0.0
        for i in 0..<count {
            let c = X.slotCentre(slots[i], count, width)
            base.append(c)
            let d = abs(c - focusX)
            let bell = d < M.reach ? (cos(.pi * d / M.reach) + 1) / 2 : 0
            let e = M.icon * (M.maxScale - 1) * bell * max(0, mag)
            extra.append(e)
            total += e
        }
        let full = X.restingWidth(count) + total
        let fit = full > 0 ? min(1, (width - 2 * M.edge) / full) : 1
        cx = []
        scale = []
        for i in 0..<count {
            // Everything grown to this icon's left pushes it right, and the dock stays centred.
            var before = 0.0
            for j in 0..<count where j != i && (slots[j] < slots[i] || (slots[j] == slots[i] && j < i)) {
                before += extra[j]
            }
            let c = base[i] + before + extra[i] / 2 - total / 2
            cx.append(width / 2 + (c - width / 2) * fit)
            scale.append((1 + extra[i] / M.icon) * fit)
        }
        dockWidth = full * fit
        self.fit = fit
    }

    /// The icon under `x`, or -1.
    func icon(at x: Double) -> Int {
        var best = -1
        var bestD = Double.infinity
        for i in cx.indices {
            let d = abs(x - cx[i])
            if d < (M.icon * scale[i]) / 2 + M.gap / 2 && d < bestD {
                best = i
                bestD = d
            }
        }
        return best
    }
}

/// The name popover: its centre x and speed, its top, its width and speed, how far it shows and
/// that speed, and each name's opacity.
struct MagnifyDockLabel {
    var x = 0.0
    var xv = 0.0
    var y = 0.0
    var w = 0.0
    var wv = 0.0
    var show = 0.0
    var showV = 0.0
    var names: [Double] = []
}

/// One frame to draw.
struct MagnifyDockFrame {
    var time: Double
    var layout: MagnifyDockLayout
    var mag: Double
    var wiggle: Double
    var fingerX: Double
    var focusX: Double
    var dragId: Int
    var dragY: Double
    var launchedAt: [Double]
    var running: [Double]
    var editing: Bool
    var ghostX: Double
    var ghostY: Double
    var ghostAlpha: Double
    var ghostPress: Double
    var label: MagnifyDockLabel
}

struct MagnifyDockStage {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double

    var dockBottom: Double { height - bottom - MagnifyDockMetrics.dockLift }
    var baseline: Double { dockBottom - MagnifyDockMetrics.pad }
    var dockTop: Double { dockBottom - MagnifyDockMetrics.icon - 2 * MagnifyDockMetrics.pad }
}

/// The dock's state, stepped once a frame like the RN frame callback, and the touch that drives it.
@MainActor
final class MagnifyDockEngine {
    private typealias M = MagnifyDockMetrics
    private typealias X = MagnifyDockMath

    private var count = 0
    private var time = 0.0
    private var stage = MagnifyDockStage(width: 0, height: 0, top: 0, bottom: 0)
    private var fingerX = -1000.0
    private var fingerY = 0.0
    private var focusX = -1000.0
    private var focusV = 0.0
    private var mag = 0.0
    private var magV = 0.0
    private var magTarget = 0.0
    // Resting slot of each app (by id), springing towards its index in `order`.
    private var slots: [Double] = []
    private var slotSpeed: [Double] = []
    private var order: [Int] = []
    private var launchedAt: [Double] = []
    private var runningOn: [Double] = []
    private var running: [Double] = []
    private var editing = false
    private var wiggle = 0.0
    private var dragId = -1
    private var dragY = 0.0
    private var pressing = false
    private var moved = false
    private var inDock = false
    private var pressAt = 0.0
    private var startX = 0.0
    private var startY = 0.0
    private var touchDown = false
    // autoPlay
    private var clock = 0.0
    private var looping = false
    private var lastAuto: Bool?
    private var hold = 0.0
    private var autoId = -1
    private var ghostX = 0.0
    private var ghostY = 0.0
    private var ghostAlpha = 0.0
    private var ghostPress = 0.0
    private var lastDate: Date?
    private var reduced = false
    private var label = MagnifyDockLabel()
    private var nameWidths: [Double] = []
    var onLaunch: ((Int) -> Void)?

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()

    private var layout: MagnifyDockLayout {
        MagnifyDockLayout(slots: slots, focusX: focusX, mag: mag, width: stage.width)
    }

    private func setUp(count: Int, names: [String]) {
        self.count = count
        label = MagnifyDockLabel(names: Array(repeating: 0, count: count))
        // Each name's width, so the popover can size itself to it.
        nameWidths = names.map { ($0 as NSString).size(withAttributes: [.font: M.labelFont]).width }
        slots = (0..<count).map(Double.init)
        slotSpeed = Array(repeating: 0, count: count)
        order = Array(0..<count)
        launchedAt = Array(repeating: -100, count: count)
        runningOn = Array(repeating: 0, count: count)
        running = Array(repeating: 0, count: count)
    }

    func step(to date: Date, stage: MagnifyDockStage, names: [String], autoPlay: Bool, reducedMotion: Bool) -> MagnifyDockFrame {
        let count = names.count
        if self.count != count { setUp(count: count, names: names) }
        self.stage = stage
        reduced = reducedMotion
        if lastAuto != autoPlay {
            lastAuto = autoPlay
            looping = autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(max(raw, 0), 0.064)
        if dt > 0 { advance(dt) }
        return MagnifyDockFrame(
            time: time, layout: layout, mag: mag, wiggle: wiggle, fingerX: fingerX, focusX: focusX, dragId: dragId, dragY: dragY,
            launchedAt: launchedAt, running: running, editing: editing,
            ghostX: ghostX, ghostY: ghostY, ghostAlpha: ghostAlpha, ghostPress: ghostPress, label: label
        )
    }

    private func launch(_ id: Int, user: Bool) {
        guard id >= 0, id < count else { return }
        if runningOn[id] < 1 {
            launchedAt[id] = time
            runningOn[id] = 1
        }
        if user {
            light.impactOccurred()
            onLaunch?(id)
        }
    }

    /// Puts a carried icon down where it is; its slot spring carries it home.
    private func drop(user: Bool) {
        let id = dragId
        guard id >= 0 else { return }
        let W = stage.width
        let half = X.restingWidth(count) / 2 - M.pad - M.icon / 2
        let x = X.clamp(fingerX, W / 2 - half, W / 2 + half)
        slots[id] = (x - X.slotCentre(0, count, W)) / M.pitch
        dragId = -1
        if user { light.impactOccurred() }
    }

    /// Moves a carried icon with the finger; past half a slot it takes the new place.
    private func carry(to x: Double, _ y: Double) {
        let id = dragId
        guard id >= 0 else { return }
        let dy = y - startY
        dragY = dy < -70 ? -70 + (dy + 70) * 0.2 : dy > 12 ? 12 + (dy - 12) * 0.2 : dy
        let slot = Int(X.clamp(X.jsRound((x - X.slotCentre(0, count, stage.width)) / M.pitch), 0, Double(count - 1)))
        if order.firstIndex(of: id) != slot {
            var next = order.filter { $0 != id }
            next.insert(id, at: slot)
            order = next
            if !looping || hold > 0 { selection.selectionChanged() }
        }
    }

    private func touched() {
        hold = M.userHold
        ghostAlpha = 0
    }

    private func advance(_ dt: Double) {
        time += dt
        let t = time

        // A press held still on the dock starts editing and picks the icon up.
        if pressing && !moved && inDock && !editing && t - pressAt > M.longPress {
            editing = true
            magTarget = 0
            dragId = layout.icon(at: fingerX)
            startY = fingerY
            dragY = 0
            medium.impactOccurred()
        }

        // The focus trails the finger on a stiff, critically damped spring; from rest it starts there.
        if (mag < 0.02 && magTarget > 0) || reduced {
            focusX = fingerX
            focusV = 0
        } else {
            (focusX, focusV) = X.critical(focusX, focusV, fingerX, M.focusOmega, dt)
        }
        // The magnification swells and settles on a critically damped spring.
        let (m, mv) = X.critical(mag, magV, magTarget, M.magOmega, dt)
        magV = mv
        mag = abs(m - magTarget) < 0.0005 && abs(mv) < 0.01 ? magTarget : X.clamp(m, 0, 1)
        stepLabel(dt)
        wiggle += ((editing ? 1 : 0) - wiggle) * (1 - exp(-dt * 10))
        if dragId < 0 && dragY != 0 {
            let y = dragY * exp(-dt * 14)
            dragY = abs(y) < 0.1 ? 0 : y
        }

        // Each icon's slot springs to its place in `order`; a carried icon is left to the finger.
        for i in 0..<count where i != dragId {
            let goal = Double(order.firstIndex(of: i) ?? i)
            if abs(goal - slots[i]) <= 0.001 && abs(slotSpeed[i]) <= 0.001 { continue }
            if reduced {
                slots[i] = goal
                slotSpeed[i] = 0
                continue
            }
            // Small steps, so a long frame cannot make the spring overshoot and fling the icon away.
            let steps = max(1, Int((dt / 0.004).rounded(.up)))
            let h = dt / Double(steps)
            for _ in 0..<steps {
                slotSpeed[i] += (M.slotStiffness * (goal - slots[i]) - M.slotDamping * slotSpeed[i]) * h
                slots[i] += slotSpeed[i] * h
            }
            if abs(goal - slots[i]) < 0.001 && abs(slotSpeed[i]) < 0.01 {
                slots[i] = goal
                slotSpeed[i] = 0
            }
        }

        // Running dots fade in once the launch bounce is under way, and out when an app quits.
        for i in 0..<count {
            let goal: Double = runningOn[i] > 0 && t - launchedAt[i] > M.dotAt ? 1 : 0
            running[i] = abs(goal - running[i]) > 0.002 ? running[i] + (goal - running[i]) * (1 - exp(-dt * 9)) : goal
        }

        script(dt)
    }

    /// The popover: over the icon under the focus while the dock is swollen, gliding between icons.
    private func stepLabel(_ dt: Double) {
        let L = layout
        let over = mag > 0.3 && magTarget > 0 && !editing && dragId < 0 ? L.icon(at: focusX) : -1
        var lb = label
        if lb.names.count != count { lb.names = Array(repeating: 0, count: count) }
        if over >= 0 {
            let tx = L.cx[over]
            let ty = stage.baseline - M.icon * L.scale[over] - M.labelGap - M.labelHeight - 6
            let tw = (nameWidths.indices.contains(over) ? nameWidths[over] : 60) + M.labelPad * 2
            if lb.show < 0.02 || reduced {
                // Appearing: it starts right over its icon, showing its name.
                lb.x = tx
                lb.xv = 0
                lb.y = ty
                lb.w = tw
                lb.wv = 0
                for i in lb.names.indices { lb.names[i] = i == over ? 1 : 0 }
            } else {
                (lb.x, lb.xv) = X.critical(lb.x, lb.xv, tx, M.labelOmega, dt)
                lb.y += (ty - lb.y) * (1 - exp(-dt * M.labelOmega))
                let (nw, nwv) = X.critical(lb.w, lb.wv, tw, M.labelOmega, dt)
                lb.w = max(M.labelPad * 2, nw)
                lb.wv = nwv
                // The old name goes first and quickly; the new one comes in once it has mostly
                // gone, so two names never sit on top of each other.
                let out = 1 - exp(-dt / M.nameOut)
                var left = 0.0
                for i in lb.names.indices where i != over {
                    lb.names[i] = lb.names[i] < 0.01 ? 0 : lb.names[i] * (1 - out)
                    left = max(left, lb.names[i])
                }
                if left < M.nameGate { lb.names[over] += (1 - lb.names[over]) * (1 - exp(-dt / M.nameFade)) }
            }
        }
        if reduced {
            lb.show = over >= 0 ? 1 : 0
            lb.showV = 0
        } else {
            let (sh, shv) = X.critical(lb.show, lb.showV, over >= 0 ? 1 : 0, M.showOmega, dt)
            lb.show = X.clamp(sh, 0, 1)
            lb.showV = shv
            if over < 0 && lb.show < 0.001 && abs(lb.showV) < 0.01 {
                lb.show = 0
                lb.showV = 0
            }
        }
        label = lb
    }

    private func script(_ dt: Double) {
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping && !pressing {
                // The loop starts over from rest: editing ends, apps quit and the icons go home.
                clock = 0
                editing = false
                dragId = -1
                magTarget = 0
                runningOn = Array(repeating: 0, count: count)
                order = Array(0..<count)
            } else if hold <= 0 && pressing {
                hold = M.userHold
            }
            return
        }
        guard looping, stage.width > 0 else { return }
        let W = stage.width
        let before = clock
        var c = before + dt
        if c >= M.loop { c -= M.loop }
        clock = c

        let dockY = stage.baseline - M.icon / 2
        // The sweep: eased from stop to stop.
        if c >= M.sweep[0].0 && c < M.liftAt {
            var x = X.slotCentre(M.sweep[0].1, count, W)
            for k in 1..<M.sweep.count {
                let (t0, s0) = M.sweep[k - 1]
                let (t1, s1) = M.sweep[k]
                if c >= t0 { x = X.slotCentre(s0 + (s1 - s0) * X.smooth((c - t0) / (t1 - t0)), count, W) }
            }
            fingerX = x
            ghostX = x
            ghostY = dockY
        }
        let last = Int(M.sweep[M.sweep.count - 1].1)
        if X.crossed(before, c, M.sweep[0].0) { magTarget = 1 }
        if X.crossed(before, c, M.liftAt) { magTarget = 0 }
        if X.crossed(before, c, M.tapAt - 0.3) {
            ghostX = X.slotCentre(Double(last), count, W)
            ghostY = dockY
        }
        if X.crossed(before, c, M.tapAt) {
            let id = order[last]
            autoId = id
            launch(id, user: false)
        }

        // Editing: a long hold, two carries and Done.
        if X.crossed(before, c, M.holdAt - 0.25) {
            ghostX = X.slotCentre(Double(M.holdSlot), count, W)
            ghostY = dockY
        }
        if X.crossed(before, c, M.holdAt + M.longPress) {
            editing = true
            fingerX = X.slotCentre(Double(M.holdSlot), count, W)
            startY = dockY
            dragId = order[M.holdSlot]
        }
        for (k, carry) in M.carry.enumerated() {
            let (t0, t1, s0, s1) = carry
            if k > 0 && X.crossed(before, c, t0 - 0.2) {
                fingerX = X.slotCentre(Double(s0), count, W)
                startY = dockY
                dragId = order[s0]
            }
            if c >= t0 && c <= t1 + M.dropLag {
                let u = X.smooth((c - t0) / (t1 - t0))
                let x = X.slotCentre(Double(s0) + Double(s1 - s0) * u, count, W)
                let y = dockY - 16 * sin(.pi * u)
                fingerX = x
                ghostX = x
                ghostY = y
                self.carry(to: x, y)
            }
            if X.crossed(before, c, t1 + M.dropLag) { drop(user: false) }
        }
        if X.crossed(before, c, M.doneAt - 0.3) {
            ghostX = W - 52
            ghostY = stage.top + 31
        }
        if X.crossed(before, c, M.doneAt) { editing = false }
        if X.crossed(before, c, M.quitAt) && autoId >= 0 { runningOn[autoId] = 0 }

        // The virtual finger shows while it touches: the sweep, the tap, the hold and carries, Done.
        var alpha = X.touchWindow(c, M.sweep[0].0, M.liftAt)
        alpha = max(alpha, X.touchWindow(c, M.tapAt - 0.08, M.tapAt + 0.08))
        alpha = max(alpha, X.touchWindow(c, M.holdAt, M.carry[0].1 + M.dropLag))
        alpha = max(alpha, X.touchWindow(c, M.carry[1].0 - 0.2, M.carry[1].1 + M.dropLag))
        alpha = max(alpha, X.touchWindow(c, M.doneAt - 0.08, M.doneAt + 0.08))
        ghostAlpha = alpha
        let press = max(X.clamp(1 - abs(c - M.tapAt) / 0.12, 0, 1), X.clamp(1 - abs(c - M.doneAt) / 0.12, 0, 1))
        ghostPress = c > M.holdAt && c < M.holdAt + M.longPress ? X.smooth((c - M.holdAt) / 0.2) : press
    }

    // MARK: The touch

    // One recogniser from touch down, so taps and still long presses reach it too: hover, tap to
    // launch, hold to edit, carry to reorder.
    func touchChanged(_ value: DragGesture.Value) {
        let x = value.location.x
        let y = value.location.y
        if !touchDown {
            touchDown = true
            begin(x: value.startLocation.x, y: value.startLocation.y)
            if value.location == value.startLocation { return }
        }
        fingerX = x
        fingerY = y
        if hypot(x - startX, y - startY) > M.slop { moved = true }
        if dragId >= 0 {
            carry(to: x, y)
        } else if !editing && inDock {
            // Rising well above the dock lets go of it, as a pointer leaving it would.
            magTarget = y < stage.dockTop - M.leave ? 0 : 1
        }
    }

    private func begin(x: Double, y: Double) {
        // An icon the demo was carrying is put down where it is, not handed to this finger.
        if dragId >= 0 { drop(user: false) }
        touched()
        pressing = true
        moved = false
        pressAt = time
        startX = x
        startY = y
        fingerX = x
        fingerY = y
        let dockHalf = layout.dockWidth / 2 + 10
        let onDock = y > stage.dockTop - 24 && abs(x - stage.width / 2) < dockHalf
        inDock = onDock
        if editing {
            if onDock {
                dragId = layout.icon(at: x)
                dragY = 0
            }
        } else if onDock {
            magTarget = 1
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        if !touchDown { begin(x: value.startLocation.x, y: value.startLocation.y) }
        touchDown = false
        let wasPressing = pressing
        pressing = false
        magTarget = 0
        hold = M.userHold
        guard wasPressing else { return }
        let quick = !moved && time - pressAt < M.tapTime
        if dragId >= 0 {
            drop(user: true)
            return
        }
        if editing {
            // A tap anywhere off the icons (or on Done) ends editing.
            if quick && (!inDock || layout.icon(at: startX) < 0) {
                editing = false
                selection.selectionChanged()
            }
            return
        }
        if quick && inDock { launch(layout.icon(at: startX), user: true) }
    }
}
