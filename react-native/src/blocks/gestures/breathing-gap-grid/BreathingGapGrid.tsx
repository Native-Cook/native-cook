import {
  BlurStyle,
  Canvas,
  FilterMode,
  LinearGradient,
  matchFont,
  MipmapMode,
  Picture,
  Rect,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkShader,
  Text as SkiaText,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/** A photo, or a square of type set in one colour on another. */
export type GridTile =
  | {
      kind: 'photo';
      title: string;
      note: string;
      /** A copy about the size of a closed tile. */
      uri: string;
      /** A larger copy, loaded only once the tile opens. */
      full?: string;
      tint: string;
    }
  | { kind: 'type'; title: string; note: string; lines: string[]; ink: string; paper: string };

type Props = {
  /** What the columns are filled with, repeated as far as they run. */
  tiles?: GridTile[];
  /** The line that opens up between the tiles once the gap is wide. */
  caption?: string;
  /** Opens the gap, closes it, opens a tile, closes it and starts over, while the columns drift. */
  autoPlay?: boolean;
  background?: string;
  textColor?: string;
  /** Called with a tile when it is opened. */
  onOpen?: (tile: GridTile) => void;
};

const unsplash = (id: string, side: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${side}&h=${side}&q=80`;
// Eighty photos stay light enough in memory at tile size; the one that opens is fetched again, sharp.
const photo = (id: string) => ({ uri: unsplash(id, 400), full: unsplash(id, 1080) });

const TILES: GridTile[] = [
  { kind: 'photo', title: 'Ninety', note: 'Portrait, black and white', ...photo('photo-1685041552051-13de9e56228c'), tint: '#2b2b2b' },
  { kind: 'photo', title: 'Stem', note: 'Still life, white', ...photo('photo-1471086569966-db3eebc25a59'), tint: '#e8e6e1' },
  { kind: 'photo', title: 'Veil', note: 'Portrait, red', ...photo('photo-1727341557146-4abab94d0812'), tint: '#8e1a14' },
  { kind: 'photo', title: 'Plaster', note: 'Sculpture', ...photo('photo-1604854574894-1be73ca43cb8'), tint: '#d9d6d0' },
  { kind: 'photo', title: 'Blue Hour', note: 'Ceramics', ...photo('photo-1660721671073-e139688fa3cf'), tint: '#5d7389' },
  { kind: 'photo', title: 'Runner', note: 'Object, blue', ...photo('photo-1618677831708-0e7fda3148b4'), tint: '#3c5aa8' },
  { kind: 'photo', title: 'Dune', note: 'Landscape', ...photo('photo-1785270101102-fe2b694a024f'), tint: '#c48a52' },
  { kind: 'type', title: 'A', note: 'Type, cobalt', lines: ['A'], ink: '#ffffff', paper: '#1f3fd1' },
  { kind: 'photo', title: 'Profile', note: 'Portrait, black and white', ...photo('photo-1657412235453-8adb25a45cd0'), tint: '#1e1e1e' },
  { kind: 'photo', title: 'Lavender', note: 'Dried flowers', ...photo('photo-1772441835051-4d32e651661d'), tint: '#6a5a8a' },
  { kind: 'photo', title: 'Mask', note: 'Sculpture, dark', ...photo('photo-1561910377-c2a014ffd1cd'), tint: '#141414' },
  { kind: 'photo', title: 'Pink', note: 'Object, magenta', ...photo('photo-1628558422453-576457df4b0d'), tint: '#c2337f' },
  { kind: 'photo', title: 'Cobalt', note: 'Portrait, blue', ...photo('photo-1629511565591-a1d494ad6c58'), tint: '#22356b' },
  { kind: 'type', title: 'Room', note: 'Type, three times', lines: ['ROOM', 'ROOM', 'ROOM'], ink: '#ffffff', paper: '#0b0b0b' },
  { kind: 'photo', title: 'Pair', note: 'Still life, colour', ...photo('photo-1655388446578-741cb6f55d14'), tint: '#d8c6c3' },
  { kind: 'photo', title: 'Director', note: 'Object, chair', ...photo('photo-1758491037354-26d601d3cadd'), tint: '#e3e0da' },
  { kind: 'photo', title: 'Gaze', note: 'Portrait, editorial', ...photo('photo-1506863530036-1efeddceb993'), tint: '#343434' },
  { kind: 'photo', title: 'Grey Room', note: 'Portrait, studio', ...photo('photo-1768247695726-022586dea3a1'), tint: '#0e0e0e' },
  { kind: 'photo', title: 'Scarlet', note: 'Portrait, red', ...photo('photo-1687360440648-ec9708d52086'), tint: '#aa564a' },
  { kind: 'photo', title: 'Azure', note: 'Portrait, blue', ...photo('photo-1675790991952-4006c7a67cad'), tint: '#1d1315' },
  { kind: 'photo', title: 'Marble', note: 'Sculpture, bust', ...photo('photo-1637666505754-7416ebd70cbf'), tint: '#c5c7cb' },
  { kind: 'photo', title: 'Cast', note: 'Sculpture, plaster', ...photo('photo-1447758902204-48010b87c24d'), tint: '#8b8e8f' },
  { kind: 'photo', title: 'Vessel', note: 'Ceramics', ...photo('photo-1631125915902-d8abe9225ff2'), tint: '#a09989' },
  { kind: 'photo', title: 'Pause', note: 'Still life', ...photo('photo-1604935067269-27c7e8b36618'), tint: '#b6a899' },
  { kind: 'photo', title: 'Speaker', note: 'Object', ...photo('photo-1604782206219-3b9576575203'), tint: '#ac8f77' },
  { kind: 'photo', title: 'Pampas', note: 'Dried flowers', ...photo('photo-1772510758813-a45fc1570a2c'), tint: '#b58c6d' },
  { kind: 'photo', title: 'Midnight', note: 'Flowers', ...photo('photo-1783680599080-abe4d5634c3a'), tint: '#342217' },
  { kind: 'photo', title: 'Cut Out', note: 'Fashion', ...photo('photo-1603189343302-e603f7add05a'), tint: '#727272' },
  { kind: 'photo', title: 'Atelier', note: 'Fashion, studio', ...photo('photo-1717765698239-88731a968d20'), tint: '#5f5a54' },
  { kind: 'photo', title: 'Noir', note: 'Fashion, black and white', ...photo('photo-1592833578500-1082e18665a3'), tint: '#c1c1c1' },
  { kind: 'photo', title: 'Plain Oak', note: 'Object, chair', ...photo('photo-1640938776314-4d303f8a1380'), tint: '#ccb048' },
  { kind: 'photo', title: 'Linen', note: 'Portrait, editorial', ...photo('photo-1762291629616-3e2c044c79a0'), tint: '#25201f' },
  { kind: 'photo', title: 'Low Key', note: 'Portrait, studio', ...photo('photo-1557053910-d9eadeed1c58'), tint: '#b16934' },
  { kind: 'type', title: 'N', note: 'Type, red', lines: ['N'], ink: '#e5322d', paper: '#f2efe8' },
  { kind: 'photo', title: 'Crimson', note: 'Portrait, red', ...photo('photo-1727341556787-7292f7515aec'), tint: '#6f0703' },
  { kind: 'photo', title: 'Marine', note: 'Portrait, blue', ...photo('photo-1752665696094-b733943393d9'), tint: '#a37a62' },
  { kind: 'photo', title: 'Pale', note: 'Sculpture, bust', ...photo('photo-1729008712499-5e6b58420789'), tint: '#303030' },
  { kind: 'photo', title: 'Chalk', note: 'Sculpture, plaster', ...photo('photo-1563301323-094e5843a962'), tint: '#948778' },
  { kind: 'photo', title: 'Glaze', note: 'Ceramics', ...photo('photo-1597696929736-6d13bed8e6a8'), tint: '#cbbfb4' },
  { kind: 'photo', title: 'Balance', note: 'Still life', ...photo('photo-1705684989920-2ae5e583c0e7'), tint: '#221e18' },
  { kind: 'photo', title: 'Arcs', note: 'Object', ...photo('photo-1783372519845-6cc782fb0f68'), tint: '#6c6b6b' },
  { kind: 'photo', title: 'Bouquet', note: 'Dried flowers', ...photo('photo-1586244897859-2cd81e1cad1f'), tint: '#e0ddd5' },
  { kind: 'type', title: 'Hi.', note: 'Type, black on white', lines: ['Hi.'], ink: '#111111', paper: '#ffffff' },
  { kind: 'photo', title: 'Meadow', note: 'Flowers', ...photo('photo-1762969217477-af1c7b85db17'), tint: '#806645' },
  { kind: 'photo', title: 'High Sky', note: 'Fashion', ...photo('photo-1574015974293-817f0ebebb74'), tint: '#4a4a4a' },
  { kind: 'photo', title: 'Contrast', note: 'Fashion, black and white', ...photo('photo-1754315052701-0b98f6ba786b'), tint: '#5f6165' },
  { kind: 'photo', title: 'Tulip', note: 'Object, chair', ...photo('photo-1659246973603-748505504837'), tint: '#ad9383' },
  { kind: 'photo', title: 'Dusk', note: 'Portrait, editorial', ...photo('photo-1765334666990-1bf2ce733864'), tint: '#403c3b' },
  { kind: 'photo', title: 'Halo', note: 'Portrait, studio', ...photo('photo-1770327960728-ad08ea1e921e'), tint: '#4b4b4b' },
  { kind: 'photo', title: 'Ember', note: 'Portrait, red', ...photo('photo-1727341557531-343cd4364501'), tint: '#653e2a' },
  { kind: 'photo', title: 'Relic', note: 'Sculpture, bust', ...photo('photo-1570877901348-07076379b061'), tint: '#544844' },
  { kind: 'photo', title: 'Gesso', note: 'Sculpture, plaster', ...photo('photo-1742495211859-d2e95a1e9354'), tint: '#563f2d' },
  { kind: 'photo', title: 'Urn', note: 'Ceramics', ...photo('photo-1631125915732-b98f8774f675'), tint: '#aca9a7' },
  { kind: 'photo', title: 'Rest', note: 'Still life', ...photo('photo-1615020977625-6c5092907e9d'), tint: '#c4c3b9' },
  { kind: 'photo', title: 'Prism', note: 'Object', ...photo('photo-1620987278429-ab178d6eb547'), tint: '#d7c7ad' },
  { kind: 'photo', title: 'Thistle', note: 'Dried flowers', ...photo('photo-1770816307374-d1ee7e423342'), tint: '#ac8d76' },
  { kind: 'photo', title: 'Orchard', note: 'Flowers', ...photo('photo-1554482091-af22387a7a0c'), tint: '#7d777a' },
  { kind: 'photo', title: 'Fringe', note: 'Fashion', ...photo('photo-1562151270-c7d22ceb586a'), tint: '#a2b6b6' },
  { kind: 'photo', title: 'Armchair', note: 'Object, chair', ...photo('photo-1722357533644-9b20e356ba5f'), tint: '#ab9685' },
  { kind: 'photo', title: 'Hush', note: 'Portrait, editorial', ...photo('photo-1674932668403-33398b81c92f'), tint: '#80746b' },
  { kind: 'photo', title: 'Matte', note: 'Portrait, studio', ...photo('photo-1535713875002-d1d0cf377fde'), tint: '#a39ea1' },
  { kind: 'photo', title: 'Bust', note: 'Sculpture, bust', ...photo('photo-1747191092806-c7da11881986'), tint: '#837c73' },
  { kind: 'photo', title: 'Moulded', note: 'Sculpture, plaster', ...photo('photo-1781707870248-e2607e82bac0'), tint: '#a5a5a5' },
  { kind: 'photo', title: 'Loop', note: 'Ceramics', ...photo('photo-1643569556871-91ec60671ed7'), tint: '#afa190' },
  { kind: 'type', title: 'Look', note: 'Type, cobalt on white', lines: ['LOOK', 'LOOK'], ink: '#1f3fd1', paper: '#ffffff' },
  { kind: 'type', title: 'O', note: 'Type, white on red', lines: ['O'], ink: '#ffffff', paper: '#e5322d' },
  { kind: 'photo', title: 'Duo', note: 'Still life', ...photo('photo-1608575474456-55113364dd66'), tint: '#959085' },
  { kind: 'photo', title: 'Plinth', note: 'Object', ...photo('photo-1621544170639-fb0189e518ba'), tint: '#d6d2d1' },
  { kind: 'photo', title: 'Harvest', note: 'Dried flowers', ...photo('photo-1604304194650-3ba3cfa752fd'), tint: '#c7c0b2' },
  { kind: 'photo', title: 'Drape', note: 'Fashion', ...photo('photo-1662532577856-e8ee8b138a8b'), tint: '#644a7a' },
  { kind: 'photo', title: 'Slats', note: 'Object, chair', ...photo('photo-1572297794744-a19043e308f8'), tint: '#cab2ac' },
  { kind: 'photo', title: 'Copper', note: 'Portrait, editorial', ...photo('photo-1606143412458-acc5f86de897'), tint: '#2c2c2a' },
  { kind: 'photo', title: 'Studio', note: 'Portrait, studio', ...photo('photo-1768247695735-e10baa73e917'), tint: '#181818' },
  { kind: 'photo', title: 'Classic', note: 'Sculpture, bust', ...photo('photo-1565170556450-bdfc81864ca1'), tint: '#1a1924' },
  { kind: 'type', title: 'Gap', note: 'Type, black on paper', lines: ['GAP'], ink: '#0b0b0b', paper: '#f2efe8' },
  { kind: 'photo', title: 'Kiln', note: 'Ceramics', ...photo('photo-1481401908818-600b7a676c0d'), tint: '#84938b' },
  { kind: 'photo', title: 'Facet', note: 'Object', ...photo('photo-1703843943664-867123b25898'), tint: '#bab6b0' },
  { kind: 'photo', title: 'Wild Oat', note: 'Dried flowers', ...photo('photo-1615851908107-5047d87d0327'), tint: '#dcd7d9' },
  { kind: 'photo', title: 'Tailor', note: 'Fashion', ...photo('photo-1779399147755-cb3c1a62c3de'), tint: '#8b9598' },
  { kind: 'photo', title: 'Navy', note: 'Object, chair', ...photo('photo-1748887522222-ae67e761fb64'), tint: '#899198' },
  { kind: 'photo', title: 'Tilt', note: 'Portrait, editorial', ...photo('photo-1774567753803-f26f0add5800'), tint: '#2d2d2d' },
  { kind: 'photo', title: 'Terracotta', note: 'Ceramics', ...photo('photo-1526198049595-f32cde2a219d'), tint: '#e1e2e3' },
  { kind: 'photo', title: 'Field', note: 'Dried flowers', ...photo('photo-1546810738-b0c7fe348c6b'), tint: '#bdc5bf' },
  { kind: 'photo', title: 'Swing', note: 'Fashion', ...photo('photo-1596993100471-c3905dafa78e'), tint: '#939278' },
  { kind: 'photo', title: 'Glance', note: 'Portrait, editorial', ...photo('photo-1772566737819-d8898e6580a4'), tint: '#222222' },
  { kind: 'photo', title: 'Quiet', note: 'Portrait, editorial', ...photo('photo-1563170446-9c3c0622d8a9'), tint: '#31241c' },
  { kind: 'photo', title: 'Still', note: 'Portrait, editorial', ...photo('photo-1612928414075-bc722ade44f1'), tint: '#878787' },
];

// The grid: columns of square cells PITCH apart, centred on the screen. Closed up there are four,
// PITCH_SHARE of the width apart so the outer two run half off the edges, and a tile fills DENSE
// of its cell. Opened out the grid zooms out to AIRY_COLUMNS whole columns, a tile fills AIRY of
// its cell, and the margins at both edges are exactly as wide as the gaps between the tiles.
const PITCH_SHARE = 0.3;
const DENSE = 0.88;
const AIRY_COLUMNS = 8;
const AIRY = 0.56;
// A pinch in by this factor goes all the way from closed to open.
const PINCH_RANGE = 2.4;
// Past either end a pinch keeps following at this share of its pull, never further than OVERSHOOT.
const RUBBER = 0.25;
const OVERSHOOT = 0.15;
// Each column drifts upward at its own speed, in cells per second, from its own phase; the eight
// values repeat across the columns.
const DRIFT = [0.22, 0.34, 0.27, 0.38, 0.3, 0.24, 0.36, 0.29];
const PHASE = [0.1, 0.62, 0.35, 0.83, 0.48, 0.2, 0.71, 0.04];
const LANES = DRIFT.length;
// The type tiles are set at TYPE_SIZE and scaled so their widest line fills TYPE_FILL of the tile.
const TYPE_SIZE = 100;
const TYPE_FILL = 0.62;
// A photo's lower half darkens towards its bottom edge by up to this alpha.
const SHADE_FROM = 0.5;
const SHADE_STRENGTH = '47';

// Arrival: the tiles grow in from SEED of their size, rippling out from the middle of the screen
// over ENTER_SPREAD s, each over ENTER_EACH s; the drift starts BURST times faster and settles to
// its own speed over BURST_TIME s. A photo fades in over IMAGE_FADE s once it has loaded.
const ENTER_DELAY = 0.08;
const ENTER_SPREAD = 0.7;
const ENTER_EACH = 0.65;
const SEED = 0.55;
const BURST = 5;
const BURST_TIME = 1.8;
const IMAGE_FADE = 0.45;
const CHROME_DELAY = 450;
const CHROME_TIME = 600;

// The caption's lane: as the gap opens, the rows within PART_RANGE of the middle part by up to
// PART pt either side, and a tile whose edge comes within CLEAR pt of the line fades out over a
// further CLEAR_SOFT pt.
const CAPTION_AT = 0.5;
const PART = 26;
const PART_RANGE = 150;
const PART_SOFT = 18;
const CLEAR = 6;
const CLEAR_SOFT = 18;
const LANE_HEIGHT = 120;
const LANE_STRENGTH = 'b3';
// The caption's letters arrive one after another once the gap is CAPTION_FROM open, each rising
// LETTER_RISE pt out of a LETTER_BLUR pt blur; rules then draw out from it to the margins.
const CAPTION_FROM = 0.35;
const LETTER_SPREAD = 0.6;
const LETTER_RISE = 12;
const LETTER_BLUR = 5;
const CAPTION_SIZE = 13;
const CAPTION_TRACKING = 3.2;
const RULE_GAP = 16;
const RULE_MARGIN = 20;

// Snapping the gap after a pinch: the pinch's speed carries it this far ahead, in seconds.
const PINCH_THROW = 0.15;
const GAP_SPRING = { duration: 520, dampingRatio: 0.86 } as const;
// A dragged wall keeps gliding, losing this share of its speed every millisecond.
const GLIDE_DECAY = 0.997;

// An open tile: its side margin, and where its centre sits as a share of the screen.
const OPEN_MARGIN = 24;
const OPEN_AT = 0.42;
const OPEN_SPRING = { duration: 520, dampingRatio: 0.88 } as const;
// Behind an open tile the wall blurs by up to WALL_BLUR and dims by WALL_DIM, and a glow in the
// tile's own colour spreads GLOW_BLUR pt below it.
const WALL_BLUR = 10;
const WALL_DIM = 0.8;
const GLOW = 0.5;
const GLOW_BLUR = 34;
const GLOW_DROP = 20;
// Dragging an open tile: it follows the finger, shrinking by up to DRAG_SHRINK over SHRINK_SHARE
// of the screen's height pulled down, and resisting at LIFT_RUBBER when pushed up. The wall comes
// back by AWAY_RELIEF as the tile is pulled AWAY_SHARE of the height away.
const DRAG_SHRINK = 0.45;
const SHRINK_SHARE = 0.6;
const LIFT_RUBBER = 0.3;
const AWAY_SHARE = 0.4;
const AWAY_RELIEF = 0.75;
const DISMISS_DISTANCE = 110;
const DISMISS_SPEED = 650;
const DRAG_SPRING = { duration: 460, dampingRatio: 0.9 } as const;
// The open tile's note and title sit DETAIL_GAP pt below it and rise CAPTION_RISE pt into place.
const DETAIL_GAP = 22;
const CAPTION_RISE = 16;
// The wall darkens towards the top and bottom edges, so the labels there stay legible.
const EDGE_FADE = 96;
const EDGE_STRENGTH = 'e6';

// autoPlay loop (seconds), with the columns always drifting: the gap opens, closes, a tile opens
// and closes again.
const LOOP = 11;
const OPEN_GAP_AT = 1.4;
const CLOSE_GAP_AT = 4.8;
const OPEN_TILE_AT = 6.6;
const CLOSE_TILE_AT = 8.8;
const GAP_TIME = 1200;
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
// After a touch the loop waits this long before it starts over.
const USER_HOLD = 3;

const NONE = -1000;
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

type Loaded = { image: SkImage; at: number };
type Layout = { width: number; height: number; pitch: number; side: number; time: number; scroll: number; part: number };

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function easeOut(p: number) {
  'worklet';
  return 1 - (1 - p) * (1 - p) * (1 - p);
}

/**
 * Which tile fills cell (c, r): a fixed shuffle. With the 87 tiles here, the nearest repeat of any
 * tile is at least eight cells away, further than the grid shows when closed up.
 */
function tileAt(c: number, r: number, n: number) {
  'worklet';
  const k = c * 3 + r * 8;
  return ((k % n) + n) % n;
}

function lane(c: number) {
  'worklet';
  return ((c % LANES) + LANES) % LANES;
}

/** The grid's spacing for a gap `spread` open (0 closed up, 1 opened out). */
function layoutOf(width: number, height: number, spread: number, time: number, scroll: number): Layout {
  'worklet';
  const g = Math.min(1 + OVERSHOOT, Math.max(-OVERSHOOT, spread));
  const dense = width * PITCH_SHARE;
  const airy = width / (AIRY_COLUMNS + 1 - AIRY);
  // Zooming evenly: the pitch changes by the same factor for every step of the gap.
  const pitch = dense * Math.pow(airy / dense, g);
  const side = pitch * (DENSE + (AIRY - DENSE) * g);
  const p = clamp01(g);
  return { width, height, pitch, side, time, scroll, part: p * p * (3 - 2 * p) };
}

/** The middle of cell (c, r): its column drifts and scrolls in cells; rows part around the caption. */
function cellCentre(c: number, r: number, l: Layout) {
  'worklet';
  const m = lane(c);
  const x = l.width / 2 + (c - 1.5) * l.pitch;
  const y = l.height / 2 + (r + PHASE[m] - l.time * DRIFT[m] + l.scroll) * l.pitch;
  const d = y - l.height * CAPTION_AT;
  const reach = Math.max(0, 1 - Math.abs(d) / PART_RANGE);
  return { x, y: y + (d / (Math.abs(d) + PART_SOFT)) * PART * l.part * reach * reach };
}

/** How many columns have their middle on screen. */
function columnsShown(width: number, pitch: number) {
  'worklet';
  return 2 * Math.floor(width / 2 / pitch + 0.5);
}

function drawTile(
  canvas: SkCanvas,
  tile: GridTile,
  loaded: Loaded | null,
  widths: number[],
  font: SkFont,
  shade: SkShader,
  x: number,
  y: number,
  side: number,
  alpha: number,
  now: number,
  sharper: Loaded | null = null,
) {
  'worklet';
  if (alpha <= 0.002 || side <= 0.5) return;
  const rect = Skia.XYWHRect(x, y, side, side);
  const paint = Skia.Paint();
  if (tile.kind === 'photo') {
    const shown = loaded ? clamp01((now - loaded.at) / IMAGE_FADE) : 0;
    if (shown < 1) {
      paint.setColor(Skia.Color(tile.tint));
      paint.setAlphaf(alpha);
      canvas.drawRect(rect, paint);
    }
    if (loaded && shown > 0) {
      // The middle square of the photo, sampled smoothly at any size.
      const w = loaded.image.width();
      const h = loaded.image.height();
      const s = Math.min(w, h);
      paint.setAlphaf(alpha * shown);
      canvas.drawImageRectOptions(
        loaded.image,
        Skia.XYWHRect((w - s) / 2, (h - s) / 2, s, s),
        rect,
        FilterMode.Linear,
        MipmapMode.Linear,
        paint,
      );
      if (sharper) {
        // The larger copy, once it arrives, fades in over the small one.
        const sw = sharper.image.width();
        const sh = sharper.image.height();
        const ss = Math.min(sw, sh);
        const sharp = Skia.Paint();
        sharp.setAlphaf(alpha * clamp01((now - sharper.at) / IMAGE_FADE));
        canvas.drawImageRectOptions(
          sharper.image,
          Skia.XYWHRect((sw - ss) / 2, (sh - ss) / 2, ss, ss),
          rect,
          FilterMode.Linear,
          MipmapMode.Linear,
          sharp,
        );
      }
      const veil = Skia.Paint();
      veil.setShader(shade);
      veil.setAlphaf(alpha * shown);
      canvas.save();
      canvas.translate(x, y);
      canvas.scale(side, side);
      canvas.drawRect(Skia.XYWHRect(0, 0, 1, 1), veil);
      canvas.restore();
    }
    return;
  }
  paint.setColor(Skia.Color(tile.paper));
  paint.setAlphaf(alpha);
  canvas.drawRect(rect, paint);
  const widest = Math.max(...widths);
  const lineHeight = TYPE_SIZE * 0.86;
  const block = lineHeight * tile.lines.length;
  const scale = Math.min((side * TYPE_FILL) / widest, (side * TYPE_FILL) / block);
  const ink = Skia.Paint();
  ink.setColor(Skia.Color(tile.ink));
  ink.setAlphaf(alpha);
  canvas.save();
  canvas.translate(x + side / 2, y + side / 2);
  canvas.scale(scale, scale);
  tile.lines.forEach((line, i) => {
    const baseline = -block / 2 + lineHeight * (i + 1) - TYPE_SIZE * 0.14;
    canvas.drawText(line, -widths[i] / 2, baseline, ink, font);
  });
  canvas.restore();
}

/** A letter's advance; Skia measures a lone space as nothing, so it is taken from between two letters. */
function advance(font: SkFont, letter: string) {
  'worklet';
  if (letter !== ' ') return font.measureText(letter).width;
  return font.measureText('a a').width - font.measureText('aa').width;
}

/** Draws text a letter at a time, `tracking` pt apart. */
function drawTracked(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, tracking: number, paint: SkPaint) {
  'worklet';
  let at = x;
  for (const letter of text) {
    canvas.drawText(letter, at, y, paint, font);
    at += advance(font, letter) + tracking;
  }
}

/**
 * A wall of square tiles in four drifting columns, photos mixed with set type. Pinching opens the
 * gap between the tiles: the wall zooms out to eight whole columns, and the rows part around the
 * middle for a line of text that arrives a letter at a time; a release settles on closed or open.
 * The wall can be dragged, and tapping a tile lifts it out over the blurred wall with its title;
 * the open tile follows the finger, and a pull down puts it back in its cell.
 */
export function BreathingGapGrid({
  tiles = TILES,
  caption = 'Room to breathe',
  autoPlay = false,
  background = '#000000',
  textColor = '#f2f1ec',
  onOpen,
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = tiles.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const fonts = useMemo(
    () => ({
      type: matchFont({ fontFamily: FAMILY, fontSize: TYPE_SIZE, fontWeight: 'bold' }),
      label: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '500' }),
      caption: matchFont({ fontFamily: FAMILY, fontSize: CAPTION_SIZE, fontWeight: '600' }),
      meta: matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: '500' }),
      title: matchFont({ fontFamily: FAMILY, fontSize: 34, fontWeight: '300' }),
    }),
    [],
  );
  // Each type tile's line widths, measured once rather than every frame.
  const typeWidths = useMemo(
    () => tiles.map((tile) => (tile.kind === 'type' ? tile.lines.map((line) => fonts.type.measureText(line).width) : [])),
    [tiles, fonts],
  );
  // The caption's letters and where each starts.
  const glyphs = useMemo(() => {
    const letters = [...caption.toUpperCase()];
    const at: number[] = [];
    let x = 0;
    letters.forEach((letter) => {
      at.push(x);
      x += advance(fonts.caption, letter) + CAPTION_TRACKING;
    });
    return { letters, at, width: x - CAPTION_TRACKING };
  }, [caption, fonts]);
  const shade = useMemo(
    () =>
      Skia.Shader.MakeLinearGradient(
        vec(0, SHADE_FROM),
        vec(0, 1),
        [Skia.Color('#00000000'), Skia.Color(`#000000${SHADE_STRENGTH}`)],
        null,
        TileMode.Clamp,
      ),
    [],
  );

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const openSide = width - OPEN_MARGIN * 2;
  const openY = height * OPEN_AT;
  const captionY = height * CAPTION_AT;

  // Seconds since the wall first appeared.
  const elapsed = useSharedValue(0);
  // The photos, each with the moment it arrived, so it can fade in.
  const loaded = useSharedValue<(Loaded | null)[]>(tiles.map(() => null));
  // The larger copy of the tile that is open, and which tile it belongs to.
  const sharp = useSharedValue<(Loaded & { index: number }) | null>(null);
  // 0 closed up … 1 opened out.
  const spread = useSharedValue(0);
  const pinchFrom = useSharedValue(0);
  const pinching = useSharedValue(false);
  // Seconds of drift so far; it slows to a stop while a tile is open.
  const time = useSharedValue(0);
  // The user's own drag of the wall, in cells, and its glide after release in cells per second.
  const scroll = useSharedValue(0);
  const glide = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const dragPitch = useSharedValue(1);
  // 0 shut, 1 open, and the cell it applies to.
  const open = useSharedValue(0);
  const openC = useSharedValue(NONE);
  const openR = useSharedValue(0);
  const isOpen = useSharedValue(false);
  // How far the open tile has been pulled from its place, in pt.
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const liftFromX = useSharedValue(0);
  const liftFromY = useSharedValue(0);
  const dragMode = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  // The labels fade in once the tiles are on their way.
  const chrome = useSharedValue(0);
  // The screen's size for the frame callback, which is registered once and cannot read `size`.
  const stage = useSharedValue({ width: 0, height: 0 });

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  // Each photo arrives on its own; until then its tile shows its tint.
  useEffect(() => {
    let alive = true;
    loaded.set(tiles.map(() => null));
    tiles.forEach((tile, i) => {
      if (tile.kind !== 'photo') return;
      Skia.Data.fromURI(tile.uri)
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
  }, [tiles, loaded, elapsed]);

  const committed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const ticked = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const opened = (index: number, user: boolean) => {
    const tile = tiles[index];
    if (tile.kind === 'photo' && tile.full && sharp.get()?.index !== index) {
      Skia.Data.fromURI(tile.full)
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (image) sharp.set({ image, at: elapsed.get(), index });
        })
        .catch(() => {});
    }
    if (!user) return;
    committed();
    onOpen?.(tiles[index]);
  };

  const setGap = (to: number, velocity: number, timed: boolean) => {
    'worklet';
    if (reducedMotion) spread.set(withTiming(to, { duration: 200 }));
    else if (timed) spread.set(withTiming(to, { duration: GAP_TIME, easing: EASE_IN_OUT }));
    else spread.set(withSpring(to, { ...GAP_SPRING, velocity }));
  };
  const settleOpen = (to: number) => {
    'worklet';
    isOpen.set(to === 1);
    open.set(reducedMotion ? withTiming(to, { duration: 200, easing: EASE_OUT }) : withSpring(to, OPEN_SPRING));
  };
  // The open tile returns to its place, carrying the finger's speed.
  const settleDrag = (vx: number, vy: number) => {
    'worklet';
    if (reducedMotion) {
      dragX.set(withTiming(0, { duration: 200, easing: EASE_OUT }));
      dragY.set(withTiming(0, { duration: 200, easing: EASE_OUT }));
      return;
    }
    dragX.set(withSpring(0, { ...DRAG_SPRING, velocity: vx }));
    dragY.set(withSpring(0, { ...DRAG_SPRING, velocity: vy }));
  };
  const openCell = (c: number, r: number, user: boolean) => {
    'worklet';
    openC.set(c);
    openR.set(r);
    dragX.set(0);
    dragY.set(0);
    settleOpen(1);
    scheduleOnRN(opened, tileAt(c, r, count), user);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const { width: stageWidth, height: stageHeight } = stage.get();
    if (stageWidth === 0) return;
    const now = elapsed.get() + dt;
    elapsed.set(now);
    if (!reducedMotion) {
      // The drift arrives fast and settles to its own pace; it slows to a stop under an open tile.
      const p = Math.min(1, now / BURST_TIME);
      const burst = 1 + (BURST - 1) * (1 - p) * (1 - p);
      time.set(time.get() + dt * burst * (1 - clamp01(open.get())));
    }
    const v = glide.get();
    if (v !== 0) {
      const pitch = layoutOf(stageWidth, stageHeight, spread.get(), 0, 0).pitch;
      scroll.set(scroll.get() + v * dt);
      const next = v * Math.pow(GLIDE_DECAY, dt * 1000);
      glide.set(Math.abs(next * pitch) < 4 ? 0 : next);
    }
    if (hold.get() > 0) {
      if (!isOpen.get()) hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
      return;
    }
    if (!looping.get()) return;
    const before = clock.get();
    const t = (before + dt) % LOOP;
    clock.set(t);
    const passed = (at: number) => (before < at && t >= at) || (t < before && (at > before || at <= t));
    if (passed(OPEN_GAP_AT)) setGap(1, 0, true);
    if (passed(CLOSE_GAP_AT)) setGap(0, 0, true);
    if (passed(OPEN_TILE_AT)) {
      // The photo in the second column nearest where an open tile sits.
      const l = layoutOf(stageWidth, stageHeight, spread.get(), time.get(), scroll.get());
      const near = Math.round((stageHeight * OPEN_AT - cellCentre(1, 0, l).y) / l.pitch);
      let row = near;
      for (const step of [0, 1, -1, 2, -2]) {
        if (tiles[tileAt(1, near + step, count)].kind === 'photo') {
          row = near + step;
          break;
        }
      }
      openCell(1, row, false);
    }
    if (passed(CLOSE_TILE_AT) && isOpen.get()) settleOpen(0);
  });

  // A selection tick each time a pinch brings another pair of columns into view.
  useAnimatedReaction(
    () => (width > 0 ? columnsShown(width, layoutOf(width, height, spread.get(), 0, 0).pitch) : 0),
    (shown, before) => {
      if (before !== null && before !== 0 && shown !== before && pinching.get()) scheduleOnRN(ticked);
    },
  );

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  const pinch = Gesture.Pinch()
    .onStart(() => {
      if (isOpen.get()) return;
      touched();
      pinching.set(true);
      pinchFrom.set(spread.get());
    })
    .onUpdate((event) => {
      if (isOpen.get()) return;
      const next = pinchFrom.get() - Math.log(Math.max(event.scale, 0.01)) / Math.log(PINCH_RANGE);
      // Past either end it follows at a quarter of the pull.
      spread.set(next > 1 ? 1 + (next - 1) * RUBBER : next < 0 ? next * RUBBER : next);
    })
    .onEnd((event) => {
      if (isOpen.get()) return;
      // The pinch's speed in gap per second.
      const speed = -event.velocity / (Math.log(PINCH_RANGE) * Math.max(event.scale, 0.01));
      const ahead = spread.get() + speed * PINCH_THROW;
      setGap(ahead > 0.5 ? 1 : 0, speed, false);
      scheduleOnRN(committed);
    })
    .onFinalize(() => {
      pinching.set(false);
    });

  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(6)
    .onStart(() => {
      touched();
      glide.set(0);
      if (isOpen.get() || open.get() > 0.5) {
        // Caught on its way back, the tile is held where it is.
        dragMode.set(1);
        if (!isOpen.get()) settleOpen(1);
        liftFromX.set(dragX.get());
        liftFromY.set(dragY.get());
        return;
      }
      dragMode.set(0);
      dragFrom.set(scroll.get());
      dragPitch.set(layoutOf(width, height, spread.get(), 0, 0).pitch);
    })
    .onUpdate((event) => {
      if (dragMode.get() === 1) {
        dragX.set(liftFromX.get() + event.translationX);
        const y = liftFromY.get() + event.translationY;
        dragY.set(y > 0 ? y : y * LIFT_RUBBER);
        return;
      }
      scroll.set(dragFrom.get() + event.translationY / dragPitch.get());
    })
    .onEnd((event) => {
      if (dragMode.get() === 1) {
        const shut =
          event.velocityY > DISMISS_SPEED || (dragY.get() > DISMISS_DISTANCE && event.velocityY > -DISMISS_SPEED / 3);
        if (shut) {
          settleOpen(0);
          scheduleOnRN(committed);
        }
        settleDrag(event.velocityX, event.velocityY);
        return;
      }
      if (!reducedMotion) glide.set(event.velocityY / dragPitch.get());
    });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success || !size) return;
      touched();
      glide.set(0);
      if (isOpen.get()) {
        settleOpen(0);
        settleDrag(0, 0);
        return;
      }
      // The cell under the finger: the column by x, then whichever nearby row is closest.
      const l = layoutOf(width, height, spread.get(), time.get(), scroll.get());
      const c = Math.round((event.x - width / 2) / l.pitch + 1.5);
      const guess = Math.round((event.y - cellCentre(c, 0, l).y) / l.pitch);
      let best = guess;
      let off = Infinity;
      for (let r = guess - 1; r <= guess + 1; r += 1) {
        const d = Math.abs(cellCentre(c, r, l).y - event.y);
        if (d < off) {
          off = d;
          best = r;
        }
      }
      if (off > l.side / 2 + (l.pitch - l.side) / 2) return;
      openCell(c, best, true);
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (!size) return recorder.finishRecordingAsPicture();
    const now = elapsed.get();
    const images = loaded.get();
    const l = layoutOf(width, height, spread.get(), time.get(), scroll.get());
    const o = open.get();
    const lifted = clamp01(o);
    const dx = dragX.get();
    const dy = dragY.get();
    // How far the open tile has been pulled away, 0 … 1.
    const away = clamp01((Math.max(0, dy) + Math.abs(dx) * 0.4) / (height * AWAY_SHARE));
    const wall = lifted * (1 - AWAY_RELIEF * away);
    const oc = openC.get();
    const or = openR.get();
    const reach = Math.hypot(width / 2, height / 2);

    // The wall, blurred behind an open tile.
    const layered = wall > 0.002;
    if (layered) {
      const layer = Skia.Paint();
      if (!reducedMotion) layer.setImageFilter(Skia.ImageFilter.MakeBlur(WALL_BLUR * wall, WALL_BLUR * wall, TileMode.Clamp, null));
      canvas.saveLayer(layer);
    }
    const span = width / 2 + l.side;
    const firstC = Math.floor(1.5 - span / l.pitch);
    const lastC = Math.ceil(1.5 + span / l.pitch);
    for (let c = firstC; c <= lastC; c += 1) {
      const m = lane(c);
      const shift = PHASE[m] - l.time * DRIFT[m] + l.scroll;
      const first = Math.floor((-height / 2 - l.side - PART) / l.pitch - shift);
      const last = Math.ceil((height / 2 + l.side + PART) / l.pitch - shift);
      for (let r = first; r <= last; r += 1) {
        if (o > 0.001 && c === oc && r === or) continue;
        const { x, y } = cellCentre(c, r, l);
        // Arrival, rippling out from the middle by where the cell started.
        const start = Math.hypot(x - width / 2, (r + PHASE[m]) * l.pitch) / reach;
        const grown = reducedMotion
          ? clamp01(now / 0.3)
          : easeOut(clamp01((now - ENTER_DELAY - start * ENTER_SPREAD) / ENTER_EACH));
        if (grown <= 0) continue;
        // Tiles give way to the caption's lane as the gap opens.
        const clearance = Math.abs(y - captionY) - l.side / 2;
        const clear = clamp01((clearance - CLEAR) / CLEAR_SOFT);
        const alpha = grown * (1 - l.part * (1 - clear));
        const side = l.side * (SEED + (1 - SEED) * grown);
        const index = tileAt(c, r, count);
        drawTile(canvas, tiles[index], images[index], typeWidths[index], fonts.type, shade, x - side / 2, y - side / 2, side, alpha, now);
      }
    }

    // The caption, in a darkened lane, a letter at a time.
    const reveal = clamp01((l.part - CAPTION_FROM) / (1 - CAPTION_FROM)) * (1 - lifted);
    if (reveal > 0.001) {
      const band = Skia.Paint();
      band.setShader(
        Skia.Shader.MakeLinearGradient(
          vec(0, captionY - LANE_HEIGHT / 2),
          vec(0, captionY + LANE_HEIGHT / 2),
          [Skia.Color(`${background}00`), Skia.Color(`${background}${LANE_STRENGTH}`), Skia.Color(`${background}00`)],
          null,
          TileMode.Clamp,
        ),
      );
      band.setAlphaf(reveal);
      canvas.drawRect(Skia.XYWHRect(0, captionY - LANE_HEIGHT / 2, width, LANE_HEIGHT), band);
      const left = width / 2 - glyphs.width / 2;
      const baseline = captionY + CAPTION_SIZE * 0.36;
      const n = glyphs.letters.length;
      for (let i = 0; i < n; i += 1) {
        const p = clamp01(reveal * (1 + LETTER_SPREAD) - (n > 1 ? i / (n - 1) : 0) * LETTER_SPREAD);
        if (p <= 0) continue;
        const e = easeOut(p);
        const ink = Skia.Paint();
        ink.setColor(Skia.Color(textColor));
        ink.setAlphaf(e);
        const blur = reducedMotion ? 0 : (1 - e) * LETTER_BLUR;
        if (blur > 0.1) ink.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
        const rise = reducedMotion ? 0 : (1 - e) * LETTER_RISE;
        canvas.drawText(glyphs.letters[i], left + glyphs.at[i], baseline + rise, ink, fonts.caption);
      }
      // Rules drawn out from the caption to the margins, once most of it has arrived.
      const ruled = easeOut(clamp01((reveal - 0.45) / 0.55));
      if (ruled > 0) {
        const rule = Skia.Paint();
        rule.setColor(Skia.Color(textColor));
        rule.setAlphaf(0.45 * ruled);
        const full = left - RULE_GAP - RULE_MARGIN;
        const length = full * ruled;
        canvas.drawRect(Skia.XYWHRect(left - RULE_GAP - length, captionY - 0.5, length, 1), rule);
        canvas.drawRect(Skia.XYWHRect(width - left + RULE_GAP, captionY - 0.5, length, 1), rule);
      }
    }
    if (layered) {
      canvas.restore();
      const dim = Skia.Paint();
      dim.setColor(Skia.Color(background));
      dim.setAlphaf(WALL_DIM * wall);
      canvas.drawRect(Skia.XYWHRect(0, 0, width, height), dim);
    }

    if (o > 0.001 && oc !== NONE) {
      // The open tile, between its cell and the middle of the screen, following the finger.
      const from = cellCentre(oc, or, l);
      const shrink = 1 - DRAG_SHRINK * clamp01(dy / (height * SHRINK_SHARE));
      const s = l.side + (openSide * shrink - l.side) * o;
      const cx = from.x + (width / 2 + dx - from.x) * o;
      const cy = from.y + (openY + dy - from.y) * o;
      const index = tileAt(oc, or, count);
      const tile = tiles[index];
      // A glow in the tile's own colour, spreading below it.
      const glow = Skia.Paint();
      glow.setColor(Skia.Color(tile.kind === 'photo' ? tile.tint : tile.paper));
      glow.setAlphaf(GLOW * lifted * (1 - away));
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, GLOW_BLUR * Math.max(0.2, s / openSide), true));
      canvas.drawRect(Skia.XYWHRect(cx - s * 0.45, cy - s * 0.45 + GLOW_DROP * lifted, s * 0.9, s * 0.9), glow);
      const larger = sharp.get();
      drawTile(canvas, tile, images[index], typeWidths[index], fonts.type, shade, cx - s / 2, cy - s / 2, s, 1, now, larger && larger.index === index ? larger : null);

      // Its note and title, riding just below it.
      const shown = clamp01((o - 0.55) / 0.45) * (1 - clamp01(away * 3));
      if (shown > 0.01) {
        const e = easeOut(shown);
        const top = cy + s / 2 + DETAIL_GAP + (1 - e) * CAPTION_RISE;
        const meta = Skia.Paint();
        meta.setColor(Skia.Color(textColor));
        meta.setAlphaf(0.6 * e);
        drawTracked(canvas, tile.note.toUpperCase(), fonts.meta, cx - s / 2, top + 12, 1, meta);
        const title = Skia.Paint();
        title.setColor(Skia.Color(textColor));
        title.setAlphaf(e);
        canvas.drawText(tile.title, cx - s / 2, top + 12 + 8 + 30, title, fonts.title);
      }
    }
    return recorder.finishRecordingAsPicture();
  });

  // The columns on screen and the live width of the gap, in points.
  const readout = useDerivedValue(() => {
    const l = layoutOf(width, height, spread.get(), 0, 0);
    return `${columnsShown(width, l.pitch)} COL · GAP ${Math.round(l.pitch - l.side)} PT`;
  });
  // Right-aligned with the other labels' edge.
  const readoutX = useDerivedValue(() => width - 20 - fonts.label.measureText(readout.get()).width);
  const chromeStyle = useAnimatedStyle(() => ({ opacity: chrome.get() }));

  return (
    <GestureDetector gesture={Gesture.Simultaneous(pinch, Gesture.Race(pan, tap))}>
      <View
        style={[styles.screen, { backgroundColor: background }]}
        accessibilityLabel={caption}
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
          stage.set({ width: layout.width, height: layout.height });
          chrome.set(withDelay(CHROME_DELAY, withTiming(1, { duration: CHROME_TIME, easing: EASE_OUT })));
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
            <Rect x={0} y={0} width={width} height={insets.top + EDGE_FADE}>
              <LinearGradient
                start={vec(0, 0)}
                end={vec(0, insets.top + EDGE_FADE)}
                colors={[`${background}${EDGE_STRENGTH}`, `${background}00`]}
              />
            </Rect>
            <Rect x={0} y={height - insets.bottom - EDGE_FADE} width={width} height={insets.bottom + EDGE_FADE}>
              <LinearGradient
                start={vec(0, height - insets.bottom - EDGE_FADE)}
                end={vec(0, height)}
                colors={[`${background}00`, `${background}${EDGE_STRENGTH}`]}
              />
            </Rect>
            <SkiaText
              x={readoutX}
              y={insets.top + 26}
              text={readout}
              font={fonts.label}
              color={textColor}
              opacity={chrome}
            />
          </Canvas>
        )}

        <Animated.View pointerEvents="none" style={[styles.row, { top: insets.top + 14 }, chromeStyle]}>
          <Text style={[styles.label, { color: textColor }]}>Studio Notes</Text>
          <Text style={[styles.label, styles.middle, { color: textColor }]}>Frames</Text>
          <View style={styles.side} />
        </Animated.View>
        <Animated.View pointerEvents="none" style={[styles.row, { bottom: insets.bottom + 18 }, chromeStyle]}>
          <Text style={[styles.label, { color: textColor }]}>Pinch</Text>
          <Text style={[styles.label, styles.middle, { color: textColor }]}>Drag</Text>
          <Text style={[styles.label, styles.end, { color: textColor }]}>Tap</Text>
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  row: {
    position: 'absolute',
    left: 20,
    right: 20,
    flexDirection: 'row',
    alignItems: 'center',
  },
  label: {
    flex: 1,
    fontSize: 11,
    fontWeight: '500',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  side: {
    flex: 1,
  },
  middle: {
    textAlign: 'center',
  },
  end: {
    textAlign: 'right',
  },
});
