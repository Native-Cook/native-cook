import AVFoundation
import CoreText
import SwiftUI
import UIKit

/// Fonts and sound files are byte-for-byte copies of the approved reference.
enum SkyTimeScrubResources {
    static let register: Void = {
        for weight in ["Regular", "Medium", "SemiBold"] {
            if let url = Bundle.main.url(forResource: "SkyTimeScrub-Inter-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private static let typefaces: [String: CGFont] = {
        var fonts: [String: CGFont] = [:]
        for weight in ["Regular", "Medium", "SemiBold"] {
            if let url = Bundle.main.url(forResource: "SkyTimeScrub-Inter-\(weight)", withExtension: "ttf"),
               let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) { fonts[weight] = font }
        }
        return fonts
    }()
    static func font(_ weight: String, _ size: Double) -> UIFont {
        _ = register
        if let cgFont = typefaces[weight] {
            let ct = CTFontCreateWithGraphicsFont(cgFont, size, nil, nil)
            return ct as UIFont
        }
        return UIFont.systemFont(ofSize: size)
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func rgb(_ values: [Double], _ start: Int = 0, alpha: Double = 1) -> Color {
        // rgbString() rounds channels before handing them to RN's native colour parser.
        Color(.sRGB, red: floor(values[start] + 0.5) / 255, green: floor(values[start + 1] + 0.5) / 255,
              blue: floor(values[start + 2] + 0.5) / 255, opacity: alpha)
    }
    static func width(_ text: String, size: Double) -> Double {
        let ct = font("Medium", size) as CTFont
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(ct, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(ct, .horizontal, glyphs, &advances, chars.count)
        return advances.reduce(0) { $0 + $1.width }
    }
    private static let tint: [Double] = [
-18, 27, 24, 47, 9, 8, 16, 224, 48, 26, -12, 28, 25, 50, 12, 11, 19, 224, 48, 26, -6, 19, 21, 45, 82, 57, 26, 224, 48, 26, -3, 28, 41, 57,
  114, 73, 24, 224, 48, 26, -1, 31, 44, 54, 116, 64, 18, 224, 48, 26, 0, 31, 43, 51, 115, 61, 16, 255, 106, 30, 1, 33, 46, 54, 118, 63, 16,
  255, 129, 41, 2, 36, 51, 58, 122, 68, 16, 255, 171, 63, 3, 37, 54, 59, 128, 77, 18, 255, 194, 74, 4.5, 38, 54, 58, 137, 94, 22, 255, 220,
  146, 6, 38, 54, 58, 145, 115, 27, 255, 246, 218, 10, 34, 48, 55, 153, 154, 40, 255, 246, 218, 18, 22, 33, 55, 106, 141, 141, 255, 246,
  218, 30, 20, 32, 61, 106, 153, 177, 255, 246, 218, 45, 21, 33, 64, 106, 154, 182, 255, 246, 218, 62, 21, 34, 66, 105, 154, 183, 255, 246,
  218,
    ]
    static func skyTint(_ elevation: Double) -> [Double] {
        let e = min(tint[150], max(tint[0], elevation))
        var i = 0
        while i < 14 && e > tint[(i + 1) * 10] { i += 1 }
        let a = i * 10, b = a + 10, span = tint[b] - tint[a]
        let t = span > 0 ? (e - tint[a]) / span : 0
        return (1..<10).map { tint[a + $0] + (tint[b + $0] - tint[a + $0]) * t }
    }
}

@MainActor
final class SkyTimeScrubFeedback {
    var haptics = true
    private var players: [String: AVAudioPlayer] = [:]
    func configure(sound: Bool, haptics: Bool) {
        self.haptics = haptics
        guard sound, players.isEmpty else { if !sound { players.removeAll() }; return }
        // .ambient respects the ringer and mixes with other apps, like playsInSilentMode:false.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        let sources = ["tick": "tick", "tickNight": "tick-night", "clack": "clack", "swell": "swell"]
        for (cue, file) in sources {
            if let url = Bundle.main.url(forResource: "SkyTimeScrub-sts-\(file)", withExtension: "wav"),
               let player = try? AVAudioPlayer(contentsOf: url) {
                player.volume = cue == "clack" ? 0.5 : cue == "swell" ? 0.4 : 0.22
                player.prepareToPlay(); players[cue] = player
            }
        }
    }
    func cue(_ kind: String, _ sound: String = "") {
        if haptics {
            if kind == "step" { UISelectionFeedbackGenerator().selectionChanged() }
            else {
                let style: UIImpactFeedbackGenerator.FeedbackStyle
                switch kind {
                case "onset", "lowerLimb": style = .medium
                case "landing": style = .rigid
                case "upperLimb": style = .soft
                default: style = .light
                }
                UIImpactFeedbackGenerator(style: style).impactOccurred()
            }
        }
        if let player = players[sound] { player.currentTime = 0; player.play() }
    }
}
