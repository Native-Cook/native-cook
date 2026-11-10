import CoreText
import ImageIO
import UIKit

final class RainWindowForecastResources {
    static let shared = RainWindowForecastResources()
    let sharp: CGImage
    let blurred: CGImage
    private var cachedKey = ""
    private var cachedArt = UIImage()
    private init() {
        for file in ["InstrumentSerif-Regular", "Inter-Regular", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "RainWindowForecast-\(file)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        func load(_ name: String) -> CGImage {
            let url = Bundle.main.url(forResource: "RainWindowForecast-\(name)", withExtension: "jpg")!
            let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
            return CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)!
        }
        sharp = load("city")
        blurred = load("city-blur")
    }
    func overlay(_ size: CGSize, _ top: CGFloat, _ bottom: CGFloat, _ scale: CGFloat) -> UIImage {
        let key = "\(size)-\(top)-\(bottom)-\(scale)"
        if key == cachedKey { return cachedArt }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.preferredRange = .standard
        let result = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            let ctx = renderer.cgContext
            let mute = UIColor.white.withAlphaComponent(0.72)
            func label(_ text: String, _ face: String, _ fontSize: CGFloat, _ x: CGFloat, _ y: CGFloat,
                       _ width: CGFloat, _ lineHeight: CGFloat? = nil, _ color: UIColor = .white,
                       _ align: NSTextAlignment = .center, _ tracking: CGFloat = 0, _ shadow: Bool = true,
                       _ tabular: Bool = false) {
                let base = UIFont(name: face, size: fontSize)!
                let font = tabular ? UIFont(descriptor: base.fontDescriptor.addingAttributes([
                    .featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                                        UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]]
                ]), size: fontSize) : base
                let para = NSMutableParagraphStyle()
                para.alignment = align
                var attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .paragraphStyle: para]
                if tracking != 0 { attrs[.kern] = tracking }
                if shadow {
                    let s = NSShadow()
                    s.shadowColor = UIColor(red: 8 / 255, green: 12 / 255, blue: 28 / 255, alpha: 0.45)
                    s.shadowOffset = CGSize(width: 0, height: 1)
                    s.shadowBlurRadius = 8
                    attrs[.shadow] = s
                }
                let h = lineHeight ?? font.lineHeight
                // RN clamps a short line box to its bottom baseline (not its centre).
                let dy = h < font.lineHeight ? h - font.lineHeight : (h - font.lineHeight) / 2
                (text as NSString).draw(in: CGRect(x: x, y: y + dy, width: width, height: max(h, font.lineHeight) + 4), withAttributes: attrs)
            }
            let city = top + 24
            label("Aurenne", "Inter-Medium", 17, 0, city, size.width, 24)
            label("14°", "InstrumentSerif-Regular", 104, 0, city + 24, size.width, 108)
            label("Light rain · stops in 25 min", "Inter-Medium", 15, 0, city + 132, size.width, 20)
            label("H 17°  L 11°", "Inter24pt-Regular", 13, 0, city + 154, size.width, 20, mute)
            let hint = size.height - max(bottom, 16) - 8 - 20
            let card = CGRect(x: 24, y: hint - 16 - 168, width: size.width - 48, height: 168)
            ctx.setFillColor(UIColor(red: 16 / 255, green: 22 / 255, blue: 40 / 255, alpha: 0.42).cgColor)
            let shape = UIBezierPath(roundedRect: card, cornerRadius: 28)
            shape.fill()
            let hair = 1 / scale
            ctx.setStrokeColor(UIColor.white.withAlphaComponent(0.18).cgColor)
            ctx.setLineWidth(hair)
            let border = UIBezierPath(roundedRect: card.insetBy(dx: hair / 2, dy: hair / 2), cornerRadius: 28 - hair / 2)
            border.lineWidth = hair
            border.stroke()
            // RN's border contributes to the padding box.
            let left = card.minX + 16 + hair
            let right = card.maxX - 16 - hair
            let y = card.minY + 16 + hair
            label("NEXT HOUR", "Inter-SemiBold", 11, left, y + 2, right - left, nil, mute, .left, 1.4, false)
            label("Stops 9:25", "Inter-Medium", 13, left, y, right - left, nil, .white, .right, 0, false)
            let headHeight = ceil(UIFont(name: "Inter-Medium", size: 13)!.lineHeight)
            let barBottom = y + headHeight + 12 + 72
            let minutes: [CGFloat] = [0.62, 0.7, 0.78, 0.66, 0.5, 0.36, 0.22, 0.12, 0.05, 0, 0, 0]
            let barWidth = (size.width - 48 - 32 - 66) / 12
            for (i, value) in minutes.enumerated() {
                UIColor(red: 196 / 255, green: 214 / 255, blue: 1, alpha: value > 0 ? 0.95 : 0.35).setFill()
                let height = max(3, value * 64)
                UIBezierPath(roundedRect: CGRect(x: left + CGFloat(i) * (barWidth + 6), y: barBottom - height, width: barWidth, height: height), cornerRadius: 3).fill()
            }
            let tickBase = UIFont(name: "Inter24pt-Regular", size: 11)!
            let tickFont = UIFont(descriptor: tickBase.fontDescriptor.addingAttributes([
                .featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                                    UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]]
            ]), size: 11)
            let ticks = ["Now", "15", "30", "45", "60"]
            let widths = ticks.map { ($0 as NSString).size(withAttributes: [.font: tickFont]).width }
            let gap = (right - left - widths.reduce(0, +)) / 4
            var x = left
            for (i, tick) in ticks.enumerated() {
                label(tick, "Inter24pt-Regular", 11, x, barBottom + 8, widths[i] + 1, nil, mute, .left, 0, false, true)
                x += widths[i] + gap
            }
            label("Wipe the glass to look outside", "Inter24pt-Regular", 13, 0, hint, size.width, 20, mute)
        }
        cachedKey = key
        cachedArt = result
        return result
    }
}
