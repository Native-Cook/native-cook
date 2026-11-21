import SwiftUI
import UIKit
import CoreText

enum PressedStampCardResources {
    enum Face: String { case serif = "PSCSerif-SemiBold", ring = "PSCSerif-Bold", regular = "PSCInter-Regular", medium = "PSCInter-Medium", semibold = "PSCInter-SemiBold", bold = "PSCInter-Bold" }
    static let leather = image("PressedStampCard-ps-leather")
    static let paper = image("PressedStampCard-ps-paper")
    static let seal = image("PressedStampCard-ps-seal-art")
    static let dieBase = image("PressedStampCard-ps-die-base")
    static let dieKnob = image("PressedStampCard-ps-die-knob")
    private static func image(_ name: String) -> UIImage { UIImage(named: name)?.preparingForDisplay() ?? UIImage() }
    private static let fontNames: [Face: String] = {
        var names: [Face: String] = [:]
        for face in [Face.serif, .ring, .regular, .medium, .semibold, .bold] {
            guard let url = Bundle.main.url(forResource: "PressedStampCard-\(face.rawValue)", withExtension: "ttf"), let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider), let name = font.postScriptName as String? else { continue }
            CTFontManagerRegisterGraphicsFont(font, nil)
            names[face] = name
        }
        return names
    }()
    static func font(_ face: Face, size: CGFloat) -> Font {
        if let name = fontNames[face] { return .custom(name, fixedSize: size) }
        return .system(size: size, weight: face == .semibold || face == .bold ? .semibold : .regular, design: face == .serif || face == .ring ? .serif : .default)
    }
    static func uiFont(_ face: Face, size: CGFloat) -> UIFont { fontNames[face].flatMap { UIFont(name: $0, size: size) } ?? .systemFont(ofSize: size, weight: .semibold) }
    static let page: UIImage = {
        let format = UIGraphicsImageRendererFormat(); format.scale = 2
        return UIGraphicsImageRenderer(size: CGSize(width: 402, height: 874), format: format).image { renderer in
            UIColor(red: 244/255, green: 237/255, blue: 224/255, alpha: 1).setFill(); renderer.fill(CGRect(x: 0, y: 0, width: 402, height: 874))
            renderer.cgContext.setBlendMode(.softLight); renderer.cgContext.setAlpha(0.35)
            paper.drawAsPattern(in: CGRect(x: 0, y: 0, width: 402, height: 874))
        }
    }()
    // The original ink art plus genuine glyph advances on the two circular baselines; generated once, never per frame.
    static let ink: UIImage = {
        let format = UIGraphicsImageRendererFormat(); format.scale = 6
        return UIGraphicsImageRenderer(size: CGSize(width: 56, height: 56), format: format).image { renderer in
            seal.draw(in: CGRect(x: 0, y: 0, width: 56, height: 56))
            let font = uiFont(.ring, size: 5.6)
            let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor(red: 46/255, green: 31/255, blue: 14/255, alpha: 1)]
            for (value, upper) in [("FRESHLY ROASTED", true), ("COFFEE CLUB", false)] {
                let glyphs = value.map(String.init), widths = glyphs.map { ($0 as NSString).size(withAttributes: attributes).width }, radius: CGFloat = upper ? 20.8 : 24.4
                var pen = -(widths.reduce(0, +) + CGFloat(glyphs.count - 1) * 0.25) / 2
                for (index, glyph) in glyphs.enumerated() {
                    let a = (pen + widths[index] / 2) / radius, theta = upper ? -.pi / 2 + a : .pi / 2 - a
                    let context = renderer.cgContext
                    context.saveGState(); context.translateBy(x: 28 + cos(theta) * radius, y: 28 + sin(theta) * radius)
                    context.rotate(by: upper ? theta + .pi / 2 : theta - .pi / 2)
                    (glyph as NSString).draw(at: CGPoint(x: -widths[index] / 2, y: -font.ascender), withAttributes: attributes)
                    context.restoreGState(); pen += widths[index] + 0.25
                }
            }
        }
    }()
}
