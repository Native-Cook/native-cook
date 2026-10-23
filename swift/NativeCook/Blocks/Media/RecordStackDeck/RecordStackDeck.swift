import SwiftUI
import UIKit

/*
 Record Stack Deck — a crate of ten records shown as a vertical 3D deck; hold one and it rises,
 flattens, and its vinyl slides half out of the sleeve, spinning.

 Look: black screen lit from above and below by the colour of the record at the focus. The
 sleeves (squares 0.78 × the screen width, covers drawn in code, invented albums) lie stacked at
 40 % of the height: the one at the focus is seen edge-on as a thin strip, the others open up
 with distance (the cosine of the tilt is 0.02 + 0.08 a − 0.0065 a², a = |offset|) and sit
 20 a + 9 a² pt from the focus, each titled in small type above its strip. A white ring marks
 the focus. Under the deck a large caption ("Every album, pressed to vinyl.") over a fade.

 autoPlay timeline (loop = 10 s, RecordStackDeckMotion.swift):
   0.0 – 0.4    rest on the first record.
   0.4 – 1.1    one record down (ease-out-back), 1.1 – 1.9 two more (ease-out).
   1.9 – 2.8    the ring fills as if held: sixteen ticks light up around it.
   2.8 – 3.6    the record rises out of the deck and flattens (ease-out-back) while the others
                dim and spread; 3.6 – 4.2 its vinyl slides half out to the right (the sleeve
                moves left), spinning at 200 °/s with the cover as its label; the caption
                changes to the album's title.
   6.6 – 8.0    the vinyl slides back in and the sleeve returns to the deck.
   8.0 – 9.7    a long flick through seven records (ease-out-quart) to the first one again:
                the crate has turned once, so 10.0 is the same frame as 0.0.

 Touch: drag up or down to turn the deck (44 pt per record, a selection tick per record); on
 release it is thrown by its speed and settles on a record (critically damped spring, ω 12).
 Tap a strip to bring it to the focus. Hold the focus (0.8 s, the ring fills) or tap it to open
 the record; tap anywhere to put it back. 2 s after the last touch the record goes back, the
 deck settles on the first record and the loop starts again.
 Reduced motion: no loop and no spin; opening and closing cross-fade in 0.25 s; the deck still
 follows a finger and settles without being thrown.
 */

struct RecordStackDeck: View {
    var autoPlay = false

    static let loop = 10.0
    typealias M = RecordStackDeckMotion
    typealias Art = RecordStackDeckArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    struct Pose {
        var scroll = 0.0
        var ring = 0.0
        /// 0 in the deck, 1 lifted flat, 2 vinyl half out.
        var open = 0.0
        /// The vinyl's angle in degrees.
        var spin = 0.0
        /// Reduced motion only: the open scene's cross-fade (0 deck, 1 open).
        var fade: Double? = nil
    }

    @State private var autoStart: Date? = nil
    @State private var settle: (date: Date, from: Double, velocity: Double, to: Double) = (.distantPast, 0, 0, 0)
    @State private var dragScroll: Double? = nil
    @State private var dragFrom = 0.0
    @State private var moved = false
    @State private var touching = false
    @State private var lastTick = 0
    /// The ring being held: when it started and how long it takes to fill.
    @State private var hold: (date: Date, duration: Double)? = nil
    /// Opening (true) or closing (false) since a date.
    @State private var openMove: (date: Date, opening: Bool)? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var holdTask: Task<Void, Never>? = nil
    @State private var busy = false
    @State private var tick = UISelectionFeedbackGenerator()
    @State private var medium = UIImpactFeedbackGenerator(style: .medium)
    @State private var light = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let w = proxy.size.width + insets.leading + insets.trailing
            let h = proxy.size.height + insets.top + insets.bottom
            TimelineView(.animation(paused: reduceMotion && !busy)) { timeline in
                let p = pose(at: timeline.date)
                content(p, w: w, h: h, insets: insets)
            }
            .ignoresSafeArea()
        }
        .background(Color(hex: 0x0A0A0C).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            tick.prepare(); medium.prepare(); light.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel(); holdTask?.cancel() }
    }

    // MARK: Time

    private func pose(at date: Date) -> Pose {
        if let autoStart, date >= autoStart {
            let t = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            return Pose(scroll: M.loopScroll(t), ring: M.loopRing(t), open: M.loopOpen(t), spin: 200 * t)
        }
        var p = Pose()
        p.scroll = dragScroll ?? M.settle(from: settle.from, velocity: settle.velocity, to: settle.to,
                                          max(0, date.timeIntervalSince(settle.date))).0
        if let hold { p.ring = M.clamp01(date.timeIntervalSince(hold.date) / hold.duration) }
        p.open = openValue(at: date)
        if reduceMotion {
            if let m = openMove {
                let f = M.clamp01(date.timeIntervalSince(m.date) / 0.25)
                p.fade = m.opening ? f : 1 - f
            } else {
                p.fade = 0
            }
            p.open = 2
        }
        p.spin = reduceMotion ? 0 : 200 * date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1800)
        return p
    }

    private func openValue(at date: Date) -> Double {
        guard let m = openMove else { return 0 }
        let e = max(0, date.timeIntervalSince(m.date))
        if e >= M.openLength { return m.opening ? 2 : 0 }
        return m.opening ? M.opening(e) : M.closing(e)
    }

    /// Freezes whatever plays into the hand-driven state.
    private func takeOver(_ now: Date) {
        resume?.cancel()
        guard let start = autoStart else { return }
        autoStart = nil
        guard now >= start else { return }
        let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop)
        settle = (now, M.loopScroll(t), 0, (M.loopScroll(t)).rounded())
        let o = M.loopOpen(t)
        if o > 0 {
            // Carry on the open record; a closing one finishes closing.
            let closingNow = t >= 6.6
            let e = closingNow ? t - 6.6 : min(t - 2.8, M.openLength)
            openMove = (now.addingTimeInterval(-e), !closingNow)
        } else {
            openMove = nil
        }
        hold = nil
    }

    private func gesture(w: Double, h: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if !touching {
                    touching = true
                    moved = false
                    takeOver(now)
                    busy = true
                    let s = pose(at: now)
                    dragFrom = s.scroll
                    lastTick = Int(floor(s.scroll + 0.5))
                    // Holding the focus fills the ring; when it is full the record opens.
                    if openMove == nil || openMove?.opening == false && openValue(at: now) == 0,
                       abs(value.startLocation.y - h * M.focusY) < 34 {
                        openMove = nil
                        hold = (now, 0.8)
                        holdTask?.cancel()
                        holdTask = Task { @MainActor in
                            try? await Task.sleep(for: .seconds(0.8))
                            guard !Task.isCancelled, touching, !moved else { return }
                            openRecord(now: .now)
                        }
                    }
                    return
                }
                guard openMove?.opening != true else { return }
                if !moved && abs(value.translation.height) > 8 {
                    moved = true
                    hold = nil
                    holdTask?.cancel()
                }
                if moved {
                    let s = dragFrom - value.translation.height / M.dragUnit
                    dragScroll = s
                    let k = Int(floor(s + 0.5))
                    if k != lastTick {
                        lastTick = k
                        tick.selectionChanged()
                    }
                }
            }
            .onEnded { value in
                let now = Date.now
                touching = false
                holdTask?.cancel()
                if moved {
                    let s = dragScroll ?? dragFrom
                    let v = reduceMotion ? 0 : -value.velocity.height / M.dragUnit
                    settle = (now, s, v, (s + v * 0.25).rounded())
                    dragScroll = nil
                } else if let open = openMove, open.opening {
                    // A tap on an open record puts it back.
                    if now.timeIntervalSince(open.date) >= M.openLength { closeRecord(now: now) }
                } else if let hd = hold {
                    // A tap on the focus: the ring finishes quickly, then the record opens.
                    let progress = M.clamp01(now.timeIntervalSince(hd.date) / hd.duration)
                    hold = (now.addingTimeInterval(-progress * 0.3), 0.3)
                    holdTask = Task { @MainActor in
                        try? await Task.sleep(for: .seconds(max(0, (1 - progress) * 0.3)))
                        guard !Task.isCancelled else { return }
                        openRecord(now: .now)
                    }
                } else {
                    // A tap on another strip brings it to the focus.
                    let s = pose(at: now).scroll
                    var best = 0.0, bestDistance = Double.infinity
                    for i in 0..<M.count {
                        let d = M.offset(i, scroll: s)
                        let dist = abs(h * M.focusY + M.y(d) - value.location.y)
                        if dist < bestDistance && M.fade(d) > 0.3 { bestDistance = dist; best = d }
                    }
                    settle = (now, s, 0, (s + best).rounded())
                    tick.selectionChanged()
                }
                scheduleResume()
                if reduceMotion { markIdle(after: 0.5) }
            }
    }

    private func openRecord(now: Date) {
        hold = nil
        openMove = (now, true)
        medium.impactOccurred()
        if reduceMotion { busy = true; markIdle(after: 0.4) }
        scheduleResume()
    }

    private func closeRecord(now: Date) {
        openMove = (now, false)
        light.impactOccurred()
        if reduceMotion { busy = true; markIdle(after: 0.4) }
    }

    private func markIdle(after seconds: Double) {
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            if !touching { busy = false }
        }
    }

    /// 2 s after the last touch: put the record back, settle on the first record, start the loop.
    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { busy = touching; return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled, !touching else { return }
            var now = Date.now
            if let m = openMove, m.opening {
                let e = now.timeIntervalSince(m.date)
                if e < M.openLength {
                    // Still opening: let it finish first.
                    now = m.date.addingTimeInterval(M.openLength)
                }
                openMove = (now, false)
                now = now.addingTimeInterval(M.openLength)
            }
            let s = pose(at: now).scroll
            let n = Double(M.count)
            settle = (now, s, 0, (s / n).rounded() * n)
            autoStart = now.addingTimeInterval(0.8)
        }
    }

    // MARK: Drawing

    @ViewBuilder
    private func content(_ p: Pose, w: Double, h: Double, insets: EdgeInsets) -> some View {
        let s = 0.78 * w
        let cy = h * M.focusY
        let deckAlpha = p.fade.map { 1 - $0 } ?? 1
        let lifted = p.fade == nil ? min(p.open, 1) : 0
        let out = p.fade == nil ? M.easeInOut(max(0, p.open - 1)) : 1
        let focus = ((Int((p.scroll).rounded()) % M.count) + M.count) % M.count
        let glow = glowColor(p.scroll)
        let captionTop = h - max(insets.bottom, 16) - 40 - 92

        ZStack(alignment: .topLeading) {
            // The focused record's colour, lighting the screen.
            Canvas { ctx, size in
                let rect = Path(CGRect(origin: .zero, size: size))
                let boost = 1 + 0.5 * min(p.open, 1) * (p.fade ?? 1)
                ctx.fill(rect, with: .radialGradient(Gradient(colors: [glow.opacity(0.5 * boost), glow.opacity(0.12 * boost), glow.opacity(0)]),
                                                     center: CGPoint(x: w / 2, y: cy - h * 0.34), startRadius: 0, endRadius: w * 0.95))
                ctx.fill(rect, with: .radialGradient(Gradient(colors: [glow.opacity(0.3 * boost), glow.opacity(0)]),
                                                     center: CGPoint(x: w / 2, y: h + 40), startRadius: 0, endRadius: w * 0.9))
            }
            .frame(width: w, height: h)
            .allowsHitTesting(false)

            ForEach(0..<M.count, id: \.self) { i in
                card(i, p: p, s: s, w: w, cy: cy, top: insets.top, focus: focus, lifted: lifted, out: out, deckAlpha: deckAlpha)
            }

            // The vinyl behind the lifted sleeve.
            if p.open > 1 || p.fade != nil {
                let f = s * M.flatScale
                Canvas { ctx, _ in
                    Art.vinyl(ctx, album: focus, center: CGPoint(x: w / 2 + M.vinylShift * f * out, y: cy), radius: M.vinylRadius * f, angle: p.spin)
                }
                .frame(width: w, height: h)
                .opacity(p.fade ?? 1)
                .zIndex(50)
                .allowsHitTesting(false)
            }

            // Reduced motion: the open sleeve cross-fades in over the deck.
            if let f = p.fade, f > 0 {
                Canvas { ctx, _ in Art.cover(ctx, focus, s) }
                    .frame(width: s, height: s)
                    .scaleEffect(M.flatScale)
                    .position(x: w / 2 + M.sleeveShift * s * M.flatScale, y: cy)
                    .opacity(f)
                    .zIndex(100)
                    .allowsHitTesting(false)
            }

            strips(p, s: s, w: w, h: h, cy: cy, safeTop: insets.top, alpha: deckAlpha * (1 - M.smoothstep(0, 0.4, p.open * (p.fade == nil ? 1 : 0))))
                .zIndex(60)

            ring(p, w: w, cy: cy, alpha: deckAlpha * (1 - M.smoothstep(0, 0.4, p.fade == nil ? p.open : 0)))
                .zIndex(61)

            // A fade under the caption.
            LinearGradient(stops: [.init(color: Color(hex: 0x0A0A0C, opacity: 0), location: 0),
                                   .init(color: Color(hex: 0x0A0A0C, opacity: 0.92), location: 0.55)],
                           startPoint: .top, endPoint: .bottom)
                .frame(width: w, height: h - captionTop + 70)
                .offset(y: captionTop - 70)
                .allowsHitTesting(false)
                .zIndex(70)

            captions(p, focus: focus, w: w, top: captionTop)
                .zIndex(80)
            header(w: w, top: insets.top)
                .zIndex(80)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(gesture(w: w, h: h))
    }

    /// A tilt about the sleeve's horizontal centre line, seen from `M.depth` points away.
    static func tilted(_ degrees: Double, side s: Double) -> ProjectionTransform {
        var t = CATransform3DMakeTranslation(-s / 2, -s / 2, 0)
        t = CATransform3DConcat(t, CATransform3DMakeRotation(degrees * .pi / 180, 1, 0, 0))
        var p = CATransform3DIdentity
        p.m34 = -1 / M.depth
        t = CATransform3DConcat(t, p)
        t = CATransform3DConcat(t, CATransform3DMakeTranslation(s / 2, s / 2, 0))
        return ProjectionTransform(t)
    }

    private func glowColor(_ scroll: Double) -> Color {
        let n = Double(M.count)
        let s = (scroll.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n)
        let a = Int(floor(s)) % M.count, b = (a + 1) % M.count
        let f = s - floor(s)
        func rgb(_ hex: UInt32) -> (Double, Double, Double) {
            (Double((hex >> 16) & 0xff) / 255, Double((hex >> 8) & 0xff) / 255, Double(hex & 0xff) / 255)
        }
        let x = rgb(Art.albums[a].glow), y = rgb(Art.albums[b].glow)
        return Color(.sRGB, red: x.0 + (y.0 - x.0) * f, green: x.1 + (y.1 - x.1) * f, blue: x.2 + (y.2 - x.2) * f)
    }

    private func card(_ i: Int, p: Pose, s: Double, w: Double, cy: Double, top: Double, focus: Int, lifted: Double, out: Double, deckAlpha: Double) -> some View {
        let d = M.offset(i, scroll: p.scroll)
        let isPicked = i == focus && p.open > 0 && p.fade == nil
        var tilt = M.tilt(d)
        var y = M.y(d)
        var scale = 1.0
        var x = w / 2
        var alpha = M.fade(d) * M.topFade(cy + M.y(d), safeTop: top) * deckAlpha
        var z = -abs(d)
        if isPicked {
            tilt *= 1 - lifted
            y *= 1 - lifted
            scale = 1 - (1 - M.flatScale) * lifted
            x += M.sleeveShift * s * M.flatScale * out
            z = 100
        } else if p.fade == nil {
            let k = min(p.open, 1)
            y *= 1 + 0.3 * k
            alpha *= 1 - 0.85 * k
        }
        return Canvas { ctx, _ in Art.cover(ctx, i, s) }
            .frame(width: s, height: s)
            .scaleEffect(scale)
            .projectionEffect(Self.tilted(tilt, side: s))
            .shadow(color: .black.opacity(isPicked ? 0.55 * lifted : 0), radius: 24, y: 14)
            .position(x: x, y: cy + y)
            .opacity(alpha)
            .zIndex(z)
            .allowsHitTesting(false)
    }

    /// Each strip's title above it.
    private func strips(_ p: Pose, s: Double, w: Double, h: Double, cy: Double, safeTop: Double, alpha: Double) -> some View {
        Canvas { ctx, _ in
            guard alpha > 0.01 else { return }
            for i in 0..<M.count {
                let d = M.offset(i, scroll: p.scroll)
                let a = alpha * M.fade(d) * M.topFade(cy + M.y(d), safeTop: safeTop) * (1 - M.smoothstep(2.4, 3.4, abs(d)))
                guard a > 0.01 else { continue }
                let top = cy + M.y(d) + M.topEdge(s, M.tilt(d))
                let label = Text(Art.albums[i].title).font(.system(size: 10, weight: .semibold)).foregroundColor(.white)
                    + Text("  " + Art.albums[i].artist).font(.system(size: 10)).foregroundColor(.white.opacity(0.6))
                var c = ctx
                c.opacity = a
                c.draw(label, at: CGPoint(x: (w - s) / 2 + 2, y: top - 4), anchor: .bottomLeading)
            }
        }
        .frame(width: w, height: h)
        .allowsHitTesting(false)
    }

    private func ring(_ p: Pose, w: Double, cy: Double, alpha: Double) -> some View {
        Canvas { ctx, _ in
            guard alpha > 0.01 else { return }
            let c = CGPoint(x: w / 2, y: cy)
            var g = ctx
            g.opacity = alpha
            let full = p.ring >= 1 ? 1.0 : 0.0
            let r = 12.0 + 2 * full
            g.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(.white.opacity(0.9)), lineWidth: 1.5)
            if p.ring > 0 {
                var arc = Path()
                arc.addArc(center: c, radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * p.ring), clockwise: false)
                g.stroke(arc, with: .color(.white), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                for k in 0..<16 {
                    let on = M.smoothstep(Double(k) / 16, Double(k + 1) / 16, p.ring)
                    guard on > 0 else { continue }
                    let a = -Double.pi / 2 + Double(k) / 16 * 2 * .pi
                    var tickPath = Path()
                    tickPath.move(to: CGPoint(x: c.x + cos(a) * (r + 6), y: c.y + sin(a) * (r + 6)))
                    tickPath.addLine(to: CGPoint(x: c.x + cos(a) * (r + 6 + 6 * on), y: c.y + sin(a) * (r + 6 + 6 * on)))
                    g.stroke(tickPath, with: .color(.white.opacity(0.9 * on)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                }
            }
        }
        .frame(width: w, height: cy * 2)
        .allowsHitTesting(false)
    }

    private func captions(_ p: Pose, focus: Int, w: Double, top: Double) -> some View {
        let deck = p.fade.map { 1 - $0 } ?? (1 - M.smoothstep(0.2, 0.8, p.open))
        let open = p.fade ?? M.smoothstep(1.2, 1.8, p.open)
        let album = Art.albums[focus]
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Every album,\npressed to vinyl.")
                    .font(.system(size: 30, weight: .bold))
                    .tracking(-0.5)
                    .foregroundStyle(.white)
                Text("Hold a record to play it.")
                    .font(.system(size: 15))
                    .foregroundStyle(.white.opacity(0.6))
            }
            .opacity(deck)
            .offset(y: 8 * (1 - deck))
            VStack(alignment: .leading, spacing: 10) {
                Text("Now spinning\n\(album.title)")
                    .font(.system(size: 30, weight: .bold))
                    .tracking(-0.5)
                    .foregroundStyle(.white)
                    .lineLimit(2)
                Text("\(album.artist) · Side A")
                    .font(.system(size: 15))
                    .foregroundStyle(.white.opacity(0.6))
            }
            .opacity(open)
            .offset(y: 8 * (1 - open))
        }
        .frame(width: w - 48, alignment: .topLeading)
        .offset(x: 24, y: top)
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(open > 0.5 ? "Now spinning \(album.title) by \(album.artist)" : "Record crate, \(album.title) by \(album.artist) in focus")
    }

    private func header(w: Double, top: Double) -> some View {
        HStack {
            Text("Crate")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.white)
            Spacer()
            Text("10 RECORDS")
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .tracking(1)
                .foregroundStyle(.white.opacity(0.55))
        }
        .frame(width: w - 48)
        .offset(x: 24, y: top + 10)
        .allowsHitTesting(false)
    }
}
