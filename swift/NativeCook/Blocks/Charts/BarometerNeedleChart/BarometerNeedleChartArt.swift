import CoreGraphics
import UIKit

/*
 The barometer's static art, baked once off the main thread: the navy chart-room background, the
 brass aneroid dial (bezel, ivory face, engraved scale, words, maker's mark) and the 48-hour chart
 card. A second bake holds what sits on the glass: the reflections and the brass set hand with its
 knob. A port of the React Native barometerArt.ts.
 */

struct BarometerNeedleChartGeo: Equatable {
    var w: CGFloat
    var h: CGFloat
    var top: CGFloat
    var cx: CGFloat
    var cy: CGFloat
    /// Bezel outer radius, face radius, the scale's outer ring.
    var rb: CGFloat
    var rf: CGFloat
    var rs: CGFloat
    var card: CGRect
    var plot: CGRect
    var statsTop: CGFloat
    var readoutBase: CGFloat

    static let plotMin = 980.0
    static let plotMax = 1030.0

    init(w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat) {
        let headerTop = top + 12
        let statsTop = h - max(bottom, 12) - 12 - 56
        let dialTop = headerTop + 104
        let minChart: CGFloat = 168
        let avail = statsTop - 12 - minChart - 20 - dialTop
        let d = max(220, min(w - 48, 330, avail))
        let rb = d / 2
        let cy = dialTop + rb
        let cardY = cy + rb + 20
        let card = CGRect(x: 16, y: cardY, width: w - 32, height: statsTop - 12 - cardY)
        self.w = w
        self.h = h
        self.top = top
        self.cx = w / 2
        self.cy = cy
        self.rb = rb
        self.rf = rb - 15
        self.rs = rb - 26
        self.card = card
        self.plot = CGRect(x: card.minX + 16, y: card.minY + 50, width: card.width - 16 - 50, height: card.height - 50 - 30)
        self.statsTop = statsTop
        self.readoutBase = headerTop + 66
    }

    func plotX(_ hour: Double) -> CGFloat {
        plot.minX + CGFloat((hour + BarometerNeedleChartMotion.span) / (2 * BarometerNeedleChartMotion.span)) * plot.width
    }

    func plotY(_ hpa: Double) -> CGFloat {
        plot.minY + CGFloat(1 - (hpa - Self.plotMin) / (Self.plotMax - Self.plotMin)) * plot.height
    }

    func hourAtX(_ x: CGFloat) -> Double {
        Double((x - plot.minX) / plot.width) * 2 * BarometerNeedleChartMotion.span - BarometerNeedleChartMotion.span
    }

    /// Point on the dial at angle a (degrees from twelve o'clock, clockwise) and radius r.
    func polar(_ a: Double, _ r: CGFloat) -> CGPoint {
        let rad = a * .pi / 180
        return CGPoint(x: cx + CGFloat(sin(rad)) * r, y: cy - CGFloat(cos(rad)) * r)
    }
}

enum BarometerNeedleChartArt {
    private typealias P = BarometerNeedleChartPaint
    private typealias M = BarometerNeedleChartMotion

    static let ink: UInt32 = 0x2B2620
    static let amber: UInt32 = 0xE6BB62
    static let sea: UInt32 = 0x9FC6C8

    static let kicker = P.sans(11, .bold)
    static let small = P.sans(13, .medium)
    static let scaleFont = P.serif(10)
    static let dialWord = P.serif(10)
    static let maker = P.serif(7.5)
    static let chartTitle = P.sans(15, .semibold)
    static let label = P.sans(11, .medium)

    /// Everything under the needle, the size of the screen.
    static func base(_ g: BarometerNeedleChartGeo, scale: CGFloat) -> UIImage {
        P.bake(CGSize(width: g.w, height: g.h), scale: scale) { c in
            background(c, g)
            P.spaced("SALTMARSH HARBOUR", x: 24, y: g.top + 24, kicker, 0xE9E0CC, 0.62, track: 2.2)
            P.text("Pressure · updated 13:52", x: 24, y: g.readoutBase + 24, small, 0xE9E0CC, 0.5)
            dial(c, g)
            card(c, g)
        }
    }

    /// What sits on and over the glass, in the dial's own box (origin at the bezel's top-left).
    static func glass(_ g: BarometerNeedleChartGeo, scale: CGFloat) -> UIImage {
        let setAngle = M.angleOf(M.nowHpa)
        return P.bake(CGSize(width: g.rb * 2, height: g.rb * 2), scale: scale) { c in
            c.translateBy(x: -(g.cx - g.rb), y: -(g.cy - g.rb))
            let cx = g.cx, cy = g.cy, rf = g.rf
            // Brass set hand, under the glass's reflections.
            c.saveGState()
            c.translateBy(x: cx, y: cy)
            c.rotate(by: CGFloat(setAngle * .pi / 180))
            P.fill(c, P.rrect(-1.6, -rf * 0.72 + 2, 3.2, rf * 0.72, 1.6), 0x000000, 0.3, blur: 2)
            let hand = CGMutablePath()
            hand.move(to: CGPoint(x: -2, y: 0))
            hand.addLine(to: CGPoint(x: -1.2, y: -rf * 0.6))
            hand.addLine(to: CGPoint(x: -5, y: -rf * 0.6))
            hand.addLine(to: CGPoint(x: 0, y: -rf * 0.74))
            hand.addLine(to: CGPoint(x: 5, y: -rf * 0.6))
            hand.addLine(to: CGPoint(x: 1.2, y: -rf * 0.6))
            hand.addLine(to: CGPoint(x: 2, y: 0))
            hand.closeSubpath()
            P.linear(c, hand, from: CGPoint(x: -4, y: 0), to: CGPoint(x: 4, y: 0),
                     [P.cg(0x8A6526), P.cg(0xF3D695), P.cg(0x9C7330)], [0, 0.5, 1])
            c.restoreGState()
            // Knurled set knob on the glass.
            P.fill(c, P.circle(cx, cy + 2, 13), 0x000000, 0.4, blur: 3)
            P.radial(c, P.circle(cx, cy, 13), center: CGPoint(x: cx - 4, y: cy - 5), radius: 16,
                     [P.cg(0xFBE6AE), P.cg(0xC89B4A), P.cg(0x6E4D1C)], [0, 0.55, 1])
            let knurl = CGMutablePath()
            for i in 0..<28 {
                let a = Double(i) * .pi * 2 / 28
                knurl.move(to: CGPoint(x: cx + CGFloat(cos(a)) * 9.5, y: cy + CGFloat(sin(a)) * 9.5))
                knurl.addLine(to: CGPoint(x: cx + CGFloat(cos(a)) * 12.6, y: cy + CGFloat(sin(a)) * 12.6))
            }
            P.stroke(c, knurl, 0x4A3312, width: 0.7, 0.45)
            P.radial(c, P.circle(cx, cy, 8.5), center: CGPoint(x: cx - 2, y: cy - 3), radius: 10,
                     [P.cg(0xFFF3CF), P.cg(0xD4A85A), P.cg(0x8A6526)], [0, 0.6, 1])
            // Glass: a crescent highlight top-left, a broad soft glare, a bright rim and a darker lower rim.
            c.saveGState()
            c.addPath(P.circle(cx, cy, rf))
            c.clip()
            let crescent = CGMutablePath()
            crescent.addPath(P.circle(cx, cy, rf - 3))
            crescent.addPath(P.circle(cx + rf * 0.12, cy + rf * 0.14, rf * 0.98))
            P.linear(c, crescent, from: CGPoint(x: cx - rf, y: cy - rf), to: CGPoint(x: cx, y: cy),
                     [P.cg(0xFFFFFF, 0.42), P.cg(0xFFFFFF, 0.12), P.cg(0xFFFFFF, 0)], [0, 0.45, 1], evenOdd: true)
            P.linear(c, P.rect(cx - rf, cy - rf, rf * 2, rf * 2),
                     from: CGPoint(x: cx - rf * 0.6, y: cy - rf), to: CGPoint(x: cx + rf * 0.3, y: cy + rf * 0.6),
                     [P.cg(0xFFFFFF, 0), P.cg(0xFFFFFF, 0), P.cg(0xFFFFFF, 0.09), P.cg(0xFFFFFF, 0.02), P.cg(0xFFFFFF, 0), P.cg(0xFFFFFF, 0)],
                     [0, 0.25, 0.42, 0.52, 0.62, 1])
            P.radial(c, P.circle(cx, cy, rf), center: CGPoint(x: cx, y: cy), radius: rf,
                     [P.cg(0x0B1A20, 0), P.cg(0x0B1A20, 0), P.cg(0x0B1A20, 0.18)], [0, 0.9, 1])
            c.restoreGState()
            P.stroke(c, P.circle(cx, cy, rf - 1), 0xFFFFFF, width: 1.2, 0.22)
        }
    }

    private static func background(_ c: CGContext, _ g: BarometerNeedleChartGeo) {
        let all = P.rect(0, 0, g.w, g.h)
        P.linear(c, all, from: .zero, to: CGPoint(x: 0, y: g.h),
                 [P.cg(0x13232B), P.cg(0x0F1C24), P.cg(0x0A1318)], [0, 0.5, 1])
        // A warm lamp over the dial and a cool vignette at the corners.
        P.radial(c, all, center: CGPoint(x: g.cx, y: g.cy - g.rb * 0.4), radius: g.w * 0.95,
                 [P.cg(0x6B5530, 0.22), P.cg(0x3A3A2C, 0.08), P.cg(0x000000, 0)], [0, 0.5, 1])
        P.radial(c, all, center: CGPoint(x: g.cx, y: g.h * 0.45), radius: g.h * 0.75,
                 [P.cg(0x000000, 0), P.cg(0x02070A, 0.1), P.cg(0x02070A, 0.55)], [0, 0.6, 1])
        P.grain(c, CGRect(x: 0, y: 0, width: g.w, height: g.h), alpha: 0.07, seed: 3)
    }

    /// Text centred on angle a along a circle of radius r, tops outward.
    private static func arcText(_ c: CGContext, _ g: BarometerNeedleChartGeo, _ str: String, _ a: Double, _ r: CGFloat,
                                _ font: UIFont, _ hex: UInt32, _ alpha: CGFloat, track: CGFloat) {
        let chars = str.map { String($0) }
        let widths = chars.map { P.width($0, font) }
        var total: CGFloat = 0
        for (i, w) in widths.enumerated() { total += w + (i < widths.count - 1 ? track : 0) }
        var s = -total / 2
        for (i, ch) in chars.enumerated() {
            let mid = s + widths[i] / 2
            let ang = a + Double(mid / r) * 180 / .pi
            c.saveGState()
            c.translateBy(x: g.cx, y: g.cy)
            c.rotate(by: CGFloat(ang * .pi / 180))
            P.text(ch, x: -widths[i] / 2, y: -r, font, hex, alpha)
            c.restoreGState()
            s += widths[i] + track
        }
    }

    private static func arc(_ g: BarometerNeedleChartGeo, _ r: CGFloat, from a: Double, to b: Double) -> CGPath {
        UIBezierPath(
            arcCenter: CGPoint(x: g.cx, y: g.cy), radius: r,
            startAngle: CGFloat((a - 90) * .pi / 180), endAngle: CGFloat((b - 90) * .pi / 180), clockwise: true
        ).cgPath
    }

    private static func dial(_ c: CGContext, _ g: BarometerNeedleChartGeo) {
        let cx = g.cx, cy = g.cy, rb = g.rb, rf = g.rf, rs = g.rs
        // Two shadows: soft ambient and a tight contact one.
        P.fill(c, P.circle(cx, cy + 16, rb), 0x000000, 0.45, blur: 22)
        P.fill(c, P.circle(cx, cy + 2, rb), 0x000000, 0.55, blur: 3)
        // Brass bezel: a sweep of light and dark bands reads as turned metal (thin wedges).
        let colors: [UInt32] = [0x7A5622, 0xF4DC9F, 0xA6782F, 0x5E4119, 0xD9B56C, 0xFBE7B5, 0x8C6428, 0x7A5622]
        let stops: [Double] = [0, 0.12, 0.27, 0.42, 0.58, 0.7, 0.86, 1]
        c.saveGState()
        c.addPath(P.circle(cx, cy, rb))
        c.clip()
        let wedges = 360
        for k in 0..<wedges {
            let u = (Double(k) + 0.5) / Double(wedges)
            var j = 0
            while j < stops.count - 2 && u > stops[j + 1] { j += 1 }
            let f = (u - stops[j]) / (stops[j + 1] - stops[j])
            let ca = colors[j], cb = colors[j + 1]
            func ch(_ shift: UInt32) -> CGFloat {
                let a = Double((ca >> shift) & 0xff), b = Double((cb >> shift) & 0xff)
                return CGFloat((a + (b - a) * f) / 255)
            }
            c.setFillColor(CGColor(srgbRed: ch(16), green: ch(8), blue: ch(0), alpha: 1))
            let a0 = Double(k) / Double(wedges) * 2 * .pi - 0.004
            let a1 = Double(k + 1) / Double(wedges) * 2 * .pi + 0.004
            let p = CGMutablePath()
            p.move(to: CGPoint(x: cx, y: cy))
            p.addLine(to: CGPoint(x: cx + CGFloat(cos(a0)) * (rb + 2), y: cy + CGFloat(sin(a0)) * (rb + 2)))
            p.addLine(to: CGPoint(x: cx + CGFloat(cos(a1)) * (rb + 2), y: cy + CGFloat(sin(a1)) * (rb + 2)))
            p.closeSubpath()
            c.addPath(p)
            c.fillPath()
        }
        c.restoreGState()
        P.radial(c, P.circle(cx, cy, rb), center: CGPoint(x: cx, y: cy - rb * 0.3), radius: rb * 1.3,
                 [P.cg(0xFFFFFF, 0.18), P.cg(0xFFFFFF, 0), P.cg(0x2A1A06, 0.35)], [0, 0.5, 1])
        P.stroke(c, P.circle(cx, cy, rb - 0.6), 0xFFF1C8, width: 1.2, 0.55)
        P.stroke(c, P.circle(cx, cy, rf + 4), 0x4A3312, width: 2.5, 0.8)
        P.stroke(c, P.circle(cx, cy, rf + 1.4), 0xF6DFA4, width: 1, 0.7)
        // Ivory face with a printed-paper grain.
        P.radial(c, P.circle(cx, cy, rf), center: CGPoint(x: cx - rf * 0.15, y: cy - rf * 0.2), radius: rf * 1.1,
                 [P.cg(0xF6EEDB), P.cg(0xECE1C8), P.cg(0xD8C7A3)], [0, 0.62, 1])
        c.saveGState()
        c.addPath(P.circle(cx, cy, rf))
        c.clip()
        P.grain(c, CGRect(x: cx - rf, y: cy - rf, width: rf * 2, height: rf * 2), alpha: 0.16, seed: 11)
        // The bezel's shadow falls on the face.
        P.radial(c, P.circle(cx, cy, rf), center: CGPoint(x: cx, y: cy + 4), radius: rf,
                 [P.cg(0x3A2A12, 0), P.cg(0x3A2A12, 0), P.cg(0x3A2A12, 0.32)], [0, 0.86, 1])
        c.restoreGState()

        // Coloured zone band just inside the scale.
        let zones: [(Double, Double, UInt32)] = [
            (M.scaleMin, 990, 0x8E3B2E), (990, 1005, 0x3E5A73), (1005, 1018, 0x7D7158),
            (1018, 1035, 0xA47B2C), (1035, M.scaleMax, 0xB5622A),
        ]
        for (a, b, col) in zones {
            P.stroke(c, arc(g, rs - 3, from: M.angleOf(a), to: M.angleOf(b)), col, width: 4, 0.55)
        }
        // The scale: two engraved rings and ticks every hPa, longer every 5 and 10.
        let a0 = M.angleOf(M.scaleMin), a1 = M.angleOf(M.scaleMax)
        P.stroke(c, arc(g, rs, from: a0, to: a1), ink, width: 1.1, 0.9)
        P.stroke(c, arc(g, rs - 6, from: a0, to: a1), ink, width: 0.6, 0.6)
        let ticks = CGMutablePath()
        let majors = CGMutablePath()
        for v in Int(M.scaleMin)...Int(M.scaleMax) {
            let a = M.angleOf(Double(v))
            let len: CGFloat = v % 10 == 0 ? 13 : v % 5 == 0 ? 9 : 5
            let p0 = g.polar(a, rs)
            let p1 = g.polar(a, rs - len)
            let target = v % 10 == 0 ? majors : ticks
            target.move(to: p0)
            target.addLine(to: p1)
        }
        P.stroke(c, ticks, ink, width: 0.7, 0.8)
        P.stroke(c, majors, ink, width: 1.6, 0.95)
        for v in stride(from: Int(M.scaleMin), through: Int(M.scaleMax), by: 10) {
            arcText(c, g, "\(v)", M.angleOf(Double(v)), rs - 26, scaleFont, ink, 0.92, track: 0.2)
        }
        for (i, word) in M.words.enumerated() {
            arcText(c, g, word.uppercased(), M.angleOf(M.wordAt[i]), rs - 48, dialWord, 0x3A2D22, 0.95, track: 1.6)
        }
        // Maker's mark and a guilloché rosette around the arbor.
        P.spaced("SALTMARSH & CO.", x: cx, y: cy + rf * 0.36, maker, 0x4A3B2C, 0.85, track: 1.6, align: 0.5)
        P.spaced("ANEROID · HPA", x: cx, y: cy + rf * 0.36 + 13, maker, 0x4A3B2C, 0.55, track: 1.4, align: 0.5)
        let rosette = CGMutablePath()
        for i in 0..<24 {
            let p = g.polar(Double(i) * 360 / 24, rf * 0.11)
            rosette.addPath(P.circle(p.x, p.y, rf * 0.075))
        }
        P.stroke(c, rosette, 0x6B5636, width: 0.5, 0.35)
    }

    private static func card(_ c: CGContext, _ g: BarometerNeedleChartGeo) {
        let card = g.card, plot = g.plot
        let rr = P.rrect(card.minX, card.minY, card.width, card.height, 22)
        P.fill(c, P.rrect(card.minX, card.minY + 10, card.width, card.height, 22), 0x000000, 0.35, blur: 18)
        P.linear(c, rr, from: CGPoint(x: 0, y: card.minY), to: CGPoint(x: 0, y: card.maxY),
                 [P.cg(0x1B2E36), P.cg(0x15252C), P.cg(0x101D23)], [0, 0.5, 1])
        P.stroke(c, rr, 0xFFFFFF, width: 1, 0.07)
        P.stroke(c, P.line(card.minX + 22, card.minY + 0.8, card.maxX - 22, card.minY + 0.8), 0xFFFFFF, width: 1, 0.1)
        P.text("48-hour pressure", x: card.minX + 16, y: card.minY + 28, chartTitle, 0xF1EAD9)
        // Legend: past (solid) and forecast (dashed).
        let lx = card.maxX - 16
        let fw = P.width("Forecast", label)
        P.text("Forecast", x: lx - fw, y: card.minY + 27, label, 0xD7D0BF, 0.6)
        P.stroke(c, P.line(lx - fw - 24, card.minY + 23, lx - fw - 8, card.minY + 23), sea, width: 2, 0.9, dash: [4, 3])
        let pw = P.width("Past", label)
        let px = lx - fw - 36 - pw
        P.text("Past", x: px, y: card.minY + 27, label, 0xD7D0BF, 0.6)
        P.stroke(c, P.line(px - 22, card.minY + 23, px - 8, card.minY + 23), amber, width: 2.2)

        // Grid and the right-hand scale.
        for v in stride(from: 990, through: 1030, by: 10) {
            let y = g.plotY(Double(v))
            P.stroke(c, P.line(plot.minX, y, plot.maxX, y), 0xFFFFFF, width: 1, 0.06, dash: [2, 4])
            P.text("\(v)", x: plot.maxX + 10, y: y + 4, label, 0xD7D0BF, 0.45)
        }
        // The lines: area under the past, an uncertainty fan over the forecast.
        let step = 0.5
        let span = M.span
        let past = CGMutablePath()
        let pastArea = CGMutablePath()
        pastArea.move(to: CGPoint(x: g.plotX(-span), y: plot.maxY))
        var hr = -span
        while hr <= 0.001 {
            let p = CGPoint(x: g.plotX(hr), y: g.plotY(M.pressureAt(hr)))
            if hr == -span { past.move(to: p) } else { past.addLine(to: p) }
            pastArea.addLine(to: p)
            hr += step
        }
        pastArea.addLine(to: CGPoint(x: g.plotX(0), y: plot.maxY))
        pastArea.closeSubpath()
        P.linear(c, pastArea, from: CGPoint(x: 0, y: plot.minY), to: CGPoint(x: 0, y: plot.maxY),
                 [P.cg(amber, 0.3), P.cg(0xB98A3C, 0.1), P.cg(amber, 0)], [0, 0.55, 1])
        let fan = CGMutablePath()
        hr = 0
        while hr <= span + 0.001 {
            let p = CGPoint(x: g.plotX(hr), y: g.plotY(M.pressureAt(hr) + hr / span * 5))
            if hr == 0 { fan.move(to: p) } else { fan.addLine(to: p) }
            hr += step
        }
        hr = span
        while hr >= -0.001 {
            fan.addLine(to: CGPoint(x: g.plotX(hr), y: g.plotY(M.pressureAt(hr) - hr / span * 5)))
            hr -= step
        }
        fan.closeSubpath()
        P.fill(c, fan, sea, 0.1)
        let future = CGMutablePath()
        hr = 0
        while hr <= span + 0.001 {
            let p = CGPoint(x: g.plotX(hr), y: g.plotY(M.pressureAt(hr)))
            if hr == 0 { future.move(to: p) } else { future.addLine(to: p) }
            hr += step
        }
        P.stroke(c, past, amber, width: 4, 0.25, blur: 3)
        P.stroke(c, past, amber, width: 2.2)
        P.stroke(c, future, sea, width: 2, 0.95, dash: [5, 4])
        // Storm note at the low, and the now line.
        let lowX = g.plotX(-14)
        let lowY = g.plotY(985)
        P.fill(c, P.circle(lowX, lowY, 2.5), 0xE0775C)
        let gw = P.width("Gale 985", label)
        P.text("Gale 985", x: lowX - gw / 2, y: lowY + 18, label, 0xE0775C, 0.85)
        P.stroke(c, P.line(g.plotX(0), plot.minY - 6, g.plotX(0), plot.maxY), 0xFFFFFF, width: 1, 0.22, dash: [1, 3])
        let labels = ["−24 h", "−12 h", "Now", "+12 h", "+24 h"]
        for (i, l) in labels.enumerated() {
            let x = g.plotX(-span + Double(i) * 12)
            let lw = P.width(l, label)
            let ax = i == 0 ? x : i == labels.count - 1 ? x - lw : x - lw / 2
            P.text(l, x: ax, y: plot.maxY + 20, label, 0xD7D0BF, i == 2 ? 0.8 : 0.45)
        }
    }
}
