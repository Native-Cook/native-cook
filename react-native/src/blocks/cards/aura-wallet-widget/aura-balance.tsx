import { useEffect } from "react";
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from "react-native-reanimated";
import { AuraChip } from "./aura-chip";
import { AuraPhase, auraFlow } from "./aura-flow";
import type { AuraMachine } from "./aura-machine";
import { Image, Pressable, StyleSheet, View } from "react-native";
import { NumericText } from "expo-content-transition";
import { AURA_ICONS } from "./aura-icons";
import { auraDesign as D, auraMotion, type AuraFrames, type AuraSkin } from "./aura-palette";
import { FRIENDS, OWNER } from "./aura-people";
import { formatAuraCents, usePreferences } from "./i18n";
import { AuraIcon, centeredLine, systemFace, touchInsets } from "./ui";

// The cushion carries a small identity and one large balance. Explicit line
// boxes keep their centres on the drawing, regardless of the chosen language.
export function AuraBalance({ frames: f, skin, machine }: {
  frames: AuraFrames;
  skin: AuraSkin;
  machine: AuraMachine;
}) {
  const { t } = usePreferences();
  const { u } = f;
  const formatted = formatAuraCents(machine.balanceCents);
  // The route is over when the drop lands, like the chip and Send, not when the bud has grown back.
  const arrived = machine.state.phase === AuraPhase.sending && machine.state.landed;
  const active = machine.state.phase !== AuraPhase.rest && machine.state.chosen >= 0 && !arrived;
  const person = FRIENDS[machine.state.chosen];
  const identity = useSharedValue(0);
  useEffect(() => {
    identity.set(withTiming(active ? 1 : 0, {
      duration: auraFlow.views.route.duration,
      easing: Easing.bezier(...auraMotion.easeOut),
      reduceMotion: ReduceMotion.Never,
    }));
  }, [active, identity]);
  const reduced = machine.reduced;
  const friendStyle = useAnimatedStyle(() => ({
    opacity: identity.get(),
    transform: [{ translateX: auraFlow.views.route.travel * f.u * (reduced ? (active ? 1 : 0) : identity.get()) }],
  }));
  const nameStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: auraFlow.views.route.travel * f.u * (reduced ? (active ? 1 : 0) : identity.get()) }],
  }));
  const [dollars, cents] = formatted.split(".");
  const face = {
    left: (D.owner.cx - D.owner.avatar / 2) * u,
    top: (D.owner.cy - D.owner.avatar / 2) * u,
    width: D.owner.avatar * u,
    height: D.owner.avatar * u,
  };
  const name = {
    left: D.owner.nameX * u,
    width: (D.info.cx - D.info.size / 2 - D.owner.nameX) * u,
    ...centeredLine(D.owner.cy, D.lines.owner, u),
  };
  const info = {
    left: (D.info.cx - D.info.size / 2) * u,
    top: (D.info.cy - D.info.size / 2) * u,
    width: D.info.size * u,
    height: D.info.size * u,
  };
  const balance = {
    left: f.hero.x,
    width: f.hero.w,
    ...centeredLine(D.balance.cy, D.lines.balance, u),
  };
  const balanceGlyphs = { width: f.hero.w, height: D.lines.balance * u };

  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <Pressable
        accessible={false}
        onPress={machine.dismiss}
        style={[styles.absolute, { left: f.hero.x, top: f.hero.y, width: f.hero.w, height: f.hero.h }]}
      />
      {person && <Animated.Image
        source={person.image}
        accessible={false}
        style={[styles.absolute, face, { borderRadius: D.owner.avatar * u / 2 }, friendStyle]}
      />}
      <Image
        source={OWNER.image}
        accessible={false}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[styles.absolute, face, { borderRadius: D.owner.avatar * u / 2 }]}
      />
      {/* This is a fixed drawing: type scales with its frame, not Dynamic Type.
          The page's hint remains scalable, and the balance has a spoken label. */}
      <Animated.Text
        pointerEvents="none"
        allowFontScaling={false}
        numberOfLines={1}
        style={[styles.text, name, nameStyle, {
          fontSize: D.owner.nameSize * u,
          lineHeight: D.lines.owner * u,
          color: skin.ink,
        }]}
      >
        {active ? `${OWNER.name} › ${person.name}` : OWNER.name}
      </Animated.Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={t("Account details", "Chi tiết tài khoản")}
        hitSlop={touchInsets(D.info.size * u)}
        onPress={() => {}}
        style={[styles.info, info, {
          borderRadius: D.info.size * u / 2,
          backgroundColor: skin.dotFill,
        }]}
      >
        <AuraIcon path={AURA_ICONS.info} side={D.info.size} u={u} color={skin.dotInk} />
      </Pressable>
      <AuraChip after={machine.preview} reduced={reduced} u={u} skin={skin} />
      <View
        accessible
        accessibilityLabel={t(
          `${machine.preview ? "Balance after" : "Your balance"}, ${dollars} dollars and ${cents} cents`,
          `${machine.preview ? "Số dư sau gửi" : "Số dư của bạn"}, ${dollars} đô la và ${cents} xu`,
        )}
        style={[styles.absolute, balance]}
      >
        <View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          {/* NumericText uses explicit point sizes and supports weight and kern.
              It has no allowFontScaling prop; its native glyphs are fixed-size. */}
          <NumericText
            value={`$ ${formatted}`}
            animated={!reduced}
            alignment="center"
            fontSize={D.balance.size * u}
            fontWeight="600"
            letterSpacing={D.balance.tracking * u}
            color={skin.ink}
            style={balanceGlyphs}
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  absolute: { position: "absolute" },
  text: {
    position: "absolute",
    fontFamily: systemFace,
    fontWeight: "400",
    includeFontPadding: false,
    opacity: D.details.ownerOpacity,
  },
  info: {
    position: "absolute",
    alignItems: "center",
    justifyContent: "center",
  },
});
