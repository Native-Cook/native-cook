// Woven Speaker Player — every colour, size and copy token. Measured on the shot ("Inside the Speaker — Music UI Concept", a 916 × 1145
// Dribbble still, Jhan): `.work/woven-speaker-player/pin-measure.md`. The shot is copied at 0.527 pt per px (the dome is 631 px = 330 pt
// across) on the 402 × 874 pt canvas; the dome sits at (201, 300) as the concept places it. Everything here is design pt on that canvas,
// scaled at run time by S = min(W / 402, 1) (the dome stays 330 pt on a 440 pt phone, 308 pt on a 375 pt one).
// Only this file holds literal colours and sizes; the component, the shader and the motion read them from here.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

export const ref = {
  color: {
    // the page (measured #131313 at three spots; the shot is not pure black, so neither is this)
    page: '#131313',
    pageDeep: '#0E0E0E', // the bottom of the page under the dome's lamp wash
    // the display's glass (disp UL #0F0F1C, disp L #0E0D19, disp bottom #0E0B17)
    glassTop: '#101020',
    glassBottom: '#0C0916',
    bezelDark: '#050506',
    bezelHi: '#9A9CA6',
    bezelLo: '#1B1C1F',
    // the lamp: the display's red and its orange tail (arc #FF3B30 → #FF7A45)
    accent: '#FF3B30',
    accentTail: '#FF7A45',
    bloom: '#B3181F',
    // the cover (cover red2 #A1272C, cover UL #A1262C, cover dark #711C20)
    coverTop: '#C6303A',
    coverBottom: '#5C0E14',
    // the cover poster: a low sun, its glow, the silhouette and the rim of light on its sun side
    sun: '#FF7452',
    sunGlow: '#FF8A5C',
    figure: '#24060A',
    figureLo: '#120204',
    rim: '#FFB088',
    amber: '#FFB020',
    // type
    ink: '#FFFFFF', // "Press play." (measured #FFFFFF)
    mute: '#8E8E93', // "Introducing …" (#8E8E93)
    pill: '#FFFFFF',
    displayInk: '#EDEAF3',
    displayMute: '#A9A5B5',
    time: '#CFCDD8',
    glyph: '#E6E3F2',
    glyphLo: '#B3AFC6',
    discFill: '#8C78C8',
    avatar: '#F2B5AE',
    avatarInk: '#6A2B30',
  },
  // the dome and its display (rows dome, bezel, glass, arc)
  dome: { cx: 201, cy: 300, r: 165 },
  display: { r: 102.8, bezelOuter: 105.4, bezelRing: 104.0, bezelW: 1.3, arcR: 96.5, arcW: 1.7 },
  // inside the display, from its centre (design pt): the rows of the shot, scaled 0.527
  // Sized to read at arm's length (the shot's 6–8 pt labels were illegible on a phone): time 12, title 15, artist 8.5 tracked caps.
  time: { y: -80, size: 12, big: 17 },
  row: { y: -57, dx: 30, cast: 13, avatar: 16, avatarInk: 9, wave: 14 },
  cover: { size: 72, r: 20 },
  disc: { dx: 67, size: 26, glyph: 9 },
  track: { y: 54, size: 15, artistY: 73, artistSize: 8.5, artistTrack: 0.12 },
  // the text block under the dome (the concept's layout: 34 pt headline, 17 pt line, 117 × 38 pill)
  title: { y: 590, size: 38 },
  line: { y: 632, size: 17 },
  pill: { y: 672, w: 124, h: 40, stroke: 1, size: 15 },
  // the lamp's wash on the page while playing (Pro 2): radius 300 pt, 5 % at rest of play, 8 % on accented beats
  wash: { r: 300, base: 0.05, beat: 0.08, title: 0.1 },
  // bloom behind the cover: two soft lobes (the shot draws a heart of two circles)
  bloom: { r0: 60, r1: 84, lobeDx: 24, lobeDy: -6 },
} as const;

export const sys = {
  surface: { page: ref.color.page },
  ink: { primary: ref.color.ink, secondary: ref.color.mute },
  accent: { base: ref.color.accent, tail: ref.color.accentTail },
} as const;

export const COPY = {
  headline: 'Press play.',
  subtitle: 'Introducing Aria OS',
  pill: 'Learn more',
  subtitlePlaying: 'Aria OS · Living room',
  pillPlaying: 'Open queue',
  label: 'Woven speaker',
  position: 'Track position',
  play: 'Play',
  pause: 'Pause',
  previous: 'Previous track',
  next: 'Next track',
  hintDome: 'Turn the dome to scrub the track. Swipe up or down to seek five seconds.',
  hintTitle: 'Swipe left or right to change track',
} as const;

export type WovenTrack = {
  title: string;
  subtitle?: string;
  /** a procedural cover variant (0, 1, 2) when no image is given */
  cover?: number;
  accent?: string;
};

/** The demo queue: invented tracks, no real artist, label or song. */
export const DEMO_QUEUE: WovenTrack[] = [
  { title: 'Lowlight', subtitle: 'NORTH OF EIGHT', cover: 0 },
  { title: 'Paper Moons', subtitle: 'JUNO VALE', cover: 1 },
  { title: 'Static Bloom', subtitle: 'HALVES', cover: 2 },
];

/** What a host can change (the `tokens` prop); every field falls back to the shot's value. */
export type WovenTokens = {
  /** the page colour (the shot: #131313) */
  page?: string;
  /** the headline's colour (#FFFFFF) */
  ink?: string;
  /** the line under it and the display's secondary text (#8E8E93) */
  mute?: string;
};

export type ResolvedTokens = { page: string; ink: string; mute: string };

export function resolveTokens(t?: WovenTokens): ResolvedTokens {
  const ok = (v: string | undefined, fallback: string) => (v && /^#[0-9a-fA-F]{6}$/.test(v) ? v : fallback);
  return { page: ok(t?.page, ref.color.page), ink: ok(t?.ink, ref.color.ink), mute: ok(t?.mute, ref.color.mute) };
}

export function hexToRgb(hex: string): number[] {
  const h = /^#[0-9a-fA-F]{6}$/.test(hex) ? hex : '#FF3B30';
  return [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];
}

export type WspLayout = {
  W: number;
  H: number;
  /** design pt → screen pt */
  S: number;
  /** the dome's centre, screen pt */
  cx: number;
  cy: number;
  domeY: number;
  /** a design y (on the 402 × 874 canvas) → screen y for the text block */
  y: (designY: number) => number;
  /** a design x offset from the centre line → screen x */
  x: (dx: number) => number;
};

// The page is a player screen: the text block (headline, line, pill) stands on the bottom safe area, 40 pt up, and the dome is
// centred in the space above it, between the top safe area and the headline. Everything scales by S = min(W / 402, 1) · size / 330.
const BOTTOM_GAP = 40;
export const DEFAULT_SIZE = 360;

export function makeLayout(
  W: number,
  H: number,
  size = DEFAULT_SIZE,
  insetTop: number = REF.safeTop,
  insetBottom: number = REF.safeBottom,
) {
  const S = Math.min(W / REF.w, 1) * (size / 330);
  const blockBottom = ref.pill.y + ref.pill.h;
  const pillBottom = H - Math.max(insetBottom, 16) - BOTTOM_GAP * S;
  const y = (dy: number) => pillBottom + (dy - blockBottom) * S;
  const titleTop = y(ref.title.y - ref.title.size * 0.5);
  const cy = (Math.max(insetTop, 20) + titleTop) / 2;
  /** the dome's centre in design pt, for the canvas group that draws in design pt */
  const domeY = (cy - y(0)) / S;
  return { W, H, S, cx: W / 2, cy, domeY, y, x: (dx: number) => W / 2 + dx * S };
}
