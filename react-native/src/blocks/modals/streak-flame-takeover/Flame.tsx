import { Canvas, Group, LinearGradient, Oval, Path, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

export const FLAME_SIZE = 180;
// Everything is drawn in a 200-point box and scaled to the flame's size.
const BOX = 200;
const SCALE = FLAME_SIZE / BOX;

const FRAME = '#4a1a10';

// The gold flame: a round body with three tips swept up and to the right.
const OUTER =
  'M100 190C58 190 28 160 28 120C28 92 40 70 58 54C62 70 70 76 78 74C76 54 84 34 104 18C104 38 112 50 122 52C124 36 134 22 152 12C146 32 148 48 156 58C162 50 170 44 182 42C172 58 168 72 170 88C174 100 172 110 172 120C172 160 142 190 100 190Z';
// The hot little character inside it, with two ears, a waving arm and a foot.
const BODY =
  'M62 150C60 120 63 96 72 82C76 70 90 66 98 78C100 80 104 80 106 78C114 66 128 70 132 82C142 98 144 124 140 150C136 172 120 182 100 182C80 182 64 172 62 150Z';
const ARM = 'M138 124C150 118 158 108 160 98C166 102 166 116 156 126C151 131 144 134 139 134Z';
const LEFT_LENS = 'M66 110L100 110L98 124C96 132 91 135 84 135L77 135C70 135 66 130 66 123Z';
const RIGHT_LENS = 'M106 110L146 108L144 122C142 131 136 135 128 135L117 135C110 135 106 130 106 123Z';
const LENSES = `${LEFT_LENS}${RIGHT_LENS}`;
const BROW = 'M60 104L152 101L152 110L60 113Z';
const BRIDGE = 'M98 112H108V117H98Z';
const STRIPES = 'M80 106L90 106L76 138L66 138ZM124 104L136 104L120 138L108 138Z';
const SPARKLE = 'M158 86Q160 96 170 98Q160 100 158 110Q156 100 146 98Q156 96 158 86Z';
const GLINT_WIDTH = 9;

/**
 * The flame mascot: a gold flame round a hot little character in shades, with a glint that sweeps
 * the lenses and a sparkle by them.
 */
export function Flame({ glint, sparkle }: { glint: SharedValue<number>; sparkle: SharedValue<number> }) {
  const sweep = useDerivedValue(() => [{ translateX: 56 + glint.get() * 100 }, { skewX: -0.5 }]);
  return (
    <Canvas style={{ width: FLAME_SIZE, height: FLAME_SIZE }}>
      <Group transform={[{ scale: SCALE }]}>
        <Oval x={52} y={180} width={96} height={18} color="rgba(255, 200, 0, 0.22)" />
        <Path path={OUTER}>
          <RadialGradient c={vec(90, 112)} r={104} colors={['#fff6b0', '#ffdb1f', '#ffbd00', '#f59e00']} positions={[0, 0.38, 0.75, 1]} />
        </Path>
        <Path path={OUTER} style="stroke" strokeWidth={2} color="rgba(255, 250, 210, 0.5)" />
        <Oval x={58} y={46} width={84} height={64} opacity={0.55}>
          <RadialGradient c={vec(100, 78)} r={44} colors={['rgba(255,255,255,0.9)', 'rgba(255,255,255,0)']} />
        </Oval>

        <Path path={ARM}>
          <LinearGradient start={vec(140, 98)} end={vec(160, 134)} colors={['#ff8a34', '#e8471a']} />
        </Path>
        <Oval x={110} y={176} width={22} height={11} color="#d93d14" />
        <Path path={BODY}>
          <LinearGradient start={vec(0, 70)} end={vec(0, 182)} colors={['#ffa040', '#f45a1c', '#d63a12']} positions={[0, 0.5, 1]} />
        </Path>
        <Oval x={72} y={86} width={22} height={12} color="rgba(255,255,255,0.32)" transform={[{ rotate: -0.5 }]} origin={vec(83, 92)} />
        <Oval x={68} y={146} width={64} height={30} color="rgba(150, 30, 0, 0.18)" />

        {/* The shades: a heavy brow bar, two cream lenses with a dark band, and a moving glint. */}
        <Path path={LENSES}>
          <LinearGradient start={vec(0, 110)} end={vec(0, 135)} colors={['#fff6e4', '#f2cf98']} />
        </Path>
        <Group clip={LENSES}>
          <Path path={STRIPES} color={FRAME} opacity={0.88} />
          <Group transform={sweep}>
            <Rect x={0} y={104} width={GLINT_WIDTH} height={36} color="rgba(255,255,255,0.9)" />
          </Group>
        </Group>
        <Path path={LENSES} style="stroke" strokeWidth={4.5} strokeJoin="round" color={FRAME} />
        <Path path={BROW} color={FRAME} />
        <Path path={BRIDGE} color={FRAME} />
        <Path path={SPARKLE} color="#ffffff" opacity={sparkle} />
      </Group>
    </Canvas>
  );
}

export const WINGS_WIDTH = 300;
export const WINGS_HEIGHT = 140;
// One feathered wing; the other is the same, mirrored.
const WING =
  'M148 84C122 50 82 30 18 34C34 44 40 50 46 58C26 60 12 68 4 82C24 82 38 86 46 90C30 96 20 106 16 118C52 116 94 108 148 98Z';
const FEATHERS = 'M46 58C70 62 100 70 128 82M46 90C74 90 104 90 132 92';

/** A pair of gold wings that flap once behind the flame as it pops. */
export function Wings() {
  return (
    <Canvas style={{ width: WINGS_WIDTH, height: WINGS_HEIGHT }}>
      {[1, -1].map((side) => (
        <Group key={side} transform={[{ scaleX: side }]} origin={vec(WINGS_WIDTH / 2, 0)}>
          <Path path={WING}>
            <LinearGradient start={vec(10, 40)} end={vec(150, 100)} colors={['#fff08a', '#ffc800', '#f2a100']} positions={[0, 0.5, 1]} />
          </Path>
          <Path path={FEATHERS} style="stroke" strokeWidth={2.5} strokeCap="round" color="rgba(214, 130, 0, 0.55)" />
        </Group>
      ))}
    </Canvas>
  );
}
