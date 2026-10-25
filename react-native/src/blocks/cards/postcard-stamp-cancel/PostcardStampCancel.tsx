import { Canvas, Fill, ImageShader, Picture, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { bakeMask, bakeTool, drawTool, recordDesk } from './postcardArt';
import { PhotoSide, WritingSide } from './PostcardFaces';
import { autoPose, CARD, DESIGN_H, DESIGN_W, INK_BOX, LOOP, PAD, type Pose, stillPose, TARGET, TOOL } from './postcardMotion';
import { INK_SKSL } from './postcardShader';

type Props = {
  /** Lifts the postmark off its pad, cancels the stamp, and turns the card, every LOOP seconds. */
  autoPlay?: boolean;
};

const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const IDLE = 2;
const DIGIT_H = 22;
const CREAM = '#F3EBDA';
const MUTED = 'rgba(243,235,218,0.62)';
const AMBER = '#EDB262';
/** A press closer than this to the stamp (pt between centres) prints on it. */
const SNAP = 64;
const INK_EFFECT = Skia.RuntimeEffect.Make(INK_SKSL);

type Hand = {
  owned: SharedValue<number>;
  touching: SharedValue<number>;
  idle: SharedValue<number>;
  returning: SharedValue<number>;
  x: SharedValue<number>;
  y: SharedValue<number>;
  h: SharedValue<number>;
  squash: SharedValue<number>;
  ink: SharedValue<number>;
  fade: SharedValue<number>;
  flip: SharedValue<number>;
  tilt: SharedValue<number>;
  count: SharedValue<number>;
  pose: SharedValue<Pose>;
};

/** A finger lands: the loop stops and the finger's values take over from the current frame. */
function takeOver(m: Hand) {
  'worklet';
  m.touching.set(1);
  m.idle.set(0);
  if (m.owned.get() === 1 && m.returning.get() === 0) return;
  const p = m.pose.get();
  for (const v of [m.x, m.y, m.h, m.squash, m.ink, m.fade, m.flip, m.tilt, m.count]) cancelAnimation(v);
  m.x.set(p.x);
  m.y.set(p.y);
  m.h.set(p.h);
  m.squash.set(p.squash);
  m.ink.set(p.ink);
  m.fade.set(p.fade);
  m.flip.set(p.flip % 360);
  m.tilt.set(p.tilt);
  m.count.set(p.count);
  m.returning.set(0);
  m.owned.set(1);
}

function letGo(m: Hand) {
  'worklet';
  m.touching.set(0);
  m.idle.set(0);
}

const thump = () => {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
};
const lift = () => {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
};

/** The press, once the tool is let go over the stamp: drop, squash, ink spreads, lift, the count rolls. */
function press(m: Hand) {
  'worklet';
  m.x.set(withSpring(TARGET.x, { damping: 18, stiffness: 220 }));
  m.y.set(withSpring(TARGET.y, { damping: 18, stiffness: 220 }));
  m.h.set(
    withSequence(
      withTiming(1.08, { duration: 90, easing: Easing.out(Easing.quad) }),
      withTiming(0, { duration: 190, easing: Easing.bezier(0.65, 0, 0.35, 1) }, (done) => {
        if (done) scheduleOnRN(thump);
      }),
      withDelay(380, withTiming(0.85, { duration: 320, easing: Easing.bezier(0.2, 0.8, 0.2, 1) })),
    ),
  );
  m.squash.set(withDelay(280, withSequence(withTiming(1, { duration: 170 }), withTiming(0, { duration: 280 }))));
  if (m.ink.get() < 1) {
    m.fade.set(1);
    m.ink.set(withDelay(280, withTiming(1, { duration: 450, easing: Easing.bezier(0.2, 0.8, 0.2, 1) })));
    m.count.set(withDelay(900, withTiming(1, { duration: 350, easing: Easing.bezier(0.65, 0, 0.35, 1) })));
  }
  m.tilt.set(withDelay(280, withSequence(withTiming(-1.6, { duration: 70 }), withSpring(0, { damping: 6, stiffness: 260, mass: 0.6 }))));
}

export function PostcardStampCancel({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);

  const room = size ? { w: size.w, h: size.h - insets.top - insets.bottom } : null;
  const k = room ? Math.min(room.w / DESIGN_W, room.h / DESIGN_H, 1.15) : 1;
  const ox = room ? (room.w - DESIGN_W * k) / 2 : 0;
  const oy = room ? insets.top + Math.max(0, (room.h - DESIGN_H * k) / 2) : 0;

  const desk = useMemo(() => (size ? recordDesk(size.w, size.h, ox, oy, k) : null), [size, ox, oy, k]);
  const art = useMemo(() => ({ mask: bakeMask(), tool: bakeTool() }), []);

  const t = useSharedValue(0);
  const owned = useSharedValue(0);
  const touching = useSharedValue(0);
  const idle = useSharedValue(0);
  const returning = useSharedValue(0);
  const grabbing = useSharedValue(0);
  const grab = useSharedValue([0, 0]);
  const x = useSharedValue(PAD.x);
  const y = useSharedValue(PAD.y);
  const h = useSharedValue(0);
  const squash = useSharedValue(0);
  const ink = useSharedValue(0);
  const fade = useSharedValue(1);
  const flip = useSharedValue(0);
  const tilt = useSharedValue(0);
  const count = useSharedValue(0);
  const pose = useSharedValue<Pose>(reduced ? stillPose() : autoPose(0));
  const rest = reduced ? stillPose() : autoPose(0);
  const m: Hand = { owned, touching, idle, returning, x, y, h, squash, ink, fade, flip, tilt, count, pose };

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    if (owned.get() === 0) {
      if (auto) t.set((t.get() + dt) % LOOP);
      pose.set(reduced ? stillPose() : autoPose(t.get()));
      return;
    }
    pose.set({ x: x.get(), y: y.get(), h: h.get(), squash: squash.get(), ink: ink.get(), fade: fade.get(), flip: flip.get(), tilt: tilt.get(), count: count.get() });
    if (touching.get() === 1 || returning.get() === 1) return;
    idle.set(idle.get() + dt);
    if (idle.get() < IDLE) return;
    // Home within 0.9 s: the tool flies back to its pad, the card turns writing side up and a fresh
    // card's blank stamp replaces the cancelled one.
    returning.set(1);
    const glide = { duration: 760, easing: Easing.bezier(0.65, 0, 0.35, 1) };
    x.set(withTiming(rest.x, glide));
    y.set(withTiming(rest.y, glide));
    h.set(withSequence(withTiming(0.7, { duration: 300 }), withTiming(0, { duration: 460, easing: Easing.bezier(0.65, 0, 0.35, 1) })));
    squash.set(withTiming(0, { duration: 200 }));
    fade.set(withTiming(rest.fade, { duration: 380 }));
    flip.set(withTiming(0, glide));
    tilt.set(withTiming(0, { duration: 300 }));
    count.set(withTiming(rest.count, glide));
    ink.set(
      withDelay(
        400,
        withTiming(rest.ink, { duration: 380 }, (done) => {
          if (!done) return;
          fade.set(1);
          returning.set(0);
          owned.set(0);
          t.set(0);
        }),
      ),
    );
  });
  const gesture = useMemo(() => {
    // Only a touch that lands on the tool picks it up; anywhere else the pan fails at once and
    // leaves the touch to the tap.
    const pan = Gesture.Pan()
      .manualActivation(true)
      .onTouchesDown((e, state) => {
        const touch = e.changedTouches[0];
        const p = pose.get();
        if (!touch || Math.abs(touch.x - p.x) > TOOL.w / 2 + 14 || Math.abs(touch.y - p.y) > TOOL.h / 2 + 14) {
          state.fail();
          return;
        }
        grab.set([p.x - touch.x, p.y - touch.y]);
        state.activate();
      })
      .onStart(() => {
        takeOver(m);
        grabbing.set(1);
        cancelAnimation(x);
        cancelAnimation(y);
        h.set(withSpring(1, { damping: 14, stiffness: 240 }));
        scheduleOnRN(lift);
      })
      .onUpdate((e) => {
        if (grabbing.get() === 0) return;
        const g = grab.get();
        x.set(e.x + g[0]);
        y.set(e.y + g[1]);
      })
      .onEnd(() => {
        if (grabbing.get() === 0) return;
        if (Math.hypot(x.get() - TARGET.x, y.get() - TARGET.y) < SNAP) press(m);
        else h.set(withTiming(0, { duration: 260, easing: Easing.bezier(0.65, 0, 0.35, 1) }));
      })
      .onFinalize(() => {
        if (grabbing.get() === 1) letGo(m);
        grabbing.set(0);
      });
    // A tap on the card turns it over.
    const tap = Gesture.Tap()
      .maxDistance(10)
      .onEnd((e) => {
        if (e.x < CARD.x || e.x > CARD.x + CARD.w || e.y < CARD.y || e.y > CARD.y + CARD.h) return;
        takeOver(m);
        const target = flip.get() < 90 ? 180 : 0;
        flip.set(withSpring(target, { damping: 16, stiffness: 120 }));
        letGo(m);
      });
    return Gesture.Race(pan, tap);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    return { size: [INK_BOX.w, INK_BOX.h], press: p.ink, fade: p.fade, tilt: [0.8, -0.5], seed: 3.7 };
  });

  const toolPicture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, DESIGN_W, DESIGN_H));
    drawTool(c, art.tool, pose.get());
    return rec.finishRecordingAsPicture();
  });

  const cardStyle = useAnimatedStyle(() => {
    const p = pose.get();
    const lifted = Math.sin((Math.PI * Math.min(180, p.flip % 180.001)) / 180);
    return {
      transform: [{ perspective: 1200 }, { translateY: -10 * lifted }, { rotateZ: `${p.tilt}deg` }, { rotateY: `${p.flip}deg` }, { scale: 1 + 0.04 * lifted }],
    };
  });
  const frontStyle = useAnimatedStyle(() => ({ opacity: Math.cos((pose.get().flip * Math.PI) / 180) >= 0 ? 1 : 0 }));
  const backStyle = useAnimatedStyle(() => ({ opacity: Math.cos((pose.get().flip * Math.PI) / 180) < 0 ? 1 : 0 }));
  const shadowStyle = useAnimatedStyle(() => {
    const lifted = Math.sin((Math.PI * Math.min(180, pose.get().flip % 180.001)) / 180);
    return { opacity: 1 - 0.4 * lifted, transform: [{ translateY: 10 * lifted }, { scaleX: Math.max(0.2, Math.abs(Math.cos((pose.get().flip * Math.PI) / 180))) }] };
  });
  const digitStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -(2 + pose.get().count) * DIGIT_H }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {desk ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={desk} />
        </Canvas>
      ) : null}
      {size ? (
        <View style={[styles.design, { left: ox, top: oy, transform: [{ scale: k }] }]}>
          <View style={styles.header}>
            <Text style={styles.kicker}>POSTMARKS</Text>
            <View style={styles.titleRow}>
              <Text style={styles.title}>Lisbon, day 4</Text>
              <View style={styles.counter} accessibilityLabel="Postcards sent this trip">
                <Text style={styles.counterLabel}>SENT</Text>
                <Text style={styles.digit}>1</Text>
                <View style={styles.digitBox}>
                  <Animated.View style={digitStyle}>
                    {['0', '1', '2', '3', '4'].map((d) => (
                      <Text key={d} style={styles.digit}>
                        {d}
                      </Text>
                    ))}
                  </Animated.View>
                </View>
              </View>
            </View>
            <Text style={styles.sub}>Tram 28 · Alfama · 24 October</Text>
          </View>

          <Animated.View style={[styles.cardShadow, shadowStyle]} />
          <Animated.View style={[styles.card, cardStyle]}>
            <Animated.View style={[StyleSheet.absoluteFill, frontStyle]}>
              <WritingSide />
              {INK_EFFECT ? (
                <Canvas style={styles.ink}>
                  <Fill>
                    <Shader source={INK_EFFECT} uniforms={uniforms}>
                      <ImageShader image={art.mask} fit="fill" rect={{ x: 0, y: 0, width: INK_BOX.w, height: INK_BOX.h }} />
                    </Shader>
                  </Fill>
                </Canvas>
              ) : null}
            </Animated.View>
            <Animated.View style={[StyleSheet.absoluteFill, backStyle]}>
              <PhotoSide />
            </Animated.View>
          </Animated.View>

          <Text style={styles.hint}>Drag the postmark onto the stamp · tap the card to turn it</Text>

          <View style={styles.info}>
            <Text style={styles.infoKicker}>TODAY’S POSTMARK</Text>
            <Text style={styles.infoTitle}>Lisboa · 24 Oct</Text>
            <Text style={styles.infoLine}>Ultramarine Nº 4 · 13:40</Text>
            <View style={styles.infoRule} />
            <Text style={styles.infoLine}>3 of 5 cards for this trip</Text>
          </View>

          <GestureDetector gesture={gesture}>
            <View style={styles.touch} accessible accessibilityLabel="Postcard and postmark. Drag the postmark onto the stamp to cancel it." />
          </GestureDetector>

          <Canvas style={styles.overlay} pointerEvents="none">
            <Picture picture={toolPicture} />
          </Canvas>

          <View style={styles.actions}>
            <Pressable accessibilityRole="button" style={({ pressed }) => [styles.secondary, pressed && styles.pressed]}>
              <Text style={styles.secondaryText}>Write another</Text>
            </Pressable>
            <Pressable accessibilityRole="button" style={({ pressed }) => [styles.primary, pressed && styles.pressed]}>
              <LinearGradient colors={['#F6CB86', AMBER, '#D38E3C']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
              <Text style={styles.primaryText}>Send card</Text>
            </Pressable>
          </View>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#14213A', overflow: 'hidden' },
  design: { position: 'absolute', width: DESIGN_W, height: DESIGN_H, transformOrigin: 'top left' },
  header: { position: 'absolute', left: 24, right: 24, top: 8, gap: 2 },
  kicker: { color: AMBER, fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  titleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { color: CREAM, fontSize: 32, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: MUTED, fontSize: 14, fontWeight: '500' },
  counter: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 32,
    paddingHorizontal: 12,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(243,235,218,0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(243,235,218,0.22)',
  },
  counterLabel: { color: MUTED, fontSize: 10, fontWeight: '700', letterSpacing: 1.4, marginRight: 6 },
  digitBox: { height: DIGIT_H, overflow: 'hidden' },
  digit: { height: DIGIT_H, lineHeight: DIGIT_H, color: CREAM, fontSize: 16, fontWeight: '700', fontVariant: ['tabular-nums'] },
  cardShadow: {
    position: 'absolute',
    left: CARD.x + 6,
    top: CARD.y + 10,
    width: CARD.w - 12,
    height: CARD.h - 8,
    borderRadius: 10,
    backgroundColor: 'rgba(4,8,18,0.35)',
    boxShadow: '0 14px 30px rgba(3,6,14,0.55), 0 2px 4px rgba(3,6,14,0.45)',
  },
  card: { position: 'absolute', left: CARD.x, top: CARD.y, width: CARD.w, height: CARD.h },
  ink: { position: 'absolute', left: INK_BOX.x, top: INK_BOX.y, width: INK_BOX.w, height: INK_BOX.h },
  hint: { position: 'absolute', left: 24, right: 24, top: 362, textAlign: 'center', color: MUTED, fontSize: 12, fontWeight: '500' },
  info: {
    position: 'absolute',
    left: 214,
    width: 163,
    top: 456,
    padding: 14,
    gap: 3,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(243,235,218,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(243,235,218,0.18)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 12px 28px rgba(0,0,0,0.3)',
  },
  infoKicker: { color: AMBER, fontSize: 10, fontWeight: '700', letterSpacing: 1.4 },
  infoTitle: { color: CREAM, fontFamily: SERIF, fontSize: 18, fontWeight: '700', marginTop: 2 },
  infoLine: { color: MUTED, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  infoRule: { height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(243,235,218,0.2)', marginVertical: 6 },
  touch: { position: 'absolute', left: 0, right: 0, top: 100, height: 580 },
  overlay: { position: 'absolute', left: 0, top: 0, width: DESIGN_W, height: DESIGN_H },
  actions: { position: 'absolute', left: 16, right: 16, top: 694, height: 52, flexDirection: 'row', gap: 12 },
  secondary: {
    flex: 1,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: 'rgba(243,235,218,0.3)',
    backgroundColor: 'rgba(243,235,218,0.06)',
  },
  secondaryText: { color: CREAM, fontSize: 16, fontWeight: '600' },
  primary: {
    flex: 1.3,
    borderRadius: 26,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 10px 22px rgba(211,142,60,0.3), inset 0 1px 0 rgba(255,246,222,0.6)',
  },
  primaryText: { color: '#2A1806', fontSize: 16, fontWeight: '700' },
  pressed: { transform: [{ scale: 0.97 }] },
});
