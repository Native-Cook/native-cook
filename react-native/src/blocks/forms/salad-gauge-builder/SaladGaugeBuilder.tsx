import { Canvas, PaintStyle, Picture, Skia, StrokeCap, TileMode, useFont, useImage, type SkCanvas, type SkFont, type SkImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

// The bowl builder of an invented lunch app, Bowlful: an empty ceramic bowl seen from above on a
// lilac table sits inside a fuel gauge running from E to F. Each ingredient you add drops into
// its place in the bowl and the needle swings on towards F with a small overshoot, the calorie
// count rolling up underneath; when the bowl is full the gauge's F lights up. Tap the chips to
// add or take out ingredients.
//
// Artwork: assets/bowl.png and five ingredient cut-outs (generated, prompts in assets/PROMPTS.md).
// Layout (393 x 852 pt): 34 pt top row; title Archivo Expanded Black 34 pt; bowl 270 pt square
// centred at 47 % of the height; gauge ticks from r 150 to r 168 between 195 deg (E) and 345 deg
// (F, screen angles); kcal Archivo Expanded Black 30 pt under the bowl; five chips and a 58 pt
// button at the bottom.
//
// autoPlay timeline (LOOP = 8 s):
//   0.0-0.8  rest: empty bowl, needle on E
//   0.8, 1.5, 2.2, 2.9, 3.6  an ingredient drops in (0.45 s, ease-out back from 1.35x and 0 alpha);
//            each moves the needle a fifth of the way with a damped spring (1 - e^-5x cos 9x)
//   4.1-5.9  full: F glows, the button says "Order bowl"
//   5.9-7.0  the ingredients lift out (scale 1 -> 1.15, alpha 1 -> 0, 0.07 s apart), the needle
//            eases back to E (ease-in-out cubic)
//   7.0-8.0  rest, identical to t = 0
// Tapping a chip toggles that ingredient (light haptic); 2 s after the last tap the bowl empties
// over 0.6 s and the loop starts again.

/** Seconds. */
export const LOOP = 8;
const BOWL = 270;
const R_IN = 150;
const R_OUT = 168;
const E_ANGLE = (195 * Math.PI) / 180;
const F_ANGLE = (345 * Math.PI) / 180;
const TICKS = 11;
const HOLD = 2;
const EMPTY = 0.6;

type Item = { name: string; kcal: number; angle: number; r: number; size: number; at: number };
const ITEMS: Item[] = [
  { name: 'Tomato', kcal: 30, angle: 180, r: 52, size: 112, at: 0.8 },
  { name: 'Salmon', kcal: 210, angle: 270, r: 54, size: 112, at: 1.5 },
  { name: 'Greens', kcal: 20, angle: 0, r: 52, size: 122, at: 2.2 },
  { name: 'Egg', kcal: 80, angle: 90, r: 54, size: 100, at: 2.9 },
  { name: 'Avocado', kcal: 160, angle: 0, r: 0, size: 96, at: 3.6 },
];
const TOTAL = ITEMS.reduce((s, it) => s + it.kcal, 0);

const ARCHIVO = require('./assets/ArchivoExpanded-Black.ttf');
const BLACK = 'SaladGaugeBuilder-ArchivoExpandedBlack';
const INK = '#2B1838';
const GREEN = '#33C27A';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function easeOutBack(x: number) {
  'worklet';
  const k = 1.7;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
/** A damped step from 0 to 1, `x` seconds after it started. */
function springStep(x: number) {
  'worklet';
  if (x <= 0) return 0;
  return 1 - Math.exp(-5 * x) * Math.cos(9 * x);
}

/** Per-ingredient amounts (> 0 dropping in, < 0 lifting out) and the needle, `t` s into the loop. */
function bowlAt(t: number) {
  'worklet';
  const amounts: number[] = [];
  let needle = 0;
  for (let i = 0; i < ITEMS.length; i++) {
    const out = seg(t, 5.9 + i * 0.07, 6.5 + i * 0.07);
    amounts.push(out > 0 ? -(1 - out) : seg(t, ITEMS[i].at, ITEMS[i].at + 0.45));
    needle += springStep(t - ITEMS[i].at) / ITEMS.length;
  }
  needle -= easeInOutCubic(seg(t, 5.9, 7.0));
  return { amounts, needle };
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(clamp01(alpha));
  return p;
}

function drawScene(c: SkCanvas, amounts: number[], needle: number, cx: number, cy: number, bowl: SkImage | null, foods: (SkImage | null)[], font: SkFont) {
  'worklet';
  // Gauge ticks.
  for (let k = 0; k < TICKS; k++) {
    const a = E_ANGLE + ((F_ANGLE - E_ANGLE) * k) / (TICKS - 1);
    const long = k % 5 === 0;
    const p = paint(k === 0 ? '#FF4D6D' : '#FFFFFF', 1);
    p.setStrokeWidth(long ? 7 : 5);
    p.setStrokeCap(StrokeCap.Round);
    const r0 = long ? R_IN - 6 : R_IN;
    c.drawLine(cx + Math.cos(a) * r0, cy + Math.sin(a) * r0, cx + Math.cos(a) * R_OUT, cy + Math.sin(a) * R_OUT, p);
  }
  const full = clamp01((needle - 0.9) / 0.1);
  const labelE = paint('#FFFFFF', 1);
  const labelF = paint(full > 0.5 ? GREEN : '#FFFFFF', 1);
  c.save();
  c.translate(cx + Math.cos(E_ANGLE) * (R_OUT - 6) - 8, cy + Math.sin(E_ANGLE) * (R_OUT - 6) + 34);
  c.scale(0.2, 0.2);
  c.drawText('E', 0, 0, labelE, font);
  c.restore();
  c.save();
  c.translate(cx + Math.cos(F_ANGLE) * (R_OUT - 6) - 6, cy + Math.sin(F_ANGLE) * (R_OUT - 6) + 34);
  c.scale(0.2, 0.2);
  c.drawText('F', 0, 0, labelF, font);
  c.restore();
  if (full > 0) {
    const glow = paint(GREEN, 0.45 * full);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 14, true));
    c.drawCircle(cx + Math.cos(F_ANGLE) * R_OUT, cy + Math.sin(F_ANGLE) * R_OUT, 18, glow);
  }

  // Bowl shadow and bowl.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setShader(Skia.Shader.MakeRadialGradient({ x: cx - 26, y: cy + 30 }, BOWL * 0.56, [Skia.Color('rgba(90,40,110,0.35)'), Skia.Color('rgba(90,40,110,0)')], [0.62, 1], TileMode.Clamp));
  c.drawCircle(cx - 26, cy + 30, BOWL * 0.56, shadow);
  if (bowl) c.drawImageRect(bowl, Skia.XYWHRect(0, 0, bowl.width(), bowl.height()), Skia.XYWHRect(cx - BOWL / 2, cy - BOWL / 2, BOWL, BOWL), paint('#000000', 1));

  // Ingredients.
  for (let i = 0; i < ITEMS.length; i++) {
    const a = amounts[i];
    const img = foods[i];
    if (a === 0 || !img) continue;
    const it = ITEMS[i];
    const x = cx + Math.cos((it.angle * Math.PI) / 180) * it.r;
    const y = cy + Math.sin((it.angle * Math.PI) / 180) * it.r;
    let s = 1;
    let alpha = 1;
    if (a > 0) {
      s = 1 + 0.35 * (1 - easeOutBack(a));
      alpha = clamp01(a * 3);
    } else {
      s = 1 + 0.15 * (1 + a);
      alpha = -a;
    }
    const sz = it.size * s;
    c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(x - sz / 2, y - sz / 2, sz, sz), paint('#000000', alpha));
  }

  // Needle: a green bar riding the ticks.
  const na = E_ANGLE + (F_ANGLE - E_ANGLE) * Math.max(-0.03, Math.min(1.04, needle));
  const np = paint(GREEN, 1);
  np.setStrokeWidth(5);
  np.setStrokeCap(StrokeCap.Round);
  c.drawLine(cx + Math.cos(na) * (R_IN - 22), cy + Math.sin(na) * (R_IN - 22), cx + Math.cos(na) * (R_OUT + 8), cy + Math.sin(na) * (R_OUT + 8), np);
  const hub = paint(GREEN, 1);
  hub.setStyle(PaintStyle.Fill);
  c.drawCircle(cx + Math.cos(na) * (R_IN - 22), cy + Math.sin(na) * (R_IN - 22), 4, hub);

  // Calories under the bowl.
  let kcal = 0;
  for (let i = 0; i < ITEMS.length; i++) kcal += ITEMS[i].kcal * (amounts[i] > 0 ? clamp01(amounts[i] * 2) : -amounts[i]);
  const label = `${Math.round(kcal)} kcal`;
  const size = 30;
  const w = font.measureText(label).width * (size / 100);
  c.save();
  c.translate(cx - w / 2, cy + BOWL / 2 + 62);
  c.scale(size / 100, size / 100);
  c.drawText(label, 0, 0, paint(INK, 1), font);
  c.restore();
}

function Chip({ i, amounts, onPress }: { i: number; amounts: SharedValue<number[]>; onPress: (i: number) => void }) {
  const style = useAnimatedStyle(() => {
    const a = amounts.get()[i];
    const on = a > 0 ? clamp01(a * 2) : 0;
    return { backgroundColor: `rgba(43,24,56,${0.06 + 0.94 * on})` };
  });
  const textStyle = useAnimatedStyle(() => {
    const a = amounts.get()[i];
    return { color: a > 0.25 ? '#FFFFFF' : INK };
  });
  return (
    <Pressable onPress={() => onPress(i)} accessibilityRole="button" accessibilityLabel={`Toggle ${ITEMS[i].name}`}>
      <Animated.View style={[styles.chip, style]}>
        <Animated.Text style={[styles.chipText, textStyle]}>{ITEMS[i].name}</Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

type Props = {
  brand?: string;
  title?: string;
  /** Fills and empties the bowl by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function SaladGaugeBuilder({ brand = 'Bowlful', title = 'Fill up\nyour bowl', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK]: ARCHIVO });
  const font = useFont(ARCHIVO, 100);
  const bowl = useImage(require('./assets/bowl.png'));
  const f0 = useImage(require('./assets/tomato.png'));
  const f1 = useImage(require('./assets/salmon.png'));
  const f2 = useImage(require('./assets/greens.png'));
  const f3 = useImage(require('./assets/egg.png'));
  const f4 = useImage(require('./assets/avocado.png'));
  const foods = useMemo(() => [f0, f1, f2, f3, f4], [f0, f1, f2, f3, f4]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.47;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  // Reduced motion shows the full bowl.
  const amounts = useSharedValue<number[]>(ITEMS.map(() => (reduced ? 1 : 0)));
  const needle = useSharedValue(reduced ? 1 : 0);
  const manual = useSharedValue(false);
  const on = useSharedValue<number[]>(ITEMS.map(() => 0));
  const changed = useSharedValue<number[]>(ITEMS.map(() => -10));
  const lastTap = useSharedValue(-10);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (manual.get()) {
      const since = now - lastTap.get();
      const drain = clamp01((since - HOLD) / EMPTY);
      const next: number[] = [];
      let target = 0;
      for (let i = 0; i < ITEMS.length; i++) {
        const x = now - changed.get()[i];
        let a = on.get()[i] ? clamp01(x / 0.45) : -(1 - clamp01(x / 0.45));
        if (a === 1 && drain > 0) a = -(1 - drain);
        if (a === -1) a = 0;
        next.push(a);
        target += on.get()[i] ? 1 - drain : 0;
      }
      amounts.set(next);
      needle.set(needle.get() + (target / ITEMS.length - needle.get()) * (1 - Math.exp(-9 * dt)));
      if (drain >= 1) {
        manual.set(false);
        on.set([0, 0, 0, 0, 0]);
        origin.set(now);
      }
      return;
    }
    if (!autoPlay) return;
    const b = bowlAt((now - origin.get()) % LOOP);
    amounts.set(b.amounts);
    needle.set(b.needle);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (font) drawScene(canvas, amounts.get(), needle.get(), cx, cy, bowl, foods, font);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, bowl, foods, font]);

  const buttonStyle = useAnimatedStyle(() => ({ backgroundColor: needle.get() > 0.9 ? GREEN : INK }));
  const readyStyle = useAnimatedStyle(() => ({ opacity: clamp01((needle.get() - 0.9) / 0.1) }));
  const pickStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01((needle.get() - 0.9) / 0.1) }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const toggle = (i: number) => {
    if (reduced) return;
    haptic();
    const start = () => {
      'worklet';
      const now = elapsed.get();
      if (!manual.get()) {
        // Take over from what the loop shows now.
        const cur: number[] = [];
        for (let k = 0; k < ITEMS.length; k++) cur.push(amounts.get()[k] > 0.5 ? 1 : 0);
        on.set(cur);
        changed.set([-10, -10, -10, -10, -10]);
        manual.set(true);
      }
      const nextOn = on.get().slice();
      nextOn[i] = nextOn[i] ? 0 : 1;
      on.set(nextOn);
      const nextChanged = changed.get().slice();
      nextChanged[i] = now;
      changed.set(nextChanged);
      lastTap.set(now);
    };
    start();
  };

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
        <View style={styles.row}>
          <Text style={styles.brand}>{brand}</Text>
          <Text style={styles.meta}>Lunch · 12:30</Text>
        </View>
        <Text style={styles.title}>{title}</Text>
      </View>
      <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel={`Salad bowl gauge, up to ${TOTAL} kcal.`}>
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.chips}>
          {ITEMS.map((it, i) => (
            <Chip key={it.name} i={i} amounts={amounts} onPress={toggle} />
          ))}
        </View>
        <Animated.View style={[styles.button, buttonStyle]} accessible accessibilityRole="button">
          <Animated.Text style={[styles.action, pickStyle]}>Pick your toppings</Animated.Text>
          <Animated.Text style={[styles.action, styles.actionOver, readyStyle]}>{`Order bowl · $12.40`}</Animated.Text>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#E4C6EC' },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BLACK, fontSize: 18, color: INK },
  meta: { fontSize: 13, fontWeight: '600', color: 'rgba(43,24,56,0.6)' },
  title: { marginTop: 12, fontFamily: BLACK, fontSize: 34, lineHeight: 40, color: INK, letterSpacing: -1 },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  chips: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 16 },
  chip: { height: 38, paddingHorizontal: 11, borderRadius: 19, justifyContent: 'center' },
  chipText: { fontSize: 14, fontWeight: '700' },
  button: { height: 58, borderRadius: 29, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: BLACK, fontSize: 15, color: '#FFFFFF' },
  actionOver: { position: 'absolute' },
});
