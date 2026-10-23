import { Text, type TextProps } from "react-native";
import { useReducedMotion } from "react-native-reanimated";
import { usePreferences } from "./i18n";
import { ink, type } from "./theme";

// Stand-ins for the two Touchline UI helpers the console uses.

/** System Reduce Motion; haptics are always on (no in-app preference). */
export function useMotionPrefs() {
  const reduced = useReducedMotion();
  return { reduced, haptics: true };
}

/** Manrope text in one of the console's two roles. */
export function Txt({
  variant = "caption",
  style,
  ...props
}: TextProps & { variant?: keyof typeof type }) {
  const { locale } = usePreferences();
  return (
    <Text
      {...props}
      accessibilityLanguage={locale === "vi" ? "vi-VN" : "en-US"}
      style={[type[variant], { color: ink }, style]}
    />
  );
}
