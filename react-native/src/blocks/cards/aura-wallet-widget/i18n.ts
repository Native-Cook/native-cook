import { useCallback, useMemo } from "react";
import { useLocales } from "expo-localization";

// The device chooses the copy. Names and transaction details stay as written
// on the wallet; only its instructions and control labels change language.
export type Locale = "en" | "vi";

export function usePreferences() {
  const locales = useLocales();
  const locale: Locale = locales[0]?.languageCode === "vi" ? "vi" : "en";
  const t = useCallback(
    (english: string, vietnamese?: string) => locale === "vi" ? (vietnamese ?? english) : english,
    [locale],
  );
  return useMemo(() => ({ locale, t }), [locale, t]);
}

// Amounts stay in cents until this boundary. Build the fraction from its
// integer remainder, so a preview and a committed receipt share exact digits.
export function formatAuraCents(cents: number) {
  const absolute = Math.abs(cents);
  const whole = Math.floor(absolute / 100).toLocaleString("en-US");
  const fraction = String(absolute % 100).padStart(2, "0");
  return `${cents < 0 ? "−" : ""}${whole}.${fraction}`;
}
