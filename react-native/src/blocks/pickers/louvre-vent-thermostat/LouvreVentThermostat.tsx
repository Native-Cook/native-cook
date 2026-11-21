import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Easing, type SharedValue, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { BlurMask, Canvas, Group, LinearGradient, Path, Rect, RoundedRect, Shader, Skia, type SkFont, Text as SkText, useFont, vec } from '@shopify/react-native-skia';
import Svg, { Line, SvgXml } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { airOf, glowOf, heatLight, toHex } from './lvt-heat';
import { CHEVRON, CLOCK, DROP, THERMO } from './lvt-icons';
import {
  bladeRow,
  clackStateAt,
  clamp,
  convAt,
  convRate,
  drumOf,
  LOOP,
  mix,
  type Scene,
  sceneAt,
  smooth,
  tAt,
  thetaOf,
  vAt,
} from './lvt-motion';
import { PAGE_FX, VENT_FX } from './lvt-shaders';
import { C, FONT, G, SPRING, T_MAX, T_MIN, T_REST } from './lvt-theme';

/** Skia runtime effects are read as Display P3 on iOS: re-encode the sRGB colours there. */
const P3 = 1;
const PER_C = G.perC;
const HIST = 48;
const LABELS = [16, 18, 20, 22, 24, 26, 28, 30, 32];

type Props = {
  /** Plays the 9 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
  /** Called on every 0.5 °C step the line crosses. */
  onChange?: (v: number) => void;
  /** Called when the vent seats on a value. */
  onCommit?: (v: number) => void;
};

type Hist = { t: number[]; T: number[]; v: number[] };

/**
 * The room screen of a heating app, Stoke: the temperature picker is a louvred vent you drag past a fixed red line.
 * The blades open with the value and with the speed of the drag, and the heat behind them pours through the gaps.
 */
export function LouvreVentThermostat({ autoPlay = true, onChange, onCommit }: Props) {
  const reduced = useReducedMotionPreference();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsOk] = useFonts({ [FONT.mono]: require('./assets/fonts/MartianMono-Regular.ttf') });
  const numFont = useFont(require('./assets/fonts/BigShouldersDisplay-Black.ttf'), G.num.size);
  const unitFont = useFont(require('./assets/fonts/BigShouldersDisplay-ExtraBold.ttf'), G.num.unitSize);
  const monoFont = useFont(require('./assets/fonts/MartianMono-Regular.ttf'), 13);

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = Math.min(W / 402, H / (G.readY + 35));
  const offsetX = (W - 402 * S) / 2;
  // the design is 874 pt tall: on a taller screen it is centred, on a shorter one it starts at the top
  const oyC = Math.max(0, (H / S - 874) / 2);

  // ---- clocks ----
  const clock = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const red = useSharedValue(reduced ? 1 : 0);
  const convOff = useSharedValue(0);
  const noiseOff = useSharedValue(0);

  // ---- the live picker ----
  const val = useSharedValue(T_REST);
  const prevT = useSharedValue(T_REST);
  const vs = useSharedValue(0); // content speed, low-passed 60 ms (pt/s)
  const hist = useSharedValue<Hist>({ t: [0], T: [T_REST], v: [0] });
  const heatL = useSharedValue(T_REST);
  const convL = useSharedValue(0);
  const T0 = useSharedValue(T_REST);
  const y0 = useSharedValue(0);
  const thumbY = useSharedValue<number>(G.lineY);
  const press = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(-10);
  const armed = useSharedValue(0);
  const tgt = useSharedValue(T_REST);
  const lastDir = useSharedValue(1);
  const clackT0 = useSharedValue(-10);
  const clackDir = useSharedValue(1);
  const lastB = useSharedValue(16);
  const lastW = useSharedValue(24);
  const lastTick = useSharedValue(-10);
  const wholeAt = useSharedValue(-10);
  const crossings = useSharedValue<number[]>([]); // pairs: level, time
  const homing = useSharedValue(0);
  const slam = useSharedValue(0);
  const atLimit = useSharedValue(0);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
    red.set(reduced ? 1 : 0);
  }, [autoPlay, reduced, auto, red]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __lvt?: unknown };
    g.__lvt = {
      at: (t: number) => {
        pinned.set(1);
        auto.set(1);
        convOff.set(0);
        noiseOff.set(0);
        clock.set(t);
      },
      play: () => {
        pinned.set(0);
        auto.set(1);
      },
      rate: (r: number) => {
        rate.set(r);
      },
      state: () => ({ t: clock.get(), auto: auto.get(), pinned: pinned.get(), T: val.get(), v: vs.get(), armed: armed.get(), homing: homing.get() }),
    };
    return () => {
      delete g.__lvt;
    };
  }, [clock, auto, pinned, rate, val, vs, armed, homing, convOff, noiseOff]);

  const buzz = (k: 'soft' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  };
  const step = (v: number) => onChange?.(v);
  const commit = (v: number) => onCommit?.(v);

  /** Back to the loop: the vent is at rest on 24.0, which is the loop's first frame. */
  const startLoop = () => {
    'worklet';
    convOff.set(convL.get() - convAt(0));
    noiseOff.set(0);
    clock.set(0);
    homing.set(0);
    auto.set(1);
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    const n = now.get() + dt;
    now.set(n);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      clock.set((clock.get() + dt) % LOOP);
      return;
    }
    // ---- live ----
    const T = val.get();
    const vRaw = dt > 0 ? ((T - prevT.get()) / dt) * PER_C : 0;
    prevT.set(T);
    const v = vs.get() + (vRaw - vs.get()) * (1 - Math.exp(-dt / 0.06));
    vs.set(v);
    const h = hist.get();
    const ht = h.t.concat(n);
    const hT = h.T.concat(T);
    const hv = h.v.concat(v);
    if (ht.length > HIST) {
      ht.shift();
      hT.shift();
      hv.shift();
    }
    hist.set({ t: ht, T: hT, v: hv });
    heatL.set(heatL.get() + (T - heatL.get()) * (1 - Math.exp(-dt / (red.get() ? 0.07 : 0.25))));
    if (!red.get()) convL.set(convL.get() + convRate(heatL.get()) * dt);

    // blades crossing the line: a selection tick each (at most 10 a second; faster, only whole degrees)
    const b = Math.floor(2 * (T - 16));
    if (b !== lastB.get()) {
      const lb = lastB.get();
      const list = crossings.get().slice(-12);
      const steps = Math.min(Math.abs(b - lb), 6);
      for (let i = 0; i < steps; i++) {
        const lev = b > lb ? lb + 1 + i : lb - i;
        list.push(16 + lev / 2, n);
      }
      crossings.set(list);
      lastB.set(b);
      const whole = Math.floor(T);
      const isWhole = whole !== lastW.get();
      if (isWhole) {
        lastW.set(whole);
        wholeAt.set(n);
      }
      if (n - lastTick.get() >= 0.1 || (isWhole && n - lastTick.get() >= 0.05)) {
        lastTick.set(n);
        scheduleOnRN(buzz, 'sel');
      }
      scheduleOnRN(step, Math.round(2 * T) / 2);
    }

    // the clack, once the vent has come to rest
    if (touching.get() === 0 && armed.get() === 1 && Math.abs(v) < 40 && Math.abs(T - tgt.get()) < 0.02) {
      armed.set(0);
      if (!red.get()) {
        clackT0.set(n);
        clackDir.set(lastDir.get());
        scheduleOnRN(buzz, 'rigid');
      }
      scheduleOnRN(commit, tgt.get());
    }

    // 2 s after the last touch: glide home to 24.0, clack, then the loop takes over again
    if (autoPlay && !red.get() && touching.get() === 0 && armed.get() === 0 && n - lastTouch.get() > 2 && n - clackT0.get() > 0.6) {
      if (Math.abs(T - T_REST) > 0.002 && homing.get() === 0) {
        homing.set(1);
        tgt.set(T_REST);
        lastDir.set(T_REST > T ? 1 : -1);
        armed.set(1);
        val.set(withTiming(T_REST, { duration: 900, easing: Easing.inOut(Easing.cubic) }));
      } else if (Math.abs(T - T_REST) <= 0.002) {
        startLoop();
      }
    }
  });

  // ---- the frame being drawn ----
  const scene = useDerivedValue<Scene>(() => {
    if (auto.get() === 1) return sceneAt(clock.get());
    const n = now.get();
    const T = val.get();
    const h = hist.get();
    const sample = (tq: number) => {
      'worklet';
      const ts = h.t;
      for (let i = ts.length - 1; i >= 0; i--) {
        if (ts[i] <= tq) {
          if (i === ts.length - 1) return [h.T[i], h.v[i]];
          const k = (tq - ts[i]) / Math.max(ts[i + 1] - ts[i], 1e-4);
          return [mix(h.T[i], h.T[i + 1], k), mix(h.v[i], h.v[i + 1], k)];
        }
      }
      return [h.T[0], h.v[0]];
    };
    const isRed = red.get() === 1;
    const cr = crossings.get();
    const row = bladeRow(
      T,
      (dk) => {
        'worklet';
        if (isRed) return thetaOf(T, 0);
        const s = sample(n - 0.016 * dk);
        return thetaOf(s[0], s[1]);
      },
      (_k, L) => {
        'worklet';
        if (isRed) return 0;
        let g = 0;
        for (let i = 0; i + 1 < cr.length; i += 2) {
          if (Math.abs(cr[i] - L) < 0.01) g = Math.max(g, clamp(1 - (n - cr[i + 1]) / 0.08, 0, 1));
        }
        return g;
      },
      thumbY.get(),
      isRed ? 0 : press.get(),
      n - clackT0.get(),
      clackDir.get(),
      isRed ? 0 : slam.get(),
    );
    const dw = n - wholeAt.get();
    const nudge = dw < 0.15 ? 2 * (1 - dw / 0.15) * (1 - dw / 0.15) * (1 - smooth(150, 420, Math.abs(vs.get()))) : 0;
    const d0 = drumOf(T);
    return {
      T,
      v: vs.get(),
      s0: row.s0,
      k0: row.k0,
      bl: row.bl,
      heat: heatL.get(),
      conv: convL.get(),
      nudge,
      breath: 0,
      time: n,
      drum: [d0.tenths, d0.units, d0.tens],
    };
  });

  // ---- uniforms ----
  const fr = G.frame;
  const vn = G.vent;
  const ventU = useDerivedValue(() => {
    const sc = scene.get();
    const hl = heatLight(sc.heat);
    const boost = 1 + 0.25 * Math.abs(Math.tanh(sc.v / 260));
    const time = auto.get() === 1 ? sc.time + noiseOff.get() : sc.time;
    const pilot = 0.2 + 0.15 * (0.5 + 0.5 * Math.sin((2 * Math.PI * time) / 1.5));
    return {
      uP3: P3,
      uFrame: [fr.x, fr.y, fr.w, fr.h],
      uVent: [vn.x, vn.y, vn.w, vn.h],
      uLineY: G.lineY,
      uS0: sc.s0,
      uK0: sc.k0,
      uBl: sc.bl,
      uHeat: hl.rgb,
      uI: hl.i * boost,
      uElem: hl.elem,
      uCore: hl.core,
      uBarOff: 0.55 * (sc.T - 24) * PER_C,
      uPh: (2 * Math.PI * time) / LOOP,
      uPilot: pilot,
    };
  });
  const pageU = useDerivedValue(() => {
    const sc = scene.get();
    const gl = glowOf(sc.heat);
    const air = airOf(sc.heat);
    const hl = heatLight(sc.heat);
    const conv = auto.get() === 1 ? sc.conv + convOff.get() : sc.conv;
    const k = 0.22 * hl.i;
    return {
      uP3: P3,
      uEdgeL: air.edgeL,
      uEdgeR: air.edgeR,
      uGlowH: gl.h + sc.breath,
      uG0: gl.g0,
      uG1: gl.g1,
      uG2: gl.g2,
      uGA: gl.a,
      uHalo: [hl.rgb[0] * k, hl.rgb[1] * k, hl.rgb[2] * k],
      uFrame: [fr.x, fr.y, fr.w, fr.h],
      uConv: conv - Math.floor(conv),
      uDot: air.dot,
      uDotA: air.dotA,
      uDotAmp: red.get() ? 0 : air.amp,
    };
  });

  // ---- numeral ----
  const metrics = useMemo(() => {
    if (!numFont) return null;
    const widths = Array.from({ length: 10 }, (_, d) => numFont.measureText(String(d)).width);
    const cell = Math.max(...widths) + 2;
    const dotW = numFont.measureText('.').width;
    const total = 3 * cell + dotW - 2;
    const x0 = 201 - total / 2;
    return { widths, w05: [widths[0], widths[5]], cell, dotW, x0, total };
  }, [numFont]);
  const baseline = G.num.capTop + G.num.cap;
  const numPaint = useDerivedValue(() => {
    const sc = scene.get();
    const hl = heatLight(sc.heat);
    // the numeral is under-lit by the vent: the line's blade openness × the chamber, α ≤ 0.5
    const i = Math.floor(sc.s0 - sc.k0 + 0.5);
    const th = sc.bl[2 * Math.min(Math.max(i, 0), 25)];
    const open = Math.abs(Math.sin((th * Math.PI) / 180));
    const a = Math.min(0.5, hl.i * (0.25 + 0.6 * open) * 0.6);
    const ink = [0.984, 0.965, 0.949];
    const lit = [mix(ink[0], hl.rgb[0], a), mix(ink[1], hl.rgb[1], a), mix(ink[2], hl.rgb[2], a)];
    return [C.ink, C.ink, toHex(lit as [number, number, number])];
  });
  const tenthsPos = useDerivedValue(() => scene.get().drum[0]);
  const unitsPos = useDerivedValue(() => scene.get().drum[1]);
  const tensPos = useDerivedValue(() => scene.get().drum[2]);

  // ---- line, triangle ----
  const triPath = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    b.moveTo(0, -G.tri.h / 2);
    b.lineTo(G.tri.w, 0);
    b.lineTo(0, G.tri.h / 2);
    b.close();
    return b.detach();
  }, []);
  const triT = useDerivedValue(() => [{ translateX: G.tri.x - scene.get().nudge }, { translateY: G.lineY }]);

  // ---- touch ----
  const takeOver = () => {
    'worklet';
    if (auto.get() === 1) {
      const t = clock.get();
      const n = now.get();
      const sc = sceneAt(t);
      val.set(sc.T);
      prevT.set(sc.T);
      vs.set(sc.v);
      heatL.set(sc.heat);
      convL.set(sc.conv + convOff.get());
      const ts: number[] = [];
      const Ts: number[] = [];
      const vv: number[] = [];
      for (let i = 0; i < 16; i++) {
        const back = (15 - i) * 0.02;
        ts.push(n - back);
        Ts.push(tAt(t - back));
        vv.push(vAt(t - back));
      }
      hist.set({ t: ts, T: Ts, v: vv });
      const cs = clackStateAt(t);
      clackT0.set(n - cs.clackT);
      clackDir.set(cs.clackDir);
      lastB.set(Math.floor(2 * (sc.T - 16)));
      lastW.set(Math.floor(sc.T));
      crossings.set([]);
      auto.set(0);
    }
    pinned.set(0);
    homing.set(0);
    touching.set(1);
    lastTouch.set(now.get());
  };
  const canvasY = (y: number) => {
    'worklet';
    return y / S - oyC;
  };
  const glideTo = (target: number, velocity: number) => {
    'worklet';
    tgt.set(target);
    lastDir.set(target >= val.get() ? 1 : -1);
    armed.set(1);
    val.set(withSpring(target, { damping: SPRING.fling.d, stiffness: SPRING.fling.k, velocity }));
  };

  // dev only: drive the live picker the way a finger would (touch injection is not available on every simulator)
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __lvt?: Record<string, unknown> };
    if (!g.__lvt) return;
    g.__lvt.drag = (to: number, ms: number, y = G.lineY) => {
      takeOver();
      armed.set(0);
      thumbY.set(y);
      press.set(withTiming(1, { duration: 110 }));
      val.set(withTiming(to, { duration: ms, easing: Easing.inOut(Easing.cubic) }));
      setTimeout(() => {
        press.set(withTiming(0, { duration: 90 }));
        touching.set(0);
        lastTouch.set(now.get());
        glideTo(clamp(Math.round(2 * to) / 2, T_MIN, T_MAX), 0);
      }, ms + 30);
    };
  });

  const pan = Gesture.Pan()
    .activeOffsetY([-4, 4])
    .onBegin((e) => {
      takeOver();
      val.set(val.get()); // a touch stops any glide at once
      armed.set(0);
      thumbY.set(canvasY(e.y));
      if (!red.get()) {
        press.set(withTiming(1, { duration: 110, easing: Easing.out(Easing.cubic) }));
        scheduleOnRN(buzz, 'soft');
      }
    })
    .onStart(() => {
      T0.set(val.get());
      y0.set(thumbY.get());
    })
    .onUpdate((e) => {
      let T = T0.get() + e.translationY / S / PER_C;
      // rubber band past 16 / 32: over / (1 + over / 120), in pt
      if (T > T_MAX || T < T_MIN) {
        const lim = T > T_MAX ? T_MAX : T_MIN;
        const over = Math.abs(T - lim) * PER_C;
        T = lim + (Math.sign(T - lim) * (over / (1 + over / 120))) / PER_C;
        if (atLimit.get() === 0) {
          atLimit.set(1);
          if (!red.get()) slam.set(withSpring(lim === T_MAX ? 1 : -1, { damping: 18, stiffness: 520 }));
          scheduleOnRN(buzz, 'rigid');
        }
      } else if (atLimit.get() === 1) {
        atLimit.set(0);
        slam.set(withSpring(0, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
      }
      val.set(T);
      thumbY.set(y0.get() + e.translationY / S);
    })
    .onEnd((e) => {
      const v = e.velocityY / S / PER_C; // °C/s
      // decay at 0.997 a ms: the projected end, rounded to the half degree
      const target = clamp(Math.round(2 * (val.get() + v * 0.332)) / 2, T_MIN, T_MAX);
      glideTo(target, v);
    })
    .onFinalize(() => {
      if (atLimit.get() === 1) {
        atLimit.set(0);
        slam.set(withSpring(0, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
      }
      press.set(withTiming(0, { duration: 90 }));
      touching.set(0);
      lastTouch.set(now.get());
      const target = clamp(Math.round(2 * val.get()) / 2, T_MIN, T_MAX);
      if (armed.get() === 0 && Math.abs(val.get() - target) > 0.001) glideTo(target, 0);
    });

  // tap a scale label: glide to that temperature, the blades ripple on the way
  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e, ok) => {
      if (!ok) return;
      const x = (e.x - offsetX) / S;
      const y = canvasY(e.y);
      if (x > G.labelX - 10 && x < G.labelX + 44 && y > G.frame.y && y < G.frame.y + G.frame.h) {
        takeOver();
        touching.set(0);
        const at = val.get() + (G.lineY - y) / PER_C;
        const target = clamp(2 * Math.round(at / 2), T_MIN, T_MAX);
        tgt.set(target);
        lastDir.set(target >= val.get() ? 1 : -1);
        armed.set(1);
        val.set(withTiming(target, { duration: 620, easing: Easing.inOut(Easing.cubic) }));
      }
    });

  const gesture = Gesture.Exclusive(pan, tap);
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const mono = fontsOk ? { fontFamily: FONT.mono } : null;
  const ready = size && numFont && unitFont && monoFont && metrics;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout} accessibilityLabel="Set temperature" accessibilityRole="adjustable">
        <StatusBar style="light" />
        {ready && (
          <Canvas style={[StyleSheet.absoluteFill, { width: W, height: H }]} pointerEvents="none">
            <Group transform={[{ translateX: offsetX }, { scale: S }, { translateY: oyC }]}>
              <Rect x={-offsetX / S} y={-oyC} width={W / S} height={H / S}>
                <Shader source={PAGE_FX} uniforms={pageU} />
              </Rect>
              <Rect x={fr.x - 2} y={fr.y - 2} width={fr.w + 4} height={fr.h + 4}>
                <Shader source={VENT_FX} uniforms={ventU} />
              </Rect>

              {/* the set-point line: a soft red bloom under a 2.5 pt line, the marker at its left end */}
              <Rect x={G.line.x0} y={G.lineY - 2.5} width={G.line.x1 - G.line.x0} height={5} color={C.red} opacity={0.55}>
                <BlurMask blur={4} style="normal" />
              </Rect>
              <RoundedRect x={G.line.x0 - 1} y={G.lineY - G.line.w / 2} width={G.line.x1 - G.line.x0 + 1} height={G.line.w} r={G.line.w / 2} color={C.red} />
              <Group transform={triT}>
                <Path path={triPath} color={C.red} />
                <Path path={triPath} color={C.red} style="stroke" strokeWidth={1} strokeJoin="round" />
              </Group>

              {/* the scale, scrolling with the blades; only the label at the line is red */}
              <Group clip={Skia.XYWHRect(G.labelX - 4, G.frame.y + 6, 60, G.frame.h - 12)}>
                {LABELS.map((v) => (
                  <ScaleLabel key={v} v={v} scene={scene} font={monoFont!} />
                ))}
              </Group>

              {/* the numeral: four masked drums in fixed-width cells, under-lit by the vent */}
              <Drum pos={tensPos} glyphs={DIGITS} font={numFont!} widths={metrics!.widths} x={metrics!.x0} cell={metrics!.cell} base={baseline} paint={numPaint} />
              <Drum pos={unitsPos} glyphs={DIGITS} font={numFont!} widths={metrics!.widths} x={metrics!.x0 + metrics!.cell} cell={metrics!.cell} base={baseline} paint={numPaint} />
              <SkText x={metrics!.x0 + 2 * metrics!.cell - 1} y={baseline} text="." font={numFont!}>
                <LinearGradient start={vec(0, G.num.capTop)} end={vec(0, baseline)} positions={[0, 0.58, 1]} colors={numPaint} />
              </SkText>
              <Drum pos={tenthsPos} glyphs={HALVES} font={numFont!} widths={metrics!.w05} x={metrics!.x0 + 2 * metrics!.cell + metrics!.dotW - 2} cell={metrics!.cell} base={baseline} paint={numPaint} />
              <SkText x={metrics!.x0 + metrics!.total + 7} y={G.num.capTop + G.num.unitSize * 0.8} text="°C" font={unitFont!} color={C.unit} />
            </Group>
          </Canvas>
        )}

        {/* unit picker, read-outs (static copy: plain RN text) */}
        <View pointerEvents="none" style={[styles.mode, { top: (G.modeY + oyC) * S - 11 * S }]}>
          <View>
            <Text allowFontScaling={false} style={[styles.modeText, mono, { fontSize: 13 * S, lineHeight: 18 * S }]}>
              Celsius
            </Text>
            <Dots w={58 * S} color={C.mute} />
          </View>
          <SvgXml xml={CHEVRON} width={15 * S} height={15 * S} style={{ marginLeft: 3 * S, marginTop: -3 * S }} />
        </View>
        <View pointerEvents="none" style={[styles.reads, { top: (G.readY + oyC) * S - 11 * S, left: offsetX, right: offsetX, paddingHorizontal: 44 * S }]}>
          <View style={styles.read}>
            <SvgXml xml={DROP} width={16 * S} height={16 * S} />
            <Text allowFontScaling={false} style={[styles.readA, mono, { fontSize: 13 * S, marginLeft: 8 * S }]}>
              67%
            </Text>
          </View>
          <View style={styles.read}>
            <SvgXml xml={CLOCK} width={16 * S} height={16 * S} />
            <View style={{ marginLeft: 8 * S }}>
              <Text allowFontScaling={false} style={[styles.readB, mono, { fontSize: 13 * S, lineHeight: 18 * S }]}>
                Schedule
              </Text>
              <Dots w={66 * S} color={C.readA} />
            </View>
          </View>
          <View style={styles.read}>
            <SvgXml xml={THERMO} width={16 * S} height={16 * S} />
            <Text allowFontScaling={false} style={[styles.readA, mono, { fontSize: 13 * S, marginLeft: 8 * S }]}>
              22°C
            </Text>
          </View>
        </View>
      </View>
    </GestureDetector>
  );
}

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
const HALVES = ['0', '5'];
/** How far one digit rolls (pt): well past the cell's cap box (round digits overshoot it by ~1 pt), so a digit at
 * rest never shows a sliver of its neighbours and only the rolling pair is ever in view. */
const ROLL = G.num.cap + 10;

type DrumProps = {
  pos: SharedValue<number>;
  glyphs: string[];
  font: SkFont;
  widths: number[];
  x: number;
  cell: number;
  base: number;
  paint: SharedValue<string[]>;
};

/**
 * One rolling digit: a masked drum in a fixed-width cell. As the value rises the outgoing digit slides down and the
 * next comes in from above, the way the scale moves past the line; every frame shows whole glyphs, never a smear.
 */
function Drum({ pos, glyphs, font, widths, x, cell, base, paint }: DrumProps) {
  const n = glyphs.length;
  const a = useDerivedValue(() => {
    const f = Math.floor(pos.get());
    return ((f % n) + n) % n;
  });
  const fr = useDerivedValue(() => pos.get() - Math.floor(pos.get()));
  const ta = useDerivedValue(() => glyphs[a.get()]);
  const tb = useDerivedValue(() => glyphs[(a.get() + 1) % n]);
  const xa = useDerivedValue(() => x + (cell - widths[a.get()]) / 2);
  const xb = useDerivedValue(() => x + (cell - widths[(a.get() + 1) % n]) / 2);
  const ya = useDerivedValue(() => base + fr.get() * ROLL);
  const yb = useDerivedValue(() => base + fr.get() * ROLL - ROLL);
  // the cell: its fixed width, the numeral's cap box + 1.5 pt (the round digits' overshoot); a rolling digit is never seen outside it
  const clip = Skia.XYWHRect(x, G.num.capTop - 1.5, cell, G.num.cap + 3);
  return (
    <Group clip={clip}>
      <SkText x={xa} y={ya} text={ta} font={font}>
        <LinearGradient start={vec(0, G.num.capTop)} end={vec(0, base)} positions={[0, 0.58, 1]} colors={paint} />
      </SkText>
      <SkText x={xb} y={yb} text={tb} font={font}>
        <LinearGradient start={vec(0, G.num.capTop)} end={vec(0, base)} positions={[0, 0.58, 1]} colors={paint} />
      </SkText>
    </Group>
  );
}

/** A scale label: scrolls with the blades, red only at the line, dimmer the farther it is from it. */
function ScaleLabel({ v, scene, font }: { v: number; scene: SharedValue<Scene>; font: SkFont }) {
  const y = useDerivedValue(() => G.lineY - (v - scene.get().T) * PER_C + 4.7);
  const color = useDerivedValue(() => {
    const d = Math.abs(y.get() - 4.7 - G.lineY);
    const near = [0.718, 0.663, 0.655];
    const far = [0.29, 0.235, 0.227];
    const k = smooth(60, 200, d);
    const base: [number, number, number] = [mix(near[0], far[0], k), mix(near[1], far[1], k), mix(near[2], far[2], k)];
    const r = 1 - smooth(3, 12, d);
    return toHex([mix(base[0], 0.878, r), mix(base[1], 0.271, r), mix(base[2], 0.227, r)]);
  });
  return <SkText x={G.labelX} y={y} text={`${v}°`} font={font} color={color} />;
}

/** A 1 px dotted underline. */
function Dots({ w, color }: { w: number; color: string }) {
  return (
    <Svg width={w} height={3} style={{ marginTop: 1 }}>
      <Line x1={0.5} y1={1.5} x2={w} y2={1.5} stroke={color} strokeOpacity={0.6} strokeWidth={1} strokeDasharray="1 2" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page0, overflow: 'hidden' },
  mode: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center' },
  modeText: { color: C.mute },
  reads: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  read: { flexDirection: 'row', alignItems: 'center' },
  // a soft dark halo keeps the read-outs legible when the glow climbs behind them
  readA: { color: C.readA, textShadowColor: 'rgba(30,6,2,0.55)', textShadowRadius: 6 },
  readB: { color: C.readA, textShadowColor: 'rgba(30,6,2,0.55)', textShadowRadius: 6 },
});
