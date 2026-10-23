import { StyleSheet, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { useFonts } from "expo-font";
import { Showcase } from "./console";
import { tactile } from "./console/tactile-theme";

// The block has the whole screen: the console page, white, under a dark status
// bar. The gesture and safe-area roots the original app set up around it are
// the gallery's here.
//
// What it needs from the app, beyond its packages: a development build (Skia 3
// and WebGPU are not in Expo Go); `three`, `three/webgpu` and `three/tsl`
// resolved to one ESM build in metro.config.js; and the react-native-webgpu,
// expo-font, expo-audio and expo-asset plugins in app.json, with Android
// minSdkVersion 26. This repository's react-native/metro.config.js and
// app.json show each.
export default function GlowknobConsoleDemo() {
  const [fontsLoaded, fontError] = useFonts({
    Manrope_400Regular: require("./assets/Manrope_400Regular.ttf"),
    Manrope_600SemiBold: require("./assets/Manrope_600SemiBold.ttf"),
  });
  const ready = fontsLoaded || fontError !== null;
  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      {ready && <Showcase />}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tactile.page },
});
