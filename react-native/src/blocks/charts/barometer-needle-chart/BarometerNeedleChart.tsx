import { Canvas, FilterMode, MipmapMode, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useAnimatedReaction, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Fonts, drawBase, drawGlass, hourAtX, makeGeo } from './barometerArt';
import { drawNeedle, drawOverlay } from './barometerFrame';
import {
  LOOP,
  NOW_HPA,
  SPAN,
  angleOf,
  buildNeedleTable,
  clamp,
  cursorAt,
  easeInOut,
  hpaOf,
  jitterAt,
  mix,
  pressureAt,
  sampleTable,
  seg,
  stepNeedle,
  wordIndex,
} from './barometerMotion';
import { bake } from './barometerPaint';

/*
 Barometer Needle Chart — a sailing weather screen ("Saltmarsh Harbour") whose hero is a brass
 aneroid barometer over a 48-hour pressure chart. Scrubbing the chart swings the blued needle on a
 spring to the reading at that hour; the brass set hand stays at now (1012 hPa), the readout rolls
 like an odometer and the forecast word and trend roll as the needle crosses their bands. Tap the
 glass and the needle twitches, as people tap real barometers.

 One Skia canvas: a baked image of everything static (background, dial, chart card), the needle,
 a baked image of the glass (reflections, set hand, knob), then the readout and chart cursor.
 Timeline and physics: barometerMotion.ts.

 Touch: drag on the chart to scrub (rubber-band past ±24 h); the needle runs the same spring,
 integrated on the UI thread. A tap on the dial kicks the needle (22°/s). 2 s after the last
 touch the cursor eases back to now (0.9 s) and the loop restarts from t = 0.
 Reduced motion: no loop, no spring or jitter; the needle points straight at the reading.
 */

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
/** Seconds after the last touch before the screen eases back to rest, and how long that takes. */
const IDLE = 2;
const RETURN = 0.9;
/** Velocity kick of a tap on the glass, degrees per second. */
const TAP_KICK = 22;

const BACKGROUND = '#0F1C24';

type Props = {
  /** Plays the 9 s scrub through the storm and the forecast on a loop. */
  autoPlay?: boolean;
};

function buzz(kind: 'tick' | 'tap') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function BarometerNeedleChart({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });

  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo<Fonts>(
    () => ({
      kicker: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '700' }),
      readout: matchFont({ fontFamily: SANS, fontSize: 54, fontWeight: '700' }),
      unit: matchFont({ fontFamily: SANS, fontSize: 17, fontWeight: '500' }),
      word: matchFont({ fontFamily: SANS, fontSize: 22, fontWeight: '600' }),
      small: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '500' }),
      scale: matchFont({ fontFamily: SERIF, fontSize: 10, fontWeight: '600' }),
      dialWord: matchFont({ fontFamily: SERIF, fontSize: 10, fontWeight: '700' }),
      maker: matchFont({ fontFamily: SERIF, fontSize: 7.5, fontWeight: '600' }),
      chartTitle: matchFont({ fontFamily: SANS, fontSize: 15, fontWeight: '600' }),
      label: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '500' }),
      tip: matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '600' }),
    }),
    [],
  );
  const digitW = useMemo(() => {
    const ids = fonts.readout.getGlyphIDs('0123456789');
    return Math.max(...fonts.readout.getGlyphWidths(ids));
  }, [fonts]);
  const table = useMemo(() => buildNeedleTable(), []);

  // Static art, baked once per size.
  const images = useMemo(() => {
    if (!g) return null;
    const scale = Math.min(PixelRatio.get(), 3);
    const base = bake(g.w, g.h, scale, (c) => drawBase(c, g, fonts));
    const glass = bake(g.rb * 2, g.rb * 2, scale, (c) => drawGlass(c, g, angleOf(NOW_HPA)));
    if (!base || !glass) return null;
    return { base, glass, scale };
  }, [g, fonts]);

  // UI-thread state. mode: 0 loop / rest, 1 a finger is in charge, 2 easing back to rest.
  const mode = useSharedValue(0);
  const autoStart = useSharedValue(-1);
  const now = useSharedValue(-1);
  const lastTouch = useSharedValue(-1);
  const touching = useSharedValue(0);
  const scrubbing = useSharedValue(0);
  const userHour = useSharedValue(0);
  const needle = useSharedValue<number[]>([angleOf(pressureAt(0)), 0]);
  const returnFrom = useSharedValue<number[]>([0, 0]);
  /** [cursor hour, needle angle] for the drawing. */
  const pose = useSharedValue<number[]>([0, angleOf(pressureAt(0))]);
  const downAt = useSharedValue<number[]>([0, 0]);

  useFrameCallback((frame) => {
    const t1 = frame.timestamp / 1000;
    const first = now.get() < 0;
    const dt = first ? 0 : Math.min(t1 - now.get(), 0.05);
    now.set(t1);
    if (first && auto) autoStart.set(t1);
    let hour = userHour.get();
    let angle: number;
    const m = mode.get();
    if (m === 0 && autoStart.get() >= 0) {
      const t = (t1 - autoStart.get()) % LOOP;
      hour = cursorAt(t);
      angle = sampleTable(table, t) + jitterAt(t);
      const ahead = sampleTable(table, t + 1 / 120);
      needle.set([angle, (ahead - sampleTable(table, t)) * 120]);
      userHour.set(hour);
    } else {
      if (m === 2) {
        const k = easeInOut(seg(t1, returnFrom.get()[1], returnFrom.get()[1] + RETURN));
        hour = mix(returnFrom.get()[0], 0, k);
        userHour.set(hour);
        if (t1 > returnFrom.get()[1] + RETURN + 0.5) {
          mode.set(0);
          autoStart.set(t1);
        }
      } else if (m === 1 && touching.get() === 0) {
        // Let go past an end: the cursor springs back inside.
        const inside = clamp(hour, -SPAN, SPAN);
        if (inside !== hour) {
          hour += (inside - hour) * (1 - Math.exp(-dt * 14));
          userHour.set(hour);
        }
        if (auto && t1 - lastTouch.get() > IDLE) {
          mode.set(2);
          returnFrom.set([hour, t1]);
        }
      }
      const target = angleOf(pressureAt(clamp(hour, -SPAN, SPAN)));
      if (reduced) {
        needle.set([target, 0]);
      } else {
        needle.set(stepNeedle(needle.get(), target, dt));
      }
      angle = needle.get()[0];
    }
    pose.set([hour, angle]);
  });

  // A selection tick each time a finger drags the needle into another forecast word.
  useAnimatedReaction(
    () => Math.round(wordIndex(hpaOf(pose.get()[1]))),
    (word, previous) => {
      if (previous !== null && word !== previous && mode.get() === 1) scheduleOnRN(buzz, 'tick');
    },
  );

  const gesture = useMemo(() => {
    const geo = g;
    function takeOver() {
      'worklet';
      mode.set(1);
      autoStart.set(-1);
      touching.set(1);
      lastTouch.set(now.get());
    }
    function hourFromX(x: number) {
      'worklet';
      if (!geo) return 0;
      const h = hourAtX(geo, x);
      // Rubber-band past either end.
      if (h > SPAN) return SPAN + (h - SPAN) / (1 + (h - SPAN) / 6);
      if (h < -SPAN) return -SPAN - (-SPAN - h) / (1 + (-SPAN - h) / 6);
      return h;
    }
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        if (!geo) return;
        takeOver();
        downAt.set([e.x, e.y]);
        const inCard = e.y >= geo.card.y && e.y <= geo.card.y + geo.card.h;
        scrubbing.set(inCard ? 1 : 0);
        if (inCard) userHour.set(hourFromX(e.x));
      })
      .onUpdate((e) => {
        lastTouch.set(now.get());
        if (scrubbing.get() === 1) userHour.set(hourFromX(e.x));
      })
      .onFinalize((e) => {
        touching.set(0);
        lastTouch.set(now.get());
        if (!geo) return;
        if (scrubbing.get() === 0) {
          const d = downAt.get();
          const moved = Math.hypot(e.x - d[0], e.y - d[1]);
          const onDial = Math.hypot(d[0] - geo.cx, d[1] - geo.cy) < geo.rb;
          if (moved < 12 && onDial) {
            const n = needle.get();
            needle.set([n[0], n[1] + (n[0] > angleOf(NOW_HPA) ? -TAP_KICK : TAP_KICK)]);
            scheduleOnRN(buzz, 'tap');
          }
        }
        scrubbing.set(0);
      });
    // Shared values are stable; the gesture depends on the geometry only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (g && images) {
      const p = pose.get();
      const s = images.scale;
      canvas.drawImageRectOptions(images.base, Skia.XYWHRect(0, 0, g.w * s, g.h * s), Skia.XYWHRect(0, 0, g.w, g.h), FilterMode.Linear, MipmapMode.None, null);
      drawNeedle(canvas, g, p[1]);
      const d = g.rb * 2;
      canvas.drawImageRectOptions(images.glass, Skia.XYWHRect(0, 0, d * s, d * s), Skia.XYWHRect(g.cx - g.rb, g.cy - g.rb, d, d), FilterMode.Linear, MipmapMode.None, null);
      drawOverlay(canvas, g, fonts, digitW, p[0], hpaOf(p[1]));
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
            <View
              style={StyleSheet.absoluteFill}
              accessible
              accessibilityLabel="Barometer at 1012 hectopascals, change. Drag the chart to see the pressure at another hour; tap the dial to tap the glass."
            >
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>
          <View pointerEvents="none" style={[styles.stats, { top: g.statsTop }]}>
            {STATS.map((s) => (
              <View key={s.label} style={styles.stat}>
                <Text style={styles.statLabel}>{s.label}</Text>
                <Text style={styles.statValue} numberOfLines={1}>
                  {s.value}
                </Text>
              </View>
            ))}
          </View>
        </>
      ) : null}
    </View>
  );
}

const STATS = [
  { label: 'Wind', value: 'SW 14 kn' },
  { label: 'Swell', value: '1.2 m · 9 s' },
  { label: 'High tide', value: '16:48' },
];

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  stats: { position: 'absolute', left: 16, right: 16, height: 56, flexDirection: 'row', gap: 8 },
  stat: {
    flex: 1,
    borderRadius: 16,
    borderCurve: 'continuous',
    paddingHorizontal: 12,
    justifyContent: 'center',
    gap: 2,
    backgroundColor: 'rgba(241,234,217,0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(241,234,217,0.12)',
  },
  statLabel: { fontSize: 11, fontWeight: '500', color: 'rgba(233,224,204,0.5)' },
  statValue: { fontSize: 15, fontWeight: '600', color: '#F1EAD9', fontVariant: ['tabular-nums'] },
});
