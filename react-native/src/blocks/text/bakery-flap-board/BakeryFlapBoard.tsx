import {
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkPicture,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { centred, COLUMNS, DRUM, menuLine, timeInWords, WOOD } from './BakeryFlapBoardParts';

export type BakeItem = {
  /** The name as the board spells it: capitals, up to eleven characters. */
  board: string;
  /** The price as the board shows it, four characters. */
  price: string;
  /** The name on the item card. */
  name: string;
  /** One line about it on the card. */
  note: string;
  /** When it came out of the oven, shown on the card. */
  baked: string;
  /** Unsplash id of the item's photo. */
  photo: string;
  /** The photo's own width ÷ height, so the card can frame it without fetching it first. */
  aspect: number;
  /**
   * Where the subject sits in the photo, as shares of its width and height: the card centres the
   * frame on it as far as the photo allows. Defaults to the middle.
   */
  focus?: [number, number];
  /** How much closer than a plain cover the card frames the photo (1 = cover). */
  zoom?: number;
};

type Props = {
  /** One list of items per menu line; tapping a line shows the next item of its list. */
  menu?: BakeItem[][];
  /** Photos in the strip across the top. */
  strip?: string[];
  /** The two lines that stand in for the time now and then. */
  notice?: [string, string];
  /** The bakery's name over the strip. */
  name?: string;
  /** Changes lines on a schedule, opens one card and puts everything back, on a loop. */
  autoPlay?: boolean;
  /** Called with a menu item when its card opens. */
  onOpen?: (item: BakeItem) => void;
};

const unsplash = (id: string, width: number) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;

const MENU: BakeItem[][] = [
  [
    {
      board: 'SOURDOUGH',
      price: '6.40',
      name: 'Sourdough',
      note: 'Three-day ferment, a dark blistered crust and an open, glossy crumb.',
      baked: '06:40',
      photo: 'photo-1549413468-cd78edb7e75c',
      aspect: 0.667,
      focus: [0.5, 0.42],
    },
    {
      board: 'SEEDED RYE',
      price: '5.80',
      name: 'Seeded Rye',
      note: 'Dense dark rye with sunflower, flax and a spoon of treacle.',
      baked: '07:05',
      photo: 'photo-1509440159596-0249088772ff',
      aspect: 1.5,
      focus: [0.55, 0.5],
    },
    {
      board: 'SPELT BOULE',
      price: '7.20',
      name: 'Spelt Boule',
      note: 'Wholegrain spelt, shaped by hand and baked on the stone.',
      baked: '06:55',
      photo: 'photo-1567042661848-7161ce446f85',
      aspect: 0.8,
      focus: [0.5, 0.48],
    },
  ],
  [
    {
      board: 'BAGUETTE',
      price: '3.20',
      name: 'Baguette',
      note: 'Long-proved, with a crackling crust. Best eaten before noon.',
      baked: '07:10',
      photo: 'photo-1559811814-e2c57b5e69df',
      aspect: 0.667,
      focus: [0.5, 0.4],
    },
    {
      board: 'MILK BREAD',
      price: '4.60',
      name: 'Milk Bread',
      note: 'A soft, pillowy loaf in a paper bag, sliced to order.',
      baked: '06:30',
      photo: 'photo-1622808516114-02a5749cd965',
      aspect: 0.667,
      focus: [0.5, 0.38],
    },
  ],
  [
    {
      board: 'CROISSANT',
      price: '3.40',
      name: 'Croissant',
      note: 'Twenty-seven layers of cultured butter, laminated overnight.',
      baked: '06:15',
      photo: 'photo-1546237769-6f84ec1a512a',
      aspect: 1,
      focus: [0.5, 0.42],
    },
    {
      board: 'OAT COOKIE',
      price: '2.10',
      name: 'Oat Cookie',
      note: 'Brown butter, flaked salt and a chewy middle.',
      baked: '08:00',
      photo: 'photo-1523294587484-bae6cc870010',
      aspect: 1.72,
      focus: [0.8, 0.42],
      zoom: 1.7,
    },
  ],
  [
    {
      board: 'HOUSE TEA',
      price: '3.00',
      name: 'House Tea',
      note: 'A pot of black tea with bergamot, poured at the counter.',
      baked: 'ALL DAY',
      photo: 'photo-1638303322580-5e6aacd59c0d',
      aspect: 0.75,
      focus: [0.6, 0.58],
    },
    {
      board: 'PEAR TART',
      price: '4.90',
      name: 'Pear Tart',
      note: 'Poached pear on almond cream, glazed with honey.',
      baked: '09:20',
      photo: 'photo-1543773407-ebc74e2bc7ff',
      aspect: 1.5,
      focus: [0.68, 0.6],
      zoom: 1.35,
    },
  ],
];

const STRIP = [
  'photo-1608198093002-ad4e005484ec',
  'photo-1587241321921-91a834d6d191',
  'photo-1579697096985-41fe1430e5df',
  'photo-1711672284661-bd70e38f31b2',
  'photo-1568254183919-78a4f43a2877',
];

// Colours: walnut case, charcoal flaps, cream letters, amber prices, brass fittings.
const BACKGROUND = '#1a0f09';
const FLAP_TOP = '#2c2723';
const FLAP_BOTTOM = '#24201c';
const SPLIT = '#0b0908';
const LETTER = '#f2e8d2';
const PRICE = '#f3b85e';
const BRASS = '#c9a25e';
const BRASS_DARK = '#6e4f24';
const CREAM = '#f4ead6';
const COCOA = '#2b1d12';

const CONDENSED = process.env.EXPO_OS === 'ios' ? 'Avenir Next Condensed' : 'sans-serif-condensed';
const SERIF = process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif';

// The board: two lines for the time and four for the menu, COLUMNS flaps each. A flap is as tall as
// FLAP_ASPECT × its width; CASE is the margin of the brass-edged case, PAD the room inside it.
const FLAP_ASPECT = 1.74;
const FLAP_GAP = 2;
const ROW_GAP = 9;
const LABEL_H = 24;
const CASE = 16;
const PAD = 14;
const TIME_ROWS = 2;
const BOARD_AT = 0.32;

// A flip: each step of the drum takes STEP s; a falling half turns faster as it goes (a share
// FALL_LINEAR of its turn is linear, the rest grows with the square). Columns start COL_DELAY s apart
// plus up to COL_JITTER s, like a real board's motors. The last flap of a cell lands with a bounce of
// BOUNCE_ANGLE rad over BOUNCE s. CAMERA is the distance of the eye, in flap heights.
const STEP = 0.05;
const FALL_LINEAR = 0.35;
const COL_DELAY = 0.028;
const COL_JITTER = 0.018;
const BOUNCE = 0.17;
const BOUNCE_ANGLE = 0.3;
const CAMERA = 3.4;
// Shade on a flap turned edge-on, and on the half it covers.
const FLAP_SHADE = 0.55;
const CAST_SHADE = 0.4;

// The strip of photos drifts one set to the left every LOOP seconds.
const TILE_W = 128;
const TILE_H = 164;
const TILE_GAP = 10;

// The item card grows out of its line; its spring, and a drag down by DISMISS pt closes it.
const CARD_DELAY = 0.55;
const CARD_FADE = 0.18;
const CARD_STIFFNESS = 170;
const CARD_DAMPING = 22;
const DISMISS = 360;
const DISMISS_SPEED = 600;
const CLOSE_BELOW = 0.6;
const RUBBER = 0.2;
const CARD_OVERHANG = 4;
// The card's photo is CARD_PHOTO × its width tall; the text under it takes CARD_BODY pt.
const CARD_PHOTO = 0.7;
const CARD_BODY = 164;

// autoPlay (seconds): lines change one after another, the time gives way to a notice and comes
// back, one line is "tapped" and its card opens and closes, and by LOOP every line is back.
const LOOP = 18;
type Cue = { at: number; row: number; item?: number; notice?: boolean; card?: boolean };
const SCRIPT: Cue[] = [
  { at: 0.6, row: 2, item: 1 },
  { at: 2.0, row: 5, item: 1 },
  { at: 3.4, row: 0, notice: true },
  { at: 5.0, row: 4, item: 1, card: true },
  { at: 8.8, row: -1, card: false },
  { at: 9.8, row: 2, item: 0 },
  { at: 11.2, row: 3, item: 1 },
  { at: 12.4, row: 0, notice: false },
  { at: 13.6, row: 5, item: 0 },
  { at: 14.4, row: 4, item: 0 },
  { at: 15.2, row: 3, item: 0 },
];
// The time the board shows while it plays by itself, so every loop is the same.
const DEMO_TIME: [number, number] = [7, 10];
// After a touch the loop waits this long; longer when a card is open, so it can be read.
const USER_HOLD = 4;
const CARD_HOLD = 7;

type Stage = { width: number; height: number; top: number; bottom: number };
type RowState = { from: string; to: string; at: number };
type Board = {
  x: number;
  y: number;
  w: number;
  h: number;
  left: number;
  pitch: number;
  cellW: number;
  cellH: number;
  rows: number[];
  labels: number[];
};
type Glyph = { x: number; w: number };

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

function stripBottom(room: Stage) {
  'worklet';
  return room.top + 40 + TILE_H;
}

/** Where the board and each of its lines sit for this screen. */
function boardLayout(room: Stage, rowCount: number): Board {
  'worklet';
  const x = CASE;
  const w = room.width - CASE * 2;
  const left = x + PAD;
  const pitch = (w - PAD * 2) / COLUMNS;
  const cellW = pitch - FLAP_GAP;
  const cellH = Math.round(cellW * FLAP_ASPECT);
  const h = PAD * 2 + LABEL_H * 2 + rowCount * (cellH + ROW_GAP) - ROW_GAP + 6;
  const regionTop = stripBottom(room) + 18;
  const regionBottom = room.height - room.bottom - 44;
  // The board sits a little above the middle of the room under the strip.
  const y = Math.max(regionTop, regionTop + (regionBottom - regionTop - h) * BOARD_AT);
  const rows: number[] = [];
  const labels: number[] = [];
  let cursor = y + PAD;
  for (let r = 0; r < rowCount; r += 1) {
    if (r === 0 || r === TIME_ROWS) {
      if (r === TIME_ROWS) cursor += 6;
      labels.push(cursor + 15);
      cursor += LABEL_H;
    }
    rows.push(cursor);
    cursor += cellH + ROW_GAP;
  }
  return { x, y, w, h, left, pitch, cellW, cellH, rows, labels };
}

/**
 * The open card: CARD_OVERHANG pt wider than the case on each side, and centred on the screen
 * between the safe areas (the scrim hides the board, so the screen is what it is seen against). On a
 * short screen it shrinks to fit, keeping its photo's proportions.
 */
function cardRect(room: Stage) {
  'worklet';
  const x = CASE - CARD_OVERHANG;
  const top = room.top + 12;
  const bottom = room.height - room.bottom - 12;
  const space = Math.max(1, bottom - top);
  const w = Math.max(1, Math.min(room.width - x * 2, (space - CARD_BODY) / CARD_PHOTO));
  const h = w * CARD_PHOTO + CARD_BODY;
  return { x: (room.width - w) / 2, y: top + Math.max(0, space - h) / 2, w, h };
}

/** The width to fetch an item's photo at: enough for the card on a large phone, at its zoom. */
function photoWidth(item: BakeItem) {
  return Math.round(Math.min(1800, 1000 * Math.max(1, item.zoom ?? 1)));
}

/**
 * Where the photo sits behind the card's photo frame (w × h): scaled to cover the frame (times its
 * zoom), and moved so its subject is as near the frame's middle as the photo's edges allow.
 */
function framePhoto(item: BakeItem, w: number, h: number) {
  const aspect = item.aspect > 0 ? item.aspect : w / Math.max(1, h);
  const zoom = Math.max(1, item.zoom ?? 1);
  const cover = aspect > w / Math.max(1, h) ? { width: h * aspect, height: h } : { width: w, height: w / aspect };
  const width = cover.width * zoom;
  const height = cover.height * zoom;
  const [fx, fy] = item.focus ?? [0.5, 0.5];
  const left = Math.min(0, Math.max(w - width, w / 2 - fx * width));
  const top = Math.min(0, Math.max(h - height, h / 2 - fy * height));
  return { left, top, width, height };
}

/** A column's own small delay, fixed per column, so a line never starts as one block. */
function jitter(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 4.1) * 43758.5453;
  return (s - Math.floor(s)) * COL_JITTER;
}

function drumIndex(ch: string) {
  'worklet';
  const k = DRUM.indexOf(ch);
  return k < 0 ? 0 : k;
}

/**
 * The state of cell `i` of a line `now`: the character showing (`cur`), the one coming (`next`)
 * and how far the falling half has turned (0…1), or −1 once it has settled; `bounce` runs 0…1 just
 * after the last flap lands.
 */
function cellAt(row: RowState, i: number, now: number) {
  'worklet';
  const from = drumIndex(row.from[i] ?? ' ');
  const to = drumIndex(row.to[i] ?? ' ');
  const steps = (to - from + DRUM.length) % DRUM.length;
  if (steps === 0) return { cur: to, next: to, p: -1, bounce: -1 };
  const t = now - row.at - i * COL_DELAY - jitter(i);
  if (t < 0) return { cur: from, next: from, p: -1, bounce: -1 };
  const k = Math.floor(t / STEP);
  if (k >= steps) {
    const after = (t - steps * STEP) / BOUNCE;
    return { cur: to, next: to, p: -1, bounce: after < 1 ? after : -1 };
  }
  return { cur: (from + k) % DRUM.length, next: (from + k + 1) % DRUM.length, p: (t - k * STEP) / STEP, bounce: -1 };
}

/** The characters a line shows `now`, mid-flip or not. */
function showing(row: RowState, now: number) {
  'worklet';
  let text = '';
  for (let i = 0; i < COLUMNS; i += 1) text += DRUM[cellAt(row, i, now).cur];
  return text;
}

/**
 * A matrix that turns a half flap about the hinge line `axis` by `angle` (0 flat, ±π/2 edge on),
 * seen from CAMERA flap heights away: the edge swinging towards the viewer grows.
 */
function hingeMatrix(cx: number, axis: number, angle: number, camera: number) {
  'worklet';
  const k = Math.sin(angle) / camera;
  const c = Math.cos(angle);
  return Skia.Matrix([1, cx * k, -cx * k * axis, 0, c + axis * k, axis * (1 - c) - k * axis * axis, 0, k, 1 - k * axis]);
}

function fill(color: string, alpha = 1) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  paint.setAlphaf(alpha);
  return paint;
}

/** One half of a flap (top or bottom) with its half of character `ch`, shaded by `shade`. */
function drawHalf(
  canvas: SkCanvas,
  x: number,
  y: number,
  w: number,
  h: number,
  top: boolean,
  ch: number,
  color: string,
  shade: number,
  font: SkFont,
  glyphs: Glyph[],
  baseline: number,
) {
  'worklet';
  const mid = y + h / 2;
  const half = top ? Skia.XYWHRect(x, y, w, h / 2) : Skia.XYWHRect(x, mid, w, h / 2);
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 3, 3), ClipOp.Intersect, true);
  canvas.clipRect(half, ClipOp.Intersect, true);
  canvas.drawRect(half, fill(top ? FLAP_TOP : FLAP_BOTTOM));
  const glyph = glyphs[ch];
  if (DRUM[ch] !== ' ') canvas.drawText(DRUM[ch], x + (w - glyph.w) / 2 - glyph.x, y + baseline, fill(color), font);
  if (shade > 0.003) canvas.drawRect(half, fill('#000000', shade));
  canvas.restore();
}

/** A whole cell: settled, flipping (two static halves and one turning), or bouncing. */
function drawCell(
  canvas: SkCanvas,
  x: number,
  y: number,
  w: number,
  h: number,
  cell: { cur: number; next: number; p: number; bounce: number },
  color: string,
  font: SkFont,
  glyphs: Glyph[],
  baseline: number,
  reducedMotion: boolean,
) {
  'worklet';
  const cx = x + w / 2;
  const axis = y + h / 2;
  if (cell.p < 0 && cell.bounce < 0) {
    const glyph = glyphs[cell.cur];
    if (DRUM[cell.cur] !== ' ') canvas.drawText(DRUM[cell.cur], x + (w - glyph.w) / 2 - glyph.x, y + baseline, fill(color), font);
    return;
  }
  const camera = CAMERA * h;
  if (cell.p < 0) {
    // The last flap has landed and lifts once off its stop.
    const q = cell.bounce;
    const lift = BOUNCE_ANGLE * Math.sin(Math.PI * q) * (1 - q);
    drawHalf(canvas, x, y, w, h, true, cell.cur, color, 0, font, glyphs, baseline);
    drawHalf(canvas, x, y, w, h, false, cell.cur, color, CAST_SHADE * (lift / BOUNCE_ANGLE) * 0.6, font, glyphs, baseline);
    canvas.save();
    canvas.concat(hingeMatrix(cx, axis, -lift, camera));
    drawHalf(canvas, x, y, w, h, false, cell.cur, color, lift * 0.5, font, glyphs, baseline);
    canvas.restore();
    return;
  }
  if (reducedMotion) {
    // Without the turn the letters just change, half by half.
    drawHalf(canvas, x, y, w, h, true, cell.p < 0.5 ? cell.cur : cell.next, color, 0, font, glyphs, baseline);
    drawHalf(canvas, x, y, w, h, false, cell.p < 0.5 ? cell.cur : cell.next, color, 0, font, glyphs, baseline);
    return;
  }
  const p = cell.p;
  const angle = Math.PI * (FALL_LINEAR * p + (1 - FALL_LINEAR) * p * p);
  const firstHalf = angle < Math.PI / 2;
  // Behind the falling flap: the next letter's top, darkened while the flap still hangs over it.
  drawHalf(canvas, x, y, w, h, true, cell.next, color, firstHalf ? CAST_SHADE * Math.sin(angle) : 0, font, glyphs, baseline);
  // Below: the current letter's bottom, shaded as the flap comes down onto it.
  drawHalf(canvas, x, y, w, h, false, cell.cur, color, firstHalf ? 0 : CAST_SHADE * (angle / (Math.PI / 2) - 1), font, glyphs, baseline);
  canvas.save();
  if (firstHalf) {
    canvas.concat(hingeMatrix(cx, axis, angle, camera));
    drawHalf(canvas, x, y, w, h, true, cell.cur, color, FLAP_SHADE * (angle / (Math.PI / 2)), font, glyphs, baseline);
  } else {
    canvas.concat(hingeMatrix(cx, axis, angle - Math.PI, camera));
    drawHalf(canvas, x, y, w, h, false, cell.next, color, FLAP_SHADE * (2 - angle / (Math.PI / 2)), font, glyphs, baseline);
  }
  canvas.restore();
}

/** Letters `text` spaced by `track` pt, from `x`. */
function spaced(canvas: SkCanvas, text: string, x: number, y: number, track: number, font: SkFont, color: string) {
  let at = x;
  for (const ch of text) {
    if (ch === ' ') {
      at += font.getSize() * 0.32 + track;
      continue;
    }
    canvas.drawText(ch, at, y, fill(color), font);
    at += font.measureText(ch).width + track;
  }
  return at - x - track;
}

/** The case, the flap backs and the section labels: everything that never moves. */
function recordCase(room: Stage, board: Board, rowCount: number, labelFont: SkFont, labels: string[]): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, room.width, room.height));
  const wood = Skia.Paint();
  wood.setShader(WOOD.makeShader([room.width, room.height]));
  canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), wood);

  const caseRect = Skia.XYWHRect(board.x, board.y, board.w, board.h);
  const shadow = fill('#000000', 0.45);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(board.x, board.y + 8, board.w, board.h), 14, 14), shadow);
  canvas.drawRRect(Skia.RRectXY(caseRect, 14, 14), fill('#120d0a'));
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(2.5);
  rim.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(board.x, board.y),
      vec(board.x + board.w, board.y + board.h),
      ['#7a5a2c', '#e8c787', '#946f35', '#dcb66f', '#5f4420'].map((c) => Skia.Color(c)),
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(Skia.RRectXY(caseRect, 14, 14), rim);
  // Brass screws in the corners.
  for (const [sx, sy] of [
    [board.x + 9, board.y + 9],
    [board.x + board.w - 9, board.y + 9],
    [board.x + 9, board.y + board.h - 9],
    [board.x + board.w - 9, board.y + board.h - 9],
  ]) {
    canvas.drawCircle(sx, sy, 3.2, fill(BRASS_DARK));
    canvas.drawCircle(sx - 0.6, sy - 0.6, 2.3, fill(BRASS));
    canvas.drawLine(sx - 1.8, sy + 1.2, sx + 1.2, sy - 1.8, fill(BRASS_DARK));
  }

  for (let k = 0; k < board.labels.length; k += 1) {
    const y = board.labels[k];
    const width = spaced(canvas, labels[k], board.left, y, 2.6, labelFont, BRASS);
    const line = fill(BRASS, 0.35);
    canvas.drawRect(Skia.XYWHRect(board.left + width + 10, y - 4, board.x + board.w - PAD - board.left - width - 10, 0.8), line);
  }

  for (let r = 0; r < rowCount; r += 1) {
    for (let i = 0; i < COLUMNS; i += 1) {
      const x = board.left + i * board.pitch + FLAP_GAP / 2;
      const y = board.rows[r];
      const rect = Skia.XYWHRect(x, y, board.cellW, board.cellH);
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 1.2, board.cellW, board.cellH), 3, 3), fill('#000000', 0.6));
      canvas.save();
      canvas.clipRRect(Skia.RRectXY(rect, 3, 3), ClipOp.Intersect, true);
      canvas.drawRect(Skia.XYWHRect(x, y, board.cellW, board.cellH / 2), fill(FLAP_TOP));
      canvas.drawRect(Skia.XYWHRect(x, y + board.cellH / 2, board.cellW, board.cellH / 2), fill(FLAP_BOTTOM));
      canvas.restore();
    }
  }
  return recorder.finishRecordingAsPicture();
}

/** The split across every flap and its two brass hinge pins, drawn over the letters. */
function recordHinges(room: Stage, board: Board, rowCount: number): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, room.width, room.height));
  for (let r = 0; r < rowCount; r += 1) {
    for (let i = 0; i < COLUMNS; i += 1) {
      const x = board.left + i * board.pitch + FLAP_GAP / 2;
      const mid = board.rows[r] + board.cellH / 2;
      canvas.drawRect(Skia.XYWHRect(x, mid - 0.7, board.cellW, 1.4), fill(SPLIT));
      canvas.drawRect(Skia.XYWHRect(x + 1, board.rows[r] + 0.6, board.cellW - 2, 0.6), fill('#ffffff', 0.05));
      canvas.drawCircle(x + 1.6, mid, 1.2, fill(BRASS_DARK));
      canvas.drawCircle(x + board.cellW - 1.6, mid, 1.2, fill(BRASS_DARK));
    }
  }
  return recorder.finishRecordingAsPicture();
}

/**
 * A split-flap menu board in a small bakery: a walnut case with brass edges under a drifting strip
 * of bakery photos. Two lines tell the time in words, four list what is out of the oven; when a line
 * changes, every flap falls through the alphabet to its new letter with the real two-half flip, a
 * column at a time, and lands with a little bounce. Tapping a line flips it to its next item and
 * grows that line into a cream card with the item's photo and price.
 */
export function BakeryFlapBoard({
  menu = MENU,
  strip = STRIP,
  notice = ['SECOND BAKE', 'AT HALF NINE'],
  name = 'ROSIER BAKEHOUSE',
  autoPlay = false,
  onOpen,
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const rowCount = TIME_ROWS + menu.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [card, setCard] = useState<BakeItem>(menu[0][0]);

  const fonts = useMemo(
    () => ({
      flap: matchFont({ fontFamily: CONDENSED, fontSize: 25, fontWeight: '700' }),
      label: matchFont({ fontFamily: CONDENSED, fontSize: 10.5, fontWeight: '600' }),
    }),
    [],
  );
  // Each drum character's ink box, to centre it on its flap, and the baseline that centres capitals.
  const glyphs = useMemo<Glyph[]>(
    () =>
      DRUM.split('').map((ch) => {
        const box = fonts.flap.measureText(ch === ' ' ? 'H' : ch);
        return { x: box.x, w: box.width };
      }),
    [fonts],
  );
  const capHeight = useMemo(() => -fonts.flap.measureText('H').y, [fonts]);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const room = useMemo<Stage>(
    () => ({ width, height, top: insets.top, bottom: insets.bottom }),
    [width, height, insets.top, insets.bottom],
  );
  const board = useMemo(() => boardLayout(room, rowCount), [room, rowCount]);
  const baseline = board.cellH / 2 + capHeight / 2;
  const pictures = useMemo(
    () =>
      width > 0
        ? {
            under: recordCase(room, board, rowCount, fonts.label, ['THE TIME', "TODAY'S BAKE"]),
            over: recordHinges(room, board, rowCount),
          }
        : null,
    [room, board, rowCount, fonts, width],
  );

  // The lines the board wants to show, which the flaps then chase.
  const lineOf = (row: number, item: number) => {
    'worklet';
    const entry = menu[row][item];
    return menuLine(entry.board, entry.price);
  };
  const liveTime = () => {
    const now = new Date();
    return timeInWords(now.getHours(), now.getMinutes()).map(centred);
  };
  const demoTime = timeInWords(DEMO_TIME[0], DEMO_TIME[1]).map(centred);
  const noticeLines = notice.map(centred);

  const stage = useSharedValue<Stage>({ width: 0, height: 0, top: 0, bottom: 0 });
  const items = useSharedValue<number[]>(menu.map(() => 0));
  const showNotice = useSharedValue(false);
  const time = useSharedValue<string[]>(auto ? demoTime : liveTime());
  const rows = useSharedValue<RowState[]>(
    Array.from({ length: rowCount }, (_, r) => {
      // On first appearance every line flips in from blank.
      const to = r < TIME_ROWS ? (auto ? demoTime : liveTime())[r] : menuLine(menu[r - TIME_ROWS][0].board, menu[r - TIME_ROWS][0].price);
      return { from: ' '.repeat(COLUMNS), to, at: 0.35 + r * 0.16 };
    }),
  );
  const elapsed = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  // The item card: 0 folded into its line … 1 open, its velocity and target, the line it came from,
  // and a line waiting to open once its flip is under way (with the time it opens).
  const cardOpen = useSharedValue(0);
  const cardVelocity = useSharedValue(0);
  const cardTarget = useSharedValue(0);
  const cardRow = useSharedValue(-1);
  const cardDragging = useSharedValue(false);
  const cardFrom = useSharedValue(0);
  const pending = useSharedValue<{ row: number; at: number; user: boolean } | null>(null);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  useEffect(() => {
    stage.set(room);
  }, [room, stage]);

  // The time in words, kept current while the board is not playing by itself.
  useEffect(() => {
    if (auto) {
      time.set(timeInWords(DEMO_TIME[0], DEMO_TIME[1]).map(centred));
      return;
    }
    const update = () => {
      const now = new Date();
      time.set(timeInWords(now.getHours(), now.getMinutes()).map(centred));
    };
    update();
    const id = setInterval(update, 15000);
    return () => clearInterval(id);
  }, [auto, time]);

  // The card photos are fetched up front, so a card never opens on an empty frame.
  useEffect(() => {
    Image.prefetch(menu.flat().map((item) => unsplash(item.photo, photoWidth(item)))).catch(() => {});
  }, [menu]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const soft = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };
  const showCard = (row: number, item: number, user: boolean) => {
    const entry = menu[row - TIME_ROWS][item];
    setCard(entry);
    if (user) onOpen?.(entry);
  };

  /** Moves menu line `row` to `item` (or the next one), and asks for its card if `card`. */
  const advance = (row: number, item: number, card: boolean, user: boolean) => {
    'worklet';
    if (row < TIME_ROWS) {
      showNotice.set(!showNotice.get());
      return;
    }
    const list = menu[row - TIME_ROWS];
    const next = item < 0 ? (items.get()[row - TIME_ROWS] + 1) % list.length : item;
    items.modify((value) => {
      'worklet';
      value[row - TIME_ROWS] = next;
      return value;
    }, true);
    if (card) pending.set({ row, at: elapsed.get() + CARD_DELAY, user });
  };

  const closeCard = (velocity: number) => {
    'worklet';
    cardTarget.set(0);
    cardVelocity.set(velocity);
    pending.set(null);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const now = elapsed.get() + dt;
    elapsed.set(now);

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        // Back to the loop's resting board before it starts over.
        clock.set(0);
        closeCard(0);
        showNotice.set(false);
        items.modify((value) => {
          'worklet';
          for (let k = 0; k < value.length; k += 1) value[k] = 0;
          return value;
        }, true);
      }
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      for (let k = 0; k < SCRIPT.length; k += 1) {
        const cue = SCRIPT[k];
        if (!passed(before, t, cue.at)) continue;
        if (cue.notice !== undefined) showNotice.set(cue.notice);
        else if (cue.card === false) closeCard(0);
        else if (cue.item !== undefined) advance(cue.row, cue.item, cue.card === true, false);
      }
    }

    // Each line chases the text it should show; a change starts from whatever it shows now.
    const wanted: string[] = [];
    for (let r = 0; r < rowCount; r += 1) {
      if (r < TIME_ROWS) wanted.push(showNotice.get() ? noticeLines[r] : time.get()[r]);
      else wanted.push(lineOf(r - TIME_ROWS, items.get()[r - TIME_ROWS]));
    }
    const current = rows.get();
    let changed = false;
    for (let r = 0; r < rowCount; r += 1) if (current[r].to !== wanted[r]) changed = true;
    if (changed) {
      rows.modify((list) => {
        'worklet';
        for (let r = 0; r < rowCount; r += 1) {
          if (list[r].to === wanted[r]) continue;
          list[r] = { from: showing(list[r], now), to: wanted[r], at: now };
        }
        return list;
      }, true);
    }

    const ask = pending.get();
    if (ask && now >= ask.at) {
      pending.set(null);
      cardRow.set(ask.row);
      cardTarget.set(1);
      scheduleOnRN(showCard, ask.row, items.get()[ask.row - TIME_ROWS], ask.user);
    }

    if (!cardDragging.get()) {
      if (reducedMotion) {
        const target = cardTarget.get();
        const o = cardOpen.get();
        cardOpen.set(o + Math.sign(target - o) * Math.min(Math.abs(target - o), dt / 0.22));
      } else {
        const v = cardVelocity.get() + (CARD_STIFFNESS * (cardTarget.get() - cardOpen.get()) - CARD_DAMPING * cardVelocity.get()) * dt;
        cardVelocity.set(v);
        cardOpen.set(cardOpen.get() + v * dt);
      }
      if (cardTarget.get() === 0 && cardOpen.get() < 0.002 && Math.abs(cardVelocity.get()) < 0.05) {
        cardOpen.set(0);
        cardVelocity.set(0);
        cardRow.set(-1);
      }
    }
  });

  const touched = (holdFor: number) => {
    'worklet';
    hold.set(holdFor);
  };

  const tap = Gesture.Tap()
    .maxDuration(450)
    .onEnd((event, success) => {
      if (!success) return;
      if (cardRow.get() >= 0 || pending.get()) {
        touched(USER_HOLD);
        closeCard(0);
        return;
      }
      const layout = boardLayout(stage.get(), rowCount);
      for (let r = 0; r < rowCount; r += 1) {
        const y = layout.rows[r];
        if (event.y < y - ROW_GAP / 2 || event.y > y + layout.cellH + ROW_GAP / 2) continue;
        if (event.x < layout.x || event.x > layout.x + layout.w) continue;
        touched(r < TIME_ROWS ? USER_HOLD : CARD_HOLD);
        advance(r, -1, r >= TIME_ROWS, true);
        scheduleOnRN(tick);
        return;
      }
    });

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart(() => {
      if (cardRow.get() < 0 || cardTarget.get() !== 1) return;
      touched(CARD_HOLD);
      cardDragging.set(true);
      cardFrom.set(cardOpen.get());
    })
    .onUpdate((event) => {
      if (!cardDragging.get()) return;
      const p = cardFrom.get() - event.translationY / DISMISS;
      cardOpen.set(p > 1 ? 1 + (p - 1) * RUBBER : Math.max(0, p));
    })
    .onEnd((event) => {
      if (!cardDragging.get()) return;
      cardDragging.set(false);
      const velocity = -event.velocityY / DISMISS;
      if (event.velocityY > DISMISS_SPEED || (cardOpen.get() < CLOSE_BELOW && event.velocityY > -DISMISS_SPEED)) {
        closeCard(velocity);
        scheduleOnRN(soft);
      } else {
        cardTarget.set(1);
        cardVelocity.set(velocity);
      }
    })
    .onFinalize(() => {
      cardDragging.set(false);
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, room.width, room.height));
    if (!pictures) return recorder.finishRecordingAsPicture();
    const now = elapsed.get();
    const list = rows.get();
    canvas.drawPicture(pictures.under);
    for (let r = 0; r < rowCount; r += 1) {
      const y = board.rows[r];
      for (let i = 0; i < COLUMNS; i += 1) {
        const x = board.left + i * board.pitch + FLAP_GAP / 2;
        // Prices are set in amber, the rest in cream.
        const color = r >= TIME_ROWS && i >= COLUMNS - 4 ? PRICE : LETTER;
        drawCell(canvas, x, y, board.cellW, board.cellH, cellAt(list[r], i, now), color, fonts.flap, glyphs, baseline, reducedMotion);
      }
    }
    canvas.drawPicture(pictures.over);
    return recorder.finishRecordingAsPicture();
  });

  // The strip drifts one set of photos to the left per loop.
  const setWidth = strip.length * (TILE_W + TILE_GAP);
  const stripStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: reducedMotion ? 0 : -(((elapsed.get() % LOOP) / LOOP) * setWidth) }],
  }));

  const scrimStyle = useAnimatedStyle(() => ({ opacity: 0.62 * clamp01(cardOpen.get()) }));
  const cardStyle = useAnimatedStyle(() => {
    const row = cardRow.get();
    const s = stage.get();
    const layout = boardLayout(s, rowCount);
    const to = cardRect(s);
    if (row < 0 || s.width <= 0) return { opacity: 0, left: to.x, top: to.y, width: to.w, height: to.h };
    const o = Math.max(0, cardOpen.get());
    const t = reducedMotion ? 1 : o;
    const from = { x: layout.left - 4, y: layout.rows[row] - 4, w: layout.w - PAD * 2 + 8, h: layout.cellH + 8 };
    // The card lifts out of its line as a cream strip and grows; it fades in over the first
    // CARD_FADE of the way so it never sits over the line as a flat block.
    return {
      opacity: reducedMotion ? clamp01(o) : clamp01(o / CARD_FADE),
      left: mix(from.x, to.x, t),
      top: mix(from.y, to.y, t),
      width: mix(from.w, to.w, t),
      height: mix(from.h, to.h, t),
      borderRadius: mix(5, 24, clamp01(t)),
    };
  });
  const cardContentStyle = useAnimatedStyle(() => {
    const s = stage.get();
    const layout = boardLayout(s, rowCount);
    const to = cardRect(s);
    const row = cardRow.get();
    const o = Math.max(0, cardOpen.get());
    const t = reducedMotion || row < 0 ? 1 : o;
    const fromW = layout.w - PAD * 2 + 8;
    const fromH = layout.cellH + 8;
    const shown = clamp01((o - 0.45) / 0.4);
    // The content stays centred in the growing card, so the card reveals it rather than squeezing it.
    return {
      opacity: shown,
      transform: [
        { translateX: (mix(fromW, to.w, t) - to.w) / 2 },
        { translateY: (mix(fromH, to.h, t) - to.h) / 2 + (reducedMotion ? 0 : (1 - shown) * 14) },
      ],
    };
  });

  const cardBox = cardRect(room);
  const photoH = cardBox.w * CARD_PHOTO;

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel="Split-flap menu board. Tap a line to flip it to the next item and open its card."
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
        }}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>

        <View pointerEvents="none" style={[styles.header, { top: insets.top + 6 }]}>
          <Text style={styles.name}>{name}</Text>
          <Text style={styles.open}>OPEN 7 — 4</Text>
        </View>
        <View pointerEvents="none" style={[styles.stripClip, { top: insets.top + 32 }]}>
          <Animated.View style={[styles.strip, stripStyle]}>
            {[...strip, ...strip, ...strip].map((id, k) => (
              <Image
                key={k}
                source={{ uri: unsplash(id, 340) }}
                style={styles.tile}
                contentFit="cover"
                transition={300}
              />
            ))}
          </Animated.View>
        </View>
        <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 18 }]}>
          Tap a line for its card
        </Text>

        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.scrim, scrimStyle]} />
        <Animated.View pointerEvents="none" style={[styles.card, cardStyle]}>
          <Animated.View style={[{ width: cardBox.w, height: cardBox.h }, cardContentStyle]}>
            <View style={{ width: cardBox.w, height: photoH, overflow: 'hidden' }}>
              <Image
                key={card.photo}
                source={{ uri: unsplash(card.photo, photoWidth(card)) }}
                style={[styles.cardPhoto, framePhoto(card, cardBox.w, photoH)]}
                contentFit="cover"
                transition={250}
              />
            </View>
            <View style={styles.cardBody}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardName} numberOfLines={1}>
                  {card.name}
                </Text>
                <Text style={styles.cardPrice}>{card.price}</Text>
              </View>
              <Text style={styles.cardNote} numberOfLines={2}>
                {card.note}
              </Text>
              <View style={styles.cardRule} />
              <View style={styles.cardMetaRow}>
                <Text style={styles.cardMeta}>{card.baked === 'ALL DAY' ? 'POURED ALL DAY' : `OUT OF THE OVEN ${card.baked}`}</Text>
                <Text style={styles.cardMeta}>DRAG DOWN TO CLOSE</Text>
              </View>
            </View>
          </Animated.View>
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
    overflow: 'hidden',
  },
  header: {
    position: 'absolute',
    left: CASE + 4,
    right: CASE + 4,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
  },
  name: {
    color: BRASS,
    fontFamily: CONDENSED,
    fontSize: 14,
    fontWeight: '700',
    letterSpacing: 3.4,
  },
  open: {
    color: BRASS,
    opacity: 0.7,
    fontFamily: CONDENSED,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 2,
  },
  stripClip: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: TILE_H,
    overflow: 'hidden',
  },
  strip: {
    flexDirection: 'row',
    gap: TILE_GAP,
    paddingLeft: CASE,
  },
  tile: {
    width: TILE_W,
    height: TILE_H,
    borderRadius: 10,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(230, 190, 120, 0.35)',
    backgroundColor: '#2c1c12',
  },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: BRASS,
    opacity: 0.75,
    fontFamily: CONDENSED,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.6,
  },
  scrim: {
    backgroundColor: '#000000',
  },
  card: {
    position: 'absolute',
    backgroundColor: CREAM,
    overflow: 'hidden',
    borderCurve: 'continuous',
    alignItems: 'flex-start',
  },
  cardPhoto: {
    position: 'absolute',
  },
  cardBody: {
    paddingHorizontal: 20,
    paddingTop: 18,
    gap: 8,
  },
  cardTitleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
  },
  cardName: {
    color: COCOA,
    fontFamily: SERIF,
    fontSize: 30,
    flexShrink: 1,
  },
  cardPrice: {
    color: '#a8722b',
    fontFamily: CONDENSED,
    fontSize: 28,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  cardNote: {
    color: COCOA,
    opacity: 0.78,
    fontFamily: SERIF,
    fontStyle: 'italic',
    fontSize: 15,
    lineHeight: 21,
  },
  cardRule: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: COCOA,
    opacity: 0.25,
    marginTop: 6,
  },
  cardMetaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  cardMeta: {
    color: COCOA,
    opacity: 0.6,
    fontFamily: CONDENSED,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.4,
  },
});
