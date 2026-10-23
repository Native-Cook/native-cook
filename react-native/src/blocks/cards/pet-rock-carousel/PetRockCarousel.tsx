import {
  Blur,
  BlurMask,
  Canvas,
  ColorMatrix,
  FractalNoise,
  Group,
  ImageShader,
  LinearGradient,
  matchFont,
  Oval,
  Paint,
  Path,
  RadialGradient,
  Rect,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  interpolate,
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
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { FACE_COUNT, type FaceFonts, FacePaint, FaceStickers, MOODS } from './PetRockFaces';
import { makeRockPath, makeSpeckles } from './petRockShapes';

type Rock = {
  name: string;
  /** Unsplash photo id: the stone's surface, laid over its base colour. */
  photo: string;
  /** Base colour of the stone. */
  tone: string;
  /** Shape seed, vertical squash and resting tilt (rad). */
  seed: number;
  aspect: number;
  tilt: number;
  /** The face it wears when the carousel opens. */
  face: number;
};

const ROCKS: Rock[] = [
  { name: 'Gus', photo: 'photo-1604951832436-90867d74f4de', tone: '#8b8680', seed: 7, aspect: 0.9, tilt: -0.1, face: 0 },
  { name: 'Marlo', photo: 'photo-1650755743867-6ad2037a2934', tone: '#998a78', seed: 21, aspect: 0.95, tilt: 0.06, face: 1 },
  { name: 'Dot', photo: 'photo-1597188702867-0a052cddba09', tone: '#948a80', seed: 33, aspect: 0.92, tilt: 0.04, face: 2 },
  { name: 'Sven', photo: 'photo-1665706782455-f8e800c8aeaf', tone: '#86898b', seed: 48, aspect: 0.82, tilt: -0.05, face: 3 },
  { name: 'Ruby', photo: 'photo-1640832956551-eaeab27afbdc', tone: '#9e8e7e', seed: 59, aspect: 0.96, tilt: 0.08, face: 4 },
  { name: 'Otis', photo: 'photo-1725959523676-556f1fadaf2f', tone: '#8e8a84', seed: 71, aspect: 0.88, tilt: -0.04, face: 5 },
];
const DEFAULT_FACES = ROCKS.map((rock) => rock.face);

const PAPER = '#edeae4';
const PAPER_TOP = '#f2f0eb';
const PAPER_BOTTOM = '#e6e2db';
const INK = '#141414';
const MUTED = '#8a857d';
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

// Sizes (pt). The focused rock is BIG across, at most this share of the screen's width; the
// rocks next to it are SMALL of that, and each one further away a little smaller again.
const BIG_MAX = 250;
const BIG_SHARE = 0.62;
const SMALL = 0.46;
const GAP = 22;
const GAP_FAR = 16;
// The carousel's centre line, as a share of the screen's height.
const CENTRE_AT = 0.5;
// The intro: the little rock that stands for the "O" between the two words, and the title.
const TITLE_ROCK = 50;
const TITLE_SIZE = 38;
const TITLE_AT = 0.46;
// Each letter of the title leaves this far (pt), the one nearest the rock first.
const SPLIT = 110;
const SPLIT_LAG = 0.07;
// A rock image is cropped to this much of the photo's middle, so the stone's surface fills it.
const PHOTO_ZOOM = 1.7;
const PHOTO_WIDTH = 520;

// Dragging: finger travel per rock is the distance between the focused rock and the next.
const DRAG_SLOP = 8;
// Past the ends the carousel gives at most this much of a rock, in a rubber band.
const RUBBER = 0.55;
// A release throws the carousel this many seconds ahead before it picks a rock.
const THROW_TIME = 0.16;
// The snap onto a rock: a spring with a little give.
const SNAP = { mass: 1, stiffness: 170, damping: 21 };
// A rock has arrived when it is this close to the front (in rocks) and slower than this (rocks/s).
const ARRIVED_AT = 0.02;
const ARRIVED_SPEED = 0.25;
// Intro ⇄ carousel: a spring that lets the rock grow a touch past its size and settle.
const INTRO_SPRING = { mass: 1, stiffness: 120, damping: 17 };
const FADE_TIME = 280;

// The face morph: after a rock comes to rest it waits MORPH_DELAY ms, then the old paint blurs
// out while the new one sharpens in, over MORPH_TIME ms, and the rock swells by MORPH_POP.
const MORPH_DELAY = 220;
const MORPH_TIME = 720;
const MORPH_BLUR = 9;
const MORPH_POP = 0.035;
const MORPH_EASE = Easing.bezier(0.45, 0, 0.2, 1);

// A tap squashes the rock (wider, shorter, standing on its base), then it springs back past
// round and wobbles about its base.
const SQUASH_X = 0.12;
const SQUASH_Y = 0.15;
const WOBBLE = 0.14;

// Googly pupils: a loose ball in each eye, hanging a little low, thrown about by the rock's motion.
const PUPIL_REST = 0.35;
const PUPIL_STIFFNESS = 70;
const PUPIL_DAMPING = 4.5;
const PUPIL_BOUNCE = 1.6;
// How hard the rock's acceleration (pt/s²) and its wobble shake the pupils.
const PUPIL_FROM_SCROLL = 0.0024;
const PUPIL_FROM_WOBBLE = 0.035;
const PUPIL_FROM_SQUASH = 0.02;

// autoPlay loop (seconds): Enter, three rocks down with a face morph on each, a tap on the
// second, back up to the first, and back to the title.
const LOOP = 14;
const PRESS_AT = 0.95;
const ENTER_AT = 1.1;
const STEPS: [number, number][] = [
  [3.0, 1],
  [5.0, 2],
  [8.0, 3],
  [10.0, 0],
];
const WIGGLE_AT = 6.9;
const EXIT_AT = 12.0;
// A touch pauses the loop this long; then it goes back to the title and starts over.
const USER_HOLD = 6;

// Stone texture: the photo in grey with a little more contrast, so it only adds relief.
const STONE_MATRIX = [
  0.2, 0.62, 0.06, 0, 0.06,
  0.2, 0.62, 0.06, 0, 0.06,
  0.2, 0.62, 0.06, 0, 0.06,
  0, 0, 0, 1, 0,
];
// Noise in grey, for the grain.
const GREY_MATRIX = [
  0.33, 0.33, 0.33, 0, 0,
  0.33, 0.33, 0.33, 0, 0,
  0.33, 0.33, 0.33, 0, 0,
  0, 0, 0, 0, 1,
];

type Props = {
  /** Plays the intro, scrolls the rocks, morphs their faces and starts over, on a loop of LOOP s. */
  autoPlay?: boolean;
  /** Called with a rock's name whenever one comes to rest in front. */
  onFocus?: (name: string) => void;
};

type Layout = {
  width: number;
  height: number;
  big: number;
  centreY: number;
  /** Distance from the focused rock's centre to its neighbour's, and between further rocks. */
  near: number;
  far: number;
  titleX: number;
  titleY: number;
};

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

/** Resistance past an end: grows with the overshoot but never past RUBBER. */
function band(x: number) {
  'worklet';
  return RUBBER * (1 - 1 / (1 + (x * 1.8) / RUBBER));
}

/** True when the clock went past `at` between `before` and `now`, wrapping round the loop. */
function passedAt(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

function rubber(raw: number, last: number) {
  'worklet';
  if (raw < 0) return -band(-raw);
  if (raw > last) return last + band(raw - last);
  return raw;
}

/** Where a rock sits for a signed distance `p` (in rocks) from the front, and how big. */
function slot(p: number, layout: Layout) {
  'worklet';
  const a = Math.abs(p);
  const offset = interpolate(a, [0, 1, 2, 3, 4], [0, layout.near, layout.near + layout.far, layout.near + 2 * layout.far, layout.near + 3 * layout.far], 'extend');
  const scale = interpolate(a, [0, 1, 2, 3], [1, SMALL, SMALL * 0.93, SMALL * 0.86], 'clamp');
  return { y: layout.centreY + Math.sign(p) * offset, scale };
}

type RockViewProps = {
  rock: Rock;
  index: number;
  face: number;
  layout: Layout;
  fonts: FaceFonts;
  reducedMotion: boolean;
  scroll: SharedValue<number>;
  intro: SharedValue<number>;
  squash: SharedValue<number>;
  wobble: SharedValue<number>;
  px: SharedValue<number>;
  py: SharedValue<number>;
};

type Slots = { a: number; b: number; showB: boolean; morphing: boolean };

/**
 * One painted rock. Its face lives in two slots: when the face changes, the hidden slot takes the
 * new one and the two crossfade through a blur, so neither is ever seen changing.
 */
function RockView({ rock, index, face, layout, fonts, reducedMotion, scroll, intro, squash, wobble, px, py }: RockViewProps) {
  const path = useMemo(() => makeRockPath(rock.seed, rock.aspect), [rock.seed, rock.aspect]);
  const speckles = useMemo(() => makeSpeckles(rock.seed), [rock.seed]);
  const image = useImage(`https://images.unsplash.com/${rock.photo}?auto=format&fit=crop&w=${PHOTO_WIDTH}&q=80`);

  const [slots, setSlots] = useState<Slots>({ a: face, b: face, showB: false, morphing: false });
  const shown = slots.showB ? slots.b : slots.a;
  if (shown !== face) {
    // A new face goes into the hidden slot, which is invisible until the crossfade starts.
    setSlots((s) => (s.showB ? { a: face, b: s.b, showB: false, morphing: true } : { a: s.a, b: face, showB: true, morphing: true }));
  }

  // 0: slot A shows, 1: slot B shows.
  const mix = useSharedValue(0);
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    const settled = () => setSlots((s) => ({ ...s, morphing: false }));
    mix.set(
      withDelay(
        MORPH_DELAY,
        withTiming(slots.showB ? 1 : 0, { duration: MORPH_TIME, easing: MORPH_EASE }, (finished) => {
          if (finished) scheduleOnRN(settled);
        }),
      ),
    );
  }, [slots.showB, mix]);

  const { big, height } = layout;
  const transform = useDerivedValue(() => {
    const p = index - scroll.get();
    const e = intro.get();
    const at = slot(p, layout);
    let x = layout.width / 2;
    let y = at.y;
    let scale = at.scale;
    if (index === 0) {
      // The first rock is the "O" of the title: it grows out of it into the carousel.
      x = layout.titleX + (x - layout.titleX) * e;
      y = layout.titleY + (y - layout.titleY) * e;
      scale = TITLE_ROCK / big + (scale - TITLE_ROCK / big) * e;
    } else if (!reducedMotion) {
      // The others come in from below as the carousel opens.
      y += (1 - clamp(e, 0, 1)) * height * 0.7;
    }
    // The tap's squash and wobble belong to the rock in front.
    const front = Math.max(0, 1 - Math.abs(p) * 2);
    const sq = squash.get() * front;
    const m = mix.get();
    const pop = reducedMotion ? 1 : 1 + MORPH_POP * 4 * m * (1 - m) * (slots.morphing ? 1 : 0);
    const sx = (1 + SQUASH_X * sq) * pop;
    const sy = (1 - SQUASH_Y * sq) * pop;
    const swing = reducedMotion ? 0 : clamp(p, -1.5, 1.5) * 0.12;
    return [
      { translateX: x },
      { translateY: y },
      { rotate: rock.tilt + swing + wobble.get() * front * WOBBLE },
      { scale: (big / 200) * scale },
      // Squash about the base, so the rock stays standing where it is.
      { translateY: 90 * rock.aspect },
      { scaleX: sx },
      { scaleY: sy },
      { translateY: -90 * rock.aspect },
    ];
  });
  const opacity = useDerivedValue(() => {
    if (index === 0) return 1;
    const a = Math.abs(index - scroll.get());
    return clamp(intro.get(), 0, 1) * clamp(4 - a, 0, 1);
  });

  const opacityA = useDerivedValue(() => 1 - mix.get());
  const opacityB = useDerivedValue(() => mix.get());
  const blurA = useDerivedValue(() => mix.get() * MORPH_BLUR);
  const blurB = useDerivedValue(() => (1 - mix.get()) * MORPH_BLUR);

  const layer = (slotB: boolean, children: ReactNode) => {
    if (!slots.morphing && slots.showB !== slotB) return null;
    const faceOf = slotB ? slots.b : slots.a;
    return slots.morphing ? (
      <Group key={`${slotB}-${faceOf}`} opacity={slotB ? opacityB : opacityA} layer={<Paint><Blur blur={slotB ? blurB : blurA} /></Paint>}>
        {children}
      </Group>
    ) : (
      <Group key={`${slotB}-${faceOf}`}>{children}</Group>
    );
  };

  const half = 120;
  const photo = 200 * PHOTO_ZOOM;
  return (
    <Group transform={transform} opacity={opacity}>
      {/* Shadow on the ground: a wide soft one and a tight dark one where the stone touches. */}
      <Oval x={-78} y={78 * rock.aspect} width={156} height={30} color="rgba(70,56,40,0.30)">
        <BlurMask blur={12} style="normal" />
      </Oval>
      <Oval x={-52} y={84 * rock.aspect} width={104} height={14} color="rgba(50,40,28,0.35)">
        <BlurMask blur={5} style="normal" />
      </Oval>
      <Group clip={path}>
        <Rect x={-half} y={-half} width={half * 2} height={half * 2} color={rock.tone} />
        {image && (
          <Rect x={-half} y={-half} width={half * 2} height={half * 2} blendMode="softLight" opacity={0.75}>
            <ImageShader image={image} fit="cover" rect={{ x: -photo / 2, y: -photo / 2, width: photo, height: photo }} />
            <ColorMatrix matrix={STONE_MATRIX} />
          </Rect>
        )}
        {layer(false, <FacePaint face={slots.a} fonts={fonts} />)}
        {layer(true, <FacePaint face={slots.b} fonts={fonts} />)}
        {/* Grain and flecks over stone and paint alike, so the paint looks sprayed on. */}
        <Rect x={-half} y={-half} width={half * 2} height={half * 2} blendMode="overlay" opacity={0.3}>
          <FractalNoise freqX={0.035} freqY={0.035} octaves={3} seed={rock.seed} />
          <ColorMatrix matrix={GREY_MATRIX} />
        </Rect>
        <Rect x={-half} y={-half} width={half * 2} height={half * 2} blendMode="overlay" opacity={0.3}>
          <FractalNoise freqX={0.45} freqY={0.45} octaves={2} seed={rock.seed + 1} />
          <ColorMatrix matrix={GREY_MATRIX} />
        </Rect>
        <Path path={speckles.dark} color="rgba(35,28,22,0.38)" />
        <Path path={speckles.light} color="rgba(255,255,255,0.32)" />
        {/* Light from the top left, and the edge falling away into shadow. */}
        <Rect x={-half} y={-half} width={half * 2} height={half * 2}>
          <RadialGradient
            c={vec(-36, -48)}
            r={165}
            colors={['rgba(255,255,255,0.34)', 'rgba(255,255,255,0.05)', 'rgba(20,14,8,0.2)', 'rgba(20,14,8,0.55)']}
            positions={[0, 0.38, 0.7, 1]}
          />
        </Rect>
        <Path path={path} style="stroke" strokeWidth={22} color="rgba(40,30,20,0.34)">
          <BlurMask blur={9} style="normal" />
        </Path>
        {/* A catch of light along the upper left edge. */}
        <Group transform={[{ translateX: 4 }, { translateY: 5 }]}>
          <Path path={path} style="stroke" strokeWidth={5} color="rgba(255,255,255,0.22)">
            <BlurMask blur={4} style="normal" />
          </Path>
        </Group>
      </Group>
      {layer(false, <FaceStickers face={slots.a} px={px} py={py} />)}
      {layer(true, <FaceStickers face={slots.b} px={px} py={py} />)}
    </Group>
  );
}

/** One letter of the title, leaving sideways as the rock grows. */
function Letter({ char, side, rank, intro, reducedMotion }: { char: string; side: -1 | 1; rank: number; intro: SharedValue<number>; reducedMotion: boolean }) {
  const style = useAnimatedStyle(() => {
    const e = clamp((intro.get() - rank * SPLIT_LAG) / 0.62, 0, 1);
    return {
      opacity: 1 - clamp(e * 1.5, 0, 1),
      transform: [{ translateX: reducedMotion ? 0 : side * e * e * (SPLIT + rank * 18) }],
    };
  });
  return <Animated.Text style={[styles.title, style]}>{char}</Animated.Text>;
}

/**
 * A carousel of painted pet rocks on paper. It opens on the title "PET ROCK" with a tiny rock for
 * the gap between the words; Enter grows that rock into the carousel as the letters split away.
 * The rock in front is large, its neighbours smaller above and below; dragging scrolls them with
 * the finger and a release snaps to a rock. When a rock comes to rest its face blurs into the
 * next one and its googly eyes jiggle; tapping it squashes it and makes it wobble.
 */
export function PetRockCarousel({ autoPlay = false, onFocus }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = ROCKS.length;
  const last = count - 1;

  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [titleX, setTitleX] = useState<number | null>(null);
  const [stage, setStage] = useState<'intro' | 'carousel'>('intro');
  const [faces, setFaces] = useState(DEFAULT_FACES);
  const [focus, setFocus] = useState(0);
  // The rock that last came to rest in front.
  const settledAt = useSharedValue(0);

  const fonts = useMemo<FaceFonts>(() => {
    const ios = process.env.EXPO_OS === 'ios';
    return {
      love: matchFont({ fontFamily: ios ? 'Avenir Next Condensed' : 'sans-serif-condensed', fontSize: 46, fontWeight: '800' }),
      tag: matchFont({ fontFamily: ios ? 'Arial Rounded MT Bold' : 'sans-serif', fontSize: 58, fontWeight: '800' }),
      zed: matchFont({ fontFamily: ios ? 'Avenir Next' : 'sans-serif', fontSize: 26, fontWeight: '800' }),
    };
  }, []);

  const layout = useMemo<Layout | null>(() => {
    if (!size || titleX === null) return null;
    const big = Math.min(BIG_MAX, size.width * BIG_SHARE);
    const small = big * SMALL;
    return {
      width: size.width,
      height: size.height,
      big,
      centreY: size.height * CENTRE_AT,
      near: big / 2 + GAP + small / 2,
      far: small + GAP_FAR,
      titleX,
      titleY: size.height * TITLE_AT,
    };
  }, [size, titleX]);
  const near = layout?.near ?? 200;
  const big = layout?.big ?? BIG_MAX;
  const centreY = layout?.centreY ?? 0;

  const scroll = useSharedValue(0);
  const intro = useSharedValue(0);
  const entered = useSharedValue(false);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  // The rock the carousel is heading for (−1 once it has arrived), and whether a finger sent it.
  const arriving = useSharedValue(-1);
  const arrivingByUser = useSharedValue(false);
  const byUser = useSharedValue(false);
  const squash = useSharedValue(0);
  const wobble = useSharedValue(0);
  const press = useSharedValue(1);
  // Pupil position in its eye (−1…1 each way) and velocity, plus a queued jolt from a face morph.
  const px = useSharedValue(0);
  const py = useSharedValue(PUPIL_REST);
  const pvx = useSharedValue(0);
  const pvy = useSharedValue(0);
  const jolt = useSharedValue(0);
  const jolts = useSharedValue(0);
  // What the pupils last saw: the carousel's speed, the wobble and the squash, with their speeds.
  const memory = useSharedValue({ s: 0, v: 0, w: 0, wv: 0, q: 0, qv: 0 });
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const haptic = (kind: 'select' | 'light' | 'medium' | 'soft') => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 'select') Haptics.selectionAsync().catch(() => {});
    else if (kind === 'light') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (kind === 'medium') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  /** A rock came to rest in front: unless it is the one that was already there, it changes face. */
  const settled = (index: number, user: boolean) => {
    setFocus(index);
    onFocus?.(ROCKS[index].name);
    if (index === settledAt.get()) return;
    settledAt.set(index);
    setFaces((now) => {
      // The next expression, skipping any worn by the rocks in view around it (two each way).
      const nearby = [now[index - 2], now[index - 1], now[index + 1], now[index + 2]];
      let next = now[index];
      do next = (next + 1) % FACE_COUNT;
      while (nearby.includes(next));
      return now.map((f, k) => (k === index ? next : f));
    });
    jolt.set(withDelay(MORPH_DELAY, withTiming(1, { duration: 16 })));
    if (user) setTimeout(() => haptic('soft'), MORPH_DELAY);
  };
  const opened = () => setStage('carousel');
  const closed = () => {
    setStage('intro');
    setFocus(0);
    settledAt.set(0);
    setFaces(DEFAULT_FACES);
  };

  // Worklet helpers are named function declarations, defined before the worklets that call them.
  function goTo(target: number, velocity: number, user: boolean) {
    'worklet';
    byUser.set(user);
    const to = clamp(Number.isFinite(target) ? target : 0, 0, last);
    // The rock counts as arrived as soon as it is still in front, before the spring's last wobble.
    arriving.set(to);
    arrivingByUser.set(user);
    scroll.set(reducedMotion ? withTiming(to, { duration: 200 }) : withSpring(to, { ...SNAP, velocity: Number.isFinite(velocity) ? velocity : 0 }));
  }

  function enter(user: boolean) {
    'worklet';
    if (entered.get()) return;
    entered.set(true);
    scroll.set(0);
    intro.set(reducedMotion ? withTiming(1, { duration: FADE_TIME }) : withSpring(1, INTRO_SPRING));
    scheduleOnRN(opened);
    if (user) scheduleOnRN(haptic, 'light');
  }

  function exit() {
    'worklet';
    if (!entered.get()) return;
    entered.set(false);
    dragging.set(false);
    arriving.set(-1);
    scroll.set(reducedMotion ? withTiming(0, { duration: FADE_TIME }) : withSpring(0, SNAP));
    intro.set(reducedMotion ? withTiming(0, { duration: FADE_TIME }) : withSpring(0, INTRO_SPRING));
    scheduleOnRN(closed);
  }

  function wiggle(user: boolean) {
    'worklet';
    if (user) scheduleOnRN(haptic, 'medium');
    if (reducedMotion) return;
    squash.set(withSequence(withTiming(1, { duration: 90, easing: Easing.out(Easing.quad) }), withSpring(0, { mass: 1, stiffness: 380, damping: 8 })));
    wobble.set(withSequence(withTiming(1, { duration: 80, easing: Easing.out(Easing.quad) }), withSpring(0, { mass: 1, stiffness: 240, damping: 6 })));
  }

  function touched() {
    'worklet';
    hold.set(USER_HOLD);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;

    // autoPlay: a fixed script on a clock, paused for a while by any touch.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        // Back to the title, then the loop starts over from its rest.
        if (entered.get()) {
          exit();
          clock.set(EXIT_AT);
        } else clock.set(0);
      }
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      if (passedAt(before, t, PRESS_AT)) press.set(withSequence(withTiming(0.96, { duration: 110 }), withTiming(1, { duration: 180 })));
      if (passedAt(before, t, ENTER_AT)) enter(false);
      for (let k = 0; k < STEPS.length; k += 1) {
        if (passedAt(before, t, STEPS[k][0]) && entered.get()) goTo(STEPS[k][1], 0, false);
      }
      if (passedAt(before, t, WIGGLE_AT) && entered.get()) wiggle(false);
      if (passedAt(before, t, EXIT_AT)) exit();
    }

    // Googly pupils: a damped spring towards hanging low, pushed by how the rock accelerates.
    const m = memory.get();
    const s = scroll.get();
    const v = (s - m.s) / dt;
    const smooth = m.v + (v - m.v) * 0.5;
    const accel = (smooth - m.v) / dt;
    const w = wobble.get();
    const wv = (w - m.w) / dt;
    const wa = (wv - m.wv) / dt;
    const q = squash.get();
    const qv = (q - m.q) / dt;
    const qa = (qv - m.qv) / dt;
    memory.set({ s, v: smooth, w, wv, q, qv });
    const goal = arriving.get();
    if (goal >= 0 && !dragging.get() && Math.abs(s - goal) < ARRIVED_AT && Math.abs(smooth) < ARRIVED_SPEED) {
      arriving.set(-1);
      scheduleOnRN(settled, goal, arrivingByUser.get());
    }
    if (reducedMotion) return;

    let vx = pvx.get();
    let vy = pvy.get();
    if (jolt.get() >= 1) {
      jolt.set(0);
      const flip = jolts.get() % 2 === 0 ? 1 : -1;
      jolts.set(jolts.get() + 1);
      vx += 7 * flip;
      vy -= 6;
    }
    const fx = -wa * PUPIL_FROM_WOBBLE;
    const fy = accel * near * PUPIL_FROM_SCROLL + qa * PUPIL_FROM_SQUASH;
    vx += (-PUPIL_STIFFNESS * px.get() - PUPIL_DAMPING * vx + clamp(fx, -400, 400)) * dt;
    vy += (-PUPIL_STIFFNESS * (py.get() - PUPIL_REST) - PUPIL_DAMPING * vy + clamp(fy, -400, 400)) * dt;
    let x = px.get() + vx * dt;
    let y = py.get() + vy * dt;
    const r = Math.hypot(x, y);
    if (r > 1) {
      // The pupil hits the rim of its eye and bounces off it.
      x /= r;
      y /= r;
      const along = vx * x + vy * y;
      if (along > 0) {
        vx -= PUPIL_BOUNCE * along * x;
        vy -= PUPIL_BOUNCE * along * y;
      }
    }
    px.set(x);
    py.set(y);
    pvx.set(vx);
    pvy.set(vy);
  });

  // A selection tick as each rock passes the front under the user's finger, and the caption follows.
  useAnimatedReaction(
    () => clamp(Math.round(scroll.get()), 0, last),
    (now, before) => {
      if (before === null || now === before || !entered.get()) return;
      scheduleOnRN(setFocus, now);
      if (byUser.get()) scheduleOnRN(haptic, 'select');
    },
  );

  const pan = Gesture.Pan()
    .minDistance(DRAG_SLOP)
    .onBegin(() => touched())
    .onStart(() => {
      if (!entered.get()) return;
      byUser.set(true);
      arriving.set(-1);
      dragFrom.set(scroll.get());
      dragging.set(true);
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      scroll.set(rubber(dragFrom.get() - event.translationY / near, last));
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      dragging.set(false);
      // The finger's speed at release, in rocks per second, carries into the snap.
      const velocity = -event.velocityY / near;
      const s = scroll.get();
      const outside = s < 0 || s > last;
      goTo(Math.round(s + (outside ? 0 : velocity * THROW_TIME)), outside ? velocity * 0.3 : velocity, true);
    })
    .onFinalize(() => {
      // A cancelled drag never reaches onEnd: snap to the nearest rock instead of staying stuck between two.
      if (!dragging.get()) return;
      dragging.set(false);
      goTo(Math.round(scroll.get()), 0, true);
    });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onBegin(() => touched())
    .onEnd((event, success) => {
      if (!success) return;
      if (!entered.get()) {
        // The little rock in the title can be poked too.
        if (Math.abs(event.y - (layout?.titleY ?? 0)) < TITLE_ROCK && Math.abs(event.x - (layout?.titleX ?? 0)) < TITLE_ROCK) wiggle(true);
        return;
      }
      const dy = event.y - centreY;
      if (Math.abs(dy) < big * 0.45) wiggle(true);
      else goTo(Math.round(scroll.get()) + (dy > 0 ? 1 : -1), 0, true);
    });

  const titleStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp(intro.get() * 2, 0, 1) }));
  const enterStyle = useAnimatedStyle(() => {
    const e = clamp(intro.get(), 0, 1);
    return {
      opacity: 1 - clamp(e * 2.2, 0, 1),
      transform: [{ translateY: reducedMotion ? 0 : e * 120 }, { scale: press.get() }],
    };
  });
  const chromeStyle = useAnimatedStyle(() => ({ opacity: clamp((intro.get() - 0.5) * 2, 0, 1) }));

  const onEnter = () => {
    hold.set(USER_HOLD);
    scheduleOnUI(enter, true);
  };
  const onTitle = () => {
    hold.set(USER_HOLD);
    scheduleOnUI(exit);
  };

  const rock = ROCKS[focus];
  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: PAPER }]}
        onLayout={(event: LayoutChangeEvent) => {
          const { width, height } = event.nativeEvent.layout;
          // A zero first layout would make the rocks' scale (TITLE_ROCK / big) infinite.
          if (width > 0 && height > 0) setSize({ width, height });
        }}
      >
        {layout && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Rect x={0} y={0} width={layout.width} height={layout.height}>
              <LinearGradient start={vec(0, 0)} end={vec(0, layout.height)} colors={[PAPER_TOP, PAPER_BOTTOM]} />
            </Rect>
            {ROCKS.map((r, index) => (
              <RockView
                key={r.name}
                rock={r}
                index={index}
                face={faces[index]}
                layout={layout}
                fonts={fonts}
                reducedMotion={reducedMotion}
                scroll={scroll}
                intro={intro}
                squash={squash}
                wobble={wobble}
                px={px}
                py={py}
              />
            ))}
            {/* The paper fades over the rocks at the top and bottom, behind the header and caption. */}
            <Rect x={0} y={0} width={layout.width} height={insets.top + 130}>
              <LinearGradient start={vec(0, insets.top + 34)} end={vec(0, insets.top + 130)} colors={[PAPER_TOP, 'rgba(242,240,235,0)']} />
            </Rect>
            <Rect x={0} y={layout.height - insets.bottom - 130} width={layout.width} height={insets.bottom + 130}>
              <LinearGradient
                start={vec(0, layout.height - insets.bottom - 130)}
                end={vec(0, layout.height - insets.bottom - 40)}
                colors={['rgba(230,226,219,0)', PAPER_BOTTOM]}
              />
            </Rect>
          </Canvas>
        )}

        {/* The title: PET, a gap the little rock sits in, ROCK. */}
        <Animated.View pointerEvents="none" style={[styles.titleRow, { top: (size?.height ?? 0) * TITLE_AT - TITLE_SIZE * 0.62 }, titleStyle]}>
          {['P', 'E', 'T'].map((char, k) => (
            <Letter key={`l${k}`} char={char} side={-1} rank={2 - k} intro={intro} reducedMotion={reducedMotion} />
          ))}
          <View
            style={{ width: TITLE_ROCK + 6 }}
            onLayout={(event) => {
              const { x, width } = event.nativeEvent.layout;
              setTitleX(x + width / 2);
            }}
          />
          {['R', 'O', 'C', 'K'].map((char, k) => (
            <Letter key={`r${k}`} char={char} side={1} rank={k} intro={intro} reducedMotion={reducedMotion} />
          ))}
        </Animated.View>

        <Animated.View style={[styles.enterWrap, { bottom: insets.bottom + 14 }, enterStyle]} pointerEvents={stage === 'intro' ? 'auto' : 'none'}>
          <Pressable
            onPress={onEnter}
            accessibilityRole="button"
            accessibilityLabel="Enter"
            style={({ pressed }) => [styles.enter, { transform: [{ scale: pressed ? 0.97 : 1 }] }]}
          >
            <Text style={styles.enterLabel}>Enter</Text>
          </Pressable>
        </Animated.View>

        <Animated.View style={[styles.header, { top: insets.top + 8 }, chromeStyle]} pointerEvents={stage === 'carousel' ? 'box-none' : 'none'}>
          <Pressable onPress={onTitle} hitSlop={12} accessibilityRole="button" accessibilityLabel="Back to the title">
            <Text style={styles.wordmark}>PET ROCK</Text>
          </Pressable>
        </Animated.View>

        <Animated.View pointerEvents="none" style={[styles.caption, { bottom: insets.bottom + 22 }, chromeStyle]}>
          <View style={styles.captionText}>
            <Animated.View key={`${focus}-${faces[focus]}`} entering={FadeIn.duration(240)} exiting={FadeOut.duration(140)} style={styles.captionLine}>
              <Text style={styles.name}>{rock.name}</Text>
              <Text style={styles.mood}>{MOODS[faces[focus]]}</Text>
            </Animated.View>
          </View>
          <Text style={styles.counter}>
            {String(focus + 1).padStart(2, '0')} / {String(count).padStart(2, '0')}
          </Text>
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
  titleRow: {
    position: 'absolute',
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    fontFamily: FAMILY,
    fontSize: TITLE_SIZE,
    fontWeight: '500',
    letterSpacing: -0.6,
    color: INK,
  },
  enterWrap: {
    position: 'absolute',
    left: 22,
    right: 22,
  },
  enter: {
    height: 50,
    borderRadius: 25,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
  },
  enterLabel: {
    fontFamily: FAMILY,
    fontSize: 16,
    fontWeight: '500',
    color: '#f4f1ea',
  },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  wordmark: {
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '600',
    letterSpacing: 3,
    color: INK,
  },
  caption: {
    position: 'absolute',
    left: 24,
    right: 24,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  captionText: {
    height: 44,
    flex: 1,
  },
  captionLine: {
    position: 'absolute',
    left: 0,
    bottom: 0,
  },
  name: {
    fontFamily: FAMILY,
    fontSize: 22,
    fontWeight: '600',
    letterSpacing: -0.3,
    color: INK,
  },
  mood: {
    marginTop: 2,
    fontFamily: FAMILY,
    fontSize: 14,
    fontWeight: '400',
    color: MUTED,
  },
  counter: {
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
    color: MUTED,
    fontVariant: ['tabular-nums'],
  },
});
