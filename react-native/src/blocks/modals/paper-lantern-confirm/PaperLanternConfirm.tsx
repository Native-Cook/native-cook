import { Canvas, Fill, Group, Rect, Shader, Skia, Text as SkText, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  LOOP,
  type Lamp,
  REST_FOLD,
  SHEET_H,
  type Scene,
  blendLamp,
  copyLamp,
  easeInOut,
  loopScene,
  makeLamp,
  makeLayout,
  makeScene,
  nextLabel,
} from './lanternMotion';
import { LANTERN_SKSL, SKY_SKSL } from './lanternShader';

/*
 Paper Lantern Confirm — "Lantern Night", a wishes app's confirm sheet. A folded paper sky lantern
 stands in the sheet with the wish written on it. Confirm and the wick lights, the paper glows from
 inside, the lantern fills, lifts off out of the sheet and rises into the night among the other
 lanterns, smaller with distance; a "Wish sent" toast drops in, and a new lantern unfolds.

 Drawing: two Canvases. Behind the sheet, the sky (SKY_SKSL: gradient, stars, moon, hills and the
 far lanterns). Above the sheet, a transparent canvas with two lanterns (LANTERN_SKSL in a rect
 under an animated transform, the wish in Skia text): the one in the sheet and the one in flight.
 Everything moving reads one scene object written per frame on the UI thread (lanternMotion.ts).

 Touch: hold the lantern to light it (the wick catches in 0.5 s, then it fills in about a second);
 drag it up while you hold it; let go of a full lantern above its seat, or flick it up, and it flies
 off into the loop's rise. A lantern let go before it is full sinks back to its seat and folds
 again; 2 s later it eases back to rest (0.9 s) and the loop restarts. Tap "Light and send" to play
 the send. Reduced motion: no loop; the lantern stands lit and full in the sheet.
*/

type Props = {
  /** Plays the 9 s light-fill-rise-toast-new-lantern loop. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
const IDLE = 2;
const RETURN = 0.9;
const LIFT_OFF = 3.0;
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });

const PAPER = '#F5EDDF';
const INK = '#2E2119';
const INK_SOFT = 'rgba(46,33,25,0.6)';
const AMBER = '#E9822F';
const sky = Skia.RuntimeEffect.Make(SKY_SKSL);
const lantern = Skia.RuntimeEffect.Make(LANTERN_SKSL);
const LABELS = ['Light and send', 'Lighting the wick…', 'Wish sent'];
const ROLL = [-1, 0, 1, 2, 3];
const WISH = ['Safe travels', 'home, Amara'];

function feel(kind: number) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 1) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** Past `limit`, a pull gives less and less. */
function rubber(x: number, limit: number) {
  'worklet';
  if (x <= limit) return x;
  const over = x - limit;
  return limit + over / (1 + over / 60);
}

export function PaperLanternConfirm({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const L = useMemo(() => makeLayout(size.width, size.height, insets.bottom), [size.width, size.height, insets.bottom]);
  const font = useMemo(() => matchFont({ fontFamily: SERIF, fontSize: 19, fontStyle: 'italic' }), []);
  const sign = useMemo(() => matchFont({ fontFamily: SERIF, fontSize: 13, fontStyle: 'italic' }), []);
  const lines = useMemo(() => {
    const measure = (f: typeof font, s: string) => f.getGlyphWidths(f.getGlyphIDs(s)).reduce((a, b) => a + b, 0);
    return { a: measure(font, WISH[0]), b: measure(font, WISH[1]), c: measure(sign, 'love, June') };
  }, [font, sign]);

  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  // The sky and the flames never stop, even under a finger (in the loop alone it equals the clock).
  const skyClock = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const scene = useSharedValue<Scene>(makeScene());
  const from = useSharedValue<Lamp>(makeLamp());
  const labelPos = useSharedValue(0);
  // The lantern under a finger: [glow, fold, dy, vy, target dy, holding].
  const hand = useSharedValue<number[]>([0, REST_FOLD, 0, 0, 0, 0]);
  // After a launch the flyer starts where the finger let go: [dy, at].
  const launch = useSharedValue<number[]>([0, -10]);

  useFrameCallback((f) => {
    const dt = Math.min((f.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    skyClock.set((skyClock.get() + dt) % LOOP);
    const s = makeScene();
    const m = mode.get();
    if (reduced) {
      loopScene(2.99, L, s);
    } else if (m === MODE_AUTO) {
      const t = auto || clock.get() > 0 ? (clock.get() + dt) % LOOP : 0;
      // Without autoPlay a send plays once and stops back at rest.
      clock.set(!auto && clock.get() + dt >= LOOP ? 0 : t);
      loopScene(clock.get(), L, s);
      const l = launch.get();
      const off = l[0] * Math.exp(-(at - l[1]) * 4);
      s.fly.y += off;
    } else if (m === MODE_USER) {
      loopScene(0, L, s);
      const h = hand.get().slice();
      const holding = h[5];
      if (holding) {
        h[0] = Math.min(1, h[0] + dt / 0.5);
        if (h[0] > 0.6) h[1] = Math.max(0, h[1] - dt / 1.0);
      } else {
        h[0] = Math.max(0, h[0] - dt / 1.5);
        h[1] = Math.min(REST_FOLD, h[1] + dt / 2.5);
      }
      // The lantern follows the finger on a spring (response ~0.35 s, a little bounce).
      const k = 170;
      const c = 2 * 0.7 * Math.sqrt(k);
      h[3] += (k * (h[4] - h[2]) - c * h[3]) * dt;
      h[2] += h[3] * dt;
      hand.set(h);
      s.seat.glow = h[0];
      s.seat.fold = h[1];
      s.seat.y = L.seatY + h[2];
      s.seat.o = 1;
      if (!holding && at - lastTouch.get() > IDLE) {
        copyLamp(s.seat, from.get());
        returnAt.set(at);
        mode.set(MODE_RETURN);
      }
    } else {
      const e = easeInOut((at - returnAt.get()) / RETURN);
      loopScene(0, L, s);
      const out = makeLamp();
      blendLamp(from.get(), s.seat, e, out);
      copyLamp(out, s.seat);
      if (e >= 1) {
        mode.set(MODE_AUTO);
        clock.set(0);
      }
    }
    scene.set(s);
    // The button's label rolls forward to the scene's label (index mod 3).
    let pos = labelPos.get();
    let target = nextLabel(pos, s.label % 3);
    if (pos >= 2 && target >= 3) {
      pos -= 3;
      target -= 3;
    }
    pos = reduced ? target : pos + (target - pos) * Math.min(1, dt * 9);
    if (Math.abs(target - pos) < 0.002) pos = target;
    labelPos.set(pos);
  });

  function takeOver() {
    'worklet';
    const s = scene.get();
    if (mode.get() === MODE_USER) return true;
    // Only a lantern standing in the sheet can be held.
    if (s.seat.o < 0.95 || s.fly.o > 0.02) return false;
    hand.set([s.seat.glow, s.seat.fold, s.seat.y - L.seatY, 0, 0, 1]);
    mode.set(MODE_USER);
    return true;
  }

  const hold = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin(() => {
      if (!takeOver()) return;
      const h = hand.get().slice();
      h[5] = 1;
      hand.set(h);
      lastTouch.set(now.get());
      scheduleOnRN(feel, 0);
    })
    .onUpdate((e) => {
      if (mode.get() !== MODE_USER) return;
      const h = hand.get().slice();
      // Up is free (a full lantern pulls a little), down is rubber-banded.
      h[4] = -rubber(-e.translationY, 140) * (h[1] < 0.1 ? 1 : 0.6);
      if (e.translationY > 0) h[4] = rubber(e.translationY, 0) * 0.5;
      hand.set(h);
      lastTouch.set(now.get());
    })
    .onEnd((e) => {
      if (mode.get() !== MODE_USER) return;
      const h = hand.get();
      if (h[1] < 0.12 && h[0] > 0.9 && (h[2] < -30 || e.velocityY < -400)) {
        // Away it goes: the loop's rise takes over from where the finger let go.
        launch.set([h[2], now.get()]);
        clock.set(LIFT_OFF);
        mode.set(MODE_AUTO);
        scheduleOnRN(feel, 1);
      }
    })
    .onFinalize(() => {
      if (mode.get() !== MODE_USER) return;
      const h = hand.get().slice();
      h[4] = 0;
      h[5] = 0;
      hand.set(h);
      lastTouch.set(now.get());
    });

  const send = Gesture.Tap()
    .enabled(!reduced)
    .onStart(() => {
      const m = mode.get();
      const t = clock.get();
      const atRest = (m === MODE_AUTO && (t < 1.1 || t >= 8.2)) || (m === MODE_USER && hand.get()[0] < 0.05);
      if (!atRest) return;
      launch.set([0, -10]);
      clock.set(1.1);
      mode.set(MODE_AUTO);
      scheduleOnRN(feel, 0);
    });

  const skyUniforms = useDerivedValue(() => ({ size: [size.width, size.height], horizon: L.sheetTop + 26, phase: skyClock.get() / LOOP }));
  const seatT = useDerivedValue(() => {
    const p = scene.get().seat;
    return [{ translateX: p.x }, { translateY: p.y }, { scaleX: p.sx }, { scaleY: p.sy }];
  });
  const flyT = useDerivedValue(() => {
    const p = scene.get().fly;
    return [{ translateX: p.x }, { translateY: p.y }, { scaleX: p.sx }, { scaleY: p.sy }];
  });
  const seatO = useDerivedValue(() => scene.get().seat.o);
  const flyO = useDerivedValue(() => scene.get().fly.o);
  const seatU = useDerivedValue(() => {
    const p = scene.get().seat;
    return { fold: p.fold, glow: p.glow, flicker: skyClock.get() / LOOP, seed: 3 };
  });
  const flyU = useDerivedValue(() => {
    const p = scene.get().fly;
    return { fold: p.fold, glow: p.glow, flicker: skyClock.get() / LOOP, seed: 7 };
  });
  // The wish fades from the paper as it creases flat, and darkens against the glow.
  const seatInk = useDerivedValue(() => 0.82 * (1 - Math.max(0, scene.get().seat.fold - REST_FOLD) / (1 - REST_FOLD)));

  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * scene.get().press }] }));
  const toastStyle = useAnimatedStyle(() => {
    const k = scene.get().toast;
    return { opacity: k, transform: [{ translateY: -70 * (1 - k) }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };
  const wishY = -22;

  function wish(ink: SharedValue<number> | number) {
    return (
      <Group opacity={ink}>
        <SkText x={-lines.a / 2} y={wishY} text={WISH[0]} font={font} color="#5B3B28" />
        <SkText x={-lines.b / 2} y={wishY + 24} text={WISH[1]} font={font} color="#5B3B28" />
        <SkText x={-lines.c / 2} y={wishY + 50} text="love, June" font={sign} color="#7A5640" />
      </Group>
    );
  }

  return (
    <View style={styles.root} onLayout={onLayout}>
      {sky ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Fill>
            <Shader source={sky} uniforms={skyUniforms} />
          </Fill>
        </Canvas>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
        <Text style={styles.kicker}>LANTERN NIGHT</Text>
        <Text style={styles.headTitle}>Riverside · Friday 8:40 pm</Text>
      </View>

      <View style={[styles.sheet, { top: L.sheetTop, paddingBottom: insets.bottom }]}>
        <View style={styles.grabber} />
        <Text style={styles.title}>Send your wish?</Text>
        <Text style={styles.sub}>It will rise with 1,283 other lanterns tonight</Text>
        <View style={styles.seatSpace} />
        <View style={styles.chips}>
          <Chip icon={{ ios: 'person.fill', android: 'person', web: 'person' }} label="From June" />
          <Chip icon={{ ios: 'heart.fill', android: 'favorite', web: 'favorite' }} label="$5 · River Fund" />
        </View>
        <GestureDetector gesture={send}>
          <Animated.View style={pressStyle} accessible accessibilityRole="button" accessibilityLabel="Light and send">
            <LinearGradient colors={['#F4A24A', '#E9822F', '#C9561F']} locations={[0, 0.5, 1]} start={{ x: 0, y: 0 }} end={{ x: 0, y: 1 }} style={styles.button}>
              <Roller pos={labelPos} />
            </LinearGradient>
          </Animated.View>
        </GestureDetector>
        <Text style={styles.edit}>Edit wish</Text>
      </View>

      <GestureDetector gesture={hold}>
        <View style={[styles.holdBox, { left: L.seatX - 80, top: L.seatY - 110 }]} accessible accessibilityLabel="Paper lantern. Hold to light it, then drag it up to let it fly." />
      </GestureDetector>

      {lantern ? (
        <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Group transform={seatT} opacity={seatO}>
            <Rect x={-90} y={-130} width={180} height={240}>
              <Shader source={lantern} uniforms={seatU} />
            </Rect>
            {wish(seatInk)}
          </Group>
          <Group transform={flyT} opacity={flyO}>
            <Rect x={-90} y={-130} width={180} height={240}>
              <Shader source={lantern} uniforms={flyU} />
            </Rect>
            {wish(0.82)}
          </Group>
        </Canvas>
      ) : null}

      <Animated.View pointerEvents="none" style={[styles.toast, { top: insets.top + 10 }, toastStyle]}>
        <View style={styles.toastIcon}>
          <SymbolView name={{ ios: 'checkmark', android: 'check', web: 'check' }} size={14} weight="bold" tintColor="#2A1708" fallback={<View style={styles.toastDot} />} />
        </View>
        <View>
          <Text style={styles.toastTitle}>Wish sent</Text>
          <Text style={styles.toastSub}>Lantern 1,284 is in the sky</Text>
        </View>
      </Animated.View>
    </View>
  );
}

function RollLine({ i, pos }: { i: number; pos: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = i - pos.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.2), transform: [{ translateY: d * 24 }] };
  });
  return (
    <Animated.Text style={[styles.buttonText, style]} numberOfLines={1}>
      {LABELS[(i + 3) % 3]}
    </Animated.Text>
  );
}

/** The button's label in a clipped box: the old one rolls up and out, the new one in from below. */
function Roller({ pos }: { pos: SharedValue<number> }) {
  return (
    <View style={styles.roll}>
      {ROLL.map((i) => (
        <RollLine key={i} i={i} pos={pos} />
      ))}
    </View>
  );
}

function Chip({ icon, label }: { icon: SymbolViewProps['name']; label: string }) {
  return (
    <View style={styles.chip}>
      <SymbolView name={icon} size={13} tintColor={AMBER} fallback={<View style={styles.chipDot} />} />
      <Text style={styles.chipText}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#0A0D22' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: '#F7B266', fontSize: 11, fontWeight: '800', letterSpacing: 2.2 },
  headTitle: { color: 'rgba(250,236,220,0.8)', fontSize: 15, fontWeight: '600' },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    minHeight: SHEET_H,
    paddingHorizontal: 20,
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    borderCurve: 'continuous',
    backgroundColor: PAPER,
    alignItems: 'stretch',
    boxShadow: '0 -1px 0 rgba(255,255,255,0.6), 0 -20px 50px rgba(5,5,20,0.45)',
  },
  grabber: { alignSelf: 'center', width: 40, height: 5, borderRadius: 3, marginTop: 8, backgroundColor: 'rgba(46,33,25,0.18)' },
  title: { color: INK, fontSize: 24, fontWeight: '800', letterSpacing: -0.4, textAlign: 'center', marginTop: 14 },
  sub: { color: INK_SOFT, fontSize: 14, fontWeight: '500', textAlign: 'center', marginTop: 4, fontVariant: ['tabular-nums'] },
  seatSpace: { height: 206 },
  chips: { flexDirection: 'row', gap: 8, justifyContent: 'center', marginBottom: 14 },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 30, paddingHorizontal: 12, borderRadius: 15, backgroundColor: 'rgba(233,130,47,0.1)' },
  chipDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: AMBER },
  chipText: { color: INK, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  button: { height: 52, borderRadius: 26, justifyContent: 'center', boxShadow: '0 1px 1px rgba(90,40,10,0.3), 0 10px 22px rgba(233,130,47,0.35), inset 0 1px 0 rgba(255,230,190,0.6)' },
  roll: { height: 24, overflow: 'hidden', marginHorizontal: 20 },
  buttonText: { position: 'absolute', left: 0, right: 0, height: 24, lineHeight: 24, textAlign: 'center', color: '#2A1708', fontSize: 17, fontWeight: '700' },
  edit: { color: INK_SOFT, fontSize: 15, fontWeight: '600', textAlign: 'center', paddingVertical: 12 },
  holdBox: { position: 'absolute', width: 160, height: 190 },
  toast: {
    position: 'absolute',
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingLeft: 8,
    paddingRight: 18,
    height: 52,
    borderRadius: 26,
    backgroundColor: 'rgba(32,24,44,0.94)',
    borderWidth: 1,
    borderColor: 'rgba(247,178,102,0.3)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 12px 30px rgba(0,0,0,0.4)',
  },
  toastIcon: { width: 36, height: 36, borderRadius: 18, backgroundColor: '#F7B266', alignItems: 'center', justifyContent: 'center' },
  toastDot: { width: 10, height: 10, borderRadius: 5, backgroundColor: '#2A1708' },
  toastTitle: { color: '#FAEEDC', fontSize: 15, fontWeight: '700' },
  toastSub: { color: 'rgba(250,238,220,0.66)', fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
});
