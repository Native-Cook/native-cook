import SwiftUI
import UIKit

/*
 Vinyl Swap Scratch — a turntable "now playing" screen you can scratch, and whose whole colour
 changes with the record on the platter.

 Look: a black record (diameter 1.12 × the screen width) cropped by the left edge, centred at
 29 % / 35.5 % of the screen, its label drawn in code (two invented albums: "Slow Satellites" by
 The Amber Rooms, a saffron sunburst; "Glass Harbor" by Noa Lind, cream with teal rings). The
 background is the label's colour, blurred and tinted (ochre to near-black for A, teal for B).
 A chrome tonearm pivots at the top right. Under the record a slim waveform strip (bars that
 scroll with the platter's angle, a fixed playhead), then the title, then three dark pills.

 autoPlay timeline (loop = 10 s, VinylSwapScratchMotion.swift):
   0.0 – 1.5    album A plays (≈ 218 °/s, rounded so the platter turns 5 whole times a loop).
   1.3 – 1.5    a finger lands on the record; 1.5 the platter stops under it in 0.12 s.
   1.62 – 3.16  three baby scratches (back 55°, 75°, 95° and forward again, 0.6 / 0.5 / 0.44 s):
                the waveform scrubs back and forth, bars smear and flare in the accent colour,
                arcs flicker at the rim.
   3.2          release: the motor pulls the platter back up (spring ω 12, ζ 0.45, ~20 % overshoot).
   4.6 – 6.4    "next" is pressed: the arm lifts and swings out (0.4 s), the record slides off to
                the left (0.3 – 0.8 s, ease-in), record B slides in from the right (0.6 s on, spring
                ω 11, ζ 0.62), the colours cross-fade (0.4 – 1.4 s), the title changes (0.45 – 1.05 s),
                the arm swings back and drops (1.3 – 1.8 s). The waveform hushes while the stylus is up.
   7.7 – 9.5    "previous": the same, B back to A.
   10.0         the same frame as 0.0.

 Touch: a finger on the record stops the platter under it (0.12 s), turns it with the finger
 (the angle around the centre) and scrubs the waveform; on release the motor spins it back up.
 Pause stops the platter over 0.8 s, play spins it up; previous/next change the record. 2 s
 after the last touch the screen goes back to album A playing and the loop starts again.
 Reduced motion: nothing spins and nothing plays by itself; changing the record cross-fades in
 0.3 s; the record still turns under a finger.
 */

struct VinylSwapScratch: View {
    var autoPlay = false

    static let loop = VinylSwapScratchMotion.loop
    typealias M = VinylSwapScratchMotion
    typealias Art = VinylSwapScratchArt
    typealias Geo = VinylSwapScratchArt.Geo

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    enum Spin {
        /// The motor pulling the platter from a speed to its own (or to rest when the motor is off).
        case approach(from: Double)
        /// Braking to rest over a duration.
        case brake(from: Double, duration: Double)
    }

    /// The loop's t = 0, and the angle added to the loop's own (so a hand-off keeps the record still).
    @State private var autoStart: Date? = nil
    @State private var angleOffset = 0.0
    // The hand-driven state.
    @State private var album = 0
    @State private var change: (date: Date, from: Int, to: Int)? = nil
    @State private var motorOn = true
    @State private var spin: (date: Date, angle: Double, kind: Spin) = (.distantPast, 0, .approach(from: 0))
    @State private var hand: (pointer: Double, turned: Double, velocity: Double, moved: Date, ticked: Double)? = nil
    @State private var ignoring = false
    @State private var resume: Task<Void, Never>? = nil
    /// Reduced motion: the timeline runs only while something cross-fades or a finger is down.
    @State private var busy = false
    @State private var soft = UIImpactFeedbackGenerator(style: .soft)
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var tick = UISelectionFeedbackGenerator()

    private var motorSpeed: Double { reduceMotion || !motorOn ? 0 : M.omega }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let g = Geo(w: proxy.size.width + insets.leading + insets.trailing,
                        h: proxy.size.height + insets.top + insets.bottom,
                        top: insets.top, bottom: insets.bottom)
            TimelineView(.animation(paused: reduceMotion && !busy)) { timeline in
                let p = pose(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in
                        Art.background(ctx, g, mix: p.mix)
                        for i in [1, 0] where p.discAlpha[i] > 0.001 && abs(p.discX[i]) < 1 {
                            let c = CGPoint(x: g.center.x + p.discX[i] * g.slide, y: g.center.y)
                            Art.disc(ctx, g, album: i, center: c, angle: p.angle, alpha: p.discAlpha[i])
                        }
                        Art.scratchMarks(ctx, g, pose: p)
                        Art.finger(ctx, g, pose: p)
                        Art.arm(ctx, g, out: p.arm, lift: p.lift)
                        Art.waveform(ctx, g, pose: p)
                    }
                    .frame(width: g.w, height: g.h)
                    .contentShape(Rectangle())
                    .gesture(scratch(g))
                    .accessibilityElement()
                    .accessibilityLabel("Now playing \(Art.albums[shownAlbum(p)].title) by \(Art.albums[shownAlbum(p)].artist)")

                    titles(p, g)
                    controls(p, g)
                }
                .frame(width: g.w, height: g.h, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(Color(hex: 0x15110A).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            soft.prepare(); light.prepare(); tick.prepare()
            spin = (.now, 0, .approach(from: motorSpeed))
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    private func shownAlbum(_ p: M.Pose) -> Int { p.titleAlpha[1] > p.titleAlpha[0] ? 1 : 0 }

    // MARK: Time

    private func pose(at date: Date) -> M.Pose {
        if let autoStart, date >= autoStart {
            let t = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: M.loop)
            var p = M.loopPose(t)
            p.angle += angleOffset
            return p
        }
        let (a, v) = platter(at: date)
        var p = M.Pose.rest(album)
        if let change {
            let e = date.timeIntervalSince(change.date)
            if e < M.swapLength { M.swap(&p, e, from: change.from, to: change.to, still: reduceMotion) } else { p = M.Pose.rest(change.to) }
        }
        p.angle = a
        p.speed = v
        p.clock = date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1000)
        return p
    }

    /// The hand-driven platter: angle and speed.
    private func platter(at date: Date) -> (Double, Double) {
        let t = max(0, date.timeIntervalSince(spin.date))
        var (a, v): (Double, Double)
        switch spin.kind {
        case .approach(let from): (a, v) = M.approach(from: from, to: motorSpeed, t)
        case .brake(let from, let duration): (a, v) = M.brake(from: from, duration: duration, t)
        }
        a += spin.angle
        if let hand {
            a += hand.turned
            v += hand.velocity * exp(-max(0, date.timeIntervalSince(hand.moved)) / 0.08)
        }
        return (a, v)
    }

    /// Freezes whatever plays into the hand-driven state.
    private func takeOver(_ now: Date) {
        resume?.cancel()
        if let start = autoStart {
            if now >= start {
                let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: M.loop)
                let p = M.loopPose(t)
                let s = M.loopState(t)
                album = s.album
                change = s.swap.map { (date: now.addingTimeInterval(-(t - $0.start)), from: $0.from, to: $0.to) }
                motorOn = true
                spin = (now, p.angle + angleOffset, .approach(from: p.speed))
            }
            autoStart = nil
        }
        if let c = change, now.timeIntervalSince(c.date) >= M.swapLength {
            album = c.to
            change = nil
        }
        if reduceMotion { markBusy(0.4) }
    }

    private func scratch(_ g: Geo) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                let c = g.center
                let pointer = atan2(value.location.y - c.y, value.location.x - c.x) * 180 / .pi
                if hand == nil && !ignoring {
                    let s = value.startLocation
                    guard hypot(s.x - c.x, s.y - c.y) <= g.r + 8, s.x < g.w * 0.82 || s.y > g.pivot.y + 60 else {
                        ignoring = true
                        return
                    }
                    takeOver(now)
                    let (a, v) = platter(at: now)
                    spin = (now, a, .brake(from: v, duration: M.stopFor))
                    hand = (pointer, 0, 0, now, 0)
                    busy = true
                    soft.impactOccurred(intensity: 0.8)
                    return
                }
                guard var h = hand else { return }
                var d = pointer - h.pointer
                if d > 180 { d -= 360 }
                if d < -180 { d += 360 }
                let dt = max(1.0 / 120, now.timeIntervalSince(h.moved))
                h.velocity = 0.6 * h.velocity * exp(-dt / 0.08) + 0.4 * d / dt
                h.turned += d
                h.pointer = pointer
                h.moved = now
                if abs(h.turned - h.ticked) >= 15 {
                    h.ticked = h.turned
                    tick.selectionChanged()
                }
                hand = h
            }
            .onEnded { _ in
                ignoring = false
                guard hand != nil else { return }
                let now = Date.now
                let (a, _) = platter(at: now)
                hand = nil
                spin = (now, a, .approach(from: 0))
                light.impactOccurred(intensity: 0.5)
                if reduceMotion { markBusy(0.1) } else { busy = false }
                scheduleResume()
            }
    }

    private func togglePlay() {
        let now = Date.now
        takeOver(now)
        let (a, v) = platter(at: now)
        motorOn.toggle()
        spin = (now, a, motorOn ? .approach(from: v) : .brake(from: v, duration: 0.8))
        light.impactOccurred()
        scheduleResume()
    }

    private func skip() {
        let now = Date.now
        takeOver(now)
        guard change == nil else { return }
        change = (now, album, 1 - album)
        light.impactOccurred()
        scheduleResume()
    }

    private func markBusy(_ seconds: Double) {
        busy = true
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            if hand == nil { busy = false }
        }
    }

    /// 2 s after the last touch: back to album A playing, then the loop from its start.
    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled, hand == nil else { return }
            let now = Date.now
            takeOver(now)
            var ready = now
            if change == nil && album != 0 {
                change = (now, album, 0)
                ready = now.addingTimeInterval(M.swapLength)
            } else if let c = change {
                ready = c.date.addingTimeInterval(M.swapLength)
            }
            if !motorOn {
                let (a, v) = platter(at: now)
                motorOn = true
                spin = (now, a, .approach(from: v))
            }
            // Let the motor settle (1 s after its last push) so the hand-off keeps the speed.
            ready = max(ready, spin.date.addingTimeInterval(1))
            angleOffset = platter(at: ready).0
            autoStart = ready
        }
    }

    // MARK: Text and controls

    private func titles(_ p: M.Pose, _ g: Geo) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<2, id: \.self) { i in
                VStack(alignment: .leading, spacing: 4) {
                    Text(Art.albums[i].title)
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white)
                    Text(Art.albums[i].artist)
                        .font(.system(size: 15, weight: .regular))
                        .foregroundStyle(.white.opacity(0.62))
                }
                .lineLimit(1)
                .opacity(p.titleAlpha[i])
                .offset(x: p.titleX[i])
            }
            Text("33⅓ RPM")
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .foregroundStyle(.white.opacity(0.55))
                .padding(.horizontal, 10)
                .frame(height: 24)
                .overlay(Capsule().strokeBorder(.white.opacity(0.18), lineWidth: 1))
                .frame(width: g.w - 48, alignment: .trailing)
                .offset(y: 6)
        }
        .frame(width: g.w - 48, alignment: .topLeading)
        .offset(x: 24, y: g.titleTop)
        .allowsHitTesting(false)
    }

    private func controls(_ p: M.Pose, _ g: Geo) -> some View {
        let playing = motorOn || autoStart != nil
        return HStack(alignment: .top, spacing: 12) {
            pill(icon: playing ? "pause.fill" : "play.fill", label: playing ? "PAUSE" : "PLAY", width: 72, press: p.press[0], action: togglePlay)
            Spacer(minLength: 0)
            pill(icon: "backward.end.fill", label: "PREV", width: 60, press: p.press[1], action: skip)
            pill(icon: "forward.end.fill", label: "NEXT", width: 60, press: p.press[2], action: skip)
        }
        .frame(width: g.w - 48)
        .offset(x: 24, y: g.controlsTop)
    }

    private func pill(icon: String, label: String, width: Double, press: Double, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.92))
                    .frame(width: width, height: 36)
                    .background(Capsule().fill(.black.opacity(0.34 - 0.14 * press)))
                    .background(Capsule().fill(.white.opacity(0.22 * press)))
                    .overlay(Capsule().strokeBorder(.white.opacity(0.1 + 0.2 * press), lineWidth: 1))
                    .scaleEffect(1 - 0.08 * press)
                Text(label)
                    .font(.system(size: 9, weight: .semibold, design: .monospaced))
                    .tracking(1.2)
                    .foregroundStyle(.white.opacity(0.55))
            }
        }
        .buttonStyle(VinylSwapScratchPress())
        .accessibilityLabel(label.capitalized)
    }
}

private struct VinylSwapScratchPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
