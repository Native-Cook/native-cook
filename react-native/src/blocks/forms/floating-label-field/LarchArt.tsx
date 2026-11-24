import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, Rect, RoundedRect, Skia, vec, type SkPath } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet } from 'react-native';

/*
 The header of the Larch sign-up: a valley in autumn drawn in code. Four ridges fade back into
 haze, a low sun sits behind them, and the near slopes carry conifers, a few of them larches
 that have turned gold. It is drawn once and never animates; the fields are the subject.
*/

export const PAGE = '#F3EFE6';
const RIDGES = ['#D3D9C6', '#B4C3AA', '#7E9C83', '#3B6B54'];
const PINE = ['#2C5A46', '#356650'];
const GOLD = ['#D9A441', '#C98A1B', '#E2B65A'];

function hash(i: number) {
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

/** Height of ridge `layer` at x: two slow sines and a little texture. */
function ridgeY(layer: number, x: number, w: number, base: number, amp: number) {
  const u = x / w;
  return (
    base +
    amp * (0.62 * Math.sin(u * (2.1 + layer * 0.9) + layer * 1.7) + 0.3 * Math.sin(u * (5.3 + layer * 1.3) + layer * 4.1) + 0.08 * Math.sin(u * 23 + layer * 2.3))
  );
}

function ridgePath(layer: number, w: number, h: number, base: number, amp: number) {
  const path = Skia.PathBuilder.Make();
  path.moveTo(0, h);
  for (let x = 0; x <= w + 4; x += 4) path.lineTo(x, ridgeY(layer, x, w, base, amp));
  path.lineTo(w, h);
  path.close();
  return path.build();
}

/** A conifer: three tiers narrowing to a point, standing on (x, y). */
function addTree(path: ReturnType<typeof Skia.PathBuilder.Make>, x: number, y: number, tall: number) {
  const wide = tall * 0.3;
  path.moveTo(x, y - tall);
  path.lineTo(x + wide * 0.42, y - tall * 0.62);
  path.lineTo(x + wide * 0.24, y - tall * 0.62);
  path.lineTo(x + wide * 0.74, y - tall * 0.3);
  path.lineTo(x + wide * 0.5, y - tall * 0.3);
  path.lineTo(x + wide, y + 2);
  path.lineTo(x - wide, y + 2);
  path.lineTo(x - wide * 0.5, y - tall * 0.3);
  path.lineTo(x - wide * 0.74, y - tall * 0.3);
  path.lineTo(x - wide * 0.24, y - tall * 0.62);
  path.lineTo(x - wide * 0.42, y - tall * 0.62);
  path.close();
}

type Art = { ridges: SkPath[]; pines: SkPath[]; golds: SkPath[]; farTrees: SkPath; sun: { x: number; y: number } };

function makeArt(w: number, h: number): Art {
  const bases = [h * 0.46, h * 0.58, h * 0.7, h * 0.84];
  const amps = [h * 0.07, h * 0.075, h * 0.07, h * 0.06];
  const ridges = RIDGES.map((_, layer) => ridgePath(layer, w, h, bases[layer], amps[layer]));

  // Small, pale trees on the third ridge; full-size ones on the nearest.
  const far = Skia.PathBuilder.Make();
  const farCount = Math.round(w / 15);
  for (let i = 0; i < farCount; i += 1) {
    const x = ((i + hash(i + 40) * 0.8) / farCount) * w;
    if (hash(i + 71) < 0.3) continue;
    addTree(far, x, ridgeY(2, x, w, bases[2], amps[2]) + 1, 7 + hash(i + 9) * 7);
  }

  const pines = PINE.map(() => Skia.PathBuilder.Make());
  const golds = GOLD.map(() => Skia.PathBuilder.Make());
  const count = Math.round(w / 9);
  for (let i = 0; i < count; i += 1) {
    const x = ((i + hash(i + 3) * 0.9) / count) * w;
    if (hash(i + 17) < 0.18) continue;
    const y = ridgeY(3, x, w, bases[3], amps[3]) + 1 + hash(i + 5) * 5;
    const tall = 15 + hash(i + 11) * 15;
    // Larches gather in two stands rather than being sprinkled evenly.
    const stand = Math.sin((x / w) * 7.4 + 0.6) * 0.5 + 0.5;
    if (hash(i + 29) < 0.14 + stand * 0.42) addTree(golds[Math.floor(hash(i + 31) * GOLD.length)], x, y, tall * 0.94);
    else addTree(pines[Math.floor(hash(i + 37) * PINE.length)], x, y, tall);
  }

  return {
    ridges,
    pines: pines.map((p) => p.build()),
    golds: golds.map((p) => p.build()),
    farTrees: far.build(),
    sun: { x: w * 0.7, y: h * 0.4 },
  };
}

/** A small conifer for the brand mark, in a 26 pt square at (x, y). */
function markTree(x: number, y: number) {
  const path = Skia.PathBuilder.Make();
  addTree(path, x + 13, y + 20, 15);
  return path.build();
}

export function LarchArt({ width, height, markX, markY }: { width: number; height: number; markX: number; markY: number }) {
  const art = useMemo(() => makeArt(width, height), [width, height]);
  const mark = useMemo(() => markTree(markX, markY), [markX, markY]);

  return (
    <Canvas style={[styles.canvas, { width, height }]} pointerEvents="none">
      <Rect x={0} y={0} width={width} height={height}>
        <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={['#DCE3D6', '#ECE6CF', '#F4E5C6', PAGE]} positions={[0, 0.34, 0.6, 1]} />
      </Rect>
      <Circle cx={art.sun.x} cy={art.sun.y} r={height * 0.3} color="rgba(255,236,190,0.55)">
        <BlurMask blur={height * 0.14} style="normal" />
      </Circle>
      <Circle cx={art.sun.x} cy={art.sun.y} r={height * 0.085} color="#FFF3D2" />

      <Path path={art.ridges[0]} color={RIDGES[0]} />
      <Path path={art.ridges[1]} color={RIDGES[1]} />
      <Rect x={0} y={height * 0.5} width={width} height={height * 0.2} color="rgba(244,239,226,0.5)">
        <BlurMask blur={16} style="normal" />
      </Rect>
      <Path path={art.farTrees} color="#6C8D74" />
      <Path path={art.ridges[2]} color={RIDGES[2]} />
      <Rect x={0} y={height * 0.7} width={width} height={height * 0.12} color="rgba(244,239,226,0.42)">
        <BlurMask blur={14} style="normal" />
      </Rect>
      <Group>
        <Path path={art.pines[0]} color={PINE[0]} />
        <Path path={art.pines[1]} color={PINE[1]} />
        <Path path={art.golds[0]} color={GOLD[0]} />
        <Path path={art.golds[1]} color={GOLD[1]} />
        <Path path={art.golds[2]} color={GOLD[2]} />
      </Group>
      <Path path={art.ridges[3]} color={RIDGES[3]} />

      {/* The valley floor melts into the page. */}
      <Rect x={0} y={height * 0.8} width={width} height={height * 0.2 + 1}>
        <LinearGradient start={vec(0, height * 0.8)} end={vec(0, height)} colors={['rgba(243,239,230,0)', 'rgba(243,239,230,0.8)', PAGE]} positions={[0, 0.55, 1]} />
      </Rect>

      <RoundedRect x={markX} y={markY} width={26} height={26} r={8} color="#1F5C45" />
      <Path path={mark} color="#F1D48A" />
    </Canvas>
  );
}

const styles = StyleSheet.create({ canvas: { position: 'absolute', left: 0, top: 0 } });
