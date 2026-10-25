import SwiftUI
import UIKit

/*
 Stillpond's October: the data, and the lily pads baked once into a full-screen image, a port of
 the React Native rippleArt.ts.

 October 2026 starts on a Thursday; weeks start on Monday, so the grid is 7 × 5 and day d sits at
 index d + 2. Today is the 25th: the days after it are drawn as faint rings. A pad's colour
 deepens with the minutes practised (5 levels, pale sage to deep emerald); a day over an hour
 carries a small water-lily flower. Each pad has the classic notch cut toward its centre (at a
 seeded angle), fine radial veins, a lighter rim, and its day number. SF stands in for Helvetica
 Neue.
*/
enum RipplePondHeatmapData {
    static let firstCol = 3
    static let days = 31
    static let today = 25
    /// Minutes practised per day of October (up to today).
    static let minutes = [22, 35, 0, 48, 15, 30, 28, 0, 40, 62, 55, 12, 42, 38, 0, 25, 33, 55, 20, 44, 36, 50, 0, 65, 72]
    static let weekdays = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    static let focus = ["Breath focus", "Body scan", "Open awareness", "Walking", "Loving kindness"]

    static func sessionsOf(_ m: Int) -> Int { m == 0 ? 0 : max(1, Int((Double(m) / 16).rounded(.toNearestOrAwayFromZero))) }

    static func levelOf(_ m: Int) -> Int {
        if m <= 0 { return 0 }
        if m < 20 { return 1 }
        if m < 35 { return 2 }
        if m < 50 { return 3 }
        return 4
    }

    static func cellOf(_ day: Int) -> (Int, Int) {
        let i = day - 1 + firstCol
        return (i % 7, i / 7)
    }

    static func hm(_ min: Int) -> String {
        let h = min / 60
        let m = min % 60
        return h > 0 ? "\(h) h \(m) min" : "\(m) min"
    }

    static let total = minutes.reduce(0, +)
    static let sessions = minutes.reduce(0) { $0 + sessionsOf($1) }
    static let bestStreak: Int = {
        var best = 0
        var run = 0
        for m in minutes {
            run = m > 0 ? run + 1 : 0
            best = max(best, run)
        }
        return best
    }()
    static let summary = "\(hm(total)) · \(sessions) sessions · \(bestStreak)-day best streak"

    /// What the detail card says: -1 = the month, 1…31 = a day, 100 + row = a week.
    static func detailFor(_ id: Int) -> (title: String, sub: String) {
        if id >= 100 {
            let row = id - 100
            var min = 0, ses = 0, first = 0, last = 0
            for d in 1...today where cellOf(d).1 == row {
                if first == 0 { first = d }
                last = d
                min += minutes[d - 1]
                ses += sessionsOf(minutes[d - 1])
            }
            return ("Week \(row + 1) · \(hm(min)) · \(ses) sessions", "Oct \(first) – \(last) · ripples across the week")
        }
        if id >= 1 {
            let m = id <= minutes.count ? minutes[id - 1] : 0
            let wd = weekdays[cellOf(id).0]
            if id > today { return ("\(wd) \(id) · not yet", "Plan a sit for this day") }
            if m == 0 { return ("\(wd) \(id) · rest day", "No sessions logged") }
            let s = sessionsOf(m)
            return ("\(wd) \(id) · \(m) min · \(s) \(s == 1 ? "session" : "sessions")",
                    "Mostly \(focus[id % focus.count].lowercased())")
        }
        return ("October · \(hm(total))", "\(sessions) sessions · \(bestStreak)-day best streak · tap a day")
    }
}

enum RipplePondHeatmapArt {
    private static let padColors: [(UInt32, UInt32)] = [
        (0x9DB3A2, 0x7F978A),
        (0xA9CF8E, 0x86B06E),
        (0x6FB06A, 0x4F914F),
        (0x3E8C4E, 0x2D7040),
        (0x1F6A3C, 0x15502C),
    ]

    private static func ui(_ v: UInt32, _ a: Double = 1) -> UIColor {
        UIColor(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
                blue: Double(v & 0xFF) / 255, alpha: a)
    }

    private static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> UIColor {
        UIColor(red: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    private static func hash(_ n: Double) -> Double {
        let x = sin(n * 127.1) * 43758.5453
        return x - floor(x)
    }

    /// The pads at their places, baked into a w × h image. Safe off the main thread.
    static func bake(w: Double, h: Double, grid: [Double], scale: Double) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let r = min(grid[2], grid[3]) * 0.43
        return UIGraphicsImageRenderer(size: CGSize(width: max(1, w), height: max(1, h)), format: format).image { ctx in
            for d in 1...RipplePondHeatmapData.days {
                let (c, row) = RipplePondHeatmapData.cellOf(d)
                drawPad(ctx.cgContext, grid[0] + (Double(c) + 0.5) * grid[2], grid[1] + (Double(row) + 0.5) * grid[3], r, d)
            }
        }
    }

    private static func drawPad(_ c: CGContext, _ cx: Double, _ cy: Double, _ r: Double, _ day: Int) {
        typealias D = RipplePondHeatmapData
        let m = day <= D.today ? D.minutes[day - 1] : -1
        let lvl = m < 0 ? 0 : D.levelOf(m)
        let future = m < 0
        let size = future ? r * 0.82 : lvl == 0 ? r * 0.78 : r * (0.86 + 0.035 * Double(lvl))
        let notch = hash(Double(day) * 3.7) * .pi * 2
        let space = CGColorSpaceCreateDeviceRGB()
        if future {
            c.setStrokeColor(rgba(220, 240, 228, 0.32).cgColor)
            c.setLineWidth(1.2)
            c.strokeEllipse(in: CGRect(x: cx - size, y: cy - size, width: size * 2, height: size * 2))
        } else {
            // The pad: a disc with a narrow wedge cut to its centre.
            let half = 0.22
            let path = CGMutablePath()
            path.move(to: CGPoint(x: cx, y: cy))
            let steps = 48
            for i in 0...steps {
                let a = notch + half + Double(i) / Double(steps) * (.pi * 2 - 2 * half)
                path.addLine(to: CGPoint(x: cx + cos(a) * size, y: cy + sin(a) * size))
            }
            path.closeSubpath()
            let (c0, c1) = padColors[lvl]
            let alpha = lvl == 0 ? 0.62 : 1
            c.saveGState()
            c.addPath(path)
            c.clip()
            let fill = CGGradient(colorsSpace: space,
                                  colors: [ui(c0, alpha).cgColor, ui(c1, alpha).cgColor, ui(c1, alpha).cgColor] as CFArray,
                                  locations: [0, 0.75, 1])!
            let center = CGPoint(x: cx - size * 0.3, y: cy - size * 0.35)
            c.drawRadialGradient(fill, startCenter: center, startRadius: 0, endCenter: center, endRadius: size * 1.5,
                                 options: [.drawsAfterEndLocation])
            c.restoreGState()
            // Veins.
            c.setLineWidth(0.7)
            c.setLineCap(.round)
            c.setStrokeColor(lvl >= 3 ? rgba(170, 220, 170, 0.28).cgColor : rgba(40, 80, 50, 0.22).cgColor)
            for i in 1..<9 {
                let a = notch + 0.22 + Double(i) / 9 * (.pi * 2 - 0.44)
                c.move(to: CGPoint(x: cx + cos(a) * size * 0.18, y: cy + sin(a) * size * 0.18))
                c.addLine(to: CGPoint(x: cx + cos(a) * size * 0.88, y: cy + sin(a) * size * 0.88))
            }
            c.strokePath()
            // Rim: lighter on the upper left, darker below.
            c.saveGState()
            c.addPath(path)
            c.setLineWidth(1.2)
            c.replacePathWithStrokedPath()
            c.clip()
            let rim = CGGradient(colorsSpace: space,
                                 colors: [rgba(235, 255, 225, 0.55).cgColor, rgba(235, 255, 225, 0).cgColor,
                                          rgba(10, 40, 20, 0.35).cgColor] as CFArray,
                                 locations: [0, 0.5, 1])!
            c.drawLinearGradient(rim, start: CGPoint(x: cx - size, y: cy - size), end: CGPoint(x: cx + size, y: cy + size),
                                 options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            c.restoreGState()
            // A water lily on the best days.
            if m >= 60 {
                let fx = cx + cos(notch + .pi * 0.8) * size * 0.64
                let fy = cy + sin(notch + .pi * 0.8) * size * 0.64
                for i in 0..<7 {
                    let a = Double(i) / 7 * .pi * 2 + Double(day)
                    c.setFillColor(ui(i % 2 == 1 ? 0xF6C9D6 : 0xFBE3EA).cgColor)
                    c.fillEllipse(in: CGRect(x: fx + cos(a) * 3 - 2.5, y: fy + sin(a) * 3 - 2.5, width: 5, height: 5))
                }
                c.setFillColor(ui(0xF4C94E).cgColor)
                c.fillEllipse(in: CGRect(x: fx - 1.8, y: fy - 1.8, width: 3.6, height: 3.6))
            }
        }
        // The day number.
        let font = UIFont.systemFont(ofSize: 12, weight: .semibold)
        let text = "\(day)" as NSString
        let color = future ? rgba(220, 240, 228, 0.5) : lvl >= 3 ? rgba(236, 250, 236, 0.95) : rgba(18, 48, 30, 0.85)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let tw = text.size(withAttributes: attrs).width
        text.draw(at: CGPoint(x: cx - tw / 2, y: cy + 4.3 - font.ascender), withAttributes: attrs)
        if day == D.today {
            // A soft ring: the stroke with a glow of the same colour (Skia blurs it, σ 1.2).
            c.saveGState()
            let ring = rgba(250, 240, 200, 0.9).cgColor
            c.setShadow(offset: .zero, blur: 2.4, color: ring)
            c.setStrokeColor(ring)
            c.setLineWidth(1.6)
            let rr = size + 3.5
            c.strokeEllipse(in: CGRect(x: cx - rr, y: cy - rr, width: rr * 2, height: rr * 2))
            c.restoreGState()
        }
    }
}
