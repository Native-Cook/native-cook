import CoreGraphics
import Foundation
import SwiftUI

/// Timing, copy, colours, icons and the frame the Oracle Glass Grid draws. Every value is a function of
/// the story time `t` (0 ..< loop) and the shimmer clock, so the three stacks match frame for frame.
enum OracleGlassGridModel {
    static let loop: Double = 10.0

    // MARK: Copy

    static let titleGrid = "What answers do you seek?"
    static let titleField = "Type your question"
    static let placeholder = "Tap to type"
    static let sample = "Should I take the leap?"
    static let ask = "ASK FOR ME"
    static let labels: [[String]] = [
        ["School"], ["Friends and", "Family"], ["Love"],
        ["Career"], ["Life's Greatest", "Mysteries"], ["My Future"],
        ["Myself"], ["Style"], ["Lunch"],
    ]
    /// The card the self-playing story picks: Career.
    static let autoChoice = 3

    // MARK: Colours

    static let white: UInt32 = 0xFFFFFF
    static let cardWhite: UInt32 = 0xF6F3EE
    static let ink: UInt32 = 0x1E2A23

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 0xFF) / 255,
              green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255,
              opacity: a)
    }

    // MARK: Layout (pt)

    static let cardW: CGFloat = 100
    static let cardH: CGFloat = 120
    static let cardR: CGFloat = 14
    static let gap: CGFloat = 7
    static let centerScale: CGFloat = 1.22
    static let fieldH: CGFloat = 56
    static let fieldMargin: CGFloat = 26.5
    static let caretW: CGFloat = 2
    static let caretH: CGFloat = 24
    static let camera: CGFloat = 600
    static let iconScale: CGFloat = 1.18     // the 56 pt icon box drawn at 66 pt
    static let iconLift: CGFloat = 13        // icon centre above the card's centre
    static let askW: CGFloat = 236
    static let askH: CGFloat = 44

    static func gridCenter(w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat) -> CGPoint {
        CGPoint(x: w / 2, y: top + (h - top - bottom) / 2 + 10)
    }

    static func fieldCenter(w: CGFloat, h: CGFloat) -> CGPoint { CGPoint(x: w / 2, y: h / 2) }

    static func slot(_ i: Int, grid: CGPoint) -> CGPoint {
        let col = CGFloat(i % 3 - 1), row = CGFloat(i / 3 - 1)
        return CGPoint(x: grid.x + col * (cardW + gap), y: grid.y + row * (cardH + gap))
    }

    /// Rank of card `i` by distance from the chosen card (1 = nearest); ties keep index order.
    static func ranks(chosen: Int) -> [Int] {
        func d(_ i: Int) -> Double {
            let dx = Double(i % 3 - chosen % 3), dy = Double(i / 3 - chosen / 3)
            return (dx * dx + dy * dy).squareRoot()
        }
        let order = (0 ..< 9).filter { $0 != chosen }.sorted { d($0) < d($1) || (d($0) == d($1) && $0 < $1) }
        var r = Array(repeating: 0, count: 9)
        for (k, i) in order.enumerated() { r[i] = k + 1 }
        return r
    }

    // MARK: Curves

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeIn(_ x: Double) -> Double { x * x * x }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutBack(_ x: Double, _ s: Double = 1.70158) -> Double {
        let c3 = s + 1
        return 1 + c3 * pow(x - 1, 3) + s * pow(x - 1, 2)
    }
    static func lerp(_ a: CGFloat, _ b: CGFloat, _ t: Double) -> CGFloat { a + (b - a) * CGFloat(t) }
    static func wave(_ t: Double, k: Double, phase: Double) -> Double {
        0.5 + 0.5 * sin(2 * .pi * k * t / loop + phase)
    }
    /// A caret blink of 1 s: on for the first half, with soft edges.
    static func blink(_ s: Double) -> Double {
        min(max(0.5 + 3 * cos(2 * .pi * s), 0), 1)
    }

    // MARK: Typing

    static func typeTime(_ k: Int) -> Double {
        5.60 + 0.056 * Double(k) + (k == 0 ? 0 : 0.018 * sin(Double(k) * 2.3))
    }

    static func typed(at t: Double) -> Int {
        if t >= 7.50 { return 0 }
        var n = 0
        for k in 0 ..< sample.count where typeTime(k) <= t { n = k + 1 }
        return n
    }

    // MARK: Frames

    /// The story at time `t`. `chosen` is the picked card; `typed` overrides the typed characters (nil: the
    /// self-playing sample); `blinkClock` overrides the caret blink (nil: from `t`); `clock` drives lava and shimmer.
    static func frame(_ t: Double, chosen: Int, typed typedOverride: Int? = nil, blinkClock: Double? = nil,
                      clock: Double) -> OracleGlassGridFrame {
        var f = OracleGlassGridFrame()
        f.clock = clock
        f.chosen = chosen
        let rank = ranks(chosen: chosen)

        f.press = easeOut(seg(t, 1.30, 1.45)) * (1 - easeOutBack(seg(t, 1.45, 1.85), 1.2))
        f.pick = easeOut(seg(t, 1.45, 1.85)) * (1 - easeInOut(seg(t, 8.50, 8.90)))
        f.center = easeInOut(seg(t, 2.40, 3.30)) * (1 - easeInOut(seg(t, 8.50, 9.30)))
        f.vanish = easeIn(seg(t, 3.10, 3.50)) * (1 - easeOut(seg(t, 8.45, 8.65)))
        f.flip = easeIn(seg(t, 3.50, 4.00)) * (1 - easeOut(seg(t, 8.20, 8.55)))
        f.line = easeInOut(seg(t, 4.00, 4.25)) * (1 - easeInOut(seg(t, 7.95, 8.20)))
        f.open = easeOutBack(seg(t, 4.25, 4.90), 1.1) * (1 - easeInOut(seg(t, 7.45, 7.95)))
        f.halo = easeOut(seg(t, 1.45, 1.85)) * (1 - f.flip) * (1 - easeInOut(seg(t, 8.50, 8.90)))
        for i in 0 ..< 9 where i != chosen {
            let a = 1.70 + 0.045 * Double(rank[i] - 1)
            let b = 8.60 + 0.04 * Double(rank[i] - 1)
            f.gather[i] = easeInOut(seg(t, a, a + 0.60)) * (1 - easeOutBack(seg(t, b, b + 0.55), 1.4))
        }
        f.ranks = rank

        f.titleGrid = (1 - easeIn(seg(t, 1.70, 2.00))) + easeOut(seg(t, 9.00, 9.40))
        f.titleGridY = t < 5 ? -8 * easeIn(seg(t, 1.70, 2.00)) : -8 * (1 - easeOut(seg(t, 9.00, 9.40)))
        f.titleField = easeOut(seg(t, 4.50, 4.90)) * (1 - easeIn(seg(t, 7.40, 7.70)))
        f.titleFieldY = t < 7.70 ? 8 * (1 - easeOut(seg(t, 4.50, 4.90))) : 8
        f.askAlpha = (1 - easeIn(seg(t, 1.70, 2.00))) + easeOut(seg(t, 4.70, 5.05)) * (1 - easeIn(seg(t, 7.40, 7.60)))
            + easeOut(seg(t, 9.10, 9.45))
        f.rings = easeOut(seg(t, 4.66, 4.90)) * (1 - easeIn(seg(t, 7.45, 7.60)))

        let n = typedOverride ?? typed(at: t)
        f.typed = n
        f.textAlpha = 1 - easeIn(seg(t, 7.30, 7.50))
        f.toPlaceholder = easeInOut(seg(t, 4.75, 5.00)) * (1 - easeIn(seg(t, 7.30, 7.50)))
        f.placeholder = n == 0 ? easeInOut(seg(t, 4.75, 5.00)) * (1 - easeIn(seg(t, 7.30, 7.45)))
            : 0
        // Caret: steady while it is the line, blinking while it waits, steady while typing and closing.
        var caret = 1.0
        if t >= 4.90 && t < 7.30 {
            if let b = blinkClock {
                caret = blink(b)
            } else if t < 5.60 {
                caret = blink(t - 4.90)
            } else if t >= 6.95 {
                caret = blink(t - 6.95)
            }
            caret = max(caret, seg(t, 7.20, 7.30))
        }
        f.caret = caret
        f.lineAlpha = seg(f.flip, 0.86, 1)

        // The grid sheen crosses once while the screen rests.
        f.sheen = t < 1.15 ? seg(t, 0.15, 1.15) : 0
        return f
    }

    /// Reduced motion: the grid (s = 0) or the open field (s = 1), crossfaded.
    static func reduced(_ s: Double, chosen: Int) -> OracleGlassGridFrame {
        var f = frame(0, chosen: chosen, clock: 0)
        let open = frame(5.2, chosen: chosen, typed: 0, blinkClock: 0.1, clock: 0)
        f.cardAlpha = 1 - s
        f.titleGrid = 1 - s
        f.titleField = s
        f.titleFieldY = 0
        f.open = open.open
        f.line = 1
        f.lineAlpha = 1
        f.fieldAlpha = s
        f.placeholder = 1
        f.toPlaceholder = 1
        f.rings = 1
        f.caret = 1
        f.sheen = 0
        f.reducedField = true
        return f
    }

    // MARK: Icons — 56 × 56 boxes, absolute SVG paths (M L C Z), drawn with 1.3 pt round strokes

    struct Mark {
        let d: String
        var fill = false
        var glow = false
        var k: Double = 0      // shimmer cycles per loop (0 = steady)
        var ph: Double = 0     // shimmer phase
        var dim: Double = 1    // base opacity
    }

    static let icons: [[Mark]] = [
        // School: an open book under a fan of rays that ripple left to right.
        [
            Mark(d: "M28 41C22 37 14 37 8 39L8 23C14 21 22 21 28 25Z"),
            Mark(d: "M28 41C34 37 42 37 48 39L48 23C42 21 34 21 28 25Z"),
            Mark(d: "M28 25L28 41"),
            Mark(d: "M19.5 19.9L13.9 17.9", glow: true, k: 4, ph: 0.0),
            Mark(d: "M21.1 17.2L16.5 13.4", glow: true, k: 4, ph: -0.6),
            Mark(d: "M23.5 15.2L20.5 10", glow: true, k: 4, ph: -1.2),
            Mark(d: "M26.4 14.1L25.4 8.2", glow: true, k: 4, ph: -1.8),
            Mark(d: "M29.6 14.1L30.6 8.2", glow: true, k: 4, ph: -2.4),
            Mark(d: "M32.5 15.2L35.5 10", glow: true, k: 4, ph: -3.0),
            Mark(d: "M34.9 17.2L39.5 13.4", glow: true, k: 4, ph: -3.6),
            Mark(d: "M36.5 19.9L42.1 17.9", glow: true, k: 4, ph: -4.2),
        ],
        // Friends and Family: a constellation of four stars.
        [
            Mark(d: "M14 36L28 15L42 32L24 46Z", dim: 0.45),
            Mark(d: "M14 32L14.8 35.2L18 36L14.8 36.8L14 40L13.2 36.8L10 36L13.2 35.2Z", fill: true, glow: true, k: 3, ph: 0.4),
            Mark(d: "M28 10.5L28.8 14.2L32.5 15L28.8 15.8L28 19.5L27.2 15.8L23.5 15L27.2 14.2Z", fill: true, glow: true, k: 4, ph: 2.1),
            Mark(d: "M42 28L42.8 31.2L46 32L42.8 32.8L42 36L41.2 32.8L38 32L41.2 31.2Z", fill: true, glow: true, k: 5, ph: 4.0),
            Mark(d: "M24 43.4L24.6 45.4L26.6 46L24.6 46.6L24 48.6L23.4 46.6L21.4 46L23.4 45.4Z", fill: true, glow: true, k: 6, ph: 1.3),
        ],
        // Love: a heart around a pulsing inner heart, two sparkles.
        [
            Mark(d: "M28 46C14 36 9 28 13 21C17 14 25 15 28 21C31 15 39 14 43 21C47 28 42 36 28 46Z"),
            Mark(d: "M28 36C22 32 20 28 22 25C24 22 27 23 28 26C29 23 32 22 34 25C36 28 34 32 28 36Z", fill: true, glow: true, k: 5, ph: 0.9),
            Mark(d: "M12 8.5L12.6 11.4L15.5 12L12.6 12.6L12 15.5L11.4 12.6L8.5 12L11.4 11.4Z", fill: true, glow: true, k: 3, ph: 2.6),
            Mark(d: "M45 8L45.6 10.4L48 11L45.6 11.6L45 14L44.4 11.6L42 11L44.4 10.4Z", fill: true, glow: true, k: 4, ph: 5.0),
        ],
        // Career: three arcs that light outward in turn over a four-point star.
        [
            Mark(d: "M19 40L19.2 38.2L19.7 36.6L20.5 35L21.6 33.6L23 32.5L24.6 31.7L26.2 31.2L28 31L29.8 31.2L31.4 31.7L33 32.5L34.4 33.6L35.5 35L36.3 36.6L36.8 38.2L37 40", glow: true, k: 3, ph: 0.0),
            Mark(d: "M13 40L13.3 37.1L14.1 34.3L15.5 31.7L17.4 29.4L19.7 27.5L22.3 26.1L25.1 25.3L28 25L30.9 25.3L33.7 26.1L36.3 27.5L38.6 29.4L40.5 31.7L41.9 34.3L42.7 37.1L43 40", glow: true, k: 3, ph: -0.9),
            Mark(d: "M7 40L7.4 35.9L8.6 32L10.5 28.3L13.2 25.2L16.3 22.5L20 20.6L23.9 19.4L28 19L32.1 19.4L36 20.6L39.7 22.5L42.8 25.2L45.5 28.3L47.4 32L48.6 35.9L49 40", glow: true, k: 3, ph: -1.8),
            Mark(d: "M28 29.5L29.1 33.9L33.5 35L29.1 36.1L28 40.5L26.9 36.1L22.5 35L26.9 33.9Z", fill: true, glow: true, k: 5, ph: 1.5),
        ],
        // Life's Greatest Mysteries: an eye with lashes and a glowing pupil.
        [
            Mark(d: "M7 30C15 19 41 19 49 30C41 41 15 41 7 30Z"),
            Mark(d: "M34 30C34 33.3 31.3 36 28 36C24.7 36 22 33.3 22 30C22 26.7 24.7 24 28 24C31.3 24 34 26.7 34 30Z"),
            Mark(d: "M30.6 30C30.6 31.4 29.4 32.6 28 32.6C26.6 32.6 25.4 31.4 25.4 30C25.4 28.6 26.6 27.4 28 27.4C29.4 27.4 30.6 28.6 30.6 30Z", fill: true, glow: true, k: 4, ph: 3.1),
            Mark(d: "M19 21L16.5 15.5", glow: true, k: 6, ph: 0.2),
            Mark(d: "M28 19L28 12.5", glow: true, k: 6, ph: -0.8),
            Mark(d: "M37 21L39.5 15.5", glow: true, k: 6, ph: -1.6),
        ],
        // My Future: a glowing sun on a horizon under flickering rays.
        [
            Mark(d: "M6 40L50 40"),
            Mark(d: "M35 36C35 39.9 31.9 43 28 43C24.1 43 21 39.9 21 36C21 32.1 24.1 29 28 29C31.9 29 35 32.1 35 36Z", fill: true, glow: true, k: 3, ph: 0.6),
            Mark(d: "M16.2 33.9L9.3 32.7", glow: true, k: 5, ph: 0.0),
            Mark(d: "M17.6 30L11.5 26.5", glow: true, k: 5, ph: 2.4),
            Mark(d: "M20.3 26.8L15.8 21.4", glow: true, k: 5, ph: 0.9),
            Mark(d: "M23.9 24.7L21.5 18.1", glow: true, k: 5, ph: 3.6),
            Mark(d: "M28 24L28 17", glow: true, k: 5, ph: 1.7),
            Mark(d: "M32.1 24.7L34.5 18.1", glow: true, k: 5, ph: 4.4),
            Mark(d: "M35.7 26.8L40.2 21.4", glow: true, k: 5, ph: 0.3),
            Mark(d: "M38.4 30L44.5 26.5", glow: true, k: 5, ph: 2.9),
            Mark(d: "M39.8 33.9L46.7 32.7", glow: true, k: 5, ph: 5.2),
        ],
        // Myself: a hand mirror with a glint.
        [
            Mark(d: "M28 7C35 7 39 14 39 21C39 28 35 35 28 35C21 35 17 28 17 21C17 14 21 7 28 7Z"),
            Mark(d: "M28 35L28 50"),
            Mark(d: "M24.5 38L31.5 38"),
            Mark(d: "M21.5 17C22.3 14.2 24 12.4 26.4 11.6", glow: true, k: 4, ph: 1.0),
            Mark(d: "M45 8L45.6 10.4L48 11L45.6 11.6L45 14L44.4 11.6L42 11L44.4 10.4Z", fill: true, glow: true, k: 3, ph: 3.7),
        ],
        // Style: a hanger with two sparkles.
        [
            Mark(d: "M28 22C28 18 32 17.5 32 14C32 11 29.5 10 27.5 10.6C26 11.1 25 12.3 25 13.6"),
            Mark(d: "M28 22L8 36L48 36Z"),
            Mark(d: "M44 12L44.6 14.4L47 15L44.6 15.6L44 18L43.4 15.6L41 15L43.4 14.4Z", fill: true, glow: true, k: 4, ph: 0.5),
            Mark(d: "M12 19.4L12.5 21.5L14.6 22L12.5 22.5L12 24.6L11.5 22.5L9.4 22L11.5 21.5Z", fill: true, glow: true, k: 6, ph: 2.8),
        ],
        // Lunch: fork and knife.
        [
            Mark(d: "M17 9L17 21C17 25 19 27 21 27L21 49"),
            Mark(d: "M21 9L21 21"),
            Mark(d: "M25 9L25 21C25 25 23 27 21 27"),
            Mark(d: "M36 49L36 9C41 13 42 22 40 29L36 29"),
            Mark(d: "M41 8.2L41.5 10.5L43.8 11L41.5 11.5L41 13.8L40.5 11.5L38.2 11L40.5 10.5Z", fill: true, glow: true, k: 5, ph: 4.2),
        ],
    ]

    /// Opacity of one mark at shimmer clock `c` (glowing marks breathe between 0.45 and 1).
    static func markAlpha(_ m: Mark, clock c: Double) -> Double {
        guard m.k > 0 else { return m.dim }
        return m.dim * (0.45 + 0.55 * wave(c, k: m.k, phase: m.ph))
    }

    // MARK: Path parsing (absolute M, L, C, Z)

    static func path(_ d: String) -> Path {
        var p = Path()
        var nums: [CGFloat] = []
        var cmd: Character = "M"
        var token = ""
        func flushToken() {
            if !token.isEmpty, let v = Double(token) { nums.append(CGFloat(v)) }
            token = ""
        }
        func flushCommand() {
            switch cmd {
            case "M":
                var i = 0
                while i + 1 < nums.count {
                    let pt = CGPoint(x: nums[i], y: nums[i + 1])
                    if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
                    i += 2
                }
            case "L":
                var i = 0
                while i + 1 < nums.count { p.addLine(to: CGPoint(x: nums[i], y: nums[i + 1])); i += 2 }
            case "C":
                var i = 0
                while i + 5 < nums.count {
                    p.addCurve(to: CGPoint(x: nums[i + 4], y: nums[i + 5]),
                               control1: CGPoint(x: nums[i], y: nums[i + 1]),
                               control2: CGPoint(x: nums[i + 2], y: nums[i + 3]))
                    i += 6
                }
            case "Z":
                p.closeSubpath()
            default:
                break
            }
            nums = []
        }
        for ch in d {
            if ch.isLetter {
                flushToken()
                flushCommand()
                cmd = ch
            } else if ch == " " || ch == "," {
                flushToken()
            } else if ch == "-" {
                flushToken()
                token = "-"
            } else {
                token.append(ch)
            }
        }
        flushToken()
        flushCommand()
        return p
    }

    static let iconPaths: [[Path]] = icons.map { $0.map { path($0.d) } }
}

struct OracleGlassGridFrame {
    var clock: Double = 0          // lava and shimmer time, 0 ..< loop
    var chosen = OracleGlassGridModel.autoChoice
    var ranks: [Int] = Array(repeating: 0, count: 9)
    var press: Double = 0          // the chosen card's press dip
    var pick: Double = 0           // glass → white
    var gather: [Double] = Array(repeating: 0, count: 9)
    var center: Double = 0         // the stack moving to the field's centre
    var vanish: Double = 0         // the other cards collapsing and fading
    var flip: Double = 0           // 0 face on … 1 edge on
    var line: Double = 0           // the edge-on line shrinking into the caret
    var open: Double = 0           // the field opening out of the caret
    var halo: Double = 0
    var lineAlpha: Double = 0
    var caret: Double = 1
    var typed = 0
    var textAlpha: Double = 1
    var placeholder: Double = 0
    var toPlaceholder: Double = 0
    var rings: Double = 0
    var titleGrid: Double = 1
    var titleGridY: Double = 0
    var titleField: Double = 0
    var titleFieldY: Double = 8
    var askAlpha: Double = 1
    var sheen: Double = 0
    var cardAlpha: Double = 1      // reduced motion crossfade
    var fieldAlpha: Double = 1
    var reducedField = false       // reduced motion: the field shows without the flip
}

/// Plays the story, hands it to the finger, and returns to the loop 2 s after the last touch.
final class OracleGlassGridDriver {
    typealias M = OracleGlassGridModel

    enum Phase { case opening, hold, closing, rest }

    private var epoch: Double?          // lava, shimmer and the self-playing story
    private var storyEpoch: Double?
    private(set) var manual = false
    private var phase: Phase = .rest
    private var tm0: Double = 0
    private var phaseStart: Double = 0
    private var chosen = M.autoChoice
    private var frozenTyped = 0
    private var lastTouch: Double = -100
    // Reduced motion: 0 grid, 1 field, crossfaded in 0.25 s.
    private var shown: Double = 0
    private var fadeFrom: Double = 0
    private var fadeStart: Double = -100

    func reset() {
        epoch = nil
        storyEpoch = nil
        manual = false
        phase = .rest
        shown = 0
        fadeFrom = 0
        fadeStart = -100
    }

    private func clock(_ now: Double) -> Double {
        if epoch == nil { epoch = now }
        return (now - (epoch ?? now)).truncatingRemainder(dividingBy: M.loop)
    }

    private func storyTime(_ now: Double) -> Double {
        if storyEpoch == nil { storyEpoch = now }
        return (now - (storyEpoch ?? now)).truncatingRemainder(dividingBy: M.loop)
    }

    /// Story time of the hand-driven story.
    private func manualTime(_ now: Double, auto: Bool) -> Double {
        switch phase {
        case .opening:
            let t = min(tm0 + (now - phaseStart), 4.90)
            if t >= 4.90 {
                phase = .hold
                tm0 = 4.90
                phaseStart = now
                frozenTyped = 0
            }
            return t
        case .hold:
            if auto && now - lastTouch > 2 && now - phaseStart > 2 {
                startClosing(at: now)
                return 7.30
            }
            return tm0
        case .closing:
            let t = min(tm0 + (now - phaseStart), 9.45)
            if t >= 9.45 { phase = .rest }
            return t
        case .rest:
            return 0
        }
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> OracleGlassGridFrame {
        let c = clock(now)
        if reduced { return M.reduced(shownAmount(at: now), chosen: chosen) }
        if manual {
            let t = manualTime(now, auto: auto)
            if phase == .rest && auto && now - lastTouch > 2 {
                manual = false
                storyEpoch = now
                return M.frame(0, chosen: M.autoChoice, clock: c)
            }
            if phase == .rest { return M.frame(0, chosen: chosen, clock: c) }
            let blinkClock: Double? = phase == .hold ? now - phaseStart : nil
            return M.frame(t, chosen: chosen, typed: frozenTyped, blinkClock: blinkClock, clock: c)
        }
        if !auto { return M.frame(0, chosen: chosen, clock: c) }
        return M.frame(storyTime(now), chosen: M.autoChoice, clock: c)
    }

    private func startClosing(at now: Double) {
        phase = .closing
        tm0 = 7.30
        phaseStart = now
    }

    /// Whether the screen shows the open field (taps then go to the back arrow or a swipe).
    func isOpen(at now: Double, auto: Bool) -> Bool {
        if manual { return phase == .hold || phase == .opening }
        guard auto else { return false }
        let t = storyTime(now)
        return t >= 1.30 && t < 7.30
    }

    func isRest(at now: Double, auto: Bool) -> Bool {
        if manual { return phase == .rest }
        guard auto else { return true }
        let t = storyTime(now)
        return t < 1.30 || t >= 9.45
    }

    /// A tap on card `i` at rest opens it. Returns true when it did.
    func pick(_ i: Int, at now: Double) -> Bool {
        lastTouch = now
        manual = true
        chosen = i
        phase = .opening
        tm0 = 1.30
        phaseStart = now
        frozenTyped = 0
        return true
    }

    /// Any touch while the story plays takes it over where it is.
    func takeOver(at now: Double, auto: Bool) {
        lastTouch = now
        guard !manual, auto else { return }
        let t = storyTime(now)
        manual = true
        chosen = M.autoChoice
        frozenTyped = M.typed(at: t)
        if t >= 1.30 && t < 4.90 {
            phase = .opening
            tm0 = t
            phaseStart = now
        } else if t >= 4.90 && t < 7.30 {
            phase = .hold
            tm0 = t
            phaseStart = now
        } else if t >= 7.30 && t < 9.45 {
            phase = .closing
            tm0 = t
            phaseStart = now
        } else {
            phase = .rest
        }
    }

    /// Back arrow or swipe down on the open field. Returns true when it folds.
    func close(at now: Double) -> Bool {
        lastTouch = now
        guard manual, phase == .hold else { return false }
        startClosing(at: now)
        return true
    }

    func touched(at now: Double) { lastTouch = now }

    // Reduced motion

    func shownAmount(at now: Double) -> Double {
        let k = M.seg(now, fadeStart, fadeStart + 0.25)
        return fadeFrom + (shown - fadeFrom) * k
    }

    func setReduced(open: Bool, chosen i: Int?, at now: Double) {
        fadeFrom = shownAmount(at: now)
        shown = open ? 1 : 0
        if let i { chosen = i }
        fadeStart = now
    }

    var reducedOpen: Bool { shown > 0.5 }
    var busy: Bool { manual }
}
