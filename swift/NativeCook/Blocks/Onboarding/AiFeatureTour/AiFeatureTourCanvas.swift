import CoreText
import SwiftUI

// Native Canvas primitives keep sRGB colours and apply blur to the shape, not a second shadow.
final class AiFeatureTourCanvas {
    private var context: GraphicsContext
    private let resources = AiFeatureTourResources.shared
    init(_ context: GraphicsContext) { self.context = context }
    private func color(_ hex: String, _ alpha: Double = 1) -> Color {
        Color(hex: UInt32(hex.dropFirst(), radix: 16) ?? 0, opacity: min(1, max(0, alpha)))
    }
    func saved(_ draw: () -> Void) {
        let previous = context
        draw()
        context = previous
    }
    func translate(_ x: Double, _ y: Double) { context.translateBy(x: x, y: y) }
    func scale(_ value: Double) { context.scaleBy(x: value, y: value) }
    func rotate(_ degrees: Double) { context.rotate(by: .degrees(degrees)) }
    func layer(_ alpha: Double, _ draw: () -> Void) {
        context.drawLayer { layer in
            let previous = context
            context = layer
            context.opacity = alpha
            draw()
            layer = context
            context = previous
        }
    }
    func path(_ path: CGPath, _ hex: String, _ alpha: Double = 1, width: Double = 0) {
        let ctx = context
        if width > 0 {
            ctx.stroke(Path(path), with: .color(color(hex, alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
        } else { ctx.fill(Path(path), with: .color(color(hex, alpha))) }
    }
    func round(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double,
               _ hex: String, _ alpha: Double = 1, width: Double = 0, blur: Double = 0) {
        if blur > 0, let mask = resources.blurredRound(w, h, radius, blur) {
            let pad = ceil(3 * blur)
            var image = context.resolve(Image(decorative: mask, scale: 1))
            image.shading = .color(color(hex, alpha))
            context.draw(image, in: CGRect(x: x - pad, y: y - pad, width: Double(mask.width), height: Double(mask.height)))
            return
        }
        path(CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: radius, cornerHeight: radius, transform: nil), hex, alpha, width: width)
    }
    func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ hex: String) {
        path(CGPath(rect: CGRect(x: x, y: y, width: w, height: h), transform: nil), hex)
    }
    func circle(_ x: Double, _ y: Double, _ radius: Double, _ hex: String, _ alpha: Double = 1, width: Double = 0) {
        path(CGPath(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2), transform: nil), hex, alpha, width: width)
    }
    func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ hex: String) {
        path(CGPath(ellipseIn: CGRect(x: x, y: y, width: w, height: h), transform: nil), hex)
    }
    func line(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ hex: String, _ width: Double, _ alpha: Double = 1) {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: x1, y: y1))
        path.addLine(to: CGPoint(x: x2, y: y2))
        self.path(path, hex, alpha, width: width)
    }
    func clipRound(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) {
        context.clip(to: Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r))
    }
    func linear(_ rect: CGRect, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double,
                _ colors: [String], _ alpha: [Double], _ stops: [Double]) {
        let gradient = Gradient(stops: colors.indices.map { .init(color: color(colors[$0], alpha[$0]), location: stops[$0]) })
        context.fill(Path(rect), with: .linearGradient(gradient, startPoint: CGPoint(x: x1, y: y1), endPoint: CGPoint(x: x2, y: y2)))
    }
    func radial(_ rect: CGRect, _ x: Double, _ y: Double, _ radius: Double, _ colors: [String], _ alpha: [Double]) {
        let gradient = Gradient(colors: colors.indices.map { color(colors[$0], alpha[$0]) })
        context.fill(Path(rect), with: .radialGradient(gradient, center: CGPoint(x: x, y: y), startRadius: 0, endRadius: radius))
    }
    func radialOval(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ cx: Double, _ cy: Double, _ radius: Double, _ first: String, _ last: String) {
        context.fill(Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h)), with: .radialGradient(Gradient(colors: [color(first), color(last)]), center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: radius))
    }
    func titleLight(_ text: String, _ x: Double, _ cy: Double, _ weight: String, _ size: Double, _ gx: Double) {
        let line = resources.line(text, weight, size)
        context.withCGContext { cg in
            cg.saveGState()
            defer { cg.restoreGState() }
            cg.translateBy(x: x, y: cy + size * 0.36)
            cg.scaleBy(x: 1, y: -1)
            cg.textMatrix = .identity
            cg.textPosition = .zero
            cg.setTextDrawingMode(.clip)
            CTLineDraw(line, cg)
            let colors = [color("#7C68FF", 0), color("#7C68FF", 0.95), color("#7C68FF", 0)]
            let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map { UIColor($0).cgColor } as CFArray, locations: [0, 0.5, 1])!
            cg.drawLinearGradient(gradient, start: CGPoint(x: gx - 70 - x, y: 0), end: CGPoint(x: gx + 70 - x, y: 0), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
    }
    func textWidth(_ text: String, _ weight: String, _ size: Double) -> Double {
        CTLineGetTypographicBounds(resources.line(text, weight, size), nil, nil, nil)
    }
    func text(_ text: String, _ x: Double, _ cy: Double, _ weight: String, _ size: Double,
              _ hex: String, _ alpha: Double = 1, ax: Double = 0) {
        let line = resources.line(text, weight, size)
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)
        let value = UInt32(hex.dropFirst(), radix: 16) ?? 0
        context.withCGContext { cg in
            cg.saveGState()
            defer { cg.restoreGState() }
            cg.setFillColor(CGColor(srgbRed: Double((value >> 16) & 255) / 255,
                                   green: Double((value >> 8) & 255) / 255,
                                   blue: Double(value & 255) / 255, alpha: alpha))
            cg.translateBy(x: x - width * ax, y: cy + size * 0.36)
            cg.scaleBy(x: 1, y: -1)
            cg.textMatrix = .identity
            cg.textPosition = .zero
            CTLineDraw(line, cg)
        }
    }
}
