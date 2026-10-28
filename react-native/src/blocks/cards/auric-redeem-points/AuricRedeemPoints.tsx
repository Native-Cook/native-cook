import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import {
  Canvas,
  Circle,
  Fill,
  Group,
  Image as SkiaImage,
  LinearGradient as SkiaLinearGradient,
  Mask,
  Oval,
  Paint,
  Path,
  RadialGradient as SkiaRadialGradient,
  Rect,
  Skia,
  interpolateColors,
  useImage,
  usePathValue,
  vec,
  type SkPath,
  type SkPathBuilder,
} from '@shopify/react-native-skia';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type Ref } from 'react';
import { AccessibilityInfo, Alert, AppState, BackHandler, Image, Pressable, StyleSheet, Text as RNText, View, findNodeHandle, useWindowDimensions, type TextProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { Easing, interpolate, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSequence, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

export const LOOP = 8;
const COIN_FIT_SCALE = 0.8021340693;
const COIN_FIT_OFFSET_X = -0.192986;
const COIN_FIT_OFFSET_Y = 0.831597;
const HEADPHONES_FIT_SCALE = 0.9448726501;
const HEADPHONES_FIT_OFFSET_X = 0.2;
const HEADPHONES_FIT_OFFSET_Y = 1.8963235294;
const SNEAKERS_FIT_SCALE = 1.0373679775;
const SNEAKERS_FIT_OFFSET_X = -3.802741573;
const SNEAKERS_FIT_OFFSET_Y = -4.887213483;

type Pose = {
  offsetX: number; lift: number; yaw: number; pitch: number; roll: number; scaleX: number; scaleY: number;
  sweep: number; light: number; morph: number; streak: number; eventAge: number;
};
type Velocity = { offsetX: number; lift: number; yaw: number; pitch: number; roll: number; scaleX: number; scaleY: number };
type Contact = { origin: Pose; startedAt: number; x: number; y: number; velocityX: number; velocityY: number };
type Release = { origin: Pose; startedAt: number; velocity: Velocity; celebrate: boolean };
type Props = { autoPlay?: boolean; previewTime?: number; previewDetailProgress?: number };

function FixedText(props: TextProps) {
  return <RNText {...props} allowFontScaling={false} />;
}

function usePillPressFeedback(reduceMotion: boolean) {
  const progress = useSharedValue(0);
  const style = useAnimatedStyle(() => ({
    opacity: 1 - 0.12 * progress.get(),
    transform: [{ scale: reduceMotion ? 1 : 1 - 0.025 * progress.get() }],
  }), [reduceMotion]);
  const onPressIn = useCallback(() => {
    progress.set(withTiming(1, { duration: 120, easing: Easing.out(Easing.quad) }));
  }, [progress]);
  const onPressOut = useCallback(() => {
    progress.set(withTiming(0, { duration: 100, easing: Easing.out(Easing.quad) }));
  }, [progress]);
  return { style, onPressIn, onPressOut };
}

const REST: Pose = { offsetX: 0, lift: 0, yaw: 0, pitch: 0, roll: 0, scaleX: 1, scaleY: 1, sweep: -1, light: 0, morph: 0, streak: 0, eventAge: -1 };
const ZERO_VELOCITY: Velocity = { offsetX: 0, lift: 0, yaw: 0, pitch: 0, roll: 0, scaleX: 0, scaleY: 0 };
const clamp = (value: number, low = 0, high = 1) => { 'worklet'; return Math.min(high, Math.max(low, value)); };
const smooth = (value: number) => { 'worklet'; const t = clamp(value); return t * t * (3 - 2 * t); };
const pulse = (time: number, start: number, duration: number) => { 'worklet'; const t = (time - start) / duration; return t > 0 && t < 1 ? Math.sin(Math.PI * t) ** 2 : 0; };
const rubber = (value: number, limit: number) => {
  'worklet'; const amount = Math.abs(value); const excess = Math.max(0, amount - limit);
  return (value < 0 ? -1 : 1) * (Math.min(amount, limit) + 4 * excess / (excess + 20));
};
const rubberDerivative = (value: number, limit: number) => { 'worklet'; const excess = Math.abs(value) - limit; return excess <= 0 ? 1 : 80 / (excess + 20) ** 2; };
const dragAngle = (origin: number, displacement: number, limit: number, hardLimit: number) => {
  'worklet'; return clamp(origin + rubber(origin + displacement, limit) - rubber(origin, limit), -hardLimit, hardLimit);
};
const dragVelocity = (origin: number, displacement: number, velocity: number, limit: number, hardLimit: number) => {
  'worklet'; const raw = origin + rubber(origin + displacement, limit) - rubber(origin, limit);
  return Math.abs(raw) >= hardLimit ? 0 : velocity * rubberDerivative(origin + displacement, limit);
};
const spring = (time: number, from: number, velocity = 0) => {
  'worklet';
  if (time >= 1.2) return 0;
  const omega = 16; const damping = 0.8; const decay = omega * damping; const frequency = omega * Math.sqrt(1 - damping * damping);
  return Math.exp(-decay * time) * (from * Math.cos(frequency * time) + (velocity + decay * from) / frequency * Math.sin(frequency * time));
};

function releasedPose(time: number, origin: Pose, velocity: Velocity, celebrate: boolean): Pose {
  'worklet';
  const t = Math.max(0, time); const flash = celebrate ? pulse(t, 0.04, 0.38) : 0;
  const liftPulse = celebrate ? pulse(t, 0, 0.66) : 0;
  const expansion = celebrate ? 0.055 * pulse(t, 0.04, 0.48) : 0;
  return {
    offsetX: spring(t, origin.offsetX, clamp(velocity.offsetX, -240, 240)),
    lift: spring(t, origin.lift, clamp(velocity.lift, -160, 160)) - 16 * liftPulse,
    yaw: clamp(spring(t, origin.yaw, clamp(velocity.yaw, -280, 280)), -24, 24),
    pitch: clamp(spring(t, origin.pitch, clamp(velocity.pitch, -160, 160)), -14, 14),
    roll: spring(t, origin.roll, clamp(velocity.roll, -160, 160)),
    scaleX: 1 + spring(t, origin.scaleX - 1, velocity.scaleX) + expansion,
    scaleY: 1 + spring(t, origin.scaleY - 1, velocity.scaleY) + expansion,
    sweep: origin.sweep + (1 - origin.sweep) * smooth(t / 0.38),
    light: Math.max(origin.light * (1 - smooth(t / 0.42)), 0.45 * flash),
    morph: origin.morph * (1 - smooth(t / 0.42)) + 0.65 * flash,
    streak: origin.streak * (1 - smooth(t / 0.48)) + 0.8 * flash,
    eventAge: celebrate ? t : -1,
  };
}

function previewPose(time: number): Pose {
  'worklet';
  const t = ((Math.max(0, time) % LOOP) + LOOP) % LOOP;
  if (t < 0.8) return { ...REST };
  const compressed: Pose = { ...REST, offsetX: -4, lift: 2, yaw: -10, roll: -8, scaleX: 1.045, scaleY: 0.9, morph: 0.7, streak: 0.4 };
  if (t < 0.96) {
    const progress = smooth((t - 0.8) / 0.16);
    return { ...compressed, offsetX: -4 * progress, lift: 2 * progress, yaw: -10 * progress, roll: -8 * progress, scaleX: 1 + 0.045 * progress, scaleY: 1 - 0.1 * progress, morph: 0.7 * progress, streak: 0.4 * progress };
  }
  if (t < 2.16) return releasedPose(t - 0.96, compressed, { offsetX: 120, lift: 0, yaw: 240, pitch: 0, roll: 120, scaleX: 0, scaleY: 0 }, true);
  if (t < 6.8) {
    const u = (t - 2.16) / 4.64; const envelope = Math.sin(Math.PI * u) ** 2; const turn = Math.sin(2 * Math.PI * u) * envelope;
    return { ...REST, offsetX: 18 * turn, lift: -10 * envelope, yaw: 24 * turn, pitch: 5 * envelope, roll: 12 * turn, scaleX: 1 + 0.025 * envelope, scaleY: 1 + 0.025 * envelope, sweep: turn, light: 0.34 * envelope, morph: 0.65 * envelope, streak: 0.35 * envelope };
  }
  return { ...REST };
}

function pressedPose(contact: Contact, now: number): Pose {
  'worklet';
  const progress = 1 - (1 - clamp((now - contact.startedAt) / 0.1)) ** 3;
  const p = { ...contact.origin };
  p.scaleX += (1.045 - p.scaleX) * progress;
  p.scaleY += (0.9 - p.scaleY) * progress;
  p.offsetX = dragAngle(contact.origin.offsetX, contact.x * 0.28, 20, 24);
  p.lift = dragAngle(contact.origin.lift, contact.y * 0.18, 14, 18);
  p.yaw = dragAngle(contact.origin.yaw, contact.x * 0.32, 20, 24);
  p.pitch = dragAngle(contact.origin.pitch, -contact.y * 0.13, 10, 14);
  p.roll = dragAngle(contact.origin.roll, contact.x * 0.08, 8, 12);
  p.sweep += (p.yaw / 24 - p.sweep) * progress;
  p.light += (0.3 - p.light) * progress;
  p.morph += (0.65 - p.morph) * progress;
  p.streak += (0.4 - p.streak) * progress;
  p.eventAge = contact.origin.eventAge >= 0 ? contact.origin.eventAge + now - contact.startedAt : -1;
  return p;
}

function pressedVelocity(contact: Contact, now: number, velocityX: number, velocityY: number): Velocity {
  'worklet';
  const u = clamp((now - contact.startedAt) / 0.1);
  const compressionSpeed = u < 1 ? 30 * (1 - u) ** 2 : 0;
  return {
    offsetX: dragVelocity(contact.origin.offsetX, contact.x * 0.28, velocityX * 0.28, 20, 24),
    lift: dragVelocity(contact.origin.lift, contact.y * 0.18, velocityY * 0.18, 14, 18),
    yaw: dragVelocity(contact.origin.yaw, contact.x * 0.32, velocityX * 0.32, 20, 24),
    pitch: dragVelocity(contact.origin.pitch, -contact.y * 0.13, -velocityY * 0.13, 10, 14),
    roll: dragVelocity(contact.origin.roll, contact.x * 0.08, velocityX * 0.08, 8, 12),
    scaleX: (1.045 - contact.origin.scaleX) * compressionSpeed,
    scaleY: (0.9 - contact.origin.scaleY) * compressionSpeed,
  };
}

function updateContact(contact: SharedValue<Contact | null>, pose: SharedValue<Pose>, frameTime: SharedValue<number>, x: number, y: number, velocityX: number, velocityY: number) {
  'worklet'; const current = contact.get(); if (!current) return;
  const next = { ...current, x, y, velocityX, velocityY };
  contact.set(next); pose.set(pressedPose(next, frameTime.get()));
}

function finishContact(contact: SharedValue<Contact | null>, release: SharedValue<Release | null>, pose: SharedValue<Pose>, frameTime: SharedValue<number>, x: number, y: number, velocityX: number, velocityY: number, celebrate: boolean) {
  'worklet'; const current = contact.get(); if (!current) return;
  const next = { ...current, x, y, velocityX, velocityY };
  const origin = pressedPose(next, frameTime.get());
  const velocity = pressedVelocity(next, frameTime.get(), velocityX, velocityY);
  contact.set(null); pose.set(origin);
  release.set({ origin, startedAt: frameTime.get(), velocity: celebrate ? velocity : pressedVelocity(next, frameTime.get(), 0, 0), celebrate });
}

function cancelContact(contact: SharedValue<Contact | null>, release: SharedValue<Release | null>, pose: SharedValue<Pose>, frameTime: SharedValue<number>) {
  'worklet'; const current = contact.get(); if (!current) return;
  finishContact(contact, release, pose, frameTime, current.x, current.y, 0, 0, false);
}

function interruptMotion(contact: SharedValue<Contact | null>, release: SharedValue<Release | null>, pose: SharedValue<Pose>, frameTime: SharedValue<number>) {
  'worklet';
  const origin = pose.get();
  contact.set(null);
  release.set({ origin, startedAt: frameTime.get(), velocity: { ...ZERO_VELOCITY }, celebrate: false });
}

function activatePreview(contact: SharedValue<Contact | null>, release: SharedValue<Release | null>, frameTime: SharedValue<number>) {
  'worklet';
  contact.set(null);
  release.set({ origin: { ...REST, offsetX: -4, lift: 2, yaw: -10, roll: -8, scaleX: 1.045, scaleY: 0.9, morph: 0.7, streak: 0.4 }, startedAt: frameTime.get(), velocity: { offsetX: 0, lift: 0, yaw: 0, pitch: 0, roll: 0, scaleX: 0, scaleY: 0 }, celebrate: true });
}

function resetMotion(pose: SharedValue<Pose>, contact: SharedValue<Contact | null>, release: SharedValue<Release | null>, pausedPose: SharedValue<Pose | null>, elapsed: SharedValue<number>, frameTime: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet';
  elapsed.set(0); contact.set(null); release.set(null); pausedPose.set(null); frameTime.set(0);
  pose.set(reduced ? { ...REST } : previewTime == null ? { ...REST } : previewPose(previewTime));
}

function pauseObscuredMotion(pose: SharedValue<Pose>, contact: SharedValue<Contact | null>, release: SharedValue<Release | null>, pausedPose: SharedValue<Pose | null>) {
  'worklet';
  pausedPose.set({ ...pose.get() });
  contact.set(null);
  release.set(null);
}

function resumeObscuredMotion(pose: SharedValue<Pose>, contact: SharedValue<Contact | null>, release: SharedValue<Release | null>, pausedPose: SharedValue<Pose | null>, frameTime: SharedValue<number>) {
  'worklet';
  const saved = pausedPose.get();
  const origin = saved ? { ...saved } : { ...REST };
  pausedPose.set(null);
  contact.set(null);
  pose.set(origin);
  release.set({ origin, startedAt: frameTime.get(), velocity: { ...ZERO_VELOCITY }, celebrate: false });
}

function buildMorphPath(path: SkPathBuilder, x: number, y: number, width: number, height: number, amount: number, rotation = 0, shapeScale = 1) {
  'worklet';
  path.reset();
  const cx = x + width / 2; const cy = y + height / 2; const dx = width * 0.5 * clamp(amount); const dy = height * 0.5 * clamp(amount);
  const angle = rotation * Math.PI / 180; const c = Math.cos(angle); const s = Math.sin(angle);
  const tx = (px: number, py: number) => {
    const sx = (px - cx) * shapeScale; const sy = (py - cy) * shapeScale;
    return { x: cx + sx * c - sy * s, y: cy + sx * s + sy * c };
  };
  const top = tx(cx, y); const right = tx(x + width, cy); const bottom = tx(cx, y + height); const left = tx(x, cy);
  const ctrls = [tx(cx + dx, cy - dy), tx(cx + dx, cy + dy), tx(cx - dx, cy + dy), tx(cx - dx, cy - dy)];
  path.moveTo(top.x, top.y);
  path.quadTo(ctrls[0].x, ctrls[0].y, right.x, right.y);
  path.quadTo(ctrls[1].x, ctrls[1].y, bottom.x, bottom.y);
  path.quadTo(ctrls[2].x, ctrls[2].y, left.x, left.y);
  path.quadTo(ctrls[3].x, ctrls[3].y, top.x, top.y);
  path.close();
}

function starPath(x: number, y: number, width: number, height: number): SkPath {
  const path = Skia.PathBuilder.Make(); const cx = x + width / 2; const cy = y + height / 2; const dx = width * 0.04; const dy = height * 0.04;
  path.moveTo(cx, y); path.quadTo(cx + dx, cy - dy, x + width, cy); path.quadTo(cx + dx, cy + dy, cx, y + height);
  path.quadTo(cx - dx, cy + dy, x, cy); path.quadTo(cx - dx, cy - dy, cx, y); path.close(); return path.detach();
}

function SourceSpark({ pose, x, y, width, height, pulseStart, rotation = 0 }: { pose: SharedValue<Pose>; x: number; y: number; width: number; height: number; pulseStart?: number; rotation?: number }) {
  const path = usePathValue((builder) => {
    'worklet';
    const pulseAmount = pulseStart == null ? 0 : pulse(pose.get().eventAge, pulseStart, 0.3);
    buildMorphPath(builder, x, y, width, height, 0.08 + pulseAmount * 0.14, rotation * pulseAmount * 10, 1 + pulseAmount * 0.22);
  }, Skia.Path.Make());
  return <Path path={path}>
    <SkiaLinearGradient start={vec(x, y)} end={vec(x + width, y + height)} colors={['#FBE078', '#FBA534']} />
  </Path>;
}

function LegacyMintReflection({ pose }: { pose: SharedValue<Pose> }) {
  const reflectionOpacity = useDerivedValue(() => pose.get().light * 0.3);
  return <Canvas style={styles.heroCanvas} pointerEvents="none">
    <Oval x={30} y={42} width={160} height={150} opacity={reflectionOpacity}>
      <SkiaRadialGradient c={vec(110, 117)} r={76} colors={['#FFAA33', '#FFAA3300']} />
    </Oval>
  </Canvas>;
}

function ReflectedAura({ pose }: { pose: SharedValue<Pose> }) {
  const center = useDerivedValue(() => {
    const p = pose.get();
    return vec(105 + 210 * (0.08 * p.yaw / 24), 105);
  });
  const movement = useAnimatedStyle(() => {
    const p = pose.get();
    const charge = smooth(p.light / 0.45);
    return { opacity: charge, transform: [{ translateX: p.offsetX * 0.15 }, { translateY: 7 + 0.2 * p.lift }] };
  });
  const scale = useAnimatedStyle(() => {
    const charge = smooth(pose.get().light / 0.45);
    return { transform: [{ scale: 1 + 0.04 * charge }] };
  });
  return <Animated.View pointerEvents="none" style={[styles.auraOuter, { transform: [{ translateY: -4 }] }]}>
    <Animated.View style={[styles.auraSize, movement]}>
      <Animated.View style={[styles.auraSize, scale]}>
        <Canvas style={styles.auraCanvas} pointerEvents="none">
          <Circle cx={105} cy={105} r={105}>
            <SkiaRadialGradient
              c={center}
              r={86}
              colors={['#FFD35A00', '#FFD35A3D', '#F297241F', '#F2972400']}
              positions={[0, 0.55, 0.8, 1]}
            />
          </Circle>
        </Canvas>
      </Animated.View>
    </Animated.View>
  </Animated.View>;
}

function MintCanvas({ pose }: { pose: SharedValue<Pose> }) {
  const coin = useImage(require('./assets/coin.png'));
  const coinFitSize = 180 * COIN_FIT_SCALE;
  const coinFitX = 110 - coinFitSize / 2 + COIN_FIT_OFFSET_X;
  const coinFitY = 120 - coinFitSize / 2 + COIN_FIT_OFFSET_Y;
  const sweep = useDerivedValue(() => [{ translateX: pose.get().sweep * 105 }]);
  const lightOpacity = useDerivedValue(() => pose.get().light);
  const bright = usePathValue((builder) => {
    'worklet';
    const p = pose.get();
    const width = 9 + 3 * p.streak; const height = 16 + 12 * p.streak;
    const cx = 110 + 39 + 4 * p.streak; const cy = 120 - 51 + 6 * p.streak;
    buildMorphPath(builder, cx - width / 2, cy - height / 2, width, height, p.morph, 22 + 10 * p.streak);
  }, Skia.Path.Make());

  return <Canvas style={styles.heroCanvas} pointerEvents="none">
    {coin && <SkiaImage image={coin} x={coinFitX} y={coinFitY} width={coinFitSize} height={coinFitSize} fit="contain" />}
    <Group layer={<Paint blendMode="screen" />}>
      <Mask mode="alpha" clip={false} mask={coin ? <SkiaImage image={coin} x={coinFitX} y={coinFitY} width={coinFitSize} height={coinFitSize} fit="contain" /> : <Rect x={coinFitX} y={coinFitY} width={coinFitSize} height={coinFitSize} color="transparent" />}>
        <Group transform={sweep} opacity={lightOpacity}>
          <Rect x={-85} y={25} width={390} height={190}>
            <SkiaLinearGradient start={vec(20, 25)} end={vec(200, 215)} colors={['#FFF0B800', '#FFF0B8', '#FFF0B800']} positions={[0.455, 0.5, 0.545]} />
          </Rect>
        </Group>
      </Mask>
    </Group>
    <Path path={bright} color="#FFFFFF" />
  </Canvas>;
}

function TransientGlint({ pose, index }: { pose: SharedValue<Pose>; index: number }) {
  const cx = useDerivedValue(() => {
    const p = pose.get(); const life = (p.eventAge - (0.17 + index * 0.018)) / 0.28;
    const angle = (-140 + index * 29) * Math.PI / 180; const distance = 54 + 17 * smooth(life);
    return 110 + Math.cos(angle) * distance;
  });
  const cy = useDerivedValue(() => {
    const p = pose.get(); const life = (p.eventAge - (0.17 + index * 0.018)) / 0.28;
    const angle = (-140 + index * 29) * Math.PI / 180; const distance = 54 + 17 * smooth(life);
    return 110 + Math.sin(angle) * distance - 4;
  });
  const radius = useDerivedValue(() => {
    const age = pose.get().eventAge; const life = (age - (0.17 + index * 0.018)) / 0.28;
    return life > 0 && life < 1 ? (0.7 + (index % 3) * 0.3) * Math.sin(Math.PI * life) : 0;
  });
  const opacity = useDerivedValue(() => {
    const age = pose.get().eventAge; const life = (age - (0.17 + index * 0.018)) / 0.28;
    return life > 0 && life < 1 ? 0.55 * Math.sin(Math.PI * life) : 0;
  });
  return <Circle cx={cx} cy={cy} r={radius} color="#FFE6A0" opacity={opacity} />;
}

function HeroStars({ pose }: { pose: SharedValue<Pose> }) {
  return <Canvas pointerEvents="none" style={styles.starsCanvas}>
    <SourceSpark pose={pose} x={19} y={74.8} width={26} height={36.4} pulseStart={0.15} rotation={-1} />
    <SourceSpark pose={pose} x={174} y={103} width={30} height={42} pulseStart={0.19} rotation={1} />
    <Path path={starPath(29.5, 142.5, 5, 7)}><SkiaLinearGradient start={vec(29, 142)} end={vec(35, 150)} colors={['#FBE078', '#FBA534']} /></Path>
    <Path path={starPath(70.5, 195.5, 5, 7)}><SkiaLinearGradient start={vec(70, 195)} end={vec(76, 203)} colors={['#FBE078', '#FBA534']} /></Path>
    <Path path={starPath(164.5, 182.5, 5, 7)}><SkiaLinearGradient start={vec(164, 182)} end={vec(170, 190)} colors={['#FBE078', '#FBA534']} /></Path>
  </Canvas>;
}

function GlintOverlay({ pose }: { pose: SharedValue<Pose> }) {
  return <Canvas pointerEvents="none" style={styles.starsCanvas}>
    {[0, 1, 2, 3, 4, 5].map(index => <TransientGlint key={index} pose={pose} index={index} />)}
  </Canvas>;
}

function MintArtwork({ pose, feedback }: { pose: SharedValue<Pose>; feedback: SharedValue<number> }) {
  const transform = useAnimatedStyle(() => {
    const p = pose.get();
    return {
      opacity: feedback.get(),
      transform: [
        { perspective: 760 }, { scaleX: p.scaleX }, { scaleY: p.scaleY }, { rotateZ: `${p.roll}deg` },
        { rotateY: `${p.yaw}deg` }, { rotateX: `${p.pitch}deg` }, { translateX: p.offsetX }, { translateY: p.lift - 4 },
      ],
    };
  });
  return <>
    <Animated.View style={[styles.coinBody, transform]}>
      <LegacyMintReflection pose={pose} />
    </Animated.View>
    <ReflectedAura pose={pose} />
    <Animated.View style={[styles.coinBody, transform]}>
      <MintCanvas pose={pose} />
    </Animated.View>
    <HeroStars pose={pose} />
    <GlintOverlay pose={pose} />
  </>;
}

function MintStage({ autoPlay, previewTime, reduceMotion, active, isObscured, interruption, stageScale }: Props & { reduceMotion: boolean; active: boolean; isObscured: boolean; interruption: number; stageScale: number }) {
  const pose = useSharedValue<Pose>({ ...REST });
  const contact = useSharedValue<Contact | null>(null);
  const release = useSharedValue<Release | null>(null);
  const pausedPose = useSharedValue<Pose | null>(null);
  const elapsed = useSharedValue(0);
  const frameTime = useSharedValue(0);
  const feedback = useSharedValue(1);
  const previousObscured = useRef(false);
  const frame = useFrameCallback((info) => {
    if (isObscured) return;
    const dt = Math.max(0, (info.timeSincePreviousFrame ?? 0) / 1000);
    frameTime.set(frameTime.get() + dt);
    if (reduceMotion) { pose.set({ ...REST }); contact.set(null); release.set(null); elapsed.set(0); return; }
    const touch = contact.get();
    if (touch) { pose.set(pressedPose(touch, frameTime.get())); return; }
    const released = release.get();
    if (released) {
      const age = frameTime.get() - released.startedAt;
      pose.set(releasedPose(age, released.origin, released.velocity, released.celebrate));
      if (age >= 2) { release.set(null); elapsed.set(0); pose.set({ ...REST }); }
      return;
    }
    if (previewTime != null) { pose.set(previewPose(previewTime)); return; }
    if (autoPlay) { elapsed.set((elapsed.get() + dt) % LOOP); pose.set(previewPose(elapsed.get())); }
    else pose.set({ ...REST });
  }, false);

  useEffect(() => {
    frame.setActive(active && !reduceMotion && !isObscured);
  }, [active, frame, isObscured, reduceMotion]);

  useEffect(() => {
    scheduleOnUI(resetMotion, pose, contact, release, pausedPose, elapsed, frameTime, previewTime, reduceMotion);
  }, [active, contact, elapsed, frameTime, pausedPose, pose, previewTime, reduceMotion, release]);

  useEffect(() => {
    if (previousObscured.current === isObscured) return;
    previousObscured.current = isObscured;
    if (isObscured) {
      frame.setActive(false);
      scheduleOnUI(pauseObscuredMotion, pose, contact, release, pausedPose);
    } else if (!active || reduceMotion) {
      scheduleOnUI(resetMotion, pose, contact, release, pausedPose, elapsed, frameTime, previewTime, reduceMotion);
    } else {
      scheduleOnUI(resumeObscuredMotion, pose, contact, release, pausedPose, frameTime);
      frame.setActive(true);
    }
  }, [active, contact, elapsed, frame, frameTime, isObscured, pausedPose, pose, previewTime, reduceMotion, release]);

  useEffect(() => {
    if (interruption !== 0 && !isObscured) {
      scheduleOnUI(interruptMotion, contact, release, pose, frameTime);
      feedback.set(withDelay(150, withTiming(1, { duration: 150 })));
    }
  }, [contact, feedback, frameTime, interruption, isObscured, pose, release]);

  const lightImpact = useCallback(() => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const activate = useCallback(() => {
    if (isObscured) return;
    if (reduceMotion) {
      feedback.set(withSequence(withTiming(0.84, { duration: 120 }), withDelay(150, withTiming(1, { duration: 150 }))));
      return;
    }
    scheduleOnUI(activatePreview, contact, release, frameTime);
  }, [contact, feedback, frameTime, isObscured, reduceMotion, release]);

  const pan = useMemo(() => Gesture.Pan().minDistance(0).averageTouches(true).enabled(active && !isObscured)
    .onBegin(() => {
      const time = frameTime.get();
      if (isObscured) return;
      if (reduceMotion) { feedback.set(withTiming(0.84, { duration: 120 })); return; }
      contact.set({ origin: pose.get(), startedAt: time, x: 0, y: 0, velocityX: 0, velocityY: 0 });
      release.set(null);
    })
    .onUpdate((event) => {
      if (isObscured) return;
      if (reduceMotion) return;
      updateContact(contact, pose, frameTime, event.translationX / stageScale, event.translationY / stageScale, event.velocityX / stageScale, event.velocityY / stageScale);
    })
    .onEnd((event) => {
      if (isObscured) return;
      if (reduceMotion) { feedback.set(withDelay(150, withTiming(1, { duration: 150 }))); return; }
      finishContact(contact, release, pose, frameTime, event.translationX / stageScale, event.translationY / stageScale, event.velocityX / stageScale, event.velocityY / stageScale, true);
      scheduleOnRN(lightImpact);
    })
    .onFinalize((_event, success) => {
      if (isObscured) return;
      if (!success && !reduceMotion) cancelContact(contact, release, pose, frameTime);
      else if (!success && reduceMotion) feedback.set(withDelay(150, withTiming(1, { duration: 150 })));
    }), [active, contact, feedback, frameTime, isObscured, lightImpact, pose, reduceMotion, release, stageScale]);

  const accessibilityActivate = useCallback(() => activate(), [activate]);

  return <GestureDetector gesture={pan}>
    <Animated.View
      accessible
      accessibilityRole="button"
      accessibilityLabel="Gold rewards coin"
      accessibilityHint="Activate to preview the gold finish. Your points do not change."
      accessibilityActions={[{ name: 'activate' }]}
      onAccessibilityAction={accessibilityActivate}
      style={styles.heroHitbox}
    >
      <View pointerEvents="none" style={styles.heroOverflow}>
        <MintArtwork pose={pose} feedback={feedback} />
      </View>
    </Animated.View>
  </GestureDetector>;
}

function RewardCard({ expired, onRedeem, accessibilityRef, animatedStyle, reduceMotion }: { expired: boolean; onRedeem: () => void; accessibilityRef?: Ref<View>; animatedStyle?: object; reduceMotion: boolean }) {
  const pressFeedback = usePillPressFeedback(reduceMotion);
  return <Animated.View style={[styles.rewardCard, expired ? styles.expiredCard : styles.activeCard, animatedStyle]}>
    {!expired && <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Rect x={0} y={0} width={177} height={193}>
        <SkiaRadialGradient c={vec(86.7, 54)} r={125} colors={['#C88629B3', '#C8862900']} positions={[0, 1]} />
      </Rect>
    </Canvas>}
    <Image source={expired ? require('./assets/sneakers.png') : require('./assets/headphones.png')} resizeMode="contain" style={expired ? styles.sneakers : styles.headphones} accessibilityIgnoresInvertColors />
    <FixedText style={[styles.productName, expired && styles.expiredText]}>{expired ? 'Aster Sneakers' : 'Aural Headphones'}</FixedText>
    <View style={styles.rewardPrice}>
      <FixedText style={[styles.rewardPoints, expired && styles.expiredText]}>{expired ? '450' : '600'}</FixedText>
      <FixedText style={[styles.rewardPts, expired && styles.expiredText]}>pts</FixedText>
    </View>
    <Pressable ref={accessibilityRef} disabled={expired} onPress={onRedeem} onPressIn={expired ? undefined : pressFeedback.onPressIn} onPressOut={expired ? undefined : pressFeedback.onPressOut} accessibilityRole="button" accessibilityLabel={expired ? 'Sneaker reward expired' : 'Redeem headphones for 600 points'} style={styles.rewardButtonHitbox}>
      <Animated.View style={[styles.rewardButton, !expired && pressFeedback.style]}>
        <LinearGradient colors={expired ? ['rgba(189,136,75,0.25)', 'rgba(189,136,75,0.25)'] : ['#F3A82F', '#FAC672', '#FED99B']} locations={expired ? [0, 1] : [0, 0.6, 1]} style={StyleSheet.absoluteFill} />
        <FixedText style={[styles.rewardButtonText, expired && styles.expiredText]}>{expired ? 'Expired' : 'Redeem'}</FixedText>
      </Animated.View>
    </Pressable>
  </Animated.View>;
}

function GoldTier({ color, icon, label, left }: { color: string; icon: 'star' | 'trophy'; label: string; left: number }) {
  return <View style={[styles.tierItem, { left }]}>
    <View style={[styles.tierIcon, { backgroundColor: color }]}><Ionicons name={icon === 'trophy' ? 'trophy' : 'star'} size={8} color="rgba(255,255,255,0.65)" /></View>
    <FixedText style={styles.tierName}>{label}</FixedText>
  </View>;
}

function RewardDetail({ progress, reduceMotion, onClose, onEscape, closeRef, viewportWidth, viewportHeight }: { progress: SharedValue<number>; reduceMotion: boolean; onClose: () => void; onEscape: () => void; closeRef: Ref<View>; viewportWidth: number; viewportHeight: number }) {
  const [actionEnabled, setActionEnabled] = useState(false);
  const [actionAccessible, setActionAccessible] = useState(false);
  useAnimatedReaction(() => reduceMotion || smooth((progress.get() - 0.55) / 0.35) > 0.5, (value, previous) => {
    if (value !== previous) scheduleOnRN(setActionEnabled, value);
  }, [progress, reduceMotion]);
  useAnimatedReaction(() => reduceMotion || smooth((progress.get() - 0.55) / 0.35) > 0.8, (value, previous) => {
    if (value !== previous) scheduleOnRN(setActionAccessible, value);
  }, [progress, reduceMotion]);
  const stageScale = Math.min(viewportWidth / 393, viewportHeight / 852);
  const stageOriginX = (viewportWidth - 393 * stageScale) / 2;
  const stageOriginY = (viewportHeight - 852 * stageScale) / 2;
  const surfaceMask = usePathValue((builder) => {
    'worklet';
    const p = reduceMotion ? 1 : progress.get();
    const x = 17.5 + (17 - 17.5) * p; const y = 447.5 + (442 - 447.5) * p;
    const w = 177 + (359 - 177) * p; const h = 193 + (348 - 193) * p; const r = 26 + (32 - 26) * p;
    builder.reset(); builder.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r));
  }, Skia.Path.Make());
  const surfaceStyle = useAnimatedStyle(() => {
    const p = progress.get(); const g = reduceMotion ? 1 : p;
    const scaleX = interpolate(g, [0, 1], [177 / 359, 1]);
    const scaleY = interpolate(g, [0, 1], [193 / 348, 1]);
    const radius = interpolate(g, [0, 1], [26, 32]) / Math.sqrt(scaleX * scaleY);
    return { transform: [
      { translateX: interpolate(g, [0, 1], [0.5, 0]) }, { translateY: interpolate(g, [0, 1], [5.5, 0]) },
      { scaleX }, { scaleY },
    ], borderRadius: radius };
  });
  const surfaceTintColor = useDerivedValue(() => {
    const g = reduceMotion ? 1 : progress.get();
    return interpolateColors(g, [0, 1], ['rgba(137,60,88,0.4)', '#371044']);
  });
  const glowCenter = useDerivedValue(() => {
    const g = reduceMotion ? 1 : progress.get();
    const x = 17.5 + (17 - 17.5) * g; const y = 447.5 + (442 - 447.5) * g;
    const width = 177 + (359 - 177) * g; const height = 193 + (348 - 193) * g;
    return vec(x + width * 0.49, y + height * 0.28);
  });
  const glowRadius = useDerivedValue(() => interpolate(reduceMotion ? 1 : progress.get(), [0, 1], [125, 220]));
  const glowOpacity = useDerivedValue(() => interpolate(reduceMotion ? 1 : progress.get(), [0, 1], [0.7, 0.45]));
  const artStyle = useAnimatedStyle(() => {
    const g = reduceMotion ? 1 : progress.get();
    const sourceScale = interpolate(g, [0, 1], [1 / 1.4, 1]);
    return { transform: [
      { translateX: -90.5 * (1 - g) + HEADPHONES_FIT_OFFSET_X * 1.4 * sourceScale },
      { translateY: -40.5 * (1 - g) + HEADPHONES_FIT_OFFSET_Y * 1.4 * sourceScale },
      { scale: HEADPHONES_FIT_SCALE * sourceScale },
    ] };
  });
  const productNamePosition = useAnimatedStyle(() => {
    const g = reduceMotion ? 1 : progress.get();
    const centerX = 106 + (196.5 - 106) * g;
    const top = 542.5 + (596 - 542.5) * g;
    return { transform: [{ translateX: centerX - 106 }, { translateY: top - 542.5 }] };
  });
  const productNameScale = useAnimatedStyle(() => {
    const g = reduceMotion ? 1 : progress.get();
    return { transform: [{ scale: 1 + 0.6 * g }] };
  });
  const priceStyle = useAnimatedStyle(() => {
    const g = reduceMotion ? 1 : progress.get();
    return { transform: [{ translateX: -90.5 * (1 - g) }, { translateY: -69.5 * (1 - g) }] };
  });
  const actionStyle = useAnimatedStyle(() => {
    const g = reduceMotion ? 1 : progress.get();
    return { transform: [{ translateX: -90.5 * (1 - g) }, { translateY: -127 * (1 - g) }, { scaleX: interpolate(g, [0, 1], [132 / 315, 1]) }, { scaleY: interpolate(g, [0, 1], [29 / 44, 1]) }] };
  });
  const actionLabelPositionStyle = useAnimatedStyle(() => {
    const g = reduceMotion ? 1 : progress.get();
    return { transform: [{ translateX: -90.5 * (1 - g) }, { translateY: -127 * (1 - g) }] };
  });
  const redeemLabelStyle = useAnimatedStyle(() => { const g = reduceMotion ? 1 : progress.get(); return { opacity: 1 - smooth((g - 0.15) / 0.30) }; });
  const keepLabelStyle = useAnimatedStyle(() => { const g = reduceMotion ? 1 : progress.get(); return { opacity: smooth((g - 0.15) / 0.30) }; });
  const supportingStyle = useAnimatedStyle(() => { const g = reduceMotion ? 1 : progress.get(); return { opacity: smooth((g - 0.55) / 0.35) }; });
  const backdropStyle = useAnimatedStyle(() => ({ opacity: 0.36 * (reduceMotion ? 1 : progress.get()) }));
  const closeStyle = useAnimatedStyle(() => { const g = reduceMotion ? 1 : progress.get(); return { opacity: smooth((g - 0.55) / 0.35) }; });
  const reducedFadeStyle = useAnimatedStyle(() => ({ opacity: reduceMotion ? progress.get() : 1 }));
  return <Animated.View style={[styles.detailLayer, reducedFadeStyle]} accessibilityViewIsModal onAccessibilityEscape={onEscape}>
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.detailBackdrop, backdropStyle]} />
    <Pressable onPress={onClose} style={styles.detailBackdropHitbox} accessibilityLabel="Close reward details" />
    <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Group clip={surfaceMask}>
        <Fill color="#20032F" />
        <Rect x={0} y={0} width={393} height={852}>
          <SkiaRadialGradient c={vec((viewportWidth * 0.49 - stageOriginX) / stageScale, (viewportHeight * 0.51 - stageOriginY) / stageScale)} r={410 / stageScale} colors={['#AA0C62', '#57054C', '#20032F']} positions={[0, 0.46, 1]} />
        </Rect>
        <Rect x={0} y={0} width={393} height={852}>
          <SkiaRadialGradient c={vec((viewportWidth * 1.03 - stageOriginX) / stageScale, (viewportHeight * 0.59 - stageOriginY) / stageScale)} r={360 / stageScale} colors={['rgba(61,56,185,0.35)', 'rgba(61,56,185,0)']} positions={[0, 1]} />
        </Rect>
        <Rect x={0} y={0} width={393} height={852}>
          <SkiaLinearGradient start={vec(0, -stageOriginY / stageScale)} end={vec(0, (viewportHeight - stageOriginY) / stageScale)} colors={['#0000002E', '#00000000', '#00000042']} positions={[0, 0.52, 1]} />
        </Rect>
      </Group>
    </Canvas>
    <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Group clip={surfaceMask}>
        <Path path={surfaceMask} color={surfaceTintColor} />
        <Group opacity={glowOpacity}>
          <Rect x={0} y={0} width={393} height={852}>
            <SkiaRadialGradient c={glowCenter} r={glowRadius} colors={['#C88629', '#C8862900']} positions={[0, 1]} />
          </Rect>
        </Group>
      </Group>
      <Path path={surfaceMask} color="rgba(255,196,59,0.65)" style="stroke" strokeWidth={0.75} />
    </Canvas>
    <Animated.View style={[styles.detailSurfaceHitbox, surfaceStyle]}><Pressable onPress={() => {}} style={StyleSheet.absoluteFill} /></Animated.View>
    <Animated.Image source={require('./assets/headphones.png')} resizeMode="contain" style={[styles.detailImage, artStyle]} accessibilityIgnoresInvertColors />
    <Animated.View pointerEvents="none" style={[styles.detailProductName, productNamePosition]}>
      <Animated.View style={[styles.detailProductNameScale, productNameScale]}><FixedText style={styles.detailSourceTitleText}>Aural Headphones</FixedText></Animated.View>
    </Animated.View>
    <Animated.View pointerEvents="none" style={[styles.detailPrice, priceStyle]}><FixedText style={styles.detailPriceNumber}>600</FixedText><FixedText style={styles.detailPricePts}>pts</FixedText></Animated.View>
    <Animated.View pointerEvents="none" style={[styles.detailShortfall, supportingStyle]}><FixedText style={styles.detailShortfallText}>You need 480 more points.</FixedText></Animated.View>
    <Animated.View style={[styles.detailAction, actionStyle]}>
      <Pressable disabled={!actionEnabled} onPress={onClose} style={StyleSheet.absoluteFill} accessibilityRole="button" accessibilityElementsHidden={!actionAccessible} importantForAccessibility={actionAccessible ? 'yes' : 'no'} accessibilityLabel="Keep earning points" accessibilityHint="Close reward details and return to your available rewards.">
        <LinearGradient colors={['#F3A82F', '#FAC672', '#FED99B']} locations={[0, 0.6, 1]} style={StyleSheet.absoluteFill} />
      </Pressable>
    </Animated.View>
    <Animated.View pointerEvents="none" style={[styles.detailActionTextFrame, actionLabelPositionStyle, redeemLabelStyle]}><FixedText style={styles.detailRedeemText}>Redeem</FixedText></Animated.View>
    <Animated.View pointerEvents="none" style={[styles.detailActionTextFrame, actionLabelPositionStyle, keepLabelStyle]}><FixedText style={styles.detailKeepText}>Keep earning</FixedText></Animated.View>
    <Animated.View style={[styles.detailCloseWrap, closeStyle]}>
      <Pressable ref={closeRef} disabled={!actionEnabled} onPress={onClose} style={styles.detailCloseButton} accessibilityRole="button" accessibilityElementsHidden={!actionAccessible} importantForAccessibility={actionAccessible ? 'yes' : 'no'} accessibilityLabel="Close reward details"><View style={styles.detailCloseCircle}><Ionicons name="close" size={13} color="rgba(255,255,255,0.9)" /></View></Pressable>
    </Animated.View>
  </Animated.View>;
}

export function AuricRedeemPoints({ autoPlay = false, previewTime, previewDetailProgress }: Props) {
  const windowSize = useWindowDimensions();
  const [measuredViewport, setMeasuredViewport] = useState<{ width: number; height: number } | null>(null);
  const viewportWidth = measuredViewport?.width ?? windowSize.width;
  const viewportHeight = measuredViewport?.height ?? windowSize.height;
  const reduceMotion = useReducedMotionPreference();
  const scale = Math.min(viewportWidth / 393, viewportHeight / 852);
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [interruption, setInterruption] = useState(0);
  const [alertVisible, setAlertVisible] = useState(false);
  const alertVisibleRef = useRef(false);
  const [detailsVisible, setDetailsVisible] = useState(false);
  const detailProgress = useSharedValue(0);
  const closeButtonRef = useRef<View>(null);
  const redeemButtonRef = useRef<View>(null);
  const detailGeneration = useRef(0);
  const detailsVisibleRef = useRef(false);
  const previewDetailProgressRef = useRef(previewDetailProgress);
  const requestedOpenRef = useRef(false);
  const openFrameRef = useRef<number | null>(null);
  const focusFrameRef = useRef<number | null>(null);
  useLayoutEffect(() => {
    detailsVisibleRef.current = detailsVisible;
    previewDetailProgressRef.current = previewDetailProgress;
  }, [detailsVisible, previewDetailProgress]);

  const focusCloseForGeneration = useCallback((generation: number) => {
    if (detailGeneration.current !== generation || !detailsVisibleRef.current) return;
    const node = findNodeHandle(closeButtonRef.current);
    if (node != null) AccessibilityInfo.setAccessibilityFocus(node);
  }, []);
  const finishCloseForGeneration = useCallback((generation: number) => {
    if (detailGeneration.current !== generation) return;
    setDetailsVisible(false);
    focusFrameRef.current = requestAnimationFrame(() => {
      focusFrameRef.current = null;
      if (detailGeneration.current !== generation) return;
      const node = findNodeHandle(redeemButtonRef.current);
      if (node != null) AccessibilityInfo.setAccessibilityFocus(node);
    });
  }, []);

  const animateOpenForGeneration = useCallback((generation: number) => {
    if (detailGeneration.current !== generation || !detailsVisibleRef.current || !requestedOpenRef.current) return;
    const onFinished = (finished?: boolean) => {
      'worklet';
      if (finished === true) scheduleOnRN(focusCloseForGeneration, generation);
    };
    detailProgress.set(reduceMotion
      ? withTiming(1, { duration: 150 }, onFinished)
      : withSpring(1, { mass: 1, stiffness: 438.649, damping: 35.605 }, onFinished));
  }, [detailProgress, focusCloseForGeneration, reduceMotion]);

  const closeDetails = useCallback((instant = false) => {
    if (!detailsVisible) return;
    const generation = ++detailGeneration.current;
    requestedOpenRef.current = false;
    if (openFrameRef.current != null) { cancelAnimationFrame(openFrameRef.current); openFrameRef.current = null; }
    if (instant) {
      detailProgress.set(0);
      finishCloseForGeneration(generation);
      return;
    }
    const onFinished = (finished?: boolean) => {
      'worklet';
      if (finished === true) scheduleOnRN(finishCloseForGeneration, generation);
    };
    detailProgress.set(reduceMotion
      ? withTiming(0, { duration: 120, easing: Easing.out(Easing.quad) }, onFinished)
      : withTiming(0, { duration: 200, easing: Easing.bezier(0.23, 1, 0.32, 1) }, onFinished));
  }, [detailProgress, detailsVisible, finishCloseForGeneration, reduceMotion]);
  const openDetails = useCallback(() => {
    if (detailsVisible) return;
    const generation = ++detailGeneration.current;
    requestedOpenRef.current = true;
    setDetailsVisible(true); detailProgress.set(0);
    openFrameRef.current = requestAnimationFrame(() => {
      openFrameRef.current = null;
      animateOpenForGeneration(generation);
    });
  }, [animateOpenForGeneration, detailProgress, detailsVisible]);

  useEffect(() => {
    if (!detailsVisibleRef.current || previewDetailProgressRef.current !== undefined) return;
    const generation = ++detailGeneration.current;
    const target = requestedOpenRef.current ? 1 : 0;
    const onFinished = (finished?: boolean) => {
      'worklet';
      if (finished !== true) return;
      if (target === 1) scheduleOnRN(focusCloseForGeneration, generation);
      else scheduleOnRN(finishCloseForGeneration, generation);
    };
    if (target === 1) {
      detailProgress.set(reduceMotion
        ? withTiming(1, { duration: 150 }, onFinished)
        : withSpring(1, { mass: 1, stiffness: 438.649, damping: 35.605 }, onFinished));
    } else {
      detailProgress.set(reduceMotion
        ? withTiming(0, { duration: 120, easing: Easing.out(Easing.quad) }, onFinished)
        : withTiming(0, { duration: 200, easing: Easing.bezier(0.23, 1, 0.32, 1) }, onFinished));
    }
  }, [detailProgress, finishCloseForGeneration, focusCloseForGeneration, reduceMotion]);

  useEffect(() => () => {
    detailGeneration.current += 1;
    if (openFrameRef.current != null) cancelAnimationFrame(openFrameRef.current);
    if (focusFrameRef.current != null) cancelAnimationFrame(focusFrameRef.current);
  }, []);

  useEffect(() => {
    if (previewDetailProgress === undefined) return;
    detailProgress.set(clamp(previewDetailProgress));
  }, [detailProgress, previewDetailProgress]);
  useEffect(() => {
    if (!detailsVisible || previewDetailProgress !== undefined) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => { closeDetails(true); return true; });
    return () => sub.remove();
  }, [closeDetails, detailsVisible, previewDetailProgress]);
  const showingDetails = previewDetailProgress !== undefined || detailsVisible;
  const isObscured = showingDetails || alertVisible;

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      const isActive = state === 'active';
      setActive(isActive);
    });
    return () => subscription.remove();
  }, []);

  const dismissNativeAlert = useCallback(() => {
    if (!alertVisibleRef.current) return;
    alertVisibleRef.current = false;
    setAlertVisible(false);
  }, []);
  const showMessage = useCallback((message: string) => {
    if (alertVisibleRef.current) return;
    alertVisibleRef.current = true;
    setAlertVisible(true);
    setInterruption(value => value + 1);
    Alert.alert('Rewards', message, [{ text: 'OK', style: 'cancel', onPress: dismissNativeAlert }], {
      cancelable: true,
      onDismiss: dismissNativeAlert,
    });
  }, [dismissNativeAlert]);
  const primaryPressFeedback = usePillPressFeedback(reduceMotion);
  const secondaryPressFeedback = usePillPressFeedback(reduceMotion);
  const activeCardStyle = useAnimatedStyle(() => ({ opacity: showingDetails && !reduceMotion ? 0 : 1 }));
  return <View style={styles.root} onLayout={({ nativeEvent: { layout } }) => {
    setMeasuredViewport(current => current?.width === layout.width && current.height === layout.height
      ? current
      : { width: layout.width, height: layout.height });
  }}>
    <StatusBar style="light" />
    <AuricBackdrop width={viewportWidth} height={viewportHeight} />
    <View style={styles.centerStage}>
      <View style={[styles.stage, { transform: [{ scale }] }]}>
        <View style={styles.baseContent} pointerEvents={showingDetails ? 'none' : 'auto'} accessibilityElementsHidden={showingDetails} importantForAccessibility={showingDetails ? 'no-hide-descendants' : 'auto'}>
        <MintStage autoPlay={autoPlay && !showingDetails} previewTime={previewTime} reduceMotion={reduceMotion} active={active} isObscured={isObscured} interruption={interruption} stageScale={scale} />
        <View accessible accessibilityLabel="120 points" style={styles.points}>
          <FixedText style={styles.pointNumber}>120</FixedText><FixedText style={styles.pointPts}>pts</FixedText>
        </View>
        <FixedText style={styles.available}>Available to Redeem</FixedText>
        <View style={styles.tierRail}>
          <View style={styles.railTrack} /><View style={styles.railProgress} /><View style={styles.railThumb} />
          <FixedText style={styles.railCaption}>675pts</FixedText>
          <GoldTier label="Bronze" color="#D7811E" icon="star" left={187} />
          <GoldTier label="Silver" color="#ADB7D2" icon="star" left={260} />
          <GoldTier label="Gold" color="#FFCE09" icon="trophy" left={330} />
        </View>
        <View style={styles.sectionTitle}><FixedText style={styles.sectionTitleText}>Redeem Rewards</FixedText><Ionicons name="chevron-forward" size={18} color="#FFFFFF" /></View>
        <RewardCard expired={false} accessibilityRef={redeemButtonRef} animatedStyle={activeCardStyle} onRedeem={openDetails} reduceMotion={reduceMotion} />
        <RewardCard expired={true} onRedeem={() => undefined} reduceMotion={reduceMotion} />
        <FixedText style={styles.weeklyNote}>Please note that offers are updated weekly</FixedText>
        <Pressable onPress={() => showMessage('Keep earning points to unlock your next offer.')} onPressIn={primaryPressFeedback.onPressIn} onPressOut={primaryPressFeedback.onPressOut} accessibilityRole="button" style={styles.primaryButton}>
          <Animated.View style={[styles.primaryButtonVisual, primaryPressFeedback.style]}>
            <LinearGradient colors={['#F3A82F', '#FAC672', '#FED99B']} locations={[0, 0.6, 1]} style={StyleSheet.absoluteFill} />
            <FixedText style={styles.buttonText}>Claim Offer</FixedText>
            <View pointerEvents="none" style={styles.primaryButtonOutline} />
          </Animated.View>
        </Pressable>
        <Pressable onPress={() => showMessage('Your offers will be here when you return.')} onPressIn={secondaryPressFeedback.onPressIn} onPressOut={secondaryPressFeedback.onPressOut} accessibilityRole="button" style={styles.secondaryButton}>
          <Animated.View style={[styles.secondaryButtonVisual, secondaryPressFeedback.style]}>
            <FixedText style={styles.buttonText}>Not Now</FixedText>
          </Animated.View>
        </Pressable>
        </View>
        {showingDetails && <RewardDetail progress={detailProgress} reduceMotion={reduceMotion} onClose={() => closeDetails(false)} onEscape={() => closeDetails(true)} closeRef={closeButtonRef} viewportWidth={viewportWidth} viewportHeight={viewportHeight} />}
      </View>
    </View>
  </View>;
}

function AuricBackdrop({ width, height }: { width: number; height: number }) {
  return <View pointerEvents="none" style={StyleSheet.absoluteFill}>
    <Canvas style={StyleSheet.absoluteFill}>
      <Fill color="#20032F" />
      <Rect x={0} y={0} width={width} height={height}>
        <SkiaRadialGradient c={vec(width * 0.49, height * 0.51)} r={410} colors={['#AA0C62', '#57054C', '#20032F']} positions={[0, 0.46, 1]} />
      </Rect>
      <Rect x={0} y={0} width={width} height={height}>
        <SkiaRadialGradient c={vec(width * 1.03, height * 0.59)} r={360} colors={['rgba(61,56,185,0.35)', 'rgba(61,56,185,0)']} positions={[0, 1]} />
      </Rect>
      <Rect x={0} y={0} width={width} height={height}>
        <SkiaLinearGradient start={vec(0, 0)} end={vec(0, height)} colors={['#0000002E', '#00000000', '#00000042']} positions={[0, 0.52, 1]} />
      </Rect>
    </Canvas>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#20032F' },
  centerStage: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 852, overflow: 'visible' },
  baseContent: { ...StyleSheet.absoluteFill },
  heroHitbox: { position: 'absolute', left: 106, top: 56, width: 180, height: 190, zIndex: 1 },
  heroOverflow: { position: 'absolute', left: -20, top: -25, width: 220, height: 240, overflow: 'visible' },
  heroCanvas: { position: 'absolute', left: -20, top: -25, width: 220, height: 240 },
  starsCanvas: { position: 'absolute', left: 0, top: 0, width: 220, height: 240 },
  auraOuter: { position: 'absolute', left: 5, top: 15, width: 210, height: 210, transform: [{ translateY: -4 }] },
  auraSize: { width: 210, height: 210 },
  auraCanvas: { width: 210, height: 210 },
  coinBody: { position: 'absolute', left: 20, top: 25, width: 180, height: 190 },
  points: { position: 'absolute', left: 0, top: 237, width: 393, height: 60, flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  pointNumber: { color: '#FFFFFF', fontSize: 53, fontWeight: '700', letterSpacing: -1.5, includeFontPadding: false },
  pointPts: { color: '#FFFFFF', fontSize: 31, fontWeight: '600', includeFontPadding: false, marginLeft: 5, transform: [{ translateY: -9 }] },
  available: { position: 'absolute', left: 0, top: 304, width: 393, color: '#C28DBB', fontSize: 16, textAlign: 'center', includeFontPadding: false },
  tierRail: { position: 'absolute', left: 17, top: 352, width: 359, height: 38 },
  railTrack: { position: 'absolute', top: 3, width: 359, height: 6, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.14)' },
  railProgress: { position: 'absolute', top: 3, width: 127, height: 6, borderRadius: 3, backgroundColor: '#F29724' },
  railThumb: { position: 'absolute', left: 120, top: -1, width: 14, height: 14, borderRadius: 7, backgroundColor: '#F29724', borderWidth: 1.5, borderColor: '#FFFFFF' },
  railCaption: { position: 'absolute', top: 19, color: '#FFFFFF', fontSize: 8, includeFontPadding: false },
  tierItem: { position: 'absolute', top: -3, width: 36, alignItems: 'center', gap: 5 },
  tierIcon: { width: 18, height: 18, borderRadius: 9, borderColor: 'rgba(255,255,255,0.45)', borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  tierName: { color: '#C28DBB', fontSize: 8, includeFontPadding: false },
  sectionTitle: { position: 'absolute', left: 17, top: 406, width: 359, height: 24, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionTitleText: { color: '#FFFFFF', fontSize: 18, fontWeight: '600', includeFontPadding: false },
  rewardCard: { position: 'absolute', top: 447.5, width: 177, height: 193, borderRadius: 26, borderWidth: 0.75, overflow: 'hidden' },
  activeCard: { left: 17.5, backgroundColor: 'rgba(137,60,88,0.4)', borderColor: 'rgba(255,196,59,0.65)' },
  expiredCard: { left: 200.5, backgroundColor: 'rgba(59,43,115,0.45)', borderColor: 'rgba(255,255,255,0.07)' },
  headphones: { position: 'absolute', top: -10 + HEADPHONES_FIT_OFFSET_Y, left: 40 + HEADPHONES_FIT_OFFSET_X, width: 97, height: 120, transform: [{ scale: HEADPHONES_FIT_SCALE }] },
  sneakers: { position: 'absolute', top: SNEAKERS_FIT_OFFSET_Y, left: 26.5 + SNEAKERS_FIT_OFFSET_X, width: 124, height: 104, opacity: 0.85, transform: [{ scale: SNEAKERS_FIT_SCALE }] },
  productName: { position: 'absolute', top: 95, width: 177, color: 'rgba(255,255,255,0.85)', fontSize: 12.5, textAlign: 'center', includeFontPadding: false },
  expiredText: { color: 'rgba(194,141,187,0.35)' },
  rewardPrice: { position: 'absolute', top: 115, width: 177, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'center' },
  rewardPoints: { color: '#FFFFFF', fontSize: 23, fontWeight: '700', includeFontPadding: false },
  rewardPts: { color: '#FFFFFF', fontSize: 11, fontWeight: '600', includeFontPadding: false, marginLeft: 3, transform: [{ translateY: -4 }] },
  rewardButtonHitbox: { position: 'absolute', left: 22.5, top: 141.5, width: 132, height: 44, alignItems: 'center', justifyContent: 'center' },
  rewardButton: { width: 132, height: 29, borderRadius: 15, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  rewardButtonText: { color: '#000000', fontSize: 12, fontWeight: '600', includeFontPadding: false },
  weeklyNote: { position: 'absolute', top: 672, left: 0, width: 393, color: 'rgba(194,141,187,0.8)', fontSize: 11.5, textAlign: 'center', includeFontPadding: false },
  primaryButton: { position: 'absolute', top: 694, left: 17, width: 359, height: 50, borderRadius: 25, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  primaryButtonVisual: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, borderRadius: 25, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  primaryButtonOutline: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, borderRadius: 25, borderWidth: 1, borderColor: 'rgba(255,255,255,0.14)' },
  secondaryButton: { position: 'absolute', top: 755, left: 17, width: 359, height: 50, borderRadius: 25, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  secondaryButtonVisual: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, borderRadius: 25, backgroundColor: 'rgba(186,175,194,0.6)', alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: '#000000', fontSize: 17, fontWeight: '600', includeFontPadding: false },
  detailLayer: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, zIndex: 10, overflow: 'visible' },
  detailBackdrop: { backgroundColor: '#000000' },
  detailBackdropHitbox: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0 },
  detailSurfaceHitbox: { position: 'absolute', left: 17, top: 442, width: 359, height: 348, overflow: 'hidden', transformOrigin: 'top left' },
  detailImage: { position: 'absolute', left: 128.6, top: 454, width: 135.8, height: 168 },
  detailProductName: { position: 'absolute', left: 17.5, top: 542.5, width: 177, height: 22 },
  detailProductNameScale: { width: 177, height: 22, transformOrigin: 'top' },
  detailSourceTitleText: { color: 'rgba(255,255,255,0.85)', fontSize: 12.5, textAlign: 'center', includeFontPadding: false },
  detailPrice: { position: 'absolute', left: 108, top: 632, width: 177, height: 40, flexDirection: 'row', alignItems: 'baseline', justifyContent: 'center' },
  detailPriceNumber: { color: '#FFFFFF', fontSize: 23, fontWeight: '700', includeFontPadding: false },
  detailPricePts: { marginLeft: 3, color: '#FFFFFF', fontSize: 11, fontWeight: '600', includeFontPadding: false, transform: [{ translateY: -4 }] },
  detailShortfall: { position: 'absolute', left: 39, top: 670, width: 315, height: 24 },
  detailShortfallText: { color: 'rgba(255,255,255,0.8)', fontSize: 13, includeFontPadding: false, textAlign: 'center' },
  detailAction: { position: 'absolute', left: 39, top: 716, width: 315, height: 44, borderRadius: 22, overflow: 'hidden' },
  detailActionTextFrame: { position: 'absolute', left: 39, top: 716, width: 315, height: 44, alignItems: 'center', justifyContent: 'center' },
  detailRedeemText: { color: '#000000', fontSize: 12, fontWeight: '600', includeFontPadding: false },
  detailKeepText: { color: '#000000', fontSize: 14, fontWeight: '600', includeFontPadding: false },
  detailCloseWrap: { position: 'absolute', left: 323, top: 446, width: 44, height: 44 },
  detailCloseButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  detailCloseCircle: { width: 28, height: 28, borderRadius: 14, backgroundColor: 'rgba(255,255,255,0.1)', alignItems: 'center', justifyContent: 'center' },
});
