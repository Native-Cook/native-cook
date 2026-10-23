import SwiftUI
import UIKit

/*
 Tape Shelf Player — a shelf of home-video tapes under a retro player: a tape slides out of its
 sleeve, flies into the slot and the little CRT lights up with the memory.

 Look: a warm off-white page. At the top a light grey shelf band carries the player: a dark
 rounded box (0.52 × the screen width, 188 pt tall) with a curved-glass CRT, a cassette slot, an
 LED and three buttons. Below, "Collections" and a 3 × 3 grid of VHS sleeves (invented brands,
 a rainbow band, a white sticker with a handwritten title in Gochi Hand), each holding its
 cassette. A magenta "Play something old" pill floats at the bottom; while a tape plays, a black
 transport bar (eject, rewind, pause, fast-forward, shuffle) takes its place.

 autoPlay timeline (loop = 10 s, TapeShelfPlayerMotion.swift), with the middle tape "Sunset pier":
   0.0 – 0.8    rest; the screen blinks INSERT TAPE once a second.
   0.8 – 1.05   the sleeve lifts (5 % larger, deeper shadow); 0.95 – 1.4 the cassette slides out to
                its right (ease-in-out); 1.4 – 2.15 it flies on an arc to the slot, turning from
                upright to level, growing 12 % mid-flight and shrinking to the slot's width; the
                sleeve settles back.
   2.15 – 2.45  it squashes flat into the slot and is gone; 2.45 the player jolts (3 pt, a damped
                sine) and the LED turns green.
   2.5 – 2.85   the CRT powers on (a bright line opening into the picture); the transport bar
                rises (2.6 – 3.0) as the pill sinks.
   2.85 – 6.6   the home video plays: a sky and a sun over water with drifting glints, a pier,
                a rolling tracking band, the camcorder's "PLAY ▶" and date stamp.
   6.6 – 8.8    eject: the eject key dips, the picture collapses to a line and a dot, the bar
                sinks and the pill returns, the cassette pops out of the slot, flies back and
                slides into its sleeve.
   8.8 – 10.0   rest: the same frame as 0.0.

 Touch: tap a tape (or the pill, for the next one) to play it; tap eject or the player to eject.
 A touch takes over from whatever is on screen, the loop included: tapping another tape sends the
 one in the player home first (ejected once it is in, turned back mid-flight before that) and then
 inserts the tapped one; tapping a tape that is on its way home turns it round. Pause freezes the
 picture. 2 s after the last touch (and once a tape that was tapped has played for 2 s) the tape
 is ejected and the loop starts again. Reduced motion: no loop and no flight; the screen and the bars cross-fade in 0.3 s and the
 picture holds still.
 */

struct TapeShelfPlayer: View {
    var autoPlay = false

    static let loop = 10.0
    typealias M = TapeShelfPlayerMotion
    typealias Art = TapeShelfPlayerArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var autoStart: Date? = nil

    /// What the player is doing, as a function of the time since `date`.
    struct Seq {
        enum Kind: Equatable {
            /// Inserting the tape, then playing it.
            case insert
            /// Ejecting it after `played` seconds of video.
            case eject(played: Double)
            /// Turned back before it was in: the insert run backwards from `from` seconds.
            case back(from: Double)
        }
        var date: Date
        var kind: Kind
        var tape: Int
        /// A tape to insert as soon as this one is home.
        var then: Int? = nil
    }
    @State private var seq: Seq? = nil
    @State private var frozen: Double? = nil
    @State private var nextTape = M.loopTape + 1
    @State private var resume: Task<Void, Never>? = nil
    @State private var busy = false
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var heavy = UIImpactFeedbackGenerator(style: .heavy)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let g = Art.Geo(w: proxy.size.width + insets.leading + insets.trailing,
                            h: proxy.size.height + insets.top + insets.bottom,
                            top: insets.top, bottom: insets.bottom)
            TimelineView(.animation(paused: reduceMotion && !busy)) { timeline in
                let (p, clock) = pose(at: timeline.date)
                content(p, clock: clock, g: g)
            }
            .ignoresSafeArea()
        }
        .background(Art.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            light.prepare(); heavy.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Time

    private func pose(at date: Date) -> (M.Pose, Double) {
        if let autoStart, date >= autoStart {
            let t = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            return (M.loopPose(t), t)
        }
        let clock = date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1000)
        guard let s = resolved(seq, at: date) else { return (M.Pose(), clock) }
        let e = date.timeIntervalSince(s.date)
        var p: M.Pose
        if reduceMotion {
            p = M.Pose()
            p.tape = s.tape
            let f = M.ramp(0, 0.3, e)
            let on = s.kind == .insert ? f : 1 - f
            p.screen = on; p.transport = on; p.pill = 1 - on; p.led = on > 0.5 ? 1 : 0
            p.video = 2.4
        } else {
            switch s.kind {
            case .insert: p = M.insert(e, tape: s.tape)
            case .eject(let played): p = e < M.ejectLength ? M.eject(e, tape: s.tape, played: played) : M.Pose()
            case .back(let from): p = M.insert(max(0, from - e), tape: s.tape)
            }
        }
        if let frozen, s.kind == .insert, !reduceMotion { p.video = frozen }
        return (p, clock)
    }

    /// How long a tape going home takes; nil while one goes in or plays.
    private func outLength(_ s: Seq) -> Double? {
        switch s.kind {
        case .insert: return nil
        case .eject: return reduceMotion ? 0.3 : M.ejectLength
        case .back(let from): return reduceMotion ? 0.3 : from
        }
    }

    /// The sequence at a date: a queued tape starts going in the moment the previous one is home.
    private func resolved(_ s: Seq?, at date: Date) -> Seq? {
        guard let s, let next = s.then, let len = outLength(s), date.timeIntervalSince(s.date) >= len else { return s }
        return Seq(date: s.date.addingTimeInterval(len), kind: .insert, tape: next)
    }

    private var playing: Bool { resolved(seq, at: .now)?.kind == .insert }

    /// Turns whatever is on screen (the loop included) into the hand-driven state.
    private func takeOver(_ now: Date) {
        resume?.cancel()
        if let start = autoStart {
            autoStart = nil
            if now >= start {
                let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop)
                if t >= M.insertAt && t < M.ejectAt {
                    seq = Seq(date: now.addingTimeInterval(-(t - M.insertAt)), kind: .insert, tape: M.loopTape)
                } else if t >= M.ejectAt && t < M.ejectAt + M.ejectLength {
                    seq = Seq(date: now.addingTimeInterval(-(t - M.ejectAt)), kind: .eject(played: M.ejectAt - M.insertAt - M.powerOnAt),
                              tape: M.loopTape)
                } else {
                    seq = nil
                }
                frozen = nil
                return
            }
        }
        seq = resolved(seq, at: now)
    }

    /// Sends the tape that is going in or playing back to its sleeve: ejected once it is in, turned
    /// back the way it came before that. False when no tape is going in or playing.
    @discardableResult
    private func stop(_ now: Date) -> Bool {
        guard let s = seq, s.kind == .insert else { return false }
        let e = now.timeIntervalSince(s.date)
        if reduceMotion || e >= M.insertLength {
            seq = Seq(date: now, kind: .eject(played: frozen ?? max(0, e - M.powerOnAt)), tape: s.tape)
        } else {
            seq = Seq(date: now, kind: .back(from: e), tape: s.tape)
        }
        frozen = nil
        return true
    }

    private func play(_ tape: Int) {
        let now = Date.now
        takeOver(now)
        nextTape = (tape + 1) % Art.tapes.count
        light.impactOccurred()
        if var s = seq, let len = outLength(s), now.timeIntervalSince(s.date) < len {
            // A tape is on its way home.
            if case .back(let from) = s.kind, s.tape == tape {
                // The tapped one: turn it round.
                seq = Seq(date: now.addingTimeInterval(-(from - now.timeIntervalSince(s.date))), kind: .insert, tape: tape)
            } else {
                s.then = tape
                seq = s
            }
        } else if let s = seq, s.kind == .insert {
            if s.tape != tape {
                // Another tape: send this one home, then insert the tapped one.
                stop(now)
                seq?.then = tape
            } else if now.timeIntervalSince(s.date) >= M.insertLength {
                // The one that plays: eject it.
                stop(now)
            }
        } else {
            seq = Seq(date: now, kind: .insert, tape: tape)
            frozen = nil
        }
        clunk()
        markBusy()
        scheduleResume()
    }

    /// The heavy tap as a tape seats, 1.65 s into its insert (which may still be queued).
    private func clunk() {
        guard !reduceMotion, let s = seq else { return }
        let start: Date
        if s.kind == .insert {
            start = s.date
        } else if s.then != nil, let len = outLength(s) {
            start = s.date.addingTimeInterval(len)
        } else {
            return
        }
        let wait = start.addingTimeInterval(1.65).timeIntervalSinceNow
        guard wait > 0 else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(wait))
            if let r = resolved(seq, at: .now), r.kind == .insert, abs(r.date.timeIntervalSince(start)) < 0.001 {
                heavy.impactOccurred(intensity: 0.8)
            }
        }
    }

    private func eject() {
        let now = Date.now
        takeOver(now)
        if stop(now) {
            light.impactOccurred()
            markBusy()
        } else if seq?.then != nil {
            // Nothing in the player yet: forget the tape that was waiting.
            seq?.then = nil
            light.impactOccurred(intensity: 0.6)
        }
        scheduleResume()
    }

    private func togglePause() {
        let now = Date.now
        takeOver(now)
        guard let s = seq, s.kind == .insert else { scheduleResume(); return }
        if let f = frozen {
            // Carry on from the frozen frame.
            seq = Seq(date: now.addingTimeInterval(-(f + M.powerOnAt)), kind: .insert, tape: s.tape)
            frozen = nil
        } else {
            frozen = max(0, now.timeIntervalSince(s.date) - M.powerOnAt)
        }
        light.impactOccurred(intensity: 0.6)
        scheduleResume()
    }

    private func markBusy() {
        guard reduceMotion else { return }
        busy = true
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(0.7))
            busy = false
        }
    }

    /// A tape that was tapped plays at least this long before it is sent home on its own.
    static let minPlay = 2.0

    /// 2 s after the last touch (and once a tape going in has seated and played `minPlay` seconds): the
    /// tape is sent home, then the loop starts at rest.
    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            var now = Date.now
            seq = resolved(seq, at: now)
            var ready: Date? = nil
            if let s = seq {
                if s.kind == .insert {
                    ready = s.date.addingTimeInterval(M.insertLength + Self.minPlay)
                } else if s.then != nil, let len = outLength(s) {
                    ready = s.date.addingTimeInterval(len + M.insertLength + Self.minPlay)
                }
            }
            if let ready, ready > now {
                try? await Task.sleep(for: .seconds(ready.timeIntervalSince(now)))
                guard !Task.isCancelled else { return }
                now = Date.now
                seq = resolved(seq, at: now)
            }
            stop(now)
            if let s = seq, let len = outLength(s) {
                autoStart = max(now, s.date.addingTimeInterval(len))
            } else {
                autoStart = now
            }
        }
    }

    // MARK: Drawing

    @ViewBuilder
    private func content(_ p: M.Pose, clock: Double, g: Art.Geo) -> some View {
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                // The shelf band behind the player.
                let band = CGRect(x: 0, y: g.shelfTop, width: g.w, height: g.shelfBottom - g.shelfTop)
                ctx.fill(Path(band), with: .linearGradient(Gradient(colors: [Color(hex: 0xE9E8E4), Color(hex: 0xDCDBD6)]),
                                                           startPoint: CGPoint(x: 0, y: band.minY), endPoint: CGPoint(x: 0, y: band.maxY)))
                ctx.fill(Path(CGRect(x: 0, y: band.maxY - 6, width: g.w, height: 6)), with: .color(Color(hex: 0xCFCEC9)))
                ctx.fill(Path(CGRect(x: 0, y: band.maxY, width: g.w, height: 10)), with: .linearGradient(
                    Gradient(colors: [.black.opacity(0.12), .clear]), startPoint: CGPoint(x: 0, y: band.maxY), endPoint: CGPoint(x: 0, y: band.maxY + 10)))
                Art.player(ctx, g, pose: p, clock: clock, still: reduceMotion)

                let picked = p.tape
                let flying = p.flight > 0.0001 || p.squash > 0
                for i in 0..<Art.tapes.count where i != picked {
                    Art.sleeve(ctx, i, g.cell(i), lift: 0)
                }
                if !flying { cassette(ctx, p, g: g) }
                Art.sleeve(ctx, picked, g.cell(picked), lift: p.lift)
                if flying { cassette(ctx, p, g: g) }
            }
            .frame(width: g.w, height: g.h)
            .contentShape(Rectangle())
            .gesture(SpatialTapGesture().onEnded { value in tap(value.location, g: g) })
            .accessibilityElement()
            .accessibilityLabel(playing ? "Playing \(Art.tapes[p.tape].label)" : "Tape player, no tape")

            HStack(alignment: .firstTextBaseline) {
                Text("Home Tapes").font(.system(size: 17, weight: .semibold)).foregroundStyle(Art.ink)
                Spacer()
                HStack(spacing: 3) {
                    ForEach(0..<3, id: \.self) { _ in Circle().fill(Art.magenta).frame(width: 5, height: 5) }
                }
            }
            .frame(width: g.w - 48)
            .offset(x: 24, y: g.top + 10)
            .allowsHitTesting(false)

            HStack(alignment: .firstTextBaseline) {
                Text("Collections").font(.system(size: 22, weight: .heavy)).tracking(-0.4).foregroundStyle(Art.ink)
                Spacer()
                Text("9 tapes").font(.system(size: 13, weight: .medium)).foregroundStyle(Art.ink.opacity(0.45))
            }
            .frame(width: g.w - 48)
            .offset(x: 24, y: g.shelfBottom + 16)
            .allowsHitTesting(false)

            // A fade so the bottom row tucks under the controls.
            LinearGradient(colors: [Art.page.opacity(0), Art.page.opacity(0.96)], startPoint: .top, endPoint: .bottom)
                .frame(width: g.w, height: 120)
                .offset(y: g.h - 120)
                .allowsHitTesting(false)

            pill(p, g: g)
            transport(p, g: g)
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
    }

    private func cassette(_ ctx: GraphicsContext, _ p: M.Pose, g: Art.Geo) {
        guard p.cassetteAlpha > 0.001 else { return }
        let box = g.cell(p.tape)
        let l = g.cassetteL, t = g.cassetteT
        let k = g.slot.width / l
        let p0 = CGPoint(x: box.midX + p.slide * g.cellW * 0.62, y: box.midY)
        let p2 = CGPoint(x: g.w / 2, y: g.slot.midY - t * k / 2 - 4)
        let p1 = CGPoint(x: (p0.x + p2.x) / 2 + 40, y: min(p0.y, p2.y) - 70)
        let f = p.flight
        let x = (1 - f) * (1 - f) * p0.x + 2 * (1 - f) * f * p1.x + f * f * p2.x
        var y = (1 - f) * (1 - f) * p0.y + 2 * (1 - f) * f * p1.y + f * f * p2.y
        let rot = 90 * (1 - f) + 14 * sin(.pi * f)
        let scale = 1 + (k - 1) * f + 0.12 * sin(.pi * f)
        y += (g.slot.midY - y) * p.squash
        var c = ctx
        c.opacity = p.cassetteAlpha
        // Its shadow, further below while it flies.
        let lift = sin(.pi * f)
        c.drawLayer { l2 in
            l2.addFilter(.blur(radius: 4 + 8 * lift))
            l2.translateBy(x: x, y: y + 4 + 14 * lift)
            l2.rotate(by: .degrees(rot))
            l2.scaleBy(x: scale, y: scale * (1 - 0.9 * p.squash))
            l2.fill(Path(roundedRect: CGRect(x: -l / 2, y: -t / 2, width: l, height: t), cornerRadius: l * 0.05), with: .color(.black.opacity(0.3)))
        }
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(rot))
        c.scaleBy(x: scale, y: scale * (1 - 0.9 * p.squash))
        Art.cassette(c, l: l, t: t, label: Art.tapes[p.tape].label, reels: f * 2 * .pi)
    }

    private func tap(_ point: CGPoint, g: Art.Geo) {
        if g.body.insetBy(dx: -8, dy: -8).contains(point) {
            eject()
            return
        }
        for i in 0..<Art.tapes.count where g.cell(i).contains(point) {
            play(i)
            return
        }
    }

    private func pill(_ p: M.Pose, g: Art.Geo) -> some View {
        Button { play(nextTape) } label: {
            Text("Play something old")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 210, height: 44)
                .background(Capsule().fill(Art.magenta))
                .shadow(color: Art.magenta.opacity(0.35), radius: 12, y: 6)
        }
        .buttonStyle(TapeShelfPlayerPress())
        .opacity(p.pill)
        .offset(x: (g.w - 210) / 2, y: g.pillY + 24 * (1 - p.pill))
        .allowsHitTesting(p.pill > 0.5)
    }

    private func transport(_ p: M.Pose, g: Art.Geo) -> some View {
        let icons = ["eject.fill", "backward.fill", frozen == nil ? "pause.fill" : "play.fill", "forward.fill", "shuffle"]
        let names = ["Eject", "Rewind", frozen == nil ? "Pause" : "Play", "Fast forward", "Shuffle"]
        return HStack(spacing: 0) {
            ForEach(0..<5, id: \.self) { i in
                Button {
                    switch i {
                    case 0: eject()
                    case 2: togglePause()
                    case 4: play(nextTape)
                    default: light.impactOccurred(intensity: 0.5)
                    }
                } label: {
                    Image(systemName: icons[i])
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white.opacity(i == 0 ? 1 : 0.85))
                        .frame(width: 52, height: 44)
                        .background(Circle().fill(.white.opacity(i == 0 ? 0.08 + 0.3 * p.ejectPress : 0)).frame(width: 44, height: 44))
                        .scaleEffect(i == 0 ? 1 - 0.12 * p.ejectPress : 1)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(TapeShelfPlayerPress())
                .accessibilityLabel(names[i])
            }
        }
        .padding(.horizontal, 8)
        .frame(width: g.w - 48, height: 60)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color(hex: 0x141416)))
        .shadow(color: .black.opacity(0.25), radius: 16, y: 8)
        .offset(x: 24, y: g.transportY + (1 - p.transport) * (90 + g.bottom))
        .opacity(min(1, p.transport * 3))
        .allowsHitTesting(p.transport > 0.5)
    }
}

private struct TapeShelfPlayerPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
