import SwiftUI

/*
 The numbers behind Filmstrip Arc Scrubber: the one-minute "video" (a night drive drawn as a
 function of its time), the filmstrip's geometry, the layout and the autoPlay timeline.
 */

struct FilmstripArcScrubberRGB {
    var r: Double, g: Double, b: Double
    init(_ r: Double, _ g: Double, _ b: Double) { self.r = r; self.g = g; self.b = b }
    static func mix(_ a: Self, _ b: Self, _ t: Double) -> Self { Self(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t) }
    func color(_ a: Double = 1) -> Color { Color(red: min(1, max(0, r)), green: min(1, max(0, g)), blue: min(1, max(0, b)), opacity: min(1, max(0, a))) }
}

/// What the screen shows at a moment.
struct FilmstripArcScrubberShown {
    /// The video's time, in seconds (0 – 60).
    var time: Double
    /// How far the filmstrip has lifted out of the bar (0 – 1).
    var lift: Double
    /// The selected frame flying up into the video (0 – 1, 0 when none) and its time.
    var fly: Double
    var flyTime: Double
    /// How fast the time is being scrubbed (seconds per second), for the motion blur.
    var speed: Double
    var playing: Bool
}

enum FilmstripArcScrubberModel {
    typealias RGB = FilmstripArcScrubberRGB
    static let duration = 60.0
    /// One frame of the strip every 2.5 s of video.
    static let frameStep = 2.5
    static let loop = 10.0
    /// Where the loop starts and ends: 0:18 into the video, playing.
    static let restTime = 18.0

    // MARK: Easing

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func easeInOut(_ x: Double) -> Double {
        let u = clamp01(x)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    static func easeOutBack(_ x: Double) -> Double {
        let u = clamp01(x), c1 = 1.5, c3 = c1 + 1
        return 1 + c3 * pow(u - 1, 3) + c1 * pow(u - 1, 2)
    }
    static func wrap(_ t: Double) -> Double {
        var x = t.truncatingRemainder(dividingBy: duration)
        if x < 0 { x += duration }
        return x
    }

    // MARK: autoPlay

    /// The video's time at a moment of the loop: plays 0:18 → 0:20, is held and dragged forward ten
    /// frames into the night (0:45), back to 0:15, released at 7.0 s and plays on to 0:18 at 10 s.
    static func time(at t: Double) -> Double {
        if t < 2 { return restTime + t }
        if t < 2.7 { return 20 }
        if t < 4.5 { return 20 + 25 * easeInOut((t - 2.7) / 1.8) }
        if t < 4.6 { return 45 }
        if t < 6.6 { return 45 - 30 * easeInOut((t - 4.6) / 2.0) }
        if t < 7 { return 15 }
        return 15 + (t - 7)
    }

    /// autoPlay (loop = 10 s):
    ///   0.0 – 2.0   playing from 0:18
    ///   2.0         a finger presses the thumb: playback holds, the strip lifts (0.5 s, ease out back)
    ///   2.7 – 4.5   dragged forward to 0:45 (night);  4.6 – 6.6 back to 0:15 (easeInOut)
    ///   7.0         released: the 0:15 frame flies up into the video (0.6 s) and playback resumes
    ///   7.2 – 7.7   the strip folds back into the bar
    ///   10.0        0:18 again, playing: the same frame as 0
    static func shown(at time0: Double) -> FilmstripArcScrubberShown {
        var t = time0.truncatingRemainder(dividingBy: loop)
        if t < 0 { t += loop }
        let lift = (t >= 2 && t < 7.2) ? easeOutBack((t - 2) / 0.5) : (t >= 7.2 ? 1 - easeInOut((t - 7.2) / 0.5) : 0)
        let speed = (time(at: t + 0.02) - time(at: max(0, t - 0.02))) / 0.04
        let fly = (t >= 7 && t < 7.6) ? easeInOut((t - 7) / 0.6) : 0
        return FilmstripArcScrubberShown(time: time(at: t), lift: lift, fly: fly, flyTime: 15, speed: speed,
                                         playing: t < 2 || t >= 7)
    }

    // MARK: Layout

    struct Layout {
        let w: Double, h: Double, top: Double, bottom: Double
        var video: CGRect { CGRect(x: 12, y: top + 8, width: w - 24, height: h * 0.6 - top - 8) }
        var titleY: Double { video.maxY + 20 }
        var barY: Double { h * 0.785 }
        var barX: Double { 24 }
        var barW: Double { w - 48 }
        var transportY: Double { h - max(bottom, 16) - 50 }
        /// The strip's centre line when lifted.
        var stripY: Double { barY - 98 }
    }

    // MARK: The film strip

    /// A strip cell: the film (64 × 82) with sprocket holes, its picture 56 × 60 at (4, 11).
    static let cellW = 64.0, cellH = 82.0
    static let picX = 4.0, picY = 11.0, picW = 56.0, picH = 60.0
    /// The strip wraps a cylinder of this radius (points), seen in perspective (focal 520).
    static let radius = 230.0
    static let focal = 520.0

    // MARK: The night drive

    /// A small integer hash in 0…1, the same in every language.
    static func hash(_ a: Int, _ b: Int, _ c: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: a &* 73_856_093) ^ UInt32(truncatingIfNeeded: b &* 19_349_663) ^ UInt32(truncatingIfNeeded: c &* 83_492_791)
        x ^= x >> 13
        x = x &* 1_274_126_177
        x ^= x >> 16
        return Double(x & 0xFFFF) / 65535
    }

    /// The light over the minute: golden hour, dusk, night, deep night (sky top, horizon, far hills, near hills).
    static let keys: [(Double, [RGB])] = [
        (0, [RGB(0.98, 0.60, 0.36), RGB(1.0, 0.86, 0.58), RGB(0.62, 0.36, 0.40), RGB(0.34, 0.18, 0.26)]),
        (22, [RGB(0.42, 0.20, 0.52), RGB(1.0, 0.50, 0.42), RGB(0.36, 0.18, 0.36), RGB(0.18, 0.09, 0.20)]),
        (40, [RGB(0.08, 0.08, 0.24), RGB(0.36, 0.22, 0.46), RGB(0.14, 0.10, 0.24), RGB(0.07, 0.05, 0.13)]),
        (60, [RGB(0.02, 0.03, 0.10), RGB(0.12, 0.12, 0.28), RGB(0.07, 0.07, 0.15), RGB(0.03, 0.03, 0.07)]),
    ]

    static func palette(_ t: Double) -> [RGB] {
        for k in 0..<(keys.count - 1) where t <= keys[k + 1].0 {
            let u = smoothstep(keys[k].0, keys[k + 1].0, t)
            return (0..<4).map { RGB.mix(keys[k].1[$0], keys[k + 1].1[$0], u) }
        }
        return keys[keys.count - 1].1
    }

    static let horizon = 0.56

    /// Draws the video at time t into a rect; `lite` leaves out the stars and the lamp posts (strip thumbnails).
    static func drawScene(_ ctx: inout GraphicsContext, _ r: CGRect, time t: Double, lite: Bool) {
        let w = r.width, h = r.height
        func P(_ u: Double, _ v: Double) -> CGPoint { CGPoint(x: r.minX + u * w, y: r.minY + v * h) }
        let pal = palette(t)
        let vh = horizon
        // Sky and ground.
        ctx.fill(Path(r), with: .color(pal[3].color()))
        ctx.fill(Path(CGRect(x: r.minX, y: r.minY, width: w, height: vh * h + 1)),
                 with: .linearGradient(Gradient(colors: [pal[0].color(), pal[1].color()]), startPoint: P(0, 0), endPoint: P(0, vh)))
        // Stars come out as it darkens.
        let starA = smoothstep(26, 44, t)
        if !lite && starA > 0.01 {
            for i in 0..<36 {
                let tw = 0.75 + 0.25 * sin(t * 3 + Double(i))
                let s = 1.2 + 0.8 * hash(i, 2, 5)
                let p = P(hash(i, 1, 5), 0.48 * hash(i, 3, 5))
                ctx.fill(Path(ellipseIn: CGRect(x: p.x - s / 2, y: p.y - s / 2, width: s, height: s)),
                         with: .color(.white.opacity(starA * (0.5 + 0.5 * hash(i, 4, 5)) * tw)))
            }
        }
        // The sun sets by 0:30; the moon rises after 0:34.
        let sunA = 1 - smoothstep(20, 34, t)
        if sunA > 0.01 {
            let sp = P(0.62, 0.30 + 0.34 * t / 30)
            let gr = 0.36 * w
            ctx.fill(Path(ellipseIn: CGRect(x: sp.x - gr, y: sp.y - gr, width: 2 * gr, height: 2 * gr)),
                     with: .radialGradient(Gradient(colors: [Color(red: 1, green: 0.75, blue: 0.45).opacity(0.8 * sunA), .clear]),
                                           center: sp, startRadius: 0, endRadius: gr))
            let sr = 0.07 * w
            ctx.fill(Path(ellipseIn: CGRect(x: sp.x - sr, y: sp.y - sr, width: 2 * sr, height: 2 * sr)),
                     with: .color(Color(red: 1, green: 0.92, blue: 0.72)))
        }
        if t > 34 {
            let mp = P(0.24, 0.62 - 0.4 * smoothstep(34, 60, t))
            let mr = 0.045 * w
            ctx.fill(Path(ellipseIn: CGRect(x: mp.x - mr * 3, y: mp.y - mr * 3, width: mr * 6, height: mr * 6)),
                     with: .radialGradient(Gradient(colors: [Color.white.opacity(0.22), .clear]), center: mp, startRadius: 0, endRadius: mr * 3))
            ctx.fill(Path(ellipseIn: CGRect(x: mp.x - mr, y: mp.y - mr, width: 2 * mr, height: 2 * mr)),
                     with: .color(Color(red: 0.96, green: 0.94, blue: 0.86)))
        }
        // Far mountains, then near hills, drifting past at two speeds.
        let step = lite ? 0.1 : 0.05
        func ridge(_ base: Double, _ a1: Double, _ f1: Double, _ a2: Double, _ f2: Double, _ shift: Double, _ phase: Double) -> Path {
            var p = Path()
            p.move(to: P(-0.05, 1.01))
            var u = -0.05
            while u <= 1.05 + 1e-9 {
                p.addLine(to: P(u, base - a1 * sin((u + shift) * f1 + phase) - a2 * sin((u + shift) * f2 + phase * 2 + 1)))
                u += step
            }
            p.addLine(to: P(1.05, 1.01))
            p.closeSubpath()
            return p
        }
        ctx.fill(ridge(vh - 0.10, 0.06, 7.1, 0.04, 13.7, t * 0.004, 0), with: .color(pal[2].color()))
        ctx.fill(ridge(vh - 0.02, 0.035, 5.3, 0.02, 11, t * 0.012, 2), with: .color(pal[3].color()))
        // The road to the vanishing point.
        var road = Path()
        road.addLines([P(0.49, vh), P(0.51, vh), P(1.15, 1), P(-0.15, 1)])
        road.closeSubpath()
        ctx.fill(road, with: .color(RGB.mix(pal[3], RGB(0.10, 0.09, 0.12), 0.6).color()))
        var edges = Path()
        edges.move(to: P(0.49, vh)); edges.addLine(to: P(-0.15, 1))
        edges.move(to: P(0.51, vh)); edges.addLine(to: P(1.15, 1))
        ctx.stroke(edges, with: .color(Color(red: 0.85, green: 0.8, blue: 0.7).opacity(0.4)), lineWidth: lite ? 0.8 : 1.5)
        // Lane dashes rushing towards the camera.
        for k in 0..<8 {
            var z = (Double(k) / 8 + t * 0.35).truncatingRemainder(dividingBy: 1)
            if z < 0 { z += 1 }
            let d0 = pow(z, 2.2), d1 = pow(min(1, z + 0.045), 2.2)
            let v0 = vh + (1 - vh) * d0, v1 = vh + (1 - vh) * d1
            let w0 = 0.002 + 0.016 * d0, w1 = 0.002 + 0.016 * d1
            var dash = Path()
            dash.addLines([P(0.5 - w0, v0), P(0.5 + w0, v0), P(0.5 + w1, v1), P(0.5 - w1, v1)])
            dash.closeSubpath()
            ctx.fill(dash, with: .color(Color(red: 0.95, green: 0.88, blue: 0.6).opacity(0.85 * smoothstep(0, 0.15, z))))
        }
        // Lamp posts along the right verge, lit as it gets dark.
        let lampA = smoothstep(16, 30, t)
        if !lite {
            for k in 0..<5 {
                var z = (Double(k) / 5 + t * 0.18).truncatingRemainder(dividingBy: 1)
                if z < 0 { z += 1 }
                let d = pow(z, 2.2)
                let base = P(0.5 + 0.08 + 0.62 * d, vh + (1 - vh) * d)
                let top = CGPoint(x: base.x, y: base.y - (0.02 + 0.5 * d) * h)
                var pole = Path()
                pole.move(to: base); pole.addLine(to: top)
                ctx.stroke(pole, with: .color(Color(red: 0.06, green: 0.05, blue: 0.08).opacity(smoothstep(0, 0.1, z))), lineWidth: 0.6 + 3 * d)
                if lampA > 0.01 {
                    let gr = (0.01 + 0.08 * d) * w
                    ctx.fill(Path(ellipseIn: CGRect(x: top.x - gr, y: top.y - gr, width: 2 * gr, height: 2 * gr)),
                             with: .radialGradient(Gradient(colors: [Color(red: 1, green: 0.8, blue: 0.5).opacity(0.85 * lampA * smoothstep(0, 0.1, z)), .clear]),
                                                   center: top, startRadius: 0, endRadius: gr))
                }
            }
        }
        // A car's tail lights ahead.
        let carA = 0.25 + 0.75 * smoothstep(10, 26, t)
        let cv = vh + 0.04 + 0.02 * sin(t * 0.4)
        for dx in [-0.014, 0.014] {
            let c = P(0.5 + dx, cv)
            let gr = 0.022 * w
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - gr, y: c.y - gr, width: 2 * gr, height: 2 * gr)),
                     with: .radialGradient(Gradient(colors: [Color(red: 1, green: 0.15, blue: 0.1).opacity(0.9 * carA), .clear]),
                                           center: c, startRadius: 0, endRadius: gr))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 1.2, y: c.y - 1.2, width: 2.4, height: 2.4)), with: .color(Color(red: 1, green: 0.5, blue: 0.45).opacity(carA)))
        }
        // Vignette.
        ctx.fill(Path(r), with: .radialGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.4)]),
                                                center: P(0.5, 0.5), startRadius: min(w, h) * 0.35, endRadius: max(w, h) * 0.75))
    }

    static func clock(_ t: Double) -> String {
        let s = Int(t)
        return String(format: "%d:%02d", s / 60, s % 60)
    }

    /// Timecode with frames at 24 per second: 00:18:04.
    static func timecode(_ t: Double) -> String {
        let s = Int(t)
        return String(format: "%02d:%02d:%02d", s / 60, s % 60, Int((t - Double(s)) * 24))
    }
}
