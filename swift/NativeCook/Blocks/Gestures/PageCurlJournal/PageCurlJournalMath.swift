import Foundation

/// Pure functions for the page turn: where the grabbed point may go, and how far the sheet has turned (curl-math.ts).
enum PageCurlJournalMath {
    /// The sheet is bound at the spine (x = 0). A spine point must stay on the unfolded side of the fold, i.e. no
    /// farther from the tip than from the grab point; otherwise the page would tear. Project the tip into both discs.
    static func clampTip(gx: Double, gy: Double, tx: Double, ty: Double, height: Double) -> (x: Double, y: Double) {
        var x = tx
        var y = ty
        for _ in 0..<3 {
            for k in 0..<2 {
                let sy = k == 0 ? 0 : height
                let r = hypot(gx, gy - sy)
                let d = hypot(x, y - sy)
                if d > r, d > 0 {
                    x = (x * r) / d
                    y = sy + ((y - sy) * r) / d
                }
            }
        }
        return (x, y)
    }

    /// 0 = flat, 1 = turned all the way over the spine.
    static func turnProgress(gx: Double, gy: Double, tx: Double, ty: Double) -> Double {
        min(1, hypot(gx - tx, gy - ty) / max(1, 2 * gx))
    }

    /// The roll is small while the sheet first lifts and while it lands flat: a sheet bends before it folds.
    static func rollRamp(_ d: Double, full: Double, rampIn: Double, rampOut: Double) -> Double {
        max(0, min(1, d / rampIn, (full - d) / rampOut))
    }

    /// The scripted finger: a quadratic path, eased so a flick is still accelerating when it lifts.
    static func strokePoint(from: SIMD2<Double>, via: SIMD2<Double>, to: SIMD2<Double>, u: Double, flick: Bool) -> SIMD2<Double> {
        let e = flick ? u * u : u * u * (3 - 2 * u)
        let a = (1 - e) * (1 - e)
        let b = 2 * e * (1 - e)
        let c = e * e
        return SIMD2(a * from.x + b * via.x + c * to.x, a * from.y + b * via.y + c * to.y)
    }
}
