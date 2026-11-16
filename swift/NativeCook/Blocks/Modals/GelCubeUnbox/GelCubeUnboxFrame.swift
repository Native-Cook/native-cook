import SwiftUI

struct GelCubeUnboxGeometry {
    let width: Double, height: Double, scale: Double, ox: Double, oy: Double
    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width; self.height = height
        let s = min(width / 402, (height - top - bottom) / (804 - 62))
        scale = s; ox = (width - 402 * s) / 2; oy = top - 62 * s
    }
    func x(_ x: Double) -> Double { ox + x * scale }
    func y(_ y: Double) -> Double { oy + y * scale }
}

struct GelCubeUnboxTokens {
    var font: String?
    var ink: UInt32 = 0xF7F9FD, mute: UInt32 = 0xD6DEEF
    var glassFill = 0.1, glassStroke = 0.35, radius = 48.0
}

/// A single opaque, full-screen material pass prevents canvas/native surface seams.
struct GelCubeUnboxFrame: View {
    let scene: GelCubeUnboxMotion.GelCubeUnboxScene
    let geometry: GelCubeUnboxGeometry
    let palette: [Double]
    let showClose: Bool, showDetails: Bool, reduced: Bool
    let size: Double
    let tokens: GelCubeUnboxTokens

    var body: some View {
        Rectangle().fill(.white)
            .colorEffect(ShaderLibrary.gelCubeUnbox(.floatArray(uniforms)))
            .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
            .accessibilityHidden(true)
    }
    private var uniforms: [Float] {
        let g = geometry, s = scene
        var values: [Double] = [g.ox, g.oy, g.scale, (showClose ? 1 : 0) + (showDetails ? 2 : 0),
                                max(0.4, size / 215), tokens.glassFill, tokens.glassStroke, tokens.radius,
                                s.progress, s.core, s.fan, s.caustic, s.angle, s.flash, 1, 0.03]
        values += palette
        return values.map(Float.init)
    }
}

struct GelCubeUnboxPrint: View {
    let text: String, pointSize: Double, width: Double
    var lineHeight: Double?
    var tracking = -0.5
    var ink: Color = GelCubeUnboxResources.color(0xF7F9FD)
    var fontName: String?
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        let image = GelCubeUnboxResources.print(text, size: pointSize, width: width,
                                              lineHeight: lineHeight, tracking: tracking, displayScale: displayScale, fontName: fontName)
        Image(uiImage: image).renderingMode(.template)
            .foregroundStyle(ink)
            .frame(width: width, height: image.size.height, alignment: .topLeading)
    }
}

/// Default-size text is printed with the exact RN TextKit configuration in physical points.
struct GelCubeUnboxLabels: View {
    let scene: GelCubeUnboxMotion.GelCubeUnboxScene
    let geometry: GelCubeUnboxGeometry
    let title: String, subtitle: String
    let sealed: GelCubeUnboxCopy, opened: GelCubeUnboxCopy
    let tokens: GelCubeUnboxTokens
    let reduced: Bool, showDetails: Bool
    var fontScale = 1.0
    typealias M = GelCubeUnboxMotion
    typealias R = GelCubeUnboxResources

    var body: some View {
        let g = geometry, s = g.scale, p = scene.textProgress, visual = reduced ? 0 : p
        ZStack(alignment: .topLeading) {
            label(title, 25, 31, 354, R.ink(0xE8EEFA, 0xF7F9FD, M.lightAt(p, 0.1)))
                .shadow(color: R.color(0x142347, 0.45 * M.lightAt(p, 0.1)), radius: 6 * s, x: 0, y: 3 * s)
                .offset(x: g.x(24), y: g.y(380))
                .accessibilityLabel(title).accessibilityAddTraits(.isHeader)
            label(subtitle, 17, 24, 362, R.ink(tokens.mute, 0xF7F9FD, M.lightAt(p, 0.16)))
                .offset(x: g.x(20), y: g.y(451)).accessibilityLabel(subtitle)
            action
            if showDetails {
                pair(sealed.chip, opened.chip, 15, 20, 92,
                     R.ink(tokens.ink, 0x24385D, M.lightAt(visual, 0.52)))
                    .offset(x: g.x(161), y: g.y(636))
                pair(sealed.sheetTitle, opened.sheetTitle, 25, 32, 370,
                     R.ink(tokens.ink, 0x24385D, M.lightAt(visual, 0.62)))
                    .offset(x: g.x(16), y: g.y(680))
                pair(sealed.sheetBody, opened.sheetBody, 17, 23, 354,
                     R.ink(tokens.mute, 0x283D62, M.lightAt(visual, 0.73)))
                    .offset(x: g.x(24), y: g.y(720))
            }
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }
    private var action: some View {
        let g = geometry, s = g.scale
        let ink = R.ink(tokens.ink, 0x24385D, M.lightAt(reduced ? 0 : scene.textProgress, 0.28))
        return ZStack(alignment: .center) {
            label(sealed.action, 21, nil, 370, ink).opacity(1 - scene.content)
            label(opened.action, 21, nil, 370, ink).opacity(scene.content)
        }
        .frame(width: 370 * s, height: 58 * s)
        .scaleEffect(scene.actionScale)
        .offset(x: g.x(16), y: g.y(497))
        .accessibilityHidden(true)
    }
    private func label(_ text: String, _ size: Double, _ line: Double?, _ width: Double, _ ink: Color) -> some View {
        let s = geometry.scale
        return GelCubeUnboxPrint(text: text, pointSize: size * s * fontScale, width: width * s,
                                 lineHeight: line.map { $0 * s * fontScale }, tracking: -0.5 * s, ink: ink, fontName: tokens.font)
    }
    private func pair(_ closed: String, _ open: String, _ size: Double, _ line: Double,
                      _ width: Double, _ ink: Color) -> some View {
        ZStack(alignment: .topLeading) {
            label(closed, size, line, width, ink).opacity(1 - scene.content)
            label(open, size, line, width, ink).opacity(scene.content)
        }.accessibilityHidden(true)
    }
}
