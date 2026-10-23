import { BlurMask, Canvas, Circle, ColorMatrix, FractalNoise, Group, Path, RadialGradient, Rect, Skia, type SkPath, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeInDown,
  FadeOut,
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

import { Padlock, PatternDot, RIGHT, toneOf, WRONG } from './PatternLockParts';

type Props = {
  /** The pattern that unlocks, as dot numbers 0–8 read like a page. Defaults to a "Z". */
  pattern?: number[];
  /** Draws a wrong pattern, then the right one, on a loop of LOOP s. */
  autoPlay?: boolean;
  onUnlock?: () => void;
};

// A "Z": along the top, down the diagonal, along the bottom.
const PATTERN_Z = [0, 1, 2, 4, 6, 7, 8];
// The wrong guess autoPlay draws first: an "L".
const PATTERN_L = [0, 3, 6, 7, 8];
const MIN_DOTS = 4;

const BACKGROUND = '#05060a';
const TEXT = '#eef3ff';
const MUTED = 'rgba(214,224,245,0.5)';
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';

// Grid (pt): dots SPACING apart, at most SPACING_MAX; the grid's centre at GRID_AT of the height.
const SPACING_SHARE = 0.27;
const SPACING_MAX = 110;
const GRID_AT = 0.53;
// A finger within HIT of a dot's centre (share of the spacing) picks it up.
const HIT = 0.34;

// The line: three strokes, a wide soft glow, a narrower one and a bright core.
const GLOW_WIDTH = 16;
const CORE_WIDTH = 3.5;
// The open end follows the finger on a spring (stiffness, damping ratio), so it lags and
// stretches; a dot it snaps onto springs from where the finger was to its centre, overshooting.
const TAIL_STIFFNESS = 650;
const TAIL_DAMPING = 0.72;
const SNAP_STIFFNESS = 420;
const SNAP_DAMPING = 0.42;
// The open end bows against its motion by this much per pt/s, at most BOW_MAX pt.
const BOW = 0.04;
const BOW_MAX = 46;

// A wrong pattern: the grid shakes sideways like a struck spring, SHAKE pt at SHAKE_HZ, dying
// away at SHAKE_DECAY (1/s) over SHAKE_TIME s; the red path holds for WRONG_HOLD ms and fades
// over FADE_TIME ms.
const SHAKE = 18;
const SHAKE_HZ = 6;
const SHAKE_DECAY = 4;
const SHAKE_TIME = 1.1;
const WRONG_HOLD = 650;
const FADE_TIME = 300;
// The right pattern: the path pulses, the dots collapse into the centre, the padlock forms and
// springs open with a check; RESET_AFTER ms after release the grid comes back.
const PULSE_IN = 140;
const PULSE_OUT = 340;
const COLLAPSE_AT = 380;
const COLLAPSE_SPRING = { duration: 620, dampingRatio: 0.82 };
const LOCK_AT = 760;
const LOCK_SPRING = { duration: 700, dampingRatio: 0.68 };
const OPEN_AT = 1380;
const OPEN_SPRING = { duration: 520, dampingRatio: 0.5 };
const CHECK_AT = 1480;
const CHECK_TIME = 340;
const RESET_AFTER = 3000;
const EXPAND_SPRING = { duration: 700, dampingRatio: 0.8 };
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// autoPlay loop (seconds): the "L" is drawn and rejected, then the "Z" unlocks and the grid
// comes back, at rest, before the loop starts over. Each stroke between dots takes SEGMENT s.
const LOOP = 10;
const WRONG_AT = 0.6;
const RIGHT_AT = 3.7;
const SEGMENT = 0.27;
const LIFT_AFTER = 0.12;
// A touch pauses the loop this long; then it starts over from the empty grid.
const USER_HOLD = 4;

type Status = 'idle' | 'wrong' | 'right';
const COPY: Record<Status, string> = {
  idle: 'Connect at least four dots',
  wrong: 'Wrong pattern. Try again.',
  right: 'Unlocked',
};

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

function passed(before: number, now: number, at: number) {
  'worklet';
  return now >= before ? before < at && now >= at : at > before || at <= now;
}

/** The dot halfway between two dots, if the line between them crosses one, else −1. */
function between(a: number, b: number) {
  'worklet';
  const ra = Math.floor(a / 3);
  const ca = a % 3;
  const rb = Math.floor(b / 3);
  const cb = b % 3;
  if ((ra + rb) % 2 !== 0 || (ca + cb) % 2 !== 0) return -1;
  return ((ra + rb) / 2) * 3 + (ca + cb) / 2;
}

/** A spring step for a 2D point (semi-implicit Euler): returns [x, y, vx, vy]. */
function springStep(x: number, y: number, vx: number, vy: number, tx: number, ty: number, k: number, ratio: number, dt: number) {
  'worklet';
  const c = 2 * Math.sqrt(k) * ratio;
  const nvx = vx + (k * (tx - x) - c * vx) * dt;
  const nvy = vy + (k * (ty - y) - c * vy) * dt;
  return [x + nvx * dt, y + nvy * dt, nvx, nvy];
}

/** Where the virtual finger is at `u` (0…1) along a pattern: each stroke eased into its dot. */
function along(pattern: number[], u: number, centres: number[]) {
  'worklet';
  const strokes = pattern.length - 1;
  const at = clamp(u, 0, 1) * strokes;
  const k = Math.min(strokes - 1, Math.floor(at));
  const f = at - k;
  const eased = 0.45 * f + 0.55 * f * f * (3 - 2 * f);
  const a = pattern[k];
  const b = pattern[k + 1];
  return [centres[a * 2] + (centres[b * 2] - centres[a * 2]) * eased, centres[a * 2 + 1] + (centres[b * 2 + 1] - centres[a * 2 + 1]) * eased];
}

function samePattern(a: number[], b: number[]) {
  'worklet';
  if (a.length !== b.length) return false;
  for (let k = 0; k < a.length; k += 1) if (a[k] !== b[k]) return false;
  return true;
}

function haptic(kind: 'select' | 'wrong' | 'right') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.notificationAsync(kind === 'wrong' ? Haptics.NotificationFeedbackType.Error : Haptics.NotificationFeedbackType.Success).catch(() => {});
}

/**
 * A 3×3 pattern lock on a dark screen lit by its own glow. Drawing connects the dots with an
 * elastic, glowing line whose open end lags the finger like a rubber band; each dot pops with a
 * ripple as it is picked up, and dots passed over are picked up too. A wrong pattern turns red
 * and shakes the grid; the right one pulses, collapses into the centre and becomes an open
 * padlock with a check, then the grid comes back.
 */
export function PatternLockInput({ pattern = PATTERN_Z, autoPlay = false, onUnlock }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [status, setStatus] = useState<Status>('idle');

  const grid = useMemo(() => {
    if (!size) return null;
    const spacing = Math.min(SPACING_MAX, size.width * SPACING_SHARE);
    const cx = size.width / 2;
    const cy = size.height * GRID_AT;
    const centres: number[] = [];
    for (let k = 0; k < 9; k += 1) centres.push(cx + ((k % 3) - 1) * spacing, cy + (Math.floor(k / 3) - 1) * spacing);
    return { spacing, cx, cy, centres, width: size.width, height: size.height };
  }, [size]);

  const now = useSharedValue(0);
  const selected = useSharedValue<number[]>([]);
  const hits = useSharedValue<number[]>([-1, -1, -1, -1, -1, -1, -1, -1, -1]);
  // 0: taking input, 1: showing a wrong pattern, 2: unlocking.
  const stage = useSharedValue(0);
  const drawing = useSharedValue(false);
  const finger = useSharedValue([0, 0]);
  const tail = useSharedValue([0, 0, 0, 0]);
  const snap = useSharedValue([0, 0, 0, 0]);
  const wrong = useSharedValue(0);
  const right = useSharedValue(0);
  // When the last wrong pattern was let go (s, on `now`).
  const shakeAt = useSharedValue(-10);
  const shake = useSharedValue(0);
  const lineAlpha = useSharedValue(1);
  const pulse = useSharedValue(0);
  const collapse = useSharedValue(0);
  const lock = useSharedValue(0);
  const open = useSharedValue(0);
  const check = useSharedValue(0);
  const glow = useSharedValue(0);
  // autoPlay.
  const clock = useSharedValue(0);
  const looping = useSharedValue(autoPlay);
  const hold = useSharedValue(0);
  const ghost = useSharedValue(-1);
  const ghostAlpha = useSharedValue(0);

  useEffect(() => {
    looping.set(autoPlay);
    clock.set(0);
    hold.set(0);
  }, [autoPlay, clock, hold, looping]);

  const unlocked = () => onUnlock?.();

  function clearPattern() {
    'worklet';
    selected.set([]);
    hits.set([-1, -1, -1, -1, -1, -1, -1, -1, -1]);
    lineAlpha.set(1);
    glow.set(withTiming(0, { duration: 400 }));
  }

  /** Adds a dot to the pattern, with the dot it jumped over first if it skipped one. */
  function pick(k: number, user: boolean) {
    'worklet';
    if (!grid) return;
    const list = selected.get();
    if (list.indexOf(k) >= 0) return;
    const next = list.slice();
    const times = hits.get().slice();
    const last = next.length > 0 ? next[next.length - 1] : -1;
    const middle = last >= 0 ? between(last, k) : -1;
    if (middle >= 0 && next.indexOf(middle) < 0) {
      next.push(middle);
      times[middle] = now.get() - 0.04;
    }
    next.push(k);
    times[k] = now.get();
    selected.set(next);
    hits.set(times);
    // The line's end snaps from where the finger is onto the dot, and springs home.
    const f = finger.get();
    snap.set(reducedMotion ? [0, 0, 0, 0] : [f[0] - grid.centres[k * 2], f[1] - grid.centres[k * 2 + 1], 0, 0]);
    glow.set(withTiming(next.length / 9, { duration: 260, easing: EASE_OUT }));
    if (user) scheduleOnRN(haptic, 'select');
  }

  /** Picks up any dot the finger passes over between two points. */
  function sweep(x0: number, y0: number, x1: number, y1: number, user: boolean) {
    'worklet';
    if (!grid) return;
    const steps = Math.max(1, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 6));
    const reach = grid.spacing * HIT;
    for (let s = 1; s <= steps; s += 1) {
      const x = x0 + ((x1 - x0) * s) / steps;
      const y = y0 + ((y1 - y0) * s) / steps;
      for (let k = 0; k < 9; k += 1) {
        if (Math.hypot(x - grid.centres[k * 2], y - grid.centres[k * 2 + 1]) < reach) pick(k, user);
      }
    }
  }

  function fail(user: boolean) {
    'worklet';
    stage.set(1);
    wrong.set(withTiming(1, { duration: 160 }));
    if (!reducedMotion) shakeAt.set(now.get());
    lineAlpha.set(
      withDelay(
        WRONG_HOLD,
        withTiming(0, { duration: FADE_TIME }, (finished) => {
          if (!finished) return;
          clearPattern();
          wrong.set(withTiming(0, { duration: 240 }));
          stage.set(0);
          scheduleOnRN(setStatus, 'idle');
        }),
      ),
    );
    scheduleOnRN(setStatus, 'wrong');
    if (user) scheduleOnRN(haptic, 'wrong');
  }

  function succeed(user: boolean) {
    'worklet';
    stage.set(2);
    right.set(withTiming(1, { duration: 200 }));
    pulse.set(withSequence(withTiming(1, { duration: PULSE_IN, easing: EASE_OUT }), withTiming(0, { duration: PULSE_OUT })));
    if (reducedMotion) {
      collapse.set(withDelay(COLLAPSE_AT, withTiming(1, { duration: 300 })));
      lock.set(withDelay(LOCK_AT, withTiming(1, { duration: 300 })));
      check.set(withDelay(CHECK_AT, withTiming(1, { duration: CHECK_TIME })));
    } else {
      collapse.set(withDelay(COLLAPSE_AT, withSpring(1, COLLAPSE_SPRING)));
      lock.set(withDelay(LOCK_AT, withSpring(1, LOCK_SPRING)));
      open.set(withDelay(OPEN_AT, withSpring(1, OPEN_SPRING)));
      check.set(withDelay(CHECK_AT, withTiming(1, { duration: CHECK_TIME, easing: EASE_OUT })));
    }
    // Then everything goes back the way it came, and the grid takes input again.
    open.set(withDelay(RESET_AFTER, withTiming(0, { duration: 200 })));
    check.set(withDelay(RESET_AFTER, withTiming(0, { duration: 200 })));
    lock.set(
      withDelay(
        RESET_AFTER,
        withTiming(0, { duration: 280 }, (finished) => {
          if (!finished) return;
          clearPattern();
          right.set(withTiming(0, { duration: 300 }));
          collapse.set(reducedMotion ? withTiming(0, { duration: 300 }) : withSpring(0, EXPAND_SPRING));
          stage.set(0);
          scheduleOnRN(setStatus, 'idle');
        }),
      ),
    );
    scheduleOnRN(setStatus, 'right');
    scheduleOnRN(unlocked);
    if (user) scheduleOnRN(haptic, 'right');
  }

  function release(user: boolean) {
    'worklet';
    if (!drawing.get()) return;
    drawing.set(false);
    const list = selected.get();
    if (list.length === 0) return;
    if (list.length >= MIN_DOTS && samePattern(list, pattern)) succeed(user);
    else fail(user);
  }

  function begin(x: number, y: number, user: boolean) {
    'worklet';
    if (stage.get() !== 0) return;
    drawing.set(true);
    finger.set([x, y]);
    tail.set([x, y, 0, 0]);
    sweep(x, y, x, y, user);
  }

  function move(x: number, y: number, user: boolean) {
    'worklet';
    if (!drawing.get()) return;
    const f = finger.get();
    finger.set([x, y]);
    sweep(f[0], f[1], x, y, user);
  }

  function touched() {
    'worklet';
    hold.set(USER_HOLD);
    if (ghost.get() >= 0) {
      // A virtual stroke in progress is dropped.
      ghost.set(-1);
      ghostAlpha.set(withTiming(0, { duration: 150 }));
      if (drawing.get()) {
        drawing.set(false);
        clearPattern();
      }
    }
  }

  useFrameCallback((frame) => {
    if (!grid) return;
    const dt = clamp((frame.timeSincePreviousFrame ?? 16) / 1000, 0, 1 / 30);
    now.set(now.get() + dt);

    // autoPlay: a fixed script on a clock, paused for a while by any touch.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) clock.set(stage.get() === 0 ? 0 : RIGHT_AT + (PATTERN_Z.length - 1) * SEGMENT + 1);
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      const strokes = [
        { at: WRONG_AT, dots: PATTERN_L },
        { at: RIGHT_AT, dots: pattern },
      ];
      for (let s = 0; s < strokes.length; s += 1) {
        const start = strokes[s].at;
        const dots = strokes[s].dots;
        const length = (dots.length - 1) * SEGMENT;
        if (passed(before, t, start) && stage.get() === 0) {
          ghost.set(s);
          ghostAlpha.set(withTiming(1, { duration: 140 }));
          const p = along(dots, 0, grid.centres);
          begin(p[0], p[1], false);
        }
        if (ghost.get() === s) {
          const p = along(dots, (t - start) / length, grid.centres);
          move(p[0], p[1], false);
          if (t >= start + length + LIFT_AFTER) {
            ghost.set(-1);
            ghostAlpha.set(withTiming(0, { duration: 180 }));
            release(false);
          }
        }
      }
    }

    // The wrong-pattern shake: a struck spring dying away.
    const since = now.get() - shakeAt.get();
    const sway = since >= 0 && since < SHAKE_TIME ? SHAKE * Math.exp(-since * SHAKE_DECAY) * Math.sin(since * SHAKE_HZ * Math.PI * 2) : 0;
    if (sway !== shake.get()) shake.set(sway);

    // The open end chases the finger (or, once let go, falls back onto the last dot).
    const list = selected.get();
    const last = list.length > 0 ? list[list.length - 1] : -1;
    const f = finger.get();
    const goalX = drawing.get() || last < 0 ? f[0] : grid.centres[last * 2];
    const goalY = drawing.get() || last < 0 ? f[1] : grid.centres[last * 2 + 1];
    const tl = tail.get();
    tail.set(reducedMotion ? [goalX, goalY, 0, 0] : springStep(tl[0], tl[1], tl[2], tl[3], goalX, goalY, TAIL_STIFFNESS, TAIL_DAMPING, dt));
    const sn = snap.get();
    if (sn[0] !== 0 || sn[1] !== 0 || sn[2] !== 0 || sn[3] !== 0) {
      const next = springStep(sn[0], sn[1], sn[2], sn[3], 0, 0, SNAP_STIFFNESS, SNAP_DAMPING, dt);
      const still = Math.abs(next[0]) + Math.abs(next[1]) < 0.05 && Math.abs(next[2]) + Math.abs(next[3]) < 1;
      snap.set(still ? [0, 0, 0, 0] : next);
    }
  });

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((event) => {
      touched();
      begin(event.x, event.y, true);
    })
    .onUpdate((event) => move(event.x, event.y, true))
    .onFinalize(() => release(true));

  /** The pattern's line: through the picked dots, the last one springing in, then the open end. */
  const line = useDerivedValue<SkPath>(() => {
    const path = Skia.PathBuilder.Make();
    if (!grid) return path.build();
    const list = selected.get();
    if (list.length === 0) return path.build();
    const c = reducedMotion ? 0 : collapse.get();
    const sn = snap.get();
    let ax = 0;
    let ay = 0;
    for (let k = 0; k < list.length; k += 1) {
      const d = list[k];
      let x = grid.centres[d * 2] + (grid.cx - grid.centres[d * 2]) * c;
      let y = grid.centres[d * 2 + 1] + (grid.cy - grid.centres[d * 2 + 1]) * c;
      if (k === list.length - 1) {
        x += sn[0];
        y += sn[1];
      }
      if (k === 0) path.moveTo(x, y);
      else path.lineTo(x, y);
      ax = x;
      ay = y;
    }
    const tl = tail.get();
    if (stage.get() === 0 && (drawing.get() || Math.hypot(tl[0] - ax, tl[1] - ay) > 0.8)) {
      // A rubber band: the open end bows back against the way it is moving.
      let bx = -tl[2] * BOW;
      let by = -tl[3] * BOW;
      const bend = Math.hypot(bx, by);
      if (bend > BOW_MAX) {
        bx *= BOW_MAX / bend;
        by *= BOW_MAX / bend;
      }
      path.quadTo((ax + tl[0]) / 2 + bx, (ay + tl[1]) / 2 + by, tl[0], tl[1]);
    }
    return path.build();
  });

  const tone = useDerivedValue(() => toneOf(wrong.get(), right.get()));
  const lineOpacity = useDerivedValue(() => lineAlpha.get() * (1 - clamp(collapse.get() * 1.4, 0, 1)));
  const glowWidth = useDerivedValue(() => GLOW_WIDTH + pulse.get() * 14);
  const coreWidth = useDerivedValue(() => CORE_WIDTH + pulse.get() * 2.5);
  const gridShift = useDerivedValue(() => [{ translateX: shake.get() }]);
  const auraColors = useDerivedValue(() => {
    const t = tone.get();
    return [t, 'rgba(5,6,10,0)'];
  });
  const auraOpacity = useDerivedValue(() => 0.13 + glow.get() * 0.1 + pulse.get() * 0.12 + lock.get() * 0.08);
  const ghostX = useDerivedValue(() => finger.get()[0]);
  const ghostY = useDerivedValue(() => finger.get()[1]);

  const statusColor = status === 'wrong' ? WRONG : status === 'right' ? RIGHT : MUTED;

  return (
    <View
      style={[styles.screen, { backgroundColor: BACKGROUND }]}
      onLayout={(event: LayoutChangeEvent) => {
        const { width, height } = event.nativeEvent.layout;
        setSize({ width, height });
      }}
    >
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          {grid && (
            <Canvas style={StyleSheet.absoluteFill}>
              {/* The grain below blends with what is under it, so the background is painted first. */}
              <Rect x={0} y={0} width={grid.width} height={grid.height} color={BACKGROUND} />
              {/* A soft aura behind the grid in the lock's colour, brighter as the pattern grows. */}
              <Rect x={0} y={0} width={grid.width} height={grid.height} opacity={auraOpacity}>
                <RadialGradient c={vec(grid.cx, grid.cy)} r={grid.spacing * 2.6} colors={auraColors} />
              </Rect>
              <Rect x={0} y={0} width={grid.width} height={grid.height} blendMode="overlay" opacity={0.12}>
                <FractalNoise freqX={0.85} freqY={0.85} octaves={2} seed={9} />
                <ColorMatrix matrix={[0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0, 0, 0, 0, 1]} />
              </Rect>
              <Group transform={gridShift}>
                <Group opacity={lineOpacity}>
                  <Path path={line} style="stroke" strokeWidth={glowWidth} strokeCap="round" strokeJoin="round" color={tone} opacity={0.32}>
                    <BlurMask blur={10} style="normal" />
                  </Path>
                  <Path path={line} style="stroke" strokeWidth={7} strokeCap="round" strokeJoin="round" color={tone} opacity={0.55}>
                    <BlurMask blur={3} style="normal" />
                  </Path>
                  <Path path={line} style="stroke" strokeWidth={coreWidth} strokeCap="round" strokeJoin="round" color="rgba(240,250,255,0.92)" />
                </Group>
                {Array.from({ length: 9 }, (_, k) => (
                  <PatternDot
                    key={k}
                    index={k}
                    x={grid.centres[k * 2]}
                    y={grid.centres[k * 2 + 1]}
                    cx={grid.cx}
                    cy={grid.cy}
                    hits={hits}
                    now={now}
                    wrong={wrong}
                    right={right}
                    collapse={collapse}
                    lock={lock}
                    pulse={pulse}
                    reducedMotion={reducedMotion}
                  />
                ))}
                <Padlock cx={grid.cx} cy={grid.cy} lock={lock} open={open} check={check} background={BACKGROUND} reducedMotion={reducedMotion} />
              </Group>
              {/* The virtual finger of autoPlay, so a recording shows what is drawing. */}
              <Group opacity={ghostAlpha}>
                <Circle cx={ghostX} cy={ghostY} r={24} color="rgba(255,255,255,0.12)" />
                <Circle cx={ghostX} cy={ghostY} r={24} style="stroke" strokeWidth={1.4} color="rgba(255,255,255,0.45)" />
              </Group>
            </Canvas>
          )}
        </View>
      </GestureDetector>

      {grid && (
        <View pointerEvents="none" style={[styles.header, { top: Math.max(insets.top + 40, grid.cy - grid.spacing * 1.5 - 150) }]}>
          <Text style={styles.caps}>PRIVATE NOTES</Text>
          <Text style={styles.title}>Draw to unlock</Text>
        </View>
      )}
      {grid && (
        <View pointerEvents="none" style={[styles.statusBox, { top: grid.cy + grid.spacing * 1.5 + 54 }]}>
          <Animated.Text
            key={status}
            entering={reducedMotion ? FadeIn.duration(200) : FadeInDown.duration(260).easing(EASE_OUT)}
            exiting={FadeOut.duration(140)}
            style={[styles.status, { color: statusColor }]}
          >
            {COPY[status]}
          </Animated.Text>
        </View>
      )}
      <Text style={[styles.footer, { bottom: insets.bottom + 26 }]}>Forgot pattern?</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  header: {
    position: 'absolute',
    left: 24,
    right: 24,
    alignItems: 'center',
    gap: 8,
  },
  caps: {
    fontFamily: FAMILY,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 2.4,
    color: MUTED,
  },
  title: {
    fontFamily: FAMILY,
    fontSize: 28,
    fontWeight: '600',
    letterSpacing: -0.4,
    color: TEXT,
  },
  statusBox: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 24,
    alignItems: 'center',
  },
  status: {
    position: 'absolute',
    fontFamily: FAMILY,
    fontSize: 15,
    fontWeight: '500',
    letterSpacing: 0.1,
  },
  footer: {
    position: 'absolute',
    alignSelf: 'center',
    fontFamily: FAMILY,
    fontSize: 14,
    fontWeight: '500',
    color: MUTED,
  },
});
