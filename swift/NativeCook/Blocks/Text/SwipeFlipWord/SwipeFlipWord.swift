import CoreText
import SwiftUI
import UIKit

/*
 Swipe Flip Word — a gesture lesson whose big word "swipe" rolls over like a twisted ribbon.

 The word (Archivo Expanded Black, 80 % of the screen's width, hot pink #FF2E6E on violet #430090)
 is printed on all four faces of a square prism lying along it, its half side R = half the word's
 glyph height + 0.04 em, its axis R behind the screen, seen through a camera F = 320 pt in front.
 Every column of the word turns about that axis by its own angle (a Metal layer effect, see
 SwipeFlipWord.metal): faces that roll up go back and shrink, the next face comes up from below, and
 faces turned from the viewer are shaded down to 55 %. Every face shows the same word, so after a
 quarter turn the word is back at rest.

 A roll: column u (0 at the side the roll starts from, 1 at the other) turns by ±90° · ease(q), where
 q = clamp(a − u·b), and ease is easeInOutBack with overshoot k (a small wind-up and overshoot).
   autoPlay roll: a = T / 0.9 s, b = 0.35 / 0.9, k = 1.0 — each column turns in 0.9 s, the last
   one starting 0.35 s after the first, so a roll takes 1.25 s and the whole word twists at once.

 autoPlay timeline (loop = 4.8 s):
   0.00 … 0.60  rest.
   0.60 … 1.85  roll up, travelling left to right (a swipe to the right).
   1.85 … 3.00  rest.
   3.00 … 4.25  roll down, travelling right to left (a swipe to the left).
   4.25 … 4.80  rest; t = 4.8 is the same frame as t = 0.

 Touch: a horizontal drag scrubs a roll in its direction (right = up, left = down): P = |dx| / (55 %
 of the width), q = clamp(P·1.4 − u·0.4), k = 0; each whole roll passed gives a light haptic and the
 drag rolls on. On release the roll finishes (P past 0.5, or a fling over 500 pt/s in its direction)
 or falls back, over 0.4 s (easeOutCubic). A tap plays one roll to the right. 2 s after the last
 touch the loop starts again from rest.
 Reduced motion: the word stays still and the loop is off; a tap dims and restores it (0.5 s).
 */

struct SwipeFlipWord: View {
    var word = "swipe"
    var lesson = "LESSON 01"
    var topic = "GESTURES"
    var caption = "One motion to clear a card.\nRight keeps it, left lets it go."
    var action = "Continue"
    var autoPlay = false
    var onAction: (() -> Void)? = nil

    static let loop: Double = 4.8
    static let rolls: [(start: Double, sign: Double)] = [(0.6, 1), (3.0, -1)]
    static let turn: Double = 0.9
    static let stagger: Double = 0.35
    static let overshoot: Double = 1.0
    static let scrubWidth: Double = 0.4
    static let focal: Double = 320
    static let violet = Color(red: 0.263, green: 0.0, blue: 0.565) // #430090
    static let pink = Color(red: 1.0, green: 0.180, blue: 0.431) // #FF2E6E
    static let lilac = Color(red: 0.780, green: 0.651, blue: 1.0) // #C7A6FF

    /// What the shader needs from the roll: q = clamp(a − u·b), the turn's sign, the overshoot and the direction.
    struct Roll: Equatable {
        var a = 0.0
        var b = 1.0
        var sign = 1.0
        var overshoot = 0.0
        var direction = 1.0
        static let rest = Roll()
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var autoStart: Date? = nil
    /// A drag: its direction and progress in rolls.
    @State private var scrub: (sign: Double, progress: Double)? = nil
    @State private var scrubBase = 0.0
    /// A release: from and to (in rolls), its sign and when.
    @State private var settle: (from: Double, to: Double, sign: Double, date: Date)? = nil
    @State private var tapRoll: Date? = nil
    @State private var dim: Date? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let metrics = SwipeFlipWordMetrics(word: word, width: size.width)
            let centerY = size.height * 0.42
            let layerHeight = metrics.radius * 4 + 40
            // Read here so that a change re-renders the timeline's content.
            let state = (autoStart, scrub, settle, tapRoll, dim)
            ZStack(alignment: .topLeading) {
                Self.violet
                TimelineView(.animation(paused: reduceMotion && state.4 == nil)) { timeline in
                    let roll = roll(at: timeline.date, autoStart: state.0, scrub: state.1, settle: state.2, tapRoll: state.3)
                    SwipeFlipWordLayer(metrics: metrics, size: CGSize(width: size.width, height: layerHeight), roll: roll)
                        .opacity(dimming(at: timeline.date, since: state.4))
                }
                .frame(width: size.width, height: layerHeight)
                .offset(y: centerY - layerHeight / 2)
                .accessibilityElement()
                .accessibilityLabel(word)

                chrome(size: size, insets: insets, below: centerY + metrics.radius * 1.6 + 24)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(drag(width: size.width))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            haptic.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Time

    private func roll(at date: Date, autoStart: Date?, scrub: (sign: Double, progress: Double)?,
                      settle: (from: Double, to: Double, sign: Double, date: Date)?, tapRoll: Date?) -> Roll {
        if reduceMotion { return .rest }
        if let scrub { return Self.scrubRoll(sign: scrub.sign, progress: scrub.progress) }
        if let settle {
            let t = min(1, max(0, date.timeIntervalSince(settle.date) / 0.4))
            let e = 1 - pow(1 - t, 3)
            return Self.scrubRoll(sign: settle.sign, progress: settle.from + (settle.to - settle.from) * e)
        }
        if let tapRoll { return Self.autoRoll(elapsed: date.timeIntervalSince(tapRoll), sign: 1) }
        if let autoStart, date >= autoStart {
            let e = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            for r in Self.rolls.reversed() where e >= r.start {
                return Self.autoRoll(elapsed: e - r.start, sign: r.sign)
            }
        }
        return .rest
    }

    static func autoRoll(elapsed: Double, sign: Double) -> Roll {
        if elapsed >= turn + stagger { return .rest }
        return Roll(a: elapsed / turn, b: stagger / turn, sign: sign, overshoot: overshoot, direction: sign)
    }

    static func scrubRoll(sign: Double, progress: Double) -> Roll {
        let p = progress - progress.rounded(.down)
        if p <= 0 { return .rest }
        return Roll(a: p * (1 + scrubWidth), b: scrubWidth, sign: sign, overshoot: 0, direction: sign)
    }

    private func dimming(at date: Date, since: Date?) -> Double {
        guard let since else { return 1 }
        let t = date.timeIntervalSince(since) / 0.5
        return t >= 1 ? 1 : 1 - 0.6 * sin(.pi * max(0, t))
    }

    // MARK: Touch

    private func drag(width: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                resume?.cancel()
                autoStart = nil
                guard !reduceMotion else { return }
                let dx = value.translation.width
                guard abs(dx) > 4 || scrub != nil else { return }
                if scrub == nil {
                    // A drag takes over from whatever plays; a roll in progress is simply replaced.
                    tapRoll = nil
                    settle = nil
                    scrubBase = 0
                }
                let sign: Double = dx >= 0 ? 1 : -1
                let progress = abs(dx) / (width * 0.55)
                if let old = scrub, old.sign == sign, Int(progress) > Int(old.progress) {
                    haptic.impactOccurred()
                }
                scrub = (sign, progress)
            }
            .onEnded { value in
                let moved = abs(value.translation.width) > 4
                if let s = scrub {
                    let whole = s.progress.rounded(.down)
                    let p = s.progress - whole
                    let fling = value.velocity.width * s.sign > 500
                    let target = (p > 0.5 || fling) ? whole + 1 : whole
                    if target > whole { haptic.impactOccurred() }
                    settle = (s.progress, target, s.sign, .now)
                    scrub = nil
                } else if !moved {
                    haptic.impactOccurred()
                    if reduceMotion {
                        dim = .now
                        Task { @MainActor in
                            try? await Task.sleep(for: .seconds(0.55))
                            dim = nil
                        }
                    } else {
                        settle = nil
                        tapRoll = .now
                    }
                }
                scheduleResume()
            }
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            settle = nil
            tapRoll = nil
            autoStart = .now
        }
    }

    // MARK: Drawing

    private func chrome(size: CGSize, insets: EdgeInsets, below: Double) -> some View {
        ZStack(alignment: .topLeading) {
            HStack {
                Text(lesson)
                Spacer()
                Text(topic)
            }
            .font(SwipeFlipWordFont.font(size: 11))
            .tracking(1.6)
            .foregroundStyle(Self.lilac.opacity(0.75))
            .padding(.horizontal, 24)
            .frame(width: size.width)
            .offset(y: insets.top + 16)
            .allowsHitTesting(false)

            Text(caption)
                .font(.system(size: 17, weight: .medium))
                .lineSpacing(4)
                .multilineTextAlignment(.center)
                .foregroundStyle(Self.lilac)
                .fixedSize(horizontal: false, vertical: true)
                .frame(width: size.width - 48)
                .offset(x: 24, y: below)
                .allowsHitTesting(false)

            HStack(spacing: 6) {
                Capsule().fill(Self.pink).frame(width: 20, height: 6)
                Circle().fill(Self.lilac.opacity(0.4)).frame(width: 6, height: 6)
                Circle().fill(Self.lilac.opacity(0.4)).frame(width: 6, height: 6)
            }
            .frame(width: size.width)
            .offset(y: size.height - max(insets.bottom, 20) - 16 - 56 - 30)
            .allowsHitTesting(false)

            Button {
                onAction?()
            } label: {
                Text(action)
                    .font(SwipeFlipWordFont.font(size: 15))
                    .tracking(0.6)
                    .foregroundStyle(Self.violet)
                    .frame(width: size.width - 48, height: 56)
                    .background(Capsule().fill(Self.pink))
            }
            .buttonStyle(.plain)
            .offset(x: 24, y: size.height - max(insets.bottom, 20) - 16 - 56)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

// MARK: - Word layer

/// The word drawn flat and turned by the shader. A view of its own, so that a new roll (a plain value)
/// re-renders it: SwiftUI does not see a change in the shader's arguments alone.
struct SwipeFlipWordLayer: View {
    let metrics: SwipeFlipWordMetrics
    let size: CGSize
    let roll: SwipeFlipWord.Roll

    var body: some View {
        let cy = size.height / 2
        let left = (size.width - metrics.width) / 2
        Canvas { ctx, _ in
            var c = ctx
            c.translateBy(x: left, y: cy + metrics.glyphMid)
            c.fill(metrics.path, with: .color(SwipeFlipWord.pink))
        }
        .frame(width: size.width, height: size.height)
        .layerEffect(
            ShaderLibrary.swipeFlipWordRoll(
                .float4(Float(size.width / 2), Float(cy), Float(metrics.radius), Float(SwipeFlipWord.focal)),
                .float4(Float(left), Float(metrics.width), Float(roll.direction), 0),
                .float4(Float(roll.a), Float(roll.b), Float(roll.sign), Float(roll.overshoot))
            ),
            maxSampleOffset: CGSize(width: 90, height: metrics.radius * 2)
        )
    }
}

// MARK: - Metrics

/// The word's outline at the size that makes it 80 % of the width, and the prism around it.
struct SwipeFlipWordMetrics {
    /// The word's outline, origin on the baseline at the left (y down).
    let path: Path
    let fontSize: Double
    /// The word's advance width.
    let width: Double
    /// How far the glyphs' vertical centre sits above the baseline.
    let glyphMid: Double
    /// The prism's half side: half the glyphs' height plus 0.04 em.
    let radius: Double

    init(word: String, width screen: Double) {
        let unit = SwipeFlipWordFont.ctFont(size: 100)
        let unitWidth = Self.advance(word, font: unit) / 100
        fontSize = screen * 0.8 / max(unitWidth, 0.1)
        let font = SwipeFlipWordFont.ctFont(size: fontSize)
        width = Self.advance(word, font: font)
        var path = Path()
        var bounds = CGRect.null
        var x = 0.0
        for ch in word {
            var chars = Array(String(ch).utf16)
            var glyphs = [CGGlyph](repeating: 0, count: chars.count)
            CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
            var adv = CGSize.zero
            CTFontGetAdvancesForGlyphs(font, .horizontal, &glyphs, &adv, 1)
            var flip = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0)
            if let cg = CTFontCreatePathForGlyph(font, glyphs[0], &flip) {
                path.addPath(Path(cg))
                bounds = bounds.union(cg.boundingBoxOfPath)
            }
            x += Double(adv.width)
        }
        self.path = path
        glyphMid = -Double(bounds.midY)
        radius = Double(bounds.height) / 2 + 0.04 * fontSize
    }

    /// The advance of a word, character by character (no kerning, as in every stack).
    static func advance(_ word: String, font: CTFont) -> Double {
        var chars = Array(word.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &adv, glyphs.count)
        return adv.reduce(0) { $0 + Double($1.width) }
    }
}

// MARK: - Font

enum SwipeFlipWordFont {
    static let name = "SwipeFlipWord-ArchivoExpandedBlack"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "SwipeFlipWord-ArchivoExpandedBlack", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    static func ctFont(size: Double) -> CTFont {
        _ = registered
        return CTFontCreateWithName(name as CFString, size, nil)
    }
}
