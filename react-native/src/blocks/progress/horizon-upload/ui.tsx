import type { ReactNode } from "react";
import { Platform, StyleSheet, Text, View, type StyleProp, type ViewStyle } from "react-native";
import { useFonts } from "expo-font";
import { horizonSize as S } from "./horizon-palette";
import { useReducedMotion } from "react-native-reanimated";

export const systemFace = Platform.OS === "ios" ? "System" : "sans-serif";

// The block's type, as approved on the device: the file's name and the
// percentage are the platform's own face at regular weight, and only the
// status label is Manrope. An earlier version asked for Manrope everywhere,
// but the name and the number were typeset before the font had loaded and
// kept the system face, and that is the look that was signed off.
export const horizonFace = { label: "Manrope-Medium" };
export const horizonCopy = { label: "#AEB6CC", size: "#8E97AE" };

export function useHorizonFonts() {
  const [loaded] = useFonts({
    "Manrope-Medium": require("./assets/fonts/Manrope_500Medium.ttf"),
  });
  return loaded;
}

// Below 272 pt the shared file/copy positions cannot leave 96 pt for the
// filename. Shrink only the file artwork, keeping its vertical centre and
// a 12 pt gap; the scene's card and well still use the unmodified contract.
export function horizonContentLayout(width: number) {
  const copyLeft = Math.min(S.copyLeft, width - S.pad - 96);
  const fileScale = Math.min(1, (copyLeft - S.pad - 12) / S.fileWidth);
  return {
    copyLeft, copyWidth: width - S.pad - copyLeft, fileScale,
    file: { x: S.pad, y: S.pad + S.fileHeight * (1 - fileScale) / 2,
      width: S.fileWidth * fileScale, height: S.fileHeight * fileScale },
  };
}

export function useMotionPrefs() {
  const reduced = useReducedMotion();
  return { reduced, haptics: true };
}

export function Hint({ children, style, color }: { children: ReactNode; style?: StyleProp<ViewStyle>; color: string }) {
  return (
    <View pointerEvents="none" style={[styles.hintRow, style]}>
      <Text style={[styles.hint, { color }]} maxFontSizeMultiplier={1.3}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  hintRow: { position: "absolute", left: 24, right: 24, alignItems: "center" },
  hint: { fontFamily: systemFace, fontSize: 13, lineHeight: 18, textAlign: "center" },
});
