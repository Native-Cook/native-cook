import SwiftUI
import UIKit

/*
 Tape Reel Physics — a cassette deck whose tape behaves like tape.

 Look: dark walnut (#17140F) with a warm glow behind a big cream cassette (the screen's width
 less 32 pt, 1.58 : 1): a label with a red-and-amber band, side A, "Night Drive Mix" in a marker
 hand (Gochi Hand) and C60; a black glass window with two brown tape packs on white six-toothed
 hubs; the tape running from each pack down round two steel guides and straight across the head
 opening, with its felt pad. Under it six transport keys (rewind, play, fast forward, pause,
 stop, record), two backlit VU meters, the tape's time and progress, and a hint.

 Mechanics, all closed form (TapeReelPhysicsModel.swift):
   Reels    the packs' radii follow the tape played s (r_supply² + r_takeup² is constant, 20 to
            50 pt); each hub's angle is ∫ feed ds / r, so a fuller pack turns slower, and both
            return exactly when the tape is rewound. Spinning past 10 rad/s the teeth smear into rings.
   Slack    the runs from the packs to the guides bow out 7 pt when the transport stops and pull
            straight when it moves: a damped spring (γ 5, ω 16 rad/s) driven by steps, so it sags
            with a wobble and tightens with a twang.
   Span     pulled, the tape between the guides is a triangle through the finger; let go, it
            rings as a string (its first four modes, 5 Hz fundamental, dying in about a second);
            winding fast makes it hum (18 and 27 Hz standing waves). Pulling turns both hubs a hair.

 autoPlay timeline (loop = 9 s), resting stopped at 12:41 with the slack bowed:
   0.6         PLAY: the tape tightens with a twang, hubs turn, meters wake.
   1.45 – 2.35 a finger comes down on the tape at 38 %, pulls it 22 pt (1.6 – 1.95, ease-out),
               holds, and lets go at 2.1; the span rings.
   3.2         FAST FORWARD: 320×, the packs trade size, the hubs blur, the span hums.
   4.6         STOP: the slack sags with a wobble.
   5.2 – 7.4   REWIND back to 12:41 (ease in-out).
   7.4         STOP; rest to 9.0, the same frame as 0.0.

 Touch: tap a key (a light click; play, fast forward, rewind and pause latch, stop and record
 flash); drag the tape between the guides to pull it, and let go to pluck it. 2 s after the last
 touch the tape winds back to 12:41 (1 s), stops, and the loop starts again 1 s later.
 Reduced motion: no loop, no ringing and no hum; the keys and the pull still work.
 */

struct TapeReelPhysics: View {
    var autoPlay = false

    static let loop = TapeReelPhysicsModel.loop
    typealias M = TapeReelPhysicsModel
    typealias A = TapeReelPhysicsArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// A timeline driven by touch, on the block's own clock (seconds since it appeared).
    struct Manual {
        var segments: [M.Segment]
        var sagBase: Double
        var steps: [(Double, Double)]
        var pluck: M.Pluck?
    }

    @State private var origin = Date.now
    @State private var autoStart: Date? = nil
    @State private var manual = Manual(segments: [M.Segment(start: 0, mode: .stop, s: M.home)], sagBase: M.slack, steps: [], pluck: nil)
    @State private var pulling = false
    @State private var lastNotch = 0
    @State private var flash: Date? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var click = UIImpactFeedbackGenerator(style: .rigid)
    @State private var soft = UIImpactFeedbackGenerator(style: .soft)

    static let bg = A.rgb(0x17140F)
    static let cream = A.rgb(0xF1E8D6)
    static let muted = A.rgb(0x9B907D)

    struct Layout {
        var w: Double, h: Double, top: Double
        var k: Double, x0: Double, y0: Double, ch: Double
        var keysY: Double, keyW: Double, metersY: Double, meterH: Double, meterW: Double, progressY: Double, hintY: Double
        init(size: CGSize, insets: EdgeInsets) {
            w = size.width; h = size.height; top = insets.top
            let cw = w - 32
            k = cw / A.size.width
            ch = A.size.height * k
            keyW = (w - 32 - 40) / 6
            meterW = (w - 32 - 14) / 2
            meterH = 160
            let bottom = max(insets.bottom, 16)
            hintY = h - bottom - 18
            // The header takes the top 76 pt; the rest is shared by four equal gaps.
            let gap = max(20, (hintY - 28 - (top + 76) - (ch + 56 + meterH + 24)) / 4)
            x0 = 16
            y0 = top + 76 + gap
            keysY = y0 + ch + gap
            metersY = keysY + 56 + gap
            progressY = metersY + meterH + gap
        }
        /// A point of the cassette's design space on the screen.
        func screen(_ p: CGPoint) -> CGPoint { CGPoint(x: x0 + p.x * k, y: y0 + p.y * k) }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let l = Layout(size: size, insets: insets)
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(paused: reduceMotion && !pulling && !manualMoving)) { timeline in
                    let (f, t) = frame(at: timeline.date)
                    Canvas { ctx, _ in draw(&ctx, l, f, t) }
                        .frame(width: size.width, height: size.height)
                }
                .accessibilityHidden(true)
                controls(l)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            _ = A.registered
            click.prepare()
            origin = .now
            if autoPlay && !reduceMotion { autoStart = origin }
        }
        .onDisappear { resume?.cancel() }
    }

    private var manualMoving: Bool {
        guard let last = manual.segments.last else { return false }
        return M.moving(last.mode) || flash != nil
    }

    // MARK: Time

    private func now() -> Double { Date.now.timeIntervalSince(origin) }

    /// The frame at a date, and the time its pluck and hum are measured on.
    private func frame(at date: Date) -> (M.Frame, Double) {
        if let autoStart, date >= autoStart, !reduceMotion {
            let t = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            return (M.auto(t), t)
        }
        let t = date.timeIntervalSince(origin)
        return (M.frame(at: t, segments: manual.segments, sagBase: manual.sagBase, steps: manual.steps, pluck: manual.pluck), t)
    }

    /// Takes the tape over from the loop (if it is playing) into the touch timeline.
    private func takeOver() -> Double {
        resume?.cancel()
        let t = now()
        if let start = autoStart, Date.now >= start {
            let lt = Date.now.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop)
            let f = M.auto(lt)
            let mode: M.Mode = f.mode == .rew ? .rew : f.mode
            var pluck: M.Pluck? = nil
            if let p = f.pluck {
                pluck = M.Pluck(x: p.x, d: p.d, releasedAt: p.releasedAt.map { t - (lt - $0) } ?? t)
            }
            let target = M.moving(mode) ? 0 : M.slack
            manual = Manual(segments: [M.Segment(start: t, mode: mode, s: f.s)], sagBase: f.sag,
                            steps: [(t, target - f.sag)], pluck: pluck)
        }
        autoStart = nil
        // Fold settled steps into the base.
        manual.sagBase += manual.steps.filter { t - $0.0 > 3 }.reduce(0) { $0 + $1.1 }
        manual.steps.removeAll { t - $0.0 > 3 }
        if manual.segments.count > 6 { manual.segments.removeFirst(manual.segments.count - 6) }
        return t
    }

    private func press(_ glyph: A.Glyph) {
        let t = takeOver()
        click.impactOccurred(intensity: glyph == .ff || glyph == .rew ? 1 : 0.7)
        let f = M.frame(at: t, segments: manual.segments, sagBase: manual.sagBase, steps: manual.steps, pluck: manual.pluck)
        let current = manual.segments.last?.mode ?? .stop
        var mode: M.Mode
        switch glyph {
        case .rew: mode = .rew
        case .play: mode = .play
        case .ff: mode = .ff
        case .pause: mode = current == .pause ? .play : .pause
        case .stop: mode = .stop
        case .rec:
            flash = .now
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(0.25))
                flash = nil
            }
            scheduleResume()
            return
        }
        if mode != current {
            manual.segments.append(M.Segment(start: t, mode: mode, s: f.s))
            let old = M.moving(current) ? 0 : M.slack, new = M.moving(mode) ? 0 : M.slack
            if old != new { manual.steps.append((t, new - old)) }
        }
        scheduleResume()
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let t = now()
            let f = M.frame(at: t, segments: manual.segments, sagBase: manual.sagBase, steps: manual.steps, pluck: manual.pluck)
            let current = manual.segments.last?.mode ?? .stop
            manual.segments.append(M.Segment(start: t, mode: .seek, s: f.s, to: M.home, duration: 1))
            manual.segments.append(M.Segment(start: t + 1, mode: .stop, s: M.home))
            if !M.moving(current) { manual.steps.append((t, -M.slack)) }
            manual.steps.append((t + 1, M.slack))
            autoStart = origin.addingTimeInterval(t + 2)
        }
    }

    // MARK: Touch

    private func controls(_ l: Layout) -> some View {
        let glyphs: [A.Glyph] = [.rew, .play, .ff, .pause, .stop, .rec]
        let names = ["Rewind", "Play", "Fast forward", "Pause", "Stop", "Record"]
        let span0 = l.screen(CGPoint(x: A.spanX0, y: A.spanY)), span1 = l.screen(CGPoint(x: A.spanX1, y: A.spanY))
        return ZStack(alignment: .topLeading) {
            ForEach(0..<6, id: \.self) { i in
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: l.keyW, height: 60)
                    .onTapGesture { press(glyphs[i]) }
                    .offset(x: l.x0 + Double(i) * (l.keyW + 8), y: l.keysY)
                    .accessibilityElement()
                    .accessibilityLabel(names[i])
                    .accessibilityAddTraits(.isButton)
            }
            Color.clear
                .contentShape(Rectangle())
                .frame(width: span1.x - span0.x + 20, height: 100)
                .offset(x: span0.x - 10, y: span0.y - 40)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { g in
                            if !pulling {
                                _ = takeOver()
                                pulling = true
                                lastNotch = 0
                            }
                            let x = min(max((g.location.x - 10) / (span1.x - span0.x), 0.08), 0.92)
                            let d = min(max((g.location.y - 40) / l.k, -36), 48)
                            manual.pluck = M.Pluck(x: x, d: d, releasedAt: nil)
                            let notch = Int(abs(d) / 8)
                            if notch != lastNotch {
                                lastNotch = notch
                                soft.impactOccurred(intensity: 0.5)
                            }
                        }
                        .onEnded { _ in
                            pulling = false
                            if var p = manual.pluck {
                                p.releasedAt = now()
                                manual.pluck = p
                                click.impactOccurred(intensity: min(1, abs(p.d) / 30 + 0.3))
                            }
                            scheduleResume()
                        }
                )
                .accessibilityLabel("Tape")
        }
    }

    // MARK: Drawing

    private func draw(_ ctx: inout GraphicsContext, _ l: Layout, _ f: M.Frame, _ t: Double) {
        let full = Path(CGRect(x: 0, y: 0, width: l.w, height: l.h))
        ctx.fill(full, with: .color(Self.bg))
        ctx.fill(full, with: .radialGradient(Gradient(colors: [A.rgb(0x3A2C1C, 0.6), A.rgb(0x3A2C1C, 0)]),
                                             center: CGPoint(x: l.w / 2, y: l.y0 + l.ch / 2), startRadius: 0, endRadius: l.w * 0.9))
        ctx.fill(full, with: .radialGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.5)]),
                                             center: CGPoint(x: l.w / 2, y: l.h * 0.45), startRadius: l.h * 0.3, endRadius: l.h * 0.75))

        // Header.
        ctx.draw(Text("SIDE A · C60").font(.system(size: 11, weight: .semibold)).tracking(1.4).foregroundColor(Self.muted),
                 at: CGPoint(x: 20, y: l.top + 22), anchor: .leading)
        ctx.draw(Text("Night Drive Mix").font(.system(size: 24, weight: .semibold)).foregroundColor(Self.cream),
                 at: CGPoint(x: 20, y: l.top + 46), anchor: .leading)
        counter(&ctx, CGRect(x: l.w - 16 - 84, y: l.top + 22, width: 84, height: 34), value: f.s / 4)

        // Cassette, on its shadow.
        ctx.drawLayer { g in
            g.addFilter(.blur(radius: 14))
            g.fill(Path(ellipseIn: CGRect(x: l.x0 + 0.06 * (l.w - 32), y: l.y0 + l.ch - 8, width: 0.88 * (l.w - 32), height: 26)),
                   with: .color(.black.opacity(0.6)))
        }
        var c = ctx
        c.translateBy(x: l.x0, y: l.y0)
        c.scaleBy(x: l.k, y: l.k)
        A.cassette(&c, frame: f, t: t, still: reduceMotion, labelFont: .custom("GochiHand-Regular", size: 24))

        // The scripted finger.
        if let fg = f.finger, fg.alpha > 0.01 {
            let p = l.screen(CGPoint(x: A.spanX0 + fg.x * (A.spanX1 - A.spanX0), y: A.spanY + fg.d))
            let r = 20.0
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r)), with: .color(.white.opacity(0.2 * fg.alpha)))
            ctx.stroke(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r)), with: .color(.white.opacity(0.55 * fg.alpha)), lineWidth: 1.5)
        }

        // Keys.
        let glyphs: [A.Glyph] = [.rew, .play, .ff, .pause, .stop, .rec]
        for (i, g) in glyphs.enumerated() {
            let pressed = f.key == i || (g == .rec && flash != nil)
            A.key(&ctx, g, rect: CGRect(x: l.x0 + Double(i) * (l.keyW + 8), y: l.keysY, width: l.keyW, height: 56), pressed: pressed)
        }

        // Meters.
        for ch in 0..<2 {
            let level = f.meters * min(1, max(0, M.level(t, channel: ch)))
            A.meter(&ctx, rect: CGRect(x: l.x0 + Double(ch) * (l.meterW + 14), y: l.metersY, width: l.meterW, height: l.meterH),
                    level: level, glow: f.meters, channel: ch == 0 ? "L" : "R")
        }

        // Progress.
        let mono = Font.system(size: 12, weight: .medium, design: .monospaced)
        ctx.draw(Text(M.clock(f.s)).font(mono).foregroundColor(Self.cream), at: CGPoint(x: l.x0, y: l.progressY), anchor: .leading)
        ctx.draw(Text(M.clock(M.total)).font(mono).foregroundColor(Self.muted), at: CGPoint(x: l.w - 16, y: l.progressY), anchor: .trailing)
        let bar = CGRect(x: l.x0, y: l.progressY + 16, width: l.w - 32, height: 4)
        ctx.fill(Path(roundedRect: bar, cornerRadius: 2), with: .color(A.rgb(0x3A342B)))
        ctx.fill(Path(roundedRect: CGRect(x: bar.minX, y: bar.minY, width: bar.width * f.s / M.total, height: 4), cornerRadius: 2),
                 with: .color(A.amber))
        ctx.draw(Text("Pull the tape, or press a key").font(.system(size: 12)).foregroundColor(A.rgb(0x7A705F)),
                 at: CGPoint(x: l.w / 2, y: l.hintY))
    }

    /// A four-digit odometer: the last digit rolls with the tape.
    private func counter(_ ctx: inout GraphicsContext, _ r: CGRect, value: Double) {
        let box = Path(roundedRect: r, cornerRadius: 8, style: .continuous)
        ctx.fill(box, with: .color(A.rgb(0x0E0C09)))
        ctx.stroke(box, with: .color(A.rgb(0x3A3225)), lineWidth: 1)
        let n = Int(floor(value)), frac = value - floor(value)
        let digits = String(format: "%04d", n % 10000).map { String($0) }
        let cell = (r.width - 12) / 4
        let font = Font.system(size: 17, weight: .semibold, design: .monospaced)
        ctx.drawLayer { g in
            g.clip(to: box)
            for i in 0..<4 {
                let x = r.minX + 6 + cell * (Double(i) + 0.5)
                if i == 3 {
                    let roll = 22 * M.smoothstep(0.7, 1, frac)
                    // The rolling digit fades as it leaves the window (a clip does not hold resolved text).
                    g.draw(Text(digits[i]).font(font).foregroundColor(Self.cream.opacity(1 - roll / 22)), at: CGPoint(x: x, y: r.midY - roll))
                    g.draw(Text("\((n + 1) % 10)").font(font).foregroundColor(Self.cream.opacity(roll / 22)), at: CGPoint(x: x, y: r.midY + 22 - roll))
                } else {
                    g.draw(Text(digits[i]).font(font).foregroundColor(Self.cream), at: CGPoint(x: x, y: r.midY))
                }
                if i > 0 {
                    var sep = Path()
                    sep.move(to: CGPoint(x: r.minX + 6 + cell * Double(i), y: r.minY + 6))
                    sep.addLine(to: CGPoint(x: r.minX + 6 + cell * Double(i), y: r.maxY - 6))
                    g.stroke(sep, with: .color(.white.opacity(0.08)), lineWidth: 1)
                }
            }
            g.fill(Path(CGRect(x: r.minX, y: r.minY, width: r.width, height: r.height / 2)), with: .color(.white.opacity(0.04)))
        }
    }
}
