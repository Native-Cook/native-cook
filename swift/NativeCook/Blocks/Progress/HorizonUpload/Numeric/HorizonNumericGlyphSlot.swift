// Native percentage engine from expo-content-transition (MIT; see HorizonUpload-Numeric-LICENSE.txt).
// Namespaced only; the original glyph reconciliation, springs and blur math are preserved.
import Foundation

struct HorizonNumericGlyphSlot: Hashable {
  let role: HorizonNumericSlotRole
  let index: Int
}

struct HorizonNumericTextUnit {
  let text: String
  let utf16Offset: Int

  var isDigit: Bool {
    text.count == 1 && text.first?.isNumber == true && text.first?.isASCII == true
  }
}
