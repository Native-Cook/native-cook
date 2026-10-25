import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawHairline, drawTape, type Fonts, makeGeo, recordBackground, recordCase } from './tapeMeasureArt';
import {
  LOOP,
  MAX,
  MIN,
  PPCM,
  REST,
  WAVE_HZ,
  band,
  easeInOut,
  feetInches,
  loopValue,
  loopWobble,
  seg,
  unband,
  wobbleFromSpeed,
} from './tapeMeasureMotion';

/*
 Tape Measure Picker — "Fitline · Your height": a height picker that is a real tape measure. Pull
 the yellow tape out of its case and the value under the red hairline is your height; let go and
 it springs to the nearest centimetre; flick it left and it reels in to the minimum with a whip.
 The readout above is a mechanical counter whose units wheel turns with the tape.

 One Skia canvas (tapeMeasureArt.ts: background and case recorded once, the tape drawn per frame
 from the value, only its visible range). Value and wobble: tapeMeasureMotion.ts, analytic in
 autoPlay (LOOP = 8 s), a spring under the finger. React renders only when the whole centimetre
 changes (the "5 ft 10 in" line and the stats row are tiny components).

 Modes: 0 loop · 1 dragging · 2 released, springing to a centimetre · 3 easing back to 178 cm
 (0.9 s) two seconds after the last touch, then the loop restarts.
 */

type Props = {
  /** Plays the 8 s demo: pull out to 192 cm, reel in to 165 with a whip, back to 178. */
  autoPlay?: boolean;
};

const BACKGROUND = '#efebe4';
const INK = '#1d1b17';
const INK_SOFT = '#6e685e';
const DIGIT_SIZE = 84;
const DIGIT_LINE = 92;
const DIGIT_W = 52;
const WEIGHT_KG = 72;
const FLICK = -90;
const RETURN_AFTER = 2;
const RETURN_FOR = 0.9;
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];
const TICK_FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif-condensed' });

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

function snap() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** One wheel of the counter: 0–9 and a second 0, rolled by the value (odometer carry). */
function Wheel({ value, place }: { value: SharedValue<number>; place: number }) {
  const style = useAnimatedStyle(() => {
    const unit = Math.pow(10, place);
    const q = Math.max(0, value.get()) / unit;
    const base = Math.floor(q) % 10;
    const frac = q - Math.floor(q);
    const roll = place === 0 ? frac : Math.min(Math.max(frac * unit - (unit - 1), 0), 1);
    return { transform: [{ translateY: -(base + roll) * DIGIT_LINE }] };
  });
  return (
    <View style={styles.wheel}>
      <Animated.View style={style}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={styles.digit}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

/** Re-renders only when the whole centimetre changes. */
function useWholeCm(value: SharedValue<number>) {
  const [cm, setCm] = useState(REST);
  useAnimatedReaction(
    () => Math.round(Math.min(MAX, Math.max(MIN, value.get()))),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setCm, v);
    },
  );
  return cm;
}

function Imperial({ value }: { value: SharedValue<number> }) {
  const cm = useWholeCm(value);
  return <Text style={styles.imperial}>{feetInches(cm)}</Text>;
}

function Stats({ value }: { value: SharedValue<number> }) {
  const cm = useWholeCm(value);
  const bmi = WEIGHT_KG / ((cm / 100) * (cm / 100));
  const items = [
    ['Weight', `${WEIGHT_KG} kg`],
    ['Stride', `${Math.round(cm * 0.415)} cm`],
    ['BMI', bmi.toFixed(1)],
  ];
  return (
    <View style={styles.stats}>
      {items.map(([label, v], i) => (
        <View key={label} style={[styles.stat, i > 0 && styles.statDivider]}>
          <Text style={styles.statLabel}>{label}</Text>
          <Text style={styles.statValue}>{v}</Text>
        </View>
      ))}
    </View>
  );
}

export function TapeMeasurePicker({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height) : null), [size]);
  const fonts = useMemo<Fonts>(
    () => ({
      tick: matchFont({ fontFamily: TICK_FAMILY, fontSize: 17, fontWeight: '700' }),
      tickRed: matchFont({ fontFamily: TICK_FAMILY, fontSize: 19, fontWeight: '800' }),
      brand: matchFont({ fontFamily: TICK_FAMILY, fontSize: 15, fontWeight: '800' }),
      small: matchFont({ fontFamily: TICK_FAMILY, fontSize: 10, fontWeight: '600' }),
    }),
    [],
  );
  const art = useMemo(() => (g ? { bg: recordBackground(g), body: recordCase(g, fonts) } : null), [g, fonts]);

  const now = useSharedValue(-1);
  const loopStart = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 2);
  const value = useSharedValue(REST);
  const speed = useSharedValue(0);
  const amp = useSharedValue(0);
  const phase = useSharedValue(0);
  /** Spring after release: [target, ω, ζ, settled 0/1]. */
  const spring = useSharedValue<number[]>([REST, 9, 0.72, 1]);
  /** Drag: [start x, start raw value, last x, last time, velocity] or []. */
  const drag = useSharedValue<number[]>([]);
  const lastTouch = useSharedValue(0);
  const back = useSharedValue<number[]>([0, REST]);
  /** [last whole cm, time of the last tick] for the detent haptics. */
  const detent = useSharedValue<number[]>([REST, 0]);

  useFrameCallback((info) => {
    const t0 = info.timestamp / 1000;
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (now.get() < 0) loopStart.set(t0);
    now.set(t0);
    phase.set(((t0 - loopStart.get()) * WAVE_HZ * Math.PI * 2) % (Math.PI * 2 * WAVE_HZ * LOOP));
    const m = mode.get();
    if (m === 0) {
      const t = (t0 - loopStart.get()) % LOOP;
      const lv = loopValue(t);
      value.set(lv[0]);
      speed.set(lv[1]);
      amp.set(loopWobble(t));
      phase.set(((t * WAVE_HZ) % 1) * Math.PI * 2);
      return;
    }
    if (m === 1) {
      // A finger that holds still stops the wobble.
      speed.set(speed.get() * Math.exp(-dt / 0.05));
    } else if (m === 2) {
      const s = spring.get();
      if (s[3] === 0) {
        let v = value.get();
        let sp = speed.get();
        const sub = 4;
        const h = dt / sub;
        for (let k = 0; k < sub; k++) {
          sp += (-s[1] * s[1] * (v - s[0]) - 2 * s[2] * s[1] * sp) * h;
          v += sp * h;
        }
        if (Math.abs(v - s[0]) < 0.02 && Math.abs(sp) < 0.3) {
          v = s[0];
          sp = 0;
          spring.set([s[0], s[1], s[2], 1]);
          scheduleOnRN(snap);
        }
        value.set(v);
        speed.set(sp);
      } else if (auto && t0 - lastTouch.get() > RETURN_AFTER) {
        mode.set(3);
        back.set([t0, value.get()]);
      }
    } else if (m === 3) {
      const b = back.get();
      const k = easeInOut(seg(t0 - b[0], 0, RETURN_FOR));
      value.set(b[1] + (REST - b[1]) * k);
      speed.set(0);
      if (k >= 1) {
        mode.set(0);
        loopStart.set(t0);
      }
    }
    amp.set(reduced ? 0 : Math.max(amp.get() * Math.exp(-dt / 0.3), wobbleFromSpeed(Math.abs(speed.get()))));
    // Detents: a selection tick per whole centimetre under the finger, at most ~12 a second.
    if (m === 1 || m === 2) {
      const d = detent.get();
      const cm = Math.round(value.get());
      if (cm !== d[0] && t0 - d[1] > 0.08) {
        detent.set([cm, t0]);
        scheduleOnRN(tick);
      }
    }
  });

  const picture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (g && art) {
      canvas.drawPicture(art.bg);
      drawTape(canvas, g, fonts, value.get(), amp.get(), phase.get());
      canvas.drawPicture(art.body);
      drawHairline(canvas, g);
    }
    return rec.finishRecordingAsPicture();
  });

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          const t = now.get();
          mode.set(1);
          drag.set([e.x, unband(value.get()), e.x, t, 0]);
          detent.set([Math.round(value.get()), t]);
        })
        .onUpdate((e) => {
          const d = drag.get();
          if (d.length !== 5) return;
          const t = now.get();
          const raw = d[1] + (e.x - d[0]) / PPCM;
          const dtt = Math.max(1 / 240, t - d[3]);
          const vel = 0.6 * d[4] + (0.4 * ((e.x - d[2]) / PPCM)) / dtt;
          drag.set([d[0], d[1], e.x, t, vel]);
          const v = band(raw);
          speed.set((v - value.get()) / dtt);
          value.set(v);
        })
        .onFinalize(() => {
          const d = drag.get();
          if (d.length !== 5) return;
          drag.set([]);
          const v = value.get();
          const vel = d[4];
          lastTouch.set(now.get());
          // A fast flick to the left lets the spring reel the tape all the way in.
          const whip = vel < FLICK && !reduced;
          const target = whip ? MIN : Math.min(MAX, Math.max(MIN, Math.round(v + (reduced ? 0 : vel * 0.12))));
          speed.set(reduced ? 0 : vel);
          spring.set(whip ? [target, 11, 0.6, 0] : [target, reduced ? 14 : 9, reduced ? 1 : 0.72, 0]);
          mode.set(2);
        }),
    // Shared values are stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced],
  );

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const top = insets.top;
  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && art ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={picture} />
          </Canvas>

          <View style={[styles.nav, { top: top + 6 }]}>
            <Pressable accessibilityRole="button" accessibilityLabel="Back" hitSlop={8} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
              <SymbolView name="chevron.left" size={17} weight="semibold" tintColor={INK} fallback={<Text style={styles.backGlyph}>‹</Text>} />
            </Pressable>
            <View style={styles.steps}>
              {[0, 1, 2, 3, 4, 5].map((i) => (
                <View key={i} style={[styles.step, i < 3 && styles.stepOn]} />
              ))}
            </View>
            <Text style={styles.skip}>Skip</Text>
          </View>

          <View pointerEvents="none" style={[styles.header, { top: top + 62 }]}>
            <Text style={styles.kicker}>STEP 3 OF 6 · BODY</Text>
            <Text style={styles.title}>How tall are you?</Text>
            <Text style={styles.subtitle}>Pull the tape out to your height. It tunes your stride and calorie burn.</Text>
          </View>

          <View pointerEvents="none" style={[styles.readout, { top: Math.max(top + 174, g.caseY - DIGIT_LINE - 34) }]} accessible accessibilityLabel="Height">
            <View style={styles.counter}>
              <Wheel value={value} place={2} />
              <Wheel value={value} place={1} />
              <Wheel value={value} place={0} />
              <LinearGradient colors={[BACKGROUND, 'rgba(239,235,228,0)']} style={styles.fadeTop} />
              <LinearGradient colors={['rgba(239,235,228,0)', BACKGROUND]} style={styles.fadeBottom} />
            </View>
            <View style={styles.unitCol}>
              <Text style={styles.unit}>cm</Text>
              <Imperial value={value} />
            </View>
          </View>

          <GestureDetector gesture={pan}>
            <View
              style={[styles.tapeHit, { top: g.tapeY - 80, left: g.caseX }]}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel="Tape measure. Drag sideways to set your height."
            />
          </GestureDetector>

          <Text pointerEvents="none" style={[styles.hint, { top: g.tapeY + g.tapeH / 2 + 30 }]}>
            Drag the tape · flick left to reel it in
          </Text>

          <View pointerEvents="none" style={[styles.statsWrap, { top: g.tapeY + g.tapeH / 2 + 62 }]}>
            <Stats value={value} />
          </View>

          <Pressable
            accessibilityRole="button"
            style={({ pressed }) => [styles.cta, { bottom: Math.max(insets.bottom, 16) + 8 }, pressed && styles.pressed]}
          >
            <Text style={styles.ctaText}>Continue</Text>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  nav: { position: 'absolute', left: 16, right: 20, height: 44, flexDirection: 'row', alignItems: 'center', gap: 16 },
  back: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,253,248,0.8)',
    boxShadow: '0 1px 2px rgba(60,44,20,0.12), 0 6px 16px rgba(60,44,20,0.08)',
  },
  backGlyph: { fontSize: 26, lineHeight: 28, color: INK, fontWeight: '500' },
  pressed: { transform: [{ scale: 0.97 }], opacity: 0.9 },
  steps: { flex: 1, flexDirection: 'row', gap: 4 },
  step: { flex: 1, height: 4, borderRadius: 2, backgroundColor: 'rgba(29,27,23,0.1)' },
  stepOn: { backgroundColor: INK },
  skip: { color: INK_SOFT, fontSize: 15, fontWeight: '500' },
  header: { position: 'absolute', left: 24, right: 24, gap: 6 },
  kicker: { color: '#b07d06', fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.5 },
  subtitle: { color: INK_SOFT, fontSize: 15, lineHeight: 21, fontWeight: '400' },
  readout: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', alignItems: 'flex-end', gap: 10 },
  counter: { flexDirection: 'row', height: DIGIT_LINE },
  wheel: { width: DIGIT_W, height: DIGIT_LINE, overflow: 'hidden' },
  digit: {
    height: DIGIT_LINE,
    lineHeight: DIGIT_LINE,
    fontSize: DIGIT_SIZE,
    fontWeight: '700',
    color: INK,
    textAlign: 'center',
    fontVariant: ['tabular-nums'],
    letterSpacing: -2,
  },
  fadeTop: { position: 'absolute', left: 0, right: 0, top: 0, height: 16 },
  fadeBottom: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 16 },
  unitCol: { paddingBottom: 16, gap: 2 },
  unit: { color: INK, fontSize: 22, fontWeight: '600' },
  imperial: { color: INK_SOFT, fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  tapeHit: { position: 'absolute', right: 0, height: 160 },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: INK_SOFT, fontSize: 13, fontWeight: '500' },
  statsWrap: { position: 'absolute', left: 20, right: 20 },
  stats: {
    flexDirection: 'row',
    paddingVertical: 14,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,253,248,0.72)',
    boxShadow: '0 1px 2px rgba(60,44,20,0.1), 0 10px 28px rgba(60,44,20,0.08), inset 0 1px 0 rgba(255,255,255,0.8)',
  },
  stat: { flex: 1, alignItems: 'center', gap: 4 },
  statDivider: { borderLeftWidth: StyleSheet.hairlineWidth, borderLeftColor: 'rgba(29,27,23,0.14)' },
  statLabel: { color: INK_SOFT, fontSize: 12, fontWeight: '500' },
  statValue: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  cta: {
    position: 'absolute',
    left: 20,
    right: 20,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#22201b',
    boxShadow: '0 1px 2px rgba(20,14,6,0.3), 0 12px 24px rgba(20,14,6,0.18), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  ctaText: { color: '#f7f3ea', fontSize: 17, fontWeight: '600' },
});
