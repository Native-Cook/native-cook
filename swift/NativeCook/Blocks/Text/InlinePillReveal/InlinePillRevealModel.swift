import CoreText
import Foundation
import UIKit

/// Timing, copy, colours and the frame the Inline Pill Reveal draws. Every value is a function of
/// the loop time (autoPlay) or of the reveal amount q (touch), so the three stacks match.
enum InlinePillRevealModel {
    static let loop: Double = 8.0

    // MARK: Copy

    static let line1 = ["Slow", "mornings,"]
    static let line2 = ["made", "yours"]
    static let brand = "slowfield"
    static let skip = "Skip"
    static let cta = "Find your stay"

    // MARK: Colours

    static let page: UInt32 = 0xB9715A
    static let cream: UInt32 = 0xFBF3EA
    static let ink: UInt32 = 0x3A2420

    // MARK: Type and layout (pt)

    static let fontSize: CGFloat = 44
    static let tracking: CGFloat = -0.4
    static let baselineDrop: CGFloat = 15      // baseline = cy + 15, so the caps sit on cy
    static let caretW: CGFloat = 2.5
    static let caretH: CGFloat = 44
    static let pillH: CGFloat = 76
    static let pillMargin: CGFloat = 14        // the inline pill's widest: W − 2 · 14
    static let wordGap: CGFloat = 12           // words ride the pill's edges at this gap
    static let caretPart: CGFloat = 3          // the words part this much around the caret
    static let dragSpan: CGFloat = 0.75        // a drag of 0.75 W covers the whole reveal

    // MARK: Fonts (Outfit, OFL)

    private static let fontFiles = ["InlinePillReveal-Outfit-Regular", "InlinePillReveal-Outfit-Medium"]

    /// PostScript names of the bundled fonts, registered once.
    static let fontNames: [String] = {
        fontFiles.map { file in
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf"),
                  let data = try? Data(contentsOf: url) as CFData,
                  let provider = CGDataProvider(data: data),
                  let font = CGFont(provider) else {
                assertionFailure("Missing Inline Pill Reveal font: \(file).ttf")
                return "HelveticaNeue"
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            return (font.postScriptName as String?) ?? "HelveticaNeue"
        }
    }()

    static var regular: String { fontNames[0] }
    static var medium: String { fontNames[1] }

    // MARK: Curves

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeIn(_ x: Double) -> Double { x * x * x }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func lerp(_ a: CGFloat, _ b: CGFloat, _ t: Double) -> CGFloat { a + (b - a) * CGFloat(t) }

    // MARK: Frames

    /// The autoPlay timeline (seconds in 0 ..< loop). Frame 0 and frame `loop` are both the text line at rest.
    static func timeline(_ t: Double) -> InlinePillRevealFrame {
        var f = InlinePillRevealFrame()
        f.t = t
        f.part = easeOut(seg(t, 0.70, 0.90)) * (1 - easeInOut(seg(t, 7.30, 7.55)))
        // Caret: on, one blink, on, then it becomes the pill; it shows again for a beat as the pill closes.
        let blinkA = seg(t, 0.70, 0.78) - seg(t, 0.98, 1.06)
        let blinkB = seg(t, 1.14, 1.22) * (1 - seg(t, 1.40, 1.47))
        let closing = seg(t, 7.36, 7.40) * (1 - seg(t, 7.46, 7.56))
        f.caret = max(blinkA, blinkB, closing)
        f.open = easeInOut(seg(t, 1.40, 2.60)) * (1 - easeInOut(seg(t, 6.40, 7.40)))
        f.full = easeInOut(seg(t, 2.60, 3.30)) * (1 - easeInOut(seg(t, 5.70, 6.40)))
        for i in 0 ..< 2 {
            f.wordIn[i] = easeOut(seg(t, 3.25 + 0.10 * Double(i), 3.70 + 0.10 * Double(i)))
            f.wordOut[i] = easeIn(seg(t, 5.46 - 0.06 * Double(i), 5.76 - 0.06 * Double(i)))
        }
        f.ctaIn = easeOut(seg(t, 3.45, 3.90))
        f.ctaOut = easeIn(seg(t, 5.40, 5.70))
        f.dots = f.full
        return f
    }

    /// The same screen driven by the reveal amount q (0 = text line, 1 = full scene) under the finger.
    static func reveal(_ q: Double, t: Double) -> InlinePillRevealFrame {
        var f = InlinePillRevealFrame()
        f.t = t
        f.open = easeInOut(seg(q, 0, 0.6))
        f.full = easeInOut(seg(q, 0.6, 1))
        for i in 0 ..< 2 {
            f.wordIn[i] = easeOut(seg(q, 0.84 + 0.05 * Double(i), 0.95 + 0.05 * Double(i)))
        }
        f.ctaIn = easeOut(seg(q, 0.9, 1))
        f.dots = f.full
        return f
    }

    /// The reveal amount that shows the same pill as `f` (open and full never move at the same time).
    static func q(of f: InlinePillRevealFrame) -> Double {
        f.full > 0 ? 0.6 + 0.4 * inverseEaseInOut(f.full) : 0.6 * inverseEaseInOut(f.open)
    }

    static func inverseEaseInOut(_ y: Double) -> Double {
        if y <= 0 { return 0 }
        if y >= 1 { return 1 }
        return y < 0.5 ? cbrt(y / 4) : 1 - cbrt(2 * (1 - y)) / 2
    }
}

struct InlinePillRevealFrame {
    var t: Double = 0          // loop time, for the scene's idle life (birds, mist, sun glow)
    var part: Double = 0       // the words part around the caret
    var caret: Double = 0      // caret opacity
    var open: Double = 0       // inline pill: 0 closed … 1 nearly full width
    var full: Double = 0       // the pill growing to full bleed
    var wordIn: [Double] = [0, 0]
    var wordOut: [Double] = [0, 0]
    var ctaIn: Double = 0
    var ctaOut: Double = 0
    var dots: Double = 0       // active page dot, 0 → 1
}

/// Plays the timeline, hands over to the finger, and eases home 2 s after the last touch.
final class InlinePillRevealDriver {
    typealias M = InlinePillRevealModel

    private var epoch: Double?
    private(set) var manual = false
    private(set) var dragging = false
    private var q: Double = 0
    private var q0: Double = 0
    private var anim: (from: Double, to: Double, start: Double, dur: Double)?
    private var lastTouch: Double = -100
    private var returning = false
    // Reduced motion: 1 shows the scene, 0 the text line; a tap crossfades in 0.25 s.
    private var shown: Double = 1
    private var fadeFrom: Double = 1
    private var fadeStart: Double = -100

    func reset() {
        epoch = nil
        manual = false
        dragging = false
        q = 0
        anim = nil
        returning = false
        shown = 1
        fadeFrom = 1
        fadeStart = -100
    }

    private func animated(_ now: Double) -> Double {
        guard let a = anim else { return q }
        let k = M.seg(now, a.start, a.start + a.dur)
        let v = a.from + (a.to - a.from) * M.easeInOut(k)
        if k >= 1 {
            q = a.to
            anim = nil
        }
        return v
    }

    private func animate(to target: Double, from: Double, at now: Double) {
        q = from
        anim = (from, target, now, 0.3 + 1.2 * abs(target - from))
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> InlinePillRevealFrame {
        if epoch == nil { epoch = now }
        let t = (now - (epoch ?? now)).truncatingRemainder(dividingBy: M.loop)
        if reduced { return M.reveal(1, t: 0) }
        if manual {
            let v = animated(now)
            if auto && !dragging && now - lastTouch > 2 {
                if !returning {
                    returning = true
                    animate(to: 0, from: v, at: now)
                } else if anim == nil {
                    manual = false
                    returning = false
                    epoch = now
                    return M.timeline(0)
                }
            }
            return M.reveal(v, t: t)
        }
        return auto ? M.timeline(t) : M.reveal(0, t: t)
    }

    /// Reduced motion: how much of the scene shows (the text line shows 1 − this).
    func shownAmount(at now: Double) -> Double {
        let k = M.seg(now, fadeStart, fadeStart + 0.25)
        return fadeFrom + (shown - fadeFrom) * k
    }

    func toggleReduced(at now: Double) {
        fadeFrom = shownAmount(at: now)
        shown = shown > 0.5 ? 0 : 1
        fadeStart = now
    }

    /// Whether a touch at this height may start a reveal drag.
    func accepts(y: CGFloat, cy: CGFloat, now: Double, auto: Bool) -> Bool {
        let f = frame(at: now, auto: auto, reduced: false)
        return M.q(of: f) > 0.02 || abs(y - cy) < 70
    }

    func begin(at now: Double, auto: Bool) {
        let f = frame(at: now, auto: auto, reduced: false)
        q = manual ? animated(now) : M.q(of: f)
        q0 = q
        anim = nil
        manual = true
        dragging = true
        returning = false
        lastTouch = now
    }

    func drag(_ dx: CGFloat, width: CGFloat, at now: Double) {
        let dir: Double = q0 < 0.5 ? 1 : -1
        q = min(max(q0 + dir * Double(abs(dx) / (M.dragSpan * width)), 0), 1)
        lastTouch = now
    }

    /// Ends the drag or tap; returns true when the reveal commits to the other state.
    func end(dx: CGFloat, vx: CGFloat, tap: Bool, width: CGFloat, at now: Double) -> Bool {
        dragging = false
        lastTouch = now
        let wasOpen = q0 >= 0.5
        let target: Double
        if tap {
            target = wasOpen ? 0 : 1
        } else {
            let dir: Double = wasOpen ? -1 : 1
            let projected = q0 + dir * Double(abs(dx + 0.25 * vx) / (M.dragSpan * width))
            target = projected > 0.5 ? 1 : 0
        }
        animate(to: target, from: q, at: now)
        return (target > 0.5) != wasOpen
    }

    var busy: Bool { manual }
}
