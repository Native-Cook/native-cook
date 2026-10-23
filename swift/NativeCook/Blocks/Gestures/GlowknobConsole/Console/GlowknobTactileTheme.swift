// Neutral material and channel light colors measured from the user's reference.
// Scoped to this specimen; the surrounding page keeps its own theme.
enum GlowknobTactile {
  static let warm = [GlowknobRGBA("#FFF797"), GlowknobRGBA("#FFA65D")]
  static let cool = [GlowknobRGBA("#91F8D1"), GlowknobRGBA("#44B3F2")]
  // warm → cool per gradient stop, sampled in OKLCH at 0/.25/.5/.75/1. A plain
  // RGB mix of orange and blue passes through gray (#A2ADA7, C≈0.014) mid
  // handoff; this path keeps chroma (C≈0.136) the whole way.
  static let transferAt: [Double] = [0, 0.25, 0.5, 0.75, 1]
  static let transfer: [[GlowknobRGBA]] = [
    ["#FFF797", "#E3FAA0", "#C7FBAE", "#AAFBBF", "#91F8D1"].map(GlowknobRGBA.init),
    ["#FFA65D", "#C8BC46", "#71CA80", "#00C7C5", "#44B3F2"].map(GlowknobRGBA.init),
  ]
  static let housing = ["#2C2C2C", "#282828", "#252525"].map(GlowknobRGBA.init)
  static let bezel = ["#484848", "#2E2E2E", "#111111"].map(GlowknobRGBA.init)
  static let dial = ["#323232", "#242424"].map(GlowknobRGBA.init)
  static let well = GlowknobRGBA("#151515")
  static let shadow = GlowknobRGBA("#000000")
  static let edge = GlowknobRGBA.rgba(255, 255, 255, 0.20)
  static let groove = GlowknobRGBA("#161616")
  static let text = GlowknobRGBA("#EEEEEE")
  // GlowknobReadout glyphs where the light passes underneath them.
  static let ink = GlowknobRGBA("#151515")
  // The page around the specimen is white; foregrounds that sit on it
  // directly (large-text readout) use this.
  static let page = GlowknobRGBA("#FFFFFF")
  static let pageText = GlowknobRGBA("#1C1C1C")
  // Quiet instruction under the console (4.6:1 on the white page).
  static let hint = GlowknobRGBA("#737373")
  static let inactive = GlowknobRGBA("#666666")
  // Unselected selector glyph under Increase Contrast (≥4.5:1 on #222222).
  static let inactiveHigh = GlowknobRGBA("#9A9A9A")
  static let release = GlowknobSpringConfig.physics(stiffness: 420, damping: 32, mass: 0.8)
}

/// Motion tokens (Touchline `motion`, copied verbatim into the RN build).
enum GlowknobMotionTokens {
  static let press = 120.0
  static let fast = 150.0
  static let base = 240.0
  static let hero = 900.0
  static let easeOut = GlowknobEasing.bezier(0.23, 1, 0.32, 1)
}

/// Spacing tokens used by the page (`space`, `screenSpacing`).
enum GlowknobSpace {
  static let sm = 12.0
  static let md = 16.0
  static let edge = 20.0
  static func screenEdge(_ width: Double) -> Double { width < 375 ? md : edge }
}
