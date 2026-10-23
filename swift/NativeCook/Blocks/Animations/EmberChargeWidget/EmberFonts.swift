import CoreText
import SwiftUI
import UIKit

/// Block-local font registration, with a system fallback if a bundled font cannot load.
enum EmberFonts {
  private static let registered: Void = {
    for name in ["Manrope_400Regular", "Manrope_600SemiBold"] {
      guard
        let url = Bundle.main.url(forResource: "EmberChargeWidget-" + name, withExtension: "ttf")
      else { continue }
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
  }()

  static func register() { registered }

  static func font(_ size: Double, semibold: Bool = false) -> Font {
    let name = semibold ? "Manrope-SemiBold" : "Manrope-Regular"
    return UIFont(name: name, size: size) == nil
      ? .system(size: size, weight: semibold ? .semibold : .regular)
      : .custom(name, fixedSize: size)
  }
}
