// Aura Wallet Widget — a square wallet widget: a cushion of violet light
// holding the balance, five friends sitting on its lower lip, the last
// transaction on a pane of glass and four pillow buttons. After a still by
// Memento Studios (@memento___studios). The names, the faces, the brand in
// the transaction and all the motion are ours.
//
// Pure data and math so node tests can import it without React Native.

/** Everything a colorway decides. A second colorway is a second object. */
export type AuraSkin = {
  /** Status bar content over the page. */
  bar: "light" | "dark";
  /** The page with no light on it. */
  page: string;
  /** The three lamps behind the widget: left, lower right, upper left. */
  lampLeft: [core: string, edge: string];
  lampLow: [core: string, edge: string];
  lampHigh: string;
  /** The smoked glass of the widget's body, and how much page light it lets through (0–1). */
  shell: string;
  shellClear: number;
  /** The cushion of light: its highlight at the top centre, its middle, its left and right edges. */
  heroCore: string;
  heroMid: string;
  heroLeft: string;
  heroEdge: string;
  /** A neutral pillow button, top to bottom, and the peak of its highlight. */
  pillowTop: string;
  pillowMid: string;
  pillowLow: string;
  pillowShine: string;
  /** The accent pillow (Send), upper left to lower right, and its highlight. */
  accentTop: string;
  accentMid: string;
  accentLow: string;
  accentShine: string;
  /** Type and marks. */
  ink: string;
  sub: string;
  onAccent: string;
  /** The chip over the balance and the pane of the last transaction. */
  chipFill: string;
  chipLine: string;
  paneFill: string;
  paneLine: string;
  /** The info dot: a darker dimple in the cushion, and its mark. */
  dotFill: string;
  dotInk: string;
  /** The pearl behind the invented transaction mark, upper left to lower right. */
  pearlHigh: string;
  pearlLow: string;
  /** The quiet line under the widget. */
  hint: string;
};

// Sampled from the violet slide of the reference (04/07).
const violet: AuraSkin = {
  bar: "light",
  page: "#040116",
  lampLeft: ["#D9A8DC", "#A25CD8"],
  lampLow: ["#5A12B8", "#2D065F"],
  lampHigh: "#1B075C",
  shell: "#17142A",
  shellClear: 0.3,
  heroCore: "#B98FF5",
  heroMid: "#8A4FD8",
  heroLeft: "#6928CA",
  heroEdge: "#5411C1",
  pillowTop: "#3E3C4A",
  pillowMid: "#2B273E",
  pillowLow: "#211D30",
  pillowShine: "#7B7986",
  accentTop: "#8655CB",
  accentMid: "#6330B9",
  accentLow: "#5829A9",
  accentShine: "#EBD9FF",
  ink: "#FFFFFF",
  sub: "rgba(255,255,255,0.62)",
  onAccent: "#FFFFFF",
  chipFill: "rgba(255,255,255,0.16)",
  chipLine: "rgba(255,255,255,0.18)",
  paneFill: "rgba(255,255,255,0.045)",
  paneLine: "rgba(255,255,255,0.16)",
  dotFill: "rgba(38,6,112,0.42)",
  dotInk: "rgba(255,255,255,0.72)",
  pearlHigh: "#FFFFFF",
  pearlLow: "#CFC1E4",
  hint: "rgba(255,255,255,0.6)",
};

export const auraSkins = { violet };
export type AuraSkinName = keyof typeof auraSkins;

/** The widget is drawn on a 362 pt square; every size below is in those points. */
export const AURA_DESIGN = 362;
/** The widget is never wider than this, and keeps this margin from the screen's edges. */
export const AURA_MAX = 362;
export const AURA_MARGIN = 20;
/** How far the shell's canvas reaches past the widget on every side, for its shadow and glow. */
export const AURA_BLEED = 64;

export type AuraRect = { x: number; y: number; w: number; h: number };

/** The four pillow buttons, left to right. */
export const AURA_ACTIONS = ["settings", "swap", "receive", "send"] as const;
export type AuraAction = (typeof AURA_ACTIONS)[number];

// The reference measured (its widget is 589 px wide; 1 px = 0.6146 pt here):
// one 12 pt inset for the three bands, the bands 17 pt apart, radii concentric
// with the 44 pt shell.
const D = {
  // Line boxes leave room for Vietnamese accents without moving optical centres.
  lines: {
    owner: 16,
    chip: 14,
    balance: 42,
    paneLabel: 14,
    title: 18,
    note: 14,
    amount: 20,
    action: 20,
  },
  // Page chrome and minimum touch geometry stay in unscaled screen points.
  page: {
    lift: 48,
    hintGap: 24,
    hintSize: 13,
    hintLine: 18,
  },
  touch: { minimum: 44, retention: 20 },
  // The chip's inset outline leaves its padding intact. Only labelled actions
  // need the optical correction; the two quiet labels keep their own opacity.
  details: {
    chipPad: 10,
    chipStroke: 1,
    actionOpticalX: 1,
    ownerOpacity: 0.85,
    viewAllOpacity: 0.9,
  },
  radius: 44,
  inset: 12,
  // The reference's grotesque sets tighter than the system face: small type
  // and labels are pulled in by this much.
  tight: -0.25,
  // A face is shown closer than its square: the head fills the disc.
  faceZoom: 1.24,
  faceLift: 0.04,
  hero: { x: 12, y: 12, w: 338, h: 156, radiusTop: 32, radiusBottom: 40 },
  // The friends sit on the cushion's lower lip, 4 pt above its edge: 22 pt of
  // each is inside the cushion and 14 pt hangs below it. With its ring a
  // friend is 42 pt wide, which leaves 8 pt of cushion between two of them.
  lip: { cy: 164, avatar: 36, ring: 3, pitch: 50, count: 5 },
  owner: { cx: 40, cy: 37, avatar: 22, nameX: 56, nameSize: 12 },
  info: { cx: 326, cy: 37, size: 22 },
  chip: { cx: 181, cy: 58, w: 90, h: 22, textSize: 10, tracking: 0.2 },
  balance: { cy: 98, size: 34, tracking: -0.6 },
  pane: { x: 12, y: 201, w: 338, h: 80, radius: 24 },
  paneLabel: { x: 26, cy: 219, size: 11, right: 336 },
  paneMark: { cx: 46, cy: 252, size: 36 },
  paneTitle: { x: 72, cy: 245, size: 14 },
  paneNote: { x: 72, cy: 262, size: 11 },
  paneAmount: { right: 334, cy: 259, size: 16 },
  actions: { y: 298, h: 52, gap: 6, widths: [52, 52, 120, 96], labelSize: 15, icon: 18, stroke: 1.5,
    // The arrow after a label is as tall as the label's capitals, not as an icon.
    arrow: 14, arrowGap: 5 },
};

export const auraDesign = D;

/** Every frame of the widget in points, for a widget `width` points wide. */
export function auraFrames(width: number) {
  const u = width / AURA_DESIGN;
  const rect = (r: AuraRect): AuraRect => ({ x: r.x * u, y: r.y * u, w: r.w * u, h: r.h * u });
  const mid = D.lip.count >> 1;
  const lipX = Array.from({ length: D.lip.count }, (_, i) => (AURA_DESIGN / 2 + (i - mid) * D.lip.pitch) * u);
  let x = D.inset;
  const actions = D.actions.widths.map((w) => {
    const r = rect({ x, y: D.actions.y, w, h: D.actions.h });
    x += w + D.actions.gap;
    return r;
  });
  return {
    u,
    size: width,
    radius: D.radius * u,
    hero: { ...rect(D.hero), radiusTop: D.hero.radiusTop * u, radiusBottom: D.hero.radiusBottom * u },
    lip: { cy: D.lip.cy * u, avatar: D.lip.avatar * u, ring: D.lip.ring * u, xs: lipX },
    pane: { ...rect(D.pane), radius: D.pane.radius * u },
    actions,
  };
}

export type AuraFrames = ReturnType<typeof auraFrames>;

/** The widget's width on a screen `screen` points wide; even, so its centre is on a whole point. */
export function auraWidth(screen: number) {
  return Math.floor(Math.min(screen - AURA_MARGIN * 2, AURA_MAX) / 2) * 2;
}

export const auraMotion = {
  // A pillow gives under the finger at once and comes back with a little life.
  pressIn: 90,
  pressScale: 0.96,
  release: { duration: 300, dampingRatio: 0.7 },
  // Strong ease-out for anything that answers a touch.
  easeOut: [0.23, 1, 0.32, 1] as const,
};
