import { Easing, type WithSpringConfig } from 'react-native-reanimated';

/**
 * Every position in this block is in points on a 402 × 874 canvas, the iPhone 17/18 Pro
 * screen the reference video was captured on. Other screens scale the canvas to fit.
 * Values match the SwiftUI block (`TradingBotsOnboarding/*.swift`).
 */
export const CANVAS = { width: 402, height: 874 } as const;

export const palette = {
  ink: '#fbfbfb',
  caption: '#8a8a8c',
  muted: '#7a7a7a',
  ctaText: '#3b3b3b',
} as const;

/** `#rrggbb` plus an opacity, like `TradingBotsPalette.hex(_, _)`. */
export function hex(value: string, opacity = 1) {
  const n = parseInt(value.replace('#', ''), 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${opacity})`;
}

export const white = (opacity: number) => `rgba(255, 255, 255, ${opacity})`;
export const black = (opacity: number) => `rgba(0, 0, 0, ${opacity})`;

/** SwiftUI `.spring(response:dampingFraction:)` as a physical spring with mass 1. */
export function spring(response: number, dampingFraction: number): WithSpringConfig {
  const omega = (2 * Math.PI) / response;
  return { mass: 1, stiffness: omega * omega, damping: 2 * dampingFraction * omega };
}

/** SwiftUI `.timingCurve(a, b, c, d, duration:)`. */
export const curve = (a: number, b: number, c: number, d: number) => Easing.bezier(a, b, c, d);

export const REVEAL_CURVE = curve(0.2, 0.7, 0.25, 1);
export const WIPE_CURVE = curve(0.3, 0.1, 0.3, 1);
export const SETTLE_CURVE = curve(0.4, 0, 0.2, 1);

/** SwiftUI's default `.easeOut` / `.easeInOut` curves. */
export const EASE_OUT = curve(0, 0, 0.58, 1);
export const EASE_IN_OUT = curve(0.42, 0, 0.58, 1);
