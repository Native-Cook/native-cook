import SwiftUI

/// One column: its label under the axis, the tooltip's title, and grams of each macro.
struct StackedBarsGlassTooltipDay {
    var label: String
    var title: String
    var carbs: Int
    var fat: Int
    var protein: Int
}

struct StackedBarsGlassTooltipRange {
    var key: String
    /// The segmented control's label.
    var name: String
    /// The period, under the screen's title.
    var period: String
    /// Calories at the top of the axis.
    var max: Double
    /// The change against the period before, in percent.
    var change: Double
    var columns: [StackedBarsGlassTooltipDay]
}

/// The data of the macros screen and the drawing of its columns.
enum StackedBarsGlassTooltipMacros {
    typealias Day = StackedBarsGlassTooltipDay
    typealias Range = StackedBarsGlassTooltipRange

    // Calories in a gram of each macro.
    static let kcal = (carbs: 4.0, fat: 9.0, protein: 4.0)

    static func calories(_ day: Day) -> Double {
        Double(day.carbs) * kcal.carbs + Double(day.fat) * kcal.fat + Double(day.protein) * kcal.protein
    }

    private static let week: [Day] = [
        Day(label: "Mar 23", title: "Mar 23, 2020", carbs: 210, fat: 52, protein: 110),
        Day(label: "Mar 24", title: "Mar 24, 2020", carbs: 160, fat: 46, protein: 128),
        Day(label: "Mar 25", title: "Mar 25, 2020", carbs: 245, fat: 44, protein: 120),
        Day(label: "Mar 26", title: "Mar 26, 2020", carbs: 128, fat: 62, protein: 165),
        Day(label: "Mar 27", title: "Mar 27, 2020", carbs: 176, fat: 38, protein: 96),
        Day(label: "Mar 28", title: "Mar 28, 2020", carbs: 150, fat: 74, protein: 150),
        Day(label: "Mar 29", title: "Mar 29, 2020", carbs: 168, fat: 50, protein: 112),
    ]

    private static let day: [Day] = [
        Day(label: "7 AM", title: "Mar 29, 7 AM", carbs: 62, fat: 14, protein: 28),
        Day(label: "10 AM", title: "Mar 29, 10 AM", carbs: 24, fat: 9, protein: 12),
        Day(label: "1 PM", title: "Mar 29, 1 PM", carbs: 48, fat: 12, protein: 36),
        Day(label: "4 PM", title: "Mar 29, 4 PM", carbs: 18, fat: 6, protein: 6),
        Day(label: "7 PM", title: "Mar 29, 7 PM", carbs: 52, fat: 15, protein: 30),
    ]

    /// A fixed hash in [0, 1) of a day and a channel, so March looks the same on every run and stack.
    private static func hash(_ day: Int, _ channel: Int) -> Double {
        var h = UInt32(truncatingIfNeeded: Int64(day) * 374_761_393 + Int64(channel) * 668_265_263)
        h = (h ^ (h >> 13)) &* 1_274_126_177
        return Double(h ^ (h >> 16)) / 4_294_967_296
    }

    /// JavaScript's Math.round: halves go up.
    static func round(_ value: Double) -> Double { (value + 0.5).rounded(.down) }

    // March: the week above in its place, the other days drawn from the hash.
    private static let month: [Day] = (1...31).map { d in
        let label = d % 7 == 1 ? "Mar \(d)" : ""
        if d >= 23 && d <= 29 {
            var day = week[d - 23]
            day.label = label
            return day
        }
        return Day(
            label: label,
            title: "Mar \(d), 2020",
            carbs: Int(round(120 + 120 * hash(d, 1))),
            fat: Int(round(32 + 40 * hash(d, 2))),
            protein: Int(round(80 + 80 * hash(d, 3)))
        )
    }

    static let ranges: [Range] = [
        Range(key: "day", name: "Day", period: "Sunday, Mar 29, 2020", max: 750, change: 4.1, columns: day),
        Range(key: "week", name: "Week", period: "Mar 23 – Mar 29, 2020", max: 2500, change: 16.2, columns: week),
        Range(key: "month", name: "Month", period: "March 2020", max: 2500, change: 8.7, columns: month),
    ]

    /// A range as the canvas draws it: per column, its calories of protein, fat and carbs.
    struct Bars: Equatable {
        var kcal: [[Double]]
        var max: Double
    }

    static func bars(_ range: Range) -> Bars {
        Bars(
            kcal: range.columns.map { [Double($0.protein) * kcal.protein, Double($0.fat) * kcal.fat, Double($0.carbs) * kcal.carbs] },
            max: range.max
        )
    }

    /// Where the columns sit in a chart `width` wide: the pitch, and each column's width.
    static func columnLayout(_ count: Int, _ width: Double, _ widest: Double, _ share: Double) -> (pitch: Double, width: Double) {
        let pitch = width / Double(count)
        return (pitch, min(widest, pitch * share))
    }

    struct Paints {
        /// Bottom to top: protein, fat, carbs; each a gradient top to bottom.
        var colors: [[Color]]
        var gap: Double
        var radius: Double
        var lift: Double
        var dim: Double
        /// The colour under the chart, which a dimmed column fades toward.
        var card: Color
        var widest: Double
        var share: Double
    }

    private static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// The top of a stack: square corners at the base, `r` at the top.
    private static func topRounded(x: Double, w: Double, top: Double, base: Double, r: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x - w / 2, y: base))
        path.addLine(to: CGPoint(x: x - w / 2, y: top + r))
        path.addArc(tangent1End: CGPoint(x: x - w / 2, y: top), tangent2End: CGPoint(x: x, y: top), radius: r)
        path.addArc(tangent1End: CGPoint(x: x + w / 2, y: top), tangent2End: CGPoint(x: x + w / 2, y: top + r), radius: r)
        path.addLine(to: CGPoint(x: x + w / 2, y: base))
        path.closeSubpath()
        return path
    }

    /// The stacked columns. During a range switch every column of the larger set is drawn, and each
    /// starts (or ends) at the column of the smaller set it shares a place with, so seven wide columns
    /// split into thirty-one slim ones and gather back. `focusX` is the hovered column's centre; the
    /// others dim by `focus`, and the hovered one lifts.
    static func draw(
        _ c: GraphicsContext, from: Bars, to: Bars, morph: Double, focusX: Double, focus: Double,
        width: Double, height: Double, paints: Paints
    ) {
        let nFrom = from.kcal.count
        let nTo = to.kcal.count
        let many = Swift.max(nFrom, nTo)
        let layoutFrom = columnLayout(nFrom, width, paints.widest, paints.share)
        let layoutTo = columnLayout(nTo, width, paints.widest, paints.share)
        let pitchNow = lerp(layoutFrom.pitch, layoutTo.pitch, morph)
        let max = lerp(from.max, to.max, morph)
        let shaders = paints.colors.map {
            GraphicsContext.Shading.linearGradient(Gradient(colors: $0), startPoint: .zero, endPoint: CGPoint(x: 0, y: height))
        }
        for k in 0..<many {
            // This column's partner in each set: itself in the larger one, the nearest in the smaller.
            let a = nFrom == many ? k : Int(round(Double(k * (nFrom - 1)) / Double(Swift.max(1, many - 1))))
            let b = nTo == many ? k : Int(round(Double(k * (nTo - 1)) / Double(Swift.max(1, many - 1))))
            let x = lerp((Double(a) + 0.5) * layoutFrom.pitch, (Double(b) + 0.5) * layoutTo.pitch, morph)
            let w = lerp(layoutFrom.width, layoutTo.width, morph)
            let near = Swift.max(0, 1 - abs(x - focusX) / pitchNow)
            let lift = paints.lift * near * focus
            // Dimming washes a column toward the card's white, which reads the same as fading it.
            let dim = paints.dim * focus * (1 - near)
            var base = height - lift
            for s in 0..<3 {
                let kcal = lerp(from.kcal[a][s], to.kcal[b][s], morph)
                let h = kcal / max * height - (s > 0 ? paints.gap : 0)
                if h <= 0.5 { continue }
                if s > 0 { base -= paints.gap }
                let top = base - h
                // Only the top of the stack is rounded.
                let path = s == 2
                    ? topRounded(x: x, w: w, top: top, base: base, r: Swift.min(paints.radius, w / 2, h))
                    : Path(CGRect(x: x - w / 2, y: top, width: w, height: h))
                c.fill(path, with: shaders[s])
                if dim > 0.01 { c.fill(path, with: .color(paints.card.opacity(dim))) }
                base = top
            }
        }
    }

    /// The top of the stack under `x`, eased between neighbouring columns, in points from the bottom.
    static func stackTop(_ bars: Bars, x: Double, width: Double, height: Double) -> Double {
        let n = bars.kcal.count
        let f = Swift.min(Double(n - 1), Swift.max(0, x / (width / Double(n)) - 0.5))
        let i = Int(floor(f))
        let j = Swift.min(n - 1, i + 1)
        let total = { (k: Int) in bars.kcal[k][0] + bars.kcal[k][1] + bars.kcal[k][2] }
        return lerp(total(i), total(j), f - Double(i)) / bars.max * height
    }
}
