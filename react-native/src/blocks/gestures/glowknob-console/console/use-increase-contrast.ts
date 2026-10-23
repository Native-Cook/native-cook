import { useEffect, useState } from "react";
import { AccessibilityInfo, Platform } from "react-native";

// iOS "Increase Contrast" (darker system colors) or Android high-contrast
// text. Read once, then kept in sync with the system setting.
export function useIncreaseContrast() {
  const [on, setOn] = useState(false);
  useEffect(() => {
    let alive = true;
    const read =
      Platform.OS === "ios"
        ? AccessibilityInfo.isDarkerSystemColorsEnabled
        : AccessibilityInfo.isHighTextContrastEnabled;
    void read()
      .then((value) => alive && setOn(value))
      .catch(() => {});
    const sub = AccessibilityInfo.addEventListener(
      Platform.OS === "ios"
        ? "darkerSystemColorsChanged"
        : "highTextContrastChanged",
      (value: boolean) => setOn(value),
    );
    return () => {
      alive = false;
      sub.remove();
    };
  }, []);
  return on;
}
