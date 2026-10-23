import SwiftUI

/// Calls `content` with the ruler's progress as it animates, so the ticks light up one after the
/// other instead of all fading in together.
private struct DockExpandSheetSkyReader<Content: View>: View, Animatable {
    var value: Double
    @ViewBuilder let content: (Double) -> Content

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View { content(value) }
}

/// The page behind the dock: a dawn sky with the sun on the arc it travels, a glow along the
/// horizon, a ruler of hours and the day's times under it.
struct DockExpandSheetSkyPage: View {
    let width: Double
    /// Where the sky ends and the horizon glows, from the top of the screen.
    let horizon: Double
    /// Top of the ruler of ticks, from the top of the screen.
    let rulerTop: Double
    /// 0 → 1: the ticks of the ruler light up from left to right.
    let ruler: Double
    /// Rows under the ruler: a label and a time each.
    let times: [[String]]

    private static let night = Color(hex: 0x05060a)
    private static let sky: [Gradient.Stop] = [
        .init(color: Color(hex: 0x0c1d45), location: 0),
        .init(color: Color(hex: 0x173a72), location: 0.38),
        .init(color: Color(hex: 0x2f6fa8), location: 0.72),
        .init(color: Color(hex: 0x6fb7d2), location: 0.94),
        .init(color: Color(hex: 0xa9d3dc), location: 1),
    ]
    private static let glow = [Color.rgba(79, 169, 201, 0), Color(hex: 0x4fa9c9), Color(hex: 0xe8b56a), Color.rgba(217, 162, 91, 0)]
    private static let tickColor = Color(hex: 0x2c3850)
    private static let majorColor = Color(hex: 0x3b4a68)
    private static let tickLit = Color(hex: 0x5aa9ff)
    private static let label = Color.white.opacity(0.42)
    private static let value = Color.white.opacity(0.62)
    private static let ticks = 41
    private static let sun = 16.0
    private static let row = 52.0
    private static let hours = ["6 AM", "Noon", "6 PM", "12 AM", "6 AM"]
    private static let icons = ["sun.max", "camera.aperture", "cloud.sun", "moon"]

    var body: some View {
        // The sun's path: a bell from below the horizon on the left, over the sun, down again on the right.
        let peak = horizon - 118
        let low = horizon + 70
        ZStack(alignment: .topLeading) {
            Self.night
            LinearGradient(stops: Self.sky, startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: horizon)
            // The first light, held just over the horizon.
            LinearGradient(colors: [Color.rgba(232, 181, 106, 0), Color.rgba(232, 181, 106, 0.28)], startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: 60)
                .offset(y: horizon - 60)
            LinearGradient(colors: Self.glow, startPoint: .leading, endPoint: .trailing)
                .frame(width: width, height: 2)
                .offset(y: horizon - 1)
            LinearGradient(colors: [Color.rgba(79, 169, 201, 0.18), Color.rgba(5, 6, 10, 0)], startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: 70)
                .offset(y: horizon + 1)

            Path { path in
                path.move(to: CGPoint(x: -30, y: low))
                path.addCurve(to: CGPoint(x: width / 2, y: peak), control1: CGPoint(x: width * 0.12, y: low), control2: CGPoint(x: width * 0.26, y: peak))
                path.addCurve(to: CGPoint(x: width + 30, y: low), control1: CGPoint(x: width * 0.74, y: peak), control2: CGPoint(x: width * 0.88, y: low))
            }
            // The gradient spans the arc's own box, from its peak to its feet, as SVG's does.
            .stroke(
                LinearGradient(
                    stops: [
                        .init(color: .white.opacity(0.4), location: 0),
                        .init(color: .white.opacity(0.2), location: (horizon - peak) / (low - peak + 20)),
                        .init(color: .white.opacity(0.04), location: 1),
                    ],
                    startPoint: UnitPoint(x: 0, y: peak / (low + 20)),
                    endPoint: UnitPoint(x: 0, y: low / (low + 20))
                ),
                lineWidth: 1
            )
            .frame(width: width, height: low + 20)

            Circle().fill(.white.opacity(0.08)).frame(width: 72, height: 72)
                .offset(x: width / 2 - 36, y: peak - 36)
            Circle().fill(.white.opacity(0.22)).frame(width: 40, height: 40)
                .offset(x: width / 2 - 20, y: peak - 20)
            Circle().fill(.white).frame(width: Self.sun, height: Self.sun)
                .shadow(color: .white.opacity(0.9), radius: 10)
                .offset(x: width / 2 - Self.sun / 2, y: peak - Self.sun / 2)

            DockExpandSheetSkyReader(value: ruler) { progress in
                HStack(alignment: .bottom, spacing: 0) {
                    ForEach(0..<Self.ticks, id: \.self) { index in
                        let major = index % 10 == 0
                        RoundedRectangle(cornerRadius: 1).fill(major ? Self.majorColor : Self.tickColor)
                            .overlay(RoundedRectangle(cornerRadius: 1).fill(Self.tickLit).opacity(min(1, max(0, progress * Double(Self.ticks + 4) - Double(index)))))
                            .frame(width: 2, height: major ? 20 : 12)
                        if index < Self.ticks - 1 { Spacer(minLength: 0) }
                    }
                }
                .frame(width: width - 40, height: 20)
            }
            .offset(x: 20, y: rulerTop)

            HStack(spacing: 0) {
                ForEach(Array(Self.hours.enumerated()), id: \.offset) { index, hour in
                    Text(hour)
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(0.4)
                        .foregroundStyle(.white.opacity(0.3))
                    if index < Self.hours.count - 1 { Spacer(minLength: 0) }
                }
            }
            .frame(width: width - 40, height: 14)
            .offset(x: 20, y: rulerTop + 26)

            VStack(spacing: 0) {
                ForEach(Array(times.enumerated()), id: \.offset) { index, row in
                    HStack {
                        HStack(spacing: 10) {
                            Image(systemName: Self.icons[index % Self.icons.count])
                                .font(.system(size: 14))
                                .frame(width: 16)
                            Text(row[0]).font(.system(size: 15, weight: .medium))
                        }
                        .foregroundStyle(Self.label)
                        Spacer(minLength: 0)
                        Text(row[1])
                            .font(.system(size: 22, weight: .semibold).monospacedDigit())
                            .tracking(-0.4)
                            .foregroundStyle(Self.value)
                    }
                    .frame(height: Self.row)
                    .overlay(alignment: .top) {
                        if index > 0 { Rectangle().fill(.white.opacity(0.08)).frame(height: 1.0 / 3) }
                    }
                }
            }
            .frame(width: width - 40)
            .offset(x: 20, y: rulerTop + 58)
        }
        .frame(width: width, alignment: .topLeading)
    }
}
