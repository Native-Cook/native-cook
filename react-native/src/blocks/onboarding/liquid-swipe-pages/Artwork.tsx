import { Ionicons } from '@expo/vector-icons';
import { Canvas, Circle, Path, RoundedRect } from '@shopify/react-native-skia';
import { StyleSheet, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { AMBIENT } from './wave';

export type ArtKind = 'tiles' | 'flag' | 'coins';

/** The box every picture is drawn in. */
export const ART_WIDTH = 300;
export const ART_HEIGHT = 340;

// Tiles: five app tiles juggled on an arc over a disc. Each bobs, a fifth of a turn after its neighbour.
const TILE = 60;
const TILE_RADIUS = 16;
const TILE_ICON = 28;
const BOB = 6;
const DISC = { x: 150, y: 232, r: 60, core: 36 };
const TILES = [
  { icon: 'image', x: 26, y: 175, tilt: -12 },
  { icon: 'play', x: 74, y: 112, tilt: -6 },
  { icon: 'partly-sunny', x: 150, y: 88, tilt: 0 },
  { icon: 'chatbubble', x: 226, y: 112, tilt: 6 },
  { icon: 'options', x: 274, y: 175, tilt: 12 },
] as const;

// Flag: a pole planted on a hill, with a second hill behind. The flag's free end waves.
const HILL = 'M30 322L150 202L270 322Z';
const BACK_HILL = 'M130 322L214 248L298 322Z';
const POLE = { x: 148, y: 18, width: 4, height: 196 };
const FLAG = { x: 152, y: 24, width: 70, height: 44 };
const FLAG_WAVE = 5;
const FLAG_TURNS = 0.9;
const FLAG_STEPS = 12;

// Coins: three discs that rise like a chart, and an arrow through them.
const COINS = [
  { x: 70, y: 243, r: 25 },
  { x: 150, y: 180, r: 35 },
  { x: 238, y: 93, r: 45 },
];
const COIN_RIM = 7;
const TREND = 'M24 291L70 243L150 180L238 93L270 61M252 61L270 61L270 79';

type Props = {
  kind: ArtKind;
  /** The page's colour: the tiles' icons are cut out of it. */
  color: string;
  /** Seconds since the screen appeared. */
  clock: SharedValue<number>;
  still: boolean;
};

function Tile({ index, color, clock, still }: { index: number; color: string; clock: SharedValue<number>; still: boolean }) {
  const tile = TILES[index];
  const style = useAnimatedStyle(() => ({
    transform: [
      { translateY: still ? 0 : -BOB * Math.sin(2 * Math.PI * (clock.get() / AMBIENT + index / TILES.length)) },
      { rotate: `${tile.tilt}deg` },
    ],
  }));
  return (
    <Animated.View style={[styles.tile, { left: tile.x - TILE / 2, top: tile.y - TILE / 2 }, style]}>
      <Ionicons name={tile.icon} size={TILE_ICON} color={color} />
    </Animated.View>
  );
}

function Flag({ clock, still }: { clock: SharedValue<number>; still: boolean }) {
  const flag = useDerivedValue(() => {
    const phase = still ? 0 : (2 * Math.PI * clock.get()) / AMBIENT;
    let top = '';
    let bottom = '';
    for (let i = 0; i <= FLAG_STEPS; i++) {
      const f = i / FLAG_STEPS;
      const x = FLAG.x + FLAG.width * f;
      // Held at the pole, free at the far end.
      const y = FLAG.y + FLAG_WAVE * f * Math.sin(phase - f * FLAG_TURNS * 2 * Math.PI);
      top += `${i === 0 ? 'M' : 'L'}${x} ${y}`;
      bottom = `L${x} ${y + FLAG.height}${bottom}`;
    }
    return `${top}${bottom}Z`;
  });
  return (
    <Canvas style={styles.box}>
      <Path path={BACK_HILL} color="rgba(0,0,0,0.07)" />
      <Path path={HILL} color="rgba(0,0,0,0.13)" />
      <RoundedRect x={POLE.x} y={POLE.y} width={POLE.width} height={POLE.height} r={POLE.width / 2} color="#ffffff" />
      <Path path={flag} color="#ffffff" />
    </Canvas>
  );
}

function Coins() {
  return (
    <Canvas style={styles.box}>
      {COINS.map((coin) => (
        <Circle key={coin.r} cx={coin.x} cy={coin.y} r={coin.r} color="rgba(255,255,255,0.26)" />
      ))}
      {COINS.map((coin) => (
        <Circle key={coin.r} cx={coin.x} cy={coin.y} r={coin.r - COIN_RIM} style="stroke" strokeWidth={2} color="rgba(255,255,255,0.6)" />
      ))}
      <Path path={TREND} style="stroke" strokeWidth={4} strokeCap="round" strokeJoin="round" color="#ffffff" />
    </Canvas>
  );
}

/** A page's picture: flat shapes in white and in shades of the page's colour. */
export function Artwork({ kind, color, clock, still }: Props) {
  if (kind === 'flag') return <Flag clock={clock} still={still} />;
  if (kind === 'coins') return <Coins />;
  return (
    <View style={styles.box}>
      <View style={[styles.disc, { width: DISC.r * 2, height: DISC.r * 2, borderRadius: DISC.r, left: DISC.x - DISC.r, top: DISC.y - DISC.r }]} />
      <View style={[styles.disc, { width: DISC.core * 2, height: DISC.core * 2, borderRadius: DISC.core, left: DISC.x - DISC.core, top: DISC.y - DISC.core }]} />
      {TILES.map((tile, index) => (
        <Tile key={tile.icon} index={index} color={color} clock={clock} still={still} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    width: ART_WIDTH,
    height: ART_HEIGHT,
  },
  disc: {
    position: 'absolute',
    backgroundColor: 'rgba(255,255,255,0.16)',
  },
  tile: {
    position: 'absolute',
    width: TILE,
    height: TILE,
    borderRadius: TILE_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,255,255,0.9)',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
