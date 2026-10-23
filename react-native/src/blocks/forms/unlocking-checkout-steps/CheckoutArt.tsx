import { Canvas, Circle, Group, Path, Rect, RoundedRect } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

const TEAL = '#2a9db3';
const OUTLINE = '#17251e';

// The street map is drawn in a 310 × 166 box and scaled to the width it is given.
const MAP_WIDTH = 310;
export const MAP_RATIO = 166 / MAP_WIDTH;
const AVENUES = 'M-10 122L124 -10M36 176L204 -10M150 176L330 38M-10 62L92 176M-10 18L330 112';
const STREETS = 'M96 -10L320 152M204 -10L252 176M-10 96L60 176M250 -10L330 20M-10 150L40 176M130 100L190 176M60 40L110 90';
const BLOCKS =
  'M132 22L166 46L150 68L116 44ZM222 60L262 72L254 104L214 92ZM40 96L72 132L50 152L18 116ZM176 104L214 122L200 152L162 134ZM258 122L300 134L294 160L252 148ZM20 34L52 12';
const ROUTE = 'M62 130C74 84 112 52 162 48L238 48';
const ROUTE_START = { x: 62, y: 130 };
const ROUTE_END = { x: 238, y: 48 };

/** A grey street map with the delivery route on it. `drawn` goes from 0 to 1 as the route is drawn. */
export function RouteMap({ width, drawn }: { width: number; drawn: SharedValue<number> }) {
  const scale = width / MAP_WIDTH;
  const arrived = useDerivedValue(() => Math.min(1, Math.max(0, drawn.get() * 5 - 4)));
  return (
    <Canvas style={{ width, height: width * MAP_RATIO }}>
      <Group transform={[{ scale }]}>
        <Rect x={0} y={0} width={MAP_WIDTH} height={MAP_WIDTH * MAP_RATIO} color="#e9e9e9" />
        <Path path={BLOCKS} style="stroke" strokeWidth={1} color="#bdbdbd" />
        <Path path={AVENUES} style="stroke" strokeWidth={7} color="#f8f8f8" />
        <Path path={STREETS} style="stroke" strokeWidth={3.5} color="#f8f8f8" />
        <Path path={ROUTE} style="stroke" strokeWidth={2.6} strokeCap="round" color={TEAL} end={drawn} />
        <Circle cx={ROUTE_START.x} cy={ROUTE_START.y} r={5} color={TEAL} />
        <Circle cx={ROUTE_END.x} cy={ROUTE_END.y} r={5} color={TEAL} opacity={arrived} />
        <Circle cx={ROUTE_END.x} cy={ROUTE_END.y} r={2} color="#ffffff" opacity={arrived} />
      </Group>
    </Canvas>
  );
}

// The success drawing in a 220 × 220 box, scaled to the size it is given: a payment card behind a
// parcel, and a check badge.
const ART = 220;
const CARD_CHIP = 'M58 46L74 46M58 52L74 52';
const CARD_WAVES = 'M112 40C116 44 116 52 112 56M119 36C125 43 125 53 119 60';
const BOX_TOP = 'M46 108L64 88L168 88L150 108Z';
const BOX_SIDE = 'M150 108L168 88L168 170L150 192Z';
const BOX_TAPE = 'M90 108L106 88L122 88L106 108L106 134L90 134Z';
const BOX_LABEL = 'M60 164L96 164M60 174L84 174';
const SPARKS = 'M196 30L204 18M204 50L216 46M182 20L182 8';
const BADGE = { x: 166, y: 66, r: 30 };
const BADGE_CHECK = 'M153 67L162 77L180 56';

/** A parcel, the card that paid for it and a check badge, as a line drawing. `checked` draws the check. */
export function SuccessArt({ size, checked }: { size: number; checked: SharedValue<number> }) {
  return (
    <Canvas style={{ width: size, height: size }}>
      <Group transform={[{ scale: size / ART }]}>
        <Group transform={[{ rotate: -0.12 }]} origin={{ x: 90, y: 58 }}>
          <RoundedRect x={36} y={22} width={108} height={72} r={12} color="#f6b48a" />
          <RoundedRect x={36} y={22} width={108} height={72} r={12} style="stroke" strokeWidth={3} color={OUTLINE} />
          <RoundedRect x={52} y={40} width={28} height={18} r={5} style="stroke" strokeWidth={2.4} color={OUTLINE} />
          <Path path={CARD_CHIP} style="stroke" strokeWidth={2} strokeCap="round" color={OUTLINE} />
          <Path path={CARD_WAVES} style="stroke" strokeWidth={2.4} strokeCap="round" color={OUTLINE} />
        </Group>

        <Rect x={46} y={108} width={104} height={84} color="#eec392" />
        <Path path={BOX_TOP} color="#dba972" />
        <Path path={BOX_SIDE} color="#c9955c" />
        <Path path={BOX_TAPE} color="#fbeed9" />
        <Rect x={46} y={108} width={104} height={84} style="stroke" strokeWidth={3} strokeJoin="round" color={OUTLINE} />
        <Path path={BOX_TOP} style="stroke" strokeWidth={3} strokeJoin="round" color={OUTLINE} />
        <Path path={BOX_SIDE} style="stroke" strokeWidth={3} strokeJoin="round" color={OUTLINE} />
        <Path path={BOX_LABEL} style="stroke" strokeWidth={2.6} strokeCap="round" color={OUTLINE} />

        <Circle cx={BADGE.x} cy={BADGE.y} r={BADGE.r} color="#34d58a" />
        <Circle cx={BADGE.x} cy={BADGE.y} r={BADGE.r} style="stroke" strokeWidth={3.4} color={OUTLINE} />
        <Path path={BADGE_CHECK} style="stroke" strokeWidth={5} strokeCap="round" strokeJoin="round" color="#ffffff" end={checked} />
        <Path path={SPARKS} style="stroke" strokeWidth={3} strokeCap="round" color={OUTLINE} />
      </Group>
    </Canvas>
  );
}
