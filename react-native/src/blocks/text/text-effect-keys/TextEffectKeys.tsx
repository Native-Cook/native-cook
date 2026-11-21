import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Canvas, Picture, Skia, useFonts } from '@shopify/react-native-skia';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { drawScene, makeStatics } from './tek-draw';
import {
  BUBBLE_BASE,
  CYCLE,
  effectPose,
  FIELD_BASE,
  type Frame,
  frameAt,
  type Geo,
  GRAV,
  type Hero,
  LAND,
  LOOP,
  REST,
  RESEAT,
  THROWS,
  XK,
} from './tek-motion';
import { makeRes } from './tek-text';
import { C, EF, FONT, G, SPRING, TYPE } from './tek-theme';

type Props = {
  /** Plays the 8 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
  /** Called when a key is chosen (or cleared with -1). */
  onChange?: (effect: number) => void;
};

/** The live state under the finger: the eight glyphs as springs and projectiles, the keys, the thread. */
type Live = {
  x: number[];
  y: number[];
  vx: number[];
  vy: number[];
  rot: number[];
  s: number[];
  lime: number[];
  home: number[];
  font: number[];
  mode: number[]; // 0 home, 1 chained to the finger, 2 flying, 3 resting on a key, 4 on the stage
  wait: number[]; // delay before a glyph starts home (s)
  rest: number[]; // time resting on a key (s)
  drag: number[]; // time constant of the sideways drag (s)
  target: number; // 0 the field, 1 the newest bubble
  rise: number;
  riseV: number;
  body: number;
  bodyV: number;
  depth: number[];
  depthV: number[];
  kick: number[]; // time since a letter landed on it (s), -1 none
  held: number; // key under the finger, -1 none
  active: number; // the staged word's effect, -1 none
  effW: number[];
  effT: number;
  amp: number;
  finger: number; // 0 none, 1 magnet, 2 keys, 3 send
  fx: number;
  fy: number;
  fvx: number;
  fvy: number;
  sendS: number;
  sendV: number;
  hapticT: number;
  returning: number;
};

const blankLive = (): Live => {
  'worklet';
  return {
  x: [],
  y: [],
  vx: [],
  vy: [],
  rot: [],
  s: [],
  lime: [],
  home: [],
  font: [],
  mode: [],
  wait: [],
  rest: [],
  drag: [],
  target: 0,
  rise: 0,
  riseV: 0,
  body: 0,
  bodyV: 0,
  depth: [0, 0, 0, 0, 0, 0, 0, 0],
  depthV: [0, 0, 0, 0, 0, 0, 0, 0],
  kick: [-1, -1, -1, -1, -1, -1, -1, -1],
  held: -1,
  active: -1,
  effW: [0, 0, 0, 0, 0, 0, 0, 0],
  effT: 0,
  amp: 1,
  finger: 0,
  fx: 0,
  fy: 0,
  fvx: 0,
  fvy: 0,
  sendS: 1,
  sendV: 0,
  hapticT: -1,
  returning: 0,
  };
};

/**
 * The composer of a messaging app with a panel of eight lit keycaps whose labels perform their own text effect.
 * Press one and the word lifts out of its bubble and performs it in the thread; send and Explode throws the letters
 * across the whole conversation, onto the keys, and back in reading order.
 */
export function TextEffectKeys({ autoPlay = true, onChange }: Props) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const mgr = useFonts({
    [FONT.regular]: [require('./assets/fonts/Geist-Regular.ttf')],
    [FONT.medium]: [require('./assets/fonts/Geist-Medium.ttf')],
    [FONT.semibold]: [require('./assets/fonts/Geist-SemiBold.ttf')],
    [FONT.bold]: [require('./assets/fonts/Geist-Bold.ttf')],
    [FONT.italic]: [require('./assets/fonts/Geist-Italic.ttf')],
  });

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = W / 402;
  // the thread, composer and panel sit on the bottom edge; the nav row under the status bar
  const oy = H / S - 874;
  const navY = Math.max(insets.top / S + 4, G.navTop + Math.min(oy, 0));
  // The throw reaches 85 pt on the reference canvas. Fit the conversation above the pinned composer.
  const conversationFit = Math.min(1, Math.max(0.35, (G.field.y + oy - navY - 56) / (G.field.y - 85)));
  const conversationScale = 1 - (1 - conversationFit) * Math.min(1, Math.max(0, (-oy - 16) / 140));

  const res = useMemo(() => (mgr ? makeRes(mgr) : null), [mgr]);
  const statics = useMemo(() => (res ? makeStatics(res, navY) : null), [res, navY]);
  const geo = res?.geo;

  // ---- clocks and live state ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const n0 = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const L = useSharedValue<Live>(blankLive());

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  const buzz = (k: 'sel' | 'light' | 'rigid' | 'medium') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'light' ? Haptics.ImpactFeedbackStyle.Light : k === 'rigid' ? Haptics.ImpactFeedbackStyle.Rigid : Haptics.ImpactFeedbackStyle.Medium;
      Haptics.impactAsync(style).catch(() => {});
    }
  };
  const changed = (e: number) => onChange?.(e);

  // the glyphs' homes
  const homeOf = (g: Geo, i: number, target: number) => {
    'worklet';
    return target === 1 ? { x: g.bubbleX[i], y: BUBBLE_BASE } : { x: g.fieldX[i], y: FIELD_BASE };
  };

  /** Take over from the autoplay at its current frame, so nothing jumps. */
  const takeOver = () => {
    'worklet';
    if (!geo) return;
    if (auto.get() === 1) {
      const t = T.get();
      const f = frameAt(t, geo);
      const l = blankLive();
      for (let i = 0; i < geo.n; i++) {
        const h = f.hero[i];
        l.x.push(h.x);
        l.y.push(h.y);
        l.vx.push(h.vx);
        l.vy.push(h.vy);
        l.rot.push(((((h.rot + 180) % 360) + 360) % 360) - 180);
        l.s.push(h.s);
        l.lime.push(h.lime);
        l.home.push(h.home);
        l.font.push(h.font);
        const th = THROWS[i];
        const flying = t >= th.launch && t < RESEAT + 0.035 * i;
        const staged = t >= 3.23 && t < 6.05;
        l.mode.push(flying ? 2 : staged ? 4 : 0);
        l.wait.push(0);
        l.rest.push(0);
        l.drag.push(0.1);
      }
      l.target = t >= 0.28 && t < 6.7 ? 1 : 0;
      l.rise = f.rise;
      l.body = f.body;
      l.depth = f.depth.slice();
      let a = -1;
      for (let k = 0; k < 8; k++) if (f.active[k] > 0.5) a = k;
      l.active = t >= 3.23 && t < 6.05 ? a : -1;
      l.sendS = f.sendS;
      L.set(l);
      n0.set(now.get() - t);
      auto.set(0);
    }
    lastTouch.set(now.get());
  };

  const keyAt = (x: number, y: number) => {
    'worklet';
    for (let k = 0; k < 8; k++) {
      const kx = G.keyX[k % 2];
      const ky = G.keyY[Math.floor(k / 2)];
      if (x >= kx - 4 && x <= kx + G.keyW + 4 && y >= ky - 5 && y <= ky + G.keyH + 5) return k;
    }
    return -1;
  };

  const hapticSel = (l: Live) => {
    'worklet';
    if (now.get() - l.hapticT >= 0.1) {
      l.hapticT = now.get();
      scheduleOnRN(buzz, 'sel');
    }
  };

  const pressKey = (l: Live, k: number) => {
    'worklet';
    if (k === l.held) return;
    l.held = k;
    if (k < 0) return;
    l.active = k;
    for (let i = 0; i < l.mode.length; i++) {
      l.mode[i] = 4;
      l.wait[i] = 0;
    }
    l.returning = 0;
    scheduleOnRN(changed, k);
  };

  /** Throw every letter out of the newest bubble on the autoplay's fan. */
  const burst = (l: Live) => {
    'worklet';
    if (!geo) return;
    for (let i = 0; i < geo.n; i++) {
      const h = THROWS[i];
      const sx = l.x[i];
      // the same sideways drag as the autoplay's throw: it ends over the letter's landing x
      l.drag[i] = h.ta / XK;
      l.vx[i] = (h.xa - sx) / l.drag[i];
      l.vy[i] = -h.vy0;
      l.mode[i] = 2;
      l.rest[i] = 0;
    }
    l.returning = 0;
    scheduleOnRN(buzz, 'rigid');
  };

  // ---- the live simulation (UI thread) ----
  const step = (l: Live, dt: number) => {
    'worklet';
    if (!geo) return;
    const n = geo.n;
    const tnow = now.get();
    const sub = 4;
    const h = dt / sub;
    const springTo = (x: number, v: number, to: number, k: number, d: number) => {
      const a = -k * (x - to) - d * v;
      const v2 = v + a * h;
      return [x + v2 * h, v2];
    };
    // thread and the new bubble
    for (let s = 0; s < sub; s++) {
      const r = springTo(l.rise, l.riseV, l.target, SPRING.glide.k, SPRING.glide.d);
      l.rise = r[0];
      l.riseV = r[1];
      const b = springTo(l.body, l.bodyV, l.target, SPRING.pop.k, SPRING.pop.d);
      l.body = Math.max(0, b[0]);
      l.bodyV = b[1];
      const sd = springTo(l.sendS, l.sendV, 1, SPRING.pop.k, SPRING.pop.d);
      l.sendS = sd[0];
      l.sendV = sd[1];
    }
    // keys
    for (let k = 0; k < 8; k++) {
      const to = l.held === k ? 1 : 0;
      for (let s = 0; s < sub; s++) {
        const r = springTo(l.depth[k], l.depthV[k], to, to === 1 ? 2400 : SPRING.snap.k, to === 1 ? 90 : SPRING.snap.d);
        l.depth[k] = r[0];
        l.depthV[k] = r[1];
      }
      if (l.kick[k] >= 0) l.kick[k] = l.kick[k] + dt > 0.8 ? -1 : l.kick[k] + dt;
    }
    // effect weights blend over ~200 ms
    const kb = 1 - Math.exp(-dt / 0.07);
    for (let e = 0; e < 8; e++) l.effW[e] += ((l.active === e ? 1 : 0) - l.effW[e]) * kb;
    l.effT += dt;

    // the finger's chain: each letter 0.6 em behind the one before it
    let staged = 0;
    let busy = 0;
    for (let i = 0; i < n; i++) {
      const m = l.mode[i];
      if (m === 4) staged = 1;
      if (m === 2 || m === 3) busy = 1;
      if (l.wait[i] > 0) {
        l.wait[i] -= dt;
        continue;
      }
      let tx = 0;
      let ty = 0;
      let ts = 1;
      let tl = 0;
      if (m === 0) {
        const p = homeOf(geo, i, l.target);
        tx = p.x;
        ty = p.y;
        const dist = Math.hypot(l.x[i] - tx, l.y[i] - ty);
        tl = dist > 14 ? 1 : 0;
        if (dist < 40) {
          l.home[i] += (l.target - l.home[i]) * kb;
          l.font[i] = l.target;
        }
      } else if (m === 1) {
        if (i === 0) {
          tx = l.fx;
          ty = l.fy + 20;
        } else {
          const dx = l.x[i] - l.x[i - 1];
          const dy = l.y[i] - l.y[i - 1];
          const d = Math.max(Math.hypot(dx, dy), 0.001);
          const gap = 0.6 * 15 * TYPE.hero;
          tx = l.x[i - 1] + (dx / d) * gap;
          ty = l.y[i - 1] + (dy / d) * gap;
        }
        ts = TYPE.hero;
        tl = 1;
        l.font[i] = 1;
      } else if (m === 4) {
        tx = geo.stageX[i];
        ty = G.stageY;
        ts = TYPE.stage;
        tl = 1;
        l.font[i] = 1;
        l.home[i] = 1;
      }
      if (m === 0 || m === 1 || m === 4) {
        const sp = m === 1 ? SPRING.chain : m === 4 ? SPRING.glide : SPRING.pop;
        for (let s = 0; s < sub; s++) {
          const rx = springTo(l.x[i], l.vx[i], tx, sp.k, sp.d);
          const ry = springTo(l.y[i], l.vy[i], ty, sp.k, sp.d);
          l.x[i] = rx[0];
          l.vx[i] = rx[1];
          l.y[i] = ry[0];
          l.vy[i] = ry[1];
        }
        l.s[i] += (ts - l.s[i]) * (1 - Math.exp(-dt / 0.09));
        l.lime[i] += (tl - l.lime[i]) * (1 - Math.exp(-dt / 0.06));
        // upright again
        const r = ((((l.rot[i] + 180) % 360) + 360) % 360) - 180;
        l.rot[i] = r * Math.exp(-dt / 0.08);
        continue;
      }
      if (m === 3) {
        l.rest[i] += dt;
        continue;
      }
      // flying: gravity, sideways drag, and the key tops as shelves
      l.s[i] += (TYPE.hero - l.s[i]) * (1 - Math.exp(-dt / 0.05));
      l.lime[i] += (1 - l.lime[i]) * (1 - Math.exp(-dt / 0.03));
      l.font[i] = 1;
      l.home[i] = 1;
      for (let s = 0; s < sub; s++) {
        const y0 = l.y[i];
        l.vy[i] += GRAV * h;
        l.vx[i] *= Math.exp(-h / l.drag[i]);
        l.x[i] += l.vx[i] * h;
        l.y[i] += l.vy[i] * h;
        l.rot[i] += l.vx[i] * h * 0.25;
        if (l.x[i] < 14 || l.x[i] > 388) {
          l.x[i] = Math.min(Math.max(l.x[i], 14), 388);
          l.vx[i] = -0.3 * l.vx[i];
        }
        if (l.vy[i] > 0) {
          for (let k = 0; k < 8; k++) {
            const kx = G.keyX[k % 2];
            const top = G.keyY[Math.floor(k / 2)] + LAND + 4 * l.depth[k];
            if (l.x[i] < kx + 4 || l.x[i] > kx + G.keyW - 4) continue;
            if (y0 <= top && l.y[i] >= top) {
              l.y[i] = top;
              const strength = Math.min(1, l.vy[i] / 1200);
              if (l.vy[i] > 160) {
                l.vy[i] = -REST * l.vy[i];
                l.depth[k] = Math.max(l.depth[k], strength);
                l.depthV[k] = 0;
                if (strength > 0.5) l.kick[k] = 0;
                hapticSel(l);
              } else {
                l.vy[i] = 0;
                l.mode[i] = 3;
                l.rest[i] = 0;
              }
              break;
            }
          }
        }
        if (l.y[i] > 832 && l.mode[i] === 2) {
          l.y[i] = 832;
          l.vy[i] = 0;
          l.mode[i] = 3;
        }
        if (l.mode[i] === 3) break;
      }
    }

    // once the thrown letters have settled (or after 1.2 s on the keys), pull them home in reading order
    if (busy && !l.returning && l.finger !== 1) {
      let settled = true;
      for (let i = 0; i < n; i++) {
        if (l.mode[i] === 2) settled = false;
        if (l.mode[i] === 3 && l.rest[i] < 0.3) settled = false;
      }
      let long = false;
      for (let i = 0; i < n; i++) if (l.mode[i] === 3 && l.rest[i] > 1.2) long = true;
      if (settled || long) {
        l.returning = 1;
        let order = 0;
        for (let i = 0; i < n; i++) {
          if (l.mode[i] === 2 || l.mode[i] === 3) {
            l.wait[i] = 0.035 * order;
            l.mode[i] = 0;
            order++;
          }
        }
      }
    }

    // the staged word's effect: label amplitude drops to 40 % while a word is staged or thrown
    l.amp += ((staged || busy ? 0.4 : 1) - l.amp) * (1 - Math.exp(-dt / 0.12));

    // 2 s after the last touch: ease to rest (the word back in the field), then restart the loop
    if (l.finger === 0 && tnow - lastTouch.get() > 2 && !busy) {
      if (l.target !== 0 || l.active >= 0 || staged) {
        l.active = -1;
        l.target = 0;
        for (let i = 0; i < n; i++) {
          if (l.mode[i] !== 0) {
            l.mode[i] = 0;
            l.wait[i] = 0.045 * i;
          }
        }
        scheduleOnRN(changed, -1);
      }
      let rest = l.rise < 0.004 && l.body < 0.004 && l.held < 0;
      for (let i = 0; i < n && rest; i++) {
        const p = homeOf(geo, i, 0);
        if (Math.hypot(l.x[i] - p.x, l.y[i] - p.y) > 0.4 || Math.abs(l.s[i] - 1) > 0.01 || l.home[i] > 0.02) rest = false;
      }
      const kp = (((tnow - n0.get()) % CYCLE) + CYCLE) % CYCLE / CYCLE;
      if (rest && autoPlay && !reduced && (kp < 0.04 || kp > 0.975)) {
        T.set(kp > 0.5 ? (kp - 1) * CYCLE + LOOP : kp * CYCLE);
        auto.set(1);
      }
    }
  };

  /** The frame drawn from the live state. */
  const liveFrame = (l: Live, g: Geo): Frame => {
    'worklet';
    const n = g.n;
    const hero: Hero[] = [];
    const p = ((l.effT % CYCLE) + CYCLE) % CYCLE / CYCLE;
    const dips = [0, 0, 0];
    // the staged word's pose: the effects' offsets blended by weight, scale effects grow it from its centre
    for (let i = 0; i < n; i++) {
      let x = l.x[i];
      let y = l.y[i];
      let rot = l.rot[i];
      let s = l.s[i];
      let w = 0;
      let blur = 0;
      if (l.mode[i] === 4) {
        let dx = 0;
        let dy = 0;
        let dr = 0;
        let sc = 0;
        for (let e = 0; e < 8; e++) {
          const k = l.effW[e];
          if (k < 0.002) continue;
          const u = e === EF.ripple ? 28 / 6 : e === EF.explode ? 2 : TYPE.stage;
          const a = effectPose(e, p, i, n, u, true);
          dx += a.dx * k;
          dy += a.dy * k;
          dr += a.rot * k;
          sc += (e === EF.big ? 0.5 : 1) * a.sc * k;
          w += a.w * k;
          blur += a.blur * k;
        }
        x = 201 + (x - 201) * (1 + sc) + dx;
        y += dy;
        rot += dr;
        s *= 1 + sc;
        if (l.effW[EF.ripple] > 0.01) {
          for (let sp = 0; sp < g.spans.length; sp++) {
            if (x >= g.spans[sp][0] && x <= g.spans[sp][1]) dips[sp] = Math.max(dips[sp], (3 * -dy) / 28);
          }
        }
      }
      hero.push({ x, y, rot, s, lime: l.lime[i], home: l.home[i], font: l.font[i], w, vx: l.vx[i], vy: l.vy[i], blur, mb: l.mode[i] === 1 || l.mode[i] === 2 || (l.mode[i] === 0 && l.lime[i] > 0.3) ? 1 : 0 });
    }
    let away = 0;
    for (let i = 0; i < n; i++) away += l.lime[i];
    const kick = [-1, -1, -1, -1, -1, -1, -1, -1];
    const active = [0, 0, 0, 0, 0, 0, 0, 0];
    for (let k = 0; k < 8; k++) {
      kick[k] = l.kick[k] >= 0 ? l.kick[k] / 0.8 : -1;
      active[k] = l.effW[k];
    }
    const kp = (((now.get() - n0.get()) % CYCLE) + CYCLE) % CYCLE / CYCLE;
    return {
      rise: l.rise,
      body: l.body,
      squash: 0,
      blip: 0,
      shell: l.target === 1 ? 1 - (0.6 * away) / n : 1,
      sendS: l.sendS,
      arrowY: 0,
      placeholder: Math.min(Math.max(l.body, 0), 1),
      select: 1 - Math.min(Math.max(l.body * 3, 0), 1),
      hero,
      depth: l.depth.slice(),
      kick,
      active,
      amp: l.amp,
      keyP: reduced ? 0 : kp,
      dips,
    };
  };

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __tek?: unknown };
    g.__tek = {
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
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), live: L.get() }),
      /** drive the live state without touch injection: 'key' k | 'send' | 'throw' x y vx vy */
      poke: (what: string, a = 0, b = 0, c = 0, d = 0) => {
        scheduleOnUI(() => {
          'worklet';
          takeOver();
          L.modify((l) => {
            'worklet';
            if (what === 'key') pressKey(l, a);
            else if (what === 'release') l.held = -1;
            else if (what === 'send') {
              if (l.target === 0) {
                l.target = 1;
                for (let i = 0; i < l.mode.length; i++) {
                  l.mode[i] = 0;
                  l.wait[i] = 0.018 * i;
                }
              } else burst(l);
            } else if (what === 'throw') {
              for (let i = 0; i < l.mode.length; i++) {
                l.x[i] = a + i * 30;
                l.y[i] = b;
                l.vx[i] = c;
                l.vy[i] = d;
                l.mode[i] = 2;
                l.drag[i] = 0.3;
                l.s[i] = TYPE.hero;
              }
              l.returning = 0;
            }
            return l;
          });
        });
      },
    };
    return () => {
      delete g.__tek;
    };
  });

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    if (!geo) return;
    L.modify((l) => {
      'worklet';
      if (l.x.length === 0) {
        // never touched (reduced motion): start at rest
        for (let i = 0; i < geo.n; i++) {
          l.x.push(geo.fieldX[i]);
          l.y.push(FIELD_BASE);
          l.vx.push(0);
          l.vy.push(0);
          l.rot.push(0);
          l.s.push(1);
          l.lime.push(0);
          l.home.push(0);
          l.font.push(0);
          l.mode.push(0);
          l.wait.push(0);
          l.rest.push(0);
          l.drag.push(0.3);
        }
      }
      step(l, Math.min(dt, 1 / 30));
      return l;
    });
  });

  // ---- touch ----
  const toCanvas = (x: number, y: number) => {
    'worklet';
    return { x: x / S, y: y / S - oy };
  };
  const conversationY = (y: number) => {
    'worklet';
    return y < G.field.y ? G.field.y + (y - G.field.y) / conversationScale : y;
  };
  const isSendAt = (x: number, y: number) => {
    'worklet';
    const { x: left, y: top, d } = G.send;
    return Math.hypot(x - (left + d / 2), y - (top + d / 2)) < 22;
  };
  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      takeOver();
      const q = toCanvas(e.x, e.y);
      L.modify((l) => {
        'worklet';
        l.fx = q.x;
        l.fy = conversationY(q.y);
        l.fvx = 0;
        l.fvy = 0;
        const k = keyAt(q.x, q.y);
        if (isSendAt(q.x, q.y)) {
          l.finger = 3;
          l.sendS = 0.88;
          l.sendV = 0;
        } else if (k >= 0) {
          l.finger = 2;
          pressKey(l, k);
          scheduleOnRN(buzz, 'light');
        } else if (q.y < G.panelY && !reduced) {
          // a finger in the thread is a magnet: the letters leave their home and chain after it
          l.finger = 1;
          for (let i = 0; i < l.mode.length; i++) {
            l.mode[i] = 1;
            l.wait[i] = 0;
          }
          l.active = -1;
          l.returning = 0;
        }
        return l;
      });
    })
    .onUpdate((e) => {
      const q = toCanvas(e.x, e.y);
      L.modify((l) => {
        'worklet';
        l.fx = q.x;
        l.fy = conversationY(q.y);
        l.fvx = e.velocityX / S;
        l.fvy = e.velocityY / S / (q.y < G.field.y ? conversationScale : 1);
        if (l.finger === 2) {
          // sliding across the keys is a glissando
          const k = keyAt(q.x, q.y);
          if (k >= 0 && k !== l.held) {
            pressKey(l, k);
            hapticSel(l);
          }
        }
        return l;
      });
      lastTouch.set(now.get());
    })
    .onFinalize((_e, success) => {
      L.modify((l) => {
        'worklet';
        if (l.finger === 1 && !success) {
          for (let i = 0; i < l.mode.length; i++) {
            l.mode[i] = 0;
            l.wait[i] = 0.035 * i;
          }
        } else if (l.finger === 1) {
          // release throws the chain with the finger's velocity (cap 1400 pt/s) onto the keys
          const sp = Math.hypot(l.fvx, l.fvy);
          const k = sp > 1400 ? 1400 / sp : 1;
          for (let i = 0; i < l.mode.length; i++) {
            l.mode[i] = 2;
            l.drag[i] = 0.3;
            if (i === 0) {
              l.vx[i] = l.fvx * k;
              l.vy[i] = l.fvy * k;
            } else {
              const v = Math.hypot(l.vx[i], l.vy[i]);
              if (v > 1400) {
                l.vx[i] *= 1400 / v;
                l.vy[i] *= 1400 / v;
              }
            }
          }
          if (sp > 300) scheduleOnRN(buzz, 'rigid');
        }
        l.held = -1;
        l.finger = 0;
        return l;
      });
      lastTouch.set(now.get());
    });
  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e, success) => {
      if (!success) return;
      const q = toCanvas(e.x, e.y);
      if (!isSendAt(q.x, q.y)) return;
      takeOver();
      L.modify((l) => {
        'worklet';
        if (l.target === 0) {
          l.target = 1;
          for (let i = 0; i < l.mode.length; i++) {
            l.mode[i] = 0;
            l.wait[i] = 0.018 * i;
            l.vx[i] = 0;
            l.vy[i] = 0;
          }
          scheduleOnRN(buzz, 'medium');
        } else burst(l);
        l.sendV = 6;
        return l;
      });
    });
  const gesture = Gesture.Simultaneous(pan, tap);

  // ---- the picture ----
  const frame = useDerivedValue<Frame | null>(() => {
    if (!geo) return null;
    if (auto.get() === 1) return frameAt(T.get(), geo);
    return liveFrame(L.get(), geo);
  });
  const picture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, W, H));
    const f = frame.get();
    if (f && res && statics) drawScene(canvas, reduced ? { ...f, still: 1 } : f, res, statics, S, oy, conversationScale);
    return rec.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout}>
        <StatusBar style="light" />
        {size && res && statics ? (
          <Canvas style={{ width: W, height: H }} pointerEvents="none">
            <Picture picture={picture} />
          </Canvas>
        ) : null}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
});
