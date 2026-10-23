// Vendored from expo-content-transition 0.1.7 (ios/Records/GlowknobTypesetLine.swift)
// https://github.com/rit3zh/expo-content-transition — MIT License, see THIRD_PARTY.md.
// Unmodified except where marked "Glowknob:".

import CoreGraphics

struct GlowknobGlyphPlacement {
  let unit: String
  let slot: GlowknobGlyphSlot
  let x: CGFloat
  let advance: CGFloat
}

struct GlowknobTypesetLine {
  let placements: [GlowknobGlyphPlacement]
  let width: CGFloat
  let height: CGFloat
}

struct GlowknobGlyphImage {
  let image: CGImage
  let scale: CGFloat
}
