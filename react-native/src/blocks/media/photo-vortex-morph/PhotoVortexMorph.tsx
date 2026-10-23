import {
  BlendMode,
  Canvas,
  ClipOp,
  FilterMode,
  matchFont,
  MipmapMode,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  Easing,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/** One photo of the set; `shape` is how the original is framed, so it is fetched wide enough to crop. */
export type VortexPhoto = { id: string; shape: 'portrait' | 'square' | 'landscape' };

type Props = {
  /** The photos, forty of them for the 5 by 8 grid; the last one tops the stack. */
  photos?: VortexPhoto[];
  /** Cycles vortex, drift, stack, strip and grid by itself, with holds, every LOOP seconds. */
  autoPlay?: boolean;
  /** The paper the photos lie on. */
  background?: string;
  /** Called with the layout's index whenever a tap moves to the next one. */
  onLayoutChange?: (layout: number) => void;
};

// Fetched at a width that leaves enough after the crop to a portrait card: a landscape original
// loses two thirds of its width, so it is fetched wider.
const FETCH_WIDTH = { portrait: 420, square: 480, landscape: 720 } as const;
const unsplash = (photo: VortexPhoto) =>
  `https://images.unsplash.com/${photo.id}?auto=format&fit=crop&w=${FETCH_WIDTH[photo.shape]}&q=80`;

const p = (id: string): VortexPhoto => ({ id: `photo-${id}`, shape: 'portrait' });
const sq = (id: string): VortexPhoto => ({ id: `photo-${id}`, shape: 'square' });
const ls = (id: string): VortexPhoto => ({ id: `photo-${id}`, shape: 'landscape' });

// Fashion, film stills, galleries and rooms, dealt so neighbours differ. The last tops the stack.
const PHOTOS: VortexPhoto[] = [
  ls('1515634928627-2a4e0dae3ddf'),
  p('1613915617430-8ab0fd7c6baf'),
  ls('1606819717115-9159c900370b'),
  ls('1618221195710-dd6b41faaea6'),
  ls('1577190651915-bf62d54d5b36'),
  p('1549298222-1c31e8915347'),
  ls('1518998053901-5348d3961a04'),
  p('1618220179428-22790b461013'),
  ls('1632187981988-40f3cbaeef5e'),
  p('1645996830739-8fe3df27c33f'),
  ls('1569783721854-33a99b4c0bae'),
  sq('1586023492125-27b2c045efd7'),
  p('1529798856831-427dfd0a1ab1'),
  p('1541130292430-a832637ddc0d'),
  ls('1554907984-15263bfd63bd'),
  ls('1664711942326-2c3351e215e6'),
  ls('1723723467478-eb4667116137'),
  sq('1553544260-f87e671974ee'),
  sq('1582555172866-f73bb12a2ab3'),
  p('1606744837616-56c9a5c6a6eb'),
  ls('1585951237318-9ea5e175b891'),
  ls('1538329972958-465d6d2144ed'),
  p('1565876427310-0695a4ff03b7'),
  sq('1616046229478-9901c5536a45'),
  ls('1486693128850-a77436e7ba3c'),
  ls('1545989253-02cc26577f88'),
  p('1583847268964-b28dc8f51f92'),
  ls('1585951237313-1979e4df7385'),
  p('1522878308970-972ec5eedc0d'),
  p('1564078516393-cf04bd966897'),
  ls('1532800783378-1bed60adaf58'),
  ls('1605429523419-d828acb941d9'),
  p('1599696848652-f0ff23bc911f'),
  ls('1496680392913-a0417ec1a0ad'),
  ls('1507643179773-3e975d7ac515'),
  ls('1606744888344-493238951221'),
  ls('1596972812018-6385b8bd3e13'),
  p('1535385793343-27dff1413c5a'),
  p('1696332415657-9fbe802a8875'),
  p('1580478491436-fd6a937acc9e'),
];

// The five layouts, in the order a tap walks through them.
const VORTEX = 0;
const DRIFT = 1;
const STACK = 2;
const STRIP = 3;
const GRID = 4;
const LAYOUTS = 5;
const NAMES = ['Vortex', 'Drift', 'Stack', 'Strip', 'Grid'];
const LINES = ['Forty frames in orbit', 'Scattered through depth', 'Gathered into one', 'Fanned along a line', 'Laid edge to edge'];

const PAPER = '#f3f2ee';
const CARD_TONE = '#dcd9d2';

// Vortex: cards on a ring RING_RADIUS·width from the middle, each turned TWIST rad past its spoke
// so they overlap like petals. It turns by itself at one full turn per LOOP; a drag turns it with
// the finger and a flick keeps it spinning, the extra speed fading by SPIN_DECAY per second.
const RING_RADIUS = 0.25;
const RING_CARD_W = 0.27;
const RING_CARD_H = 0.355;
const TWIST = -0.5;
const SPIN_DECAY = 1.4;
const MAX_SPIN = 14;
// A fast spin opens the ring by up to RING_OPEN of its radius.
const RING_OPEN = 0.16;

// Drift: the cards float in a box DRIFT_X by DRIFT_Y (shares of the screen) and DRIFT_Z deep
// (shares of the width), seen from CAMERA widths away. The box turns about the vertical axis at
// DRIFT_TURN rad/s; a drag turns it (DRIFT_DRAG rad per pt) and tips it (DRIFT_TIP rad per pt).
const DRIFT_X = 0.7;
const DRIFT_Y = 0.42;
const DRIFT_Z = 0.7;
const CAMERA = 1.6;
const DRIFT_CARD_W = 0.135;
const DRIFT_CARD_H = 0.175;
const DRIFT_TURN = 0.11;
const DRIFT_DRAG = 0.005;
const DRIFT_TIP = 0.0035;
const DRIFT_TIP_MAX = 0.45;
const DRIFT_DECAY = 1.8;
// Each card bobs BOB pt, three times per LOOP, so a recording of one loop repeats exactly.
const BOB = 5;

// Stack: one pile in the middle, the cards under the top one off by up to STACK_JITTER pt and
// STACK_TILT rad. A drag pulls the pile, which follows with resistance and springs back.
const STACK_W = 0.42;
const STACK_H = 0.56;
const STACK_JITTER = 4;
const STACK_TILT = 0.05;

// Strip: the cards side by side, each showing a sliver STRIP_STEP·width wide, one open in full.
// A drag slides which card is open, STRIP_DRAG pt per card.
const STRIP_W = 0.34;
const STRIP_H = 0.45;
const STRIP_STEP = 0.012;
const STRIP_DRAG = 9;

// Grid: GRID_COLS by GRID_ROWS tiles over the whole screen, GRID_GAP pt apart.
const GRID_COLS = 5;
const GRID_ROWS = 8;
const GRID_GAP = 2;

// Each card travels on its own spring: stiffness STIFFNESS times 0.8 to 1.2, damping ratio DAMPING.
// On the way it is spun TWIRL rad/s and tipped TIP rad/s, so the cards tumble a little.
const STIFFNESS = 150;
const DAMPING = 0.74;
const TWIRL = 2.6;
const TIP = 1.6;
const SUBSTEP = 1 / 120;
// A drag on the pile or the grid follows the finger with this resistance, never past PULL_MAX pt.
const PULL = 0.55;
const PULL_MAX = 140;
const RELEASE_SPRING = { duration: 650, dampingRatio: 0.62 } as const;
const SNAP_SPRING = { duration: 520, dampingRatio: 0.9 } as const;

// autoPlay: the vortex turns for 2.4 s, then the layouts follow at these times (s) and the loop
// starts over every LOOP s, back on a settled vortex. The strip slides open card STRIP_PEEK and back.
const LOOP = 14.4;
const SCRIPT: { at: number; to: number }[] = [
  { at: 2.4, to: DRIFT },
  { at: 5.0, to: STACK },
  { at: 7.0, to: STRIP },
  { at: 9.4, to: GRID },
  { at: 11.8, to: VORTEX },
];
const STRIP_PEEK = { at: 7.9, back: 8.6, card: 27 };
// Where the loop stands just after each layout arrives, for picking it up after a touch.
const SCRIPT_START = [0, 2.4, 5.0, 7.0, 9.4];
const USER_HOLD = 3;

// Captions: the new one rises CAPTION_RISE pt into place over CAPTION_IN s.
const CAPTION_IN = 0.45;
const CAPTION_RISE = 10;
const IMAGE_FADE = 0.35;
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

// A card's pose: centre, size, turn in the plane, tip about the vertical and horizontal axes,
// and its place in the drawing order. Positions and velocities are kept side by side.
const X = 0;
const Y = 1;
const W = 2;
const H = 3;
const ROLL = 4;
const YAW = 5;
const PITCH = 6;
const LAYER = 7;
const K = 8;

type Loaded = { image: SkImage; at: number };
/** Where each card floats in the drift, in shares of the box, and how it is tipped there. */
type Seed = { x: number; y: number; z: number; yaw: number; pitch: number; roll: number; stiff: number; spin: number; phase: number };

/** A small seeded generator, so the drift is the same every time. */
function random(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** The drift is dealt on a jittered 5 by 8 grid, so the cards spread evenly but not in rows. */
function sow(count: number): Seed[] {
  const r = random(23);
  const order = Array.from({ length: count }, (_, i) => i).sort(() => r() - 0.5);
  return Array.from({ length: count }, (_, i) => {
    const cell = order[i];
    const col = cell % GRID_COLS;
    const row = Math.floor(cell / GRID_COLS) % GRID_ROWS;
    return {
      x: ((col + 0.15 + r() * 0.7) / GRID_COLS) * 2 - 1,
      y: ((row + 0.15 + r() * 0.7) / GRID_ROWS) * 2 - 1,
      z: r() * 2 - 1,
      yaw: (r() * 2 - 1) * 0.55,
      pitch: (r() * 2 - 1) * 0.35,
      roll: (r() * 2 - 1) * 0.3,
      stiff: 0.8 + r() * 0.4,
      spin: r() < 0.5 ? -1 : 1,
      phase: r() * Math.PI * 2,
    };
  });
}

function clamp(value: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, value));
}

/** Resistance past an edge: the further it is pulled, the less it gives, never past `max`. */
function rubber(offset: number, max: number) {
  'worklet';
  const sign = offset < 0 ? -1 : 1;
  const d = Math.abs(offset) * PULL;
  return sign * max * (1 - 1 / (d / max + 1));
}

function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

/** Whether the looping clock, moving from `before` to `t` (wrapping at LOOP), passed `at`. */
function crossed(before: number, t: number, at: number) {
  'worklet';
  return (before < at && t >= at) || (t < before && (at > before || at <= t));
}

function wrapAngle(a: number) {
  'worklet';
  const t = Math.PI * 2;
  return a - t * Math.floor((a + Math.PI) / t);
}

/** Everything a layout's poses depend on besides the card. */
type Scene = {
  width: number;
  height: number;
  spin: number;
  spinSpeed: number;
  turn: number;
  tip: number;
  pullX: number;
  pullY: number;
  open: number;
  bob: number;
};

/** Writes card `i`'s pose in `layout` into `out` at `at`. */
function pose(layout: number, i: number, n: number, seed: Seed, s: Scene, out: number[], at: number) {
  'worklet';
  const cx = s.width / 2;
  const cy = s.height / 2;
  const sw = s.width;
  if (layout === VORTEX) {
    const open = 1 + RING_OPEN * Math.tanh(Math.abs(s.spinSpeed) / 6);
    const a = (i / n) * Math.PI * 2 + s.spin;
    out[at + X] = cx + Math.cos(a) * RING_RADIUS * sw * open;
    out[at + Y] = cy + Math.sin(a) * RING_RADIUS * sw * open;
    out[at + W] = RING_CARD_W * sw;
    out[at + H] = RING_CARD_H * sw;
    out[at + ROLL] = a + Math.PI / 2 + TWIST;
    out[at + YAW] = 0;
    out[at + PITCH] = 0;
    out[at + LAYER] = i;
    return;
  }
  if (layout === DRIFT) {
    // The box turned and tipped, then seen through the lens.
    const bx = seed.x * DRIFT_X * sw;
    const by = seed.y * DRIFT_Y * s.height + Math.sin(s.bob + seed.phase) * BOB;
    const bz = seed.z * DRIFT_Z * sw;
    const cT = Math.cos(s.turn);
    const sT = Math.sin(s.turn);
    const x1 = bx * cT + bz * sT;
    const z1 = -bx * sT + bz * cT;
    const cP = Math.cos(s.tip);
    const sP = Math.sin(s.tip);
    const y2 = by * cP - z1 * sP;
    const z2 = by * sP + z1 * cP;
    const d = CAMERA * sw;
    const k = d / Math.max(d + z2, d * 0.3);
    out[at + X] = cx + x1 * k;
    out[at + Y] = cy + y2 * k;
    out[at + W] = DRIFT_CARD_W * sw * k;
    out[at + H] = DRIFT_CARD_H * sw * k;
    out[at + ROLL] = seed.roll;
    out[at + YAW] = seed.yaw + s.turn * 0.8;
    out[at + PITCH] = seed.pitch - s.tip * 0.8;
    out[at + LAYER] = -z2;
    return;
  }
  if (layout === STACK) {
    const top = i === n - 1;
    // Cards lower in the pile trail the drag further behind.
    out[at + X] = cx + s.pullX + (top ? 0 : Math.sin(seed.phase) * STACK_JITTER);
    out[at + Y] = cy + s.pullY + (top ? 0 : Math.cos(seed.phase * 1.3) * STACK_JITTER);
    out[at + W] = STACK_W * sw;
    out[at + H] = STACK_H * sw;
    out[at + ROLL] = top ? 0 : seed.roll * (STACK_TILT / 0.3);
    out[at + YAW] = 0;
    out[at + PITCH] = 0;
    out[at + LAYER] = i;
    return;
  }
  if (layout === STRIP) {
    // Each card lies over the one before, showing only a sliver of it; the cards after the open
    // one move right by a card's width, so it shows in full. The last card always does.
    const step = STRIP_STEP * sw;
    const w = STRIP_W * sw;
    const gap = (w - step) * clamp(n - 1 - s.open, 0, 1);
    const total = (n - 1) * step + w + gap;
    const left = cx - total / 2 + s.pullX;
    out[at + X] = left + i * step + (w - step) * clamp(i - s.open, 0, 1) + w / 2;
    out[at + Y] = cy + s.pullY;
    out[at + W] = w;
    out[at + H] = STRIP_H * sw;
    out[at + ROLL] = 0;
    out[at + YAW] = 0;
    out[at + PITCH] = 0;
    out[at + LAYER] = i;
    return;
  }
  // Grid
  const col = i % GRID_COLS;
  const row = Math.floor(i / GRID_COLS);
  const tw = (s.width - GRID_GAP * (GRID_COLS - 1)) / GRID_COLS;
  const th = (s.height - GRID_GAP * (GRID_ROWS - 1)) / GRID_ROWS;
  out[at + X] = col * (tw + GRID_GAP) + tw / 2 + s.pullX;
  out[at + Y] = row * (th + GRID_GAP) + th / 2 + s.pullY;
  out[at + W] = tw;
  out[at + H] = th;
  out[at + ROLL] = 0;
  out[at + YAW] = 0;
  out[at + PITCH] = 0;
  out[at + LAYER] = i;
}

/** How long card `i` waits before leaving for `layout`, in seconds, so the cards go in a wave. */
function delayFor(layout: number, i: number, n: number, s: Scene, state: number[]) {
  'worklet';
  if (layout === DRIFT) return (i / n) * 0.45;
  if (layout === STACK) {
    const dx = state[i * K + X] - s.width / 2;
    const dy = state[i * K + Y] - s.height / 2;
    return Math.min(0.3, (Math.hypot(dx, dy) / s.height) * 0.5);
  }
  if (layout === STRIP) return ((n - 1 - i) / n) * 0.28;
  if (layout === GRID) return ((i % GRID_COLS) + Math.floor(i / GRID_COLS)) * 0.032;
  return (i / n) * 0.32;
}

function falses(n: number) {
  'worklet';
  const out: boolean[] = [];
  for (let i = 0; i < n; i += 1) out.push(false);
  return out;
}

/** The cards in drawing order, back to front. */
function byLayer(state: number[], n: number) {
  'worklet';
  const order: number[] = [];
  for (let i = 0; i < n; i += 1) order.push(i);
  return order.sort((p, q) => state[p * K + LAYER] - state[q * K + LAYER]);
}

/** A card's pose as a perspective matrix over its face, `w` by `h` about its middle. */
function cardMatrix(x: number, y: number, roll: number, yaw: number, pitch: number, focal: number) {
  'worklet';
  const cY = Math.cos(yaw);
  const sY = Math.sin(yaw);
  const cP = Math.cos(pitch);
  const sP = Math.sin(pitch);
  const cR = Math.cos(roll);
  const sR = Math.sin(roll);
  const ax = cY * cR + sY * sP * sR;
  const ay = cP * sR;
  const az = -sY * cR + cY * sP * sR;
  const bx = -cY * sR + sY * sP * cR;
  const by = cP * cR;
  const bz = sY * sR + cY * sP * cR;
  return [ax + (x * az) / focal, bx + (x * bz) / focal, x, ay + (y * az) / focal, by + (y * bz) / focal, y, az / focal, bz / focal, 1];
}

/** A photo cropped to fill its card, faded in once it arrives. */
function drawCard(canvas: SkCanvas, state: number[], i: number, loaded: Loaded | null, now: number, alpha: number, focal: number) {
  'worklet';
  const o = i * K;
  const w = state[o + W];
  const h = state[o + H];
  if (w < 0.5 || h < 0.5) return;
  canvas.save();
  canvas.concat(Skia.Matrix(cardMatrix(state[o + X], state[o + Y], state[o + ROLL], state[o + YAW], state[o + PITCH], focal)));
  const rect = Skia.XYWHRect(-w / 2, -h / 2, w, h);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(CARD_TONE));
  paint.setAlphaf(alpha);
  canvas.drawRect(rect, paint);
  if (loaded) {
    const image = loaded.image;
    const iw = image.width();
    const ih = image.height();
    const fit = Math.max(w / iw, h / ih);
    const sw = w / fit;
    const sh = h / fit;
    paint.setAlphaf(alpha * clamp((now - loaded.at) / IMAGE_FADE, 0, 1));
    canvas.drawImageRectOptions(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), rect, FilterMode.Linear, MipmapMode.Linear, paint);
  }
  canvas.restore();
}

/** A letter's advance; Skia measures a lone space as nothing, so it is taken from between two letters. */
function advance(font: SkFont, letter: string) {
  'worklet';
  if (letter !== ' ') return font.measureText(letter).width;
  return font.measureText('a a').width - font.measureText('aa').width;
}

function trackedWidth(font: SkFont, text: string, tracking: number) {
  'worklet';
  let width = 0;
  for (const letter of text) width += advance(font, letter) + tracking;
  return width - tracking;
}

function drawTracked(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, tracking: number, paint: SkPaint) {
  'worklet';
  let at = x;
  for (const letter of text) {
    canvas.drawText(letter, at, y, paint, font);
    at += advance(font, letter) + tracking;
  }
}

type Fonts = { label: SkFont; title: SkFont; line: SkFont };

/** The layout's number in the top corner and its name and line at the bottom, `rise` pt low. */
function drawCaption(
  canvas: SkCanvas,
  fonts: Fonts,
  ink: SkPaint,
  index: number,
  shown: number,
  rise: number,
  width: number,
  top: number,
  base: number,
) {
  'worklet';
  if (shown <= 0.01) return;
  const count = `${String(index + 1).padStart(2, '0')} / ${String(LAYOUTS).padStart(2, '0')}`;
  ink.setAlphaf(shown);
  drawTracked(canvas, count, fonts.label, width - 20 - trackedWidth(fonts.label, count, 1.2), top + rise * 0.5, 1.2, ink);
  canvas.drawText(NAMES[index], 18, base - 22 + rise, ink, fonts.title);
  ink.setAlphaf(shown * 0.62);
  canvas.drawText(LINES[index], 20, base + rise, ink, fonts.line);
}

/**
 * Forty photos on a sheet of paper that morph between five layouts, each card travelling on its
 * own spring, a little after its neighbour and tumbling on the way: a spinning vortex, a drift
 * through depth, a single stack, a strip of slivers and a grid edge to edge. A tap moves on to
 * the next layout; a drag spins the vortex, turns the drift, pulls the stack or the grid, and
 * slides the strip open card by card.
 */
export function PhotoVortexMorph({ photos = PHOTOS, autoPlay = false, background = PAPER, onLayoutChange }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const n = photos.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const fonts = useMemo<Fonts>(
    () => ({
      label: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '500' }),
      title: matchFont({ fontFamily: FAMILY, fontSize: 30, fontWeight: '500' }),
      line: matchFont({ fontFamily: FAMILY, fontSize: 13, fontWeight: '400' }),
    }),
    [],
  );
  const seeds = useMemo(() => sow(n), [n]);

  const stage = useSharedValue({ width: 0, height: 0 });
  // Seconds since the block appeared, and the poses and velocities of every card.
  const elapsed = useSharedValue(0);
  const state = useSharedValue<number[]>([]);
  // Whether each card has left for the current layout yet.
  const gone = useSharedValue<boolean[]>([]);
  const loaded = useSharedValue<(Loaded | null)[]>(photos.map(() => null));
  const layout = useSharedValue(VORTEX);
  const previous = useSharedValue(VORTEX);
  const changedAt = useSharedValue(0);
  // Scene values the layouts read.
  const spin = useSharedValue(0);
  const spinSpeed = useSharedValue(0);
  const turn = useSharedValue(0);
  const turnSpeed = useSharedValue(0);
  const tip = useSharedValue(0);
  const pullX = useSharedValue(0);
  const pullY = useSharedValue(0);
  const open = useSharedValue(n - 1);
  // Gesture bookkeeping.
  const dragging = useSharedValue(false);
  const lastAngle = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const tipFrom = useSharedValue(0);
  // The vortex is drawn twice, with the overlap seam on opposite sides, once it has settled.
  const settled = useSharedValue(true);
  // Under reduced motion the cards fade out, jump and fade in.
  const fade = useSharedValue(1);
  const jump = useSharedValue(-1);
  // The self-playing loop.
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  useEffect(() => {
    let alive = true;
    loaded.set(photos.map(() => null));
    photos.forEach((photo, i) => {
      Skia.Data.fromURI(unsplash(photo))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          loaded.modify((list) => {
            'worklet';
            const next = list.slice();
            next[i] = { image, at: elapsed.get() };
            return next;
          });
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
  }, [photos, loaded, elapsed]);

  const changed = (to: number, user: boolean) => {
    if (user && process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onLayoutChange?.(to);
  };
  const snapped = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  const scene = (): Scene => {
    'worklet';
    const { width, height } = stage.get();
    return {
      width,
      height,
      spin: spin.get(),
      spinSpeed: spinSpeed.get(),
      turn: turn.get(),
      tip: tip.get(),
      pullX: pullX.get(),
      pullY: pullY.get(),
      open: open.get(),
      bob: ((elapsed.get() * 3) / LOOP) * Math.PI * 2,
    };
  };

  /** Moves on to `to`: every card waits its turn in the wave, then leaves with a little tumble. */
  const goTo = (to: number, user: boolean) => {
    'worklet';
    if (reducedMotion) {
      fade.set(
        withSequence(
          withTiming(0, { duration: 160, easing: EASE_OUT }, () => jump.set(to)),
          withTiming(1, { duration: 220, easing: EASE_OUT }),
        ),
      );
      scheduleOnRN(changed, to, user);
      return;
    }
    previous.set(layout.get());
    layout.set(to);
    changedAt.set(elapsed.get());
    gone.set(falses(n));
    // Each layout starts from rest: the drift square on, the stack and grid unpulled, the strip on its last card.
    pullX.set(0);
    pullY.set(0);
    if (to === DRIFT) {
      turn.set(-0.2);
      turnSpeed.set(0);
      tip.set(0);
    }
    if (to === STRIP) open.set(n - 1);
    scheduleOnRN(changed, to, user);
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const { width } = stage.get();
    if (width === 0) return;
    const now = elapsed.get() + dt;
    elapsed.set(now);

    // The script.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(SCRIPT_START[layout.get()]);
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      for (const step of SCRIPT) if (crossed(before, t, step.at)) goTo(step.to, false);
      if (crossed(before, t, STRIP_PEEK.at) && layout.get() === STRIP) open.set(withSpring(STRIP_PEEK.card, SNAP_SPRING));
      if (crossed(before, t, STRIP_PEEK.back) && layout.get() === STRIP) open.set(withSpring(n - 1, SNAP_SPRING));
    }

    // The vortex turns by itself, plus whatever a flick left it; the drift turns slowly.
    if (!dragging.get()) {
      const base = reducedMotion ? 0 : (Math.PI * 2) / LOOP;
      spin.set(wrapAngle(spin.get() + (base + spinSpeed.get()) * dt));
      spinSpeed.set(spinSpeed.get() * Math.exp(-SPIN_DECAY * dt));
      if (layout.get() === DRIFT) {
        turn.set(turn.get() + ((reducedMotion ? 0 : DRIFT_TURN) + turnSpeed.get()) * dt);
        turnSpeed.set(turnSpeed.get() * Math.exp(-DRIFT_DECAY * dt));
      }
    }

    const s = scene();
    const current = layout.get();
    const from = previous.get();
    const since = now - changedAt.get();
    const target = [0, 0, 0, 0, 0, 0, 0, 0];
    let worst = 0;

    state.modify((list) => {
      'worklet';
      const fresh = list.length !== n * K * 2;
      const flags = gone.get();
      const reset = jump.get();
      for (let i = 0; i < n; i += 1) {
        const o = i * K;
        const v = n * K + o;
        const leaving = since >= delayFor(current, i, n, s, list);
        // A jump under reduced motion lands on the layout it jumps to, not the one it leaves.
        pose(reset >= 0 ? reset : leaving ? current : from, i, n, seeds[i], s, target, 0);
        if (fresh || reset >= 0) {
          // First frame, or a jump under reduced motion: straight to the pose, at rest.
          for (let c = 0; c < K; c += 1) {
            list[o + c] = target[c];
            list[v + c] = 0;
          }
          continue;
        }
        if (leaving && flags.length === n && !flags[i]) {
          // Leaving: a little spin and tip, a different way for every card.
          flags[i] = true;
          const seed = seeds[i];
          list[v + ROLL] += seed.spin * TWIRL * seed.stiff;
          list[v + YAW] += -seed.spin * TIP * (2 - seed.stiff);
        }
        // Angles take the short way round.
        list[o + ROLL] = target[ROLL] + wrapAngle(list[o + ROLL] - target[ROLL]);
        const k = STIFFNESS * seeds[i].stiff * (current === STACK ? 0.55 + (0.6 * i) / Math.max(1, n - 1) : 1);
        const c = 2 * DAMPING * Math.sqrt(k);
        let left = dt;
        while (left > 1e-6) {
          const h = Math.min(SUBSTEP, left);
          left -= h;
          for (let q = 0; q < K; q += 1) {
            const a = k * (target[q] - list[o + q]) - c * list[v + q];
            list[v + q] += a * h;
            list[o + q] += list[v + q] * h;
          }
        }
        worst = Math.max(worst, Math.abs(list[o + X] - target[X]) + Math.abs(list[o + Y] - target[Y]));
      }
      if (reset >= 0) {
        layout.set(reset);
        previous.set(reset);
        jump.set(-1);
      }
      return list;
    });
    settled.set(current === VORTEX && worst < 8);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart((event) => {
      touched();
      dragging.set(true);
      const { width, height } = stage.get();
      lastAngle.set(Math.atan2(event.y - height / 2, event.x - width / 2));
      dragFrom.set(layout.get() === STRIP ? open.get() : turn.get());
      tipFrom.set(tip.get());
      spinSpeed.set(0);
      turnSpeed.set(0);
    })
    .onUpdate((event) => {
      const mode = layout.get();
      const { width, height } = stage.get();
      if (mode === VORTEX) {
        // The ring turns with the finger about its middle.
        const a = Math.atan2(event.y - height / 2, event.x - width / 2);
        spin.set(spin.get() + wrapAngle(a - lastAngle.get()));
        lastAngle.set(a);
      } else if (mode === DRIFT) {
        turn.set(dragFrom.get() + event.translationX * DRIFT_DRAG);
        tip.set(clamp(tipFrom.get() - event.translationY * DRIFT_TIP, -DRIFT_TIP_MAX, DRIFT_TIP_MAX));
      } else if (mode === STRIP) {
        // Past either end the strip gives less and less.
        const raw = dragFrom.get() - event.translationX / STRIP_DRAG;
        const over = raw < 0 ? raw : raw > n - 1 ? raw - (n - 1) : 0;
        open.set(raw - over + rubber(over * STRIP_DRAG, 60) / STRIP_DRAG);
      } else {
        pullX.set(rubber(event.translationX, PULL_MAX));
        pullY.set(rubber(event.translationY, PULL_MAX));
      }
    })
    .onEnd((event) => {
      touched();
      dragging.set(false);
      const mode = layout.get();
      const { width, height } = stage.get();
      if (mode === VORTEX) {
        // The finger's speed about the middle carries on as spin.
        const rx = event.x - width / 2;
        const ry = event.y - height / 2;
        const r2 = Math.max(rx * rx + ry * ry, 40 * 40);
        spinSpeed.set(reducedMotion ? 0 : clamp((rx * event.velocityY - ry * event.velocityX) / r2, -MAX_SPIN, MAX_SPIN));
      } else if (mode === DRIFT) {
        turnSpeed.set(reducedMotion ? 0 : event.velocityX * DRIFT_DRAG);
        tip.set(withSpring(0, { ...RELEASE_SPRING, velocity: -event.velocityY * DRIFT_TIP }));
      } else if (mode === STRIP) {
        // Lands on the card nearest where the throw was heading.
        const v = -event.velocityX / STRIP_DRAG;
        const aim = Math.round(clamp(open.get() + v * 0.2, 0, n - 1));
        open.set(withSpring(aim, { ...SNAP_SPRING, velocity: v }));
        scheduleOnRN(snapped);
      } else {
        pullX.set(withSpring(0, { ...RELEASE_SPRING, velocity: event.velocityX * 0.3 }));
        pullY.set(withSpring(0, { ...RELEASE_SPRING, velocity: event.velocityY * 0.3 }));
      }
    })
    .onFinalize(() => {
      // A cancelled drag never reaches onEnd; without this the vortex would stop turning by itself.
      dragging.set(false);
    });

  const tap = Gesture.Tap()
    .maxDuration(350)
    .onEnd((_event, success) => {
      if (!success) return;
      touched();
      goTo((layout.get() + 1) % LAYOUTS, true);
    });

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const list = state.get();
    if (!size || list.length !== n * K * 2) return recorder.finishRecordingAsPicture();
    const now = elapsed.get();
    const images = loaded.get();
    const alpha = fade.get();
    const focal = width * 2.2;

    const ground = Skia.Paint();
    ground.setColor(Skia.Color(background));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), ground);

    if (settled.get()) {
      // A ring of overlapping cards has a seam where the last card meets the first. It is drawn
      // twice, the seam on opposite sides, and each copy shows only the half away from its seam.
      const cx = width / 2;
      const cy = height / 2;
      const a = spin.get();
      const far = Math.hypot(width, height);
      const degrees = (a * 180) / Math.PI;
      for (const side of [-1, 1]) {
        // The half-plane on one side of the line through the middle across card 0's spoke.
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(degrees, 0, 0);
        canvas.clipRect(Skia.XYWHRect(side < 0 ? -far : 0, -far, far, far * 2), ClipOp.Intersect, false);
        canvas.rotate(-degrees, 0, 0);
        canvas.translate(-cx, -cy);
        const start = side < 0 ? 0 : Math.floor(n / 2);
        for (let k = 0; k < n; k += 1) {
          const i = (start + k) % n;
          drawCard(canvas, list, i, images[i], now, alpha, focal);
        }
        canvas.restore();
      }
    } else {
      const order = byLayer(list, n);
      for (const i of order) drawCard(canvas, list, i, images[i], now, alpha, focal);
    }

    // The labels, in white set to difference, so they read dark on the paper and light on photos.
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color('#ffffff'));
    ink.setBlendMode(BlendMode.Difference);
    const top = insets.top + 22;
    drawTracked(canvas, 'KINETIC ARCHIVE', fonts.label, 20, top, 1.2, ink);
    const current = layout.get();
    const fresh = reducedMotion ? 1 : easeOut(clamp((now - changedAt.get()) / CAPTION_IN, 0, 1));
    const base = height - insets.bottom - 26;
    if (current !== previous.get() && fresh < 1) {
      drawCaption(canvas, fonts, ink, previous.get(), 1 - fresh, -fresh * CAPTION_RISE, width, top, base);
    }
    drawCaption(canvas, fonts, ink, current, fresh, (1 - fresh) * CAPTION_RISE, width, top, base);
    ink.setAlphaf(0.5);
    const hint = 'TAP TO MORPH';
    drawTracked(canvas, hint, fonts.label, width - 20 - trackedWidth(fonts.label, hint, 1.2), base, 1.2, ink);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: background }]}
        accessible
        accessibilityRole="button"
        accessibilityLabel="Photo vortex"
        accessibilityHint="Tap to morph the photos into the next layout; drag to spin, turn or slide them"
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
          stage.set({ width: w, height: h });
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
});
