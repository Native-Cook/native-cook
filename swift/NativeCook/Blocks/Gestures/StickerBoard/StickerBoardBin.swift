import CoreText
import SwiftUI

/// Everything about the bin that moves, kept as springs (value and speed) and a few flags.
struct StickerBoardBinState {
    var show = 0.0
    var showV = 0.0
    var lid = 0.0
    var lidV = 0.0
    var lean = 0.0
    var leanV = 0.0
    var grow = 1.0
    var growV = 0.0
    var squash = 0.0
    var squashV = 0.0
    var wobble = 0.0
    var wobbleV = 0.0
    var magnet = 0.0
    var magnetV = 0.0
    // A loose sticker is within reach and held over the mouth.
    var snapped = false
    // A ball has gone in and the lid is slamming; whether it has hit, and who threw the ball.
    var landed = false
    var slam = false
    var slamHit = false
    var byUser = false
    // Seconds the bin stays up after the slam.
    var linger = 0.0
    // Stickers thrown away (shown modulo 100), the number before, the roll between them, and balls
    // that went in since the last slam.
    var count = 0
    var prev = 0
    var roll = 1.0
    var pending = 0

    mutating func resetCount() {
        count = 0
        prev = 0
        roll = 1
        pending = 0
    }
}

/// Where the bin stands on the screen and how it is turned and scaled, from its springs.
struct StickerBoardBinFrame {
    var x: Double
    var y: Double
    var rot: Double
    var sx: Double
    var sy: Double
    var alpha: Double

    /// The transform that draws the bin from its own units.
    var transform: CGAffineTransform {
        CGAffineTransform(translationX: x, y: y).rotated(by: rot * .pi / 180).scaledBy(x: sx, y: sy)
    }

    /// A point in the bin's own units on the screen.
    func point(_ lx: Double, _ ly: Double) -> CGPoint {
        CGPoint(x: lx, y: ly).applying(transform)
    }
}

/**
 * The bin: a small steel pedal bin drawn in vector at the bottom of the board. Its units are points
 * of a 402 pt wide screen, measured from the middle of its base, up being negative.
 */
@MainActor
enum StickerBoardBinArt {
    // Body: radius at the rim and at the foot, height, and how flat its round top and foot look.
    static let topR = 28.0
    static let footR = 24.5
    static let body = 64.0
    static let tilt = 0.28
    static let side = (1 - tilt * tilt).squareRoot()
    // The lid: a little wider than the rim, its thickness, and how far it opens on its hinge (degrees).
    static let lidR = 29.6
    static let lidThick = 4.5
    static let lidOpen = 100.0
    static let foot = 6.0
    // The base stands this far above the bottom safe area; tucked away it sits this far below the screen.
    static let floor = 18.0
    static let tuck = 120.0
    // Where a sticker hangs over the bin as it is pulled in, and where a paper ball goes in.
    static let hover = 72.0
    static let mouthDrop = 4.0
    // The counter plate on the front, and the roll of its digits.
    static let plateW = 28.0
    static let plateH = 16.0
    static let plateY = -body * 0.46
    static let rollTime = 0.34

    static let digits = CTFontCreateWithName("AvenirNext-Bold" as CFString, 11.5, nil)

    static func frame(_ b: StickerBoardBinState, k: Double, width: Double, height: Double, bottom: Double, reduced: Bool) -> StickerBoardBinFrame {
        let baseY = height - bottom - floor * k
        if reduced {
            return StickerBoardBinFrame(x: width / 2, y: baseY, rot: 0, sx: k, sy: k, alpha: min(1, max(0, b.show)))
        }
        let g = max(0.2, b.grow)
        return StickerBoardBinFrame(
            x: width / 2,
            y: baseY + (1 - b.show) * (bottom + tuck * k),
            rot: b.lean + b.wobble,
            sx: k * g * (1 + 0.5 * b.squash),
            sy: k * g * (1 - b.squash),
            alpha: 1
        )
    }

    /// An arc of the oval in `rect`, from `start` turning `sweep` degrees (positive is clockwise on screen).
    private static func arc(_ path: inout Path, _ rect: CGRect, _ start: Double, _ sweep: Double) {
        path.addArc(
            center: .zero,
            radius: 1,
            startAngle: .degrees(start),
            endAngle: .degrees(start + sweep),
            clockwise: sweep < 0,
            transform: CGAffineTransform(translationX: rect.midX, y: rect.midY).scaledBy(x: rect.width / 2, y: rect.height / 2)
        )
    }

    private static func oval(_ r: Double, _ cy: Double) -> CGRect {
        CGRect(x: -r, y: cy - r * tilt, width: r * 2, height: r * 2 * tilt)
    }

    static let bodyPath: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -topR, y: -body))
        p.addLine(to: CGPoint(x: -footR, y: 0))
        arc(&p, oval(footR, 0), 180, -180)
        p.addLine(to: CGPoint(x: topR, y: -body))
        arc(&p, oval(topR, -body), 0, -180)
        p.closeSubpath()
        return p
    }()

    // The dark plastic foot ring: between the foot and a line `foot` above it.
    private static let footRingR = footR + (topR - footR) * foot / body

    static let footRing: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -footRingR, y: -foot))
        arc(&p, oval(footRingR, -foot), 180, -180)
        p.addLine(to: CGPoint(x: footR, y: 0))
        arc(&p, oval(footR, 0), 0, 180)
        p.closeSubpath()
        return p
    }()

    static let footEdge: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -footRingR, y: -foot))
        arc(&p, oval(footRingR, -foot), 180, -180)
        return p
    }()

    // A bead just under the rim.
    static let bead: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -topR + 0.3, y: -body + 3.2))
        arc(&p, oval(topR - 0.3, -body + 3.2), 180, -180)
        return p
    }()

    static let rim = Path(ellipseIn: oval(topR + 0.9, -body))
    static let mouth = Path(ellipseIn: oval(topR - 2, -body))

    // A ball going in is seen above the rim and through the mouth, never through the front.
    static let insideClip: Path = {
        var p = Path(CGRect(x: -400, y: -1000, width: 800, height: 1000 - body))
        p.addEllipse(in: oval(topR - 2, -body))
        return p
    }()

    static let plate = Path(roundedRect: CGRect(x: -plateW / 2, y: plateY - plateH / 2, width: plateW, height: plateH), cornerRadius: 4, style: .circular)

    private static func stops(_ colors: [UInt32], _ at: [Double]) -> Gradient {
        Gradient(stops: zip(colors, at).map { .init(color: Color(hex: $0.0), location: $0.1) })
    }

    static let steel = stops([0x585c61, 0x8a8f94, 0xc8ccd0, 0xf5f7f8, 0xdde0e3, 0xaab0b5, 0x7e8388, 0x63676c, 0x4b4f54], [0, 0.08, 0.21, 0.29, 0.34, 0.55, 0.77, 0.91, 1])
    static let dusk = Gradient(stops: [
        .init(color: .rgba(20, 22, 25, 0), location: 0),
        .init(color: .rgba(20, 22, 25, 0), location: 0.55),
        .init(color: .rgba(20, 22, 25, 0.24), location: 1),
    ])
    static let cavity = stops([0x4f5358, 0x1f2124, 0x0f1011], [0, 0.45, 1])
    static let lidTop = stops([0xfbfcfc, 0xdadee1, 0xaeb3b8, 0x868b90], [0, 0.34, 0.72, 1])
    static let lidBand = stops([0x4c5055, 0x9ea3a8, 0xd9dcdf, 0x7c8186, 0x43474b], [0, 0.2, 0.3, 0.7, 1])
    static let underside = stops([0x3c4044, 0x6a6f74, 0x55595e, 0x3a3d41], [0, 0.3, 0.65, 1])

    private static func circle(_ r: Double, _ dy: Double = 0) -> Path {
        Path(ellipseIn: CGRect(x: -r, y: -r + dy, width: r * 2, height: r * 2))
    }

    private static func circleArc(_ r: Double, _ dy: Double, _ start: Double, _ sweep: Double) -> Path {
        var p = Path()
        arc(&p, CGRect(x: -r, y: -r + dy, width: r * 2, height: r * 2), start, sweep)
        return p
    }

    /// The context seen through the lid's hinge turn: its plane squeezed by `a` about the line `y`.
    private static func lidPlane(_ ctx: GraphicsContext, _ y: Double, _ a: Double) -> GraphicsContext {
        var c = ctx
        c.concatenate(CGAffineTransform(a: 1, b: 0, c: 0, d: a, tx: 0, ty: y))
        return c
    }

    /**
     * The lid, hinged at the back of the rim. Turned back by θ, a point of its plane `y` from its
     * middle lands at `y·(t·cosθ − s·sinθ)` on the screen, so it is drawn as a circle squeezed by
     * that factor; past about 17° the factor turns negative and its underside faces us.
     */
    private static func drawLid(_ ctx: GraphicsContext, _ lid: Double) {
        let th = max(0, lid) * lidOpen * .pi / 180
        var a = tilt * cos(th) - side * sin(th)
        if abs(a) < 0.004 { a = a < 0 ? -0.004 : 0.004 }
        let yb = -body - tilt * lidR + lidR * a
        let yt = yb - (tilt * sin(th) + side * cos(th)) * lidThick
        let band = GraphicsContext.Shading.linearGradient(lidBand, startPoint: CGPoint(x: -lidR, y: 0), endPoint: CGPoint(x: lidR, y: 0))
        let slab = Path(CGRect(x: -lidR, y: min(yb, yt), width: lidR * 2, height: abs(yb - yt)))
        let edge = GraphicsContext.Shading.color(.rgba(36, 38, 42, 0.55))
        if a >= 0 {
            // Top face up: the edge band below, then the domed top.
            lidPlane(ctx, yb, a).fill(circle(lidR), with: band)
            ctx.fill(slab, with: band)
            let top = lidPlane(ctx, yt, a)
            top.fill(circle(lidR), with: .linearGradient(lidTop, startPoint: CGPoint(x: -lidR, y: -lidR), endPoint: CGPoint(x: lidR, y: lidR)))
            top.stroke(circle(lidR), with: edge, lineWidth: 0.7)
            // An embossed ring, and a sharp highlight along the front edge.
            top.stroke(circle(lidR * 0.72), with: .color(.rgba(60, 64, 70, 0.32)), lineWidth: 1)
            top.stroke(circleArc(lidR * 0.72, 1.2, 20, 140), with: .color(.white.opacity(0.7)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
            top.stroke(circleArc(lidR * 0.9, 0, 205, 55), with: .color(.white.opacity(0.95)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        } else {
            // Underside to us: the top face is behind, then the band, then the underside.
            lidPlane(ctx, yt, a).fill(circle(lidR), with: band)
            ctx.fill(slab, with: band)
            let under = lidPlane(ctx, yb, a)
            under.fill(circle(lidR), with: .linearGradient(underside, startPoint: CGPoint(x: -lidR, y: 0), endPoint: CGPoint(x: lidR, y: 0)))
            under.stroke(circle(lidR), with: edge, lineWidth: 0.7)
            under.stroke(circle(lidR * 0.82), with: .color(.rgba(20, 22, 25, 0.4)), lineWidth: 1.2)
        }
    }

    /// The number on the plate, each digit centred in a cell of the same width so it never shifts.
    private static func drawNumber(_ ctx: GraphicsContext, _ n: Int, _ y: Double) {
        let text = "\(max(0, n) % 100)"
        let cell = StickerBoardType.line("0", digits).width
        let x0 = -cell * Double(text.count) / 2
        for (i, ch) in text.enumerated() {
            let glyph = StickerBoardType.line(String(ch), digits)
            ctx.fill(glyph.path.applying(CGAffineTransform(translationX: x0 + cell * Double(i) + (cell - glyph.width) / 2, y: y)), with: .color(Color(hex: 0xf3eee4)))
        }
    }

    /**
     * The bin and its contact shadow. `inside` draws whatever is falling in, in the bin's own
     * units, after the lid (which stands behind the mouth when open) and clipped to the mouth.
     */
    static func draw(_ base: GraphicsContext, _ f: StickerBoardBinFrame, _ b: StickerBoardBinState, k: Double, inside: (GraphicsContext) -> Void) {
        if b.show < 0.004 && abs(b.showV) < 0.01 && f.alpha >= 1 { return }
        if f.alpha <= 0.004 { return }
        func paint(_ ctx: GraphicsContext) {
            let gx = f.sx / max(0.0001, k)
            // The contact shadow stays on the board, under the foot, whatever the bin's lean.
            var soft = ctx
            soft.addFilter(.blur(radius: 5 * k))
            soft.fill(Path(ellipseIn: CGRect(x: f.x - (footR + 9) * f.sx, y: f.y - 3 * k, width: (footR + 9) * 2 * f.sx, height: (footR * tilt * 2 + 8) * k)), with: .color(Color(hex: 0x2a1505, opacity: 0.34)))
            var tight = ctx
            tight.addFilter(.blur(radius: 1.6 * k))
            tight.fill(Path(ellipseIn: CGRect(x: f.x - (footR + 1.5) * gx * k, y: f.y - footR * tilt * k, width: (footR + 1.5) * 2 * gx * k, height: (footR * tilt * 2 + 1.5) * k)), with: .color(Color(hex: 0x1c0e03, opacity: 0.32)))

            var c = ctx
            c.transform = f.transform
            // Body: brushed steel, darker towards the foot, a sharp highlight and a faint second one.
            c.fill(bodyPath, with: .linearGradient(steel, startPoint: CGPoint(x: -topR, y: 0), endPoint: CGPoint(x: topR, y: 0)))
            var clipped = c
            clipped.clip(to: bodyPath)
            clipped.fill(Path(CGRect(x: -topR, y: -body - 20, width: topR * 2, height: body + 30)), with: .linearGradient(dusk, startPoint: CGPoint(x: 0, y: -body), endPoint: CGPoint(x: 0, y: 0)))
            var hi = Path()
            hi.move(to: CGPoint(x: -topR * 0.37, y: -body + 5))
            hi.addLine(to: CGPoint(x: -footR * 0.37, y: -foot - 2))
            clipped.stroke(hi, with: .color(.white.opacity(0.92)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
            var faint = Path()
            faint.move(to: CGPoint(x: topR * 0.6, y: -body + 6))
            faint.addLine(to: CGPoint(x: footR * 0.6, y: -foot - 3))
            clipped.stroke(faint, with: .color(.white.opacity(0.28)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
            clipped.stroke(bead, with: .color(.rgba(30, 32, 36, 0.3)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
            // The foot ring and the pedal, pressed down a little as the lid opens.
            c.fill(footRing, with: .color(Color(hex: 0x26282b)))
            c.stroke(footEdge, with: .color(.white.opacity(0.2)), style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
            c.stroke(bodyPath, with: .color(.rgba(28, 30, 33, 0.55)), style: StrokeStyle(lineWidth: 0.8, lineJoin: .round))
            let down = 1.6 * min(1, max(0, b.lid))
            c.fill(Path(roundedRect: CGRect(x: -11, y: footR * tilt - 3 + down, width: 22, height: 6), cornerRadius: 2.6, style: .circular), with: .color(Color(hex: 0x1d1f22)))
            c.fill(Path(roundedRect: CGRect(x: -10.4, y: footR * tilt - 2.6 + down, width: 20.8, height: 2.2), cornerRadius: 1.1, style: .circular), with: .color(Color(hex: 0x4b4f54)))

            // The counter plate, its digits rolling up to the new number.
            c.fill(plate, with: .color(Color(hex: 0x202226)))
            c.stroke(plate, with: .color(.white.opacity(0.16)), lineWidth: 0.6)
            var digitsCtx = c
            digitsCtx.clip(to: plate)
            let r = min(1, max(0, b.roll))
            let e = r >= 1 ? 1 : StickerBoardMath.easeOutBack(r)
            let baseline = plateY + 4.1
            if r < 1 { drawNumber(digitsCtx, b.prev, baseline - e * plateH) }
            drawNumber(digitsCtx, b.count, baseline + (1 - e) * plateH)

            // The rim, and the dark mouth inside it.
            c.fill(rim, with: .color(Color(hex: 0xe4e7e9)))
            c.fill(mouth, with: .linearGradient(cavity, startPoint: CGPoint(x: 0, y: -body - (topR - 2) * tilt), endPoint: CGPoint(x: 0, y: -body + (topR - 2) * tilt)))
            c.stroke(rim, with: .color(Color(hex: 0x3a3d41)), lineWidth: 0.9)
            c.stroke(mouth, with: .color(.black.opacity(0.5)), lineWidth: 0.7)

            drawLid(c, b.lid)
            // The hinge, seen once the lid stands up behind the mouth.
            if b.lid > 0.3 {
                c.fill(Path(roundedRect: CGRect(x: -8, y: -body - (topR + 0.9) * tilt - 2.4, width: 16, height: 3.6), cornerRadius: 1.2, style: .circular), with: .color(Color(hex: 0x2c2f33)))
            }
            var into = c
            into.clip(to: insideClip)
            inside(into)
        }
        if f.alpha < 0.995 {
            var layer = base
            layer.opacity = f.alpha
            layer.drawLayer { paint($0) }
        } else {
            paint(base)
        }
    }
}
