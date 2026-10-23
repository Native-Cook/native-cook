import { Image } from 'expo-image';
import { LinearGradient as Shade } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';
import Svg, { Circle, Defs, G, LinearGradient, Path, Rect, Stop } from 'react-native-svg';

import type { Design } from './designs';
import { CARD } from './theme';

// The face of a card: its design, the bank's leaf mark, the chip and a network mark.
// Everything but the design is the pin's furniture, drawn so it stays crisp in 3D.

export function CardArt({ design, w = CARD.w, h = CARD.h, furniture = true }: { design: Design; w?: number; h?: number; furniture?: boolean }) {
  return (
    <View style={[styles.clip, { width: w, height: h }]}>
      {design.art ? <Image source={design.art} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} /> : <Blossoms w={w} h={h} />}
      {/* the light falls from the upper right: the card darkens towards its lower left, as in the pin */}
      {furniture ? <Shade colors={['rgba(0,0,0,0)', SHADE]} start={{ x: 0.85, y: 0 }} end={{ x: 0.05, y: 1 }} style={StyleSheet.absoluteFill} /> : null}
      {furniture ? <Furniture /> : null}
    </View>
  );
}

const SHADE = 'rgba(20, 20, 30, 0.16)';

// Flat five-petal flowers on green, placed where the pin's card has them (card units 260×163,
// measured on the pin's rest frame through the card's slant).
const FLOWERS = [
  { x: 207, y: 59, r: 46, petal: '#eab6c4', heart: '#d1323c', rot: 10 },
  { x: 87, y: 46, r: 25, petal: '#eab6c4', heart: '#d1323c', rot: -8 },
  { x: 23, y: 135, r: 44, petal: '#eab6c4', heart: '#d1323c', rot: 20 },
  { x: 128, y: 123, r: 25, petal: '#eab6c4', heart: '#d1323c', rot: 4 },
  { x: 157, y: 71, r: 17, petal: '#f1ecd6', heart: '#e2a8b2', rot: -14 },
];

function flower(r: number) {
  // five round petals around a centre
  let d = '';
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2 - Math.PI / 2;
    const px = Math.cos(a) * r * 0.5;
    const py = Math.sin(a) * r * 0.5;
    const pr = r * 0.52;
    d += `M${(px - pr).toFixed(1)} ${py.toFixed(1)} a${pr.toFixed(1)} ${pr.toFixed(1)} 0 1 0 ${(pr * 2).toFixed(1)} 0 a${pr.toFixed(1)} ${pr.toFixed(1)} 0 1 0 ${(-pr * 2).toFixed(1)} 0 `;
  }
  return d;
}

function Blossoms({ w, h }: { w: number; h: number }) {
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${CARD.w} ${CARD.h}`} style={StyleSheet.absoluteFill}>
      <Defs>
        <LinearGradient id="g" x1="0" y1="1" x2="1" y2="0">
          <Stop offset="0" stopColor="#2f8f40" />
          <Stop offset="1" stopColor="#4aaf55" />
        </LinearGradient>
      </Defs>
      <Rect x={0} y={0} width={CARD.w} height={CARD.h} fill="url(#g)" />
      {FLOWERS.map((f, i) => (
        <G key={i} transform={`translate(${f.x} ${f.y}) rotate(${f.rot})`}>
          <Path d={flower(f.r)} fill={f.petal} />
          <Circle cx={0} cy={0} r={f.r * 0.2} fill={f.heart} />
        </G>
      ))}
    </Svg>
  );
}

function Furniture() {
  const { logo, chip, mark } = CARD;
  return (
    <Svg width={CARD.w} height={CARD.h} style={StyleSheet.absoluteFill} pointerEvents="none">
      {/* bank mark: a ring around a leaf (stands in for the pin's bank logo) */}
      <Circle cx={logo.x} cy={logo.y} r={logo.r} stroke="rgba(255,255,255,0.88)" strokeWidth={2.4} fill="none" />
      <Path
        d={`M${logo.x - 5.5} ${logo.y + 5.5} C${logo.x - 6} ${logo.y - 3} ${logo.x - 1} ${logo.y - 6.5} ${logo.x + 6} ${logo.y - 6} C${logo.x + 6.5} ${logo.y + 1} ${logo.x + 3} ${logo.y + 6} ${logo.x - 5.5} ${logo.y + 5.5} Z`}
        fill="rgba(255,255,255,0.88)"
      />
      {/* chip */}
      <Rect x={chip.x} y={chip.y} width={chip.w} height={chip.h} rx={chip.r} fill="#ecebe6" />
      <Path
        d={`M${chip.x} ${chip.y + 8} h9 M${chip.x} ${chip.y + 17} h9 M${chip.x + chip.w} ${chip.y + 8} h-9 M${chip.x + chip.w} ${chip.y + 17} h-9 M${chip.x + 9} ${chip.y} v${chip.h} M${chip.x + chip.w - 9} ${chip.y} v${chip.h} M${chip.x + 9} ${chip.y + 12.5} h${chip.w - 18}`}
        stroke="#b9b6ad"
        strokeWidth={0.9}
        fill="none"
      />
      {/* network mark: an invented network, three rising slanted bars */}
      <G transform={`translate(${mark.x + 14} ${mark.y})`} fill="#ffffff">
        <Path d="M6 25 L11 11 H21 L16 25 Z" />
        <Path d="M20 25 L28 4 H38 L30 25 Z" />
        <Path d="M34 25 L43 0 H53 L44 25 Z" />
      </G>
    </Svg>
  );
}

const styles = StyleSheet.create({
  clip: { borderRadius: CARD.radius, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: '#2f8f40' },
});
