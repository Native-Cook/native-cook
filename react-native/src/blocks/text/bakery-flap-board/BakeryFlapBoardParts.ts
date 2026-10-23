import { Skia } from '@shopify/react-native-skia';

// The characters on each flap drum, in the order the flaps fall. A cell only moves forward through
// this list, so a change from Y to B passes through Z, the digits and the marks first.
export const DRUM = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.'&-";

export const COLUMNS = 16;

const HOURS = ['TWELVE', 'ONE', 'TWO', 'THREE', 'FOUR', 'FIVE', 'SIX', 'SEVEN', 'EIGHT', 'NINE', 'TEN', 'ELEVEN'];
const MINUTES: Record<number, string> = {
  5: 'FIVE PAST',
  10: 'TEN PAST',
  15: 'QUARTER PAST',
  20: 'TWENTY PAST',
  25: 'TWENTY FIVE PAST',
  30: 'HALF PAST',
  35: 'TWENTY FIVE TO',
  40: 'TWENTY TO',
  45: 'QUARTER TO',
  50: 'TEN TO',
  55: 'FIVE TO',
};

/** The time in words to the nearest five minutes, on two lines of the board. */
export function timeInWords(hours: number, minutes: number): [string, string] {
  let m = Math.round(minutes / 5) * 5;
  let h = hours;
  if (m === 60) {
    m = 0;
    h += 1;
  }
  if (m === 0) return ['IT IS', `${HOURS[h % 12]} O'CLOCK`];
  return [MINUTES[m], HOURS[(m > 30 ? h + 1 : h) % 12]];
}

/** `text` centred on a row of COLUMNS flaps. */
export function centred(text: string) {
  const pad = Math.max(0, Math.floor((COLUMNS - text.length) / 2));
  return (' '.repeat(pad) + text).padEnd(COLUMNS, ' ').slice(0, COLUMNS);
}

/** A menu line: the name on the left, the price on the right. */
export function menuLine(name: string, price: string) {
  'worklet';
  let line = name;
  while (line.length < COLUMNS - price.length) line += ' ';
  return (line + price).slice(0, COLUMNS);
}

// Dark walnut with long, slightly wavy grain and a fine fibre on top, for the board's case.
export const WOOD = Skia.RuntimeEffect.Make(`
uniform float2 size;

float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
             mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

half4 main(float2 xy) {
  float2 p = xy / size.x;
  float wave = noise(float2(p.x * 2.2, p.y * 9.0)) * 0.9 + noise(float2(p.x * 5.0, p.y * 30.0)) * 0.35;
  float rings = 0.5 + 0.5 * sin(p.y * 70.0 + wave * 7.0);
  float fibre = noise(float2(p.x * 40.0, p.y * 700.0));
  float3 dark = float3(0.105, 0.062, 0.038);
  float3 light = float3(0.235, 0.137, 0.078);
  float3 col = mix(dark, light, rings * 0.55 + wave * 0.3);
  col *= 0.86 + fibre * 0.22;
  // A soft light from the top left, falling off towards the bottom.
  col *= 1.08 - 0.32 * clamp(xy.y / size.y, 0.0, 1.0);
  return half4(half3(col), 1.0);
}
`)!;
