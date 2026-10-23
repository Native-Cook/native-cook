import Accelerate
import simd
import CoreGraphics
import CoreText
import SwiftUI
import UIKit

// Everything the Cosmic Drift splash draws that is not tied to a frame: the glossy sphere
// sprites (shaded pixel by pixel once, then blurred into depth-of-field levels), the seeded
// scene (spheres, dust streaks, stars) and the Orrery mark's geometry.

enum CosmicDriftSplashArt {
    // MARK: Sprites

    /// Sphere diameter inside a sprite, in pixels, and the sprite's side: 2.1 diameters,
    /// so the blurriest level still fades out inside the image.
    static let diameter = 168
    static let side = 352
    static let spriteSpan = Double(side) / Double(diameter)
    /// Blur of each level, as a fraction of the sphere's diameter (Gaussian sigma).
    static let blurLevels: [Double] = [0, 0.02, 0.04, 0.07, 0.11, 0.17]

    struct Palette {
        let base: SIMD3<Double>, deep: SIMD3<Double>, band: SIMD3<Double>, rim: SIMD3<Double>
        let bandFrom: Double, bandStrength: Double, rimStrength: Double
        /// What a trail of this sphere is drawn with while the field recedes.
        var trail: Color { Color(red: base.x, green: base.y, blue: base.z) }
    }

    static func rgb(_ hex: UInt32) -> SIMD3<Double> {
        SIMD3(Double((hex >> 16) & 255) / 255, Double((hex >> 8) & 255) / 255, Double(hex & 255) / 255)
    }

    /// Ember with a teal cap, cream, teal with a cream band, dusty blue, ochre with a teal cap, charcoal.
    static let palettes: [Palette] = [
        Palette(base: rgb(0xD5532F), deep: rgb(0x2A0A07), band: rgb(0x4DBDB5), rim: rgb(0x6FD8CE), bandFrom: 0.64, bandStrength: 1, rimStrength: 0.55),
        Palette(base: rgb(0xE8DEC6), deep: rgb(0x2B2720), band: rgb(0xE8DEC6), rim: rgb(0xF3C48E), bandFrom: 2, bandStrength: 0, rimStrength: 0.35),
        Palette(base: rgb(0x2F8E89), deep: rgb(0x051817), band: rgb(0xE6D9BC), rim: rgb(0xA6E8DA), bandFrom: 0.72, bandStrength: 0.95, rimStrength: 0.4),
        Palette(base: rgb(0x5D7894), deep: rgb(0x0C131C), band: rgb(0x5D7894), rim: rgb(0xF2A46C), bandFrom: 2, bandStrength: 0, rimStrength: 0.38),
        Palette(base: rgb(0xCB972F), deep: rgb(0x281805), band: rgb(0x3FA19C), rim: rgb(0x7FD9D1), bandFrom: 0.68, bandStrength: 1, rimStrength: 0.45),
        Palette(base: rgb(0x5E6364), deep: rgb(0x0A0B0C), band: rgb(0x5E6364), rim: rgb(0xAACCCE), bandFrom: 2, bandStrength: 0, rimStrength: 0.42),
    ]

    static func fract(_ v: Double) -> Double { v - floor(v) }
    static func hash(_ x: Double, _ y: Double) -> Double { fract(sin(x * 127.1 + y * 311.7) * 43758.5453) }
    static func hash(_ i: Int, _ k: Int) -> Double { hash(Double(i) * 1.618 + 0.37, Double(k) * 2.71 + 0.11) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = min(1, max(0, (x - a) / (b - a))); return u * u * (3 - 2 * u)
    }
    static func noise(_ x: Double, _ y: Double) -> Double {
        let ix = floor(x), iy = floor(y), fx = x - ix, fy = y - iy
        let ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy)
        let a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1)
        return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy
    }

    /// One sphere shaded per pixel: soft key light from the upper left, a tight specular spot and a
    /// broad sheen, a coloured rim light on the upper right, a tilted colour cap, grain and glints.
    /// Premultiplied RGBA, `side` × `side`.
    static func shade(_ p: Palette, index: Int) -> [UInt8] {
        var out = [UInt8](repeating: 0, count: side * side * 4)
        let r = Double(diameter) / 2, c = Double(side) / 2
        let light = simd_normalize(SIMD3<Double>(-0.55, -0.68, 0.48))
        let half = simd_normalize(light + SIMD3(0, 0, 1))
        let axis = simd_normalize(SIMD3<Double>(0.45, -0.75, 0.48))
        let rimDir = SIMD2<Double>(0.95, -0.31)
        let lo = Int(c - r - 2), hi = Int(c + r + 2)
        let seed = Double(index)
        out.withUnsafeMutableBufferPointer { buf in
            for py in lo..<hi {
                for px in lo..<hi {
                    let x = (Double(px) + 0.5 - c) / r, y = (Double(py) + 0.5 - c) / r
                    let d2 = x * x + y * y
                    let alpha = min(1, max(0, r * (1 - d2.squareRoot()) + 0.5))
                    if alpha <= 0 { continue }
                    let z = max(0, 1 - d2).squareRoot()
                    let n = SIMD3(x, y, z) / max(1, d2.squareRoot())
                    let diff = smoothstep(-0.28, 1.0, simd_dot(n, light))
                    let cap = smoothstep(p.bandFrom - 0.12, p.bandFrom + 0.12, simd_dot(n, axis)) * p.bandStrength
                    let grain = 0.6 * noise(x * 5 + seed * 7, y * 5) + 0.4 * noise(x * 11, y * 11 + seed * 3)
                    let albedo = (p.base + (p.band - p.base) * cap) * (0.9 + 0.12 * grain)
                    var col = p.deep + (albedo - p.deep) * pow(diff, 0.9)
                    col *= 0.82 + 0.36 * diff
                    let nh = max(0, simd_dot(n, half))
                    col += SIMD3(1.0, 0.97, 0.92) * (pow(nh, 46) * 0.65 + pow(nh, 9) * 0.11)
                    let edge = 1 - z
                    let len = max(1e-4, (x * x + y * y).squareRoot())
                    let toward = (x * rimDir.x + y * rimDir.y) / len
                    col += p.rim * (pow(edge, 2.6) * smoothstep(-0.15, 0.9, toward) * p.rimStrength)
                    col += albedo * (pow(edge, 2) * smoothstep(0.2, 1, y) * 0.12)
                    col *= (1 - 0.28 * pow(edge, 4) * (1 - diff)) * 0.86
                    if hash(Double(px) * 1.37 + seed * 19, Double(py) * 0.91) > 0.9972 {
                        col += SIMD3(repeating: 0.55 * (0.3 + 0.7 * diff))
                    }
                    let i = (py * side + px) * 4
                    buf[i] = UInt8(min(1, max(0, col.x)) * alpha * 255)
                    buf[i + 1] = UInt8(min(1, max(0, col.y)) * alpha * 255)
                    buf[i + 2] = UInt8(min(1, max(0, col.z)) * alpha * 255)
                    buf[i + 3] = UInt8(alpha * 255)
                }
            }
        }
        return out
    }

    /// Three box blurs approximate a Gaussian of the given sigma (in pixels).
    static func blur(_ pixels: [UInt8], sigma: Double) -> [UInt8] {
        if sigma < 0.5 { return pixels }
        let kernel = UInt32(2 * Int(sigma.rounded()) + 1)
        var a = pixels, b = [UInt8](repeating: 0, count: pixels.count)
        a.withUnsafeMutableBytes { pa in
            b.withUnsafeMutableBytes { pb in
                var src = vImage_Buffer(data: pa.baseAddress, height: vImagePixelCount(side), width: vImagePixelCount(side), rowBytes: side * 4)
                var dst = vImage_Buffer(data: pb.baseAddress, height: vImagePixelCount(side), width: vImagePixelCount(side), rowBytes: side * 4)
                for pass in 0..<3 {
                    if pass % 2 == 0 {
                        vImageBoxConvolve_ARGB8888(&src, &dst, nil, 0, 0, kernel, kernel, nil, vImage_Flags(kvImageEdgeExtend))
                    } else {
                        vImageBoxConvolve_ARGB8888(&dst, &src, nil, 0, 0, kernel, kernel, nil, vImage_Flags(kvImageEdgeExtend))
                    }
                }
            }
        }
        return b // three passes end in the second buffer
    }

    /// The pixels as an image in the layout the renderer uses as is (BGRA, premultiplied, 8-bit sRGB), so its
    /// first draw is a plain upload rather than a conversion.
    static func image(_ pixels: [UInt8]) -> UIImage? {
        let data = Data(pixels) as CFData
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        guard let provider = CGDataProvider(data: data),
              let rgba = CGImage(width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: side * 4,
                                 space: space,
                                 bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                                 provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent),
              let context = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4, space: space,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
        else { return nil }
        context.setBlendMode(.copy)
        context.draw(rgba, in: CGRect(x: 0, y: 0, width: side, height: side))
        return context.makeImage().map { UIImage(cgImage: $0) }
    }

    // MARK: Scene

    struct Sphere { let z, angle, distance, radius: Double; let palette: Int }
    struct Speck { let x, y, z: Double; let size: Double; let rate: Double; let phase: Double }
    struct Star { let u, v, size, brightness: Double; let blue: Bool; let rate: Double; let phase: Double; let twinkles: Bool }

    static let spheres: [Sphere] = (0..<56).map { i in
        let weights = [0, 0, 1, 2, 2, 3, 4, 5, 0, 1] // ember and teal a little more often
        return Sphere(z: Double(i) * 0.56 + hash(i, 1) * 0.4,
                      angle: Double(i) * 2.39996 + hash(i, 2) * 0.6,
                      distance: 0.75 + 1.5 * hash(i, 3),
                      radius: 0.45 + 0.35 * hash(i, 4),
                      palette: weights[Int(hash(i, 5) * 10) % 10])
    }

    /// Fourteen streaks of dust, 44 glints each, drifting with the spheres.
    static let specks: [Speck] = (0..<14).flatMap { j -> [Speck] in
        let zc = Double(j) * 2.2 + hash(j, 21) * 1.5
        let ac = hash(j, 22) * 6.283, dc = 0.7 + 1.2 * hash(j, 23)
        let cx = dc * cos(ac), cy = dc * sin(ac), tx = -sin(ac), ty = cos(ac)
        return (0..<44).map { k in
            let s = j * 44 + k
            let u = hash(s, 24) * 2 - 1
            let along = u * 1.2, across = (hash(s, 25) - 0.5) * 0.16 * (1 - abs(u) * 0.5)
            return Speck(x: cx + tx * along + cos(ac) * across, y: cy + ty * along + sin(ac) * across, z: zc + u * 0.6,
                         size: 0.5 + hash(s, 26), rate: Double(14 + Int(hash(s, 27) * 20)), phase: hash(s, 28) * 6.283)
        }
    }

    static let stars: [Star] = (0..<260).map { i in
        Star(u: hash(i, 31), v: hash(i, 32), size: 0.35 + 0.75 * pow(hash(i, 33), 3),
             brightness: 0.22 + 0.7 * pow(hash(i, 34), 2), blue: hash(i, 35) > 0.82,
             rate: Double(4 + Int(hash(i, 36) * 9)), phase: hash(i, 37) * 6.283, twinkles: hash(i, 38) > 0.62)
    }

    // MARK: Mark (in a 100-unit box; the planet sits at its centre)

    static let planetRadius = 17.0
    static let stroke = 7.5
    /// Inner and outer orbit: radius, start and end angle in degrees (y down, clockwise).
    static let orbits: [(radius: Double, from: Double, to: Double)] = [(30, 120, 330), (43.5, 160, 345)]
    static let moonAngle = 8.0, moonRadius = 5.5
    /// The planet's offset from the mark's visual centre, in mark units.
    static let visualOffset = CGPoint(x: -1, y: 9)

    /// An orbit drawn from its middle outwards: `progress` 0 is nothing, 1 the whole arc.
    static func orbitPath(_ orbit: (radius: Double, from: Double, to: Double), progress: Double) -> Path {
        var path = Path()
        guard progress > 0.001 else { return path }
        let mid = (orbit.from + orbit.to) / 2, half = (orbit.to - orbit.from) / 2 * progress
        let a0 = (mid - half) * .pi / 180, a1 = (mid + half) * .pi / 180
        let steps = max(2, Int(48 * progress))
        for s in 0...steps {
            let a = a0 + (a1 - a0) * Double(s) / Double(steps)
            let p = CGPoint(x: orbit.radius * cos(a), y: orbit.radius * sin(a))
            if s == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        return path
    }

    // MARK: Font

    static let fontName: String = {
        if let url = Bundle.main.url(forResource: "CosmicDriftSplash-Questrial-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        return "Questrial-Regular"
    }()
}

/// Builds the sprites once per app run, off the main thread; the splash holds its resting frame until they exist.
final class CosmicDriftSplashSprites: @unchecked Sendable {
    static let shared = CosmicDriftSplashSprites()
    private let lock = NSLock()
    private var built: [[Image]]?
    private lazy var task = Task.detached(priority: .userInitiated) { () -> [[Image]] in
        let art = CosmicDriftSplashArt.self
        let result: [[Image]] = art.palettes.enumerated().map { index, palette in
            let sharp = art.shade(palette, index: index)
            return art.blurLevels.map { level in
                let pixels = art.blur(sharp, sigma: level * Double(art.diameter))
                return art.image(pixels).map { Image(uiImage: $0) } ?? Image(systemName: "circle.fill")
            }
        }
        self.lock.withLock { self.built = result }
        return result
    }

    var images: [[Image]]? { lock.withLock { built } }

    /// Starts the build on the first call and waits for it.
    func ready() async -> [[Image]] {
        let task = lock.withLock { self.task }
        return await task.value
    }
}
