import {
  Canvas,
  ClipOp,
  FilterMode,
  matchFont,
  MipmapMode,
  PaintStyle,
  Picture,
  Skia,
  TileMode,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDecay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/** One plate of the herbarium: an Unsplash photo id, its name and a line under it. */
export type Plate = { id: string; name: string; note: string };

type Props = {
  plates?: Plate[];
  /** Pinches out to 2 and 1 columns, back in to 5 and out to 3 again, every LOOP seconds. */
  autoPlay?: boolean;
  /** The paper and the ink. */
  background?: string;
  ink?: string;
  /** Called with the column count each time the grid settles on a new one. */
  onColumnsChange?: (columns: number) => void;
};

const unsplash = (id: string, width: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;
// Every plate is fetched small for the dense grids (a 5 column tile is ~80 pt, a 3 column one
// ~125 pt); the ones on screen at 2 or 1 columns are fetched again at SHARP_WIDTH px.
const THUMB_WIDTH = 420;
const SHARP_WIDTH = 1080;
// Sharp copies are kept for the plates within SHARP_KEEP of the screen and dropped beyond.
const SHARP_AHEAD = 2;
const SHARP_KEEP = 6;

const plate = (id: string, name: string, note: string): Plate => ({ id: `photo-${id}`, name, note });

// Leaves, bouquets and tulips, dealt so neighbours differ.
const PLATES: Plate[] = [
  plate('1503149779833-1de50ebe5f8a', 'Shade Leaf', 'Monstera deliciosa'),
  plate('1582794543139-8ac9cb0f7b11', 'Blush', 'Paeonia lactiflora'),
  plate('1554494583-c4e1649bfe71', 'Open Sky', 'Tulipa, red'),
  plate('1491147334573-44cbb4602074', 'Pinstripe', 'Calathea orbifolia'),
  plate('1572454591674-2739f30d8c40', 'Apricot', 'Garden roses'),
  plate('1586968295564-92fd7572718b', 'On Cedar', 'Tulipa, pink'),
  plate('1446071103084-c257b5f70672', 'Rosette', 'Echeveria'),
  plate('1561181286-d3fee7d55364', 'Glass Vase', 'Tulipa, mixed'),
  plate('1587316830148-c9b01df2da38', 'Peach Light', 'Tulipa, apricot'),
  plate('1598764557991-b9f211b73b81', 'Split Leaf', 'Monstera deliciosa'),
  plate('1457089328109-e5d9bd499191', 'Ember Posy', 'Ranunculus, anemone'),
  plate('1488928741225-2aaf732c96cc', 'Field Rows', 'Tulipa, mixed'),
  plate('1477554193778-9562c28588c0', 'Rubber Fig', 'Ficus elastica'),
  plate('1567696153798-9111f9cd3d0d', 'Kraft Wrap', 'Sunflower, eucalyptus'),
  plate('1614791199038-6869a104fe5f', 'Single Stem', 'Tulipa'),
  plate('1543459176-4426b37223ba', 'Understory', 'Monstera and fern'),
  plate('1533616688419-b7a585564566', 'Marmalade', 'Roses and stock'),
  plate('1525898939552-346875c89d92', 'Soft Focus', 'Tulipa, spring'),
  plate('1525498128493-380d1990a112', 'White Room', 'Monstera, potted'),
  plate('1589095181425-c038b3871b6a', 'Handed Over', 'Spray roses'),
  plate('1520763185298-1b434c919102', 'Pink Cup', 'Tulipa, pink'),
  plate('1530049478161-0780526964f4', 'Night Frond', 'Monstera deliciosa'),
  plate('1487530811176-3780de880c2d', 'Dusk Bouquet', 'Roses, wax flower'),
  plate('1559150180-2467f804f9bf', 'Open Field', 'Tulipa, mixed'),
  plate('1493552152660-f915ab47ae9d', 'Window Fig', 'Ficus elastica'),
  plate('1685613858397-64f79a0f3603', 'Market Stall', 'Carnations, daisies'),
  plate('1518701005037-d53b1f67bb1c', 'Mint', 'Tulipa, white'),
  plate('1525923838299-2312b60f6d69', 'Palm Fan', 'Chamaedorea'),
  plate('1531120364508-a6b656c3e78d', 'Meadow', 'Sunflower, cornflower'),
  plate('1561181226-e8a7edd504c6', 'Water Glass', 'Tulipa, pink'),
  plate('1605449670493-ca1d812d0488', 'First Leaf', 'Monstera, young'),
  plate('1615385639736-362b69696227', 'Paper Cone', 'Tulipa, red'),
  plate('1468327768560-75b778cbb551', 'Flame', 'Tulipa, flamed'),
  plate('1604866830513-d54766457f45', 'Olive Pot', 'Monstera, potted'),
  plate('1583228858294-6745cb25969e', 'Pale Gift', 'Roses, gypsophila'),
  plate('1586554978186-deffc54a0a5c', 'Shadow', 'Tulipa, pink'),
];

const PAPER = '#eeece4';
const INK = '#1e2a20';
const TILE_TONE = '#dcdccf';

// The column ladder a pinch walks along, densest first. Each rung sets the margin and gap (pt),
// the corner radius (pt), the tile's height over its width, and the room left under it for the label.
type Rung = { cols: number; margin: number; gap: number; radius: number; aspect: number; label: number };
const LADDER: Rung[] = [
  { cols: 5, margin: 2, gap: 2, radius: 3, aspect: 1.25, label: 0 },
  { cols: 3, margin: 10, gap: 6, radius: 8, aspect: 1.25, label: 0 },
  { cols: 2, margin: 16, gap: 12, radius: 12, aspect: 1.25, label: 44 },
  { cols: 1, margin: 24, gap: 26, radius: 18, aspect: 1.25, label: 56 },
];
const LAST = LADDER.length - 1;
// The rung the grid opens on, and the two a double tap toggles between.
const START_RUNG = 1;
const ONE_COLUMN = 3;
const THREE_COLUMNS = 1;

// A pinch moves along the ladder in log columns: spreading the fingers by a factor s divides the
// column count by s^PINCH_GAIN, so 5 → 1 takes a spread of about 3.4×.
const PINCH_GAIN = 1.3;
// Past either end the pinch keeps following with resistance, never further than OVERPINCH rungs,
// which grow or shrink the whole grid about the fingers by OVERPINCH_SCALE per rung.
const OVERPINCH = 0.3;
const OVERPINCH_SCALE = 0.32;
// The tiles follow the fingers sideways during a pinch: a share of the anchor tile's drift, plus
// the fingers' own movement, with resistance, never past SIDEWAYS_MAX pt.
const SIDEWAYS_FOLLOW = 0.5;
const SIDEWAYS_MAX = 60;
// Release: the pinch is thrown PROJECT s ahead at its speed before the nearest rung is chosen.
const PROJECT = 0.14;
// A throw moves the landing rung by at most this much, so it lands on a neighbour, never further.
const PROJECT_MAX = 0.6;
const SETTLE_SPRING = { duration: 520, dampingRatio: 0.8 } as const;
const SCROLL_SPRING = { duration: 520, dampingRatio: 1 } as const;
// Scrolling past either end gives less the further it is pulled, never more than RUBBER_MAX pt.
const RUBBER_MAX = 90;

// Labels fade in between these rungs (2 columns reads them in full) and grow by LABEL_GROW at 1.
const LABEL_FROM = 1.35;
const LABEL_TO = 1.95;
const LABEL_GROW = 0.25;
const IMAGE_FADE = 0.35;

// autoPlay: a scripted pinch walks the ladder, each move taking `span` s on an ease-in-out, from
// the 3 column grid back to it, every LOOP s. Two touch marks show the fingers while it moves.
const LOOP = 12;
const SCRIPT: { at: number; span: number; to: number }[] = [
  { at: 1.4, span: 1.2, to: 2 },
  { at: 3.8, span: 1.2, to: 3 },
  { at: 6.6, span: 1.7, to: 0 },
  { at: 9.8, span: 1.2, to: 1 },
];
// Where the loop stands at rest on each rung, for picking it up after a touch.
const SCRIPT_REST = [8.3, 0, 2.6, 5.0];
const USER_HOLD = 3;
// The anchored plate the script pinches over (its middle stays under the fingers).
const SCRIPT_ANCHOR = 7;
const TOUCH_RADIUS = 21;
const TOUCH_FADE = 0.25;

// The header: the title band is HEADER pt below the safe area, then the grid begins.
const HEADER = 78;
const EDGE_FADE = 22;

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';
const LOG_COLS = LADDER.map((r) => Math.log(r.cols));

type Fonts = { title: SkFont; small: SkFont; name: SkFont; note: SkFont; rung: SkFont };
type Loaded = { image: SkImage; at: number };
/** Everything a frame of the grid depends on besides the plate. */
type View2 = {
  width: number;
  height: number;
  top: number;
  bottom: number;
  z: number;
  scroll: number;
  sideways: number;
  focalX: number;
  focalY: number;
};

function clamp(value: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, value));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Resistance past an edge: the further it is pulled, the less it gives, never past `max`. */
function rubber(offset: number, max: number) {
  'worklet';
  const sign = offset < 0 ? -1 : 1;
  return sign * max * (1 - 1 / (Math.abs(offset) / max + 1));
}

/** `value` kept within lo..hi, with the part beyond either edge given back with resistance. */
function rubberClamp(value: number, lo: number, hi: number, max: number) {
  'worklet';
  if (value < lo) return lo + rubber(value - lo, max);
  if (value > hi) return hi + rubber(value - hi, max);
  return value;
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** The lower rung, and how far towards the next one, for a place `z` on the ladder. */
function split(z: number) {
  'worklet';
  // A NaN z would index LADDER out of range below, and `LADDER[NaN].cols` throws.
  const zc = clamp(Number.isNaN(z) ? 0 : z, 0, LAST);
  const k = Math.min(Math.floor(zc), LAST - 1);
  return { k, f: zc - k };
}

/** Plate `i`'s frame on rung `k`, in content coordinates (before scrolling). */
function slot(k: number, i: number, width: number, top: number, out: number[]) {
  'worklet';
  const r = LADDER[k];
  const w = (width - r.margin * 2 - r.gap * (r.cols - 1)) / r.cols;
  const h = w * r.aspect;
  out[0] = r.margin + (i % r.cols) * (w + r.gap);
  out[1] = top + Math.floor(i / r.cols) * (h + r.label + r.gap);
  out[2] = w;
  out[3] = h;
  out[4] = r.radius;
}

/** Plate `i`'s frame at `z`, between the two rungs on either side, in content coordinates. */
function between(z: number, i: number, width: number, top: number, out: number[], a: number[], b: number[]) {
  'worklet';
  const { k, f } = split(z);
  slot(k, i, width, top, a);
  slot(k + 1, i, width, top, b);
  for (let c = 0; c < 5; c += 1) out[c] = mix(a[c], b[c], f);
}

type Stage = { width: number; height: number; top: number; bottom: number };

/** The grid's full height on rung `k`, header and bottom margin included. */
function gridLength(k: number, n: number, v: Stage) {
  'worklet';
  const r = LADDER[k];
  const w = (v.width - r.margin * 2 - r.gap * (r.cols - 1)) / r.cols;
  return v.top + Math.ceil(n / r.cols) * (w * r.aspect + r.label + r.gap) - r.gap + v.bottom;
}

/** How far the grid can scroll at `z`. */
function maxScroll(z: number, n: number, v: Stage) {
  'worklet';
  const { k, f } = split(z);
  return Math.max(0, mix(gridLength(k, n, v), gridLength(k + 1, n, v), f) - v.height);
}

/** Where on the ladder a pinch is, from its log column count, with resistance past either end. */
function rungForLog(logCols: number) {
  'worklet';
  if (logCols >= LOG_COLS[0]) {
    const over = (logCols - LOG_COLS[0]) / (LOG_COLS[0] - LOG_COLS[1]);
    return -rubber(over, OVERPINCH);
  }
  if (logCols <= LOG_COLS[LAST]) {
    const over = (LOG_COLS[LAST] - logCols) / (LOG_COLS[LAST - 1] - LOG_COLS[LAST]);
    return LAST + rubber(over, OVERPINCH);
  }
  for (let k = 0; k < LAST; k += 1) {
    if (logCols <= LOG_COLS[k] && logCols >= LOG_COLS[k + 1]) {
      return k + (LOG_COLS[k] - logCols) / (LOG_COLS[k] - LOG_COLS[k + 1]);
    }
  }
  return LAST;
}

function logForRung(z: number) {
  'worklet';
  const { k, f } = split(z);
  return mix(LOG_COLS[k], LOG_COLS[k + 1], f);
}

/** How many rungs one unit of log columns is worth around `z`. */
function rungsPerLog(z: number) {
  'worklet';
  const { k } = split(z);
  return 1 / (LOG_COLS[k] - LOG_COLS[k + 1]);
}

/** The column count at `z`, for the touch marks' spread. */
function columnsAt(z: number) {
  'worklet';
  return Math.exp(logForRung(z));
}

/** Plate `i`'s frame on screen: between the rungs, scrolled, slid sideways, and grown past the ends. */
function onScreen(i: number, v: View2, out: number[], a: number[], b: number[]) {
  'worklet';
  between(v.z, i, v.width, v.top, out, a, b);
  out[0] += v.sideways;
  out[1] -= v.scroll;
  const over = v.z < 0 ? v.z : v.z > LAST ? v.z - LAST : 0;
  if (over !== 0) {
    const s = 1 + over * OVERPINCH_SCALE;
    out[0] = v.focalX + (out[0] - v.focalX) * s;
    out[1] = v.focalY + (out[1] - v.focalY) * s;
    out[2] *= s;
    out[3] *= s;
  }
}

/** A label cut to `max` pt, with an ellipsis when it does not fit. */
function fit(font: SkFont, text: string, max: number) {
  'worklet';
  if (font.measureText(text).width <= max) return text;
  let cut = text;
  while (cut.length > 1 && font.measureText(`${cut}…`).width > max) cut = cut.slice(0, -1);
  return `${cut.trimEnd()}…`;
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

/** One plate: its photo cropped to fill a rounded frame, the sharp copy fading in over the small one. */
function drawPlate(
  canvas: SkCanvas,
  frame: number[],
  thumb: Loaded | null,
  sharp: Loaded | null,
  now: number,
  paint: SkPaint,
  alpha: number,
) {
  'worklet';
  const [x, y, w, h, r] = frame;
  const rect = Skia.XYWHRect(x, y, w, h);
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(rect, r, r), ClipOp.Intersect, true);
  paint.setColor(Skia.Color(TILE_TONE));
  paint.setAlphaf(alpha);
  canvas.drawRect(rect, paint);
  for (const copy of [thumb, sharp]) {
    if (!copy) continue;
    const image = copy.image;
    const iw = image.width();
    const ih = image.height();
    const scale = Math.max(w / iw, h / ih);
    const sw = w / scale;
    const sh = h / scale;
    paint.setAlphaf(alpha * clamp((now - copy.at) / IMAGE_FADE, 0, 1));
    canvas.drawImageRectOptions(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), rect, FilterMode.Linear, MipmapMode.Linear, paint);
  }
  canvas.restore();
}

/** The plate's name, its note and its number under it, set larger at one column. */
function drawLabel(canvas: SkCanvas, frame: number[], item: Plate, i: number, fonts: Fonts, ink: SkPaint, shown: number, grow: number) {
  'worklet';
  const [x, y, w, h] = frame;
  const s = 1 + grow * LABEL_GROW;
  canvas.save();
  canvas.translate(x, y + h);
  canvas.scale(s, s);
  const room = w / s;
  const number = `No. ${String(i + 1).padStart(2, '0')}`;
  const numberWidth = trackedWidth(fonts.small, number, 0.6);
  ink.setAlphaf(shown);
  canvas.drawText(fit(fonts.name, item.name, room - numberWidth - 8), 1, 19, ink, fonts.name);
  ink.setAlphaf(shown * 0.55);
  canvas.drawText(fit(fonts.note, item.note, room - 2), 1, 35, ink, fonts.note);
  drawTracked(canvas, number, fonts.small, room - numberWidth, 18, 0.6, ink);
  canvas.restore();
}

/**
 * A herbarium of botanical plates whose column count follows a pinch continuously: spreading the
 * fingers walks 5 → 3 → 2 → 1 columns and pinching walks back, every tile sliding and growing
 * between the two neighbouring grids around the fingers rather than jumping. On release the grid
 * springs to the nearest column count with a tap of haptics. Names fade in under the plates at
 * two columns and one. A double tap toggles one column and three; a drag scrolls.
 */
export function PinchDensityGrid({ plates = PLATES, autoPlay = false, background = PAPER, ink = INK, onColumnsChange }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const n = plates.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const fonts = useMemo<Fonts>(
    () => ({
      title: matchFont({ fontFamily: FAMILY, fontSize: 30, fontWeight: '500' }),
      small: matchFont({ fontFamily: FAMILY, fontSize: 10, fontWeight: '500' }),
      name: matchFont({ fontFamily: FAMILY, fontSize: 14, fontWeight: '500' }),
      note: matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: '400' }),
      rung: matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: '500' }),
    }),
    [],
  );

  const stage = useSharedValue({ width: 0, height: 0, top: 0, bottom: 0 });
  const now = useSharedValue(0);
  const thumbs = useSharedValue<(Loaded | null)[]>([]);
  const sharp = useSharedValue<(Loaded | null)[]>([]);
  // Where the grid is on the ladder (0 = 5 columns … 3 = 1 column).
  const z = useSharedValue(START_RUNG);
  // The anchor: a point on one plate (in shares of its frame) that sits at screen height `focalY`.
  // Scrolling moves `focalY`; a pinch re-anchors under the fingers, so the plate stays under them.
  const anchor = useSharedValue(SCRIPT_ANCHOR);
  const anchorU = useSharedValue(0.5);
  const anchorV = useSharedValue(0.5);
  const focalX = useSharedValue(0);
  const focalY = useSharedValue(0);
  const sideways = useSharedValue(0);
  const ready = useSharedValue(false);
  // Gesture bookkeeping.
  const pinching = useSharedValue(false);
  const pinchLog = useSharedValue(0);
  const pinchFrom = useSharedValue({ x: 0, y: 0, focalY: 0, anchorX: 0, sideways: 0 });
  const panFrom = useSharedValue(0);
  // Under reduced motion the grid fades out, changes and fades in.
  const fade = useSharedValue(1);
  // The self-playing loop.
  const looping = useSharedValue(auto);
  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const scriptFocal = useSharedValue(-1);
  const wanted = useSharedValue({ first: -1, last: -1 });

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
    scriptFocal.set(-1);
  }, [auto, clock, hold, looping, scriptFocal]);

  // The small copies, all at once.
  useEffect(() => {
    let alive = true;
    thumbs.set(plates.map(() => null));
    sharp.set(plates.map(() => null));
    plates.forEach((item, i) => {
      Skia.Data.fromURI(unsplash(item.id, THUMB_WIDTH))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          thumbs.modify((list) => {
            'worklet';
            const next = list.slice();
            next[i] = { image, at: now.get() };
            return next;
          });
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
  }, [plates, thumbs, sharp, now]);

  // The sharp copies, only for the plates near the screen at two columns or one.
  const requested = useRef(new Set<number>());
  const wantSharp = (first: number, last: number) => {
    const keepFrom = first - SHARP_KEEP;
    const keepTo = last + SHARP_KEEP;
    const drop: number[] = [];
    requested.current.forEach((i) => {
      if (i < keepFrom || i > keepTo) drop.push(i);
    });
    drop.forEach((i) => requested.current.delete(i));
    if (drop.length > 0) {
      sharp.modify((list) => {
        'worklet';
        const next = list.slice();
        for (const i of drop) next[i] = null;
        return next;
      });
    }
    for (let i = Math.max(0, first - SHARP_AHEAD); i <= Math.min(n - 1, last + SHARP_AHEAD); i += 1) {
      if (requested.current.has(i)) continue;
      requested.current.add(i);
      const index = i;
      Skia.Data.fromURI(unsplash(plates[index].id, SHARP_WIDTH))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!image || !requested.current.has(index)) return;
          sharp.modify((list) => {
            'worklet';
            const next = list.slice();
            next[index] = { image, at: now.get() };
            return next;
          });
        })
        .catch(() => requested.current.delete(index));
    }
  };

  const committed = (rung: number) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onColumnsChange?.(LADDER[rung].cols);
  };

  const view = (): View2 => {
    'worklet';
    const s = stage.get();
    const zNow = z.get();
    // The anchor point's height in content coordinates, and so the scroll that puts it at `focalY`.
    const a = [0, 0, 0, 0, 0];
    const b = [0, 0, 0, 0, 0];
    const frame = [0, 0, 0, 0, 0];
    between(zNow, anchor.get(), s.width, s.top, frame, a, b);
    const raw = frame[1] + frame[3] * anchorV.get() - focalY.get();
    return {
      ...s,
      z: zNow,
      scroll: rubberClamp(raw, 0, maxScroll(zNow, n, s), RUBBER_MAX),
      sideways: sideways.get(),
      focalX: focalX.get(),
      focalY: focalY.get(),
    };
  };

  /** The `focalY` that keeps the anchor where it is at rung `to`, moved so the grid stays in range. */
  const focalFor = (to: number, focal: number) => {
    'worklet';
    const s = stage.get();
    const a = [0, 0, 0, 0, 0];
    const b = [0, 0, 0, 0, 0];
    const frame = [0, 0, 0, 0, 0];
    between(to, anchor.get(), s.width, s.top, frame, a, b);
    const y = frame[1] + frame[3] * anchorV.get();
    return y - clamp(y - focal, 0, maxScroll(to, n, s));
  };

  /** Re-anchors on the plate under (or nearest to) a point on screen, without moving anything. */
  const anchorAt = (px: number, py: number) => {
    'worklet';
    const v = view();
    const a = [0, 0, 0, 0, 0];
    const b = [0, 0, 0, 0, 0];
    const frame = [0, 0, 0, 0, 0];
    let best = 0;
    let bestDistance = Number.MAX_VALUE;
    let u = 0.5;
    let w = 0.5;
    for (let i = 0; i < n; i += 1) {
      onScreen(i, v, frame, a, b);
      const dx = Math.max(frame[0] - px, 0, px - frame[0] - frame[2]);
      const dy = Math.max(frame[1] - py, 0, py - frame[1] - frame[3]);
      const d = dx * dx + dy * dy;
      if (d < bestDistance) {
        bestDistance = d;
        best = i;
        u = (px - frame[0]) / frame[2];
        w = (py - frame[1]) / frame[3];
      }
    }
    // The new anchor's scroll has to match what is on screen now.
    const target = [0, 0, 0, 0, 0];
    between(v.z, best, v.width, v.top, target, a, b);
    anchor.set(best);
    anchorU.set(u);
    anchorV.set(w);
    focalX.set(px);
    focalY.set(target[1] + target[3] * w - v.scroll);
  };

  /** Springs to rung `to`, keeping the anchor in place and the grid in range. */
  const settle = (to: number, velocity: number, user: boolean) => {
    'worklet';
    const focal = focalFor(to, focalY.get());
    if (reducedMotion) {
      fade.set(
        withSequence(
          withTiming(0, { duration: 140 }, () => {
            z.set(to);
            focalY.set(focal);
            sideways.set(0);
          }),
          withTiming(1, { duration: 200 }),
        ),
      );
    } else {
      z.set(withSpring(to, { ...SETTLE_SPRING, velocity }));
      focalY.set(withSpring(focal, SCROLL_SPRING));
      sideways.set(withSpring(0, SCROLL_SPRING));
    }
    if (user) scheduleOnRN(committed, to);
  };

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    scriptFocal.set(-1);
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64) / 1000;
    const s = stage.get();
    if (s.width === 0) return;
    now.set(now.get() + dt);
    if (!ready.get()) {
      // At rest on the first rung, scrolled to the top, anchored on the scripted plate.
      const a = [0, 0, 0, 0, 0];
      const b = [0, 0, 0, 0, 0];
      const frame = [0, 0, 0, 0, 0];
      between(START_RUNG, SCRIPT_ANCHOR, s.width, s.top, frame, a, b);
      anchor.set(SCRIPT_ANCHOR);
      anchorU.set(0.5);
      anchorV.set(0.5);
      focalX.set(frame[0] + frame[2] / 2);
      focalY.set(frame[1] + frame[3] / 2);
      ready.set(true);
    }

    // The script: z follows the eased moves, and the anchor stays at the height it started at,
    // unless that would scroll the grid out of range.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(SCRIPT_REST[clamp(Math.round(z.get()), 0, LAST)]);
    } else if (looping.get() && !pinching.get()) {
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      if (scriptFocal.get() < 0) scriptFocal.set(focalY.get());
      let rung = START_RUNG;
      for (const step of SCRIPT) {
        if (t >= step.at) {
          const from = rung;
          rung = step.to;
          if (t < step.at + step.span) rung = mix(from, step.to, easeInOut((t - step.at) / step.span));
        }
      }
      z.set(rung);
      focalY.set(focalFor(rung, scriptFocal.get()));
    }

    // Which plates are on screen, for the sharp copies, reported only when it changes.
    const v = view();
    if (v.z > LABEL_FROM) {
      const a = [0, 0, 0, 0, 0];
      const b = [0, 0, 0, 0, 0];
      const frame = [0, 0, 0, 0, 0];
      let first = -1;
      let last = -1;
      for (let i = 0; i < n; i += 1) {
        onScreen(i, v, frame, a, b);
        if (frame[1] + frame[3] < 0 || frame[1] > s.height) continue;
        if (first < 0) first = i;
        last = i;
      }
      const w = wanted.get();
      if (first !== w.first || last !== w.last) {
        wanted.set({ first, last });
        if (first >= 0) scheduleOnRN(wantSharp, first, last);
      }
    }
  });

  const pinch = Gesture.Pinch()
    .onStart((event) => {
      touched();
      pinching.set(true);
      cancelAnimation(z);
      cancelAnimation(focalY);
      cancelAnimation(sideways);
      anchorAt(event.focalX, event.focalY);
      const s = stage.get();
      const a = [0, 0, 0, 0, 0];
      const b = [0, 0, 0, 0, 0];
      const frame = [0, 0, 0, 0, 0];
      between(z.get(), anchor.get(), s.width, s.top, frame, a, b);
      pinchLog.set(logForRung(clamp(z.get(), 0, LAST)));
      pinchFrom.set({
        x: event.focalX,
        y: event.focalY,
        focalY: focalY.get(),
        anchorX: frame[0] + frame[2] * anchorU.get(),
        sideways: sideways.get(),
      });
    })
    .onUpdate((event) => {
      const from = pinchFrom.get();
      const rung = rungForLog(pinchLog.get() - PINCH_GAIN * Math.log(Math.max(event.scale, 0.01)));
      z.set(rung);
      focalX.set(event.focalX);
      focalY.set(from.focalY + (event.focalY - from.y));
      // The plate under the fingers drifts sideways as the columns change; follow part of it.
      const s = stage.get();
      const a = [0, 0, 0, 0, 0];
      const b = [0, 0, 0, 0, 0];
      const frame = [0, 0, 0, 0, 0];
      between(rung, anchor.get(), s.width, s.top, frame, a, b);
      const drift = from.anchorX - (frame[0] + frame[2] * anchorU.get());
      sideways.set(from.sideways + rubber(drift * SIDEWAYS_FOLLOW + (event.focalX - from.x), SIDEWAYS_MAX));
    })
    .onEnd((event) => {
      touched();
      pinching.set(false);
      // Throwing the pinch carries on along the ladder; it lands on the rung it was heading for.
      const zNow = z.get();
      // Spreading (scale rising) lowers the log column count, which moves up the ladder. iOS can
      // report a NaN pinch velocity (a pinch lifted at once); it is taken as no throw at all.
      const raw = PINCH_GAIN * (event.velocity / Math.max(event.scale, 0.01)) * rungsPerLog(zNow);
      const velocity = Number.isFinite(raw) ? clamp(raw, -8, 8) : 0;
      const landing = Math.round(zNow + clamp(velocity * PROJECT, -PROJECT_MAX, PROJECT_MAX));
      const to = clamp(Number.isNaN(landing) ? START_RUNG : landing, 0, LAST);
      settle(to, velocity, true);
    })
    .onFinalize(() => {
      // A pinch cancelled without onEnd would leave the grid between rungs, the drag and the
      // script locked out; it lands on the nearest rung instead.
      if (!pinching.get()) return;
      pinching.set(false);
      const zNow = z.get();
      settle(clamp(Number.isNaN(zNow) ? START_RUNG : Math.round(zNow), 0, LAST), 0, false);
    });

  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(4)
    .onStart(() => {
      if (pinching.get()) return;
      touched();
      cancelAnimation(focalY);
      panFrom.set(focalY.get());
    })
    .onUpdate((event) => {
      if (pinching.get()) return;
      focalY.set(panFrom.get() + event.translationY);
    })
    .onEnd((event) => {
      if (pinching.get()) return;
      touched();
      // The anchor's height that scrolls the grid to its end, and to its top.
      const lo = focalFor(z.get(), -1e6);
      const hi = focalFor(z.get(), 1e6);
      focalY.set(
        withDecay({
          velocity: event.velocityY,
          clamp: [lo, hi],
          rubberBandEffect: true,
          rubberBandFactor: 0.7,
        }),
      );
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .maxDelay(260)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      cancelAnimation(z);
      cancelAnimation(focalY);
      anchorAt(event.x, event.y);
      settle(Math.round(z.get()) === ONE_COLUMN ? THREE_COLUMNS : ONE_COLUMN, 0, true);
    });

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (!size || !ready.get()) return recorder.finishRecordingAsPicture();
    const v = view();
    const t = now.get();
    const small = thumbs.get();
    const big = sharp.get();
    const alpha = fade.get();

    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Skia.Color(background));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);

    const inkPaint = Skia.Paint();
    inkPaint.setAntiAlias(true);
    inkPaint.setColor(Skia.Color(ink));
    const zc = clamp(v.z, 0, LAST);
    const labels = clamp((zc - LABEL_FROM) / (LABEL_TO - LABEL_FROM), 0, 1) * alpha;
    const grow = clamp(zc - 2, 0, 1);
    const a = [0, 0, 0, 0, 0];
    const b = [0, 0, 0, 0, 0];
    const frame = [0, 0, 0, 0, 0];
    for (let i = 0; i < n; i += 1) {
      onScreen(i, v, frame, a, b);
      if (frame[1] > height || frame[1] + frame[3] + 60 < 0 || frame[0] > width || frame[0] + frame[2] < 0) continue;
      drawPlate(canvas, frame, small[i] ?? null, big[i] ?? null, t, paint, alpha);
      if (labels > 0.01) drawLabel(canvas, frame, plates[i], i, fonts, inkPaint, labels, grow);
    }

    // The paper runs under the header and the home indicator, fading into the grid.
    const head = v.top - EDGE_FADE + 8;
    const shade = Skia.Paint();
    shade.setColor(Skia.Color(background));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, head), shade);
    const solid = Skia.Color(background);
    const clear = Float32Array.of(solid[0], solid[1], solid[2], 0);
    shade.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: head }, { x: 0, y: head + EDGE_FADE }, [solid, clear], null, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(0, head, width, EDGE_FADE), shade);
    const foot = height - insets.bottom - EDGE_FADE;
    shade.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: foot }, { x: 0, y: height }, [clear, solid], null, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(0, foot, width, height - foot), shade);

    // Title, count and the ladder, the current rung marked by a pill that slides between them.
    const titleY = insets.top + 34;
    inkPaint.setAlphaf(1);
    canvas.drawText('Herbarium', 18, titleY, inkPaint, fonts.title);
    inkPaint.setAlphaf(0.5);
    drawTracked(canvas, `${n} PLATES · PINCH`, fonts.small, 20, titleY + 20, 1.1, inkPaint);
    const step = 28;
    const right = width - 18;
    const left = right - step * LADDER.length;
    const pillX = left + step * (zc + 0.5);
    inkPaint.setAlphaf(1);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(pillX - 12, titleY - 19, 24, 24), 12, 12), inkPaint);
    for (let k = 0; k < LADDER.length; k += 1) {
      const label = String(LADDER[k].cols);
      const near = clamp(1 - Math.abs(zc - k), 0, 1);
      const x = left + step * (k + 0.5) - fonts.rung.measureText(label).width / 2;
      inkPaint.setColor(Skia.Color(ink));
      inkPaint.setAlphaf(0.45 * (1 - near));
      canvas.drawText(label, x, titleY - 3, inkPaint, fonts.rung);
      inkPaint.setColor(Skia.Color(background));
      inkPaint.setAlphaf(near);
      canvas.drawText(label, x, titleY - 3, inkPaint, fonts.rung);
    }
    inkPaint.setColor(Skia.Color(ink));
    inkPaint.setAlphaf(0.5);
    const colsLabel = 'COLUMNS';
    drawTracked(canvas, colsLabel, fonts.small, right - trackedWidth(fonts.small, colsLabel, 1.1) - 6, titleY + 20, 1.1, inkPaint);

    // The scripted pinch's two fingers, while it moves.
    if (looping.get() && hold.get() <= 0) {
      const clockNow = clock.get();
      for (const scripted of SCRIPT) {
        const into = clockNow - scripted.at;
        const shown = clamp(Math.min((into + TOUCH_FADE) / TOUCH_FADE, (scripted.span + TOUCH_FADE - into) / TOUCH_FADE), 0, 1);
        if (shown <= 0) continue;
        const fromRung = SCRIPT[(SCRIPT.indexOf(scripted) + SCRIPT.length - 1) % SCRIPT.length].to;
        const outward = scripted.to > fromRung;
        const spread = (outward ? 34 : 120) * Math.pow(columnsAt(fromRung) / columnsAt(zc), 0.85);
        const d = clamp(spread, 26, 150) * Math.SQRT1_2;
        const finger = Skia.Paint();
        finger.setAntiAlias(true);
        for (const sign of [-1, 1]) {
          const fx = v.focalX + sign * d;
          const fy = v.focalY + sign * d;
          finger.setStyle(PaintStyle.Fill);
          finger.setColor(Skia.Color('#ffffff'));
          finger.setAlphaf(0.5 * shown);
          canvas.drawCircle(fx, fy, TOUCH_RADIUS, finger);
          finger.setStyle(PaintStyle.Stroke);
          finger.setStrokeWidth(1.5);
          finger.setColor(Skia.Color(ink));
          finger.setAlphaf(0.45 * shown);
          canvas.drawCircle(fx, fy, TOUCH_RADIUS, finger);
        }
      }
    }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Simultaneous(pinch, Gesture.Race(pan, doubleTap))}>
      <View
        style={[styles.screen, { backgroundColor: background }]}
        accessible
        accessibilityRole="image"
        accessibilityLabel={`Herbarium, ${n} botanical plates`}
        accessibilityHint="Pinch to change the number of columns; double tap to switch between one and three"
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
          stage.set({ width: w, height: h, top: insets.top + HEADER, bottom: insets.bottom + 28 });
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
