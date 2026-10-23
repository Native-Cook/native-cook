import SwiftUI

/// Generic glyphs for `DepthButton`, drawn on an 18-point grid.
enum DepthButtonIcon {
    case share, plus, heart, play

    /// Line width for outlined glyphs; nil for filled ones.
    fileprivate var stroke: Double? {
        switch self {
        case .share: 2
        case .plus: 2.4
        case .heart, .play: nil
        }
    }

    fileprivate var path: Path {
        func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }
        var path = Path()
        switch self {
        case .share:
            path.move(to: p(9, 11.5))
            path.addLine(to: p(9, 2.6))
            path.move(to: p(5.4, 6))
            path.addLine(to: p(9, 2.4))
            path.addLine(to: p(12.6, 6))
            path.move(to: p(3, 10))
            path.addLine(to: p(3, 14.2))
            path.addQuadCurve(to: p(4.4, 15.6), control: p(3, 15.6))
            path.addLine(to: p(13.6, 15.6))
            path.addQuadCurve(to: p(15, 14.2), control: p(15, 15.6))
            path.addLine(to: p(15, 10))
        case .plus:
            path.move(to: p(9, 3.2))
            path.addLine(to: p(9, 14.8))
            path.move(to: p(3.2, 9))
            path.addLine(to: p(14.8, 9))
        case .heart:
            path.move(to: p(9, 15.5))
            path.addCurve(to: p(1.5, 6), control1: p(3, 11.5), control2: p(1.5, 8.5))
            path.addCurve(to: p(5.2, 2.3), control1: p(1.5, 3.8), control2: p(3.2, 2.3))
            path.addCurve(to: p(9, 4.6), control1: p(6.8, 2.3), control2: p(8.2, 3.2))
            path.addCurve(to: p(12.8, 2.3), control1: p(9.8, 3.2), control2: p(11.2, 2.3))
            path.addCurve(to: p(16.5, 6), control1: p(14.8, 2.3), control2: p(16.5, 3.8))
            path.addCurve(to: p(9, 15.5), control1: p(16.5, 8.5), control2: p(15, 11.5))
            path.closeSubpath()
        case .play:
            path.move(to: p(5, 2.8))
            path.addLine(to: p(15.2, 9))
            path.addLine(to: p(5, 15.2))
            path.closeSubpath()
        }
        return path
    }
}

/// Chunky push button: a flat coloured face sitting on a darker side wall. Pressing it sinks the
/// face into the wall; letting go pops it back up.
struct DepthButton: View {
    var label = "Button"
    /// Colour of the face.
    var color = Color(hex: 0x1361ef)
    /// Colour of the side wall the face sinks into.
    var sideColor = Color(hex: 0x1d2f61)
    var icon: DepthButtonIcon? = nil
    var width = 148.0
    /// Presses itself once per cycle, as if a finger kept tapping it.
    var autoPlay = false
    /// Where in the cycle the self-press happens (seconds), so a group of buttons can take turns.
    var autoPlayDelay = 0.0
    var action: () -> Void = {}

    @State private var auto = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { glyph(icon) }
                Text(label)
                    .font(.system(size: 17, weight: .medium))
                    .kerning(0.5)
                    .lineLimit(1)
            }
            .foregroundStyle(.white)
        }
        .buttonStyle(Style(color: color, sideColor: sideColor, width: width, auto: autoPlay && auto))
        .task(id: autoPlay) {
            auto = false
            guard autoPlay else { return }
            try? await Task.sleep(for: .seconds(0.9 + autoPlayDelay))
            while !Task.isCancelled {
                auto = true
                try? await Task.sleep(for: .seconds(Metrics.autoPlayHold))
                auto = false
                try? await Task.sleep(for: .seconds(Metrics.autoPlayCycle - Metrics.autoPlayHold))
            }
        }
    }

    private func glyph(_ icon: DepthButtonIcon) -> some View {
        Canvas { context, _ in
            let path = icon.path
            if let width = icon.stroke {
                context.stroke(path, with: .color(.white), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
            } else {
                context.fill(path, with: .color(.white))
                // A thin same-colour outline rounds the filled shape's corners.
                context.stroke(path, with: .color(.white), style: StrokeStyle(lineWidth: 1.4, lineJoin: .round))
            }
        }
        .frame(width: 18, height: 18)
    }

    private struct Style: ButtonStyle {
        let color: Color
        let sideColor: Color
        let width: Double
        let auto: Bool

        @Environment(\.accessibilityReduceMotion) private var reduceMotion

        func makeBody(configuration: Configuration) -> some View {
            let down = configuration.isPressed || auto
            configuration.label
                .frame(width: width, height: Metrics.faceHeight)
                .background(color, in: RoundedRectangle(cornerRadius: Metrics.radius))
                .offset(y: down ? Metrics.depth : 0)
                .frame(height: Metrics.faceHeight + Metrics.depth, alignment: .top)
                .background(alignment: .bottom) {
                    RoundedRectangle(cornerRadius: Metrics.radius)
                        .fill(sideColor)
                        .frame(height: Metrics.faceHeight)
                }
                .contentShape(Rectangle())
                // Quick on the way down, a touch slower coming back up; reduced motion snaps,
                // which is exactly how the reference behaves.
                .animation(
                    reduceMotion ? nil : .timingCurve(0.23, 1, 0.32, 1, duration: down ? 0.09 : 0.15),
                    value: down
                )
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

/// The four buttons from the reference as a 2×2 group. With `autoPlay` they press in turn.
struct DepthButtonGroup: View {
    var autoPlay = false

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                DepthButton(
                    label: "Share", color: Color(hex: 0x1361ef), sideColor: Color(hex: 0x1d2f61), icon: .share,
                    autoPlay: autoPlay, autoPlayDelay: turn(2)
                )
                DepthButton(
                    label: "Follow", color: Color(hex: 0x198cec), sideColor: Color(hex: 0x233d6b), icon: .plus,
                    autoPlay: autoPlay, autoPlayDelay: turn(3)
                )
            }
            HStack(spacing: 12) {
                DepthButton(
                    label: "Like", color: Color(hex: 0xee542c), sideColor: Color(hex: 0x8a0a24), icon: .heart,
                    autoPlay: autoPlay, autoPlayDelay: turn(0)
                )
                DepthButton(
                    label: "Play", color: Color(hex: 0xcf161e), sideColor: Color(hex: 0x8a0005), icon: .play,
                    autoPlay: autoPlay, autoPlayDelay: turn(1)
                )
            }
        }
    }

    /// The order the reference's cursor visits the buttons in: clockwise from the bottom left.
    private func turn(_ index: Double) -> Double {
        index * Metrics.autoPlayCycle / 4
    }
}

private enum Metrics {
    static let faceHeight = 46.0
    /// Height of the side wall, and how far the face travels when pressed.
    static let depth = 9.0
    static let radius = 5.0
    static let autoPlayCycle = 4.4
    static let autoPlayHold = 0.24
}

#Preview {
    ZStack {
        Color(hex: 0xf5e9d3).ignoresSafeArea()
        DepthButtonGroup(autoPlay: true)
    }
}
