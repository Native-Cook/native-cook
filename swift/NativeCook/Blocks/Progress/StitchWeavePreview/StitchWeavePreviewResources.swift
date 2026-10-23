import SwiftUI
import UIKit

/// One immutable data atlas: sixteen yarn poses and a 65×129 material arc-length lookup.
final class StitchWeavePreviewResources {
    static let shared = StitchWeavePreviewResources()
    let atlas: Image
    let grain: Image
    let shadow: Image
    private let arcs: [Double]
    private let completion: [Float]
    private typealias M = StitchWeavePreviewMath

    private init() {
        let width = 512, height = 256
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        for i in stride(from: 0, to: pixels.count, by: 4) { pixels[i + 1] = 128; pixels[i + 2] = 128; pixels[i + 3] = 255 }

        // These are actual centerline poses, not opacity frames of an existing fabric surface.
        for frame in 0..<16 {
            let f = Double(frame) / 15
            let front = Self.frontCurve(f), back = Self.backCurve(f)
            for y in 0..<40 { for x in 0..<32 {
                let p = CGPoint(x: (Double(x) + 0.5) / 3 - 2.2, y: (Double(y) + 0.5) / 3 - 2.4)
                let a = Self.nearest(p, front), b = Self.nearest(p, back)
                // Cotton is a soft bundle, rather than a thin wire with large transparent holes.
                let radius = 0.98
                let side = (p.x - a.point.x) * -a.tangent.y + (p.y - a.point.y) * a.tangent.x
                let fibers = 0.5 + 0.5 * cos(side * 14.3 + 0.25 * sin(a.along * 0.9))
                let fringe = 0.10 * M.clamp((radius + 0.38 - a.distance) / 0.38) * (0.45 + 0.55 * M.hash(x * 37 + y * 103))
                let core = M.clamp((radius + 0.17 - a.distance) / 0.34)
                let aa = core + fringe * (1 - core)
                let ba = M.clamp((radius + 0.19 - b.distance) / 0.38) * 0.96
                let coverage = (aa + ba * (1 - aa)) * M.smooth(f * 6)
                let nearest = aa > 0.02 ? a : b
                let nx = max(-1, min(1, (p.x - nearest.point.x) / radius))
                let ny = max(-1, min(1, (p.y - nearest.point.y) / radius))
                let index = (((frame / 8) * 40 + y) * width + (frame % 8) * 32 + x) * 4
                pixels[index] = Self.byte(coverage)
                pixels[index + 1] = Self.byte(nx * 0.5 + 0.5)
                pixels[index + 2] = Self.byte(ny * 0.5 + 0.5)
                let fiberIndex = index + 256 * 4
                pixels[fiberIndex] = Self.byte(fibers)
                pixels[fiberIndex + 1] = Self.byte(fringe)
                pixels[fiberIndex + 2] = Self.byte(0.5 + 0.5 * sin(side * 20 + a.along * 1.2))
                pixels[fiberIndex + 3] = 255
            } }
        }

        var table = [Double](repeating: 0, count: 65 * 129)
        for amplitude in 0..<65 {
            let a = Double(amplitude) / 64 * 17
            var samples = [Double](repeating: 0, count: 129)
            for column in 1..<129 {
                let l = Double(column - 1) / 128, r = Double(column) / 128, mid = (l + r) / 2
                func integrand(_ u: Double) -> Double {
                    let dz = 2 * Double.pi * a * cos(2 * .pi * u + 0.6)
                    return sqrt(322.4 * 322.4 - dz * dz)
                }
                samples[column] = samples[column - 1] + (r - l) / 6 * (integrand(l) + 4 * integrand(mid) + integrand(r))
            }
            let center = samples[128] / 2
            for column in 0..<129 {
                let x = samples[column] - center
                let baseline = (Double(column) / 128 - 0.5) * 322.4
                // Pack only the small correction, avoiding half-texture quantization of a 322pt span.
                let normalized = M.clamp((x - baseline) / 16 + 0.5)
                let packed = Int(round(normalized * 65535))
                let index = ((128 + amplitude) * width + column) * 4
                pixels[index] = UInt8(packed >> 8); pixels[index + 1] = UInt8(packed & 255)
                pixels[index + 2] = 0; pixels[index + 3] = 255
                // Use the same 16-bit quantized value as the shader for exact thread registration.
                table[amplitude * 129 + column] = baseline + (Double(packed) / 65535 - 0.5) * 16
            }
        }
        // Motif membership is fixed per completed material cell, not reevaluated per hero pixel.
        for row in 0..<48 { for column in 0..<52 {
            let point = CGPoint(x: (Double(column) + 0.5) * 6.2, y: (47.5 - Double(row)) * 6.8)
            func segment(_ u: Double, _ v: Double, _ x: Double, _ y: Double) -> Double {
                Self.nearest(point, [.init(x: u * 322.4, y: v * 326.4), .init(x: x * 322.4, y: y * 326.4)]).distance
            }
            var sprig = segment(0.5, 0.28, 0.5, 0.73), ripple = Double.greatestFiniteMagnitude
            for i in 0..<3 {
                var v = 0.40 + Double(i) * 0.12
                sprig = min(sprig, segment(0.5, v, 0.33, v - 0.085), segment(0.5, v, 0.67, v - 0.085))
                v = 0.30 + Double(i) * 0.16
                ripple = min(ripple, segment(0.24, v, 0.50, v + 0.1), segment(0.50, v + 0.1, 0.76, v))
            }
            let index = ((208 + row) * width + column) * 4
            pixels[index] = Self.byte(1 - M.smooth((sprig - 6.8) / 0.9))
            pixels[index + 1] = Self.byte(1 - M.smooth((ripple - 6.8) / 0.9))
            pixels[index + 2] = 0; pixels[index + 3] = 255
        } }
        arcs = table
        completion = (0..<48).map { Float(M.canonicalTime(M.clamp((Double($0 + 1) - 3) / 45))) }
        atlas = Self.image(pixels, width: width, height: height, linear: true)

        var noise = [UInt8](repeating: 255, count: 128 * 128 * 4)
        for i in 0..<(128 * 128) {
            let n = UInt8(round(M.hash(i + 321) * 255))
            noise[i * 4] = n; noise[i * 4 + 1] = n; noise[i * 4 + 2] = n
        }
        grain = Self.image(noise, width: 128, height: 128, linear: false)
        var shade = [UInt8](repeating: 0, count: 256 * 96 * 4)
        for y in 0..<96 { for x in 0..<256 {
            let dx = (Double(x) - 127.5) / 73, dy = (Double(y) - 47.5) / 18
            shade[(y * 256 + x) * 4 + 3] = Self.byte(exp(-0.5 * (dx * dx + dy * dy)))
        } }
        shadow = Self.image(shade, width: 256, height: 96, linear: false)
    }

    func arc(u: Double, amplitude: Double) -> Double {
        let x = M.clamp(u) * 128, a = M.clamp(amplitude / 17) * 64
        let xi = min(127, Int(x)), ai = min(63, Int(a)), xf = x - Double(xi), af = a - Double(ai)
        let first = arcs[ai * 129 + xi] * (1 - xf) + arcs[ai * 129 + xi + 1] * xf
        let next = arcs[(ai + 1) * 129 + xi] * (1 - xf) + arcs[(ai + 1) * 129 + xi + 1] * xf
        return first * (1 - af) + next * af
    }

    func uniforms(pose: StitchWeavePreviewPose) -> [Float] {
        let a = M.rotationMatrix(pose.rotation), b = M.rotationMatrix(pose.lagRotation)
        return a.map(Float.init) + zip(a, b).map { Float($0.1 - $0.0) } + completion + (0..<52).map { Float(M.delay($0)) }
    }

    func project(u: Double, v: Double, pose: StitchWeavePreviewPose) -> CGPoint {
        let amplitude = (8 + 9 * pose.p) * sin(.pi * v)
        let x = arc(u: u, amplitude: max(0, amplitude))
        let y = 245.6 + 326.4 * v - 420 + pose.hem
        let lead = 1 - (3 + 45 * pose.p) / 48
        let weight = M.smooth(1 - max(0, (v - lead) * 326.4) / 256)
        let z = amplitude * sin(2 * .pi * u + 0.6) + pose.lift * weight
        let a = M.rotationMatrix(pose.rotation), b = M.rotationMatrix(pose.lagRotation)
        let lag = M.clamp(1 - v)
        func row(_ index: Int) -> Double {
            let n = index * 3
            return (a[n] + (b[n] - a[n]) * lag) * x + (a[n + 1] + (b[n + 1] - a[n + 1]) * lag) * y + (a[n + 2] + (b[n + 2] - a[n + 2]) * lag) * z
        }
        let perspective = 950 / (950 - row(2))
        return CGPoint(x: 196.5 + row(0) * perspective, y: 420 + row(1) * perspective)
    }

    private static func byte(_ value: Double) -> UInt8 { UInt8(min(255, max(0, Int(round(value * 255))))) }
    private static func image(_ bytes: [UInt8], width: Int, height: Int, linear: Bool) -> Image {
        let data = Data(bytes)
        let provider = CGDataProvider(data: data as CFData)!
        let space = linear ? CGColorSpace(name: CGColorSpace.linearSRGB)! : CGColorSpaceCreateDeviceRGB()
        let cg = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
                         space: space, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                         provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
        return Image(decorative: cg, scale: 1)
    }
    private static func cubic(_ a: CGPoint, _ b: CGPoint, _ c: CGPoint, _ d: CGPoint, _ t: Double) -> CGPoint {
        let s = 1 - t
        return CGPoint(x: s * s * s * a.x + 3 * s * s * t * b.x + 3 * s * t * t * c.x + t * t * t * d.x,
                       y: s * s * s * a.y + 3 * s * s * t * b.y + 3 * s * t * t * c.y + t * t * t * d.y)
    }
    private static func transformed(_ p: CGPoint, _ f: Double) -> CGPoint {
        let spread = 0.08 + 0.92 * M.smooth(f)
        let top = M.clamp((6.3 - p.y) / 5.8)
        return CGPoint(x: 3.1 + (p.x - 3.1) * spread, y: p.y - 1.8 * (1 - f) * top)
    }
    private static func frontCurve(_ f: Double) -> [CGPoint] {
        let segments: [(CGPoint, CGPoint, CGPoint, CGPoint)] = [
            (.init(x: 0.8, y: 6.3), .init(x: 1.8, y: 5.7), .init(x: 1.2, y: 3.8), .init(x: 1.2, y: 2)),
            (.init(x: 1.2, y: 2), .init(x: 1.65, y: 0.2), .init(x: 4.35, y: 0.2), .init(x: 4.8, y: 2)),
            (.init(x: 4.8, y: 2), .init(x: 4.8, y: 3.8), .init(x: 4.4, y: 5.7), .init(x: 5.4, y: 6.3)),
            (.init(x: 5.4, y: 6.3), .init(x: 6.1, y: 7.1), .init(x: 6.9, y: 7.0), .init(x: 6.9, y: 6.0))]
        return segments.flatMap { a, b, c, d in (0...12).map { transformed(cubic(a, b, c, d, Double($0) / 12), f) } }
    }
    private static func backCurve(_ f: Double) -> [CGPoint] {
        let a = CGPoint(x: -0.7, y: 3.7), b = CGPoint(x: 3.1, y: 8.0), c = CGPoint(x: 6.9, y: 3.7)
        return (0...32).map { i in
            let t = Double(i) / 32, s = 1 - t
            return transformed(.init(x: s * s * a.x + 2 * s * t * b.x + t * t * c.x,
                                     y: s * s * a.y + 2 * s * t * b.y + t * t * c.y), f)
        }
    }
    private static func nearest(_ point: CGPoint, _ path: [CGPoint]) -> (distance: Double, point: CGPoint, tangent: CGPoint, along: Double) {
        var best = Double.greatestFiniteMagnitude, result = CGPoint.zero, tangent = CGPoint(x: 0, y: 1), along = 0.0
        for index in 1..<path.count {
            let a = path[index - 1], b = path[index]
            let dx = b.x - a.x, dy = b.y - a.y
            let t = M.clamp(((point.x - a.x) * dx + (point.y - a.y) * dy) / max(0.000001, dx * dx + dy * dy))
            let q = CGPoint(x: a.x + dx * t, y: a.y + dy * t)
            let distance = hypot(point.x - q.x, point.y - q.y)
            if distance < best {
                best = distance; result = q
                let length = max(0.000001, hypot(dx, dy))
                tangent = CGPoint(x: dx / length, y: dy / length)
                along = Double(index - 1) + t
            }
        }
        return (best, result, tangent, along)
    }
}
