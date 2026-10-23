// Vendored from expo-content-transition 0.1.7 (ios/Records/GlowknobTransitionShape.swift)
// https://github.com/rit3zh/expo-content-transition — MIT License, see THIRD_PARTY.md.
// Unmodified except where marked "Glowknob:".

import CoreGraphics

struct GlowknobTransitionShape: Equatable {
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
