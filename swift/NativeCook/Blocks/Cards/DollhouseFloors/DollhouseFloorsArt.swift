import SwiftUI
import UIKit

// The projected faces are rasterised once per screen size; animation only moves whole layers.
final class DollhouseFloorsArt {
    let size: CGSize
    let s: Double
    let cx: Double
    let base: Double
    var storeys: [UIImage] = []
    var roof = UIImage()
    var plate = UIImage()
    init(_ size: CGSize) {
        self.size = size
        s = (size.width * 0.074).rounded()
        cx = size.width / 2 + 6
        base = (size.height * 0.63).rounded()
        plate = image { c in
            let p = point(2, 1.5, -0.3)
            c.setFillColor(Self.uiColor(0xE7DCCB).cgColor)
            c.fillEllipse(in: CGRect(x: p.x - s * 4.2, y: p.y - s * 1.6, width: s * 8.4, height: s * 3.6))
            c.setFillColor(Self.uiColor(0x3A2E22, 0.08).cgColor)
            c.fillEllipse(in: CGRect(x: p.x - s * 3.2, y: p.y - s, width: s * 6.4, height: s * 2.4))
        }
        storeys = (0..<2).map { i in image { c in storey(c, Double(i) * 2.65, i, 1) } }
        roof = image { c in drawRoof(c, 5.3, 1) }
    }
    static func uiColor(_ hex: Int, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, alpha: alpha)
    }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    private func image(_ body: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = UIScreen.main.scale; format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: size, format: format).image { body($0.cgContext) }
    }
    func point(_ x: Double, _ y: Double, _ z: Double) -> CGPoint {
        CGPoint(x: cx + (x - y) * s * 0.866, y: base - (x + y) * s * 0.5 - z * s)
    }
    private func polygon(_ c: CGContext, _ pts: [[Double]], _ color: Int, _ a: Double) {
        c.beginPath()
        for (i,p) in pts.enumerated() {
            let q = point(p[0], p[1], p[2])
            if i == 0 { c.move(to: q) } else { c.addLine(to: q) }
        }
        c.closePath(); c.setFillColor(Self.uiColor(color, a).cgColor); c.fillPath()
    }
    private func box(_ c: CGContext, _ x: Double, _ y: Double, _ z: Double, _ w: Double, _ d: Double, _ h: Double, _ top: Int, _ left: Int, _ right: Int, _ a: Double) {
        polygon(c, [[x,y,z],[x+w,y,z],[x+w,y,z+h],[x,y,z+h]], left, a)
        polygon(c, [[x,y,z],[x,y+d,z],[x,y+d,z+h],[x,y,z+h]], right, a)
        polygon(c, [[x,y,z+h],[x+w,y,z+h],[x+w,y+d,z+h],[x,y+d,z+h]], top, a)
    }
    private func storey(_ c: CGContext, _ z: Double, _ kind: Int, _ a: Double) {
        box(c, 0, 0, z - 0.25, 4.0, 3.0, 0.25, 0xE9C99B, 0xC9A574, 0xD8B585, a)
        polygon(c, [ [0, 3.0, z], [4.0, 3.0, z], [4.0, 3.0, z + 2.4], [0, 3.0, z + 2.4], ], 0xF6EFE4, a)
        polygon(c, [ [4.0, 0, z], [4.0, 3.0, z], [4.0, 3.0, z + 2.4], [4.0, 0, z + 2.4], ], 0xEDE3D4, a)
        polygon(c, [ [0, 3.0 - 0.12, z + 2.4], [4.0, 3.0 - 0.12, z + 2.4], [4.0, 3.0, z + 2.4], [0, 3.0, z + 2.4], ], 0xD9CBB6, a)
        polygon(c, [ [4.0 - 0.12, 0, z + 2.4], [4.0, 0, z + 2.4], [4.0, 3.0, z + 2.4], [4.0 - 0.12, 3.0, z + 2.4], ], 0xD9CBB6, a)
        polygon(c, [ [1.3, 3.0 - 0.01, z + 0.9], [2.4, 3.0 - 0.01, z + 0.9], [2.4, 3.0 - 0.01, z + 1.9], [1.3, 3.0 - 0.01, z + 1.9], ], 0xBFE3F2, a)
        polygon(c, [ [4.0 - 0.01, 0.8, z + 0.9], [4.0 - 0.01, 1.8, z + 0.9], [4.0 - 0.01, 1.8, z + 1.9], [4.0 - 0.01, 0.8, z + 1.9], ], 0xBFE3F2, a)
        if kind == 0 {
            polygon(c, [ [0.4, 0.4, z + 0.01], [2.0, 0.4, z + 0.01], [2.0, 1.8, z + 0.01], [0.4, 1.8, z + 0.01], ], 0xF2B8A2, a)
            box(c, 0.5, 2.1, z, 1.8, 0.7, 0.45, 0x5FA8A0, 0x4C8E86, 0x56998F, a)
            box(c, 0.5, 2.6, z + 0.45, 1.8, 0.2, 0.4, 0x5FA8A0, 0x4C8E86, 0x56998F, a)
            box(c, 0.9, 0.8, z, 0.8, 0.6, 0.35, 0xB07A4A, 0x8E5E35, 0x9D6A3E, a)
            box(c, 4.0 - 0.7, 0.3, z, 0.6, 2.4, 0.9, 0xF4F6F8, 0xD5DBE2, 0xE3E8EE, a)
            box(c, 4.0 - 0.7, 0.3, z + 0.9, 0.6, 2.4, 0.06, 0x7C8898, 0x66717F, 0x707C8B, a)
            box(c, 2.4, 0.5, z, 0.7, 0.7, 0.7, 0x5DBB6B, 0x46A65A, 0x52B062, a)
        } else {
            box(c, 0.3, 1.4, z, 1.6, 1.4, 0.4, 0xF7F9FC, 0xD6DEE8, 0xE4EAF2, a)
            box(c, 0.3, 1.4, z + 0.4, 1.6, 0.6, 0.12, 0x7E9CFF, 0x6A86E6, 0x7590F2, a)
            box(c, 0.3, 2.7, z + 0.4, 1.6, 0.25, 0.5, 0xB98BFF, 0x9D72E6, 0xAA7EF2, a)
            box(c, 2.1, 2.3, z, 0.5, 0.5, 0.5, 0xB07A4A, 0x8E5E35, 0x9D6A3E, a)
            box(c, 4.0 - 1.2, 0.3, z, 1.0, 1.6, 0.55, 0xFFFFFF, 0xDCE3EA, 0xE8EDF2, a)
            polygon(c, [ [4.0 - 1.1, 0.4, z + 0.56], [4.0 - 0.3, 0.4, z + 0.56], [4.0 - 0.3, 1.8, z + 0.56], [4.0 - 1.1, 1.8, z + 0.56], ], 0xBFE3F2, a)
        }
    }
    private func drawRoof(_ c: CGContext, _ z: Double, _ a: Double) {
        let h = 1.7
        box(c, 0, 0, z - 0.2, 4.0, 3.0, 0.2, 0xD9CBB6, 0xBCAE98, 0xC9BBA5, a)
        polygon(c, [ [0, 0, z], [0, 3.0, z], [0, 3.0 / 2, z + h], ], 0xF0E6D8, a)
        polygon(c, [ [-0.15, -0.15, z], [4.0 + 0.15, -0.15, z], [4.0 + 0.15, 3.0 / 2, z + h], [-0.15, 3.0 / 2, z + h], ], 0x5B6475, a)
        for i in 1..<5 {
            let k = Double(i) / 5
            let q0 = point(-0.15, -0.15 + 1.65 * k, z + h * k)
            let q1 = point(4.15, -0.15 + 1.65 * k, z + h * k)
            c.beginPath(); c.move(to: q0); c.addLine(to: q1)
            c.setStrokeColor(Self.uiColor(0x4A5262, a).cgColor); c.setLineWidth(1.2); c.strokePath()
        }
        box(c, 4.0 - 1.1, 3.0 / 2 - 0.2, z + h * 0.5, 0.45, 0.45, 1.2, 0xC97B5A, 0xA9624A, 0xB86D51, a)
        polygon(c, [ [0.6, 0.0, z + 0.6], [1.4, 0.0, z + 0.6], [1.4, 0.4, z + 0.98], [0.6, 0.4, z + 0.98], ], 0x7FB4E8, a)
    }
    private static let shadowShape = Path(ellipseIn: CGRect(x: -2.6, y: -0.9, width: 5.2, height: 1.8))
    func draw(_ c: GraphicsContext, _ f: DollhouseFloorsMotion.Frame, reduced: Bool) {
        let (l1, l2) = DollhouseFloorsMotion.lifts(f.e, reduced)
        let bounds = CGRect(origin: .zero, size: size)
        c.draw(Image(uiImage: plate), in: bounds)
        func layer(_ image: UIImage, _ lift: Double, _ alpha: Double) {
            var d = c; d.opacity = alpha
            d.draw(Image(uiImage: image), in: bounds.offsetBy(dx: 0, dy: -lift * s))
        }
        func shadow(_ z: Double, _ amount: Double) {
            let p = point(2, 1.5, z)
            var shadow = c
            shadow.translateBy(x: p.x, y: p.y)
            shadow.scaleBy(x: s, y: s)
            shadow.fill(Self.shadowShape, with: .color(Self.color(0x3A2E22, 0.1 * min(1, amount))))
        }
        layer(storeys[0], 0, 1 - 0.62 * f.selK[0])
        if l1 > 0.05 { shadow(2.42, l1) }
        layer(storeys[1], l1, 1 - 0.62 * f.selK[1])
        if l2 - l1 > 0.05 { shadow(2.65 + l1 + 2.42, l2 - l1) }
        layer(roof, l2, reduced ? 1 - f.e * 0.9 : 1 - 0.62 * f.selK[2])
    }
}
