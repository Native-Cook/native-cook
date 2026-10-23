import SwiftUI
import UIKit

enum EditorialMenuMorphMetrics {
    // The menu's timeline. Opening plays `open` from 0 to 1 over `openTime`, linearly; every part
    // takes its own slice of it and eases within that slice, so closing (1 to 0 over `closeTime`)
    // plays the same choreography backwards: the last letter drops first and the curtain leaves last.
    static let openTime = 1.15
    static let closeTime = 0.9
    static let reducedTime = 0.26
    static let panel = (0.0, 0.42)
    static let burger = (0.0, 0.36)
    static let wordsFrom = 0.3
    static let wordStagger = 0.07
    static let wordSpan = 0.4
    static let letterStagger = 0.012
    static let letterSpan = 0.3
    static let footer = (0.68, 1.0)

    // The curtain: its leading edge bows `bulge` pt ahead of its sides at mid-travel, flat when it
    // starts and when it lands. The page under it sinks `sink` pt, shrinks by `sinkScale` and
    // darkens to `sinkShade` black.
    static let bulge = 110.0
    static let sink = 34.0
    static let sinkScale = 0.04
    static let sinkShade = 0.28

    // The words: `wordSize` pt Iowan Old Style, one per `row` pt, each in a box that clips at its
    // own baseline so its letters rise out of nothing. The list starts `listTop` below the safe area.
    static let wordSize = 52.0
    static let row = 80.0
    static let clip = 66.0
    static let listTop = 112.0
    static let listLeft = 22.0
    static let numberWidth = 30.0
    static let focusShift = 14.0
    static let dimmed = 0.28
    static let ruleY = clip + 3

    // The floating preview: a round photo `previewW` pt across in a `border` pt ring of paper,
    // sitting `previewDX` right of and `previewDY` above the finger, chasing it at `lag` (1/s) and
    // leaning with its horizontal speed up to `leanMax`°. A new word wipes its photo up from the
    // bottom over `wipeTime` while it settles from `wipeZoom`, with a band of the word's colour on
    // the wipe's edge.
    static let previewW = 168.0
    static let previewH = previewW
    static let previewDX = 100.0
    static let previewDY = -64.0
    static let border = 3.0
    static let lag = 7.0
    static let leanPerSpeed = 0.012
    static let leanMax = 9.0
    static let wipeTime = 0.46
    static let wipeZoom = 1.14
    static let band = 5.0
    static let bgTime = 0.42

    // Item to page: the card flies to the hero's frame on a spring (stiffness `flightK`, damping
    // ratio `flightZeta`, mass 1), integrated per frame; its corners open from a full circle to the
    // hero's square ones on the same progress, and its ring thins away.
    static let flightK = 180.0
    static let flightZeta = 0.9
    static let flightC = 2 * flightZeta * flightK.squareRoot()

    // autoPlay (seconds): rest, tap the burger, glide down the words and back up, lift on
    // Architecture, rest on its page, tap the burger, glide up to Exhibitions, lift, rest.
    static let loop = 16.0
    static let taps = [1.0, 10.0]
    static let glides: [[(Double, Double)]] = [
        [(2.4, 0), (2.9, 0), (3.5, 1), (4.1, 2), (4.7, 3), (5.3, 4), (6.2, 2), (6.8, 2)],
        [(11.4, 2), (11.9, 2), (12.5, 1), (13.1, 0), (13.6, 0)],
    ]
    static let userHold = 4.0
    /// After a touch's pause the loop restarts this long before its own start, at rest.
    static let resumeRest = 1.0
    static let tapMaxDuration = 0.5
}

/// The easing curves the block uses, as plain functions of progress.
enum EditorialMenuMorphEase {
    private static let strong = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    private static let panelCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))
    private static let wipeCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))

    static func out(_ t: Double) -> Double { strong.value(at: min(max(t, 0), 1)) }
    static func inOut(_ t: Double) -> Double { panelCurve.value(at: min(max(t, 0), 1)) }
    static func wipe(_ t: Double) -> Double { wipeCurve.value(at: min(max(t, 0), 1)) }
    static func linear(_ t: Double) -> Double { t }
    /// Reanimated's default timing curve, `Easing.inOut(Easing.quad)`.
    static func quad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }

    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, x)) }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// 0…1 through `slice` of a timeline.
    static func part(_ t: Double, _ slice: (Double, Double)) -> Double {
        clamp((t - slice.0) / (slice.1 - slice.0))
    }

    static func wordReveal(_ t: Double, _ index: Int) -> Double {
        typealias M = EditorialMenuMorphMetrics
        return out((t - M.wordsFrom - Double(index) * M.wordStagger) / M.wordSpan)
    }

    static func letterReveal(_ t: Double, _ index: Int, _ letter: Int) -> Double {
        typealias M = EditorialMenuMorphMetrics
        return out((t - M.wordsFrom - Double(index) * M.wordStagger - Double(letter) * M.letterStagger) / M.letterSpan)
    }
}

/// A number timed from where it is to a target on a curve, like `withTiming` (with an optional delay).
struct EditorialMenuMorphValue {
    private(set) var value: Double
    private var from = 0.0
    private var to = 0.0
    private var time = 0.0
    private var delay = 0.0
    private var duration = 0.0
    private var curve: (Double) -> Double = EditorialMenuMorphEase.linear
    private var moving = false

    init(_ value: Double) {
        self.value = value
    }

    mutating func set(_ next: Double) {
        value = next
        moving = false
    }

    mutating func animate(to target: Double, duration: Double, delay: Double = 0, curve: @escaping (Double) -> Double = EditorialMenuMorphEase.quad) {
        from = value
        to = target
        time = 0
        self.delay = delay
        self.duration = duration
        self.curve = curve
        moving = true
    }

    mutating func step(_ dt: Double) {
        guard moving else { return }
        time += dt
        let t = time - delay
        guard t >= 0 else { return }
        let p = duration > 0 ? min(1, t / duration) : 1
        value = from + (to - from) * curve(p)
        if p >= 1 { moving = false }
    }
}

/// An sRGB colour as 0…1 channels, mixed like Reanimated's `interpolateColor`: in RGB raised to
/// the power 2.2, then brought back.
struct EditorialMenuMorphRGB: Equatable {
    var r: Double
    var g: Double
    var b: Double

    init(_ hex: UInt32) {
        r = Double((hex >> 16) & 0xff) / 255
        g = Double((hex >> 8) & 0xff) / 255
        b = Double(hex & 0xff) / 255
    }

    init(r: Double, g: Double, b: Double) {
        self.r = r
        self.g = g
        self.b = b
    }

    func mix(_ other: Self, _ t: Double) -> Self {
        func ch(_ a: Double, _ b: Double) -> Double {
            let x = pow(a, 2.2) + (pow(b, 2.2) - pow(a, 2.2)) * t
            return pow(max(0, x), 1 / 2.2)
        }
        return Self(r: ch(r, other.r), g: ch(g, other.g), b: ch(b, other.b))
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b) }
}

/// Where the block is drawn: its size and the safe area it keeps clear of.
struct EditorialMenuMorphStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double

    var listTop: Double { top + EditorialMenuMorphMetrics.listTop }
    var layout: EditorialMenuMorphLayout { EditorialMenuMorphLayout(width: width, height: height, top: top, bottom: bottom) }
}

/// The card as drawn: centre, size, turn (degrees), corner radius, opacity, and how far its
/// empty tone has gone from the menu's to the page's placeholder.
struct EditorialMenuMorphCard {
    var x = 0.0
    var y = 0.0
    var w = EditorialMenuMorphMetrics.previewW
    var h = EditorialMenuMorphMetrics.previewH
    var rot = 0.0
    var radius = EditorialMenuMorphMetrics.previewW / 2
    var alpha = 0.0
    var tone = 0.0
}

struct EditorialMenuMorphFrame {
    var open: Double
    var dir: Double
    var focus: [Double]
    var shift: [Double]
    var italic: [Double]
    var rule: [Double]
    var current: Int
    var previous: Int
    var wipe: Double
    var card: EditorialMenuMorphCard
    var background: EditorialMenuMorphRGB
    var section: Int
    var previousSection: Int
    var pageIn: Double
    var heroShown: Double
    var ghostX: Double
    var ghostY: Double
    var ghostAlpha: Double
    var ghostPress: Double
}

/// The menu's state, stepped once a frame like the RN frame callback, and the touch that drives it.
@MainActor
final class EditorialMenuMorphEngine {
    private typealias M = EditorialMenuMorphMetrics
    private typealias E = EditorialMenuMorphEase
    private typealias RGB = EditorialMenuMorphRGB

    private var count = 0
    private var stage = EditorialMenuMorphStage(width: 0, height: 0, top: 0, bottom: 0)
    private var open = EditorialMenuMorphValue(0)
    private var isOpen = false
    private var dir = 1.0
    private var hover = -1
    private var focus: [EditorialMenuMorphValue] = []
    private var shift: [EditorialMenuMorphValue] = []
    private var italic: [EditorialMenuMorphValue] = []
    private var rule: [EditorialMenuMorphValue] = []
    private var current = 0
    private var previous = -1
    private var wipe = EditorialMenuMorphValue(1)
    private var shown = EditorialMenuMorphValue(0)
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var px = 0.0
    private var py = 0.0
    private var lean = 0.0
    private var bgFrom = RGB(EditorialMenuMorphStyle.baseTint)
    private var bgTo = RGB(EditorialMenuMorphStyle.baseTint)
    private var bgT = EditorialMenuMorphValue(1)
    private var tints: [RGB] = []
    private var card = EditorialMenuMorphCard()
    // Item to page: false at rest or hovering, true while the card flies to (or sits on) the hero.
    private var flying = false
    private var flight = 0.0
    private var flightV = 0.0
    private var flightAlpha = EditorialMenuMorphValue(1)
    private var start = EditorialMenuMorphCard()
    private var heroShown = EditorialMenuMorphValue(1)
    private var pageIn = EditorialMenuMorphValue(1)
    private var section = 0
    private var previousSection = 0
    // autoPlay
    private var clock = 0.0
    private var looping = false
    private var lastAuto: Bool?
    private var hold = 0.0
    private var userTouch = false
    private var ghostX = 0.0
    private var ghostY = 0.0
    private var ghostAlpha = 0.0
    private var ghostPress = 0.0
    private var lastDate: Date?
    private var reduced = false
    var onSelect: ((Int) -> Void)?
    // The touch under way: on the burger, or over the open menu.
    private enum Touch { case none, burger, menu, ignored }
    private var touch = Touch.none
    private var touchStart: Date?

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    func step(to date: Date, stage: EditorialMenuMorphStage, tints: [UInt32], autoPlay: Bool, reducedMotion: Bool) -> EditorialMenuMorphFrame {
        if count != tints.count {
            count = tints.count
            focus = Array(repeating: EditorialMenuMorphValue(1), count: count)
            shift = Array(repeating: EditorialMenuMorphValue(0), count: count)
            italic = Array(repeating: EditorialMenuMorphValue(0), count: count)
            rule = Array(repeating: EditorialMenuMorphValue(0), count: count)
            self.tints = tints.map(RGB.init)
            section = min(section, max(0, count - 1))
            previousSection = min(previousSection, max(0, count - 1))
        }
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
        if dt > 0 {
            open.step(dt)
            wipe.step(dt)
            shown.step(dt)
            bgT.step(dt)
            flightAlpha.step(dt)
            heroShown.step(dt)
            pageIn.step(dt)
            for i in 0..<count {
                focus[i].step(dt)
                shift[i].step(dt)
                italic[i].step(dt)
                rule[i].step(dt)
            }
            frame(dt)
        }
        return EditorialMenuMorphFrame(
            open: open.value, dir: dir,
            focus: focus.map(\.value), shift: shift.map(\.value), italic: italic.map(\.value), rule: rule.map(\.value),
            current: current, previous: previous, wipe: wipe.value, card: card,
            background: bgFrom.mix(bgTo, bgT.value),
            section: section, previousSection: previousSection, pageIn: pageIn.value, heroShown: heroShown.value,
            ghostX: ghostX, ghostY: ghostY, ghostAlpha: ghostAlpha, ghostPress: ghostPress
        )
    }

    /// The card has landed and the curtain is up: the page's hero takes over, its text writes in.
    private func handoff() {
        flying = false
        shown.set(0)
        card.alpha = 0
        if reduced { heroShown.animate(to: 1, duration: 0.3) } else { heroShown.set(1) }
        pageIn.set(0)
        pageIn.animate(to: 1, duration: EditorialMenuMorphStyle.pageInTime, curve: E.linear)
        setHover(-1)
    }

    private func toggle(user: Bool) {
        if flying { handoff() }
        isOpen.toggle()
        dir = isOpen ? 1 : -1
        setHover(-1)
        open.animate(to: isOpen ? 1 : 0, duration: reduced ? M.reducedTime : isOpen ? M.openTime : M.closeTime, curve: E.linear)
        if user { light.impactOccurred() }
    }

    private func moveTo(section next: Int) {
        previousSection = section
        section = next
    }

    /// Lifting on word `h`: the card flies to the hero while the curtain lifts off the new page.
    private func commit(_ h: Int) {
        let s = 0.86 + 0.14 * shown.value
        start = EditorialMenuMorphCard(x: px, y: py, w: M.previewW * s, h: M.previewH * s, rot: lean)
        flight = 0
        flightV = 0
        if reduced {
            flightAlpha.set(shown.value)
            flightAlpha.animate(to: 0, duration: 0.2)
        } else {
            flightAlpha.set(max(shown.value, 0.01))
            flightAlpha.animate(to: 1, duration: 0.16)
        }
        if current != h {
            previous = -1
            current = h
            wipe.set(1)
        }
        flying = true
        heroShown.set(0)
        pageIn.set(0)
        // The same choreography as a close, keeping the chosen word lit while it drops.
        isOpen = false
        dir = -1
        open.animate(to: 0, duration: reduced ? M.reducedTime : M.closeTime, curve: E.linear)
        moveTo(section: h)
        if userTouch { light.impactOccurred() }
        onSelect?(h)
    }

    /// Shows section `i` without the menu: its hero fades in and its text writes in again.
    private func swap(to i: Int) {
        if flying { handoff() }
        heroShown.set(0)
        heroShown.animate(to: 1, duration: 0.4, delay: 0.08)
        pageIn.set(0)
        pageIn.animate(to: 1, duration: EditorialMenuMorphStyle.pageInTime, curve: E.linear)
        moveTo(section: i)
        onSelect?(i)
    }

    /// The word under a point, or -1.
    private func wordAt(_ x: Double, _ y: Double) -> Int {
        let k = Int(floor((y - stage.listTop) / M.row))
        if k < 0 || k >= count || x > stage.width - 8 { return -1 }
        return k
    }

    private func release() {
        let h = hover
        if h >= 0 && isOpen && open.value > 0.9 && !flying { commit(h) } else { setHover(-1) }
    }

    /// A new word under the finger: the words dim, step and turn italic, the background
    /// crossfades to its colour and its photo wipes in.
    private func setHover(_ now: Int) {
        let before = hover
        guard now != before else { return }
        hover = now
        for i in 0..<count {
            let on = now < 0 || now == i
            let held = now == i
            focus[i].animate(to: on ? 1 : M.dimmed, duration: 0.22)
            shift[i].animate(to: held && !reduced ? M.focusShift : 0, duration: 0.32, curve: E.out)
            italic[i].animate(to: held ? 1 : 0, duration: 0.24)
            if held { rule[i].animate(to: 1, duration: 0.38, curve: E.out) } else { rule[i].animate(to: 0, duration: 0.2) }
        }
        bgFrom = bgFrom.mix(bgTo, bgT.value)
        bgTo = now >= 0 && now < tints.count ? tints[now] : RGB(EditorialMenuMorphStyle.baseTint)
        bgT.set(0)
        bgT.animate(to: 1, duration: M.bgTime)
        if flying { return }
        if now < 0 {
            shown.animate(to: 0, duration: 0.22)
            return
        }
        if before < 0 || shown.value < 0.05 {
            // The first word under the finger: the card appears where the finger is, already filled.
            previous = -1
            current = now
            wipe.set(1)
            px = fingerX + M.previewDX
            py = fingerY + M.previewDY
        } else {
            previous = current
            current = now
            wipe.set(0)
            wipe.animate(to: 1, duration: reduced ? 0.001 : M.wipeTime, curve: E.wipe)
        }
        shown.animate(to: 1, duration: 0.26, curve: E.out)
        if userTouch { selection.selectionChanged() }
    }

    private func frame(_ dt: Double) {
        // The card chases the finger; it leans into its own sideways speed.
        let W = stage.width
        let tx = min(max(fingerX + M.previewDX, M.previewW / 2 + 10), W - M.previewW / 2 - 10)
        let ty = max(fingerY + M.previewDY, stage.top + 60 + M.previewH / 2)
        let k = reduced ? 1 : 1 - exp(-dt * M.lag)
        let nx = px + (tx - px) * k
        let vx = (nx - px) / dt
        px = nx
        py += (ty - py) * k
        let leanTarget = reduced ? 0 : E.clamp(vx * M.leanPerSpeed, -M.leanMax, M.leanMax)
        lean += (leanTarget - lean) * (1 - exp(-dt * 10))

        if flying {
            // The flight: a damped spring from 0 to 1, in small steps so a long frame stays stable.
            var f = flight
            var v = flightV
            if reduced {
                f = 1
                v = 0
            } else {
                let h = dt / 4
                for _ in 0..<4 {
                    v += (-M.flightK * (f - 1) - M.flightC * v) * h
                    f += v * h
                }
                if abs(1 - f) < 0.0015 && abs(v) < 0.02 {
                    f = 1
                    v = 0
                }
            }
            flight = f
            flightV = v
            // The hero's frame as it is drawn right now, inside the sinking page.
            let L = stage.layout
            let p = reduced ? 0 : E.inOut(E.part(open.value, M.panel))
            let s = 1 - M.sinkScale * p
            let hx = W / 2 + (L.heroX + L.heroW / 2 - W / 2) * s
            let hy = stage.height / 2 + (L.heroY + L.heroH / 2 - stage.height / 2) * s + M.sink * p
            let F = reduced ? 0 : E.clamp(f)
            let w = E.lerp(start.w, L.heroW * s, F)
            let h = E.lerp(start.h, L.heroH * s, F)
            // A circle opening into the hero's rectangle: the corner keeps to half the shorter side,
            // shrinking to nothing a little ahead of the size so the last of the round goes calmly.
            card = EditorialMenuMorphCard(
                x: E.lerp(start.x, hx, F), y: E.lerp(start.y, hy, F),
                w: w, h: h,
                rot: E.lerp(start.rot, 0, F), radius: min(w, h) / 2 * pow(1 - F, 1.4),
                alpha: flightAlpha.value, tone: F
            )
            if f >= 1 && open.value <= 0.0005 { handoff() }
        } else {
            let s = 0.86 + 0.14 * shown.value
            card = EditorialMenuMorphCard(x: px, y: py, w: M.previewW * s, h: M.previewH * s, rot: lean, radius: M.previewW * s / 2, alpha: shown.value, tone: 0)
        }

        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                // The pause is over: close the menu, go back to the first page, start the loop again.
                clock = -M.resumeRest
                userTouch = false
                if isOpen { toggle(user: false) }
                if section != 0 { swap(to: 0) }
            }
            return
        }
        guard looping, W > 0 else { return }
        let before = clock
        var c = before + dt
        if c >= M.loop { c -= M.loop }
        clock = c

        let burgerX = W - 36
        let burgerY = stage.top + 30
        var ghostOn = 0.0
        var press = 0.0
        for at in M.taps {
            if crossed(before, c, at - 0.3) {
                ghostX = burgerX
                ghostY = burgerY
            }
            if crossed(before, c, at) && !isOpen { toggle(user: false) }
            ghostOn = max(ghostOn, min(1, 2 * pulse(c, at, 0.3)))
            press = max(press, pulse(c, at, 0.12))
        }
        // The glides: from word to word, easing between stops, drifting a little to the right;
        // the last stop is the lift.
        for glide in M.glides {
            guard let first = glide.first?.0, let lift = glide.last?.0 else { continue }
            if c >= first && c < lift && isOpen {
                var row = glide[0].1
                for i in 1..<glide.count {
                    let (t0, r0) = glide[i - 1]
                    let (t1, r1) = glide[i]
                    if c >= t0 { row = E.lerp(r0, r1, smooth((c - t0) / (t1 - t0))) }
                }
                let x = M.listLeft + 90 + 50 * sin((c - first) * 1.3)
                let y = stage.listTop + (row + 0.5) * M.row
                fingerX = x
                fingerY = y
                ghostX = x
                ghostY = y
                setHover(wordAt(x, y))
            }
            if crossed(before, c, lift) { release() }
            ghostOn = max(ghostOn, E.clamp(min((c - first + 0.25) / 0.2, (lift + 0.2 - c) / 0.2)))
        }
        ghostAlpha = ghostOn
        ghostPress = press
    }

    private func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    /// 1 at `at`, falling to 0 `width` s either side.
    private func pulse(_ c: Double, _ at: Double, _ width: Double) -> Double {
        E.clamp(1 - abs(c - at) / width)
    }

    private func smooth(_ u: Double) -> Double {
        let t = E.clamp(u)
        return t * t * (3 - 2 * t)
    }

    private func touched() {
        hold = M.userHold
        userTouch = true
        ghostAlpha = 0
    }

    /// The burger and its "Menu" label, with some slop around them.
    private func onBurger(_ p: CGPoint) -> Bool {
        p.x >= stage.width - 36 - 22 - 58 && p.x <= stage.width && abs(p.y - (stage.top + 30)) <= 32
    }

    // MARK: The touch

    // One touch for the whole screen: a tap on the burger toggles the menu; over the open menu the
    // finger is a pointer from touch down, and lifting on a word opens its page.
    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            touched()
            let start = value.startLocation
            if onBurger(start) {
                touch = .burger
            } else if isOpen {
                touch = .menu
                fingerX = start.x
                fingerY = start.y
                setHover(wordAt(start.x, start.y))
            } else {
                touch = .ignored
            }
        }
        if touch == .menu {
            fingerX = value.location.x
            fingerY = value.location.y
            if isOpen { setHover(wordAt(fingerX, fingerY)) }
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touch = .none
            touchStart = nil
        }
        switch touch {
        case .burger:
            if onBurger(value.location), let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration {
                toggle(user: true)
            }
        case .menu:
            release()
        default:
            break
        }
    }
}
