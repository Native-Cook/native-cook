import {
  Canvas,
  Circle,
  Group,
  Image as SkiaImage,
  LinearGradient,
  Path,
  Rect,
  Skia,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  interpolate,
  type SharedValue,
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
import { scheduleOnRN } from 'react-native-worklets';

import { type Coin, COIN_SLOTS, type JarSpec, jarGeometry, landingTime, NO_COIN, PillRiseJar } from './PillRiseJar';

type Props = {
  /** Plays the splash, the reveal, a few coins and the way back, on a loop. */
  autoPlay?: boolean;
  /** The headline, one entry per line. */
  headline?: string[];
  link?: string;
  cta?: string;
  /** Splash and button colour, the mark on it, and the board's background. */
  lime?: string;
  ink?: string;
  night?: string;
  onGetStarted?: () => void;
};

const unsplash = (id: string, w: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&q=80`;

// The colour that fills the capsule as it rises: an iridescent swirl, drawn full screen.
const REVEAL_PHOTO = 'photo-1620421680010-0766ff230392';
const REVEAL_WIDTH = 1200;
const JAR_PHOTO_WIDTH = 360;

// Three glazed jars, back to front, placed around the middle of the jar stage (pt at 402 pt wide).
const JARS: JarSpec[] = [
  { photo: 'photo-1541961017774-22349e4a1262', x: 8, y: -150, body: 82, height: 120, mouth: 48, tilt: 0.1, period: 3.1, phase: 0.4 },
  { photo: 'photo-1580566176138-daa588058b59', x: 96, y: -58, body: 96, height: 142, mouth: 58, tilt: 0.17, period: 3.6, phase: 2.1 },
  { photo: 'photo-1536849460588-696219a9e98d', x: -92, y: -30, body: 116, height: 168, mouth: 66, tilt: -0.21, period: 3.3, phase: 4.0 },
];
const STAGE_REF_WIDTH = 402;
const STAGE_REF_HEIGHT = 470;

const LIME = '#a3eb6e';
const INK = '#173300';
const NIGHT = '#161a14';
const HEADLINE = ['90 countries,', '30 currencies.', 'One wallet'];

// The capsule: PILL_W wide, its top a half circle. The splash's progress `s` runs 0 (hidden under
// the bottom edge) → PEEK (a dark capsule holding the button) → 0.48 (risen to 30% of the screen)
// → 0.68 (touching the top, a little wider) → 1 (the whole screen).
const PILL_W = 68;
const PEEK = 0.12;
const BUTTON_R = 24;
// How far above the home indicator the button sits while the capsule peeks.
const PEEK_LIFT = 64;
// Drag: a full screen of travel times this share moves `s` from 0 to 1. A release commits when
// it would carry `s` past COMMIT within THROW s.
const DRAG_SPAN = 0.85;
const COMMIT = 0.42;
const THROW = 0.22;
// Splash timings, ms.
const PEEK_MS = 520;
const RISE_MS = 1500;
const BOARD_DELAY = 120;
const BOARD_MS = 1400;
const EXIT_MS = 650;
const LOGO_MS = 520;

// Jar drag: pulled further than RUBBER pt, a jar gives less and less. Released, it springs home
// with the finger's speed (stiffness JAR_K, damping JAR_C per unit mass: under-damped, it wobbles).
const RUBBER = 160;
const JAR_K = 220;
const JAR_C = 13;
const JAR_GRAB = 90;
// The line the coins appear on, below the progress bar.
const COIN_LINE = 34;
const COIN_DX = [-14, 11, -7, 16, -10];

// autoPlay script (s). One loop starts and ends on the resting splash.
const LOOP = 11;
const PEEK_AT = 0.6;
const RISE_AT = 1.4;
const COIN_AT = [4.5, 5.25, 5.95, 6.7, 7.4];
const COIN_JAR = [0, 2, 1, 0, 2];
const PRESS_AT = 9.0;
const EXIT_AT = 9.15;
// A touch pauses the script for USER_HOLD s; it resumes at the matching point of the loop.
const USER_HOLD = 4;
const RESUME_SPLASH = PEEK_AT - 0.2;
const RESUME_BOARD = 8.2;

const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);
const RISE_EASE = Easing.bezier(0.6, 0, 0.3, 1);

const HEADLINE_FAMILY = process.env.EXPO_OS === 'ios' ? 'Futura-CondensedExtraBold' : 'sans-serif-condensed';
const HEADLINE_SIZE = 42;
const HEADLINE_LINE = 41;

// An invented mark: two slanted bars passing each other, like a transfer.
const MARK_SIZE = 64;
const MARK_SCALE = 1.35;
const MARK = Skia.Path.MakeFromSVGString(
  'M14 16 L44 16 L54 24.5 L44 33 L14 33 Z M50 37 L20 37 L10 45.5 L20 54 L50 54 Z',
)!;

function seg(v: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (v - a) / (b - a)));
}

/** Past `limit`, a pull gives less and less. */
function rubber(d: number, limit: number) {
  'worklet';
  const a = Math.abs(d);
  return Math.sign(d) * (a < limit ? a : limit + (a - limit) * 0.3);
}

/**
 * A lime splash with a mark in the middle. A dark capsule rises from the bottom edge holding an
 * arrow button, fills with colour and grows into the whole screen, pushing the mark away; then
 * the board settles in: three glazed jars floating in the dark, coins dropping into them, a big
 * headline and a lime button. Drag the capsule up (or tap) to open it, drag the jars, tap a jar
 * for a coin, and press the button to go back to the splash.
 */
export function PillRiseSplash({
  autoPlay = false,
  headline = HEADLINE,
  link = 'See our rates',
  cta = 'Get started',
  lime = LIME,
  ink = INK,
  night = NIGHT,
  onGetStarted,
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const W = size.width;
  const H = size.height;

  const reveal = useImage(unsplash(REVEAL_PHOTO, REVEAL_WIDTH));
  const glaze0 = useImage(unsplash(JARS[0].photo, JAR_PHOTO_WIDTH));
  const glaze1 = useImage(unsplash(JARS[1].photo, JAR_PHOTO_WIDTH));
  const glaze2 = useImage(unsplash(JARS[2].photo, JAR_PHOTO_WIDTH));
  const glazes = [glaze0, glaze1, glaze2];

  // Layout, bottom up: button, link, headline; the jars share what is left above.
  const ctaHeight = 56;
  const ctaTop = H - Math.max(insets.bottom, 16) - 8 - ctaHeight;
  const ctaLeft = 20;
  const ctaWidth = W - ctaLeft * 2;
  const linkTop = ctaTop - 52;
  const headlineTop = linkTop - 18 - HEADLINE_LINE * headline.length - 6;
  const barTop = insets.top + 14;
  const stageTop = barTop + 24;
  const stageBottom = headlineTop - 16;
  const stageMid = (stageTop + stageBottom) / 2;
  const k = Math.min(W / STAGE_REF_WIDTH, (stageBottom - stageTop) / STAGE_REF_HEIGHT, 1.25);

  const jars = useMemo(
    () =>
      JARS.map((spec) => {
        const geometry = jarGeometry(spec, k);
        const x = W / 2 + spec.x * k;
        const y = stageMid + spec.y * k;
        return { spec, geometry, x, y, drop: y - (stageTop + COIN_LINE) };
      }),
    [W, k, stageMid, stageTop],
  );
  const jarX = jars.map((j) => j.x);
  const jarMid = jars.map((j) => j.y + j.geometry.height / 2);
  const jarDrop = jars.map((j) => j.drop);

  // Splash progress (see PEEK), board entrance, the way back, and the mark's own fade.
  const s = useSharedValue(0);
  const board = useSharedValue(0);
  const exit = useSharedValue(0);
  const logoIn = useSharedValue(1);
  const press = useSharedValue(0);
  // 0 splash, 1 opening or board, 2 on the way back.
  const phase = useSharedValue(0);
  // Seconds since mount (drives the floating and the coins), and the autoPlay clock.
  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const coins = useSharedValue<Coin[]>(Array.from({ length: COIN_SLOTS }, () => NO_COIN));
  const nextSlot = useSharedValue(0);
  const offsetX = useSharedValue([0, 0, 0]);
  const offsetY = useSharedValue([0, 0, 0]);
  const speedX = useSharedValue([0, 0, 0]);
  const speedY = useSharedValue([0, 0, 0]);
  const landed = useSharedValue([-100, -100, -100]);
  // What the current pan moves: 0 nothing, 1 the capsule, 2 a jar.
  const panMode = useSharedValue(0);
  const grabbed = useSharedValue(-1);
  const grabFrom = useSharedValue({ s: 0, x: 0, y: 0 });

  const thud = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const started = () => {
    thud();
    onGetStarted?.();
  };

  function peek(delay: number) {
    'worklet';
    if (phase.get() !== 0) return;
    s.set(withDelay(delay, withTiming(PEEK, { duration: reducedMotion ? 0 : PEEK_MS, easing: Easing.bezier(0.23, 1, 0.32, 1) })));
  }

  function startBoard(finished?: boolean) {
    'worklet';
    if (!finished) return;
    board.set(withDelay(BOARD_DELAY, withTiming(1, { duration: reducedMotion ? 500 : BOARD_MS, easing: Easing.linear })));
  }

  /** Opens the capsule into the board. `velocity` is the finger's, in units of `s` per second. */
  function commit(user: boolean, velocity: number) {
    'worklet';
    if (phase.get() !== 0) return;
    phase.set(1);
    if (user) scheduleOnRN(thud);
    if (reducedMotion) {
      s.set(withTiming(1, { duration: 420 }, startBoard));
    } else if (user) {
      s.set(withSpring(1, { velocity, dampingRatio: 1, duration: 1300, overshootClamping: true }, startBoard));
    } else {
      s.set(withTiming(1, { duration: RISE_MS, easing: RISE_EASE }, startBoard));
    }
  }

  /** Back to the resting splash: everything resets under the full-screen lime. */
  function reset(finished?: boolean) {
    'worklet';
    if (!finished) return;
    s.set(0);
    board.set(0);
    exit.set(0);
    phase.set(0);
    coins.set(Array.from({ length: COIN_SLOTS }, () => NO_COIN));
    offsetX.set([0, 0, 0]);
    offsetY.set([0, 0, 0]);
    speedX.set([0, 0, 0]);
    speedY.set([0, 0, 0]);
    logoIn.set(0);
    logoIn.set(withTiming(1, { duration: LOGO_MS, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
    if (!looping.get() || hold.get() > 0) peek(LOGO_MS + 400);
  }

  /** The button grows into a full-screen lime and the splash comes back. */
  function leave(user: boolean) {
    'worklet';
    if (phase.get() !== 1 || board.get() < 0.9) return;
    phase.set(2);
    if (user) scheduleOnRN(started);
    exit.set(withTiming(1, { duration: reducedMotion ? 300 : EXIT_MS, easing: Easing.bezier(0.77, 0, 0.175, 1) }, reset));
  }

  /** Drops a coin into jar `jar`. */
  function drop(jar: number, user: boolean) {
    'worklet';
    const slot = nextSlot.get();
    nextSlot.set((slot + 1) % COIN_SLOTS);
    coins.modify((list) => {
      'worklet';
      list[slot] = { t0: time.get(), jar, dx: COIN_DX[slot], user };
      return list;
    });
  }

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
    if (!auto) peek(700);
    // peek is a worklet over shared values; it only needs to run when the mode changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto, clock, hold, looping]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const before = time.get();
    const now = before + dt;
    time.set(now);

    // A coin passing through a rim rings its jar; one a finger dropped gets a haptic too.
    const list = coins.get();
    for (let i = 0; i < list.length; i += 1) {
      const c = list[i];
      if (c.t0 < 0) continue;
      const at = c.t0 + landingTime(jarDrop[c.jar]);
      if (before < at && now >= at) {
        landed.modify((l) => {
          'worklet';
          l[c.jar] = at;
          return l;
        });
        if (c.user) scheduleOnRN(thud);
      }
    }

    // Jars not under a finger spring home, carrying the speed they were let go with.
    const g = grabbed.get();
    const ox = offsetX.get();
    const oy = offsetY.get();
    const vx = speedX.get();
    const vy = speedY.get();
    let moving = false;
    for (let i = 0; i < ox.length; i += 1) {
      if (i === g) continue;
      if (Math.abs(ox[i]) + Math.abs(oy[i]) + Math.abs(vx[i]) + Math.abs(vy[i]) < 0.05) continue;
      moving = true;
      vx[i] += (-JAR_K * ox[i] - JAR_C * vx[i]) * dt;
      vy[i] += (-JAR_K * oy[i] - JAR_C * vy[i]) * dt;
      ox[i] += vx[i] * dt;
      oy[i] += vy[i] * dt;
    }
    if (moving) {
      offsetX.set([...ox]);
      offsetY.set([...oy]);
      speedX.set([...vx]);
      speedY.set([...vy]);
    }

    // The script.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        const p = phase.get();
        clock.set(p === 0 ? RESUME_SPLASH : p === 1 ? RESUME_BOARD : EXIT_AT + 0.01);
      }
      return;
    }
    if (!looping.get()) return;
    const t0 = clock.get();
    const t = (t0 + dt) % LOOP;
    clock.set(t);
    const passed = (at: number) => (t0 < at && t >= at) || (t < t0 && (at > t0 || at <= t));
    if (passed(PEEK_AT)) peek(0);
    if (passed(RISE_AT)) commit(false, 0);
    for (let i = 0; i < COIN_AT.length; i += 1) {
      if (passed(COIN_AT[i]) && phase.get() === 1 && board.get() > 0.9) drop(COIN_JAR[i], false);
    }
    if (passed(PRESS_AT) && phase.get() === 1) {
      press.set(withSequence(withTiming(1, { duration: 110 }), withTiming(0, { duration: 200 })));
    }
    if (passed(EXIT_AT)) leave(false);
  });

  function touched() {
    'worklet';
    hold.set(USER_HOLD);
  }

  /** The jar nearest a point on screen, or -1 when none is within reach. */
  function jarAt(x: number, y: number, reach: number) {
    'worklet';
    let best = -1;
    let bestD = reach;
    for (let i = 0; i < jarX.length; i += 1) {
      const d = Math.hypot(x - (jarX[i] + offsetX.get()[i]), y - (jarMid[i] + offsetY.get()[i]));
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    return best;
  }

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart((event) => {
      panMode.set(0);
      touched();
      if (phase.get() === 0 && !reducedMotion) {
        cancelAnimation(s);
        grabFrom.set({ s: s.get(), x: 0, y: 0 });
        panMode.set(1);
      } else if (phase.get() === 1 && board.get() > 0.9) {
        const j = jarAt(event.x, event.y, JAR_GRAB * k + 20);
        if (j < 0) return;
        grabbed.set(j);
        grabFrom.set({ s: 0, x: offsetX.get()[j], y: offsetY.get()[j] });
        panMode.set(2);
      }
    })
    .onUpdate((event) => {
      if (panMode.get() === 1) {
        const raw = grabFrom.get().s - event.translationY / (H * DRAG_SPAN);
        // Below the resting peek the capsule resists; it never tears off the bottom edge.
        s.set(raw < PEEK ? PEEK - rubber(PEEK - raw, 0) * 0.35 : Math.min(raw, 0.97));
      } else if (panMode.get() === 2) {
        const j = grabbed.get();
        if (j < 0) return;
        const from = grabFrom.get();
        const x = rubber(from.x + event.translationX, RUBBER);
        const y = rubber(from.y + event.translationY, RUBBER);
        offsetX.modify((a) => {
          'worklet';
          a[j] = x;
          return a;
        });
        offsetY.modify((a) => {
          'worklet';
          a[j] = y;
          return a;
        });
        // The finger's speed tilts the jar while it is carried.
        speedX.modify((a) => {
          'worklet';
          a[j] = event.velocityX;
          return a;
        });
      }
    })
    .onEnd((event) => {
      if (panMode.get() === 1) {
        const velocity = -event.velocityY / (H * DRAG_SPAN);
        if (s.get() + velocity * THROW > COMMIT) commit(true, velocity);
        else s.set(withSpring(PEEK, { velocity, duration: 450, dampingRatio: 0.8 }));
      } else if (panMode.get() === 2) {
        const j = grabbed.get();
        if (j < 0) return;
        speedX.modify((a) => {
          'worklet';
          a[j] = event.velocityX;
          return a;
        });
        speedY.modify((a) => {
          'worklet';
          a[j] = event.velocityY;
          return a;
        });
      }
    })
    .onFinalize(() => {
      panMode.set(0);
      grabbed.set(-1);
    });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      if (phase.get() === 0) {
        commit(true, 0);
      } else if (phase.get() === 1 && board.get() > 0.9 && event.y > stageTop && event.y < stageBottom) {
        const j = jarAt(event.x, event.y, 1000);
        if (j >= 0) drop(j, true);
      }
    });

  const ctaTap = Gesture.Tap()
    .maxDuration(600)
    .onBegin(() => {
      if (phase.get() === 1) press.set(withTiming(1, { duration: 110 }));
    })
    .onFinalize(() => {
      press.set(withTiming(0, { duration: 180 }));
    })
    .onEnd((_, success) => {
      if (!success) return;
      touched();
      leave(true);
    });

  // ——— The splash ———

  // Without big motion the capsule stays at its peek and the colour fades in instead.
  const shape = useDerivedValue(() => (reducedMotion ? Math.min(s.get(), PEEK) : s.get()));
  const peekTop = H - Math.max(insets.bottom, 12) - PEEK_LIFT - BUTTON_R - 10;
  const pillTop = useDerivedValue(() =>
    interpolate(shape.get(), [0, PEEK, 0.48, 0.68, 1], [H + 2, peekTop, H * 0.3, 0, 0], 'clamp'),
  );
  const pill = useDerivedValue(() => {
    const g = shape.get();
    const w = interpolate(g, [0, 0.48, 0.68, 1], [PILL_W, PILL_W, PILL_W * 1.8, W], 'clamp');
    const r = Math.min(w / 2, interpolate(g, [0, 0.48, 0.68, 0.9, 1], [PILL_W / 2, PILL_W / 2, PILL_W * 0.9, 46, 0], 'clamp'));
    const top = pillTop.get();
    return Skia.RRectXY(Skia.XYWHRect((W - w) / 2, top, w, H - top + 80), r, r);
  });
  const pillOpacity = useDerivedValue(() => (s.get() > 0.001 ? 1 : 0));
  // The colour inside grows still as the capsule opens.
  const revealTransform = useDerivedValue(() => [{ scale: interpolate(shape.get(), [PEEK, 1], [1.2, 1], 'clamp') }]);
  // A dark cap at the top of the capsule, gone by the time it is a third of the way up.
  const capOpacity = useDerivedValue(() => 1 - seg(shape.get(), PEEK + 0.04, 0.46));
  const capStart = useDerivedValue(() => vec(0, pillTop.get() + 40));
  const capEnd = useDerivedValue(() => vec(0, pillTop.get() + 150));
  const fadeOpacity = useDerivedValue(() => (reducedMotion ? seg(s.get(), PEEK, 1) : 0));

  // The arrow button rides the top of the capsule, then flies off the top and fades.
  const buttonTransform = useDerivedValue(() => {
    const g = shape.get();
    const lift = interpolate(g, [0.48, 0.7], [0, -110], 'clamp');
    const grow = interpolate(g, [0.02, PEEK], [0.55, 1], 'clamp') * interpolate(g, [0.5, 0.7], [1, 0.85], 'clamp');
    return [{ translateX: W / 2 }, { translateY: pillTop.get() + PILL_W / 2 + lift }, { scale: grow }];
  });
  const buttonOpacity = useDerivedValue(() => {
    const g = shape.get();
    return seg(g, 0.03, PEEK * 0.9) * (1 - seg(g, 0.52, 0.68));
  });
  // A soft nudge upwards while the capsule waits at its peek.
  const arrowTransform = useDerivedValue(() => {
    const at = 1 - seg(Math.abs(shape.get() - PEEK), 0, 0.04);
    const nudge = reducedMotion ? 0 : -3 * at * (0.5 - 0.5 * Math.cos((Math.PI * 2 * time.get()) / 1.5));
    return [{ translateY: nudge }];
  });

  // The capsule pushes the mark up as its top passes it; the mark fades on the way.
  const markTransform = useDerivedValue(() => {
    const mid = H / 2;
    const push = Math.max(0, mid + MARK_SIZE / 2 + 24 - pillTop.get());
    const grow = 0.92 + 0.08 * EASE_OUT(logoIn.get());
    return [
      { translateX: W / 2 },
      { translateY: mid - push * (reducedMotion ? 0 : 0.85) },
      { scale: grow * MARK_SCALE },
      { skewX: -0.22 },
      { translateX: -MARK_SIZE / 2 },
      { translateY: -MARK_SIZE / 2 },
    ];
  });
  const markOpacity = useDerivedValue(() => {
    const push = Math.max(0, H / 2 + MARK_SIZE / 2 + 24 - pillTop.get());
    return logoIn.get() * (1 - seg(push, 20, 150)) * (1 - seg(s.get(), PEEK, 0.6) * (reducedMotion ? 1 : 0));
  });

  // ——— The board ———

  // The button opens into the full screen, from its own frame.
  const exitStyle = useAnimatedStyle(() => {
    const e = exit.get();
    if (reducedMotion) return { opacity: e, left: 0, top: 0, width: W, height: H, borderRadius: 0 };
    return {
      opacity: e > 0 ? 1 : 0,
      left: ctaLeft * (1 - e),
      top: ctaTop * (1 - e),
      width: ctaWidth + (W - ctaWidth) * e,
      height: ctaHeight + (H - ctaHeight) * e,
      borderRadius: (ctaHeight / 2) * (1 - e) + 46 * Math.sin(Math.PI * Math.min(e, 0.5)) * (1 - e),
    };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    if (width !== W || height !== H) setSize({ width, height });
  };

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: lime }]}
        onLayout={onLayout}
        accessible
        accessibilityRole="button"
        accessibilityLabel={`${headline.join(' ')}. ${cta}`}
      >
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Group transform={markTransform} opacity={markOpacity}>
            <Path path={MARK} color={ink} />
          </Group>
          <Group opacity={pillOpacity} clip={pill}>
            <Rect x={0} y={0} width={W} height={H}>
              <LinearGradient start={vec(0, 0)} end={vec(W, H)} colors={['#f25fb4', '#8b5cf6', '#38bdf8']} />
            </Rect>
            {reveal && (
              <Group transform={revealTransform} origin={vec(W / 2, H / 2)}>
                <SkiaImage image={reveal} x={0} y={0} width={W} height={H} fit="cover" />
              </Group>
            )}
            <Rect x={0} y={0} width={W} height={H + 80} opacity={capOpacity}>
              <LinearGradient start={capStart} end={capEnd} colors={[night, `${night}00`]} />
            </Rect>
          </Group>
          {reveal && <SkiaImage image={reveal} x={0} y={0} width={W} height={H} fit="cover" opacity={fadeOpacity} />}
          <Group transform={buttonTransform} opacity={buttonOpacity}>
            <Circle cx={0} cy={0} r={BUTTON_R + 4} style="stroke" strokeWidth={1.5} color={lime} opacity={0.45} />
            <Circle cx={0} cy={0} r={BUTTON_R} color={lime} />
            <Group transform={arrowTransform}>
              <Path
                path="M 0 9 L 0 -9 M -7 -2 L 0 -9 L 7 -2"
                style="stroke"
                strokeWidth={2.4}
                strokeCap="round"
                strokeJoin="round"
                color={ink}
              />
            </Group>
          </Group>
        </Canvas>

        <Board
          board={board}
          press={press}
          night={night}
          lime={lime}
          ink={ink}
          headline={headline}
          link={link}
          cta={cta}
          layout={{ barTop, headlineTop, linkTop, ctaTop, ctaLeft, ctaWidth, ctaHeight }}
          ctaGesture={ctaTap}
          reducedMotion={reducedMotion}
        >
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            {jars.map((jar, index) => (
              <PillRiseJar
                key={jar.spec.photo}
                index={index}
                spec={jar.spec}
                geometry={jar.geometry}
                x={jar.x}
                y={jar.y}
                drop={jar.drop}
                image={glazes[index]}
                board={board}
                time={time}
                offsetX={offsetX}
                offsetY={offsetY}
                speedX={speedX}
                landed={landed}
                coins={coins}
                reducedMotion={reducedMotion}
              />
            ))}
          </Canvas>
        </Board>

        <Animated.View pointerEvents="none" style={[styles.exit, { backgroundColor: lime }, exitStyle]} />
      </View>
    </GestureDetector>
  );
}

type BoardProps = {
  board: SharedValue<number>;
  press: SharedValue<number>;
  ctaGesture: ReturnType<typeof Gesture.Tap>;
  night: string;
  lime: string;
  ink: string;
  headline: string[];
  link: string;
  cta: string;
  layout: {
    barTop: number;
    headlineTop: number;
    linkTop: number;
    ctaTop: number;
    ctaLeft: number;
    ctaWidth: number;
    ctaHeight: number;
  };
  reducedMotion: boolean;
  children: React.ReactNode;
};

/** The onboarding screen the capsule opens into: progress, jars, headline, link and button. */
function Board({
  board,
  press,
  ctaGesture,
  night,
  lime,
  ink,
  headline,
  link,
  cta,
  layout,
  reducedMotion,
  children,
}: BoardProps) {
  const backdrop = useAnimatedStyle(() => ({ opacity: seg(board.get(), 0, 0.5) }));
  const barStyle = useAnimatedStyle(() => ({ opacity: seg(board.get(), 0.35, 0.6) }));
  const barFill = useAnimatedStyle(() => ({ transform: [{ scaleX: EASE_OUT(seg(board.get(), 0.5, 1)) }] }));
  const linkStyle = useAnimatedStyle(() => ({ opacity: seg(board.get(), 0.55, 0.85) }));
  const ctaStyle = useAnimatedStyle(() => {
    const p = EASE_OUT(seg(board.get(), 0.42, 0.85));
    return {
      opacity: seg(board.get(), 0.42, 0.6),
      transform: [{ translateY: reducedMotion ? 0 : (1 - p) * 90 }, { scale: 1 - press.get() * 0.03 }],
    };
  });
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: night }, backdrop]} />
      <Animated.View pointerEvents="none" style={[styles.bar, { top: layout.barTop }, barStyle]}>
        {[0, 1, 2].map((step) => (
          <View key={step} style={styles.track}>
            {step === 0 && <Animated.View style={[styles.fill, { backgroundColor: lime }, barFill]} />}
          </View>
        ))}
      </Animated.View>
      {children}
      <View pointerEvents="none" style={[styles.headline, { top: layout.headlineTop }]}>
        {headline.map((line, index) => (
          <HeadlineLine key={index} text={line} index={index} board={board} reducedMotion={reducedMotion} />
        ))}
      </View>
      <Animated.Text
        pointerEvents="none"
        style={[styles.link, { top: layout.linkTop, color: lime, textDecorationColor: lime }, linkStyle]}
      >
        {link}
      </Animated.Text>
      <GestureDetector gesture={ctaGesture}>
        <Animated.View
          accessibilityRole="button"
          accessibilityLabel={cta}
          style={[
            styles.cta,
            {
              top: layout.ctaTop,
              left: layout.ctaLeft,
              width: layout.ctaWidth,
              height: layout.ctaHeight,
              borderRadius: layout.ctaHeight / 2,
              backgroundColor: lime,
            },
            ctaStyle,
          ]}
        >
          <Text style={[styles.ctaLabel, { color: ink }]}>{cta}</Text>
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

type LineProps = { text: string; index: number; board: SharedValue<number>; reducedMotion: boolean };

/** One line of the headline, rising out of its own baseline. */
function HeadlineLine({ text, index, board, reducedMotion }: LineProps) {
  const style = useAnimatedStyle(() => {
    const from = 0.1 + index * 0.08;
    const p = EASE_OUT(seg(board.get(), from, from + 0.38));
    return {
      opacity: reducedMotion ? p : seg(board.get(), from, from + 0.2),
      transform: [{ translateY: reducedMotion ? 0 : (1 - p) * HEADLINE_LINE * 1.1 }],
    };
  });
  return (
    <View style={styles.lineClip}>
      <Animated.Text style={[styles.line, style]} numberOfLines={1}>
        {text.toUpperCase()}
      </Animated.Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  bar: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 3,
    flexDirection: 'row',
    gap: 6,
  },
  track: {
    flex: 1,
    borderRadius: 1.5,
    backgroundColor: 'rgba(255,255,255,0.14)',
    overflow: 'hidden',
  },
  fill: {
    flex: 1,
    transformOrigin: 'left',
  },
  headline: {
    position: 'absolute',
    left: 24,
    right: 24,
    alignItems: 'center',
  },
  lineClip: {
    height: HEADLINE_LINE,
    overflow: 'hidden',
    alignSelf: 'stretch',
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  line: {
    fontFamily: HEADLINE_FAMILY,
    fontWeight: process.env.EXPO_OS === 'ios' ? undefined : '900',
    fontSize: HEADLINE_SIZE,
    lineHeight: HEADLINE_LINE + 4,
    color: '#f2f5ec',
    letterSpacing: 0.3,
    textAlign: 'center',
  },
  link: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontSize: 15,
    fontWeight: '600',
    textDecorationLine: 'underline',
  },
  cta: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    borderCurve: 'continuous',
  },
  ctaLabel: {
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  exit: {
    position: 'absolute',
  },
});
