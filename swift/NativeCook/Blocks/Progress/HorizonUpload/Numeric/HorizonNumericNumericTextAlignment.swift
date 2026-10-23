// Native percentage engine from expo-content-transition (MIT; see HorizonUpload-Numeric-LICENSE.txt).
// Namespaced only; the original glyph reconciliation, springs and blur math are preserved.
import Foundation

enum HorizonNumericNumericTextAlignment: String {
  case leading
  case center
  case trailing

  init(propValue: String?) {
    switch propValue {
    case "center": self = .center
    case "end": self = .trailing
    default: self = .leading
    }
  }
}
