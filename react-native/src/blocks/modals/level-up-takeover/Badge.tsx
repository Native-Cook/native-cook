import { Canvas, Group, LinearGradient, Path, Rect, Shadow, Skia, vec } from '@shopify/react-native-skia';
import { StyleSheet, Text, View } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

export const BADGE_WIDTH = 152;
export const BADGE_HEIGHT = 132;
/** Room round the badge for its shadow and sparkles. */
const MARGIN = 28;
const SIZE_W = BADGE_WIDTH + MARGIN * 2;
const SIZE_H = BADGE_HEIGHT + MARGIN * 2;

/** A flat-top hexagon `inset` points inside the badge, its corners rounded by `corner`. */
function hexagon(inset: number, corner: number) {
  const w = BADGE_WIDTH - inset * 2;
  const h = BADGE_HEIGHT - inset * 2;
  const quarter = w / 4;
  const ox = MARGIN + inset;
  const oy = MARGIN + inset;
  const points = [
    [ox + quarter, oy],
    [ox + w - quarter, oy],
    [ox + w, oy + h / 2],
    [ox + w - quarter, oy + h],
    [ox + quarter, oy + h],
    [ox, oy + h / 2],
  ];
  const builder = Skia.PathBuilder.Make();
  for (let i = 0; i < points.length; i++) {
    const [px, py] = points[(i + points.length - 1) % points.length];
    const [x, y] = points[i];
    const [nx, ny] = points[(i + 1) % points.length];
    // Cut each corner short along both edges and round it with a quad.
    const a = Math.hypot(x - px, y - py);
    const b = Math.hypot(nx - x, ny - y);
    const sx = x + ((px - x) / a) * corner;
    const sy = y + ((py - y) / a) * corner;
    const ex = x + ((nx - x) / b) * corner;
    const ey = y + ((ny - y) / b) * corner;
    if (i === 0) builder.moveTo(sx, sy);
    else builder.lineTo(sx, sy);
    builder.quadTo(x, y, ex, ey);
  }
  return builder.close().build();
}

/** A four-pointed sparkle centred on (x, y). */
function sparkle(x: number, y: number, r: number) {
  const k = r * 0.22;
  return Skia.PathBuilder.Make()
    .moveTo(x, y - r)
    .quadTo(x + k, y - k, x + r, y)
    .quadTo(x + k, y + k, x, y + r)
    .quadTo(x - k, y + k, x - r, y)
    .quadTo(x - k, y - k, x, y - r)
    .close()
    .build();
}

const BEZEL = hexagon(0, 22);
const RIM = hexagon(9, 17);
const BODY = hexagon(14, 15);
const FACE = hexagon(26, 11);
const TOP = MARGIN;
const BOTTOM = MARGIN + BADGE_HEIGHT;
const SPARKLES = [sparkle(MARGIN + BADGE_WIDTH - 4, MARGIN + 8, 9), sparkle(MARGIN + BADGE_WIDTH + 12, MARGIN + 30, 5), sparkle(MARGIN + 8, MARGIN + BADGE_HEIGHT - 10, 6)];
const SHINE_WIDTH = 30;

/**
 * The level badge: a white bezel round a glossy gold hexagon, the number on its face, a band of
 * light that sweeps across once and a few sparkles.
 */
export function Badge({ level, sweep, twinkle }: { level: number; sweep: SharedValue<number>; twinkle: SharedValue<number> }) {
  // The band runs from left of the badge to its right, leaning forward.
  const shine = useDerivedValue(() => [
    { translateX: MARGIN - SHINE_WIDTH * 2 + sweep.get() * (BADGE_WIDTH + SHINE_WIDTH * 4) },
    { skewX: -0.45 },
  ]);
  return (
    <View style={styles.badge}>
      <Canvas style={styles.canvas}>
        <Path path={BEZEL}>
          <LinearGradient start={vec(0, TOP)} end={vec(0, BOTTOM)} colors={['#ffffff', '#fff1c9']} />
          <Shadow dx={0} dy={12} blur={16} color="rgba(214, 128, 0, 0.38)" />
          <Shadow dx={0} dy={2} blur={3} color="rgba(160, 90, 0, 0.25)" />
        </Path>
        <Path path={RIM}>
          <LinearGradient start={vec(0, TOP + 9)} end={vec(0, BOTTOM - 9)} colors={['#ffb81a', '#e47400']} />
        </Path>
        <Path path={BODY}>
          <LinearGradient start={vec(0, TOP + 14)} end={vec(0, BOTTOM - 14)} colors={['#ffd84a', '#ffa812']} />
        </Path>
        <Path path={FACE}>
          <LinearGradient start={vec(0, TOP + 26)} end={vec(0, BOTTOM - 26)} colors={['#ffe98a', '#ffc21a', '#ffb000']} positions={[0, 0.55, 1]} />
        </Path>
        <Path path={FACE} style="stroke" strokeWidth={2} color="rgba(255, 255, 255, 0.55)" />
        <Group clip={BODY}>
          {/* Gloss over the upper half, and the moving band of light. */}
          <Rect x={MARGIN} y={TOP} width={BADGE_WIDTH} height={BADGE_HEIGHT * 0.48}>
            <LinearGradient start={vec(0, TOP)} end={vec(0, TOP + BADGE_HEIGHT * 0.48)} colors={['rgba(255,255,255,0.5)', 'rgba(255,255,255,0.06)']} />
          </Rect>
          <Group transform={shine}>
            <Rect x={0} y={TOP - 8} width={SHINE_WIDTH} height={BADGE_HEIGHT + 16} color="rgba(255,255,255,0.6)" />
          </Group>
        </Group>
        <Group opacity={twinkle}>
          {SPARKLES.map((p, i) => (
            <Path key={i} path={p} color="#ffffff" />
          ))}
        </Group>
      </Canvas>
      <Text style={styles.level}>{level}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: { width: SIZE_W, height: SIZE_H, alignItems: 'center', justifyContent: 'center' },
  canvas: { position: 'absolute', left: 0, top: 0, width: SIZE_W, height: SIZE_H },
  level: {
    color: '#ffffff',
    fontSize: 64,
    lineHeight: 72,
    fontWeight: '900',
    letterSpacing: -1,
    textShadowColor: '#d06f00',
    textShadowOffset: { width: 0, height: 3 },
    textShadowRadius: 0.5,
  },
});

export const BADGE_BOX = SIZE_H;
