// Vendored from expo-content-transition 0.1.7 (ios/GlowknobGlyphs/GlowknobGlyphSlots.swift)
// https://github.com/rit3zh/expo-content-transition — MIT License, see THIRD_PARTY.md.
// Unmodified except where marked "Glowknob:".

import Foundation

enum GlowknobGlyphSlots {
  static func splitUnits(_ text: String) -> [GlowknobTextUnit] {
    var units: [GlowknobTextUnit] = []
    units.reserveCapacity(text.count)
    var offset = 0
    for character in text {
      units.append(GlowknobTextUnit(text: String(character), utf16Offset: offset))
      offset += String(character).utf16.count
    }

    return units
  }

  static func compute(
    units: [GlowknobTextUnit],
    decimalSeparator: Character,
    leftAnchored: Bool
  ) -> [GlowknobGlyphSlot] {
    guard !units.isEmpty else { return [] }

    guard let firstDigit = units.firstIndex(where: { $0.isDigit }) else {
      return units.indices.map { index in
        GlowknobGlyphSlot(role: .core, index: leftAnchored ? index : index - units.count)
      }
    }

    let lastDigit = units.lastIndex(where: { $0.isDigit }) ?? firstDigit

    var anchor = lastDigit + 1
    for index in stride(from: lastDigit, through: firstDigit, by: -1)
    where units[index].text == String(decimalSeparator) {
      anchor = index
      break
    }

    return units.indices.map { index in
      if index < firstDigit {
        return GlowknobGlyphSlot(role: .prefix, index: index)
      }

      if index > lastDigit {
        return GlowknobGlyphSlot(role: .suffix, index: units.count - 1 - index)
      }

      return GlowknobGlyphSlot(role: .core, index: index - anchor)
    }
  }

  static func numericValue(_ text: String, decimalSeparator: Character) -> Double? {
    var builder = ""
    var seenDigit = false
    var seenSeparator = false

    for character in text {
      if character.isNumber, character.isASCII {
        builder.append(character)
        seenDigit = true
      } else if character == decimalSeparator, seenDigit, !seenSeparator {
        builder.append(".")
        seenSeparator = true
      } else if character == "-" || character == "\u{2212}", builder.isEmpty {
        builder.append("-")
      }
    }

    guard seenDigit else { return nil }
    return Double(builder)
  }
}
