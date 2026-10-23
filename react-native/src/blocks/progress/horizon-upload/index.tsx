import { StyleSheet, View, useWindowDimensions } from "react-native";
import { StatusBar } from "expo-status-bar";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { HorizonCard } from "./horizon-card";
import { HorizonScene } from "./horizon-scene";
import { horizonSize as S, horizonTone } from "./horizon-palette";
import { useHorizonUpload } from "./horizon-upload";
import { usePreferences } from "./i18n";
import { Hint, useMotionPrefs } from "./ui";

export default function HorizonUploadDemo() {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const { reduced } = useMotionPrefs();
  const { t } = usePreferences();
  const upload = useHorizonUpload({ autoPlay: true });
  const cardWidth = Math.floor(Math.min(width - 2 * S.cardMargin, S.cardMax) / 2) * 2;
  const card = {
    width: cardWidth,
    x: Math.floor((width - cardWidth) / 2),
    y: Math.round(insets.top + (height - insets.top - insets.bottom - S.cardHeight) * S.cardRest),
  };
  return (
    <View style={styles.root} onTouchStart={upload.touch}>
      <StatusBar style="light" />
      <HorizonScene width={width} height={height} card={card} fx={upload.fx} animated={!reduced} />
      <View style={[styles.card, { left: card.x, top: card.y }]}>
        <HorizonCard width={card.width} upload={upload} reduced={reduced} />
      </View>
      <Hint color={horizonTone.hint} style={{ bottom: insets.bottom + 28 }}>
        {t("Drag the card. Tap the bar to pause.", "Kéo thẻ thử xem. Chạm thanh tiến độ để tạm dừng.")}
      </Hint>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: horizonTone.page },
  card: { position: "absolute" },
});
