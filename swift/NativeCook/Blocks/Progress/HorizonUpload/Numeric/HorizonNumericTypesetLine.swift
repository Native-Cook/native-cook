// Native percentage engine from expo-content-transition (MIT; see HorizonUpload-Numeric-LICENSE.txt).
// Namespaced only; the original glyph reconciliation, springs and blur math are preserved.
import CoreGraphics

struct HorizonNumericGlyphPlacement {
  let unit: String
  let slot: HorizonNumericGlyphSlot
  let x: CGFloat
  let advance: CGFloat
}

struct HorizonNumericTypesetLine {
  let placements: [HorizonNumericGlyphPlacement]
  let width: CGFloat
  let height: CGFloat
}

struct HorizonNumericGlyphImage {
  let image: CGImage
  let scale: CGFloat
}
