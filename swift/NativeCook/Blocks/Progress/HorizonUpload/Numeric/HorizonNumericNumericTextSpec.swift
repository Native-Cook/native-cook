// Native percentage engine from expo-content-transition (MIT; see HorizonUpload-Numeric-LICENSE.txt).
// Namespaced only; the original glyph reconciliation, springs and blur math are preserved.
import Foundation

struct HorizonNumericNumericTextSpec: Equatable {
  var text: String = ""
  var alignment: HorizonNumericNumericTextAlignment = .leading
  var direction: HorizonNumericTransitionDirection = .auto
  var decimalSeparator: Character = "."
  var blurEnabled: Bool = true
  var shape = HorizonNumericTransitionShape()
  var clipEnabled: Bool = true
  var animationsEnabled: Bool = true
  var duration: Double = HorizonNumericTransitionSprings.referenceDuration
  var bounce: Double = HorizonNumericTransitionSprings.defaultBounce

  var blurActive: Bool { blurEnabled && shape.blurIntensity > 0 }

  var effectiveShape: HorizonNumericTransitionShape {
    guard blurActive else {
      var shape = shape
      shape.blurIntensity = 0
      return shape
    }

    return shape
  }
}
