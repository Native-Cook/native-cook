import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { Canvas, Fill, Shader, type Uniforms } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions, type StyleProp, type ViewStyle } from 'react-native';
import { GestureDetector, Pressable } from 'react-native-gesture-handler';
import Animated, { type CSSStyle, cubicBezier, interpolate, SensorType, useAnimatedReaction, useAnimatedSensor, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { CanvasRef } from 'react-native-webgpu';

import { HabitatGpuBoundary, useHabitatWebGPU } from './gpu/HabitatGpuField';
import { HabitatParticleField, HabitatParticleFront } from './gpu/HabitatParticleField';
import { HabitatIdentityText, habitatIdentityTextHeight } from './HabitatIdentityText';
import { HabitatAffection } from './HabitatAffection';
import { CREATURE_PAD, HabitatCreature } from './HabitatCreature';
import { HabitatHint } from './HabitatHint';
import { EXCHANGE_DRIFT, type HabitatTab, signatureEnvelope, specimenDistance, useHabitatMotion } from './HabitatMotion';
import { HOP_HEIGHT, hopLift, landEnvelope } from './HabitatRig';
import { HabitatPanels } from './HabitatPanels';
import { MorphingText } from '../../gestures/choose-your-ball/MorphingText';
import { TextSwap } from './HabitatTextMotion';
import { HABITAT_EFFECT } from './HabitatShaders';
import { HabitatSignature } from './HabitatSignature';

export { LOOP } from './HabitatMotion';
export type HabitatSpecimenCarouselProps = {
  autoPlay?: boolean;
  previewTime?: number;
  previewReducedMotion?: boolean;
  renderer?: 'auto' | 'skia' | 'webgpu';
  onRendererStatus?: (status: string) => void;
};

const SPECIMENS = [
  { name: 'Charizard', number: '006', species: 'Flame Pokémon', element: 'fire', weight: '90.5 kg', height: '1.7 m',
    image: require('./assets/charizard.png'),
    types: [{ label: 'Fire', icon: 'fire', color: '#EAC28C' }, { label: 'Flying', icon: 'feather', color: '#C2CBDD' }],
    copy: 'Charizard flies around the sky in search of powerful opponents. It breathes fire of such great heat that it melts anything. However, it never turns its fiery breath on any opponent weaker than itself.',
    stats: [78, 84, 78, 109, 85, 100],
    evolution: [{ name: 'Charmander', number: '004' }, { name: 'Charmeleon', number: '005', level: 16 }, { name: 'Charizard', number: '006', level: 36 }] },
  { name: 'Blastoise', number: '009', species: 'Shellfish Pokémon', element: 'water', weight: '85.5 kg', height: '1.6 m',
    image: require('./assets/blastoise.png'),
    types: [{ label: 'Water', icon: 'water', color: '#9CE0ED' }],
    copy: 'Blastoise has water spouts that protrude from its shell. The water spouts are very accurate. They can shoot bullets of water with enough accuracy to strike empty cans from over 160 feet away.',
    stats: [79, 83, 100, 85, 105, 78],
    evolution: [{ name: 'Squirtle', number: '007' }, { name: 'Wartortle', number: '008', level: 16 }, { name: 'Blastoise', number: '009', level: 36 }] },
  { name: 'Bulbasaur', number: '001', species: 'Seed Pokémon', element: 'grass', weight: '6.9 kg', height: '0.7 m',
    image: require('./assets/bulbasaur.png'),
    types: [{ label: 'Grass', icon: 'leaf', color: '#C2E9A2' }, { label: 'Poison', icon: 'bottle-tonic-skull', color: '#D2B4E6' }],
    copy: 'A strange seed was planted on its back at birth. The plant sprouts and grows with this Pokémon, soaking up sunlight to store energy for the long journey ahead.',
    stats: [45, 49, 49, 65, 65, 45],
    evolution: [{ name: 'Bulbasaur', number: '001' }, { name: 'Ivysaur', number: '002', level: 16 }, { name: 'Venusaur', number: '003', level: 32 }] },
] as const;
const PRESS_EASING = cubicBezier(0.23, 1, 0.32, 1);
type IconName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];

function HabitatButton({ label, onPress, selected, children, style, reduced }: {
  label: string; onPress: () => void; selected?: boolean; children: React.ReactNode; style?: StyleProp<ViewStyle>; reduced: boolean;
}) {
  return <Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={selected == null ? undefined : { selected }}
    onPress={onPress} style={[styles.button, style]} pressRetentionOffset={16}>
    {({ pressed }) => <Animated.View style={[styles.buttonInner, {
      opacity: pressed ? 0.78 : 1, transform: [{ scale: pressed && !reduced ? 0.97 : 1 }],
      transitionProperty: reduced ? 'opacity' : 'transform', transitionDuration: 120, transitionTimingFunction: PRESS_EASING,
    }]}>{children}</Animated.View>}
  </Pressable>;
}

function EnvironmentFallback({ position, width, height }: { position: SharedValue<number>; width: number; height: number }) {
  return <View pointerEvents="none" style={{ width, height, position: 'absolute' }}>
    {([
      ['#34221B', '#68351D', '#160F0E'], ['#103F58', '#145766', '#081F30'], ['#173F2A', '#315D32', '#10281F'],
    ] as const).map((colors, index) => <FallbackLayer key={index} index={index} colors={colors} position={position} />)}
  </View>;
}
function FallbackLayer({ index, colors, position }: { index: number; colors: readonly [string, string, string]; position: SharedValue<number> }) {
  const opacity = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - Math.abs(specimenDistance(index, position.get()))) }));
  return <Animated.View style={[StyleSheet.absoluteFill, opacity]}><LinearGradient colors={colors} locations={[0, 0.4, 1]} style={StyleSheet.absoluteFill} /></Animated.View>;
}

/** The incoming specimen's hop height in points, and the landed specimen's contact squash, for the shadows. */
function liftUniform(position: number, reduced: boolean, landing: number, landingIndex: number, size: number) {
  'worklet';
  if (reduced) return [0, 0];
  const p = ((position % 3) + 3) % 3;
  const f = p - Math.floor(p);
  const from = Math.floor(p) % 3;
  return [f > 0.0005 ? hopLift(f) * HOP_HEIGHT * size : 0, landingIndex === from ? Math.max(0, landEnvelope(landing)) : 0];
}

const HabitatEnvironment = memo(function HabitatEnvironment({ position, time, influence, reduced, power, powerIndex, landing, landingIndex, width, height, heroTop, heroHeight, creatureSize }: {
  position: SharedValue<number>; time: SharedValue<number>; influence: SharedValue<number>; reduced: SharedValue<boolean>;
  power: SharedValue<number>; powerIndex: SharedValue<number>; landing: SharedValue<number>; landingIndex: SharedValue<number>;
  width: number; height: number; heroTop: number; heroHeight: number; creatureSize: number;
}) {
  const uniforms = useDerivedValue<Uniforms>(() => ({
    resolution: [width, height], stage: [heroTop, heroHeight], creature: [creatureSize, EXCHANGE_DRIFT * width, (heroHeight - creatureSize) / 2],
    position: position.get(), time: time.get(), influence: influence.get(), reduced: reduced.get() ? 1 : 0,
    flare: signatureEnvelope(power.get()), flareElement: powerIndex.get(),
    lift: liftUniform(position.get(), reduced.get(), landing.get(), landingIndex.get(), creatureSize),
  }));
  if (!HABITAT_EFFECT) return <EnvironmentFallback position={position} width={width} height={height} />;
  return <Canvas pointerEvents="none" style={StyleSheet.absoluteFill} accessibilityElementsHidden>
    <Fill><Shader source={HABITAT_EFFECT} uniforms={uniforms} /></Fill>
  </Canvas>;
});

/**
 * The key light over the baked volume: it leans with the phone (gravity, high-passed so it
 * re-centres on however the phone is held) and drifts slowly on its own. Still under Reduced Motion.
 */
function useKeyLight(time: SharedValue<number>, reduced: SharedValue<boolean>) {
  const gravity = useAnimatedSensor(SensorType.GRAVITY, { interval: 'auto' });
  const rest = useSharedValue<readonly [number, number] | null>(null);
  const tilt = useSharedValue<readonly [number, number]>([0, 0]);
  useFrameCallback(info => {
    const g = gravity.sensor.get();
    if (reduced.get() || (g.x === 0 && g.y === 0)) return;
    const gx = g.x / 9.81;
    const gy = g.y / 9.81;
    const base = rest.get() ?? [gx, gy];
    const a = Math.min(1, (info.timeSincePreviousFrame ?? 16) / 1000 / 2.5);
    rest.set([base[0] + (gx - base[0]) * a, base[1] + (gy - base[1]) * a]);
    const previous = tilt.get();
    const target = [Math.max(-1, Math.min(1, (gx - base[0]) * 3)), Math.max(-1, Math.min(1, (base[1] - gy) * 3))];
    tilt.set([previous[0] + (target[0] - previous[0]) * 0.18, previous[1] + (target[1] - previous[1]) * 0.18]);
  });
  const light = useDerivedValue<readonly [number, number, number]>(() => {
    const still = reduced.get();
    const drift = still ? 0 : Math.sin(time.get() * Math.PI * 2 * 2);
    const [tx, ty] = still ? [0, 0] : tilt.get();
    return [-0.5 + drift * 0.12 + tx * 0.55, -0.6 + ty * 0.45, 1];
  });
  // The same tilt turns which way is down for the particle world.
  return { light, tilt };
}

/** A heart on the favourite button's burst, per element. */
const FAVORITE_BURST = ['#F2A766', '#7FD3EA', '#A9DD7F'] as const;
const ICON_SWAP = cubicBezier(0.2, 0, 0, 1);
/**
 * The favourite icon cross-fades (scale 0.25 → 1, blur 4 → 0) and, when it turns on, throws six
 * small sparks in the specimen's colour. Under Reduced Motion only the opacity changes.
 */
function FavoriteHeart({ on, size, reduced, burst }: { on: boolean; size: number; reduced: boolean; burst: string }) {
  const icon = (shown: boolean): ViewStyle => ({
    position: 'absolute', opacity: shown ? 1 : 0,
    transform: [{ scale: shown || reduced ? 1 : 0.25 }],
    filter: reduced ? undefined : [{ blur: shown ? 0 : 4 }],
  });
  const swap: CSSStyle<ViewStyle> = { transitionProperty: ['opacity', 'transform', 'filter'], transitionDuration: 300, transitionTimingFunction: ICON_SWAP };
  return <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
    <Animated.View style={[icon(!on), swap]}><MaterialCommunityIcons name="heart-outline" size={size} color="#D7E3D2" /></Animated.View>
    <Animated.View style={[icon(on), swap]}><MaterialCommunityIcons name="heart" size={size} color="#EDC591" /></Animated.View>
    {on && !reduced && [0, 1, 2, 3, 4, 5].map(i => <FavoriteSpark key={i} index={i} color={burst} size={size} />)}
  </View>;
}
function FavoriteSpark({ index, color, size }: { index: number; color: string; size: number }) {
  const angle = (index / 6) * Math.PI * 2 - Math.PI / 2 + (index % 2) * 0.22;
  const reach = size * (0.95 + (index % 3) * 0.12);
  return <Animated.View pointerEvents="none" style={{
    position: 'absolute', width: 4, height: 4, borderRadius: 2, backgroundColor: color,
    animationName: { from: { opacity: 1, transform: [{ translateX: 0 }, { translateY: 0 }, { scale: 1 }] },
      to: { opacity: 0, transform: [{ translateX: Math.cos(angle) * reach }, { translateY: Math.sin(angle) * reach }, { scale: 0.4 }] } },
    animationDuration: 520, animationTimingFunction: PRESS_EASING, animationFillMode: 'forwards',
  }} />;
}

/** The dex number behind the hero, rewritten with the same morph: the shared 0s glide, the last digit swaps. */
const DexGhost = memo(function DexGhost({ text, reducedMotion, width, scale }: { text: string; reducedMotion: boolean; width: number; scale: number }) {
  const fontSize = 168 * scale;
  const height = Math.round(fontSize * 1.34);
  return <View pointerEvents="none" style={[styles.ghost, { width, height, top: -10 * scale }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <MorphingText align="center" color="#C5DAC7" fitTexts={SPECIMENS.map(specimen => specimen.number)} fontSize={fontSize} height={height}
      reducedMotion={reducedMotion} text={text} tracking={-6 * scale} weight="700" width={width} />
  </View>;
});

/** Choose Your Ball's belt index, with the specimens: each thumbnail places itself by its distance from centre. */
const STRIP_STRIDE = 40;
const STRIP_THUMB = 24;
const STRIP_SELECTED = 34;
function SpecimenStrip({ position, onPick, reduced }: { position: SharedValue<number>; onPick: (index: number) => void; reduced: boolean }) {
  return <View pointerEvents="box-none" style={styles.strip}>
    {SPECIMENS.map((specimen, index) => <StripThumb key={specimen.name} index={index} position={position} onPick={onPick} reduced={reduced} />)}
  </View>;
}
const StripThumb = memo(function StripThumb({ index, position, onPick, reduced }: { index: number; position: SharedValue<number>; onPick: (index: number) => void; reduced: boolean }) {
  const specimen = SPECIMENS[index];
  const style = useAnimatedStyle(() => {
    const p = position.get();
    const slot = specimenDistance(index, reduced ? Math.round(p) : p);
    const distance = Math.abs(slot);
    return {
      opacity: interpolate(distance, [0, 1, 1.5], [1, 0.42, 0.2], 'clamp'),
      transform: [{ translateX: slot * STRIP_STRIDE }, { scale: interpolate(distance, [0, 1], [STRIP_SELECTED / STRIP_THUMB, 1], 'clamp') }],
    };
  });
  return <Animated.View style={[styles.stripSlot, style]}>
    <Pressable accessibilityRole="button" accessibilityLabel={`Select ${specimen.name}`} hitSlop={10} onPress={() => onPick(index)}>
      {({ pressed }) => <Animated.View style={{ transform: [{ scale: pressed && !reduced ? 0.96 : 1 }], opacity: pressed ? 0.8 : 1,
        transitionProperty: ['transform', 'opacity'], transitionDuration: 150, transitionTimingFunction: PRESS_EASING }}>
        <Image source={specimen.image} style={{ width: STRIP_THUMB, height: STRIP_THUMB }} contentFit="contain" accessible={false} />
      </Animated.View>}
    </Pressable>
  </Animated.View>;
});

function SpecimenTypes({ position, reduced, top, scale, selectedIndex }: {
  position: SharedValue<number>; reduced: SharedValue<boolean>; top: number; scale: number; selectedIndex: number;
}) {
  const row = 25 * scale;
  return <View pointerEvents="none" style={[styles.identity, { top: top + 9 * scale }]}>
    <TextSwap position={position} reduced={reduced} height={row} selectedIndex={selectedIndex} style={styles.typeWindow}
      items={SPECIMENS.map(specimen => <View key={specimen.name} style={[styles.typeRow, { gap: 8 * scale }]}>{specimen.types.map(type => <View key={type.label}
        style={[styles.type, { height: row, borderRadius: 15 * scale, paddingHorizontal: 11 * scale, borderColor: `${type.color}45`, backgroundColor: `${type.color}08` }]}>
        <MaterialCommunityIcons name={type.icon as IconName} size={12 * scale} color={type.color} />
        <Text style={{ color: type.color, fontSize: 10 * scale, fontWeight: '600' }}>{type.label}</Text>
      </View>)}</View>)} />
  </View>;
}

function SpecimenFacts({ position, reduced, top, scale, selectedIndex }: {
  position: SharedValue<number>; reduced: SharedValue<boolean>; top: number; scale: number; selectedIndex: number;
}) {
  const line = 17 * scale;
  const value = (key: 'weight' | 'height') => <TextSwap position={position} reduced={reduced} height={line} selectedIndex={selectedIndex} style={{ minWidth: 58 * scale }}
    items={SPECIMENS.map(specimen => <Text key={specimen.name} style={[styles.factNumber, { fontSize: 12 * scale, lineHeight: line }]}>{specimen[key]}</Text>)} />;
  return <View pointerEvents="none" style={[styles.facts, { left: 25 * scale, right: 25 * scale, top, height: 44 * scale, paddingTop: 10 * scale }]}
    accessible accessibilityLabel={`Weight ${SPECIMENS[selectedIndex].weight}, height ${SPECIMENS[selectedIndex].height}`}>
    <View style={styles.fact}><View style={styles.factValue}><MaterialCommunityIcons name="weight-kilogram" size={15 * scale} color="#B1C1B0" />{value('weight')}</View><Text style={[styles.factLabel, { fontSize: 8 * scale }]}>WEIGHT</Text></View>
    <View style={[styles.fact, styles.secondFact]}><View style={styles.factValue}><MaterialCommunityIcons name="ruler" size={15 * scale} color="#B1C1B0" />{value('height')}</View><Text style={[styles.factLabel, { fontSize: 8 * scale }]}>HEIGHT</Text></View>
  </View>;
}

export function HabitatSpecimenCarousel({ autoPlay = true, previewTime, previewReducedMotion, renderer = 'auto', onRendererStatus }: HabitatSpecimenCarouselProps) {
  const window = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const [viewport, setViewport] = useState<{ width: number; height: number }>();
  const [favorites, setFavorites] = useState<readonly boolean[]>([false, false, false]);
  const { width, height } = viewport ?? window;
  const scale = width / 393;
  const identityHeight = habitatIdentityTextHeight(scale, window.fontScale);
  const available = height - insets.top - insets.bottom;
  const heroHeight = Math.max(190 * scale, Math.min(340 * scale, available - 415 * scale - Math.max(0, identityHeight - 60 * scale)));
  const heroTop = insets.top + 78 * scale;
  const creatureSize = Math.min(357 * scale, heroHeight - 4 * scale);
  // About half the screen of finger travel per specimen: the front leads the finger, so one deliberate
  // swipe turns one page, as on Choose Your Ball.
  const gap = Math.round(width * 0.58);
  const identityTop = heroTop + heroHeight + 29 * scale;
  const tabsTop = identityTop + identityHeight + 48 * scale;
  const descriptionTop = tabsTop + 44 * scale;
  // Weight and height sit on the specimen strip, which takes the footer's centre.
  const factsTop = height - insets.bottom - 84 * scale;
  const signatureFeedback = useCallback(() => {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    // The heavier tap lands with the release, after the crouch.
    setTimeout(() => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {}); }, 215);
  }, []);
  // A light tick when a specimen someone sent lands; a soft one when a hold turns into petting.
  const landFeedback = useCallback(() => { void Haptics.selectionAsync().catch(() => {}); }, []);
  const petFeedback = useCallback(() => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {}); }, []);
  const motion = useHabitatMotion({ autoPlay, previewTime, previewReducedMotion, gap, width, onSignature: signatureFeedback, onLand: landFeedback, onPet: petFeedback });
  const { selectedIndex, reduced } = motion;
  const selected = SPECIMENS[selectedIndex];
  const favorite = favorites[selectedIndex];
  // A tab the person picks holds until the demo itself moves on to another panel.
  const [picked, setPicked] = useState<{ tab: HabitatTab; over: HabitatTab } | null>(null);
  const tab = picked && picked.over === motion.demoTab ? picked.tab : motion.demoTab;
  const { hold, demoTab } = motion;
  // The habitat front's slow current, held still outside a crossing (where it has no effect) so resting art never redraws.
  const frontPhase = useSharedValue(0);
  const { position: carouselPosition, time: ambientTime, reducedValue } = motion;
  useAnimatedReaction(() => {
    const p = carouselPosition.get();
    return p - Math.floor(p) > 0.0005 && !reducedValue.get() ? Math.PI * 2 * (ambientTime.get() % 1) : 0;
  }, phase => { if (phase !== frontPhase.get()) frontPhase.set(phase); });
  const chooseTab = useCallback((next: HabitatTab) => { hold(6000); setPicked({ tab: next, over: demoTab }); }, [demoTab, hold]);
  const { light, tilt } = useKeyLight(ambientTime, reducedValue);
  const creatureMotion = useMemo(() => ({
    position: motion.position, reduced: motion.reducedValue, time: motion.time, power: motion.power, powerIndex: motion.powerIndex, fx: motion.fx,
    press: motion.press, look: motion.look, pet: motion.pet, petClock: motion.petClock, landing: motion.landing, landingIndex: motion.landingIndex,
  }), [motion.fx, motion.landing, motion.landingIndex, motion.look, motion.pet, motion.petClock, motion.position, motion.power, motion.powerIndex, motion.press, motion.reducedValue, motion.time]);
  const signatureLayout = { width, height: heroTop + heroHeight + 40 * scale, size: creatureSize, left: (width - creatureSize) / 2, top: (heroHeight - creatureSize) / 2,
    heroTop, screenHeight: height, scale, pad: Math.round(creatureSize * CREATURE_PAD) };

  const rendererReporter = useRef(onRendererStatus);
  const lastRenderer = useRef<typeof renderer | undefined>(undefined);
  const confirmedStatus = useRef<string | undefined>(undefined);
  useEffect(() => { rendererReporter.current = onRendererStatus; }, [onRendererStatus]);
  const reportRenderer = useCallback((status: string) => {
    confirmedStatus.current = status;
    rendererReporter.current?.(status);
  }, []);
  useEffect(() => {
    // Fast Refresh replays effects while a ready native canvas may remain mounted.
    if (lastRenderer.current === renderer && confirmedStatus.current) {
      rendererReporter.current?.(confirmedStatus.current);
      return;
    }
    lastRenderer.current = renderer;
    reportRenderer(renderer === 'skia' ? 'Skia habitats · crisp creature art' : 'Skia habitats · checking native WebGPU field');
  }, [renderer, reportRenderer]);
  const gpuReady = useCallback(() => reportRenderer('WebGPU particle world + Skia environment · crisp creature art'), [reportRenderer]);
  // The particle world runs on WebGPU when there is one; a frozen review frame keeps Skia's
  // deterministic particles instead, so a moment can be scrubbed to.
  const [gpuFailed, setGpuFailed] = useState(false);
  const gpuUnavailable = useCallback(() => { setGpuFailed(true); reportRenderer('Skia fallback · native WebGPU particles unavailable'); }, [reportRenderer]);
  const webgpu = useHabitatWebGPU(renderer !== 'skia' && previewTime == null && !gpuFailed, gpuUnavailable);
  const gpuParticles = useSharedValue(false);
  const particleFront = useRef<CanvasRef>(null);
  const [frontLaidOut, setFrontLaidOut] = useState(false);
  const markFrontLaidOut = useCallback(() => setFrontLaidOut(true), []);

  return <View style={styles.root} onLayout={({ nativeEvent: { layout } }) => setViewport(previous => previous?.width === layout.width && previous.height === layout.height ? previous : { width: layout.width, height: layout.height })}>
    <StatusBar style="light" />
    <HabitatEnvironment position={motion.position} time={motion.time} influence={motion.influence} reduced={motion.reducedValue} power={motion.power} powerIndex={motion.powerIndex} landing={motion.landing} landingIndex={motion.landingIndex} width={width} height={height} heroTop={heroTop} heroHeight={heroHeight} creatureSize={creatureSize} />
    {webgpu && <HabitatGpuBoundary onFailure={gpuUnavailable}>
      <HabitatParticleField webgpu={webgpu} motion={creatureMotion} tilt={tilt} running={gpuParticles} touch={motion.touch} tap={motion.tapAt}
        front={particleFront} frontLaidOut={frontLaidOut} reducedMotion={reduced} onReady={gpuReady} onUnavailable={gpuUnavailable}
        width={width} height={signatureLayout.height} heroTop={heroTop} heroHeight={heroHeight} screenHeight={height} scale={scale}
        size={creatureSize} left={signatureLayout.left} top={signatureLayout.top} pad={signatureLayout.pad} />
    </HabitatGpuBoundary>}
    <View style={[styles.header, { left: 17 * scale, right: 17 * scale, top: insets.top + 1 * scale }]}>
      <HabitatButton label="Previous Pokémon" reduced={reduced} onPress={() => motion.select((selectedIndex + 2) % 3)}><MaterialCommunityIcons name="chevron-left" size={25 * scale} color="#F3F4E9" /></HabitatButton>
      <HabitatButton label={`${favorite ? 'Remove' : 'Add'} ${selected.name} ${favorite ? 'from' : 'to'} favorites`} reduced={reduced} selected={favorite} onPress={() => {
        if (!favorite) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        setFavorites(previous => previous.map((value, index) => index === selectedIndex ? !value : value));
      }}>
        <FavoriteHeart on={favorite} size={22 * scale} reduced={reduced} burst={FAVORITE_BURST[selectedIndex]} />
      </HabitatButton>
    </View>
    <View pointerEvents="none" style={[styles.eyebrowRow, { top: insets.top + 58 * scale, left: 26 * scale, right: 27 * scale }]}><Text style={[styles.eyebrow, { fontSize: 10 * scale }]}>POKÉDEX</Text><TextSwap position={motion.position} reduced={motion.reducedValue} height={17 * scale} selectedIndex={selectedIndex} style={{ width: 64 * scale }}
      items={SPECIMENS.map(specimen => <Text key={specimen.name} style={[styles.dexNumber, { fontSize: 12 * scale, lineHeight: 17 * scale }]}>#{specimen.number}</Text>)} /></View>
    <HabitatSignature motion={creatureMotion} frontPhase={frontPhase} layer={0} gpuParticles={gpuParticles} {...signatureLayout} />
    <GestureDetector gesture={motion.gesture}>
      <Animated.View style={{ position: 'absolute', top: heroTop, left: 0, width, height: heroHeight }} accessible accessibilityRole="adjustable" accessibilityLabel={`Pokédex carousel, ${selected.name}`} accessibilityHint="Swipe horizontally to choose a Pokémon. Tap to see its signature move, or touch and hold to pet it" accessibilityValue={{ min: 1, max: 3, now: selectedIndex + 1, text: selected.name }} accessibilityActions={[{ name: 'increment', label: 'Next Pokémon' }, { name: 'decrement', label: 'Previous Pokémon' }]} onAccessibilityAction={event => motion.select((selectedIndex + (event.nativeEvent.actionName === 'increment' ? 1 : 2)) % 3)}>
        <DexGhost text={selected.number} reducedMotion={reduced} width={width} scale={scale} />
        {SPECIMENS.map((specimen, index) => <HabitatCreature key={specimen.name} index={index} source={specimen.image} motion={creatureMotion} frontPhase={frontPhase} light={light} size={creatureSize} left={(width - creatureSize) / 2} top={(heroHeight - creatureSize) / 2} width={width} heroTop={heroTop} screenHeight={height} scale={scale} />)}
      </Animated.View>
    </GestureDetector>
    <HabitatAffection pet={motion.pet} petClock={motion.petClock} petUntil={motion.petUntil} position={motion.position} reduced={motion.reducedValue} width={width} heroTop={heroTop} heroHeight={heroHeight} size={creatureSize} />
    <HabitatHint top={heroTop + heroHeight + 2 * scale} scale={scale} reduced={reduced} touched={motion.manual} />
    <HabitatSignature motion={creatureMotion} frontPhase={frontPhase} layer={1} gpuParticles={gpuParticles} {...signatureLayout} />
    {webgpu && <HabitatGpuBoundary onFailure={gpuUnavailable}>
      <HabitatParticleFront webgpu={webgpu} canvas={particleFront} width={width} height={signatureLayout.height} onLaidOut={markFrontLaidOut} />
    </HabitatGpuBoundary>}
    <HabitatIdentityText reducedMotion={reduced} scale={scale} fontScale={window.fontScale} top={identityTop} specs={SPECIMENS} selectedIndex={selectedIndex} width={width} />
    <SpecimenTypes position={motion.position} reduced={motion.reducedValue} top={identityTop + identityHeight} scale={scale} selectedIndex={selectedIndex} />
    <HabitatPanels specs={SPECIMENS} position={motion.position} reduced={motion.reducedValue} reducedMotion={reduced} scale={scale} width={width}
      tabsTop={tabsTop} contentTop={descriptionTop} contentHeight={Math.max(24, factsTop - descriptionTop - 4 * scale)} tab={tab} onTab={chooseTab} selectedIndex={selectedIndex} />
    <SpecimenFacts position={motion.position} reduced={motion.reducedValue} top={factsTop} scale={scale} selectedIndex={selectedIndex} />
    <View pointerEvents="box-none" style={[styles.footer, { left: 27 * scale, right: 27 * scale, bottom: insets.bottom + 4 * scale, height: STRIP_SELECTED }]}><View style={styles.footerMark}><MaterialCommunityIcons name="pokeball" size={14 * scale} color="#A9B8A3" /><Text style={[styles.footerText, { fontSize: 8 * scale }]}>KANTO REGION</Text></View>
      <SpecimenStrip position={motion.position} onPick={motion.select} reduced={reduced} /><TextSwap position={motion.position} reduced={motion.reducedValue} height={12 * scale} selectedIndex={selectedIndex} style={{ width: 70 * scale }}
      items={SPECIMENS.map(specimen => <Text key={specimen.name} style={[styles.footerText, styles.alignEnd, { fontSize: 8 * scale, lineHeight: 12 * scale }]}>{specimen.number} / 151</Text>)} /></View>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, alignSelf: 'stretch', overflow: 'hidden', backgroundColor: '#111D19' },
  button: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
  buttonInner: { flex: 1, alignSelf: 'stretch', alignItems: 'center', justifyContent: 'center' },
  header: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', zIndex: 12 },
  eyebrowRow: { position: 'absolute', flexDirection: 'row', justifyContent: 'space-between', zIndex: 10 },
  eyebrow: { color: '#D0D8C9', letterSpacing: 3, fontWeight: '500' },
  dexNumber: { color: '#F1F4E7', letterSpacing: 2, fontWeight: '600', textAlign: 'right', fontVariant: ['tabular-nums'] },
  alignEnd: { textAlign: 'right' },
  typeWindow: { alignSelf: 'stretch' },
  ghost: { position: 'absolute', left: 0, opacity: 0.065 },
  creatureBody: { flex: 1, transformOrigin: '50% 97%' },
  strip: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  stripSlot: { position: 'absolute', width: STRIP_THUMB, height: STRIP_THUMB },
  identity: { position: 'absolute', left: 16, right: 16, alignItems: 'center', zIndex: 10 },
  typeRow: { flexDirection: 'row', justifyContent: 'center', alignSelf: 'stretch' },
  type: { flexDirection: 'row', alignItems: 'center', gap: 5, borderWidth: StyleSheet.hairlineWidth },
  facts: { position: 'absolute', zIndex: 10, flexDirection: 'row', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#D4E4DA19' },
  fact: { width: '50%', alignItems: 'center', gap: 5 },
  secondFact: { borderLeftWidth: StyleSheet.hairlineWidth, borderLeftColor: '#D4E4DA1A' },
  factValue: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  factNumber: { fontWeight: '600', color: '#EFF3E4' },
  factLabel: { color: '#A5B49D', letterSpacing: 1 },
  footer: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', zIndex: 10 },
  footerMark: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  footerText: { color: '#A1B297', letterSpacing: 2 },
});
