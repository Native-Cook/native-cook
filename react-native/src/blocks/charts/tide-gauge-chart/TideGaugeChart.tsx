import {
  BlurMask,
  Canvas,
  Circle,
  DashPathEffect,
  Fill,
  Group,
  LinearGradient,
  Path,
  Picture,
  RoundedRect,
  Shader,
  Skia,
  matchFont,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo } from 'react';
import { Platform, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { INK, INK_SOFT, recordBackdrop, recordBuoy } from './tideGaugeArt';
import {
  DAY,
  EXTREMES,
  IDLE_BEFORE_RETURN,
  LOOP,
  NOW,
  RETURN_FOR,
  SLOSH_GAIN,
  SLOSH_MAX,
  SLOSH_OMEGA,
  SLOSH_ZETA,
  type Plot,
  clamp,
  clockText,
  easeInOut,
  hourToX,
  scriptGlow,
  scriptHour,
  scriptSlosh,
  surfaceY,
  tide,
  tideTrend,
} from './tideGaugeMotion';
import { BACKDROP_SKSL, TIDE_SKSL } from './tideGaugeShader';

/*
 Tide Gauge Chart — "Harbor Point · Tides": a 24-hour tide chart whose area is real water.
 The loop and its phases are in tideGaugeMotion.ts (LOOP = 9 s).

 Touch: the scrubber follows the finger along the day (a fast critically damped follow, ω 28),
 the buoy rides the surface, and the speed of the scrub drives the slosh oscillator (ω 9 rad/s,
 damping ratio 0.3): the surface leans against the move and rings when the finger stops. Past
 either end of the day the scrubber rubber-bands and springs back on release. A selection tick
 when the scrubber passes a high or low tide. 2 s after the last touch the scrubber eases back to
 now in 0.8 s and the loop restarts. Reduced motion: no loop, no ripples, no slosh; the finger
 still scrubs.
*/

type Props = {
  /** Plays the 9 s scrub demo: back to the morning, across the high tide, and home to now. */
  autoPlay?: boolean;
};

const FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const P3 = Platform.OS === 'ios' ? 1 : 0;
const PAD = 20;
const CARD_RADIUS = 24;
const DIGIT_H = 60;
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

/**
 * One odometer column: 0–9 and a second 0 so the wrap from 9 rolls on. The wheel springs to the
 * column's whole count (critically damped, 0.26 s), so it only shows a partial digit while the value
 * changes and always comes to rest square on a digit.
 */
function RollingDigit({ value, place }: { value: SharedValue<number>; place: 0 | 1 | 2 }) {
  const reduced = useReducedMotion();
  const wheel = useSharedValue(0);
  useAnimatedReaction(
    () => Math.floor(Math.round(Math.max(0, value.get()) * 100) / (place === 2 ? 1 : place === 1 ? 10 : 100)),
    (n, prev) => {
      if (prev === null || reduced) wheel.set(n);
      else wheel.set(withSpring(n, { duration: 260, dampingRatio: 1 }));
    },
  );
  const style = useAnimatedStyle(() => {
    const pos = ((wheel.get() % 10) + 10) % 10;
    return { transform: [{ translateY: -pos * DIGIT_H }] };
  });
  return (
    <View style={styles.digitWindow}>
      <Animated.View style={style}>
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0].map((d, i) => (
          <Text key={i} style={styles.digit}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

export function TideGaugeChart({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;

  // Layout, computed rather than measured so the canvas and the views agree exactly.
  const top = insets.top + 16;
  const cardX = PAD - 4;
  const cardY = top + 164;
  const cardW = width - cardX * 2;
  const cardH = clamp(height - cardY - 214 - insets.bottom, 220, 340);
  const plot: Plot = useMemo(() => ({ x: 14, y: 40, w: cardW - 28, h: cardH - 40 - 48 }), [cardW, cardH]);
  const tilesY = cardY + cardH + 16;

  const effect = useMemo(() => Skia.RuntimeEffect.Make(TIDE_SKSL), []);
  const backdropFx = useMemo(() => Skia.RuntimeEffect.Make(BACKDROP_SKSL), []);
  const fonts = useMemo(
    () => ({
      small: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '600' }),
      tiny: matchFont({ fontFamily: FAMILY, fontSize: 10, fontWeight: '600' }),
    }),
    [],
  );
  const backdrop = useMemo(() => recordBackdrop(cardW, cardH, plot, fonts.small, fonts.tiny), [cardW, cardH, plot, fonts]);
  const buoy = useMemo(() => recordBuoy(), []);

  // Loop clock and the values every frame is drawn from.
  const clock = useSharedValue(0);
  const hour = useSharedValue(NOW);
  const tilt = useSharedValue(0);
  const chop = useSharedValue(0);
  const glow = useSharedValue(0);
  const phase = useSharedValue(0);
  // Touch: 0 loop, 1 finger down, 2 released (waiting), 3 easing back to rest.
  const mode = useSharedValue(0);
  const fingerHour = useSharedValue(NOW);
  const hourV = useSharedValue(0);
  const tiltV = useSharedValue(0);
  const idle = useSharedValue(0);
  const fromHour = useSharedValue(NOW);
  const ret = useSharedValue(0);

  useEffect(() => {
    clock.set(0);
    mode.set(0);
    hour.set(NOW);
  }, [auto, clock, mode, hour]);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m === 0) {
      if (!auto) {
        hour.set(NOW);
        tilt.set(0);
        chop.set(0);
        glow.set(0);
        if (!reduced) phase.set((phase.get() + dt / LOOP) % 1);
        return;
      }
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      const s = scriptSlosh(t, plot.w);
      hour.set(scriptHour(t));
      tilt.set(s[0]);
      chop.set(s[1]);
      glow.set(scriptGlow(t));
      phase.set(t / LOOP);
      return;
    }
    // Ripples keep travelling while the finger is in charge (not under reduced motion).
    if (!reduced) phase.set((phase.get() + dt / LOOP) % 1);
    const before = hour.get();
    if (m === 3) {
      const r = Math.min(1, ret.get() + dt / RETURN_FOR);
      ret.set(r);
      hour.set(fromHour.get() + (NOW - fromHour.get()) * easeInOut(r));
      if (r >= 1) {
        mode.set(0);
        clock.set(0);
        phase.set(0);
      }
    } else {
      // The scrubber follows the finger (or, released, springs back inside the day).
      const target = m === 1 ? fingerHour.get() : clamp(hour.get(), 0, DAY);
      const w = 28;
      const a = -w * w * (hour.get() - target) - 2 * w * hourV.get();
      hourV.set(hourV.get() + a * dt);
      hour.set(hour.get() + hourV.get() * dt);
      if (m === 2) {
        idle.set(idle.get() + dt);
        if (idle.get() > IDLE_BEFORE_RETURN) {
          mode.set(3);
          ret.set(0);
          fromHour.set(hour.get());
        }
      }
    }
    const h = hour.get();
    const speed = ((h - before) / Math.max(dt, 1e-3)) * (plot.w / DAY);
    // Slosh: the oscillator, driven by the scrub speed (critically damped under reduced motion).
    const zeta = reduced ? 1 : SLOSH_ZETA;
    const drive = reduced ? 0 : clamp(-speed * SLOSH_GAIN, -SLOSH_MAX, SLOSH_MAX);
    const acc = -SLOSH_OMEGA * SLOSH_OMEGA * (tilt.get() - drive) - 2 * zeta * SLOSH_OMEGA * tiltV.get();
    tiltV.set(tiltV.get() + acc * dt);
    tilt.set(tilt.get() + tiltV.get() * dt);
    // While easing back to rest the slosh is let go, so the loop takes over from a calm surface.
    if (m === 3) tilt.set(tilt.get() * (1 - Math.min(1, ret.get()) * 0.25));
    chop.set(reduced ? 0 : chop.get() + (clamp(Math.abs(speed) / 700, 0, 1) - chop.get()) * Math.min(1, dt * 6));
    glow.set(glow.get() * Math.exp(-dt * 4));
    // A tick when the scrubber passes a high or low tide (touch only; the loop is silent).
    if (m !== 3) {
      for (let i = 0; i < EXTREMES.length; i += 1) {
        const e = EXTREMES[i];
        if ((before - e) * (h - e) < 0) scheduleOnRN(tick);
      }
    }
  });

  function hourAt(x: number) {
    'worklet';
    const raw = ((x - plot.x) / plot.w) * DAY;
    // Rubber band past either end of the day.
    if (raw < 0) return raw / (1 - raw / 3);
    if (raw > DAY) return DAY + (raw - DAY) / (1 + (raw - DAY) / 3);
    return raw;
  }

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      if (mode.get() === 0) {
        hourV.set(0);
        tiltV.set(0);
      }
      mode.set(1);
      fingerHour.set(hourAt(e.x));
    })
    .onUpdate((e) => {
      fingerHour.set(hourAt(e.x));
    })
    .onFinalize(() => {
      mode.set(2);
      idle.set(0);
    });

  const scrubX = useDerivedValue(() => hourToX(hour.get(), plot));
  const surf = useDerivedValue(() => surfaceY(scrubX.get(), plot, phase.get(), scrubX.get(), tilt.get(), chop.get()));
  const uniforms = useDerivedValue(() => ({
    plot: [plot.x, plot.y, plot.w, plot.h],
    scrub: [scrubX.get(), tilt.get(), chop.get(), glow.get()],
    phase: phase.get(),
    p3: P3,
  }));
  const backdropUniforms = useMemo(() => ({ size: [width, height], p3: P3 }), [width, height]);
  const scrubPath = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    b.moveTo(scrubX.get(), plot.y - 4);
    b.lineTo(scrubX.get(), surf.get() - 30);
    return b.build();
  });
  const buoyTransform = useDerivedValue(() => {
    const x = scrubX.get();
    const ph = phase.get();
    const p = plot;
    const left = surfaceY(x - 9, p, ph, x, tilt.get(), chop.get());
    const right = surfaceY(x + 9, p, ph, x, tilt.get(), chop.get());
    const bob = reduced ? 0 : 1.4 * Math.sin(2 * Math.PI * 3 * ph);
    const lean = Math.atan2(right - left, 18) * 0.8 + tilt.get() * 0.012;
    return [{ translateX: x }, { translateY: surf.get() + 2 + bob }, { rotate: lean }];
  });
  const lampOpacity = useDerivedValue(() => 0.35 + 0.25 * Math.sin(2 * Math.PI * 2 * phase.get()) ** 2 + glow.get() * 0.4);

  // Readout.
  const level = useDerivedValue(() => tide(clamp(hour.get(), 0, DAY)));
  const statusProps = useAnimatedProps(() => {
    const h = clamp(hour.get(), 0, DAY);
    const text = `${tideTrend(h) > 0 ? 'Rising' : 'Falling'} · ${clockText(h)}`;
    return { text, defaultValue: text } as unknown as Record<string, unknown>;
  });
  const glowStyle = useAnimatedStyle(() => ({ opacity: glow.get() }));

  return (
    <View style={styles.root}>
      <Canvas style={StyleSheet.absoluteFill}>
        {backdropFx ? (
          <Fill>
            <Shader source={backdropFx} uniforms={backdropUniforms} />
          </Fill>
        ) : null}
        <Group transform={[{ translateX: cardX }, { translateY: cardY }]}>
          {/* Ambient and contact shadows, then the card with a lit top edge. */}
          <RoundedRect x={4} y={18} width={cardW - 8} height={cardH - 10} r={CARD_RADIUS} color="rgba(1,8,14,0.55)">
            <BlurMask blur={22} style="normal" />
          </RoundedRect>
          <RoundedRect x={1} y={2} width={cardW - 2} height={cardH} r={CARD_RADIUS} color="rgba(1,8,14,0.5)">
            <BlurMask blur={3} style="normal" />
          </RoundedRect>
          <RoundedRect x={0} y={0} width={cardW} height={cardH} r={CARD_RADIUS}>
            <LinearGradient start={vec(0, 0)} end={vec(cardW * 0.3, cardH)} colors={['#123447', '#0C2636', '#091C2A']} positions={[0, 0.55, 1]} />
          </RoundedRect>
          <RoundedRect x={0.5} y={0.5} width={cardW - 1} height={cardH - 1} r={CARD_RADIUS - 0.5} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, 0)} end={vec(0, cardH)} colors={['rgba(190,230,245,0.16)', 'rgba(190,230,245,0.04)', 'rgba(0,0,0,0.25)']} positions={[0, 0.3, 1]} />
          </RoundedRect>
          <Picture picture={backdrop} />
          {effect ? (
            <RoundedRect x={0} y={0} width={cardW} height={cardH} r={CARD_RADIUS}>
              <Shader source={effect} uniforms={uniforms} />
            </RoundedRect>
          ) : null}
          <Path path={scrubPath} style="stroke" strokeWidth={1} color="rgba(230,240,243,0.55)">
            <DashPathEffect intervals={[3, 3]} />
          </Path>
          <Circle cx={scrubX} cy={plot.y - 4} r={3.5} color={INK} />
          <Group transform={buoyTransform}>
            <Circle cx={0} cy={-30.5} r={9} color="#FFC967" opacity={lampOpacity}>
              <BlurMask blur={6} style="normal" />
            </Circle>
            <Picture picture={buoy} />
          </Group>
        </Group>
      </Canvas>

      <View pointerEvents="none" style={[styles.header, { top }]}>
        <Text style={styles.kicker}>TIDES · STATION 4127</Text>
        <Text style={styles.title}>Harbor Point</Text>
        <Text style={styles.secondary}>Sat 25 Oct · Spring tide · Swell 1.2 m</Text>
      </View>

      <View pointerEvents="none" style={[styles.reading, { top: top + 96 }]}>
        <View style={styles.number}>
          <Animated.View style={[styles.valueGlow, glowStyle]} />
          <RollingDigit value={level} place={0} />
          <Text style={styles.digit}>.</Text>
          <RollingDigit value={level} place={1} />
          <RollingDigit value={level} place={2} />
          <Text style={styles.unit}>m</Text>
        </View>
        <View style={styles.status}>
          <AnimatedTextInput
            editable={false}
            underlineColorAndroid="transparent"
            defaultValue="Rising · 10:40"
            animatedProps={statusProps}
            style={styles.statusText}
          />
          <Text style={styles.statusSub}>Water 17 °C · Wind 9 kn SW</Text>
        </View>
      </View>

      <GestureDetector gesture={pan}>
        <View
          style={[styles.touch, { left: cardX, top: cardY, width: cardW, height: cardH }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Tide chart for today. Drag to scrub through the day."
        />
      </GestureDetector>

      <View pointerEvents="none" style={[styles.tiles, { top: tilesY }]}>
        <View style={styles.tile}>
          <Text style={styles.tileLabel}>HIGH TIDE</Text>
          <Text style={styles.tileValue}>2.4 m at 14:12</Text>
          <Text style={styles.tileSub}>Next 02:26 · 2.3 m</Text>
        </View>
        <View style={styles.tile}>
          <Text style={styles.tileLabel}>LOW TIDE</Text>
          <Text style={styles.tileValue}>0.3 m at 08:05</Text>
          <Text style={styles.tileSub}>Next 20:19 · 0.4 m</Text>
        </View>
      </View>

      <View pointerEvents="none" style={[styles.window, { top: tilesY + 96 }]}>
        <View style={styles.windowDot} />
        <Text style={styles.windowText}>Surf window 12:30 – 15:40</Text>
        <Text style={styles.windowTag}>Good</Text>
      </View>

      <Text pointerEvents="none" style={[styles.footer, { bottom: insets.bottom + 14 }]}>
        Predicted levels above chart datum · Not for navigation
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#081822', overflow: 'hidden' },
  header: { position: 'absolute', left: PAD, right: PAD, gap: 4 },
  kicker: { color: '#7FC3D6', fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  title: { color: INK, fontSize: 30, lineHeight: 36, fontWeight: '700', letterSpacing: -0.4 },
  secondary: { color: INK_SOFT, fontSize: 15, fontWeight: '500' },
  reading: { position: 'absolute', left: PAD, right: PAD, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  number: { flexDirection: 'row', alignItems: 'flex-end' },
  valueGlow: {
    position: 'absolute',
    left: -6,
    right: -6,
    top: 10,
    bottom: 6,
    borderRadius: 30,
    backgroundColor: 'rgba(143,214,232,0.10)',
    boxShadow: '0 0 28px rgba(143,214,232,0.55)',
  },
  digitWindow: { height: DIGIT_H, overflow: 'hidden' },
  digit: { color: INK, fontSize: 52, lineHeight: DIGIT_H, height: DIGIT_H, fontWeight: '600', fontVariant: ['tabular-nums'], letterSpacing: -1 },
  unit: { color: INK_SOFT, fontSize: 20, fontWeight: '600', marginLeft: 4, marginBottom: 10 },
  status: { alignItems: 'flex-end', marginBottom: 8, gap: 2 },
  statusText: { color: INK, fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'], padding: 0, textAlign: 'right', minWidth: 140 },
  statusSub: { color: INK_SOFT, fontSize: 12, fontWeight: '500' },
  touch: { position: 'absolute' },
  tiles: { position: 'absolute', left: PAD - 4, right: PAD - 4, flexDirection: 'row', gap: 12 },
  tile: {
    flex: 1,
    height: 84,
    borderRadius: 18,
    borderCurve: 'continuous',
    padding: 14,
    gap: 3,
    backgroundColor: 'rgba(18,48,66,0.72)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(190,230,245,0.14)',
    boxShadow: '0 1px 2px rgba(0,8,14,0.35), 0 10px 24px rgba(0,8,14,0.25)',
  },
  tileLabel: { color: '#7FC3D6', fontSize: 11, fontWeight: '700', letterSpacing: 1.2 },
  tileValue: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  tileSub: { color: INK_SOFT, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  window: {
    position: 'absolute',
    left: PAD - 4,
    right: PAD - 4,
    height: 48,
    borderRadius: 24,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: 'rgba(18,48,66,0.5)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(190,230,245,0.12)',
  },
  windowDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#7FE0B4' },
  windowText: { flex: 1, color: INK, fontSize: 15, fontWeight: '500', fontVariant: ['tabular-nums'] },
  windowTag: { color: '#7FE0B4', fontSize: 13, fontWeight: '700' },
  footer: { position: 'absolute', left: PAD, right: PAD, textAlign: 'center', color: 'rgba(200,222,230,0.4)', fontSize: 11, fontWeight: '500' },
});
