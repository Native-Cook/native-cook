// Vendored from expo-content-transition 0.1.7 (ios/Enums/GlowknobNumericTextAlignment.swift)
// https://github.com/rit3zh/expo-content-transition — MIT License, see THIRD_PARTY.md.
// Unmodified except where marked "Glowknob:".

import Foundation

enum GlowknobNumericTextAlignment: String {
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
