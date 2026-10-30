import { BlurStyle, Canvas, Circle, Group, LinearGradient, Picture, RadialGradient, Rect, Skia, useImage, vec, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The last step of an invented planner's onboarding, tidy: a glossy 3D ghost floats in a violet
// glow above a huge "All done!". It squashes, pops and throws a burst of glossy 3D confetti
// (curls, sparkles, balls, rings, squiggles and cubes) that swirls round it, behind the ghost and
// the headline except for a few pieces above the ghost that pass in front of it, while the headline's letters bounce one after another; then the burst spirals back
// in and the ghost swallows it with a gulp. Tap anywhere to throw the confetti again.
//
// Artwork: assets/ghost.png and six confetti sprites (generated, prompts in assets/PROMPTS.md).
//
// Layout (393 x 852 pt): violet gradient #7A5BFF -> #4A2ED3 with a #B9A6FF glow (radius 230 pt)
// behind the ghost; ghost 212 pt at 38.5 % of the height; headline Nunito Black 62 pt 136 pt
// below the ghost's centre, two lines of Nunito ExtraBold 17 pt under it; a white 58 pt button.
//
// autoPlay timeline (LOOP = 5 s), t in seconds:
//   always    the ghost bobs 6 pt and tilts 3 deg, twice per loop (sines)
//   0.00-0.60 rest: ghost, glow, headline; no confetti
//   0.60-0.90 anticipation: the ghost squashes to 1.12 x 0.86 from its hem (ease-in cubic)
//   0.90      pop: damped bounce (0.55 s); the glow flares (0.5 -> 0.85 -> 0.5 over 1.2 s)
//   0.90-2.10 burst: 22 pieces fly out along spirals to radii of 120-250 pt (ellipse 1 x 1.25),
//             15 ms apart, ease-out cubic, turning 1.1 rad; sizes 46-80 pt, front ones bigger
//   0.95-1.75 the headline's letters bounce up 12 pt and to 1.2 x, 45 ms apart
//   2.10-3.40 drift: the burst turns 0.35 rad round the ghost, each piece spinning
//   3.40-4.40 gather: radius back to 0 (ease-in cubic) with +1.5 rad of spiral, shrinking to 0.15
//   4.30-4.85 gulp: the ghost bumps 1 -> 1.12 -> 1 (damped)
//   4.40-5.00 rest, identical to t = 0
// Tap: plays the burst once from 0.6 s with a light haptic, rests 2 s, then the loop goes on.

/** Seconds. */
export const LOOP = 5;
const START = 0.6;
const GHOST = 212;
const PIECES = 22;

const C = {
  top: '#7A5BFF',
  bottom: '#4A2ED3',
  glow: '#B9A6FF',
  shadow: '#24107A',
  white: '#FFFFFF',
  soft: 'rgba(255,255,255,0.78)',
  button: '#4A2ED3',
};

const NUNITO_BLACK = require('./assets/Nunito-Black.ttf');
const NUNITO_EXTRA = require('./assets/Nunito-ExtraBold.ttf');
const BLACK = 'ConfettiGatherBadge-NunitoBlack';
const EXTRA = 'ConfettiGatherBadge-NunitoExtraBold';

type Piece = { sprite: number; angle: number; radius: number; size: number; spin: number; turn: number; front: boolean };

/** The 22 pieces, the same every loop. */
const LIST: Piece[] = Array.from({ length: PIECES }, (_, i) => {
  const h = (n: number) => {
    const x = Math.sin((i + 1) * 12.9898 + n * 78.233) * 43758.5453;
    return x - Math.floor(x);
  };
  const angle = (i / PIECES) * Math.PI * 2 + (h(1) - 0.5) * 0.35;
  // Only pieces flying above the ghost pass in front of it, so the headline stays readable.
  const front = i % 2 === 0 && Math.sin(angle) < -0.15;
  return {
    sprite: (i * 5) % 6,
    angle,
    radius: 120 + 130 * h(2),
    size: (46 + 34 * h(3)) * (front ? 1.12 : 0.9),
    spin: (h(4) > 0.5 ? 1 : -1) * (40 + 80 * h(5)),
    turn: h(6) * 360,
    front,
  };
});

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}
/** A damped bounce that starts and ends at 0. */
function bounce(x: number) {
  'worklet';
  if (x <= 0 || x >= 1) return 0;
  return Math.sin(x * Math.PI * 2.5) * Math.exp(-4 * x);
}

/** The ghost's scale (x, y), bob and tilt, and the glow's strength, `t` seconds into the loop. */
function ghostAt(t: number) {
  'worklet';
  const squash = easeInCubic(seg(t, 0.6, 0.9)) * (1 - seg(t, 0.9, 0.96));
  const pop = bounce(seg(t, 0.9, 1.45)) * 0.42;
  const gulp = bounce(seg(t, 4.3, 4.85)) * 0.3;
  const phase = (t / LOOP) * Math.PI * 4;
  return {
    sx: 1 + 0.12 * squash - pop * 0.35 + gulp,
    sy: 1 - 0.14 * squash + pop + gulp,
    bob: Math.sin(phase) * 6,
    tilt: Math.sin(phase + 0.8) * 3,
    glow: 0.5 + 0.35 * Math.sin(Math.PI * seg(t, 0.9, 2.1)),
  };
}

/** How far the burst is out (0-1) and its extra turn, for piece i. */
function spreadAt(t: number, i: number) {
  'worklet';
  const out = easeOutCubic(seg(t, 0.9 + i * 0.015, 2.1 + i * 0.015));
  const back = easeInCubic(seg(t, 3.4 + (PIECES - 1 - i) * 0.01, 4.4 - i * 0.003));
  return { spread: out * (1 - back), out, back };
}

function drawPieces(c: SkCanvas, t: number, cx: number, cy: number, sprites: (SkImage | null)[], front: boolean) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  for (let i = 0; i < PIECES; i++) {
    const p = LIST[i];
    if (p.front !== front) continue;
    const s = spreadAt(t, i);
    if (s.spread <= 0.001) continue;
    const img = sprites[p.sprite];
    if (!img) continue;
    const swirl = -1.1 * (1 - s.out) * Math.sign(p.spin) + 0.35 * smooth(seg(t, 2.1, 3.4)) + 1.5 * s.back;
    const a = p.angle + swirl;
    const r = p.radius * s.spread;
    const x = cx + Math.cos(a) * r;
    const y = cy + Math.sin(a) * r * 1.25;
    const size = p.size * (0.15 + 0.85 * Math.min(1, s.spread * 1.6));
    paint.setAlphaf(Math.min(1, s.spread * 5));
    c.save();
    c.translate(x, y);
    c.rotate(p.turn + p.spin * (t - 0.9) * s.spread, 0, 0);
    c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-size / 2, -size / 2, size, size), paint);
    c.restore();
  }
}

function drawGhost(c: SkCanvas, t: number, cx: number, cy: number, ghost: SkImage | null) {
  'worklet';
  const g = ghostAt(t);
  // Contact shadow on the glow, tightening as the ghost rises.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color(C.shadow));
  shadow.setAlphaf(0.42);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  const sw = 118 * g.sx * (1 - g.bob / 60);
  c.drawOval(Skia.XYWHRect(cx - sw / 2, cy + GHOST * 0.56, sw, 20), shadow);
  if (!ghost) return;
  c.save();
  // Squash from the hem, around the bottom of the body.
  c.translate(cx, cy + g.bob + GHOST * 0.42);
  c.rotate(g.tilt, 0, 0);
  c.scale(g.sx, g.sy);
  c.translate(0, -GHOST * 0.42);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  c.drawImageRect(ghost, Skia.XYWHRect(0, 0, ghost.width(), ghost.height()), Skia.XYWHRect(-GHOST / 2, -GHOST / 2, GHOST, GHOST), paint);
  c.restore();
}

/** Letter k of the headline: its lift and scale. */
function letterAt(t: number, k: number) {
  'worklet';
  const b = bounce(seg(t, 0.95 + k * 0.045, 1.75 + k * 0.045));
  return { lift: -12 * Math.max(0, b), scale: 1 + 0.2 * Math.max(0, b) };
}

type Props = {
  headline?: string;
  body?: string;
  action?: string;
  secondary?: string;
  /** Throws the confetti by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

function Letter({ ch, k, time }: { ch: string; k: number; time: { get: () => number } }) {
  const style = useAnimatedStyle(() => {
    const l = letterAt(time.get(), k);
    return { transform: [{ translateY: l.lift }, { scale: l.scale }] };
  });
  return <Animated.Text style={[styles.headline, style]}>{ch === ' ' ? ' ' : ch}</Animated.Text>;
}

export function ConfettiGatherBadge({
  headline = 'All done!',
  body = 'Your week is planned.\nNow go and enjoy it.',
  action = 'Let’s go',
  secondary = 'Edit my plan',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK]: NUNITO_BLACK, [EXTRA]: NUNITO_EXTRA });
  const ghost = useImage(require('./assets/ghost.png'));
  const ribbon = useImage(require('./assets/confetti-ribbon.png'));
  const star = useImage(require('./assets/confetti-star.png'));
  const ball = useImage(require('./assets/confetti-ball.png'));
  const ring = useImage(require('./assets/confetti-ring.png'));
  const squiggle = useImage(require('./assets/confetti-squiggle.png'));
  const cube = useImage(require('./assets/confetti-cube.png'));
  const sprites = useMemo(() => [ribbon, star, ball, ring, squiggle, cube], [ribbon, star, ball, ring, squiggle, cube]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.385;

  const time = useSharedValue(0);
  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const playing = useSharedValue(false);
  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const local = elapsed.get() - origin.get();
    if (playing.get()) {
      // A tapped burst plays once to rest, then the loop starts again 2 s later.
      if (local < LOOP) time.set(local);
      else if (local < LOOP + 2) time.set(0);
      else {
        playing.set(false);
        origin.set(elapsed.get());
        time.set(0);
      }
      return;
    }
    time.set(autoPlay ? local % LOOP : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  // Behind the headline: the far pieces and the ghost; in front of it: the near pieces.
  const back = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawPieces(canvas, time.get(), cx, cy, sprites, false);
    drawGhost(canvas, time.get(), cx, cy, ghost);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, sprites, ghost]);
  const front = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawPieces(canvas, time.get(), cx, cy, sprites, true);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, sprites]);
  const glowOpacity = useDerivedValue(() => ghostAt(time.get()).glow);

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => {
      playing.set(true);
      origin.set(elapsed.get() - START);
      scheduleOnRN(haptic);
    });

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: C.bottom }]} />;

  const headlineTop = cy + 136;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={tap}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Rect x={0} y={0} width={width} height={height}>
              <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[C.top, C.bottom]} />
            </Rect>
            <Group opacity={glowOpacity}>
              <Circle cx={cx} cy={cy} r={230}>
                <RadialGradient c={vec(cx, cy)} r={230} colors={[C.glow, 'rgba(185,166,255,0)']} />
              </Circle>
            </Group>
            <Picture picture={back} />
          </Canvas>

          <View style={[styles.top, { top: insets.top + 10 }]} pointerEvents="none">
            <Text style={styles.brand}>tidy</Text>
            <View style={styles.steps}>
              {[0, 1, 2, 3].map((k) => (
                <View key={k} style={styles.step} />
              ))}
            </View>
          </View>

          <View style={[styles.copy, { top: headlineTop }]} pointerEvents="none" accessible accessibilityLabel={headline}>
            <View style={styles.letters}>
              {headline.split('').map((ch, k) => (
                <Letter key={k} ch={ch} k={k} time={time} />
              ))}
            </View>
            <Text style={styles.body}>{body}</Text>
          </View>

          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={front} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 12 }]}>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
        <Text style={styles.secondary}>{secondary}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.top },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BLACK, fontSize: 28, lineHeight: 34, color: C.white, letterSpacing: -0.5 },
  steps: { flexDirection: 'row', gap: 5 },
  step: { width: 22, height: 5, borderRadius: 3, backgroundColor: C.white },
  copy: { position: 'absolute', left: 20, right: 20, alignItems: 'center' },
  letters: { flexDirection: 'row', justifyContent: 'center' },
  headline: { fontFamily: BLACK, fontSize: 62, lineHeight: 70, color: C.white, letterSpacing: -1 },
  body: { marginTop: 8, fontFamily: EXTRA, fontSize: 17, lineHeight: 23, color: C.soft, textAlign: 'center' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0, alignItems: 'center' },
  button: { alignSelf: 'stretch', height: 58, borderRadius: 29, backgroundColor: C.white, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: BLACK, fontSize: 19, color: C.button },
  secondary: { marginTop: 14, marginBottom: 2, fontFamily: EXTRA, fontSize: 15, lineHeight: 20, color: C.soft },
});
