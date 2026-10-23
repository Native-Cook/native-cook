// Native percentage engine from expo-content-transition (MIT; see HorizonUpload-Numeric-LICENSE.txt).
// Namespaced only; the original glyph reconciliation, springs and blur math are preserved.
import CoreGraphics

struct HorizonNumericTransitionSprings: Equatable {
  static let referenceDuration: Double = 0.42
  static let defaultBounce: Double = 0.46

  let scale: CGFloat
  let bounce: CGFloat

  let position: HorizonNumericSpring
  let glyphScale: HorizonNumericSpring
  let offset: HorizonNumericSpring
  let opacity: HorizonNumericSpring
  let blur: HorizonNumericSpring

  init(duration: Double, bounce: Double) {
    let scale = CGFloat(min(max(duration / Self.referenceDuration, 0.1), 10))
    let bounce = CGFloat(min(max(bounce, 0), 0.95))
    self.scale = scale
    self.bounce = bounce
    self.position = HorizonNumericSpring(response: 0.5 * scale, dampingRatio: 1)
    self.glyphScale = HorizonNumericSpring(response: 0.3 * scale, dampingRatio: 1)
    self.offset = HorizonNumericSpring(response: 0.4 * scale, dampingRatio: max(1 - bounce, 0.05))
    self.opacity = HorizonNumericSpring(response: 0.42 * scale, dampingRatio: 1)
    self.blur = HorizonNumericSpring(response: 0.42 * scale, dampingRatio: 0.8)
  }

  var staggerWindow: CGFloat { 0.2 * scale }

  static func == (lhs: HorizonNumericTransitionSprings, rhs: HorizonNumericTransitionSprings) -> Bool {
    lhs.scale == rhs.scale && lhs.bounce == rhs.bounce
  }
}
