// Neutral material and channel light colors measured from the user's reference.
// Scoped to this specimen; Touchline's surrounding screen keeps its own theme.
export const tactile = {
  warm: ["#FFF797", "#FFA65D"] as const,
  cool: ["#91F8D1", "#44B3F2"] as const,
  // warm → cool per gradient stop, sampled in OKLCH at 0/.25/.5/.75/1. A plain
  // RGB mix of orange and blue passes through gray (#A2ADA7, C≈0.014) mid
  // handoff; this path keeps chroma (C≈0.136) the whole way.
  transferAt: [0, 0.25, 0.5, 0.75, 1],
  transfer: [
    ["#FFF797", "#E3FAA0", "#C7FBAE", "#AAFBBF", "#91F8D1"],
    ["#FFA65D", "#C8BC46", "#71CA80", "#00C7C5", "#44B3F2"],
  ] as const,
  housing: ["#2C2C2C", "#282828", "#252525"] as const,
  bezel: ["#484848", "#2E2E2E", "#111111"] as const,
  dial: ["#323232", "#242424"] as const,
  well: "#151515",
  shadow: "#000000",
  edge: "rgba(255,255,255,0.20)",
  groove: "#161616",
  text: "#EEEEEE",
  // Readout glyphs where the light passes underneath them.
  ink: "#151515",
  // The Showcase page around the specimen is white; foregrounds that sit on
  // it directly (large-text readout) use this.
  page: "#FFFFFF",
  pageText: "#1C1C1C",
  // Quiet instruction under the console (4.6:1 on the white page).
  hint: "#737373",
  inactive: "#666666",
  // Unselected selector glyph under Increase Contrast (≥4.5:1 on #222222).
  inactiveHigh: "#9A9A9A",
  release: { stiffness: 420, damping: 32, mass: 0.8 },
};
