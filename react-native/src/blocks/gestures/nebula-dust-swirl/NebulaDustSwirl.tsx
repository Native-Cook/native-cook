// Nebula Dust Swirl — a sleep sound playing under forty thousand grains of light.
//
// Drift's now-playing screen: a compute shader (gpu/nds-wgsl.ts) turns a slow spiral nebula, a warm
// core and two cool arms in a tilted disc, every grain added as light, nearer grains larger. A timer
// ring frames the core; the sleep timer and the track sit under it.
//
// The reusable piece is this component: give it the track, the timer's state and your labels.
//
// autoPlay: LOOP = 10 s. The nebula turns half a turn per loop (its grains come in pairs on opposite
// arms, so the frame at 10 s equals the frame at 0). A ghost finger stirs the arms along an arc round
// the core from 1.2 to 6 s; the stirred dust springs back, what is left is blended out from 8.6 to
// 9.4 s and cleared at the cut. A real touch takes over at once: the dust swirls round the finger and
// is dragged along with it, while the film slows to 0.4×. IDLE seconds after the last touch the film
// eases to the nearer end of the loop and restarts at t = 0. Reduced motion: the rest pose, still.
//
// Thread model: one useFrameCallback on the UI thread advances the clock and writes one small array
// the GPU canvas reads in the same frame; the timer and the glow are animated styles of the film time.
// react-native-webgpu needs a development build; Expo Go gets NdsSkiaFallback.

import { BlurMask, Canvas, Circle, Group, LinearGradient as SkiaLinearGradient, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { DUST, IDLE, LOOP, RETURN, SESSION, SESSION_TOTAL, STILL, TOUCH, ghostAt, glowAt, holdAt, seg, sessionAt, smooth } from './nds-motion';
import { NdsGpu } from './gpu/NdsGpu';
import { NdsSkiaFallback } from './NdsSkiaFallback';

export { LOOP };

export type NebulaTrack = {
  app: string;
  section: string;
  title: string;
  detail: string;
  hint: string;
};

const TRACK: NebulaTrack = {
  app: 'Drift',
  section: 'Sleep sounds',
  title: 'Low Tide Hum',
  detail: 'Brown noise · 8 layers',
  hint: 'Drag to stir the dust',
};

type Props = {
  autoPlay?: boolean;
  track?: NebulaTrack;
  /** seconds left on the sleep timer when the screen opens (keep ten or more in its minute: the loop counts ten down), and its whole length */
  remaining?: number;
  total?: number;
};

const INK = '#F6EEE6';
const MUTE = 'rgba(246,238,230,0.66)';
const DIGIT = 54;
const GHOST = 46;
const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;
const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0];

function buzz() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** a digit's place on its strip; the tens roll while the ones go from 9 to 10 */
function stripOf(v: number, tens: boolean) {
  'worklet';
  const ones = ((v % 10) + 10) % 10;
  return tens ? (Math.floor(v / 10) % 10) + Math.max(0, ones - 9) : ones;
}

function Digit({ seconds, tens }: { seconds: SharedValue<number>; tens: boolean }) {
  const strip = useAnimatedStyle(() => ({ transform: [{ translateY: -DIGIT * stripOf(seconds.get(), tens) }] }));
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

export function NebulaDustSwirl({ autoPlay = true, track = TRACK, remaining = SESSION, total = SESSION_TOTAL }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const compact = height < 740;
  const unit = Math.min(width * DUST.scaleW, height * DUST.scaleH);
  const centreX = width * DUST.centreX;
  const centreY = height * DUST.centreY;
  const minutes = Math.floor(remaining / 60);
  /** null until the GPU has answered */
  const [gpu, setGpu] = useState<boolean | null>(null);

  const clock = useSharedValue(0);
  const gate = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const autoStart = useSharedValue(0);
  const film = useSharedValue(STILL);
  const prevFilm = useSharedValue(0);
  const epoch = useSharedValue(0);
  const serial = useSharedValue(0);
  const down = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerVX = useSharedValue(0);
  const fingerVY = useSharedValue(0);
  const touchOn = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostA = useSharedValue(0);
  const hint = useSharedValue(1);
  const frame = useSharedValue<number[]>([]);

  useEffect(() => {
    // the loop waits at t = 0 until the dust (or its fallback) is known
    gate.set(gpu === null ? 0 : 1);
  }, [gpu, gate]);

  useEffect(() => {
    if (!reduced) return;
    film.set(STILL);
    ghostA.set(0);
    frame.set([1, 0, STILL, 0, 1, 0, 0, 0, 0, 0]);
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
    let vx = fingerVX.get();
    let vy = fingerVY.get();

    if (mode.get() === MODE_AUTO) {
      if (gate.get() === 0 || !auto) {
        autoStart.set(now);
        t = 0;
        ghostA.set(0);
        vx = 0;
        vy = 0;
      } else {
        t = (now - autoStart.get()) % LOOP;
        if (t < prevFilm.get()) epoch.set(epoch.get() + 1);
        const g = ghostAt(t);
        tx = centreX + g[0] * unit;
        ty = centreY + g[1] * unit;
        // the ghost's speed, so it drags the dust like a finger would
        const cap = TOUCH.maxSpeed;
        vx = Math.max(-cap, Math.min(cap, (tx - ghostX.get()) / Math.max(dt, 0.001)));
        vy = Math.max(-cap, Math.min(cap, (ty - ghostY.get()) / Math.max(dt, 0.001)));
        if (g[2] < 0.001) {
          vx = 0;
          vy = 0;
        }
        aim = g[2];
        ghostX.set(tx);
        ghostY.set(ty);
        ghostA.set(g[2]);
        hold = holdAt(t);
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
      vx = 0;
      vy = 0;
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
    frame.set([serial.get(), epoch.get(), t, dt, hold, tx, ty, on, vx, vy]);
  }, false);

  useEffect(() => {
    ticker.setActive(!reduced);
    return () => ticker.setActive(false);
  }, [ticker, reduced]);

  const stir = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .enabled(!reduced)
    .onBegin((e) => {
      mode.set(MODE_TOUCH);
      down.set(1);
      lastTouch.set(clock.get());
      fingerX.set(e.x);
      fingerY.set(e.y);
      fingerVX.set(0);
      fingerVY.set(0);
      ghostA.set(withTiming(0, { duration: 150 }));
      hint.set(withTiming(0, { duration: 250 }));
      scheduleOnRN(buzz);
    })
    .onUpdate((e) => {
      const cap = TOUCH.maxSpeed;
      fingerX.set(e.x);
      fingerY.set(e.y);
      fingerVX.set(Math.max(-cap, Math.min(cap, e.velocityX)));
      fingerVY.set(Math.max(-cap, Math.min(cap, e.velocityY)));
      lastTouch.set(clock.get());
    })
    .onFinalize(() => {
      down.set(0);
      fingerVX.set(0);
      fingerVY.set(0);
      lastTouch.set(clock.get());
    });

  const seconds = useDerivedValue(() => Math.max(sessionAt(film.get(), remaining) - minutes * 60, 0));
  const glow = useDerivedValue(() => 0.82 + 0.18 * glowAt(film.get()));
  const glowR = useDerivedValue(() => unit * (0.62 + 0.05 * glowAt(film.get())));
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghostA.get() * 0.9,
    transform: [{ translateX: ghostX.get() - GHOST / 2 }, { translateY: ghostY.get() - GHOST / 2 }],
  }));
  const headStyle = useAnimatedStyle(() => ({ opacity: 0.55 + 0.45 * glowAt(film.get()) }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() * 0.8 }));

  const fallback = gpu === false;
  const ring = unit * 0.5;
  const done = Math.min(Math.max(1 - remaining / total, 0), 1);
  const circ = 2 * Math.PI * ring;
  // the timer's head on the ring, from the top, clockwise
  const headA = -Math.PI / 2 + 2 * Math.PI * done;
  const headX = centreX + ring * Math.cos(headA);
  const headY = centreY + ring * Math.sin(headA);

  return (
    <GestureDetector gesture={stir}>
      <View style={styles.screen}>
        <StatusBar style="light" />
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Rect x={0} y={0} width={width} height={height}>
            <SkiaLinearGradient start={vec(0, 0)} end={vec(0, height)} colors={['#0A0E1D', '#121834', '#1B1630', '#140F22']} positions={[0, 0.45, 0.8, 1]} />
          </Rect>
          {/* the cool haze of the disc and the warm breath of the core */}
          <Group transform={[{ translateX: centreX }, { translateY: centreY }, { scaleY: 0.62 }]}>
            <Circle cx={0} cy={0} r={unit * 1.25}>
              <RadialGradient c={vec(0, 0)} r={unit * 1.25} colors={['rgba(96,128,196,0.2)', 'rgba(70,90,160,0.08)', 'rgba(40,50,100,0)']} positions={[0, 0.55, 1]} />
            </Circle>
          </Group>
          <Group opacity={glow}>
            <Circle cx={centreX} cy={centreY} r={glowR}>
              <RadialGradient c={vec(centreX, centreY)} r={unit * 0.68} colors={['rgba(255,196,140,0.34)', 'rgba(214,132,128,0.12)', 'rgba(120,70,110,0)']} positions={[0, 0.4, 1]} />
              <BlurMask blur={6} style="normal" />
            </Circle>
          </Group>
        </Canvas>

        {fallback ? <NdsSkiaFallback frame={frame} width={width} height={height} centreX={centreX} centreY={centreY} unit={unit} /> : null}
        <NdsGpu frame={frame} width={width} height={height} centreX={centreX} centreY={centreY} unit={unit} onAvailable={setGpu} disabled={fallback} />

        {/* the timer ring round the core */}
        <Svg pointerEvents="none" width={width} height={height} style={StyleSheet.absoluteFill}>
          <SvgCircle cx={centreX} cy={centreY} r={ring} stroke="rgba(246,238,230,0.16)" strokeWidth={1} fill="none" />
          <SvgCircle
            cx={centreX}
            cy={centreY}
            r={ring}
            stroke="rgba(255,226,196,0.72)"
            strokeWidth={1.6}
            strokeLinecap="round"
            fill="none"
            strokeDasharray={`${circ * done} ${circ}`}
            transform={`rotate(-90 ${centreX} ${centreY})`}
          />
        </Svg>
        <Animated.View pointerEvents="none" style={[styles.head, { left: headX - 5, top: headY - 5 }, headStyle]} />

        <View pointerEvents="none" style={[styles.page, { paddingTop: insets.top + (compact ? 8 : 14), paddingBottom: Math.max(insets.bottom, 12) + (compact ? 6 : 12) }]}>
          <View style={styles.header}>
            <Text style={styles.app}>{track.app}</Text>
            <Text style={styles.section}>{track.section}</Text>
          </View>
          <View>
            {/* one element for VoiceOver: the digit strips would read as every digit on them */}
            <View style={styles.timerRow} accessible accessibilityRole="text" accessibilityLabel={`${minutes}:${String(Math.floor(remaining % 60)).padStart(2, '0')}`}>
              <Text style={styles.digit}>{`${minutes}:`}</Text>
              <Digit seconds={seconds} tens />
              <Digit seconds={seconds} tens={false} />
            </View>
            <Text style={styles.left}>{`left of ${Math.round(total / 60)} min`}</Text>
            <View style={[styles.card, compact && styles.cardCompact]}>
              <LinearGradient colors={['#F2C49A', '#B56F86', '#3A3566', '#1E1B38']} locations={[0, 0.35, 0.72, 1]} start={{ x: 0.2, y: 0.1 }} end={{ x: 0.9, y: 1 }} style={styles.cover}>
                <View style={styles.coverCore} />
              </LinearGradient>
              <View style={styles.cardText}>
                <Text style={styles.title} numberOfLines={1}>
                  {track.title}
                </Text>
                <Text style={styles.detail} numberOfLines={1}>
                  {track.detail}
                </Text>
              </View>
              <View style={styles.pause}>
                <View style={styles.bar} />
                <View style={styles.bar} />
              </View>
            </View>
            <Animated.Text style={[styles.hint, hintStyle]}>{track.hint}</Animated.Text>
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
  screen: { flex: 1, backgroundColor: '#0A0E1D', overflow: 'hidden' },
  head: {
    position: 'absolute',
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#FFE6CC',
    boxShadow: '0 0 10px rgba(255,214,170,0.9)',
  },
  page: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, paddingHorizontal: 24, justifyContent: 'space-between' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  app: { color: INK, fontSize: 20, fontWeight: '700', letterSpacing: -0.2 },
  section: { color: MUTE, fontSize: 15, fontWeight: '500' },
  timerRow: { flexDirection: 'row', alignItems: 'center', alignSelf: 'center', height: DIGIT },
  digitBox: { height: DIGIT, overflow: 'hidden' },
  digitCell: { height: DIGIT, justifyContent: 'center' },
  digit: { color: INK, fontSize: 48, lineHeight: DIGIT, fontWeight: '600', letterSpacing: -1, fontVariant: ['tabular-nums'] },
  left: { color: MUTE, fontSize: 15, fontWeight: '500', textAlign: 'center', marginTop: 2 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 22,
    padding: 12,
    borderRadius: 22,
    backgroundColor: 'rgba(30,30,58,0.55)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(246,238,230,0.18)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 16px 36px rgba(0,0,0,0.32)',
  },
  cardCompact: { marginTop: 14 },
  cover: {
    width: 52,
    height: 52,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  coverCore: { width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(255,240,224,0.85)' },
  cardText: { flex: 1, marginHorizontal: 14 },
  title: { color: INK, fontSize: 17, fontWeight: '600' },
  detail: { color: MUTE, fontSize: 14, fontWeight: '500', marginTop: 2 },
  pause: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#F6EEE6',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 8px 18px rgba(0,0,0,0.25)',
  },
  bar: { width: 4, height: 16, borderRadius: 2, backgroundColor: '#1E1B38' },
  hint: { color: INK, fontSize: 13, fontWeight: '500', textAlign: 'center', marginTop: 12 },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(246,238,230,0.12)',
    borderWidth: 1.5,
    borderColor: 'rgba(246,238,230,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  ghostCore: { width: 14, height: 14, borderRadius: 7, backgroundColor: 'rgba(246,238,230,0.5)' },
});
