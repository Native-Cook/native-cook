import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { type SharedValue, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import {
  Blur,
  BlurMask,
  Canvas,
  Glyphs,
  Group,
  ImageShader,
  LinearGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  type SkFont,
  type SkImage,
  useFont,
  vec,
} from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import {
  bandOf,
  clamp,
  curveY,
  FRONT_SPEED,
  type Frame,
  frameAt,
  humpA,
  humpX,
  LOOP,
  mix,
  numeralLayout,
  POSTER_T,
  smooth,
  springAt,
} from './gps-motion';
import { LENS_FX, SCENE_FX } from './gps-shaders';
import { BAND_RGB, C, CAPS, G, gearX, SPRING } from './gps-theme';

/** Skia runtime effects are read as Display P3 on iOS: re-encode the sRGB colours there. */
const P3 = Platform.OS === 'ios' ? 1 : 0;

const FONTS = {
  num: require('./assets/Exo2Num-Light.ttf'),
  letter: require('./assets/Exo2-Regular.ttf'),
  unit: require('./assets/SairaCondensed-Medium.ttf'),
};

const NUM = G.numeral;
const PITCH = 12; // roll travel between two digits (pt at 76): a short slide with a crossfade, never a chopped drum
const ATLAS_Y = 600;
const ATLAS_H = 130;
const ATLAS_SCALE = 4;

type Props = {
  /** Plays the 10 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
};

// ---------------------------------------------------------------- live state (a finger in charge)

type Live = {
  g: number;
  gv: number;
  gT: number;
  drag: number;
  landed: number;
  lastWell: number;
  press: number;
  flashT: number;
  speed: number;
  sv: number;
  thr: number;
  rate: number;
  capHit: number;
  redline: number;
  infl: number;
  inflV: number;
  numK: number;
  numV: number;
  drop: number;
  dropV: number;
  gapT: number;
  lean: number;
  phase: number;
  c0: number;
  c1: number;
  c2: number;
  fpX: number;
  fpT: number;
  flX: number;
  flT: number;
  pkT: number;
  pkFrom: number;
  pkTo: number;
  plX: number;
  plA: number;
  plHold: number;
  plRelT: number;
  plRelA: number;
};

const LIVE_REST: Live = {
  g: 0, gv: 0, gT: 0, drag: 0, landed: 1, lastWell: 0, press: 0, flashT: -9, speed: 0, sv: 0, thr: 0, rate: 0,
  capHit: 0, redline: 0, infl: 0, inflV: 0, numK: 0, numV: 0, drop: 0, dropV: 0, gapT: -9, lean: 0, phase: 0, c0: 0,
  c1: 0, c2: 0, fpX: 56, fpT: -99, flX: 56, flT: -99, pkT: -9, pkFrom: 0, pkTo: 0, plX: 200, plA: 0, plHold: 0,
  plRelT: -9, plRelA: 0,
};

// a plucked line rings at 5 Hz with ζ 0.18
const PLUCK_W = (2 * Math.PI * 5) / Math.sqrt(1 - 0.18 * 0.18);
const PLUCK = { d: 2 * 0.18 * PLUCK_W, k: PLUCK_W * PLUCK_W };

/** One spring step (semi-implicit Euler, a few sub-steps). */
const stepSpring = (x: number, v: number, to: number, d: number, k: number, dt: number): [number, number] => {
  'worklet';
  let xx = x;
  let vv = v;
  const n = 4;
  const h = dt / n;
  for (let i = 0; i < n; i++) {
    vv += (-k * (xx - to) - d * vv) * h;
    xx += vv * h;
  }
  return [xx, vv];
};

const flashAt = (age: number) => {
  'worklet';
  if (age < 0) return 0;
  if (age < 0.03) return age / 0.03;
  return Math.exp(-(age - 0.03) / 0.09);
};

/** Haptic cues returned by a live step: 0 none, 1 detent, 2 landing, 3 band edge, 4 cap, 5 redline train. */
const stepLive = (s: Live, dt: number, now: number, reduced: number): [Live, number] => {
  'worklet';
  const n: Live = { ...s };
  let cue = 0;
  // the ball
  if (!n.drag) {
    if (reduced) {
      n.g = n.gT;
      n.gv = 0;
    } else {
      const r = stepSpring(n.g, n.gv, n.gT, SPRING.detent.d, SPRING.detent.k, dt);
      n.g = r[0];
      n.gv = r[1];
    }
  }
  const well = Math.round(clamp(n.g, 0, 4));
  if (well !== n.lastWell) {
    n.lastWell = well;
    cue = 1;
  }
  if (!n.drag && !n.landed && Math.abs(n.g - n.gT) < 0.02 && Math.abs(n.gv) < 0.4) {
    n.landed = 1;
    n.flashT = now;
    cue = 2;
    if (n.gT >= 2) {
      n.pkT = now;
      n.pkFrom = gearX(n.gT);
      n.pkTo = humpX(n.speed);
    }
  }
  const gi = n.drag ? Math.round(clamp(n.g, 0, 4)) : n.gT;
  const driving = gi >= 2;
  const cap = CAPS[gi];
  n.press = n.drag ? Math.min(n.press + dt / 0.12, 1) : Math.max(n.press - dt / 0.2, 0);

  // speed
  const prev = n.speed;
  if (!n.thr) {
    if (!driving) n.speed = Math.max(n.speed - 60 * dt, 0);
    else if (n.sv !== 0) {
      n.speed = clamp(n.speed + n.sv * dt, 0, cap);
      const dv = 18 * dt;
      n.sv = Math.abs(n.sv) <= dv ? 0 : n.sv - Math.sign(n.sv) * dv;
    } else n.speed = Math.max(n.speed - 12 * dt, 0);
  }
  if (n.speed > cap) n.speed = Math.max(cap, n.speed - 120 * dt);
  if (driving && n.thr && cap > 0 && n.speed >= cap - 0.01 && !n.capHit) {
    n.capHit = 1;
    cue = 4;
  } else if (n.speed < cap - 4) n.capHit = 0;
  if (n.speed >= 185 && !n.redline) {
    n.redline = 1;
    cue = 5;
  } else if (n.speed < 175) n.redline = 0;
  n.rate = dt > 0 ? (n.speed - prev) / dt : 0;
  const leanTo = clamp(n.rate / 70, -1, 1);
  n.lean += (leanTo - n.lean) * (1 - Math.exp(-dt / 0.15));
  n.phase += reduced ? 0 : (6 + 1.2 * n.speed) * dt;

  // the band fronts
  const b = bandOf(n.speed);
  if (b !== n.c2) {
    n.c0 = n.c1;
    n.c1 = n.c2;
    n.c2 = b;
    n.fpX = n.flX;
    n.fpT = n.flT;
    n.flX = humpX(n.speed);
    n.flT = reduced ? now - 100 : now;
    if (n.thr && cue === 0) cue = 3;
  }

  // the hump, the numeral and the torque gap
  const gap = now - n.gapT;
  const inflTo = reduced ? 0 : (driving ? 1 : smooth(0, 30, n.speed)) * (gap >= 0 && gap < 0.25 ? 0 : 1);
  let r2 = stepSpring(n.infl, n.inflV, inflTo, SPRING.pop.d, SPRING.pop.k, dt);
  n.infl = Math.max(r2[0], 0);
  n.inflV = r2[1];
  const numTo = reduced ? 0 : inflTo > 0.5 || n.speed > 50 ? 1 : 0;
  r2 = stepSpring(n.numK, n.numV, numTo, SPRING.glide.d, SPRING.glide.k, dt);
  n.numK = clamp(r2[0], -0.05, 1.05);
  n.numV = r2[1];
  const dropTo = !reduced && gap >= 0 && gap < 0.42 && n.numK > 0.5 ? 1 : 0;
  r2 = stepSpring(n.drop, n.dropV, dropTo, dropTo ? SPRING.snap.d : SPRING.glide.d, dropTo ? SPRING.snap.k * 2 : SPRING.glide.k, dt);
  n.drop = clamp(r2[0], 0, 1);
  n.dropV = r2[1];

  // the pluck rings down after release
  if (!n.plHold) n.plA = reduced ? 0 : springAt(now - n.plRelT, n.plRelA, 0, 0, PLUCK.d, PLUCK.k);
  return [n, cue];
};

const liveFrame = (s: Live, now: number): Frame => {
  'worklet';
  let packetX = 0;
  let packetGain = 0;
  const dur = Math.max(Math.abs(s.pkTo - s.pkFrom) / 900, 0.06);
  const k = (now - s.pkT) / dur;
  if (k >= 0 && k < 1.25) {
    packetX = mix(s.pkFrom, s.pkTo, Math.min(k, 1));
    packetGain = k < 1 ? 1 : 1 - (k - 1) / 0.25;
  }
  const lag = s.plHold ? s.plA * 0.7 : springAt(now - s.plRelT - 0.09, s.plRelA, 0, 0, PLUCK.d, PLUCK.k);
  return {
    g: s.g,
    press: s.press,
    flash: flashAt(now - s.flashT),
    speed: s.speed,
    rate: s.rate,
    A: humpA(s.speed, s.infl),
    x0: humpX(s.speed),
    lean: s.lean,
    jit: 0,
    phase: s.phase,
    curt: s.speed * mix(0.55, 1, Math.min(s.infl, 1)),
    whip: 10 * smooth(180, 190, s.speed),
    sway: 3 * Math.sin((2 * Math.PI * 4 * now) / LOOP),
    cols: [s.c0, s.c1, s.c2],
    fPrev: [s.fpX, FRONT_SPEED * (now - s.fpT)],
    fLast: [s.flX, FRONT_SPEED * (now - s.flT)],
    packetX,
    packetGain,
    pluckX: s.plX,
    pluckA: s.plA,
    pluckLag: lag,
    numK: s.numK,
    drop: s.drop,
    time: now,
  };
};

/** The live state that continues exactly what the loop shows at time t. */
const liveFromAuto = (f: Frame, now: number): Live => {
  'worklet';
  const target = Math.round(clamp(f.g, 0, 4));
  const a0 = G.hump.a0 + G.hump.aPerMph * f.speed;
  return {
    ...LIVE_REST,
    g: f.g,
    gT: target,
    landed: Math.abs(f.g - target) < 0.02 ? 1 : 0,
    lastWell: target,
    flashT: f.flash > 0 ? now - 0.05 : -9,
    speed: f.speed,
    infl: a0 > 0 ? f.A / a0 : 0,
    numK: f.numK,
    drop: f.drop,
    lean: f.lean,
    phase: f.phase,
    c0: f.cols[0],
    c1: f.cols[1],
    c2: f.cols[2],
    fpX: f.fPrev[0],
    fpT: now - f.fPrev[1] / FRONT_SPEED,
    flX: f.fLast[0],
    flT: now - f.fLast[1] / FRONT_SPEED,
  };
};

// ---------------------------------------------------------------- helpers for drawing

const bandRgbAt = (f: Frame, x: number) => {
  'worklet';
  const inside = (fx: number, r: number) => 1 - smooth(r - 8, r + 8, Math.abs(x - fx));
  const a = BAND_RGB[f.cols[0]];
  const b = BAND_RGB[f.cols[1]];
  const c = BAND_RGB[f.cols[2]];
  const k1 = inside(f.fPrev[0], f.fPrev[1]);
  const k2 = inside(f.fLast[0], f.fLast[1]);
  const out = [0, 0, 0];
  for (let i = 0; i < 3; i++) out[i] = mix(mix(a[i], b[i], k1), c[i], k2);
  return out;
};

/** The odometer: units roll near each half, the higher places roll with the lower one's wrap. */
const drums = (v: number, still: number) => {
  'worklet';
  const a = Math.max(Math.abs(v), 0);
  const fl = Math.floor(a);
  const u = still ? Math.round(a) : fl + smooth(0.3, 0.7, a - fl);
  const wrap = (p: number) => {
    const m = p - 10 * Math.floor(p / 10);
    return Math.floor(p / 10) + (m > 9 ? m - 9 : 0);
  };
  const t = wrap(u);
  const h = wrap(t);
  return [h, t, u];
};

/** The unit sits centred under the numeral in the hump, left-aligned under it when parked. */
const unitXAt = (f: Frame, L: { cx: number; scale: number }, unitW: number) => {
  'worklet';
  const parked = clamp((1 - L.scale) / (1 - G.parked.scale), 0, 1) * (1 - f.drop);
  return mix(L.cx - unitW / 2, G.parked.x, parked);
};

type Run = { id: number; pos: { x: number; y: number } }[];
/** One digit place: the digit leaving (cur) and the one arriving (nxt), crossfading over a short roll. */
type Slot = { cur: Run; nxt: Run; roll: number };

// ---------------------------------------------------------------- the numeral (drawn twice: glow and ink)

function Place({
  font,
  slots,
  i,
  color,
  blur,
  glow,
}: {
  font: SkFont;
  slots: SharedValue<Slot[]>;
  i: number;
  color: SharedValue<string> | string;
  blur?: SharedValue<{ x: number; y: number }[]>;
  glow: boolean;
}) {
  const cur = useDerivedValue(() => slots.get()[i].cur);
  const nxt = useDerivedValue(() => slots.get()[i].nxt);
  const aCur = useDerivedValue(() => 1 - slots.get()[i].roll);
  const aNxt = useDerivedValue(() => slots.get()[i].roll);
  const b = useDerivedValue(() => (blur ? blur.get()[i] : { x: 0, y: 0 }));
  return (
    <>
      <Glyphs font={font} glyphs={cur} x={0} y={0} color={color} opacity={aCur}>
        {glow ? <BlurMask blur={8} style="normal" /> : <Blur blur={b} mode="decal" />}
      </Glyphs>
      <Glyphs font={font} glyphs={nxt} x={0} y={0} color={color} opacity={aNxt}>
        {glow ? <BlurMask blur={8} style="normal" /> : <Blur blur={b} mode="decal" />}
      </Glyphs>
    </>
  );
}

/** The speed, drawn twice: a mask-filter halo in the band colour, then the ink. */
function Numeral({
  font,
  slots,
  matrix,
  color,
  blur,
  glow = false,
}: {
  font: SkFont;
  slots: SharedValue<Slot[]>;
  matrix: SharedValue<{ translateX: number }[] | { translateY: number }[] | { scale: number }[]>;
  color: SharedValue<string> | string;
  blur?: SharedValue<{ x: number; y: number }[]>;
  glow?: boolean;
}) {
  return (
    <Group transform={matrix as never}>
      <Place font={font} slots={slots} i={0} color={color} blur={blur} glow={glow} />
      <Place font={font} slots={slots} i={1} color={color} blur={blur} glow={glow} />
      <Place font={font} slots={slots} i={2} color={color} blur={blur} glow={glow} />
    </Group>
  );
}

// ---------------------------------------------------------------- the capsule's letters, white on clear, for the lens

function makeLetterAtlas(font: SkFont): SkImage | null {
  const surface = Skia.Surface.MakeOffscreen(402 * ATLAS_SCALE, ATLAS_H * ATLAS_SCALE);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.clear(Skia.Color('transparent'));
  canvas.scale(ATLAS_SCALE, ATLAS_SCALE);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color('#ffffff'));
  G.gears.forEach((l, i) => {
    const ids = font.getGlyphIDs(l);
    const w = font.getGlyphWidths(ids)[0];
    canvas.drawGlyphs(ids, [{ x: gearX(i) - w / 2, y: G.ballY - ATLAS_Y + (0.69 * G.letterSize) / 2 }], 0, 0, font, paint);
  });
  // the capsule's outline, seen bent near the rim
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(1);
  stroke.setStrokeWidth(2);
  stroke.setColor(Skia.Color('rgba(255,255,255,0.45)'));
  const cp = G.capsule;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cp.x, cp.y - ATLAS_Y, cp.w, cp.h), cp.h / 2, cp.h / 2), stroke);
  surface.flush();
  return surface.makeImageSnapshot();
}

// ---------------------------------------------------------------- the block

/**
 * A drive-mode selector and speedometer made of one line of light and one glass ball: roll the ball to D and the
 * line wakes into a travelling hump that carries the speed, with a curtain of light strands that grows with it.
 */
export function GearPuckSpeedLine({ autoPlay = true }: Props) {
  const reduced = useReducedMotionPreference();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const numFont = useFont(FONTS.num, NUM.size);
  const letterFont = useFont(FONTS.letter, G.letterSize);
  const unitFont = useFont(FONTS.unit, G.unit.size);

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = Math.min(W / 402, H / (G.ballY + G.ringR + 16));
  const Hc = H / S;
  const offsetX = (W - 402 * S) / 2;

  const atlas = useMemo(() => (letterFont ? makeLetterAtlas(letterFont) : null), [letterFont]);
  const digitIds = useMemo(() => (numFont ? numFont.getGlyphIDs('0123456789') : []), [numFont]);
  const unitRun = useMemo(() => {
    if (!unitFont) return null;
    const ids = unitFont.getGlyphIDs('MP/H');
    const ws = unitFont.getGlyphWidths(ids);
    const track = G.unit.tracking * G.unit.size;
    let x = 0;
    const pos = ids.map((id, i) => {
      const p = { id, pos: { x, y: 0 } };
      x += ws[i] + (i < ids.length - 1 ? track : 0);
      return p;
    });
    return { glyphs: pos, w: x };
  }, [unitFont]);
  const unitW = unitRun?.w ?? 30;
  const letters = useMemo(() => {
    if (!letterFont) return [];
    return G.gears.map((l, i) => {
      const ids = letterFont.getGlyphIDs(l);
      const w = letterFont.getGlyphWidths(ids)[0];
      return { glyphs: [{ id: ids[0], pos: { x: 0, y: 0 } }], x: gearX(i) - w / 2 };
    });
  }, [letterFont]);

  // ---- clocks and state ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const phaseOff = useSharedValue(0);
  const live = useSharedValue<Live>(LIVE_REST);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returning = useSharedValue(0);
  const mode = useSharedValue(0); // 1 ball, 2 throttle, 3 pluck
  const g0 = useSharedValue(0);
  const lastX = useSharedValue(0);
  const red = useSharedValue(reduced ? 1 : 0);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
    red.set(reduced ? 1 : 0);
  }, [autoPlay, reduced, auto, red]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __gps?: unknown };
    g.__gps = {
      at: (t: number) => {
        pinned.set(1);
        auto.set(1);
        T.set(t);
      },
      play: () => {
        pinned.set(0);
      },
      rate: (r: number) => {
        rate.set(r);
      },
      poster: POSTER_T,
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), live: live.get() }),
      // drive the live state as a finger would (touch injection is not available on the batch simulator)
      gear: (i: number) => {
        auto.set(0);
        pinned.set(0);
        const s = live.get();
        if ((s.gT === 2 && i === 3) || (s.gT === 3 && i === 2)) live.set({ ...s, gT: i, landed: 0, gapT: now.get() });
        else live.set({ ...s, gT: i, landed: 0 });
        lastTouch.set(now.get());
      },
      throttle: (mph: number) => {
        auto.set(0);
        pinned.set(0);
        live.set({ ...live.get(), speed: mph, sv: 0 });
        lastTouch.set(now.get());
      },
      pluck: (x: number, a: number) => {
        auto.set(0);
        pinned.set(0);
        live.set({ ...live.get(), plX: x, plA: 0, plHold: 0, plRelT: now.get(), plRelA: a });
        lastTouch.set(now.get());
      },
    };
    return () => {
      delete g.__gps;
    };
  }, [T, auto, pinned, rate, live, now, lastTouch]);

  const buzz = (k: number) => {
    if (k === 1) Haptics.selectionAsync().catch(() => {});
    else if (k === 2 || k === 4) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
    else if (k === 3) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (k === 5) {
      [0, 60, 120].forEach((ms) => setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}), ms));
    } else if (k === 6) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    const r = stepLive(live.get(), dt, now.get(), red.get());
    live.set(r[0]);
    if (r[1] !== 0 && touching.get() === 1) scheduleOnRN(buzz, r[1]);
    // 2 s after the last touch: roll home to P, let the line settle, then restart the loop
    if (!red.get() && autoPlay && touching.get() === 0 && now.get() - lastTouch.get() > 2) {
      const s = r[0];
      if (returning.get() === 0) {
        returning.set(1);
        if (s.gT !== 0) live.set({ ...s, gT: 0, landed: 0, thr: 0 });
      } else if (s.landed && s.gT === 0 && s.speed < 0.3 && s.infl < 0.01 && Math.abs(s.numK) < 0.01 && Math.abs(s.plA) < 0.2) {
        phaseOff.set(s.phase - frameAt(0).phase);
        T.set(0);
        auto.set(1);
        returning.set(0);
      }
    }
  });

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    if (auto.get() === 1) {
      const f = frameAt(T.get());
      return { ...f, phase: f.phase + phaseOff.get() };
    }
    return liveFrame(live.get(), now.get());
  });

  const digitsN = useDerivedValue(() => {
    const d = drums(F.get().speed, red.get());
    const tensVis = clamp(d[1], 0, 1);
    const hundVis = clamp(d[0], 0, 1);
    return { d, cells: 1 + tensVis + hundVis };
  });
  const layout = useDerivedValue(() => numeralLayout(F.get(), digitsN.get().cells));

  // ---- the scene shader ----
  const sceneU = useDerivedValue(() => {
    const f = F.get();
    const L = layout.get();
    const wide = 1 + G.hump.lean * Math.abs(f.lean);
    const sigL = G.hump.sigma * (f.lean > 0 ? wide : 1);
    const sigR = G.hump.sigma * (f.lean < 0 ? wide : 1);
    // the extra arc length the hump adds, so the cells stay evenly spaced on its slopes
    const arc: number[] = [0];
    let acc = 0;
    const dx = 402 / 63;
    for (let i = 1; i < 64; i++) {
      for (let j = 0; j < 4; j++) {
        const x = (i - 1) * dx + ((j + 0.5) * dx) / 4;
        const sl = curveY(f, x + 0.5) - curveY(f, x - 0.5);
        acc += ((Math.sqrt(1 + sl * sl) - 1) * dx) / 4;
      }
      arc.push(acc);
    }
    // the curtain parts around whatever text hangs below the line
    const top = L.base - NUM.digitH * L.scale;
    const unitTop = L.base + NUM.unitGap * L.scale - 0.69 * G.unit.size;
    const numBelow = smooth(G.rest - 12, G.rest + 6, top);
    const numW = L.w * L.scale;
    const ux = unitXAt(f, L, unitW);
    const left = Math.min(ux, mix(ux, L.cx - numW / 2, numBelow));
    const right = Math.max(ux + unitW, mix(ux + unitW, L.cx + numW / 2, numBelow));
    const by = mix(unitTop, top, numBelow);
    const bh = L.base + NUM.unitGap * L.scale + 2 - by;
    const c = (i: number) => BAND_RGB[f.cols[i]];
    return {
      uP3: P3,
      uH: Hc,
      uRest: G.rest,
      uHump: [f.x0, f.A + f.jit, sigL, sigR],
      uPluck: [f.pluckX, f.pluckA, f.pluckLag, 0],
      uPhase: f.phase,
      uC0: c(0),
      uC1: c(1),
      uC2: c(2),
      uFront: [f.fPrev[0], f.fPrev[1], f.fLast[0], f.fLast[1]],
      uPacket: [f.packetX, f.packetGain],
      uCurt: [f.curt, f.whip, f.time, f.sway],
      uTrail: -0.08 * f.speed,
      uBox: [left - 4, by - 4, right - left + 8, bh + 4],
      uBright: mix(220, f.x0, clamp(f.A / 96, 0, 1)),
      uArc: arc,
    };
  });

  // ---- the ball ----
  const ballX = useDerivedValue(() => gearX(F.get().g));
  const lensU = useDerivedValue(() => {
    const f = F.get();
    const x = gearX(f.g);
    const lc = bandRgbAt(f, x);
    return {
      uP3: P3,
      uC: [x, G.ballY],
      uR: G.ballR,
      uRing: 1 + 0.4 * f.flash,
      uFlash: f.flash,
      uLine: lc,
      uRoll: (x - G.firstX) / G.ballR,
      uPress: clamp(f.press, 0, 1),
    };
  });
  const lensRect = useDerivedValue(() => Skia.XYWHRect(ballX.get() - 84, G.ballY - 84, 168, 168));

  // ---- the numeral ----
  const slots = useDerivedValue<Slot[]>(() => {
    const { d, cells } = digitsN.get();
    const out: Slot[] = [];
    for (let i = 0; i < 3; i++) {
      const p = d[i];
      const fl = Math.floor(p);
      const roll = p - fl;
      const cur = ((fl % 10) + 10) % 10;
      const nxt = (cur + 1) % 10;
      const x = (cells - 3 + i) * NUM.cell;
      // a higher place's 0 is blank while nothing stands before it
      const blankCur = i < 2 && fl === 0;
      const c: Run = !blankCur && digitIds.length ? [{ id: digitIds[cur], pos: { x, y: -roll * PITCH } }] : [];
      const n: Run = roll > 0.001 && digitIds.length ? [{ id: digitIds[nxt], pos: { x, y: (1 - roll) * PITCH } }] : [];
      out.push({ cur: c, nxt: n, roll });
    }
    return out;
  });
  const numM = useDerivedValue(() => {
    const L = layout.get();
    return [{ translateX: L.cx - (L.w * L.scale) / 2 }, { translateY: L.base }, { scale: L.scale }];
  });
  // motion blur along the drum only (σ ≤ 3 pt), so a fast count is still glyphs, never a smear
  // only while that drum is between two digits: a digit at rest is always sharp
  const blurInk = useDerivedValue(() => {
    const r = Math.abs(F.get().rate);
    const d = digitsN.get().d;
    const k = (i: number) => {
      const f = d[i] - Math.floor(d[i]);
      return Math.min(1, 6.4 * f * (1 - f));
    };
    return [
      { x: 0, y: Math.min(3, (r / 100) * 0.035) * k(0) },
      { x: 0, y: Math.min(3, (r / 10) * 0.035) * k(1) },
      { x: 0, y: Math.min(3, r * 0.035) * k(2) },
    ];
  });
  const glowCol = useDerivedValue(() => {
    const c = bandRgbAt(F.get(), layout.get().cx);
    return `rgba(${Math.round(c[0] * 255)},${Math.round(c[1] * 255)},${Math.round(c[2] * 255)},0.5)`;
  });
  const unitM = useDerivedValue(() => {
    const L = layout.get();
    return [{ translateX: unitXAt(F.get(), L, unitW) }, { translateY: L.base + NUM.unitGap * L.scale }];
  });

  // ---- touch ----
  const takeOver = () => {
    'worklet';
    if (auto.get() === 1) {
      const f = frameAt(T.get());
      live.set(liveFromAuto({ ...f, phase: f.phase + phaseOff.get() }, now.get()));
      auto.set(0);
    }
    pinned.set(0);
    returning.set(0);
    touching.set(1);
    lastTouch.set(now.get());
  };
  const release = () => {
    'worklet';
    touching.set(0);
    lastTouch.set(now.get());
  };
  const shift = (s: Live, to: number) => {
    'worklet';
    const gap = ((s.gT === 2 && to >= 3) || (s.gT >= 3 && to === 2)) && s.speed > 1;
    return { ...s, gT: to, landed: to === s.gT && s.landed ? 1 : 0, gapT: gap ? now.get() : s.gapT };
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      takeOver();
    })
    .onStart((e) => {
      const x = (e.x - offsetX) / S;
      const y = e.y / S;
      const s = live.get();
      const f = liveFrame(s, now.get());
      const bx = gearX(s.g);
      if ((x - bx) * (x - bx) + (y - G.ballY) * (y - G.ballY) < 56 * 56) {
        mode.set(1);
        g0.set(s.g);
        live.set({ ...s, drag: 1, landed: 0 });
        scheduleOnRN(buzz, 6);
      } else if (s.gT >= 2 && Math.abs(x - f.x0) < 110 && y > curveY(f, f.x0) - 40 && y < G.rest + 70) {
        mode.set(2);
        lastX.set(x);
        live.set({ ...s, thr: 1, sv: 0 });
      } else if (Math.abs(y - G.rest) < 90 && !red.get()) {
        mode.set(3);
        live.set({ ...s, plX: x, plHold: 1 });
      } else mode.set(0);
    })
    .onUpdate((e) => {
      const s = live.get();
      if (mode.get() === 1) {
        let r = g0.get() + e.translationX / S / G.pitch;
        if (r < 0) r = r / (1 - r * 3);
        if (r > 4) r = 4 + (r - 4) / (1 + (r - 4) * 3);
        const w = Math.round(clamp(r, 0, 4));
        const shown = r + 0.22 * (w - r) * (1 - smooth(0.18, 0.5, Math.abs(r - w)));
        live.set({ ...s, g: shown });
      } else if (mode.get() === 2) {
        const x = (e.x - offsetX) / S;
        const gi = s.gT;
        const v = clamp(((x - G.hump.x) / G.hump.span) * G.hump.maxSpeed, 0, CAPS[gi]);
        live.set({ ...s, speed: v });
        lastX.set(x);
      } else if (mode.get() === 3) {
        const d = e.translationY / S;
        const a = (Math.sign(d) * 40 * Math.abs(d)) / (Math.abs(d) + 40);
        live.set({ ...s, plX: (e.x - offsetX) / S, plA: a });
      }
    })
    .onEnd((e) => {
      const s = live.get();
      if (mode.get() === 1) {
        const v = e.velocityX / S / G.pitch;
        const to = Math.round(clamp(s.g + v * 0.12, 0, 4));
        live.set({ ...shift(s, to), drag: 0, gv: v, landed: 0 });
      } else if (mode.get() === 2) {
        live.set({ ...s, thr: 0, sv: ((e.velocityX / S) * G.hump.maxSpeed * 0.69) / G.hump.span });
      } else if (mode.get() === 3) {
        live.set({ ...s, plHold: 0, plRelT: now.get(), plRelA: s.plA });
      }
    })
    .onFinalize(() => {
      const s = live.get();
      if (s.drag || s.thr || s.plHold) live.set({ ...s, drag: 0, thr: 0, plHold: 0, plRelT: s.plHold ? now.get() : s.plRelT, plRelA: s.plHold ? s.plA : s.plRelA });
      mode.set(0);
      release();
    });

  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e, ok) => {
      if (!ok) return;
      takeOver();
      const x = (e.x - offsetX) / S;
      const y = e.y / S;
      const s = live.get();
      const bx = gearX(s.g);
      if ((x - bx) * (x - bx) + (y - G.ballY) * (y - G.ballY) < 50 * 50 && (s.gT === 2 || s.gT === 3)) {
        live.set(shift(s, s.gT === 2 ? 3 : 2));
        scheduleOnRN(buzz, 6);
      } else if (y > G.capsule.y - 16 && y < G.capsule.y + G.capsule.h + 16) {
        const to = Math.round(clamp((x - G.firstX) / G.pitch, 0, 4));
        if (to !== s.gT) {
          live.set(shift(s, to));
          scheduleOnRN(buzz, 6);
        }
      }
      release();
    });

  const gesture = Gesture.Exclusive(pan, tap);
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const cp = G.capsule;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout} accessibilityLabel="Drive mode selector and speed line">
        <StatusBar style="light" />
        {size && (
          <Canvas style={{ width: W, height: H }} pointerEvents="none">
            <Group transform={[{ translateX: offsetX }, { scale: S }]}>
              <Rect x={-offsetX / S} y={0} width={W / S} height={Hc}>
                <Shader source={SCENE_FX} uniforms={sceneU} />
              </Rect>

              {/* the capsule: a lit hairline of anodised metal, lighter on its top edge */}
              <RoundedRect x={cp.x} y={cp.y} width={cp.w} height={cp.h} r={cp.h / 2} style="stroke" strokeWidth={2}>
                <LinearGradient start={vec(0, cp.y)} end={vec(0, cp.y + cp.h)} colors={['rgba(170,176,186,0.62)', 'rgba(154,160,170,0.34)', 'rgba(154,160,170,0.5)']} />
              </RoundedRect>
              <RoundedRect x={cp.x + 1.5} y={cp.y + 1.5} width={cp.w - 3} height={cp.h - 3} r={cp.h / 2 - 1.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.05)" />
              {letterFont &&
                letters.map((l, i) => (
                  <Glyphs key={G.gears[i]} font={letterFont} glyphs={l.glyphs} x={l.x} y={G.ballY + (0.69 * G.letterSize) / 2} color={C.letter} />
                ))}

              {/* the glass ball and its ring */}
              {atlas && (
                <Rect rect={lensRect}>
                  <Shader source={LENS_FX} uniforms={lensU}>
                    <ImageShader image={atlas} fit="fill" rect={{ x: 0, y: ATLAS_Y, width: 402, height: ATLAS_H }} tx="decal" ty="decal" />
                  </Shader>
                </Rect>
              )}

              {/* the speed: glow, then ink */}
              {numFont && (
                <>
                  <Numeral font={numFont} slots={slots} matrix={numM as never} color={glowCol} glow />
                  <Numeral font={numFont} slots={slots} matrix={numM as never} color={C.ink} blur={blurInk} />
                </>
              )}
              {unitFont && unitRun && (
                <Group transform={unitM as never}>
                  <Glyphs font={unitFont} glyphs={unitRun.glyphs} x={0} y={0} color={C.ink2} />
                </Group>
              )}
            </Group>
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page, overflow: 'hidden' },
});
