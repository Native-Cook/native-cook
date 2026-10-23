import CoreText
import SwiftUI
import UIKit

// A festival poster on hot pink whose huge black number shreds into a comb of vertical strips,
// melts into a grainy blur, comes back out through the strips and settles sharp again, strip by
// strip from left to right. The strips are a layer shader over the number (ShredBlurNumber.metal);
// the blur is SwiftUI's, and a grain shader lies over the whole screen, strongest while it is blurred.
//
// autoPlay timeline (ShredBlurNumberMotion.loop = 6 s). `amount` 0 is the clean number and 1 every
// strip shredded; strips start in turn over the left-to-right order (`lead` 0) or right-to-left
// (`lead` 1). `blur` 0-1 is 0-22 pt of Gaussian blur, a 12 % zoom towards the 9 and the grain
// (0.08 at rest, so the poster always has a faint living grain, up to 0.45).
//   0.0-1.2   rest: the clean number
//   1.2-2.1   shred in, left strips first (amount 0 -> 1, ease-in-out cubic)
//   2.1-2.6   the comb ripples (phase-driven)
//   2.5-3.1   blur in (blur 0 -> 1, ease-in-out quad); under it the strips fall back to 0.3 (2.8-3.2)
//   3.1-3.4   the grainy blob
//   3.4-3.9   blur out (ease-out cubic) while the strips come back (amount 0.3 -> 1, 3.4-3.8)
//   3.9       lead flips to right-to-left (every strip is at 1, so nothing moves)
//   3.9-4.4   the comb ripples
//   4.4-5.4   resolve, right strips last (amount 1 -> 0, ease-out cubic), the steps of the reference
//   5.4-6.0   rest
// The ripple's phase runs once around per loop, so one period repeats exactly.
//
// Touch: a finger shreds the strips nearest to it first (amount 0.45, + 1 per 200 pt dragged
// sideways), the strips slide with it, and dragging up or down blurs (1 per 240 pt). 2 s after
// the finger lifts everything eases back over 0.8 s and the loop starts over.

enum ShredBlurNumberMotion {
    static let loop = 6.0
    /// Points of blur at `blur` 1.
    static let maxBlur = 22.0
    static let zoom = 0.12
    /// The grain's strength at rest and at full blur.
    static let grain = 0.08
    static let blurGrain = 0.45
    static let userHold = 2.0
    static let settle = 0.8

    struct State {
        var amount: Double
        var lead: Double
        var blur: Double
        static let rest = State(amount: 0, lead: 0, blur: 0)
    }

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func easeInOutCubic(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
    static func easeInOutQuad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }
    static func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
    static func span(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }

    /// The screen `t` seconds into the loop.
    static func state(at t: Double) -> State {
        let blur = t < 3.4
            ? easeInOutQuad(span(t, 2.5, 3.1))
            : 1 - easeOutCubic(span(t, 3.4, 3.9))
        var amount: Double
        if t < 2.8 {
            amount = easeInOutCubic(span(t, 1.2, 2.1))
        } else if t < 3.4 {
            amount = 1 - 0.7 * easeInOutCubic(span(t, 2.8, 3.2))
        } else if t < 4.4 {
            amount = 0.3 + 0.7 * easeInOutCubic(span(t, 3.4, 3.8))
        } else {
            amount = 1 - easeOutCubic(span(t, 4.4, 5.4))
        }
        return State(amount: amount, lead: t < 3.9 ? 0 : 1, blur: blur)
    }
}

enum ShredBlurNumberPalette {
    static let background = Color(hex: 0xF063D4)
    static let ink = Color(hex: 0x14090F)
}

enum ShredBlurNumberType {
    static let fontName = "ArchivoBlack-Regular"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "ShredBlurNumber-ArchivoBlack-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(_ size: CGFloat) -> Font {
        _ = registered
        return .custom(fontName, size: size)
    }

    private static var cache: [String: (path: Path, size: CGSize)] = [:]

    /// The text's outline with its ink box's top-left corner at the origin, and that box's size.
    static func outline(_ text: String, size: CGFloat) -> (path: Path, size: CGSize) {
        let key = "\(size)|\(text)"
        if let hit = cache[key] { return hit }
        _ = registered
        let font = UIFont(name: fontName, size: size) ?? .systemFont(ofSize: size, weight: .black)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
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

    /// The font size that makes `text` span `width` and stand at most `height` tall.
    static func fittingSize(_ text: String, width: CGFloat, height: CGFloat) -> CGFloat {
        let probe = outline(text, size: 100).size
        return floor(min(100 * width / max(probe.width, 1), 100 * height / max(probe.height, 1)))
    }
}

struct ShredBlurNumber: View {
    var number = "19"
    var brand = "LATE FORMS"
    var since = "SINCE 2007"
    var kicker = "EDITION NINETEEN"
    var headline = "Nineteen years\nof loud letters."
    var dates = "Oct 17 – 19"
    var venue = "Pier Hall, Dock 4"
    var action = "Get passes"
    var hint = "Drag across the number"
    /// Shreds, blurs and resolves the number by itself, every `ShredBlurNumberMotion.loop` seconds.
    var autoPlay = false

    private typealias M = ShredBlurNumberMotion
    private typealias P = ShredBlurNumberPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Where the loop's time starts.
    @State private var origin = Date()
    /// True from a touch until the number is back at rest and the loop has started over.
    @State private var manual = false
    @State private var amount = Tween(0)
    @State private var blur = Tween(0)
    @State private var drift = Tween(0)
    @State private var focusMix = Tween(0)
    @State private var lead = 0.0
    @State private var focusX = 0.0
    @State private var dragging = false
    @State private var atFull = false
    @State private var atBlur = false
    /// Changes on every touch, which restarts the loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    /// Room above and below the glyphs for the stretched strips.
    private let pad: CGFloat = 72

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            let fontSize = ShredBlurNumberType.fittingSize(number, width: full.width - 48, height: full.height * 0.3)
            let glyphs = ShredBlurNumberType.outline(number, size: fontSize)
            TimelineView(.animation(paused: reduceMotion && !manual)) { context in
                let now = context.date
                let s = state(at: now)
                let t = reduceMotion ? 0 : now.timeIntervalSince(origin)
                let phase = 2 * Double.pi * t.truncatingRemainder(dividingBy: M.loop) / M.loop
                ZStack(alignment: .top) {
                    P.background

                    VStack(spacing: 0) {
                        header
                            .padding(.top, safe.top + 12)
                            .padding(.horizontal, 24)

                        Spacer(minLength: 0)

                        numberView(glyphs, state: s, phase: phase, width: full.width)
                            .zIndex(-1)

                        Spacer(minLength: 0)

                        footer
                            .padding(.horizontal, 24)
                            .padding(.bottom, safe.bottom + 16)
                    }
                    .frame(width: full.width, height: full.height)

                    // Film grain: faint all the time (so the poster is never quite still), strong
                    // while the number is blurred.
                    Rectangle()
                        .fill(.white)
                        .colorEffect(ShaderLibrary.shredBlurNumberGrain(
                            .float(M.grain + (M.blurGrain - M.grain) * s.blur),
                            .float(floor(t.truncatingRemainder(dividingBy: M.loop) * 24))))
                        .allowsHitTesting(false)
                }
            }
            .frame(width: full.width, height: full.height)
            .contentShape(Rectangle())
            .gesture(drag, including: reduceMotion ? .none : .all)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .task(id: touches) { await settle() }
    }

    private var header: some View {
        VStack(spacing: 12) {
            HStack {
                Text(brand)
                Spacer()
                Text(since)
            }
            .font(.system(size: 12, weight: .semibold, design: .monospaced))
            .tracking(1.4)
            .foregroundStyle(P.ink)
            Rectangle().fill(P.ink).frame(height: 1.5)
        }
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(kicker)
                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                .tracking(1.4)
                .foregroundStyle(P.ink.opacity(0.7))
            Text(headline)
                .font(ShredBlurNumberType.font(30))
                .tracking(-0.6)
                .lineSpacing(-2)
                .foregroundStyle(P.ink)
                .padding(.top, 10)
                .fixedSize(horizontal: false, vertical: true)
            Rectangle().fill(P.ink).frame(height: 1.5)
                .padding(.top, 20)
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(dates)
                        .font(.system(size: 17, weight: .bold))
                    Text(venue)
                        .font(.system(size: 15, weight: .medium))
                        .opacity(0.7)
                }
                .foregroundStyle(P.ink)
                Spacer(minLength: 0)
                Text(action)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(P.background)
                    .padding(.horizontal, 24)
                    .frame(height: 48)
                    .background(Capsule().fill(P.ink))
                    .accessibilityAddTraits(.isButton)
            }
            .padding(.top, 16)
            Text(hint)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(P.ink.opacity(0.55))
                .frame(maxWidth: .infinity)
                .padding(.top, 20)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func numberView(_ glyphs: (path: Path, size: CGSize), state s: M.State, phase: Double, width: CGFloat) -> some View {
        let height = glyphs.size.height + 2 * pad
        let manualFocus = manual ? focusMix.value(at: .now) : 0
        return Canvas { context, size in
            // The canvas is opaque pink, so the layer the shader samples spans all of it.
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(P.background))
            context.translateBy(x: (size.width - glyphs.size.width) / 2, y: pad)
            context.fill(glyphs.path, with: .color(P.ink))
        }
        .frame(width: width, height: height)
        .layerEffect(
            ShaderLibrary.shredBlurNumberShred(
                .float2(width, height), .float(s.amount), .float(s.lead),
                .float(focusX), .float(manualFocus), .float(phase), .float(manual ? drift.value(at: .now) : 0)
            ),
            maxSampleOffset: CGSize(width: width, height: height),
            isEnabled: s.amount > 0.0005
        )
        .scaleEffect(1 + M.zoom * s.blur, anchor: UnitPoint(x: 0.68, y: 0.5))
        .blur(radius: M.maxBlur * s.blur)
        .accessibilityElement()
        .accessibilityLabel(number)
    }

    private func state(at date: Date) -> M.State {
        if manual {
            return M.State(amount: amount.value(at: date), lead: lead, blur: blur.value(at: date))
        }
        guard autoPlay, !reduceMotion else { return .rest }
        let t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)
        return M.state(at: max(0, t))
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date()
                if !dragging {
                    let current = state(at: now)
                    dragging = true
                    manual = true
                    amount = Tween(current.amount)
                    blur = Tween(current.blur)
                    lead = current.lead
                    drift = Tween(0)
                    focusMix = Tween(0)
                    focusMix.retarget(1, at: now, duration: 0.3, curve: .easeOut)
                    touches += 1
                    haptic.impactOccurred()
                }
                focusX = value.location.x
                let a = min(1, 0.45 + abs(value.translation.width) / 200)
                let b = min(1, abs(value.translation.height) / 240)
                amount.retarget(a, at: now, duration: 0.18, curve: .easeOut)
                blur.retarget(b, at: now, duration: 0.18, curve: .easeOut)
                drift.retarget(value.translation.width * 0.35, at: now, duration: 0.18, curve: .easeOut)
                // A tap when the strips are all out, and when the blur is full.
                if a >= 1, !atFull { haptic.impactOccurred(intensity: 0.7) }
                if b >= 1, !atBlur { haptic.impactOccurred(intensity: 0.5) }
                atFull = a >= 1
                atBlur = b >= 1
            }
            .onEnded { _ in
                dragging = false
                atFull = false
                atBlur = false
                touches += 1
            }
    }

    /// 2 s after the last touch the number eases back to rest, and the loop starts over.
    private func settle() async {
        guard touches > 0 else { return }
        try? await Task.sleep(for: .seconds(M.userHold))
        guard !Task.isCancelled, !dragging else { return }
        let now = Date()
        amount.retarget(0, at: now, duration: M.settle, curve: .easeInOut)
        blur.retarget(0, at: now, duration: M.settle, curve: .easeInOut)
        drift.retarget(0, at: now, duration: M.settle, curve: .easeInOut)
        try? await Task.sleep(for: .seconds(M.settle))
        guard !Task.isCancelled, !dragging else { return }
        origin = .now
        manual = false
    }
}

#Preview {
    ShredBlurNumber(autoPlay: true)
}
