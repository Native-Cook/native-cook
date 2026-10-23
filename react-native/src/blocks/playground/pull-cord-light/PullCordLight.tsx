import {
  BlurMask,
  Canvas,
  Circle,
  ColorMatrix,
  Group,
  Image as SkiaImage,
  LinearGradient,
  Mask,
  Oval,
  Path,
  RadialGradient,
  Rect,
  Skia,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The room, drawn dark and lit from the same photo. */
  photo?: string;
  /** Starts with the light on. */
  initiallyOn?: boolean;
  /** Pulls the cord to switch the light on, swings the lamp, and pulls it off again, on a loop. */
  autoPlay?: boolean;
  /** Called when a pull switches the light. */
  onToggle?: (on: boolean) => void;
};

const PHOTO = 'https://images.unsplash.com/photo-1529047033375-f402d3da24ca?auto=format&fit=crop&w=900&h=1950&q=80';

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';

// The lamp hangs from a ceiling point just above the screen on a wire WIRE_SHARE of the screen's
// height long, and swings about that point as a damped pendulum.
const PIVOT_Y = -24;
const WIRE_SHARE = 0.27;
const GRAVITY = 1800; // pt/s²
const SWING_DAMPING = 1.7; // 1/s
// While held, the lamp follows the finger's angle through a stiff spring, so letting go hands its
// angular speed to the pendulum. Past MAX_TILT it is held back with rubber banding.
const HOLD_STIFFNESS = 260;
const HOLD_DAMPING = 26;
const MAX_TILT = 0.55;

// The shade, in pt, with its top at the end of the wire: a dome RIM wide at its mouth.
const SHADE_RIM = 76;
const SHADE_DEPTH = 84;
const BULB_R = 15;

// The pull cord: a chain of SEGMENTS verlet particles CORD pt long, hanging from inside the rim
// beside the bulb, with a wooden bead on the end that weighs BEAD_WEIGHT times a link.
const SEGMENTS = 15;
const CORD = 190;
const CORD_X = 26;
const CORD_Y = SHADE_DEPTH + 2;
const BEAD_WEIGHT = 2.6;
const SUBSTEPS = 3;
const ITERATIONS = 14;
const AIR = 0.9955; // velocity kept per substep
// Pulled, the links between the ceiling and the finger stretch up to MAX_STRETCH; past that the
// finger is followed with rubber banding. Let go, the stretch springs back at RECOIL per second.
const MAX_STRETCH = 1.32;
const RECOIL = 6;
// Pulling the bead PULL_TO_CLICK pt below where it hangs arms the switch; letting go clicks it.
const PULL_TO_CLICK = 62;
const GRAB_RADIUS = 38;
// How strongly a stretched cord tugs the lamp, and the little kick the switch gives it.
const TUG = 0.02;
const CLICK_KICK = 0.09;

// The light: it comes up at LIGHT_ON per second through a few quick flickers, and dies at
// LIGHT_OFF per second while the filament glows a little longer.
const LIGHT_ON = 9;
const LIGHT_OFF = 5.5;
const FILAMENT_OFF = 2.4;
const CONE_SPREAD = 1.05; // half-width at the far end, in screen widths
const CONE_LENGTH = 1.05; // in screen heights

// Night grading and lamp grading of the same photo (4×5 colour matrices).
const NIGHT = [0.11, 0.15, 0.03, 0, 0.004, 0.11, 0.17, 0.05, 0, 0.008, 0.14, 0.22, 0.15, 0, 0.02, 0, 0, 0, 1, 0];
const LAMP = [1.12, 0.07, 0, 0, -0.03, 0.03, 0.97, 0.02, 0, -0.03, 0, 0.03, 0.72, 0, -0.04, 0, 0, 0, 1, 0];

// autoPlay (seconds into LOOP): pull the cord on, push the lamp into a swing, pull it off, rest.
const LOOP = 10;
const PULL_ON = [0.7, 1.55, 1.75];
const SWING = [3.2, 3.8];
const PULL_OFF = [5.9, 6.75, 6.95];
const PULL_BY = { x: 7, y: 96 };
const SWING_TO = 0.3;
// Over the last SETTLE s the motion is damped harder, so the loop seam meets a still room.
const SETTLE = 0.9;
const USER_HOLD = 4;

const EASE_IN_OUT = Easing.bezierFn(0.65, 0, 0.35, 1);

function rubber(over: number, limit: number) {
  'worklet';
  return limit * (1 - 1 / (1 + over / limit));
}

/** An alpha for a colour string, rounded so a tiny value never prints as 1e-7. */
function alpha(value: number) {
  'worklet';
  return Math.round(Math.min(1, Math.max(0, value)) * 1000) / 1000;
}

function crossed(before: number, now: number, at: number) {
  'worklet';
  return before < at && now >= at;
}

/** A bell, 1 at `at`, gone `width` either side. */
function bump(t: number, at: number, width: number) {
  'worklet';
  return Math.exp(-(((t - at) / width) ** 2));
}

/** The light's level `since` s after it was switched on: three quick dips, then a faint shimmer. */
function flicker(since: number, now: number) {
  'worklet';
  const dips = 0.62 * bump(since, 0.09, 0.045) + 0.4 * bump(since, 0.24, 0.04) + 0.2 * bump(since, 0.43, 0.05);
  const shimmer = 0.012 * Math.sin(now * 13.1) * Math.sin(now * 3.7);
  return Math.max(0, 1 - dips + shimmer);
}

/**
 * A dark bedroom at dusk with a pendant lamp hanging from the ceiling and its pull cord, a chain
 * of links simulated as a rope. Pulling the cord down stretches it; letting go past the click
 * switches the lamp: the bulb glows, a warm cone of light fades in under the shade and the room
 * comes up in lamplight with a soft flicker, while the cord bounces and swings. Pull again to switch
 * it off. The lamp itself can be grabbed and swung, and the cone sweeps the room with it.
 */
export function PullCordLight({ photo = PHOTO, initiallyOn = false, autoPlay = false, onToggle }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const image = useImage(photo);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const pivotX = width / 2;
  const wire = height * WIRE_SHARE;
  const link = CORD / SEGMENTS;
  // Moment arm of the pendulum: the wire plus half the shade.
  const arm = wire + SHADE_DEPTH * 0.6;

  // The lamp's angle (rad, positive swings it left) and its speed.
  const angle = useSharedValue(0);
  const spin = useSharedValue(0);
  const lampHeld = useSharedValue(false);
  const lampTarget = useSharedValue(0);
  const lampGrabFrom = useSharedValue(0);
  const lampAngleFrom = useSharedValue(0);
  // The rope: x, y, previous x, previous y for each particle, flat.
  const rope = useSharedValue<number[]>([]);
  // The particle held by a finger (−1 for none), where the finger wants it, and the offset of the grab.
  const grabbed = useSharedValue(-1);
  const goalX = useSharedValue(0);
  const goalY = useSharedValue(0);
  const offsetX = useSharedValue(0);
  const offsetY = useSharedValue(0);
  const stretch = useSharedValue(1);
  const armed = useSharedValue(false);
  // The light: `on` is the switch, `level` follows it, `glow` adds the flicker, `filament` the bulb.
  const on = useSharedValue(initiallyOn);
  const level = useSharedValue(initiallyOn ? 1 : 0);
  const glow = useSharedValue(initiallyOn ? 1 : 0);
  const filament = useSharedValue(initiallyOn ? 1 : 0);
  const switchedAt = useSharedValue(-10);
  const time = useSharedValue(0);
  const byUser = useSharedValue(false);
  const loopClock = useSharedValue(0);
  // Where the scripted finger took the bead.
  const takenX = useSharedValue(0);
  const takenY = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    loopClock.set(0);
    hold.set(0);
  }, [auto, hold, loopClock, looping]);

  // The rope hangs straight down from its anchor whenever the screen is measured.
  useEffect(() => {
    if (!size) return;
    const next: number[] = [];
    for (let i = 0; i <= SEGMENTS; i += 1) {
      const x = pivotX + CORD_X;
      const y = PIVOT_Y + wire + CORD_Y + i * link;
      next.push(x, y, x, y);
    }
    rope.set(next);
  }, [size, pivotX, wire, link, rope]);

  const click = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };
  const detent = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const toggled = (value: boolean) => onToggle?.(value);

  /** Where the cord hangs from on the shade, for the lamp at angle `a`. */
  const anchorX = (a: number) => {
    'worklet';
    return pivotX + CORD_X * Math.cos(a) - (wire + CORD_Y) * Math.sin(a);
  };
  const anchorY = (a: number) => {
    'worklet';
    return PIVOT_Y + CORD_X * Math.sin(a) + (wire + CORD_Y) * Math.cos(a);
  };

  const flip = (user: boolean) => {
    'worklet';
    const next = !on.get();
    on.set(next);
    if (next) switchedAt.set(time.get());
    if (!reducedMotion) spin.set(spin.get() + (next ? CLICK_KICK : -CLICK_KICK));
    if (user) {
      scheduleOnRN(click);
      scheduleOnRN(toggled, next);
    }
  };

  const grabCord = (index: number, fingerX: number, fingerY: number, user: boolean) => {
    'worklet';
    const r = rope.get();
    byUser.set(user);
    grabbed.set(index);
    offsetX.set(r[index * 4] - fingerX);
    offsetY.set(r[index * 4 + 1] - fingerY);
    goalX.set(r[index * 4]);
    goalY.set(r[index * 4 + 1]);
    armed.set(false);
  };
  const moveCord = (fingerX: number, fingerY: number) => {
    'worklet';
    goalX.set(fingerX + offsetX.get());
    goalY.set(fingerY + offsetY.get());
  };
  const releaseCord = () => {
    'worklet';
    if (grabbed.get() < 0) return;
    grabbed.set(-1);
    if (armed.get()) flip(byUser.get());
    armed.set(false);
  };

  /** The lamp's angle with the finger at (x, y): the angle of the line from the ceiling point. */
  const fingerAngle = (x: number, y: number) => {
    'worklet';
    return Math.atan2(-(x - pivotX), Math.max(1, y - PIVOT_Y));
  };
  const grabLamp = (x: number, y: number, user: boolean) => {
    'worklet';
    byUser.set(user);
    lampHeld.set(true);
    lampGrabFrom.set(fingerAngle(x, y));
    lampAngleFrom.set(angle.get());
    lampTarget.set(angle.get());
  };
  const moveLamp = (x: number, y: number) => {
    'worklet';
    const raw = lampAngleFrom.get() + fingerAngle(x, y) - lampGrabFrom.get();
    const over = Math.abs(raw) - MAX_TILT;
    lampTarget.set(over > 0 ? Math.sign(raw) * (MAX_TILT + rubber(over, 0.2)) : raw);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 40) / 1000;
    const r = rope.get();
    if (dt <= 0 || r.length === 0) return;
    const now = time.get() + dt;
    time.set(now);

    // The script, while nobody is touching.
    let settle = 0;
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        loopClock.set(0);
        // A light left on by hand is switched off by the next pull of the loop.
        if (on.get()) loopClock.set(PULL_OFF[0] - 0.4);
      }
    } else if (looping.get() && !(grabbed.get() >= 0 && byUser.get()) && !(lampHeld.get() && byUser.get())) {
      const before = loopClock.get();
      let t = before + dt;
      if (t >= LOOP) t -= LOOP;
      loopClock.set(t);
      // Two pulls of the cord: down to PULL_BY from where the bead was taken, a beat held, and let go.
      for (let k = 0; k < 2; k += 1) {
        const pull = k === 0 ? PULL_ON : PULL_OFF;
        if (crossed(before, t, pull[0])) {
          const o = SEGMENTS * 4;
          takenX.set(r[o]);
          takenY.set(r[o + 1]);
          grabCord(SEGMENTS, r[o], r[o + 1], false);
        }
        if (t >= pull[0] && t < pull[2] && grabbed.get() === SEGMENTS && !byUser.get()) {
          const p = EASE_IN_OUT(Math.min(1, (t - pull[0]) / (pull[1] - pull[0])));
          moveCord(takenX.get() + PULL_BY.x * p, takenY.get() + PULL_BY.y * p);
        }
        if (crossed(before, t, pull[2])) releaseCord();
      }
      // A push on the lamp, let go mid-swing.
      if (crossed(before, t, SWING[0])) {
        lampHeld.set(true);
        byUser.set(false);
        lampTarget.set(angle.get());
      }
      if (lampHeld.get() && !byUser.get() && t >= SWING[0] && t < SWING[1]) {
        lampTarget.set(SWING_TO * EASE_IN_OUT((t - SWING[0]) / (SWING[1] - SWING[0])));
      }
      if (crossed(before, t, SWING[1])) lampHeld.set(false);
      if (t > LOOP - SETTLE) settle = (t - (LOOP - SETTLE)) / SETTLE;
      if (t < before) {
        // The seam: everything has come to rest; clear what is left so each loop starts the same.
        angle.set(0);
        spin.set(0);
        settle = 1;
      }
    }

    // The light.
    const wantOn = on.get() ? 1 : 0;
    const rate = on.get() ? LIGHT_ON : LIGHT_OFF;
    level.set(level.get() + (wantOn - level.get()) * (1 - Math.exp(-dt * (reducedMotion ? 6 : rate))));
    const fl = reducedMotion || !on.get() ? 1 : flicker(now - switchedAt.get(), now);
    glow.set(level.get() * fl);
    const fRate = on.get() ? LIGHT_ON * 1.4 : FILAMENT_OFF;
    filament.set(filament.get() + (wantOn * fl - filament.get()) * (1 - Math.exp(-dt * fRate)));

    // The lamp: a damped pendulum, pulled toward the finger while held and tugged by the cord.
    const a = angle.get();
    let accel = -(GRAVITY / arm) * Math.sin(a) - SWING_DAMPING * (1 + settle * 6) * spin.get();
    if (reducedMotion) accel = -(GRAVITY / arm) * Math.sin(a) - 9 * spin.get();
    if (lampHeld.get()) accel = HOLD_STIFFNESS * (lampTarget.get() - a) - HOLD_DAMPING * spin.get();

    // Stretch of the held stretch of cord: toward what the finger asks for, or back to 1.
    const g = grabbed.get();
    const ax0 = anchorX(a);
    const ay0 = anchorY(a);
    let tx = goalX.get();
    let ty = goalY.get();
    if (g > 0) {
      const dx = tx - ax0;
      const dy = ty - ay0;
      const d = Math.max(1, Math.hypot(dx, dy));
      const natural = g * link;
      const limit = natural * MAX_STRETCH;
      if (d > limit) {
        const reach = limit + rubber(d - limit, 46);
        tx = ax0 + (dx / d) * reach;
        ty = ay0 + (dy / d) * reach;
      }
      stretch.set(Math.max(1, Math.min(MAX_STRETCH, d / natural)));
      // The click is armed once the held link is far enough below where it hangs.
      const pulled = ty - (ay0 + g * link);
      if (!armed.get() && pulled > PULL_TO_CLICK) {
        armed.set(true);
        if (byUser.get()) scheduleOnRN(detent);
      } else if (armed.get() && pulled < PULL_TO_CLICK - 10) {
        armed.set(false);
      }
      // A stretched cord pulls the shade toward the finger.
      const pull = (stretch.get() - 1) * 1.6;
      const fx = (dx / d) * pull;
      const fy = (dy / d) * pull;
      const rx = ax0 - pivotX;
      const ry = ay0 - PIVOT_Y;
      accel += (fy * rx - fx * ry) * TUG * (reducedMotion ? 0.3 : 1);
    } else {
      stretch.set(1 + (stretch.get() - 1) * Math.exp(-dt * RECOIL));
    }
    const nextSpin = spin.get() + accel * dt;
    spin.set(nextSpin);
    angle.set(a + nextSpin * dt);

    // The rope, in substeps: verlet integration, then the links pulled back to their lengths.
    const a1 = angle.get();
    const ax1 = anchorX(a1);
    const ay1 = anchorY(a1);
    const h = dt / SUBSTEPS;
    const keep = AIR - settle * 0.03 - (reducedMotion ? 0.02 : 0);
    const s = stretch.get();
    rope.modify((p) => {
      'worklet';
      const n = SEGMENTS + 1;
      for (let step = 1; step <= SUBSTEPS; step += 1) {
        const f = step / SUBSTEPS;
        const topX = ax0 + (ax1 - ax0) * f;
        const topY = ay0 + (ay1 - ay0) * f;
        for (let i = 1; i < n; i += 1) {
          if (i === g) continue;
          const o = i * 4;
          const vx = (p[o] - p[o + 2]) * keep;
          const vy = (p[o + 1] - p[o + 3]) * keep;
          p[o + 2] = p[o];
          p[o + 3] = p[o + 1];
          p[o] += vx;
          p[o + 1] += vy + GRAVITY * h * h;
        }
        p[0] = topX;
        p[1] = topY;
        p[2] = topX;
        p[3] = topY;
        if (g > 0) {
          const o = g * 4;
          p[o + 2] = p[o];
          p[o + 3] = p[o + 1];
          p[o] += (tx - p[o]) * f;
          p[o + 1] += (ty - p[o + 1]) * f;
        }
        for (let it = 0; it < ITERATIONS; it += 1) {
          for (let i = 0; i < n - 1; i += 1) {
            const a0 = i * 4;
            const b0 = a0 + 4;
            const wa = i === 0 || i === g ? 0 : 1;
            const wb = i + 1 === g ? 0 : i + 1 === n - 1 ? 1 / BEAD_WEIGHT : 1;
            const sum = wa + wb;
            if (sum === 0) continue;
            const dx = p[b0] - p[a0];
            const dy = p[b0 + 1] - p[a0 + 1];
            const d = Math.max(0.0001, Math.hypot(dx, dy));
            const rest = link * (g > 0 && i >= g ? 1 : s);
            const k = (d - rest) / d / sum;
            p[a0] += dx * k * wa;
            p[a0 + 1] += dy * k * wa;
            p[b0] -= dx * k * wb;
            p[b0 + 1] -= dy * k * wb;
          }
        }
      }
      if (settle >= 1) {
        // At the seam: hang the rope straight, at rest.
        for (let i = 0; i < n; i += 1) {
          const o = i * 4;
          p[o] = ax1;
          p[o + 1] = ay1 + i * link;
          p[o + 2] = p[o];
          p[o + 3] = p[o + 1];
        }
      }
      return p;
    });
  });

  // Drawing.
  const lampTransform = useDerivedValue(() => [{ translateX: pivotX }, { translateY: PIVOT_Y }, { rotate: angle.get() }]);
  const cordPath = useDerivedValue(() => {
    const p = rope.get();
    const builder = Skia.PathBuilder.Make();
    if (p.length === 0) return builder.build();
    builder.moveTo(p[0], p[1]);
    // Through the midpoints between links, so the cord bends smoothly.
    for (let i = 1; i < SEGMENTS; i += 1) {
      const o = i * 4;
      builder.quadTo(p[o], p[o + 1], (p[o] + p[o + 4]) / 2, (p[o + 1] + p[o + 5]) / 2);
    }
    const last = SEGMENTS * 4;
    builder.lineTo(p[last], p[last + 1]);
    return builder.build();
  });
  const beadTransform = useDerivedValue(() => {
    const p = rope.get();
    if (p.length === 0) return [{ translateX: 0 }];
    const o = SEGMENTS * 4;
    const turn = Math.atan2(p[o + 1] - p[o - 3], p[o] - p[o - 4]) - Math.PI / 2;
    return [{ translateX: p[o] }, { translateY: p[o + 1] }, { rotate: turn }];
  });

  // Colours that follow the light.
  const room = useDerivedValue(() => 0.16 + 0.84 * glow.get());
  const shadeColors = useDerivedValue(() => {
    const l = room.get();
    return [
      interpolateColor(l, [0, 1], ['#0a0c11', '#2a2622']),
      interpolateColor(l, [0, 1], ['#2c3243', '#5d564d']),
      interpolateColor(l, [0, 1], ['#3c4660', '#3b352f']),
      interpolateColor(l, [0, 1], ['#1a1f2c', '#1d1a17']),
    ];
  });
  const rimInside = useDerivedValue(() => interpolateColor(glow.get(), [0, 1], ['#05060a', '#ffd9a0']));
  const bulbColor = useDerivedValue(() => interpolateColor(filament.get(), [0, 0.35, 1], ['#4d525e', '#ffb36b', '#fff6e2']));
  const cordColor = useDerivedValue(() => interpolateColor(room.get(), [0, 1], ['#80869a', '#efe2cc']));
  const beadColors = useDerivedValue(() => {
    const l = room.get();
    return [interpolateColor(l, [0, 1], ['#6c5847', '#d49a62']), interpolateColor(l, [0, 1], ['#2a2019', '#7a4b28'])];
  });
  const halo = useDerivedValue(() => [`rgba(255, 214, 150, ${alpha(0.75 * filament.get())})`, 'rgba(255, 190, 120, 0)']);
  const coneColors = useDerivedValue(() => {
    const l = glow.get();
    return [`rgba(255, 220, 160, ${alpha(0.5 * l)})`, `rgba(255, 196, 120, ${alpha(0.2 * l)})`, 'rgba(255, 180, 110, 0)'];
  });
  const coreColors = useDerivedValue(() => {
    const l = glow.get();
    return [`rgba(255, 238, 205, ${alpha(0.4 * l)})`, 'rgba(255, 220, 170, 0)'];
  });
  const shadowColor = useDerivedValue(() => `rgba(0, 0, 0, ${alpha(0.38 * glow.get())})`);
  const shadowTransform = useDerivedValue(() => [{ translateX: 5 * glow.get() }, { translateY: 4 * glow.get() }]);

  // The lit room: brightest where the cone lands, so it sweeps the room as the lamp swings.
  const pool = useDerivedValue(() => {
    const a = angle.get();
    const d = wire + SHADE_DEPTH + height * 0.42;
    return vec(pivotX - d * Math.sin(a), PIVOT_Y + d * Math.cos(a));
  });

  const coneTop = SHADE_DEPTH + 4;
  const coneEnd = height * CONE_LENGTH;
  const coneHalf = width * CONE_SPREAD;
  const cone = Skia.PathBuilder.Make()
    .moveTo(-SHADE_RIM * 0.86, coneTop)
    .lineTo(SHADE_RIM * 0.86, coneTop)
    .lineTo(coneHalf, coneEnd)
    .lineTo(-coneHalf, coneEnd)
    .close()
    .build();
  const core = Skia.PathBuilder.Make()
    .moveTo(-SHADE_RIM * 0.5, coneTop)
    .lineTo(SHADE_RIM * 0.5, coneTop)
    .lineTo(coneHalf * 0.42, coneEnd * 0.8)
    .lineTo(-coneHalf * 0.42, coneEnd * 0.8)
    .close()
    .build();
  const shade = Skia.PathBuilder.Make()
    .moveTo(-11, 10)
    .cubicTo(-38, 12, -SHADE_RIM + 2, 40, -SHADE_RIM, SHADE_DEPTH)
    .lineTo(SHADE_RIM, SHADE_DEPTH)
    .cubicTo(SHADE_RIM - 2, 40, 38, 12, 11, 10)
    .close()
    .build();
  const bead = Skia.PathBuilder.Make()
    .moveTo(0, -2)
    .cubicTo(5, -2, 7.5, 10, 7, 17)
    .cubicTo(6.5, 25, 3.5, 28, 0, 28)
    .cubicTo(-3.5, 28, -6.5, 25, -7, 17)
    .cubicTo(-7.5, 10, -5, -2, 0, -2)
    .close()
    .build();

  const titleOn = useAnimatedStyle(() => ({ opacity: glow.get() }));
  const titleOff = useAnimatedStyle(() => ({ opacity: 1 - level.get() }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: 0.45 + 0.25 * level.get() }));

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    // Hand a scripted pull or push over to the finger.
    if (grabbed.get() >= 0 && !byUser.get()) grabbed.set(-1);
    if (lampHeld.get() && !byUser.get()) lampHeld.set(false);
    armed.set(false);
  };

  // One finger pulls: a second would move the pan's centroid and yank the cord or the lamp.
  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((event) => {
      const p = rope.get();
      if (p.length === 0) return;
      // The nearest link of the cord, the bead first.
      let best = -1;
      let bestDistance = GRAB_RADIUS;
      for (let i = SEGMENTS; i >= 3; i -= 1) {
        const d = Math.hypot(p[i * 4] - event.x, p[i * 4 + 1] - event.y);
        if (d < bestDistance - (i === SEGMENTS ? 8 : 0)) {
          best = i;
          bestDistance = d;
        }
      }
      if (best > 0) {
        touched();
        grabCord(best, event.x, event.y, true);
        return;
      }
      // Or the lamp: the point in the shade's own frame.
      const a = angle.get();
      const dx = event.x - pivotX;
      const dy = event.y - PIVOT_Y;
      const lx = dx * Math.cos(a) + dy * Math.sin(a);
      const ly = -dx * Math.sin(a) + dy * Math.cos(a) - wire;
      if (Math.abs(lx) < SHADE_RIM + 18 && ly > -40 && ly < SHADE_DEPTH + 40) {
        touched();
        grabLamp(event.x, event.y, true);
      }
    })
    .onUpdate((event) => {
      if (grabbed.get() >= 0 && byUser.get()) moveCord(event.x, event.y);
      else if (lampHeld.get() && byUser.get()) moveLamp(event.x, event.y);
    })
    .onFinalize(() => {
      if (!byUser.get()) return;
      if (grabbed.get() >= 0) releaseCord();
      if (lampHeld.get()) lampHeld.set(false);
    });

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="switch"
        accessibilityLabel="Pendant lamp. Pull the cord to switch the light."
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: hgt } = event.nativeEvent.layout;
          setSize({ width: w, height: hgt });
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            {image && (
              <Group>
                <SkiaImage image={image} x={0} y={0} width={width} height={height} fit="cover">
                  <ColorMatrix matrix={NIGHT} />
                </SkiaImage>
                <Mask
                  mask={
                    <Rect x={0} y={0} width={width} height={height}>
                      <RadialGradient
                        c={pool}
                        r={height * 0.8}
                        colors={['rgba(255,255,255,1)', 'rgba(255,255,255,0.5)', 'rgba(255,255,255,0.12)']}
                        positions={[0, 0.45, 1]}
                      />
                    </Rect>
                  }
                >
                  <SkiaImage image={image} x={0} y={0} width={width} height={height} fit="cover" opacity={glow}>
                    <ColorMatrix matrix={LAMP} />
                  </SkiaImage>
                </Mask>
              </Group>
            )}
            {!image && <Rect x={0} y={0} width={width} height={height} color="#07090d" />}
            {/* A vignette that keeps the corners of the room in shadow. */}
            <Rect x={0} y={0} width={width} height={height}>
              <RadialGradient
                c={vec(width / 2, height * 0.42)}
                r={height * 0.72}
                colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.25)', 'rgba(0,0,0,0.72)']}
                positions={[0, 0.55, 1]}
              />
            </Rect>

            {/* The cone of light, which turns with the lamp. */}
            <Group transform={lampTransform}>
              <Group transform={[{ translateY: wire }]}>
                <Path path={cone} blendMode="screen">
                  <LinearGradient start={vec(0, coneTop)} end={vec(0, coneEnd)} colors={coneColors} positions={[0, 0.35, 1]} />
                  <BlurMask blur={16} style="normal" />
                </Path>
                <Circle cx={0} cy={SHADE_DEPTH + 10} r={150} blendMode="screen">
                  <RadialGradient c={vec(0, SHADE_DEPTH + 10)} r={150} colors={halo} />
                </Circle>
                <Path path={core} blendMode="screen">
                  <LinearGradient start={vec(0, coneTop)} end={vec(0, coneEnd * 0.8)} colors={coreColors} />
                  <BlurMask blur={16} style="normal" />
                </Path>
              </Group>
            </Group>

            {/* The cord's shadow on the wall, then the cord and its bead. */}
            <Group transform={shadowTransform}>
              <Path path={cordPath} style="stroke" strokeWidth={2.2} strokeCap="round" color={shadowColor}>
                <BlurMask blur={2.5} style="normal" />
              </Path>
            </Group>

            {/* The wire and the shade. */}
            <Group transform={lampTransform}>
              <Path path={`M 0 0 L 0 ${wire + 4}`} style="stroke" strokeWidth={1.6} color="#2a2e38" />
              <Group transform={[{ translateY: wire }]}>
                <Rect x={-9} y={-2} width={18} height={14} color="#1d1a16" />
                <Path path={shade}>
                  <LinearGradient start={vec(-SHADE_RIM, 0)} end={vec(SHADE_RIM, 0)} colors={shadeColors} positions={[0, 0.32, 0.7, 1]} />
                </Path>
                <Oval x={-SHADE_RIM} y={SHADE_DEPTH - 6} width={SHADE_RIM * 2} height={12} color={rimInside} />
              </Group>
            </Group>

            <Path path={cordPath} style="stroke" strokeWidth={2.2} strokeCap="round" color={cordColor} />
            <Group transform={beadTransform}>
              <Path path={bead}>
                <LinearGradient start={vec(-7, 0)} end={vec(7, 0)} colors={beadColors} />
              </Path>
            </Group>

            {/* The bulb and its glow. */}
            <Group transform={lampTransform}>
              <Group transform={[{ translateY: wire }]}>
                <Circle cx={0} cy={SHADE_DEPTH + 2} r={34} blendMode="screen">
                  <RadialGradient c={vec(0, SHADE_DEPTH + 2)} r={34} colors={halo} />
                </Circle>
                <Circle cx={0} cy={SHADE_DEPTH + 1} r={BULB_R} color={bulbColor} />
                <Circle cx={-4.5} cy={SHADE_DEPTH - 3} r={3.2} color="rgba(255,255,255,0.35)" />
              </Group>
            </Group>
          </Canvas>
        )}

        <View pointerEvents="none" style={[styles.caption, { bottom: insets.bottom + 26 }]}>
          <Text style={styles.overline}>LINEN ROOM · PENDANT</Text>
          <View style={styles.titleRow}>
            <Animated.Text style={[styles.title, styles.titleLayer, titleOff]}>Lights off</Animated.Text>
            <Animated.Text style={[styles.title, styles.titleLayer, titleOn]}>Lights on</Animated.Text>
          </View>
          <Animated.Text style={[styles.hint, hintStyle]}>Pull the cord · Swing the lamp</Animated.Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#05060a',
    overflow: 'hidden',
  },
  caption: {
    position: 'absolute',
    left: 28,
    right: 28,
    gap: 4,
  },
  overline: {
    fontFamily: FAMILY,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 2,
    color: 'rgba(255, 240, 220, 0.6)',
  },
  titleRow: {
    height: 44,
  },
  titleLayer: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  title: {
    fontFamily: FAMILY,
    fontSize: 34,
    fontWeight: '600',
    letterSpacing: -0.5,
    color: '#fff3e2',
  },
  hint: {
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
    color: '#f3e6d4',
  },
});
