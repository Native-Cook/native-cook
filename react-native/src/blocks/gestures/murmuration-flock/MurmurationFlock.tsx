// Murmuration Flock — a breathing session led by twenty thousand starlings.
//
// Dusk's wind-down screen: a compute shader (gpu/mf-wgsl.ts) places a flock against the evening sky, a
// dark ribbon that folds and streams. On the inhale it gathers into a soft circle, on the exhale it
// lets go and streams out again.
//
// autoPlay: LOOP = 12 s. Every bird's place is a pure function of its seeds and the loop time; a ghost
// finger parts the flock during the exhale, and what it pushed is blended out in the rest and cleared
// at the cut, so frame 12 s equals frame 0. A real touch takes over at once: birds flee and swirl
// around it while the film slows to a quarter. IDLE seconds after the last touch the film runs to the
// nearer end of the loop and restarts at t = 0. Reduced motion: the circle, still, no loop.
//
// Thread model: one useFrameCallback on the UI thread advances the clock and writes one small array
// the canvas reads in the same frame; labels and the timer are animated styles of the film time.
// react-native-webgpu needs a development build; Expo Go gets MfSkiaFallback.

import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { FLOCK, IDLE, LOOP, RETURN, RING, STILL, T, TOUCH, ghostAt, guideAt, seg, sessionAt, smooth, wordsAt } from './mf-motion';
import { MfGpu } from './gpu/MfGpu';
import { MfSkiaFallback } from './MfSkiaFallback';

export { LOOP };

export type MurmurationCopy = {
  app: string;
  session: string;
  settle: string;
  inhale: string;
  exhale: string;
  pace: string;
  hint: string;
};

const COPY: MurmurationCopy = {
  app: 'Dusk',
  session: 'Evening wind-down',
  settle: 'Settle',
  inhale: 'Inhale',
  exhale: 'Exhale',
  pace: '4 in, 1 hold, 4 out',
  hint: 'Touch the flock',
};

const INK = '#FFF4E6';
const MUTE = 'rgba(255,244,230,0.72)';
const DIGIT = 24;
const GHOST = 46;
const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;
const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0];

type Props = {
  autoPlay?: boolean;
  copy?: MurmurationCopy;
};

function buzz() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** the ones digit's place on its strip */
function onesOf(s: number) {
  'worklet';
  return ((s % 10) + 10) % 10;
}

/** the tens digit's place: it rolls while the ones go from 9 to 10 */
function tensOf(s: number) {
  'worklet';
  const ones = onesOf(s);
  return (Math.floor(s / 10) % 10) + Math.max(0, ones - 9);
}

function Digit({ film, tens }: { film: SharedValue<number>; tens: boolean }) {
  const strip = useAnimatedStyle(() => {
    const s = sessionAt(film.get());
    return { transform: [{ translateY: -DIGIT * (tens ? tensOf(s) : onesOf(s)) }] };
  });
  return (
    <View style={styles.digitBox}>
      <Animated.View style={strip}>
        {DIGITS.map((d, i) => (
          <View key={i} style={styles.digitCell}>
            <Text style={styles.digit}>{d}</Text>
          </View>
        ))}
      </Animated.View>
    </View>
  );
}

function Word({ film, index, label }: { film: SharedValue<number>; index: 0 | 1 | 2; label: string }) {
  const style = useAnimatedStyle(() => {
    const k = wordsAt(film.get())[index];
    return { opacity: k, transform: [{ translateY: 8 * (1 - k) }, { scale: 0.97 + 0.03 * k }] };
  });
  return <Animated.Text style={[styles.word, style]}>{label}</Animated.Text>;
}

export function MurmurationFlock({ autoPlay = true, copy = COPY }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const compact = height < 740;
  const unit = Math.min(width * FLOCK.scaleW, height * FLOCK.scaleH);
  const centreX = width * FLOCK.centreX;
  const centreY = height * FLOCK.centreY;
  /** null until the GPU has answered */
  const [gpu, setGpu] = useState<boolean | null>(null);

  const clock = useSharedValue(0);
  const gate = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const autoStart = useSharedValue(0);
  const film = useSharedValue(reduced ? STILL : 0);
  const prevFilm = useSharedValue(0);
  const epoch = useSharedValue(0);
  const serial = useSharedValue(0);
  const down = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const touchOn = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostA = useSharedValue(0);
  const hint = useSharedValue(1);
  const frame = useSharedValue<number[]>([]);

  useEffect(() => {
    // the loop waits at t = 0 until the flock (or its fallback) is known
    gate.set(gpu === null ? 0 : 1);
  }, [gpu, gate]);

  useEffect(() => {
    if (!reduced) return;
    // one still frame: the circle
    film.set(STILL);
    ghostA.set(0);
    frame.set([1, 0, STILL, 0, 1, 0, 0, 0]);
  }, [reduced, film, ghostA, frame]);

  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    const now = clock.get();
    let t = film.get();
    let hold = 1;
    let aim = 0;
    let tx = fingerX.get();
    let ty = fingerY.get();

    if (mode.get() === MODE_AUTO) {
      if (gate.get() === 0 || !auto) {
        autoStart.set(now);
        t = 0;
        ghostA.set(0);
      } else {
        t = (now - autoStart.get()) % LOOP;
        if (t < prevFilm.get()) epoch.set(epoch.get() + 1);
        const g = ghostAt(t);
        tx = g[0] * width;
        ty = g[1] * height;
        aim = g[2];
        ghostX.set(tx);
        ghostY.set(ty);
        ghostA.set(g[2]);
        hold = 1 - smooth(seg(t, T.settle[0], T.settle[1]));
      }
    } else if (mode.get() === MODE_TOUCH) {
      t = (t + dt * TOUCH.filmRate) % LOOP;
      aim = down.get();
      if (auto && down.get() === 0 && now - lastTouch.get() >= IDLE) {
        returnFrom.set(t);
        returnAt.set(now);
        mode.set(MODE_RETURN);
      }
    } else {
      const u = seg(now - returnAt.get(), 0, RETURN);
      const from = returnFrom.get();
      const to = from > LOOP / 2 ? LOOP : 0;
      t = from + (to - from) * smooth(u);
      hold = 1 - smooth(u);
      if (u >= 1) {
        t = 0;
        autoStart.set(now);
        epoch.set(epoch.get() + 1);
        mode.set(MODE_AUTO);
        hint.set(withTiming(1, { duration: 400 }));
      }
    }

    prevFilm.set(t);
    film.set(t);
    const on = touchOn.get() + (aim - touchOn.get()) * (1 - Math.exp(-dt * TOUCH.ease));
    touchOn.set(on);
    serial.set(serial.get() + 1);
    frame.set([serial.get(), epoch.get(), t, dt, hold, tx, ty, on]);
  }, false);

  useEffect(() => {
    ticker.setActive(!reduced);
    return () => ticker.setActive(false);
  }, [ticker, reduced]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .enabled(!reduced)
    .onBegin((e) => {
      mode.set(MODE_TOUCH);
      down.set(1);
      lastTouch.set(clock.get());
      fingerX.set(e.x);
      fingerY.set(e.y);
      ghostA.set(withTiming(0, { duration: 150 }));
      hint.set(withTiming(0, { duration: 250 }));
      scheduleOnRN(buzz);
    })
    .onUpdate((e) => {
      fingerX.set(e.x);
      fingerY.set(e.y);
      lastTouch.set(clock.get());
    })
    .onFinalize(() => {
      down.set(0);
      lastTouch.set(clock.get());
    });

  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghostA.get() * 0.9,
    transform: [{ translateX: ghostX.get() - GHOST / 2 }, { translateY: ghostY.get() - GHOST / 2 }],
  }));
  const guideStyle = useAnimatedStyle(() => {
    const k = guideAt(film.get());
    return { opacity: 0.3 * k, transform: [{ scale: 0.92 + 0.08 * k }] };
  });
  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() * 0.8 }));

  const fallback = gpu === false;
  const guide = unit * RING.radius * 2 + 34;
  const ridgeH = Math.round(height * 0.3);

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.screen}>
        <StatusBar style="light" />
        <LinearGradient
          colors={['#2C3163', '#5B4A82', '#A9668A', '#E58F73', '#F6BE7E']}
          locations={[0, 0.3, 0.56, 0.8, 1]}
          style={StyleSheet.absoluteFill}
        />
        <Animated.View
          pointerEvents="none"
          style={[styles.guide, { width: guide, height: guide, borderRadius: guide / 2, left: centreX - guide / 2, top: centreY - guide / 2 }, guideStyle]}
        />
        {fallback ? <MfSkiaFallback frame={frame} width={width} height={height} centreX={centreX} centreY={centreY} unit={unit} /> : null}
        <MfGpu frame={frame} width={width} height={height} centreX={centreX} centreY={centreY} unit={unit} onAvailable={setGpu} disabled={fallback} />

        <Svg pointerEvents="none" width={width} height={ridgeH} viewBox="0 0 400 160" preserveAspectRatio="none" style={styles.ridge}>
          <Path d="M0 34 C 44 18 82 24 124 36 C 170 50 208 20 256 16 C 306 12 352 36 400 24 L400 160 L0 160 Z" fill="#6E4468" opacity={0.72} />
          <Path d="M0 66 C 60 48 104 60 158 66 C 214 72 262 50 316 54 C 350 56 376 64 400 60 L400 160 L0 160 Z" fill="#2E2340" />
        </Svg>

        <View pointerEvents="none" style={[styles.page, { paddingTop: insets.top + (compact ? 8 : 14), paddingBottom: Math.max(insets.bottom, 12) + (compact ? 8 : 16) }]}>
          <View style={styles.head}>
            <View>
              <Text style={styles.app}>{copy.app}</Text>
              <Text style={styles.session}>{copy.session}</Text>
            </View>
            {/* An odometer: VoiceOver would read its columns as loose digits. */}
            <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.timer}>
              <Text style={styles.digit}>4:</Text>
              <Digit film={film} tens />
              <Digit film={film} tens={false} />
            </View>
          </View>
          <View style={styles.foot}>
            <View style={styles.words}>
              <Word film={film} index={0} label={copy.settle} />
              <Word film={film} index={1} label={copy.inhale} />
              <Word film={film} index={2} label={copy.exhale} />
            </View>
            <Text style={styles.pace}>{copy.pace}</Text>
            <Animated.Text style={[styles.hint, hintStyle]}>{copy.hint}</Animated.Text>
          </View>
        </View>

        <Animated.View pointerEvents="none" style={[styles.ghost, ghostStyle]}>
          <View style={styles.ghostCore} />
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#2C3163', overflow: 'hidden' },
  guide: { position: 'absolute', borderWidth: 1, borderColor: 'rgba(255,240,222,0.9)' },
  ridge: { position: 'absolute', left: 0, bottom: 0 },
  page: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, paddingHorizontal: 24, justifyContent: 'space-between' },
  head: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  app: { color: INK, fontSize: 17, fontWeight: '700' },
  session: { color: MUTE, fontSize: 14, fontWeight: '500', marginTop: 2 },
  timer: { flexDirection: 'row', alignItems: 'center', height: DIGIT },
  digitBox: { height: DIGIT, overflow: 'hidden' },
  digitCell: { height: DIGIT, justifyContent: 'center' },
  digit: { color: INK, fontSize: 19, lineHeight: DIGIT, fontWeight: '600', fontVariant: ['tabular-nums'] },
  foot: { alignItems: 'center' },
  words: { height: 58, alignSelf: 'stretch', alignItems: 'center', justifyContent: 'center' },
  word: { position: 'absolute', color: INK, fontSize: 46, lineHeight: 56, fontWeight: '700', letterSpacing: -0.8 },
  pace: { color: INK, fontSize: 15, fontWeight: '500', marginTop: 2, fontVariant: ['tabular-nums'] },
  hint: { color: INK, fontSize: 13, fontWeight: '500', marginTop: 8 },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(255,244,230,0.14)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,244,230,0.6)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  ghostCore: { width: 14, height: 14, borderRadius: 7, backgroundColor: 'rgba(255,244,230,0.55)' },
});
