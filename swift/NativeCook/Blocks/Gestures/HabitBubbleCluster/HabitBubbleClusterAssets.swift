import CoreText
import SwiftUI
import UIKit

@MainActor
final class HabitBubbleClusterAssets {
    static let shared = HabitBubbleClusterAssets()

    private static let fontFiles = [
        "HabitBubbleCluster-Inter-Regular",
        "HabitBubbleCluster-Inter-Medium",
        "HabitBubbleCluster-Inter-SemiBold",
    ]

    private var backdropSize = CGSize.zero
    private var backdropScale = 0.0
    private var cachedBackdrop = UIImage()

    private init() {
        for file in Self.fontFiles {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                assertionFailure("Missing Habit Bubble Cluster font: \(file).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        #if DEBUG
        assert(Bundle.main.url(forResource: "HabitBubbleCluster-Inter-OFL", withExtension: "txt") != nil, "Missing Habit Bubble Cluster font license")
        #endif
    }

    func backdrop(size: CGSize, scale: CGFloat) -> UIImage {
        guard size.width > 0, size.height > 0 else { return UIImage() }
        if size == backdropSize, abs(scale - backdropScale) < 0.001 { return cachedBackdrop }
        backdropSize = size
        backdropScale = scale

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        cachedBackdrop = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            let context = renderer.cgContext
            let bounds = CGRect(origin: .zero, size: size)
            let rgb = CGColorSpaceCreateDeviceRGB()

            let base = CGGradient(
                colorsSpace: rgb,
                colors: [UIColor(hex: "#101317").cgColor, UIColor(hex: "#0D0F12").cgColor, UIColor(hex: "#121110").cgColor] as CFArray,
                locations: [0, 0.55, 1]
            )!
            context.drawLinearGradient(base, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])

            Self.drawRadial(
                context,
                center: CGPoint(x: size.width * 0.12, y: size.height * 0.22),
                radius: size.width * 0.95,
                colors: [UIColor(red: 130 / 255, green: 170 / 255, blue: 150 / 255, alpha: 0.20), UIColor(red: 90 / 255, green: 120 / 255, blue: 110 / 255, alpha: 0.07), .clear]
            )
            Self.drawRadial(
                context,
                center: CGPoint(x: size.width * 0.90, y: size.height * 0.70),
                radius: size.width * 0.95,
                colors: [UIColor(red: 220 / 255, green: 190 / 255, blue: 140 / 255, alpha: 0.17), UIColor(red: 160 / 255, green: 120 / 255, blue: 80 / 255, alpha: 0.06), .clear]
            )

            // Deterministic, one-time fine grain. It is intentionally baked into the page rather than redrawn per frame.
            var seed: UInt64 = 0x484243
            let grainCount = max(4_000, Int(size.width * size.height / 24))
            for _ in 0..<grainCount {
                seed = seed &* 6_364_136_223_846_793_005 &+ 1
                let x = CGFloat((seed >> 16) & 0xffff) / 65_535 * size.width
                seed = seed &* 6_364_136_223_846_793_005 &+ 1
                let y = CGFloat((seed >> 16) & 0xffff) / 65_535 * size.height
                seed = seed &* 6_364_136_223_846_793_005 &+ 1
                let bright = ((seed >> 20) & 1) == 0
                context.setFillColor((bright ? UIColor.white : UIColor.black).withAlphaComponent(0.018).cgColor)
                context.fill(CGRect(x: x, y: y, width: 0.55, height: 0.55))
            }
            context.setStrokeColor(UIColor.white.withAlphaComponent(0.025).cgColor)
            context.stroke(bounds.insetBy(dx: 0.25, dy: 0.25))
        }
        return cachedBackdrop
    }

    static func font(_ name: String, size: Double) -> Font {
        _ = shared
        return .custom(name, fixedSize: size)
    }

    static func uiFont(_ name: String, size: Double) -> UIFont {
        _ = shared
        return UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
    }

    private static func drawRadial(_ context: CGContext, center: CGPoint, radius: CGFloat, colors: [UIColor]) {
        let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: colors.map(\.cgColor) as CFArray,
            locations: [0, 0.5, 1]
        )!
        context.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius, options: [.drawsAfterEndLocation])
    }
}

private extension UIColor {
    convenience init(hex: String) {
        let raw = UInt64(hex.dropFirst(), radix: 16) ?? 0
        self.init(
            red: CGFloat((raw >> 16) & 0xff) / 255,
            green: CGFloat((raw >> 8) & 0xff) / 255,
            blue: CGFloat(raw & 0xff) / 255,
            alpha: 1
        )
    }
}
