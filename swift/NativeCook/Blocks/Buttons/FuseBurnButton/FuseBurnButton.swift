import SwiftUI
import UIKit

/// Fuse Burn Button — an event's ticket screen, "Launch Night — Rooftop 27, 21:00". A braided fuse
/// is wrapped round the "Light it up" capsule. In the loop (FuseBurnButtonMotion, loop 9 s) a match
/// flares at the capsule's left end, a spark runs clockwise round it (sputtering once at 40 %),
/// the cord behind it turns to crinkled charcoal under rising smoke, and when the spark meets the
/// start the capsule bursts into 30 sparks, lights up amber, reads "You're in", and the count of
/// people going rolls from 211 to 212; then the cord re-weaves itself and the screen rests.
///
/// Touch: hold the capsule to burn (a full lap takes 4.2 s); let go early and the spark sputters
/// and dies where it is, the next hold relights it there. Drag a finger along the cord just ahead
/// of the spark to pull the burn along (the finger is the match). 2 s after the last touch the cord
/// re-weaves (0.85 s) and the loop starts again. Reduced motion: no spark or smoke; a tap on the
/// capsule fades it between "Light it up" and "You're in".
struct FuseBurnButton: View {
    /// Lights, burns, bursts and re-weaves the fuse by itself, on a loop of `loop` s.
    var autoPlay = false

    static let loop = FuseBurnButtonMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let layout = FuseBurnButtonLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            FuseBurnButtonScene(layout: layout, autoPlay: autoPlay && !reduceMotion, reduced: reduceMotion)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(FuseBurnButtonPalette.background.ignoresSafeArea())
    }
}

/// The fuse sits this far outside the capsule's outline.
private let fuseBurnButtonFuseOffset = 8.0
/// Touch slop round the capsule.
private let fuseBurnButtonSlop = 22.0
/// The count's line height (it rolls by one line).
private let fuseBurnButtonCountH = 22.0

private struct FuseBurnButtonScene: View {
    let layout: FuseBurnButtonLayout
    let autoPlay: Bool
    let reduced: Bool

    @State private var engine = FuseBurnButtonEngine()
    /// Keeps the timeline running while a touch's animations play outside autoPlay.
    @State private var awake = false
    @State private var down = false

    private typealias P = FuseBurnButtonPalette

    var body: some View {
        let capsule = layout.capsule
        let slop = fuseBurnButtonSlop
        let fuse = engine.fuse(for: capsule)

        ZStack(alignment: .topLeading) {
            FuseBurnButtonStatic(layout: layout).equatable()

            TimelineView(.animation(paused: !(autoPlay || awake))) { timeline in
                let frame = engine.frame(at: timeline.date.timeIntervalSinceReferenceDate, autoPlay: autoPlay, reduced: reduced)
                let _ = settle(frame.settled)
                ZStack(alignment: .topLeading) {
                    FuseBurnButtonCanvas(layout: layout, fuse: fuse, pose: frame.pose)
                        .frame(width: layout.width, height: layout.height)
                    labels(frame.pose)
                    countRoll(frame.pose.count)
                }
                .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            header
            posterInfo
            rows

            Text("Hold to light · drag along the fuse")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(P.hint)
                .multilineTextAlignment(.center)
                .frame(width: layout.width - 48)
                .offset(x: 24, y: layout.hintTop)
                .allowsHitTesting(false)

            Color.clear
                .frame(width: capsule.width + slop * 2, height: capsule.height + slop * 2)
                .contentShape(Rectangle())
                .gesture(touch)
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Light it up. Hold to burn the fuse.")
                .offset(x: capsule.minX - slop, y: capsule.minY - slop)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
    }

    private func settle(_ settled: Bool) {
        guard settled, awake else { return }
        DispatchQueue.main.async { awake = false }
    }

    // MARK: Text

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("LAUNCH NIGHT")
                .font(.system(size: 11, weight: .bold))
                .kerning(2.4)
                .foregroundStyle(P.hex(0xFFB46B))
            Text("Rooftop 27")
                .font(.system(size: 32, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(P.ink)
            Text("Sat 22 Nov · 21:00 · Pier District")
                .font(.system(size: 14, weight: .medium).monospacedDigit())
                .foregroundStyle(P.mute)
        }
        .frame(width: layout.width - 48, alignment: .leading)
        .offset(x: 24, y: layout.headerTop)
        .allowsHitTesting(false)
    }

    private var posterInfo: some View {
        let poster = layout.poster
        return VStack(alignment: .leading, spacing: 6) {
            Text("LINEUP")
                .font(.system(size: 10, weight: .bold))
                .kerning(1.4)
                .foregroundStyle(P.hex(0xFFD9A8))
                .padding(.horizontal, 8)
                .frame(height: 20)
                .background(Capsule().fill(P.rgba(10, 10, 30, 0.55)))
            Text("Mara Quell · The Low Tides · Okoro")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(P.hex(0xFBF3EA))
                .lineLimit(1)
                .shadow(color: P.rgba(10, 6, 30, 0.6), radius: 4, x: 0, y: 1)
        }
        .frame(width: poster.width - 32, alignment: .leading)
        .offset(x: poster.minX + 16, y: poster.maxY - 64)
        .allowsHitTesting(false)
    }

    private var rows: some View {
        let avatars: [(String, UInt32)] = [("JR", 0xC46B4A), ("AM", 0x5F7FBF), ("SK", 0x8C6BB8)]
        return VStack(spacing: 14) {
            HStack(spacing: 0) {
                HStack(spacing: -8) {
                    ForEach(0..<avatars.count, id: \.self) { i in
                        Text(avatars[i].0)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(P.hex(0xFFF6EE))
                            .frame(width: 28, height: 28)
                            .background(Circle().fill(P.hex(avatars[i].1)))
                            .overlay(Circle().strokeBorder(P.hex(0x111833), lineWidth: 2))
                    }
                }
                .padding(.trailing, 10)
                // The count rolls inside the timeline; this keeps its place in the row.
                Text("211")
                    .font(.system(size: 17, weight: .bold).monospacedDigit())
                    .hidden()
                    .padding(.trailing, 5)
                Text("going · 12 friends")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(P.mute)
                Spacer(minLength: 0)
            }
            .frame(height: 30)
            HStack(spacing: 0) {
                Text("General admission")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(P.mute)
                Spacer(minLength: 8)
                Text("$24.00")
                    .font(.system(size: 17, weight: .bold).monospacedDigit())
                    .foregroundStyle(P.ink)
            }
            .frame(height: 30)
        }
        .frame(width: layout.capsule.width)
        .offset(x: layout.capsule.minX, y: layout.rowsTop)
        .allowsHitTesting(false)
    }

    /// 211 above 212 in a one-line window, rolled by `count`; drawn where the row keeps its place.
    private func countRoll(_ count: Double) -> some View {
        let h = fuseBurnButtonCountH
        let x = layout.capsule.minX + 28 * 3 - 16 + 10
        return VStack(spacing: 0) {
            Text("211").frame(height: h)
            Text("212").frame(height: h)
        }
        .font(.system(size: 17, weight: .bold).monospacedDigit())
        .foregroundStyle(P.ink)
        .offset(y: -count * h)
        .frame(height: h, alignment: .top)
        .clipped()
        .offset(x: x, y: layout.rowsTop + (30 - h) / 2)
    }

    private func labels(_ pose: FuseBurnButtonPose) -> some View {
        let c = layout.capsule
        let l = pose.label
        return ZStack {
            HStack(spacing: 8) {
                FuseBurnButtonIcon(flame: true)
                Text("Light it up")
                    .font(.system(size: 18, weight: .bold))
                    .kerning(0.2)
                    .foregroundStyle(P.cream)
            }
            .opacity(1 - l)
            .scaleEffect(1 - l * 0.04)
            .offset(y: -l * 8)
            HStack(spacing: 8) {
                FuseBurnButtonIcon(flame: false)
                Text("You're in")
                    .font(.system(size: 18, weight: .bold))
                    .kerning(0.2)
                    .foregroundStyle(P.inInk)
            }
            .opacity(l)
            .scaleEffect(0.96 + l * 0.04)
            .offset(y: (1 - l) * 8)
        }
        .frame(width: c.width, height: c.height)
        .offset(x: c.minX, y: c.minY)
    }

    // MARK: Touch

    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.timeIntervalSinceReferenceDate
                let p = CGPoint(
                    x: value.location.x + layout.capsule.minX - fuseBurnButtonSlop,
                    y: value.location.y + layout.capsule.minY - fuseBurnButtonSlop
                )
                if !down {
                    down = true
                    if !awake { awake = true }
                    engine.touchDown(now: now, reduced: reduced)
                } else {
                    engine.touchMove(p, now: now, reduced: reduced)
                }
            }
            .onEnded { _ in
                down = false
                engine.touchUp(now: Date.timeIntervalSinceReferenceDate)
            }
    }
}

private struct FuseBurnButtonIcon: View {
    let flame: Bool

    var body: some View {
        Canvas { ctx, _ in
            if flame {
                ctx.fill(FuseBurnButtonArt.flame, with: .color(FuseBurnButtonPalette.cream))
                ctx.opacity = 0.6
                ctx.stroke(FuseBurnButtonArt.flameBase, with: .color(FuseBurnButtonPalette.cream),
                           style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            } else {
                ctx.stroke(FuseBurnButtonArt.check, with: .color(FuseBurnButtonPalette.inInk),
                           style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: flame ? 16 : 18, height: 18)
    }
}

/// Everything that never moves: the backdrop shader, the poster and the capsule's body.
private struct FuseBurnButtonStatic: View, Equatable {
    let layout: FuseBurnButtonLayout

    @Environment(\.displayScale) private var displayScale

    static func == (a: Self, b: Self) -> Bool { a.layout == b.layout }

    var body: some View {
        let poster = FuseBurnButtonArt.poster(layout.poster, scale: min(displayScale, 3))
        ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(.white)
                .frame(width: layout.width, height: layout.height)
                .colorEffect(ShaderLibrary.fuseBurnButtonBackdrop(
                    .float2(layout.width, layout.height),
                    .float2(layout.width / 2, layout.capsule.midY)
                ))
            Canvas { ctx, _ in
                draw(&ctx, poster: poster)
            }
            .frame(width: layout.width, height: layout.height)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func draw(_ ctx: inout GraphicsContext, poster image: UIImage) {
        typealias P = FuseBurnButtonPalette
        let p = layout.poster
        // The poster, with a soft shadow and a hairline of light on its top edge.
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 22))
            l.fill(Path(roundedRect: CGRect(x: p.minX, y: p.minY + 14, width: p.width, height: p.height - 10), cornerRadius: 24),
                   with: .color(P.rgba(0, 0, 8, 0.55)))
        }
        ctx.draw(Image(uiImage: image), in: p)
        ctx.stroke(Path(roundedRect: p.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 23.5), with: .linearGradient(
            Gradient(colors: [P.rgba(255, 255, 255, 0.22), P.rgba(255, 255, 255, 0.03), P.rgba(255, 255, 255, 0.06)]),
            startPoint: CGPoint(x: 0, y: p.minY), endPoint: CGPoint(x: 0, y: p.maxY)
        ), lineWidth: 1)

        // The capsule's two shadows and its body.
        let c = layout.capsule
        let r = c.height / 2
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 18))
            l.fill(Path(roundedRect: CGRect(x: c.minX + 6, y: c.minY + 14, width: c.width - 12, height: c.height), cornerRadius: r),
                   with: .color(P.rgba(0, 0, 10, 0.6)))
        }
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 2.5))
            l.fill(Path(roundedRect: c.offsetBy(dx: 0, dy: 1.5), cornerRadius: r), with: .color(P.rgba(0, 0, 8, 0.7)))
        }
        ctx.fill(Path(roundedRect: c, cornerRadius: r), with: .linearGradient(
            Gradient(colors: [P.hex(0x2D3768), P.hex(0x20284D), P.hex(0x171D3A)]),
            startPoint: CGPoint(x: 0, y: c.minY), endPoint: CGPoint(x: 0, y: c.maxY)
        ))
    }
}

/// The capsule's light, the fuse, its smoke and its sparks, for one pose.
private struct FuseBurnButtonCanvas: View {
    let layout: FuseBurnButtonLayout
    let fuse: FuseBurnButtonFuse
    let pose: FuseBurnButtonPose

    private typealias P = FuseBurnButtonPalette

    var body: some View {
        Canvas { ctx, _ in draw(&ctx) }
    }

    private func draw(_ ctx: inout GraphicsContext) {
        let c = layout.capsule
        let r = c.height / 2
        let body = Path(roundedRect: c, cornerRadius: r)
        let s = fuse.at(pose.b)
        let sparkAt = CGPoint(x: s.x, y: s.y)

        // The capsule: lit from inside by the spark, then amber, a flash at the burst, its rim.
        let insideGlow = pose.spark * 0.45 * (1 - pose.lit)
        if insideGlow > 0.001 {
            var l = ctx
            l.opacity = insideGlow
            l.fill(body, with: .radialGradient(
                Gradient(colors: [P.rgba(255, 140, 60, 0.9), P.rgba(255, 110, 50, 0.25), P.rgba(255, 110, 50, 0)]),
                center: sparkAt, startRadius: 0, endRadius: 160
            ))
        }
        if pose.lit > 0.001 {
            var l = ctx
            l.opacity = pose.lit
            l.fill(body, with: .linearGradient(
                Gradient(colors: [P.hex(0xFFC06A), P.hex(0xFF9444), P.hex(0xF2672C), P.hex(0xFF8C3E)]),
                startPoint: CGPoint(x: c.minX, y: c.minY), endPoint: CGPoint(x: c.maxX, y: c.maxY)
            ))
        }
        let flash = pose.burstAge < 0 ? 0 : exp(-pose.burstAge * 5) * 0.8
        if flash > 0.001 {
            var l = ctx
            l.opacity = flash
            l.fill(body, with: .color(P.hex(0xFFF4DC)))
        }
        ctx.stroke(Path(roundedRect: c.insetBy(dx: 0.5, dy: 0.5), cornerRadius: r - 0.5), with: .linearGradient(
            Gradient(colors: [P.rgba(255, 255, 255, 0.28), P.rgba(255, 255, 255, 0.02), P.rgba(0, 0, 0, 0.25)]),
            startPoint: CGPoint(x: 0, y: c.minY), endPoint: CGPoint(x: 0, y: c.maxY)
        ), lineWidth: 1)

        // The fuse: a dark core, the braid, its twist highlights; charcoal and embers behind the spark.
        let paths = fuse.cordPaths(b: pose.b, w: pose.w)
        ctx.stroke(paths.cord, with: .color(P.hex(0x3B2812)), style: StrokeStyle(lineWidth: 6.2, lineCap: .round))
        ctx.stroke(paths.cord, with: .color(P.hex(0xB98D55)), style: StrokeStyle(lineWidth: 4.4, lineCap: .round))
        ctx.stroke(paths.ticks, with: .color(P.hex(0xF0D7A2, 0.85)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
        let charcoalOpacity = 1 - pose.w
        if charcoalOpacity > 0.001 {
            ctx.drawLayer { l in
                l.opacity = charcoalOpacity
                l.stroke(paths.charcoal, with: .color(P.hex(0x0B0A0C, 0.7)),
                         style: StrokeStyle(lineWidth: 3.4, lineCap: .round, lineJoin: .round))
                l.stroke(paths.charcoal, with: .color(P.hex(0x3A3230)),
                         style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
                l.stroke(paths.charcoal, with: .color(P.hex(0x9A8E84, 0.55)),
                         style: StrokeStyle(lineWidth: 0.8, dash: [1.5, 3.5]))
            }
        }
        let emberOpacity = min(1, pose.spark * 1.2) * (1 - pose.w)
        if emberOpacity > 0.001 {
            // Skia's "solid" blur: the stroke itself plus a soft glow round it, added as light.
            ctx.drawLayer { l in
                l.opacity = emberOpacity
                l.blendMode = .plusLighter
                l.drawLayer { g in
                    g.addFilter(.blur(radius: 2))
                    g.stroke(paths.ember, with: .color(P.hex(0xFF6A2E)), style: StrokeStyle(lineWidth: 3.4, lineCap: .round))
                }
                l.stroke(paths.ember, with: .color(P.hex(0xFF6A2E)), style: StrokeStyle(lineWidth: 3.4, lineCap: .round))
            }
        }

        // Smoke, then the spark: a glow, its core and the thrown sparks (added light).
        let smokeAlpha = FuseBurnButtonFuse.smokeOpacity(pose)
        if smokeAlpha > 0.001 {
            ctx.drawLayer { l in
                l.opacity = smokeAlpha
                l.addFilter(.blur(radius: 7))
                l.fill(fuse.smokePath(pose), with: .color(P.hex(0x9AA3C4)))
            }
        }
        let glowR = 20 + pose.flare * 34
        let glowAlpha = min(1, pose.spark + pose.flare)
        let sparks = fuse.sparkPaths(pose, cx: c.midX, cy: c.midY)
        ctx.drawLayer { l in
            l.blendMode = .plusLighter
            if glowAlpha > 0.001 {
                var g = l
                g.opacity = glowAlpha
                g.fill(Path(ellipseIn: CGRect(x: s.x - glowR, y: s.y - glowR, width: glowR * 2, height: glowR * 2)), with: .radialGradient(
                    Gradient(stops: [
                        .init(color: P.hex(0xFFF6E0), location: 0),
                        .init(color: P.hex(0xFFD27A), location: 0.18),
                        .init(color: P.rgba(255, 106, 46, 0.45), location: 0.5),
                        .init(color: P.rgba(255, 106, 46, 0), location: 1),
                    ]),
                    center: sparkAt, startRadius: 0, endRadius: glowR
                ))
            }
            l.stroke(sparks.warm, with: .color(P.hex(0xFF9A3C)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
            l.stroke(sparks.hot, with: .color(P.hex(0xFFF0C2)), style: StrokeStyle(lineWidth: 1.7, lineCap: .round))
        }
        if pose.spark > 0.001 {
            var l = ctx
            l.opacity = pose.spark
            l.fill(Path(ellipseIn: CGRect(x: s.x - 2.6, y: s.y - 2.6, width: 5.2, height: 5.2)), with: .color(P.hex(0xFFFAF0)))
        }
    }
}

/// Who owns the screen (the loop or the finger), and the finger's burn.
@MainActor
final class FuseBurnButtonEngine {
    private typealias M = FuseBurnButtonMotion
    private typealias C = FuseBurnButtonCurve

    // Burning under a held finger: a whole lap in 1 / burnRate s.
    private static let burnRate = 0.24
    // A drag pulls the burn along when it lands within scrubReach pt of the cord, ahead of the
    // spark by less than scrubAhead of a lap.
    private static let scrubReach = 34.0
    private static let scrubAhead = 0.12
    private static let idle = 2.0
    private static let reset = 0.85

    private var cachedFuse: FuseBurnButtonFuse?
    private var cachedRect = CGRect.null

    private var configured = false
    private var reduced = false
    private var loopStart: Double?
    private var clock = 0.0
    private var lastFrame: Double?
    private var user = false
    private var lastTouch = 0.0
    private var resetAt = -1.0
    private var holding = false
    private var fired = false

    private var b = 0.0
    private var w = FuseBurnButtonTrack(0)
    private var spark = 0.0
    private var flare = FuseBurnButtonTrack(0)
    private var speed = 0.0
    private var burstStart = -1.0
    private var lit = FuseBurnButtonTrack(0)
    private var label = FuseBurnButtonTrack(0)
    private var count = FuseBurnButtonTrack(0)

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let success = UINotificationFeedbackGenerator()

    func fuse(for capsule: CGRect) -> FuseBurnButtonFuse {
        if let cachedFuse, cachedRect == capsule { return cachedFuse }
        let f = FuseBurnButtonFuse(
            x: capsule.minX, y: capsule.minY, w: capsule.width, h: capsule.height, offset: fuseBurnButtonFuseOffset
        )
        cachedFuse = f
        cachedRect = capsule
        return f
    }

    private var length: Double { cachedFuse?.length ?? 1 }

    struct Frame {
        var pose: FuseBurnButtonPose
        /// Nothing is moving and nothing will until the next touch.
        var settled: Bool
    }

    private func configure(reduced: Bool) {
        guard !configured || reduced != self.reduced else { return }
        configured = true
        self.reduced = reduced
        holding = false
        resetAt = -1
        user = reduced
        loopStart = nil
        clock = 0
        b = 0
        spark = 0
        speed = 0
        burstStart = -1
        fired = false
        w.set(0)
        flare.set(0)
        lit.set(0)
        label.set(0)
        count.set(0)
    }

    func frame(at now: Double, autoPlay: Bool, reduced: Bool) -> Frame {
        configure(reduced: reduced)
        let dt = min(max(now - (lastFrame ?? now - 1.0 / 60), 0), 0.05)
        lastFrame = now
        if !user {
            if autoPlay {
                let start = loopStart ?? now
                loopStart = start
                clock = (now - start).truncatingRemainder(dividingBy: M.loop)
            } else {
                loopStart = nil
                clock = 0
            }
            return Frame(pose: M.loopPose(clock, length: length), settled: !autoPlay)
        }
        if !reduced { step(now, dt) }
        if !user { return Frame(pose: M.loopPose(clock, length: length), settled: !autoPlay) }
        let settled = reduced && [lit, label, count].allSatisfy { $0.done(at: now) }
        return Frame(pose: userPose(now), settled: settled)
    }

    private func step(_ t: Double, _ dt: Double) {
        if holding && !fired {
            // Burning: the spark brightens quickly and runs at a slightly uneven pace.
            let pace = Self.burnRate * (0.85 + 0.3 * sin(t * 3.1) * sin(t * 1.7))
            b = min(1, b + dt * pace)
            speed = pace * length
            spark += (1 - spark) * min(1, dt * 12)
            if b >= 1 { burst(t) }
        } else if spark > 0.001 {
            // Let go: the spark sputters and dies where it is.
            speed = 0
            spark *= exp(-dt * 3.2) * (0.92 + 0.08 * sin(t * 40))
        }
        if holding { return }
        if resetAt < 0 && t - lastTouch > Self.idle {
            w.timing(1, duration: Self.reset, curve: .easeInOut, now: t)
            lit.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            label.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            count.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            resetAt = t + Self.reset + 0.05
        } else if resetAt > 0 && t >= resetAt {
            resetAt = -1
            clock = 0
            loopStart = t
            user = false
        }
    }

    private func userPose(_ t: Double) -> FuseBurnButtonPose {
        FuseBurnButtonPose(
            b: b,
            w: w.value(at: t),
            spark: reduced ? 0 : spark,
            flare: flare.value(at: t),
            speed: speed,
            burstAge: burstStart < 0 ? -1 : t - burstStart,
            lit: lit.value(at: t),
            label: label.value(at: t),
            count: count.value(at: t),
            time: t.truncatingRemainder(dividingBy: 3600)
        )
    }

    /// Takes over from the loop without a jump: the finger's pose starts as the loop's frame.
    private func touched(_ now: Double) {
        lastTouch = now
        resetAt = -1
        if user { return }
        let p = M.loopPose(clock, length: length)
        b = p.b
        w.set(p.w)
        spark = p.spark
        flare.set(p.flare)
        speed = 0
        burstStart = p.burstAge >= 0 ? now - p.burstAge : -1
        lit.set(p.lit)
        label.set(p.label)
        count.set(p.count)
        fired = p.b >= 0.999
        user = true
    }

    private func burst(_ t: Double) {
        fired = true
        burstStart = t
        lit.timing(1, duration: 0.3, curve: .easeOut, now: t)
        label.timing(1, duration: 0.5, curve: .easeOut, now: t)
        count.timing(1, duration: 0.55, curve: .easeInOut, now: t)
        success.notificationOccurred(.success)
    }

    func touchDown(now: Double, reduced: Bool) {
        touched(now)
        if reduced {
            let on = label.value(at: now) > 0.5
            lit.timing(on ? 0 : 1, duration: 0.3, curve: .easeInOut, now: now)
            label.timing(on ? 0 : 1, duration: 0.3, curve: .easeInOut, now: now)
            count.timing(on ? 0 : 1, duration: 0.3, curve: .easeInOut, now: now)
            return
        }
        holding = true
        if !fired && spark < 0.2 {
            // A fresh light: the match flares.
            flare.pulse(peak: 1, up: 0.11, down: 0.32, downCurve: .easeOut, now: now)
            light.impactOccurred()
        }
    }

    func touchMove(_ p: CGPoint, now: Double, reduced: Bool) {
        if reduced || fired { return }
        lastTouch = now
        guard let f = cachedFuse else { return }
        let (u, d) = f.nearest(p.x, p.y)
        if d < Self.scrubReach && u > b && u - b < Self.scrubAhead {
            speed = (u - b) * f.length / 0.016
            spark = max(spark, 0.85)
            b = u
            if u >= 1 { burst(now) }
        }
    }

    func touchUp(now: Double) {
        holding = false
        lastTouch = now
    }
}
