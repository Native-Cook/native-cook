import Foundation
import CoreGraphics

/// One invented paper of the morning pile.
struct NewspaperPileReaderPaper {
    let masthead: String
    /// 0 tabloid, 1 caps broadsheet, 2 blackletter with photo right, 3 blackletter with photo left,
    /// 4 sans masthead between red rules, 5 serif masthead with a wide photo.
    let style: Int
    let headline: String
    let deck: String
    let kicker: String
    let title: String
    let summary: String
    let tone: UInt32
}

/// Where one paper is drawn this frame: its centre, scale (1 = open size 340 × 228), rotation in degrees and lift.
struct NewspaperPileReaderSheet {
    var x = 0.0
    var y = 0.0
    var scale = 1.0
    var rot = 0.0
    var lift = 0.0
}

struct NewspaperPileReaderFrame {
    var sheets: [NewspaperPileReaderSheet] = []
    /// The paper drawn a second time on top of the pile (the lifted one), and that copy's opacity.
    var raised = -1
    var raisedAlpha = 0.0
    /// Reduced motion crossfades the whole pile.
    var papersAlpha = 1.0
    /// The paper the caption is about, and the opacity / rise of its four rows (kicker, title, summary, dashes).
    var caption = 1
    var rows: [Double] = [0, 0, 0, 0]
    var rise: [Double] = [0, 0, 0, 0]
    var page = 1.0
    /// 0 = pile mode in the toggle, 1 = reader mode.
    var toggle = 0.0
}

/// Everything that drives a frame, as times and values: a pure input to `NewspaperPileReaderModel.frame`.
struct NewspaperPileReaderState {
    var openAt: Double?
    var closeAt: Double?
    var sel = 1
    var closeSel = 1
    var pageFrom = 1.0
    var pageTo = 1.0
    var pageAt = -1e9
    var pageLive: Double?
    var dragLive: Double?
    var dragFrom = 0.0
    var dragAt = -1e9
    var closeDrag = 0.0
    var riffleOn = false
    var riffleDx = 0.0
    var riffleY = 0.0
    var riffleVx = 0.0
    var riffleFromDx = [Double](repeating: 0, count: 6)
    var riffleFromRot = [Double](repeating: 0, count: 6)
    var riffleFromLift = [Double](repeating: 0, count: 6)
    var riffleAt = -1e9
    var autoRiffle: Double?
    var reduced = false
}

struct NewspaperPileReaderGeo {
    let w: Double
    let h: Double
    let top: Double
    let bottom: Double

    var cx: Double { w / 2 }
    var pileTop: Double { top + 96 }
    var barY: Double { h - max(bottom, 16) - 30 }
    var gap: Double { (barY - 4 - pileTop - 98) / 5 }
    var openY: Double { top + 222 }
    var paperBottom: Double { openY + 114 }
    var captionTop: Double { paperBottom + 30 }
    var dashY: Double { min(captionTop + 200, barY - 70) }

    func pileX(_ i: Int) -> Double { cx + NewspaperPileReaderModel.jx[i] }
    func pileY(_ i: Int) -> Double { pileTop + 98 + Double(i) * gap + NewspaperPileReaderModel.jy[i] }
}

enum NewspaperPileReaderModel {
    static let loop = 9.0

    static let paperW = 340.0
    static let paperH = 228.0
    static let pileScale = 0.86
    static let pageStride = 170 + 14 + 0.9 * 170.0
    static let jx: [Double] = [-5, 7, -8, 4, -3, 6]
    static let jy: [Double] = [0, 4, -3, 5, -2, 0]
    static let pileRot: [Double] = [-2.2, 1.6, -3.0, 2.4, -1.4, 2.0]

    // Loop beats (s).
    static let riffleAt = 1.0
    static let openAt = 2.6
    static let closeAt = 6.0
    static let idleBeforeBack = 2.0

    static let papers: [NewspaperPileReaderPaper] = [
        NewspaperPileReaderPaper(
            masthead: "Evening Lantern", style: 0, headline: "SIGNING OFF", deck: "Keeper of Gull Point, 41 years",
            kicker: "EVENING LANTERN · 4 MIN READ", title: "Lighthouse keeper signs off after 41 years",
            summary: "Ada Morrow climbed the 212 steps of Gull Point every night since 1985. On Saturday the light goes automatic.",
            tone: 0xF3F1EA),
        NewspaperPileReaderPaper(
            masthead: "THE QUAYSIDE JOURNAL.", style: 1, headline: "Port Cities Bet on Electric Ferries", deck: "Spring Fares Fall 12%",
            kicker: "THE QUAYSIDE JOURNAL · 6 MIN READ", title: "Port cities bet on electric ferries",
            summary: "Three harbors retire their diesel fleets by 2029. Fares fall 12% this spring as the first quiet crossings begin.",
            tone: 0xEFECE4),
        NewspaperPileReaderPaper(
            masthead: "The Larkmoor Herald", style: 2, headline: "Night Market Returns to the Old Docks", deck: "",
            kicker: "THE LARKMOOR HERALD · 3 MIN READ", title: "Night market returns to the old docks",
            summary: "Forty stalls, a string of lights and a brass band: the Friday market is back after six years, with late ferries home.",
            tone: 0xF2EFE6),
        NewspaperPileReaderPaper(
            masthead: "The Fernhill Post", style: 3, headline: "Rail Line Reopens After Storm Repairs", deck: "",
            kicker: "THE FERNHILL POST · 5 MIN READ", title: "Rail line reopens after storm repairs",
            summary: "Trains run again between Fernhill and Copper Bay after eleven weeks of work on the washed-out embankment.",
            tone: 0xEEEDE8),
        NewspaperPileReaderPaper(
            masthead: "Saltday", style: 4, headline: "Tides of Change on the North Shore", deck: "",
            kicker: "SALTDAY · 7 MIN READ", title: "Tides of change on the north shore",
            summary: "A new seawall, a wider beach and a boardwalk that floats: how one coastal town plans for the next fifty winters.",
            tone: 0xF1EEE5),
        NewspaperPileReaderPaper(
            masthead: "Orrin Bay Gazette", style: 5, headline: "Gardens Bloom a Month Early", deck: "",
            kicker: "ORRIN BAY GAZETTE · 2 MIN READ", title: "Gardens bloom a month early",
            summary: "A warm March has the magnolias out ahead of the festival, so the botanic garden opens its gates this weekend.",
            tone: 0xEDEAE1),
    ]

    // MARK: Curves

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    /// 0 before a, 1 after b.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    /// A damped spring as a curve: ≈ 3.5 % overshoot at x ≈ 0.42, exactly 1 from x = 1.
    static func spr(_ x: Double) -> Double { x >= 1 ? 1 : 1 - exp(-8 * x) * cos(7.5 * x) }
    /// A riffle pulse: out, back, a small overshoot, 0 at both ends.
    static func rif(_ x: Double) -> Double { x <= 0 || x >= 1 ? 0 : sin(1.25 * .pi * x) * sqrt(1 - x) / 0.8 }
    static func hash(_ n: Double) -> Double {
        let v = sin(n * 12.9898) * 43758.5453
        return v - floor(v)
    }

    // MARK: Frame

    static func autoState() -> NewspaperPileReaderState {
        var s = NewspaperPileReaderState()
        s.autoRiffle = riffleAt
        s.openAt = openAt
        s.closeAt = closeAt
        return s
    }

    static func page(_ s: NewspaperPileReaderState, _ t: Double) -> Double {
        if let p = s.pageLive { return p }
        return lerp(s.pageFrom, s.pageTo, spr(seg(t, s.pageAt, s.pageAt + 0.5)))
    }

    static func openSpring(_ s: NewspaperPileReaderState, _ t: Double, _ i: Int) -> Double {
        guard let a = s.openAt else { return 0 }
        if s.reduced { return t >= a + 0.2 ? 1 : 0 }
        let lead = i == s.sel
        let d = lead ? 0 : 0.06 + 0.045 * Double(abs(i - s.sel))
        let start = a + 0.1 + d
        return spr(seg(t, start, start + (lead ? 0.75 : 0.6)))
    }

    static func closeSpring(_ s: NewspaperPileReaderState, _ t: Double, _ i: Int) -> Double {
        guard let b = s.closeAt else { return 0 }
        if s.reduced { return t >= b + 0.2 ? 1 : 0 }
        let lead = i == s.closeSel
        let start = b + (lead ? 0.05 : 0.15 + 0.045 * Double(abs(i - s.closeSel)))
        return spr(seg(t, start, start + (lead ? 0.75 : 0.65)))
    }

    /// 0 = in the pile, 1 = in the reader (may overshoot either way).
    static func morph(_ s: NewspaperPileReaderState, _ t: Double, _ i: Int) -> Double {
        openSpring(s, t, i) * (1 - closeSpring(s, t, i))
    }

    static func dragOffset(_ s: NewspaperPileReaderState, _ t: Double) -> Double {
        if let d = s.dragLive { return d }
        if s.closeAt != nil { return s.closeDrag * (1 - clamp01(closeSpring(s, t, s.closeSel))) }
        return s.dragFrom * (1 - spr(seg(t, s.dragAt, s.dragAt + 0.45)))
    }

    static func riffleShift(_ dx: Double) -> Double { dx * 0.55 / (1 + abs(dx) / 240) }

    /// The finger's part of the riffle: papers near the finger follow it most.
    static func fingerRiffle(_ s: NewspaperPileReaderState, _ i: Int, _ g: NewspaperPileReaderGeo) -> (Double, Double, Double) {
        let w = exp(-pow((g.pileY(i) - s.riffleY) / 110, 2))
        let shift = riffleShift(s.riffleDx)
        return (shift * w, (shift * 0.1 + min(max(s.riffleVx * 0.0015, -4), 4)) * w, 0.35 * min(1, abs(shift) / 22) * w)
    }

    /// Sideways shift, extra rotation and lift of paper i from the riffle (loop wave, finger or release).
    static func riffle(_ s: NewspaperPileReaderState, _ t: Double, _ i: Int, _ g: NewspaperPileReaderGeo) -> (Double, Double, Double) {
        var dx = 0.0
        var rot = 0.0
        var lift = 0.0
        if let a = s.autoRiffle, !s.reduced {
            let x = seg(t, a + 0.07 * Double(i), a + 0.07 * Double(i) + 0.6)
            let r = rif(x)
            dx += 22 * r
            rot += 3 * r
            lift += 0.35 * max(r, 0)
        }
        if s.riffleOn {
            let (fdx, frot, flift) = fingerRiffle(s, i, g)
            dx += fdx
            rot += frot
            lift += flift
        } else {
            let k = 1 - spr(seg(t, s.riffleAt, s.riffleAt + 0.6))
            dx += s.riffleFromDx[i] * k
            rot += s.riffleFromRot[i] * k
            lift += s.riffleFromLift[i] * k
        }
        return (dx, rot, lift)
    }

    static func frame(_ s: NewspaperPileReaderState, _ t: Double, _ g: NewspaperPileReaderGeo) -> NewspaperPileReaderFrame {
        var f = NewspaperPileReaderFrame()
        let n = papers.count
        let p = page(s, t)
        let rp = min(max(Int(p.rounded()), 0), n - 1)
        let lead = s.closeAt != nil ? s.closeSel : s.sel
        let drag = dragOffset(s, t)
        f.page = p
        f.caption = rp
        for i in 0..<n {
            let m = morph(s, t, i)
            let mc = clamp01(m)
            let (rdx, rrot, rlift) = riffle(s, t, i, g)
            let di = Double(i) - p
            let d = min(1, abs(di))
            let near = max(0, 1 - abs(di))
            let sgn: Double = di > 0 ? 1 : (di < 0 ? -1 : 0)
            let ox = g.cx + di * pageStride
            let oy = g.openY + 10 * d + drag * near
            let os = 1 - 0.1 * d - drag / 1500 * near
            let orot = -0.6 * (1 - d) + 3 * sgn * d
            var sh = NewspaperPileReaderSheet(
                x: lerp(g.pileX(i) + rdx, ox, m),
                y: lerp(g.pileY(i), oy, m),
                scale: lerp(pileScale, os, m),
                rot: lerp(pileRot[i] + rrot, orot, m),
                lift: mc * near + rlift)
            if i == lead {
                sh.scale += 0.04 * sin(.pi * mc)
            } else {
                sh.rot += 6 * (i > lead ? 1 : -1) * sin(.pi * mc)
            }
            if i == s.sel, let a = s.openAt, !s.reduced {
                sh.scale *= 1 - 0.025 * sin(.pi * seg(t, a, a + 0.14))
            }
            f.sheets.append(sh)
        }
        if let a = s.openAt {
            if s.reduced {
                f.papersAlpha = 1 - sin(.pi * seg(t, a, a + 0.4))
                if let b = s.closeAt { f.papersAlpha *= 1 - sin(.pi * seg(t, b, b + 0.4)) }
            } else {
                f.raised = s.closeAt != nil ? s.closeSel : rp
                f.raisedAlpha = seg(t, a + 0.1, a + 0.2) * (s.closeAt.map { 1 - seg(t, $0, $0 + 0.12) } ?? 1)
            }
            let out = s.closeAt.map { s.reduced ? seg(t, $0, $0 + 0.3) : easeInCubic(seg(t, $0, $0 + 0.2)) } ?? 0
            let pageFade = clamp01(1 - 2 * abs(p - Double(rp)))
            let dragFade = 1 - min(1, max(0, drag) / 120)
            for k in 0..<4 {
                let e: Double
                if s.reduced {
                    e = seg(t, a + 0.2, a + 0.5)
                } else {
                    let a0 = a + 0.45 + 0.06 * Double(k)
                    e = easeOutCubic(seg(t, a0, a0 + 0.4))
                }
                f.rows[k] = e * (1 - out) * dragFade * (k == 3 ? 1 : pageFade)
                f.rise[k] = s.reduced ? 0 : 8 * (1 - e)
            }
        }
        f.toggle = easeInOutCubic(clamp01(morph(s, t, lead)))
        return f
    }

    /// The front-most paper under a point in the pile, or nil.
    static func hit(_ f: NewspaperPileReaderFrame, _ x: Double, _ y: Double) -> Int? {
        for i in stride(from: f.sheets.count - 1, through: 0, by: -1) {
            let sh = f.sheets[i]
            let a = -sh.rot * .pi / 180
            let dx = x - sh.x
            let dy = y - sh.y
            let lx = (dx * cos(a) - dy * sin(a)) / sh.scale
            let ly = (dx * sin(a) + dy * cos(a)) / sh.scale
            if abs(lx) <= paperW / 2 && abs(ly) <= paperH / 2 { return i }
        }
        return nil
    }
}
