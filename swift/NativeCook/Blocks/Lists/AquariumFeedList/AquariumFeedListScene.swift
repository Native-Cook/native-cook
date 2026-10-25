import SwiftUI

/*
 Aquarium Feed List — the loop and the fish, as pure functions of time.

 Six fish cruise on closed Lissajous paths whose frequencies are whole multiples of the loop, so
 every frame at t = loop equals the frame at 0. Two values stir them:
 - `stir` (0–1): how hard the water is being churned by scrolling. Fish scatter outward and away
   from the scroll direction, tails beat wider, bubbles stream up.
 - `feed` (0–1): a card was tapped; fish leave their paths and circle it.
 Under autoPlay both come from the script; under touch from the scroll's speed and the last tap.
*/

struct AquariumFeedListFishSpec {
    /// Body length and depth (pt).
    let len: Double
    let dep: Double
    let colors: [UInt32]
    /// White bands (as fractions of the body from tail to nose), drawn over the body.
    let bands: [Double]
    /// Path centre and reach as fractions of the swimming area; whole-number frequencies.
    let cx: Double
    let cy: Double
    let ax: Double
    let ay: Double
    let kx: Double
    let ky: Double
    let phase: Double
    /// Tail beats per loop (whole).
    let beats: Double
    /// Depth: far fish are smaller and hazier.
    let near: Double
}

struct AquariumFeedListEntry {
    let title: String
    let detail: String
    let when: String
    let tag: String
    let symbol: String
    let tint: UInt32
}

struct AquariumFeedListArea {
    var x = 0.0
    var y = 0.0
    var w = 0.0
    var h = 0.0
}

enum AquariumFeedListScene {
    /// Card rows in the list: a 104 pt card and a 12 pt gap.
    static let cardH = 104.0
    static let cardGap = 12.0
    static let row = cardH + cardGap
    /// The scripted scroll: down to scrollTo over 1.4–4.4 s, back to 0 over 6.6–8.2 s.
    static let scrollTo = 420.0
    /// The card the loop taps at 5.8 s.
    static let tapCard = 4
    static let tapAt = 5.8
    static let header = 150.0

    static let fish: [AquariumFeedListFishSpec] = [
        .init(len: 75, dep: 44, colors: [0xE39A1F, 0xF6C33F, 0xFCE59A], bands: [], cx: 0.5, cy: 0.3, ax: 0.36, ay: 0.12, kx: 1, ky: 2, phase: 0.2, beats: 24, near: 1),
        .init(len: 49, dep: 22, colors: [0x1B4FB0, 0x2F86E4, 0x9ED4FF], bands: [], cx: 0.42, cy: 0.62, ax: 0.32, ay: 0.16, kx: 1, ky: 3, phase: 2.1, beats: 33, near: 0.8),
        .init(len: 58, dep: 29, colors: [0xD24A15, 0xFF7A33, 0xFFC08F], bands: [0.32, 0.66], cx: 0.6, cy: 0.48, ax: 0.3, ay: 0.14, kx: -1, ky: 2, phase: 4.0, beats: 30, near: 0.92),
        .init(len: 70, dep: 25, colors: [0x5A36B8, 0x9A63E6, 0xE6C6FF], bands: [], cx: 0.46, cy: 0.82, ax: 0.38, ay: 0.08, kx: 1, ky: 2, phase: 5.2, beats: 27, near: 0.95),
        .init(len: 38, dep: 16, colors: [0x5E9A2E, 0x9CCF55, 0xE3F5B4], bands: [], cx: 0.3, cy: 0.18, ax: 0.22, ay: 0.08, kx: -2, ky: 3, phase: 1.1, beats: 36, near: 0.66),
        .init(len: 44, dep: 16, colors: [0x6F8897, 0xB9CCD7, 0xF1F7FA], bands: [], cx: 0.68, cy: 0.72, ax: 0.26, ay: 0.12, kx: 2, ky: -3, phase: 3.3, beats: 36, near: 0.72),
    ]

    /// Draw the far fish first.
    static let order: [Int] = fish.indices.sorted { fish[$0].near < fish[$1].near }

    static let entries: [AquariumFeedListEntry] = [
        .init(title: "Fed", detail: "2 cubes mysis, 1 pinch of pellets", when: "08:12", tag: "Feed", symbol: "fish.fill", tint: 0xF6C33F),
        .init(title: "Water test", detail: "pH 8.2 · dKH 8.4 · NO₃ 5 ppm", when: "07:40", tag: "Test", symbol: "testtube.2", tint: 0x7FD6E8),
        .init(title: "Frag added", detail: "Green star polyp, left rock", when: "Yesterday", tag: "Coral", symbol: "leaf.fill", tint: 0x9CCF55),
        .init(title: "Water change", detail: "15 % · 33 L fresh saltwater", when: "Yesterday", tag: "Care", symbol: "drop.fill", tint: 0x5FB2F0),
        .init(title: "Glass scraped", detail: "Front pane, 4 minutes", when: "Tue", tag: "Care", symbol: "paintbrush.pointed.fill", tint: 0xC9A7F5),
        .init(title: "Light schedule", detail: "Sunrise 09:00 · 70 % blue", when: "Tue", tag: "Lights", symbol: "sun.horizon.fill", tint: 0xFFB36B),
        .init(title: "Fed", detail: "Nori clip for the tang", when: "Mon", tag: "Feed", symbol: "fish.fill", tint: 0xF6C33F),
        .init(title: "Temperature dip", detail: "25.1 °C overnight, heater checked", when: "Mon", tag: "Alert", symbol: "thermometer.medium", tint: 0xFF8A73),
        .init(title: "Skimmer cleaned", detail: "Collection cup, two days early", when: "Sun", tag: "Care", symbol: "wind", tint: 0x9FD0DA),
        .init(title: "Snail count", detail: "11 nerites, all accounted for", when: "Sat", tag: "Census", symbol: "checklist", tint: 0xE8D29A),
        .init(title: "Top-off", detail: "1.2 L RO water", when: "Sat", tag: "Care", symbol: "drop.fill", tint: 0x5FB2F0),
        .init(title: "New arrival", detail: "Lavender wrasse, quarantine 3 weeks", when: "Fri", tag: "Fish", symbol: "fish.fill", tint: 0xB07CF0),
    ]

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : (v > 1 ? 1 : v) }

    static func easeInOutCubic(_ v: Double) -> Double {
        let x = clamp01(v)
        if x < 0.5 { return 4 * x * x * x }
        let q = -2 * x + 2
        return 1 - q * q * q / 2
    }

    static func easeOutCubic(_ v: Double) -> Double {
        let q = 1 - clamp01(v)
        return 1 - q * q * q
    }

    static func hash(_ n: Double) -> Double {
        let v = sin(n * 127.1 + 311.7) * 43758.5453
        return v - floor(v)
    }

    /// The list's scroll offset in the loop.
    static func autoScroll(_ t: Double) -> Double {
        scrollTo * (easeInOutCubic((t - 1.4) / 3.0) - easeInOutCubic((t - 6.6) / 1.6))
    }

    /// The loop's stir: a long churn for the first scroll, a shorter one for the way back.
    /// Direction +1 = content moving up.
    static func autoStir(_ t: Double) -> (stir: Double, dir: Double) {
        let a = easeOutCubic((t - 1.5) / 0.7) * (1 - easeInOutCubic((t - 4.0) / 1.5))
        let b = 0.75 * easeOutCubic((t - 6.7) / 0.6) * (1 - easeInOutCubic((t - 7.8) / 0.9))
        return (max(a, b), a >= b ? 1 : -1)
    }

    /// The loop's feeding: fish gather round the tapped card from 5.8 s and leave as the list scrolls back.
    static func autoFeed(_ t: Double) -> Double {
        easeInOutCubic((t - tapAt) / 0.8) * (1 - easeInOutCubic((t - 6.7) / 0.9))
    }

    /// Fish i's centre at fish-time `tau`, stirred by `stir` in direction `dir`, drawn to (tx, ty) by `feed`.
    static func fishAt(_ i: Int, _ tau: Double, _ area: AquariumFeedListArea, _ stir: Double, _ dir: Double, _ feed: Double, _ tx: Double, _ ty: Double) -> CGPoint {
        let f = fish[i]
        let w = 2 * Double.pi * tau / AquariumFeedList.loop
        var x = area.x + area.w * (f.cx + f.ax * sin(f.kx * w + f.phase))
        var y = area.y + area.h * (f.cy + f.ay * sin(f.ky * w + f.phase * 1.7))
        // Scatter: away from the middle of the tank and against the scroll.
        let mx = x - (area.x + area.w / 2)
        let my = y - (area.y + area.h / 2)
        let d = (mx * mx + my * my).squareRoot() + 30
        x += stir * (mx / d) * 70
        y += stir * ((my / d) * 50 - dir * 60)
        // Feeding: each fish takes its own place on a slow ring round the card (two laps a loop).
        let ang = f.phase * 2.3 + 2 * w * (i % 2 == 0 ? 1 : -1)
        let gx = tx + cos(ang) * (110 + 18 * Double(i % 3))
        let gy = ty + sin(ang) * (46 + 8 * Double(i % 2))
        return CGPoint(x: x + (gx - x) * feed, y: y + (gy - y) * feed)
    }

    private static func halfWidth(_ u: Double, _ h: Double) -> Double {
        if u < 0.6 { return h / 2 * (0.16 + 0.84 * sin(Double.pi / 2 * (u / 0.6))) }
        let q = (u - 0.6) / 0.4
        return h / 2 * max(0, 1 - q * q).squareRoot()
    }

    /// A fish's body in its own frame (nose toward +x, centred), its tail and fins swinging with
    /// `phase`; `amp` is the swing as a share of the body's depth.
    static func body(_ f: AquariumFeedListFishSpec, _ phase: Double, _ amp: Double) -> Path {
        let len = f.len
        let h = f.dep
        let n = 14
        var top = [Double](repeating: 0, count: n + 1)
        var bot = top
        var xs = top
        for k in 0...n {
            let u = Double(k) / Double(n)
            let bend = amp * h * sin(phase - u * 4.5) * (1 - u) * (1 - u)
            let w = halfWidth(u, h)
            xs[k] = -len / 2 + u * len
            top[k] = bend - w
            bot[k] = bend + w
        }
        var p = Path()
        p.move(to: CGPoint(x: xs[0], y: top[0]))
        for k in 1...n { p.addLine(to: CGPoint(x: xs[k], y: top[k])) }
        for k in stride(from: n, through: 0, by: -1) { p.addLine(to: CGPoint(x: xs[k], y: bot[k])) }
        p.closeSubpath()
        // Tail: a forked fan that trails the body's swing.
        let tx = xs[0]
        let ty = (top[0] + bot[0]) / 2
        let swing = amp * h * 1.1 * sin(phase + 0.9)
        p.move(to: CGPoint(x: tx + 2, y: ty - h * 0.08))
        p.addQuadCurve(to: CGPoint(x: tx - len * 0.3, y: ty + swing - h * 0.42), control: CGPoint(x: tx - len * 0.18, y: ty + swing * 0.6 - h * 0.2))
        p.addQuadCurve(to: CGPoint(x: tx - len * 0.3, y: ty + swing + h * 0.42), control: CGPoint(x: tx - len * 0.2, y: ty + swing * 0.85))
        p.addQuadCurve(to: CGPoint(x: tx + 2, y: ty + h * 0.08), control: CGPoint(x: tx - len * 0.18, y: ty + swing * 0.6 + h * 0.2))
        p.closeSubpath()
        // Dorsal fin: a soft sail over the back.
        let a = 5
        let c = 10
        p.move(to: CGPoint(x: xs[a], y: top[a] + 1))
        p.addQuadCurve(to: CGPoint(x: xs[c], y: top[c] + 1), control: CGPoint(x: xs[a + 1], y: top[a + 1] - h * 0.42))
        p.closeSubpath()
        return p
    }

    /// The white bands of a banded fish, following the same swing.
    static func bands(_ f: AquariumFeedListFishSpec, _ phase: Double, _ amp: Double) -> Path {
        var p = Path()
        let len = f.len
        let h = f.dep
        for u0 in f.bands {
            var xs: [Double] = []
            var tops: [Double] = []
            var bots: [Double] = []
            for k in 0...3 {
                let u = u0 + Double(k) / 3 * 0.09
                let bend = amp * h * sin(phase - u * 4.5) * (1 - u) * (1 - u)
                let w = halfWidth(u, h)
                xs.append(-len / 2 + u * len)
                tops.append(bend - w * 0.98)
                bots.append(bend + w * 0.98)
            }
            p.move(to: CGPoint(x: xs[0], y: tops[0]))
            for k in 1...3 { p.addLine(to: CGPoint(x: xs[k], y: tops[k])) }
            for k in stride(from: 3, through: 0, by: -1) { p.addLine(to: CGPoint(x: xs[k], y: bots[k])) }
            p.closeSubpath()
        }
        return p
    }

    /// Bubbles: rings and their glints, a steady thin stream from the airstone plus many more while stirred.
    static func bubbles(_ t: Double, _ stir: Double, _ W: Double, _ top: Double, _ bottom: Double) -> (rings: Path, glints: Path) {
        var rings = Path()
        var glints = Path()
        let span = bottom - top
        for j in 0..<54 {
            let jd = Double(j)
            // Periods 2.25, 3 or 4.5 s: whole divisions of the loop.
            let period = j % 3 == 0 ? 2.25 : (j % 3 == 1 ? 3 : 4.5)
            let life = (t / period + hash(jd * 1.7)).truncatingRemainder(dividingBy: 1)
            let air = j < 10
            let on = air ? 0.85 : clamp01(stir * 1.6 - hash(jd * 3.1) * 0.6)
            if on <= 0.02 { continue }
            let r = (air ? 2 + hash(jd) * 2.2 : 2 + hash(jd * 5.3) * 4) * on * min(1, life * 6, (1 - life) * 8)
            if r < 0.4 { continue }
            let x0 = air ? W - 34 + hash(jd * 2.9) * 10 : 24 + hash(jd * 4.4) * (W - 48)
            let x = x0 + sin(2 * Double.pi * (life * 3 + hash(jd * 6.1))) * (3 + r)
            let y = bottom - life * span * (air ? 1 : 0.85)
            rings.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            let g = r * 0.28
            glints.addEllipse(in: CGRect(x: x - r * 0.35 - g, y: y - r * 0.35 - g, width: g * 2, height: g * 2))
        }
        return (rings, glints)
    }

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
    }
}
