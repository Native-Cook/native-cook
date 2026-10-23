import { Platform, StyleSheet } from "react-native";
import { tactile } from "./tactile-theme";

// Native text inside the artwork uses the platform system face, as in the
// reference, rather than the app's display font.
export const systemFace = Platform.OS === "ios" ? "System" : "sans-serif";

export const artText = StyleSheet.create({
  label: {
    position: "absolute",
    color: tactile.text,
    fontFamily: systemFace,
    fontWeight: "400",
  },
  number: {
    padding: 0,
    margin: 0,
    textAlign: "right",
    fontFamily: systemFace,
    fontWeight: "500",
    fontVariant: ["tabular-nums"],
  },
});
