import { Skia } from '@shopify/react-native-skia';

// The whole screen is one function of a scene time `s`, in seconds. 0 is the welcome at rest;
// holding the button runs s to HOLD; from there it runs by itself through the tunnel to AT_B, the
// first page at rest; going back runs it on to END, which is the welcome again (s jumps to 0).

/** How long the button has to be held. */
export const HOLD = 1.4;
/** A release before that springs the button back: seconds, and the spring (mass 1). */
export const RELEASE = 0.6;
export const RELEASE_STIFFNESS = 220;
export const RELEASE_DAMPING = 22;

// After the release: the texts fade, the orange covers the screen, and five shapes are born in
// the centre one after another, each growing until it covers the screen in turn.
export const TEXT_OUT = 0.2;
export const COVER = 0.4;
export const SHAPE_FIRST = 0.3;
export const SHAPE_GAP = 0.2;
export const SHAPE_GROW = 0.5;
/** Degrees a shape turns while it grows. */
export const SHAPE_TURN = 25;
/** A shape is born this many points across. */
export const SHAPE_BORN = 60;

// The first page arrives on the plain dark screen, each part a little after the one before.
export const ARRIVE = HOLD + 1.7;
export const MARK_IN = 0.3;
export const HEADING_AFTER = 0.1;
export const HEADING_IN = 0.4;
export const HEADING_SLIDE = 24;
export const DISC_AFTER = 0.2;
export const DISC_IN = 0.4;
export const BALL_AFTER = 0.3;
export const BALL_GAP = 0.1;
export const BALL_DROP = 70;
export const BALL_FADE = 0.12;
export const BALL_STIFFNESS = 180;
export const BALL_DAMPING = 16;
/** The balls' spring is taken as settled after this long. */
export const BALL_SETTLE = 0.7;
export const PAGER_AFTER = 0.6;
export const PAGER_IN = 0.4;
export const ACTION_AFTER = 0.7;
export const ACTION_GAP = 0.1;
export const ACTION_IN = 0.35;
export const ACTION_RISE = 16;
export const AT_B = ARRIVE + 1.2;

// Going back: the page fades and three shapes shrink into the centre, one behind the other.
export const PAGE_OUT = 0.15;
export const BACK_GAP = 0.15;
export const BACK_SHRINK = 0.5;
export const LABEL_BACK = 0.25;
export const END = AT_B + 2 * BACK_GAP + BACK_SHRINK;
/** Under reduced motion the two screens cross-fade instead. */
export const CALM_FADE = 0.3;

/** autoPlay: the button is pressed at PRESS_AT of every LOOP seconds, the page left at BACK_AT. */
export const LOOP = 9;
export const PRESS_AT = 1.2;
export const BACK_AT = PRESS_AT + AT_B + 2;
/** After a touch autoPlay waits this long before it starts over from the welcome. */
export const USER_HOLD = 3;

// Sizes, in points.
export const BUTTON = 58;
/** The button once the hold is complete: the white disc keeps a thin rim round it. */
export const BUTTON_FULL = 232;
/** The small button sits this far above the disc's centre, which is where it grows to. */
export const BUTTON_RISE = 10;
export const DISC = 259;
/** How much the disc swells while the button is held. */
export const DISC_SWELL = 0.04;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

export function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

/** Where a spring of mass 1 that was let go at 0 is after `t` seconds, on its way to 1. */
export function spring(t: number, stiffness: number, damping: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(stiffness);
  const z = damping / (2 * w);
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}

/** The timing curve of an early release: the spring above, over RELEASE seconds. */
export function releaseEase(x: number) {
  'worklet';
  return spring(x * RELEASE, RELEASE_STIFFNESS, RELEASE_DAMPING);
}

/**
 * How far the button has grown, 0 to 1: steady, easing out a little at the end. Below 0 while a
 * release overshoots, which makes the button dip under its size for a moment.
 */
export function held(s: number) {
  'worklet';
  const p = Math.min(1, s / HOLD);
  return p + 0.12 * Math.sin(Math.PI * p);
}

/** The orange circle's radius. `reach` is the radius that covers the screen. */
export function buttonRadius(s: number, reach: number, calm: boolean) {
  'worklet';
  if (s <= HOLD) return BUTTON / 2 + ((BUTTON_FULL - BUTTON) / 2) * held(s);
  if (s > AT_B) return BUTTON / 2;
  if (calm) return BUTTON_FULL / 2;
  const u = clamp01((s - HOLD) / COVER);
  return BUTTON_FULL / 2 + (reach - BUTTON_FULL / 2) * u * u * u;
}

/** How far above the disc's centre the orange circle's centre is. */
export function buttonRise(s: number) {
  'worklet';
  if (s <= HOLD) return BUTTON_RISE * (1 - clamp01(held(s)));
  return s > AT_B ? BUTTON_RISE : 0;
}

export function discScale(s: number) {
  'worklet';
  return s <= HOLD ? 1 + DISC_SWELL * clamp01(held(s)) : 1;
}

/** Under reduced motion: how much of the dark page shows over the welcome. */
export function dusk(s: number) {
  'worklet';
  if (s <= HOLD) return 0;
  return s <= AT_B ? clamp01((s - HOLD) / CALM_FADE) : 1 - clamp01((s - AT_B) / CALM_FADE);
}

/** The welcome's texts, which lie under the tunnel. */
export function textAlpha(s: number, calm: boolean) {
  'worklet';
  if (calm || s <= HOLD || s > AT_B) return 1;
  return 1 - clamp01((s - HOLD) / TEXT_OUT);
}

/** The bolt and the button's label, which lie over it. */
export function labelAlpha(s: number, calm: boolean) {
  'worklet';
  if (calm) return 1 - dusk(s);
  if (s <= HOLD) return 1;
  if (s <= AT_B) return 1 - clamp01((s - HOLD) / TEXT_OUT);
  return clamp01((s - (END - LABEL_BACK)) / LABEL_BACK);
}

/** The status bar's icons are light from the moment the orange has covered the screen. */
export function night(s: number, calm: boolean) {
  'worklet';
  if (calm) return dusk(s) > 0.5;
  return s > HOLD + COVER && s < AT_B + BACK_SHRINK;
}

/** Shape `index` of the tunnel (0 to 4): radius, opacity and turn in degrees. */
export function grown(s: number, index: number, reach: number) {
  'worklet';
  const u = (s - HOLD - SHAPE_FIRST - index * SHAPE_GAP) / SHAPE_GROW;
  const c = clamp01(u);
  // Translucent while small, so the shape behind tints it. Gone once the next one covers it.
  const covered = index < 4 && u >= 1 + SHAPE_GAP / SHAPE_GROW;
  const alpha = u <= 0 || s > AT_B || covered ? 0 : Math.min(1, u / 0.12) * (0.6 + 0.4 * smooth(u, 0.1, 0.5));
  return { radius: (SHAPE_BORN / 2) * Math.pow(reach / (SHAPE_BORN / 2), c), alpha, turn: SHAPE_TURN * c };
}

/** Shape `index` of the way back (0 is in front): the same, shrinking. */
export function shrunk(s: number, index: number, reach: number) {
  'worklet';
  const w = clamp01((s - AT_B - index * BACK_GAP) / BACK_SHRINK);
  const alpha = s <= AT_B ? 0 : 1 - smooth(w, 0.55, 1);
  return { radius: reach * Math.pow(SHAPE_BORN / 2 / reach, w), alpha, turn: -SHAPE_TURN * w };
}

/** The first page fades as soon as the way back starts. */
export function pageOut(s: number) {
  'worklet';
  return s > AT_B ? 1 - clamp01((s - AT_B) / PAGE_OUT) : 1;
}

/** A part of the first page that fades in over `length`, `after` seconds into the arrival. */
export function arrived(s: number, after: number, length: number, calm: boolean) {
  'worklet';
  return calm ? 1 : easeOut((s - ARRIVE - after) / length);
}

/** The first page's opacity as a whole (each part multiplies its own arrival into it). */
export function pageAlpha(s: number, calm: boolean) {
  'worklet';
  if (calm) return dusk(s);
  return s < ARRIVE ? 0 : pageOut(s);
}

/** Ball `index`: how far above its place it is, and its opacity. */
export function dropped(s: number, index: number, calm: boolean) {
  'worklet';
  if (calm) return { lift: 0, alpha: 1 };
  const t = s - ARRIVE - BALL_AFTER - index * BALL_GAP;
  const settled = t >= BALL_SETTLE ? 1 : spring(t, BALL_STIFFNESS, BALL_DAMPING);
  return { lift: BALL_DROP * (1 - settled), alpha: clamp01(t / BALL_FADE) };
}

/** How much of the pager's arc is drawn, from its left end. */
export function pagerDrawn(s: number, calm: boolean) {
  'worklet';
  return calm ? 1 : smooth(s - ARRIVE, PAGER_AFTER, PAGER_AFTER + PAGER_IN);
}

// The tunnel's shapes, as outlines round the origin with a radius of about 1. Each is a closed
// line through many points, so the three stacks draw exactly the same outline.
const STEPS = 144;

function polar(radius: (angle: number) => number) {
  const points: number[][] = [];
  for (let i = 0; i < STEPS; i++) {
    const angle = (i / STEPS) * Math.PI * 2;
    const r = radius(angle);
    points.push([r * Math.cos(angle), r * Math.sin(angle)]);
  }
  return points;
}

/** A square of half-side 1 with corners rounded by 0.3. */
function roundedSquare() {
  const corner = 0.3;
  const points: number[][] = [];
  for (let quarter = 0; quarter < 4; quarter++) {
    const base = (quarter * Math.PI) / 2;
    const cx = (1 - corner) * Math.sign(Math.cos(base + Math.PI / 4));
    const cy = (1 - corner) * Math.sign(Math.sin(base + Math.PI / 4));
    for (let i = 0; i <= 12; i++) {
      const angle = base + (i / 12) * (Math.PI / 2);
      points.push([cx + corner * Math.cos(angle), cy + corner * Math.sin(angle)]);
    }
  }
  return points;
}

function outline(points: number[][]) {
  const builder = Skia.PathBuilder.Make();
  points.forEach(([x, y], i) => (i === 0 ? builder.moveTo(x, y) : builder.lineTo(x, y)));
  return builder.close().build();
}

export const SQUARE = outline(roundedSquare());
/** A circle with twelve shallow lobes. */
export const SCALLOP = outline(polar((a) => 1 + 0.06 * Math.sin(12 * a)));
/** A superellipse: |x|⁴ + |y|⁴ = 1. */
export const SQUIRCLE = outline(polar((a) => Math.pow(Math.pow(Math.cos(a), 4) + Math.pow(Math.sin(a), 4), -0.25)));
/** A circle with a slow wave in its edge. */
export const BLOB = outline(polar((a) => 1 + 0.09 * Math.sin(3 * a + 0.5) + 0.04 * Math.sin(5 * a + 2)));
export const ROUND = Skia.Path.Circle(0, 0, 1);
/** The wavy shape is the one that reaches least far for its size: the others cover with room to spare. */
export const REACH = 1 / 0.87 + 0.03;

/** A lightning bolt in a 16 × 16 box. */
export const BOLT = Skia.Path.MakeFromSVGString('M9.4 0.8L3 9.1H7.3L6.3 15.2L13 6.7H8.6Z')!;
