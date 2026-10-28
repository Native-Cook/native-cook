import { StatusBar } from 'expo-status-bar';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedStyle, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle as SvgCircle, Ellipse as SvgEllipse, Path as SvgPath, Rect as SvgRect, Text as SvgText } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const LOOP = 8;
const INK = '#6A3D28';
const CREAM = '#FFF8E8';
const clamp = (value: number, low: number, high: number) => { 'worklet'; return Math.max(low, Math.min(high, value)); };
const smooth = (value: number) => { 'worklet'; const u = clamp(value, 0, 1); return u * u * (3 - 2 * u); };
const loopPose = (time: number) => { 'worklet'; return smooth((time - 1.5) / 1.7) * (1 - smooth((time - 4.7) / 1.8)); };

type Props = { autoPlay?: boolean; previewTime?: number };

function resetLampMotion(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet';
  const time = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  clock.value = time;
  pose.value = reduced ? 0 : previewTime == null ? 0 : loopPose(time);
  mode.value = 0;
}
function finishLampTouch(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2;
}
function beginLampDrag(pose: SharedValue<number>, origin: SharedValue<number>, mode: SharedValue<number>, hapticSent: SharedValue<boolean>) {
  'worklet'; origin.value = pose.value; mode.value = 1; hapticSent.value = false;
}
function updateLampDrag(pose: SharedValue<number>, origin: SharedValue<number>, translationX: number, hapticSent: SharedValue<boolean>, mode: SharedValue<number>) {
  'worklet';
  mode.value = 1;
  const current = clamp(origin.value + Math.abs(translationX) / 120, 0, 1);
  pose.value = current;
  const crossedThreshold = current > 0.8 && !hapticSent.value;
  if (crossedThreshold) hapticSent.value = true;
  return crossedThreshold;
}
function makeWishOnUI(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; pose.value = 1; finishLampTouch(pose, from, elapsed, mode);
}

export function LampWishCompanion({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [wished, setWished] = useState(false);
  const [manualActive, setManualActive] = useState(false);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760);
  const pose = useSharedValue(reduced ? 0 : previewTime == null ? 0 : loopPose(((previewTime % LOOP) + LOOP) % LOOP));
  const clock = useSharedValue(previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP);
  const mode = useSharedValue(0); // autoplay/rest, touch, return
  const dragOrigin = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnElapsed = useSharedValue(0);
  const thresholdHapticSent = useSharedValue(false);
  const clearManual = useCallback(() => setManualActive(false), []);
  const lightImpact = useCallback(() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const thresholdImpact = useCallback(() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', state => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const frame = useFrameCallback(info => {
    const dt = Math.max(0, (info.timeSincePreviousFrame ?? 0) / 1000);
    if (mode.value === 2) {
      returnElapsed.value += dt;
      if (returnElapsed.value < 2) pose.value = returnFrom.value;
      else {
        const t = (returnElapsed.value - 2) / 0.45;
        pose.value = returnFrom.value * (1 - smooth(t));
        if (t >= 1) {
          pose.value = 0; clock.value = 0; mode.value = 0;
          if (!autoPlay) scheduleOnRN(clearManual);
        }
      }
      return;
    }
    if (mode.value === 0 && !autoPlay) { scheduleOnRN(clearManual); return; }
    if (mode.value === 0 && autoPlay && previewTime == null) {
      clock.value = (clock.value + dt) % LOOP;
      pose.value = loopPose(clock.value);
    }
  }, false);

  useEffect(() => {
    scheduleOnUI(resetLampMotion, pose, clock, mode, previewTime, reduced);
  }, [active, autoPlay, clock, mode, pose, previewTime, reduced]);
  useEffect(() => {
    frame.setActive(active && !reduced && previewTime == null && (autoPlay || manualActive));
  }, [active, autoPlay, frame, manualActive, previewTime, reduced]);

  const beginTouch = useCallback(() => { setManualActive(true); }, []);
  const makeWish = useCallback(() => {
    setWished(true);
    if (!reduced && previewTime == null) {
      setManualActive(true);
      scheduleOnUI(makeWishOnUI, pose, returnFrom, returnElapsed, mode);
    }
    lightImpact();
  }, [lightImpact, mode, pose, previewTime, reduced, returnElapsed, returnFrom]);

  const lampDrag = useMemo(() => Gesture.Pan().minDistance(1)
    .onStart(() => {
      if (reduced || previewTime != null) return;
      beginLampDrag(pose, dragOrigin, mode, thresholdHapticSent);
      scheduleOnRN(beginTouch);
    })
    .onUpdate(event => {
      if (reduced || previewTime != null) return;
      if (updateLampDrag(pose, dragOrigin, event.translationX, thresholdHapticSent, mode)) {
        scheduleOnRN(thresholdImpact);
        scheduleOnRN(setWished, true);
      }
    })
    .onFinalize(() => {
      if (reduced || previewTime != null || mode.value !== 1) return;
      finishLampTouch(pose, returnFrom, returnElapsed, mode);
    }), [beginTouch, dragOrigin, mode, pose, previewTime, reduced, returnElapsed, returnFrom, thresholdHapticSent, thresholdImpact]);
  const buttonTap = useMemo(() => Gesture.Tap().onEnd((_event, success) => { if (success) scheduleOnRN(makeWish); }), [makeWish]);

  const catStyle = useAnimatedStyle(() => {
    const bounce = Math.sin(pose.value * Math.PI * 2);
    return { transform: [{ translateY: -30 * pose.value }, { rotateZ: `${-3 + pose.value * 6}deg` }, { scaleX: 1 + 0.09 * bounce }, { scaleY: 1 - 0.09 * bounce }] };
  });
  const lampStyle = useAnimatedStyle(() => ({ transform: [{ rotateZ: `${Math.sin(pose.value * Math.PI) * -8}deg` }] }));
  const sparkles = Array.from({ length: 8 }, (_, i) => <LampSparkle key={i} pose={pose} index={i} />);

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <View style={styles.safeArea} pointerEvents="none" />
      <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
        <View style={[styles.stage, { transform: [{ scale }] }]}>
          <Village />
          <View style={styles.header}>
            <Text style={styles.headerTitle}>DAILY LITTLE WONDER</Text>
            <Text style={styles.headerCount}>01 / 07</Text>
          </View>
          <Animated.View style={[styles.catHit, catStyle]}>
            <Cat pose={pose} />
          </Animated.View>
          <Text style={styles.headline}>A little wish,{`\n`}just for you.</Text>
          <Text style={styles.subtitle}>{wished ? 'A bright little moment is yours.' : 'Meet Miso. Every day holds a surprise.'}</Text>
          <GestureDetector gesture={lampDrag}>
            <Animated.View accessible accessibilityRole="button" accessibilityLabel="Wish lamp" accessibilityHint="Rub horizontally to make a wish" onAccessibilityTap={makeWish} style={[styles.lampHit, lampStyle]}>
              {sparkles}
              <Lamp />
            </Animated.View>
          </GestureDetector>
          <GestureDetector gesture={buttonTap}>
            <Animated.View accessible accessibilityRole="button" accessibilityLabel={wished ? 'Wish made' : 'Rub to make a wish'} onAccessibilityTap={makeWish} style={styles.cta}>
              <Text style={styles.ctaCopy}>✧  {wished ? 'Wish made' : 'Rub to make a wish'}</Text>
            </Animated.View>
          </GestureDetector>
          <Text style={styles.footer}>ONE SMALL MOMENT. ALL YOURS.</Text>
        </View>
      </View>
    </View>
  );
}

function Village() {
  return <Svg pointerEvents="none" width={393} height={300} viewBox="0 0 393 300" style={styles.village}>
    <SvgRect x="0" y="0" width="393" height="240" fill="#FAF6CC" />
    {Array.from({ length: 8 }, (_, i) => <SvgEllipse key={`tree-${i}`} cx={i * 59 + 21} cy={190 - (i % 3) * 15} rx="41" ry="50" fill="#A5C878" />)}
    <SvgRect x="32" y="88" width="95" height="146" rx="4" fill="#C98D4A" />
    <SvgRect x="42" y="104" width="75" height="45" fill="#A5DFD9" />
    <SvgRect x="28" y="156" width="104" height="17" fill="#F4C147" />
    {Array.from({ length: 5 }, (_, i) => <SvgRect key={`awning-${i}`} x={28 + i * 21} y="156" width="10" height="17" fill="#B87435" />)}
    <SvgText x="80" y="80" textAnchor="middle" fill="#805B65" fontSize="13" fontWeight="900">COFFEE</SvgText>
    <SvgRect x="282" y="44" width="62" height="190" rx="7" fill="#9DCED4" />
    {Array.from({ length: 8 }, (_, i) => <SvgRect key={`tower-${i}`} x="290" y={64 + i * 20} width="46" height="9" fill="#C3E5DC" />)}
    <SvgPath d="M270 48 L313 4 L355 48 Z" fill="#D9E4DE" />
    <SvgRect x="0" y="229" width="393" height="22" fill={CREAM} />
    {Array.from({ length: 7 }, (_, i) => <SvgRect key={`stone-${i}`} x={(i * 79) % 370} y={265 + (i % 2) * 18} width="17" height="6" rx="3" fill="#E9C589" />)}
  </Svg>;
}

const AnimatedSvgRect = Animated.createAnimatedComponent(SvgRect);
const AnimatedSvgEllipse = Animated.createAnimatedComponent(SvgEllipse);

function lampRotation(degrees: number, x: number, y: number) {
  'worklet';
  const angle = degrees * Math.PI / 180;
  const a = Math.cos(angle), b = Math.sin(angle);
  return [a, b, -b, a, x - a * x + b * y, y - b * x - a * y];
}

function CatTail({ pose }: { pose: SharedValue<number> }) {
  const tail = useAnimatedProps(() => ({ opacity: 1, matrix: lampRotation(-35 - pose.value * 28, 190, 158) }));
  const tip = useAnimatedProps(() => ({ opacity: 1, matrix: lampRotation(-35 - pose.value * 28, 221, 137) }));
  return <>
    <AnimatedSvgRect animatedProps={tail} x="147.5" y="140" width="85" height="36" rx="18" fill="#FFAF30" />
    <AnimatedSvgRect animatedProps={tip} x="206.5" y="119" width="29" height="36" rx="14.5" fill={CREAM} />
  </>;
}

function CatArm({ pose, right = false }: { pose: SharedValue<number>; right?: boolean }) {
  const centerX = right ? 185 : 35;
  const props = useAnimatedProps(() => ({
    y: 125 - pose.value * 12,
    opacity: 1, matrix: lampRotation(right ? -35 - pose.value * 35 : 35 + pose.value * 35, centerX, 155 - pose.value * 12),
  }));
  return <AnimatedSvgRect animatedProps={props} x={right ? 167 : 17} width="36" height="60" rx="18" fill={CREAM} />;
}

function CatLeg({ pose, right = false }: { pose: SharedValue<number>; right?: boolean }) {
  const centerX = right ? 149 : 72;
  const props = useAnimatedProps(() => ({
    opacity: 1, matrix: lampRotation(right ? -12 - pose.value * 18 : 12 + pose.value * 18, centerX, 211),
  }));
  return <AnimatedSvgRect animatedProps={props} x={right ? 130 : 53} y="179" width="38" height="64" rx="19" fill={CREAM} />;
}

function CatEye({ pose, x }: { pose: SharedValue<number>; x: number }) {
  const props = useAnimatedProps(() => {
    const h = 15 - pose.value * 8;
    return { y: 68 + (15 - h) / 2, height: h };
  });
  return <AnimatedSvgRect animatedProps={props} x={x} width="13" rx="6.5" fill={INK} />;
}

function CatMouth({ pose }: { pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => ({ rx: 18.5 + pose.value * 5, ry: 10 + pose.value * 4.5 }));
  return <AnimatedSvgEllipse animatedProps={props} cx="110" cy="112" fill="#B8482D" />;
}

function Cat({ pose }: { pose: SharedValue<number> }) {
  return <Svg width={300} height={300} viewBox="-40 -40 300 300" style={{ position: 'absolute', left: -40, top: -40, overflow: 'visible' }} accessibilityElementsHidden>
    <SvgEllipse cx="110" cy="232" rx="72.5" ry="11" fill="rgba(106,61,40,0.1)" />
    <CatTail pose={pose} />
    <CatLeg pose={pose} />
    <CatLeg pose={pose} right />
    <SvgEllipse cx="110" cy="159" rx="73" ry="72.5" fill="#FFAF30" />
    <SvgEllipse cx="110" cy="163" rx="38" ry="44" fill="#FFE1AB" />
    <CatArm pose={pose} />
    <CatArm pose={pose} right />
    <SvgRect x="19.5" y="-9" width="65" height="76" rx="16" fill="#FFAF30" transform="rotate(-22 52 29)" />
    <SvgRect x="135.5" y="-9" width="65" height="76" rx="16" fill="#FFAF30" transform="rotate(22 168 29)" />
    <SvgEllipse cx="52" cy="22" rx="14.5" ry="21.5" fill={CREAM} transform="rotate(-22 52 22)" />
    <SvgEllipse cx="168" cy="22" rx="14.5" ry="21.5" fill={CREAM} transform="rotate(22 168 22)" />
    <SvgEllipse cx="110" cy="80" rx="95" ry="74" fill="#FFAF30" />
    <SvgEllipse cx="110" cy="117" rx="90" ry="44.5" fill={CREAM} />
    <SvgRect x="74" y="4" width="18" height="28" rx="9" fill="#F58B28" transform="rotate(-15 83 18)" />
    <SvgRect x="101" y="4" width="18" height="28" rx="9" fill="#F58B28" />
    <SvgRect x="128" y="4" width="18" height="28" rx="9" fill="#F58B28" transform="rotate(15 137 18)" />
    <SvgEllipse cx="110" cy="92" rx="9" ry="6" fill="#E37854" />
    <CatEye pose={pose} x={77} />
    <CatEye pose={pose} x={130} />
    <CatMouth pose={pose} />
    <SvgRect x="50" y="149" width="120" height="15" rx="7.5" fill="#78D6DB" />
    <SvgCircle cx="110" cy="155" r="14" fill="#A5EBE9" />
    <SvgCircle cx="104" cy="149" r="3.5" fill="rgba(255,255,255,0.7)" />
  </Svg>;
}

function Lamp() {
  return <Svg width={175} height={120} viewBox="0 0 175 120" accessibilityElementsHidden>
    <SvgEllipse cx="87.5" cy="106" rx="85" ry="14" fill="rgba(106,61,40,0.1)" />
    <SvgEllipse cx="30.5" cy="52" rx="22.5" ry="22.5" fill="none" stroke="#E7C93E" strokeWidth="10" transform="rotate(-25 30.5 52)" />
    <SvgRect x="56.5" y="93.5" width="62" height="13" rx="6.5" fill="#E7C93E" />
    <SvgEllipse cx="87.5" cy="60" rx="52.5" ry="33" fill="#FFE64F" />
    <SvgPath d="M113 45 Q148 58 165 40 L170 48 Q152 72 123 75 Z" fill="#FFE64F" />
    <SvgEllipse cx="87.5" cy="29" rx="25" ry="7.5" fill="#FFF18B" />
    <SvgRect x="79.5" y="8.5" width="16" height="21" rx="8" fill="#78D6DB" />
    <SvgPath d="M87.5 65 C70 46 57 68 87.5 90 C118 68 105 46 87.5 65Z" fill="#78D6DB" />
  </Svg>;
}

function LampSparkle({ pose, index }: { pose: SharedValue<number>; index: number }) {
  const style = useAnimatedStyle(() => {
    const angle = index * Math.PI / 4;
    const progress = pose.value;
    return { opacity: progress, transform: [{ translateX: Math.cos(angle) * (50 + progress * 40) }, { translateY: Math.sin(angle) * (30 + progress * 42) - 20 }, { scale: 0.5 + progress * 0.5 }] };
  });
  const size = 10 + index % 3 * 4;
  return <Animated.Text pointerEvents="none" style={[styles.sparkle, style, { fontSize: size }]}>✧</Animated.Text>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#F2D49B' },
  safeArea: { ...StyleSheet.absoluteFill, backgroundColor: '#F2D49B' },
  stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 760, overflow: 'visible' },
  village: { position: 'absolute', top: 55, left: 0 },
  header: { position: 'absolute', left: 28, top: 22, width: 337, height: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headerTitle: { color: INK, fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  headerCount: { color: INK, fontFamily: 'monospace', fontSize: 11 },
  catHit: { position: 'absolute', left: 86.5, top: 175, width: 220, height: 240, alignItems: 'center', justifyContent: 'center' },
  headline: { position: 'absolute', left: 26.5, top: 420, width: 340, color: INK, fontSize: 30, fontWeight: '700', lineHeight: 36, textAlign: 'center' },
  subtitle: { position: 'absolute', left: 21.5, top: 501, width: 350, color: INK, fontSize: 14, textAlign: 'center' },
  lampHit: { position: 'absolute', left: 109, top: 535, width: 175, height: 120, alignItems: 'center', justifyContent: 'center' },
  sparkle: { position: 'absolute', left: '50%', top: '50%', marginLeft: -10, marginTop: -16, color: CREAM },
  cta: { position: 'absolute', left: 44, top: 671, width: 305, height: 54, borderRadius: 27, alignItems: 'center', justifyContent: 'center', backgroundColor: CREAM },
  ctaCopy: { color: INK, fontSize: 16, fontWeight: '700' },
  footer: { position: 'absolute', left: 0, top: 740, width: 393, color: 'rgba(106,61,40,0.55)', fontSize: 9, fontWeight: '700', letterSpacing: 1.8, textAlign: 'center' },
});
