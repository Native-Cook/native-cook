import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, Pressable, StyleSheet, Text, useWindowDimensions, View, type TextStyle } from 'react-native';
import { Canvas, ClipOp, FilterMode, MipmapMode, PaintStyle, Path, Picture, Skia, TileMode, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import { BOWL_FLAGS, buildAtlas, DISHES, foodFraction } from './pastaArt';
import {
  beginTouch,
  centreY,
  CENTRE_X,
  COUNT,
  DISC_RADIUS,
  endTouch,
  infoTop as infoTopOf,
  initialTouch,
  moveTouch,
  ORBIT,
  phiAt,
  plates,
  popScale,
  restsAfter,
  roundAway,
} from './pastaMotion';

/*
 Pasta Plate Wheel — the pasta menu of Trattoria Lumetta, an invented restaurant. A big black disc
 sits half off the left edge with eight plates of pasta on its rim; the wheel turns and the plates
 ride it, each food spinning on its own plate as it rolls, while the plate at the right middle lifts
 out of the wheel (larger, deeper shadow) as the selection. The selected dish's number, name, line
 and price roll in below like drums, a little after the wheel (LOOP = 8 s).

 Drag up or down to turn the wheel (it carries the finger's speed and snaps to a plate); tap a plate
 to bring it to the front, tap the front plate to pop it. 2 s after the last touch the wheel springs
 back to the first dish and the loop restarts. Reduced motion: the first dish, still.
 */

const BG = '#ECEBE7';
const INK = '#1B1A17';
const PAPER = '#FBFAF7';
const ACCENT = '#C8472B';
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });

function rgba(hex: number, a: number) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, Math.min(1, Math.max(0, a)));
}

function radialFill(c: SkCanvas, x: number, y: number, r: number, colors: Float32Array[], stops: number[]) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors, stops, TileMode.Clamp));
  c.drawCircle(x, y, r, paint);
}

function drawImageIn(c: SkCanvas, image: SkImage, x: number, y: number, w: number, h: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  c.drawImageRectOptions(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(x, y, w, h), FilterMode.Linear, MipmapMode.None, paint);
}

/** The disc and the plates, in the 393-wide design space. */
function drawWheel(c: SkCanvas, phi: number, cy: number, pop: number, popIndex: number, platesImg: SkImage[], lights: SkImage[], foods: SkImage[]) {
  'worklet';
  const cx = CENTRE_X;
  const disc = DISC_RADIUS;
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setColor(rgba(0x151413, 1));
  c.drawCircle(cx, cy, disc, fill);
  c.save();
  const clip = Skia.PathBuilder.Make();
  clip.addCircle(cx, cy, disc);
  c.clipPath(clip.detach(), ClipOp.Intersect, true);
  radialFill(c, cx + 120, cy - 40, 260, [rgba(0x2b2723, 0.95), rgba(0x2b2723, 0)], [0, 1]);
  c.restore();
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(1.5);
  line.setColor(rgba(0xffffff, 0.045));
  c.drawCircle(cx, cy, ORBIT - 72, line);
  line.setColor(rgba(0xffffff, 0.03));
  c.drawCircle(cx, cy, ORBIT - 112, line);
  line.setStrokeWidth(1);
  line.setColor(rgba(0xffffff, 0.07));
  c.drawCircle(cx, cy, disc - 0.5, line);
  fill.setColor(rgba(0xffffff, 0.18));
  for (let j = 0; j < COUNT; j++) {
    const a = ((j + 0.5 - phi) * Math.PI) / 4;
    c.drawCircle(cx + ORBIT * Math.cos(a), cy + ORBIT * Math.sin(a), 2.5, fill);
  }

  const list = plates(phi, cy, popIndex, pop);
  for (let n = 0; n < list.length; n++) {
    const p = list[n];
    if (p.x + p.size / 2 < -4) continue;
    const bowl = BOWL_FLAGS[p.index];
    const half = p.size / 2;
    const f = p.front;
    const blur = p.size * 0.06 + 10 * f;
    const outer = half + blur;
    const alpha = 0.22 + 0.2 * f;
    radialFill(c, p.x + 4 + 6 * f, p.y + 8 + 12 * f, outer, [rgba(0x2a2118, alpha), rgba(0x2a2118, alpha), rgba(0x2a2118, 0)], [
      0,
      Math.max(0, (half - blur * 0.6) / outer),
      1,
    ]);
    const contact = half + 2.5;
    const ca = 0.32 - 0.15 * f;
    radialFill(c, p.x + 1, p.y + 2, contact, [rgba(0x1a140e, ca), rgba(0x1a140e, ca), rgba(0x1a140e, 0)], [0, (half - 2.5) / contact, 1]);
    drawImageIn(c, platesImg[bowl ? 1 : 0], p.x - half, p.y - half, p.size, p.size);
    const foodHalf = (p.size * foodFraction(bowl) * 1.08) / 2;
    c.save();
    c.translate(p.x, p.y);
    c.rotate((p.spin * 180) / Math.PI, 0, 0);
    drawImageIn(c, foods[p.index], -foodHalf, -foodHalf, foodHalf * 2, foodHalf * 2);
    c.restore();
    drawImageIn(c, lights[bowl ? 1 : 0], p.x - foodHalf, p.y - foodHalf, foodHalf * 2, foodHalf * 2);
  }
}

function haptic(kind: number) {
  if (process.env.EXPO_OS === 'web' || kind === 0) return;
  Haptics.impactAsync(kind === 2 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** One row slot of a drum: it rolls with φ and fades away from the middle. */
function DrumRow({ text, index, phi, rowPx, style }: { text: string; index: number; phi: SharedValue<number>; rowPx: number; style: TextStyle }) {
  const animated = useAnimatedStyle(() => {
    const d = index - phi.get();
    return { transform: [{ translateY: d * rowPx }], opacity: Math.max(0, 1 - 2.2 * Math.abs(d)) };
  });
  return (
    <Animated.View style={[styles.drumRow, animated]}>
      <Text style={style} numberOfLines={1} allowFontScaling={false}>
        {text}
      </Text>
    </Animated.View>
  );
}

/** A line of text on a drum: rows sit `row` apart and roll with φ. */
function Drum({ items, phi, row, width, k, left, top, style }: {
  items: string[];
  phi: SharedValue<number>;
  row: number;
  width: number;
  k: number;
  left: number;
  top: number;
  style: TextStyle;
}) {
  const [base, setBase] = useState(0);
  useAnimatedReaction(
    () => Math.floor(phi.get()),
    (value, previous) => {
      if (value !== previous) scheduleOnRN(setBase, value);
    },
  );
  const n = items.length;
  return (
    <View
      style={[styles.drum, { left: left * k, top: top * k, width: width * k, height: row * k }]}
      pointerEvents="none"
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
    >
      {[-1, 0, 1, 2].map((o) => {
        const j = base + o;
        return <DrumRow key={o} text={items[((j % n) + n) % n]} index={j} phi={phi} rowPx={row * k} style={style} />;
      })}
    </View>
  );
}

type Props = {
  /** Plays the rest → B → C → back to A story on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function PastaPlateWheel({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const [bagCount, setBagCount] = useState(2);
  const [selected, setSelected] = useState(0);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const k = width / 393;
  const designHeight = height / k;
  const safeTop = insets.top / k;
  const safeBottom = insets.bottom / k;
  const cy = centreY(safeTop, designHeight, safeBottom);
  const infoTop = infoTopOf(designHeight, safeBottom);

  const atlas = useMemo(() => buildAtlas(), []);
  const platesImg = useMemo(() => atlas?.plates ?? [], [atlas]);
  const lights = useMemo(() => atlas?.lights ?? [], [atlas]);
  const foods = useMemo(() => atlas?.foods ?? [], [atlas]);
  const grain = useMemo(() => {
    if (!atlas) return null;
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const paint = Skia.Paint();
    const m = Skia.Matrix();
    m.scale(k / 2, k / 2);
    paint.setShader(atlas.grain.makeShaderOptions(TileMode.Repeat, TileMode.Repeat, FilterMode.Nearest, MipmapMode.None, m));
    paint.setAlphaf(0.035);
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);
    return recorder.finishRecordingAsPicture();
  }, [atlas, width, height, k]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);
  const phi0 = useSharedValue(0);
  const phi1 = useSharedValue(0);
  const phi2 = useSharedValue(0);
  const phi3 = useSharedValue(0);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const t = touch.get();
    phi0.set(phiAt(t, now, autoPlay));
    phi1.set(phiAt(t, now - 0.04, autoPlay));
    phi2.set(phiAt(t, now - 0.08, autoPlay));
    phi3.set(phiAt(t, now - 0.12, autoPlay));
    if (!autoPlay && !asleep.get() && !t.touching && t.release >= 0 && now > restsAfter(t)) {
      asleep.set(true);
      scheduleOnRN(sleep);
    }
  }, false);
  const running = !reduced && active && (autoPlay || awake);
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);
  useEffect(() => {
    if (!reduced) return;
    phi0.set(0);
    phi1.set(0);
    phi2.set(0);
    phi3.set(0);
  }, [reduced, phi0, phi1, phi2, phi3]);

  useAnimatedReaction(
    () => {
      const v = roundAway(phi0.get()) % 8;
      return v < 0 ? v + 8 : v;
    },
    (value, previous) => {
      if (value !== previous) scheduleOnRN(setSelected, value);
    },
  );

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (foods.length === COUNT) {
      canvas.scale(k, k);
      const t = touch.get();
      drawWheel(canvas, phi0.get(), cy, popScale(clock.get() - t.popClock), t.popIndex, platesImg, lights, foods);
    }
    return recorder.finishRecordingAsPicture();
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .enabled(!reduced)
        .onBegin(() => {
          touch.set(beginTouch(touch.get(), clock.get(), autoPlay));
          if (!autoPlay) {
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onUpdate((e) => {
          const result = moveTouch(touch.get(), e.translationX / k, e.translationY / k);
          touch.set(result.next);
          if (result.detent) scheduleOnRN(haptic, 1);
        })
        .onFinalize((e) => {
          if (!touch.get().touching) return;
          const result = endTouch(touch.get(), clock.get(), e.velocityY / k, e.x / k, e.y / k, cy);
          touch.set(result.next);
          scheduleOnRN(haptic, result.feedback);
        }),
    [reduced, autoPlay, k, cy, touch, clock, wake, asleep],
  );

  const badgeScale = useSharedValue(1);
  const badgeStyle = useAnimatedStyle(() => ({ transform: [{ scale: badgeScale.get() }] }));
  const addToOrder = useCallback(() => {
    setBagCount((n) => n + 1);
    badgeScale.set(withSequence(withSpring(1.35, { duration: 120, dampingRatio: 0.9 }), withSpring(1, { duration: 450, dampingRatio: 0.5 })));
    haptic(1);
  }, [badgeScale]);

  const u = (v: number) => v * k;
  const counters = useMemo(() => DISHES.map((_, i) => `${String(i + 1).padStart(2, '0')} / ${String(DISHES.length).padStart(2, '0')}`), []);
  const names = useMemo(() => DISHES.map((d) => d.name), []);
  const lines = useMemo(() => DISHES.map((d) => d.line), []);
  const prices = useMemo(() => DISHES.map((d) => d.price), []);
  const dish = DISHES[selected];

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="adjustable" accessibilityLabel="Pasta wheel">
          <Canvas style={StyleSheet.absoluteFill}>
            {grain ? <Picture picture={grain} /> : null}
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      {/* "Select Pasta" pill */}
      <View
        pointerEvents="none"
        importantForAccessibility="no-hide-descendants"
        accessibilityElementsHidden
        style={[styles.pill, { left: u(-24), top: u(cy - 22), width: u(160), height: u(44), borderRadius: u(22), paddingLeft: u(42), paddingRight: u(16) }]}
      >
        <Text style={[styles.pillText, { fontSize: u(15) }]} numberOfLines={1} allowFontScaling={false}>
          Select Pasta
        </Text>
        <Canvas style={{ width: u(4), height: u(9) }}>
          <Path
            path={`M0 0 L${u(4)} ${u(4.5)} L0 ${u(9)}`}
            style="stroke"
            strokeWidth={u(2)}
            strokeCap="round"
            strokeJoin="round"
            color={INK}
          />
        </Canvas>
      </View>

      {/* Header */}
      <View style={[styles.header, { top: u(safeTop + 8), height: u(44) }]} pointerEvents="box-none">
        <Text style={[styles.title, { fontSize: u(22) }]} accessibilityRole="header" allowFontScaling={false}>
          Delicious Pasta
        </Text>
        <View style={[styles.menu, { left: u(24), width: u(44), height: u(44), gap: u(6) }]} accessible accessibilityLabel="Menu">
          {[22, 14, 18].map((w) => (
            <View key={w} style={{ width: u(w), height: u(2.2), borderRadius: u(1.1), backgroundColor: INK }} />
          ))}
        </View>
        <View
          style={[styles.bag, { left: u(393 - 20 - 42), top: u(1), width: u(42), height: u(42), borderRadius: u(21) }]}
          accessible
          accessibilityLabel={`Order, ${bagCount} items`}
        >
          <Canvas style={{ width: u(16), height: u(19) }}>
            <Path
              path={`M${u(4.5)} ${u(7.5)} L${u(4.5)} ${u(4)} A${u(3.5)} ${u(3.5)} 0 0 1 ${u(11.5)} ${u(4)} L${u(11.5)} ${u(7.5)}`}
              style="stroke"
              strokeWidth={u(1.8)}
              strokeCap="round"
              strokeJoin="round"
              color={INK}
            />
            <Path
              path={Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(u(0.9), u(5.9), u(14.2), u(12.2)), u(3), u(3))).detach()}
              style="stroke"
              strokeWidth={u(1.8)}
              color={INK}
            />
          </Canvas>
        </View>
        <Animated.View
          pointerEvents="none"
          style={[styles.badge, { left: u(393 - 20 + 4 - 18), top: u(-2), width: u(18), height: u(18), borderRadius: u(9) }, badgeStyle]}
        >
          <Text style={[styles.badgeText, { fontSize: u(11) }]} allowFontScaling={false}>
            {bagCount}
          </Text>
        </Animated.View>
      </View>
      <Text
        style={[styles.subtitle, { top: u(safeTop + 49), fontSize: u(13) }]}
        pointerEvents="none"
        allowFontScaling={false}
      >
        Trattoria Lumetta · 8 dishes
      </Text>

      {/* Info drums */}
      <View
        style={[styles.infoA11y, { left: u(24), top: u(infoTop), width: u(345), height: u(90) }]}
        pointerEvents="none"
        accessible
        accessibilityLabel={`${dish.name}, ${dish.line}, ${dish.price}`}
      />
      <Drum items={counters} phi={phi0} row={16} width={320} k={k} left={24} top={infoTop}
        style={{ color: ACCENT, fontSize: u(12), fontWeight: '600', fontVariant: ['tabular-nums'] }} />
      <Drum items={names} phi={phi1} row={36} width={320} k={k} left={24} top={infoTop + 20}
        style={{ color: INK, fontSize: u(28), fontWeight: '600', fontFamily: SERIF }} />
      <Drum items={lines} phi={phi2} row={20} width={320} k={k} left={24} top={infoTop + 60}
        style={{ color: '#7A766E', fontSize: u(14) }} />
      <Drum items={prices} phi={phi3} row={32} width={140} k={k} left={24} top={infoTop + 108}
        style={{ color: INK, fontSize: u(26), fontWeight: '700', fontVariant: ['tabular-nums'] }} />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Add ${dish.name} to order`}
        onPress={addToOrder}
        style={[styles.button, { left: u(393 - 20 - 196), top: u(infoTop + 96), width: u(196), height: u(56), borderRadius: u(28), paddingLeft: u(14), gap: u(12) }]}
      >
        <View style={[styles.plus, { width: u(28), height: u(28), borderRadius: u(14) }]}>
          <View style={{ position: 'absolute', width: u(10), height: u(2), borderRadius: u(1), backgroundColor: '#FFFFFF' }} />
          <View style={{ position: 'absolute', width: u(2), height: u(10), borderRadius: u(1), backgroundColor: '#FFFFFF' }} />
        </View>
        <Text style={[styles.buttonText, { fontSize: u(16) }]} numberOfLines={1} allowFontScaling={false}>
          Add to order
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG },
  pill: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: PAPER },
  pillText: { color: INK, fontWeight: '500' },
  header: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  title: { color: INK, fontWeight: '700' },
  menu: { position: 'absolute', top: 0, justifyContent: 'center' },
  bag: {
    position: 'absolute',
    backgroundColor: PAPER,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000000',
    shadowOpacity: 0.12,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  badge: { position: 'absolute', backgroundColor: ACCENT, alignItems: 'center', justifyContent: 'center', elevation: 4 },
  badgeText: { color: '#FFFFFF', fontWeight: '700', fontVariant: ['tabular-nums'] },
  subtitle: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#8A867D', fontWeight: '500' },
  infoA11y: { position: 'absolute' },
  drum: { position: 'absolute', overflow: 'hidden' },
  drumRow: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, justifyContent: 'center' },
  button: { position: 'absolute', flexDirection: 'row', alignItems: 'center', backgroundColor: INK },
  plus: { backgroundColor: ACCENT, alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: PAPER, fontWeight: '600' },
});
