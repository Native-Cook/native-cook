import { Canvas, FilterMode, MipmapMode, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useAnimatedReaction, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Fonts, drawBackground, drawLevel, drawLevelBody, drawReadout, drawRoof, levelBox, makeGeo } from './spiritArt';
import {
  LOOP,
  angleAt,
  bubbleGoal,
  buildBubbleTable,
  clamp,
  easeInOut,
  MAX_ANGLE,
  MIN_ANGLE,
  mix,
  riseOf,
  rubber,
  sampleBubble,
  seg,
  stepBubble,
} from './spiritMotion';
import { bake } from './spiritPaint';

/*
 Spirit Level Slider — step 3 of a roof-estimate form ("Level — How steep is your roof?"). The
 slider is a yellow aluminium spirit level: drag either end up or down to tilt it about its centre
 and the bubble slides along the curved lime vial to the higher end, lagging on a spring, stretched
 by its speed and squashed against the vial's ends. The pitch card above follows: the degrees and
 the rise per 12 roll like an odometer, the pitch word rolls in its pill, and the house's roof
 tilts to the same pitch. Presets under the level spring it to 4/12, 6/12, 9/12 or 12/12.

 One Skia canvas: a baked background (paper, card, house walls), the roof and readouts drawn per
 frame, and the level as a baked image under its rotation with the bubble and glass on top.
 Timeline and bubble physics: spiritMotion.ts.

 Touch: the level follows the finger (rubber-band past −6° and 48°); on release it springs back
 inside the range (ω 12, ζ 0.8). A selection tick each whole step of rise, a light tap when the
 bubble returns between the centre marks. 2 s after the last touch the level eases back to 0°
 (0.9 s) and the loop restarts. Reduced motion: no loop; the bubble sits where the tilt puts it.
 */

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const IDLE = 2;
const RETURN = 0.9;
const LEVEL_OMEGA = 12;
const LEVEL_ZETA = 0.8;
const PRESETS = [4, 6, 9, 12];
const BACKGROUND = '#ECE4D5';

type Props = {
  /** Tilts the level to 28°, past 40° and down to 18°, then back to level, on an 8 s loop. */
  autoPlay?: boolean;
};

function buzz(kind: 'tick' | 'centre') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function SpiritLevelSlider({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo<Fonts>(
    () => ({
      kicker: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '700' }),
      degrees: matchFont({ fontFamily: SANS, fontSize: 58, fontWeight: '700' }),
      rise: matchFont({ fontFamily: SANS, fontSize: 17, fontWeight: '600' }),
      small: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '500' }),
      pill: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '600' }),
      tiny: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '600' }),
      etch: matchFont({ fontFamily: SANS, fontSize: 7, fontWeight: '700' }),
    }),
    [],
  );
  const widths = useMemo(() => {
    const max = (f: typeof fonts.degrees) => Math.max(...f.getGlyphWidths(f.getGlyphIDs('0123456789')));
    return { degrees: max(fonts.degrees), rise: max(fonts.rise) };
  }, [fonts]);
  const table = useMemo(() => buildBubbleTable(), []);
  const images = useMemo(() => {
    if (!g) return null;
    const scale = Math.min(PixelRatio.get(), 3);
    const background = bake(g.w, g.h, scale, (c) => drawBackground(c, g, fonts));
    const box = levelBox(g);
    const level = bake(box.w, box.h, scale, (c) => drawLevelBody(c, g, fonts));
    return background && level ? { background, level, scale } : null;
  }, [g, fonts]);

  // mode: 0 loop / rest, 1 a finger (or a preset) is in charge, 2 easing back to rest.
  const mode = useSharedValue(0);
  const autoStart = useSharedValue(-1);
  const now = useSharedValue(-1);
  const lastTouch = useSharedValue(-1);
  const dragging = useSharedValue(0);
  /** [angle, angular velocity] of the level; the spring's goal; the drag's start. */
  const level = useSharedValue<number[]>([0, 0]);
  const goal = useSharedValue(0);
  const grab = useSharedValue<number[]>([0, 0, 0]);
  const bubble = useSharedValue<number[]>([0, 0]);
  const returnFrom = useSharedValue<number[]>([0, 0]);
  /** [angle, bubble share, bubble speed] for the drawing. */
  const pose = useSharedValue<number[]>([0, 0, 0]);

  useFrameCallback((frame) => {
    const t1 = frame.timestamp / 1000;
    const first = now.get() < 0;
    const dt = first ? 0 : Math.min(t1 - now.get(), 0.05);
    now.set(t1);
    if (first && auto) autoStart.set(t1);
    const m = mode.get();
    if (m === 0 && autoStart.get() >= 0) {
      const t = (t1 - autoStart.get()) % LOOP;
      const a = angleAt(t);
      level.set([a, (angleAt(t + 1 / 120) - a) * 120]);
      bubble.set(sampleBubble(table, t));
    } else {
      let a = level.get()[0];
      let v = level.get()[1];
      if (m === 2) {
        const r = returnFrom.get();
        a = mix(r[0], 0, easeInOut(seg(t1, r[1], r[1] + RETURN)));
        v = 0;
        if (t1 > r[1] + RETURN + 0.6) {
          mode.set(0);
          autoStart.set(t1);
        }
      } else if (dragging.get() === 0 && dt > 0) {
        // Spring toward the goal (a preset, or back inside the range after a rubber-band).
        const steps = Math.ceil(dt * 240);
        const h = dt / steps;
        for (let s = 0; s < steps; s += 1) {
          const acc = -LEVEL_OMEGA * LEVEL_OMEGA * (a - goal.get()) - 2 * LEVEL_ZETA * LEVEL_OMEGA * v;
          v += acc * h;
          a += v * h;
        }
        if (m === 1 && auto && t1 - lastTouch.get() > IDLE) {
          mode.set(2);
          returnFrom.set([a, t1]);
        }
      }
      level.set([a, v]);
      bubble.set(reduced ? [bubbleGoal(a), 0] : stepBubble(bubble.get(), bubbleGoal(a), dt));
    }
    pose.set([level.get()[0], bubble.get()[0], bubble.get()[1]]);
  });

  // Haptics only for a finger: a tick per whole step of rise, a tap back between the centre marks.
  useAnimatedReaction(
    () => (mode.get() === 1 ? Math.round(riseOf(pose.get()[0])) * 2 + (Math.abs(pose.get()[1]) < 0.06 ? 1 : 0) : -1),
    (key, previous) => {
      if (previous === null || previous < 0 || key < 0 || key === previous) return;
      if (key >> 1 !== previous >> 1) scheduleOnRN(buzz, 'tick');
      else if ((key & 1) === 1) scheduleOnRN(buzz, 'centre');
    },
  );

  const gesture = useMemo(() => {
    const geo = g;
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        if (!geo || e.y < geo.card.y + geo.card.h || e.y > geo.chipsTop - 4) {
          grab.set([0, 0, -1]);
          return;
        }
        mode.set(1);
        autoStart.set(-1);
        dragging.set(1);
        lastTouch.set(now.get());
        // The lever arm is where the finger landed; the level keeps its angle under the finger.
        grab.set([level.get()[0], e.x, 1]);
        level.set([level.get()[0], 0]);
      })
      .onUpdate((e) => {
        const gr = grab.get();
        if (!geo || gr[2] < 0) return;
        lastTouch.set(now.get());
        const side = gr[1] >= geo.cx ? 1 : -1;
        const arm = Math.max(Math.abs(gr[1] - geo.cx), 60);
        const turned = (Math.atan2(-e.translationY * side, arm) * 180) / Math.PI;
        const a = rubber(gr[0] + turned);
        const prev = level.get()[0];
        level.set([a, (a - prev) * 60]);
      })
      .onFinalize(() => {
        if (grab.get()[2] < 0) return;
        dragging.set(0);
        lastTouch.set(now.get());
        goal.set(clamp(level.get()[0], MIN_ANGLE + 6, MAX_ANGLE));
        grab.set([0, 0, -1]);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g]);

  const choose = (rise: number) => {
    buzz('tick');
    const angle = (Math.atan(rise / 12) * 180) / Math.PI;
    scheduleOnUI(() => {
      'worklet';
      mode.set(1);
      autoStart.set(-1);
      lastTouch.set(now.get());
      goal.set(angle);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (g && images) {
      const p = pose.get();
      const s = images.scale;
      canvas.drawImageRectOptions(images.background, Skia.XYWHRect(0, 0, g.w * s, g.h * s), Skia.XYWHRect(0, 0, g.w, g.h), FilterMode.Linear, MipmapMode.None, null);
      drawRoof(canvas, g, fonts, p[0]);
      drawReadout(canvas, g, fonts, widths.degrees, widths.rise, p[0]);
      drawLevel(canvas, g, images.level, s, p[0], p[1], p[2]);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && images ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityRole="adjustable" accessibilityLabel="Roof pitch. Drag the level's end up or down to tilt it.">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          <View pointerEvents="box-none" style={[styles.header, { top: insets.top + 4 }]}>
            <View style={styles.nav}>
              <Pressable accessibilityRole="button" accessibilityLabel="Back" style={styles.back}>
                <View style={styles.chevron} />
              </Pressable>
              <View style={styles.steps}>
                <Text style={styles.stepText}>Step 3 of 5</Text>
                <View style={styles.track}>
                  <View style={styles.trackFill} />
                </View>
              </View>
            </View>
            <Text style={styles.kicker}>ROOF ESTIMATE</Text>
            <Text style={styles.title}>How steep is your roof?</Text>
            <Text style={styles.subtitle}>Tilt the level to match its pitch.</Text>
          </View>

          <View style={[styles.chips, { top: g.chipsTop }]}>
            {PRESETS.map((r) => (
              <Pressable key={r} onPress={() => choose(r)} accessibilityRole="button" accessibilityLabel={`${r} in 12 pitch`} style={({ pressed }) => [styles.chip, pressed && styles.pressed]}>
                <Text style={styles.chipText}>{r}/12</Text>
              </Pressable>
            ))}
          </View>
          <Pressable accessibilityRole="button" style={({ pressed }) => [styles.cta, { top: g.ctaTop }, pressed && styles.pressed]}>
            <Text style={styles.ctaText}>Continue</Text>
            <Text style={styles.ctaNote}>Next: roof area</Text>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const INK = '#2A241C';

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  nav: { height: 40, flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 14 },
  back: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,253,248,0.9)',
    boxShadow: '0 1px 2px rgba(90,74,48,0.18), 0 6px 16px rgba(90,74,48,0.10)',
  },
  chevron: { width: 10, height: 10, borderLeftWidth: 2, borderBottomWidth: 2, borderColor: INK, transform: [{ translateX: 2 }, { rotate: '45deg' }] },
  steps: { flex: 1, gap: 6 },
  stepText: { fontSize: 12, fontWeight: '600', color: 'rgba(42,36,28,0.55)' },
  track: { height: 4, borderRadius: 2, backgroundColor: 'rgba(42,36,28,0.1)', overflow: 'hidden' },
  trackFill: { width: '60%', height: 4, borderRadius: 2, backgroundColor: '#E8862A' },
  kicker: { fontSize: 11, fontWeight: '700', letterSpacing: 1.8, color: 'rgba(42,36,28,0.5)' },
  title: { marginTop: 6, fontSize: 28, fontWeight: '700', color: INK, letterSpacing: -0.4 },
  subtitle: { marginTop: 4, fontSize: 15, color: 'rgba(42,36,28,0.62)' },
  chips: { position: 'absolute', left: 16, right: 16, height: 40, flexDirection: 'row', gap: 8 },
  chip: {
    flex: 1,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,253,248,0.85)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(42,36,28,0.14)',
  },
  chipText: { fontSize: 15, fontWeight: '600', color: INK, fontVariant: ['tabular-nums'] },
  cta: {
    position: 'absolute',
    left: 16,
    right: 16,
    height: 56,
    borderRadius: 28,
    paddingHorizontal: 24,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: INK,
    boxShadow: '0 1px 2px rgba(42,36,28,0.3), 0 10px 24px rgba(42,36,28,0.22)',
  },
  ctaText: { fontSize: 17, fontWeight: '600', color: '#F8F2E6' },
  ctaNote: { fontSize: 13, fontWeight: '500', color: 'rgba(248,242,230,0.55)' },
  pressed: { transform: [{ scale: 0.97 }] },
});
