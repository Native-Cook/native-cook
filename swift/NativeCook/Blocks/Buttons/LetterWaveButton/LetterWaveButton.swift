import SwiftUI

/// Black "SWAGGER" slab. While pressed (or by itself with `autoPlay`) orange and then navy sweep
/// across it with a round leading edge while the letters hop one after another; on release the
/// colours pull back and the letters ripple the other way.
struct LetterWaveButton: View {
    var label = "SWAGGER"
    /// Fills and empties by itself, as if a pointer kept hovering in and out.
    var autoPlay = false
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            Text(label)
        }
        .buttonStyle(Style(label: label, autoPlay: autoPlay))
        .accessibilityLabel(label)
    }

    private struct Style: ButtonStyle {
        let label: String
        let autoPlay: Bool

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, autoPlay: autoPlay, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let height = 56.0
    static let padX = 34.0
    static let radius = 2.0
    /// Extra room between letters; each one is its own view so it can hop by itself.
    static let spacing = 2.6

    /// The colours sweep in with a round leading edge this big, relative to the button's height.
    static let edgeRadius = 0.6
    static let sweepSeconds = 0.19
    static let sweepGap = 0.11
    // Each letter swaps for a fresh copy over `letterSeconds`, starting `stagger` after the one before.
    static let letterSeconds = 0.22
    static let stagger = 0.1
    static let rise = 12.0
    static let autoPlayPause = 1.2
    static let autoPlayHold = 0.9
}

private let orangeColor = Color(hex: 0xe57e32)
private let navyColor = Color(hex: 0x1b1f65)
private let easeOutQuad = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.25, y: 0.46),
    endControlPoint: UnitPoint(x: 0.45, y: 0.94)
)

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var orange = Tween(0)
    @State private var navy = Tween(0)
    /// 0 → 1 as the hop travels through the label; it rests at 0.
    @State private var wave = Tween(0)
    /// 1: letters leave upwards (filling), -1: downwards (emptying).
    @State private var direction = 1.0
    @State private var held = false
    @State private var auto = false
    @State private var shown = false
    @State private var busy = false

    private var letters: [Character] { Array(label) }
    private var waveSeconds: Double {
        Metrics.letterSeconds + Metrics.stagger * Double(max(letters.count - 1, 0))
    }

    var body: some View {
        TimelineView(.animation(paused: !busy)) { timeline in
            let date = timeline.date
            let waveNow = wave.value(at: date)
            let orangeNow = orange.value(at: date)
            let navyNow = navy.value(at: date)

            HStack(spacing: 0) {
                ForEach(Array(letters.enumerated()), id: \.offset) { index, char in
                    letter(char, index: index, wave: waveNow)
                }
            }
            .padding(.horizontal, Metrics.padX)
            .frame(height: Metrics.height)
            .background {
                Canvas { context, size in
                    draw(context, size: size, orange: orangeNow, navy: navyNow)
                }
                .allowsHitTesting(false)
            }
        }
        .contentShape(Rectangle())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: isPressed) { _, pressed in
            held = pressed
            Task { await sync() }
        }
        .task(id: autoPlay) {
            guard autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { break }
                auto = true
                Task { await sync() }
                try? await Task.sleep(for: .seconds(waveSeconds + Metrics.autoPlayHold))
                auto = false
                Task { await sync() }
                try? await Task.sleep(for: .seconds(waveSeconds))
            }
            auto = false
            Task { await sync() }
        }
    }

    /// One letter as two stacked copies: as the wave passes, the first leaves in the wave's
    /// direction while the second arrives from the opposite side.
    private func letter(_ char: Character, index: Int, wave: Double) -> some View {
        let t = min(max((wave * waveSeconds - Double(index) * Metrics.stagger) / Metrics.letterSeconds, 0), 1)
        let eased = t * t * (3 - 2 * t)
        // The copies are decoration; the button itself carries the label.
        let glyph = Text(String(char))
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(.white)
            .accessibilityHidden(true)
        return ZStack {
            glyph.opacity(1 - t).offset(y: -direction * Metrics.rise * eased)
            glyph.opacity(t).offset(y: direction * Metrics.rise * (1 - eased))
        }
        .padding(.horizontal, Metrics.spacing / 2)
    }

    /// Plays whole waves until the button shows what is wanted, so letters are never cut off mid-hop.
    private func sync() async {
        guard !busy else { return }
        busy = true
        while shown != (held || auto) {
            let on = !shown
            // Reduced motion keeps the colour change as a fade (see `draw`) and leaves the letters still.
            if !reduceMotion {
                direction = on ? 1 : -1
                wave = Tween(0)
                wave.retarget(1, duration: waveSeconds, curve: .linear)
            }
            if on {
                orange.retarget(1, duration: Metrics.sweepSeconds, curve: easeOutQuad)
                try? await Task.sleep(for: .seconds(Metrics.sweepGap))
                navy.retarget(1, duration: Metrics.sweepSeconds, curve: easeOutQuad)
            } else {
                navy.retarget(0, duration: Metrics.sweepSeconds, curve: easeOutQuad)
                try? await Task.sleep(for: .seconds(Metrics.sweepGap))
                orange.retarget(0, duration: Metrics.sweepSeconds, curve: easeOutQuad)
            }
            try? await Task.sleep(for: .seconds(waveSeconds - Metrics.sweepGap + 0.04))
            wave = Tween(0)
            shown = on
        }
        busy = false
    }

    private func draw(_ context: GraphicsContext, size: CGSize, orange: Double, navy: Double) {
        let w = size.width
        let h = size.height
        var context = context
        context.clip(to: Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: Metrics.radius))
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.black))

        let r = h * Metrics.edgeRadius
        // The circle's centre travels from fully outside to where its arc has cleared the far corners.
        let travel = w + r - (r * r - h * h / 4).squareRoot() + 1

        func sweep(_ progress: Double, color: Color) {
            let x = -r + travel * progress
            var shape = Path(CGRect(x: 0, y: 0, width: max(x, 0), height: h))
            shape.addEllipse(in: CGRect(x: x - r, y: h / 2 - r, width: r * 2, height: r * 2))
            context.fill(shape, with: .color(color))
        }

        if reduceMotion {
            // No sweep, the navy simply fades in.
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(navyColor.opacity(navy)))
        } else {
            sweep(orange, color: orangeColor)
            sweep(navy, color: navyColor)
        }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xfcfcfc).ignoresSafeArea()
        LetterWaveButton(autoPlay: true)
    }
}
