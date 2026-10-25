import { BlurMask, Canvas, Circle, Fill, Image, ImageShader, Path, RadialGradient, Shader, Skia, useImage, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { bakeAtlas, bakeProjector, hubPath, KEY_BOTTOM, KEY_TOP, layoutProjector, SLIDES, TILE_H, TILE_W, trayMounts } from './projectorArt';
import { autoPose, CHANGE, changePose, LOOP, mote, type ProjPose, restPose, smoothstep, TRAY_SLOTS } from './projectorMotion';
import { PROJECTOR_SKSL } from './projectorShader';

/*
 Slide Projector Gallery — a photo-story screen ("Carousel Night — Summer '87"): a carousel slide
 projector at the bottom throws the current slide onto a dark living-room wall, through a visible
 beam with drifting dust. Changing slides: the picture goes dark, the lamp stutters, the round
 tray turns one slot, the next slide drops in and the focus pulls from blurry to sharp. The wall,
 the slide and the beam are one shader (projectorShader.ts); the timeline is projectorMotion.ts
 (LOOP = 9 s).

 Touch: swipe left / right (or the chevrons) for the next / previous slide, with the same change
 as the loop; past the first or last slide nothing changes. Pinch to turn the focus ring: the
 spread from where the fingers landed defocuses either way, and a tick marks sharp focus. 2 s
 after the last touch the tray goes back to the first slide, focus sharp, and the loop restarts.
 Reduced motion: the first slide, sharp and still; a change becomes a plain dim-and-back fade.
 */

type Props = {
  /** Shows three slide changes and goes back to the first slide, on a loop. */
  autoPlay?: boolean;
};

const BG = '#1A1411';
const INK = '#F3E8DA';
const MUTE = 'rgba(243,232,218,0.56)';
const ACCENT = '#F0B25E';
const HOLD_AFTER_TOUCH = 2;
const MOTES = 64;
const BOKEH = 7;
const ROW = 46;

function feel(kind: 'tick' | 'change' | 'edge') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'change') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
}

function Chevron({ dir }: { dir: -1 | 1 }) {
  return (
    <Svg width={18} height={18} viewBox="0 0 18 18">
      <SvgPath d={dir < 0 ? 'M11.5 3.5 L6 9 L11.5 14.5' : 'M6.5 3.5 L12 9 L6.5 14.5'} stroke={INK} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** One caption in the clipped caption box: rolls up and fades as the next rolls in from below. */
function CaptionRow({ k, roll, title, meta }: { k: number; roll: SharedValue<number>; title: string; meta: string }) {
  const style = useAnimatedStyle(() => {
    const d = k - roll.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.8), transform: [{ translateY: d * ROW }] };
  });
  return (
    <Animated.View style={[styles.captionRow, { height: ROW }, style]}>
      <Text style={styles.captionTitle} numberOfLines={1} maxFontSizeMultiplier={1.1}>
        {title}
      </Text>
      <Text style={styles.captionMeta} numberOfLines={1} maxFontSizeMultiplier={1.1}>
        {meta}
      </Text>
    </Animated.View>
  );
}

export function SlideProjectorGallery({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const s0 = useImage(require('./assets/seaside.jpg'));
  const s1 = useImage(require('./assets/umbrellas.jpg'));
  const s2 = useImage(require('./assets/picnic.jpg'));
  const s3 = useImage(require('./assets/lifeguard.jpg'));
  const s4 = useImage(require('./assets/chrome.jpg'));
  const atlas = useMemo(() => (s0 && s1 && s2 && s3 && s4 ? bakeAtlas([s0, s1, s2, s3, s4]) : null), [s0, s1, s2, s3, s4]);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(PROJECTOR_SKSL), []);
  const L = useMemo(() => (size ? layoutProjector(size.w, size.h, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const body = useMemo(() => (L ? bakeProjector(L, Math.min(3, PixelRatio.get())) : null), [L]);
  const hub = useMemo(() => (L ? hubPath(L) : null), [L]);

  const PR = L?.proj ?? { cx: 0, top: 0, w: 1, h: 1 };
  const LN = L?.lens ?? { x: 0, y: 0 };
  const T = L?.tray ?? { cx: 0, cy: 0, rx: 1, ry: 1, side: 0 };
  const p3 = Platform.OS === 'ios' ? 1 : 0;

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  /** 0 = autoPlay, 1 = the finger's slides, 2 = going back to the first slide. */
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const pinching = useSharedValue(0);
  const chFrom = useSharedValue(0);
  const chTo = useSharedValue(0);
  const chStart = useSharedValue(-100);
  const userFocus = useSharedValue(0);
  const pinchBase = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
    } else if (m === 1 && pinching.get() === 0 && now.get() - lastTouch.get() > HOLD_AFTER_TOUCH && now.get() - chStart.get() > CHANGE) {
      mode.set(2);
      userFocus.set(withTiming(0, { duration: 500 }));
      if (chTo.get() !== 0) {
        chFrom.set(chTo.get());
        chTo.set(0);
        chStart.set(now.get());
      }
    } else if (m === 2 && now.get() - chStart.get() > CHANGE && Math.abs(userFocus.get()) < 0.01) {
      clock.set(0);
      mode.set(0);
    }
  });

  const pose = useDerivedValue<ProjPose>(() => {
    if (mode.get() === 0) return reduced ? restPose(0) : autoPose(clock.get());
    const u = (now.get() - chStart.get()) / CHANGE;
    let p = u >= 0 && u < 1 ? changePose(u, chFrom.get(), chTo.get()) : restPose(chTo.get());
    if (reduced && u >= 0 && u < 1) {
      const r = restPose(u < 0.5 ? chFrom.get() : chTo.get());
      r.bright = Math.abs(1 - 2 * u);
      p = r;
    }
    const uf = Math.abs(userFocus.get());
    if (uf > p.focus) {
      p.focus = uf;
      p.scale = 1 + 0.03 * Math.min(1, uf);
    }
    return p;
  });

  /** Take over from the loop where it is (on the nearest whole slide). */
  function takeOver() {
    'worklet';
    if (mode.get() === 1) return;
    const cur = mode.get() === 0 ? Math.round(pose.get().tray) : chTo.get();
    chFrom.set(cur);
    chTo.set(cur);
    chStart.set(-100);
    mode.set(1);
  }

  function step(dir: number) {
    'worklet';
    takeOver();
    lastTouch.set(now.get());
    if (now.get() - chStart.get() < CHANGE * 0.6) return;
    const cur = chTo.get();
    const to = Math.max(0, Math.min(SLIDES.length - 1, cur + dir));
    if (to === cur) {
      scheduleOnRN(feel, 'edge');
      return;
    }
    chFrom.set(cur);
    chTo.set(to);
    chStart.set(now.get());
    scheduleOnRN(feel, 'change');
  }

  const swipe = Gesture.Pan()
    .activeOffsetX([-14, 14])
    .failOffsetY([-24, 24])
    .onEnd((e) => {
      if (Math.abs(e.translationX) > 44 || Math.abs(e.velocityX) > 500) step(e.translationX < 0 ? 1 : -1);
    });
  const pinch = Gesture.Pinch()
    .onBegin(() => {
      takeOver();
      pinching.set(1);
      pinchBase.set(userFocus.get());
      lastTouch.set(now.get());
    })
    .onUpdate((e) => {
      lastTouch.set(now.get());
      userFocus.set(Math.max(-1, Math.min(1, pinchBase.get() + (e.scale - 1) * 1.6)));
    })
    .onFinalize(() => {
      pinching.set(0);
      lastTouch.set(now.get());
    });
  const gestures = Gesture.Simultaneous(swipe, pinch);

  // A tick when a pinch brings the picture into focus.
  useAnimatedReaction(
    () => (pinching.get() === 1 ? (Math.abs(userFocus.get()) < 0.06 ? 1 : 0) : -1),
    (cur, prev) => {
      if (cur === 1 && prev === 0) scheduleOnRN(feel, 'tick');
    },
  );

  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    return {
      size: [L?.W ?? 1, L?.H ?? 1],
      proj: [PR.cx, PR.top, PR.w, PR.h],
      key: [KEY_TOP, KEY_BOTTOM],
      lens: [LN.x, LN.y],
      slide: [p.slide, p.bright, p.drop, p.focus],
      look: [p.flicker, p.scale, p3, 0],
      tile: [TILE_W, TILE_H],
    };
  });

  const mounts = useDerivedValue(() => trayMounts(T, pose.get().tray, TRAY_SLOTS));
  const farMounts = useDerivedValue(() => mounts.get()[0]);
  const nearMounts = useDerivedValue(() => mounts.get()[1]);

  // Dust in the beam: one path per brightness band, plus a few soft out-of-focus motes.
  const dust = useDerivedValue(() => {
    const t = mode.get() === 0 ? clock.get() : now.get() % LOOP;
    const p = pose.get();
    const lit = p.bright * p.flicker;
    const bands = [Skia.PathBuilder.Make(), Skia.PathBuilder.Make(), Skia.PathBuilder.Make(), Skia.PathBuilder.Make()];
    const half = PR.w * 0.5 * KEY_TOP;
    for (let i = 0; i < MOTES + BOKEH; i += 1) {
      const m = mote(i, reduced ? 0 : t, LN.x, LN.y, PR.top + PR.h * 0.6, half);
      const b = m[3] * lit;
      if (b < 0.08) continue;
      if (i >= MOTES) bands[3].addCircle(m[0], m[1], 2.6 + m[2] * 1.4);
      else bands[b > 0.6 ? 2 : b > 0.3 ? 1 : 0].addCircle(m[0], m[1], m[2]);
    }
    // A plain loop: an inline arrow here would be hoisted out of the worklet by the React Compiler.
    return [bands[0].build(), bands[1].build(), bands[2].build(), bands[3].build()];
  });
  const dust0 = useDerivedValue(() => dust.get()[0]);
  const dust1 = useDerivedValue(() => dust.get()[1]);
  const dust2 = useDerivedValue(() => dust.get()[2]);
  const dust3 = useDerivedValue(() => dust.get()[3]);
  const lensGlow = useDerivedValue(() => pose.get().bright * pose.get().flicker);

  /** The caption's roll position: the tray's slot, rolling over the middle 40 % of a change. */
  const roll = useDerivedValue(() => {
    const tr = pose.get().tray;
    const base = Math.floor(tr);
    return base + smoothstep(0.3, 0.7, tr - base);
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const tap = (dir: number) => scheduleOnUI(step, dir);

  return (
    <View style={[styles.root, { backgroundColor: BG }]} onLayout={onLayout}>
      <GestureDetector gesture={gestures}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A slide projector showing summer photos on a wall. Swipe for the next slide, pinch to focus.">
          {L && effect && atlas && body ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms}>
                  <ImageShader image={atlas} tx="clamp" ty="clamp" />
                </Shader>
              </Fill>
              <Path path={dust0} color="rgba(255,236,200,0.35)" />
              <Path path={dust1} color="rgba(255,238,206,0.6)" />
              <Path path={dust2} color="rgba(255,244,222,0.9)" />
              <Path path={dust3} color="rgba(255,236,200,0.14)">
                <BlurMask blur={2.4} style="normal" />
              </Path>
              <Image image={body} x={0} y={0} width={L.W} height={L.H} />
              <Path path={farMounts} color="#7E7465" />
              {hub ? (
                <Path path={hub}>
                  <RadialGradient c={vec(T.cx - T.rx * 0.12, T.cy - T.ry * 0.2)} r={T.rx * 0.5} colors={['#5B5450', '#2E2A28', '#1C1918']} positions={[0, 0.6, 1]} />
                </Path>
              ) : null}
              <Path path={nearMounts} color="#E8DCC5" />
              <Circle cx={LN.x} cy={LN.y} r={28} opacity={lensGlow}>
                <RadialGradient c={vec(LN.x, LN.y)} r={28} colors={['rgba(255,240,205,0.85)', 'rgba(255,214,150,0.25)', 'rgba(255,200,130,0)']} positions={[0, 0.35, 1]} />
              </Circle>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <Text style={styles.kicker}>CAROUSEL NIGHT</Text>
        <Text style={styles.title}>Summer &apos;87</Text>
        <Text style={styles.sub}>From Aunt Ivy&apos;s tray · 5 slides · 35 mm</Text>
      </View>

      {L ? (
        <>
          <View pointerEvents="none" style={[styles.captionClip, { top: L.captionY, height: ROW }]}>
            {SLIDES.map((s, k) => (
              <CaptionRow key={k} k={k} roll={roll} title={s.title} meta={`${k + 1} of 5 · ${s.meta}`} />
            ))}
          </View>
          <View style={[styles.controls, { top: L.lens.y - 34 }]} pointerEvents="box-none">
            <Pressable accessibilityRole="button" accessibilityLabel="Previous slide" hitSlop={8} onPress={() => tap(-1)} style={({ pressed }) => [styles.round, { transform: [{ scale: pressed ? 0.94 : 1 }] }]}>
              <Chevron dir={-1} />
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel="Next slide" hitSlop={8} onPress={() => tap(1)} style={({ pressed }) => [styles.round, { transform: [{ scale: pressed ? 0.94 : 1 }] }]}>
              <Chevron dir={1} />
            </Pressable>
          </View>
          <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 12 }]}>
            Swipe to change slides · pinch to focus
          </Text>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kicker: { color: ACCENT, fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.6, marginTop: 4 },
  sub: { color: MUTE, fontSize: 14, fontWeight: '500', marginTop: 2 },
  captionClip: { position: 'absolute', left: 24, right: 24, overflow: 'hidden' },
  captionRow: { position: 'absolute', left: 0, right: 0, top: 0, justifyContent: 'center', alignItems: 'center', overflow: 'hidden' },
  captionTitle: { color: INK, fontSize: 17, lineHeight: 22, fontWeight: '600', letterSpacing: -0.2 },
  captionMeta: { color: MUTE, fontSize: 12, lineHeight: 16, fontWeight: '500', fontVariant: ['tabular-nums'] },
  controls: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', justifyContent: 'space-between' },
  round: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(243,232,218,0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(243,232,218,0.22)',
  },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: 'rgba(243,232,218,0.5)', fontSize: 12, fontWeight: '500' },
});
