import { useId } from "react";
import { Image, Pressable, StyleSheet, Text, View } from "react-native";
import Svg, { Circle, ClipPath, Defs, ForeignObject, G, LinearGradient, Path, Stop } from "react-native-svg";
import Animated, { useAnimatedProps, useAnimatedStyle } from "react-native-reanimated";
import { AURA_LAND_Y, auraFlow } from "./aura-flow";
import type { AuraMachine, AuraReceipt } from "./aura-machine";
import { AURA_ICONS } from "./aura-icons";
import { auraDesign as D, type AuraFrames, type AuraSkin } from "./aura-palette";
import { FRIENDS, LAST } from "./aura-people";
import { formatAuraCents, usePreferences } from "./i18n";
import { centeredLine, systemFace, touchInsets } from "./ui";

// A receipt laid over the glass pane. Orbit's pearl and its ringed planet
// are an invented mark; the pane's material belongs entirely to the canvas.
export function AuraActivity({ frames: f, skin, machine }: { frames: AuraFrames; skin: AuraSkin; machine: AuraMachine }) {
  const { t } = usePreferences();
  const { u } = f;
  const middle = f.size / 2;
  // The pane's two columns meet at the widget's centre, not the text's edge.
  const label = {
    left: D.paneLabel.x * u,
    width: middle - D.paneLabel.x * u,
    ...centeredLine(D.paneLabel.cy, D.lines.paneLabel, u),
  };
  const viewAll = {
    left: middle,
    width: D.paneLabel.right * u - middle,
    ...centeredLine(D.paneLabel.cy, D.lines.paneLabel, u),
  };

  // The line of the last transaction: from the mark's left to the amount's right.
  const line = {
    left: (D.paneMark.cx - D.paneMark.size / 2) * u,
    top: (D.paneMark.cy - D.paneMark.size / 2) * u,
    width: (D.paneAmount.right - D.paneMark.cx + D.paneMark.size / 2) * u,
    height: D.paneMark.size * u,
  };
  const { receipt } = machine.state;
  const dollars = (receipt.cents / 100).toFixed(2);
  const spoken = receipt.friend >= 0
    ? t(`${FRIENDS[receipt.friend].name}, just now, minus ${dollars} dollars`, `${FRIENDS[receipt.friend].name}, vừa xong, trừ ${dollars} đô la`)
    : t(`${LAST.title}, ${LAST.date}, ${LAST.time}, ${dollars} dollars`, `${LAST.title}, ${LAST.date}, ${LAST.time}, ${dollars} đô la`);

  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <Text
        allowFontScaling={false}
        numberOfLines={1}
        style={[styles.text, label, {
          fontSize: D.paneLabel.size * u,
          lineHeight: D.lines.paneLabel * u,
          color: skin.sub,
        }]}
      >
        {t("Last transaction", "Giao dịch gần nhất")}
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={t("View all transactions", "Xem tất cả giao dịch")}
        hitSlop={touchInsets(D.paneLabel.right * u - middle, D.lines.paneLabel * u)}
        onPress={() => {}}
        style={[styles.absolute, viewAll]}
      >
        <Text
          allowFontScaling={false}
          numberOfLines={1}
          style={[styles.viewAll, {
            fontSize: D.paneLabel.size * u,
            lineHeight: D.lines.paneLabel * u,
            height: D.lines.paneLabel * u,
            color: skin.ink,
          }]}
        >
          {t("View all", "Xem tất cả")}
        </Text>
      </Pressable>
      <ReceiptWrite frames={f} skin={skin} machine={machine} />
      {/* The line itself is drawn inside an SVG, which a screen reader cannot
          enter: this empty view over it carries what the line says. */}
      <View
        accessible
        accessibilityLabel={spoken}
        pointerEvents="none"
        style={[styles.absolute, line]}
      />
    </View>
  );
}

// Native text remains at its final position and size while a circular clip
// moves across it. The complementary clip removes old ink at precisely the
// same edge; painting an opaque disc would flatten the glass underneath.
const AnimatedCircle = Animated.createAnimatedComponent(Circle);
const AnimatedPath = Animated.createAnimatedComponent(Path);

function ReceiptWrite({ frames: f, skin, machine }: {
  frames: AuraFrames;
  skin: AuraSkin;
  machine: AuraMachine;
}) {
  const id = useId().replace(/:/g, "");
  const { receiptClock, receiptX, reduced, state } = machine;
  const { u, size } = f;
  const writing = state.previous !== null;
  const incoming = useAnimatedProps(() => ({
    cx: receiptX.get() * u,
    cy: AURA_LAND_Y * u,
    r: Math.max(0, receiptClock.get() * auraFlow.send.rippleSpeed / 1000) * u,
  }));
  const outgoing = useAnimatedProps(() => {
    const x = receiptX.get() * u;
    const y = AURA_LAND_Y * u;
    const r = Math.max(0.001, receiptClock.get() * auraFlow.send.rippleSpeed / 1000) * u;
    return { d: `M0 0H${size}V${size}H0Z M${x - r} ${y}a${r} ${r} 0 1 0 ${r * 2} 0a${r} ${r} 0 1 0 ${-r * 2} 0Z` };
  });
  const oldFade = useAnimatedStyle(() => ({
    opacity: reduced ? 1 - Math.min(1, receiptClock.get() / auraFlow.views.receiptFade) : 1,
  }));
  const newFade = useAnimatedStyle(() => ({
    opacity: reduced && writing ? Math.min(1, receiptClock.get() / auraFlow.views.receiptFade) : 1,
  }));
  const square = { width: size, height: size };
  const clipped = writing && !reduced;
  // The incoming line keeps its native image through the last frame. Only
  // its clip and the outgoing ink retire when the write is finished.
  return (
    <Svg pointerEvents="none" accessible={false} width={size} height={size} style={StyleSheet.absoluteFill}>
      <Defs>
        <ClipPath id={`${id}-new`}><AnimatedCircle animatedProps={incoming} /></ClipPath>
        <ClipPath id={`${id}-old`}><AnimatedPath animatedProps={outgoing} clipRule="evenodd" fillRule="evenodd" /></ClipPath>
      </Defs>
      {state.previous && <ForeignObject
        key="previous"
        x={0}
        y={0}
        width={size}
        height={size}
        clipPath={clipped ? `url(#${id}-old)` : undefined}
        clipRule="evenodd"
      >
        <Animated.View collapsable={false} style={[square, oldFade]}>
          <Receipt frames={f} skin={skin} receipt={state.previous} hidden />
        </Animated.View>
      </ForeignObject>}
      <ForeignObject
        key="current"
        x={0}
        y={0}
        width={size}
        height={size}
        clipPath={clipped ? `url(#${id}-new)` : undefined}
      >
        <Animated.View collapsable={false} style={[square, newFade]}>
          <Receipt key="current" frames={f} skin={skin} receipt={state.receipt} hidden={writing} />
        </Animated.View>
      </ForeignObject>
    </Svg>
  );
}

function Receipt({ frames: f, skin, receipt, hidden = false }: {
  frames: AuraFrames;
  skin: AuraSkin;
  receipt: AuraReceipt;
  hidden?: boolean;
}) {
  const { t } = usePreferences();
  const pearlId = `aura-pearl-${useId().replace(/:/g, "")}`;
  const { u } = f;
  const middle = f.size / 2;
  const mark = {
    left: (D.paneMark.cx - D.paneMark.size / 2) * u,
    top: (D.paneMark.cy - D.paneMark.size / 2) * u,
    width: D.paneMark.size * u,
    height: D.paneMark.size * u,
  };
  const title = {
    left: D.paneTitle.x * u,
    width: middle - D.paneTitle.x * u,
    ...centeredLine(D.paneTitle.cy, D.lines.title, u),
  };
  const note = {
    left: D.paneNote.x * u,
    width: middle - D.paneNote.x * u,
    ...centeredLine(D.paneNote.cy, D.lines.note, u),
  };
  const amount = {
    left: middle,
    width: D.paneAmount.right * u - middle,
    ...centeredLine(D.paneAmount.cy, D.lines.amount, u),
  };

  return (
    <View pointerEvents="none" accessibilityElementsHidden={hidden} importantForAccessibility={hidden ? "no-hide-descendants" : "auto"} style={StyleSheet.absoluteFill}>
      <View
        pointerEvents="none"
        accessible={false}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[styles.absolute, mark]}
      >
        {receipt.friend >= 0 ? (
          <Image source={FRIENDS[receipt.friend].image} style={{ width: "100%", height: "100%", borderRadius: D.paneMark.size * u / 2 }} />
        ) : <Svg width="100%" height="100%" viewBox="0 0 36 36" accessible={false}>
          <Defs>
            <LinearGradient id={pearlId} x1="0%" y1="0%" x2="80%" y2="100%">
              <Stop offset="0" stopColor={skin.pearlHigh} />
              <Stop offset="1" stopColor={skin.pearlLow} />
            </LinearGradient>
          </Defs>
          <Circle cx={18} cy={18} r={18} fill={`url(#${pearlId})`} />
          <G transform="translate(6 6)">
            <Path
              d={AURA_ICONS.orbit}
              fill="none"
              stroke={skin.heroMid}
              strokeWidth={D.actions.stroke}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </G>
        </Svg>}
      </View>
      <Text
        allowFontScaling={false}
        numberOfLines={1}
        style={[styles.text, styles.title, title, {
          fontSize: D.paneTitle.size * u,
          lineHeight: D.lines.title * u,
          color: skin.ink,
        }]}
      >
        {receipt.friend < 0 ? LAST.title : FRIENDS[receipt.friend].name}
      </Text>
      <Text
        allowFontScaling={false}
        numberOfLines={1}
        style={[styles.text, note, {
          fontSize: D.paneNote.size * u,
          lineHeight: D.lines.note * u,
          color: skin.sub,
        }]}
      >
        {receipt.friend < 0 ? `${LAST.date} · ${LAST.time}` : t("Just now", "Vừa xong")}
      </Text>
      <Text
        allowFontScaling={false}
        numberOfLines={1}
        style={[styles.text, styles.figures, amount, {
          fontSize: D.paneAmount.size * u,
          lineHeight: D.lines.amount * u,
          color: skin.ink,
        }]}
      >
        {`${receipt.friend < 0 ? "" : "−"}$${formatAuraCents(receipt.cents)}`}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  absolute: { position: "absolute" },
  text: {
    position: "absolute",
    fontFamily: systemFace,
    includeFontPadding: false,
    letterSpacing: D.tight,
  },
  viewAll: {
    fontFamily: systemFace,
    includeFontPadding: false,
    letterSpacing: D.tight,
    opacity: D.details.viewAllOpacity,
    textAlign: "right",
    textDecorationLine: "underline",
  },
  title: { fontWeight: "600" },
  figures: {
    fontVariant: ["tabular-nums"],
    fontWeight: "600",
    textAlign: "right",
  },
});
