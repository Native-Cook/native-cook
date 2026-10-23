import CoreText
import SwiftUI
import UIKit

enum KineticTypeSplashMetrics {
    // Huge condensed capitals, sized so the widest word fills the width between `margin` and the edge.
    static let fontName = "AvenirNextCondensed-Heavy"
    static let margin = 22.0
    static let maxSize = 168.0
    // Each line is a window as tall as a capital plus `pad` of its size above and below; letters rise
    // into it from below the baseline. Lines sit `leading` of a capital apart.
    static let pad = 0.12
    static let leading = 1.2
    // The rolling word glows like a neon tube: a copy blurred by `glowBlur` pt under it.
    static let glowBlur = 9.0
    // Letters: each rises over `letterTime` s, `letterLag` s after the one before; lines `lineLag` apart.
    static let letterTime = 0.55
    static let letterLag = 0.045
    static let lineLag = 0.38
    static let introEnd = 2 * lineLag + 5 * letterLag + letterTime
    static let outEnd = 4 * letterLag + letterTime
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.16, y: 1), endControlPoint: UnitPoint(x: 0.3, y: 1))
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.76, y: 0), endControlPoint: UnitPoint(x: 0.24, y: 1))

    // The rolling word's columns: the first follows the reel on a lively spring, each next one
    // follows its neighbour on a stiffer one, so the letters land one after another like a slot machine.
    static let leadK = 230.0
    static let leadC = 21.0
    static let chainK = 420.0
    static let chainC = 37.0
    static let columns = 8
    // The columns' springs are stepped at most this long (s) at a time: the stiff chain blows up
    // when stepped in long frames.
    static let maxStep = 0.025
    // A flick throws the reel this many seconds ahead before it snaps to a word.
    static let throwTime = 0.12
    static let dragSlop = 8.0
    static let tapMax = 0.4

    // Reveal: the photo fills the last word, then the word grows from a point in its first stem
    // until the photo is the whole screen.
    static let fillTime = 0.5
    static let zoomTime = 1.15
    static let zoomMax = 70.0
    static let finalTime = 0.85
    static let exitTime = 0.65

    // autoPlay script (s).
    static let loop = 9.6
    static let introAt = 0.25
    static let rollAt = [2.2, 3.1]
    static let revealAt = 3.8
    static let pressAt = 8.3
    static let exitAt = 8.45
    static let userHold = 4.0

    static func seg(_ v: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (v - a) / (b - a))) }
    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func standard(_ p: Double) -> Double { p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2 }
}

/// A word laid out letter by letter: each letter's outline at the origin of its baseline, and its left edge.
struct KineticTypeSplashWord {
    var letters: [(path: Path, x: Double)]
    /// The whole word as one outline on its baseline.
    var path: Path
}

/// The type fitted to the screen.
struct KineticTypeSplashType {
    var size: Double
    var cap: Double
    var lines: [KineticTypeSplashWord]
    var words: [KineticTypeSplashWord]

    private typealias M = KineticTypeSplashMetrics

    init(width: Double, lines: [String], words: [String]) {
        func font(_ size: Double) -> CTFont { CTFontCreateWithName(M.fontName as CFString, size, nil) }
        func glyphs(_ text: String, _ font: CTFont) -> [CGGlyph] {
            text.map { char -> CGGlyph in
                var units = Array(String(char).utf16)
                var glyph = [CGGlyph](repeating: 0, count: units.count)
                CTFontGetGlyphsForCharacters(font, &units, &glyph, units.count)
                return glyph[0]
            }
        }
        func advances(_ text: String, _ font: CTFont) -> [Double] {
            var g = glyphs(text, font)
            var out = [CGSize](repeating: .zero, count: g.count)
            CTFontGetAdvancesForGlyphs(font, .horizontal, &g, &out, g.count)
            return out.map { Double($0.width) }
        }
        // Fitted at 100 pt, then set at the size that makes the widest word fill the width.
        let probe = font(100)
        let widest = (lines + words).map { advances($0, probe).reduce(0, +) }.max() ?? 1
        size = min(M.maxSize, 100 * (width - M.margin * 2) / widest)
        let face = font(size)
        let flip = CGAffineTransform(scaleX: 1, y: -1)
        func outline(_ glyph: CGGlyph) -> Path {
            var t = flip
            guard let cg = CTFontCreatePathForGlyph(face, glyph, &t) else { return Path() }
            return Path(cg)
        }
        cap = outline(glyphs("H", face)[0]).boundingRect.height
        func layout(_ text: String) -> KineticTypeSplashWord {
            let g = glyphs(text, face)
            let a = advances(text, face)
            var x = M.margin
            var letters: [(Path, Double)] = []
            var whole = Path()
            for i in g.indices {
                let p = outline(g[i])
                letters.append((p, x))
                whole.addPath(p, transform: CGAffineTransform(translationX: x, y: 0))
                x += a[i]
            }
            return KineticTypeSplashWord(letters: letters, path: whole)
        }
        self.lines = lines.map(layout)
        self.words = words.map(layout)
    }
}

/// A number driven like a Reanimated shared value by a (delayed) timing, stepped by the block's
/// clock; a finished timing hands back its callback, an interrupted one never calls it.
struct KineticTypeSplashValue {
    private(set) var value: Double
    private var from: Double?
    private var to = 0.0
    private var start = 0.0
    private var duration = 0.0
    private var curve: (Double) -> Double = { $0 }
    private var running = false
    private var done: (() -> Void)?

    init(_ value: Double) { self.value = value }

    mutating func set(_ value: Double) {
        self.value = value
        running = false
        done = nil
    }

    mutating func timing(to target: Double, duration: Double, delay: Double = 0, now: Double,
                         curve: @escaping (Double) -> Double = { $0 }, then: (() -> Void)? = nil) {
        from = delay > 0 ? nil : value
        to = target
        start = now + delay
        self.duration = duration
        self.curve = curve
        running = true
        done = then
    }

    mutating func step(now: Double) -> (() -> Void)? {
        guard running, now >= start else { return nil }
        let origin = from ?? value
        from = origin
        let p = duration <= 0 ? 1 : min(1, max(0, (now - start) / duration))
        value = origin + (to - origin) * curve(p)
        guard p >= 1 else { return nil }
        running = false
        let callback = done
        done = nil
        return callback
    }
}

struct KineticTypeSplashLayout: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double

    var ctaTop: Double { height - max(bottom, 16) - 8 - 56 }
}

/// Everything one frame draws.
struct KineticTypeSplashFrame {
    var layout: KineticTypeSplashLayout
    var intro: Double
    var outro: Double
    var cols: [Double]
    var fill: Double
    var zoom: Double
    var final: Double
    var exit: Double
    var press: Double
    var reducedMotion: Bool
}

struct KineticTypeSplashSettings {
    var autoPlay: Bool
    var reducedMotion: Bool
    var layout: KineticTypeSplashLayout
    /// How many words the last line rolls through, and the height a word rolls by.
    var words: Int
    var windowHeight: Double
    var onGetStarted: (() -> Void)?
}

/// The splash's state, stepped once a frame like the RN frame callback, and the touches that drive it.
@MainActor
final class KineticTypeSplashEngine {
    private typealias M = KineticTypeSplashMetrics

    // Seconds into the lines' entrance and exit, the reel (in words), the reveal's three steps, the way out.
    private var intro = KineticTypeSplashValue(0)
    private var outro = KineticTypeSplashValue(0)
    private var reel = 0.0
    private var cols = Array(repeating: 0.0, count: M.columns)
    private var colVel = Array(repeating: 0.0, count: M.columns)
    private var fill = KineticTypeSplashValue(0)
    private var zoom = KineticTypeSplashValue(0)
    private var final = KineticTypeSplashValue(0)
    private var exit = KineticTypeSplashValue(0)
    private var press = KineticTypeSplashValue(0)
    // 0 lines, 1 rolling, 2 revealing, 3 welcome, 4 leaving.
    private var stage = 0
    private var dragging = false
    private var dragFrom = 0.0
    private var time = 0.0
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastWhole = 0

    private var lastDate: Date?
    private var started = false
    private var settings = KineticTypeSplashSettings(
        autoPlay: false, reducedMotion: false,
        layout: KineticTypeSplashLayout(width: 1, height: 1, top: 0, bottom: 0),
        words: 3, windowHeight: 100, onGetStarted: nil
    )
    private var touchStart: Date?
    private var panning = false

    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    private var reduced: Bool { settings.reducedMotion }
    private var last: Int { settings.words - 1 }

    private var typeSet: (key: [String], set: KineticTypeSplashType)?

    /// The type fitted to `width`, set once per width and set of words.
    func type(width: Double, lines: [String], words: [String]) -> KineticTypeSplashType {
        let key = ["\(width)"] + lines + ["|"] + words
        if let typeSet, typeSet.key == key { return typeSet.set }
        let set = KineticTypeSplashType(width: width, lines: lines, words: words)
        typeSet = (key, set)
        return set
    }

    func step(to date: Date, settings next: KineticTypeSplashSettings) -> KineticTypeSplashFrame {
        let auto = next.autoPlay && !next.reducedMotion
        if !started || auto != (settings.autoPlay && !settings.reducedMotion) {
            settings = next
            started = true
            looping = auto
            clock = 0
            hold = 0
            if !auto { startIntro(delay: M.introAt, fast: false) }
        }
        settings = next
        let dt = min(lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60, 0.064)
        lastDate = date
        if dt > 0 { advance(dt) }
        return KineticTypeSplashFrame(
            layout: next.layout, intro: intro.value, outro: outro.value, cols: cols, fill: fill.value,
            zoom: zoom.value, final: final.value, exit: exit.value, press: press.value, reducedMotion: reduced
        )
    }

    private func advance(_ dt: Double) {
        time += dt
        let now = time
        var callbacks: [() -> Void] = []
        for value in [\KineticTypeSplashEngine.intro, \.outro, \.fill, \.zoom, \.final, \.exit, \.press] {
            if let c = self[keyPath: value].step(now: now) { callbacks.append(c) }
        }
        callbacks.forEach { $0() }

        // The reel's columns: the first chases the reel (or sits under the finger), the rest chase it.
        if !reel.isFinite { reel = 0 }
        let target = reel
        let steps = max(1, Int((dt / M.maxStep).rounded(.up)))
        let h = dt / Double(steps)
        for j in 0..<cols.count {
            if reduced {
                cols[j] += (target - cols[j]) * min(1, dt * 14)
                colVel[j] = 0
                continue
            }
            if j == 0 && dragging {
                colVel[0] = (target - cols[0]) / dt
                cols[0] = target
                continue
            }
            let lead = j == 0 ? target : cols[j - 1]
            let k = j == 0 ? M.leadK : M.chainK
            let damp = j == 0 ? M.leadC : M.chainC
            for _ in 0..<steps {
                colVel[j] += (k * (lead - cols[j]) - damp * colVel[j]) * h
                cols[j] += colVel[j] * h
            }
            if !cols[j].isFinite || !colVel[j].isFinite {
                cols[j] = target
                colVel[j] = 0
            }
        }
        // A tick each time a word reaches the window under the finger (Int() traps on NaN).
        let whole = cols[0].isFinite ? Int(cols[0].rounded()) : lastWhole
        if whole != lastWhole && dragging { selection.selectionChanged() }
        lastWhole = whole

        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                clock = stage <= 1 ? (intro.value > 0 ? M.introEnd + M.introAt - 0.2 : 0) : stage <= 3 ? M.pressAt - 0.5 : M.exitAt + 0.01
            }
            return
        }
        guard looping else { return }
        let t0 = clock
        let t = (t0 + dt).truncatingRemainder(dividingBy: M.loop)
        clock = t
        func passed(_ at: Double) -> Bool { (t0 < at && t >= at) || (t < t0 && (at > t0 || at <= t)) }
        if passed(M.introAt) && stage == 0 && intro.value == 0 { startIntro(delay: 0, fast: false) }
        for at in M.rollAt where passed(at) && stage <= 1 && reel < Double(last) {
            roll(to: reel.rounded() + 1)
        }
        if passed(M.revealAt) { reveal(delay: 0) }
        if passed(M.pressAt) && stage == 3 { pulse(down: 0.11, up: 0.2) }
        if passed(M.exitAt) { leave(user: false) }
    }

    // MARK: - Moves

    private func startIntro(delay: Double, fast: Bool) {
        let left = max(0, M.introEnd - intro.value)
        intro.timing(to: M.introEnd, duration: fast ? 0.26 : left, delay: delay, now: time)
    }

    private func roll(to word: Double) {
        guard stage <= 1 else { return }
        stage = 1
        reel = max(0, min(Double(last), word))
    }

    private func reveal(delay: Double) {
        guard stage <= 1 else { return }
        stage = 2
        reel = Double(last)
        fill.timing(to: 1, duration: M.fillTime, delay: delay, now: time)
        outro.timing(to: M.outEnd, duration: M.outEnd, delay: delay, now: time)
        zoom.timing(to: 1, duration: reduced ? 0.5 : M.zoomTime, delay: delay + M.fillTime + 0.2, now: time, then: { [weak self] in
            guard let self else { return }
            self.stage = 3
            self.final.timing(to: 1, duration: M.finalTime, now: self.time)
        })
    }

    private func reset() {
        intro.set(0)
        outro.set(0)
        reel = 0
        cols = Array(repeating: 0, count: M.columns)
        colVel = Array(repeating: 0, count: M.columns)
        fill.set(0)
        zoom.set(0)
        final.set(0)
        exit.set(0)
        stage = 0
        if !looping || hold > 0 { startIntro(delay: 0.4, fast: false) }
    }

    private func leave(user: Bool) {
        guard stage == 3, final.value >= 0.9 else { return }
        stage = 4
        if user {
            light.impactOccurred()
            settings.onGetStarted?()
        }
        exit.timing(to: 1, duration: reduced ? 0.35 : M.exitTime, now: time, curve: M.easeInOut.value(at:)) { [weak self] in
            self?.reset()
        }
    }

    /// The button scales down and back, like `withSequence` of two timings.
    private func pulse(down: Double, up: Double) {
        press.timing(to: 1, duration: down, now: time, curve: M.standard) { [weak self] in
            guard let self else { return }
            self.press.timing(to: 0, duration: up, now: self.time, curve: M.standard)
        }
    }

    // MARK: - Touches

    // One recogniser stands in for the race of a vertical pan (after 8 pt up or down) and a tap (a
    // touch lifted within 400 ms that never travelled that far).
    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
        }
        if !panning && abs(value.translation.height) >= M.dragSlop {
            panning = true
            panStart()
        }
        if panning && dragging {
            let raw = dragFrom - value.translation.height / settings.windowHeight
            // Past the first or last word the reel gives less and less.
            let over = raw < 0 ? raw : raw > Double(last) ? raw - Double(last) : 0
            reel = raw - over + over / (1 + abs(over) * 2.5)
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        let held = touchStart.map { value.time.timeIntervalSince($0) } ?? 0
        if panning {
            if dragging {
                dragging = false
                let speed = -value.velocity.height / settings.windowHeight
                colVel[0] = speed
                let to = max(0, min(Double(last), (reel + speed * M.throwTime).rounded()))
                reel = to
                if Int(to) == last { reveal(delay: 0.7) }
            }
        } else if held <= M.tapMax {
            tap(at: value.location)
        }
        dragging = false
        touchStart = nil
        panning = false
    }

    /// A touch the system cancelled: the reel is let go where it is, like the RN pan's onFinalize.
    func touchLost() {
        guard touchStart != nil else { return }
        dragging = false
        touchStart = nil
        panning = false
    }

    private func panStart() {
        hold = M.userHold
        guard stage <= 1, intro.value >= M.introEnd - 0.3, !reduced else { return }
        stage = 1
        dragFrom = cols[0]
        dragging = true
    }

    private func tap(at point: CGPoint) {
        hold = M.userHold
        let ctaTop = settings.layout.ctaTop
        if stage <= 1 && intro.value < M.introEnd - 0.05 {
            startIntro(delay: 0, fast: true)
            selection.selectionChanged()
        } else if stage <= 1 && reel < Double(last) {
            roll(to: reel.rounded() + 1)
            selection.selectionChanged()
        } else if stage <= 1 {
            reveal(delay: 0)
            selection.selectionChanged()
        } else if stage == 3 && point.y > ctaTop - 8 && point.y < ctaTop + 64 {
            pulse(down: 0.09, up: 0.18)
            leave(user: true)
        }
    }
}
