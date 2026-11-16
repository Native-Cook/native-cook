import SwiftUI

/// Everything one frame of the scene needs; the view derives it from the loop time or the finger.
struct AuroraSlatSyncFrame: Equatable {
    /// Sync energy, 0 (resting core) to 1 (full aurora).
    var p = 0.0
    /// Seconds on the loop's clock that the aurora's wander is sampled at.
    var drift = 0.0
    var pushX = 0.0
    var pushY = 0.0
    /// Travel of the peak sweep down the slats, 0 to 1; negative when there is none.
    var sweep = -1.0
    var percent = 0
    var numberAlpha = 1.0
    var syncingAlpha = 1.0
    var syncedAlpha = 0.0
}

/// The timeline, shared number for number with the Compose and React Native ports.
enum AuroraSlatSyncMotion {
    static let period = 8.0
    static let buildStart = 0.8, buildEnd = 4.4
    static let sweepStart = 4.5, sweepLength = 1.0
    static let returnStart = 6.0, returnEnd = 7.2

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func inOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func smooth(_ x: Double) -> Double { let u = min(max(x, 0), 1); return u * u * (3 - 2 * u) }
    static func wrap(_ t: Double) -> Double { let m = t.truncatingRemainder(dividingBy: period); return m < 0 ? m + period : m }

    /// Finger travel with a rubber band: follows at first, then falls off.
    static func rubber(_ x: Double) -> Double { x / (1 + abs(x) / 140) }

    /// Unit step response of the release spring (response 0.45 s, damping ratio 0.78), from 1 to 0.
    static func spring(_ tau: Double) -> Double {
        guard tau > 0 else { return 1 }
        let omega = 2 * Double.pi / 0.45, zeta = 0.78
        let damped = omega * (1 - zeta * zeta).squareRoot()
        return exp(-zeta * omega * tau) * (cos(damped * tau) + zeta * omega / damped * sin(damped * tau))
    }

    /// Rest 0–0.8 s, build 0.8–4.4 s, peak 4.4–6 s (sweep 4.5–5.5 s), return 6–7.2 s, rest to 8 s.
    static func loopFrame(_ t: Double) -> AuroraSlatSyncFrame {
        let build = inOut(seg(t, buildStart, buildEnd))
        let back = inOut(seg(t, returnStart, returnEnd))
        var f = AuroraSlatSyncFrame()
        f.p = build * (1 - back)
        f.drift = t
        f.sweep = t >= sweepStart && t <= sweepStart + sweepLength ? (t - sweepStart) / sweepLength : -1
        f.percent = t < 6.55 ? Int((build * 100).rounded()) : 0
        f.numberAlpha = t < 6.55 ? 1 - seg(t, 6.0, 6.35) : seg(t, 6.75, 7.1)
        f.syncedAlpha = seg(t, 4.45, 4.7) * (1 - seg(t, 6.0, 6.35))
        f.syncingAlpha = t < 5.5 ? 1 - seg(t, 4.4, 4.6) : seg(t, 6.75, 7.1)
        return f
    }

    /// The state under the finger: the label follows the energy directly.
    static func heldFrame(p: Double, drift: Double, pushX: Double, pushY: Double, sweep: Double) -> AuroraSlatSyncFrame {
        var f = AuroraSlatSyncFrame()
        f.p = p
        f.drift = drift
        f.pushX = pushX
        f.pushY = pushY
        f.sweep = sweep
        f.percent = Int((p * 100).rounded())
        f.syncedAlpha = seg(p, 0.97, 1.0)
        f.syncingAlpha = 1 - seg(p, 0.94, 0.98)
        return f
    }

    /// Reduced motion: the finished, legible state.
    static let finished: AuroraSlatSyncFrame = {
        var f = AuroraSlatSyncFrame()
        f.p = 1
        f.percent = 100
        f.syncingAlpha = 0
        f.syncedAlpha = 1
        return f
    }()
}

/// Draws the whole screen in a 402 × 874 pt design space, scaled to the width it is given.
enum AuroraSlatSyncScene {
    static let designWidth = 402.0, designHeight = 874.0
    static let centerX = 201.0, stackY = 366.0
    static let slats = 11, pitch = 44.0, slatHeight = 36.0

    // Per-slat refraction: sideways displacement (× 22 pt) and blur amount (0 sharp … 1 soft).
    static let offsets: [Double] = [-0.62, 0.38, -0.21, 0.74, -0.48, 0.12, 0.57, -0.33, 0.81, -0.70, 0.26]
    static let blurs: [Double] = [0.95, 0.55, 0.80, 0.30, 0.62, 0.18, 0.45, 0.70, 0.35, 0.85, 1.0]

    // The aurora's three bodies: cobalt, teal, lime.
    static let colors: [(Double, Double, Double)] = [(0.110, 0.220, 1.0), (0.200, 0.740, 0.760), (0.640, 0.980, 0.300)]
    static let riseY: [Double] = [-0.52, 0.04, 0.56]
    static let wanderY: [Double] = [1, 2, 1]
    static let phaseY: [Double] = [0.0, 2.1, 4.0]
    static let swayX: [Double] = [0.46, 0.34, 0.50]
    static let wanderX: [Double] = [2, 1, 3]
    static let phaseX: [Double] = [0.6, 2.9, 4.4]
    static let radiusX: [Double] = [0.86, 0.66, 0.62]

    static let background = Color(.sRGB, red: 0.071, green: 0.039, blue: 0.149)

    static func draw(_ context: inout GraphicsContext, size: CGSize, frame f: AuroraSlatSyncFrame) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(background))
        let k = Double(size.width) / designWidth
        guard k > 0 else { return }
        context.scaleBy(x: k, y: k)
        context.translateBy(x: 0, y: (Double(size.height) / k - designHeight) / 2)

        // Vignette: a slightly lifted indigo behind the stack, darker towards the corners.
        var vignette = context
        vignette.translateBy(x: centerX, y: stackY)
        vignette.scaleBy(x: 560, y: 560)
        vignette.fill(
            Path(CGRect(x: -2, y: -2, width: 4, height: 4)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(.sRGB, red: 0.106, green: 0.063, blue: 0.227), location: 0),
                    .init(color: background, location: 0.55),
                    .init(color: Color(.sRGB, red: 0.043, green: 0.024, blue: 0.094), location: 1),
                ]),
                center: .zero, startRadius: 0, endRadius: 1))

        let p = f.p
        let eased = AuroraSlatSyncMotion.smooth(p)
        let energy = 0.86 + 0.14 * p
        let amps = [0.80 + 0.20 * p, 0.88, 0.34 + 0.66 * p]
        let turn = 2 * Double.pi / AuroraSlatSyncMotion.period
        var centersY = [Double](repeating: 0, count: 3)
        for b in 0..<3 {
            centersY[b] = riseY[b] * p + 0.10 * sin(turn * wanderY[b] * f.drift + phaseY[b]) + f.pushY / 240
        }

        // The light that escapes the glass: a wide, faint bloom behind the stack.
        var bloom = context
        bloom.blendMode = .plusLighter
        for b in 0..<3 {
            blob(bloom, x: centerX + f.pushX * 0.3, y: stackY + centersY[b] * 5 * pitch,
                 rx: 150 + 90 * p, ry: 120 + 70 * p, color: colors[b], alpha: (0.05 + 0.10 * p) * amps[b])
        }

        let sweepAt = f.sweep < 0 ? -99.0 : -1.5 + 13 * AuroraSlatSyncMotion.smooth(f.sweep)
        let sweepAmp = f.sweep < 0 ? 0.0 : sin(Double.pi * min(f.sweep, 1))
        let sigma = 0.42 + 0.30 * p

        for i in 0..<slats {
            let di = Double(i - 5), u = di / 5
            let half = 180 * (1 - 0.70 * u * u).squareRoot()
            let dim = 1 - 0.5 * u * u
            let top = stackY + di * pitch - slatHeight / 2
            let slope = -3.5 * u / half
            let lit = AuroraSlatSyncMotion.smooth(1 - (abs(di) - (1.9 + 3.0 * p)) / 1.6)
            let blur = blurs[i]
            let band = half * (0.46 + 0.54 * eased) * (0.75 + 0.25 * lit)
            let flash = sweepAmp * exp(-(di + 5 - sweepAt) * (di + 5 - sweepAt) / 1.6)
            let shift = offsets[i] * 22 * (0.5 + 0.5 * p) + f.pushX * (0.55 + 0.6 * blur)

            // The slat: a strip of glass seen slightly from the side, so its edges fan out.
            let x0 = -half - 26, x1 = half + 26
            var quad = Path()
            quad.move(to: CGPoint(x: centerX + x0, y: top + slope * x0))
            quad.addLine(to: CGPoint(x: centerX + x1, y: top + slope * x1))
            quad.addLine(to: CGPoint(x: centerX + x1, y: top + slatHeight + 0.4 * slope * x1))
            quad.addLine(to: CGPoint(x: centerX + x0, y: top + slatHeight + 0.4 * slope * x0))
            quad.closeSubpath()

            context.fill(quad, with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0), location: 0),
                    .init(color: .white.opacity(0.034 * dim), location: 0.2),
                    .init(color: .white.opacity(0.034 * dim), location: 0.8),
                    .init(color: .white.opacity(0), location: 1),
                ]),
                startPoint: CGPoint(x: centerX + x0, y: top), endPoint: CGPoint(x: centerX + x1, y: top)))

            // Inside it the aurora is displaced, stretched and blurred by this slat's own amount:
            // brightest under the sharp top edge, gone before the bottom one.
            var pane = Path()
            pane.move(to: CGPoint(x: centerX + x0 - 70, y: top + slope * (x0 - 70)))
            pane.addLine(to: CGPoint(x: centerX + x1 + 70, y: top + slope * (x1 + 70)))
            pane.addLine(to: CGPoint(x: centerX + x1 + 70, y: top + slatHeight + 4))
            pane.addLine(to: CGPoint(x: centerX + x0 - 70, y: top + slatHeight + 4))
            pane.closeSubpath()
            var inside = context
            inside.clip(to: pane)
            inside.translateBy(x: centerX, y: top)
            inside.concatenate(CGAffineTransform(a: 1, b: slope, c: 0, d: 1, tx: 0, ty: 0))
            for b in 0..<3 {
                let d = (u - centersY[b]) / sigma
                let alpha = energy * lit * dim * exp(-d * d) * amps[b] * (1 - 0.25 * blur)
                let x = swayX[b] * band * sin(turn * wanderX[b] * f.drift + phaseX[b] + Double(i) * 0.55) + shift
                blob(inside, x: x, y: slatHeight * 0.10,
                     rx: band * radiusX[b] * (1 + 0.45 * blur), ry: slatHeight * (1.9 + 0.5 * blur),
                     color: colors[b], alpha: min(1, alpha * 1.7))
            }
            if flash > 0.003 {
                var light = inside
                light.blendMode = .plusLighter
                blob(light, x: shift * 0.4, y: slatHeight * 0.10, rx: half * 1.05, ry: slatHeight * 1.9,
                     color: (0.90, 0.98, 1.0), alpha: 0.38 * flash * dim)
            }

            // The soft bottom edge of reeded glass: the image falls away into the dark.
            context.fill(pane, with: .linearGradient(
                Gradient(stops: [
                    .init(color: background.opacity(0), location: 0),
                    .init(color: background.opacity(0.42 + 0.2 * blur), location: 0.6),
                    .init(color: background.opacity(0.92), location: 1),
                ]),
                startPoint: CGPoint(x: centerX, y: top + slatHeight * 0.5),
                endPoint: CGPoint(x: centerX, y: top + slatHeight + 2)))

            // Edges catch light: a hairline on the glass, brighter where the aurora sits under it.
            let edge = 0.09 * dim
            edgeLine(context, from: x0 + 20, to: x1 - 20, slope: slope, top: top, alpha: edge)
            let glow = min(1, 0.55 * lit * energy * dim + 0.7 * flash * dim)
            edgeLine(context, from: shift - band * 1.1, to: shift + band * 1.1, slope: slope, top: top, alpha: glow)
        }

        ornaments(context)
        labels(context, frame: f)
    }

    /// A soft elliptical body of light: a radial falloff squashed to `rx` × `ry`.
    private static func blob(_ context: GraphicsContext, x: Double, y: Double, rx: Double, ry: Double,
                             color: (Double, Double, Double), alpha: Double) {
        guard alpha > 0.002, rx > 0.5 else { return }
        let tint = Color(.sRGB, red: color.0, green: color.1, blue: color.2)
        var c = context
        c.translateBy(x: x, y: y)
        c.scaleBy(x: rx, y: ry)
        c.fill(
            Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: tint.opacity(alpha), location: 0),
                    .init(color: tint.opacity(alpha * 0.86), location: 0.5),
                    .init(color: tint.opacity(alpha * 0.30), location: 0.8),
                    .init(color: tint.opacity(0), location: 1),
                ]),
                center: .zero, startRadius: 0, endRadius: 1))
    }

    private static func edgeLine(_ context: GraphicsContext, from a: Double, to b: Double, slope: Double,
                                 top: Double, alpha: Double) {
        guard alpha > 0.004, b > a else { return }
        var line = Path()
        let start = CGPoint(x: centerX + a, y: top + slope * a)
        let end = CGPoint(x: centerX + b, y: top + slope * b)
        line.move(to: start)
        line.addLine(to: end)
        context.stroke(line, with: .linearGradient(
            Gradient(stops: [
                .init(color: .white.opacity(0), location: 0),
                .init(color: .white.opacity(alpha), location: 0.5),
                .init(color: .white.opacity(0), location: 1),
            ]),
            startPoint: start, endPoint: end), lineWidth: 1)
    }

    private static func ornaments(_ context: GraphicsContext) {
        // Crosshair on the stack's centre.
        var cross = Path()
        cross.move(to: CGPoint(x: centerX - 11, y: stackY))
        cross.addLine(to: CGPoint(x: centerX + 11, y: stackY))
        cross.move(to: CGPoint(x: centerX, y: stackY - 11))
        cross.addLine(to: CGPoint(x: centerX, y: stackY + 11))
        context.stroke(cross, with: .color(.white.opacity(0.9)), lineWidth: 1)

        // Viewfinder corners, top right.
        var corners = Path()
        let l = 346.0, t = 62.0, s = 30.0, a = 8.0
        for (cx, cy, dx, dy) in [(l, t, 1.0, 1.0), (l + s, t, -1.0, 1.0), (l, t + s, 1.0, -1.0), (l + s, t + s, -1.0, -1.0)] {
            corners.move(to: CGPoint(x: cx + dx * a, y: cy))
            corners.addLine(to: CGPoint(x: cx, y: cy))
            corners.addLine(to: CGPoint(x: cx, y: cy + dy * a))
        }
        context.stroke(corners, with: .color(.white.opacity(0.55)), lineWidth: 1)

        // Dot grids in two corners: a staircase of small dots.
        var dots = Path()
        for row in 0..<4 {
            for col in 0..<(4 - row) {
                dots.addEllipse(in: CGRect(x: 26 + Double(col) * 6 - 1, y: 122 + Double(row) * 6 - 1, width: 2, height: 2))
                dots.addEllipse(in: CGRect(x: 376 - Double(col) * 6 - 1, y: 626 - Double(row) * 6 - 1, width: 2, height: 2))
            }
        }
        context.fill(dots, with: .color(.white.opacity(0.42)))
    }

    private static func labels(_ context: GraphicsContext, frame f: AuroraSlatSyncFrame) {
        context.draw(
            Text("Halo Vault").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white.opacity(0.94)),
            at: CGPoint(x: 24, y: 77), anchor: .leading)
        context.draw(
            Text("Studio library").font(.system(size: 13)).foregroundStyle(.white.opacity(0.5)),
            at: CGPoint(x: 24, y: 98), anchor: .leading)

        var number = context
        number.opacity = f.numberAlpha
        number.draw(
            Text("\(f.percent)%").font(.system(size: 46, weight: .light)).monospacedDigit()
                .foregroundStyle(.white.opacity(0.96)),
            at: CGPoint(x: centerX, y: 672), anchor: .center)

        if f.syncingAlpha > 0.003 {
            var syncing = context
            syncing.opacity = f.syncingAlpha
            syncing.draw(
                Text("Syncing…").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white.opacity(0.9)),
                at: CGPoint(x: centerX, y: 716 - 6 * (1 - f.syncingAlpha)), anchor: .center)
        }
        if f.syncedAlpha > 0.003 {
            var synced = context
            synced.opacity = f.syncedAlpha
            synced.draw(
                Text("All synced").font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color(.sRGB, red: 0.72, green: 0.98, blue: 0.44)),
                at: CGPoint(x: centerX, y: 716 + 6 * (1 - f.syncedAlpha)), anchor: .center)
        }
        context.draw(
            Text("1,284 files · 3 devices").font(.system(size: 13)).foregroundStyle(.white.opacity(0.46)),
            at: CGPoint(x: centerX, y: 742), anchor: .center)
    }
}
