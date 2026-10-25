import { Blur, Canvas, Group, Image, Path, Shader, Skia, type SkPoint, Vertices } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { addStroke, bakePaper, buildSplash, type Layout, makeLayout, type Mesh } from './brushInkArt';
import { drops, easeOut, LOOP, loopPose, MAX_STROKES, paintedPose, type Pose } from './brushInkMotion';
import { INK_SKSL } from './brushInkShader';

/*
 Brush Ink Splash — the launch screen of "Sumi", a journaling app. On warm rice paper a loaded
 brush lands with a blot and paints an ensō clockwise, thinning and running dry into bristle
 streaks, then writes the wordmark "Sumi" stroke by stroke; the ink bleeds into the fibres at every
 edge, a few drops lie where the brush landed and flicked off. Then the sheet slides up to reveal
 the journal's home screen, and a fresh sheet fades back in. The loop is in brushInkMotion.ts
 (LOOP = 8 s).

 The strokes are one triangle mesh whose texture coordinates are stroke coordinates (distance
 along the paint clock, position across the stroke), shaded by INK_SKSL: so the wet tip, ragged
 edges, dry streaks, pooled rims and bleeding all follow the brush, not the screen.

 Touch: draw with a finger and the same brush paints under it, thinner the faster it moves,
 drying out along a long stroke; lift and the ink bleeds (0.6 s). A new touch starts a new stroke.
 2 s after the last touch the ink fades (0.9 s) and the loop starts again. Reduced motion: the
 painted splash, still; drawing still works, and the bleed appears at once.
 */

const INK = '#1f1d22';
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const MUTE = '#7e766b';
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const IDLE = 2;
const RESET = 0.9;
// The finger's brush: half-width from W_MAX (slow) to W_MIN (fast), easing toward it; at most
// MAX_POINTS points, one every STEP pt; dry over DRY_LENGTH pt.
const W_MAX = 9;
const W_MIN = 3.2;
const MAX_POINTS = 220;
const STEP = 2.5;
const DRY_LENGTH = 700;

const inkEffect = Skia.RuntimeEffect.Make(INK_SKSL);
const EMPTY: Mesh = { vertices: [{ x: -9, y: -9 }, { x: -8, y: -9 }, { x: -9, y: -8 }], textures: [{ x: 0, y: 0 }, { x: 0, y: 0 }, { x: 0, y: 0 }], indices: [0, 1, 2] };
const FAR = new Array(MAX_STROKES - 1).fill(1e6);
const ONES = new Array(MAX_STROKES - 1).fill(1);
const ZEROS = new Array(MAX_STROKES - 1).fill(0);

type Props = {
  /** Paints the mark and the wordmark, reveals the home screen and starts over, on a loop of LOOP s. */
  autoPlay?: boolean;
};

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function BrushInkSplash({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  const layout = useMemo(
    () => (size ? makeLayout(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  return (
    <View style={styles.root} onLayout={onLayout}>
      <Home top={insets.top} bottom={insets.bottom} />
      {layout ? <Scene layout={layout} autoPlay={autoPlay && !reduced} reduced={reduced} /> : null}
    </View>
  );
}

const PAGES = [
  ['Rain on the tram window', 'Thu', '312'],
  ['Tea with my mother', 'Tue', '540'],
  ['First frost at the allotment', 'Sun', '198'],
  ['A walk to the harbour', 'Fri', '421'],
];

/** The journal's home screen, under the splash. */
function Home({ top, bottom }: { top: number; bottom: number }) {
  return (
    <View style={[styles.home, { paddingTop: top + 14, paddingBottom: Math.max(bottom, 16) + 8 }]}>
      <View style={styles.homeHeader}>
        <Svg width={30} height={30} viewBox="0 0 30 30">
          <SvgPath d="M8 22.5A10 10 0 1 1 22.6 22" stroke={INK} strokeWidth={3.2} strokeLinecap="round" fill="none" />
        </Svg>
        <Text style={styles.homeTitle}>Sumi</Text>
        <View style={{ flex: 1 }} />
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>AK</Text>
        </View>
      </View>
      <Text style={styles.date}>Saturday, 25 October</Text>
      <View style={styles.prompt}>
        <Text style={styles.promptKicker}>{"TODAY'S PAGE"}</Text>
        <Text style={styles.promptText}>What did the light look like this morning?</Text>
        <View style={styles.promptButton}>
          <Text style={styles.promptButtonText}>Start writing</Text>
        </View>
      </View>
      <Text style={styles.section}>Recent pages</Text>
      {PAGES.map(([title, day, words]) => (
        <View key={title} style={styles.page}>
          <Text style={styles.pageTitle}>{title}</Text>
          <Text style={styles.pageMeta}>
            {day} · {words} words
          </Text>
        </View>
      ))}
      <View style={{ flex: 1 }} />
      <View style={styles.streak}>
        <Text style={styles.streakText}>12-day streak</Text>
        <View style={styles.dots}>
          {[0, 1, 2, 3, 4, 5, 6].map((i) => (
            <Svg key={i} width={12} height={12}>
              <SvgCircle cx={6} cy={6} r={5} fill={i < 6 ? INK : 'none'} stroke={INK} strokeWidth={1.2} />
            </Svg>
          ))}
        </View>
      </View>
    </View>
  );
}

function Scene({ layout, autoPlay, reduced }: { layout: Layout; autoPlay: boolean; reduced: boolean }) {
  const scale = Math.min(PixelRatio.get(), 3);
  const paper = useMemo(() => bakePaper(layout, Math.min(scale, 2)), [layout, scale]);
  const splash = useMemo(() => buildSplash(layout), [layout]);
  const { plan, mesh, ensoStart, ensoEnd } = splash;
  const landing = useMemo(() => drops(0, 0), []);
  const flick = useMemo(() => drops(1, ensoEnd.angle), [ensoEnd.angle]);

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const user = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const resetAt = useSharedValue(-1);
  const drawing = useSharedValue(0);
  // The splash under a finger (frozen where the loop was, fading away).
  const uHead = useSharedValue(0);
  const uBleed = useSharedValue(0);
  const uInk = useSharedValue(0);
  const uSlide = useSharedValue(0);
  const uPaper = useSharedValue(1);
  const uLanded = useSharedValue(-1);
  const uFlicked = useSharedValue(-1);
  // The finger's stroke.
  const xs = useSharedValue<number[]>([]);
  const ys = useSharedValue<number[]>([]);
  const hws = useSharedValue<number[]>([]);
  const lastMove = useSharedValue(0);
  const userMesh = useSharedValue<Mesh>(EMPTY);
  const strokeBleed = useSharedValue(0);
  const strokeAlpha = useSharedValue(0);

  function touched() {
    'worklet';
    lastTouch.set(now.get());
    resetAt.set(-1);
    if (user.get() === 1) return;
    const p = reduced ? paintedPose(plan) : loopPose(clock.get(), plan);
    uHead.set(p.head);
    uBleed.set(p.bleed);
    uInk.set(p.ink);
    uSlide.set(p.slide);
    uPaper.set(p.paper);
    uLanded.set(p.landed >= 0 ? now.get() - p.landed : -1);
    uFlicked.set(p.flicked >= 0 ? now.get() - p.flicked : -1);
    user.set(1);
    if (reduced) return;
    // Clear the sheet for the finger: the splash fades, a slid sheet comes back.
    const quick = { duration: 300, easing: EASE_OUT };
    uInk.set(withTiming(0, quick));
    uSlide.set(withTiming(0, quick));
    uPaper.set(withTiming(1, quick));
  }

  function rebuild() {
    'worklet';
    const m: Mesh = { vertices: [], textures: [], indices: [] };
    const px = xs.get();
    if (px.length < 2) {
      userMesh.set(EMPTY);
      return;
    }
    addStroke(m, { xs: px, ys: ys.get(), hw: hws.get(), dry: 0.8 }, 0, 0, 1.7);
    userMesh.set(m);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    const t = now.get() + dt;
    now.set(t);
    if (user.get() === 0) {
      if (autoPlay) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    if (reduced || drawing.get() === 1) return;
    if (resetAt.get() < 0 && t - lastTouch.get() > IDLE) {
      const timing = { duration: RESET * 1000, easing: EASE_IN_OUT };
      strokeAlpha.set(withTiming(0, timing));
      uInk.set(withTiming(0, timing));
      resetAt.set(t + RESET + 0.05);
    } else if (resetAt.get() > 0 && t >= resetAt.get()) {
      resetAt.set(-1);
      xs.set([]);
      ys.set([]);
      hws.set([]);
      userMesh.set(EMPTY);
      clock.set(0);
      user.set(0);
    }
  });

  const draw = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      touched();
      drawing.set(1);
      xs.set([e.x]);
      ys.set([e.y]);
      hws.set([W_MAX * 1.15]);
      lastMove.set(now.get());
      strokeBleed.set(0);
      strokeAlpha.set(1);
      rebuild();
    })
    .onUpdate((e) => {
      lastTouch.set(now.get());
      const px = xs.get();
      const py = ys.get();
      const n = px.length;
      if (n >= MAX_POINTS) return;
      const d = Math.hypot(e.x - px[n - 1], e.y - py[n - 1]);
      if (d < STEP) return;
      // Faster strokes are thinner; the width eases toward its target so it never jumps.
      const speed = Math.hypot(e.velocityX, e.velocityY);
      const target = W_MAX - (W_MAX - W_MIN) * Math.min(1, speed / 1600);
      const hw = hws.get();
      const w = hw[n - 1] + (target - hw[n - 1]) * 0.25;
      xs.set([...px, e.x]);
      ys.set([...py, e.y]);
      hws.set([...hw, w]);
      rebuild();
    })
    .onFinalize(() => {
      drawing.set(0);
      lastTouch.set(now.get());
      strokeBleed.set(reduced ? 1 : withTiming(1, { duration: 600, easing: EASE_OUT }));
      if (xs.get().length > 8) scheduleOnRN(tick);
    });

  const pose = useDerivedValue<Pose>(() => {
    if (reduced && user.get() === 0) return paintedPose(plan);
    if (user.get() === 0) return loopPose(clock.get(), plan);
    const landed = uLanded.get();
    const flicked = uFlicked.get();
    return {
      head: uHead.get(),
      bleed: uBleed.get(),
      ink: uInk.get(),
      slide: uSlide.get(),
      paper: uPaper.get(),
      landed: landed < 0 ? -1 : now.get() - landed,
      flicked: flicked < 0 ? -1 : now.get() - flicked,
    };
  });

  const splashUniforms = useDerivedValue(() => {
    const p = pose.get();
    return {
      head: p.head,
      bleed: p.bleed,
      alpha: p.ink,
      starts: plan.starts,
      lengths: plan.lengths,
      dry: plan.dry,
      caps: plan.caps,
      ends: plan.ends,
    };
  });
  const strokeUniforms = useDerivedValue(() => {
    const hw = hws.get();
    return {
      head: 1e5,
      bleed: strokeBleed.get(),
      alpha: strokeAlpha.get(),
      starts: [0, ...FAR],
      lengths: [DRY_LENGTH, ...ONES],
      dry: [0.8, ...ZEROS],
      caps: [(hw[0] ?? W_MAX) * 1.2, ...ONES],
      ends: [(hw[hw.length - 1] ?? W_MAX) * 1.2, ...ONES],
    };
  });
  const userVerts = useDerivedValue<SkPoint[]>(() => userMesh.get().vertices);
  const userTex = useDerivedValue<SkPoint[]>(() => userMesh.get().textures);
  const userIdx = useDerivedValue<number[]>(() => userMesh.get().indices);

  // Drops where the brush landed and where it flicked off the end of the circle.
  const dropPath = useDerivedValue(() => {
    const p = pose.get();
    const b = Skia.PathBuilder.Make();
    if (p.landed >= 0) {
      const g = 0.6 + 0.4 * easeOut(p.landed / 0.15);
      for (const [x, y, r] of landing) b.addCircle(ensoStart.x + x, ensoStart.y + y, r * g);
    }
    if (p.flicked >= 0) {
      for (let i = 0; i < flick.length; i += 1) {
        const at = p.flicked - i * 0.04;
        if (at < 0) continue;
        const g = 0.6 + 0.4 * easeOut(at / 0.12);
        b.addCircle(ensoEnd.x + flick[i][0], ensoEnd.y + flick[i][1], flick[i][2] * g);
      }
    }
    return b.build();
  });
  const dropAlpha = useDerivedValue(() => pose.get().ink * 0.9);
  const dropBleed = useDerivedValue(() => pose.get().ink * pose.get().bleed * 0.35);

  const sheet = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.paper, transform: [{ translateY: -p.slide * layout.height }] };
  });
  const tagline = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.ink * p.bleed, transform: [{ translateY: (1 - p.bleed) * 6 }] };
  });

  return (
    <Animated.View style={[StyleSheet.absoluteFill, sheet]}>
      <GestureDetector gesture={draw}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Sumi. Draw on the paper with the brush.">
          <Canvas style={StyleSheet.absoluteFill}>
            {paper ? <Image image={paper} x={0} y={0} width={layout.width} height={layout.height} fit="fill" /> : null}
            {inkEffect ? (
              <>
                <Vertices vertices={mesh.vertices} textures={mesh.textures} indices={mesh.indices} mode="triangles">
                  <Shader source={inkEffect} uniforms={splashUniforms} />
                </Vertices>
                <Group>
                  <Path path={dropPath} color={INK} opacity={dropBleed}>
                    <Blur blur={2.5} />
                  </Path>
                  <Path path={dropPath} color={INK} opacity={dropAlpha} />
                </Group>
                <Vertices vertices={userVerts} textures={userTex} indices={userIdx} mode="triangles">
                  <Shader source={inkEffect} uniforms={strokeUniforms} />
                </Vertices>
              </>
            ) : null}
          </Canvas>
        </View>
      </GestureDetector>
      <Animated.View pointerEvents="none" style={[styles.tagline, { top: layout.taglineTop }, tagline]}>
        <Text style={styles.taglineText}>A page a day, in ink.</Text>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.footer, { top: layout.footerTop }, tagline]}>
        <Text style={styles.footerText}>Syncing 214 pages</Text>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#f3eee4' },
  home: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, paddingHorizontal: 24, backgroundColor: '#f3eee4' },
  homeHeader: { flexDirection: 'row', alignItems: 'center', gap: 10, height: 44 },
  homeTitle: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.4 },
  avatar: { width: 36, height: 36, borderRadius: 18, backgroundColor: '#d9cdb8', alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: INK, fontSize: 13, fontWeight: '700' },
  date: { color: MUTE, fontSize: 15, fontWeight: '500', marginTop: 4, marginBottom: 20 },
  prompt: {
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: '#fbf8f1',
    padding: 20,
    gap: 10,
    boxShadow: '0 1px 2px rgba(60,45,25,0.12), 0 10px 24px rgba(60,45,25,0.08)',
  },
  promptKicker: { color: '#a1533a', fontSize: 11, fontWeight: '700', letterSpacing: 1.8 },
  promptText: { color: INK, fontSize: 21, fontWeight: '600', lineHeight: 27, fontFamily: SERIF },
  promptButton: { alignSelf: 'flex-start', height: 40, paddingHorizontal: 18, borderRadius: 20, backgroundColor: INK, justifyContent: 'center', marginTop: 4 },
  promptButtonText: { color: '#f6f1e7', fontSize: 15, fontWeight: '600' },
  section: { color: INK, fontSize: 17, fontWeight: '700', marginTop: 28, marginBottom: 6 },
  page: { paddingVertical: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: 'rgba(31,29,34,0.15)', gap: 3 },
  pageTitle: { color: INK, fontSize: 16, fontWeight: '600' },
  pageMeta: { color: MUTE, fontSize: 13, fontVariant: ['tabular-nums'] },
  streak: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingTop: 12 },
  streakText: { color: INK, fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] },
  dots: { flexDirection: 'row', gap: 6 },
  tagline: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  taglineText: { color: '#5d564f', fontSize: 17, fontStyle: 'italic', fontFamily: SERIF },
  footer: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  footerText: { color: MUTE, fontSize: 12, fontWeight: '600', letterSpacing: 1.2, fontVariant: ['tabular-nums'] },
});
