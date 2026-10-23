import CoreText
import SwiftUI

/*
 Accordion Letters: four rows of one repeated capital (Anybody Black), each row a fixed width shared by
 its five letters, which squeeze to slivers and stretch to slabs like the bellows of an accordion.

 A row's letter widths are proportional to exp(G·s·(2 − j)) for letters j = 0 … 4, with G = 0.8, so at
 s = +1 the first letter is 24.5 times as wide as the last and at s = −1 the other way round; the widths
 always add up to the row (minus 5 pt gaps).

 Timeline of the self-playing loop (`AccordionLetters.loop` = 7.2 s, six beats of 1.2 s):
   beat b shows letter A, B, C (b mod 3) in its palette: blue with pale grey, black with yellow,
   orange-red with black; palettes and letters cut hard at the beat's start.
   In beat b every row flips s from σ to −σ (σ = +1 on even beats, −1 on odd), row r starting at
   0.15 + 0.07·r s and taking 0.7 s on an ease-in-out cubic, so the flip ripples down the rows; each
   beat ends where the next begins. Beat 6 is beat 0 again: A, blue, wide on the left — the resting state.

 Touch: dragging across the screen pulls the bulge to the finger: the letter under it grows widest
 (log-width 2.4·exp(−((j − f)/1.1)²), f the finger's place in letters) and each row follows 0.07 s after
 the one above; the drag's shape blends in over 0.25 s. A tap plays the next beat now (cut and flip),
 the drag's shape giving way to it over 0.25 s.
 2 s after the last touch the loop starts over from its first frame and the drag's shape blends out over
 0.5 s. Reduced motion: the letters sit at equal widths and a tap cuts to the next letter and palette.
*/
struct AccordionLetters: View {
    var letters = ["A", "B", "C"]
    var kicker = "Width axis"
    var range = "12 – 300 %"
    var title = "Every letter,\nevery width."
    var hint = "Drag across the rows · tap for the next letter"
    /// Plays the accordion by itself, every `loop` seconds round.
    var autoPlay = false

    static let loop = M.beat * 6

    struct Palette {
        let back: Color
        let ink: Color
    }

    static let palettes = [
        Palette(back: Color(hex: 0x0a47e8), ink: Color(hex: 0xdcdcdc)),
        Palette(back: Color(hex: 0x0d0d0d), ink: Color(hex: 0xfadb0a)),
        Palette(back: Color(hex: 0xff4a1c), ink: Color(hex: 0x111111)),
    ]

    enum M {
        static let beat = 1.2
        static let flipAt = 0.15
        static let flip = 0.7
        static let rowLag = 0.07
        static let rows = 4
        static let count = 5
        static let gain = 0.8
        static let gap = 5.0
        static let margin = 16.0
        static let rowHeight = 110.0
        static let rowGap = 14.0
        static let bulge = 2.4
        static let bulgeWidth = 1.1
        static let blendIn = 0.25
        static let blendOut = 0.5
        static let idle = 2.0
    }

    /// What drives the rows: a beat and how far into it, or a touch.
    enum Mode {
        case loop
        /// A beat played by a tap, from `start`; it holds at its end.
        case beat(Int, start: Date)
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var glyphs = AccordionLettersGlyphs()
    @State private var loopStart = Date.now
    @State private var mode = Mode.loop
    @State private var blend = Tween(0)
    @State private var trail = AccordionLettersTrail()
    @State private var touches = 0
    @State private var dragging = false
    @State private var dragged = false
    /// The finger on screen has moved, so this touch is a drag, not a tap.
    @State private var swiping = false
    /// The beat a drag froze, and how far into it.
    @State private var frozen = (beat: 0, tau: 0.0)
    @State private var stillBeat = 0
    @State private var tick = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .rigid)
    @State private var dragHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var tickHaptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let blockTop = screen.height * 0.47 - Self.blockHeight / 2
            TimelineView(.animation(paused: reduceMotion || (!autoPlay && !dragging && blend.value(at: .now) == 0 && !isPlayingBeat))) { timeline in
                let (beat, tau) = beatAt(timeline.date)
                let palette = Self.palettes[beat % Self.palettes.count]
                ZStack(alignment: .topLeading) {
                    palette.back
                    Canvas { context, size in
                        draw(&context, size: size, top: blockTop, beat: beat, tau: tau, date: timeline.date, ink: palette.ink)
                    }
                    .accessibilityLabel(String(repeating: letters[beat % letters.count], count: M.count))
                    chrome(screen: screen, insets: insets, blockTop: blockTop, beat: beat, ink: palette.ink)
                }
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
            }
            .contentShape(Rectangle())
            .gesture(drag(width: screen.width))
        }
        .onAppear {
            loopStart = .now
            tapHaptic.prepare()
        }
        .task(id: touches) { await settle() }
    }

    static var blockHeight: Double { Double(M.rows) * M.rowHeight + Double(M.rows - 1) * M.rowGap }

    private var isPlayingBeat: Bool {
        if case .beat = mode { return true }
        return false
    }

    // MARK: Timeline

    /// The beat on screen and how far into it (0 … 1.2 s).
    private func beatAt(_ date: Date) -> (Int, Double) {
        if reduceMotion { return (stillBeat, M.beat) }
        if dragged { return frozen }
        switch mode {
        case let .beat(b, start):
            return (b, min(date.timeIntervalSince(start), M.beat))
        case .loop:
            guard autoPlay else { return (0, 0) }
            let e = date.timeIntervalSince(loopStart).truncatingRemainder(dividingBy: Self.loop)
            let b = Int(e / M.beat)
            return (b, e - Double(b) * M.beat)
        }
    }

    /// Log-widths of a row's letters for beat `b` at `tau`.
    static func loopShape(beat b: Int, tau: Double, row: Int) -> [Double] {
        let sigma = b % 2 == 0 ? 1.0 : -1.0
        let p = easeInOutCubic(min(max((tau - M.flipAt - M.rowLag * Double(row)) / M.flip, 0), 1))
        let s = sigma * (1 - 2 * p)
        let centre = Double(M.count - 1) / 2
        return (0..<M.count).map { M.gain * s * (centre - Double($0)) }
    }

    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    // MARK: Touch

    private func drag(width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .onChanged { value in
                let now = Date.now
                if !dragging {
                    dragging = true
                    touches += 1
                    tick = Int.min
                    swiping = false
                }
                guard !reduceMotion else { return }
                if hypot(value.translation.width, value.translation.height) > 8 { swiping = true }
                guard swiping else { return }
                if !dragged {
                    let (b, tau) = beatAt(now)
                    frozen = (b, tau)
                    dragged = true
                    blend.retarget(1, at: now, duration: M.blendIn, curve: Ease.out)
                    dragHaptic.impactOccurred()
                }
                let f = Self.fingerIndex(x: value.location.x, width: width)
                trail.add(f, at: now)
                let notch = Int(f.rounded())
                if notch != tick {
                    if tick != Int.min { tickHaptic.selectionChanged() }
                    tick = notch
                }
            }
            .onEnded { _ in
                let now = Date.now
                defer {
                    dragging = false
                    touches += 1
                }
                if reduceMotion {
                    stillBeat += 1
                    tapHaptic.impactOccurred()
                    return
                }
                if !swiping {
                    // A tap: the next beat, now; a drag's shape gives way to it.
                    let (b, _) = beatAt(now)
                    if dragged {
                        dragged = false
                        blend.retarget(0, at: now, duration: M.blendIn, curve: Ease.out)
                    }
                    mode = .beat(b + 1, start: now)
                    tapHaptic.impactOccurred()
                }
            }
    }

    /// The finger's place along a row, in letters (0 … 4).
    static func fingerIndex(x: Double, width: Double) -> Double {
        let fraction = (x - M.margin) / (width - 2 * M.margin)
        return min(max(fraction, 0), 1) * Double(M.count) - 0.5
    }

    /// 2 s after the last touch the loop starts over and a drag's shape blends out.
    private func settle() async {
        guard touches > 0, !dragging else { return }
        try? await Task.sleep(for: .seconds(M.idle))
        guard !Task.isCancelled, !dragging else { return }
        let now = Date.now
        loopStart = now
        mode = .loop
        stillBeat = 0
        if dragged {
            dragged = false
            blend.retarget(0, at: now, duration: M.blendOut, curve: .easeInOut)
        }
    }

    // MARK: Drawing

    private func draw(_ context: inout GraphicsContext, size: CGSize, top: Double, beat: Int, tau: Double, date: Date, ink: Color) {
        let letter = letters[beat % letters.count]
        guard let glyph = glyphs.glyph(letter) else { return }
        let k = reduceMotion ? 0 : blend.value(at: date)
        let rowWidth = size.width - 2 * M.margin - M.gap * Double(M.count - 1)
        for row in 0..<M.rows {
            var logs = reduceMotion ? Array(repeating: 0.0, count: M.count) : Self.loopShape(beat: beat, tau: tau, row: row)
            if k > 0 {
                let f = trail.value(at: date.addingTimeInterval(-M.rowLag * Double(row)))
                for j in 0..<M.count {
                    let d = (Double(j) - f) / M.bulgeWidth
                    logs[j] += (M.bulge * exp(-d * d) - logs[j]) * k
                }
            }
            let raw = logs.map { exp($0) }
            let total = raw.reduce(0, +)
            let baseline = top + Double(row) * (M.rowHeight + M.rowGap) + M.rowHeight
            var x = M.margin
            for j in 0..<M.count {
                let w = raw[j] / total * rowWidth
                let sx = w / glyph.box.width
                let sy = M.rowHeight / glyph.capHeight
                let transform = CGAffineTransform(a: sx, b: 0, c: 0, d: sy, tx: x - glyph.box.minX * sx, ty: baseline)
                context.fill(glyph.path.applying(transform), with: .color(ink))
                x += w + M.gap
            }
        }
    }

    // MARK: Chrome

    private func chrome(screen: CGSize, insets: EdgeInsets, blockTop: Double, beat: Int, ink: Color) -> some View {
        ZStack(alignment: .topLeading) {
            HStack {
                Text(kicker.uppercased())
                Spacer(minLength: 12)
                Text(range)
            }
            .font(.system(size: 12, weight: .semibold, design: .monospaced))
            .tracking(2)
            .foregroundStyle(ink.opacity(0.75))
            .lineLimit(1)
            .padding(.horizontal, 20)
            .frame(width: screen.width, height: 20)
            .offset(y: insets.top + 16)

            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .lastTextBaseline) {
                    Text(title)
                        .font(.system(size: 30, weight: .heavy))
                        .tracking(-0.6)
                        .lineSpacing(-2)
                        .foregroundStyle(ink)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 12)
                    HStack(spacing: 10) {
                        ForEach(Array(letters.enumerated()), id: \.offset) { index, l in
                            Text(l)
                                .font(.system(size: 15, weight: .bold, design: .monospaced))
                                .foregroundStyle(ink.opacity(index == beat % letters.count ? 1 : 0.35))
                        }
                    }
                }
                Text(hint.uppercased())
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .tracking(1.4)
                    .foregroundStyle(ink.opacity(0.6))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .padding(.horizontal, 20)
            .frame(width: screen.width, alignment: .leading)
            .frame(height: screen.height - max(insets.bottom, 16) - 12, alignment: .bottom)
        }
        .frame(width: screen.width, height: screen.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }
}

/// The finger's place along a row over the last second, so lower rows can follow a little later.
final class AccordionLettersTrail {
    private var samples: [(Date, Double)] = []

    func add(_ value: Double, at date: Date) {
        samples.append((date, value))
        samples.removeAll { date.timeIntervalSince($0.0) > 1 }
    }

    /// The place at `date`, interpolated between samples (the oldest before them, the newest after).
    func value(at date: Date) -> Double {
        guard let first = samples.first, let last = samples.last else { return 0 }
        if date <= first.0 { return first.1 }
        if date >= last.0 { return last.1 }
        for i in 1..<samples.count where samples[i].0 >= date {
            let (t0, v0) = samples[i - 1], (t1, v1) = samples[i]
            let span = t1.timeIntervalSince(t0)
            return span > 0 ? v0 + (v1 - v0) * date.timeIntervalSince(t0) / span : v1
        }
        return last.1
    }
}

/// Letter outlines of Anybody Black at 100 pt (baseline at y = 0, y down) with their boxes.
final class AccordionLettersGlyphs {
    struct Glyph {
        let path: Path
        let box: CGRect
        let capHeight: Double
    }

    private var cache: [String: Glyph] = [:]

    static let fontName = "Anybody-Black"

    private static let registered: Void = {
        guard let url = Bundle.main.url(forResource: "AccordionLetters-Anybody-Black", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    func glyph(_ letter: String) -> Glyph? {
        if let hit = cache[letter] { return hit }
        _ = Self.registered
        let font = CTFontCreateWithName(Self.fontName as CFString, 100, nil)
        var chars = Array(letter.utf16.prefix(1))
        var glyph: CGGlyph = 0
        guard !chars.isEmpty, CTFontGetGlyphsForCharacters(font, &chars, &glyph, 1),
              let cg = CTFontCreatePathForGlyph(font, glyph, nil) else { return nil }
        let path = Path(cg).applying(CGAffineTransform(scaleX: 1, y: -1))
        let made = Glyph(path: path, box: path.boundingRect, capHeight: CTFontGetCapHeight(font))
        cache[letter] = made
        return made
    }
}
