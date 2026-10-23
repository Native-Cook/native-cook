import { StyleSheet, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { FlareNotes } from "./flare-notes";
import { flareTone } from "./flare-palette";

// The block has the whole screen: the reference's cool gray page, dark status
// bar, and the notes laid out inside the safe area.
export default function FlareNotificationsDemo() {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <StatusBar style="dark" />
      <View style={styles.root}>
        <FlareNotes />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: flareTone.page },
});
