import { Canvas, LinearGradient as SkiaGradient, RoundedRect, vec } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withDelay, withSpring } from 'react-native-reanimated';

import { Character } from '../characters';
import { DotField, Mark } from '../glyphs';
import { Centered, Frame, PopIn, PressScale, Reveal, SFSymbol, Wipe } from '../primitives';
import { hex, palette, spring, white } from '../theme';

const NAMES = ['@ScalpX', 'RiskGuard', 'TrendHunter'];

type Slot = { x: number; y: number; angle: number; scale: number; opacity: number; z: number };

/** Where each bot stands when it is in front (the art is not centred the same way in each). */
const FRONT: [number, number][] = [[207, 276], [215, 270], [211, 272]];
const BACK_LEFT: Slot = { x: 103, y: 220, angle: -9, scale: 0.8, opacity: 0.48, z: 1 };
const BACK_RIGHT: Slot = { x: 304, y: 323, angle: 10, scale: 0.8, opacity: 0.5, z: 2 };
const ENTRY_X = 212;
const ENTRY_Y = 250;

function slotFor(bot: number, focus: number): Slot {
  if (bot === focus) return { x: FRONT[bot][0], y: FRONT[bot][1], angle: 0, scale: 1, opacity: 1, z: 3 };
  return bot === (focus + 2) % 3 ? BACK_LEFT : BACK_RIGHT;
}

/**
 * Step 6: three frosted bot cards (182 × 236). The front one stands at x 121…303, y 159;
 * the other two lean back to the upper left and lower right. The SwiftUI version also blurs
 * the back cards 1.3 pt; React Native cannot blur a live view, so they are only dimmed.
 */
export function ProScene({ focus, select, reduced, focusResponse }: { focus: number; select: (bot: number) => void; reduced: boolean; focusResponse: number }) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      {[0, 1, 2].map((bot) => (
        <ProSlot key={bot} bot={bot} focus={focus} reduced={reduced} focusResponse={focusResponse} onPress={() => select(bot)} />
      ))}

      <Centered x={200.5} y={523}>
        <PopIn reduced={reduced} delay={0.08} from={0.6} damping={0.65} style={{ flexDirection: 'row', alignItems: 'center', gap: 5, marginBottom: 8, transform: [{ translateX: -0.5 }] }}>
          <ProBadge />
          <Text style={{ fontSize: 12, fontWeight: '600', color: '#fff' }}>Bots</Text>
        </PopIn>
        <Wipe reduced={reduced} delay={0.18}>
          <View style={{ flexDirection: 'row', alignItems: 'center' }} accessible accessibilityLabel="More Bots">
            <Text style={TITLE}>More B</Text>
            <View style={{ marginHorizontal: 1.2, transform: [{ translateY: 3.2 }] }}>
              <Mark style="outline" size={20.5} />
            </View>
            <Text style={TITLE}>ts</Text>
          </View>
        </Wipe>
        <Reveal reduced={reduced} delay={0.34} y={8}>
          <Text style={TITLE}>More Possibilities.</Text>
        </Reveal>
        <Reveal reduced={reduced} delay={0.45} y={4} style={{ marginTop: 8 }}>
          <Text style={{ fontSize: 14.2, letterSpacing: -0.15, color: hex('#7c7c7c') }}>Access more bots and trading strategies.</Text>
        </Reveal>
      </Centered>

      <Frame x={200.5} y={668.8} width={342.4} height={72.3}>
        <Reveal reduced={reduced} y={16} duration={0.6}>
          <Plan />
        </Reveal>
      </Frame>
    </View>
  );
}

const TITLE = { fontSize: 32.3, fontWeight: '400', letterSpacing: -0.6, color: palette.ink } as const;

function ProSlot({ bot, focus, reduced, focusResponse, onPress }: { bot: number; focus: number; reduced: boolean; focusResponse: number; onPress: () => void }) {
  const slot = slotFor(bot, focus);
  const front = bot === focus;
  const x = useSharedValue(reduced ? slot.x : ENTRY_X);
  const y = useSharedValue(reduced ? slot.y : ENTRY_Y);
  const angle = useSharedValue(reduced ? slot.angle : 0);
  const scale = useSharedValue(reduced ? slot.scale : front ? 0.32 : 0.5);
  const opacity = useSharedValue(0);
  const entered = useSharedValue(false);

  useEffect(() => {
    const first = !entered.get();
    entered.set(true);
    if (reduced) {
      x.set(slot.x);
      y.set(slot.y);
      angle.set(slot.angle);
      scale.set(slot.scale);
      opacity.set(slot.opacity);
      return;
    }
    // Entry: the front card first, the others 0.14 s later. Later focus changes move all three at once.
    const config = first ? spring(0.7, 0.84) : spring(focusResponse, 0.86);
    const delay = first && !front ? 140 : 0;
    const go = (value: number) => withDelay(delay, withSpring(value, config));
    x.set(go(slot.x));
    y.set(go(slot.y));
    angle.set(go(slot.angle));
    scale.set(go(slot.scale));
    opacity.set(go(slot.opacity));
  }, [angle, entered, focusResponse, front, opacity, reduced, scale, slot.angle, slot.opacity, slot.scale, slot.x, slot.y, x, y]);

  const style = useAnimatedStyle(() => ({
    opacity: Math.max(0, Math.min(1, opacity.get())),
    transform: [{ translateX: x.get() - 91 }, { translateY: y.get() - 118 }, { rotate: `${angle.get()}deg` }, { scale: scale.get() }],
  }));
  return (
    <Animated.View style={[{ position: 'absolute', left: 0, top: 0, zIndex: slot.z }, style]}>
      <PressScale onPress={onPress} accessibilityLabel={`Focus ${NAMES[bot]}`} selected={front}>
        <ProCard kind={bot} />
      </PressScale>
    </Animated.View>
  );
}

function ProBadge() {
  return (
    <LinearGradient colors={[hex('#eec6dc'), hex('#f4d9c6')]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }}
      style={{ width: 46, height: 19.3, borderRadius: 9.65, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 2 }}>
      <SFSymbol name="diamond.fill" size={9} weight="bold" color={hex('#141414')} style={{ transform: [{ scaleX: 1.25 }] }} />
      <Text style={{ fontSize: 14, fontWeight: '500', letterSpacing: -0.3, color: hex('#141414') }}>PRO</Text>
    </LinearGradient>
  );
}

function ProCard({ kind }: { kind: number }) {
  return (
    <View style={{ width: 182, height: 236, shadowColor: '#000', shadowOpacity: 0.6, shadowRadius: 20, shadowOffset: { width: 0, height: 14 } }}>
      <View style={{ width: 182, height: 236, borderRadius: 30, borderCurve: 'continuous', overflow: 'hidden', alignItems: 'center' }}>
        {Platform.OS === 'ios' ? (
          <BlurView tint="systemUltraThinMaterialDark" intensity={100} style={StyleSheet.absoluteFill} />
        ) : (
          <View style={[StyleSheet.absoluteFill, { backgroundColor: hex('#2a2a2a', 0.72) }]} />
        )}
        <LinearGradient colors={[white(0.07), white(0.13)]} style={StyleSheet.absoluteFill} />
        <View style={{ marginTop: 6, width: 172, height: 160, borderRadius: 26, borderCurve: 'continuous', backgroundColor: hex('#0b0b0b'), overflow: 'hidden', alignItems: 'center', justifyContent: 'center' }}>
          <DotField spacing={5} opacity={0.05} style={StyleSheet.absoluteFill} />
          {kind === 0 && <Character kind="purple" size={100} saturation={0.82} frost={0.4} />}
          {kind === 1 && (
            <View style={{ transform: [{ translateX: 2 }, { translateY: -2 }] }}>
              <Character kind="green" size={101} saturation={0.8} brightness={-0.04} />
            </View>
          )}
          {kind === 2 && <Character kind="red" size={90} />}
          {kind === 0 && (
            <View style={{ position: 'absolute', left: 12, top: 8, width: 31, height: 32, borderRadius: 9, borderCurve: 'continuous', backgroundColor: white(0.05), borderWidth: 1, borderColor: white(0.16), alignItems: 'center', justifyContent: 'center' }}>
              <SFSymbol name="lock" size={12} color="#fff" />
            </View>
          )}
        </View>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <RoundedRect x={0.6} y={0.6} width={180.8} height={234.8} r={29.4} style="stroke" strokeWidth={1.2}>
            <SkiaGradient start={vec(0, 0)} end={vec(0, 236)} colors={[white(0.22), white(0.06), white(0.14)]} />
          </RoundedRect>
        </Canvas>
        <Text style={{ position: 'absolute', top: 177, fontSize: kind === 0 ? 12.5 : 11.6, fontWeight: '600', color: '#fff' }}>{NAMES[kind]}</Text>
        <Text style={{ position: 'absolute', top: kind === 0 ? 201 : 194, fontSize: kind === 0 ? 10.3 : 9.5, letterSpacing: -0.1, lineHeight: kind === 0 ? undefined : 10.4, textAlign: 'center', color: hex('#7a7a7a') }}>
          {kind === 0 ? 'Fast Trade Detection' : 'Analyzes short-term price\nmovements for quick trade setups.'}
        </Text>
      </View>
    </View>
  );
}

function Plan() {
  return (
    <View accessible accessibilityLabel="Pro, access to all AI trading bots, $9.99 per month"
      style={{ width: 342.4, height: 72.3, borderRadius: 22, borderCurve: 'continuous', backgroundColor: hex('#161616'), borderWidth: 1, borderColor: white(0.06), flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingLeft: 15.2, paddingRight: 15.2 }}>
      <View style={{ gap: 7 }}>
        <Text style={{ fontSize: 15.2, fontWeight: '500', color: '#fff' }}>Pro</Text>
        <Text style={{ fontSize: 11.4, letterSpacing: -0.1, color: hex('#6c6c6c') }}>Access to all AI trading bots</Text>
      </View>
      <Text style={{ fontSize: 16.3, fontWeight: '500', letterSpacing: -0.3 }}>
        <Text style={{ color: '#fff' }}>$9.99</Text>
        <Text style={{ color: hex('#6c6c6c') }}> /Mo</Text>
      </Text>
    </View>
  );
}
