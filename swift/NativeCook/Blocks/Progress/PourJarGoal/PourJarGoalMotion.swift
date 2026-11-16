import Foundation

/// pjg-motion.ts / pjg-vessel.ts: Double precision and the same integration order.
enum PourJarGoalMotion {
    static let loop = 8.0
    static let start = 2240.0, maximum = 3100.0, step = 10.0
    static let snap = (damping: 30.5, stiffness: 322.3, mass: 1.0)
    static let glide = (damping: 22.1, stiffness: 157.9, mass: 1.0)
    static let toastSpring = (damping: 16.0, stiffness: 220.0, mass: 1.0)
    static let easingOut = [0.2, 0.8, 0.2, 1.0], easingInOut = [0.65, 0.0, 0.35, 1.0]
    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, x)) }
    static func smooth(_ x: Double) -> Double { let a = clamp(x); return a * a * (3 - 2 * a) }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { smooth((t - a) / (b - a)) }
    static func springAt(_ t: Double, _ stiffness: Double = 220, _ damping: Double = 16) -> Double {
        guard t > 0 else { return 0 }
        let w = sqrt(stiffness), z = damping / (2 * w), wd = w * sqrt(max(0.001, 1 - z * z))
        return 1 - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
    }
    struct Timeline {
        var t: Double, lift: Double, grip: Double, toast: Double, bubbleTime: Double, impact: Double
    }
    static func timeline(_ time: Double) -> Timeline {
        let s = (time.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        let rise = segment(s, 1.6, 2.8)
        return Timeline(t: s,
            lift: (1 - (1 - rise) * (1 - rise)) * (1 - segment(s, 5.6, 7.2)),
            grip: segment(s, 0.5, 0.64) * (1 - segment(s, 2.8, 2.96)),
            toast: clamp(springAt(s - 3.3)) * (1 - segment(s, 5.4, 5.74)),
            bubbleTime: segment(s, 0.5, 0.8) * min(max(s - 0.5, 0), 4.8) * (1 - segment(s, 5.6, 7.2)),
            impact: exp(-pow((s - 2.94) / 0.2, 2)))
    }

    struct Field {
        var h = Array(repeating: 0.0, count: 64)
        var v = Array(repeating: 0.0, count: 64)
        var work = Array(repeating: 0.0, count: 64)
        var velocityWork = Array(repeating: 0.0, count: 64)
        var output = Array(repeating: 0.0, count: 64)
        var n = 64, energy = 0.0

        mutating func impulse(_ x: Double, _ velocity: Double, _ width: Double) {
            for i in 0..<n {
                v[i] += clamp(velocity, -900, 900) * exp(-pow((Double(i) * width / Double(n - 1) - x) / 14, 2))
            }
        }
        mutating func step(_ dt: Double, _ width: Double, _ damping: Double,
                           _ fingerX: Double = -1000, _ mound: Double = 0,
                           _ secondX: Double = -1000, _ columns: Int = 64, _ secondMound: Double? = nil) {
            let count = min(64, max(32, columns)), mound2 = secondMound ?? mound
            if n != count {
                for i in 0..<count {
                    let u = Double(i) / Double(count - 1) * Double(n - 1)
                    let a = Int(floor(u)), b = min(n - 1, a + 1)
                    work[i] = h[a] + (h[b] - h[a]) * (u - Double(a))
                    velocityWork[i] = v[a] + (v[b] - v[a]) * (u - Double(a))
                }
                for i in 0..<count { h[i] = work[i]; v[i] = velocityWork[i] }
                n = count
            }
            let dx = max(8, width) / Double(count - 1), c2 = 520.0 * 520 / (dx * dx)
            let substeps = max(1, Int(ceil(520 * dt / dx / 0.75))), hdt = dt / Double(substeps)
            for _ in 0..<substeps {
                var mean = 0.0, velocityMean = 0.0, maxEnergy = 0.0
                for i in 0..<count {
                    let l = h[i == 0 ? 1 : i - 1], r = h[i == count - 1 ? count - 2 : i + 1]
                    let x = Double(i) * dx
                    let g = exp(-pow((x - fingerX) / 22, 2)) + exp(-pow((x - secondX) / 22, 2))
                    let secondG = exp(-pow((x - secondX) / 22, 2))
                    let force = mound * (g - secondG) + mound2 * secondG
                    let acceleration = (l + r - 2 * h[i]) * c2
                        - (damping + 18 * g * min(1, abs(mound))) * v[i]
                        + (force - h[i]) * (mound != 0 || mound2 != 0 ? 1800 : 0)
                    v[i] += acceleration * hdt
                    work[i] = clamp(h[i] + v[i] * hdt, -48, 48)
                    mean += work[i]; velocityMean += v[i]
                }
                mean /= Double(count); velocityMean /= Double(count)
                for i in 0..<count {
                    h[i] = work[i] - mean; v[i] = clamp(v[i] - velocityMean, -1200, 1200)
                    maxEnergy = max(maxEnergy, abs(v[i]))
                }
                energy = maxEnergy
            }
            var mean = 0.0
            for i in 0..<64 {
                let u = Double(i) / 63 * Double(count - 1), a = Int(floor(u))
                let b = min(count - 1, a + 1)
                output[i] = h[a] + (h[b] - h[a]) * (u - Double(a)); mean += output[i]
            }
            for i in 0..<64 { output[i] -= mean / 64 }
        }
    }

    static let loopFields: [[Double]] = {
        var f = Field(), frames: [[Double]] = []
        for n in 0...960 {
            let t = Double(n) / 120, k = timeline(t)
            if n == 336 { f.impulse(108, 280, 216) }
            f.step(1.0 / 120, 216, 3.2, 108, k.grip * 24)
            let fade = 1 - segment(t, 6.6, 7.2)
            frames.append(f.output.map { $0 * fade })
        }
        frames[0] = Array(repeating: 0, count: 64)
        frames[960] = Array(repeating: 0, count: 64)
        return frames
    }()

    struct Vessel {
        let width = 222.0, height = 422.0, topRadius = 68.0, bottomRadius = 70.0
        var left: [Double] = [], right: [Double] = [], volume = Array(repeating: 0.0, count: 128)
        var total = 0.0
        init() {
            for row in 0..<128 {
                let y = (Double(row) + 0.5) / 128 * height
                var l = 0.0
                if y < topRadius { l = topRadius - sqrt(max(0, topRadius * topRadius - pow(y - topRadius, 2))) }
                if y > height - bottomRadius {
                    l = bottomRadius - sqrt(max(0, bottomRadius * bottomRadius - pow(y - height + bottomRadius, 2)))
                }
                left.append(l); right.append(width - l)
            }
            for row in stride(from: 127, through: 0, by: -1) {
                total += (right[row] - left[row]) * height / 128; volume[row] = total
            }
        }
        func volumeY(_ fraction: Double) -> Double {
            let target = clamp(fraction) * total
            if target <= 0 { return height }
            if target >= total { return 0 }
            if target <= volume[127] { return (128 - target / volume[127]) / 128 * height }
            var lo = 0, hi = 127
            while hi - lo > 1 {
                let m = (lo + hi) >> 1
                if volume[m] > target { lo = m } else { hi = m }
            }
            let a = volume[lo], b = volume[hi]
            return (Double(lo) + (a - target) / max(a - b, 0.001)) / 128 * height
        }
        func yVolume(_ y: Double) -> Double {
            let row = clamp(y / height * 128, 0, 128), i = min(127, Int(floor(row)))
            let next = i == 127 ? 0 : volume[i + 1]
            return (volume[i] + (next - volume[i]) * (row - Double(i))) / total
        }
    }
    static let vessel = Vessel()
    static let seeds: [[Double]] = [[0.541,0.75,4.5],[0.384,0.572,8.9],[0.623,0.484,7.2],
        [0.246,0.395,4.5],[0.493,0.307,2.7],[0.715,0.216,8.9],[0.323,0.125,7.2],
        [0.594,0.036,5.4],[0.809,0.661,2.7],[0.42,0.84,0.8],[0.29,0.45,0.7],[0.76,0.37,0.8]]
    static func bubbles(_ time: Double, _ meanY: Double, _ heights: [Double]) -> [Double] {
        var data: [Double] = []
        for i in 0..<12 {
            let seed = Double(i + 1), pose = seeds[i], radius = pose[2]
            let range = max(vessel.height - meanY - 20, 10)
            var x = pose[0] * vessel.width
            let col = min(62, max(1, Int(floor(x / vessel.width * 63 + 0.5))))
            let slope = (heights[col + 1] - heights[col - 1]) / (vessel.width / 63 * 2)
            let y = vessel.height - 18 - (pose[1] * range + time * (10 + radius * 5)).truncatingRemainder(dividingBy: range)
            x += (sin(seed * 3.1 + time * 2) - sin(seed * 3.1)) * 2 + slope * time * 2
            let row = min(127, max(0, Int(floor(y / vessel.height * 128))))
            let lens = min(x - vessel.left[row], vessel.right[row] - x) < 30 ? 0.85 : 1.0
            data += [x, y, radius, lens]
        }
        return data
    }
    static func quantize(_ x: Double, _ low: Double, _ high: Double) -> Double {
        floor(clamp((x - low) / (high - low)) * 65535 + 0.5) / 65535 * (high - low) + low
    }
    static let shaderWalls = (vessel.left + vessel.right).map { quantize($0, 0, 222) }
    static let shaderLoopFields = loopFields.map { $0.map { quantize($0, -64, 64) } }
    static func rubber(_ value: Double, _ lower: Double, _ upper: Double) -> Double {
        if value < lower { return lower + (value - lower) * 0.25 / (1 + abs(value - lower) / 120) }
        if value > upper { return upper + (value - upper) * 0.3 / (1 + abs(value - upper) / 120) }
        return value
    }
    static func digit(_ value: Double, _ place: Double, _ reduced: Bool) -> Double {
        let n = max(0, value)
        if reduced { return floor(n / place).truncatingRemainder(dividingBy: 10) }
        if place == 1 { return n.truncatingRemainder(dividingBy: 10) }
        let phase = smooth(clamp(n.truncatingRemainder(dividingBy: place) - (place - 1)))
        return floor(n / place).truncatingRemainder(dividingBy: 10) + phase
    }
    static func money(_ value: Double) -> String {
        let s = String(Int(max(0, floor(value + 0.5))))
        var result = ""
        for (i, c) in s.enumerated() {
            if i > 0 && (s.count - i).isMultiple(of: 3) { result += "," }
            result.append(c)
        }
        return "$" + result
    }
}
