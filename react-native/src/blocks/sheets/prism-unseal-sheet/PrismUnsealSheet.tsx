import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import {
  AppState,
  Pressable,
  type PressableProps,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
  type ViewProps,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedProps,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, {
  Circle,
  ClipPath,
  Defs,
  Ellipse,
  G,
  LinearGradient as SvgLinearGradient,
  Path,
  RadialGradient,
  Rect,
  Stop,
} from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 8;

type Props = {
  autoPlay?: boolean;
  /** Exact loop time for previews and matching captures, without starting the clock. */
  previewTime?: number;
};

type Pose = {
  lid: number;
  spread: number;
  extraction: number;
  ready: number;
  tilt: number;
  progress: number;
};

function clamp(value: number, low = 0, high = 1) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

function segment(time: number, start: number, end: number) {
  'worklet';
  return clamp((time - start) / (end - start));
}

function smooth(value: number) {
  'worklet';
  return value * value * (3 - 2 * value);
}

function easeOut(value: number) {
  'worklet';
  return 1 - Math.pow(1 - value, 3);
}

function timeline(time: number): Pose {
  'worklet';
  const t = Math.max(0, time) % LOOP;
  const refold = 1 - smooth(segment(t, 5.9, 6.9));
  const returning = 1 - smooth(segment(t, 5, 5.9));
  return {
    lid: easeOut(segment(t, 1.4, 2.6)) * refold,
    spread: smooth(segment(t, 1.55, 2.8)) * refold,
    extraction: easeOut(segment(t, 2.5, 3.55)) * returning,
    ready: smooth(segment(t, 3.1, 3.65)) * returning,
    tilt: 0,
    progress: clamp((t - 1.4) / 2.25) * (1 - smooth(segment(t, 5, 6.9))),
  };
}

function scrub(progress: number): Pose {
  'worklet';
  const p = clamp(progress);
  return {
    lid: p,
    spread: smooth(p),
    extraction: clamp((p - 0.45) / 0.55),
    ready: smooth(segment(p, 0.7, 0.98)),
    tilt: 0,
    progress: p,
  };
}

function blend(from: Pose, to: Pose, amount: number): Pose {
  'worklet';
  return {
    lid: from.lid + (to.lid - from.lid) * amount,
    spread: from.spread + (to.spread - from.spread) * amount,
    extraction: from.extraction + (to.extraction - from.extraction) * amount,
    ready: clamp(from.ready + (to.ready - from.ready) * amount),
    tilt: from.tilt + (to.tilt - from.tilt) * amount,
    progress: clamp(from.progress + (to.progress - from.progress) * amount),
  };
}

/** Closed-form reference spring: response 420ms, damping 0.82, exact end at 650ms. */
function spring(time: number) {
  'worklet';
  if (time >= 0.65) return 1;
  const omega = (2 * Math.PI) / 0.42;
  const damping = 0.82;
  const damped = omega * Math.sqrt(1 - damping * damping);
  const t = Math.max(0, time);
  const response = 1 - Math.exp(-damping * omega * t) *
    (Math.cos(damped * t) + ((damping * omega) / damped) * Math.sin(damped * t));
  const end = 1 - Math.exp(-damping * omega * 0.65) *
    (Math.cos(damped * 0.65) + ((damping * omega) / damped) * Math.sin(damped * 0.65));
  return response / end;
}

function nowSeconds() {
  'worklet';
  return Date.now() / 1000;
}

function sleeveOpacity(extraction: number) {
  'worklet';
  return 1 - smooth(segment(extraction, 0.72, 1));
}

const AnimatedG = Animated.createAnimatedComponent(G);
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);
const POCKET = 'M0 0L87 27.84L174 0V41Q174 48 167 48H7Q0 48 0 41Z';
const ringPalette = [[202, 220, 118], [243, 203, 132], [226, 82, 119], [120, 96, 171], [202, 220, 118]];
const ringSectors = Array.from({ length: 72 }, (_, i) => {
  const stop = (i + 0.5) / 18;
  const index = Math.floor(stop);
  const amount = stop - index;
  const rgb = ringPalette[index].map((channel, k) => Math.round(channel + (ringPalette[index + 1][k] - channel) * amount));
  const start = (i * 2 * Math.PI) / 72;
  const end = ((i + 1) * 2 * Math.PI) / 72 + 0.002;
  return {
    color: `rgb(${rgb.join(',')})`,
    path: `M17.5 17.5L${17.5 + 17.5 * Math.cos(start)} ${17.5 + 17.5 * Math.sin(start)}A17.5 17.5 0 0 1 ${17.5 + 17.5 * Math.cos(end)} ${17.5 + 17.5 * Math.sin(end)}Z`,
  };
});

// Static seeded grain matches the reference atlas; only the surrounding surfaces animate.
const grainPaths = Array.from({ length: 70 }, (_, alpha) => {
  let path = '';
  for (let i = 0; i < 1800; i += 1) {
    if ((i * 17) % 70 !== alpha) continue;
    const x = (i * 73 + 19) % 180;
    const y = (i * 43 + Math.floor(i / 11)) % 140;
    path += `M${x} ${y}h1v1h-1Z`;
  }
  return { path, opacity: alpha / 100 };
});

function Grain({ width, height, opacity }: { width: number; height: number; opacity: number }) {
  return (
    <G opacity={opacity} transform={`scale(${width / 180},${height / 140})`}>
      {grainPaths.map(({ path, opacity: alpha }, i) => (
        <Path key={i} d={path} fill="#1F1F1F" fillOpacity={alpha} />
      ))}
    </G>
  );
}

function RainbowDefs({ id, height }: { id: string; height: number }) {
  return (
    <Defs>
      <SvgLinearGradient id={`${id}-base`} x1="0" y1="0" x2="174" y2={height} gradientUnits="userSpaceOnUse">
        <Stop offset="0" stopColor="#CADC76" />
        <Stop offset="0.32" stopColor="#F3CB84" />
        <Stop offset="0.66" stopColor="#E25277" />
        <Stop offset="1" stopColor="#7860AB" />
      </SvgLinearGradient>
      <RadialGradient id={`${id}-blue`} cx="174" cy="0" r="145" gradientUnits="userSpaceOnUse">
        <Stop offset="0" stopColor="#B5CFED" stopOpacity={0.9} />
        <Stop offset="1" stopColor="#B5CFED" stopOpacity={0} />
      </RadialGradient>
      <SvgLinearGradient id={`${id}-light`} x1="0" y1="0" x2="0" y2={height} gradientUnits="userSpaceOnUse">
        <Stop offset="0" stopColor="#FFFFFF" stopOpacity={0.32} />
        <Stop offset="0.5" stopColor="#FFFFFF" stopOpacity={0} />
        <Stop offset="1" stopColor="#B04C9D" stopOpacity={0.18} />
      </SvgLinearGradient>
    </Defs>
  );
}

function RainbowFill({ id, height }: { id: string; height: number }) {
  return (
    <>
      <Rect width={174} height={height} fill={`url(#${id}-base)`} />
      <Rect width={174} height={height} fill={`url(#${id}-blue)`} />
      <Rect width={174} height={height} fill={`url(#${id}-light)`} />
      <Grain width={174} height={height} opacity={0.035} />
    </>
  );
}

function Card() {
  return (
    <View style={styles.cardFace}>
      <Svg width={150} height={94} style={StyleSheet.absoluteFill}>
        <Defs>
          <SvgLinearGradient id="prism-card" x1="0" y1="0" x2="150" y2="94" gradientUnits="userSpaceOnUse">
            <Stop offset="0" stopColor="#FEFDFE" />
            <Stop offset="0.48" stopColor="#E3E1E8" />
            <Stop offset="0.7" stopColor="#F5F4F7" />
            <Stop offset="1" stopColor="#D7D5DE" />
          </SvgLinearGradient>
        </Defs>
        <Rect width={150} height={94} rx={9} fill="url(#prism-card)" />
        <Grain width={150} height={94} opacity={0.025} />
        <Rect x={112} y={65} width={18} height={13} rx={3} stroke="#A6A0AD" strokeWidth={0.7} fill="none" />
        <Path d="M118 65V78M124 65V78M112 71.5H130" stroke="#B6B0BF" strokeWidth={0.55} />
        {[3, 6, 9].map((r) => {
          const dx = r * Math.cos((48 * Math.PI) / 180);
          const dy = r * Math.sin((48 * Math.PI) / 180);
          return <Path key={r} d={`M${131 + dx} ${40 - dy}A${r} ${r} 0 0 1 ${131 + dx} ${40 + dy}`} stroke="#92909A" strokeWidth={0.75} strokeLinecap="round" fill="none" />;
        })}
        <Rect x={0.4} y={0.4} width={149.2} height={93.2} rx={8.6} fill="none" stroke="#FFFFFF" strokeOpacity={0.85} strokeWidth={0.8} />
      </Svg>
      <Text style={styles.cardBrand}>prism</Text>
      <Text style={styles.cardType}>EVERYDAY</Text>
      <Text style={styles.cardName}>NOVA REED</Text>
    </View>
  );
}

function impact() {
  if (process.env.EXPO_OS !== 'web') {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }
}

/** A hinged silver sleeve delivers an everyday card inside a quiet delivery sheet. */
export function PrismUnsealSheet({ autoPlay = false, previewTime }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const scale = Math.min(size.width / 393, size.height / 852);
  const topShift = Math.max(0, insets.top / scale + 12 - 70);
  const pose = useSharedValue(reducedMotion ? scrub(1) : previewTime === undefined ? scrub(0) : timeline(previewTime));
  const epoch = useSharedValue(0);
  const releasedAt = useSharedValue(-1);
  const releaseFrom = useSharedValue(scrub(0));
  const releaseTo = useSharedValue(0);
  const dragStart = useSharedValue(scrub(0));
  const dragging = useSharedValue(false);
  const hapticCrossed = useSharedValue(false);
  const handSettled = useSharedValue(true);
  const frameController = useRef<ReturnType<typeof useFrameCallback> | null>(null);

  function stopClock() {
    frameController.current?.setActive(false);
  }

  const clock = useFrameCallback(() => {
    const now = nowSeconds();
    if (dragging.get()) return;
    if (releasedAt.get() >= 0) {
      const elapsed = Math.max(0, now - releasedAt.get());
      if (elapsed < 2) {
        pose.set(blend(releaseFrom.get(), scrub(releaseTo.get()), spring(elapsed)));
      } else if (elapsed < 2.6) {
        pose.set(blend(scrub(releaseTo.get()), scrub(0), smooth(segment(elapsed, 2, 2.6))));
      } else {
        pose.set(autoPlay ? timeline(elapsed - 2.6) : scrub(0));
        if (!autoPlay && !handSettled.get()) {
          handSettled.set(true);
          scheduleOnRN(stopClock);
        }
      }
    } else {
      pose.set(autoPlay ? timeline(now - epoch.get()) : scrub(0));
    }
  }, false);

  useEffect(() => {
    frameController.current = clock;
    return () => { frameController.current = null; };
  }, [clock]);

  function startClock() {
    if (!reducedMotion && previewTime === undefined) clock.setActive(true);
  }

  useEffect(() => {
    const now = nowSeconds();
    epoch.set(now);
    pose.set(reducedMotion ? scrub(1) : previewTime === undefined ? scrub(0) : timeline(previewTime));
    releasedAt.set(-1);
    clock.setActive(!reducedMotion && previewTime === undefined && autoPlay);
    return () => clock.setActive(false);
  }, [autoPlay, reducedMotion, previewTime, clock, epoch, pose, releasedAt]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        epoch.set(nowSeconds());
        if (autoPlay && !reducedMotion && previewTime === undefined) clock.setActive(true);
      } else {
        clock.setActive(false);
      }
    });
    return () => subscription.remove();
  }, [autoPlay, reducedMotion, previewTime, clock, epoch]);

  function setManual(target: number) {
    releaseFrom.set(pose.get());
    releaseTo.set(target);
    releasedAt.set(nowSeconds());
    dragging.set(false);
    handSettled.set(false);
    if (reducedMotion) pose.set(scrub(target));
    else startClock();
    impact();
  }

  // Sample release time even when a quick gesture begins while the frame clock is paused.
  const finalizeDrag = () => {
    'worklet';
    const current = pose.get();
    releaseFrom.set(current);
    releaseTo.set(reducedMotion ? 1 : current.progress >= 0.5 ? 1 : 0);
    releasedAt.set(nowSeconds());
    dragging.set(false);
    handSettled.set(false);
    if (reducedMotion) pose.set(scrub(1));
  };

  const drag = Gesture.Pan()
    .minDistance(0)
    .onStart(() => {
      if (reducedMotion) return;
      dragStart.set(pose.get());
      dragging.set(true);
      hapticCrossed.set(false);
      handSettled.set(false);
      scheduleOnRN(startClock);
    })
    .onUpdate((event) => {
      if (reducedMotion) return;
      const origin = dragStart.get();
      const delta = -event.translationY / scale / 180;
      const target = delta >= 0 ? scrub(1) : scrub(0);
      const distance = delta >= 0 ? Math.max(0.001, 1 - origin.progress) : Math.max(0.001, origin.progress);
      const next = blend(origin, target, clamp(Math.abs(delta) / distance));
      // Gesture Handler exposes velocity, corresponding to the reference's predicted travel.
      next.tilt = clamp((event.velocityY / scale * 0.1) / 140, -1, 1) * 4;
      pose.set(next);
      if (next.progress >= 0.7 && !hapticCrossed.get()) {
        hapticCrossed.set(true);
        scheduleOnRN(impact);
      }
    })
    .onFinalize(finalizeDrag);

  const oldTitle = useAnimatedStyle(() => {
    const frame = pose.get();
    const clearance = 1 - smooth(segment(frame.lid, 0.25, 0.65));
    return { opacity: (1 - frame.ready) * clearance, transform: [{ translateY: -6 * frame.ready }] };
  });
  const newTitle = useAnimatedStyle(() => ({ opacity: pose.get().ready, transform: [{ translateY: 6 - 6 * pose.get().ready }] }));
  const oldCopyProps = useAnimatedProps<ViewProps>(() => {
    const frame = pose.get();
    const hidden = frame.ready >= 0.5 || 1 - smooth(segment(frame.lid, 0.25, 0.65)) < 0.5;
    return { accessibilityElementsHidden: hidden, importantForAccessibility: hidden ? 'no-hide-descendants' : 'auto' };
  });
  const newCopyProps = useAnimatedProps<ViewProps>(() => ({
    accessibilityElementsHidden: pose.get().ready < 0.5,
    importantForAccessibility: pose.get().ready < 0.5 ? 'no-hide-descendants' : 'auto',
  }));
  const oldReady = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - 2 * pose.get().ready) }));
  const newReady = useAnimatedStyle(() => ({ opacity: Math.max(0, 2 * pose.get().ready - 1) }));
  const shadowStyle = useAnimatedStyle(() => ({
    opacity: (0.13 - 0.04 * pose.get().spread) / 0.13 * sleeveOpacity(pose.get().extraction),
    transform: [{ translateY: 18 * pose.get().spread }, { scaleX: 1 + 26 * pose.get().spread / 176 }, { scaleY: 1 + 5 * pose.get().spread / 11 }],
  }));
  const rearStyle = useAnimatedStyle(() => ({ opacity: pose.get().spread * sleeveOpacity(pose.get().extraction), transform: [{ translateY: -22 * pose.get().spread }] }));
  const lidStyle = useAnimatedStyle(() => ({
    opacity: 1 - smooth(segment(pose.get().lid, 0.72, 1)),
    zIndex: pose.get().lid < 0.55 ? 4 : 1,
    transform: [{ perspective: 174 / 0.55 }, { rotateX: `${-155 * pose.get().lid}deg` }],
  }));
  const gleamStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -65 + 135 * pose.get().lid }, { rotate: '22deg' }] }));
  const lidBrandStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth(segment(pose.get().lid, 0.35, 0.55)) }));
  const cardStyle = useAnimatedStyle(() => ({
    opacity: smooth(segment(pose.get().lid, 0.1, 0.4)),
    transform: [{ translateY: -44 * pose.get().extraction }, { scale: 1 + 0.12 * pose.get().extraction }, { rotate: `${-4 * (1 - pose.get().extraction) + pose.get().tilt}deg` }],
  }));
  const cardShadowStyle = useAnimatedStyle(() => ({ opacity: pose.get().extraction }));
  const pocketStyle = useAnimatedStyle(() => ({ opacity: sleeveOpacity(pose.get().extraction), transform: [{ translateY: 18 * pose.get().spread }, { rotate: `${3 * pose.get().spread}deg` }] }));
  const pocketSilverProps = useAnimatedProps(() => ({ opacity: 1 - pose.get().spread }));
  const heroProps = useAnimatedProps<ViewProps>(() => ({
    accessibilityValue: { text: pose.get().ready > 0.9 ? 'Open' : pose.get().progress > 0.01 ? 'Partly open' : 'Closed' },
  }));
  const primaryProps = useAnimatedProps<PressableProps>(() => ({ accessibilityLabel: pose.get().progress > 0.5 ? 'Close card sleeve' : 'Unseal card' }));

  return (
    <View style={styles.root} onLayout={(event) => setSize(event.nativeEvent.layout)}>
      <StatusBar style="light" />
      <View style={[styles.canvas, { left: (size.width - 393 * scale) / 2, top: (size.height - 852 * scale) / 2, transform: [{ scale }] }]}>
        <View style={[styles.wallet, { transform: [{ translateY: topShift }] }]}>
          <Text style={styles.walletTitle}>Prism</Text>
          <View style={styles.avatar}><Text style={styles.avatarLabel}>P</Text></View>
          <Text style={styles.walletCaption}>EVERYDAY WALLET</Text>
          <Text style={styles.balance}>$2,840.50</Text>
          <View style={styles.summary}>
            <Svg width={35} height={35}>
              {ringSectors.map(({ color, path }, i) => <Path key={i} d={path} fill={color} />)}
              <Circle cx={17.5} cy={17.5} r={7.5} fill="#22242B" />
            </Svg>
            <View style={styles.summaryCopy}>
              <Text style={styles.summaryTitle}>Your next everyday card</Text>
              <Text style={styles.summaryCaption}>Made for the way you move</Text>
            </View>
            <Svg width={14} height={14}><Path d="M3 11L11 3M4 3H11V10" stroke="#86848F" strokeWidth={1.1} fill="none" /></Svg>
          </View>
        </View>
        <View style={styles.dim} pointerEvents="none" />
        <View style={styles.sheet} />
        <View style={styles.grabber} />
        <Pressable style={styles.closeHit} onPress={() => setManual(0)} accessibilityRole="button" accessibilityLabel="Close package">
          <View style={styles.closeFace}><Svg width={10} height={10}><Path d="M1 1L9 9M9 1L1 9" stroke="#77747F" strokeWidth={1.2} strokeLinecap="round" /></Svg></View>
        </Pressable>
        <View style={styles.deliverySlot}><Text style={styles.delivery}>PRISM / CARD DELIVERY</Text></View>
        <Animated.View style={[styles.copy, oldTitle]} animatedProps={oldCopyProps} pointerEvents="none">
          <View style={styles.titleSlot}><Text style={styles.title}>A little more possibility.</Text></View>
          <View style={styles.subtitleSlot}><Text style={styles.subtitle}>{'A new everyday card.\nUnseal something good.'}</Text></View>
        </Animated.View>
        <Animated.View style={[styles.copy, newTitle]} animatedProps={newCopyProps} pointerEvents="none">
          <View style={styles.titleSlot}><Text style={styles.title}>Your card is ready</Text></View>
          <View style={styles.subtitleSlot}><Text style={styles.subtitle}>{'Made for wherever you go.\nReady when you are.'}</Text></View>
        </Animated.View>

        <View style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Animated.View style={[styles.ambient, shadowStyle]}>
            <Svg width={260} height={80}>
              <Defs><RadialGradient id="prism-shadow"><Stop offset="0" stopColor="#54475E" stopOpacity={0.13} /><Stop offset="0.55" stopColor="#54475E" stopOpacity={0.055} /><Stop offset="1" stopColor="#54475E" stopOpacity={0} /></RadialGradient></Defs>
              <Ellipse cx={130} cy={40} rx={115} ry={23} fill="url(#prism-shadow)" />
            </Svg>
          </Animated.View>
          <Animated.View style={[styles.rear, rearStyle]}>
            <Svg width={174} height={132}>
              <RainbowDefs id="prism-rear" height={132} />
              <Defs><ClipPath id="prism-rear-clip"><Rect width={174} height={132} rx={8} /></ClipPath></Defs>
              <G clipPath="url(#prism-rear-clip)"><RainbowFill id="prism-rear" height={132} /></G>
              <Rect x={0.4} y={0.4} width={173.2} height={131.2} rx={7.6} fill="none" stroke="#FFFFFF" strokeOpacity={0.5} strokeWidth={0.8} />
            </Svg>
          </Animated.View>
          <Animated.View style={[styles.lid, lidStyle]}>
            <View style={styles.lidClip}>
              <LinearGradient colors={['#F1F0F3', '#E5E4E9', '#F3F2F5', '#DAD9DF']} locations={[0, 0.28, 0.54, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
              <Animated.View style={[styles.gleam, gleamStyle]}><LinearGradient colors={['#FFFFFF00', '#FFFFFFB0', '#FFFFFF00']} locations={[0, 0.5, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} /></Animated.View>
              <Svg width={174} height={118} style={StyleSheet.absoluteFill}>
                <Defs><SvgLinearGradient id="prism-edge" x1="0" y1="0" x2="0" y2="1"><Stop offset="0" stopColor="#FFFFFF" stopOpacity={0.95} /><Stop offset="1" stopColor="#B8B6C0" stopOpacity={0.55} /></SvgLinearGradient></Defs>
                <Grain width={174} height={118} opacity={0.04} />
                <Path d="M2 2L87 65L172 2" fill="none" stroke="#C3C0CC" strokeOpacity={0.35} strokeWidth={0.65} />
                <Rect x={0.4} y={0.4} width={173.2} height={117.2} rx={7.6} fill="none" stroke="url(#prism-edge)" strokeWidth={0.8} />
              </Svg>
              <Animated.View style={[styles.lidBrandSlot, lidBrandStyle]}><Text style={styles.lidBrand}>prism</Text></Animated.View>
            </View>
          </Animated.View>
          <Animated.View style={[styles.card, cardStyle]}>
            <Animated.View style={[styles.cardShadow, cardShadowStyle]} />
            <Card />
          </Animated.View>
          <Animated.View style={[styles.pocket, pocketStyle]}>
            <Svg width={174} height={48}>
              <RainbowDefs id="prism-pocket" height={48} />
              <Defs>
                <ClipPath id="prism-pocket-clip"><Path d={POCKET} /></ClipPath>
                <SvgLinearGradient id="prism-pocket-silver" x1="0" y1="0" x2="1" y2="1"><Stop offset="0" stopColor="#F0EFF3" /><Stop offset="1" stopColor="#DEDBE3" /></SvgLinearGradient>
                <SvgLinearGradient id="prism-pocket-light" x1="0" y1="0" x2="1" y2="1"><Stop offset="0" stopColor="#FFFFFF" stopOpacity={0.35} /><Stop offset="0.5" stopColor="#FFFFFF" stopOpacity={0} /><Stop offset="1" stopColor="#A54585" stopOpacity={0.2} /></SvgLinearGradient>
              </Defs>
              <G clipPath="url(#prism-pocket-clip)">
                <RainbowFill id="prism-pocket" height={48} />
                <AnimatedG animatedProps={pocketSilverProps}><Rect width={174} height={48} fill="url(#prism-pocket-silver)" /></AnimatedG>
                <Path d="M0 1L87 29L174 1" fill="none" stroke="#FFFFFF" strokeOpacity={0.6} strokeWidth={0.8} />
                <Rect y={43} width={174} height={5} fill="#D75D99" />
                <Rect width={174} height={48} fill="url(#prism-pocket-light)" />
              </G>
              <Path d={POCKET} fill="none" stroke="#FFFFFF" strokeOpacity={0.5} strokeWidth={0.8} />
            </Svg>
          </Animated.View>
        </View>

        <View style={styles.hintSlot} pointerEvents="none" accessibilityElementsHidden>
          <Animated.Text style={[styles.hint, oldReady]}>Drag up to unseal</Animated.Text>
          <Animated.Text style={[styles.hint, newReady]}>A fresh start, in your hands</Animated.Text>
        </View>
        <AnimatedPressable style={styles.primary} onPress={() => setManual(pose.get().progress > 0.5 ? 0 : 1)} accessibilityRole="button" animatedProps={primaryProps}>
          <Animated.Text style={[styles.primaryLabel, oldReady]} accessible={false}>Unseal card</Animated.Text>
          <Animated.Text style={[styles.primaryLabel, newReady]} accessible={false}>All set</Animated.Text>
        </AnimatedPressable>
        <View style={styles.footerSlot}><Text style={styles.footer}>A small reveal. A new beginning.</Text></View>
        <GestureDetector gesture={drag}>
          <Animated.View style={styles.heroHit} accessible accessibilityRole="adjustable" accessibilityLabel="Unseal package" animatedProps={heroProps}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(event) => setManual(event.nativeEvent.actionName === 'increment' ? 1 : 0)} />
        </GestureDetector>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#111216' },
  canvas: { position: 'absolute', width: 393, height: 852, transformOrigin: 'top left' },
  wallet: { ...StyleSheet.absoluteFill, backgroundColor: '#111216' },
  walletTitle: { position: 'absolute', left: 26, top: 80, fontSize: 28, lineHeight: 34, fontWeight: '600', color: '#FAF9FC' },
  avatar: { position: 'absolute', left: 331, top: 80, width: 36, height: 36, borderRadius: 18, backgroundColor: '#2C2A34', alignItems: 'center', justifyContent: 'center' },
  avatarLabel: { color: '#E6E3EC', fontSize: 14, lineHeight: 18, fontWeight: '500' },
  walletCaption: { position: 'absolute', left: 26, top: 144, color: '#9B9DA6', fontSize: 11, lineHeight: 14, fontWeight: '500', letterSpacing: 2 },
  balance: { position: 'absolute', left: 26, top: 171, color: '#F4F2F6', fontSize: 42, lineHeight: 51, fontWeight: '500', fontVariant: ['tabular-nums'] },
  summary: { position: 'absolute', left: 26, top: 239, width: 341, height: 90, borderRadius: 20, borderCurve: 'continuous', backgroundColor: '#22242B', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, gap: 14 },
  summaryCopy: { flex: 1, gap: 6 },
  summaryTitle: { color: '#E8E6ED', fontSize: 13, lineHeight: 16, fontWeight: '500' },
  summaryCaption: { color: '#9797A1', fontSize: 11, lineHeight: 14 },
  dim: { position: 'absolute', left: 0, top: 0, width: 393, height: 364, backgroundColor: 'rgba(0,0,0,0.26)' },
  sheet: { position: 'absolute', left: 0, top: 364, width: 393, height: 488, borderTopLeftRadius: 32, borderTopRightRadius: 32, borderCurve: 'continuous', backgroundColor: '#FAF9FC', boxShadow: '0 16px 32px rgba(0,0,0,0.18), 0 2px 4px rgba(0,0,0,0.16)' },
  grabber: { position: 'absolute', left: 177, top: 375, width: 39, height: 4, borderRadius: 2, backgroundColor: '#D9D8DF' },
  closeHit: { position: 'absolute', left: 336, top: 378, width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  closeFace: { width: 30, height: 30, borderRadius: 15, backgroundColor: '#EEEAF0', alignItems: 'center', justifyContent: 'center' },
  deliverySlot: { position: 'absolute', left: 41.5, top: 401, width: 310, height: 16, alignItems: 'center', justifyContent: 'center' },
  delivery: { color: '#93909A', fontSize: 10, lineHeight: 13, fontWeight: '500', letterSpacing: 2 },
  copy: { ...StyleSheet.absoluteFill },
  titleSlot: { position: 'absolute', left: 14, top: 429, width: 365, height: 34, alignItems: 'center', justifyContent: 'center' },
  title: { color: '#25242A', fontSize: 25, lineHeight: 31, fontWeight: '600', textAlign: 'center' },
  subtitleSlot: { position: 'absolute', left: 31.5, top: 468, width: 330, height: 40, alignItems: 'center', justifyContent: 'center' },
  subtitle: { color: '#77717F', fontSize: 13, lineHeight: 18, textAlign: 'center' },
  ambient: { position: 'absolute', left: 66.5, top: 631, width: 260, height: 80 },
  rear: { position: 'absolute', left: 109.5, top: 538, width: 174, height: 132, borderRadius: 8, boxShadow: '0 6px 10px rgba(85,64,101,0.12)', zIndex: 0 },
  lid: { position: 'absolute', left: 109.5, top: 545, width: 174, height: 118, transformOrigin: '50% 0%', borderRadius: 8, boxShadow: '0 2px 3px rgba(93,83,103,0.12)' },
  lidClip: { flex: 1, borderRadius: 8, overflow: 'hidden' },
  gleam: { position: 'absolute', left: 62, top: -31, width: 50, height: 180 },
  lidBrandSlot: { position: 'absolute', left: 0, top: 37, width: 174, height: 16, alignItems: 'center', justifyContent: 'center' },
  lidBrand: { color: '#92909A', fontSize: 11, lineHeight: 14, fontWeight: '500', letterSpacing: 0.3 },
  card: { position: 'absolute', left: 121.5, top: 569, width: 150, height: 94, zIndex: 2 },
  cardShadow: { ...StyleSheet.absoluteFill, borderRadius: 9, boxShadow: '0 6px 8px rgba(74,63,90,0.17)' },
  cardFace: { width: 150, height: 94, borderRadius: 9, overflow: 'hidden' },
  cardBrand: { position: 'absolute', left: 13, top: 12, color: '#78777D', fontSize: 12, lineHeight: 15, fontWeight: '600' },
  cardType: { position: 'absolute', left: 13, top: 31, color: '#78777D', fontSize: 7, lineHeight: 9, fontWeight: '500', letterSpacing: 1.6 },
  cardName: { position: 'absolute', left: 13, top: 72, color: '#78777D', fontSize: 8, lineHeight: 10, fontWeight: '500', letterSpacing: 0.5 },
  pocket: { position: 'absolute', left: 109.5, top: 615, width: 174, height: 48, zIndex: 5, transformOrigin: '50% 0%', borderRadius: 7, boxShadow: '0 2px 2px rgba(102,68,94,0.1)' },
  hintSlot: { position: 'absolute', left: 31.5, top: 706, width: 330, height: 18, alignItems: 'center', justifyContent: 'center' },
  hint: { position: 'absolute', color: '#77717F', fontSize: 11, lineHeight: 15, textAlign: 'center' },
  primary: { position: 'absolute', left: 26, top: 748, width: 341, height: 50, borderRadius: 14, borderCurve: 'continuous', backgroundColor: '#EEEAF2', borderWidth: 1, borderColor: 'rgba(255,255,255,0.85)', alignItems: 'center', justifyContent: 'center' },
  primaryLabel: { position: 'absolute', color: '#3A3542', fontSize: 13, lineHeight: 17, fontWeight: '600' },
  footerSlot: { position: 'absolute', left: 26, top: 797, width: 341, height: 16, alignItems: 'center', justifyContent: 'center' },
  footer: { color: '#AAA6B0', fontSize: 10, lineHeight: 13 },
  heroHit: { position: 'absolute', left: 70, top: 509, width: 253, height: 196, zIndex: 10 },
});
