import AVFoundation
import CoreText
import SwiftUI
import UIKit

enum GelCubeUnboxResources {
    private static var prints: [String: UIImage] = [:]
    static let register: Void = {
        for weight in ["Regular", "Medium"] {
            if let url = Bundle.main.url(forResource: "GelCubeUnbox-Inter-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func font(_ size: Double, name: String? = nil) -> UIFont {
        _ = register
        if let name {
            let face = name == "GcuInter" ? "Inter-Regular" : name == "GcuInterMedium" ? "Inter-Medium" : name
            if let font = UIFont(name: face, size: size) { return font }
        }
        // Use the bundled face even if another block has registered Inter first.
        if let url = Bundle.main.url(forResource: "GelCubeUnbox-Inter-Regular", withExtension: "ttf"),
           let provider = CGDataProvider(url: url as CFURL), let cgFont = CGFont(provider) {
            return CTFontCreateWithGraphicsFont(cgFont, size, nil, nil) as UIFont
        }
        return UIFont(name: "Inter-Regular", size: size) ?? .systemFont(ofSize: size)
    }
    /// Same TextKit 1 settings as RN's iOS RCTTextLayoutManager. Cache white alpha prints;
    /// light/cross-fade/shadow change independently without reshaping text on every frame.
    static func print(_ text: String, size: Double, width: Double, lineHeight: Double?,
                      tracking: Double, displayScale: Double, fontName: String? = nil) -> UIImage {
        let key = "\(text)|\(size)|\(width)|\(lineHeight ?? -1)|\(tracking)|\(displayScale)|\(fontName ?? "Inter-Regular")"
        if let image = prints[key] { return image }
        let font = font(size, name: fontName), paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        if let h = lineHeight { paragraph.minimumLineHeight = h; paragraph.maximumLineHeight = h }
        var attrs: [NSAttributedString.Key: Any] = [
            .font: font, .foregroundColor: UIColor.white, .kern: tracking, .paragraphStyle: paragraph
        ]
        if let h = lineHeight, h >= font.lineHeight { attrs[.baselineOffset] = (h - font.lineHeight) / 2 }
        let storage = NSTextStorage(string: text, attributes: attrs)
        let manager = NSLayoutManager(), container = NSTextContainer(size: CGSize(width: width, height: 10000))
        container.lineFragmentPadding = 0; container.maximumNumberOfLines = 0; container.lineBreakMode = .byClipping
        manager.usesFontLeading = false
        manager.addTextContainer(container); storage.addLayoutManager(manager)
        manager.ensureLayout(for: container)
        let used = manager.usedRect(for: container)
        let height = ceil(used.height * displayScale) / displayScale
        let format = UIGraphicsImageRendererFormat(); format.scale = displayScale; format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: width, height: max(1, height)), format: format).image { _ in
            manager.drawGlyphs(forGlyphRange: manager.glyphRange(for: container), at: .zero)
        }
        prints[key] = image
        return image
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    /// Reanimated interpolateColor defaults to RGB with gamma=2.2, not encoded-RGB lerp.
    static func ink(_ a: UInt32, _ b: UInt32, _ progress: Double) -> Color {
        let ca = rgb(a), cb = rgb(b), k = GelCubeUnboxMotion.clamp01(progress)
        let c = zip(ca, cb).map { pow(pow($0.0, 2.2) * (1 - k) + pow($0.1, 2.2) * k, 1 / 2.2) }
        return Color(.sRGB, red: c[0], green: c[1], blue: c[2])
    }
    static func rgb(_ hex: UInt32) -> [Double] {
        [Double(hex >> 16 & 255) / 255, Double(hex >> 8 & 255) / 255, Double(hex & 255) / 255]
    }
    static func parsed(_ hex: String) -> UInt32? {
        guard hex.count == 7, hex.first == "#", hex.dropFirst().allSatisfy({ "0123456789abcdefABCDEF".contains($0) }) else { return nil }
        return UInt32(hex.dropFirst(), radix: 16)
    }
    static func palette(_ tint: String) -> [Double] {
        let base: UInt32 = 0x2F63D8, chosen = parsed(tint) ?? base
        let colors: [UInt32] = [0x3C5180, 0x8FA6DA, 0x7FB2FF, 0xF4FAFF, 0xDCEBFF, 0x9CC4FF]
        if chosen == base { return rgb(base) + colors.flatMap { rgb($0) } }
        let original = lab(rgb(base)), selected = lab(rgb(chosen))
        let delta = atan2(selected[2], selected[1]) - atan2(original[2], original[1])
        let ratio = min(1.4, hypot(selected[1], selected[2]) / max(0.001, hypot(original[1], original[2])))
        return rgb(chosen) + colors.flatMap { hex -> [Double] in
            let c = lab(rgb(hex)), chroma = hypot(c[1], c[2]) * ratio, h = atan2(c[2], c[1]) + delta
            return fromLab([c[0], chroma * cos(h), chroma * sin(h)])
        }
    }
    private static func lab(_ c: [Double]) -> [Double] {
        let a = c.map { $0 <= 0.04045 ? $0 / 12.92 : pow(($0 + 0.055) / 1.055, 2.4) }
        let r = a[0], g = a[1], b = a[2]
        let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s]
    }
    private static func fromLab(_ c: [Double]) -> [Double] {
        let ll = c[0], a = c[1], b = c[2]
        let l = pow(ll + 0.3963377774 * a + 0.2158037573 * b, 3)
        let m = pow(ll - 0.1055613458 * a - 0.0638541728 * b, 3)
        let s = pow(ll - 0.0894841775 * a - 1.291485548 * b, 3)
        return [4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s].map {
            max(0, min(1, $0 <= 0.0031308 ? 12.92 * $0 : 1.055 * pow(max(0, $0), 1 / 2.4) - 0.055))
        }
    }
}

struct GelCubeUnboxCopy {
    var action: String, chip: String, sheetTitle: String, sheetBody: String
    static let sealed = Self(action: "Unbox", chip: "54 days", sheetTitle: "Next one in $38.56",
                             sheetBody: "Save up the required amount before\nyour Lumen Box turns into a pumpkin")
    static let opened = Self(action: "See deals", chip: "7 days", sheetTitle: "Unlocked today",
                             sheetBody: "$8 off your next order · Free delivery\nA little extra with your next coffee")
}

/// No autoplay calls this object. Ambient respects the silent switch and mixes with other audio.
@MainActor
final class GelCubeUnboxFeedback {
    private var air: AVAudioPlayer?, contact: AVAudioPlayer?
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()
    func cue(_ cue: String, sound: Bool) {
        switch cue {
        case "seal": selection.selectionChanged()
        case "close": medium.impactOccurred()
        case "threshold": light.impactOccurred()
        default: soft.impactOccurred()
        }
        guard sound, cue == "seal" || cue == "close" else { return }
        if air == nil {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
            if let url = Bundle.main.url(forResource: "GelCubeUnbox-air", withExtension: "wav") { air = try? AVAudioPlayer(contentsOf: url) }
            if let url = Bundle.main.url(forResource: "GelCubeUnbox-contact", withExtension: "wav") { contact = try? AVAudioPlayer(contentsOf: url) }
            air?.prepareToPlay(); contact?.prepareToPlay()
        }
        let player = cue == "seal" ? air : contact
        player?.currentTime = 0
        player?.play()
    }
}
