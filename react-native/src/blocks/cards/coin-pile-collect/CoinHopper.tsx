// CoinHopper — the teal tile of Coin Pile Collect on its own: a live pile of satin coins whose sum is the amount, and a
// Collect pill that is the hopper. Any size through `style` (the tile keeps its 174:340 proportions, centred in the box);
// the canvas draws only the rounded tile, so nothing but the tile shows. The copy is the block's fixed English.
//
//   <CoinHopper defaultAmount={220.34} onEmpty={claim} />
import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent, type StyleProp, type ViewStyle } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue } from 'react-native-reanimated';
import { Canvas, Picture, Skia } from '@shopify/react-native-skia';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { drawHopper } from './cpc-art';
import { useCues } from './cpc-cues';
import { makeHopperCfg, useHopper, type HopperHandle } from './cpc-hook';
import { COPY, comp } from './cpc-theme';
import { formatMoney, useCpcRes } from './cpc-use';

export type CoinHopperHandle = HopperHandle;

export type CoinHopperProps = {
  /** dollars still in the tile (controlled: a change that is not the tile's own count reloads it) */
  amount?: number;
  /** dollars the tile starts with (uncontrolled), default 220.34 */
  defaultAmount?: number;
  /** number of coins, 8 to 32; another count than the default settles a new heap once on mount */
  coins?: number;
  seed?: number;
  /** a finger in the pile is a collider (default true) */
  touchable?: boolean;
  /** device gravity pushes the top coins sideways (default false) */
  tilt?: boolean;
  /** autoplay: a scripted thumb pours, throttles and refills in a loop; a touch interrupts it */
  autoPlay?: boolean;
  haptics?: boolean;
  sound?: boolean;
  /** JS thread, at most 10 a second: dollars collected since the last call, dollars left */
  onCollect?: (collected: number, remaining: number) => void;
  onEmpty?: (total: number) => void;
  onRefill?: () => void;
  style?: StyleProp<ViewStyle>;
};

export const CoinHopper = forwardRef<CoinHopperHandle, CoinHopperProps>(function CoinHopper(
  {
    amount,
    defaultAmount = COPY.amountCents / 100,
    coins,
    seed,
    touchable = true,
    tilt = false,
    autoPlay = false,
    haptics = true,
    sound = false,
    onCollect,
    onEmpty,
    onRefill,
    style,
  },
  handle,
) {
  const reduce = useReducedMotionPreference();
  const [box, setBox] = useState({ w: 0, h: 0 });
  const [total, setTotal] = useState(Math.round((amount ?? defaultAmount) * 100));
  const lastRemaining = useRef(total);
  useEffect(() => {
    if (amount === undefined) return;
    const c = Math.round(amount * 100);
    if (c !== lastRemaining.current) {
      lastRemaining.current = c;
      setTotal(c);
    }
  }, [amount]);

  const t = comp.teal;
  const S = box.w > 0 && box.h > 0 ? Math.min(box.w / t.w, box.h / t.h) : 1;
  const ox = (box.w - t.w * S) / 2;
  const oy = (box.h - t.h * S) / 2;
  const res = useCpcRes(box.w, box.h, S);
  const cfg = useMemo(() => makeHopperCfg(coins, seed), [coins, seed]);
  const { onCue, spoken, empty } = useCues({
    haptics,
    sound,
    totalCents: total,
    onCollect: (c, r) => {
      lastRemaining.current = Math.round(r * 100);
      onCollect?.(c, r);
    },
    onEmpty,
    onRefill,
  });
  const { sim, tick, pan, api } = useHopper(cfg, {
    totalCents: total,
    coins,
    seed,
    autoPlay,
    touchable,
    tilt,
    reduce,
    ready: res !== null && box.w > 0,
    scale: S,
    onCue,
  });
  useImperativeHandle(handle, () => api, [api]);

  const picture = useDerivedValue(() => {
    'worklet';
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, box.w, box.h));
    if (res) {
      canvas.translate(ox, oy);
      canvas.scale(S, S);
      drawHopper(canvas, res, sim.get().A);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => setBox({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  return (
    <View style={[styles.box, style]} onLayout={onLayout}>
      {res ? (
        <Canvas style={StyleSheet.absoluteFill} accessible={false}>
          <Picture picture={picture} />
        </Canvas>
      ) : null}
      <GestureDetector gesture={pan}>
        <View style={{ position: 'absolute', left: ox, top: oy, width: t.w * S, height: t.h * S }} accessible={false} />
      </GestureDetector>
      <View
        pointerEvents="none"
        accessible
        accessibilityLabel={spoken}
        accessibilityLiveRegion="polite"
        style={{ position: 'absolute', left: ox, top: oy, width: t.w * S, height: 100 * S }}
      />
      <View
        accessible
        accessibilityRole="button"
        accessibilityLabel={empty ? COPY.a11yEmpty : COPY.a11yCollect(formatMoney(total))}
        accessibilityHint={COPY.hintCollect}
        accessibilityState={{ disabled: empty }}
        onAccessibilityTap={() => api.pour()}
        pointerEvents="none"
        style={{
          position: 'absolute',
          left: ox + comp.pill.x * S,
          top: oy + comp.pill.y * S,
          width: comp.pill.w * S,
          height: comp.pill.h * S,
        }}
      />
    </View>
  );
});

const styles = StyleSheet.create({
  box: { width: comp.teal.w, height: comp.teal.h, backgroundColor: 'transparent' },
});
