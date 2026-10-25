import { Canvas, Fill, Picture, Shader, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  type SharedValue,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { RollLabel, RollNumber } from './SundialTimeRoll';
import {
  type DialGeo,
  LOOP,
  REST_TIME,
  SIN_LAT,
  easeInOut,
  fingerAt,
  makeGeo,
  quarterOf,
  rubberTime,
  sunDir,
  sunOnArc,
  timeAt,
  timeFromFinger,
  uniformsOf,
} from './sundialMotion';
import { SUNDIAL_SKSL } from './sundialShader';

/*
 Sundial Time Picker — a calendar's time picker ("Meridian — Pick a time") that is a stone
 sundial seen from above. Drag the sun along the sky arc, or drag the shadow round the dial: the
 gnomon's shadow swings to that hour, shortens towards noon and stretches in the morning and late
 afternoon, the light (and the stone, and the backdrop) warms when the sun is low. While dragging,
 every quarter hour ticks (selection haptic) and the readout rolls; on release the time springs to
 the nearest quarter, carrying the drag's speed. Two seconds after the last touch it eases back to
 10:00 (0.9 s) and the loop starts again.

 One Canvas: the shader (sundialShader.ts) plus the carved numerals, recorded once into a Picture.
 The readout and the event card are RN views. Reduced motion: 10:00, still; dragging still works.
 */

const INK = '#2C2722';
const MUTE = 'rgba(44,39,34,0.58)';
const ACCENT = '#C77A35';
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const NUMERALS = ['VII', 'VIII', 'IX', 'X', 'XI', 'XII', 'I', 'II', 'III', 'IV', 'V'];
const QUARTERS = ['00', '15', '30', '45'];
const IDLE = 2;
const RETURN = 0.9;
const MODE_AUTO = 0;
const MODE_HAND = 1;
const MODE_RETURN = 2;
/** The snap spring (response 0.45 s, damping ratio 0.7) in hours. */
const K_SNAP = 195;
const C_SNAP = 19.5;

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

/** The carved numerals and the motto, recorded once: dark cut with a light lower lip. */
function carveNumerals(g: DialGeo) {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  const font = matchFont({ fontFamily: SERIF, fontSize: Math.round(15 * g.unit), fontWeight: '600' });
  const small = matchFont({ fontFamily: SERIF, fontSize: Math.round(10 * g.unit), fontWeight: '600' });
  const dark = Skia.Paint();
  dark.setColor(Skia.Color('rgba(58,48,38,0.78)'));
  const lip = Skia.Paint();
  lip.setColor(Skia.Color('rgba(255,252,244,0.55)'));
  const put = (text: string, f: ReturnType<typeof matchFont>, x: number, y: number) => {
    const ids = f.getGlyphIDs(text);
    const w = f.getGlyphWidths(ids).reduce((a, b) => a + b, 0);
    canvas.drawText(text, x - w / 2 + 0.6, y + 0.8, lip, f);
    canvas.drawText(text, x - w / 2, y, dark, f);
  };
  const r = g.R * 0.87;
  for (let i = 0; i < NUMERALS.length; i += 1) {
    const h = ((i + 7 - 12) * 15 * Math.PI) / 180;
    const a = Math.atan(SIN_LAT * Math.tan(h));
    put(NUMERALS[i], font, g.cx + Math.sin(a) * r, g.cy - Math.cos(a) * r + 5 * g.unit);
  }
  put('MERIDIAN', small, g.cx, g.cy + g.R * 0.5);
  put('LAT 40 N  -  EST 1894', small, g.cx, g.cy + g.R * 0.62);
  return rec.finishRecordingAsPicture();
}

type Motion = {
  now: SharedValue<number>;
  mode: SharedValue<number>;
  autoStart: SharedValue<number>;
  lastTouch: SharedValue<number>;
  touching: SharedValue<number>;
  /** Time [hours, velocity h/s, asked-for hours]; drag [on the dial (1) or the arc (0)]. */
  time: SharedValue<number[]>;
  onDial: SharedValue<number>;
  returnFrom: SharedValue<number>;
  returnStart: SharedValue<number>;
};

export function SundialTimePicker({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.top) : null), [size, insets.top]);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(SUNDIAL_SKSL), []);
  const numerals = useMemo(() => (g ? carveNumerals(g) : null), [g]);

  const m: Motion = {
    now: useSharedValue(-1),
    mode: useSharedValue(MODE_AUTO),
    autoStart: useSharedValue(0),
    lastTouch: useSharedValue(0),
    touching: useSharedValue(0),
    time: useSharedValue<number[]>([REST_TIME, 0, REST_TIME]),
    onDial: useSharedValue(0),
    returnFrom: useSharedValue(REST_TIME),
    returnStart: useSharedValue(0),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    const prev = m.now.get();
    const dt = prev < 0 ? 0 : Math.min(0.033, now - prev);
    if (prev < 0) m.autoStart.set(now);
    m.now.set(now);
    const mode = m.mode.get();
    if (mode === MODE_HAND) {
      const tm = m.time.get().slice();
      if (m.touching.get() === 1) {
        const next = tm[0] + (tm[2] - tm[0]) * Math.min(1, dt * 30);
        tm[1] = dt > 0 ? tm[1] * 0.6 + (0.4 * (next - tm[0])) / dt : tm[1];
        tm[0] = next;
      } else {
        tm[1] += (-K_SNAP * (tm[0] - tm[2]) - C_SNAP * tm[1]) * dt;
        tm[0] += tm[1] * dt;
        if (now - m.lastTouch.get() > IDLE && Math.abs(tm[1]) < 0.01 && autoPlay) {
          m.mode.set(MODE_RETURN);
          m.returnStart.set(now);
          m.returnFrom.set(tm[0]);
        }
      }
      m.time.set(tm);
    } else if (mode === MODE_RETURN && now - m.returnStart.get() >= RETURN) {
      m.mode.set(MODE_AUTO);
      m.autoStart.set(now);
    }
  });

  /** [hours, finger alpha] for this frame. */
  const state = useDerivedValue(() => {
    const now = m.now.get();
    const mode = m.mode.get();
    if (mode === MODE_HAND) return [m.time.get()[0], m.touching.get()];
    if (mode === MODE_RETURN) {
      const k = easeInOut(Math.min(1, (now - m.returnStart.get()) / RETURN));
      return [m.returnFrom.get() + (REST_TIME - m.returnFrom.get()) * k, 0];
    }
    if (!auto) return [REST_TIME, 0];
    const t = (now - m.autoStart.get()) % LOOP;
    return [timeAt(t), fingerAt(t)];
  });

  const uniforms = useDerivedValue(() => {
    const geo = g ?? makeGeo(1, 1, 0);
    const s = state.get();
    const [sx, sy] = sunOnArc(geo, s[0]);
    return uniformsOf(geo, s[0], [sx, sy, s[1]]);
  });

  const shown = useDerivedValue(() => quarterOf(state.get()[0]));
  const hours = useDerivedValue(() => Math.floor(shown.get() + 1e-6));
  const quarter = useDerivedValue<number>(() => Math.round((shown.get() - Math.floor(shown.get() + 1e-6)) * 4) % 4);
  const altitude = useDerivedValue(() => (Math.asin(Math.max(0, sunDir(state.get()[0])[2])) * 180) / Math.PI);
  const endHours = useDerivedValue(() => Math.floor(shown.get() + 0.75 + 1e-6));
  const endQuarter = useDerivedValue<number>(() => Math.round((shown.get() + 0.75 - Math.floor(shown.get() + 0.75 + 1e-6)) * 4) % 4);

  useAnimatedReaction(
    () => (m.mode.get() === MODE_HAND && m.touching.get() === 1 ? shown.get() : -1),
    (q, prev) => {
      if (q >= 0 && prev !== null && prev >= 0 && q !== prev) scheduleOnRN(tick);
    },
  );

  function grab(x: number, y: number) {
    'worklet';
    if (!g) return;
    const s = state.get();
    const onDial = Math.hypot(x - g.cx, y - g.cy) < g.R ? 1 : 0;
    m.onDial.set(onDial);
    m.mode.set(MODE_HAND);
    m.touching.set(1);
    m.lastTouch.set(m.now.get());
    m.time.set([s[0], 0, rubberTime(timeFromFinger(g, x, y, onDial === 1))]);
  }

  function drag(x: number, y: number) {
    'worklet';
    if (!g || m.touching.get() === 0) return;
    const tm = m.time.get();
    m.time.set([tm[0], tm[1], rubberTime(timeFromFinger(g, x, y, m.onDial.get() === 1))]);
    m.lastTouch.set(m.now.get());
  }

  function release() {
    'worklet';
    if (m.touching.get() === 1) {
      // the release aims at the quarter the drag's momentum carries it to
      const tm = m.time.get();
      m.time.set([tm[0], tm[1], quarterOf(tm[0] + tm[1] * 0.12)]);
    }
    m.touching.set(0);
    m.lastTouch.set(m.now.get());
  }

  const gesture = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      grab(e.x, e.y);
    })
    .onUpdate((e) => {
      drag(e.x, e.y);
    })
    .onFinalize(() => {
      release();
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="adjustable" accessibilityLabel="Sundial time picker. Drag the sun or the shadow to choose a time.">
          {g && effect && numerals ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
              <Picture picture={numerals} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
        <Text style={styles.kicker}>MERIDIAN · NEW EVENT</Text>
        <Text style={styles.title}>Pick a time</Text>
        <Text style={styles.sub}>Tea with Ines · Thursday, 24 October</Text>
      </View>

      {g ? (
        <View pointerEvents="none" style={[styles.readout, { top: g.cy + g.R + 22 * g.unit }]}>
          <View style={styles.timeRow}>
            <RollNumber value={hours} digits={2} pad line={60} style={styles.time} />
            <Text style={[styles.time, styles.colon]}>:</Text>
            <RollLabel labels={QUARTERS} index={quarter} line={60} style={styles.time} />
          </View>
          <View style={styles.metaRow}>
            <Text style={styles.meta}>SUN </Text>
            <RollNumber value={altitude} digits={2} line={16} style={styles.meta} suffix="° HIGH  ·  UNTIL " />
            <RollNumber value={endHours} digits={2} pad line={16} style={styles.meta} />
            <Text style={styles.meta}>:</Text>
            <RollLabel labels={QUARTERS} index={endQuarter} line={16} style={styles.meta} />
          </View>
        </View>
      ) : null}

      <View pointerEvents="none" style={[styles.card, { bottom: insets.bottom + 16 }]}>
        <View style={styles.swatch} />
        <View style={{ flex: 1 }}>
          <Text style={styles.cardTitle}>Tea with Ines</Text>
          <Text style={styles.cardSub}>Garden room · 45 min · Reminder 10 min before</Text>
        </View>
        <View style={styles.button}>
          <Text style={styles.buttonText}>Confirm</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EEE8DF', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kicker: { color: ACCENT, fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  title: { color: INK, fontSize: 34, fontWeight: '800', letterSpacing: -0.6, marginTop: 8 },
  sub: { color: MUTE, fontSize: 15, marginTop: 4 },
  readout: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  timeRow: { flexDirection: 'row', alignItems: 'center' },
  time: { color: INK, fontSize: 52, fontWeight: '700', letterSpacing: -1, fontVariant: ['tabular-nums'] },
  colon: { lineHeight: 60, marginHorizontal: 2, marginTop: -4 },
  metaRow: { flexDirection: 'row', alignItems: 'center', marginTop: 2 },
  meta: { color: MUTE, fontSize: 12, fontWeight: '700', letterSpacing: 1.2, fontVariant: ['tabular-nums'] },
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
    height: 72,
    borderRadius: 22,
    borderCurve: 'continuous',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingLeft: 16,
    paddingRight: 12,
    backgroundColor: '#FBF8F3',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(44,39,34,0.1)',
    boxShadow: '0 1px 3px rgba(70,50,30,0.12), 0 14px 30px rgba(70,50,30,0.12), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  swatch: { width: 6, height: 36, borderRadius: 3, backgroundColor: ACCENT },
  cardTitle: { color: INK, fontSize: 16, fontWeight: '700' },
  cardSub: { color: MUTE, fontSize: 12, marginTop: 2 },
  button: {
    height: 44,
    paddingHorizontal: 18,
    borderRadius: 22,
    justifyContent: 'center',
    backgroundColor: '#2C2722',
    boxShadow: '0 1px 2px rgba(0,0,0,0.2), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  buttonText: { color: '#FBF5EC', fontSize: 15, fontWeight: '700' },
});
