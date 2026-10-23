import { BlurMask, Circle, Group, Path, RoundedRect, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { interpolateColor, type SharedValue, useDerivedValue } from 'react-native-reanimated';

/**
 * The drawn pieces of the pattern lock: a dot of the grid, and the padlock the dots collapse into.
 * Both take the shared values of the lock and derive their whole look on the UI thread.
 */

export const NEUTRAL = '#6fe3ff';
export const WRONG = '#ff4d63';
export const RIGHT = '#59f5a6';
const IDLE_CORE = 'rgba(232,240,255,0.55)';
const IDLE_RING = 'rgba(232,240,255,0.13)';

// A dot: a ring of RING pt round a core of CORE pt that grows by CORE_GROW when the dot is hit.
const RING = 22;
const CORE = 4.5;
const CORE_GROW = 4.5;
// The ripple a hit sends out: from the core to RIPPLE pt over RIPPLE_TIME s.
const RIPPLE = 46;
const RIPPLE_TIME = 0.65;

/** The colour of the lock's light for its state: neutral, red as `wrong` → 1, green as `right` → 1. */
export function toneOf(wrong: number, right: number) {
  'worklet';
  const base = interpolateColor(wrong, [0, 1], [NEUTRAL, WRONG]);
  return interpolateColor(right, [0, 1], [base as string, RIGHT]) as string;
}

/** A hit's pop: 0 → 1 with a springy overshoot, `t` seconds after the hit. */
function pop(t: number) {
  'worklet';
  if (t < 0) return 0;
  return 1 - Math.exp(-t * 13) * Math.cos(t * 21);
}

type DotProps = {
  index: number;
  /** Where the dot rests, and the point the dots collapse into. */
  x: number;
  y: number;
  cx: number;
  cy: number;
  /** When each dot was hit (s, on `now`), or a negative number if it is not in the pattern. */
  hits: SharedValue<number[]>;
  now: SharedValue<number>;
  wrong: SharedValue<number>;
  right: SharedValue<number>;
  collapse: SharedValue<number>;
  lock: SharedValue<number>;
  pulse: SharedValue<number>;
  reducedMotion: boolean;
};

export function PatternDot({ index, x, y, cx, cy, hits, now, wrong, right, collapse, lock, pulse, reducedMotion }: DotProps) {
  const age = useDerivedValue(() => {
    const at = hits.get()[index];
    return at < 0 ? -1 : now.get() - at;
  });
  const p = useDerivedValue(() => (reducedMotion ? (age.get() < 0 ? 0 : Math.min(1, age.get() * 8)) : pop(age.get())));
  const tone = useDerivedValue(() => toneOf(wrong.get(), right.get()));
  const position = useDerivedValue(() => {
    const c = reducedMotion ? 0 : collapse.get();
    return [{ translateX: x + (cx - x) * c }, { translateY: y + (cy - y) * c }];
  });
  // The dots fade as the padlock forms out of them (and, with reduced motion, as they would collapse).
  const opacity = useDerivedValue(() => {
    const fade = reducedMotion ? collapse.get() : Math.min(1, lock.get() * 3);
    return Math.max(0, 1 - fade);
  });

  const coreR = useDerivedValue(() => CORE + CORE_GROW * p.get() + pulse.get() * 2.5 * (age.get() < 0 ? 0 : 1));
  const coreColor = useDerivedValue(() => (age.get() < 0 ? IDLE_CORE : (interpolateColor(Math.min(1, Math.max(0, p.get())), [0, 1], [IDLE_CORE, tone.get()]) as string)));
  const haloOpacity = useDerivedValue(() => (age.get() < 0 ? 0 : Math.max(0, Math.min(1, p.get())) * (0.55 + pulse.get() * 0.45)));
  const haloR = useDerivedValue(() => 15 + pulse.get() * 10);
  const ringColor = useDerivedValue(() => (age.get() < 0 ? IDLE_RING : tone.get()));
  const ringOpacity = useDerivedValue(() => (age.get() < 0 ? 1 : 0.45));
  const ringR = useDerivedValue(() => RING * (1 - 0.25 * (reducedMotion ? 0 : collapse.get())));
  const rippleE = useDerivedValue(() => {
    const t = age.get();
    return t < 0 || t > RIPPLE_TIME ? 1 : t / RIPPLE_TIME;
  });
  const rippleR = useDerivedValue(() => {
    const e = rippleE.get();
    return reducedMotion ? RING : CORE + (RIPPLE - CORE) * (1 - (1 - e) * (1 - e) * (1 - e));
  });
  const rippleOpacity = useDerivedValue(() => {
    const e = rippleE.get();
    return (1 - e) * (1 - e) * 0.8;
  });

  return (
    <Group transform={position} opacity={opacity}>
      <Circle cx={0} cy={0} r={haloR} color={tone} opacity={haloOpacity}>
        <BlurMask blur={12} style="normal" />
      </Circle>
      <Circle cx={0} cy={0} r={ringR} style="stroke" strokeWidth={1.2} color={ringColor} opacity={ringOpacity} />
      <Circle cx={0} cy={0} r={rippleR} style="stroke" strokeWidth={2} color={tone} opacity={rippleOpacity} />
      <Circle cx={0} cy={0} r={coreR} color={coreColor} />
    </Group>
  );
}

type PadlockProps = {
  cx: number;
  cy: number;
  /** 0 → 1: the collapsed dots swell into the padlock's body and its shackle draws on. */
  lock: SharedValue<number>;
  /** 0 → 1: the shackle springs open. */
  open: SharedValue<number>;
  /** 0 → 1: the check is drawn on the body. */
  check: SharedValue<number>;
  background: string;
  reducedMotion: boolean;
};

// The padlock (pt): its body, the shackle's half-width and height above the body, and stroke.
const BODY_W = 66;
const BODY_H = 54;
const BODY_R = 15;
const BLOB = 20;
const SHACKLE_HALF = 18;
const SHACKLE_RISE = 30;
const SHACKLE_STROKE = 7.5;
// The shackle lifts this far and its free leg swings out this much (rad) when it opens.
const OPEN_LIFT = 13;
const OPEN_SWING = -0.32;

export function Padlock({ cx, cy, lock, open, check, background, reducedMotion }: PadlockProps) {
  const shackle = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    b.moveTo(-SHACKLE_HALF, 0);
    b.lineTo(-SHACKLE_HALF, -SHACKLE_RISE + SHACKLE_HALF);
    b.arcToOval({ x: -SHACKLE_HALF, y: -SHACKLE_RISE, width: SHACKLE_HALF * 2, height: SHACKLE_HALF * 2 }, 180, 180, false);
    b.lineTo(SHACKLE_HALF, 0);
    return b.build();
  }, []);
  const tick = useMemo(() => Skia.PathBuilder.Make().moveTo(-12, 1).lineTo(-3.5, 9.5).lineTo(13, -8).build(), []);

  const tone = RIGHT;
  const e = useDerivedValue(() => (reducedMotion ? Math.min(1, lock.get()) : lock.get()));
  // The body grows out of the dot-sized blob the grid collapsed into, and sinks a little to make
  // room for the shackle above it.
  const bodyX = useDerivedValue(() => -(BLOB + (BODY_W - BLOB) * e.get()) / 2);
  const bodyY = useDerivedValue(() => -(BLOB + (BODY_H - BLOB) * e.get()) / 2 + 14 * e.get());
  const bodyW = useDerivedValue(() => BLOB + (BODY_W - BLOB) * e.get());
  const bodyH = useDerivedValue(() => BLOB + (BODY_H - BLOB) * e.get());
  const bodyR = useDerivedValue(() => BLOB / 2 + (BODY_R - BLOB / 2) * Math.min(1, e.get()));
  const opacity = useDerivedValue(() => Math.max(0, Math.min(1, reducedMotion ? lock.get() : lock.get() * 3)));
  const shackleEnd = useDerivedValue(() => Math.max(0, Math.min(1, reducedMotion ? lock.get() : lock.get() * 1.6 - 0.6)));
  const shackleTransform = useDerivedValue(() => {
    const o = reducedMotion ? 0 : open.get();
    const top = -BODY_H / 2 + 14 + 3;
    // Pivot on the right leg's foot, so the left leg swings out of the body as it lifts.
    return [
      { translateX: SHACKLE_HALF },
      { translateY: top - OPEN_LIFT * o },
      { rotate: OPEN_SWING * o },
      { translateX: -SHACKLE_HALF },
    ];
  });
  const checkEnd = useDerivedValue(() => Math.max(0, Math.min(1, check.get())));
  const haloOpacity = useDerivedValue(() => opacity.get() * 0.5);

  return (
    <Group transform={[{ translateX: cx }, { translateY: cy }]} opacity={opacity}>
      <RoundedRect x={bodyX} y={bodyY} width={bodyW} height={bodyH} r={bodyR} color={tone} opacity={haloOpacity}>
        <BlurMask blur={22} style="normal" />
      </RoundedRect>
      <Group transform={shackleTransform}>
        <Path path={shackle} style="stroke" strokeWidth={SHACKLE_STROKE} strokeCap="round" color={tone} start={0} end={shackleEnd} />
      </Group>
      <RoundedRect x={bodyX} y={bodyY} width={bodyW} height={bodyH} r={bodyR} color={tone} />
      <Group transform={[{ translateY: 14 }]}>
        <Path path={tick} style="stroke" strokeWidth={5} strokeCap="round" strokeJoin="round" color={background} start={0} end={checkEnd} />
      </Group>
    </Group>
  );
}
