import { StyleSheet, View, useWindowDimensions } from "react-native";
import { StatusBar } from "expo-status-bar";
import { useFonts } from "expo-font";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { EmberCharge } from "./ember-charge";
import { E, emberTone } from "./ember-theme";
import { usePreferences } from "./i18n";
import { Hint } from "./ui";

// The block has the whole screen: a black page, light status bar, the widget
// in the middle of the safe area and one line under it saying what to do.
export default function EmberChargeWidgetDemo() {
  const { width } = useWindowDimensions();
  const scale = Math.min(1, (width - E.pageMargin * 2) / E.size);
  const insets = useSafeAreaInsets();
  const { t } = usePreferences();
  // The card waits for its typeface: numbers that swap faces mid-roll would
  // be the first thing anyone saw. If loading fails the system face stands in.
  const [loaded, failed] = useFonts({
    Manrope_400Regular: require("./assets/Manrope_400Regular.ttf"),
    Manrope_600SemiBold: require("./assets/Manrope_600SemiBold.ttf"),
  });
  if (!loaded && !failed) return <View style={styles.root} />;
  return (
    <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <StatusBar style="light" />
      <EmberCharge autoPlay />
      <Hint color={emberTone.sub} style={{ marginTop: E.space.corner * scale }}>
        {t("Hold to charge. Let go early and it cools.", "Giữ để nạp. Thả sớm thì nguội lại.")}
      </Hint>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: emberTone.page },
});
