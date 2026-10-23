import Metal
import SwiftUI
import UIKit

/// The same 180-point curve, band sampler and layer order as Hero.tsx.
struct VitalsChipMorphHero: View {
    let blend: VitalsChipMorphMotion.Blend
    let quiet: Bool
    let k: Double

    private typealias M = VitalsChipMorphMotion
    private typealias T = VitalsChipMorphTheme
    // Metal is compiled at build time. Resolve its availability lazily once;
    // an unavailable device or library keeps the unfiltered, fully working morph.
    private static let liquidAvailable: Bool = {
        guard let device = MTLCreateSystemDefaultDevice(),
              let library = try? device.makeDefaultLibrary(bundle: .main) else { return false }
        return library.functionNames.contains("vitalsChipMorphLiquid")
    }()
    private static let heart: UIImage = {
        guard let url = Bundle.main.url(forResource: "VitalsChipMorph-heart", withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }()

    var body: some View {
        let lo = quiet ? (blend.u < 0.5 ? blend.lo : blend.hi) : blend.lo
        let hi = quiet ? lo : blend.hi
        let u = quiet ? 0 : blend.u
        let alpha = quiet ? abs(1 - 2 * blend.u) : 1
        let f = M.heroFrame(lo, hi, u)
        let points = M.heroPoints(lo, hi, f.sh)
        let closed = M.lerp(Double(M.CLOSED[lo]), Double(M.CLOSED[hi]), f.sh)
        let main = Self.path(points, closed: closed > 0.999)
        let color = Self.mix(M.MAIN_COLOUR[lo], M.MAIN_COLOUR[hi], f.colourMix)
        let liquid = quiet ? 0 : M.liquidAmount(lo, hi, u)
        let wobble = quiet ? 0 : M.wobbleAmount(blend.speed)
        let ripple = M.rippleOf(quiet ? -1 : blend.beatCount)
        let rippleAmount = ripple.amp * f.heartImage
        let materialActive = liquid > 0.0005 || wobble > 0.0005 || rippleAmount > 0

        let halo = Canvas { context, _ in
            context.scaleBy(x: k, y: k)
            context.translateBy(x: 0, y: -400)
            context.opacity = alpha
            var body = context
            body.translateBy(x: 601.5, y: 863)
            body.scaleBy(x: 1 + blend.beat, y: 1 + blend.beat)
            body.translateBy(x: -601.5, y: -863)
            if f.halo > 0 {
                body.drawLayer { glow in
                    glow.opacity *= f.halo * (0.72 + 2.5 * blend.beat)
                    glow.addFilter(.blur(radius: 30))
                    glow.fill(Self.halo, with: .color(T.colors.heartHalo))
                }
            }
        }

        let content = Canvas { context, _ in
            context.scaleBy(x: k, y: k)
            context.translateBy(x: 0, y: -400)
            context.opacity = alpha
            var body = context
            body.translateBy(x: 601.5, y: 863)
            body.scaleBy(x: 1 + blend.beat, y: 1 + blend.beat)
            body.translateBy(x: -601.5, y: -863)
            if f.fill * (1 - f.heartImage) > 0 {
                body.fill(main, with: .color(color.opacity(f.fill * (1 - f.heartImage))))
            }
            if f.width > 0.5 {
                body.stroke(main, with: .color(color),
                            style: StrokeStyle(lineWidth: f.width, lineCap: .round, lineJoin: .round))
            }
            if f.heartImage > 0 {
                var imageContext = body
                imageContext.opacity *= f.heartImage
                imageContext.draw(Image(uiImage: Self.heart),
                                  in: CGRect(x: 213, y: 488, width: 780, height: 750))
            }
            Self.ring(&context, radius: 359 * f.ringR0, progress: f.ringP0, opacity: f.ringO0,
                      track: T.colors.exerciseTrack, start: T.colors.exerciseStart, end: T.colors.exerciseEnd, stop: 0.63)
            Self.ring(&context, radius: 359 * f.ringR1, progress: f.ringP1, opacity: f.ringO1,
                      track: T.colors.standTrack, start: T.colors.standStart, end: T.colors.standEnd, stop: 0.33)
            if f.accentOpacity > 0 {
                let half = f.accentWidth / 2
                let band = Self.bandPath(points, f.t0, f.t1, closed, half,
                                         half * f.accentRed, M.lerp(40, half, f.accentRed))
                context.drawLayer { accent in
                    accent.opacity *= f.accentOpacity
                    accent.addFilter(.blur(radius: max(M.lerp(5, 0, f.accentRed), 0.01)))
                    accent.fill(band, with: Self.sweep(
                        Self.mix(T.colors.segment, T.colors.moveStart, f.accentRed),
                        Self.mix(T.colors.segment, T.colors.moveEnd, f.accentRed), stop: 0.75))
                }
            }
            if f.dotOpacity > 0 {
                let dot = Self.pointAt(points, max(f.t1 - 0.0335 * (1 - f.accentRed), f.t0), closed)
                let glowR = f.dotD / 76 * 75
                context.drawLayer { glow in
                    glow.opacity *= 0.9 * f.dotOpacity
                    glow.addFilter(.blur(radius: 10))
                    glow.fill(Path(ellipseIn: CGRect(x: dot.x - glowR, y: dot.y - glowR,
                                                    width: 2 * glowR, height: 2 * glowR)),
                              with: .color(T.colors.blueGlow))
                }
                let r = f.dotD / 2
                context.fill(Path(ellipseIn: CGRect(x: dot.x - r, y: dot.y - r, width: 2 * r, height: 2 * r)),
                             with: .color(T.colors.blue.opacity(f.dotOpacity)))
            }
            context.stroke(Self.glyphs, with: .color(T.colors.glyph.opacity(0.86 * f.arrows)),
                           style: StrokeStyle(lineWidth: 7.5, lineCap: .round, lineJoin: .round))
        }

        ZStack(alignment: .topLeading) {
            halo
            if materialActive && Self.liquidAvailable {
                content
                    .drawingGroup(opaque: false, colorMode: .nonLinear)
                    .layerEffect(
                        ShaderLibrary.vitalsChipMorphLiquid(
                            .float(k),
                            .float(blend.fxTime),
                            .float(liquid),
                            .float(wobble),
                            .float(liquid),
                            .float2(T.liquidLook.gooCentre),
                            .float2(CGPoint(x: T.layout.heart.cx, y: T.layout.heart.cy)),
                            .float(ripple.age),
                            .float(rippleAmount)
                        ),
                        maxSampleOffset: CGSize(
                            width: T.liquidLook.sampleRadius * k,
                            height: T.liquidLook.sampleRadius * k
                        )
                    )
            } else {
                content
            }
        }
        .frame(width: 1206 * k, height: 1020 * k, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// Reanimated's default RGB interpolation applies gamma 2.2 before interpolating.
    static func mix(_ a: Color, _ b: Color, _ p: Double) -> Color {
        var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
        var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
        UIColor(a).getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        UIColor(b).getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        func channel(_ x: Double, _ y: Double) -> Double {
            // interpolateColor packs to 8-bit sRGB.
            (pow(max(0, M.lerp(pow(x, 2.2), pow(y, 2.2), p)), 1 / 2.2) * 255).rounded() / 255
        }
        return Color(.sRGB, red: channel(ar, br), green: channel(ag, bg),
                     blue: channel(ab, bb), opacity: M.lerp(aa, ba, p))
    }

    static func pointAt(_ points: [Double], _ t: Double, _ closed: Double) -> CGPoint {
        let span = 179 + closed
        var f = t * span
        if closed > 0.5 { f = (f.truncatingRemainder(dividingBy: 180) + 180).truncatingRemainder(dividingBy: 180) }
        else { f = min(max(f, 0), 179) }
        let i = Int(floor(f)), j = (i + 1) % 180, w = f - floor(f)
        return CGPoint(x: M.lerp(points[2 * i], points[2 * j], w),
                       y: M.lerp(points[2 * i + 1], points[2 * j + 1], w))
    }

    static func path(_ points: [Double], closed: Bool) -> Path {
        Path { path in
            path.move(to: CGPoint(x: points[0], y: points[1]))
            for i in 1..<180 { path.addLine(to: CGPoint(x: points[2 * i], y: points[2 * i + 1])) }
            if closed { path.closeSubpath() }
        }
    }

    static func bandPath(_ points: [Double], _ t0: Double, _ t1: Double, _ closed: Double,
                         _ half: Double, _ extend: Double, _ corner: Double) -> Path {
        guard t1 - t0 > 0.0005, half > 0.01 else { return Path() }
        let span = 179 + closed
        var samples = [pointAt(points, t0, closed)]
        let first = Int(floor(t0 * span)) + 1, last = Int(ceil(t1 * span)) - 1
        if first <= last { for j in first...last { samples.append(pointAt(points, Double(j) / span, closed)) } }
        samples.append(pointAt(points, t1, closed))
        var normals: [CGPoint] = []
        for i in samples.indices {
            let a = samples[max(i - 1, 0)], b = samples[min(i + 1, samples.count - 1)]
            let dx = b.x - a.x, dy = b.y - a.y, length = max(hypot(dx, dy), 1e-20)
            normals.append(CGPoint(x: -dy / length, y: dx / length))
            // Preserve RN's sequential endpoint mutation before computing the next normal.
            if i == 0 || i == samples.count - 1 {
                let sign = i == 0 ? -1.0 : 1.0
                samples[i].x += sign * dx / length * extend
                samples[i].y += sign * dy / length * extend
            }
        }
        let n = samples.count - 1, c = min(max(corner, 0), half)
        func side(_ i: Int, _ amount: Double) -> CGPoint {
            CGPoint(x: samples[i].x + normals[i].x * amount, y: samples[i].y + normals[i].y * amount)
        }
        func tangent(_ point: CGPoint, _ i: Int, _ amount: Double) -> CGPoint {
            CGPoint(x: point.x + normals[i].y * amount, y: point.y - normals[i].x * amount)
        }
        let l0 = side(0, half), r0 = side(0, -half), l1 = side(n, half), r1 = side(n, -half)
        var p = Path()
        p.move(to: tangent(l0, 0, c))
        if n > 1 { for i in 1..<n { p.addLine(to: side(i, half)) } }
        p.addLine(to: tangent(l1, n, -c))
        conic(&p, control: l1, end: side(n, half - c))
        p.addLine(to: side(n, -half + c))
        conic(&p, control: r1, end: tangent(r1, n, -c))
        if n > 1 { for i in stride(from: n - 1, through: 1, by: -1) { p.addLine(to: side(i, -half)) } }
        p.addLine(to: tangent(r0, 0, c))
        conic(&p, control: r0, end: side(0, -half + c))
        p.addLine(to: side(0, half - c))
        conic(&p, control: l0, end: tangent(l0, 0, c))
        p.closeSubpath()
        return p
    }

    /// Core Graphics has no rational-quadratic command. Evaluate the exact Skia conic
    /// (weight √½) at 64 subdivisions; maximum chord error is below 0.006 ref px.
    private static func conic(_ path: inout Path, control: CGPoint, end: CGPoint) {
        guard let start = path.currentPoint else { return }
        let w = sqrt(0.5)
        for i in 1...64 {
            let t = Double(i) / 64, a = (1 - t) * (1 - t), b = 2 * w * t * (1 - t), c = t * t
            path.addLine(to: CGPoint(x: (a * start.x + b * control.x + c * end.x) / (a + b + c),
                                    y: (a * start.y + b * control.y + c * end.y) / (a + b + c)))
        }
    }

    private static func sweep(_ start: Color, _ end: Color, stop: Double) -> GraphicsContext.Shading {
        .conicGradient(Gradient(stops: [.init(color: start, location: 0),
                                        .init(color: end, location: stop), .init(color: end, location: 1)]),
                       center: CGPoint(x: 608, y: 851.5), angle: .degrees(-90))
    }

    private static func ring(_ context: inout GraphicsContext, radius: Double, progress: Double,
                             opacity: Double, track: Color, start: Color, end: Color, stop: Double) {
        guard opacity > 0 else { return }
        var c = context
        c.opacity *= opacity
        c.stroke(Path(ellipseIn: CGRect(x: 608 - radius, y: 851.5 - radius,
                                       width: 2 * radius, height: 2 * radius)),
                 with: .color(track), lineWidth: 88)
        guard progress > 0.0005 else { return }
        var arc = Path()
        arc.addArc(center: CGPoint(x: 608, y: 851.5), radius: radius,
                   startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * min(progress, 0.9999)), clockwise: false)
        c.stroke(arc, with: sweep(start, end, stop: stop), style: StrokeStyle(lineWidth: 88, lineCap: .round))
    }

    private static let halo: Path = {
        var points = M.HEART
        for i in 0..<180 {
            points[2 * i] = 601.5 + (points[2 * i] - 601.5) * 1.22
            points[2 * i + 1] = 863 + (points[2 * i + 1] - 863) * 1.22
        }
        return path(points, closed: true)
    }()

    private static let glyphs: Path = Path { p in
        func line(_ points: [(Double, Double)]) {
            p.move(to: CGPoint(x: points[0].0, y: points[0].1))
            for point in points.dropFirst() { p.addLine(to: CGPoint(x: point.0, y: point.1)) }
        }
        let x = 608.0
        var y = 851.5 - 359
        line([(x - 14, y), (x + 14, y)])
        line([(x + 3, y - 11), (x + 14, y), (x + 3, y + 11)])
        y = 851.5 - 262
        line([(x - 14, y - 11), (x - 3, y), (x - 14, y + 11)])
        line([(x + 1, y - 11), (x + 12, y), (x + 1, y + 11)])
        y = 851.5 - 165
        line([(x, y + 14), (x, y - 13)])
        line([(x - 11, y - 2), (x, y - 13), (x + 11, y - 2)])
    }
}
