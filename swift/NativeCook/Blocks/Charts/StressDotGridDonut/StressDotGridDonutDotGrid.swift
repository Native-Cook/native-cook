import SwiftUI

private let labelColor = Color(hex: 0x6C6C70)
private let rowLine = Color(.sRGB, red: 0.17, green: 0.17, blue: 0.19)
private let nowRing = Color(hex: 0x8E8E93)

// Layout, in points.
private let header = 24.0
private let row = 28.0
// The row labels' column: day names are short, week ranges are not.
private let dayLabels = 40.0
private let weekLabels = 92.0
// Dot radii: a day's period, a day of the four weeks, and a cell with no data.
private let dotDay = 4.0
private let dotWeek = 5.0
private let dotEmpty = 2.0
private let nowRadius = 6.0
private let glowBlur = 3.0
private let glowAlpha = 0.45

// Motion, in seconds of the morph: each dot moves for dotFor, starting up to stagger later the
// farther it is from the top left.
private let dotFor = 0.6
private let stagger = 0.3
private let labelsOver = 0.4

/// The stress heat map. Every two-hour dot of the week has a place in the four-week grid: the day's
/// row becomes that weekday's column, and its hours split into the four week rows, so three dots
/// meet in each cell of the four weeks and the grid can morph either way dot for dot.
struct StressDotGridDonutDotGrid: View {
    static let morphFor = dotFor + stagger

    var width: Double
    var days: StressDotGridDonutPeriod
    var weeks: StressDotGridDonutPeriod
    /// 0 shows the days, 1 the weeks; between, every dot is on its way.
    var morph: Double
    /// The grid's opacity; dips when the view jumps under reduced motion.
    var opacity: Double

    static func height(_ period: StressDotGridDonutPeriod) -> Double {
        header + Double(period.rows.count) * row
    }

    /// Smooth start and stop over 0…1.
    static func ease(_ t: Double) -> Double {
        let x = min(1, max(0, t))
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    var body: some View {
        let dayHeight = Self.height(days)
        let weekHeight = Self.height(weeks)
        // The labels hand over without overlapping: the old ones leave in the first part of the morph, the new arrive in the last.
        let daysShown = 1 - min(1, max(0, morph / labelsOver))
        let weeksShown = min(1, max(0, (morph - 1 + labelsOver) / labelsOver))
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in draw(context) }
                .frame(width: width, height: max(dayHeight, weekHeight))
            labels(days, dayLabels).opacity(daysShown)
            labels(weeks, weekLabels).opacity(weeksShown)
        }
        .frame(width: width, height: dayHeight + (weekHeight - dayHeight) * Self.ease(morph), alignment: .topLeading)
        .clipped()
        .opacity(opacity)
    }

    private func at(_ period: StressDotGridDonutPeriod, _ labels: Double) -> (x: (Int) -> Double, y: (Int) -> Double) {
        let pitch = (width - labels) / Double(period.columns.count)
        return ({ labels + pitch * (Double($0) + 0.5) }, { header + row * (Double($0) + 0.5) })
    }

    private func labels(_ period: StressDotGridDonutPeriod, _ labelWidth: Double) -> some View {
        let place = at(period, labelWidth)
        return ZStack(alignment: .topLeading) {
            ForEach(period.columns.indices, id: \.self) { c in
                label(period.columns[c])
                    .frame(width: 40)
                    .offset(x: place.x(c) - 20, y: 2)
            }
            ForEach(period.rows.indices, id: \.self) { r in
                label(period.rows[r])
                    .fixedSize()
                    .offset(y: place.y(r) - 7)
            }
        }
        .frame(width: width, height: Self.height(period), alignment: .topLeading)
    }

    private func label(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .medium).monospacedDigit())
            .foregroundStyle(labelColor)
            .lineLimit(1)
            .frame(height: 14)
    }

    private func draw(_ context: GraphicsContext) {
        let whole = Self.ease(morph)
        let from = at(days, dayLabels)
        let to = at(weeks, weekLabels)

        func lines(_ period: StressDotGridDonutPeriod, _ place: (x: (Int) -> Double, y: (Int) -> Double), _ alpha: Double) {
            var path = Path()
            for r in period.rows.indices {
                path.move(to: CGPoint(x: place.x(0), y: place.y(r)))
                path.addLine(to: CGPoint(x: place.x(period.columns.count - 1), y: place.y(r)))
            }
            context.stroke(path, with: .color(rowLine.opacity(alpha)), lineWidth: 1)
        }
        lines(days, from, 1 - whole)
        lines(weeks, to, whole)

        let reach = hypot(width, Self.height(days))
        var glows: [(CGRect, Color)] = []
        var dots: [(CGRect, Color)] = []
        for r in days.rows.indices {
            for c in days.columns.indices {
                let level = days.level(r, c)
                let weekRow = min(weeks.rows.count - 1, c * weeks.rows.count / days.columns.count)
                let weekColumn = min(weeks.columns.count - 1, r * weeks.columns.count / days.rows.count)
                let target = weeks.level(weekRow, weekColumn)
                let x0 = from.x(c), y0 = from.y(r)
                let x1 = to.x(weekColumn), y1 = to.y(weekRow)
                let r0 = level > 0 ? dotDay : dotEmpty
                let r1 = target > 0 ? dotWeek : dotEmpty
                let delay = hypot(x0, y0) / reach * stagger
                let k = Self.ease((morph * Self.morphFor - delay) / dotFor)
                let x = x0 + (x1 - x0) * k
                let y = y0 + (y1 - y0) * k
                let radius = r0 + (r1 - r0) * k
                let a = StressDotGridDonutLevel.rgb[level], b = StressDotGridDonutLevel.rgb[target]
                let rgb = (0..<3).map { a[$0] + (b[$0] - a[$0]) * k }
                // Only dots with data glow; an empty one fades its glow out as it fills.
                let lit = (r0 > dotEmpty ? 1 - k : 0) + (r1 > dotEmpty ? k : 0)
                if lit > 0 {
                    let g = radius + 1
                    glows.append((CGRect(x: x - g, y: y - g, width: g * 2, height: g * 2), StressDotGridDonutLevel.color(rgb, opacity: glowAlpha * lit)))
                }
                dots.append((CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2), StressDotGridDonutLevel.color(rgb)))
            }
        }
        // One blurred layer for every glow: drawn one at a time, 84 blurs a frame would not hold 60 fps.
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: glowBlur))
            for (rect, color) in glows { layer.fill(Path(ellipseIn: rect), with: .color(color)) }
        }
        for (rect, color) in dots { context.fill(Path(ellipseIn: rect), with: .color(color)) }

        let nx = from.x(days.now.column) + (to.x(weeks.now.column) - from.x(days.now.column)) * whole
        let ny = from.y(days.now.row) + (to.y(weeks.now.row) - from.y(days.now.row)) * whole
        context.stroke(
            Path(ellipseIn: CGRect(x: nx - nowRadius, y: ny - nowRadius, width: nowRadius * 2, height: nowRadius * 2)),
            with: .color(nowRing), lineWidth: 1.5
        )
    }
}
