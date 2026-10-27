import type { ReactNode } from 'react';
import { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';

import { Character, LivingOrb } from '../characters';
import { Bars, BellCurve, Candles, DotField, Mark } from '../glyphs';
import { Centered, Leading, Panel, PopIn, PressScale, Reveal, Wipe, headingText, useEntrance } from '../primitives';
import { hex, spring, white } from '../theme';

const NAMES = ['RiskGuard', '@ScalpX', 'TrendHunter'];
const LABELS = ['Market Analysis', 'Trading', 'AI Signals'];

/** -1 left, 0 centre, 1 right, so the three cards wrap around the selection. */
const slotOf = (bot: number, selection: number) => {
  const raw = (bot - selection + 3) % 3;
  return raw === 2 ? -1 : raw;
};

/**
 * Step 2: "Choose Your Trading" with strategy chips and a carousel of bot cards
 * (241.7 × 300 at x 79, y 328; neighbours 230 pt away at 72 %).
 */
export function SelectorScene({ selection, choose, reduced }: { selection: number; choose: (bot: number) => void; reduced: boolean }) {
  const carousel = useEntrance(reduced, { spring: [0.7, 0.92] });
  const carouselStyle = useAnimatedStyle(() => ({
    opacity: carousel.get(),
    transform: [{ translateX: reduced ? 0 : (1 - carousel.get()) * 58 }],
  }));
  const swipe = Gesture.Pan()
    .runOnJS(true)
    .activeOffsetX([-20, 20])
    .onEnd((event) => {
      if (Math.abs(event.translationX) <= 30) return;
      choose((selection + (event.translationX < 0 ? 1 : 2)) % 3);
    });

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Leading left={19.3} y={181.2}>
        <Wipe reduced={reduced}>
          <View style={{ height: 39, justifyContent: 'center' }}>
            <Text style={[headingText, { textAlign: 'left' }]}>Choose Your</Text>
          </View>
        </Wipe>
        <Wipe reduced={reduced} delay={0.16}>
          <View style={{ height: 39, flexDirection: 'row', alignItems: 'center', gap: 7.5 }}>
            <Text style={[headingText, { textAlign: 'left' }]}>Trading</Text>
            <PopIn reduced={reduced} delay={0.32} style={{ transform: [{ translateY: 3 }] }}>
              <Mark size={33} />
            </PopIn>
          </View>
        </Wipe>
      </Leading>

      <Centered x={19.3 + 177} y={256.7}>
        <Reveal reduced={reduced} delay={0.32} y={0}>
          <View style={{ flexDirection: 'row', gap: 8.3 }}>
            <Chip title="Trading" width={94.7} active={selection === 1} onPress={() => choose(1)}>
              <Candles width={16} height={15} />
            </Chip>
            <Chip title="Market Analysis" width={137.7} active={selection === 0} onPress={() => choose(0)}>
              <BellCurve width={15} height={14} />
            </Chip>
            <Chip title="AI Signals" width={104} active={selection === 2} onPress={() => choose(2)}>
              <Bars width={14} height={14} />
            </Chip>
          </View>
        </Reveal>
      </Centered>

      <GestureDetector gesture={swipe}>
        <Animated.View style={[{ position: 'absolute', left: 0, top: 478 - 160, width: 402, height: 320 }, carouselStyle]}>
          {[0, 1, 2].map((bot) => (
            <CarouselCard key={bot} bot={bot} selection={selection} reduced={reduced} onPress={() => choose(bot)} />
          ))}
        </Animated.View>
      </GestureDetector>

      <Centered x={200} y={650.7}>
        <Reveal reduced={reduced} delay={0.45} y={4}>
          <Text style={{ fontSize: 15.2, letterSpacing: -0.2, color: hex('#b4b4b4') }}>{NAMES[selection]}</Text>
        </Reveal>
      </Centered>
      <Centered x={200.2} y={690.3}>
        <Reveal reduced={reduced} delay={0.5} y={0}>
          <View style={{ flexDirection: 'row', gap: 4 }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            {[0, 1, 2].map((bot) => <PageDot key={bot} active={bot === selection} reduced={reduced} />)}
          </View>
        </Reveal>
      </Centered>
      <Centered x={200} y={739.3}>
        <Reveal reduced={reduced} delay={0.5} y={4}>
          <Text style={{ fontSize: 14.4, letterSpacing: -0.15, color: hex('#7a7a7a') }}>Pick your strategy. Let AI handle the analysis</Text>
        </Reveal>
      </Centered>
    </View>
  );
}

function Chip({ title, width, active, onPress, children }: { title: string; width: number; active: boolean; onPress: () => void; children: ReactNode }) {
  return (
    <PressScale onPress={onPress} accessibilityLabel={title} selected={active}>
      <View style={{ width, height: 34.7, borderRadius: 17.35, backgroundColor: hex(active ? '#151515' : '#101010'), flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9 }}>
        <View style={{ transform: [{ scale: 0.85 }] }}>{children}</View>
        <Text style={{ fontSize: 13, letterSpacing: -0.2, color: hex(active ? '#b0b0b0' : '#9a9a9a') }}>{title}</Text>
      </View>
    </PressScale>
  );
}

function PageDot({ active, reduced }: { active: boolean; reduced: boolean }) {
  const width = useSharedValue(active ? 14 : 8);
  useEffect(() => {
    width.set(reduced ? (active ? 14 : 8) : withSpring(active ? 14 : 8, spring(0.4, 0.85)));
  }, [active, reduced, width]);
  const style = useAnimatedStyle(() => ({ width: width.get() }));
  return <Animated.View style={[{ height: 5, borderRadius: 2.5, backgroundColor: hex(active ? '#dcdcdc' : '#343434') }, style]} />;
}

function CarouselCard({ bot, selection, reduced, onPress }: { bot: number; selection: number; reduced: boolean; onPress: () => void }) {
  const slot = slotOf(bot, selection);
  const active = slot === 0;
  const place = useSharedValue(slot);
  useEffect(() => {
    place.set(reduced ? slot : withSpring(slot, spring(0.5, 0.86)));
  }, [place, reduced, slot]);
  const style = useAnimatedStyle(() => {
    const away = Math.min(1, Math.abs(place.get()));
    return {
      transform: [{ translateX: place.get() * 230 }, { translateY: away * 3.7 }, { scale: 1 - 0.28 * away }],
    };
  });
  return (
    <Animated.View style={[{ position: 'absolute', left: 201 - 241.7 / 2, top: 160 - 150, zIndex: active ? 2 : 1 }, style]}>
      <PressScale onPress={onPress} accessibilityLabel={`Select ${NAMES[bot]}`} selected={active}>
        <SelectorCard bot={bot} active={active} reduced={reduced} />
      </PressScale>
    </Animated.View>
  );
}

/** One bot in the carousel: an outer 241.7 × 300 shell, a dotted screen with the character, and a label pill. */
function SelectorCard({ bot, active, reduced }: { bot: number; active: boolean; reduced: boolean }) {
  return (
    <Panel width={241.7} height={300} radius={40} fill={[hex('#1a1a1a'), hex('#141414')]} stroke={[white(0.07), white(0.02)]}
      shadow={{ color: '#000', opacity: 0.6, radius: 18, y: 10 }}>
      <View style={{ position: 'absolute', left: (241.7 - 223.3) / 2, top: 9, width: 223.3, height: 213, borderRadius: 31, borderCurve: 'continuous', backgroundColor: hex('#0e0e0e'), overflow: 'hidden', alignItems: 'center', justifyContent: 'center' }}>
        <DotField spacing={5} opacity={0.045} style={StyleSheet.absoluteFill} />
        <View style={[StyleSheet.absoluteFill, { borderRadius: 31, borderWidth: 1, borderColor: white(0.03) }]} />
        <View style={{ opacity: active ? 1 : 0.3, transform: bot === 1 ? [{ translateX: 1.5 }, { translateY: -2 }] : [] }}>
          {bot === 1 ? <LivingOrb reduced={reduced} /> : <Character kind={bot === 0 ? 'green' : 'red'} size={bot === 0 ? 128 : 112} saturation={active ? 1 : 0.25} />}
        </View>
      </View>
      <View style={{ position: 'absolute', top: 238.7, left: 0, right: 0, alignItems: 'center' }}>
        <Reveal reduced={reduced} delay={0.5} y={4}>
          <View style={{ height: 37.3, flexDirection: 'row', alignItems: 'center', gap: 10, paddingLeft: 2, paddingRight: 11, borderRadius: 13, borderCurve: 'continuous', backgroundColor: hex('#0b0b0b'), borderWidth: 1, borderColor: white(0.06) }}>
            <View style={{ width: 32.7, height: 31.3, borderRadius: 10, borderCurve: 'continuous', backgroundColor: hex('#1a1a1a'), alignItems: 'center', justifyContent: 'center' }}>
              <Candles width={18} height={17} />
            </View>
            <Text style={{ fontSize: 12.5, letterSpacing: -0.15, color: hex('#8a8a8a'), opacity: active ? 1 : 0 }} importantForAccessibility={active ? 'auto' : 'no'}>{LABELS[bot]}</Text>
          </View>
        </Reveal>
      </View>
    </Panel>
  );
}
