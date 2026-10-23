import SwiftUI

/// The harp's still parts: the room, the carved frame, the pins and eyelets, laid out for a screen.
/// Drawn once into its own `Canvas`, under the strings that move.
enum PluckStringHarpPalette {
    // A dark room, warm varnished wood, gilt, and strings that glow like filaments.
    static let room: [UInt32] = [0x22170f, 0x0e0a07, 0x050403]
    static let wood: UInt32 = 0x5b3419
    static let woodLight: UInt32 = 0xc98f55
    static let woodDark: UInt32 = 0x1d0e05
    static let grain: UInt32 = 0x2a1407
    static let gilt: UInt32 = 0xd6ad66
    static let stringIdle: UInt32 = 0xa8804f
    static let stringHot: UInt32 = 0xfff4df
    static let glow: UInt32 = 0xffb257
    static let glowCore: UInt32 = 0xffd49a
    static let noteInk: UInt32 = 0xffe8c4
    static let label: UInt32 = 0xb08a5a
}

/// A band of wood: its centre line, a width at each point, and the unit normal there.
struct PluckStringHarpRibbon {
    var centre: [CGPoint]
    var width: [Double]
    var normal: [CGPoint]
    var path: Path
}

/// Where everything is, and the picture of the frame.
struct PluckStringHarpLayout {
    var geometry: PluckStringHarpGeometry
    var centreX: Double
    var centreY: Double
    var top: Double
    var bottom: Double
    var span: Double
    var neck: PluckStringHarpRibbon
    var box: PluckStringHarpRibbon
    var pillar: PluckStringHarpRibbon
}

enum PluckStringHarpDrawing {
    private typealias C = PluckStringHarpPalette
    // Strings span this share of the width, the lowest on the left.
    static let stringLeft = 0.27
    static let stringRight = 0.8

    static func cubic(_ p0: CGPoint, _ p1: CGPoint, _ p2: CGPoint, _ p3: CGPoint, _ t: Double) -> CGPoint {
        let u = 1 - t
        return CGPoint(
            x: u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
            y: u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y
        )
    }

    static func offset(_ p: CGPoint, _ n: CGPoint, _ d: Double) -> CGPoint {
        CGPoint(x: p.x + n.x * d, y: p.y + n.y * d)
    }

    /// A band along `centre`, `width(t)` wide, with normals turned towards `up` (−1 up, +1 down).
    static func ribbon(_ centre: [CGPoint], width: (Double) -> Double, up: Double) -> PluckStringHarpRibbon {
        let n = centre.count
        var normal: [CGPoint] = []
        var widths: [Double] = []
        for i in 0..<n {
            let a = centre[max(0, i - 1)]
            let b = centre[min(n - 1, i + 1)]
            let dx = b.x - a.x
            let dy = b.y - a.y
            let h = hypot(dx, dy)
            let len = h == 0 ? 1 : h
            var nx = -dy / len
            var ny = dx / len
            if ny * up < 0 || (ny == 0 && nx * up < 0) {
                nx = -nx
                ny = -ny
            }
            normal.append(CGPoint(x: nx, y: ny))
            widths.append(width(Double(i) / Double(n - 1)))
        }
        var path = Path()
        for i in 0..<n {
            let p = offset(centre[i], normal[i], widths[i] / 2)
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        for i in stride(from: n - 1, through: 0, by: -1) {
            path.addLine(to: offset(centre[i], normal[i], -widths[i] / 2))
        }
        path.closeSubpath()
        return PluckStringHarpRibbon(centre: centre, width: widths, normal: normal, path: path)
    }

    /// A line along the band at `f` (−1 one edge … 1 the other), wobbling by `wave` of the width.
    static func alongRibbon(_ r: PluckStringHarpRibbon, _ f: Double, wave: Double = 0, phase: Double = 0, from: Double = 0, to: Double = 1) -> Path {
        var path = Path()
        let n = r.centre.count
        // JavaScript's Math.round: halves go up.
        let first = Int(floor(from * Double(n - 1) + 0.5))
        let last = Int(floor(to * Double(n - 1) + 0.5))
        for i in first...last {
            let t = Double(i) / Double(n - 1)
            let g = f + wave * sin(t * 9 + phase) + wave * 0.6 * sin(t * 23 + phase * 2.3)
            let p = offset(r.centre[i], r.normal[i], (r.width[i] / 2) * g)
            if i == first { path.move(to: p) } else { path.addLine(to: p) }
        }
        return path
    }

    /// y of a polyline at `x` (the polyline runs left to right).
    static func yAt(_ points: [CGPoint], _ x: Double) -> Double {
        for i in 0..<(points.count - 1) {
            let a = points[i]
            let b = points[i + 1]
            if (x >= a.x && x <= b.x) || (x <= a.x && x >= b.x) {
                let t = b.x == a.x ? 0 : (x - a.x) / (b.x - a.x)
                return a.y + (b.y - a.y) * t
            }
        }
        return points[points.count - 1].y
    }

    /// Lays out the harp for a screen of `width` × `height` with the given safe-area insets.
    static func layout(width W: Double, height: Double, insetTop: Double, insetBottom: Double) -> PluckStringHarpLayout {
        let top = insetTop + 62
        let bottom = height - insetBottom - 64
        let span = bottom - top

        // The neck: a swan curve from the top of the pillar down to the shoulder.
        let n0 = CGPoint(x: W * 0.115, y: top + span * 0.08)
        let n1 = CGPoint(x: W * 0.4, y: top - span * 0.07)
        let n2 = CGPoint(x: W * 0.62, y: top + span * 0.33)
        let n3 = CGPoint(x: W * 0.925, y: top + span * 0.35)
        let neckLine = (0...80).map { cubic(n0, n1, n2, n3, Double($0) / 80) }
        let neck = ribbon(neckLine, width: { t in 38 - 14 * t + 7 * sin(t * .pi) }, up: 1)

        // The soundbox: a tapered box rising from the foot to the shoulder.
        let b0 = CGPoint(x: W * 0.185, y: bottom - span * 0.035)
        // The top of the box tucks under the end of the neck, so the two read as one joint.
        let b1 = CGPoint(x: n3.x - 6, y: n3.y + 8)
        let boxLine: [CGPoint] = (0...40).map { i in
            let t = Double(i) / 40
            // A slight belly, so the box is not a ruler.
            let bow = sin(t * .pi) * span * 0.018
            return CGPoint(x: b0.x + (b1.x - b0.x) * t + bow * 0.7, y: b0.y + (b1.y - b0.y) * t + bow)
        }
        let box = ribbon(boxLine, width: { t in 74 - 46 * t }, up: -1)

        // The pillar: a column from the foot up to the neck, swelling at the base and the crown.
        let p0 = CGPoint(x: W * 0.13, y: bottom + 4)
        let p1 = CGPoint(x: W * 0.11, y: top + span * 0.09)
        let pillarLine: [CGPoint] = (0...40).map { i in
            let t = Double(i) / 40
            return CGPoint(x: p0.x + (p1.x - p0.x) * t, y: p0.y + (p1.y - p0.y) * t)
        }
        let pillar = ribbon(pillarLine, width: { t in 22 + 14 * pow(1 - t, 6) + 8 * pow(t, 8) }, up: 1)

        // The strings hang from the underside of the neck to the face of the soundbox.
        let neckUnder = neck.centre.indices.map { offset(neck.centre[$0], neck.normal[$0], neck.width[$0] * 0.32) }
        let boxFace = box.centre.indices.map { offset(box.centre[$0], box.normal[$0], box.width[$0] * 0.34) }
        var xs: [Double] = []
        var tops: [Double] = []
        var bottoms: [Double] = []
        let count = PluckStringHarpPhysics.strings
        for i in 0..<count {
            let x = W * (stringLeft + ((stringRight - stringLeft) * Double(i)) / Double(count - 1))
            xs.append(x)
            tops.append(yAt(neckUnder, x))
            bottoms.append(yAt(boxFace, x))
        }
        return PluckStringHarpLayout(
            geometry: PluckStringHarpGeometry(x: xs, top: tops, bottom: bottoms),
            centreX: (xs[0] + xs[count - 1]) / 2,
            centreY: (tops[0] + bottoms[0]) / 2,
            top: top,
            bottom: bottom,
            span: span,
            neck: neck,
            box: box,
            pillar: pillar
        )
    }

    // MARK: Painting helpers (Skia paints as GraphicsContext calls)

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(hex: hex, opacity: alpha)
    }

    static let round = StrokeStyle(lineWidth: 1, lineCap: .round, lineJoin: .round)

    /// A round-capped stroke; with `blur` > 0 it is blurred like a Skia normal mask blur of that sigma.
    static func stroke(_ ctx: inout GraphicsContext, _ path: Path, _ hex: UInt32, _ width: Double, _ alpha: Double = 1, blur: Double = 0) {
        let style = StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
        if blur > 0 {
            ctx.drawLayer { layer in
                layer.addFilter(.blur(radius: blur))
                layer.stroke(path, with: .color(color(hex, alpha)), style: style)
            }
        } else {
            ctx.stroke(path, with: .color(color(hex, alpha)), style: style)
        }
    }

    static func fill(_ ctx: inout GraphicsContext, _ path: Path, _ hex: UInt32, _ alpha: Double = 1, blur: Double = 0) {
        if blur > 0 {
            ctx.drawLayer { layer in
                layer.addFilter(.blur(radius: blur))
                layer.fill(path, with: .color(color(hex, alpha)))
            }
        } else {
            ctx.fill(path, with: .color(color(hex, alpha)))
        }
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    static func radial(_ colors: [UInt32], _ stops: [Double], center: CGPoint, radius: Double) -> GraphicsContext.Shading {
        .radialGradient(
            Gradient(stops: zip(colors, stops).map { Gradient.Stop(color: color($0.0), location: $0.1) }),
            center: center,
            startRadius: 0,
            endRadius: radius
        )
    }

    // MARK: The frame

    /// One piece of the frame: varnished wood with a lit edge, a shaded edge and grain that follows it.
    static func drawWood(_ ctx: inout GraphicsContext, _ r: PluckStringHarpRibbon, seed: Double) {
        fill(&ctx, r.path, C.wood)
        var inner = ctx
        inner.clip(to: r.path)
        // Shade on the far side, light on the near side: the piece reads as round.
        stroke(&inner, alongRibbon(r, 0.85), C.woodDark, 16, 0.85, blur: 9)
        stroke(&inner, alongRibbon(r, -0.55), C.woodLight, 9, 0.42, blur: 7)
        // Grain: thin lines that drift across the band like the figure of the wood.
        for k in 0..<9 {
            let f = -0.85 + (Double(k) / 8) * 1.7
            stroke(&inner, alongRibbon(r, f, wave: 0.05, phase: seed + Double(k) * 1.7), C.grain, 0.8, 0.32)
        }
        for k in 0..<4 {
            let f = -0.6 + Double(k) * 0.32
            stroke(&inner, alongRibbon(r, f, wave: 0.07, phase: seed * 2 + Double(k)), C.woodLight, 0.6, 0.12)
        }
        // The varnish: a narrow specular line along the lit side.
        stroke(&inner, alongRibbon(r, -0.42), 0xffe2b8, 1.3, 0.38, blur: 1)
        stroke(&ctx, r.path, C.woodDark, 1, 0.7)
    }

    /// A round carved boss with gilt rings, like the scroll at the top of a harp's column.
    static func drawScroll(_ ctx: inout GraphicsContext, _ c: CGPoint, _ radius: Double) {
        let disc = circle(c.x, c.y, radius)
        ctx.fill(disc, with: radial([0xcf9a60, C.wood, C.woodDark], [0, 0.5, 1], center: CGPoint(x: c.x - radius * 0.32, y: c.y - radius * 0.36), radius: radius * 1.25))
        stroke(&ctx, disc, C.woodDark, 1, 0.7)
        stroke(&ctx, circle(c.x, c.y, radius * 0.66), C.gilt, 1.4, 0.9)
        stroke(&ctx, circle(c.x, c.y, radius * 0.36), C.gilt, 1.1, 0.7)
        drawPin(&ctx, c.x, c.y, max(2.4, radius * 0.17))
    }

    /// A brass tuning pin or bridge pin, lit from the top left.
    static func drawPin(_ ctx: inout GraphicsContext, _ x: Double, _ y: Double, _ radius: Double) {
        fill(&ctx, circle(x + 1.2, y + 2, radius + 0.6), 0x000000, 0.55, blur: 2)
        ctx.fill(circle(x, y, radius), with: radial([0xfff3cf, 0xd9a957, 0x5e3d14], [0, 0.45, 1], center: CGPoint(x: x - radius * 0.35, y: y - radius * 0.4), radius: radius * 1.4))
    }

    /// Paints everything that never moves: the room, the frame's shadow, the wood, the pins.
    static func drawFrame(_ ctx: inout GraphicsContext, _ l: PluckStringHarpLayout, size: CGSize) {
        let W = size.width
        let top = l.top
        let bottom = l.bottom
        let span = l.span
        ctx.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: radial(C.room, [0, 0.5, 1], center: CGPoint(x: W * 0.52, y: top + span * 0.42), radius: max(size.width, size.height) * 0.78)
        )

        // One soft shadow for the whole frame, cast down and to the right.
        var shadow = ctx
        shadow.translateBy(x: 10, y: 18)
        var boxShadow = shadow
        boxShadow.clip(to: Path(CGRect(x: -20, y: -20, width: W + 40, height: bottom - 20)))
        fill(&boxShadow, l.box.path, 0x000000, 0.7, blur: 16)
        fill(&shadow, l.pillar.path, 0x000000, 0.7, blur: 16)
        fill(&shadow, l.neck.path, 0x000000, 0.7, blur: 16)

        // The box is cut flat where it stands on the plinth.
        var boxCtx = ctx
        boxCtx.clip(to: Path(CGRect(x: 0, y: 0, width: W, height: bottom - 2)))
        drawWood(&boxCtx, l.box, seed: 1.3)
        // Gilt along the soundboard, where the strings land.
        stroke(&boxCtx, alongRibbon(l.box, 0.62, from: 0.04, to: 0.97), C.gilt, 1.3, 0.85)
        stroke(&boxCtx, alongRibbon(l.box, -0.7, from: 0.05, to: 0.95), C.gilt, 0.8, 0.35)
        drawWood(&ctx, l.pillar, seed: 4.1)
        // Gilt rings on the column.
        for t in [0.07, 0.12, 0.9] {
            let i = Int(floor(t * Double(l.pillar.centre.count - 1) + 0.5))
            let c = l.pillar.centre[i]
            let half = l.pillar.width[i] / 2 + 0.5
            stroke(&ctx, line(c.x - half, c.y, c.x + half, c.y), C.gilt, 2, 0.9)
        }
        drawWood(&ctx, l.neck, seed: 7.7)
        stroke(&ctx, alongRibbon(l.neck, -0.1, from: 0.06, to: 0.96), C.gilt, 1.1, 0.8)

        // Carved scrolls where the neck meets the column and the shoulder.
        drawScroll(&ctx, l.neck.centre[0], 22)
        drawScroll(&ctx, l.neck.centre[l.neck.centre.count - 1], 14)

        // The plinth the column and the box stand on.
        let foot = Path(roundedRect: CGRect(x: W * 0.045, y: bottom - 4, width: W * 0.3, height: 20), cornerSize: CGSize(width: 6, height: 6))
        ctx.fill(
            foot,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: color(0x9a6434), location: 0),
                    .init(color: color(C.wood), location: 0.45),
                    .init(color: color(C.woodDark), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: bottom - 4),
                endPoint: CGPoint(x: 0, y: bottom + 16)
            )
        )
        stroke(&ctx, line(W * 0.045 + 6, bottom - 2.5, W * 0.345 - 6, bottom - 2.5), C.gilt, 1.2, 0.8)

        let g = l.geometry
        for i in 0..<PluckStringHarpPhysics.strings {
            drawPin(&ctx, g.x[i], g.top[i] - 1, 3.6)
            // The eyelet in the soundboard.
            let eyelet = circle(g.x[i], g.bottom[i] + 1, 3.4)
            ctx.fill(eyelet, with: .color(color(0x120904)))
            stroke(&ctx, eyelet, C.gilt, 1, 0.75)
        }
    }
}
