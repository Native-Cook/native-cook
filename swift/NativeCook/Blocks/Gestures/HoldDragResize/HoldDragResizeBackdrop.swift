import CoreImage
import SwiftUI
import UIKit

struct HoldDragResizeBackdrop {
    let sharp: Image
    let frosted: Image
}

@MainActor
enum HoldDragResizeBackdropCache {
    private static var cache: [String: HoldDragResizeBackdrop] = [:]
    private static let context = CIContext(options: [.cacheIntermediates: true])

    static func images(for layout: HoldDragResizeLayout) -> HoldDragResizeBackdrop {
        let key = "\(Int(layout.width.rounded()))x\(Int(layout.height.rounded()))-\(Int(layout.top.rounded()))"
        if let cached = cache[key] { return cached }

        let format = UIGraphicsImageRendererFormat.preferred()
        // The cache is addressed in points and Canvas scales it to the display. Rendering the
        // full-screen blur at 3x blocks first paint for seconds without a visible benefit.
        format.scale = 1
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: layout.width, height: layout.height), format: format)
        let sharpImage = renderer.image { output in
            drawPage(in: output.cgContext, layout: layout)
        }
        let frostedImage = blur(sharpImage, radius: 24) ?? sharpImage
        let result = HoldDragResizeBackdrop(sharp: Image(uiImage: sharpImage), frosted: Image(uiImage: frostedImage))
        cache[key] = result
        return result
    }

    private static func drawPage(in context: CGContext, layout: HoldDragResizeLayout) {
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        if let gradient = CGGradient(
            colorsSpace: colorSpace,
            colors: [
                UIColor(red: 8 / 255, green: 11 / 255, blue: 19 / 255, alpha: 1).cgColor,
                UIColor(red: 6 / 255, green: 8 / 255, blue: 13 / 255, alpha: 1).cgColor,
                UIColor(red: 10 / 255, green: 10 / 255, blue: 14 / 255, alpha: 1).cgColor,
            ] as CFArray,
            locations: [0, 0.45, 1]
        ) {
            context.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: layout.height), options: [])
        }

        if let horizon = CGGradient(
            colorsSpace: colorSpace,
            colors: [
                UIColor(red: 1, green: 120 / 255, blue: 50 / 255, alpha: 0).cgColor,
                UIColor(red: 230 / 255, green: 104 / 255, blue: 48 / 255, alpha: 0.12).cgColor,
                UIColor(red: 1, green: 140 / 255, blue: 60 / 255, alpha: 0.3).cgColor,
            ] as CFArray,
            locations: [0, 0.55, 1]
        ) {
            context.drawLinearGradient(
                horizon,
                start: CGPoint(x: 0, y: layout.height * 0.38),
                end: CGPoint(x: 0, y: layout.height),
                options: []
            )
        }
        drawRadial(
            context,
            center: CGPoint(x: layout.width * 0.92, y: layout.height * 0.98),
            radius: layout.width * 1.15,
            colors: [
                UIColor(red: 1, green: 170 / 255, blue: 80 / 255, alpha: 0.42),
                UIColor(red: 236 / 255, green: 100 / 255, blue: 46 / 255, alpha: 0.18),
                .clear,
            ],
            locations: [0, 0.4, 1]
        )
        drawRadial(
            context,
            center: CGPoint(x: layout.width * 0.08, y: layout.height * 0.2),
            radius: layout.width * 0.9,
            colors: [UIColor(red: 88 / 255, green: 120 / 255, blue: 200 / 255, alpha: 0.14), .clear],
            locations: [0, 1]
        )

        context.setFillColor(UIColor.white.withAlphaComponent(0.065).cgColor)
        for hour in 9...18 {
            context.fill(CGRect(x: 63, y: layout.y(Double(hour * 60)) - 0.25, width: layout.right - 63, height: 0.5))
        }
        context.setFillColor(UIColor.white.withAlphaComponent(0.2).cgColor)
        var dotY = layout.y(HoldDragResizeData.dayStart) - 6
        while dotY <= layout.y(HoldDragResizeData.dayEnd) + 6 {
            context.fillEllipse(in: CGRect(x: 62.1, y: dotY - 0.9, width: 1.8, height: 1.8))
            dotY += 6
        }
        let nowY = layout.y(HoldDragResizeData.now)
        context.setFillColor(UIColor(red: 1, green: 90 / 255, blue: 78 / 255, alpha: 1).cgColor)
        context.fill(CGRect(x: 63, y: nowY - 0.75, width: layout.right - 63, height: 1.5))
        context.fillEllipse(in: CGRect(x: 59, y: nowY - 4, width: 8, height: 8))

        // Fixed low-amplitude grain. It is part of the cached backdrop, never redrawn per animation frame.
        context.setFillColor(UIColor.white.withAlphaComponent(0.016).cgColor)
        for y in stride(from: 1, through: Int(layout.height), by: 7) {
            for x in stride(from: (y * 17) % 11, through: Int(layout.width), by: 13) {
                context.fill(
                    CGRect(x: CGFloat(x), y: CGFloat(y), width: 0.55, height: 0.55)
                )
            }
        }
    }

    private static func drawRadial(
        _ context: CGContext,
        center: CGPoint,
        radius: CGFloat,
        colors: [UIColor],
        locations: [CGFloat]
    ) {
        guard let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: colors.map(\.cgColor) as CFArray,
            locations: locations
        ) else { return }
        context.drawRadialGradient(
            gradient,
            startCenter: center,
            startRadius: 0,
            endCenter: center,
            endRadius: radius,
            options: .drawsAfterEndLocation
        )
    }

    private static func blur(_ image: UIImage, radius: CGFloat) -> UIImage? {
        guard let input = CIImage(image: image) else { return nil }
        let output = input
            .clampedToExtent()
            .applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: radius * image.scale])
            .cropped(to: input.extent)
        guard let cgImage = context.createCGImage(output, from: input.extent) else { return nil }
        return UIImage(cgImage: cgImage, scale: image.scale, orientation: .up)
    }
}
