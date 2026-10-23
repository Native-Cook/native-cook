// Vendored from expo-content-transition 0.1.7 (ios/Animation/GlowknobTransitionSprings.swift)
// https://github.com/rit3zh/expo-content-transition — MIT License, see THIRD_PARTY.md.
// Unmodified except where marked "Glowknob:".

import CoreGraphics

struct GlowknobTransitionSprings: Equatable {
  static let referenceDuration: Double = 0.42
  static let defaultBounce: Double = 0.46

  let scale: CGFloat
  let bounce: CGFloat

  let position: GlowknobSpring
  let glyphScale: GlowknobSpring
  let offset: GlowknobSpring
  let opacity: GlowknobSpring
  let blur: GlowknobSpring

  init(duration: Double, bounce: Double) {
    let scale = CGFloat(min(max(duration / Self.referenceDuration, 0.1), 10))
    let bounce = CGFloat(min(max(bounce, 0), 0.95))
    self.scale = scale
    self.bounce = bounce
    self.position = GlowknobSpring(response: 0.5 * scale, dampingRatio: 1)
    self.glyphScale = GlowknobSpring(response: 0.3 * scale, dampingRatio: 1)
    self.offset = GlowknobSpring(response: 0.4 * scale, dampingRatio: max(1 - bounce, 0.05))
    self.opacity = GlowknobSpring(response: 0.42 * scale, dampingRatio: 1)
    self.blur = GlowknobSpring(response: 0.42 * scale, dampingRatio: 0.8)
  }

  var staggerWindow: CGFloat { 0.2 * scale }

  static func == (lhs: GlowknobTransitionSprings, rhs: GlowknobTransitionSprings) -> Bool {
    lhs.scale == rhs.scale && lhs.bounce == rhs.bounce
  }
}
