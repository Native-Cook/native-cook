import {
  AlphaType,
  Canvas,
  ColorType,
  FilterMode,
  Fill,
  ImageShader,
  matchFont,
  MipmapMode,
  Picture,
  Shader,
  type SkImage,
  Skia,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { bakeRelief, drawCrayon, recordRoom, reliefWeights } from './crayonRubbingArt';
import {
  autoBadge,
  autoGrid,
  autoPeel,
  CANVAS,
  CELL,
  crayonAt,
  CRAYON_REST,
  DESIGN_H,
  DESIGN_W,
  GX,
  GY,
  LOOP,
  packGrid,
  PAPER,
  PLAQUE,
  revealed,
  rubSegment,
} from './crayonRubbingMotion';
import { RUB_SKSL } from './crayonRubbingShader';

type Props = {
  /** Scrubs the plaque with a crayon until the heron shows, collects it and peels the sheet, every LOOP seconds. */
  autoPlay?: boolean;
};

const IDLE = 2;
const DIGIT_H = 34;
const CREAM = '#F1EADF';
const MUTED = 'rgba(241,234,223,0.6)';
const GOLD = '#E2B66E';
/** Reduced motion shows the finished rubbing: the demo's field just after the badge lands. */
const STILL_T = 5.0;
const RUB_EFFECT = Skia.RuntimeEffect.Make(RUB_SKSL);
const LABEL_FONT = matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }), fontSize: 6.5, fontWeight: '700' });

/** Three wax sticks: the shader's colour (linear 0–1 sRGB) and the crayon's body tones. */
const WAXES: { name: string; rgb: number[]; tones: [string, string, string] }[] = [
  { name: 'INDIGO', rgb: [0.17, 0.19, 0.36], tones: ['#5A6296', '#2C3160', '#181B3A'] },
  { name: 'OXBLOOD', rgb: [0.4, 0.11, 0.12], tones: ['#9A4A48', '#651D20', '#3E0F12'] },
  { name: 'GRAPHITE', rgb: [0.15, 0.15, 0.16], tones: ['#6E6E72', '#38383C', '#1D1D20'] },
];

type Rub = {
  owned: SharedValue<number>;
  touching: SharedValue<number>;
  idle: SharedValue<number>;
  returning: SharedValue<number>;
  grid: SharedValue<number[]>;
  user: SharedValue<number[]>;
  cx: SharedValue<number>;
  cy: SharedValue<number>;
  angle: SharedValue<number>;
  contact: SharedValue<number>;
  peel: SharedValue<number>;
  badge: SharedValue<number>;
};

/** A finger lands on the paper: the demo stops and the finger carries on from what is on screen. */
function takeOver(m: Rub) {
  'worklet';
  m.touching.set(1);
  m.idle.set(0);
  if (m.owned.get() === 1 && m.returning.get() === 0) return;
  cancelAnimation(m.peel);
  cancelAnimation(m.cx);
  cancelAnimation(m.cy);
  const shown = m.grid.get();
  const user = m.user.get();
  // Mid-peel, the finger starts on the fresh sheet; otherwise it keeps rubbing the one on screen.
  const fresh = m.peel.get() > 0.001;
  for (let i = 0; i < GX * GY; i += 1) user[i] = fresh ? 0 : shown[i];
  m.peel.set(0);
  m.returning.set(0);
  m.owned.set(1);
}

function letGo(m: Rub) {
  'worklet';
  m.touching.set(0);
  m.idle.set(0);
  m.contact.set(withTiming(0, { duration: 180 }));
}

const tick = () => {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
};
const collected = () => {
  if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
};

export function CrayonRubbingCard({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [wax, setWax] = useState(0);

  const room = size ? { w: size.w, h: size.h - insets.top - insets.bottom } : null;
  const k = room ? Math.min(room.w / DESIGN_W, room.h / DESIGN_H, 1.15) : 1;
  const ox = room ? (room.w - DESIGN_W * k) / 2 : 0;
  const oy = room ? insets.top + Math.max(0, (room.h - DESIGN_H * k) / 2) : 0;
  const roomPicture = useMemo(() => (size ? recordRoom(size.w, size.h, ox, oy, k) : null), [size, ox, oy, k]);
  const art = useMemo(() => {
    const relief = bakeRelief();
    // A blank field for the first frame, before the frame callback has made one.
    const bytes = new Uint8Array(GX * GY * 4);
    for (let i = 3; i < bytes.length; i += 4) bytes[i] = 255;
    const data = Skia.Data.fromBytes(bytes);
    const blank = Skia.Image.MakeImage({ width: GX, height: GY, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Opaque }, data, GX * 4);
    return { relief, blank, ...reliefWeights(relief) };
  }, []);

  const t = useSharedValue(reduced ? STILL_T : 0);
  const owned = useSharedValue(0);
  const touching = useSharedValue(0);
  const idle = useSharedValue(0);
  const returning = useSharedValue(0);
  const grid = useSharedValue<number[]>(new Array(GX * GY).fill(0));
  const user = useSharedValue<number[]>(new Array(GX * GY).fill(0));
  const cx = useSharedValue(CRAYON_REST.x);
  const cy = useSharedValue(CRAYON_REST.y);
  const angle = useSharedValue(CRAYON_REST.angle);
  const contact = useSharedValue(0);
  const peel = useSharedValue(0);
  const badge = useSharedValue(0);
  const grain = useSharedValue(-0.6);
  const reveal = useSharedValue(0);
  const shownPct = useSharedValue(0);
  const rubImage = useSharedValue<SkImage | null>(art.blank);
  const old = useSharedValue<SkImage | null>(null);
  const lastSum = useSharedValue(-1);
  const waxRgb = useSharedValue(WAXES[0].rgb);
  const waxIndex = useSharedValue(0);
  const last = useSharedValue([0, 0]);
  const m: Rub = { owned, touching, idle, returning, grid, user, cx, cy, angle, contact, peel, badge };

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const g = grid.get();
    if (owned.get() === 0) {
      if (auto) t.set((t.get() + dt) % LOOP);
      const now = t.get();
      autoGrid(now, g);
      const c = crayonAt(now);
      const ahead = crayonAt(Math.min(now + 1 / 60, LOOP - 1e-3));
      cx.set(c[0]);
      cy.set(c[1]);
      angle.set(c[2]);
      contact.set(c[3]);
      peel.set(autoPeel(now));
      badge.set(autoBadge(now));
      if (c[3] > 0) grain.set(Math.atan2(ahead[1] - c[1], ahead[0] - c[0]));
    } else {
      const u = user.get();
      for (let i = 0; i < GX * GY; i += 1) g[i] = u[i];
      if (touching.get() === 0 && returning.get() === 0) {
        idle.set(idle.get() + dt);
        if (idle.get() >= IDLE) {
          // Back to rest within 0.9 s: the sheet peels away to a fresh one, the crayon goes home.
          returning.set(1);
          const glide = { duration: 800, easing: Easing.bezier(0.65, 0, 0.35, 1) };
          cx.set(withTiming(CRAYON_REST.x, glide));
          cy.set(withTiming(CRAYON_REST.y, glide));
          angle.set(withTiming(CRAYON_REST.angle, glide));
          badge.set(withTiming(0, { duration: 300 }));
          peel.set(
            withTiming(1, { duration: 850, easing: Easing.bezier(0.65, 0, 0.35, 1) }, (done) => {
              if (!done) return;
              const uu = user.get();
              for (let i = 0; i < GX * GY; i += 1) uu[i] = 0;
              peel.set(0);
              returning.set(0);
              owned.set(0);
              t.set(0);
            }),
          );
        }
      }
    }
    // Rebuild the field's little image only when it changed.
    let sum = 0;
    for (let i = 0; i < GX * GY; i += 1) sum += g[i];
    if (sum !== lastSum.get()) {
      lastSum.set(sum);
      const bytes = new Uint8Array(GX * GY * 4);
      packGrid(g, bytes);
      const data = Skia.Data.fromBytes(bytes);
      const image = Skia.Image.MakeImage({ width: GX, height: GY, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Opaque }, data, GX * 4);
      data.dispose();
      // The image two frames back is surely no longer drawn.
      const stale = old.get();
      if (stale && stale !== art.blank) stale.dispose();
      old.set(rubImage.get());
      rubImage.set(image);
      reveal.set(revealed(g, art.weights, art.total));
    }
  });

  // The percentage rests on whole numbers: each change springs the counter to the new value.
  useAnimatedReaction(
    () => Math.min(99, Math.round(reveal.get() * 100)),
    (now, before) => {
      if (now === before) return;
      shownPct.set(withSpring(now, { damping: 20, stiffness: 180 }));
      if (owned.get() === 1 && before !== null && Math.floor(now / 10) > Math.floor(before / 10)) scheduleOnRN(tick);
    },
  );
  // Rubbing it far enough by hand collects the plaque.
  useAnimatedReaction(
    () => (owned.get() === 1 && returning.get() === 0 && reveal.get() > 0.7 ? 1 : 0),
    (now, before) => {
      if (now === 1 && before === 0) {
        badge.set(withSpring(1, { damping: 9, stiffness: 180 }));
        scheduleOnRN(collected);
      }
    },
  );

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          if (returning.get() === 1) return;
          takeOver(m);
          // The crayon's working edge sits just above and left of the finger, so the finger never hides it.
          const x = e.x - 12;
          const y = e.y - 16;
          cx.set(withSpring(x, { damping: 20, stiffness: 400 }));
          cy.set(withSpring(y, { damping: 20, stiffness: 400 }));
          angle.set(withTiming(-34, { duration: 160 }));
          contact.set(withTiming(1, { duration: 120 }));
          last.set([x, y]);
        })
        .onUpdate((e) => {
          if (owned.get() === 0 || returning.get() === 1) return;
          const x = e.x - 12;
          const y = e.y - 16;
          const p = last.get();
          cancelAnimation(cx);
          cancelAnimation(cy);
          cx.set(x);
          cy.set(y);
          const dx = x - p[0];
          const dy = y - p[1];
          if (dx * dx + dy * dy > 1) {
            grain.set(Math.atan2(dy, dx));
            // The hand turns the crayon a little toward the stroke.
            angle.set(-34 + Math.max(-8, Math.min(8, dx * 0.4)));
          }
          rubSegment(user.get(), p[0] - PAPER.x, p[1] - PAPER.y, x - PAPER.x, y - PAPER.y);
          last.set([x, y]);
        })
        .onFinalize(() => {
          letGo(m);
        }),
    // The shared values are stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const uniforms = useDerivedValue(() => ({
    plaque: [PLAQUE.x, PLAQUE.y, PLAQUE.w, PLAQUE.h],
    paper: [PAPER.x, PAPER.y, PAPER.w, PAPER.h],
    peel: peel.get(),
    wax: waxRgb.get(),
    grainAngle: grain.get(),
  }));

  // The crayon is drawn into one picture per frame (no animated groups), so exactly one is ever drawn.
  const crayonPicture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, CANVAS.w, CANVAS.h));
    const w = WAXES[waxIndex.get()];
    drawCrayon(c, cx.get(), cy.get(), angle.get(), contact.get(), w.tones, w.name, LABEL_FONT);
    return rec.finishRecordingAsPicture();
  });

  const tens = useAnimatedStyle(() => {
    const v = shownPct.get();
    const ones = v % 10;
    return { transform: [{ translateY: -(Math.floor(v / 10) + Math.max(0, ones - 9)) * DIGIT_H }] };
  });
  const ones = useAnimatedStyle(() => ({ transform: [{ translateY: -(shownPct.get() % 10) * DIGIT_H }] }));
  const bar = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.max(0.001, shownPct.get() / 100) }] }));
  const badgeStyle = useAnimatedStyle(() => {
    const b = badge.get();
    return { opacity: Math.min(1, b * 1.4), transform: [{ scale: 0.86 + 0.14 * b }, { rotate: `${-4 * b}deg` }] };
  });

  const pickWax = (i: number) => {
    setWax(i);
    waxRgb.set(WAXES[i].rgb);
    waxIndex.set(i);
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const sampling = { filter: FilterMode.Linear, mipmap: MipmapMode.None };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {roomPicture ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={roomPicture} />
        </Canvas>
      ) : null}
      {size ? (
        <View style={[styles.design, { left: ox, top: oy, transform: [{ scale: k }] }]}>
          <View style={styles.header}>
            <Text style={styles.kicker}>RUBBINGS</Text>
            <Text style={styles.title}>Hall C, plaque 14</Text>
            <Text style={styles.sub}>Cast bronze, 1911 · Riverside Gate</Text>
          </View>

          <GestureDetector gesture={gesture}>
            <View style={styles.hero} accessible accessibilityLabel="A sheet of paper over a bronze plaque. Rub it with the crayon to reveal the relief.">
              {RUB_EFFECT ? (
                <Canvas style={StyleSheet.absoluteFill}>
                  <Fill>
                    <Shader source={RUB_EFFECT} uniforms={uniforms}>
                      <ImageShader image={art.relief} fit="fill" rect={{ x: PAPER.x, y: PAPER.y, width: PAPER.w, height: PAPER.h }} sampling={sampling} />
                      <ImageShader image={rubImage} fit="fill" rect={{ x: PAPER.x, y: PAPER.y, width: GX * CELL, height: GY * CELL }} sampling={sampling} />
                    </Shader>
                  </Fill>
                  <Picture picture={crayonPicture} />
                </Canvas>
              ) : null}
              <Animated.View style={[styles.badge, badgeStyle]} pointerEvents="none">
                <Svg width={16} height={16} viewBox="0 0 16 16">
                  <SvgPath d="M3.5 8.4 L6.6 11.4 L12.6 4.8" stroke="#2A1A08" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
                </Svg>
                <View>
                  <Text style={styles.badgeTitle}>Collected</Text>
                  <Text style={styles.badgeSub}>No. 14 of 40</Text>
                </View>
              </Animated.View>
            </View>
          </GestureDetector>

          <View style={styles.panel}>
            <View style={styles.revealBlock}>
              <Text style={styles.panelLabel}>REVEALED</Text>
              <View style={styles.pctRow}>
                <View style={styles.digitBox}>
                  <Animated.View style={tens}>
                    {DIGITS.map((d, i) => (
                      <Text key={i} style={styles.digit}>
                        {d}
                      </Text>
                    ))}
                  </Animated.View>
                </View>
                <View style={styles.digitBox}>
                  <Animated.View style={ones}>
                    {DIGITS.map((d, i) => (
                      <Text key={i} style={styles.digit}>
                        {d}
                      </Text>
                    ))}
                  </Animated.View>
                </View>
                <Text style={styles.pctSign}>%</Text>
              </View>
              <View style={styles.track}>
                <Animated.View style={[styles.fill, bar]} />
              </View>
            </View>
            <View style={styles.waxBlock}>
              <Text style={styles.panelLabel}>WAX</Text>
              <View style={styles.chips}>
                {WAXES.map((w, i) => (
                  <Pressable
                    key={w.name}
                    onPress={() => pickWax(i)}
                    accessibilityRole="button"
                    accessibilityLabel={`${w.name.toLowerCase()} wax`}
                    accessibilityState={{ selected: wax === i }}
                    hitSlop={8}
                    style={[styles.chip, wax === i && styles.chipOn]}
                  >
                    <LinearGradient colors={w.tones} style={styles.chipFill} />
                  </Pressable>
                ))}
              </View>
            </View>
          </View>

          <Pressable accessibilityRole="button" style={({ pressed }) => [styles.button, pressed && styles.pressed]}>
            <LinearGradient colors={['#F2CF8E', GOLD, '#C9913F']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
            <Text style={styles.buttonText}>Save to my rubbings</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#211D19', overflow: 'hidden' },
  design: { position: 'absolute', width: DESIGN_W, height: DESIGN_H, transformOrigin: 'top left' },
  header: { position: 'absolute', left: 24, right: 24, top: 8, gap: 2 },
  kicker: { color: GOLD, fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: CREAM, fontSize: 32, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: MUTED, fontSize: 14, fontWeight: '500' },
  hero: { position: 'absolute', left: CANVAS.x, top: CANVAS.y, width: CANVAS.w, height: CANVAS.h },
  badge: {
    position: 'absolute',
    right: 14,
    top: 0,
    height: 44,
    paddingLeft: 12,
    paddingRight: 16,
    borderRadius: 22,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: GOLD,
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 10px 24px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,246,220,0.6)',
  },
  badgeTitle: { color: '#2A1A08', fontSize: 15, fontWeight: '800' },
  badgeSub: { color: 'rgba(42,26,8,0.7)', fontSize: 10, fontWeight: '700', letterSpacing: 0.4 },
  panel: {
    position: 'absolute',
    left: 16,
    right: 16,
    top: 612,
    height: 72,
    borderRadius: 20,
    borderCurve: 'continuous',
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(241,234,223,0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(241,234,223,0.16)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 12px 28px rgba(0,0,0,0.3)',
  },
  revealBlock: { width: 150, gap: 2 },
  panelLabel: { color: MUTED, fontSize: 10, fontWeight: '700', letterSpacing: 1.4 },
  pctRow: { flexDirection: 'row', alignItems: 'flex-end', height: DIGIT_H },
  digitBox: { height: DIGIT_H, width: 17, overflow: 'hidden' },
  digit: { height: DIGIT_H, lineHeight: DIGIT_H, color: CREAM, fontSize: 28, fontWeight: '800', fontVariant: ['tabular-nums'], textAlign: 'center' },
  pctSign: { color: MUTED, fontSize: 16, fontWeight: '700', marginLeft: 2, marginBottom: 5 },
  track: { height: 4, borderRadius: 2, backgroundColor: 'rgba(241,234,223,0.12)', overflow: 'hidden', marginTop: 2 },
  fill: { height: 4, width: '100%', backgroundColor: GOLD, transformOrigin: 'left center' },
  waxBlock: { alignItems: 'flex-end', gap: 8 },
  chips: { flexDirection: 'row', gap: 10 },
  chip: { width: 30, height: 30, borderRadius: 15, padding: 3, borderWidth: 1.5, borderColor: 'transparent' },
  chipOn: { borderColor: GOLD },
  chipFill: { flex: 1, borderRadius: 12 },
  button: {
    position: 'absolute',
    left: 16,
    right: 16,
    top: 698,
    height: 52,
    borderRadius: 26,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(0,0,0,0.45), 0 10px 24px rgba(201,145,63,0.28), inset 0 1px 0 rgba(255,246,220,0.6)',
  },
  pressed: { transform: [{ scale: 0.97 }] },
  buttonText: { color: '#2A1A08', fontSize: 17, fontWeight: '700' },
});
