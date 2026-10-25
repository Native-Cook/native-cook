import { BlurMask, Canvas, Fill, Group, Path, Picture, Shader, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo } from 'react';
import { Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { buildTeeth, recordPatch, recordSlider, recordStop, recordTab, tabPath } from './zipperArt';
import { ZipperJournal } from './ZipperJournal';
import {
  IDLE_BEFORE_RETURN,
  LOOP,
  OPEN_AT,
  TAB_PIVOT,
  clamp,
  easeInOut,
  easeOut,
  gapAt,
  makeZip,
  script,
  sliderY,
  smooth,
} from './zipperMotion';
import { DENIM_SKSL } from './zipperShader';

/*
 Zipper Unlock — the lock screen of "Pocket", a private journal: a denim cover with a brass zipper
 down the middle. Pull the tab down and the teeth part in a V above the slider while the two
 halves roll back at their edges, showing the journal underneath. The loop is in zipperMotion.ts.

 Touch: the tab follows the finger (the slider rides the finger's height, rubber-banded past both
 stops); the tab points at the finger on a stiff spring (ω 24, ζ 0.9) and swings free as a
 pendulum when let go (ω 7, ζ 0.16). Released past 70 % (or flicked so its projection passes it)
 the slider springs to the bottom (ω 13, ζ 0.82) and the halves swing open in 0.6 s (ease-out);
 short of it, it zips back up on the same spring. A selection tick at 70 %, a light impact when
 the halves let go and when the zipper closes. 2 s after the last touch (or on the lock button)
 the halves come back in 0.35 s and the slider rises in 0.55 s, then the loop restarts.
 Reduced motion: no loop, critically damped springs, the halves fade open instead of swinging.
*/

type Props = {
  /** Plays the 9 s demo: grab, unzip, open the journal, zip back up. */
  autoPlay?: boolean;
};

const P3 = Platform.OS === 'ios' ? 1 : 0;
const FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const OPEN_FOR = 0.6;
const CLOSE_HALVES_FOR = 0.35;
const CLOSE_SLIDER_FOR = 0.55;

function feel(kind: number) {
  if (kind === 0) Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function ZipperUnlock({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const zip = useMemo(() => makeZip(width, height, insets.top, insets.bottom), [width, height, insets.top, insets.bottom]);
  const range = zip.ys1 - zip.ys0;
  const patchX = width * 0.25;
  const patchY = height * 0.34;

  const effect = useMemo(() => Skia.RuntimeEffect.Make(DENIM_SKSL), []);
  const art = useMemo(() => {
    const title = matchFont({ fontFamily: FAMILY, fontSize: 24, fontWeight: '800' });
    const small = matchFont({ fontFamily: FAMILY, fontSize: 8.5, fontWeight: '700' });
    return { slider: recordSlider(), tab: recordTab(), tabShape: tabPath(), stop: recordStop(), patch: recordPatch(title, small) };
  }, []);

  const clock = useSharedValue(0);
  const mode = useSharedValue(0); // 0 loop, 1 dragging, 2 settling, 3 closing
  const p = useSharedValue(0);
  const pV = useSharedValue(0);
  const o = useSharedValue(0);
  const oLin = useSharedValue(0);
  const lift = useSharedValue(0);
  const angle = useSharedValue(0);
  const angleV = useSharedValue(0);
  const since = useSharedValue(-1);
  const target = useSharedValue(0);
  const idle = useSharedValue(0);
  const ret = useSharedValue(0);
  const fromP = useSharedValue(0);
  const fromO = useSharedValue(0);
  const startYs = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const grabbing = useSharedValue(false);
  const armed = useSharedValue(false);

  useEffect(() => {
    clock.set(0);
    mode.set(0);
  }, [auto, clock, mode]);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m === 0) {
      if (!auto) {
        p.set(0);
        o.set(0);
        lift.set(0);
        angle.set(0);
        since.set(-1);
        return;
      }
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      const s = script(t);
      p.set(s[0]);
      o.set(s[1]);
      lift.set(s[2]);
      angle.set(s[3]);
      since.set(s[4]);
      return;
    }
    // The pull tab: aims at the finger while held, a free pendulum otherwise.
    const ys = sliderY(zip, p.get());
    const sx = zip.cx + (width / 2 + 70) * o.get();
    let aim = 0;
    let w = 7;
    let z = reduced ? 1 : 0.16;
    if (m === 1) {
      aim = clamp(Math.atan2(-(fingerX.get() - sx), Math.max(18, fingerY.get() - ys - TAB_PIVOT)), -1.1, 1.1);
      w = 24;
      z = 0.9;
    }
    const acc = -w * w * (angle.get() - aim) - 2 * z * w * angleV.get();
    angleV.set(angleV.get() + acc * dt);
    angle.set(angle.get() + angleV.get() * dt);
    lift.set(lift.get() + ((m === 1 ? 1 : 0) - lift.get()) * Math.min(1, dt * 12));

    if (m === 2) {
      const goal = target.get();
      const sw = 13;
      const sz = reduced ? 1 : 0.82;
      const a = -sw * sw * (p.get() - goal) - 2 * sz * sw * pV.get();
      pV.set(pV.get() + a * dt);
      p.set(p.get() + pV.get() * dt);
      if (goal === 1) p.set(Math.min(p.get(), 1.0));
      if (goal === 1 && p.get() > 0.985 && oLin.get() < 1) {
        if (oLin.get() === 0) scheduleOnRN(feel, 1);
        oLin.set(Math.min(1, oLin.get() + dt / OPEN_FOR));
        o.set(easeOut(oLin.get()));
      }
      if (goal === 0 && armed.get() && p.get() < 0.01) {
        armed.set(false);
        scheduleOnRN(feel, 1);
      }
      if (o.get() > 0) since.set(since.get() < 0 ? 0 : since.get() + dt);
      const settled = Math.abs(p.get() - goal) < 0.004 && Math.abs(pV.get()) < 0.03 && (goal === 0 || oLin.get() >= 1);
      if (settled) idle.set(idle.get() + dt);
      if (idle.get() > IDLE_BEFORE_RETURN) {
        mode.set(3);
        ret.set(0);
        fromP.set(p.get());
        fromO.set(o.get());
      }
    } else if (m === 3) {
      const r = ret.get() + dt;
      ret.set(r);
      o.set(fromO.get() * (1 - easeInOut(r / CLOSE_HALVES_FOR)));
      const k = fromO.get() > 0 ? CLOSE_HALVES_FOR : 0;
      p.set(fromP.get() * (1 - easeInOut((r - k) / CLOSE_SLIDER_FOR)));
      if (o.get() <= 0.001) since.set(-1);
      else since.set(since.get() + dt);
      if (r >= k + CLOSE_SLIDER_FOR) {
        mode.set(0);
        clock.set(0);
        oLin.set(0);
        o.set(0);
        p.set(0);
      }
    }
  });

  function rubber(raw: number) {
    'worklet';
    if (raw < 0) {
      const over = -raw * range;
      return -over / (1 + over / 60) / range;
    }
    if (raw > 1) {
      const over = (raw - 1) * range;
      return 1 + over / (1 + over / 40) / range;
    }
    return raw;
  }

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      // The tab can't be caught while the halves are swinging open.
      if (o.get() > 0.02) {
        grabbing.set(false);
        return;
      }
      grabbing.set(true);
      if (mode.get() === 0) {
        angleV.set(0);
        pV.set(0);
      }
      mode.set(1);
      startYs.set(sliderY(zip, p.get()));
      fingerX.set(e.absoluteX);
      fingerY.set(e.absoluteY);
    })
    .onUpdate((e) => {
      if (!grabbing.get()) return;
      const before = p.get();
      const next = rubber((startYs.get() + e.translationY - zip.ys0) / range);
      p.set(next);
      pV.set(e.velocityY / range);
      fingerX.set(e.absoluteX);
      fingerY.set(e.absoluteY);
      if ((before - OPEN_AT) * (next - OPEN_AT) < 0) scheduleOnRN(feel, 0);
      if (next > 0.05) armed.set(true);
    })
    .onFinalize(() => {
      if (!grabbing.get()) return;
      grabbing.set(false);
      const projected = p.get() + pV.get() * 0.15;
      target.set(projected >= OPEN_AT ? 1 : 0);
      oLin.set(0);
      idle.set(0);
      mode.set(2);
    });

  const lock = () => {
    if (mode.get() === 2 && o.get() > 0) {
      mode.set(3);
      ret.set(0);
      fromP.set(p.get());
      fromO.set(o.get());
    }
  };

  const ys = useDerivedValue(() => sliderY(zip, p.get()));
  const uniforms = useDerivedValue(() => ({
    size: [width, height],
    zip: [zip.yTop, ys.get(), zip.yEnd, zip.gapMax],
    open: [reduced ? 0 : o.get(), 6 + 22 * o.get() + 6 * smooth(p.get() * 3)],
    p3: P3,
  }));
  const teeth = useDerivedValue(() => buildTeeth(zip, ys.get(), reduced ? 0 : o.get()));
  const toothShadow = useDerivedValue(() => teeth.get()[0]);
  const toothBody = useDerivedValue(() => teeth.get()[1]);
  const toothLight = useDerivedValue(() => teeth.get()[2]);
  const sliderX = useDerivedValue(() => zip.cx + (width / 2 + 70) * (reduced ? 0 : o.get()));
  const sliderT = useDerivedValue(() => [{ translateX: sliderX.get() }, { translateY: ys.get() }]);
  const tabT = useDerivedValue(() => [
    { translateX: sliderX.get() },
    { translateY: ys.get() + TAB_PIVOT },
    { rotate: angle.get() },
    { scale: 1 + 0.06 * lift.get() },
  ]);
  const tabShadowT = useDerivedValue(() => [
    { translateX: sliderX.get() + 2 + 5 * lift.get() },
    { translateY: ys.get() + TAB_PIVOT + 3 + 7 * lift.get() },
    { rotate: angle.get() },
  ]);
  const tabShadowOpacity = useDerivedValue(() => 0.45 - 0.15 * lift.get());
  const stopT = useDerivedValue(() => [{ translateX: zip.cx - gapAt(zip, zip.yEnd, ys.get(), reduced ? 0 : o.get()) + 1 }, { translateY: zip.yEnd }]);
  const patchT = useDerivedValue(() => [
    { translateX: patchX - gapAt(zip, patchY, ys.get(), reduced ? 0 : o.get()) },
    { translateY: patchY },
    { rotate: -0.04 },
  ]);
  // Under reduced motion the cover fades instead of swinging.
  const coverOpacity = useDerivedValue(() => (reduced ? 1 - o.get() : 1));

  const hitStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: sliderX.get() - 55 }, { translateY: ys.get() - 22 }],
  }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: (1 - smooth(p.get() * 8)) * (1 - o.get()) }));

  return (
    <View style={styles.root}>
      <ZipperJournal since={since} onLock={lock} />
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Group opacity={coverOpacity}>
            {effect ? (
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
            ) : null}
            <Group transform={patchT}>
              <Picture picture={art.patch} />
            </Group>
            <Path path={toothShadow} color="rgba(4,8,22,0.5)" />
            <Path path={toothBody} color="#C89A4C" />
            <Path path={toothBody} color="#6B4A1B" style="stroke" strokeWidth={0.6} />
            <Path path={toothLight} color="rgba(250,228,168,0.85)" />
            <Group transform={stopT}>
              <Picture picture={art.stop} />
            </Group>
            <Group transform={sliderT}>
              <Picture picture={art.slider} />
            </Group>
            <Group transform={tabShadowT} opacity={tabShadowOpacity}>
              <Path path={art.tabShape} color="#040816">
                <BlurMask blur={4} style="normal" />
              </Path>
            </Group>
            <Group transform={tabT}>
              <Picture picture={art.tab} />
            </Group>
          </Group>
        </Canvas>
        <Animated.View style={[styles.hint, { bottom: insets.bottom + 34 }, hintStyle]}>
          <Text style={styles.hintText}>Pull the tab down to open</Text>
        </Animated.View>
      </View>
      <GestureDetector gesture={pan}>
        <Animated.View
          style={[styles.hit, hitStyle]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Zipper pull. Drag down to open the journal."
        />
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#17243F' },
  hit: { position: 'absolute', left: 0, top: 0, width: 110, height: 130 },
  hint: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  hintText: { color: 'rgba(236,226,206,0.62)', fontSize: 13, fontWeight: '600', letterSpacing: 0.4 },
});
