import { Platform, StyleSheet, Text, View } from 'react-native';
import { type GestureType, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { CARD as DATA } from './data';
import { FlutedCard } from './FlutedCard';
import { NetworkMark } from './glyphs';
import { LIFT, LIGHT, TILT } from './motion';
import { BALANCE, CARD, COLOR, FAMILY, FONT, SCREEN } from './theme';

/** The pin's card; a finger tilts it and the reeded glass refracts with the tilt. */
export function Card({ width, oy, gesture, rx, ry, lift, glow, reduced }: {
  width: number; oy: number; gesture: GestureType; rx: SharedValue<number>; ry: SharedValue<number>;
  lift: SharedValue<number>; glow: SharedValue<number>; reduced: boolean;
}) {
  const w = width - SCREEN.inset * 2;
  const shift = useDerivedValue(() => (reduced ? 0 : (ry.get() / TILT.max) * TILT.refraction));
  // The specular streak: brighter the higher the card is held, and it runs against the tilt.
  const spec = useDerivedValue(() => (reduced ? glow.get() : Math.min(1, Math.max(0, lift.get())) * LIGHT.spec + glow.get()));
  const light = useDerivedValue(() => (reduced ? 0.5 : 0.5 - (ry.get() / TILT.max) * LIGHT.specTravel));
  const tilt = useAnimatedStyle(() => ({
    transform: [{ perspective: 900 }, { rotateX: `${reduced ? 0 : rx.get()}deg` }, { rotateY: `${reduced ? 0 : ry.get()}deg` },
      { scale: reduced ? 1 : 1 + LIFT.scale * lift.get() }],
  }));
  return (
    <GestureDetector gesture={gesture}>
      <Animated.View style={[styles.card, { top: oy + CARD.top, width: w }, tilt]}
        accessible accessibilityRole="summary" accessibilityLabel={`Card ending ${DATA.last4}, balance ${DATA.balance}`}>
        <FlutedCard width={w} height={CARD.height} shift={shift} spec={spec} light={light} />
        <Text style={[styles.label, { left: CARD.padX }]}>card balance</Text>
        <Text style={[styles.label, { right: CARD.padX }]}>*{DATA.last4}</Text>
        <View style={styles.badge}><NetworkMark size={26} /></View>
        <Balance />
      </Animated.View>
    </GestureDetector>
  );
}

const FACE = Platform.OS === 'ios' ? BALANCE.ios : BALANCE.android;
const ASCENT = Platform.OS === 'ios' ? 63.4 : 61.6; // baseline below the text box's top at FACE.size

/** The balance, one glyph per box so each lands where the pin has it (see BALANCE in theme.ts). */
function Balance() {
  const glyphs = DATA.balance === BALANCE.text ? [...BALANCE.text] : null;
  if (!glyphs) return <Text style={[styles.glyph, { left: CARD.padX }]}>{DATA.balance}</Text>;
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {glyphs.map((ch, i) => (
        <Text key={i} style={[styles.glyph, { left: BALANCE.inkLeft[i] - FACE.bearing[i] }]}>{ch}</Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    position: 'absolute', left: SCREEN.inset, height: CARD.height, borderRadius: CARD.radius, overflow: 'hidden',
    borderCurve: 'continuous',
  },
  label: {
    position: 'absolute', top: CARD.labelY - 12, fontFamily: FAMILY.regular, fontSize: FONT.label, lineHeight: 24,
    color: COLOR.white, letterSpacing: 0.1,
  },
  badge: {
    position: 'absolute', right: CARD.badge.right, top: CARD.badge.top, width: CARD.badge.w, height: CARD.badge.h,
    borderRadius: CARD.badge.r, backgroundColor: COLOR.badge, alignItems: 'center', justifyContent: 'center',
  },
  glyph: {
    position: 'absolute', top: BALANCE.baseline - ASCENT, fontSize: FACE.size, color: COLOR.white, includeFontPadding: false,
    ...(Platform.OS === 'ios' ? { fontWeight: BALANCE.ios.weight } : { fontFamily: FAMILY.display }),
  },
});
