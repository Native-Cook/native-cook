import { useState } from "react";
import { type LayoutRectangle, StyleSheet, Text, View } from "react-native";
import Animated, { type SharedValue, cancelAnimation, useAnimatedStyle, withSpring } from "react-native-reanimated";
import { referralSize as S, referralTone } from "./referral-palette";
import { BUTTON_DOT, gentle, systemFace } from "./ui";

// Contents wait for the shape; reversing peek takes them out before it closes.
export const PEEK = { open: 520, close: 360, name: 0.35, status: 0.42, action: 0.5, span: 0.4 };
// The action's 22 pt radius and 20 pt inset share the capsule's 42 pt end.
export const CAPSULE_ACTION = { height: 44, inset: 20, padding: 12 };
// 42 to the portrait centre, 36 to its edge, then 12 to the text.
const COPY_START = S.ring + S.focus / 2 + 12;
// Keep the copy clear of the action's touch target.
const COPY_ACTION_GAP = 8;
export type PeekFx = {
  peek: SharedValue<number>;
  open: SharedValue<boolean>;
  shape: SharedValue<number>;
  label: SharedValue<number>;
  // -1 at rest; otherwise peek when the action was pressed.
  launching: SharedValue<number>;
};

export function peekPart(peek: number, start: number) {
  "worklet";
  return Math.min(Math.max((peek - start) / PEEK.span, 0), 1);
}

// The ends move by this distance while a circle opens into the capsule.
export function capsuleShift(width: number, peek: number) {
  "worklet";
  return (width / 2 - S.cardPad - S.ring) * (1 - peek);
}

export function movePeek(fx: PeekFx, open: boolean, reduced: boolean) {
  "worklet";
  fx.open.set(open);
  cancelAnimation(fx.peek);
  fx.peek.set(reduced ? gentle(open ? 1 : 0) : withSpring(open ? 1 : 0, {
    duration: open ? PEEK.open : PEEK.close,
    dampingRatio: open ? 0.78 : 1,
    overshootClamping: !open,
  }));
}

export function ReferralCapsule({ width, name, status, action, joined, open, fx, press, reduced, onInvite, onActionLayout }: {
  width: number;
  name: string;
  status: string;
  action: string;
  joined: boolean;
  open: boolean;
  fx: PeekFx;
  press: SharedValue<number>;
  reduced: boolean;
  onInvite: () => void;
  onActionLayout: (layout: LayoutRectangle) => void;
}) {
  const [labelWidth, setLabelWidth] = useState(0);
  const actionWidth = Math.max(44, Math.ceil((labelWidth + CAPSULE_ACTION.padding * 2) / 2) * 2);
  // A joined friend needs only the status, not a second label taking copy space.
  const copyWidth = Math.max(width - S.cardPad * 2 - COPY_START - CAPSULE_ACTION.inset - (joined ? 0 : actionWidth + COPY_ACTION_GAP), 0);
  const [copyHeight, setCopyHeight] = useState(46);
  const copy = useAnimatedStyle(() => {
    const shift = reduced ? 0 : capsuleShift(width, fx.peek.get());
    return { left: S.cardPad + COPY_START + shift, width: Math.max(copyWidth - 2 * shift, 0) };
  });
  const title = useAnimatedStyle(() => {
    const p = peekPart(fx.peek.get(), PEEK.name);
    return { opacity: p, filter: `blur(${reduced ? 0 : 4 * (1 - p)}px)`, transform: [{ translateX: reduced ? 0 : 10 * (1 - p) }] };
  });
  const detail = useAnimatedStyle(() => {
    const p = peekPart(fx.peek.get(), PEEK.status);
    return { opacity: p, filter: `blur(${reduced ? 0 : 4 * (1 - p)}px)`, transform: [{ translateX: reduced ? 0 : 10 * (1 - p) }] };
  });
  const actionStyle = useAnimatedStyle(() => {
    const launching = !reduced && fx.launching.get() >= 0;
    const at = launching ? fx.launching.get() : fx.peek.get();
    const arrived = peekPart(at, PEEK.action);
    const p = launching ? arrived + (1 - arrived) * fx.shape.get() : arrived;
    return {
      opacity: p,
      filter: `blur(${reduced ? 0 : 4 * (1 - p)}px)`,
      transform: [{ translateX: reduced ? 0 : -capsuleShift(width, at) }, { scale: (reduced ? 1 : 0.92 + 0.08 * p) * (1 - 0.04 * press.get()) }],
    };
  });
  const body = useAnimatedStyle(() => {
    const shape = reduced || fx.launching.get() < 0 ? 0 : fx.shape.get();
    return {
      width: actionWidth + (BUTTON_DOT - actionWidth) * shape,
      height: CAPSULE_ACTION.height + (BUTTON_DOT - CAPSULE_ACTION.height) * shape,
      borderRadius: CAPSULE_ACTION.height / 2 + (BUTTON_DOT / 2 - CAPSULE_ACTION.height / 2) * shape,
      opacity: shape >= 1 ? 0 : 1,
    };
  });
  const label = useAnimatedStyle(() => {
    const fading = reduced || fx.launching.get() < 0 ? 0 : fx.label.get();
    return { opacity: 1 - fading, filter: `blur(${reduced ? 0 : 4 * fading}px)` };
  });

  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Animated.View
        accessible={open}
        accessibilityRole="text"
        accessibilityLabel={`${name}. ${status}.`}
        accessibilityElementsHidden={!open}
        importantForAccessibility={open ? "yes" : "no-hide-descendants"}
        style={[styles.copy, { top: S.cy - copyHeight / 2, height: copyHeight }, copy]}
      >
        <View style={{ width: copyWidth, gap: 4 }} onLayout={(e) => setCopyHeight(Math.ceil(e.nativeEvent.layout.height / 2) * 2)}>
          <Animated.Text style={[styles.name, title]} numberOfLines={1}>{name}</Animated.Text>
          <Animated.Text style={[styles.status, detail]} numberOfLines={2}>{status}</Animated.Text>
        </View>
      </Animated.View>
      {!joined && <Animated.View
        accessible={open}
        accessibilityRole="button"
        accessibilityLabel={`${action} ${name}`}
        accessibilityElementsHidden={!open}
        importantForAccessibility={open ? "yes" : "no-hide-descendants"}
        accessibilityActions={[{ name: "activate" }]}
        onAccessibilityAction={onInvite}
        onAccessibilityTap={onInvite}
        onLayout={(e) => {
          onActionLayout(e.nativeEvent.layout);
        }}
        style={[styles.action, { width: actionWidth }, actionStyle]}
      >
        <View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.measure, { width: width - S.cardPad * 2 }]}>
          <Text style={styles.actionLabel} onLayout={(e) => setLabelWidth(e.nativeEvent.layout.width)}>{action}</Text>
        </View>
        <Animated.View style={[styles.body, body]} />
        <Animated.Text style={[styles.actionLabel, label]}>{action}</Animated.Text>
      </Animated.View>}
    </View>
  );
}

const styles = StyleSheet.create({
  copy: { position: "absolute", overflow: "hidden" },
  name: { fontFamily: systemFace, fontSize: 17, lineHeight: 22, fontWeight: "600", color: referralTone.ink },
  status: { fontFamily: systemFace, fontSize: 13, lineHeight: 20, color: referralTone.sub },
  action: { position: "absolute", right: S.cardPad + CAPSULE_ACTION.inset, top: S.cy - CAPSULE_ACTION.height / 2, height: CAPSULE_ACTION.height, paddingHorizontal: CAPSULE_ACTION.padding, alignItems: "center", justifyContent: "center" },
  measure: { position: "absolute", alignItems: "flex-start", opacity: 0 },
  body: { position: "absolute", minWidth: BUTTON_DOT, backgroundColor: referralTone.ink },
  actionLabel: { fontFamily: systemFace, fontSize: 14, lineHeight: 20, fontWeight: "600", color: referralTone.night, includeFontPadding: false },
});
