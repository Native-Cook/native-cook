import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { Blur, BlurMask, Canvas, Circle, Group, Image, ImageShader, LinearGradient, Oval, Paint, Path, Rect, Shader, Skia, type SkImage, Text as SkText, useFont, useImage, vec } from '@shopify/react-native-skia';
import { SvgXml } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { PIN, TAB_BADGE, TAB_MAP, TAB_TRAIL } from './tpt-icons';
import { hourRow, paletteAt } from './tpt-light';
import { cadenceOf, cardP, clamp01, dayValue, easeInOut, type Frame, frameAt, leanOf, LOOP, mix, poseOf, runOf, springAt, waypointIdx } from './tpt-motion';
import { GRAIN_FX, LAYER_FX, SKY_FX } from './tpt-shaders';
import { BIOMES, BORDERS, C, FONT, G, MI, RATES, SPRING, TO_GO, TOTAL, WAYPOINTS } from './tpt-theme';

type Props = {
  /** Plays the 12 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
};

// ---- the world ----

const OCC_RATE = 1.7;
const SHAFT_RATE = 0.5;
const CB = BORDERS.map((b) => b * MI); // camera pt of each border
const BIOME_START = [0, CB[0], CB[1], CB[2]];
const BIOME_END = [CB[0], CB[1], CB[2], TOTAL * MI];
/** Reduced motion: every layer at rate 1.0, the strip centred on its biome's travel. */
const FLAT_OFF = BIOMES.map((b, i) => (b.w - 402 - (BIOME_END[i] - BIOME_START[i])) / 2);

/** The hill: a sum of three sines of period 520 pt, ±26 pt, crest under the hiker at rest. */
const groundY = (xw: number, base: number) => {
  'worklet';
  const a = (2 * Math.PI * xw) / 520;
  return base + G.groundAmp[0] * Math.sin(a + 2.3) + G.groundAmp[1] * Math.sin(2 * a + 1.25) + G.groundAmp[2] * Math.sin(3 * a + 0.2);
};

/** Occluders: `cAt` camera pt where their centre line (`core`, local pt) is on the screen's centre. */
type Occ = { cAt: number; w: number; core: number };
const TRUNK_W = 330;
const OCC_ROCK: Occ = { cAt: CB[0], w: 300, core: 150 };
const OCC_TRUNK: Occ = { cAt: CB[1], w: TRUNK_W, core: TRUNK_W / 2 };
const OCC_WALL: Occ = { cAt: CB[2], w: (700 / 3) * 1.6 + 250, core: (340 / 3) * 1.6 };
const GROVE_SX = 0.75;
const OCC_GROVE: Occ = { cAt: 1759, w: TRUNK_W * GROVE_SX, core: (TRUNK_W * GROVE_SX) / 2 };
const OCC_PINES: Occ = { cAt: 3350, w: (1000 / 3) * 1.3, core: (564 / 3) * 1.3 };

/** Light shafts in the grove: world x at rate 0.5 from the grove's start, width. */
const SHAFTS: [number, number][] = [
  [284, 56],
  [372, 42],
  [446, 68],
  [640, 50],
  [800, 64],
  [1010, 44],
];

/** The tree at the grove's border: three lit planes, two stubs, four bark notches (built once, local pt). */
const TRUNK = (() => {
  const top = -200;
  const bot = 620;
  const edgeL = (y: number) => 8 - 8 * ((y - top) / (bot - top)) + 2.5 * Math.sin(y / 37);
  const edgeR = (y: number) => TRUNK_W - 10 + 10 * ((y - top) / (bot - top)) + 2 * Math.sin(y / 29 + 1);
  const band = (fx: (y: number) => number, gx: (y: number) => number) => {
    const b = Skia.PathBuilder.Make();
    const ys: number[] = [];
    for (let y = top; y <= bot; y += 20) ys.push(y);
    b.moveTo(fx(top), top);
    ys.forEach((y) => b.lineTo(fx(y), y));
    [...ys].reverse().forEach((y) => b.lineTo(gx(y), y));
    b.close();
    return b.detach();
  };
  // plane boundaries: lit 22 %, body (split by a fissure), shade 30 %
  const litEdge = (y: number) => 0.22 * TRUNK_W + 5 * Math.sin(y / 23) + 3 * Math.sin(y / 9);
  const shadeEdge = (y: number) => 0.7 * TRUNK_W + 4 * Math.sin(y / 31 + 2) + 2 * Math.sin(y / 11);
  const fissL = (y: number) => 0.47 * TRUNK_W + 7 * Math.sin(y / 41) - 3;
  const fissR = (y: number) => fissL(y) + 5 + 3 * Math.abs(Math.sin(y / 17));
  const lit = band(edgeL, litEdge);
  const body = band(litEdge, shadeEdge);
  const shade = band(shadeEdge, edgeR);
  const fissure = band(fissL, fissR);
  // stubs: one on the lit side reaching up-left, one on the shade side reaching up-right
  const stubL = Skia.PathBuilder.Make().moveTo(10, 118).lineTo(-58, 76).lineTo(-62, 86).lineTo(-52, 94).lineTo(8, 150).close().detach();
  const stubLTop = Skia.PathBuilder.Make().moveTo(10, 118).lineTo(-58, 76).lineTo(-62, 86).lineTo(4, 126).close().detach();
  const stubR = Skia.PathBuilder.Make().moveTo(TRUNK_W - 10, 236).lineTo(TRUNK_W + 54, 196).lineTo(TRUNK_W + 60, 206).lineTo(TRUNK_W + 50, 214).lineTo(TRUNK_W - 6, 270).close().detach();
  // bark notches: short dark chevrons
  const notch = (x: number, y: number, w: number, h: number) =>
    Skia.PathBuilder.Make()
      .moveTo(x, y)
      .lineTo(x + w / 2, y + h * 0.35)
      .lineTo(x + w, y)
      .lineTo(x + w * 0.62, y + h)
      .lineTo(x + w * 0.38, y + h)
      .close()
      .detach();
  const notches = [notch(30, 60, 16, 30), notch(110, 190, 22, 42), notch(190, 300, 18, 34), notch(262, 110, 20, 38)];
  return { lit, body, shade, fissure, stubL, stubLTop, stubR, notches };
})();

/** The rock at the canyon/granite border: a faceted fin in the granite's shade fills (it is nearer, in shadow), lit face, body, shade. */
const ROCK = (() => {
  const poly = (pts: number[][]) => {
    const b = Skia.PathBuilder.Make().moveTo(pts[0][0], pts[0][1]);
    for (let i = 1; i < pts.length; i++) b.lineTo(pts[i][0], pts[i][1]);
    return b.close().detach();
  };
  const left = [[-46, 620], [-34, 430], [-6, 372], [0, 330], [22, 300], [30, 240], [58, 205], [64, 140], [88, 90], [96, 30], [110, -200]];
  const right = [[205, -200], [214, 40], [240, 70], [248, 150], [276, 190], [284, 270], [300, 320], [324, 400], [342, 470], [350, 620]];
  const r1 = [[110, -200], [118, 60], [104, 160], [120, 260], [98, 360], [112, 620]];
  const r2 = [[205, -200], [190, 80], [202, 190], [184, 300], [198, 420], [190, 620]];
  return {
    body: poly([...left, ...right]),
    lit: poly([...left, ...r1.slice(1)]),
    shade: poly([...right, ...[...r2].reverse().slice(0, -1)]),
    crack: poly([[150, 40], [156, 120], [146, 210], [152, 232], [162, 120], [157, 40]]),
    chip: poly([[118, 60], [138, 92], [112, 118]]),
  };
})();

const NO_SPLIT = 9999;
/** Strip pixels per pt: the strips ship at @2x (the grove's 8400 px @3x strips exceed the GPU's texture limit, and @2x
 * flat art keeps its edges while using 4/9 of the memory). */
const STRIP_SCALE = [2, 2, 2, 2];

/** Which biome is left and right of the split line, and where the line is, for camera c (pt). */
const splitOf = (c: number, occRate: number) => {
  'worklet';
  let k = 0;
  for (let i = 0; i < CB.length; i++) if (c >= CB[i]) k = i + 1;
  if (k < CB.length) {
    const sx = 201 - occRate * (c - CB[k]);
    if (sx < 402 + 260) return { a: k, b: k + 1, x: sx };
  }
  if (k > 0) {
    const sx = 201 - occRate * (c - CB[k - 1]);
    if (sx > -260) return { a: k - 1, b: k, x: sx };
  }
  return { a: k, b: k, x: NO_SPLIT };
};

/** Odometer: the position (digit + roll) of column j (0 = lowest) for a continuous value m in that unit. */
const odo = (m: number, j: number) => {
  'worklet';
  const B = Math.pow(10, j);
  const base = Math.floor(m / B);
  const dig = ((base % 10) + 10) % 10;
  if (j === 0) return dig + (m - Math.floor(m));
  const r = (((m % B) + B) % B) - (B - 1);
  return dig + Math.max(0, r);
};

export function TrailParallaxTracker({ autoPlay = true }: Props) {
  const reduced = useReducedMotionPreference();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsOk] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
    [FONT.display]: require('./assets/fonts/Outfit-Bold.ttf'),
  });
  const numFont = useFont(require('./assets/fonts/Outfit-Bold.ttf'), G.numSize);
  const dayFont = useFont(require('./assets/fonts/Inter-Medium.ttf'), G.daySize);

  const b1 = [useImage(require('./assets/b1-canyon-far-ridge-2x.png')), useImage(require('./assets/b1-canyon-mid-snow-2x.png')), useImage(require('./assets/b1-canyon-near-rim-2x.png'))];
  const b2 = [useImage(require('./assets/b2-granite-far-range-2x.png')), useImage(require('./assets/b2-granite-main-spires-2x.png')), useImage(require('./assets/b2-granite-near-buttress-2x.png'))];
  const b3 = [useImage(require('./assets/b3-grove-backlight-2x.png')), useImage(require('./assets/b3-grove-mid-trunks-2x.png')), useImage(require('./assets/b3-grove-near-trunks-2x.png'))];
  const b4 = [useImage(require('./assets/b4-dusk-far-mountain-2x.png')), useImage(require('./assets/b4-dusk-mid-hills-2x.png')), useImage(require('./assets/b4-dusk-pine-row-2x.png'))];
  const blackTrunk = useImage(require('./assets/occluder-black-trunk.png'));
  const pines = useImage(require('./assets/occluder-black-pine-cluster.png'));
  const sheet = useImage(require('./assets/hiker-sheet.png'));
  const strips = [b1, b2, b3, b4];
  const ready = strips.every((s) => s.every(Boolean)) && !!blackTrunk && !!pines && !!sheet;

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = W / 402;
  const HC = H / S; // canvas height in pt
  const dy = HC - 874;
  const sceneB = G.sceneH + dy;
  const base = G.groundBase + dy;
  const flat = reduced;
  const rates = flat ? [1, 1, 1] : RATES;
  const occRate = flat ? 1 : OCC_RATE;

  // ---- clocks and live state ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(-10);
  // live trail state
  const dL = useSharedValue(0);
  const vL = useSharedValue(0); // measured (mi/s)
  const vRel = useSharedValue(0); // release / decay velocity
  const dPrev = useSharedValue(0);
  const phaseL = useSharedValue(0);
  const faceL = useSharedValue(1);
  const faceT0 = useSharedValue(-10);
  const anchor = useSharedValue(0);
  const mode = useSharedValue(0); // 0 settle, 1 decay, 2 home, 3 turn before autoplay
  const target = useSharedValue(0);
  const homeFrom = useSharedValue(0);
  const homeT0 = useSharedValue(0);
  const d0 = useSharedValue(0);
  const skidT0 = useSharedValue(-10);
  const braceT0 = useSharedValue(-10);
  const waveT0 = useSharedValue(-10);
  const bubTapT0 = useSharedValue(-10);
  const bubS = useSharedValue(1);
  const bubV = useSharedValue(0);
  const bubT = useSharedValue(0);
  const cardFrom = useSharedValue(0);
  const cardTo = useSharedValue(0);
  const cardDir = useSharedValue(1);
  const cardT0 = useSharedValue(-10);
  const plants = useSharedValue(0);
  const atEnd = useSharedValue(0);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __tpt?: unknown };
    g.__tpt = {
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
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), d: dL.get(), v: vL.get(), mode: mode.get(), face: faceL.get(), card: cardTo.get() }),
    };
    return () => {
      delete g.__tpt;
    };
  }, [T, auto, pinned, rate, dL, vL, mode, faceL, cardTo]);

  const buzz = (k: 'soft' | 'light' | 'medium' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : k === 'light' ? Haptics.ImpactFeedbackStyle.Light : k === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    if (dt <= 0) return;
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    const n = now.get();
    let d = dL.get();
    const user = mode.get() < 2;
    if (touching.get() === 0) {
      const m = mode.get();
      if (m === 2) {
        // ease home along the trail, then turn round and restart the loop
        const dur = Math.min(Math.max(Math.abs(homeFrom.get()) / 58 + 0.7, 0.8), 3.1);
        const p = Math.min((n - homeT0.get()) / dur, 1);
        d = homeFrom.get() * (1 - easeInOut(p));
        if (p >= 1) {
          d = 0;
          mode.set(3);
          homeT0.set(n);
          if (faceL.get() !== 1) {
            faceL.set(1);
            faceT0.set(n);
          }
        }
      } else if (m === 3) {
        d = 0;
        if (n - homeT0.get() > 0.45) {
          T.set(0);
          auto.set(1);
          mode.set(0);
        }
      } else if (d < 0 || d > TOTAL) {
        // rubber band back to the end
        const b = d < 0 ? 0 : TOTAL;
        const a = -SPRING.glide.k * (d - b) - SPRING.glide.d * vRel.get();
        vRel.set(vRel.get() + a * dt);
        d += vRel.get() * dt;
        mode.set(0);
        target.set(b);
      } else if (m === 1) {
        // decay: deceleration 0.9972 per ms
        const decay = -1000 * Math.log(0.9972);
        const previousVelocity = vRel.get();
        const velocity = previousVelocity * Math.exp(-decay * dt);
        vRel.set(velocity);
        d += (previousVelocity - velocity) / decay;
        if (Math.abs(vRel.get()) < 0.6) {
          mode.set(0);
          target.set(Math.round(d * 10) / 10);
        }
      } else {
        // settle on the nearest tenth: the hiker takes his last step and plants the pole
        const a = -SPRING.glide.k * (d - target.get()) - SPRING.glide.d * vRel.get();
        vRel.set(vRel.get() + a * dt);
        d += vRel.get() * dt;
      }
      // 2 s after the last touch, walk home and restart the loop
      if (autoPlay && !reduced && mode.get() === 0 && n - lastTouch.get() > 2 && Math.abs(d - target.get()) < 0.02 && Math.abs(vRel.get()) < 0.08) {
        mode.set(2);
        homeFrom.set(d);
        homeT0.set(n);
      }
      dL.set(d);
    }
    // measured velocity drives the run, lean, blur and the walk cycle
    const vNow = (d - dPrev.get()) / dt;
    const vOld = vL.get();
    const v = mix(vOld, vNow, 0.35);
    vL.set(v);
    dPrev.set(d);
    const run = reduced ? 0 : runOf(v);
    const ph0 = phaseL.get();
    const ph = ph0 + cadenceOf(v, run) * dt;
    phaseL.set(ph);
    // facing follows travel (when released; the pan flips it itself)
    if (touching.get() === 0 && mode.get() !== 3 && Math.abs(v) > 0.6 && Math.sign(v) !== faceL.get()) {
      faceL.set(Math.sign(v));
      faceT0.set(n);
    }
    // brake skid: |vel| falls through 14
    if (Math.abs(vOld) >= 14 && Math.abs(v) < 14 && n - skidT0.get() > 0.5) {
      skidT0.set(n);
      if (user) scheduleOnRN(buzz, 'medium');
    }
    // footfalls: a plant every half cycle; running, every second one
    const pl = Math.floor(ph * 2);
    if (pl !== plants.get()) {
      plants.set(pl);
      if (user && Math.abs(v) > 0.3) {
        if (run > 0.5) {
          if (pl % 2 === 0) scheduleOnRN(buzz, 'light');
        } else scheduleOnRN(buzz, 'sel');
      }
    }
    // waypoints: the card slides in the travel direction
    const w = waypointIdx(d);
    if (w !== cardTo.get()) {
      cardFrom.set(cardTo.get());
      cardDir.set(w > cardTo.get() ? 1 : -1);
      cardTo.set(w);
      cardT0.set(n);
      if (user) scheduleOnRN(buzz, 'medium');
    }
    // ends: one rigid tap when the band starts to stretch
    const out = d < -0.05 || d > TOTAL + 0.05 ? 1 : 0;
    if (out === 1 && atEnd.get() === 0 && touching.get() === 1) scheduleOnRN(buzz, 'rigid');
    atEnd.set(out);
    // the "Today" bubble: shown at the start of the trail, or for 2 s after a tap on the hiker
    const show = (Math.abs(d) < 0.6 && Math.abs(v) < 1.5 && mode.get() !== 2) || n - bubTapT0.get() < 2 ? 1 : 0;
    const sa = -SPRING.pop.k * (bubS.get() - show) - SPRING.pop.d * bubV.get();
    bubV.set(bubV.get() + sa * dt);
    bubS.set(Math.max(0, bubS.get() + bubV.get() * dt));
    bubT.set(mix(bubT.get(), show === 1 ? 0 : 1, Math.min(1, dt * 10)));
  });

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    if (auto.get() === 1) return frameAt(T.get());
    const n = now.get();
    const v = vL.get();
    const run = reduced ? 0 : runOf(v);
    const ph = phaseL.get();
    const skidAge = n - skidT0.get() < 0.5 ? n - skidT0.get() : -1;
    const turnK = Math.min(Math.max((n - faceT0.get()) / 0.16, 0), 1);
    const hop = turnK > 0 && turnK < 1 ? Math.sin(Math.PI * turnK) : 0;
    const braceAge = n - braceT0.get();
    const brace = braceAge >= 0 && braceAge < 0.6 ? (touching.get() === 1 && braceAge < 0.08 ? braceAge / 0.08 : Math.max(0, springAt(braceAge - 0.08, 1, 0, 0, SPRING.pop.d, SPRING.pop.k))) : 0;
    const waveAge = n - waveT0.get();
    const wave = waveAge >= 0 && waveAge < 0.5 ? [16, 17, 18, 17, 16][Math.min(4, Math.floor(waveAge / 0.1))] : -1;
    const breath = 0.015 * Math.sin((2 * Math.PI * n) / 2);
    // during a hop-turn the old facing holds until the apex
    const face = turnK < 0.5 && hop > 0 ? -faceL.get() : faceL.get();
    return {
      d: dL.get(),
      v,
      run,
      phase: ph,
      pose: poseOf(ph, v, run, wave),
      face,
      hop,
      lean: leanOf(v, run, skidAge),
      squash: 1 - 0.1 * hop - 0.06 * brace + breath,
      bubS: bubS.get(),
      bubT: bubT.get(),
      skidAge,
      cardFrom: cardFrom.get(),
      cardTo: cardTo.get(),
      cardDir: cardDir.get(),
      cardAge: n - cardT0.get(),
    };
  });
  const clockT = useDerivedValue(() => (auto.get() === 1 ? T.get() : now.get()));

  // camera (pt at rate 1) and the split between the biome left behind and the one ahead
  const cam = useDerivedValue(() => F.get().d * MI);
  const split = useDerivedValue(() => splitOf(cam.get(), occRate));
  const row = useDerivedValue(() => hourRow(F.get().d));
  const palA = useDerivedValue(() => paletteAt(split.get().a, row.get()));
  const palB = useDerivedValue(() => paletteAt(split.get().b, row.get()));
  const xBlur = useDerivedValue(() => (flat ? 0 : Math.min(14, Math.abs(F.get().v) * 0.22)));
  const xBlurV = useDerivedValue(() => vec(xBlur.get(), 0));

  const layerOff = (bi: number, depth: number, c: number) => {
    'worklet';
    const rel = c - BIOME_START[bi];
    return rel * rates[depth] + (flat ? FLAT_OFF[bi] : BIOMES[bi].off[depth]);
  };
  const imgsOf = (depth: number) => {
    const list = [b1[depth], b2[depth], b3[depth], b4[depth]];
    return list;
  };
  const L0 = imgsOf(0);
  const L1 = imgsOf(1);
  const L2 = imgsOf(2);
  const imgA0 = useDerivedValue<SkImage | null>(() => L0[split.get().a]);
  const imgB0 = useDerivedValue<SkImage | null>(() => L0[split.get().b]);
  const imgA1 = useDerivedValue<SkImage | null>(() => L1[split.get().a]);
  const imgB1 = useDerivedValue<SkImage | null>(() => L1[split.get().b]);
  const imgA2 = useDerivedValue<SkImage | null>(() => L2[split.get().a]);
  const imgB2 = useDerivedValue<SkImage | null>(() => L2[split.get().b]);
  const layerU = (depth: number) => () => {
    'worklet';
    const sp = split.get();
    const c = cam.get();
    const pa = palA.get();
    const pb = palB.get();
    return {
      uSplit: sp.x,
      uOffA: layerOff(sp.a, depth, c),
      uOffB: layerOff(sp.b, depth, c),
      uTop: dy + G.artTop,
      uScA: STRIP_SCALE[sp.a],
      uScB: STRIP_SCALE[sp.b],
      uNA: pa.n,
      uNB: pb.n,
      uSrcA: pa.src,
      uDstA: pa.dst,
      uSrcB: pb.src,
      uDstB: pb.dst,
    };
  };
  const u0 = useDerivedValue(layerU(0));
  const u1 = useDerivedValue(layerU(1));
  const u2 = useDerivedValue(layerU(2));
  const skyU = useDerivedValue(() => {
    const pa = palA.get();
    const pb = palB.get();
    return { uSplit: split.get().x, uHor: 400 + dy + G.artTop, uTopA: pa.sky[0], uMidA: pa.sky[1], uHorA: pa.sky[2], uTopB: pb.sky[0], uMidB: pb.sky[1], uHorB: pb.sky[2] };
  });

  // light shafts: only on the grove's side of the split
  const groveClip = useDerivedValue(() => {
    const sp = split.get();
    if (sp.a === 2 && sp.b === 2) return Skia.XYWHRect(-50, 0, 520, sceneB);
    if (sp.b === 2) return Skia.XYWHRect(sp.x, 0, 600, sceneB);
    if (sp.a === 2) return Skia.XYWHRect(-200, 0, sp.x + 200, sceneB);
    return Skia.XYWHRect(0, 0, 0, 0);
  });
  const shafts = useDerivedValue(() => {
    const rel = cam.get() - BIOME_START[2];
    const b = Skia.PathBuilder.Make();
    for (let i = 0; i < SHAFTS.length; i++) {
      const x = SHAFTS[i][0] - rel * (flat ? 1 : SHAFT_RATE);
      const w = SHAFTS[i][1];
      if (x > 460 || x < -260) continue;
      b.moveTo(x, -4)
        .lineTo(x + w * 0.62, -4)
        .lineTo(x + w * 0.62 + 150, sceneB)
        .lineTo(x + 150 - w * 0.38, sceneB)
        .close();
    }
    return b.detach();
  });

  // the hill and its rim light
  const contour = useDerivedValue(() => {
    const c = cam.get();
    const pts: number[] = [];
    for (let x = -12; x <= 414; x += 6) pts.push(x, groundY(x + c, base));
    return pts;
  });
  const groundPath = useDerivedValue(() => {
    const p = contour.get();
    const b = Skia.PathBuilder.Make().moveTo(p[0], p[1]);
    for (let i = 2; i < p.length; i += 2) b.lineTo(p[i], p[i + 1]);
    b.lineTo(414, HC + 10).lineTo(-12, HC + 10).close();
    return b.detach();
  });
  const rimPath = useDerivedValue(() => {
    const p = contour.get();
    const b = Skia.PathBuilder.Make().moveTo(p[0], p[1]);
    for (let i = 2; i < p.length; i += 2) b.lineTo(p[i], p[i + 1]);
    return b.detach();
  });
  /** Occluders stand on a nearer fold of the hill, 30 pt below the crest line: clip them above it. */
  const occClip = useDerivedValue(() => {
    const p = contour.get();
    const b = Skia.PathBuilder.Make().moveTo(-12, -20);
    for (let i = 0; i < p.length; i += 2) b.lineTo(p[i], p[i + 1] + 30);
    b.lineTo(414, -20).close();
    return b.detach();
  });
  const rimClipA = useDerivedValue(() => Skia.XYWHRect(-20, 0, Math.max(0, Math.min(split.get().x, 460) + 20), HC));
  const rimClipB = useDerivedValue(() => {
    const x = Math.max(-20, Math.min(split.get().x, 460));
    return Skia.XYWHRect(x, 0, 480 - x, HC);
  });
  const horA = useDerivedValue(() => palA.get().horizon);
  const horB = useDerivedValue(() => palB.get().horizon);

  // occluders
  const occLeft = (o: Occ) => () => {
    'worklet';
    return 201 - o.core - occRate * (cam.get() - o.cAt);
  };
  const finX = useDerivedValue(occLeft(OCC_ROCK));
  const trunkX = useDerivedValue(occLeft(OCC_TRUNK));
  const wallX = useDerivedValue(occLeft(OCC_WALL));
  const groveX = useDerivedValue(occLeft(OCC_GROVE));
  const pinesX = useDerivedValue(occLeft(OCC_PINES));
  const onScreen = (x: SharedValue<number>, o: Occ) => () => {
    'worklet';
    const l = x.get();
    return l < 420 && l + o.w > -80 ? 1 : 0;
  };
  const finOn = useDerivedValue(onScreen(finX, OCC_ROCK));
  const trunkOn = useDerivedValue(onScreen(trunkX, OCC_TRUNK));
  const wallOn = useDerivedValue(onScreen(wallX, OCC_WALL));
  const groveOn = useDerivedValue(onScreen(groveX, OCC_GROVE));
  const pinesOn = useDerivedValue(onScreen(pinesX, OCC_PINES));
  const trunkM = useDerivedValue(() => [{ translateX: trunkX.get() }, { translateY: dy + G.artTop }]);
  const groveM = useDerivedValue(() => [{ translateX: groveX.get() }, { translateY: dy + G.artTop }, { scaleX: GROVE_SX }]);
  const rockM = useDerivedValue(() => [{ translateX: finX.get() }, { translateY: dy + G.artTop }]);
  const occY = (scale: number) => sceneB + 26 - 440 * scale;

  // the hiker
  const hiker = useDerivedValue(() => {
    const f = F.get();
    const c = cam.get();
    const x = G.hikerX;
    const y = groundY(x + c, base) + 1;
    const slope = Math.atan2(groundY(x + c + 6, base) - groundY(x + c - 6, base), 12);
    const rot = Math.max(-14, Math.min(14, (slope * 180) / Math.PI * 0.6 + f.lean * f.face));
    return { x, y: y - 4 * f.hop, foot: y, rot, f };
  });
  const hikerT = useDerivedValue(() => {
    const h = hiker.get();
    return [
      { translateX: h.x },
      { translateY: h.y },
      { rotate: (h.rot * Math.PI) / 180 },
      { scaleX: h.f.face },
      { scaleY: h.f.squash },
      { translateX: -G.hikerAx * G.hikerW },
      { translateY: -G.hikerAy * G.hikerH },
    ];
  });
  const sheetX = useDerivedValue(() => -hiker.get().f.pose * G.hikerW);
  const shadowY = useDerivedValue(() => hiker.get().foot - 2);
  const puffs = useDerivedValue(() => {
    const h = hiker.get();
    const age = h.f.skidAge;
    const k = age >= 0 && age < 0.32 ? age / 0.32 : -1;
    const r = [5, 8, 6];
    const out: { x: number; y: number; r: number }[] = [];
    for (let i = 0; i < 3; i++) {
      const s = k < 0 ? 0 : Math.sin(Math.PI * Math.min(1, k * 1.15 + i * 0.05));
      out.push({ x: h.x - h.f.face * (8 + i * 9), y: h.foot - 3 - 10 * Math.max(k, 0) - i * 2, r: r[i] * s });
    }
    return out;
  });
  const p0x = useDerivedValue(() => puffs.get()[0].x);
  const p0y = useDerivedValue(() => puffs.get()[0].y);
  const p0r = useDerivedValue(() => puffs.get()[0].r);
  const p1x = useDerivedValue(() => puffs.get()[1].x);
  const p1y = useDerivedValue(() => puffs.get()[1].y);
  const p1r = useDerivedValue(() => puffs.get()[1].r);
  const p2x = useDerivedValue(() => puffs.get()[2].x);
  const p2y = useDerivedValue(() => puffs.get()[2].y);
  const p2r = useDerivedValue(() => puffs.get()[2].r);
  const puffCol = useDerivedValue(() => (split.get().x > G.hikerX ? palA.get().horizon : palB.get().horizon));

  // ---- the number: an odometer of five columns; "Day" rolls the same way ----
  const numW = numFont ? numFont.getGlyphWidths(numFont.getGlyphIDs('0123456789.')) : [];
  const cell = numW.length ? Math.max(...numW.slice(0, 10)) + 1.2 : 36;
  const dotW = numW.length ? numW[10] + 1.2 : 14;
  const numTotal = 4 * cell + dotW;
  const numX0 = 201 - numTotal / 2;
  const colX = [numX0 + 3 * cell + dotW, numX0 + 2 * cell, numX0 + cell, numX0]; // tenths, units, tens, hundreds
  const numBase = G.numBase + dy;
  const dayW = dayFont ? dayFont.getGlyphWidths(dayFont.getGlyphIDs('0123456789')) : [];
  const dayCell = dayW.length ? Math.max(...dayW) : 8;
  const dayLabelW = dayFont ? dayFont.getGlyphWidths(dayFont.getGlyphIDs('Day ')).reduce((a, b) => a + b, 0) : 30;
  const dayX0 = 201 - (dayLabelW + 3 * dayCell) / 2;
  const dayBase = G.dayBase + dy;
  const milesW = dayFont ? dayFont.getGlyphWidths(dayFont.getGlyphIDs('miles')).reduce((a, b) => a + b, 0) : 30;

  const numM = useDerivedValue(() => (TO_GO - Math.min(Math.max(F.get().d, 0), TOTAL)) * 10);
  const dayM = useDerivedValue(() => dayValue(Math.min(Math.max(F.get().d, 0), TOTAL)));

  const numClip = Skia.XYWHRect(0, G.numClipTop + dy, 402, G.numClipBottom - G.numClipTop);
  const dayClip = Skia.XYWHRect(0, dayBase - 19, 402, 25);

  // ---- touch ----
  const takeOver = () => {
    'worklet';
    const n = now.get();
    if (auto.get() === 1) {
      // keep what the loop shows: the scene stops under the finger
      const f = frameAt(T.get());
      dL.set(f.d);
      dPrev.set(f.d);
      vL.set(f.v);
      phaseL.set(f.phase);
      faceL.set(f.face);
      cardFrom.set(f.cardFrom);
      cardTo.set(f.cardTo);
      cardDir.set(f.cardDir);
      cardT0.set(n - f.cardAge);
      bubS.set(f.bubS);
      bubV.set(0);
      bubT.set(f.bubT);
      auto.set(0);
      if (Math.abs(f.v) > 14) skidT0.set(n);
    } else if (Math.abs(vL.get()) > 14) {
      skidT0.set(n);
      scheduleOnRN(buzz, 'medium');
    }
    pinned.set(0);
    mode.set(0);
    vRel.set(0);
    target.set(dL.get());
    d0.set(dL.get());
    anchor.set(dL.get());
    braceT0.set(n);
    touching.set(1);
    lastTouch.set(n);
  };
  const release = () => {
    'worklet';
    touching.set(0);
    lastTouch.set(now.get());
  };
  /** Finger moved tx (px) since touch-down: 24 pt = 1 mi, drag left = forward, rubber band past either end. */
  const panMove = (tx: number) => {
    'worklet';
    let d = d0.get() - tx / S / MI;
    if (d < 0) {
      const over = -d * MI;
      d = -over / (1 + over / 120) / MI;
    } else if (d > TOTAL) {
      const over = (d - TOTAL) * MI;
      d = TOTAL + over / (1 + over / 120) / MI;
    }
    dL.set(d);
    // turn round once the finger goes 4 pt the other way
    const f = faceL.get();
    if ((d - anchor.get()) * f > 0) anchor.set(d);
    else if ((anchor.get() - d) * f > 4 / MI) {
      faceL.set(-f);
      faceT0.set(now.get());
      anchor.set(d);
    }
    lastTouch.set(now.get());
  };
  /** Release with the finger's velocity (px/s): decay, or settle on the nearest tenth. */
  const panEnd = (vx: number) => {
    'worklet';
    const v = -vx / S / MI;
    vRel.set(v);
    if (dL.get() >= 0 && dL.get() <= TOTAL && Math.abs(v) >= 0.6) mode.set(1);
    else {
      mode.set(0);
      target.set(Math.round(Math.min(Math.max(dL.get(), 0), TOTAL) * 10) / 10);
    }
  };
  /** A tap at canvas pt (x, y): on the hiker he waves and the bubble pops out for 2 s. */
  const tapAt = (x: number, y: number) => {
    'worklet';
    const c = dL.get() * MI;
    const fy = groundY(G.hikerX + c, base);
    if (Math.abs(x - G.hikerX) < 36 && y > fy - G.hikerH - 14 && y < fy + 10) {
      waveT0.set(now.get());
      bubTapT0.set(now.get());
      scheduleOnRN(buzz, 'sel');
    }
    if (mode.get() === 0) target.set(Math.round(Math.min(Math.max(dL.get(), 0), TOTAL) * 10) / 10);
  };
  const pan = Gesture.Pan()
    .activeOffsetX([-6, 6])
    .maxPointers(1)
    .onBegin(() => {
      takeOver();
      scheduleOnRN(buzz, 'soft');
    })
    .onStart(() => {
      d0.set(dL.get());
      anchor.set(dL.get());
    })
    .onUpdate((e) => {
      panMove(e.translationX);
    })
    .onFinalize((e, success) => {
      panEnd(success ? e.velocityX : 0);
      release();
    });
  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e, ok) => {
      if (ok) tapAt(e.x / S, e.y / S);
    });
  const gesture = Gesture.Simultaneous(pan, tap);

  // dev only: drive the same touch code without touch injection (down, move tx px, up vx px/s, tap at pt)
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __tptTouch?: unknown };
    g.__tptTouch = {
      down: () => scheduleOnUI(() => {
        'worklet';
        takeOver();
        d0.set(dL.get());
        anchor.set(dL.get());
      }),
      move: (tx: number) => scheduleOnUI(panMove, tx),
      up: (vx: number) =>
        scheduleOnUI((v: number) => {
          'worklet';
          panEnd(v);
          release();
        }, vx),
      tap: (x: number, y: number) =>
        scheduleOnUI((a: number, b: number) => {
          'worklet';
          takeOver();
          tapAt(a, b);
          release();
        }, x, y),
    };
    return () => {
      delete g.__tptTouch;
    };
  });

  return (
    <View style={styles.root} onLayout={(e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill}>
          {size && ready && (
            <Canvas style={{ width: W, height: H }} pointerEvents="none">
              <Group transform={[{ scale: S }]}>
                <Group clip={Skia.XYWHRect(0, 0, 402, sceneB + 40)}>
                  {/* sky */}
                  <Rect x={0} y={0} width={402} height={sceneB + 40}>
                    <Shader source={SKY_FX} uniforms={skyU} />
                  </Rect>
                  {/* far, mid, near: two biomes at once, split under the occluder's centre line */}
                  <Rect x={0} y={0} width={402} height={sceneB + 40}>
                    <Shader source={LAYER_FX} uniforms={u0}>
                      <ImageShader image={imgA0} tx="decal" ty="decal" />
                      <ImageShader image={imgB0} tx="decal" ty="decal" />
                    </Shader>
                  </Rect>
                  <Rect x={0} y={0} width={402} height={sceneB + 40}>
                    <Shader source={LAYER_FX} uniforms={u1}>
                      <ImageShader image={imgA1} tx="decal" ty="decal" />
                      <ImageShader image={imgB1} tx="decal" ty="decal" />
                    </Shader>
                    <Blur blur={xBlurV} mode="clamp" />
                  </Rect>
                  {/* the grove's light shafts slide over the teal trunks */}
                  <Group clip={groveClip}>
                    <Path path={shafts} color="#F8FAD6" opacity={0.45}>
                      <Blur blur={xBlurV} mode="decal" />
                    </Path>
                  </Group>
                  <Rect x={0} y={0} width={402} height={sceneB + 40}>
                    <Shader source={LAYER_FX} uniforms={u2}>
                      <ImageShader image={imgA2} tx="decal" ty="decal" />
                      <ImageShader image={imgB2} tx="decal" ty="decal" />
                    </Shader>
                    <Blur blur={xBlurV} mode="clamp" />
                  </Rect>
                </Group>

                {/* the black hill, which is also the data panel */}
                <Path path={groundPath} color={C.panel} />
                {/* rim light: 2 pt in the horizon colour on the crest, a 14 pt bleed down into the panel */}
                <Group clip={groundPath}>
                  <Group clip={rimClipA}>
                    <Path path={rimPath} style="stroke" strokeWidth={28} color={horA} opacity={0.24}>
                      <BlurMask blur={5} style="normal" />
                    </Path>
                  </Group>
                  <Group clip={rimClipB}>
                    <Path path={rimPath} style="stroke" strokeWidth={28} color={horB} opacity={0.24}>
                      <BlurMask blur={5} style="normal" />
                    </Path>
                  </Group>
                </Group>
                <Group clip={rimClipA}>
                  <Path path={rimPath} style="stroke" strokeWidth={2.5} color={horA} opacity={0.75} />
                </Group>
                <Group clip={rimClipB}>
                  <Path path={rimPath} style="stroke" strokeWidth={2.5} color={horB} opacity={0.75} />
                </Group>

                {/* the hiker: contact shadow, sprite, skid dust */}
                <Oval x={G.hikerX - 14} y={shadowY} width={28} height={4} color="rgba(0,0,0,0.35)" />
                <Group transform={hikerT}>
                  <Group clip={Skia.XYWHRect(0, 0, G.hikerW, G.hikerH)}>
                    <Image image={sheet} x={sheetX} y={0} width={G.hikerW * G.sheetFrames} height={G.hikerH} fit="fill" />
                  </Group>
                </Group>
                <Circle cx={p0x} cy={p0y} r={p0r} color={puffCol} opacity={0.6} />
                <Circle cx={p1x} cy={p1y} r={p1r} color={puffCol} opacity={0.6} />
                <Circle cx={p2x} cy={p2y} r={p2r} color={puffCol} opacity={0.6} />

                {/* occluders: nearer than the hiker, standing on a fold of the hill below the crest */}
                <Group clip={occClip}>
                  <Group opacity={finOn} transform={rockM} layer={<Paint><Blur blur={xBlurV} mode="decal" /></Paint>}>
                    <Path path={ROCK.body} color="#4B5980" />
                    <Path path={ROCK.lit} color="#6E6C96" />
                    <Path path={ROCK.shade} color="#323C5A" />
                    <Path path={ROCK.crack} color="#323C5A" />
                    <Path path={ROCK.chip} color="#9A8AA5" />
                  </Group>
                  {/* a near trunk in the grove, in silhouette, rim-lit green by the clearing behind it */}
                  <Group opacity={groveOn} transform={groveM} layer={<Paint><Blur blur={xBlurV} mode="decal" /></Paint>}>
                    <Path path={TRUNK.stubLTop} color="#2C4F45" />
                    <Path path={TRUNK.stubL} color="#0D1418" />
                    <Path path={TRUNK.stubR} color="#070B0D" />
                    <Path path={TRUNK.lit} color="#2C4F45" />
                    <Path path={TRUNK.body} color="#0D1418" />
                    <Path path={TRUNK.shade} color="#080C0E" />
                    <Path path={TRUNK.fissure} color="#080C0E" />
                    {TRUNK.notches.map((q, i) => (
                      <Path key={i} path={q} color={i === 0 ? '#1B332D' : '#080C0E'} />
                    ))}
                  </Group>
                  <Group opacity={wallOn}>
                    <Image image={blackTrunk} x={wallX} y={occY(1.6)} width={(700 / 3) * 1.6} height={440 * 1.6} fit="fill">
                      <Blur blur={xBlurV} mode="decal" />
                    </Image>
                  </Group>
                  <Group opacity={wallOn} transform={[{ translateX: 0 }]}>
                    <WallPines image={pines} x={wallX} y={occY(1.3)} blur={xBlurV} />
                  </Group>
                  <Group opacity={pinesOn}>
                    <Image image={pines} x={pinesX} y={occY(1.3)} width={(1000 / 3) * 1.3} height={440 * 1.3} fit="fill">
                      <Blur blur={xBlurV} mode="decal" />
                    </Image>
                  </Group>
                  <Group opacity={trunkOn} transform={trunkM} layer={<Paint><Blur blur={xBlurV} mode="decal" /></Paint>}>
                    <Path path={TRUNK.stubLTop} color="#A8392B" />
                    <Path path={TRUNK.stubL} color="#8E2A22" />
                    <Path path={TRUNK.stubR} color="#4A1517" />
                    <Path path={TRUNK.lit} color="#A8392B" />
                    <Path path={TRUNK.body} color="#8E2A22" />
                    <Path path={TRUNK.shade} color="#5E1A1C" />
                    <Path path={TRUNK.fissure} color="#5E1A1C" />
                    {TRUNK.notches.map((p, i) => (
                      <Path key={i} path={p} color={i === 3 ? '#47131A' : '#5E1A1C'} />
                    ))}
                  </Group>
                </Group>

                <Rect x={0} y={0} width={402} height={sceneB}>
                  <Shader source={GRAIN_FX} uniforms={{ uScale: S * 3 }} />
                </Rect>

                {/* Day, the number, "miles" */}
                {dayFont && (
                  <Group>
                    <SkText x={dayX0} y={dayBase} text="Day " font={dayFont} color={C.mute} />
                    <Group clip={dayClip}>
                      {[2, 1, 0].map((j) => (
                        <Odo key={j} m={dayM} j={j} font={dayFont} widths={dayW} x={dayX0 + dayLabelW + (2 - j) * dayCell} cell={dayCell} base={dayBase} step={22} color={C.mute} speed={null} top={dayBase - 19} bottom={dayBase + 6} />
                      ))}
                    </Group>
                    <SkText x={201 - milesW / 2} y={G.milesBase + dy} text="miles" font={dayFont} color={C.mute} />
                  </Group>
                )}
                {numFont && (
                  <Group clip={numClip}>
                    {[3, 2, 1, 0].map((j) => (
                      <Odo key={j} m={numM} j={j} font={numFont} widths={numW} x={colX[j]} cell={cell} base={numBase} step={G.numStep} color={C.ink} speed={F} reduced={flat} top={G.numClipTop + dy} bottom={G.numClipBottom + dy} />
                    ))}
                    <SkText x={numX0 + 3 * cell + 0.6} y={numBase} text="." font={numFont} color={C.ink} />
                  </Group>
                )}
              </Group>
            </Canvas>
          )}
          <Bubble F={F} cam={cam} base={base} S={S} fontsOk={fontsOk} clockT={clockT} />
          <PlaceCard F={F} S={S} dy={dy} fontsOk={fontsOk} reduced={flat} />
          <View pointerEvents="none" style={[styles.tabs, { top: (G.tabs.y + dy - 12) * S }]}>
            {[TAB_TRAIL(C.tabOn), TAB_MAP(C.tabOff), TAB_BADGE(C.tabOff)].map((xml, i) => (
              <View key={i} style={{ position: 'absolute', left: (G.tabs.xs[i] - 12) * S }}>
                <SvgXml xml={xml} width={G.tabs.size * S} height={G.tabs.size * S} />
              </View>
            ))}
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

/** The black wall's trailing pines, attached to its right edge (part of the grove → dusk occluder). */
function WallPines({ image, x, y, blur }: { image: SkImage | null; x: SharedValue<number>; y: number; blur: SharedValue<{ x: number; y: number }> }) {
  const px = useDerivedValue(() => x.get() + (700 / 3) * 1.6 - 180);
  return (
    <Image image={image} x={px} y={y} width={(1000 / 3) * 1.3} height={440 * 1.3} fit="fill">
      <Blur blur={blur} mode="decal" />
    </Image>
  );
}

type OdoProps = {
  m: SharedValue<number>;
  j: number;
  font: NonNullable<ReturnType<typeof useFont>>;
  widths: number[];
  x: number;
  cell: number;
  base: number;
  step: number;
  color: string;
  /** frame source for the column's speed blur (y only); null for none */
  speed: SharedValue<Frame> | null;
  reduced?: boolean;
  /** the drum's window (canvas pt) */
  top: number;
  bottom: number;
};

/** Drum digits drawn per column: the one above, the resting one, and two below (a stripe at speed). */
const DRUM = [-1, 0, 1, 2];

/**
 * One odometer column, a masked drum: the resting digit sits fully opaque in the middle of the window, the outgoing
 * and incoming digits slide past it and fade out through soft top and bottom edges (a dstIn gradient, no hard cut).
 * As the column speeds up its digits close up into a stripe of stacked glyphs, blurred vertically by at most 3 pt,
 * so every pinned frame still shows digits.
 */
function Odo({ m, j, font, widths, x, cell, base, step, color, speed, reduced, top, bottom }: OdoProps) {
  const pos = useDerivedValue(() => odo(m.get(), j));
  const k = useDerivedValue(() => {
    if (!speed || reduced) return 0;
    const colSpeed = (Math.abs(speed.get().v) * 10) / Math.pow(10, j);
    return clamp01((colSpeed - 4) / 40);
  });
  const blur = useDerivedValue(() => vec(0, k.get() * 3));
  const fade = Math.min(16, (bottom - top) * 0.2);
  const clip = Skia.XYWHRect(x - 4, top, cell + 8, bottom - top);
  return (
    <Group clip={clip} layer={<Paint />}>
      <Group layer={<Paint><Blur blur={blur} mode="decal" /></Paint>}>
        {DRUM.map((i) => (
          <DrumDigit key={i} i={i} pos={pos} k={k} font={font} widths={widths} x={x} cell={cell} base={base} step={step} color={color} />
        ))}
      </Group>
      <Rect x={x - 4} y={top} width={cell + 8} height={bottom - top} blendMode="dstIn">
        <LinearGradient start={vec(0, top)} end={vec(0, bottom)} positions={[0, fade / (bottom - top), 1 - fade / (bottom - top), 1]} colors={['rgba(0,0,0,0)', 'black', 'black', 'rgba(0,0,0,0)']} />
      </Rect>
    </Group>
  );
}

type DrumDigitProps = Omit<OdoProps, 'm' | 'speed' | 'reduced' | 'top' | 'bottom' | 'j'> & { i: number; pos: SharedValue<number>; k: SharedValue<number> };

/** Digit (resting + i) of a drum: spaced a full step apart at rest, 0.8 of it at full speed (never overlapping). */
function DrumDigit({ i, pos, k, font, widths, x, cell, base, step, color }: DrumDigitProps) {
  const d = useDerivedValue(() => (((Math.floor(pos.get()) + i) % 10) + 10) % 10);
  const text = useDerivedValue(() => String(d.get()));
  const dx = useDerivedValue(() => x + (cell - widths[d.get()]) / 2);
  const dy = useDerivedValue(() => {
    const p = pos.get();
    const st = step * mix(1, 0.8, k.get());
    return base + (i - (p - Math.floor(p))) * st;
  });
  return <SkText x={dx} y={dy} text={text} font={font} color={color} />;
}

type BubbleProps = { F: SharedValue<Frame>; cam: SharedValue<number>; base: number; S: number; fontsOk: boolean; clockT: SharedValue<number> };

/** "Today 0.6 mi": tethered over the hiker's head; shrinks along the tether into his pack and pops back out. */
function Bubble({ F, cam, base, S, fontsOk, clockT }: BubbleProps) {
  const B = G.bubble;
  const st = useAnimatedStyle(() => {
    const f = F.get();
    const c = cam.get();
    const foot = groundY(G.hikerX + c, base);
    const float = 1.5 * Math.sin((2 * Math.PI * 3 * clockT.get()) / LOOP);
    const headX = G.hikerX;
    const headY = foot - G.hikerH - B.gap + float;
    const packX = G.hikerX - f.face * 14;
    const packY = foot - 45;
    const x = mix(headX, packX, f.bubT);
    const y = mix(headY, packY, f.bubT);
    const s = Math.max(f.bubS, 0);
    return {
      opacity: s > 0.01 ? 1 : 0,
      transform: [{ translateX: (x - B.w / 2) * S }, { translateY: (y - B.h) * S + ((B.h * S) / 2) * (1 - s) }, { scale: s }],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.bubble, { width: B.w * S, height: B.h * S, borderRadius: B.r * S }, st]}>
      <Text allowFontScaling={false} style={[styles.bubLabel, fontsOk && { fontFamily: FONT.semibold }, { fontSize: 13 * S, lineHeight: 16 * S }]}>
        Today
      </Text>
      <Text allowFontScaling={false} style={[styles.bubValue, fontsOk && { fontFamily: FONT.display }, { fontSize: 17 * S, lineHeight: 20 * S }]}>
        0.6 mi
      </Text>
    </Animated.View>
  );
}

type CardProps = { F: SharedValue<Frame>; S: number; dy: number; fontsOk: boolean; reduced: boolean };

/** The place card: a masked slide in the travel direction (24 pt), the edge sweeping the card; no fades. */
function PlaceCard({ F, S, dy, fontsOk, reduced }: CardProps) {
  const K = G.card;
  return (
    <View pointerEvents="none" style={[styles.card, { left: K.x * S, top: (K.y + dy) * S, width: K.w * S, height: K.h * S, borderRadius: K.r * S }]}>
      {WAYPOINTS.map((w, i) => (
        <CardFace key={w.name} i={i} F={F} S={S} fontsOk={fontsOk} reduced={reduced} />
      ))}
      {!reduced && <CardBlade F={F} S={S} />}
    </View>
  );
}

/** The wipe's blade: a band of card colour with a lit hairline rides the mask edge, so the outgoing and incoming
 * text never touch mid-sweep. It exists only while the card is changing. */
function CardBlade({ F, S }: { F: SharedValue<Frame>; S: number }) {
  const K = G.card;
  const BW = 14;
  const st = useAnimatedStyle(() => {
    const f = F.get();
    const p = cardP(f.cardAge);
    const on = f.cardFrom !== f.cardTo && p > 0 && p < 1;
    const edge = f.cardDir >= 0 ? K.w * (1 - p) : K.w * p;
    return { transform: [{ translateX: (edge - BW / 2) * S }], width: on ? BW * S : 0 };
  });
  return (
    <Animated.View style={[styles.blade, { height: K.h * S }, st]}>
      <View style={[styles.bladeLine, { left: (BW / 2 - 0.75) * S, width: 1.5 * S, height: K.h * S }]} />
    </Animated.View>
  );
}

function CardFace({ i, F, S, fontsOk, reduced }: { i: number; F: SharedValue<Frame>; S: number; fontsOk: boolean; reduced: boolean }) {
  const K = G.card;
  const clip = useAnimatedStyle(() => {
    const f = F.get();
    const p = reduced ? clamp01(f.cardAge / 0.2) : cardP(f.cardAge);
    const fw = f.cardDir >= 0;
    const edge = fw ? K.w * (1 - p) : K.w * p; // the sweeping mask edge
    let l = 0;
    let w = 0;
    let o = 1;
    if (i === f.cardTo) {
      if (reduced) {
        w = K.w;
        o = p;
      } else if (fw) {
        l = edge;
        w = K.w - edge;
      } else w = edge;
    } else if (i === f.cardFrom && p < 1) {
      if (reduced) {
        w = K.w;
        o = 1 - p;
      } else if (fw) w = edge;
      else {
        l = edge;
        w = K.w - edge;
      }
    }
    return { left: l * S, width: w * S, opacity: o };
  });
  const inner = useAnimatedStyle(() => {
    const f = F.get();
    const p = reduced ? 1 : cardP(f.cardAge);
    const fw = f.cardDir >= 0;
    const edge = fw ? K.w * (1 - p) : K.w * p;
    let l = 0;
    let slide = 0;
    if (i === f.cardTo) {
      l = fw ? edge : 0;
      slide = (fw ? 24 : -24) * (1 - p);
    } else {
      l = fw ? 0 : edge;
      slide = (fw ? -24 : 24) * p;
    }
    if (reduced) return { transform: [{ translateX: 0 }] };
    return { transform: [{ translateX: (-l + slide) * S }] };
  });
  const w = WAYPOINTS[i];
  return (
    <Animated.View style={[styles.cardClip, { height: K.h * S }, clip]}>
      <Animated.View style={[{ width: K.w * S, height: K.h * S }, inner]}>
        <View style={{ position: 'absolute', left: 14 * S, top: 15 * S }}>
          <SvgXml xml={PIN} width={16 * S} height={16 * S} />
        </View>
        <Text allowFontScaling={false} numberOfLines={1} style={[styles.cardTitle, fontsOk && { fontFamily: FONT.semibold }, { left: 38 * S, top: 14 * S, fontSize: 15 * S, lineHeight: 18 * S, width: (K.w - 52) * S }]}>
          {w.name}
        </Text>
        <Text allowFontScaling={false} numberOfLines={2} style={[styles.cardBody, fontsOk && { fontFamily: FONT.regular }, { left: 14 * S, top: 40 * S, fontSize: 13 * S, lineHeight: 19 * S, width: (K.w - 28) * S }]}>
          {w.note}
        </Text>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.panel, overflow: 'hidden' },
  bubble: {
    position: 'absolute',
    left: 0,
    top: 0,
    backgroundColor: C.bubble,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#0A1A30',
    shadowOpacity: 0.16,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
  },
  bubLabel: { color: C.bubbleLabel, fontWeight: '600' },
  bubValue: { color: C.bubbleValue, fontWeight: '700' },
  card: { position: 'absolute', backgroundColor: C.card, overflow: 'hidden', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.07)' },
  cardClip: { position: 'absolute', top: 0, overflow: 'hidden' },
  blade: { position: 'absolute', left: 0, top: 0, backgroundColor: C.card, overflow: 'hidden' },
  bladeLine: { position: 'absolute', top: 0, backgroundColor: 'rgba(255,255,255,0.28)' },
  cardTitle: { position: 'absolute', color: '#FFFFFF', fontWeight: '600' },
  cardBody: { position: 'absolute', color: C.body },
  tabs: { position: 'absolute', left: 0, right: 0, height: 24 },
});
