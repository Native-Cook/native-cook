import SwiftUI
import UIKit

/// Brush Ink Splash — the launch screen of "Sumi", a journaling app. On warm rice paper a loaded
/// brush lands with a blot and paints an ensō clockwise, thinning and running dry into bristle
/// streaks, then writes the wordmark "Sumi" stroke by stroke; the ink bleeds into the fibres at every
/// edge, a few drops lie where the brush landed and flicked off. Then the sheet slides up to reveal
/// the journal's home screen, and a fresh sheet fades back in. The loop is in BrushInkSplashMotion
/// (loop 8 s).
///
/// Every stroke is shaded by one Metal shader in stroke coordinates (distance along the paint
/// clock, position across the stroke), so the wet tip, ragged edges, dry streaks, pooled rims and
/// bleeding all follow the brush, not the screen.
///
/// Touch: draw with a finger and the same brush paints under it, thinner the faster it moves,
/// drying out along a long stroke; lift and the ink bleeds (0.6 s). A new touch starts a new stroke.
/// 2 s after the last touch the ink fades (0.9 s) and the loop starts again. Reduced motion: the
/// painted splash, still; drawing still works, and the bleed appears at once.
struct BrushInkSplash: View {
    /// Paints the mark and the wordmark, reveals the home screen and starts over, on a loop of `loop` s.
    var autoPlay = false

    static let loop = BrushInkSplashMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let layout = BrushInkSplashLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            ZStack(alignment: .topLeading) {
                BrushInkSplashHome(top: insets.top, bottom: insets.bottom)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                BrushInkSplashScene(
                    layout: layout, top: insets.top, bottom: insets.bottom,
                    autoPlay: autoPlay && !reduceMotion, reduced: reduceMotion
                )
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(BrushInkSplashPalette.background.ignoresSafeArea())
    }
}

enum BrushInkSplashPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(
            red: Double((v >> 16) & 0xFF) / 255,
            green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255,
            opacity: a
        )
    }

    static let background = hex(0xF3EEE4)
    static let ink = hex(0x1F1D22)
    static let mute = hex(0x7E766B)
}

/// Where everything sits, from the screen size and its safe area (as in React Native).
struct BrushInkSplashLayout: Equatable {
    var width: Double
    var height: Double
    var ensoX: Double
    var ensoY: Double
    var ensoR: Double
    var wordTop: Double
    var wordK: Double
    var taglineTop: Double
    var footerTop: Double

    /// How far past the ensō's nominal half-width its mesh reaches (room for the bleed).
    static let margin = 1.7

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        let r = min(width * 0.34, 140, (height - top - bottom) * 0.18)
        let y = top + (height - top - bottom) * 0.38
        let k = min(1.3, width / 300)
        let wordTop = y + r + 44
        ensoX = width / 2
        ensoY = y
        ensoR = r
        self.wordTop = wordTop
        wordK = k
        taglineTop = wordTop + 60 * k + 26
        footerTop = height - max(bottom, 16) - 34
    }
}

/// The splash's strokes (ensō first, then the wordmark), their plan, and where the ensō starts and
/// ends (for the ink drops). Built once per layout.
struct BrushInkSplashArt {
    var plan: BrushInkSplashPlan
    var shapes: [BrushInkSplashShape]
    var ensoStart: CGPoint
    var ensoEnd: CGPoint
    var ensoEndAngle: Double
    var landing: [(x: Double, y: Double, r: Double)]
    var flick: [(x: Double, y: Double, r: Double)]

    init(_ l: BrushInkSplashLayout) {
        typealias M = BrushInkSplashMotion
        let strokes = [M.enso(cx: l.ensoX, cy: l.ensoY, R: l.ensoR)] + M.wordmark(cx: l.width / 2, top: l.wordTop, k: l.wordK)
        let plan = BrushInkSplashPlan(strokes)
        self.plan = plan
        shapes = strokes.enumerated().map { i, s in
            BrushInkSplashShape(s, index: i, start: plan.starts[i], length: plan.lengths[i], margin: i == 0 ? BrushInkSplashLayout.margin : 1.6)
        }
        let e = strokes[0]
        let n = e.xs.count
        ensoStart = CGPoint(x: e.xs[0], y: e.ys[0])
        ensoEnd = CGPoint(x: e.xs[n - 1], y: e.ys[n - 1])
        ensoEndAngle = atan2(e.ys[n - 1] - e.ys[n - 4], e.xs[n - 1] - e.xs[n - 4])
        landing = M.drops(0, angle: 0)
        flick = M.drops(1, angle: ensoEndAngle)
    }
}

private struct BrushInkSplashScene: View {
    let layout: BrushInkSplashLayout
    let top: Double
    let bottom: Double
    let autoPlay: Bool
    let reduced: Bool

    @State private var engine = BrushInkSplashEngine()
    /// Keeps the timeline running while a touch's animations play outside autoPlay.
    @State private var awake = false
    @State private var down = false

    private typealias P = BrushInkSplashPalette

    var body: some View {
        let art = engine.art(for: layout)
        TimelineView(.animation(paused: !(autoPlay || awake))) { timeline in
            let frame = engine.frame(at: timeline.date.timeIntervalSinceReferenceDate, autoPlay: autoPlay, reduced: reduced)
            let _ = settle(frame.settled)
            let pose = frame.pose
            ZStack(alignment: .topLeading) {
                BrushInkSplashPaper(layout: layout).equatable()

                ForEach(0..<art.shapes.count, id: \.self) { i in
                    BrushInkSplashInkView(
                        shape: art.shapes[i], head: pose.head, bleed: pose.bleed, alpha: pose.ink
                    )
                }

                BrushInkSplashDrops(art: art, pose: pose)
                    .frame(width: layout.width, height: layout.height)

                if let user = frame.userShape {
                    BrushInkSplashInkView(shape: user, head: 1e5, bleed: frame.userBleed, alpha: frame.userAlpha)
                }

                let words = pose.ink * pose.bleed
                Text("A page a day, in ink.")
                    .font(.custom("Georgia-Italic", size: 17))
                    .foregroundStyle(P.hex(0x5D564F))
                    .frame(width: layout.width - 48)
                    .opacity(words)
                    .offset(x: 24, y: layout.taglineTop + (1 - pose.bleed) * 6)
                Text("Syncing 214 pages")
                    .font(.system(size: 12, weight: .semibold).monospacedDigit())
                    .kerning(1.2)
                    .foregroundStyle(P.mute)
                    .frame(width: layout.width - 48)
                    .opacity(words)
                    .offset(x: 24, y: layout.footerTop + (1 - pose.bleed) * 6)
            }
            .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(draw)
            .accessibilityElement()
            .accessibilityLabel("Sumi. Draw on the paper with the brush.")
            .offset(y: -pose.slide * layout.height)
            // A fresh sheet fades back in over the home screen (React Native fades the sheet's
            // opacity). The paper is opaque, so a copy of the home screen on top fading out the
            // other way gives the same picture without relying on opacity reaching the shaders.
            .overlay(alignment: .topLeading) {
                if pose.paper < 0.999 {
                    BrushInkSplashHome(top: top, bottom: bottom)
                        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
                        .opacity(1 - pose.paper)
                        .allowsHitTesting(false)
                }
            }
        }
    }

    private func settle(_ settled: Bool) {
        guard settled, awake else { return }
        DispatchQueue.main.async { awake = false }
    }

    private var draw: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.timeIntervalSinceReferenceDate
                if !down {
                    down = true
                    if !awake { awake = true }
                    engine.drawBegan(value.location, now: now, reduced: reduced)
                } else {
                    engine.drawMoved(value.location, velocity: value.velocity, now: now)
                }
            }
            .onEnded { _ in
                down = false
                engine.drawEnded(now: Date.timeIntervalSinceReferenceDate, reduced: reduced)
            }
    }
}

/// One stroke, shaded over its own bounds.
private struct BrushInkSplashInkView: View {
    let shape: BrushInkSplashShape
    let head: Double
    let bleed: Double
    let alpha: Double

    var body: some View {
        let b = shape.bounds
        // Nothing to draw before the brush reaches the stroke, or once the ink has faded.
        if !b.isNull, alpha >= 0.002, head > shape.start - shape.cap {
            Rectangle()
                .fill(.white)
                .frame(width: b.width, height: b.height)
                .colorEffect(ShaderLibrary.brushInkSplashInk(
                    .float2(b.minX, b.minY),
                    .floatArray(shape.points),
                    .floatArray(shape.chunks),
                    .float4(shape.start, shape.length, shape.dry, shape.index),
                    .float4(shape.cap, shape.endCap, shape.margin, head),
                    .float2(bleed, alpha)
                ))
                .offset(x: b.minX, y: b.minY)
                .allowsHitTesting(false)
        }
    }
}

/// Drops where the brush landed and where it flicked off the end of the circle.
private struct BrushInkSplashDrops: View {
    let art: BrushInkSplashArt
    let pose: BrushInkSplashPose

    var body: some View {
        Canvas { ctx, _ in
            typealias M = BrushInkSplashMotion
            var path = Path()
            func dot(_ x: Double, _ y: Double, _ r: Double) {
                path.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            }
            if pose.landed >= 0 {
                let g = 0.6 + 0.4 * M.easeOut(pose.landed / 0.15)
                for d in art.landing { dot(art.ensoStart.x + d.x, art.ensoStart.y + d.y, d.r * g) }
            }
            if pose.flicked >= 0 {
                for (i, d) in art.flick.enumerated() {
                    let at = pose.flicked - Double(i) * 0.04
                    if at < 0 { continue }
                    let g = 0.6 + 0.4 * M.easeOut(at / 0.12)
                    dot(art.ensoEnd.x + d.x, art.ensoEnd.y + d.y, d.r * g)
                }
            }
            guard !path.isEmpty, pose.ink > 0.001 else { return }
            let bleed = pose.ink * pose.bleed * 0.35
            if bleed > 0.001 {
                ctx.drawLayer { l in
                    l.opacity = bleed
                    l.addFilter(.blur(radius: 2.5))
                    l.fill(path, with: .color(BrushInkSplashPalette.ink))
                }
            }
            var l = ctx
            l.opacity = pose.ink * 0.9
            l.fill(path, with: .color(BrushInkSplashPalette.ink))
        }
        .allowsHitTesting(false)
    }
}

/// The rice paper; it never changes, so its view is only drawn again when the sheet moves.
private struct BrushInkSplashPaper: View, Equatable {
    let layout: BrushInkSplashLayout

    var body: some View {
        Rectangle()
            .fill(.white)
            .frame(width: layout.width, height: layout.height)
            .colorEffect(ShaderLibrary.brushInkSplashPaper(.float2(layout.width, layout.height)))
            .allowsHitTesting(false)
    }
}

/// The journal's home screen, under the splash.
private struct BrushInkSplashHome: View {
    let top: Double
    let bottom: Double

    private typealias P = BrushInkSplashPalette

    private static let pages = [
        ("Rain on the tram window", "Thu", "312"),
        ("Tea with my mother", "Tue", "540"),
        ("First frost at the allotment", "Sun", "198"),
        ("A walk to the harbour", "Fri", "421"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                BrushInkSplashMark()
                    .stroke(P.ink, style: StrokeStyle(lineWidth: 3.2, lineCap: .round))
                    .frame(width: 30, height: 30)
                Text("Sumi")
                    .font(.system(size: 28, weight: .bold))
                    .kerning(-0.4)
                    .foregroundStyle(P.ink)
                Spacer(minLength: 0)
                Text("AK")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(P.ink)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(P.hex(0xD9CDB8)))
            }
            .frame(height: 44)
            Text("Saturday, 25 October")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(P.mute)
                .padding(.top, 4)
                .padding(.bottom, 20)

            VStack(alignment: .leading, spacing: 10) {
                Text("TODAY'S PAGE")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.8)
                    .foregroundStyle(P.hex(0xA1533A))
                Text("What did the light look like this morning?")
                    .font(.custom("Georgia-Bold", size: 21))
                    .lineSpacing(3)
                    .foregroundStyle(P.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Start writing")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(P.hex(0xF6F1E7))
                    .padding(.horizontal, 18)
                    .frame(height: 40)
                    .background(Capsule().fill(P.ink))
                    .padding(.top, 4)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(P.hex(0xFBF8F1))
                    .shadow(color: P.hex(0x3C2D19, 0.12), radius: 1, x: 0, y: 1)
                    .shadow(color: P.hex(0x3C2D19, 0.08), radius: 12, x: 0, y: 10)
            )

            Text("Recent pages")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(P.ink)
                .padding(.top, 28)
                .padding(.bottom, 6)
            ForEach(0..<Self.pages.count, id: \.self) { i in
                let page = Self.pages[i]
                VStack(alignment: .leading, spacing: 3) {
                    Text(page.0)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(P.ink)
                    Text("\(page.1) · \(page.2) words")
                        .font(.system(size: 13).monospacedDigit())
                        .foregroundStyle(P.mute)
                }
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(P.hex(0x1F1D22, 0.15)).frame(height: 1 / 3)
                }
            }
            Spacer(minLength: 0)
            HStack {
                Text("12-day streak")
                    .font(.system(size: 15, weight: .semibold).monospacedDigit())
                    .foregroundStyle(P.ink)
                Spacer(minLength: 8)
                HStack(spacing: 6) {
                    ForEach(0..<7, id: \.self) { i in
                        ZStack {
                            if i < 6 { Circle().fill(P.ink) }
                            Circle().stroke(P.ink, lineWidth: 1.2)
                        }
                        .frame(width: 10, height: 10)
                        .frame(width: 12, height: 12)
                    }
                }
            }
            .padding(.top, 12)
        }
        .padding(.horizontal, 24)
        .padding(.top, top + 14)
        .padding(.bottom, max(bottom, 16) + 8)
        .background(P.background)
    }
}

/// The app's mark on the home screen: an open ensō (the React Native SVG arc
/// "M8 22.5A10 10 0 1 1 22.6 22" in a 30 × 30 box).
private struct BrushInkSplashMark: Shape {
    func path(in rect: CGRect) -> Path {
        let k = min(rect.width, rect.height) / 30
        var p = Path()
        // The arc's centre, from its two ends and its radius (large arc, clockwise on screen).
        let center = CGPoint(x: 15.07 * k, y: 15.42 * k)
        p.addArc(center: center, radius: 10 * k, startAngle: .degrees(135.0), endAngle: .degrees(41.1), clockwise: false)
        return p
    }
}

/// Who owns the screen (the loop or the finger), the finger's stroke and its animated values.
@MainActor
final class BrushInkSplashEngine {
    private typealias M = BrushInkSplashMotion
    private typealias C = BrushInkSplashCurve

    private static let idle = 2.0
    private static let reset = 0.9
    // The finger's brush: half-width from wMax (slow) to wMin (fast), easing toward it; at most
    // maxPoints points, one every step pt; dry over dryLength pt.
    private static let wMax = 9.0
    private static let wMin = 3.2
    private static let maxPoints = 220
    private static let step = 2.5
    private static let dryLength = 700.0

    private var cachedArt: BrushInkSplashArt?
    private var cachedLayout: BrushInkSplashLayout?

    private var configured = false
    private var reduced = false
    private var loopStart: Double?
    private var clock = 0.0
    private var user = false
    private var lastTouch = 0.0
    private var resetAt = -1.0
    private var drawing = false

    private var head = 0.0
    private var bleed = 0.0
    private var ink = BrushInkSplashTrack(0)
    private var slide = BrushInkSplashTrack(0)
    private var paper = BrushInkSplashTrack(1)
    private var landedAt = -1.0
    private var flickedAt = -1.0

    private var xs: [Double] = []
    private var ys: [Double] = []
    private var hws: [Double] = []
    private var userShape: BrushInkSplashShape?
    private var strokeBleed = BrushInkSplashTrack(0)
    private var strokeAlpha = BrushInkSplashTrack(0)

    private let impact = UIImpactFeedbackGenerator(style: .light)

    func art(for layout: BrushInkSplashLayout) -> BrushInkSplashArt {
        if let cachedArt, cachedLayout == layout { return cachedArt }
        let a = BrushInkSplashArt(layout)
        cachedArt = a
        cachedLayout = layout
        return a
    }

    private var plan: BrushInkSplashPlan? { cachedArt?.plan }

    struct Frame {
        var pose: BrushInkSplashPose
        var userShape: BrushInkSplashShape?
        var userBleed: Double
        var userAlpha: Double
        /// Nothing is moving and nothing will until the next touch.
        var settled: Bool
    }

    private func configure(reduced: Bool) {
        guard !configured || reduced != self.reduced else { return }
        configured = true
        self.reduced = reduced
        user = false
        drawing = false
        resetAt = -1
        loopStart = nil
        clock = 0
        clearStroke()
    }

    func frame(at now: Double, autoPlay: Bool, reduced: Bool) -> Frame {
        configure(reduced: reduced)
        guard let plan else { return Frame(pose: BrushInkSplashPose(), userShape: nil, userBleed: 0, userAlpha: 0, settled: true) }
        if user && !reduced && !drawing { step(now) }
        if !user {
            if autoPlay {
                let start = loopStart ?? now
                loopStart = start
                clock = (now - start).truncatingRemainder(dividingBy: M.loop)
            } else {
                loopStart = nil
                clock = 0
            }
            let pose = reduced ? M.paintedPose(plan: plan) : M.loopPose(clock, plan: plan)
            return Frame(pose: pose, userShape: nil, userBleed: 0, userAlpha: 0, settled: !autoPlay)
        }
        let pose = BrushInkSplashPose(
            head: head,
            bleed: bleed,
            ink: ink.value(at: now),
            slide: slide.value(at: now),
            paper: paper.value(at: now),
            landed: landedAt < 0 ? -1 : now - landedAt,
            flicked: flickedAt < 0 ? -1 : now - flickedAt
        )
        let tracks = [ink, slide, paper, strokeBleed, strokeAlpha]
        let settled = reduced && !drawing && tracks.allSatisfy { $0.done(at: now) }
        return Frame(
            pose: pose,
            userShape: userShape,
            userBleed: strokeBleed.value(at: now),
            userAlpha: strokeAlpha.value(at: now),
            settled: settled
        )
    }

    private func step(_ t: Double) {
        if resetAt < 0 && t - lastTouch > Self.idle {
            strokeAlpha.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            ink.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            resetAt = t + Self.reset + 0.05
        } else if resetAt > 0 && t >= resetAt {
            resetAt = -1
            clearStroke()
            clock = 0
            loopStart = t
            user = false
        }
    }

    private func clearStroke() {
        xs = []
        ys = []
        hws = []
        userShape = nil
    }

    /// Takes over from the loop without a jump: the splash freezes where the loop was, then fades.
    private func touched(_ now: Double, reduced: Bool) {
        lastTouch = now
        resetAt = -1
        if user { return }
        guard let plan else { return }
        let p = reduced ? M.paintedPose(plan: plan) : M.loopPose(clock, plan: plan)
        head = p.head
        bleed = p.bleed
        ink.set(p.ink)
        slide.set(p.slide)
        paper.set(p.paper)
        landedAt = p.landed >= 0 ? now - p.landed : -1
        flickedAt = p.flicked >= 0 ? now - p.flicked : -1
        user = true
        if reduced { return }
        // Clear the sheet for the finger: the splash fades, a slid sheet comes back.
        ink.timing(0, duration: 0.3, curve: .easeOut, now: now)
        slide.timing(0, duration: 0.3, curve: .easeOut, now: now)
        paper.timing(1, duration: 0.3, curve: .easeOut, now: now)
    }

    private func rebuild() {
        guard xs.count >= 2 else {
            userShape = nil
            return
        }
        let s = BrushInkSplashStroke(xs: xs, ys: ys, hw: hws, dry: 0.8)
        userShape = BrushInkSplashShape(s, index: 0, start: 0, length: Self.dryLength, margin: 1.7)
    }

    func drawBegan(_ p: CGPoint, now: Double, reduced: Bool) {
        touched(now, reduced: reduced)
        drawing = true
        xs = [p.x]
        ys = [p.y]
        hws = [Self.wMax * 1.15]
        strokeBleed.set(0)
        strokeAlpha.set(1)
        rebuild()
    }

    func drawMoved(_ p: CGPoint, velocity: CGSize, now: Double) {
        lastTouch = now
        let n = xs.count
        guard n > 0, n < Self.maxPoints else { return }
        let d = hypot(p.x - xs[n - 1], p.y - ys[n - 1])
        if d < Self.step { return }
        // Faster strokes are thinner; the width eases toward its target so it never jumps.
        let speed = hypot(velocity.width, velocity.height)
        let target = Self.wMax - (Self.wMax - Self.wMin) * min(1, speed / 1600)
        let w = hws[n - 1] + (target - hws[n - 1]) * 0.25
        xs.append(p.x)
        ys.append(p.y)
        hws.append(w)
        rebuild()
    }

    func drawEnded(now: Double, reduced: Bool) {
        drawing = false
        lastTouch = now
        if reduced {
            strokeBleed.set(1)
        } else {
            strokeBleed.timing(1, duration: 0.6, curve: .easeOut, now: now)
        }
        if xs.count > 8 { impact.impactOccurred() }
    }
}

/// A cubic Bézier easing curve, like Reanimated's `Easing.bezier`.
struct BrushInkSplashCurve {
    let x1: Double, y1: Double, x2: Double, y2: Double

    static let easeOut = BrushInkSplashCurve(x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    static let easeInOut = BrushInkSplashCurve(x1: 0.77, y1: 0, x2: 0.175, y2: 1)

    func callAsFunction(_ x: Double) -> Double {
        let u = min(1, max(0, x))
        var s = u
        for _ in 0..<8 {
            let xs = bez(s, x1, x2) - u
            let d = dbez(s, x1, x2)
            if abs(xs) < 1e-5 || abs(d) < 1e-6 { break }
            s = min(1, max(0, s - xs / d))
        }
        return bez(s, y1, y2)
    }

    private func bez(_ s: Double, _ a: Double, _ b: Double) -> Double {
        let m = 1 - s
        return 3 * m * m * s * a + 3 * m * s * s * b + s * s * s
    }

    private func dbez(_ s: Double, _ a: Double, _ b: Double) -> Double {
        let m = 1 - s
        return 3 * m * m * a + 6 * m * s * (b - a) + 3 * s * s * (1 - b)
    }
}

/// One value the finger drives, as Reanimated's `withTiming` would.
struct BrushInkSplashTrack {
    private var from: Double
    private var to: Double
    private var start = 0.0
    private var duration = 0.0
    private var curve = BrushInkSplashCurve.easeInOut

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at t: Double) -> Double {
        let u = t - start
        if duration <= 0 || u >= duration { return to }
        if u <= 0 { return from }
        return from + (to - from) * curve(u / duration)
    }

    func done(at t: Double) -> Bool { t - start >= duration }

    mutating func set(_ value: Double) {
        from = value
        to = value
        duration = 0
    }

    mutating func timing(_ target: Double, duration: Double, curve: BrushInkSplashCurve, now: Double) {
        from = value(at: now)
        to = target
        start = now
        self.duration = duration
        self.curve = curve
    }
}
