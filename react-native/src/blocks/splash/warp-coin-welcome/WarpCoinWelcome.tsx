import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Canvas, Group, Path, Picture, Skia } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import { drawScene, markPaths } from './warpCoinArt';
import { beginTouch, endTouch, IDLE, initialTouch, moveTouch, restFrame, stepFrame, vanishY } from './warpCoinMotion';

/*
 Warp Coin Welcome — the welcome screen of Arcwell, an invented bank. A slow star field drifts out
 of a vanishing point behind a large brushed-silver coin; the warp spins up, the coin dives into
 it, comes back out of the warp toward the viewer, swoops close and lands with a settle while the
 streaks slow back down to stars (LOOP = 7 s). 340 seeded particles in a tunnel are drawn as lines
 from their previous to their current projected position, so speed is length; the coin is a thick
 disc with a reeded rim, a concentric brushed face whose highlight follows the light and an
 embossed mark.

 Drag up to warp faster (down drifts back); the coin tilts with the drag. Tap the coin to spin it
 once. 2 s after the last touch the loop starts again from rest. Reduced motion: the rest pose, still.
 */

const BG = '#05060B';
const HEADLINE = ['BANKING AT', 'THE SPEED', 'OF LIGHT'];

function haptic(strong: boolean) {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
}

function Logo() {
  const paths = useMemo(() => markPaths(), []);
  return (
    <Canvas style={styles.logo}>
      <Group transform={[{ translateX: 9 }, { translateY: 9 }, { scale: 18 }]}>
        <Path path={paths.arch} style="stroke" strokeWidth={0.2} strokeCap="butt" strokeJoin="round" color="white" />
        <Path path={paths.bar} style="stroke" strokeWidth={0.12} strokeCap="butt" color="white" />
      </Group>
    </Canvas>
  );
}

/** The static product screen over the scene: page indicator, header, headline and the two buttons. */
function Chrome({ top, bottom }: { top: number; bottom: number }) {
  return (
    <View style={[styles.chrome, { paddingTop: top, paddingBottom: bottom }]} pointerEvents="box-none">
      <View style={styles.indicator} pointerEvents="none">
        {[0, 1, 2, 3, 4].map((i) => (
          <View key={i} style={[styles.segment, { opacity: i === 0 ? 0.9 : 0.22 }]} />
        ))}
      </View>
      <View style={styles.header} pointerEvents="none">
        <Logo />
        <Text style={styles.welcome}>Welcome to Arcwell</Text>
      </View>
      <View style={styles.headline} pointerEvents="none" accessible accessibilityRole="header" accessibilityLabel="Banking at the speed of light">
        {HEADLINE.map((line) => (
          <View key={line} style={styles.line}>
            <Text style={styles.lineText} numberOfLines={1}>
              {line}
            </Text>
          </View>
        ))}
      </View>
      <View style={styles.spacer} pointerEvents="none" />
      <View style={styles.buttons}>
        <Pressable accessibilityRole="button" style={[styles.button, styles.login]}>
          <Text style={[styles.buttonText, styles.loginText]}>Log in</Text>
        </Pressable>
        <Pressable accessibilityRole="button" style={[styles.button, styles.signup]}>
          <Text style={[styles.buttonText, styles.signupText]}>Sign up</Text>
        </Pressable>
      </View>
    </View>
  );
}

type Props = {
  /** Plays the rest → warp → dive → return → land story on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function WarpCoinWelcome({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const k = width / 393;
  const designHeight = height / k;
  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const frame = useSharedValue(restFrame());
  const asleep = useSharedValue(false);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const step = stepFrame(now, touch.get(), autoPlay);
    touch.set(step.next);
    frame.set(step.frame);
    const t = step.next;
    if (!autoPlay && !asleep.get() && !t.touching && t.release >= 0 && now > t.release + IDLE + 1.2) {
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
    if (reduced) frame.set(restFrame());
  }, [reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.scale(k, k);
    drawScene(canvas, designHeight, frame.get());
    return recorder.finishRecordingAsPicture();
  });

  const coinY = vanishY(designHeight) + 162;
  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .enabled(!reduced)
        .onBegin((e) => {
          touch.set(beginTouch(touch.get(), e.x / k, e.y / k, clock.get()));
          if (!autoPlay) {
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onUpdate((e) => {
          const result = moveTouch(touch.get(), e.x / k, e.y / k);
          touch.set(result.next);
          if (result.full) scheduleOnRN(haptic, true);
        })
        .onFinalize(() => {
          if (!touch.get().touching) return;
          const result = endTouch(touch.get(), clock.get(), coinY);
          touch.set(result.next);
          scheduleOnRN(haptic, result.tapped);
        }),
    [reduced, autoPlay, k, coinY, touch, clock, wake, asleep],
  );

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="image" accessibilityLabel="Arcwell. Banking at the speed of light.">
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <Chrome top={insets.top} bottom={insets.bottom} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG },
  chrome: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 },
  indicator: { flexDirection: 'row', gap: 5, paddingHorizontal: 12, marginTop: 10 },
  segment: { flex: 1, height: 2, borderRadius: 1, backgroundColor: '#FFFFFF' },
  header: { flexDirection: 'row', alignItems: 'center', gap: 8, height: 20, marginTop: 10, paddingLeft: 16 },
  logo: { width: 18, height: 18 },
  welcome: { color: '#FFFFFF', fontSize: 15 },
  headline: { paddingLeft: 24, marginTop: 14 },
  line: { height: 36, justifyContent: 'center' },
  lineText: { color: '#FFFFFF', fontSize: 34, lineHeight: 40, fontWeight: '900' },
  spacer: { flex: 1 },
  buttons: { flexDirection: 'row', gap: 8, paddingHorizontal: 16, marginBottom: 16 },
  button: { flex: 1, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
  login: { backgroundColor: '#1E2127' },
  signup: { backgroundColor: '#F4F5F7' },
  buttonText: { fontSize: 17, fontWeight: '600' },
  loginText: { color: '#FFFFFF' },
  signupText: { color: '#0A0B0E' },
});
