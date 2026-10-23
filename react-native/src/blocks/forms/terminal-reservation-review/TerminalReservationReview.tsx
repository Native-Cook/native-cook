import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { AccessibilityInfo, Image, Pressable, StyleSheet, Text, useWindowDimensions, View, type ViewProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import Svg, { Ellipse, G, Image as SvgImage, Path } from 'react-native-svg';

export const LOOP = 8;
const AnimatedPath = Animated.createAnimatedComponent(Path);
const AnimatedG = Animated.createAnimatedComponent(G);
const AnimatedView = Animated.createAnimatedComponent(View);
function ease(value: number) { 'worklet'; const p = Math.max(0, Math.min(1, value)); return p * p * (3 - 2 * p); }
function phase(t: number, a: number, b: number) { 'worklet'; return ease((t - a) / (b - a)); }
function out(t: number, a: number, b: number) { 'worklet'; const p = Math.max(0, Math.min(1, (t - a) / (b - a))); return 1 - (1 - p) ** 3; }
function pose(x: number, y: number, scale: number, roll: number, dx = 0, dy = 0) {
  'worklet';
  const angle = roll * Math.PI / 180, a = Math.cos(angle) * scale, b = Math.sin(angle) * scale;
  return [a, b, -b, a, x + dx - a * x + b * y, y + dy - b * x - a * y];
}
const CARD_FEED = [
  [0.99, -0.9303293],
  [1.071, -0.545208],
  [1.143, -0.2256194],
  [1.214, 0.0167858],
  [1.286, 0.3133213],
  [1.357, 0.510619],
  [1.429, 0.751616],
  [1.5, 0.969007],
  [1.571, 1],
];
const CARD_EJECT = [
  [5.179, 0],
  [5.214, 0.1080205],
  [5.25, 0.2542007],
  [5.286, 0.6051038],
  [5.321, 0.7773739],
  [5.42, 1.5710126],
];
function cardSample(t: number, keys: number[][]) {
  'worklet';
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    if (t > keys[i][0]) continue;
    const a = keys[i - 1], b = keys[i], p = (t - a[0]) / (b[0] - a[0]);
    return a[1] + (b[1] - a[1]) * p;
  }
  return keys[keys.length - 1][1];
}
const RING_KEYS = [
  [1.5, 259, 190],
  [1.625, 214, 190],
  [1.678571, 194.71, 190],
  [1.714286, 181.86, 190],
  [1.75, 169, 194],
  [1.875, 120, 197],
  [2, 78, 197],
  [2.125, 44, 193],
  [2.25, -11, 200],
  [2.375, -54, 187],
  [2.5, -97, 183],
  [2.625, -142, 186],
  [2.75, -188, 196],
  [2.875, -237, 199],
  [3, -292, 211],
  [3.125, -313, 187],
  [3.25, -368, 200],
  [3.375, -411, 182],
  [3.5, -457, 183],
  [3.571429, -481, 187],
  [3.642857, -504, 194],
  [3.714286, -529, 189],
  [3.785714, -563, 193],
  [3.857143, -629, 210],
  [3.928571, -664, 212],
  [4, -728, 256],
  [4.071429, -773, 274],
  [4.142857, -803, 283],
  [4.214286, -834, 309],
  [4.285714, -859, 332],
  [4.357143, -874, 345],
  [4.392857, -874, 345],
  [4.428571, -874, 360],
  [4.464286, -874, 360],
  [4.5, -874, 360],
];
function ringPose(t: number): [number, number] {
  'worklet';
  if (t <= RING_KEYS[0][0]) return [RING_KEYS[0][1] + (RING_KEYS[0][0] - t) * 360, RING_KEYS[0][2]];
  for (let i = 1; i < RING_KEYS.length; i++) {
    if (t > RING_KEYS[i][0]) continue;
    const a = RING_KEYS[i - 1], b = RING_KEYS[i], p = (t - a[0]) / (b[0] - a[0]);
    return [a[1] + (b[1] - a[1]) * p, a[2] + (b[2] - a[2]) * p];
  }
  const last = RING_KEYS[RING_KEYS.length - 1]; return [last[1], last[2]];
}
function ringPath(t: number) {
  'worklet';
  const [startDegrees, sweepDegrees] = ringPose(t);
  const start = startDegrees * Math.PI / 180, sweep = sweepDegrees * Math.PI / 180;
  let result = '';
  for (let i = 0; i <= 72; i++) {
    const angle = start + sweep * i / 72;
    result += `${i ? 'L' : 'M'}${(42 + 28.5 * Math.cos(angle)).toFixed(3)},${(37 + 28.5 * Math.sin(angle)).toFixed(3)}`;
  }
  return result;
}
function handsPath(t: number) {
  'worklet';
  const length = out(t, 4.40, 4.65) * 28.85, first = Math.min(1, length / 16.33), second = Math.max(0, Math.min(1, (length - 16.33) / 12.52));
  return `M31.22 24.73 L${(31.22 + 10.78 * first).toFixed(3)} ${(24.73 + 12.27 * first).toFixed(3)}${length > 16.33 ? ` L${(42 + 9.48 * second).toFixed(3)} ${(37 - 8.18 * second).toFixed(3)}` : ''}`;
}
function impact() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

/** Separate generated alpha layers keep the same source card feed and flower follow-through. */
function TerminalArt({ time }: { time: SharedValue<number> }) {
  const vase = useAnimatedProps(() => {
    const p = out(time.get(), 0.94, 1.13);
    return { opacity: p, matrix: pose(168, 432, 0.10 + 0.90 * p, 0, 0, (1 - p) * 8) };
  });
  const flowers = useAnimatedProps(() => {
    const t = time.get(), age = Math.max(0, t - 0.97), sway = t < 1.80 ? Math.sin(age * 24) * Math.exp(-age * 6) * 18 : 0;
    return { opacity: 1, matrix: pose(168, 373, 1, sway) };
  });
  const reader = useAnimatedProps(() => {
    const p = out(time.get(), 0.87, 1.08);
    return { opacity: p, matrix: pose(213, 443, 0.08 + 0.92 * p, -28 * (1 - p), -8 * (1 - p), 20 * (1 - p)) };
  });
  const paper = useAnimatedProps(() => {
    const t = time.get(), feed = cardSample(t, CARD_FEED), eject = cardSample(t, CARD_EJECT);
    // Translate inside the mouth-centered plane so its centerline stays on the slot.
    const travel = 95 * (1 - feed) + 110 * eject;
    const matrix = pose(192, 471, 1.22 - 0.22 * feed + 0.05 * eject, -18 * (1 - feed) - 12 * eject);
    matrix[4] += -matrix[0] * travel + matrix[2] * travel * 34 / 42;
    matrix[5] += -matrix[1] * travel + matrix[3] * travel * 34 / 42;
    return { opacity: phase(t, 0.98, 1.04) * (1 - phase(t, 5.34, 5.48)), matrix };
  });
  const shadow = useAnimatedProps(() => ({ opacity: Math.max(out(time.get(), 0.87, 1.08), out(time.get(), 0.94, 1.13)) }));
  const arc = useAnimatedProps(() => ({ d: ringPath(time.get()), opacity: phase(time.get(), 1.25, 1.75) * (1 - phase(time.get(), 5.214, 5.232) * (0.96 + 0.04 * phase(time.get(), 5.232, 5.304))) }));
  const hands = useAnimatedProps(() => ({ d: handsPath(time.get()), opacity: phase(time.get(), 1.25, 1.75) * (1 - phase(time.get(), 5.214, 5.232) * (0.96 + 0.04 * phase(time.get(), 5.232, 5.304))) }));
  return <Svg width={393} height={852} viewBox="0 0 393 852" accessible={false}>
    <AnimatedG animatedProps={shadow}>{[0, 1, 2, 3, 4, 5].map(i => <Ellipse key={i} cx={187.5} cy={435.5} rx={70.5 + i * 2} ry={11.5 + i * 1.5} fill="#000" opacity={0.007} />)}</AnimatedG>
    <AnimatedG animatedProps={vase}>
      <AnimatedG animatedProps={flowers}><SvgImage x={134} y={308} width={70} height={125} href={require('./assets/flowers.png')} preserveAspectRatio="none" /></AnimatedG>
      <SvgImage x={134} y={308} width={70} height={125} href={require('./assets/vase.png')} preserveAspectRatio="none" />
    </AnimatedG>
    <AnimatedG animatedProps={paper}><G transform="matrix(.14 -.113333333333 .194736842105 .089473684211 148.3 482.9)"><SvgImage width={300} height={190} href={require('./assets/card.png')} preserveAspectRatio="none" /></G></AnimatedG>
    <AnimatedG animatedProps={reader}>
      <SvgImage x={155} y={385} width={121} height={105} href={require('./assets/terminal.png')} preserveAspectRatio="none" />
      <G transform="matrix(.66 .30 -.58 .47 204 397)">
        <AnimatedPath animatedProps={arc} fill="none" stroke="#ED2868" strokeWidth={5} strokeOpacity={0.26} strokeLinecap="round" />
        <AnimatedPath animatedProps={arc} fill="none" stroke="#ED2868" strokeWidth={2.8} strokeLinecap="round" />
        <AnimatedPath animatedProps={hands} fill="none" stroke="#ED2868" strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" />
      </G>
    </AnimatedG>
  </Svg>;
}

function RoomArt() {
  return <Image source={require('./assets/room.jpg')} style={{ width: 271, height: 254 }} resizeMode="cover" accessible={false} />;
}

function Label({ children, x, y, width, size = 14, weight = '400', color = '#1F1F1F', center = false }: { children: string; x: number; y: number; width: number; size?: number; weight?: '400' | '500' | '600'; color?: string; center?: boolean }) {
  return <Text allowFontScaling={false} style={{ position: 'absolute', left: x, top: y, width, color, fontSize: size, fontWeight: weight, textAlign: center ? 'center' : 'left', lineHeight: size * 1.19 }}>{children}</Text>;
}

type Props = { autoPlay?: boolean; renderTime?: number };
/** Local-only checkout: no network request, financial transaction or audio. */
export function TerminalReservationReview({ autoPlay = false, renderTime }: Props) {
  const { width, height } = useWindowDimensions(), scale = Math.min(width / 393, height / 852);
  const initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced), [insurance, setInsurance] = useState(false), [interactive, setInteractive] = useState(false);
  const now = useSharedValue(0), epoch = useSharedValue(0);
  useEffect(() => { const stamp = Date.now() / 1000; now.set(stamp); epoch.set(stamp); }, [epoch, now]);
  const lastTouch = useSharedValue(-1), manualTime = useSharedValue(0), dragging = useSharedValue(false), anchor = useSharedValue(0), replay = useSharedValue(false);
  const clock = useDerivedValue(() => {
    if (reduced) return lastTouch.get() < 0 ? 6.5 : replay.get() ? 6.5 : manualTime.get();
    if (renderTime !== undefined) return ((renderTime % LOOP) + LOOP) % LOOP;
    if (lastTouch.get() >= 0) {
      const age = now.get() - lastTouch.get();
      if (dragging.get() || age < 2) return replay.get() ? 1 + 5.5 * ease(age / 1.8) : manualTime.get();
      if (age < 2.6) return (replay.get() ? 6.5 : manualTime.get()) * (1 - ease((age - 2) / 0.6));
      return autoPlay ? (age - 2.6) % LOOP : 0;
    }
    return autoPlay ? Math.max(0, now.get() - epoch.get()) % LOOP : 0;
  });
  const ticker = useFrameCallback(() => {
    const stamp = Date.now() / 1000;
    now.set(stamp);
    if (!dragging.get() && lastTouch.get() >= 0 && stamp - lastTouch.get() >= 2.6) {
      epoch.set(lastTouch.get() + 2.6); lastTouch.set(-1); replay.set(false);
    }
  }, false);
  useAnimatedReaction(() => lastTouch.get() >= 0 || dragging.get(), (next, previous) => { if (next !== previous) scheduleOnRN(setInteractive, next); });
  useEffect(() => { ticker.setActive(!reduced && renderTime === undefined && (autoPlay || interactive)); return () => ticker.setActive(false); }, [autoPlay, interactive, reduced, renderTime, ticker]);
  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted) setReduced(value); });
    const listener = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => { mounted = false; listener.remove(); };
  }, []);
  function touch(value: number, play = false) { lastTouch.set(Date.now() / 1000); manualTime.set(value); replay.set(play); now.set(Date.now() / 1000); impact(); }
  const beginDrag = useCallback(() => {
    'worklet';
    if (renderTime !== undefined || reduced) return;
    const captured = clock.get(); anchor.set(captured); manualTime.set(captured); replay.set(false); dragging.set(true); lastTouch.set(Date.now() / 1000);
  }, [anchor, clock, dragging, lastTouch, manualTime, reduced, renderTime, replay]);
  const updateDrag = useCallback((event: { translationX: number }) => {
    'worklet';
    if (renderTime !== undefined || reduced) return;
    manualTime.set(Math.max(0, Math.min(6.8, anchor.get() + event.translationX / scale / 45))); lastTouch.set(Date.now() / 1000);
  }, [anchor, lastTouch, manualTime, reduced, renderTime, scale]);
  const finishDrag = useCallback((event: { velocityX: number }) => {
    'worklet';
    if (renderTime !== undefined || reduced || !dragging.get()) return;
    manualTime.set(Math.max(0, Math.min(6.8, manualTime.get() + Math.max(-0.4, Math.min(0.4, event.velocityX / 2500))))); dragging.set(false); lastTouch.set(Date.now() / 1000); scheduleOnRN(impact);
  }, [dragging, lastTouch, manualTime, reduced, renderTime]);
  const gesture = Gesture.Pan().minDistance(12).onStart(beginDrag).onUpdate(updateDrag).onFinalize(finishDrag);
  const checkoutStyle = useAnimatedStyle(() => ({ opacity: 1 - out(clock.get(), 0.70, 1.00) * (1 - phase(clock.get(), 7.05, 7.6)), transform: [{ translateX: -78 * out(clock.get(), 0.70, 1.00) * (1 - phase(clock.get(), 7.05, 7.6)) }] }));
  const checkoutTint = useAnimatedStyle(() => ({ opacity: out(clock.get(), 0.70, 1.00) * 0.10 * (1 - phase(clock.get(), 7.05, 7.6)) }));
  const reviewStyle = useAnimatedStyle(() => ({ opacity: (1 - phase(clock.get(), 5.25, 5.60)) * (1 - phase(clock.get(), 7.05, 7.6)), transform: [{ translateX: (1 - out(clock.get(), 0.70, 1.00)) * 393 }] }));
  const confirmationOpacity = useAnimatedStyle(() => ({ opacity: phase(clock.get(), 5.25, 5.60) * (1 - phase(clock.get(), 7.05, 7.6)) }));
  const confirmationStyle = useAnimatedStyle(() => ({ opacity: 1, backgroundColor: `rgba(249,249,245,${phase(clock.get(), 5.25, 5.60) * (1 - phase(clock.get(), 7.05, 7.6))})`, transform: [{ translateY: (1 - phase(clock.get(), 5.25, 5.60)) * 18 }] }));
  const reviewLabel = useAnimatedStyle(() => ({ opacity: 1 - phase(clock.get(), 4.30, 4.55) }));
  const checkoutProps = useAnimatedProps<ViewProps>(() => { const hidden = out(clock.get(), 0.70, 1.00) * (1 - phase(clock.get(), 7.05, 7.6)) >= 0.5; return { pointerEvents: hidden ? 'none' : 'auto', accessibilityElementsHidden: hidden, importantForAccessibility: hidden ? 'no-hide-descendants' : 'auto' }; });
  const confirmationProps = useAnimatedProps<ViewProps>(() => { const hidden = phase(clock.get(), 5.25, 5.60) * (1 - phase(clock.get(), 7.05, 7.6)) <= 0.5; return { pointerEvents: hidden ? 'none' : 'auto', accessibilityElementsHidden: hidden, importantForAccessibility: hidden ? 'no-hide-descendants' : 'auto' }; });
  return <View style={{ flex: 1, backgroundColor: '#FFFFFF' }}><StatusBar style="dark" />
    <GestureDetector gesture={gesture}><View style={{ position: 'absolute', left: (width - 393 * scale) / 2, top: (height - 852 * scale) / 2, width: 393, height: 852, transform: [{ scale }], transformOrigin: 'top left', overflow: 'hidden' }}>
      <AnimatedView needsOffscreenAlphaCompositing animatedProps={checkoutProps} style={[styles.scene, checkoutStyle]}>
        <Label x={50} y={87} width={293} size={17} weight="600" center>Confirm and pay</Label>
        <Label x={354} y={87} width={24} size={21}>×</Label>
        <Label x={25} y={133} width={330} size={12} color="#808080">{'$1,530.00 now, $2,172.60 charged on May\n21. No extra fees. More info'}</Label>
        <Label x={24} y={204} width={345} size={15} weight="600">Payment method</Label>
        <View style={{ position: 'absolute', left: 24, top: 234, width: 345, height: 50, borderWidth: 1, borderColor: '#E8E8E8', borderRadius: 8 }} />
        <Label x={43} y={252} width={280} size={13} weight="600">CARD  0636</Label><Label x={346} y={244} width={20} size={22} color="#808080">›</Label>
        <Label x={30} y={303} width={330} size={11} color="#808080">▰  ▰   Other ways to pay</Label>
        <View style={{ position: 'absolute', left: 24, top: 332, width: 345, height: 1, backgroundColor: '#F0F0F0' }} />
        <Label x={24} y={356} width={330} size={15} weight="600">Add travel insurance?</Label>
        <Label x={39} y={400} width={280} size={13}>{'Yes, add peace of mind for $112.42.\nUp to 100% back for covered reasons.'}</Label>
        <Pressable accessibilityRole="checkbox" accessibilityLabel="Add travel insurance" accessibilityState={{ checked: insurance }} onPress={() => { setInsurance(!insurance); touch(0); }} style={{ position: 'absolute', left: 323, top: 395, width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}><Text style={{ fontSize: 24, color: '#A6A6A6' }}>{insurance ? '☑' : '□'}</Text></Pressable>
        <Label x={39} y={451} width={310} size={11} color="#808080">{'Get reimbursed if you cancel due to illness, flight\ndelays, and more. Plus, get assistance services\nfor emergency help. Terms apply.'}</Label>
        <Label x={24} y={531} width={330} size={15} weight="600">Price details</Label>
        {[['6 nights', '$3,060.00'], ['Taxes', '$642.60'], ['Total USD', '$3,702.60']].map((pair, i) => <View key={pair[0]}><Label x={24} y={565 + i * 32} width={200} weight={i === 2 ? '600' : '400'}>{pair[0]}</Label><Label x={264} y={565 + i * 32} width={105}>{pair[1]}</Label></View>)}
        <Label x={24} y={679} width={330} size={12}>Price breakdown</Label>
        <Pressable accessibilityRole="button" onPress={() => touch(1, true)} style={[styles.action, { top: 745, backgroundColor: '#E60055' }]}><Text style={styles.actionText}>Confirm and pay</Text></Pressable>
        <Label x={24} y={806} width={345} size={10} color="#808080" center>By selecting the button, I agree to the booking terms.</Label>
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: '#000000' }, checkoutTint]} />
      </AnimatedView>
      <Animated.View needsOffscreenAlphaCompositing pointerEvents="none" accessibilityElementsHidden style={[styles.scene, reviewStyle]}>
        <TerminalArt time={clock} />
        <Animated.View style={[StyleSheet.absoluteFill, reviewLabel]}><Label x={60} y={535} width={273} size={26} weight="500" color="#000000" center>{'Reviewing\npayment details'}</Label></Animated.View>
      </Animated.View>
      <AnimatedView animatedProps={confirmationProps} style={[styles.scene, { backgroundColor: '#F9F9F5' }, confirmationStyle]}>
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, confirmationOpacity]}><Label x={45} y={110} width={303} size={25} weight="600" color="#000000" center>{'Your reservation is\nconfirmed!'}</Label></Animated.View>
        <Animated.View style={[confirmationOpacity, { position: 'absolute', left: 49, top: 225, width: 295, height: 411, backgroundColor: 'white', borderRadius: 17, boxShadow: '0 2px 2px #0000000F, 0 18px 20px #0000001A' }]} />
        <Animated.View style={[confirmationOpacity, { position: 'absolute', left: 61, top: 237, width: 271, height: 254, borderRadius: 8, overflow: 'hidden' }]}><RoomArt /></Animated.View>
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, confirmationOpacity]}><Label x={65} y={511} width={263} size={16} weight="500">Fern House Art Deco</Label><Label x={65} y={536} width={263} size={12} color="#808080">May 5–11</Label>
        <Label x={65} y={582} width={263} size={12} color="#808080">{'1 guest\n$3,702.60 total'}</Label></Animated.View>
        <Animated.View pointerEvents="box-none" style={[StyleSheet.absoluteFill, confirmationOpacity]}><Pressable accessibilityRole="button" onPress={() => touch(0)} style={[styles.action, { top: 768, backgroundColor: '#141414' }]}><Text style={styles.actionText}>Done</Text></Pressable></Animated.View>
      </AnimatedView>
    </View></GestureDetector>
  </View>;
}
const styles = StyleSheet.create({ scene: { position: 'absolute', left: 0, top: 0, width: 393, height: 852, backgroundColor: '#FFFFFF' }, action: { position: 'absolute', left: 24, width: 345, height: 48, borderRadius: 7, alignItems: 'center', justifyContent: 'center' }, actionText: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' } });
