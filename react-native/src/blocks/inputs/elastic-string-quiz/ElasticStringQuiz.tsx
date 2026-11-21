import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector, State } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  BlendColor,
  BlurMask,
  Canvas,
  Circle,
  Glyphs,
  Group,
  Image,
  Path,
  RadialGradient,
  Rect,
  Shader,
  type SkFont,
  type SkPath,
  Skia,
  useFont,
  useImage,
} from '@shopify/react-native-skia';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { useEsqAudio } from './esq-audio';
import {
  addBump,
  addPluck,
  arrive,
  autoEventsAt,
  bendAt,
  clamp01,
  cloneEv,
  compose,
  cordPoints,
  easeOut,
  elastic,
  elasticInv,
  elasticSlope,
  type Ev,
  type Frame,
  firePulse,
  frameAt,
  haloLevel,
  initEv,
  LOOP,
  MAXL,
  mix,
  NEG,
  pointAt,
  POS,
  type Pts,
  releaseCord,
  ROPE,
  ROPE_N,
  ropeAt,
  sAt,
  type Script,
  seg,
  startTransition,
  TR,
  U_SQ,
  wordAt,
} from './esq-motion';
import { FIELD_FX } from './esq-shaders';
import { C, DEFAULT_ANSWERS, DEFAULT_PROMPTS, FONT, G, LREST, type Prompt, PULSE_SPEED } from './esq-theme';

/** Skia runtime effects are read as Display P3 on iOS: re-encode the sRGB colours there. */
const P3 = 1;

type Props = {
  /** Plays the 12 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
  /** The three answers on the beads, left to right. */
  answers?: string[];
  /** The words of a round and the index of each one's answer. */
  prompts?: Prompt[];
  onAnswer?: (e: { word: string; index: number; correct: boolean; ms: number }) => void;
  onRoundEnd?: (matched: Record<string, string[]>) => void;
  /** pt of pull that counts as an answer (default 44). */
  armAt?: number;
  /** The cords' plucks, the resolve and the snap, for a finger only (default true). */
  sound?: boolean;
  score?: number;
  seconds?: number;
};

const hex = (h: string) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const CORD_RGB = C.cord.map(hex);
const BEAD_RGB = C.bead.map(hex);
const HEAT_RGB = hex(C.heat);
const ACCENT_RGB = hex(C.accent);

const rgba = (a: number[], b: number[], k: number, al: number) => {
  'worklet';
  const r = Math.round(a[0] + (b[0] - a[0]) * k);
  const g = Math.round(a[1] + (b[1] - a[1]) * k);
  const bl = Math.round(a[2] + (b[2] - a[2]) * k);
  return `rgba(${r},${g},${bl},${Math.max(0, Math.min(1, al)).toFixed(3)})`;
};

const EMPTY = Skia.PathBuilder.Make().detach();

/** Codex's `tally-icon.svg` (10 × 10 pt: a rounded box and three ticks, 1.25 pt), drawn as a path so the HUD's
    opacity applies to it (Skia's ImageSVG ignores a group's opacity). */
const TALLY_PATH = (() => {
  const b = Skia.PathBuilder.Make();
  b.addRRect(Skia.RRectXY(Skia.XYWHRect(0.625, 0.625, 8.75, 8.75), 1.875, 1.875));
  for (const x of [2.75, 5, 7.25]) {
    b.moveTo(x, 3.25);
    b.lineTo(x, 6.75);
  }
  return b.detach();
})();

const vecOf = (x: number, y: number) => {
  'worklet';
  return { x, y };
};

/** The part of a polyline between arc lengths a and b. */
const subPath = (p: Pts, a: number, b: number): SkPath => {
  'worklet';
  if (b - a < 0.05) return EMPTY;
  const pb = Skia.PathBuilder.Make();
  const s = pointAt(p, a);
  pb.moveTo(s.x, s.y);
  for (let k = 0; k < p.x.length; k++) if (p.s[k] > a && p.s[k] < b) pb.lineTo(p.x[k], p.y[k]);
  const e = pointAt(p, b);
  pb.lineTo(e.x, e.y);
  return pb.detach();
};

/** A rope through its nodes, smoothed through the midpoints. */
const ropePath = (xs: number[], ys: number[]): SkPath => {
  'worklet';
  const pb = Skia.PathBuilder.Make();
  pb.moveTo(xs[0], ys[0]);
  for (let k = 1; k < xs.length - 1; k++) pb.quadTo(xs[k], ys[k], (xs[k] + xs[k + 1]) / 2, (ys[k] + ys[k + 1]) / 2);
  pb.lineTo(xs[xs.length - 1], ys[ys.length - 1]);
  return pb.detach();
};

type CordGeo = {
  a: SkPath; // hub side (still hot ahead of a pulse; the fading shoulder while the beads fly)
  b: SkPath; // bead side
  aCol: string;
  bCol: string;
  aBody: string;
  bBody: string;
  bloom: number;
  width: number;
  body: number;
  pulse: [SkPath, SkPath, SkPath];
  px: number;
  py: number;
  pA: number;
  beadX: number;
  beadY: number;
};

/** One cord's drawing for the frame. */
const cordGeo = (f: Frame, i: number): CordGeo => {
  'worklet';
  const base = CORD_RGB[i];
  const lineA = f.cordA[i];
  const out: CordGeo = {
    a: EMPTY,
    b: EMPTY,
    aCol: 'transparent',
    bCol: 'transparent',
    aBody: 'transparent',
    bBody: 'transparent',
    bloom: 0,
    width: f.width[i],
    body: f.bodyW[i],
    pulse: [EMPTY, EMPTY, EMPTY],
    px: -100,
    py: -100,
    pA: 0,
    beadX: f.bx[i],
    beadY: f.by[i],
  };
  // a snapped cord: the free rope
  if (f.ropeTau[i] >= 0) {
    const r = ropeAt(ROPE[i], f.ropeTau[i], f.e.snapS[i], f.e.snapB[i]);
    out.b = ropePath(r.x, r.y);
    out.bCol = rgba(base, HEAT_RGB, 0.35 * (1 - seg(f.ropeTau[i], 0, 0.3)), 0.8 * lineA);
    out.bBody = rgba(base, base, 0, 0.20 * lineA);
    out.beadX = r.x[ROPE_N - 1];
    out.beadY = r.y[ROPE_N - 1];
    return out;
  }

  const p = cordPoints(i, f.hx, f.hy, f.by[i], f.bend[i], f.e.pl, f.t, f.waveFrom);
  const drawLen = p.total * f.draw[i];
  let split = 0;
  if (f.pulseFrac[i] >= 0) {
    split = p.total * (1 - f.pulseFrac[i]);
    // the bump fades out over the last 30 pt so it meets the hub without a hook
    addBump(p, split, f.pulseAmp[i] * seg(split, 0, 30));
    const c = pointAt(p, split);
    out.px = c.x;
    out.py = c.y;
    out.pA = f.pulseAmp[i] * lineA;
    out.pulse = [subPath(p, split - 18, split + 18), subPath(p, split - 12, split + 12), subPath(p, split - 6, split + 6)];
  }
  const hot = f.heatHot[i];
  const ht = f.heat[i];
  out.bloom = 0.35 * Math.max(ht, hot) * lineA;
  out.a = split > 0 ? subPath(p, 0, Math.min(split, drawLen)) : EMPTY;
  out.b = subPath(p, Math.min(split, drawLen), drawLen);
  out.aCol = rgba(base, HEAT_RGB, hot, (0.8 + 0.2 * hot) * lineA);
  out.bCol = rgba(base, HEAT_RGB, ht, (0.8 + 0.2 * ht) * lineA);
  out.aBody = rgba(base, HEAT_RGB, hot, 0.20 * lineA);
  out.bBody = rgba(base, HEAT_RGB, ht, 0.20 * lineA);
  if (f.tip === 1) {
    const tp = pointAt(p, drawLen);
    out.beadX = tp.x;
    out.beadY = tp.y;
  }
  return out;
};

type WordLayout = { ids: number[][]; xs: number[][]; ws: number[][] };

const layoutRun = (font: SkFont, text: string, cx: number, track: number) => {
  const ids = font.getGlyphIDs(text);
  const ws = font.getGlyphWidths(ids);
  const total = ws.reduce((a, b) => a + b, 0) + track * (ids.length - 1);
  const xs: number[] = [];
  let x = cx - total / 2;
  for (let k = 0; k < ids.length; k++) {
    xs.push(x);
    x += ws[k] + track;
  }
  return { ids, xs, ws, total };
};

/**
 * A synonym quiz where each answer hangs on an elastic cord. Pull a bead down and its cord stretches, thins and
 * heats up; let go and a pulse of light runs up to the hub, which swallows the word if the answer is right and
 * snaps the cord if it is not. Sweep a finger across the cords and they ring like strings.
 */
export function ElasticStringQuiz({
  autoPlay = true,
  answers = DEFAULT_ANSWERS,
  prompts = DEFAULT_PROMPTS,
  onAnswer,
  onRoundEnd,
  armAt = 44,
  sound = true,
  score = 6412,
  seconds = 41,
}: Props) {
  const systemReduced = useReducedMotionPreference();
  const [devReduced, setDevReduced] = useState(false);
  const reduced = systemReduced || (__DEV__ && devReduced);
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsOk] = useFonts({
    [FONT.medium]: require('./assets/Barlow-Medium.ttf'),
    [FONT.semi]: require('./assets/Barlow-SemiBold.ttf'),
    [FONT.bold]: require('./assets/Barlow-Bold.ttf'),
  });
  const wordFont = useFont(require('./assets/Barlow-SemiBold.ttf'), 52);
  const labelFont = useFont(require('./assets/Barlow-SemiBold.ttf'), 24);
  const hudFont = useFont(require('./assets/Barlow-Bold.ttf'), 15);
  const halo = useImage(require('./assets/halo.png'));
  const audio = useEsqAudio(sound);
  const [matched, setMatched] = useState<string[][]>([[], [], []]);

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = W / 402;
  const dy = (H - 874 * S) / 2;
  const top = -dy / S;
  const bottom = (H - dy) / S;
  const hudY = (Math.max(insets.top, 20) + 12 - dy) / S;
  const px = S * PixelRatio.get();

  // ---- glyph layout (once the fonts are in) ----
  const words = useMemo<WordLayout | null>(() => {
    if (!wordFont) return null;
    const out: WordLayout = { ids: [], xs: [], ws: [] };
    for (const p of prompts) {
      const r = layoutRun(wordFont, p.word, G.hubX, 0);
      out.ids.push(r.ids);
      out.xs.push(r.xs);
      out.ws.push(r.ws);
    }
    return out;
  }, [wordFont, prompts]);
  const labels = useMemo(() => {
    if (!labelFont) return null;
    return answers.slice(0, 3).map((a, i) => {
      const r = layoutRun(labelFont, a, G.bx[i], 0.4);
      return r.ids.map((id, k) => ({ id, pos: { x: r.xs[k], y: G.labelBase } }));
    });
  }, [labelFont, answers]);
  const hud = useMemo(() => {
    if (!hudFont) return null;
    const dIds = hudFont.getGlyphIDs('0123456789');
    const dWs = hudFont.getGlyphWidths(dIds);
    const slot = 0.544 * 15; // Barlow Bold's tabular advance: the digits sit in equal slots
    const timer = layoutRun(hudFont, `:${seconds}`, 0, 0);
    const timerGlyphs = timer.ids.map((id, k) => ({ id, pos: { x: 386 - timer.total + (timer.xs[k] + timer.total / 2), y: 0 } }));
    return { dIds, dWs, slot, timerGlyphs };
  }, [hudFont, seconds]);

  // ---- the script the timeline reads ----
  const script = useMemo<Script>(() => {
    const ans = prompts.map((p) => p.answer);
    const cA = ans[0] ?? 2;
    const cB = ans[1] ?? 1;
    let cW = 0;
    for (let i = 0; i < 3; i++) {
      if (i !== cB && i !== cA) {
        cW = i;
        break;
      }
    }
    const cx: number[] = [];
    for (let w = 0; w < prompts.length; w++) {
      for (let k = 0; k < MAXL; k++) cx.push(words ? (words.xs[w][k] ?? 0) + (words.ws[w][k] ?? 0) / 2 : G.hubX);
    }
    return { n: prompts.length, answers: ans, lens: prompts.map((p) => p.word.length), cx, cA, cB, cW, score, top, arm: armAt };
  }, [prompts, words, score, top, armAt]);

  // ---- clocks and live state ----
  const cfg = useSharedValue<Script>(script);
  const T = useSharedValue(0);
  const now = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const ev = useSharedValue<Ev>(initEv(score, reduced ? 1 : 0));
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const grab = useSharedValue([0, 0, 0]);
  const fingerUp = useSharedValue([0, 0, 0]);
  const dy0 = useSharedValue([0, 0, 0]);
  const grabOffset = useSharedValue([0, 0, 0]);
  const g0 = useSharedValue([0, 0, 0]);
  const moved = useSharedValue([0, 0, 0]);
  const vel = useSharedValue([0, 0, 0]);
  const armed = useSharedValue([0, 0, 0]);
  const strum = useSharedValue({ x: 0, y: 0, last: -1 });

  useEffect(() => {
    cfg.set(script);
  }, [script, cfg]);
  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
    if (reduced) {
      ev.set(initEv(score, 1));
      now.set(0);
      pinned.set(0);
      touching.set(0);
    } else {
      ev.modify((v) => {
        'worklet';
        v.rm = 0;
        return v;
      });
    }
  }, [autoPlay, reduced, score, auto, ev, now, pinned, touching]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __esq?: unknown };
    g.__esq = {
      at: (t: number) => {
        pinned.set(1);
        auto.set(1);
        T.set(t);
      },
      play: () => {
        pinned.set(0);
        auto.set(1);
      },
      rate: (r: number) => {
        rate.set(r);
      },
      reducedMotion: (on: boolean) => setDevReduced(on),
      state: () => {
        const f = auto.get() === 1 ? frameAt(T.get(), cfg.get()) : compose(ev.get(), now.get(), cfg.get());
        return { T: T.get(), now: now.get(), auto: auto.get(), pinned: pinned.get(), reduced: f.e.rm, word: f.w, pull: f.s, heat: f.heatHot, width: f.width, field: { x: f.fx, y: f.fy, gain: f.gain }, pulse: f.pulseFrac, shock: { r: f.shockR, a: f.shockA }, mode: f.mode, labelScale: f.labelSc };
      },
    };
    return () => {
      delete g.__esq;
    };
  }, [T, auto, pinned, rate, now, ev, cfg]);

  // ---- JS side: haptics, sound, callbacks ----
  const buzz = (k: 'soft' | 'light' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : k === 'light' ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };
  const onRelease = (i: number, level: number) => {
    buzz('light');
    audio.play(i === 0 ? 'p0' : i === 1 ? 'p1' : 'p2', level);
  };
  const onPluck = (i: number, level: number, tick: number) => {
    if (tick) buzz('sel');
    audio.play(i === 0 ? 'p0' : i === 1 ? 'p1' : 'p2', level);
  };
  const onArrive = (i: number, correct: number, w: number, ms: number, last: number) => {
    buzz('rigid');
    setTimeout(() => {
      Haptics.notificationAsync(correct ? Haptics.NotificationFeedbackType.Success : Haptics.NotificationFeedbackType.Error).catch(() => {});
    }, 60);
    audio.play(correct ? 'resolve' : 'snap');
    const word = prompts[w]?.word ?? '';
    onAnswer?.({ word, index: i, correct: correct === 1, ms });
    if (correct) {
      setMatched((m) => {
        const next = m.map((l, k) => (k === i ? [...l, word] : l));
        if (last) {
          const rec: Record<string, string[]> = {};
          answers.forEach((a, k) => {
            rec[a] = next[k] ?? [];
          });
          onRoundEnd?.(rec);
        }
        return next;
      });
    }
  };
  const onRoundStart = (lead = U_SQ) => {
    setMatched([[], [], []]);
    // the knot swallows the last string
    setTimeout(() => buzz('sel'), Math.max(lead, 0) * 1000);
  };

  // ---- the clock ----
  const quiet = (e: Ev, t: number) => {
    'worklet';
    for (let i = 0; i < 3; i++) {
      if (e.held[i] === 1 || e.tapT[i] > NEG / 2 || e.pend[i] === 1) return false;
      if (t - e.relT[i] < 1.5) return false;
      if (e.snapT[i] < POS / 2 && t < e.snapT[i] + 1.6) return false;
      if (haloLevel(e, i, t) > 0.01) return false;
      for (let s = 0; s < 3; s++) {
        const b = (i * 3 + s) * 5;
        if (t >= e.pl[b + 3] && Math.abs(e.pl[b]) * Math.exp(-(t - e.pl[b + 3]) / e.pl[b + 4]) > 0.03) return false;
      }
    }
    if (t < e.pop[1] + 0.6 || t < e.pop[3] + 0.6) return false;
    if (e.nxT < POS / 2 && t < e.nxT + 1) return false;
    if (t < e.transT + 2.3 || t < e.rejT + 1.5) return false;
    return true;
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    now.set(now.get() + dt);
    const t = now.get();
    const sc = cfg.get();
    const e0 = ev.get();
    let due = false;
    for (let i = 0; i < 3; i++) {
      if (e0.tapT[i] > NEG / 2 && t >= e0.tapT[i] + 0.14) due = true;
      if (e0.pend[i] === 1 && t >= e0.pulseT[i] + LREST[i] / PULSE_SPEED) due = true;
    }
    if (t >= e0.trAt) due = true;
    if (due) {
      const e = cloneEv(e0);
      for (let i = 0; i < 3; i++) {
        if (e.tapT[i] > NEG / 2 && t >= e.tapT[i] + 0.14) {
          const tr = e.tapT[i] + 0.14;
          releaseCord(e, i, tr, 52, 0, 0);
          firePulse(e, i, tr, 1);
          scheduleOnRN(onRelease, i, 0.6);
        }
        if (e.pend[i] === 1 && t >= e.pulseT[i] + LREST[i] / PULSE_SPEED) {
          const ta = e.pulseT[i] + LREST[i] / PULSE_SPEED;
          const w = wordAt(e, ta);
          const correct = sc.answers[w] === i;
          const shown = e.shownT;
          arrive(e, i, ta, correct, sc, 0);
          scheduleOnRN(onArrive, i, correct ? 1 : 0, w, Math.round((ta - Math.max(shown, 0)) * 1000), correct && w >= sc.n - 1 ? 1 : 0);
        }
      }
      if (t >= e.trAt) {
        startTransition(e, e.trAt, sc, 0);
        scheduleOnRN(onRoundStart);
      }
      ev.set(e);
    }
    // 2 s after the last touch: once everything has settled, the loop starts again from its first frame
    if (!reduced && autoPlay && touching.get() === 0 && t - lastTouch.get() > 2) {
      const e = ev.get();
      const recap = e.recapT <= t && !(e.recapOffT > e.recapT);
      // the recap stays up long enough to be read before the loop takes over again
      if (quiet(e, t) && !(recap && t < e.recapT + 3.5)) {
        if (!recap && wordAt(e, t) === 0 && e.sTo === sc.score) {
          T.set(0);
          auto.set(1);
        } else {
          const n = cloneEv(e);
          if (recap) {
            n.recapOffT = t;
            startTransition(n, t - 0.2, sc, 1);
          } else startTransition(n, t, sc, 0);
          ev.set(n);
          scheduleOnRN(onRoundStart);
          lastTouch.set(t);
        }
      }
    }
  });

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    if (auto.get() === 1) return frameAt(T.get(), cfg.get());
    return compose(ev.get(), now.get(), cfg.get());
  });

  // while the next word rolls in each letter waits below this edge; otherwise the window is the whole canvas
  const wordMask = useDerivedValue(() => Skia.XYWHRect(0, top - 10, 402, (F.get().roll === 1 ? TR.mask : bottom + 10) - top + 10));
  const fieldU = useDerivedValue(() => {
    const f = F.get();
    return { uP3: P3, uC: [f.fx, f.fy], uGain: f.gain, uCool: f.cool, uPx: px, uSpan: [top, bottom] };
  });
  const geo = useDerivedValue(() => {
    const f = F.get();
    return [cordGeo(f, 0), cordGeo(f, 1), cordGeo(f, 2)];
  });

  // ---- touch ----
  const takeOver = () => {
    'worklet';
    if (auto.get() === 1) {
      const T0 = T.get();
      const e = autoEventsAt(T0, cfg.get());
      for (let i = 0; i < 3; i++) if (e.held[i] === 1) releaseCord(e, i, T0, e.hs[i], 0, 0);
      e.rm = reduced ? 1 : 0;
      now.set(T0);
      ev.set(e);
      auto.set(0);
    }
    pinned.set(0);
    lastTouch.set(now.get());
  };
  const busy = (e: Ev, t: number) => {
    'worklet';
    return (t >= e.transT && t < e.transT + 2.2) || t >= e.recapT - 0.4;
  };
  const canGrab = (e: Ev, i: number, t: number) => {
    'worklet';
    if (busy(e, t)) return false;
    if (e.snapT[i] < POS / 2 && t >= e.snapT[i] - 0.06 && t < e.snapT[i] + 1.1) return false;
    if (e.pend[i] === 1 && e.rm === 1) return false;
    return true;
  };

  const setArr = (sv: SharedValue<number[]>, i: number, v: number) => {
    'worklet';
    sv.modify((a) => {
      'worklet';
      a[i] = v;
      return a;
    });
  };

  const beadBegin = (i: number) => {
    'worklet';
    takeOver();
    touching.set(touching.get() + 1);
    const t = now.get();
    const e = cloneEv(ev.get());
    if (!canGrab(e, i, t)) {
      setArr(grab, i, 0);
      return;
    }
    setArr(grab, i, 1);
    const s0 = sAt(e, i, t);
    const b0 = bendAt(e, i, t);
    e.held[i] = 1;
    e.hs[i] = e.rm === 1 ? 0 : s0;
    e.hb[i] = e.rm === 1 ? 0 : b0;
    e.tapT[i] = NEG;
    if (haloLevel(e, i, t) < 0.5) e.haloOn[i] = t;
    e.haloOff[i] = NEG;
    ev.set(e);
    // Preserve even the negative recoil, which can travel beyond the finger's 8 pt upward band.
    const d0 = elasticInv(Math.max(s0, -7));
    setArr(dy0, i, d0);
    setArr(grabOffset, i, s0 - elastic(d0));
    setArr(g0, i, t);
    setArr(moved, i, 0);
    setArr(vel, i, 0);
    setArr(armed, i, s0 >= cfg.get().arm ? 1 : 0);
    scheduleOnRN(buzz, 'soft');
  };
  const beadMove = (i: number, tx: number, ty: number, vy: number) => {
    'worklet';
    if (grab.get()[i] !== 1) return;
    setArr(moved, i, Math.max(moved.get()[i], Math.hypot(tx, ty)));
    if (ev.get().rm === 1) return;
    const d = dy0.get()[i] + ty / S;
    const s = elastic(d) + grabOffset.get()[i];
    const b = 24 * Math.tanh(tx / S / 48);
    ev.modify((v) => {
      'worklet';
      v.hs[i] = s;
      v.hb[i] = b;
      return v;
    });
    setArr(vel, i, (vy / S) * elasticSlope(d));
    const arm = cfg.get().arm;
    const was = armed.get()[i];
    const is = s >= arm ? 1 : 0;
    if (is !== was) {
      setArr(armed, i, is);
      scheduleOnRN(buzz, 'sel');
    }
    lastTouch.set(now.get());
  };
  const beadEnd = (i: number, success = true) => {
    'worklet';
    touching.set(Math.max(0, touching.get() - 1));
    lastTouch.set(now.get());
    if (grab.get()[i] !== 1) return;
    setArr(grab, i, 0);
    const t = now.get();
    const sc = cfg.get();
    const e = cloneEv(ev.get());
    const s = e.hs[i];
    if (!success) {
      releaseCord(e, i, t, s, 0, e.hb[i]);
      e.haloOff[i] = t + 0.15;
      ev.set(e);
      return;
    }
    const isTap = moved.get()[i] < 8 && t - g0.get()[i] < 0.3;
    if (e.rm === 1) {
      e.held[i] = 0;
      const w = wordAt(e, t);
      const correct = sc.answers[w] === i;
      arrive(e, i, t, correct, sc, 0);
      ev.set(e);
      scheduleOnRN(onRelease, i, 0.6);
      scheduleOnRN(onArrive, i, correct ? 1 : 0, w, 0, correct && w >= sc.n - 1 ? 1 : 0);
      return;
    }
    if (isTap) {
      e.held[i] = 0;
      e.tapT[i] = t;
      e.tapS0[i] = s;
      ev.set(e);
      return;
    }
    const v = vel.get()[i];
    const answer = s + v * 0.1 >= sc.arm;
    releaseCord(e, i, t, s, v, e.hb[i]);
    firePulse(e, i, t, answer ? 1 : 2);
    if (!answer) e.haloOff[i] = t + 0.15;
    ev.set(e);
    scheduleOnRN(onRelease, i, clamp01(s / 96));
  };

  const beadGesture = (i: number) =>
    Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin(() => {
        setArr(fingerUp, i, 0);
        beadBegin(i);
      })
      .onUpdate((g) => {
        beadMove(i, g.translationX, g.translationY, g.velocityY);
      })
      .onTouchesUp(() => {
        setArr(fingerUp, i, 1);
      })
      .onFinalize((g, success) => {
        // A stationary tap can finish without activating the pan.
        beadEnd(i, success || (g.oldState === State.BEGAN && fingerUp.get()[i] === 1));
      });
  const beadL = beadGesture(0);
  const beadM = beadGesture(1);
  const beadR = beadGesture(2);

  // a sweep across the strings plucks each cord it crosses
  const strumBegin = (x: number, y: number) => {
    'worklet';
    takeOver();
    touching.set(touching.get() + 1);
    strum.set({ x, y, last: strum.get().last });
  };
  /** The finger moved to (x, y) (canvas pt) at vx pt/s: pluck every cord the segment from the last point crosses. */
  const strumMove = (x: number, y: number, vx: number) => {
    'worklet';
    const t = now.get();
    const p = strum.get();
    const e0 = ev.get();
    lastTouch.set(t);
    if (busy(e0, t) || e0.rm === 1) {
      strum.set({ x, y, last: p.last });
      return;
    }
    let last = p.last;
    let e: Ev | null = null;
    for (let i = 0; i < 3; i++) {
      const cx = G.bx[i];
      if ((p.x - cx) * (x - cx) >= 0 || p.x === x) continue;
      const yc = mix(p.y, y, (cx - p.x) / (x - p.x));
      if (yc < G.shoulderY + G.corner || yc > G.beadY - 20) continue;
      if (e0.held[i] === 1 || (e0.snapT[i] < POS / 2 && t >= e0.snapT[i] && t < e0.snapT[i] + 1.1)) continue;
      const vTop = i === 1 ? G.hubY : G.shoulderY + G.corner;
      const u0 = clamp01((yc - vTop) / (G.beadY - vTop));
      const A = Math.min(Math.max(Math.abs(vx) / 150, 2), 9) * Math.sign(x - p.x);
      if (!e) e = cloneEv(e0);
      addPluck(e, i, A * Math.sin(Math.PI * u0), (A * Math.sin(2 * Math.PI * u0)) / 4, (A * Math.sin(3 * Math.PI * u0)) / 9, t, 0.9);
      const tick = t - last >= 0.06 ? 1 : 0;
      if (tick) last = t;
      scheduleOnRN(onPluck, i, Math.abs(A) / 9, tick);
    }
    if (e) ev.set(e);
    strum.set({ x, y, last });
  };
  const strumEnd = () => {
    'worklet';
    touching.set(Math.max(0, touching.get() - 1));
    lastTouch.set(now.get());
  };
  const strumPan = Gesture.Pan()
    .minDistance(2)
    .onBegin((g) => {
      strumBegin(g.x / S, (g.y - dy) / S);
    })
    .onUpdate((g) => {
      strumMove(g.x / S, (g.y - dy) / S, g.velocityX / S);
    })
    .onFinalize(() => {
      strumEnd();
    });
  const recapContinue = () => {
    'worklet';
    takeOver();
    const t = now.get();
    const e0 = ev.get();
    if (e0.recapT <= t && !(e0.recapOffT > e0.recapT)) {
      const e = cloneEv(e0);
      e.recapOffT = t;
      startTransition(e, t - 0.2, cfg.get(), 1);
      ev.set(e);
      scheduleOnRN(onRoundStart, U_SQ - 0.2);
      scheduleOnRN(buzz, 'light');
    }
  };
  const recapTap = Gesture.Tap().onEnd((_, success) => {
    if (success) recapContinue();
  });
  const rootGesture = Gesture.Simultaneous(strumPan, recapTap);

  // dev only: drive the same worklets the gestures call (for checks where touch injection is unavailable)
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __esqTouch?: unknown };
    g.__esqTouch = {
      grab: (i: number) => scheduleOnUI(beadBegin, i),
      move: (i: number, tx: number, ty: number, vy: number) => scheduleOnUI(beadMove, i, tx * S, ty * S, vy * S),
      release: (i: number) => scheduleOnUI(beadEnd, i),
      strumBegin: (x: number, y: number) => scheduleOnUI(strumBegin, x, y),
      strumMove: (x: number, y: number, vx: number) => scheduleOnUI(strumMove, x, y, vx),
      strumEnd: () => scheduleOnUI(strumEnd),
      tapRecap: () => scheduleOnUI(recapContinue),
      ev: () => {
        const e = ev.get();
        const t = now.get();
        return { t, auto: auto.get(), s: [0, 1, 2].map((i) => sAt(e, i, t)), held: e.held, pend: e.pend, word: wordAt(e, t), score: e.sTo, snapT: e.snapT, pl: e.pl.filter((_, k) => k % 5 === 0), recapT: e.recapT, transT: e.transT };
      },
    };
    return () => {
      delete g.__esqTouch;
    };
  });

  // ---- RN layers ----
  const recapSt = useAnimatedStyle(() => ({ opacity: F.get().recapA }));

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const ready = !!(size && words && labels && hud && halo && fontsOk && wordFont && labelFont && hudFont);
  const toY = (y: number) => dy + y * S;

  return (
    <GestureDetector gesture={rootGesture}>
      <View style={styles.root} onLayout={onLayout}>
        <StatusBar style="light" />
        {ready && (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Group transform={[{ translateY: dy }, { scale: S }]}>
              <Rect x={0} y={top} width={402} height={bottom - top}>
                <Shader source={FIELD_FX} uniforms={fieldU} />
              </Rect>
              {[0, 1, 2].map((i) => (
                <Cord key={i} i={i} geo={geo} halo={halo} />
              ))}
              {[0, 1, 2].map((i) => (
                <Bead key={i} i={i} F={F} geo={geo} halo={halo} />
              ))}
              {[0, 1, 2].map((i) => (
                <Label key={i} i={i} F={F} geo={geo} font={labelFont} glyphs={labels[i]} />
              ))}
              <Group clip={wordMask}>
                {Array.from({ length: MAXL }, (_, k) => (
                  <Letter key={k} k={k} F={F} L={words} font={wordFont} />
                ))}
              </Group>
              <Ball F={F} />
              <Hud F={F} hud={hud} font={hudFont} hudY={hudY} />
            </Group>
          </Canvas>
        )}

        {/* the recap after a round (live only) */}
        {ready && (
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, recapSt]}>
            <Text allowFontScaling={false} style={[styles.recapTitle, { top: toY(200) - 12 * S, fontSize: 20 * S, letterSpacing: 0.8 * S }]}>
              WORDS MATCHED
            </Text>
            {[
              { x: 99, y: 304, ly: 360 },
              { x: 201, y: 492, ly: 549 },
              { x: 302, y: 304, ly: 360 },
            ].map((n, k) => {
              const i = k === 0 ? 0 : k === 1 ? 1 : 2;
              return (
                <View key={i} style={[styles.recapNode, { left: (n.x - 70) * S, top: toY(n.y) - 7 * S, width: 140 * S }]}>
                  <View style={{ width: 14 * S, height: 14 * S, borderRadius: 7 * S, backgroundColor: C.bead[i] }} />
                  <Text allowFontScaling={false} style={[styles.recapAnswer, { marginTop: (n.ly - n.y - 13) * S, fontSize: 22 * S, lineHeight: 26 * S }]}>
                    {answers[i]}
                  </Text>
                  <View style={{ marginTop: (42 - 13 - 10) * S }}>
                    {(matched[i] ?? []).map((w) => (
                      <Text key={w} allowFontScaling={false} style={[styles.recapWord, { fontSize: 13 * S, lineHeight: 21 * S }]}>
                        {w}
                      </Text>
                    ))}
                  </View>
                </View>
              );
            })}
            <Text allowFontScaling={false} style={[styles.recapTap, { top: toY(762) - 9 * S, fontSize: 14 * S, letterSpacing: 0.84 * S }]}>
              TAP TO CONTINUE
            </Text>
            <Text allowFontScaling={false} style={[styles.paused, { top: (hudY + 9) * S + dy, right: 16 * S, fontSize: 13 * S, letterSpacing: 0.64 * S }]}>
              PAUSED
            </Text>
          </Animated.View>
        )}

        {/* the beads' hit areas (bead and label, 88 × 110 pt) */}
        {ready &&
          [beadL, beadM, beadR].map((g, i) => (
            <GestureDetector key={i} gesture={g}>
              <View style={[styles.hit, { left: (G.bx[i] - 44) * S, top: toY(G.beadY - 22), width: 88 * S, height: 110 * S }]} />
            </GestureDetector>
          ))}
      </View>
    </GestureDetector>
  );
}

// ---------------------------------------------------------------------------------------------------------------
// Canvas parts

type GeoSV = SharedValue<CordGeo[]>;
type FrameSV = SharedValue<Frame>;
type Img = ReturnType<typeof useImage>;

function Cord({ i, geo, halo }: { i: number; geo: GeoSV; halo: Img }) {
  const a = useDerivedValue(() => geo.get()[i].a);
  const b = useDerivedValue(() => geo.get()[i].b);
  const aCol = useDerivedValue(() => geo.get()[i].aCol);
  const bCol = useDerivedValue(() => geo.get()[i].bCol);
  const aBody = useDerivedValue(() => geo.get()[i].aBody);
  const bBody = useDerivedValue(() => geo.get()[i].bBody);
  const bloom = useDerivedValue(() => geo.get()[i].bloom);
  const w = useDerivedValue(() => geo.get()[i].width);
  const bw = useDerivedValue(() => geo.get()[i].body);
  const p0 = useDerivedValue(() => geo.get()[i].pulse[0]);
  const p1 = useDerivedValue(() => geo.get()[i].pulse[1]);
  const p2 = useDerivedValue(() => geo.get()[i].pulse[2]);
  const pA = useDerivedValue(() => geo.get()[i].pA);
  const pA0 = useDerivedValue(() => 0.3 * geo.get()[i].pA);
  const pA1 = useDerivedValue(() => 0.6 * geo.get()[i].pA);
  const hx = useDerivedValue(() => geo.get()[i].px - 18);
  const hy = useDerivedValue(() => geo.get()[i].py - 18);
  const hA = useDerivedValue(() => 0.6 * geo.get()[i].pA);
  return (
    <Group>
      <Path path={a} style="stroke" strokeWidth={bw} strokeJoin="round" strokeCap="round" color={aBody} />
      <Path path={b} style="stroke" strokeWidth={bw} strokeJoin="round" strokeCap="round" color={bBody} />
      <Group blendMode="plus" opacity={bloom}>
        <Path path={a} style="stroke" strokeWidth={14} strokeJoin="round" strokeCap="round" color={aCol}>
          <BlurMask blur={4} style="normal" respectCTM />
        </Path>
        <Path path={b} style="stroke" strokeWidth={14} strokeJoin="round" strokeCap="round" color={bCol}>
          <BlurMask blur={4} style="normal" respectCTM />
        </Path>
      </Group>
      <Path path={a} style="stroke" strokeWidth={w} strokeJoin="round" strokeCap="round" color={aCol} />
      <Path path={b} style="stroke" strokeWidth={w} strokeJoin="round" strokeCap="round" color={bCol} />
      {halo && (
        <Image image={halo} x={hx} y={hy} width={36} height={36} opacity={hA}>
          <BlendColor color={C.accent} mode="srcIn" />
        </Image>
      )}
      <Path path={p0} style="stroke" strokeWidth={2} strokeCap="round" color="#FFFFFF" opacity={pA0} />
      <Path path={p1} style="stroke" strokeWidth={3} strokeCap="round" color="#FFFFFF" opacity={pA1} />
      <Path path={p2} style="stroke" strokeWidth={4} strokeCap="round" color="#FFFFFF" opacity={pA} />
    </Group>
  );
}

function Bead({ i, F, geo, halo }: { i: number; F: FrameSV; geo: GeoSV; halo: Img }) {
  const x = useDerivedValue(() => geo.get()[i].beadX);
  const y = useDerivedValue(() => geo.get()[i].beadY);
  const h = useDerivedValue(() => F.get().halo[i]);
  const a = useDerivedValue(() => F.get().beadA[i]);
  const sc = useDerivedValue(() => F.get().beadSc[i]);
  const haloR = useDerivedValue(() => mix(10, 30, h.get()) * sc.get());
  const haloX = useDerivedValue(() => x.get() - haloR.get());
  const haloY = useDerivedValue(() => y.get() - haloR.get());
  const haloD = useDerivedValue(() => 2 * haloR.get());
  const haloA = useDerivedValue(() => 0.55 * h.get());
  const r = useDerivedValue(() => 10 * sc.get());
  const c = useDerivedValue(() => vecOf(x.get(), y.get()));
  const cols = useDerivedValue(() => {
    const col = rgba(BEAD_RGB[i], ACCENT_RGB, h.get(), 1);
    const edge = rgba(BEAD_RGB[i], ACCENT_RGB, h.get(), 0);
    return [col, col, edge];
  });
  const coreR = useDerivedValue(() => 5 * h.get() * sc.get());
  return (
    <Group opacity={a}>
      {halo && (
        <Image image={halo} x={haloX} y={haloY} width={haloD} height={haloD} opacity={haloA}>
          <BlendColor color={C.accent} mode="srcIn" />
        </Image>
      )}
      <Circle cx={x} cy={y} r={r}>
        <RadialGradient c={c} r={r} colors={cols} positions={[0, 0.74, 1]} />
      </Circle>
      <Circle cx={x} cy={y} r={coreR} color="#FFFFFF" />
    </Group>
  );
}

function Label({ i, F, geo, font, glyphs }: { i: number; F: FrameSV; geo: GeoSV; font: SkFont; glyphs: { id: number; pos: { x: number; y: number } }[] }) {
  const tr = useDerivedValue(() => {
    const f = F.get();
    const g = geo.get()[i];
    // in the reset the label hangs under its string's end and shrinks into the knot with it
    const riding = f.mode === 6;
    const ddx = f.ropeTau[i] >= 0 || riding ? g.beadX - G.bx[i] : f.bend[i] * 0.85;
    const ddy = riding ? g.beadY - G.beadY + (G.labelY - G.beadY) * (f.labelSc[i] - 1) : f.ropeTau[i] >= 0 ? g.beadY - G.beadY : f.labelDy[i];
    return [{ translateX: G.bx[i] + ddx }, { translateY: G.labelY + ddy }, { scale: Math.max(f.labelSc[i], 0.001) }, { translateX: -G.bx[i] }, { translateY: -G.labelY }];
  });
  const a = useDerivedValue(() => F.get().labelA[i]);
  const blur = useDerivedValue(() => Math.max(F.get().labelBlur[i], 0));
  return (
    <Group transform={tr} opacity={a}>
      <Glyphs font={font} glyphs={glyphs} color={C.ink}>
        <BlurMask blur={blur} style="normal" respectCTM />
      </Glyphs>
    </Group>
  );
}

function Letter({ k, F, L, font }: { k: number; F: FrameSV; L: WordLayout; font: SkFont }) {
  const glyphs = useDerivedValue(() => {
    const f = F.get();
    const ids = L.ids[f.w];
    if (!ids || k >= ids.length || f.la[k] <= 0.001) return [];
    return [{ id: ids[k], pos: vecOf(L.xs[f.w][k], G.wordBase) }];
  });
  const tr = useDerivedValue(() => {
    const f = F.get();
    const xs = L.xs[f.w];
    const cx = xs && k < xs.length ? xs[k] + L.ws[f.w][k] / 2 : G.hubX;
    return [{ translateX: cx + f.lx[k] }, { translateY: G.wordY + f.ly[k] }, { scale: Math.max(f.ls[k], 0.01) }, { translateX: -cx }, { translateY: -G.wordY }];
  });
  const a = useDerivedValue(() => F.get().la[k]);
  return (
    <Group transform={tr} opacity={a}>
      <Glyphs font={font} glyphs={glyphs} color={C.ink} />
    </Group>
  );
}

function Ball({ F }: { F: FrameSV }) {
  const cx = useDerivedValue(() => F.get().hx);
  const cy = useDerivedValue(() => F.get().hy);
  const r = useDerivedValue(() => F.get().ballR);
  const a = useDerivedValue(() => F.get().hubA);
  const arcs = useDerivedValue(() => {
    const f = F.get();
    if (f.arcA <= 0.001) return EMPTY;
    const R = f.ballR + 8;
    const ov = Skia.XYWHRect(f.hx - R, f.hy - R, 2 * R, 2 * R);
    const pb = Skia.PathBuilder.Make();
    const rot = (f.arcRot * 180) / Math.PI;
    pb.addArc(ov, -150 + rot, 110 * f.arcSweep);
    pb.addArc(ov, 40 - rot, 70 * f.arcSweep);
    return pb.detach();
  });
  const arcA = useDerivedValue(() => 0.7 * F.get().arcA * F.get().hubA);
  const shockR = useDerivedValue(() => F.get().shockR);
  const shockA = useDerivedValue(() => F.get().shockA * F.get().hubA);
  return (
    <Group>
      <Circle cx={cx} cy={cy} r={shockR} style="stroke" strokeWidth={2} color="#FFFFFF" opacity={shockA} />
      <Path path={arcs} style="stroke" strokeWidth={2} strokeCap="round" color="#FFFFFF" opacity={arcA} />
      <Circle cx={cx} cy={cy} r={r} color="#FFFFFF" opacity={a} />
    </Group>
  );
}

type HudLayout = { dIds: number[]; dWs: number[]; slot: number; timerGlyphs: { id: number; pos: { x: number; y: number } }[] };

/** One digit of the score: it rolls up from its old value to its new one, the leftmost a little later. */
function Digit({ k, F, hud, font, hudY }: { k: number; F: FrameSV; hud: HudLayout; font: SkFont; hudY: number }) {
  const place = 3 - k;
  const x0 = 36 + k * hud.slot;
  const LH = 18;
  const st = useDerivedValue(() => {
    const f = F.get();
    const p10 = Math.pow(10, place);
    const dF = Math.floor(Math.min(f.sF, 9999) / p10) % 10;
    const dT = Math.floor(Math.min(f.sTo, 9999) / p10) % 10;
    const delta = (dT - dF + 10) % 10;
    let q = f.t >= f.sT0 ? 1 : 0;
    if (f.sDur > 0) q = easeOut(seg(f.t, f.sT0 + 0.05 * place, f.sT0 + f.sDur));
    const pos = dF + delta * q;
    const d0 = Math.floor(pos) % 10;
    return { d0, d1: (d0 + 1) % 10, fr: pos - Math.floor(pos) };
  });
  const g0 = useDerivedValue(() => {
    const d = st.get().d0;
    return [{ id: hud.dIds[d], pos: vecOf(x0 + (hud.slot - hud.dWs[d]) / 2, hudY + 5.3 - st.get().fr * LH) }];
  });
  const g1 = useDerivedValue(() => {
    const d = st.get().d1;
    return [{ id: hud.dIds[d], pos: vecOf(x0 + (hud.slot - hud.dWs[d]) / 2, hudY + 5.3 + (1 - st.get().fr) * LH) }];
  });
  const a1 = useDerivedValue(() => (st.get().fr > 0.001 ? 1 : 0));
  return (
    <Group clip={Skia.XYWHRect(x0 - 1, hudY - 10, hud.slot + 2, 21)}>
      <Glyphs font={font} glyphs={g0} color={C.hud} />
      <Glyphs font={font} glyphs={g1} color={C.hud} opacity={a1} />
    </Group>
  );
}

function Hud({ F, hud, font, hudY }: { F: FrameSV; hud: HudLayout; font: SkFont; hudY: number }) {
  const scoreA = useDerivedValue(() => F.get().scoreA);
  const hudA = useDerivedValue(() => F.get().hudA);
  const timer = hud.timerGlyphs.map((g) => ({ id: g.id, pos: { x: g.pos.x, y: hudY + 5.3 } }));
  return (
    <Group>
      <Group opacity={scoreA}>
        <Group transform={[{ translateX: 16 }, { translateY: hudY - 7 }, { scale: 1.4 }]}>
          <Path path={TALLY_PATH} style="stroke" strokeWidth={1.45} strokeCap="round" strokeJoin="round" color={C.hud} />
        </Group>
        {[0, 1, 2, 3].map((k) => (
          <Digit key={k} k={k} F={F} hud={hud} font={font} hudY={hudY} />
        ))}
      </Group>
      <Group opacity={hudA}>
        <Glyphs font={font} glyphs={timer} color={C.hud} />
      </Group>
    </Group>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.fieldEdge, overflow: 'hidden' },
  hit: { position: 'absolute' },
  recapTitle: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: C.ink, fontFamily: FONT.bold },
  recapNode: { position: 'absolute', alignItems: 'center' },
  recapAnswer: { color: C.ink, fontFamily: FONT.medium },
  recapWord: { color: C.ink2, fontFamily: FONT.medium, textAlign: 'center' },
  recapTap: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: C.ink3, fontFamily: FONT.semi },
  paused: { position: 'absolute', color: C.hud, fontFamily: FONT.bold, opacity: 0.8 },
});
