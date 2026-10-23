import SwiftUI
import UIKit

enum OrigamiFoldListMetrics {
    // Rows, in pt.
    static let side: CGFloat = 18
    static let rowHeight = 78.0
    static let rowGap = 10.0
    static let thumb: CGFloat = 58
    // The fold under a row: three panels of `panelHeight` pt, and a `foldGap` between the row and the paper.
    static let panels = 3
    static let panelHeight = 76.0
    static let foldGap = 6.0
    static let foldHeight = Double(panels) * panelHeight
    // Each panel starts to open `stagger` (of the whole opening) after the one above it, so the paper
    // unfolds one panel after another. Panels hinge on their top edges, seen through `perspective`.
    static let stagger = 0.24
    static let perspective = 700.0
    // How dark a fold's shadow is while it is shut.
    static let shade = 0.78
    // Past fully open, the paper stretches with a quarter of the finger's travel.
    static let rubber = 0.25

    // The spring that opens and closes a fold, in fold-lengths: stiffness, damping (mass 1).
    static let stiffness = 130.0
    static let damping = 20.0
    // A release opens the fold when the projected amount (now + velocity × `throwTime`) passes half.
    static let throwTime = 0.2
    // Reduced motion: the fold eases to its target at this rate per second, flat and fading.
    static let reducedRate = 9.0
    // The pan starts after 8 pt of vertical travel and gives up after 24 pt of sideways travel.
    static let panSlop: CGFloat = 8
    static let panFail: CGFloat = 24
    static let tapMaxDuration = 0.3

    // autoPlay (seconds): unfold a row, unfold another (the first folds back), a third, fold all.
    static let loop = 11.0
    static let cues: [(at: Double, row: Int)] = [(0.8, 1), (3.4, 3), (6.0, 0), (8.6, -1)]
    static let userHold = 4.0
    // Entering the screen: the script waits `entryDelay` s before its first cue, the header fades in
    // and the rows fade up `listRise` pt over `listIn` s once the header has been measured, and the
    // folded paper under the rows is built one row per frame from `foldsAfter` s, after the screen
    // has settled, so the first frames only carry what is visible.
    static let entryDelay = 1.2
    static let listIn = 0.32
    static let listRise = 10.0
    static let foldsAfter = 0.38
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    // The header is gone once the list has slid up this far, in pt.
    static let headerFade = 90.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    /// How far panel `k` is open, 0 … 1, for a fold opened by `open`.
    static func panelOpen(_ open: Double, _ k: Int) -> Double {
        clamp01((open - Double(k) * stagger) / (1 - Double(panels - 1) * stagger))
    }

    /// The height a panel takes on screen while it is `p` open.
    static func shown(_ p: Double, reduced: Bool) -> Double {
        panelHeight * (reduced ? p : sin(p * .pi / 2))
    }

    /// The height a fold takes on screen: each panel shows its height × sin of how far it has turned.
    static func foldHeight(_ open: Double, reduced: Bool) -> Double {
        var h = 0.0
        for k in 0..<panels { h += shown(panelOpen(open, k), reduced: reduced) }
        let gap = foldGap * clamp01(open * 4)
        return h + gap + max(0, open - 1) * foldHeight
    }

    /// Where panel `k` hangs under its row, how far it is open and how far it is turned (degrees).
    static func panel(_ k: Int, open: Double, reduced: Bool) -> (top: Double, open: Double, angle: Double) {
        var top = foldGap * clamp01(open * 4)
        for j in 0..<k { top += shown(panelOpen(open, j), reduced: reduced) }
        // Stretched past open, the panels part a little.
        top += max(0, open - 1) * foldHeight * (Double(k) / Double(panels))
        let p = panelOpen(open, k)
        // The paper is folded like an accordion: every other panel leans the other way.
        let angle = reduced ? 0 : (k % 2 == 0 ? -1.0 : 1.0) * 90 * (1 - p)
        return (top, p, angle)
    }

    /// A panel `width` pt wide turned `angle` degrees about its top edge, seen through its own
    /// vanishing point at the middle of that edge, then moved down to `top`.
    static func hinge(width: CGFloat, top: Double, angle: Double) -> ProjectionTransform {
        let w = Double(width)
        var t = CATransform3DMakeTranslation(-w / 2, 0, 0)
        t = CATransform3DConcat(t, CATransform3DMakeRotation(angle * .pi / 180, 1, 0, 0))
        var p = CATransform3DIdentity
        p.m34 = -1 / perspective
        t = CATransform3DConcat(t, p)
        t = CATransform3DConcat(t, CATransform3DMakeTranslation(w / 2, top, 0))
        return ProjectionTransform(t)
    }
}

struct OrigamiFoldListLayout {
    var tops: [Double]
    var folds: [Double]
    var shift: Double
}

struct OrigamiFoldListSettings {
    var count: Int
    var autoPlay: Bool
    var reduced: Bool
    /// Where the first row sits, and the lowest an open fold may reach.
    var top: Double
    var bottom: Double
}

/// Every row's fold, stepped once a frame like the RN frame callback, and the gestures that move them.
@MainActor
final class OrigamiFoldListEngine {
    private typealias M = OrigamiFoldListMetrics

    // Per row: how far its fold is open (1 = open), its speed, and where it is going.
    private(set) var opens: [Double] = []
    private var speeds: [Double] = []
    private var targets: [Double] = []
    private var dragRow = -1
    private var dragFrom = 0.0
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastAuto: Bool?
    private(set) var elapsed = 0.0
    /// How many rows have their folded paper built (see `foldsAfter`).
    private(set) var built = 0
    /// When the list was first placed (the header measured), for its fade-up.
    private var listInAt: Double?
    private var lastDate: Date?
    private var settings = OrigamiFoldListSettings(count: 0, autoPlay: false, reduced: false, top: 0, bottom: 0)
    // The touch under way.
    private var touchStart: Date?
    private var touchMode = 0 // 0 undecided, 1 pan, 2 failed

    var onOpen: ((Int) -> Void)?
    private let light = UIImpactFeedbackGenerator(style: .light)

    func step(to date: Date, settings: OrigamiFoldListSettings) {
        self.settings = settings
        if opens.count != settings.count {
            opens = Array(repeating: 0, count: settings.count)
            speeds = opens
            targets = opens
        }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            // The first cue waits for the screen to settle; later loops run from 0.
            clock = -M.entryDelay
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.048)
        guard dt > 0 else { return }
        elapsed += dt
        if listInAt == nil && settings.top > 0 { listInAt = elapsed }
        if elapsed >= M.foldsAfter && built < settings.count { built += 1 }
        if built > settings.count { built = settings.count }
        if dragRow < 0 {
            if hold > 0 {
                hold -= dt
                if hold <= 0 { clock = 0 }
            } else if looping {
                let before = clock
                let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
                clock = t
                for cue in M.cues {
                    let at = cue.at
                    if (before < at && t >= at) || (t < before && (at > before || at <= t)) { openOnly(cue.row) }
                }
            }
        }
        for k in opens.indices where k != dragRow {
            if abs(targets[k] - opens[k]) < 0.0005 && abs(speeds[k]) < 0.001 {
                opens[k] = targets[k]
                speeds[k] = 0
                continue
            }
            if settings.reduced {
                opens[k] += (targets[k] - opens[k]) * (1 - exp(-M.reducedRate * dt))
                speeds[k] = 0
            } else {
                speeds[k] += (M.stiffness * (targets[k] - opens[k]) - M.damping * speeds[k]) * dt
                opens[k] += speeds[k] * dt
            }
        }
    }

    /// How far the list has faded up, 0 … 1 (eased); 0 until the header has been measured.
    func shown(reduced: Bool) -> Double {
        guard let at = listInAt else { return 0 }
        let k = M.clamp01((elapsed - at) / (reduced ? 0.2 : M.listIn))
        return M.easeOut.value(at: k)
    }

    /// Where each row sits: below the rows above it and their folds. When an open fold would run
    /// off the bottom, the whole list slides up to keep it in view.
    func layout() -> OrigamiFoldListLayout {
        var tops: [Double] = []
        var folds: [Double] = []
        var y = settings.top
        var shift = 0.0
        for k in opens.indices {
            tops.append(y)
            let f = M.foldHeight(opens[k], reduced: settings.reduced)
            folds.append(f)
            if opens[k] > 0.001 { shift = max(shift, y + M.rowHeight + f - settings.bottom) }
            y += M.rowHeight + f + M.rowGap
        }
        return OrigamiFoldListLayout(tops: tops, folds: folds, shift: max(0, shift))
    }

    /// Sends one row's fold open (or `-1` for none) and every other one shut.
    private func openOnly(_ row: Int) {
        targets = targets.indices.map { $0 == row ? 1 : 0 }
        let callback = onOpen
        DispatchQueue.main.async { callback?(row) }
    }

    /// The row under a point of the screen, folds included, or -1.
    private func rowAt(_ y: Double) -> Int {
        let l = layout()
        for k in opens.indices where l.tops.indices.contains(k) && l.folds.indices.contains(k) {
            let t = l.tops[k] - l.shift
            if y >= t - M.rowGap / 2 && y <= t + M.rowHeight + l.folds[k] + M.rowGap / 2 { return k }
        }
        return -1
    }

    // MARK: Gestures

    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            touchMode = 0
        }
        let dx = value.translation.width
        let dy = value.translation.height
        if touchMode == 0 {
            if abs(dx) >= M.panFail {
                touchMode = 2
            } else if abs(dy) >= M.panSlop {
                touchMode = 1
                panStart(y: value.startLocation.y)
            }
        }
        if touchMode == 1 { panUpdate(translationY: dy) }
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            touchMode = 0
        }
        if touchMode == 1 {
            panEnd(velocityY: value.velocity.height)
        } else if touchMode == 0, let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration {
            tap(y: value.location.y)
        }
    }

    /// A touch the system took away (a DragGesture then never ends): the row let go of springs back
    /// to where it was going, like the RN pan's onFinalize. Nothing happens when the touch already ended.
    func touchCancelled() {
        guard touchStart != nil || dragRow >= 0 else { return }
        dragRow = -1
        touchStart = nil
        touchMode = 0
    }

    private func panStart(y: Double) {
        let row = rowAt(y)
        guard opens.indices.contains(row) else { return }
        built = settings.count
        hold = M.userHold
        dragRow = row
        dragFrom = opens[row]
        // Only one fold open at a time: the others start folding away.
        targets = targets.indices.map { $0 == row ? targets[$0] : 0 }
    }

    private func panUpdate(translationY: Double) {
        guard opens.indices.contains(dragRow) else { return }
        let raw = dragFrom + translationY / M.foldHeight
        opens[dragRow] = raw > 1 ? 1 + (raw - 1) * M.rubber : max(0, raw)
    }

    private func panEnd(velocityY: Double) {
        let row = dragRow
        dragRow = -1
        guard row >= 0, opens.indices.contains(row) else { return }
        // A velocity that could not be measured counts as none.
        let velocity = velocityY.isFinite ? velocityY / M.foldHeight : 0
        let open = opens[row] + velocity * M.throwTime > 0.5
        speeds[row] = settings.reduced ? 0 : velocity
        if open != (dragFrom > 0.5) { light.impactOccurred() }
        if open {
            openOnly(row)
        } else {
            targets[row] = 0
            let callback = onOpen
            DispatchQueue.main.async { callback?(-1) }
        }
    }

    private func tap(y: Double) {
        let row = rowAt(y)
        guard targets.indices.contains(row) else { return }
        hold = M.userHold
        built = settings.count
        light.impactOccurred()
        openOnly(targets[row] > 0.5 ? -1 : row)
    }
}
