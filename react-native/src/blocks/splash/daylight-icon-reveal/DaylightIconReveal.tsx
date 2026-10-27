import { StatusBar } from 'expo-status-bar';
import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { useMotionPreference } from '@/shared/useMotionPreference';
import { PressScale } from '@/shared/PressScale';
import { SwapText } from '@/shared/SwapText';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import Svg, { Defs, Ellipse, RadialGradient, Stop } from 'react-native-svg';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Image, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { ScrollView } from 'react-native-gesture-handler';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Animated, { cancelAnimation, Easing, type SharedValue, useAnimatedReaction, useAnimatedStyle, useSharedValue, withRepeat, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 8.8;
function previewPhase(t: number) { if (!Number.isFinite(t)) return 0; const phase = t % LOOP; return phase < 0 ? phase + LOOP : phase; }
const EASE = Easing.bezier(0.23, 1, 0.32, 1).factory();
const SWEEP = Easing.bezier(0.77, 0, 0.175, 1).factory();
const ART = require('./assets/daylight-icon.webp');
const IMAGE_SIZE = 256;
// Native image body within the original transparent1254px artwork (not its shadow).
const BODY = { x: 82 / 1254, y: 86 / 1254, width: 1093 / 1254, height: 1090 / 1254 };
// t, horizontal extent, horizontal perspective, vertical perspective,
// top-center Y, top-edge inclination, vertical extent, opacity.
// Visible plane fits come from source60fps edge measurements at1.3s onward.
// Earlier offscreen corners are inferred from the visible top edge/divider.
const POSES = [
  [0.35, 4, 0.15, -0.3, 0.66, -0.581, 1.2, 0],
  [0.4, 4, 0.15, -0.3, 0.66, -0.581, 1.2, 0.06],
  [0.5, 3.3, 0.15, -0.25, 0.6075, -0.4072, 0.95, 0.14],
  [0.75, 2.2, 0.15, -0.27, 0.467, -0.04, 0.65, 0.48],
  [1, 1.3, 0.15, -0.27, 0.407, 0.035, 0.45, 0.76],
  [1.2, 0.86, 0.14, -0.27, 0.352, 0.055, 0.35, 0.94],
  [1.3, 0.769814, 0.135696, -0.263308, 0.33301, 0.054257, 0.260944, 1],
  [1.4, 0.695834, 0.104908, -0.216638, 0.31571, 0.038729, 0.239891, 1],
  [1.5, 0.653934, 0.08079, -0.149138, 0.300033, 0.036785, 0.247728, 1],
  [1.6, 0.617184, 0.085426, -0.102367, 0.288539, 0.0336, 0.246882, 1],
  [1.7, 0.584664, 0.056244, -0.080393, 0.278003, 0.025165, 0.239353, 1],
  [1.8, 0.56726, 0.049661, -0.026634, 0.267928, 0.023277, 0.25257, 1],
  [2, 0.517074, 0.060975, -0.038964, 0.255785, 0.01578, 0.222516, 1],
  [2.2, 0.5037, 0, 0, 0.24573, 0, 0.23549, 1],
  [2.5, 0.48889, 0, 0, 0.23891, 0, 0.22867, 1],
  [2.7, 0.48889, 0, 0, 0.23891, 0, 0.22526, 1],
  [3, 0.48889, 0, 0, 0.23891, 0, 0.22526, 1],
  [3.3, 0.5037, 0, 0, 0.23891, 0, 0.23208, 1],
  [4, 0.5037, 0, 0, 0.24232, 0, 0.23208, 1],
] as const;
const HEADING_LIFT = [[1.75, 0.32], [1.85, 0.29], [1.9, 0.24061], [2, 0.18089], [2.1, 0.1314], [2.2, 0.09215], [2.3, 0.06143], [2.4, 0.03754], [2.5, 0.02048], [2.6, 0.01024], [2.7, 0.00171], [2.9, -0.00683], [3.3, -0.00683], [4, 0]] as const;
const BUTTON_LIFT = [[2.15, 0.25], [2.25, 0.165], [2.3, 0.13311], [2.4, 0.09215], [2.5, 0.06143], [2.6, 0.03754], [2.7, 0.02048], [2.8, 0.01024], [2.9, 0], [3.1, -0.00683], [3.5, -0.00683], [4, 0]] as const;

function sampleChannel(t: number, keys: readonly (readonly number[])[], channel: number) {
  'worklet';
  const last = keys.length - 1;
  if (t <= keys[0][0]) return keys[0][channel];
  if (t >= keys[last][0]) return keys[last][channel];
  let i = 0;
  while (keys[i + 1][0] < t) i++;
  const a = keys[i][0], b = keys[i + 1][0], y0 = keys[i][channel], y1 = keys[i + 1][channel];
  const d = (y1 - y0) / (b - a);
  const before = i > 0 ? (y0 - keys[i - 1][channel]) / (a - keys[i - 1][0]) : 0;
  const after = i + 1 < last ? (keys[i + 2][channel] - y1) / (keys[i + 2][0] - b) : 0;
  const m0 = before * d > 0 ? 2 * before * d / (before + d) : 0;
  const m1 = after * d > 0 ? 2 * after * d / (after + d) : 0;
  const p = (t - a) / (b - a), p2 = p * p, p3 = p2 * p;
  return (2 * p3 - 3 * p2 + 1) * y0 + (p3 - 2 * p2 + p) * (b - a) * m0 + (-2 * p3 + 3 * p2) * y1 + (p3 - p2) * (b - a) * m1;
}
function poseChannel(t: number, channel: number) { 'worklet'; return sampleChannel(t, POSES, channel); }

function imagePlane(t: number, width: number, height: number) {
  'worklet';
  const a = poseChannel(t, 1), g = poseChannel(t, 2), h = poseChannel(t, 3);
  const y = poseChannel(t, 4), b = poseChannel(t, 5), c = poseChannel(t, 6);
  const ux = 1 / (IMAGE_SIZE * BODY.width), u0 = -BODY.x / BODY.width - 0.5;
  const vy = 1 / (IMAGE_SIZE * BODY.height), v0 = -BODY.y / BODY.height;
  const denominator = 1 + g * u0 + h * v0;
  // Project a single textured plane. Its center divider stays upright while the
  // near/far edges change apparent height. No native-view snapshots or3D engine.
  return [
    width * (0.5 * g + a) * ux / denominator, height * b * ux / denominator, 0, g * ux / denominator,
    width * 0.5 * h * vy / denominator, height * c * vy / denominator, 0, h * vy / denominator,
    0, 0, 1, 0,
    width * ((0.5 * g + a) * u0 + 0.5 * h * v0 + 0.5) / denominator,
    height * (y + b * u0 + c * v0) / denominator, 0, 1,
  ];
}

function segment(t: number, start: number, end: number) {
  'worklet';
  return Math.max(0, Math.min(1, (t - start) / (end - start)));
}

function Reveal({ clock, start, reduced, children, distance = 76 }: { clock: SharedValue<number>; start: number; reduced: boolean; children: React.ReactNode; distance?: number }) {
  const style = useAnimatedStyle(() => {
    const t = clock.get();
    const opacityStart = start === 2.25 ? 2.15 : start === 2 ? 1.8 : start;
    const progress = reduced ? 1 : EASE(segment(t, opacityStart, opacityStart + 0.75));
    const returnProgress = reduced ? 0 : EASE(segment(t, 7.2, 7.55));
    const remaining = start === 1.75 ? sampleChannel(t, HEADING_LIFT, 1) / 0.32 : start === 2 ? sampleChannel(t - 0.1, HEADING_LIFT, 1) / 0.32 : start === 2.25 ? sampleChannel(t, BUTTON_LIFT, 1) / 0.25 : 1 - progress;
    // Copy materialises out of a soft focus and leaves upward into one, never as a hard fade.
    const blur = reduced ? 0 : Math.max(0, 1 - progress) * 6 + returnProgress * 4;
    return { opacity: progress * (1 - returnProgress), filter: `blur(${blur.toFixed(2)}px)`, transform: [{ translateY: reduced ? 0 : distance * remaining - returnProgress * 10 }] };
  });
  return <Animated.View style={style}>{children}</Animated.View>;
}

export function DaylightIconReveal({ autoPlay = false, previewTime, onContinue }: { autoPlay?: boolean; previewTime?: number; onContinue?: () => void }) {
  const { width, height, fontScale } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useMotionPreference();
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  useEffect(() => { const subscription = AppState.addEventListener('change', value => setForeground(value === 'active')); return () => subscription.remove(); }, []);
  const clock = useSharedValue(previewTime === undefined ? reduced || !autoPlay ? 4 : 0 : previewPhase(previewTime));
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const holding = useRef(false);
  const [began, setBegan] = useState(false);
  const [replayEpoch, setReplayEpoch] = useState(0);
  const [ready, setReady] = useState(reduced || !autoPlay);
  const compact = fontScale > 1.4;
  const heroHeight = Math.max(compact ? 240 : 320, height * (compact ? 0.38 : 0.57));
  const artWidth = width * (compact ? 0.8 : 1), artHeight = height * (compact ? 0.8 : 1);
  // Where the settled icon's centre lands (top 0.242 + half its 0.232 height).
  const iconCenterY = compact ? heroHeight / 2 : height * 0.358;
  const play = useCallback(() => {
    if (!autoPlay || reduced || !foreground || holding.current || began || previewTime !== undefined) return;
    clock.set(0);
    clock.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear }), -1, false));
  }, [autoPlay, began, clock, foreground, previewTime, reduced]);
  useEffect(() => {
    if (previewTime !== undefined) { cancelAnimation(clock); clock.set(previewPhase(previewTime)); }
    else if (!autoPlay && replayEpoch > 0 && !began && !reduced && foreground) { clock.set(0); clock.set(withTiming(4, { duration: 4000, easing: Easing.linear })); }
    else play();
    return () => { cancelAnimation(clock); if (timer.current) clearTimeout(timer.current); };
  }, [autoPlay, began, clock, foreground, play, previewTime, reduced, replayEpoch]);
  useAnimatedReaction(() => reduced || (clock.get() >= 2.25 && clock.get() < 7.2), (next, previous) => { if (next !== previous) scheduleOnRN(setReady, next); }, [reduced]);
  const iconStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const artTime = reduced ? 4 : Math.min(t, 4);
    return {
      opacity: reduced ? 1 : poseChannel(artTime, 7),
      transform: [{ matrix: imagePlane(artTime, artWidth, artHeight) }],
    };
  });
  // The loop closes by letting the settled icon recede: 4% smaller, out of focus, gone by 8.15 s.
  const recede = useAnimatedStyle(() => {
    const p = reduced ? 0 : EASE(segment(clock.get(), 7.45, 8.15));
    return { opacity: 1 - p, filter: `blur(${(p * 8).toFixed(2)}px)`, transform: [{ scale: 1 - 0.04 * p }] };
  });
  // One sweep of light crosses the icon as it comes to rest, the way a lens catches the sun.
  const glint = useAnimatedStyle(() => {
    const p = segment(clock.get(), 2.5, 3.4);
    return { opacity: reduced ? 0 : Math.sin(p * Math.PI), transform: [{ translateX: -110 + SWEEP(p) * 400 }, { rotate: '18deg' }] };
  });
  // Day on the left, night on the right: the icon's two lights spill faintly onto the page.
  const glow = useAnimatedStyle(() => {
    const t = clock.get();
    return { opacity: reduced ? 0.55 : 0.55 * segment(t, 1.4, 2.6) * (1 - segment(t, 7.45, 8.1)) };
  });
  const landed = useRef(false);
  const tapLanding = useCallback(() => { if (landed.current) return; landed.current = true; void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft); }, []);
  useAnimatedReaction(() => clock.get() >= 2.45 && clock.get() < 2.7, (now, before) => { if (now && !before && !autoPlay) scheduleOnRN(tapLanding); }, [autoPlay]);
  const begin = () => { holding.current = true; if (timer.current) clearTimeout(timer.current); cancelAnimation(clock); };
  const end = () => {
    holding.current = false;
    if (timer.current) clearTimeout(timer.current);
    if (previewTime !== undefined) return;
    timer.current = setTimeout(() => {
      if (!began && autoPlay && !reduced && !holding.current) {
        // Finish settling before starting a fresh explanatory sequence.
        clock.set(withTiming(4, { duration: 240, easing: EASE }));
        timer.current = setTimeout(play, 650);
      }
    }, 2000);
  };
  const continueAction = () => {
    if (timer.current) clearTimeout(timer.current);
    cancelAnimation(clock);
    void Haptics.selectionAsync();
    if (began) { landed.current = false; setBegan(false); setReplayEpoch(value => value + 1); if (reduced) clock.set(4); }
    else { setBegan(true); clock.set(4); onContinue?.(); }
  };
  return <View style={styles.screen}><StatusBar style="dark" />
    <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edgeSize={28} enabled={fontScale > 1.4}><ScrollView bounces={false} contentContainerStyle={{ minHeight: height, paddingBottom: Math.max(safe.bottom, 18) }}>
      <View style={[styles.hero, { height: heroHeight }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: 0, top: 0, width, height: heroHeight }, glow]}>
          <Svg width={width} height={heroHeight}>
            <Defs>
              <RadialGradient id="daylight-day" cx="50%" cy="50%" r="50%"><Stop offset="0" stopColor="#FFD35A" stopOpacity={0.42} /><Stop offset="1" stopColor="#FFD35A" stopOpacity={0} /></RadialGradient>
              <RadialGradient id="daylight-night" cx="50%" cy="50%" r="50%"><Stop offset="0" stopColor="#5B4BE0" stopOpacity={0.34} /><Stop offset="1" stopColor="#5B4BE0" stopOpacity={0} /></RadialGradient>
            </Defs>
            <Ellipse cx={width * 0.36} cy={iconCenterY} rx={width * 0.42} ry={width * 0.36} fill="url(#daylight-day)" />
            <Ellipse cx={width * 0.64} cy={iconCenterY} rx={width * 0.42} ry={width * 0.36} fill="url(#daylight-night)" />
          </Svg>
        </Animated.View>
        <Animated.View style={[{ position: 'absolute', left: (width - artWidth) / 2, top: compact ? (heroHeight + safe.top) / 2 - artHeight * 0.35836 : 0, width: IMAGE_SIZE, height: IMAGE_SIZE, transformOrigin: 'top left' }, iconStyle]}>
          <Animated.View style={[{ width: IMAGE_SIZE, height: IMAGE_SIZE }, recede]}>
            <Image source={ART} style={{ width: IMAGE_SIZE, height: IMAGE_SIZE }} resizeMode="contain" />
            <View pointerEvents="none" style={[styles.glintClip, { left: IMAGE_SIZE * BODY.x, top: IMAGE_SIZE * BODY.y, width: IMAGE_SIZE * BODY.width, height: IMAGE_SIZE * BODY.height, borderRadius: IMAGE_SIZE * BODY.width * 0.235 }]}>
              <Animated.View style={[styles.glint, glint]}><LinearGradient colors={['#FFFFFF00', '#FFFFFF40', '#FFFFFFB8', '#FFFFFF40', '#FFFFFF00']} locations={[0, 0.3, 0.5, 0.7, 1]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} /></Animated.View>
            </View>
          </Animated.View>
        </Animated.View>
      </View>
      <View style={[styles.copy, { paddingHorizontal: width > 420 ? 40 : 25 }, compact ? { flex: 0, paddingTop: 16 } : { transform: [{ translateY: height * 0.032 }] }]}>
        <Reveal clock={clock} start={1.75} reduced={reduced} distance={height * 0.32}>
          <SwapText maxFontSizeMultiplier={1.6} style={styles.title} accessibilityRole="header" reduced={reduced} text={began ? 'Make room for\nyour rhythm.' : 'Find your energy.\nFollow your rhythm.'} />
        </Reveal>
        <Reveal clock={clock} start={2} reduced={reduced} distance={height * 0.29}>
          <SwapText style={styles.subtitle} reduced={reduced} delay={50} text={began ? 'Daylight is ready. Begin with a quiet moment and notice how you feel.' : 'Meet Daylight, a gentle guide to the daily patterns that help you feel your best.'} />
        </Reveal>
        <Reveal clock={clock} start={2.25} reduced={reduced} distance={height * 0.25}>
          <PressScale accessibilityRole="button" accessibilityState={{ disabled: !ready }} disabled={!ready} reduced={reduced} onPressIn={begin} onPressOut={end} onPress={continueAction} style={styles.cta}>
            <Text style={styles.ctaText}>{began ? 'Replay welcome' : 'Continue'}</Text>
          </PressScale>
        </Reveal>
        <Reveal clock={clock} start={3.25} reduced={reduced} distance={8}>
          <SwapText style={styles.note} reduced={reduced} delay={100} text={began ? 'A fresh start, whenever you need one.' : 'By continuing you agree to the Terms and Conditions and confirm that you have read and understood the Privacy Notice. ›'} />
        </Reveal>
      </View>
    </ScrollView></ProgressiveBlurViewport>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f0eff5', overflow: 'hidden' }, hero: { justifyContent: 'center', alignItems: 'center' },
  copy: { flex: 1, justifyContent: 'center', paddingBottom: 12, paddingTop: 26 },
  title: { color: '#181820', fontSize: 31, lineHeight: 36, letterSpacing: -0.65, fontWeight: '700', textAlign: 'center' },
  subtitle: { color: '#8E8C96', fontSize: 15, lineHeight: 20, textAlign: 'center', marginTop: 10, alignSelf: 'center', maxWidth: 300 },
  glintClip: { position: 'absolute', overflow: 'hidden' }, glint: { position: 'absolute', top: -60, bottom: -60, width: 96 },
  cta: { backgroundColor: '#503ad4', boxShadow: '0px 1px 0px rgba(255,255,255,0.18) inset, 0px 12px 22px -12px rgba(80,58,212,0.75)', minHeight: 48, borderRadius: 16, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 25, paddingVertical: 13, marginTop: 23, width: '66%', maxWidth: 300, alignSelf: 'center' },
  ctaText: { color: '#fff', fontSize: 16, fontWeight: '600', lineHeight: 22, textAlign: 'center' },
  note: { color: '#9A98A2', fontSize: 9.5, lineHeight: 12.5, textAlign: 'center', marginTop: 14, marginHorizontal: 56 },
});
