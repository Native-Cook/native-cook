import CoreText
import Foundation

/// The hint under the console is set in Manrope. The original app named the two
/// files under `UIAppFonts` in its Info.plist; here they are registered when the
/// block first opens, so the block brings its fonts without touching the app.
enum GlowknobFonts {
  private static let registered: Void = {
    for name in ["Manrope_400Regular", "Manrope_600SemiBold"] {
      guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else { continue }
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
  }()

  static func register() { registered }
}
