// Vendored from expo-content-transition 0.1.7 (ios/Records/GlowknobNumericTextSpec.swift)
// https://github.com/rit3zh/expo-content-transition — MIT License, see THIRD_PARTY.md.
// Unmodified except where marked "Glowknob:".

import Foundation

struct GlowknobNumericTextSpec: Equatable {
  var text: String = ""
  var alignment: GlowknobNumericTextAlignment = .leading
  var direction: GlowknobTransitionDirection = .auto
  var decimalSeparator: Character = "."
  var blurEnabled: Bool = true
  var shape = GlowknobTransitionShape()
  var clipEnabled: Bool = true
  var animationsEnabled: Bool = true
  var duration: Double = GlowknobTransitionSprings.referenceDuration
  var bounce: Double = GlowknobTransitionSprings.defaultBounce

  var blurActive: Bool { blurEnabled && shape.blurIntensity > 0 }

  var effectiveShape: GlowknobTransitionShape {
    guard blurActive else {
      var shape = shape
      shape.blurIntensity = 0
      return shape
    }

    return shape
  }
}
