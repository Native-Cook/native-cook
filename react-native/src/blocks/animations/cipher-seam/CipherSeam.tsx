import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { useMotionPreference } from '@/shared/useMotionPreference';
import { PressScale } from '@/shared/PressScale';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, cancelAnimation, interpolate, useAnimatedProps, useAnimatedStyle, useDerivedValue, useSharedValue, withRepeat, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, Ellipse, Path, RadialGradient, Stop } from 'react-native-svg';
import { Blur, Canvas, Group, LinearGradient as SkiaGradient, Path as SkiaPath, Rect, Skia, vec } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 10.8;
// The reference card spans 0.52 of the phone: 210 × 135 on the 402 pt design width.
const CARD_WIDTH = 210;
const CARD_HEIGHT = 135;
const CHARACTERS = '08#0B8D0#8O08B#0';
const AnimatedInput = Animated.createAnimatedComponent(TextInput);
// The reference's token matrix: 35 small glyphs on a 5.7 pt pitch (7 pt × .6 em + 1.5 pt), 16 rows of 8 pt.
const GLYPHS = Array.from({ length: 8 }, (_, phase) => Array.from({ length: 16 }, (_, row) => Array.from({ length: 35 }, (_, column) => CHARACTERS[(row * 7 + column * 11 + row * column + phase * (column % 3)) % 16]).join('')).join('\n'));

function TokenField({ time, reduced }: { time: SharedValue<number>; reduced: boolean }) {
  const props = useAnimatedProps(() => { const text = GLYPHS[reduced ? 0 : Math.floor(time.get() * 12) % GLYPHS.length]; return { text, defaultValue: text }; });
  return <View style={[styles.card, styles.encoded]}><AnimatedInput animatedProps={props} defaultValue={GLYPHS[0]} editable={false} accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" multiline scrollEnabled={false} caretHidden allowFontScaling={false} underlineColorAndroid="transparent" pointerEvents="none" style={[styles.code, { fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace', includeFontPadding: false, textAlignVertical: 'top' }]} /></View>;
}

function Card() {
  return <LinearGradient colors={['#212027', '#1C1B21', '#18171C']} locations={[0, .55, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={[styles.card, styles.cardFace]}>
    <LinearGradient pointerEvents="none" colors={['#FFFFFF0C', '#FFFFFF00']} start={{ x: 0, y: 0 }} end={{ x: .6, y: .7 }} style={StyleSheet.absoluteFill} />
    <View style={styles.cardTop}>
      <View style={styles.cardTopLeft}><LinearGradient colors={['#F4F6EE', '#C9D4C6', '#EEE8D6']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.chip}><View style={styles.chipCenter} /><View style={styles.chipLine} /><View style={[styles.chipLine, styles.chipLineVertical]} /></LinearGradient><Ionicons name="wifi" size={14} color="#A9A6B4" style={{ transform: [{ rotate: '90deg' }] }} /></View>
      <View style={styles.cardCircles}><View style={styles.cardCircle} /><View style={[styles.cardCircle, styles.cardCircleBack]} /></View>
    </View>
    <Text allowFontScaling={false} style={styles.cardNumber}>4821 0000 5577 0816</Text>
    <View style={styles.cardBottom}><View><Text style={styles.cardLabel} allowFontScaling={false}>Card Holder</Text><Text style={styles.cardValue} allowFontScaling={false}>Alex River</Text></View><View><Text style={styles.cardLabel} allowFontScaling={false}>Expiry</Text><Text style={styles.cardValue} allowFontScaling={false}>08/29</Text></View><View style={{ width: 40 }} /></View>
  </LinearGradient>;
}

function Plane({ time, width, right, reduced, previous = false }: { time: SharedValue<number>; width: number; right: boolean; reduced: boolean; previous?: boolean }) {
  const scale = width / 402;
  const transform = useAnimatedStyle(() => {
    const t = time.get();
    // Measured left edge in the source's362px phone viewport. The converted
    // rectangle keeps travelling after the normal face clears the seam at7.8s.
    const sourceX = previous
      ? interpolate(t, [0, 2.5, 10.55, 10.8], [226, 416, 226, 226], 'clamp')
      : interpolate(t, [0, 2.75, 3, 3.5, 4, 4.5, 5, 5.5, 6, 6.5, 7, 7.5, 8, 9, 9.8, 10.8], [-188, -188, -167, -131, -92, -55, -17, 16, 55, 82, 120, 158, 197, 273, 333, 409], 'clamp');
    const position = reduced ? width / 2 - CARD_WIDTH * scale / 2 : sourceX / 362 * width;
    return { opacity: previous ? (reduced ? 0 : interpolate(t, [0, 2.5, 2.51, 10.55, 10.8], [1, 1, 0, 0, 1], 'clamp')) : 1, transform: [{ translateX: position - (right ? width / 2 : 0) }] };
  });
  return <View style={[styles.clip, { height: CARD_HEIGHT * scale, width: width / 2, left: right ? width / 2 : 0 }]}><Animated.View style={[styles.plane, transform]}><View style={{ transform: [{ scale }], transformOrigin: 'top left' }}>{right ? <TokenField time={time} reduced={reduced} /> : <Card />}</View></Animated.View></View>;
}

// Deterministic dust: 660 fine points confined to the card's lane, as in the reference,
// where nothing sits above or below the band. Three brightness layers, three paths.
function hash(i: number) { 'worklet'; const v = Math.sin(i * 127.1 + 311.7) * 43758.5453; return v - Math.floor(v); }
const DUST = Array.from({ length: 660 }, (_, i) => ({ x: hash(i + 1), y: hash(i + 501) + hash(i + 701) - 1, r: .42 + Math.pow(hash(i + 901), 4) * .7, phase: hash(i + 1301) * Math.PI * 2, layer: i % 3 }));
const SPARKS = Array.from({ length: 320 }, (_, i) => ({ y: hash(i + 2101), speed: .7 + hash(i + 2301) * 1.1, offset: hash(i + 2501), r: .5 + hash(i + 2701) * .8, reach: hash(i + 2901) }));

function useDustLayer(layer: number, time: SharedValue<number>, width: number, center: number, lane: number, reduced: boolean) {
  return useDerivedValue(() => {
    const t = reduced ? 0 : time.get();
    const path = Skia.PathBuilder.Make();
    for (let i = layer; i < DUST.length; i += 3) {
      const d = DUST[i];
      const u = (d.x + t * .004 * (1 + layer)) % 1;
      // A triangular spread (two hashes) is densest on the card's centre line; the lane sags
      // a little toward the screen edges, following the violet bowl below it.
      const y = center + d.y * lane * (1 + .25 * Math.abs(u - .5)) + Math.abs(u - .5) * 10 + Math.sin(t * .6 + d.phase) * 1.5;
      path.addCircle(u * width, y, d.r * (.85 + .3 * Math.sin(t * 2.2 + d.phase)));
    }
    return path.build();
  });
}

function DustField({ time, width, height, cardTop, cardHeight, reduced }: { time: SharedValue<number>; width: number; height: number; cardTop: number; cardHeight: number; reduced: boolean }) {
  const center = cardTop + cardHeight / 2 + 14, lane = cardHeight * .66;
  const layers = [useDustLayer(0, time, width, center, lane, reduced), useDustLayer(1, time, width, center, lane, reduced), useDustLayer(2, time, width, center, lane, reduced)];
  return <Canvas pointerEvents="none" style={{ position: 'absolute', left: 0, top: 0, width, height }}>
    <Group>
      <SkiaPath path={layers[2]} color="#FFFFFF" opacity={.38} />
      <SkiaPath path={layers[1]} color="#EDE6FF" opacity={.62} />
      <SkiaPath path={layers[0]} color="#FFFFFF" opacity={.9} />
    </Group>
  </Canvas>;
}

// The seam: a hairline of white inside two violet glows, tapering at both ends.
// While the card crosses it flares, and sparks peel off the boundary into the token side.
function Seam({ time, width, height, cardTop, cardHeight, reduced }: { time: SharedValue<number>; width: number; height: number; cardTop: number; cardHeight: number; reduced: boolean }) {
  const flare = useDerivedValue(() => reduced ? .35 : interpolate(time.get(), [5, 5.4, 7.6, 8.1], [0, 1, 1, 0], 'clamp'));
  const glow = useDerivedValue(() => reduced ? .8 : interpolate(time.get(), [0, 5, 5.4, 7.6, 8.1, 10.8], [.85, .85, 1, 1, .85, .85], 'clamp') * (.94 + .06 * Math.sin(time.get() * 23)));
  // Idle, the seam is a thin lens of light; while the card crosses, the boundary becomes a
  // white band about 12 pt wide whose right edge breaks into particles over the tokens.
  const coreWidth = useDerivedValue(() => 1.6 + flare.get() * 10.4);
  const coreX = useDerivedValue(() => width / 2 - 1 - flare.get() * 1);
  const coreTop = useDerivedValue(() => cardTop - cardHeight * .02 * flare.get());
  const coreHeight = useDerivedValue(() => cardHeight * (1 + .04 * flare.get()));
  const bandOpacity = useDerivedValue(() => flare.get());
  const bloomWidth = useDerivedValue(() => 10 + flare.get() * 4);
  const bloomX = useDerivedValue(() => width / 2 - 5 - flare.get() * 4);
  const edge = useDerivedValue(() => {
    const f = flare.get(), frame = Math.floor(time.get() * 24);
    const path = Skia.PathBuilder.Make();
    if (f <= .01) return path.build();
    for (let row = 0; row < 60; row++) {
      const jitter = hash(row * 31 + frame * 7), reach = hash(row * 17 + frame * 13 + 5);
      path.addRect(Skia.XYWHRect(width / 2 + 11 * f - 1, cardTop + row * cardHeight / 60, (1 + jitter * 7) * f, cardHeight / 60 + .4));
      if (reach > .4) path.addRect(Skia.XYWHRect(width / 2 + 11 * f + 4 + reach * reach * 16, cardTop + row * cardHeight / 60, 1.3, 1.3));
    }
    return path.build();
  });
  const sparks = useDerivedValue(() => {
    const t = time.get(), f = flare.get();
    const path = Skia.PathBuilder.Make();
    if (f <= .01) return path.build();
    for (let i = 0; i < SPARKS.length; i++) {
      const s = SPARKS[i];
      const age = ((t * s.speed + s.offset) % 1);
      const x = width / 2 + 10 * f + age * age * (10 + s.reach * 40);
      const y = cardTop + 2 + s.y * (cardHeight - 4) + Math.sin(age * 7 + i) * 1.2;
      path.addCircle(x, y, s.r * (1 - age * .7) * f);
    }
    return path.build();
  });
  const tall = cardHeight * 1.2, top = cardTop + cardHeight / 2 - tall / 2;
  const fade = [vec(0, top), vec(0, top + tall)];
  return <Canvas pointerEvents="none" style={{ position: 'absolute', left: 0, top: 0, width, height }}>
    <Group opacity={glow}>
      <Rect x={width / 2 - 22} y={top} width={44} height={tall} color="#7A4DFF" opacity={.55}><SkiaGradient start={fade[0]} end={fade[1]} colors={['#7A4DFF00', '#7A4DFF', '#7A4DFF00']} /><Blur blur={18} /></Rect>
      <Rect x={bloomX} y={top + tall * .08} width={bloomWidth} height={tall * .84} color="#C7AEFF"><SkiaGradient start={fade[0]} end={fade[1]} colors={['#9A6BFF00', '#A884FF', '#9A6BFF00']} /><Blur blur={5} /></Rect>
      <Rect x={width / 2 - .8} y={top + tall * .14} width={1.6} height={tall * .72} color="#FFFFFF"><SkiaGradient start={fade[0]} end={fade[1]} colors={['#FFFFFF00', '#FFFFFF', '#FFFFFF00']} /><Blur blur={.7} /></Rect>
    </Group>
    <Group opacity={bandOpacity}>
      <Rect x={width / 2 - 9} y={coreTop} width={12} height={coreHeight} color="#7B4BFF"><Blur blur={5} /></Rect>
      <Rect x={coreX} y={coreTop} width={coreWidth} height={coreHeight} color="#FFFFFF"><Blur blur={1.4} /></Rect>
      <SkiaPath path={edge} color="#FFFFFF" />
    </Group>
    <SkiaPath path={sparks} color="#FFFFFF" />
  </Canvas>;
}

export function CipherSeam({ autoPlay = false, previewTime, onLearnMore }: { autoPlay?: boolean; previewTime?: number; onLearnMore?: () => void }) {
  const insets = useSafeAreaInsets();
  const { width, height, fontScale } = useWindowDimensions();
  const reduced = useMotionPreference();
  const [info, setInfo] = useState(false);
  const [foreground, setForeground] = useState(true);
  const [manual, setManual] = useState(false);
  const normalizedPreview = previewTime === undefined || !Number.isFinite(previewTime) ? undefined : previewTime >= 0 ? previewTime % LOOP : ((previewTime % LOOP) + LOOP) % LOOP;
  const time = useSharedValue(normalizedPreview ?? 0);
  const dragStart = useSharedValue(0);
  const interactionEpoch = useRef(0);
  const dragOwner = useRef<number | null>(null);
  const [interacting, setInteracting] = useState(false);
  const heroWidth = Math.min(width, 560);
  const heroHeight = height < 740 || fontScale > 1.3 ? 260 : 370;
  const cardHeight = CARD_HEIGHT * heroWidth / 402;
  // Panel coordinates: the panel reaches 60 pt under the nav, the hero starts 1 pt below that.
  const [panelHeight, setPanelHeight] = useState(760);
  const heroBottom = 61 + heroHeight, cardBottom = 61 + heroHeight / 2 + cardHeight / 2, laneTop = cardBottom - cardHeight - 30;

  useEffect(() => { const sub = AppState.addEventListener('change', value => { setForeground(value === 'active'); if (value !== 'active') setInteracting(false); }); return () => sub.remove(); }, []);
  useEffect(() => {
    if (normalizedPreview !== undefined) { cancelAnimation(time); time.set(normalizedPreview); return; }
    if (!foreground || info || reduced || (!autoPlay && !manual)) { cancelAnimation(time); return; }
    if (autoPlay && !manual) {
      time.set(0);
      time.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear }), -1));
    }
  }, [autoPlay, foreground, info, manual, normalizedPreview, reduced, time]);
  useEffect(() => () => cancelAnimation(time), [time]);
  useEffect(() => {
    if (!manual || interacting || info || !foreground) return;
    const timer = setTimeout(() => setManual(false), 2000);
    return () => clearTimeout(timer);
  }, [foreground, info, interacting, manual]);

  const begin = useCallback(() => { const owner = ++interactionEpoch.current; setManual(true); setInteracting(true); return owner; }, []);
  const release = useCallback((owner?: number) => { if (owner === undefined || owner === interactionEpoch.current) setInteracting(false); }, []);
  const beginDrag = useCallback(() => { dragOwner.current = begin(); }, [begin]);
  const releaseDrag = useCallback(() => { const owner = dragOwner.current; dragOwner.current = null; if (owner !== null) release(owner); }, [release]);
  const replay = () => {
    const owner = begin();
    cancelAnimation(time);
    time.set(reduced ? 6 : 2.75);
    if (!reduced) time.set(withTiming(10.5, { duration: 7750, easing: Easing.linear }, () => { scheduleOnRN(release, owner); }));
    else release(owner);
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  };
  const drag = useMemo(() => Gesture.Pan().maxPointers(1).activeOffsetX([-6, 6]).failOffsetY([-12, 12]).enabled(!reduced)
    // Deferred RNGH events: the interaction refs are touched only after scheduleOnRN runs.
    // eslint-disable-next-line react-hooks/refs
    .onStart(() => { cancelAnimation(time); dragStart.set(Math.max(2.75, Math.min(10.5, time.get()))); scheduleOnRN(beginDrag); })
    .onUpdate(event => { time.set(Math.max(2.75, Math.min(10.5, dragStart.get() + event.translationX / heroWidth * 7.75))); })
    .onEnd(event => { const rightward = Math.abs(event.velocityX) > 110 ? event.velocityX > 0 : time.get() > 6.5; const target = rightward ? 10.5 : 2.75; time.set(withSpring(target, { duration: 400, dampingRatio: 0.8, velocity: event.velocityX / heroWidth * 7.75, overshootClamping: true })); })
    // eslint-disable-next-line react-hooks/refs
    .onFinalize(() => { scheduleOnRN(releaseDrag); }), [beginDrag, dragStart, heroWidth, reduced, releaseDrag, time]);

  return <View key={`type-${fontScale}`} style={styles.screen}>
    <StatusBar style="light" />
    <ProgressiveBlurViewport style={{ flex: 1 }} tint="dark" edgeSize={28}><ScrollView contentContainerStyle={{ paddingTop: insets.top, paddingBottom: insets.bottom + 90 }} showsVerticalScrollIndicator={false}>
      <View style={styles.nav}>
        <Svg width={26} height={22} viewBox="0 0 26 22"><Path d="M13 1 25 7 13 13 1 7Z" fill="#F4F1FF" /><Path d="M1 11.5 13 17.5 25 11.5" stroke="#F4F1FF" strokeWidth={2.2} fill="none" strokeLinejoin="round" /><Path d="M1 15.5 13 21.5 25 15.5" stroke="#F4F1FF" strokeWidth={2.2} fill="none" strokeLinejoin="round" /></Svg>
        <View style={styles.navRight}>
          <Text maxFontSizeMultiplier={1.3} style={styles.navLink}>Log in</Text>
          <PressScale accessibilityRole="button" accessibilityLabel="Talk to an expert about the tokenization demo" reduced={reduced} onPress={() => { begin(); setInfo(true); onLearnMore?.(); }} style={styles.navPill}><Text maxFontSizeMultiplier={1.3} style={styles.navPillText}>Talk to an Expert</Text></PressScale>
          <View style={styles.navMenu}><Ionicons name="menu" size={18} color="#C9C3D6" /></View>
        </View>
      </View>
      <LinearGradient colors={['#06040F', '#06040F', '#2C1478', '#4B2FD6', '#6A45F5', '#A48CFF']} locations={[0, laneTop / panelHeight, (cardBottom + 20) / panelHeight, (heroBottom + 90) / panelHeight, 0.88, 1]} style={styles.heroPanel} onLayout={event => setPanelHeight(event.nativeEvent.layout.height)}>
        <Svg pointerEvents="none" width={heroWidth} height={panelHeight} style={StyleSheet.absoluteFill}>
          <Defs><RadialGradient id="cipher-bowl" cx="50%" cy="50%" rx="50%" ry="50%"><Stop offset="0" stopColor="#06040F" stopOpacity="1" /><Stop offset="0.7" stopColor="#06040F" stopOpacity="1" /><Stop offset="1" stopColor="#06040F" stopOpacity="0" /></RadialGradient></Defs>
          <Ellipse cx={heroWidth / 2} cy={cardBottom - 92} rx={heroWidth * .5} ry={172} fill="url(#cipher-bowl)" />
        </Svg>
        <GestureDetector gesture={drag}>
          <View style={[styles.visual, { width: heroWidth, height: heroHeight }]} accessible accessibilityRole="image" accessibilityLabel="A sample payment card crosses a violet seam and becomes a token pattern. Drag across to scrub or use Replay below.">
            <DustField time={time} width={heroWidth} height={heroHeight} cardTop={(heroHeight - cardHeight) / 2} cardHeight={cardHeight} reduced={reduced} />
            <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.cardTrack, { top: (heroHeight - cardHeight) / 2, height: cardHeight }]}><Plane time={time} width={heroWidth} right={false} reduced={reduced} /><Plane time={time} width={heroWidth} right reduced={reduced} /><Plane time={time} width={heroWidth} right previous reduced={reduced} /></View>
            <Seam time={time} width={heroWidth} height={heroHeight} cardTop={(heroHeight - cardHeight) / 2} cardHeight={cardHeight} reduced={reduced} />
          </View>
        </GestureDetector>
        <View style={styles.copy}><Text accessibilityRole="header" maxFontSizeMultiplier={1.5} style={styles.title}>Flexible Payments Security</Text><Text style={styles.subtitle}>Maximum protection, minimum compliance{'\n'}burden</Text><Text style={styles.description}>Take control of your payments stack — easily tokenize cards, optimize margins, comply with PCI, avoid gateway lock-in, or spin-up card issuing programs.</Text><PressScale accessibilityRole="button" accessibilityLabel="Replay the card transformation" reduced={reduced} onPress={replay} style={styles.cta}><Text style={styles.ctaText}>Talk to an Expert</Text><Ionicons name="chevron-forward" size={15} color="#120A22" /></PressScale></View>
      </LinearGradient>
      <View style={styles.logos} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <View style={styles.logoItem}><Ionicons name="reorder-three" size={18} color="#E9E6F2" /><Text style={[styles.logoText, { letterSpacing: 1.6 }]}>LEDGERLY</Text></View>
        <View style={styles.logoItem}><Ionicons name="sparkles" size={14} color="#E9E6F2" /><Text style={[styles.logoText, { fontWeight: '700', letterSpacing: -0.3, fontSize: 16 }]}>Tripwell</Text></View>
        <View style={styles.logoItem}><Ionicons name="radio-button-on" size={15} color="#E9E6F2" /><Text style={[styles.logoText, { fontWeight: '400', letterSpacing: 0, fontSize: 15 }]}>orbita</Text></View>
      </View>
      <View style={styles.domain}><Text style={styles.domainText}>cipher.dev</Text></View>
    </ScrollView></ProgressiveBlurViewport>
    <PressScale accessibilityRole="button" accessibilityLabel="Chat with us" reduced={reduced} onPress={() => { begin(); setInfo(true); }} style={[styles.fab, { bottom: insets.bottom + 37 }]}><Ionicons name="chatbox" size={22} color="#FFFFFF" /></PressScale>
    <Modal visible={info} transparent animationType="fade" onRequestClose={() => { setInfo(false); release(); }}><View style={styles.modalBackdrop}><View style={[styles.modal, { maxHeight: height - insets.top - insets.bottom - 48 }]}><ProgressiveBlurViewport style={{ flexShrink: 1 }} tint="dark" edgeSize={28} enabled={info}><ScrollView><Text style={styles.modalTitle}>One boundary. Two representations.</Text><Text style={styles.modalCopy}>The card and the encoded pattern move as one plane. A fixed violet seam clips each representation, so the transformation stays attached to the card.</Text><Text style={styles.modalCopy}>This is an interactive visual example. It does not process, store, or encrypt payment information.</Text></ScrollView></ProgressiveBlurViewport><Pressable accessibilityRole="button" onPress={() => { setInfo(false); release(); }} style={styles.modalClose}><Text style={styles.ctaText}>Got it</Text></Pressable></View></View></Modal>
  </View>;
}

const styles = StyleSheet.create({
  fab: { position: 'absolute', right: 6, width: 44, height: 44, borderRadius: 22, backgroundColor: '#5B33F2', alignItems: 'center', justifyContent: 'center', boxShadow: '0px 8px 20px -6px rgba(91,51,242,0.8)' },
  domainText: { color: '#BDB6CC', fontSize: 12 },
  domain: { alignSelf: 'center', height: 28, paddingHorizontal: 14, borderRadius: 14, backgroundColor: '#1B1724', justifyContent: 'center', marginTop: 4 },
  logoText: { color: '#E9E6F2', fontSize: 13, fontWeight: '800' },
  logoItem: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  logos: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 22, marginTop: 67 },
  navMenu: { width: 40, height: 30, borderRadius: 15, backgroundColor: '#2A2735', alignItems: 'center', justifyContent: 'center' },
  navPillText: { fontSize: 13, fontWeight: '600', color: '#110A20' },
  navPill: { height: 28, paddingHorizontal: 11, borderRadius: 14, backgroundColor: '#FFFFFF', justifyContent: 'center' },
  navLink: { color: '#E8E4F2', fontSize: 13, fontWeight: '500' },
  navRight: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  nav: { height: 44, marginHorizontal: 10, marginTop: 6, borderRadius: 22, borderWidth: 1, borderColor: '#FFFFFF1F', backgroundColor: '#0B0718', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingLeft: 14, paddingRight: 6, zIndex: 2 },
  screen: { flex: 1, backgroundColor: '#070411' }, header: { minHeight: 56, paddingHorizontal: 24, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, logo: { flexDirection: 'row', alignItems: 'center', gap: 8 }, wordmark: { fontSize: 23, color: '#F9F6FF', fontWeight: '600', letterSpacing: -1 }, help: { minHeight: 48, paddingHorizontal: 12, flexDirection: 'row', gap: 6, alignItems: 'center' }, helpText: { fontSize: 11, color: '#D4C9F8' }, heroPanel: { borderBottomLeftRadius: 32, borderBottomRightRadius: 32, overflow: 'hidden', alignItems: 'center', marginTop: -60, paddingTop: 60 }, visual: { overflow: 'hidden', marginTop: 1 }, field: { position: 'absolute', top: 30 }, cardTrack: { position: 'absolute', left: 0, right: 0, height: CARD_HEIGHT }, clip: { position: 'absolute', top: 0, height: CARD_HEIGHT, overflow: 'hidden' }, plane: { width: CARD_WIDTH, height: CARD_HEIGHT }, card: { width: CARD_WIDTH, height: CARD_HEIGHT, borderRadius: 11, borderWidth: 1, borderColor: '#FFFFFF0D', padding: 13, overflow: 'hidden' }, cardFace: { justifyContent: 'space-between' }, cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, cardTopLeft: { flexDirection: 'row', alignItems: 'center', gap: 8 }, chip: { width: 30, height: 23, borderRadius: 6, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }, chipCenter: { width: 12, height: 15, borderRadius: 3, borderWidth: 0.6, borderColor: '#8F978C' }, chipLine: { width: 30, height: 0.6, backgroundColor: '#8F978C', position: 'absolute' }, chipLineVertical: { width: 0.6, height: 23 }, cardNumber: { fontSize: 10.5, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', color: '#E2DFE9', letterSpacing: 0 }, cardBottom: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' }, cardLabel: { color: '#8D889B', fontSize: 6.5, marginBottom: 2 }, cardValue: { color: '#E4E0EC', fontSize: 9, fontWeight: '500' }, cardCircles: { flexDirection: 'row' }, cardCircle: { width: 15, height: 15, borderRadius: 7.5, backgroundColor: '#F4F2F8' }, cardCircleBack: { marginLeft: -6, borderWidth: 1.5, borderColor: '#1F1E25', width: 16.5, height: 16.5, borderRadius: 8.25, marginTop: -0.75 }, encoded: { backgroundColor: '#05030E59', borderColor: '#00000000', paddingHorizontal: 2, paddingVertical: 2 }, code: { width: '100%', height: '100%', padding: 0, color: '#E9E6F1', fontSize: 7, lineHeight: 8, letterSpacing: 1.5, fontFamily: 'Courier', fontWeight: '700', opacity: 0.62 }, beam: { position: 'absolute', width: 26, height: CARD_HEIGHT, borderRadius: 100, overflow: 'hidden' }, beamCore: { position: 'absolute', left: 12, top: 4, bottom: 4, width: 2, borderRadius: 2, backgroundColor: '#FFFFFF' }, copy: { alignItems: 'center', paddingHorizontal: 10, paddingBottom: 36, width: '100%', marginTop: 22 }, tag: { flexDirection: 'row', gap: 6, alignItems: 'center', marginBottom: 18 }, statusDot: { width: 4, height: 4, borderRadius: 2, backgroundColor: '#C8B9FF' }, tagText: { flexShrink: 1, textAlign: 'center', color: '#B8A2E6', fontSize: 8, letterSpacing: 1.3, fontWeight: '600' }, title: { color: '#FFFFFF', fontSize: 28, lineHeight: 34, letterSpacing: -0.5, textAlign: 'center', fontWeight: '500' }, subtitle: { fontSize: 15, lineHeight: 18, color: '#E3DAFF', marginTop: 6, textAlign: 'center' }, description: { fontSize: 11.4, lineHeight: 20, color: '#E6DEFF', textAlign: 'center', marginTop: 12, maxWidth: 380 }, cta: { height: 34, paddingHorizontal: 14, borderRadius: 17, flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: '#FFFFFF', marginTop: 14 }, ctaText: { textAlign: 'center', fontSize: 14, fontWeight: '500', color: '#120A22' }, demoNote: { fontSize: 9, color: '#E9DFFF', marginTop: 16 }, bottom: { paddingHorizontal: 40, paddingTop: 28, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 20 }, bottomItem: { flex: 1, gap: 8, alignItems: 'center' }, bottomLabel: { textAlign: 'center', fontSize: 8, letterSpacing: 1.2, color: '#A79AB9' }, bottomLine: { height: 1, width: 44, backgroundColor: '#463252' }, modalBackdrop: { flex: 1, backgroundColor: '#00000099', justifyContent: 'center', padding: 24 }, modal: { backgroundColor: '#201332', borderRadius: 28, padding: 24 }, modalTitle: { fontSize: 24, lineHeight: 29, color: '#F4EAFE', fontWeight: '600', marginBottom: 18 }, modalCopy: { fontSize: 15, lineHeight: 23, color: '#C5B7D5', marginBottom: 18 }, modalClose: { minHeight: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: '#F0E5FF', marginTop: 8 },
});
