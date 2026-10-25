import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
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
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  BOTTOM_H,
  type DialGeo,
  type DialSim,
  dialUniforms,
  digitWheels,
  HEADER_H,
  makeDialGeo,
  makeSim,
  MODE_LIVE,
  ROOM,
  simGrab,
  simRelease,
  simStep,
  simTurn,
} from './thermostatMotion';
import { THERMO_SKSL } from './thermostatShader';

/*
 Thermostat Frost Dial — "Hearthline", the living room's thermostat in a smart-home app.

 Look: a slatted oak wall with a round thermostat on it: a matte ceramic ring with fine grip
 grooves and a set-point notch, around a dark face with 41 ticks (10° – 30°, every 0.5°) and the
 set point in big light digits. Turning colder grows frost over the ring from the rim inward and
 cools the wall to steel blue; turning warmer melts the frost into beads that dry off, the wall
 glows amber and the air above the dial shimmers. One full-screen shader (thermostatShader.ts),
 views for the text and controls.

 autoPlay: LOOP = 9 s (21° → 16° → 25° → 21°, script in thermostatMotion.ts).
 Touch: drag around the dial to turn it (13.5° of turn per degree, rubber-band past 10° and 30°);
 on release a spring carries the finger's speed and settles on the nearest 0.5°; a selection
 haptic at every 0.5° step. 2 s after the last touch the dial eases back to 21° (0.9 s) and the
 loop restarts. Reduced motion: 21°, still; the dial still turns under a finger.
*/

type Props = {
  /** Plays the 9 s cold-warm-rest loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(THERMO_SKSL);
const INK = '#F2EDE6';
const SOFT = 'rgba(242,237,230,0.62)';
const DIGIT_H = 76;
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];
const HALVES = ['0', '5', '0'];
const MODES = ['Heat', 'Cool', 'Auto', 'Eco'];
const EASE = Easing.bezier(0.77, 0, 0.175, 1);

export function ThermostatFrostDial({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo<DialGeo | null>(
    () => (size ? makeDialGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const sim = useSharedValue<DialSim>(makeSim());
  const tick = useSharedValue(0);
  const heating = useSharedValue(1);

  useEffect(() => {
    sim.set(makeSim());
  }, [auto, sim]);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    simStep(sim.get(), dt, auto, reduced);
    tick.set(tick.get() + 1);
  });

  const step = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  // A detent every 0.5° while a finger is on the dial (or its release spring is settling).
  useAnimatedReaction(
    () => {
      tick.get();
      const s = sim.get();
      return s.mode === MODE_LIVE ? Math.round(s.frame.T * 2) : -1;
    },
    (now, before) => {
      if (now >= 0 && before !== null && before >= 0 && now !== before) scheduleOnRN(step);
    },
  );

  // "Heating to" / "Cooling to" rolls when the set point crosses the room's temperature.
  useAnimatedReaction(
    () => {
      tick.get();
      return sim.get().frame.T >= ROOM ? 1 : 0;
    },
    (now, before) => {
      if (now !== before) heating.set(withTiming(now, { duration: 320, easing: EASE }));
    },
  );

  function angleAt(x: number, y: number) {
    'worklet';
    return geo ? Math.atan2(y - geo.cy, x - geo.cx) : 0;
  }

  const turn = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      if (!geo) return;
      simGrab(sim.get(), angleAt(e.x, e.y));
    })
    .onUpdate((e) => {
      simTurn(sim.get(), angleAt(e.x, e.y));
    })
    .onFinalize(() => {
      simRelease(sim.get());
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    return dialUniforms(sim.get().frame, geo ?? makeDialGeo(1, 1, 0, 0));
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {size && EFFECT ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Fill>
            <Shader source={EFFECT} uniforms={uniforms} />
          </Fill>
        </Canvas>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, height: HEADER_H }]}>
        <View style={styles.brandRow}>
          <Svg width={16} height={16} viewBox="0 0 16 16">
            <Path d="M2.5 7.4 L8 2.8 L13.5 7.4 V13.2 H2.5 Z" stroke={INK} strokeWidth={1.5} strokeLinejoin="round" fill="none" />
            <Path d="M6.4 13.2 V9.6 H9.6 V13.2" stroke={INK} strokeWidth={1.5} strokeLinejoin="round" fill="none" />
          </Svg>
          <Text style={styles.brand}>HEARTHLINE</Text>
          <View style={styles.spacer} />
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>MR</Text>
          </View>
        </View>
        <Text style={styles.title}>Living room</Text>
        <Text style={styles.status}>Indoor 20.5° · Humidity 41% · Eco on</Text>
      </View>

      {geo ? (
        <>
          <GestureDetector gesture={turn}>
            <View
              style={[styles.dialHit, { left: geo.cx - geo.ro - 16, top: geo.cy - geo.ro - 16, width: geo.ro * 2 + 32, height: geo.ro * 2 + 32 }]}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel="Set temperature. Turn the dial."
            />
          </GestureDetector>
          <View pointerEvents="none" style={[styles.readout, { left: geo.cx - 120, top: geo.cy - 70 }]}>
            <ModeLabel heating={heating} />
            <View style={styles.number}>
              <Wheel sim={sim} tick={tick} column={0} strip={DIGITS} />
              <Wheel sim={sim} tick={tick} column={1} strip={DIGITS} />
              <Text style={styles.big}>.</Text>
              <Wheel sim={sim} tick={tick} column={2} strip={HALVES} />
              <Text style={[styles.big, styles.degree]}>°</Text>
            </View>
            <Text style={styles.indoor}>Indoor 20.5°</Text>
          </View>
          <Text pointerEvents="none" style={[styles.rangeLabel, { left: geo.cx - geo.ri * 0.5 - 14, top: geo.cy + geo.ri * 0.5 - 7 }]}>
            10°
          </Text>
          <Text pointerEvents="none" style={[styles.rangeLabel, { left: geo.cx + geo.ri * 0.5 - 14, top: geo.cy + geo.ri * 0.5 - 7 }]}>
            30°
          </Text>
        </>
      ) : null}

      <View style={[styles.bottom, { bottom: Math.max(insets.bottom, 12), height: BOTTOM_H }]}>
        <View style={styles.segment}>
          {MODES.map((m) => (
            <View key={m} style={[styles.segItem, m === 'Auto' && styles.segActive]}>
              <Text style={[styles.segText, m === 'Auto' && styles.segTextActive]}>{m}</Text>
            </View>
          ))}
        </View>
        <View style={styles.card}>
          <Text style={styles.cardTitle}>TODAY</Text>
          <ScheduleRow time="18:00" name="Evening" temp="21°" active />
          <ScheduleRow time="22:30" name="Night" temp="18°" />
          <View style={styles.energy}>
            <Svg width={16} height={16} viewBox="0 0 16 16">
              <Path d="M9 1.8 L3.6 9 H7.6 L6.8 14.2 L12.4 6.8 H8.4 Z" fill="#F2B45C" />
            </Svg>
            <Text style={styles.energyText}>3.2 kWh today · 12% under your usual</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

type Live = { sim: SharedValue<DialSim>; tick: SharedValue<number> };

/** One column of the big number: a strip of digits behind a one-digit window. */
function Wheel({ sim, tick, column, strip }: Live & { column: number; strip: string[] }) {
  const style = useAnimatedStyle(() => {
    tick.get();
    return { transform: [{ translateY: -digitWheels(sim.get().frame.T)[column] * DIGIT_H }] };
  });
  return (
    <View style={styles.wheel}>
      <Animated.View style={style}>
        {strip.map((d, i) => (
          <Text key={i} style={styles.big}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

function ModeLabel({ heating }: { heating: SharedValue<number> }) {
  const heat = useAnimatedStyle(() => ({
    opacity: heating.get(),
    transform: [{ translateY: (1 - heating.get()) * -14 }],
  }));
  const cool = useAnimatedStyle(() => ({
    opacity: 1 - heating.get(),
    transform: [{ translateY: heating.get() * 14 }],
  }));
  return (
    <View style={styles.modeClip}>
      <Animated.Text style={[styles.mode, styles.modeHeat, heat]}>HEATING TO</Animated.Text>
      <Animated.Text style={[styles.mode, styles.modeCool, cool]}>COOLING TO</Animated.Text>
    </View>
  );
}

function ScheduleRow({ time, name, temp, active }: { time: string; name: string; temp: string; active?: boolean }) {
  return (
    <View style={styles.row}>
      <Svg width={10} height={10} viewBox="0 0 10 10">
        <Circle cx={5} cy={5} r={4} fill={active ? '#F2B45C' : 'rgba(242,237,230,0.25)'} />
      </Svg>
      <Text style={styles.rowTime}>{time}</Text>
      <Text style={styles.rowName}>{name}</Text>
      <Text style={styles.rowTemp}>{temp}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#3A3530', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: 8, height: 32 },
  brand: { color: INK, fontSize: 12, fontWeight: '700', letterSpacing: 2 },
  spacer: { flex: 1 },
  avatar: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(242,237,230,0.14)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(242,237,230,0.3)',
  },
  avatarText: { color: INK, fontSize: 12, fontWeight: '700' },
  title: { color: INK, fontSize: 34, fontWeight: '700', letterSpacing: -0.5, marginTop: 10 },
  status: { color: SOFT, fontSize: 15, fontWeight: '500', marginTop: 4, fontVariant: ['tabular-nums'] },
  dialHit: { position: 'absolute', borderRadius: 999 },
  readout: { position: 'absolute', width: 240, height: 140, alignItems: 'center' },
  modeClip: { height: 18, width: 160, overflow: 'hidden' },
  mode: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 12, fontWeight: '700', letterSpacing: 1.6 },
  modeHeat: { color: '#F5B46A' },
  modeCool: { color: '#9CCBF2' },
  number: { flexDirection: 'row', alignItems: 'flex-start', height: DIGIT_H, marginTop: 4 },
  wheel: { height: DIGIT_H, overflow: 'hidden' },
  big: { height: DIGIT_H, lineHeight: DIGIT_H, fontSize: 66, fontWeight: '200', color: INK, fontVariant: ['tabular-nums'], letterSpacing: -1 },
  degree: { fontSize: 44, lineHeight: 64, height: 64, marginLeft: 2 },
  indoor: { color: SOFT, fontSize: 14, fontWeight: '500', marginTop: 4, fontVariant: ['tabular-nums'] },
  rangeLabel: { position: 'absolute', width: 28, textAlign: 'center', color: 'rgba(242,237,230,0.45)', fontSize: 11, fontWeight: '600' },
  bottom: { position: 'absolute', left: 20, right: 20, gap: 14 },
  segment: {
    flexDirection: 'row',
    height: 44,
    padding: 4,
    borderRadius: 22,
    backgroundColor: 'rgba(20,18,16,0.42)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(242,237,230,0.14)',
  },
  segItem: { flex: 1, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  segActive: {
    backgroundColor: 'rgba(242,237,230,0.92)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  segText: { color: SOFT, fontSize: 14, fontWeight: '600' },
  segTextActive: { color: '#2A2622' },
  card: {
    flex: 1,
    paddingHorizontal: 18,
    paddingVertical: 14,
    gap: 10,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(24,22,20,0.55)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(242,237,230,0.14)',
    boxShadow: '0 2px 4px rgba(0,0,0,0.25), 0 18px 40px rgba(0,0,0,0.3), inset 0 1px 0 rgba(255,255,255,0.08)',
  },
  cardTitle: { color: 'rgba(242,237,230,0.5)', fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  rowTime: { color: INK, fontSize: 15, fontWeight: '600', width: 48, fontVariant: ['tabular-nums'] },
  rowName: { flex: 1, color: SOFT, fontSize: 15, fontWeight: '500' },
  rowTemp: { color: INK, fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] },
  energy: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingTop: 10,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(242,237,230,0.14)',
  },
  energyText: { color: SOFT, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
});
