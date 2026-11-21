import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Blur, Canvas, Group, ImageShader, Path, Rect, Shader, Skia, Text as SkText, type SkFont, type SkImage, useFont, vec } from '@shopify/react-native-skia';
import Svg, { Circle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { arrowPath, makeFace, textWidth } from './arc-art';
import { type ArcFrame, clamp, fingerSlats, frameAt, LOOP, mix, releaseAt, restFrame, seg, shortestTurn, smooth } from './arc-motion';
import { TRIVISION_FX } from './arc-shaders';
import { C, DECK, FONT, G, headingFor } from './arc-theme';

type Cue = 'light' | 'medium' | 'rigid' | 'selection';
const buzz = (cue: Cue) => {
  if (cue === 'selection') void Haptics.selectionAsync().catch(() => {});
  else void Haptics.impactAsync(cue === 'light' ? Haptics.ImpactFeedbackStyle.Light : cue === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
};
const PANEL_CLIP = Skia.RRectXY(Skia.XYWHRect(0, 0, G.panelW, G.panelH), 18, 18);
// Open on the panel's right and bottom edges: a leaving digit slides off the sign, not into an invisible wall.
const DRUM_CLIP = Skia.XYWHRect(278, 252, 97, 122);

function Icon({ name, saved = false }: { name: 'bookmark' | 'calendar' | 'folder' | 'chevron'; saved?: boolean }) {
  const d = name === 'bookmark' ? 'M5 4H19V20L12 15.5L5 20Z' : name === 'calendar' ? 'M4 6H20V21H4Z M8 3V8 M16 3V8 M4 10H20' : name === 'folder' ? 'M3 7H9L11 9H21V20H3Z' : 'M2 3L9 10L16 3';
  return <Svg width={name === 'chevron' ? 18 : 24} height={name === 'chevron' ? 12 : 24} viewBox={name === 'chevron' ? '0 0 18 12' : '0 0 24 24'}><SvgPath d={d} fill={saved ? C.ink : 'none'} stroke={C.ink} strokeWidth={2} strokeLinecap="square" strokeLinejoin="miter" />{name === 'calendar' && <Circle cx={8} cy={15} r={1.25} fill={C.ink} />}</Svg>;
}

/** One album line as a split-flap: the old name rolls up out of a clip as the new one rolls in, line 2 a beat later. */
function AlbumName({ index, line, frame }: { index: number; line: 0 | 1; frame: SharedValue<ArcFrame> }) {
  const style = useAnimatedStyle(() => {
    const f = frame.get();
    if (f.flat) return { opacity: f.index === index ? 1 - f.drum : f.next === index ? f.drum : 0, transform: [{ translateY: 0 }] };
    const e = smooth(seg(f.drum, 0.08 + 0.16 * line, 0.68 + 0.16 * line));
    if (f.index === index) return { opacity: 1, transform: [{ translateY: -29 * e }] };
    if (f.next === index) return { opacity: 1, transform: [{ translateY: 29 * (1 - e) }] };
    return { opacity: 0, transform: [{ translateY: 29 }] };
  });
  return <Animated.Text style={[styles.albumText, styles.albumName, line === 1 && styles.albumName2, line === 1 && { color: C.albumSecondary }, style]}>{DECK[index].album[line]}</Animated.Text>;
}

function Album({ frame }: { frame: SharedValue<ArcFrame> }) {
  return <View style={styles.album} pointerEvents="none">
    {([0, 1] as const).map(line => <View key={line} style={[styles.albumClip, line === 0 ? { top: 0, height: 27 } : { top: 27, height: 29 }]}>{DECK.map((_, i) => <AlbumName key={i} index={i} line={line} frame={frame} />)}</View>)}
  </View>;
}

function SavedMark({ index, frame, saved }: { index: number; frame: SharedValue<ArcFrame>; saved: boolean }) {
  const style = useAnimatedStyle(() => ({ opacity: (frame.get().drum < 0.5 ? frame.get().index : frame.get().next) === index ? 1 : 0 }));
  return <Animated.View style={[StyleSheet.absoluteFill, style]}><Icon name="bookmark" saved={saved} /></Animated.View>;
}

function Numeral({ index, frame, font }: { index: number; frame: SharedValue<ArcFrame>; font: SkFont }) {
  const visible = useDerivedValue(() => {
    const f = frame.get();
    if (f.flat) return f.index === index ? 1 - f.drum : f.next === index ? f.drum : 0;
    return f.index === index || f.next === index ? 1 : 0;
  });
  const transform = useDerivedValue(() => {
    const f = frame.get();
    const offset = f.flat ? 0 : f.index === index ? f.drum * 110 : (f.drum - 1) * 110;
    return [{ translateX: f.axis === 0 ? offset * (f.reverse ? -1 : 1) : 0 }, { translateY: f.axis === 1 ? offset * (f.reverse ? -1 : 1) : 0 }];
  });
  // One-axis motion blur along the roll, at most 6 pt, only while the drum moves.
  const blur = useDerivedValue(() => { const f = frame.get(); const b = f.flat ? 0 : f.blur / 2; return f.axis === 0 ? vec(b, 0) : vec(0, b); });
  const x = 364 - textWidth(font, DECK[index].rank);
  return <Group opacity={visible} transform={transform}><SkText x={x} y={358} text={DECK[index].rank} font={font} color={C.ink}><Blur blur={blur} mode="decal" /></SkText></Group>;
}

function SignPanel({ frame, images, font }: { frame: SharedValue<ArcFrame>; images: SkImage[]; font: SkFont }) {
  const oldFace = useDerivedValue(() => images[frame.get().index]);
  const newFace = useDerivedValue(() => images[frame.get().next]);
  const uniforms = useDerivedValue(() => ({ q: frame.get().q, axis: frame.get().axis, reverse: frame.get().reverse, twitch: frame.get().twitch, flatMix: frame.get().flat ? frame.get().drum : -1 }));
  const arrow = useDerivedValue(() => { const f = frame.get(); return arrowPath(f.flat ? { ...f, head: headingFor(DECK[f.index].direction), tail: headingFor(DECK[f.index].direction) } : f); });
  const nextArrow = useDerivedValue(() => { const f = frame.get(); return arrowPath({ ...f, head: headingFor(DECK[f.next].direction), tail: headingFor(DECK[f.next].direction) }); });
  const oldOpacity = useDerivedValue(() => frame.get().flat ? 1 - frame.get().drum : 1);
  const nextOpacity = useDerivedValue(() => frame.get().flat ? frame.get().drum : 0);
  const shadow = useDerivedValue(() => {
    // Plain loops: the React Compiler hoists capture-free arrows out of worklets.
    let m = 0;
    for (const v of frame.get().q) m = Math.max(m, v * (1 - v));
    return 0.18 * clamp(8 * m);
  });
  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
    <Group clip={PANEL_CLIP}>
      <Rect x={0} y={0} width={375} height={374}><Shader source={TRIVISION_FX!} uniforms={uniforms}>
        <ImageShader image={oldFace} fit="fill" rect={Skia.XYWHRect(0, 0, 375, 374)} />
        <ImageShader image={newFace} fit="fill" rect={Skia.XYWHRect(0, 0, 375, 374)} />
      </Shader></Rect>
      <Group opacity={shadow} transform={[{ translateY: 2 }]}><Path path={arrow} color={C.ink} style="stroke" strokeWidth={22} strokeCap="butt" strokeJoin="miter" /><Group clip={DRUM_CLIP}>{DECK.map((_, i) => <Numeral key={i} index={i} frame={frame} font={font} />)}</Group></Group>
      <Group opacity={oldOpacity}><Path path={arrow} color={C.ink} style="stroke" strokeWidth={22} strokeCap="butt" strokeJoin="miter" /></Group>
      <Group opacity={nextOpacity}><Path path={nextArrow} color={C.ink} style="stroke" strokeWidth={22} strokeCap="butt" strokeJoin="miter" /></Group>
      <Group clip={DRUM_CLIP}>{DECK.map((_, i) => <Numeral key={i} index={i} frame={frame} font={font} />)}</Group>
    </Group>
  </Canvas>;
}

/** Persistent wayfinding sign over a nine-prism chart deck. All time and live state are on the UI thread. */
export function ArrowRankCards({ autoPlay = true }: { autoPlay?: boolean }) {
  const systemReduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ w: 402, h: 874 });
  const S = size.w / 402;
  const [saved, setSaved] = useState([false, false, false]);
  const [fontsOk] = useFonts({ [FONT.bold]: require('./assets/InterTight-Bold.ttf'), [FONT.extraBold]: require('./assets/InterTight-ExtraBold.ttf') });
  const titleFont = useFont(require('./assets/InterTight-Bold.ttf'), 34);
  const artistFont = useFont(require('./assets/InterTight-Bold.ttf'), 15);
  const chipFont = useFont(require('./assets/InterTight-Bold.ttf'), 30);
  const rankFont = useFont(require('./assets/InterTight-ExtraBold.ttf'), 130);
  const images = useMemo(() => titleFont && artistFont && chipFont ? DECK.map((_, i) => makeFace(i, titleFont, artistFont, chipFont)) : null, [titleFont, artistFont, chipFont]);
  useEffect(() => () => { images?.forEach(image => image.dispose()); }, [images]);

  const T = useSharedValue(0), auto = useSharedValue(autoPlay ? 1 : 0), pinned = useSharedValue(0), rate = useSharedValue(1);
  const now = useSharedValue(0), touching = useSharedValue(0), lastTouch = useSharedValue(0), reduced = useSharedValue(systemReduced ? 1 : 0);
  const live = useSharedValue<ArcFrame>(restFrame()), start = useSharedValue<ArcFrame>(restFrame());
  const release = useSharedValue(-1), commit = useSharedValue(0), releaseVelocity = useSharedValue(0);
  const wrongAt = useSharedValue(-1), hintAt = useSharedValue(-1), wrongOnce = useSharedValue(0), threshold = useSharedValue(0);
  const rulerStart = useSharedValue(0), rulerValue = useSharedValue(0), rulerOn = useSharedValue(0), rulerVelocity = useSharedValue(0);
  const bookmarkScale = useSharedValue(1), lastHaptic = useSharedValue(-1), lastSelection = useSharedValue(0);
  const returnAt = useSharedValue(-1), returnStart = useSharedValue<ArcFrame>(restFrame());
  useEffect(() => { reduced.set(systemReduced ? 1 : 0); if (systemReduced) { auto.set(0); live.set(restFrame()); } else auto.set(autoPlay ? 1 : 0); }, [systemReduced, autoPlay, reduced, auto, live]);

  const F = useDerivedValue(() => auto.get() ? frameAt(T.get()) : live.get());

  const begin = () => {
    'worklet';
    const f = F.get();
    live.set(f); start.set(f); auto.set(0); pinned.set(0); release.set(-1); returnAt.set(-1);
    touching.set(1); rulerOn.set(0); hintAt.set(-1); wrongAt.set(-1); wrongOnce.set(0); threshold.set(0);
    lastTouch.set(now.get()); scheduleOnRN(buzz, 'light');
  };
  const move = (x: number, y: number) => {
    'worklet';
    const f = start.get();
    const angle = headingFor(DECK[f.index].direction) * Math.PI / 180;
    const dot = x * Math.cos(angle) + y * Math.sin(angle);
    const p = clamp(f.p + dot / G.distance, 0, 1.15);
    if (dot < -10 && !wrongOnce.get()) {
      wrongOnce.set(1); wrongAt.set(now.get()); scheduleOnRN(buzz, 'rigid');
    }
    const base = fingerSlats(f.p), next = fingerSlats(p);
    live.set({ ...f, p, q: reduced.get() ? f.q : f.q.map((q, i) => clamp(q + next[i] - base[i])),
      stretch: reduced.get() ? 0 : clamp(f.stretch + 72 * (p - f.p), 0, 36) });
    if (p >= 0.5 && !threshold.get()) { threshold.set(1); scheduleOnRN(buzz, 'light'); }
  };
  const end = (vx: number, vy: number, success = true) => {
    'worklet';
    const f = live.get();
    const angle = headingFor(DECK[f.index].direction) * Math.PI / 180;
    const velocity = success ? (vx * Math.cos(angle) + vy * Math.sin(angle)) / G.distance : 0;
    commit.set(success && f.p + velocity * 0.12 >= 0.5 ? 1 : 0);
    releaseVelocity.set(velocity); start.set(f); release.set(0); touching.set(0); lastTouch.set(now.get());
  };
  const rulerBegin = () => {
    'worklet';
    const position = rulerOn.get() ? rulerValue.get() : F.get().index;
    begin();
    rulerOn.set(1); rulerValue.set(position); rulerStart.set(position); rulerVelocity.set(0);
  };
  const rulerMove = (dx: number) => {
    'worklet';
    rulerValue.set(rulerStart.get() - dx / 12);
  };
  const rulerEnd = (vx: number) => {
    'worklet';
    touching.set(0); rulerVelocity.set(clamp(-vx / 12, -12, 12)); lastTouch.set(now.get());
  };
  const hint = () => { 'worklet'; begin(); touching.set(0); hintAt.set(now.get()); };

  useFrameCallback(info => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get()) return;
    if (auto.get()) { if (!reduced.get()) T.set((T.get() + dt) % LOOP); return; }
    if (rulerOn.get()) {
      if (!touching.get()) {
        const previousVelocity = rulerVelocity.get();
        const velocity = previousVelocity * Math.exp(-dt * 7);
        rulerVelocity.set(velocity);
        rulerValue.set(rulerValue.get() + (previousVelocity - velocity) / 7);
        if (Math.abs(velocity) < 0.25) {
          rulerValue.set(mix(rulerValue.get(), Math.round(rulerValue.get()), Math.min(1, dt * 20)));
          if (Math.abs(rulerValue.get() - Math.round(rulerValue.get())) < 0.001) {
            live.set(restFrame(Math.round(rulerValue.get()))); rulerOn.set(0);
          }
        }
      }
      if (rulerOn.get()) {
        const v = rulerValue.get(); const n = Math.floor(v); const p = v - n;
        const f = restFrame(n);
        const direction = headingFor(DECK[f.next].direction);
        const a = shortestTurn(f.head, direction);
        live.set({ ...f, p, q: reduced.get() ? f.q : fingerSlats(p), drum: p, flat: reduced.get(), head: reduced.get() ? f.head : mix(f.head, a, smooth(p)), tail: reduced.get() ? f.tail : mix(f.tail, a, smooth(p)), ticks: -v * 12 });
      }
    } else if (release.get() >= 0) {
      const previous = release.get(); const elapsed = previous + dt; release.set(elapsed);
      if (reduced.get()) {
        // Flat crossfade, no prism rotation, stretch or directional drum in reduced motion.
        const f = start.get(); const p = commit.get() ? seg(elapsed, 0, 0.2) : 0;
        live.set({ ...f, q: Array(9).fill(0), drum: p, stretch: 0, flat: 1 });
      } else live.set(releaseAt(start.get(), elapsed, !!commit.get(), releaseVelocity.get()));
      const landing = reduced.get() ? 0.2 : 0.3;
      if (commit.get() && previous < landing && elapsed >= landing) scheduleOnRN(buzz, 'medium');
      if (elapsed >= (reduced.get() ? 0.2 : 0.78)) {
        const f = start.get(); live.set(restFrame(commit.get() ? f.next : f.index)); release.set(-1);
      }
    } else if (!touching.get() && !reduced.get()) {
      const f = live.get();
      if (hintAt.get() >= 0) {
        const u = seg(now.get() - hintAt.get(), 0, 0.36);
        live.set({ ...f, stretch: 18 * Math.sin(Math.PI * u), beckon: -6 * Math.sin(Math.PI * 2 * u) });
        if (u === 1) hintAt.set(-1);
      }
    }
    if (wrongAt.get() >= 0 && now.get() - wrongAt.get() < 0.24 && !reduced.get()) {
      const shake = 6 * Math.sin((now.get() - wrongAt.get()) / 0.24 * Math.PI * 4);
      live.set({ ...live.get(), shake, twitch: shake });
    } else if (live.get().shake || live.get().twitch) live.set({ ...live.get(), shake: 0, twitch: 0 });
    if (autoPlay && !reduced.get() && !touching.get() && release.get() < 0 && !rulerOn.get() && now.get() - lastTouch.get() > 2) {
      if (returnAt.get() < 0) { returnAt.set(now.get()); returnStart.set(live.get()); }
      const u = seg(now.get() - returnAt.get(), 0, 0.65);
      const from = returnStart.get();
      // Return through the same mechanics to the first chart, then restart from its true resting frame.
      const rest = restFrame(from.index); rest.next = 0;
      live.set(from.index === 0 ? restFrame() : { ...rest, p: smooth(u), q: fingerSlats(smooth(u)), drum: smooth(u),
        head: mix(from.head, shortestTurn(from.head, 0), smooth(u)), tail: mix(from.tail, shortestTurn(from.tail, 0), smooth(u)),
        stretch: 0, beckon: 0 });
      if (u === 1) { T.set(0); auto.set(1); returnAt.set(-1); }
    }
  });

  useAnimatedReaction(() => {
    if (auto.get()) return -1;
    let n = 0;
    for (const q of F.get().q) if (q >= 0.5) n++;
    return n;
  }, (v, previous) => {
    if (v > 0 && v % 3 === 0 && v !== previous && now.get() - lastSelection.get() > 0.08) {
      lastSelection.set(now.get()); scheduleOnRN(buzz, 'selection');
    }
  });
  useAnimatedReaction(() => rulerOn.get() ? Math.round(rulerValue.get()) : null, (v, previous) => {
    if (v !== null && previous !== null && v !== previous && now.get() - lastHaptic.get() >= 0.1) { lastHaptic.set(now.get()); scheduleOnRN(buzz, 'selection'); }
  });

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __arc?: unknown };
    g.__arc = {
      at: (t: number) => { pinned.set(1); auto.set(1); T.set(t); },
      play: () => { auto.set(reduced.get() ? 0 : 1); pinned.set(0); }, rate: (r: number) => rate.set(r),
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), reduced: reduced.get(), touching: touching.get(), release: release.get(), ruler: rulerValue.get(), saved, frame: F.get() }),
      // Run on the UI thread like the real gestures, so each step reads the previous step's state.
      begin: () => scheduleOnUI(begin), drag: (x: number, y: number) => scheduleOnUI(move, x, y), end: (vx: number, vy: number) => scheduleOnUI(end, vx, vy),
      rulerBegin: () => scheduleOnUI(rulerBegin), rulerDrag: (dx: number) => scheduleOnUI(rulerMove, dx), rulerEnd: (vx: number) => scheduleOnUI(rulerEnd, vx), hint: () => scheduleOnUI(hint),
      reduced: (on: boolean) => { reduced.set(on ? 1 : 0); auto.set(0); live.set(restFrame()); pinned.set(0); },
    };
    return () => { delete g.__arc; };
  });

  const pan = Gesture.Pan().minDistance(2).onStart(begin).onUpdate(e => move(e.translationX / S, e.translationY / S)).onEnd((e, success) => end(e.velocityX / S, e.velocityY / S, success));
  const tap = Gesture.Tap().maxDuration(220).onEnd((_, success) => { if (success) hint(); });
  const rulerPan = Gesture.Pan().minDistance(2).onBegin(rulerBegin).onUpdate(e => rulerMove(e.translationX / S)).onFinalize((e, success) => rulerEnd(success ? e.velocityX / S : 0));
  const top = Math.max(0, insets.top - 62 * S);
  const designH = (size.h - top) / S;
  const deckScale = Math.min(1, (designH - 204) / 670);
  const deckTop = 178;
  const lowerY = deckTop + (774 - deckTop) * deckScale;
  const bookmarkStyle = useAnimatedStyle(() => ({ transform: [{ scale: bookmarkScale.get() }] }));
  const ticksStyle = useAnimatedStyle(() => ({ transform: [{ translateX: ((F.get().ticks % 12) + 12) % 12 }] }));
  const peekStyle = useAnimatedStyle(() => ({ transform: [{ translateY: Math.sin(Math.PI * F.get().drum) * 8 }] }));
  const bookmark = () => {
    begin(); touching.set(0);
    const i = F.get().drum < 0.5 ? F.get().index : F.get().next;
    setSaved(old => old.map((v, j) => j === i ? !v : v));
    bookmarkScale.set(0.86); bookmarkScale.set(withSpring(1, { damping: 22, stiffness: 360 }));
  };
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  // Nothing in the page may draw in a fallback face: wait for Inter Tight.
  if (!fontsOk) return <View onLayout={onLayout} style={styles.screen}><StatusBar style="dark" /></View>;
  return <View onLayout={onLayout} style={styles.screen}>
    <StatusBar style="dark" />
    <LinearGradient colors={[C.pageTop, C.page, C.page]} locations={[0, 0.3, 1]} style={StyleSheet.absoluteFill} />
    <View style={{ width: 402, height: designH, transformOrigin: 'top left', transform: [{ scale: S }], top }}>
      <Text style={[styles.heading, { top: 70 }]}>Popular</Text><Text style={[styles.heading, { top: 104, color: C.secondary }]}>Album</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="Switch chart" onPress={hint} style={styles.chevron}><Icon name="chevron" /></Pressable>
      <View style={{ position: 'absolute', left: 0, top: deckTop, width: 402, height: 578, transformOrigin: 'top center', transform: [{ scaleY: deckScale }] }}>
        <Animated.View style={[styles.peek3, peekStyle]} /><Animated.View style={[styles.peek2, peekStyle]} />
        <GestureDetector gesture={Gesture.Race(pan, tap)}><Animated.View accessibilityLabel="Music chart. Swipe in the arrow direction to turn the sign." style={styles.card}>
          <Album frame={F} />
          <View style={styles.panel}>{fontsOk && images && rankFont && <SignPanel frame={F} images={images} font={rankFont} />}</View>
        </Animated.View></GestureDetector>
        <Pressable accessibilityRole="button" accessibilityLabel="Save album" onPress={bookmark} style={styles.bookmark}><Animated.View style={[{ width: 24, height: 24 }, bookmarkStyle]}>{DECK.map((_, i) => <SavedMark key={i} index={i} frame={F} saved={saved[i]} />)}</Animated.View></Pressable>
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel="Today's chart" onPress={hint} style={[styles.round, { left: 11, top: lowerY }]}><Icon name="calendar" /></Pressable>
      <GestureDetector gesture={rulerPan}><Animated.View accessibilityLabel="Scrub chart ranks" style={[styles.ruler, { top: lowerY }]}>
        <Animated.View style={[StyleSheet.absoluteFill, ticksStyle]}>{Array.from({ length: 21 }, (_, i) => <View key={i} style={[styles.tick, { left: -12 + i * 12 }]}><View style={styles.tickInk} /><View style={styles.tickInk} /></View>)}</Animated.View>
        <Text style={styles.today}>Today</Text>
      </Animated.View></GestureDetector>
      <View pointerEvents="none" style={[styles.needle, { top: lowerY - 1 }]} />
      <Pressable accessibilityRole="button" accessibilityLabel="Saved albums" onPress={bookmark} style={[styles.round, { left: 330, top: lowerY }]}><Icon name="folder" /></Pressable>
    </View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.page, overflow: 'hidden' },
  heading: { position: 'absolute', left: 18, width: 300, fontFamily: FONT.bold, fontSize: 34, lineHeight: 34, letterSpacing: -0.714, color: C.ink, includeFontPadding: false },
  chevron: { position: 'absolute', left: 349, top: 68, width: 44, height: 44, justifyContent: 'center', alignItems: 'center' },
  peek3: { position: 'absolute', left: 81, top: 28, width: 233, height: 40, backgroundColor: '#F0F0EF', borderRadius: 20, borderCurve: 'continuous' },
  peek2: { position: 'absolute', left: 41, top: 36, width: 312, height: 40, backgroundColor: '#F4F4F3', borderRadius: 22, borderCurve: 'continuous' },
  card: { position: 'absolute', left: 8, top: 44, width: 386, height: 534, backgroundColor: C.card, borderRadius: 26, borderCurve: 'continuous', shadowColor: C.shadow, shadowOffset: { width: 0, height: 3 }, shadowOpacity: 0.1, shadowRadius: 12 },
  album: { position: 'absolute', left: 18, top: 11, width: 290, height: 56 },
  albumClip: { position: 'absolute', left: 0, right: 0, overflow: 'hidden' },
  albumName: { position: 'absolute', left: 0, top: 0, width: 290 },
  albumName2: { top: -1 },
  albumText: { fontFamily: FONT.bold, fontSize: 24, lineHeight: 26, letterSpacing: -0.408, color: C.ink, includeFontPadding: false },
  panel: { position: 'absolute', left: 7, top: 152, width: 375, height: 374, borderRadius: 18, overflow: 'hidden' },
  bookmark: { position: 'absolute', left: 321, top: 57, width: 64, height: 64, borderRadius: 32, borderWidth: 1, borderColor: '#E7E7E6', backgroundColor: '#FAFAF9', alignItems: 'center', justifyContent: 'center' },
  round: { position: 'absolute', width: 64, height: 64, borderRadius: 32, backgroundColor: C.control, justifyContent: 'center', alignItems: 'center' },
  ruler: { position: 'absolute', left: 94, width: 217, height: 64, borderRadius: 32, backgroundColor: C.control, overflow: 'hidden', justifyContent: 'center', alignItems: 'center' },
  tick: { position: 'absolute', top: 6, height: 52, justifyContent: 'space-between' },
  tickInk: { width: 2, height: 6, backgroundColor: C.tick },
  today: { fontFamily: FONT.bold, fontSize: 13, color: C.ink },
  needle: { position: 'absolute', left: 200, width: 2, height: 66, backgroundColor: C.needle },
});
