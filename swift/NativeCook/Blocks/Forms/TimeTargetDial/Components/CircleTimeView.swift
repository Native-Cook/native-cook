import SwiftUI

/// `src/components/circle-time/index.tsx` + `line-time.tsx`
///
/// The RN version renders 200 absolutely-positioned `Animated.View` ticks plus 10 hour labels and
/// rotates them with a shared value. Here everything is drawn in a single `Canvas`, which is both
/// simpler and cheaper, and the same gradient overlays are layered on top.
struct CircleTimeView: View {
    var selectedDuration: Double
    var bigLineIndexOffset: Int = 10

    private let radius: CGFloat = 200
    private let hourLabelsAmount = 10
    private let maxLineHeight: CGFloat = 20
    private let minLineHeight: CGFloat = 8
    private let linesAmount = 200
    private let lineWidth: CGFloat = 1.5
    private let tickColor = Color(timeTargetHex: 0xC6C6C6)

    private let labelWidth: CGFloat = 60
    private let labelHeight: CGFloat = 30
    private let labelRadiusOffset: CGFloat = 40

    /// The RN container is shifted `right: 60`, so the circle centre sits 60pt left of the screen edge.
    private let centerXOffset: CGFloat = -60

    private var mediumLineHeight: CGFloat { (maxLineHeight + minLineHeight) / 2 }

    /// `interpolate(-progress, [0, circumference], [0, 2π])` collapses to `targetIndex * 2π / linesAmount`.
    private var progressRadians: Double {
        let clampedDuration = max(0, selectedDuration)
        let targetIndex = min(Double(linesAmount - 1), clampedDuration * Double(bigLineIndexOffset))
        return targetIndex * 2 * .pi / Double(linesAmount)
    }

    var body: some View {
        GeometryReader { geometry in
            let center = CGPoint(x: centerXOffset, y: geometry.size.height / 2)
            let gradientLeft = center.x + 27 - (radius + 47)
            let gradientRight = gradientLeft + (radius * 2 + 94) * 1.1
            let fadeFraction = min(0.5, 80 / max(1, geometry.size.height))

            ZStack {
                Canvas(rendersAsynchronously: false) { context, _ in
                    drawTicks(in: &context, center: center)
                    drawHourLabels(in: &context, center: center)
                }

                // Cover the full canvas so an hour glyph cannot escape a circular
                // overlay and abruptly become bright near its top/bottom boundary.
                Rectangle()
                    .fill(
                        LinearGradient(
                            stops: [
                                .init(color: .black, location: 0.0),
                                .init(color: .black, location: 0.2),
                                .init(color: .black, location: 0.4),
                                .init(color: .black.opacity(0xF9 / 255.0), location: 0.6),
                                .init(color: .black.opacity(0xB2 / 255.0), location: 0.8),
                                .init(color: .clear, location: 1.0),
                            ],
                            startPoint: UnitPoint(x: gradientLeft / max(1, geometry.size.width), y: 0.5),
                            endPoint: UnitPoint(x: gradientRight / max(1, geometry.size.width), y: 0.5)
                        )
                    )

                // Inner mask: hides the currently selected hour label on the ring
                // (it is shown big in `TimeRangeView` instead).
                Capsule()
                    .fill(
                        LinearGradient(
                            stops: [
                                .init(color: .clear, location: 0),
                                .init(color: .black, location: 0.25),
                                .init(color: .black, location: 0.75),
                                .init(color: .clear, location: 1),
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .frame(width: labelWidth, height: labelHeight)
                    .position(x: center.x + radius + labelRadiusOffset + 10, y: center.y)
            }
            // Fade the complete ruler (ticks and hour labels) into the black
            // background before it reaches either clipped edge. The picker is separate.
            .mask {
                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0),
                        .init(color: .white, location: fadeFraction),
                        .init(color: .white, location: 1 - fadeFraction),
                        .init(color: .clear, location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
        }
        .clipped()
        .allowsHitTesting(false)
    }

    private func angle(forTick index: Int) -> Double {
        (2 * .pi / Double(linesAmount)) * Double(index) - progressRadians
    }

    private func drawTicks(in context: inout GraphicsContext, center: CGPoint) {
        let midpointOffset = bigLineIndexOffset / 2
        var path = Path()

        for index in 0..<linesAmount {
            let isBigLine = index % bigLineIndexOffset == 0
            let isMediumLine = !isBigLine && index % bigLineIndexOffset == midpointOffset
            let height: CGFloat = isBigLine ? maxLineHeight : (isMediumLine ? mediumLineHeight : minLineHeight)

            let a = angle(forTick: index)
            let dx = CGFloat(cos(a))
            let dy = CGFloat(sin(a))

            // Each tick originates at the circle and points outward.
            path.move(to: CGPoint(x: center.x + dx * radius, y: center.y + dy * radius))
            path.addLine(to: CGPoint(x: center.x + dx * (radius + height), y: center.y + dy * (radius + height)))
        }

        context.stroke(path, with: .color(tickColor), lineWidth: lineWidth)
    }

    private func drawHourLabels(in context: inout GraphicsContext, center: CGPoint) {
        let labelRadius = radius + labelRadiusOffset

        for hourIndex in 0..<hourLabelsAmount {
            let tickIndex = (hourIndex + 1) * bigLineIndexOffset
            guard tickIndex <= linesAmount else { continue }

            let a = angle(forTick: tickIndex)
            let position = CGPoint(
                x: center.x + CGFloat(cos(a)) * labelRadius + 10, // `left: 10`
                y: center.y + CGFloat(sin(a)) * labelRadius
            )

            let text = context.resolve(
                Text("\(hourIndex + 1) hr")
                    .font(.rounded(20))
                    .foregroundStyle(Color(timeTargetHex: 0xD8D8D8))
            )
            context.draw(text, at: position, anchor: .center)
        }
    }
}

#Preview {
    CircleTimeView(selectedDuration: 5)
        .background(Color.black)
        .preferredColorScheme(.dark)
}
