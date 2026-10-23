/** The reel: four stills with a slow push-in and a cross-fade, captions scaled with the frame. */
import { BlurView } from 'expo-blur';
import { Image } from 'expo-image';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, FadeIn, FadeInDown, FadeOut, FadeOutUp, useAnimatedStyle, useDerivedValue, withTiming, type SharedValue } from 'react-native-reanimated';

import { previewFrame } from './layout';
import { LOOP_MS, RESIZE, SIZES, TIMING, UPGRADE } from './motion';
import { CLIPS } from './reel';
import { ASPECT, BADGE, COLOR, SPACE, TYPE } from './theme';

const SIZE_NAMES = ['Compact', 'Large', 'Full screen'] as const;

const CLIP = TIMING.clip / 1000;
const FADE = TIMING.crossfade / 1000;
const LOOP = LOOP_MS / 1000;
const MAX = SIZES[SIZES.length - 1];
const FULL_W = MAX * ASPECT;

type Props = {
  shown: SharedValue<number>; held: SharedValue<number>; press: SharedValue<number>; clock: SharedValue<number>;
  width: number; topInset: number; clip: number; near: number; reduceMotion: boolean;
};

function Layer({ index, clock, reduceMotion }: { index: number; clock: SharedValue<number>; reduceMotion: boolean }) {
  const style = useAnimatedStyle(() => {
    const c = clock.get();
    const start = index * CLIP - FADE;
    // Clip 0 sits on top: it fades out into clip 1 and back in over the last clip, so the loop is seamless.
    let opacity: number;
    if (index === 0) opacity = c < CLIP ? 1 - Math.min(Math.max((c - (CLIP - FADE)) / FADE, 0), 1) : Math.min(Math.max((c - (LOOP - FADE)) / FADE, 0), 1);
    else if (index === 1) opacity = c >= start && c < (index + 1) * CLIP ? 1 : 0;
    else opacity = c >= start && c < (index + 1) * CLIP ? Math.min((c - start) / FADE, 1) : 0;
    const local = (((c - start) % LOOP) + LOOP) % LOOP;
    const push = reduceMotion ? 0 : TIMING.kenBurns * Math.min(local / (CLIP + FADE), 1);
    return { opacity, transform: [{ scale: 1 + push }] };
  });
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]}>
      <Image source={CLIPS[index].art} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
    </Animated.View>
  );
}

// Clip 0 last, so it is drawn on top.
const ORDER = [1, 2, 3, 0];

export function Preview({ shown, held, press, clock, width, topInset, clip, near, reduceMotion }: Props) {
  const frame = useAnimatedStyle(() => {
    const p = previewFrame(shown.get(), width);
    return {
      top: p.top, left: p.left, width: p.width, height: p.height, borderRadius: p.radius,
      transform: [{ scale: 1 - (1 - UPGRADE.press) * press.get() * (1 - held.get()) - (1 - RESIZE.lift) * held.get() }],
    };
  });
  // Lifted, the frame casts a deeper shadow and catches light on its rim (one key light from above).
  const shadow = useAnimatedStyle(() => {
    // A full-screen plate scaled onto the frame: transforms only, no layout per frame.
    const p = previewFrame(shown.get(), width);
    const s = 1 - (1 - RESIZE.lift) * held.get();
    const sx = (p.width / width) * s;
    const sy = (p.height / MAX) * s;
    return {
      shadowOpacity: UPGRADE.liftShadow.opacity * held.get(),
      transform: [
        { translateX: p.left + p.width / 2 - width / 2 },
        { translateY: p.top + p.height / 2 - MAX / 2 + UPGRADE.liftShadow.y * held.get() * 0.5 },
        { scaleX: sx }, { scaleY: sy },
      ],
    };
  });
  const rim = useAnimatedStyle(() => ({ opacity: UPGRADE.rim * held.get(), borderRadius: previewFrame(shown.get(), width).radius }));
  // The size badge: in fast while held, out slower after the release (asymmetric).
  const badgeOn = useDerivedValue(() => withTiming(held.get() > 0.5 ? 1 : 0, { duration: held.get() > 0.5 ? UPGRADE.badgeInMs : UPGRADE.badgeOutMs, easing: Easing.out(Easing.cubic) }));
  const badge = useAnimatedStyle(() => ({
    opacity: badgeOn.get(),
    transform: [{ translateY: UPGRADE.badgeRise * (1 - badgeOn.get()) }, { scale: 0.96 + 0.04 * badgeOn.get() }],
  }));
  const caption = useAnimatedStyle(() => {
    const w = shown.get() * ASPECT;
    return { left: (w - FULL_W) / 2, transform: [{ scale: shown.get() / MAX }] };
  });
  return (
    <>
    <Animated.View style={[styles.shadow, { width, height: MAX }, shadow]} pointerEvents="none" />
    <Animated.View style={[styles.preview, frame]}>
      {ORDER.map((i) => <Layer key={i} index={i} clock={clock} reduceMotion={reduceMotion} />)}
      <Animated.View style={[styles.captionBox, caption]} pointerEvents="none">
        <Animated.View key={clip} entering={FadeIn.duration(TIMING.crossfade)} exiting={FadeOut.duration(TIMING.crossfade)}>
          <Text style={styles.caption}>{CLIPS[clip].caption}</Text>
        </Animated.View>
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, styles.rim, rim]} pointerEvents="none" />
      <Animated.View style={[styles.badgeWrap, badge]} pointerEvents="none">
        <BlurView intensity={BADGE.blur} tint="dark" style={styles.badge}>
          <View style={styles.dots}>
            {SIZE_NAMES.map((n, i) => <View key={n} style={[styles.dot, i === near && styles.dotOn]} />)}
          </View>
          <Animated.Text key={near} entering={reduceMotion ? FadeIn.duration(150) : FadeInDown.duration(160)} exiting={reduceMotion ? FadeOut.duration(120) : FadeOutUp.duration(120)} style={styles.badgeText}>
            {SIZE_NAMES[near]}
          </Animated.Text>
        </BlurView>
      </Animated.View>
    </Animated.View>
    </>
  );
}

const styles = StyleSheet.create({
  preview: { position: 'absolute', overflow: 'hidden', backgroundColor: COLOR.chrome, borderCurve: 'continuous' },
  shadow: {
    position: 'absolute', top: 0, left: 0, backgroundColor: COLOR.background, borderRadius: 24, borderCurve: 'continuous',
    // The plate is scaled to about half, so its shadow is drawn at twice the radius.
    shadowColor: COLOR.background, shadowRadius: UPGRADE.liftShadow.radius * 2, shadowOffset: { width: 0, height: UPGRADE.liftShadow.y }, shadowOpacity: 0,
  },
  rim: { borderWidth: StyleSheet.hairlineWidth * 2, borderColor: COLOR.rim, borderCurve: 'continuous' },
  badgeWrap: { position: 'absolute', left: 0, right: 0, bottom: BADGE.bottom, alignItems: 'center' },
  badge: {
    height: BADGE.height, borderRadius: BADGE.height / 2, overflow: 'hidden', paddingHorizontal: BADGE.padX,
    flexDirection: 'row', alignItems: 'center', gap: BADGE.gap, backgroundColor: COLOR.badge,
    borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.18)',
  },
  dots: { flexDirection: 'row', gap: SPACE.xs },
  dot: { width: BADGE.dot, height: BADGE.dot, borderRadius: BADGE.dot / 2, backgroundColor: COLOR.badgeDot },
  dotOn: { backgroundColor: COLOR.ink },
  badgeText: { ...TYPE.time, color: COLOR.ink, fontWeight: '600' },
  // Captions are laid out at full-screen size and scaled with the frame, like burned-in text.
  captionBox: { position: 'absolute', width: FULL_W, top: '62%', alignItems: 'center', height: 40 },
  caption: {
    ...TYPE.caption, fontSize: 20, lineHeight: 26, color: COLOR.caption, textAlign: 'center',
    textShadowColor: COLOR.captionShadow, textShadowRadius: 6, textShadowOffset: { width: 0, height: 1 },
  },
});
