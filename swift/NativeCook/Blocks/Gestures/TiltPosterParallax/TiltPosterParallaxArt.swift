import CoreImage
import UIKit

struct TiltPosterParallaxArt {
    let layers: [UIImage]
    let backdrop: UIImage
}

@MainActor
enum TiltPosterParallaxArtFactory {
    static let overscan: [CGFloat] = [1.24, 1.20, 1.18, 1.14, 1.12]
    private static var cache: [String: TiltPosterParallaxArt] = [:]
    private static let imageContext = CIContext(options: [.cacheIntermediates: true])

    static func make(cardWidth: CGFloat, cardHeight: CGFloat, scale: CGFloat) -> TiltPosterParallaxArt {
        let key = "\(Int(cardWidth.rounded()))x\(Int(cardHeight.rounded()))@\(scale)"
        if let cached = cache[key] { return cached }

        let layers = overscan.indices.map { index in
            makeLayer(index: index, cardWidth: cardWidth, cardHeight: cardHeight, scale: scale)
        }
        let art = TiltPosterParallaxArt(
            layers: layers,
            backdrop: makeBackdrop(layers: layers, cardWidth: cardWidth, cardHeight: cardHeight, scale: scale)
        )
        cache[key] = art
        return art
    }

    private static func makeLayer(index: Int, cardWidth: CGFloat, cardHeight: CGFloat, scale: CGFloat) -> UIImage {
        let width = cardWidth * overscan[index]
        let height = cardHeight * overscan[index]
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = index == 0
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { renderer in
            drawLayer(
                context: renderer.cgContext,
                index: index,
                width: width,
                height: height,
                cardWidth: cardWidth,
                cardHeight: cardHeight,
                scale: scale
            )
        }
    }

    private static func drawLayer(
        context: CGContext,
        index: Int,
        width: CGFloat,
        height: CGFloat,
        cardWidth: CGFloat,
        cardHeight: CGFloat,
        scale: CGFloat
    ) {
        let offsetX = (width - cardWidth) / 2
        let offsetY = (height - cardHeight) / 2
        func y(_ share: CGFloat) -> CGFloat { offsetY + cardHeight * share }

        switch index {
        case 0:
            drawGradient(
                context: context,
                colors: ["141A30", "2E2B4A", "7A4E5E", "D98C62", "F2B77E"].map { UIColor(hex: $0) },
                locations: [0, y(0.30) / height, y(0.50) / height, y(0.64) / height, 1],
                start: .zero,
                end: CGPoint(x: 0, y: height)
            )
            drawGrain(context: context, width: width, height: height, scale: scale, amount: 0.05)
        case 1:
            let sun = CGPoint(x: offsetX + cardWidth * 0.50, y: y(0.60))
            let radius = cardWidth * 0.17
            if let halo = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: [
                    UIColor(red: 1, green: 214 / 255, blue: 160 / 255, alpha: 0.45).cgColor,
                    UIColor(red: 1, green: 170 / 255, blue: 110 / 255, alpha: 0.12).cgColor,
                    UIColor(red: 1, green: 170 / 255, blue: 110 / 255, alpha: 0).cgColor,
                ] as CFArray,
                locations: [0, 0.4, 1]
            ) {
                context.drawRadialGradient(halo, startCenter: sun, startRadius: 0, endCenter: sun, endRadius: radius * 3.2, options: .drawsAfterEndLocation)
            }
            drawGradient(
                context: context,
                colors: [UIColor(hex: "FFF1D6"), UIColor(hex: "F7C98F")],
                locations: [0, 1],
                start: CGPoint(x: 0, y: sun.y - radius),
                end: CGPoint(x: 0, y: sun.y + radius),
                clip: UIBezierPath(ovalIn: CGRect(x: sun.x - radius, y: sun.y - radius, width: radius * 2, height: radius * 2)).cgPath
            )
        case 2:
            let path = ridge(width: width, height: height, base: y(0.64), amplitude: cardHeight * 0.05, seed: 1.3)
            fillRidge(context: context, path: path, top: "9C6E78", bottom: "7A5A6C", startY: y(0.58), endY: y(0.80))
            drawGrain(context: context, width: width, height: height, scale: scale, amount: 0.04, clip: path)
        case 3:
            let path = ridge(width: width, height: height, base: y(0.73), amplitude: cardHeight * 0.06, seed: 4.1)
            fillRidge(context: context, path: path, top: "53405A", bottom: "3A2E44", startY: y(0.66), endY: y(0.90))
            drawGrain(context: context, width: width, height: height, scale: scale, amount: 0.04, clip: path)
        default:
            let path = ridge(width: width, height: height, base: y(0.84), amplitude: cardHeight * 0.045, seed: 2.6)
            fillRidge(context: context, path: path, top: "211A28", bottom: "120E17", startY: y(0.80), endY: y(1))
            drawGrain(context: context, width: width, height: height, scale: scale, amount: 0.035, clip: path)
            drawWalker(context: context, width: width, offsetX: offsetX, base: y(0.84), cardWidth: cardWidth, cardHeight: cardHeight)
        }
    }

    private static func ridgeY(base: CGFloat, amplitude: CGFloat, seed: CGFloat, unit: CGFloat) -> CGFloat {
        base - amplitude * (
            0.55 * sin(unit * 5.1 + seed)
                + 0.30 * sin(unit * 11.7 + seed * 2.3)
                + 0.15 * sin(unit * 23.9 + seed * 0.7)
        )
    }

    private static func ridge(width: CGFloat, height: CGFloat, base: CGFloat, amplitude: CGFloat, seed: CGFloat) -> CGPath {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: 0, y: height))
        for index in 0...48 {
            let unit = CGFloat(index) / 48
            path.addLine(to: CGPoint(x: width * unit, y: ridgeY(base: base, amplitude: amplitude, seed: seed, unit: unit)))
        }
        path.addLine(to: CGPoint(x: width, y: height))
        path.closeSubpath()
        return path
    }

    private static func fillRidge(context: CGContext, path: CGPath, top: String, bottom: String, startY: CGFloat, endY: CGFloat) {
        drawGradient(
            context: context,
            colors: [UIColor(hex: top), UIColor(hex: bottom)],
            locations: [0, 1],
            start: CGPoint(x: 0, y: startY),
            end: CGPoint(x: 0, y: endY),
            clip: path
        )
    }

    private static func drawWalker(
        context: CGContext,
        width: CGFloat,
        offsetX: CGFloat,
        base: CGFloat,
        cardWidth: CGFloat,
        cardHeight: CGFloat
    ) {
        let x = offsetX + cardWidth * 0.62
        let ground = ridgeY(base: base, amplitude: cardHeight * 0.045, seed: 2.6, unit: x / width) + 1
        let scale = cardHeight / 250
        context.setFillColor(UIColor(hex: "120E17").cgColor)
        context.fillEllipse(in: CGRect(x: x - 1.6 * scale, y: ground - 24.4 * scale, width: 3.8 * scale, height: 3.8 * scale))
        let body = CGMutablePath()
        body.move(to: CGPoint(x: x - 1.4 * scale, y: ground - 20 * scale))
        body.addLine(to: CGPoint(x: x + 1.8 * scale, y: ground - 20 * scale))
        body.addLine(to: CGPoint(x: x + 2.6 * scale, y: ground - 8.5 * scale))
        body.addLine(to: CGPoint(x: x - 4.2 * scale, y: ground - 7.5 * scale))
        body.closeSubpath()
        body.move(to: CGPoint(x: x - 0.9 * scale, y: ground - 8 * scale))
        body.addLine(to: CGPoint(x: x - 3.2 * scale, y: ground + 0.5 * scale))
        body.addLine(to: CGPoint(x: x - 2.2 * scale, y: ground + 0.5 * scale))
        body.addLine(to: CGPoint(x: x + 0.1 * scale, y: ground - 8 * scale))
        body.closeSubpath()
        body.move(to: CGPoint(x: x + 0.7 * scale, y: ground - 8 * scale))
        body.addLine(to: CGPoint(x: x + 2.9 * scale, y: ground + 0.5 * scale))
        body.addLine(to: CGPoint(x: x + 3.9 * scale, y: ground + 0.5 * scale))
        body.addLine(to: CGPoint(x: x + 1.7 * scale, y: ground - 8 * scale))
        body.closeSubpath()
        context.addPath(body)
        context.fillPath()
    }

    private static func drawGradient(
        context: CGContext,
        colors: [UIColor],
        locations: [CGFloat],
        start: CGPoint,
        end: CGPoint,
        clip: CGPath? = nil
    ) {
        guard let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: colors.map(\.cgColor) as CFArray,
            locations: locations
        ) else { return }
        context.saveGState()
        if let clip {
            context.addPath(clip)
            context.clip()
        }
        context.drawLinearGradient(gradient, start: start, end: end, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        context.restoreGState()
    }

    private static func drawGrain(
        context: CGContext,
        width: CGFloat,
        height: CGFloat,
        scale: CGFloat,
        amount: CGFloat,
        clip: CGPath? = nil
    ) {
        context.saveGState()
        if let clip {
            context.addPath(clip)
            context.clip()
        }
        context.setBlendMode(.softLight)
        context.setLineWidth(max(0.45, 0.7 / scale))
        var state: UInt32 = 0x6D2B79F5
        let count = max(180, Int(width * height / 28))
        for _ in 0..<count {
            state = state &* 1_664_525 &+ 1_013_904_223
            let x = CGFloat((state >> 8) & 0xFFFF) / 65_535 * width
            state = state &* 1_664_525 &+ 1_013_904_223
            let y = CGFloat((state >> 8) & 0xFFFF) / 65_535 * height
            let white = state & 1 == 0
            context.setStrokeColor((white ? UIColor.white : UIColor.black).withAlphaComponent(amount * 0.22).cgColor)
            context.move(to: CGPoint(x: x, y: y))
            context.addLine(to: CGPoint(x: x + 0.01, y: y + 0.01))
            context.strokePath()
        }
        context.restoreGState()
    }

    private static func makeBackdrop(layers: [UIImage], cardWidth: CGFloat, cardHeight: CGFloat, scale: CGFloat) -> UIImage {
        let quarterScale = scale / 4
        let width = max(1, (cardWidth * quarterScale).rounded())
        let height = max(1, (cardHeight * quarterScale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { _ in
            for (index, layer) in layers.enumerated() {
                let layerWidth = cardWidth * overscan[index] * quarterScale
                let layerHeight = cardHeight * overscan[index] * quarterScale
                layer.draw(in: CGRect(
                    x: (width - layerWidth) / 2,
                    y: (height - layerHeight) / 2,
                    width: layerWidth,
                    height: layerHeight
                ))
            }
        }
        guard let input = CIImage(image: image) else { return image }
        let radius = 14 * quarterScale
        let blurred = input.clampedToExtent().applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: radius]).cropped(to: input.extent)
        guard let output = imageContext.createCGImage(blurred, from: input.extent) else { return image }
        return UIImage(cgImage: output, scale: 1, orientation: .up)
    }
}

private extension UIColor {
    convenience init(hex: String) {
        let value = UInt64(hex, radix: 16) ?? 0
        self.init(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: 1
        )
    }
}
