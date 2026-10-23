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
  type SkColorFilter,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkRect,
  TileMode,
  vec,
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
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Glyph = 'search' | 'expand' | 'figure' | 'basket';

/** What a card shows: a photo printed in black and white or in lime, or a piece of the identity. */
export type DepthCard =
  | { kind: 'photo'; title: string; note: string; uri: string; full?: string; print: 'mono' | 'lime' }
  | { kind: 'poster'; title: string; note: string; uri: string; full?: string; headline: [string, string] }
  | { kind: 'dots'; title: string; note: string; pattern: number }
  | { kind: 'glyph'; title: string; note: string; glyph: Glyph }
  | { kind: 'chip'; title: string; note: string; rows: [string, string] }
  | { kind: 'mark'; title: string; note: string; word: string };

type Props = {
  /** What the cards in the field show; they repeat as far as the field runs. */
  cards?: DepthCard[];
  /** Flies from card to card, drifting in on each, and turns the lights over now and then. */
  autoPlay?: boolean;
  /** Starts on the dark scene. */
  dark?: boolean;
  /** The identity's colour: the dotted cards, the lime prints and the switch. */
  accent?: string;
  /** Called with a card when the camera is sent to it by hand. */
  onFocus?: (card: DepthCard) => void;
};

const unsplash = (id: string, w: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&h=${Math.round(w * 1.25)}&q=82`;
// Every photo is held at a size fit for the field; the card the camera stops at is fetched again
// at full size, so it is sharp across most of the screen without holding fifty large photos.
const photo = (id: string) => ({ uri: unsplash(id, 480), full: unsplash(id, 1000) });

const CARDS: DepthCard[] = [
  { kind: 'dots', title: 'Signal', note: 'Pattern, lime', pattern: 0 },
  { kind: 'photo', title: 'Grey Room', note: 'Portrait, black and white', ...photo('photo-1684598273432-8f8e882cd16f'), print: 'mono' },
  { kind: 'photo', title: 'Outline', note: 'Profile, lime print', ...photo('photo-1676195470090-7c90bf539b3b'), print: 'lime' },
  { kind: 'photo', title: 'Ninety', note: 'Portrait, black and white', ...photo('photo-1685041552051-13de9e56228c'), print: 'mono' },
  { kind: 'photo', title: 'Antique', note: 'Sculpture, black and white', ...photo('photo-1729008712499-5e6b58420789'), print: 'mono' },
  { kind: 'photo', title: 'Atelier', note: 'Fashion, black and white', ...photo('photo-1717765698239-88731a968d20'), print: 'mono' },
  { kind: 'glyph', title: 'Find', note: 'Icon, dotted', glyph: 'search' },
  { kind: 'photo', title: 'Low Key', note: 'Portrait, lime print', ...photo('photo-1587987746776-302404b98970'), print: 'lime' },
  { kind: 'poster', title: 'Edge', note: 'Poster, framed', ...photo('photo-1508153460964-48ffffcb0829'), headline: ['Light finds', 'its level'] },
  { kind: 'mark', title: 'Aloft', note: 'Wordmark', word: 'aloft' },
  { kind: 'photo', title: 'Relic', note: 'Sculpture, black and white', ...photo('photo-1565170556450-bdfc81864ca1'), print: 'mono' },
  { kind: 'photo', title: 'Noir', note: 'Fashion, lime print', ...photo('photo-1592833578500-1082e18665a3'), print: 'lime' },
  { kind: 'photo', title: 'Plaster', note: 'Sculpture, lime print', ...photo('photo-1604854574894-1be73ca43cb8'), print: 'lime' },
  { kind: 'photo', title: 'Gaze', note: 'Portrait, black and white', ...photo('photo-1506863530036-1efeddceb993'), print: 'mono' },
  { kind: 'photo', title: 'Contour', note: 'Profile, black and white', ...photo('photo-1592479950461-2c8ef29f2a14'), print: 'mono' },
  { kind: 'chip', title: 'Controls', note: 'Interface', rows: ['Lift off', 'Glide'] },
  { kind: 'photo', title: 'Bust', note: 'Sculpture, lime print', ...photo('photo-1612753666134-2792aa809104'), print: 'lime' },
  { kind: 'photo', title: 'Contrast', note: 'Fashion, black and white', ...photo('photo-1754315052701-0b98f6ba786b'), print: 'mono' },
  { kind: 'glyph', title: 'Expand', note: 'Icon, dotted', glyph: 'expand' },
  { kind: 'photo', title: 'Linen', note: 'Portrait, black and white', ...photo('photo-1606143412458-acc5f86de897'), print: 'mono' },
  { kind: 'photo', title: 'Shadow', note: 'Profile, lime print', ...photo('photo-1611034540516-665df2bbdfd9'), print: 'lime' },
  { kind: 'poster', title: 'Focus', note: 'Poster, framed', ...photo('photo-1629511565591-a1d494ad6c58'), headline: ['Where depth', 'meets focus'] },
  { kind: 'photo', title: 'Classic', note: 'Sculpture, black and white', ...photo('photo-1673707180677-6e3605339daa'), print: 'mono' },
  { kind: 'photo', title: 'Grain', note: 'Fashion, black and white', ...photo('photo-1565754121773-b185234efb37'), print: 'mono' },
  { kind: 'dots', title: 'Swarm', note: 'Pattern, lime', pattern: 1 },
  { kind: 'photo', title: 'Dusk', note: 'Portrait, lime print', ...photo('photo-1690376647869-45b2e9987018'), print: 'lime' },
  { kind: 'photo', title: 'Silhouette', note: 'Profile, black and white', ...photo('photo-1540304801084-0944beb20de7'), print: 'mono' },
  { kind: 'photo', title: 'Profile', note: 'Portrait, black and white', ...photo('photo-1657412235453-8adb25a45cd0'), print: 'mono' },
  { kind: 'photo', title: 'Cast', note: 'Sculpture, black and white', ...photo('photo-1781707870248-e2607e82bac0'), print: 'mono' },
  { kind: 'poster', title: 'Cut Out', note: 'Poster, framed', ...photo('photo-1574015974293-817f0ebebb74'), headline: ['Still air,', 'clear sight'] },
  { kind: 'glyph', title: 'Pilot', note: 'Icon, dotted', glyph: 'figure' },
  { kind: 'photo', title: 'Hush', note: 'Portrait, black and white', ...photo('photo-1563170446-9c3c0622d8a9'), print: 'mono' },
  { kind: 'photo', title: 'Turn', note: 'Profile, black and white', ...photo('photo-1584462746497-276f4aeb9fca'), print: 'mono' },
  { kind: 'photo', title: 'Stem', note: 'Still life, lime print', ...photo('photo-1471086569966-db3eebc25a59'), print: 'lime' },
  { kind: 'photo', title: 'Chalk', note: 'Sculpture, lime print', ...photo('photo-1637246344338-2afcfb02e766'), print: 'lime' },
  { kind: 'photo', title: 'Swing', note: 'Fashion, black and white', ...photo('photo-1596993100471-c3905dafa78e'), print: 'mono' },
  { kind: 'chip', title: 'Status', note: 'Interface', rows: ['Ascend', 'Hold'] },
  { kind: 'photo', title: 'Glance', note: 'Portrait, black and white', ...photo('photo-1612928414075-bc722ade44f1'), print: 'mono' },
  { kind: 'photo', title: 'Side', note: 'Profile, lime print', ...photo('photo-1663232176328-5430c668593e'), print: 'lime' },
  { kind: 'photo', title: 'Mask', note: 'Sculpture, black and white', ...photo('photo-1561910377-c2a014ffd1cd'), print: 'mono' },
  { kind: 'photo', title: 'Gesso', note: 'Sculpture, black and white', ...photo('photo-1682422911441-a4afa4ddf659'), print: 'mono' },
  { kind: 'photo', title: 'Halo', note: 'Portrait, black and white', ...photo('photo-1535713875002-d1d0cf377fde'), print: 'mono' },
  { kind: 'glyph', title: 'Carry', note: 'Icon, dotted', glyph: 'basket' },
  { kind: 'photo', title: 'Line', note: 'Profile, lime print', ...photo('photo-1704202632056-de2c85caf4cf'), print: 'lime' },
  { kind: 'photo', title: 'Matte', note: 'Portrait, black and white', ...photo('photo-1768247695735-e10baa73e917'), print: 'mono' },
  { kind: 'dots', title: 'Pulse', note: 'Pattern, lime', pattern: 2 },
  { kind: 'photo', title: 'Rim', note: 'Profile, black and white', ...photo('photo-1627626487034-0a3dc7278c18'), print: 'mono' },
  { kind: 'photo', title: 'Studio', note: 'Portrait, lime print', ...photo('photo-1631002724711-d1ae3cf651b0'), print: 'lime' },
  { kind: 'photo', title: 'Soft Box', note: 'Portrait, black and white', ...photo('photo-1649058047388-b49c028df9ac'), print: 'mono' },
];

// The two scenes, and the inks of the cards.
const LIGHT = { bg: '#ffffff', ink: '#111111' };
const DARK = { bg: '#0e0e0e', ink: '#f0f0ea' };
const CARD_INK = '#1c1c1c';
const MARK_INK = '#0b0b0b';
const DOT_INK = '#f4f4f0';

// The field: FIELD cards through a tunnel DEPTH units long that repeats behind itself, a unit
// being about a card's width. Every HERO_EVERY-th card floats near the middle as a stop for the
// camera; the rest are scattered SPREAD_X by SPREAD_Y units around them, clear of the middle.
const FIELD = 160;
const HERO_EVERY = 4;
const DEPTH = 64;
const HERO_X = 0.8;
const HERO_Y = 1.3;
const SPREAD_X = 3;
const SPREAD_Y = 4.8;
const CLEAR_X = 1;
const CLEAR_Y = 1.7;
// Each card is turned by up to these angles (radians) and sways a little as it floats.
const YAW = 0.14;
const PITCH = 0.1;
const ROLL = 0.035;
const SWAY = 0.03;
// The lens: its focal length as a share of the screen's width. A card the camera stops at
// fills FRAME_SHARE of the width, a little off the middle. Cards fade in between FAR and
// FAR_FADE units away and out between NEAR_FADE and NEAR.
const FOCAL = 0.95;
const FRAME_SHARE = 0.62;
const FRAME_OFF_X = 0.07;
const FRAME_OFF_Y = 0.05;
const FAR = 12;
const FAR_FADE = 9;
const NEAR = 0.35;
const NEAR_FADE = 0.8;

// The self-playing reel: the camera flies on to the next stop ahead over FLIGHT ms on an
// ease-in-out curve, arriving DRIFT units short of it, then drifts in the rest of the way over
// HOLD ms; every TURN_EVERY stops the lights turn over on the way.
const FLIGHT = 950;
const HOLD = 900;
const DRIFT = 0.2;
const DRIFT_SIDE = 0.06;
const TURN_EVERY = 6;
// Arrival: the camera starts ARRIVE units back and flies in over ARRIVE_TIME ms.
const ARRIVE = 3;
const ARRIVE_TIME = 1500;
// By hand: a drag flies DRAG_RATE units per pt and pans PAN_RATE units per pt; on release the
// camera flies to the stop nearest where the drag was heading THROW s ahead.
const DRAG_RATE = 0.014;
const PAN_RATE = 0.006;
const THROW = 0.25;
const SNAP_SPRING = { duration: 750, dampingRatio: 0.9 } as const;
const FLY_SPRING = { duration: 950, dampingRatio: 0.92 } as const;
const USER_HOLD = 3;

// Turning the lights over by hand: the new scene spreads from the switch over WIPE_TIME ms.
const WIPE_TIME = 900;
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EDGE_FADE = 64;
const CHROME_DELAY = 500;
const CHROME_TIME = 600;
const CAPTION_IN = 0.35;
const CAPTION_RISE = 8;
const IMAGE_FADE = 0.4;
// How many full-size photos are kept: the card in view, the next one and the last two.
const LARGE_KEEP = 4;
// The photos' prints are a little harder than the originals.
const CONTRAST = 1.2;

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

type Loaded = { image: SkImage; at: number };
type Large = Loaded & { card: number };
type Dot = { x: number; y: number; r: number };
/** Where one card floats in the field. Sizes are in units, angles in radians. */
type Placement = {
  x: number;
  y: number;
  z: number;
  width: number;
  aspect: number;
  yaw: number;
  pitch: number;
  roll: number;
  card: number;
  hero: boolean;
  frameX: number;
  frameY: number;
};
type Camera = { x: number; y: number; z: number };
type Shown = { index: number; depth: number; matrix: number[]; quad: number[]; alpha: number };

/** A small seeded generator, so the field is the same every time. */
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

const SIZE: Record<DepthCard['kind'], { width: number; aspect: number }> = {
  photo: { width: 0.9, aspect: 1.22 },
  poster: { width: 0.85, aspect: 1.32 },
  dots: { width: 0.78, aspect: 1.12 },
  glyph: { width: 0.8, aspect: 1.18 },
  chip: { width: 0.72, aspect: 0.78 },
  mark: { width: 1, aspect: 0.62 },
};

function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b);
}

/** The first step from `from` up that walks through all `n` cards before it repeats one. */
function coprime(n: number, from: number) {
  let step = from;
  while (gcd(step, n) !== 1) step += 1;
  return step;
}

function scatter(cards: DepthCard[]): Placement[] {
  const r = random(11);
  const heroStep = coprime(cards.length, 7);
  const restStep = coprime(cards.length, 5);
  return Array.from({ length: FIELD }, (_, i) => {
    const hero = i % HERO_EVERY === 0;
    // The stops take every card in turn; the cards around them are dealt from a different shuffle.
    const card = hero ? ((i / HERO_EVERY) * heroStep) % cards.length : (i * restStep + 3) % cards.length;
    const size = SIZE[cards[card].kind];
    let x = (r() * 2 - 1) * HERO_X;
    let y = (r() * 2 - 1) * HERO_Y;
    if (!hero) {
      do {
        x = (r() * 2 - 1) * SPREAD_X;
        y = (r() * 2 - 1) * SPREAD_Y;
      } while (Math.abs(x) < CLEAR_X && Math.abs(y) < CLEAR_Y);
    }
    return {
      x,
      y,
      z: ((i + r() * 0.4) * DEPTH) / FIELD,
      width: size.width * (hero ? 1 : 0.55 + r() * 0.5),
      aspect: size.aspect,
      yaw: (r() * 2 - 1) * YAW,
      pitch: (r() * 2 - 1) * PITCH,
      roll: (r() * 2 - 1) * ROLL,
      card,
      hero,
      frameX: (r() * 2 - 1) * FRAME_OFF_X,
      frameY: (r() * 2 - 1) * FRAME_OFF_Y,
    };
  });
}

// Three clusters of dots, bigger towards the top right, in card widths about the card's middle.
const PATTERNS: Dot[][] = [
  [
    { x: -0.3, y: -0.16, r: 0.035 },
    { x: -0.12, y: -0.17, r: 0.075 },
    { x: 0.14, y: -0.19, r: 0.115 },
    { x: -0.24, y: 0, r: 0.05 },
    { x: -0.02, y: 0.01, r: 0.075 },
    { x: 0.21, y: 0.04, r: 0.075 },
    { x: -0.17, y: 0.18, r: 0.035 },
    { x: 0.04, y: 0.21, r: 0.05 },
    { x: 0.21, y: 0.24, r: 0.035 },
  ],
  [
    { x: -0.26, y: -0.2, r: 0.03 },
    { x: -0.05, y: -0.2, r: 0.055 },
    { x: 0.17, y: -0.16, r: 0.09 },
    { x: -0.18, y: -0.03, r: 0.07 },
    { x: 0.06, y: 0.02, r: 0.1 },
    { x: -0.27, y: 0.14, r: 0.035 },
    { x: -0.06, y: 0.19, r: 0.05 },
    { x: 0.18, y: 0.2, r: 0.04 },
  ],
  [
    { x: -0.2, y: -0.22, r: 0.05 },
    { x: 0.04, y: -0.2, r: 0.035 },
    { x: 0.2, y: -0.12, r: 0.06 },
    { x: -0.25, y: -0.04, r: 0.035 },
    { x: -0.05, y: -0.02, r: 0.11 },
    { x: 0.2, y: 0.08, r: 0.075 },
    { x: -0.2, y: 0.17, r: 0.06 },
    { x: 0.02, y: 0.22, r: 0.035 },
  ],
];

/** An icon drawn in evenly spaced dots, in card widths about the card's middle. */
function dotted(glyph: Glyph): Dot[] {
  const dots: Dot[] = [];
  const size = 0.017;
  const line = (x0: number, y0: number, x1: number, y1: number, n: number) => {
    for (let i = 0; i < n; i += 1) {
      const t = n === 1 ? 0 : i / (n - 1);
      dots.push({ x: x0 + (x1 - x0) * t, y: y0 + (y1 - y0) * t, r: size });
    }
  };
  const ring = (cx: number, cy: number, radius: number, n: number) => {
    for (let i = 0; i < n; i += 1) {
      const a = (i / n) * Math.PI * 2;
      dots.push({ x: cx + Math.cos(a) * radius, y: cy + Math.sin(a) * radius, r: size });
    }
  };
  if (glyph === 'search') {
    ring(0.03, -0.04, 0.2, 18);
    line(-0.15, 0.14, -0.25, 0.24, 3);
  }
  if (glyph === 'expand') {
    line(-0.07, -0.07, 0.07, -0.07, 4);
    line(-0.07, 0.07, 0.07, 0.07, 4);
    line(-0.07, -0.023, -0.07, 0.023, 2);
    line(0.07, -0.023, 0.07, 0.023, 2);
    for (const [sx, sy] of [
      [1, 1],
      [1, -1],
      [-1, 1],
      [-1, -1],
    ]) {
      line(sx * 0.12, sy * 0.12, sx * 0.28, sy * 0.28, 4);
      dots.push({ x: sx * 0.2, y: sy * 0.28, r: size }, { x: sx * 0.28, y: sy * 0.2, r: size });
    }
  }
  if (glyph === 'figure') {
    ring(0, -0.13, 0.1, 12);
    line(0, 0.03, 0, 0.27, 5);
    dots.push({ x: -0.06, y: 0.21, r: size }, { x: 0.06, y: 0.21, r: size });
  }
  if (glyph === 'basket') {
    line(-0.27, -0.02, 0.27, -0.02, 9);
    line(-0.24, 0.05, -0.18, 0.22, 3);
    line(0.24, 0.05, 0.18, 0.22, 3);
    line(-0.11, 0.22, 0.11, 0.22, 3);
    line(-0.1, -0.09, -0.04, -0.21, 3);
    line(0.1, -0.09, 0.04, -0.21, 3);
  }
  return dots;
}

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function easeOut(p: number) {
  'worklet';
  return 1 - (1 - p) * (1 - p) * (1 - p);
}

function mod(value: number, by: number) {
  'worklet';
  return ((value % by) + by) % by;
}

/** Mixes two #rrggbb colours. */
function mix(a: string, b: string, t: number) {
  'worklet';
  const channel = (hex: string, at: number) => parseInt(hex.slice(at, at + 2), 16);
  const out = [1, 3, 5].map((at) => Math.round(channel(a, at) + (channel(b, at) - channel(a, at)) * t));
  return `#${out.map((value) => value.toString(16).padStart(2, '0')).join('')}`;
}

/**
 * Where the camera stands to frame a card: far enough back for it to fill FRAME_SHARE of the
 * width, a little off the middle, at the copy of the tunnel nearest `near` (or the next one ahead).
 */
function framing(p: Placement, width: number, height: number, near: number, ahead: boolean): Camera {
  'worklet';
  const distance = (FOCAL * p.width) / FRAME_SHARE;
  const gap = p.z - distance - near;
  return {
    x: p.x - (p.frameX * distance) / FOCAL,
    y: p.y - ((p.frameY * height) / width) * (distance / FOCAL),
    z: near + (ahead ? mod(gap, DEPTH) : mod(gap + DEPTH / 2, DEPTH) - DEPTH / 2),
  };
}

/**
 * Every card the camera can see, far to near: the perspective matrix that maps its face (in
 * hundredths of a unit about its middle) onto the screen, its corners there, and how faded it is.
 */
function frame(field: Placement[], cam: Camera, width: number, height: number, now: number, sway: number): Shown[] {
  'worklet';
  const shown: Shown[] = [];
  const f = width * FOCAL;
  for (let i = 0; i < field.length; i += 1) {
    const p = field[i];
    const depth = mod(p.z - cam.z, DEPTH);
    if (depth < NEAR || depth > FAR) continue;
    const wave = sway * Math.sin(now * 0.7 + i * 1.7);
    const cx = p.x - cam.x;
    const cy = p.y - cam.y + wave * SWAY;
    const cz = depth;
    const yaw = p.yaw + wave * 0.03;
    // The card's axes after it turns, and the plane they span projected through the lens.
    const cY = Math.cos(yaw);
    const sY = Math.sin(yaw);
    const cP = Math.cos(p.pitch);
    const sP = Math.sin(p.pitch);
    const cR = Math.cos(p.roll);
    const sR = Math.sin(p.roll);
    const k = 1 / 100;
    const ax = (cY * cR + sY * sP * sR) * k;
    const ay = cP * sR * k;
    const az = (-sY * cR + cY * sP * sR) * k;
    const bx = (-cY * sR + sY * sP * cR) * k;
    const by = cP * cR * k;
    const bz = (sY * sR + cY * sP * cR) * k;
    const mx = width / 2;
    const my = height / 2;
    const matrix = [
      (mx * az + f * ax) / cz,
      (mx * bz + f * bx) / cz,
      (mx * cz + f * cx) / cz,
      (my * az + f * ay) / cz,
      (my * bz + f * by) / cz,
      (my * cz + f * cy) / cz,
      az / cz,
      bz / cz,
      1,
    ];
    const w = p.width * 50;
    const h = p.width * p.aspect * 50;
    const quad: number[] = [];
    let behind = false;
    for (const [u, v] of [
      [-w, -h],
      [w, -h],
      [w, h],
      [-w, h],
    ]) {
      const z = matrix[6] * u + matrix[7] * v + 1;
      if (z <= 0.05) behind = true;
      quad.push((matrix[0] * u + matrix[1] * v + matrix[2]) / z, (matrix[3] * u + matrix[4] * v + matrix[5]) / z);
    }
    if (behind) continue;
    const left = Math.min(quad[0], quad[2], quad[4], quad[6]);
    const right = Math.max(quad[0], quad[2], quad[4], quad[6]);
    const top = Math.min(quad[1], quad[3], quad[5], quad[7]);
    const bottom = Math.max(quad[1], quad[3], quad[5], quad[7]);
    if (right < 0 || left > width || bottom < 0 || top > height) continue;
    const alpha = clamp01((FAR - depth) / (FAR - FAR_FADE)) * clamp01((depth - NEAR) / (NEAR_FADE - NEAR));
    shown.push({ index: i, depth, matrix, quad, alpha });
  }
  return shown.sort((a, b) => b.depth - a.depth);
}

/** The full-size copy of a card's photo, if it has arrived. */
function sharpFor(large: Large[], card: number): Loaded | null {
  'worklet';
  for (const item of large) if (item.card === card) return item;
  return null;
}

/** Whether a point lies inside a convex quad given corner by corner. */
function inside(quad: number[], x: number, y: number) {
  'worklet';
  let sign = 0;
  for (let i = 0; i < 4; i += 1) {
    const j = (i + 1) % 4;
    const cross = (quad[j * 2] - quad[i * 2]) * (y - quad[i * 2 + 1]) - (quad[j * 2 + 1] - quad[i * 2 + 1]) * (x - quad[i * 2]);
    if (cross === 0) continue;
    if (sign === 0) sign = Math.sign(cross);
    else if (Math.sign(cross) !== sign) return false;
  }
  return true;
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

/** Draws text a letter at a time, `tracking` pt apart. */
function drawTracked(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, tracking: number, paint: SkPaint) {
  'worklet';
  let at = x;
  for (const letter of text) {
    canvas.drawText(letter, at, y, paint, font);
    at += advance(font, letter) + tracking;
  }
}

/** Text set at `size` from a 100 pt font, so it stays sharp at any scale. */
function drawSized(canvas: SkCanvas, text: string, font: SkFont, size: number, x: number, y: number, paint: SkPaint) {
  'worklet';
  canvas.save();
  canvas.translate(x, y);
  canvas.scale(size / 100, size / 100);
  canvas.drawText(text, 0, 0, paint, font);
  canvas.restore();
}

function drawPrint(canvas: SkCanvas, loaded: Loaded, rect: SkRect, print: SkColorFilter, now: number) {
  'worklet';
  const image = loaded.image;
  const iw = image.width();
  const ih = image.height();
  const fit = Math.max(rect.width / iw, rect.height / ih);
  const sw = rect.width / fit;
  const sh = rect.height / fit;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColorFilter(print);
  paint.setAlphaf(clamp01((now - loaded.at) / IMAGE_FADE));
  canvas.drawImageRectOptions(
    image,
    Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh),
    rect,
    FilterMode.Linear,
    MipmapMode.Linear,
    paint,
  );
}

/** A photo cropped to fill `rect`, sampled smoothly at any size and faded in once it arrives. */
function drawPhoto(canvas: SkCanvas, loaded: Loaded, rect: SkRect, print: SkColorFilter, now: number, sharper: Loaded | null) {
  'worklet';
  drawPrint(canvas, loaded, rect, print, now);
  // The full-size copy, once it arrives, fades in over the field-sized one.
  if (sharper) drawPrint(canvas, sharper, rect, print, now);
}

/** A card's face, w by h about its middle. */
function drawCard(
  canvas: SkCanvas,
  card: DepthCard,
  dots: Dot[],
  loaded: Loaded | null,
  sharper: Loaded | null,
  prints: { mono: SkColorFilter; lime: SkColorFilter },
  fonts: { text: SkFont; bold: SkFont },
  accent: string,
  w: number,
  h: number,
  dark: number,
  now: number,
) {
  'worklet';
  const rect = Skia.XYWHRect(-w / 2, -h / 2, w, h);
  const corner = Math.min(w, h) * 0.045;
  const rounded = Skia.RRectXY(rect, corner, corner);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);

  if (card.kind === 'photo') {
    fill.setColor(Skia.Color(card.print === 'lime' ? accent : '#d6d6d2'));
    canvas.drawRRect(rounded, fill);
    if (loaded) {
      canvas.save();
      canvas.clipRRect(rounded, ClipOp.Intersect, true);
      drawPhoto(canvas, loaded, rect, card.print === 'lime' ? prints.lime : prints.mono, now, sharper);
      canvas.restore();
    }
    return;
  }
  if (card.kind === 'poster') {
    // A print in a brushed grey frame: two lines of type over a portrait, the wordmark below.
    fill.setShader(
      Skia.Shader.MakeLinearGradient(vec(0, -h / 2), vec(0, h / 2), [Skia.Color('#e4e4e2'), Skia.Color('#8d8d8b')], null, TileMode.Clamp),
    );
    canvas.drawRRect(rounded, fill);
    const inset = w * 0.07;
    const paper = Skia.XYWHRect(-w / 2 + inset, -h / 2 + inset, w - inset * 2, h - inset * 2);
    const sheet = Skia.Paint();
    sheet.setColor(Skia.Color('#fafaf8'));
    canvas.drawRect(paper, sheet);
    ink.setColor(Skia.Color('#151515'));
    const size = paper.width * 0.11;
    card.headline.forEach((line, i) => {
      drawSized(canvas, line, fonts.text, size, paper.x + paper.width * 0.07, paper.y + paper.width * 0.07 + size * (0.9 + i * 1.05), ink);
    });
    const portrait = Skia.XYWHRect(paper.x + paper.width * 0.22, paper.y + paper.height * 0.36, paper.width * 0.56, paper.height * 0.5);
    if (loaded) drawPhoto(canvas, loaded, portrait, prints.mono, now, sharper);
    drawSized(canvas, 'aloft', fonts.bold, paper.width * 0.07, paper.x + paper.width * 0.74, paper.y + paper.height * 0.95, ink);
    return;
  }
  if (card.kind === 'dots') {
    fill.setColor(Skia.Color(accent));
    canvas.drawRRect(rounded, fill);
    ink.setColor(Skia.Color(CARD_INK));
    for (const dot of dots) canvas.drawCircle(dot.x * w, dot.y * w, dot.r * w, ink);
    return;
  }

  if (card.kind === 'chip') {
    // A dark, faintly green photograph's worth of ground under two glassy rows.
    fill.setShader(
      Skia.Shader.MakeRadialGradient(vec(-w * 0.15, -h * 0.25), Math.max(w, h), [Skia.Color('#2c3329'), Skia.Color('#0b0c0b')], null, TileMode.Clamp),
    );
  } else {
    fill.setColor(Skia.Color(card.kind === 'mark' ? MARK_INK : CARD_INK));
  }
  canvas.drawRRect(rounded, fill);
  if (dark > 0) {
    // On the dark scene the dark cards keep a faint edge.
    const edge = Skia.Paint();
    edge.setAntiAlias(true);
    edge.setStyle(PaintStyle.Stroke);
    edge.setStrokeWidth(1);
    edge.setColor(Skia.Color('#ffffff'));
    edge.setAlphaf(0.1 * dark);
    canvas.drawRRect(rounded, edge);
  }
  if (card.kind === 'glyph') {
    ink.setColor(Skia.Color(DOT_INK));
    for (const dot of dots) canvas.drawCircle(dot.x * w, dot.y * w, dot.r * w, ink);
    return;
  }
  if (card.kind === 'chip') {
    card.rows.forEach((row, i) => {
      const pill = Skia.XYWHRect(-w * 0.36, -h * 0.2 + i * h * 0.26, w * 0.72, h * 0.19);
      const glass = Skia.Paint();
      glass.setAntiAlias(true);
      glass.setColor(Skia.Color('#ffffff'));
      glass.setAlphaf(0.1);
      canvas.drawRRect(Skia.RRectXY(pill, pill.height / 2, pill.height / 2), glass);
      const cy = pill.y + pill.height / 2;
      ink.setColor(Skia.Color(accent));
      canvas.drawCircle(pill.x + pill.height / 2, cy, pill.height * 0.32, ink);
      ink.setColor(Skia.Color(DOT_INK));
      drawSized(canvas, row, fonts.text, pill.height * 0.42, pill.x + pill.height * 1.05, cy + pill.height * 0.15, ink);
    });
    return;
  }
  // The wordmark, with a square of dots after it.
  ink.setColor(Skia.Color(DOT_INK));
  drawSized(canvas, card.word, fonts.bold, h * 0.32, -w / 2 + w * 0.09, h * 0.11, ink);
  ink.setColor(Skia.Color(accent));
  for (let row = 0; row < 3; row += 1) {
    for (let col = 0; col < 3; col += 1) {
      canvas.drawCircle(w / 2 - w * 0.2 + col * w * 0.045, -h * 0.09 + row * h * 0.09, h * 0.022, ink);
    }
  }
}

/**
 * A field of cards floating in space, photos printed in black and white or lime among pieces of
 * a dotted identity. By itself the camera flies from card to card, drifting in on each, and now
 * and then the lights turn over on the way. Dragging flies the camera through the
 * field and lands it on the nearest card; tapping a card flies to it. The switch turns the
 * lights over by hand, the new scene spreading from it across the screen.
 */
export function DepthFieldGallery({ cards = CARDS, autoPlay = false, dark = false, accent = '#d8f05a', onFocus }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const fonts = useMemo(
    () => ({
      label: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '500' }),
      title: matchFont({ fontFamily: FAMILY, fontSize: 30, fontWeight: '300' }),
      text: matchFont({ fontFamily: FAMILY, fontSize: 100, fontWeight: '500' }),
      bold: matchFont({ fontFamily: FAMILY, fontSize: 100, fontWeight: 'bold' }),
    }),
    [],
  );
  const field = useMemo(() => scatter(cards), [cards]);
  // The cards the camera stops at.
  const stops = useMemo(() => field.map((p, i) => (p.hero ? i : -1)).filter((i) => i >= 0), [field]);
  const art = useMemo(
    () => cards.map((card) => (card.kind === 'dots' ? PATTERNS[card.pattern % PATTERNS.length] : card.kind === 'glyph' ? dotted(card.glyph) : [])),
    [cards],
  );
  // The photos' prints: black and white, or black to lime, with a little more contrast.
  const prints = useMemo(() => {
    const [r, g, b] = [1, 3, 5].map((at) => parseInt(accent.slice(at, at + 2), 16) / 255);
    const tone = (red: number, green: number, blue: number) =>
      [red, green, blue]
        .flatMap((c) => [0.2126 * CONTRAST * c, 0.7152 * CONTRAST * c, 0.0722 * CONTRAST * c, 0, 0.5 * (1 - CONTRAST) * c])
        .concat([0, 0, 0, 1, 0]);
    return { mono: Skia.ColorFilter.MakeMatrix(tone(1, 1, 1)), lime: Skia.ColorFilter.MakeMatrix(tone(r, g, b)) };
  }, [accent]);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  // The switch, bottom right; the new scene spreads from its middle.
  const switchX = width - 20 - 22;
  const switchY = height - insets.bottom - 30;
  const reach = Math.hypot(Math.max(switchX, width - switchX), Math.max(switchY, height - switchY));

  // Seconds since the field first appeared.
  const elapsed = useSharedValue(0);
  const loaded = useSharedValue<(Loaded | null)[]>(cards.map(() => null));
  const sharp = useSharedValue<Large[]>([]);
  // The cards whose full-size copy is on its way.
  const [fetching] = useState(() => new Set<number>());
  // The camera, in units; its depth runs on for ever and the field repeats around it.
  const camX = useSharedValue(0);
  const camY = useSharedValue(0);
  const camZ = useSharedValue(0);
  const fromX = useSharedValue(0);
  const fromZ = useSharedValue(0);
  // The card the camera is on, and when it got there, for the caption.
  const focus = useSharedValue(stops[0] ?? 0);
  const focusAt = useSharedValue(0);
  // The card it was on before, whose caption is on its way out.
  const lastFocus = useSharedValue(stops[0] ?? 0);
  // 0 light, 1 dark, and where it is headed.
  const theme = useSharedValue(dark ? 1 : 0);
  const darkOn = useSharedValue(dark);
  const chrome = useSharedValue(0);
  // The montage: time into the current shot, and how many shots so far.
  const shotClock = useSharedValue(0);
  const shots = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const started = useSharedValue(false);
  // The screen's size for the frame callback, which is registered once and cannot read `size`.
  const stage = useSharedValue({ width: 0, height: 0 });

  useEffect(() => {
    looping.set(auto);
    shotClock.set(0);
    hold.set(0);
  }, [auto, hold, looping, shotClock]);

  // Each photo arrives on its own and fades in; until then its card shows its paper.
  useEffect(() => {
    let alive = true;
    loaded.set(cards.map(() => null));
    sharp.set([]);
    fetching.clear();
    cards.forEach((card, i) => {
      if (card.kind !== 'photo' && card.kind !== 'poster') return;
      Skia.Data.fromURI(card.uri)
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
  }, [cards, loaded, sharp, fetching, elapsed]);

  const tapped = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const switched = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const focused = (index: number) => {
    onFocus?.(cards[field[index].card]);
  };

  // Fetches the full-size copies of these cards' photos, keeping only the last few.
  const sharpen = (wanted: number[]) => {
    for (const card of wanted) {
      const item = cards[card];
      if ((item.kind !== 'photo' && item.kind !== 'poster') || !item.full) continue;
      if (fetching.has(card) || sharp.get().some((l) => l.card === card)) continue;
      fetching.add(card);
      Skia.Data.fromURI(item.full)
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          fetching.delete(card);
          if (!image) return;
          sharp.modify((list) => {
            'worklet';
            return [...list.filter((l) => l.card !== card), { image, at: elapsed.get(), card }].slice(-LARGE_KEEP);
          });
        })
        .catch(() => fetching.delete(card));
    }
  };
  // The card at a stop and the one at the stop after it.
  const sharpenAt = (index: number) => {
    'worklet';
    const k = stops.indexOf(index);
    const after = k >= 0 ? stops[(k + 1) % stops.length] : index;
    scheduleOnRN(sharpen, [field[index].card, field[after].card]);
  };

  const setFocus = (index: number) => {
    'worklet';
    if (index === focus.get()) return;
    sharpenAt(index);
    lastFocus.set(focus.get());
    focusAt.set(elapsed.get());
    focus.set(index);
  };
  const turn = (user: boolean) => {
    'worklet';
    const next = !darkOn.get();
    darkOn.set(next);
    theme.set(withTiming(next ? 1 : 0, { duration: reducedMotion ? 250 : WIPE_TIME, easing: EASE_IN_OUT }));
    if (user) scheduleOnRN(switched);
  };
  // Sends the camera to frame a card on a spring, carrying the finger's speed.
  const flyTo = (index: number, spring: { duration: number; dampingRatio: number }, vx: number, vz: number) => {
    'worklet';
    const { width: w, height: h } = stage.get();
    const to = framing(field[index], w, h, camZ.get(), false);
    setFocus(index);
    if (reducedMotion) {
      camX.set(withTiming(to.x, { duration: 250, easing: EASE_OUT }));
      camY.set(withTiming(to.y, { duration: 250, easing: EASE_OUT }));
      camZ.set(withTiming(to.z, { duration: 250, easing: EASE_OUT }));
      return;
    }
    camX.set(withSpring(to.x, { ...spring, velocity: vx }));
    camY.set(withSpring(to.y, spring));
    camZ.set(withSpring(to.z, { ...spring, velocity: vz }));
  };
  // The reel: the camera flies on to a card, arriving a little short and to one side, and
  // drifts in the rest of the way while it holds.
  const glideTo = (index: number, flight: number) => {
    'worklet';
    const { width: w, height: h } = stage.get();
    const to = framing(field[index], w, h, camZ.get(), true);
    const side = Math.sin(index * 12.9898) * DRIFT_SIDE;
    setFocus(index);
    if (reducedMotion) {
      camX.set(withTiming(to.x, { duration: 250, easing: EASE_OUT }));
      camY.set(withTiming(to.y, { duration: 250, easing: EASE_OUT }));
      camZ.set(withTiming(to.z, { duration: 250, easing: EASE_OUT }));
      return;
    }
    const fly = { duration: flight, easing: EASE_IN_OUT };
    const drift = { duration: HOLD + 200, easing: Easing.out(Easing.quad) };
    camX.set(withSequence(withTiming(to.x - side, fly), withTiming(to.x, drift)));
    camY.set(withTiming(to.y, fly));
    camZ.set(withSequence(withTiming(to.z - DRIFT, fly), withTiming(to.z, drift)));
  };
  // The next stop ahead of the camera.
  const nextStop = () => {
    'worklet';
    const { width: w, height: h } = stage.get();
    let best = stops[0];
    let gap = Infinity;
    for (const index of stops) {
      const ahead = framing(field[index], w, h, camZ.get(), true).z - camZ.get();
      if (ahead > DRIFT * 2 && ahead < gap) {
        gap = ahead;
        best = index;
      }
    }
    return best;
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const { width: w } = stage.get();
    if (w === 0) return;
    elapsed.set(elapsed.get() + dt);
    if (!started.get()) {
      // Arrival: the camera starts well back and flies in on the first card.
      started.set(true);
      const { height: h } = stage.get();
      const to = framing(field[stops[0]], w, h, 0, true);
      camX.set(to.x);
      camY.set(to.y);
      camZ.set(to.z - (reducedMotion ? 0 : ARRIVE));
      glideTo(stops[0], ARRIVE_TIME);
      sharpenAt(stops[0]);
      shotClock.set(-ARRIVE_TIME / 1000);
      return;
    }
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) shotClock.set(0);
      return;
    }
    if (!looping.get()) return;
    const t = shotClock.get() + dt;
    if (t < HOLD / 1000) {
      shotClock.set(t);
      return;
    }
    shotClock.set(-FLIGHT / 1000);
    const n = shots.get() + 1;
    shots.set(n);
    if (n % TURN_EVERY === 0) turn(false);
    glideTo(nextStop(), FLIGHT);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(4)
    .onStart(() => {
      touched();
      // Caught wherever the camera is.
      camX.set(camX.get());
      camY.set(camY.get());
      camZ.set(camZ.get());
      fromX.set(camX.get());
      fromZ.set(camZ.get());
    })
    .onUpdate((event) => {
      // Up flies on, down flies back; sideways pans, the cards following the finger.
      camZ.set(fromZ.get() - event.translationY * DRAG_RATE);
      camX.set(fromX.get() - event.translationX * PAN_RATE);
    })
    .onEnd((event) => {
      touched();
      const vz = -event.velocityY * DRAG_RATE;
      const vx = -event.velocityX * PAN_RATE;
      // The stop nearest where the drag was heading.
      const { width: w, height: h } = stage.get();
      const aimZ = camZ.get() + vz * THROW;
      const aimX = camX.get() + vx * THROW;
      let best = stops[0];
      let score = Infinity;
      for (const index of stops) {
        const to = framing(field[index], w, h, aimZ, false);
        const off = Math.abs(to.z - aimZ) + Math.abs(to.x - aimX) * 0.5;
        if (off < score) {
          score = off;
          best = index;
        }
      }
      flyTo(best, SNAP_SPRING, vx, vz);
      scheduleOnRN(focused, best);
    });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success || !size) return;
      touched();
      if (Math.hypot(event.x - switchX, event.y - switchY) < 30) {
        turn(true);
        return;
      }
      // The nearest card under the finger.
      const cam = { x: camX.get(), y: camY.get(), z: camZ.get() };
      const shown = frame(field, cam, width, height, elapsed.get(), reducedMotion ? 0 : 1);
      for (let i = shown.length - 1; i >= 0; i -= 1) {
        if (shown[i].alpha > 0.3 && inside(shown[i].quad, event.x, event.y)) {
          flyTo(shown[i].index, FLY_SPRING, 0, 0);
          scheduleOnRN(tapped);
          scheduleOnRN(focused, shown[i].index);
          return;
        }
      }
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (!size) return recorder.finishRecordingAsPicture();
    const now = elapsed.get();
    const cam = { x: camX.get(), y: camY.get(), z: camZ.get() };
    const images = loaded.get();
    const large = sharp.get();
    const t = theme.get();
    const arrive = reducedMotion ? 1 : easeOut(clamp01(now / 0.6));
    // The new scene spreads from the switch; under reduced motion it fades in instead.
    const radius = reducedMotion ? 0 : t * reach;
    const circle = Skia.Path.Circle(switchX, switchY, Math.max(0, radius));

    const ground = Skia.Paint();
    ground.setColor(Skia.Color(reducedMotion ? mix(LIGHT.bg, DARK.bg, t) : LIGHT.bg));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), ground);
    if (radius > 0) {
      ground.setColor(Skia.Color(DARK.bg));
      canvas.drawPath(circle, ground);
    }

    for (const item of frame(field, cam, width, height, now, reducedMotion ? 0 : 1)) {
      const alpha = item.alpha * arrive;
      if (alpha <= 0.004) continue;
      const p = field[item.index];
      if (alpha < 0.995) {
        // Fading in or out: the card is drawn on its own layer, just big enough for it.
        const q = item.quad;
        const left = Math.min(q[0], q[2], q[4], q[6]);
        const top = Math.min(q[1], q[3], q[5], q[7]);
        const right = Math.max(q[0], q[2], q[4], q[6]);
        const bottom = Math.max(q[1], q[3], q[5], q[7]);
        const layer = Skia.Paint();
        layer.setAlphaf(alpha);
        canvas.saveLayer(layer, Skia.XYWHRect(left - 2, top - 2, right - left + 4, bottom - top + 4));
      } else {
        canvas.save();
      }
      canvas.concat(Skia.Matrix(item.matrix));
      drawCard(canvas, cards[p.card], art[p.card], images[p.card], sharpFor(large, p.card), prints, fonts, accent, p.width * 100, p.width * p.aspect * 100, t, now);
      canvas.restore();
    }

    // Labels, the caption and the switch, in each scene's ink, cut by the spreading edge.
    const chromeIn = chrome.get();
    const current = cards[field[focus.get()].card];
    const last = cards[field[lastFocus.get()].card];
    const numbered = (card: number) => `${String(card + 1).padStart(2, '0')} / ${String(cards.length).padStart(2, '0')}`;
    const count = numbered(field[focus.get()].card);
    const lastCount = numbered(field[lastFocus.get()].card);
    const fresh = easeOut(clamp01((now - focusAt.get()) / CAPTION_IN));
    const paintChrome = (palette: { bg: string; ink: string }, alpha: number) => {
      const fade = Skia.Paint();
      fade.setAlphaf(alpha);
      fade.setShader(
        Skia.Shader.MakeLinearGradient(
          vec(0, 0),
          vec(0, insets.top + EDGE_FADE),
          [Skia.Color(`${palette.bg}d9`), Skia.Color(`${palette.bg}00`)],
          null,
          TileMode.Clamp,
        ),
      );
      canvas.drawRect(Skia.XYWHRect(0, 0, width, insets.top + EDGE_FADE), fade);
      const low = height - insets.bottom - EDGE_FADE - 40;
      fade.setShader(
        Skia.Shader.MakeLinearGradient(vec(0, low), vec(0, height), [Skia.Color(`${palette.bg}00`), Skia.Color(`${palette.bg}e6`)], null, TileMode.Clamp),
      );
      canvas.drawRect(Skia.XYWHRect(0, low, width, height - low), fade);

      const ink = Skia.Paint();
      ink.setAntiAlias(true);
      ink.setColor(Skia.Color(palette.ink));
      ink.setAlphaf(alpha * chromeIn);
      const top = insets.top + 26;
      const label = fonts.label;
      drawTracked(canvas, 'ALOFT STUDIO', label, 20, top, 1, ink);
      drawTracked(canvas, 'DEPTH FIELD', label, width / 2 - trackedWidth(label, 'DEPTH FIELD', 1) / 2, top, 1, ink);
      // The card the camera is on, numbered top right and named bottom left: the new one rises
      // in as the last one rises away.
      const base = height - insets.bottom - 22;
      const caption = (card: DepthCard, number: string, shown: number, rise: number) => {
        if (shown <= 0.01) return;
        ink.setAlphaf(alpha * chromeIn * shown);
        drawTracked(canvas, number, label, width - 20 - trackedWidth(label, number, 1), top + rise * 0.5, 1, ink);
        ink.setAlphaf(alpha * chromeIn * shown * 0.6);
        drawTracked(canvas, card.note.toUpperCase(), label, 20, base - 36 + rise, 1, ink);
        ink.setAlphaf(alpha * chromeIn * shown);
        canvas.drawText(card.title, 20, base + rise, ink, fonts.title);
      };
      if (last !== current || lastCount !== count) caption(last, lastCount, 1 - fresh, -fresh * CAPTION_RISE);
      caption(current, count, fresh, (1 - fresh) * CAPTION_RISE);
      ink.setAlphaf(alpha * chromeIn);

      // The switch: a track, and a lime knob that slides across as the lights turn over.
      const track = Skia.Paint();
      track.setAntiAlias(true);
      track.setStyle(PaintStyle.Stroke);
      track.setStrokeWidth(1);
      track.setColor(Skia.Color(palette.ink));
      track.setAlphaf(0.5 * alpha * chromeIn);
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(switchX - 22, switchY - 12, 44, 24), 12, 12), track);
      const knob = Skia.Paint();
      knob.setAntiAlias(true);
      knob.setColor(Skia.Color(accent));
      knob.setAlphaf(alpha * chromeIn);
      const knobX = switchX - 10 + 20 * t;
      canvas.drawCircle(knobX, switchY, 8, knob);
      knob.setColor(Skia.Color(CARD_INK));
      canvas.drawCircle(knobX, switchY, 2.2, knob);
    };
    if (reducedMotion) {
      if (t < 1) paintChrome(LIGHT, 1 - t);
      if (t > 0) paintChrome(DARK, t);
    } else {
      if (radius < reach) {
        canvas.save();
        canvas.clipPath(circle, ClipOp.Difference, true);
        paintChrome(LIGHT, 1);
        canvas.restore();
      }
      if (radius > 0) {
        canvas.save();
        canvas.clipPath(circle, ClipOp.Intersect, true);
        paintChrome(DARK, 1);
        canvas.restore();
      }
    }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel="Depth field gallery"
        accessibilityHint="Drag to fly through the cards; tap a card to fly to it"
        accessibilityActions={[{ name: 'activate', label: 'Turn the lights over' }]}
        onAccessibilityAction={() => scheduleOnUI(turn, true)}
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
