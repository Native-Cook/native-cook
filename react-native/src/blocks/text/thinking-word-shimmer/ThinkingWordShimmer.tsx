import {
  Blur,
  Canvas,
  Group,
  LinearGradient,
  Paint,
  RadialGradient,
  Rect,
  Text as SkiaText,
  useClock,
  useFont,
  vec,
  type SkFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  ReduceMotion,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

// An assistant at work: the request on top, and in a black card one status word at a time
// ("Thinking...", "Processing...", "Analyzing...", "Computing...", "Almost..."). Each word rises
// in sharpening from a blur, a bright band sweeps through its letters while its three dots pulse
// one after the other, then it lifts away blurring as the next word rises in under it. A port of
// the SwiftUI block, number for number.
//
// Every word gets a 2 s slot; times below are from the start of the slot, when the word has
// just landed:
//   0.00-0.15   at rest, dots pulsing (each dot 1 s around, 0.9 rad behind the one before)
//   0.15-1.15   a white band 0.6 of the word wide crosses it, left to right (ease-in-out);
//               outside the band the word is at 50% white
//   1.45-1.80   the word lifts 14 pt, blurs to 6 pt and fades (ease-in)
//   1.60-2.00   the next word rises 14 pt into place, sharpening and fading in (ease-out)
// autoPlay loop: the five words, LOOP = 10 s, back on "Thinking..." exactly where it started.
// The ambient glows drift on the same 10 s period.

/** Seconds. */
const SLOT = 2;
export const LOOP = SLOT * 5;
const SWEEP_AT = 0.15;
const SWEEP_FOR = 1;
/** From the start of a slot to the start of the change to the next word. */
const SWITCH_AT = 1.45;
const EXIT_FOR = 0.35;
const ENTER_DELAY = 0.15;
const ENTER_FOR = 0.4;
/** From the start of a change to the moment the new word has landed (its slot's start). */
const LANDED = ENTER_DELAY + ENTER_FOR;
const TRAVEL = 14;
const BLUR = 6;
/** The word's brightness outside the band, which is white; the band is 0.6 of the word wide. */
const BASE = 0.5;
const DOT_PERIOD = 1;
const DOT_LAG = 0.9;
const USER_HOLD = 2;
const TEXT_SIZE = 24;
// Inter's line box (ascent 0.969 em, descent 0.241 em) centred, as SwiftUI centres a Text: the
// baseline sits 0.364 em below the middle.
const BASELINE_DROP = ((0.96875 - 0.2412109375) / 2) * TEXT_SIZE;

const C = {
  background: '#04080A',
  teal: '#12505F',
  deepTeal: '#0B3A47',
  warm: '#8C7A4E',
  text: '#E6ECEE',
  label: '#7D8A90',
  step: '#263034',
};

/**
 * A run's advance width from its glyphs. `SkFont.getTextWidth` came back rounded down to whole points
 * here (94 for "Thinking", whose glyphs add up to 97.4), which put the first dot on the "g".
 */
const textWidth = (font: SkFont, text: string) => font.getGlyphWidths(font.getGlyphIDs(text)).reduce((a, b) => a + b, 0);

const alpha = (hex: string, a: number) => {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
};

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOut(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}
function easeIn(t: number) {
  'worklet';
  return t * t;
}
function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t);
}
/** Where the bright band's centre is, in units of the word's width, `s` seconds into its slot. */
function band(s: number) {
  'worklet';
  const t = s % SLOT;
  return -0.35 + 1.7 * easeInOut(clamp01((t - SWEEP_AT) / SWEEP_FOR));
}
/** A dot's opacity, `s` seconds into the word's slot. */
function dot(k: number, s: number) {
  'worklet';
  return 0.3 + 0.7 * (0.5 + 0.5 * Math.sin((2 * Math.PI * s) / DOT_PERIOD - k * DOT_LAG));
}

type Props = {
  words?: string[];
  label?: string;
  request?: string;
  hint?: string;
  /** Goes through the words by itself, one every 2 s. */
  autoPlay?: boolean;
};

const DEFAULT_WORDS = ['Thinking', 'Processing', 'Analyzing', 'Computing', 'Almost'];

export function ThinkingWordShimmer({
  words = DEFAULT_WORDS,
  label = 'Assistant',
  request = "Summarise this week's customer calls and flag anything urgent.",
  hint = 'Tap to skip ahead',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ 'Inter-SemiBold': require('./assets/Inter-SemiBold.ttf') });
  const font = useFont(require('./assets/Inter-SemiBold.ttf'), TEXT_SIZE);
  const loop = SLOT * words.length;

  const clock = useClock();
  const [current, setCurrent] = useState(0);
  // Which word is in and which is on its way out, on the UI thread, so a change starts on the frame
  // it is made and never waits for React (every word is always mounted and posed from these).
  const currentWord = useSharedValue(0);
  const previousWord = useSharedValue(-1);
  // When the change to `current` began (clock ms); `current` landed LANDED later.
  const changedAt = useSharedValue(-LANDED * 1000);
  // Where the ambient glows' period starts (clock ms).
  const origin = useSharedValue(0);
  const [touches, setTouches] = useState(0);

  const latest = useRef({ current, words });
  useEffect(() => {
    latest.current = { current, words };
  });

  // The screen shows once both faces are loaded; its first frame: "Thinking..." has just landed.
  const ready = fontsLoaded && font != null;
  useEffect(() => {
    if (!ready) return;
    origin.set(clock.get());
    changedAt.set(clock.get() - LANDED * 1000);
  }, [ready, clock, origin, changedAt]);

  const change = useCallback(
    (to: number) => {
      previousWord.set(latest.current.current);
      currentWord.set(to);
      changedAt.set(clock.get());
      latest.current.current = to;
      setCurrent(to);
    },
    [changedAt, clock, currentWord, previousWord],
  );

  useEffect(() => {
    if (!autoPlay || reduced || !ready) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, Math.max(0, ms)));
    // `landed` is when the word on screen landed (clock ms); each slot changes word at 1.45 s.
    const cycle = (landed: number) => {
      after(landed + SWITCH_AT * 1000 - clock.get(), () => {
        change((latest.current.current + 1) % latest.current.words.length);
        cycle(landed + SLOT * 1000);
      });
    };
    if (touches === 0) cycle(clock.get() - Math.max(0, clock.get() - changedAt.get() - LANDED * 1000));
    else
      after(USER_HOLD * 1000, () => {
        // Back to the first word, unless it is already up and not yet leaving.
        const s = (clock.get() - changedAt.get()) / 1000 - LANDED;
        let landed: number;
        if (latest.current.current === 0 && s >= 0 && s < SWITCH_AT) landed = clock.get() - s * 1000;
        else {
          change(0);
          landed = clock.get() + LANDED * 1000;
        }
        // The glows' period starts over with the loop.
        origin.set(landed);
        cycle(landed);
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, reduced, ready, touches, clock, changedAt, origin, change]);

  const tapped = () => {
    setTouches((n) => n + 1);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    change((latest.current.current + 1) % latest.current.words.length);
  };

  // The glows drift a little once per loop.
  const phase = useDerivedValue(() => (reduced ? 0 : (2 * Math.PI * (((clock.get() - origin.get()) / 1000) % loop)) / loop));
  const tealC = useDerivedValue(() => vec(width * (0.7 + 0.05 * Math.sin(phase.get())), height * (0.3 + 0.03 * Math.cos(phase.get()))));
  const deepC = useDerivedValue(() => vec(width * (0.2 + 0.04 * Math.cos(phase.get())), height * 0.7));
  const warmC = useDerivedValue(() => vec(width * (0.56 + 0.06 * Math.sin(phase.get() + 1.3)), height * (0.68 + 0.02 * Math.sin(phase.get()))));

  const cardW = width - 40;
  const cardH = Math.round(cardW * 0.62);

  const backdrop = (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={width} height={height} color={C.background} />
        <Rect x={0} y={0} width={width} height={height}>
          <RadialGradient c={tealC} r={width * 0.75} colors={[alpha(C.teal, 0.75), alpha(C.teal, 0)]} />
        </Rect>
        <Rect x={0} y={0} width={width} height={height}>
          <RadialGradient c={deepC} r={width * 0.65} colors={[alpha(C.deepTeal, 0.85), alpha(C.deepTeal, 0)]} />
        </Rect>
        <Rect x={0} y={0} width={width} height={height}>
          <RadialGradient c={warmC} r={width * 0.6} colors={[alpha(C.warm, 0.6), alpha(C.warm, 0)]} />
        </Rect>
    </Canvas>
  );

  if (!ready || !font) {
    return (
      <View style={styles.screen}>
        <StatusBar style="light" />
        {backdrop}
      </View>
    );
  }

  return (
    <Pressable style={styles.screen} onPress={tapped} accessibilityRole="button" accessibilityHint="Skips to the next status">
      <StatusBar style="light" />
      {/* Near-black with a teal glow up high, a deeper teal low on the left and a warm one low in the middle. */}
      {backdrop}

      <View style={[styles.column, { paddingTop: insets.top + 20 }]} pointerEvents="none">
        <View>
          <Text style={styles.label}>{label.toUpperCase()}</Text>
          <Text style={[styles.request, { fontFamily: 'Inter-SemiBold' }]}>{request}</Text>
        </View>

        <View style={{ flex: 1 }} />

        <View style={[styles.card, { width: cardW, height: cardH }]} accessible accessibilityLabel={`${words[current]}…`}>
          <Canvas style={{ width: cardW, height: cardH }}>
            {words.map((word, i) => (
              <Status
                key={word}
                index={i}
                word={word}
                font={font}
                w={cardW}
                h={cardH}
                clock={clock}
                changedAt={changedAt}
                current={currentWord}
                previous={previousWord}
                reduced={reduced}
              />
            ))}
          </Canvas>
          <View style={styles.cardEdge} />
        </View>

        {/* One step per word. */}
        <View style={styles.steps}>
          {words.map((w, i) => (
            <Step key={w} on={i === current} reduced={reduced} />
          ))}
        </View>

        <View style={{ flex: 1 }} />

        <Text style={[styles.hint, { marginBottom: insets.bottom + 28 }]}>{hint}</Text>
      </View>
    </Pressable>
  );
}

function Step({ on, reduced }: { on: boolean; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const config = { mass: 1, stiffness: 246.7, damping: 2 * 0.85 * Math.sqrt(246.7), reduceMotion: ReduceMotion.Never };
    return { width: reduced ? (on ? 28 : 14) : withSpring(on ? 28 : 14, config), backgroundColor: on ? C.text : C.step };
  });
  return <Animated.View style={[styles.step, style]} />;
}

type StatusProps = {
  word: string;
  font: SkFont;
  w: number;
  h: number;
  clock: SharedValue<number>;
  changedAt: SharedValue<number>;
  /** This word's place in the list, and which word is in and which on its way out. */
  index: number;
  current: SharedValue<number>;
  previous: SharedValue<number>;
  reduced: boolean;
};

/** A status word and its three dots, centred in the card: the word is lit by the moving band, the dots pulse. */
function Status({ word, font, w, h, clock, changedAt, index, current, previous, reduced }: StatusProps) {
  const wordW = textWidth(font, word);
  const dotW = textWidth(font, '.');
  const x0 = (w - (wordW + 3 * dotW)) / 2;
  const baseline = h / 2 + BASELINE_DROP;

  // Lift, blur, opacity, and seconds into the word's slot.
  const pose = useDerivedValue(() => {
    const since = (clock.get() - changedAt.get()) / 1000;
    const coming = current.get() === index;
    const leaving = !coming && previous.get() === index;
    if (!coming && !leaving) return { dy: 0, blur: 0, opacity: 0, s: 0 };
    if (reduced) {
      const p = clamp01(since / 0.25);
      return { dy: 0, blur: 0, opacity: leaving ? 1 - p : p, s: 0 };
    }
    if (leaving) {
      if (since >= EXIT_FOR) return { dy: 0, blur: 0, opacity: 0, s: 0 };
      const q = easeIn(clamp01(since / EXIT_FOR));
      return { dy: -TRAVEL * q, blur: BLUR * q, opacity: 1 - q, s: SWITCH_AT + since };
    }
    const q = easeOut(clamp01((since - ENTER_DELAY) / ENTER_FOR));
    return { dy: TRAVEL * (1 - q), blur: BLUR * (1 - q), opacity: q, s: Math.max(0, since - LANDED) };
  });
  const transform = useDerivedValue(() => [{ translateY: pose.get().dy }]);
  const opacity = useDerivedValue(() => pose.get().opacity);
  const blur = useDerivedValue(() => pose.get().blur);
  const start = useDerivedValue(() => vec(x0 + ((reduced ? 2 : band(pose.get().s)) - 0.5) * wordW, 0));
  const end = useDerivedValue(() => vec(x0 + ((reduced ? 2 : band(pose.get().s)) + 0.5) * wordW, 0));
  const base = `rgba(255,255,255,${reduced ? 0.9 : BASE})`;
  const dot0 = useDerivedValue(() => (reduced ? 0.9 : dot(0, pose.get().s)));
  const dot1 = useDerivedValue(() => (reduced ? 0.9 : dot(1, pose.get().s)));
  const dot2 = useDerivedValue(() => (reduced ? 0.9 : dot(2, pose.get().s)));
  const dots = [dot0, dot1, dot2];

  return (
    <Group transform={transform} opacity={opacity} layer={<BlurLayer blur={blur} />}>
      <SkiaText x={x0} y={baseline} text={word} font={font}>
        <LinearGradient start={start} end={end} colors={[base, base, '#FFFFFF', base, base]} positions={[0, 0.2, 0.5, 0.8, 1]} />
      </SkiaText>
      {dots.map((o, k) => (
        <SkiaText key={k} x={x0 + wordW + k * dotW} y={baseline} text="." font={font} color="#FFFFFF" opacity={o} />
      ))}
    </Group>
  );
}

function BlurLayer({ blur }: { blur: SharedValue<number> }) {
  return (
    <Paint>
      <Blur blur={blur} mode="decal" />
    </Paint>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  column: { flex: 1, paddingHorizontal: 20 },
  label: { fontSize: 13, fontWeight: '600', letterSpacing: 1.6, color: C.label },
  request: { marginTop: 10, fontSize: 22, lineHeight: 22 * 1.21 + 4, color: C.text, opacity: 0.92 },
  card: {
    alignSelf: 'center',
    borderRadius: 28,
    borderCurve: 'continuous',
    backgroundColor: '#000000',
    boxShadow: '0 18px 30px rgba(0,0,0,0.6)',
    overflow: 'hidden',
  },
  cardEdge: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: 28,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.07)',
  },
  steps: { alignSelf: 'center', marginTop: 24, flexDirection: 'row', gap: 6 },
  step: { height: 3, borderRadius: 1.5 },
  hint: { alignSelf: 'center', fontSize: 13, fontWeight: '500', color: C.label, opacity: 0.8 },
});
