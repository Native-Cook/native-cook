/**
 * The source's refresh lever on its dark artboard: pull it down until it is full, let go, and it is thrown
 * up while the request runs, then falls back and lights again.
 */
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useDerivedValue, useReducedMotion } from 'react-native-reanimated';

import { REPLICA_ONLY } from './motion';
import { PullLever } from './PullLever';
import { RollingCaption } from './RollingCaption';
import { ARTBOARD, COLOR, LAYOUT, LEVER, TYPE } from './theme';
import { usePullLever } from './use-pull-lever';

const CAPTION = { refreshing: 'Checking…', updated: 'Updated just now' } as const;

export type PullLeverRefreshProps = { autoPlay?: boolean; onRefresh?: () => Promise<void> };

export function PullLeverRefresh({ autoPlay = false, onRefresh }: PullLeverRefreshProps) {
  const { height } = useWindowDimensions();
  const reduceMotion = useReducedMotion();
  const { gesture, refreshNow, label, values } = usePullLever({ autoPlay, reduceMotion, onFetch: onRefresh });
  const { angle, sway, idle, shake, fill, lit, bloom, glow, breath, pulse, ripple, knobLit, arcLit } = values;
  const turn = useDerivedValue(() => angle.get() + sway.get() + idle.get() + shake.get());
  // The source is a 720 × 540 artboard on its own dark ground: shown at the screen's width, centred.
  const top = (height - ARTBOARD.height) / 2;

  return (
    <GestureHandlerRootView style={styles.root}>
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          collapsable={false}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Refresh"
          accessibilityHint="Pull the lever down, or double tap, to refresh"
          accessibilityState={{ busy: label === 'refreshing' }}
          accessibilityValue={label === 'updated' ? { text: 'Updated just now' } : undefined}
          onAccessibilityTap={refreshNow}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={(e) => { if (e.nativeEvent.actionName === 'activate') refreshNow(); }}
        >
          <View style={[styles.lever, { top: top + ARTBOARD.leverCentreY - LEVER.thickness / 2 }]}>
            <PullLever angle={turn} fill={fill} lit={lit} bloom={bloom} glow={glow} breath={breath} pulse={pulse} ripple={ripple} knobLit={knobLit} arcLit={arcLit} />
          </View>
          <View style={[styles.captionSlot, { top: top + ARTBOARD.leverCentreY - TYPE.caption.lineHeight / 2 }]} pointerEvents="none">
            {!REPLICA_ONLY && <RollingCaption text={label === 'none' ? '' : CAPTION[label]} reduceMotion={reduceMotion} />}
          </View>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.background },
  lever: { position: 'absolute', left: ARTBOARD.leverLeft },
  // Left of the pivot, on its line: the lever only ever swings to the right of it.
  captionSlot: { position: 'absolute', left: 0, width: ARTBOARD.leverLeft - LAYOUT.captionGap, height: TYPE.caption.lineHeight },
});
