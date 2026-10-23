import CoreText
import SwiftUI
import UIKit

/*
 Bubble Ring Recorder — a voice recorder whose outline wobbles like a soap bubble with the voice.

 Look: a charcoal screen (#141416, a soft lighter pool behind the orb). Top left a big thin timer
 (Barlow Condensed ExtraLight 104 pt, the minutes in white 42 %, ":ss" in white) with a caption
 under it; a three-line menu top right. In the middle a glowing red record orb (radius 20 % of the
 width, #FF5C7C → #FF1F4B → #D4093A, "RECORD" set vertically in it) inside a faint 1 pt circle
 (radius 29 % of the width). Under it three small glyphs (note, waveform, list), then a list of
 three takes: "Untitled take" (the last take, with its waveform), "Chorus hum", "Kitchen demo".

 Recording: the orb collapses into a 9 pt red dot that pulses with the level; the faint circle
 becomes a 2.25 pt white line whose radius is pushed in and out by four low harmonics scaled by
 the level (up to ±14 %), with a thinner grey ghost of the shape 0.22 s behind and a soft glow.
 The timer counts, the first row turns into a live "Recording" row whose waveform fills left to
 right. Stopping: the orb springs back out of the dot (stiffness 170, damping 16, ~8 % overshoot),
 the ring gets a jelly kick (radius −7 %, wobble 8 %, e^(−4.5u)·sin(11u)) and fades back to the
 faint circle over 0.6 s; the row shows the new take.

 autoPlay timeline (loop = 9 s):
   0.0 – 1.2   rest; the orb's glow breathes (period 2.25 s, four per loop).
   1.2         tap: a ripple leaves the orb (0.7 s); the orb collapses into the dot (0.5 s,
               ease-in-out) while the ring turns white (0.45 s).
   1.2 – 6.4   a scripted 5.2 s take: nine syllables of a voice, a quiet floor between them.
   6.4         tap on the dot: a red ripple (0.6 s), the orb springs back, the ring jiggles out.
   7.4 – 9.0   rest; at 9.0 the frame is the one at 0.0.

 Touch: tap the orb (or the dot) to start or stop a take. Hold anywhere while recording and the
 ring bulges towards the finger and the voice gets louder. 2 s after the last touch the loop
 starts again from rest (a take left running stops itself 8 s after the last touch).
 Reduced motion: no breathing, no wobble; start and stop cross-fade the orb and the dot over
 0.25 s and the ring is a plain circle while recording.
 */

struct BubbleRingRecorder: View {
    var autoPlay = false

    static let loop = BubbleRingRecorderMotion.loop
    typealias M = BubbleRingRecorderMotion

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var appear = Date.now
    /// The loop's t = 0 while autoPlay runs, and when its first stop happens.
    @State private var autoStart: Date? = nil
    @State private var firstAutoStop = Date.distantFuture
    /// A take made by hand.
    @State private var recStart: Date? = nil
    @State private var recStop: Date? = nil
    @State private var lastTake = BubbleRingRecorderMotion.take
    /// A finger held on the screen.
    @State private var press: (point: CGPoint, at: Date)? = nil
    @State private var released: (point: CGPoint, at: Date, amount: Double)? = nil
    @State private var reducedLive = false
    @State private var resume: Task<Void, Never>? = nil
    @State private var lightHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var mediumHaptic = UIImpactFeedbackGenerator(style: .medium)

    struct Layout {
        var size: CGSize
        var insets: EdgeInsets
        var center: CGPoint
        var orb: Double
        var ring: Double
        var iconsY: Double
        var rowsTop: Double

        init(size: CGSize, insets: EdgeInsets) {
            self.size = size
            self.insets = insets
            let w = size.width
            orb = 0.2 * w
            ring = 0.29 * w
            let timerBottom = insets.top + 4 + 146
            let listBottom = size.height - max(insets.bottom, 16) - 4
            rowsTop = listBottom - 3 * BubbleRingRecorder.rowHeight
            iconsY = rowsTop - 66
            center = CGPoint(x: w / 2, y: (timerBottom + iconsY - 12) / 2)
        }
    }

    static let rowHeight = 68.0
    static let red = Color(red: 1.0, green: 0.122, blue: 0.294)
    static let redText = Color(red: 1.0, green: 0.302, blue: 0.427)
    static let background = Color(red: 0.078, green: 0.078, blue: 0.086)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = Layout(size: size, insets: insets)
            TimelineView(.animation(paused: reduceMotion && !reducedLive && press == nil)) { timeline in
                let now = timeline.date
                let k = clock(at: now)
                let f = frame(k, now: now, layout: layout)
                ZStack(alignment: .topLeading) {
                    stage(f, layout: layout)
                    timer(f, layout: layout)
                    menu(layout: layout)
                    list(k, f, layout: layout)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(touch(layout: layout))
            }
            .ignoresSafeArea()
        }
        .background(Self.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            _ = BubbleRingRecorderFont.registered
            lightHaptic.prepare()
            mediumHaptic.prepare()
            appear = .now
            if autoPlay && !reduceMotion {
                autoStart = appear
                firstAutoStop = appear.addingTimeInterval(M.stopAt)
            }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Time

    private func clock(at now: Date) -> M.Clock {
        let c = now.timeIntervalSince(appear)
        if let autoStart, now >= autoStart {
            let t = now.timeIntervalSince(autoStart)
            let e = t - floor(t / M.loop) * M.loop
            return M.autoClock(e, clock: c, lastTake: lastTake, afterFirstStop: now >= firstAutoStop)
        }
        guard let recStart else { return M.Clock(sinceStart: nil, sinceStop: nil, lastTake: lastTake, clock: c) }
        let s = now.timeIntervalSince(recStart)
        if let recStop {
            let u = now.timeIntervalSince(recStop)
            if u > 3 { return M.Clock(sinceStart: nil, sinceStop: nil, lastTake: lastTake, clock: c) }
            return M.Clock(sinceStart: s, sinceStop: u, lastTake: lastTake, clock: c)
        }
        return M.Clock(sinceStart: s, sinceStop: nil, lastTake: lastTake, clock: c)
    }

    /// The finger's pull on the ring: 0…1 and where it is.
    private func pull(at now: Date) -> (amount: Double, point: CGPoint)? {
        if let press { return (M.easeOut(now.timeIntervalSince(press.at) / 0.2), press.point) }
        if let released {
            let a = released.amount * (1 - M.easeOut(now.timeIntervalSince(released.at) / 0.35))
            return a > 0.001 ? (a, released.point) : nil
        }
        return nil
    }

    private struct Shown {
        var f: M.Frame
        var bulge: Double
        var bulgeAngle: Double
    }

    private func frame(_ k: M.Clock, now: Date, layout: Layout) -> Shown {
        var f = M.frame(k, R: layout.orb, reduced: reduceMotion)
        var bulge = 0.0, angle = 0.0
        if !reduceMotion, let p = pull(at: now), f.presence > 0 {
            let dx = p.point.x - layout.center.x, dy = p.point.y - layout.center.y
            let reach = M.smoothstep(0.3 * layout.ring, 0.9 * layout.ring, hypot(dx, dy))
            bulge = 0.16 * p.amount * f.presence * reach
            angle = atan2(dy, dx)
            // A finger on the screen is a louder voice.
            f.wobble += 0.04 * p.amount * f.presence
            f.ringScale += 0.03 * p.amount * f.presence
            f.level = min(1, f.level + 0.35 * p.amount)
            if f.recording > 0 { f.orbRadius = M.dotRadius * (1 + 0.35 * f.level) }
        }
        return Shown(f: f, bulge: bulge, bulgeAngle: angle)
    }

    // MARK: Touch

    private func touch(layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if press == nil {
                    takeOver(at: now)
                    press = (value.startLocation, now)
                    released = nil
                } else {
                    press?.point = value.location
                }
            }
            .onEnded { value in
                let now = Date.now
                let held = press.map { now.timeIntervalSince($0.at) } ?? 0
                let amount = pull(at: now)?.amount ?? 0
                released = (value.location, now, amount)
                press = nil
                let moved = hypot(value.translation.width, value.translation.height)
                let d = hypot(value.startLocation.x - layout.center.x, value.startLocation.y - layout.center.y)
                if moved < 10 && held < 0.4 && d < layout.ring * 1.15 { toggle(at: now) }
                scheduleResume()
            }
    }

    /// Freezes whatever autoPlay shows into the hand-made state.
    private func takeOver(at now: Date) {
        resume?.cancel()
        guard autoStart != nil else { return }
        let k = clock(at: now)
        recStart = k.sinceStart.map { now.addingTimeInterval(-$0) }
        recStop = k.sinceStop.map { now.addingTimeInterval(-$0) }
        lastTake = k.lastTake
        autoStart = nil
    }

    private var recording: Bool { recStart != nil && recStop == nil }

    private func toggle(at now: Date) {
        if recording, let recStart {
            lastTake = now.timeIntervalSince(recStart)
            recStop = now
            mediumHaptic.impactOccurred()
        } else {
            recStart = now
            recStop = nil
            lightHaptic.impactOccurred()
        }
        if reduceMotion {
            reducedLive = true
            if !recording {
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(0.4))
                    if !recording { reducedLive = false }
                }
            }
        }
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            if recording {
                // A take left running stops itself 8 s after the last touch.
                try? await Task.sleep(for: .seconds(6))
                guard !Task.isCancelled else { return }
                toggle(at: .now)
                try? await Task.sleep(for: .seconds(2))
            }
            guard !Task.isCancelled else { return }
            // Start the loop inside its rest, in step with the breathing glow.
            let now = Date.now
            let m = floor((now.timeIntervalSince(appear) - 7.4) / M.breath)
            let start = appear.addingTimeInterval(m * M.breath)
            autoStart = start
            firstAutoStop = start.addingTimeInterval(M.loop + M.stopAt)
            recStart = nil
            recStop = nil
        }
    }

    // MARK: Drawing

    private func stage(_ s: Shown, layout: Layout) -> some View {
        let f = s.f
        let c = layout.center
        return Canvas { ctx, size in
            let full = Path(CGRect(origin: .zero, size: size))
            ctx.fill(full, with: .color(Self.background))
            // A lighter pool behind the orb.
            ctx.fill(full, with: .radialGradient(
                Gradient(colors: [Color(red: 0.16, green: 0.16, blue: 0.17), Color(red: 0.16, green: 0.16, blue: 0.17).opacity(0)]),
                center: c, startRadius: 0, endRadius: size.width * 0.85))

            // The orb's glow, and the dot's.
            let r = f.orbRadius
            ctx.drawLayer { g in
                g.addFilter(.blur(radius: 26))
                let gr = r * 1.12
                g.fill(Path(ellipseIn: CGRect(x: c.x - gr, y: c.y - gr, width: 2 * gr, height: 2 * gr)),
                       with: .color(Self.red.opacity((0.42 + 0.22 * f.glow) * f.orbness)))
            }
            if f.orbness < 1 {
                ctx.drawLayer { g in
                    g.addFilter(.blur(radius: 9))
                    let gr = r * (2.0 + 0.8 * f.level)
                    g.fill(Path(ellipseIn: CGRect(x: c.x - gr, y: c.y - gr, width: 2 * gr, height: 2 * gr)),
                           with: .color(Self.red.opacity(0.55 * (1 - f.orbness))))
                }
            }
            // The orb itself.
            let body = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
            ctx.fill(body, with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(red: 1.0, green: 0.361, blue: 0.486), location: 0),
                    .init(color: Color(red: 1.0, green: 0.122, blue: 0.294), location: 0.55),
                    .init(color: Color(red: 0.831, green: 0.035, blue: 0.227), location: 1),
                ]),
                center: CGPoint(x: c.x - 0.3 * r, y: c.y - 0.35 * r), startRadius: 0, endRadius: 1.45 * r))
            // "RECORD", set vertically, shrinking and fading with the orb.
            let letters = Array("RECORD")
            let scale = r / layout.orb
            let textAlpha = M.smoothstep(0.55, 0.95, f.orbness)
            if textAlpha > 0.01 {
                let step = 0.19 * layout.orb * scale
                for (i, ch) in letters.enumerated() {
                    let t = ctx.resolve(Text(String(ch)).font(.system(size: 11 * scale, weight: .semibold))
                        .foregroundColor(.white.opacity(0.88 * textAlpha)))
                    ctx.draw(t, at: CGPoint(x: c.x, y: c.y + (Double(i) - 2.5) * step), anchor: .center)
                }
            }

            // The ring: a faint circle at rest, a wobbling white outline while recording.
            let ringPath = Self.ring(center: c, radius: layout.ring * f.ringScale, wobble: f.wobble, time: f.shapeTime,
                                     bulge: s.bulge * layout.ring, bulgeAngle: s.bulgeAngle)
            if f.presence > 0.01 {
                let ghost = Self.ring(center: c, radius: layout.ring * (f.ringScale - 0.01), wobble: f.ghostWobble,
                                      time: f.shapeTime - M.ghostLag, bulge: s.bulge * layout.ring * 0.8, bulgeAngle: s.bulgeAngle)
                ctx.stroke(ghost, with: .color(.white.opacity(0.32 * f.presence)), lineWidth: 1.25)
                ctx.drawLayer { g in
                    g.addFilter(.blur(radius: 5))
                    g.stroke(ringPath, with: .color(.white.opacity(0.35 * f.presence)), lineWidth: 5)
                }
            }
            ctx.stroke(ringPath, with: .color(.white.opacity(0.1 + 0.9 * f.presence)), lineWidth: 1 + 1.25 * f.presence)

            // A tap's ripple: white out of the orb when a take starts, red out of the dot when it stops.
            if let p = f.ripple {
                let e = M.easeOut(p)
                let rr = f.rippleIsStop ? M.dotRadius + (layout.orb * 1.3 - M.dotRadius) * e : layout.orb * (1 + 0.45 * e)
                let color = f.rippleIsStop ? Self.red : Color.white
                ctx.stroke(Path(ellipseIn: CGRect(x: c.x - rr, y: c.y - rr, width: 2 * rr, height: 2 * rr)),
                           with: .color(color.opacity(0.45 * (1 - p))), lineWidth: 1.5)
            }

            // The three glyphs under the orb.
            Self.glyphs(&ctx, center: CGPoint(x: size.width / 2, y: layout.iconsY))
        }
        .frame(width: layout.size.width, height: layout.size.height)
        .accessibilityElement()
        .accessibilityLabel(f.recording > 0 ? "Stop recording" : "Record")
        .accessibilityAddTraits(.isButton)
    }

    /// A closed outline of 180 points: radius × (1 + wobble × shape), plus a bump towards a finger.
    static func ring(center c: CGPoint, radius: Double, wobble: Double, time: Double, bulge: Double, bulgeAngle: Double) -> Path {
        var p = Path()
        let n = 180
        for i in 0...n {
            let th = 2 * Double.pi * Double(i % n) / Double(n)
            var r = radius * (1 + wobble * M.shape(th, time))
            if bulge > 0 {
                var d = th - bulgeAngle
                d = atan2(sin(d), cos(d))
                r += bulge * exp(-(d / 0.55) * (d / 0.55))
            }
            let pt = CGPoint(x: c.x + r * cos(th), y: c.y + r * sin(th))
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        return p
    }

    static func glyphs(_ ctx: inout GraphicsContext, center: CGPoint) {
        let dim = Color.white.opacity(0.45)
        let style = StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)
        // A note.
        var note = Path()
        let n = CGPoint(x: center.x - 44, y: center.y)
        note.move(to: CGPoint(x: n.x + 2, y: n.y + 6))
        note.addLine(to: CGPoint(x: n.x + 2, y: n.y - 9))
        note.addLine(to: CGPoint(x: n.x + 7, y: n.y - 6))
        ctx.stroke(note, with: .color(dim), style: style)
        ctx.fill(Path(ellipseIn: CGRect(x: n.x - 4, y: n.y + 3, width: 6.5, height: 5.5)), with: .color(dim))
        // A waveform (the selected one).
        let hs = [4.0, 10, 16, 8, 18, 10, 5]
        for (i, h) in hs.enumerated() {
            let x = center.x + (Double(i) - 3) * 3.4
            var l = Path()
            l.move(to: CGPoint(x: x, y: center.y - h / 2))
            l.addLine(to: CGPoint(x: x, y: center.y + h / 2))
            ctx.stroke(l, with: .color(.white), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        }
        // A list.
        let lx = center.x + 44
        for (i, w) in [14.0, 10, 14].enumerated() {
            var l = Path()
            let y = center.y - 6 + Double(i) * 6
            l.move(to: CGPoint(x: lx - 7 + (14 - w), y: y))
            l.addLine(to: CGPoint(x: lx + 7, y: y))
            ctx.stroke(l, with: .color(dim), style: style)
        }
    }

    private func timer(_ s: Shown, layout: Layout) -> some View {
        let f = s.f
        let secs = Int(f.seconds)
        let live = f.recording > 0 ? f.presence : 0
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text("\(secs / 60)").foregroundStyle(.white.opacity(0.42))
                Text(String(format: ":%02d", secs % 60)).foregroundStyle(.white)
            }
            .font(BubbleRingRecorderFont.font(size: 104))
            .frame(height: 112, alignment: .leading)
            ZStack(alignment: .leading) {
                Text("Tap the orb to record")
                    .foregroundStyle(.white.opacity(0.5))
                    .opacity(1 - live)
                HStack(spacing: 8) {
                    Circle().fill(Self.red).frame(width: 7, height: 7)
                        .opacity(0.55 + 0.45 * f.level)
                    Text("Recording · 48 kHz").foregroundStyle(Self.redText)
                }
                .opacity(live)
            }
            .font(.system(size: 15, weight: .medium))
            .frame(height: 24)
        }
        .offset(x: 24, y: layout.insets.top + 4)
        .allowsHitTesting(false)
    }

    private func menu(layout: Layout) -> some View {
        VStack(alignment: .trailing, spacing: 5) {
            Capsule().fill(.white.opacity(0.85)).frame(width: 20, height: 1.6)
            Capsule().fill(.white.opacity(0.85)).frame(width: 20, height: 1.6)
            Capsule().fill(.white.opacity(0.85)).frame(width: 13, height: 1.6)
        }
        .frame(width: 44, height: 44)
        .offset(x: layout.size.width - 24 - 34, y: layout.insets.top + 22)
        .accessibilityLabel("Menu")
    }

    // MARK: List

    private struct Row {
        var title: String
        var detail: String
        var seed: Double
    }

    private static let rows = [
        Row(title: "Chorus hum", detail: "0:14 · Oct 12", seed: 1.3),
        Row(title: "Kitchen demo", detail: "0:34 · Oct 10", seed: 4.1),
    ]

    private static let barCount = 30

    private func list(_ k: M.Clock, _ s: Shown, layout: Layout) -> some View {
        let f = s.f
        let w = layout.size.width
        let live = f.recording > 0 ? f.presence : 0
        let length = f.recording > 0 ? f.seconds : k.lastTake
        let bars = M.bars(Self.barCount, length: length)
        return ZStack(alignment: .topLeading) {
            Text("RECORDINGS")
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.4)
                .foregroundStyle(.white.opacity(0.4))
                .offset(x: 24, y: layout.rowsTop - 26)
            ForEach(0..<4, id: \.self) { i in
                Rectangle().fill(.white.opacity(0.07))
                    .frame(width: w - 48, height: 1)
                    .offset(x: 24, y: layout.rowsTop + Double(i) * Self.rowHeight)
            }
            // The last take, live while recording.
            rowView(y: layout.rowsTop, width: w) {
                ZStack {
                    Circle().strokeBorder(.white.opacity(0.18 * (1 - live)), lineWidth: 1)
                    Circle().strokeBorder(Self.red.opacity(0.5 * live), lineWidth: 1)
                    Self.playTriangle.fill(.white).frame(width: 10, height: 12).offset(x: 1.5).opacity(1 - live)
                    Circle().fill(Self.red).frame(width: 10 * (1 + 0.3 * f.level), height: 10 * (1 + 0.3 * f.level)).opacity(live)
                }
            } text: {
                ZStack(alignment: .leading) {
                    titles("Untitled take", detail: "\(Self.time(k.lastTake)) · Today, 9:41").opacity(1 - live)
                    titles("Recording", detail: "\(Self.time(f.seconds)) · Now", tint: Self.redText).opacity(live)
                }
            } wave: {
                Canvas { ctx, size in
                    Self.drawBars(&ctx, size: size, bars: bars, color: .white.opacity(0.55 + 0.35 * live))
                }
            }
            ForEach(0..<Self.rows.count, id: \.self) { i in
                let row = Self.rows[i]
                rowView(y: layout.rowsTop + Double(i + 1) * Self.rowHeight, width: w) {
                    ZStack {
                        Circle().strokeBorder(.white.opacity(0.18), lineWidth: 1)
                        Self.playTriangle.fill(.white).frame(width: 10, height: 12).offset(x: 1.5)
                    }
                } text: {
                    titles(row.title, detail: row.detail)
                } wave: {
                    Canvas { ctx, size in
                        let b: [Double?] = (0..<Self.barCount).map { j in
                            let x = Double(j)
                            return 0.15 + 0.85 * abs(sin(x * 0.61 + row.seed)) * (0.35 + 0.65 * pow(sin(x * 0.23 + row.seed * 2), 2))
                        }
                        Self.drawBars(&ctx, size: size, bars: b, color: .white.opacity(0.3))
                    }
                }
            }
        }
        .allowsHitTesting(false)
    }

    private func titles(_ title: String, detail: String, tint: Color = .white) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.system(size: 16, weight: .medium)).foregroundStyle(tint)
            Text(detail).font(.system(size: 12.5).monospacedDigit()).foregroundStyle(.white.opacity(0.45))
        }
        .lineLimit(1)
    }

    private func rowView<A: View, B: View, C: View>(y: Double, width: Double, @ViewBuilder icon: () -> A,
                                                   @ViewBuilder text: () -> B, @ViewBuilder wave: () -> C) -> some View {
        HStack(spacing: 14) {
            icon().frame(width: 36, height: 36)
            text().frame(maxWidth: .infinity, alignment: .leading)
            wave().frame(width: 118, height: 24)
            VStack(spacing: 3) {
                ForEach(0..<3, id: \.self) { _ in Circle().fill(.white.opacity(0.45)).frame(width: 3, height: 3) }
            }
            .frame(width: 12)
        }
        .padding(.horizontal, 24)
        .frame(width: width, height: Self.rowHeight)
        .offset(y: y)
    }

    static let playTriangle = Path { p in
        p.move(to: CGPoint(x: 0, y: 0))
        p.addLine(to: CGPoint(x: 10, y: 6))
        p.addLine(to: CGPoint(x: 0, y: 12))
        p.closeSubpath()
    }

    /// Bars 2 pt wide, 4 pt apart, 3…22 pt tall; bars not recorded yet are 2 pt dots.
    static func drawBars(_ ctx: inout GraphicsContext, size: CGSize, bars: [Double?], color: Color) {
        for (i, v) in bars.enumerated() {
            let x = Double(i) * 4
            if let v {
                let h = 3 + 19 * v
                ctx.fill(Path(roundedRect: CGRect(x: x, y: (size.height - h) / 2, width: 2, height: h), cornerRadius: 1), with: .color(color))
            } else {
                ctx.fill(Path(ellipseIn: CGRect(x: x, y: size.height / 2 - 1, width: 2, height: 2)), with: .color(.white.opacity(0.15)))
            }
        }
    }

    static func time(_ seconds: Double) -> String {
        let s = Int(seconds)
        return String(format: "%d:%02d", s / 60, s % 60)
    }
}

enum BubbleRingRecorderFont {
    static let name = "BarlowCondensed-ExtraLight"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "BubbleRingRecorder-BarlowCondensed-ExtraLight", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }
}
