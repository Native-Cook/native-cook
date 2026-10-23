import SwiftUI
import UIKit

/// A dusk meadow drawn in layered grass silhouettes under a crescent moon, with about 150 fireflies
/// flocking over it as boids and blinking with an additive glow. Touching gathers the swarm round the
/// finger, where it orbits; a fast flick scatters it; holding still makes the blinking roll out from
/// the finger in a wave. The grass sways and bends where the swarm passes low.
struct FireflySwarm: View {
    /// Gathers the swarm on a figure-eight, flicks it apart, syncs its blinking, lets it settle, on a loop.
    var autoPlay = false
    /// The line of hints along the bottom edge.
    var hint = ["Touch to gather", "Flick to scatter", "Hold to sync"]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = FireflySwarmEngine()
    @State private var art = FireflySwarmArt()
    // True while a finger is down; SwiftUI resets it even when the drag is cancelled without onEnded.
    @GestureState private var touching = false

    private typealias M = FireflySwarmMetrics

    var body: some View {
        // The block draws under the system bars and keeps its hints clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x070a1d).ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        return ZStack(alignment: .bottom) {
            TimelineView(.animation) { timeline in
                let _ = engine.step(to: timeline.date, size: size, autoPlay: auto, reducedMotion: reduceMotion)
                let _ = art.prepare(size: size, scale: displayScale)
                Canvas { context, canvasSize in
                    draw(in: &context, size: canvasSize)
                }
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(
                // One recogniser stands in for the simultaneous pan and long press.
                DragGesture(minimumDistance: 0)
                    .updating($touching) { _, state, _ in state = true }
                    .onChanged { engine.touchChanged($0) }
                    .onEnded { engine.touchEnded($0, reducedMotion: reduceMotion) }
            )
            .onChange(of: touching) { _, now in
                if !now { engine.touchMaybeCancelled() }
            }
            .accessibilityElement()
            .accessibilityLabel("Firefly swarm")

            HStack(spacing: 10) {
                ForEach(hint.indices, id: \.self) { i in
                    if i > 0 {
                        Circle().fill(Color(hex: 0xe8e2c8)).frame(width: 3, height: 3).opacity(0.3)
                    }
                    Text(hint[i])
                        .font(.system(size: 12, weight: .medium))
                        .kerning(0.2)
                        .foregroundStyle(Color(hex: 0xe8e2c8))
                        .opacity(0.5)
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, insets.bottom + 14)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
    }

    private func draw(in context: inout GraphicsContext, size: CGSize) {
        guard engine.ready, let backdrop = art.backdrop, let halo = art.halo, let core = art.core else { return }
        let now = engine.elapsed
        context.draw(backdrop, in: CGRect(origin: .zero, size: size))

        // Stars, each twinkling a whole number of times per loop.
        let stars = FireflySwarmStars.field
        for i in 0..<M.stars {
            let a = 0.35 + 0.65 * (0.5 + 0.5 * sin(stars.k[i] * M.w0 * now + stars.p[i]))
            let r = stars.size[i]
            let c = CGPoint(x: stars.x[i] * size.width, y: stars.y[i] * size.height)
            context.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
                         with: .color(Color(.sRGB, red: 0.92, green: 0.9, blue: 1, opacity: a * 0.8)))
        }

        // Faint rings show the sync wave rolling out, one per pulse.
        let S = engine.sync
        if S > 0.01 {
            let phase = (M.syncRate * now).truncatingRemainder(dividingBy: 1)
            context.drawLayer { layer in
                // Skia's blur sigma of 4 pt; a SwiftUI blur radius spreads about half as far per unit.
                layer.addFilter(.blur(radius: 8))
                for k in 0..<3 {
                    let r = (phase + Double(k)) * M.waveSpeed
                    let ring = Path(ellipseIn: CGRect(x: engine.syncX - r, y: engine.syncY - r, width: r * 2, height: r * 2))
                    layer.stroke(ring, with: .color(Color(.sRGB, red: 0.85, green: 1, blue: 0.55, opacity: 0.1 * S * (1 - (phase + Double(k)) / 3))), lineWidth: 2)
                }
            }
        }

        let haloImage = context.resolve(halo)
        let coreImage = context.resolve(core)
        drawGrass(0, in: context, size: size, now: now)
        drawFlies(in: context, halo: haloImage, core: coreImage, now: now, near: false)
        drawGrass(1, in: context, size: size, now: now)
        drawGrass(2, in: context, size: size, now: now)
        drawFlies(in: context, halo: haloImage, core: coreImage, now: now, near: true)
    }

    /// One layer of grass: each blade a tapered curve bent by the wind and the swarm.
    private func drawGrass(_ index: Int, in context: GraphicsContext, size: CGSize, now: Double) {
        let layer = M.grass[index]
        let blades = FireflySwarmBlades.layers[index]
        let bend = engine.bend
        let w = size.width
        let y0 = layer.base * size.height
        var path = Path()
        for i in blades.x.indices {
            let x0 = blades.x[i] * w
            let height = blades.h[i]
            let half = blades.w[i] / 2
            // Wind: two gusts that repeat per loop, travelling across the meadow.
            let wind = 0.07 * sin(3 * M.w0 * now - x0 * 0.011 + blades.p[i] * 0.3) + 0.035 * sin(7 * M.w0 * now + blades.p[i])
            let k = min(bend.count - 1, max(0, Int(floor(x0 / M.bucket))))
            let angle = blades.lean[i] + wind + (bend.isEmpty ? 0 : bend[k]) * layer.give
            let tip = CGPoint(x: x0 + sin(angle) * height, y: y0 - cos(angle) * height)
            let midX = x0 + sin(angle * 0.45) * height * 0.55
            let midY = y0 - cos(angle * 0.45) * height * 0.55
            path.move(to: CGPoint(x: x0 - half, y: y0))
            path.addQuadCurve(to: tip, control: CGPoint(x: midX - half * 0.7, y: midY))
            path.addQuadCurve(to: CGPoint(x: x0 + half, y: y0), control: CGPoint(x: midX + half * 0.7, y: midY))
            path.closeSubpath()
        }
        // The bank the blades stand on, then the blades lit faintly from the sky at their tips.
        path.addRect(CGRect(x: -10, y: y0 - 1, width: w + 20, height: size.height - y0 + 12))
        context.fill(path, with: .linearGradient(
            Gradient(stops: [.init(color: Color(hex: layer.tip), location: 0), .init(color: Color(hex: layer.color), location: 0.75)]),
            startPoint: CGPoint(x: 0, y: y0 - layer.high),
            endPoint: CGPoint(x: 0, y: y0)
        ))
    }

    /// Glows: halos and cores of one depth layer, added onto the scene in one pass.
    private func drawFlies(in parent: GraphicsContext, halo: GraphicsContext.ResolvedImage, core: GraphicsContext.ResolvedImage, now: Double, near: Bool) {
        var context = parent
        context.blendMode = .plusLighter
        let t = FireflySwarmTraits.shared
        let x = engine.x
        let y = engine.y
        let drift = engine.drift
        for i in 0..<M.count {
            let depth = t.depth[i]
            if (depth >= 0.85) != near { continue }
            let own = t.blinkP[i] + (t.blinkK[i] / M.loop) * now
            let glow = M.flash(own + drift[i])
            let haloSize = (14 + 62 * glow) * depth
            let coreSize = (4 + 6 * glow) * depth
            let fade = 0.55 + 0.45 * M.clamp01((depth - 0.55) / 0.6)
            let hot = min(1, (0.3 + glow) * fade)
            let warm = min(1, glow * 0.85 * fade)
            // The sprites are already tinted; their brightness is the opacity, which `plusLighter`
            // adds exactly as the colour-modulated atlas does.
            context.opacity = warm
            context.draw(halo, in: CGRect(x: x[i] - haloSize / 2, y: y[i] - haloSize / 2, width: haloSize, height: haloSize))
            context.opacity = hot
            context.draw(core, in: CGRect(x: x[i] - coreSize / 2, y: y[i] - coreSize / 2, width: coreSize, height: coreSize))
        }
    }
}

/// The still pictures: the dusk backdrop (sky, moon, tree lines, meadow floor) and the two glow
/// sprites, drawn once.
final class FireflySwarmArt {
    private typealias M = FireflySwarmMetrics
    private(set) var backdrop: Image?
    private(set) var halo: Image?
    private(set) var core: Image?
    private var size = CGSize.zero

    @MainActor
    func prepare(size: CGSize, scale: CGFloat) {
        guard size.width > 0, size != self.size else { return }
        self.size = size
        let renderer = ImageRenderer(content: Canvas { context, canvasSize in
            Self.drawBackdrop(in: &context, size: canvasSize)
        }.frame(width: size.width, height: size.height))
        renderer.scale = scale
        if let cg = renderer.cgImage { backdrop = Image(decorative: cg, scale: scale) }
        // A soft halo and a hot core, tinted, on clear.
        halo = Self.sprite(color: M.halo, alphas: [1, 0.62, 0.3, 0.1, 0], stops: [0, 0.12, 0.3, 0.55, 1])
        core = Self.sprite(color: M.core, alphas: [1, 0.9, 0.35, 0], stops: [0, 0.22, 0.5, 1])
    }

    private static func sprite(color: [Double], alphas: [CGFloat], stops: [CGFloat]) -> Image? {
        let px = Int(M.sprite * 2)
        guard let ctx = CGContext(data: nil, width: px, height: px, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        var comps: [CGFloat] = []
        for a in alphas { comps += [color[0], color[1], color[2], a].map { CGFloat($0) } }
        guard let g = CGGradient(colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!, colorComponents: comps, locations: stops, count: stops.count)
        else { return nil }
        let c = CGPoint(x: px / 2, y: px / 2)
        ctx.drawRadialGradient(g, startCenter: c, startRadius: 0, endCenter: c, endRadius: CGFloat(px) / 2, options: [])
        guard let cg = ctx.makeImage() else { return nil }
        return Image(decorative: cg, scale: 2)
    }

    /// A tree line along `base`: rounded crowns with now and then a tall pointed conifer.
    private static func treeLine(_ w: Double, _ base: Double, _ scale: Double, _ seed: Int) -> Path {
        let line = FireflySwarmStars.treeLine
        func pick(_ k: Int) -> Double { line[(seed + k) % line.count] }
        var p = Path()
        p.move(to: CGPoint(x: -10, y: base + 12))
        p.addLine(to: CGPoint(x: -10, y: base - 6 * scale))
        var x = -10.0
        var n = 0
        while x < w + 20 {
            if pick(n) < 0.22 {
                // A conifer: narrow, tall, pointed, its sides gently concave.
                let wide = (13 + pick(n + 1) * 10) * scale
                let tall = (24 + pick(n + 2) * 22) * scale
                p.addQuadCurve(to: CGPoint(x: x + wide * 0.5, y: base - tall), control: CGPoint(x: x + wide * 0.42, y: base - tall * 0.45))
                p.addQuadCurve(to: CGPoint(x: x + wide, y: base - 6 * scale), control: CGPoint(x: x + wide * 0.58, y: base - tall * 0.45))
                x += wide
            } else {
                let crown = (12 + pick(n + 1) * 24) * scale
                let top = base - (12 + pick(n + 2) * 22) * scale
                p.addCurve(to: CGPoint(x: x + crown, y: base - (4 + pick(n + 3) * 6) * scale),
                           control1: CGPoint(x: x, y: top), control2: CGPoint(x: x + crown, y: top))
                x += crown * 0.8
            }
            n += 4
        }
        p.addLine(to: CGPoint(x: w + 20, y: base + 12))
        p.closeSubpath()
        return p
    }

    private static func drawBackdrop(in c: inout GraphicsContext, size: CGSize) {
        let w = size.width
        let h = size.height
        c.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x070a1d), location: 0),
                .init(color: Color(hex: 0x121741), location: 0.32),
                .init(color: Color(hex: 0x2b2a5e), location: 0.6),
                .init(color: Color(hex: 0x6b4a76), location: 0.82),
                .init(color: Color(hex: 0xc97a5c), location: 1),
            ]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: h * 0.6)
        ))
        // The moon: a thin crescent with a soft halo.
        let mx = w * 0.78
        let my = h * 0.13
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: 68))
            layer.fill(Path(ellipseIn: CGRect(x: mx - 48, y: my - 48, width: 96, height: 96)), with: .color(.rgba(214, 208, 255, 0.13)))
        }
        let disc = Path(ellipseIn: CGRect(x: mx - 15, y: my - 15, width: 30, height: 30))
        let bite = Path(ellipseIn: CGRect(x: mx + 6.5 - 13.5, y: my - 4 - 13.5, width: 27, height: 27))
        c.fill(disc.subtracting(bite), with: .color(Color(hex: 0xf2ead6)))
        // Two tree lines, the far one paler in the haze, mist lying between them, then the meadow.
        let base = h * 0.6
        c.fill(treeLine(w, base - 12, 0.7, 0), with: .color(Color(hex: 0x3a2f5e)))
        c.fill(Path(CGRect(x: 0, y: base - 40, width: w, height: 50)), with: .linearGradient(
            Gradient(stops: [
                .init(color: .rgba(196, 128, 140, 0), location: 0),
                .init(color: .rgba(176, 118, 146, 0.35), location: 0.7),
                .init(color: .rgba(120, 90, 140, 0.1), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: base - 40), endPoint: CGPoint(x: 0, y: base + 10)
        ))
        c.fill(treeLine(w, base, 1, 97), with: .color(Color(hex: M.meadowTop)))
        c.fill(Path(CGRect(x: 0, y: base + 6, width: w, height: h - base - 6)), with: .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: M.meadowTop), location: 0),
                .init(color: Color(hex: 0x0c0e22), location: 0.45),
                .init(color: Color(hex: 0x04050b), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: base + 6), endPoint: CGPoint(x: 0, y: h)
        ))
    }
}
