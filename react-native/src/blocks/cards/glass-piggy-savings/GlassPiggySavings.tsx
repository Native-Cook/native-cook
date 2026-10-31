import {
  BlurStyle,
  Canvas,
  FilterMode,
  MipmapMode,
  Picture,
  type SkCanvas,
  Skia,
  TileMode,
  useImage,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  clamp01,
  COIN,
  easeIn,
  easeInOut,
  easeOut,
  easeOutBack,
  GOAL,
  hash,
  LOOP,
  makePig,
  mix,
  type Pig,
  SAVED,
  seg,
  smooth,
  stepPig,
  swapDip,
  T,
  totalAt,
} from './gps-motion';
import { type Cue, useGpsAudio } from './use-gps-audio';

/*
 Glass Piggy Savings — the savings goal of an invented banking app, kept in a glass piggy bank.

 Look (the pin, a still): an emerald glass piggy bank with a frosted belly, clear glass legs and a
 chrome slot, glowing green on a deep green page, a gold coin standing in its slot.

 Motion (ours): a coin pops off the "+ $20" chip, flips through the air over the pig and drops
 edge-first into the slot; the glass pig squashes and wobbles on a spring, sparks jump out of the
 slot, "+$20" floats up and the total rolls up digit by digit while the goal bar grows. A light
 sweeps the glass. At the end of the loop the total rolls back like an odometer.

 autoPlay: LOOP = 7 s (marks in gps-motion.ts); frame 7 s equals frame 0.
 Touch: drag a coin off any chip and let it go over the pig to drop it in (anywhere else it flies
 back); tap the pig and it wiggles. 2 s after the last touch the loop resumes.
 Reduced motion: still; a dropped coin just updates the total.
*/

let debugState = '';
const PIG_W = 340;
const IMG = { w: 900, h: 800, slotX: 378, slotY: 116, slotW: 161 };
const CHIPS = [1, 5, 20];

type Props = { autoPlay?: boolean; previewTime?: number };

const SHEEN_SKSL = `
uniform shader pig;
uniform float pos; uniform float strength; uniform float w;
half4 main(vec2 xy) {
  half4 c = pig.eval(xy);
  float band = exp(-pow((xy.x + xy.y * 0.55 - pos) / (w * 0.07), 2.0));
  return half4(c.rgb + half3(band * strength) * c.a, c.a);
}`;
const sheenFx = Skia.RuntimeEffect.Make(SHEEN_SKSL);

/** A plain gold coin turning about its vertical axis: `flip` is the cosine of its angle, so the face
 * narrows to an ellipse and the milled edge shows beside it. */
const drawCoin = (c: SkCanvas, x: number, y: number, r: number, flip: number, a: number) => {
  'worklet';
  if (a <= 0.01) return;
  c.save();
  c.translate(x, y);
  const sx = Math.max(0.06, Math.abs(flip));
  const side = Math.sqrt(Math.max(0, 1 - sx * sx)) * r * 0.2 * (flip < 0 ? -1 : 1);
  // the edge: the same disc pushed sideways, darker
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setColor(Skia.Color('#9a6516'));
  edge.setAlphaf(a);
  c.drawOval(Skia.XYWHRect(-r * sx + side, -r, 2 * r * sx, 2 * r), edge);
  if (Math.abs(side) > 0.5) c.drawRect(Skia.XYWHRect(Math.min(0, side), -r, Math.abs(side), 2 * r), edge);
  c.scale(sx, 1);
  const back = flip < 0;
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: -r, y: -r },
      { x: r * 0.7, y: r },
      [Skia.Color('#fff3b8'), Skia.Color('#f0bf45'), Skia.Color('#b57a1d')],
      [0, 0.45, 1],
      0,
    ),
  );
  rim.setAlphaf(a);
  c.drawCircle(0, 0, r, rim);
  const face = Skia.Paint();
  face.setAntiAlias(true);
  face.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: r * 0.8, y: r * 0.8 },
      { x: -r * 0.8, y: -r * 0.8 },
      back ? [Skia.Color('#f2c457'), Skia.Color('#c98f27')] : [Skia.Color('#ffe48f'), Skia.Color('#dca031')],
      null,
      0,
    ),
  );
  face.setAlphaf(a);
  c.drawCircle(0, 0, r * 0.82, face);
  // a fine engraved ring inside the rim
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(1);
  ring.setStrokeWidth(Math.max(0.8, r * 0.05));
  ring.setColor(Skia.Color('#b98222'));
  ring.setAlphaf(0.7 * a);
  c.drawCircle(0, 0, r * 0.66, ring);
  const gl = Skia.Paint();
  gl.setAntiAlias(true);
  gl.setColor(Skia.Color('#ffffff'));
  gl.setAlphaf(0.45 * a);
  gl.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, r * 0.06, true));
  c.drawOval(Skia.XYWHRect(-r * 0.62, -r * 0.74, r * 0.62, r * 0.3), gl);
  c.restore();
};

/** "$1,256" without Intl (the UI runtime may not have it). */
const money = (v: number) => {
  'worklet';
  const n = Math.round(v);
  const s = `${n}`;
  let out = '';
  for (let i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 === 0) out += ',';
    out += s[i];
  }
  return `$${out}`;
};

export function GlassPiggySavings({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const pigImg = useImage(require('./assets/pig.png'));
  useFonts({
    'GPS-Black': require('./assets/fonts/Nunito-Black.ttf'),
    'GPS-ExtraBold': require('./assets/fonts/Nunito-ExtraBold.ttf'),
    'GPS-SemiBold': require('./assets/fonts/Nunito-SemiBold.ttf'),
  });
  const [plusAmt, setPlusAmt] = useState(COIN);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const sc = PIG_W / IMG.w;
  const pigX = w / 2 - PIG_W / 2 + 6;
  const pigY = insets.top + 250;
  const slotX = pigX + IMG.slotX * sc;
  const slotY = pigY + IMG.slotY * sc;
  const chipY = h - insets.bottom - 92;
  const chipX = (i: number) => {
    'worklet';
    return w / 2 + (i - 1) * 112;
  };

  const play = useGpsAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(previewTime ?? 0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const pig = useSharedValue<Pig>(makePig());
  const tick = useSharedValue(0);
  // a finger's coin: x, y while held; drop clock; amount
  const held = useSharedValue({ on: 0, x: 0, y: 0, chip: 2, dropAt: -10, fromX: 0, fromY: 0, backAt: -10 });
  const bonus = useSharedValue(0); // dollars a finger added on top of the loop
  const bonusAt = useSharedValue(-10);
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __glassPiggy?: unknown };
    g.__glassPiggy = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__glassPiggy;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const p = pig.get();
    const dbg = debugT.get();
    let t = time.get();
    const touched = held.get().on === 1 || now - last.get() < 2;
    if (dbg >= 0) t = dbg;
    else if (auto && !touched) {
      const before = t;
      t += dt;
      if (t >= LOOP) t -= LOOP;
      if (before < T.seat[1] && t >= T.seat[1]) p.sv -= 0.7;
      if (before < T.land && t >= T.land) {
        p.sv -= 2.2;
        scheduleOnRN(setPlusAmt, COIN);
      }
    }
    time.set(t);
    // a finger's coin lands
    const hd = held.get();
    if (hd.dropAt > 0 && now - hd.dropAt >= 0.23 && now - hd.dropAt - dt < 0.23) {
      p.sv -= 2.2;
      bonus.set(bonus.get() + CHIPS[hd.chip]);
      bonusAt.set(now);
      scheduleOnRN(setPlusAmt, CHIPS[hd.chip]);
      scheduleOnRN(cue, 'clink');
      scheduleOnRN(buzz, 2);
    }
    stepPig(p, dt);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} sq=${p.sq.toFixed(3)} bonus=${bonus.get()}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && !reduced && size !== null && previewTime === undefined);
    return () => frameCb.setActive(false);
  }, [active, reduced, size, previewTime, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    c.drawColor(Skia.Color('#03110b'));
    const t = time.get();
    const now = clock.get();
    const p = pig.get();
    const hd = held.get();
    // glow behind the pig
    const glow = Skia.Paint();
    const pulse = Math.exp(-Math.max(0, t - T.land) * 3) * (t >= T.land ? 1 : 0);
    glow.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: w / 2, y: pigY + 140 },
        330,
        [Skia.Color('rgba(32,215,128,0.78)'), Skia.Color('rgba(14,130,78,0.42)'), Skia.Color('rgba(5,40,24,0.12)'), Skia.Color('rgba(3,17,11,0)')],
        [0, 0.38, 0.72, 1],
        0,
      ),
    );
    glow.setAlphaf(0.8 + 0.2 * pulse);
    c.drawRect(Skia.XYWHRect(0, 0, w, h), glow);
    // floor shadow
    const sh = Skia.Paint();
    sh.setColor(Skia.Color('#000000'));
    sh.setAlphaf(0.5);
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
    c.drawOval(Skia.XYWHRect(pigX + 50, pigY + 290, PIG_W - 90, 26), sh);

    // the coins in flight are drawn behind the pig's front once they reach the slot
    let coinX = 0;
    let coinY = 0;
    let coinA = 0;
    let flip = 1;
    let coinR = 30;
    let clipY = -1;
    if (hd.on === 1) {
      coinX = hd.x;
      coinY = hd.y;
      coinA = 1;
      flip = Math.cos(now * 9);
    } else if (hd.dropAt > 0 && now - hd.dropAt < 0.3) {
      const k = easeIn(seg(now - hd.dropAt, 0, 0.23));
      coinX = mix(hd.fromX, slotX, Math.min(1, k * 1.6));
      coinY = mix(hd.fromY, slotY + 30, k);
      coinR = mix(30, 23, k);
      coinA = 1;
      flip = 1;
      clipY = slotY + 2;
    } else if (hd.backAt > 0 && now - hd.backAt < 0.45) {
      const k = easeOutBack(seg(now - hd.backAt, 0, 0.45));
      coinX = mix(hd.fromX, chipX(hd.chip), k);
      coinY = mix(hd.fromY, chipY, k);
      coinA = 1 - smooth(seg(now - hd.backAt, 0.3, 0.45));
      flip = Math.cos((now - hd.backAt) * 14);
    } else if (t >= T.pop && t < T.sink[1]) {
      // autoplay: pop off the $20 chip, toss up past the pig's shoulder, over the top, into the slot.
      // The coin starts near the camera (bigger, in front of the pig) and shrinks as it flies away.
      const sx = chipX(2);
      const sy = chipY;
      const pop = easeOutBack(seg(t, T.pop, T.pop + 0.16));
      const k = seg(t, T.arc[0], T.arc[1]);
      const kx = easeInOut(k);
      const rise = easeOut(seg(k, 0, 0.62));
      const fall = easeIn(seg(k, 0.62, 1));
      const ax = mix(sx, slotX, kx);
      const ay = mix(sy - 18 * pop, slotY - 118, rise) + fall * 78;
      // seat: drops the last 50 pt and stops half in the slot, then sinks
      const seat = easeOutBack(seg(t, T.seat[0], T.seat[1]), 1.2);
      const sink = easeIn(seg(t, T.sink[0], T.sink[1]));
      coinX = ax;
      coinY = ay + seat * 34 + sink * 44;
      coinR = mix(30, 23, Math.min(1, k * 1.3));
      coinA = Math.min(1, pop * 1.4);
      flip = t < T.arc[1] ? Math.cos(k * Math.PI * 6) : 1;
      if (t >= T.seat[0]) clipY = slotY + 1;
    }

    // the pig, squashing on its feet and wiggling round its middle
    const sq = p.sq;
    c.save();
    const footX = pigX + PIG_W / 2;
    const footY = pigY + IMG.h * sc - 20;
    c.translate(footX, footY);
    c.rotate(p.wob * 6, 0, -120);
    c.scale(1 + sq * 0.06, 1 - sq * 0.08);
    const breathe = 1 + Math.sin((2 * Math.PI * 2 * t) / LOOP) * 0.006;
    c.scale(breathe, breathe);
    c.translate(-footX, -footY);
    if (pigImg && sheenFx) {
      const m = Skia.Matrix();
      m.translate(pigX, pigY);
      m.scale(sc, sc);
      const shader = pigImg.makeShaderOptions(TileMode.Decal, TileMode.Decal, FilterMode.Linear, MipmapMode.None, m);
      const sweep = seg(t, T.land + 0.1, T.land + 1.2);
      const pos = mix(pigX - 200, pigX + PIG_W + 300, sweep);
      const pp = Skia.Paint();
      pp.setShader(sheenFx.makeShaderWithChildren([pos, sweep > 0 && sweep < 1 ? 0.22 * Math.sin(sweep * Math.PI) : 0, PIG_W], [shader]));
      c.drawRect(Skia.XYWHRect(pigX, pigY, PIG_W, IMG.h * sc), pp);
    }
    if (coinA > 0 && clipY > 0) {
      // the coin in the slot, drawn over the glass and cut along the slot's front lip, which runs
      // down to the right like the slot itself (measured on pig.png: 0.2 pt per pt)
      const lip = Skia.PathBuilder.Make();
      const y0 = slotY + 6 * sc * 2.6;
      lip.moveTo(slotX - 80, -50);
      lip.lineTo(slotX + 80, -50);
      lip.lineTo(slotX + 80, y0 + 0.2 * 80);
      lip.lineTo(slotX - 80, y0 - 0.2 * 80);
      lip.close();
      c.save();
      c.clipPath(lip.build(), 1, true);
      drawCoin(c, coinX, coinY, coinR, flip, coinA);
      c.restore();
    }
    c.restore();
    if (coinA > 0 && clipY < 0) drawCoin(c, coinX, coinY, coinR, flip, coinA);

    // sparks out of the slot when a coin lands
    const since = Math.max(t >= T.land ? t - T.land : -1, hd.dropAt > 0 ? now - hd.dropAt - 0.23 : -1);
    if (since >= 0 && since < 1) {
      for (let i = 0; i < 16; i++) {
        const a = -Math.PI / 2 + (hash(i * 3.3) - 0.5) * 2.4;
        const v = 70 + hash(i * 1.7) * 110;
        const d = (1 - Math.exp(-since * 4)) / 4;
        const x = slotX + Math.cos(a) * v * d;
        const y = slotY + Math.sin(a) * v * d + 80 * since * since;
        const al = 1 - smooth(seg(since, 0.35, 0.9));
        const sp = Skia.Paint();
        sp.setAntiAlias(true);
        sp.setColor(Skia.Color(i % 3 === 0 ? '#fff4c2' : '#ffd25c'));
        sp.setAlphaf(al);
        c.drawCircle(x, y, 1.4 + hash(i) * 1.6, sp);
      }
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, pigImg, pigX, pigY, slotX, slotY, chipY]);

  // ---------------------------------------------------------------- the total and the goal
  const total = useDerivedValue(() => {
    tick.get();
    return totalAt(time.get()) + bonus.get();
  });
  const totalText = useDerivedValue(() => {
    const v = total.get();
    return money(v);
  });
  const barStyle = useAnimatedStyle(() => ({ width: `${clamp01(total.get() / GOAL) * 100}%` }));
  const plusStyle = useAnimatedStyle(() => {
    const t = time.get();
    const a = t >= T.float[0] && t < T.float[1] ? seg(t, T.float[0], T.float[1]) : -1;
    const fb = clock.get() - bonusAt.get();
    const b = fb >= 0 && fb < 1.1 ? fb / 1.1 : -1;
    const k = Math.max(a, b);
    if (k < 0) return { opacity: 0 };
    return { opacity: Math.sin(Math.min(k * 1.4, 1) * Math.PI), transform: [{ translateY: -easeOut(k) * 60 }] };
  });

  // a receipt slides up after a coin lands (as a savings app confirms a deposit), then away
  const receiptStyle = useAnimatedStyle(() => {
    const t = time.get();
    const a = t >= T.land + 0.1 && t < T.land + 2.4 ? t - T.land - 0.1 : -1;
    const fb = clock.get() - bonusAt.get() - 0.05;
    const b = fb >= 0 && fb < 2.3 ? fb : -1;
    const k = Math.max(a, b);
    if (k < 0) return { opacity: 0, transform: [{ translateY: 90 }] };
    const inK = easeOutBack(k / 0.35);
    const outK = easeIn((k - 1.9) / 0.4);
    return { opacity: 1 - outK, transform: [{ translateY: (1 - inK) * 90 + outK * 40 }] };
  });
  const totalStyle = useAnimatedStyle(() => {
    const d = swapDip(time.get());
    return { opacity: 1 - d, transform: [{ translateY: d * 6 }] };
  });
  /** A chip's coin is gone while it is in the air and grows back once it has landed. */
  const dotStyle = (i: number) => {
    'worklet';
    const t = time.get();
    const hd = held.get();
    const now = clock.get();
    let s = 1;
    if (i === 2) {
      if (t >= T.pop && t < T.land) s = 0;
      else if (t >= T.land) s = easeOutBack(seg(t, T.land + 0.25, T.land + 0.55));
    }
    if (hd.chip === i) {
      if (hd.on === 1) s = 0;
      else {
        const since = now - Math.max(hd.dropAt + 0.23, hd.backAt + 0.45);
        if (since > -0.5 && since < 0.35) s = since < 0 ? 0 : Math.min(s, easeOutBack(since / 0.3));
      }
    }
    return { transform: [{ scale: Math.max(0, s) }] };
  };
  const dot0 = useAnimatedStyle(() => {
    time.get();
    held.get();
    clock.get();
    return dotStyle(0);
  });
  const dot1 = useAnimatedStyle(() => {
    time.get();
    held.get();
    clock.get();
    return dotStyle(1);
  });
  const dot2 = useAnimatedStyle(() => {
    time.get();
    held.get();
    clock.get();
    return dotStyle(2);
  });
  const dots = [dot0, dot1, dot2];
  const pressStyle = (i: number) => {
    'worklet';
    const hd = held.get();
    return { transform: [{ scale: hd.on === 1 && hd.chip === i ? 0.96 : 1 }] };
  };
  const chip0 = useAnimatedStyle(() => {
    held.get();
    return pressStyle(0);
  });
  const chip1 = useAnimatedStyle(() => {
    held.get();
    return pressStyle(1);
  });
  const chip2 = useAnimatedStyle(() => {
    held.get();
    return pressStyle(2);
  });
  const chipStyles = [chip0, chip1, chip2];

  const toLocal = (x: number, y: number) => {
    'worklet';
    return { x, y };
  };
  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const p = toLocal(e.x, e.y);
      last.set(clock.get());
      for (let i = 0; i < CHIPS.length; i++) {
        if (Math.abs(p.x - chipX(i)) < 52 && Math.abs(p.y - chipY) < 30) {
          held.set({ ...held.get(), on: 1, x: p.x, y: p.y - 30, chip: i });
          scheduleOnRN(buzz, 0);
          return;
        }
      }
      if (p.x > pigX && p.x < pigX + PIG_W && p.y > pigY && p.y < pigY + IMG.h * sc) {
        pig.get().wv += 3.2;
        scheduleOnRN(cue, 'wiggle');
        scheduleOnRN(buzz, 0);
      }
    })
    .onUpdate((e) => {
      const hd = held.get();
      if (hd.on !== 1) return;
      last.set(clock.get());
      held.set({ ...hd, x: e.x, y: e.y - 30 });
    })
    .onFinalize((e) => {
      const hd = held.get();
      if (hd.on !== 1) return;
      const now = clock.get();
      last.set(now);
      const x = e.x;
      const y = e.y - 30;
      const over = Math.abs(x - slotX) < 120 && y > pigY - 80 && y < pigY + 160;
      if (over && !reduced) held.set({ ...hd, on: 0, dropAt: now, fromX: x, fromY: Math.min(y, slotY - 30) });
      else if (over) {
        held.set({ ...hd, on: 0 });
        bonus.set(bonus.get() + CHIPS[hd.chip]);
        bonusAt.set(now);
        scheduleOnRN(setPlusAmt, CHIPS[hd.chip]);
        scheduleOnRN(buzz, 2);
      } else held.set({ ...hd, on: 0, backAt: now, fromX: x, fromY: y });
    });

  /** VoiceOver / Switch Control: a chip is a button that drops its coin in. */
  const dropFrom = (i: number) => {
    const now = clock.get();
    last.set(now);
    if (reduced || !frameCb.isActive) {
      bonus.set(bonus.get() + CHIPS[i]);
      bonusAt.set(now);
      setPlusAmt(CHIPS[i]);
      return;
    }
    held.set({ ...held.get(), on: 0, chip: i, dropAt: now, fromX: slotX, fromY: slotY - 90 });
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={touch}>
        <View style={StyleSheet.absoluteFill}>
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View style={[styles.head, { top: insets.top + 18 }]} pointerEvents="none" accessibilityRole="summary">
        <Text style={styles.kicker} allowFontScaling={false}>
          LISBON TRIP · SAVED SO FAR
        </Text>
        <Animated.View style={totalStyle}>
          <Total text={totalText} />
        </Animated.View>
        <View style={styles.track}>
          <Animated.View style={[styles.fill, barStyle]} />
        </View>
        <Text style={styles.goal} allowFontScaling={false}>
          Goal $2,000 · by June 14
        </Text>
      </View>
      <Animated.Text style={[styles.plus, { left: slotX - 40, top: slotY - 50 }, plusStyle]} allowFontScaling={false}>
        +${plusAmt}
      </Animated.Text>
      <View style={[styles.chips, { top: chipY - 22 }]} pointerEvents="none">
        {CHIPS.map((v, i) => (
          <Animated.View
            key={v}
            style={[styles.chip, v === 20 && styles.chipHot, chipStyles[i]]}
            accessible
            accessibilityRole="button"
            accessibilityLabel={`Add $${v} to the piggy bank`}
            onAccessibilityTap={() => dropFrom(i)}
          >
            <Animated.View style={[styles.dot, dots[i]]} />
            <Text style={styles.chipText} allowFontScaling={false}>
              + ${v}
            </Text>
          </Animated.View>
        ))}
      </View>
      <Text style={[styles.hint, { top: chipY + 34 }]} allowFontScaling={false}>
        Drag a coin into the piggy
      </Text>
      <Animated.View style={[styles.receipt, { bottom: insets.bottom + 6 }, receiptStyle]} pointerEvents="none" accessibilityLiveRegion="polite">
        <View style={styles.tick}>
          <Text style={styles.tickGlyph} allowFontScaling={false}>
            ✓
          </Text>
        </View>
        <View style={styles.receiptText}>
          <Text style={styles.receiptTitle} allowFontScaling={false}>
            ${plusAmt} saved to Lisbon trip
          </Text>
          <Text style={styles.receiptSub} allowFontScaling={false}>
            Just now · from Everyday account
          </Text>
        </View>
      </Animated.View>
    </View>
  );
}

/** The total, re-rendered only when its rounded value changes. */
function Total({ text }: { text: { get: () => string } }) {
  const [label, setLabel] = useState(money(SAVED));
  useAnimatedReaction(
    () => text.get(),
    (cur, prev) => {
      if (cur !== prev) scheduleOnRN(setLabel, cur);
    },
  );
  return (
    <Text style={styles.total} allowFontScaling={false}>
      {label}
    </Text>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#03110b' },
  head: { position: 'absolute', left: 24, right: 24 },
  kicker: { color: 'rgba(150,240,190,0.72)', fontSize: 12, fontFamily: 'GPS-ExtraBold', letterSpacing: 1.4 },
  total: { color: '#ffffff', fontSize: 54, fontFamily: 'GPS-Black', letterSpacing: -1, marginTop: 2, fontVariant: ['tabular-nums'] },
  track: { marginTop: 14, height: 8, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.1)', overflow: 'hidden' },
  fill: { height: 8, borderRadius: 4, backgroundColor: '#3fe08f' },
  goal: { color: 'rgba(255,255,255,0.58)', fontSize: 14, fontFamily: 'GPS-SemiBold', marginTop: 8 },
  plus: { position: 'absolute', width: 80, textAlign: 'center', color: '#ffd25c', fontSize: 22, fontFamily: 'GPS-Black' },
  chips: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: 12 },
  chip: {
    width: 100,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.16)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  chipHot: { backgroundColor: 'rgba(63,224,143,0.16)', borderColor: 'rgba(63,224,143,0.6)' },
  dot: { width: 16, height: 16, borderRadius: 8, backgroundColor: '#f2bf45', borderWidth: 2, borderColor: '#c98f27' },
  chipText: { color: '#ffffff', fontSize: 16, fontFamily: 'GPS-ExtraBold' },
  receipt: { position: 'absolute', left: 16, right: 16, height: 58, borderRadius: 18, backgroundColor: '#0d2a1d', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(63,224,143,0.45)', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, gap: 12 },
  tick: { width: 32, height: 32, borderRadius: 16, backgroundColor: '#3fe08f', alignItems: 'center', justifyContent: 'center' },
  tickGlyph: { color: '#03110b', fontSize: 17, fontFamily: 'GPS-Black' },
  receiptText: { flex: 1 },
  receiptTitle: { color: '#ffffff', fontSize: 15, fontFamily: 'GPS-ExtraBold' },
  receiptSub: { color: 'rgba(255,255,255,0.55)', fontSize: 12, fontFamily: 'GPS-SemiBold', marginTop: 1 },
  hint: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: 'rgba(255,255,255,0.45)', fontSize: 13, fontFamily: 'GPS-SemiBold' },
});

export { LOOP };
