import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  type KettleState,
  LOOP,
  T_TARGET,
  blendState,
  copyState,
  easeInOut,
  loopState,
  makeLayout,
  makeState,
  nextStatus,
  shownTemp,
  stepUser,
} from './kettleMotion';
import { KETTLE_SKSL } from './kettleShader';

/*
 Kettle Boil Progress — "Steep", a tea app heating water for a sencha. A glass kettle on a
 charcoal base: the LED ring round the base fills with the temperature, micro bubbles gather on
 the floor, streams rise, the surface starts to roll, the kettle clicks off at 80 °C and steam
 pours from the spout. The temperature rolls up digit by digit.

 Drawing: one Canvas with one RuntimeEffect (kettleShader.ts): wall, counter, base and ring,
 glass, water, bubbles, steam. Its uniforms come from one state object written per frame on the
 UI thread (kettleMotion.ts); the readout, the status line and the button are views reading
 shared values, so React does not re-render while it heats.

 Touch: a tap anywhere starts heating, a tap while it heats cancels (the bubbles settle and it
 cools); at 80 °C it clicks off by itself. 2 s after the last tap, once the element is off, it
 eases back to cold still water (0.9 s) and the loop starts again. Reduced motion: still water at
 21 °C, no loop; a tap still heats and cancels, with the bubbles and steam shown but the readout
 changing without rolling.
*/

type Props = {
  /** Plays the 9 s heat-click-steam-cool loop. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
const IDLE = 2;
const RETURN = 0.9;

const INK = '#EEF3EF';
const INK_SOFT = 'rgba(238,243,239,0.64)';
const INK_FAINT = 'rgba(238,243,239,0.42)';
const MINT = '#A9E3BE';
const MINT_INK = '#123222';
const effect = Skia.RuntimeEffect.Make(KETTLE_SKSL);

const STATUS = ['Ready · cold water', 'Heating to 80 °C', 'Ready to steep', 'Cooling down'];
const ACTION = ['Heat to 80 °C', 'Cancel', 'Start 2 min timer', 'Heat again'];
/** Roll positions -1 … 4 are drawn; each shows the label of its index mod 4. */
const ROLL = [-1, 0, 1, 2, 3, 4];

function feel(kind: number) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 1) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function KettleBoilProgress({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const L = useMemo(() => makeLayout(size.width, size.height, insets.top, insets.bottom), [size.width, size.height, insets.top, insets.bottom]);

  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(-10);
  const offAt = useSharedValue(-10);
  const returnAt = useSharedValue(0);
  const heater = useSharedValue(0);
  const phase = useSharedValue(0);
  const state = useSharedValue<KettleState>(makeState());
  const from = useSharedValue<KettleState>(makeState());
  const temp = useSharedValue(21);
  const statusPos = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min((f.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    const s = copyState(state.get());
    const m = mode.get();
    let st = 0;
    if (m === MODE_AUTO) {
      const t = auto ? (clock.get() + dt) % LOOP : 0;
      clock.set(t);
      loopState(t, s);
      phase.set(t / LOOP);
      st = s.status % 4;
    } else {
      phase.set((phase.get() + dt / LOOP) % 1);
      if (m === MODE_USER) {
        const on = heater.get();
        stepUser(s, on, dt);
        if (on && s.temp >= T_TARGET - 0.01) {
          // The kettle clicks off by itself: a flash of the ring.
          heater.set(0);
          offAt.set(at);
          s.ringOn = 1.35;
          scheduleOnRN(feel, 1);
        }
        st = heater.get() ? 1 : s.temp > 74 ? 2 : s.temp > 23 ? 3 : 0;
        if (!heater.get() && at - Math.max(lastTouch.get(), offAt.get()) > IDLE) {
          from.set(copyState(s));
          returnAt.set(at);
          mode.set(MODE_RETURN);
        }
      } else {
        const e = easeInOut((at - returnAt.get()) / RETURN);
        const rest = makeState();
        loopState(0, rest);
        blendState(from.get(), rest, e, s);
        st = e < 1 ? 3 : 0;
        if (e >= 1) {
          mode.set(MODE_AUTO);
          clock.set(0);
        }
      }
    }
    state.set(s);
    temp.set(reduced ? Math.round(s.temp) : shownTemp(s.temp));
    // The labels roll forward to the state's next index; index 4 reads like 0, so wrap there.
    let pos = statusPos.get();
    let target = nextStatus(pos, st);
    if (pos >= 3 && target >= 4) {
      pos -= 4;
      target -= 4;
    }
    pos = reduced ? target : pos + (target - pos) * Math.min(1, dt * 9);
    if (Math.abs(target - pos) < 0.002) pos = target;
    statusPos.set(pos);
  });

  /** Hands the loop's current frame to the hand-driven kettle. */
  function takeOver() {
    'worklet';
    if (mode.get() === MODE_AUTO) {
      const t = clock.get();
      const s = makeState();
      loopState(t, s);
      state.set(s);
      heater.set(t >= 1.4 && t < 5.2 ? 1 : 0);
    } else if (mode.get() === MODE_RETURN) {
      heater.set(0);
    }
    mode.set(MODE_USER);
  }

  const tap = Gesture.Tap()
    .maxDuration(600)
    .onStart(() => {
      takeOver();
      lastTouch.set(now.get());
      const on = heater.get() ? 0 : state.get().temp >= T_TARGET - 0.5 ? 0 : 1;
      heater.set(on);
      if (!on) offAt.set(now.get());
      scheduleOnRN(feel, 0);
    });

  const uniforms = useDerivedValue(() => {
    const s = state.get();
    return {
      size: [size.width, size.height],
      kettle: [L.ox, L.oy, L.k, 0],
      heat: s.heat,
      ring: [s.ringFill, s.ringOn],
      steam: s.steam,
      phase: phase.get(),
      lever: s.lever,
    };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };

  return (
    <GestureDetector gesture={tap}>
      <View style={styles.root} onLayout={onLayout} accessible accessibilityRole="button" accessibilityLabel="Glass kettle. Tap to heat to 80 degrees, tap again to cancel.">
        {effect ? (
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>
              <Shader source={effect} uniforms={uniforms} />
            </Fill>
          </Canvas>
        ) : null}

        <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
          <Text style={styles.kicker}>STEEP</Text>
          <Text style={styles.title}>Sencha, 80 °C</Text>
          <Text style={styles.sub}>Japanese green · 250 ml · 2 min</Text>
        </View>

        <View pointerEvents="none" style={[styles.readout, { top: insets.top + 118 }]}>
          <View style={styles.tempRow}>
            <Column place={10} temp={temp} />
            <Column place={1} temp={temp} />
            <Text style={styles.unit}>°C</Text>
          </View>
          <Roller lines={STATUS} pos={statusPos} height={20} textStyle={styles.status} align="right" />
        </View>

        <View pointerEvents="none" style={[styles.panel, { bottom: insets.bottom + 16 }]}>
          <View style={styles.chips}>
            <Chip icon={{ ios: 'drop.fill', android: 'water_drop', web: 'water_drop' }} label="250 ml" />
            <Chip icon={{ ios: 'thermometer.medium', android: 'thermostat', web: 'thermostat' }} label="80 °C" />
            <Chip icon={{ ios: 'timer', android: 'timer', web: 'timer' }} label="2:00" />
          </View>
          <View style={styles.button}>
            <Roller lines={ACTION} pos={statusPos} height={22} textStyle={styles.buttonText} align="center" />
          </View>
          <Text style={styles.hint}>Tap the kettle to heat or cancel</Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const DIGIT_H = 72;
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

/** One digit wheel: the ones turn with the value, the tens as the ones pass 9. */
function Column({ place, temp }: { place: number; temp: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const v = Math.max(0, temp.get());
    const m = v / place;
    const base = Math.floor(m);
    const pos = place === 1 ? m : base + Math.max(0, (m - base) * 10 - 9);
    return { transform: [{ translateY: -(pos % 10) * DIGIT_H }] };
  });
  return (
    <View style={styles.column}>
      <Animated.View style={style}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={styles.digit}>{d}</Text>
        ))}
      </Animated.View>
    </View>
  );
}

function RollLine({ i, text, pos, height, textStyle, align }: { i: number; text: string; pos: SharedValue<number>; height: number; textStyle: object; align: 'right' | 'center' }) {
  const style = useAnimatedStyle(() => {
    const d = i - pos.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.2), transform: [{ translateY: d * height }] };
  });
  return (
    <Animated.Text style={[textStyle, { position: 'absolute', left: 0, right: 0, height, lineHeight: height, textAlign: align }, style]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

/** Labels in a clipped box: the old one rolls up and out, the new one in from below. */
function Roller({ lines, pos, height, textStyle, align }: { lines: string[]; pos: SharedValue<number>; height: number; textStyle: object; align: 'right' | 'center' }) {
  return (
    <View style={{ height, overflow: 'hidden', alignSelf: 'stretch' }}>
      {ROLL.map((i) => (
        <RollLine key={i} i={i} text={lines[(i + 4) % 4]} pos={pos} height={height} textStyle={textStyle} align={align} />
      ))}
    </View>
  );
}

function Chip({ icon, label }: { icon: SymbolViewProps['name']; label: string }) {
  return (
    <View style={styles.chip}>
      <SymbolView
        name={icon}
        size={14}
        weight="semibold"
        tintColor={MINT}
        fallback={<View style={styles.chipDot} />}
      />
      <Text style={styles.chipText}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#141C1D' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: MINT, fontSize: 11, fontWeight: '800', letterSpacing: 2.2 },
  title: { color: INK, fontSize: 30, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: INK_SOFT, fontSize: 14, fontWeight: '500' },
  readout: { position: 'absolute', right: 24, width: 200, alignItems: 'flex-end', gap: 2 },
  tempRow: { flexDirection: 'row', alignItems: 'flex-start' },
  column: { height: DIGIT_H, width: 40, overflow: 'hidden' },
  digit: { height: DIGIT_H, lineHeight: DIGIT_H, color: INK, fontSize: 66, fontWeight: '300', fontVariant: ['tabular-nums'], textAlign: 'center', letterSpacing: -2 },
  unit: { color: INK_SOFT, fontSize: 20, fontWeight: '500', marginTop: 12, marginLeft: 4 },
  status: { color: INK_SOFT, fontSize: 15, fontWeight: '600' },
  panel: {
    position: 'absolute',
    left: 16,
    right: 16,
    padding: 14,
    gap: 12,
    borderRadius: 26,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(22,30,30,0.9)',
    borderWidth: 1,
    borderColor: 'rgba(220,240,230,0.07)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 18px 40px rgba(0,0,0,0.4), inset 0 1px 0 rgba(230,245,238,0.07)',
  },
  chips: { flexDirection: 'row', gap: 8 },
  chip: {
    flex: 1,
    height: 32,
    borderRadius: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    backgroundColor: 'rgba(169,227,190,0.08)',
  },
  chipDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: MINT },
  chipText: { color: INK, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  button: {
    height: 52,
    borderRadius: 26,
    justifyContent: 'center',
    paddingHorizontal: 20,
    backgroundColor: MINT,
    boxShadow: '0 1px 1px rgba(0,0,0,0.25), 0 8px 20px rgba(80,180,120,0.22), inset 0 1px 0 rgba(255,255,255,0.45)',
  },
  buttonText: { color: MINT_INK, fontSize: 17, fontWeight: '700' },
  hint: { color: INK_FAINT, fontSize: 12, fontWeight: '600', textAlign: 'center' },
});
