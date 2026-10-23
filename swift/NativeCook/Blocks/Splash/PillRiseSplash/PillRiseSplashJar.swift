import SwiftUI
import UIKit

/// One jar of the onboarding scene, in pt for a 402 pt wide screen (scaled by the block).
struct PillRiseSplashJarSpec {
    /// Unsplash photo id painted onto the glaze.
    var photo: String
    /// Centre of the jar's mouth, from the middle of the jar stage.
    var x: Double
    var y: Double
    /// Widest part of the body, its height from the rim to the foot, and the mouth's width.
    var body: Double
    var height: Double
    var mouth: Double
    /// Resting tilt, in radians.
    var tilt: Double
    /// Seconds per bob, and where in the bob it starts.
    var period: Double
    var phase: Double
}

/// The jar's outline in its own space: the origin is the centre of the mouth. The body's top edge is
/// the near half of the opening, so whatever is drawn before the body (a coin) disappears into it.
struct PillRiseSplashJarGeometry {
    var body: Path
    var rimFront: Path
    var lipFront: Path
    var back: CGRect
    var opening: CGRect
    var bounds: CGRect
    /// Half the body's width and its height, in pt.
    var half: Double
    var height: Double
}

/// A jar placed on screen: the centre of its mouth at rest, the middle of its body and the fall a
/// coin makes from the coins' line to its rim.
struct PillRiseSplashPlacedJar {
    var spec: PillRiseSplashJarSpec
    var geometry: PillRiseSplashJarGeometry
    var x: Double
    var y: Double
    var drop: Double
    var mid: Double { y + geometry.height / 2 }
}

enum PillRiseSplashJars {
    // Three glazed jars, back to front, placed around the middle of the jar stage.
    static let specs: [PillRiseSplashJarSpec] = [
        .init(photo: "photo-1541961017774-22349e4a1262", x: 8, y: -150, body: 82, height: 120, mouth: 48, tilt: 0.1, period: 3.1, phase: 0.4),
        .init(photo: "photo-1580566176138-daa588058b59", x: 96, y: -58, body: 96, height: 142, mouth: 58, tilt: 0.17, period: 3.6, phase: 2.1),
        .init(photo: "photo-1536849460588-696219a9e98d", x: -92, y: -30, body: 116, height: 168, mouth: 66, tilt: -0.21, period: 3.3, phase: 4.0),
    ]

    static let coinSlots = 5
    // A coin hovers, spinning, for `coinHover` s while it sinks `coinSink` pt, then falls with gravity.
    static let coinHover = 0.32
    static let coinSink = 8.0
    static let coinGravity = 1900.0
    static let coinRadius = 12.0
    // Thickness of the coin's milled edge, seen as it turns.
    static let coinEdge = 2.4
    // Spin while hovering and the extra spin a fall adds, in rad/s.
    static let spin = 7.0
    static let spinFall = 9.0

    // The lip stands this far proud of the opening; the opening is seen from above, squashed.
    static let lip = 4.0
    static let rimSquash = 0.34
    // The glaze photo is drawn this many times larger than the jar.
    static let glazeZoom = 1.7

    // A landing coin dips the jar by up to `dip` pt, rings at `dipFreq` rad/s, dies away at `dipDecay` /s.
    static let dip = 7.0
    static let dipFreq = 19.0
    static let dipDecay = 6.0
    // Gentle float: `bob` pt up and down, `wobble` rad of sway.
    static let bob = 6.0
    static let wobble = 0.025
    // The jars come in over this share of the board's entrance, one after another.
    static let appearFrom = 0.2
    static let appearSpan = 0.4
    static let appearLag = 0.09
    // Dragging tilts a jar by its sideways speed, up to `tiltMax` rad.
    static let tiltPerSpeed = 0.00035
    static let tiltMax = 0.4

    private static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    private static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))

    private static func seg(_ v: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (v - a) / (b - a))) }

    /// Seconds from a coin's appearance to the moment it drops through the rim of a jar `drop` pt below.
    static func landingTime(_ drop: Double) -> Double {
        coinHover + sqrt(2 * max(0, drop - coinSink) / coinGravity)
    }

    static func place(layout: PillRiseSplashLayout) -> [PillRiseSplashPlacedJar] {
        let k = layout.k
        return specs.map { spec in
            let x = layout.width / 2 + spec.x * k
            let y = layout.stageMid + spec.y * k
            return PillRiseSplashPlacedJar(
                spec: spec, geometry: geometry(spec, k: k), x: x, y: y,
                drop: y - (layout.stageTop + PillRiseSplashMetrics.coinLine)
            )
        }
    }

    /// Appends an arc of the ellipse in `rect`, Skia style: angles in degrees, clockwise on screen.
    private static func arc(_ path: inout Path, _ rect: CGRect, start: Double, sweep: Double, move: Bool) {
        let steps = max(8, Int(abs(sweep) / 4))
        for i in 0...steps {
            let a = (start + sweep * Double(i) / Double(steps)) * .pi / 180
            let p = CGPoint(x: rect.midX + rect.width / 2 * cos(a), y: rect.midY + rect.height / 2 * sin(a))
            if i == 0 && move { path.move(to: p) } else { path.addLine(to: p) }
        }
    }

    static func geometry(_ spec: PillRiseSplashJarSpec, k: Double) -> PillRiseSplashJarGeometry {
        let rin = spec.mouth / 2 * k
        let rout = rin + lip * k
        let ry = rin * rimSquash
        let ryo = rout * rimSquash
        let bw = spec.body / 2 * k
        let h = spec.height * k
        let neck = rout * 0.9
        let neckY = h * 0.14
        let collar = 7 * k
        let opening = CGRect(x: -rin, y: -ry, width: rin * 2, height: ry * 2)
        let back = CGRect(x: -rout, y: -ryo, width: rout * 2, height: ryo * 2)

        var body = Path()
        body.move(to: CGPoint(x: -rout, y: 0))
        body.addLine(to: CGPoint(x: -rin, y: 0))
        // From the left of the opening round its near side to the right.
        arc(&body, opening, start: 180, sweep: -180, move: false)
        body.addLine(to: CGPoint(x: rout, y: 0))
        body.addLine(to: CGPoint(x: rout, y: collar))
        body.addLine(to: CGPoint(x: neck, y: collar + 3 * k))
        body.addLine(to: CGPoint(x: neck, y: neckY))
        body.addCurve(to: CGPoint(x: bw, y: neckY + h * 0.24), control1: CGPoint(x: neck, y: neckY + h * 0.1), control2: CGPoint(x: bw, y: neckY + h * 0.04))
        body.addCurve(to: CGPoint(x: bw * 0.84, y: h - 4 * k), control1: CGPoint(x: bw * 1.03, y: h * 0.62), control2: CGPoint(x: bw, y: h * 0.86))
        body.addQuadCurve(to: CGPoint(x: -bw * 0.84, y: h - 4 * k), control: CGPoint(x: 0, y: h + ry * 1.5))
        body.addCurve(to: CGPoint(x: -bw, y: neckY + h * 0.24), control1: CGPoint(x: -bw, y: h * 0.86), control2: CGPoint(x: -bw * 1.03, y: h * 0.62))
        body.addCurve(to: CGPoint(x: -neck, y: neckY), control1: CGPoint(x: -bw, y: neckY + h * 0.04), control2: CGPoint(x: -neck, y: neckY + h * 0.1))
        body.addLine(to: CGPoint(x: -neck, y: collar + 3 * k))
        body.addLine(to: CGPoint(x: -rout, y: collar))
        body.closeSubpath()

        var rimFront = Path()
        arc(&rimFront, opening, start: 180, sweep: -180, move: true)
        var lipFront = Path()
        arc(&lipFront, back, start: 172, sweep: -164, move: true)

        return PillRiseSplashJarGeometry(
            body: body, rimFront: rimFront, lipFront: lipFront, back: back, opening: opening,
            bounds: CGRect(x: -bw * 1.05, y: -ryo, width: bw * 2.1, height: h + ryo + ry * 1.5),
            half: bw, height: h
        )
    }

    // MARK: - Drawing

    /// A glazed jar floating in the dark: the photo is its glaze, shaded into a body by a dark edge on
    /// both sides, a soft streak of light down its left and a lit rim. Coins fall into its mouth.
    static func draw(_ jar: PillRiseSplashPlacedJar, index: Int, image: Image?, frame f: PillRiseSplashFrame, in context: GraphicsContext) {
        let spec = jar.spec
        let g = jar.geometry
        let t = f.time
        let reduced = f.reducedMotion
        let from = appearFrom + Double(index) * appearLag
        let appear = easeOut.value(at: seg(f.board, from, from + appearSpan))
        let opacity = seg(f.board, from, from + appearSpan * 0.6)
        guard opacity > 0 else { return }
        let bobY = reduced ? 0 : bob * sin(.pi * 2 * t / spec.period + spec.phase)
        let sway = reduced ? 0 : wobble * sin(.pi * 2 * t / (spec.period * 1.3) + spec.phase * 2)
        let since = t - f.landed[index]
        let dipY = reduced || since < 0 || since > 1.4 ? 0 : dip * exp(-since * dipDecay) * sin(since * dipFreq)
        let tilt = max(-tiltMax, min(tiltMax, f.speedX[index] * tiltPerSpeed))
        let rise = reduced ? 0 : (1 - appear) * 46
        let grow = reduced ? 1 : 0.84 + 0.16 * appear

        var ctx = context
        // Turned and scaled about the middle of the jar, not its mouth.
        ctx.translateBy(x: jar.x + f.offsetX[index], y: jar.y + f.offsetY[index] + bobY + dipY + rise + g.height / 2)
        ctx.rotate(by: .radians(spec.tilt + sway + tilt))
        ctx.scaleBy(x: grow * (1 + dipY * 0.005), y: grow * (1 - dipY * 0.008))
        ctx.translateBy(x: 0, y: -g.height / 2)

        if opacity < 1 {
            ctx.opacity = opacity
            ctx.drawLayer { layer in drawJar(jar, index: index, image: image, frame: f, in: layer) }
        } else {
            drawJar(jar, index: index, image: image, frame: f, in: ctx)
        }
    }

    /// The photo, cropped to cover `rect`.
    private static func cover(_ size: CGSize, _ rect: CGRect) -> CGRect {
        let scale = max(rect.width / size.width, rect.height / size.height)
        let w = size.width * scale
        let h = size.height * scale
        return CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h)
    }

    private static func drawJar(_ jar: PillRiseSplashPlacedJar, index: Int, image: Image?, frame f: PillRiseSplashFrame, in ctx: GraphicsContext) {
        let g = jar.geometry
        let b = g.bounds
        // The photo is spread over `glazeZoom` times the jar, so its strokes read as runs of glaze.
        let glazeRect = CGRect(
            x: b.minX - b.width * (glazeZoom - 1) / 2,
            y: b.minY - b.height * (glazeZoom - 1) / 2,
            width: b.width * glazeZoom,
            height: b.height * glazeZoom
        )
        let resolved = image.map { ctx.resolve($0) }
        func glaze(_ shape: Path, in c: GraphicsContext) {
            if let resolved {
                var clipped = c
                clipped.clip(to: shape)
                clipped.draw(resolved, in: cover(resolved.size, glazeRect))
            } else {
                c.fill(shape, with: .linearGradient(
                    Gradient(colors: [Color(hex: 0xc2352b), Color(hex: 0xf0a63a), Color(hex: 0x2f8fd6)]),
                    startPoint: CGPoint(x: b.minX, y: 0), endPoint: CGPoint(x: b.maxX, y: 0)
                ))
            }
        }

        // A bloom of the jar's own colours behind it: the glaze, seen through a blurred silhouette of
        // the jar (Skia's mask blur softens the shape, not the photo).
        var bloom = ctx
        bloom.opacity = 0.22
        bloom.drawLayer { layer in
            var mask = layer
            mask.addFilter(.blur(radius: 30))
            mask.fill(g.body, with: .color(.white))
            var paint = layer
            paint.blendMode = .sourceIn
            if let resolved {
                paint.draw(resolved, in: cover(resolved.size, glazeRect))
            } else {
                glaze(Path(glazeRect), in: paint)
            }
        }

        // The far side of the lip, then the dark inside of the jar.
        let back = Path(ellipseIn: g.back)
        glaze(back, in: ctx)
        ctx.fill(back, with: .color(.white.opacity(0.16)))
        ctx.fill(Path(ellipseIn: g.opening), with: .linearGradient(
            Gradient(colors: [Color(hex: 0x040405), Color(hex: 0x2b2420)]),
            startPoint: CGPoint(x: 0, y: g.opening.minY), endPoint: CGPoint(x: 0, y: g.opening.maxY)
        ))
        for slot in 0..<coinSlots {
            drawCoin(slot: slot, jar: index, drop: jar.drop, frame: f, in: ctx)
        }
        glaze(g.body, in: ctx)

        var inside = ctx
        inside.clip(to: g.body)
        // Round the body: dark at both edges, a little light left of centre.
        inside.fill(g.body, with: .linearGradient(
            Gradient(stops: [
                .init(color: .rgba(6, 4, 12, 0.85), location: 0),
                .init(color: .rgba(6, 4, 12, 0.35), location: 0.1),
                .init(color: .rgba(255, 255, 255, 0.14), location: 0.32),
                .init(color: .rgba(255, 255, 255, 0), location: 0.6),
                .init(color: .rgba(6, 4, 12, 0.45), location: 0.86),
                .init(color: .rgba(6, 4, 12, 0.9), location: 1),
            ]),
            startPoint: CGPoint(x: b.minX, y: 0), endPoint: CGPoint(x: b.maxX, y: 0)
        ))
        // Shadow under the lip and towards the foot.
        inside.fill(g.body, with: .linearGradient(
            Gradient(stops: [
                .init(color: .black.opacity(0.42), location: 0),
                .init(color: .black.opacity(0), location: 0.16),
                .init(color: .black.opacity(0), location: 0.7),
                .init(color: .black.opacity(0.38), location: 1),
            ]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: g.height)
        ))
        // The glossy streak and the hot spot on the shoulder, then a cooler rim light on the right.
        let half = g.half
        let h = g.height
        var streak = inside
        streak.addFilter(.blur(radius: 4))
        streak.fill(
            Path(roundedRect: CGRect(x: -half * 0.6, y: h * 0.27, width: half * 0.17, height: h * 0.5), cornerRadius: half * 0.085),
            with: .color(.white.opacity(0.5))
        )
        var spot = inside
        spot.addFilter(.blur(radius: 2.5))
        spot.fill(Path(ellipseIn: CGRect(x: -half * 0.42 - 4.5, y: h * 0.25 - 4.5, width: 9, height: 9)), with: .color(.white.opacity(0.85)))
        var rim = inside
        rim.addFilter(.blur(radius: 3))
        rim.fill(
            Path(roundedRect: CGRect(x: half * 0.74, y: h * 0.3, width: half * 0.1, height: h * 0.46), cornerRadius: half * 0.05),
            with: .color(.rgba(200, 225, 255, 0.3))
        )

        ctx.stroke(g.lipFront, with: .color(.black.opacity(0.3)), lineWidth: 1.2)
        ctx.stroke(g.rimFront, with: .color(.white.opacity(0.55)), lineWidth: 1.4)
    }

    /// A gold coin that spins above a jar, then drops into it. Drawn in the jar's space.
    private static func drawCoin(slot: Int, jar: Int, drop: Double, frame f: PillRiseSplashFrame, in context: GraphicsContext) {
        let coin = f.coins[slot]
        let t = f.time - coin.t0
        if coin.jar != jar || t < 0 || t > 2 { return }
        var x = 0.0, y = 0.0, spinAngle = 0.0, alpha = 0.0, scale = 1.0
        if f.reducedMotion {
            // No fall: the coin fades in at the mouth and fades away into it.
            y = -14
            alpha = seg(t, 0, 0.15) * (1 - seg(t, 0.45, 0.7))
        } else {
            let land = landingTime(drop) - coinHover
            let tf = max(0, t - coinHover)
            y = t < coinHover
                ? -drop + coinSink * easeOut.value(at: t / coinHover)
                : -drop + coinSink + 0.5 * coinGravity * tf * tf
            // Fully swallowed once it is a coin's height inside the rim.
            alpha = seg(t, 0, 0.14) * (y > coinRadius * 3 ? 0 : 1)
            x = coin.dx * (1 - easeInOut.value(at: min(1, tf / max(land, 0.01))))
            spinAngle = spin * t + spinFall * tf
            scale = 0.7 + 0.3 * easeOut.value(at: seg(t, 0, 0.25))
        }
        guard alpha > 0 else { return }

        var ctx = context
        ctx.translateBy(x: x, y: y)
        ctx.scaleBy(x: scale, y: scale)
        ctx.opacity = alpha
        ctx.drawLayer { c in
            // The coin turns about its horizontal axis: its face flattens, and its edge shows under it.
            let flat = max(0.07, abs(cos(spinAngle)))
            let r = coinRadius
            let disc = Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2))
            var edge = c
            edge.translateBy(x: 0, y: coinEdge * abs(sin(spinAngle)))
            edge.scaleBy(x: 1, y: flat)
            edge.fill(disc, with: .color(Color(hex: 0x94600f)))
            var face = c
            face.scaleBy(x: 1, y: flat)
            face.fill(disc, with: .linearGradient(
                Gradient(colors: [Color(hex: 0xfff4bf), Color(hex: 0xf5c64f), Color(hex: 0xc58a1e)]),
                startPoint: CGPoint(x: -r, y: -r), endPoint: CGPoint(x: r, y: r)
            ))
            let ring = r * 0.68
            face.stroke(Path(ellipseIn: CGRect(x: -ring, y: -ring, width: ring * 2, height: ring * 2)),
                        with: .color(Color(hex: 0xc48a1d, opacity: 0.85)), lineWidth: 1.3)
            let glint = r * 0.22
            face.fill(Path(ellipseIn: CGRect(x: -r * 0.3 - glint, y: -r * 0.35 - glint, width: glint * 2, height: glint * 2)),
                      with: .color(Color(hex: 0xfffbe6, opacity: 0.8)))
        }
    }
}
