import SwiftUI
import UIKit

enum RubberChainListMetrics {
    static let background: UInt32 = 0x0e0d12
    static let row: UInt32 = 0x18171d
    static let rowPressed: UInt32 = 0x26242d
    static let chip: UInt32 = 0x24222a
    static let text: UInt32 = 0xf3eee6
    static let muted: UInt32 = 0x8d8896
    static let accent: UInt32 = 0xffb547

    // Layout, in pt.
    static let side: CGFloat = 16
    static let rowH = 92.0
    static let gap = 10.0
    static let extra = 132.0
    static let thumb: CGFloat = 68
    static let radius: CGFloat = 22
    static let headerH: CGFloat = 128
    static let fade: CGFloat = 28

    // The chain: each row is held to the list by its own spring and lags behind it while the list
    // moves, `lag` s of the list's speed for every row it is from the leading edge (up to `lagMax`
    // pt per row, `rankMax` rows), so a fast list opens up like a stretched spring and closes again.
    static let lag = 0.0105
    static let lagMax = 42.0
    static let rankMax = 9.0
    static let linkStiffness = 110.0
    static let linkDamping = 14.7
    static let speedSmooth = 14.0
    // Past either end each row gives a little more than the one before it, `overSpread` more per
    // row from the end that is being pulled.
    static let overSpread = 0.2
    // The list: iOS-like deceleration, resistance past the ends over `rubberSpan` of the screen,
    // and a critically damped spring back.
    static let deceleration = 2.1
    static let edgeStiffness = 180.0
    static let edgeDamping = 26.8
    static let rubberSpan = 0.45
    static let dragSlop = 6.0
    static let tapMaxDuration = 0.5
    // Opening a row: a spring on its height, with a little overshoot.
    static let openStiffness = 210.0
    static let openDamping = 22.0
    static let pressIn = 0.09
    static let pressOut = 0.22

    // autoPlay (seconds).
    static let loop = 9.0
    static let flingUpAt = 0.8
    static let flingDownAt = 3.3
    static let flingSpeed = 3600.0
    static let pressAt = 5.5
    static let openAt = 5.65
    static let closeAt = 7.4
    static let demoRow = 1
    static let userHold = 3.5
    // The first fling waits this long after the screen opens, so the list rests while it arrives.
    static let entryDelay = 1.2
    // Only the rows that fit on screen are built with the screen; the rest follow one per frame
    // after `restAfter` s (or at once on the first touch), so the first frame stays light.
    static let restAfter = 0.36
    // The content settles in once: the header's words fade in and the rows fade up `enterRise` pt.
    static let enterTime = 0.32
    static let enterRise = 8.0
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, x)) }
    static func rubber(_ x: Double, _ span: Double) -> Double { (1 - 1 / ((x * 0.55) / span + 1)) * span }
    static func unrubber(_ r: Double, _ span: Double) -> Double {
        let k = min(0.999, r / span)
        return (span / 0.55) * (1 / (1 - k) - 1)
    }
    static func sign(_ x: Double) -> Double { x > 0 ? 1 : x < 0 ? -1 : 0 }

    /// Reanimated's `interpolateColor`: each channel mixed in gamma 2.2 and rounded.
    static func mix(_ from: UInt32, _ to: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let a = pow(Double((from >> shift) & 0xff) / 255, 2.2)
            let b = pow(Double((to >> shift) & 0xff) / 255, 2.2)
            return (pow(a + (b - a) * t, 1 / 2.2) * 255).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }
}

/// One frame to draw.
struct RubberChainListFrame {
    var ys: [Double]
    var opens: [Double]
    var glows: [Double]
    /// How many rows are built so far.
    var built: Int
    /// How far the content has settled in, 0 … 1 (eased).
    var shown: Double
}

/// The list's place on screen, which the motion needs.
struct RubberChainListStage {
    var top: Double
    var bottom: Double
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    var onOpen: ((Int) -> Void)?
}

/// The list's state, stepped once a frame like the RN frame callback, and the touches that move it.
@MainActor
final class RubberChainListEngine {
    private typealias M = RubberChainListMetrics
    private enum Mode { case drag, free }

    // The list's offset: 0 at the top, negative scrolled down; its speed and a smoothed speed.
    private var scroll = 0.0
    private var velocity = 0.0
    private var smooth = 0.0
    private var mode = Mode.free
    private var dragFrom = 0.0
    private var last = 0.0
    // Each row's lag behind the list and its speed, how open it is, and where it is drawn.
    private var lags: [Double] = []
    private var lagSpeeds: [Double] = []
    private var opens: [Double] = []
    private var openSpeeds: [Double] = []
    private var ys: [Double] = []
    // The press highlight of each row, 0…1.
    private var glows: [Double] = []
    private var openIndex = -1
    private var pressed = -1
    private var byUser = false

    private var clock = 0.0
    private var elapsed = 0.0
    private var built = 0
    private var looping = false
    private var hold = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var stage = RubberChainListStage(top: 0, bottom: 0, count: 0, autoPlay: false, reducedMotion: false)
    // The touch under way.
    private var touchStart: Date?
    private var panning = false

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let soft = UIImpactFeedbackGenerator(style: .soft)

    func step(to date: Date, stage: RubberChainListStage) -> RubberChainListFrame {
        self.stage = stage
        if lags.count != stage.count {
            let n = stage.count
            lags = Array(repeating: 0, count: n)
            lagSpeeds = lags
            opens = lags
            openSpeeds = lags
            glows = lags
            // Every row starts at its resting place under the header.
            ys = (0..<n).map { stage.top + Double($0) * (M.rowH + M.gap) }
            built = min(n, Int(((stage.bottom - stage.top) / (M.rowH + M.gap)).rounded(.up)) + 1)
        }
        if lastAuto != stage.autoPlay {
            lastAuto = stage.autoPlay
            looping = stage.autoPlay
            // The first fling waits for the screen to settle; later loops run from 0.
            clock = -M.entryDelay
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 {
            elapsed += dt
            if elapsed >= M.restAfter && built < stage.count { built += 1 }
        }
        if dt > 0 && stage.bottom > 0 { advance(dt) }
        let shown = M.easeOut.value(at: M.clamp(elapsed / (stage.reducedMotion ? 0.2 : M.enterTime), 0, 1))
        return RubberChainListFrame(ys: ys, opens: opens, glows: glows, built: min(built, stage.count), shown: shown)
    }

    /// The lowest the list may go, with the open rows' extra height.
    private func floorOf() -> Double {
        let open = opens.reduce(0) { $0 + max(0, $1) }
        let content = Double(stage.count) * (M.rowH + M.gap) - M.gap + open * M.extra + 24
        return min(0, stage.bottom - stage.top - content)
    }

    private func bounded(_ raw: Double) -> Double {
        // At least 1 pt, so a stage not yet measured never divides by zero.
        let span = max(1, (stage.bottom - stage.top) * M.rubberSpan)
        let bottom = floorOf()
        if raw > 0 { return M.rubber(raw, span) }
        if raw < bottom { return bottom - M.rubber(bottom - raw, span) }
        return raw
    }

    /// Opens a row, closing the one that was open; or closes it if it is the open one.
    private func toggle(_ index: Int) {
        let next = openIndex == index ? -1 : index
        openIndex = next
        guard byUser else { return }
        if next == index {
            light.impactOccurred()
            stage.onOpen?(index)
        } else {
            soft.impactOccurred()
        }
    }

    private func advance(_ dt: Double) {
        let viewTop = stage.top
        let viewBottom = stage.bottom
        let reduced = stage.reducedMotion
        let count = stage.count

        // The script flings and taps as a hand would.
        if mode != .drag && hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                clock = 0
                if openIndex >= 0 { openIndex = -1 }
                // Home first, so the loop starts from the top.
                if scroll < -1 { velocity = M.flingSpeed }
            }
        } else if looping && mode != .drag {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            func passed(_ at: Double) -> Bool { before < at && t >= at }
            if passed(M.flingUpAt) || passed(M.flingDownAt) {
                byUser = false
                velocity = passed(M.flingUpAt) ? -M.flingSpeed : M.flingSpeed
            }
            if passed(M.pressAt) { pressed = M.demoRow }
            if passed(M.openAt) {
                pressed = -1
                byUser = false
                toggle(M.demoRow)
            }
            if passed(M.closeAt) && openIndex == M.demoRow {
                byUser = false
                toggle(M.demoRow)
            }
        }

        // The list itself.
        let bottom = floorOf()
        if mode == .free {
            let s = scroll
            var v = velocity
            if s > 0.01 || s < bottom - 0.01 {
                let edge = s > 0 ? 0 : bottom
                v += (M.edgeStiffness * (edge - s) - M.edgeDamping * v) * dt
                var next = s + v * dt
                if (s > 0 && next < 0) || (s < bottom && next > bottom) {
                    next = edge
                    v = 0
                }
                scroll = reduced ? edge : next
            } else {
                v *= exp(-M.deceleration * dt)
                if abs(v) < 4 { v = 0 }
                scroll = s + v * dt
            }
            velocity = v
        } else {
            velocity += ((scroll - last) / dt - velocity) * 0.5
        }
        last = scroll
        smooth += (velocity - smooth) * (1 - exp(-dt * M.speedSmooth))

        // The open rows' heights.
        for i in 0..<count {
            let aim: Double = openIndex == i ? 1 : 0
            if reduced {
                opens[i] = M.clamp(opens[i] + M.sign(aim - opens[i]) * dt * 5, min(aim, opens[i]), max(aim, opens[i]))
                openSpeeds[i] = 0
            } else {
                openSpeeds[i] += (M.openStiffness * (aim - opens[i]) - M.openDamping * openSpeeds[i]) * dt
                opens[i] += openSpeeds[i] * dt
            }
        }
        // The highlight comes up fast under the finger and fades out slower.
        for i in 0..<count {
            let on = pressed == i
            let step = dt / (on ? M.pressIn : M.pressOut)
            glows[i] = on ? min(1, glows[i] + step) : max(0, glows[i] - step)
        }

        // Each row: its place in the list, how far past an end the list is, and its lag.
        let s = scroll
        let over = s > 0 ? s : s < bottom ? s - bottom : 0
        let v = reduced ? 0 : smooth
        let pitch = M.rowH + M.gap
        var y = viewTop + s
        for i in 0..<count {
            let at = y + lags[i]
            // Rows count from the edge the list is moving away from: from the top while it moves up.
            let rank = v < 0 ? M.clamp((at - viewTop) / pitch, 0, M.rankMax) : M.clamp((viewBottom - at - M.rowH) / pitch, 0, M.rankMax)
            let aim = -M.sign(v) * min(abs(v) * M.lag, M.lagMax) * rank
            lagSpeeds[i] += (M.linkStiffness * (aim - lags[i]) - M.linkDamping * lagSpeeds[i]) * dt
            lags[i] += lagSpeeds[i] * dt
            if reduced {
                lags[i] = 0
                lagSpeeds[i] = 0
            }
            // Past an end, the rows further from it give more.
            let fromEnd = over > 0 ? M.clamp((at - viewTop) / pitch, 0, M.rankMax) : M.clamp((viewBottom - at - M.rowH) / pitch, 0, M.rankMax)
            let stretch = reduced ? 0 : over * fromEnd * M.overSpread
            ys[i] = y + lags[i] + stretch
            y += pitch + M.extra * max(0, opens[i])
        }
    }

    /// The row under a point, from where the rows are drawn.
    private func rowAt(_ pointY: Double) -> Int {
        for i in 0..<min(ys.count, opens.count) where pointY >= ys[i] && pointY <= ys[i] + M.rowH + M.extra * max(0, opens[i]) {
            return i
        }
        return -1
    }

    // MARK: Gestures

    /// One recogniser stands in for the race of a pan (after 6 pt of travel) and a tap (a touch
    /// lifted within 500 ms that never travelled that far). The touch going down catches the list
    /// and lights the row under it, as the tap's begin does.
    func touchMoved(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
            built = stage.count
            hold = M.userHold
            // A touch catches a moving list, as on iOS.
            velocity = 0
            pressed = rowAt(value.startLocation.y)
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.dragSlop else { return }
            panning = true
            hold = M.userHold
            byUser = true
            pressed = -1
            let s = scroll
            let span = max(1, (stage.bottom - stage.top) * M.rubberSpan)
            let bottom = floorOf()
            dragFrom = s > 0 ? M.unrubber(s, span) : s < bottom ? bottom - M.unrubber(bottom - s, span) : s
            last = s
            mode = .drag
        }
        guard mode == .drag else { return }
        hold = M.userHold
        scroll = bounded(dragFrom + value.translation.height)
    }

    /// The drag ended without `onEnded` (the system took the touch): let go of the list and the
    /// highlight. Deferred a turn so a normal `onEnded` in the same pass always wins.
    func touchCancelled() {
        Task { @MainActor [weak self] in self?.cancelNow() }
    }

    private func cancelNow() {
        guard touchStart != nil else { return }
        touchStart = nil
        panning = false
        pressed = -1
        if mode == .drag { mode = .free }
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            panning = false
            pressed = -1
        }
        if panning {
            guard mode == .drag else { return }
            velocity = stage.reducedMotion ? 0 : value.velocity.height
            mode = .free
            return
        }
        guard let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration else { return }
        let index = rowAt(value.location.y)
        guard index >= 0 else { return }
        byUser = true
        toggle(index)
    }
}

/// Downloads a photo once and keeps it decoded.
enum RubberChainListPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func cached(_ address: String) -> UIImage? { cache.object(forKey: address as NSString) }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cached(address) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}
