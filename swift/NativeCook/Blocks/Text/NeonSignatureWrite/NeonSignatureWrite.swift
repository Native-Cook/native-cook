import SwiftUI

/*
 Neon Signature Write: a script word on a dark wall that writes itself in neon.

 Timeline of the self-playing loop (`NeonSignatureWrite.loop` = 9.6 s, three segments of 3.2 s):
 segment k opens with word k lit, the resting state.
   0.00–0.80  word k is lit; its glow breathes once (+10 %).
   0.80–1.20  word k switches off: it flickers twice, then the tube cools to black.
   1.20–1.30  dark.
   1.30–2.80  word k + 1 is written (the "write" clock τ runs 0 → 1.5 s). Three tubes trace the outline
              of every letter: mint first, rose 0.18 s later, the word's own colour 0.36 s later, each over
              1.1 s. Within a tube, a contour starts later the further right it sits (up to 50 % of the
              tube's run) and takes 50 % of it. The mint and rose heads burn white-hot and throw sparks.
   2.80–3.20  ignite (τ 1.5 → 1.9): the letters fill with the word's gradient after two flickers while the
              tubes fade; the glow overshoots by 45 % and settles.
 Segment 3 writes word 0 again, so the period ends exactly where it began.

 Touch: dragging across the sign rewrites the shown word with the pen under the finger (left edge of the
 word = nothing, right edge = every tube closed); letting go finishes it and lights it. A tap switches the
 sign off and writes the next word. 2 s after the last touch the sign goes back to the first word, lit,
 and the loop starts over. Reduced motion: the first word is shown lit and still; a tap cross-fades to
 the next one.
*/
struct NeonSignatureWrite: View {
    var words = NeonSignatureWriteWord.samples
    /// Writes the words one after another by itself, every `loop` seconds round.
    var autoPlay = false
    var caption = "Open late"
    var hint = "Drag across the sign to write it"

    static let loop = M.segment * 3

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var geometry = NeonSignatureWriteGeometry()
    /// When the self-playing loop was at 0.
    @State private var loopStart = Date.now
    /// What a touch is playing; nil while the loop (or the still first word) is shown.
    @State private var manual: Manual?
    @State private var touches = 0
    @State private var dragging = false
    @State private var scrubbing = false
    @State private var grabbed = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var igniteHaptic = UIImpactFeedbackGenerator(style: .soft)

    enum M {
        static let segment = 3.2
        static let hold = 0.8
        static let off = 0.4
        static let dark = 0.1
        static let write = 1.5
        static let ignite = 0.4
        /// τ when the sign is fully lit.
        static let lit = write + ignite
        /// The three tubes: when each starts on τ, and how long it runs.
        static let lags = [0.0, 0.18, 0.36]
        static let run = 1.1
        static let stagger = 0.5
        static let span = 0.5
        /// τ when the last tube closes: the right edge of a drag.
        static let closed = 0.36 + run
        static let idle = 2.0
        static let fade = 0.25
    }

    /// What the sign shows at one moment.
    struct Look {
        var word: Int
        /// The write clock: 0…1.5 writing, 1.5…1.9 igniting, 1.9 lit.
        var tau: Double
        /// 0 on, 1 switched off.
        var off = 0.0
        /// Extra glow while lit.
        var breath = 0.0
        /// The whole sign's opacity (the reduced-motion cross-fade).
        var alpha = 1.0
    }

    enum Manual {
        case scrub(word: Int, tau: Double)
        case play(word: Int, from: Double, start: Date)
        /// Switch `from` off, then write `to`.
        case next(from: Int, to: Int, start: Date)
        /// Reduced motion: cross-fade from one lit word to another.
        case fade(from: Int, to: Int, start: Date)
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: paused)) { timeline in
                let look = look(at: timeline.date)
                ZStack {
                    Canvas { context, size in
                        draw(&context, size: size, look: look)
                    }
                    .ignoresSafeArea()
                    .accessibilityLabel(words[look.word].text)
                    .accessibilityAddTraits(.isImage)

                    chrome(look: look)
                }
                .contentShape(Rectangle())
                .gesture(drag(screen: screen, top: insets.top))
            }
        }
        .background(Palette.wall.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            loopStart = .now
            tapHaptic.prepare()
        }
        .task(id: touches) { await settle() }
    }

    private var paused: Bool {
        manual == nil && (reduceMotion || !autoPlay)
    }

    // MARK: Timeline

    private func look(at date: Date) -> Look {
        let n = words.count
        if let manual {
            switch manual {
            case let .scrub(word, tau):
                return Look(word: word, tau: tau)
            case let .play(word, from, start):
                return Look(word: word, tau: min(from + date.timeIntervalSince(start), M.lit))
            case let .next(from, to, start):
                return Self.switchOver(from: from, to: to, e: date.timeIntervalSince(start))
            case let .fade(from, to, start):
                let e = date.timeIntervalSince(start)
                if e < M.fade { return Look(word: from, tau: M.lit, alpha: 1 - e / M.fade) }
                return Look(word: to, tau: M.lit, alpha: min((e - M.fade) / M.fade, 1))
            }
        }
        guard autoPlay, !reduceMotion else { return Look(word: 0, tau: M.lit) }
        let e = date.timeIntervalSince(loopStart).truncatingRemainder(dividingBy: Self.loop)
        let k = Int(e / M.segment) % n
        let l = e - Double(k) * M.segment
        if l < M.hold { return Look(word: k, tau: M.lit, breath: sin(.pi * l / M.hold)) }
        return Self.switchOver(from: k, to: (k + 1) % n, e: l - M.hold)
    }

    /// `from` switches off, a beat of dark, then `to` is written and lit; `e` counts from the switch-off.
    private static func switchOver(from: Int, to: Int, e: Double) -> Look {
        if e < M.off { return Look(word: from, tau: M.lit, off: e / M.off) }
        if e < M.off + M.dark { return Look(word: to, tau: 0) }
        return Look(word: to, tau: min(e - M.off - M.dark, M.lit))
    }

    private static let switchOverLength = M.off + M.dark + M.lit

    // MARK: Touch

    private func drag(screen: CGSize, top: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { value in
                if !dragging {
                    dragging = true
                    grabbed = look(at: .now).word
                    touches += 1
                }
                guard !reduceMotion else { return }
                let moved = hypot(value.translation.width, value.translation.height) > 8
                guard moved || scrubbing else { return }
                if !scrubbing {
                    scrubbing = true
                    tapHaptic.impactOccurred()
                    igniteHaptic.prepare()
                }
                let words = geometry.words(for: words.map(\.text), in: screen)
                let box = words[grabbed].box
                let fraction = min(max((value.location.x - box.minX) / box.width, 0), 1)
                manual = .scrub(word: grabbed, tau: fraction * M.closed)
            }
            .onEnded { _ in
                defer {
                    dragging = false
                    scrubbing = false
                    touches += 1
                }
                let now = Date.now
                if reduceMotion {
                    let from = look(at: now).word
                    manual = .fade(from: from, to: (from + 1) % words.count, start: now)
                    tapHaptic.impactOccurred()
                    return
                }
                if scrubbing, case let .scrub(word, tau) = manual {
                    manual = .play(word: word, from: tau, start: now)
                    igniteSoon(after: M.write - tau)
                } else {
                    let from = look(at: now).word
                    manual = .next(from: from, to: (from + 1) % words.count, start: now)
                    tapHaptic.impactOccurred()
                    igniteSoon(after: M.off + M.dark + M.write)
                }
            }
    }

    private func igniteSoon(after delay: Double) {
        let mark = touches + 1
        DispatchQueue.main.asyncAfter(deadline: .now() + max(delay, 0)) {
            if touches == mark, !dragging { igniteHaptic.impactOccurred() }
        }
    }

    /// 2 s after the last touch, once what it started has finished, the sign returns to the first word
    /// and the loop starts over from its resting frame.
    private func settle() async {
        guard touches > 0, !dragging else { return }
        try? await Task.sleep(for: .seconds(M.idle))
        guard !Task.isCancelled, !dragging else { return }
        while !Task.isCancelled, let m = manual, !finished(m, at: .now) {
            try? await Task.sleep(for: .seconds(0.05))
        }
        guard !Task.isCancelled else { return }
        let current = look(at: .now).word
        if current != 0 {
            let start = Date.now
            manual = reduceMotion ? .fade(from: current, to: 0, start: start) : .next(from: current, to: 0, start: start)
            try? await Task.sleep(for: .seconds(reduceMotion ? M.fade * 2 : Self.switchOverLength))
            guard !Task.isCancelled else { return }
        }
        loopStart = .now
        manual = nil
    }

    private func finished(_ manual: Manual, at date: Date) -> Bool {
        switch manual {
        case .scrub: return false
        case let .play(_, from, start): return from + date.timeIntervalSince(start) >= M.lit
        case let .next(_, _, start): return date.timeIntervalSince(start) >= Self.switchOverLength
        case let .fade(_, _, start): return date.timeIntervalSince(start) >= M.fade * 2
        }
    }

    // MARK: Drawing

    private enum Palette {
        static let wall = Color(hex: 0x060509)
        static let tubes = [Color(hex: 0x3dffc4), Color(hex: 0xff6fae)]
        static let ink = Color.white
    }

    /// Fill brightness while igniting: two flickers, then on.
    private static let igniteKeys: [(Double, Double)] = [(0, 0), (0.08, 0.85), (0.16, 0.15), (0.26, 0.95), (0.34, 0.4), (0.5, 1), (1, 1)]
    /// Brightness while switching off: dips and catches twice, then cools.
    private static let offKeys: [(Double, Double)] = [(0, 1), (0.1, 0.25), (0.18, 0.9), (0.28, 0.15), (0.36, 0.7), (0.55, 0.35), (1, 0)]

    static func keyed(_ keys: [(Double, Double)], _ x: Double) -> Double {
        guard x > keys[0].0 else { return keys[0].1 }
        for i in 1..<keys.count where x <= keys[i].0 {
            let (x0, y0) = keys[i - 1], (x1, y1) = keys[i]
            return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        }
        return keys[keys.count - 1].1
    }

    private func draw(_ context: inout GraphicsContext, size: CGSize, look: Look) {
        let all = geometry.words(for: words.map(\.text), in: size)
        guard look.word < all.count else { return }
        let shape = all[look.word]
        let colours = words[look.word]
        let ignite = min(max((look.tau - M.write) / M.ignite, 0), 1)
        let fill = (look.tau >= M.write ? Self.keyed(Self.igniteKeys, ignite) : 0) * Self.keyed(Self.offKeys, look.off) * look.alpha
        let gain = 1 + 0.45 * (ignite > 0.4 && ignite < 1 ? sin(.pi * (ignite - 0.4) / 0.6) : 0) + 0.1 * look.breath
        let tubes = (1 - ignite) * look.alpha
        let writing = min(look.tau / M.write, 1) * tubes

        // Light thrown on the wall.
        let light = max(fill * gain, writing * 0.55)
        if light > 0.002 {
            let centre = CGPoint(x: size.width / 2, y: size.height * 0.45)
            let r = size.width * 0.95
            var wall = context
            wall.opacity = light
            wall.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .radialGradient(
                    Gradient(colors: [colours.glow.opacity(0.2), colours.glow.opacity(0.06), .clear]),
                    center: centre, startRadius: 0, endRadius: r
                )
            )
        }

        if tubes > 0.002, look.tau > 0 { drawTubes(&context, shape: shape, colours: colours, tau: min(look.tau, M.write), alpha: tubes) }
        if fill > 0.002 { drawFill(&context, shape: shape, colours: colours, level: fill, gain: gain) }
    }

    private func drawTubes(_ context: inout GraphicsContext, shape: NeonSignatureWriteGeometry.Word, colours: NeonSignatureWriteWord, tau: Double, alpha: Double) {
        var heads = Path()
        var hot = Path()
        var sparks: [(CGPoint, CGPoint, Double, Double, Bool)] = []
        for (layer, lag) in M.lags.enumerated() {
            let run = min(max((tau - lag) / M.run, 0), 1)
            guard run > 0 else { continue }
            let colour = layer < 2 ? Palette.tubes[layer] : colours.glow
            var tube = Path()
            for contour in shape.contours {
                let p = min(max((run - M.stagger * contour.delay) / M.span, 0), 1)
                guard p > 0 else { continue }
                let piece = p >= 1 ? contour.path : contour.path.trimmedPath(from: 0, to: p)
                tube.addPath(piece)
                guard layer < 2 else { continue }
                if p < 1, let head = piece.currentPoint {
                    heads.addEllipse(in: CGRect(x: head.x - 1.6, y: head.y - 1.6, width: 3.2, height: 3.2))
                    hot.addPath(contour.path.trimmedPath(from: max(p - 0.07, 0), to: p))
                }
                for spark in contour.sparks where spark.layer == layer && p >= spark.at {
                    let age = (p - spark.at) * M.span * M.run
                    guard age < spark.life else { continue }
                    let x = spark.origin.x + spark.velocity.dx * age
                    let y = spark.origin.y + spark.velocity.dy * age + 0.5 * 260 * age * age
                    let vx = spark.velocity.dx, vy = spark.velocity.dy + 260 * age
                    let speed = max(hypot(vx, vy), 1)
                    let tail = CGPoint(x: x - vx / speed * spark.length, y: y - vy / speed * spark.length)
                    sparks.append((CGPoint(x: x, y: y), tail, 1 - age / spark.life, spark.width, spark.white))
                }
            }
            context.drawLayer { glow in
                glow.addFilter(.blur(radius: 5))
                glow.stroke(tube, with: .color(colour.opacity(0.6 * alpha)), lineWidth: 3.5)
            }
            context.stroke(tube, with: .color(colour.opacity(alpha)), style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
            context.stroke(tube, with: .color(.white.opacity(0.4 * alpha)), lineWidth: 0.6)
        }
        // The pen heads: white-hot ends with a halo, and the sparks they throw.
        context.drawLayer { glow in
            glow.addFilter(.blur(radius: 5))
            glow.stroke(hot, with: .color(.white.opacity(0.9 * alpha)), lineWidth: 4)
            glow.fill(heads, with: .color(Color(hex: 0xd8fff4).opacity(alpha)))
        }
        context.stroke(hot, with: .color(.white.opacity(alpha)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
        context.fill(heads, with: .color(.white.opacity(alpha)))
        for (head, tail, life, width, white) in sparks {
            var line = Path()
            line.move(to: tail)
            line.addLine(to: head)
            let colour = white ? Color.white : Color(hex: 0x9ffcff)
            context.stroke(line, with: .color(colour.opacity(alpha * life)), style: StrokeStyle(lineWidth: width, lineCap: .round))
        }
    }

    private func drawFill(_ context: inout GraphicsContext, shape: NeonSignatureWriteGeometry.Word, colours: NeonSignatureWriteWord, level: Double, gain: Double) {
        let left = CGPoint(x: shape.box.minX, y: shape.box.midY).applying(shape.transform)
        let right = CGPoint(x: shape.box.maxX, y: shape.box.midY).applying(shape.transform)
        let tube = GraphicsContext.Shading.linearGradient(Gradient(colors: [colours.start, colours.end]), startPoint: left, endPoint: right)
        let core = GraphicsContext.Shading.linearGradient(
            Gradient(colors: [Self.whiten(colours.start, 0.5), Self.whiten(colours.end, 0.45)]),
            startPoint: left, endPoint: right
        )
        context.drawLayer { halo in
            halo.opacity = min(level * 0.55 * gain, 1)
            halo.addFilter(.blur(radius: 18))
            halo.fill(shape.fill, with: .color(colours.glow))
            halo.stroke(shape.fill, with: .color(colours.glow), lineWidth: 6)
        }
        context.drawLayer { edge in
            edge.opacity = min(level * gain, 1)
            edge.addFilter(.blur(radius: 3))
            edge.stroke(shape.fill, with: tube, lineWidth: 3.2)
        }
        var lit = context
        lit.opacity = level
        lit.stroke(shape.fill, with: tube, lineWidth: 1.2)
        lit.fill(shape.fill, with: core)
    }

    /// `colour` moved `amount` of the way to white.
    private static func whiten(_ colour: Color, _ amount: Double) -> Color {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(colour).getRed(&r, green: &g, blue: &b, alpha: &a)
        return Color(.sRGB, red: r + (1 - r) * amount, green: g + (1 - g) * amount, blue: b + (1 - b) * amount, opacity: a)
    }

    // MARK: Chrome

    private func chrome(look: Look) -> some View {
        let current = words[look.word]
        let on = look.tau >= M.write && look.off < 0.5
        return VStack(spacing: 0) {
            HStack(spacing: 10) {
                Circle()
                    .fill(on ? current.glow : Color.white.opacity(0.25))
                    .frame(width: 7, height: 7)
                    .shadow(color: on ? current.glow : .clear, radius: 5)
                Text(caption.uppercased())
                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    .tracking(2.4)
                    .foregroundStyle(Palette.ink.opacity(0.72))
                Spacer(minLength: 12)
                Text(String(format: "%02d / %02d", look.word + 1, words.count))
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .monospacedDigit()
                    .foregroundStyle(Palette.ink.opacity(0.42))
            }
            .padding(.horizontal, 24)
            .padding(.top, 16)

            Spacer(minLength: 0)

            VStack(spacing: 20) {
                Text(hint)
                    .font(.system(size: 13, weight: .regular))
                    .foregroundStyle(Palette.ink.opacity(0.42))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                HStack(spacing: 8) {
                    ForEach(Array(words.enumerated()), id: \.offset) { index, word in
                        let selected = index == look.word
                        Text(word.text)
                            .font(.system(size: 14, weight: selected ? .semibold : .regular))
                            .foregroundStyle(Palette.ink.opacity(selected ? 0.95 : 0.36))
                            .lineLimit(1)
                            .padding(.horizontal, 14)
                            .frame(height: 32)
                            .background(
                                Capsule().strokeBorder(selected ? word.glow.opacity(0.7) : Color.white.opacity(0.1), lineWidth: 1)
                                    .background(Capsule().fill(selected ? word.glow.opacity(0.12) : .clear))
                            )
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .allowsHitTesting(false)
    }
}
