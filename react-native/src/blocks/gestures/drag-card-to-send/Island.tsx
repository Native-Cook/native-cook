/**
 * The island's liquid (one Skia shader over the top of the screen), the amount riding the drop, and the receipt the
 * island becomes after a send: who and how much on the left of the cut-out, Undo and its countdown on the right.
 */
import { Canvas } from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  ReduceMotion, useAnimatedProps, useAnimatedStyle, useDerivedValue, withDelay, withTiming, type EntryExitAnimationFunction,
} from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';

import { clamp01, liquidLook } from './liquid-look';
import { EASE_OUT_CSS, ROLL, SEND, UNDO_PRESS, easeOut } from './motion';
import { Avatar } from './People';
import { LiquidLayer, liquidUniforms } from './scenes';
import { COLORS, GLYPH, PEOPLE, RECEIPT, STEPS, TYPE, formatMoney, type Layout } from './theme';
import type { SendValues } from './use-send-motion';

export type Subscribe = (fn: (step: number, person: number) => void) => () => void;

export function useLiquid(lay: Layout, v: SendValues) {
  return useDerivedValue(() => liquidLook(lay, { r: v.r.get(), t: v.t.get(), flight: v.flight.get(), receipt: v.receipt.get() }));
}
type Look = ReturnType<typeof useLiquid>;

export function IslandLiquid({ lay, look }: { lay: Layout; look: Look }) {
  const aa = 1 / PixelRatio.get();
  const uniforms = useDerivedValue(() => liquidUniforms(look.get(), aa));
  const height = useDerivedValue(() => look.get().bottom);
  // Tall enough for the drop's whole flight (it ends inside the avatar).
  const canvasH = lay.avatarY + 80;
  return (
    <View style={[styles.canvas, { width: lay.width, height: canvasH }]} pointerEvents="none">
      <Canvas style={StyleSheet.absoluteFill}>
        <LiquidLayer width={lay.width} height={height} uniforms={uniforms} />
      </Canvas>
    </View>
  );
}

/**
 * The amount, written on the drop. It changes per detent (React state, never per frame): the new figure rises in
 * after the old one has left, 160 ms in all. Not a rolling odometer: detents come faster than drums can turn and read garbled.
 */
export function DropAmount({ look, subscribe, reduced }: { look: Look; subscribe: Subscribe; reduced: boolean }) {
  const [step, setStep] = useState(0);
  useEffect(() => subscribe((s) => { if (s >= 0) setStep(s); }), [subscribe]);
  const style = useAnimatedStyle(() => {
    const l = look.get().label;
    return {
      opacity: l.opacity,
      transform: [{ translateX: l.x - BOX / 2 }, { translateY: l.y - TYPE.amount.lineHeight / 2 }, { scale: l.scale }],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.amount, style]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Animated.Text key={step} style={styles.amountText} entering={reduced ? AMOUNT_IN.still : AMOUNT_IN.rise}
        exiting={reduced ? AMOUNT_OUT.still : AMOUNT_OUT.rise}>
        {`$${STEPS[step]}`}
      </Animated.Text>
    </Animated.View>
  );
}
const BOX = 140;
/**
 * The old amount is gone before the new one is half in, so two figures never stack on the drop. The figures rise
 * `AMOUNT_RISE` pt; under Reduce Motion they only crossfade.
 */
const AMOUNT_RISE = 6;
const amountOut = (rise: number): EntryExitAnimationFunction => () => {
  'worklet';
  const t = { duration: ROLL.stepMs * 0.5, easing: easeOut, reduceMotion: ReduceMotion.Never };
  return {
    initialValues: { opacity: 1, transform: [{ translateY: 0 }] },
    animations: { opacity: withTiming(0, t), transform: [{ translateY: withTiming(-rise, t) }] },
  };
};
const amountIn = (rise: number): EntryExitAnimationFunction => () => {
  'worklet';
  const t = { duration: ROLL.stepMs, easing: easeOut, reduceMotion: ReduceMotion.Never };
  const after = ROLL.stepMs * 0.35;
  return {
    initialValues: { opacity: 0, transform: [{ translateY: rise }] },
    animations: { opacity: withDelay(after, withTiming(1, t), ReduceMotion.Never), transform: [{ translateY: withDelay(after, withTiming(0, t), ReduceMotion.Never) }] },
  };
};
const AMOUNT_OUT = { rise: amountOut(AMOUNT_RISE), still: amountOut(0) };
const AMOUNT_IN = { rise: amountIn(AMOUNT_RISE), still: amountIn(0) };

type ReceiptProps = {
  lay: Layout; v: SendValues; last: { person: number; cents: number } | null; canUndo: boolean; reduced: boolean; onUndo: () => void;
};

const AnimatedCircle = Animated.createAnimatedComponent(Circle);
const RING_R = (RECEIPT.ring - RECEIPT.ringStroke) / 2;
const RING_C = 2 * Math.PI * RING_R;

/**
 * The receipt, laid out like an expanded Live Activity: each end is concentric with the pill's round end (the photo
 * and the Undo button sit 12 and 14 pt inside it), and nothing crosses the hardware cut-out in the middle.
 */
export function Receipt({ lay, v, last, canUndo, reduced, onUndo }: ReceiptProps) {
  // The content fades in over the last 15 % of the island's growth and grows with the island itself (0.84 → 1, from
  // the pill's centre, a little behind it to cover a frame the canvas may lag), so no photo or line of type ever stands
  // outside the black. Reduce Motion: no scale, so the fade waits for the last tenth.
  const isl = lay.island.hw;
  const full = lay.receipt.hw;
  const grow = reduced ? 0 : 1;
  const from = reduced ? SEND.receiptContentFromReduced : SEND.receiptContentFrom;
  const style = useAnimatedStyle(() => {
    const k = v.receipt.get();
    const width = isl + (full - isl) * k;
    const lag = SEND.receiptContentLag * clamp01((1 - k) / (1 - from));
    return {
      opacity: clamp01((k - from) / (1 - from)),
      transform: [{ scale: 1 - grow * (1 - (width / full) * (1 - lag)) }],
    };
  });
  // A finger still on Undo when the countdown ends gets no press-out (the button is disabled): `canUndo` clears it.
  const [pressed, setPressed] = useState(false);
  if (pressed && !canUndo) setPressed(false);
  // The countdown: the ring empties clockwise from twelve o'clock over the 4 s Undo stays open.
  const ring = useAnimatedProps(() => ({ strokeDashoffset: RING_C * (1 - v.undo.get()) }));
  // Undo clears `last` at once, but the receipt is still folding: it keeps showing the payment it fades out with.
  const [held, setHeld] = useState(last);
  if (last !== null && last !== held) setHeld(last);
  const shown = last ?? held;
  const person = shown ? PEOPLE[shown.person] : null;
  const amount = shown ? formatMoney(shown.cents, shown.cents % 100 !== 0) : '';
  const r = lay.receipt;
  const end = r.hh - RECEIPT.undoHeight / 2;
  return (
    <Animated.View style={[styles.receipt, { left: r.cx - r.hw, top: r.cy - r.hh, width: r.hw * 2, height: r.hh * 2 }, style]}
      pointerEvents={canUndo ? 'box-none' : 'none'}>
      <View style={[styles.lead, { left: RECEIPT.pad }]} accessible={last !== null} accessibilityLiveRegion="polite"
        accessibilityElementsHidden={last === null} accessibilityLabel={last && person ? `Sent ${amount} to ${person.full}` : ''}>
        <View>
          {person ? <Avatar source={person.photo} size={RECEIPT.avatar} /> : <View style={styles.blank} />}
          <View style={styles.badge}>
            <Svg width={12} height={12} viewBox="5 6 14 12">
              <Path d={GLYPH.check} stroke={COLORS.onIsland} strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </Svg>
          </View>
        </View>
        <View style={styles.text}>
          <Text style={[TYPE.receiptMeta, styles.mute]} numberOfLines={1}>{person ? `To ${person.name}` : ''}</Text>
          <Text style={[TYPE.receiptAmount, styles.onIsland]} numberOfLines={1}>{amount}</Text>
        </View>
      </View>
      <Pressable onPress={onUndo} disabled={!canUndo} hitSlop={8} pressRetentionOffset={16}
        onPressIn={() => setPressed(true)} onPressOut={() => setPressed(false)}
        accessibilityRole="button" accessibilityLabel="Undo the payment" style={[styles.undoHit, { right: end, top: end }]}>
        {/* Press transition inline: StyleSheet.create does not type Reanimated's CSS easing objects. */}
        <Animated.View style={[styles.undo, { transitionProperty: ['transform', 'opacity'], transitionTimingFunction: EASE_OUT_CSS }, pressed ? (reduced ? styles.undoDim : styles.undoPressed) : styles.undoRest]}>
        <Svg width={RECEIPT.ring} height={RECEIPT.ring}>
          <Circle cx={RECEIPT.ring / 2} cy={RECEIPT.ring / 2} r={RING_R} stroke="rgba(255,255,255,0.22)" strokeWidth={RECEIPT.ringStroke} fill="none" />
          <AnimatedCircle cx={RECEIPT.ring / 2} cy={RECEIPT.ring / 2} r={RING_R} stroke={COLORS.onIsland} strokeWidth={RECEIPT.ringStroke}
            strokeLinecap="round" fill="none" strokeDasharray={`${RING_C} ${RING_C}`} animatedProps={ring}
            transform={`rotate(-90 ${RECEIPT.ring / 2} ${RECEIPT.ring / 2})`} />
        </Svg>
        <Text style={[TYPE.undo, styles.onIsland]}>Undo</Text>
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  canvas: { position: 'absolute', left: 0, top: 0 },
  amount: { position: 'absolute', left: 0, top: 0, width: BOX, height: TYPE.amount.lineHeight },
  amountText: { ...TYPE.amount, position: 'absolute', left: 0, right: 0, textAlign: 'center', color: COLORS.onIsland, fontVariant: ['tabular-nums'] },
  receipt: { position: 'absolute' },
  lead: { position: 'absolute', top: RECEIPT.pad, flexDirection: 'row', alignItems: 'center', gap: RECEIPT.gap },
  blank: { width: RECEIPT.avatar, height: RECEIPT.avatar },
  badge: {
    position: 'absolute', right: -3, bottom: -3, width: RECEIPT.badge, height: RECEIPT.badge, borderRadius: RECEIPT.badge / 2,
    borderWidth: 2, borderColor: COLORS.island, backgroundColor: COLORS.accent, alignItems: 'center', justifyContent: 'center',
  },
  text: { justifyContent: 'center' },
  mute: { color: COLORS.onIslandMute },
  onIsland: { color: COLORS.onIsland, fontVariant: ['tabular-nums'] },
  undoHit: { position: 'absolute', width: RECEIPT.undoWidth, height: RECEIPT.undoHeight },
  undo: {
    width: RECEIPT.undoWidth, height: RECEIPT.undoHeight, borderRadius: RECEIPT.undoHeight / 2,
    backgroundColor: COLORS.undo, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7,
  },
  /** Let go: back faster than it went in. */
  undoRest: { opacity: 1, transform: [{ scale: 1 }], transitionDuration: UNDO_PRESS.outMs },
  undoPressed: { opacity: UNDO_PRESS.opacity, transform: [{ scale: UNDO_PRESS.scale }], transitionDuration: UNDO_PRESS.inMs },
  undoDim: { opacity: UNDO_PRESS.opacity, transform: [{ scale: 1 }], transitionDuration: UNDO_PRESS.inMs },
});
