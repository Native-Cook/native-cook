import SwiftUI
import UIKit

/*
 Ingot Coin Switch — the embossed marks, drawn once into images.

 An ingot's top face carries "FINE", the metal's name, "999.9", "NET WT" and "1000 g"; a coin's faces
 carry a groove ring, a big letter and the invented ticker. Every line is stamped three times: a light
 edge up-left, a dark edge down-right and the letters in a deeper shade of the metal, so they read as
 raised. The images are in the face's own units (centred on 0), drawn through the face's projection.
*/
enum IngotCoinSwitchArt {
    typealias M = IngotCoinSwitchModel

    static let ingotSize = CGSize(width: 70, height: 190)
    static let coinSize = CGSize(width: 136, height: 136)

    static let ingotMarks: [Image] = (0..<M.styles).map { Image(uiImage: ingot($0)) }
    static let coinMarks: [Image] = (0..<M.styles).map { Image(uiImage: coin($0)) }

    static func uiColor(_ c: IngotCoinSwitchRGB, _ a: Double = 1) -> UIColor {
        UIColor(red: c.r, green: c.g, blue: c.b, alpha: a)
    }

    /// One line of embossed text centred on (x, y), shrunk to fit `maxWidth`.
    private static func stamp(_ s: String, size: CGFloat, weight: UIFont.Weight, kern: CGFloat,
                              x: CGFloat, y: CGFloat, maxWidth: CGFloat, ink: UIColor) {
        func attrs(_ color: UIColor, _ font: UIFont) -> [NSAttributedString.Key: Any] {
            [.font: font, .foregroundColor: color, .kern: kern]
        }
        var font = UIFont.systemFont(ofSize: size, weight: weight)
        var w = (s as NSString).size(withAttributes: attrs(ink, font)).width - kern
        if w > maxWidth {
            font = UIFont.systemFont(ofSize: size * maxWidth / w, weight: weight)
            w = (s as NSString).size(withAttributes: attrs(ink, font)).width - kern
        }
        let h = font.lineHeight
        let origin = CGPoint(x: x - w / 2, y: y - h / 2)
        let e = max(0.5, size / 28)
        (s as NSString).draw(at: CGPoint(x: origin.x - e, y: origin.y - e),
                             withAttributes: attrs(UIColor(white: 1, alpha: 0.6), font))
        (s as NSString).draw(at: CGPoint(x: origin.x + e, y: origin.y + e * 1.2),
                             withAttributes: attrs(UIColor(white: 0, alpha: 0.28), font))
        (s as NSString).draw(at: origin, withAttributes: attrs(ink, font))
    }

    private static func render(_ size: CGSize, _ body: () -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in body() }
    }

    static func ingot(_ style: Int) -> UIImage {
        let base = IngotCoinSwitchRGB(hex: M.ingotHex[style])
        let ink = uiColor(base * 0.72)
        let w = ingotSize.width
        let cx = w / 2
        return render(ingotSize) {
            // Image y is down; the face's centre is at 95.
            stamp("FINE", size: 7, weight: .bold, kern: 1.2, x: cx, y: 95 - 58, maxWidth: 50, ink: ink)
            stamp(M.metals[style].uppercased(), size: 15, weight: .heavy, kern: 0.2, x: cx, y: 95 - 42, maxWidth: 52, ink: ink)
            stamp("999.9", size: 7.5, weight: .semibold, kern: 0.6, x: cx, y: 95 - 27, maxWidth: 50, ink: ink)
            stamp("NET WT", size: 6.5, weight: .bold, kern: 0.8, x: cx, y: 95 + 48, maxWidth: 50, ink: ink)
            stamp("1000 g", size: 7.5, weight: .semibold, kern: 0.4, x: cx, y: 95 + 59, maxWidth: 50, ink: ink)
        }
    }

    static func coin(_ style: Int) -> UIImage {
        let base = IngotCoinSwitchRGB(hex: M.coinHex[style])
        let ink = uiColor(base * 0.7)
        let c = coinSize.width / 2
        return render(coinSize) {
            // The groove: a dark ring with a light ring just inside it.
            let r = 58.0
            UIColor(white: 0, alpha: 0.18).setStroke()
            let dark = UIBezierPath(ovalIn: CGRect(x: c - r, y: c - r, width: 2 * r, height: 2 * r))
            dark.lineWidth = 1.3
            dark.stroke()
            UIColor(white: 1, alpha: 0.55).setStroke()
            let light = UIBezierPath(ovalIn: CGRect(x: c - r + 1.3, y: c - r + 1.3, width: 2 * r - 2.6, height: 2 * r - 2.6))
            light.lineWidth = 1
            light.stroke()
            stamp(M.marks[style], size: 62, weight: .heavy, kern: 0, x: c, y: c - 4, maxWidth: 90, ink: ink)
            stamp(M.tickers[style], size: 9, weight: .bold, kern: 1.6, x: c, y: c + 38, maxWidth: 70, ink: ink)
        }
    }
}
