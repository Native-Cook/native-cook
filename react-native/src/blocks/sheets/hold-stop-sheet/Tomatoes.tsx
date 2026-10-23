import { memo } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Circle, Defs, Ellipse, G, Path, RadialGradient, Stop } from 'react-native-svg';

type Value = { get(): number };

/** The pile's height above the bottom edge, in points. */
export const PILE_HEIGHT = 380;
// Rows of the pile, from the bottom up, and the gap between them.
const ROWS = 13;
const ROW_STEP = 28;
// How far a tomato falls out of sight below the screen, and how much of the motion is spread over the tomatoes.
const FALL = 640;
const SPREAD = 0.5;

type Kind = 'red' | 'yellow' | 'orange';
type Face = 'none' | 'dots' | 'smile' | 'happy' | 'oh';

// Colours of a piece: light where the light falls, base, and the shaded edge.
const BODY: Record<Kind, [string, string, string]> = {
  red: ['#ff9a62', '#f2622d', '#cf401b'],
  yellow: ['#ffe68a', '#fbc63a', '#e39d12'],
  orange: ['#ffbe6a', '#f88f1f', '#d9690b'],
};
const LEAF = '#4fb03c';
const LEAF_DARK = '#2f8a2a';
const FACE = '#3a2019';
const BLUSH = '#ff7f6e';

/** A fixed pseudo-random number in [0, 1) for a seed, so every pile is the same pile. */
function seeded(seed: number) {
  const x = Math.sin(seed * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}

export type Tomato = { x: number; y: number; size: number; kind: Kind; face: Face; delay: number; tilt: number; calyx: number };

/** How high the pile stands at a point `u` of the width (0 left, 1 right), in points. */
function surface(u: number) {
  return 262 + 58 * Math.sin(u * 5.6 + 0.9) + 26 * Math.sin(u * 13.1 + 2.2);
}

/** The tomatoes of a pile `width` wide, back ones first. */
export function pile(width: number): Tomato[] {
  const list: Tomato[] = [];
  let i = 0;
  for (let row = 0; row < ROWS; row++) {
    const rowY = PILE_HEIGHT - 20 - row * ROW_STEP;
    let x = -14 + seeded(row * 13 + 1) * 22;
    while (x < width + 8) {
      i += 1;
      const big = seeded(i * 11 + 9) > 0.95 && row > 1 && row < 7;
      const size = big ? 64 + seeded(i * 11 + 3) * 12 : 36 + seeded(i * 11 + 3) * 18;
      const cx = x + size * 0.45;
      const cy = rowY + (seeded(i * 11 + 4) - 0.5) * 16;
      if (PILE_HEIGHT - cy < surface(cx / width)) {
        const pick = seeded(i * 11 + 6);
        const kind: Kind = pick < 0.76 ? 'red' : pick < 0.93 ? 'yellow' : 'orange';
        const look = seeded(i * 11 + 7);
        const face: Face = size < 40 && look < 0.5 ? 'none' : look < 0.35 ? 'dots' : look < 0.6 ? 'smile' : look < 0.82 ? 'happy' : 'oh';
        list.push({
          x: cx - size / 2,
          y: cy - size / 2,
          size,
          kind,
          face,
          delay: seeded(i * 11 + 5) * SPREAD,
          tilt: (seeded(i * 11 + 8) - 0.5) * 50,
          calyx: seeded(i * 11 + 10) * 72,
        });
      }
      x += size * 0.74 + seeded(i * 11 + 2) * 8;
    }
  }
  // Lower and bigger pieces sit in front.
  return list.sort((a, b) => a.y + a.size * 0.6 - (b.y + b.size * 0.6));
}

/** A five-leaf calyx around (50, 20) in the 100 x 100 box, turned by `turn` degrees. */
function calyxPath(turn: number) {
  let d = '';
  for (let k = 0; k < 5; k++) {
    const a = ((-90 + turn + k * 72) * Math.PI) / 180;
    const tipX = 50 + Math.cos(a) * 24;
    const tipY = 20 + Math.sin(a) * 15;
    const l = a - 0.5;
    const r = a + 0.5;
    d += `M50 20Q${50 + Math.cos(l) * 13} ${20 + Math.sin(l) * 9} ${tipX} ${tipY}Q${50 + Math.cos(r) * 13} ${20 + Math.sin(r) * 9} 50 20Z`;
  }
  return d;
}

const Art = memo(function Art({ kind, face, calyx }: { kind: Kind; face: Face; calyx: number }) {
  const [light, base, edge] = BODY[kind];
  return (
    <Svg viewBox="0 0 100 100" style={StyleSheet.absoluteFill}>
      <Defs>
        <RadialGradient id="body" cx="36" cy="36" r="70" gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={light} />
          <Stop offset="0.5" stopColor={base} />
          <Stop offset="1" stopColor={edge} />
        </RadialGradient>
      </Defs>
      <Ellipse cx={50} cy={56} rx={46} ry={42} fill="url(#body)" />
      {kind === 'orange' ? (
        <G>
          {[
            [30, 50],
            [62, 42],
            [44, 74],
            [70, 66],
            [52, 58],
          ].map(([x, y]) => (
            <Circle key={`${x}`} cx={x} cy={y} r={1.6} fill={edge} opacity={0.35} />
          ))}
          <Path d="M50 18 L51 9" stroke={LEAF_DARK} strokeWidth={4} strokeLinecap="round" />
          <Path d="M52 15 Q66 2 80 10 Q70 22 52 15Z" fill={LEAF} stroke={LEAF_DARK} strokeWidth={1.5} strokeLinejoin="round" />
        </G>
      ) : (
        <G>
          <Path d={calyxPath(calyx)} fill={LEAF} stroke={LEAF_DARK} strokeWidth={1.6} strokeLinejoin="round" />
          <Path d="M50 20 L52 9" stroke={LEAF_DARK} strokeWidth={4.5} strokeLinecap="round" />
        </G>
      )}
      <Ellipse cx={30} cy={40} rx={10} ry={5.5} fill="#ffffff" opacity={0.45} transform="rotate(-38 30 40)" />
      {face !== 'none' && (
        <G>
          <Ellipse cx={34} cy={66} rx={6} ry={3.4} fill={BLUSH} opacity={0.5} />
          <Ellipse cx={66} cy={66} rx={6} ry={3.4} fill={BLUSH} opacity={0.5} />
          {face === 'happy' ? (
            <G>
              <Path d="M37 59 Q41 54 45 59" stroke={FACE} strokeWidth={2.6} fill="none" strokeLinecap="round" />
              <Path d="M55 59 Q59 54 63 59" stroke={FACE} strokeWidth={2.6} fill="none" strokeLinecap="round" />
            </G>
          ) : (
            <G>
              <Circle cx={41} cy={58} r={3.4} fill={FACE} />
              <Circle cx={59} cy={58} r={3.4} fill={FACE} />
            </G>
          )}
          {(face === 'smile' || face === 'happy') && <Path d="M45 66 Q50 72 55 66" stroke={FACE} strokeWidth={2.4} fill="none" strokeLinecap="round" />}
          {face === 'oh' && <Ellipse cx={50} cy={68} rx={2.6} ry={3.2} fill={FACE} />}
        </G>
      )}
    </Svg>
  );
});

/**
 * `fall` goes 0 to 1 as the pile drops away below the screen, `rise` 0 to 1 as it rains back in
 * from above and lands with bounces. Each tomato is a little behind the one before it, which is
 * what makes a pile and not a sheet.
 */
export function useFall(fall: Value, rise: Value, delay: number, drop: number, reduced: boolean) {
  return useAnimatedStyle(() => {
    const down = Math.min(1, Math.max(0, fall.get() * (1 + SPREAD) - delay));
    const up = Math.min(1, Math.max(0, rise.get() * (1 + SPREAD) - delay));
    if (reduced) return { opacity: 1 - down * (1 - up), transform: [{ translateY: 0 }] };
    if (rise.get() > 0) return { opacity: 1, transform: [{ translateY: -drop * (1 - Easing.bounce(up)) }] };
    return { opacity: 1, transform: [{ translateY: FALL * down * down }] };
  });
}

function Piece({ tomato, fall, rise, drop, reduced }: { tomato: Tomato; fall: Value; rise: Value; drop: number; reduced: boolean }) {
  const style = useFall(fall, rise, tomato.delay, drop, reduced);
  const { x, y, size, kind, face, tilt, calyx } = tomato;
  return (
    <Animated.View pointerEvents="none" style={[styles.piece, { left: x, top: y, width: size, height: size }, style]}>
      <View style={[StyleSheet.absoluteFill, { transform: [{ rotate: `${tilt}deg` }] }]}>
        <Art kind={kind} face={face} calyx={calyx} />
      </View>
    </Animated.View>
  );
}

/** A pile of tomatoes along the bottom of the screen; `drop` is how far above it they rain in from. */
export function Tomatoes({ width, drop, fall, rise, reduced }: { width: number; drop: number; fall: Value; rise: Value; reduced: boolean }) {
  return (
    <View style={[styles.pile, { height: PILE_HEIGHT }]} pointerEvents="none">
      {pile(width).map((tomato, index) => (
        <Piece key={index} tomato={tomato} fall={fall} rise={rise} drop={drop} reduced={reduced} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  pile: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  piece: { position: 'absolute' },
});
