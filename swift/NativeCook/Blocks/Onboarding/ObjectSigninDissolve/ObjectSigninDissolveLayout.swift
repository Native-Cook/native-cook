import Foundation

struct ObjectSigninDissolveLayout {
    let width: Double, height: Double, top: Double, bottom: Double
    var scale: Double { min(width / 402, 1.12) }
    func x(_ x: Double) -> Double { (width - 402 * scale) / 2 + x * scale }
    func yt(_ y: Double) -> Double { top + (y - 62) * scale }
    func yb(_ y: Double) -> Double { height - bottom - (874 - 34 - y) * scale }
    var cy: Double { height * (338.0 / 874) }
    var reach: Double { hypot(width + 80, height + 80) + 6 }
    var heroBox: CGRect { CGRect(x: width / 2 - 120 * scale, y: cy - 220 * scale, width: 240 * scale, height: 400 * scale) }
    var pill: CGRect { CGRect(x: x(28), y: yb(662), width: 346 * scale, height: 56 * scale) }
    func half(_ i: Int) -> CGRect { CGRect(x: x(28) + (i == 0 ? 0 : (346 - 165) * scale), y: yb(731), width: 165 * scale, height: 48 * scale) }
}
