import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useState } from 'react';
import { AccessibilityInfo, AppState, PixelRatio, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  cancelAnimation,
  Easing,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, G, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { Coin, Gem } from './Badges';
import { Board, type BoardGeo, STAT_GLYPHS } from './Board';
import { ChevronLeft, Globe, Meh, Pin, Send, Share, Star, ThumbUp } from './icons';
import {
  type Change,
  chipEdges,
  CITIES,
  clamp01,
  coinAt,
  COVER,
  easeOut,
  EXPAND,
  FOLLOW,
  type Hold,
  HOLD_SCRIPT,
  holdAt,
  NO_HOLD,
  LABEL,
  loopChange,
  LOOP,
  pillAt,
  PRESS,
  pressAt,
  RANK,
  RANK_WORDS,
  RESUME_AFTER,
  SCRIPT,
  SETTLE,
  seg,
  SHEEN,
  STRIP,
  STRIP_SCRIPT,
  stripAt,
} from './motion';
import { GlyphView, type RollGroup, RollWords } from './Roll';
import { barUnder, chartRect, HOLD_REST, type HoldView, TileChart } from './TileChart';
import {
  ASCENT,
  box,
  colors,
  FONT,
  followIng,
  glyphRuns,
  type GlyphRun,
  radius,
  REF_INSET,
  REF_PT,
  REF_W,
  text,
  type TextSpec,
} from './theme';

export { LOOP };

type Props = {
  /** Plays all → london → all every LOOP seconds; touch takes over and hands back 2 s after the last touch. */
  autoPlay?: boolean;
};

/** Clock modes: looping on the script, or following the last tap. */
const AUTO = 2;
const MANUAL = 1;

type Rec = { from: number; to: number; start: number; l0: number; r0: number; a0: number; back0: number; x0: number; sheenAt: number };
type Press = { chip: number; t0: number; t1: number };

const REST: Change = loopChange(0);


/** Seconds per sheen frame; 0 while the sheen is switched off (SHEEN.from = to), so it never plays. */
const SHEEN_FRAME_S = SHEEN.to > SHEEN.from ? (SHEEN.to - SHEEN.from) / SHEEN.frames : 0;
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

/** A new city change starting at `start` from what the screen shows in `c` (strip at offset x0). */
function changeFrom(c: Change, to: number, start: number, now: number, x0: number): Rec {
  'worklet';
  const [l0, r0] = pillAt(c);
  const coin = coinAt(c);
  return {
    from: c.tau >= 0 ? c.to : c.from,
    to,
    start,
    l0,
    r0,
    a0: coin.angle,
    back0: coin.back,
    x0,
    sheenAt: c.sheen > 0 && SHEEN_FRAME_S > 0 ? now - c.sheen * SHEEN_FRAME_S : -1,
  };
}

/** The autoPlay finger on the bar chart: once the bars are up it slides to September, then August (120 ms per bar, as under a finger). */
function scriptHl(since: number) {
  'worklet';
  return 5 - easeOut(seg(since, 0.85, 0.97)) - easeOut(seg(since, 1.1, 1.22));
}

/** The hold as the screen shows it at `now`. */
function holdView(now: number, h: Hold, hl: number, reduced: boolean): HoldView {
  'worklet';
  const v = holdAt(now, h);
  let e = v.e;
  if (reduced && h.t1 >= 0 && now >= h.t1) {
    // Reduce Motion: the chart fades in and out (200 ms), nothing grows.
    e = now < h.t2 ? clamp01((now - h.t1) / 0.2) : 1 - clamp01((now - h.t2) / 0.2);
    return { tile: e > 0 ? h.tile : -1, press: 0, e, since: 10, hl };
  }
  return { tile: v.tile, press: reduced ? 0 : v.press, e, since: v.since, hl };
}

function sameHold(a: HoldView, b: HoldView) {
  'worklet';
  return a.tile === b.tile && a.press === b.press && a.e === b.e && a.since === b.since && a.hl === b.hl;
}

const RANK_GROUP: RollGroup = {
  layerOf: RANK.map((r) => r.word),
  vals: RANK_WORDS.map((_, i) => i),
  start: LABEL.delay,
  dur: LABEL.dur,
  stagger: 0,
  travel: LABEL.travel,
};

const FONT_FILES = {
  [FONT.body]: require('./assets/Anybody-Body.ttf'),
  [FONT.semi]: require('./assets/Anybody-SemiBold.ttf'),
  [FONT.extra]: require('./assets/Anybody-ExtraBold.ttf'),
};

const IMG = {
  profile: require('./assets/profile.jpg'),
  mutual1: require('./assets/mutual-1.jpg'),
  mutual2: require('./assets/mutual-2.jpg'),
  /** The strip's photos: the pin's four, then six per city (STRIP in motion.ts indexes this list). */
  photos: [
    require('./assets/grid-1.jpg'),
    require('./assets/grid-2.jpg'),
    require('./assets/grid-3.jpg'),
    require('./assets/grid-4.jpg'),
    require('./assets/ny-1.webp'),
    require('./assets/ny-2.webp'),
    require('./assets/ny-3.webp'),
    require('./assets/ny-4.webp'),
    require('./assets/ny-5.webp'),
    require('./assets/ny-6.webp'),
    require('./assets/ld-1.webp'),
    require('./assets/ld-2.webp'),
    require('./assets/ld-3.webp'),
    require('./assets/ld-4.webp'),
    require('./assets/ld-5.webp'),
    require('./assets/ld-6.webp'),
    require('./assets/pa-1.webp'),
    require('./assets/pa-2.webp'),
    require('./assets/pa-3.webp'),
    require('./assets/pa-4.webp'),
    require('./assets/pa-5.webp'),
    require('./assets/pa-6.webp'),
  ] as number[],
  big1: require('./assets/grid-5.jpg'),
  big2: require('./assets/grid-6.jpg'),
};

/** The pin's name and handle are swapped for invented ones of the same length and weight. */
const NAME = { ...text.name, text: 'Theo Hogan', x: 427.7, ls: 0.32 };
const HANDLE = { ...text.handle, text: '@theohogan', ls: 0.62 };

export function TasteProfile({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const [fontsReady] = useFonts(FONT_FILES);
  const initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced);
  const [screenReader, setScreenReader] = useState(false);
  const [city, setCity] = useState(0);
  const [following, setFollowing] = useState(false);
  const [boardReady, setBoardReady] = useState(false);
  const [holdTile, setHoldTile] = useState(-1);
  const onBoardReady = useCallback(() => setBoardReady(true), []);
  const U = width / REF_W;
  const FS = width / REF_PT;
  const Y0 = insets.top - REF_INSET * U;
  const y = (v: number) => v * U + Y0;
  const geo = { U, FS, y };
  const boardGeo: BoardGeo = { U, Y0, width, height, pr: PixelRatio.get() };
  /** One photo of the strip, in pt. */
  const cellPt = (box.strip.w / 4) * U;
  const looping = autoPlay && !reduced && !screenReader;

  const change = useSharedValue<Change>(REST);
  const mode = useSharedValue(looping ? AUTO : MANUAL);
  const loopT = useSharedValue(0);
  const rec = useSharedValue<Rec>({ from: 0, to: 0, start: -1e3, l0: REST.l0, r0: REST.r0, a0: 0, back0: 0, x0: 0, sheenAt: -1 });
  /** Photo strip: offset in photos and separation (0 flat … 1 cover-flow); the finger drives both. */
  const sx = useSharedValue(0);
  const sk = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  /** When the strip goes flat again (s, -1 none): set on release, run by the frame callback. */
  const flatAt = useSharedValue(-1);
  /** Holding a stat tile: the record (MANUAL), what the screen shows, the highlighted bar. */
  const holdRec = useSharedValue<Hold>(NO_HOLD);
  const hold = useSharedValue<HoldView>(HOLD_REST);
  const hl = useSharedValue(5);
  const press = useSharedValue<Press>({ chip: -1, t0: 0, t1: 0 });
  const nowS = useSharedValue(0);
  const last = useSharedValue(-1);
  const touchedAt = useSharedValue(-1e3);
  const holding = useSharedValue(false);
  const foreground = useSharedValue(AppState.currentState === 'active');
  const followP = useSharedValue(0);
  const followPress = useSharedValue(0);

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((on) => {
      if (mounted) setReduced(on);
    });
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => {
      mounted = false;
      sub.remove();
    };
  }, []);
  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isScreenReaderEnabled().then((on) => {
      if (mounted) setScreenReader(on);
    });
    const sub = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => {
      mounted = false;
      sub.remove();
    };
  }, []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      foreground.set(state === 'active');
      last.set(-1);
    });
    return () => sub.remove();
  }, [foreground, last]);
  // Leaving the loop (Reduce Motion, VoiceOver, autoPlay off): hold the screen as it is now.
  useEffect(() => {
    if (!looping && mode.get() === AUTO) {
      const c = change.get();
      rec.set(changeFrom(c, c.tau >= 0 ? c.to : c.from, nowS.get() - SETTLE, nowS.get(), Math.round(sx.get())));
      sx.set(Math.round(sx.get()));
      sk.set(0);
      mode.set(MANUAL);
    }
  }, [looping, mode, change, rec, nowS, sx, sk]);

  // React hears only which city is chosen (for accessibility), never per-frame values.
  useAnimatedReaction(
    () => change.get().to,
    (next, prev) => {
      if (next !== prev) scheduleOnRN(setCity, next);
    },
  );
  useAnimatedReaction(
    () => hold.get().tile,
    (next, prev) => {
      if (next !== prev) scheduleOnRN(setHoldTile, next);
    },
  );

  /** autoPlay takes back a followed profile: "following" returns to the pin's "follow". */
  const unfollow = () => {
    setFollowing(false);
    followP.set(withSpring(0, FOLLOW.spring));
  };

  const frame = (info: { timestamp: number }) => {
    'worklet';
    if (!foreground.get()) return;
    const now = info.timestamp / 1000;
    const before = last.get();
    last.set(now);
    nowS.set(now);
    const dt = before < 0 ? 0 : Math.min(now - before, 0.1);
    let c: Change;
    let hv: HoldView;
    if (mode.get() === AUTO) {
      const t = (loopT.get() + dt) % LOOP;
      loopT.set(t);
      c = loopChange(t);
      const st = stripAt(t);
      if (st.x !== sx.get()) sx.set(st.x);
      if (st.k !== sk.get()) sk.set(st.k);
      const hs = holdAt(t, HOLD_SCRIPT);
      hv = holdView(t, HOLD_SCRIPT, scriptHl(hs.since), false);
    } else {
      const r = rec.get();
      const p = press.get();
      let sheen = 0;
      if (r.sheenAt >= 0 && SHEEN_FRAME_S > 0) {
        sheen = (now - r.sheenAt) / SHEEN_FRAME_S;
        if (sheen >= SHEEN.frames) sheen = 0;
      }
      c = {
        from: r.from,
        to: r.to,
        tau: now - r.start,
        l0: r.l0,
        r0: r.r0,
        a0: r.a0,
        back0: r.back0,
        pressChip: p.chip,
        press: p.chip >= 0 ? pressAt(now, p.t0, p.t1) : 0,
        x0: r.x0,
        sheen,
        reduced,
      };
      hv = holdView(now, holdRec.get(), hl.get(), reduced);
      // The strip goes flat once its snap has (nearly) settled.
      const fa = flatAt.get();
      if (fa >= 0 && now >= fa && !holding.get()) {
        flatAt.set(-1);
        sk.set(withTiming(0, { duration: COVER.out * 1000, easing: EASE_IN_OUT }));
      }
      // Hand back to autoPlay: 2 s after the last touch, once the screen has settled.
      const x = sx.get();
      const still = sk.get() === 0 && Math.abs(x - Math.round(x)) < 1e-3 && hv.tile < 0;
      if (looping && still && !holding.get() && now - touchedAt.get() >= RESUME_AFTER && c.tau >= SETTLE && sheen === 0 && c.press === 0) {
        if (followP.get() > 0.5) scheduleOnRN(unfollow);
        holdRec.set(NO_HOLD);
        hl.set(5);
        if (r.to === 0 && Math.round(x) === 0) {
          loopT.set(0);
          mode.set(AUTO);
        } else if (r.to === SCRIPT[0].city && Math.round(x) === 0) {
          // The loop's own "london" frame, before the swipe.
          loopT.set(STRIP_SCRIPT.down - 0.1);
          mode.set(AUTO);
        } else if (r.to === SCRIPT[0].city && Math.round(x) === STRIP_SCRIPT.to) {
          // After the loop's swipe, before its hold.
          loopT.set(HOLD_SCRIPT.t0 - 0.1);
          mode.set(AUTO);
        } else {
          // Anything else: the autoPlay finger taps "all" and the board flaps back to the pin.
          press.set({ chip: 0, t0: now, t1: now + PRESS.in });
          rec.set(changeFrom(c, 0, now + PRESS.in, now, Math.round(x)));
          sx.set(0);
        }
      }
    }
    if (!sameHold(hv, hold.get())) hold.set(hv);
    // Publish only when something on screen can differ.
    const tauN = c.tau < 0 ? -1 : Math.min(c.tau, SETTLE);
    const cur = change.get();
    const curN = cur.tau < 0 ? -1 : Math.min(cur.tau, SETTLE);
    if (
      tauN !== curN ||
      c.from !== cur.from ||
      c.to !== cur.to ||
      c.press !== cur.press ||
      c.pressChip !== cur.pressChip ||
      c.sheen !== cur.sheen ||
      c.l0 !== cur.l0 ||
      c.r0 !== cur.r0 ||
      c.a0 !== cur.a0 ||
      c.back0 !== cur.back0 ||
      c.x0 !== cur.x0 ||
      c.reduced !== cur.reduced
    ) {
      change.set({ ...c, tau: tauN === SETTLE ? SETTLE : c.tau });
    }
  };
  useFrameCallback((info) => frame(info));

  /** Taking over from the loop: keep everything where it is and follow the finger from here. */
  const takeOver = () => {
    'worklet';
    if (mode.get() !== AUTO) return;
    const c = change.get();
    const now = nowS.get();
    rec.set({
      from: c.from,
      to: c.to,
      start: now - c.tau,
      l0: c.l0,
      r0: c.r0,
      a0: c.a0,
      back0: c.back0,
      x0: c.x0,
      sheenAt: c.sheen > 0 && SHEEN_FRAME_S > 0 ? now - c.sheen * SHEEN_FRAME_S : -1,
    });
    press.set({ chip: -1, t0: 0, t1: 0 });
    // The loop's swipe and hold continue from where they are: the strip snaps flat, the chart closes.
    const t = loopT.get();
    const h = HOLD_SCRIPT;
    if (t >= h.t0 && t < h.t2 + EXPAND.out) {
      holdRec.set({ tile: h.tile, t0: now - (t - h.t0), t1: t >= h.t1 ? now - (t - h.t1) : -1, t2: t < h.t2 ? now : now - (t - h.t2) });
      hl.set(hold.get().hl);
    } else holdRec.set(NO_HOLD);
    if (sk.get() > 0) {
      sx.set(withSpring(Math.round(sx.get()), { duration: 400, dampingRatio: 0.82 }));
      flatAt.set(now + 0.3);
    }
    mode.set(MANUAL);
  };

  const chipIn = (i: number) => {
    takeOver();
    holding.set(true);
    touchedAt.set(nowS.get());
    if (!reduced) press.set({ chip: i, t0: nowS.get(), t1: 1e9 });
  };
  const chipOut = () => {
    holding.set(false);
    touchedAt.set(nowS.get());
    const p = press.get();
    if (p.chip >= 0 && p.t1 > nowS.get()) press.set({ ...p, t1: nowS.get() });
  };
  const chipTap = (i: number) => {
    takeOver();
    const c = change.get();
    const now = nowS.get();
    touchedAt.set(now);
    const shown = c.tau >= 0 ? c.to : c.from;
    if (i === shown && i === c.to) return;
    // The strip flattens and the board flaps from what it shows.
    cancelAnimation(sx);
    cancelAnimation(sk);
    const x0 = Math.round(sx.get());
    sx.set(0);
    sk.set(0);
    rec.set(changeFrom(c, i, now, now, x0));
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  const followTap = () => {
    const next = !following;
    setFollowing(next);
    const target = next ? 1 : 0;
    followP.set(reduced ? target : withSpring(target, FOLLOW.spring));
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const haptic = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Swipe the strip: it separates into a 3D cover-flow, carries momentum, snaps to a photo, goes flat. */
  const stripPan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-12, 12])
    .onBegin(() => {
      takeOver();
      holding.set(true);
      touchedAt.set(nowS.get());
    })
    .onStart(() => {
      cancelAnimation(sx);
      dragFrom.set(sx.get());
      if (!reduced) sk.set(withTiming(1, { duration: COVER.in * 1000, easing: EASE_OUT }));
    })
    .onUpdate((e) => {
      const max = STRIP[change.get().to].length - 4;
      const raw = dragFrom.get() - e.translationX / cellPt;
      // Rubber-band past either end.
      const x = raw < 0 ? raw * COVER.rubber : raw > max ? max + (raw - max) * COVER.rubber : raw;
      sx.set(x);
    })
    .onEnd((e) => {
      const max = STRIP[change.get().to].length - 4;
      const v = -e.velocityX / cellPt;
      const from = Math.round(dragFrom.get());
      const target = Math.max(0, Math.min(max, Math.round(sx.get() + v * COVER.project)));
      sx.set(withSpring(target, { duration: 520, dampingRatio: COVER.snap.zeta, velocity: v }));
      flatAt.set(nowS.get() + 0.42);
      if (target !== from) scheduleOnRN(haptic, 0);
    })
    .onFinalize(() => {
      holding.set(false);
      touchedAt.set(nowS.get());
      if (sk.get() > 0 && flatAt.get() < 0) flatAt.set(nowS.get());
    });

  /** Hold a stat tile: it grows into the city's bar chart; slide along the bars to read them. */
  const tileHold = (k: number) => {
    const cardX = chartRect(k).x;
    return Gesture.Pan()
      .activateAfterLongPress(EXPAND.hold * 1000)
      .onBegin(() => {
        takeOver();
        holding.set(true);
        touchedAt.set(nowS.get());
        hl.set(5);
        holdRec.set({ tile: k, t0: nowS.get(), t1: -1, t2: 1e9 });
      })
      .onStart(() => {
        const h = holdRec.get();
        holdRec.set({ ...h, t1: nowS.get() });
        scheduleOnRN(haptic, 1);
      })
      .onUpdate((e) => {
        const i = barUnder(e.absoluteX / U - cardX);
        if (Math.round(hl.get()) !== i || hl.get() !== i) {
          if (Math.round(hl.get()) !== i) scheduleOnRN(haptic, 0);
          hl.set(withTiming(i, { duration: 120, easing: EASE_OUT }));
        }
      })
      .onFinalize(() => {
        const h = holdRec.get();
        holdRec.set({ ...h, t2: nowS.get() });
        holding.set(false);
        touchedAt.set(nowS.get());
      });
  };

  /** A one-line Text whose baseline sits at the spec's baseline. */
  const lineStyle = (s: TextSpec, color = s.color) => ({
    position: 'absolute' as const,
    left: s.x * U,
    top: y(s.baseline) - ASCENT * s.size * FS,
    fontFamily: s.font,
    fontSize: s.size * FS,
    letterSpacing: s.ls * FS,
    color,
    includeFontPadding: false,
  });
  const line = (s: TextSpec, color?: string) => (
    <Text allowFontScaling={false} numberOfLines={1} style={lineStyle(s, color)}>
      {s.text}
    </Text>
  );

  /** A run of glyphs placed one by one at their measured ink positions (window = the screen). */
  const run = (r: GlyphRun, color = r.color) =>
    r.glyphs.map(([ch, inkL, inkW], i) => (
      <GlyphView key={i} ch={ch} inkL={inkL} inkW={inkW} run={{ ...r, color }} geo={geo} wx={0} wy={-Y0 / U} />
    ));

  /** Absolutely placed box in ref px. */
  const at = (b: { x: number; y: number; w: number; h: number }) => ({
    position: 'absolute' as const,
    left: b.x * U,
    top: y(b.y),
    width: b.w * U,
    height: b.h * U,
  });

  const stops = colors.pageStops;
  const pageColors = stops.map((s) => s[1]) as [string, string, ...string[]];
  const pageLocs: number[] = [];
  stops.forEach((s) => {
    const loc = Math.min(1, Math.max(0, y(s[0]) / height));
    pageLocs.push(Math.max(loc, pageLocs[pageLocs.length - 1] ?? 0));
  });

  const navPill = (b: { x: number; y: number; w: number; h: number }) => (
    <View
      style={[
        at(b),
        styles.navPill,
        { borderRadius: radius.nav * U, borderWidth: Math.max(1, 1.6 * U), shadowRadius: 5 * FS, shadowOffset: { width: 0, height: 3 * FS } },
      ]}
    >
      <LinearGradient
        colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.03)', 'rgba(0,0,0,0.06)']}
        locations={[0, 0.6, 1]}
        style={[StyleSheet.absoluteFill, { borderRadius: radius.nav * U }]}
      />
    </View>
  );

  const chipH = box.chipH;
  const chipTop = box.chipY;
  const chipRadius = (chipH / 2) * U;

  /** A chip's label (for "all": the globe and the glyph run) in one colour. */
  const chipLabel = (i: number, color: string) =>
    i === 0 ? (
      <>
        <View style={{ position: 'absolute', left: box.globe.x * U, top: y(box.globe.y) }}>
          <Globe w={box.globe.w} h={box.globe.h} u={U} color={color} />
        </View>
        {fontsReady ? run(glyphRuns.chipAll, color) : null}
      </>
    ) : fontsReady ? (
      line([text.chipA, text.chipB, text.chipC][i - 1], color)
    ) : null;

  return (
    <GestureHandlerRootView style={styles.root}>
      <LinearGradient colors={pageColors} locations={pageLocs as [number, number, ...number[]]} style={StyleSheet.absoluteFill} />

      {/* nav row */}
      <View style={{ position: 'absolute', left: box.chevron.x * U, top: y(box.chevron.y) }}>
        <ChevronLeft w={box.chevron.w} h={box.chevron.h} u={U} stroke={box.chevron.stroke} />
      </View>
      <FollowButton
        geo={geo}
        followP={followP}
        followPress={followPress}
        fontsReady={fontsReady}
        lineStyle={lineStyle}
        following={following}
        reduced={reduced}
        onPress={followTap}
      />
      {navPill(box.send)}
      <View style={{ position: 'absolute', left: box.sendIcon.x * U, top: y(box.sendIcon.y) }}>
        <Send w={box.sendIcon.w} h={box.sendIcon.h} u={U} />
      </View>
      {navPill(box.share)}
      <View style={{ position: 'absolute', left: box.shareIcon.x * U, top: y(box.shareIcon.y) }}>
        <Share w={box.shareIcon.w} h={box.shareIcon.h} u={U} />
      </View>

      {/* portrait capsule */}
      <View
        style={[
          at(box.capsule),
          styles.capsule,
          { borderRadius: (box.capsule.w / 2) * U, padding: box.capsule.border * U, shadowRadius: 6 * FS, shadowOffset: { width: 0, height: 3 * FS } },
        ]}
      >
        <Image source={IMG.profile} contentFit="cover" style={{ flex: 1, borderRadius: (box.capsule.w / 2 - box.capsule.border) * U }} />
      </View>

      {/* name, handle, counts */}
      {fontsReady ? (
        <>
          {line(NAME)}
          {line(HANDLE)}
          {run(glyphRuns.n39)}
          {line(text.following)}
          <Followers geo={geo} followP={followP} lineStyle={lineStyle} />
        </>
      ) : null}

      {/* mutual friends */}
      {box.avatars.map((a, i) => (
        <View
          key={i}
          style={{
            position: 'absolute',
            left: (a.cx - a.d / 2) * U,
            top: y(a.cy - a.d / 2),
            width: a.d * U,
            height: a.d * U,
            borderRadius: (a.d / 2) * U,
            backgroundColor: colors.white,
            padding: 3 * U,
          }}
        >
          <Image source={i === 0 ? IMG.mutual1 : IMG.mutual2} contentFit="cover" style={{ flex: 1, borderRadius: (a.d / 2) * U }} />
        </View>
      ))}
      {fontsReady ? (
        <>
          {line(text.mutual2)}
          {line(text.mutual)}
          {line(text.friends)}
        </>
      ) : null}

      {/* rank coin and streak gem (Blender renders) */}
      <Coin change={change} geo={geo} />
      <Gem change={change} geo={geo} />
      {fontsReady ? (
        <>
          <RollWords change={change} group={RANK_GROUP} words={RANK_WORDS} style={lineStyle(text.rankA)} U={U} />
          {line(text.rankB)}
          {line(text.weekA)}
          {line(text.weekB)}
        </>
      ) : null}

      {/* location and bio */}
      <View style={{ position: 'absolute', left: box.pin.x * U, top: y(box.pin.y) }}>
        <Pin w={box.pin.w} h={box.pin.h} u={U} color={colors.pinIcon} />
      </View>
      {fontsReady ? (
        <>
          {line(text.loc)}
          {line(text.bio)}
        </>
      ) : null}

      {/* city chips: white chips, grey labels, then the black pill with white labels inside it */}
      {box.chips.map((c, i) => (
        <WhiteChip key={i} i={i} change={change} geo={geo} top={chipTop} h={chipH} radius={chipRadius} />
      ))}
      {box.chips.map((_, i) => (
        <Pressed key={i} i={i} change={change} geo={geo} width={width} height={height}>
          {chipLabel(i, colors.chipText)}
        </Pressed>
      ))}
      <Pill change={change} geo={geo} width={width} top={chipTop} h={chipH} radius={chipRadius}>
        {box.chips.map((_, i) => (
          <Pressed key={i} i={i} change={change} geo={geo} width={width} height={height}>
            {chipLabel(i, colors.white)}
          </Pressed>
        ))}
      </Pill>
      {box.chips.map((c, i) => (
        <Pressable
          key={i}
          accessibilityRole="tab"
          accessibilityLabel={CITIES[i]}
          accessibilityState={{ selected: city === i }}
          hitSlop={{ top: 6, bottom: 6 }}
          onPressIn={() => chipIn(i)}
          onPressOut={chipOut}
          onPress={() => chipTap(i)}
          style={{ position: 'absolute', left: c.x * U, top: y(chipTop), width: c.w * U, height: chipH * U }}
        />
      ))}

      {/* stat tiles: the first is a partial one scrolled off the left edge */}
      {[box.tiles[0] - (box.tileW + 19), ...box.tiles].map((x, i) => (
        <HideWhenHeld key={i} k={i - 1} hold={hold}>
        <View
          style={[
            styles.tile,
            {
              left: x * U,
              top: y(box.tileY),
              width: box.tileW * U,
              height: box.tileH * U,
              borderRadius: radius.tile * U,
              borderWidth: Math.max(1, 2.4 * U),
              shadowRadius: 4 * FS,
              shadowOffset: { width: 0, height: 2 * FS },
            },
          ]}
        />
        </HideWhenHeld>
      ))}
      <HideWhenHeld k={0} hold={hold}>
        <View style={{ position: 'absolute', left: box.tileIcons[0].x * U, top: y(box.tileIcons[0].y) }}>
          <Star w={box.tileIcons[0].w} h={box.tileIcons[0].h} u={U} />
        </View>
      </HideWhenHeld>
      <HideWhenHeld k={1} hold={hold}>
        <View style={{ position: 'absolute', left: box.tileIcons[1].x * U, top: y(box.tileIcons[1].y) }}>
          <ThumbUp w={box.tileIcons[1].w} h={box.tileIcons[1].h} u={U} />
        </View>
      </HideWhenHeld>
      <HideWhenHeld k={2} hold={hold}>
        <View style={{ position: 'absolute', left: box.tileIcons[2].x * U, top: y(box.tileIcons[2].y) }}>
          <Meh w={box.tileIcons[2].w} h={box.tileIcons[2].h} u={U} />
        </View>
      </HideWhenHeld>
      <HideWhenHeld k={3} hold={hold}>
        <View style={{ position: 'absolute', left: box.tileIcons[3].x * U, top: y(box.tileIcons[3].y) }}>
          <ThumbUp w={box.tileIcons[3].w} h={box.tileIcons[3].h} u={U} down />
        </View>
      </HideWhenHeld>
      {fontsReady ? (
        <>
          {[text.lfav, text.lrec, text.lok, text.ldis].map((s2, k) => (
            <HideWhenHeld key={k} k={k} hold={hold}>
              {line(s2)}
            </HideWhenHeld>
          ))}
          {/* Until the board's canvas is ready: the pin's numbers. */}
          {!boardReady ? [glyphRuns.t125, glyphRuns.t764, glyphRuns.t183, glyphRuns.t17].map((r, k) => <View key={k}>{run(r)}</View>) : null}
        </>
      ) : null}

      {/* photo strip */}
      {!boardReady ? (
        <View style={[at(box.strip), styles.strip, { borderRadius: radius.photo * U }]}>
          {IMG.photos.slice(0, 4).map((src, i) => (
            <Image key={i} source={src} contentFit="cover" style={{ flex: 1, height: '100%' }} />
          ))}
          <View
            pointerEvents="none"
            style={[StyleSheet.absoluteFill, { borderRadius: radius.photo * U, borderWidth: Math.max(1, 2 * U), borderColor: 'rgba(255,255,255,0.55)' }]}
          />
        </View>
      ) : null}
      {(boardReady ? [] : box.dots).map((d, i) => (
        <View
          key={i}
          style={{
            position: 'absolute',
            left: (d.cx - d.d / 2) * U,
            top: y(box.dotsY - d.d / 2),
            width: d.d * U,
            height: d.d * U,
            borderRadius: (d.d / 2) * U,
            backgroundColor: d.color,
          }}
        />
      ))}
      {fontsReady ? (
        <>
          {line(text.viewAll)}
          {!boardReady ? run(glyphRuns.c1470) : null}
          {line(text.photos)}
        </>
      ) : null}

      {/* two large photos and the next one peeking in */}
      <Image source={IMG.big1} contentFit="cover" style={[at(box.photoL), { borderRadius: radius.photo * U }]} />
      <Image source={IMG.big2} contentFit="cover" style={[at(box.photoR), { borderRadius: radius.photo * U }]} />
      <LinearGradient
        colors={['#1A1E18', '#10110C', '#090B09', '#14130F', '#4B4A46', '#1A1C1C']}
        style={[at(box.peek), { borderRadius: radius.photo * U }]}
      />

      {/* The departure board (stat numbers, photo strip, dots, photo count) in one Skia picture. */}
      <Board change={change} sx={sx} sk={sk} hold={hold} geo={boardGeo} photos={IMG.photos} onReady={onBoardReady} />

      {/* Touch: swipe the strip, hold a tile. */}
      <GestureDetector gesture={stripPan}>
        <View accessibilityLabel="Photos" style={at(box.strip)} />
      </GestureDetector>
      {box.tiles.map((x, k) => (
        <GestureDetector key={k} gesture={tileHold(k)}>
          <View
            accessible
            accessibilityLabel={`${STAT_GLYPHS[k][city].map((g) => g[0]).join('')} ${[text.lfav, text.lrec, text.lok, text.ldis][k].text}`}
            accessibilityHint="Hold to see the last six months"
            style={{ position: 'absolute', left: x * U, top: y(box.tileY), width: box.tileW * U, height: box.tileH * U }}
          />
        </GestureDetector>
      ))}

      {holdTile >= 0 ? (
        <TileChart
          hold={hold}
          tile={holdTile}
          city={city}
          cityName={CITIES[city] === 'all' ? 'all cities' : CITIES[city]}
          geo={geo}
          fontsReady={fontsReady}
          lineStyle={lineStyle}
          reduced={reduced}
          width={width}
          height={height}
        />
      ) : null}
    </GestureHandlerRootView>
  );
}

type Geo = { U: number; FS: number; y: (v: number) => number };

/** A layer over the whole screen that hides tile k's own views while the chart layer stands in for it. */
function HideWhenHeld({ k, hold, children }: { k: number; hold: SharedValue<HoldView>; children: React.ReactNode }) {
  const style = useAnimatedStyle(() => ({ opacity: k >= 0 && hold.get().tile === k ? 0 : 1 }));
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, style]}>
      {children}
    </Animated.View>
  );
}

/** How much of chip i the pill covers (0…1). */
function coverage(c: Change, i: number) {
  'worklet';
  const [l, r] = pillAt(c);
  const [a, b] = chipEdges(i);
  return clamp01((Math.min(r, b) - Math.max(l, a)) / (b - a));
}

/** Press scale of chip i. */
function chipScale(c: Change, i: number) {
  'worklet';
  return c.pressChip === i ? 1 - (1 - PRESS.scale) * c.press : 1;
}

/** Pill edges with press feedback: pressing the selected chip pulls both edges in by the press scale. */
function pillEdges(c: Change): [number, number] {
  'worklet';
  const [l, r] = pillAt(c);
  if (c.pressChip !== c.to || c.press === 0) return [l, r];
  const inset = ((r - l) * (1 - PRESS.scale) * c.press) / 2;
  return [l + inset, r - inset];
}

/** A white chip: fades out under the pill (so its shadow never shows through), scales when pressed. */
function WhiteChip({ i, change, geo, top, h, radius: r }: { i: number; change: SharedValue<Change>; geo: Geo; top: number; h: number; radius: number }) {
  const { U, FS, y } = geo;
  const c = box.chips[i];
  const style = useAnimatedStyle(() => {
    const ch = change.get();
    return { opacity: 1 - coverage(ch, i), transform: [{ scale: chipScale(ch, i) }] };
  });
  return (
    <Animated.View
      pointerEvents="none"
      style={[
        { position: 'absolute', left: c.x * U, top: y(top), width: c.w * U, height: h * U, borderRadius: r },
        styles.chipOff,
        { shadowRadius: 5 * FS, shadowOffset: { width: 0, height: 2.5 * FS } },
        style,
      ]}
    >
      <LinearGradient
        colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.025)', 'rgba(0,0,0,0.06)']}
        locations={[0, 0.65, 1]}
        style={[StyleSheet.absoluteFill, { borderRadius: r }]}
      />
    </Animated.View>
  );
}

/** A screen-sized layer that scales chip i's label about the chip's centre while it is pressed. */
function Pressed({ i, change, geo, width, height, children }: { i: number; change: SharedValue<Change>; geo: Geo; width: number; height: number; children: React.ReactNode }) {
  const { U, y } = geo;
  const c = box.chips[i];
  const style = useAnimatedStyle(() => ({ transform: [{ scale: chipScale(change.get(), i) }] }));
  return (
    <Animated.View
      pointerEvents="none"
      style={[
        { position: 'absolute', left: 0, top: 0, width, height, transformOrigin: [(c.x + c.w / 2) * U, y(box.chipY + box.chipH / 2), 0] },
        style,
      ]}
    >
      {children}
    </Animated.View>
  );
}

/**
 * The black selection pill, cut out of a screen-wide black band by two nested rounded clips (one
 * per edge), so its edges move independently with transforms only and its ends stay round.
 */
function Pill(props: { change: SharedValue<Change>; geo: Geo; width: number; top: number; h: number; radius: number; children: React.ReactNode }) {
  const { change, geo, width, top, h, radius: r, children } = props;
  const { U, y } = geo;
  // Edges snapped to device pixels, so the nested clips meet without a seam.
  const PR = PixelRatio.get();
  const left = useAnimatedStyle(() => {
    const [l] = pillEdges(change.get());
    return { transform: [{ translateX: Math.round(l * U * PR) / PR }] };
  });
  const right = useAnimatedStyle(() => {
    const [l, rr] = pillEdges(change.get());
    return { transform: [{ translateX: Math.round(rr * U * PR) / PR - width - Math.round(l * U * PR) / PR }] };
  });
  const content = useAnimatedStyle(() => {
    const [, rr] = pillEdges(change.get());
    return { transform: [{ translateX: width - Math.round(rr * U * PR) / PR }] };
  });
  const band = { position: 'absolute' as const, left: 0, top: 0, width, height: h * U, borderRadius: r, overflow: 'hidden' as const };
  return (
    <Animated.View pointerEvents="none" style={[band, { top: y(top) }, left]}>
      <Animated.View style={[band, right]}>
        <Animated.View style={[{ position: 'absolute', left: 0, top: 0, width, height: h * U }, content]}>
          <View style={[StyleSheet.absoluteFill, { backgroundColor: '#000000' }]} />
          <LinearGradient colors={['rgba(255,255,255,0.30)', 'rgba(255,255,255,0)']} style={{ position: 'absolute', left: 0, top: 0, width, height: 30 * U }} />
          <View style={{ position: 'absolute', left: 0, top: -y(top) }}>{children}</View>
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

const AnimatedPath = Animated.createAnimatedComponent(Path);

/** "follow" ⇄ "following": the pill grows to the left, + turns into ✓, "ing" slides in. */
function FollowButton(props: {
  geo: Geo;
  followP: SharedValue<number>;
  followPress: SharedValue<number>;
  fontsReady: boolean;
  lineStyle: (s: TextSpec) => object;
  following: boolean;
  reduced: boolean;
  onPress: () => void;
}) {
  const { geo, followP, followPress, fontsReady, lineStyle, following, reduced, onPress } = props;
  const { U, FS, y } = geo;
  const b = box.follow;
  const ic = box.followIcon;
  const wrap = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS.scale) * followPress.get() }] }));
  const bg = useAnimatedStyle(() => {
    const g = FOLLOW.grow * followP.get();
    return { left: -g * U, width: (b.w + g) * U };
  });
  const content = useAnimatedStyle(() => ({ transform: [{ translateX: -FOLLOW.grow * followP.get() * U }] }));
  const ing = useAnimatedStyle(() => {
    const p = clamp01(followP.get());
    return { opacity: clamp01(p * 1.6 - 0.4), transform: [{ translateX: (1 - p) * -10 * U }] };
  });
  // Plus (two strokes) → check (short leg, long leg), endpoints interpolated.
  const mark = useAnimatedProps(() => {
    const p = clamp01(followP.get());
    const L = (a: number, z: number) => a + (z - a) * p;
    return {
      d: `M${L(34.7, 33.6)} ${L(19, 19.6)} L${L(47, 38.8)} ${L(19, 25.4)} M${L(40.9, 38.8)} ${L(12.8, 25.4)} L${L(40.9, 47.6)} ${L(25.2, 12.6)}`,
    };
  });
  const pill = { position: 'absolute' as const, left: 0, top: 0, width: b.w * U, height: b.h * U, borderRadius: radius.nav * U };
  return (
    <>
      <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: b.x * U, top: y(b.y), width: b.w * U, height: b.h * U }, wrap]}>
        <Animated.View
          style={[
            pill,
            styles.navPill,
            { borderWidth: Math.max(1, 1.6 * U), shadowRadius: 5 * FS, shadowOffset: { width: 0, height: 3 * FS } },
            bg,
          ]}
        />
        <Animated.View style={[pill, { overflow: 'hidden' }, bg]}>
          <LinearGradient colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.03)', 'rgba(0,0,0,0.06)']} locations={[0, 0.6, 1]} style={StyleSheet.absoluteFill} />
        </Animated.View>
        <Animated.View style={[{ position: 'absolute', left: -b.x * U, top: -y(b.y) }, content]}>
          <View style={{ position: 'absolute', left: ic.x * U, top: y(ic.y) }}>
            <Svg width={ic.w * U} height={ic.h * U} viewBox={`0 0 ${ic.w} ${ic.h}`} fill="none">
              <G stroke="#000" strokeWidth={5} strokeLinecap="round" strokeLinejoin="round">
                <Circle cx="17" cy="8.8" r="6.2" />
                <Path d="M4.5 37.5 V34 C4.5 28.5 9 27.5 18.5 27.5 C28 27.5 32.5 28.5 32.5 34 V37.5" />
                <AnimatedPath animatedProps={mark} />
              </G>
            </Svg>
          </View>
          {fontsReady ? (
            <>
              <Text allowFontScaling={false} numberOfLines={1} style={lineStyle(text.follow)}>
                {text.follow.text}
              </Text>
              <Animated.Text allowFontScaling={false} numberOfLines={1} style={[lineStyle({ ...text.follow, text: 'ing', x: followIng.x }), ing]}>
                ing
              </Animated.Text>
            </>
          ) : null}
        </Animated.View>
      </Animated.View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={following ? 'Following' : 'Follow'}
        accessibilityState={{ selected: following }}
        hitSlop={{ top: 6, bottom: 6, left: 4 }}
        onPressIn={() => {
          if (!reduced) followPress.set(withTiming(1, { duration: PRESS.in * 1000, easing: EASE_OUT }));
        }}
        onPressOut={() => followPress.set(withTiming(0, { duration: PRESS.out * 1000, easing: EASE_OUT }))}
        onPress={onPress}
        style={{ position: 'absolute', left: b.x * U, top: y(b.y), width: b.w * U, height: b.h * U }}
      />
    </>
  );
}

/** "60 followers" → "61 followers" when following: the last digit rolls up, the word closes the gap. */
function Followers(props: { geo: Geo; followP: SharedValue<number>; lineStyle: (s: TextSpec) => object }) {
  const { geo, followP, lineStyle } = props;
  const { U, y } = geo;
  const r = glyphRuns.n60;
  const [six, zero] = r.glyphs;
  const one: [string, number, number] = ['1', zero[1], Math.round((17 * r.digitH) / 36)];
  const pad = 8;
  const travel = r.digitH + pad;
  const wx = zero[1] - 4;
  const wy = r.baseline - r.digitH - pad;
  const shift = zero[2] - one[2];
  const out = useAnimatedStyle(() => {
    const q = clamp01(followP.get());
    return { opacity: 1 - q, transform: [{ translateY: -q * travel * U }] };
  });
  const inn = useAnimatedStyle(() => {
    const q = clamp01(followP.get());
    return { opacity: q, transform: [{ translateY: (1 - q) * travel * U }] };
  });
  const word = useAnimatedStyle(() => ({ transform: [{ translateX: -shift * clamp01(followP.get()) * U }] }));
  return (
    <>
      <GlyphView ch={six[0]} inkL={six[1]} inkW={six[2]} run={r} geo={geo} wx={0} wy={-geo.y(0) / U} />
      <View style={{ position: 'absolute', left: wx * U, top: y(wy), width: (zero[2] + 8) * U, height: (r.digitH + 2 * pad) * U, overflow: 'hidden' }}>
        <Animated.View style={[{ position: 'absolute', left: 0, top: 0 }, out]}>
          <GlyphView ch={zero[0]} inkL={zero[1]} inkW={zero[2]} run={r} geo={geo} wx={wx} wy={wy} />
        </Animated.View>
        <Animated.View style={[{ position: 'absolute', left: 0, top: 0 }, inn]}>
          <GlyphView ch={one[0]} inkL={one[1]} inkW={one[2]} run={r} geo={geo} wx={wx} wy={wy} />
        </Animated.View>
      </View>
      <Animated.Text allowFontScaling={false} numberOfLines={1} style={[lineStyle(text.followers), word]}>
        {text.followers.text}
      </Animated.Text>
    </>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  navPill: {
    backgroundColor: colors.chipFill,
    borderColor: colors.hairline,
    shadowColor: '#000',
    shadowOpacity: 0.1,
    overflow: 'visible',
  },
  capsule: {
    backgroundColor: colors.white,
    shadowColor: '#000',
    shadowOpacity: 0.12,
  },
  chipOff: { backgroundColor: colors.chipFill, shadowColor: '#000', shadowOpacity: 0.09, overflow: 'visible' },
  tile: {
    position: 'absolute',
    backgroundColor: colors.tileFill,
    borderColor: colors.tileHairline,
    shadowColor: '#000',
    shadowOpacity: 0.05,
  },
  strip: { flexDirection: 'row', overflow: 'hidden' },
});
