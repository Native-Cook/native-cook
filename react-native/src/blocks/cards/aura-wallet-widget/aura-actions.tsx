import { useRef, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { NumericText } from "expo-content-transition";
import Animated, { useAnimatedStyle } from "react-native-reanimated";
import type { AuraDrive } from "./aura-drive";
import { AuraPhase, auraActionFrames, auraFlow } from "./aura-flow";
import { AURA_ICONS } from "./aura-icons";
import type { AuraMachine } from "./aura-machine";
import { AURA_ACTIONS, auraDesign as D, type AuraAction, type AuraFrames, type AuraSkin } from "./aura-palette";
import { usePreferences } from "./i18n";
import { AuraIcon, systemFace, useAuraPress } from "./ui";

// The hit areas trade width with the glass. Counter-scaling their contents
// keeps type sharp while native hit testing follows the transformed frame.
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

export function AuraActions({ frames, skin, drive, machine }: {
  frames: AuraFrames;
  skin: AuraSkin;
  drive: AuraDrive;
  machine: AuraMachine;
}) {
  const { t } = usePreferences();
  const labels: Record<AuraAction, string> = {
    settings: t("Settings", "Cài đặt"),
    swap: t("Swap", "Đổi màu"),
    receive: t("Receive", "Nhận"),
    send: t("Send", "Gửi"),
  };
  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      {AURA_ACTIONS.map((action, index) => (
        <Pillow key={action} action={action} index={index} label={labels[action]}
          frames={frames} skin={skin} drive={drive} machine={machine} />
      ))}
    </View>
  );
}

function Pillow({ action, index, label, frames: f, skin, drive, machine }: {
  action: AuraAction;
  index: number;
  label: string;
  frames: AuraFrames;
  skin: AuraSkin;
  drive: AuraDrive;
  machine: AuraMachine;
}) {
  const { contentStyle, onPressIn, onPressOut } = useAuraPress(drive.press[index]);
  const [labelWidth, setLabelWidth] = useState(0);
  const accepted = useRef(true);
  const { wide, phase, clock } = drive;
  const { reduced, state } = machine;
  const { u } = f;
  const frame = f.actions[index];
  const labelled = action === "receive" || action === "send";
  const color = action === "send" ? skin.onAccent : skin.ink;
  const side = labelled ? D.actions.arrow : D.actions.icon;
  const expanded = state.phase !== AuraPhase.rest && !state.landed;
  const offered = action === "send" && expanded;
  // A pull that has not reached its first step offers nothing yet: plain "Send".
  const priced = offered && state.amount > 0;
  const pillow = {
    left: frame.x,
    top: frame.y,
    width: frame.w,
    height: frame.h,
  };
  const content = { gap: labelled ? D.actions.arrowGap * u : 0, left: labelled ? D.details.actionOpticalX * u : 0 };
  const text = {
    fontSize: D.actions.labelSize * u,
    lineHeight: D.lines.action * u,
    height: D.lines.action * u,
    color,
  };
  const targetStyle = useAnimatedStyle(() => {
    const trade = reduced ? (wide.get() < 0.5 ? 0 : 1) : wide.get();
    const frames = auraActionFrames(trade);
    const width = frames.widths[index] * u;
    const centre = frames.xs[index] * u + width / 2;
    return { transform: [{ translateX: centre - (frame.x + frame.w / 2) }, { scaleX: width / frame.w }] };
  });
  const counterStyle = useAnimatedStyle(() => {
    const p = wide.get();
    const trade = reduced ? (p < 0.5 ? 0 : 1) : p;
    const frames = auraActionFrames(trade);
    return {
      opacity: reduced && index >= 2 ? Math.abs(1 - 2 * Math.min(1, Math.max(0, p))) : 1,
      transform: [{ scaleX: frame.w / (frames.widths[index] * u) }],
    };
  });
  const receiveText = useAnimatedStyle(() => ({
    opacity: action === "receive" ? Math.max(0, 1 - wide.get() * 2) : 1,
  }));
  const receiveShift = useAnimatedStyle(() => ({
    transform: [{ translateX: action === "receive"
      ? -(labelWidth + D.actions.arrowGap * u) / 2 * (reduced ? (wide.get() < 0.5 ? 0 : 1) : wide.get())
      : 0 }],
  }));
  const outgoing = useAnimatedStyle(() => {
    const sending = action === "send" && phase.get() === AuraPhase.sending;
    const p = sending ? Math.min(1, clock.get() / auraFlow.views.arrow.exitTime) : 0;
    return { opacity: 1 - p, transform: [{ translateY: reduced ? 0 : -auraFlow.views.arrow.exit * u * p }] };
  });
  const incoming = useAnimatedStyle(() => {
    const sending = action === "send" && phase.get() === AuraPhase.sending;
    const p = sending ? Math.max(0, Math.min(1, (clock.get() - auraFlow.views.arrow.enterDelay) / auraFlow.views.arrow.enterTime)) : 0;
    return { opacity: p, transform: [{ translateY: reduced ? 0 : auraFlow.views.arrow.enter * u * (1 - p) }] };
  });

  return (
    <AnimatedPressable
      accessibilityRole="button"
      accessibilityLabel={priced ? `${label} $${state.amount}` : label}
      pressRetentionOffset={D.touch.retention}
      onPressIn={() => {
        machine.onTouch();
        accepted.current = action !== "send" || !machine.isLocked();
        if (accepted.current) onPressIn();
      }}
      onPressOut={onPressOut}
      onPress={() => { if (action === "send" && accepted.current) machine.send(); }}
      style={[styles.pillow, pillow, targetStyle]}
    >
      <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants" style={counterStyle}>
        <Animated.View style={[styles.content, content, contentStyle]}>
          {labelled && <Animated.View style={receiveText}>
            <Text allowFontScaling={false} numberOfLines={1}
              onLayout={(event) => setLabelWidth(event.nativeEvent.layout.width)} style={[styles.label, text]}>{label}</Text>
          </Animated.View>}
          {priced && <Amount dollars={state.amount} u={u} color={color} reduced={reduced} />}
          <Animated.View style={receiveShift}>
            <View style={{ width: side * u, height: side * u }}>
              <Animated.View style={[StyleSheet.absoluteFill, outgoing]}>
                <AuraIcon path={AURA_ICONS[action]} side={side} u={u} color={color} rounded />
              </Animated.View>
              {action === "send" && <Animated.View style={[StyleSheet.absoluteFill, incoming]}>
                <AuraIcon path={AURA_ICONS.send} side={side} u={u} color={color} rounded />
              </Animated.View>}
            </View>
          </Animated.View>
        </Animated.View>
      </Animated.View>
    </AnimatedPressable>
  );
}

// The invisible measure uses the same face as the native rolling glyphs.
// A changed amount can relayout once; it never changes layout on a frame.
function Amount({ dollars, u, color, reduced }: { dollars: number; u: number; color: string; reduced: boolean }) {
  const [width, setWidth] = useState<number | undefined>(undefined);
  const value = `$${dollars}`;
  const text = {
    fontSize: D.actions.labelSize * u,
    lineHeight: D.lines.action * u,
    height: D.lines.action * u,
    color,
  };
  return (
    <View style={{ height: D.lines.action * u }}>
      <Text allowFontScaling={false} style={[styles.label, text, styles.measure]}
        onLayout={(event) => setWidth(event.nativeEvent.layout.width)}>{value}</Text>
      <NumericText value={value} animated={!reduced} fontSize={D.actions.labelSize * u} fontWeight="500"
        letterSpacing={D.tight} color={color} style={[StyleSheet.absoluteFill, { width }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  pillow: {
    position: "absolute",
    alignItems: "center",
    justifyContent: "center",
  },
  content: { flexDirection: "row", alignItems: "center", justifyContent: "center" },
  label: {
    fontFamily: systemFace,
    fontWeight: "500",
    letterSpacing: D.tight,
    includeFontPadding: false,
  },
  measure: { opacity: 0 },
});
