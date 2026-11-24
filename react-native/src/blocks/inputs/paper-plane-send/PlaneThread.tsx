import type { JSX } from "react";
import { StyleSheet, Text, View } from "react-native";
import type { LayoutChangeEvent } from "react-native";
import Animated, { useAnimatedStyle } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { bubbleAt, deliveredAt, shiftAt } from "./planeMotion";
import { BUBBLE_IN, BUBBLE_OUT, DELIVERED, INK, MESSAGE, MUTE as MUTE_RECEIPT, THREAD } from "./planeScene";

export const GUTTER = 16;
/** A one-line bubble's height, the gap between bubbles and the receipt line's height. */
export const BUBBLE_H = 38;
export const BUBBLE_GAP = 6;
export const RECEIPT_H = 20;
/** How far the thread moves up for the new message. */
export const SHIFT = BUBBLE_H + BUBBLE_GAP + RECEIPT_H;

export type PlaneThreadProps = {
  /** How far the thread's bottom edge rests above the screen's bottom edge (pt). */
  bottom: number;
  t: SharedValue<number>;
  /** Reports the sent bubble's width once it is laid out (the plane lands on its centre). */
  onSlotWidth: (w: number) => void;
};

/** The conversation: three bubbles that move up as one, and the new message's bubble with its receipt, which pops
 * in where the plane lands. Transforms and opacity only. */
export function PlaneThread({ bottom, t, onSlotWidth }: PlaneThreadProps): JSX.Element {
  const older = useAnimatedStyle(() => ({ transform: [{ translateY: -SHIFT * shiftAt(t.get()) }] }));
  const sent = useAnimatedStyle(() => {
    const b = [0, 1];
    bubbleAt(t.get(), b);
    return { opacity: b[0], transform: [{ scale: b[1] }] };
  });
  const receipt = useAnimatedStyle(() => ({ opacity: deliveredAt(t.get()) }));
  const measure = (e: LayoutChangeEvent) => onSlotWidth(e.nativeEvent.layout.width);
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Animated.View style={[styles.older, { bottom }, older]}>
        {THREAD.map((m) => (
          <View key={m.text} style={[styles.bubble, m.out ? styles.out : styles.in]}>
            <Text numberOfLines={1} style={styles.text}>
              {m.text}
            </Text>
          </View>
        ))}
      </Animated.View>
      <Animated.View onLayout={measure} style={[styles.bubble, styles.out, styles.sent, { bottom: bottom + RECEIPT_H }, sent]}>
        <Text numberOfLines={1} style={styles.text}>
          {MESSAGE}
        </Text>
      </Animated.View>
      <Animated.Text style={[styles.receipt, { bottom }, receipt]}>{DELIVERED}</Animated.Text>
    </View>
  );
}

const styles = StyleSheet.create({
  older: { position: "absolute", left: GUTTER, right: GUTTER, gap: BUBBLE_GAP },
  bubble: { height: BUBBLE_H, maxWidth: "82%", paddingHorizontal: 14, borderRadius: 19, justifyContent: "center" },
  in: { alignSelf: "flex-start", backgroundColor: BUBBLE_IN, borderBottomLeftRadius: 6, borderWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.06)" },
  out: { alignSelf: "flex-end", backgroundColor: BUBBLE_OUT, borderBottomRightRadius: 6 },
  sent: { position: "absolute", right: GUTTER, transformOrigin: "right bottom" },
  text: { fontSize: 16, lineHeight: 21, color: INK },
  receipt: { position: "absolute", right: GUTTER + 4, height: RECEIPT_H, lineHeight: RECEIPT_H, fontSize: 12, fontWeight: "500", color: MUTE_RECEIPT },
});
