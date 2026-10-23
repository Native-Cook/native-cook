import Foundation
import CoreGraphics

/// A permutation of original RGBA tuples on disjoint eight-connected digital scanlines.
/// This mutable scratch owner is used exclusively by the cache's one serial worker.
final class LumaRunStudioSorter: @unchecked Sendable {
    static let width = 288
    static let height = 330
    static let count = width * height
    private let source: LumaRunStudioSource
    private let luminance: [Double]
    private var mapping = [Int32](repeating: 0, count: count)
    private var run = [Int32](repeating: 0, count: 330)
    private var order = [Int32](repeating: 0, count: 330)
    private var rank = [Int32](repeating: 0, count: 330)
    // Double Y + one full Int32 permutation + three line buffers, plus <=330 shifts.
    static let scratchBytes = count * (8 + 4) + 330 * (3 * 4 + 8)

    init(source: LumaRunStudioSource) {
        precondition(source.canonical.width == Self.width && source.canonical.height == Self.height)
        precondition(source.rgba.count == Self.count * 4)
        self.source = source
        let decode = (0..<256).map { byte -> Double in
            let c = Double(byte) / 255
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        luminance = source.rgba.withUnsafeBytes { raw in
            let bytes = raw.bindMemory(to: UInt8.self)
            return (0..<Self.count).map { i in
                0.2126 * decode[Int(bytes[4 * i])] + 0.7152 * decode[Int(bytes[4 * i + 1])]
                    + 0.0722 * decode[Int(bytes[4 * i + 2])]
            }
        }
    }

    static func roundHalfUp(_ x: Double) -> Int { Int(floor(x + 0.5)) }

    /// Each source index occurs once. The slope branch/tie rules are also used by the proof harness.
    static func scanlines(angle: Double, visit: (Int32?) -> Void) {
        let theta = min(90, max(-90, angle))
        if abs(theta) <= 45 {
            let m = theta == 45 ? 1.0 : (theta == -45 ? -1.0 : tan(theta * .pi / 180))
            let shifts = (0..<width).map { roundHalfUp(m * Double($0)) }
            let last = shifts[width - 1]
            for line in (-max(0, last))...(height - 1 - min(0, last)) {
                for x in 0..<width {
                    let y = line + shifts[x]
                    if y >= 0 && y < height { visit(Int32(y * width + x)) }
                }
                visit(nil)
            }
        } else {
            let n = abs(theta) == 90 ? 0.0 : 1 / tan(theta * .pi / 180)
            let shifts = (0..<height).map { roundHalfUp(n * Double($0)) }
            let last = shifts[height - 1]
            for line in (-max(0, last))...(width - 1 - min(0, last)) {
                if theta > 0 {
                    for y in 0..<height {
                        let x = line + shifts[y]
                        if x >= 0 && x < width { visit(Int32(y * width + x)) }
                    }
                } else {
                    for y in stride(from: height - 1, through: 0, by: -1) {
                        let x = line + shifts[y]
                        if x >= 0 && x < width { visit(Int32(y * width + x)) }
                    }
                }
                visit(nil)
            }
        }
    }

    func isEligible(_ index: Int, params: LumaRunStudioParams) -> Bool {
        let x = 29 + (Double(index % Self.width) + 0.5) * 335 / Double(Self.width)
        let y = 136 + (Double(index / Self.width) + 0.5) * 384 / Double(Self.height)
        return x >= 59 + params.roiX && x <= 334 + params.roiX
            && y >= 178 + params.roiY && y <= 478 + params.roiY
            && luminance[index] >= params.low && luminance[index] <= params.high
    }

    func luma(_ index: Int) -> Double { luminance[index] }

    /// Returning the array shares its COW storage; normal rendering consumes it before the next job.
    func permutation(params: LumaRunStudioParams, cancelled: () -> Bool = { false }) -> [Int32]? {
        for i in 0..<Self.count { mapping[i] = Int32(i) }
        let q = min(1, max(0, params.q))
        if q == 0 { return mapping }
        var count = 0, previous = -1, stopped = false
        func flush() {
            defer { count = 0 }
            guard count >= 6 else { return }
            for ordinal in 0..<count { order[ordinal] = Int32(ordinal) }
            // Rank the complete original tuples by (Double linear Y, original global index).
            let original = run, y = luminance
            order.withUnsafeMutableBufferPointer { buffer in
                var active = UnsafeMutableBufferPointer(start: buffer.baseAddress!, count: count)
                active.sort { a, b in
                    let ia = Int(original[Int(a)]), ib = Int(original[Int(b)])
                    return y[ia] == y[ib] ? ia < ib : y[ia] < y[ib]
                }
            }
            if q != 1 {
                for r in 0..<count { rank[Int(order[r])] = Int32(r) }
                for ordinal in 0..<count { order[ordinal] = Int32(ordinal) }
                let ranks = rank
                order.withUnsafeMutableBufferPointer { buffer in
                    var active = UnsafeMutableBufferPointer(start: buffer.baseAddress!, count: count)
                    active.sort { a, b in
                        let aa = Int(a), bb = Int(b)
                        let sa = (1 - q) * Double(aa) + q * Double(ranks[aa])
                        let sb = (1 - q) * Double(bb) + q * Double(ranks[bb])
                        return sa == sb ? original[aa] < original[bb] : sa < sb
                    }
                }
            }
            for destination in 0..<count {
                mapping[Int(run[destination])] = run[Int(order[destination])]
            }
        }
        Self.scanlines(angle: params.angle) { sample in
            if stopped { return }
            guard let raw = sample else {
                flush(); previous = -1
                if cancelled() { stopped = true }
                return
            }
            let index = Int(raw)
            if previous >= 0 {
                let dx = abs(index % Self.width - previous % Self.width)
                let dy = abs(index / Self.width - previous / Self.width)
                if max(dx, dy) > 1 { flush() }
            }
            previous = index
            if isEligible(index, params: params) { run[count] = raw; count += 1 }
            else { flush() }
        }
        if cancelled() || stopped { return nil }
        return mapping
    }

    func render(params: LumaRunStudioParams, cancelled: () -> Bool = { false }) -> LumaRunStudioBitmap? {
        // Exact original identity, without a sorting or display-resolution substitution.
        if params.q == 0 { return cancelled() ? nil : .init(image: source.canonical, params: params) }
        guard let permutation = permutation(params: params, cancelled: cancelled) else { return nil }
        var pixels = Data(count: Self.count * 4)
        source.rgba.withUnsafeBytes { sourceBytes in
            pixels.withUnsafeMutableBytes { targetBytes in
                let src = sourceBytes.bindMemory(to: UInt32.self)
                let dst = targetBytes.bindMemory(to: UInt32.self)
                for destination in 0..<Self.count { dst[destination] = src[Int(permutation[destination])] }
            }
        }
        guard !cancelled(), let provider = CGDataProvider(data: pixels as CFData),
              let image = CGImage(width: Self.width, height: Self.height, bitsPerComponent: 8,
                bitsPerPixel: 32, bytesPerRow: Self.width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
        else { return nil }
        return .init(image: image, params: params)
    }
}
