import CoreText
import SwiftUI
import UIKit

// A big word filled with three flat liquids. The letters are a mask over a field coloured by
// level: lime at the bottom, green above it and salmon on top, each with its own wavy surface,
// plus green blobs (salmon at their cores) swirling in the lime near its surface. The letter edges
// wobble a little, like a meniscus. Dragging up or down pours or drains it.
//
// autoPlay timeline (LiquidFillWordMotion.loop = 8 s), levels from 0 (bottom of the descender) to
// 1 (top of the ascenders); 1.15 is brim-full and -0.08 empty:
//   0.0-2.0   full and resting: lime, with green blobs swirling near the top
//   2.0-4.0   drains (ease-in-out cubic), only the lime showing; the blobs fade out by 2.8
//   4.0-4.5   empty: only the faint glass of the letters
//   4.5-7.1   pours: salmon first (4.5 + 1.6 s), green behind it (4.8 + 1.8 s), lime last
//             (5.1 + 2.0 s), each ease-in-out cubic; the blobs come back 6.4-7.6
//   7.1-8.0   full and resting again
// The surfaces slosh harder while the level moves (slosh 0.35 -> 1, a sine bump over each move).
// The swirl's phase runs once around per loop, so one period repeats exactly.

enum LiquidFillWordMotion {
    static let loop = 8.0
    static let full = 1.15
    static let empty = -0.08
    static let drainAt = 2.0
    static let drainFor = 2.0
    /// Start and length of each liquid's pour.
    static let salmonPour = (4.5, 1.6)
    static let greenPour = (4.8, 1.8)
    static let limePour = (5.1, 2.0)
    /// While draining, green and salmon sit this far under the lime, hidden.
    static let hidden = 0.25
    /// While a finger sets the level, green and salmon ride this far above the lime.
    static let greenBand = 0.06
    static let salmonBand = 0.12
    /// Points of drag for the whole range.
    static let dragRange = 300.0
    static let userHold = 2.0
    static let settle = 1.2

    struct Liquid {
        var lime: Double
        var green: Double
        var salmon: Double
        var blobs: Double
        var slosh: Double

        static let rest = Liquid(lime: full, green: full, salmon: full, blobs: 1, slosh: 0.35)
    }

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func easeInOutCubic(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
    static func level(_ t: Double, _ pour: (Double, Double)) -> Double {
        empty + (full - empty) * easeInOutCubic(clamp((t - pour.0) / pour.1))
    }
    static func bump(_ t: Double, _ a: Double, _ b: Double) -> Double {
        sin(.pi * clamp((t - a) / (b - a)))
    }

    /// The liquids `t` seconds into the loop.
    static func liquid(at t: Double) -> Liquid {
        let slosh = 0.35 + 0.65 * (bump(t, 2.0, 4.3) + bump(t, 4.5, 7.6))
        let blobs = t < 4.25
            ? 1 - easeInOutCubic(clamp((t - drainAt) / 0.8))
            : easeInOutCubic(clamp((t - 6.4) / 1.2))
        if t < 4.25 {
            let l = full + (empty - full) * easeInOutCubic(clamp((t - drainAt) / drainFor))
            return Liquid(lime: l, green: l - hidden, salmon: l - hidden, blobs: blobs, slosh: slosh)
        }
        return Liquid(lime: level(t, limePour), green: level(t, greenPour), salmon: level(t, salmonPour), blobs: blobs, slosh: slosh)
    }
}

enum LiquidFillWordPalette {
    static let background = Color(hex: 0x93B0A1)
    static let ink = Color(hex: 0x1F3329)
    static let lime = Color(hex: 0xCDD98F)
    static let green = Color(hex: 0x1DA07F)
    static let salmon = Color(hex: 0xE8967A)
    /// The empty letters: a shade darker than the backdrop.
    static let ghost = Color(hex: 0x86A495)
}

enum LiquidFillWordType {
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "LiquidFillWord-PaytoneOne-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static var fontName: String {
        _ = registered
        return "PaytoneOne-Regular"
    }

    /// The word's outline with its ink box's top-left corner at the origin, and that box's size.
    private static var cache: [String: (path: Path, size: CGSize)] = [:]

    static func outline(_ word: String, size: CGFloat) -> (path: Path, size: CGSize) {
        let key = "\(size)|\(word)"
        if let hit = cache[key] { return hit }
        _ = registered
        let font = UIFont(name: "PaytoneOne-Regular", size: size) ?? .systemFont(ofSize: size, weight: .black)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: word, attributes: [.font: font]))
        let path = CGMutablePath()
        for run in CTLineGetGlyphRuns(line) as? [CTRun] ?? [] {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
            let runFont = (CTRunGetAttributes(run) as NSDictionary)[kCTFontAttributeName] as! CTFont
            for i in 0..<count {
                var t = CGAffineTransform(translationX: positions[i].x, y: 0).scaledBy(x: 1, y: -1)
                if let glyph = CTFontCreatePathForGlyph(runFont, glyphs[i], &t) { path.addPath(glyph) }
            }
        }
        let box = path.boundingBoxOfPath
        let moved = Path(path).offsetBy(dx: -box.minX, dy: -box.minY)
        cache[key] = (moved, box.size)
        return (moved, box.size)
    }

    /// The font size that makes `word` span `width`, at most `limit`.
    static func fittingSize(_ word: String, width: CGFloat, limit: CGFloat) -> CGFloat {
        let probe = outline(word, size: 100).size.width
        return min(limit, floor(100 * width / max(probe, 1)))
    }
}

struct LiquidFillWord: View {
    var word = "Liquid"
    var label = "Fluid type"
    var issue = "No. 07"
    var caption = "Three liquids, one level."
    var levelLabel = "Level"
    var hint = "Drag up or down to pour"
    /// Drains and pours the word by itself, every `LiquidFillWordMotion.loop` seconds.
    var autoPlay = false

    private typealias M = LiquidFillWordMotion
    private typealias P = LiquidFillWordPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Where the loop's time starts.
    @State private var origin = Date()
    /// True from a touch until the level is back at rest and the loop has started over.
    @State private var manual = false
    @State private var level = Tween(M.full)
    @State private var blobs = Tween(1)
    @State private var slosh = Tween(0.35)
    /// The level under the finger when the drag began.
    @State private var dragFrom: Double? = nil
    @State private var atLimit = false
    /// Changes on every touch, which restarts the loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            let size = LiquidFillWordType.fittingSize(word, width: full.width - 48, limit: 110)
            let glyphs = LiquidFillWordType.outline(word, size: size)
            TimelineView(.animation(paused: reduceMotion && !manual)) { context in
                let now = context.date
                let liquid = liquid(at: now)
                ZStack(alignment: .top) {
                    P.background

                    VStack(spacing: 0) {
                        HStack {
                            Text(label)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(P.ink)
                            Spacer()
                            Text(issue)
                                .font(.system(size: 15, weight: .medium).monospacedDigit())
                                .foregroundStyle(P.ink.opacity(0.55))
                        }
                        .padding(.top, safe.top + 14)
                        .padding(.horizontal, 28)

                        Spacer(minLength: 0)

                        wordView(glyphs, liquid: liquid, at: now, width: full.width)
                            .accessibilityLabel(word)

                        Text(caption)
                            .font(.system(size: 17, weight: .medium))
                            .foregroundStyle(P.ink.opacity(0.7))
                            .multilineTextAlignment(.center)
                            .padding(.top, 6)
                            .padding(.horizontal, 28)

                        Spacer(minLength: 0)

                        meter(liquid.lime)
                            .padding(.bottom, safe.bottom + 28)
                            .padding(.horizontal, 28)
                    }
                    .frame(width: full.width)
                }
            }
            .frame(width: full.width, height: full.height)
            .contentShape(Rectangle())
            .gesture(drag)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .task(id: touches) { await settle() }
    }

    private func wordView(_ glyphs: (path: Path, size: CGSize), liquid: M.Liquid, at now: Date, width: CGFloat) -> some View {
        let pad: CGFloat = 24
        let height = glyphs.size.height + 2 * pad
        let phase = reduceMotion ? 0 : 2 * Double.pi * now.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: M.loop) / M.loop
        return Canvas { context, size in
            // A veil over the whole canvas, under the shader's edge threshold (0.35), so the layer
            // the shader sees spans the canvas: with the word alone SwiftUI cuts the layer at the
            // word's box and the wobbled outer edges come out straight.
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.white.opacity(0.2)))
            context.translateBy(x: (size.width - glyphs.size.width) / 2, y: pad)
            context.fill(glyphs.path, with: .color(.white))
        }
        .frame(width: width, height: height)
        .layerEffect(
            ShaderLibrary.liquidFillWordFill(
                .float(phase), .float(liquid.lime), .float(liquid.green), .float(liquid.salmon),
                .float(liquid.blobs), .float(reduceMotion ? 0 : liquid.slosh),
                .float(pad + glyphs.size.height), .float(pad),
                .color(P.lime), .color(P.green), .color(P.salmon), .color(P.ghost)
            ),
            maxSampleOffset: CGSize(width: 3, height: 3)
        )
    }

    private func meter(_ lime: Double) -> some View {
        let fraction = M.clamp(lime)
        let percent = Int((fraction * 100).rounded())
        return VStack(alignment: .leading, spacing: 0) {
            Text(levelLabel.uppercased())
                .font(.system(size: 13, weight: .semibold))
                .tracking(1.2)
                .foregroundStyle(P.ink.opacity(0.6))
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text("\(percent)")
                    .font(.custom(LiquidFillWordType.fontName, size: 64))
                Text("%")
                    .font(.custom(LiquidFillWordType.fontName, size: 26))
            }
            .foregroundStyle(P.ink)
            .padding(.top, 2)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(P.ink.opacity(0.12))
                    Capsule().fill(P.ink).frame(width: geo.size.width * fraction)
                }
            }
            .frame(height: 4)
            .padding(.top, 10)
            Text(hint)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(P.ink.opacity(0.5))
                .padding(.top, 14)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(levelLabel) \(percent) percent")
    }

    private func liquid(at date: Date) -> M.Liquid {
        if manual {
            let l = level.value(at: date)
            return M.Liquid(lime: l, green: l + M.greenBand, salmon: l + M.salmonBand, blobs: blobs.value(at: date), slosh: slosh.value(at: date))
        }
        guard autoPlay, !reduceMotion else { return .rest }
        let t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)
        return M.liquid(at: max(0, t))
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date()
                if dragFrom == nil {
                    let current = liquid(at: now).lime
                    dragFrom = current
                    manual = true
                    level = Tween(current)
                    blobs.retarget(0.5, at: now, duration: 0.4, curve: .easeOut)
                    slosh.retarget(1, at: now, duration: 0.4, curve: .easeOut)
                    touches += 1
                    haptic.impactOccurred()
                }
                let target = min(M.full, max(M.empty, (dragFrom ?? M.full) - value.translation.height / M.dragRange))
                level.retarget(target, at: now, duration: 0.18, curve: .easeOut)
                // A tap at either end of the range.
                let limit = target >= M.full || target <= M.empty
                if limit, !atLimit { haptic.impactOccurred(intensity: 0.6) }
                atLimit = limit
            }
            .onEnded { _ in
                dragFrom = nil
                touches += 1
            }
    }

    /// 2 s after the last touch the word fills back up, and the loop starts over from rest.
    private func settle() async {
        guard touches > 0 else { return }
        try? await Task.sleep(for: .seconds(M.userHold))
        guard !Task.isCancelled, dragFrom == nil else { return }
        let now = Date()
        level.retarget(M.full, at: now, duration: M.settle, curve: .easeInOut)
        blobs.retarget(1, at: now, duration: M.settle, curve: .easeInOut)
        slosh.retarget(0.35, at: now, duration: M.settle, curve: .easeInOut)
        try? await Task.sleep(for: .seconds(M.settle))
        guard !Task.isCancelled, dragFrom == nil else { return }
        origin = .now
        manual = false
    }
}

#Preview {
    LiquidFillWord(autoPlay: true)
}
