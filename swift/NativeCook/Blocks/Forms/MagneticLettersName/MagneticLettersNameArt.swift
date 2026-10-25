import CoreGraphics
import CoreText
import SwiftUI
import UIKit

/*
 Magnetic Letters Name — everything drawn once, off the main thread, with Core Graphics:
 - the fridge door (brushed white steel, reflections, the freezer seam, two chrome handles, the
   engraved name line) as one screen-sized image;
 - the magnets' faces (glossy plastic with a 3 pt extruded side, a bevel and a gloss spot), one
   image per magnet at the screen's pixel ratio;
 - glyph-shaped shadows, four per letter (contact → lifted: more blur, less alpha).
 A frame then only places those images (one Canvas).
*/

struct MagneticLettersNameMagnet {
    let letter: Character
    let color: Int
    var home: MagneticLettersNamePose
    let w: Double
    let r: Double
}

struct MagneticLettersNameLayout {
    typealias Pose = MagneticLettersNamePose

    /// The letters on the door: A–Z and a spare E and A.
    static let letters = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZEA")
    /// The loop's name gets its own colours (red, yellow, blue, green) so it reads as a set.
    static let scriptColors = [0, 2, 4, 3]

    let W: Double
    let H: Double
    let top: Double
    /// Magnet scale against the 393 × 852 design.
    let s: Double
    let seamY: Double
    let lineY: Double
    let letterY: Double
    let lineX0: Double
    let lineX1: Double
    let pile: (x0: Double, y0: Double, x1: Double, y1: Double)
    let magnets: [MagneticLettersNameMagnet]
    /// Index of the magnet each letter of the script uses.
    let script: [Int]
    /// Base draw order: higher on the pile first, so lower magnets overlap them.
    let order: [Int]
    let cell: Double
    let shadowCell: Double
    let capH: Double
    let gap = 5.0
    /// The letters' outlines, baseline at y = 0 (y down), at the magnets' size.
    let paths: [CGPath]

    static func hash(_ n: Double) -> Double {
        let v = sin(n * 127.1 + 311.7) * 43758.5453
        return v - floor(v)
    }

    /// A rounded, heavy face for the magnets; falls back to a bold system face if it is missing.
    static func font(size: Double) -> CTFont {
        if let f = UIFont(name: "ArialRoundedMTBold", size: size) { return f as CTFont }
        return UIFont.systemFont(ofSize: size, weight: .bold) as CTFont
    }

    static func glyphPaths(size: Double) -> [CGPath] {
        let font = font(size: size)
        let flip = CGAffineTransform(scaleX: 1, y: -1)
        return letters.map { ch in
            var chars = Array(String(ch).utf16)
            var glyphs = [CGGlyph](repeating: 0, count: chars.count)
            CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
            var t = flip
            return CTFontCreatePathForGlyph(font, glyphs[0], &t) ?? CGPath(rect: .zero, transform: nil)
        }
    }

    init(W: Double, H: Double, top: Double, bottom: Double) {
        self.W = W
        self.H = H
        self.top = top
        seamY = top + 152
        lineY = seamY + 128
        pile = (22, lineY + 46, W - 50, H - bottom - 104)
        s = max(0.74, min(1, W / 393, (pile.y1 - pile.y0) / 330))
        paths = Self.glyphPaths(size: 52 * s)
        let hIndex = Self.letters.firstIndex(of: "H")!
        capH = paths[hIndex].boundingBoxOfPath.height
        letterY = lineY - 5 - capH / 2
        cell = ceil(capH * 1.9)
        shadowCell = cell + 28
        lineX0 = 30
        lineX1 = W - 72

        let letters = Self.letters
        let script = MagneticLettersNameMotion.script
        var scriptIdx = [Int](repeating: -1, count: script.count)
        var mags: [MagneticLettersNameMagnet] = []
        for i in 0..<letters.count {
            let b = paths[i].boundingBoxOfPath
            let k = script.firstIndex(of: letters[i]) ?? -1
            let isScript = k >= 0 && scriptIdx[k] < 0
            if isScript { scriptIdx[k] = i }
            mags.append(MagneticLettersNameMagnet(
                letter: letters[i],
                color: isScript ? Self.scriptColors[k] : (i * 5 + 1) % 6,
                home: Pose(),
                w: b.width + 4,
                r: max(b.width, capH) * 0.58
            ))
        }

        // A loose heap: a jittered grid, then pushed apart a few times so magnets overlap only at
        // their edges. Seeded, so the heap is the same on every launch.
        let n = mags.count
        let pile = self.pile
        let capH = self.capH
        let cols = 6
        let rows = (n + cols - 1) / cols
        let cw = (pile.x1 - pile.x0) / Double(cols)
        let ch = (pile.y1 - pile.y0) / Double(rows)
        let slots = Array(0..<(cols * rows)).sorted { Self.hash(Double($0) * 7.3) < Self.hash(Double($1) * 7.3) }
        var pts: [(x: Double, y: Double)] = (0..<n).map { i in
            let slot = slots[i]
            let c = Double(slot % cols)
            let r = Double(slot / cols)
            return (
                pile.x0 + (c + 0.5 + (Self.hash(Double(i) * 3.1) - 0.5) * 0.6) * cw,
                pile.y0 + (r + 0.5 + (Self.hash(Double(i) * 5.7) - 0.5) * 0.55) * ch
            )
        }
        let minD = capH * 0.95
        for _ in 0..<6 {
            for i in 0..<n {
                for j in (i + 1)..<max(i + 1, n) {
                    let dx = pts[j].x - pts[i].x
                    let dy = pts[j].y - pts[i].y
                    let dd = (dx * dx + dy * dy).squareRoot()
                    let d = dd == 0 ? 1 : dd
                    if d < minD {
                        let push = (minD - d) / 2
                        pts[i].x -= dx / d * push; pts[i].y -= dy / d * push
                        pts[j].x += dx / d * push; pts[j].y += dy / d * push
                    }
                }
            }
            for k in 0..<n {
                pts[k].x = min(pile.x1 - capH * 0.5, max(pile.x0 + capH * 0.5, pts[k].x))
                pts[k].y = min(pile.y1 - capH * 0.4, max(pile.y0 + capH * 0.5, pts[k].y))
            }
        }
        for i in 0..<n {
            mags[i].home = Pose(x: pts[i].x, y: pts[i].y, rot: (Self.hash(Double(i) * 9.1) - 0.5) * 0.9, lift: 0, scale: 1)
        }
        magnets = mags
        self.script = scriptIdx
        order = Array(0..<n).sorted { mags[$0].home.y < mags[$1].home.y }
    }

    /// The x of each letter's centre on the line for a name made of these magnets.
    func slotXs(_ name: [Int]) -> [Double] {
        var xs: [Double] = []
        var x = lineX0
        for i in name {
            let w = magnets[i].w
            xs.append(x + w / 2)
            x += w + gap
        }
        return xs
    }

    var maxFit: Int {
        min(MagneticLettersNameMotion.maxLetters, Int(floor((lineX1 - lineX0) / (capH * 0.95 + gap))))
    }
}

/// The baked images a frame draws.
struct MagneticLettersNameArt {
    let door: Image
    /// One face per magnet, `cell` pt square.
    let faceImages: [Image]
    /// Per magnet, four lift levels, `shadowCell` pt square.
    let shadowImages: [[Image]]

    /// The bitmaps, made off the main thread.
    struct Bitmaps: @unchecked Sendable {
        let px: Double
        let door: CGImage
        let faces: [CGImage]
        let shadows: [[CGImage]]
    }

    static let shadowSigma: [Double] = [1.4, 3.2, 5.6, 8.5]
    static let shadowAlpha: [Double] = [0.46, 0.32, 0.23, 0.17]
    static let shadowScale = 1.5
    static var levels: Int { shadowSigma.count }

    /// Plastic colours: light (top of the face), base, deep (bottom), side (the extrusion).
    static let plastic: [[UInt32]] = [
        [0xFF8A7A, 0xE8483C, 0xBC2C26, 0x8A1D1A],
        [0xFFBE6E, 0xF28A2E, 0xCA641B, 0x954310],
        [0xFFE893, 0xF5C232, 0xD59A16, 0x9F6F0B],
        [0x86D896, 0x3FA45B, 0x2A7B43, 0x1C5630],
        [0x86B9FF, 0x3076D2, 0x1F53A2, 0x163B74],
        [0xC29DF4, 0x8858CB, 0x603C9C, 0x452A71],
    ]

    static func bake(_ L: MagneticLettersNameLayout, px: Double) -> Bitmaps {
        Bitmaps(
            px: px,
            door: bakeDoor(L, px: px),
            faces: L.magnets.indices.map { bakeFace(L, $0, px: px) },
            shadows: L.magnets.indices.map { i in (0..<levels).map { bakeShadow(L, i, level: $0) } }
        )
    }

    init(_ b: Bitmaps) {
        door = Image(decorative: b.door, scale: b.px)
        faceImages = b.faces.map { Image(decorative: $0, scale: b.px) }
        shadowImages = b.shadows.map { $0.map { Image(decorative: $0, scale: Self.shadowScale) } }
    }

    // MARK: - Core Graphics helpers

    static func cg(_ hex: UInt32, _ a: Double = 1) -> CGColor {
        CGColor(
            srgbRed: CGFloat((hex >> 16) & 255) / 255,
            green: CGFloat((hex >> 8) & 255) / 255,
            blue: CGFloat(hex & 255) / 255,
            alpha: CGFloat(a)
        )
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> CGColor {
        CGColor(srgbRed: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    static func gradient(_ colors: [CGColor], _ pos: [CGFloat]?) -> CGGradient {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray, locations: pos)!
    }

    /// Fills the current clip with a linear gradient from p0 to p1 (clamped at both ends).
    static func linear(_ c: CGContext, _ p0: CGPoint, _ p1: CGPoint, _ colors: [CGColor], _ pos: [CGFloat]? = nil) {
        c.drawLinearGradient(gradient(colors, pos), start: p0, end: p1, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }

    static func fillLinear(_ c: CGContext, _ path: CGPath, _ p0: CGPoint, _ p1: CGPoint, _ colors: [CGColor], _ pos: [CGFloat]? = nil) {
        c.saveGState()
        c.addPath(path)
        c.clip()
        linear(c, p0, p1, colors, pos)
        c.restoreGState()
    }

    /// Draws `draw` blurred (a Gaussian of `sigma` pt) by rendering it far off to the left and
    /// letting its shadow fall back into place. `px` is the context's pixels per point.
    static func blurred(_ c: CGContext, sigma: Double, px: Double, color: CGColor, _ draw: (CGContext) -> Void) {
        let far = 40000.0
        c.saveGState()
        c.setShadow(offset: CGSize(width: far, height: 0), blur: CGFloat(sigma * 2 * px), color: color)
        // The shape moves `far` pixels left in device space, whatever the current rotation.
        let m = c.ctm.inverted()
        let d = CGPoint(x: -far, y: 0).applying(CGAffineTransform(a: m.a, b: m.b, c: m.c, d: m.d, tx: 0, ty: 0))
        c.translateBy(x: d.x, y: d.y)
        // The shadow takes its alpha from `color`; the shape itself is opaque so it is not applied twice.
        c.setFillColor(CGColor(gray: 0, alpha: 1))
        c.setStrokeColor(CGColor(gray: 0, alpha: 1))
        draw(c)
        c.restoreGState()
    }

    static func render(width: Double, height: Double, px: Double, _ draw: (CGContext) -> Void) -> CGImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        format.preferredRange = .standard
        let size = CGSize(width: ceil(width * px), height: ceil(height * px))
        let img = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let c = ctx.cgContext
            c.scaleBy(x: px, y: px)
            draw(c)
        }
        return img.cgImage!
    }

    // MARK: - The door

    static func bakeDoor(_ L: MagneticLettersNameLayout, px: Double) -> CGImage {
        let W = L.W, H = L.H, seamY = L.seamY, lineY = L.lineY
        let hash = MagneticLettersNameLayout.hash
        return render(width: W, height: H, px: px) { c in
            // Steel: a soft fall-off from the kitchen light above.
            fillLinear(c, CGPath(rect: CGRect(x: 0, y: 0, width: W, height: H), transform: nil), .zero, CGPoint(x: 0, y: H),
                       [cg(0xF6F6F3), cg(0xE8E9E5), cg(0xDFE1DD), cg(0xD7D9D5)], [0, 0.35, 0.7, 1])
            // Brushing: thousands of horizontal hairlines, a little lighter or darker than the steel.
            for k in 0..<Int(H * 3) {
                let kd = Double(k)
                let y = hash(kd + 1) * H
                let x0 = hash(kd * 3 + 2) * W * 0.8 - 40
                let len = 40 + hash(kd * 7 + 3) * W * 0.9
                let light = hash(kd * 11 + 4) > 0.45
                c.setFillColor(cg(light ? 0xFFFFFF : 0x8E9290, 0.05 + hash(kd * 13 + 5) * (light ? 0.16 : 0.06)))
                c.fill(CGRect(x: x0, y: y, width: len, height: 0.4))
            }
            // Reflections: a cool window light from the left and a broad soft band across the upper door.
            fillLinear(c, CGPath(rect: CGRect(x: 0, y: 0, width: W * 0.4, height: H), transform: nil), .zero, CGPoint(x: W * 0.4, y: 0),
                       [rgba(196, 212, 228, 0.42), rgba(214, 224, 234, 0.12), rgba(230, 236, 240, 0)], [0, 0.5, 1])
            c.saveGState()
            c.translateBy(x: W / 2, y: H / 2)
            c.rotate(by: -16 * .pi / 180)
            c.translateBy(x: -W / 2, y: -H / 2)
            blurred(c, sigma: 34, px: px, color: cg(0xFFFFFF, 0.5)) { $0.fill(CGRect(x: -W, y: H * 0.18, width: W * 3, height: H * 0.13)) }
            blurred(c, sigma: 8, px: px, color: cg(0xFFFFFF, 0.4)) { $0.fill(CGRect(x: -W, y: H * 0.36, width: W * 3, height: H * 0.025)) }
            blurred(c, sigma: 30, px: px, color: cg(0xB9BDBA, 0.16)) { $0.fill(CGRect(x: -W, y: H * 0.62, width: W * 3, height: H * 0.09)) }
            c.restoreGState()
            // Warm bounce from the kitchen floor.
            fillLinear(c, CGPath(rect: CGRect(x: 0, y: H * 0.72, width: W, height: H * 0.28), transform: nil),
                       CGPoint(x: 0, y: H * 0.72), CGPoint(x: 0, y: H), [rgba(232, 214, 190, 0), rgba(228, 206, 178, 0.24)])
            // The door's rolled edges, left and right.
            fillLinear(c, CGPath(rect: CGRect(x: 0, y: 0, width: 10, height: H), transform: nil), .zero, CGPoint(x: 10, y: 0),
                       [rgba(120, 124, 122, 0.28), rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0)], [0, 0.35, 1])
            fillLinear(c, CGPath(rect: CGRect(x: W - 8, y: 0, width: 8, height: H), transform: nil), CGPoint(x: W - 8, y: 0), CGPoint(x: W, y: 0),
                       [rgba(255, 255, 255, 0), rgba(110, 114, 112, 0.3)])

            // The gap between the freezer and fridge doors: a dark seam, the freezer's shaded lower
            // lip, the fridge door's lit top edge.
            fillLinear(c, CGPath(rect: CGRect(x: 0, y: seamY - 14, width: W, height: 14), transform: nil),
                       CGPoint(x: 0, y: seamY - 14), CGPoint(x: 0, y: seamY), [rgba(140, 144, 142, 0), rgba(120, 124, 122, 0.32)])
            fillLinear(c, CGPath(rect: CGRect(x: 0, y: seamY, width: W, height: 5), transform: nil),
                       CGPoint(x: 0, y: seamY), CGPoint(x: 0, y: seamY + 5), [cg(0x55595A), cg(0x2F3335), cg(0x4A4E4F)], [0, 0.5, 1])
            c.setFillColor(cg(0xFFFFFF, 0.95))
            c.fill(CGRect(x: 0, y: seamY + 5, width: W, height: 1.2))
            fillLinear(c, CGPath(rect: CGRect(x: 0, y: seamY + 6, width: W, height: 18), transform: nil),
                       CGPoint(x: 0, y: seamY + 6), CGPoint(x: 0, y: seamY + 24), [rgba(255, 255, 255, 0.55), rgba(255, 255, 255, 0)])

            // Chrome handles on the right: the freezer's short one, the fridge's long one (below the
            // name line, so the backspace key has the door to itself).
            handle(c, px: px, x: W - 25, y0: L.top + 26, y1: seamY - 26)
            handle(c, px: px, x: W - 25, y0: lineY + 44, y1: min(lineY + 300, H * 0.7))

            // The name line: an engraved groove (dark line, light line under it) and the strip's shade.
            blurred(c, sigma: 14, px: px, color: cg(0x9EA3A0, 0.1)) {
                $0.fill(CGRect(x: 18, y: lineY - L.capH - 30, width: L.lineX1 - 4, height: L.capH + 38))
            }
            c.setFillColor(cg(0x6A6F6E, 0.5))
            c.fill(CGRect(x: L.lineX0 - 6, y: lineY, width: L.lineX1 - L.lineX0 + 12, height: 1))
            c.setFillColor(cg(0xFFFFFF, 0.95))
            c.fill(CGRect(x: L.lineX0 - 6, y: lineY + 1, width: L.lineX1 - L.lineX0 + 12, height: 1))

            // Grain, so the large pale flat does not read as vector.
            let light = cg(0xFFFFFF, 0.07)
            let dark = cg(0x6C706E, 0.07)
            for k in 0..<5200 {
                let kd = Double(k)
                c.setFillColor(hash(kd * 3.9) > 0.5 ? light : dark)
                c.fill(CGRect(x: hash(kd * 1.7 + 9) * W, y: hash(kd * 2.3 + 4) * H, width: 0.7, height: 0.7))
            }
        }
    }

    /// A chrome bar handle standing off the door on two posts, with its contact shadow.
    static func handle(_ c: CGContext, px: Double, x: Double, y0: Double, y1: Double) {
        let w = 12.0
        blurred(c, sigma: 6, px: px, color: cg(0x3A3E40, 0.3)) {
            $0.addPath(CGPath(roundedRect: CGRect(x: x - 7, y: y0 + 6, width: w + 2, height: y1 - y0), cornerWidth: 7, cornerHeight: 7, transform: nil))
            $0.fillPath()
        }
        blurred(c, sigma: 1.6, px: px, color: cg(0x3A3E40, 0.28)) {
            $0.addPath(CGPath(roundedRect: CGRect(x: x - 1, y: y0 + 2, width: w, height: y1 - y0), cornerWidth: 6, cornerHeight: 6, transform: nil))
            $0.fillPath()
        }
        for py in [y0 + 8, y1 - 14] {
            fillLinear(c, CGPath(roundedRect: CGRect(x: x - 2, y: py, width: w + 4, height: 8), cornerWidth: 3, cornerHeight: 3, transform: nil),
                       CGPoint(x: x - 2, y: 0), CGPoint(x: x + w + 2, y: 0), [cg(0x8D9294), cg(0xE9ECEC), cg(0x6F7476)], [0, 0.45, 1])
        }
        fillLinear(c, CGPath(roundedRect: CGRect(x: x, y: y0, width: w, height: y1 - y0), cornerWidth: 6, cornerHeight: 6, transform: nil),
                   CGPoint(x: x, y: 0), CGPoint(x: x + w, y: 0),
                   [cg(0x6E7375), cg(0xC9CDCE), cg(0xFBFCFC), cg(0x9EA3A5), cg(0x5C6163)], [0, 0.28, 0.42, 0.7, 1])
        c.setStrokeColor(cg(0xFFFFFF, 0.6))
        c.setLineWidth(0.7)
        c.addPath(CGPath(roundedRect: CGRect(x: x + 0.4, y: y0 + 0.4, width: w - 0.8, height: y1 - y0 - 0.8), cornerWidth: 6, cornerHeight: 6, transform: nil))
        c.strokePath()
    }

    // MARK: - Magnets

    /// Moves the origin so the cap box of glyph `p` is centred on (cx, cy).
    static func placeGlyph(_ c: CGContext, _ p: CGPath, _ L: MagneticLettersNameLayout, _ cx: Double, _ cy: Double) {
        let b = p.boundingBoxOfPath
        c.translateBy(x: cx - b.midX, y: cy + L.capH / 2)
    }

    /// One magnet's face: a `cell` pt square at `px` pixels per point.
    static func bakeFace(_ L: MagneticLettersNameLayout, _ i: Int, px: Double) -> CGImage {
        let cell = L.cell
        let p = L.paths[i]
        let col = plastic[L.magnets[i].color]
        return render(width: cell, height: cell, px: px) { c in
            placeGlyph(c, p, L, cell / 2, cell / 2)
            let b = p.boundingBoxOfPath
            let top = CGPoint(x: 0, y: b.minY)
            let bottom = CGPoint(x: 0, y: b.maxY)
            // The extruded side: the glyph stacked down and to the right, ~3 pt deep.
            for k in stride(from: 6, through: 1, by: -1) {
                c.saveGState()
                c.translateBy(x: Double(k) * 0.3, y: Double(k) * 0.5)
                if k == 6 {
                    c.setFillColor(cg(col[3]))
                    c.addPath(p)
                    c.fillPath()
                } else {
                    fillLinear(c, p, top, bottom, [cg(col[2]), cg(col[3])])
                }
                c.restoreGState()
            }
            // The face: three stops, lighter at the top where the room light lands.
            fillLinear(c, p, top, bottom, [cg(col[0]), cg(col[1]), cg(col[2])], [0, 0.45, 1])
            c.saveGState()
            c.addPath(p)
            c.clip()
            // Bevel: a bright inner edge along the top, a darker one along the bottom.
            c.saveGState()
            c.translateBy(x: 0, y: 1.4)
            blurred(c, sigma: 0.6, px: px, color: cg(0xFFFFFF, 0.5)) {
                $0.setLineWidth(3)
                $0.addPath(p)
                $0.strokePath()
            }
            c.restoreGState()
            c.saveGState()
            c.translateBy(x: 0, y: -1.4)
            blurred(c, sigma: 0.8, px: px, color: cg(col[3], 0.45)) {
                $0.setLineWidth(3)
                $0.addPath(p)
                $0.strokePath()
            }
            c.restoreGState()
            // Gloss: a soft specular spot up and to the left, and a thin sharp glint inside it.
            let gx = b.minX + b.width * 0.32
            let gy = b.minY + b.height * 0.22
            c.saveGState()
            c.addEllipse(in: CGRect(x: gx - b.width * 0.45, y: gy - b.height * 0.2, width: b.width * 0.9, height: b.height * 0.4))
            c.clip()
            c.drawRadialGradient(
                gradient([cg(0xFFFFFF, 0.62), cg(0xFFFFFF, 0.18), cg(0xFFFFFF, 0)], [0, 0.45, 1]),
                startCenter: CGPoint(x: gx, y: gy), startRadius: 0,
                endCenter: CGPoint(x: gx, y: gy), endRadius: b.width * 0.5,
                options: [.drawsAfterEndLocation]
            )
            c.restoreGState()
            blurred(c, sigma: 0.9, px: px, color: cg(0xFFFFFF, 0.75)) {
                $0.fillEllipse(in: CGRect(x: gx - b.width * 0.16, y: gy - 2.2, width: b.width * 0.26, height: 3.4))
            }
            c.restoreGState()
            c.setStrokeColor(cg(col[3], 0.35))
            c.setLineWidth(0.8)
            c.addPath(p)
            c.strokePath()
        }
    }

    /// One magnet's glyph-shaped shadow at one lift level: a `shadowCell` pt square.
    static func bakeShadow(_ L: MagneticLettersNameLayout, _ i: Int, level: Int) -> CGImage {
        let cell = L.shadowCell
        let p = L.paths[i]
        return render(width: cell, height: cell, px: shadowScale) { c in
            placeGlyph(c, p, L, cell / 2, cell / 2)
            blurred(c, sigma: shadowSigma[level], px: shadowScale, color: cg(0x2C2A26, shadowAlpha[level])) {
                $0.addPath(p)
                $0.fillPath()
            }
        }
    }
}
