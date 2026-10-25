import UIKit

/// Seeded gradient noise baked into grey images, standing in for Skia's turbulence and fractal noise.
enum CrayonBoxPickerNoise {
    private static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Double {
        var h = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263 &+ seed &* 1_442_695_041)
        h = (h ^ (h >> 13)) &* 1_274_126_177
        h ^= h >> 16
        return Double(h & 0xFFFF) / 65535
    }

    private static func grad(_ ix: Int, _ iy: Int, _ seed: Int, _ dx: Double, _ dy: Double) -> Double {
        let a = hash(ix, iy, seed) * 2 * .pi
        return cos(a) * dx + sin(a) * dy
    }

    /// Gradient noise in about -0.7...0.7.
    static func noise(_ x: Double, _ y: Double, _ seed: Int) -> Double {
        let x0 = Int(floor(x)), y0 = Int(floor(y))
        let fx = x - Double(x0), fy = y - Double(y0)
        let u = fx * fx * fx * (fx * (fx * 6 - 15) + 10)
        let v = fy * fy * fy * (fy * (fy * 6 - 15) + 10)
        let n00 = grad(x0, y0, seed, fx, fy)
        let n10 = grad(x0 + 1, y0, seed, fx - 1, fy)
        let n01 = grad(x0, y0 + 1, seed, fx, fy - 1)
        let n11 = grad(x0 + 1, y0 + 1, seed, fx - 1, fy - 1)
        let a = n00 + (n10 - n00) * u
        let b = n01 + (n11 - n01) * u
        return a + (b - a) * v
    }

    /// A grey image `w` × `h` px; (fx, fy) is the base frequency per px. `turbulence` sums |noise|
    /// (Skia's turbulence), otherwise it is fractal noise around mid grey.
    static func image(w: Int, h: Int, fx: Double, fy: Double, octaves: Int, seed: Int, turbulence: Bool) -> CGImage? {
        var px = [UInt8](repeating: 0, count: w * h)
        for y in 0..<h {
            for x in 0..<w {
                var sum = 0.0
                var amp = 1.0
                var f = 1.0
                for o in 0..<octaves {
                    let n = noise(Double(x) * fx * f, Double(y) * fy * f, seed + o * 31)
                    sum += (turbulence ? abs(n) : n) * amp
                    amp *= 0.5
                    f *= 2
                }
                let v = turbulence ? sum * 1.4 : 0.5 + sum * 0.7
                px[y * w + x] = UInt8(max(0, min(255, v * 255)))
            }
        }
        guard let provider = CGDataProvider(data: Data(px) as CFData) else { return nil }
        return CGImage(
            width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: w,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }
}
