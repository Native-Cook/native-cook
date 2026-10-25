import Foundation

/*
 Paint Mixer Picker — layout, the paint model and the autoPlay script. A port of the React Native
 paintMixerMotion.ts.

 Paint is mixed subtractively, as a weighted average of absorbance (−ln of each sRGB channel): a
 base of white with weight 1 and each drop with weight K × amount. The bowl's shader uses the same
 weights per point (each drop is a blob that a swirl winds into a spiral and stirring spreads), and
 `mixColor` uses them with every blob spread evenly: the colour the paint will be once mixed. Yellow
 and teal over white give a soft fern green.

 autoPlay timeline (loop = 9 s):
   0.0 – 1.4   rest: a bowl of white paint, five pots around it.
   1.4 – 1.6   the yellow pot lifts (scale 1 → 1.06, shadow grows).
   1.5 – 2.0   a yellow drop flies to the bowl (ease-in-out, arcing towards the camera), lands at
               2.0: ripple, splash, the paint swirls (1.6π, ease-out cubic over 1.4 s).
   2.6 – 2.8   the teal pot lifts; 2.75 – 3.25 its drop flies; lands at 3.25, swirl 1.2π over 1.15 s.
   3.9 – 5.4   a stirring stick comes in, circles twice (4.0 – 5.2, ease-in-out sine), leaves;
               the paint winds 3.2π and mixes to green.
   5.3 – 6.6   hold: "Morning Fern" and its code; the wall card behind is that colour.
   6.6 – 8.3   white paint washes back in from the centre in a reverse spiral; the drops fade.
   8.3 – 9.0   rest.
 */
typealias PaintMixerPickerRGB = (Double, Double, Double)

enum PaintMixerPickerMotion {
    static let loop = 9.0
    /// Weight of one full drop against the white base.
    static let k = 1.2
    /// Swirl (radians of winding) at which the paint is about 63 % mixed.
    static let mixTurn = 3.4 * Double.pi

    static func hexRGB(_ hex: UInt32) -> PaintMixerPickerRGB {
        (Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    struct Pot {
        let name: String
        let hex: UInt32
        var rgb: PaintMixerPickerRGB { PaintMixerPickerMotion.hexRGB(hex) }
    }

    static let pots: [Pot] = [
        Pot(name: "Teal", hex: 0x24879A),
        Pot(name: "Magenta", hex: 0xB8336A),
        Pot(name: "Yellow", hex: 0xE6B23C),
        Pot(name: "White", hex: 0xF7F4EE),
        Pot(name: "Umber", hex: 0x6B4A33),
    ]
    static let baseHex: UInt32 = 0xF2EEE6
    static let baseRGB = hexRGB(baseHex)
    /// Pot angles around the bowl (degrees, 0 = right, y down).
    static let potAngles: [Double] = [200, 152, 90, 28, -20]
    static let yellow = 2
    static let teal = 0

    static func absorb(_ c: PaintMixerPickerRGB) -> PaintMixerPickerRGB {
        (-log(max(c.0, 0.02)), -log(max(c.1, 0.02)), -log(max(c.2, 0.02)))
    }

    static let baseA = absorb(baseRGB)
    static let potA: [PaintMixerPickerRGB] = pots.map { absorb($0.rgb) }

    /// Names for colours the bowl can make; the nearest one is shown.
    static let names: [(String, UInt32)] = [
        ("Chalk White", 0xF2EEE6),
        ("Lemon Sorbet", 0xEED9A2),
        ("Saffron", 0xE3B04A),
        ("Pale Lagoon", 0xA9CDD2),
        ("Deep Teal", 0x3E8B95),
        ("Sea Glass", 0x94C3B5),
        ("Morning Fern", 0x7AAE7C),
        ("Moss", 0x6F8A55),
        ("Olive Grove", 0x8E8A4E),
        ("Rose Quartz", 0xE5B4C2),
        ("Raspberry", 0xB54A72),
        ("Terracotta", 0xC0704F),
        ("Dusk Plum", 0x6E4A73),
        ("Driftwood", 0xA8927C),
        ("Cocoa", 0x7A5A44),
    ]
    static let nameRGB: [PaintMixerPickerRGB] = names.map { hexRGB($0.1) }

    static func nearestName(_ c: PaintMixerPickerRGB) -> Int {
        var best = 0
        var bestD = 1e9
        for (i, n) in nameRGB.enumerated() {
            let d = (n.0 - c.0) * (n.0 - c.0) * 0.9 + (n.1 - c.1) * (n.1 - c.1) * 1.2 + (n.2 - c.2) * (n.2 - c.2) * 0.8
            if d < bestD {
                bestD = d
                best = i
            }
        }
        return best
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double {
        let u = 1 - x
        return 1 - u * u * u
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeInOutSine(_ x: Double) -> Double { 0.5 - 0.5 * cos(Double.pi * x) }

    static func potCenter(_ g: PaintMixerPickerGeo, _ i: Int) -> (Double, Double) {
        let a = potAngles[i] * Double.pi / 180
        return (g.cx + cos(a) * g.potRing, g.cy + sin(a) * g.potRing)
    }

    /// The colour the bowl mixes to (sRGB 0 … 1).
    static func mixColor(_ m: PaintMixerPickerMix) -> PaintMixerPickerRGB {
        var r = m.baseA.0 * m.baseW
        var gg = m.baseA.1 * m.baseW
        var b = m.baseA.2 * m.baseW
        var wsum = m.baseW
        for d in m.drops {
            let wgt = k * d.amt * m.fade
            r += wgt * d.a.0
            gg += wgt * d.a.1
            b += wgt * d.a.2
            wsum += wgt
        }
        return (exp(-r / wsum), exp(-gg / wsum), exp(-b / wsum))
    }

    static func mixedShare(_ winding: Double) -> Double {
        min(1, 1.04 * (1 - exp(-max(0, winding) / mixTurn)))
    }

    // The script's pieces.
    static let landY = 2.0
    static let landT = 3.25

    static func windingAt(_ t: Double) -> Double {
        1.6 * Double.pi * easeOutCubic(seg(t, landY, landY + 1.4))
            + 1.2 * Double.pi * easeOutCubic(seg(t, landT, landT + 1.15))
            + 3.2 * Double.pi * easeInOutSine(seg(t, 4.0, 5.3))
    }

    static func landing(_ g: PaintMixerPickerGeo, _ which: Int) -> (Double, Double) {
        which == yellow ? (g.cx - 20 * g.u, g.cy + 18 * g.u) : (g.cx + 22 * g.u, g.cy - 16 * g.u)
    }

    private static func flightAt(_ g: PaintMixerPickerGeo, _ pot: Int, _ s: Double) -> PaintMixerPickerMix.Flight {
        let (px, py) = potCenter(g, pot)
        let (lx, ly) = landing(g, pot)
        let e = easeInOutSine(s)
        return .init(x: px + (lx - px) * e, y: py + (ly - py) * e, h: sin(Double.pi * s), pot: pot)
    }

    /// The scripted bowl at loop time t (pure).
    static func autoMix(_ g: PaintMixerPickerGeo, _ t: Double) -> PaintMixerPickerMix {
        let wind = windingAt(t)
        let back = easeInOutSine(seg(t, 6.6, 8.4))
        let twistNow = wind * (1 - back)
        let fade = 1 - easeInOutCubic(seg(t, 6.6, 8.2))
        var m = PaintMixerPickerMix()
        for (pot, at) in [(yellow, landY), (teal, landT)] {
            if t >= at {
                let (x, y) = landing(g, pot)
                let landWind = windingAt(at)
                m.drops.append(.init(
                    x: x,
                    y: y,
                    amt: easeOutCubic(seg(t, at, at + 0.4)),
                    m: mixedShare(wind - landWind),
                    tw: twistNow - landWind,
                    a: potA[pot]
                ))
                if t < at + 1.6 { m.ripples.append(.init(x: x, y: y, age: t - at, str: 1)) }
                if t < at + 0.45 { m.splashes.append(.init(x: x, y: y, age: t - at, pot: pot)) }
            } else if t >= at - 0.5 {
                m.flights.append(flightAt(g, pot, seg(t, at - 0.5, at)))
            }
        }
        var lift = [0.0, 0, 0, 0, 0]
        lift[yellow] = sin(Double.pi * seg(t, 1.4, 1.75)) * 0.6 + easeOutCubic(seg(t, 1.4, 1.55)) * (1 - seg(t, 1.55, 1.85)) * 0.4
        lift[teal] = sin(Double.pi * seg(t, 2.6, 2.95)) * 0.6 + easeOutCubic(seg(t, 2.6, 2.75)) * (1 - seg(t, 2.75, 3.05)) * 0.4
        m.lift = lift
        // The stick: in over 3.9 – 4.1, two turns, out over 5.2 – 5.4.
        let stickA = 4 * Double.pi * easeInOutSine(seg(t, 4.0, 5.2)) - Double.pi * 0.75
        let stickIn = easeOutCubic(seg(t, 3.85, 4.1)) * (1 - easeInOutCubic(seg(t, 5.15, 5.45)))
        let sr = g.paint * 0.46
        m.stick = (g.cx + cos(stickA) * sr, g.cy + sin(stickA) * sr, stickIn)
        if stickIn > 0.5 { m.ripples.append(.init(x: m.stick.0, y: m.stick.1, age: 0.25, str: 0.35 * stickIn)) }
        if m.ripples.count > 3 { m.ripples = Array(m.ripples.prefix(3)) }
        let front = t > 6.6 && t < 8.4 ? (g.paint + 46 * g.u) * easeInOutSine(seg(t, 6.6, 8.2)) : -1
        m.wash = (front, -2.4 * Double.pi * back)
        m.fade = fade
        return m
    }

    /// The colour shown on the wall card and the label, a quarter second behind the bowl.
    static func shownColor(_ g: PaintMixerPickerGeo, _ t: Double) -> PaintMixerPickerRGB {
        mixColor(autoMix(g, max(0, t - 0.25)))
    }

    static func toHex(_ c: PaintMixerPickerRGB) -> String {
        var s = "#"
        for v in [c.0, c.1, c.2] {
            s += String(format: "%02X", Int((clamp01(v) * 255).rounded()))
        }
        return s
    }

    /// Light reflectance value (0 … 100), as paint charts quote it.
    static func lrv(_ c: PaintMixerPickerRGB) -> Int {
        func lin(_ v: Double) -> Double { v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
        return Int((100 * (0.2126 * lin(c.0) + 0.7152 * lin(c.1) + 0.0722 * lin(c.2))).rounded())
    }
}

/// Everything one frame needs.
struct PaintMixerPickerMix {
    /// A drop in the bowl, ready for the shader: position, amount, mixed share, winding, absorbance.
    struct Drop {
        var x: Double
        var y: Double
        var amt: Double
        var m: Double
        var tw: Double
        var a: PaintMixerPickerRGB
    }

    /// A drop in the air (h = height towards the camera, 0 … 1) or one held under the finger.
    struct Flight {
        var x: Double
        var y: Double
        var h: Double
        var pot: Int
    }

    struct Ripple {
        var x: Double
        var y: Double
        var age: Double
        var str: Double
    }

    struct Splash {
        var x: Double
        var y: Double
        var age: Double
        var pot: Int
    }

    var baseA: PaintMixerPickerRGB = PaintMixerPickerMotion.baseA
    var baseW = 1.0
    var drops: [Drop] = []
    var ripples: [Ripple] = []
    var splashes: [Splash] = []
    var flights: [Flight] = []
    /// Wash-back front radius (−1: none) and its reverse twist.
    var wash: (Double, Double) = (-1, 0)
    /// How much of the drops' paint is left (the wash fades it).
    var fade = 1.0
    var stick: (Double, Double, Double) = (0, 0, 0)
    var lift: [Double] = [0, 0, 0, 0, 0]
}

/// Screen layout, in points.
struct PaintMixerPickerGeo: Equatable {
    let w: Double
    let h: Double
    let u: Double
    let top: Double
    let cardY: Double
    let cardH: Double
    let cx: Double
    let cy: Double
    /// Outer rim radius and the paint's radius.
    let rim: Double
    let paint: Double
    let potR: Double
    let potRing: Double
    let panelY: Double
    let buttonY: Double

    init(w: Double, h: Double, top: Double, bottom: Double) {
        let u = min(w / 393, (h - top - bottom) / 759)
        let cardY = top + 112
        let cardH = 150 * u
        let rim = 104 * u
        let potR = 25 * u
        let potRing = rim + 54 * u
        let cy = cardY + cardH + 112 * u
        self.w = w
        self.h = h
        self.u = u
        self.top = top
        self.cardY = cardY
        self.cardH = cardH
        self.cx = w / 2
        self.cy = cy
        self.rim = rim
        self.paint = rim * 0.8
        self.potR = potR
        self.potRing = potRing
        self.panelY = cy + potRing + potR + 30 * u
        self.buttonY = h - bottom - 16 - 52
    }
}
