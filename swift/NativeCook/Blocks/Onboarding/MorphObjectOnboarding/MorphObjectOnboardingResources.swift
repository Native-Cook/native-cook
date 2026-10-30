import CoreText
import ImageIO
import SwiftUI
import UIKit

final class MorphObjectOnboardingResources {
    static let shared = MorphObjectOnboardingResources()
    struct Label {
        let path: Path
        let width: Double
        let size: Double
    }
    let titles: [[Label]]
    let subtitles: [[Label]]
    let next: Label
    let start: Label
    let skip: Label
    let frames: [CGImage]
    let keys: [CGImage]

    private init() {
        for weight in ["Regular", "Medium", "Bold"] {
            if let url = Self.url("Poppins-\(weight)", "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        titles = MorphObjectOnboardingTheme.titles.map { $0.map { Self.label($0, weight: "Bold", size: 26) } }
        subtitles = MorphObjectOnboardingTheme.subtitles.map { $0.map { Self.label($0, weight: "Regular", size: 15.6) } }
        next = Self.label("Continue Now", weight: "Medium", size: 16)
        start = Self.label("Let's start", weight: "Medium", size: 16)
        skip = Self.label("Skip", weight: "Regular", size: 13.5)
        let atlas = Self.image("morph-atlas")
        frames = (0..<33).map { index in
            let source = CGRect(x: (index % 6) * 256, y: (index / 6) * 256, width: 256, height: 256)
            return atlas.cropping(to: source)!
        }
        keys = ["morph-clock", "morph-laptop", "morph-book"].map { Self.image($0) }
    }
    static func url(_ name: String, _ ext: String) -> URL? {
        Bundle.main.url(forResource: "MorphObjectOnboarding-\(name)", withExtension: ext)
    }
    private static func image(_ name: String) -> CGImage {
        guard let url = url(name, "webp"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
        else { preconditionFailure("Missing MorphObjectOnboarding asset: \(name)") }
        return image
    }
    private static func label(_ text: String, weight: String, size: Double) -> Label {
        let font = CTFontCreateWithName("Poppins-\(weight)" as CFString, size, nil)
        let characters = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: characters.count)
        CTFontGetGlyphsForCharacters(font, characters, &glyphs, characters.count)
        var advances = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
        let path = CGMutablePath()
        var cursor = 0.0
        for (index, glyph) in glyphs.enumerated() {
            if let outline = CTFontCreatePathForGlyph(font, glyph, nil) {
                var transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: cursor, ty: 0)
                if let flipped = outline.copy(using: &transform) { path.addPath(flipped) }
            }
            cursor += advances[index].width
        }
        return Label(path: Path(path), width: cursor, size: size)
    }
}
