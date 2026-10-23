import SwiftUI
import UIKit

// Native text inside the artwork uses the platform system face, as in the
// reference, rather than the app's display font (typography.ts).
enum GlowknobArtText {
  static let systemFace = "System"
}

/// React Native's `fontScale` on iOS (RCTFontSizeMultiplier): the multiplier
/// for each content size category. RN text with `allowFontScaling` (the
/// default) multiplies `fontSize` and `lineHeight` by it.
func glowknobRnFontScale(_ category: UIContentSizeCategory) -> Double {
  switch category {
  case .extraSmall: return 0.823
  case .small: return 0.882
  case .medium: return 0.941
  case .large: return 1.0
  case .extraLarge: return 1.118
  case .extraExtraLarge: return 1.235
  case .extraExtraExtraLarge: return 1.353
  case .accessibilityMedium: return 1.786
  case .accessibilityLarge: return 2.143
  case .accessibilityExtraLarge: return 2.643
  case .accessibilityExtraExtraLarge: return 3.143
  case .accessibilityExtraExtraExtraLarge: return 3.571
  default: return 1.0
  }
}

/// Manrope text in one of the page's two roles (theme.ts `type`), scaled by
/// `fontScale` like RN's `<Text>`.
enum GlowknobPageType {
  /// label: Manrope 600, 13/19.
  static func label(_ text: String, scale: Double, color: GlowknobRGBA) -> GlowknobRNText {
    GlowknobRNText(text: text, fontName: "Manrope-SemiBold", size: 13 * scale, lineHeight: 19 * scale, color: color)
  }
  /// caption: Manrope 400, 12/18.
  static func caption(_ text: String, scale: Double, color: GlowknobRGBA) -> GlowknobRNText {
    GlowknobRNText(text: text, fontName: "Manrope-Regular", size: 12 * scale, lineHeight: 18 * scale, color: color)
  }
}

/// i18n.ts: English unless the device's preferred language is Vietnamese.
enum GlowknobL10n {
  static var isVietnamese: Bool {
    (Locale.preferredLanguages.first.map { Locale(identifier: $0).language.languageCode?.identifier } ?? nil) == "vi"
  }
  static func t(_ english: String, _ vietnamese: String? = nil) -> String {
    isVietnamese ? (vietnamese ?? english) : english
  }
}

/// RN `<Text>` with an explicit lineHeight, single line: the line box is
/// `lineHeight` tall and the glyphs sit centered in it (RN iOS offsets the
/// baseline by half the extra leading).
struct GlowknobRNText: View {
  let text: String
  let fontName: String
  let size: Double
  let lineHeight: Double
  let color: GlowknobRGBA

  var body: some View {
    Text(text)
      .font(.custom(fontName, fixedSize: size))
      .foregroundStyle(color.color)
      .lineLimit(1)
      .fixedSize()
      .frame(height: lineHeight)
  }
}

/// GlowknobYoga's `roundLayoutResultsToPixelGrid`: React Native rounds each node's
/// position (relative to its parent) to the pixel grid on its own, and its
/// size from its rounded absolute edges. Overlays placed with these frames
/// land on the same device pixels as in the RN build.
struct GlowknobYoga {
  let pointScale: Double

  func round(_ value: Double, ceil forceCeil: Bool = false) -> Double {
    var scaled = value * pointScale
    var fraction = fmod(scaled, 1.0)
    if fraction < 0 { fraction += 1 }
    func near(_ a: Double, _ b: Double) -> Bool { abs(a - b) < 0.0001 }
    if near(fraction, 0) {
      scaled -= fraction
    } else if near(fraction, 1) {
      scaled = scaled - fraction + 1
    } else if forceCeil {
      scaled = scaled - fraction + 1
    } else {
      scaled = scaled - fraction + ((fraction > 0.5 || near(fraction, 0.5)) ? 1 : 0)
    }
    return scaled / pointScale
  }

  /// A child at `rel` (relative to its parent) whose parent's unrounded
  /// absolute origin is `parent`: its rounded relative frame, and its own
  /// unrounded absolute origin for its children.
  func node(_ rel: CGRect, parent: CGPoint, text: Bool = false) -> (frame: CGRect, absolute: CGPoint) {
    let absX = parent.x + rel.minX, absY = parent.y + rel.minY
    let w = round(absX + rel.width, ceil: text) - round(absX, ceil: text)
    let h = round(absY + rel.height, ceil: text) - round(absY, ceil: text)
    return (CGRect(x: round(rel.minX), y: round(rel.minY), width: w, height: h), CGPoint(x: absX, y: absY))
  }
}
