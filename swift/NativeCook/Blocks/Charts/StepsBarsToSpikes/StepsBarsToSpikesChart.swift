import SwiftUI

/// The bars of the blue field, drawn in one canvas: they sink and rise again on every switch.
enum StepsBarsToSpikesChart {
    // A bar fades from its top into the blue; the bar that is now, or the one under the finger, is solid at the top.
    private static let bar = Gradient(colors: [Color(.sRGB, white: 1, opacity: 0.42), Color(.sRGB, white: 1, opacity: 0.03)])
    private static let lit = Gradient(stops: [
        .init(color: Color(.sRGB, red: 1, green: 1, blue: 1, opacity: 1), location: 0),
        .init(color: Color(.sRGB, red: 0.78, green: 0.76, blue: 1, opacity: 0.75), location: 0.3),
        .init(color: Color(.sRGB, red: 0.6, green: 0.58, blue: 1, opacity: 0.08), location: 1),
    ])

    // Bars are this share of their column, between these widths; a day of a month is a hairline.
    private static let barShare = 0.62
    private static let barMax = 30.0
    private static let spike = 3.0
    private static let radius = 7.0

    // Motion, in seconds from a switch: the old bars sink together, then the new ones rise one after another.
    static let sinkFor = 0.3
    static let riseFor = 0.45
    private static let riseGapBars = 0.04
    private static let riseGapSpikes = 0.012
    static let switchFor = sinkFor + riseFor + 0.4

    struct Box {
        var left: Double
        var width: Double
        var base: Double
        var height: Double
    }

    private struct Bar {
        var x: Double
        var width: Double
        var height: Double
        var delay: Double
    }

    /// Where each bar of a view stands: its centre, width and full height.
    private static func bars(_ view: StepsBarsToSpikesView, _ box: Box) -> [Bar] {
        let step = box.width / Double(view.values.count)
        let spiky = view.values.count > 7
        let width = spiky ? spike : min(barMax, step * barShare)
        return view.values.enumerated().map { i, value in
            Bar(
                x: box.left + step * (Double(i) + 0.5),
                width: width,
                height: min(value, view.max) / view.max * box.height,
                delay: sinkFor + Double(i) * (spiky ? riseGapSpikes : riseGapBars)
            )
        }
    }

    private static func easeIn(_ t: Double) -> Double {
        let x = min(1, max(0, t))
        return x * x * x
    }

    private static func easeOut(_ t: Double) -> Double {
        let x = min(1, max(0, t))
        return 1 - pow(1 - x, 3)
    }

    /// Draws the views switched `from` and `to`, `since` seconds after the switch, lighting `picked` (or -1).
    static func draw(_ c: GraphicsContext, box: Box, views: [StepsBarsToSpikesView], from: Int, to: Int, since: Double, picked: Int) {
        func draw(_ bars: [Bar], lit litIndex: Int, grown: (Int) -> Double) {
            for (i, bar) in bars.enumerated() {
                let h = bar.height * grown(i)
                if h < 0.5 { continue }
                let top = box.base - h
                let rect = CGRect(x: bar.x - bar.width / 2, y: top, width: bar.width, height: h)
                let r = min(bar.width / 2, radius)
                let shading = GraphicsContext.Shading.linearGradient(
                    i == litIndex ? lit : self.bar, startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: box.base))
                c.fill(Path(roundedRect: rect, cornerRadius: r, style: .circular), with: shading)
            }
        }
        if from != to && since < sinkFor {
            let left = 1 - easeIn(since / sinkFor)
            draw(bars(views[from], box), lit: views[from].current) { _ in left }
        }
        let rising = bars(views[to], box)
        draw(rising, lit: picked >= 0 ? picked : views[to].current) { i in
            from == to ? 1 : easeOut((since - rising[i].delay) / riseFor)
        }
    }
}
