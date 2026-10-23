import SwiftUI

let ParticleCardDetailsLOOP = 7.8

private enum ParticleCardDetailsCardSpec {
    static let cardIn: Float = 0.09, flashFor: Float = 0.3
    static let lineAt: Float = 0.08, lineFor: Float = 0.62, trail: Float = 0.5
    static let linePad: Float = 14, rise: Float = 34
    static let stillHold: Float = 0.35, stillOut: Float = 0.35
    static let side: Float = 36, top: Float = 52, bottom: Float = 36, radius: Float = 13
    static let markRadii: [Float] = [4, 8.5, 13, 17.5]
    static let markSweep: Float = 76, markRight: Float = 40, markBottom: Float = 38, markStroke: Float = 2
    static let markInk = Color(hex: 0xDCDCE1)
    static let tau: Float = 6.2831853
    static let cloudCells: [Float] = [4.2, 6.6], cloudFills: [Float] = [0.62, 0.3]
    static let cloudSeeds: [Float] = [0, 91], cloudOffsets: [Float] = [0, 2.6]
    static let grainCells: [Float] = [1.7, 2.3, 3.1], grainFills: [Float] = [0.92, 0.4, 0.12]
    static let grainSeeds: [Float] = [7, 19, 43], grainDecay: [Float] = [6, 18, 80], grainRise: [Float] = [0, 1, 2.2]
    static let bodyCell: Float = 1.4
    // Coverage rings and colour buckets replace per-pixel shader work with batched paths.
    static let coverageLayers = 4, cloudLevels = 64, dustLevels = 16, lightLevels = 32
    static let cloudColors = [Color(red: 0.74, green: 0.64, blue: 1), Color(red: 0.6, green: 0.96, blue: 0.76), Color.white]
}
private typealias ParticleCardDetailsCS = ParticleCardDetailsCardSpec

private func particleCardDetailsClamp(_ value: Float) -> Float { min(1, max(0, value)) }
private func particleCardDetailsSmooth(_ value: Float, _ low: Float, _ high: Float) -> Float {
    let t = particleCardDetailsClamp((value - low) / (high - low))
    return t * t * (3 - 2 * t)
}
private func particleCardDetailsFract(_ value: Float) -> Float { value - floor(value) }
private func particleCardDetailsHash(_ x: Float, _ y: Float) -> Float {
    var a = particleCardDetailsFract(x * 0.1031)
    var b = particleCardDetailsFract(y * 0.1031)
    var c = a
    let d = a * (b + 33.33) + b * (c + 33.33) + c * (a + 33.33)
    a += d; b += d; c += d
    return particleCardDetailsFract((a + b) * c)
}
private func particleCardDetailsNoise(_ x: Float) -> Float {
    let i = floor(x), f = x - i, u = f * f * (3 - 2 * f)
    let a = particleCardDetailsHash(i, 3.7), b = particleCardDetailsHash(i + 1, 3.7)
    return a + (b - a) * u
}
private func particleCardDetailsRagged(_ x: Float) -> Float {
    (particleCardDetailsNoise(x * 0.07) - 0.5) * 12 + (particleCardDetailsNoise(x * 0.31 + 40) - 0.5) * 6
}
private func particleCardDetailsInverseSmooth(_ value: Float) -> Float {
    0.5 - sin(asin(1 - 2 * particleCardDetailsClamp(value)) / 3)
}
private func particleCardDetailsBox(_ x: Float, _ y: Float, _ width: Float, _ height: Float) -> Float {
    let qx = abs(x) - width / 2 + ParticleCardDetailsCS.radius
    let qy = abs(y) - height / 2 + ParticleCardDetailsCS.radius
    return hypot(max(0, qx), max(0, qy)) + min(max(qx, qy), 0) - ParticleCardDetailsCS.radius
}
private struct ParticleCardDetailsDot {
    let x: Float, y: Float, radius: Float, beats: Float, phase: Float, bright: Float
    let tint: Int
}
private struct ParticleCardDetailsGrain {
    let x: Float, y: Float, radius: Float, reach: Float, ragged: Float
}
private struct ParticleCardDetailsGrainLayer {
    let rows: [[ParticleCardDetailsGrain]]
    let cell: Float, rise: Float
}
private struct ParticleCardDetailsColumn {
    let x: Float, ragged: Float
    let thresholds: [Float]
}

private final class ParticleCardDetailsCardCache: ObservableObject {
    private(set) var width: Float = -1, height: Float = -1
    private(set) var dots: [ParticleCardDetailsDot] = []
    private(set) var grains: [ParticleCardDetailsGrainLayer] = []
    private(set) var columns: [ParticleCardDetailsColumn] = []
    private(set) var rounded = Path(), mark = Path()
    let coverage = (0..<ParticleCardDetailsCS.coverageLayers).map {
        particleCardDetailsInverseSmooth(1 - (Float($0) + 0.5) / Float(ParticleCardDetailsCS.coverageLayers))
    }

    func prepare(_ width: Float, _ height: Float) {
        guard self.width != width || self.height != height else { return }
        self.width = width; self.height = height
        dots.removeAll(keepingCapacity: true); grains.removeAll(keepingCapacity: true); columns.removeAll(keepingCapacity: true)
        let s = ParticleCardDetailsCS.self
        let canvasWidth = width + s.side * 2, canvasHeight = height + s.top + s.bottom
        rounded = Path(roundedRect: CGRect(x: CGFloat(s.side), y: CGFloat(s.top), width: CGFloat(width), height: CGFloat(height)), cornerRadius: CGFloat(s.radius))
        mark = Path()
        let markX = s.side + width - s.markRight - s.markRadii[s.markRadii.count - 1]
        let markY = s.top + height - s.markBottom
        for radius in s.markRadii {
            var arc = Path()
            arc.addArc(center: CGPoint(x: CGFloat(markX), y: CGFloat(markY)), radius: CGFloat(radius), startAngle: .degrees(Double(-s.markSweep / 2)), endAngle: .degrees(Double(s.markSweep / 2)), clockwise: false)
            mark.addPath(arc)
        }
        for layer in s.cloudCells.indices {
            let cell = s.cloudCells[layer], seed = s.cloudSeeds[layer], offset = s.cloudOffsets[layer]
            for gy in 0...Int(ceil((canvasHeight + offset) / cell)) {
                for gx in 0...Int(ceil((canvasWidth + offset) / cell)) {
                    let sx = Float(gx) + seed, sy = Float(gy) + seed
                    let x = (Float(gx) + 0.5 + (particleCardDetailsHash(sx, sy) - 0.5) * 0.7) * cell
                    let y = (Float(gy) + 0.5 + (particleCardDetailsHash(sx + 11.3, sy + 11.3) - 0.5) * 0.7) * cell
                    let edge = particleCardDetailsBox(x - s.side - width / 2, y - s.top - height / 2, width, height)
                    guard particleCardDetailsHash(sx + 23.1, sy + 23.1) <= s.cloudFills[layer] * (1 - particleCardDetailsSmooth(edge, -10, 14)) else { continue }
                    let glint = particleCardDetailsHash(sx + 67.1, sy + 67.1), tint = particleCardDetailsHash(sx + 79.9, sy + 79.9)
                    dots.append(ParticleCardDetailsDot(x: x - offset, y: y - offset, radius: 0.4 + 0.5 * particleCardDetailsHash(sx + 37.9, sy + 37.9), beats: floor(5 + 11 * particleCardDetailsHash(sx + 41.7, sy + 41.7)), phase: particleCardDetailsHash(sx + 53.3, sy + 53.3), bright: 0.22 + 0.78 * glint * glint, tint: tint < 0.1 ? 0 : tint < 0.2 ? 1 : 2))
                }
            }
        }
        for layer in s.grainCells.indices {
            let cell = s.grainCells[layer], seed = s.grainSeeds[layer], drift = s.grainRise[layer]
            let maxRise = s.rise * (s.lineFor + s.trail) * drift
            var rows: [[ParticleCardDetailsGrain]] = []
            for gy in 0...Int(ceil((canvasHeight + maxRise) / cell)) {
                var row: [ParticleCardDetailsGrain] = []
                for gx in Int(floor(-24 / cell))...Int(ceil((canvasWidth + 24) / cell)) {
                    let sx = Float(gx) + seed, sy = Float(gy) + seed
                    let keep = particleCardDetailsHash(sx + 23.1, sy + 23.1)
                    guard keep <= s.grainFills[layer] else { continue }
                    let x = (Float(gx) + 0.5 + (particleCardDetailsHash(sx, sy) - 0.5) * 0.6) * cell
                    let y = (Float(gy) + 0.5 + (particleCardDetailsHash(sx + 11.3, sy + 11.3) - 0.5) * 0.6) * cell
                    row.append(ParticleCardDetailsGrain(x: x, y: y, radius: cell * (0.28 + 0.22 * particleCardDetailsHash(sx + 37.9, sy + 37.9)), reach: keep == 0 ? .infinity : -s.grainDecay[layer] * log(keep / s.grainFills[layer]), ragged: particleCardDetailsRagged(x - s.side)))
                }
                rows.append(row)
            }
            grains.append(ParticleCardDetailsGrainLayer(rows: rows, cell: cell, rise: drift))
        }
        let cell = s.bodyCell
        for gx in Int(floor(s.side / cell))...Int(ceil((s.side + width) / cell)) {
            let x = Float(gx) * cell
            let thresholds = (0...Int(ceil(canvasHeight / cell))).map { gy in
                -1.5 + 8.5 * particleCardDetailsInverseSmooth(particleCardDetailsHash(Float(gx) + 5, Float(gy) + 5))
            }
            columns.append(ParticleCardDetailsColumn(x: x, ragged: particleCardDetailsRagged(x + cell / 2 - s.side), thresholds: thresholds))
        }
    }
}

/// Cached hashed points keep the shader's grids and clock without per-pixel CPU shading.
struct ParticleCardDetailsParticleCard: View {
    let width: Double, height: Double, time: Double, period: Double, elapsed: Double
    let still: Bool
    @StateObject private var cache = ParticleCardDetailsCardCache()

    init(width: Double, height: Double, time: Double, period: Double, elapsed: Double, still: Bool) {
        self.width = width; self.height = height; self.time = time; self.period = period; self.elapsed = elapsed; self.still = still
    }

    var body: some View {
        Canvas { input, _ in
            var context = input
            guard width > 0, height > 0, width.isFinite, height.isFinite else { return }
            cache.prepare(Float(width), Float(height))
            let s = ParticleCardDetailsCS.self, e = Float(elapsed)
            let line = still ? -1000 : -s.linePad + ((Float(height) + s.linePad * 2) * max(0, e - s.lineAt)) / s.lineFor
            let solid: Float = e < 0 ? 0 : still
                ? particleCardDetailsSmooth(e, 0, s.cardIn * 2) * (1 - particleCardDetailsSmooth(e, s.cardIn * 2 + s.stillHold, s.cardIn * 2 + s.stillHold + s.stillOut))
                : e < s.lineAt + s.lineFor + s.trail ? particleCardDetailsSmooth(e, 0, s.cardIn) : 0
            drawCloud(&context)
            if solid > 0 {
                let flash = still ? 0 : 0.45 * (1 - particleCardDetailsSmooth(e, s.cardIn, s.flashFor))
                drawBody(&context, line: line, solid: solid, flash: flash)
                if !still { drawDust(&context, line: line, solid: solid, rise: s.rise * max(0, e - s.lineAt)) }
                var mark = context
                let top = s.top + max(0, line)
                mark.clip(to: Path(CGRect(x: 0, y: CGFloat(top), width: width + Double(s.side * 2), height: Double(max(0, s.top + Float(height) - top)))))
                mark.opacity = Double(solid)
                mark.stroke(cache.mark, with: .color(s.markInk), style: StrokeStyle(lineWidth: CGFloat(s.markStroke), lineCap: .round))
            }
        }
        .frame(width: max(0, width + 72), height: max(0, height + 88))
        .offset(x: -36, y: -52)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func drawCloud(_ context: inout GraphicsContext) {
        let s = ParticleCardDetailsCS.self
        var paths = Array(repeating: Path(), count: s.cloudLevels * 3 * s.coverageLayers)
        let phaseTime = period.isFinite && period > 0 ? Float(time / period) : 0
        for dot in cache.dots {
            let twinkle = 0.5 + 0.5 * sin(s.tau * (dot.beats * phaseTime + dot.phase))
            let bright = dot.bright * (0.12 + 0.88 * twinkle * twinkle)
            let level = min(s.cloudLevels - 1, max(0, Int((bright * Float(s.cloudLevels - 1)).rounded())))
            guard level > 0 else { continue }
            for (ring, coverage) in cache.coverage.enumerated() {
                let index = (dot.tint * s.cloudLevels + level) * s.coverageLayers + ring
                let r = max(0, dot.radius - 0.45 + 0.75 * coverage)
                paths[index].addEllipse(in: CGRect(x: CGFloat(dot.x - r), y: CGFloat(dot.y - r), width: CGFloat(r * 2), height: CGFloat(r * 2)))
            }
        }
        var cloud = context
        cloud.blendMode = .plusLighter
        for index in paths.indices where !paths[index].isEmpty {
            let bucket = index / s.coverageLayers
            let alpha = Double(bucket % s.cloudLevels) / Double(s.cloudLevels - 1) / Double(s.coverageLayers)
            cloud.fill(paths[index], with: .color(s.cloudColors[bucket / s.cloudLevels].opacity(alpha)))
        }
    }

    private func drawBody(_ context: inout GraphicsContext, line: Float, solid: Float, flash: Float) {
        let s = ParticleCardDetailsCS.self, cell = s.bodyCell
        var mask = Path(), highlights = Array(repeating: Path(), count: s.lightLevels)
        let bottom = s.top + Float(height)
        for column in cache.columns {
            let edge = s.top + line + column.ragged
            let fullTop = max(s.top, edge + 7)
            if fullTop < bottom { mask.addRect(CGRect(x: CGFloat(column.x), y: CGFloat(fullTop), width: CGFloat(cell), height: CGFloat(bottom - fullTop))) }
            let first = max(0, Int(floor((edge - 1.5) / cell))), last = min(column.thresholds.count - 1, Int(floor((edge + 7) / cell)))
            if first <= last {
                for row in first...last {
                    let top = max(Float(row) * cell, edge + column.thresholds[row]), end = Float(row + 1) * cell
                    if top < end { mask.addRect(CGRect(x: CGFloat(column.x), y: CGFloat(top), width: CGFloat(cell), height: CGFloat(end - top))) }
                }
            }
            for strip in 0..<Int(ceil(11.5 / cell)) {
                let y = edge - 1.5 + Float(strip) * cell
                let alpha = 1 - particleCardDetailsSmooth(y + cell / 2 - edge, 0, 10)
                let level = min(s.lightLevels - 1, max(0, Int((alpha * Float(s.lightLevels - 1)).rounded())))
                if level > 0 { highlights[level].addRect(CGRect(x: CGFloat(column.x), y: CGFloat(y), width: CGFloat(cell), height: CGFloat(cell))) }
            }
        }
        var body = context
        body.opacity = Double(solid)
        body.drawLayer { layer in
            layer.clip(to: cache.rounded)
            layer.clip(to: mask)
            let factor = Double(1 + flash)
            let topColor = Color(red: 0.2 * factor, green: 0.2 * factor, blue: 0.215 * factor)
            let bottomColor = Color(red: 0.135 * factor, green: 0.135 * factor, blue: 0.15 * factor)
            layer.fill(cache.rounded, with: .linearGradient(Gradient(colors: [topColor, bottomColor]), startPoint: CGPoint(x: 0, y: CGFloat(s.top)), endPoint: CGPoint(x: 0, y: CGFloat(bottom))))
            for level in 1..<s.lightLevels where !highlights[level].isEmpty {
                let ink = Color(red: 0.66 * factor, green: 0.66 * factor, blue: 0.7 * factor).opacity(Double(level) / Double(s.lightLevels - 1))
                layer.fill(highlights[level], with: .color(ink))
            }
        }
    }

    private func drawDust(_ context: inout GraphicsContext, line: Float, solid: Float, rise: Float) {
        let s = ParticleCardDetailsCS.self, canvasHeight = Float(height) + s.top + s.bottom
        var paths: [Int: Path] = [:]
        for (layerIndex, layer) in cache.grains.enumerated() {
            let shift = rise * layer.rise
            let first = max(0, Int(floor(shift / layer.cell)) - 1)
            let last = min(layer.rows.count - 1, Int(ceil((min(canvasHeight, s.top + line + 9) + shift) / layer.cell)))
            guard first <= last else { continue }
            for row in first...last {
                for grain in layer.rows[row] {
                    let y = grain.y - shift, localY = y - s.top, approximateAbove = line - localY
                    guard localY <= Float(height), localY > -46, approximateAbove + 9 >= 0, approximateAbove - 9 <= grain.reach else { continue }
                    var x = grain.x, edge = line + grain.ragged
                    if layerIndex == 1 {
                        let sway = sin(localY * 0.09 + rise * 0.08) * 0.06
                        for _ in 0..<3 {
                            x = grain.x - sway * (edge - localY)
                            edge = line + particleCardDetailsRagged(x - s.side)
                        }
                    }
                    let above = edge - localY, localX = x - s.side
                    guard above >= 0, above <= grain.reach else { continue }
                    let beside = max(-localX, localX - Float(width))
                    let within = solid * particleCardDetailsSmooth(localY, -46, -6) * (1 - particleCardDetailsSmooth(beside, -2, 8)) * particleCardDetailsSmooth(edge, 0, 14)
                    let opacity = min(s.dustLevels - 1, max(0, Int((within * Float(s.dustLevels - 1)).rounded())))
                    guard opacity > 0 else { continue }
                    let tint = min(s.dustLevels - 1, max(0, Int((particleCardDetailsSmooth(above, 0, 60) * Float(s.dustLevels - 1)).rounded())))
                    for ring in 0..<s.coverageLayers {
                        let r = max(0, grain.radius - 0.4 + 0.65 * cache.coverage[ring])
                        let key = (tint * s.dustLevels + opacity) * s.coverageLayers + ring
                        paths[key, default: Path()].addEllipse(in: CGRect(x: CGFloat(x - r), y: CGFloat(y - r), width: CGFloat(r * 2), height: CGFloat(r * 2)))
                    }
                }
            }
        }
        let keys = paths.keys.sorted()
        for ring in 0..<s.coverageLayers {
            for key in keys where key % s.coverageLayers == ring {
                guard let path = paths[key] else { continue }
                let bucket = key / s.coverageLayers
                let t = Double(bucket / s.dustLevels) / Double(s.dustLevels - 1)
                let within = Double(bucket % s.dustLevels) / Double(s.dustLevels - 1)
                let before = within * Double(ring) / Double(s.coverageLayers)
                let next = within * Double(ring + 1) / Double(s.coverageLayers)
                let opacity = (next - before) / max(0.0001, 1 - before)
                context.fill(path, with: .color(Color(red: 0.72 - 0.22 * t, green: 0.72 - 0.22 * t, blue: 0.76 - 0.21 * t).opacity(opacity)))
            }
        }
    }
}

