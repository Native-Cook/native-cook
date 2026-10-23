import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  ColorMatrix,
  FractalNoise,
  Glyphs,
  Group,
  Image as SkiaImage,
  LinearGradient,
  matchFont,
  Paint,
  RadialGradient,
  Rect,
  RoundedRect,
  type SkFont,
  type SkImage,
  Text as SkiaText,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
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
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Snaps two prints, shakes one, throws it under the pile, clears the table and starts over. */
  autoPlay?: boolean;
  /** Called by the primary button. */
  onStart?: () => void;
  /** Called by the secondary button. */
  onSignIn?: () => void;
};

type Shot = { photo: string; caption: string };

// The photos are drawn about 130 pt square; 520 px covers that at 3x with the lift.
const photo = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=520&h=520&q=80`;

// The film in the camera, shot in this order. The captions are written on the border by hand.
const SHOTS: Shot[] = [
  { photo: photo('photo-1526478512290-5397e7d2ca6a'), caption: 'Route 9, first light' },
  { photo: photo('photo-1597649260564-997729f07c5d'), caption: 'Red rock pass' },
  { photo: photo('photo-1578158335529-27b8d78cfea5'), caption: 'Van, mile 212' },
  { photo: photo('photo-1557159557-7a93eaadf72a'), caption: 'Straight on, 90 mi' },
  { photo: photo('photo-1534540378968-85a7b8fde19f'), caption: 'Last of the sun' },
  { photo: photo('photo-1494822493217-c9840aba840c'), caption: 'Snowline in July' },
  { photo: photo('photo-1484687742385-1249620c2687'), caption: 'Parked for pie' },
  { photo: photo('photo-1573097637683-58e6462d2902'), caption: 'Pines all the way' },
];
// Where each print comes to rest on the pile: a tilt (degrees) and an offset (pt), so the pile is
// messy in the same way every time.
const LANDINGS = [
  { tilt: -6, x: -8, y: 4 },
  { tilt: 5, x: 10, y: -2 },
  { tilt: -2, x: -2, y: 8 },
  { tilt: 7.5, x: 13, y: 2 },
  { tilt: -8.5, x: -13, y: -1 },
  { tilt: 3, x: 5, y: 6 },
  { tilt: -4, x: -6, y: -4 },
  { tilt: 6, x: 8, y: 3 },
];
// The pile keeps the newest prints; the fifth from the top has faded away.
const MAX_PRINTS = 5;

const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const HAND = process.env.EXPO_OS === 'ios' ? 'Bradley Hand' : 'cursive';

// Palette: a walnut table under a warm lamp, a cream camera, cream paper and blue ink.
const TABLE_LIGHT = '#57412f';
const TABLE_DARK = '#140e0a';
const PAPER = '#f5f1e8';
const PAPER_SHADE = '#e4dccd';
const INK = '#26365a';
const CREAM = '#f4ebdd';
const SHUTTER = '#c9553b';

// The camera, front on, in pt (the whole hero shrinks on short screens).
const CAMERA_WIDTH = 236;
const CAMERA_HEIGHT = 170;
const CAMERA_RADIUS = 36;
const LENS = 50;
// The print: an instant-film card with a square photo and a deep bottom border.
const PRINT_WIDTH = 152;
const PRINT_HEIGHT = 184;
const PHOTO_INSET = 10;
const PHOTO_SIZE = PRINT_WIDTH - PHOTO_INSET * 2;
const CAPTION_SIZE = 17;
const HERO_HEIGHT = CAMERA_HEIGHT + 22 + PRINT_HEIGHT + 18;

// A snap: the shutter sinks, the flash fires, and after EJECT_DELAY the motor pushes the print
// out of the slot over EJECT_TIME. Before it is all the way out it starts to settle onto the pile
// below on a spring, so the two moves blend into one.
const EJECT_DELAY = 180; // ms
const EJECT_TIME = 1050; // ms
const EJECT_EASE = Easing.bezier(0.33, 0, 0.25, 1);
const EJECT_TUCK = 14; // pt of the print still in the slot when the motor stops
const SETTLE_AT = EJECT_DELAY + EJECT_TIME * 0.72; // ms after the shutter
const SETTLE_SPRING = { duration: 950, dampingRatio: 0.78 };
const SETTLE_LIFT = 0.045; // extra scale halfway, as if lifted towards the viewer
const SHUDDER = 0.6; // pt
const SNAP_GAP = 1.6; // s, the next snap waits for the motor

// Flash: the window lights up and a warm bloom spreads from it.
const FLASH_IN = 50; // ms
const FLASH_OUT = 650; // ms
const BLOOM_PEAK = 0.38;

// Developing: from a dark blank through a pale, washed-out ghost to full colour, over
// DEVELOP_TIME; shaking the held print speeds it up to SHAKE_BOOST + 1 times.
const DEVELOP_TIME = 4.5; // s
const SHAKE_BOOST = 4;
const SHAKE_SPEED = 320; // pt/s of finger speed per unit of boost
const DEVELOP_BLUR = 5;

// The pile: each print under the top one sits 3 pt higher, a little smaller and darker; the
// fourth fades out.
const DEPTH_SPRING = { duration: 520, dampingRatio: 0.85 };
const DEPTH_SCALE = 0.025;
const DEPTH_DIM = 0.07;
// Holding the top print: it lifts, leans into its motion and resists being pushed up into the
// camera. A throw (TOSS_DISTANCE pt or TOSS_SPEED pt/s sideways) sends it under the pile.
const HOLD_LIFT = 0.05;
const TOSS_DISTANCE = 96;
const TOSS_SPEED = 700;
const TOSS_OUT = 0.43; // screen widths
const TOSS_OUT_SPRING = { duration: 380, dampingRatio: 1 };
const TOSS_BACK_SPRING = { duration: 640, dampingRatio: 0.82 };
const RETURN_SPRING = { duration: 520, dampingRatio: 0.72 };
// Clearing the table: the prints fade and sink, the top one first.
const CLEAR_TIME = 700; // ms
const CLEAR_STAGGER = 0.12;

// Arrival on screen: the camera and each line of copy rise ENTER_RISE pt and fade in.
const ENTER_TIME = 700; // ms
const ENTER_RISE = 14;
const ENTER_STAGGER = 90; // ms between lines
const INTRO_SNAP = 1.0; // s, the first snap when not on autoPlay

// autoPlay, in seconds of a LOOP that starts and ends on an empty table.
const LOOP = 14;
const AUTO_SNAPS = [0.6, 4.6];
const AUTO_SHAKE = [7.2, 8.4];
const AUTO_TOSS = 9.6;
const AUTO_CLEAR = 12.0;
const USER_HOLD = 4;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function smooth(x: number) {
  'worklet';
  const t = clamp01(x);
  return t * t * (3 - 2 * t);
}

/** True when the loop clock went past `at` between two frames. */
function crossed(before: number, now: number, at: number) {
  'worklet';
  return before < at && now >= at;
}

/** Pushing a print up past the top of the pile meets more and more resistance (60 pt at most). */
function resistUp(dy: number) {
  'worklet';
  return dy >= 0 ? dy : -60 * (1 - Math.exp(dy / 60));
}

/**
 * The colour of a developing print as a colour matrix, `d` from 0 (just out) to 1 (done). It
 * starts as a flat dark brown with no picture, turns into a pale, low-contrast, slightly cyan
 * ghost of the photo at 42%, and then gains contrast and colour.
 */
function developMatrix(d: number) {
  'worklet';
  // The ghost: a quarter of the colour, half the contrast, lifted towards a cool grey.
  const s = 0.25;
  const c = 0.5;
  const lr = 0.2126 * (1 - s);
  const lg = 0.7152 * (1 - s);
  const lb = 0.0722 * (1 - s);
  const ghost = [
    c * (lr + s), c * lg, c * lb, 0, 0.4,
    c * lr, c * (lg + s), c * lb, 0, 0.43,
    c * lr, c * lg, c * (lb + s), 0, 0.46,
    0, 0, 0, 1, 0,
  ];
  const blank = [0, 0, 0, 0, 0.2, 0, 0, 0, 0, 0.17, 0, 0, 0, 0, 0.14, 0, 0, 0, 1, 0];
  const full = [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0];
  const out: number[] = [];
  if (d < 0.42) {
    const t = smooth(d / 0.42);
    for (let k = 0; k < 20; k += 1) out.push(blank[k] + (ghost[k] - blank[k]) * t);
  } else {
    const t = smooth((d - 0.42) / 0.58);
    for (let k = 0; k < 20; k += 1) out.push(ghost[k] + (full[k] - ghost[k]) * t);
  }
  return out;
}

/** Glyphs of a tracked label (Skia text has no letter spacing). */
function trackedGlyphs(font: SkFont, text: string, tracking: number) {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let x = 0;
  return ids.map((id, i) => {
    const glyph = { id, pos: vec(x, 0) };
    x += widths[i] + tracking;
    return glyph;
  });
}

type Layout = {
  width: number;
  height: number;
  /** The hero (camera and pile) scales by this about the camera's top centre. */
  scale: number;
  cameraX: number;
  cameraY: number;
  slotY: number;
  pileX: number;
  pileY: number;
  headerTop: number;
  copyTop: number;
  primaryTop: number;
};

function layoutFor(width: number, height: number, top: number, bottom: number): Layout {
  // From the bottom up: the secondary action, the primary one, the sub line and the headline.
  const secondaryTop = height - Math.max(bottom, 12) - 6 - 44;
  const primaryTop = secondaryTop - 8 - 56;
  const copyTop = primaryTop - 24 - 44 - 10 - 76;
  const cameraY = top + 56;
  const room = copyTop - 18 - cameraY;
  const scale = Math.max(0.5, Math.min(1, room / HERO_HEIGHT));
  // Hero coordinates are unscaled; spare room splits above and below the pile.
  const spare = Math.max(0, room / scale - HERO_HEIGHT) / 2;
  return {
    width,
    height,
    scale,
    cameraX: (width - CAMERA_WIDTH) / 2,
    cameraY,
    slotY: cameraY + CAMERA_HEIGHT - 9,
    pileX: width / 2,
    pileY: cameraY + CAMERA_HEIGHT + 22 + spare + PRINT_HEIGHT / 2,
    headerTop: top + 8,
    copyTop,
    primaryTop,
  };
}

type PrintData = { key: number; shot: number; byUser: boolean };

type Shared = {
  heldKey: SharedValue<number>;
  heldX: SharedValue<number>;
  heldY: SharedValue<number>;
  heldTilt: SharedValue<number>;
  holding: SharedValue<number>;
  boost: SharedValue<number>;
  tossKey: SharedValue<number>;
  tossX: SharedValue<number>;
  tossY: SharedValue<number>;
  tossTilt: SharedValue<number>;
  tossFade: SharedValue<number>;
  clearing: SharedValue<number>;
  /** Key of the top print once it has settled on the pile (it can be picked up), else -1. */
  readyKey: SharedValue<number>;
};

/**
 * One print: it slides out of the camera's slot and, still moving, settles onto the pile and
 * develops. On the pile it sinks a level each time a newer print lands on it; while it is held
 * or thrown it follows the shared hold and throw offsets.
 */
function Print({ data, depth, layout, shared, image, captionFont, reduced, onLand }: {
  data: PrintData;
  depth: number;
  layout: Layout;
  shared: Shared;
  image: SkImage | null;
  captionFont: SkFont;
  reduced: boolean;
  onLand: () => void;
}) {
  const eject = useSharedValue(reduced ? 1 : 0);
  const settle = useSharedValue(reduced ? 1 : 0);
  const appear = useSharedValue(reduced ? 0 : 1);
  const develop = useSharedValue(0);
  const level = useSharedValue(depth);
  const landing = LANDINGS[data.shot % LANDINGS.length];
  const caption = SHOTS[data.shot % SHOTS.length].caption;
  const captionWidth = useMemo(
    () => captionFont.getGlyphWidths(captionFont.getGlyphIDs(caption)).reduce((sum, w) => sum + w, 0),
    [captionFont, caption],
  );
  const { slotY, pileX, pileY } = layout;
  const key = data.key;
  // Read straight from the shared values, so every worklet below tracks them.
  const { heldKey, heldX, heldY, heldTilt, holding, boost, tossKey, tossX, tossY, tossTilt, tossFade, clearing, readyKey } = shared;

  useEffect(() => {
    level.set(reduced ? withTiming(depth, { duration: 260 }) : withSpring(depth, DEPTH_SPRING));
  }, [depth, level, reduced]);

  useEffect(() => {
    if (reduced) {
      appear.set(withTiming(1, { duration: 420 }));
      return;
    }
    eject.set(withDelay(EJECT_DELAY, withTiming(1, { duration: EJECT_TIME, easing: EJECT_EASE })));
    settle.set(withDelay(SETTLE_AT, withSpring(1, SETTLE_SPRING)));
  }, [appear, eject, settle, reduced]);

  // A print the user took lands with a light haptic.
  useAnimatedReaction(
    () => settle.get() > 0.94 && appear.get() > 0.9,
    (landed, before) => {
      if (landed && before === false && data.byUser) scheduleOnRN(onLand);
    },
  );
  // Once it has come to rest on top of the pile, it can be picked up.
  const onTop = depth === 0;
  useAnimatedReaction(
    () => onTop && settle.get() > 0.94 && appear.get() > 0.9,
    (ready) => {
      if (ready) readyKey.set(key);
      else if (readyKey.get() === key) readyKey.set(-1);
    },
    [onTop],
  );

  useFrameCallback((frame) => {
    const d = develop.get();
    if (d >= 1 || eject.get() < 0.2) return;
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const rate = 1 + (heldKey.get() === key ? boost.get() : 0);
    develop.set(Math.min(1, d + (dt / DEVELOP_TIME) * rate));
  });

  const pose = useDerivedValue(() => {
    const lv = level.get();
    // Its place on the pile, a little higher and smaller for every print on top of it.
    const restX = pileX + landing.x;
    const restY = pileY + landing.y - lv * 3;
    // Coming out of the slot, pushed by the motor with a faint shudder.
    const e = eject.get();
    const shudder = e > 0 && e < 1 ? Math.sin(e * 80) * SHUDDER : 0;
    const outEnd = slotY + PRINT_HEIGHT / 2 - EJECT_TUCK;
    const outY = lerp(slotY - PRINT_HEIGHT / 2, outEnd, e);
    const st = settle.get();
    let x = lerp(pileX + shudder, restX, st);
    let y = outY + (restY - outEnd) * st;
    let tilt = landing.tilt * st;
    let lift = Math.sin(Math.PI * clamp01(st)) * SETTLE_LIFT - lv * DEPTH_SCALE;
    let alpha = clamp01(3.6 - lv);
    if (heldKey.get() === key) {
      x += heldX.get();
      y += resistUp(heldY.get());
      tilt += heldTilt.get();
      lift += holding.get() * HOLD_LIFT;
    }
    if (tossKey.get() === key) {
      x += tossX.get();
      y += tossY.get();
      tilt += tossTilt.get();
      alpha *= 1 - tossFade.get();
    }
    // Clearing the table: fade and sink, the top print first.
    const c = smooth(clearing.get() * (1 + CLEAR_STAGGER * 4) - lv * CLEAR_STAGGER);
    y += c * 18;
    lift -= c * 0.04;
    alpha *= (1 - c) * appear.get();
    return { x, y, tilt, lift, alpha, dim: Math.min(0.3, Math.max(0, lv) * DEPTH_DIM) };
  });
  const transform = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: (p.tilt * Math.PI) / 180 }, { scale: 1 + p.lift }];
  });
  const opacity = useDerivedValue(() => pose.get().alpha);
  const dim = useDerivedValue(() => pose.get().dim);
  const lifted = useDerivedValue(() => Math.max(0, pose.get().lift));
  const shadowOffset = useDerivedValue(() => [{ translateY: 3 + lifted.get() * 160 }]);
  const shadowBlur = useDerivedValue(() => 4 + lifted.get() * 140);
  const shadowOpacity = useDerivedValue(() => 0.55 - lifted.get() * 3);
  const matrix = useDerivedValue(() => developMatrix(develop.get()));
  const blur = useDerivedValue(() => DEVELOP_BLUR * Math.pow(1 - develop.get(), 2));
  // The caption is written left to right once the picture has come through.
  const captionClip = useDerivedValue(() => ({
    x: -PRINT_WIDTH / 2 + PHOTO_INSET,
    y: PRINT_HEIGHT / 2 - 44,
    width: (captionWidth + 12) * smooth((develop.get() - 0.62) / 0.3),
    height: 44,
  }));

  const left = -PRINT_WIDTH / 2;
  const topY = -PRINT_HEIGHT / 2;
  const photoRect = { x: left + PHOTO_INSET, y: topY + PHOTO_INSET, width: PHOTO_SIZE, height: PHOTO_SIZE };
  return (
    // The print's opacity goes on a layer of its own, so it can never reach what is drawn after it.
    <Group transform={transform} layer={<Paint opacity={opacity} />}>
      <Group transform={shadowOffset} opacity={shadowOpacity}>
        <RoundedRect x={left + 3} y={topY + 3} width={PRINT_WIDTH - 6} height={PRINT_HEIGHT - 6} r={3} color="#000000">
          <BlurMask blur={shadowBlur} style="normal" />
        </RoundedRect>
      </Group>
      <RoundedRect x={left} y={topY} width={PRINT_WIDTH} height={PRINT_HEIGHT} r={2.5}>
        <LinearGradient start={vec(left, topY)} end={vec(-left, -topY)} colors={[PAPER, PAPER_SHADE]} />
      </RoundedRect>
      <Group clip={photoRect}>
        <Rect rect={photoRect} color="#2a2420" />
        <Group layer={<Paint><ColorMatrix matrix={matrix} /><Blur blur={blur} mode="clamp" /></Paint>}>
          {image && <SkiaImage image={image} rect={photoRect} fit="cover" />}
        </Group>
        {/* A faint sheen across the glossy face of the film. */}
        <Rect rect={photoRect}>
          <LinearGradient
            start={vec(photoRect.x, photoRect.y)}
            end={vec(photoRect.x + PHOTO_SIZE, photoRect.y + PHOTO_SIZE)}
            colors={['#ffffff26', '#ffffff00', '#ffffff12']}
            positions={[0, 0.55, 1]}
          />
        </Rect>
        <Rect rect={photoRect} style="stroke" strokeWidth={1} color="#00000033" />
      </Group>
      <Group clip={captionClip}>
        <Group transform={[{ translateX: left + PHOTO_INSET + 4 }, { translateY: PRINT_HEIGHT / 2 - 15 }, { rotate: -0.025 }]}>
          <SkiaText x={0} y={0} text={caption} font={captionFont} color={INK} />
        </Group>
      </Group>
      {/* Prints lower in the pile sit in the shade of the ones above. */}
      <RoundedRect x={left} y={topY} width={PRINT_WIDTH} height={PRINT_HEIGHT} r={2.5} color="#1a1009" opacity={dim} />
    </Group>
  );
}

/**
 * The instant camera, front on: a cream body with a darker lower band, a big lens in a ringed
 * barrel, a ribbed flash window, a viewfinder, a terracotta shutter button and the film slot.
 */
function Camera({ layout, shutter, flash, ready, time, plateFont }: {
  layout: Layout;
  shutter: SharedValue<number>;
  flash: SharedValue<number>;
  ready: SharedValue<number>;
  time: SharedValue<number>;
  plateFont: SkFont;
}) {
  const { cameraX: x, cameraY: y } = layout;
  const w = CAMERA_WIDTH;
  const h = CAMERA_HEIGHT;
  const r = CAMERA_RADIUS;
  const lensX = x + w / 2;
  const lensY = y + h * 0.5;
  const buttonX = x + w - 38;
  const buttonY = y + h - 44;
  // The shutter sinks into the body; the whole camera gives a tiny jolt.
  const jolt = useDerivedValue(() => [{ translateY: shutter.get() * 1.5 }]);
  const button = useDerivedValue(() => 11 - shutter.get() * 2);
  // While the camera is ready, a soft ring breathes out of the shutter button every 2.4 s.
  const pulse = useDerivedValue(() => (time.get() % 2.4) / 2.4);
  const pulseR = useDerivedValue(() => 15 + pulse.get() * 14);
  const pulseOpacity = useDerivedValue(() => (1 - pulse.get()) * 0.5 * ready.get());
  const plate = useMemo(() => trackedGlyphs(plateFont, 'ROADBOOK', 1.6), [plateFont]);
  const seam = y + h * 0.66;
  return (
    <Group transform={jolt}>
      {/* Contact shadow on the table. */}
      <RoundedRect x={x + 10} y={y + 18} width={w - 20} height={h - 6} r={r} color="#000000" opacity={0.6}>
        <BlurMask blur={18} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={w} height={h} r={r}>
        <LinearGradient start={vec(0, y)} end={vec(0, y + h)} colors={['#f6eee1', '#e7dcc9', '#d9ccb6']} positions={[0, 0.6, 1]} />
      </RoundedRect>
      {/* The lower band, a shade darker, split from the top by a fine seam. */}
      <Group clip={{ x, y: seam, width: w, height: h }}>
        <RoundedRect x={x} y={y} width={w} height={h} r={r}>
          <LinearGradient start={vec(0, seam)} end={vec(0, y + h)} colors={['#d8c9b0', '#c6b498']} />
        </RoundedRect>
      </Group>
      <Rect x={x + 4} y={seam} width={w - 8} height={1} color="#a8977c" opacity={0.7} />
      <Rect x={x + 4} y={seam + 1} width={w - 8} height={1} color="#ffffff" opacity={0.35} />
      {/* Light along the top edge, shade along the bottom. */}
      <RoundedRect x={x + 0.75} y={y + 0.75} width={w - 1.5} height={h - 1.5} r={r - 0.75} style="stroke" strokeWidth={1.5}>
        <LinearGradient start={vec(0, y)} end={vec(0, y + h)} colors={['#ffffffcc', '#ffffff00', '#00000040']} positions={[0, 0.4, 1]} />
      </RoundedRect>

      {/* Viewfinder, top left, and the maker's plate on the lower band. */}
      <RoundedRect x={x + 24} y={y + 18} width={36} height={26} r={7} color="#2b2622" />
      <RoundedRect x={x + 27} y={y + 21} width={30} height={20} r={5}>
        <LinearGradient start={vec(x + 27, y + 21)} end={vec(x + 57, y + 41)} colors={['#4a5a72', '#0e1219']} />
      </RoundedRect>
      <RoundedRect x={x + 30} y={y + 23} width={9} height={3.5} r={1.75} color="#ffffff" opacity={0.4} />
      <Group transform={[{ translateX: x + 24 }, { translateY: y + h - 28 }]}>
        <Glyphs font={plateFont} glyphs={plate} color="#8f7d62" />
      </Group>

      {/* Flash window, top right, with its ribbed lens; it lights up when it fires. */}
      <RoundedRect x={x + w - 84} y={y + 16} width={62} height={30} r={7} color="#b7ad9d" />
      <RoundedRect x={x + w - 82} y={y + 18} width={58} height={26} r={6}>
        <LinearGradient start={vec(0, y + 18)} end={vec(0, y + 44)} colors={['#fdfcf8', '#d8d2c6']} />
      </RoundedRect>
      {[0, 1, 2, 3, 4, 5, 6].map((k) => (
        <Rect key={k} x={x + w - 76.5 + k * 7.5} y={y + 19} width={1} height={24} color="#b5ad9f" opacity={0.75} />
      ))}
      <Group opacity={flash}>
        <RoundedRect x={x + w - 94} y={y + 8} width={82} height={46} r={14} color="#ffffff">
          <BlurMask blur={14} style="normal" />
        </RoundedRect>
        <RoundedRect x={x + w - 82} y={y + 18} width={58} height={26} r={6} color="#ffffff" />
      </Group>

      {/* Lens: a cream outer ring, a dark barrel with fine rings, and the glass. */}
      <Circle cx={lensX} cy={lensY + 4} r={LENS + 8} color="#000000" opacity={0.28}>
        <BlurMask blur={8} style="normal" />
      </Circle>
      <Circle cx={lensX} cy={lensY} r={LENS + 6}>
        <LinearGradient start={vec(lensX, lensY - LENS)} end={vec(lensX, lensY + LENS)} colors={['#fbf6ee', '#cbbda6']} />
      </Circle>
      <Circle cx={lensX} cy={lensY} r={LENS}>
        <LinearGradient start={vec(lensX, lensY - LENS)} end={vec(lensX, lensY + LENS)} colors={['#45403b', '#121110']} />
      </Circle>
      <Circle cx={lensX} cy={lensY} r={LENS - 8} color="#0e0d0c" />
      <Circle cx={lensX} cy={lensY} r={LENS - 11} style="stroke" strokeWidth={1.2} color="#5a554f" />
      <Circle cx={lensX} cy={lensY} r={LENS - 17}>
        <RadialGradient c={vec(lensX - 7, lensY - 9)} r={LENS - 12} colors={['#46608a', '#16213a', '#04060a']} positions={[0, 0.55, 1]} />
      </Circle>
      <Circle cx={lensX} cy={lensY} r={10} color="#020306" opacity={0.85} />
      <Circle cx={lensX - 12} cy={lensY - 13} r={6.5} color="#ffffff" opacity={0.4} />
      <Circle cx={lensX + 11} cy={lensY + 12} r={2.2} color="#ffffff" opacity={0.28} />

      {/* Shutter button, with its ready pulse. */}
      <Circle cx={buttonX} cy={buttonY} r={pulseR} style="stroke" strokeWidth={2} color={SHUTTER} opacity={pulseOpacity} />
      <Circle cx={buttonX} cy={buttonY} r={15} color="#b9a78b" />
      <Circle cx={buttonX} cy={buttonY} r={13.5} color="#8b7a61" opacity={0.6} />
      <Circle cx={buttonX} cy={buttonY} r={button}>
        <RadialGradient c={vec(buttonX - 4, buttonY - 5)} r={14} colors={['#e57259', SHUTTER, '#9d3a25']} positions={[0, 0.6, 1]} />
      </Circle>

      {/* The film slot along the bottom edge. */}
      <RoundedRect x={x + w / 2 - PRINT_WIDTH / 2 - 9} y={y + h - 13} width={PRINT_WIDTH + 18} height={7} r={3.5} color="#120f0c" />
      <Rect x={x + w / 2 - PRINT_WIDTH / 2 - 6} y={y + h - 6} width={PRINT_WIDTH + 12} height={1} color="#ffffff" opacity={0.3} />
    </Group>
  );
}

/** The app's mark: a print with a road running into it. */
function Mark() {
  return (
    <Svg width={22} height={22} viewBox="0 0 22 22">
      <Path d="M4 4h14a1.5 1.5 0 0 1 1.5 1.5V18h-17V5.5A1.5 1.5 0 0 1 4 4Z" stroke={CREAM} strokeWidth={1.9} fill="none" strokeLinejoin="round" />
      <Path d="M8.6 18 10.4 9.6M13.4 18 11.6 9.6" stroke="#e0a46a" strokeWidth={1.9} fill="none" strokeLinecap="round" />
    </Svg>
  );
}

/** One element arriving: it rises and fades in after `delay` ms. */
function useEnter(delay: number, reduced: boolean) {
  const t = useSharedValue(0);
  useEffect(() => {
    t.set(withDelay(delay, withTiming(1, { duration: ENTER_TIME, easing: EASE_OUT })));
  }, [delay, t]);
  return useAnimatedStyle(() => ({
    opacity: t.get(),
    transform: [{ translateY: reduced ? 0 : (1 - t.get()) * ENTER_RISE }],
  }));
}

/**
 * A welcome screen built around an instant camera. It opens with the camera taking a photo: the
 * flash fires and a print slides out of the slot and, still moving, settles onto the table below,
 * where it develops from a dark blank through a washed-out ghost to full colour and a caption
 * writes itself on its border. Tap the shutter for more; hold the top print and shake it to
 * develop it faster, or throw it aside to send it under the pile. A headline, a sub line, a
 * primary and a secondary action sit below.
 */
export function PolaroidDevelopWelcome({ autoPlay = false, onStart, onSignIn }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const layout = useMemo(
    () => (size ? layoutFor(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  // The prints on the table, bottom of the pile first; `taken` picks the next shot on the film.
  const [film, setFilm] = useState<{ prints: PrintData[]; taken: number; key: number }>({ prints: [], taken: 0, key: 0 });
  const prints = film.prints;

  const captionFont = useMemo(() => matchFont({ fontFamily: HAND, fontSize: CAPTION_SIZE, fontWeight: 'bold' }), []);
  const plateFont = useMemo(() => matchFont({ fontFamily: SANS, fontSize: 8, fontWeight: '700' }), []);
  const images = [
    useImage(SHOTS[0].photo),
    useImage(SHOTS[1].photo),
    useImage(SHOTS[2].photo),
    useImage(SHOTS[3].photo),
    useImage(SHOTS[4].photo),
    useImage(SHOTS[5].photo),
    useImage(SHOTS[6].photo),
    useImage(SHOTS[7].photo),
  ];

  const shared: Shared = {
    heldKey: useSharedValue(-1),
    heldX: useSharedValue(0),
    heldY: useSharedValue(0),
    heldTilt: useSharedValue(0),
    holding: useSharedValue(0),
    boost: useSharedValue(0),
    tossKey: useSharedValue(-1),
    tossX: useSharedValue(0),
    tossY: useSharedValue(0),
    tossTilt: useSharedValue(0),
    tossFade: useSharedValue(0),
    clearing: useSharedValue(0),
    readyKey: useSharedValue(-1),
  };
  const { heldKey, heldX, heldY, heldTilt, holding, boost, tossKey, tossX, tossY, tossTilt, tossFade, clearing, readyKey } = shared;
  const shutter = useSharedValue(0);
  const flash = useSharedValue(0);
  const cameraPress = useSharedValue(0);
  const count = useSharedValue(0);
  const time = useSharedValue(0);
  const lastSnap = useSharedValue(-10);
  const clock = useSharedValue(0);
  const looping = useSharedValue(autoPlay);
  const hold = useSharedValue(0);
  const introDone = useSharedValue(autoPlay);
  const autoShaking = useSharedValue(false);
  const enterCamera = useSharedValue(0);
  const primaryPress = useSharedValue(0);
  const readyGlow = useSharedValue(0);
  const screenWidth = useSharedValue(1);
  const grabX = useSharedValue(0);
  const grabY = useSharedValue(0);
  const grabbing = useSharedValue(false);

  useEffect(() => {
    looping.set(autoPlay);
    clock.set(0);
    hold.set(0);
  }, [autoPlay, clock, hold, looping]);

  useEffect(() => {
    enterCamera.set(withTiming(1, { duration: ENTER_TIME, easing: EASE_OUT }));
  }, [enterCamera]);

  useEffect(() => {
    if (layout) screenWidth.set(layout.width);
  }, [layout, screenWidth]);

  useEffect(() => {
    count.set(prints.length);
    // Once the cleared prints are gone, the table is ready for the next ones.
    if (prints.length === 0) clearing.set(0);
  }, [prints.length, count, clearing]);

  const addPrint = (byUser: boolean) => {
    if (byUser && process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    setFilm((f) => ({
      prints: [...f.prints, { key: f.key + 1, shot: f.taken % SHOTS.length, byUser }].slice(-MAX_PRINTS),
      taken: f.taken + 1,
      key: f.key + 1,
    }));
  };
  const clearPrints = () => {
    setFilm((f) => ({ prints: [], taken: 0, key: f.key }));
  };
  /** Moves the thrown print to the bottom of the pile. */
  const sendToBack = (key: number) => {
    setFilm((f) => {
      const thrown = f.prints.find((p) => p.key === key);
      if (!thrown) return f;
      return { ...f, prints: [thrown, ...f.prints.filter((p) => p.key !== key)] };
    });
  };
  const landed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const thrown = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  const cleared = (done?: boolean) => {
    'worklet';
    if (done) scheduleOnRN(clearPrints);
  };

  /** Fires the shutter: flash, a jolt, and a new print on its way out of the camera. */
  const snap = (byUser: boolean) => {
    'worklet';
    if (time.get() - lastSnap.get() < SNAP_GAP || clearing.get() > 0) return;
    lastSnap.set(time.get());
    shutter.set(withSequence(withTiming(1, { duration: 70 }), withTiming(0, { duration: 200 })));
    const peak = reduced ? 0.35 : 1;
    flash.set(withSequence(withTiming(peak, { duration: FLASH_IN }), withTiming(0, { duration: FLASH_OUT, easing: EASE_OUT })));
    scheduleOnRN(addPrint, byUser);
  };
  const clear = () => {
    'worklet';
    if (count.get() === 0) return;
    clearing.set(withTiming(1, { duration: reduced ? 400 : CLEAR_TIME, easing: Easing.bezier(0.4, 0, 0.6, 1) }, cleared));
  };

  const backDone = (done?: boolean) => {
    'worklet';
    if (done) tossKey.set(-1);
  };
  /** The thrown print is out to the side: it goes under the pile and slides back in. */
  const tossedOut = (key: number) => {
    'worklet';
    scheduleOnRN(sendToBack, key);
    // Back in a few frames after the pile has been reordered.
    if (reduced) {
      tossX.set(0);
      tossY.set(0);
      tossTilt.set(0);
      tossFade.set(withDelay(60, withTiming(0, { duration: 220 }, backDone)));
      return;
    }
    tossX.set(withDelay(60, withSpring(0, TOSS_BACK_SPRING, backDone)));
    tossY.set(withDelay(60, withSpring(0, TOSS_BACK_SPRING)));
    tossTilt.set(withDelay(60, withSpring(0, TOSS_BACK_SPRING)));
  };
  /** Throws the held top print aside (`direction` ±1), from where it slides back under the pile. */
  const toss = (key: number, direction: number, velocity: number) => {
    'worklet';
    tossKey.set(key);
    tossX.set(heldKey.get() === key ? heldX.get() : 0);
    tossY.set(heldKey.get() === key ? resistUp(heldY.get()) : 0);
    tossTilt.set(heldKey.get() === key ? heldTilt.get() : 0);
    tossFade.set(0);
    heldKey.set(-1);
    heldX.set(0);
    heldY.set(0);
    heldTilt.set(0);
    holding.set(0);
    if (reduced) {
      tossFade.set(withTiming(1, { duration: 200 }, (done) => {
        if (done) tossedOut(key);
      }));
      return;
    }
    tossY.set(withSpring(22, TOSS_OUT_SPRING));
    tossTilt.set(withSpring(direction * 14, TOSS_OUT_SPRING));
    tossX.set(withSpring(direction * screenWidth.get() * TOSS_OUT, { ...TOSS_OUT_SPRING, velocity }, (done) => {
      if (done) tossedOut(key);
    }));
  };
  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    const ready = time.get() - lastSnap.get() >= SNAP_GAP && clearing.get() === 0 ? 1 : 0;
    readyGlow.set(readyGlow.get() + (ready - readyGlow.get()) * (1 - Math.exp(-dt * 4)));
    if (!introDone.get() && time.get() >= INTRO_SNAP) {
      introDone.set(true);
      snap(false);
    }
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        // Start over from an empty table: clear what the user left first.
        if (count.get() > 0) {
          clock.set(AUTO_CLEAR);
          clear();
        } else clock.set(0);
      }
      return;
    }
    if (!looping.get()) return;
    const before = clock.get();
    const now = before + dt;
    for (let k = 0; k < AUTO_SNAPS.length; k += 1) {
      if (crossed(before, now, AUTO_SNAPS[k])) snap(false);
    }
    // Shaking the top print: it is held and wiggled, which develops it faster.
    if (crossed(before, now, AUTO_SHAKE[0]) && readyKey.get() >= 0 && tossKey.get() < 0) {
      autoShaking.set(true);
      heldKey.set(readyKey.get());
      holding.set(withTiming(1, { duration: 140 }));
    }
    if (autoShaking.get() && now < AUTO_SHAKE[1]) {
      const s = (now - AUTO_SHAKE[0]) / (AUTO_SHAKE[1] - AUTO_SHAKE[0]);
      const envelope = Math.sin(Math.PI * clamp01(s));
      heldX.set(Math.sin(now * Math.PI * 2 * 3.2) * 12 * envelope);
      heldY.set(-6 * envelope);
      heldTilt.set(Math.cos(now * Math.PI * 2 * 3.2) * 5 * envelope);
      boost.set(SHAKE_BOOST * envelope);
    }
    if (crossed(before, now, AUTO_SHAKE[1]) && autoShaking.get()) {
      autoShaking.set(false);
      heldX.set(withSpring(0, RETURN_SPRING));
      heldY.set(withSpring(0, RETURN_SPRING));
      heldTilt.set(withSpring(0, RETURN_SPRING));
      holding.set(withTiming(0, { duration: 220 }));
      boost.set(withTiming(0, { duration: 600 }));
    }
    if (crossed(before, now, AUTO_TOSS) && readyKey.get() >= 0 && count.get() > 1 && tossKey.get() < 0) {
      toss(readyKey.get(), 1, 900);
    }
    if (crossed(before, now, AUTO_CLEAR)) clear();
    clock.set(now >= LOOP ? now - LOOP : now);
  });

  const scale = layout?.scale ?? 1;
  const width = layout?.width ?? 1;
  const cameraX = layout?.cameraX ?? 0;
  const cameraY = layout?.cameraY ?? 0;
  const pileX = layout?.pileX ?? 0;
  const pileY = layout?.pileY ?? 0;

  /** Screen point to hero coordinates (the hero scales about the camera's top centre). */
  const heroX = (x: number) => {
    'worklet';
    return width / 2 + (x - width / 2) / scale;
  };
  const heroY = (y: number) => {
    'worklet';
    return cameraY + (y - cameraY) / scale;
  };
  const onCamera = (x: number, y: number) => {
    'worklet';
    const hx = heroX(x);
    const hy = heroY(y);
    return hx > cameraX - 8 && hx < cameraX + CAMERA_WIDTH + 8 && hy > cameraY - 8 && hy < cameraY + CAMERA_HEIGHT + 8;
  };
  const onPile = (x: number, y: number) => {
    'worklet';
    const hx = heroX(x);
    const hy = heroY(y);
    return Math.abs(hx - pileX) < PRINT_WIDTH * 0.62 && Math.abs(hy - pileY) < PRINT_HEIGHT * 0.6;
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      touched();
    })
    .onStart((event) => {
      // The top print can be picked up once it has settled, from where it is now.
      const top = readyKey.get();
      if (top < 0 || tossKey.get() >= 0 || clearing.get() > 0 || !onPile(event.x, event.y)) return;
      autoShaking.set(false);
      if (heldKey.get() !== top) {
        heldX.set(0);
        heldY.set(0);
        heldTilt.set(0);
      }
      heldKey.set(top);
      grabbing.set(true);
      grabX.set(heldX.get() - event.translationX / scale);
      grabY.set(heldY.get() - event.translationY / scale);
      holding.set(withTiming(1, { duration: 140 }));
    })
    .onUpdate((event) => {
      touched();
      const key = heldKey.get();
      if (!grabbing.get() || key < 0 || key === tossKey.get()) return;
      heldX.set(grabX.get() + event.translationX / scale);
      heldY.set(grabY.get() + event.translationY / scale);
      // The print leans into the way it is moving, a little like a card held by its edge.
      heldTilt.set(Math.max(-16, Math.min(16, heldX.get() * 0.05 + event.velocityX * 0.006)));
      // Shaking speeds up developing.
      const speed = Math.hypot(event.velocityX, event.velocityY);
      boost.set(boost.get() + (Math.min(SHAKE_BOOST, speed / SHAKE_SPEED) - boost.get()) * 0.2);
    })
    .onEnd((event) => {
      const key = heldKey.get();
      if (!grabbing.get() || key < 0) return;
      const throwIt = count.get() > 1 && (Math.abs(heldX.get()) > TOSS_DISTANCE || Math.abs(event.velocityX) > TOSS_SPEED);
      if (throwIt) {
        const direction = (Math.abs(event.velocityX) > TOSS_SPEED ? event.velocityX : heldX.get()) > 0 ? 1 : -1;
        toss(key, direction, event.velocityX / scale);
        scheduleOnRN(thrown);
        return;
      }
      // Back to its place, carrying the finger's speed.
      heldX.set(withSpring(0, { ...RETURN_SPRING, velocity: event.velocityX / scale }));
      heldY.set(withSpring(0, { ...RETURN_SPRING, velocity: event.velocityY / scale }));
      heldTilt.set(withSpring(0, RETURN_SPRING));
    })
    .onFinalize(() => {
      grabbing.set(false);
      if (!autoShaking.get()) holding.set(withTiming(0, { duration: 220 }));
      boost.set(withTiming(0, { duration: 600 }));
    });

  const tap = Gesture.Tap()
    .maxDuration(500)
    .onBegin((event) => {
      if (onCamera(event.x, event.y)) cameraPress.set(withTiming(1, { duration: 90 }));
    })
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      if (onCamera(event.x, event.y)) snap(true);
    })
    .onFinalize(() => {
      cameraPress.set(withTiming(0, { duration: 200 }));
    });

  const bloom = useDerivedValue(() => flash.get() * (reduced ? 0.35 : BLOOM_PEAK));
  const heroTransform = useDerivedValue(() => {
    const e = enterCamera.get();
    const press = 1 - cameraPress.get() * 0.015;
    return [
      { translateX: width / 2 },
      { translateY: cameraY + (reduced ? 0 : (1 - e) * ENTER_RISE) },
      { scale: scale * press },
      { translateX: -width / 2 },
      { translateY: -cameraY },
    ];
  });
  // Prints are hidden above the camera's middle, so one still inside the camera never shows.
  const printsClip = useMemo(
    () => ({ x: -width, y: cameraY + 30, width: width * 3, height: (layout?.height ?? 1) * 2 }),
    [width, cameraY, layout?.height],
  );

  const headerStyle = useEnter(0, reduced);
  const titleStyle = useEnter(ENTER_STAGGER * 2, reduced);
  const subStyle = useEnter(ENTER_STAGGER * 3, reduced);
  const actionsStyle = useEnter(ENTER_STAGGER * 4, reduced);
  const primaryStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - primaryPress.get() * 0.03 }] }));

  return (
    <View
      style={styles.screen}
      onLayout={(event: LayoutChangeEvent) => {
        const { width: w, height: h } = event.nativeEvent.layout;
        if (w > 0 && h > 0) setSize({ width: w, height: h });
      }}
    >
      <GestureDetector gesture={Gesture.Race(pan, tap)}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Instant camera. Tap the shutter to take a photo."
          onAccessibilityTap={() => snap(true)}
        >
          {layout && (
            <Canvas style={StyleSheet.absoluteFill}>
              <Rect x={0} y={0} width={layout.width} height={layout.height}>
                <RadialGradient c={vec(layout.width / 2, layout.pileY - 60)} r={layout.height * 0.7} colors={[TABLE_LIGHT, TABLE_DARK]} />
              </Rect>
              {/* Grain, so the table reads as a surface rather than a gradient. */}
              <Rect x={0} y={0} width={layout.width} height={layout.height} opacity={0.07}>
                <FractalNoise freqX={0.9} freqY={0.07} octaves={3} />
              </Rect>
              {/* The lamp's pool of light falls off towards the copy. */}
              <Rect x={0} y={layout.copyTop - 120} width={layout.width} height={layout.height - layout.copyTop + 120}>
                <LinearGradient start={vec(0, layout.copyTop - 120)} end={vec(0, layout.copyTop + 40)} colors={['#140e0a00', '#140e0ad9']} />
              </Rect>
              <Group transform={heroTransform} opacity={enterCamera}>
                <Group clip={printsClip}>
                  {prints.map((data, rank) => (
                    <Print
                      key={data.key}
                      data={data}
                      depth={prints.length - 1 - rank}
                      layout={layout}
                      shared={shared}
                      image={images[data.shot % images.length]}
                      captionFont={captionFont}
                      reduced={reduced}
                      onLand={landed}
                    />
                  ))}
                </Group>
                <Camera layout={layout} shutter={shutter} flash={flash} ready={readyGlow} time={time} plateFont={plateFont} />
              </Group>
              {/* The flash's warm bloom, spreading from the flash window. */}
              <Rect x={0} y={0} width={layout.width} height={layout.height} opacity={bloom}>
                <RadialGradient
                  c={vec(layout.cameraX + CAMERA_WIDTH - 53, layout.cameraY + 31)}
                  r={layout.height * 0.6}
                  colors={['#fff8eaff', '#fff1d8aa', '#fff1d800']}
                  positions={[0, 0.35, 1]}
                />
              </Rect>
            </Canvas>
          )}
        </View>
      </GestureDetector>

      {layout && (
        <>
          <Animated.View pointerEvents="none" style={[styles.header, { top: layout.headerTop }, headerStyle]}>
            <Mark />
            <Text style={styles.brand}>Roadbook</Text>
          </Animated.View>
          <View pointerEvents="none" style={[styles.copy, { top: layout.copyTop }]}>
            <Animated.Text style={[styles.title, titleStyle]}>Every mile,{'\n'}one frame.</Animated.Text>
            <Animated.Text style={[styles.sub, subStyle]}>
              A travel journal that develops itself, one instant photo for every stop.
            </Animated.Text>
          </View>
          <Animated.View style={[styles.actions, { top: layout.primaryTop }, actionsStyle]}>
            <Animated.View style={primaryStyle}>
              <Pressable
                onPress={() => {
                  if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
                  onStart?.();
                }}
                onPressIn={() => primaryPress.set(withTiming(1, { duration: 110 }))}
                onPressOut={() => primaryPress.set(withTiming(0, { duration: 220 }))}
                style={styles.primary}
                accessibilityRole="button"
              >
                <Text style={styles.primaryText}>Start your roadbook</Text>
              </Pressable>
            </Animated.View>
            <Pressable
              onPress={() => onSignIn?.()}
              style={({ pressed }) => [styles.secondary, { opacity: pressed ? 0.55 : 1 }]}
              accessibilityRole="button"
            >
              <Text style={styles.secondaryText}>I already have an account</Text>
            </Pressable>
          </Animated.View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: TABLE_DARK,
  },
  header: {
    position: 'absolute',
    left: 28,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  brand: {
    fontFamily: SANS,
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: -0.2,
    color: CREAM,
  },
  copy: {
    position: 'absolute',
    left: 28,
    right: 28,
    gap: 10,
  },
  title: {
    fontFamily: SANS,
    fontSize: 34,
    fontWeight: '700',
    lineHeight: 38,
    letterSpacing: -0.6,
    color: CREAM,
  },
  sub: {
    fontFamily: SANS,
    fontSize: 16,
    fontWeight: '500',
    lineHeight: 22,
    color: '#f4ebdd9e',
  },
  actions: {
    position: 'absolute',
    left: 28,
    right: 28,
    gap: 8,
  },
  primary: {
    height: 56,
    borderRadius: 28,
    borderCurve: 'continuous',
    backgroundColor: CREAM,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryText: {
    fontFamily: SANS,
    fontSize: 17,
    fontWeight: '700',
    color: TABLE_DARK,
  },
  secondary: {
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryText: {
    fontFamily: SANS,
    fontSize: 15,
    fontWeight: '600',
    color: '#f4ebddd0',
  },
});
