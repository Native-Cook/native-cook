import SwiftUI
import CoreText

struct GlowToggleChecklistSurface: View {
    let frame: GlowToggleChecklistSimulation
    let size: CGSize
    private static let ink = Color(red: 244.0 / 255, green: 243.0 / 255, blue: 239.0 / 255)
    static let labels = ["DISCIPLINE", "DEDICATION", "HARDWORK", "CONSISTENCY"]

    static func scale(_ size: CGSize) -> Double {
        min(1, size.width / 402, size.height / 700)
    }

    var body: some View {
        ZStack {
            Canvas { context, canvasSize in
                context.fill(Path(CGRect(origin: .zero, size: canvasSize)), with: .shader(shader(halo: true)))
            }
            .frame(width: size.width / 3, height: size.height / 3)
            .scaleEffect(3)
            Canvas { context, canvasSize in
                context.fill(Path(CGRect(origin: .zero, size: canvasSize)), with: .shader(shader(halo: false)))
                drawText(context)
            }
        }
        .frame(width: size.width, height: size.height)
        .compositingGroup()
        .colorEffect(ShaderLibrary.glowToggleChecklistOutput())
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func shader(halo: Bool) -> Shader {
        let unit = GlowToggleChecklistSimulation.unit
        let k = Self.scale(size)
        let calm = unit(frame.tip)
        let open = unit(frame.open)
        let present = unit(frame.present)
        let baseReach = 60 + 24 * open + 12 * max(0, min(3, frame.fill))
        let reach = baseReach + (160 - baseReach) * calm + max(0, frame.tip - 1) * 160
        let colour = GlowToggleChecklistColour.colour(frame.hue, tip: frame.tip)
        let remainder = GlowToggleChecklistColour.colour(frame.hue, tip: 0)
        let arguments: [Shader.Argument] = [
            .float2(size),
            .float4(Float(size.width / 2), Float(size.height / 2 - 158 * k * open), Float(k * (1 - 0.05 * unit(frame.press))), Float(present)),
            .float4(Float(open), Float(frame.still ? 0 : frame.clock), frame.still ? 1 : 0, Float(calm)),
            .float4(Float(144 * present), Float(min(22, 144 * present)), Float(30 + 316 * open), 0),
            .float3(Float(colour[0]), Float(colour[1]), Float(colour[2])),
            .float3(Float(remainder[0]), Float(remainder[1]), Float(remainder[2])),
            .float4(Float(frame.fill), Float(calm), Float(frame.still ? 0 : frame.surge), Float(unit(frame.flash))),
            .float4(Float(frame.still ? -1 : frame.glare), Float(reach), Float(baseReach), halo ? 3 : 1),
            .float4(Float(frame.knobs[0].lead), Float(frame.knobs[1].lead), Float(frame.knobs[2].lead), Float(frame.knobs[3].lead)),
            .float4(Float(frame.knobs[0].trail), Float(frame.knobs[1].trail), Float(frame.knobs[2].trail), Float(frame.knobs[3].trail)),
            .float4(Float(frame.knobs[0].hold), Float(frame.knobs[1].hold), Float(frame.knobs[2].hold), Float(frame.knobs[3].hold)),
        ]
        return Shader(function: ShaderFunction(library: .default, name: halo ? "glowToggleChecklistHalo" : "glowToggleChecklistGlass"), arguments: arguments)
    }

    private func drawText(_ context: GraphicsContext) {
        let k = Self.scale(size)
        let scale = k * (1 - 0.05 * frame.press)
        var text = context
        text.translateBy(x: size.width / 2, y: size.height / 2 - 158 * k * frame.open)
        text.scaleBy(x: scale, y: scale)
        let visibility = GlowToggleChecklistSimulation.unit((frame.present - 0.02) / 0.18)
        text.opacity = visibility * visibility * (3 - 2 * visibility)
        var rows = text
        rows.clip(to: Path(CGRect(x: -144, y: 30, width: 288, height: 376)))
        let paint = GraphicsContext.Shading.linearGradient(
            Gradient(colors: [Self.ink.opacity(0), Self.ink]),
            startPoint: CGPoint(x: 0, y: 30), endPoint: CGPoint(x: 0, y: 40)
        )
        for i in 0..<4 {
            let rowY = 30 + 316 * frame.open - [278.0, 210, 142, 74][i]
            var transform = CGAffineTransform(translationX: -118, y: rowY)
            let path = Path(GlowToggleChecklistText.rows[i].copy(using: &transform)!)
            drawGlow(path, context: rows, paint: paint)
        }
        let width = 288 * GlowToggleChecklistSimulation.unit(frame.present)
        text.clip(to: Path(roundedRect: CGRect(x: -width / 2, y: -30, width: width, height: 60),
                           cornerRadius: min(22, width / 2)))
        for (i, glyph) in GlowToggleChecklistText.title.enumerated() {
            let progress = GlowToggleChecklistSimulation.unit(frame.letters[i])
            var letter = text
            letter.opacity *= min(1, progress / 0.4)
            letter.translateBy(x: glyph.centreX, y: 0)
            letter.scaleBy(x: 0.25 + 0.75 * progress, y: 0.25 + 0.75 * progress)
            letter.translateBy(x: -glyph.centreX, y: 0)
            drawGlow(Path(glyph.path), context: letter, paint: .color(Self.ink))
        }
    }

    private func drawGlow(_ path: Path, context: GraphicsContext, paint: GraphicsContext.Shading) {
        for (radius, alpha) in [(5.0, 0.30), (1.5, 0.35)] {
            var glow = context
            glow.opacity *= alpha
            glow.addFilter(.blur(radius: radius))
            glow.fill(path, with: paint)
        }
        context.fill(path, with: paint)
    }
}

private enum GlowToggleChecklistText {
    struct GlowToggleChecklistGlyph {
        let path: CGPath
        let centreX: Double
    }
    static let font: CGFont = {
        let url = Bundle.main.url(forResource: "GlowToggleChecklist-Inter-Bold", withExtension: "ttf")!
        return CGFont(CGDataProvider(url: url as CFURL)!)!
    }()
    static let title = layout("SUCCESS", size: 40, tracking: 4.5, centred: true)
    static let rows = GlowToggleChecklistSurface.labels.map { label in
        let path = CGMutablePath()
        for glyph in layout(label, size: 21, tracking: 2.8, centred: false) { path.addPath(glyph.path) }
        return path.copy()!
    }

    /// Centre actual ink, without a trailing tracking space or a font line box.
    static func layout(_ text: String, size: Double, tracking: Double, centred: Bool) -> [GlowToggleChecklistGlyph] {
        let ctFont = CTFontCreateWithGraphicsFont(font, size, nil, nil)
        let chars = Array(text.utf16)
        var ids = Array(repeating: CGGlyph(), count: chars.count)
        CTFontGetGlyphsForCharacters(ctFont, chars, &ids, chars.count)
        var advances = Array(repeating: CGSize.zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(ctFont, .horizontal, ids, &advances, ids.count)
        var cursor = 0.0
        var bounds = CGRect.null
        var paths: [CGPath] = []
        var metrics: [CGRect] = []
        for (i, id) in ids.enumerated() {
            var flip = CGAffineTransform(scaleX: 1, y: -1)
            let glyph = CTFontCreatePathForGlyph(ctFont, id, &flip)!
            // Skia measures integer ink bounds, while keeping fractional advances.
            let ink = glyph.boundingBoxOfPath.integral.offsetBy(dx: cursor, dy: 0)
            var position = CGAffineTransform(translationX: cursor, y: 0)
            paths.append(glyph.copy(using: &position)!)
            metrics.append(ink)
            bounds = bounds.union(ink)
            cursor += advances[i].width + tracking
        }
        let shiftX = centred ? -bounds.midX : -bounds.minX
        var shift = CGAffineTransform(translationX: shiftX, y: -bounds.midY)
        return paths.enumerated().map { index, path in
            GlowToggleChecklistGlyph(path: path.copy(using: &shift)!, centreX: metrics[index].midX + shiftX)
        }
    }
}

private enum GlowToggleChecklistColour {
    static let hues: [[Double]] = [[1, 0.231, 0.231], [0.91, 0.19, 0.55], [0.47, 0.33, 1], [0.15, 0.745, 0.705], [0.18, 0.878, 0.416]]

    static func lab(_ rgb: [Double]) -> [Double] {
        let c = rgb.map { $0 <= 0.04045 ? $0 / 12.92 : pow(($0 + 0.055) / 1.055, 2.4) }
        let l = cbrt(0.4122214708 * c[0] + 0.5363325363 * c[1] + 0.0514459929 * c[2])
        let m = cbrt(0.2119034982 * c[0] + 0.6806995451 * c[1] + 0.1073969566 * c[2])
        let s = cbrt(0.0883024619 * c[0] + 0.2817188376 * c[1] + 0.6299787005 * c[2])
        return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s]
    }

    static func rgb(_ lab: [Double]) -> [Double] {
        let l = pow(lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2], 3)
        let m = pow(lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2], 3)
        let s = pow(lab[0] - 0.0894841775 * lab[1] - 1.291485548 * lab[2], 3)
        return [4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s].map {
            GlowToggleChecklistSimulation.unit($0 <= 0.0031308 ? $0 * 12.92 : 1.055 * pow(max(0, $0), 1 / 2.4) - 0.055)
        }
    }

    static func between(_ a: [Double], _ b: [Double], _ t: Double) -> [Double] {
        let left = lab(a)
        let right = lab(b)
        return rgb((0..<3).map { left[$0] + (right[$0] - left[$0]) * t })
    }

    static func colour(_ hue: Double, tip: Double) -> [Double] {
        let h = max(0, min(3, hue))
        let i = min(2, Int(floor(h)))
        return between(between(hues[i], hues[i + 1], h - Double(i)), hues[4], GlowToggleChecklistSimulation.unit(tip))
    }
}
