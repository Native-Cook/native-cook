import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A daily check-in for an invented mood journal, tuft. A fluffy 3D furball sits in the middle of
// a soft gradient under "How's your vibe today?"; sliding the mood bar turns it from a grumpy
// tomato-red ball (which shakes) through a sleepy tangerine one to a beaming lime-green one
// (which hops), while the background, the big mood word and the button warm or cool with it.
// Drag anywhere sideways to set your vibe.
//
// Artwork: assets/monster-grumpy.png, monster-meh.png, monster-sunny.png (generated, prompts in
// assets/PROMPTS.md); each mood's picture fades in by max(0, 1 - |m - k|).
//
// One value drives the screen, mood m (0 grumpy, 1 meh, 2 sunny).
// Layout (393 x 852 pt): top row 34 pt; question Bricolage 38/42 pt; furball 290 pt square centred
// at 45 % of the height with a contact shadow; mood word Bricolage 46 pt; a 14 pt gradient track
// (300 pt) with a 34 pt white thumb at m / 2 of it; a 58 pt button.
//
// autoPlay timeline (LOOP = 8 s), t in seconds:
//   0.0-0.8  rest at meh (m = 1); the ball breathes (scale 1 +- 0.025, one sine per 2 s)
//   0.8-2.0  m 1 -> 0 (ease-in-out cubic); a ghost finger rides the thumb
//   2.0-3.0  grumpy: the ball shakes +-7 pt, 6 times per second, fading in and out
//   3.0-4.6  m 0 -> 2 (ease-in-out cubic)
//   4.6-6.0  sunny: two hops of 26 pt (0.5 s each, at 4.7 and 5.3) with a squash on landing
//   6.0-7.2  m 2 -> 1 (ease-in-out cubic)
//   7.2-8.0  rest, identical to t = 0
// Dragging: m follows the finger across the track (light haptic at each mood); 2 s after release
// it eases back to meh over 0.8 s and the loop starts again.

/** Seconds. */
export const LOOP = 8;
const HOLD = 2;
const RETURN = 0.8;
const BALL = 290;
const TRACK = 300;
const THUMB = 34;

const MOODS = [
  { word: 'Grumpy', note: 'Everything is a little loud today.', top: '#FFE3DC', bottom: '#F4A493', deep: '#B7311C' },
  { word: 'Meh', note: 'Not bad, not great. Just here.', top: '#FFF2DC', bottom: '#FFC57E', deep: '#B76A00' },
  { word: 'Sunny', note: 'Light, easy and ready to go.', top: '#EDF8DD', bottom: '#AEDD84', deep: '#2F7F1A' },
];
const ART = [require('./assets/monster-grumpy.png'), require('./assets/monster-meh.png'), require('./assets/monster-sunny.png')];

const BRICOLAGE = require('./assets/BricolageHeadline.ttf');
const FAMILY = 'FuzzyMoodMonster-Bricolage';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function mixColor(a: string, b: string, u: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - u) + ((pb >> 16) & 255) * u);
  const g = Math.round(((pa >> 8) & 255) * (1 - u) + ((pb >> 8) & 255) * u);
  const bl = Math.round((pa & 255) * (1 - u) + (pb & 255) * u);
  return `rgb(${r}, ${g}, ${bl})`;
}
/** A mood colour blended at m (0-2). */
function moodColor(m: number, key: 'top' | 'bottom' | 'deep') {
  'worklet';
  const c = Math.min(2, Math.max(0, m));
  return c <= 1 ? mixColor(MOODS[0][key], MOODS[1][key], c) : mixColor(MOODS[1][key], MOODS[2][key], c - 1);
}

/** Mood, shake (0-1), hop height (pt), squash (0-1) and ghost finger (0-1), `t` s into the loop. */
function stateAt(t: number) {
  'worklet';
  let m = 1;
  if (t < 2.0) m = 1 - easeInOutCubic(seg(t, 0.8, 2.0));
  else if (t < 3.0) m = 0;
  else if (t < 4.6) m = 2 * easeInOutCubic(seg(t, 3.0, 4.6));
  else if (t < 6.0) m = 2;
  else m = 2 - easeInOutCubic(seg(t, 6.0, 7.2));
  const shake = Math.sin(Math.PI * seg(t, 2.0, 3.0));
  let hop = 0;
  let squash = 0;
  for (let k = 0; k < 2; k++) {
    const u = seg(t, 4.7 + 0.6 * k, 5.2 + 0.6 * k);
    if (u > 0 && u < 1) hop = Math.sin(Math.PI * u) * 26;
    const land = seg(t, 5.2 + 0.6 * k, 5.4 + 0.6 * k);
    if (land > 0 && land < 1) squash = Math.sin(Math.PI * land);
  }
  const finger = seg(t, 0.6, 0.8) * (1 - seg(t, 2.0, 2.2)) + seg(t, 2.8, 3.0) * (1 - seg(t, 4.6, 4.8)) + seg(t, 5.8, 6.0) * (1 - seg(t, 7.2, 7.4));
  return { m, shake, hop, squash, finger };
}

function MoodArt({ k, mood, ball }: { k: number; mood: { get: () => number }; ball: { get: () => { breath: number; shake: number; hop: number; squash: number } } }) {
  const style = useAnimatedStyle(() => {
    const b = ball.get();
    const w = Math.max(0, 1 - Math.abs(mood.get() - k));
    const sx = 1 + b.breath + 0.08 * b.squash;
    const sy = 1 - b.breath - 0.1 * b.squash;
    return {
      opacity: w,
      transform: [{ translateX: b.shake }, { translateY: -b.hop + 0.05 * BALL * b.squash }, { scaleX: sx }, { scaleY: sy }],
    };
  });
  return <Animated.Image source={ART[k]} style={[styles.ball, style]} resizeMode="contain" />;
}

function MoodWord({ k, mood }: { k: number; mood: { get: () => number } }) {
  const style = useAnimatedStyle(() => {
    const w = Math.max(0, 1 - Math.abs(mood.get() - k) * 1.6);
    return { opacity: w, transform: [{ translateY: (mood.get() - k) * -18 }] };
  });
  return (
    <Animated.View style={[styles.wordBox, style]}>
      <Text style={[styles.word, { color: MOODS[k].deep }]}>{MOODS[k].word}</Text>
      <Text style={styles.note}>{MOODS[k].note}</Text>
    </Animated.View>
  );
}

type Props = {
  brand?: string;
  date?: string;
  question?: string;
  action?: string;
  /** Slides through the moods by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function FuzzyMoodMonster({
  brand = 'tuft',
  date = 'Thu, 31 Oct',
  question = 'How’s your vibe\ntoday?',
  action = 'Log my vibe',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [FAMILY]: BRICOLAGE });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const trackLeft = (width - TRACK) / 2;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const mood = useSharedValue(1);
  const ball = useSharedValue({ breath: 0, shake: 0, hop: 0, squash: 0 });
  const finger = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const userMood = useSharedValue(1);
  const lastStep = useSharedValue(1);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const t = (now - origin.get()) % LOOP;
    // Breathing runs on the loop clock: four breaths per loop.
    const breath = 0.025 * Math.sin((t / LOOP) * Math.PI * 8);
    if (touching.get() || manual.get()) {
      let m = userMood.get();
      if (!touching.get()) {
        const since = now - released.get();
        const k = easeInOutCubic(clamp01((since - HOLD) / RETURN));
        m = m + (1 - m) * k;
        if (since > HOLD + RETURN) {
          manual.set(false);
          origin.set(now);
        }
      }
      mood.set(m);
      const angry = clamp01(1 - m / 0.5);
      ball.set({ breath, shake: angry * 6 * Math.sin(now * Math.PI * 12), hop: clamp01((m - 1.6) / 0.4) * Math.abs(Math.sin(now * Math.PI * 1.6)) * 18, squash: 0 });
      finger.set(0);
      return;
    }
    const s = autoPlay ? stateAt(t) : { m: 1, shake: 0, hop: 0, squash: 0, finger: 0 };
    mood.set(s.m);
    ball.set({ breath, shake: s.shake * 7 * Math.sin(t * Math.PI * 12), hop: s.hop, squash: s.squash });
    finger.set(s.finger);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const screenStyle = useAnimatedStyle(() => ({ backgroundColor: moodColor(mood.get(), 'top') }));
  const glowStyle = useAnimatedStyle(() => ({ backgroundColor: moodColor(mood.get(), 'bottom') }));
  const buttonStyle = useAnimatedStyle(() => ({ backgroundColor: moodColor(mood.get(), 'deep') }));
  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: (Math.min(2, Math.max(0, mood.get())) / 2) * (TRACK - THUMB) }] }));
  const fingerStyle = useAnimatedStyle(() => ({ opacity: finger.get(), transform: [{ scale: 0.8 + 0.2 * finger.get() }] }));
  const shadowStyle = useAnimatedStyle(() => {
    const b = ball.get();
    return { transform: [{ scaleX: 1 - b.hop / 80 + 0.1 * b.squash }], opacity: 0.1 - b.hop / 600 };
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };
  const follow = (x: number) => {
    'worklet';
    const m = Math.min(2, Math.max(0, ((x - trackLeft - THUMB / 2) / (TRACK - THUMB)) * 2));
    userMood.set(m);
    const step = Math.round(m);
    if (step !== lastStep.get()) {
      lastStep.set(step);
      scheduleOnRN(haptic);
    }
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      touching.set(true);
      manual.set(true);
      follow(e.x);
    })
    .onUpdate((e) => follow(e.x))
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: MOODS[1].top }]} />;

  const ballTop = height * 0.45 - BALL / 2;
  const wordTop = ballTop + BALL + 6;

  return (
    <Animated.View style={[styles.screen, screenStyle]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          {/* The lower half of the screen glows in the mood's warmer colour. */}
          <Animated.View style={[styles.glow, { top: height * 0.35, height: height * 0.65 }, glowStyle]}>
            <LinearGradient colors={[MOODS[1].top + '00', '#FFFFFF33']} style={StyleSheet.absoluteFill} />
          </Animated.View>
          <LinearGradient colors={['rgba(255,255,255,0.9)', 'rgba(255,255,255,0)']} style={[styles.fade, { height: height * 0.5 }]} pointerEvents="none" />

          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <Text style={styles.date}>{date}</Text>
          </View>
          <Text style={[styles.question, { top: insets.top + 64 }]} pointerEvents="none">
            {question}
          </Text>

          <Animated.View style={[styles.shadow, { top: ballTop + BALL - 24, left: (width - 180) / 2 }, shadowStyle]} />
          <View style={[styles.ballBox, { top: ballTop, left: (width - BALL) / 2 }]} pointerEvents="none" accessible accessibilityLabel="Mood furball">
            <MoodArt k={0} mood={mood} ball={ball} />
            <MoodArt k={1} mood={mood} ball={ball} />
            <MoodArt k={2} mood={mood} ball={ball} />
          </View>

          <View style={[styles.words, { top: wordTop }]} pointerEvents="none">
            <MoodWord k={0} mood={mood} />
            <MoodWord k={1} mood={mood} />
            <MoodWord k={2} mood={mood} />
          </View>

          <View style={[styles.slider, { top: wordTop + 104, left: trackLeft }]} pointerEvents="none">
            <LinearGradient colors={['#E8472E', '#FFA329', '#7CC63A']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={styles.track} />
            <Animated.View style={[styles.thumbBox, thumbStyle]}>
              <Animated.View style={[styles.finger, fingerStyle]} />
              <View style={styles.thumb} />
            </Animated.View>
          </View>
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <Animated.View style={[styles.button, buttonStyle]} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </Animated.View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  glow: { position: 'absolute', left: 0, right: 0, borderTopLeftRadius: 200, borderTopRightRadius: 200, opacity: 0.55 },
  fade: { position: 'absolute', left: 0, right: 0, top: 0 },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: FAMILY, fontSize: 26, color: '#2B1B17', letterSpacing: -0.5 },
  date: { fontSize: 14, fontWeight: '600', color: 'rgba(43,27,23,0.55)' },
  question: { position: 'absolute', left: 24, right: 24, fontFamily: FAMILY, fontSize: 38, lineHeight: 42, color: '#2B1B17', letterSpacing: -1, textAlign: 'center' },
  ballBox: { position: 'absolute', width: BALL, height: BALL },
  ball: { position: 'absolute', left: 0, top: 0, width: BALL, height: BALL },
  shadow: { position: 'absolute', width: 180, height: 18, borderRadius: 90, backgroundColor: '#2B1B17', shadowColor: '#2B1B17', shadowOpacity: 0.6, shadowRadius: 10, shadowOffset: { width: 0, height: 0 } },
  words: { position: 'absolute', left: 24, right: 24, height: 90 },
  wordBox: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  word: { fontFamily: FAMILY, fontSize: 46, lineHeight: 54, letterSpacing: -1.2 },
  note: { marginTop: 2, fontSize: 15, lineHeight: 20, fontWeight: '500', color: 'rgba(43,27,23,0.6)' },
  slider: { position: 'absolute', width: TRACK, height: THUMB, justifyContent: 'center' },
  track: { height: 14, borderRadius: 7 },
  thumbBox: { position: 'absolute', left: 0, top: 0, width: THUMB, height: THUMB },
  thumb: { width: THUMB, height: THUMB, borderRadius: THUMB / 2, backgroundColor: '#FFFFFF', borderWidth: 3, borderColor: 'rgba(43,27,23,0.08)', shadowColor: '#2B1B17', shadowOpacity: 0.25, shadowRadius: 8, shadowOffset: { width: 0, height: 4 } },
  finger: { position: 'absolute', left: -9, top: -9, width: THUMB + 18, height: THUMB + 18, borderRadius: (THUMB + 18) / 2, backgroundColor: 'rgba(43,27,23,0.12)', borderWidth: 1.5, borderColor: 'rgba(43,27,23,0.35)' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  button: { height: 58, borderRadius: 29, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: FAMILY, fontSize: 19, color: '#FFFFFF' },
});
