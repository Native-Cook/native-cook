import CoreText
import SwiftUI

// MARK: - Projected drawing

/// Draws a flat face through its perspective matrix. GraphicsContext only takes affine transforms,
/// so the face's outlines are mapped point by point (curves flattened first), and round parts small
/// enough for the perspective inside them not to show are drawn through the face's local affine
/// approximation at their centre.
struct OrbitPhotoRingSurface {
    let m: [Double]

    func point(_ u: Double, _ v: Double) -> CGPoint {
        let w = m[6] * u + m[7] * v + 1
        return CGPoint(x: (m[0] * u + m[1] * v + m[2]) / w, y: (m[3] * u + m[4] * v + m[5]) / w)
    }

    func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        var path = Path()
        path.move(to: point(x, y))
        path.addLine(to: point(x + w, y))
        path.addLine(to: point(x + w, y + h))
        path.addLine(to: point(x, y + h))
        path.closeSubpath()
        return path
    }

    /// Any outline of the face's drawing, flattened and mapped through the perspective.
    func path(_ local: Path, threshold: CGFloat = 0.3) -> Path {
        var out = Path()
        local.cgPath.flattened(threshold: threshold).applyWithBlock { element in
            let e = element.pointee
            switch e.type {
            case .moveToPoint:
                out.move(to: point(e.points[0].x, e.points[0].y))
            case .addLineToPoint:
                out.addLine(to: point(e.points[0].x, e.points[0].y))
            case .closeSubpath:
                out.closeSubpath()
            default:
                break
            }
        }
        return out
    }

    /// The affine map that matches the perspective at (u, v) to first order.
    func affine(at u: Double = 0, _ v: Double = 0) -> CGAffineTransform {
        let x = m[0] * u + m[1] * v + m[2]
        let y = m[3] * u + m[4] * v + m[5]
        let w = m[6] * u + m[7] * v + 1
        let a = (m[0] * w - x * m[6]) / (w * w)
        let c = (m[1] * w - x * m[7]) / (w * w)
        let b = (m[3] * w - y * m[6]) / (w * w)
        let d = (m[4] * w - y * m[7]) / (w * w)
        let sx = x / w
        let sy = y / w
        return CGAffineTransform(a: a, b: b, c: c, d: d, tx: sx - a * u - c * v, ty: sy - b * u - d * v)
    }

    /// The matrix that maps screen points back onto the face, rows of a 3×3.
    var inverse: [Double] {
        let a = m
        let c00 = a[4] * a[8] - a[5] * a[7]
        let c01 = a[2] * a[7] - a[1] * a[8]
        let c02 = a[1] * a[5] - a[2] * a[4]
        let c10 = a[5] * a[6] - a[3] * a[8]
        let c11 = a[0] * a[8] - a[2] * a[6]
        let c12 = a[2] * a[3] - a[0] * a[5]
        let c20 = a[3] * a[7] - a[4] * a[6]
        let c21 = a[1] * a[6] - a[0] * a[7]
        let c22 = a[0] * a[4] - a[1] * a[3]
        let det = a[0] * c00 + a[1] * c10 + a[2] * c20
        let k = abs(det) > 1e-12 ? 1 / det : 0
        return [c00, c01, c02, c10, c11, c12, c20, c21, c22].map { $0 * k }
    }
}

// MARK: - Type

/// Letters as outlines, measured one by one like Skia's `measureText`, so tracked text sets the
/// same way as in the React Native version and can be drawn on a face in perspective.
@MainActor
enum OrbitPhotoRingType {
    static let name = CTFontCreateWithName("AvenirNext-Heavy" as CFString, 25, nil)
    static let spec = CTFontCreateWithName("Menlo-Regular" as CFString, 10, nil)
    static let label = CTFontCreateWithName("Menlo-Regular" as CFString, 9, nil)
    static let caption = CTFontCreateWithName("Menlo-Regular" as CFString, 6.5, nil)
    static let engrave = CTFontCreateWithName("AvenirNext-Bold" as CFString, 10, nil)

    private struct Key: Hashable {
        let font: String
        let size: CGFloat
        let char: Character
    }

    private static var glyphs: [Key: (path: Path, advance: CGFloat, measured: CGFloat)] = [:]

    /// One letter: its outline with the baseline at y = 0, its advance, and its width as Skia's
    /// `measureText` gives it for a lone letter: the glyph's ink bounds rounded out to whole points
    /// plus a point of padding on each side (which is what spaces the tracked labels so widely).
    static func letter(_ char: Character, _ font: CTFont) -> (path: Path, advance: CGFloat, measured: CGFloat) {
        let key = Key(font: CTFontCopyPostScriptName(font) as String, size: CTFontGetSize(font), char: char)
        if let hit = glyphs[key] { return hit }
        let units = Array(String(char).utf16)
        var ids = [CGGlyph](repeating: 0, count: units.count)
        CTFontGetGlyphsForCharacters(font, units, &ids, units.count)
        var advance = CGSize.zero
        CTFontGetAdvancesForGlyphs(font, .horizontal, &ids, &advance, 1)
        var ink = CGRect.zero
        CTFontGetBoundingRectsForGlyphs(font, .horizontal, &ids, &ink, 1)
        let measured = ink.isEmpty ? 0 : ceil(ink.maxX) - floor(ink.minX) + 2
        var flip = CGAffineTransform(scaleX: 1, y: -1)
        let path = CTFontCreatePathForGlyph(font, ids[0], &flip).map { Path($0) } ?? Path()
        let entry = (path, advance.width, measured)
        glyphs[key] = entry
        return entry
    }

    /// The width of one letter as the React Native version measures it: Skia measures a lone space
    /// as nothing, so a space is given a third of the size.
    static func width(_ char: Character, _ font: CTFont) -> CGFloat {
        char == " " ? CTFontGetSize(font) * 0.34 : letter(char, font).measured
    }

    /// Text set on its own advances, as Skia's `drawText` sets a whole string, baseline at (x, y).
    static func plain(_ text: String, _ font: CTFont, x: CGFloat, y: CGFloat) -> Path {
        var at = x
        var out = Path()
        for c in text {
            let glyph = letter(c, font)
            out.addPath(glyph.path, transform: CGAffineTransform(translationX: at, y: y))
            at += glyph.advance
        }
        return out
    }

    /// Text with extra space between letters, its baseline at `y`, set around `x` by `align`
    /// (0 left, 0.5 middle, 1 right), as one outline.
    static func tracked(_ text: String, _ font: CTFont, x: CGFloat, y: CGFloat, tracking: CGFloat, align: CGFloat) -> Path {
        let chars = Array(text)
        var total: CGFloat = 0
        for (i, c) in chars.enumerated() { total += width(c, font) + (i < chars.count - 1 ? tracking : 0) }
        var at = x - total * align
        var out = Path()
        for c in chars {
            if c != " " { out.addPath(letter(c, font).path, transform: CGAffineTransform(translationX: at, y: y)) }
            at += width(c, font) + tracking
        }
        return out
    }
}

// MARK: - The camera

/// Draws a camera body the way the React Native version records it into a Skia picture: every face
/// through its own perspective, lit from the upper left, the lens from slices of a cylinder.
@MainActor
enum OrbitPhotoRingPainter {
    private typealias V = OrbitPhotoRingVector
    private typealias P = OrbitPhotoRingMath

    static func color(_ v: [Double], _ i: Int, _ k: Double, _ a: Double = 1) -> Color {
        Color(.sRGB, red: P.clamp01(v[i] * k), green: P.clamp01(v[i + 1] * k), blue: P.clamp01(v[i + 2] * k), opacity: a)
    }

    /// Brushed chrome (or black paint) along a face's vertical: bright, dark, bright, dark.
    private static func metal(_ v: [Double], _ s: OrbitPhotoRingSurface, _ y0: Double, _ y1: Double) -> GraphicsContext.Shading {
        let stops = Gradient(stops: [
            .init(color: color(v, V.metal, 1.3), location: 0),
            .init(color: color(v, V.metal, 0.9), location: 0.28),
            .init(color: color(v, V.metal, 1.12), location: 0.55),
            .init(color: color(v, V.metal, 0.6), location: 0.82),
            .init(color: color(v, V.metal, 0.85), location: 1),
        ])
        return .linearGradient(stops, startPoint: s.point(0, y0), endPoint: s.point(0, y1))
    }

    private static func shadeRect(_ ctx: GraphicsContext, _ s: OrbitPhotoRingSurface, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ light: Double) {
        ctx.fill(s.rect(x, y, w, h), with: .color(.black.opacity((1 - light) * 0.72)))
    }

    /// A face of the body: leather between a metal top plate and a thin metal foot, or plain metal
    /// on the top and bottom. The front also carries the finder windows, the accent dot and the name.
    private static func paintBodyFace(_ ctx: GraphicsContext, _ s: OrbitPhotoRingSurface, _ v: [Double], kind: Int, w: Double, h: Double, light: Double, detail: Bool, label: String) {
        let x0 = -w / 2
        let y0 = -h / 2
        if kind >= 4 {
            // Top or bottom: a metal plate, its gradient running front to back.
            ctx.fill(s.rect(x0, y0, w, h), with: metal(v, s, y0, y0 + h))
            shadeRect(ctx, s, x0, y0, w, h, light)
            return
        }
        let plate = h * v[V.plate]
        let face = s.rect(x0, y0, w, h)
        if detail {
            let inv = s.inverse
            let leather = ShaderLibrary.orbitPhotoRingLeather(
                .float3(inv[0], inv[1], inv[2]),
                .float3(inv[3], inv[4], inv[5]),
                .float3(inv[6], inv[7], inv[8]),
                .float3(v[V.leather], v[V.leather + 1], v[V.leather + 2]),
                .float(OrbitPhotoRingMetrics.leatherGrain)
            )
            ctx.fill(face, with: .shader(leather))
        } else {
            ctx.fill(face, with: .color(color(v, V.leather, 1)))
        }
        ctx.fill(s.rect(x0, y0, w, plate), with: metal(v, s, y0, y0 + plate))
        ctx.fill(s.rect(x0, y0 + h - 7, w, 7), with: metal(v, s, y0 + h - 7, y0 + h))
        // A dark seam where the leather meets the metal.
        ctx.fill(s.rect(x0, y0 + plate, w, 1.2), with: .color(.black.opacity(0.45)))
        ctx.fill(s.rect(x0, y0 + h - 8, w, 1), with: .color(.black.opacity(0.45)))

        if kind == 1 && detail {
            // The back: the film door's outline, the eyepiece on the top plate and a film window.
            let door = Path(roundedRect: CGRect(x: x0 + 12, y: y0 + plate + 7, width: w - 24, height: h - plate - 22), cornerSize: CGSize(width: 5, height: 5), style: .circular)
            ctx.fill(s.path(door.strokedPath(StrokeStyle(lineWidth: 1.2))), with: .color(.black.opacity(0.4)))
            let ex = w * 0.22
            let eye = Path(roundedRect: CGRect(x: ex - 15, y: y0 + plate * 0.18, width: 30, height: plate * 0.64), cornerSize: CGSize(width: 4, height: 4), style: .circular)
            ctx.fill(s.path(eye), with: .color(Color(orbitHex: 0x0e0e10)))
            let eyeGlass = Path(roundedRect: CGRect(x: ex - 9, y: y0 + plate * 0.3, width: 18, height: plate * 0.4), cornerSize: CGSize(width: 2.5, height: 2.5), style: .circular)
            ctx.fill(s.path(eyeGlass), with: .color(color(v, V.glass, 1.2)))
            let fx = -w * 0.18
            let window = Path(roundedRect: CGRect(x: fx - 20, y: y0 + plate + 18, width: 40, height: 22), cornerSize: CGSize(width: 3, height: 3), style: .circular)
            ctx.fill(s.path(window), with: metal(v, s, y0 + plate + 18, y0 + plate + 40))
            ctx.fill(s.rect(fx - 15, y0 + plate + 23, 30, 12), with: .color(color(v, V.accent, 0.9, 0.85)))
        }
        if kind == 0 && detail {
            // Two finder windows on a rangefinder, fading in and out with the morph.
            let windows = v[V.windows]
            if windows > 0.02 {
                for (cx, ww) in [(-w * 0.34, 34.0), (w * 0.3, 22.0)] {
                    let r = Path(roundedRect: CGRect(x: cx - ww / 2, y: y0 + plate * 0.24, width: ww, height: plate * 0.52), cornerSize: CGSize(width: 3, height: 3), style: .circular)
                    let shape = s.path(r)
                    let glass = Gradient(stops: [
                        .init(color: color(v, V.glass, 1.8, windows), location: 0),
                        .init(color: color(v, V.glass, 0.4, windows), location: 0.6),
                        .init(color: color(v, V.glass, 1.1, windows), location: 1),
                    ])
                    ctx.fill(shape, with: .linearGradient(glass, startPoint: s.point(cx - ww / 2, y0), endPoint: s.point(cx + ww / 2, y0 + plate)))
                    // A 1.4 pt rim, stroked on the face and mapped as an outline.
                    let rim = r.strokedPath(StrokeStyle(lineWidth: 1.4))
                    ctx.fill(s.path(rim), with: .color(.black.opacity(0.5 * windows)))
                }
            }
            // The accent dot and the engraved name on the top plate.
            let dotX = -w * 0.4
            let dotY = y0 + plate + (h - plate) * 0.22
            ctx.fill(s.path(circle(dotX, dotY, 4.2)), with: .color(color(v, V.accent, 1)))
            ctx.fill(s.path(circle(dotX - 1.2, dotY - 1.2, 1.3)), with: .color(.white.opacity(0.45)))
            let light = v[V.metal] + v[V.metal + 1] + v[V.metal + 2] > 1.5
            let ink = light ? Color(.sRGB, red: 0.1, green: 0.1, blue: 0.11, opacity: 0.75) : Color(.sRGB, red: 0.85, green: 0.85, blue: 0.83, opacity: 0.8)
            let text = OrbitPhotoRingType.tracked(label, OrbitPhotoRingType.engrave, x: w * 0.44, y: y0 + plate * 0.66, tracking: 1.6, align: 1)
            ctx.fill(s.path(text, threshold: 0.15), with: .color(ink))
        }
        shadeRect(ctx, s, x0, y0, w, h, light)
    }

    /// A metal box: the prism hump or the hood on top. Its front carries a small dark name plate.
    private static func paintHumpFace(_ ctx: GraphicsContext, _ s: OrbitPhotoRingSurface, _ v: [Double], kind: Int, w: Double, h: Double, light: Double) {
        ctx.fill(s.rect(-w / 2, -h / 2, w, h), with: metal(v, s, -h / 2, h / 2))
        if kind == 0 && h > 14 {
            let plate = Path(roundedRect: CGRect(x: -w * 0.3, y: -h * 0.18, width: w * 0.6, height: h * 0.42), cornerSize: CGSize(width: 2, height: 2), style: .circular)
            ctx.fill(s.path(plate), with: .color(Color(orbitHex: 0x101012)))
            ctx.fill(s.rect(-w * 0.22, h * 0.02, w * 0.44, 1.4), with: .color(color(v, V.accent, 1, 0.9)))
        }
        shadeRect(ctx, s, -w / 2, -h / 2, w, h, light)
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    /// A box of the camera, `hump` false for the body and true for the hump. Only the faces turned
    /// towards the eye are drawn; a box is convex, so they never overlap.
    private static func drawBox(_ ctx: GraphicsContext, _ view: OrbitPhotoRingView3, _ pose: OrbitPhotoRingPose, _ v: [Double], at: [Double], w: Double, h: Double, d: Double, hump: Bool, detail: Bool, label: String) {
        let x = at[0], y = at[1], z = at[2]
        // front, back, right, left, top, bottom: centre, u, v, normal, drawing size.
        let faces: [([Double], [Double], [Double], [Double], Double, Double)] = [
            ([x, y, z - d / 2], [1, 0, 0], [0, 1, 0], [0, 0, -1], w, h),
            ([x, y, z + d / 2], [-1, 0, 0], [0, 1, 0], [0, 0, 1], w, h),
            ([x + w / 2, y, z], [0, 0, 1], [0, 1, 0], [1, 0, 0], d, h),
            ([x - w / 2, y, z], [0, 0, -1], [0, 1, 0], [-1, 0, 0], d, h),
            ([x, y - h / 2, z], [1, 0, 0], [0, 0, -1], [0, -1, 0], w, d),
            ([x, y + h / 2, z], [1, 0, 0], [0, 0, 1], [0, 1, 0], w, d),
        ]
        for (k, f) in faces.enumerated() {
            if hump && k == 5 { continue }
            let info = P.face(view, pose, f.0, f.1, f.2, f.3)
            guard info.visible, let m = info.m else { continue }
            let s = OrbitPhotoRingSurface(m: m)
            if hump {
                paintHumpFace(ctx, s, v, kind: k, w: f.4, h: f.5, light: info.light)
            } else {
                paintBodyFace(ctx, s, v, kind: k, w: f.4, h: f.5, light: info.light, detail: detail, label: label)
            }
        }
    }

    /// A disc drawn through the local affine map at its centre.
    private static func disc(_ ctx: GraphicsContext, _ m: [Double]) -> GraphicsContext {
        var c = ctx
        c.transform = OrbitPhotoRingSurface(m: m).affine()
        return c
    }

    /// A knob standing on the top plate: slices of a cylinder, then a knurled cap.
    private static func drawKnob(_ ctx: GraphicsContext, _ view: OrbitPhotoRingView3, _ pose: OrbitPhotoRingPose, _ v: [Double], x: Double, top: Double, z: Double, r: Double, h: Double, detail: Bool) {
        let slices = detail ? 4 : 2
        for i in 0...slices {
            let s = h * Double(i) / Double(slices)
            let info = P.face(view, pose, [x, top - s, z], [1, 0, 0], [0, 0, -1], [0, -1, 0])
            guard let m = info.m else { continue }
            let c = disc(ctx, m)
            if i < slices {
                c.fill(circle(0, 0, r), with: .color(color(v, V.metal, i % 2 == 0 ? 0.55 : 0.75)))
            } else {
                let cap = Gradient(colors: [color(v, V.metal, 1.35), color(v, V.metal, 0.7)])
                c.fill(circle(0, 0, r), with: .radialGradient(cap, center: CGPoint(x: -r * 0.3, y: -r * 0.3), startRadius: 0, endRadius: r * 1.3))
                if detail {
                    var ticks = Path()
                    for k in 0..<18 {
                        let a = Double(k) / 18 * .pi * 2
                        ticks.move(to: CGPoint(x: cos(a) * r * 0.72, y: sin(a) * r * 0.72))
                        ticks.addLine(to: CGPoint(x: cos(a) * r * 0.95, y: sin(a) * r * 0.95))
                    }
                    c.stroke(ticks, with: .color(.black.opacity(0.35)), lineWidth: 0.8)
                }
            }
        }
    }

    /// The lens: a metal mount, a ribbed black barrel built from slices of a cylinder, a chrome
    /// front ring and the glass with its reflections. Drawn from the body outwards, or the other
    /// way round when the lens points away.
    private static func drawLens(_ ctx: GraphicsContext, _ view: OrbitPhotoRingView3, _ pose: OrbitPhotoRingPose, _ v: [Double], outward: Bool, detail: Bool, flash: Double) {
        let r = v[V.lensR]
        let length = v[V.lensL]
        let lx = v[V.lensX]
        let ly = v[V.lensY]
        let front = -v[V.d] / 2
        let slices = detail ? max(6, Int(P.round(length / 3))) : 4
        var order = Array(0...(slices + 1))
        if !outward { order.reverse() }
        for i in order {
            // Slice 0 is the mount; slices 1…n the barrel; n + 1 the front with the glass.
            let s = i == 0 ? 2 : i == slices + 1 ? length : 2 + (length - 2) * Double(i) / Double(slices + 1)
            let info = P.face(view, pose, [lx, ly, front - s], [1, 0, 0], [0, 1, 0], [0, 0, -1])
            guard let m = info.m else { continue }
            let c = disc(ctx, m)
            if i == 0 {
                c.fill(circle(0, 0, r * 1.16), with: .color(color(v, V.metal, 0.8)))
            } else if i <= slices {
                let nearFront = i >= slices - 1
                if nearFront {
                    c.fill(circle(0, 0, r * 1.02), with: .color(color(v, V.metal, i % 2 == 0 ? 0.75 : 1)))
                } else {
                    // A sheen across each rib, so the barrel reads as a lit cylinder when seen from the side.
                    let k = i % 2 == 0 ? 1.0 : 1.5
                    let rib = Gradient(stops: [
                        .init(color: color(v, V.barrel, 3.4 * k), location: 0),
                        .init(color: color(v, V.barrel, 1.3 * k), location: 0.5),
                        .init(color: color(v, V.barrel, 0.8 * k), location: 1),
                    ])
                    c.fill(circle(0, 0, r * 0.98), with: .linearGradient(rib, startPoint: CGPoint(x: -r, y: -r), endPoint: CGPoint(x: r, y: r)))
                }
            } else {
                let ring = Gradient(stops: [
                    .init(color: color(v, V.metal, 1.35), location: 0),
                    .init(color: color(v, V.metal, 0.65), location: 0.55),
                    .init(color: color(v, V.metal, 1.1), location: 1),
                ])
                c.fill(circle(0, 0, r * 1.02), with: .linearGradient(ring, startPoint: CGPoint(x: -r, y: -r), endPoint: CGPoint(x: r, y: r)))
                c.fill(circle(0, 0, r * 0.86), with: .color(Color(orbitHex: 0x0b0b0d)))
                if detail {
                    var ticks = Path()
                    for k in 0..<28 {
                        let a = Double(k) / 28 * .pi * 2
                        let long = k % 4 == 0 ? 0.74 : 0.79
                        ticks.move(to: CGPoint(x: cos(a) * r * long, y: sin(a) * r * long))
                        ticks.addLine(to: CGPoint(x: cos(a) * r * 0.84, y: sin(a) * r * 0.84))
                    }
                    c.stroke(ticks, with: .color(.white.opacity(0.35)), lineWidth: 0.7)
                }
                // The glass: deep colour falling to black, two reflections, and the shutter's flash.
                let glass = Gradient(stops: [
                    .init(color: color(v, V.glass, 2.1), location: 0),
                    .init(color: color(v, V.glass, 0.9), location: 0.45),
                    .init(color: Color(.sRGB, red: 0.02, green: 0.02, blue: 0.03), location: 1),
                ])
                c.fill(circle(0, 0, r * 0.66), with: .radialGradient(glass, center: CGPoint(x: -r * 0.18, y: -r * 0.22), startRadius: 0, endRadius: r * 0.8))
                c.stroke(circle(0, 0, r * 0.4), with: .color(color(v, V.glass, 2.4, 0.5)), lineWidth: 1)
                var glint = c
                glint.translateBy(x: -r * 0.26, y: -r * 0.3)
                glint.rotate(by: .degrees(-35))
                glint.fill(Path(ellipseIn: CGRect(x: -r * 0.2, y: -r * 0.08, width: r * 0.4, height: r * 0.16)), with: .color(.white.opacity(0.55)))
                c.fill(circle(r * 0.28, r * 0.24, r * 0.05), with: .color(.white.opacity(0.4)))
                if flash > 0.01 {
                    let burst = Gradient(colors: [Color(.sRGB, red: 1, green: 1, blue: 1, opacity: flash), Color(.sRGB, red: 1, green: 0.97, blue: 0.9, opacity: 0)])
                    c.fill(circle(0, 0, r * 0.9), with: .radialGradient(burst, center: .zero, startRadius: 0, endRadius: r * 0.9))
                }
            }
            if i > 0 {
                // The barrel and the front ring take the light of the way the lens points.
                let alpha = (1 - max(info.light, i == slices + 1 ? 0.85 : 0)) * 0.72
                c.fill(circle(0, 0, r * 1.02), with: .color(.black.opacity(alpha)))
            }
        }
    }

    /// What stands on the top plate: the prism hump or hood, the speed dial with its release, the rewind knob.
    private static func drawTopParts(_ ctx: GraphicsContext, _ view: OrbitPhotoRingView3, _ pose: OrbitPhotoRingPose, _ v: [Double], detail: Bool, label: String) {
        let w = v[V.w], h = v[V.h], d = v[V.d]
        let hh = v[V.humpH]
        if hh > 0.8 {
            drawBox(ctx, view, pose, v, at: [0, -h / 2 - hh / 2, d * 0.04], w: v[V.humpW], h: hh, d: v[V.humpD], hump: true, detail: detail, label: label)
        }
        drawKnob(ctx, view, pose, v, x: w * 0.36, top: -h / 2, z: -d * 0.05, r: min(15, w * 0.065), h: 6, detail: detail)
        drawKnob(ctx, view, pose, v, x: w * 0.36, top: -h / 2 - 6, z: -d * 0.05, r: 5.5, h: 5, detail: detail)
        drawKnob(ctx, view, pose, v, x: -w * 0.36, top: -h / 2, z: 0, r: min(13, w * 0.058), h: 9, detail: detail)
    }

    /// The whole camera at `pose`: the parts on its top plate and the lens are drawn before or after
    /// the body depending on whether they face the eye, which is all the depth sorting a box with a
    /// knob on top and a lens in front needs.
    static func drawCamera(_ ctx: GraphicsContext, view: OrbitPhotoRingView3, pose: OrbitPhotoRingPose, v: [Double], detail: Bool, label: String, flash: Double) {
        let w = v[V.w], h = v[V.h], d = v[V.d]
        let front = P.face(view, pose, [0, 0, -d / 2], [1, 0, 0], [0, 1, 0], [0, 0, -1])
        let top = P.face(view, pose, [0, -h / 2, 0], [1, 0, 0], [0, 0, -1], [0, -1, 0])
        if !front.visible { drawLens(ctx, view, pose, v, outward: false, detail: detail, flash: flash) }
        if !top.visible { drawTopParts(ctx, view, pose, v, detail: detail, label: label) }
        drawBox(ctx, view, pose, v, at: [0, 0, 0], w: w, h: h, d: d, hump: false, detail: detail, label: label)
        if top.visible { drawTopParts(ctx, view, pose, v, detail: detail, label: label) }
        if front.visible { drawLens(ctx, view, pose, v, outward: true, detail: detail, flash: flash) }
    }
}
