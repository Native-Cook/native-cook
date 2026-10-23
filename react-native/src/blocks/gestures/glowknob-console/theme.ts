import type { TextStyle } from "react-native";
import { Easing } from "react-native-reanimated";

// Tokens copied verbatim from Touchline (src/theme/index.ts).
export const space = {
  xxs: 4,
  xs: 8,
  sm: 12,
  md: 16,
  edge: 20,
  lg: 24,
  xl: 32,
  xxl: 48,
};
export function screenSpacing(width: number) {
  return {
    edge: width < 375 ? space.md : space.edge,
    group: space.xs,
    section: space.xl,
    card: space.edge,
  };
}
export const motion = {
  press: 120,
  fast: 150,
  base: 240,
  hero: 900,
  easeOut: Easing.bezier(0.23, 1, 0.32, 1),
  settle: { duration: 400, dampingRatio: 1 } as const,
};
// The two text roles the console uses (Touchline `type.label` / `type.caption`).
export const type = {
  label: { fontFamily: "Manrope_600SemiBold", fontSize: 13, lineHeight: 19 },
  caption: { fontFamily: "Manrope_400Regular", fontSize: 12, lineHeight: 18 },
} satisfies Record<string, TextStyle>;
export const ink = "#000000";
