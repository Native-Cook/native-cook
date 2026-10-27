import { StatusBar } from 'expo-status-bar';
import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { useMotionPreference } from '@/shared/useMotionPreference';
import { PressScale } from '@/shared/PressScale';
import { SwapText } from '@/shared/SwapText';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Pressable, ScrollView } from 'react-native-gesture-handler';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Animated, { cancelAnimation, Easing, type SharedValue, useAnimatedProps, useAnimatedStyle, useDerivedValue, useSharedValue, withRepeat, withSpring, withTiming } from 'react-native-reanimated';
import Svg, { G, Path } from 'react-native-svg';
import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';
import { BODY_FOOT, BODY_INK_FLAT, BODY_LIT, BODY_LIT_FLAT, DEPTH, FACE_GLINT, FACE_PATH, FRONT, GLYPHS, RING_PATH, type GlyphPart, type Operation } from './keyGeometry';

export const LOOP = 6.1;
function previewPhase(t: number) { if (!Number.isFinite(t)) return 0; const phase = t % LOOP; return phase < 0 ? phase + LOOP : phase; }
const EASE = Easing.bezier(0.23, 1, 0.32, 1);
const CREAM = '#eee8dc';
const PRESSES: { symbol: Operation; down: number; release: number }[] = [
  { symbol: '÷', down: 10 / 60, release: 40 / 60 },
  { symbol: '+', down: 70 / 60, release: 100 / 60 },
  { symbol: '×', down: 132 / 60, release: 160 / 60 },
  { symbol: '=', down: 191 / 60, release: 220 / 60 },
  { symbol: '÷', down: 251 / 60, release: 280 / 60 },
  { symbol: '+', down: 311 / 60, release: 340 / 60 },
];
const INK = '#151412';
const OUTLINE = '#1D1B18';
type Key = { symbol: Operation; face: string; wall: string; glyphWall: string; label: string; centerX: number; top: number };
const KEYS: Key[] = [
  { symbol: '−', face: '#F7EED9', wall: '#E8D2A4', glyphWall: '#E8D2A4', label: 'Subtract', centerX: 0.5, top: -0.024 },
  { symbol: '÷', face: '#6F9CF2', wall: '#F2C46F', glyphWall: '#F2C46F', label: 'Divide', centerX: 0.928, top: 0.092 },
  { symbol: '+', face: '#FFC694', wall: '#FA9441', glyphWall: '#F89A4B', label: 'Add', centerX: 0.5, top: 0.2099 },
  { symbol: '×', face: '#F45A45', wall: '#F2C46F', glyphWall: '#C13A28', label: 'Multiply', centerX: 0.074, top: 0.321 },
  { symbol: '=', face: '#FFF6DA', wall: '#F2C46F', glyphWall: '#F2C46F', label: 'Equals', centerX: 0.505, top: 0.4403 },
];
const AnimatedPath = Animated.createAnimatedComponent(Path);
// Local copies: worklets capture module constants, not imported bindings.
const WALL = DEPTH;
const LIT_CHAIN = BODY_LIT_FLAT;
const INK_CHAIN = BODY_INK_FLAT;
const TRAVEL = WALL - 0.6;
// Two pinstripes catch the light just left of the front corner, as on the reference keys.
const STRIPES = [4.5, 9].map(offset => {
  const x = FRONT[0] - offset;
  let y = FRONT[1];
  for (let i = 1; i < BODY_LIT.length; i++) {
    const [x0, y0] = BODY_LIT[i - 1], [x1, y1] = BODY_LIT[i];
    if ((x0 - x) * (x1 - x) <= 0 && x0 !== x1) { y = y0 + (y1 - y0) * (x - x0) / (x1 - x0); break; }
  }
  return [x, y] as const;
});

function chainWall(flat: readonly number[], top: number, bottom: number) {
  'worklet';
  let d = '';
  for (let i = 0; i < flat.length; i += 2) d += `${i ? 'L' : 'M'}${flat[i]} ${flat[i + 1] + top}`;
  for (let i = flat.length - 2; i >= 0; i -= 2) d += `L${flat[i]} ${flat[i + 1] + bottom}`;
  return `${d}Z`;
}

/** A raised symbol: walls first, then the tops drawn twice so overlapping bars read as one outline. */
function Glyph({ parts, face, wall }: { parts: GlyphPart[]; face: string; wall: string }) {
  return <G>
    {parts.map((p, i) => <G key={`w${i}`}>{p.lit.map((d, j) => <Path key={`l${j}`} d={d} fill={wall} stroke={OUTLINE} strokeWidth={1.3} strokeLinejoin="round" />)}{p.ink.map((d, j) => <Path key={`k${j}`} d={d} fill={INK} stroke={OUTLINE} strokeWidth={1.3} strokeLinejoin="round" />)}</G>)}
    {parts.map((p, i) => <Path key={`o${i}`} d={p.top} fill={OUTLINE} stroke={OUTLINE} strokeWidth={2.6} strokeLinejoin="round" />)}
    {parts.map((p, i) => <Path key={`t${i}`} d={p.top} fill={face} />)}
  </G>;
}

function KeyButton({ item, clock, reduced, sceneWidth, sceneHeight, scale, onBegin, onEnd, onSelect }: { item: Key; clock: SharedValue<number>; reduced: boolean; sceneWidth: number; sceneHeight: number; scale: number; onBegin: () => void; onEnd: () => void; onSelect: (value: Operation) => void }) {
  const press = useSharedValue(0);
  const pressure = useDerivedValue(() => {
    const t = clock.get();
    let demo = 0;
    for (const beat of PRESSES) {
      if (beat.symbol !== item.symbol || t < beat.down || t > beat.release + 0.3) continue;
      if (t < beat.down + 5 / 60) { const p = (t - beat.down) / (5 / 60); demo = p * p * (3 - 2 * p); }
      else if (t < beat.release) demo = 1;
      else { const elapsed = t - beat.release; demo = Math.max(0, Math.exp(-13 * elapsed) * Math.cos(7 * elapsed)); }
    }
    return reduced ? 0 : Math.min(1, Math.max(0, press.get(), demo));
  });
  const cap = useAnimatedStyle(() => ({ transform: [{ translateY: TRAVEL * pressure.get() * scale }] }));
  const lit = useAnimatedProps(() => ({ d: chainWall(LIT_CHAIN, TRAVEL * pressure.get(), WALL) }));
  const ink = useAnimatedProps(() => ({ d: chainWall(INK_CHAIN, TRAVEL * pressure.get(), WALL) }));
  const stripes = useAnimatedProps(() => {
    const top = TRAVEL * pressure.get();
    return { d: STRIPES.map(([x, y]) => `M${x} ${y + top}V${y + WALL}`).join(''), strokeOpacity: 1 - pressure.get() };
  });
  const parts = GLYPHS[item.symbol];
  const left = item.centerX * sceneWidth - 108 * scale, top = item.top * sceneHeight - 8 * scale;
  return <View pointerEvents="box-none" style={[styles.key, { left, top, width: 216 * scale, height: 150 * scale }]}>
    <Svg width={216 * scale} height={150 * scale} viewBox="0 0 216 150" style={StyleSheet.absoluteFill} pointerEvents="none">
      <Path d={RING_PATH} fill="none" stroke={OUTLINE} strokeWidth={1.1} />
      <AnimatedPath animatedProps={lit} fill={item.wall} />
      <AnimatedPath animatedProps={ink} fill={INK} />
      <AnimatedPath animatedProps={stripes} fill="none" stroke={INK} strokeWidth={1.8} />
      <Path d={BODY_FOOT} fill="none" stroke={OUTLINE} strokeWidth={1.4} strokeLinejoin="round" />
    </Svg>
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, cap]}>
      <Svg width={216 * scale} height={150 * scale} viewBox="0 0 216 150">
        <Path d={FACE_PATH} fill={item.face} stroke={OUTLINE} strokeWidth={1.4} strokeLinejoin="round" />
        <Path d={FACE_GLINT} fill="none" stroke="#FFFFFF" strokeOpacity={0.34} strokeWidth={1.5} strokeLinecap="round" />
        <Glyph parts={parts} face={item.face} wall={item.glyphWall} />
      </Svg>
    </Animated.View>
    {/* The hit area is the face's inscribed rectangle, so neighbouring plates never steal a press. */}
    <Pressable accessibilityRole="button" accessibilityLabel={item.label} onPressIn={() => { onBegin(); press.set(reduced ? 0 : withTiming(1, { duration: 110, easing: EASE })); void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid); }} onPressOut={() => { press.set(withSpring(0, { duration: 420, dampingRatio: 0.62 })); onEnd(); }} onPress={() => onSelect(item.symbol)} style={{ position: 'absolute', left: 50 * scale, top: 30 * scale, width: 116 * scale, height: 78 * scale }} />
  </View>;
}

const TOP_EDGE = ['top'] as const;
export function TactileCalculatorWelcome({ autoPlay = false, previewTime, onContinue }: { autoPlay?: boolean; previewTime?: number; onContinue?: () => void }) {
  const { width, height, fontScale } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useMotionPreference();
  // The reference sets its welcome in a wide grotesk; Inter matches its proportions. Copy waits
  // for the face so it never flashes in the narrower system font.
  const [fontsLoaded] = useFonts({ 'TactileInter-Regular': require('./assets/Inter-Regular.ttf'), 'TactileInter-Bold': require('./assets/Inter-Bold.ttf') });
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  useEffect(() => { const subscription = AppState.addEventListener('change', value => setForeground(value === 'active')); return () => subscription.remove(); }, []);
  const clock = useSharedValue(previewTime === undefined ? 0 : previewPhase(previewTime));
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const held = useRef(false);
  const [operation, setOperation] = useState<Operation | null>(null);
  const [started, setStarted] = useState(false);
  const compactScale = fontScale > 1.4 ? 0.7 : 1;
  const heroHeight = height * 0.625 * compactScale;
  const scale = Math.min(width * 0.6125 / 200, height * 0.1628 / 116, 1.6) * compactScale;
  const play = useCallback(() => {
    if (previewTime !== undefined) { cancelAnimation(clock); clock.set(previewPhase(previewTime)); return; }
    if (!autoPlay || reduced || !foreground || held.current || started) return;
    clock.set(0);
    clock.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear }), -1, false));
  }, [autoPlay, clock, foreground, previewTime, reduced, started]);
  useEffect(() => { play(); return () => { cancelAnimation(clock); if (timer.current) clearTimeout(timer.current); }; }, [clock, play]);
  const begin = useCallback(() => { held.current = true; if (timer.current) clearTimeout(timer.current); cancelAnimation(clock); clock.set(0); }, [clock]);
  const end = useCallback(() => { held.current = false; if (timer.current) clearTimeout(timer.current); if (previewTime !== undefined) { clock.set(previewPhase(previewTime)); return; } timer.current = setTimeout(play, 2000); }, [clock, play, previewTime]);
  const expression = operation === '+' ? '8 + 4 = 12' : operation === '−' ? '8 − 4 = 4' : operation === '×' ? '8 × 4 = 32' : operation === '÷' ? '8 ÷ 4 = 2' : operation === '=' ? '8 + 4 = 12' : 'Welcome to';
  return <View style={styles.screen}><StatusBar style="dark" />
    {/* The reference frosts the status-bar band: the top key slides under a progressive blur. */}
    <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edges={fontScale > 1.4 ? undefined : TOP_EDGE} edgeSize={fontScale > 1.4 ? 28 : 56} onlyWhenOverflow={fontScale > 1.4}><ScrollView contentContainerStyle={[styles.content, { minHeight: height, paddingBottom: Math.max(safe.bottom, height * 0.0614) }]} bounces={false}>
      <View style={{ height: heroHeight, width: '100%', overflow: 'hidden' }}>
        <View style={{ position: 'absolute', width, height: heroHeight, top: 0, left: 0 }}>
          {KEYS.map(item => <KeyButton key={item.symbol} item={item} clock={clock} reduced={reduced} sceneWidth={width} sceneHeight={height * compactScale} scale={scale} onBegin={begin} onEnd={end} onSelect={setOperation} />)}
        </View>
      </View>
      <View style={[styles.copy, { paddingTop: fontScale > 1.4 ? 28 : height * 0.068 }, fontScale > 1.4 && { flex: 0 }]} accessibilityLiveRegion="polite">
        {fontsLoaded && <SwapText text={started ? 'A little clarity, every day.' : expression} maxFontSizeMultiplier={1.6} style={[styles.welcome, operation && !started && styles.expression]} reduced={reduced} />}
        {fontsLoaded && <SwapText text={started ? 'It all adds up.' : 'Tally Calculator'} maxFontSizeMultiplier={1.5} style={styles.title} reduced={reduced} delay={40} />}
      </View>
      <PressScale accessibilityRole="button" accessibilityLabel={started ? 'Start again' : 'Continue'} reduced={reduced} onPressIn={begin} onPressOut={end} onPress={() => { setStarted(value => !value); if (!started) onContinue?.(); void Haptics.selectionAsync(); }} style={styles.cta}>
        {fontsLoaded && <Text maxFontSizeMultiplier={1.6} style={styles.ctaText}>{started ? 'Start again' : 'Continue'}</Text>}
      </PressScale>
    </ScrollView></ProgressiveBlurViewport>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: CREAM }, content: { alignItems: 'center' }, key: { position: 'absolute', width: 216, height: 150 },
  copy: { flex: 1, justifyContent: 'flex-start', alignItems: 'center', paddingHorizontal: 24, paddingBottom: 28, minHeight: 125 },
  welcome: { color: '#2A2725', fontFamily: 'TactileInter-Regular', fontSize: 36, lineHeight: 44, letterSpacing: -0.7, textAlign: 'center' }, expression: { fontVariant: ['tabular-nums'] }, title: { color: '#D9402D', fontFamily: 'TactileInter-Bold', fontSize: 36, lineHeight: 44, letterSpacing: -0.8, textAlign: 'center', marginTop: 11 },
  cta: { minHeight: 56, backgroundColor: '#2D2B2C', borderRadius: 28, marginHorizontal: 30, alignSelf: 'stretch', alignItems: 'center', justifyContent: 'center', paddingHorizontal: 20, paddingVertical: 14, marginTop: 10 },
  ctaText: { color: '#FFFFFF', fontFamily: 'TactileInter-Bold', fontSize: 21, lineHeight: 26, letterSpacing: -0.3 },
});
