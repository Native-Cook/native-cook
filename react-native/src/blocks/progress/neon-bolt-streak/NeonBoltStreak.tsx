import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector, State } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  FilterMode,
  Group,
  ImageShader,
  LinearGradient,
  Mask,
  MipmapMode,
  Paint,
  Path,
  processTransform3d,
  RadialGradient,
  Rect,
  Shader,
  Shadow,
  Skia,
  Text,
  useFont,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { caughtAt, clamp01, easeOut, frameAt, ignTimes, type Live, liveFrameAt, LOOP, makeLive, mix, seg } from './nbs-motion';
import { FINISH_FX, HERO_FX, WEEK_FX } from './nbs-shaders';
import { C, cellX, DAYS, G, STRIKE_SCALE, TIP_LOW, TIP_TOP, TODAY, TUBE_D } from './nbs-theme';
import { type NbsCue, useNbsAudio } from './use-nbs-audio';

/** Skia runtime effects are read as Display P3 on iOS: re-encode the sRGB colours there. */
const P3 = 1;
/** The atlases are raw data: Skia hands an untagged PNG to the shader as stored (checked on device: 1 bends the outline). */
const DATA_FIX = 0;
const LINEAR = { filter: FilterMode.Linear, mipmap: MipmapMode.None };
const TUBE = Skia.Path.MakeFromSVGString(TUBE_D)!;

type Props = {
  /** Plays the 9.5 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
};

/**
 * A week of habit as seven neon tubes on a dark panel. Striking today's dead zinc bolt makes the gas stutter, catch at
 * the electrodes and burn up both sides of the outline; its light lands on the panel and the neighbours, the count
 * rolls and the hero bolt fills. Seven of seven sends current round the panel's edge and floods the hero.
 */
export function NeonBoltStreak({ autoPlay = true }: Props) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const dayAtlas = useImage(require('./assets/bolt-day-sdf.png'));
  const heroAtlas = useImage(require('./assets/bolt-hero-sdf.png'));
  const zinc = useImage(require('./assets/zinc-grain.png'));
  const grain = useImage(require('./assets/grain.png'));
  const countFont = useFont(require('./assets/fonts/Inter-ExtraBold.ttf'), G.countSize);
  const wordFont = useFont(require('./assets/fonts/Inter-Bold.ttf'), G.wordSize);
  const dayFont = useFont(require('./assets/fonts/Inter-Medium.ttf'), G.labelSize);
  const { play: playCue } = useNbsAudio(true);

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const halfContent = Math.max(437 - G.heroY, G.panelY + G.panelH - 437);
  const safeHalfHeight = H / 2 - Math.max(insets.top, insets.bottom);
  const S = Math.max(0.01, Math.min(W / 402, safeHalfHeight / halfContent));
  const sceneLeft = (W - 402 * S) / 2;
  const oy = (H / S - 874) / 2;
  const PR = PixelRatio.get();

  // ---- clocks and live state ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const live = useSharedValue<Live>(makeLive(frameAt(0), 0, 0));
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(-10);
  const pressX = useSharedValue(0);
  const pressY = useSharedValue(0);
  const pressT = useSharedValue(0);
  const moved = useSharedValue(0);
  const lastCell = useSharedValue(-1);
  const countPress = useSharedValue(0);
  const lpFired = useSharedValue(0);
  const lastTick = useSharedValue(0);
  const touchX = useSharedValue(0);
  const touchY = useSharedValue(0);
  const touchA = useSharedValue(0);
  const dataFix = useSharedValue(DATA_FIX);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __nbs?: unknown };
    g.__nbs = {
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
      data: (v: number) => {
        dataFix.set(v);
      },
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), count: live.get().cTo, restT: live.get().restT }),
    };
    return () => {
      delete g.__nbs;
    };
  }, [T, auto, pinned, rate, live, dataFix]);

  const buzz = (k: 'soft' | 'rigid' | 'sel' | 'success') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else if (k === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  };
  const cue = (c: NbsCue) => {
    playCue(c);
  };

  /** A finger takes over: the autoplay's moment becomes the live state, nothing jumps. */
  const takeover = () => {
    'worklet';
    if (auto.get() !== 1) return;
    const t = T.get();
    live.set(makeLive(frameAt(t), t, now.get()));
    auto.set(0);
    pinned.set(0);
  };

  /** The first day that is not lit yet: the one a tap or a hold strikes. */
  const nextDay = (L: Live, n: number) => {
    'worklet';
    for (let k = 0; k < 7; k++) if (!caughtAt(L, k, n) && L.kind[k] !== 1 && L.kind[k] !== 5) return k;
    return -1;
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    const prev = now.get();
    const n = prev + dt;
    now.set(n);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    const L = live.get();

    // contacts and catches of running ignitions
    for (let k = 0; k < 7; k++) {
      const kd = L.kind[k];
      if (kd !== 1 && kd !== 5) continue;
      const { c, kc } = ignTimes(kd === 5);
      const a = prev - L.ev[k];
      const b = n - L.ev[k];
      if (a < c && b >= c) {
        scheduleOnRN(buzz, 'rigid');
        scheduleOnRN(cue, 'zap');
      }
      if (a < kc && b >= kc) {
        scheduleOnRN(buzz, 'soft');
        scheduleOnRN(cue, 'tick');
      }
    }

    // the count follows the lit days; seven of seven closes the circuit
    let target = 0;
    for (let k = 0; k < 7; k++) if (caughtAt(L, k, n)) target++;
    if (target !== L.cTo) {
      const cur = liveFrameAt(L, n, reduced).count;
      const up = target > L.cTo;
      live.modify((v) => {
        'worklet';
        v.cFrom = cur;
        v.cTo = target;
        v.cT = n;
        if (up && v.restT < 0) {
          v.flT = n;
          v.flS = target === 7 ? 1 : 0.45;
        }
        if (target === 7 && up && v.restT < 0) {
          v.circT = n;
          v.circR = -1;
        }
        return v;
      });
    }
    if (L.circT >= 0 && n - L.circT >= 1.1 && prev - L.circT < 1.1) scheduleOnRN(buzz, 'success');

    // charging: a selection train, faster as the charge rises
    if (L.chargeT >= 0 && n - L.chargeT > 0.12) {
      const charge = Math.min((n - L.chargeT) / 1.2, 1);
      if (n - lastTick.get() > mix(0.12, 0.05, charge)) {
        lastTick.set(n);
        scheduleOnRN(buzz, 'sel');
      }
    }

    // long-press the count: replay the last celebration
    if (countPress.get() === 1 && touching.get() === 1 && lpFired.get() === 0 && moved.get() === 0 && n - pressT.get() > 0.5) {
      lpFired.set(1);
      scheduleOnRN(buzz, 'soft');
      live.modify((v) => {
        'worklet';
        if (v.cTo >= 7) {
          v.circT = n;
          v.circR = -1;
        } else {
          v.flT = n;
          v.flS = 1;
        }
        return v;
      });
    }

    // 2 s after the last touch, once everything has settled, ease home and restart the loop
    if (!autoPlay || reduced) return;
    if (L.restT >= 0) {
      if (n - L.restT > 0.95) {
        T.set(0);
        auto.set(1);
      }
      return;
    }
    if (touching.get() === 1 || n - lastTouch.get() < 2 || L.chargeT >= 0) return;
    for (let k = 0; k < 7; k++) {
      const s = n - L.ev[k];
      const kd = L.kind[k];
      if ((kd === 1 || kd === 5) && s < 1.4) return;
      if (kd >= 2 && kd <= 4 && s < 0.9) return;
    }
    if (L.circT >= 0 && n - L.circT < 1.9) return;
    if (n - L.cT < 0.7) return;
    const cur = liveFrameAt(L, n, reduced);
    live.modify((v) => {
      'worklet';
      for (let k = 0; k < 7; k++) {
        if (k < 3) {
          if (cur.burn[k] < 1.02 || cur.lit[k] < 0.99) {
            v.kind[k] = 3;
            v.ev[k] = n;
            v.f0[k] = cur.fill[k];
            v.l0[k] = cur.lit[k];
            v.lab0[k] = cur.label[k];
          }
        } else if (cur.burn[k] > 0.005 || cur.lit[k] > 0.005 || cur.label[k] > 0.005) {
          v.kind[k] = 4;
          v.ev[k] = n;
          v.l0[k] = cur.lit[k];
          v.f0[k] = cur.fill[k];
          v.lab0[k] = cur.label[k];
        }
      }
      v.cFrom = cur.count;
      v.cTo = 3;
      v.cT = n;
      if (cur.borderLit > 0 || cur.flood > 0) {
        v.circR = n;
        v.bl0 = cur.borderLit;
        v.flood0 = cur.flood;
      }
      v.circT = -1;
      v.restT = n;
      return v;
    });
  });

  // ---- the frame being drawn ----
  const F = useDerivedValue(() => (auto.get() === 1 ? frameAt(T.get()) : liveFrameAt(live.get(), now.get(), reduced)));

  // ---- gestures (live use has no camera move, so the screen maps straight onto the wide scene) ----
  const cellAt = (sx: number, sy: number) => {
    'worklet';
    if (sy < G.panelY || sy > G.panelY + G.panelH || sx < G.panelX || sx > G.panelX + G.panelW) return -1;
    return Math.min(6, Math.floor((sx - G.panelX) / G.cell));
  };
  const press = (x: number, y: number) => {
    'worklet';
    takeover();
    const sx = (x - sceneLeft) / S;
    const sy = y / S - oy;
    const n = now.get();
    touching.set(1);
    pressX.set(sx);
    pressY.set(sy);
    pressT.set(n);
    moved.set(0);
    lpFired.set(0);
    touchX.set(sx);
    touchY.set(sy);
    touchA.set(withTiming(1, { duration: 120 }));
    const cell = cellAt(sx, sy);
    lastCell.set(cell);
    countPress.set(Math.abs(sx - G.countX) < 80 && sy > G.countBase - 120 && sy < G.countBase + 20 ? 1 : 0);
    const L = live.get();
    if (cell >= 0 && cell === nextDay(L, n) && L.restT < 0) {
      scheduleOnRN(buzz, 'soft');
      live.modify((v) => {
        'worklet';
        v.chargeT = n;
        v.chargeDay = cell;
        v.cancelT = -1;
        return v;
      });
    }
  };
  /** Re-strike one weekday using the same ignition/flicker envelopes as a tap. */
  const restrike = (day: number, n: number) => {
    'worklet';
    if (day < 0 || day > TODAY) return;
    const L = live.get();
    const kd = L.kind[day];
    const s = n - L.ev[day];
    const lit = caughtAt(L, day, n);
    if (reduced && lit) return;
    const igniting = (kd === 1 || kd === 5) && s < ignTimes(kd === 5).kc + ignTimes(kd === 5).dur;
    if (igniting || reduced) {
      // Finish a running ignition without stacking another one. Reduced motion uses a steady light-up.
      const cur = liveFrameAt(L, n, reduced);
      live.modify((v) => {
        'worklet';
        v.kind[day] = 3;
        v.ev[day] = n;
        v.f0[day] = cur.fill[day];
        v.l0[day] = cur.lit[day];
        v.lab0[day] = cur.label[day];
        v.restT = -1;
        return v;
      });
      return;
    }
    live.modify((v) => {
      'worklet';
      // A lit past day replays its existing two-failed-strikes flicker; a dead eligible day runs the full ignition.
      v.kind[day] = lit ? 2 : 1;
      v.chg[day] = 0;
      v.ev[day] = n;
      v.restT = -1;
      return v;
    });
  };
  const move = (x: number, y: number) => {
    'worklet';
    const sx = (x - sceneLeft) / S;
    const sy = y / S - oy;
    touchX.set(sx);
    touchY.set(sy);
    const dist = Math.hypot(sx - pressX.get(), sy - pressY.get());
    if (dist > 30 && moved.get() === 0) {
      moved.set(1);
      if (live.get().chargeT >= 0) {
        const n = now.get();
        live.modify((v) => {
          'worklet';
          v.chargeT = -1;
          v.cancelT = n;
          return v;
        });
      }
    }
    const cell = cellAt(sx, sy);
    const previous = lastCell.get();
    if (moved.get() === 0 || cell === previous) return;
    lastCell.set(cell);
    if (cell < 0) return;

    // A fast move can cross several cells between frames. Visit each one, with one haptic for the sweep.
    const n = now.get();
    const step = cell > previous ? 1 : -1;
    let struck = false;
    for (let entered = previous < 0 ? cell : previous + step; entered !== cell + step; entered += step) {
      if (entered > TODAY) continue;
      restrike(entered, n);
      struck = true;
    }
    if (struck && n - lastTick.get() >= 0.1) {
      lastTick.set(n);
      scheduleOnRN(buzz, 'sel');
    }
  };
  const release = (cancelled: boolean) => {
    'worklet';
    const n = now.get();
    touchA.set(withTiming(0, { duration: 220 }));
    touching.set(0);
    lastTouch.set(n);
    const L = live.get();
    if (cancelled) {
      countPress.set(0);
      if (L.chargeT >= 0) {
        live.modify((v) => {
          'worklet';
          v.chargeT = -1;
          v.cancelT = n;
          return v;
        });
      }
      return;
    }
    if (L.chargeT >= 0) {
      const hold = n - L.chargeT;
      const charge = hold > 0.12 ? Math.min(hold / 1.2, 1) : 0;
      const day = L.chargeDay;
      live.modify((v) => {
        'worklet';
        v.chargeT = -1;
        v.restT = -1;
        if (reduced) {
          v.kind[day] = 3;
          v.f0[day] = 0;
          v.l0[day] = 0;
          v.lab0[day] = 0;
        } else {
          v.kind[day] = 1;
          v.chg[day] = charge;
        }
        v.ev[day] = n;
        return v;
      });
      return;
    }
    if (moved.get() === 1 || n - pressT.get() > 0.4) return;
    const cell = cellAt(pressX.get(), pressY.get());
    if (cell < 0) return;
    const kd = L.kind[cell];
    const s = n - L.ev[cell];
    const igniting = (kd === 1 || kd === 5) && s < ignTimes(kd === 5).kc + ignTimes(kd === 5).dur;
    const cur = liveFrameAt(L, n, reduced);
    if (igniting) {
      // a tap on a tube that is still igniting completes it at once
      live.modify((v) => {
        'worklet';
        v.kind[cell] = 3;
        v.ev[cell] = n;
        v.f0[cell] = cur.fill[cell];
        v.l0[cell] = cur.lit[cell];
        v.lab0[cell] = cur.label[cell];
        return v;
      });
    } else if (caughtAt(L, cell, n) && !reduced) {
      live.modify((v) => {
        'worklet';
        v.kind[cell] = 2;
        v.ev[cell] = n;
        return v;
      });
    }
  };
  const gesture = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      press(e.x, e.y);
    })
    .onUpdate((e) => {
      move(e.x, e.y);
    })
    .onFinalize((e) => {
      // A stationary tap may end before Pan activates. Only a native cancellation discards it.
      release(e.state === State.CANCELLED);
    });

  // dev only: drive the same press / move / release worklets as a finger, in scene points (touch injection is not
  // available on the batch simulator)
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __nbs?: Record<string, unknown> };
    if (!g.__nbs) return;
    Object.assign(g.__nbs, {
      down: (x: number, y: number) => scheduleOnUI(press, sceneLeft + x * S, (y + oy) * S),
      move: (x: number, y: number) => scheduleOnUI(move, sceneLeft + x * S, (y + oy) * S),
      up: () => scheduleOnUI(release, false),
      live: () => ({ count: live.get().cTo, kind: live.get().kind, chargeT: live.get().chargeT, restT: live.get().restT, auto: auto.get() }),
    });
  });

  // ---- camera: one 3D matrix for the whole scene ----
  const camM = useDerivedValue(() => {
    const c = F.get().cam;
    const z = Math.pow(G.zoom, c);
    const fx = cellX(TODAY) + G.focusDX;
    const fy = G.boltCY;
    return processTransform3d([
      { translateX: sceneLeft },
      { scale: S },
      { translateY: oy },
      { translateX: mix(fx, G.focusSX, c) },
      { translateY: mix(fy, G.focusSY, c) },
      { perspective: G.persp },
      { rotateX: (G.rotX * c * Math.PI) / 180 },
      { rotateZ: (G.rotZ * c * Math.PI) / 180 },
      { scale: z },
      { translateX: -fx },
      { translateY: -fy },
    ]);
  });
  const aa = useDerivedValue(() => 0.6 / (S * PR * Math.pow(G.zoom, F.get().cam)));

  // ---- shader uniforms ----
  const weekU = useDerivedValue(() => {
    const f = F.get();
    const rimL = [0, 0, 0, 0, 0, 0, 0];
    const rimR = [0, 0, 0, 0, 0, 0, 0];
    const blur = [0, 0, 0, 0, 0, 0, 0];
    for (let k = 0; k < 7; k++) {
      const on = (j: number) => {
        'worklet';
        return j < 0 || j > 6 ? 0 : Math.min(f.lit[j], 1.3) * clamp01(f.fill[j]);
      };
      rimL[k] = on(k - 1) + 0.25 * on(k - 2);
      rimR[k] = on(k + 1) + 0.25 * on(k + 2);
      blur[k] = f.cam * Math.min(Math.abs(k - TODAY), 2) * 0.5;
    }
    return {
      uP3: P3,
      uData: dataFix.get(),
      uAA: aa.get(),
      uLit: f.lit,
      uFill: f.fill,
      uBurn: f.burn,
      uHead: f.head,
      uAfter: f.after,
      uPop: f.pop,
      uDy: f.dy,
      uRimL: rimL,
      uRimR: rimR,
      uBlur: blur,
      uBorder: f.border,
      uBorderLit: f.borderLit,
      uBorderAfter: f.borderAfter,
      uTime: f.time,
      uLife: f.life,
      uOff: mix(3, 6, f.cam),
      uTouch: [touchX.get(), touchY.get(), touchA.get()],
    };
  });
  const heroU = useDerivedValue(() => {
    const f = F.get();
    return {
      uP3: P3,
      uData: dataFix.get(),
      uAA: aa.get(),
      uLevel: f.level,
      uBloom: f.bloom,
      uFlash: f.flash,
      uGhost: f.ghost,
      uGhostTint: f.ghostTint,
    };
  });
  const finishU = useMemo(() => ({ uSize: [W, H] }), [W, H]);

  // ---- the count's drum ----
  const widths = useMemo(() => {
    if (!countFont) return null;
    return Array.from({ length: 10 }, (_, i) => countFont.measureText(String(i)).width);
  }, [countFont]);
  const drum = useDerivedValue(() => {
    const p = F.get().count;
    const n = Math.floor(p + 1e-4);
    const fr = p - n;
    const a = ((n % 10) + 10) % 10;
    const b = (a + 1) % 10;
    const wa = widths ? widths[a] : 70;
    const wb = widths ? widths[b] : 70;
    return {
      ta: String(a),
      tb: String(b),
      xa: G.countX - wa / 2,
      xb: G.countX - wb / 2,
      ya: G.countBase - fr * G.countStep,
      yb: G.countBase + (1 - fr) * G.countStep,
    };
  });
  const dTa = useDerivedValue(() => drum.get().ta);
  const dTb = useDerivedValue(() => drum.get().tb);
  const dXa = useDerivedValue(() => drum.get().xa);
  const dXb = useDerivedValue(() => drum.get().xb);
  const dYa = useDerivedValue(() => drum.get().ya);
  const dYb = useDerivedValue(() => drum.get().yb);
  const drumBlur = useDerivedValue(() => vec(0, Math.min(3, (Math.abs(F.get().countV) * G.countStep) / 60 * 0.35)));

  const wordX = useMemo(() => (wordFont ? 201 - wordFont.measureText('days streak').width / 2 : 130), [wordFont]);
  // colours change by cross-fading fixed-colour layers (numbers animate on the UI thread; no colour strings per frame)
  const wordA = useDerivedValue(() => F.get().words);
  const labelX = useMemo(() => DAYS.map((d, i) => cellX(i) - (dayFont ? dayFont.measureText(d).width / 2 : 12)), [dayFont]);
  // each derived value reads F itself: Reanimated subscribes only to shared values in the worklet's own closure
  const la0 = useDerivedValue(() => clamp01(F.get().label[0]));
  const la1 = useDerivedValue(() => clamp01(F.get().label[1]));
  const la2 = useDerivedValue(() => clamp01(F.get().label[2]));
  const la3 = useDerivedValue(() => clamp01(F.get().label[3]));
  const la4 = useDerivedValue(() => clamp01(F.get().label[4]));
  const la5 = useDerivedValue(() => clamp01(F.get().label[5]));
  const la6 = useDerivedValue(() => clamp01(F.get().label[6]));
  const aa0 = useDerivedValue(() => clamp01(F.get().label[0]) * F.get().after[0] * 0.8);
  const aa1 = useDerivedValue(() => clamp01(F.get().label[1]) * F.get().after[1] * 0.8);
  const aa2 = useDerivedValue(() => clamp01(F.get().label[2]) * F.get().after[2] * 0.8);
  const aa3 = useDerivedValue(() => clamp01(F.get().label[3]) * F.get().after[3] * 0.8);
  const aa4 = useDerivedValue(() => clamp01(F.get().label[4]) * F.get().after[4] * 0.8);
  const aa5 = useDerivedValue(() => clamp01(F.get().label[5]) * F.get().after[5] * 0.8);
  const aa6 = useDerivedValue(() => clamp01(F.get().label[6]) * F.get().after[6] * 0.8);
  const labelLit = [la0, la1, la2, la3, la4, la5, la6];
  const labelAmber = [aa0, aa1, aa2, aa3, aa4, aa5, aa6];
  const dofB = useDerivedValue(() => 3.2 * F.get().cam);
  const dofA = useDerivedValue(() => 1 - 0.6 * F.get().cam);
  const joltM = useDerivedValue(() => [{ translateY: F.get().jolt }]);

  // ---- the strike bolt, its streak and the charge arc ----
  const strike = useDerivedValue(() => {
    const f = F.get();
    const d = f.sDay;
    if (d < 0) return { on: 0, x: 0, y: 0, sc: 0, lx: 0, ly: 0, spike: 0, streak: 0 };
    const lx = cellX(d) - 20 + TIP_TOP.x;
    const ly = G.boltCY - 30 + TIP_TOP.y + f.dy[d];
    const x = mix(lx + 6, lx, f.sDrop);
    const y = mix(ly - f.sH, ly, f.sDrop);
    const grow = f.sH > 100 || f.sH === 40 ? 1 : easeOut(seg(f.sDrop, 0, 0.18));
    const sc = STRIKE_SCALE * mix(1, 0.7, f.sDrop) * (1 - f.sAbsorb) * grow;
    return { on: 1, x, y, sc, lx, ly, spike: f.sDrop >= 1 ? 1 - f.sAbsorb : 0, streak: (f.sV * 2) / 60 };
  });
  const strikeM = useDerivedValue(() => {
    const s = strike.get();
    return processTransform3d([{ translateX: s.x }, { translateY: s.y }, { scale: Math.max(s.sc, 0.0001) }, { translateX: -TIP_LOW.x }, { translateY: -TIP_LOW.y }]);
  });
  const strikeOn = useDerivedValue(() => strike.get().on);
  const haloA = useDerivedValue(() => 0.5 + 1.5 * strike.get().spike);
  const streakRect = useDerivedValue(() => {
    const s = strike.get();
    const len = s.on && s.streak > 1 ? Math.min(s.streak, 60) : 0;
    return Skia.XYWHRect(s.x - 0.6, s.y - len - 6 * s.sc, 1.2, len);
  });
  const streakFrom = useDerivedValue(() => vec(0, streakRect.get().y));
  const streakTo = useDerivedValue(() => vec(0, streakRect.get().y + streakRect.get().height));
  const arcPath = useDerivedValue(() => {
    const f = F.get();
    const s = strike.get();
    const b = Skia.PathBuilder.Make();
    if (f.arc <= 0 || !s.on) return b.detach();
    const seed = Math.floor(now.get() * 30);
    const rnd = (i: number) => {
      'worklet';
      const v = Math.sin((seed * 12.9898 + i * 78.233) * 1.0) * 43758.5453;
      return v - Math.floor(v) - 0.5;
    };
    const x0 = s.x;
    const y0 = s.y + 1;
    const x1 = s.lx;
    const y1 = s.ly;
    b.moveTo(x0, y0);
    for (let i = 1; i < 3; i++) {
      const k = i / 3;
      b.lineTo(mix(x0, x1, k) + rnd(i) * 9 * f.arc, mix(y0, y1, k) + rnd(i + 7) * 3);
    }
    b.lineTo(x1, y1);
    return b.detach();
  });
  const arcA = useDerivedValue(() => F.get().arc);

  // ---- the hero's flash ----
  const flashR = useDerivedValue(() => F.get().flashR);
  const flashA = useDerivedValue(() => F.get().flash);

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const ready = size && dayAtlas && heroAtlas && zinc && grain;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout}>
        <StatusBar style="light" />
        {ready && (
          <Canvas style={[StyleSheet.absoluteFill, { width: W, height: H }]} pointerEvents="none">
            <Rect x={0} y={0} width={W} height={H} color={C.page} />
            <Group matrix={camM}>
              {/* hero bolt and ghost bolts */}
              <Rect x={G.heroRX} y={G.heroRY} width={G.heroRW} height={G.heroRH}>
                <Shader source={HERO_FX} uniforms={heroU}>
                  <ImageShader image={heroAtlas} fit="fill" rect={{ x: 0, y: 0, width: 256, height: 384 }} tx="clamp" ty="clamp" sampling={LINEAR} />
                </Shader>
              </Rect>
              {/* the count and its word: out of focus while the camera is in close on the week */}
              <Group opacity={dofA} layer={<Paint><Blur blur={dofB} mode="decal" /></Paint>}>
              <Mask
                mode="alpha"
                mask={
                  <Rect x={0} y={G.countBase - 112} width={402} height={128}>
                    <LinearGradient
                      start={vec(0, G.countBase - 112)}
                      end={vec(0, G.countBase + 16)}
                      colors={['rgba(0,0,0,0)', '#000', '#000', 'rgba(0,0,0,0)']}
                      positions={[0, 0.1, 0.9, 1]}
                    />
                  </Rect>
                }
              >
                <Text x={dXa} y={dYa} text={dTa} font={countFont} color={C.ink}>
                  <Blur blur={drumBlur} mode="decal">
                    <Shadow dx={0} dy={0} blur={3} color="rgba(38,39,43,0.6)" />
                  </Blur>
                </Text>
                <Text x={dXb} y={dYb} text={dTb} font={countFont} color={C.ink}>
                  <Blur blur={drumBlur} mode="decal">
                    <Shadow dx={0} dy={0} blur={3} color="rgba(38,39,43,0.6)" />
                  </Blur>
                </Text>
              </Mask>
              <Text x={wordX} y={G.wordBase} text="days streak" font={wordFont} color={C.word} />
              <Text x={wordX} y={G.wordBase} text="days streak" font={wordFont} color={C.wordLit} opacity={wordA} />
              </Group>
              {/* the week panel and its tubes */}
              <Group transform={joltM}>
                <Rect x={G.weekX} y={G.weekY} width={G.weekW} height={G.weekH}>
                  <Shader source={WEEK_FX} uniforms={weekU}>
                    <ImageShader image={dayAtlas} fit="fill" rect={{ x: 0, y: 0, width: 128, height: 192 }} tx="clamp" ty="clamp" sampling={LINEAR} />
                    <ImageShader image={zinc} fit="fill" rect={{ x: 0, y: 0, width: 128, height: 128 }} tx="repeat" ty="repeat" sampling={LINEAR} />
                  </Shader>
                </Rect>
                {DAYS.map((d, i) => (
                  <Group key={d}>
                    <Text x={labelX[i]} y={G.labelBase} text={d} font={dayFont} color={C.dayDead} />
                    <Text x={labelX[i]} y={G.labelBase} text={d} font={dayFont} color={C.dayLit} opacity={labelLit[i]} />
                    <Text x={labelX[i]} y={G.labelBase} text={d} font={dayFont} color={C.dayAfter} opacity={labelAmber[i]} />
                  </Group>
                ))}
              </Group>
              {/* strike bolt: streak, glow, tube, core; and the charge arc */}
              <Group opacity={strikeOn}>
                <Rect rect={streakRect}>
                  <LinearGradient start={streakFrom} end={streakTo} colors={['rgba(255,217,26,0)', 'rgba(255,217,26,0.7)']} />
                </Rect>
                <Path path={arcPath} style="stroke" strokeWidth={6} color={C.bloom} opacity={arcA} blendMode="plus" strokeJoin="round">
                  <BlurMask blur={3} style="normal" />
                </Path>
                <Path path={arcPath} style="stroke" strokeWidth={2} color={C.core} opacity={arcA} strokeJoin="round" />
                <Group matrix={strikeM}>
                  <Path path={TUBE} style="stroke" strokeWidth={12} color={C.bloom} opacity={haloA} blendMode="plus" strokeJoin="round">
                    <BlurMask blur={9} style="normal" respectCTM />
                  </Path>
                  <Path path={TUBE} style="stroke" strokeWidth={4.6} color={C.tube} strokeJoin="round" />
                  <Path path={TUBE} style="stroke" strokeWidth={1.8} color={C.core} strokeJoin="round" />
                </Group>
              </Group>
              {/* the hero's yellow flash: light on everything */}
              <Circle cx={201} cy={296} r={flashR} opacity={flashA} blendMode="plus">
                <RadialGradient c={vec(201, 296)} r={flashR} colors={['rgba(245,196,0,0.55)', 'rgba(245,196,0,0.22)', 'rgba(245,196,0,0)']} positions={[0, 0.45, 1]} />
              </Circle>
            </Group>
            <Rect x={0} y={0} width={W} height={H}>
              <Shader source={FINISH_FX} uniforms={finishU}>
                <ImageShader image={grain} fit="fill" rect={{ x: 0, y: 0, width: 256, height: 256 }} tx="repeat" ty="repeat" sampling={LINEAR} />
              </Shader>
            </Rect>
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page, overflow: 'hidden' },
});
