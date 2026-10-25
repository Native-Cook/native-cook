import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo, useState } from 'react';
import { Keyboard, type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { bakeCard, drawScene, makeFonts, recordBed, recordHead, recordReader, type Scene } from './punchCardArt';
import { recordRoom } from './punchCardRoom';
import {
  AUTO_CHAR,
  AUTO_TIME,
  autoBusy,
  autoColumn,
  autoFeed,
  autoField,
  autoLabel,
  autoRegister,
  autoTyped,
  CANVAS_H,
  CANVAS_Y,
  COLS,
  DESIGN_H,
  DESIGN_W,
  FIELDS,
  LOOP,
  SCRIPT,
} from './punchCardMotion';

type Props = {
  /** Types a member card, submits it into the reader and loads a fresh one, every LOOP seconds. */
  autoPlay?: boolean;
};

const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const IDLE = 2;
const LABEL_H = 22;
const LABELS = ['Submit card', 'Reading…', 'Welcome, member'];
const PLACEHOLDERS = ['Your name', 'Year', 'Wing'];
/** Monospace advance of the field's type (Menlo is 0.602 em), for the demo's caret. */
const ADVANCE = 17 * 0.602;
/** The demo's stand-in for "now" in reduced motion: the card typed, before Submit. */
const STILL_T = 5.1;

type Desk = {
  owned: SharedValue<number>;
  idle: SharedValue<number>;
  focused: SharedValue<number>;
  busy: SharedValue<number>;
  returning: SharedValue<number>;
  chars: SharedValue<number[]>;
  times: SharedValue<number[]>;
  clock: SharedValue<number>;
  col: SharedValue<number>;
  feed: SharedValue<number>;
  register: SharedValue<number>;
  ink: SharedValue<number>;
  label: SharedValue<number>;
  t: SharedValue<number>;
};

/** True when no column of the card holds a character. */
function isBlank(chars: number[]) {
  'worklet';
  for (let i = 0; i < chars.length; i += 1) if (chars[i] !== 0) return false;
  return true;
}

/** A finger or a keyboard takes the desk: the demo stops, and a blank card waits at column 1. */
function takeOver(m: Desk, startCol: number) {
  'worklet';
  m.idle.set(0);
  if (m.owned.get() === 1 && m.returning.get() === 0) return;
  cancelAnimation(m.col);
  cancelAnimation(m.ink);
  m.returning.set(0);
  m.owned.set(1);
  m.chars.set(new Array(COLS).fill(0));
  m.times.set(new Array(COLS).fill(-1));
  m.col.set(startCol);
  m.feed.set(0);
  m.register.set(1);
  m.ink.set(1);
  m.label.set(0);
  m.busy.set(0);
}

/** One field's text changed: punch (or clear) the columns that differ, and move the card on. */
function punchField(m: Desk, field: number, codes: number[]) {
  'worklet';
  const f = FIELDS[field];
  const chars = m.chars.get().slice();
  const times = m.times.get().slice();
  const now = m.clock.get();
  for (let i = 0; i < f.size; i += 1) {
    const code = i < codes.length ? codes[i] : 0;
    const k = f.first - 1 + i;
    if (chars[k] !== code) {
      chars[k] = code;
      times[k] = code === 0 ? -1 : now;
    }
  }
  m.chars.set(chars);
  m.times.set(times);
  const next = Math.min(f.first + codes.length, f.first + f.size - 1);
  m.col.set(withDelay(70, withSpring(next, { damping: 18, stiffness: 260, mass: 0.6 })));
  m.idle.set(0);
}

/** Submit: the card goes into the reader, a fresh one registers from the right. */
function submit(m: Desk) {
  'worklet';
  m.idle.set(0);
  m.busy.set(1);
  m.label.set(withSequence(withTiming(1, { duration: 250 }), withDelay(650, withTiming(2, { duration: 250 })), withDelay(1300, withTiming(0, { duration: 300 }))));
  m.feed.set(withTiming(1, { duration: 850, easing: Easing.bezier(0.65, 0, 0.35, 1) }));
  m.register.set(
    withDelay(
      450,
      withTiming(0, { duration: 1500, easing: Easing.bezier(0.2, 0.9, 0.25, 1.06) }, (done) => {
        if (!done) return;
        m.chars.set(new Array(COLS).fill(0));
        m.times.set(new Array(COLS).fill(-1));
        m.col.set(1);
        m.feed.set(0);
        m.register.set(1);
        m.busy.set(0);
        m.idle.set(0);
      }),
    ),
  );
}

const knock = () => {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
};

function clean(text: string, field: number) {
  const up = text.toUpperCase().replace(field === 1 ? /[^0-9]/g : /[^A-Z0-9 -]/g, '');
  return up.slice(0, FIELDS[field].size);
}

export function PunchCardForm({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [values, setValues] = useState(['', '', '']);
  const [caret, setCaret] = useState(-1);
  const [userMode, setUserMode] = useState(false);

  const room = size ? { w: size.w, h: size.h - insets.top - insets.bottom } : null;
  const k = room ? Math.min(room.w / DESIGN_W, room.h / DESIGN_H, 1.15) : 1;
  const ox = room ? (room.w - DESIGN_W * k) / 2 : 0;
  const oy = room ? insets.top + Math.max(0, (room.h - DESIGN_H * k) / 2) : 0;

  const art = useMemo(() => {
    const fonts = makeFonts();
    return { fonts, card: bakeCard(fonts), bed: recordBed(), reader: recordReader(fonts), head: recordHead() };
  }, []);
  const roomPicture = useMemo(() => (size ? recordRoom(size.w, size.h) : null), [size]);

  const t = useSharedValue(reduced ? STILL_T : 0);
  const owned = useSharedValue(0);
  const idle = useSharedValue(0);
  const focused = useSharedValue(0);
  const busy = useSharedValue(0);
  const returning = useSharedValue(0);
  const chars = useSharedValue<number[]>(new Array(COLS).fill(0));
  const times = useSharedValue<number[]>(new Array(COLS).fill(-1));
  const clock = useSharedValue(0);
  const col = useSharedValue(1);
  const feed = useSharedValue(0);
  const register = useSharedValue(1);
  const ink = useSharedValue(1);
  const label = useSharedValue(0);
  const m: Desk = { owned, idle, focused, busy, returning, chars, times, clock, col, feed, register, ink, label, t };

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    clock.set(clock.get() + dt);
    if (owned.get() === 0) {
      if (auto) t.set((t.get() + dt) % LOOP);
      return;
    }
    if (focused.get() === 1 || busy.get() === 1 || returning.get() === 1) return;
    idle.set(idle.get() + dt);
    if (idle.get() < IDLE) return;
    // Back to rest within 0.9 s: the holes fade as the card slides back to column 1.
    returning.set(1);
    ink.set(withTiming(0, { duration: 320 }));
    col.set(
      withTiming(1, { duration: 760, easing: Easing.bezier(0.65, 0, 0.35, 1) }, (done) => {
        if (!done) return;
        chars.set(new Array(COLS).fill(0));
        times.set(new Array(COLS).fill(-1));
        ink.set(1);
        returning.set(0);
        owned.set(0);
        t.set(0);
      }),
    );
  });

  function showDemo(n: number, field: number) {
    setValues([SCRIPT[0].slice(0, Math.min(n, 3)), SCRIPT[1].slice(0, Math.max(0, Math.min(n - 3, 4))), SCRIPT[2].slice(0, Math.max(0, n - 7))]);
    setCaret(field);
  }

  function clearValues() {
    setValues(['', '', '']);
  }

  // The fields follow the demo's keystrokes (a dozen changes a loop, never per frame).
  useAnimatedReaction(
    () => (owned.get() === 0 ? autoTyped(t.get()) * 10 + (autoField(t.get()) + 1) : -1),
    (now, before) => {
      if (now < 0 || now === before) return;
      scheduleOnRN(showDemo, Math.floor(now / 10), (now % 10) - 1);
    },
  );
  useAnimatedReaction(
    () => owned.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setUserMode, now === 1);
    },
  );
  // A fresh card after Submit (or after the return) empties the fields.
  useAnimatedReaction(
    () => (owned.get() === 1 && register.get() === 1 && feed.get() === 0 && isBlank(chars.get()) ? 1 : 0),
    (now, before) => {
      if (now === 1 && before === 0) scheduleOnRN(clearValues);
    },
  );

  const onFocus = (field: number) => {
    focused.set(1);
    setCaret(-1);
    if (!userMode) setValues(['', '', '']);
    scheduleOnUI(takeOver, m, FIELDS[field].first);
  };

  const onBlur = () => {
    focused.set(0);
    idle.set(0);
  };

  const onChange = (field: number, text: string) => {
    const next = clean(text, field);
    if (next === values[field]) return;
    const copy = values.slice();
    copy[field] = next;
    setValues(copy);
    if (next.length > values[field].length) knock();
    const codes = Array.from(next).map((ch) => ch.charCodeAt(0));
    scheduleOnUI(punchField, m, field, codes);
  };

  const onSubmit = () => {
    Keyboard.dismiss();
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    scheduleOnUI(takeOver, m, 1);
    scheduleOnUI(submit, m);
  };

  const picture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, DESIGN_W, CANVAS_H));
    const user = owned.get() === 1;
    const now = t.get();
    let scene: Scene;
    if (user) {
      scene = { chars: chars.get(), times: times.get(), now: clock.get(), col: col.get(), feed: feed.get(), register: register.get(), busy: busy.get() === 1 ? 1 : 0, t: now, ink: ink.get() };
    } else if (reduced) {
      // Reduced motion: the typed card, still, before it is submitted (no chads in the air).
      scene = { chars: AUTO_CHAR, times: AUTO_TIME, now: LOOP, col: autoColumn(STILL_T), feed: 0, register: 1, busy: 0, t: 0, ink: 1 };
    } else {
      scene = { chars: AUTO_CHAR, times: AUTO_TIME, now, col: autoColumn(now), feed: autoFeed(now), register: autoRegister(now), busy: autoBusy(now), t: now, ink: 1 };
    }
    // While the user owns it the lamps' pattern runs on the user clock, which keeps them moving.
    if (user) scene.t = clock.get() % LOOP;
    drawScene(canvas, scene, art.card, art.bed, art.reader, art.head, art.fonts.print);
    return rec.finishRecordingAsPicture();
  });

  const labelStyle = useAnimatedStyle(() => {
    const at = owned.get() === 1 ? label.get() : autoLabel(t.get());
    return { transform: [{ translateY: -at * LABEL_H }] };
  });
  const blink = useAnimatedStyle(() => ({ opacity: Math.sin((2 * Math.PI * 9 * t.get()) / LOOP) > -0.2 ? 1 : 0 }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {roomPicture ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={roomPicture} />
        </Canvas>
      ) : null}
      {size ? (
        <View style={[styles.design, { left: ox, top: oy, transform: [{ scale: k }] }]}>
          <View style={styles.header}>
            <Text style={styles.kicker}>ANALYTICAL SOCIETY</Text>
            <Text style={styles.title}>Member card</Text>
            <Text style={styles.sub}>Each letter is punched into its own column.</Text>
          </View>

          <View style={styles.fields}>
            {FIELDS.map((f, i) => (
              <View key={f.label} style={[styles.field, { flex: i === 0 ? 1.7 : i === 1 ? 1 : 1.3 }, caret === i && styles.fieldActive]}>
                <Text style={styles.fieldLabel}>
                  {f.label} · {f.first}–{f.first + f.size - 1}
                </Text>
                <View>
                  <TextInput
                    value={values[i]}
                    onChangeText={(text) => onChange(i, text)}
                    onFocus={() => onFocus(i)}
                    onBlur={onBlur}
                    placeholder={PLACEHOLDERS[i]}
                    placeholderTextColor="rgba(236,228,206,0.32)"
                    autoCapitalize="characters"
                    autoCorrect={false}
                    spellCheck={false}
                    keyboardType={i === 1 ? 'number-pad' : 'default'}
                    maxLength={f.size}
                    returnKeyType="next"
                    style={styles.input}
                    accessibilityLabel={`${f.label}, columns ${f.first} to ${f.first + f.size - 1}`}
                  />
                  {caret === i && !userMode ? <Animated.View style={[styles.caret, { left: values[i].length * ADVANCE }, blink]} /> : null}
                </View>
              </View>
            ))}
          </View>

          <Pressable style={styles.hero} onPress={() => Keyboard.dismiss()} accessible={false}>
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </Pressable>

          <Pressable onPress={onSubmit} accessibilityRole="button" accessibilityLabel="Submit card" style={({ pressed }) => [styles.button, pressed && styles.pressed]}>
            <LinearGradient colors={['#F3CF86', '#E2AE57', '#C68D39']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
            <View style={styles.labelBox}>
              <Animated.View style={labelStyle}>
                {LABELS.map((l) => (
                  <Text key={l} style={styles.buttonText}>
                    {l}
                  </Text>
                ))}
              </Animated.View>
            </View>
          </Pressable>
          <Text style={styles.foot}>Cards are read at the front desk · Gallery 3</Text>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#121613', overflow: 'hidden' },
  design: { position: 'absolute', width: DESIGN_W, height: DESIGN_H, transformOrigin: 'top left' },
  header: { position: 'absolute', left: 24, right: 24, top: 8, gap: 2 },
  kicker: { color: '#E2B05C', fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: '#EFE8D6', fontSize: 32, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: 'rgba(239,232,214,0.6)', fontSize: 14, fontWeight: '500' },
  fields: { position: 'absolute', left: 16, right: 16, top: 98, height: 58, flexDirection: 'row', gap: 8 },
  field: {
    borderRadius: 14,
    borderCurve: 'continuous',
    paddingHorizontal: 12,
    paddingTop: 8,
    backgroundColor: 'rgba(236,228,206,0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(236,228,206,0.16)',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.05), 0 1px 2px rgba(0,0,0,0.4)',
  },
  fieldActive: { borderColor: 'rgba(226,176,92,0.7)', backgroundColor: 'rgba(226,176,92,0.08)' },
  fieldLabel: { color: 'rgba(236,228,206,0.55)', fontSize: 9, fontWeight: '700', letterSpacing: 1.2, fontVariant: ['tabular-nums'] },
  input: { color: '#F2EBD8', fontFamily: MONO, fontSize: 17, fontWeight: '700', padding: 0, height: 28, marginTop: 2 },
  caret: { position: 'absolute', top: 5, width: 2, height: 20, borderRadius: 1, backgroundColor: '#E2B05C' },
  hero: { position: 'absolute', left: 0, top: CANVAS_Y, width: DESIGN_W, height: CANVAS_H },
  button: {
    position: 'absolute',
    left: 16,
    right: 16,
    top: 632,
    height: 52,
    borderRadius: 26,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(0,0,0,0.45), 0 10px 24px rgba(198,141,57,0.25), inset 0 1px 0 rgba(255,246,220,0.6)',
  },
  pressed: { transform: [{ scale: 0.97 }] },
  labelBox: { height: LABEL_H, overflow: 'hidden' },
  buttonText: { height: LABEL_H, lineHeight: LABEL_H, color: '#2A1A06', fontSize: 17, fontWeight: '700', textAlign: 'center' },
  foot: { position: 'absolute', left: 24, right: 24, top: 700, textAlign: 'center', color: 'rgba(239,232,214,0.45)', fontSize: 12, fontWeight: '500' },
});
