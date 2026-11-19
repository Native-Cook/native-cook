import { Button as SwiftButton, Host, Picker, Text as SwiftText } from '@expo/ui/swift-ui';
import { buttonStyle, controlSize, frame, pickerStyle, tag, tint } from '@expo/ui/swift-ui/modifiers';
import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useState, type ReactNode } from 'react';
import { PixelRatio, Platform, Pressable, StyleSheet, Text, TextInput, useWindowDimensions, View } from 'react-native';
import Animated, {
  Easing,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import {
  LENGTHS,
  LOOP,
  RHYTHMS,
  breathAt,
  clamp01,
  filmLength,
  filmStep,
  lightAt,
  offsetsAt,
  stepVis,
} from './breatheMotion';
import { BREATHE_SKSL } from './breatheShader';

const EFFECT = Skia.RuntimeEffect.Make(BREATHE_SKSL);
const WIDE = Platform.OS === 'ios' ? 1 : 0;
const DPR = PixelRatio.get();
const IOS = Platform.OS === 'ios';

const INK = '#1F2333';
const INK_SOFT = 'rgba(31,35,51,0.66)';
const IDLE_MS = 2000;
const RETURN_MS = 900;
const SWITCH_S = 0.8; // a rhythm change blends the old breath into the new one
const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;
const EASE = Easing.bezier(0.2, 0.8, 0.2, 1);

/** Haptics live on the JS thread; worklets schedule these. */
function lightTap() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

const STEP_TITLES = ['Take a slower minute', 'Find your rhythm', 'Your first session'];
const STEP_BODIES = [
  'Follow the light as it fills and empties. Three slow breaths are enough to start.',
  '',
  'No sound, no streaks. Just the light, for as long as you chose.',
];

type Props = { autoPlay?: boolean };

/**
 * A breathing onboarding whose background is a mesh gradient that breathes: on the inhale its control
 * points swell away from the glass orb and the light warms, on the exhale they gather back and cool.
 * The orb guides the breath with the phase and a count. Three steps with native controls: a
 * segmented Picker for the rhythm, a wheel Picker for the session length, and a glass button.
 */
export function MeshGradientBreathe({ autoPlay = true }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = autoPlay && !reduced;
  const compact = height < 740;

  const contentW = Math.min(width, 520) - 48;
  const avail = height - insets.top - insets.bottom;
  const orbR = compact ? 54 : Math.min(width * 0.2, 80);
  const orbY = insets.top + avail * (compact ? 0.22 : 0.25);
  const textTop = orbY + orbR * 1.32 + (compact ? 18 : 30);
  const wheelH = Math.max(96, Math.min(height * 0.15, 136));

  // ---- React state that changes a few times a loop at most
  const [step, setStep] = useState(0);
  const [rhythm, setRhythm] = useState(0);
  const [minutes, setMinutes] = useState(3);

  // ---- one clock on the UI thread
  const amb = useSharedValue(0); // seconds since the first frame (never resets: the breath)
  const origin = useSharedValue(-1);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const lastActivity = useSharedValue(-1);
  const rhythmSV = useSharedValue(0);
  const switchFrom = useSharedValue(0); // fullness when the rhythm last changed
  const switchAt = useSharedValue(-100); // amb time of that change
  const breathOrigin = useSharedValue(0); // amb time the current rhythm counts from
  const full = useSharedValue(0); // the breath, 0..1
  const phaseK = useSharedValue(0);
  const left = useSharedValue(4);
  const phaseLen = useSharedValue(4);
  const vis0 = useSharedValue(1);
  const vis1 = useSharedValue(0);
  const vis2 = useSharedValue(0);
  const session = useSharedValue(0);
  const filmStepSV = useSharedValue(0);
  const filmLenSV = useSharedValue(3);

  const resetChoices = useCallback(() => {
    setStep(0);
    setMinutes(3);
    setRhythm(0);
    rhythmSV.set(0);
  }, [rhythmSV]);

  useFrameCallback((frame) => {
    'worklet';
    const now = frame.timestamp;
    if (origin.get() < 0) origin.set(now);
    const a = reduced ? 0 : (now - origin.get()) / 1000;
    amb.set(a);

    // the breath (reduced motion: held at the gentle middle, no count)
    const out = [0, 0, 0, 0];
    breathAt(a - breathOrigin.get(), rhythmSV.get(), out);
    const since = a - switchAt.get();
    const blend = since < SWITCH_S ? clamp01(since / SWITCH_S) : 1;
    full.set(reduced ? 0.5 : switchFrom.get() + (out[0] - switchFrom.get()) * blend);
    phaseK.set(out[1]);
    left.set(out[2]);
    phaseLen.set(out[3]);

    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      const t = ((now - loopStart.get()) / 1000) % LOOP;
      vis0.set(stepVis(0, t));
      vis1.set(stepVis(1, t));
      vis2.set(stepVis(2, t));
      const s = filmStep(t);
      if (s !== filmStepSV.get()) filmStepSV.set(s);
      const l = filmLength(t);
      if (l !== filmLenSV.get()) filmLenSV.set(l);
      return;
    }
    if (m !== MODE_MANUAL || session.get() > 0) return;
    if (lastActivity.get() < 0) {
      lastActivity.set(now);
      return;
    }
    if (film && now - lastActivity.get() > IDLE_MS) {
      // back to the film's first frame: Welcome, Box, 3 min; the breath carries on
      mode.set(MODE_RETURN);
      const out = { duration: 300, easing: Easing.in(Easing.quad) };
      vis1.set(withTiming(0, out));
      vis2.set(withTiming(0, out));
      vis0.set(
        withDelay(vis0.get() > 0.5 ? 0 : 300, withTiming(1, { duration: RETURN_MS - 300, easing: EASE }, (done) => {
          'worklet';
          if (!done) return;
          loopStart.set(-1);
          filmStepSV.set(0);
          filmLenSV.set(3);
          mode.set(MODE_AUTO);
        })),
      );
      scheduleOnRN(resetChoices);
    }
  });

  // the film's step and length reach React (a handful of times per loop)
  useAnimatedReaction(
    () => filmStepSV.get(),
    (s, prev) => {
      if (prev != null && s !== prev && mode.get() === MODE_AUTO) scheduleOnRN(setStep, s);
    },
  );
  useAnimatedReaction(
    () => filmLenSV.get(),
    (l, prev) => {
      if (prev != null && l !== prev && mode.get() === MODE_AUTO) scheduleOnRN(setMinutes, l);
    },
  );

  // a light tap as each breath turns (inhale starts, exhale starts), only once a person is using it
  useAnimatedReaction(
    () => phaseK.get(),
    (k, prev) => {
      if (prev == null || k === prev || mode.get() === MODE_AUTO) return;
      if (k === 0 || k === 2) scheduleOnRN(lightTap);
    },
  );

  const touch = useCallback(() => {
    'worklet';
    mode.set(MODE_MANUAL);
    lastActivity.set(-1);
  }, [lastActivity, mode]);

  const goTo = useCallback(
    (k: number) => {
      scheduleOnUI(touch);
      setStep(k);
      // out first, then in: never two steps' words on screen at once
      const out = { duration: 200, easing: Easing.in(Easing.quad) };
      const inn = { duration: 340, easing: EASE };
      vis0.set(k === 0 ? withDelay(200, withTiming(1, inn)) : withTiming(0, out));
      vis1.set(k === 1 ? withDelay(200, withTiming(1, inn)) : withTiming(0, out));
      vis2.set(k === 2 ? withDelay(200, withTiming(1, inn)) : withTiming(0, out));
    },
    [touch, vis0, vis1, vis2],
  );

  const onPrimary = useCallback(() => {
    if (step < 2) {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      goTo(step + 1);
      return;
    }
    // Begin: the words step aside and the orb takes the middle of the screen
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    scheduleOnUI(touch);
    session.set(withTiming(1, { duration: 700, easing: EASE }));
  }, [goTo, session, step, touch]);

  const onEnd = useCallback(() => {
    scheduleOnUI(touch);
    session.set(withTiming(0, { duration: 600, easing: EASE }));
  }, [session, touch]);

  const onRhythm = useCallback(
    (r: number) => {
      scheduleOnUI(touch);
      setRhythm(r);
      // the new rhythm starts with an inhale; the old breath blends into it
      switchFrom.set(full.get());
      switchAt.set(amb.get());
      breathOrigin.set(amb.get());
      rhythmSV.set(r);
    },
    [amb, breathOrigin, full, rhythmSV, switchAt, switchFrom, touch],
  );

  const onMinutes = useCallback(
    (m: number) => {
      scheduleOnUI(touch);
      setMinutes(m);
    },
    [touch],
  );

  // ---- the orb, in points (the shader draws it; the label sits on it)
  const orbCY = useDerivedValue(() => orbY + (height * 0.44 - orbY) * session.get());
  const orbRad = useDerivedValue(() => orbR * (1 + 0.3 * session.get()) * (1 + 0.26 * full.get()));

  const uniforms = useDerivedValue(() => {
    const b = full.get();
    const lab: number[] = new Array(75);
    const off: number[] = new Array(50);
    lightAt(b, lab);
    offsetsAt(b, amb.get() % LOOP, 0.5, orbCY.get() / height, off);
    return {
      size: [width, height],
      off,
      lab,
      orb: [width / 2, orbCY.get(), orbRad.get()],
      lift: b,
      wide: WIDE,
      dpr: DPR,
    };
  });

  const orbLabel = useAnimatedStyle(() => ({
    transform: [{ translateY: orbCY.get() - 40 }, { scale: 0.9 + 0.14 * full.get() + 0.12 * session.get() }],
  }));
  const content = useAnimatedStyle(() => ({ opacity: 1 - session.get(), transform: [{ translateY: 24 * session.get() }] }));
  const endBtn = useAnimatedStyle(() => ({ opacity: session.get() }));

  const primaryLabel = step < 2 ? 'Continue' : 'Begin';
  const r = RHYTHMS[rhythm];

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      {EFFECT ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Fill>
            <Shader source={EFFECT} uniforms={uniforms} />
          </Fill>
        </Canvas>
      ) : null}

      {/* the guide on the orb */}
      <Animated.View pointerEvents="none" style={[styles.orbLabel, orbLabel]}>
        {[0, 1, 2].map((k) => (
          <PhaseLabel key={k} k={k} phaseK={phaseK} left={left} len={phaseLen} reduced={reduced} />
        ))}
        <Count left={left} reduced={reduced} />
      </Animated.View>

      {/* top bar */}
      <Animated.View style={[styles.topBar, { top: insets.top + 6 }, content]}>
        <Text style={styles.brand}>Stillwater</Text>
        <Pressable hitSlop={12} onPress={() => goTo(2)} accessibilityRole="button">
          <Text style={styles.skip}>Skip</Text>
        </Pressable>
      </Animated.View>

      {/* the three steps, stacked; only the visible one takes touches */}
      <Animated.View style={[StyleSheet.absoluteFill, content]} pointerEvents="box-none">
        <Step vis={vis0} top={textTop} active={step === 0}>
          <Text style={[styles.title, compact && styles.titleCompact]}>{STEP_TITLES[0]}</Text>
          <Text style={styles.body}>{STEP_BODIES[0]}</Text>
        </Step>
        <Step vis={vis1} top={textTop} active={step === 1}>
          <Text style={[styles.title, compact && styles.titleCompact]}>{STEP_TITLES[1]}</Text>
          <Text style={styles.label}>Rhythm</Text>
          <RhythmPicker width={contentW} value={rhythm} onChange={onRhythm} />
          <Text style={[styles.label, { marginTop: compact ? 12 : 20 }]}>Session length</Text>
          <LengthPicker width={contentW} height={wheelH} value={minutes} onChange={onMinutes} />
        </Step>
        <Step vis={vis2} top={textTop} active={step === 2}>
          <Text style={[styles.title, compact && styles.titleCompact]}>{STEP_TITLES[2]}</Text>
          <Text style={styles.summary}>
            {minutes} min · {r.detail}
          </Text>
          <Text style={styles.body}>{STEP_BODIES[2]}</Text>
        </Step>

        <View style={[styles.bottom, { bottom: insets.bottom + 16 }]} pointerEvents="box-none">
          <View style={styles.dots}>
            <Dot vis={vis0} />
            <Dot vis={vis1} />
            <Dot vis={vis2} />
          </View>
          <PrimaryButton width={contentW} label={primaryLabel} onPress={onPrimary} />
        </View>
      </Animated.View>

      <Animated.View style={[styles.bottom, { bottom: insets.bottom + 16 }, endBtn]} pointerEvents="box-none">
        <SessionEnd width={contentW} session={session} onPress={onEnd} />
      </Animated.View>
    </View>
  );
}

function Step({ vis, top, active, children }: { vis: SharedValue<number>; top: number; active: boolean; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const v = vis.get();
    return { opacity: v, transform: [{ translateY: (1 - v) * 12 }] };
  });
  return (
    <Animated.View style={[styles.step, { top }, style]} pointerEvents={active ? 'box-none' : 'none'}>
      {children}
    </Animated.View>
  );
}

function Dot({ vis }: { vis: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const v = clamp01(vis.get());
    return { width: 8 + 16 * v, opacity: 0.32 + 0.68 * v };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

/** Breathe in / Hold / Breathe out, crossfading as the phase turns. k: 0 in, 1 hold, 2 out. */
function PhaseLabel({
  k,
  phaseK,
  left,
  len,
  reduced,
}: {
  k: number;
  phaseK: SharedValue<number>;
  left: SharedValue<number>;
  len: SharedValue<number>;
  reduced: boolean;
}) {
  const label = k === 0 ? 'Breathe in' : k === 1 ? 'Hold' : 'Breathe out';
  const style = useAnimatedStyle(() => {
    if (reduced) return { opacity: k === 0 ? 1 : 0 };
    const p = phaseK.get();
    const mine = p === k || (k === 1 && p === 3);
    // fade out over the phase's last 0.3 s, in over its first 0.35 s
    const l = left.get();
    const o = mine ? Math.min(1, l / 0.3, (len.get() - l) / 0.35) : 0;
    return { opacity: o };
  });
  return <Animated.Text style={[styles.phase, style]}>{label}</Animated.Text>;
}

function Count({ left, reduced }: { left: SharedValue<number>; reduced: boolean }) {
  const props = useAnimatedProps(() => {
    const v = reduced ? '' : `${Math.max(1, Math.ceil(left.get() - 0.001))}`;
    return { text: v, defaultValue: v } as never;
  });
  return <AnimatedTextInput animatedProps={props} editable={false} underlineColorAndroid="transparent" style={styles.count} />;
}

// ---------------------------------------------------------------- native controls

function RhythmPicker({ width, value, onChange }: { width: number; value: number; onChange: (r: number) => void }) {
  if (!IOS) {
    return (
      <View style={[styles.fallbackRow, { width }]}>
        {RHYTHMS.map((r, i) => (
          <Pressable key={r.id} onPress={() => onChange(i)} style={[styles.fallbackSeg, value === i && styles.fallbackSegOn]}>
            <Text style={styles.fallbackText}>{r.label}</Text>
          </Pressable>
        ))}
      </View>
    );
  }
  return (
    <Host style={{ width, height: 34 }} colorScheme="light">
      <Picker selection={value} onSelectionChange={(v: number) => onChange(v)} modifiers={[pickerStyle('segmented')]}>
        {RHYTHMS.map((r, i) => (
          <SwiftText key={r.id} modifiers={[tag(i)]}>
            {r.label}
          </SwiftText>
        ))}
      </Picker>
    </Host>
  );
}

function LengthPicker({ width, height, value, onChange }: { width: number; height: number; value: number; onChange: (m: number) => void }) {
  if (!IOS) {
    return (
      <View style={[styles.fallbackRow, { width }]}>
        {LENGTHS.map((m) => (
          <Pressable key={m} onPress={() => onChange(m)} style={[styles.fallbackSeg, value === m && styles.fallbackSegOn]}>
            <Text style={styles.fallbackText}>{m} min</Text>
          </Pressable>
        ))}
      </View>
    );
  }
  return (
    <Host style={{ width, height }} colorScheme="light">
      <Picker selection={value} onSelectionChange={(v: number) => onChange(v)} modifiers={[pickerStyle('wheel'), frame({ height })]}>
        {LENGTHS.map((m) => (
          <SwiftText key={m} modifiers={[tag(m)]}>
            {m === 1 ? '1 minute' : `${m} minutes`}
          </SwiftText>
        ))}
      </Picker>
    </Host>
  );
}

function PrimaryButton({ width, label, onPress }: { width: number; label: string; onPress: () => void }) {
  if (!IOS) {
    return (
      <Pressable onPress={onPress} style={[styles.fallbackButton, { width }]}>
        <Text style={styles.fallbackButtonText}>{label}</Text>
      </Pressable>
    );
  }
  return (
    <Host style={{ width, height: 54 }} colorScheme="light">
      <SwiftButton onPress={onPress} modifiers={[buttonStyle('glassProminent'), controlSize('large'), tint(INK)]}>
        <SwiftText modifiers={[frame({ maxWidth: 10000 })]}>{label}</SwiftText>
      </SwiftButton>
    </Host>
  );
}

function SessionEnd({ width, session, onPress }: { width: number; session: SharedValue<number>; onPress: () => void }) {
  const [on, setOn] = useState(false);
  useAnimatedReaction(
    () => session.get() > 0.5,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setOn, v);
    },
  );
  if (!on) return null;
  if (!IOS) {
    return (
      <Pressable onPress={onPress} style={[styles.fallbackButton, { width, backgroundColor: 'rgba(255,255,255,0.6)' }]}>
        <Text style={[styles.fallbackButtonText, { color: INK }]}>End session</Text>
      </Pressable>
    );
  }
  return (
    <Host style={{ width, height: 54 }} colorScheme="light">
      <SwiftButton onPress={onPress} modifiers={[buttonStyle('glass'), controlSize('large')]}>
        <SwiftText modifiers={[frame({ maxWidth: 10000 })]}>End session</SwiftText>
      </SwiftButton>
    </Host>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#D8DCEE' },
  topBar: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', height: 32 },
  brand: { color: INK, fontSize: 17, fontWeight: '700', fontFamily: IOS ? 'ui-rounded' : undefined },
  skip: { color: INK_SOFT, fontSize: 16, fontWeight: '600' },
  orbLabel: { position: 'absolute', left: 0, right: 0, top: 0, height: 80, alignItems: 'center', justifyContent: 'center' },
  phase: { position: 'absolute', top: 10, color: INK, fontSize: 17, fontWeight: '600', textAlign: 'center' },
  count: { position: 'absolute', top: 32, width: 80, textAlign: 'center', color: INK_SOFT, fontSize: 28, fontWeight: '300', fontVariant: ['tabular-nums'], padding: 0 },
  step: { position: 'absolute', left: 24, right: 24 },
  title: { color: INK, fontSize: 34, fontWeight: '700', letterSpacing: -0.4, lineHeight: 40 },
  titleCompact: { fontSize: 28, lineHeight: 33 },
  body: { color: INK_SOFT, fontSize: 17, lineHeight: 24, marginTop: 10 },
  summary: { color: INK, fontSize: 17, fontWeight: '600', marginTop: 10 },
  label: { color: INK_SOFT, fontSize: 15, fontWeight: '600', marginTop: 14, marginBottom: 8 },
  bottom: { position: 'absolute', left: 24, right: 24, alignItems: 'center', gap: 20 },
  dots: { flexDirection: 'row', gap: 6, height: 8 },
  dot: { height: 8, borderRadius: 4, backgroundColor: INK },
  fallbackRow: { flexDirection: 'row', gap: 6 },
  fallbackSeg: { flex: 1, height: 34, borderRadius: 9, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.35)' },
  fallbackSegOn: { backgroundColor: 'rgba(255,255,255,0.9)' },
  fallbackText: { color: INK, fontSize: 14, fontWeight: '600' },
  fallbackButton: { height: 54, borderRadius: 27, alignItems: 'center', justifyContent: 'center', backgroundColor: INK },
  fallbackButtonText: { color: '#FFFFFF', fontSize: 17, fontWeight: '600' },
});
