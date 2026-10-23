import SwiftUI

/// The three stages of one switch, in seconds, sampled from a single clock.
enum BarsToLinesMorphMotion {
    static let hours = 24
    static let collapse = 0.25
    static let step = 0.008
    static let travel = 0.45
    static let second = 0.04
    static let grow = 0.25
    static let draw = 0.45
    static let labelIn = 0.2
    static let labelOut = 0.15
    static let aEnd = collapse + Double(hours - 1) * step
    static let bEnd = aEnd + travel + second
    static let cEnd = bEnd + max(grow + Double(hours - 1) * step, draw)
    static let total = cEnd + labelIn
    static let fade = 0.2

    struct BarsToLinesMorphLook {
        var barMax: Double
        var barShare: Double
        var barRadius: Double
        var dot: Double
        var gap: Double
    }

    struct BarsToLinesMorphScene {
        var bars = Path()
        var dots1 = Path()
        var dots2 = Path()
        var line1 = Path()
        var line2 = Path()
        var trim = 1.0
        var mix = 1.0
        var opacity = 1.0
        var lineCount = 0
        var above = 0.0
        var below = 0.0
    }

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    static func ease(_ value: Double) -> Double {
        let u = clamp01(value)
        return u * u * (3 - 2 * u)
    }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double { ease((t - from) / (to - from)) }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// Reanimated's entering and exiting text fades use quadratic ease-in-out by default.
    static func layoutFade(_ value: Double) -> Double {
        let u = clamp01(value)
        return u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u)
    }

    static func yOf(_ value: Double, _ plot: BarsToLinesMorphPlot, _ height: Double) -> Double {
        min(height + 40, max(-40, height * (1 - (value - plot.min) / (plot.max - plot.min))))
    }

    private static func line(_ values: [Double], _ plot: BarsToLinesMorphPlot, _ pitch: Double, _ height: Double) -> Path {
        Path { path in
            for i in 0..<hours {
                let point = CGPoint(x: (Double(i) + 0.5) * pitch, y: yOf(values[i], plot, height))
                if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
            }
        }
    }

    static func scene(_ p: Double, from: BarsToLinesMorphPlot, to: BarsToLinesMorphPlot, width: Double, height: Double, look: BarsToLinesMorphLook, still: Bool) -> BarsToLinesMorphScene {
        let pitch = width / Double(hours)
        let bw = min(look.barMax, pitch * look.barShare)
        var out = BarsToLinesMorphScene()
        var plot = to
        var stage = 3
        if still {
            plot = p < fade ? from : to
            out.opacity = p < fade ? 1 - p / fade : clamp01((p - fade) / fade)
            out.mix = p < fade ? 0 : 1
        } else if p < aEnd {
            plot = from; stage = 1; out.mix = 0
        } else if p < bEnd {
            stage = 2
        }
        out.lineCount = plot.lines
        if stage == 1 || stage == 3 {
            for i in 0..<hours {
                let x = (Double(i) + 0.5) * pitch
                let ya = yOf(plot.a[i], plot, height)
                let yb = yOf(plot.b[i], plot, height)
                if plot.bars {
                    let fold = still ? 0 : stage == 1 ? ease((p - Double(i) * step) / collapse) : 1 - ease((p - bEnd - Double(i) * step) / grow)
                    let centre = (ya + yb) / 2
                    let half = lerp(max(bw / 2, abs(ya - yb) / 2), bw / 2, fold)
                    let radius = lerp(look.barRadius, bw / 2, fold)
                    out.bars.addRoundedRect(in: CGRect(x: x - bw / 2, y: centre - half, width: bw, height: half * 2), cornerSize: CGSize(width: radius, height: radius))
                } else {
                    out.dots1.addEllipse(in: CGRect(x: x - look.dot, y: ya - look.dot, width: look.dot * 2, height: look.dot * 2))
                    if plot.lines == 2 {
                        out.dots2.addEllipse(in: CGRect(x: x - look.dot, y: yb - look.dot, width: look.dot * 2, height: look.dot * 2))
                    }
                }
            }
            if !plot.bars {
                out.line1 = line(plot.a, plot, pitch, height)
                if plot.lines == 2 { out.line2 = line(plot.b, plot, pitch, height) }
                if !still { out.trim = stage == 1 ? 1 - ease(p / aEnd) : ease((p - bEnd) / draw) }
            }
        } else {
            let e1 = ease((p - aEnd) / travel)
            let e2 = ease((p - aEnd - second) / travel)
            out.mix = e1
            out.trim = 0
            let r1 = lerp(from.bars ? bw / 2 : look.dot, to.bars ? bw / 2 : look.dot, e1)
            let r2 = lerp(from.bars ? bw / 2 : look.dot, to.bars ? bw / 2 : look.dot, e2)
            for i in 0..<hours {
                let x = (Double(i) + 0.5) * pitch
                let fa = yOf(from.a[i], from, height)
                let fb = yOf(from.b[i], from, height)
                let ta = yOf(to.a[i], to, height)
                let tb = yOf(to.b[i], to, height)
                let f1 = from.bars ? (fa + fb) / 2 : fa
                let f2 = from.bars ? (fa + fb) / 2 : fb
                let t1 = to.bars ? (ta + tb) / 2 : ta
                let t2 = to.bars ? (ta + tb) / 2 : tb
                let y1 = lerp(f1, t1, e1)
                let y2 = lerp(f2, t2, e2)
                out.dots1.addEllipse(in: CGRect(x: x - r1, y: y1 - r1, width: r1 * 2, height: r1 * 2))
                out.dots2.addEllipse(in: CGRect(x: x - r2, y: y2 - r2, width: r2 * 2, height: r2 * 2))
            }
        }
        let travelled = stage == 2 ? ease((p - aEnd) / travel) : stage == 1 ? 0 : 1
        out.above = lerp(yOf(from.above, from, height), yOf(to.above, to, height), still ? out.mix : travelled)
        out.below = lerp(yOf(from.below, from, height), yOf(to.below, to, height), still ? out.mix : travelled)
        return out
    }

    /// Bars leave a gap at the limit; lines only change colour there.
    static func limitClips(above: Double, below: Double, width: Double, height: Double, gap: Double) -> (red: Path, plain: Path) {
        var red = Path()
        var plain = Path()
        if above - gap > 0 { red.addRect(CGRect(x: 0, y: -50, width: width, height: above - gap + 50)) }
        if below + gap < height + 50 { red.addRect(CGRect(x: 0, y: below + gap, width: width, height: height + 50 - below - gap)) }
        let top = max(-50, above + gap)
        let bottom = min(height + 50, below - gap)
        if bottom > top { plain.addRect(CGRect(x: 0, y: top, width: width, height: bottom - top)) }
        return (red, plain)
    }

    /// The source requests RGB interpolation with gamma 1, so mix sRGB components directly.
    static func mixColor(_ from: Color, _ to: Color, _ t: Double) -> Color {
        var fr: CGFloat = 0, fg: CGFloat = 0, fb: CGFloat = 0, fa: CGFloat = 0
        var tr: CGFloat = 0, tg: CGFloat = 0, tb: CGFloat = 0, ta: CGFloat = 0
        UIColor(from).getRed(&fr, green: &fg, blue: &fb, alpha: &fa)
        UIColor(to).getRed(&tr, green: &tg, blue: &tb, alpha: &ta)
        return Color(.sRGB, red: lerp(fr, tr, t), green: lerp(fg, tg, t), blue: lerp(fb, tb, t), opacity: lerp(fa, ta, t))
    }
}
