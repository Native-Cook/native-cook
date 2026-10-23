// Vendored from expo-content-transition 0.1.7 (ios/Records/GlowknobGlyphSlot.swift)
// https://github.com/rit3zh/expo-content-transition — MIT License, see THIRD_PARTY.md.
// Unmodified except where marked "Glowknob:".

import Foundation

struct GlowknobGlyphSlot: Hashable {
  let role: GlowknobSlotRole
  let index: Int
}

struct GlowknobTextUnit {
  let text: String
  let utf16Offset: Int

  var isDigit: Bool {
    text.count == 1 && text.first?.isNumber == true && text.first?.isASCII == true
  }
}
