import {
  BackdropBlur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  Image,
  LinearGradient,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  useImage,
  vec,
  type SkImage,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useMotionPreference } from '@/shared/useMotionPreference';

import { FLY, HOLD, ITEMS, LOOP, PAID, S, STRETCH, TOTAL, bagStretch, easeInOut, script, seg, smooth } from './mbc-motion';
import { type Cue, useMbcAudio } from './use-mbc-audio';

/*
 Market Bag Checkout. A sunny kitchen counter; the basket's five items in a frosted list; a cotton net
 bag hanging from a brass hook. Tap an item (or start paying) and it lifts out of its row and drops into
 the bag in an arc; the bag stretches with the weight and bounces each time, its knotted net (drawn
 knot by knot) bulging and the baguette sticking out of the top. Hold to pay fills the button; at the
 end the drawstring cinches the top shut, the bag hops on its hook and the button reads Paid.
 Art: the counter and the five items are image renders; the bag, its net, the cords, the hook and every
 move are Skia; the list and the button are real views.
 autoPlay: LOOP = 10 s: rest 0-1, the items drop in 1.0-2.83 (every 0.32 s), hold to pay 3.0-4.25, the
 cinch and hop, paid until 8.55, then everything goes back to the list (8.55-9.4), rest from 9.5.
*/

const C = {
  ink: '#1F2A22',
  mute: '#6C7568',
  green: '#1F3A2C',
  greenFill: '#3F7A57',
  cream: '#F2EAD8',
  cord: '#EDE3CC',
  cordDark: '#B9A988',
  card: 'rgba(255,253,248,0.66)',
};
const NU = 9; // net columns
const NV = 9; // net rows

type St = {
  auto: number;
  t: number;
  now: number;
  touchAt: number;
  packAt: number[]; // when each item left its row for the bag, or -1
  unpackAt: number[]; // when each item left the bag back for its row, or -1
  holding: number;
  hold: number;
  paidAt: number;
  resetAt: number;
  ticks: number;
  pose: { fly: number[]; land: number[]; leave: number[]; hold: number; paid: number; reset: number; bag: number };
  open: number;
};

/** Half the bag's width at depth v (0 the rim, 1 the bottom). */
function halfWidth(v: number, rim: number, belly: number) {
  'worklet';
  if (v < 0.5) {
    const k = Math.sin((Math.PI / 2) * (v / 0.5));
    return rim + (belly - rim) * k;
  }
  const b = (v - 0.5) / 0.5;
  return belly * Math.sqrt(Math.max(0, 1 - b * b * 0.92));
}

type Props = { autoPlay?: boolean };

export function MarketBagCheckout({ autoPlay = false }: Props) {
  const reduced = useMotionPreference();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [paidState, setPaidState] = useState(false);
  const [fontsOk] = useFonts({
    'MBC-Archivo-ExtraBold': require('./assets/fonts/Archivo-ExtraBold.ttf'),
    'MBC-Archivo-SemiBold': require('./assets/fonts/Archivo-SemiBold.ttf'),
    'MBC-Archivo-Medium': require('./assets/fonts/Archivo-Medium.ttf'),
    'MBC-Mono': require('./assets/fonts/JetBrainsMono-Medium.ttf'),
  });
  const counter = useImage(require('./assets/counter.jpg'));
  const imgs: (SkImage | null)[] = [
    useImage(require('./assets/avocado.webp')),
    useImage(require('./assets/milk.webp')),
    useImage(require('./assets/lemons.webp')),
    useImage(require('./assets/tomatoes.webp')),
    useImage(require('./assets/baguette.webp')),
  ];
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  // ------------------------------------------------------------------ geometry
  const W = size?.width ?? 402;
  const H = size?.height ?? 874;
  const top = insets.top;
  const CX = 16;
  const CW = W - 32;
  const CY = top + 108;
  const ROW = 46;
  const CH = 12 + ROW * ITEMS.length + 46;
  const THUMB = 38;
  const TX = CX + 14 + THUMB / 2;
  const rowY = (i: number) => CY + 12 + ROW / 2 + ROW * i;
  const BTN_H = 56;
  const BTN_Y = H - insets.bottom - 16 - BTN_H;
  const HOOKY = CY + CH + 26;
  const RIMY = HOOKY + 56;
  const BAGX = W / 2;
  const LEN0 = Math.max(120, Math.min(160, BTN_Y - RIMY - STRETCH - 34));

  // ------------------------------------------------------------------ state
  const st = useSharedValue<St>({
    auto: 0, t: 0, now: 0, touchAt: -10,
    packAt: [-1, -1, -1, -1, -1], unpackAt: [-1, -1, -1, -1, -1],
    holding: 0, hold: 0, paidAt: -10, resetAt: -10, ticks: 0,
    pose: { fly: [0, 0, 0, 0, 0], land: [-1, -1, -1, -1, -1], leave: [-1, -1, -1, -1, -1], hold: 0, paid: -1, reset: 0, bag: 0 }, open: 0,
  });
  const tick = useSharedValue(0);
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __marketBag?: unknown };
    g.__marketBag = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), manual: () => debugT.set(-3) };
    return () => {
      delete g.__marketBag;
    };
  }, [debugT]);

  const play = useMbcAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  // ------------------------------------------------------------------ the clock
  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const s = st.get();
    s.now += dt;
    const now = s.now;
    const dbg = debugT.get();

    if (dbg === -3 && s.auto === 1) {
      s.auto = 0;
      for (let i = 0; i < ITEMS.length; i++) {
        s.packAt[i] = -1;
        s.unpackAt[i] = -1;
      }
      s.hold = 0;
      s.paidAt = -10;
    }
    let anyPacked = false;
    for (let i = 0; i < ITEMS.length; i++) if (s.packAt[i] >= 0 && s.unpackAt[i] < 0) anyPacked = true;
    if (auto && dbg !== -3 && s.auto === 0 && !anyPacked && s.hold < 0.001 && now - s.touchAt > 2 && s.paidAt < 0 && now - s.resetAt > 1.2) {
      s.auto = 1;
      s.t = 0;
    }
    if (dbg >= 0 && s.auto === 0) s.auto = 1;

    if (s.auto === 1) {
      let t = s.t;
      if (dbg >= 0) t = dbg;
      else if (dbg !== -2) {
        t += dt;
        if (t >= LOOP) t -= LOOP;
      }
      s.t = t;
      const p = script(t);
      s.pose = { fly: p.fly, land: p.land, leave: p.leave, hold: p.hold, paid: p.paid, reset: p.reset, bag: t };
    } else {
      // hold to pay: fills while held, drains when let go
      if (s.paidAt < 0) {
        if (s.holding === 1) {
          s.hold = Math.min(1, s.hold + dt / HOLD);
          const step = Math.floor(s.hold * 10);
          if (step !== s.ticks) {
            s.ticks = step;
            scheduleOnRN(buzz, 0);
          }
          if (s.hold >= 1) {
            s.paidAt = now;
            s.holding = 0;
            scheduleOnRN(buzz, 2);
            scheduleOnRN(cue, 'paid');
            scheduleOnRN(cue, 'cinch');
          }
        } else s.hold = Math.max(0, s.hold - dt * 2.4);
      }
      const fly: number[] = [];
      const land: number[] = [];
      const leave: number[] = [];
      for (let i = 0; i < ITEMS.length; i++) {
        const pa = s.packAt[i];
        const ua = s.unpackAt[i];
        if (pa < 0) {
          fly.push(0);
          land.push(-1);
          leave.push(-1);
        } else if (ua < 0) {
          const f = easeInOut(seg(now - pa, 0, FLY));
          if (now - pa - dt < FLY && now - pa >= FLY) {
            scheduleOnRN(buzz, 1);
            scheduleOnRN(cue, 'drop');
          }
          fly.push(f);
          land.push(now - pa >= FLY ? pa + FLY : -1);
          leave.push(-1);
        } else {
          const f = 1 - easeInOut(seg(now - ua, 0, FLY * 0.8));
          fly.push(f);
          land.push(pa + FLY);
          leave.push(ua);
          if (f <= 0 && now - ua > 1) {
            s.packAt[i] = -1;
            s.unpackAt[i] = -1;
          }
        }
      }
      const reset = s.resetAt > 0 ? seg(now - s.resetAt, 0, 0.35) : 0;
      if (s.resetAt > 0 && now - s.resetAt > 0.35) {
        s.paidAt = -10;
        s.hold = 0;
      }
      const hold = s.paidAt >= 0 ? 1 - reset : s.hold;
      s.pose = { fly, land, leave, hold, paid: s.paidAt >= 0 ? now - s.paidAt : -1, reset, bag: now };
    }
    const open = s.pose.paid >= 0 && s.pose.reset < 0.5 ? 1 : 0;
    if (open !== s.open) {
      s.open = open;
      scheduleOnRN(setPaidState, open === 1);
    }
    if (dbg >= 0) debugT.set(-2);
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // ------------------------------------------------------------------ actions
  /** A touch takes over from the script with the same items in the bag. */
  const interrupt = (s: St) => {
    'worklet';
    s.touchAt = s.now;
    if (s.auto === 0) return;
    s.auto = 0;
    const t = s.t;
    for (let i = 0; i < ITEMS.length; i++) {
      const t0 = S.pack0 + i * S.packGap;
      const t1 = S.unpack + (ITEMS.length - 1 - i) * S.unpackGap;
      if (t >= S.rest || t < t0) {
        s.packAt[i] = -1;
        s.unpackAt[i] = -1;
      } else {
        s.packAt[i] = s.now - (t - t0);
        s.unpackAt[i] = t >= t1 ? s.now - (t - t1) : -1;
      }
    }
    s.hold = s.pose.hold;
    s.paidAt = t >= S.hold[1] && t < S.unpack ? s.now - (t - S.hold[1]) : -10;
    s.resetAt = t >= S.unpack && t < S.rest ? s.now - (t - S.unpack) : -10;
  };
  const toggleItem = (i: number) => {
    st.modify((s) => {
      'worklet';
      interrupt(s);
      if (s.paidAt >= 0) return s;
      if (s.packAt[i] < 0) {
        s.packAt[i] = s.now;
        s.unpackAt[i] = -1;
      } else if (s.unpackAt[i] < 0 && s.now - s.packAt[i] > FLY) s.unpackAt[i] = s.now;
      return s;
    });
    cue('lift');
  };
  const holdStart = () => {
    st.modify((s) => {
      'worklet';
      interrupt(s);
      if (s.paidAt >= 0) {
        // paid: a tap starts a new basket
        s.resetAt = s.now;
        for (let i = 0; i < ITEMS.length; i++) if (s.packAt[i] >= 0 && s.unpackAt[i] < 0) s.unpackAt[i] = s.now + (ITEMS.length - 1 - i) * S.unpackGap;
        return s;
      }
      s.holding = 1;
      s.ticks = 0;
      // anything still in the list goes into the bag first
      let k = 0;
      for (let i = 0; i < ITEMS.length; i++) {
        if (s.packAt[i] < 0) {
          s.packAt[i] = s.now + k * 0.18;
          s.unpackAt[i] = -1;
          k++;
        }
      }
      return s;
    });
  };
  const holdEnd = () => {
    st.modify((s) => {
      'worklet';
      s.holding = 0;
      s.touchAt = s.now;
      return s;
    });
  };

  // ------------------------------------------------------------------ the bag
  const bag = useDerivedValue(() => {
    tick.get();
    const pose = st.get().pose;
    const stretch = bagStretch(pose.land, pose.leave, pose.bag);
    const paid = pose.paid;
    const cinch = paid >= 0 ? easeInOut(seg(paid, PAID.cinch[0], PAID.cinch[1])) * (1 - pose.reset) : 0;
    const hopU = paid >= 0 ? seg(paid, PAID.hop[0], PAID.hop[1]) : 0;
    const hop = Math.sin(Math.PI * hopU) * 16 * (1 - pose.reset);
    const len = LEN0 + STRETCH * stretch;
    let fill = 0;
    for (let i = 0; i < ITEMS.length; i++) if (pose.land[i] >= 0) fill += 0.2;
    const rim = 58 - 34 * cinch;
    const belly = 52 + 38 * Math.min(1, stretch * 1.3);
    const top0 = RIMY - hop;
    // the outline
    const L: number[] = [];
    const R: number[] = [];
    const N = 24;
    for (let i = 0; i <= N; i++) {
      const v = i / N;
      const hw = halfWidth(v, rim, belly);
      const y = top0 + v * len;
      L.push(BAGX - hw, y);
      R.push(BAGX + hw, y);
    }
    const ob = Skia.PathBuilder.Make().moveTo(L[0], L[1]);
    for (let i = 1; i <= N; i++) ob.lineTo(L[i * 2], L[i * 2 + 1]);
    for (let i = N; i >= 0; i--) ob.lineTo(R[i * 2], R[i * 2 + 1]);
    ob.close();
    // the net: knots on a diamond lattice, each row's knots shifted half a step from the last
    const net = Skia.PathBuilder.Make();
    const knots = Skia.PathBuilder.Make();
    const kx: number[] = [];
    const ky: number[] = [];
    for (let r = 0; r <= NV; r++) {
      const v = r / NV;
      const hw = halfWidth(v, rim, belly);
      const off = r % 2 === 0 ? 0 : 0.5;
      for (let c = 0; c <= NU; c++) {
        const u = Math.min(1, (c + off) / NU);
        // the net wraps round the bag: knots near the sides crowd together
        const a = (u - 0.5) * Math.PI;
        const x = BAGX + Math.sin(a) * hw;
        // knots sag a little between the items' weight
        const y = top0 + v * len + Math.cos(a) * 3 * v * (0.5 + fill);
        kx.push(x);
        ky.push(y);
      }
    }
    for (let r = 0; r < NV; r++) {
      for (let c = 0; c <= NU; c++) {
        const a = r * (NU + 1) + c;
        const even = r % 2 === 0;
        const c1 = even ? c - 1 : c;
        const c2 = even ? c : c + 1;
        if (c1 >= 0) net.moveTo(kx[a], ky[a]).lineTo(kx[(r + 1) * (NU + 1) + c1], ky[(r + 1) * (NU + 1) + c1]);
        if (c2 <= NU) net.moveTo(kx[a], ky[a]).lineTo(kx[(r + 1) * (NU + 1) + c2], ky[(r + 1) * (NU + 1) + c2]);
        if (r > 0 && r < NV) knots.addCircle(kx[a], ky[a], 1.5);
      }
    }
    // the rim's hem and the drawstring with its wooden toggle
    const hem = Skia.PathBuilder.Make().moveTo(BAGX - rim, top0).quadTo(BAGX, top0 + 6 + 4 * cinch, BAGX + rim, top0).build();
    // the two handles up to the hook
    const handles = Skia.PathBuilder.Make()
      .moveTo(BAGX - rim + 4, top0)
      .cubicTo(BAGX - rim - 10, top0 - 30, BAGX - 18, HOOKY - 8 - hop * 0.2, BAGX - 2, HOOKY)
      .moveTo(BAGX + rim - 4, top0)
      .cubicTo(BAGX + rim + 10, top0 - 30, BAGX + 18, HOOKY - 8 - hop * 0.2, BAGX + 2, HOOKY)
      .build();
    return { outline: ob.build(), net: net.build(), knots: knots.build(), hem, handles, top0, len, belly, rim, cinch, hop };
  });
  const bagOutline = useDerivedValue(() => bag.get().outline);
  const bagNet = useDerivedValue(() => bag.get().net);
  const bagKnots = useDerivedValue(() => bag.get().knots);
  const bagHem = useDerivedValue(() => bag.get().hem);
  const bagHandles = useDerivedValue(() => bag.get().handles);
  const netShadowT = useDerivedValue(() => [{ translateX: 1.2 }, { translateY: 1.6 }]);
  const bagShadowT = useDerivedValue(() => [{ translateX: 10 }, { translateY: 12 + bag.get().hop * 0.3 }]);
  const toggleX = useDerivedValue(() => BAGX + 6);
  const toggleY = useDerivedValue(() => bag.get().top0 + 10 + 10 * bag.get().cinch);

  /** Where item i is drawn: in its row, on its way, or in its place in the bag. */
  const itemPose = (i: number) => {
    'worklet';
    const s = st.get();
    const b = bag.get();
    const f = s.pose.fly[i];
    const it = ITEMS[i];
    const sx = BAGX + it.slot[0] * b.belly * 1.5;
    const sy = b.top0 + it.slot[1] * b.len + it.size * 0.22;
    const fx = TX;
    const fy = CY + 12 + ROW / 2 + ROW * i;
    const e = f;
    const apex = Math.min(fy, sy) - 70;
    const x = fx + (sx - fx) * e;
    const y = (1 - e) * (1 - e) * fy + 2 * (1 - e) * e * apex + e * e * sy;
    const sc = (THUMB + (it.size - THUMB) * Math.min(1, e * 1.4)) / it.size;
    const rot = it.rot * e + Math.sin(Math.PI * e) * 0.6;
    return { x, y, sc, rot, f };
  };
  const itemT0 = useDerivedValue(() => {
    tick.get();
    const p = itemPose(0);
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rot }, { scale: p.sc }];
  });
  const itemT1 = useDerivedValue(() => {
    tick.get();
    const p = itemPose(1);
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rot }, { scale: p.sc }];
  });
  const itemT2 = useDerivedValue(() => {
    tick.get();
    const p = itemPose(2);
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rot }, { scale: p.sc }];
  });
  const itemT3 = useDerivedValue(() => {
    tick.get();
    const p = itemPose(3);
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rot }, { scale: p.sc }];
  });
  const itemT4 = useDerivedValue(() => {
    tick.get();
    const p = itemPose(4);
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rot }, { scale: p.sc }];
  });
  const itemTs = [itemT0, itemT1, itemT2, itemT3, itemT4];
  // in the bag (under the net) once past the rim, over everything while in the air
  const inA0 = useDerivedValue(() => (tick.get() >= 0 && st.get().pose.fly[0] > 0.86 ? 1 : 0));
  const inA1 = useDerivedValue(() => (tick.get() >= 0 && st.get().pose.fly[1] > 0.86 ? 1 : 0));
  const inA2 = useDerivedValue(() => (tick.get() >= 0 && st.get().pose.fly[2] > 0.86 ? 1 : 0));
  const inA3 = useDerivedValue(() => (tick.get() >= 0 && st.get().pose.fly[3] > 0.86 ? 1 : 0));
  const inA4 = useDerivedValue(() => (tick.get() >= 0 && st.get().pose.fly[4] > 0.86 ? 1 : 0));
  const inAs = [inA0, inA1, inA2, inA3, inA4];
  const outA0 = useDerivedValue(() => 1 - inA0.get());
  const outA1 = useDerivedValue(() => 1 - inA1.get());
  const outA2 = useDerivedValue(() => 1 - inA2.get());
  const outA3 = useDerivedValue(() => 1 - inA3.get());
  const outA4 = useDerivedValue(() => 1 - inA4.get());
  const outAs = [outA0, outA1, outA2, outA3, outA4];
  // the drawing order inside the bag: back items first (the baguette last, it sticks out at the top)
  const ORDER = [3, 1, 0, 2, 4];

  // ------------------------------------------------------------------ RN layers
  const rowStyle = (i: number) => {
    'worklet';
    const f = st.get().pose.fly[i];
    return { opacity: 1 - 0.55 * smooth(0, 0.6, f) };
  };
  const r0 = useAnimatedStyle(() => (tick.get() >= 0 ? rowStyle(0) : {}));
  const r1 = useAnimatedStyle(() => (tick.get() >= 0 ? rowStyle(1) : {}));
  const r2 = useAnimatedStyle(() => (tick.get() >= 0 ? rowStyle(2) : {}));
  const r3 = useAnimatedStyle(() => (tick.get() >= 0 ? rowStyle(3) : {}));
  const r4 = useAnimatedStyle(() => (tick.get() >= 0 ? rowStyle(4) : {}));
  const rowStyles = [r0, r1, r2, r3, r4];
  const checkStyle = (i: number) => {
    'worklet';
    const f = st.get().pose.fly[i];
    const k = smooth(0.7, 1, f);
    return { opacity: k, transform: [{ scale: 0.6 + 0.4 * k }] };
  };
  const c0 = useAnimatedStyle(() => (tick.get() >= 0 ? checkStyle(0) : {}));
  const c1 = useAnimatedStyle(() => (tick.get() >= 0 ? checkStyle(1) : {}));
  const c2 = useAnimatedStyle(() => (tick.get() >= 0 ? checkStyle(2) : {}));
  const c3 = useAnimatedStyle(() => (tick.get() >= 0 ? checkStyle(3) : {}));
  const c4 = useAnimatedStyle(() => (tick.get() >= 0 ? checkStyle(4) : {}));
  const checkStyles = [c0, c1, c2, c3, c4];
  const fillStyle = useAnimatedStyle(() => {
    tick.get();
    return { transform: [{ scaleX: Math.max(0.0001, st.get().pose.hold) }] };
  });
  const payLabel = useAnimatedStyle(() => {
    tick.get();
    const p = st.get().pose;
    const k = p.paid >= 0 ? smooth(0.15, 0.4, p.paid) * (1 - p.reset) : 0;
    return { opacity: 1 - k, transform: [{ translateY: -8 * k }] };
  });
  const paidLabel = useAnimatedStyle(() => {
    tick.get();
    const p = st.get().pose;
    const k = p.paid >= 0 ? smooth(0.25, 0.55, p.paid) * (1 - p.reset) : 0;
    return { opacity: k, transform: [{ translateY: 8 * (1 - k) }] };
  });
  const btnStyle = useAnimatedStyle(() => {
    tick.get();
    const s = st.get();
    return { transform: [{ scale: s.holding === 1 ? 0.97 : 1 }] };
  });

  const ready = fontsOk && size !== null;

  return (
    <View style={styles.screen} onLayout={(e) => setSize({ width: e.nativeEvent.layout.width, height: e.nativeEvent.layout.height })}>
      {size && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          {counter && <Image image={counter} x={0} y={0} width={W} height={H} fit="cover" />}
          <Rect x={0} y={0} width={W} height={top + 120}>
            <LinearGradient start={vec(0, 0)} end={vec(0, top + 120)} colors={['rgba(250,246,238,0.85)', 'rgba(250,246,238,0)']} />
          </Rect>

          {/* the list: frosted glass */}
          <BackdropBlur blur={16} clip={Skia.RRectXY(Skia.XYWHRect(CX, CY, CW, CH), 24, 24)}>
            <RoundedRect x={CX} y={CY} width={CW} height={CH} r={24} color={C.card} />
          </BackdropBlur>
          <RoundedRect x={CX + 0.5} y={CY + 0.5} width={CW - 1} height={CH - 1} r={23.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.75)" />
          {ITEMS.map((it, i) => (
            <Circle key={it.key} cx={TX} cy={rowY(i)} r={THUMB / 2 + 1} color="rgba(31,42,34,0.06)" />
          ))}

          {/* the hook on the wall */}
          <Circle cx={BAGX + 3} cy={HOOKY + 4} r={9} color="rgba(40,30,20,0.25)">
            <BlurMask blur={4} style="normal" />
          </Circle>
          <Circle cx={BAGX} cy={HOOKY} r={8}>
            <RadialGradient c={vec(BAGX - 3, HOOKY - 3)} r={10} colors={['#F6DFA2', '#C79A45', '#7C5A22']} />
          </Circle>

          {/* the bag: its shadow on the wall, the cords, the items in it, the net over them */}
          <Group transform={bagShadowT}>
            <Path path={bagOutline} color="rgba(60,50,30,0.16)">
              <BlurMask blur={14} style="normal" />
            </Path>
          </Group>
          <Path path={bagHandles} style="stroke" strokeWidth={4} strokeCap="round" color={C.cordDark} />
          <Path path={bagHandles} style="stroke" strokeWidth={2.6} strokeCap="round" color={C.cord} />
          {ORDER.map((i) => (
            <Group key={`in${i}`} transform={itemTs[i]} opacity={inAs[i]}>
              {imgs[i] && <Image image={imgs[i]} x={-ITEMS[i].size / 2} y={-ITEMS[i].size / 2} width={ITEMS[i].size} height={ITEMS[i].size} fit="contain" />}
            </Group>
          ))}
          <Path path={bagOutline} color="rgba(242,234,216,0.10)" />
          <Group transform={netShadowT}>
            <Path path={bagNet} style="stroke" strokeWidth={1.6} color="rgba(60,45,25,0.35)" />
          </Group>
          <Path path={bagNet} style="stroke" strokeWidth={1.5} color={C.cream} />
          <Path path={bagKnots} color="#FBF5E6" />
          <Path path={bagHem} style="stroke" strokeWidth={5} strokeCap="round" color={C.cordDark} />
          <Path path={bagHem} style="stroke" strokeWidth={3.6} strokeCap="round" color={C.cord} />
          <RoundedRect x={toggleX} y={toggleY} width={9} height={14} r={4} color="#9C6B3C" />

          {/* items on their way (and still in the list) */}
          {ITEMS.map((it, i) => (
            <Group key={`out${it.key}`} transform={itemTs[i]} opacity={outAs[i]}>
              {imgs[i] && <Image image={imgs[i]} x={-it.size / 2} y={-it.size / 2} width={it.size} height={it.size} fit="contain" />}
            </Group>
          ))}
        </Canvas>
      )}

      {ready && (
        <>
          <View style={[styles.head, { top: top + 12 }]} pointerEvents="none">
            <Text style={styles.eyebrow} maxFontSizeMultiplier={1.3}>CORNER GREENGROCER · PICKUP</Text>
            <Text style={styles.title} accessibilityRole="header" maxFontSizeMultiplier={1.2}>Your basket</Text>
          </View>

          <View style={[styles.list, { left: CX, top: CY + 12, width: CW }]}>
            {ITEMS.map((it, i) => (
              <Pressable key={it.key} onPress={() => toggleItem(i)} style={styles.row} accessibilityRole="button" accessibilityLabel={`${it.name}, ${it.meta}, $${it.price.toFixed(2)}`} accessibilityHint="Puts it in the bag, or takes it out">
                <Animated.View style={[styles.rowInner, rowStyles[i]]}>
                  <View style={styles.rowText}>
                    <Text style={styles.name} numberOfLines={1}>{it.name}</Text>
                    <Text style={styles.meta} numberOfLines={1}>{it.meta}</Text>
                  </View>
                  <Text style={styles.price}>${it.price.toFixed(2)}</Text>
                </Animated.View>
                <Animated.View style={[styles.tick, checkStyles[i]]}>
                  <Svg width={12} height={12} viewBox="0 0 12 12">
                    <SvgPath d="M2.5 6.3 L5 8.6 L9.6 3.6" stroke="#FFFFFF" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
                  </Svg>
                </Animated.View>
              </Pressable>
            ))}
            <View style={styles.totalRow}>
              <Text style={styles.totalLabel}>Total</Text>
              <Text style={styles.total}>${TOTAL.toFixed(2)}</Text>
            </View>
          </View>

          <Animated.View style={[styles.btnWrap, { left: 16, right: 16, top: BTN_Y }, btnStyle]}>
            <Pressable
              onPressIn={holdStart}
              onPressOut={holdEnd}
              style={styles.btn}
              accessibilityRole="button"
              accessibilityLabel={paidState ? `Paid $${TOTAL.toFixed(2)}. Tap to start a new basket` : `Hold to pay $${TOTAL.toFixed(2)}`}
            >
              <Animated.View style={[styles.fill, fillStyle]} />
              <Animated.View style={[styles.btnLabel, payLabel]}>
                <Text style={styles.btnText}>Hold to pay ${TOTAL.toFixed(2)}</Text>
              </Animated.View>
              <Animated.View style={[styles.btnLabel, paidLabel]}>
                <Svg width={18} height={18} viewBox="0 0 18 18">
                  <SvgPath d="M4 9.4 L7.6 12.8 L14.2 5.6" stroke={C.cream} strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
                </Svg>
                <Text style={[styles.btnText, styles.paidText]}>Paid · ready at the counter</Text>
              </Animated.View>
            </Pressable>
          </Animated.View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EFE9DD', overflow: 'hidden' },
  head: { position: 'absolute', left: 24, right: 24 },
  eyebrow: { fontFamily: 'MBC-Mono', fontSize: 11, letterSpacing: 1.6, color: C.mute },
  title: { fontFamily: 'MBC-Archivo-ExtraBold', fontSize: 36, lineHeight: 40, color: C.ink, letterSpacing: -0.7, marginTop: 8 },
  list: { position: 'absolute' },
  row: { height: 46, justifyContent: 'center' },
  rowInner: { flexDirection: 'row', alignItems: 'center', paddingLeft: 66, paddingRight: 18 },
  rowText: { flex: 1 },
  name: { fontFamily: 'MBC-Archivo-SemiBold', fontSize: 15, color: C.ink },
  meta: { fontFamily: 'MBC-Archivo-Medium', fontSize: 12, color: C.mute, marginTop: 1 },
  price: { fontFamily: 'MBC-Mono', fontSize: 14, color: C.ink },
  tick: { position: 'absolute', left: 44, top: 6, width: 18, height: 18, borderRadius: 9, backgroundColor: C.greenFill, alignItems: 'center', justifyContent: 'center' },
  totalRow: { height: 46, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 18, borderTopWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(31,42,34,0.16)', marginTop: 0 },
  totalLabel: { fontFamily: 'MBC-Archivo-SemiBold', fontSize: 15, color: C.mute },
  total: { fontFamily: 'MBC-Archivo-ExtraBold', fontSize: 20, color: C.ink },
  btnWrap: { position: 'absolute', height: 56 },
  btn: { flex: 1, borderRadius: 28, backgroundColor: C.green, overflow: 'hidden', justifyContent: 'center' },
  fill: { position: 'absolute', left: 0, top: 0, bottom: 0, right: 0, backgroundColor: C.greenFill, transformOrigin: 'left' },
  btnLabel: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  btnText: { fontFamily: 'MBC-Archivo-SemiBold', fontSize: 17, color: C.cream },
  paidText: { marginLeft: 8 },
});

export { LOOP };
