import { useState } from "react";
import { StyleSheet, View, useWindowDimensions } from "react-native";
import { StatusBar } from "expo-status-bar";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { usePreferences } from "./i18n";
import { ReferralCard } from "./referral-card";
import { referralSize as S, referralTone } from "./referral-palette";
import { Hint } from "./ui";

// The block has the whole screen: a black page, light status bar, the card a
// little above the middle of the safe area and one quiet line under it. The
// card plays by itself until it is touched.
export default function ReferralRewardCardDemo() {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const { t } = usePreferences();
  const [verticalSnap, setVerticalSnap] = useState(0);
  // An even width keeps the lens centre on a whole point.
  const card = Math.floor(Math.min(width - S.cardMargin * 2, S.cardMax) / 2) * 2;
  return (
    <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <StatusBar style="light" />
      <View style={styles.stage}>
        <View
          style={{ width: "100%", transform: [{ translateY: verticalSnap }] }}
          onLayout={(e) => setVerticalSnap(Math.round(e.nativeEvent.layout.y) - e.nativeEvent.layout.y)}
        >
          {/* An odd screen width leaves the extra point on the trailing side. */}
          <View style={{ marginLeft: Math.floor((width - card) / 2) }}>
            <ReferralCard width={card} autoPlay />
          </View>
          <View style={styles.hint}>
            <Hint>{t("Drag the row to look at a friend.", "Kéo hàng ảnh để xem một người bạn.")}</Hint>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: referralTone.page },
  // The stage's bottom padding lifts the card above the centre.
  stage: { flex: 1, alignItems: "center", justifyContent: "center", paddingBottom: 48 },
  hint: { marginTop: 24, paddingHorizontal: 24 },
});
