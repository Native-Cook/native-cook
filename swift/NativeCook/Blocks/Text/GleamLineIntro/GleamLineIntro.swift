import CoreText
import SwiftUI
import UIKit

/*
 Gleam Line Intro — the opening of a journaling app: short lines of a serif on black, each word
 arriving with a gleam, then fading for the next beat.

 Text: Newsreader (a static instance at weight 360, optical size 60), 26 pt, left aligned at 32 pt with
 a 1.3 line height, the block centred on 46 % of the height. Settled words are warm grey #D8D2C8;
 italic words are the emphasis. Three thin progress segments at the top fill with the beats; a hint
 ("Tap to continue") sits at the bottom during the first two beats, a white "Begin writing" pill on
 the last one.

 A word arriving, p = (t − its start) / 0.7 s:
   opacity     smoothstep(0, 0.6, p)
   blur        5 · (1 − easeOutCubic(p / 0.8)) (a Gaussian sigma)
   drift       rises 7 pt into place, easeOutCubic over p = 0 … 1
   gleam       g = exp(−((p − 0.42) / 0.2)²): its colour runs to white and a white glow (sigma 8,
               60 % · g) blooms behind it, so with the words staggered a highlight sweeps along the line.
 A word leaving over d seconds, q = (t − its fade) / d: opacity × (1 − smoothstep(q)), blur + 4·q,
 drifting 6 pt up.

 autoPlay timeline (loop = 10 s); the loop starts and ends on the last beat, fully shown:
   0.0 … 1.2   rest: "Keep them here, / one line a day." and the Begin pill.
   1.2 … 1.8   that beat and the pill fade out (0.6 s); the progress segments empty.
   1.9 … 4.9   beat 1, "Some days are loud. / Most of them go unwritten.", word by word every 0.16 s;
               it fades out at 4.9 (0.6 s). The hint fades in at 1.6 and out at 8.6 (0.4 s each).
   5.5 … 8.65  beat 2, three lines arriving at 5.5, 6.3 and 7.1 (a word every 0.12 s), dimmed away line
               by line from 7.9, 8.05 and 8.2 (0.45 s each).
   8.7 … 9.94  beat 3 again, a word every 0.09 s; the pill fades in from 9.3 to 9.8. t = 10 = t = 0.
   Segment k fills linearly while its beat plays: 1.9 → 4.9, 5.5 → 8.6, 8.7 → 9.9.

 Touch: a tap skips to the next beat: what is on screen freezes and fades out in 0.35 s, then the
 next beat arrives as in the loop and holds. 2 s after the last tap the last beat is brought back
 the same way (if it is not already showing) and the loop carries on from rest. A light haptic per tap.
 Reduced motion: the last beat stands still and the loop is off; taps still step through the beats,
 the words fading in without blur, drift or gleam.
 */

struct GleamLineIntro: View {
    var beats = [
        "Some days are loud.\nMost of them go *unwritten.*",
        "A thought on the train.\nA name you almost forgot.\nThe light at six o’clock.",
        "Keep them here,\n*one line a day.*",
    ]
    var hint = "Tap to continue"
    var action = "Begin writing"
    var autoPlay = false
    var onAction: (() -> Void)? = nil

    static let loop: Double = 10
    static let fontSize: Double = 26
    static let settled = (r: 0.847, g: 0.824, b: 0.784) // #D8D2C8

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var autoStart: Date? = nil
    /// A beat shown by hand: which, and when it was asked for.
    @State private var manual: (beat: Int, date: Date)? = nil
    /// What was on screen when the last change was asked for, frozen at that loop time, and when.
    @State private var outgoing: (e: Double, date: Date)? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = GleamLineIntroLayout(beats: beats, size: size)
            // Read here so that a change re-renders the timeline's content.
            let state = (autoStart, manual, outgoing)
            let still = reduceMotion && (state.1 == nil || Date.now.timeIntervalSince(state.1!.date) > 3)
            TimelineView(.animation(paused: still)) { timeline in
                let frame = frame(at: timeline.date, autoStart: state.0, manual: state.1, outgoing: state.2)
                GleamLineIntroScreen(layout: layout, frame: frame, insets: insets, size: size,
                                     hint: hint, action: action, onAction: onAction)
            }
            .contentShape(Rectangle())
            .onTapGesture { tap(layout: layout) }
            .ignoresSafeArea()
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            haptic.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Time

    /// The loop time that shows what is asked for, and the frozen outgoing time with its fade.
    private func frame(at date: Date, autoStart: Date?, manual: (beat: Int, date: Date)?,
                       outgoing: (e: Double, date: Date)?) -> GleamLineIntroFrame {
        var e = 0.0
        if let manual {
            let b = GleamLineIntroTimeline.beats[manual.beat]
            e = b.reveal + max(0, date.timeIntervalSince(manual.date) - 0.35) - 0.001
            e = min(e, b.hold)
        } else if let autoStart, date >= autoStart, !reduceMotion {
            e = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
        }
        var out: (e: Double, k: Double)? = nil
        if let outgoing {
            let k = GleamLineIntroMath.smoothstep(0, 0.35, date.timeIntervalSince(outgoing.date))
            if k < 1 { out = (outgoing.e, 1 - k) }
        }
        return GleamLineIntroFrame(e: e, outgoing: out, reduced: reduceMotion)
    }

    private func currentE(at date: Date) -> Double {
        frame(at: date, autoStart: autoStart, manual: manual, outgoing: nil).e
    }

    private func show(beat: Int, at date: Date) {
        outgoing = (currentE(at: date), date)
        autoStart = nil
        manual = (beat, date)
    }

    private func tap(layout: GleamLineIntroLayout) {
        let now = Date.now
        haptic.impactOccurred()
        show(beat: GleamLineIntroTimeline.next(after: currentE(at: now)), at: now)
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            if manual?.beat != 2 {
                show(beat: 2, at: .now)
                try? await Task.sleep(for: .seconds(1.7))
                guard !Task.isCancelled else { return }
            }
            outgoing = nil
            manual = nil
            autoStart = .now
        }
    }
}

// MARK: - Timeline

struct GleamLineIntroFrame: Equatable {
    var e: Double
    var outgoing: (e: Double, k: Double)?
    var reduced: Bool

    static func == (a: Self, b: Self) -> Bool {
        a.e == b.e && a.outgoing?.e == b.outgoing?.e && a.outgoing?.k == b.outgoing?.k && a.reduced == b.reduced
    }
}

/// The beats' times in the loop.
enum GleamLineIntroTimeline {
    struct Beat {
        /// When its first word starts, and the latest loop time that still shows it whole.
        var reveal: Double
        var hold: Double
        /// When each line starts, and the gap between its words.
        var lineStarts: [Double]
        var stagger: Double
        /// When each line fades, and over how long; the last beat fades at 1.2 of the next loop.
        var fades: [Double]
        var fadeSpan: Double
    }

    static let wordSpan = 0.7
    static let beats = [
        Beat(reveal: 1.9, hold: 4.85, lineStarts: [1.9], stagger: 0.16, fades: [4.9], fadeSpan: 0.6),
        Beat(reveal: 5.5, hold: 7.85, lineStarts: [5.5, 6.3, 7.1], stagger: 0.12, fades: [7.9, 8.05, 8.2], fadeSpan: 0.45),
        Beat(reveal: 8.7, hold: 9.999, lineStarts: [8.7], stagger: 0.09, fades: [1.2], fadeSpan: 0.6),
    ]

    /// The beat after the one on screen at loop time `e`.
    static func next(after e: Double) -> Int {
        if e < 1.2 { return 0 }
        if e < 4.9 { return 1 }
        if e < 8.7 { return 2 }
        return 0
    }

    /// A word's look at loop time `e`: opacity, blur (sigma), vertical drift and gleam.
    struct Look {
        var alpha = 0.0
        var blur = 0.0
        var dy = 0.0
        var gleam = 0.0
    }

    /// A beat with one start counts its words through the beat (`index`); one with a start per line
    /// counts them through the line (`place`).
    static func look(beat: Int, line: Int, index: Int, place: Int, e: Double, reduced: Bool) -> Look {
        let b = beats[beat]
        let start: Double
        let fade: Double
        if b.lineStarts.count == 1 {
            start = b.lineStarts[0] + Double(index) * b.stagger
            fade = b.fades[0]
        } else {
            start = b.lineStarts[min(line, b.lineStarts.count - 1)] + Double(place) * b.stagger
            fade = b.fades[min(line, b.fades.count - 1)]
        }
        var s = start, f = fade
        if beat == 2 {
            // The last beat is whole at the start of the loop and fades at 1.2; later it arrives again.
            if e < 5 { s -= GleamLineIntro.loop } else { f += GleamLineIntro.loop }
        }
        let p = (e - s) / wordSpan
        if p <= 0 { return Look() }
        var look = Look()
        look.alpha = GleamLineIntroMath.smoothstep(0, 0.6, p)
        if !reduced {
            look.blur = 5 * (1 - GleamLineIntroMath.easeOutCubic(min(1, p / 0.8)))
            look.dy = 7 * (1 - GleamLineIntroMath.easeOutCubic(min(1, p)))
            look.gleam = p < 1.2 ? exp(-pow((p - 0.42) / 0.2, 2)) : 0
        }
        if e >= f {
            let q = (e - f) / b.fadeSpan
            if q >= 1 { return Look() }
            let k = GleamLineIntroMath.smoothstep(0, 1, q)
            look.alpha *= 1 - k
            if !reduced {
                look.blur += 4 * q
                look.dy -= 6 * k
            }
        }
        return look
    }

    /// The chrome at loop time `e`: the segments' fills, the hint's and the pill's opacity.
    static func chrome(e: Double) -> (fills: [Double], hint: Double, pill: Double) {
        var fills = [0.0, 0.0, 0.0]
        if e < 1.2 {
            fills = [1, 1, 1]
        } else if e < 1.8 {
            let k = 1 - GleamLineIntroMath.smoothstep(1.2, 1.8, e)
            fills = [k, k, k]
        } else {
            let windows = [(1.9, 4.9), (5.5, 8.6), (8.7, 9.9)]
            fills = windows.map { min(1, max(0, (e - $0.0) / ($0.1 - $0.0))) }
        }
        let hint = GleamLineIntroMath.smoothstep(1.6, 2.0, e) * (1 - GleamLineIntroMath.smoothstep(8.6, 9.0, e))
        let pill = e < 5 ? 1 - GleamLineIntroMath.smoothstep(1.2, 1.6, e) : GleamLineIntroMath.smoothstep(9.3, 9.8, e)
        return (fills, hint, pill)
    }
}

enum GleamLineIntroMath {
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(1, max(0, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    static func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - min(1, max(0, t)), 3) }
}

// MARK: - Layout

/// Every word of every beat: its outline, where its baseline starts, its line and its place.
struct GleamLineIntroLayout {
    struct Word {
        var path: Path
        var x: Double
        var baseline: Double
        var line: Int
        /// The word's place in its beat, and in its line.
        var index: Int
        var place: Int
    }

    let beats: [[Word]]

    init(beats texts: [String], size: CGSize) {
        let size30 = GleamLineIntro.fontSize
        let regular = GleamLineIntroFont.ctFont(italic: false, size: size30)
        let italic = GleamLineIntroFont.ctFont(italic: true, size: size30)
        let ascent = Double(CTFontGetAscent(regular)), descent = Double(CTFontGetDescent(regular))
        let lineHeight = 1.3 * size30
        let left = 32.0
        let maxWidth = Double(size.width) - 64
        let space = GleamLineIntroFont.advance(" ", font: regular)
        var all: [[Word]] = []
        for text in texts {
            // Words with their style; *…* marks italics. Lines wrap greedily if a line is too long.
            var lines: [[(String, Bool, Double)]] = []
            for raw in text.components(separatedBy: "\n") {
                var line: [(String, Bool, Double)] = []
                var width = 0.0
                var inItalic = false
                for token in raw.split(separator: " ") {
                    var w = String(token)
                    let opens = w.hasPrefix("*")
                    if opens { inItalic = true; w.removeFirst() }
                    let italicWord = inItalic
                    if w.hasSuffix("*") { inItalic = false; w.removeLast() }
                    let a = GleamLineIntroFont.advance(w, font: italicWord ? italic : regular)
                    if !line.isEmpty && width + space + a > maxWidth {
                        lines.append(line)
                        line = []
                        width = 0
                    }
                    width += (line.isEmpty ? 0 : space) + a
                    line.append((w, italicWord, a))
                }
                lines.append(line)
            }
            let top = Double(size.height) * 0.46 - Double(lines.count) * lineHeight / 2
            var words: [Word] = []
            var count = 0
            for (li, line) in lines.enumerated() {
                let baseline = top + Double(li) * lineHeight + (lineHeight - ascent - descent) / 2 + ascent
                var x = left
                for (wi, item) in line.enumerated() {
                    let path = GleamLineIntroFont.path(item.0, font: item.1 ? italic : regular)
                    words.append(Word(path: path, x: x, baseline: baseline, line: li, index: count, place: wi))
                    count += 1
                    x += item.2 + space
                }
            }
            all.append(words)
        }
        beats = all
    }
}

// MARK: - Screen

struct GleamLineIntroScreen: View {
    let layout: GleamLineIntroLayout
    let frame: GleamLineIntroFrame
    let insets: EdgeInsets
    let size: CGSize
    let hint: String
    let action: String
    let onAction: (() -> Void)?

    var body: some View {
        let now = GleamLineIntroTimeline.chrome(e: frame.e)
        let was = frame.outgoing.map { GleamLineIntroTimeline.chrome(e: $0.e) }
        let k = frame.outgoing?.k ?? 0
        let fills = (0..<3).map { i in now.fills[i] * (1 - k) + (was?.fills[i] ?? 0) * k }
        let hintAlpha = now.hint * (1 - k) + (was?.hint ?? 0) * k
        let pillAlpha = now.pill * (1 - k) + (was?.pill ?? 0) * k
        ZStack(alignment: .topLeading) {
            Color.black
            Canvas { ctx, _ in
                if let out = frame.outgoing {
                    draw(&ctx, e: out.e, fade: out.k)
                }
                draw(&ctx, e: frame.e, fade: 1)
            }
            .frame(width: size.width, height: size.height)
            .accessibilityHidden(true)

            // Progress segments.
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { i in
                    Capsule().fill(Color.white.opacity(0.16))
                        .overlay(alignment: .leading) {
                            GeometryReader { g in
                                Capsule().fill(Color.white.opacity(0.85)).frame(width: g.size.width * fills[i])
                            }
                        }
                        .frame(height: 2)
                }
            }
            .padding(.horizontal, 32)
            .frame(width: size.width)
            .offset(y: insets.top + 14)

            Text(hint)
                .font(.system(size: 13, weight: .medium))
                .tracking(0.3)
                .foregroundStyle(Color(red: 0.541, green: 0.522, blue: 0.490))
                .frame(width: size.width)
                .offset(y: size.height - max(insets.bottom, 20) - 16 - 28 - 9)
                .opacity(hintAlpha)

            Button {
                onAction?()
            } label: {
                Text(action)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.black)
                    .frame(width: size.width - 64, height: 56)
                    .background(Capsule().fill(Color.white))
            }
            .buttonStyle(.plain)
            .offset(x: 32, y: size.height - max(insets.bottom, 20) - 16 - 56)
            .opacity(pillAlpha)
            .allowsHitTesting(pillAlpha > 0.9)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func draw(_ ctx: inout GraphicsContext, e: Double, fade: Double) {
        let s = GleamLineIntro.settled
        for (bi, words) in layout.beats.enumerated() {
            for w in words {
                let look = GleamLineIntroTimeline.look(beat: bi, line: w.line, index: w.index, place: w.place, e: e, reduced: frame.reduced)
                let alpha = look.alpha * fade
                if alpha < 0.004 { continue }
                let path = w.path.offsetBy(dx: w.x, dy: w.baseline + look.dy)
                if look.gleam > 0.01 {
                    ctx.drawLayer { l in
                        l.addFilter(.blur(radius: 8))
                        l.opacity = 0.6 * look.gleam * alpha
                        l.fill(path, with: .color(.white))
                    }
                }
                let g = look.gleam
                let color = Color(red: s.r + (1 - s.r) * g, green: s.g + (1 - s.g) * g, blue: s.b + (1 - s.b) * g)
                if look.blur > 0.05 {
                    ctx.drawLayer { l in
                        l.addFilter(.blur(radius: look.blur))
                        l.opacity = alpha
                        l.fill(path, with: .color(color))
                    }
                } else {
                    var c = ctx
                    c.opacity = alpha
                    c.fill(path, with: .color(color))
                }
            }
        }
    }
}

// MARK: - Font

enum GleamLineIntroFont {
    static let registered: Void = {
        for name in ["GleamLineIntro-Newsreader", "GleamLineIntro-NewsreaderItalic"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    static func ctFont(italic: Bool, size: Double) -> CTFont {
        _ = registered
        return CTFontCreateWithName((italic ? "GleamLineIntro-NewsreaderItalic" : "GleamLineIntro-Newsreader") as CFString, size, nil)
    }

    static func line(_ text: String, font: CTFont) -> CTLine {
        let attributed = NSAttributedString(string: text, attributes: [kCTFontAttributeName as NSAttributedString.Key: font])
        return CTLineCreateWithAttributedString(attributed)
    }

    static func advance(_ text: String, font: CTFont) -> Double {
        Double(CTLineGetTypographicBounds(line(text, font: font), nil, nil, nil))
    }

    /// A word's outline, origin on the baseline at the left (y down).
    static func path(_ text: String, font: CTFont) -> Path {
        let cg = CGMutablePath()
        let runs = CTLineGetGlyphRuns(line(text, font: font)) as! [CTRun]
        for run in runs {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: count), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: count), &positions)
            let attributes = CTRunGetAttributes(run) as NSDictionary
            let runFont = attributes[kCTFontAttributeName as String].map { $0 as! CTFont } ?? font
            for i in 0..<count {
                var t = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: positions[i].x, ty: -positions[i].y)
                if let g = CTFontCreatePathForGlyph(runFont, glyphs[i], &t) { cg.addPath(g) }
            }
        }
        return Path(cg)
    }
}
