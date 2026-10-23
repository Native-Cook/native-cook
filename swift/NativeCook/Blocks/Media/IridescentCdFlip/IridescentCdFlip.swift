import SwiftUI
import UIKit

/*
 Iridescent CD Flip — a compact disc in a sci-fi HUD that turns over to show its rainbow data side.

 Look: black with fixed film grain. A white chamfered frame (corners cut at the top right and
 bottom left) holds the disc, 0.78 × the frame across: the printed side is glossy black with
 warm light bands across its lower third, a fan of cold streaks at the right, a gold mono
 "MIRROR INDEX" and red and green hub circles; the data side is mirror grey with two hue cycles
 of diffraction colour that sweep round as it turns. Both have a clear hub, a hole and a rim, and
 a soft band of light that slides across with the turn. Mono captions sit above and below the
 frame and retype themselves (a glitch) at every flip. Under the frame: the jewel case with the
 same art, the track list, a level meter and a "FLIP DISC" pill.

 The disc turns about its vertical axis, seen from 1000 pt away (Core Animation m34 = −1/1000);
 near edge-on a chrome edge shows.

 autoPlay timeline (loop = 8 s, IridescentCdFlipMotion.swift):
   0.0 – 1.2    printed side, swaying ±4° (one sine a loop) so the light slides.
   1.2          flip: 180° on a spring (ω 6.5, ζ 0.6: about 9 % overshoot and a wobble), captions retype.
   1.2 – 4.2    the data side, its rainbow sweeping with the sway.
   4.2          flip on round to 360°: the printed side again, captions retype.
   8.0          360° is 0°: the same frame as 0.0.

 Touch: drag sideways on the disc to turn it with the finger (half a turn per disc width); on
 release it springs to the nearest side, thrown by its speed (a medium haptic when it lands on
 the other side). Tap the disc or the pill to flip it. 2 s after the last touch it turns back to
 the printed side and the loop starts again.
 Reduced motion: no loop and no sway; a flip is a 0.3 s cross-fade between the sides, the disc
 never turns; dragging does nothing.
 */

struct IridescentCdFlip: View {
    var autoPlay = false

    static let loop = 8.0
    typealias M = IridescentCdFlipMotion
    typealias Art = IridescentCdFlipArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale

    struct Pose {
        var angle = 0.0
        /// Reduced motion: how much of the data side shows (a cross-fade), else nil.
        var back: Double? = nil
        var printed = true
        var since = 10.0
        var clock = 0.0
    }

    @State private var autoStart: Date? = nil
    /// Whole turns added to the loop's angle after a hand-off.
    @State private var angleOffset = 0.0
    @State private var turn: (date: Date, from: Double, velocity: Double, to: Double) = (.distantPast, 0, 0, 0)
    @State private var dragAngle: Double? = nil
    @State private var dragFrom = 0.0
    @State private var touching = false
    @State private var moved = false
    @State private var resume: Task<Void, Never>? = nil
    @State private var busy = false
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var medium = UIImpactFeedbackGenerator(style: .medium)
    /// The printed side's streaks, drawn once (they are blurred, which is the costly part of the
    /// face) and blitted into the face every frame.
    @State private var streakArt: CGImage? = nil

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let w = proxy.size.width + insets.leading + insets.trailing
            let h = proxy.size.height + insets.top + insets.bottom
            let s = w - 48
            let r = 0.39 * s
            let caseTop = insets.top + 40 + s + 44
            // What never changes is drawn once, outside the timeline: the grain, the jewel case.
            // Only the disc, the glow, the captions and the meters are redrawn every frame.
            ZStack(alignment: .topLeading) {
                Canvas { ctx, size in Art.grain(ctx, size: size) }
                    .frame(width: w, height: h)
                    .allowsHitTesting(false)
                caseArt
                    .frame(width: 150, height: 134)
                    .offset(x: 24, y: caseTop)
                    .allowsHitTesting(false)
                TimelineView(.animation(paused: reduceMotion && !busy)) { timeline in
                    content(pose(at: timeline.date), w: w, h: h, insets: insets)
                }
            }
            .frame(width: w, height: h, alignment: .topLeading)
            .ignoresSafeArea()
            .onAppear {
                let renderer = ImageRenderer(content: Canvas { ctx, size in
                    Art.streaks(ctx, CGRect(origin: .zero, size: size))
                }.frame(width: 2 * r, height: 2 * r))
                renderer.scale = displayScale
                streakArt = renderer.cgImage
            }
        }
        .background(Art.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            light.prepare(); medium.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    /// The jewel case: the same art as the disc, a spine at the left.
    private var caseArt: some View {
        Canvas { ctx, size in
            let rect = CGRect(origin: .zero, size: size)
            let art = CGRect(x: 14, y: 0, width: size.width - 14, height: size.height)
            ctx.fill(Path(rect), with: .color(Color(hex: 0x0B0B0E)))
            var a = ctx
            a.clip(to: Path(art))
            Art.streaks(a, CGRect(x: art.minX, y: art.minY - art.width * 0.08, width: art.width, height: art.width))
            a.draw(Text("MIRROR INDEX · HALATION").font(.system(size: 6, weight: .medium, design: .monospaced)).tracking(1)
                    .foregroundColor(.white.opacity(0.6)), at: CGPoint(x: art.minX + 8, y: art.minY + art.height * 0.6), anchor: .leading)
            ctx.stroke(Path(rect.insetBy(dx: 0.5, dy: 0.5)), with: .color(.white.opacity(0.85)), lineWidth: 1)
            ctx.fill(Path(CGRect(x: 10, y: 0, width: 1, height: size.height)), with: .color(.white.opacity(0.85)))
            ctx.fill(Path(CGRect(x: 13, y: 0, width: 1, height: size.height)), with: .color(.white.opacity(0.5)))
        }
    }

    // MARK: Time

    private func pose(at date: Date) -> Pose {
        if let autoStart, date >= autoStart {
            let t = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            let cap = M.loopCaption(t)
            return Pose(angle: M.loopAngle(t) + angleOffset, back: nil, printed: cap.printed, since: cap.since, clock: t)
        }
        let e = max(0, date.timeIntervalSince(turn.date))
        let clock = date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: Self.loop * 100)
        if reduceMotion {
            let showBack = !M.printedSide(turn.to)
            let f = M.clamp01(e / 0.3)
            return Pose(angle: 0, back: showBack ? f : 1 - f, printed: !showBack, since: e, clock: 0)
        }
        let angle = dragAngle ?? M.spring(from: turn.from, velocity: turn.velocity, to: turn.to, e).0
        return Pose(angle: angle, back: nil, printed: M.printedSide(turn.to), since: dragAngle == nil ? e : 10, clock: clock)
    }

    private func velocity(at date: Date) -> Double {
        M.spring(from: turn.from, velocity: turn.velocity, to: turn.to, max(0, date.timeIntervalSince(turn.date))).1
    }

    /// Freezes whatever plays into the hand-driven state.
    private func takeOver(_ now: Date) {
        resume?.cancel()
        guard let start = autoStart else { return }
        autoStart = nil
        guard now >= start else { return }
        let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop)
        let a = M.loopAngle(t) + angleOffset
        let target = (a / 180).rounded() * 180
        turn = (now, a, 0, target)
    }

    private func flip() {
        let now = Date.now
        takeOver(now)
        let p = pose(at: now)
        turn = (now, p.angle, reduceMotion ? 0 : velocity(at: now), turn.to + 180)
        medium.impactOccurred()
        if reduceMotion { markBusy() }
        scheduleResume()
    }

    private func markBusy() {
        busy = true
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(0.4))
            if !touching { busy = false }
        }
    }

    private func drag(r: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if !touching {
                    touching = true
                    moved = false
                    takeOver(now)
                    dragFrom = pose(at: now).angle
                    return
                }
                guard !reduceMotion else { return }
                if !moved && abs(value.translation.width) > 6 {
                    moved = true
                    light.impactOccurred(intensity: 0.5)
                }
                if moved { dragAngle = dragFrom + value.translation.width / (2 * r) * 180 }
            }
            .onEnded { value in
                let now = Date.now
                touching = false
                if moved, let a = dragAngle {
                    let v = value.velocity.width / (2 * r) * 180
                    let target = ((a + v * 0.15) / 180).rounded() * 180
                    if M.printedSide(target) != M.printedSide(dragFrom) { medium.impactOccurred() }
                    turn = (now, a, v, target)
                    dragAngle = nil
                    scheduleResume()
                } else {
                    flip()
                }
            }
    }

    /// 2 s after the last touch: back to the printed side, then the loop.
    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled, !touching else { return }
            let now = Date.now
            var target = turn.to
            if !M.printedSide(target) { target += 180 }
            let p = pose(at: now)
            if target != turn.to { turn = (now, p.angle, velocity(at: now), target) }
            // Start once the spring has settled (within a hundredth of a degree after 2.5 s).
            let ready = max(now, turn.date.addingTimeInterval(2.5))
            autoStart = ready
            angleOffset = target
        }
    }

    // MARK: Drawing

    @ViewBuilder
    private func content(_ p: Pose, w: Double, h: Double, insets: EdgeInsets) -> some View {
        let s = w - 48
        let y0 = insets.top + 40
        let r = 0.39 * s
        let c = CGPoint(x: w / 2, y: y0 + s / 2)
        let angle = p.angle
        let cosA = cos(angle * .pi / 180)
        let backAmount = p.back ?? (cosA < 0 ? 1 : 0)
        let caseTop = y0 + s + 44
        let headline = p.printed ? "COMPACT DISC (CD) — PRINTED SIDE" : "DATA SIDE — READ BY LASER 780 NM"
        let footline = p.printed ? "MIRROR INDEX — HALATION" : "BE READ WITH A BEAM OF LIGHT"

        ZStack(alignment: .topLeading) {
            // The HUD and the light the disc throws.
            Canvas { ctx, _ in
                let glowColor = backAmount > 0.5 ? Color(hex: 0x8A7CFF) : Color(hex: 0xFF6A2B)
                let strength = p.back.map { 0.12 + 0.1 * $0 } ?? (cosA < 0 ? 0.22 * -cosA : 0.12 * cosA)
                ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .radialGradient(
                    Gradient(colors: [glowColor.opacity(strength), glowColor.opacity(0)]), center: c, startRadius: r * 0.4, endRadius: r * 1.7))
                ctx.stroke(Art.frame(CGRect(x: 24, y: y0, width: s, height: s), cut: 0.18 * s), with: .color(.white.opacity(0.85)), lineWidth: 1)
                let mono = { (text: String) in
                    Text(text).font(.system(size: 10, weight: .medium, design: .monospaced)).tracking(1.5).foregroundColor(.white.opacity(0.85))
                }
                ctx.draw(mono(M.glitch(headline, since: p.since, salt: p.printed ? 1 : 2)), at: CGPoint(x: 24, y: y0 - 8), anchor: .bottomLeading)
                ctx.draw(mono(M.glitch(footline, since: p.since - 0.1, salt: p.printed ? 3 : 4)), at: CGPoint(x: 24 + s, y: y0 + s + 8), anchor: .topTrailing)
                // Edge-on, the disc's chrome edge.
                let edge = abs(sin(angle * .pi / 180))
                if p.back == nil && abs(cosA) < 0.4 {
                    let ew = 1.5 + 3 * edge
                    ctx.fill(Path(roundedRect: CGRect(x: c.x - ew / 2, y: c.y - r, width: ew, height: 2 * r), cornerRadius: ew / 2),
                             with: .linearGradient(Gradient(colors: [Color(hex: 0xFF9AD5), .white, Color(hex: 0x9B9EA6), Color(hex: 0x5BC8FF), .white]),
                                                   startPoint: CGPoint(x: c.x, y: c.y - r), endPoint: CGPoint(x: c.x, y: c.y + r)))
                }
            }
            .frame(width: w, height: h)
            .allowsHitTesting(false)

            // The disc.
            ZStack {
                if let b = p.back {
                    Canvas { ctx, _ in Art.face(ctx, r: r, angle: 0, back: false, streaks: streakArt, scale: displayScale) }.opacity(1 - b)
                    Canvas { ctx, _ in Art.face(ctx, r: r, angle: 0, back: true, streaks: streakArt, scale: displayScale) }.opacity(b)
                } else {
                    Canvas { ctx, _ in Art.face(ctx, r: r, angle: angle, back: cosA < 0, streaks: streakArt, scale: displayScale) }
                        .projectionEffect(Self.turned(angle, side: 2 * r))
                }
            }
            .frame(width: 2 * r, height: 2 * r)
            .position(c)
            .accessibilityElement()
            .accessibilityLabel(p.printed ? "Compact disc, printed side" : "Compact disc, data side")
            .accessibilityAddTraits(.isButton)

            // Touches on the disc's square.
            Color.clear
                .frame(width: s, height: s)
                .contentShape(Rectangle())
                .gesture(drag(r: r))
                .offset(x: 24, y: y0)

            caseAndTracks(p, w: w, top: caseTop)
            scanner(p, w: w, top: caseTop + 134 + 40)
            flipButton(w: w, h: h, insets: insets)
        }
        .frame(width: w, height: h, alignment: .topLeading)
    }

    /// A turn about the disc's vertical centre line, seen from `M.depth` points away.
    static func turned(_ degrees: Double, side s: Double) -> ProjectionTransform {
        var t = CATransform3DMakeTranslation(-s / 2, -s / 2, 0)
        t = CATransform3DConcat(t, CATransform3DMakeRotation(degrees * .pi / 180, 0, 1, 0))
        var p = CATransform3DIdentity
        p.m34 = -1 / M.depth
        t = CATransform3DConcat(t, p)
        t = CATransform3DConcat(t, CATransform3DMakeTranslation(s / 2, s / 2, 0))
        return ProjectionTransform(t)
    }

    private func caseAndTracks(_ p: Pose, w: Double, top: Double) -> some View {
        let tracks = [("01", "Halation", "3:41"), ("02", "Glass Sea", "4:02"), ("03", "Afterimage", "2:58"), ("04", "Beam of Light", "5:10")]
        let mono = Font.system(size: 11, weight: .medium, design: .monospaced)
        return HStack(alignment: .top, spacing: 20) {
            // The jewel case is drawn once, in `body`; this keeps its place in the row.
            Color.clear
                .frame(width: 150, height: 134)

            VStack(alignment: .leading, spacing: 0) {
                ForEach(0..<4, id: \.self) { i in
                    HStack(spacing: 8) {
                        Text(tracks[i].0).foregroundStyle(.white.opacity(0.45))
                        Text(tracks[i].1).foregroundStyle(.white.opacity(i == 0 ? 1 : 0.6)).lineLimit(1)
                        Spacer(minLength: 4)
                        Text(tracks[i].2).foregroundStyle(.white.opacity(0.45))
                    }
                    .font(mono)
                    .frame(height: 26)
                    .overlay(alignment: .bottom) { Rectangle().fill(.white.opacity(0.12)).frame(height: 1) }
                }
                // A level meter: each bar moves a whole number of times a loop.
                HStack(alignment: .center, spacing: 3) {
                    Text("NOW PLAYING · 01")
                        .font(.system(size: 9, weight: .medium, design: .monospaced)).tracking(1)
                        .foregroundStyle(.white.opacity(0.55))
                    Spacer(minLength: 6)
                    ForEach(0..<8, id: \.self) { b in
                        let cycles = Double([3, 5, 4, 7, 6, 3, 5, 4][b])
                        let level = reduceMotion ? 0.4 : 0.5 + 0.5 * sin(2 * .pi * cycles * p.clock / Self.loop + Double(b) * 1.7)
                        Capsule().fill(.white.opacity(0.8)).frame(width: 2, height: 3 + 11 * level)
                    }
                }
                .frame(height: 30)
            }
            .frame(maxWidth: .infinity)
        }
        .frame(width: w - 48)
        .offset(x: 24, y: top)
        .allowsHitTesting(false)
    }

    /// A HUD read-out: the laser's position sweeping over the disc's radius, once there and back a loop.
    private func scanner(_ p: Pose, w: Double, top: Double) -> some View {
        let u = reduceMotion ? 0.3 : 0.5 - 0.42 * cos(2 * .pi * p.clock / Self.loop)
        return Canvas { ctx, size in
            let line = size.width
            for k in 0...40 {
                let x = line * Double(k) / 40
                let tall = k % 5 == 0 ? 8.0 : 4.0
                ctx.fill(Path(CGRect(x: x - 0.5, y: 18 - tall, width: 1, height: tall)), with: .color(.white.opacity(k % 5 == 0 ? 0.6 : 0.3)))
            }
            ctx.fill(Path(CGRect(x: 0, y: 18, width: line, height: 1)), with: .color(.white.opacity(0.35)))
            let x = line * u
            ctx.fill(Path(CGRect(x: x - 1, y: 4, width: 2, height: 20)), with: .color(Color(hex: 0xFF4B3A)))
            ctx.fill(Path(ellipseIn: CGRect(x: x - 3, y: 15.5, width: 6, height: 6)), with: .color(Color(hex: 0xFF4B3A)))
            let mono = { (t: String) in Text(t).font(.system(size: 9, weight: .medium, design: .monospaced)).tracking(1).foregroundColor(.white.opacity(0.55)) }
            ctx.draw(mono("LASER 780 NM"), at: CGPoint(x: 0, y: 32), anchor: .topLeading)
            ctx.draw(mono("HUB  —  RIM"), at: CGPoint(x: line, y: 32), anchor: .topTrailing)
        }
        .frame(width: w - 48, height: 46)
        .offset(x: 24, y: top)
        .allowsHitTesting(false)
    }

    private func flipButton(w: Double, h: Double, insets: EdgeInsets) -> some View {
        Button(action: flip) {
            HStack(spacing: 10) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 13, weight: .semibold))
                Text("FLIP DISC")
                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    .tracking(2)
            }
            .foregroundStyle(.white)
            .frame(width: 168, height: 44)
            .overlay(Capsule().strokeBorder(.white.opacity(0.85), lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(IridescentCdFlipPress())
        .offset(x: (w - 168) / 2, y: h - max(insets.bottom, 16) - 60)
    }
}

private struct IridescentCdFlipPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(Capsule().fill(.white.opacity(configuration.isPressed ? 0.14 : 0)))
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
