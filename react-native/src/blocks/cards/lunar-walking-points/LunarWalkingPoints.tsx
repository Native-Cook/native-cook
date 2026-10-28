import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState, type ComponentProps, type ReactNode } from 'react';
import { Alert, AppState, Image, Text, TextInput, View, useWindowDimensions, type LayoutChangeEvent, type StyleProp, type TextProps, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedProps, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Svg, { Circle, Defs, Ellipse, LinearGradient as SvgLinearGradient, Path, RadialGradient, Stop } from 'react-native-svg';
import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

export const LOOP = 8;
const WIDTH = 393;
const HEIGHT = 852;
const SOFT_LIMIT = 0.045;
const HARD_LIMIT = 0.065;
const AnimatedPath = Animated.createAnimatedComponent(Path);

function impactOrbitRelease() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

type OrbitMode = 'loop' | 'drag' | 'settle' | 'paused' | 'idle';
type MotionState = {
  mode: OrbitMode;
  q: number;
  envelope: number;
  loopEpoch: number;
  dragOriginQ: number;
  dragOriginEnvelope: number;
  dragRawOrigin: number;
  dragRubberAnchor: number;
  dragTranslationX: number;
  dragVelocityX: number;
  dragMoved: boolean;
  ignoreTapUntil: number;
  settleOriginQ: number;
  settleOriginEnvelope: number;
  settleVelocity: number;
  settleEnvelopeVelocity: number;
  settleStartedAt: number;
  resumeAt: number;
  pausedQ: number;
  pausedEnvelope: number;
};

type FriendData = { id: number; name: string; level: string; color: string; diameter: number; x: number; y: number; avatar: number; hitWidth: number; hitHeight: number };
type TapControlProps = {
  accessibilityLabel: string;
  children: ReactNode;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
  reduceMotion: boolean;
  active: boolean;
  selected?: boolean;
};

export type LunarWalkingPointsProps = {
  autoPlay?: boolean;
  previewTime?: number;
  previewOrbit?: number;
  previewReducedMotion?: boolean;
};

const FRIENDS: FriendData[] = [
  { id: 0, name: 'Ari', level: 'L3', color: '#22A5E9', diameter: 61, x: 54, y: 293, avatar: 0, hitWidth: 86, hitHeight: 88 },
  { id: 1, name: 'Mika', level: 'L6', color: '#EE45AC', diameter: 49, x: 344, y: 405, avatar: 1, hitWidth: 60, hitHeight: 82 },
  { id: 2, name: 'Jules', level: 'L4', color: '#F7DC21', diameter: 33, x: 42, y: 453, avatar: 2, hitWidth: 48, hitHeight: 54 },
];

function FixedText(props: TextProps) {
  return <Text {...props} allowFontScaling={false} />;
}

function clamp(value: number, low = 0, high = 1) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

function rubberOrbit(value: number) {
  'worklet';
  const amount = Math.abs(value);
  if (amount <= SOFT_LIMIT) return value;
  const compressed = SOFT_LIMIT + 0.02 * (1 - Math.exp(-(amount - SOFT_LIMIT) / 0.02));
  const result = Math.min(HARD_LIMIT, compressed);
  return value < 0 ? -result : result;
}

function rubberDerivative(value: number) {
  'worklet';
  const amount = Math.abs(value);
  if (amount <= SOFT_LIMIT) return 1;
  const compressed = SOFT_LIMIT + 0.02 * (1 - Math.exp(-(amount - SOFT_LIMIT) / 0.02));
  if (compressed >= HARD_LIMIT - 1e-12) return 0;
  return Math.exp(-(amount - SOFT_LIMIT) / 0.02);
}

function inverseRubberOrbit(value: number) {
  'worklet';
  const amount = Math.min(HARD_LIMIT - 1e-10, Math.abs(value));
  if (amount <= SOFT_LIMIT) return value;
  const raw = SOFT_LIMIT - 0.02 * Math.log(1 - (amount - SOFT_LIMIT) / 0.02);
  return value < 0 ? -raw : raw;
}

function loopOrbit(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  if (t < 1.2 || t >= 5.8) return { q: 0, envelope: 0 };
  const u = (t - 1.2) / 4.6;
  const envelope = Math.sin(Math.PI * u) ** 2;
  return { q: 0.10 * Math.sin(2 * Math.PI * u) * envelope, envelope };
}

function springOrbit(time: number, origin: number, velocity: number) {
  'worklet';
  if (time >= 1.2) return 0;
  const omega = 12;
  const damping = 0.85;
  const decay = damping * omega;
  const frequency = omega * Math.sqrt(1 - damping * damping);
  return Math.exp(-decay * time) * (origin * Math.cos(frequency * time) + ((velocity + decay * origin) / frequency) * Math.sin(frequency * time));
}

function springIsBounded(velocity: number, origin: number, limit = HARD_LIMIT, ceilingOnly = false) {
  'worklet';
  const omega = 12;
  const damping = 0.85;
  const decay = damping * omega;
  const frequency = omega * Math.sqrt(1 - damping * damping);
  const sineCoefficient = (velocity + decay * origin) / frequency;
  const derivativeCos = velocity;
  const derivativeSin = -decay * sineCoefficient - origin * frequency;
  const phase = Math.atan2(derivativeSin, derivativeCos) + Math.PI / 2;
  const withinLimit = (value: number) => ceilingOnly ? value <= limit + 1e-10 : Math.abs(value) <= limit + 1e-10;
  if (!withinLimit(origin)) return false;
  if (!withinLimit(springOrbit(1.2, origin, velocity))) return false;
  for (let turn = -8; turn <= 8; turn += 1) {
    const time = (phase + turn * Math.PI) / frequency;
    if (time > 0 && time < 1.2 && !withinLimit(springOrbit(time, origin, velocity))) return false;
  }
  return true;
}

function boundedSpringVelocity(velocity: number, origin: number, limit = HARD_LIMIT, ceilingOnly = false) {
  'worklet';
  const initial = velocity;
  if (springIsBounded(initial, origin, limit, ceilingOnly)) return initial;
  const sign = initial < 0 ? -1 : 1;
  let low = 0;
  let high = Math.abs(initial);
  for (let iteration = 0; iteration < 40; iteration += 1) {
    const middle = (low + high) / 2;
    if (springIsBounded(sign * middle, origin, limit, ceilingOnly)) low = middle;
    else high = middle;
  }
  return sign * low;
}

function envelopeReleaseVelocity(q: number, envelope: number, velocity: number) {
  'worklet';
  const direction = q === 0 ? (velocity < 0 ? -1 : 1) : (q < 0 ? -1 : 1);
  const requested = direction * velocity / 0.06495;
  if ((envelope <= 0 && requested < 0) || (envelope >= 1 && requested > 0)) return 0;
  return boundedSpringVelocity(requested, envelope, 1, true);
}

function makeMotion(mode: OrbitMode = 'idle'): MotionState {
  'worklet';
  return {
    mode, q: 0, envelope: 0, loopEpoch: 0, dragOriginQ: 0, dragOriginEnvelope: 0,
    dragRawOrigin: 0, dragRubberAnchor: 0, dragTranslationX: 0, dragVelocityX: 0,
    dragMoved: false, ignoreTapUntil: 0, settleOriginQ: 0, settleOriginEnvelope: 0,
    settleVelocity: 0, settleEnvelopeVelocity: 0, settleStartedAt: 0, resumeAt: 0, pausedQ: 0, pausedEnvelope: 0,
  };
}

function beginContact(motion: SharedValue<MotionState>, frameTime: SharedValue<number>) {
  'worklet';
  const current = motion.get();
  const raw = inverseRubberOrbit(current.q);
  motion.set({
    ...current,
    mode: 'drag',
    dragOriginQ: current.q,
    dragOriginEnvelope: current.envelope,
    dragRawOrigin: raw,
    dragRubberAnchor: rubberOrbit(raw),
    dragTranslationX: 0,
    dragVelocityX: 0,
    dragMoved: false,
    settleOriginQ: current.q,
    settleOriginEnvelope: current.envelope,
    settleVelocity: 0,
    settleEnvelopeVelocity: 0,
    settleStartedAt: frameTime.get(),
  });
}

function updateContact(motion: SharedValue<MotionState>, stageScale: number, translationX: number, velocityX: number) {
  'worklet';
  const current = motion.get();
  if (current.mode !== 'drag') return;
  const translation = translationX / stageScale;
  const raw = current.dragRawOrigin + translation / 420;
  const q = clamp(current.dragOriginQ + rubberOrbit(raw) - current.dragRubberAnchor, -HARD_LIMIT, HARD_LIMIT);
  const rawEnvelope = current.dragOriginEnvelope + (Math.abs(q) - Math.abs(current.dragOriginQ)) / 0.06495;
  const envelope = clamp(rawEnvelope);
  motion.set({
    ...current,
    q,
    envelope,
    dragTranslationX: translation,
    dragVelocityX: velocityX / stageScale,
    dragMoved: current.dragMoved || Math.abs(translation) > 5,
  });
}

function finishContact(motion: SharedValue<MotionState>, frameTime: SharedValue<number>, stageScale: number, velocityX: number, cancelled: boolean) {
  'worklet';
  const current = motion.get();
  if (current.mode !== 'drag') return;
  const raw = current.dragRawOrigin + current.dragTranslationX / 420;
  const q = clamp(current.dragOriginQ + rubberOrbit(raw) - current.dragRubberAnchor, -HARD_LIMIT, HARD_LIMIT);
  const rawEnvelope = current.dragOriginEnvelope + (Math.abs(q) - Math.abs(current.dragOriginQ)) / 0.06495;
  const envelope = clamp(rawEnvelope);
  const velocity = current.dragMoved && !cancelled
    ? rubberDerivative(raw) * (velocityX / stageScale) / 420
    : 0;
  const boundedVelocity = boundedSpringVelocity(clamp(velocity, -1, 1), q);
  const carryEnvelopeVelocity = current.dragMoved && !cancelled && rawEnvelope >= 0 && rawEnvelope <= 1;
  const settleEnvelopeVelocity = carryEnvelopeVelocity ? envelopeReleaseVelocity(q, envelope, boundedVelocity) : 0;
  const now = frameTime.get();
  motion.set({
    ...current,
    mode: 'settle', q, envelope,
    settleOriginQ: q,
    settleOriginEnvelope: envelope,
    settleVelocity: boundedVelocity,
    settleEnvelopeVelocity,
    settleStartedAt: now,
    resumeAt: now + 2,
    ignoreTapUntil: current.dragMoved ? now + 0.25 : current.ignoreTapUntil,
    dragMoved: current.dragMoved,
  });
  if (current.dragMoved && !cancelled) scheduleOnRN(impactOrbitRelease);
  return current.dragMoved && !cancelled;
}

function pauseMotion(motion: SharedValue<MotionState>) {
  'worklet';
  const current = motion.get();
  if (current.mode === 'paused') return;
  motion.set({ ...current, mode: 'paused', pausedQ: current.q, pausedEnvelope: current.envelope, dragMoved: false });
}

function resumeMotion(motion: SharedValue<MotionState>, frameTime: SharedValue<number>) {
  'worklet';
  const current = motion.get();
  const now = frameTime.get();
  motion.set({
    ...current,
    mode: 'settle', q: current.pausedQ, envelope: current.pausedEnvelope,
    settleOriginQ: current.pausedQ,
    settleOriginEnvelope: current.pausedEnvelope,
    settleVelocity: 0,
    settleEnvelopeVelocity: 0,
    settleStartedAt: now,
    resumeAt: now + 2,
    dragMoved: false,
  });
}

function interruptMotion(motion: SharedValue<MotionState>, frameTime: SharedValue<number>) {
  'worklet';
  const current = motion.get();
  if (current.mode === 'paused') return;
  const now = frameTime.get();
  motion.set({
    ...current,
    mode: 'settle',
    settleOriginQ: current.q,
    settleOriginEnvelope: current.envelope,
    settleVelocity: 0,
    settleEnvelopeVelocity: 0,
    settleStartedAt: now,
    resumeAt: now + 2,
    dragMoved: false,
  });
}

function resetMotion(motion: SharedValue<MotionState>, frameTime: SharedValue<number>, autoPlay: boolean, reduced: boolean, previewTime?: number, previewOrbit?: number) {
  'worklet';
  frameTime.set(0);
  const next = makeMotion(autoPlay && !reduced && previewTime == null && previewOrbit == null ? 'loop' : 'idle');
  if (!reduced && previewOrbit != null) {
    next.q = clamp(previewOrbit, -HARD_LIMIT, HARD_LIMIT);
    next.envelope = clamp(Math.abs(next.q) / 0.06495);
  } else if (!reduced && previewTime != null) {
    const preview = loopOrbit(previewTime);
    next.q = preview.q;
    next.envelope = preview.envelope;
  }
  motion.set(next);
}

function createOrbitPan(
  motion: SharedValue<MotionState>,
  frameTime: SharedValue<number>,
  stageScale: number,
  enabled: boolean,
  onFrameStart: () => void,
) {
  return Gesture.Pan().minDistance(0).averageTouches(true).enabled(enabled)
    .onBegin(() => {
      beginContact(motion, frameTime);
      scheduleOnRN(onFrameStart);
    })
    .onUpdate(event => updateContact(motion, stageScale, event.translationX, event.velocityX))
    .onEnd((event, success) => {
      if (success) finishContact(motion, frameTime, stageScale, event.velocityX, false);
    })
    .onFinalize((_event, success) => {
      if (!success) finishContact(motion, frameTime, stageScale, 0, true);
    });
}

function createTapGesture(onPress: () => void, pressed: SharedValue<number>, enabled: boolean) {
  return Gesture.Tap().enabled(enabled)
    .onBegin(() => { pressed.set(withTiming(1, { duration: 120, easing: Easing.out(Easing.quad) })); })
    .onEnd((_event, success) => { if (success) scheduleOnRN(onPress); })
    .onFinalize(() => { pressed.set(withTiming(0, { duration: 100, easing: Easing.out(Easing.quad) })); });
}

function ButtonFrame({ children, accessibilityLabel, onPress, style, reduceMotion, active, selected }: TapControlProps) {
  const pressed = useSharedValue(0);
  const animatedStyle = useAnimatedStyle(() => ({
    opacity: 1 - 0.12 * pressed.get(),
    transform: [{ scale: reduceMotion ? 1 : 1 - 0.025 * pressed.get() }],
  }), [reduceMotion]);
  const tap = useMemo(() => createTapGesture(onPress, pressed, active), [active, onPress, pressed]);
  const accessibilityAction = useCallback(() => onPress(), [onPress]);
  return (
    <GestureDetector gesture={tap}>
      <Animated.View
        accessible
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
        accessibilityState={selected ? { selected: true } : undefined}
        accessibilityActions={[{ name: 'activate', label: 'Activate' }]}
        onAccessibilityAction={accessibilityAction}
        onAccessibilityTap={accessibilityAction}
        style={[style, animatedStyle]}
      >
        {children}
      </Animated.View>
    </GestureDetector>
  );
}

function iconName(name: string): ComponentProps<typeof Ionicons>['name'] {
  return name as ComponentProps<typeof Ionicons>['name'];
}

const AVATARS = require('./assets/avatars.png');
const MOON = require('./assets/moon.png');
const RUNNER = require('./assets/runner.png');

function AvatarImage({ index, diameter }: { index: number; diameter: number }) {
  const column = index % 2;
  const row = Math.floor(index / 2);
  return (
    <View style={{ width: diameter, height: diameter, borderRadius: diameter / 2, overflow: 'hidden', backgroundColor: '#8D8E85' }}>
      <Image source={AVATARS} resizeMode="stretch" style={{ position: 'absolute', width: diameter * 2, height: diameter * 2, left: -column * diameter, top: -row * diameter }} />
    </View>
  );
}

function roundedHexPath(width: number, height: number, amount: number) {
  'worklet';
  const vertices: { x: number; y: number }[] = [];
  for (let index = 0; index < 6; index += 1) {
    const angle = index * Math.PI / 3 - Math.PI / 2;
    vertices.push({ x: width / 2 + Math.cos(angle) * width / 2, y: height / 2 + Math.sin(angle) * height / 2 });
  }
  let d = `M ${vertices[0].x} ${vertices[0].y}`;
  for (let index = 0; index < 6; index += 1) {
    const start = vertices[index];
    const end = vertices[(index + 1) % 6];
    const midpointX = (start.x + end.x) / 2;
    const midpointY = (start.y + end.y) / 2;
    const radialX = midpointX - width / 2;
    const radialY = midpointY - height / 2;
    const controlX = midpointX + radialX * 0.309401 * amount;
    const controlY = midpointY + radialY * 0.309401 * amount;
    d += ` Q ${controlX} ${controlY} ${end.x} ${end.y}`;
  }
  return `${d} Z`;
}

function FriendButton({
  friend, selected, motion, frameTime, reduceMotion, active, dragEnabled, stageScale, onFrameStart, onSelect,
}: {
  friend: FriendData; selected: boolean; motion: SharedValue<MotionState>; frameTime: SharedValue<number>; reduceMotion: boolean; active: boolean; dragEnabled: boolean; stageScale: number;
  onFrameStart: () => void; onSelect: (id: number) => void;
}) {
  const pressed = useSharedValue(0);
  const isSelectedShape = selected;
  const hexWidth = friend.id === 0 ? 28 : friend.id === 1 ? 22 : 18;
  const hexHeight = friend.id === 0 ? 32 : friend.id === 1 ? 25 : 21;
  const badgeOffsetX = friend.id === 0 ? 29 : friend.id === 1 ? 19 : 14;
  const badgeOffsetY = friend.id === 2 ? -16 : -28;
  const badgeScale = useAnimatedStyle(() => ({ transform: [{ scale: selected && friend.id !== 0 && !reduceMotion ? 1.06 : 1 }] }), [friend.id, reduceMotion, selected]);
  const orbitStyle = useAnimatedStyle(() => {
    const q = motion.get().q;
    const dx = friend.x - 196.5;
    const dy = friend.y - 496;
    const x = 196.5 + dx * Math.cos(q) - (dy / 0.72) * Math.sin(q) - friend.x;
    const y = 496 + 0.72 * dx * Math.sin(q) + dy * Math.cos(q) - friend.y;
    return { transform: [{ translateX: x }, { translateY: y }] };
  }, [friend.x, friend.y]);
  const feedbackStyle = useAnimatedStyle(() => ({
    opacity: 1 - 0.12 * pressed.get(),
    transform: [{ scale: reduceMotion ? 1 : 1 - 0.025 * pressed.get() }],
  }), [reduceMotion]);
  const onPress = useCallback(() => onSelect(friend.id), [friend.id, onSelect]);
  const pan = useMemo(() => createOrbitPan(motion, frameTime, stageScale, dragEnabled && !reduceMotion, onFrameStart), [dragEnabled, frameTime, motion, onFrameStart, reduceMotion, stageScale]);
  const tap = useMemo(() => Gesture.Tap().enabled(active)
    .onBegin(() => { pressed.set(withTiming(1, { duration: 120, easing: Easing.out(Easing.quad) })); })
    .onEnd((_event, success) => {
      if (!success) return;
      const current = motion.get();
      if (current.dragMoved || frameTime.get() < current.ignoreTapUntil) return;
      scheduleOnRN(onPress);
    })
    .onFinalize(() => { pressed.set(withTiming(0, { duration: 100, easing: Easing.out(Easing.quad) })); }), [active, frameTime, motion, onPress, pressed]);
  const combined = useMemo(() => Gesture.Simultaneous(pan, tap), [pan, tap]);
  const hexProps = useAnimatedProps(() => ({ d: roundedHexPath(hexWidth, hexHeight, selected ? Math.min(1, Math.abs(motion.get().q) / HARD_LIMIT) : 0) }), [hexHeight, hexWidth, selected]);
  const labelX = friend.hitWidth / 2 + friend.diameter * 0.24;
  const labelY = friend.hitHeight / 2 + friend.diameter * 0.48;
  const avatarLeft = (friend.hitWidth - friend.diameter) / 2;
  const avatarTop = (friend.hitHeight - friend.diameter) / 2;
  const levelSize = friend.diameter < 40 ? 12 : 17;
  const badgeLeft = friend.hitWidth / 2 + badgeOffsetX - hexWidth / 2;
  const badgeTop = friend.hitHeight / 2 + badgeOffsetY - hexHeight / 2;
  const staticD = roundedHexPath(hexWidth, hexHeight, 0);
  return (
    <GestureDetector gesture={combined}>
      <Animated.View
        accessible
        accessibilityRole="button"
        accessibilityLabel={`${friend.name}, level ${friend.level}`}
        accessibilityState={selected ? { selected: true } : undefined}
        accessibilityActions={[{ name: 'activate', label: 'Select friend' }]}
        onAccessibilityAction={onPress}
        onAccessibilityTap={onPress}
        style={[{ position: 'absolute', width: friend.hitWidth, height: friend.hitHeight, left: friend.x - friend.hitWidth / 2, top: friend.y - friend.hitHeight / 2 }, orbitStyle]}
      >
        <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: 0, top: 0, width: friend.hitWidth, height: friend.hitHeight }, feedbackStyle]}>
          <View style={{ position: 'absolute', left: avatarLeft, top: avatarTop }}>
            <AvatarImage index={friend.avatar} diameter={friend.diameter} />
            <View style={{ position: 'absolute', left: 0, top: 0, width: friend.diameter, height: friend.diameter, borderRadius: friend.diameter / 2, borderWidth: selected && friend.id !== 0 ? 1.5 : 1, borderColor: selected && friend.id !== 0 ? '#FFE000' : '#FFFFFF' }} />
          </View>
          <View style={{ position: 'absolute', left: labelX - levelSize / 2, top: labelY - levelSize / 2, width: levelSize, height: levelSize, borderRadius: levelSize / 2, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' }}>
            <FixedText style={{ fontSize: friend.diameter < 40 ? 5 : 6, lineHeight: friend.diameter < 40 ? 6 : 7, fontWeight: '700', color: '#000000' }}>{friend.level}</FixedText>
          </View>
          <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: badgeLeft, top: badgeTop, width: hexWidth, height: hexHeight }, badgeScale]}>
            <Svg width={hexWidth} height={hexHeight} viewBox={`0 0 ${hexWidth} ${hexHeight}`}>
              {isSelectedShape
                ? <AnimatedPath animatedProps={hexProps} fill={friend.color} stroke={selected && friend.id !== 0 ? '#FFE000' : '#FFFFFF'} strokeOpacity={selected && friend.id !== 0 ? 1 : 0.9} strokeWidth={selected && friend.id !== 0 ? 2 : 1.5} />
                : <Path d={staticD} fill={friend.color} stroke="#FFFFFF" strokeOpacity={0.9} strokeWidth={1.5} />}
            </Svg>
          </Animated.View>
        </Animated.View>
      </Animated.View>
    </GestureDetector>
  );
}

function AppBackdrop() {
  const stars = useMemo(() => Array.from({ length: 38 }, (_, index) => ({
    x: (index * 97 + 31) % 390,
    y: (index * 61 + 120) % 680,
    radius: index % 4 === 0 ? 1 : 0.5,
  })), []);
  return (
    <Svg width={WIDTH} height={HEIGHT} style={{ position: 'absolute', left: 0, top: 0 }} pointerEvents="none">
      <Defs>
        <RadialGradient id="lwpMoonHalo" cx="50%" cy="50%" r="50%">
          <Stop offset="0%" stopColor="#FF4400" stopOpacity="0.18" />
          <Stop offset="50%" stopColor="#FF4400" stopOpacity="0.18" />
          <Stop offset="100%" stopColor="#FF4400" stopOpacity="0" />
        </RadialGradient>
        <RadialGradient id="lwpBottomBloom" cx="50%" cy="50%" r="50%">
          <Stop offset="0%" stopColor="#3F0070" stopOpacity="0.18" />
          <Stop offset="100%" stopColor="#3F0070" stopOpacity="0" />
        </RadialGradient>
        <SvgLinearGradient id="lwpStarFill" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0%" stopColor="#FFFFFF" stopOpacity="0.55" />
          <Stop offset="100%" stopColor="#FFFFFF" stopOpacity="0.02" />
        </SvgLinearGradient>
      </Defs>
      {stars.map((star, index) => <Circle key={index} cx={star.x} cy={star.y} r={star.radius} fill="#FFFFFF" opacity={0.22} />)}
      <Path d="M -30 292 C 165 258 330 355 440 520" fill="none" stroke="#FFFFFF" strokeOpacity={0.045} strokeWidth={1} />
      <Circle cx={199} cy={496} r={120} fill="url(#lwpMoonHalo)" />
      <Circle cx={196.5} cy={800} r={230} fill="url(#lwpBottomBloom)" />
      <Path d="M 11 0 Q 11 14 22 14 Q 11 14 11 28 Q 11 14 0 14 Q 11 14 11 0 Z" transform="translate(9 664)" fill="url(#lwpStarFill)" />
      <Path d="M 3.5 0 Q 3.5 4.5 7 4.5 Q 3.5 4.5 3.5 9 Q 3.5 4.5 0 4.5 Q 3.5 4.5 3.5 0 Z" transform="translate(295.5 354.5)" fill="#FFFFFF" opacity={0.85} />
    </Svg>
  );
}

function OrbitField({
  motion, frameTime, calloutOpacity, stageScale, reduceMotion, active, searchFocused, previewTime, previewOrbit,
  selectedFriendId, displayedFriendId, calloutAccessible, searchText, matchingFriends, onFrameStart, onSelectFriend,
}: {
  motion: SharedValue<MotionState>; frameTime: SharedValue<number>; calloutOpacity: SharedValue<number>; stageScale: number; reduceMotion: boolean; active: boolean;
  searchFocused: boolean;
  previewTime?: number; previewOrbit?: number; selectedFriendId: number; displayedFriendId: number; calloutAccessible: boolean; searchText: string;
  matchingFriends: FriendData[]; onFrameStart: () => void; onSelectFriend: (id: number) => void;
}) {
  const fieldPan = useMemo(() => createOrbitPan(motion, frameTime, stageScale, active && !searchFocused && !reduceMotion && previewTime == null && previewOrbit == null, onFrameStart), [active, frameTime, motion, onFrameStart, previewOrbit, previewTime, reduceMotion, searchFocused, stageScale]);
  const runnerOffset = useAnimatedStyle(() => {
    const { q, envelope } = motion.get();
    const e = reduceMotion ? 0 : clamp(envelope);
    const roll = -2 * clamp(q / HARD_LIMIT, -1, 1) * e;
    const angle = roll * Math.PI / 180;
    const tx = -70 * q;
    const ty = -16 * e;
    return {
      transform: [
        { translateX: tx * Math.cos(angle) - ty * Math.sin(angle) },
        { translateY: tx * Math.sin(angle) + ty * Math.cos(angle) },
      ],
    };
  }, [reduceMotion]);
  const runnerRotation = useAnimatedStyle(() => {
    const { q, envelope } = motion.get();
    const e = reduceMotion ? 0 : clamp(envelope);
    const roll = -2 * clamp(q / HARD_LIMIT, -1, 1) * e;
    return { transform: [{ rotateZ: `${roll}deg` }] };
  }, [reduceMotion]);
  const shadowPlacement = useAnimatedStyle(() => {
    const { q, envelope } = motion.get();
    const e = reduceMotion ? 0 : clamp(envelope);
    const opacity = 0.16 * Math.min(1, e / 0.12) * (1 - e);
    return { opacity, transform: [{ translateX: -70 * q }] };
  }, [reduceMotion]);
  const shadowScale = useAnimatedStyle(() => {
    const e = reduceMotion ? 0 : clamp(motion.get().envelope);
    return { transform: [{ scaleX: 1 - 0.4 * e }, { scaleY: 1 - 0.375 * e }] };
  }, [reduceMotion]);
  const auraOffset = useAnimatedStyle(() => {
    const { q, envelope } = motion.get();
    const e = reduceMotion ? 0 : clamp(envelope);
    const alpha = e * e * (3 - 2 * e);
    return { opacity: alpha, transform: [{ translateX: -45 * q }, { translateY: -3 * e }] };
  }, [reduceMotion]);
  const auraScale = useAnimatedStyle(() => {
    const e = reduceMotion ? 0 : clamp(motion.get().envelope);
    const alpha = e * e * (3 - 2 * e);
    return { transform: [{ scale: 1 + 0.04 * alpha }] };
  }, [reduceMotion]);
  const calloutMotion = useAnimatedStyle(() => {
    const q = motion.get().q;
    const dx = 54 - 196.5;
    const dy = 293 - 496;
    const x = 196.5 + dx * Math.cos(q) - (dy / 0.72) * Math.sin(q);
    const y = 496 + 0.72 * dx * Math.sin(q) + dy * Math.cos(q);
    return { transform: [{ translateX: x - 54 }, { translateY: y - 293 }] };
  });
  const fieldGesture = active && !searchFocused && !reduceMotion && previewTime == null && previewOrbit == null;
  const currentFriends = matchingFriends;
  const displayedFriend = FRIENDS.find(friend => friend.id === displayedFriendId) ?? FRIENDS[0];
  const calloutOpacityStyle = useAnimatedStyle(() => ({ opacity: calloutOpacity.get() }));
  return (
    <>
      <GestureDetector gesture={fieldPan}>
        <View pointerEvents={fieldGesture ? 'auto' : 'none'} style={{ position: 'absolute', left: 0, top: 216, width: WIDTH, height: 390, backgroundColor: 'transparent' }} />
      </GestureDetector>
      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[{ position: 'absolute', left: 79, top: 376, width: 240, height: 240 }, auraOffset]}
      >
        <Animated.View style={[{ width: 240, height: 240 }, auraScale]}>
          <Svg width={240} height={240} viewBox="0 0 240 240">
            <Defs>
              <RadialGradient id="lwpGravityAura" cx="50%" cy="50%" r="50%">
                <Stop offset="0%" stopColor="#FFD24A" stopOpacity={0} />
                <Stop offset="58%" stopColor="#FFD24A" stopOpacity={0.3} />
                <Stop offset="78%" stopColor="#FF6A00" stopOpacity={0.16} />
                <Stop offset="100%" stopColor="#FF6A00" stopOpacity={0} />
              </RadialGradient>
            </Defs>
            <Circle cx={120} cy={120} r={120} fill="url(#lwpGravityAura)" />
          </Svg>
        </Animated.View>
      </Animated.View>
      <View pointerEvents="none" style={{ position: 'absolute', width: 180, height: 180, left: 109, top: 406 }}>
        <Image source={MOON} resizeMode="contain" style={{ width: 180, height: 180 }} />
      </View>
      <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: 179, top: 429, width: 40, height: 12 }, shadowPlacement]}>
        <Animated.View style={[{ width: 40, height: 12 }, shadowScale]}>
          <Svg width={40} height={12} viewBox="0 0 40 12">
            <Defs>
              <RadialGradient id="lwpGravityShadow" gradientUnits="userSpaceOnUse" cx={20} cy={6} r={20} gradientTransform={[{ translateY: 6 }, { scaleY: 0.3 }, { translateY: -6 }]}>
                <Stop offset="0%" stopColor="#02013A" stopOpacity={1} />
                <Stop offset="70%" stopColor="#02013A" stopOpacity={1} />
                <Stop offset="100%" stopColor="#02013A" stopOpacity={0} />
              </RadialGradient>
            </Defs>
            <Ellipse cx={20} cy={6} rx={20} ry={6} fill="url(#lwpGravityShadow)" />
          </Svg>
        </Animated.View>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[{ position: 'absolute', width: 129, height: 142, left: 134.5, top: 308 }, runnerOffset]}>
        <Animated.View style={[{ width: 129, height: 142 }, runnerRotation]}>
          <Image source={RUNNER} resizeMode="contain" style={{ width: 129, height: 142 }} />
        </Animated.View>
      </Animated.View>
      {currentFriends.map(friend => (
        <FriendButton
          key={friend.id}
          friend={friend}
          selected={selectedFriendId === friend.id}
          motion={motion}
          frameTime={frameTime}
          reduceMotion={reduceMotion}
          active={active && previewTime == null && previewOrbit == null}
          dragEnabled={active && !searchFocused && previewTime == null && previewOrbit == null}
          stageScale={stageScale}
          onFrameStart={onFrameStart}
          onSelect={onSelectFriend}
        />
      ))}
      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden={!calloutAccessible}
        importantForAccessibility={calloutAccessible ? 'yes' : 'no-hide-descendants'}
        accessible
        accessibilityLabel={searchText.length > 0 && matchingFriends.length === 0 ? 'No friends found' : displayedFriend.id === 0 ? 'Ari just scored 345 points by 5 kilometre walk' : `${displayedFriend.name}, level ${displayedFriend.level}, walking friend`}
        style={[{ position: 'absolute', width: 154, height: 59, left: 107, top: 243 }, calloutMotion, calloutOpacityStyle]}
      >
        {searchText.length > 0 && matchingFriends.length === 0 ? (
          <View style={styles.noMatchCallout}>
            <FixedText style={styles.calloutSecondary}>No friends found</FixedText>
          </View>
        ) : (
          <View style={styles.callout}>
            <Svg width={10} height={14} style={styles.calloutTail}>
              <Path d="M 10 0 L 0 7 L 10 14 Z" fill="#1D1B43" stroke="rgba(255,255,255,0.12)" strokeWidth={1} />
            </Svg>
            <View style={styles.calloutCopy}>
              {displayedFriend.id === 0 ? (
                <>
                  <FixedText numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.9} style={styles.calloutPrimary}>Ari just scored <FixedText style={styles.calloutPoints}>345 pts</FixedText></FixedText>
                  <FixedText style={styles.calloutSecondary}>by 5 km walk</FixedText>
                </>
              ) : (
                <>
                  <FixedText style={styles.calloutFriend}>{displayedFriend.name} · {displayedFriend.level}</FixedText>
                  <FixedText style={styles.calloutSecondary}>Walking friend</FixedText>
                </>
              )}
            </View>
          </View>
        )}
      </Animated.View>
    </>
  );
}

export function LunarWalkingPoints({
  autoPlay = false,
  previewTime,
  previewOrbit,
  previewReducedMotion = false,
}: LunarWalkingPointsProps) {
  const systemReducedMotion = useReducedMotionPreference();
  const reduceMotion = systemReducedMotion || previewReducedMotion;
  const { width: windowWidth, height: windowHeight } = useWindowDimensions();
  const [viewport, setViewport] = useState({ width: windowWidth, height: windowHeight });
  const hasRootLayout = useRef(false);
  const [appActive, setAppActive] = useState(AppState.currentState === 'active');
  const [searchText, setSearchText] = useState('');
  const [searchFocused, setSearchFocused] = useState(false);
  const searchFocusedRef = useRef(false);
  const alertOpen = useRef(false);
  const [selectedFriendId, setSelectedFriendId] = useState(0);
  const [displayedFriendId, setDisplayedFriendId] = useState(0);
  const selectedFriendRef = useRef(0);
  const displayedFriendRef = useRef(0);
  const [calloutAccessible, setCalloutAccessible] = useState(true);
  const calloutGeneration = useRef(0);
  const calloutTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [selectedTab, setSelectedTab] = useState(1);
  const [selectedNav, setSelectedNav] = useState(0);
  const runtimeSettings = useRef({ reduceMotion, appActive, previewTime, previewOrbit });
  useEffect(() => {
    runtimeSettings.current = { reduceMotion, appActive, previewTime, previewOrbit };
  }, [appActive, previewOrbit, previewTime, reduceMotion]);
  const activeShared = useSharedValue(appActive);
  const reducedShared = useSharedValue(reduceMotion);
  const autoPlayShared = useSharedValue(autoPlay);
  const frameTime = useSharedValue(0);
  const calloutOpacity = useSharedValue(1);
  const motion = useSharedValue<MotionState>(makeMotion(autoPlay && !reduceMotion ? 'loop' : 'idle'));
  const stageScale = Math.min(viewport.width / WIDTH, viewport.height / HEIGHT);
  const scale = Math.max(0.01, stageScale);
  const matchingFriends = useMemo(() => {
    const query = searchText.trim().toLocaleLowerCase();
    if (!query) return FRIENDS;
    return FRIENDS.filter(friend => friend.name.toLocaleLowerCase().includes(query) || friend.level.toLocaleLowerCase().includes(query));
  }, [searchText]);

  const frameHandle = useRef<{ setActive: (active: boolean) => void } | null>(null);
  const onFrameStart = useCallback(() => frameHandle.current?.setActive(true), []);
  const onFrameStop = useCallback(() => frameHandle.current?.setActive(false), []);

  const frame = useFrameCallback(info => {
    const delta = Math.max(0, (info.timeSincePreviousFrame ?? 0) / 1000);
    if (delta <= 0) return;
    const now = frameTime.get() + delta;
    frameTime.set(now);
    const current = motion.get();
    if (!activeShared.get() || reducedShared.get()) return;
    if (current.mode === 'loop') {
      const pose = loopOrbit(now - current.loopEpoch);
      motion.set({ ...current, q: pose.q, envelope: pose.envelope });
      return;
    }
    if (current.mode !== 'settle') return;
    if (now >= current.resumeAt) {
      const restartLoop = autoPlayShared.get();
      motion.set({ ...current, mode: restartLoop ? 'loop' : 'idle', q: 0, envelope: 0, loopEpoch: now, dragMoved: false });
      if (!restartLoop) scheduleOnRN(onFrameStop);
      return;
    }
    const age = now - current.settleStartedAt;
    motion.set({
      ...current,
      q: springOrbit(age, current.settleOriginQ, current.settleVelocity),
      envelope: clamp(springOrbit(age, current.settleOriginEnvelope, current.settleEnvelopeVelocity)),
    });
  }, false);

  useEffect(() => {
    frameHandle.current = frame;
    autoPlayShared.set(autoPlay);
    reducedShared.set(reduceMotion);
    activeShared.set(appActive);
    const overlayPaused = searchFocusedRef.current || alertOpen.current;
    if (!overlayPaused || reduceMotion || !appActive) {
      scheduleOnUI(resetMotion, motion, frameTime, autoPlay, reduceMotion, previewTime, previewOrbit);
    }
    frame.setActive(!overlayPaused && autoPlay && appActive && !reduceMotion && previewTime == null && previewOrbit == null);
    return () => {
      frame.setActive(false);
      if (frameHandle.current === frame) frameHandle.current = null;
    };
  }, [activeShared, appActive, autoPlay, autoPlayShared, frame, frameTime, motion, previewOrbit, previewTime, reduceMotion, reducedShared]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      const active = state === 'active';
      setAppActive(active);
      runtimeSettings.current.appActive = active;
      activeShared.set(active);
      if (!active) frameHandle.current?.setActive(false);
    });
    return () => subscription.remove();
  }, [activeShared]);

  useEffect(() => {
    if (!hasRootLayout.current) setViewport({ width: windowWidth, height: windowHeight });
  }, [windowHeight, windowWidth]);

  useEffect(() => () => {
    if (calloutTimeout.current) clearTimeout(calloutTimeout.current);
  }, []);

  const interrupt = useCallback(() => {
    const settings = runtimeSettings.current;
    if (settings.reduceMotion || !settings.appActive || searchFocusedRef.current || alertOpen.current || settings.previewTime != null || settings.previewOrbit != null) return;
    frameHandle.current?.setActive(true);
    scheduleOnUI(interruptMotion, motion, frameTime);
  }, [frameTime, motion]);

  const pauseForOverlay = useCallback(() => {
    frameHandle.current?.setActive(false);
    scheduleOnUI(pauseMotion, motion);
  }, [motion]);

  const resumeFromOverlay = useCallback(() => {
    const settings = runtimeSettings.current;
    if (settings.reduceMotion || !settings.appActive || searchFocusedRef.current || alertOpen.current || settings.previewTime != null || settings.previewOrbit != null) return;
    frameHandle.current?.setActive(true);
    scheduleOnUI(resumeMotion, motion, frameTime);
  }, [frameTime, motion]);

  const dismissAlert = useCallback(() => {
    if (!alertOpen.current) return;
    alertOpen.current = false;
    resumeFromOverlay();
  }, [resumeFromOverlay]);

  const showAlert = useCallback((message: string) => {
    if (alertOpen.current) return;
    alertOpen.current = true;
    pauseForOverlay();
    Alert.alert('Walking Activity', message, [{ text: 'OK', style: 'cancel', onPress: dismissAlert }], {
      cancelable: true,
      onDismiss: dismissAlert,
    });
  }, [dismissAlert, pauseForOverlay]);

  const selectFriend = useCallback((id: number) => {
    interrupt();
    if (selectedFriendRef.current === id) return;
    selectedFriendRef.current = id;
    setSelectedFriendId(id);
    void Haptics.selectionAsync();
    const generation = ++calloutGeneration.current;
    if (calloutTimeout.current) clearTimeout(calloutTimeout.current);
    if (displayedFriendRef.current === id) {
      setCalloutAccessible(true);
      calloutOpacity.set(withTiming(1, { duration: reduceMotion ? 0 : 100, easing: Easing.out(Easing.quad) }));
      return;
    }
    setCalloutAccessible(false);
    if (reduceMotion) {
      displayedFriendRef.current = id;
      setDisplayedFriendId(id);
      calloutOpacity.set(withTiming(0.84, { duration: 120, easing: Easing.out(Easing.quad) }));
      calloutTimeout.current = setTimeout(() => {
        if (generation !== calloutGeneration.current) return;
        calloutOpacity.set(withTiming(1, { duration: 100, easing: Easing.out(Easing.quad) }));
        setCalloutAccessible(true);
      }, 120);
      return;
    }
    calloutOpacity.set(withTiming(0, { duration: 70, easing: Easing.out(Easing.quad) }));
    calloutTimeout.current = setTimeout(() => {
      if (generation !== calloutGeneration.current) return;
      displayedFriendRef.current = id;
      setDisplayedFriendId(id);
      calloutOpacity.set(withTiming(1, { duration: 100, easing: Easing.out(Easing.quad) }));
      calloutTimeout.current = setTimeout(() => {
        if (generation === calloutGeneration.current) setCalloutAccessible(true);
      }, 100);
    }, 70);
  }, [calloutOpacity, interrupt, reduceMotion]);

  const changeTab = useCallback((index: number) => {
    interrupt();
    setSelectedTab(index);
  }, [interrupt]);

  const changeNav = useCallback((index: number) => {
    interrupt();
    setSelectedNav(index);
  }, [interrupt]);

  const handleRootLayout = useCallback((event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    hasRootLayout.current = true;
    setViewport(previous => Math.abs(previous.width - width) < 0.5 && Math.abs(previous.height - height) < 0.5 ? previous : { width, height });
  }, []);

  const setSearchFocus = useCallback((focused: boolean) => {
    searchFocusedRef.current = focused;
    setSearchFocused(focused);
    if (focused) pauseForOverlay();
    else if (!alertOpen.current) resumeFromOverlay();
  }, [pauseForOverlay, resumeFromOverlay]);

  return (
    <View onLayout={handleRootLayout} style={styles.root}>
      <LinearGradient colors={['#02013A', '#02013A', '#110248']} locations={[0, 0.5, 1]} style={styles.fullBackground} />
      <StatusBar style="light" />
      <View style={{ position: 'absolute', left: (viewport.width - WIDTH) / 2, top: (viewport.height - HEIGHT) / 2, width: WIDTH, height: HEIGHT, transform: [{ scale }] }}>
        <View style={styles.stage}>
          <LinearGradient colors={['#02013A', '#02013A', '#110248']} locations={[0, 0.5, 1]} style={styles.fullBackground} />
          <AppBackdrop />

          <View style={styles.headerAvatar}>
            <ButtonFrame accessibilityLabel="Maya’s profile" onPress={() => showAlert('Maya’s walking profile.')} reduceMotion={reduceMotion} active={appActive} style={styles.headerAvatarHit}>
              <AvatarImage index={3} diameter={43} />
            </ButtonFrame>
          </View>
          <View style={styles.levelRing}>
            <Svg width={34} height={34} viewBox="0 0 34 34"><Circle cx={17} cy={17} r={15} fill="none" stroke="#D3BC00" strokeWidth={2} strokeDasharray="78 100" strokeDashoffset={-9.42} /></Svg>
            <FixedText style={styles.levelRingText}>L1</FixedText>
          </View>
          <ButtonFrame accessibilityLabel="Point balance, 3745 points" onPress={() => showAlert('Your point balance is 3745.')} reduceMotion={reduceMotion} active={appActive} style={styles.balanceButton}>
            <View style={styles.currencyCircle}><FixedText style={styles.currencyMark}>$</FixedText></View>
            <FixedText style={styles.balanceText}>3745</FixedText>
            <Ionicons name={iconName('add-circle')} size={16} color="#49405E" />
          </ButtonFrame>
          <ButtonFrame accessibilityLabel="Notifications" onPress={() => showAlert('You’re all caught up.')} reduceMotion={reduceMotion} active={appActive} style={styles.notificationButton}>
            <Ionicons name={iconName('notifications')} size={16} color="#FFFFFF" />
            <View style={styles.notificationDot} />
          </ButtonFrame>

          {['We Walk', 'Aster', 'Nimbly', 'Arc'].map((tab, index) => (
            <ButtonFrame key={tab} accessibilityLabel={tab} selected={selectedTab === index} onPress={() => changeTab(index)} reduceMotion={reduceMotion} active={appActive} style={[styles.tabButton, { left: [13.5, 96.5, 177.5, 243.5][index] }]}>
              <FixedText style={[styles.tabText, selectedTab === index && styles.tabTextSelected]}>{tab}</FixedText>
              <View style={[styles.tabUnderline, selectedTab === index && styles.tabUnderlineSelected]} />
            </ButtonFrame>
          ))}

          <View style={styles.searchBox}>
            <Ionicons name={iconName('search')} size={13} color="#837A99" />
            <TextInput
              accessibilityLabel="Search friends"
              placeholder="Search Friends"
              placeholderTextColor="#837A99"
              value={searchText}
              onChangeText={setSearchText}
              onFocus={() => setSearchFocus(true)}
              onBlur={() => setSearchFocus(false)}
              onSubmitEditing={() => { if (matchingFriends.length > 0) selectFriend(matchingFriends[0].id); }}
              returnKeyType="search"
              autoCapitalize="none"
              autoCorrect={false}
              style={styles.searchInput}
              allowFontScaling={false}
            />
            {searchText.length > 0 && (
              <ButtonFrame accessibilityLabel="Clear search" onPress={() => { interrupt(); setSearchText(''); }} reduceMotion={reduceMotion} active={appActive} style={styles.clearSearchButton}>
                <Ionicons name={iconName('close-circle')} size={14} color="#9189A5" />
              </ButtonFrame>
            )}
          </View>

          <OrbitField
            motion={motion}
            frameTime={frameTime}
            calloutOpacity={calloutOpacity}
            stageScale={scale}
            reduceMotion={reduceMotion}
            active={appActive}
            searchFocused={searchFocused}
            previewTime={previewTime}
            previewOrbit={previewOrbit}
            selectedFriendId={selectedFriendId}
            displayedFriendId={displayedFriendId}
            calloutAccessible={calloutAccessible}
            searchText={searchText}
            matchingFriends={matchingFriends}
            onFrameStart={onFrameStart}
            onSelectFriend={selectFriend}
          />

          <View style={[styles.stat, { left: 71, top: 604 }]}>
            <FixedText style={styles.statValue}>233 km</FixedText>
            <FixedText style={styles.statLabel}>Distance</FixedText>
          </View>
          <View style={[styles.stat, { left: 161, top: 604 }]}>
            <FixedText style={styles.statValue}>127</FixedText>
            <FixedText style={styles.statLabel}>Points</FixedText>
          </View>
          <View style={[styles.stat, { left: 246, top: 604 }]}>
            <FixedText style={styles.statValue}>34</FixedText>
            <FixedText style={styles.statLabel}>My Rank</FixedText>
          </View>
          <View accessible accessibilityLabel="50% completed" style={styles.progressPill}>
            <FixedText style={styles.progressText}>50% Completed</FixedText>
          </View>

          <ButtonFrame accessibilityLabel="Activity" onPress={() => showAlert('Walking summary: 233 km and 127 points.')} reduceMotion={reduceMotion} active={appActive} style={[styles.actionHit, { left: 54.5, top: 679, width: 113 }]}>
            <View style={[styles.actionPill, { width: 113 }]}><FixedText style={styles.actionText}>Activity</FixedText></View>
          </ButtonFrame>
          <ButtonFrame accessibilityLabel="Next milestone" onPress={() => showAlert('Keep walking to reach your next activity milestone.')} reduceMotion={reduceMotion} active={appActive} style={[styles.actionHit, { left: 174.5, top: 679, width: 44 }]}>
            <View style={[styles.actionPill, { width: 37 }]}><Ionicons name={iconName('flash')} size={14} color="#000000" /></View>
          </ButtonFrame>
          <ButtonFrame accessibilityLabel="Leaderboard" onPress={() => showAlert('Your current rank is 34.')} reduceMotion={reduceMotion} active={appActive} style={[styles.actionHit, { left: 225.5, top: 679, width: 113 }]}>
            <View style={[styles.actionPill, { width: 113 }]}><FixedText style={styles.actionText}>Leaderboard</FixedText></View>
          </ButtonFrame>

          <View style={styles.bottomNav}>
            {[
              ['footsteps', 'Home'], ['walk', 'Walkathons'], ['newspaper', 'Feed'], ['bag', 'Shop'],
            ].map(([symbol, label], index) => (
              <ButtonFrame key={label} accessibilityLabel={label} selected={selectedNav === index} onPress={() => changeNav(index)} reduceMotion={reduceMotion} active={appActive} style={styles.navButton}>
                <View style={styles.navIconBox}>
                  {selectedNav === index && <Svg width={36} height={36} style={styles.navGlow}><Defs><RadialGradient id={`lwpNavGlow${index}`} cx="50%" cy="50%" r="50%"><Stop offset="0%" stopColor="#FFE000" stopOpacity="0.28" /><Stop offset="100%" stopColor="#FFE000" stopOpacity="0" /></RadialGradient></Defs><Circle cx={18} cy={18} r={18} fill={`url(#lwpNavGlow${index})`} /></Svg>}
                  <Ionicons name={iconName(symbol)} size={18} color={selectedNav === index ? '#EBDC3D' : '#A29AAE'} />
                </View>
                <FixedText style={[styles.navLabel, selectedNav === index && styles.navLabelSelected]}>{label}</FixedText>
              </ButtonFrame>
            ))}
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = {
  root: { flex: 1, backgroundColor: '#02013A' } as ViewStyle,
  stage: { width: WIDTH, height: HEIGHT, overflow: 'hidden' as const },
  fullBackground: { position: 'absolute' as const, left: 0, top: 0, right: 0, bottom: 0 },
  headerAvatar: { position: 'absolute' as const, left: 16, top: 51, width: 44, height: 44 },
  headerAvatarHit: { width: 44, height: 44, alignItems: 'center' as const, justifyContent: 'center' as const },
  levelRing: { position: 'absolute' as const, left: 72, top: 56, width: 34, height: 34, alignItems: 'center' as const, justifyContent: 'center' as const },
  levelRingText: { position: 'absolute' as const, color: '#EED934', fontSize: 10, lineHeight: 12, fontWeight: '600' as const },
  balanceButton: { position: 'absolute' as const, left: 246, top: 51, width: 74, height: 44, flexDirection: 'row' as const, alignItems: 'center' as const, justifyContent: 'center' as const, gap: 5 },
  currencyCircle: { width: 16, height: 16, borderRadius: 8, backgroundColor: '#F7D80B', alignItems: 'center' as const, justifyContent: 'center' as const },
  currencyMark: { color: '#02013A', fontSize: 11, lineHeight: 13, fontWeight: '700' as const },
  balanceText: { color: '#FFFFFF', fontSize: 13, lineHeight: 16, fontWeight: '500' as const },
  notificationButton: { position: 'absolute' as const, left: 332, top: 51, width: 44, height: 44, alignItems: 'center' as const, justifyContent: 'center' as const },
  notificationDot: { position: 'absolute' as const, right: 12, top: 12, width: 4, height: 4, borderRadius: 2, backgroundColor: '#FF3B30' },
  tabButton: { position: 'absolute' as const, top: 110, width: 75, height: 44, alignItems: 'center' as const, justifyContent: 'center' as const },
  tabText: { color: '#A49CB5', fontSize: 14, lineHeight: 17, fontWeight: '400' as const },
  tabTextSelected: { color: '#FFFFFF', fontWeight: '600' as const },
  tabUnderline: { width: 40, height: 2, marginTop: 5, backgroundColor: 'transparent' },
  tabUnderlineSelected: { backgroundColor: '#F1D633' },
  searchBox: { position: 'absolute' as const, left: 17, top: 166, width: 359, height: 44, borderRadius: 4, backgroundColor: '#030020', flexDirection: 'row' as const, alignItems: 'center' as const, paddingLeft: 16, paddingRight: 2, gap: 9 },
  searchInput: { flex: 1, padding: 0, color: '#FFFFFF', fontSize: 12, lineHeight: 15 },
  clearSearchButton: { width: 44, height: 44, alignItems: 'center' as const, justifyContent: 'center' as const },
  callout: { width: 154, height: 59, borderRadius: 8, borderWidth: 1, borderColor: 'rgba(255,255,255,0.12)', backgroundColor: '#1D1B43', justifyContent: 'center' as const, paddingHorizontal: 12 },
  calloutTail: { position: 'absolute' as const, left: -10, top: 22 },
  calloutCopy: { gap: 3 },
  calloutPrimary: { color: '#E0DFEB', fontSize: 12, lineHeight: 14, fontWeight: '400' as const },
  calloutPoints: { color: '#FFFFFF', fontWeight: '700' as const },
  calloutSecondary: { color: '#BFB5D3', fontSize: 12, lineHeight: 14, fontWeight: '400' as const },
  calloutFriend: { color: '#FFFFFF', fontSize: 12, lineHeight: 14, fontWeight: '600' as const },
  noMatchCallout: { width: 154, height: 59, borderRadius: 8, borderWidth: 1, borderColor: 'rgba(255,255,255,0.12)', backgroundColor: '#1D1B43', alignItems: 'center' as const, justifyContent: 'center' as const },
  stat: { position: 'absolute' as const, width: 80, height: 44, alignItems: 'center' as const, justifyContent: 'center' as const, gap: 5 },
  statValue: { color: '#FFFFFF', fontSize: 17, lineHeight: 20, fontWeight: '700' as const },
  statLabel: { color: '#BFB5D3', fontSize: 11.5, lineHeight: 14, fontWeight: '400' as const },
  progressPill: { position: 'absolute' as const, left: 67, top: 673, width: 88, height: 10, borderRadius: 4, backgroundColor: '#342B5A', alignItems: 'center' as const, justifyContent: 'center' as const },
  progressText: { color: '#FFFFFF', fontSize: 7, lineHeight: 9, fontWeight: '400' as const },
  actionHit: { position: 'absolute' as const, height: 44, alignItems: 'center' as const, justifyContent: 'center' as const },
  actionPill: { height: 36, borderRadius: 6, backgroundColor: '#FFE000', alignItems: 'center' as const, justifyContent: 'center' as const },
  actionText: { color: '#000000', fontSize: 12, lineHeight: 14, fontWeight: '700' as const },
  bottomNav: { position: 'absolute' as const, left: 14, top: 757, width: 365, height: 74, borderRadius: 16, backgroundColor: '#32264E', flexDirection: 'row' as const, paddingHorizontal: 10, overflow: 'hidden' as const },
  navButton: { flex: 1, height: 74, alignItems: 'center' as const, justifyContent: 'center' as const, gap: 4 },
  navIconBox: { width: 18, height: 18, alignItems: 'center' as const, justifyContent: 'center' as const },
  navGlow: { position: 'absolute' as const, left: -9, top: -9 },
  navLabel: { color: '#A29AAE', fontSize: 10, lineHeight: 12, fontWeight: '400' as const },
  navLabelSelected: { color: '#EBDC3D' },
};

export default LunarWalkingPoints;


