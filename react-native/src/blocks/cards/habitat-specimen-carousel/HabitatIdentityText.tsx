import { memo } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { MorphingText } from '../../gestures/choose-your-ball/MorphingText';

export type HabitatIdentitySpec = { name: string; species: string };
export type HabitatIdentityTextProps = {
  reducedMotion: boolean;
  scale: number;
  /** System font scale; visual windows support1× through2× without double-scaling text. */
  fontScale?: number;
  top: number;
  specs: readonly HabitatIdentitySpec[];
  /** Parent carousel's existing semantic selection; never updated here per animation tick. */
  selectedIndex: number;
  /** Width of the screen the lines are centred in; defaults to the window. */
  width?: number;
};

/** Parent layout reserves this height only when device size or system text size changes. */
export function habitatIdentityTextHeight(scale: number, fontScale = 1) {
  return 60 * scale * Math.min(2, Math.max(1, fontScale));
}

/**
 * Choose Your Ball's wordmark morph, reused: when the selection changes, letters the two names
 * share glide to their new place while the rest blur out and back in, so the headline is rewritten
 * rather than replaced (CHARIZARD → BLASTOISE keeps its A, R and I).
 * Species15 + name45 at default text size; windows expand up to 2× without animating layout.
 */
export const HabitatIdentityText = memo(function HabitatIdentityText({ reducedMotion, scale, fontScale = 1, top, specs, selectedIndex, width: viewportWidth }: HabitatIdentityTextProps) {
  const window = useWindowDimensions();
  const width = viewportWidth ?? window.width;
  const count = specs.length;
  const active = count > 0 ? specs[((selectedIndex % count) + count) % count] : undefined;
  const effectiveFontScale = Math.min(2, Math.max(1, fontScale));
  const speciesHeight = 15 * scale * effectiveFontScale;
  const nameHeight = 45 * scale * effectiveFontScale;
  return <View pointerEvents="none" style={[styles.identity, { top, height: habitatIdentityTextHeight(scale, fontScale) }]}
    accessible={Boolean(active)} accessibilityRole="header" accessibilityLabel={active ? `${active.name}, ${active.species}` : undefined}
    testID="habitat-identity-text">
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <MorphingText align="center" color="#B3C1AD" fitTexts={specs.map(spec => spec.species)} fontSize={10 * scale * effectiveFontScale}
        height={speciesHeight} reducedMotion={reducedMotion} text={active?.species ?? ''} tracking={1 * scale} weight="400" width={width} />
      <MorphingText align="center" color="#F5F4E9" fitTexts={specs.map(spec => spec.name)} fontSize={37 * scale * effectiveFontScale}
        height={nameHeight} reducedMotion={reducedMotion} text={active?.name ?? ''} tracking={-1.3 * scale} weight="600" width={width} />
    </View>
  </View>;
});

const styles = StyleSheet.create({
  identity: { position: 'absolute', left: 0, right: 0, zIndex: 10 },
});
