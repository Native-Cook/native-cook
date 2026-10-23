import { useState } from "react";
import { StyleSheet, View, useWindowDimensions } from "react-native";
import { StatusBar } from "expo-status-bar";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { auraDesign as D, auraSkins, auraWidth } from "./aura-palette";
import { AuraPage } from "./aura-shell";
import { AuraWidget } from "./aura-widget";
import type { AuraHint } from "./aura-machine";
import { usePreferences } from "./i18n";
import { Hint } from "./ui";

// The whole page belongs to the wallet. Its light and its widget share one
// origin, calculated before the first frame so the glow never jumps into place.
export default function AuraWalletWidgetDemo() {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const { t } = usePreferences();
  const [instruction, setInstruction] = useState<AuraHint | null>(null);
  const skin = auraSkins.violet;
  const cardWidth = auraWidth(width);
  const cardX = Math.floor((width - cardWidth) / 2);
  const safeHeight = height - insets.top - insets.bottom;
  const cardY = Math.round(insets.top + (safeHeight - cardWidth) / 2 - D.page.lift);
  const card = { left: cardX, top: cardY };
  const hint = {
    left: cardX,
    top: cardY + cardWidth + D.page.hintGap,
    width: cardWidth,
  };

  return (
    <View style={[styles.page, { backgroundColor: skin.page }]}>
      <StatusBar style={skin.bar} />
      <AuraPage
        width={width}
        height={height}
        cardX={cardX}
        cardY={cardY}
        cardWidth={cardWidth}
        skin={skin}
      />
      <View style={[styles.absolute, card]}>
        <AuraWidget width={cardWidth} skin={skin} autoPlay={false} onHint={setInstruction} />
      </View>
      <View style={[styles.absolute, hint]}>
        <Hint color={skin.hint} kind={instruction?.kind ?? "rest"}>
          {instruction?.text ?? t("Pull a friend down to send.", "Kéo một người bạn xuống để gửi.")}
        </Hint>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1 },
  absolute: { position: "absolute" },
});
