import { Canvas, Picture, type SkImage, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Geo, makeGeo, scribblePath, slot } from './crayonArt';
import { bakeCrayon, bakeDesk, bakeFront } from './crayonBake';
import { CRAYONS, LOOP, REST_COLOUR, easeInOut, loopColour, loopSwatch, pickPhase, seg } from './crayonMotion';
import { type Art, drawLoopFrame, drawPick } from './crayonScene';

/*
 Crayon Box Picker — "Doodlebug · Pick a colour". An open box of twelve wax crayons stands above a
 sheet of drawing paper. Pull a crayon up and it slides out against the box's friction, its name
 showing on the wrapper, and wobbles free while its neighbours lean into the gap; let go and it
 swings down onto the paper, scribbles a swatch in wax (the old swatch fades under it), becomes the
 current colour (its name rolls in), and goes home into its slot.

 Touch: drag a crayon upward (0.85 × the finger, rubber-banding past fully out); let go past 75 %
 to pick it, or earlier and it springs back (0.4 s, damping 0.7). Sideways speed tips it (wobble).
 A tick when it comes free, a light tap as it is picked, a tick when the name changes. With
 autoPlay, 2 s after a pick finishes the scene cross-fades to the loop's first frame (0.8 s).
 Reduced motion: nothing moves by itself; a pick changes the colour and swatch at once.

 One Canvas: desk, paper, box and the twelve crayons are baked once at 2×; per frame the picture
 places the crayon images, draws the swatches (wax strokes with the paper's tooth knocked out) and
 the box front.
 */

const AUTO = 0;
const USER = 1;
const RETURN = 2;
const STILL = 3;
const RETURN_FOR = 0.8;
const NAME_H = 28;
const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });

const INK = '#173A30';
const MUTED = '#4E6E66';

/** The user's pick times relative to its release r: fly, scribble, home, settle. */
function userPick(crayon: number, r: number) {
  'worklet';
  return { crayon, times: [r - 1, r, r + 0.35, r + 1.35, r + 1.85, r + 2.25] };
}

/** Which crayon a point (hero-root coordinates) is on, front row first; -1 for none. */
function crayonAt(g: Geo, x: number, y: number) {
  'worklet';
  for (let row = 1; row >= 0; row -= 1) {
    for (let k = 0; k < 6; k += 1) {
      const i = row === 0 ? k : 6 + k;
      const s = slot(g, i);
      const top = g.rimY - s[3] - 12;
      if (Math.abs(x - s[0]) < g.pitch * 0.5 && y > top && y < g.rimY + (row === 1 ? g.frontH * 0.6 : 0)) return i;
    }
  }
  return -1;
}

function NameRow({ index, colour }: { index: number; colour: SharedValue<number[]> }) {
  const style = useAnimatedStyle(() => {
    const c = colour.get();
    const roll = c[2];
    if (index === c[1]) return { opacity: roll, transform: [{ translateY: (1 - roll) * NAME_H }] };
    if (index === c[0] && roll < 1) return { opacity: 1 - roll, transform: [{ translateY: -roll * NAME_H }] };
    return { opacity: 0, transform: [{ translateY: NAME_H }] };
  });
  return (
    <Animated.View style={[styles.nameRow, style]}>
      <View style={[styles.dot, { backgroundColor: CRAYONS[index].hex }]} />
      <Text style={styles.name}>{CRAYONS[index].name}</Text>
    </Animated.View>
  );
}

export function CrayonBoxPicker({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [hero, setHero] = useState({ x: 0, y: 0, width: 0, height: 0 });

  const geo = useMemo(() => (size.width > 0 && hero.width > 0 ? makeGeo(size.width, size.height, hero) : null), [size, hero]);
  const path = useMemo(() => (geo ? scribblePath(geo) : { pts: [0, 0], len: [0] }), [geo]);
  const total = path.len[path.len.length - 1];
  const fonts = useMemo(
    () => ({
      brand: matchFont({ fontFamily: SANS, fontSize: 24, fontWeight: '800' }),
      small: matchFont({ fontFamily: SANS, fontSize: 9, fontWeight: '700' }),
      wrapper: matchFont({ fontFamily: SANS, fontSize: 8, fontWeight: '700' }),
    }),
    [],
  );
  const art = useSharedValue<Art | null>(null);
  useEffect(() => {
    if (!geo) return undefined;
    const desk = bakeDesk(geo, 2);
    const front = bakeFront(geo, 2, fonts.brand, fonts.small);
    const crayons: SkImage[] = [];
    for (let i = 0; i < CRAYONS.length; i += 1) {
      const img = bakeCrayon(geo, i, 2, fonts.wrapper);
      if (img) crayons.push(img);
    }
    if (desk && front && crayons.length === CRAYONS.length) art.set({ desk, front, crayons });
    return () => {
      art.set(null);
      desk?.dispose();
      front?.dispose();
      for (const c of crayons) c.dispose();
    };
  }, [geo, fonts, art]);

  const now = useSharedValue(0);
  const loopT = useSharedValue(0);
  const mode = useSharedValue(auto ? AUTO : STILL);
  const modeAt = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const touching = useSharedValue(0);
  const active = useSharedValue(-1);
  const pull = useSharedValue(0);
  const wobble = useSharedValue(0);
  const releaseAt = useSharedValue(-100);
  const freed = useSharedValue(0);
  // The chosen colour [from, to, since] and the swatch [old, new, since].
  const colourState = useSharedValue<number[]>([REST_COLOUR, REST_COLOUR, -100]);
  const swatchState = useSharedValue<number[]>([REST_COLOUR, REST_COLOUR, -100]);

  useFrameCallback((info) => {
    const dt = Math.min(0.05, (info.timeSincePreviousFrame ?? 16) / 1000);
    const t = now.get() + dt;
    now.set(t);
    const m = mode.get();
    if (m === AUTO) loopT.set((loopT.get() + dt) % LOOP);
    const r = releaseAt.get();
    if (r > 0 && t >= r + 2.25) {
      active.set(-1);
      releaseAt.set(-100);
      pull.set(0);
      lastTouch.set(t);
    }
    if (r < 0 && active.get() >= 0 && touching.get() === 0 && Math.abs(pull.get()) < 0.004) active.set(-1);
    wobble.set(wobble.get() * Math.exp(-dt * 5));
    if (m === USER && auto && touching.get() === 0 && active.get() < 0 && t - lastTouch.get() > 2) {
      mode.set(RETURN);
      modeAt.set(t);
    }
    if (m === RETURN && t - modeAt.get() >= RETURN_FOR) {
      mode.set(AUTO);
      loopT.set(0);
    }
  });

  const tick = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };
  const tickLater = (ms: number) => {
    setTimeout(() => tick(0), ms);
  };

  /** The loop hands over: the colour and swatch it shows become the user's. */
  function takeOver() {
    'worklet';
    const t = now.get();
    lastTouch.set(t);
    const m = mode.get();
    if (m === USER) return;
    let shown = REST_COLOUR;
    if (m === AUTO) {
      const c = loopColour(loopT.get());
      shown = c[2] >= 0.5 ? c[1] : c[0];
      const s = loopSwatch(loopT.get());
      swatchState.set([s[3] >= 0.5 ? s[2] : s[0], s[3] >= 0.5 ? s[2] : s[0], -100]);
    } else swatchState.set([REST_COLOUR, REST_COLOUR, -100]);
    colourState.set([shown, shown, -100]);
    active.set(-1);
    releaseAt.set(-100);
    pull.set(0);
    mode.set(USER);
  }

  const drag = Gesture.Pan()
    .minDistance(2)
    .onBegin((e) => {
      if (!geo) return;
      const r = releaseAt.get();
      if (r > 0) return;
      const i = crayonAt(geo, hero.x + e.x, hero.y + e.y);
      if (i < 0) return;
      takeOver();
      touching.set(1);
      active.set(i);
      freed.set(0);
    })
    .onChange((e) => {
      if (!geo || touching.get() === 0) return;
      const i = active.get();
      if (i < 0) return;
      const s = slot(geo, i);
      const out = geo.cl - s[3] + 6;
      // Friction inside the box, rubber-band once it is fully out.
      const raw = Math.max(0, -e.translationY * 0.85);
      const over = Math.max(0, raw - out);
      const shown = raw <= out ? raw : out + over / (1 + over / 60);
      pull.set(shown / out);
      wobble.set(Math.max(-1, Math.min(1, wobble.get() * 0.7 + e.velocityX * 0.0012)));
      if (freed.get() === 0 && raw >= out) {
        freed.set(1);
        scheduleOnRN(tick, 0);
      }
      lastTouch.set(now.get());
    })
    .onEnd(() => {
      const i = active.get();
      if (i < 0 || touching.get() === 0) return;
      const t = now.get();
      const cur = colourState.get();
      const shown = seg(t, cur[2], cur[2] + 0.35) >= 0.5 ? cur[1] : cur[0];
      if (pull.get() >= 0.75) {
        scheduleOnRN(tick, 1);
        if (reduced) {
          colourState.set([shown, i, t - 1]);
          swatchState.set([shown, i, t - 2]);
          pull.set(0);
          return;
        }
        // Finish coming out (0.12 s), then the pick runs from its release.
        const r = t + 0.12;
        pull.set(withTiming(1, { duration: 120 }));
        releaseAt.set(r);
        swatchState.set([swatchState.get()[1], i, r + 0.35]);
        colourState.set([shown, i, r + 1.35]);
        scheduleOnRN(tickLater, 1470);
      } else pull.set(withSpring(0, { duration: 400, dampingRatio: 0.7 }));
    })
    .onFinalize(() => {
      touching.set(0);
      lastTouch.set(now.get());
    });

  const colour = useDerivedValue(() => {
    const m = mode.get();
    if (m === AUTO) return loopColour(loopT.get());
    if (m === STILL) return [REST_COLOUR, REST_COLOUR, 1];
    const c = colourState.get();
    if (m === RETURN) {
      const k = easeInOut(seg(now.get(), modeAt.get(), modeAt.get() + RETURN_FOR));
      return c[1] === REST_COLOUR ? [c[1], c[1], 1] : [c[1], REST_COLOUR, k];
    }
    return [c[0], c[1], easeInOut(seg(now.get(), c[2], c[2] + 0.35))];
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    const a = art.get();
    if (!geo || !a) return recorder.finishRecordingAsPicture();
    const m = mode.get();
    if (m === AUTO || m === STILL) {
      drawLoopFrame(canvas, geo, a, path, m === AUTO ? loopT.get() : 0, 1);
      return recorder.finishRecordingAsPicture();
    }
    const t = now.get();
    const i = active.get();
    const r = releaseAt.get();
    const phase = r > 0 && t >= r ? pickPhase(userPick(i, r), t) : [1, pull.get(), wobble.get()];
    const sw = swatchState.get();
    const prog = seg(t, sw[2], sw[2] + 1.0);
    drawPick(canvas, geo, a, path, total, i, phase, [sw[0], 1 - prog, sw[1], prog], 1);
    if (m === RETURN) {
      const layer = Skia.Paint();
      layer.setAlphaf(easeInOut(seg(t, modeAt.get(), modeAt.get() + RETURN_FOR)));
      canvas.saveLayer(layer);
      drawLoopFrame(canvas, geo, a, path, 0, 1);
      canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  });

  const onRoot = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };
  const onHero = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    if (width !== hero.width || height !== hero.height || y !== hero.y) setHero({ x, y, width, height });
  };

  return (
    <View style={styles.root} onLayout={onRoot}>
      <Canvas style={StyleSheet.absoluteFill}>
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.column, { paddingTop: insets.top + 6, paddingBottom: insets.bottom + 12 }]}>
        <View style={styles.nav}>
          <View style={styles.navButton}>
            <SymbolView name="chevron.left" size={17} weight="semibold" tintColor={INK} fallback={<Text style={styles.navGlyph}>‹</Text>} />
          </View>
          <Text style={styles.navTitle}>Doodlebug</Text>
          <View style={styles.page}>
            <Text style={styles.pageText}>Page 3</Text>
          </View>
        </View>
        <View style={styles.titles}>
          <Text style={styles.title}>Pick a colour</Text>
          <Text style={styles.subtitle}>Pull a crayon out of the box</Text>
        </View>

        <GestureDetector gesture={drag}>
          <View style={styles.hero} onLayout={onHero} accessible accessibilityLabel="Box of twelve crayons. Drag one up to pick its colour." />
        </GestureDetector>

        <View style={styles.info}>
          <View style={{ gap: 2 }}>
            <Text style={styles.small}>Colour</Text>
            <View style={styles.nameBox}>
              {CRAYONS.map((c, i) => (
                <NameRow key={c.name} index={i} colour={colour} />
              ))}
            </View>
          </View>
          <View style={{ alignItems: 'flex-end', gap: 2 }}>
            <Text style={styles.small}>Box</Text>
            <Text style={styles.meta}>12 wax crayons</Text>
          </View>
        </View>
        <Pressable style={({ pressed }) => [styles.cta, { transform: [{ scale: pressed ? 0.97 : 1 }] }]} accessibilityRole="button">
          <Text style={styles.ctaText}>Start drawing</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#A9C9C2', overflow: 'hidden' },
  column: { flex: 1, paddingHorizontal: 20 },
  nav: { height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(250,253,251,0.78)',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(20,50,40,0.18), 0 6px 16px rgba(20,50,40,0.1)',
  },
  navGlyph: { fontSize: 26, color: INK, marginTop: -3 },
  navTitle: { fontSize: 15, fontWeight: '700', color: INK },
  page: { height: 28, paddingHorizontal: 12, borderRadius: 14, backgroundColor: 'rgba(250,253,251,0.6)', justifyContent: 'center' },
  pageText: { fontSize: 12, fontWeight: '600', color: MUTED, fontVariant: ['tabular-nums'] },
  titles: { marginTop: 12, gap: 4 },
  title: { fontSize: 30, lineHeight: 36, fontWeight: '800', color: INK, letterSpacing: -0.4 },
  subtitle: { fontSize: 15, lineHeight: 20, color: MUTED },
  hero: { flex: 1, minHeight: 300 },
  info: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: 8 },
  small: { fontSize: 12, fontWeight: '600', color: MUTED, letterSpacing: 0.4, textTransform: 'uppercase' },
  nameBox: { height: NAME_H, width: 180, overflow: 'hidden' },
  nameRow: { position: 'absolute', left: 0, top: 0, height: NAME_H, flexDirection: 'row', alignItems: 'center', gap: 8 },
  dot: {
    width: 14,
    height: 14,
    borderRadius: 7,
    boxShadow: '0 1px 2px rgba(20,40,30,0.3), inset 0 -2px 3px rgba(0,0,0,0.2), inset 0 1px 2px rgba(255,255,255,0.35)',
  },
  name: { fontSize: 22, lineHeight: NAME_H, fontWeight: '800', color: INK },
  meta: { fontSize: 15, fontWeight: '600', color: INK, lineHeight: NAME_H },
  cta: {
    marginTop: 16,
    height: 56,
    borderRadius: 28,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 2px 4px rgba(23,58,48,0.25), 0 12px 24px rgba(23,58,48,0.2), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  ctaText: { fontSize: 17, fontWeight: '600', color: '#F3FAF7' },
});
