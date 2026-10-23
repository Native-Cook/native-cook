import CoreText
import SwiftUI

/*
 Weight Wave Wall: a wall of one word, every letter on its own weight of a variable font (Epilogue,
 100 hairline to 900 black), with bands of black letters rolling through it like surf.

 Timeline of the self-playing loop (`WeightWaveWall.loop` = 7 s, two wave periods of 3.5 s):
   a letter's weight follows s = ½ + ½·sin(2π·(d·(u + 0.06·row) − t / 3.5) + 0.9·group), where u is the
   letter's place along its row (0 … 1, ten letters), rows go in groups of four whose waves run in
   opposite directions (d = ±1), and s is sharpened with smoothstep(0.18, 0.82). The weight is
   100 + 800·s, taken in 33 steps of 25; the letter's opacity is 0.42 + 0.58·s. Each letter advances by
   its own weight's width, so the black bands push their neighbours along the row. The frame at t = 0 is
   the resting state, and t = 7 s is the same frame.

 Touch: a finger drops a stone into the wall. The letters near it go black (a Gaussian of 80 pt) and
 rings run outward from it (wavelength 140 pt, one ring a second, fading over 220 pt); the touch's
 field fades in over 0.35 s (strong ease-out) and follows the finger. 2 s after the last touch the
 loop starts over from t = 0 while the touch's field fades out over 0.6 s (ease-in-out). Reduced
 motion: the wall holds the first frame and touch does nothing.
*/
struct WeightWaveWall: View {
    var word = "waves"
    var kicker = "Focus sounds — 04"
    var title = "Let it roll in."
    var subtitle = "Twenty minutes of slow surf for deep work."
    var action = "Play waves"
    /// Rolls the waves by itself; the pattern repeats every `loop` seconds.
    var autoPlay = false
    var onAction: (() -> Void)? = nil

    static let loop = 7.0

    enum M {
        static let period = 3.5
        static let groupRows = 4
        static let rowSlant = 0.06
        static let groupPhase = 0.9
        static let levels = 33
        static let touchRadius = 80.0
        static let ringLength = 140.0
        static let ringPeriod = 1.0
        static let ringFade = 220.0
        static let blendIn = 0.35
        static let blendOut = 0.6
        static let idle = 2.0
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var face = WeightWaveWallFace()
    @State private var loopStart = Date.now
    @State private var blend = Tween(0)
    @State private var finger = CGPoint.zero
    @State private var touchStart = Date.now
    @State private var touches = 0
    @State private var dragging = false
    @State private var haptic = UIImpactFeedbackGenerator(style: .soft)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            ZStack(alignment: .bottom) {
                TimelineView(.animation(paused: reduceMotion || (!autoPlay && blend.value(at: .now) == 0 && !dragging))) { timeline in
                    Canvas { context, size in
                        draw(&context, size: size, date: timeline.date)
                    }
                }
                .ignoresSafeArea()
                .accessibilityHidden(true)

                // The status bar and the copy each sit on a fade to black.
                VStack(spacing: 0) {
                    LinearGradient(
                        stops: [.init(color: .black, location: 0), .init(color: .black.opacity(0.75), location: 0.5), .init(color: .black.opacity(0), location: 1)],
                        startPoint: .top, endPoint: .bottom
                    )
                    .frame(height: insets.top + 44)
                    Spacer(minLength: 0)
                    LinearGradient(
                        stops: [.init(color: .black.opacity(0), location: 0), .init(color: .black.opacity(0.92), location: 0.4), .init(color: .black, location: 0.55)],
                        startPoint: .top, endPoint: .bottom
                    )
                    .frame(height: 440 + insets.bottom)
                }
                .ignoresSafeArea()
                .allowsHitTesting(false)

                copy
                    .padding(.horizontal, 24)
                    .padding(.bottom, max(insets.bottom, 16) > 16 ? 8 : 16)
            }
            .contentShape(Rectangle())
            .simultaneousGesture(drag)
            .onAppear { _ = face.layout(word: word, width: screen.width) }
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            loopStart = .now
            haptic.prepare()
        }
        .task(id: touches) { await settle() }
    }

    private var copy: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(kicker.uppercased())
                .font(.system(size: 12, weight: .medium, design: .monospaced))
                .tracking(2)
                .foregroundStyle(.white.opacity(0.55))
                .lineLimit(1)
            Text(title)
                .font(.system(size: 34, weight: .semibold))
                .tracking(-0.8)
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .padding(.top, 12)
            Text(subtitle)
                .font(.system(size: 15))
                .foregroundStyle(.white.opacity(0.6))
                .lineLimit(2)
                .padding(.top, 8)
            Button {
                onAction?()
            } label: {
                Text(action)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.black)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Capsule().fill(.white))
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 24)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Touch

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .onChanged { value in
                guard !reduceMotion else { return }
                finger = value.location
                if !dragging {
                    dragging = true
                    touches += 1
                    touchStart = .now
                    blend.retarget(1, duration: M.blendIn * (1 - blend.value(at: .now)), curve: Ease.out)
                    haptic.impactOccurred()
                }
            }
            .onEnded { _ in
                guard dragging else { return }
                dragging = false
                touches += 1
            }
    }

    /// 2 s after the last touch the loop starts over while the touch's field fades out.
    private func settle() async {
        guard touches > 0, !dragging else { return }
        try? await Task.sleep(for: .seconds(M.idle))
        guard !Task.isCancelled, !dragging else { return }
        loopStart = .now
        blend.retarget(0, duration: M.blendOut, curve: .easeInOut)
    }

    // MARK: Drawing

    private func draw(_ context: inout GraphicsContext, size: CGSize, date: Date) {
        let wall = face.layout(word: word, width: size.width)
        guard !wall.levels.isEmpty else { return }
        let t = reduceMotion ? 0 : date.timeIntervalSince(loopStart).truncatingRemainder(dividingBy: Self.loop)
        let k = reduceMotion ? 0 : blend.value(at: date)
        let since = date.timeIntervalSince(touchStart)
        let letters = word.count
        let perRow = letters * 2
        let rows = Int(ceil(size.height / wall.pitch)) + 1
        let firstBaseline = (size.height - Double(rows - 1) * wall.pitch) / 2 + wall.xHeight / 2

        for row in 0..<rows {
            let group = row / M.groupRows
            let direction = group % 2 == 0 ? 1.0 : -1.0
            let baseline = firstBaseline + Double(row) * wall.pitch
            let middle = baseline - wall.xHeight / 2
            for column in 0..<2 {
                var x = wall.margin + Double(column) * (wall.column + wall.gap)
                for i in 0..<letters {
                    let u = (Double(column * letters + i) + 0.5) / Double(perRow)
                    var s = 0.5 + 0.5 * sin(2 * .pi * (direction * (u + M.rowSlant * Double(row)) - t / M.period) + M.groupPhase * Double(group))
                    if k > 0 {
                        let cx = wall.margin + Double(column) * (wall.column + wall.gap) + (Double(i) + 0.5) * wall.column / Double(letters)
                        let d = hypot(cx - finger.x, middle - finger.y)
                        let ring = (0.5 + 0.5 * cos(2 * .pi * (d / M.ringLength - since / M.ringPeriod))) * exp(-d / M.ringFade)
                        let touch = max(exp(-(d / M.touchRadius) * (d / M.touchRadius)), ring)
                        s += (touch - s) * k
                    }
                    let level = Int((Self.smoothstep(0.18, 0.82, s) * Double(M.levels - 1)).rounded())
                    let glyph = wall.levels[level][i]
                    var c = context
                    c.translateBy(x: x, y: baseline)
                    c.fill(glyph.path, with: .color(.white.opacity(0.42 + 0.58 * Double(level) / Double(M.levels - 1))))
                    x += glyph.advance
                }
            }
        }
    }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(max((x - a) / (b - a), 0), 1)
        return t * t * (3 - 2 * t)
    }
}

/// The word's glyphs at each weight step, sized so two words of the heaviest weight fit a row.
final class WeightWaveWallFace {
    struct Glyph {
        let path: Path
        let advance: Double
    }

    struct Wall {
        /// Per weight step (100 … 900 in 33 steps), per letter of the word.
        var levels: [[Glyph]] = []
        var size = 0.0
        var xHeight = 0.0
        /// Row to row.
        var pitch = 0.0
        /// The room one word gets at its heaviest, the gap between the two words and the side margin.
        var column = 0.0
        var gap = 0.0
        var margin = 16.0
    }

    private var wall = Wall()
    private var key: (String, CGFloat) = ("", 0)

    static let fontName = "Epilogue-Regular"
    private static let weightTag = 0x7767_6874

    private static let registered: Void = {
        guard let url = Bundle.main.url(forResource: "WeightWaveWall-Epilogue-VF", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    func layout(word: String, width: CGFloat) -> Wall {
        if key == (word, width) { return wall }
        _ = Self.registered
        let unit: CGFloat = 100
        let base = CTFontCreateWithName(Self.fontName as CFString, unit, nil)
        let heaviest = Self.glyphs(word, font: Self.font(base, weight: 900, size: unit))
        let heavyWidth = heaviest.reduce(0) { $0 + $1.advance }
        // Two heaviest words and a 0.2 em gap fill the width between 16 pt margins.
        let size = (Double(width) - 32) / (2 * heavyWidth / unit + 0.2)
        let sized = CTFontCreateCopyWithAttributes(base, size, nil, nil)
        var wall = Wall()
        wall.size = size
        wall.xHeight = CTFontGetXHeight(sized)
        wall.pitch = wall.xHeight / 0.7
        wall.column = heavyWidth / unit * size
        wall.gap = 0.2 * size
        wall.levels = (0..<WeightWaveWall.M.levels).map { level in
            let weight = 100 + 800 * Double(level) / Double(WeightWaveWall.M.levels - 1)
            return Self.glyphs(word, font: Self.font(base, weight: weight, size: size))
        }
        self.wall = wall
        key = (word, width)
        return wall
    }

    private static func font(_ base: CTFont, weight: Double, size: CGFloat) -> CTFont {
        let variation = [weightTag: weight] as CFDictionary
        let descriptor = CTFontDescriptorCreateWithAttributes([kCTFontVariationAttribute: variation] as CFDictionary)
        return CTFontCreateCopyWithAttributes(base, size, nil, descriptor)
    }

    private static func glyphs(_ word: String, font: CTFont) -> [Glyph] {
        word.utf16.map { char in
            var c = char
            var glyph: CGGlyph = 0
            CTFontGetGlyphsForCharacters(font, &c, &glyph, 1)
            var advance = CGSize.zero
            CTFontGetAdvancesForGlyphs(font, .horizontal, &glyph, &advance, 1)
            let path = CTFontCreatePathForGlyph(font, glyph, nil).map { Path($0).applying(CGAffineTransform(scaleX: 1, y: -1)) } ?? Path()
            return Glyph(path: path, advance: advance.width)
        }
    }
}
