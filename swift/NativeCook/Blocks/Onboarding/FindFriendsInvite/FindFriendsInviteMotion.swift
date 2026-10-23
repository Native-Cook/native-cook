import CoreGraphics
import Foundation

/// Every value on screen is a pure function of three effect clocks (seconds). In autoPlay the clocks
/// are a pure function of the loop time `t` (0 <= t < loop), so t = loop renders exactly like t = 0.
///
///   0.00-0.50  rest
///   0.50-0.77  "invite them" presses in to 0.97 and is released
///   0.77-1.73  the button folds itself into a paper plane
///   1.73-3.63  the dart launches on a curve with one loop, contrail, its wake rocks the cup
///   2.97-3.97  a new sheet unfolds into the button
///   4.10-4.37  "check contacts" presses; the black button fills
///   4.37-4.80  the four avatars melt into one liquid blob
///   4.72-5.80  five contact droplets fly in from the screen edges
///   5.90-6.85  the blob splits back into the four avatars
///   6.90-7.80  rest
enum FindFriendsInviteMotion {
    static let loop = 7.8
    static let resumeAfter = 2.0

    // MARK: easing

    typealias Bezier = (Double, Double, Double, Double)
    static let easeOut: Bezier = (0.23, 1, 0.32, 1)
    static let easeInOut: Bezier = (0.77, 0, 0.175, 1)
    static let easeFold: Bezier = (0.6, 0, 0.3, 1)
    static let easeHang: Bezier = (0.3, 0.7, 0.7, 0.3)

    static func bezier(_ c: Bezier, _ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        var lo = 0.0, hi = 1.0, u = x
        for _ in 0..<24 {
            u = (lo + hi) / 2
            let v = 1 - u
            let xu = 3 * v * v * u * c.0 + 3 * v * u * u * c.2 + u * u * u
            if xu < x { lo = u } else { hi = u }
        }
        let v = 1 - u
        return 3 * v * v * u * c.1 + 3 * v * u * u * c.3 + u * u * u
    }

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : (v > 1 ? 1 : v) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    /// Underdamped step response 0 -> 1 (closed form of a spring released at rest).
    static func springStep(_ tau: Double, omega: Double, zeta: Double) -> Double {
        if tau <= 0 { return 0 }
        if zeta * omega * tau > 9 { return 1 }
        let root = (1 - zeta * zeta).squareRoot()
        let wd = omega * root
        let e = exp(-zeta * omega * tau)
        return 1 - e * (cos(wd * tau) + (zeta / root) * sin(wd * tau))
    }

    /// Damped sine kick, windowed so it is exactly 0 from `end` on.
    static func kick(_ tau: Double, period: Double, decay: Double, fadeFrom: Double, end: Double) -> Double {
        if tau <= 0 || tau >= end { return 0 }
        return exp(-tau / decay) * sin(2 * .pi * tau / period) * (1 - seg(tau, fadeFrom, end))
    }

    /// 0 -> 1 -> 0 over [0, 1] (reduced-motion dips).
    static func triangle(_ u: Double) -> Double {
        if u <= 0 || u >= 1 { return 0 }
        return u < 0.5 ? bezier(easeOut, u * 2) : 1 - bezier(easeOut, (u - 0.5) * 2)
    }

    // MARK: invite

    enum Spring {
        static let omega = 15.7
        static let zeta = 0.8
    }

    enum Invite {
        static let pressIn = 0.12
        static let release = 0.27
        static let pressScale = 0.97
    }

    /// Times are seconds after the release. Angles in degrees.
    enum Fold {
        static let labelOut = 0.1
        static let labelScale = 0.96
        static let radiusDur = 0.16
        static let paperRadius = 3.0
        static let skiaOn = 0.1
        static let rnOff = 0.15
        static let book = (0.16, 0.5)
        static let flapTop = (0.5, 0.7)
        static let flapBot = (0.54, 0.74)
        static let flapAngle = 90.0
        static let flapFade = 20.0
        static let keelClose = (0.72, 0.88)
        static let keelAngle = 64.0
        static let wingAngle = 24.0
        static let wingOmega = 15.0
        static let wingZeta = 0.42
        static let launch = 0.96
    }

    enum Flight {
        static let duration = 1.9
        static let scale = 0.46
        static let scaleDur = 0.42
        static let yawDur = 0.32
        static let bankIn = 0.25
        static let lift = 60.0
        static let camera = 1000.0
        static let bankGain = 30.0
        static let cameraAt = (312.0, 222.0)
        static let bankMax = 0.62
        static let points: [(Double, Double)] = [
            (281, 341.2), (325, 326), (360, 292), (368, 246), (350, 200), (314, 176), (277, 190),
            (264, 226), (282, 256), (320, 262), (360, 238), (398, 186), (445, 105), (505, 15), (560, -60)
        ]
        static let samplesPerSegment = 24
    }

    enum Reform {
        static let appear = 2.2
        static let appearDur = 0.2
        static let appearScale = 0.96
        static let unfold = 2.38
        static let omega = 11.0
        static let zeta = 0.86
        static let rnOn = 2.92
        static let skiaOff = 2.98
        static let radiusBack = (2.92, 3.16)
        static let labelIn = (2.96, 3.2)
    }

    enum Contrail {
        static let samples = 36
        static let length = 230.0
        static let headGap = 0.85
        static let widthHead = 1.4
        static let widthTail = 11.0
        static let wobble = 3.2
        static let opacityHead = 0.38
        static let fadeIn = 0.15
        static let fadeOut = 0.45
        static let blur = 1.6
        static let segments = 6
    }

    enum Light {
        static let dir = (-0.35, -0.55, 0.76)
        static let dark = 0.7
        static let bright = 0.5
        static let shadowDir = (0.16, 0.28)
        static let shadowOpacity = 0.13
        static let shadowBlur = 2.5
        static let shadowBlurPerPt = 0.05
    }

    static let inviteEnd = Invite.release + Fold.launch + Flight.duration + Contrail.fadeOut + 0.05

    // MARK: check contacts

    enum Scan {
        static let pressIn = 0.12
        static let release = 0.27
        static let fillStart = 0.3
        static let fill = 1.45
        static let fadeDur = 0.25
        static let end = 2.8
        static let pressScale = 0.97
        static let fillOpacity = 0.18
    }

    struct Drop {
        let side: Double
        let dy: Double
        let r: Double
        let color: UInt32
        let start: Double
    }

    enum Goo {
        static let skiaOn = 0.27
        static let rnOff = 0.31
        static let sinkStart = 0.3, sinkStagger = 0.04, sinkDur = 0.24, sinkScale = 0.5
        static let gooIn = (0.32, 0.7)
        static let pullStart = 0.36, pullAmount = 0.7, pullOmega = 9.0, pullZeta = 0.55, pullShrink = 0.1
        static let wobbleAmp = 3.4, wobbleFreq = 2.6
        static let wobbleOn = (0.55, 0.85)
        static let wobbleOff = (1.7, 1.9)
        static let drops: [Drop] = [
            Drop(side: -1, dy: -22, r: 9, color: 0xFFD59A, start: 0.62),
            Drop(side: 1, dy: 18, r: 11, color: 0xA9D8FF, start: 0.74),
            Drop(side: -1, dy: 26, r: 8, color: 0xADEEA9, start: 0.86),
            Drop(side: 1, dy: -28, r: 7, color: 0xF298B9, start: 0.98),
            Drop(side: -1, dy: 4, r: 10, color: 0xCCC0FB, start: 1.1)
        ]
        /// Head per droplet (asset keys) and whether it is mirrored.
        static let dropHeads = ["head-1", "head-3", "head-2", "head-5", "head-1"]
        static let dropMirror = [true, true, true, true, false]
        static let dropFade = (0.62, 0.86)
        static let dropDur = 0.56
        static let dropArc = 24.0
        static let dropAbsorb = 0.8
        static let dropOffscreen = 24.0
        static let kickAmp = 0.07, kickPeriod = 0.3, kickDecay = 0.16, kickFadeFrom = 0.45, kickEnd = 0.6
        static let splitStart = 1.8, splitOmega = 11.0, splitZeta = 0.5
        static let gooOut = (1.86, 2.2)
        static let faceStart = 1.92, faceStagger = 0.05, faceDur = 0.2, faceFrom = 0.6, faceOmega = 14.0, faceZeta = 0.6
        static let rnOn = 2.68
        static let skiaOff = 2.74
        static let bandPad = 60.0
        static let rank = [2, 0, 1, 3]
    }

    // MARK: the cup

    enum Cup {
        static let end = 1.6
        static let amp = -7.0
        static let shiftX = -2.5
        static let shiftLag = 0.05
        static let period = 0.55
        static let decay = 0.35
        static let fadeFrom = 1.25
        static let tapAmp = 5.0
        static let tapRearm = 0.45
        static let centre = (200.0, 209.0)
    }

    enum Reduced {
        static let planeDip = 0.35
        static let inviteEnd = 0.4
        static let scanEnd = 0.4
    }

    /// GPU warm-up pose: shown at alpha 1/255 for the first frames after mount.
    enum Warm {
        static let frames = 3
        static let alpha = 1.0 / 255
        static let inv = 0.27 + 2.5
        static let scan = 1.0
    }

    // MARK: the flight path, sampled once by arc length

    struct PathTable {
        let xs: [Double], ys: [Double], ss: [Double], hs: [Double], bank: [Double]
        var length: Double { ss[ss.count - 1] }

        init() {
            let p = Flight.points
            let n = p.count
            func at(_ i: Int) -> (Double, Double) {
                if i < 0 { return (2 * p[0].0 - p[1].0, 2 * p[0].1 - p[1].1) }
                if i >= n { return (2 * p[n - 1].0 - p[n - 2].0, 2 * p[n - 1].1 - p[n - 2].1) }
                return p[i]
            }
            var xs: [Double] = [], ys: [Double] = []
            for i in 0..<(n - 1) {
                let p0 = at(i - 1), p1 = at(i), p2 = at(i + 1), p3 = at(i + 2)
                for k in 0..<Flight.samplesPerSegment {
                    let u = Double(k) / Double(Flight.samplesPerSegment)
                    let u2 = u * u, u3 = u2 * u
                    func cr(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
                        0.5 * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3)
                    }
                    xs.append(cr(p0.0, p1.0, p2.0, p3.0))
                    ys.append(cr(p0.1, p1.1, p2.1, p3.1))
                }
            }
            xs.append(p[n - 1].0)
            ys.append(p[n - 1].1)
            let m = xs.count
            var ss = [0.0]
            for i in 1..<m { ss.append(ss[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])) }
            var hs: [Double] = []
            for i in 0..<m {
                let a = max(0, i - 1), b = min(m - 1, i + 1)
                var h = atan2(ys[b] - ys[a], xs[b] - xs[a])
                if i > 0 {
                    while h - hs[i - 1] > .pi { h -= 2 * .pi }
                    while h - hs[i - 1] < -.pi { h += 2 * .pi }
                }
                hs.append(h)
            }
            var raw: [Double] = []
            for i in 0..<m {
                let a = max(0, i - 2), b = min(m - 1, i + 2)
                let ds = ss[b] - ss[a]
                raw.append(ds > 0 ? (hs[b] - hs[a]) / ds : 0)
            }
            var bank: [Double] = []
            let r = 8
            for i in 0..<m {
                var acc = 0.0, cnt = 0.0
                for j in max(0, i - r)...min(m - 1, i + r) { acc += raw[j]; cnt += 1 }
                let k = (acc / cnt) * Flight.bankGain
                bank.append(max(-Flight.bankMax, min(Flight.bankMax, k)))
            }
            self.xs = xs; self.ys = ys; self.ss = ss; self.hs = hs; self.bank = bank
        }

        /// Position (pin pt), unwrapped heading (rad) and bank (rad) at arc distance s.
        func at(_ distance: Double) -> (x: Double, y: Double, h: Double, b: Double) {
            let n = ss.count
            let s = min(max(distance, 0), ss[n - 1])
            var lo = 0, hi = n - 1
            while hi - lo > 1 {
                let mid = (lo + hi) >> 1
                if ss[mid] <= s { lo = mid } else { hi = mid }
            }
            let f = ss[hi] == ss[lo] ? 0 : (s - ss[lo]) / (ss[hi] - ss[lo])
            return (xs[lo] + (xs[hi] - xs[lo]) * f, ys[lo] + (ys[hi] - ys[lo]) * f,
                    hs[lo] + (hs[hi] - hs[lo]) * f, bank[lo] + (bank[hi] - bank[lo]) * f)
        }
    }

    static let path = PathTable()

    /// Flight time (s after launch) at which the dart passes closest to the cup: the gust.
    static let gust: Double = {
        var best = 0.0, bestD = Double.infinity
        for i in 0..<path.ss.count {
            let d = hypot(path.xs[i] - Cup.centre.0, path.ys[i] - Cup.centre.1)
            if d < bestD { bestD = d; best = path.ss[i] / path.length }
        }
        var lo = 0.0, hi = 1.0
        for _ in 0..<40 {
            let mid = (lo + hi) / 2
            if bezier(easeHang, mid) < best { lo = mid } else { hi = mid }
        }
        return lo * Flight.duration
    }()

    /// The plane passes the cup this long after the press of "invite them".
    static let gustAfterPress = Invite.release + Fold.launch + gust

    enum Start {
        static let invite = 0.5
        static let cup = 0.5 + FindFriendsInviteMotion.gustAfterPress
        static let scan = 4.1
    }

    // MARK: clocks

    struct Clocks {
        var invite = 0.0
        var scan = 0.0
        var cup = Cup.end
        var cupTap = Cup.end
    }

    static func automatic(time: Double) -> Clocks {
        let t = (time.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        return Clocks(invite: min(max(t - Start.invite, 0), inviteEnd),
                      scan: min(max(t - Start.scan, 0), Scan.end),
                      cup: min(max(t - Start.cup, 0), Cup.end), cupTap: Cup.end)
    }

    /// Loop-time windows in which nothing moves (the timeline is not scheduled there).
    static let restFrom = Scan.end + Start.scan   // 6.9
    static let restUntil = Start.invite           // 0.5
}
