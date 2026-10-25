import Foundation

/*
 Zipper Unlock — geometry and the autoPlay script, shared by the screen, the shader and the teeth
 (a port of zipperMotion.ts).

 loop = 9 s:
   0.0 – 1.6   rest: closed, the pull tab sways gently on its pivot (whole-k sines).
   1.6 – 2.0   grab: the tab lifts toward the viewer (scale 1 → 1.06, its shadow grows).
   2.0 – 4.8   unzip to 100 % with a slow start and a quick finish (p = x²(1.6 − 0.6x)).
   4.8         the tab is let go and swings (damped, 3.2 /s, 7 rad/s).
   4.8 – 5.4   the halves swing open past the screen edges (ease-out cubic); the journal's
               header and three note cards come in from 5.0, 40 ms apart (0.36 s ease-out).
   6.2 – 6.7   the halves come back (ease-in-out); 6.4 – 6.6 the tab is grabbed again.
   6.6 – 8.4   zipped back up (ease-in-out); 8.4 the tab is let go and swings out by 9.0.
   8.4 – 9.0   rest.
*/

enum ZipperUnlockMotion {
    static let loop = 9.0

    /// Teeth: spacing along the tape and size (pt).
    static let toothPitch = 7.2
    static let toothW = 7.4
    static let toothH = 4.2
    /// The V: largest half-gap (share of the width), how far above the slider it reaches it, and
    /// the length over which the opening closes again at the top stop (pt).
    static let gapShare = 0.21
    static let gapReach = 300.0
    static let gapTop = 64.0
    /// Half-gap right at the slider's mouth (inside the slider body).
    static let gapMouth = 5.0
    /// Release past this share of the travel and the zipper opens fully.
    static let openAt = 0.7
    /// Pull tab: pivot below the slider's centre, length to the ring's centre (pt).
    static let tabPivot = 13.0
    static let tabLen = 50.0

    static let idleBeforeReturn = 2.0
    static let openFor = 0.6
    static let closeHalvesFor = 0.35
    static let closeSliderFor = 0.55

    struct Zip {
        var width = 0.0
        var cx = 0.0
        /// Top stop, closed slider centre, open slider centre, bottom stop (pt).
        var yTop = 0.0
        var ys0 = 0.0
        var ys1 = 1.0
        var yEnd = 0.0
        var gapMax = 0.0

        init() {}

        init(width: Double, height: Double, top: Double, bottom: Double) {
            self.width = width
            cx = width / 2
            ys0 = top + 40
            ys1 = height - bottom - 110
            yTop = ys0 - 18
            yEnd = ys1 + 22
            gapMax = width * gapShare
        }

        var range: Double { ys1 - ys0 }

        func sliderY(_ p: Double) -> Double { ys0 + (ys1 - ys0) * p }

        /// Half-width of the opening at y for slider y `ys` and full-open amount `o`, already
        /// eased (same in the shader).
        func gapAt(_ y: Double, ys: Double, o: Double) -> Double {
            var v = 0.0
            if y < ys && y > yTop {
                v = gapMouth + gapMax * smooth((ys - y) / gapReach) * smooth((y - yTop) / gapTop)
            }
            let full = width / 2 + 70
            return v + (full - v) * clamp(o, 0, 1)
        }
    }

    static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, v)) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }

    static func smooth(_ x: Double) -> Double {
        let c = clamp(x, 0, 1)
        return c * c * (3 - 2 * c)
    }

    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp(x, 0, 1), 3) }

    static func easeInOut(_ x: Double) -> Double {
        let c = clamp(x, 0, 1)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    /// The loop's state at t: zip progress, full open, tab lift, tab angle (rad), seconds since open.
    static func script(_ t: Double) -> (p: Double, o: Double, lift: Double, angle: Double, since: Double) {
        // Unzip: slow start, quick finish; zip up: ease-in-out.
        let x = seg(t, 2.0, 4.8)
        let down = x * x * (1.6 - 0.6 * x)
        let up = easeInOut(seg(t, 6.6, 8.4))
        let p = t < 6.6 ? down : 1 - up
        let o = easeOut(seg(t, 4.8, 5.4)) * (1 - easeInOut(seg(t, 6.2, 6.7)))
        let lift = easeOut(seg(t, 1.6, 2.0)) * (1 - easeOut(seg(t, 4.8, 5.0))) + easeOut(seg(t, 6.4, 6.6)) * (1 - easeOut(seg(t, 8.4, 8.6)))
        // Sway at rest (2 and 3 cycles a loop), held still while the tab is held, plus the swing
        // after each let-go, faded out before the loop closes.
        let sway = 0.05 * sin(2 * Double.pi * 2 * t / loop) + 0.02 * sin(2 * Double.pi * 3 * t / loop)
        var swing = 0.0
        if t > 4.8 { swing += 0.26 * exp(-3.2 * (t - 4.8)) * sin(7 * (t - 4.8)) }
        if t > 8.4 { swing -= 0.22 * exp(-3.2 * (t - 8.4)) * sin(7 * (t - 8.4)) }
        swing *= 1 - smooth(seg(t, 8.7, 9.0))
        let angle = sway * (1 - lift) + swing
        let since = t >= 4.8 && t < 6.7 ? t - 4.8 : -1
        return (p, o, lift, angle, since)
    }

    /// Entrance of the journal's i-th element, from seconds since the halves started to open.
    static func revealOf(_ since: Double, _ i: Int) -> Double {
        if since < 0 { return 0 }
        return easeOut((since - 0.2 - Double(i) * 0.04) / 0.36)
    }
}
