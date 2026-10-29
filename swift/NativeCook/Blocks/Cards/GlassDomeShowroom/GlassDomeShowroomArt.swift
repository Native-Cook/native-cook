import SwiftUI
import UIKit

/// One car of the invented Nuvra line-up, with the colour wash it brings to the screen.
struct GlassDomeShowroomCar {
    let model: String
    let trim: String
    let info: String
    let file: String
    /// Wash: top, middle (55 % down) and bottom of the screen.
    let top: UInt32
    let mid: UInt32
    let bottom: UInt32
}

enum GlassDomeShowroomArt {
    static let cars: [GlassDomeShowroomCar] = [
        .init(model: "Kite 3", trim: "Spark", info: "268 mi range · from $219/mo", file: "cobalt", top: 0x5A93DA, mid: 0x2B5FAA, bottom: 0x0D2246),
        .init(model: "Torva", trim: "Line", info: "301 mi range · from $259/mo", file: "red", top: 0xCF4A57, mid: 0x9C1D2C, bottom: 0x36060D),
        .init(model: "Orla 4", trim: "Pro", info: "322 mi range · from $289/mo", file: "aqua", top: 0x3DB8CF, mid: 0x14879E, bottom: 0x063A47),
        .init(model: "Nimbus", trim: "Lux Pro", info: "347 mi range · from $339/mo", file: "pearl", top: 0xA8B3BE, mid: 0x75818E, bottom: 0x252C34),
        .init(model: "Zest", trim: "Sport", info: "244 mi range · from $199/mo", file: "lime", top: 0xADBD3A, mid: 0x78881D, bottom: 0x262E08),
    ]

    static let heartRed: UInt32 = 0xFF3B4E
    static let heartPink: UInt32 = 0xFF8A9A

    /// The car artwork, decoded once.
    static let images: [UIImage] = cars.map { car in
        guard let path = Bundle.main.path(forResource: "GlassDomeShowroom-car-\(car.file)", ofType: "webp"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }

    static func rgb(_ hex: UInt32) -> (Double, Double, Double) {
        (Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        let (r, g, b) = rgb(hex)
        return Color(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }

    static func mix(_ a: UInt32, _ b: UInt32, _ f: Double, _ alpha: Double = 1) -> Color {
        let x = rgb(a), y = rgb(b)
        return Color(.sRGB, red: x.0 + (y.0 - x.0) * f, green: x.1 + (y.1 - x.1) * f, blue: x.2 + (y.2 - x.2) * f, opacity: alpha)
    }

    /// The wash for carousel position φ: the two nearest cars' colours mixed.
    static func wash(_ phi: Double) -> (top: Color, mid: Color, bottom: Color) {
        let n = cars.count
        let base = floor(phi)
        let f = phi - base
        let i = ((Int(base) % n) + n) % n
        let a = cars[i], b = cars[(i + 1) % n]
        return (mix(a.top, b.top, f), mix(a.mid, b.mid, f), mix(a.bottom, b.bottom, f))
    }

    /// A heart `size` pt wide centred on the origin (the 24-pt "favourite" glyph, redrawn).
    static func heart(size: Double) -> Path {
        let s = size / 20
        func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: (x - 12) * s, y: (y - 12.18) * s) }
        var path = Path()
        path.move(to: p(12, 21.35))
        path.addLine(to: p(10.55, 20.03))
        path.addCurve(to: p(2, 8.5), control1: p(5.4, 15.36), control2: p(2, 12.28))
        path.addCurve(to: p(7.5, 3), control1: p(2, 5.42), control2: p(4.42, 3))
        path.addCurve(to: p(12, 5.09), control1: p(9.24, 3), control2: p(10.91, 3.81))
        path.addCurve(to: p(16.5, 3), control1: p(13.09, 3.81), control2: p(14.76, 3))
        path.addCurve(to: p(22, 8.5), control1: p(19.58, 3), control2: p(22, 5.42))
        path.addCurve(to: p(13.45, 20.04), control1: p(22, 12.28), control2: p(18.6, 15.36))
        path.closeSubpath()
        return path
    }

    static func hash(_ i: Double) -> Double {
        let v = sin(i * 12.9898) * 43758.5453
        return v - floor(v)
    }
}
