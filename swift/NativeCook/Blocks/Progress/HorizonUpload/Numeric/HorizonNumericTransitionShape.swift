// Native percentage engine from expo-content-transition (MIT; see HorizonUpload-Numeric-LICENSE.txt).
// Namespaced only; the original glyph reconciliation, springs and blur math are preserved.
import CoreGraphics

struct HorizonNumericTransitionShape: Equatable {
  var blurIntensity: CGFloat = 1
  var maxBlurRadius: CGFloat = .greatestFiniteMagnitude
  var enterScale: CGFloat = Defaults.enterScale
  var travelRatio: CGFloat = Defaults.travelRatio

  enum Defaults {
    static let enterScale: CGFloat = 0.4
    static let travelRatio: CGFloat = 1.0 / 3.0
    static let blurIntensity: CGFloat = 1
  }
}
