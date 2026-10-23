import SwiftUI
import UIKit

struct HoldDragResizeSnapshot {
    var starts: [Double]
    var ends: [Double]
    var tops: [Double]
    var lengths: [Double]
    var active = -1
    var shown = -1
    var edge = 1
    var grabEdge = 0.0
    var target = 0.0
    var over = 0.0
    var lift = 0.0
    var press = 0.0
    var pressCard = -1
    var lock: [Double]
    var pushed: [Double]
    var oldTimes: [Double]
    var focus = 60.0
    var open = 195.0
    var ghost = CGPoint.zero
    var ghostOpacity = 0.0
    var ghostPress = 0.0
    var reduced = false

    static func resting(reduced: Bool = false) -> HoldDragResizeSnapshot {
        let plan = HoldDragResizeData.plan
        return .init(
            starts: plan.map(\.start),
            ends: plan.map(\.end),
            tops: plan.map(\.start),
            lengths: plan.map { $0.end - $0.start },
            lock: Array(repeating: 0, count: plan.count),
            pushed: Array(repeating: 0, count: plan.count),
            oldTimes: plan.map(\.start),
            reduced: reduced
        )
    }
}

@MainActor
final class HoldDragResizeInteraction: ObservableObject {
    @Published private(set) var revision = 0

    private var loopOrigin = Date()
    private var resumeAt: Date?
    private var touchDate: Date?
    private var releaseDate: Date?
    private var current = HoldDragResizeSnapshot.resting()
    private var touchCard = -1
    private var startY = 0.0
    private var startEdge = 0.0
    private var lastQuarter = 0.0
    private var wallHaptic = false
    private var configured = false

    var needsFrames: Bool {
        touchDate != nil || releaseDate != nil
    }

    func configure(layout: HoldDragResizeLayout, autoPlay: Bool, reduceMotion: Bool) {
        if !configured {
            configured = true
            loopOrigin = Date()
            current = .resting(reduced: reduceMotion)
        }
        current.reduced = reduceMotion
        if reduceMotion {
            resumeAt = nil
            current = .resting(reduced: true)
        } else if autoPlay, resumeAt == nil, touchDate == nil {
            loopOrigin = Date()
        }
        revision &+= 1
    }

    func snapshot(at date: Date, layout: HoldDragResizeLayout, autoPlay: Bool, reduceMotion: Bool) -> HoldDragResizeSnapshot {
        if autoPlay, !reduceMotion, touchDate == nil {
            if let resumeAt {
                if date >= resumeAt {
                    return HoldDragResizeScript.snapshot(
                        time: date.timeIntervalSince(resumeAt).truncatingRemainder(dividingBy: HoldDragResize.loop),
                        layout: layout
                    )
                }
            } else {
                return HoldDragResizeScript.snapshot(
                    time: date.timeIntervalSince(loopOrigin).truncatingRemainder(dividingBy: HoldDragResize.loop),
                    layout: layout
                )
            }
        }

        var value = current
        value.reduced = reduceMotion
        if touchDate != nil, value.active < 0, value.pressCard >= 0 {
            let progress = min(max(date.timeIntervalSince(touchDate!) / HoldDragResizeMetrics.holdDuration, 0), 1)
            value.press = reduceMotion ? 0 : progress
        }
        if let releaseDate, value.active < 0 {
            let duration = reduceMotion ? 0.2 : 0.3
            let progress = min(max(date.timeIntervalSince(releaseDate) / duration, 0), 1)
            value.lift = 1 - HoldDragResizeMath.easeOut(progress)
            if progress >= 1 {
                value.shown = -1
                value.lift = 0
            }
        }
        return value
    }

    func touchBegan(at point: CGPoint, layout: HoldDragResizeLayout, autoPlay: Bool, reduceMotion: Bool) {
        let now = Date()
        current = snapshot(at: now, layout: layout, autoPlay: autoPlay, reduceMotion: reduceMotion)
        current.starts = current.tops
        current.ends = zip(current.tops, current.lengths).map(+)
        current.active = -1
        current.shown = -1
        current.lift = 0
        current.press = 0
        current.reduced = reduceMotion
        releaseDate = nil
        resumeAt = autoPlay ? now.addingTimeInterval(HoldDragResizeMetrics.resumeDuration) : nil
        touchDate = now
        touchCard = card(at: point, layout: layout, snapshot: current)
        current.pressCard = touchCard >= 0 && HoldDragResizeData.plan[touchCard].kind != .meeting ? touchCard : -1
        current.shown = current.pressCard
        current.ghostOpacity = 0
        revision &+= 1
    }

    func holdBegan(at point: CGPoint, layout: HoldDragResizeLayout, reduceMotion: Bool) {
        guard touchCard >= 0 else { return }
        let item = HoldDragResizeData.plan[touchCard]
        if item.kind == .meeting {
            current.lock[touchCard] = 1
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            revision &+= 1
            return
        }
        current.active = touchCard
        current.shown = touchCard
        current.pressCard = -1
        current.press = 1
        current.edge = Double(point.y) > Double(layout.y(current.tops[touchCard] + current.lengths[touchCard] / 2)) ? 1 : 0
        current.grabEdge = current.edge == 1 ? current.ends[touchCard] : current.starts[touchCard]
        current.target = current.grabEdge
        current.lift = reduceMotion ? 0 : 1
        startY = Double(point.y)
        startEdge = current.grabEdge
        lastQuarter = current.target
        wallHaptic = false
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        revision &+= 1
    }

    func holdMoved(to point: CGPoint, layout: HoldDragResizeLayout, reduceMotion: Bool) {
        let index = current.active
        guard index >= 0 else { return }
        let raw = startEdge + (Double(point.y) - startY) / Double(layout.ppm)
        let limits = HoldDragResizeMath.edgeLimits(index: index, edge: current.edge, snapshot: current)
        let snapped = HoldDragResizeMath.clamp((raw / 15).rounded() * 15, limits.0, limits.1)
        current.target = snapped
        current.over = 0
        if !reduceMotion {
            if current.edge == 1, raw > limits.1 {
                current.over = HoldDragResizeMath.rubber(raw - limits.1)
            } else if current.edge == 1, raw < limits.0 {
                current.over = -HoldDragResizeMath.rubber(limits.0 - raw) * 0.5
            } else if current.edge == 0, raw > limits.1 {
                current.over = HoldDragResizeMath.rubber(raw - limits.1) * 0.5
            }
        }
        let atWall = current.edge == 1 && raw > limits.1 + 1
        current.lock[4] = atWall ? 1 : 0
        if atWall, !wallHaptic {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            wallHaptic = true
        } else if snapped != lastQuarter, !atWall {
            UISelectionFeedbackGenerator().selectionChanged()
        }
        if !atWall { wallHaptic = false }
        lastQuarter = snapped
        HoldDragResizeMath.applyLayout(to: &current)
        revision &+= 1
    }

    func touchEnded(reduceMotion: Bool, autoPlay: Bool) {
        let now = Date()
        if current.active >= 0 {
            let index = current.active
            if current.edge == 1 { current.ends[index] = current.target }
            else { current.starts[index] = current.target }
            current.over = 0
            current.active = -1
            current.pressCard = -1
            current.lock = Array(repeating: 0, count: current.lock.count)
            HoldDragResizeMath.applyLayout(to: &current)
            releaseDate = now
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } else {
            current.pressCard = -1
            current.press = 0
            current.lock = Array(repeating: 0, count: current.lock.count)
            current.shown = -1
        }
        touchDate = nil
        touchCard = -1
        if autoPlay { resumeAt = now.addingTimeInterval(HoldDragResizeMetrics.resumeDuration) }
        revision &+= 1
    }

    private func card(at point: CGPoint, layout: HoldDragResizeLayout, snapshot: HoldDragResizeSnapshot) -> Int {
        guard point.x >= layout.cardX - 4 else { return -1 }
        for index in snapshot.tops.indices {
            if point.y >= layout.y(snapshot.tops[index]), point.y <= layout.y(snapshot.tops[index] + snapshot.lengths[index]) {
                return index
            }
        }
        return -1
    }
}

enum HoldDragResizeScript {
    static func snapshot(time t: Double, layout: HoldDragResizeLayout) -> HoldDragResizeSnapshot {
        var value = HoldDragResizeSnapshot.resting()
        var memoEnd = 660.0
        var rawEnd = 660.0
        var active = false

        if t >= 0.8, t < 3.2 {
            active = true
            rawEnd = 660 + path(
                [[0.9, 0], [2.1, 60], [2.5, 92], [2.75, 92], [3.0, 62]],
                at: t
            )
            memoEnd = min((rawEnd / 15).rounded() * 15, 720)
            value.over = rawEnd > 720 ? HoldDragResizeMath.rubber(rawEnd - 720) : 0
        } else if t >= 3.2, t < 7.1 {
            memoEnd = 720
        }

        if t >= 5.3, t < 7.1 {
            active = true
            rawEnd = 720 + path([[5.4, 0], [6.8, -60]], at: t)
            memoEnd = HoldDragResizeMath.clamp((rawEnd / 15).rounded() * 15, 615, 720)
            value.over = 0
        } else if t >= 7.1 {
            memoEnd = 660
        }

        value.ends[HoldDragResizeData.memo] = memoEnd
        value.active = active ? HoldDragResizeData.memo : -1
        value.shown = active ? HoldDragResizeData.memo : -1
        value.edge = 1
        value.grabEdge = t >= 5 ? 720 : 660
        value.target = memoEnd
        value.pressCard = (t >= 0.5 && t < 0.8) || (t >= 5 && t < 5.3) ? HoldDragResizeData.memo : -1
        value.press = value.pressCard >= 0 ? min((t - (t < 4 ? 0.5 : 5.0)) / 0.3, 1) : 0
        value.lift = active ? 1 : releaseLift(t)
        if value.lift > 0, value.shown < 0 { value.shown = HoldDragResizeData.memo }
        value.lock[4] = value.over > 0.5 ? 1 : 0
        HoldDragResizeMath.applyLayout(to: &value)

        let x = layout.cardX + layout.cardW * 0.62
        if t >= 0.5, t < 3.2 {
            value.ghost = CGPoint(x: x, y: layout.y(645 + path([[0.9, 0], [2.1, 60], [2.5, 92], [2.75, 92], [3.0, 62]], at: t)))
            value.ghostOpacity = fingerOpacity(t, from: 0.5, to: 3.2)
            value.ghostPress = t < 0.8 ? min((t - 0.5) / 0.3, 1) : 1
        } else if t >= 5, t < 7.1 {
            value.ghost = CGPoint(x: x, y: layout.y(705 + path([[5.4, 0], [6.8, -60]], at: t)))
            value.ghostOpacity = fingerOpacity(t, from: 5, to: 7.1)
            value.ghostPress = t < 5.3 ? min((t - 5) / 0.3, 1) : 1
        }
        return value
    }

    private static func path(_ keyframes: [[Double]], at time: Double) -> Double {
        guard let first = keyframes.first, let last = keyframes.last else { return 0 }
        if time <= first[0] { return first[1] }
        for index in 1..<keyframes.count where time <= keyframes[index][0] {
            let a = keyframes[index - 1]
            let b = keyframes[index]
            let u = HoldDragResizeMath.easeInOut((time - a[0]) / (b[0] - a[0]))
            return a[1] + (b[1] - a[1]) * u
        }
        return last[1]
    }

    private static func releaseLift(_ time: Double) -> Double {
        for release in [3.2, 7.1] where time >= release && time < release + 0.3 {
            return 1 - HoldDragResizeMath.easeOut((time - release) / 0.3)
        }
        return 0
    }

    private static func fingerOpacity(_ time: Double, from: Double, to: Double) -> Double {
        min((time - from) / 0.15, (to - time) / 0.12, 1)
    }
}

enum HoldDragResizeMath {
    static func clamp(_ value: Double, _ lower: Double, _ upper: Double) -> Double {
        min(max(value, lower), upper)
    }

    static func rubber(_ value: Double) -> Double {
        15 * (1 - exp((-value * 0.35) / 15))
    }

    static func easeOut(_ value: Double) -> Double {
        bezier(0.23, 1, 0.32, 1, value)
    }

    static func easeInOut(_ value: Double) -> Double {
        bezier(0.77, 0, 0.175, 1, value)
    }

    static func edgeLimits(index: Int, edge: Int, snapshot: HoldDragResizeSnapshot) -> (Double, Double) {
        if edge == 1 {
            var tasks = 0.0
            var upper = HoldDragResizeData.dayEnd
            for item in HoldDragResizeData.plan[(index + 1)...] {
                if item.kind == .meeting {
                    upper = item.start
                    break
                }
                tasks += item.end - item.start
            }
            return (snapshot.starts[index] + 15, upper - tasks)
        }
        let lower = index > 0 ? snapshot.tops[index - 1] + snapshot.lengths[index - 1] : HoldDragResizeData.dayStart
        return (lower, snapshot.ends[index] - 15)
    }

    static func applyLayout(to value: inout HoldDragResizeSnapshot) {
        var floor = HoldDragResizeData.dayStart
        for index in HoldDragResizeData.plan.indices {
            var start = value.starts[index]
            var end = value.ends[index]
            if index == value.active {
                if value.edge == 1 { end = value.target + value.over }
                else { start = value.target + value.over }
            }
            if HoldDragResizeData.plan[index].kind == .meeting || index == value.active {
                value.tops[index] = start
            } else {
                value.tops[index] = max(start, floor)
            }
            value.lengths[index] = end - start
            floor = value.tops[index] + value.lengths[index]
        }

        if value.active >= 0, value.edge == 1, value.over > 0 {
            let wall = 4
            let bottom = value.tops[value.active] + value.lengths[value.active]
            let available = HoldDragResizeData.plan[wall].start - bottom
            let natural = value.lengths[(value.active + 1)..<wall].reduce(0, +)
            let scale = natural > 0 ? max(available / natural, 0.3) : 1
            var y = bottom
            for index in (value.active + 1)..<wall {
                value.tops[index] = y
                value.lengths[index] *= scale
                y += value.lengths[index]
            }
        }

        value.pushed = HoldDragResizeData.plan.indices.map { index in
            HoldDragResizeData.plan[index].kind == .task && value.tops[index] > value.starts[index] + 0.5 ? 1 : 0
        }
        value.focus = zip(HoldDragResizeData.plan, value.ends).enumerated().reduce(0) { total, pair in
            let (index, entry) = pair
            return entry.0.kind == .focus ? total + entry.1 - value.starts[index] : total
        }
        let occupied = zip(value.starts, value.ends).reduce(0) { $0 + $1.1 - $1.0 }
        value.open = HoldDragResizeData.dayEnd - HoldDragResizeData.dayStart - occupied
    }

    private static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ input: Double) -> Double {
        let value = clamp(input, 0, 1)
        let cx = 3 * x1
        let bx = 3 * (x2 - x1) - cx
        let ax = 1 - cx - bx
        let cy = 3 * y1
        let by = 3 * (y2 - y1) - cy
        let ay = 1 - cy - by
        var t = value
        for _ in 0..<7 {
            let x = ((ax * t + bx) * t + cx) * t - value
            let derivative = (3 * ax * t + 2 * bx) * t + cx
            if abs(x) < 0.00001 || abs(derivative) < 0.000001 { break }
            t -= x / derivative
        }
        return ((ay * t + by) * t + cy) * t
    }
}

