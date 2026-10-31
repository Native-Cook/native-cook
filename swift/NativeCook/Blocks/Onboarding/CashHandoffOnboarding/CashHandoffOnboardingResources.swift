import AVFoundation
import CoreText
import SwiftUI
import UIKit

final class CashHandoffOnboardingResources {
    static let shared = CashHandoffOnboardingResources()
    let dollar: Path
    let plus: Path
    init() {
        for name in ["Anton-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold"] {
            if let url = Bundle.main.url(forResource: "CashHandoffOnboarding-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        func glyphPath(_ text: String, _ size: Double) -> Path {
            let font = (UIFont(name: "Inter24pt-Bold", size: size) ?? UIFont.boldSystemFont(ofSize: size)) as CTFont
            let chars = Array(text.utf16)
            var glyphs = [CGGlyph](repeating: 0, count: chars.count)
            CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
            var widths = [CGSize](repeating: .zero, count: chars.count)
            CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &widths, chars.count)
            let path = CGMutablePath()
            var x = 0.0
            for i in glyphs.indices {
                if let glyph = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
                    path.addPath(glyph, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
                }
                x += widths[i].width
            }
            return Path(path)
        }
        dollar = glyphPath("$", 15)
        plus = glyphPath("+$50", 22)
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
}

final class CashHandoffOnboardingAudio {
    private var players: [String: AVAudioPlayer] = [:]
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
        for name in ["flutter", "catch", "return"] {
            if let url = Bundle.main.url(forResource: "CashHandoffOnboarding-cho-\(name)", withExtension: "wav"),
               let player = try? AVAudioPlayer(contentsOf: url) {
                player.volume = name == "flutter" ? 0.55 : 0.75
                player.prepareToPlay()
                players[name] = player
            }
        }
    }
    func play(_ name: String) { if let p = players[name] { p.currentTime = 0; p.play() } }
    func stop() { for p in players.values { p.stop() } }
}
