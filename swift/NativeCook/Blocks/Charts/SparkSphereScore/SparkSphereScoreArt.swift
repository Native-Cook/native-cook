import SwiftUI

/// Precomputed chrome and static typography. All coordinates are the RN layout's physical points.
struct SparkSphereScoreArt {
    private typealias R = SparkSphereScoreResources
    struct Label {
        var line: SparkSphereScoreResources.Line
        var x: Double, y: Double
        var color: Color
        var legend = 0
    }
    let g: SparkSphereScoreLayout
    let labels: [Label]
    let cardTitles: [SparkSphereScoreResources.Line]
    let cardBodies: [[SparkSphereScoreResources.Line]]
    let scoreFont: SparkSphereScoreResources.Glyphs
    let deltaFont: SparkSphereScoreResources.Glyphs
    let wordFont: SparkSphereScoreResources.Glyphs
    let cursorFont: SparkSphereScoreResources.Glyphs
    let unitFont: SparkSphereScoreResources.Glyphs
    let back: Path, previous: Path, next: Path, infoStem: Path, infoDot: Path, blob: Path

    init(_ g: SparkSphereScoreLayout) {
        self.g = g
        scoreFont = R.Glyphs(4, 44.3 * g.ts * g.sc)
        deltaFont = R.Glyphs(4, 16 * g.ts * g.sc)
        wordFont = R.Glyphs(3, 14.5 * g.ts * g.sc)
        cursorFont = R.Glyphs(4, 22 * g.s)
        unitFont = R.Glyphs(4, 22 * g.s * 0.78)
        var labels: [Label] = []
        func label(_ text: String, face: Int, size: Double, track: Double, base: Double,
                   x: Double? = nil, right: Double? = nil, color: UInt32, scale: Double? = nil, legend: Int = 0) {
            let sz = max(size * (scale ?? g.s), min(size, 11)) * g.fontScale
            let line = R.shaped(text, face, sz, track * sz)
            let xx = x ?? right.map { $0 - line.width } ?? (g.width - line.width) / 2
            labels.append(Label(line: line, x: xx, y: base, color: R.color(color), legend: legend))
        }
        label("LONGEVITY", face: 2, size: 13, track: 0.083, base: g.yA(79.2), color: 0xFDFEFE)
        label("Next update in 6 days", face: 0, size: 11, track: 0.005, base: g.yA(93.9), color: 0x8C8C8E)
        label("AUG 19  -  AUG 26", face: 2, size: 12, track: 0.091, base: g.yA(132.3), color: 0xFDFEFE)
        label("VITAL AGE", face: 2, size: 15.4, track: 0.094, base: g.centre(18.1), color: 0xC4C2C5, scale: g.ts * g.sc)
        label("PACE OF AGING", face: 2, size: 12, track: 0.082, base: g.yP(571.6), x: g.x(21.5), color: 0xFDFEFE)
        label("Slow", face: 0, size: 12, track: -0.01, base: g.yP(595.4), x: g.x(40), color: 0x93979A, legend: 1)
        label("Fast", face: 0, size: 12, track: -0.01, base: g.yP(595.4), right: g.x(360), color: 0x93979A, legend: 2)
        for (i, text) in ["-1.0x", "1.0x", "3.0x"].enumerated() {
            let size = max(10.5 * g.s, 10.5) * g.fontScale
            let line = R.shaped(text, 0, size)
            labels.append(Label(line: line, x: g.rulerX + Double(i) * 40 * g.pitch - line.width / 2,
                                y: g.yP(646.9), color: R.color(0x93979A)))
        }
        self.labels = labels
        let titleSize = max(15 * g.s, 11) * g.fontScale
        cardTitles = R.titles.map { R.shaped($0, 1, titleSize, -0.015 * titleSize) }
        cardBodies = R.bodies.map { R.bodyLines($0, size: 14.5 * g.s * g.fontScale, width: g.cardW - 2 * g.cardPad, count: g.lines) }
        func poly(_ points: [CGPoint]) -> Path {
            Path { p in p.addLines(points) }
        }
        let bx = g.x(27.7), by = g.yA(83.1), bw = 12.3 * g.s, bh = 23.1 * g.sc * 0.5
        back = poly([CGPoint(x: bx + bw, y: by - bh), CGPoint(x: bx, y: by), CGPoint(x: bx + bw, y: by + bh)])
        let dy = g.yA(127.8), dw = 7.5 * g.s, dh = 11.5 * g.sc * 0.5
        previous = poly([CGPoint(x: g.x(121.2) + dw, y: dy - dh), CGPoint(x: g.x(121.2), y: dy), CGPoint(x: g.x(121.2) + dw, y: dy + dh)])
        next = poly([CGPoint(x: g.x(279.6), y: dy - dh), CGPoint(x: g.x(279.6) + dw, y: dy), CGPoint(x: g.x(279.6), y: dy + dh)])
        infoStem = poly([CGPoint(x: g.x(365.8), y: g.yA(82.8) - 1.4 * g.s), CGPoint(x: g.x(365.8), y: g.yA(82.8) + 6 * g.s)])
        infoDot = poly([CGPoint(x: g.x(365.8), y: g.yA(82.8) - 6 * g.s), CGPoint(x: g.x(365.8), y: g.yA(82.8) - 5.9 * g.s)])
        blob = Path { p in
            for i in 0...28 {
                let a = Double(i) / 28 * .pi * 2
                let r = 1 + 0.16 * cos(2 * a + 0.6) + 0.1 * cos(3 * a + 1.4) + 0.05 * cos(5 * a)
                let point = CGPoint(x: g.x(374.3) + 13.1 * g.s / 2 * r * cos(a), y: g.yP(590.4) + 14.6 * g.sc / 2 * r * sin(a))
                if i == 0 { p.move(to: point) } else { p.addLine(to: point) }
            }
            p.closeSubpath()
        }
    }

    func draw(_ c: GraphicsContext, _ scene: SparkSphereScoreScene, slow: Double, fast: Double) {
        func stroke(_ path: Path, _ width: Double, _ color: UInt32) {
            c.stroke(path, with: .color(R.color(color)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
        }
        stroke(back, 2.4 * g.s, 0xFDFEFE)
        stroke(previous, 3 * g.s, 0xFDFEFE); stroke(next, 3 * g.s, 0x3F3F41)
        let infoD = 28.5 * g.s
        let circle = Path(ellipseIn: CGRect(x: g.x(365.8) - infoD / 2, y: g.yA(82.8) - infoD / 2, width: infoD, height: infoD))
        stroke(circle, 1.5 * g.s, 0x949496); stroke(infoStem, 2.4 * g.s, 0x949496); stroke(infoDot, 2.6 * g.s, 0x949496)
        let d = 14.6 * g.s
        c.fill(Path(ellipseIn: CGRect(x: g.x(28.1) - d / 2, y: g.yP(590.4) - d / 2, width: d, height: d)), with: .color(R.color(0x606467, slow)))
        c.fill(blob, with: .color(R.color(0x6E7275, fast)))
        for label in labels {
            R.draw(c, label.line, label.x, label.y, label.color, label.legend == 1 ? slow : label.legend == 2 ? fast : 1)
        }
        for i in 0..<4 {
            let a = scene.bandAlpha[i]
            R.draw(c, cardTitles[i], g.cardX + g.cardPad - 0.4, g.cardTitleBase, R.color(0xF9FBFB), a)
            for (j, line) in cardBodies[i].enumerated() {
                R.draw(c, line, g.cardX + g.cardPad, g.cardBodyBase + Double(j) * 20 * g.k * g.fontScale, R.color(0xC6CACD), a)
            }
        }
    }
}
