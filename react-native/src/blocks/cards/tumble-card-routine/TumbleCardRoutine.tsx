import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { Image as RNImage, type LayoutChangeEvent, type NativeScrollEvent, type NativeSyntheticEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector, ScrollView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  BlendMode,
  Canvas,
  Group,
  LinearGradient,
  Picture,
  Rect,
  processTransform3d,
  RoundedRect,
  type SkMatrix,
  type SkPaint,
  type SkPicture,
  Skia,
  TileMode,
  useFont,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { SvgXml } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { type Assets, type Metrics, recordOver, recordStatic, recordUnder, squircle, textW } from './tcr-cards';
import { BOLT, CHART, DROPLET, FLAME, FORK_KNIFE, MIC, PLUS, SCALE } from './tcr-icons';
import { cardPose, type Deck, frameAt, idxOf, type Lay, LOOP, mix, REST_AT, REVEAL, rolesAt, seg } from './tcr-motion';
import { GRAIN_FX } from './tcr-shaders';
import { C, CARDS, COPY, FONT, G, SPRING } from './tcr-theme';

type Props = {
  /** Plays the 9 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
  /** Day titles in the scrolling row; the first is today. */
  days?: string[];
  /** The card in front changed (0 food, 1 water, 2 weight, 3 activity). */
  onIndexChange?: (index: number) => void;
  onCardPress?: (index: number) => void;
  onAdd?: (index: number) => void;
};

type Hit = 0 | 1 | 2 | 3 | 4; // none, card, plus, stat, avatar

const EMPTY_PIC: SkPicture = (() => {
  const r = Skia.PictureRecorder();
  r.beginRecording(Skia.XYWHRect(0, 0, 1, 1));
  return r.finishRecordingAsPicture();
})();

/**
 * A health tracker's "Today's routine": one tilted card owns the stage at a time. Throw it up and it tumbles off
 * through a motion blur as long as its speed; the next one rises out of a blurred slot behind the "+" and pulls into
 * focus at the opposite tilt, its numbers rolling in. Press and hold the slot to rack focus to the next card.
 */
export function TumbleCardRoutine({ autoPlay = true, days = [...COPY.days], onIndexChange, onCardPress, onAdd }: Props) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsOk] = useFonts({
    [FONT.regular]: require('./assets/fonts/Geist-Regular.ttf'),
    [FONT.semibold]: require('./assets/fonts/Geist-SemiBold.ttf'),
  });
  const r12 = useFont(require('./assets/fonts/Geist-Regular.ttf'), 12);
  const s11 = useFont(require('./assets/fonts/Geist-SemiBold.ttf'), 11);
  const s12 = useFont(require('./assets/fonts/Geist-SemiBold.ttf'), 12);
  const s14 = useFont(require('./assets/fonts/Geist-SemiBold.ttf'), 14);
  const s15 = useFont(require('./assets/fonts/Geist-SemiBold.ttf'), 15);
  const s40 = useFont(require('./assets/fonts/Geist-SemiBold.ttf'), 40);
  const water = useImage(require('./assets/photo-water.jpg'));
  const act = useImage(require('./assets/photo-activity.jpg'));

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = W / 402;
  // canvas units are points on the 402-wide canvas; top-anchored rows follow the top inset, bottom ones the bottom
  const lay: Lay = useMemo(() => ({ tOff: insets.top / S - 62, bOff: (H - insets.bottom) / S - 840 }), [insets.top, insets.bottom, S, H]);
  const yt = (y: number) => (y + lay.tOff) * S;
  const yb = (y: number) => (y + lay.bOff) * S;
  const panelBottomU = G.panelBottom + lay.bOff;

  const A: Assets | null = useMemo(() => {
    if (!r12 || !s11 || !s12 || !s14 || !s15 || !s40 || !water || !act) return null;
    const digit: number[] = [];
    for (let d = 0; d < 10; d++) digit.push(textW(s40, String(d)));
    const metrics: Metrics = {
      digit,
      dot: textW(s40, '.'),
      cell: digit[0] - 0.06 * 40, // tabular cell, tightened to the display tracking
      unit: { kcal: 0, ml: 0, kg: 0, km: 0 },
      burned: textW(r12, COPY.actBurned),
    };
    for (const u of Object.keys(metrics.unit)) metrics.unit[u] = textW(s40, u) - 0.03 * 40 * (u.length - 1);
    const svg = (x: string) => Skia.SVG.MakeFromString(x)!;
    return {
      fonts: { r12, s11, s12, s14, s15, s40 },
      svgs: { fork: svg(FORK_KNIFE), drop: svg(DROPLET), scale: svg(SCALE), bolt: svg(BOLT), flame: svg(FLAME) },
      photos: [null, water, null, act],
      paths: CARDS.map((c) => squircle(c.w, c.h, c.r)),
      metrics,
    };
  }, [r12, s11, s12, s14, s15, s40, water, act]);
  const statics = useMemo(() => {
    if (!A) return null;
    const paper = squircle(CARDS[0].w - 16, CARDS[0].h - 44, 8, 8, 36);
    return CARDS.map((_, i) => recordStatic(i, A, GRAIN_FX, paper));
  }, [A]);

  // ---- clocks and live state ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const posLive = useSharedValue(0);
  const peekLive = useSharedValue(0);
  const dALive = useSharedValue(0);
  const rvFront = useSharedValue(REVEAL);
  const bumpLive = useSharedValue(0);
  const bumpIdxLive = useSharedValue(-1);
  const antLive = useSharedValue(0);
  const posRate = useSharedValue(0);
  const prevPos = useSharedValue(0);
  const spin = useSharedValue(0);
  const press = useSharedValue(0);
  const nod = useSharedValue(0);
  const plusPress = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(-10);
  const lastK = useSharedValue(0);
  const p0 = useSharedValue(0);
  const base = useSharedValue(0);
  const panned = useSharedValue(0);
  const peekHeld = useSharedValue(0);
  const bandHit = useSharedValue(0);
  const halfSide = useSharedValue(0);
  const hit = useSharedValue<Hit>(0);
  const deckOpacity = useSharedValue(1);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __tcr?: unknown };
    g.__tcr = {
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
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), pos: posLive.get(), peek: peekLive.get() }),
    };
    return () => {
      delete g.__tcr;
    };
  }, [T, auto, pinned, rate, posLive, peekLive]);

  const buzz = (k: 'soft' | 'light' | 'medium' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style =
        k === 'soft'
          ? Haptics.ImpactFeedbackStyle.Soft
          : k === 'light'
            ? Haptics.ImpactFeedbackStyle.Light
            : k === 'medium'
              ? Haptics.ImpactFeedbackStyle.Medium
              : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };
  const indexChanged = (i: number) => onIndexChange?.(i);
  const cardPressed = (i: number) => onCardPress?.(i);
  const added = (i: number) => onAdd?.(i);

  const spring = (c: { d: number; k: number }, velocity = 0) => {
    'worklet';
    return { damping: c.d, stiffness: c.k, mass: 1, velocity };
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      const t0 = T.get();
      const t1 = (t0 + dt) % LOOP;
      T.set(t1);
      const k0 = Math.floor(frameAt(t0).pos + 1e-6) % 4;
      const k1 = Math.floor(frameAt(t1).pos + 1e-6) % 4;
      if (k0 !== k1) scheduleOnRN(indexChanged, k1);
      return;
    }
    // finger or settle: the deck's speed (for the streak), commits, landings
    const p = posLive.get();
    const r = dt > 0 ? (p - prevPos.get()) / dt : 0;
    posRate.set(posRate.get() + (r - posRate.get()) * 0.6);
    prevPos.set(p);
    const k = Math.floor(p + 1e-6);
    if (k !== lastK.get()) {
      const up = k > lastK.get();
      lastK.set(k);
      rvFront.set(REVEAL);
      if (up && peekLive.get() > 0) peekLive.set(0); // the peeked card has landed: the next one waits in the slot
      spin.set(spin.get()); // keep it; the new front card only picks it up once it is thrown
      scheduleOnRN(indexChanged, idxOf(k));
      if (touching.get() === 0) scheduleOnRN(buzz, 'medium');
    }
    if (touching.get() === 0 && Math.abs(p - Math.round(p)) < 0.001) spin.set(0);
    // 2 s after the last touch, ease to the nearest card and restart the loop from its beat
    if (!reduced && autoPlay && touching.get() === 0 && now.get() - lastTouch.get() > 2) {
      const target = Math.round(p);
      const busy = Math.abs(p - target) > 0.002 || peekLive.get() > 0.002 || dALive.get() > 0.5 || rvFront.get() < REVEAL - 0.001;
      if (busy) {
        posLive.set(withTiming(target, { duration: 600, easing: Easing.inOut(Easing.cubic) }));
        peekLive.set(withTiming(0, { duration: 300 }));
        dALive.set(withSpring(0, spring(SPRING.glide)));
        rvFront.set(withTiming(REVEAL, { duration: 300 }));
        lastTouch.set(now.get() - 1.25); // look again in 0.75 s
      } else {
        const i = idxOf(target);
        posLive.set(i);
        prevPos.set(i);
        lastK.set(i);
        T.set(REST_AT[i]);
        auto.set(1);
      }
    }
  });

  // ---- the deck being drawn ----
  const D = useDerivedValue<Deck>(() => {
    if (auto.get() === 1) {
      const t = T.get();
      const d = frameAt(t);
      const e = 1 / 240;
      let dp = d.pos - frameAt(t - e).pos;
      if (dp > 2) dp -= 4;
      if (dp < -2) dp += 4;
      d.posRate = dp / e;
      return d;
    }
    const pos = posLive.get();
    // a peeked card that has just landed hands the slot to the next card in the same frame (the frame callback
    // clears the peek right after)
    const peek = Math.floor(pos + 1e-6) > lastK.get() ? 0 : peekLive.get();
    const { k, ha } = rolesAt(pos, peek);
    const rv = [0, 0, 0, 0];
    rv[idxOf(k)] = rvFront.get();
    rv[idxOf(k + 1)] = REVEAL * seg(ha, 0.35, 1);
    const a = antLive.get();
    return {
      pos,
      peek,
      dA: dALive.get(),
      live: 1,
      antY: 6 * a,
      antRot: 1.5 * a,
      antSliver: -6 * a,
      bumpIdx: bumpIdxLive.get(),
      bump: bumpLive.get(),
      rv,
      posRate: posRate.get(),
      spin: spin.get(),
      press: press.get(),
      nod: nod.get(),
      clk: now.get(),
    };
  });

  const fogA = useDerivedValue(() => {
    const d = D.get();
    const { ha } = rolesAt(d.pos, d.peek);
    return G.fogA - 0.55 * d.peek * (1 - seg(ha, 0.22, 0.5));
  });

  const panelClip = useMemo(() => {
    const b = panelBottomU;
    const r = G.panelR;
    return Skia.Path.MakeFromSVGString(`M0 -200 H402 V${b - r} A${r} ${r} 0 0 1 ${402 - r} ${b} H${r} A${r} ${r} 0 0 1 0 ${b - r} Z`)!;
  }, [panelBottomU]);

  // ---- touch ----
  const toU = (x: number, y: number) => {
    'worklet';
    return { x: x / S, y: y / S };
  };
  const hitButton = (x: number, y: number): Hit => {
    'worklet';
    const ry = G.rowY + lay.bOff;
    const near = (cx: number, r: number) => (x - cx) * (x - cx) + (y - ry) * (y - ry) <= r * r;
    if (near(G.plus.x, G.plus.d / 2 + 6)) return 2;
    if (near(G.stat.x, G.stat.d / 2 + 6)) return 3;
    if (near(G.me.x, G.me.d / 2 + 6)) return 4;
    return 0;
  };
  const hitCard = (x: number, y: number) => {
    'worklet';
    const d = D.get();
    const i = idxOf(Math.floor(d.pos + 1e-6));
    const p = cardPose(i, d, lay);
    const c = CARDS[i];
    const r = (-p.rot * Math.PI) / 180;
    const dx = x - p.x;
    const dy = y - p.y;
    const lx = (dx * Math.cos(r) - dy * Math.sin(r)) / p.s;
    const ly = (dx * Math.sin(r) + dy * Math.cos(r)) / p.s;
    return Math.abs(lx) <= c.w / 2 && Math.abs(ly) <= c.h / 2;
  };

  const takeOver = () => {
    'worklet';
    if (auto.get() === 1) {
      const d = frameAt(T.get());
      const pos = d.pos >= 4 - 1e-6 ? d.pos - 4 : d.pos;
      posLive.set(pos);
      prevPos.set(pos);
      lastK.set(Math.floor(pos + 1e-6));
      peekLive.set(d.peek);
      dALive.set(d.dA);
      if (d.dA > 0.5) dALive.set(withSpring(0, spring(SPRING.glide)));
      const fi = idxOf(Math.floor(pos + 1e-6));
      rvFront.set(d.rv[fi]);
      if (d.rv[fi] < REVEAL) rvFront.set(withTiming(REVEAL, { duration: (REVEAL - d.rv[fi]) * 1000, easing: Easing.linear }));
      bumpIdxLive.set(d.bumpIdx);
      bumpLive.set(d.bump);
      bumpLive.set(withTiming(0, { duration: 260 }));
      antLive.set(d.antY / 6);
      antLive.set(withTiming(0, { duration: 160 }));
      posRate.set(0);
      auto.set(0);
    }
    pinned.set(0);
    touching.set(1);
    lastTouch.set(now.get());
  };
  const release = () => {
    'worklet';
    touching.set(0);
    lastTouch.set(now.get());
  };

  const pan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .failOffsetX([-14, 14])
    .onBegin(() => {
      takeOver();
      panned.set(0);
      // touch-down during a settle grabs the deck where it is
      posLive.set(posLive.get());
    })
    .onStart(() => {
      panned.set(1);
      const p = posLive.get();
      const peek = peekLive.get();
      base.set(Math.round(p - 0.22 * peek));
      if (peek > 0.001) {
        // continue from the peek: h = 0.22 · peek is the same pose
        peekLive.set(peek);
        posLive.set(base.get() + 0.22 * peek);
      }
      p0.set(posLive.get());
      prevPos.set(posLive.get());
      bandHit.set(0);
      halfSide.set(posLive.get() - base.get() > 0.5 ? 1 : posLive.get() - base.get() < -0.5 ? -1 : 0);
      if (!reduced) scheduleOnRN(buzz, 'soft');
    })
    .onUpdate((e) => {
      if (reduced) return;
      const pull = G.pull * S;
      let p = p0.get() - e.translationY / pull;
      const hi = base.get() + 1;
      const lo = base.get() - 1;
      if (p > hi || p < lo) {
        const edge = p > hi ? hi : lo;
        const over = Math.abs(p - edge) * G.pull;
        p = edge + (Math.sign(p - edge) * (over / (1 + over / 120))) / G.pull;
        if (bandHit.get() === 0) {
          bandHit.set(1);
          scheduleOnRN(buzz, 'rigid');
        }
      }
      if (e.translationY > 6 && peekLive.get() > 0 && p < base.get() + 0.22 * peekLive.get()) {
        // dragging down from a peek: let the peeked card go back to its slot
        peekLive.set(withTiming(0, { duration: 200 }));
      }
      posLive.set(p);
      const rel = p - base.get();
      const side = rel > 0.5 ? 1 : rel < -0.5 ? -1 : 0;
      if (side !== halfSide.get()) {
        halfSide.set(side);
        scheduleOnRN(buzz, 'light');
      }
    })
    .onEnd((e, success) => {
      const pull = G.pull * S;
      if (reduced) {
        if (success && Math.abs(e.translationY) > 30) {
          const target = base.get() + (e.translationY < 0 ? 1 : -1);
          deckOpacity.set(
            withTiming(0, { duration: 100 }, () => {
              posLive.set(target);
              deckOpacity.set(withTiming(1, { duration: 100 }));
            }),
          );
          scheduleOnRN(buzz, 'light');
        }
        return;
      }
      const v = success ? -e.velocityY / pull : 0;
      const p = posLive.get();
      const origin = base.get();
      const target = success ? Math.min(Math.max(Math.round(p + v * 0.14), origin - 1), origin + 1) : origin;
      posLive.set(withSpring(target, spring(SPRING.weighty, v)));
      if (target > origin) {
        // angular momentum from the release carries the tumble
        const c = CARDS[idxOf(Math.floor(p + 1e-6))];
        const dir = c.tilt < 0 ? -1 : 1;
        const w = Math.min(Math.max(Math.abs(v) * 30, 0), 90) * dir;
        spin.set(0);
        spin.set(withTiming(w * 0.6, { duration: 600, easing: Easing.out(Easing.quad) }));
      }
      if (target === origin && peekLive.get() > 0) peekLive.set(withTiming(0, { duration: 300 }));
    })
    .onFinalize(() => {
      if (panned.get() === 0 && !reduced) {
        // a touch without a drag: settle the deck where it was going
        const p = posLive.get();
        if (Math.abs(p - Math.round(p)) > 0.001 && peekHeld.get() === 0) posLive.set(withSpring(Math.round(p), spring(SPRING.weighty)));
      }
      release();
    });

  const tap = Gesture.Tap()
    .maxDuration(450)
    .maxDistance(10)
    .onBegin((e) => {
      takeOver();
      const q = toU(e.x, e.y);
      const b = hitButton(q.x, q.y);
      const h: Hit = b !== 0 ? b : hitCard(q.x, q.y) ? 1 : 0;
      hit.set(h);
      if (h === 1 && !reduced) press.set(withTiming(1, { duration: 16 }));
      if (h === 2) plusPress.set(withTiming(1, { duration: 60 }));
    })
    .onEnd((_e, ok) => {
      const i = idxOf(Math.floor(posLive.get() + 1e-6));
      const h = hit.get();
      if (!ok) return;
      if (h === 1) {
        scheduleOnRN(cardPressed, i);
        scheduleOnRN(buzz, 'light');
      } else if (h === 2) {
        scheduleOnRN(added, i);
        scheduleOnRN(buzz, 'light');
        if (!reduced) nod.set(withSequence(withTiming(1.5, { duration: 90 }), withSpring(0, spring(SPRING.snap))));
      } else if (h === 3 || h === 4) {
        scheduleOnRN(buzz, 'light');
      }
    })
    .onFinalize(() => {
      press.set(withSpring(0, spring(SPRING.snap)));
      plusPress.set(withSpring(0, spring(SPRING.snap)));
      hit.set(0);
    });

  const hold = Gesture.LongPress()
    .minDuration(180)
    .maxDistance(4000)
    .shouldCancelWhenOutside(false)
    .onStart((e) => {
      if (reduced) return;
      const q = toU(e.x, e.y);
      if (q.y < G.sliverTop + lay.bOff || q.y > panelBottomU || hitButton(q.x, q.y) !== 0 || panned.get() === 1) return;
      takeOver();
      peekHeld.set(1);
      peekLive.set(
        withTiming(1, { duration: 300, easing: Easing.bezier(0.2, 0.8, 0.2, 1) }, (done) => {
          if (done) scheduleOnRN(buzz, 'soft');
        }),
      );
    })
    .onFinalize(() => {
      if (peekHeld.get() === 1 && panned.get() === 0) peekLive.set(withTiming(0, { duration: 300, easing: Easing.bezier(0.2, 0.8, 0.2, 1) }));
      peekHeld.set(0);
    });

  const gesture = Gesture.Simultaneous(pan, tap, hold);

  // ---- RN layers ----
  const deckSt = useAnimatedStyle(() => ({ opacity: deckOpacity.get() }));
  const plusSt = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.06 * plusPress.get() }] }));

  // the day titles: snap per title
  const [titleW, setTitleW] = useState<number[]>(() => days.map(() => 0));
  const [day, setDay] = useState(0);
  const titleOffsets = useMemo(() => {
    const out: number[] = [];
    let x = 0;
    for (let i = 0; i < days.length; i++) {
      out.push(x + titleW[i] / 2 - titleW[0] / 2);
      x += titleW[i] + G.titleGap * S;
    }
    return out;
  }, [titleW, days.length, S]);
  const onTitleScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const x = e.nativeEvent.contentOffset.x;
    let best = 0;
    for (let i = 1; i < titleOffsets.length; i++) if (Math.abs(titleOffsets[i] - x) < Math.abs(titleOffsets[best] - x)) best = i;
    if (best !== day) {
      setDay(best);
      Haptics.selectionAsync().catch(() => {});
    }
  };

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const ff = (w: 'r' | 's') => (fontsOk ? { fontFamily: w === 'r' ? FONT.regular : FONT.semibold } : { fontWeight: w === 'r' ? ('400' as const) : ('600' as const) });
  const rowY = yb(G.rowY);

  return (
    <View style={styles.root} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <View style={[styles.stage, { height: yb(G.panelBottom), borderBottomLeftRadius: G.panelR * S, borderBottomRightRadius: G.panelR * S }]}>
          <Text allowFontScaling={false} style={[styles.date, ff('r'), { top: yt(G.dateY) - 9 * S, fontSize: 13 * S, lineHeight: 18 * S }]}>
            {COPY.date}
          </Text>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            style={[styles.titles, { top: yt(G.titleY) - 16 * S, height: 32 * S }]}
            contentContainerStyle={{ paddingLeft: 201 * S - titleW[0] / 2, paddingRight: W }}
            snapToOffsets={titleOffsets}
            decelerationRate="fast"
            scrollEventThrottle={32}
            onScroll={onTitleScroll}
          >
            {days.map((d, i) => (
              <Text
                key={d}
                allowFontScaling={false}
                onLayout={(e) => {
                  const w = e.nativeEvent.layout.width;
                  setTitleW((prev) => (Math.abs(prev[i] - w) < 0.5 ? prev : prev.map((v, j) => (j === i ? w : v))));
                }}
                style={[
                  styles.title,
                  ff('s'),
                  { fontSize: 22 * S, lineHeight: 32 * S, letterSpacing: -0.44 * S, marginRight: i < days.length - 1 ? G.titleGap * S : 0, color: i === day ? C.ink : C.ink3 },
                ]}
              >
                {d}
              </Text>
            ))}
          </ScrollView>

          {/* the deck: one canvas, clipped to the white panel */}
          {size && A && statics && (
            <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, deckSt]}>
              <Canvas style={{ width: W, height: yb(G.panelBottom) }}>
                <Group transform={[{ scale: S }]} clip={panelClip}>
                  <Group zIndex={3}>
                    <Rect x={0} y={G.fogTop + lay.bOff} width={402} height={G.panelBottom - G.fogTop} opacity={fogA}>
                      <LinearGradient
                        start={vec(0, G.fogTop + lay.bOff)}
                        end={vec(0, G.panelBottom + lay.bOff)}
                        colors={['rgba(251,251,251,0)', 'rgba(251,251,251,1)']}
                      />
                    </Rect>
                  </Group>
                  {CARDS.map((_, i) => (
                    <DeckCard key={i} i={i} A={A} pic={statics[i]} D={D} lay={lay} />
                  ))}
                </Group>
              </Canvas>
            </Animated.View>
          )}

          {/* the row under the deck */}
          <View
            accessible
            accessibilityRole="button"
            accessibilityLabel="Statistics"
            style={[styles.round, { left: (G.stat.x - G.stat.d / 2) * S, top: rowY - (G.stat.d / 2) * S, width: G.stat.d * S, height: G.stat.d * S, backgroundColor: C.statFill }]}
          >
            <SvgXml xml={CHART} width={20 * S} height={20 * S} />
          </View>
          <Animated.View
            accessible
            accessibilityRole="button"
            accessibilityLabel="Add"
            onAccessibilityTap={() => added(idxOf(Math.floor(posLive.get() + 1e-6)))}
            style={[styles.round, { left: (G.plus.x - G.plus.d / 2) * S, top: rowY - (G.plus.d / 2) * S, width: G.plus.d * S, height: G.plus.d * S, backgroundColor: C.plusFill }, plusSt]}
          >
            <SvgXml xml={PLUS} width={18 * S} height={18 * S} />
          </Animated.View>
          <View
            accessible
            accessibilityRole="button"
            accessibilityLabel="Profile"
            style={[styles.round, styles.me, { left: (G.me.x - G.me.d / 2) * S, top: rowY - (G.me.d / 2) * S, width: G.me.d * S, height: G.me.d * S }]}
          >
            <RNImage source={require('./assets/avatar.jpg')} style={{ width: (G.me.d - 2) * S, height: (G.me.d - 2) * S, borderRadius: 999 }} />
          </View>
        </View>
      </GestureDetector>

      {/* ask field and suggestions on the dark page under the panel */}
      <View style={[styles.ask, { left: G.ask.x * S, top: yb(G.ask.y), width: G.ask.w * S, height: G.ask.h * S, borderRadius: G.ask.r * S, paddingLeft: 16 * S, paddingRight: 14 * S }]}>
        <Text allowFontScaling={false} style={[styles.askText, ff('r'), { fontSize: 15 * S }]}>
          {COPY.ask}
        </Text>
        <SvgXml xml={MIC} width={20 * S} height={20 * S} />
      </View>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={[styles.chips, { top: yb(G.chips.y), height: G.chips.h * S }]}
        contentContainerStyle={{ paddingHorizontal: G.chips.x * S, gap: G.chips.gap * S }}
      >
        {COPY.chips.map((c) => (
          <View key={c} style={[styles.chip, { height: G.chips.h * S, borderRadius: G.chips.r * S, paddingHorizontal: G.chips.pad * S }]}>
            <Text allowFontScaling={false} style={[styles.chipText, ff('r'), { fontSize: 13 * S }]}>
              {c}
            </Text>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

/** One card of the deck: its pose, its blurs and its three pictures, all derived on the UI thread. */
function DeckCard({ i, A, pic, D, lay }: { i: number; A: Assets; pic: SkPicture; D: SharedValue<Deck>; lay: Lay }) {
  const c = CARDS[i];
  const pose = useDerivedValue(() => cardPose(i, D.get(), lay));
  const z = useDerivedValue(() => pose.get().z);
  const clip = useDerivedValue(() => {
    const p = pose.get();
    if (!p.vis) return Skia.XYWHRect(0, 0, 0, 0);
    const R = 0.5 * Math.sqrt(c.w * c.w + c.h * c.h) * p.s * G.cardScale + 64 + p.blurLen + 3 * p.D;
    return Skia.XYWHRect(p.x - R, p.y - R, 2 * R, 2 * R + 30);
  });
  const outerM = useDerivedValue<SkMatrix>(() => {
    const p = pose.get();
    const m = Skia.Matrix();
    m.translate(p.x, p.y);
    m.rotate(p.blurLen >= 1 ? p.blurAng : 0);
    return m;
  });
  const innerM = useDerivedValue(() => {
    const p = pose.get();
    const sc = p.s * G.cardScale;
    return processTransform3d([
      { rotate: (p.rot * Math.PI) / 180 - (p.blurLen >= 1 ? p.blurAng : 0) },
      { perspective: 900 },
      { rotateX: (p.rx * Math.PI) / 180 },
      { scale: sc },
      { translateX: -c.w / 2 },
      { translateY: -c.h / 2 },
    ]);
  });
  // the card's 3 pt edge, seen only while it is tipped in depth
  const edgeY = useDerivedValue(() => 3 * seg(Math.abs(pose.get().rx), 2, 12) * (pose.get().rx >= 0 ? -1 : 1));
  const edgeA = useDerivedValue(() => seg(Math.abs(pose.get().rx), 2, 12) * pose.get().alpha);
  // the streak: a Gaussian along the velocity (the layer is drawn in the velocity's frame), as long as the speed
  const motionLayer = useDerivedValue<SkPaint | false>(() => {
    const p = pose.get();
    if (!p.vis || (p.blurLen < 1 && p.alpha > 0.999)) return false;
    const paint = Skia.Paint();
    paint.setAlphaf(p.alpha);
    if (p.blurLen >= 1) paint.setImageFilter(Skia.ImageFilter.MakeBlur(0.35 * p.blurLen, 0, TileMode.Decal, null));
    return paint;
  });
  // depth of field: uniform (the peeked front card) or graded from y0 down over 120 pt (the slot), mixed exactly so
  // the sharp edge never shows through the blurred one
  const depthLayer = useDerivedValue<SkPaint | false>(() => {
    const p = pose.get();
    if (!p.vis || p.D < 0.3) return false;
    const paint = Skia.Paint();
    const blurred = Skia.ImageFilter.MakeBlur(p.D, p.D, TileMode.Decal, null);
    if (p.U >= 1) {
      paint.setImageFilter(blurred);
      return paint;
    }
    const grad = Skia.Shader.MakeLinearGradient(vec(0, p.y0), vec(0, p.y0 + 120), [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,1)')], null, TileMode.Clamp);
    const mask = Skia.ImageFilter.MakeShader(grad, false, null);
    const bm = Skia.ImageFilter.MakeBlend(BlendMode.SrcIn, mask, blurred);
    const sm = Skia.ImageFilter.MakeBlend(BlendMode.SrcOut, mask, null);
    paint.setImageFilter(Skia.ImageFilter.MakeBlend(BlendMode.Plus, sm, bm));
    return paint;
  });
  const under = useDerivedValue(() => {
    const p = pose.get();
    if (!p.vis) return EMPTY_PIC;
    const shadowK = p.role === 0 ? 1 : p.role === 1 ? mix(0.35, 1, seg(p.s, 0.9, 1)) : 0.35;
    return recordUnder(i, A, p.rv, p.rot, p.lift, shadowK);
  });
  const over = useDerivedValue(() => {
    const p = pose.get();
    if (!p.vis) return EMPTY_PIC;
    return recordOver(i, A, p.rv, p.soft);
  });
  return (
    <Group zIndex={z} clip={clip}>
      <Group matrix={outerM}>
        <Group layer={motionLayer}>
          <Group matrix={innerM}>
            <Group layer={depthLayer}>
              <Picture picture={under} />
              <RoundedRect x={0} y={edgeY} width={c.w} height={c.h} r={c.r} color={c.shadow} opacity={edgeA} />
              <Picture picture={pic} />
              <Picture picture={over} />
            </Group>
          </Group>
        </Group>
      </Group>
    </Group>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.under, overflow: 'hidden' },
  stage: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: C.stage, overflow: 'hidden', borderCurve: 'continuous' },
  date: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: C.ink2 },
  titles: { position: 'absolute', left: 0, right: 0 },
  title: { color: C.ink },
  round: { position: 'absolute', alignItems: 'center', justifyContent: 'center', borderRadius: 999, overflow: 'hidden' },
  me: { borderWidth: 1, borderColor: C.avatarRing, backgroundColor: C.avatarRing },
  ask: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: C.field, borderCurve: 'continuous' },
  askText: { color: C.fieldInk },
  chips: { position: 'absolute', left: 0, right: 0 },
  chip: { justifyContent: 'center', backgroundColor: C.field, borderCurve: 'continuous' },
  chipText: { color: C.chipInk },
});
