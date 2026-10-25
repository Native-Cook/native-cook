import {
  Canvas,
  Circle,
  Fill,
  Glyphs,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  RoundedRect,
  Shader,
  Shadow,
  Skia,
  matchFont,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { GRAIN_SKSL, LINE_SKSL, SCREEN_SKSL, freePath, knobRidges, linePath } from './etchArt';
import { type Etch, LOOP, PT_PER_TURN, SWEEP_END, clamp01, easeInOut, etchAt, seg, shake, signature } from './etchMotion';

type Props = {
  /** Turns the knobs to sign a stair-stepped "J" with a flourish, then shakes it clear, every 10 s. */
  autoPlay?: boolean;
};

const IOS = process.env.EXPO_OS === 'ios';
/** Seconds after the last touch before the loop starts again. */
const IDLE = 2;
/** Stylus travel per point of finger travel on a knob. */
const KNOB_GAIN = 0.9;
const KNOB_R = 32;
/** A user clear: wobble and sweep, seconds. */
const CLEAR_LEN = 1.4;

const INK = '#1F2328';
const MUTE = 'rgba(31,35,40,0.58)';
const RED = ['#E5493D', '#CB3129', '#A2221C'];

export function EtchSketchSignature({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const looping = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const W = size.width;
  const H = size.height;

  // Layout (pt): the toy fills the space between the parcel details and the buttons.
  const fx = 16;
  const fy = insets.top + 124;
  const fw = Math.max(200, W - 32);
  const buttonsTop = H - insets.bottom - 16 - 52;
  const fh = Math.max(260, buttonsTop - 44 - fy);
  const scr = { x: fx + 26, y: fy + 30, w: fw - 52, h: fh - 30 - 92 };
  const knobY = fy + fh - 46;
  const knobLX = fx + 58;
  const knobRX = fx + fw - 58;
  const sig = useMemo(() => signature(scr.x, scr.y, scr.w, scr.h), [scr.x, scr.y, scr.w, scr.h]);
  const restX = sig.pts[0];
  const restY = sig.pts[1];

  const clock = useSharedValue(0);
  const user = useSharedValue(0);
  const active = useSharedValue(0);
  const idle = useSharedValue(0);
  const returning = useSharedValue(0);
  const clearing = useSharedValue(0);
  const uClear = useSharedValue(0);
  const uPts = useSharedValue<number[]>([]);
  const uSx = useSharedValue(0);
  const uSy = useSharedValue(0);
  const uTx = useSharedValue(0);
  const uTy = useSharedValue(0);
  const following = useSharedValue(0);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Moves the stylus, scraping a line behind it. */
  function scrape(x: number, y: number) {
    'worklet';
    const nx = Math.min(scr.x + scr.w - 6, Math.max(scr.x + 6, x));
    const ny = Math.min(scr.y + scr.h - 6, Math.max(scr.y + 6, y));
    const px = uSx.get();
    const py = uSy.get();
    if (Math.hypot(nx - px, ny - py) < 0.8) return;
    uSx.set(nx);
    uSy.set(ny);
    if (clearing.get() === 1) return;
    uPts.modify((a) => {
      'worklet';
      if (a.length === 0) a.push(px, py);
      if (a.length < 6000) a.push(nx, ny);
      return a;
    });
  }

  /** The finger takes over: what the loop has drawn becomes the finger's own line. */
  function takeOver() {
    'worklet';
    if (user.get() === 1) return;
    const e = etchAt(clock.get(), sig.pts, sig.run, sig.total, scr.y, scr.h);
    const pts: number[] = [];
    if (e.sweep < -1000 && e.drawn > 0) {
      for (let k = 0; k < sig.run.length; k += 1) {
        if (sig.run[k] > e.drawn) break;
        pts.push(sig.pts[2 * k], sig.pts[2 * k + 1]);
      }
      pts.push(e.sx, e.sy);
    }
    uPts.set(pts);
    uSx.set(e.sx);
    uSy.set(e.sy);
    uClear.set(0);
    clearing.set(0);
    user.set(1);
  }

  /** Shake: the toy wobbles and the powder resettles from the top, wiping the line. */
  function clearAll(duration: number) {
    'worklet';
    if (clearing.get() === 1) return;
    clearing.set(1);
    uClear.set(0);
    uClear.set(
      withTiming(1, { duration, easing: Easing.linear }, (done) => {
        if (!done) return;
        uPts.set([]);
        clearing.set(0);
        uClear.set(0);
      }),
    );
  }

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (user.get() === 1) {
      if (following.get() === 1) {
        // On the screen the stylus chases the finger, as fast as two quick hands could turn the knobs.
        const k = Math.min(1, dt * 16);
        scrape(uSx.get() + (uTx.get() - uSx.get()) * k, uSy.get() + (uTy.get() - uSy.get()) * k);
      }
      if (active.get() === 0 && returning.get() === 0) {
        idle.set(idle.get() + dt);
        if (idle.get() > IDLE) {
          returning.set(1);
          if (uPts.get().length > 0) clearAll(900);
          const ease = Easing.bezier(0.77, 0, 0.175, 1);
          uSx.set(withTiming(restX, { duration: 900, easing: ease }));
          uSy.set(
            withTiming(restY, { duration: 900, easing: ease }, (done) => {
              if (!done) return;
              // Back in the loop where the screen is clear and the pen about to set off.
              clock.set(SWEEP_END);
              user.set(0);
              returning.set(0);
            }),
          );
        }
      }
      return;
    }
    if (looping) clock.set((clock.get() + dt) % LOOP);
  });

  const etch = useDerivedValue<Etch>(() => {
    if (user.get() === 0) return etchAt(clock.get(), sig.pts, sig.run, sig.total, scr.y, scr.h);
    const c = uClear.get();
    const sw = easeInOut(seg(c, 0.12, 0.85));
    const sweeping = clearing.get() === 1;
    return {
      drawn: 0,
      sx: uSx.get(),
      sy: uSy.get(),
      sweep: sweeping ? scr.y - 30 + (scr.h + 60) * sw : -1e4,
      band: sweeping ? Math.sin(Math.PI * sw) : 0,
      wobble: sweeping ? shake(c * CLEAR_LEN, CLEAR_LEN) : 0,
      signed: uPts.get().length > 8 && !sweeping ? 1 : 0,
      t: 0,
    };
  });

  const line = useDerivedValue(() => (user.get() === 1 ? freePath(uPts.get()) : linePath(sig.pts, sig.run, etch.get().drawn)));
  const lineU = useDerivedValue(() => ({ sweep: etch.get().sweep, ink: [0.16, 0.17, 0.19, 1] }));
  const lipU = useDerivedValue(() => ({ sweep: etch.get().sweep, ink: [0.97, 0.98, 1, 0.6] }));
  const screenU = useDerivedValue(() => {
    const e = etch.get();
    return { rect: [scr.x, scr.y, scr.w, scr.h], tilt: e.wobble, sweep: e.sweep, band: e.band };
  });
  const toyT = useDerivedValue(() => {
    const w = etch.get().wobble;
    const cx = fx + fw / 2;
    const cy = fy + fh / 2;
    return [{ translateX: cx + w * 140 }, { translateY: cy }, { rotate: w }, { translateX: -cx }, { translateY: -cy }];
  });
  const stylus = useDerivedValue(() => vec(etch.get().sx, etch.get().sy));
  const knobLT = useDerivedValue(() => [{ translateX: knobLX }, { translateY: knobY }, { rotate: ((etch.get().sx - restX) / PT_PER_TURN) * Math.PI * 2 }]);
  const knobRT = useDerivedValue(() => [{ translateX: knobRX }, { translateY: knobY }, { rotate: ((etch.get().sy - restY) / PT_PER_TURN) * Math.PI * 2 }]);

  const fx_ = useMemo(
    () => ({ screen: Skia.RuntimeEffect.Make(SCREEN_SKSL), line: Skia.RuntimeEffect.Make(LINE_SKSL), grain: Skia.RuntimeEffect.Make(GRAIN_SKSL) }),
    [],
  );
  const ridges = useMemo(() => knobRidges(KNOB_R), []);
  const label = useMemo(() => {
    const font = matchFont({ fontFamily: IOS ? 'Helvetica Neue' : 'sans-serif', fontSize: 11, fontWeight: '700' });
    const ids = font.getGlyphIDs('SIGN HERE');
    const ws = font.getGlyphWidths(ids);
    const track = 3;
    const glyphs: { id: number; pos: ReturnType<typeof vec> }[] = [];
    let x = W / 2 - (ws.reduce((a, b) => a + b, 0) + track * (ids.length - 1)) / 2;
    for (let i = 0; i < ids.length; i += 1) {
      glyphs.push({ id: ids[i], pos: vec(x, knobY + 4) });
      x += ws[i] + track;
    }
    return { font, glyphs };
  }, [W, knobY]);

  // Knobs: drag up and down on one to turn it. Left draws across, right draws up and down.
  const prevX = useSharedValue(0);
  const prevY = useSharedValue(0);
  const knob = (dir: 'x' | 'y') =>
    Gesture.Pan()
      .minDistance(0)
      .onStart(() => {
        prevX.set(0);
        prevY.set(0);
        takeOver();
        active.set(1);
        idle.set(0);
        following.set(0);
        scheduleOnRN(feel);
      })
      .onUpdate((e) => {
        idle.set(0);
        const dx = e.translationX - prevX.get();
        const dy = e.translationY - prevY.get();
        prevX.set(e.translationX);
        prevY.set(e.translationY);
        if (dir === 'x') scrape(uSx.get() - dy * KNOB_GAIN + dx * 0.3, uSy.get());
        else scrape(uSx.get(), uSy.get() + dy * KNOB_GAIN - dx * 0.3);
      })
      .onFinalize(() => {
        active.set(0);
      });
  const left = knob('x');
  const right = knob('y');

  // On the screen: one finger draws (the stylus follows it), two fingers shake the toy clear.
  const draw = Gesture.Pan()
    .minDistance(0)
    .maxPointers(2)
    .onStart((e) => {
      takeOver();
      active.set(1);
      idle.set(0);
      uTx.set(e.x);
      uTy.set(e.y);
      following.set(e.numberOfPointers > 1 ? 0 : 1);
    })
    .onUpdate((e) => {
      idle.set(0);
      if (e.numberOfPointers > 1) {
        following.set(0);
        if (Math.hypot(e.translationX, e.translationY) > 24 && clearing.get() === 0) {
          clearAll(CLEAR_LEN * 1000);
          scheduleOnRN(feel);
        }
        return;
      }
      uTx.set(e.x);
      uTy.set(e.y);
    })
    .onFinalize(() => {
      following.set(0);
      active.set(0);
    });

  const onClear = () => {
    takeOver();
    idle.set(0);
    clearAll(CLEAR_LEN * 1000);
    feel();
  };

  const confirm = useAnimatedStyle(() => ({ opacity: 0.45 + 0.55 * clamp01(etch.get().signed) }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  const ready = W > 0 && fx_.screen && fx_.line && fx_.grain;

  return (
    <View style={styles.root} onLayout={onLayout}>
      {ready ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Fill>
            <LinearGradient start={vec(0, 0)} end={vec(W * 0.3, H)} colors={['#F4F3F0', '#ECEAE5', '#E2DFD8']} />
          </Fill>
          <Group transform={toyT}>
            {/* Red plastic frame: two shadows, a lit top edge, a darker lower lip, a little grain. */}
            <RoundedRect x={fx} y={fy} width={fw} height={fh} r={34}>
              <LinearGradient start={vec(0, fy)} end={vec(0, fy + fh)} colors={RED} />
              <Shadow dx={0} dy={18} blur={34} color="rgba(90,20,15,0.28)" />
              <Shadow dx={0} dy={2} blur={3} color="rgba(90,20,15,0.4)" />
            </RoundedRect>
            <RoundedRect x={fx} y={fy} width={fw} height={fh} r={34} blendMode="softLight">
              <Shader source={fx_.grain!} uniforms={{ amount: 0.3 }} />
            </RoundedRect>
            <RoundedRect x={fx + 1} y={fy + 1} width={fw - 2} height={fh - 2} r={33} style="stroke" strokeWidth={1.5} color="rgba(255,190,180,0.45)" />
            <RoundedRect x={fx + 6} y={fy + fh - 30} width={fw - 12} height={24} r={14} color="rgba(80,10,8,0.12)" />
            {/* The bezel and the powder screen. */}
            <RoundedRect x={scr.x - 7} y={scr.y - 7} width={scr.w + 14} height={scr.h + 14} r={20}>
              <LinearGradient start={vec(0, scr.y - 7)} end={vec(0, scr.y + scr.h + 7)} colors={['#8E1A16', '#B32820', '#D5463A']} />
            </RoundedRect>
            <RoundedRect x={scr.x} y={scr.y} width={scr.w} height={scr.h} r={14}>
              <Shader source={fx_.screen!} uniforms={screenU} />
            </RoundedRect>
            <Group transform={[{ translateY: 1.3 }]}>
              <Path path={line} style="stroke" strokeWidth={1.5} strokeCap="round" strokeJoin="round">
                <Shader source={fx_.line!} uniforms={lipU} />
              </Path>
            </Group>
            <Path path={line} style="stroke" strokeWidth={1.9} strokeCap="round" strokeJoin="round">
              <Shader source={fx_.line!} uniforms={lineU} />
            </Path>
            <Circle c={stylus} r={2.4} color="#3A3D42" />
            <Circle c={stylus} r={1} color="rgba(255,255,255,0.5)" />
            <Glyphs font={label.font} glyphs={label.glyphs} color="#F3CF7A">
              <Shadow dx={0} dy={1} blur={0.5} color="rgba(90,10,6,0.6)" />
            </Glyphs>

            {[knobLT, knobRT].map((t, i) => (
              <Group key={i}>
                <Circle cx={i === 0 ? knobLX : knobRX} cy={knobY + 3} r={KNOB_R + 1} color="rgba(70,8,6,0.35)" />
                <Group transform={t}>
                  <Circle cx={0} cy={0} r={KNOB_R}>
                    <RadialGradient c={vec(-9, -11)} r={KNOB_R * 1.5} colors={['#FFFFFC', '#EEEAE2', '#CFC8BC']} />
                    <Shadow dx={0} dy={4} blur={6} color="rgba(70,8,6,0.4)" />
                  </Circle>
                  <Path path={ridges} style="stroke" strokeWidth={1.4} strokeCap="round" color="rgba(120,110,95,0.45)" />
                  <Circle cx={0} cy={0} r={KNOB_R - 9} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.8)" />
                  <Circle cx={0} cy={-KNOB_R + 13} r={2.6} color="#C9302A" />
                </Group>
              </Group>
            ))}
          </Group>
        </Canvas>
      ) : null}

      <GestureDetector gesture={draw}>
        <View style={[styles.hit, { left: scr.x, top: scr.y, width: scr.w, height: scr.h }]} accessible accessibilityLabel="Signature pad. Draw with your finger, or turn the knobs." />
      </GestureDetector>
      <GestureDetector gesture={left}>
        <View style={[styles.hit, { left: knobLX - 44, top: knobY - 44, width: 88, height: 88 }]} accessible accessibilityLabel="Left knob: draws across" />
      </GestureDetector>
      <GestureDetector gesture={right}>
        <View style={[styles.hit, { left: knobRX - 44, top: knobY - 44, width: 88, height: 88 }]} accessible accessibilityLabel="Right knob: draws up and down" />
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <Text style={styles.kicker}>PROOF OF DELIVERY</Text>
        <Text style={styles.step}>Step 2 of 2</Text>
      </View>
      <View pointerEvents="none" style={[styles.titles, { top: insets.top + 42 }]}>
        <Text style={styles.title}>Sign for parcel #A-2291</Text>
        <Text style={styles.sub}>Dana Whitfield · 14 Larch Lane, Apt 3</Text>
      </View>
      <Text pointerEvents="none" style={[styles.hint, { top: fy + fh + 14 }]}>
        Turn the knobs or draw · two fingers shake it clear
      </Text>
      <View style={[styles.actions, { top: buttonsTop }]}>
        <Pressable onPress={onClear} style={({ pressed }) => [styles.button, styles.secondary, pressed && styles.pressed]} accessibilityRole="button">
          <Text style={[styles.buttonText, { color: INK }]}>Clear</Text>
        </Pressable>
        <Animated.View style={[styles.primaryWrap, confirm]}>
          <Pressable style={({ pressed }) => [styles.button, styles.primary, pressed && styles.pressed]} accessibilityRole="button">
            <Text style={[styles.buttonText, { color: '#F7F5F2' }]}>Confirm delivery</Text>
          </Pressable>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#ECEAE5' },
  hit: { position: 'absolute' },
  header: { position: 'absolute', left: 20, right: 20, height: 24, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kicker: { color: MUTE, fontSize: 12, fontWeight: '700', letterSpacing: 1.4 },
  step: { color: MUTE, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  titles: { position: 'absolute', left: 20, right: 20, gap: 4 },
  title: { color: INK, fontSize: 26, fontWeight: '800', letterSpacing: -0.5 },
  sub: { color: MUTE, fontSize: 15, fontWeight: '500' },
  hint: { position: 'absolute', left: 20, right: 20, textAlign: 'center', color: MUTE, fontSize: 13, fontWeight: '500' },
  actions: { position: 'absolute', left: 16, right: 16, height: 52, flexDirection: 'row', gap: 12 },
  button: { height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
  secondary: { flex: 1, backgroundColor: 'rgba(31,35,40,0.06)', borderWidth: 1, borderColor: 'rgba(31,35,40,0.12)' },
  primaryWrap: { flex: 2 },
  primary: { backgroundColor: INK, boxShadow: '0 1px 2px rgba(10,12,16,0.35), 0 10px 22px rgba(10,12,16,0.2)' },
  pressed: { transform: [{ scale: 0.97 }] },
  buttonText: { fontSize: 17, fontWeight: '700' },
});
