import Foundation

/*
 Laundry Spin Progress — the wash cycle's script and the clothes' motion, as pure functions of the
 loop time (frame at t = loop equals frame at 0).

 A scene is [drum angle, speed rev/s, tumble, plaster, water level, suds, fog, slosh]:
   tumble   0 = the clothes lie in a pile at the bottom … 1 = they tumble
   plaster  1 = pressed flat against the wall by the spin, turning with the drum
   level    the water's height as a share of the drum's diameter
   fog      condensation on the inside of the glass (hides the reset)

 autoPlay script (loop = 10 s):
   0.0 – 1.6   rest: water calm, clothes settled, a few suds.
   1.6 – 4.2   wash: the drum starts (0.8 rev/s), the clothes tumble, suds build.
   4.2 – 5.6   drain (to 12 %) and rinse (back to 32 %, slower, fewer suds).
   5.6 – 8.0   spin: the water drains, the drum speeds up to 11 rev/s (6.0 – 7.0), the clothes
               plaster to the wall (motion blur), then it slows to a stop (7.4 – 8.0).
   8.0 – 8.6   done: the clothes drop off the wall into a pile.
   8.6 – 10.0  the glass fogs up, the water and suds come back behind it, the fog clears.
 The drum turns a whole number of times per loop, so its holes and lifters match at the seam.
 */
enum LaundrySpinProgressMotion {
    static let sAngle = 0, sSpeed = 1, sTumble = 2, sPlaster = 3, sLevel = 4, sSuds = 5, sFog = 6, sSlosh = 7
    static let garments = 6
    static let loop = LaundrySpinProgress.loop

    /// Drum speed keyframes (t, rev/s), linear between them.
    private static let speedKeys: [(Double, Double)] = [
        (0, 0), (1.6, 0), (2.0, 0.8), (4.0, 0.8), (4.3, 0.4), (5.6, 0.5), (6.0, 0.6), (7.0, 11), (7.4, 11), (8.0, 0), (10, 0),
    ]

    static let phases = ["Ready to wash", "Washing", "Rinsing", "Spinning", "Done"]

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let v = -2 * x + 2
        return 1 - v * v * v / 2
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    static func speedAt(_ t: Double) -> Double {
        for i in 0..<(speedKeys.count - 1) {
            let a = speedKeys[i], b = speedKeys[i + 1]
            if t <= b.0 { return a.1 + (b.1 - a.1) * (t - a.0) / (b.0 - a.0) }
        }
        return 0
    }

    /// Revolutions turned by time t (the integral of the speed keyframes).
    private static func revsRaw(_ t: Double) -> Double {
        var sum = 0.0
        for i in 0..<(speedKeys.count - 1) {
            let a = speedKeys[i], b = speedKeys[i + 1]
            if t <= a.0 { break }
            let e = min(t, b.0)
            let vE = a.1 + (b.1 - a.1) * (e - a.0) / (b.0 - a.0)
            sum += (a.1 + vE) / 2 * (e - a.0)
        }
        return sum
    }

    private static let totalRevs = revsRaw(loop)
    private static let wholeRevs = totalRevs.rounded()

    /// The drum's angle (radians) at t: the raw integral scaled to a whole number of turns.
    static func angleAt(_ t: Double) -> Double {
        revsRaw(t) * wholeRevs * .pi * 2 / totalRevs
    }

    static func restScene() -> [Double] { [0, 0, 0, 0, 0.38, 0.15, 0, 0] }

    static func sceneAt(_ t: Double) -> [Double] {
        var s = restScene()
        let v = speedAt(t)
        s[sAngle] = angleAt(t)
        s[sSpeed] = v
        let wash = easeInOut(seg(t, 1.6, 2.2)) * (1 - easeInOut(seg(t, 4.2, 4.6)))
        let rinse = easeInOut(seg(t, 4.7, 5.1)) * (1 - easeInOut(seg(t, 5.6, 6.0)))
        s[sTumble] = max(wash, rinse * 0.8)
        s[sPlaster] = easeInOut(seg(t, 6.0, 6.7)) * (1 - easeOut(seg(t, 7.9, 8.4)))
        var level = 0.38
        if t >= 4.2 { level = 0.38 - 0.26 * easeInOut(seg(t, 4.2, 4.7)) + 0.2 * easeInOut(seg(t, 4.8, 5.4)) }
        if t >= 5.6 { level = 0.32 * (1 - easeInOut(seg(t, 5.6, 6.1))) }
        if t >= 8.8 { level = 0.38 * easeInOut(seg(t, 8.8, 9.4)) }
        s[sLevel] = level
        var suds = 0.15 + 0.85 * easeOut(seg(t, 1.8, 4.0))
        if t >= 4.2 { suds = 1 - 0.7 * easeInOut(seg(t, 4.2, 5.2)) }
        if t >= 5.6 { suds = 0.3 * (1 - easeInOut(seg(t, 5.6, 6.2))) }
        if t >= 8.8 { suds = 0.15 * easeInOut(seg(t, 8.8, 9.4)) }
        s[sSuds] = suds
        s[sFog] = easeOut(seg(t, 8.6, 9.2)) * (1 - easeInOut(seg(t, 9.4, 10)))
        s[sSlosh] = min(1, v / 0.8) * (1 - s[sPlaster])
        return s
    }

    /// Where each garment lies in the pile at rest (drum units: centre 0, radius 1, y down).
    private static let pile: [(Double, Double, Double)] = [
        (-0.46, 0.56, 0.5), (0.06, 0.66, -0.3), (0.5, 0.5, 1.0), (-0.2, 0.36, 1.7), (0.3, 0.28, -1.2), (-0.04, 0.12, 2.5),
    ]

    /// Garment i for a scene: [x, y (drum units), rotation, blur (radians of smear), stretch].
    static func garmentAt(_ i: Int, _ s: [Double]) -> [Double] {
        let pl = pile[i]
        let fi = Double(i)
        let A = s[sAngle]
        let raw = (A / (.pi * 2) + hash(fi + 3) * 0.9 + fi * 0.17).truncatingRemainder(dividingBy: 1)
        let phase = (raw + 1).truncatingRemainder(dividingBy: 1)
        let ride = 0.58
        var tx = 0.0, ty = 0.0, stretch = 0.0
        let r = 0.6 - 0.06 * hash(fi + 9)
        let a0 = -110 * Double.pi / 180
        let a1 = 30 * Double.pi / 180
        if phase < ride {
            let a = a0 + (a1 - a0) * (phase / ride)
            tx = cos(a) * r
            ty = -sin(a) * r
        } else {
            let f = (phase - ride) / (1 - ride)
            let x0 = cos(a1) * r, y0 = -sin(a1) * r
            let x1 = cos(a0) * r, y1 = -sin(a0) * r
            tx = x0 + (x1 - x0) * f
            ty = y0 + (y1 - y0) * f * f - 0.25 * sin(.pi * f) * (1 - f)
            stretch = sin(.pi * f) * 0.35
        }
        let tw = s[sTumble]
        var x = pl.0 + (tx - pl.0) * tw
        var y = pl.1 + (ty - pl.1) * tw
        var rot = pl.2 + phase * .pi * 2 * 0.6 * tw
        // pressed against the wall by the spin, evenly round it, turning with the drum
        let pa = A + fi / Double(garments) * .pi * 2 + 0.3 * hash(fi + 5)
        let pw = s[sPlaster]
        x += (cos(pa) * 0.74 - x) * pw
        y += (-sin(pa) * 0.74 - y) * pw
        rot += (-pa + .pi / 2 - rot) * pw
        let blur = min(0.9, s[sSpeed] * 0.09) * pw
        return [x, y, rot, blur, stretch * tw * (1 - pw)]
    }

    static func phaseAt(_ t: Double) -> Int {
        if t < 1.6 { return 0 }
        if t < 4.2 { return 1 }
        if t < 5.6 { return 2 }
        if t < 8.0 { return 3 }
        if t < 9.4 { return 4 }
        return 0
    }

    /// Progress through the cycle (0 … 1) at loop time t; it runs back to 0 behind the fog.
    static func progressAt(_ t: Double) -> Double {
        if t < 1.6 { return 0 }
        if t < 8.0 {
            if t < 4.2 { return 0.25 * seg(t, 1.6, 4.2) }
            if t < 5.6 { return 0.25 + 0.25 * seg(t, 4.2, 5.6) }
            return 0.5 + 0.25 * seg(t, 5.6, 8.0)
        }
        if t < 9.0 { return 0.75 + 0.25 * easeOut(seg(t, 8.0, 8.5)) }
        return 1 - easeInOut(seg(t, 9.0, 9.6))
    }

    /// Minutes left in a 42-minute cycle at a progress.
    static func minutesLeft(_ p: Double) -> Double { (42 * (1 - p)).rounded() }

    /// A hand-turned drum's scene from what it was when grabbed and how fast it turns now.
    static func handScene(_ base: [Double], _ angle: Double, _ speed: Double) -> [Double] {
        var s = base
        let v = abs(speed)
        let plaster = min(1, max(0, (v - 2.5) / 2))
        s[sAngle] = angle
        s[sSpeed] = v
        s[sPlaster] = plaster * plaster * (3 - 2 * plaster)
        s[sTumble] = min(1, v / 0.4) * (1 - s[sPlaster])
        s[sSlosh] = min(1, v / 0.8) * (1 - s[sPlaster])
        s[sFog] = 0
        return s
    }
}
