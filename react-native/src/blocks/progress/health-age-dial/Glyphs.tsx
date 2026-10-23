import Svg, { Circle, G, Path, Rect } from 'react-native-svg';

import { colors } from './theme';

/** One rounded wedge of the eight-petal mark, pointing at the centre, at 0° (12 o'clock). */
function petalPath(rIn: number, rOut: number, halfDeg: number): string {
  const a = (halfDeg * Math.PI) / 180;
  const x1 = Math.sin(-a) * rOut;
  const y1 = -Math.cos(-a) * rOut;
  const x2 = Math.sin(a) * rOut;
  const y2 = -Math.cos(a) * rOut;
  const xi1 = Math.sin(-a * 0.55) * rIn;
  const yi1 = -Math.cos(-a * 0.55) * rIn;
  const xi2 = Math.sin(a * 0.55) * rIn;
  const yi2 = -Math.cos(a * 0.55) * rIn;
  return `M${xi1.toFixed(3)} ${yi1.toFixed(3)} L${x1.toFixed(3)} ${y1.toFixed(3)} A${rOut} ${rOut} 0 0 1 ${x2.toFixed(3)} ${y2.toFixed(3)} L${xi2.toFixed(3)} ${yi2.toFixed(3)} Z`;
}

const PETALS = { chip: petalPath(5.4, 14.6, 13.5), tab: petalPath(4.2, 14.8, 15.5) } as const;

/** The Longevity mark: eight rounded wedges round an open centre (36 × 36 viewBox). */
export function PetalMark({
  size,
  fills,
  shape = 'chip',
}: {
  size: number;
  shape?: keyof typeof PETALS;
  /** Eight colours clockwise from 12 o'clock; null leaves the slot empty. */
  fills: readonly (string | null)[];
}) {
  return (
    <Svg width={size} height={size} viewBox="-18 -18 36 36">
      {fills.map((fill, i) =>
        fill ? (
          <G key={i} rotation={i * 45}>
            <Path d={PETALS[shape]} fill={fill} stroke={fill} strokeWidth={2.2} strokeLinejoin="round" />
          </G>
        ) : null,
      )}
    </Svg>
  );
}

export const OVERVIEW_FILLS = ['#43B93A', '#EE5A2C', '#E5285B', '#CF2DC0', null, '#6A3FD0', '#1E7DE2', '#18AE9C'] as const;

export function HeartGlyph({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path
        d="M12 21.2c-.3 0-.6-.1-.8-.3C6.2 16.8 2.2 13.4 2.2 8.9 2.2 5.9 4.5 3.6 7.4 3.6c1.9 0 3.5 1 4.6 2.6 1.1-1.6 2.7-2.6 4.6-2.6 2.9 0 5.2 2.3 5.2 5.3 0 4.5-4 7.9-9 12-.2.2-.5.3-.8.3Z"
        fill={color}
      />
    </Svg>
  );
}

export function GridGlyph({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Rect x="2.5" y="2.5" width="8.6" height="8.6" rx="2" fill={color} />
      <Rect x="12.9" y="2.5" width="8.6" height="8.6" rx="2" fill={color} />
      <Rect x="2.5" y="12.9" width="8.6" height="8.6" rx="2" fill={color} />
      <Rect x="12.9" y="12.9" width="8.6" height="8.6" rx="2" fill={color} />
    </Svg>
  );
}

/** A double bed, side on (Sleep Score). */
export function BedGlyph({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M3.6 6.2c0-1 .8-1.8 1.8-1.8h13.2c1 0 1.8.8 1.8 1.8v4H18.6V9.1c0-.9-.7-1.6-1.6-1.6h-2.2c-.9 0-1.6.7-1.6 1.6v1.1h-2.4V9.1c0-.9-.7-1.6-1.6-1.6H7c-.9 0-1.6.7-1.6 1.6v1.1H3.6v-4Z" fill={color} />
      <Path d="M2 12.6c0-.9.7-1.6 1.6-1.6h16.8c.9 0 1.6.7 1.6 1.6v4.6H2v-4.6Z" fill={color} />
      <Rect x="2" y="17.2" width="2.4" height="2.6" rx="0.8" fill={color} />
      <Rect x="19.6" y="17.2" width="2.4" height="2.6" rx="0.8" fill={color} />
    </Svg>
  );
}

/** A rounded square with a pulse trace (Heart Rate Variability). */
export function PulseGlyph({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Rect x="2" y="4" width="20" height="16" rx="4" fill={color} />
      <Path
        d="M4.6 12.4h3.6l1.5-2.6 2 5.4 1.9-6.4 1.6 3.6h4.2"
        fill="none"
        stroke={colors.white}
        strokeWidth={1.9}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

/** The grey info button above the dial: a slab-serif "i" in a disc. */
export function InfoGlyph({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 25 25">
      <Circle cx="12.5" cy="12.5" r="12.5" fill={colors.info} />
      <Circle cx="12.6" cy="6.9" r="1.55" fill={colors.infoGlyph} />
      <Path
        d="M10.2 10h3.5v6.9h1.6v1.6H9.8v-1.6h1.7v-5.3h-1.3V10Z"
        fill={colors.infoGlyph}
      />
    </Svg>
  );
}
