import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import {
  AccessibilityInfo,
  type AccessibilityActionEvent,
  type LayoutChangeEvent,
  PixelRatio,
  Pressable,
  type StyleProp,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { makeMutable, type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { SvgXml } from 'react-native-svg';
import { Blur, Canvas, Circle, Group, Line, LinearGradient, Oval, RadialGradient, Rect, Shader, Vertices } from 'react-native-tgfx';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { CLOSE_ICON, TAB_ICONS } from './wfp-icons';
import {
  bandUV,
  unbandUV,
  breathAt,
  clamp01,
  eSettle,
  eOut,
  type Frame,
  frameAt,
  hzAt,
  LOOP,
  mix,
  RANGE_X,
  RIP_LIFE,
  RANGE_Y,
  scatterOf,
  smooth,
  uvToPad,
} from './wfp-motion';
import { createWaveMesh, updateWaveMesh, WAVE_GAP } from './wfp-mesh';
import { PAD_FX, PAGE_FX } from './wfp-shaders';
import { C, FONT, G, SPRING } from './wfp-theme';

const PAD = G.pad;
const R = G.puckR;
const BLEED = G.padBleed;
const TABS = [
  { label: 'Tune mix', w: 134 },
  { label: 'Sounds', w: 112 },
  { label: 'Timers', w: 112 },
] as const;
const TABS_W = TABS.reduce((a, t) => a + t.w, 0) + G.tabs.gap * (TABS.length - 1);
const tabX = (i: number) => {
  let x = (402 - TABS_W) / 2;
  for (let k = 0; k < i; k++) x += TABS[k].w + G.tabs.gap;
  return x;
};
/** Settle haptics: 7 selection ticks whose spacing shrinks from 90 to 40 ms, placed on the 0.9 s settle. */
const TICKS: number[] = (() => {
  const out: number[] = [];
  let c = 0;
  for (let i = 0; i < 7; i++) {
    c += 0.09 - (0.05 * i) / 6;
    out.push(c / 0.9);
  }
  return out;
})();

type Props = {
  /** Plays the 9 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
  onClose?: () => void;
};

type Rip = [number, number, number, number];
const PAD_ACTIONS = [
  { name: 'increment', label: 'Mix more waves' },
  { name: 'decrement', label: 'Mix more sounds' },
  { name: 'increaseIntensity', label: 'Increase intensity' },
  { name: 'decreaseIntensity', label: 'Decrease intensity' },
  { name: 'reset', label: 'Recenter mix' },
];

function PadTouchSurface({
  frame, style, onAction, children,
}: {
  frame: Pick<SharedValue<Frame>, 'get'>;
  style: StyleProp<ViewStyle>;
  onAction: (event: AccessibilityActionEvent) => void;
  children: ReactNode;
}) {
  const [description, setDescription] = useState('');
  useAnimatedReaction(
    () => {
      const { u, v, hz } = frame.get();
      const waves = Math.round(clamp01(u) * 10) * 10;
      const intensity = Math.round(clamp01(v) * 10) * 10;
      return `${waves}% waves, ${intensity}% intensity, ${Math.round(hz)} hertz`;
    },
    (next, previous) => {
      if (next !== previous) scheduleOnRN(setDescription, next);
    },
  );
  return (
    <View
      accessible
      collapsable={false}
      accessibilityRole="adjustable"
      accessibilityLabel="Mix and intensity pad"
      accessibilityHint="Adjust the mix, or choose an action to adjust intensity or recenter."
      accessibilityValue={{ text: description }}
      accessibilityActions={PAD_ACTIONS}
      onAccessibilityAction={onAction}
      style={style}
    >
      {children}
    </View>
  );
}

type ShaderValues = Record<string, number | number[]>;
type ShaderSlots = Record<string, SharedValue<number> | SharedValue<number>[]>;

function makeShaderSlots(values: ShaderValues): ShaderSlots {
  return Object.fromEntries(
    Object.entries(values).map(([name, value]) => [
      name, Array.isArray(value) ? value.map(component => makeMutable(component)) : makeMutable(value),
    ]),
  );
}

function writeShaderSlots(slots: ShaderSlots, values: ShaderValues) {
  'worklet';
  for (const name in values) {
    const slot = slots[name];
    const value = values[name];
    if (Array.isArray(slot) && Array.isArray(value)) {
      for (let i = 0; i < slot.length; i++) slot[i].set(value[i]);
    } else if (!Array.isArray(slot) && typeof value === 'number') {
      slot.set(value);
    }
  }
}

function padValues(f: Frame, pixelSize: number, reduced: boolean): ShaderValues {
  'worklet';
  const puck = uvToPad(f.u, f.v);
  const dent = uvToPad(f.du, f.dv);
  const n = 1 + 3 * clamp01(f.u);
  return {
    uPad: [PAD.x, PAD.y, PAD.w, PAD.h],
    uRad: PAD.r,
    uPx: pixelSize,
    uPuck: [puck.x, puck.y],
    uPuckR: R * f.scale,
    uDent: [dent.x, dent.y],
    uDepth: f.depth,
    uRip0: f.rip0,
    uRip1: f.rip1,
    uSettle: f.settle,
    uMode: [n, n + 1 + 2 * clamp01(f.v)],
    uRing: f.ring,
    uWall: [f.wallX, f.wallY],
    uScatter: [f.scatterX, f.scatterY],
    uVel: [f.velX, f.velY],
    uJit: f.jit,
    uCells: G.grid,
    uSmallR: G.smallArc,
    uLight: 0.85 + 0.15 * clamp01((f.halo - 0.38) / 0.17),
    uAdv: reduced ? 0 : 26,
  };
}

function pageValues(f: Frame): ShaderValues {
  'worklet';
  const dent = uvToPad(f.du, f.dv);
  return {
    uLift: [201, G.wave.y],
    uAmb: [mix(-20, 422, smooth(0.42, 0.8, f.du)), PAD.y + dent.y],
    uAmbA: 1,
    uPadTop: PAD.y,
    uPadBottom: PAD.y + PAD.h,
  };
}


/**
 * The "tune tonight's mix" sheet of a sleep-sound app: one ember puck on an engraved pad sets the mix (Sounds ↔
 * Waves) and the intensity (Easy ↔ Intense) at once. The pad answers like a physical plate: it dents under the
 * puck and arrives a beat late, rings when the puck is thrown into a wall, and when the puck is held still its
 * sand contracts onto the Chladni figure of that exact setting while the wave above locks into a standing wave.
 */
export function WaveFieldPad({ autoPlay = true, onClose }: Props) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [screenReader, setScreenReader] = useState(false);
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [tab, setTab] = useState(0);
  const [pressed, setPressed] = useState(-1);
  const [fontsOk] = useFonts({
    [FONT.regular]: require('./assets/fonts/Outfit-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Outfit-Medium.ttf'),
    [FONT.tabular]: require('./assets/fonts/OutfitTabular-Medium.ttf'),
  });

  const width = size?.w ?? 402;
  const height = size?.h ?? 874;
  const closeTop = G.close.y - G.close.hit / 2;
  const tabsEnd = G.tabs.y + G.tabs.h;
  const usableBottom = height - insets.bottom - 12;
  const S = Math.max(0.01, Math.min(width / 402, usableBottom / tabsEnd, (usableBottom - insets.top) / (tabsEnd - closeTop)));
  const topInset = Math.max(0, insets.top - closeTop * S);
  const W = 402 * S;
  const H = height - topInset;
  const HC = H / S;
  const px = 1 / (S * PixelRatio.get());

  // ---- clocks ----
  const T = useSharedValue(0);
  const canAutoplay = autoPlay && !reduced && !screenReader;
  const auto = useSharedValue(canAutoplay ? 1 : 0);
  const autoplayAllowed = useSharedValue(canAutoplay ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(-10);
  const beatOff = useSharedValue(0);
  const rm = useSharedValue(reduced ? 1 : 0);

  // ---- live state (a finger, or the physics after it) ----
  // mode: 0 resting, 1 under the finger, 2 thrown (decay + rubber-band walls), 3 going home (double tap / idle)
  const mode = useSharedValue(0);
  const returning = useSharedValue(0);
  const lu = useSharedValue(0.5);
  const lv = useSharedValue(0.5);
  const vu = useSharedValue(0);
  const vv = useSharedValue(0);
  const du = useSharedValue(0.5);
  const dv = useSharedValue(0.5);
  const dvu = useSharedValue(0);
  const dvv = useSharedValue(0);
  const fu = useSharedValue(0.5);
  const fv = useSharedValue(0.5);
  const offU = useSharedValue(0);
  const offV = useSharedValue(0);
  const press = useSharedValue(0);
  const pressTo = useSharedValue(0);
  const settleLin = useSharedValue(0);
  const stillFor = useSharedValue(0);
  const anchorX = useSharedValue(0);
  const anchorY = useSharedValue(0);
  const formed = useSharedValue(0);
  const ticks = useSharedValue(0);
  const ringL = useSharedValue(0);
  const lockL = useSharedValue(0);
  const flashL = useSharedValue(-1);
  const blip = useSharedValue(-1);
  const beatL = useSharedValue(0);
  const rip0 = useSharedValue<Rip>([0, 0, 9, 0]);
  const rip1 = useSharedValue<Rip>([0, 0, 9, 0]);
  const ripNext = useSharedValue(0);
  const wallIn = useSharedValue(1);
  const prevU = useSharedValue(0.5);
  const prevV = useSharedValue(0.5);
  const prevHz = useSharedValue(1);
  const velX = useSharedValue(0);
  const velY = useSharedValue(0);
  // dev only: a touch command [kind 1 down / 2 move / 3 up / 4 double tap, a, b, sequence] run on the UI thread
  const devCmd = useSharedValue<[number, number, number, number]>([0, 0, 0, 0]);
  const devSeen = useSharedValue(0);

  useEffect(() => {
    let live = true;
    let receivedChange = false;
    const subscription = AccessibilityInfo.addEventListener('screenReaderChanged', enabled => {
      receivedChange = true;
      if (live) setScreenReader(enabled);
    });
    void AccessibilityInfo.isScreenReaderEnabled().then(enabled => {
      if (live && !receivedChange) setScreenReader(enabled);
    }).catch(() => {});
    return () => {
      live = false;
      subscription.remove();
    };
  }, []);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __wfp?: unknown };
    g.__wfp = {
      at: (t: number) => {
        if (autoplayAllowed.get() === 0) return;
        pinned.set(1);
        auto.set(1);
        beatOff.set(0);
        T.set(t);
      },
      play: () => {
        if (autoplayAllowed.get() === 0) return;
        pinned.set(0);
        auto.set(1);
      },
      rate: (r: number) => {
        rate.set(r);
      },
      // drive the touch handlers without touch injection (pad-local screen points; velocities in pt/s)
      touch: (kind: 'down' | 'move' | 'up' | 'double', a = 0, b = 0) => {
        devCmd.set([kind === 'down' ? 1 : kind === 'move' ? 2 : kind === 'up' ? 3 : 4, a, b, devCmd.get()[3] + 1]);
      },
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), mode: mode.get(), u: lu.get(), v: lv.get(), du: du.get(), dv: dv.get(), settle: settleLin.get(), touching: touching.get(), ticks: ticks.get() }),
    };
    return () => {
      delete g.__wfp;
    };
  }, [T, auto, autoplayAllowed, pinned, rate, beatOff, mode, lu, lv, du, dv, settleLin, touching, ticks, devCmd]);

  const buzz = (k: 'soft' | 'light' | 'medium' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : k === 'light' ? Haptics.ImpactFeedbackStyle.Light : k === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };

  const addRipple = (x: number, y: number, amp: number) => {
    'worklet';
    if (rm.get() === 1 || amp < 0.3) return;
    const r: Rip = [x, y, 0, amp];
    if (ripNext.get() === 0) rip0.set(r);
    else rip1.set(r);
    ripNext.set(1 - ripNext.get());
  };
  const ripAmp = (r: Rip) => {
    'worklet';
    return r[2] < RIP_LIFE ? r[3] * (1 - eOut(r[2] / RIP_LIFE)) : 0;
  };

  /** Spring a raw coordinate back inside 0..1 (weighty), or let it glide with the decay (0.994 per ms). */
  const throwAxis = (x: number, v: number, h: number) => {
    'worklet';
    let a = 0;
    if (x > 1) a = -SPRING.weighty.k * (x - 1) - SPRING.weighty.d * v;
    else if (x < 0) a = -SPRING.weighty.k * x - SPRING.weighty.d * v;
    let nv = v + a * h;
    if (x >= 0 && x <= 1) nv *= Math.pow(0.994, h * 1000);
    return { x: x + nv * h, v: nv };
  };

  /** One step of the live pad (UI thread). */
  const step = (dt: number) => {
    'worklet';
    const m = mode.get();
    let u = lu.get();
    let v = lv.get();
    if (rm.get() === 1 && m !== 1) {
      if (m === 3) { u = 0.5; v = 0.5; }
      u = clamp01(u);
      v = clamp01(v);
      vu.set(0);
      vv.set(0);
      mode.set(0);
    } else if (m === 1) {
      // a touch off the puck glides it to the finger (≤ 180 ms), then it tracks 1:1
      const k = rm.get() === 1 ? 1 : 1 - Math.exp(-dt / 0.045);
      offU.set(offU.get() * (1 - k));
      offV.set(offV.get() * (1 - k));
      const nu = fu.get() - offU.get();
      const nv = fv.get() - offV.get();
      vu.set(mix(vu.get(), (nu - u) / Math.max(dt, 0.001), 0.5));
      vv.set(mix(vv.get(), (nv - v) / Math.max(dt, 0.001), 0.5));
      u = nu;
      v = nv;
    } else if (m === 2) {
      let a = vu.get();
      let b = vv.get();
      for (let i = 0; i < 4; i++) {
        const sx = throwAxis(u, a, dt / 4);
        const sy = throwAxis(v, b, dt / 4);
        u = sx.x;
        a = sx.v;
        v = sy.x;
        b = sy.v;
      }
      vu.set(a);
      vv.set(b);
      const inside = u >= 0 && u <= 1 && v >= 0 && v <= 1;
      if (inside && Math.abs(a) * RANGE_X < 8 && Math.abs(b) * RANGE_Y < 8) mode.set(0);
    } else if (m === 3) {
      // home with a pop
      let a = vu.get();
      let b = vv.get();
      const h = dt / 4;
      for (let i = 0; i < 4; i++) {
        a += (-SPRING.pop.k * (u - 0.5) - SPRING.pop.d * a) * h;
        b += (-SPRING.pop.k * (v - 0.5) - SPRING.pop.d * b) * h;
        u += a * h;
        v += b * h;
      }
      vu.set(a);
      vv.set(b);
      if (Math.abs(u - 0.5) < 0.0015 && Math.abs(v - 0.5) < 0.0015 && Math.abs(a) < 0.01 && Math.abs(b) < 0.01) {
        u = 0.5;
        v = 0.5;
        vu.set(0);
        vv.set(0);
        mode.set(0);
      }
    }
    lu.set(u);
    lv.set(v);

    // wall hit: one rigid tap and a ring from the wall
    const out = u < 0 || u > 1 || v < 0 || v > 1;
    if (out && wallIn.get() === 1 && mode.get() !== 1) {
      const sp = Math.hypot(vu.get() * RANGE_X, vv.get() * RANGE_Y);
      const p = uvToPad(clamp01(u), clamp01(v));
      const wx = u > 1 ? PAD.w : u < 0 ? 0 : p.x;
      const wy = v < 0 ? PAD.h : v > 1 ? 0 : p.y;
      addRipple(wx, wy, Math.min(10, 3 + sp / 200));
      scheduleOnRN(buzz, 'rigid');
    }
    wallIn.set(out ? 0 : 1);

    // the sheet follows through its spring: ~100 ms behind, a few pt past on a stop
    const shown = { u: bandUV(u, RANGE_X), v: bandUV(v, RANGE_Y) };
    const n = 4;
    let a = dvu.get();
    let b = dvv.get();
    let x = du.get();
    let y = dv.get();
    for (let i = 0; i < n; i++) {
      a += (-SPRING.sheet.k * (x - shown.u) - SPRING.sheet.d * a) * (dt / n);
      b += (-SPRING.sheet.k * (y - shown.v) - SPRING.sheet.d * b) * (dt / n);
      x += a * (dt / n);
      y += b * (dt / n);
    }
    dvu.set(a);
    dvv.set(b);
    du.set(x);
    dv.set(y);
    if (rm.get() === 1) {
      du.set(shown.u);
      dv.set(shown.v);
      dvu.set(0);
      dvv.set(0);
    }
    press.set(rm.get() === 1 ? 0 : mix(press.get(), pressTo.get(), 1 - Math.exp(-dt / 0.03)));

    // detents: the centre lines, and the band boundary (rounded Hz 1 → 2)
    const hz = hzAt(shown.u, shown.v);
    if (mode.get() === 1 || mode.get() === 2) {
      if ((prevU.get() - 0.5) * (shown.u - 0.5) < 0 || (prevV.get() - 0.5) * (shown.v - 0.5) < 0) scheduleOnRN(buzz, 'sel');
      if ((prevHz.get() - 1.5) * (hz - 1.5) < 0) scheduleOnRN(buzz, 'light');
    }
    const ddt = Math.max(dt, 0.001);
    velX.set(mix(velX.get(), ((shown.u - prevU.get()) * RANGE_X) / ddt, 0.35));
    velY.set(mix(velY.get(), (-(shown.v - prevV.get()) * RANGE_Y) / ddt, 0.35));
    // the plate sings after 250 ms of stillness under 12 pt/s; moving 6 pt scatters it
    const ptx = shown.u * RANGE_X;
    const pty = shown.v * RANGE_Y;
    const speed = (Math.hypot((shown.u - prevU.get()) * RANGE_X, (shown.v - prevV.get()) * RANGE_Y)) / ddt;
    prevU.set(shown.u);
    prevV.set(shown.v);
    prevHz.set(hz);
    const home = returning.get() === 1;
    stillFor.set(speed < 12 ? stillFor.get() + dt : 0);
    const s0 = settleLin.get();
    if (!home && stillFor.get() >= 0.25) {
      anchorX.set(ptx);
      anchorY.set(pty);
      settleLin.set(Math.min(1, s0 + dt / (rm.get() === 1 ? 0.2 : 0.9)));
    } else if (home || Math.hypot(ptx - anchorX.get(), pty - anchorY.get()) > 6) {
      settleLin.set(Math.max(0, s0 - dt / (rm.get() === 1 ? 0.2 : 0.3)));
      // moving breaks a forming figure with one light tap
      if (!home && ticks.get() > 0 && s0 > 0 && settleLin.get() < s0) {
        ticks.set(0);
        scheduleOnRN(buzz, 'light');
      }
    }
    const s1 = settleLin.get();
    // settle haptics: seven ticks, accelerating, then a soft tap when the figure is formed
    if (s1 > s0 && ticks.get() < TICKS.length && s1 >= TICKS[ticks.get()]) {
      ticks.set(ticks.get() + 1);
      scheduleOnRN(buzz, 'sel');
    }
    if (s1 >= 1 && formed.get() === 0) {
      formed.set(1);
      if (rm.get() === 0) {
        ringL.set(0.0001);
        flashL.set(0);
      }
      scheduleOnRN(buzz, 'soft');
    } else if (s1 < 0.5) {
      formed.set(0);
      if (s1 === 0 && ticks.get() > 0 && s0 === 0) ticks.set(0);
    }
    if (ringL.get() > 0) ringL.set(ringL.get() + dt / 0.5 >= 1 ? 0 : ringL.get() + dt / 0.5);
    if (flashL.get() >= 0) flashL.set(flashL.get() + dt > 0.5 ? -1 : flashL.get() + dt);
    lockL.set(formed.get() === 1 ? Math.min(1, lockL.get() + dt / 0.08) : Math.max(0, lockL.get() - dt / 0.15));

    // clocks
    beatL.set(beatL.get() + hz * dt);
    const r0 = rip0.get();
    if (r0[2] < 9) rip0.set([r0[0], r0[1], r0[2] + dt, r0[3]]);
    const r1 = rip1.get();
    if (r1[2] < 9) rip1.set([r1[0], r1[1], r1[2] + dt, r1[3]]);
    if (blip.get() >= 0) blip.set(blip.get() + dt > 0.36 ? -1 : blip.get() + dt);
  };

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    if (auto.get() === 1) {
      const f = frameAt(T.get());
      return { ...f, beat: f.beat + beatOff.get() };
    }
    const u = bandUV(lu.get(), RANGE_X);
    const v = bandUV(lv.get(), RANGE_Y);
    const s = settleLin.get();
    const settle = rm.get() === 1 ? s : eSettle(s);
    const b = blip.get();
    const hum = rm.get() === 1 ? 0 : 0.012 * settle * Math.sin(2 * Math.PI * 3 * now.get());
    const r0 = rip0.get();
    const r1 = rip1.get();
    const overX = Math.max(0, u - 1) * RANGE_X - Math.max(0, -u) * RANGE_X;
    const overY = Math.max(0, -v) * RANGE_Y - Math.max(0, v - 1) * RANGE_Y;
    const sc = rm.get() === 1 ? { x: 0, y: 0 } : scatterOf(u, v);
    const fl = flashL.get();
    return {
      u,
      v,
      du: du.get(),
      dv: dv.get(),
      depth: 16 + 6 * press.get(),
      scale: rm.get() === 1 ? 1 : 1 - 0.06 * press.get() + (b >= 0 ? 0.08 * Math.sin((Math.PI * b) / 0.36) : 0) + hum,
      halo: 0.38 + 0.17 * press.get() + 0.07 * settle + (rm.get() === 1 ? 0 : breathAt(now.get()) * (1 - press.get())),
      settle,
      ring: ringL.get(),
      lock: lockL.get(),
      flash: fl >= 0 ? Math.sin((Math.PI * fl) / 0.5) : 0,
      rip0: [r0[0], r0[1], r0[2], ripAmp(r0)],
      rip1: [r1[0], r1[1], r1[2], ripAmp(r1)],
      wallX: overX * 0.8,
      wallY: overY * 0.8,
      beat: beatL.get(),
      hz: hzAt(u, v),
      scatterX: sc.x,
      scatterY: sc.y,
      velX: velX.get(),
      velY: velY.get(),
      jit: rm.get() === 1 ? 0 : Math.floor(now.get() * 30) % Math.round(LOOP * 30),
    };
  });

  /** A ripple taken over from the loop: recover its starting amplitude from the current one. */
  const ripBack = (r: Rip): Rip => {
    'worklet';
    return [r[0], r[1], r[2], r[3] > 0 ? Math.min(10, r[3] / Math.max(1 - eOut(Math.min(r[2] / RIP_LIFE, 0.999)), 0.05)) : 0];
  };

  // Capture only while autoplay owns the frame. Policy changes must not steal a live gesture/action.
  const captureAutoplay = () => {
    'worklet';
    if (auto.get() === 1) {
      const f = F.get();
      const f2 = frameAt(T.get() - 1 / 60);
      lu.set(unbandUV(f.u, RANGE_X));
      lv.set(unbandUV(f.v, RANGE_Y));
      du.set(f.du);
      dv.set(f.dv);
      dvu.set((f.du - f2.du) * 60);
      dvv.set((f.dv - f2.dv) * 60);
      // frameAt stores eased settlement; the live integrator stores linear progress.
      let lo = 0;
      let hi = 1;
      for (let i = 0; i < 20; i++) {
        const mid = (lo + hi) / 2;
        if (eSettle(mid) < f.settle) lo = mid;
        else hi = mid;
      }
      settleLin.set(rm.get() === 1 ? f.settle : (lo + hi) / 2);
      stillFor.set(f.settle > 0 ? 0.25 : 0);
      anchorX.set(f.u * RANGE_X);
      anchorY.set(f.v * RANGE_Y);
      formed.set(f.settle > 0.99 ? 1 : 0);
      ticks.set(f.settle > 0 ? TICKS.length : 0);
      lockL.set(f.lock);
      beatL.set(f.beat);
      ringL.set(f.ring);
      rip0.set(ripBack(f.rip0));
      rip1.set(ripBack(f.rip1));
      prevU.set(f.u);
      prevV.set(f.v);
      prevHz.set(f.hz);
      press.set(clamp01((f.depth - 16) / 6));
      pressTo.set(0);
      velX.set(f.velX);
      velY.set(f.velY);
      flashL.set(f.ring > 0 ? f.ring * 0.5 : -1);
      auto.set(0);
    }
  };

  useEffect(() => {
    scheduleOnUI(() => {
      'worklet';
      autoplayAllowed.set(canAutoplay ? 1 : 0);
      if (!canAutoplay) {
        captureAutoplay();
        // A dev-pinned frame must also resume the live integrator after handoff.
        pinned.set(0);
      }
      if (reduced && rm.get() === 0) {
        // Settlement changes representation when Reduced Motion bypasses easing.
        settleLin.set(eSettle(settleLin.get()));
      } else if (!reduced && rm.get() === 1) {
        let lo = 0;
        let hi = 1;
        for (let i = 0; i < 20; i++) {
          const mid = (lo + hi) / 2;
          if (eSettle(mid) < settleLin.get()) lo = mid;
          else hi = mid;
        }
        settleLin.set((lo + hi) / 2);
      }
      rm.set(reduced ? 1 : 0);
      if (reduced) {
        rip0.set([0, 0, 9, 0]);
        rip1.set([0, 0, 9, 0]);
        ringL.set(0);
        flashL.set(-1);
        blip.set(-1);
      }
    });
    // Shared values are stable; reschedule only when the policy changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canAutoplay, reduced]);

  // ---- the takeover: a touch keeps whatever the loop shows ----
  const takeOver = () => {
    'worklet';
    captureAutoplay();
    pinned.set(0);
    returning.set(0);
    touching.set(1);
    lastTouch.set(now.get());
  };

  const toUV = (x: number, y: number) => {
    'worklet';
    return { u: (x / S - R) / RANGE_X, v: 1 - (y / S - R) / RANGE_Y };
  };

  // touch handlers (screen points relative to the pad view; velocities in screen points per second)
  const touchDown = (x: number, y: number) => {
    'worklet';
    takeOver();
    const q = toUV(x, y);
    fu.set(q.u);
    fv.set(q.v);
    offU.set(q.u - lu.get());
    offV.set(q.v - lv.get());
    vu.set(0);
    vv.set(0);
    mode.set(1);
    pressTo.set(1);
    // a touch anywhere rings the sheet from the finger
    addRipple(x / S, y / S, 8);
    scheduleOnRN(buzz, 'soft');
  };
  const touchMove = (x: number, y: number) => {
    'worklet';
    const q = toUV(x, y);
    fu.set(q.u);
    fv.set(q.v);
    lastTouch.set(now.get());
  };
  const touchUp = (vx: number, vy: number) => {
    'worklet';
    // thrown with the finger's velocity: decay, rubber-band walls; the sheet rings in proportion
    vu.set(vx / S / RANGE_X);
    vv.set(-vy / S / RANGE_Y);
    mode.set(2);
    const sp = Math.hypot(vx, vy) / S;
    const p = uvToPad(bandUV(lu.get(), RANGE_X), bandUV(lv.get(), RANGE_Y));
    if (sp > 200) addRipple(p.x, p.y, 6 * Math.min(1, sp / 1500));
  };
  const touchFinal = () => {
    'worklet';
    if (mode.get() === 1) {
      vu.set(0);
      vv.set(0);
      mode.set(2);
    }
    pressTo.set(0);
    touching.set(0);
    lastTouch.set(now.get());
  };
  const recentre = () => {
    'worklet';
    takeOver();
    const p = uvToPad(bandUV(lu.get(), RANGE_X), bandUV(lv.get(), RANGE_Y));
    const away = Math.abs(lu.get() - 0.5) > 0.01 || Math.abs(lv.get() - 0.5) > 0.01;
    if (away) addRipple(p.x, p.y, 8);
    vu.set(0);
    vv.set(0);
    mode.set(3);
    blip.set(0);
    scheduleOnRN(buzz, 'medium');
    touching.set(0);
    lastTouch.set(now.get());
  };

  const adjustPad = (mixDelta: number, intensityDelta: number) => {
    'worklet';
    takeOver();
    lu.set(clamp01(lu.get() + mixDelta));
    lv.set(clamp01(lv.get() + intensityDelta));
    vu.set(0);
    vv.set(0);
    mode.set(0);
    touchFinal();
    scheduleOnRN(buzz, 'sel');
  };
  const onPadAction = (event: AccessibilityActionEvent) => {
    switch (event.nativeEvent.actionName) {
      case 'increment': scheduleOnUI(adjustPad, 0.1, 0); break;
      case 'decrement': scheduleOnUI(adjustPad, -0.1, 0); break;
      case 'increaseIntensity': scheduleOnUI(adjustPad, 0, 0.1); break;
      case 'decreaseIntensity': scheduleOnUI(adjustPad, 0, -0.1); break;
      case 'reset': scheduleOnUI(recentre); break;
    }
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      touchDown(e.x, e.y);
    })
    .onUpdate((e) => {
      touchMove(e.x, e.y);
    })
    .onEnd((e, ok) => {
      if (ok) touchUp(e.velocityX, e.velocityY);
    })
    .onFinalize(() => {
      touchFinal();
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .onEnd((_e, ok) => {
      if (ok) recentre();
    });



  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    const cmd = devCmd.get();
    if (cmd[3] !== devSeen.get()) {
      devSeen.set(cmd[3]);
      if (cmd[0] === 1) touchDown(cmd[1], cmd[2]);
      else if (cmd[0] === 2) touchMove(cmd[1], cmd[2]);
      else if (cmd[0] === 3) {
        touchUp(cmd[1], cmd[2]);
        touchFinal();
      } else if (cmd[0] === 4) recentre();
    }
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    step(dt);
    // 2 s after the last touch: ease home, let the figure go, then restart the loop from its rest frame
    if (autoplayAllowed.get() === 1 && touching.get() === 0 && now.get() - lastTouch.get() > 2) {
      if (mode.get() === 0 && returning.get() === 0 && (Math.abs(lu.get() - 0.5) > 0.0015 || Math.abs(lv.get() - 0.5) > 0.0015)) mode.set(3);
      returning.set(1);
      const calm = mode.get() === 0 && settleLin.get() < 0.002 && ripAmp(rip0.get()) < 0.05 && ripAmp(rip1.get()) < 0.05 && blip.get() < 0 && ringL.get() === 0;
      if (calm && Math.abs(du.get() - 0.5) < 0.002 && Math.abs(dv.get() - 0.5) < 0.002 && press.get() < 0.01) {
        beatOff.set(beatL.get() % 2);
        T.set(0);
        returning.set(0);
        auto.set(1);
      }
    }
  });

  const gesture = Gesture.Simultaneous(pan, doubleTap);

  // ---- geometry ----
  const puckPt = useDerivedValue(() => {
    const f = F.get();
    const p = uvToPad(f.u, f.v);
    return { x: PAD.x + p.x, y: PAD.y + p.y };
  });
  const padU = useMemo(() => makeShaderSlots(padValues(frameAt(0), px, reduced)), [px, reduced]);
  const pageU = useMemo(() => makeShaderSlots(pageValues(frameAt(0))), []);
  const waveMesh = useMemo(() => {
    const mesh = createWaveMesh();
    updateWaveMesh(mesh, frameAt(0), px);
    return mesh;
  }, [px]);

  useAnimatedReaction(
    () => ({ frame: F.get(), reduced: rm.get() === 1 }),
    ({ frame, reduced: preferReducedMotion }) => {
      updateWaveMesh(waveMesh, frame, px);
      writeShaderSlots(padU, padValues(frame, px, preferReducedMotion));
      writeShaderSlots(pageU, pageValues(frame));
    },
  );

  // ---- the ember: a glass dome whose hot core lags 4 pt against its motion ----
  const pcx = useDerivedValue(() => puckPt.get().x);
  const pcy = useDerivedValue(() => puckPt.get().y);
  const pr = useDerivedValue(() => R * F.get().scale);
  const shY = useDerivedValue(() => puckPt.get().y + 3);
  const haloA = useDerivedValue(() => F.get().halo / 0.55);
  const coreC = useDerivedValue(() => {
    const f = F.get();
    const p = uvToPad(f.u, f.v);
    const d = uvToPad(f.du, f.dv);
    const lx = d.x - p.x;
    const ly = d.y - p.y;
    const l = Math.hypot(lx, ly);
    const k = l > 0.001 ? Math.min(l, 4) / l : 0;
    return { x: puckPt.get().x - 0.18 * pr.get() + lx * k, y: puckPt.get().y - 0.2 * pr.get() + ly * k };
  });
  const bodyR = useDerivedValue(() => pr.get() * 1.2);
  const coreX = useDerivedValue(() => coreC.get().x);
  const coreY = useDerivedValue(() => coreC.get().y);
  const rimX0 = useDerivedValue(() => pcx.get() - pr.get());
  const rimY0 = useDerivedValue(() => pcy.get() - pr.get());
  const rimX1 = useDerivedValue(() => pcx.get() + pr.get() * 0.4);
  const rimY1 = useDerivedValue(() => pcy.get() + pr.get() * 0.4);
  const specX = useDerivedValue(() => pcx.get() - 0.4 * pr.get());
  const specY = useDerivedValue(() => pcy.get() - 0.52 * pr.get());
  const left = useDerivedValue(() => pcx.get() - 8 * F.get().scale);
  const right = useDerivedValue(() => pcx.get() + 8 * F.get().scale);
  const top = useDerivedValue(() => pcy.get() - 8 * F.get().scale);
  const bottom = useDerivedValue(() => pcy.get() + 8 * F.get().scale);

  const loops = useDerivedValue(() => Math.round(1 + 3 * clamp01(F.get().u)));
  // the standing wave's nodes flash as it locks
  const nodeX = (j: number) => () => {
    'worklet';
    return G.wave.x0 + (G.wave.x1 - G.wave.x0) * (j / Math.max(loops.get(), 1));
  };
  const nodeA = (j: number) => () => {
    'worklet';
    const x = j / Math.max(loops.get(), 1);
    return j < loops.get() && Math.abs(x - 0.5) > WAVE_GAP + 0.03 ? F.get().flash * F.get().lock : 0;
  };
  const n1x = useDerivedValue(nodeX(1));
  const n2x = useDerivedValue(nodeX(2));
  const n3x = useDerivedValue(nodeX(3));
  const n1a = useDerivedValue(nodeA(1));
  const n2a = useDerivedValue(nodeA(2));
  const n3a = useDerivedValue(nodeA(3));
  const nodes = [
    { x: n1x, a: n1a },
    { x: n2x, a: n2a },
    { x: n3x, a: n3a },
  ];

  // ---- read-out: masked rolls ----
  const hzShown = useDerivedValue(() => {
    const h = Math.min(Math.max(F.get().hz, 1), 4);
    const b = Math.floor(h);
    // the digit rolls while the rounded value changes (0.3..0.7 of the step), so it reads as detents
    return Math.min(b + smooth(0.3, 0.7, h - b), 4);
  });
  const digitSt = useAnimatedStyle(() => ({ transform: [{ translateY: -(hzShown.get() - 1) * G.hz.line * S }] }));
  const bandK = useDerivedValue(() => smooth(1.42, 1.58, F.get().hz));
  const bandSt = useAnimatedStyle(() => ({ transform: [{ translateY: -bandK.get() * G.band.line * S }] }));

  // the pad's labels are printed on the plate: the ember covers one it passes over (RN text sits above the canvas)
  const lblT = useAnimatedStyle(() => {
    const p = puckPt.get();
    return { opacity: smooth(R - 6, R + 22, Math.hypot(p.x - PAD.x - PAD.w / 2, p.y - PAD.y - G.label.inset)) };
  });
  const lblB = useAnimatedStyle(() => {
    const p = puckPt.get();
    return { opacity: smooth(R - 6, R + 22, Math.hypot(p.x - PAD.x - PAD.w / 2, p.y - PAD.y - PAD.h + G.label.inset)) };
  });
  const lblL = useAnimatedStyle(() => {
    const p = puckPt.get();
    return { opacity: smooth(R - 6, R + 22, Math.hypot(p.x - PAD.x - G.label.inset, p.y - PAD.y - PAD.h / 2)) };
  });
  const lblR = useAnimatedStyle(() => {
    const p = puckPt.get();
    return { opacity: smooth(R - 6, R + 22, Math.hypot(p.x - PAD.x - PAD.w + G.label.inset, p.y - PAD.y - PAD.h / 2)) };
  });

  const pickTab = (i: number) => {
    setTab(i);
    buzz('sel');
  };

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const font = (f: string) => (fontsOk ? { fontFamily: f } : null);

  return (
    <View style={styles.root} onLayout={onLayout}>
      <StatusBar style="light" />
      <View style={{ alignSelf: 'center', width: W, height: H, marginTop: topInset }}>
      {size && (
        <Canvas style={[StyleSheet.absoluteFill, { width: W, height: H, pointerEvents: 'none' }]} opaque>
          <Group transform={[{ scale: S }]}>
            <Rect x={0} y={0} width={402} height={HC}>
              <Shader source={PAGE_FX} uniforms={pageU} />
            </Rect>
            <Vertices buffer={waveMesh} mode="triangles" color="#FFFFFF" />
            {nodes.map((nd, i) => (
              <Group key={i} opacity={nd.a}>
                <Circle cx={nd.x} cy={G.wave.y} r={9} color="rgba(255,201,120,0.45)">
                  <Blur blur={5} />
                </Circle>
                <Circle cx={nd.x} cy={G.wave.y} r={3.5} color="#FFE7BD" />
              </Group>
            ))}
            <Rect x={PAD.x - BLEED} y={PAD.y - BLEED} width={PAD.w + 2 * BLEED} height={PAD.h + 2 * BLEED}>
              <Shader source={PAD_FX} uniforms={padU} />
            </Rect>
            {/* the ember: halo on the plate, contact shadow, dome, rim, specular crescent, plus */}
            <Group opacity={haloA}>
              <Circle cx={pcx} cy={pcy} r={100}>
                <RadialGradient center={{ x: pcx, y: pcy }} r={100} colors={['rgba(240,128,30,0.55)', 'rgba(240,128,30,0.55)', 'rgba(240,128,30,0.18)', 'rgba(240,128,30,0)']} positions={[0, R / 100, 0.72, 1]} />
              </Circle>
            </Group>
            <Circle cx={pcx} cy={shY} r={pr} color="rgba(0,0,0,0.35)">
              <Blur blur={8} />
            </Circle>
            <Circle cx={pcx} cy={pcy} r={pr}>
              <RadialGradient center={{ x: coreX, y: coreY }} r={bodyR} colors={[C.emberCore, C.emberCore, C.ember, C.emberRim, C.emberDeep]} positions={[0, 0.16, 0.5, 0.84, 1]} />
            </Circle>
            <Circle cx={pcx} cy={pcy} r={pr} style="stroke" strokeWidth={1.5} color="rgba(255,192,122,0.6)" />
            <Circle cx={pcx} cy={pcy} r={pr} style="stroke" strokeWidth={1.5}>
              <LinearGradient start={{ x: rimX0, y: rimY0 }} end={{ x: rimX1, y: rimY1 }} colors={['rgba(255,226,176,0.6)', 'rgba(255,226,176,0)']} />
            </Circle>
            <Group transform={[{ translateX: specX }, { translateY: specY }, { rotate: "-38deg" }]}>
              <Oval x={-10} y={-4} width={20} height={8}>
                <LinearGradient start={{ x: 0, y: -4 }} end={{ x: 0, y: 4 }} colors={['rgba(255,255,255,0.55)', 'rgba(255,255,255,0.08)']} />
                <Blur blur={0.8} />
              </Oval>
            </Group>
            <Line x1={left} y1={pcy} x2={right} y2={pcy} style="stroke" strokeWidth={2} strokeCap="round" color={C.plus} />
            <Line x1={pcx} y1={top} x2={pcx} y2={bottom} style="stroke" strokeWidth={2} strokeCap="round" color={C.plus} />
          </Group>
        </Canvas>
      )}

      {/* close */}
      <Pressable accessibilityRole="button" accessibilityLabel="Close" disabled={!onClose} onPress={() => { buzz('light'); onClose?.(); }} style={[styles.abs, styles.center, { left: (G.close.x - G.close.hit / 2) * S, top: (G.close.y - G.close.hit / 2) * S, width: G.close.hit * S, height: G.close.hit * S }]}>
        {CLOSE_ICON !== '' && <SvgXml xml={CLOSE_ICON} width={24 * S} height={24 * S} color="#E9ECE8" />}
      </Pressable>

      {/* read-out over the wave */}
      <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.abs, styles.row, { left: 0, right: 0, top: (G.hz.y - G.hz.line / 2) * S, height: G.hz.line * S }]}>
        <View style={{ width: 19 * S, height: G.hz.line * S, overflow: 'hidden' }}>
          <Animated.View style={digitSt}>
            {['1', '2', '3', '4'].map((d) => (
              <Text key={d} allowFontScaling={false} style={[styles.hz, font(FONT.tabular), { fontSize: G.hz.size * S, lineHeight: G.hz.line * S, height: G.hz.line * S }]}>
                {d}
              </Text>
            ))}
          </Animated.View>
        </View>
        <Text allowFontScaling={false} style={[styles.hz, font(FONT.medium), { fontSize: G.hz.size * S, lineHeight: G.hz.line * S }]}>
          Hz
        </Text>
      </View>
      <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.abs, { left: 0, right: 0, top: (G.band.y - G.band.line / 2) * S, height: G.band.line * S, overflow: 'hidden' }]}>
        <Animated.View style={bandSt}>
          {['Deep Delta', 'Delta'].map((t) => (
            <Text key={t} allowFontScaling={false} style={[styles.band, font(FONT.regular), { fontSize: G.band.size * S, lineHeight: G.band.line * S, height: G.band.line * S }]}>
              {t}
            </Text>
          ))}
        </Animated.View>
      </View>

      <Text allowFontScaling={false} style={[styles.abs, styles.title, font(FONT.medium), { top: (G.title.y - G.title.line / 2) * S, fontSize: G.title.size * S, lineHeight: G.title.line * S }]}>
        {"Shape tonight's mix"}
      </Text>
      <Text allowFontScaling={false} style={[styles.abs, styles.sub, font(FONT.regular), { top: (G.sub.y - G.sub.line / 2) * S, fontSize: G.sub.size * S, lineHeight: G.sub.line * S }]}>
        Slide the ember to balance sounds and waves.
      </Text>

      {/* the pad's touch surface and its labels */}
      <GestureDetector gesture={gesture}>
        <PadTouchSurface frame={F} onAction={onPadAction} style={[styles.abs, { left: PAD.x * S, top: PAD.y * S, width: PAD.w * S, height: PAD.h * S }]}>
          <Animated.Text allowFontScaling={false} style={[styles.abs, styles.lbl, font(FONT.medium), { left: 0, right: 0, top: (G.label.inset - 9) * S, fontSize: G.label.size * S, lineHeight: 18 * S }, lblT]}>
            Intense
          </Animated.Text>
          <Animated.Text allowFontScaling={false} style={[styles.abs, styles.lbl, font(FONT.medium), { left: 0, right: 0, top: (PAD.h - G.label.inset - 9) * S, fontSize: G.label.size * S, lineHeight: 18 * S }, lblB]}>
            Easy
          </Animated.Text>
          <Animated.View style={[styles.abs, styles.center, { left: (G.label.inset - 40) * S, top: (PAD.h / 2 - 9) * S, width: 80 * S, height: 18 * S, transform: [{ rotate: '-90deg' }] }, lblL]}>
            <Text allowFontScaling={false} style={[styles.lbl, font(FONT.medium), { fontSize: G.label.size * S, lineHeight: 18 * S }]}>
              Sounds
            </Text>
          </Animated.View>
          <Animated.View style={[styles.abs, styles.center, { left: (PAD.w - G.label.inset - 40) * S, top: (PAD.h / 2 - 9) * S, width: 80 * S, height: 18 * S, transform: [{ rotate: '90deg' }] }, lblR]}>
            <Text allowFontScaling={false} style={[styles.lbl, font(FONT.medium), { fontSize: G.label.size * S, lineHeight: 18 * S }]}>
              Waves
            </Text>
          </Animated.View>
        </PadTouchSurface>
      </GestureDetector>

      {/* tabs: the pill moves at once; a press scales the tab */}
      {TABS.map((t, i) => (
        <Pressable
          key={t.label}
          accessibilityRole="tab"
          accessibilityState={{ selected: tab === i }}
          onPressIn={() => setPressed(i)}
          onPressOut={() => setPressed(-1)}
          onPress={() => pickTab(i)}
          style={[
            styles.abs,
            styles.tab,
            {
              left: tabX(i) * S,
              top: G.tabs.y * S,
              width: t.w * S,
              height: G.tabs.h * S,
              borderRadius: (G.tabs.h / 2) * S,
              backgroundColor: tab === i ? C.tabOn : C.tabOff,
              transform: [{ scale: pressed === i ? 0.96 : 1 }],
            },
          ]}
        >
          {TAB_ICONS[i] !== '' && <SvgXml xml={TAB_ICONS[i]} width={18 * S} height={18 * S} color={tab === i ? C.tabOnInk : C.tabOffInk} />}
          <Text allowFontScaling={false} style={[font(FONT.medium), { color: tab === i ? C.tabOnInk : C.tabOffInk, fontSize: 14 * S, marginLeft: TAB_ICONS[i] !== '' ? 7 * S : 0 }]}>
            {t.label}
          </Text>
        </Pressable>
      ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  center: { alignItems: 'center', justifyContent: 'center' },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  hz: { color: C.ink, textAlign: 'center', letterSpacing: -0.3, fontVariant: ['tabular-nums'] },
  band: { color: C.ink2, textAlign: 'center' },
  title: { left: 0, right: 0, color: C.ink, textAlign: 'center', letterSpacing: -0.33 },
  sub: { left: 20, right: 20, color: C.mute, textAlign: 'center' },
  lbl: { color: C.ink, textAlign: 'center', letterSpacing: 0.13, textShadowColor: 'rgba(14,12,16,0.85)', textShadowRadius: 6, textShadowOffset: { width: 0, height: 0 } },
  tab: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
});
