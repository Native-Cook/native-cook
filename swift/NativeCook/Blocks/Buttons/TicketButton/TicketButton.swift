import SwiftUI

/// White "GET TICKET" slab. While pressed (or by itself with `autoPlay`) a teal sheet with a
/// rounded leading edge rises from the bottom and the label rolls over to white; on release a white
/// sheet drops back down from the top.
struct TicketButton: View {
    var label = "GET TICKET"
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
    static let height = 48.0
    static let padX = 62.0
    /// The label rolls inside a window this tall, like a one-line ticker.
    static let line = 24.0
    static let fillSeconds = 0.55
    static let rollSeconds = 0.3
    static let autoPlayPause = 1.3
    static let autoPlayHold = 1.5
    /// How far the corners of the moving edge trail behind its middle, as a fraction of the height.
    static let lag = 0.45
}

private let paper = Color(hex: 0xfffdfe)
private let teal = Color(hex: 0x46c9cb)
private let ink = Color(hex: 0x141016)
private let easeOutCubic = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.215, y: 0.61),
    endControlPoint: UnitPoint(x: 0.355, y: 1)
)

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Runs 0 → 1 while the teal sheet rises from the bottom and 1 → 2 while the white sheet drops
    /// back from the top; 2 looks exactly like 0, so it can snap back unnoticed.
    @State private var phase = Tween(0)
    /// 0 shows the dark label, 1 the white one.
    @State private var roll = Tween(0)
    @State private var held = false
    @State private var auto = false
    @State private var shown = false
    @State private var busy = false

    var body: some View {
        TimelineView(.animation(paused: !busy)) { timeline in
            let date = timeline.date
            let rollNow = roll.value(at: date)
            let phaseNow = phase.value(at: date)

            ZStack {
                text(ink)
                    .opacity(reduceMotion ? 1 - rollNow : 1)
                    .offset(y: reduceMotion ? 0 : -Metrics.line * rollNow)
                text(.white)
                    .opacity(reduceMotion ? rollNow : 1)
                    .offset(y: reduceMotion ? 0 : Metrics.line * (1 - rollNow))
            }
            .frame(height: Metrics.line)
            .clipped()
            .padding(.horizontal, Metrics.padX)
            .frame(height: Metrics.height)
            .background {
                Canvas { context, size in
                    draw(context, size: size, phase: phaseNow)
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
                try? await Task.sleep(for: .seconds(Metrics.fillSeconds + Metrics.autoPlayHold))
                auto = false
                Task { await sync() }
                try? await Task.sleep(for: .seconds(Metrics.fillSeconds))
            }
            auto = false
            Task { await sync() }
        }
    }

    private func text(_ color: Color) -> some View {
        Text(label)
            .font(.system(size: 16, weight: .bold, design: .monospaced))
            .kerning(1.2)
            .lineLimit(1)
            .foregroundStyle(color)
            // Both copies say the same thing; the button itself carries the label.
            .accessibilityHidden(true)
    }

    /// Plays whole sweeps until the button shows what is wanted, so a quick tap never cuts one short.
    private func sync() async {
        guard !busy else { return }
        busy = true
        while shown != (held || auto) {
            let on = !shown
            if on { phase = Tween(0) }
            phase.retarget(on ? 1 : 2, duration: Metrics.fillSeconds, curve: easeOutCubic)
            roll.retarget(on ? 1 : 0, duration: Metrics.rollSeconds, curve: easeOutCubic)
            try? await Task.sleep(for: .seconds(Metrics.fillSeconds))
            shown = on
        }
        busy = false
    }

    private func draw(_ context: GraphicsContext, size: CGSize, phase: Double) {
        let w = size.width
        let h = size.height
        let filling = phase <= 1
        let progress = filling ? phase : phase - 1
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(filling ? paper : teal))

        // Reduced motion: the sheet does not travel, it fades in over the whole button.
        let p = reduceMotion ? 1 : progress
        let lag = h * Metrics.lag * (1 - p)
        // A full-width dome at first that flattens into a straight edge with rounded corners.
        let rx = w * (0.06 + 0.44 * pow(1 - p, 3))
        let k = 0.5523
        // `toward` points from the edge back to the side the sheet came from.
        let home = filling ? h : 0.0
        let toward = filling ? 1.0 : -1.0
        let edge = filling ? h * (1 - p) : h * p
        let side = edge + toward * lag

        var sheet = Path()
        sheet.move(to: CGPoint(x: 0, y: home))
        sheet.addLine(to: CGPoint(x: 0, y: side))
        sheet.addCurve(
            to: CGPoint(x: rx, y: edge),
            control1: CGPoint(x: 0, y: side - toward * k * lag),
            control2: CGPoint(x: rx - k * rx, y: edge)
        )
        sheet.addLine(to: CGPoint(x: w - rx, y: edge))
        sheet.addCurve(
            to: CGPoint(x: w, y: side),
            control1: CGPoint(x: w - rx + k * rx, y: edge),
            control2: CGPoint(x: w, y: side - toward * k * lag)
        )
        sheet.addLine(to: CGPoint(x: w, y: home))
        sheet.closeSubpath()

        var layer = context
        layer.opacity = reduceMotion ? progress : 1
        layer.fill(sheet, with: .color(filling ? teal : paper))
    }
}

#Preview {
    ZStack {
        Color(hex: 0x190e1c).ignoresSafeArea()
        TicketButton(autoPlay: true)
    }
}
