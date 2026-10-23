export const WORD_CHOICES = [['i', 'we'], ['feel', 'felt'], ['so', 'very', 'a bit']];
export const FAN_ORDER = [5, 2, 0, 3, 4, 1];
export const INK = { dark: '#0B0B0F', light: '#FFFFFF' };

function linear(hex: string): number[] {
  const rgb: number[] = [];
  for (let i = 1; i < 7; i += 2) {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    rgb.push(c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  }
  return rgb;
}

export const MOODS = [
  { name: 'joy', accessibilityName: 'happy', hex: '#B6FF3B' },
  { name: 'love', accessibilityName: 'in love', hex: '#FF4FA3' },
  { name: 'wow', accessibilityName: 'amazed', hex: '#FFB21F' },
  { name: 'calm', accessibilityName: 'calm', hex: '#2FE6D2' },
  { name: 'low', accessibilityName: 'down', hex: '#8E6BFF' },
  { name: 'mad', accessibilityName: 'angry', hex: '#FF4A2E' },
].map(mood => ({ ...mood, linear: linear(mood.hex) }));
// Only this numeric table is captured by simulation worklets.
export const MOOD_RGB = MOODS.map(mood => mood.linear);
export type KeycapRect = { x: number; y: number; width: number; height: number };
export type KeycapLayout = {
  width: number; height: number; u: number; K: number; P: number; M: number;
  keys: KeycapRect[]; hits: KeycapRect[]; minis: { x: number; y: number }[];
};

export function keycapLayout(width: number, height: number): KeycapLayout {
  'worklet';
  const u = Math.min(width / 402, height / 874);
  const K = 134 * u;
  const P = 142 * u;
  const y0 = (height - 560 * u) / 2 - 20 * u;
  const keys: KeycapRect[] = [];
  const hits: KeycapRect[] = [];
  const minis: { x: number; y: number }[] = [];
  for (let i = 0; i < 4; i++) {
    const x = width / 2 - K / 2;
    const y = y0 + i * P;
    keys.push({ x, y, width: K, height: K });
    hits.push({ x: x - 12 * u, y: y - 4 * u, width: K + 24 * u, height: P });
  }
  for (let i = 0; i < 6; i++) {
    const angle = (155 - 26 * i) * Math.PI / 180;
    minis.push({ x: width / 2 + P * Math.cos(angle), y: y0 + 3 * P + K / 2 + P * Math.sin(angle) });
  }
  return { width, height, u, K, P, M: 52 * u, keys, hits, minis };
}

/** Renderer passes PixelRatio.get(); data modules remain usable without a native runtime. */
export function dotPitch(key: number, u: number, pixelRatio: number): number {
  'worklet';
  return Math.round((key === 3 ? 16 / 3 : 13 / 3) * u * pixelRatio) / pixelRatio;
}
