import { BlurMask, Canvas, Group, Path as SkiaPath } from '@shopify/react-native-skia';
import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import Svg, { Path, Rect } from 'react-native-svg';

export const MARK_SIZE = 30;
// Blur and strength of the mark's glow, and the room its canvas keeps for it on every side.
const MARK_GLOW = 9;
const MARK_GLOW_OPACITY = 0.7;
const MARK_BLEED = 28;

// An svg is this much larger than its icon on every side, so round caps on the edge are not cut.
const PAD = 1;

const WAVEFORM_BARS = [6, 12, 16, 9, 5];
// Up arrow in the 30 × 30 send button.
const ARROW = 'M15 21L15 9.5M10 14.25L15 9.25L20 14.25';
// Chevron pointing down in an 8 × 5 box.
const CHEVRON = 'M0.5 0.75L4 4.25L7.5 0.75';
// Check mark in a 12 × 10 box.
const CHECK = 'M1 5.4L4.4 8.8L11 1.2';
// The chips' outline icons in a 13 × 13 box: a sparkle, a pencil, an open book and code brackets.
const CHIP_ICONS = [
  'M6.5 1Q7.2 5.8 12 6.5Q7.2 7.2 6.5 12Q5.8 7.2 1 6.5Q5.8 5.8 6.5 1Z',
  'M1.8 11.2L2.5 8.3L8.9 1.9L11.1 4.1L4.7 10.5ZM7.4 3.4L9.6 5.6',
  'M6.5 3.4Q4.4 2.1 1.5 2.6L1.5 10.2Q4.4 9.7 6.5 11Q8.6 9.7 11.5 10.2L11.5 2.6Q8.6 2.1 6.5 3.4L6.5 11',
  'M4.4 3.2L1.2 6.5L4.4 9.8M8.6 3.2L11.8 6.5L8.6 9.8',
];

const ROUND = { fill: 'none', strokeLinecap: 'round', strokeLinejoin: 'round' } as const;

/**
 * A four-pointed star in the mark's box. `pinch` pulls the sides towards the centre: 0 is a
 * needle-thin star, 0.5 a diamond.
 */
function star(radius: number, turn: number, pinch: number) {
  const r = radius * MARK_SIZE;
  const point = (angle: number, distance: number) =>
    `${(MARK_SIZE / 2 + Math.cos(angle) * distance).toFixed(3)} ${(MARK_SIZE / 2 + Math.sin(angle) * distance).toFixed(3)}`;
  let path = `M${point(turn, r)}`;
  for (let step = 1; step <= 4; step++) {
    const angle = turn + (step * Math.PI) / 2;
    path += `Q${point(angle - Math.PI / 4, r * pinch * Math.SQRT2)} ${point(angle, r)}`;
  }
  return `${path}Z`;
}

// A four-pointed star with a smaller one turned 45° behind it, like a compass rose.
const MARK = star(0.5, 0, 0.1) + star(0.3, Math.PI / 4, 0.16);

/** An open ring in a `size` box: `share` of the circle, clockwise from the top. */
function arc(size: number, share: number) {
  const r = size / 2;
  const end = (share - 0.25) * Math.PI * 2;
  return `M${r} 0A${r} ${r} 0 ${share > 0.5 ? 1 : 0} 1 ${(r + Math.cos(end) * r).toFixed(3)} ${(r + Math.sin(end) * r).toFixed(3)}`;
}

function Icon({ width, height, children }: { width: number; height: number; children: ReactNode }) {
  return (
    <View style={{ width, height }}>
      <Svg
        style={styles.svg}
        width={width + PAD * 2}
        height={height + PAD * 2}
        viewBox={`${-PAD} ${-PAD} ${width + PAD * 2} ${height + PAD * 2}`}
      >
        {children}
      </Svg>
    </View>
  );
}

/** The assistant's mark, with a glow of its own colour around it. */
export function Mark({ color }: { color: string }) {
  return (
    <View style={styles.mark}>
      <Canvas style={styles.markCanvas}>
        <Group transform={[{ translateX: MARK_BLEED }, { translateY: MARK_BLEED }]}>
          <SkiaPath path={MARK} color={color} opacity={MARK_GLOW_OPACITY}>
            <BlurMask blur={MARK_GLOW} style="normal" />
          </SkiaPath>
          <SkiaPath path={MARK} color={color} />
        </Group>
      </Canvas>
    </View>
  );
}

/** Five bars of a voice waveform in an 18 × 18 box. */
export function Waveform({ color }: { color: string }) {
  return (
    <Icon width={18} height={18}>
      {WAVEFORM_BARS.map((height, index) => (
        <Rect key={index} x={1.1 + index * 3.5} y={9 - height / 2} width={1.8} height={height} rx={0.9} fill={color} />
      ))}
    </Icon>
  );
}

export function Arrow({ color }: { color: string }) {
  return (
    <Icon width={30} height={30}>
      <Path d={ARROW} stroke={color} strokeWidth={2} {...ROUND} />
    </Icon>
  );
}

export function Chevron({ color }: { color: string }) {
  return (
    <Icon width={8} height={5}>
      <Path d={CHEVRON} stroke={color} strokeWidth={1.5} {...ROUND} />
    </Icon>
  );
}

export function Check({ color }: { color: string }) {
  return (
    <Icon width={12} height={10}>
      <Path d={CHECK} stroke={color} strokeWidth={2} {...ROUND} />
    </Icon>
  );
}

export function ChipIcon({ kind, color }: { kind: number; color: string }) {
  return (
    <Icon width={13} height={13}>
      <Path d={CHIP_ICONS[kind % CHIP_ICONS.length]} stroke={color} strokeWidth={1.3} {...ROUND} />
    </Icon>
  );
}

/** The stop button's open ring: `share` of a circle `size` across, starting at the top. */
export function Ring({ size, share, color }: { size: number; share: number; color: string }) {
  return (
    <Icon width={size} height={size}>
      <Path d={arc(size, share)} stroke={color} strokeWidth={1.8} {...ROUND} />
    </Icon>
  );
}

const styles = StyleSheet.create({
  svg: {
    position: 'absolute',
    left: -PAD,
    top: -PAD,
  },
  mark: {
    width: MARK_SIZE,
    height: MARK_SIZE,
  },
  markCanvas: {
    position: 'absolute',
    left: -MARK_BLEED,
    top: -MARK_BLEED,
    width: MARK_SIZE + MARK_BLEED * 2,
    height: MARK_SIZE + MARK_BLEED * 2,
  },
});
