import SwiftUI

/*
 RGB Glitch Headline: a still, light geometric headline that is hit by bursts of signal interference.

 Timeline of the self-playing loop (`RgbGlitchHeadline.loop` = 4.8 s); the headline is clean and still
 outside the bursts, which is also the resting frame:
   0.90–1.18  burst, strength 0.7
   2.05–2.21  short burst, strength 0.45
   3.30–3.74  long burst, strength 1.0
 A burst is stepped, never eased: every 40 ms it picks a new frame from a seeded hash (the same every
 loop): 2–5 horizontal bands of the headline knocked sideways by 8–50 % of the cap height, a red copy and
 a cyan copy split apart by 4–14 % of it (added together they make white again), sometimes a 10° shear of
 the whole block, a band whose white layer drops out, and up to two thin noise lines. Each step's
 strength is the burst's strength times 0.55–1.
 The "LIVE" dot pulses with a 1.2 s period, four times a loop.

 Touch: a tap fires a 0.3 s burst (strength 0.85). Dragging tears the band under the finger sideways
 with the finger (0.9 × the drag, at most 40 % of the width) while the rest jams lightly (strength
 0.35), the colour split growing with the tear; letting go snaps it back with a 0.25 s burst. 2 s after
 the last touch the loop starts over. Reduced motion: the headline stays clean, the dot still, and touch
 does nothing.
*/
struct RgbGlitchHeadline: View {
    var lines = ["NOTHING", "IS", "STATIC"]
    var channel = "CH 07"
    var programme = "Night broadcast"
    var live = "Live"
    var subtitle = "Season two airs Friday at midnight."
    var action = "Remind me"
    var hint = "Tap to interfere · drag to tear"
    /// Plays the interference by itself, every `loop` seconds round.
    var autoPlay = false
    var onAction: (() -> Void)? = nil

    static let loop = 4.8

    struct Burst {
        let start: Double
        let duration: Double
        let amount: Double
        let seed: Int
    }

    /// The loop's bursts, by their start in the loop.
    static let bursts = [
        Burst(start: 0.9, duration: 0.28, amount: 0.7, seed: 11),
        Burst(start: 2.05, duration: 0.16, amount: 0.45, seed: 23),
        Burst(start: 3.3, duration: 0.44, amount: 1.0, seed: 37),
    ]

    enum M {
        static let step = 0.04
        static let tap = 0.3
        static let tapAmount = 0.85
        static let release = 0.25
        static let releaseAmount = 0.7
        static let jam = 0.35
        static let idle = 2.0
        static let pulse = 1.2
        /// Where the middle of the headline sits, as a share of the screen's height.
        static let centre = 0.43
    }

    private struct Tear {
        var y: Double
        var dx: Double
        let start: Date
    }

    private struct Shot {
        let start: Date
        let duration: Double
        let amount: Double
        let seed: Int
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var type = RgbGlitchHeadlineType()
    @State private var loopStart = Date.now
    @State private var shot: Shot?
    @State private var tear: Tear?
    /// From the first touch until the loop starts over.
    @State private var held = false
    @State private var touches = 0
    @State private var dragging = false
    @State private var tick = 0
    @State private var softHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var hardHaptic = UIImpactFeedbackGenerator(style: .rigid)
    @State private var tickHaptic = UISelectionFeedbackGenerator()

    private enum Palette {
        static let back = Color(hex: 0x050506)
        static let lift = Color(hex: 0x111116)
        static let red = Color(hex: 0xff2a55)
        static let cyan = Color(hex: 0x00f0ff)
        static let ink = Color.white
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let block = type.block(lines, width: screen.width, centreY: screen.height * M.centre)
            TimelineView(.animation(paused: reduceMotion || (!autoPlay && shot == nil && tear == nil))) { timeline in
                let frame = glitch(at: timeline.date, block: block)
                let pulse = reduceMotion ? 1 : 0.5 + 0.5 * cos(2 * .pi * timeline.date.timeIntervalSince(loopStart) / M.pulse)
                ZStack(alignment: .topLeading) {
                    Canvas { context, size in
                        draw(&context, size: size, block: block, frame: frame)
                    }
                    .accessibilityLabel(lines.joined(separator: " ").capitalized)
                    .accessibilityAddTraits(.isHeader)

                    chrome(screen: screen, insets: insets, block: block, pulse: pulse)
                }
                .ignoresSafeArea()
            }
            .contentShape(Rectangle())
            .simultaneousGesture(drag(block: block, width: screen.width))
        }
        .background(Palette.back.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            loopStart = .now
            softHaptic.prepare()
            hardHaptic.prepare()
        }
        .task(id: touches) { await settle() }
    }

    // MARK: Timeline

    private func glitch(at date: Date, block: RgbGlitchHeadlineType.Block) -> RgbGlitchHeadlineFrame? {
        guard !reduceMotion else { return nil }
        if let tear {
            let step = Int(date.timeIntervalSince(tear.start) / M.step)
            var frame = RgbGlitchHeadlineFrame.make(seed: 101, step: step, amount: M.jam, block: block)
            let half = block.capHeight * 0.28
            frame.bands.append(.init(y0: tear.y - half, y1: tear.y + half, dx: tear.dx))
            let split = min(abs(tear.dx) * 0.12, block.capHeight * 0.2)
            frame.split = tear.dx < 0 ? -max(abs(frame.split), split) : max(abs(frame.split), split)
            return frame
        }
        if let shot {
            let e = date.timeIntervalSince(shot.start)
            if e >= 0, e < shot.duration { return Self.frame(seed: shot.seed, e: e, amount: shot.amount, block: block) }
        }
        guard autoPlay, !held else { return nil }
        let e = date.timeIntervalSince(loopStart).truncatingRemainder(dividingBy: Self.loop)
        for burst in Self.bursts where e >= burst.start && e < burst.start + burst.duration {
            return Self.frame(seed: burst.seed, e: e - burst.start, amount: burst.amount, block: block)
        }
        return nil
    }

    private static func frame(seed: Int, e: Double, amount: Double, block: RgbGlitchHeadlineType.Block) -> RgbGlitchHeadlineFrame {
        let step = Int(e / M.step)
        let strength = amount * (0.55 + 0.45 * RgbGlitchHeadlineFrame.random(seed &* 977 &+ step))
        return RgbGlitchHeadlineFrame.make(seed: seed, step: step, amount: strength, block: block)
    }

    // MARK: Touch

    private func drag(block: RgbGlitchHeadlineType.Block, width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .onChanged { value in
                if !dragging {
                    dragging = true
                    held = true
                    touches += 1
                    softHaptic.impactOccurred(intensity: 0.6)
                }
                guard !reduceMotion else { return }
                let moved = hypot(value.translation.width, value.translation.height) > 6
                guard moved || tear != nil else { return }
                let limit = width * 0.4
                let dx = min(max(value.translation.width * 0.9, -limit), limit)
                if tear == nil {
                    tear = Tear(y: value.location.y, dx: dx, start: .now)
                    tickHaptic.prepare()
                } else {
                    tear?.y = value.location.y
                    tear?.dx = dx
                }
                let notch = Int(dx / 40)
                if notch != tick {
                    tick = notch
                    tickHaptic.selectionChanged()
                }
            }
            .onEnded { _ in
                dragging = false
                touches += 1
                tick = 0
                guard !reduceMotion else { return }
                let released = tear != nil
                tear = nil
                shot = Shot(
                    start: .now,
                    duration: released ? M.release : M.tap,
                    amount: released ? M.releaseAmount : M.tapAmount,
                    seed: 300 + touches
                )
                hardHaptic.impactOccurred()
            }
    }

    /// 2 s after the last touch the loop starts over from its clean frame.
    private func settle() async {
        guard touches > 0, !dragging else { return }
        try? await Task.sleep(for: .seconds(M.idle))
        guard !Task.isCancelled, !dragging else { return }
        shot = nil
        held = false
        loopStart = .now
    }

    // MARK: Drawing

    private func draw(_ context: inout GraphicsContext, size: CGSize, block: RgbGlitchHeadlineType.Block, frame: RgbGlitchHeadlineFrame?) {
        let full = Path(CGRect(origin: .zero, size: size))
        context.fill(full, with: .radialGradient(
            Gradient(colors: [Palette.lift, Palette.back]),
            center: block.centre, startRadius: 0, endRadius: size.height * 0.7
        ))

        let cap = block.capHeight
        let top = block.top - cap * 0.5
        let bottom = block.bottom + cap * 0.5
        if let frame {
            let strips = frame.strips(top: top, bottom: bottom)
            let layers: [(Color, Double, Bool)] = [(Palette.red, frame.split, true), (Palette.cyan, -frame.split, true), (Palette.ink, 0, false)]
            for (colour, shift, additive) in layers {
                for strip in strips where !(strip.hidden && !additive) {
                    var c = context
                    c.clip(to: Path(CGRect(x: 0, y: strip.y0, width: size.width, height: strip.y1 - strip.y0)))
                    if additive { c.blendMode = .plusLighter }
                    c.translateBy(x: strip.dx + frame.jolt + shift, y: 0)
                    if frame.shear != 0 {
                        c.concatenate(CGAffineTransform(a: 1, b: 0, c: -frame.shear, d: 1, tx: frame.shear * block.centre.y, ty: 0))
                    }
                    c.fill(block.path, with: .color(colour))
                }
            }
            for (y, alpha) in frame.noise {
                context.fill(Path(CGRect(x: 0, y: y, width: size.width, height: 1)), with: .color(.white.opacity(alpha)))
                context.fill(Path(CGRect(x: 0, y: y + 2, width: size.width, height: 1)), with: .color(Palette.cyan.opacity(alpha * 0.6)))
            }
        } else {
            context.fill(block.path, with: .color(Palette.ink))
        }

        // Scan lines over the headline: a 1 pt dark line every 4 pt.
        var scan = Path()
        var y = top
        while y < bottom {
            scan.addRect(CGRect(x: 0, y: y, width: size.width, height: 1))
            y += 4
        }
        context.fill(scan, with: .color(Palette.back.opacity(0.2)))
    }

    // MARK: Chrome

    private func chrome(screen: CGSize, insets: EdgeInsets, block: RgbGlitchHeadlineType.Block, pulse: Double) -> some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: 10) {
                Text(channel.uppercased())
                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    .tracking(2)
                    .foregroundStyle(Palette.ink.opacity(0.75))
                Text(programme.uppercased())
                    .font(.system(size: 12, weight: .regular, design: .monospaced))
                    .tracking(2)
                    .foregroundStyle(Palette.ink.opacity(0.38))
                    .lineLimit(1)
                Spacer(minLength: 12)
                Circle()
                    .fill(Palette.red)
                    .frame(width: 7, height: 7)
                    .opacity(0.35 + 0.65 * pulse)
                Text(live.uppercased())
                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    .tracking(2)
                    .foregroundStyle(Palette.red)
            }
            .padding(.horizontal, 24)
            .frame(width: screen.width, height: 20)
            .offset(y: insets.top + 16)

            VStack(spacing: 24) {
                Text(subtitle)
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(Palette.ink.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                Button {
                    onAction?()
                } label: {
                    Text(action)
                        .font(.system(size: 15, weight: .medium))
                        .tracking(0.4)
                        .foregroundStyle(Palette.ink)
                        .frame(width: 168, height: 48)
                        .background(Capsule().strokeBorder(Palette.ink.opacity(0.4), lineWidth: 1))
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 32)
            .frame(width: screen.width)
            .offset(y: block.bottom + block.capHeight * 0.5 + 28)

            Text(hint.uppercased())
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .tracking(1.6)
                .foregroundStyle(Palette.ink.opacity(0.34))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(width: screen.width)
                .offset(y: screen.height - max(insets.bottom, 16) - 24 - 14)
        }
        .frame(width: screen.width, height: screen.height, alignment: .topLeading)
    }
}
