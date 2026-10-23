import CoreGraphics
import SwiftUI
import UIKit

enum ZenSandGardenMetrics {
    // The height map: the sand's shape kept as a grey level, `heightScale` pixels per point. `base`
    // is level sand, darker is lower, lighter is higher. It is only ever drawn into, a strip at a time.
    static let heightScale: CGFloat = 2
    static let base: CGFloat = 0.5

    // The rake: `tines` teeth `tineGap` pt apart. One pass leaves a groove under each tooth, a ridge
    // between them and a berm on either side, `rakeHalf` pt from the middle. The profile is sampled at
    // `profileSteps` + 1 points across and stamped from the last cross-section to the new one every
    // `stampStep` pt the rake travels.
    static let tines = 5
    static let tineGap: CGFloat = 9
    static let rakeHalf: CGFloat = tineGap * 3
    static let groove: CGFloat = 0.3
    static let profileSteps = 48
    static let stampStep = 1.5
    // The head follows the finger at `follow` per second, stepped at `substep` s, and turns towards
    // its motion at `turn` per second.
    static let follow = 32.0
    static let substep = 1.0 / 240
    static let turn = 24.0
    // The head keeps this share of its half width clear of a stone's outline.
    static let stoneClearance: CGFloat = 0.85
    // The wooden rake drawn over the sand.
    static let barLength: CGFloat = CGFloat(tines - 1) * tineGap + 18
    static let barThick: CGFloat = 8
    static let handleLength: CGFloat = 220
    static let handleThick: CGFloat = 6
    static let rakeLift = 7.0
    static let fade = 0.18

    // A tap on a stone sends `rings` grooves out round it over `rippleTime` s.
    static let rings = 5
    static let rippleTime = 1.3
    static let ringStops = 72
    // The smoothing sweep: a bank of sand `pile` pt deep crosses the garden in `sweepTime` s and
    // leaves faint broom streaks `streakPeriod` pt apart behind it.
    static let pile: CGFloat = 44
    static let sweepTime = 2.6
    static let streakPeriod: CGFloat = 37
    static let streaks: [CGFloat] = [0.0, 0.62, 0.15, 0.9, 0.35, 0.05, 0.7, 0.48, 0.95, 0.22, 0.55, 0.1, 0.8, 0.3]
    static let streakDepth: CGFloat = 0.02
    static let smoothSwipe: CGFloat = 40
    static let userHold = 4.0

    // Light: a low warm sun swinging about its bearing and height once per `loop`.
    static let sunBearing = -138.0
    static let sunSwing = 26.0
    static let sunLow = 17.0
    static let sunHigh = 25.0
    static let sunMorning: [Double] = [1.0, 0.9, 0.74]
    static let sunEvening: [Double] = [1.0, 0.74, 0.56]
    static let sky: [Double] = [0.52, 0.55, 0.64]

    // autoPlay, one `loop` in seconds from smooth sand: a spiral, three rows of waves, the stones
    // rung one after another, then the sweep, then rest.
    static let loop = 18.0
    static let spiralFrom = 0.8
    static let spiralTime = 4.6
    static let spiralCentre = CGPoint(x: 0.63, y: 0.25)
    static let spiralTurns = 2.4
    static let spiralInner = 16.0
    static let spiralOuter = 124.0
    static let rowsFrom = 5.8
    static let rowTime = 1.3
    static let rowPause = 0.16
    static let rowTop = 0.66
    static let rowGap = Double(rakeHalf * 2)
    static let rowWave = 12.0
    static let rowWavelength = 96.0
    static let ringAt = [10.6, 11.2, 11.8]
    static let sweepAt = 13.2

    static let stones: [ZenSandGardenStone] = [
        ZenSandGardenStone(x: 0.27, y: 0.42, r: 48, h: 36, a2: 0.12, p2: 0.6, a3: 0.05, p3: 2.1, a5: 0.025, p5: 0.4, tint: [0.4, 0.41, 0.43], moss: 0.85),
        ZenSandGardenStone(x: 0.74, y: 0.6, r: 33, h: 27, a2: 0.09, p2: 2.2, a3: 0.06, p3: 0.9, a5: 0.02, p5: 1.7, tint: [0.56, 0.5, 0.44], moss: 0.4),
        ZenSandGardenStone(x: 0.41, y: 0.79, r: 21, h: 19, a2: 0.14, p2: 1.3, a3: 0.04, p3: 3.0, a5: 0.03, p5: 2.6, tint: [0.31, 0.3, 0.3], moss: 1),
    ]

    /// The rake's cross-section: grooves under the teeth, ridges between, berms at the edges.
    static func profile(_ u: CGFloat) -> CGFloat {
        let a = abs(u)
        let edge = tineGap * 2.5
        if a <= edge { return base - groove * cos(2 * .pi * u / tineGap) }
        let t = min(1, (a - edge) / (rakeHalf - edge))
        let s = t * t * (3 - 2 * t)
        return base + groove * (1 - s)
    }

    static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    static func easeOut(_ t: Double) -> Double { 1 - pow(1 - t, 3) }

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }
}

/// A stone, placed as a share of the screen: radius and height in pt, a wobbly outline made of three
/// cosines (amplitude, phase), a tint and how much moss grows at its foot.
struct ZenSandGardenStone {
    let x: Double
    let y: Double
    let r: Double
    let h: Double
    let a2: Double
    let p2: Double
    let a3: Double
    let p3: Double
    let a5: Double
    let p5: Double
    let tint: [Double]
    let moss: Double

    func centre(_ size: CGSize) -> CGPoint { CGPoint(x: x * size.width, y: y * size.height) }

    /// The outline radius at a bearing (the same three cosines the shader uses).
    func radius(_ th: Double) -> Double {
        r * (1 + a2 * cos(2 * th + p2) + a3 * cos(3 * th + p3) + a5 * cos(5 * th + p5))
    }

    /// Distance from the centre as a share of the outline at that bearing.
    func distance(_ p: CGPoint, _ size: CGSize) -> Double {
        let c = centre(size)
        let qx = p.x - c.x
        let qy = p.y - c.y
        return hypot(qx, qy) / radius(atan2(qy, qx))
    }

    /// The outer edge of the outline, where its ring grooves start.
    var reach: Double { r * (1 + a2 + a3 + a5) }
}

/// The sand's shape: an 8-bit grey bitmap, 2 px per pt, drawn into incrementally with Core Graphics.
/// The stones are clipped out of every stamp.
final class ZenSandGardenHeightMap {
    private typealias M = ZenSandGardenMetrics
    let size: CGSize
    private let context: CGContext
    private let grey = CGColorSpaceCreateDeviceGray()
    private let stonesClip: CGPath
    private let profileValues: [Float]
    // The bitmap's own memory (row 0 at the top), its size in px, and which pixels are stone.
    private let pixels: UnsafeMutablePointer<UInt8>
    private let pw: Int
    private let ph: Int
    private let stoneMask: UnsafeMutablePointer<Bool>
    private let pileGradient: CGGradient
    private let streakAcross: CGImage
    private let streakDown: CGImage
    private(set) var image: Image?

    init?(size: CGSize) {
        self.size = size
        let pw = Int(ceil(size.width * M.heightScale))
        let ph = Int(ceil(size.height * M.heightScale))
        guard pw > 0, ph > 0 else { return nil }
        let memory = UnsafeMutablePointer<UInt8>.allocate(capacity: pw * ph)
        memory.initialize(repeating: 128, count: pw * ph)
        guard let ctx = CGContext(data: memory, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: pw,
                                  space: grey, bitmapInfo: CGImageAlphaInfo.none.rawValue)
        else {
            memory.deallocate()
            return nil
        }
        context = ctx
        pixels = memory
        self.pw = pw
        self.ph = ph
        // Points, y down.
        ctx.translateBy(x: 0, y: CGFloat(ph))
        ctx.scaleBy(x: M.heightScale, y: -M.heightScale)
        ctx.interpolationQuality = .low

        // Everything but the stones, slightly grown: sand is never drawn under a stone.
        let clip = CGMutablePath()
        clip.addRect(CGRect(x: -10, y: -10, width: size.width + 20, height: size.height + 20))
        for s in M.stones {
            let c = s.centre(size)
            for i in 0...72 {
                let th = Double(i) / 72 * .pi * 2
                let r = s.radius(th) + 0.5
                let p = CGPoint(x: c.x + cos(th) * r, y: c.y + sin(th) * r)
                if i == 0 { clip.move(to: p) } else { clip.addLine(to: p) }
            }
            clip.closeSubpath()
        }
        stonesClip = clip
        // The same outlines as a mask for the stamps, which write the pixels directly.
        let mask = UnsafeMutablePointer<Bool>.allocate(capacity: pw * ph)
        mask.initialize(repeating: false, count: pw * ph)
        if let m = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: pw,
                             space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue),
           let data = m.data?.assumingMemoryBound(to: UInt8.self) {
            m.translateBy(x: 0, y: CGFloat(ph))
            m.scaleBy(x: M.heightScale, y: -M.heightScale)
            m.setFillColor(gray: 1, alpha: 1)
            m.addPath(clip)
            m.fillPath(using: .evenOdd)
            for i in 0..<(pw * ph) { mask[i] = data[i] < 128 }
        }
        stoneMask = mask

        // The rake's profile, sampled at 49 points across; between them the grey is interpolated.
        profileValues = (0...M.profileSteps).map {
            Float(M.profile(-M.rakeHalf + 2 * M.rakeHalf * CGFloat($0) / CGFloat(M.profileSteps)))
        }

        let pileValues: [CGFloat] = [M.base, M.base + M.groove * 0.35, M.base + M.groove * 1.1, 0.98, M.base + M.groove * 0.4]
        pileGradient = CGGradient(colorSpace: grey, colorComponents: pileValues.flatMap { [$0, 1] },
                                  locations: [0, 0.45, 0.76, 0.9, 1], count: 5)!
        streakAcross = Self.streakTile(across: true)
        streakDown = Self.streakTile(across: false)

        // Level, streaked sand to start with.
        ctx.saveGState()
        drawStreaks(CGRect(x: -2, y: -2, width: size.width + 4, height: size.height + 4), across: true)
        ctx.restoreGState()
        snapshot()
    }

    /// One period of faint broom stripes (14 stops, linearly between, repeating), as a tile 2 px per pt.
    private static func streakTile(across: Bool) -> CGImage {
        let period = Int(M.streakPeriod * M.heightScale)
        let thick = 8
        let w = across ? period : thick
        let h = across ? thick : period
        var pixels = [UInt8](repeating: 0, count: w * h)
        let n = M.streaks.count
        for k in 0..<period {
            let t = (CGFloat(k) + 0.5) / CGFloat(period) * CGFloat(n)
            let i = min(n - 1, Int(t))
            let f = t - CGFloat(i)
            let a = M.streaks[i]
            let b = M.streaks[(i + 1) % n]
            let v = M.base + ((a + (b - a) * f) - 0.5) * M.streakDepth
            let byte = UInt8(max(0, min(255, (v * 255).rounded())))
            for j in 0..<thick {
                if across { pixels[j * w + k] = byte } else { pixels[k * w + j] = byte }
            }
        }
        let provider = CGDataProvider(data: Data(pixels) as CFData)!
        return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: w,
                       space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
                       provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
    }

    private func drawStreaks(_ rect: CGRect, across: Bool) {
        context.saveGState()
        context.clip(to: rect)
        // The tile is anchored at the origin, so the stripes stay put from one sweep to the next.
        let tile = across
            ? CGRect(x: 0, y: 0, width: M.streakPeriod, height: 4)
            : CGRect(x: 0, y: 0, width: 4, height: M.streakPeriod)
        context.draw(across ? streakAcross : streakDown, in: tile, byTiling: true)
        context.restoreGState()
    }

    /// Starts a frame's drawing: everything drawn until `end` misses the stones.
    func begin() {
        context.saveGState()
        context.addPath(stonesClip)
        context.clip(using: .evenOdd)
    }

    func end(dirty: Bool) {
        context.restoreGState()
        if dirty { snapshot() }
    }

    private func snapshot() {
        guard let cg = context.makeImage() else { return }
        image = Image(decorative: cg, scale: M.heightScale).interpolation(.high)
    }

    deinit {
        pixels.deallocate()
        stoneMask.deallocate()
    }

    /// Fills the strip between two cross-sections (centre and unit normal) with the rake's profile:
    /// two triangles per profile step, like the React Native triangle strip, each one's grey
    /// interpolated between its corners. Rasterised straight into the bitmap, skipping the stones.
    func stamp(from c0: CGPoint, _ n0: CGPoint, to c1: CGPoint, _ n1: CGPoint) {
        let count = M.profileSteps + 1
        let k = Float(M.heightScale)
        var xs = [Float](repeating: 0, count: count * 2)
        var ys = [Float](repeating: 0, count: count * 2)
        for i in 0..<count {
            let u = -M.rakeHalf + 2 * M.rakeHalf * CGFloat(i) / CGFloat(M.profileSteps)
            xs[i] = Float(c0.x + n0.x * u) * k
            ys[i] = Float(c0.y + n0.y * u) * k
            xs[count + i] = Float(c1.x + n1.x * u) * k
            ys[count + i] = Float(c1.y + n1.y * u) * k
        }
        for i in 0..<M.profileSteps {
            let a = i, b = i + 1, c = count + i, d = count + i + 1
            triangle(xs[a], ys[a], profileValues[i], xs[b], ys[b], profileValues[i + 1], xs[c], ys[c], profileValues[i])
            triangle(xs[b], ys[b], profileValues[i + 1], xs[d], ys[d], profileValues[i + 1], xs[c], ys[c], profileValues[i])
        }
    }

    /// Writes one triangle (pixel coordinates) whose grey varies linearly between its corners.
    private func triangle(_ x0: Float, _ y0: Float, _ v0: Float, _ x1: Float, _ y1: Float, _ v1: Float,
                          _ x2: Float, _ y2: Float, _ v2: Float) {
        let area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)
        // NaN fails the first test; an infinite corner would trap in Int() below.
        guard abs(area) > 1e-4, area.isFinite else { return }
        let minX = max(0, Int((min(x0, x1, x2) - 0.5).rounded(.down)))
        let maxX = min(pw - 1, Int((max(x0, x1, x2) - 0.5).rounded(.up)))
        let minY = max(0, Int((min(y0, y1, y2) - 0.5).rounded(.down)))
        let maxY = min(ph - 1, Int((max(y0, y1, y2) - 0.5).rounded(.up)))
        guard minX <= maxX, minY <= maxY else { return }
        let inv = 1 / area
        // A hair of slack so pixels on a shared edge are written by both triangles (same value).
        let eps: Float = -1e-4
        for py in minY...maxY {
            let cy = Float(py) + 0.5
            let row = py * pw
            for px in minX...maxX {
                let cx = Float(px) + 0.5
                let w0 = ((x1 - cx) * (y2 - cy) - (y1 - cy) * (x2 - cx)) * inv
                let w1 = ((x2 - cx) * (y0 - cy) - (y2 - cy) * (x0 - cx)) * inv
                let w2 = 1 - w0 - w1
                if w0 < eps || w1 < eps || w2 < eps { continue }
                let index = row + px
                if stoneMask[index] { continue }
                let v = w0 * v0 + w1 * v1 + w2 * v2
                pixels[index] = UInt8(max(0, min(255, (v * 255).rounded())))
            }
        }
    }

    /// The rings round a stone, out to `front` pt from its centre: a radial gradient of 73 stops,
    /// written straight into the bitmap (Core Graphics draws many-stop radial gradients in visible
    /// facets on an 8-bit grey bitmap).
    func ripple(_ s: ZenSandGardenStone, front: CGFloat) {
        let inner = CGFloat(s.reach) + 3
        guard front > inner else { return }
        let n = M.ringStops
        var stops = [Float](repeating: 0, count: n + 2)
        for i in 0...n {
            let r = CGFloat(i) / CGFloat(n) * front
            var v = M.base + M.groove * 0.25
            if r >= inner {
                // Rings start on a ridge, so the sand rises gently from the stone's collar.
                v = M.base + M.groove * cos(2 * .pi * (r - inner) / M.tineGap)
                // The leading edge is a berm of pushed sand.
                let lead = CGFloat(M.clamp01(Double((r - (front - M.tineGap * 0.6)) / (M.tineGap * 0.6))))
                v += (M.base + M.groove * 1.15 - v) * lead
            }
            stops[i] = Float(v) * 255
        }
        stops[n + 1] = stops[n]
        let k = Float(M.heightScale)
        let c = s.centre(size)
        let cx = Float(c.x) * k
        let cy = Float(c.y) * k
        let radius = Float(front) * k
        let r2 = radius * radius
        let scale = Float(n) / radius
        // Inside the stone's smallest radius everything is stone: only the annulus is visited.
        let hole = Float(s.r * (1 - s.a2 - s.a3 - s.a5) - 1) * k
        let hole2 = hole > 0 ? hole * hole : -1
        let minY = max(0, Int(cy - radius)), maxY = min(ph - 1, Int(cy + radius) + 1)
        guard minY <= maxY else { return }
        let pixels = self.pixels
        let mask = stoneMask
        let width = pw
        stops.withUnsafeBufferPointer { buffer in
            let table = buffer.baseAddress!
            for py in minY...maxY {
                let dy = Float(py) + 0.5 - cy
                let dy2 = dy * dy
                if dy2 > r2 { continue }
                let span = (r2 - dy2).squareRoot()
                var x0 = Int(cx - span - 0.5)
                var x1 = Int(cx + span - 0.5) + 1
                if x0 < 0 { x0 = 0 }
                if x1 > width - 1 { x1 = width - 1 }
                if x0 > x1 { continue }
                // The span hidden under the stone, skipped.
                var h0 = x1 + 1
                var h1 = x1
                if dy2 < hole2 {
                    let inside = (hole2 - dy2).squareRoot()
                    h0 = Int(cx - inside + 0.5)
                    h1 = Int(cx + inside - 1.5)
                }
                let row = py * width
                var px = x0
                while px <= x1 {
                    if px == h0, h1 >= h0 {
                        px = h1 + 1
                        continue
                    }
                    let dx = Float(px) + 0.5 - cx
                    let d2 = dx * dx + dy2
                    let index = row + px
                    if d2 <= r2 && !mask[index] {
                        let t = d2.squareRoot() * scale
                        let i = Int(t)
                        let a = table[i]
                        var v = a + (table[i + 1] - a) * (t - Float(i)) + 0.5
                        if v < 0 { v = 0 } else if v > 255 { v = 255 }
                        pixels[index] = UInt8(v)
                    }
                    px += 1
                }
            }
        }
    }

    /// One frame of the sweep: level, streaked sand behind the bank, the bank itself at `front` pt
    /// along the sweep's direction (1 down, 2 up, 3 right, 4 left).
    func sweep(dir: Int, front: CGFloat) {
        let w = size.width
        let h = size.height
        let vertical = dir <= 2
        let extent = vertical ? h : w
        let back = front - M.pile
        let forward = dir == 1 || dir == 3
        let a = forward ? back : extent - back
        let b = forward ? front : extent - front
        if vertical {
            let top = min(a, b)
            if dir == 1 { drawStreaks(CGRect(x: 0, y: -2, width: w, height: max(0, a + 2)), across: true) }
            else { drawStreaks(CGRect(x: 0, y: a, width: w, height: max(0, h + 2 - a)), across: true) }
            context.saveGState()
            context.clip(to: CGRect(x: 0, y: top, width: w, height: M.pile))
            context.drawLinearGradient(pileGradient, start: CGPoint(x: 0, y: a), end: CGPoint(x: 0, y: b),
                                       options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            context.restoreGState()
        } else {
            let left = min(a, b)
            if dir == 3 { drawStreaks(CGRect(x: -2, y: 0, width: max(0, a + 2), height: h), across: false) }
            else { drawStreaks(CGRect(x: a, y: 0, width: max(0, w + 2 - a), height: h), across: false) }
            context.saveGState()
            context.clip(to: CGRect(x: left, y: 0, width: M.pile, height: h))
            context.drawLinearGradient(pileGradient, start: CGPoint(x: a, y: 0), end: CGPoint(x: b, y: 0),
                                       options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            context.restoreGState()
        }
    }
}

/// One frame to draw.
struct ZenSandGardenFrame {
    var heightMap: Image?
    var elapsed: Double
    var head: CGPoint
    var heading: Double
    var rakeAlpha: Double
}

/// The garden's state, stepped once a frame like the React Native frame callback: the rake chasing
/// the finger, the rings, the sweep and the autoPlay script, all drawn into the height map.
final class ZenSandGardenEngine {
    private typealias M = ZenSandGardenMetrics

    private var map: ZenSandGardenHeightMap?
    private var lastDate: Date?
    private var elapsed = 0.0

    // The rake: the finger (target), the head that follows it, its heading and the last section.
    private var fingerDown = false
    private var target = CGPoint.zero
    private var head = CGPoint.zero
    private var dir = CGPoint(x: 1, y: 0)
    private var rakeAlpha = 0.0
    private var lastSection: (c: CGPoint, n: CGPoint)?
    private var lastStamp = CGPoint.zero
    private var newStroke = true

    // Ripples (start time per stone, < 0 when idle) and the sweep (direction 0 when idle).
    private var ripples = [-1.0, -1.0, -1.0]
    private var sweepDir = 0
    private var sweepFrom = 0.0

    // autoPlay's clock, how long a touch still holds it, and whether its finger is down.
    private var clock = 0.0
    private var hold = 0.0
    private var autoFinger = false

    // The touch under way.
    private var touchStart: Date?
    private var panning = false
    private var multi = false
    private var swept = false
    // Whether two fingers are on the garden right now (the watcher's view of the touch).
    private var twoDown = false

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let notify = UINotificationFeedbackGenerator()

    private var size = CGSize.zero

    func step(to date: Date, size: CGSize, autoPlay: Bool, reducedMotion: Bool) -> ZenSandGardenFrame {
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.05)
        if size.width > 0, map == nil || map?.size != size {
            map = ZenSandGardenHeightMap(size: size)
            self.size = size
        }
        if dt > 0, let map {
            advance(dt: dt, map: map, autoPlay: autoPlay, reducedMotion: reducedMotion)
        }
        return ZenSandGardenFrame(heightMap: map?.image, elapsed: elapsed, head: head,
                                  heading: atan2(dir.y, dir.x), rakeAlpha: rakeAlpha)
    }

    private func advance(dt: Double, map: ZenSandGardenHeightMap, autoPlay: Bool, reducedMotion: Bool) {
        let w = size.width
        let h = size.height
        var dirty = false
        let now = elapsed + dt
        elapsed = now

        // autoPlay drives the same finger, ripples and sweep a touch would.
        if hold > 0 {
            if !fingerDown || autoFinger { hold -= dt }
            if hold <= 0 { clock = 0 }
        } else if autoPlay {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            func passed(_ at: Double) -> Bool { (before < at && t >= at) || (t < before && (at > before || at <= t)) }
            if let f = scriptFinger(t, w, h) {
                if !autoFinger {
                    autoFinger = true
                    press(f)
                } else {
                    target = f
                }
            } else if autoFinger {
                autoFinger = false
                fingerDown = false
            }
            for k in M.ringAt.indices where passed(M.ringAt[k]) { ring(k) }
            if passed(M.sweepAt) { sweep(1) }
        }

        map.begin()
        // The head chases the finger in small steps, never entering a stone.
        let down = fingerDown
        if down {
            let steps = max(1, Int(ceil(dt / M.substep)))
            let k = min(1, M.follow * (dt / Double(steps)))
            for _ in 0..<steps {
                head.x += (target.x - head.x) * k
                head.y += (target.y - head.y) * k
                keepOffStones()
            }
            let mx = head.x - lastStamp.x
            let my = head.y - lastStamp.y
            let moved = hypot(mx, my)
            if moved >= M.stampStep {
                let ux = mx / moved
                let uy = my / moved
                var dx = dir.x
                var dy = dir.y
                let turned = newStroke || dx * ux + dy * uy < -0.2
                if turned {
                    dx = ux
                    dy = uy
                } else {
                    let turn = min(1, M.turn * dt)
                    dx += (ux - dx) * turn
                    dy += (uy - dy) * turn
                    let l = hypot(dx, dy)
                    let d = l == 0 ? 1 : l
                    dx /= d
                    dy /= d
                }
                dir = CGPoint(x: dx, y: dy)
                let section = (c: head, n: CGPoint(x: -dy, y: dx))
                if !turned, let last = lastSection {
                    map.stamp(from: last.c, last.n, to: section.c, section.n)
                    dirty = true
                }
                lastSection = section
                lastStamp = head
                newStroke = false
            }
        }
        let fade = min(1, dt / M.fade)
        rakeAlpha += ((down ? 1 : 0) - rakeAlpha) * fade

        // Rings travelling out from struck stones.
        for k in M.stones.indices where ripples[k] >= 0 {
            let p = M.clamp01((now - ripples[k]) / (reducedMotion ? 0.01 : M.rippleTime))
            let s = M.stones[k]
            let inner = s.reach + 3
            let front = inner + (Double(M.rings) * Double(M.tineGap) + Double(M.tineGap) * 0.6) * M.easeOut(p)
            map.ripple(s, front: CGFloat(front))
            dirty = true
            if p >= 1 { ripples[k] = -1 }
        }

        // The sweep: across the whole garden, then done.
        if sweepDir != 0 {
            let p = M.clamp01((now - sweepFrom) / (reducedMotion ? 0.01 : M.sweepTime))
            let extent = sweepDir <= 2 ? h : w
            map.sweep(dir: sweepDir, front: -M.pile + (extent + M.pile * 2 + 4) * CGFloat(M.easeInOut(p)))
            dirty = true
            if p >= 1 { sweepDir = 0 }
        }
        map.end(dirty: dirty)
    }

    /// Where autoPlay's finger is at `t` s into the loop, or nil while it is up.
    private func scriptFinger(_ t: Double, _ w: CGFloat, _ h: CGFloat) -> CGPoint? {
        let spiral = (t - M.spiralFrom) / M.spiralTime
        if spiral >= 0 && spiral <= 1 {
            let e = M.easeInOut(spiral) * 0.85 + spiral * 0.15
            let r = M.spiralInner + (M.spiralOuter - M.spiralInner) * e
            let a = -Double.pi / 2 + e * M.spiralTurns * .pi * 2
            return CGPoint(x: M.spiralCentre.x * w + cos(a) * r, y: M.spiralCentre.y * h + sin(a) * r)
        }
        for row in 0..<3 {
            let start = M.rowsFrom + Double(row) * (M.rowTime + M.rowPause)
            let p = (t - start) / M.rowTime
            if p >= 0 && p <= 1 {
                let e = p * 0.7 + M.easeInOut(p) * 0.3
                let forward = row % 2 == 0
                let x = forward ? -50 + (w + 100) * e : w + 50 - (w + 100) * e
                let y = M.rowTop * h + Double(row) * M.rowGap + M.rowWave * sin(2 * .pi * x / M.rowWavelength)
                return CGPoint(x: x, y: y)
            }
        }
        return nil
    }

    /// Puts the head outside every stone's clearance ring, sliding it round the outline.
    private func keepOffStones() {
        var x = head.x
        var y = head.y
        for s in M.stones {
            let c = s.centre(size)
            let qx = x - c.x
            let qy = y - c.y
            let l = hypot(qx, qy)
            let keep = s.radius(atan2(qy, qx)) + M.rakeHalf * M.stoneClearance
            if l < keep {
                if l < 0.001 {
                    x = c.x + keep
                    y = c.y
                } else {
                    x = c.x + qx * keep / l
                    y = c.y + qy * keep / l
                }
            }
        }
        head = CGPoint(x: x, y: y)
    }

    private func press(_ p: CGPoint) {
        fingerDown = true
        target = p
        head = p
        keepOffStones()
        newStroke = true
        lastStamp = head
    }

    private func ring(_ k: Int) { ripples[k] = elapsed }

    private func sweep(_ d: Int) {
        sweepDir = d
        sweepFrom = elapsed
    }

    private func touched() {
        hold = M.userHold
        if autoFinger {
            autoFinger = false
            fingerDown = false
        }
    }

    // MARK: Touches

    /// One finger: a pan after 4 pt rakes; a touch lifted within 0.5 s that never went that far is a
    /// tap, which rings a stone when it lands within 1.2 of its radius.
    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
            // A two-finger touch whose drag never reached SwiftUI must not leave the next drag dead.
            if !twoDown { multi = false }
        }
        guard !multi else { return }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= 4 else { return }
            panning = true
            touched()
            press(value.startLocation)
            target = value.location
            light.impactOccurred()
        } else {
            target = value.location
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            panning = false
            fingerDown = false
            multi = false
        }
        guard !multi, !panning, let start = touchStart, value.time.timeIntervalSince(start) <= 0.5,
              hypot(value.translation.width, value.translation.height) < 10 else { return }
        for k in M.stones.indices where M.stones[k].distance(value.location, size) < 1.2 {
            touched()
            ring(k)
            medium.impactOccurred()
            return
        }
    }

    /// Two fingers: the rake lets go, and a swipe longer than 40 pt sweeps the sand in its direction.
    func twoFingersBegan() {
        twoDown = true
        multi = true
        swept = false
        touched()
        fingerDown = false
        panning = false
    }

    func twoFingersMoved(_ t: CGSize) {
        guard multi, !swept, hypot(t.width, t.height) >= M.smoothSwipe else { return }
        swept = true
        sweep(abs(t.height) >= abs(t.width) ? (t.height > 0 ? 1 : 2) : (t.width > 0 ? 3 : 4))
        notify.notificationOccurred(.success)
    }

    /// The touch stays two-fingered until the last finger lifts (the one-finger drag ends then).
    func twoFingersEnded() { twoDown = false }
}
