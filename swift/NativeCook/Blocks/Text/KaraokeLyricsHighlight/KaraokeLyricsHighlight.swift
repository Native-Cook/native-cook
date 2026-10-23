import SwiftUI
import UIKit

/*
 Karaoke Lyrics Highlight — a "now playing" lyrics screen that sings its chorus word by word.

 Look: an invented song ("Paper Satellites" by Lumen Fair) on a drifting violet–magenta field.
 Big left-aligned lines in Poppins Bold 32 pt (rows 38 pt, 22 pt between lines) fill the screen;
 the line being sung sits with its top at 30 % of the screen height. A header (art, title,
 artist, a small live waveform) sits at the top and a slim scrubber with times at the bottom;
 the lyrics fade out under both.

 Per line, by its distance d from the focus (negative: already sung):
   sung ink   white × 1 at the focus, down to 0.25 one line above;
   unsung ink white × 0.35 at the focus, 0.25 one line below, 0.15 two below;
   blur       2.5 pt one line above, 1.5 pt per line below (up to 3 pt).
 A word fills left to right with white: a soft 16 pt edge, and a glow (blur 5) on the 28 pt
 behind the edge. A word being sung lifts 2 pt and grows 3 % (in 0.12 s, out 0.2 s); the held
 "Ohhh," fills over 0.95 s and its letters wave (2.5 pt, 1.6 Hz, 0.7 rad apart).

 autoPlay timeline (loop = 10 s, see KaraokeLyricsHighlightModel.swift for the word times):
   0.0 / 2.5 / 5.0 / 7.5   a chorus line starts; its words fill over the next ~2 s.
   +1.95 … +2.5            the list scrolls one line up (ease-out-back, small overshoot).
   9.5 … 10.0              the playhead glides from 1:21 back to 1:12 (the chorus loops).
   10.0                    one chorus further down: the same frame as t = 0.
 The background blobs drift once per loop and the header waveform's bars move a whole number
 of times per loop.

 Touch: dragging scrolls the lyrics freely; the fill pauses and the lines un-blur while the
 finger is down (0.25 s), and blur again after release (0.4 s). Tapping a line jumps the song
 to it: the loop carries on from that line, the list gliding there (0.55 s, ease-out-back) and
 its fill restarting. 2 s after a drag, the list eases back to the chorus's first line over
 0.6 s (the fill rewinds with it) and the loop starts again.
 Reduced motion: no loop, no drift, no lift or wave; the first line shows fully sung. Dragging
 still scrolls and a tap moves the focus to a line, sung at once.
 */

struct KaraokeLyricsHighlight: View {
    var autoPlay = false

    static let loop = KaraokeLyricsHighlightTimeline.loop
    typealias TL = KaraokeLyricsHighlightTimeline
    typealias L = KaraokeLyricsHighlightLayout

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The loop's t = 0, and the absolute line it starts on.
    @State private var autoStart: Date? = nil
    @State private var origin = 0
    /// A tap's glide: the difference in lines between where the list was and the tapped line.
    @State private var correction: (date: Date, lines: Double)? = nil
    /// The manual state: scroll offset in points, the line being sung and how far, the playhead.
    @State private var manualOffset = 0.0
    @State private var frozen: (line: Int, tau: Double, playhead: Double) = (0, 0, KaraokeLyricsHighlightSong.chorusAt)
    @State private var returning: (date: Date, from: Double, playhead: Double)? = nil
    @State private var dragStartOffset: Double? = nil
    @State private var moved = false
    @State private var pressedAt: Date? = nil
    @State private var releasedAt: Date? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    /// What the screen shows at a moment.
    struct Shown {
        var scroll: Double
        /// The line being sung and how far into its slot; while returning, blended towards `restLine`.
        var line: Int
        var tau: Double
        var restLine: Int
        var rest: Double
        var playhead: Double
        /// Loop time for the drift and the waveform.
        var clock: Double
        /// 1 normally, 0 while a finger is dragging the lyrics.
        var blurScale: Double
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = L(width: size.width)
            TimelineView(.animation(paused: reduceMotion && dragStartOffset == nil)) { timeline in
                let shown = shown(at: timeline.date, layout: layout)
                ZStack(alignment: .topLeading) {
                    background(size: size, clock: shown.clock)
                    lyrics(layout: layout, size: size, insets: insets, shown: shown)
                        .contentShape(Rectangle())
                        .gesture(drag(layout: layout, size: size))
                    header(size: size, insets: insets, clock: shown.clock)
                    scrubber(size: size, insets: insets, playhead: shown.playhead)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .onAppear {
            haptic.prepare()
            frozen = (0, reduceMotion ? TL.slot : 0, KaraokeLyricsHighlightSong.chorusAt)
            manualOffset = 0
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Time

    private func shown(at date: Date, layout: L) -> Shown {
        var blurScale = 1.0
        if let pressedAt, moved {
            blurScale = 1 - TL.smoothstep(0, 0.25, date.timeIntervalSince(pressedAt))
            if let releasedAt { blurScale = max(blurScale, TL.smoothstep(0, 0.4, date.timeIntervalSince(releasedAt))) }
        }
        if let autoStart, date >= autoStart, !reduceMotion {
            let t = date.timeIntervalSince(autoStart)
            let cycles = floor(t / Self.loop)
            let e = t - cycles * Self.loop
            let base = origin + 4 * Int(cycles)
            var s = Double(base) + TL.scroll(e)
            if let c = correction {
                let u = date.timeIntervalSince(c.date) / 0.55
                if u < 1 { s += c.lines * (1 - TL.easeOutBack(u)) }
            }
            let (k, tau) = TL.singing(e)
            return Shown(scroll: s, line: base + k, tau: tau, restLine: base + k, rest: 0, playhead: TL.playhead(e),
                         clock: e, blurScale: blurScale)
        }
        if let r = returning {
            let u = TL.easeInOut(date.timeIntervalSince(r.date) / 0.6)
            return Shown(scroll: r.from + (Double(origin) - r.from) * u, line: frozen.line, tau: frozen.tau,
                         restLine: origin, rest: u,
                         playhead: r.playhead + (KaraokeLyricsHighlightSong.chorusAt - r.playhead) * u,
                         clock: 0, blurScale: blurScale)
        }
        return Shown(scroll: layout.position(manualOffset), line: frozen.line, tau: frozen.tau, restLine: frozen.line, rest: 0,
                     playhead: frozen.playhead, clock: 0, blurScale: blurScale)
    }

    /// How much word w of absolute line i is filled.
    private func fill(_ i: Int, _ w: KaraokeLyricsHighlightWord, _ s: Shown) -> Double {
        func base(_ line: Int, _ tau: Double) -> Double {
            i < line ? 1 : i > line ? 0 : TL.fill(w, tau: tau)
        }
        let a = base(s.line, s.tau)
        guard s.rest > 0 else { return a }
        return a + (base(s.restLine, 0) - a) * s.rest
    }

    /// Freezes whatever plays into the manual state.
    private func takeOver(at now: Date, layout: L) {
        resume?.cancel()
        let s = shown(at: now, layout: layout)
        manualOffset = layout.offset(s.scroll)
        frozen = (s.rest >= 1 ? s.restLine : s.line, s.rest >= 1 ? 0 : s.tau, s.playhead)
        autoStart = nil
        returning = nil
        correction = nil
    }

    private func drag(layout: L, size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if dragStartOffset == nil {
                    takeOver(at: now, layout: layout)
                    dragStartOffset = manualOffset
                    moved = false
                    pressedAt = nil
                    releasedAt = nil
                }
                if !moved, hypot(value.translation.width, value.translation.height) > 6 {
                    moved = true
                    pressedAt = now
                    haptic.impactOccurred(intensity: 0.4)
                }
                if moved, let start = dragStartOffset { manualOffset = start - value.translation.height }
            }
            .onEnded { value in
                let now = Date.now
                dragStartOffset = nil
                if moved {
                    releasedAt = now
                    scheduleResume(layout: layout)
                } else {
                    tap(at: value.location, now: now, layout: layout, size: size)
                }
            }
    }

    /// Jumps the song to the line under the finger; the loop carries on from it.
    private func tap(at point: CGPoint, now: Date, layout: L, size: CGSize) {
        let s = layout.position(manualOffset)
        let focus = size.height * 0.3
        let first = Int(floor(s)) - 4
        var hit: Int? = nil
        for i in first...(first + 12) {
            let top = focus + layout.top(i) - layout.offset(s)
            if point.y >= top - L.gap / 2 && point.y < top + layout.line(i).height + L.gap / 2 { hit = i }
        }
        guard let i = hit else {
            scheduleResume(layout: layout)
            return
        }
        haptic.impactOccurred(intensity: 0.8)
        let k = ((i % 4) + 4) % 4
        if reduceMotion {
            manualOffset = layout.offset(Double(i))
            frozen = (i, TL.slot, frozen.playhead)
            return
        }
        origin = i - k
        autoStart = now.addingTimeInterval(-Double(k) * TL.slot)
        correction = (now, s - Double(i))
        pressedAt = nil
    }

    private func scheduleResume(layout: L) {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Date.now
            let s = layout.position(manualOffset)
            // The nearest first line of the chorus.
            origin = Int((s / 4).rounded()) * 4
            returning = (now, s, frozen.playhead)
            autoStart = now.addingTimeInterval(0.6)
            correction = nil
        }
    }

    // MARK: Drawing

    private func background(size: CGSize, clock: Double) -> some View {
        let a = 2 * Double.pi * clock / Self.loop
        let still = reduceMotion
        return Canvas { ctx, sz in
            ctx.fill(Path(CGRect(origin: .zero, size: sz)), with: .color(Color(red: 0.071, green: 0.024, blue: 0.165)))
            func blob(_ c: CGPoint, _ r: Double, _ color: Color) {
                ctx.fill(Path(CGRect(origin: .zero, size: sz)),
                         with: .radialGradient(Gradient(colors: [color, color.opacity(0)]), center: c, startRadius: 0, endRadius: r))
            }
            let w = sz.width, h = sz.height
            blob(CGPoint(x: w * (0.25 + (still ? 0 : 0.08 * sin(a))), y: h * (0.22 + (still ? 0 : 0.05 * cos(a)))), w * 0.95,
                 Color(red: 0.835, green: 0.227, blue: 0.616).opacity(0.75))
            blob(CGPoint(x: w * (0.8 + (still ? 0 : 0.07 * cos(a))), y: h * (0.7 + (still ? 0 : 0.06 * sin(a)))), w * 1.0,
                 Color(red: 0.353, green: 0.122, blue: 0.659).opacity(0.9))
            blob(CGPoint(x: w * (0.92 - (still ? 0 : 0.05 * sin(a))), y: h * 0.12), w * 0.6,
                 Color(red: 1.0, green: 0.478, blue: 0.349).opacity(0.35))
            ctx.fill(Path(CGRect(origin: .zero, size: sz)),
                     with: .linearGradient(Gradient(colors: [.black.opacity(0.1), .black.opacity(0.45)]),
                                           startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
    }

    private func lyrics(layout: L, size: CGSize, insets: EdgeInsets, shown s: Shown) -> some View {
        let focus = size.height * 0.3
        let headerBottom = insets.top + 12 + 48
        let scrubTop = size.height - max(insets.bottom, 20) - 44
        let reduce = reduceMotion
        return Canvas { ctx, _ in
            let first = Int(floor(s.scroll)) - 4
            let fontSize = CGFloat(L.size)
            let base = ctx.resolve(Text("Hg").font(KaraokeLyricsHighlightFont.font(size: fontSize)))
            let ascent = base.firstBaseline(in: CGSize(width: 400, height: 200))
            for i in first...(first + 12) {
                let line = layout.line(i)
                let top = focus + layout.top(i) - layout.offset(s.scroll)
                if top > size.height + 20 || top + line.height < -20 { continue }
                let d = Double(i) - s.scroll
                let blur = TL.blur(d) * s.blurScale
                let sungA = TL.sungAlpha(d), unsungA = TL.unsungAlpha(d)
                ctx.drawLayer { lc in
                    if blur > 0.1 { lc.addFilter(.blur(radius: blur)) }
                    for pw in line.words {
                        let f = fill(i, pw.word, s)
                        let lift = (reduce || s.rest > 0 || i != s.line) ? 0 : TL.lift(pw.word, tau: s.tau)
                        // The caps' middle sits on the middle of the row.
                        let baseline = top + Double(pw.row) * L.rowHeight + L.rowHeight / 2 + 0.35 * L.size - 2 * lift
                        let rect = CGRect(x: pw.x - 4, y: baseline - L.size * 1.1, width: pw.width + 8, height: L.size * 1.5)
                        var wc = lc
                        // Grow 3 % about the word's centre.
                        let grow = 1 + 0.03 * lift
                        wc.translateBy(x: rect.midX, y: baseline - 0.35 * L.size)
                        wc.scaleBy(x: grow, y: grow)
                        wc.translateBy(x: -rect.midX, y: -(baseline - 0.35 * L.size))
                        let draw: (inout GraphicsContext, Color) -> Void = { g, color in
                            if pw.word.held {
                                for (li, (ch, ox)) in pw.letters.enumerated() {
                                    let wave = 2.5 * sin(2 * .pi * 1.6 * s.tau - Double(li) * 0.7) * lift
                                    let t = g.resolve(Text(String(ch)).font(KaraokeLyricsHighlightFont.font(size: fontSize)).foregroundColor(color))
                                    g.draw(t, at: CGPoint(x: pw.x + ox, y: baseline - ascent - wave), anchor: .topLeading)
                                }
                            } else {
                                let t = g.resolve(Text(pw.word.text).font(KaraokeLyricsHighlightFont.font(size: fontSize)).foregroundColor(color))
                                g.draw(t, at: CGPoint(x: pw.x, y: baseline - ascent), anchor: .topLeading)
                            }
                        }
                        if f < 1 { draw(&wc, .white.opacity(unsungA)) }
                        if f >= 1 {
                            draw(&wc, .white.opacity(sungA))
                        } else if f > 0 {
                            let edge = pw.x + f * pw.width
                            wc.drawLayer { sc in
                                sc.clipToLayer { m in
                                    m.fill(Path(rect), with: .linearGradient(
                                        Gradient(stops: [.init(color: .white, location: 0), .init(color: .white, location: 0.5), .init(color: .clear, location: 1)]),
                                        startPoint: CGPoint(x: edge - 16, y: 0), endPoint: CGPoint(x: edge + 8, y: 0)))
                                }
                                draw(&sc, .white.opacity(sungA))
                            }
                            // The glowing leading edge.
                            wc.drawLayer { gc in
                                gc.addFilter(.blur(radius: 5))
                                gc.clipToLayer { m in
                                    m.fill(Path(rect.insetBy(dx: -10, dy: -10)), with: .linearGradient(
                                        Gradient(stops: [.init(color: .clear, location: 0), .init(color: .white, location: 0.85), .init(color: .clear, location: 1)]),
                                        startPoint: CGPoint(x: edge - 28, y: 0), endPoint: CGPoint(x: edge + 4, y: 0)))
                                }
                                draw(&gc, .white.opacity(0.9 * sungA))
                            }
                        }
                    }
                }
            }
        }
        .frame(width: size.width, height: size.height)
        // The lyrics fade out under the header and above the scrubber.
        .mask(
            LinearGradient(stops: [
                .init(color: .clear, location: headerBottom / size.height),
                .init(color: .black, location: (headerBottom + 56) / size.height),
                .init(color: .black, location: (scrubTop - 64) / size.height),
                .init(color: .clear, location: (scrubTop - 12) / size.height),
            ], startPoint: .top, endPoint: .bottom)
        )
        .accessibilityElement()
        .accessibilityLabel(KaraokeLyricsHighlightSong.chorus.map { $0.map(\.text).joined(separator: " ") }.joined(separator: ", "))
    }

    private func header(size: CGSize, insets: EdgeInsets, clock: Double) -> some View {
        HStack(spacing: 12) {
            // Album art: a gradient square with a small orbit.
            ZStack {
                LinearGradient(colors: [Color(red: 1.0, green: 0.478, blue: 0.349), Color(red: 0.835, green: 0.227, blue: 0.616),
                                        Color(red: 0.169, green: 0.063, blue: 0.333)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
                Circle().strokeBorder(.white.opacity(0.7), lineWidth: 1.5).frame(width: 26, height: 26)
                Circle().fill(.white).frame(width: 6, height: 6).offset(x: 9, y: -9)
            }
            .frame(width: 48, height: 48)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(KaraokeLyricsHighlightSong.title)
                    .font(KaraokeLyricsHighlightFont.font(size: 17))
                    .foregroundStyle(.white)
                Text(KaraokeLyricsHighlightSong.artist)
                    .font(.system(size: 14))
                    .foregroundStyle(.white.opacity(0.6))
            }
            .lineLimit(1)
            Spacer(minLength: 8)
            // A small live waveform: five bars, each moving a whole number of times per loop.
            HStack(alignment: .center, spacing: 3) {
                ForEach(0..<5, id: \.self) { b in
                    let cycles = [3.0, 5, 4, 6, 3][b]
                    let h = reduceMotion ? 8 : 6 + 12 * (0.5 + 0.5 * sin(2 * .pi * cycles * clock / Self.loop + Double(b) * 1.3))
                    Capsule().fill(.white.opacity(0.85)).frame(width: 3, height: h)
                }
            }
            .frame(height: 18)
        }
        .padding(.horizontal, 24)
        .frame(width: size.width, height: 48)
        .offset(y: insets.top + 12)
        .allowsHitTesting(false)
    }

    private func scrubber(size: CGSize, insets: EdgeInsets, playhead: Double) -> some View {
        let y = size.height - max(insets.bottom, 20) - 44
        let w = size.width - 48
        let len = KaraokeLyricsHighlightSong.length
        let p = playhead / len
        let a = KaraokeLyricsHighlightSong.chorusAt / len
        let b = (KaraokeLyricsHighlightSong.chorusAt + Self.loop - TL.rewind) / len
        return ZStack(alignment: .topLeading) {
            Capsule().fill(.white.opacity(0.2)).frame(width: w, height: 4)
            Capsule().fill(.white.opacity(0.9)).frame(width: w * p, height: 4)
            // The chorus being looped, marked above the track.
            Capsule().fill(.white.opacity(0.45)).frame(width: w * (b - a), height: 2).offset(x: w * a, y: -6)
            Circle().fill(.white).frame(width: 12, height: 12).offset(x: w * p - 6, y: -4)
            HStack {
                Text(Self.time(playhead))
                Spacer()
                Text("-" + Self.time(len - playhead))
            }
            .font(.system(size: 12, weight: .medium).monospacedDigit())
            .foregroundStyle(.white.opacity(0.6))
            .frame(width: w)
            .offset(y: 14)
        }
        .frame(width: w, alignment: .topLeading)
        .offset(x: 24, y: y)
        .allowsHitTesting(false)
    }

    static func time(_ seconds: Double) -> String {
        let s = Int(seconds)
        return String(format: "%d:%02d", s / 60, s % 60)
    }
}
