/**
 * Distill: each highlighted sentence leaves the page as a chip (an overlay drawn exactly like the sentence where it
 * was) and travels on a curved path into the summary line it became, turning from ink to white on the way. A chip
 * and its line only ever swap in place: the line stays hidden until the chip is on it, and every other line waits
 * until the chips have passed (summary-layout.ts). On the way the words sit on a soft backing (the page's white,
 * then a pill of the field's indigo), so they read over whatever they cross. The same chips fly home when the article
 * comes back, as ink on their paper backing over the reopening page.
 */
import { StyleSheet, View, type ViewStyle } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { KeyLineText } from './ArticlePage';
import { mix, span, type KeyGeom } from './geometry';
import { FLIGHT, MARK } from './motion';
import { chipU, homeSwap, lineSwap, swapChipAlpha, type ChipTarget, type SummaryLayout } from './summary-layout';
import { COLOR, LAYOUT, TYPE } from './theme';
import type { SummaryValues } from './use-summary-motion';

type ChipProps = { k: KeyGeom; index: number; target: ChipTarget; owns: boolean; top: number; values: SummaryValues };

const HIDDEN = { shown: 0, x: 0, y: 0, k: 1, u: 0, white: 0, paper: 1, words: 1 };

function Chip({ k, index, target, owns, top, values }: ChipProps) {
  const kw = Math.max(1, k.width);
  const kh = Math.max(1, k.height);
  // Only the four values the flight reads (capturing `values` whole would re-run this on every value of the block).
  const { flightD, flightDir, chipFrom, chipFade } = values;
  const pose = useDerivedValue(() => {
    const d = flightD.get();
    const dir = flightDir.get();
    const u = chipU(d, index, dir);
    const from = chipFrom.get()[index];
    // Before it lifts off (and once it is home again) the page draws the sentence.
    const home = homeSwap(d, index, dir);
    if (from === undefined || home >= 1) return HIDDEN;
    // chipFade: a new gesture caught it mid-flight, so it fades where it stopped.
    const seen = (from.vis > 0 ? 1 : span(u, 0, FLIGHT.hiddenIn)) * (1 - home) * chipFade.get();
    if (target === null || (!owns && dir < 0)) {
      // Left out at this depth: it dissolves where it is, still ink on the page. A sentence that melted into another
      // chip's line comes back the same way, in place at home, so the way back never crosses another chip.
      return { shown: seen * (1 - span(u, 0, FLIGHT.dissolve)), x: from.x, y: from.y, k: from.k * (1 - FLIGHT.dissolveScale * u), u, white: 0, paper: 1, words: 1 };
    }
    // The chip that becomes its line swaps with it in place; one that melts into another chip's line fades on the way.
    // Back, the swap lays the paper over the line first and writes the words on it after.
    const swap = lineSwap(d, index, dir);
    const back = owns && dir < 0;
    const stay = !owns ? 1 - span(u, FLIGHT.merge[0], FLIGHT.merge[1]) : back ? 1 : swapChipAlpha(swap);
    const paper = back ? span(1 - swap, FLIGHT.backPaper[0], FLIGHT.backPaper[1]) : 1;
    const words = back ? span(1 - swap, FLIGHT.backWords[0], FLIGHT.backWords[1]) : 1;
    // Lands centred on its line, as wide as it.
    const k1 = target.width / kw;
    const tx = target.x + (target.width - kw * k1) / 2;
    const ty = top + target.y + (target.height - kh * k1) / 2;
    // A quadratic path that bows sideways, more for a longer drop, so the chips stream rather than slide.
    const cx = (from.x + tx) / 2 + FLIGHT.bow + FLIGHT.bowPerDy * Math.abs(ty - from.y);
    const cy = (from.y + ty) / 2;
    const r = 1 - u;
    return {
      shown: seen * stay,
      x: r * r * from.x + 2 * r * u * cx + u * u * tx,
      y: r * r * from.y + 2 * r * u * cy + u * u * ty,
      k: mix(from.k, k1, u),
      u,
      // Out over the field it turns white on the indigo pill. On the way back the page is open under it within a
      // few frames, so it swaps in place straight to ink on its paper backing and stays so all the way home.
      white: dir > 0 ? span(u, FLIGHT.inkToWhite[0], FLIGHT.inkToWhite[1]) : 0,
      paper,
      words,
    };
  });
  const box = useAnimatedStyle(() => {
    const m = pose.get();
    return { opacity: m.shown, transform: [{ translateX: m.x }, { translateY: m.y }, { scale: m.k }] };
  });
  const paper = useAnimatedStyle(() => ({ opacity: (1 - pose.get().white) * pose.get().paper }));
  const pill = useAnimatedStyle(() => ({ opacity: pose.get().white }));
  const bands = useAnimatedStyle(() => {
    const m = pose.get();
    return { opacity: MARK.toneLocked * (1 - span(m.u, FLIGHT.markerOut[0], FLIGHT.markerOut[1])) * m.words };
  });
  const ink = useAnimatedStyle(() => ({ opacity: (1 - pose.get().white) * pose.get().words }));
  const white = useAnimatedStyle(() => ({ opacity: pose.get().white * pose.get().words }));
  // One rounded band per line behind the words. The paper keeps to the marker's 2 pt, so on the page it never covers
  // the next word of the paragraph; the pill, out on the field, has room for 6.
  const backing = (fill: ViewStyle, pad: number) => k.lines.map((line, j) => <View key={j} style={[styles.backing, fill, {
    left: line.x - pad, top: line.y + LAYOUT.chipInsetY, width: line.width + pad * 2, height: TYPE.body.lineHeight - LAYOUT.chipInsetY * 2,
  }]} />);
  return <Animated.View style={[styles.chip, { width: kw + 8, height: kh }, box]}>
    <Animated.View style={[StyleSheet.absoluteFill, paper]}>{backing(styles.paper, LAYOUT.markerPadX)}</Animated.View>
    <Animated.View style={[StyleSheet.absoluteFill, pill]}>{backing(styles.pill, LAYOUT.chipPadX)}</Animated.View>
    <Animated.View style={[StyleSheet.absoluteFill, bands]}>
      {k.lines.map((line, j) => <View key={j} style={[styles.band, {
        left: line.x - LAYOUT.markerPadX, top: line.y + LAYOUT.markerTop, width: line.width + LAYOUT.markerPadX * 2,
        height: TYPE.body.lineHeight - LAYOUT.markerTop - LAYOUT.markerBottom,
      }]} />)}
    </Animated.View>
    <Animated.View style={[StyleSheet.absoluteFill, ink]}>
      {k.lines.map((line, j) => <KeyLineText key={j} line={line} leadIn={j === 0 ? k.leadIn : 0} style={styles.ink} />)}
    </Animated.View>
    <Animated.View style={[StyleSheet.absoluteFill, white]}>
      {k.lines.map((line, j) => <KeyLineText key={j} line={line} leadIn={j === 0 ? k.leadIn : 0} style={styles.white} />)}
    </Animated.View>
  </Animated.View>;
}

type Props = { keys: KeyGeom[]; layout: SummaryLayout; top: number; values: SummaryValues };

/** Reduce Motion never mounts this layer: the summary cross-fades instead. */
export function ChipLayer({ keys, layout, top, values }: Props) {
  return <View style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    {keys.map((k, i) => <Chip key={i} k={k} index={i} target={layout.targets[i] ?? null} owns={layout.owns[i] ?? false} top={top}
      values={values} />)}
  </View>;
}

const styles = StyleSheet.create({
  chip: { position: 'absolute', left: 0, top: 0, transformOrigin: 'left top' },
  band: { position: 'absolute', borderRadius: LAYOUT.markerRadius, backgroundColor: COLOR.marker },
  backing: { position: 'absolute', borderRadius: LAYOUT.chipRadius },
  paper: { backgroundColor: COLOR.chipPaper },
  pill: { backgroundColor: COLOR.chipPill },
  ink: { position: 'absolute', color: COLOR.ink },
  white: { position: 'absolute', color: COLOR.summaryInk },
});
