import Foundation

/*
 Velvet Curtain Sheet — layout of the seat map and the autoPlay script (a port of velvetMotion.ts).

 loop = 9 s:
   0.0 – 1.4   rest: the sheet at its half detent, the curtain closed over the seat map;
               1.1 – 1.4 the "Choose seats" button is pressed (scale 0.96 and back).
   1.4 – 2.6   the curtain's rail opens (ease-in-out); the hem lags behind on a spring
               (ω 9 rad/s, damping ratio 0.35), so the cloth swings out, overshoots and settles by 3.2.
   3.2 – 5.6   seats C7 (3.6) and C8 (4.3) light up with a pop; the booking bar rises at 3.0.
   5.6 – 6.8   the curtain closes (ease-in-out), the hem swings in after it; the bar drops at 5.6.
   7.4 – 7.6   the seats switch off behind the closed curtain.
   7.6 – 9.0   rest. The hem's swing is faded out by 8.6 – 9.0 so t = 9 equals t = 0.
*/

enum VelvetCurtainSheetMotion {
    static let loop = 9.0

    static let rows = ["A", "B", "C", "D", "E", "F", "G", "H"]
    static let perRow = 12
    static let seatW = 20.0
    static let seatH = 18.0
    static let seatPitch = 24.0
    static let aisle = 18.0
    /// Sheet coordinates: where the curtain starts (under the pelmet), the screen arc, the first row.
    static let pelmet = 40.0
    static let screenY = 62.0
    static let seatsY = 98.0
    static let price = 14.0

    /// Seats the loop selects: C7 and C8 (indices into the row-major seat list).
    static let loopSeats = [2 * 12 + 6, 2 * 12 + 7]
    static let loopSeatAt = [3.6, 4.3]

    /// Sway of the hem: a spring trailing the rail.
    static let swayOmega = 9.0
    static let swayZeta = 0.35

    static let idleBeforeReturn = 2.0
    static let returnFor = 0.8

    static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, v)) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }

    static func easeInOut(_ x: Double) -> Double {
        let c = clamp(x, 0, 1)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp(x, 0, 1), 3) }

    /// Row pitch that keeps the map, the legend and the bar apart on short screens.
    static func rowPitch(_ visible: Double) -> Double { visible < 420 ? 22 : 24 }

    static var seatsWidth: Double { Double(perRow) * seatPitch - (seatPitch - seatW) + aisle }

    /// Top-left of seat i in sheet coordinates.
    static func seatAt(_ i: Int, width: Double, pitch: Double) -> (x: Double, y: Double) {
        let row = i / perRow
        let col = i % perRow
        let x0 = (width - seatsWidth) / 2
        let x = x0 + Double(col) * seatPitch + (col >= perRow / 2 ? aisle : 0)
        return (x, seatsY + Double(row) * pitch)
    }

    static func seatName(_ i: Int) -> String { "\(rows[i / perRow])\(i % perRow + 1)" }

    /// Taken seats: seeded, never the loop's pair or their neighbours.
    static func isTaken(_ i: Int) -> Bool {
        let row = i / perRow
        if row == 2 && abs(Double(i % perRow) - 6.5) < 2 { return false }
        let h = abs(sin(Double(i) * 12.9898 + 4.1) * 43758.5453).truncatingRemainder(dividingBy: 1)
        return h < (row < 2 ? 0.18 : row > 5 ? 0.42 : 0.3)
    }

    /// The rail's opening in the loop (0 closed, 1 open).
    static func railAt(_ t: Double) -> Double {
        easeInOut(seg(t, 1.4, 2.6)) * (1 - easeInOut(seg(t, 5.6, 6.8)))
    }

    /// The hem's opening: the rail's, through the sway spring, integrated from t = 0 (pure in t).
    static func hemAt(_ t: Double) -> Double {
        let step = 1.0 / 60
        var x = 0.0
        var v = 0.0
        let n = Int(floor(t / step))
        if n >= 1 {
            for i in 1...n {
                let goal = railAt(Double(i) * step)
                let a = -swayOmega * swayOmega * (x - goal) - 2 * swayZeta * swayOmega * v
                v += a * step
                x += v * step
            }
        }
        let rail = railAt(t)
        let fade = 1 - easeInOut(seg(t, 8.6, 9.0))
        return rail + (x - rail) * fade
    }

    /// A seat's light in the loop: 0 when off, 1 lit.
    static func loopSeat(_ t: Double, _ k: Int) -> Double {
        let on = seg(t, loopSeatAt[k], loopSeatAt[k] + 0.35)
        let off = seg(t, 7.4, 7.6)
        return on * (1 - off)
    }

    /// Pop scale for a seat lit `e` seconds ago (a small overshoot, settled in 0.35 s).
    static func popScale(_ e: Double) -> Double {
        if e < 0 { return 1 }
        let x = clamp(e / 0.35, 0, 1)
        return 1 + 0.12 * sin(Double.pi * x) * (1 - x * 0.4)
    }

    static func pressAt(_ t: Double) -> Double { 1 - 0.04 * sin(Double.pi * seg(t, 1.1, 1.4)) }

    /// The booking bar's rise (0 hidden, 1 shown).
    static func barAt(_ t: Double) -> Double { easeOut(seg(t, 3.0, 3.4)) * (1 - easeInOut(seg(t, 5.6, 5.9))) }

    /// The seat under a point in sheet coordinates, or -1.
    static func seatIndexAt(_ x: Double, _ y: Double, width: Double, pitch: Double) -> Int {
        let row = Int(floor((y - seatsY + (pitch - seatH) / 2) / pitch))
        if row < 0 || row >= rows.count { return -1 }
        for col in 0..<perRow {
            let sx = seatAt(row * perRow + col, width: width, pitch: pitch).x
            if x >= sx - 2 && x <= sx + seatW + 2 { return row * perRow + col }
        }
        return -1
    }
}
