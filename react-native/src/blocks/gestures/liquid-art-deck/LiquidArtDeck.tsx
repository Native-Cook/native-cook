import { Canvas, Path, Picture, Skia, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
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
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { ART, ARTFX, CARD, clamp01, type Deck, easeInOut, FLY, LOOP, makeDeck, mix, N, scriptFinger, smooth, stepDeck, swipe, undo } from './lad-deck';
import { type Cue, useLadAudio } from './use-lad-audio';

/*
 Liquid Art Deck — the discover screen of an invented art-collecting app.

 Look (the pin, a video of a Figma "Tinder swipe" tutorial): a dark screen with a row of creators,
 Favorites, and a deck of art cards with liquid-chrome portraits, the next card peeking from the side
 at 80 %. The pin's prototype only slides between cards; the people and art in it are third-party.
 Ours are invented, and the art is a shader.

 Motion (ours): the art is liquid. Drag a card and its paint is dragged back along the motion like
 thick syrup, by how fast you move; let go and it wobbles back like jelly. Swipe right and the card
 shrinks along an arc into the heart in the tab bar, which beats and counts up; swipe left and it
 slides away. Undo brings the last card back along the very path it left by, out of the heart if it
 was liked. The next card grows from its peek into place.

 autoPlay: LOOP = 9 s. 1.0-1.6 s drag right, like; 3.4-3.9 s drag left, pass; 5.6 s undo (the passed
 card returns), 7.2 s undo (the liked card comes out of the heart). Frame 9 s equals frame 0.
 Reduced motion: no smear; cards cross-fade into place.
*/

let debugState = '';
const HEART = 'M12 20.5s-7.5-4.6-7.5-10.1A4.3 4.3 0 0 1 12 7.7a4.3 4.3 0 0 1 7.5 2.7c0 5.5-7.5 10.1-7.5 10.1z';

const TAB_HOME = 'M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z';
const TAB_SEARCH = 'M11 4a7 7 0 1 0 0.01 0 M16 16l4.5 4.5';
const TAB_USER = 'M8.2 8.5a3.8 3.8 0 1 0 7.6 0a3.8 3.8 0 1 0 -7.6 0 M4.8 20.5c1.1-3.8 3.9-5.8 7.2-5.8s6.1 2 7.2 5.8';
function TabIcon({ d }: { d: string }) {
  const path = useMemo(() => Skia.Path.MakeFromSVGString(d), [d]);
  if (!path) return null;
  return (
    <Canvas style={styles.icon}>
      <Path path={path} style="stroke" strokeWidth={1.9} strokeCap="round" strokeJoin="round" color="rgba(255,255,255,0.5)" transform={[{ scale: 26 / 24 }]} />
    </Canvas>
  );
}

type Props = { autoPlay?: boolean };

export function LiquidArtDeck({ autoPlay = false }: Props) {
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
    'LAD-Title': require('./assets/fonts/Outfit-Medium.ttf'),
    'LAD-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'LAD-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const titleFont = useFont(require('./assets/fonts/Outfit-Medium.ttf'), 19);
  const smallFont = useFont(require('./assets/fonts/Inter-Medium.ttf'), 12.5);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const CY = insets.top + 128 + CARD.h / 2 + 20;
  const TAB_Y = h - insets.bottom - 34;
  const HX = w * 0.625; // the heart in the tab bar
  const PEEK = CARD.w * 0.5 + CARD.w * 0.8 * 0.5 - 42;

  const play = useLadAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  const deck = useSharedValue<Deck>(makeDeck());
  const phase = useSharedValue(0); // the art's drift clock: equals t while the loop is untouched
  const debugT = useSharedValue(-1);
  const [count, setCount] = useState(12);
  const [front, setFront] = useState(0);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __liquidDeck?: unknown };
    g.__liquidDeck = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__liquidDeck;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** One fixed step of the autoplay script, from loop time t0 to t1. */
  const scriptStep = (d: Deck, t0: number, t1: number) => {
    'worklet';
    const f = scriptFinger(t1);
    if (f.on) {
      d.held = 1;
      d.dx = f.dx;
      d.dy = f.dy;
    } else if (d.held === 1) {
      d.held = 0;
      swipe(d, d.dx > 0 ? 1 : 2, t1);
    }
    if (t0 < 5.6 && t1 >= 5.6) undo(d, t1);
    if (t0 < 7.2 && t1 >= 7.2) undo(d, t1);
    stepDeck(d, t1 - t0, t1);
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    let d = deck.get();
    let t = time.get();
    if (dbg >= 0) {
      d = makeDeck();
      let st = 0;
      while (st < dbg) {
        const s1 = Math.min(dbg, st + 1 / 120);
        scriptStep(d, st, s1);
        st = s1;
      }
      deck.set(d);
      t = dbg;
      debugT.set(-2);
    } else if (dbg === -2) {
      // pinned
    } else if (auto && d.held === 0 && now - last.get() >= 2 && (t > 0 || (d.cur === 0 && d.fly < 0 && d.hist.length === 0 && Math.abs(d.dx) < 1 && Math.abs(d.ds[0]) < 0.01))) {
      // after a touch the loop resumes only from the deck's first state (else the script and the deck disagree)
      const t1 = t + dt;
      if (t1 >= LOOP) {
        // a fresh deck for the next loop (it is the same as the one this loop ended with)
        d = makeDeck();
        deck.set(d);
        t = t1 - LOOP;
      } else {
        scriptStep(d, t, t1);
        t = t1;
      }
    } else {
      stepDeck(d, dt, now);
    }
    if (dbg >= 0 || dbg === -2) phase.set(t);
    else phase.set((phase.get() + dt) % LOOP);
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} cur=${d.cur} fly=${d.fly} count=${d.count}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  useAnimatedReaction(
    () => {
      tick.get();
      const d = deck.get();
      return d.count * 100 + d.cur;
    },
    (n, prev) => {
      if (n !== prev) {
        scheduleOnRN(setCount, Math.floor(n / 100));
        scheduleOnRN(setFront, n % 100);
      }
    },
  );

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const d = deck.get();
    const dbg = debugT.get();
    const now = dbg === -2 || (auto && d.held === 0 && clock.get() - last.get() >= 2) ? time.get() : clock.get();
    if (!ARTFX || !titleFont || !smallFont) return rec.finishRecordingAsPicture();
    const ph = (2 * Math.PI * phase.get()) / LOOP;
    // the creators' row: each avatar is a little window onto their own art
    for (let i = 0; i < 6; i++) {
      const ccx = 20 + 25 + i * ((w - 40 - 50) / 5);
      const ccy = insets.top + 35;
      const ap = Skia.Paint();
      ap.setAntiAlias(true);
      ap.setShader(ARTFX.makeShader([ccx, ccy, 46, 46, ART[i].seed, 0, 0, ph + i]));
      c.drawCircle(ccx, ccy, 21, ap);
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(1);
      ring.setStrokeWidth(2);
      ring.setColor(Skia.Color(i === 0 ? '#e8ff8a' : 'rgba(255,255,255,0.18)'));
      c.drawCircle(ccx, ccy, 25, ring);
    }
    // order: far slots first, the front card, then the card in flight on top
    const order: number[] = [];
    for (let s = 3; s >= 0; s--) {
      for (let i = 0; i < N; i++) {
        if (i === d.fly) continue;
        const slot = Math.round(d.ds[i]);
        if (slot === s && i >= d.cur) order.push(i);
      }
    }
    if (d.fly >= 0) order.push(d.fly);
    for (let n = 0; n < order.length; n++) {
      const i = order[n];
      let x = 0;
      let y = 0;
      let rot = 0;
      let sc = 1;
      let alpha = 1;
      let dim = 0;
      if (i === d.fly) {
        const dur = d.kind === 1 ? FLY.like : FLY.pass;
        let k = clamp01((now - d.start) / dur);
        if (d.back === 1) k = 1 - k;
        const e = d.back === 1 ? easeInOut(k) : k * k * (3 - 2 * k);
        if (d.kind === 1) {
          // into the heart along an arc, shrinking
          const tx = HX - cx;
          const ty = TAB_Y - CY;
          const kx = (d.fx + tx) * 0.5;
          const ky = Math.min(d.fy, ty) - 170;
          const u = 1 - e;
          x = u * u * d.fx + 2 * u * e * kx + e * e * tx;
          y = u * u * d.fy + 2 * u * e * ky + e * e * ty;
          sc = mix(1, 0.06, e * e);
          rot = mix(d.fx * 0.06, 8, e);
          alpha = 1 - smooth((e - 0.88) / 0.12);
        } else {
          x = mix(d.fx, -w * 1.25, e * e);
          y = mix(d.fy, d.fy + 50, e);
          rot = mix(d.fx * 0.06, -22, e);
        }
      } else {
        const ds = d.ds[i];
        const a = Math.min(Math.abs(ds), 1);
        x = Math.sign(ds) * (PEEK * a + Math.max(0, Math.abs(ds) - 1) * 140);
        sc = 1 - 0.2 * a;
        dim = 0.55 * a;
        alpha = 1 - smooth(Math.abs(ds) - 1.4);
        if (i === d.cur) {
          x += d.dx;
          y += d.dy;
          rot = d.dx * 0.06;
        }
      }
      if (alpha <= 0.01) continue;
      c.save();
      c.translate(cx + x, CY + y);
      c.rotate(rot, 0, 0);
      c.scale(sc, sc);
      const layer = Skia.Paint();
      layer.setAlphaf(alpha);
      c.saveLayer(layer);
      const W = CARD.w;
      const H = CARD.h;
      const rr = Skia.RRectXY(Skia.XYWHRect(-W / 2, -H / 2, W, H), 26, 26);
      const shadow = Skia.Paint();
      shadow.setColor(Skia.Color('#000000'));
      shadow.setAlphaf(0.5);
      shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 18, true));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-W / 2 + 10, -H / 2 + 22, W - 20, H - 10), 26, 26), shadow);
      const body = Skia.Paint();
      body.setColor(Skia.Color('#1c1c22'));
      c.drawRRect(rr, body);
      // the art, smeared by the card's own motion
      const AW = W - 20;
      const AH = 290;
      const ax = -AW / 2;
      const ay = -H / 2 + 10;
      const live = i === d.fly || i === d.cur;
      const smx = live && !reduced ? d.sx : 0;
      const smy = live && !reduced ? d.sy : 0;
      const ap = Skia.Paint();
      ap.setShader(ARTFX.makeShader([0, ay + AH / 2, AW, AH, ART[i].seed, smx, smy, ph + i]));
      c.save();
      c.clipRRect(Skia.RRectXY(Skia.XYWHRect(ax, ay, AW, AH), 18, 18), 1, true);
      c.drawRect(Skia.XYWHRect(ax, ay, AW, AH), ap);
      c.restore();
      const tp = Skia.Paint();
      tp.setAntiAlias(true);
      tp.setColor(Skia.Color('#ffffff'));
      c.drawText(ART[i].title, ax + 4, ay + AH + 30, tp, titleFont);
      const sp = Skia.Paint();
      sp.setAntiAlias(true);
      sp.setColor(Skia.Color('#9a9aa6'));
      c.drawText(ART[i].by, ax + 4, ay + AH + 52, sp, smallFont);
      const pw = smallFont.getGlyphWidths(smallFont.getGlyphIDs(ART[i].price));
      let tw = 0;
      for (let m = 0; m < pw.length; m++) tw += pw[m];
      const pill = Skia.Paint();
      pill.setAntiAlias(true);
      pill.setColor(Skia.Color('#2b2b33'));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(AW / 2 - tw - 22, ay + AH + 28, tw + 18, 26), 13, 13), pill);
      tp.setColor(Skia.Color('#e8ff8a'));
      c.drawText(ART[i].price, AW / 2 - tw - 13, ay + AH + 45, tp, smallFont);
      if (dim > 0.01) {
        const dp = Skia.Paint();
        dp.setColor(Skia.Color('#0f0f12'));
        dp.setAlphaf(dim);
        c.drawRRect(rr, dp);
      }
      c.restore();
      c.restore();
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, cx, CY, TAB_Y, HX, PEEK, titleFont, smallFont, insets.top]);

  // the heart in the tab bar beats when a card lands in it
  const heartStyle = useAnimatedStyle(() => {
    tick.get();
    const d = deck.get();
    const now = debugT.get() === -2 || (auto && d.held === 0 && clock.get() - last.get() >= 2) ? time.get() : clock.get();
    const s = now - d.beat;
    const k = s >= 0 && s < 0.6 ? Math.sin(Math.min(1, s / 0.12) * Math.PI) * 0.35 * Math.exp(-s * 4) + Math.sin(s * 18) * 0.08 * Math.exp(-s * 7) : 0;
    return { transform: [{ scale: 1 + k }] };
  });

  // ---------------------------------------------------------------- touch
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      const d = deck.get();
      last.set(clock.get());
      time.set(0);
      if (d.fly >= 0 || d.cur >= N) return;
      d.held = 1;
      d.vx = 0;
      d.vy = 0;
    })
    .onUpdate((e) => {
      const d = deck.get();
      if (d.held !== 1) return;
      last.set(clock.get());
      d.dx = e.translationX;
      d.dy = e.translationY * 0.6;
    })
    .onFinalize((e) => {
      const d = deck.get();
      if (d.held !== 1) return;
      last.set(clock.get());
      d.held = 0;
      const proj = d.dx + e.velocityX * 0.18;
      if (proj > 120) {
        swipe(d, 1, clock.get());
        scheduleOnRN(cue, 'like');
        scheduleOnRN(buzz, 2);
      } else if (proj < -120) {
        swipe(d, 2, clock.get());
        scheduleOnRN(cue, 'pass');
        scheduleOnRN(buzz, 1);
      } else {
        d.vx = e.velocityX;
        d.vy = e.velocityY * 0.6;
      }
    });
  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const actUI = (kind: number) => {
    'worklet';
    const d = deck.get();
    last.set(clock.get());
    time.set(0);
    if (kind === 3) {
      if (undo(d, clock.get())) {
        scheduleOnRN(cue, 'undo');
        scheduleOnRN(buzz, 0);
      }
      return;
    }
    if (swipe(d, kind, clock.get())) {
      scheduleOnRN(cue, kind === 1 ? 'like' : 'pass');
      scheduleOnRN(buzz, kind === 1 ? 2 : 1);
    }
  };
  const act = (kind: number) => scheduleOnUI(actUI, kind);

  const heart = useMemo(() => Skia.Path.MakeFromSVGString(HEART), []);
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;
  const art = ART[Math.min(front, N - 1)];

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <View style={[styles.head, { top: insets.top + 78 }]}>
        <Text style={styles.headTitle} allowFontScaling={false} accessibilityRole="header">
          Favorites
        </Text>
        <Text style={styles.seeAll} allowFontScaling={false}>
          See all ›
        </Text>
      </View>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
          <View
            style={[styles.cardHit, { left: cx - CARD.w / 2, top: CY - CARD.h / 2 }]}
            accessible
            accessibilityLabel={front < N ? `${art.title} by ${art.by.slice(1)}, ${art.price}. Swipe right to add to favourites, left to pass.` : 'No more art'}
            accessibilityActions={[{ name: 'activate', label: 'Add to favourites' }, { name: 'escape', label: 'Pass' }]}
            onAccessibilityAction={(e) => act(e.nativeEvent.actionName === 'activate' ? 1 : 2)}
          />
        </View>
      </GestureDetector>
      <View style={[styles.actions, { top: CY + CARD.h / 2 + 22 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Pass" onPress={() => act(2)} style={({ pressed }) => [styles.round, pressed && styles.pressed]}>
          <Text style={styles.roundGlyph} allowFontScaling={false}>
            ✕
          </Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Undo" onPress={() => act(3)} style={({ pressed }) => [styles.round, styles.small, pressed && styles.pressed]}>
          <Text style={styles.roundGlyph} allowFontScaling={false}>
            ↺
          </Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Add to favourites" onPress={() => act(1)} style={({ pressed }) => [styles.round, styles.like, pressed && styles.pressed]}>
          {heart ? (
            <Canvas style={styles.icon}>
              <Path path={heart} color="#0f0f12" transform={[{ scale: 26 / 24 }]} />
            </Canvas>
          ) : null}
        </Pressable>
      </View>
      <View style={[styles.tabs, { bottom: insets.bottom + 6 }]}>
        <TabIcon d={TAB_HOME} />
        <TabIcon d={TAB_SEARCH} />
        <Animated.View style={[styles.heartTab, { left: HX - 20 - 18 }, heartStyle]} accessible accessibilityLabel={`Favourites, ${count}`}>
          {heart ? (
            <Canvas style={styles.icon}>
              <Path path={heart} color="#ff6ad0" transform={[{ scale: 26 / 24 }]} />
            </Canvas>
          ) : null}
          <View style={styles.badge}>
            <Text style={styles.badgeText} allowFontScaling={false}>
              {count}
            </Text>
          </View>
        </Animated.View>
        <View style={styles.tabLast}>
          <TabIcon d={TAB_USER} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0f0f12' },
  head: { position: 'absolute', left: 22, right: 22, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  headTitle: { color: '#ffffff', fontSize: 22, fontFamily: 'LAD-Title' },
  seeAll: { color: 'rgba(255,255,255,0.55)', fontSize: 14, fontFamily: 'LAD-Medium' },
  cardHit: { position: 'absolute', width: CARD.w, height: CARD.h },
  actions: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 22 },
  round: { width: 58, height: 58, borderRadius: 29, backgroundColor: '#22222a', alignItems: 'center', justifyContent: 'center', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.12)' },
  small: { width: 46, height: 46, borderRadius: 23 },
  like: { backgroundColor: '#e8ff8a' },
  roundGlyph: { color: '#ffffff', fontSize: 20, fontFamily: 'LAD-SemiBold' },
  icon: { width: 26, height: 26 },
  tabs: { position: 'absolute', left: 18, right: 18, height: 62, borderRadius: 31, backgroundColor: '#1b1b21', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 30, gap: 52 },
  tabLast: { position: 'absolute', right: 30 },
  heartTab: { position: 'absolute', width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  badge: { position: 'absolute', right: -10, top: -2, minWidth: 22, height: 18, borderRadius: 9, backgroundColor: '#ff6ad0', alignItems: 'center', justifyContent: 'center', paddingHorizontal: 4 },
  badgeText: { color: '#0f0f12', fontSize: 11, fontFamily: 'LAD-SemiBold', fontVariant: ['tabular-nums'] },
  pressed: { transform: [{ scale: 0.95 }] },
});

export { LOOP };
