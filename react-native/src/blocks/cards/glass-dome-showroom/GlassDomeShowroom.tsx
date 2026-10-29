import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, TileMode, matchFont, useImage } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import { CAR_IMAGES, CARS, drawBackground, drawHeadline, drawJar, hexColor, makeHeadline, washAt } from './glassDomeArt';
import {
  beginTouch,
  CENTRE_X,
  endTouch,
  floorYOf,
  HEADLINE_PITCH,
  indexOf,
  initialTouch,
  isLiked,
  jars,
  moveTouch,
  phiAt,
  popAge,
  restsAfter,
  roundAway,
  SPACING,
  wrap,
} from './glassDomeMotion';

/*
 Glass Dome Showroom — the line-up of Nuvra, an invented electric car maker. Each car stands under a
 tall glass bell jar; the jars ride a carousel with the featured car in the middle and its neighbours
 peeking in at the edges. The glass reads as glass: a bright rim, vertical specular streaks that
 slide across it as the jar moves, a lighter refracted copy of the wash inside, the far and near rims
 of its base around the car, a contact shadow and a mirror image on the glossy floor. Each car brings
 its own full-screen colour wash, the giant MORE stack rolls up one row per car, and the carousel
 smears with its speed (LOOP = 10 s).

 Swipe to change car (it carries the finger's speed and snaps to a jar); tap a side jar to bring it
 to the middle; tap the heart to like the car. 2 s after the last touch the carousel springs back to
 the first car and the loop restarts. Reduced motion: the first car, still.
 */

const FAMILY = Platform.OS === 'ios' ? 'System' : 'sans-serif';

function haptic(kind: number) {
  if (process.env.EXPO_OS === 'web' || kind === 0) return;
  if (kind === 2) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = {
  /** Plays the A → B → C → D → E → A round on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function GlassDomeShowroom({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const [selected, setSelected] = useState(0);
  const [selectedLiked, setSelectedLiked] = useState(false);
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
  const floorY = floorYOf(designHeight, safeBottom);
  const playing = autoPlay && !reduced;

  const img0 = useImage(CAR_IMAGES[0]);
  const img1 = useImage(CAR_IMAGES[1]);
  const img2 = useImage(CAR_IMAGES[2]);
  const img3 = useImage(CAR_IMAGES[3]);
  const img4 = useImage(CAR_IMAGES[4]);
  const images = useMemo(() => [img0, img1, img2, img3, img4], [img0, img1, img2, img3, img4]);
  const fonts = useMemo(() => {
    const head = matchFont({ fontFamily: FAMILY, fontSize: 76, fontWeight: '900' });
    const bold = matchFont({ fontFamily: FAMILY, fontSize: 20, fontWeight: '700' });
    const regular = matchFont({ fontFamily: FAMILY, fontSize: 20, fontWeight: '400' });
    return { head, headline: makeHeadline(head), jar: { bold, regular } };
  }, []);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);
  const likeTick = useSharedValue(0);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const t = touch.get();
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

  /** φ now, and φ 60 ms ago (the headline follows the jars). */
  const phi = useDerivedValue(() => (reduced ? 0 : phiAt(touch.get(), clock.get(), playing)));
  const speedOf = (c: number) => {
    'worklet';
    if (reduced) return 0;
    const t = touch.get();
    if (t.touching) return t.dragVelocity;
    return wrap(phiAt(t, c, playing) - phiAt(t, c - 1 / 240, playing)) * 240;
  };

  useAnimatedReaction(
    () => {
      likeTick.get();
      const i = indexOf(phi.get());
      return i * 2 + (isLiked(touch.get(), i, clock.get(), playing) ? 1 : 0);
    },
    (value, previous) => {
      if (value === previous) return;
      scheduleOnRN(setSelected, value >> 1);
      scheduleOnRN(setSelectedLiked, (value & 1) === 1);
    },
  );

  const picture = useDerivedValue(() => {
    likeTick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const c = clock.get();
    const p = phi.get();
    const t = touch.get();
    canvas.save();
    canvas.scale(k, k);
    drawBackground(canvas, 393, designHeight, p, floorY);

    // The headline, 60 ms behind the jars, with a vertical smear.
    const hp = reduced ? 0 : phiAt(t, c - 0.06, playing);
    const headSmear = Math.min(9, 0.03 * Math.abs(speedOf(c - 0.06)) * HEADLINE_PITCH);
    canvas.save();
    canvas.translate(0, safeTop);
    drawHeadline(canvas, fonts.head, fonts.headline, (hp - Math.floor(hp)) * HEADLINE_PITCH, headSmear * 0.5);
    canvas.restore();

    // The jars, smeared horizontally with the carousel's speed.
    const jarSmear = Math.min(18, 0.018 * Math.abs(speedOf(c)) * SPACING);
    const layer = Skia.Paint();
    if (jarSmear > 0.35) layer.setImageFilter(Skia.ImageFilter.MakeBlur(jarSmear * 0.5, 0.01, TileMode.Decal, null));
    canvas.saveLayer(layer, Skia.XYWHRect(0, 0, 393, designHeight));
    const list = jars(p);
    const washTop = washAt(p, 0);
    for (let n = 0; n < list.length; n++) {
      const jar = list[n];
      const age = reduced ? 10 : popAge(t, jar.index, c, playing);
      drawJar(canvas, jar, floorY, washTop, isLiked(t, jar.index, c, playing), age, images[jar.index], fonts.jar);
    }
    canvas.restore();
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin(() => {
          touch.set(beginTouch(touch.get(), clock.get(), playing));
          if (!autoPlay && !reduced) {
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onUpdate((e) => {
          if (reduced) return;
          const result = moveTouch(touch.get(), e.translationX / k, e.translationY / k, clock.get());
          touch.set(result.next);
          if (result.detent) scheduleOnRN(haptic, 1);
        })
        .onFinalize((e) => {
          if (!touch.get().touching) return;
          const result = endTouch(touch.get(), clock.get(), reduced ? 0 : e.velocityX / k, e.x / k, e.y / k, floorY);
          touch.set(result.next);
          likeTick.set(likeTick.get() + 1);
          scheduleOnRN(haptic, result.feedback);
        }),
    [reduced, autoPlay, playing, k, floorY, touch, clock, wake, asleep, likeTick],
  );

  const infoStyle = useAnimatedStyle(() => {
    const p = phi.get();
    return { opacity: 1 - 2 * Math.abs(p - roundAway(p)) };
  });
  const ctaTextStyle = useAnimatedStyle(() => ({ color: hexColor(washAt(phi.get(), 2)) }));

  const u = (v: number) => v * k;
  const car = CARS[selected];

  return (
    <View style={[styles.screen, { backgroundColor: hexColor(CARS[0].mid) }]}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`The new ${car.model} ${car.trim}${selectedLiked ? ', liked' : ''}`}
          accessibilityHint="Swipe left or right to see another car"
        >
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.sub, { top: u(safeTop + 238) }]} pointerEvents="none">
        <Text style={[styles.subText, { fontSize: u(18), lineHeight: u(22) }]} numberOfLines={1} allowFontScaling={false}>
          Nuvra Electric
        </Text>
        <Text style={[styles.subText, { fontSize: u(18), lineHeight: u(22) }]} numberOfLines={1} allowFontScaling={false}>
          more miles for your money
        </Text>
      </View>

      <Animated.Text
        style={[styles.info, { top: u(floorY + 56 - 9), fontSize: u(14) }, infoStyle]}
        pointerEvents="none"
        numberOfLines={1}
        allowFontScaling={false}
      >
        {car.info}
      </Animated.Text>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Book a test drive"
        onPress={() => haptic(1)}
        style={[styles.cta, { left: u(CENTRE_X - 110), top: u(floorY + 76), width: u(220), height: u(52), borderRadius: u(26), shadowRadius: u(10), shadowOffset: { width: 0, height: u(4) } }]}
      >
        <Animated.Text style={[styles.ctaText, { fontSize: u(17) }, ctaTextStyle]} numberOfLines={1} allowFontScaling={false}>
          Book a test drive
        </Animated.Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  sub: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  subText: { color: '#FFFFFF', fontWeight: '600', textAlign: 'center' },
  info: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: 'rgba(255,255,255,0.78)', fontWeight: '600', fontVariant: ['tabular-nums'] },
  cta: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.96)',
    shadowColor: '#000000',
    shadowOpacity: 0.18,
    elevation: 6,
  },
  ctaText: { fontWeight: '600' },
});
