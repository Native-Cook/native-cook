import { Canvas, LinearGradient, RadialGradient, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { Pressable, StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withDelay, withTiming } from 'react-native-reanimated';

import { DotField, Mark } from '../glyphs';
import { Caption, Centered, Frame, Panel, PopIn, Reveal, SFSymbol, formatAmount, headingText, useEntrance, useSettle } from '../primitives';
import { SETTLE_CURVE, hex, white } from '../theme';
import { DashboardControls } from './Setup';

type Actions = { configure: () => void; overview: () => void };

const CARD_W = 280;
const CARD_H = 174;

/**
 * Step 5: a luminous blue results card (280 × 174, tilted 4.5°) resting on a frosted card and
 * a dark tray with Risk Overview / Configure Bot, above "Your Trading Co-pilot".
 * The card leaves its tray in two moves: it rises flat with the tray (0 – 0.6 s), then keeps
 * climbing as it tips into its pose.
 */
export function ResultsScene({ reduced, configure, overview }: { reduced: boolean } & Actions) {
  const tray = useEntrance(reduced, { spring: [0.6, 0.92] });
  const lift = useEntrance(reduced, { spring: [0.55, 0.95] });
  const settled = useSharedValue(reduced ? 1 : 0);
  useEffect(() => {
    settled.set(reduced ? 1 : withDelay(600, withTiming(1, { duration: 1000, easing: SETTLE_CURVE })));
  }, [reduced, settled]);
  const amount = useSettle(1_022.11, 1_284.5, 0.97, 0.1, 0.4, 2, reduced, 0.05);

  const trayStyle = useAnimatedStyle(() => ({ opacity: tray.get(), transform: [{ translateY: reduced ? 0 : (1 - tray.get()) * 24 }] }));
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: settled.get(),
    transform: [{ translateY: (1 - settled.get()) * 40 }, { rotate: `${-5 * settled.get()}deg` }],
  }));
  const cardStyle = useAnimatedStyle(() => {
    const s = settled.get();
    const on = reduced ? 1 : lift.get();
    return {
      opacity: Math.min(1, lift.get()),
      transform: [
        { translateY: (150 - 72 * on) * (1 - s) },
        { scale: (0.84 + 0.11 * on) * (1 - s) + s },
        { rotate: `${4.5 * s}deg` },
        // SwiftUI tips the card 7° back in 3D; a 3D layer here intersects the frosted card
        // behind it on iOS, so the tip is a flat foreshortening instead.
        { scaleY: 1 - 0.0075 * s },
      ],
    };
  });

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Frame x={200} y={401} width={300} height={278}>
        <Animated.View style={trayStyle}>
          <Tray configure={configure} overview={overview} />
        </Animated.View>
      </Frame>
      <Frame x={204} y={344} width={282} height={152}>
        <Animated.View style={ghostStyle} pointerEvents="none">
          <GhostCard />
        </Animated.View>
      </Frame>
      <Frame x={195} y={303} width={CARD_W} height={CARD_H}>
        <Animated.View style={cardStyle}>
          <ResultCard amount={amount} overview={overview} />
        </Animated.View>
      </Frame>

      <Centered x={201} y={603.5}>
        <View style={{ height: 39, flexDirection: 'row', alignItems: 'center', gap: 7 }}>
          <Text style={headingText}>Your</Text>
          <PopIn reduced={reduced} delay={0.12} style={{ transform: [{ translateY: 2 }] }}>
            <Mark size={33} />
          </PopIn>
          <Reveal reduced={reduced} delay={0.2} y={4}>
            <Text style={headingText}>Trading</Text>
          </Reveal>
        </View>
        <Reveal reduced={reduced} delay={0.36} y={10} style={{ marginTop: -3 }}>
          <Text style={headingText}>Co-pilot</Text>
        </Reveal>
      </Centered>
      <Centered x={201} y={681.7}>
        <Reveal reduced={reduced} delay={0.42} y={6}>
          <Caption text={'Analyze the market, develop smarter strategies, and\ncontrol every trade.'} />
        </Reveal>
      </Centered>
    </View>
  );
}

/** The dark base (300 × 278 at x 50, y 262): a dotted tray and two controls. */
function Tray({ configure, overview }: Actions) {
  return (
    <Panel width={300} height={278} radius={34} fill={[hex('#0e0e0e'), hex('#0e0e0e')]} stroke={[white(0.07), white(0.07)]}>
      <View style={{ position: 'absolute', left: 8, top: 6, width: 284, height: 200, borderRadius: 28, borderCurve: 'continuous', backgroundColor: hex('#080808'), overflow: 'hidden', borderWidth: 1, borderColor: white(0.04) }}>
        <DotField spacing={5} opacity={0.05} style={StyleSheet.absoluteFill} />
      </View>
      <View style={{ position: 'absolute', top: 220, left: 0, right: 0, alignItems: 'center' }}>
        <DashboardControls configure={configure} overview={overview} width={278} height={46} fontSize={9.4} />
      </View>
    </Panel>
  );
}

function GhostCard() {
  return (
    <Panel width={282} height={152} radius={26} fill={[hex('#5a6070', 0.55), hex('#2a2c32', 0.75)]} stroke={[white(0.12), white(0.12)]} style={{ overflow: 'hidden', borderRadius: 26 }}>
      <Centered x={141} y={76 + 30} box={300}>
        <Text style={{ fontSize: 38, fontWeight: '600', color: white(0.08), textShadowColor: white(0.25), textShadowRadius: 6 }}>+$1,284.50</Text>
      </Centered>
    </Panel>
  );
}

const SPARKS: [number, number][] = [[0, -30], [-28, 4], [30, 6], [22, -22]];

function ResultCard({ amount, overview }: { amount: number; overview: () => void }) {
  return (
    <View style={{ width: CARD_W, height: CARD_H, shadowColor: '#000', shadowOpacity: 0.5, shadowRadius: 16, shadowOffset: { width: 0, height: 12 } }}>
      <View style={{ width: CARD_W, height: CARD_H, borderRadius: 26, borderCurve: 'continuous', overflow: 'hidden' }}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Rect x={0} y={0} width={CARD_W} height={CARD_H}>
            <LinearGradient start={vec(0, 0)} end={vec(0, CARD_H)} colors={[hex('#2a62e0'), hex('#2f66e2'), hex('#4f78d9'), hex('#7690c8')]} positions={[0, 0.35, 0.62, 1]} />
          </Rect>
          <Rect x={0} y={0} width={CARD_W} height={CARD_H}>
            <RadialGradient c={vec(CARD_W * 0.15, CARD_H)} r={150} colors={[hex('#c8d2ea', 0.28), hex('#c8d2ea', 0)]} />
          </Rect>
          <Rect x={0} y={0} width={CARD_W} height={CARD_H}>
            <RadialGradient c={vec(CARD_W * 0.52, CARD_H * 0.3)} r={60} colors={[hex('#1d4fff', 0.45), hex('#1d4fff', 0)]} />
          </Rect>
          <RoundedRect x={0.5} y={0.5} width={CARD_W - 1} height={CARD_H - 1} r={25.5} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, 0)} end={vec(0, CARD_H)} colors={[white(0.3), white(0.05), white(0.4)]} />
          </RoundedRect>
        </Canvas>
        {SPARKS.map(([dx, dy], index) => (
          <Frame key={index} x={140 + dx} y={40 + dy} width={2} height={2} style={{ borderRadius: 1, backgroundColor: white(0.75) }} />
        ))}
        <Frame x={140} y={40} width={38} height={38} style={{ shadowColor: '#fff', shadowOpacity: 0.35, shadowRadius: 10 }}>
          <Mark style="white" size={38} />
        </Frame>
        <Frame x={250} y={30} width={33} height={33}>
          <Pressable onPress={overview} accessibilityRole="button" accessibilityLabel="View simulated results"
            style={{ width: 33, height: 33, borderRadius: 11, borderCurve: 'continuous', backgroundColor: hex('#0b0b0b'), alignItems: 'center', justifyContent: 'center' }}>
            <SFSymbol name="square.and.arrow.up" size={14} weight="medium" color="#fff" />
          </Pressable>
        </Frame>
        <Centered x={140} y={83} box={300}>
          <Text style={{ fontSize: 12.4, letterSpacing: -0.15, color: white(0.88) }}>Simulated Trading Results</Text>
        </Centered>
        <Centered x={140} y={113} box={300}>
          <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 1 }}>
            <Text style={[AMOUNT, { color: white(0.45) }]}>+</Text>
            <Text style={[AMOUNT, { color: '#fff' }]}>{`$${formatAmount(amount, 2)}`}</Text>
          </View>
        </Centered>
      </View>
    </View>
  );
}

const AMOUNT: TextStyle = { fontSize: 31, fontWeight: '600', letterSpacing: -0.3, fontVariant: ['tabular-nums'] };
