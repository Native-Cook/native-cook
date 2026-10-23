import { useCallback, useMemo } from "react";
import { useLocales } from "expo-localization";

export type Locale = "en" | "vi";

/** English unless the device's preferred language is Vietnamese. */
export function usePreferences() {
  const locales = useLocales();
  const locale: Locale = locales[0]?.languageCode === "vi" ? "vi" : "en";
  const t = useCallback(
    (english: string, vietnamese?: string) =>
      locale === "vi" ? (vietnamese ?? english) : english,
    [locale],
  );
  return useMemo(() => ({ locale, t }), [locale, t]);
}
