import { Canvas, Group, Image, LinearGradient, Rect, Skia, type SkImage, type SkPath, useImage, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { bakePaper, COUNT, CTA_H, layoutJigsaw, piecePaths, TEXT_H, TOP_BAR } from './jigsawGeometry';
import { LOOP, piecePose, slotPiece, smoothstep, textRoll, wholePose } from './jigsawMotion';
import { JigsawPiece, POSE } from './JigsawPiece';

/*
 Jigsaw Steps Welcome — onboarding for a family-photo app ("Piecemeal"). A beach photo is cut
 into twelve jigsaw pieces scattered under the board; each onboarding step snaps four of them
 home (corners, outer middles, middle row), the cut lines fade, and the photo comes alive with a
 slow push-in. The text and dots above the button roll to each step. Timeline in jigsawMotion.ts
 (LOOP = 10 s); piece outlines and layout in jigsawGeometry.ts.

 Touch: pick up any piece (it lifts, its shadow grows, its tilt straightens on a spring) and drag
 it; let go within 35 % of a cell of its home and it snaps in (spring ω 13 rad/s, damping ratio
 0.7, a light haptic, a light running round its edges); anywhere else it stays where it was
 dropped. The text follows how many pieces are home; with all twelve home the cut lines fade.
 2 s after the last touch the pieces spring back to the pile and the loop restarts.
 Reduced motion: the finished photo and the last step, still; pieces can still be dragged.
 */

type Props = {
  /** Plays the three onboarding steps and scatters the pieces again, on a loop. */
  autoPlay?: boolean;
};

const INK = '#2A221C';
const MUTE = 'rgba(42,34,28,0.6)';
const ACCENT = '#D4643A';
const HOLD_AFTER_TOUCH = 2;
const SLOTS = 4;
const K = 170;
const DAMP = 2 * 0.7 * Math.sqrt(K);
const ROW = TEXT_H;

const TEXTS = [
  { title: 'Your family, in pieces', body: 'Photos scattered across phones, drawers and old albums.' },
  { title: 'Gather every photo', body: 'Bring in your camera roll, scanned prints and shared links.' },
  { title: 'Invite the whole family', body: 'Everyone adds the moments only they were there for.' },
  { title: 'See the whole picture', body: 'Piecemeal puts it all together, year by year.' },
];

function snapFeel() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function Mark() {
  return (
    <Svg width={22} height={22} viewBox="0 0 22 22">
      <SvgPath
        d="M3 3h6.2c-.9-2.6 4.5-2.6 3.6 0H19v6.2c2.6-.9 2.6 4.5 0 3.6V19h-6.2c.9 2.6-4.5 2.6-3.6 0H3v-6.2c2.6.9 2.6-4.5 0-3.6z"
        fill={ACCENT}
      />
    </Svg>
  );
}

function Dot({ k, roll }: { k: number; roll: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const on = smoothstep(k + 0.5, k + 1, roll.get());
    return { width: 8 + 16 * on, opacity: 0.3 + 0.7 * on };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

export function JigsawStepsWelcome({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const photo = useImage(require('./assets/beach.jpg'));
  const L = useMemo(() => (size ? layoutJigsaw(size.w, size.h, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const paths = useMemo(() => (L ? piecePaths(L) : []), [L]);
  const paper = useMemo(() => (L && paths.length ? bakePaper(L, paths, Math.min(3, PixelRatio.get())) : null), [L, paths]);

  const homes = L?.home ?? [];
  const scat = L?.scatter ?? [];
  const B = L?.board ?? { x: 0, y: 0, w: 1, h: 1 };
  const snapR = (L?.cell.w ?? 80) * 0.35;

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  /** 0 = autoPlay, 1 = the finger's, 2 = springing back to the pile. */
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const poses = useSharedValue<number[]>(new Array(COUNT * POSE).fill(0));
  const slots = useSharedValue<number[]>([-1, -1, -1, -1]);
  const roll = useSharedValue(0);
  const whole = useSharedValue<number[]>([0, 1, 0]);
  /** Manual state per piece: x, y, rot, vx, vy, vrot, lift, flash, target x, target y, target rot, on board. */
  const M = useSharedValue<number[]>(new Array(COUNT * 12).fill(0));
  const held = useSharedValue(-1);
  const top = useSharedValue(-1);
  const grabDX = useSharedValue(0);
  const grabDY = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const returnAt = useSharedValue(0);

  useFrameCallback((f) => {
    if (homes.length !== COUNT) return;
    const dt = Math.min(0.034, (f.timeSincePreviousFrame ?? 16) / 1000);
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      const t = reduced ? 7.2 : clock.get();
      const out: number[] = [];
      for (let i = 0; i < COUNT; i += 1) {
        const p = piecePose(i, t, homes[i], scat[i]);
        out.push(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[6] > 0 || (t > 6.4 && t < 8.2) ? 1 : 0);
      }
      poses.set(out);
      const s: number[] = [];
      for (let j = 0; j < SLOTS; j += 1) s.push(reduced ? -1 : slotPiece(j, t));
      slots.set(s);
      roll.set(textRoll(t));
      whole.set(wholePose(t));
      return;
    }
    if (m === 1 && held.get() < 0 && now.get() - lastTouch.get() > HOLD_AFTER_TOUCH) {
      mode.set(2);
      returnAt.set(now.get());
      held.set(-1);
      top.set(-1);
    }
    if (m === 2 && now.get() - returnAt.get() > 1.2) {
      clock.set(0);
      mode.set(0);
      return;
    }
    const st = M.get().slice();
    const out: number[] = [];
    let home = 0;
    for (let i = 0; i < COUNT; i += 1) {
      const o = i * 12;
      if (m === 2) {
        st[o + 8] = scat[i][0];
        st[o + 9] = scat[i][1];
        st[o + 10] = scat[i][2];
        st[o + 11] = 0;
      }
      const isHeld = held.get() === i;
      if (isHeld) {
        st[o] = fingerX.get() - grabDX.get();
        st[o + 1] = fingerY.get() - grabDY.get();
        st[o + 3] = 0;
        st[o + 4] = 0;
      } else {
        st[o + 3] += (K * (st[o + 8] - st[o]) - DAMP * st[o + 3]) * dt;
        st[o + 4] += (K * (st[o + 9] - st[o + 1]) - DAMP * st[o + 4]) * dt;
        st[o] += st[o + 3] * dt;
        st[o + 1] += st[o + 4] * dt;
      }
      const rotTarget = isHeld ? 0 : st[o + 10];
      st[o + 5] += (K * 0.6 * (rotTarget - st[o + 2]) - DAMP * 0.8 * st[o + 5]) * dt;
      st[o + 2] += st[o + 5] * dt;
      const liftTarget = isHeld || (m === 2 && now.get() - returnAt.get() < 0.6) ? 1 : 0;
      st[o + 6] += (liftTarget - st[o + 6]) * Math.min(1, dt * (m === 2 ? 4 : 12));
      st[o + 7] = Math.max(0, st[o + 7] - dt / 0.35);
      if (st[o + 11] > 0) home += 1;
      out.push(st[o], st[o + 1], st[o + 2], 1 + 0.07 * st[o + 6], st[o + 6], st[o + 7], st[o + 11], st[o + 11]);
    }
    // With every piece home the cut lines fade (the whole photo fades in over them).
    if (home === COUNT) for (let i = 0; i < COUNT; i += 1) out[i * POSE + 6] = 0;
    M.set(st);
    poses.set(out);
    const tp = top.get();
    slots.set([tp >= 0 && (held.get() === tp || st[tp * 12 + 6] > 0.02) ? tp : -1, -1, -1, -1]);
    const target = home === COUNT ? 3 : Math.floor(home / 4);
    roll.set(roll.get() + (target - roll.get()) * Math.min(1, dt * 8));
    const w = whole.get();
    const wa = w[0] + ((home === COUNT ? 1 : 0) - w[0]) * Math.min(1, dt * 3);
    whole.set([wa, 1, 0]);
  });

  /** Is the point inside piece i where it is drawn now? */
  function hits(i: number, x: number, y: number) {
    'worklet';
    const p = poses.get();
    const o = i * POSE;
    const dx = x - p[o];
    const dy = y - p[o + 1];
    const c = Math.cos(-p[o + 2]);
    const s = Math.sin(-p[o + 2]);
    const lx = (dx * c - dy * s) / p[o + 3] + homes[i][0];
    const ly = (dx * s + dy * c) / p[o + 3] + homes[i][1];
    return paths[i].contains(lx, ly);
  }

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      if (homes.length !== COUNT) return;
      let hit = -1;
      const sl = slots.get();
      for (let j = 0; j < SLOTS && hit < 0; j += 1) if (sl[j] >= 0 && hits(sl[j], e.x, e.y)) hit = sl[j];
      for (let i = COUNT - 1; i >= 0 && hit < 0; i -= 1) if (hits(i, e.x, e.y)) hit = i;
      if (hit < 0) return;
      if (mode.get() !== 1) {
        // Take over from wherever the pieces are drawn now.
        const p = poses.get();
        const st: number[] = [];
        for (let i = 0; i < COUNT; i += 1) {
          const o = i * POSE;
          st.push(p[o], p[o + 1], p[o + 2], 0, 0, 0, p[o + 4], 0, p[o], p[o + 1], p[o + 2], p[o + 7]);
        }
        M.set(st);
        mode.set(1);
      }
      const p = poses.get();
      held.set(hit);
      top.set(hit);
      grabDX.set(e.x - p[hit * POSE]);
      grabDY.set(e.y - p[hit * POSE + 1]);
      fingerX.set(e.x);
      fingerY.set(e.y);
      lastTouch.set(now.get());
      const st = M.get().slice();
      st[hit * 12 + 11] = 0;
      M.set(st);
    })
    .onUpdate((e) => {
      if (held.get() < 0) return;
      fingerX.set(e.x);
      fingerY.set(e.y);
      lastTouch.set(now.get());
    })
    .onFinalize((e) => {
      const i = held.get();
      if (i < 0) return;
      held.set(-1);
      lastTouch.set(now.get());
      const st = M.get().slice();
      const o = i * 12;
      const x = e.x - grabDX.get();
      const y = e.y - grabDY.get();
      st[o + 3] = e.velocityX * 0.25;
      st[o + 4] = e.velocityY * 0.25;
      if (Math.hypot(x - homes[i][0], y - homes[i][1]) < snapR) {
        st[o + 8] = homes[i][0];
        st[o + 9] = homes[i][1];
        st[o + 10] = 0;
        st[o + 11] = 1;
        st[o + 7] = 1;
        scheduleOnRN(snapFeel);
      } else {
        st[o + 8] = x;
        st[o + 9] = y;
        st[o + 10] = st[o + 2];
      }
      M.set(st);
    });

  const textStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -roll.get() * ROW }] }));
  const ctaStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -smoothstep(2.4, 2.9, roll.get()) * 22 }] }));
  const wholeOpacity = useDerivedValue(() => whole.get()[0]);
  const burns = useDerivedValue(() => [{ scale: whole.get()[1] }]);
  const gleam = useDerivedValue(() => [{ translateX: (whole.get()[2] * 1.6 - 0.3) * B.w }]);
  const gleamOpacity = useDerivedValue(() => Math.sin(Math.PI * whole.get()[2]));
  const boardClip = useMemo(() => Skia.RRectXY(Skia.XYWHRect(B.x, B.y, B.w, B.h), 4, 4), [B.x, B.y, B.w, B.h]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A beach photo cut into jigsaw pieces. Drag a piece to its place on the board.">
          {L && paper ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Image image={paper} x={0} y={0} width={L.W} height={L.H} />
              {paths.map((_, i) => (
                <JigsawPiece key={i} index={i} paths={paths} homes={homes} poses={poses} slots={slots} photo={photo} board={B} />
              ))}
              <Group clip={boardClip} opacity={wholeOpacity}>
                <Group origin={vec(B.x + B.w * 0.62, B.y + B.h * 0.45)} transform={burns}>
                  <Image image={photo} x={B.x} y={B.y} width={B.w} height={B.h} fit="cover" />
                </Group>
                <Group transform={gleam} opacity={gleamOpacity}>
                  <Rect x={B.x - B.w * 0.3} y={B.y} width={B.w * 0.6} height={B.h} blendMode="screen">
                    <LinearGradient start={vec(B.x - B.w * 0.3, B.y)} end={vec(B.x + B.w * 0.3, B.y + B.h * 0.4)} colors={['rgba(255,220,170,0)', 'rgba(255,220,170,0.28)', 'rgba(255,220,170,0)']} />
                  </Rect>
                </Group>
              </Group>
              {[0, 1, 2, 3].map((j) => (
                <SlotPiece key={`s${j}`} j={j} slots={slots} paths={paths} homes={homes} poses={poses} photo={photo} board={B} />
              ))}
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View style={[styles.topBar, { top: insets.top + 8 }]}>
        <View style={styles.brand}>
          <Mark />
          <Text style={styles.brandText}>Piecemeal</Text>
        </View>
        <Pressable accessibilityRole="button" hitSlop={10}>
          <Text style={styles.skip}>Skip</Text>
        </Pressable>
      </View>

      {L ? (
        <>
          <View pointerEvents="none" style={[styles.textBlock, { top: L.textY }]}>
            <View style={styles.dots}>
              {[0, 1, 2].map((k) => (
                <Dot key={k} k={k} roll={roll} />
              ))}
            </View>
            <View style={[styles.textClip, { height: ROW - 22 }]}>
              <Animated.View style={textStyle}>
                {TEXTS.map((t, k) => (
                  <View key={k} style={{ height: ROW }}>
                    <Text style={styles.title}>{t.title}</Text>
                    <Text style={styles.body}>{t.body}</Text>
                  </View>
                ))}
              </Animated.View>
            </View>
          </View>
          <Pressable accessibilityRole="button" style={({ pressed }) => [styles.cta, { top: L.ctaY, transform: [{ scale: pressed ? 0.97 : 1 }] }]}>
            <View style={styles.ctaClip}>
              <Animated.View style={ctaStyle}>
                <Text style={styles.ctaText}>Continue</Text>
                <Text style={styles.ctaText}>Start your album</Text>
              </Animated.View>
            </View>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

type SlotProps = {
  j: number;
  slots: SharedValue<number[]>;
  paths: SkPath[];
  homes: number[][];
  poses: SharedValue<number[]>;
  photo: SkImage | null;
  board: { x: number; y: number; w: number; h: number };
};

/** An overlay slot: draws whichever piece is in flight in slot j, above everything else. */
function SlotPiece({ j, slots, ...rest }: SlotProps) {
  const index = useDerivedValue(() => slots.get()[j]);
  return <JigsawPiece index={index} {...rest} />;
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#F2E9DC' },
  topBar: { position: 'absolute', left: 20, right: 20, height: TOP_BAR, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  brandText: { color: INK, fontSize: 18, fontWeight: '700', letterSpacing: -0.3 },
  skip: { color: MUTE, fontSize: 16, fontWeight: '500' },
  textBlock: { position: 'absolute', left: 24, right: 24, height: TEXT_H },
  dots: { flexDirection: 'row', gap: 6, height: 8, marginBottom: 14 },
  dot: { height: 8, borderRadius: 4, backgroundColor: ACCENT },
  textClip: { overflow: 'hidden' },
  title: { color: INK, fontSize: 26, fontWeight: '700', letterSpacing: -0.5 },
  body: { color: MUTE, fontSize: 15, lineHeight: 21, fontWeight: '500', marginTop: 6 },
  cta: {
    position: 'absolute',
    left: 20,
    right: 20,
    height: CTA_H,
    borderRadius: CTA_H / 2,
    borderCurve: 'continuous',
    backgroundColor: ACCENT,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(120,50,20,0.3), 0 10px 22px rgba(212,100,58,0.28), inset 0 1px 0 rgba(255,255,255,0.3)',
  },
  ctaClip: { height: 22, overflow: 'hidden' },
  ctaText: { height: 22, lineHeight: 22, color: '#FFF8F0', fontSize: 17, fontWeight: '700', textAlign: 'center' },
});
