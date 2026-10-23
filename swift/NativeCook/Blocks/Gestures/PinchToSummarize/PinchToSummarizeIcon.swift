import CoreText
import SwiftUI

/// A block-local Ionicons renderer matching the React Native implementation.
///
/// The font is bundled with this block so the icon geometry does not depend on
/// SF Symbols or on the set of fonts installed by the host app. CoreText
/// registration is lazy and happens once per process; the custom font name is
/// read from the font itself instead of being guessed.
struct PinchToSummarizeIcon: View {
    let name: String
    let size: CGFloat
    let color: Color

    var body: some View {
        if let fontName = Self.registeredFontName, let glyph = Self.glyph(for: name) {
            Text(glyph)
                .font(.custom(fontName, size: size))
                .foregroundStyle(color)
        }
    }

    private static let glyphs: [String: UInt32] = [
        "person": 0xF4A5,
        "add": 0xF103,
        "chevron-up": 0xF241,
        "pricetag": 0xF4EA,
        "star": 0xF595,
        "body": 0xF19F,
        "sparkles": 0xF58C,
        "close": 0xF24A,
    ]

    private static let assetName = "PinchToSummarize-Ionicons"

    private static let registeredFontName: String? = {
        guard
            let url = Bundle.main.url(forResource: assetName, withExtension: "ttf"),
            let provider = CGDataProvider(url: url as CFURL),
            let font = CGFont(provider),
            let postScriptName = font.postScriptName
        else {
#if DEBUG
            assertionFailure("Missing PinchToSummarize-Ionicons.ttf asset")
#endif
            return nil
        }

        // A second block cannot register this same font during this closure's
        // lifetime, but CoreText may report an already-registered font if the
        // host app loaded the asset before the view did. The derived name is
        // valid in either case, so registration failure is intentionally not a
        // reason to fall back to a guessed family name.
        var registrationError: Unmanaged<CFError>?
        _ = CTFontManagerRegisterGraphicsFont(font, &registrationError)
        return postScriptName as String
    }()

    private static func glyph(for name: String) -> String? {
        guard let code = glyphs[name], let scalar = UnicodeScalar(code) else {
#if DEBUG
            assertionFailure("Unknown PinchToSummarize Ionicons glyph: \(name)")
#endif
            return nil
        }
        return String(scalar)
    }
}
