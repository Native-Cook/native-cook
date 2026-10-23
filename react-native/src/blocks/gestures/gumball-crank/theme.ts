// Every colour, size and string of the block. Sizes are points on a 402 pt wide screen, scaled by width / 402.

export const STAGE_WIDTH = 402;

export const COLORS = {
  // Measured from the source frame f003.
  wallTop: '#D9CCDD',
  wallBottom: '#D2C1D7',
  beam: 'rgba(255, 255, 255, 0.28)',
  beamFar: 'rgba(255, 255, 255, 0)',
  tableTop: '#C9AEDC',
  tableFront: '#B897CF',
  tableEdge: 'rgba(255, 255, 255, 0.45)',
  shadow: 'rgba(80, 40, 110, 0.22)',
  body: '#B57FDB',
  bodyLight: '#CFA4F0',
  bodyDeep: '#9A63C6',
  wave: 'rgba(255, 255, 255, 0.22)',
  plate: '#C799EC',
  door: '#9C64CB',
  doorDeep: '#7E49AE',
  knob: '#F6B04A',
  knobLight: '#FFD58A',
  knobDeep: '#D98A26',
  slot: '#D07F1E',
  glassRim: 'rgba(255, 255, 255, 0.85)',
  glassTint: 'rgba(255, 255, 255, 0.12)',
  glassShade: 'rgba(120, 70, 150, 0.16)',
  reflect: 'rgba(255, 255, 255, 0.55)',
  ink: '#3B2350',
  mute: 'rgba(59, 35, 80, 0.6)',
  capsuleTop: 'rgba(255, 255, 255, 0.78)',
  capsuleEdge: 'rgba(255, 255, 255, 0.95)',
  capsule: '#F0507E',
  capsuleDeep: '#C73460',
  card: '#FFFFFF',
  cardAccent: '#F0507E',
  button: '#3B2350',
  buttonInk: '#FFFFFF',
  scrim: 'rgba(214, 196, 222, 0.55)',
  bag: '#9A63C6',
  bagLight: '#CFA4F0',
};

// Gumball colours, soft and slightly dusty to sit with the lilac clay.
export const BALLS = ['#FF9E7A', '#E8547A', '#FFD66B', '#7FD8B5', '#8B6CE0', '#6FB7FF', '#F7A6C8', '#5B3B6E'];

// Machine geometry, measured from the source and placed for a 402 × 874 screen (y in points from the top).
export const MACHINE = {
  lidTop: 196,
  lidHeight: 58,
  lidWidth: 188,
  lidKnob: 14,
  globeY: 348,
  globeR: 130,
  glass: 6,
  collarY: 436,
  collarHeight: 16,
  collarWidth: 204,
  baseTop: 452,
  baseBottom: 645,
  baseTopWidth: 158,
  baseBottomWidth: 224,
  baseRadius: 26,
  plateY: 514,
  plateSize: 72,
  plateRadius: 18,
  knobR: 22,
  slotWidth: 30,
  slotHeight: 7,
  doorTop: 560,
  doorWidth: 84,
  doorHeight: 78,
  doorRadius: 28,
  tableY: 600,
  tableFrontY: 812,
  extraY: 0.5, // share of the screen's extra height (beyond 874) added above the machine
};

export const BALL = { count: 15, rMin: 18, rMax: 24, seed: 7 };

export const CAPSULE = { r: 20, rollTo: 726, rollScale: 1.7 };

export const CARD = { width: 288, height: 236, radius: 28, y: 560, pad: 24 };

export const BAG = { size: 44, right: 24 };

export const TYPE = {
  side: 24,
  top: 16,
  titleSize: 28,
  titleFamily: 'Georgia',
  subSize: 14,
  hintSize: 15,
  bottom: 28,
  amountSize: 56,
};

export const COPY = {
  cardMeta: 'Day 7 streak · added Oct 15',
  coinsWord: 'coins',
  title: 'Daily treat',
  sub: 'Day 7 of your streak',
  hint: 'Turn the knob a full circle',
  hintBusy: 'Here it comes',
  amount: '+50',
  unit: 'coins',
  prize: 'Day 7 reward',
  collect: 'Collect',
  knob: 'Turn the knob',
  knobHint: 'Dispenses today’s capsule',
  bag: 'Rewards bag',
};

// Replica step (owner's fidelity gate): the source's own scene and motion, framed like ref.py fit (source ×2.048,
// centre-cropped to 1206 × 2622 px). All positions below are target pixels / 3 = points on a 402 × 874 screen.
export const REPLICA = false;
export const REPLICA_ART = {
  wallFill: '#D6C8DA',
  globe: { x: 198, y: 341, r: 160, floor: 462 }, // measured on ref/gumball-crank-rest.png
  knob: { x: 500.5 / 3, y: 1553.5 / 3, size: 169 / 3, tilt: -0.35 }, // assets/knob.png centred at (585, 1638) px (measured on the source at rest); the source’s bar leans
  // The generated knob is more saturated than the source's pale peach (213,153,101 vs 244,157,85).
  knobMatrix: [0.87, 0, 0, 0, 0, 0, 0.97, 0, 0, 0, 0, 0, 1.19, 0, 0, 0, 0, 0, 1, 0],
  // The door: a light lavender recessed panel (source 191–197, 122–130, 223 RGB) with a soft inner shadow.
  door: { x: 443 / 3, y: 1786 / 3, w: 295 / 3, h: 290 / 3, r: 16, top: '#C27CE0', bottom: '#C583E1', shade: 'rgba(110, 50, 150, 0.35)' },
  fruitBlur: 1.2,
  pushGrade: [0.97, 0, 0, 0, 0, 0, 0.93, 0, 0, 0, 0, 0, 0.98, 0, 0, 0, 0, 0, 1, 0], // the source's push-in frames are a touch more magenta
  defocus: 1.2, // pt: the room and machine go soft behind the pot during the push-in (as in the source)
  knobBlur: 1.6, // the source's knob is soft (depth of field)
  fillBlur: 0, // the source's fruit sits behind glass with a shallow depth of field
  fruitPack: 0.72, // fruit overlaps in depth, packed like the source's globe
  haze: 'rgba(255, 255, 255, 0.10)',
  // The source's glass: a curved white glare across the upper left and a soft rim highlight.
  glare: { r: 138, from: 196, sweep: 62, width: 14, blur: 6, color: 'rgba(255, 255, 255, 0)' }, // the generated glass carries the glare
  rim: { width: 3, color: 'rgba(255, 255, 255, 0)' }, // the generated glass carries the rim
  jostle: { amp: 5, freq: 9, decay: 3.2 }, // the packed fruit nudges when the knob turns (pt, Hz, 1/s)
  // assets/pot.png: 1206 × 1000 px, traced from the source (Codex); placed exactly as in ref b (canvas at 0,760 px).
  pot: { cx: 201, cy: 420, w: 402, h: 1000 / 3 },
  // ref a: the source shows the tub 2 % larger and tilted 5.9° as it comes forward.
  potA: { cx: 201, cy: 450, scale: 1, rot: 0 }, // assets/pot-a.png (traced from ref a) at canvas (0, 850) px
  exit: { x: 199, y: 644 }, // the pot comes out of the door
  // Fruit as in the source: [sprite, radius pt, sprite size pt]; peach.png 253 px, peach-b.png 337 × 247, passion 161.
  // ~25 pieces packed to the brim (peaches, nectarines, dark plums): [sprite, radius pt, sprite size pt].
  fruit: [
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach-b', 40, 112],
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach-b', 40, 112],
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach-b', 40, 112],
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach-b', 40, 112],
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach', 38, 86],
    ['passion', 24, 54],
    ['peach-b', 40, 112],
    ['peach', 38, 86],
    ['passion', 24, 54],
  ] as const,
};

// Upgrade tokens (on top of the accepted replica). Positions in points on a 402 pt wide screen.
/**
 * Spacing scale (step 0, 20 Oct): 4 pt base, from the source's card padding (24) and bag inset (24). The upgrade's
 * additions use it: the coin pill (12 from the top, 16 from the left, 12 padding, 8 after the coin, 4 before the word),
 * the Collect button (24 padding, 8 after its coin) 16 under the coupon.
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const UP = {
  // The tub's navy label panel at its replica position (pot.png px 471–979 × 284–815 at ×0.871 / 3).
  panel: { x: 119.3, y: 315.7, w: 188, h: 218, r: 14 }, // the traced pot.png's label panel at its b pose
  // The coupon it peels into (assets/coupon.png, 1050 × 600 px; the stub is the right third).
  card: { x: 41, y: 452, w: 320, h: 183, r: 10 },
  couponText: { x: 0.36, y: 0.2, w: 0.3 }, // where '+50' sits on the coupon (fractions of its size)
  stub: { x: 0.83, y: 0.5 }, // the coins leave from the stub
  blur: 14, // the stage behind the coupon: a pre-blurred copy of the scene, crossfaded
  tubBack: 0.9,
  peel: -0.14, // rad: the panel lifts off at an angle and flattens
  navy: '#293267',
  scrim: 0.35,
  scrimColor: '#2A1840',
  shadow: 'rgba(70, 30, 100, 0.55)',
  shadowY: 640,
  sweep: 'rgba(255, 255, 255, 0.45)',
  sweepW: 34,
  ring: 34,
  ringColor: 'rgba(255, 240, 220, 0.9)',
  confettiKinds: 3,
  counter: { top: SPACE.md, left: SPACE.lg },
};
