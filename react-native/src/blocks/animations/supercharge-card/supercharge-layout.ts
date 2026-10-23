// Supercharge Card — where everything sits.
//
// One design space of 402 × 874 pt (an iPhone 17 Pro). The whole design is scaled by
// k = min(1, width / 402, height / 874) and centred on the screen, so nothing is laid out twice.
// The measurements come from the reference still, scaled so the glass is 272 pt wide.

export const DESIGN = { width: 402, height: 874 } as const;

/** The glass card. Its lower half is hidden inside the paper sleeve. */
export const GLASS = { x: 65, y: 288, width: 272, height: 334, radius: 18 } as const;

/**
 * The paper sleeve, in front of the glass. Its outline, clockwise from the top-left corner:
 * top edge at `top` from the left to `notch.from`, an S-curve `notch.ease` wide down to
 * `notch.floor`, flat until `notch.to - notch.easeOut`, an S-curve back up that ends at `notch.to`,
 * a short tab to the top-right corner,
 * the right edge down to `step.from`, an S-curve in to `step.x` by `step.to`, down to the
 * bottom-right corner, the bottom edge, and up the left edge.
 */
export const PAPER = {
  left: 65,
  top: 448,
  right: 341,
  bottom: 628,
  radius: { topLeft: 10, topRight: 8, bottomRight: 10, bottomLeft: 14 },
  notch: { from: 198, to: 328, floor: 466, ease: 22, easeOut: 16 },
  step: { from: 556, to: 570, x: 328 },
} as const;

/** The sleeve line: above it the glass shows, below it the paper covers it. */
export const SLEEVE = { y: PAPER.top, notchY: PAPER.notch.floor } as const;

/** The ring light. It sits on the notch's edge, half over the glass and half over the paper. */
export const RING = { x: 268, y: 466, radius: 30, stroke: 4.5 } as const;

/** The bolt glyph in front of the glass: six corners, clockwise from the top tip, each rounded by `BOLT_ROUND`. */
export const BOLT = [
  [240, 220], // A  top tip: where the sky channel lands
  [222, 273], // B  inner right
  [281, 271], // C  right spike
  [156, 421], // D  bottom tip: where the discharge enters the glass
  [193, 320], // E  inner left
  [149, 327], // F  left spike
] as const;
export const BOLT_ROUND = 3;
export const BOLT_TOP = { x: 240, y: 220 } as const;
export const BOLT_ROOT = { x: 156, y: 421 } as const;
export const BOLT_CENTRE = { x: 214, y: 312 } as const;

/** The card turns, shakes and squeezes about this point (the middle of the glass-and-sleeve). */
export const PIVOT = { x: 201, y: 458 } as const;

/** Type and print on the paper. Baselines, not tops. */
export const PRINT = {
  inset: 85,
  readout: { x: 85, baseline: 492, size: 10, tracking: 1.2 },
  // Twelve dots after the readout; they light one by one as the charge rises.
  dots: { x: 150, y: 488.5, gap: 6, count: 12, radius: 1 },
  title: { x: 85, baseline: 544, size: 28, tracking: -0.6 },
  body: { x: 85, baseline: 566, size: 13, line: 18 },
  barcode: { x: 85, y: 602, width: 224, height: 10 },
  micro: { y: 615, height: 2.5 },
} as const;

/** The line under the card. */
export const CAPTION = { x: 201, baseline: 712, size: 11, tracking: 2.2 } as const;

/**
 * Data strips (greyscale tiles side by side, see the block's notes).
 * A tile covers `rect` in design points at `scale` pixels per point.
 */
export const PAPER_STRIP = {
  rect: { x: 59, y: 442, width: 288, height: 192 },
  scale: 3,
  tile: [864, 576],
  tiles: 3, // coverage · height · ink
} as const;
export const TREE_STRIP = {
  rect: { x: 65, y: 288, width: 272, height: 334 },
  scale: 2,
  tile: [544, 668],
  tiles: 3, // core · halo · arrival
} as const;

/** How the design is placed on a screen of `width` × `height`. */
export function designFrame(width: number, height: number) {
  const k = Math.min(1, width / DESIGN.width, height / DESIGN.height);
  return { k, x: (width - DESIGN.width * k) / 2, y: (height - DESIGN.height * k) / 2 };
}

/**
 * The transform of everything that rides on the card, for a Skia `<Group transform>`.
 * `card` is [dx, dy, rotation in radians, scale]. The scene shader applies the inverse.
 */
export function cardTransform(card: readonly number[]) {
  'worklet';
  return [
    { translateX: PIVOT.x + card[0] },
    { translateY: PIVOT.y + card[1] },
    { rotate: card[2] },
    { scale: card[3] },
    { translateX: -PIVOT.x },
    { translateY: -PIVOT.y },
  ];
}
