// Every number the three parts of the block agree on: colors, the card's
// fixed layout and the clocks. The scene shader, the file and the card read
// their geometry from here, so a rectangle drawn by one lands under the
// content laid out by another without measuring.

export const horizonTone = {
  // Measured from the reference. The page is one flat navy; light only
  // gathers near the horizon and the lamp.
  page: "#051022",
  haze: "#15255C",
  // The horizon is a soft tube of periwinkle light, not a white wire.
  core: "#DDE4FF",
  bloom: "#8CA6ED",
  deep: "#314892",
  // The lamp over the well is a deeper, more saturated blue.
  lamp: "#273DA0",
  // The pane is a little lighter and greyer than the page; the well is a
  // hole back to the page's own navy.
  pane: "#0D1426",
  paneEdge: "#1A2537",
  paneGlow: "#364B84",
  well: "#051021",
  // Copy on the glass.
  ink: "#F4F7FF",
  inkSoft: "rgba(214, 224, 255, 0.62)",
  inkFaint: "rgba(214, 224, 255, 0.4)",
  hint: "rgba(214, 224, 255, 0.5)",
  // The bar.
  track: "#2C3848",
  fill: "#FDFCFE",
  // The close disc.
  disc: "#343B49",
  // The plain surface under Reduce Transparency.
  plain: "#0B1224",
  plainEdge: "rgba(255, 255, 255, 0.12)",
  plainWell: "#060B18",
} as const;

export const horizonSize = {
  cardMargin: 36,
  cardMax: 360,
  cardHeight: 240,
  cardRadius: 28,
  // The inset of the file, the panel and the copy from the card's edge.
  pad: 24,
  // The file's box, from the card's top left.
  fileWidth: 116,
  fileHeight: 112,
  // Where the name starts, from the card's left, and where its block starts
  // from the top: a 24 pt line, 4 pt, an 18 pt line.
  copyLeft: 152,
  copyTop: 68,
  // The close disc and its center's inset from the top right corner.
  close: 32,
  closeInset: 26,
  // The well at the bottom of the card.
  panelHeight: 58,
  panelRadius: 18,
  panelPad: 12,
  // Centers of the label row and of the bar, from the panel's top.
  rowY: 20,
  barY: 44,
  barHeight: 4,
  // The status glyph left of the label.
  statusGlyph: 18,
  // The horizon is the limb of a planet far larger than the screen: a
  // shallow curve across the whole width that never closes into a ring. Its
  // top passes behind the card this far above the card's resting bottom edge.
  horizonRadius: 560,
  horizonRise: 34,
  // How far down the free height the card rests (0 top, 1 bottom).
  cardRest: 0.42,
  // How far the card rises when the upload completes, uncovering the sun
  // that was behind its lower edge.
  lift: 46,
  // The file floats above the pane: dragged, it travels this much further.
  fileParallax: 0.07,
} as const;

export const horizonMotion = {
  // autoPlay: wait, upload, celebrate, rest, clear. The loop's length is the sum.
  lead: 700,
  transfer: 5200,
  crest: 1400,
  hold: 1800,
  reset: 600,
  // How long a chunk's arrival shows in the beam above the panel.
  pulse: 450,
  // The horizon's own slow shimmer, independent of the upload.
  shimmer: 9000,
} as const;

export const HORIZON_LOOP =
  horizonMotion.lead + horizonMotion.transfer + horizonMotion.crest + horizonMotion.hold + horizonMotion.reset;

/** The card's rectangles in its own coordinates, for a card `width` wide. */
export function horizonLayout(width: number) {
  "worklet";
  const S = horizonSize;
  const panel = { x: S.pad, y: S.cardHeight - S.pad - S.panelHeight, width: width - S.pad * 2, height: S.panelHeight };
  return {
    card: { width, height: S.cardHeight },
    file: { x: S.pad, y: S.pad, width: S.fileWidth, height: S.fileHeight },
    panel,
    bar: { x: panel.x + S.panelPad, y: panel.y + S.barY - S.barHeight / 2, width: panel.width - S.panelPad * 2, height: S.barHeight },
    close: { x: width - S.closeInset, y: S.closeInset, radius: S.close / 2 },
  };
}
