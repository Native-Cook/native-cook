// Flare — notification pills that keep a small fire at their trailing cap.
// The neutrals were sampled from the user's reference clip (4K frames); they
// lean violet (OKLCH h≈301, C≈0.005), which stops the dark pill reading as
// plain gray next to the magenta. The flames themselves are fitted blob
// animations (flare-blobs.ts), so they carry the reference's own colors.
//
// Pure data and math so node tests can import it without React Native.

import { BLOOM_KEYS, KILN_KEYS, TIDE_KEYS } from "./flare-blobs";

export type Flare = {
  id: string;
  title: string;
  line: [string, string];
  body: string;
  ink: string;
  sub: string;
  // The flame: fitted blob keyframes (see flare-blobs.ts, flare-shader.ts).
  blobs: readonly number[];
  // The cast shadow's color; the reference draws no rim stroke.
  shadow: string;
};

export const flareTone = {
  // The reference's page: a cool gray with the same violet lean.
  page: "#ECEDF4",
  // Hint and index text on that page (#737373 only reaches 4.1:1 there).
  pageHint: "#666569",
  dark: "#212023",
  mist: "#F6F5F8",
  white: "#FFFFFF",
  // On the dark pill: the reference's #A1A0A3 (6.2:1).
  subDark: "#A1A0A3",
  // On the light pills. The reference used #89898B, which only reaches 3.2:1
  // on #F6F5F8; this keeps the hue and clears 4.5:1.
  subLight: "#6B6A70",
  inkLight: "#0C0B0E",
  inkDark: "#FFFFFF",
};

export const FLARES: Flare[] = [
  {
    id: "kiln",
    title: "KILN.*",
    line: ["You made a sale", "Bạn vừa có đơn hàng"],
    body: flareTone.dark,
    ink: flareTone.inkDark,
    sub: flareTone.subDark,
    blobs: KILN_KEYS,
    shadow: "rgba(22,20,34,0.30)",
  },
  {
    id: "tide",
    title: "TIDE*",
    line: ["$2,000 commission", "Hoa hồng $2.000"],
    body: flareTone.mist,
    ink: flareTone.inkLight,
    sub: flareTone.subLight,
    blobs: TIDE_KEYS,
    shadow: "rgba(22,20,34,0.11)",
  },
  {
    id: "bloom",
    title: "BLOOM.*",
    line: ["Deployed in 24h", "Đã triển khai trong 24h"],
    body: flareTone.white,
    ink: flareTone.inkLight,
    sub: flareTone.subLight,
    blobs: BLOOM_KEYS,
    shadow: "rgba(22,20,34,0.10)",
  },
];

export function hexToRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

// The motion beats, in ms and in pill heights, shared by the component and
// the tests so the arrival choreography can't drift from what's documented.
export const flareMotion = {
  // Measured on the 4K frames: the pill is 3.66:1, pills sit 0.39 heights
  // apart, the copy starts 0.35 heights in and the title's cap height is
  // 0.2 heights.
  height: 84,
  aspect: 3.66,
  gap: 32,
  inset: 29,
  stagger: 70,
  enter: 320,
  igniteDelay: 60,
  flash: 160,
  settle: 700,
  holdAfter: 260,
  stoke: 1300,
  cool: 650,
  relight: 380,
  // Read state: the flame is cut back to an ember reaching this far in from
  // the cap, in pill heights (the lit flame reaches ~2.1).
  ember: 0.75,
  // The status line crossfades when a note is read or relit.
  swap: 240,
  // "Deliver again": the old notes leave before the new ones arrive.
  leave: 180,
  // The cast shadow, fitted to the reference's falloff (pill heights 84):
  // it hangs 12 below, 6 in from each side, sigma 10, nothing above.
  shadowDrop: 12,
  shadowInset: 6,
  shadowBlur: 10,
};
