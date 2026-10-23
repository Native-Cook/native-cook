/** The source's white bookmark: it gulps when a card lands, swells under a dragged card, and rises to the centre when a pile is saved. */
import { BlurView } from 'expo-blur';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import { EASE_OUT, REPLICA_ONLY, TIMING } from './motion';
import { COLOR, GLASS, LAYOUT, SPACE, TYPE } from './theme';

const { width: W, height: H } = LAYOUT.bookmark;
// Rounded top, straight sides, a deep notch: the source's glyph (radius ≈ 16% of the width, notch ≈ 26% of the height).
const R = W * 0.16;
const NOTCH = H * 0.26;
const GLYPH = `M${R} 0H${W - R}A${R} ${R} 0 0 1 ${W} ${R}V${H - 3}A3 3 0 0 1 ${W - 4.6} ${H - 0.6}L${W / 2} ${H - NOTCH}L4.6 ${H - 0.6}A3 3 0 0 1 0 ${H - 3}V${R}A${R} ${R} 0 0 1 ${R} 0Z`;

type Props = { press: SharedValue<number>; pressCard: SharedValue<number>; bounce: SharedValue<number>; open: SharedValue<number>; rise: SharedValue<number>; ring: SharedValue<number>; centre: { x: number; y: number }; screenMidY: number; saved: number; pileCount: number; reduceMotion: boolean };

export function Bookmark({ press, pressCard, bounce, open, rise, ring, centre, screenMidY, saved, pileCount, reduceMotion }: Props) {
  const glyph = useAnimatedStyle(() => ({
    transform: [
      { translateY: (screenMidY - centre.y) * rise.get() },
      { scale: (reduceMotion ? 1 : 1 + 0.18 * open.get() + 0.22 * bounce.get()) * (1 + 0.14 * rise.get()) * (pressCard.get() === -2 ? 1 - 0.06 * press.get() : 1) }, // the source's glyph grows 72 → 82 pt as it climbs
    ],
  }));
  // A light ring leaves the glyph when a card lands.
  const ringStyle = useAnimatedStyle(() => {
    const r = ring.get();
    return { opacity: r >= 1 ? 0 : 0.55 * (1 - r), transform: [{ translateY: (screenMidY - centre.y) * rise.get() }, { scale: 0.6 + 1.8 * r }] };
  });
  // Payoff: under the risen glyph, how many the pile saved.
  const countStyle = useAnimatedStyle(() => ({ opacity: Math.max(rise.get() * 2 - 1, 0), transform: [{ translateY: (screenMidY - centre.y) * rise.get() + (1 - rise.get()) * 10 }] }));
  const halo = useAnimatedStyle(() => ({ opacity: Math.min(open.get() + bounce.get(), 1), transform: [{ scale: 0.6 + 0.6 * open.get() }] }));
  return (
    <View style={[styles.box, { left: centre.x - W / 2, top: centre.y - H / 2 }]} pointerEvents="none" accessible accessibilityRole="button" accessibilityLabel={`Saved, ${saved} items. Double tap to save every card`}>
      <Animated.View style={[styles.halo, halo]} />
      <Animated.View style={[styles.ring, ringStyle]} />
      {!REPLICA_ONLY && <Animated.View style={[styles.count, countStyle]}>
        {/* A glass pill: static blur, a bright 1 px top edge. */}
        <BlurView intensity={GLASS.intensity} tint="dark" style={styles.pill}>
          <View style={styles.pillEdge} />
          <Animated.Text key={pileCount} entering={FadeInDown.duration(TIMING.countIn).easing(EASE_OUT)} style={styles.countText}>{`${pileCount} saved`}</Animated.Text>
        </BlurView>
      </Animated.View>}
      <Animated.View style={glyph}>
        <Svg width={W} height={H}><Path d={GLYPH} fill={COLOR.ink} /></Svg>
      </Animated.View>
    </View>
  );
}

const HALO = 140;
const RING = 70;

const styles = StyleSheet.create({
  box: { position: 'absolute', width: W, height: H, alignItems: 'center', justifyContent: 'center' },
  ring: { position: 'absolute', width: RING, height: RING, borderRadius: RING / 2, borderWidth: 1.5, borderColor: COLOR.ink },
  count: { position: 'absolute', top: H + SPACE.l, left: (W - 160) / 2, width: 160, alignItems: 'center' },
  pill: { height: SPACE.xxl, borderRadius: SPACE.l, paddingHorizontal: SPACE.l, justifyContent: 'center', overflow: 'hidden', backgroundColor: 'rgba(255,255,255,0.06)' },
  pillEdge: { position: 'absolute', left: SPACE.m, right: SPACE.m, top: 0, height: StyleSheet.hairlineWidth * 2, backgroundColor: COLOR.glassEdge },
  countText: { ...TYPE.count, color: COLOR.ink },
  halo: { position: 'absolute', width: HALO, height: HALO, borderRadius: HALO / 2, backgroundColor: COLOR.glow },
});
