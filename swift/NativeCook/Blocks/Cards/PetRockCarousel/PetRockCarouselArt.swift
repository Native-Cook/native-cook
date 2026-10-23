import CoreImage
import CoreText
import SwiftUI
import UIKit

// Rock geometry and paint. Every rock is drawn in its own "rock units": a box of about 200 × 200
// centred on (0, 0), so the faces painted on them share one set of coordinates whatever the
// rock's size.

/// A small seeded random generator (mulberry32, bit for bit as in the React Native version), so
/// every rock keeps its shape from run to run.
struct PetRockCarouselRandom {
    private var t: UInt32

    init(_ seed: Int) { t = UInt32(truncatingIfNeeded: seed) }

    mutating func next() -> Double {
        t = t &+ 0x6d2b_79f5
        var r = (t ^ (t >> 15)) &* (1 | t)
        r = (r &+ ((r ^ (r >> 7)) &* (61 | r))) ^ r
        return Double(r ^ (r >> 14)) / 4_294_967_296
    }
}

enum PetRockCarouselShapes {
    // Samples around the outline, and how far the outline leans to a faceted polygon (1) rather
    // than a round blob (0): river stones are mostly round with a few flat, chipped faces.
    private static let samples = 120
    private static let faceted = 0.55
    // Corners of the underlying polygon.
    private static let cornersMin = 7
    private static let cornersSpread = 4.0
    // Low harmonics that make the blob lumpy, and a fine ripple that roughens the edge.
    private static let harmonics = [2.0, 3, 4, 5]
    private static let lump = 0.05
    private static let ripple = 0.006
    // Rocks sit on a flatter base: below this height (rock units) the outline is pressed up by half.
    private static let baseAt = 66.0

    /// A pebble outline: a faceted polygon blended with a lumpy blob and smoothed, so it reads as a
    /// stone with a few flat faces rather than a circle. `aspect` squashes it vertically.
    static func rock(seed: Int, aspect: Double) -> Path {
        var rand = PetRockCarouselRandom(seed)
        let corners = cornersMin + Int(floor(rand.next() * cornersSpread))
        var points: [CGPoint] = []
        for k in 0..<corners {
            let angle = ((Double(k) + (rand.next() - 0.5) * 0.5) / Double(corners)) * tau
            let radius = 0.86 + rand.next() * 0.16
            points.append(CGPoint(x: cos(angle) * radius, y: sin(angle) * radius))
        }
        let order = points
            .map { p -> (a: Double, p: CGPoint) in
                let a = atan2(p.y, p.x)
                return (a < 0 ? a + tau : a, p)
            }
            .sorted { $0.a < $1.a }
        let lumps = harmonics.map { n in
            (n: n, amp: (rand.next() * lump) / sqrt(n * 0.5), phase: rand.next() * tau)
        }
        let ripplePhase = rand.next() * tau

        var radii: [Double] = []
        for j in 0..<samples {
            let theta = Double(j) / Double(samples) * tau
            // The polygon edge this ray crosses: r·d = P1 + u·(P2 − P1)  ⇒  r = (P1 × E) / (d × E).
            var k = (order.firstIndex { $0.a > theta } ?? -1) - 1
            if k < 0 { k = order.count - 1 }
            let p1 = order[k].p
            let p2 = order[(k + 1) % order.count].p
            let ex = p2.x - p1.x
            let ey = p2.y - p1.y
            let dx = cos(theta)
            let dy = sin(theta)
            let denominator = dx * ey - dy * ex
            let line = abs(denominator) < 1e-6 ? 1 : (p1.x * ey - p1.y * ex) / denominator
            var round = 0.95
            for l in lumps { round += l.amp * sin(l.n * theta + l.phase) }
            round += ripple * sin(23 * theta + ripplePhase)
            radii.append(faceted * min(1.1, max(0.7, line)) + (1 - faceted) * round)
        }
        // A short running average rounds the polygon's corners off, like water would.
        var path = Path()
        for j in 0..<samples {
            var sum = 0.0
            for o in -3...3 { sum += radii[(j + o + samples) % samples] }
            let r = sum / 7
            let theta = Double(j) / Double(samples) * tau
            let x = cos(theta) * r * 100
            var y = sin(theta) * r * 100 * aspect
            let base = baseAt * aspect
            if y > base { y = base + (y - base) * 0.5 }
            if j == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
        }
        path.closeSubpath()
        return path
    }

    /// Tiny dark and light flecks scattered over the stone, as two paths.
    static func speckles(seed: Int) -> (dark: Path, light: Path) {
        var rand = PetRockCarouselRandom(seed * 7 + 3)
        var dark = Path()
        var light = Path()
        for k in 0..<70 {
            let angle = rand.next() * tau
            let distance = sqrt(rand.next()) * 94
            let radius = 0.5 + rand.next() * rand.next() * 2.2
            let circle = CGRect(x: cos(angle) * distance - radius, y: sin(angle) * distance - radius, width: radius * 2, height: radius * 2)
            if k % 3 == 0 { light.addEllipse(in: circle) } else { dark.addEllipse(in: circle) }
        }
        return (dark, light)
    }

    /// The absolute SVG path commands the faces use (M, L, C, Q, Z) as a SwiftUI path.
    static func svg(_ d: String) -> Path {
        var path = Path()
        var command: Character = "M"
        var numbers: [Double] = []
        func flush() {
            var i = 0
            func point() -> CGPoint {
                defer { i += 2 }
                return CGPoint(x: numbers[i], y: numbers[i + 1])
            }
            switch command {
            case "M":
                guard numbers.count >= 2 else { break }
                path.move(to: point())
                while i + 1 < numbers.count { path.addLine(to: point()) }
            case "L":
                while i + 1 < numbers.count { path.addLine(to: point()) }
            case "C":
                while i + 5 < numbers.count {
                    let c1 = point(), c2 = point(), to = point()
                    path.addCurve(to: to, control1: c1, control2: c2)
                }
            case "Q":
                while i + 3 < numbers.count {
                    let c = point(), to = point()
                    path.addQuadCurve(to: to, control: c)
                }
            case "Z":
                path.closeSubpath()
            default:
                break
            }
            numbers = []
        }
        for token in d.split(separator: " ") {
            if let value = Double(token) {
                numbers.append(value)
            } else if let first = token.first {
                flush()
                command = first
                if first == "Z" { flush() }
            }
        }
        flush()
        return path
    }

    /// A string's outline in a font, with its baseline's left end at `origin`, and its advance.
    static func text(_ string: String, font: UIFont, at origin: CGPoint) -> (path: Path, width: Double) {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [.font: font]))
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)
        let outline = CGMutablePath()
        for run in CTLineGetGlyphRuns(line) as? [CTRun] ?? [] {
            let attributes = CTRunGetAttributes(run) as NSDictionary
            // swiftlint:disable:next force_cast
            let runFont = attributes[kCTFontAttributeName as String].map { $0 as! CTFont } ?? font as CTFont
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: count), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: count), &positions)
            for g in 0..<count {
                guard let glyph = CTFontCreatePathForGlyph(runFont, glyphs[g], nil) else { continue }
                // Glyphs come y-up; the canvas is y-down.
                let place = CGAffineTransform(translationX: origin.x + positions[g].x, y: origin.y).scaledBy(x: 1, y: -1)
                outline.addPath(glyph, transform: place)
            }
        }
        return (Path(outline), width)
    }
}

// MARK: - Faces

/// The faces painted on the rocks, in rock units. Each face has two parts: the paint, which is
/// clipped to the stone and takes its grain and shading, and the stickers (googly eyes), which sit
/// on top of it with their own little shadow.
enum PetRockCarouselFaces {
    static let count = 6
    /// How each face reads in the caption, in the same order as the faces.
    static let moods = ["all eyes", "in a mood", "smitten", "freshly tagged", "giddy", "half asleep"]

    struct Eye {
        let x: Double
        let y: Double
        let r: Double
    }

    // Googly eyes of each face, if it has any.
    static let eyes: [[Eye]] = [
        [Eye(x: -30, y: -30, r: 25), Eye(x: 22, y: -28, r: 22)],
        [],
        [],
        [Eye(x: -22, y: -46, r: 15), Eye(x: 12, y: -48, r: 14)],
        [Eye(x: -27, y: -40, r: 19), Eye(x: 23, y: -41, r: 18)],
        [],
    ]

    // A pupil is this share of its eye, and travels at most this far from the centre.
    static let pupil = 0.55
    static let pupilTravel = 0.38

    private static let ink = Color(hex: 0x17171a)
    private static let heart = PetRockCarouselShapes.svg(
        "M 0 64 C -40 36 -84 8 -84 -24 C -84 -52 -60 -70 -37 -70 C -19 -70 -6 -59 0 -47 C 6 -59 19 -70 37 -70 C 60 -70 84 -52 84 -24 C 84 8 40 36 0 64 Z"
    )
    private static let giddyMouth = PetRockCarouselShapes.svg("M -48 0 C -30 -6 30 -6 48 0 C 48 50 -48 50 -48 0 Z")
    private static let giddyThroat = PetRockCarouselShapes.svg("M -37 7 C -22 3 22 3 37 7 C 35 38 -35 38 -37 7 Z")
    private static let giddyTongue = PetRockCarouselShapes.svg("M -21 34 C -18 21 18 21 21 34 C 13 41 -13 41 -21 34 Z")

    // Fonts close to the RN version's: a heavy condensed sans, a rounded bold and a heavy sans.
    private static let loveFont = UIFont(name: "AvenirNextCondensed-Heavy", size: 46) ?? .systemFont(ofSize: 46, weight: .heavy)
    private static let tagFont = UIFont(name: "ArialRoundedMTBold", size: 58) ?? .systemFont(ofSize: 58, weight: .heavy)
    private static let zedFont = UIFont(name: "AvenirNext-Heavy", size: 26) ?? .systemFont(ofSize: 26, weight: .heavy)

    /// A Skia blur sigma (rock units here) as a SwiftUI blur radius, which spreads about half as far.
    static func radius(_ sigma: Double) -> Double { sigma * 2 }

    /// Draws with a soft blur, like Skia's normal BlurMask.
    static func blurred(_ ctx: GraphicsContext, _ sigma: Double, opacity: Double = 1, _ draw: (inout GraphicsContext) -> Void) {
        var c = ctx
        c.opacity *= opacity
        c.addFilter(.blur(radius: radius(sigma)))
        draw(&c)
    }

    private static func round(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// Paint softened by a halo of overspray, like a spray can held a little too far away.
    private static func spray(_ ctx: GraphicsContext, _ path: Path, _ color: Color, width: Double? = nil) {
        blurred(ctx, 5, opacity: 0.35) { c in
            if let width { c.stroke(path, with: .color(color), style: round(width + 7)) } else { c.fill(path, with: .color(color)) }
        }
        blurred(ctx, 0.7) { c in
            if let width { c.stroke(path, with: .color(color), style: round(width)) } else { c.fill(path, with: .color(color)) }
        }
    }

    private static func spray(_ ctx: GraphicsContext, _ d: String, _ color: Color, width: Double? = nil) {
        spray(ctx, PetRockCarouselShapes.svg(d), color, width: width)
    }

    /// Text drawn like a spray-painted letter: a dark outline under a coloured fill.
    private static func lettering(_ ctx: GraphicsContext, _ text: Path, fill: Color, outline: Color, width: Double) {
        let stroke = StrokeStyle(lineWidth: width, lineJoin: .round)
        blurred(ctx, 3, opacity: 0.5) { c in
            c.translateBy(x: 2.5, y: 3)
            c.stroke(text, with: .color(outline), style: stroke)
        }
        ctx.stroke(text, with: .color(outline), style: stroke)
        ctx.fill(text, with: .color(fill))
    }

    /// The painted part of a face.
    static func paint(_ face: Int, in ctx: GraphicsContext) {
        switch face {
        case 0:
            // Googly: a blue swoosh hugging the top of the stone and a black tag scrawled below.
            spray(ctx, "M -82 -18 C -78 -64 -28 -88 16 -84 C 54 -80 80 -58 86 -14", Color(hex: 0x3a8ed8), width: 7)
            spray(ctx, "M -70 -6 C -66 -48 -24 -70 14 -68", Color(hex: 0x3a8ed8), width: 3)
            spray(
                ctx,
                "M -56 28 C -62 4 -22 -6 -16 18 C -10 42 -46 52 -40 30 C -34 10 2 2 10 24 C 18 44 -6 54 0 34 C 6 10 40 6 46 28 C 50 48 24 56 30 38 C 34 22 60 22 64 40",
                ink,
                width: 4.5
            )
            spray(ctx, "M -52 54 C -20 64 22 66 58 54", ink, width: 3.5)
        case 1:
            // Grumpy: a yellow disc with brows pulled down and a frown.
            let yellow = Color(hex: 0xf1c42e)
            let brown = Color(hex: 0x2a1f14)
            blurred(ctx, 6, opacity: 0.35) { $0.fill(Path(ellipseIn: CGRect(x: -70, y: -66, width: 140, height: 140)), with: .color(yellow)) }
            blurred(ctx, 0.8) { $0.fill(Path(ellipseIn: CGRect(x: -64, y: -60, width: 128, height: 128)), with: .color(yellow)) }
            spray(ctx, "M -46 -28 L -12 -12", brown, width: 8)
            spray(ctx, "M 46 -30 L 12 -12", brown, width: 8)
            ctx.fill(Path(ellipseIn: CGRect(x: -28, y: -8, width: 12, height: 16)), with: .color(brown))
            ctx.fill(Path(ellipseIn: CGRect(x: 16, y: -8, width: 12, height: 16)), with: .color(brown))
            spray(ctx, "M -27 38 Q 0 14 27 38", brown, width: 7)
        case 2:
            // Smitten: a red heart with LOVE lettered across it.
            spray(ctx, heart, Color(hex: 0xd8343b))
            blurred(ctx, 2, opacity: 0.6) { $0.stroke(heart, with: .color(Color(hex: 0x9d1c24)), lineWidth: 3) }
            let width = PetRockCarouselShapes.text("LOVE", font: loveFont, at: .zero).width
            var turned = ctx
            turned.rotate(by: .radians(-0.08))
            turned.fill(PetRockCarouselShapes.text("LOVE", font: loveFont, at: CGPoint(x: -width / 2, y: 8)).path, with: .color(ink))
            spray(ctx, "M -40 24 Q 0 34 44 20", ink, width: 4)
        case 3:
            // Tagged: bubble letters in teal with a dark outline.
            var turned = ctx
            turned.rotate(by: .radians(-0.12))
            let width = PetRockCarouselShapes.text("blub", font: tagFont, at: .zero).width
            let text = PetRockCarouselShapes.text("blub", font: tagFont, at: CGPoint(x: -width / 2, y: 24)).path
            lettering(turned, text, fill: Color(hex: 0x47b8c6), outline: Color(hex: 0x0f2f3b), width: 9)
            spray(turned, "M -58 40 C -30 50 30 48 60 34", Color(hex: 0x0f2f3b), width: 3)
        case 4:
            // Giddy: a wide purple grin.
            spray(ctx, giddyMouth, Color(hex: 0x8b3f99))
            ctx.fill(giddyThroat, with: .color(Color(hex: 0x3a1346)))
            ctx.fill(giddyTongue, with: .color(Color(hex: 0xe37b9c)))
            spray(ctx, "M -60 -6 Q -54 -14 -46 -10", Color(hex: 0x2d8c5c), width: 5)
            spray(ctx, "M 60 -6 Q 54 -14 46 -10", Color(hex: 0x2d8c5c), width: 5)
        default:
            // Sleepy: closed eyes, rosy cheeks, a small "o" and a few z's.
            spray(ctx, "M -44 -14 Q -27 2 -10 -14", ink, width: 5)
            spray(ctx, "M 10 -14 Q 27 2 44 -14", ink, width: 5)
            spray(ctx, "M -40 -8 L -45 -1 M -27 -5 L -28 3 M -15 -8 L -11 -1", ink, width: 2.5)
            spray(ctx, "M 15 -8 L 11 -1 M 27 -5 L 28 3 M 40 -8 L 45 -1", ink, width: 2.5)
            let rosy = Color(hex: 0xec8a8a)
            blurred(ctx, 6, opacity: 0.75) { c in
                c.fill(Path(ellipseIn: CGRect(x: -52, y: 4, width: 24, height: 24)), with: .color(rosy))
                c.fill(Path(ellipseIn: CGRect(x: 28, y: 4, width: 24, height: 24)), with: .color(rosy))
            }
            ctx.stroke(Path(ellipseIn: CGRect(x: -7, y: 26, width: 14, height: 17)), with: .color(ink), lineWidth: 4)
            let cream = Color(hex: 0xf4f1ea)
            lettering(ctx, PetRockCarouselShapes.text("z", font: zedFont, at: CGPoint(x: 30, y: -42)).path, fill: cream, outline: ink, width: 4)
            var small = ctx
            small.translateBy(x: 52, y: -56)
            small.scaleBy(x: 0.72, y: 0.72)
            lettering(small, PetRockCarouselShapes.text("z", font: zedFont, at: .zero).path, fill: cream, outline: ink, width: 4)
        }
    }

    /// The still part of a face's googly eyes: shadow, white dome and rim. The pupils and the glint
    /// are drawn over it every frame.
    static func eyeDomes(_ face: Int, in ctx: GraphicsContext) {
        for eye in eyes[face] {
            let x = eye.x, y = eye.y, r = eye.r
            blurred(ctx, 3.5) { $0.fill(Path(ellipseIn: CGRect(x: x + 1 - r, y: y + 3 - r, width: r * 2, height: r * 2)), with: .color(.rgba(30, 22, 14, 0.45))) }
            let dome = Gradient(stops: [
                .init(color: Color(hex: 0xffffff), location: 0),
                .init(color: Color(hex: 0xf3f1ec), location: 0.55),
                .init(color: Color(hex: 0xc7c3bb), location: 1),
            ])
            ctx.fill(
                Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                with: .radialGradient(dome, center: CGPoint(x: x - r * 0.3, y: y - r * 0.35), startRadius: 0, endRadius: r * 1.35)
            )
            let rim = r - 0.5
            ctx.stroke(Path(ellipseIn: CGRect(x: x - rim, y: y - rim, width: rim * 2, height: rim * 2)), with: .color(.rgba(0, 0, 0, 0.22)), lineWidth: 1)
        }
    }

    /// The loose pupils (at `px`, `py`, −1…1 each way in their eye) and the glint over them.
    static func pupils(_ face: Int, px: Double, py: Double, in ctx: GraphicsContext) {
        for eye in eyes[face] {
            let x = eye.x + px * eye.r * pupilTravel
            let y = eye.y + py * eye.r * pupilTravel
            let p = eye.r * pupil
            ctx.fill(Path(ellipseIn: CGRect(x: x - p, y: y - p, width: p * 2, height: p * 2)), with: .color(Color(hex: 0x0c0c0d)))
            let r = eye.r
            ctx.fill(
                Path(ellipseIn: CGRect(x: eye.x - r * 0.62, y: eye.y - r * 0.72, width: r * 0.55, height: r * 0.32)),
                with: .color(.rgba(255, 255, 255, 0.8))
            )
        }
    }
}

// MARK: - Cached layers

/// Everything about a rock that never moves relative to it is drawn once into an image, so a
/// frame only places images: the ground shadow, the shading over the stone (flecks, light, rim),
/// each face's paint and eye domes, a grain texture and the stone photos.
@MainActor
final class PetRockCarouselArt {
    struct RockLayers {
        let path: Path
        let shadow: CGImage?
        let shade: CGImage?
    }

    /// The box every cached rock layer covers, in rock units.
    static let box = CGRect(x: -120, y: -120, width: 240, height: 240)
    static let shadowBox = CGRect(x: -125, y: 20, width: 250, height: 140)
    /// The grain texture: a square this many rock units wide that each rock samples a part of.
    nonisolated static let grainSide = 360.0

    private(set) var rocks: [RockLayers?]
    private(set) var paint: [CGImage?] = Array(repeating: nil, count: PetRockCarouselFaces.count)
    private(set) var domes: [CGImage?] = Array(repeating: nil, count: PetRockCarouselFaces.count)
    private(set) var photos: [CGImage?]
    private(set) var grain: CGImage?
    private var started = false

    init(count: Int) {
        rocks = Array(repeating: nil, count: count)
        photos = Array(repeating: nil, count: count)
    }

    /// Draws every layer once, `pixels` pixels per rock unit, a rock or a face at a time so the
    /// first frames are not held up.
    func prepare(rocks specs: [PetRockCarouselRock], pixels: Double) async {
        guard !started else { return }
        started = true
        for (i, rock) in specs.enumerated() {
            Task {
                let address = "https://images.unsplash.com/\(rock.photo)?auto=format&fit=crop&w=\(PetRockCarouselMetrics.photoWidth)&q=80"
                photos[i] = await Self.stonePhoto(address)
            }
        }
        Task {
            grain = await Task.detached(priority: .userInitiated) { Self.grainTexture(pixelsPerUnit: 3) }.value
        }
        for (i, rock) in specs.enumerated() {
            let path = PetRockCarouselShapes.rock(seed: rock.seed, aspect: rock.aspect)
            let speckles = PetRockCarouselShapes.speckles(seed: rock.seed)
            let shade = Self.render(Self.box, scale: pixels * 0.8) { ctx in
                Self.shade(path: path, speckles: speckles, in: ctx)
            }
            let shadow = Self.render(Self.shadowBox, scale: pixels * 0.5) { ctx in
                Self.groundShadow(aspect: rock.aspect, in: ctx)
            }
            rocks[i] = RockLayers(path: path, shadow: shadow, shade: shade)
            await Task.yield()
            // The faces the rocks open with first, then the rest.
            let face = rock.face
            if paint[face] == nil { renderFace(face, pixels: pixels) }
        }
        for face in 0..<PetRockCarouselFaces.count where paint[face] == nil {
            renderFace(face, pixels: pixels)
            await Task.yield()
        }
    }

    private func renderFace(_ face: Int, pixels: Double) {
        paint[face] = Self.render(Self.box, scale: pixels) { PetRockCarouselFaces.paint(face, in: $0) }
        if !PetRockCarouselFaces.eyes[face].isEmpty {
            domes[face] = Self.render(Self.box, scale: pixels) { PetRockCarouselFaces.eyeDomes(face, in: $0) }
        }
    }

    /// Shadow on the ground: a wide soft one and a tight dark one where the stone touches.
    private static func groundShadow(aspect: Double, in ctx: GraphicsContext) {
        PetRockCarouselFaces.blurred(ctx, 12) {
            $0.fill(Path(ellipseIn: CGRect(x: -78, y: 78 * aspect, width: 156, height: 30)), with: .color(.rgba(70, 56, 40, 0.30)))
        }
        PetRockCarouselFaces.blurred(ctx, 5) {
            $0.fill(Path(ellipseIn: CGRect(x: -52, y: 84 * aspect, width: 104, height: 14)), with: .color(.rgba(50, 40, 28, 0.35)))
        }
    }

    /// Flecks, light from the top left and the edge falling away into shadow, clipped to the stone.
    private static func shade(path: Path, speckles: (dark: Path, light: Path), in context: GraphicsContext) {
        var ctx = context
        ctx.clip(to: path)
        ctx.fill(speckles.dark, with: .color(.rgba(35, 28, 22, 0.38)))
        ctx.fill(speckles.light, with: .color(.rgba(255, 255, 255, 0.32)))
        let light = Gradient(stops: [
            .init(color: .rgba(255, 255, 255, 0.34), location: 0),
            .init(color: .rgba(255, 255, 255, 0.05), location: 0.38),
            .init(color: .rgba(20, 14, 8, 0.2), location: 0.7),
            .init(color: .rgba(20, 14, 8, 0.55), location: 1),
        ])
        ctx.fill(Path(box), with: .radialGradient(light, center: CGPoint(x: -36, y: -48), startRadius: 0, endRadius: 165))
        PetRockCarouselFaces.blurred(ctx, 9) { $0.stroke(path, with: .color(.rgba(40, 30, 20, 0.34)), lineWidth: 22) }
        // A catch of light along the upper left edge.
        PetRockCarouselFaces.blurred(ctx, 4) { c in
            c.translateBy(x: 4, y: 5)
            c.stroke(path, with: .color(.rgba(255, 255, 255, 0.22)), lineWidth: 5)
        }
    }

    /// Draws `rect` (rock units) into an image at `scale` pixels per unit.
    private static func render(_ rect: CGRect, scale: Double, draw: @escaping (GraphicsContext) -> Void) -> CGImage? {
        let canvas = Canvas { context, _ in
            var ctx = context
            ctx.translateBy(x: -rect.minX, y: -rect.minY)
            draw(ctx)
        }
        .frame(width: rect.width, height: rect.height)
        let renderer = ImageRenderer(content: canvas)
        renderer.scale = scale
        renderer.isOpaque = false
        return renderer.cgImage
    }

    /// The stone photo in grey with a little more contrast, so laid over the tone it only adds relief.
    private nonisolated static func stonePhoto(_ address: String) async -> CGImage? {
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let input = CIImage(data: data)
        else { return nil }
        let filter = CIFilter(name: "CIColorMatrix")
        filter?.setValue(input, forKey: kCIInputImageKey)
        let row = CIVector(x: 0.2, y: 0.62, z: 0.06, w: 0)
        filter?.setValue(row, forKey: "inputRVector")
        filter?.setValue(row, forKey: "inputGVector")
        filter?.setValue(row, forKey: "inputBVector")
        filter?.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
        filter?.setValue(CIVector(x: 0.06, y: 0.06, z: 0.06, w: 0), forKey: "inputBiasVector")
        guard let output = filter?.outputImage else { return nil }
        // The matrix works on the stored (gamma) values, as Skia's colour matrix does.
        let context = CIContext(options: [.workingColorSpace: NSNull(), .outputColorSpace: NSNull()])
        return context.createCGImage(output, from: input.extent)
    }

    /// Two octave sums of gradient noise in grey, a coarse mottling and a fine grain, added into one
    /// texture that is laid over the stone and its paint in overlay.
    private nonisolated static func grainTexture(pixelsPerUnit: Double) -> CGImage? {
        let side = Int(grainSide * pixelsPerUnit)
        let noise = PetRockCarouselNoise(seed: 11)
        var pixels = [UInt8](repeating: 255, count: side * side * 4)
        for py in 0..<side {
            for px in 0..<side {
                let x = Double(px) / pixelsPerUnit
                let y = Double(py) / pixelsPerUnit
                let coarse = noise.fractal(x * 0.035, y * 0.035, octaves: 3)
                let fine = noise.fractal(x * 0.45 + 37.1, y * 0.45 + 11.7, octaves: 2)
                // Skia's fractal noise is (sum + 1) / 2 per channel; three channels averaged to grey
                // spread about 0.58 as far.
                let g = 0.5 + 0.5 * 0.58 * (coarse + fine)
                let v = UInt8(max(0, min(255, g * 255)))
                let i = (py * side + px) * 4
                pixels[i] = v
                pixels[i + 1] = v
                pixels[i + 2] = v
            }
        }
        let data = Data(pixels) as CFData
        guard let provider = CGDataProvider(data: data) else { return nil }
        return CGImage(
            width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: side * 4,
            space: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }
}

/// Classic gradient (Perlin) noise with a seeded permutation, summed over octaves.
struct PetRockCarouselNoise {
    private var perm: [Int] = []

    init(seed: Int) {
        var rand = PetRockCarouselRandom(seed)
        var p = Array(0..<256)
        for i in stride(from: 255, to: 0, by: -1) {
            let j = Int(rand.next() * Double(i + 1))
            p.swapAt(i, j)
        }
        perm = p + p
    }

    private func grad(_ hash: Int, _ x: Double, _ y: Double) -> Double {
        switch hash & 7 {
        case 0: return x + y
        case 1: return -x + y
        case 2: return x - y
        case 3: return -x - y
        case 4: return x
        case 5: return -x
        case 6: return y
        default: return -y
        }
    }

    func noise(_ x: Double, _ y: Double) -> Double {
        let fx = floor(x), fy = floor(y)
        let xi = Int(fx) & 255, yi = Int(fy) & 255
        let xf = x - fx, yf = y - fy
        let u = xf * xf * xf * (xf * (xf * 6 - 15) + 10)
        let v = yf * yf * yf * (yf * (yf * 6 - 15) + 10)
        let aa = perm[perm[xi] + yi], ab = perm[perm[xi] + yi + 1]
        let ba = perm[perm[xi + 1] + yi], bb = perm[perm[xi + 1] + yi + 1]
        let x1 = grad(aa, xf, yf) + (grad(ba, xf - 1, yf) - grad(aa, xf, yf)) * u
        let x2 = grad(ab, xf, yf - 1) + (grad(bb, xf - 1, yf - 1) - grad(ab, xf, yf - 1)) * u
        return (x1 + (x2 - x1) * v) * 0.7
    }

    func fractal(_ x: Double, _ y: Double, octaves: Int) -> Double {
        var sum = 0.0, amp = 1.0, f = 1.0
        for _ in 0..<octaves {
            sum += noise(x * f, y * f) * amp
            amp *= 0.5
            f *= 2
        }
        return sum
    }
}
