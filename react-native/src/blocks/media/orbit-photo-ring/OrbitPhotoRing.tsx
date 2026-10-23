import {
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
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawToy, type ToyShape } from './OrbitPhotoRingToy';

/**
 * One camera body: its name, a short spec line, its proportions and colours, and the prints it
 * deals into the ring. Every number is in points of a 402 pt wide screen.
 */
export type OrbitCamera = {
  name: string;
  spec: string;
  /** Body width, height and depth. */
  body: [number, number, number];
  /** The prism hump (or hood) on top: width, height, depth. Zero height for none. */
  hump: [number, number, number];
  /** Lens radius and how far it stands out of the body. */
  lens: [number, number];
  /** Where the lens sits on the front, from the middle of the body. */
  lensAt: [number, number];
  /** 1 for a rangefinder with two finder windows on the front plate. */
  windows: number;
  /** Share of the body's height covered by the metal top plate. */
  plate: number;
  /** Colours as #rrggbb. */
  leather: string;
  metal: string;
  barrel: string;
  accent: string;
  glass: string;
  /** Tumble style: the glossy shell on top and the satin body under it, as #rrggbb. */
  toyShell?: string;
  toyBody?: string;
  /** Unsplash photo ids for its prints. */
  photos: string[];
};

/** How the prints orbit: one tilted ring, a close ring around a tumbling camera, or two crossed rings. */
export type OrbitRingStyle = 'ring' | 'tumble' | 'gyro';

type Props = {
  cameras?: OrbitCamera[];
  /** The style the block opens with; the switcher at the top changes it, morphing smoothly. */
  initialRingStyle?: OrbitRingStyle;
  /** Shows the Ring · Tumble · Gyro switcher at the top. */
  showStylePicker?: boolean;
  /** Called with the style chosen in the switcher. */
  onStyleChange?: (style: OrbitRingStyle) => void;
  /** Plays a loop of LOOP seconds: idle spin, a flick, camera changes, back to the first. */
  autoPlay?: boolean;
  background?: string;
  textColor?: string;
  /** Called with the index of a camera when it is chosen. */
  onSelect?: (index: number) => void;
};

// Length of the self-playing loop, in seconds. It starts and ends on the first camera, with the
// ring turned by exactly one whole turn, so one period records as a seamless loop.
const LOOP = 16;
// Script of the loop: two flicks (time in s, release strength in radians of throw) and three
// camera changes (time in s, cameras forward from the one the loop started on).
const FLICKS: [number, number][] = [
  [1.8, 2.2],
  [10.4, -1.3],
];
const SWITCHES: [number, number][] = [
  [4.6, 1],
  [8.2, 2],
  [12.6, 0],
];
// A scripted flick: the finger speeds up over FLICK_DRAG s, lets go, and the ring coasts down
// with a time constant of COAST s. A user's throw coasts the same way.
const FLICK_DRAG = 0.28;
const COAST = 0.7;
// The idle spin, in rad/s, chosen so that the loop adds up to exactly one turn.
const IDLE_SPIN = (() => {
  let thrown = 0;
  for (const [, a] of FLICKS) thrown += a + (a * FLICK_DRAG) / (2 * COAST);
  return (Math.PI * 2 - thrown) / LOOP;
})();
// After a touch the loop waits this long, in seconds, before it plays again.
const USER_HOLD = 3.5;

// The scene, designed on a 402 pt wide screen and scaled to the real width. A pinhole camera with
// a focal length of FOCAL pt looks at the body standing DEPTH pt away.
const DESIGN_WIDTH = 402;
const FOCAL = 700;
const DEPTH = 700;
// The middle of the body, as a share of the height between the safe areas.
const BODY_AT = 0.41;
// The body rests turned a little to show its right side, and seen a little from above (degrees).
const REST_YAW = 28;
const REST_PITCH = 15;
const REST_ROLL = -2;
// How far the body turns with the ring's speed (degrees) and the speed that turns it ~76% of that.
const YAW_GAIN = 26;
const YAW_SPEED = 2.4;
// Springs on the body's turn and tilt: stiffness and damping per second, unit mass.
const TURN_STIFFNESS = 90;
const TURN_DAMPING = 11;
// Vertical drags tilt the body and the ring, rubber-banded: degrees per point, and the travel at
// which the band has taken half.
const TILT_PER_PT = 0.11;
const TILT_BAND = 140;
// The body bobs by BOB pt, four times a loop.
const BOB = 3;

// The ring styles, each a vector of numbers so that one morphs into another on the UI thread:
// the ring's radius, how far below the body's middle its centre sits, its tilt towards the viewer
// and its roll in the picture plane (degrees); the prints' width and how far they turn towards
// the outside at the sides (degrees); the body's scale; how much the body tumbles (0…1); how far
// the prints split into two rings rolled ±GYRO_ROLL° (0…1); which way the second ring spins; and how
// far the prints lean with the ring's roll instead of each at its own angle (0…1); and how far the
// body and its ring sit above the usual place (pt).
const STYLES: OrbitRingStyle[] = ['ring', 'tumble', 'gyro'];
const STYLE_LABELS = ['Ring', 'Tumble', 'Gyro'];
const S_RADIUS = 0;
const S_DROP = 1;
const S_TILT = 2;
const S_ROLL = 3;
const S_PRINT = 4;
const S_TURN = 5;
const S_BODY = 6;
const S_TUMBLE = 7;
const S_SPLIT = 8;
const S_DIR = 9;
const S_LEAN = 10;
const S_RISE = 11;
const STYLE_SHAPES: number[][] = [
  // Ring: a wide, gently tilted halo around a camera at rest.
  [206, 14, 21, -9, 74, 28, 1, 0, 0, 1, 0, 0],
  // Tumble: a glossy toy camera turning round with a wide ring of polaroids. The ring is sunk below the
  // body and its front edge raised, so the front prints sweep across the lens while the back ones pass
  // low under the body; it is rolled so its left side hangs lower than its right, and every print
  // leans with it. The whole scene sits higher to leave the ring room below.
  [200, 155, -20, -16, 76, 40, 1.18, 1, 0, 1, 1, 72],
  // Gyro: two rings of five crossing like a gyroscope, spinning against each other.
  [188, 6, 64, 0, 66, 18, 0.88, 0, 1, -1, 0, 0],
];
const TUMBLE_STYLE = 1;
const GYRO_ROLL = 34;
// A change of style morphs every number over STYLE_TIME s on an ease-in-out curve.
const STYLE_TIME = 0.9;
// Tumbling, the camera and its ring turn round together, the front of both travelling left to right:
// whole turns over these spans of the loop (start s, end s) on an ease-in-out curve, between the
// camera changes; between them the ring rests and the body only sways TUMBLE_SWAY° four times a loop.
// The body turns about an axis leaning back and to the side, so halfway round it shows its base.
// The turns are locked to the clock, so a period records as a seamless loop.
const TUMBLE_TURNS: [number, number][] = [
  [0.4, 4.2],
  [9.7, 12.5],
];
const TUMBLE_AXIS = [0.1, 1, 0.24];
// After a touch the body comes back round to face the viewer at this rate (1/s).
const TUMBLE_HOME = 2.6;
const TUMBLE_SWAY = 5;
// The toy faces the viewer more squarely than the box camera: its rest turn is this much less (°).
const TOY_YAW = 20;
// Each print floats by PRINT_FLOAT pt, twice a loop.
const PRINT_FLOAT = 4;
const PRINT_ROLLS = [-8, 6, -12, 4, 10, -5, 8, -10, 3, -6, 9, -3];
// Prints behind the body darken up to BACK_SHADE and blur up to BACK_BLUR px.
const BACK_SHADE = 0.62;
const BACK_BLUR = 3.2;
// Finger travel follows the front prints. Throws are capped, in rad/s, and a throw faster than
// FLICK_TICK rad/s ends with a light haptic.
const MAX_SPIN = 12;
const FLICK_TICK = 2;

// A camera change: the body spins once (SPIN_TIME s), morphing into the next between 30% and 70%
// of the turn while it dips; the prints fly off (outward and down, staggered) and the new ones
// are shot out of the lens to their places, one after another.
const SPIN_TIME = 1.1;
const SPIN_DIP = 0.14;
const SPIN_LIFT = 18;
const OUT_TIME = 0.42;
const OUT_STAGGER = 0.025;
const IN_DELAY = 0.35;
const IN_STAGGER = 0.07;
const IN_TIME = 0.75;
// A new change waits until the deal is done.
const DEAL_TIME = IN_DELAY + IN_STAGGER * 9 + IN_TIME;
// Tapping the body fires the shutter: the glass flashes for FLASH_TIME s and the body jumps back.
const FLASH_TIME = 0.45;
const SHUTTER_PUNCH = 0.035;

// The bar of camera thumbnails: spacing, thumbnail width, and its middle above the safe area.
const BAR_SPACING = 82;
const THUMB_WIDTH = 46;
const BAR_ABOVE = 64;
const BAR_STIFFNESS = 200;
const BAR_DAMPING = 24;
// A drag that starts this close to the bar's middle line scrolls the bar instead of spinning the ring (pt).
const BAR_TOUCH = 44;
// The bar scrolls freely: a flick coasts down with a time constant of BAR_COAST s, flicks are capped at
// BAR_MAX_SPEED cameras per second, and it may be pulled BAR_RUBBER cameras past either end before it
// springs back.
const BAR_COAST = 0.33;
const BAR_MAX_SPEED = 30;
const BAR_RUBBER = 0.45;

const LEATHER_GRAIN = 0.42;
const IMAGE_FADE = 0.35;
const DISPLAY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const MONO = process.env.EXPO_OS === 'ios' ? 'Menlo' : 'monospace';
// Light comes from the upper left, in front.
const LIGHT = (() => {
  const l = [-0.45, -0.75, -0.55];
  const n = Math.hypot(l[0], l[1], l[2]);
  return [l[0] / n, l[1] / n, l[2] / n];
})();

const photo = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=300&h=340&q=80`;

const CAMERAS: OrbitCamera[] = [
  {
    name: 'KDX',
    spec: 'SLR · 35 MM · F/1.8 · 1/1000',
    body: [224, 124, 70],
    hump: [92, 34, 52],
    lens: [40, 38],
    lensAt: [0, 10],
    windows: 0,
    plate: 0.3,
    leather: '#7a2e1c',
    metal: '#cbc9c3',
    barrel: '#19191b',
    accent: '#dc3428',
    glass: '#33386b',
    toyShell: '#8a2e14',
    toyBody: '#252238',
    photos: [
      'photo-1590486803833-1c5dc8ddd4c8',
      'photo-1569346276519-709519eeaa51',
      'photo-1568190002605-b51fa38eac46',
      'photo-1518676590629-3dcbd9c5a5c9',
      'photo-1597157153515-028fa3d4bd69',
      'photo-1561871733-40a3338b8cb4',
      'photo-1643138874737-33babf17f0a8',
      'photo-1598461336473-b0cc035d4e35',
      'photo-1603118258032-aea76c546616',
      'photo-1452587925148-ce544e77e70d',
    ],
  },
  {
    name: 'ORT4',
    spec: 'RANGEFINDER · F/2 · 1/500',
    body: [238, 110, 62],
    hump: [0, 0, 40],
    lens: [34, 30],
    lensAt: [-16, 12],
    windows: 1,
    plate: 0.34,
    leather: '#9c5a2c',
    metal: '#2b2b2e',
    barrel: '#1d1d20',
    accent: '#ec8a2e',
    glass: '#28505a',
    toyShell: '#1f1f22',
    toyBody: '#7b4426',
    photos: [
      'photo-1623475329493-889804e377f8',
      'photo-1613749742342-5abdaa0d0af3',
      'photo-1649366735070-8e78a4f86f31',
      'photo-1617618482053-36e9280bbf5a',
      'photo-1622297118224-2a816821a174',
      'photo-1570385404967-fe4e1b48454b',
      'photo-1542388481-52d5a10ae242',
      'photo-1714201623945-0079e2150240',
      'photo-1690023430523-a298e78ab6fd',
      'photo-1602230168052-741c2b79ea91',
    ],
  },
  {
    name: 'ADR3',
    spec: 'HALF FRAME · F/2.8 · 1/250',
    body: [206, 128, 74],
    hump: [80, 30, 50],
    lens: [42, 50],
    lensAt: [0, 12],
    windows: 0,
    plate: 0.28,
    leather: '#424b31',
    metal: '#c8c3b6',
    barrel: '#202020',
    accent: '#f0bd40',
    glass: '#4f2d60',
    toyShell: '#bdb9ae',
    toyBody: '#3c4530',
    photos: [
      'photo-1573455494060-c5595004fb6c',
      'photo-1549693578-d683be217e58',
      'photo-1480796927426-f609979314bd',
      'photo-1564284369929-026ba231f89b',
      'photo-1542931287-023b922fa89b',
      'photo-1561503972-839d0c56de17',
      'photo-1525230071276-4a87f42f469e',
      'photo-1601042879364-f3947d3f9c16',
      'photo-1498036882173-b41c28a8ba34',
      'photo-1573455494057-12684d151bf4',
    ],
  },
  {
    name: 'MRV6',
    spec: '6×6 · F/3.5 · 1/400',
    body: [196, 146, 84],
    hump: [96, 18, 70],
    lens: [46, 40],
    lensAt: [0, 16],
    windows: 0,
    plate: 0.22,
    leather: '#1c1c1f',
    metal: '#bcbdc2',
    barrel: '#141416',
    accent: '#d8343c',
    glass: '#2c3c66',
    toyShell: '#a82429',
    toyBody: '#1c1c20',
    photos: [
      'photo-1605437241278-c1806d14a4d9',
      'photo-1620584899131-a5ff5f8fbb03',
      'photo-1633014041037-f5446fb4ce99',
      'photo-1620584898989-d39f7f9ed1b7',
      'photo-1694678505383-676d78ea3b96',
      'photo-1515569067071-ec3b51335dd0',
      'photo-1708805282695-ef186db20192',
      'photo-1732357624591-f2137085659b',
      'photo-1708805283017-c662be2c7a44',
      'photo-1520340356584-f9917d1eea6f',
    ],
  },
];

// A camera flattened to numbers, so two of them can be morphed into each other on the UI thread.
// Indices into that vector:
const V_W = 0;
const V_H = 1;
const V_D = 2;
const V_HUMP_W = 3;
const V_HUMP_H = 4;
const V_HUMP_D = 5;
const V_LENS_R = 6;
const V_LENS_L = 7;
const V_LENS_X = 8;
const V_LENS_Y = 9;
const V_WINDOWS = 10;
const V_PLATE = 11;
const V_LEATHER = 12;
const V_METAL = 15;
const V_BARREL = 18;
const V_ACCENT = 21;
const V_GLASS = 24;
const V_SHELL = 27;
const V_TOY_BODY = 30;

function hexRgb(hex: string) {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

function toVector(c: OrbitCamera) {
  return [
    ...c.body,
    ...c.hump,
    ...c.lens,
    ...c.lensAt,
    c.windows,
    c.plate,
    ...hexRgb(c.leather),
    ...hexRgb(c.metal),
    ...hexRgb(c.barrel),
    ...hexRgb(c.accent),
    ...hexRgb(c.glass),
    ...hexRgb(c.toyShell ?? c.metal),
    ...hexRgb(c.toyBody ?? c.leather),
  ];
}

// Pebbled leather: bright pebbles on a darker ground, in the face's own coordinates so the grain
// turns and foreshortens with the body.
const LEATHER = Skia.RuntimeEffect.Make(`
uniform float3 tone;
uniform float grain;

float2 hash(float2 p) {
  return fract(sin(float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)))) * 43758.5453);
}

half4 main(float2 xy) {
  float2 g = xy * grain;
  float2 i = floor(g);
  float2 f = fract(g);
  float d = 8.0;
  for (int y = -1; y <= 1; y++) {
    for (int x = -1; x <= 1; x++) {
      float2 o = float2(float(x), float(y));
      float2 r = o + hash(i + o) - f;
      d = min(d, dot(r, r));
    }
  }
  float pebble = 1.0 - smoothstep(0.04, 0.6, d);
  return half4(half3(tone * (0.78 + 0.34 * pebble)), 1.0);
}`);

type Loaded = { image: SkImage; at: number };
type Stage = { width: number; height: number; top: number; bottom: number };
type View3 = { f: number; cx: number; cy: number };
type Pose = { x: number; y: number; z: number; s: number; r: number[] };

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function easeInOut(p: number) {
  'worklet';
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}

function easeOut(p: number) {
  'worklet';
  return 1 - Math.pow(1 - p, 3);
}

function easeIn(p: number) {
  'worklet';
  return p * p * p;
}

function backOut(p: number) {
  'worklet';
  const c = 1.5;
  const q = p - 1;
  return 1 + (c + 1) * q * q * q + c * q * q;
}

function smooth(p: number) {
  'worklet';
  return p * p * (3 - 2 * p);
}

/** Rubber band: follows `x` at first, then gives less and less, never passing `band`. */
function rubber(x: number, band: number) {
  'worklet';
  return Math.sign(x) * band * (1 - 1 / (Math.abs(x) / band + 1));
}

/** The loop's turn of the ring at `t` seconds into it, beyond where it started. */
function flickTurn(t: number, at: number, throwBy: number) {
  'worklet';
  if (t <= at) return 0;
  const drag = (throwBy * FLICK_DRAG) / (2 * COAST);
  if (t < at + FLICK_DRAG) {
    const s = (t - at) / FLICK_DRAG;
    return drag * s * s;
  }
  return drag + throwBy * (1 - Math.exp(-(t - at - FLICK_DRAG) / COAST));
}

function scriptTurn(t: number, reduced: boolean, style: number) {
  'worklet';
  if (reduced) return 0;
  if (style === TUMBLE_STYLE) {
    let turned = 0;
    for (let i = 0; i < TUMBLE_TURNS.length; i += 1) {
      const [start, end] = TUMBLE_TURNS[i];
      turned += Math.PI * 2 * easeInOut(clamp01((t - start) / (end - start)));
    }
    return turned;
  }
  let turn = IDLE_SPIN * t;
  for (let i = 0; i < FLICKS.length; i += 1) turn += flickTurn(t, FLICKS[i][0], FLICKS[i][1]);
  return turn;
}

/** 3×3 row-major product. */
function mul3(a: number[], b: number[]) {
  'worklet';
  const out = new Array(9);
  for (let r = 0; r < 3; r += 1) {
    for (let c = 0; c < 3; c += 1) {
      out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
    }
  }
  return out;
}

/**
 * Rotation of an object: rolled in the picture plane, turned about the vertical (positive shows its
 * right side) and tipped about the horizontal (positive shows its top). Angles in radians; the
 * screen's y points down and z away from the viewer.
 */
function rotation(yaw: number, pitch: number, roll: number) {
  'worklet';
  const cr = Math.cos(roll);
  const sr = Math.sin(roll);
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const cp = Math.cos(pitch);
  const sp = Math.sin(pitch);
  const rz = [cr, -sr, 0, sr, cr, 0, 0, 0, 1];
  const ry = [cy, 0, sy, 0, 1, 0, -sy, 0, cy];
  const rx = [1, 0, 0, 0, cp, -sp, 0, sp, cp];
  return mul3(rx, mul3(ry, rz));
}

function turn(m: number[], x: number, y: number, z: number) {
  'worklet';
  return [m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z];
}

/** A point of an object, from its own coordinates to the eye's. */
function place(pose: Pose, x: number, y: number, z: number) {
  'worklet';
  const p = turn(pose.r, x * pose.s, y * pose.s, z * pose.s);
  return [pose.x + p[0], pose.y + p[1], pose.z + p[2]];
}

/**
 * The perspective matrix that draws a flat face: a point (u, v) of the face's own drawing lands
 * where the eye sees O + u·U + v·V. `null` when the face is behind the eye.
 */
function faceMatrix(view: View3, o: number[], u: number[], v: number[]) {
  'worklet';
  const { f, cx, cy } = view;
  const z = o[2];
  if (z <= 1) return null;
  return [
    (f * u[0] + cx * u[2]) / z,
    (f * v[0] + cx * v[2]) / z,
    (f * o[0] + cx * o[2]) / z,
    (f * u[1] + cy * u[2]) / z,
    (f * v[1] + cy * v[2]) / z,
    (f * o[1] + cy * o[2]) / z,
    u[2] / z,
    v[2] / z,
    1,
  ];
}

type Face = { m: number[] | null; visible: boolean; light: number };

/** A face of an object: centre c, its drawing's axes u and v, its outward normal n. */
function face(view: View3, pose: Pose, c: number[], u: number[], v: number[], n: number[]): Face {
  'worklet';
  const o = place(pose, c[0], c[1], c[2]);
  const uu = turn(pose.r, u[0] * pose.s, u[1] * pose.s, u[2] * pose.s);
  const vv = turn(pose.r, v[0] * pose.s, v[1] * pose.s, v[2] * pose.s);
  const nn = turn(pose.r, n[0], n[1], n[2]);
  const facing = nn[0] * o[0] + nn[1] * o[1] + nn[2] * o[2];
  const lit = Math.max(0, nn[0] * LIGHT[0] + nn[1] * LIGHT[1] + nn[2] * LIGHT[2]);
  return { m: faceMatrix(view, o, uu, vv), visible: facing < 0, light: 0.3 + 0.7 * lit };
}

function mixVector(a: number[], b: number[], t: number) {
  'worklet';
  const out = new Array(a.length);
  for (let i = 0; i < a.length; i += 1) out[i] = a[i] + (b[i] - a[i]) * t;
  return out;
}

/** A colour of the camera vector at `i`, brightened by `k`. */
function tone(v: number[], i: number, k: number, a: number) {
  'worklet';
  return Float32Array.of(clamp01(v[i] * k), clamp01(v[i + 1] * k), clamp01(v[i + 2] * k), a);
}

/** Brushed chrome (or black paint) along a face's vertical: bright, dark, bright, dark. */
function metal(v: number[], y0: number, y1: number) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(
    vec(0, y0),
    vec(0, y1),
    [tone(v, V_METAL, 1.3, 1), tone(v, V_METAL, 0.9, 1), tone(v, V_METAL, 1.12, 1), tone(v, V_METAL, 0.6, 1), tone(v, V_METAL, 0.85, 1)],
    [0, 0.28, 0.55, 0.82, 1],
    TileMode.Clamp,
  );
}

function shadeRect(canvas: SkCanvas, x: number, y: number, w: number, h: number, light: number) {
  'worklet';
  const dark = Skia.Paint();
  dark.setColor(Skia.Color('#000'));
  dark.setAlphaf((1 - light) * 0.72);
  canvas.drawRect(Skia.XYWHRect(x, y, w, h), dark);
}

/** Text with extra space between letters, set around `x` by `align` (0 left, 0.5 middle, 1 right). */
function drawTracked(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, tracking: number, align: number, color: Float32Array) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(color);
  let width = 0;
  const widths: number[] = [];
  for (let i = 0; i < text.length; i += 1) {
    // Skia measures a lone space as nothing, so a space is given a third of the size.
    const w = text[i] === ' ' ? font.getSize() * 0.34 : font.measureText(text[i]).width;
    widths.push(w);
    width += w + (i < text.length - 1 ? tracking : 0);
  }
  let at = x - width * align;
  for (let i = 0; i < text.length; i += 1) {
    canvas.drawText(text[i], at, y, paint, font);
    at += widths[i] + tracking;
  }
}

/**
 * A face of the body: leather between a metal top plate and a thin metal foot, or plain metal on
 * the top and bottom. The front also carries the finder windows, the accent dot and the name.
 */
function paintBodyFace(canvas: SkCanvas, v: number[], kind: number, w: number, h: number, light: number, detail: boolean, label: string, font: SkFont) {
  'worklet';
  const x0 = -w / 2;
  const y0 = -h / 2;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  if (kind >= 4) {
    // Top or bottom: a metal plate, its gradient running front to back.
    paint.setShader(metal(v, y0, y0 + h));
    canvas.drawRect(Skia.XYWHRect(x0, y0, w, h), paint);
    shadeRect(canvas, x0, y0, w, h, light);
    return;
  }
  const plate = h * v[V_PLATE];
  if (detail && LEATHER) {
    paint.setShader(LEATHER.makeShader([v[V_LEATHER], v[V_LEATHER + 1], v[V_LEATHER + 2], LEATHER_GRAIN]));
  } else {
    paint.setColor(tone(v, V_LEATHER, 1, 1));
  }
  canvas.drawRect(Skia.XYWHRect(x0, y0, w, h), paint);
  const shine = Skia.Paint();
  shine.setAntiAlias(true);
  shine.setShader(metal(v, y0, y0 + plate));
  canvas.drawRect(Skia.XYWHRect(x0, y0, w, plate), shine);
  shine.setShader(metal(v, y0 + h - 7, y0 + h));
  canvas.drawRect(Skia.XYWHRect(x0, y0 + h - 7, w, 7), shine);
  // A dark seam where the leather meets the metal.
  const seam = Skia.Paint();
  seam.setColor(Skia.Color('#000'));
  seam.setAlphaf(0.45);
  canvas.drawRect(Skia.XYWHRect(x0, y0 + plate, w, 1.2), seam);
  canvas.drawRect(Skia.XYWHRect(x0, y0 + h - 8, w, 1), seam);

  if (kind === 1 && detail) {
    // The back: the film door's outline, the eyepiece on the top plate and a film window.
    const line = Skia.Paint();
    line.setAntiAlias(true);
    line.setStyle(1);
    line.setStrokeWidth(1.2);
    line.setColor(Skia.Color('#000'));
    line.setAlphaf(0.4);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + 12, y0 + plate + 7, w - 24, h - plate - 22), 5, 5), line);
    const eye = Skia.Paint();
    eye.setAntiAlias(true);
    eye.setColor(Skia.Color('#0e0e10'));
    const ex = w * 0.22;
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(ex - 15, y0 + plate * 0.18, 30, plate * 0.64), 4, 4), eye);
    eye.setColor(tone(v, V_GLASS, 1.2, 1));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(ex - 9, y0 + plate * 0.3, 18, plate * 0.4), 2.5, 2.5), eye);
    const frame = Skia.Paint();
    frame.setAntiAlias(true);
    frame.setShader(metal(v, y0 + plate + 18, y0 + plate + 40));
    const fx = -w * 0.18;
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(fx - 20, y0 + plate + 18, 40, 22), 3, 3), frame);
    frame.setShader(null);
    frame.setColor(tone(v, V_ACCENT, 0.9, 0.85));
    canvas.drawRect(Skia.XYWHRect(fx - 15, y0 + plate + 23, 30, 12), frame);
  }
  if (kind === 0 && detail) {
    // Two finder windows on a rangefinder, fading in and out with the morph.
    const windows = v[V_WINDOWS];
    if (windows > 0.02) {
      for (const [cx, ww] of [
        [-w * 0.34, 34],
        [w * 0.3, 22],
      ]) {
        const r = Skia.RRectXY(Skia.XYWHRect(cx - ww / 2, y0 + plate * 0.24, ww, plate * 0.52), 3, 3);
        const glass = Skia.Paint();
        glass.setAntiAlias(true);
        glass.setShader(
          Skia.Shader.MakeLinearGradient(
            vec(cx - ww / 2, y0),
            vec(cx + ww / 2, y0 + plate),
            [tone(v, V_GLASS, 1.8, windows), tone(v, V_GLASS, 0.4, windows), tone(v, V_GLASS, 1.1, windows)],
            [0, 0.6, 1],
            TileMode.Clamp,
          ),
        );
        canvas.drawRRect(r, glass);
        const rim = Skia.Paint();
        rim.setAntiAlias(true);
        rim.setStyle(1);
        rim.setStrokeWidth(1.4);
        rim.setColor(Skia.Color('#000'));
        rim.setAlphaf(0.5 * windows);
        canvas.drawRRect(r, rim);
      }
    }
    // The accent dot and the engraved name on the top plate.
    const dot = Skia.Paint();
    dot.setAntiAlias(true);
    dot.setColor(tone(v, V_ACCENT, 1, 1));
    canvas.drawCircle(-w * 0.4, y0 + plate + (h - plate) * 0.22, 4.2, dot);
    dot.setColor(Skia.Color('#ffffff'));
    dot.setAlphaf(0.45);
    canvas.drawCircle(-w * 0.4 - 1.2, y0 + plate + (h - plate) * 0.22 - 1.2, 1.3, dot);
    const ink = v[V_METAL] + v[V_METAL + 1] + v[V_METAL + 2] > 1.5 ? Float32Array.of(0.1, 0.1, 0.11, 0.75) : Float32Array.of(0.85, 0.85, 0.83, 0.8);
    drawTracked(canvas, label, font, w * 0.44, y0 + plate * 0.66, 1.6, 1, ink);
  }
  shadeRect(canvas, x0, y0, w, h, light);
}

/** A metal box: the prism hump or the hood on top. Its front carries a small dark name plate. */
function paintHumpFace(canvas: SkCanvas, v: number[], kind: number, w: number, h: number, light: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(metal(v, -h / 2, h / 2));
  canvas.drawRect(Skia.XYWHRect(-w / 2, -h / 2, w, h), paint);
  if (kind === 0 && h > 14) {
    const plate = Skia.Paint();
    plate.setAntiAlias(true);
    plate.setColor(Skia.Color('#101012'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-w * 0.3, -h * 0.18, w * 0.6, h * 0.42), 2, 2), plate);
    plate.setColor(tone(v, V_ACCENT, 1, 0.9));
    canvas.drawRect(Skia.XYWHRect(-w * 0.22, h * 0.02, w * 0.44, 1.4), plate);
  }
  shadeRect(canvas, -w / 2, -h / 2, w, h, light);
}

/**
 * A box of the camera, `style` 0 for the body and 1 for the hump. Only the faces turned towards
 * the eye are drawn; a box is convex, so they never overlap.
 */
function drawBox(
  canvas: SkCanvas,
  view: View3,
  pose: Pose,
  v: number[],
  at: number[],
  w: number,
  h: number,
  d: number,
  style: number,
  detail: boolean,
  label: string,
  font: SkFont,
) {
  'worklet';
  const [x, y, z] = at;
  // front, back, right, left, top, bottom: centre, u, v, normal, drawing size.
  const faces: [number[], number[], number[], number[], number, number][] = [
    [[x, y, z - d / 2], [1, 0, 0], [0, 1, 0], [0, 0, -1], w, h],
    [[x, y, z + d / 2], [-1, 0, 0], [0, 1, 0], [0, 0, 1], w, h],
    [[x + w / 2, y, z], [0, 0, 1], [0, 1, 0], [1, 0, 0], d, h],
    [[x - w / 2, y, z], [0, 0, -1], [0, 1, 0], [-1, 0, 0], d, h],
    [[x, y - h / 2, z], [1, 0, 0], [0, 0, -1], [0, -1, 0], w, d],
    [[x, y + h / 2, z], [1, 0, 0], [0, 0, 1], [0, 1, 0], w, d],
  ];
  for (let k = 0; k < faces.length; k += 1) {
    if (style === 1 && k === 5) continue;
    const [c, u, vv, n, fw, fh] = faces[k];
    const info = face(view, pose, c, u, vv, n);
    if (!info.visible || !info.m) continue;
    canvas.save();
    canvas.concat(Skia.Matrix(info.m));
    if (style === 0) paintBodyFace(canvas, v, k, fw, fh, info.light, detail, label, font);
    else paintHumpFace(canvas, v, k, fw, fh, info.light);
    canvas.restore();
  }
}

/** A disc on a plane parallel to a face: centre, the plane's axes and normal. */
function discFace(view: View3, pose: Pose, c: number[], u: number[], v: number[], n: number[]) {
  'worklet';
  return face(view, pose, c, u, v, n);
}

/** A knob standing on the top plate: slices of a cylinder, then a knurled cap. */
function drawKnob(canvas: SkCanvas, view: View3, pose: Pose, v: number[], x: number, top: number, z: number, r: number, h: number, detail: boolean) {
  'worklet';
  const slices = detail ? 4 : 2;
  for (let i = 0; i <= slices; i += 1) {
    const s = (h * i) / slices;
    const info = discFace(view, pose, [x, top - s, z], [1, 0, 0], [0, 0, -1], [0, -1, 0]);
    if (!info.m) continue;
    canvas.save();
    canvas.concat(Skia.Matrix(info.m));
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    if (i < slices) {
      paint.setColor(tone(v, V_METAL, i % 2 === 0 ? 0.55 : 0.75, 1));
      canvas.drawCircle(0, 0, r, paint);
    } else {
      paint.setShader(Skia.Shader.MakeRadialGradient(vec(-r * 0.3, -r * 0.3), r * 1.3, [tone(v, V_METAL, 1.35, 1), tone(v, V_METAL, 0.7, 1)], null, TileMode.Clamp));
      canvas.drawCircle(0, 0, r, paint);
      if (detail) {
        const tick = Skia.Paint();
        tick.setAntiAlias(true);
        tick.setColor(Skia.Color('#000'));
        tick.setAlphaf(0.35);
        tick.setStrokeWidth(0.8);
        for (let k = 0; k < 18; k += 1) {
          const a = (k / 18) * Math.PI * 2;
          canvas.drawLine(Math.cos(a) * r * 0.72, Math.sin(a) * r * 0.72, Math.cos(a) * r * 0.95, Math.sin(a) * r * 0.95, tick);
        }
      }
    }
    canvas.restore();
  }
}

/**
 * The lens: a metal mount, a ribbed black barrel built from slices of a cylinder, a chrome front
 * ring and the glass with its reflections. Drawn from the body outwards, or the other way round
 * when the lens points away.
 */
function drawLens(canvas: SkCanvas, view: View3, pose: Pose, v: number[], outward: boolean, detail: boolean, flash: number) {
  'worklet';
  const r = v[V_LENS_R];
  const length = v[V_LENS_L];
  const lx = v[V_LENS_X];
  const ly = v[V_LENS_Y];
  const front = -v[V_D] / 2;
  const slices = detail ? Math.max(6, Math.round(length / 3)) : 4;
  const order: number[] = [];
  for (let i = 0; i <= slices + 1; i += 1) order.push(i);
  if (!outward) order.reverse();
  for (const i of order) {
    // Slice 0 is the mount; slices 1…n the barrel; n + 1 the front with the glass.
    const s = i === 0 ? 2 : i === slices + 1 ? length : 2 + ((length - 2) * i) / (slices + 1);
    const info = discFace(view, pose, [lx, ly, front - s], [1, 0, 0], [0, 1, 0], [0, 0, -1]);
    if (!info.m) continue;
    canvas.save();
    canvas.concat(Skia.Matrix(info.m));
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    if (i === 0) {
      paint.setColor(tone(v, V_METAL, 0.8, 1));
      canvas.drawCircle(0, 0, r * 1.16, paint);
    } else if (i <= slices) {
      const nearFront = i >= slices - 1;
      if (nearFront) {
        paint.setColor(tone(v, V_METAL, i % 2 === 0 ? 0.75 : 1, 1));
      } else {
        // A sheen across each rib, so the barrel reads as a lit cylinder when seen from the side.
        const k = i % 2 === 0 ? 1 : 1.5;
        paint.setShader(
          Skia.Shader.MakeLinearGradient(vec(-r, -r), vec(r, r), [tone(v, V_BARREL, 3.4 * k, 1), tone(v, V_BARREL, 1.3 * k, 1), tone(v, V_BARREL, 0.8 * k, 1)], [0, 0.5, 1], TileMode.Clamp),
        );
      }
      canvas.drawCircle(0, 0, r * (nearFront ? 1.02 : 0.98), paint);
    } else {
      paint.setShader(Skia.Shader.MakeLinearGradient(vec(-r, -r), vec(r, r), [tone(v, V_METAL, 1.35, 1), tone(v, V_METAL, 0.65, 1), tone(v, V_METAL, 1.1, 1)], [0, 0.55, 1], TileMode.Clamp));
      canvas.drawCircle(0, 0, r * 1.02, paint);
      const black = Skia.Paint();
      black.setAntiAlias(true);
      black.setColor(Skia.Color('#0b0b0d'));
      canvas.drawCircle(0, 0, r * 0.86, black);
      if (detail) {
        const tick = Skia.Paint();
        tick.setAntiAlias(true);
        tick.setColor(Skia.Color('#ffffff'));
        tick.setAlphaf(0.35);
        tick.setStrokeWidth(0.7);
        for (let k = 0; k < 28; k += 1) {
          const a = (k / 28) * Math.PI * 2;
          const long = k % 4 === 0 ? 0.74 : 0.79;
          canvas.drawLine(Math.cos(a) * r * long, Math.sin(a) * r * long, Math.cos(a) * r * 0.84, Math.sin(a) * r * 0.84, tick);
        }
      }
      // The glass: deep colour falling to black, two reflections, and the shutter's flash.
      const glass = Skia.Paint();
      glass.setAntiAlias(true);
      glass.setShader(
        Skia.Shader.MakeRadialGradient(
          vec(-r * 0.18, -r * 0.22),
          r * 0.8,
          [tone(v, V_GLASS, 2.1, 1), tone(v, V_GLASS, 0.9, 1), Float32Array.of(0.02, 0.02, 0.03, 1)],
          [0, 0.45, 1],
          TileMode.Clamp,
        ),
      );
      canvas.drawCircle(0, 0, r * 0.66, glass);
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(1);
      ring.setStrokeWidth(1);
      ring.setColor(tone(v, V_GLASS, 2.4, 0.5));
      canvas.drawCircle(0, 0, r * 0.4, ring);
      const glint = Skia.Paint();
      glint.setAntiAlias(true);
      glint.setColor(Skia.Color('#ffffff'));
      glint.setAlphaf(0.55);
      canvas.save();
      canvas.rotate(-35, -r * 0.26, -r * 0.3);
      canvas.drawOval(Skia.XYWHRect(-r * 0.26 - r * 0.2, -r * 0.3 - r * 0.08, r * 0.4, r * 0.16), glint);
      canvas.restore();
      glint.setAlphaf(0.4);
      canvas.drawCircle(r * 0.28, r * 0.24, r * 0.05, glint);
      if (flash > 0.01) {
        const burst = Skia.Paint();
        burst.setShader(Skia.Shader.MakeRadialGradient(vec(0, 0), r * 0.9, [Float32Array.of(1, 1, 1, flash), Float32Array.of(1, 0.97, 0.9, 0)], null, TileMode.Clamp));
        canvas.drawCircle(0, 0, r * 0.9, burst);
      }
    }
    if (i > 0) {
      // The barrel and the front ring take the light of the way the lens points.
      const dark = Skia.Paint();
      dark.setAntiAlias(true);
      dark.setColor(Skia.Color('#000'));
      dark.setAlphaf((1 - Math.max(info.light, i === slices + 1 ? 0.85 : 0)) * 0.72);
      canvas.drawCircle(0, 0, r * 1.02, dark);
    }
    canvas.restore();
  }
}

/** What stands on the top plate: the prism hump or hood, the speed dial with its release, the rewind knob. */
function drawTopParts(canvas: SkCanvas, view: View3, pose: Pose, v: number[], detail: boolean, label: string, font: SkFont) {
  'worklet';
  const w = v[V_W];
  const h = v[V_H];
  const d = v[V_D];
  const hh = v[V_HUMP_H];
  if (hh > 0.8) drawBox(canvas, view, pose, v, [0, -h / 2 - hh / 2, d * 0.04], v[V_HUMP_W], hh, v[V_HUMP_D], 1, detail, label, font);
  drawKnob(canvas, view, pose, v, w * 0.36, -h / 2, -d * 0.05, Math.min(15, w * 0.065), 6, detail);
  drawKnob(canvas, view, pose, v, w * 0.36, -h / 2 - 6, -d * 0.05, 5.5, 5, detail);
  drawKnob(canvas, view, pose, v, -w * 0.36, -h / 2, 0, Math.min(13, w * 0.058), 9, detail);
}

/**
 * The whole camera at `pose`: the parts on its top plate and the lens are drawn before or after
 * the body depending on whether they face the eye, which is all the depth sorting a box with a
 * knob on top and a lens in front needs.
 */
function drawCamera(canvas: SkCanvas, view: View3, pose: Pose, v: number[], detail: boolean, label: string, font: SkFont, flash: number) {
  'worklet';
  const w = v[V_W];
  const h = v[V_H];
  const d = v[V_D];
  const front = face(view, pose, [0, 0, -d / 2], [1, 0, 0], [0, 1, 0], [0, 0, -1]);
  const top = face(view, pose, [0, -h / 2, 0], [1, 0, 0], [0, 0, -1], [0, -1, 0]);
  if (!front.visible) drawLens(canvas, view, pose, v, false, detail, flash);
  if (!top.visible) drawTopParts(canvas, view, pose, v, detail, label, font);
  drawBox(canvas, view, pose, v, [0, 0, 0], w, h, d, 0, detail, label, font);
  if (top.visible) drawTopParts(canvas, view, pose, v, detail, label, font);
  if (front.visible) drawLens(canvas, view, pose, v, true, detail, flash);
}

/** A print in the ring, or flying in or out of it. */
type PrintState = { set: number; index: number; pos: number[]; r: number[]; s: number; alpha: number; shade: number; blur: number };

/** Sort order for prints: the farthest first. */
function farthestFirst(a: PrintState, b: PrintState) {
  'worklet';
  return b.pos[2] - a.pos[2];
}

/** Where slot `theta` of a ring (radius, tilt and roll in degrees, drop) is, around the body's middle. */
function ringPoint(theta: number, radius: number, tilt: number, roll: number, drop: number) {
  'worklet';
  const x = radius * Math.sin(theta);
  const z = -radius * Math.cos(theta);
  const t = (tilt * Math.PI) / 180;
  const y1 = -z * Math.sin(t);
  const z1 = z * Math.cos(t);
  const r = (roll * Math.PI) / 180;
  return [x * Math.cos(r) - y1 * Math.sin(r), x * Math.sin(r) + y1 * Math.cos(r) + drop, z1];
}

/** The style vector `t` of the way from `from` to style `to`. */
function styleMix(from: number[], to: number, t: number) {
  'worklet';
  const shape = STYLE_SHAPES[Math.min(STYLE_SHAPES.length - 1, Math.max(0, to))];
  const e = easeInOut(clamp01(t));
  const out = new Array(shape.length);
  for (let i = 0; i < shape.length; i += 1) out[i] = (from[i] ?? shape[i]) + (shape[i] - (from[i] ?? shape[i])) * e;
  return out;
}

/** An angle brought into (−π, π]. */
function wrapPi(a: number) {
  'worklet';
  const t = Math.PI * 2;
  return a - t * Math.round(a / t);
}

/** Rotation by `a` radians about the axis (x, y, z), normalised here (row-major). */
function axisRotation(x: number, y: number, z: number, a: number) {
  'worklet';
  const n = Math.hypot(x, y, z) || 1;
  const ux = x / n;
  const uy = y / n;
  const uz = z / n;
  const c = Math.cos(a);
  const s = Math.sin(a);
  const k = 1 - c;
  return [
    c + ux * ux * k,
    ux * uy * k - uz * s,
    ux * uz * k + uy * s,
    uy * ux * k + uz * s,
    c + uy * uy * k,
    uy * uz * k - ux * s,
    uz * ux * k - uy * s,
    uz * uy * k + ux * s,
    c + uz * uz * k,
  ];
}

/**
 * The tumble at `t` seconds into the loop, with the body turned `turned` radians round with its ring,
 * scaled by `weight` (0 none, 1 all of it). The turn is taken the short way round, so leaving the
 * style never spins the body back.
 */
function tumbleMatrix(t: number, turned: number, weight: number) {
  'worklet';
  const sway = ((TUMBLE_SWAY * Math.PI) / 180) * Math.sin((t / LOOP) * Math.PI * 8) * weight;
  const m = axisRotation(0.25, 1, 0.1, sway);
  return mul3(axisRotation(TUMBLE_AXIS[0], TUMBLE_AXIS[1], TUMBLE_AXIS[2], -wrapPi(turned) * weight), m);
}

/** The toy camera's measures and colours for a camera vector (see OrbitPhotoRingToy). */
function toyShape(v: number[]): ToyShape {
  'worklet';
  const w = v[V_W];
  const h = Math.max(v[V_H], w * 0.6);
  return {
    width: w,
    height: h,
    depth: Math.max(v[V_D], w * 0.42),
    shell: h * Math.max(0.3, v[V_PLATE] * 1.1),
    humpWidth: v[V_HUMP_W] * 0.72,
    humpHeight: v[V_HUMP_H] * 0.7,
    lensX: v[V_LENS_X] * 0.8 - w * 0.08,
    lensY: h * 0.17,
    lensRadius: v[V_LENS_R] * 1.35,
    lensLength: Math.max(v[V_LENS_L] * 0.8, w * 0.16),
    shellTone: [v[V_SHELL], v[V_SHELL + 1], v[V_SHELL + 2]],
    bodyTone: [v[V_TOY_BODY], v[V_TOY_BODY + 1], v[V_TOY_BODY + 2]],
    accentTone: [v[V_ACCENT], v[V_ACCENT + 1], v[V_ACCENT + 2]],
  };
}

/**
 * The camera as the style wants it: the box camera, the toy, or both cross-fading while the style
 * morphs (`toy` 0…1).
 */
function drawAnyCamera(canvas: SkCanvas, view: View3, pose: Pose, v: number[], toy: number, detail: boolean, label: string, font: SkFont, flash: number, bounds: number[]) {
  'worklet';
  if (toy < 0.99) {
    if (toy > 0.01) {
      const layer = Skia.Paint();
      layer.setAlphaf(1 - toy);
      canvas.saveLayer(layer, Skia.XYWHRect(bounds[0], bounds[1], bounds[2], bounds[3]));
    }
    drawCamera(canvas, view, pose, v, detail, label, font, flash);
    if (toy > 0.01) canvas.restore();
  }
  if (toy > 0.01) drawToy(canvas, view, pose, toyShape(v), toy, flash);
}

/** Finger travel per radian of turn: the speed of the front prints on screen. */
function travelPerRadian(radius: number, width: number) {
  'worklet';
  return Math.max(1, ((radius * FOCAL) / Math.max(1, DEPTH - radius)) * (width / DESIGN_WIDTH));
}

/**
 * A polaroid: a white card with a slightly tall photo, and on the wide bottom margin a caption in
 * pencil grey and a small ring mark. 100 × 126 units.
 */
function drawPrint(canvas: SkCanvas, view: View3, p: PrintState, loaded: Loaded | null, caption: string, font: SkFont, now: number) {
  'worklet';
  const k = p.s / 100;
  const o = [p.pos[0], p.pos[1], p.pos[2]];
  const u = turn(p.r, k, 0, 0);
  const v = turn(p.r, 0, k, 0);
  const n = turn(p.r, 0, 0, -1);
  const m = faceMatrix(view, o, u, v);
  if (!m) return;
  const facing = n[0] * o[0] + n[1] * o[1] + n[2] * o[2] < 0;
  const layered = p.alpha < 0.995 || p.blur > 0.25;
  if (layered) {
    let left = Infinity;
    let top = Infinity;
    let right = -Infinity;
    let bottom = -Infinity;
    for (const [cx, cy] of [
      [-50, -63],
      [50, -63],
      [50, 63],
      [-50, 63],
    ]) {
      const w = m[6] * cx + m[7] * cy + 1;
      const sx = (m[0] * cx + m[1] * cy + m[2]) / w;
      const sy = (m[3] * cx + m[4] * cy + m[5]) / w;
      left = Math.min(left, sx);
      right = Math.max(right, sx);
      top = Math.min(top, sy);
      bottom = Math.max(bottom, sy);
    }
    const pad = 2 + p.blur * 3;
    const layer = Skia.Paint();
    layer.setAlphaf(clamp01(p.alpha));
    if (p.blur > 0.25) layer.setImageFilter(Skia.ImageFilter.MakeBlur(p.blur, p.blur, TileMode.Decal, null));
    canvas.saveLayer(layer, Skia.XYWHRect(left - pad, top - pad, right - left + pad * 2, bottom - top + pad * 2));
  } else {
    canvas.save();
  }
  canvas.concat(Skia.Matrix(m));
  const card = Skia.RRectXY(Skia.XYWHRect(-50, -63, 100, 126), 2, 2);
  const paper = Skia.Paint();
  paper.setAntiAlias(true);
  paper.setColor(Skia.Color(facing ? '#f4f2ec' : '#d9d5cc'));
  canvas.drawRRect(card, paper);
  if (facing) {
    const window = Skia.XYWHRect(-44, -57, 88, 98);
    const ground = Skia.Paint();
    ground.setColor(Skia.Color('#26262a'));
    canvas.drawRect(window, ground);
    if (loaded) {
      const image = loaded.image;
      const iw = image.width();
      const ih = image.height();
      const sw = Math.min(iw, (ih * 88) / 98);
      const sh = (sw * 98) / 88;
      const art = Skia.Paint();
      art.setAntiAlias(true);
      art.setAlphaf(clamp01((now - loaded.at) / IMAGE_FADE));
      canvas.drawImageRectOptions(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), window, FilterMode.Linear, MipmapMode.Linear, art);
    }
    // A soft sheen across the gloss, and the caption in pencil grey.
    const sheen = Skia.Paint();
    sheen.setShader(
      Skia.Shader.MakeLinearGradient(vec(-44, -57), vec(44, 41), [Float32Array.of(1, 1, 1, 0.16), Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 0.06)], [0, 0.5, 1], TileMode.Clamp),
    );
    canvas.drawRect(window, sheen);
    const pencil = Skia.Paint();
    pencil.setAntiAlias(true);
    pencil.setColor(Float32Array.of(0.22, 0.21, 0.2, 0.7));
    canvas.drawText(caption, -43, 54.5, pencil, font);
    pencil.setStyle(1);
    pencil.setStrokeWidth(0.8);
    canvas.drawCircle(40, 52, 2.6, pencil);
  }
  if (p.shade > 0.004) {
    const dark = Skia.Paint();
    dark.setAntiAlias(true);
    dark.setColor(Skia.Color('#000'));
    dark.setAlphaf(p.shade);
    canvas.drawRRect(card, dark);
  }
  canvas.restore();
}

/**
 * The camera's name, letter by letter. While a change is under way (`t` its age in seconds, −1
 * for none) the old name's letters leave upwards and the new one's rise in from below, each a
 * little after the one before.
 */
function rollName(canvas: SkCanvas, text: string, font: SkFont, cx: number, y: number, t: number, incoming: boolean, reduced: boolean, ink: Float32Array) {
  'worklet';
  const tracking = 3;
  let total = 0;
  for (let i = 0; i < text.length; i += 1) total += font.measureText(text[i]).width + (i < text.length - 1 ? tracking : 0);
  let at = cx - total / 2;
  for (let i = 0; i < text.length; i += 1) {
    const w = font.measureText(text[i]).width;
    let dy = 0;
    let a = incoming ? 1 : 0;
    if (t >= 0) {
      if (incoming) {
        const q = clamp01((t - 0.22 - i * 0.045) / 0.38);
        dy = 30 * (1 - easeOut(q));
        a = q;
      } else {
        const q = clamp01((t - i * 0.045) / 0.28);
        dy = -30 * easeIn(q);
        a = 1 - q;
      }
    }
    if (reduced) dy = 0;
    if (a > 0.01) {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setColor(Float32Array.of(ink[0], ink[1], ink[2], a));
      canvas.drawText(text[i], at, y + dy, paint, font);
    }
    at += w + tracking;
  }
}

/** A colour matrix that keeps `s` of the colour and greys the rest. */
function saturation(s: number) {
  'worklet';
  const r = (1 - s) * 0.2126;
  const g = (1 - s) * 0.7152;
  const b = (1 - s) * 0.0722;
  return [r + s, g, b, 0, 0, r, g + s, b, 0, 0, r, g, b + s, 0, 0, 0, 0, 0, 1, 0];
}

/**
 * A retro camera hangs in the dark, drawn in true perspective in Skia, with ten polaroids orbiting
 * it on a tilted ring: the prints behind it shrink, darken and blur. Dragging sideways spins the
 * ring, a throw coasts, and the body turns with the spin; dragging up or down tilts the scene on a
 * rubber band. A bar of thumbnails swaps the body: it spins once while it morphs into the next
 * model, the prints fly off, and new ones are shot out of the lens. Tapping the body fires the
 * shutter.
 */
export function OrbitPhotoRing({
  cameras: camerasIn = CAMERAS,
  initialRingStyle = 'ring',
  showStylePicker = true,
  onStyleChange,
  autoPlay = false,
  background = '#050506',
  textColor = '#f3f1ea',
  onSelect,
}: Props) {
  // Every camera needs a print; a list without cameras falls back to the four built in.
  const cameras = camerasIn.length > 0 && camerasIn.every((c) => c.photos.length > 0) ? camerasIn : CAMERAS;
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay;
  const count = cameras.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const vectors = useMemo(() => cameras.map(toVector), [cameras]);
  const names = useMemo(() => cameras.map((c) => c.name), [cameras]);
  const specs = useMemo(() => cameras.map((c) => c.spec), [cameras]);
  const fonts = useMemo(
    () => ({
      name: matchFont({ fontFamily: DISPLAY, fontSize: 25, fontWeight: '800' }),
      spec: matchFont({ fontFamily: MONO, fontSize: 10 }),
      label: matchFont({ fontFamily: MONO, fontSize: 9 }),
      caption: matchFont({ fontFamily: MONO, fontSize: 6.5 }),
      engrave: matchFont({ fontFamily: DISPLAY, fontSize: 10, fontWeight: '700' }),
    }),
    [],
  );

  // The ring's turn (rad) and spin (rad/s).
  const angle = useSharedValue(0);
  const spin = useSharedValue(0);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  // A drag along the bar scrolls it: whether the drag is on the bar, and where the bar was when it began.
  const barDrag = useSharedValue(false);
  const barFrom = useSharedValue(0);
  // The body's turn and tilt, on springs (degrees, degrees per second).
  const yaw = useSharedValue(REST_YAW);
  const yawSpeed = useSharedValue(0);
  const tilt = useSharedValue(0);
  const tiltSpeed = useSharedValue(0);
  // The camera shown, the one before it, and seconds since the change.
  const from = useSharedValue(0);
  const to = useSharedValue(0);
  const since = useSharedValue(100);
  const shutter = useSharedValue(100);
  // The bar's scroll position, in cameras, and its speed. It coasts where a flick leaves it, or glides to
  // a chosen camera while `barFollow` is set; the highlight follows the chosen camera on its own spring
  // (scrolling never chooses).
  const bar = useSharedValue(0);
  const barSpeed = useSharedValue(0);
  const barTarget = useSharedValue(0);
  const barFollow = useSharedValue(true);
  const pick = useSharedValue(0);
  const pickSpeed = useSharedValue(0);
  // The loop: its clock, the turn it started from, the camera it started on; and the pause a touch makes.
  const clock = useSharedValue(0);
  const script = useSharedValue(0);
  const scriptFrom = useSharedValue(0);
  const scriptCamera = useSharedValue(0);
  const scriptStyle = useSharedValue(Math.max(0, STYLES.indexOf(initialRingStyle)));
  const hold = useSharedValue(0);
  // Tumbling, the body turns round with the ring: by the ring's turn beyond `camHome` (rad).
  const camHome = useSharedValue(0);
  const elapsed = useSharedValue(0);
  const loaded = useSharedValue<(Loaded | null)[][]>(cameras.map((c) => c.photos.map(() => null)));
  // The ring style: the numbers it is morphing from, the style it is morphing to, and seconds since.
  const firstStyle = Math.max(0, STYLES.indexOf(initialRingStyle));
  const [styleIndex, setStyleIndex] = useState(firstStyle);
  const styleFrom = useSharedValue<number[]>(STYLE_SHAPES[firstStyle]);
  const styleTo = useSharedValue(firstStyle);
  const styleSince = useSharedValue(STYLE_TIME);
  // The second ring's own turn (rad).
  const angleB = useSharedValue(0);
  const radiusNow = useSharedValue(STYLE_SHAPES[firstStyle][S_RADIUS]);
  const stage = useSharedValue<Stage>({ width: 0, height: 0, top: 0, bottom: 0 });

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  useEffect(() => {
    stage.set({ width, height, top: insets.top, bottom: insets.bottom });
  }, [width, height, insets.top, insets.bottom, stage]);

  useEffect(() => {
    hold.set(0);
    script.set(0);
    scriptFrom.set(angle.get());
    scriptCamera.set(to.get());
  }, [auto, hold, script, scriptFrom, scriptCamera, angle, to]);

  // Every print of every camera is fetched up front; each fades in once it arrives.
  useEffect(() => {
    let alive = true;
    loaded.set(cameras.map((c) => c.photos.map(() => null)));
    cameras.forEach((camera, ci) => {
      camera.photos.forEach((id, pi) => {
        Skia.Data.fromURI(photo(id))
          .then((data) => Skia.Image.MakeImageFromEncoded(data))
          .then((image) => {
            if (!alive || !image) return;
            loaded.modify((list) => {
              'worklet';
              const next = list.slice();
              const row = next[ci].slice();
              row[pi] = { image, at: elapsed.get() };
              next[ci] = row;
              return next;
            });
          })
          .catch(() => {});
      });
    });
    return () => {
      alive = false;
    };
  }, [cameras, loaded, elapsed]);

  const tap = (style: 'light' | 'medium' | 'rigid') => {
    if (process.env.EXPO_OS === 'web') return;
    const kind = style === 'light' ? Haptics.ImpactFeedbackStyle.Light : style === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Rigid;
    Haptics.impactAsync(kind).catch(() => {});
  };
  const selected = (index: number) => {
    tap('medium');
    onSelect?.(index);
  };

  /** Starts a camera change, unless one is still being dealt. */
  const choose = (index: number, byUser: boolean) => {
    'worklet';
    if (index < 0 || index >= count) return;
    if (index === to.get()) {
      // Tapping the chosen camera only brings it back to the middle of the bar.
      barTarget.set(index);
      barFollow.set(true);
      return;
    }
    if (since.get() < DEAL_TIME) return;
    from.set(to.get());
    to.set(index);
    barTarget.set(index);
    barFollow.set(true);
    since.set(0);
    if (byUser) scheduleOnRN(selected, index);
  };

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  /** Morphs to style `index` from wherever the current morph is. */
  const chooseStyle = (index: number) => {
    'worklet';
    if (index === styleTo.get() || index < 0 || index >= STYLES.length) return;
    styleFrom.set(styleMix(styleFrom.get(), styleTo.get(), styleSince.get() / STYLE_TIME));
    styleTo.set(index);
    styleSince.set(0);
  };

  const pickStyle = (index: number) => {
    if (index === styleIndex) return;
    setStyleIndex(index);
    chooseStyle(index);
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onStyleChange?.(STYLES[index]);
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    elapsed.set(elapsed.get() + dt);
    clock.set((clock.get() + dt) % LOOP);
    since.set(since.get() + dt);
    shutter.set(shutter.get() + dt);
    styleSince.set(styleSince.get() + dt);
    const shape = styleMix(styleFrom.get(), styleTo.get(), styleSince.get() / STYLE_TIME);
    radiusNow.set(shape[S_RADIUS]);
    const room = stage.get();
    const perRadian = travelPerRadian(shape[S_RADIUS], room.width);
    // Tumbling, the ring rests between its turns instead of drifting.
    const idle = reducedMotion ? 0 : IDLE_SPIN * (1 - shape[S_TUMBLE]);
    const before = angle.get();

    if (dragging.get() && !barDrag.get()) {
      angle.set(dragFrom.get() + dragX.get() / perRadian);
      spin.set(spin.get() + ((angle.get() - before) / dt - spin.get()) * 0.35);
    } else {
      if (hold.get() > 0) {
        hold.set(hold.get() - dt);
        if (hold.get() <= 0) {
          // The loop starts again from wherever the ring and the camera are.
          script.set(0);
          scriptFrom.set(angle.get());
          scriptCamera.set(to.get());
        }
      }
      if (scriptStyle.get() !== styleTo.get()) {
        // The loop's turn changes with the style; the ring carries on from where it is.
        const at = script.get();
        scriptFrom.set(scriptFrom.get() + scriptTurn(at, reducedMotion, scriptStyle.get()) - scriptTurn(at, reducedMotion, styleTo.get()));
        scriptStyle.set(styleTo.get());
      }
      if (auto && hold.get() <= 0) {
        const was = script.get();
        let now = was + dt;
        if (now >= LOOP) {
          scriptFrom.set(scriptFrom.get() + scriptTurn(LOOP, reducedMotion, scriptStyle.get()));
          now -= LOOP;
        }
        for (let i = 0; i < SWITCHES.length; i += 1) {
          const at = SWITCHES[i][0];
          const crossed = now >= was ? was < at && now >= at : was < at || now >= at;
          if (crossed) choose((scriptCamera.get() + SWITCHES[i][1]) % count, false);
        }
        script.set(now);
        angle.set(scriptFrom.get() + scriptTurn(now, reducedMotion, scriptStyle.get()));
        spin.set((angle.get() - before) / dt);
      } else if (reducedMotion) {
        spin.set(0);
      } else {
        // A throw coasts down to the idle spin.
        const s = idle + (spin.get() - idle) * Math.exp(-dt / COAST);
        spin.set(s);
        angle.set(before + s * dt);
      }
    }

    // The second ring follows the first, the way its style spins it, and is pulled onto its place:
    // the first ring's turn when the prints make one ring, its mirror image when they make two
    // (mirrored, a print of one ring never meets a print of the other where the rings cross).
    const moved = angle.get() - before;
    // Tumbling, the body turns with the ring while the loop plays or a finger holds it; otherwise it
    // comes back round to face the viewer (in the other styles it is always home).
    const ringHeld = dragging.get() && !barDrag.get();
    const scripted = auto && hold.get() <= 0;
    if (styleTo.get() !== TUMBLE_STYLE) camHome.set(camHome.get() + moved);
    if (styleTo.get() !== TUMBLE_STYLE || (!scripted && !ringHeld)) {
      camHome.set(camHome.get() + wrapPi(angle.get() - camHome.get()) * (1 - Math.exp(-dt * TUMBLE_HOME)));
    }
    const mirror = STYLE_SHAPES[styleTo.get()][S_DIR] < 0 ? -1 : 1;
    let b = angleB.get() + moved * shape[S_DIR];
    b += wrapPi(mirror * angle.get() - b) * Math.min(1, dt * 4);
    angleB.set(b);

    // The body turns with the ring's spin and tilts with a vertical drag, both on springs.
    const turnTo = REST_YAW + (reducedMotion ? 0 : (1 - shape[S_TUMBLE]) * YAW_GAIN * Math.tanh((spin.get() - idle) / YAW_SPEED));
    yawSpeed.set(yawSpeed.get() + (TURN_STIFFNESS * (turnTo - yaw.get()) - TURN_DAMPING * yawSpeed.get()) * dt);
    yaw.set(yaw.get() + yawSpeed.get() * dt);
    const tiltTo = dragging.get() && !barDrag.get() ? rubber(dragY.get() * TILT_PER_PT, TILT_BAND * TILT_PER_PT) : 0;
    tiltSpeed.set(tiltSpeed.get() + (TURN_STIFFNESS * (tiltTo - tilt.get()) - TURN_DAMPING * tiltSpeed.get()) * dt);
    tilt.set(tilt.get() + tiltSpeed.get() * dt);
    if (barDrag.get() && dragging.get()) {
      // The bar follows the finger, rubber-banding past its first and last camera.
      const k = room.width / DESIGN_WIDTH;
      const raw = barFrom.get() - dragX.get() / (BAR_SPACING * k);
      const last = count - 1;
      const at = raw < 0 ? rubber(raw, BAR_RUBBER) : raw > last ? last + rubber(raw - last, BAR_RUBBER) : raw;
      barSpeed.set((at - bar.get()) / dt);
      bar.set(at);
    } else if (barFollow.get()) {
      barSpeed.set(barSpeed.get() + (BAR_STIFFNESS * (barTarget.get() - bar.get()) - BAR_DAMPING * barSpeed.get()) * dt);
      bar.set(bar.get() + barSpeed.get() * dt);
    } else {
      // Left alone, the bar coasts down; past an end it springs back to it.
      const last = count - 1;
      const edge = bar.get() < 0 ? 0 : bar.get() > last ? last : bar.get();
      if (edge !== bar.get()) {
        barSpeed.set(barSpeed.get() + (BAR_STIFFNESS * (edge - bar.get()) - BAR_DAMPING * barSpeed.get()) * dt);
      } else {
        barSpeed.set(barSpeed.get() * Math.exp(-dt / BAR_COAST));
      }
      bar.set(bar.get() + barSpeed.get() * dt);
    }
    pickSpeed.set(pickSpeed.get() + (BAR_STIFFNESS * (to.get() - pick.get()) - BAR_DAMPING * pickSpeed.get()) * dt);
    pick.set(pick.get() + pickSpeed.get() * dt);
  });

  const pan = Gesture.Pan()
    .minDistance(6)
    .onBegin((event) => {
      const room = stage.get();
      const onBar = Math.abs(event.y - (room.height - room.bottom - BAR_ABOVE)) < BAR_TOUCH;
      barDrag.set(onBar);
      if (onBar) {
        // A finger on the bar catches it.
        barFollow.set(false);
        barSpeed.set(0);
      }
    })
    .onStart(() => {
      touched();
      barFrom.set(bar.get());
      dragFrom.set(angle.get());
      dragX.set(0);
      dragY.set(0);
      dragging.set(true);
    })
    .onUpdate((event) => {
      dragX.set(event.translationX);
      dragY.set(event.translationY);
    })
    .onEnd((event) => {
      touched();
      const room = stage.get();
      if (barDrag.get()) {
        // The bar coasts on from the flick and stops wherever it runs out. Scrolling only moves the
        // bar: a camera is chosen by tapping it.
        const k = room.width / DESIGN_WIDTH;
        const speed = -event.velocityX / (BAR_SPACING * k);
        barSpeed.set(reducedMotion ? 0 : Math.max(-BAR_MAX_SPEED, Math.min(BAR_MAX_SPEED, speed)));
        barFollow.set(false);
        return;
      }
      const perRadian = travelPerRadian(radiusNow.get(), room.width);
      const thrown = reducedMotion ? 0 : Math.max(-MAX_SPIN, Math.min(MAX_SPIN, event.velocityX / perRadian));
      spin.set(thrown);
      if (Math.abs(thrown) > FLICK_TICK) scheduleOnRN(tap, 'light');
    })
    .onFinalize(() => {
      dragging.set(false);
      barDrag.set(false);
    });

  const press = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      const room = stage.get();
      const k = room.width / DESIGN_WIDTH;
      const barY = room.height - room.bottom - BAR_ABOVE;
      if (Math.abs(event.y - barY) < 44) {
        const index = Math.round(bar.get() + (event.x - room.width / 2) / (BAR_SPACING * k));
        choose(index, true);
        return;
      }
      const bodyY = room.top + (room.height - room.top - room.bottom) * BODY_AT - STYLE_SHAPES[styleTo.get()][S_RISE] * k;
      if (Math.hypot(event.x - room.width / 2, event.y - bodyY) < 120 * k * STYLE_SHAPES[styleTo.get()][S_BODY]) {
        shutter.set(0);
        scheduleOnRN(tap, 'rigid');
      }
    });

  const picture = useDerivedValue(() => {
    const room = stage.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, room.width, room.height));
    if (room.width <= 0) return recorder.finishRecordingAsPicture();
    const k = room.width / DESIGN_WIDTH;
    const now = elapsed.get();
    const images = loaded.get();
    const loop = clock.get();
    const shape = styleMix(styleFrom.get(), styleTo.get(), styleSince.get() / STYLE_TIME);
    // How much of the toy camera shows, and how much it tumbles (none under reduced motion).
    const toy = shape[S_TUMBLE];
    const tumbling = reducedMotion ? 0 : toy;
    const cy = room.top + (room.height - room.top - room.bottom) * BODY_AT - shape[S_RISE] * k;
    const view: View3 = { f: FOCAL * k, cx: room.width / 2, cy };
    const screenRect = [0, 0, room.width, room.height];

    const ground = Skia.Paint();
    ground.setColor(Skia.Color(background));
    canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), ground);
    // A dim warm pool of light behind the body.
    const pool = Skia.Paint();
    pool.setShader(
      Skia.Shader.MakeRadialGradient(vec(room.width / 2, cy + 20 * k), room.width * 0.75, [Skia.Color('#221d1a'), Skia.Color('#0d0c0c'), Skia.Color(background)], [0, 0.5, 1], TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), pool);

    // The camera: the morph between the last body and this one, the spin and the dip.
    const t = since.get();
    const change = clamp01(t / SPIN_TIME);
    const fromIndex = from.get();
    const toIndex = to.get();
    const morph = smooth(clamp01((change - 0.3) / 0.4));
    const body = mixVector(vectors[fromIndex], vectors[toIndex], morph);
    const dip = Math.sin(Math.PI * change);
    // Tumbling, the change spins the body the way its ring turns.
    const spinWay = STYLE_SHAPES[styleTo.get()][S_TUMBLE] > 0.5 ? -1 : 1;
    const turning = reducedMotion ? 0 : spinWay * 360 * easeInOut(change);
    const flashAge = shutter.get() / FLASH_TIME;
    const flash = flashAge < 1 ? 1 - flashAge : 0;
    const punch = flashAge < 1 ? SHUTTER_PUNCH * Math.sin(Math.PI * Math.min(1, flashAge * 2)) : 0;
    const bob = reducedMotion ? 0 : BOB * Math.sin((loop / LOOP) * Math.PI * 8);
    const pitch = REST_PITCH + tilt.get();
    const pose: Pose = {
      x: 0,
      y: bob - (reducedMotion ? 0 : SPIN_LIFT * dip),
      z: DEPTH,
      s: shape[S_BODY] * (1 - (reducedMotion ? 0 : SPIN_DIP) * dip) * (1 - punch),
      r: mul3(tumbleMatrix(loop, angle.get() - camHome.get(), tumbling), rotation(((yaw.get() - TOY_YAW * toy + turning) * Math.PI) / 180, (pitch * Math.PI) / 180, (REST_ROLL * Math.PI) / 180)),
    };
    const lensTip = place(pose, body[V_LENS_X], body[V_LENS_Y], -body[V_D] / 2 - body[V_LENS_L]);

    // The prints: the ones flying off and the ones being dealt, then the ring at rest.
    const prints: PrintState[] = [];
    const radius = shape[S_RADIUS];
    const ringTilt = shape[S_TILT] + tilt.get() * 0.6;
    const split = shape[S_SPLIT];
    const turnNow = angle.get();
    const turnB = angleB.get();
    const sets = fromIndex !== toIndex && t < DEAL_TIME ? [fromIndex, toIndex] : [toIndex];
    for (const set of sets) {
      const outgoing = set !== toIndex;
      const n = cameras[set].photos.length;
      for (let i = 0; i < n; i += 1) {
        // Odd prints make the second ring when the style splits them.
        const second = i % 2 === 1;
        const theta = (second ? turnB : turnNow) + (i / n) * Math.PI * 2;
        const ringRoll = shape[S_ROLL] + (second ? -1 : 1) * split * GYRO_ROLL;
        const slot = ringPoint(theta, radius, ringTilt, ringRoll, shape[S_DROP]);
        const float = reducedMotion ? 0 : PRINT_FLOAT * Math.sin((loop / LOOP) * Math.PI * 4 + i * 1.7);
        let x = slot[0];
        let y = slot[1] + float + pose.y;
        let z = slot[2] + DEPTH;
        let s = shape[S_PRINT];
        let alpha = 1;
        const lean = shape[S_LEAN];
        let roll = (PRINT_ROLLS[i % PRINT_ROLLS.length] + Math.sin(theta) * 6) * (1 - 0.75 * lean) + ringRoll * (split * 0.5 + lean * 1.15);
        let yawP = -Math.sin(theta) * shape[S_TURN];
        if (outgoing) {
          const q = clamp01((t - i * OUT_STAGGER) / OUT_TIME);
          if (q >= 1) continue;
          const e = reducedMotion ? 0 : easeIn(q);
          x += slot[0] * 0.9 * e;
          y += 170 * e;
          z += (slot[2] > 0 ? 0.4 : 0.9) * slot[2] * e;
          roll += 40 * e * (i % 2 === 0 ? 1 : -1);
          s *= 1 + 0.15 * e;
          alpha = 1 - (reducedMotion ? q : smooth(q));
        } else if (t < DEAL_TIME && sets.length === 2) {
          const q = clamp01((t - IN_DELAY - i * IN_STAGGER) / IN_TIME);
          if (q <= 0) continue;
          if (reducedMotion) {
            alpha = smooth(q);
          } else {
            const e = easeOut(q);
            x = lensTip[0] + (x - lensTip[0]) * e;
            y = lensTip[1] + (y - lensTip[1]) * e;
            z = lensTip[2] - 30 * (1 - e) + (z - lensTip[2]) * e;
            s *= 0.25 + 0.75 * backOut(q);
            roll -= 160 * (1 - e);
            yawP *= e;
            alpha = clamp01(q * 4);
          }
        }
        const depth = clamp01((z - (DEPTH - radius * 0.3)) / (radius * 1.3));
        prints.push({
          set,
          index: i,
          pos: [x, y, z],
          r: rotation((yawP * Math.PI) / 180, ((Math.min(ringTilt, 34) * 0.5 + pitch * 0.2) * Math.PI) / 180, (roll * Math.PI) / 180),
          s,
          alpha,
          // Leaning prints pass low under the body in sight, so they stay sharper there.
          shade: depth * BACK_SHADE * (1 - 0.3 * shape[S_LEAN]),
          blur: reducedMotion ? 0 : depth * BACK_BLUR * (1 - 0.6 * shape[S_LEAN]),
        });
      }
    }
    prints.sort(farthestFirst);
    // Tumbling, a print passes behind the body once it is beyond the body's front face rather than its
    // middle, so the prints coming round the sides slip behind its edges instead of over them.
    const bodyHalf = ((body[V_D] + (Math.max(body[V_D], body[V_W] * 0.42) - body[V_D]) * toy) / 2) * pose.s;
    const bodyLine = DEPTH - bodyHalf * 0.8 * toy;
    let bodyDrawn = false;
    for (const p of prints) {
      if (!bodyDrawn && p.pos[2] < bodyLine) {
        drawAnyCamera(canvas, view, pose, body, toy, true, names[morph < 0.5 ? fromIndex : toIndex], fonts.engrave, flash, screenRect);
        bodyDrawn = true;
      }
      const caption = `${names[p.set]} · ${String(p.index + 1).padStart(2, '0')}/${String(cameras[p.set].photos.length).padStart(2, '0')}`;
      drawPrint(canvas, view, p, images[p.set]?.[p.index] ?? null, caption, fonts.caption, now);
    }
    if (!bodyDrawn) drawAnyCamera(canvas, view, pose, body, toy, true, names[morph < 0.5 ? fromIndex : toIndex], fonts.engrave, flash, screenRect);

    // A faint white flash over the whole scene when the shutter fires.
    if (flash > 0.01) {
      const white = Skia.Paint();
      white.setColor(Skia.Color('#ffffff'));
      white.setAlphaf(flash * flash * 0.12);
      canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), white);
    }

    // The name rolls letter by letter: the old one up and out, the new one up from below.
    const barY = room.height - room.bottom - BAR_ABOVE;
    const nameY = barY - 92;
    const ink = Skia.Color(textColor);
    const inkAt = (a: number) => Float32Array.of(ink[0], ink[1], ink[2], a);
    canvas.save();
    canvas.clipRect(Skia.XYWHRect(0, nameY - 34, room.width, 42), ClipOp.Intersect, true);
    const changing = fromIndex !== toIndex && t < 1.2;
    rollName(canvas, names[fromIndex], fonts.name, room.width / 2, nameY, changing ? t : -1, false, reducedMotion, ink);
    rollName(canvas, names[toIndex], fonts.name, room.width / 2, nameY, changing ? t : -1, true, reducedMotion, ink);
    canvas.restore();
    const specOut = fromIndex !== toIndex ? 1 - clamp01((t - 0.05) / 0.25) : 0;
    const specIn = fromIndex !== toIndex ? clamp01((t - 0.35) / 0.3) : 1;
    if (specOut > 0.01) drawTracked(canvas, specs[fromIndex], fonts.spec, room.width / 2, nameY + 22, 1.4, 0.5, inkAt(0.5 * specOut));
    if (specIn > 0.01) drawTracked(canvas, specs[toIndex], fonts.spec, room.width / 2, nameY + 22, 1.4, 0.5, inkAt(0.5 * specIn));

    // The bar: the chosen camera in colour, the others smaller and grey; it scrolls on its own.
    const barAt = bar.get();
    const pickAt = pick.get();
    for (let i = 0; i < count; i += 1) {
      const x = room.width / 2 + (i - barAt) * BAR_SPACING * k;
      if (x < -BAR_SPACING || x > room.width + BAR_SPACING) continue;
      const e = clamp01(1 - Math.abs(i - pickAt));
      const layer = Skia.Paint();
      layer.setAlphaf(0.42 + 0.58 * e);
      layer.setColorFilter(Skia.ColorFilter.MakeMatrix(saturation(0.15 + 0.85 * e)));
      canvas.saveLayer(layer, Skia.XYWHRect(x - 50 * k, barY - 40 * k, 100 * k, 70 * k));
      const v = vectors[i];
      const thumb: Pose = {
        x: 0,
        y: 0,
        z: DEPTH,
        s: (THUMB_WIDTH / v[V_W]) * (0.82 + 0.3 * e),
        r: rotation((24 * Math.PI) / 180, (16 * Math.PI) / 180, 0),
      };
      drawAnyCamera(canvas, { f: FOCAL * k, cx: x, cy: barY - 6 * k }, thumb, v, toy, false, names[i], fonts.engrave, 0, [x - 50 * k, barY - 40 * k, 100 * k, 70 * k]);
      canvas.restore();
      drawTracked(canvas, names[i], fonts.label, x, barY + 30 * k, 1.2, 0.5, inkAt(0.3 + 0.7 * e));
    }

    return recorder.finishRecordingAsPicture();
  });

  return (
    <View style={[styles.screen, { backgroundColor: background }]}>
      <GestureDetector gesture={Gesture.Race(pan, press)}>
        <View
          style={styles.screen}
          accessible
          accessibilityLabel={`A ${names.join(', ')} camera with ten photo prints orbiting it. Drag sideways to spin the prints; swipe the bar of cameras below to browse them and tap one to change cameras.`}
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
      {showStylePicker && (
        <OrbitStylePicker top={insets.top + 10} selected={styleIndex} onPick={pickStyle} ink={textColor} reduced={reducedMotion} />
      )}
    </View>
  );
}

type PickerProps = { top: number; selected: number; onPick: (index: number) => void; ink: string; reduced: boolean };

// The switcher: a dark capsule of three segments, a light pill sliding under the chosen one.
const PICKER_PAD = 3;
const PICKER_SPRING = { duration: 380, dampingRatio: 0.86 } as const;

/** Ring · Tumble · Gyro, at the top of the screen. */
function OrbitStylePicker({ top, selected, onPick, ink, reduced }: PickerProps) {
  const [frames, setFrames] = useState<{ x: number; width: number }[]>([]);
  const pillX = useSharedValue(0);
  const pillW = useSharedValue(0);
  useEffect(() => {
    const frame = frames[selected];
    if (!frame) return;
    if (pillW.get() === 0 || reduced) {
      pillX.set(frame.x);
      pillW.set(frame.width);
    } else {
      pillX.set(withSpring(frame.x, PICKER_SPRING));
      pillW.set(withSpring(frame.width, PICKER_SPRING));
    }
  }, [frames, selected, pillX, pillW, reduced]);
  const pillStyle = useAnimatedStyle(() => ({
    width: pillW.get(),
    opacity: pillW.get() > 0 ? 1 : 0,
    transform: [{ translateX: pillX.get() }],
  }));
  return (
    <View pointerEvents="box-none" style={[styles.pickerRow, { top }]}>
      <View style={styles.picker} accessibilityRole="tablist">
        <Animated.View pointerEvents="none" style={[styles.pickerPill, { backgroundColor: ink }, pillStyle]} />
        {STYLE_LABELS.map((label, index) => (
          <Pressable
            key={label}
            onPress={() => onPick(index)}
            hitSlop={{ top: 8, bottom: 8 }}
            accessibilityRole="tab"
            accessibilityState={{ selected: index === selected }}
            accessibilityLabel={`${label} style`}
            onLayout={(event: LayoutChangeEvent) => {
              const { x, width } = event.nativeEvent.layout;
              setFrames((list) => {
                if (list[index]?.x === x && list[index]?.width === width) return list;
                const next = list.slice();
                next[index] = { x, width };
                return next;
              });
            }}
            style={({ pressed }) => [styles.segment, pressed && styles.segmentPressed]}
          >
            <Text style={[styles.segmentText, { color: index === selected ? '#0b0b0c' : ink }, index !== selected && styles.segmentIdle]}>{label}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  pickerRow: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  picker: {
    flexDirection: 'row',
    padding: PICKER_PAD,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.16)',
  },
  pickerPill: {
    position: 'absolute',
    left: 0,
    top: PICKER_PAD,
    bottom: PICKER_PAD,
    borderRadius: 999,
  },
  segment: {
    height: 30,
    paddingHorizontal: 16,
    borderRadius: 999,
    justifyContent: 'center',
  },
  segmentPressed: {
    opacity: 0.7,
  },
  segmentText: {
    fontFamily: DISPLAY,
    fontSize: 13,
    fontWeight: '600',
    letterSpacing: 0.2,
  },
  segmentIdle: {
    opacity: 0.62,
  },
});
