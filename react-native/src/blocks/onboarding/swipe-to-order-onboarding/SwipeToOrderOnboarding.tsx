import { Canvas, Path, Picture, Skia, type SkRSXform, useFont } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Cue, useStoAudio } from './use-sto-audio';

/*
 Swipe To Order Onboarding — the first screen of an invented planner app.

 Look (the pin, a still of three screens; this is the first): an electric purple page, three task
 cards thrown down at angles (a frosted glass one, a lime one with tags, a dark one), a round
 sticker with text running round it, "Take control of your schedule" with "control" in an outlined
 pill and a lime dot in the line, and a lime Get Started bar with a dark arrow knob.

 Motion (ours): order from chaos. The bar is a swipe-to-start slider; as the knob is dragged the
 cards straighten one after another into a tidy list, the sticker spins faster, and the word
 "control" fills with lime from the left. Each card follows its place on a spring, so the list
 lands with a little follow-through; let go early and the cards fly back to the pile. At the end
 the list clicks into place, the sticker turns into a tick and the bar reads "You're all set".

 autoPlay: LOOP = 8 s. 1.2-2.6 s the knob is dragged half way, eases back a little, 3.0-3.8 s goes
 the rest of the way; 3.8 s done; 5.9-6.5 s it is let go and the pile comes back. Frame 8 s = 0.
 Reduced motion: the cards change places on a short spring, the sticker does not spin.
*/

let debugState = '';
const LOOP = 8;
const N = 3;
const ROW = { w: 300, h: 86 } as const;

const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
/** The script's knob position 0..1 at loop time t. */
const scriptKnob = (t: number) => {
  'worklet';
  if (t < 1.2) return 0;
  if (t < 2.6) return 0.55 * easeInOut((t - 1.2) / 1.4);
  if (t < 3.0) return mix(0.55, 0.42, smooth((t - 2.6) / 0.4));
  if (t < 3.8) return mix(0.42, 1, easeInOut((t - 3.0) / 0.8));
  if (t < 5.9) return 1;
  if (t < 6.5) return 1 - easeInOut((t - 5.9) / 0.6);
  return 0;
};

// The pile (x, y offsets from the screen centre and the cards' top, rotation, scale) and the list.
const PILE = [
  { x: 52, y: 70, r: -9, s: 1.06 },
  { x: -36, y: 196, r: -13, s: 1 },
  { x: 18, y: 290, r: 8, s: 0.96 },
];
const LIST_Y = [60, 156, 252];

type Body = { x: number; y: number; r: number; s: number; vx: number; vy: number; vr: number; vs: number };

type Props = { autoPlay?: boolean };

export function SwipeToOrderOnboarding({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [fontsOk] = useFonts({
    'STO-Display': require('./assets/fonts/Epilogue-400.ttf'),
    'STO-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'STO-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const badgeFont = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 9.5);

  const w = size?.width ?? 402;
  const cx = w / 2;
  const TOP = insets.top + 56;
  const BAR = { x: 20, w: w - 40, h: 62 };
  const KNOB = 54;
  const confY = (size?.height ?? 874) - insets.bottom - 22 - BAR.h / 2;
  const RANGE = BAR.w - KNOB - 8;
  const headTopV = TOP + 368;

  const play = useStoAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  const knob = useSharedValue({ s: 0, v: 0, held: 0, start: 0, done: 0, placed: 0 });
  const doneAt = useSharedValue(-10); // clock when the list was completed by hand
  const cards = useSharedValue<Body[]>(
    PILE.map((p) => ({ x: cx + p.x, y: TOP + p.y, r: p.r, s: p.s, vx: 0, vy: 0, vr: 0, vs: 0 })),
  );
  const spin = useSharedValue(0);
  const pillW = useSharedValue(200); // the pill's inner width, measured
  const debugT = useSharedValue(-1);
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __swipeOrder?: unknown };
    g.__swipeOrder = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__swipeOrder;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** Where card i wants to be at knob position s: from the pile to its row, one after another. */
  const poseAt = (i: number, s: number) => {
    'worklet';
    const k = smooth((s - i * 0.12) / 0.76);
    const p = PILE[i];
    return {
      x: mix(cx + p.x, cx, k),
      y: mix(TOP + p.y, TOP + LIST_Y[i], k),
      r: mix(p.r, 0, k),
      s: mix(p.s, 1, k),
    };
  };

  const stepCards = (cs: Body[], s: number, dt: number) => {
    'worklet';
    const k = reduced ? 400 : 180;
    const z = reduced ? 1 : 0.62;
    const c = 2 * Math.sqrt(k) * z;
    const n = Math.max(1, Math.ceil(dt / (1 / 240)));
    const hh = dt / n;
    for (let i = 0; i < N; i++) {
      const b = cs[i];
      const t = poseAt(i, s);
      for (let j = 0; j < n; j++) {
        b.vx += (k * (t.x - b.x) - c * b.vx) * hh;
        b.x += b.vx * hh;
        b.vy += (k * (t.y - b.y) - c * b.vy) * hh;
        b.y += b.vy * hh;
        b.vr += (k * (t.r - b.r) - c * b.vr) * hh;
        b.r += b.vr * hh;
        b.vs += (k * (t.s - b.s) - c * b.vs) * hh;
        b.s += b.vs * hh;
      }
    }
  };

  /** The knob reached the end, or came back off it. */
  const settleDone = (kb: { s: number; done: number }, sound: boolean) => {
    'worklet';
    if (kb.done === 0 && kb.s >= 0.985) {
      kb.done = 1;
      if (sound) doneAt.set(clock.get());
      scheduleOnRN(setDone, true);
      if (sound) {
        scheduleOnRN(cue, 'done');
        scheduleOnRN(buzz, 2);
      }
    } else if (kb.done === 1 && kb.s < 0.6) {
      kb.done = 0;
      scheduleOnRN(setDone, false);
      if (sound) scheduleOnRN(cue, 'back');
    }
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    const kb = knob.get();
    const cs = cards.get();
    let t = time.get();
    if (dbg >= 0) {
      // replay from the start so the pinned frame holds the springs where the loop has them
      for (let i = 0; i < N; i++) {
        const p = PILE[i];
        cs[i] = { x: cx + p.x, y: TOP + p.y, r: p.r, s: p.s, vx: 0, vy: 0, vr: 0, vs: 0 };
      }
      let st = 0;
      while (st < dbg) {
        const d = Math.min(1 / 120, dbg - st);
        st += d;
        stepCards(cs, scriptKnob(st), d);
      }
      kb.s = scriptKnob(dbg);
      settleDone(kb, false);
      t = dbg;
      spin.set((4 * Math.PI * t) / LOOP);
      debugT.set(-2);
    } else if (dbg !== -2) {
      const touched = kb.held === 1 || now - last.get() < 2;
      if (auto && !touched) {
        t += dt;
        if (t >= LOOP) t -= LOOP;
        kb.s = scriptKnob(t);
        settleDone(kb, false);
      } else if (kb.held === 0) {
        // a released knob runs back to the start unless it reached the end
        const target = kb.done ? 1 : 0;
        kb.v += (260 * (target - kb.s) - 2 * Math.sqrt(260) * 0.95 * kb.v) * dt;
        kb.s = clamp01(kb.s + kb.v * dt);
        settleDone(kb, true);
      } else settleDone(kb, true);
      stepCards(cs, kb.s, dt);
      if (!reduced) spin.set(auto && !(kb.held === 1 || now - last.get() < 2) ? (4 * Math.PI * t) / LOOP : spin.get() + (dt * 4 * Math.PI) / LOOP);
    }
    // a click as each card lands in its row
    const placed = Math.floor(clamp01(kb.s) / 0.34);
    if (placed > kb.placed && kb.held === 1) {
      scheduleOnRN(cue, 'snap');
      scheduleOnRN(buzz, 0);
    }
    kb.placed = placed;
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} s=${kb.s.toFixed(3)} done=${kb.done}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // ---------------------------------------------------------------- the slider
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      const kb = knob.get();
      last.set(clock.get());
      kb.held = 1;
      kb.start = kb.s;
      kb.v = 0;
      scheduleOnRN(buzz, 1);
    })
    .onUpdate((e) => {
      const kb = knob.get();
      last.set(clock.get());
      kb.s = clamp01(kb.start + e.translationX / RANGE);
    })
    .onFinalize((e) => {
      const kb = knob.get();
      last.set(clock.get());
      kb.held = 0;
      kb.v = e.velocityX / RANGE;
      // a quick flick finishes it
      if (kb.s > 0.85 || (kb.s > 0.5 && e.velocityX > 900)) {
        kb.s = Math.max(kb.s, 0.986);
      }
      settleDone(kb, true);
      if (kb.done === 0 && kb.s > 0.05) scheduleOnRN(cue, 'back');
    });
  const a11yTap = () => {
    'worklet';
    const kb = knob.get();
    kb.s = kb.done ? 0 : 0.99;
    last.set(clock.get());
  };
  const knobStyle = useAnimatedStyle(() => {
    tick.get();
    return { transform: [{ translateX: knob.get().s * RANGE }] };
  });
  const trailStyle = useAnimatedStyle(() => {
    tick.get();
    return { width: KNOB + 8 + knob.get().s * RANGE };
  });
  const labelStyle = useAnimatedStyle(() => {
    tick.get();
    const s = knob.get().s;
    return { opacity: 1 - smooth(s / 0.4), transform: [{ translateX: s * 30 }] };
  });
  const doneLabel = useAnimatedStyle(() => {
    tick.get();
    return { opacity: smooth((knob.get().s - 0.8) / 0.2) };
  });
  // "control" fills with lime from the left
  const fillStyle = useAnimatedStyle(() => {
    tick.get();
    return { width: smooth(knob.get().s) * pillW.get() };
  });
  const badgeStyle = useAnimatedStyle(() => {
    tick.get();
    const s = smooth(knob.get().s);
    return {
      transform: [
        // the sticker lands at the end of "of ◐ your", and spins one extra turn on the way
        { translateX: mix(0, w - 24 - 40 - 64 - 24, s) },
        { translateY: mix(0, headTopV - TOP + 4 + 87 - 64, s) },
        { scale: mix(1, 0.62, s) },
        { rotate: `${spin.get() + (reduced ? 0 : 2 * Math.PI * knob.get().s)}rad` },
      ],
    };
  });

  const ring = useDerivedValue(() => {
    // the sticker's text running round its edge
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, 128, 128));
    if (!badgeFont) return rec.finishRecordingAsPicture();
    const txt = 'PLAN SMARTER • TAKE CONTROL • ';
    const ids = badgeFont.getGlyphIDs(txt);
    const ws = badgeFont.getGlyphWidths(ids);
    let total = 0;
    for (let i = 0; i < ws.length; i++) total += ws[i];
    const R = 47;
    const scale = (2 * Math.PI * R) / total;
    const xf: SkRSXform[] = [];
    let s = 0;
    for (let i = 0; i < ids.length; i++) {
      const a = ((s + ws[i] / 2) * scale) / R - Math.PI / 2;
      const cs = Math.cos(a + Math.PI / 2);
      const sn = Math.sin(a + Math.PI / 2);
      const px = 64 + Math.cos(a) * R;
      const py = 64 + Math.sin(a) * R;
      xf.push(Skia.RSXform(cs, sn, px - cs * (ws[i] / 2) - sn * 3.4, py - sn * (ws[i] / 2) + cs * 3.4));
      s += ws[i];
    }
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(Skia.Color('#1d1a2b'));
    c.drawTextBlob(Skia.TextBlob.MakeFromRSXformGlyphs(ids, xf, badgeFont), 0, 0, p);
    return rec.finishRecordingAsPicture();
  }, [badgeFont]);

  // a burst of confetti off the knob when the list completes (as a confirm button's payoff)
  const confetti = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, confY + 100));
    const t = time.get();
    const auto0 = auto && knob.get().held === 0 && clock.get() - last.get() >= 2;
    const since = auto0 || debugT.get() === -2 ? (t >= 3.8 && t < 5.9 ? t - 3.8 : -1) : clock.get() - doneAt.get();
    if (reduced || since < 0 || since > 1.6) return rec.finishRecordingAsPicture();
    const ox = BAR.x + BAR.w - KNOB / 2 - 4;
    const oy = confY;
    const cols = ['#c8f54a', '#ffffff', '#ff8ad8', '#ffd84a', '#8fd0ff'];
    const p = Skia.Paint();
    p.setAntiAlias(true);
    for (let i = 0; i < 46; i++) {
      const r1 = Math.sin((i + 1) * 12.9898 + 78.233) * 43758.5453;
      const r2 = Math.sin((i + 1) * 12.9898 + 156.466) * 43758.5453;
      const r3 = Math.sin((i + 1) * 12.9898 + 234.699) * 43758.5453;
      const h1 = r1 - Math.floor(r1);
      const h2 = r2 - Math.floor(r2);
      const h3 = r3 - Math.floor(r3);
      const a = -Math.PI / 2 + (h1 - 0.62) * 2.2;
      const v = 380 + h2 * 520;
      const x = ox + Math.cos(a) * v * since * (1 - since * 0.25);
      const y = oy + Math.sin(a) * v * since + 900 * since * since;
      const al = 1 - smooth((since - 1.0) / 0.6);
      p.setColor(Skia.Color(cols[i % cols.length]));
      p.setAlphaf(al);
      c.save();
      c.translate(x, y);
      c.rotate(since * 720 * (h3 - 0.5) + i * 40, 0, 0);
      c.scale(1, Math.abs(Math.cos(since * 9 + i)) + 0.15);
      c.drawRect(Skia.XYWHRect(-4, -2.5, 8, 5), p);
      c.restore();
    }
    return rec.finishRecordingAsPicture();
  });

  const bolt = useMemo(() => Skia.Path.MakeFromSVGString('M14 2 5 14h6l-1 8 9-12h-6z'), []);
  const tickPath = useMemo(() => Skia.Path.MakeFromSVGString('M5 12.5l4.5 4.5L19 7.5'), []);
  const arrow = useMemo(() => Skia.Path.MakeFromSVGString('M6 18 18 6 M9 6h9v9'), []);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;
  const headTop = headTopV;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <View style={[styles.loop, { top: TOP + 40, left: -150 }]} />
      <View style={[styles.bar, { top: insets.top + 8 }]}>
        <Text style={styles.brand} allowFontScaling={false}>
          Taskloop
        </Text>
        <Text style={styles.skip} allowFontScaling={false} accessibilityRole="button">
          Skip →
        </Text>
      </View>

      <Card i={2} cards={cards} tick={tick} tone="dark" title="Leisure Reading" sub="2 Hours Needed" pct={60} />
      <Card i={1} cards={cards} tick={tick} tone="lime" title="Focus Block · Code Review" sub="" pct={-1} />
      <Card i={0} cards={cards} tick={tick} tone="glass" title="Client Call ABC Corp." sub="12 Hours Needed" pct={24} />

      <Animated.View style={[styles.badge, { top: TOP - 4, left: 24 }, badgeStyle]} pointerEvents="none">
        <View style={styles.badgeDisc} />
        {size ? (
          <Canvas style={styles.badgeCanvas}>
            <Picture picture={ring} />
          </Canvas>
        ) : null}
        <View style={styles.badgeCore}>
          {bolt && tickPath ? (
            <Canvas style={styles.icon}>
              <Path path={done ? tickPath : bolt} style={done ? 'stroke' : 'fill'} strokeWidth={2.6} strokeCap="round" strokeJoin="round" color="#c8f54a" />
            </Canvas>
          ) : null}
        </View>
      </Animated.View>

      <View style={[styles.head, { top: headTop }]} accessible accessibilityRole="header" accessibilityLabel="Take control of your schedule">
        <View style={styles.line}>
          <Text style={styles.headText} allowFontScaling={false}>
            Take{' '}
          </Text>
          <View style={styles.pill} onLayout={(e) => pillW.set(e.nativeEvent.layout.width - 4)}>
            <Text style={[styles.headText, styles.pillText]} allowFontScaling={false}>
              control
            </Text>
            {/* the lime fill, on top, carries its own dark copy of the word, so the colour change is a clean wipe */}
            <Animated.View style={[styles.pillFill, fillStyle]}>
              <Text style={[styles.headText, styles.pillText, styles.pillInk]} allowFontScaling={false}>
                control
              </Text>
            </Animated.View>
          </View>
        </View>
        <View style={styles.line}>
          <Text style={styles.headText} allowFontScaling={false}>
            of{' '}
          </Text>
          <View style={styles.dot}>
            <View style={styles.dotHalf} />
          </View>
          <Text style={styles.headText} allowFontScaling={false}>
            {' '}your
          </Text>
        </View>
        <Text style={styles.headText} allowFontScaling={false}>
          schedule
        </Text>
      </View>

      <GestureDetector gesture={pan}>
        <View
          style={[styles.slider, { left: BAR.x, width: BAR.w, height: BAR.h, bottom: insets.bottom + 22 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={done ? "You're all set" : 'Get started'}
          accessibilityHint="Swipe right, or double-tap, to get started"
          onAccessibilityTap={() => scheduleOnUI(a11yTap)}
        >
          <Animated.View style={[styles.trail, trailStyle]} />
          <Animated.Text style={[styles.sliderLabel, labelStyle]} allowFontScaling={false}>
            Swipe to get started
          </Animated.Text>
          <Animated.Text style={[styles.sliderLabel, doneLabel]} allowFontScaling={false}>
            {"You're all set"}
          </Animated.Text>
          <Animated.View style={[styles.knob, knobStyle]}>
            {arrow && tickPath ? (
              <Canvas style={styles.icon}>
                <Path path={done ? tickPath : arrow} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color="#ffffff" />
              </Canvas>
            ) : null}
          </Animated.View>
        </View>
      </GestureDetector>
      {size ? (
        <Canvas style={[StyleSheet.absoluteFill, { top: 0 }]} pointerEvents="none">
          <Picture picture={confetti} />
        </Canvas>
      ) : null}
    </View>
  );
}

function Card({
  i,
  cards,
  tick,
  tone,
  title,
  sub,
  pct,
}: {
  i: number;
  cards: SharedValue<Body[]>;
  tick: SharedValue<number>;
  tone: 'glass' | 'lime' | 'dark';
  title: string;
  sub: string;
  pct: number;
}) {
  const place = useAnimatedStyle(() => {
    tick.get();
    const b = cards.get()[i];
    return {
      transform: [{ translateX: b.x - ROW.w / 2 }, { translateY: b.y - ROW.h / 2 }, { rotate: `${b.r}deg` }, { scale: b.s }],
    };
  });
  const ink = tone === 'lime' ? '#1d1a2b' : '#ffffff';
  return (
    <Animated.View style={[styles.card, tone === 'lime' && styles.lime, tone === 'dark' && styles.dark, place]} accessible accessibilityLabel={`${title}${sub ? `, ${sub}` : ''}`}>
      {tone === 'glass' ? <BlurView intensity={28} tint="light" style={StyleSheet.absoluteFill} /> : null}
      {tone === 'glass' ? <View style={[StyleSheet.absoluteFill, styles.glassTint]} /> : null}
      <View style={styles.cardBody}>
        <View style={styles.cardText}>
          <Text style={[styles.cardTitle, { color: ink }]} numberOfLines={1} allowFontScaling={false}>
            {title}
          </Text>
          {sub ? (
            <Text style={[styles.cardSub, { color: tone === 'lime' ? 'rgba(29,26,43,0.65)' : 'rgba(255,255,255,0.7)' }]} allowFontScaling={false}>
              {sub}
            </Text>
          ) : (
            <View style={styles.tags}>
              <Text style={styles.tag} allowFontScaling={false}>
                DESIGN
              </Text>
              <Text style={styles.tag} allowFontScaling={false}>
                REVIEW
              </Text>
              <Text style={[styles.cardSub, { color: 'rgba(29,26,43,0.65)' }]} allowFontScaling={false}>
                Nov 12
              </Text>
            </View>
          )}
        </View>
        {pct >= 0 ? (
          <View style={styles.pct}>
            <View style={[styles.ring, { borderColor: tone === 'glass' ? 'rgba(200,245,74,0.95)' : '#c8f54a' }]} />
            <Text style={[styles.pctText, { color: ink }]} allowFontScaling={false}>
              {pct}%
            </Text>
          </View>
        ) : null}
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#6a37ef' },
  loop: { position: 'absolute', width: 380, height: 380, borderRadius: 190, borderWidth: 92, borderColor: '#5b2be0' },
  bar: { position: 'absolute', left: 22, right: 22, height: 36, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { color: '#ffffff', fontSize: 20, fontFamily: 'STO-Display', letterSpacing: -0.3 },
  skip: { color: '#ffffff', fontSize: 15, fontFamily: 'STO-Medium' },
  card: { position: 'absolute', left: 0, top: 0, width: ROW.w, height: ROW.h, borderRadius: 18, overflow: 'hidden', borderWidth: 1, borderColor: 'rgba(255,255,255,0.28)' },
  glassTint: { backgroundColor: 'rgba(255,255,255,0.14)' },
  lime: { backgroundColor: '#c8f54a', borderColor: 'rgba(29,26,43,0.08)' },
  dark: { backgroundColor: '#24202f', borderColor: 'rgba(255,255,255,0.1)' },
  cardBody: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, gap: 12 },
  cardText: { flex: 1, gap: 6 },
  cardTitle: { fontSize: 18, fontFamily: 'STO-Medium', letterSpacing: -0.2 },
  cardSub: { fontSize: 13, fontFamily: 'STO-Medium' },
  tags: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  tag: { fontSize: 10, fontFamily: 'STO-SemiBold', color: '#c8f54a', backgroundColor: '#1d1a2b', paddingHorizontal: 7, paddingVertical: 3, borderRadius: 9, overflow: 'hidden' },
  pct: { width: 46, height: 46, alignItems: 'center', justifyContent: 'center' },
  ring: { position: 'absolute', width: 46, height: 46, borderRadius: 23, borderWidth: 4, borderTopColor: 'rgba(255,255,255,0.18)', borderRightColor: 'rgba(255,255,255,0.18)' },
  pctText: { fontSize: 12, fontFamily: 'STO-SemiBold' },
  badge: { position: 'absolute', width: 128, height: 128, alignItems: 'center', justifyContent: 'center' },
  badgeDisc: { position: 'absolute', width: 118, height: 118, borderRadius: 59, backgroundColor: '#c8f54a' },
  badgeCanvas: { position: 'absolute', width: 128, height: 128 },
  badgeCore: { width: 62, height: 62, borderRadius: 31, backgroundColor: '#1d1a2b', alignItems: 'center', justifyContent: 'center' },
  icon: { width: 24, height: 24 },
  head: { position: 'absolute', left: 24, right: 24 },
  line: { flexDirection: 'row', alignItems: 'center' },
  headText: { color: '#ffffff', fontSize: 50, lineHeight: 58, fontFamily: 'STO-Display', letterSpacing: -1.5 },
  pill: { borderWidth: 2, borderColor: '#ffffff', borderRadius: 32, paddingHorizontal: 14, height: 60, justifyContent: 'center', overflow: 'hidden' },
  pillFill: { position: 'absolute', left: 0, top: 0, bottom: 0, backgroundColor: '#c8f54a', overflow: 'hidden' },
  pillInk: { position: 'absolute', left: 14, top: 3, marginTop: 0, color: '#1d1a2b', width: 220 },
  pillText: { lineHeight: 58, marginTop: 4 },
  dot: { width: 46, height: 46, borderRadius: 23, backgroundColor: '#c8f54a', overflow: 'hidden', alignItems: 'flex-end' },
  dotHalf: { width: 23, height: 46, backgroundColor: '#1d1a2b' },
  slider: { position: 'absolute', borderRadius: 31, backgroundColor: '#c8f54a', justifyContent: 'center' },
  trail: { position: 'absolute', left: 0, top: 0, bottom: 0, borderRadius: 31, backgroundColor: '#b4e036' },
  sliderLabel: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#1d1a2b', fontSize: 17, fontFamily: 'STO-SemiBold' },
  knob: { position: 'absolute', left: 4, width: 54, height: 54, borderRadius: 27, backgroundColor: '#1d1a2b', alignItems: 'center', justifyContent: 'center' },
});

export { LOOP };
