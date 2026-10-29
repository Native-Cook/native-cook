import CoreText
import SwiftUI
import UIKit

// A magazine's new-issue screen whose cover line, "MAKE / SOME / WAVES", is drawn only as
// horizontal stripes. A swirl field stirs them: around its centre the stripes twist into rings
// and arcs that break into coloured capsules, then the swirl unwinds (past straight and back) and
// the words read again. Dragging through the type stirs it with the finger.
//
// autoPlay timeline (StripeSwirlTypeMotion.loop = 8 s):
//   0.0-1.0   rest: straight stripes, legible words
//   0.8-1.15  a ghost finger ring fades in on the swirl's centre
//   1.0-4.6   the centre circles the words clockwise (1.25 turns, ease-in-out cubic)
//   1.0-3.3   the twist builds 0 -> 7.5 rad, the radius 60 -> 170 pt (ease-in-out cubic)
//   2.4-4.6   an extra spin of the core (2.2 rad, sin² bump)
//   3.3-4.4   peak: rings and colour
//   4.4-4.85  the ring fades out
//   4.4-6.8   the twist unwinds: 7.5 (1 - u)³ cos(1.8 π u), u = smoothstep over the phase, so it
//             dips about 1 rad the other way and lands at 0 with no speed; the radius shrinks back
//   6.8-8.0   rest, identical to t = 0

enum StripeSwirlTypeMotion {
    static let loop = 8.0
    /// Twist at the peak, radians at the centre.
    static let peak = 7.5
    static let minRadius = 60.0
    static let maxRadius = 170.0
    static let spin = 2.2
    /// The centre's path, as fractions of the screen width and of the type block's height.
    static let orbitX = 0.2
    static let orbitY = 0.16
    static let userHold = 2.0
    static let unwind = 1.6

    struct Stir {
        /// The centre on the orbit, -1...1 on each axis.
        var orbit: CGPoint
        var twist: Double
        var radius: Double
        var spin: Double
        var ring: Double
        var ringScale: Double

        static let rest = Stir(orbit: CGPoint(x: cos(-0.35 * .pi), y: sin(-0.35 * .pi)), twist: 0, radius: minRadius, spin: 0, ring: 0, ringScale: 0.8)
    }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func easeInOutCubic(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
    static func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
    static func easeInCubic(_ t: Double) -> Double { t * t * t }
    /// From 1 to 0, dipping about 0.14 below zero on the way and landing with no speed.
    static func unwound(_ x: Double) -> Double {
        let u = x * x * (3 - 2 * x)
        return pow(1 - u, 3) * cos(1.8 * .pi * u)
    }
    static func radius(forTwist twist: Double) -> Double {
        minRadius + (maxRadius - minRadius) * min(1, abs(twist) / peak)
    }

    /// The stir `t` seconds into the loop.
    static func stir(at t: Double) -> Stir {
        let build = easeInOutCubic(seg(t, 1.0, 3.3))
        let x = seg(t, 4.4, 6.8)
        let twist = t < 4.4 ? peak * build : peak * unwound(x)
        let env = t < 4.4 ? build : 1 - easeInOutCubic(x)
        let swing = sin(.pi * seg(t, 2.4, 4.6))
        let phi = -0.35 * .pi + 2.5 * .pi * easeInOutCubic(seg(t, 1.0, 4.6))
        let ringIn = easeOutCubic(seg(t, 0.8, 1.15))
        let ringOut = easeInCubic(seg(t, 4.4, 4.85))
        return Stir(
            orbit: CGPoint(x: cos(phi), y: sin(phi)),
            twist: twist,
            radius: minRadius + (maxRadius - minRadius) * env,
            spin: spin * swing * swing,
            ring: ringIn * (1 - ringOut),
            ringScale: 0.8 + 0.2 * ringIn - 0.15 * ringOut
        )
    }
}

enum StripeSwirlTypePalette {
    static let background = Color(hex: 0x100F0E)
    static let ink = Color(hex: 0xF1ECE3)
    static let red = Color(hex: 0xE8432E)
    static let blue = Color(hex: 0x2D63D8)
    static let green = Color(hex: 0x2C9F5B)
    static let yellow = Color(hex: 0xF2B42C)
    static let sky = Color(hex: 0x49B3EA)
}

enum StripeSwirlTypeType {
    static let anton = "Anton-Regular"
    static let serif = "DMSerifDisplay-Regular"
    /// The cover line: each word in its face.
    static let lines = [("MAKE", anton), ("SOME", serif), ("WAVES", anton)]
    static let margin: CGFloat = 24
    static let gap: CGFloat = 18

    static let registered: Void = {
        for name in ["StripeSwirlType-Anton-Regular", "StripeSwirlType-DMSerifDisplay-Regular"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    static func font(_ name: String, size: CGFloat) -> Font {
        _ = registered
        return .custom(name, size: size)
    }

    /// A word's outline with its ink box's top-left corner at the origin, and that box's size.
    static func outline(_ word: String, font name: String, size: CGFloat) -> (path: Path, size: CGSize) {
        _ = registered
        let font = UIFont(name: name, size: size) ?? .systemFont(ofSize: size, weight: .black)
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
        return (Path(path).offsetBy(dx: -box.minX, dy: -box.minY), box.size)
    }

    private static var cache: [String: (path: Path, rect: CGRect)] = [:]

    /// The three words, each fitted to the width less the margins and centred, stacked with `gap`
    /// between them and centred between `top` and `bottom` (scaled down if they do not fit).
    static func block(width: CGFloat, top: CGFloat, bottom: CGFloat) -> (path: Path, rect: CGRect) {
        let key = "\(width)|\(top)|\(bottom)"
        if let hit = cache[key] { return hit }
        let target = width - 2 * margin
        let sizes = lines.map { word, face in 100 * target / max(outline(word, font: face, size: 100).size.width, 1) }
        let natural = lines.indices.map { outline(lines[$0].0, font: lines[$0].1, size: sizes[$0]).size.height }
        let total = natural.reduce(0, +) + gap * CGFloat(lines.count - 1)
        let k = min(1, (bottom - top) / total)
        var y = top + (bottom - top - total * k) / 2
        let startY = y
        var path = Path()
        for (i, (word, face)) in lines.enumerated() {
            let o = outline(word, font: face, size: sizes[i] * k)
            path.addPath(o.path.offsetBy(dx: (width - o.size.width) / 2, dy: y))
            y += o.size.height + gap * k
        }
        let result = (path, CGRect(x: 0, y: startY, width: width, height: y - gap * k - startY))
        cache[key] = result
        return result
    }
}

struct StripeSwirlType: View {
    var wordmark = "UNDERTOW"
    var issue = "Issue 07 · Autumn"
    var hint = "Drag through the type to stir it"
    var headline = "Issue 07 is out."
    var subline = "Twelve stories about change, told slowly."
    var action = "Start reading"
    /// Stirs the words by itself, every `StripeSwirlTypeMotion.loop` seconds.
    var autoPlay = false

    private typealias M = StripeSwirlTypeMotion
    private typealias P = StripeSwirlTypePalette
    private typealias T = StripeSwirlTypeType

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Where the loop's time starts.
    @State private var origin = Date()
    /// True from a touch until the swirl has unwound and the loop has started over.
    @State private var manual = false
    @State private var centreX = Tween(0)
    @State private var centreY = Tween(0)
    @State private var twist = Tween(0)
    /// When the finger's swirl started to unwind, and from which twist.
    @State private var unwindStart: Date? = nil
    @State private var unwindFrom = 0.0
    @State private var dragging = false
    @State private var stirSign = 1.0
    @State private var peaked = false
    /// Changes on every touch, which restarts the loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            // Bottom copy, from the bottom up: 18 + button 54 + 22 + subline 20 + 10 + headline 32 + 22 + hint 16.
            let hintTop = size.height - safe.bottom - 194
            let block = T.block(width: size.width, top: safe.top + 70, bottom: hintTop - 24)
            TimelineView(.animation(paused: (reduceMotion || !autoPlay) && !manual)) { context in
                let field = field(at: context.date, size: size, block: block.rect)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, canvasSize in
                        ctx.fill(Path(CGRect(origin: .zero, size: canvasSize)), with: .color(.black))
                        ctx.addFilter(.blur(radius: 1.6))
                        ctx.fill(block.path, with: .color(.white))
                    }
                    .frame(width: size.width, height: size.height)
                    .layerEffect(
                        ShaderLibrary.stripeSwirlTypeField(
                            .float2(Float(size.width), Float(size.height)),
                            .float2(Float(field.centre.x), Float(field.centre.y)),
                            .float(field.twist), .float(field.radius), .float(field.spin),
                            .color(P.ink), .color(P.background),
                            .color(P.red), .color(P.blue), .color(P.green), .color(P.yellow), .color(P.sky)
                        ),
                        maxSampleOffset: .zero
                    )
                    .accessibilityLabel(T.lines.map(\.0).joined(separator: " "))

                    Circle()
                        .fill(P.ink.opacity(0.1))
                        .overlay(Circle().stroke(P.ink.opacity(0.7), lineWidth: 1.5))
                        .frame(width: 44, height: 44)
                        .scaleEffect(field.ringScale)
                        .opacity(field.ring)
                        .position(field.centre)
                        .allowsHitTesting(false)

                    copy(safe: safe, size: size)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(drag(size: size, block: block.rect))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .task(id: touches) { await settle() }
    }

    private func copy(safe: EdgeInsets, size: CGSize) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(wordmark)
                    .font(T.font(T.anton, size: 20))
                    .tracking(2)
                    .foregroundStyle(P.ink)
                Spacer()
                Text(issue)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(P.ink.opacity(0.55))
            }
            .frame(height: 30)
            .padding(.top, safe.top + 10)
            Rectangle()
                .fill(P.ink.opacity(0.14))
                .frame(height: 1)
                .padding(.top, 10)

            Spacer(minLength: 0)

            Text(hint)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(P.ink.opacity(0.45))
                .frame(height: 16)
            Text(headline)
                .font(T.font(T.serif, size: 26))
                .foregroundStyle(P.ink)
                .frame(height: 32)
                .padding(.top, 22)
            Text(subline)
                .font(.system(size: 15))
                .foregroundStyle(P.ink.opacity(0.62))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(height: 20)
                .padding(.top, 10)
            Text(action)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(P.background)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(Capsule().fill(P.ink))
                .padding(.top, 22)
                .padding(.bottom, safe.bottom + 18)
                .accessibilityAddTraits(.isButton)
        }
        .padding(.horizontal, T.margin)
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private struct Field {
        var centre: CGPoint
        var twist: Double
        var radius: Double
        var spin: Double
        var ring: Double
        var ringScale: Double
    }

    private func manualTwist(at date: Date) -> Double {
        if let start = unwindStart {
            let x = min(max(date.timeIntervalSince(start) / M.unwind, 0), 1)
            return unwindFrom * M.unwound(x)
        }
        return twist.value(at: date)
    }

    private func field(at date: Date, size: CGSize, block: CGRect) -> Field {
        if manual {
            let tw = manualTwist(at: date)
            return Field(centre: CGPoint(x: centreX.value(at: date), y: centreY.value(at: date)), twist: tw, radius: M.radius(forTwist: tw), spin: 0, ring: 0, ringScale: 1)
        }
        let stir: M.Stir
        if autoPlay, !reduceMotion {
            stir = M.stir(at: max(0, date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)))
        } else {
            stir = .rest
        }
        let centre = CGPoint(x: block.midX + stir.orbit.x * M.orbitX * size.width, y: block.midY + stir.orbit.y * M.orbitY * block.height)
        return Field(centre: centre, twist: stir.twist, radius: stir.radius, spin: stir.spin, ring: stir.ring, ringScale: stir.ringScale)
    }

    private func drag(size: CGSize, block: CGRect) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                let now = Date()
                if !dragging {
                    // Pick the swirl up where it is, so the touch never jumps.
                    let current = field(at: now, size: size, block: block)
                    dragging = true
                    manual = true
                    peaked = false
                    twist = Tween(current.twist)
                    unwindStart = nil
                    centreX = Tween(current.centre.x)
                    centreY = Tween(current.centre.y)
                    touches += 1
                    haptic.impactOccurred()
                }
                let p = value.location
                centreX.retarget(p.x, at: now, duration: 0.1, curve: .easeOut)
                centreY.retarget(p.y, at: now, duration: 0.1, curve: .easeOut)
                let v = value.velocity
                let speed = hypot(v.width, v.height)
                // Stirring clockwise around the words twists one way, anticlockwise the other.
                let cross = (p.x - block.midX) * v.height - (p.y - block.midY) * v.width
                if speed > 40 { stirSign = cross >= 0 ? 1 : -1 }
                let target = stirSign * M.peak * min(1, max(0, 0.35 + speed / 900))
                twist.retarget(target, at: now, duration: 0.35, curve: .easeOut)
                if !peaked, abs(twist.value(at: now)) > 7 {
                    peaked = true
                    haptic.impactOccurred(intensity: 0.7)
                }
            }
            .onEnded { _ in
                dragging = false
                touches += 1
            }
    }

    /// 2 s after the last touch the swirl unwinds, and the loop starts over from rest.
    private func settle() async {
        guard touches > 0 else { return }
        try? await Task.sleep(for: .seconds(M.userHold))
        guard !Task.isCancelled, !dragging else { return }
        let now = Date()
        unwindFrom = twist.value(at: now)
        unwindStart = now
        try? await Task.sleep(for: .seconds(M.unwind))
        guard !Task.isCancelled, !dragging else { return }
        unwindStart = nil
        twist = Tween(0)
        origin = .now
        manual = false
    }
}

#Preview {
    StripeSwirlType(autoPlay: true)
}
