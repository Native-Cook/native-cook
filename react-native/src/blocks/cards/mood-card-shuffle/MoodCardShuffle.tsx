import {
  BlurStyle,
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
import { SymbolView } from 'expo-symbols';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type MoodPlace = {
  name: string;
  /** Small caps line above the name, e.g. "Yên Bái · Vietnam". */
  region: string;
  /** One sentence about the place; it is set on at most two lines. */
  blurb: string;
  /** An Unsplash photo id (`photo-…`), landscape. */
  photo: string;
};

export type Mood = {
  label: string;
  /** Four places: the deck always holds four cards (fewer are repeated). */
  places: MoodPlace[];
};

type Props = {
  moods?: Mood[];
  title?: string;
  /** Turns cards and changes the mood twice, on a loop of LOOP seconds. */
  autoPlay?: boolean;
  /** Called with a mood's label when the deck is reshuffled to it. */
  onMoodChange?: (label: string) => void;
};

const MOODS: Mood[] = [
  {
    label: 'Quiet',
    places: [
      {
        name: 'Mù Cang Chải',
        region: 'Yên Bái · Vietnam',
        blurb: 'Layered rice terraces, misty hills and slow mornings in the highlands.',
        photo: 'photo-1609412058473-c199497c3c5d',
      },
      {
        name: 'Black Forest',
        region: 'Baden · Germany',
        blurb: 'Fog-soft spruce trails, wood-fired saunas and nobody in a hurry.',
        photo: 'photo-1732130318657-c8740c0f5215',
      },
      {
        name: 'Blue Ridge',
        region: 'Virginia · USA',
        blurb: 'Ridge after ridge fading into blue, best seen from a porch at dusk.',
        photo: 'photo-1552152370-fb05b25ff17d',
      },
      {
        name: 'Trà Quế',
        region: 'Quảng Nam · Vietnam',
        blurb: 'Bicycle lanes through the herb gardens, lanterns once the sun goes down.',
        photo: 'photo-1608335715837-1994a535d5c3',
      },
    ],
  },
  {
    label: 'Nature',
    places: [
      {
        name: 'Hoàng Su Phì',
        region: 'Hà Giang · Vietnam',
        blurb: 'Terraces stacked like contour lines, bright green before the harvest.',
        photo: 'photo-1480996408299-fc0e830b5db1',
      },
      {
        name: 'Hoh Valley',
        region: 'Washington · USA',
        blurb: 'Moss-hung rainforest and rivers the colour of glacier milk.',
        photo: 'photo-1542273917363-3b1817f69a2d',
      },
      {
        name: 'Central Otago',
        region: 'South Island · New Zealand',
        blurb: 'Rolling hills, open roads and quiet valleys stretching into the distance.',
        photo: 'photo-1612441804231-77a36b284856',
      },
      {
        name: 'Bieszczady',
        region: 'Podkarpacie · Poland',
        blurb: 'Cloud pouring through old beech forest along the eastern border.',
        photo: 'photo-1488330890490-c291ecf62571',
      },
    ],
  },
  {
    label: 'Artisanal Food',
    places: [
      {
        name: 'Sa Pa',
        region: 'Lào Cai · Vietnam',
        blurb: 'Hand-milled sticky rice, smoked pork and corn wine at the Sunday market.',
        photo: 'photo-1711060266983-92bd378c850c',
      },
      {
        name: 'Banaue',
        region: 'Ifugao · Philippines',
        blurb: 'Heirloom red rice grown on terraces two thousand years old.',
        photo: 'photo-1548710332-3e03e8220a79',
      },
      {
        name: 'Jatiluwih',
        region: 'Bali · Indonesia',
        blurb: 'Farm kitchens cooking straight from the fields outside the window.',
        photo: 'photo-1715755455989-76413081ad10',
      },
      {
        name: 'Campo Imperatore',
        region: 'Abruzzo · Italy',
        blurb: "Shepherds' pecorino, saffron and lentils from the high plains.",
        photo: 'photo-1601281426795-9fedf41df8d1',
      },
    ],
  },
  {
    label: 'Wild',
    places: [
      {
        name: 'Dolomites',
        region: 'South Tyrol · Italy',
        blurb: 'Limestone spires, iron-rung climbs and huts above the clouds.',
        photo: 'photo-1611080402167-ed75bae6df32',
      },
      {
        name: 'Julian Alps',
        region: 'Triglav · Slovenia',
        blurb: 'Glacial valleys, emerald rivers and trails with no signal at all.',
        photo: 'photo-1610552050890-fe99536c2615',
      },
      {
        name: 'Tongass',
        region: 'Alaska · USA',
        blurb: 'The wettest, wildest rainforest in the north, and bears on the shore.',
        photo: 'photo-1476362555312-ab9e108a0b7e',
      },
      {
        name: 'Huayhuash',
        region: 'Áncash · Peru',
        blurb: 'Ten days of passes over 4,500 m, dawn turning the peaks pink.',
        photo: 'photo-1500964757637-c85e8a162699',
      },
    ],
  },
];

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const COUNT = 4;

function photoUrl(id: string, width: number) {
  return `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;
}
// Card photos are drawn about 270 pt wide (810 px at 3×).
const CARD_PHOTO_PX = 800;
// The backdrop is the photo blurred once into a small BACKDROP_W × BACKDROP_H px image (sigma
// BACKDROP_BLUR px there), drawn stretched over the screen with cubic sampling.
const BACKDROP_W = 120;
const BACKDROP_H = 260;
const BACKDROP_BLUR = 9;
const BACKDROP_SATURATION = 0.72;
// Seconds the backdrop takes to cross-fade when a reshuffle changes the front photo.
const BACKDROP_FADE = 0.52;
// Seconds a photo takes to fade in once it has loaded.
const IMAGE_FADE = 0.35;

// The front card is CARD_SHARE of the screen's width (at most CARD_MAX pt), CARD_ASPECT as tall as
// it is wide, with CARD_RADIUS pt corners. Each card has its own vanishing point PERSPECTIVE pt away.
const CARD_SHARE = 0.68;
const CARD_MAX = 300;
const CARD_ASPECT = 0.84;
const CARD_RADIUS = 26;
const PERSPECTIVE = 900;

// The deck is a drum seen from the front. A card's place is its signed distance s from the front
// (in cards, −2 … 2): s 0 in front, 1 peeking out on the right, −1 on the left, ±2 at the back.
// PEEK_X is the peeks' offset in card widths; they shrink by DEPTH_SCALE at the back of the drum,
// drop DEPTH_DROP pt, turn PEEK_TURN° with their outer edge away, tilt PEEK_TILT° and darken up to
// DEPTH_DIM.
const PEEK_X = 0.25;
const DEPTH_SCALE = 0.3;
const DEPTH_DROP = 18;
const PEEK_TURN = 16;
const PEEK_TILT = 1.6;
const DEPTH_DIM = 0.62;
// The card leaving the front swings out the way the deck turns: SWING_X card widths further out
// at the middle of its move, turned SWING_TURN° more, lifted SWING_LIFT pt and shrunk by
// SWING_DIP, then tucks in as the peek. The card arriving slides straight in over it. Which card
// swings follows the deck's heading, eased over HEADING_EASE s so a reversal never jumps.
const SWING_X = 0.36;
const SWING_TURN = 26;
const SWING_LIFT = 14;
const SWING_DIP = 0.08;
const HEADING_EASE = 0.12;
// When two cards trade places in the drawing order, the one going under is drawn once more on top,
// fading out over GHOST_BAND of their depth difference, so the change is a cross-fade, not a cut.
const GHOST_BAND = 0.3;
// The chip, bookmark and text show only on the front card: they fade out by |s| = 1 / TEXT_FADE.
const TEXT_FADE = 3;
const SHADOW_BLUR = 16;
const SHADOW_DROP = 16;
const SHADOW_ALPHA = 0.34;
// Extra space between the region line's letters, in pt.
const REGION_TRACKING = 0.7;

// Reshuffling to a new mood: over SHUFFLE_TIME s the cards gather into a riffled pile, turn
// edge-on together (their faces change while nobody can see them), turn back and deal out.
const SHUFFLE_TIME = 1.15;
const GATHER_X = [0, 0.12, -0.12, 0.06];
const GATHER_Y = [0, -10, -20, -30];
const GATHER_SCALE = [0.9, 0.87, 0.84, 0.81];
const GATHER_TILT = [0, 7, -7, 3.5];

// Finger travel that turns the deck by one card, as a share of the screen's width.
const DRAG_SPAN = 0.72;
// A release commits when the position projected THROW s ahead passes half a card.
const THROW = 0.18;
// Past one card in either direction the deck gives only RUBBER of the finger's travel.
const RUBBER = 0.2;
// After a drag or a tap the deck lands on this spring, carrying the finger's speed.
const DECK_SPRING = { mass: 1, stiffness: 90, damping: 17 } as const;
// autoPlay turns the deck over ADVANCE_TIME ms on an ease-in-out curve.
const ADVANCE_TIME = 1150;
const ADVANCE_EASE = Easing.bezier(0.5, 0, 0.25, 1);
// The bookmark pops to BOOKMARK_POP × over 0.11 s, then settles on a damped wobble.
const BOOKMARK_POP = 1.28;
const PILL_SPRING = { duration: 420, dampingRatio: 0.82 } as const;

// autoPlay (seconds): two cards, a reshuffle to Nature, a card, a reshuffle to Wild, two cards,
// back to Quiet and a rest. Five cards in all, and the last reshuffle deals the first place onto
// the front card, so one period ends looking exactly as it started.
const LOOP = 14;
type Cue = { at: number; next?: boolean; mood?: number };
const CUES: Cue[] = [
  { at: 1.0, next: true },
  { at: 2.4, next: true },
  { at: 3.8, mood: 1 },
  { at: 5.6, next: true },
  { at: 7.0, mood: 3 },
  { at: 8.8, next: true },
  { at: 10.2, next: true },
  { at: 11.6, mood: 0 },
];
// A touch pauses autoPlay for this long; then the loop starts over.
const USER_HOLD = 4;

/** A place as the canvas draws it: the blurb already broken into at most two lines, and the x of
 * each letter of the region line (glyph advances plus REGION_TRACKING). */
type CardFace = { name: string; region: string; regionX: number[]; photo: string; lines: string[] };
type Loaded = { image: SkImage; blur: SkImage | null; at: number };
type Stage = { width: number; height: number; cx: number; cy: number; cardW: number; cardH: number };
type Fonts = { region: SkFont; name: SkFont; blurb: SkFont; chip: SkFont };

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function smooth(x: number) {
  'worklet';
  const t = clamp01(x);
  return t * t * (3 - 2 * t);
}

/** Linear interpolation through `values` at whole steps 0, 1, 2 … of `x`, wrapping at the end. */
function around(values: number[], x: number) {
  'worklet';
  const n = values.length;
  const at = ((x % n) + n) % n;
  const i = Math.floor(at);
  const f = at - i;
  return values[i] + (values[(i + 1) % n] - values[i]) * f;
}

/** A card's signed distance from the front, in (−2, 2]. */
function signedOf(index: number, progress: number) {
  'worklet';
  let s = (index - progress) % COUNT;
  if (s <= -2) s += COUNT;
  if (s > 2) s -= COUNT;
  return s;
}

/** 0 at rest, 1 for the riffled pile in the middle of a reshuffle. */
function gatherOf(shuffle: number) {
  'worklet';
  return smooth(shuffle / 0.36) * (1 - smooth((shuffle - 0.64) / 0.36));
}

/** Extra turn of the whole pile during a reshuffle: edge-on from 44% to 56%. */
function shuffleTurnOf(shuffle: number) {
  'worklet';
  return 90 * smooth((shuffle - 0.2) / 0.24) * (1 - smooth((shuffle - 0.56) / 0.24));
}

type Pose = { x: number; y: number; scale: number; turn: number; tilt: number; dim: number; text: number; alpha: number; key: number };

/** Where card `index` is drawn, from the deck's progress and the reshuffle. */
function poseOf(index: number, progress: number, heading: number, shuffle: number, reduced: boolean, cardW: number): Pose {
  'worklet';
  const s = signedOf(index, progress);
  const a = Math.abs(s);
  const side = s < 0 ? -1 : 1;
  const angle = (s * Math.PI) / 2;
  const depth = (1 - Math.cos(angle)) / 2;
  // Only the card going out on the side the deck turns towards swings (heading +1: to the left).
  const leaving = clamp01((1 - side * heading) / 2);
  const swing = !reduced && a < 1 ? Math.sin(Math.PI * a) * leaving : 0;
  let x = Math.sin(angle) * PEEK_X * cardW + side * SWING_X * cardW * swing;
  let y = DEPTH_DROP * depth - SWING_LIFT * swing;
  let scale = (1 - DEPTH_SCALE * depth) * (1 - SWING_DIP * swing);
  let turn = Math.sin(angle) * PEEK_TURN + side * SWING_TURN * swing;
  let tilt = Math.sin(angle) * PEEK_TILT;
  let alpha = 1;
  // Upper cards draw later: the closer to the front, the higher.
  let key = -a;
  const g = reduced ? 0 : gatherOf(shuffle);
  if (g > 0) {
    // The pile, by each card's rank from the front: 0 front, 1 right, 2 back, 3 left.
    const d = s < 0 ? s + COUNT : s;
    x += (around(GATHER_X, d) * cardW - x) * g;
    y += (around(GATHER_Y, d) - y) * g;
    scale += (around(GATHER_SCALE, d) - scale) * g;
    tilt += (around(GATHER_TILT, d) - tilt) * g;
    turn -= turn * g;
    key += (-d - key) * g;
  }
  turn += reduced ? 0 : shuffleTurnOf(shuffle);
  // Reduced motion: the deck dips out and back in while a reshuffle changes its faces.
  if (reduced && shuffle > 0) alpha = 1 - Math.sin(Math.PI * clamp01(shuffle));
  return {
    x,
    y,
    scale,
    turn,
    tilt,
    dim: DEPTH_DIM * depth + 0.1 * swing,
    text: clamp01(1 - a * TEXT_FADE) * (1 - g),
    alpha,
    key,
  };
}

/**
 * The 3 × 3 matrix that draws a card (its own coordinates centred on its middle) at a pose:
 * scaled, tilted in the picture plane, turned about its vertical axis and seen in perspective.
 */
function cardMatrix(p: Pose, cx: number, cy: number) {
  'worklet';
  const t = (p.turn * Math.PI) / 180;
  const r = (p.tilt * Math.PI) / 180;
  const ct = Math.cos(t);
  const st = Math.sin(t);
  const a = p.scale * Math.cos(r);
  const b = p.scale * Math.sin(r);
  const tx = cx + p.x;
  const ty = cy + p.y;
  // Local (u, v) → (a·u − b·v, b·u + a·v); then x = X·cos t, depth = X·sin t (away from the eye).
  const q0 = ct + (tx * st) / PERSPECTIVE;
  const q1 = (ty * st) / PERSPECTIVE;
  const q2 = st / PERSPECTIVE;
  return [q0 * a, -q0 * b, tx, q1 * a + b, -q1 * b + a, ty, q2 * a, -q2 * b, 1];
}

/** Where the front card's photo for slot `slot` comes from under the deal (mood, offset). */
function faceOf(faces: CardFace[][], mood: number, offset: number, slot: number) {
  'worklet';
  const list = faces[Math.min(faces.length - 1, Math.max(0, mood))];
  return list[(((slot - offset) % COUNT) + COUNT) % COUNT];
}

/** The bookmark glyph, 9 × 12, centred on (0, 0). */
function bookmarkPath() {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(-4.5, -6);
  b.lineTo(4.5, -6);
  b.lineTo(4.5, 6);
  b.lineTo(0, 2.6);
  b.lineTo(-4.5, 6);
  b.close();
  return b.build();
}

/** One card, drawn in its own coordinates (w × h, centred). */
function drawCard(
  canvas: SkCanvas,
  face: CardFace,
  loaded: Loaded | undefined,
  p: Pose,
  w: number,
  h: number,
  fonts: Fonts,
  now: number,
  saved: boolean,
  pop: number,
  withShadow: boolean,
) {
  'worklet';
  const card = Skia.RRectXY(Skia.XYWHRect(-w / 2, -h / 2, w, h), CARD_RADIUS, CARD_RADIUS);
  if (withShadow) {
    const shadow = Skia.Paint();
    shadow.setAntiAlias(true);
    shadow.setColor(Skia.Color('#000000'));
    shadow.setAlphaf(SHADOW_ALPHA * (1 - p.dim * 0.6));
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, SHADOW_BLUR, true));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-w / 2 + 10, -h / 2 + SHADOW_DROP, w - 20, h - 6), CARD_RADIUS, CARD_RADIUS), shadow);
  }
  canvas.save();
  canvas.clipRRect(card, ClipOp.Intersect, true);
  // Until the photo arrives the card is a warm grey with a soft fall of light.
  const ground = Skia.Paint();
  ground.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, -h / 2), vec(0, h / 2), [Skia.Color('#5a5848'), Skia.Color('#34332a')], null, TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(-w / 2, -h / 2, w, h), ground);
  if (loaded) {
    const image = loaded.image;
    const iw = image.width();
    const ih = image.height();
    if (iw > 0 && ih > 0) {
      // Cover: crop the photo to the card's shape.
      const k = Math.max(w / iw, h / ih);
      const sw = w / k;
      const sh = h / k;
      const art = Skia.Paint();
      art.setAntiAlias(true);
      art.setAlphaf(clamp01((now - loaded.at) / IMAGE_FADE));
      canvas.drawImageRectOptions(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), Skia.XYWHRect(-w / 2, -h / 2, w, h), FilterMode.Linear, MipmapMode.Linear, art);
    }
  }
  if (p.text > 0.01) {
    // The shade under the text, then the chip, the bookmark and the words, all fading together.
    const t = p.text;
    const shade = Skia.Paint();
    shade.setShader(
      Skia.Shader.MakeLinearGradient(
        vec(0, -h / 2),
        vec(0, h / 2),
        [Float32Array.of(0.03, 0.03, 0.02, 0), Float32Array.of(0.03, 0.03, 0.02, 0.2 * t), Float32Array.of(0.03, 0.03, 0.02, 0.8 * t)],
        [0.32, 0.55, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(-w / 2, -h / 2, w, h), shade);

    const left = -w / 2 + 16;
    const top = -h / 2 + 14;
    const chipW = fonts.chip.measureText('Curated').width + 20;
    const chip = Skia.Paint();
    chip.setAntiAlias(true);
    chip.setColor(Float32Array.of(1, 1, 1, 0.94 * t));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(left - 2, top, chipW, 22), 11, 11), chip);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Float32Array.of(0.08, 0.08, 0.06, t));
    canvas.drawText('Curated', left + 8, top + 15, ink, fonts.chip);

    // The bookmark: a round glass button, white when the place is saved.
    const bx = w / 2 - 14 - 15;
    const by = top + 11 + 4;
    canvas.save();
    canvas.translate(bx, by);
    canvas.scale(pop, pop);
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setColor(saved ? Float32Array.of(1, 1, 1, t) : Float32Array.of(0.08, 0.08, 0.06, 0.32 * t));
    canvas.drawCircle(0, 0, 15, ring);
    if (!saved) {
      ring.setStyle(1);
      ring.setStrokeWidth(1);
      ring.setColor(Float32Array.of(1, 1, 1, 0.4 * t));
      canvas.drawCircle(0, 0, 14.5, ring);
    }
    const glyph = Skia.Paint();
    glyph.setAntiAlias(true);
    glyph.setStrokeJoin(1);
    glyph.setStrokeWidth(1.6);
    glyph.setStyle(saved ? 0 : 1);
    glyph.setColor(saved ? Float32Array.of(0.08, 0.08, 0.06, t) : Float32Array.of(1, 1, 1, t));
    canvas.drawPath(bookmarkPath(), glyph);
    canvas.restore();

    const lines = face.lines;
    const bottom = h / 2 - 17;
    const words = Skia.Paint();
    words.setAntiAlias(true);
    words.setColor(Float32Array.of(1, 1, 1, 0.78 * t));
    for (let i = 0; i < lines.length; i += 1) {
      canvas.drawText(lines[i], left, bottom - (lines.length - 1 - i) * 17, words, fonts.blurb);
    }
    const nameY = bottom - (lines.length - 1) * 17 - 22;
    words.setColor(Float32Array.of(1, 1, 1, t));
    canvas.drawText(face.name, left, nameY, words, fonts.name);
    words.setColor(Float32Array.of(1, 1, 1, 0.72 * t));
    for (let i = 0; i < face.region.length && i < face.regionX.length; i += 1) {
      canvas.drawText(face.region[i], left + face.regionX[i], nameY - 24, words, fonts.region);
    }
  }
  if (p.dim > 0.004) {
    const dim = Skia.Paint();
    dim.setColor(Float32Array.of(0.04, 0.04, 0.03, p.dim));
    canvas.drawRect(Skia.XYWHRect(-w / 2, -h / 2, w, h), dim);
  }
  canvas.restore();
  // A hairline of light along the rim.
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(1);
  rim.setStrokeWidth(1);
  rim.setColor(Float32Array.of(1, 1, 1, 0.14 * (1 - p.dim)));
  canvas.drawRRect(card, rim);
}

/** A blurred photo stretched over the screen, cover-fit, at `alpha`. */
function drawBackdrop(canvas: SkCanvas, image: SkImage, width: number, height: number, alpha: number) {
  'worklet';
  const iw = image.width();
  const ih = image.height();
  if (iw <= 0 || ih <= 0 || alpha <= 0.002) return;
  const k = Math.max(width / iw, height / ih);
  const sw = width / k;
  const sh = height / k;
  const paint = Skia.Paint();
  paint.setAlphaf(clamp01(alpha));
  canvas.drawImageRectCubic(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), Skia.XYWHRect(0, 0, width, height), 1 / 3, 1 / 3, paint);
}

/** The blurred backdrop for a photo: blurred once, small, on the JS thread. */
function makeBackdrop(image: SkImage) {
  const surface = Skia.Surface.Make(BACKDROP_W, BACKDROP_H);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  const iw = image.width();
  const ih = image.height();
  if (iw <= 0 || ih <= 0) return null;
  const k = Math.max(BACKDROP_W / iw, BACKDROP_H / ih);
  const sw = BACKDROP_W / k;
  const sh = BACKDROP_H / k;
  const paint = Skia.Paint();
  paint.setImageFilter(Skia.ImageFilter.MakeBlur(BACKDROP_BLUR, BACKDROP_BLUR, TileMode.Clamp, null));
  // Muted towards grey, so the backdrop sets a mood without competing with the cards.
  const s = BACKDROP_SATURATION;
  const r = (1 - s) * 0.2126;
  const g = (1 - s) * 0.7152;
  const b = (1 - s) * 0.0722;
  paint.setColorFilter(Skia.ColorFilter.MakeMatrix([r + s, g, b, 0, 0, r, g + s, b, 0, 0, r, g, b + s, 0, 0, 0, 0, 0, 1, 0]));
  canvas.drawImageRectOptions(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), Skia.XYWHRect(0, 0, BACKDROP_W, BACKDROP_H), FilterMode.Linear, MipmapMode.None, paint);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** Breaks `text` into at most two lines of `width` pt, the second ending in "…" if it overflows. */
function wrapTwo(text: string, font: SkFont, width: number) {
  const words = text.split(' ');
  const lines: string[] = [];
  let line = '';
  for (let i = 0; i < words.length; i += 1) {
    const next = line ? `${line} ${words[i]}` : words[i];
    if (font.measureText(next).width <= width || !line) {
      line = next;
      continue;
    }
    if (lines.length === 1) {
      // Second line full: end it with an ellipsis that fits.
      let cut = `${line}…`;
      while (cut.length > 1 && font.measureText(cut).width > width) cut = `${cut.slice(0, -2).trimEnd()}…`;
      lines.push(cut);
      return lines;
    }
    lines.push(line);
    line = words[i];
  }
  if (line) lines.push(line);
  return lines.slice(0, 2);
}

/** Where each letter of `text` starts, set with its glyph advances and REGION_TRACKING between letters. */
function letterPositions(text: string, font: SkFont) {
  const xs: number[] = [];
  let at = 0;
  for (let i = 0; i < text.length; i += 1) {
    xs.push(at);
    const ids = font.getGlyphIDs(text[i]);
    const advance = ids.length > 0 ? (font.getGlyphWidths(ids)[0] ?? 0) : 0;
    at += (Number.isFinite(advance) ? advance : 0) + REGION_TRACKING;
  }
  return xs;
}

/** Every mood padded (or trimmed) to exactly four places. */
function normalise(moods: Mood[]) {
  const list = moods.filter((m) => m.places.length > 0);
  const safe = list.length > 0 ? list : MOODS;
  return safe.map((m) => ({ label: m.label, places: Array.from({ length: COUNT }, (_, i) => m.places[i % m.places.length]) }));
}

type ChipFrame = { x: number; width: number };

/**
 * A mood-based place finder: a deck of photo cards over a blurred copy of the front one. Swiping
 * turns the deck like a drum: the front card swings out to the side and tucks in behind while the
 * next one swings in and straightens up. The mood chips in the sheet below riffle the deck into a
 * pile and deal out another set of places. The deck and the backdrop are drawn in one Skia canvas,
 * so every frame of a swipe is computed on the UI thread without a single re-render.
 */
export function MoodCardShuffle({ moods: moodsIn = MOODS, title = 'Where do you feel\nlike going?', autoPlay = false, onMoodChange }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay;
  const moods = useMemo(() => normalise(moodsIn), [moodsIn]);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [deckFrame, setDeckFrame] = useState<{ y: number; height: number } | null>(null);
  const [chosen, setChosen] = useState(0);
  const [chips, setChips] = useState<Record<number, ChipFrame>>({});
  const chipScroll = useRef<ScrollView>(null);
  const chipRowWidth = useRef(0);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const cardW = Math.min(CARD_MAX, Math.round(width * CARD_SHARE));
  const cardH = Math.round(cardW * CARD_ASPECT);
  const span = Math.max(1, width * DRAG_SPAN);

  const fonts = useMemo<Fonts>(
    () => ({
      region: matchFont({ fontFamily: FAMILY, fontSize: 9.5, fontWeight: '600' }),
      name: matchFont({ fontFamily: FAMILY, fontSize: 21, fontWeight: '600' }),
      blurb: matchFont({ fontFamily: FAMILY, fontSize: 12.5, fontWeight: '500' }),
      chip: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '600' }),
    }),
    [],
  );
  // Every place as the canvas draws it, its blurb set for the card's width.
  const faces = useMemo<CardFace[][]>(
    () =>
      moods.map((m) =>
        m.places.map((p) => ({
          name: p.name,
          region: p.region.toUpperCase(),
          regionX: letterPositions(p.region.toUpperCase(), fonts.region),
          photo: p.photo,
          lines: cardW > 0 ? wrapTwo(p.blurb, fonts.blurb, cardW - 32) : [],
        })),
      ),
    [moods, fonts, cardW],
  );
  const labels = useMemo(() => moods.map((m) => m.label), [moods]);

  // Deck position in cards (grows by one per card turned to the left).
  const progress = useSharedValue(0);
  // +1 while the deck turns on (front card out to the left), −1 while it turns back; eased.
  const heading = useSharedValue(1);
  const lastProgress = useSharedValue(0);
  const grab = useSharedValue(0);
  const dragging = useSharedValue(false);
  // 0 … 1 through a reshuffle; `pending` is the mood it deals. The deal: the mood on the cards and
  // the slot that shows its first place, so a reshuffle keeps the front card in front.
  const shuffle = useSharedValue(0);
  const shuffling = useSharedValue(false);
  const pending = useSharedValue(0);
  const current = useSharedValue(0);
  const dealMood = useSharedValue(0);
  const dealOffset = useSharedValue(0);
  const lastShuffle = useSharedValue(0);
  // The backdrop that fades out after a reshuffle, and when it began to.
  const fadePhoto = useSharedValue('');
  const fadeAt = useSharedValue(-10);
  // Saved places by name, and the last bookmark pop.
  const saved = useSharedValue<Record<string, boolean>>({});
  const popName = useSharedValue('');
  const popAt = useSharedValue(-10);
  const elapsed = useSharedValue(0);
  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const loaded = useSharedValue<Record<string, Loaded>>({});
  const stage = useSharedValue<Stage>({ width: 0, height: 0, cx: 0, cy: 0, cardW: 0, cardH: 0 });
  const pillX = useSharedValue(0);
  const pillWidth = useSharedValue(0);

  useEffect(() => {
    if (!size || !deckFrame) return;
    stage.set({ width, height, cx: width / 2, cy: deckFrame.y + deckFrame.height / 2, cardW, cardH });
  }, [size, deckFrame, width, height, cardW, cardH, stage]);

  useEffect(() => {
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold]);

  // Every photo of every mood, fetched once; each gets its blurred backdrop as it arrives.
  useEffect(() => {
    let alive = true;
    const ids = Array.from(new Set(moods.flatMap((m) => m.places.map((p) => p.photo))));
    for (const id of ids) {
      Skia.Data.fromURI(photoUrl(id, CARD_PHOTO_PX))
        .then((data) => {
          if (!alive) return;
          const encoded = Skia.Image.MakeImageFromEncoded(data);
          if (!encoded) return;
          const image = encoded.makeNonTextureImage() ?? encoded;
          const blur = makeBackdrop(image);
          loaded.modify((all) => {
            'worklet';
            return { ...all, [id]: { image, blur, at: elapsed.get() } };
          });
        })
        .catch(() => {});
    }
    return () => {
      alive = false;
    };
  }, [moods, loaded, elapsed]);

  // The black pill follows the chosen chip, and the row scrolls to keep it in view.
  useEffect(() => {
    const frame = chips[chosen];
    if (!frame) return;
    if (pillWidth.get() === 0 || reduced) {
      pillX.set(frame.x);
      pillWidth.set(frame.width);
    } else {
      pillX.set(withSpring(frame.x, PILL_SPRING));
      pillWidth.set(withSpring(frame.width, PILL_SPRING));
    }
    const view = chipRowWidth.current;
    if (view > 0) chipScroll.current?.scrollTo({ x: Math.max(0, frame.x + frame.width / 2 - view / 2), animated: true });
  }, [chosen, chips, pillX, pillWidth, reduced]);

  const haptic = (kind: 'select' | 'light') => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 'select') Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const announce = (next: number, user: boolean) => {
    setChosen(next);
    if (user) haptic('select');
    onMoodChange?.(labels[next] ?? '');
  };

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  /** Picks a mood: the chip pill moves at once and the deck riffles and deals it. */
  const choose = (next: number, user: boolean) => {
    'worklet';
    if (shuffling.get() || next === current.get() || next < 0 || next >= moods.length) return;
    current.set(next);
    shuffling.set(true);
    pending.set(next);
    scheduleOnRN(announce, next, user);
    // The deck settles on the nearest card first, so the pile gathers from rest. Whatever moves it
    // is stopped first, so the settle always starts from where the deck is (no snap).
    cancelAnimation(progress);
    progress.set(withTiming(Math.round(progress.get()), { duration: 200 }));
    shuffle.set(0);
    shuffle.set(
      withTiming(1, { duration: (reduced ? 0.6 : SHUFFLE_TIME) * 1000, easing: Easing.linear }, () => {
        shuffle.set(0);
        shuffling.set(false);
      }),
    );
  };

  /** Turns the deck one card on: `step` 1 brings the right card forward, −1 the left one. */
  const advance = (step: 1 | -1, byUser: boolean) => {
    'worklet';
    if (shuffling.get()) return;
    const target = Math.round(progress.get()) + step;
    // Whole turns of the deck change nothing on screen, so the numbers are kept small once it lands.
    if (byUser) {
      progress.set(
        withSpring(target, DECK_SPRING, (done) => {
          if (done) progress.set(((progress.get() % COUNT) + COUNT) % COUNT);
        }),
      );
    } else {
      progress.set(
        withTiming(target, { duration: reduced ? 320 : ADVANCE_TIME, easing: ADVANCE_EASE }, (done) => {
          if (done) progress.set(((progress.get() % COUNT) + COUNT) % COUNT);
        }),
      );
    }
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const now = elapsed.get() + dt;
    elapsed.set(now);
    const speed = (progress.get() - lastProgress.get()) / dt;
    lastProgress.set(progress.get());
    // A wrap of the numbers (a whole turn) is not a motion.
    if (Math.abs(speed) > 0.05 && Math.abs(speed) < 40) {
      heading.set(heading.get() + ((speed > 0 ? 1 : -1) - heading.get()) * (1 - Math.exp(-dt / HEADING_EASE)));
    }

    // Half way through a reshuffle, edge-on, the cards take the new mood's faces.
    const sh = shuffle.get();
    if (lastShuffle.get() < 0.5 && sh >= 0.5) {
      const front = ((Math.round(progress.get()) % COUNT) + COUNT) % COUNT;
      fadePhoto.set(faceOf(faces, dealMood.get(), dealOffset.get(), front).photo);
      fadeAt.set(now);
      dealMood.set(pending.get());
      dealOffset.set(front);
    }
    lastShuffle.set(sh);

    if (dragging.get()) return;
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
      return;
    }
    if (!auto) return;
    const before = clock.get();
    const t = (before + dt) % LOOP;
    clock.set(t);
    for (let k = 0; k < CUES.length; k += 1) {
      const at = CUES[k].at;
      const passed = (before < at && t >= at) || (t < before && (at > before || at <= t));
      if (!passed) continue;
      if (CUES[k].next) advance(1, false);
      if (CUES[k].mood !== undefined) choose(CUES[k].mood as number, false);
    }
  });

  const pan = Gesture.Pan()
    .activeOffsetX([-12, 12])
    .failOffsetY([-16, 16])
    .maxPointers(1)
    .onStart(() => {
      if (shuffling.get()) return;
      touched();
      cancelAnimation(progress);
      grab.set(progress.get());
      dragging.set(true);
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      // Dragging left turns the deck on, dragging right turns it back.
      const g = grab.get();
      const base = Math.round(g);
      const raw = g - event.translationX / span;
      const hi = base + 1;
      const lo = base - 1;
      progress.set(raw > hi ? hi + (raw - hi) * RUBBER : raw < lo ? lo + (raw - lo) * RUBBER : raw);
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      dragging.set(false);
      touched();
      const base = Math.round(grab.get());
      const velocity = -event.velocityX / span;
      const projected = progress.get() + velocity * THROW;
      const target = Math.max(base - 1, Math.min(base + 1, Math.round(projected)));
      if (target !== base) scheduleOnRN(haptic, 'light');
      progress.set(
        withSpring(target, { ...DECK_SPRING, velocity }, (done) => {
          if (done) progress.set(((progress.get() % COUNT) + COUNT) % COUNT);
        }),
      );
    })
    .onFinalize(() => {
      dragging.set(false);
    });

  const toggleHaptic = () => haptic('light');

  // Taps on the deck: the bookmark on the front card, or a peeking card to bring it forward.
  const tap = Gesture.Tap()
    .maxDuration(320)
    .onEnd((event, success) => {
      if (!success || shuffling.get()) return;
      touched();
      const room = stage.get();
      if (room.cardW <= 0) return;
      const y = event.y + (deckFrame?.y ?? 0);
      const right = room.cx + room.cardW / 2;
      const topEdge = room.cy - room.cardH / 2;
      const settled = Math.abs(progress.get() - Math.round(progress.get())) < 0.05;
      if (settled && event.x > right - 56 && event.x < right && y > topEdge && y < topEdge + 56) {
        const front = ((Math.round(progress.get()) % COUNT) + COUNT) % COUNT;
        const name = faceOf(faces, dealMood.get(), dealOffset.get(), front).name;
        saved.modify((all) => {
          'worklet';
          return { ...all, [name]: !all[name] };
        });
        popName.set(name);
        popAt.set(elapsed.get());
        scheduleOnRN(toggleHaptic);
        return;
      }
      if (event.x > right) {
        scheduleOnRN(toggleHaptic);
        advance(1, true);
      } else if (event.x < room.cx - room.cardW / 2) {
        scheduleOnRN(toggleHaptic);
        advance(-1, true);
      }
    });

  const picture = useDerivedValue(() => {
    const room = stage.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, room.width), Math.max(1, room.height)));
    if (room.width <= 0 || room.cardW <= 0) {
      const ground = Skia.Paint();
      ground.setColor(Skia.Color('#2a2820'));
      canvas.drawRect(Skia.XYWHRect(0, 0, Math.max(1, room.width), Math.max(1, room.height)), ground);
      return recorder.finishRecordingAsPicture();
    }
    const now = elapsed.get();
    const images = loaded.get();
    const p = progress.get();
    const sh = shuffle.get();
    const mood = dealMood.get();
    const offset = dealOffset.get();
    const marks = saved.get();

    // The backdrop: the front card's photo, blurred, cross-fading with the card arriving.
    const base = Skia.Paint();
    base.setColor(Skia.Color('#2a2820'));
    canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), base);
    let near = 0;
    let second = 1;
    let nearA = 9;
    let secondA = 9;
    for (let i = 0; i < COUNT; i += 1) {
      const a = Math.abs(signedOf(i, p));
      if (a < nearA) {
        second = near;
        secondA = nearA;
        near = i;
        nearA = a;
      } else if (a < secondA) {
        second = i;
        secondA = a;
      }
    }
    const nearPhoto = images[faceOf(faces, mood, offset, near).photo];
    const secondPhoto = images[faceOf(faces, mood, offset, second).photo];
    if (nearPhoto?.blur) drawBackdrop(canvas, nearPhoto.blur, room.width, room.height, clamp01((now - nearPhoto.at) / IMAGE_FADE));
    const mix = nearA + secondA > 0 ? nearA / (nearA + secondA) : 0;
    if (secondPhoto?.blur && mix > 0.002) drawBackdrop(canvas, secondPhoto.blur, room.width, room.height, mix);
    const leaving = images[fadePhoto.get()];
    const fade = 1 - (now - fadeAt.get()) / BACKDROP_FADE;
    if (leaving?.blur && fade > 0) drawBackdrop(canvas, leaving.blur, room.width, room.height, smooth(fade));
    // A warm veil: darker at the top for the title, lighter around the deck.
    const veil = Skia.Paint();
    veil.setShader(
      Skia.Shader.MakeLinearGradient(
        vec(0, 0),
        vec(0, room.height),
        [Float32Array.of(0.07, 0.066, 0.047, 0.46), Float32Array.of(0.07, 0.066, 0.047, 0.1), Float32Array.of(0.07, 0.066, 0.047, 0.4)],
        [0, 0.45, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), veil);

    // The deck, back to front. Where two neighbours in the order are about to trade places, the
    // lower one is drawn again on top, fading, so the trade is a cross-fade.
    const poses: Pose[] = [];
    const head = heading.get();
    for (let i = 0; i < COUNT; i += 1) poses.push(poseOf(i, p, head, sh, reduced, room.cardW));
    const order = [0, 1, 2, 3];
    for (let i = 1; i < COUNT; i += 1) {
      const v = order[i];
      let j = i - 1;
      while (j >= 0 && poses[order[j]].key > poses[v].key) {
        order[j + 1] = order[j];
        j -= 1;
      }
      order[j + 1] = v;
    }
    const popAge = now - popAt.get();
    for (let k = 0; k < COUNT; k += 1) {
      for (let pass = 0; pass < 2; pass += 1) {
        // Pass 0 draws the card; pass 1 the ghost of the card under it.
        if (pass === 1 && k === 0) continue;
        const index = pass === 0 ? order[k] : order[k - 1];
        const pose = poses[index];
        let ghost = 1;
        if (pass === 1) {
          const gap = poses[order[k]].key - pose.key;
          if (gap >= GHOST_BAND) continue;
          ghost = 0.5 * (1 - gap / GHOST_BAND);
        }
        const alpha = pose.alpha * ghost;
        if (alpha <= 0.004) continue;
        const face = faceOf(faces, mood, offset, index);
        let pop = 1;
        if (face.name === popName.get() && popAge < 0.8 && !reduced) {
          pop = popAge < 0.11 ? 1 + (BOOKMARK_POP - 1) * smooth(popAge / 0.11) : 1 + (BOOKMARK_POP - 1) * Math.exp(-(popAge - 0.11) * 7) * Math.cos((popAge - 0.11) * 18);
        }
        canvas.save();
        canvas.concat(Skia.Matrix(cardMatrix(pose, room.cx, room.cy)));
        if (alpha < 0.996) {
          const layer = Skia.Paint();
          layer.setAlphaf(alpha);
          canvas.saveLayer(layer, Skia.XYWHRect(-room.cardW / 2 - 40, -room.cardH / 2 - 40, room.cardW + 80, room.cardH + 80));
        }
        drawCard(canvas, face, images[face.photo], pose, room.cardW, room.cardH, fonts, now, !!marks[face.name], pop, pass === 0);
        if (alpha < 0.996) canvas.restore();
        canvas.restore();
      }
    }
    return recorder.finishRecordingAsPicture();
  });

  const pillStyle = useAnimatedStyle(() => ({
    width: pillWidth.get(),
    transform: [{ translateX: pillX.get() }],
    opacity: pillWidth.get() > 0 ? 1 : 0,
  }));

  const surprise = () => {
    if (moods.length < 2) return;
    let next = Math.floor(Math.random() * (moods.length - 1));
    if (next >= chosen) next += 1;
    choose(next, true);
  };

  return (
    <View
      style={styles.screen}
      onLayout={(event: LayoutChangeEvent) => {
        const { width: w, height: h } = event.nativeEvent.layout;
        setSize({ width: w, height: h });
      }}
    >
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>

      <View style={[styles.column, { paddingTop: insets.top + 30 }]}>
        <Text style={styles.title} accessibilityRole="header">
          {title}
        </Text>

        <GestureDetector gesture={Gesture.Race(pan, tap)}>
          <View
            style={styles.deck}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={`${labels[chosen] ?? ''} places. Swipe to turn the deck.`}
            onLayout={(event: LayoutChangeEvent) => {
              const { y, height: h } = event.nativeEvent.layout;
              setDeckFrame({ y, height: h });
            }}
          />
        </GestureDetector>

        <View style={styles.search}>
          <SymbolView name="magnifyingglass" size={15} weight="semibold" tintColor="rgba(255,255,255,0.92)" />
          <Text style={styles.searchText}>Search places…</Text>
        </View>
      </View>

      <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, 16) + 14 }]}>
        <ScrollView
          ref={chipScroll}
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.chips}
          onLayout={(event: LayoutChangeEvent) => {
            chipRowWidth.current = event.nativeEvent.layout.width;
          }}
        >
          <Animated.View pointerEvents="none" style={[styles.pill, pillStyle]} />
          {labels.map((label, index) => (
            <Pressable
              key={label}
              onPress={() => {
                hold.set(USER_HOLD);
                choose(index, true);
              }}
              onLayout={(event: LayoutChangeEvent) => {
                const { x, width: w } = event.nativeEvent.layout;
                setChips((c) => (c[index]?.x === x && c[index]?.width === w ? c : { ...c, [index]: { x, width: w } }));
              }}
              style={({ pressed }) => [styles.chip, index === chosen && styles.chipChosen, pressed && styles.pressed]}
              accessibilityRole="button"
              accessibilityState={{ selected: index === chosen }}
            >
              <Text style={[styles.chipText, index === chosen && styles.chipTextChosen]}>{label}</Text>
            </Pressable>
          ))}
        </ScrollView>

        <Pressable
          onPress={() => {
            hold.set(USER_HOLD);
            surprise();
          }}
          style={({ pressed }) => [styles.unexpected, pressed && styles.pressed]}
          accessibilityRole="button"
        >
          <View style={styles.unexpectedText}>
            <Text style={styles.unexpectedTitle}>Go Unexpected</Text>
            <Text style={styles.unexpectedLine}>Let the deck pick a mood for you</Text>
          </View>
          <View style={styles.shuffleButton}>
            <SymbolView name="shuffle" size={16} weight="semibold" tintColor="#ffffff" />
          </View>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#2a2820',
  },
  column: {
    flex: 1,
  },
  title: {
    color: '#ffffff',
    fontFamily: FAMILY,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '500',
    letterSpacing: -0.4,
    textAlign: 'center',
    textShadowColor: 'rgba(0,0,0,0.18)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 12,
  },
  deck: {
    flex: 1,
    minHeight: 280,
  },
  search: {
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 24,
    paddingHorizontal: 20,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.14)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.45)',
  },
  searchText: {
    color: 'rgba(255,255,255,0.92)',
    fontFamily: FAMILY,
    fontSize: 15,
    fontWeight: '500',
  },
  sheet: {
    backgroundColor: '#ffffff',
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    borderCurve: 'continuous',
    paddingTop: 20,
    gap: 20,
    boxShadow: '0 -8px 30px rgba(0,0,0,0.12)',
  },
  chips: {
    paddingHorizontal: 20,
    gap: 8,
  },
  pill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    borderRadius: 999,
    backgroundColor: '#11110d',
  },
  chip: {
    height: 44,
    paddingHorizontal: 20,
    borderRadius: 999,
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: 'rgba(17,17,13,0.12)',
  },
  chipChosen: {
    borderColor: 'transparent',
  },
  chipText: {
    color: '#11110d',
    fontFamily: FAMILY,
    fontSize: 15,
    fontWeight: '500',
  },
  chipTextChosen: {
    color: '#ffffff',
  },
  pressed: {
    transform: [{ scale: 0.97 }],
  },
  unexpected: {
    marginHorizontal: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  unexpectedText: {
    flex: 1,
    gap: 2,
  },
  unexpectedTitle: {
    color: '#11110d',
    fontFamily: FAMILY,
    fontSize: 21,
    fontWeight: '600',
    letterSpacing: -0.3,
  },
  unexpectedLine: {
    color: 'rgba(17,17,13,0.55)',
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
  },
  shuffleButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: '#11110d',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
