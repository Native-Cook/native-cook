import SwiftUI

struct LineToCandlesSwitchRange: Equatable { var label: String; var prices: [Double]; var start: Double; var end: Double; var hours: Bool
    static let samples = [
        Self(label: "1D", prices: [196.4, 196.57, 196.23, 196.13, 196.02, 196.05, 196.14, 195.89, 196.29, 196.07, 196.06, 196.06, 195.82, 195.95, 195.98, 195.76, 195.83, 195.62, 195.62, 195.69, 195.89, 196.02, 195.87, 195.91, 196.22, 196.07, 196.41, 196.4, 196.41, 196.57, 196.45, 196.78, 196.91, 197.12, 196.8, 197.12, 196.89, 196.83, 196.9, 196.63, 196.88, 196.68, 196.79, 196.56, 196.93, 196.85, 196.86, 196.55, 196.5, 196.83, 196.85, 196.73, 197.18, 197.37, 196.99, 197.33, 197.63, 197.63, 197.66, 197.97, 197.72, 198.05, 197.95, 198.1, 198.31, 198.07, 198.28, 198.43, 198.51, 198.49, 198.04, 198.18, 198.21, 198.32, 198.08, 197.86, 198.01, 197.98, 197.7, 197.96, 197.81, 197.5, 197.98, 197.77, 198.15, 198.22, 198.07, 197.99, 198.06, 198.11, 198.25, 198.61, 198.42, 198.89, 199.0, 198.87], start: 1744574400000.0, end: 1744660800000.0, hours: true),
        Self(label: "1W", prices: [181.48, 181.86, 182.24, 183.31, 183.73, 182.9, 182.78, 183.54, 183.51, 183.44, 183.67, 182.93, 183.93, 183.3, 183.54, 183.82, 183.74, 183.63, 183.95, 184.19, 190.53, 190.57, 191.17, 190.59, 190.61, 190.36, 190.22, 190.48, 190.07, 190.28, 190.27, 190.27, 190.07, 190.51, 190.49, 190.09, 190.33, 190.1, 189.96, 190.09, 190.44, 189.72, 189.46, 189.97, 189.81, 189.41, 189.5, 190.3, 190.07, 189.97, 190.36, 190.54, 191.16, 190.45, 191.32, 191.08, 191.04, 191.39, 192.03, 193.82, 194.03, 194.9, 195.6, 195.6, 195.61, 195.65, 195.48, 195.41, 196.03, 196.14, 196.65, 196.52, 198.74, 199.76, 199.85, 199.19, 199.8, 198.35, 198.45, 197.03, 197.47, 197.27, 198.16, 199.62, 199.21, 199.43, 199.17, 199.34, 199.16, 199.48, 198.75, 199.59, 199.08, 199.42, 199.44, 198.87], start: 1744056000000.0, end: 1744660800000.0, hours: true),
        Self(label: "1M", prices: [172.15, 171.22, 169.24, 168.65, 168.62, 170.49, 167.73, 167.26, 166.02, 166.86, 166.45, 165.4, 165.28, 164.5, 163.5, 163.17, 162.85, 165.46, 164.99, 166.83, 167.85, 167.27, 166.96, 168.83, 167.24, 169.89, 167.83, 168.83, 168.68, 171.66, 176.87, 180.72, 180.65, 179.21, 181.17, 178.2, 180.53, 179.69, 178.24, 178.59, 179.07, 177.88, 177.09, 176.42, 178.55, 178.44, 176.55, 177.11, 177.99, 176.81, 178.58, 177.55, 178.47, 180.68, 178.7, 182.32, 180.79, 181.52, 183.05, 181.95, 182.26, 183.24, 182.84, 185.02, 184.72, 182.54, 182.26, 181.78, 183.19, 184.76, 185.41, 189.1, 189.77, 190.68, 191.71, 193.86, 193.87, 193.85, 193.38, 193.88, 193.74, 194.39, 191.36, 192.69, 190.92, 191.82, 191.28, 192.24, 192.53, 193.62, 196.21, 197.48, 197.31, 197.09, 196.34, 198.87], start: 1742068800000.0, end: 1744660800000.0, hours: true),
        Self(label: "1Y", prices: [120.3, 122.14, 123.93, 124.1, 120.3, 123.41, 123.68, 122.66, 128.93, 128.89, 128.66, 127.17, 130.55, 130.26, 130.01, 132.66, 129.05, 134.09, 135.12, 136.39, 131.79, 134.68, 133.4, 132.04, 131.27, 131.28, 128.36, 127.25, 122.4, 123.62, 125.28, 130.29, 129.17, 130.78, 134.96, 136.43, 140.21, 140.77, 142.49, 144.43, 142.48, 144.44, 146.0, 144.99, 148.98, 142.66, 144.26, 140.95, 142.6, 144.45, 144.04, 141.01, 141.08, 140.49, 141.45, 141.87, 149.04, 149.49, 152.97, 153.96, 152.38, 157.76, 157.17, 162.1, 163.57, 166.32, 165.36, 165.19, 167.42, 162.3, 164.91, 161.36, 163.17, 157.66, 161.98, 156.05, 159.47, 159.68, 164.46, 163.73, 165.81, 168.1, 169.32, 170.13, 175.45, 176.75, 179.27, 181.37, 183.97, 183.68, 188.16, 192.54, 192.07, 194.19, 198.63, 198.87], start: 1713124800000.0, end: 1744660800000.0, hours: false),
        Self(label: "ALL", prices: [4.12, 4.05, 5.38, 4.97, 5.18, 3.25, 3.8, 4.7, 4.29, 4.89, 3.72, 5.29, 4.77, 4.72, 3.94, 5.77, 5.27, 4.92, 4.37, 4.7, 4.7, 5.36, 6.95, 4.8, 7.07, 5.93, 6.8, 5.56, 5.28, 5.59, 5.19, 7.33, 7.16, 6.57, 5.54, 6.51, 8.35, 7.64, 6.36, 7.96, 7.66, 7.14, 9.01, 7.52, 8.1, 8.95, 7.83, 7.35, 8.97, 7.73, 10.39, 10.15, 9.12, 9.74, 10.42, 10.79, 12.92, 11.77, 11.96, 11.51, 11.87, 13.98, 14.48, 14.24, 14.41, 14.13, 15.21, 15.13, 17.65, 19.8, 19.18, 22.27, 23.11, 24.11, 23.75, 26.05, 28.0, 32.85, 37.67, 40.4, 45.21, 49.88, 54.98, 59.34, 67.58, 78.12, 86.2, 98.54, 108.83, 108.22, 106.26, 100.82, 121.53, 148.35, 173.97, 198.87], start: 1429300800000.0, end: 1744660800000.0, hours: false),
    ]
}

struct LineToCandlesSwitchFrame {
    var o: [Double], h: [Double], l: [Double], c: [Double], x: [Double], w: [Double], line: [Double]
    var low: Double, high: Double, lineAlpha: Double, candles: Double
}
struct LineToCandlesSwitchChartView { var label: String; var group: Int }
struct LineToCandlesSwitchStep { var at: Double; var range: Int; var view: Int; var menu: Int }
enum LineToCandlesSwitchMotion {
    static let points = 96, hour = 3600000.0, day = 24 * hour, now = 1744660800000.0, nowPrice = 198.87, body = 0.62
    static let views = [LineToCandlesSwitchChartView(label: "Line", group: 0), .init(label: "30m", group: 1), .init(label: "1h", group: 2), .init(label: "4h", group: 4)]
    static let loop = 10.0, pressAt = 1.7, sweepTo = 3.2, backTo = 3.8, liftAt = 4.0, pressX = 0.12, turnX = 0.94, restX = 0.55, heldFor = 0.2, userHold = 3.0, fade = 0.2, spread = 0.3, grow = 0.4, move = 0.5
    static let steps = [
        LineToCandlesSwitchStep(at: 0.0, range: 1, view: 0, menu: -1),
        LineToCandlesSwitchStep(at: 0.8, range: 2, view: 0, menu: -1),
        LineToCandlesSwitchStep(at: 4.3, range: 2, view: 0, menu: 4),
        LineToCandlesSwitchStep(at: 4.6, range: 2, view: 0, menu: 1),
        LineToCandlesSwitchStep(at: 4.75, range: 2, view: 1, menu: -1),
        LineToCandlesSwitchStep(at: 6.0, range: 2, view: 1, menu: 4),
        LineToCandlesSwitchStep(at: 6.3, range: 2, view: 1, menu: 3),
        LineToCandlesSwitchStep(at: 6.45, range: 2, view: 3, menu: -1),
        LineToCandlesSwitchStep(at: 7.3, range: 2, view: 3, menu: 4),
        LineToCandlesSwitchStep(at: 7.6, range: 2, view: 3, menu: 0),
        LineToCandlesSwitchStep(at: 7.75, range: 2, view: 0, menu: -1),
        LineToCandlesSwitchStep(at: 8.9, range: 1, view: 0, menu: -1)
    ]
    static func clamp(_ t: Double) -> Double { min(1, max(0, t)) }
    static func ease(_ t: Double) -> Double { let u = clamp(t); return u < 0.5 ? 4 * pow(u, 3) : 1 - pow(-2 * u + 2, 3) / 2 }
    static func scrubAt(_ t: Double) -> (x: Double, held: Double, down: Double) {
        var x = pressX
        if t >= pressAt { x = pressX + (turnX - pressX) * ease((t - pressAt) / (sweepTo - pressAt)) }
        if t >= sweepTo { x = turnX + (restX - turnX) * ease((t - sweepTo) / (backTo - sweepTo)) }
        return (x, clamp((t - pressAt) / heldFor) - clamp((t - liftAt) / heldFor), t >= pressAt && t < liftAt ? 1 : 0)
    }
    static func duration(_ a: LineToCandlesSwitchFrame, _ b: LineToCandlesSwitchFrame) -> Double {
        if a.candles == 0 && b.candles == 0 { return move }
        return a.lineAlpha != b.lineAlpha ? fade + spread + grow : move
    }
    static func lineAt(_ line: [Double], _ x: Double) -> Double {
        let at = clamp((x * Double(points) - 0.5) / Double(points - 1)) * Double(points - 1), i = min(points - 2, Int(floor(at)))
        return line[i] + (line[i + 1] - line[i]) * (at - Double(i))
    }
    static func frames(_ range: LineToCandlesSwitchRange, _ seed: UInt32) -> [LineToCandlesSwitchFrame] {
        let p = range.prices.count == points ? range.prices : (0..<points).map { range.prices[Int(floor(Double($0) / Double(points - 1) * Double(range.prices.count - 1) + 0.5))] }
        var a = seed
        func random() -> Double { a = a &+ 0x6d2b79f5; var t = (a ^ (a >> 15)) &* (a | 1); t ^= t &+ ((t ^ (t >> 7)) &* (t | 61)); return Double(t ^ (t >> 14)) / 4294967296 }
        let reach = (1..<points).reduce(0.0) { $0 + abs(p[$1] - p[$1 - 1]) } / Double(points - 1) * 0.9
        var o: [Double] = [], h: [Double] = [], l: [Double] = []
        for i in 0..<points { let open = i == 0 ? p[i] - reach * 0.5 : p[i - 1]; o.append(open); h.append(max(open, p[i]) + random() * reach); l.append(min(open, p[i]) - random() * reach) }
        let low = l.min()!, high = h.max()!, slot = 1 / Double(points)
        return views.map { view in
            var f = LineToCandlesSwitchFrame(o: [], h: [], l: [], c: [], x: [], w: [], line: p, low: low, high: high, lineAlpha: view.group == 0 ? 1 : 0, candles: view.group == 0 ? 0 : 1)
            for i in 0..<points {
                if view.group == 0 { f.o.append(p[i]); f.c.append(p[i]); f.h.append(p[i]); f.l.append(p[i]); f.x.append((Double(i) + 0.5) * slot); f.w.append(slot * body) }
                else { let first = i / view.group * view.group, last = min(points - 1, first + view.group - 1); f.o.append(o[first]); f.c.append(p[last]); f.h.append(h[first...last].max()!); f.l.append(l[first...last].min()!); f.x.append((Double(first + last) / 2 + 0.5) * slot); f.w.append(slot * Double(view.group) * body) }
            }
            return f
        }
    }
    // toFixed rounds the exact binary value; multiplying a Double first can change a half-cent tie.
    static func fixed(_ value: Double) -> String {
        let bits = abs(value).bitPattern, exponent = Int((bits >> 52) & 0x7ff) - 1023 - 52
        let scaled = ((bits & 0x000fffffffffffff) | (UInt64(1) << 52)) * 100
        let cents: UInt64
        if exponent >= 0 { cents = scaled << exponent }
        else { let shift = -exponent; cents = shift >= 64 ? 0 : (scaled >> shift) + ((scaled & ((UInt64(1) << shift) - 1)) >= (UInt64(1) << (shift - 1)) ? 1 : 0) }
        return "\(cents / 100).\(String(format: "%02d", Int(cents % 100)))"
    }
    static func dateLabel(_ ms: Double, _ hours: Bool) -> String {
        let quarter = 60000.0, date = Date(timeIntervalSince1970: floor(ms / quarter + 0.5) * quarter / 1000)
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let c = calendar.dateComponents([.month, .day, .year, .hour, .minute], from: date), names = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"], day = "\(names[c.month! - 1]) \(c.day!)"
        if !hours { return "\(day), \(c.year!)" }
        return "\(day) at \(c.hour! % 12 == 0 ? 12 : c.hour! % 12):\(String(format: "%02d", c.minute!)) \(c.hour! < 12 ? "AM" : "PM")"
    }
}
