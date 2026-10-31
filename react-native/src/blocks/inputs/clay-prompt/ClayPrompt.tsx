import { Canvas, Circle, DashPathEffect, Rect, RoundedRect, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, Keyboard, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier,
  Easing,
  ReduceMotion,
  useAnimatedKeyboard,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { composed, frameAt, look, LOOP, poseAt, PROMPT_KINDS, slotX } from './cpr-motion';
import { CPR_SKSL } from './cpr-shader';
import { COLOR, FONT, type Kind, KINDS, kindOfWord, makeLayout, PROMPT, SLOTS, SPACE } from './cpr-theme';
import { ClayParticles } from './gpu/ClayParticles';
import { type CprCue, useCprAudio } from './use-cpr-audio';

/*
 Clay Prompt — the prompt box of an invented image generator, "Mold".

 The screen: a menu, the name and the credits on top; the picture card; under it the clay pieces the
 prompt has called up; the prompt box with Generate.

 Signature: words become clay. As you type, every noun the app knows (cat, sofa, moon, plant) pops
 out of the text as a small 3D clay object and sits on the prompt box. Generate lifts the pieces into
 the card, where they melt into one blob; the blob opens the picture and the same clay comes back out
 as the scene: the cat on the sofa, the moon in the window, lit by night. It is one number from 0 to 1,
 so pulling the picture down scrubs it back into the blob and onto the prompt box. Clay is
 ray-marched in one Skia shader (smooth unions between objects, colours mixed in OKLab); the glowing
 dust that carries a word into its clay and swirls round the blob is a WebGPU compute simulation
 (TypeGPU), drawn above it when the device has WebGPU.

 autoPlay: LOOP = 11 s (type the prompt, three pieces pop, Generate, pull the picture half way back
 and let go, wipe); frame 11 s equals frame 0. A touch takes over; 2 s after the last one (and with
 the keyboard down) the screen wipes and the loop starts again.
 Reduced motion: no loop; pieces fade in; Generate cross-fades to the picture in 300 ms.
*/

const effect = Skia.RuntimeEffect.Make(CPR_SKSL);
if (!effect) throw new Error('Clay Prompt: the clay shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const NEVER = ReduceMotion.Never;
const GEN_MS = 2400;

// where each word of the prompt box sits on screen, for the dust to start from (JS-side only)
const js = { words: new Map<number, { x: number; y: number }>(), composerX: 0, composerY: 0 }; // by kind

function buzz(kind: 'light' | 'medium' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(kind === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** The nouns of a prompt that the app can sculpt, in order of first mention, at most SLOTS. */
function kindsOf(text: string): number[] {
  const out: number[] = [];
  for (const word of text.split(/\s+/)) {
    const k = kindOfWord(word);
    if (k >= 0 && !out.includes(k) && out.length < SLOTS) out.push(k);
  }
  return out;
}

type Props = { autoPlay?: boolean };

export function ClayPrompt({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const audio = useCprAudio(!reduced);
  const cue = useCallback((c: CprCue) => audio.play(c), [audio]);
  const keyboard = useAnimatedKeyboard();
  const input = useRef<TextInput>(null);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const focused = useSharedValue(0);
  const touching = useSharedValue(0); // a finger on the picture

  const g = useSharedValue(0); // the generation, 0 … 1
  const clear = useSharedValue(0); // the wipe back to an empty screen
  // each slot of clay: presence, x on the prompt box, kind
  const pop0 = useSharedValue(0);
  const pop1 = useSharedValue(0);
  const pop2 = useSharedValue(0);
  const pop3 = useSharedValue(0);
  const x0 = useSharedValue(L.w / 2);
  const x1 = useSharedValue(L.w / 2);
  const x2 = useSharedValue(L.w / 2);
  const x3 = useSharedValue(L.w / 2);
  const kinds = useSharedValue<number[]>([0, 1, 2, 3]);
  const press = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const scene = useSharedValue<number[]>(new Array(34).fill(0)); // the shader's uniforms, packed
  // a dust burst for the particles: from x, y, to x, y, kind (−1 = the blob swirl), start time
  const emit = useSharedValue<number[]>([0, 0, 0, 0, 0, -1]);

  const [text, setText] = useState('');
  const [typed, setTyped] = useState(0); // autoPlay's typed characters
  const [shown, setShown] = useState<number[]>([]); // manual: kinds on the prompt box, in slot order
  const [generated, setGenerated] = useState(false);
  const [manual, setManual] = useState(!autoPlay); // the prompt box shows the user's text
  const [editing, setEditing] = useState(false);

  /* ------------------------------------------------------------ frames */
  const cardCx = L.card.x + L.card.w / 2;
  const cardCy = L.card.y + L.card.h * 0.52;

  const resetText = useCallback(() => {
    setText('');
    setShown([]);
    setGenerated(false);
    setTyped(0);
    setManual(false);
  }, []);

  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    const lift = Math.max(0, keyboard.height.get() - insets.bottom);
    const ty = L.tokenY - lift;
    const pops = [pop0, pop1, pop2, pop3];
    const xs = [x0, x1, x2, x3];
    let k = kinds.get();
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      g.set(s.g);
      clear.set(s.clear);
      press.set(s.press);
      finger.set(s.finger);
      // the pieces re-centre as each one arrives
      const n = s.pops[0] + s.pops[1] + s.pops[2];
      for (let i = 0; i < 3; i++) {
        pops[i].set(s.pops[i]);
        xs[i].set(slotX(i, Math.max(1, n), L.w, L.tokenGap));
      }
      pop3.set(0);
      if (k[0] !== 0 || k[1] !== 1 || k[2] !== 2) {
        k = [0, 1, 2, 3];
        kinds.set(k);
      }
      if (s.fingerAt === 0) {
        fingerX.set(L.w - SPACE.l - SPACE.m - 58);
        fingerY.set(L.composerTop + L.composerH - SPACE.m - 22);
      } else {
        fingerX.set(cardCx + 40);
        fingerY.set(L.card.y + L.card.h * 0.4 + s.dragY);
      }
    } else if (auto && resumeAsked.get() === 0 && focused.get() === 0 && touching.get() === 0 && (g.get() < 0.001 || g.get() > 0.999) && clock.get() - lastTouch.get() > 2) {
      resumeAsked.set(1);
      clear.set(withTiming(1, { duration: 700, easing: EASE_OUT }));
    } else if (resumeAsked.get() === 1 && clear.get() > 0.999) {
      resumeAsked.set(0);
      g.set(0);
      clear.set(0);
      for (let i = 0; i < SLOTS; i++) pops[i].set(0);
      scheduleOnRN(resetText);
      autoStart.set(clock.get());
      mode.set(0);
    }
    // pack the clay for the shader
    const gNow = g.get();
    const wipe = clear.get();
    const l = look(gNow);
    const out = new Array<number>(34).fill(0); // a new array each frame, so readers see the change
    let hasSofa = false;
    for (let i = 0; i < SLOTS; i++) if (k[i] === 1 && pops[i].get() > 0.01) hasSofa = true;
    for (let i = 0; i < SLOTS; i++) {
      const p = pops[i].get();
      const c = composed(k[i], hasSofa, L.card.x, L.card.y, L.card.w, L.card.h);
      const pose = poseAt(gNow, xs[i].get(), ty, L.tokenSize, c[0], c[1], c[2], cardCx, cardCy);
      out[i * 4] = pose.x;
      out[i * 4 + 1] = pose.y;
      out[i * 4 + 2] = pose.size;
      out[i * 4 + 3] = Math.max(0, p) * (1 - wipe);
      out[16 + i * 4] = k[i];
      out[16 + i * 4 + 1] = 0.28 * Math.sin(clock.get() * 1.1 + i * 1.7) * (1 - l.reveal * 0.6);
      // a pop overshoots into a squash; a piece in flight stretches
      out[16 + i * 4 + 2] = pose.squash + 0.5 * Math.max(-0.2, Math.min(0.2, p - 1));
      out[16 + i * 4 + 3] = 0.6 + 0.4 * l.night;
    }
    out[32] = clock.get();
    scene.set(out);
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__clay = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ mode: mode.get(), g: +g.get().toFixed(3), clear: +clear.get().toFixed(3), pops: [pop0.get(), pop1.get(), pop2.get(), pop3.get()].map((v) => +v.toFixed(2)), scene: scene.get().slice(0, 16).map((v) => +v.toFixed(1)) }),
    };
  }, [pin, mode, g, clear, pop0, pop1, pop2, pop3, scene]);

  /* ------------------------------------------------------------ autoPlay → React (on change only) */
  const autoChars = useDerivedValue(() => {
    if (mode.get() !== 0) return -1;
    const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
    return frameAt(t).chars;
  });
  useAnimatedReaction(
    () => autoChars.get(),
    (now, before) => {
      if (now >= 0 && now !== before) scheduleOnRN(setTyped, now);
    },
  );
  // dust from each noun to its clay as it pops (autoPlay)
  const onAutoPop = useCallback(
    (i: number) => {
      const from = js.words.get(PROMPT_KINDS[i]);
      if (!from) return;
      const n = i + 1;
      emit.set([from.x, from.y, slotX(i, n, L.w, L.tokenGap), L.tokenY, PROMPT_KINDS[i], clock.get()]);
    },
    [emit, clock, L],
  );
  useAnimatedReaction(
    () => (mode.get() === 0 ? (pop0.get() > 0.02 ? 1 : 0) + (pop1.get() > 0.02 ? 1 : 0) + (pop2.get() > 0.02 ? 1 : 0) : -1),
    (now, before) => {
      if (before !== null && before >= 0 && now > before) scheduleOnRN(onAutoPop, now - 1);
    },
  );
  // the swirl round the blob (autoPlay and manual alike)
  useAnimatedReaction(
    () => g.get() > 0.2 && g.get() < 0.5,
    (now, before) => {
      if (now && !before) emit.set([cardCx, cardCy, cardCx, cardCy, -1, clock.get()]);
    },
  );

  /* ------------------------------------------------------------ the finger */
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      press.set(0);
    }
    if (clear.get() > 0) clear.set(withTiming(0, { duration: 200 }));
  };

  // manual typing: nouns pop out of the text, removed ones sink back, the rest re-centre
  const onText = useCallback(
    (value: string) => {
      takeOver();
      if (mode.get() === 0) mode.set(1);
      setManual(true);
      setText(value);
      const next = kindsOf(value);
      const pops = [pop0, pop1, pop2, pop3];
      const xs = [x0, x1, x2, x3];
      const added = next.filter((k) => !shown.includes(k));
      const nk = kinds.get().slice();
      next.forEach((k, i) => {
        nk[i] = k;
      });
      kinds.set(nk);
      for (let i = 0; i < SLOTS; i++) {
        const want = i < next.length ? 1 : 0;
        const tx = slotX(i, Math.max(1, next.length), L.w, L.tokenGap);
        const isNew = i < next.length && added.includes(next[i]);
        if (reduced) {
          xs[i].set(tx);
          pops[i].set(withTiming(want, { duration: 200, reduceMotion: NEVER }));
          continue;
        }
        if (isNew) {
          xs[i].set(tx);
          pops[i].set(0);
          pops[i].set(withSpring(1, { duration: 520, dampingRatio: 0.5 }));
        } else {
          xs[i].set(withSpring(tx, { duration: 420, dampingRatio: 0.8 }));
          pops[i].set(withSpring(want, { duration: 420, dampingRatio: 0.8 }));
        }
      }
      if (added.length) {
        const k = added[added.length - 1];
        const i = next.indexOf(k);
        cue('pop');
        buzz('light');
        const at = js.words.get(k) ?? { x: L.w / 2, y: L.composerTop + 30 };
        emit.set([at.x, at.y, slotX(i, next.length, L.w, L.tokenGap), L.tokenY, k, clock.get()]);
      }
      setShown(next);
      if (g.get() > 0.01) {
        g.set(withTiming(0, { duration: 600, easing: EASE_OUT }));
        setGenerated(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, L, shown, cue],
  );

  const onFocus = useCallback(() => {
    takeOver();
    focused.set(1);
    setManual(true);
    setEditing(true);
    if (mode.get() === 0) {
      mode.set(1);
      // start from an empty box: the loop's pieces sink away
      for (const p of [pop0, pop1, pop2, pop3]) p.set(withTiming(0, { duration: 250 }));
      g.set(withTiming(0, { duration: 400, easing: EASE_OUT }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const onBlur = useCallback(() => {
    setEditing(false);
    focused.set(0);
    lastTouch.set(clock.get());
  }, [focused, lastTouch, clock]);

  const onGenerated = useCallback(() => {
    cue('bloom');
    buzz('success');
    setGenerated(true);
  }, [cue]);
  const onMelt = useCallback(() => {
    cue('melt');
    buzz('medium');
  }, [cue]);
  useAnimatedReaction(
    () => (mode.get() === 1 ? (g.get() > 0.22 ? 1 : 0) + (g.get() > 0.55 ? 1 : 0) : -1),
    (now, before) => {
      if (before === null || before < 0 || now <= before) return;
      if (now === 1) scheduleOnRN(onMelt);
      if (now === 2) scheduleOnRN(onGenerated);
    },
  );

  const generate = useCallback(() => {
    takeOver();
    // nothing to sculpt: no typed pieces and none left on the box by the loop
    if (shown.length === 0 && mode.get() === 1 && pop0.get() + pop1.get() + pop2.get() + pop3.get() < 0.5) return;
    Keyboard.dismiss();
    if (reduced) {
      g.set(withTiming(1, { duration: 300, reduceMotion: NEVER }, () => lastTouch.set(clock.get())));
      return;
    }
    const from = g.get();
    // the 2 s before the loop comes back count from the moment the picture has landed
    g.set(withTiming(1, { duration: GEN_MS * (1 - from), easing: Easing.linear }, () => lastTouch.set(clock.get())));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced, shown]);

  const startOver = useCallback(() => {
    takeOver();
    g.set(withTiming(0, { duration: reduced ? 200 : 900, easing: EASE_OUT }));
    setGenerated(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced]);

  // pull the picture down: the generation runs backwards under the finger
  const cardPan = Gesture.Pan()
    .activeOffsetY([8, 999])
    .failOffsetX([-16, 16])
    .onBegin(() => {
      takeOver();
      touching.set(1);
      // catch the picture only once it is out; a touch during the generation leaves it running
      if (g.get() >= 0.5) g.set(g.get());
      dragFrom.set(g.get());
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (dragFrom.get() < 0.5) return;
      g.set(Math.max(0, Math.min(1, dragFrom.get() - Math.max(0, e.translationY) / 340)));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      touching.set(0);
      if (dragFrom.get() < 0.5) return;
      const back = e.translationY > 150 || e.velocityY > 900;
      // a spring, not a curve: the clock leaves the finger at the speed the finger had
      g.set(withSpring(back ? 0 : 1, { duration: back ? 700 : 520, dampingRatio: 1, overshootClamping: true, velocity: -e.velocityY / 340 }));
      if (back) scheduleOnRN(setGenerated, false);
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const s = scene.get();
    const l = look(g.get());
    const wipe = clear.get();
    return {
      uO0: [s[0], s[1], s[2], s[3]],
      uO1: [s[4], s[5], s[6], s[7]],
      uO2: [s[8], s[9], s[10], s[11]],
      uO3: [s[12], s[13], s[14], s[15]],
      uP0: [s[16], s[17], s[18], s[19]],
      uP1: [s[20], s[21], s[22], s[23]],
      uP2: [s[24], s[25], s[26], s[27]],
      uP3: [s[28], s[29], s[30], s[31]],
      uMerge: [cardCx, cardCy, l.k, l.pull],
      uCard: [L.card.x, L.card.y, L.card.w, L.card.h],
      uReveal: [l.reveal, l.night],
      uBlobR: l.blobR,
      uFade: 1 - wipe,
      uTime: s[32],
      uPixelRatio: pixelRatio,
    };
  });
  const emptyCard = useDerivedValue(() => (1 - look(g.get()).reveal * (1 - clear.get())) * 0.9);
  const fingerFill = useDerivedValue(() => `rgba(255,255,255,${0.16 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.55 * finger.get()})`);
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * press.get()));

  const composerStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -Math.max(0, keyboard.height.get() - insets.bottom) }] }));
  const captionStyle = useAnimatedStyle(() => ({ opacity: Math.max(0, Math.min(1, (g.get() - 0.8) / 0.2)) * (1 - clear.get()) }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: (1 - Math.min(1, g.get() * 3)) * (1 - Math.min(1, Math.max(0, pop0.get()) + Math.max(0, pop1.get()) + Math.max(0, pop2.get()) + Math.max(0, pop3.get()))) }));
  // autoPlay's words come from React state; when the loop wraps, React still shows the last loop's
  // whole prompt for a frame or two: hide words React has not taken back yet
  const typedSV = useSharedValue(0);
  useEffect(() => {
    typedSV.set(typed);
  }, [typed, typedSV]);
  const textStyle = useAnimatedStyle(() => ({ opacity: (1 - clear.get()) * (mode.get() === 0 && typedSV.get() > autoChars.get() ? 0 : 1) }));
  const genStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.04 * press.get() }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const value = manual ? text : PROMPT.slice(0, typed);
  const words = value.length ? value.split(' ') : [];
  const canGenerate = auto ? true : shown.length > 0;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <RoundedRect x={L.card.x + 0.5} y={L.card.y + 0.5} width={L.card.w - 1} height={L.card.h - 1} r={28} color={COLOR.line} style="stroke" strokeWidth={1.5} opacity={emptyCard}>
          <DashPathEffect intervals={[6, 6]} />
        </RoundedRect>
        <Rect x={0} y={0} width={w} height={h}>
          <Shader source={effect!} uniforms={uniforms} />
        </Rect>
      </Canvas>
      <ClayParticles emit={emit} g={g} clear={clear} blobX={cardCx} blobY={cardCy} width={w} height={h} enabled={!reduced} />

      {/* the bar */}
      <View style={[styles.bar, { top: L.barTop }]}>
        <PressScale style={styles.round} reduced={reduced} accessibilityRole="button" accessibilityLabel="Menu" hitSlop={4}>
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <SvgPath d="M4 6.5 H16 M4 13.5 H12" stroke={COLOR.ink} strokeWidth={1.8} strokeLinecap="round" />
          </Svg>
        </PressScale>
        <Text style={styles.brand} allowFontScaling={false}>
          Mold
        </Text>
        <View style={styles.credits} accessible accessibilityLabel="24 credits">
          <Svg width={14} height={14} viewBox="0 0 14 14">
            <SvgPath d="M7 1 C7.6 4.6 9.4 6.4 13 7 C9.4 7.6 7.6 9.4 7 13 C6.4 9.4 4.6 7.6 1 7 C4.6 6.4 6.4 4.6 7 1 Z" fill={COLOR.accent} />
          </Svg>
          <Text style={styles.creditsText} allowFontScaling={false}>
            24
          </Text>
        </View>
      </View>

      {/* the card: empty hint, and the picture you can pull back down */}
      <GestureDetector gesture={cardPan}>
        <View
          style={[styles.card, { left: L.card.x, top: L.card.y, width: L.card.w, height: L.card.h }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel={generated ? `Picture: ${value || PROMPT}, sculpted in clay` : 'Empty picture. Describe a scene below.'}
          accessibilityHint={generated ? 'Drag down to melt it back into the prompt' : undefined}
        >
          {fontsLoaded && (
            <Animated.View style={[styles.hint, hintStyle]} pointerEvents="none">
              <Text style={styles.hintTitle} allowFontScaling={false}>
                Describe a scene
              </Text>
              <Text style={styles.hintBody} allowFontScaling={false}>
                Its things turn into clay. Try a cat, a sofa, the moon or a plant.
              </Text>
            </Animated.View>
          )}
        </View>
      </GestureDetector>

      {fontsLoaded && (
        <>
          <Animated.View style={[styles.caption, { top: L.captionTop, left: L.card.x, width: L.card.w }, captionStyle]}>
            <Text style={styles.captionText} numberOfLines={1} allowFontScaling={false}>
              “{value || PROMPT}”
            </Text>
            {!auto || generated ? (
              <PressScale style={styles.newPill} reduced={reduced} onPress={startOver} accessibilityRole="button" accessibilityLabel="Remix" accessibilityHint="Melts the picture back into clay on the prompt box">
                <Text style={styles.newText} allowFontScaling={false}>
                  Remix
                </Text>
              </PressScale>
            ) : null}
          </Animated.View>

          {/* the prompt box */}
          <Animated.View style={[styles.composer, { top: L.composerTop, left: SPACE.l, right: SPACE.l, height: L.composerH }, composerStyle]}>
            <View
              style={styles.words}
              onLayout={(e) => {
                js.composerX = e.nativeEvent.layout.x + SPACE.l;
                js.composerY = e.nativeEvent.layout.y + L.composerTop;
              }}
            >
              <Animated.View style={[styles.wordsRow, textStyle]}>
                {words.length === 0 && editing ? <View style={[styles.caret, styles.caretLead]} /> : null}
                {words.length === 0 ? (
                  <Text style={[styles.word, styles.placeholder]} allowFontScaling={false}>
                    Describe a scene…
                  </Text>
                ) : (
                  words.map((wd, i) => {
                    const k = kindOfWord(wd);
                    const complete = k >= 0 && (manual || i < words.length - 1 || typed >= PROMPT.length);
                    return (
                      <Animated.Text
                        key={`${i}-${wd}`}
                        onLayout={(e) => {
                          const { x, y, width: ww, height: hh } = e.nativeEvent.layout;
                          if (k >= 0) js.words.set(k, { x: js.composerX + SPACE.m + x + ww / 2, y: js.composerY + SPACE.m + y + hh / 2 });
                        }}
                        style={[
                          styles.word,
                          complete
                            ? { color: KINDS[k as Kind].tint, backgroundColor: `${KINDS[k as Kind].tint}26`, transitionProperty: 'backgroundColor', transitionDuration: 200, transitionTimingFunction: CSS_EASE_OUT }
                            : null,
                        ]}
                        allowFontScaling={false}
                      >
                        {wd}
                      </Animated.Text>
                    );
                  })
                )}
                {(editing && words.length > 0) || (!manual && typed > 0 && typed < PROMPT.length) ? <View style={styles.caret} /> : null}
              </Animated.View>
              <TextInput
                ref={input}
                value={text}
                onChangeText={onText}
                onFocus={onFocus}
                onBlur={onBlur}
                style={styles.input}
                multiline
                autoCorrect={false}
                autoCapitalize="none"
                caretHidden
                selectionColor={COLOR.accent}
                accessibilityLabel="Prompt"
                accessibilityHint="Describe a scene. A cat, a sofa, the moon or a plant become clay."
              />
            </View>
            <View style={styles.composerRow}>
              <View style={styles.style} accessible accessibilityLabel="Style: clay">
                <Text style={styles.styleText} allowFontScaling={false}>
                  Clay
                </Text>
              </View>
              <Animated.View style={genStyle}>
                <PressScale
                  style={[styles.generate, !canGenerate && styles.disabled]}
                  reduced={reduced}
                  disabled={!canGenerate}
                  onPress={generate}
                  accessibilityRole="button"
                  accessibilityLabel="Generate"
                  accessibilityState={{ disabled: !canGenerate }}
                >
                  <Svg width={16} height={16} viewBox="0 0 14 14">
                    <SvgPath d="M7 1 C7.6 4.6 9.4 6.4 13 7 C9.4 7.6 7.6 9.4 7 13 C6.4 9.4 4.6 7.6 1 7 C4.6 6.4 6.4 4.6 7 1 Z" fill={COLOR.accentInk} />
                  </Svg>
                  <Text style={styles.generateText} allowFontScaling={false}>
                    Generate
                  </Text>
                </PressScale>
              </Animated.View>
            </View>
          </Animated.View>
        </>
      )}

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  bar: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  round: { width: 40, height: 40, borderRadius: 20, backgroundColor: COLOR.surface, alignItems: 'center', justifyContent: 'center' },
  brand: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ink },
  credits: { height: 32, paddingHorizontal: SPACE.m, borderRadius: 16, backgroundColor: COLOR.surface, flexDirection: 'row', alignItems: 'center', gap: 6 },
  creditsText: { fontFamily: FONT.semibold, fontSize: 15, color: COLOR.ink, fontVariant: ['tabular-nums'] },
  card: { position: 'absolute', borderRadius: 28 },
  hint: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center', paddingHorizontal: SPACE.xxl, gap: SPACE.s },
  hintTitle: { fontFamily: FONT.semibold, fontSize: 20, color: COLOR.ink },
  hintBody: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 21, color: COLOR.mute, textAlign: 'center' },
  caption: { position: 'absolute', height: 32, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: SPACE.m, paddingHorizontal: SPACE.xs },
  captionText: { flex: 1, fontFamily: FONT.regular, fontSize: 15, color: COLOR.mute },
  newPill: { height: 32, paddingHorizontal: SPACE.m, borderRadius: 16, backgroundColor: COLOR.surface, justifyContent: 'center' },
  newText: { fontFamily: FONT.medium, fontSize: 14, color: COLOR.ink },
  composer: { position: 'absolute', borderRadius: 24, backgroundColor: COLOR.surface, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.line, padding: SPACE.m, justifyContent: 'space-between' },
  words: { flex: 1, paddingHorizontal: 2, paddingTop: 2 },
  wordsRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', rowGap: 2 },
  word: { fontFamily: FONT.regular, fontSize: 17, lineHeight: 24, color: COLOR.ink, marginRight: 5, paddingHorizontal: 2, borderRadius: 6, overflow: 'hidden' },
  placeholder: { color: COLOR.faint },
  caret: { width: 2, height: 20, borderRadius: 1, backgroundColor: COLOR.accent, marginLeft: -2 },
  caretLead: { marginLeft: 2, marginRight: 2 },
  input: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, color: 'transparent', fontFamily: FONT.regular, fontSize: 17, lineHeight: 24, padding: 2, textAlignVertical: 'top' },
  composerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  style: { height: 32, paddingHorizontal: SPACE.m, borderRadius: 16, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.line, justifyContent: 'center' },
  styleText: { fontFamily: FONT.medium, fontSize: 14, color: COLOR.mute },
  generate: { height: 44, paddingHorizontal: SPACE.l, borderRadius: 22, backgroundColor: COLOR.accent, flexDirection: 'row', alignItems: 'center', gap: SPACE.s },
  generateText: { fontFamily: FONT.semibold, fontSize: 16, color: COLOR.accentInk },
  disabled: { opacity: 0.4 },
});
