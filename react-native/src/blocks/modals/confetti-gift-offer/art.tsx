import { Image } from 'expo-image';
import { StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import Svg, { Defs, Ellipse, G, Path, Pattern, Rect } from 'react-native-svg';

// Miso, an original raccoon (Codex, low effort): heart eyes over the ticket, worried in the
// skip sheet, heart hands on the account screen, and its tail poking out of the gift.
const POSES = { hearts: require('./assets/raccoon-hearts.webp'), worried: require('./assets/raccoon-worried.webp'), love: require('./assets/raccoon-love.webp') } as const;
export function Mascot({ pose, size = 144 }: { pose: keyof typeof POSES; size?: number }) {
  return <Image source={POSES[pose]} contentFit="contain" transition={0} accessible={false} style={{ width: size, height: size }} />;
}

// The reference's gift: an upright checked box with a heavy blue outline, its lid knocked
// askew by a striped tail poking out, a two-loop bow on top. Drawn on a 270 × 310 pt board.
const BLUE = '#3A7FE9';
function Checks({ id }: { id: string }) {
  return <Pattern id={id} width={40} height={40} patternUnits="userSpaceOnUse"><Rect width={40} height={40} fill="#A6E7DD" /><Rect width={20} height={20} fill="#F3FBFA" /><Rect x={20} y={20} width={20} height={20} fill="#F3FBFA" /></Pattern>;
}
export function Gift({ open }: { open: SharedValue<number> }) {
  // Two rocks build anticipation, the box squats, then the lid kicks free just before the cut.
  const shake = useAnimatedStyle(() => { const p = open.get(); const wave = p >= 1 ? 0 : Math.sin(Math.min(p / .72, 1) * Math.PI * 4) * (.6 + p * .6); return { transform: [{ translateX: wave * 3.4 }, { rotate: `${wave * 4}deg` }] }; });
  const squat = useAnimatedStyle(() => { const p = open.get(); const k = p < .6 ? 0 : p < .82 ? (p - .6) / .22 : 1 - (p - .82) / .18; return { transform: [{ translateY: 6 * k }, { scaleY: 1 - .06 * k }, { scaleX: 1 + .04 * k }] }; });
  const pop = useAnimatedStyle(() => { const p = Math.max(0, (open.get() - .78) / .22); const e = 1 - Math.pow(1 - Math.min(1, p), 3); return { transform: [{ translateY: -44 * e }, { rotate: `${-12 * e}deg` }] }; });
  return <Animated.View style={[styles.gift, shake]} accessible={false}>
    <Animated.View style={[StyleSheet.absoluteFill, styles.base, squat]}><Image source={require('./assets/raccoon-tail.webp')} contentFit="contain" transition={0} accessible={false} style={styles.tail} /><Svg style={StyleSheet.absoluteFill} width={270} height={310} viewBox="0 0 270 310">
      <Defs><Checks id="giftBoxChecks" /></Defs>
      <Rect x={8} y={300} width={240} height={5} rx={2.5} fill="#CDD0E6" />
      <Rect x={5} y={160} width={243} height={140} rx={14} fill="url(#giftBoxChecks)" stroke={BLUE} strokeWidth={5} />
      <Rect x={108} y={162.5} width={34} height={135} fill={BLUE} />
    </Svg></Animated.View>
    <Animated.View style={[StyleSheet.absoluteFill, styles.lid, pop]}><Svg width={270} height={310} viewBox="0 0 270 310">
      <Defs><Checks id="giftLidChecks" /></Defs>
      {/* The bow: two fat lobes meeting at a knot, in the reference's flat blue. */}
      <Path d="M114 96C74 104 30 76 38 38 46 2 96 6 108 46c4 14 6 32 6 50Z" fill={BLUE} />
      <Path d="M114 96c2-30 12-62 40-74 30-12 56 16 40 42-14 22-44 32-80 32Z" fill={BLUE} />
      <Path d="M62 34c18 10 34 32 44 54M176 36c-18 6-38 26-54 52" stroke="#2F6AD0" strokeWidth={5} strokeLinecap="round" fill="none" />
      <G transform="rotate(-13 4 162)">
        <Rect x={2} y={107} width={146} height={55} rx={10} fill="url(#giftLidChecks)" stroke={BLUE} strokeWidth={5} />
        <Rect x={86} y={109.5} width={30} height={50} fill={BLUE} />
      </G>
      <Ellipse cx={114} cy={94} rx={15} ry={12} fill={BLUE} />
    </Svg></Animated.View>
  </Animated.View>;
}
const styles = StyleSheet.create({ gift: { width: 270, height: 310 }, base: { transformOrigin: '50% 100%' }, lid: { transformOrigin: '1% 52%' }, tail: { position: 'absolute', left: 100, top: 4, width: 190, height: 190 } });
