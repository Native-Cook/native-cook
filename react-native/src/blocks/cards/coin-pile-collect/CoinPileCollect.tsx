// Coin Pile Collect — the "Today's offers" screen of Halden, an invented bank. The teal tile is a live pile of heavy
// satin coins: the amount is the sum of the coins left in it, the Collect pill is a hopper you hold (the pile pours in,
// nearest coins lit cyan), slide the thumb away from the pill to throttle it to single coins, tap to pour it all, put a
// finger in the pile to push coins around. LOOP = 8 s of autoplay: a held pour, a throttled stretch, the rest, the empty
// beat, the rain that refills the tile. Everything is one Skia picture per frame (page, tiles, type, pile, pill).
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Canvas, Picture, Skia } from '@shopify/react-native-skia';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { drawScreen } from './cpc-art';
import { useCues } from './cpc-cues';
import { makeHopperCfg, useHopper, type HopperHandle } from './cpc-hook';
import { LOOP } from './cpc-motion';
import { COPY, comp, hopperRect, makeLayout, ref } from './cpc-theme';
import { formatMoney, useCpcRes } from './cpc-use';

export { LOOP };

export type CoinPileCollectProps = {
  /** plays the 8 s loop; a touch interrupts it and it resumes 2 s after the last touch */
  autoPlay?: boolean;
  /** total in cents the tile stands for (default 22034 = $220.34) */
  amountCents?: number;
  /** device gravity pushes the top coins sideways (iOS and Android, live modes only) */
  tilt?: boolean;
  haptics?: boolean;
  sound?: boolean;
  onCollect?: (collected: number, remaining: number) => void;
  onEmpty?: (total: number) => void;
  onRefill?: () => void;
  handleRef?: (h: HopperHandle) => void;
};

export function CoinPileCollect({
  autoPlay = false,
  amountCents = COPY.amountCents,
  tilt = true,
  haptics = true,
  sound = false,
  onCollect,
  onEmpty,
  onRefill,
  handleRef,
}: CoinPileCollectProps) {
  const { width: W, height: H } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotionPreference();
  const L = useMemo(() => makeLayout(W, H, insets.top, insets.bottom), [W, H, insets.top, insets.bottom]);
  const res = useCpcRes(W, H, L.S, L.oy);
  const cfg = useMemo(() => makeHopperCfg(undefined, undefined), []);
  const { onCue, spoken, empty } = useCues({ haptics, sound, totalCents: amountCents, onCollect, onEmpty, onRefill });
  const { sim, theta, tick, pan, api } = useHopper(cfg, {
    totalCents: amountCents,
    autoPlay,
    touchable: true,
    tilt,
    reduce,
    ready: res !== null,
    scale: L.S,
    onCue,
  });
  useEffect(() => handleRef?.(api), [handleRef, api]);

  const picture = useDerivedValue(() => {
    'worklet';
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, W, H));
    if (res) drawScreen(canvas, res, L, sim.get().A, theta.get());
    return recorder.finishRecordingAsPicture();
  });

  const hop = hopperRect(L);
  const S = L.S;
  const pill = { x: hop.x + comp.pill.x * S, y: hop.y + comp.pill.y * S, w: comp.pill.w * S, h: comp.pill.h * S };
  const copyBox = { x: hop.x, y: hop.y, w: hop.w, h: 100 * S };
  const title = { x: L.ox + comp.title.x * S, y: L.oy + comp.title.capTop * S - 8 * S, w: 240 * S, h: 120 * S };

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      {res ? (
        <Canvas style={StyleSheet.absoluteFill} accessible={false}>
          <Picture picture={picture} />
        </Canvas>
      ) : null}
      <GestureDetector gesture={pan}>
        <View style={{ position: 'absolute', left: hop.x, top: hop.y, width: hop.w, height: hop.h }} accessible={false} />
      </GestureDetector>
      <View
        pointerEvents="none"
        accessible
        accessibilityRole="header"
        accessibilityLabel={COPY.a11yTitle}
        style={{ position: 'absolute', ...title }}
      />
      <View
        pointerEvents="none"
        accessible
        accessibilityLabel={spoken}
        accessibilityLiveRegion="polite"
        style={{ position: 'absolute', left: copyBox.x, top: copyBox.y, width: copyBox.w, height: copyBox.h }}
      />
      <View
        accessible
        accessibilityRole="button"
        accessibilityLabel={empty ? COPY.a11yEmpty : COPY.a11yCollect(formatMoney(amountCents))}
        accessibilityHint={COPY.hintCollect}
        accessibilityState={{ disabled: empty }}
        onAccessibilityTap={() => api.pour()}
        pointerEvents="none"
        style={{ position: 'absolute', left: pill.x, top: pill.y, width: pill.w, height: pill.h }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: ref.color.page },
});
