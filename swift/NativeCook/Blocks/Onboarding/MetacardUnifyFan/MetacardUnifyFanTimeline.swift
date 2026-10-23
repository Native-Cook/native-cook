import Foundation

/// Design-point geometry and the source's reversible 1,900 ms transformation.
enum MetacardUnifyFanTimeline {
    typealias Vector = SIMD4<Float>

    static func vector(_ x: Double = 0, _ y: Double = 0, _ z: Double = 0, _ w: Double = 0) -> Vector {
        Vector(Float(x), Float(y), Float(z), Float(w))
    }

    static let colors: [Vector] = [
        vector(1, 107 / 255, 26 / 255), vector(234 / 255, 17 / 255, 121 / 255),
        vector(61 / 255, 107 / 255, 224 / 255), vector(57 / 255, 194 / 255, 180 / 255),
        vector(244 / 255 * 0.55, 244 / 255 * 0.55, 242 / 255 * 0.55),
        vector(242 / 255, 178 / 255, 74 / 255), vector(122 / 255, 43 / 255, 226 / 255),
        vector(53 / 255, 208 / 255, 127 / 255)
    ]

    static func clamp(_ x: Double, _ low: Double = 0, _ high: Double = 1) -> Double { max(low, min(high, x)) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func phase(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func radians(_ x: Double) -> Double { x * .pi / 180 }
    static func cubic(_ p: Double, _ a: Double, _ b: Double) -> Double {
        3 * (1 - p) * (1 - p) * p * a + 3 * (1 - p) * p * p * b + p * p * p
    }
    static func bezier(_ p: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        guard p > 0 && p < 1 else { return clamp(p) }
        var low = 0.0
        var high = 1.0
        for _ in 0..<16 {
            let middle = (low + high) / 2
            if cubic(middle, x1, x2) < p { low = middle } else { high = middle }
        }
        return cubic((low + high) / 2, y1, y2)
    }
    static func ease(_ p: Double) -> Double { bezier(p, 0.23, 1, 0.32, 1) }
    static func fold(_ p: Double) -> Double { bezier(p, 0.77, 0, 0.175, 1) }
    static func spring(_ seconds: Double) -> Double {
        let t = max(0, seconds)
        let z = 0.62
        let w = 9.5
        let wd = w * sqrt(1 - z * z)
        return 1 - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
    }
    static func lift(_ t: Double) -> Double {
        mix(spring((t - 1060) / 1000), 1, ease(phase(t, 1800, 1900)))
    }
    static func loop(_ seconds: Double) -> (u: Double, forward: Double, spin: Double) {
        let t = seconds.truncatingRemainder(dividingBy: 12)
        let u = t < 3 ? 0 : t < 4.9 ? (t - 3) / 1.9 : t < 9.4 ? 1 : t < 10.8 ? 1 - (t - 9.4) / 1.4 : 0
        return (clamp(u), t < 9.4 ? 1 : 0, -49 + 7.5 * (t < 7 ? t : t - 12))
    }
    static func reducedLoop(_ t: Double) -> (u: Double, press: Double) {
        let u = t < 3.12 ? 0 : t < 4.9 ? phase(t, 3.12, 3.44) : t < 9.52 ? 1 : 1 - phase(t, 9.52, 9.84)
        let press = t < 9.4 ? phase(t, 3, 3.12) * (1 - phase(t, 3.12, 3.30)) : phase(t, 9.4, 9.52) * (1 - phase(t, 9.52, 9.70))
        return (u, press)
    }

    struct Card {
        var x: Double
        var y: Double
        var rotation: Double
        var scale: Double
        var opacity: Double
        var progress: Double
    }
    static func card(_ i: Int, _ spin: Double, _ u: Double, _ anchor: Double) -> Card {
        let t = u * 1900
        let start = 260 + 24 * Double(i)
        let end = start + 500
        let p = fold(phase(t, start, end))
        let breath = ease(phase(t, 0, 260))
        let angle = spin + 45 * Double(i) - 5 * breath
        let orbit = radians(angle + 135 * p)
        let radius = (132 + 8 * breath) * (1 - p) * mix(1, 176 / 144, p)
        let target = ceil((anchor + 45 * Double(i)) / 180) * 180
        let square = fold(phase(t, 1025, 1110))
        let residual = (Double(i) - 3.5) * 11 * mix(1, 0.85, phase(t, end, 1025))
        let overshoot = 7 * p * (1 - ease(phase(t, end, end + 160)))
        let spread = fold(phase(t, end - 150, end))
        return Card(
            x: 201 + sin(orbit) * radius,
            y: 356 - cos(orbit) * radius + 166 * lift(t),
            rotation: mix(mix(angle - 90, target, p) + residual * spread + overshoot, target, square),
            scale: mix(1, 176 / 144, p) * (1 + 0.16 * spread * (1 - square)),
            opacity: 1 - phase(t, 1100, 1110), progress: p
        )
    }
    static func fan(_ i: Int, _ spin: Double, _ u: Double, _ anchor: Double, _ press: Double, seam: Bool = false) -> [Vector] {
        let p = card(i, spin, u, anchor)
        let first = card(0, spin, u, anchor)
        let last = card(7, spin, u, anchor)
        let gloss = [0.22, 0.34, 0.28, 0.10, 0.16, 0.40, 0.24, 0.10][i]
        return [vector(p.x, p.y), vector(radians(p.rotation)), vector(p.scale), vector(first.scale), vector(last.scale),
                vector(p.opacity), vector(gloss), vector(i == 2 ? 0.6 : 0), vector(1 - p.progress), vector(press),
                vector(seam ? 1 : 0), vector(i == 7 ? 1 : 0), vector(first.x, first.y, radians(first.rotation)),
                vector(last.x, last.y, radians(last.rotation)), vector(u * 1900 < 505 ? 1 : 0),
                vector(1 - ease(phase(u * 1900, 495, 505)))]
    }
    static func rotation(_ yaw: Double, _ pitch: Double, _ roll: Double) -> [Double] {
        let y = radians(yaw)
        let p = radians(pitch)
        let r = radians(roll)
        let cy = cos(y), sy = sin(y), cp = cos(p), sp = sin(p), cr = cos(r), sr = sin(r)
        return [cr * cy, sr * cy, -sy, cr * sy * sp - sr * cp, sr * sy * sp + cr * cp, cy * sp,
                cr * sy * cp + sr * sp, sr * sy * cp - cr * sp, cy * cp]
    }
    static func reflection(_ r: [Double], _ x: Double, _ y: Double) -> Double {
        let wx = r[0] * x + r[3] * y
        let wy = r[1] * x + r[4] * y
        let wz = r[2] * x + r[5] * y - 555
        let length = sqrt(wx * wx + wy * wy + wz * wz)
        let dot = (wx * r[6] + wy * r[7] + wz * r[8]) / length
        return wy / length - 2 * dot * r[7] + 0.9 * (wx / length - 2 * dot * r[6])
    }

    struct Hero {
        var uniforms: [Vector]
        var shadow: [Vector]
        var inner: [Vector]
        var bloom: [Vector]
    }
    static func hero(_ u: Double, _ spin: Double, _ time: Double, _ forward: Double,
                     _ pressed: Double, _ dragYaw: Double, _ dragPitch: Double, reduced: Bool) -> Hero {
        let t = u * 1900
        let morph = fold(phase(t, 300, 760))
        let press = t < 140 ? ease(phase(t, 0, 140)) : 1 - ease(phase(t, 140, 300))
        var bumps = 0.0
        if forward > 0 {
            for i in 0..<8 {
                let b = phase(t, 760 + 24 * Double(i), 850 + 24 * Double(i))
                bumps += sin(.pi * b) * (1 - b) * 0.024
            }
        }
        let lock = forward > 0 ? sin(.pi * phase(t, 1025, 1110)) * 0.03 : 0
        let scale = (1 - 0.06 * press + bumps - lock) * (1 - mix(0.06, 0.03, morph) * pressed)
        let turn = spin - 5 * ease(phase(t, 0, 260))
        let hubRoll = (turn - floor(turn / 180 + 0.5) * 180) * (1 - morph)
        let markProgress = fold(phase(t, 620, 1000))
        let s = lift(t)
        let idle = ease(phase(t, 1800, 1900))
        let width = mix(mix(134, 184, fold(phase(t, 260, 760))), 304, s)
        let height = mix(mix(85, 116.6, fold(phase(t, 260, 760))), 192, s)
        let yaw = -26 * s + (dragYaw + (reduced ? 0 : 6 * sin(time * .pi * 2 / 6.4))) * idle
        let pitch = 13 * s + (dragPitch + (reduced ? 0 : 2 * sin(time * .pi * 2 / 5.2 + 1))) * idle
        let roll = hubRoll - 1.5 * s
        let y = mix(356, 522, s) + (reduced ? 0 : 3 * sin(time * .pi * 2 / 5.8) * idle)
        let rot = rotation(yaw, pitch, roll)
        let hx = width / 2 * scale
        let hy = height / 2 * scale
        var left = Double.infinity, right = -Double.infinity, top = Double.infinity, bottom = -Double.infinity
        for i in 0..<4 {
            let x = (i < 2 ? -1.0 : 1.0) * hx
            let y = (i % 2 == 1 ? -1.0 : 1.0) * hy
            let projection = 555 / (555 - rot[2] * x - rot[5] * y)
            let px = (rot[0] * x + rot[3] * y) * projection
            let py = (rot[1] * x + rot[4] * y) * projection
            left = min(left, px)
            right = max(right, px)
            top = min(top, py)
            bottom = max(bottom, py)
        }
        let centre = vector(201 - (left + right) / 2, y - bumps * 75 - (top + bottom) / 2)
        let halfSize = vector(hx, hy)
        let corner = mix(9.6, 13, clamp(s)) * scale
        let matrices = [vector(rot[0], rot[1], rot[2]), vector(rot[3], rot[4], rot[5]), vector(rot[6], rot[7], rot[8])]
        let one = forward > 0 && !reduced ? 1.0 : 0.0
        let fraction = 0.46 - 0.16 * sin(clamp((yaw + 26) / 6, -1, 1) * .pi / 2)
        let along = fraction * 2 - 1
        let lead = 80 / hypot(hx * 2, hy * 2)
        let studio = vector(mix(-1.1, reflection(rot, hx * along, hy * along), clamp(s)),
                            mix(-1.06, reflection(rot, hx * (along + lead), hy * (along + lead)), clamp(s)))
        let size = width / 304
        let mark = vector(110 * size * markProgress * scale, -54 * size * markProgress * scale,
                          mix(34, 36 * size, markProgress) * scale)
        let uniforms = [centre, halfSize, vector(62 * scale), vector(morph), vector(corner), vector(3.2), vector(555)]
            + matrices + [mark, vector(-radians(roll) * (1 - markProgress)), vector(ease(phase(t, 1150, 1500))),
                          vector(one * (t >= 1060 ? 1 - ease(phase(t, 1300, 1900)) : 0)), vector(phase(t, 1080, 1700)),
                          vector(one * ease(phase(t, 1080, 1110)) * (1 - phase(t, 1240, 1700))), studio] + colors
        let inner = one * 0.85 * ease(phase(t, 1025, 1040)) * (1 - phase(t, 1040, 1180))
        let bloom = one * 0.8 * ease(phase(t, 1025, 1040)) * pow(1 - phase(t, 1040, 1500), 2)
            * mix(1, 0.45, ease(phase(t, 1040, 1110)))
        return Hero(uniforms: uniforms,
                    shadow: [centre, halfSize, vector(morph), vector(mix(10, 36, clamp(s)))],
                    inner: [centre, halfSize, vector(corner)] + matrices + [vector(555), vector(inner), vector(radians(spin))] + colors,
                    bloom: [vector(bloom), vector(mix(46, 150, phase(t, 1040, 1500))), centre, halfSize, vector(corner), vector(radians(spin))] + colors)
    }

    static func bounce(_ u: Double, _ spin: Double, _ anchor: Double, reduced: Bool) -> [Vector] {
        let progress = reduced ? 0 : u
        let pools = (0..<8).map { i -> Vector in
            let p = card(i, spin, progress, anchor)
            let orbit = radians(spin + 45 * Double(i) - 5 * ease(phase(progress * 1900, 0, 260)) + 135 * p.progress)
            let offset = 84 * (1 - p.progress)
            return vector(p.x + sin(orbit) * offset, p.y - cos(orbit) * offset)
        }
        return pools + colors + [vector(reduced ? 1 - u : 1 - phase(u * 1900, 1025, 1110)), vector(reduced ? 1 : 1 - phase(u * 1900, 300, 900))]
    }

    static func text(_ index: Int, _ u: Double, reduced: Bool) -> (alpha: Double, offset: Double, blur: Double) {
        let t = u * 1900
        if reduced { return (index < 2 || index == 5 ? 1 - u : u, 0, 0) }
        if index < 2 || index == 5 {
            let out = ease(phase(t, index == 5 ? 100 : 60, index == 5 ? 340 : 300))
            return (1 - out, -6 * out, 6 * out)
        }
        let start = index == 4 ? 1350.0 : index == 6 ? 1470.0 : 1430.0
        let end = index == 4 ? 1800.0 : index == 6 ? 1920.0 : 1880.0
        let incoming = ease(phase(t, start, end))
        return (incoming, 10 * (1 - incoming), 6 * (1 - incoming))
    }
}
