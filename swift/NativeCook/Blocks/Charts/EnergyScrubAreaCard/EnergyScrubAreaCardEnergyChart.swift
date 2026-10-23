import SwiftUI

struct EnergyScrubAreaCardFilm { var cursor: Double; var held: Double; var down: Double; var eco: Double; var knob: Double }
struct EnergyScrubAreaCardFrame { var width: Double; var top: Double; var bottom: Double; var min: Double; var max: Double }
enum EnergyScrubAreaCardMotion {
    static let loop = 9.0, pressAt = 0.8, glideAt = 1.0, glideFor = 3.2, backFor = 0.8, liftAt = 5.6, ecoOnAt = 6.0, ecoOffAt = 7.6
    static let morphFor = 0.8, knobFor = 0.35, dotIn = 0.3, dotOut = 0.25, userHold = 3.0, returnFor = 0.45
    static let startDay = 0, turnDay = 6, restDay = 2, backOvershoot = 1.4
    static func clamp(_ t: Double) -> Double { min(1, max(0, t)) }
    static func easeInOut(_ t: Double) -> Double { let u = clamp(t); return u < 0.5 ? 4 * pow(u, 3) : 1 - pow(-2 * u + 2, 3) / 2 }
    static func backOut(_ t: Double) -> Double { let u = clamp(t); return 1 + (backOvershoot + 1) * pow(u - 1, 3) + backOvershoot * pow(u - 1, 2) }
    static func dayX(_ day: Int) -> Double { (Double(day) + 0.5) / 7 }
    static func filmAt(_ t: Double, _ still: Bool) -> EnergyScrubAreaCardFilm {
        let down = t >= pressAt && t < liftAt ? 1.0 : 0.0
        let glide = easeInOut((t - glideAt) / glideFor), back = easeInOut((t - glideAt - glideFor) / backFor)
        let cursor = still ? dayX(restDay) : dayX(startDay) + (dayX(turnDay) - dayX(startDay)) * glide + (dayX(restDay) - dayX(turnDay)) * back
        let held = still ? down : (t < liftAt ? (t < pressAt ? 0 : backOut((t - pressAt) / dotIn)) : 1 - clamp((t - liftAt) / dotOut))
        let eco = still ? (t >= ecoOnAt && t < ecoOffAt ? 1.0 : 0.0) : easeInOut((t - ecoOnAt) / morphFor) - easeInOut((t - ecoOffAt) / morphFor)
        let knob = still ? eco : backOut((t - ecoOnAt) / knobFor) - backOut((t - ecoOffAt) / knobFor)
        return EnergyScrubAreaCardFilm(cursor: cursor, held: held, down: down, eco: eco, knob: knob)
    }
    static func mix(_ a: [Double], _ b: [Double], _ t: Double) -> [Double] { zip(a, b).map { $0 + ($1 - $0) * t } }
    static func slope(_ values: [Double], _ i: Int) -> Double {
        if i == 0 { return values[1] - values[0] }
        if i == values.count - 1 { return values[i] - values[i - 1] }
        return (values[i + 1] - values[i - 1]) / 2
    }
    static func valueAt(_ values: [Double], _ x: Double) -> Double {
        let steps = values.count - 1, at = clamp(x) * Double(steps), i = min(steps - 1, Int(floor(at))), t = at - Double(i), t2 = t * t, t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * values[i] + (t3 - 2 * t2 + t) * slope(values, i) + (-2 * t3 + 3 * t2) * values[i + 1] + (t3 - t2) * slope(values, i + 1)
    }
    static func yOf(_ value: Double, _ frame: EnergyScrubAreaCardFrame) -> Double { frame.bottom - (value - frame.min) / (frame.max - frame.min) * (frame.bottom - frame.top) }
    static func curvePath(_ values: [Double], _ frame: EnergyScrubAreaCardFrame, _ floor: Double? = nil) -> Path {
        var path = Path(); let steps = values.count - 1, dx = frame.width / Double(steps)
        path.move(to: CGPoint(x: 0, y: yOf(values[0], frame)))
        for i in 0..<steps {
            path.addCurve(to: CGPoint(x: Double(i + 1) * dx, y: yOf(values[i + 1], frame)), control1: CGPoint(x: Double(i) * dx + dx / 3, y: yOf(values[i] + slope(values, i) / 3, frame)), control2: CGPoint(x: Double(i + 1) * dx - dx / 3, y: yOf(values[i + 1] - slope(values, i + 1) / 3, frame)))
        }
        if let floor { path.addLine(to: CGPoint(x: frame.width, y: floor)); path.addLine(to: CGPoint(x: 0, y: floor)); path.closeSubpath() }
        return path
    }
}
