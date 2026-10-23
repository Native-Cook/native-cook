import {
  Canvas,
  ClipOp,
  FilterMode,
  matchFont,
  MipmapMode,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkPath,
  StrokeCap,
  StrokeJoin,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  Easing,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { PIXEL_GLYPHS, pixelBlockPath, pixelGlyphs } from './MorphBubbleColumnGlyphs';

export type SurveySite = {
  /** Unsplash photo id (a portrait photo reads best, as it opens to the whole card). */
  id: string;
  /** The site's name beside its bubble. */
  name: string;
  /** Its position, shown under the name while the site is selected. */
  coords: string;
  /** The heading that types itself once the photo is open, already broken into lines. */
  heading: string[];
  /** The reading that counts up in big dot-matrix figures. */
  value: number;
  /** Two short lines under the reading; the last one gets trailing dots. */
  caption: string[];
  /** A short readout under the target ring. */
  tag: string;
  /** Where the target ring sits on the open photo, as shares of the card. */
  ring: [number, number];
  /** Text colour over the open photo. */
  ink: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
};

type Props = {
  /** The sites down the column, top to bottom (five fit any phone). */
  sites?: SurveySite[];
  /** Small photo in the pill at the top right. */
  avatar?: string;
  /** The name in the pill. */
  greeting?: string;
  /** Lines at the top left of the card. */
  date?: string[];
  /** The clock in pixels: digits, ':', 'P' and 'M'. */
  clock?: string;
  /** Walks the selection down the column, opens a site, reads it, closes it and walks back. */
  autoPlay?: boolean;
  /** Called with a site's index when it opens. */
  onOpen?: (index: number) => void;
};

const unsplash = (id: string, size: string) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&${size}&q=80`;

const SITES: SurveySite[] = [
  {
    id: 'photo-1618336174403-79bfae755448',
    name: 'GLACIER FRONT',
    coords: '64.02°N 16.21°W',
    heading: ['MEASURE', 'ICE FRONT:', '74 M'],
    value: 74,
    caption: ['TRACKING_CALVING_', 'FACE'],
    tag: 'FRONT SKAFT-1',
    ring: [0.6, 0.42],
    ink: '#ffffff',
    tint: '#9fb9cc',
  },
  {
    id: 'photo-1520811288788-6f2e3da049e0',
    name: 'RED STRATA',
    coords: '36.86°N 111.37°W',
    heading: ['SAMPLE', 'STRATA', 'LAYER: 07'],
    value: 36,
    caption: ['MAPPING_RIDGE_', 'LINES'],
    tag: 'GRID K-12',
    ring: [0.62, 0.44],
    ink: '#ffffff',
    tint: '#9a5a3a',
  },
  {
    id: 'photo-1524799526615-766a9833dec0',
    name: 'DUNE FIELD',
    coords: '23.40°N 12.85°E',
    heading: ['CALCULATE', 'AZIMUTH:', '345°'],
    value: 80,
    caption: ['ANALYZING_SURFACE_', 'BIOME'],
    tag: 'TARGET LUNA-2',
    ring: [0.51, 0.4],
    ink: '#ffffff',
    tint: '#8fa9b8',
  },
  {
    id: 'photo-1686861453864-9b2a22a97fc9',
    name: 'CRATER RIM',
    coords: '0.67°N 23.47°E',
    heading: ['MEASURE', 'CRATER', 'DEPTH: 2.4KM'],
    value: 12,
    caption: ['SCANNING_CRATER_', 'FIELD'],
    tag: 'RIM MARE-09',
    ring: [0.58, 0.46],
    ink: '#ffffff',
    tint: '#6a6a68',
  },
  {
    id: 'photo-1576059620175-9b608f96797b',
    name: 'ICE VAULT',
    coords: '64.40°N 16.80°W',
    heading: ['TRACE', 'MELT RATE:', '0.8 M/DAY'],
    value: 24,
    caption: ['SOUNDING_ICE_', 'CAVE'],
    tag: 'VAULT SKAFT-3',
    ring: [0.5, 0.5],
    ink: '#ffffff',
    tint: '#6f9cc0',
  },
];

const AVATAR = 'photo-1641901960200-1e878f0cbf63';

// Colours: a white screen with a faint dot grid, near-black ink; graphite shows in the rail's notch.
const PAGE = '#1d1d1b';
const CARD = '#f4f4f0';
const GRID_DOT = '#c9c9c2';
const INK = '#141414';
const MUTED = '#8b8b84';
const ON_PAGE = '#ecece6';
const GRID_STEP = 22;

// The card is the whole screen, edge to edge (the display's own corners round it). A notch cut into
// its right edge holds the tool rail (pt).
const CARD_INSET = 0;
const CARD_RADIUS = 0;
const NOTCH_AT = 0.56;
const NOTCH_HEIGHT = 128;
const NOTCH_DEPTH = 34;
const NOTCH_RADIUS = 13;

// The column: its middle sits this share of the card from the left; a bubble's unit size is this
// share of the card's width, scaled by the zoom slider between ZOOM_MIN and ZOOM_MAX.
const COLUMN_X = 0.57;
const UNIT = 0.21;
const GAP = 14;
const ZOOM_MIN = 0.92;
const ZOOM_MAX = 1.08;
// Each bubble sits a little off the column's axis, as in a hand-set column; the selected one centres.
const NUDGE = [0.08, -0.06, 0.07, -0.08, 0.05];
// The selected bubble: a rounded square this many units wide and tall, with corners of this share of
// half its side and a squircle shoulder of this exponent (2 is a circle's arc).
const BIG_W = 1.5;
const BIG_H = 1.42;
const BIG_R = 0.4;
const BIG_N = 3.6;

// Selection: a number from 0 (top site) to the last site, on a spring. Under a finger it moves one
// site per FOCUS_DRAG pt and rubber-bands past the ends; on release the finger's speed carries it
// FOCUS_THROW s further before it settles on the nearest site.
const FOCUS_DRAG = 92;
const FOCUS_RUBBER = 0.3;
const FOCUS_THROW = 0.16;
const HAND_STIFFNESS = 210;
const HAND_DAMPING = 2 * 0.82 * Math.sqrt(HAND_STIFFNESS);
// When the loop or a tap moves it: a calmer, critically damped spring (about 0.8 s).
const CALM_STIFFNESS = 70;
const CALM_DAMPING = 2 * Math.sqrt(CALM_STIFFNESS);

// First appearance: every bubble blooms from a dot, top to bottom.
const DOT = 0.1;
const ENTER_DELAY = 0.2;
const ENTER_STAGGER = 0.11;
const BLOOM_OMEGA = 9;
const BLOOM_ZETA = 0.8;

// Opening: a spring on 0 (in the column) … 1 (the whole card). A drag down by DISMISS_SHARE of the
// screen closes it fully; a release faster than DISMISS_SPEED pt/s, or below CLOSE_BELOW, closes it.
const OPEN_STIFFNESS = 95;
const OPEN_DAMPING = 17.5;
const DISMISS_SHARE = 0.6;
const DISMISS_SPEED = 650;
const CLOSE_BELOW = 0.8;
const RUBBER = 0.22;
// The other bubbles sink back a little while one opens.
const SINK = 0.08;

// Once open: the heading types TYPE_STEP s a character after TYPE_DELAY s, the reading counts for
// COUNT_TIME s after COUNT_DELAY s, and the cursor blinks at CURSOR_HZ.
const TYPE_DELAY = 0.7;
const TYPE_STEP = 0.06;
const COUNT_DELAY = 1.6;
const COUNT_TIME = 1.6;
const CURSOR_HZ = 1.6;
const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);
// The reading is set in dot-matrix figures: pixels PIXEL pt apart, with this share of each left as
// the gap between them.
const PIXEL = 8;
const PIXEL_GAP = 0.24;
// The open photo zooms in with the slider: 1 + ZOOM_PHOTO × slider.
const ZOOM_PHOTO = 0.2;

// The zoom slider: rest value, and the spring that glides it when not held.
const SLIDER_REST = 0.5;
const SLIDER_STIFFNESS = 26;
const SLIDER_DAMPING = 10.2;
const SLIDER_STEPS = 8;

// autoPlay (s): the selection walks down two sites, the third opens and is read while the slider
// zooms in and out, then it closes and the selection walks back to the top, where the loop rests.
const LOOP = 18;
const STEPS: [number, number][] = [
  [1.2, 1],
  [2.7, 2],
  [14.2, 1],
  [15.6, 0],
];
const OPEN_AT = 4.3;
const ZOOM_IN_AT = 8.6;
const ZOOM_IN_TO = 0.82;
const ZOOM_OUT_AT = 10.8;
const CLOSE_AT = 12.4;
const AUTO_SITE = 2;
// After a touch the loop waits this long; longer when the touch opened a photo, so it can be read.
const USER_HOLD = 4;
const OPEN_HOLD = 8;

// Photos fade in over IMAGE_FADE s as they arrive. Bubbles and the open card show the same
// portrait crop, sized for each, so the sharp copy cross-fades over the thumbnail without a shift.
const IMAGE_FADE = 0.35;
const THUMB_SIZE = 'w=400&h=880';
const SHARP_SIZE = 'w=828&h=1820';

const MONO = process.env.EXPO_OS === 'ios' ? 'Menlo' : 'monospace';

type Stage = { width: number; height: number; top: number; bottom: number };
type Card = { x: number; y: number; w: number; h: number };
type Loaded = { image: SkImage; at: number };
type Box = { x: number; y: number; w: number; h: number; r: number; n: number; scale: number; big: number };
type Fonts = { label: SkFont; tiny: SkFont; heading: SkFont; caption: SkFont };
/** Each dot-matrix figure, one pixel to a unit, and how many pixels wide it is. */
type Figures = Record<string, { path: SkPath; cols: number }>;

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** True when the loop clock went from `before` to `now` across `at`, wrapping at LOOP. */
function passed(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

/** The size multiplier of a bubble `t` s after it starts to bloom from a dot. */
function bloomScale(t: number) {
  'worklet';
  if (t <= 0) return DOT;
  const wd = BLOOM_OMEGA * Math.sqrt(1 - BLOOM_ZETA * BLOOM_ZETA);
  const decay = Math.exp(-BLOOM_ZETA * BLOOM_OMEGA * t);
  return 1 - (1 - DOT) * decay * (Math.cos(wd * t) + ((BLOOM_ZETA * BLOOM_OMEGA) / wd) * Math.sin(wd * t));
}

function springStep(value: number, velocity: number, target: number, dt: number, k: number, c: number) {
  'worklet';
  const v = velocity + (k * (target - value) - c * velocity) * dt;
  return [value + v * dt, v];
}

function cardOf(room: Stage): Card {
  'worklet';
  return { x: CARD_INSET, y: CARD_INSET, w: room.width - 2 * CARD_INSET, h: room.height - 2 * CARD_INSET };
}

function barTop(room: Stage) {
  'worklet';
  return room.height - Math.max(room.bottom + 4, 14) - 56;
}

/** The slider track's ends and its touch band on screen. */
function track(room: Stage) {
  'worklet';
  const top = barTop(room);
  return { x0: CARD_INSET + 10 + 112, x1: room.width - CARD_INSET - 10 - 22, y: top + 24, top, bottom: top + 56 };
}

/**
 * Where each bubble sits this frame. A bubble is as much "selected" as the selection number is
 * close to it, and grows from a circle into the big rounded square by that much. The column is laid
 * out from those sizes, each bubble GAP below the one above, so a growing bubble pushes its
 * neighbours instead of overlapping them; the whole column stays centred between the clock and the
 * bar, and shrinks to fit if it would ever outgrow that room.
 */
function columnBoxes(count: number, focus: number, room: Stage, zoom: number, since: number, still: boolean): Box[] {
  'worklet';
  const card = cardOf(room);
  const unit = card.w * UNIT * mix(ZOOM_MIN, ZOOM_MAX, zoom);
  const boxes: Box[] = [];
  let total = 0;
  for (let s = 0; s < count; s += 1) {
    const big = clamp01(1 - Math.abs(s - focus));
    // Corners ease more than size, so a half-selected bubble still reads as round.
    const soft = big * big * (3 - 2 * big);
    const scale = still ? 1 : bloomScale(since - ENTER_DELAY - s * ENTER_STAGGER);
    const box = {
      x: 0,
      y: 0,
      w: mix(1, BIG_W, big) * unit * scale,
      h: mix(1, BIG_H, big) * unit * scale,
      r: mix(1, BIG_R, soft),
      n: mix(2, BIG_N, soft),
      scale,
      big,
    };
    boxes.push(box);
    total += mix(1, BIG_H, big) * unit + (s > 0 ? GAP : 0);
  }
  const top = Math.max(room.top, 50) + 92;
  const bottom = barTop(room) - 18;
  const fit = Math.min(1, (bottom - top) / Math.max(1, total));
  let y = (top + bottom) / 2 - (total * fit) / 2;
  for (let s = 0; s < count; s += 1) {
    const box = boxes[s];
    const slotH = mix(1, BIG_H, box.big) * unit * fit;
    box.w *= fit;
    box.h *= fit;
    const cx = card.x + card.w * COLUMN_X + NUDGE[s % NUDGE.length] * unit * (1 - box.big);
    box.x = cx - box.w / 2;
    // A blooming bubble grows from the middle of its slot.
    box.y = y + (slotH - box.h) / 2;
    y += slotH + GAP * fit;
  }
  return boxes;
}

/**
 * A rounded rectangle whose corners are quarter superellipses: radius `rf` × half the shorter side,
 * exponent `n`. With rf = 1 and n = 2 a square becomes a circle, with n ≈ 4 a squircle.
 */
function shapePath(x: number, y: number, w: number, h: number, rf: number, n: number): SkPath {
  'worklet';
  const rc = Math.max(0, Math.min(rf, 1)) * Math.min(w, h) * 0.5;
  const e = 2 / Math.max(2, n);
  const builder = Skia.PathBuilder.Make();
  const corners = [
    [x + w - rc, y + rc, -Math.PI / 2],
    [x + w - rc, y + h - rc, 0],
    [x + rc, y + h - rc, Math.PI / 2],
    [x + rc, y + rc, Math.PI],
  ];
  const steps = 14;
  for (let k = 0; k < 4; k += 1) {
    const [cx, cy, start] = corners[k];
    for (let i = 0; i <= steps; i += 1) {
      const a = start + (i / steps) * (Math.PI / 2);
      const c = Math.cos(a);
      const s = Math.sin(a);
      const px = cx + rc * Math.sign(c) * Math.pow(Math.abs(c), e);
      const py = cy + rc * Math.sign(s) * Math.pow(Math.abs(s), e);
      if (k === 0 && i === 0) builder.moveTo(px, py);
      else builder.lineTo(px, py);
    }
  }
  builder.close();
  return builder.build();
}

/** The card's outline: a rounded rectangle with a soft-cornered notch in its right edge. */
function cardPath(card: Card): SkPath {
  const l = card.x;
  const t = card.y;
  const r = card.x + card.w;
  const b = card.y + card.h;
  const n0 = t + card.h * NOTCH_AT - NOTCH_HEIGHT / 2;
  const n1 = n0 + NOTCH_HEIGHT;
  const inner = r - NOTCH_DEPTH;
  const corner = (builder: ReturnType<typeof Skia.PathBuilder.Make>, x1: number, y1: number, x2: number, y2: number, radius: number) =>
    radius > 0 ? builder.arcToTangent(x1, y1, x2, y2, radius) : builder.lineTo(x1, y1);
  const builder = Skia.PathBuilder.Make().moveTo(l + CARD_RADIUS, t);
  corner(builder, r, t, r, b, CARD_RADIUS);
  builder.arcToTangent(r, n0, inner, n0, NOTCH_RADIUS);
  builder.arcToTangent(inner, n0, inner, n1, NOTCH_RADIUS);
  builder.arcToTangent(inner, n1, r, n1, NOTCH_RADIUS);
  builder.arcToTangent(r, n1, r, b, NOTCH_RADIUS);
  corner(builder, r, b, l, b, CARD_RADIUS);
  corner(builder, l, b, l, t, CARD_RADIUS);
  corner(builder, l, t, r, t, CARD_RADIUS);
  return builder.close().build();
}

/** Every dot-matrix figure as one path of square pixels, built once. */
function makeFigures(): Figures {
  const figures: Figures = {};
  for (const ch of '0123456789+') {
    const rows = PIXEL_GLYPHS[ch];
    figures[ch] = { path: pixelBlockPath([rows], 0, 0, 1, PIXEL_GAP), cols: rows[0].length };
  }
  return figures;
}

/** `image` scaled to cover the box (and `zoom` more), centred, at `alpha`. */
function drawCover(canvas: SkCanvas, image: SkImage, x: number, y: number, w: number, h: number, zoom: number, alpha: number) {
  'worklet';
  const iw = image.width();
  const ih = image.height();
  if (iw <= 0 || ih <= 0 || w <= 0 || h <= 0) return;
  const fit = Math.max(w / iw, h / ih) * zoom;
  const sw = w / fit;
  const sh = h / fit;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setAlphaf(alpha);
  canvas.drawImageRectOptions(
    image,
    Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh),
    Skia.XYWHRect(x, y, w, h),
    FilterMode.Linear,
    MipmapMode.Linear,
    paint,
  );
}

function fillPaint(color: string, alpha: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  paint.setAlphaf(alpha);
  return paint;
}

function strokePaint(color: string, width: number, alpha: number) {
  'worklet';
  const paint = fillPaint(color, alpha);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(width);
  paint.setStrokeCap(StrokeCap.Round);
  paint.setStrokeJoin(StrokeJoin.Round);
  return paint;
}

/** One bubble: the site's tint, then its photo fading in, clipped to the bubble's shape. */
function drawBubble(canvas: SkCanvas, box: Box, site: SurveySite, loaded: Loaded | null, now: number, alpha: number) {
  'worklet';
  if (alpha <= 0.002 || box.w < 0.5) return;
  if (alpha < 0.995) {
    canvas.saveLayer(fillPaint('#000000', alpha), Skia.XYWHRect(box.x - 2, box.y - 2, box.w + 4, box.h + 4));
  } else {
    canvas.save();
  }
  canvas.clipPath(shapePath(box.x, box.y, box.w, box.h, box.r, box.n), ClipOp.Intersect, true);
  canvas.drawRect(Skia.XYWHRect(box.x, box.y, box.w, box.h), fillPaint(site.tint, 1));
  if (loaded) drawCover(canvas, loaded.image, box.x, box.y, box.w, box.h, 1, clamp01((now - loaded.at) / IMAGE_FADE));
  canvas.restore();
}

/**
 * A site's label, left of its bubble and centred on it: its number, its name and, while selected,
 * its position and a hairline that runs to the bubble.
 */
function drawSiteLabel(canvas: SkCanvas, site: SurveySite, index: number, count: number, box: Box, card: Card, fonts: Fonts, alpha: number) {
  'worklet';
  if (alpha <= 0.002) return;
  const x = card.x + 22;
  const cy = box.y + box.h / 2;
  const on = box.big;
  const number = `${index < 9 ? '0' : ''}${index + 1}/${count < 10 ? '0' : ''}${count}`;
  canvas.drawText(number, x, cy - 9, fillPaint(MUTED, alpha * mix(0.7, 1, on)), fonts.tiny);
  canvas.drawText(site.name, x, cy + 3, fillPaint(INK, alpha * mix(0.42, 1, on)), fonts.label);
  if (on > 0.01) {
    canvas.drawText(site.coords, x, cy + 15, fillPaint(MUTED, alpha * on), fonts.tiny);
    // The leader: from just past the name to just short of the bubble, drawn out as it is selected.
    const from = x + fonts.label.measureText(site.name).width + 8;
    const to = box.x - 8;
    if (to - from > 6) {
      const end = mix(from, to, EASE_OUT(on));
      canvas.drawLine(from, cy - 0.5, end, cy - 0.5, strokePaint(INK, 0.8, alpha * on));
      canvas.drawCircle(end, cy - 0.5, 1.8, fillPaint(INK, alpha * on));
    }
  }
}

/** The pill at the top right: the avatar, a greeting and a round bolt button. */
function drawPill(canvas: SkCanvas, room: Stage, avatar: Loaded | null, greeting: string, tiny: SkFont, now: number) {
  'worklet';
  const w = 48;
  const x = room.width - CARD_INSET - 14 - w;
  const y = Math.max(14, room.top - 40);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, 122), w / 2, w / 2), fillPaint(INK, 1));
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(x + 7, y + 7, 34, 34), 17, 17), ClipOp.Intersect, true);
  canvas.drawRect(Skia.XYWHRect(x + 7, y + 7, 34, 34), fillPaint('#555555', 1));
  if (avatar) drawCover(canvas, avatar.image, x + 7, y + 7, 34, 34, 1, clamp01((now - avatar.at) / IMAGE_FADE));
  canvas.restore();
  const width = tiny.measureText(greeting).width;
  canvas.drawText(greeting, x + w / 2 - width / 2, y + 58, fillPaint(CARD, 1), tiny);
  const cx = x + w / 2;
  const cy = y + 93;
  canvas.drawCircle(cx, cy, 16, fillPaint(CARD, 1));
  // A bolt, drawn: two offset triangles.
  const bolt = Skia.PathBuilder.Make()
    .moveTo(cx + 1.6, cy - 8)
    .lineTo(cx - 5, cy + 1.4)
    .lineTo(cx - 0.4, cy + 1.4)
    .lineTo(cx - 1.6, cy + 8)
    .lineTo(cx + 5, cy - 1.4)
    .lineTo(cx + 0.4, cy - 1.4)
    .close()
    .build();
  canvas.drawPath(bolt, fillPaint(INK, 1));
}

/** The tool rail in the card's notch: a viewfinder, a card and an exit icon, drawn in light lines. */
function drawRail(canvas: SkCanvas, card: Card) {
  'worklet';
  const cx = card.x + card.w - NOTCH_DEPTH / 2;
  const top = card.y + card.h * NOTCH_AT - NOTCH_HEIGHT / 2;
  const line = strokePaint(ON_PAGE, 1.4, 1);
  // Viewfinder: four corners.
  const v = Skia.PathBuilder.Make();
  const a = 6.5;
  const l = 3.2;
  const vy = top + 30;
  v.moveTo(cx - a, vy - a + l).lineTo(cx - a, vy - a).lineTo(cx - a + l, vy - a);
  v.moveTo(cx + a - l, vy - a).lineTo(cx + a, vy - a).lineTo(cx + a, vy - a + l);
  v.moveTo(cx + a, vy + a - l).lineTo(cx + a, vy + a).lineTo(cx + a - l, vy + a);
  v.moveTo(cx - a + l, vy + a).lineTo(cx - a, vy + a).lineTo(cx - a, vy + a - l);
  canvas.drawPath(v.build(), line);
  // A card.
  const my = top + NOTCH_HEIGHT / 2;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 6, my - 5, 12, 10), 2, 2), line);
  canvas.drawLine(cx - 6, my - 1.5, cx + 6, my - 1.5, line);
  // Exit: a door with an arrow.
  const ey = top + NOTCH_HEIGHT - 30;
  const door = Skia.PathBuilder.Make()
    .moveTo(cx + 1, ey - 6)
    .lineTo(cx + 6, ey - 6)
    .lineTo(cx + 6, ey + 6)
    .lineTo(cx + 1, ey + 6)
    .moveTo(cx - 6, ey)
    .lineTo(cx + 2, ey)
    .moveTo(cx - 1, ey - 3)
    .lineTo(cx + 2, ey)
    .lineTo(cx - 1, ey + 3)
    .build();
  canvas.drawPath(door, line);
}

/** The white bar at the bottom of the card: menu, EXPLORE, the zoom slider and its reading. */
function drawBar(canvas: SkCanvas, room: Stage, zoom: number, label: SkFont, tiny: SkFont) {
  'worklet';
  const top = barTop(room);
  const left = CARD_INSET + 10;
  const rect = Skia.XYWHRect(left, top, room.width - 2 * left, 56);
  canvas.drawRRect(Skia.RRectXY(rect, 18, 18), fillPaint('#fbfbf8', 1));
  canvas.drawRRect(Skia.RRectXY(rect, 18, 18), strokePaint('#dcdcd5', 0.8, 1));
  const ink = fillPaint(INK, 1);
  const line = strokePaint(INK, 1.4, 1);
  canvas.drawLine(left + 18, top + 24, left + 33, top + 24, line);
  canvas.drawLine(left + 18, top + 30, left + 33, top + 30, line);
  canvas.drawText('EXPLORE', left + 44, top + 30.5, ink, label);
  const t = track(room);
  canvas.drawLine(t.x0, t.y, t.x1, t.y, strokePaint(INK, 0.9, 1));
  const at = mix(t.x0, t.x1, zoom);
  canvas.drawLine(t.x0, t.y, at, t.y, strokePaint(INK, 2, 1));
  // End stops, each with a pixel above and below, like a survey instrument's scale.
  for (const ex of [t.x0, t.x1]) {
    canvas.drawRect(Skia.XYWHRect(ex - 1, t.y - 5, 2, 10), ink);
    canvas.drawRect(Skia.XYWHRect(ex - 1.5, t.y - 11, 3, 3), ink);
    canvas.drawRect(Skia.XYWHRect(ex - 1.5, t.y + 8, 3, 3), ink);
  }
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(at - 1.5, t.y - 10, 3, 20), 1.5, 1.5), ink);
  const reading = `${Math.round(10 + clamp01(zoom) * 80)} x`;
  const width = label.measureText(reading).width;
  canvas.drawText(reading, t.x1 - width, top + 46, ink, label);
  canvas.drawText('ZOOM', t.x0, top + 46, fillPaint(MUTED, 1), tiny);
}

/** `text` in dot-matrix figures, PIXEL pt to a pixel, its top left at (x, y). */
function drawFigures(canvas: SkCanvas, figures: Figures, text: string, x: number, y: number, paint: SkPaint) {
  'worklet';
  let left = x;
  for (let i = 0; i < text.length; i += 1) {
    const figure = figures[text[i]];
    if (!figure) continue;
    canvas.save();
    canvas.translate(left, y);
    canvas.scale(PIXEL, PIXEL);
    canvas.drawPath(figure.path, paint);
    canvas.restore();
    left += (figure.cols + 1) * PIXEL;
  }
}

/** What shows over an open photo: the typed heading, the target ring, the counted reading. */
function drawOpenDetails(
  canvas: SkCanvas,
  room: Stage,
  card: Card,
  site: SurveySite,
  alpha: number,
  shown: number,
  zoom: number,
  now: number,
  fonts: Fonts,
  figures: Figures,
  advance: number,
  still: boolean,
) {
  'worklet';
  if (alpha <= 0.002) return;
  // A light shade at the top and the bottom keeps white type legible on a bright sky or ice.
  const shadeTop = Skia.Paint();
  shadeTop.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, card.y), vec(0, card.y + 240), [Skia.Color('rgba(0,0,0,0.3)'), Skia.Color('rgba(0,0,0,0)')], null, TileMode.Clamp),
  );
  shadeTop.setAlphaf(alpha);
  canvas.drawRect(Skia.XYWHRect(card.x, card.y, card.w, 240), shadeTop);
  const low = barTop(room) - 230;
  const shadeLow = Skia.Paint();
  shadeLow.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, low), vec(0, card.y + card.h), [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,0.34)')], null, TileMode.Clamp),
  );
  shadeLow.setAlphaf(alpha);
  canvas.drawRect(Skia.XYWHRect(card.x, low, card.w, card.y + card.h - low), shadeLow);
  const ink = fillPaint(site.ink, alpha);

  // The heading types itself a character at a time behind a block cursor.
  let total = 0;
  for (const line of site.heading) total += line.length;
  const typed = still ? total : Math.max(0, Math.min(total, Math.floor((shown - TYPE_DELAY) / TYPE_STEP)));
  const x = card.x + 22;
  const lead = 29;
  const first = Math.max(room.top, 50) + 76;
  let left = typed;
  let cursorLine = 0;
  let cursorCol = 0;
  for (let i = 0; i < site.heading.length; i += 1) {
    const line = site.heading[i];
    const n = Math.min(line.length, left);
    if (n > 0) canvas.drawText(line.slice(0, n), x, first + i * lead, ink, fonts.heading);
    cursorLine = i;
    cursorCol = n;
    if (n < line.length) break;
    left -= n;
  }
  const done = typed >= total;
  const blink = done ? Math.floor(now * CURSOR_HZ * 2) % 2 === 0 : true;
  if (shown > TYPE_DELAY - 0.2 && blink) {
    canvas.drawRect(Skia.XYWHRect(x + cursorCol * advance + 2, first + cursorLine * lead - 19, advance * 0.72, 22), ink);
  }

  // A target ring and its readout, pinned to the photo: it moves with the slider's zoom.
  const ring = clamp01((shown - 1.0) / 0.6);
  if (ring > 0) {
    const ccx = card.x + card.w / 2;
    const ccy = card.y + card.h / 2;
    const scale = 1 + ZOOM_PHOTO * zoom;
    const rx = ccx + (card.x + card.w * site.ring[0] - ccx) * scale;
    const ry = ccy + (card.y + card.h * site.ring[1] - ccy) * scale;
    const grow = still ? 1 : EASE_OUT(ring);
    const line = strokePaint(site.ink, 1.2, alpha * ring);
    canvas.drawCircle(rx, ry, 12 + 8 * grow, line);
    canvas.save();
    canvas.translate(rx, ry);
    canvas.rotate(still ? 0 : now * 14, 0, 0);
    for (let k = 0; k < 4; k += 1) {
      const a = (k * Math.PI) / 2;
      canvas.drawLine(Math.cos(a) * 25 * grow, Math.sin(a) * 25 * grow, Math.cos(a) * 31 * grow, Math.sin(a) * 31 * grow, line);
    }
    canvas.restore();
    canvas.drawCircle(rx, ry, 1.8, fillPaint(site.ink, alpha * ring));
    const tagWidth = fonts.caption.measureText(site.tag).width;
    canvas.drawText(site.tag, rx - tagWidth / 2, ry + 52, fillPaint(site.ink, alpha * ring), fonts.caption);
  }

  // The reading counts up in big dot-matrix figures, with its caption below.
  if (shown > COUNT_DELAY - 0.3) {
    const count = still ? 1 : EASE_OUT(clamp01((shown - COUNT_DELAY) / COUNT_TIME));
    const value = Math.round(site.value * count);
    const fade = clamp01((shown - COUNT_DELAY + 0.3) / 0.3);
    const top = barTop(room) - 124;
    drawFigures(canvas, figures, `+${value}`, x, top, fillPaint(site.ink, alpha * fade));
    const dots = '.'.repeat(Math.floor(now * 2.5) % 4);
    for (let i = 0; i < site.caption.length; i += 1) {
      const text = i === site.caption.length - 1 ? `${site.caption[i]}${dots}` : site.caption[i];
      canvas.drawText(text, x, top + 7 * PIXEL + 22 + i * 13, fillPaint(site.ink, alpha * fade), fonts.caption);
    }
  }
  const hint = 'DRAG DOWN TO CLOSE';
  const hintWidth = fonts.label.measureText(hint).width;
  canvas.drawText(hint, card.x + card.w - 22 - hintWidth, barTop(room) - 16, fillPaint(site.ink, alpha * 0.85), fonts.label);
}

/**
 * A full-screen field-survey page: white, edge to edge, holding a column of round photo bubbles, one per site, each
 * with its name beside it. The selected site is a big rounded square; drag along the column to move
 * the selection and the bubbles morph between circle and square as it passes. Tap the selected site
 * to grow it into the whole card, where a heading types itself, a target ring locks on and a big
 * pixel reading counts up; the slider zooms into the photo, and dragging down puts it back.
 */
export function MorphBubbleColumn({
  sites = SITES,
  avatar = AVATAR,
  greeting = 'HI MARA',
  date = ['THURSDAY', '15', 'OCT'],
  clock = '9:PM',
  autoPlay = false,
  onOpen,
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = sites.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  // Sites whose sharp, full-card photo has been asked for.
  const [wanted, setWanted] = useState<number[]>(() => (auto && count > AUTO_SITE ? [AUTO_SITE] : []));

  const fonts = useMemo<Fonts>(
    () => ({
      label: matchFont({ fontFamily: MONO, fontSize: 9, fontWeight: 'bold' }),
      tiny: matchFont({ fontFamily: MONO, fontSize: 8, fontWeight: 'bold' }),
      heading: matchFont({ fontFamily: MONO, fontSize: 25, fontWeight: 'bold' }),
      caption: matchFont({ fontFamily: MONO, fontSize: 10, fontWeight: 'bold' }),
    }),
    [],
  );
  // One character cell of the monospace heading: the glyph's advance (measureText gives its padded ink
  // bounds, which drift the cursor off the cell a little more with every character).
  const headingAdvance = useMemo(() => fonts.heading.getGlyphWidths(fonts.heading.getGlyphIDs('M'))[0] ?? 15, [fonts]);
  const figures = useMemo(() => makeFigures(), []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  // The static parts, built once per size: the card's outline, its dot grid and the pixel clock.
  const statics = useMemo(() => {
    const card = { x: CARD_INSET, y: CARD_INSET, w: Math.max(0, width - 2 * CARD_INSET), h: Math.max(0, height - 2 * CARD_INSET) };
    const grid = Skia.PathBuilder.Make();
    for (let gy = GRID_STEP / 2; gy < height; gy += GRID_STEP) {
      for (let gx = GRID_STEP / 2; gx < width; gx += GRID_STEP) grid.addRect(Skia.XYWHRect(gx - 0.6, gy - 0.6, 1.2, 1.2));
    }
    return {
      card: cardPath(card),
      grid: grid.build(),
      clock: pixelBlockPath(pixelGlyphs(clock), CARD_INSET + 22, Math.max(insets.top, 50) + 50, 3.2, 0.7),
    };
  }, [width, height, clock, insets.top]);

  const stage = useSharedValue<Stage>({ width: 0, height: 0, top: 0, bottom: 0 });
  const boxes = useSharedValue<Box[]>([]);
  // The selection: 0 (top site) … count − 1, its velocity and target; true while a finger set it.
  const focus = useSharedValue(0);
  const focusVelocity = useSharedValue(0);
  const focusTarget = useSharedValue(0);
  const focusFrom = useSharedValue(0);
  const byHand = useSharedValue(false);
  // 0 in the column … 1 the whole card, its velocity and target; the site that opens.
  const open = useSharedValue(0);
  const openVelocity = useSharedValue(0);
  const openTarget = useSharedValue(0);
  const openSite = useSharedValue(-1);
  const openFrom = useSharedValue(0);
  // Seconds since the open photo was asked to open.
  const shownFor = useSharedValue(0);
  // What a drag moves: 0 nothing, 1 the open photo, 2 the slider, 3 the selection.
  const dragMode = useSharedValue(0);
  const slider = useSharedValue(SLIDER_REST);
  const sliderVelocity = useSharedValue(0);
  const sliderTarget = useSharedValue(SLIDER_REST);
  const clockT = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const elapsed = useSharedValue(0);
  const thumbs = useSharedValue<(Loaded | null)[]>(sites.map(() => null));
  const sharps = useSharedValue<(Loaded | null)[]>(sites.map(() => null));
  const face = useSharedValue<Loaded | null>(null);

  useEffect(() => {
    looping.set(auto);
    clockT.set(0);
    hold.set(0);
  }, [auto, clockT, hold, looping]);

  useEffect(() => {
    stage.set({ width, height, top: insets.top, bottom: insets.bottom });
  }, [width, height, insets.top, insets.bottom, stage]);

  // Thumbnails for the column, and the avatar; each fades in as it arrives.
  useEffect(() => {
    let alive = true;
    thumbs.set(sites.map(() => null));
    sharps.set(sites.map(() => null));
    sites.forEach((site, i) => {
      Skia.Data.fromURI(unsplash(site.id, THUMB_SIZE))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          thumbs.modify((list) => {
            'worklet';
            const next = list.slice();
            next[i] = { image, at: elapsed.get() };
            return next;
          });
        })
        .catch(() => {});
    });
    Skia.Data.fromURI(unsplash(avatar, 'w=120&h=120'))
      .then((data) => Skia.Image.MakeImageFromEncoded(data))
      .then((image) => {
        if (alive && image) face.set({ image, at: elapsed.get() });
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [sites, avatar, thumbs, sharps, face, elapsed]);

  // Sharp copies for the full card, fetched once per site when it first opens (the loop's site up
  // front); the thumbnail stands in until each arrives.
  useEffect(() => {
    let alive = true;
    wanted.forEach((i) => {
      const site = sites[i];
      if (!site) return;
      Skia.Data.fromURI(unsplash(site.id, SHARP_SIZE))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          sharps.modify((list) => {
            'worklet';
            if (list[i]) return list;
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
  }, [wanted, sites, sharps, elapsed]);

  const impact = (style: Haptics.ImpactFeedbackStyle) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
  };
  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  // Called from worklets, so the haptics module is only touched on the JS side.
  const closedByHand = () => impact(Haptics.ImpactFeedbackStyle.Soft);
  const opened = (index: number, user: boolean) => {
    setWanted((list) => (list.includes(index) ? list : [...list, index]));
    if (!user) return;
    impact(Haptics.ImpactFeedbackStyle.Light);
    onOpen?.(index);
  };

  /** Sends the selection to `index` on the calm spring. */
  const select = (index: number) => {
    'worklet';
    focusTarget.set(Math.min(count - 1, Math.max(0, index)));
    byHand.set(false);
    if (reducedMotion) {
      focus.set(focusTarget.get());
      focusVelocity.set(0);
    }
  };

  const openFocused = (user: boolean) => {
    'worklet';
    const index = Math.round(focusTarget.get());
    if (index < 0 || index >= count) return;
    openSite.set(index);
    openTarget.set(1);
    shownFor.set(0);
    scheduleOnRN(opened, index, user);
  };

  const close = (velocity: number) => {
    'worklet';
    openTarget.set(0);
    openVelocity.set(velocity);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    elapsed.set(elapsed.get() + dt);
    const isOpen = openSite.get() >= 0;

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        // The loop takes over again from its resting state: closed, the top site selected.
        clockT.set(0);
        if (isOpen) close(0);
        sliderTarget.set(SLIDER_REST);
        select(0);
      }
    } else if (looping.get()) {
      const before = clockT.get();
      const t = (before + dt) % LOOP;
      clockT.set(t);
      for (let k = 0; k < STEPS.length; k += 1) {
        if (passed(before, t, STEPS[k][0])) select(STEPS[k][1]);
      }
      if (passed(before, t, OPEN_AT) && !isOpen && count > AUTO_SITE) {
        select(AUTO_SITE);
        openFocused(false);
      }
      if (passed(before, t, ZOOM_IN_AT)) sliderTarget.set(ZOOM_IN_TO);
      if (passed(before, t, ZOOM_OUT_AT)) sliderTarget.set(SLIDER_REST);
      if (passed(before, t, CLOSE_AT) && isOpen) close(0);
    }

    if (dragMode.get() !== 3) {
      const hand = byHand.get();
      const step = springStep(
        focus.get(),
        focusVelocity.get(),
        focusTarget.get(),
        dt,
        hand ? HAND_STIFFNESS : CALM_STIFFNESS,
        hand ? HAND_DAMPING : CALM_DAMPING,
      );
      focus.set(Number.isFinite(step[0]) ? step[0] : focusTarget.get());
      focusVelocity.set(Number.isFinite(step[1]) ? step[1] : 0);
    }

    if (dragMode.get() !== 1) {
      if (reducedMotion) {
        // Without spatial motion the photo only fades in and out.
        const target = openTarget.get();
        open.set(open.get() + Math.sign(target - open.get()) * Math.min(Math.abs(target - open.get()), dt / 0.25));
        openVelocity.set(0);
      } else {
        const step = springStep(open.get(), openVelocity.get(), openTarget.get(), dt, OPEN_STIFFNESS, OPEN_DAMPING);
        open.set(step[0]);
        openVelocity.set(step[1]);
      }
      if (openTarget.get() === 0 && open.get() < 0.002 && Math.abs(openVelocity.get()) < 0.05) {
        open.set(0);
        openVelocity.set(0);
        openSite.set(-1);
      }
    }
    if (openSite.get() >= 0) shownFor.set(shownFor.get() + dt);

    if (dragMode.get() !== 2) {
      const step = springStep(slider.get(), sliderVelocity.get(), sliderTarget.get(), dt, SLIDER_STIFFNESS, SLIDER_DAMPING);
      slider.set(step[0]);
      sliderVelocity.set(step[1]);
    }

    boxes.set(columnBoxes(count, focus.get(), stage.get(), slider.get(), elapsed.get(), reducedMotion));
  });

  // A tick each time a dragged selection passes a site, or the slider passes a step.
  useAnimatedReaction(
    () => Math.round(Math.min(count - 1, Math.max(0, focus.get()))),
    (now, before) => {
      if (before !== null && now !== before && dragMode.get() === 3) scheduleOnRN(tick);
    },
  );
  useAnimatedReaction(
    () => Math.round(slider.get() * SLIDER_STEPS),
    (now, before) => {
      if (before !== null && now !== before && dragMode.get() === 2) scheduleOnRN(tick);
    },
  );

  const touched = (holdFor: number) => {
    'worklet';
    hold.set(holdFor);
  };

  const setSliderFrom = (x: number) => {
    'worklet';
    const t = track(stage.get());
    const value = clamp01((x - t.x0) / Math.max(1, t.x1 - t.x0));
    slider.set(value);
    sliderTarget.set(value);
    sliderVelocity.set(0);
  };

  const onSlider = (x: number, y: number) => {
    'worklet';
    const t = track(stage.get());
    return x > t.x0 - 18 && x < t.x1 + 18 && y > t.top && y < t.bottom;
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    .onStart((event) => {
      touched(openSite.get() >= 0 ? OPEN_HOLD : USER_HOLD);
      if (onSlider(event.x - event.translationX, event.y - event.translationY)) {
        dragMode.set(2);
        setSliderFrom(event.x);
        return;
      }
      if (openSite.get() >= 0) {
        if (openTarget.get() === 1) {
          dragMode.set(1);
          openFrom.set(open.get());
        } else {
          dragMode.set(0);
        }
        return;
      }
      // Anywhere on the column screen, a drag moves the selection with the finger.
      dragMode.set(3);
      focusFrom.set(focus.get());
      focusVelocity.set(0);
    })
    .onUpdate((event) => {
      const mode = dragMode.get();
      if (mode === 2) {
        setSliderFrom(event.x);
      } else if (mode === 1) {
        const distance = stage.get().height * DISMISS_SHARE;
        const p = openFrom.get() - event.translationY / distance;
        open.set(p > 1 ? 1 + (p - 1) * RUBBER : Math.max(0, p));
      } else if (mode === 3) {
        const raw = focusFrom.get() + event.translationY / FOCUS_DRAG;
        const max = count - 1;
        // Past either end the selection gives way less and less.
        const over = raw < 0 ? raw : raw > max ? raw - max : 0;
        const give = (FOCUS_RUBBER * over) / (FOCUS_RUBBER + Math.abs(over));
        focus.set(raw < 0 ? give : raw > max ? max + give : raw);
      }
    })
    .onEnd((event) => {
      const mode = dragMode.get();
      if (mode === 1) {
        const distance = stage.get().height * DISMISS_SHARE;
        // The finger's speed carries into the spring, in opens per second.
        const velocity = -event.velocityY / distance;
        if (event.velocityY > DISMISS_SPEED || (open.get() < CLOSE_BELOW && event.velocityY > -DISMISS_SPEED)) {
          close(velocity);
          scheduleOnRN(closedByHand);
        } else {
          openTarget.set(1);
          openVelocity.set(velocity);
        }
      } else if (mode === 3) {
        // The finger's speed, in sites per second, is handed to the spring; a flick carries on.
        const velocity = event.velocityY / FOCUS_DRAG;
        const aim = Math.round(focus.get() + velocity * FOCUS_THROW);
        focusTarget.set(Math.min(count - 1, Math.max(0, aim)));
        focusVelocity.set(reducedMotion ? 0 : velocity);
        byHand.set(true);
        if (reducedMotion) focus.set(focusTarget.get());
      }
    })
    .onFinalize(() => {
      // Whatever the drag held is let go, even if the pan was cancelled before onEnd: the selection
      // settles on the nearest site.
      if (dragMode.get() === 3) {
        focusTarget.set(Math.min(count - 1, Math.max(0, Math.round(focusTarget.get()))));
        byHand.set(true);
      }
      dragMode.set(0);
    });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success) return;
      if (onSlider(event.x, event.y)) {
        touched(openSite.get() >= 0 ? OPEN_HOLD : USER_HOLD);
        const t = track(stage.get());
        sliderTarget.set(clamp01((event.x - t.x0) / Math.max(1, t.x1 - t.x0)));
        scheduleOnRN(tick);
        return;
      }
      if (openSite.get() >= 0) return;
      const list = boxes.get();
      for (let s = 0; s < list.length; s += 1) {
        const b = list[s];
        // A little slop around the small bubbles keeps them easy to hit.
        const pad = Math.max(0, (44 - Math.min(b.w, b.h)) / 2);
        if (event.x > b.x - pad && event.x < b.x + b.w + pad && event.y > b.y - pad && event.y < b.y + b.h + pad) {
          if (s === Math.round(focusTarget.get()) && Math.abs(focus.get() - s) < 0.35) {
            touched(OPEN_HOLD);
            openFocused(true);
          } else {
            touched(USER_HOLD);
            select(s);
            scheduleOnRN(tick);
          }
          return;
        }
      }
    });

  const picture = useDerivedValue(() => {
    const room = stage.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, room.width), Math.max(1, room.height)));
    if (room.width <= 0) {
      canvas.drawRect(Skia.XYWHRect(0, 0, 1, 1), fillPaint(PAGE, 1));
      return recorder.finishRecordingAsPicture();
    }
    const card = cardOf(room);
    const now = elapsed.get();
    const o = open.get();
    const which = openSite.get();
    const column = boxes.get();
    const images = thumbs.get();
    const zoom = slider.get();

    canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), fillPaint(PAGE, 1));
    canvas.drawPath(statics.card, fillPaint(CARD, 1));
    canvas.save();
    canvas.clipPath(statics.card, ClipOp.Intersect, true);
    canvas.drawPath(statics.grid, fillPaint(GRID_DOT, 1));

    // The header and the site labels fade as a photo opens over them.
    const chrome = clamp01(1 - o * 1.6);
    if (chrome > 0) {
      const ink = fillPaint(INK, chrome);
      for (let i = 0; i < date.length; i += 1) canvas.drawText(date[i], card.x + 30, Math.max(22, room.top - 30) + i * 11, ink, fonts.tiny);
      canvas.drawPath(statics.clock, ink);
      for (let s = 0; s < column.length; s += 1) {
        const enter = reducedMotion ? 1 : clamp01((now - ENTER_DELAY - s * ENTER_STAGGER - 0.15) / 0.35);
        drawSiteLabel(canvas, sites[s], s, count, column[s], card, fonts, chrome * enter);
      }
    }

    for (let s = 0; s < column.length; s += 1) {
      if (s === which) continue;
      const box = column[s];
      // The rest sink back a little and fade while one opens.
      const sink = reducedMotion ? 1 : 1 - SINK * clamp01(o);
      const sunk: Box = {
        ...box,
        x: box.x + (box.w * (1 - sink)) / 2,
        y: box.y + (box.h * (1 - sink)) / 2,
        w: box.w * sink,
        h: box.h * sink,
      };
      const appear = reducedMotion ? clamp01(now / 0.3) : clamp01((box.scale - DOT) / 0.3);
      drawBubble(canvas, sunk, sites[s], images[s], now, appear * clamp01(1 - o));
    }

    if (which >= 0 && column[which]) {
      const from = column[which];
      const site = sites[which];
      const full = reducedMotion ? 1 : clamp01(o);
      // The bubble grows into the card: its box, corners and squircle shoulder all morph to the card's
      // own rounded rectangle, and the notch bites into it as it arrives.
      const w = mix(from.w, card.w, full);
      const h = mix(from.h, card.h, full);
      const half = Math.max(1, Math.min(w, h) / 2);
      const radius = mix((from.r * Math.min(from.w, from.h)) / 2, CARD_RADIUS, full);
      const box: Box = {
        x: mix(from.x, card.x, full),
        y: mix(from.y, card.y, full),
        w,
        h,
        r: Math.min(1, radius / half),
        n: mix(from.n, 2, full),
        scale: 1,
        big: 1,
      };
      const zoomed = 1 + ZOOM_PHOTO * zoom * full;
      const shape = shapePath(box.x, box.y, box.w, box.h, box.r, box.n);
      canvas.save();
      canvas.clipPath(shape, ClipOp.Intersect, true);
      if (reducedMotion) {
        canvas.saveLayer(fillPaint('#000000', clamp01(o)));
      }
      canvas.drawRect(Skia.XYWHRect(box.x, box.y, box.w, box.h), fillPaint(site.tint, 1));
      const thumb = images[which];
      if (thumb) drawCover(canvas, thumb.image, box.x, box.y, box.w, box.h, zoomed, clamp01((now - thumb.at) / IMAGE_FADE));
      const crisp = sharps.get()[which];
      if (crisp) drawCover(canvas, crisp.image, box.x, box.y, box.w, box.h, zoomed, clamp01((now - crisp.at) / IMAGE_FADE));
      drawOpenDetails(canvas, room, card, site, clamp01((o - 0.72) / 0.28), shownFor.get(), zoom, now, fonts, figures, headingAdvance, reducedMotion);
      if (reducedMotion) canvas.restore();
      canvas.restore();
    }
    canvas.restore();

    drawRail(canvas, card);
    drawPill(canvas, room, face.get(), greeting, fonts.tiny, now);
    drawBar(canvas, room, zoom, fonts.label, fonts.tiny);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel="Survey sites in a column of photo bubbles. Drag to move the selection; tap the selected site to open it; drag down to close it."
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
        }}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: CARD,
    overflow: 'hidden',
  },
});
