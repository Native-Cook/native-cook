import { Image } from 'expo-image';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { FAVOURITES } from './data';
import type { Box } from './geometry';
import { ArrowGlyph } from './glyphs';
import { COLOR, FAMILY, FONT, PILL, TAB } from './theme';

/**
 * What the black pill shows. It is laid out at the pill's resting size and seen through a window that
 * follows the pill, so when the pill shrinks back to the island the words are cut by its edge, as in the pin.
 */
export function IslandPill({ width, oy, pill, bloom, arm, sheet, fade, reduced }: {
  width: number; oy: number; pill: SharedValue<Box>; bloom: SharedValue<Box>; arm: SharedValue<number>; sheet: SharedValue<number>;
  fade: SharedValue<number>; reduced: boolean;
}) {
  const restLeft = (width - PILL.width) / 2;
  const clip = useAnimatedStyle(() => {
    const b = pill.get();
    return { left: b.x, top: b.y, width: b.w, height: b.h, borderRadius: b.r - 0.5 };
  });
  const inner = useAnimatedStyle(() => {
    const b = pill.get();
    const shown = reduced ? 1 - fade.get() : 1 - Math.min(1, Math.max(0, (sheet.get() - 0.25) / 0.25));
    return { left: restLeft - b.x, top: oy + PILL.top - b.y, opacity: shown };
  });
  // The arrow rides down inside the swelling drop (pin 9.20 s: 104 pt) and goes as it becomes the sheet.
  const arrow = useAnimatedStyle(() => {
    const open = reduced ? fade.get() : Math.min(1, Math.max(0, (sheet.get() - 0.35) / 0.2));
    const b = bloom.get();
    const drop = reduced ? 0 : Math.max(0, b.y + b.h - (oy + TAB.bottom)) * TAB.arrowFollow;
    return { opacity: arm.get() * (1 - open), transform: [{ translateY: drop }] };
  });
  return (
    <>
      <Animated.View style={[styles.clip, clip]} pointerEvents="none">
        <Animated.View style={[styles.inner, inner]}>
          <Text style={styles.label}>
            <Text style={styles.soft}>Send </Text>
            <Text style={styles.strong}>to</Text>
          </Text>
          <View style={styles.stack}>
            {FAVOURITES.map((f, i) => (
              <Image key={f.name} source={f.photo} contentFit="cover"
                style={[styles.face, { left: i * PILL.avatarStep, zIndex: i }]} />
            ))}
          </View>
        </Animated.View>
      </Animated.View>
      <Animated.View pointerEvents="none"
        style={[styles.arrow, { left: width / 2 - 12, top: oy + TAB.arrowY - 12 }, arrow]}>
        <ArrowGlyph direction="upLeft" color={COLOR.white} size={15} stroke={1.5} />
      </Animated.View>
    </>
  );
}

const STACK = PILL.avatar + PILL.avatarStep * 2;

const styles = StyleSheet.create({
  clip: { position: 'absolute', overflow: 'hidden' },
  inner: {
    position: 'absolute', width: PILL.width, height: PILL.height, flexDirection: 'row', alignItems: 'center',
    justifyContent: 'space-between', paddingLeft: PILL.padX, paddingRight: PILL.padRight,
  },
  label: { fontFamily: FAMILY.medium, fontSize: FONT.pill, lineHeight: 24 },
  soft: { color: COLOR.pillMuted, fontFamily: FAMILY.regular },
  strong: { color: COLOR.white, fontFamily: FAMILY.medium },
  stack: { width: STACK, height: PILL.avatar },
  face: {
    position: 'absolute', top: 0, width: PILL.avatar, height: PILL.avatar, borderRadius: PILL.avatar / 2,
    borderWidth: 1.5, borderColor: COLOR.black,
  },
  arrow: { position: 'absolute', width: 24, height: 24, alignItems: 'center', justifyContent: 'center' },
});
