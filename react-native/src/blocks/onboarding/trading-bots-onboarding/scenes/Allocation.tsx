import { BlurMask, Canvas, LinearGradient, Path, RadialGradient, Rect, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';

import { RISKS, type Risk } from '../flow';
import { HexNut, Mark } from '../glyphs';
import { Caption, Centered, Frame, Heading, Leading, Panel, PressScale, Reveal, SFSymbol, useEntrance, useSettle } from '../primitives';
import { hex, white } from '../theme';

type Props = {
  allocation: number;
  setAllocation: (value: number) => void;
  risk: Risk;
  setRisk: (risk: Risk) => void;
  autoTrading: boolean;
  setAutoTrading: (on: boolean) => void;
  maxLoss: boolean;
  setMaxLoss: (on: boolean) => void;
  reduced: boolean;
};

const FACE_W = 306;
const FACE_H = 136;
const METER_W = 286;

/**
 * Step 4: the orange TrendHunter card, tilted −5°, over a dark Risk Per Trade card tilted
 * +4.5°, above "Your Bots. / Your Control.".
 */
export function AllocationScene(props: Props) {
  const { reduced } = props;
  const riskIn = useEntrance(reduced, { spring: [0.6, 0.9] });
  const riskStyle = useAnimatedStyle(() => ({
    opacity: riskIn.get(),
    transform: [{ translateY: reduced ? 0 : (1 - riskIn.get()) * 26 }, { rotate: '4.5deg' }],
  }));
  const cardIn = useEntrance(reduced, { spring: [0.72, 0.82], delay: 0.22 });
  const cardStyle = useAnimatedStyle(() => {
    const p = reduced ? 1 : cardIn.get();
    return {
      opacity: Math.min(1, cardIn.get()),
      transform: [
        { perspective: 900 },
        { translateY: (1 - p) * -46 },
        { scale: 1.06 - 0.06 * p },
        { rotateX: `${(1 - p) * 24}deg` },
        { rotate: `${-11 + 6 * p}deg` },
      ],
    };
  });
  const reveal = useSettle(0.49, 1, 0.7, 0.12, 0.6, 2, reduced, 0.35);
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Frame x={200} y={466} width={304} height={130}>
        <Animated.View style={riskStyle}>
          <RiskCard risk={props.risk} setRisk={props.setRisk} />
        </Animated.View>
      </Frame>
      <Frame x={198} y={301} width={318} height={184}>
        <Animated.View style={cardStyle}>
          <AllocationCard {...props} shown={props.allocation * reveal} />
        </Animated.View>
      </Frame>
      <Heading first="Your Bots." second="Your Control." reduced={reduced} />
      <Centered x={200} y={681.7}>
        <Reveal reduced={reduced} delay={0.38} y={6}>
          <Caption text={'Monitor, configure, and manage your\ntrading agents.'} />
        </Reveal>
      </Centered>
    </View>
  );
}

/** Orange card: 318 × 184 frame, 306 × 136 face, two check pills under it. */
function AllocationCard({ allocation, setAllocation, autoTrading, setAutoTrading, maxLoss, setMaxLoss, shown }: Props & { shown: number }) {
  const drag = Gesture.Pan()
    .runOnJS(true)
    .minDistance(0)
    .onBegin((event) => setAllocation((event.x / METER_W) * 100))
    .onUpdate((event) => setAllocation((event.x / METER_W) * 100));
  return (
    <Panel width={318} height={184} radius={31} fill={[hex('#2e2e2e', 0.92), hex('#161616', 0.96)]} stroke={[white(0.22), white(0.06), white(0.14)]} strokeWidth={1.2} diagonalStroke
      shadow={{ color: '#000', opacity: 0.85, radius: 22, y: 18 }}>
      <View style={{ position: 'absolute', left: (318 - FACE_W) / 2, top: 5, width: FACE_W, height: FACE_H, borderRadius: 26, borderCurve: 'continuous', overflow: 'hidden' }}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Rect x={0} y={0} width={FACE_W} height={FACE_H}>
            <LinearGradient start={vec(0, 0)} end={vec(0, FACE_H)} colors={[hex('#ff5c00'), hex('#ff7a06'), hex('#ff9a24'), hex('#ffc568')]} positions={[0, 0.45, 0.75, 1]} />
          </Rect>
          <Rect x={0} y={0} width={FACE_W} height={FACE_H}>
            <RadialGradient c={vec(FACE_W, 0)} r={190} colors={[hex('#ff4a00', 0.55), hex('#ff4a00', 0)]} />
          </Rect>
          <Rect x={0} y={0} width={FACE_W} height={FACE_H}>
            <RadialGradient c={vec(FACE_W * 0.3, FACE_H * 1.1)} r={150} colors={[hex('#ffe3a8', 0.5), hex('#ffe3a8', 0)]} />
          </Rect>
          <RoundedRect x={0.5} y={0.5} width={FACE_W - 1} height={FACE_H - 1} r={25.5} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, 0)} end={vec(0, FACE_H)} colors={[white(0.35), white(0.05), white(0.25)]} />
          </RoundedRect>
        </Canvas>
        <Frame x={37} y={38} width={58} height={56} style={{ borderRadius: 14, borderCurve: 'continuous', backgroundColor: hex('#0d0d0d'), alignItems: 'center', justifyContent: 'center' }}>
          <Mark style="white" size={38} rotation={-8} />
        </Frame>
        <Leading left={82} y={36}>
          <View style={{ gap: 9 }}>
            <Text style={{ fontSize: 15.5, fontWeight: '500', color: '#fff' }}>TrendHunter</Text>
            <Text style={{ fontSize: 10.7, color: white(0.55) }}>BTC/USDT · Trend Analysis</Text>
          </View>
        </Leading>
        <Frame x={275} y={26} width={33} height={31} style={{ borderRadius: 10, borderCurve: 'continuous', backgroundColor: hex('#0d0d0d'), alignItems: 'center', justifyContent: 'center' }}>
          <HexNut width={15} height={16} />
        </Frame>
        <Leading left={15} y={86}>
          <Text style={{ fontSize: 15, fontWeight: '500', color: '#fff' }}>Capital Allocation</Text>
        </Leading>
        <Frame x={276} y={84} width={33} height={18} style={{ borderRadius: 7, borderCurve: 'continuous', backgroundColor: white(0.2), alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ fontSize: 12.5, fontWeight: '600', color: '#fff', fontVariant: ['tabular-nums'] }}>{`${Math.round(shown)}%`}</Text>
        </Frame>
        <Frame x={16 + 143} y={110} width={METER_W} height={25}>
          <GestureDetector gesture={drag}>
            <View
              collapsable={false}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel="Capital allocation"
              accessibilityValue={{ text: `${Math.round(allocation)} percent` }}
              accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
              onAccessibilityAction={(event) => setAllocation(allocation + (event.nativeEvent.actionName === 'increment' ? 5 : -5))}
              style={{ width: METER_W, height: 25 }}>
              <Meter value={shown} />
            </View>
          </GestureDetector>
        </Frame>
      </View>
      <View style={{ position: 'absolute', top: 150, left: 0, right: 0, alignItems: 'center' }}>
        <View style={{ flexDirection: 'row', gap: 8, transform: [{ translateX: -4 }] }}>
          <CheckPill title="Auto-Trading" width={98} selected={autoTrading} toggle={() => setAutoTrading(!autoTrading)} />
          <CheckPill title="Max Daily Loss" width={104} selected={maxLoss} toggle={() => setMaxLoss(!maxLoss)} />
        </View>
      </View>
    </Panel>
  );
}

/** White bars up to the allocation, darker amber bars after it, over a soft white glow. */
function Meter({ value }: { value: number }) {
  const bars = useMemo(() => {
    const count = 83;
    const pitch = METER_W / count;
    const filled = Math.round((value / 100) * count);
    const on = Skia.Path.Make();
    const off = Skia.Path.Make();
    for (let index = 0; index < count; index++) {
      const width = pitch * 0.68;
      const rect = Skia.RRectXY(Skia.XYWHRect(index * pitch, 0, width, 25), width / 2, width / 2);
      (index < filled ? on : off).addRRect(rect);
    }
    return { on, off };
  }, [value]);
  const glow = (METER_W * value) / 100;
  return (
    <Canvas style={{ width: METER_W, height: 25 }}>
      <RoundedRect x={0} y={1.5} width={glow} height={22} r={11} color={white(0.35)}>
        <BlurMask blur={4} style="normal" />
      </RoundedRect>
      <Path path={bars.off} color={hex('#8a3a00', 0.55)} />
      <Path path={bars.on} color="#fff" />
    </Canvas>
  );
}

function CheckPill({ title, width, selected, toggle }: { title: string; width: number; selected: boolean; toggle: () => void }) {
  return (
    <PressScale onPress={toggle} accessibilityLabel={title} selected={selected}>
      <View style={{ width, height: 27, borderRadius: 13.5, backgroundColor: hex('#1a1a1a'), borderWidth: 1, borderColor: white(0.07), flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 }}>
        <SFSymbol name="checkmark" size={9} weight="bold" color={hex('#b4b4b4')} style={{ opacity: selected ? 1 : 0.15 }} />
        <Text style={{ fontSize: 12, letterSpacing: -0.15, color: hex('#e6e6e6') }}>{title}</Text>
      </View>
    </PressScale>
  );
}

/** Risk card (304 × 130). */
function RiskCard({ risk, setRisk }: { risk: Risk; setRisk: (risk: Risk) => void }) {
  return (
    <Panel width={304} height={130} radius={28} fill={[hex('#040404'), hex('#040404')]} stroke={[hex('#3a3a3a'), hex('#262626')]} strokeWidth={3}
      shadow={{ color: '#000', opacity: 0.6, radius: 14, y: 10 }}>
      <Leading left={14} y={21}>
        <Text style={{ fontSize: 13.5, color: hex('#6c6c6c') }}>Risk Per Trade</Text>
      </Leading>
      <Leading left={14} y={41}>
        <Text style={{ fontSize: 10.4, color: hex('#2c2c2c') }}>Maximum Planned Risk Per Position</Text>
      </Leading>
      <Centered x={152} y={84} box={320}>
        <View style={{ flexDirection: 'row', gap: 9 }}>
          {RISKS.map((choice) => (
            <PressScale key={choice} onPress={() => setRisk(choice)} accessibilityLabel={`Risk per trade ${choice}`} selected={risk === choice}>
              <View style={{ width: 87, height: 38, borderRadius: 12, borderCurve: 'continuous', backgroundColor: hex(risk === choice ? '#121212' : '#050505'), borderWidth: 1, borderColor: white(risk === choice ? 0.12 : 0.06), alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ fontSize: 10.5, fontWeight: '500', color: '#fff' }}>{choice}</Text>
              </View>
            </PressScale>
          ))}
        </View>
      </Centered>
    </Panel>
  );
}
