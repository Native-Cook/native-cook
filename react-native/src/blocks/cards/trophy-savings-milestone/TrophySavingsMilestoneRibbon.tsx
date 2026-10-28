import { Canvas, Group, Image as SkiaImage, Path, Skia, Vertices, type SkImage, type SkPath, type SkPathBuilder } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet } from 'react-native';
import { useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';

import { smooth } from './TrophySavingsMilestoneMotion';
import { streamerEdge, streamerFrame, STREAMER_FEED_JOINT, type StreamerFrame, type StreamerPoint, type StreamerSpan, type StreamerStation } from './TrophySavingsMilestoneStreamerGeometry';

type Triangle = { indices: number[]; depth: number };
type Mesh = { vertices: StreamerPoint[]; colors: string[]; indices: number[]; triangles: Triangle[] };
type RibbonGeometry = { model: StreamerFrame; rear: Mesh; front: Mesh; coil: SkPath; cut: SkPath; cutForeground: boolean };
const FRONT = ['#FBA4BA', '#F57D9E', '#F57D9E', '#D9507C'];
const REVERSE = ['#D25D82', '#A92C56', '#A92C56', '#8E2048'];
const EDGE = ['#D25078', '#C34C70', '#C34C70', '#B13E64'];
const CUT_EDGE = ['#F2B1C5', '#E99AB5', '#BC4F78', '#8E2349'];
const ROLL_SEAM = ['#D46C88', '#D56D89', '#ED7A99', '#EE7B9A'];
const PINK = '#FF7F8C';
const BOWL_CLIP = Skia.PathBuilder.Make().moveTo(139, 120)
  .cubicTo(166, 105, 226, 105, 252, 120).cubicTo(252, 157, 240, 186, 214, 208)
  .cubicTo(208, 215, 202, 218, 195.5, 219).cubicTo(189, 218, 183, 216, 178, 211)
  .cubicTo(150, 190, 140, 156, 139, 120).close().detach();

function emptyMesh(): Mesh {
  'worklet';
  // A transparent degenerate triangle keeps the native vertices node valid at exact rest.
  return { vertices: [{ x: 0, y: 0 }, { x: 0, y: 0 }, { x: 0, y: 0 }], colors: ['transparent', 'transparent', 'transparent'], indices: [0, 1, 2], triangles: [{ indices: [0, 1, 2], depth: -1000 }] };
}
function stationColors(station: StreamerStation) {
  'worklet';
  const palette = Math.abs(station.facing) < 0.09 ? EDGE : station.facing >= 0 ? FRONT : REVERSE;
  const mix = station.joinBlend ?? 0;
  if (mix <= 0) return palette;
  return palette.map((color, index) => {
    const target = ROLL_SEAM[index];
    const channels = [1, 3, 5].map(offset => {
      const a = Number.parseInt(color.slice(offset, offset + 2), 16);
      const b = Number.parseInt(target.slice(offset, offset + 2), 16);
      return Math.round(a + (b - a) * mix);
    });
    return `rgb(${channels[0]},${channels[1]},${channels[2]})`;
  });
}
function appendSpan(mesh: Mesh, span: StreamerSpan) {
  'worklet';
  const base = mesh.vertices.length;
  const depths: number[] = [];
  for (const station of [span.a, span.b]) {
    const left = streamerEdge(station, 1);
    const right = streamerEdge(station, -1);
    // Broad face is flat matte. Only the sub-point cut edge changes tone.
    const edge = Math.min(0.08, 0.65 / Math.max(0.8, station.width));
    mesh.vertices.push(left,
      { x: left.x + (right.x - left.x) * edge, y: left.y + (right.y - left.y) * edge },
      { x: right.x + (left.x - right.x) * edge, y: right.y + (left.y - right.y) * edge }, right);
    const depth = station.depth ?? 0;
    const axisDepth = station.axisDepth ?? 0;
    depths.push(depth + axisDepth, depth + axisDepth * (1 - 2 * edge), depth - axisDepth * (1 - 2 * edge), depth - axisDepth);
    const palette = span.surface === 'edge' ? CUT_EDGE : stationColors(station);
    mesh.colors.push(palette[0], palette[1], palette[2], palette[3]);
  }
  for (let index = 0; index < 3; index += 1) {
    const a = [index, 4 + index, 5 + index];
    const b = [index, 5 + index, 1 + index];
    mesh.triangles.push({ indices: a.map(value => base + value), depth: (depths[a[0]] + depths[a[1]] + depths[a[2]]) / 3 },
      { indices: b.map(value => base + value), depth: (depths[b[0]] + depths[b[1]] + depths[b[2]]) / 3 });
  }
}

function coilPoint(point: StreamerPoint, scale: number, joint: StreamerPoint): StreamerPoint {
  'worklet';
  return { x: joint.x + (point.x - STREAMER_FEED_JOINT.x) * scale, y: joint.y + (point.y - STREAMER_FEED_JOINT.y) * scale };
}
function geometryFor(progress: number, pose: number, builder: SkPathBuilder, cutBuilder: SkPathBuilder): RibbonGeometry {
  'worklet';
  const model = streamerFrame(progress, pose);
  const rear = emptyMesh();
  const front = emptyMesh();
  for (const span of [...model.spans, ...(model.edgeSpans ?? [])]) appendSpan(span.foreground ? front : rear, span);
  for (const mesh of [rear, front]) {
    mesh.triangles.sort((a, b) => a.depth - b.depth);
    mesh.indices = [];
    for (const triangle of mesh.triangles) mesh.indices.push(...triangle.indices);
  }
  const end = model.coilJoint ?? STREAMER_FEED_JOINT;
  const a = coilPoint({ x: 44.08, y: 138.8 }, model.coilScale, end);
  const b = coilPoint({ x: 44.76, y: 172.05 }, model.coilScale, end);
  const c = coilPoint({ x: 40, y: 154 }, model.coilScale, end);
  const d = coilPoint({ x: 37.28, y: 141.65 }, model.coilScale, end);
  const coil = builder.reset().moveTo(a.x, a.y).quadTo(b.x, b.y, c.x, c.y).quadTo(d.x, d.y, end.x, end.y).detach();
  // Keep the retained node/hook shape stable across Fast Refresh; the cut is now a real depth-sorted edge face.
  cutBuilder.reset();
  return { model, rear, front, coil, cut: cutBuilder.detach(), cutForeground: false };
}

export function useTrophyRibbonGeometry(progress: SharedValue<number>, pose?: SharedValue<number>) {
  const builders = useMemo(() => ({ coil: Skia.PathBuilder.Make(), cut: Skia.PathBuilder.Make() }), []);
  return useDerivedValue(() => geometryFor(progress.get(), pose?.get() ?? progress.get(), builders.coil, builders.cut));
}

export function TrophySavingsMilestoneRibbon({ geometry, foreground, rollImage }: { geometry: DerivedValue<RibbonGeometry>; foreground: boolean; rollImage: SkImage | null }) {
  const vertices = useDerivedValue(() => (foreground ? geometry.get().front : geometry.get().rear).vertices);
  const colors = useDerivedValue(() => (foreground ? geometry.get().front : geometry.get().rear).colors);
  const indices = useDerivedValue(() => (foreground ? geometry.get().front : geometry.get().rear).indices);
  const coil = useDerivedValue(() => geometry.get().coil);
  const coilOpacity = useDerivedValue(() => geometry.get().model.coilOpacity);
  const coilWidth = useDerivedValue(() => geometry.get().model.coilWidth);
  const opacity = useDerivedValue(() => geometry.get().model.meshOpacity);
  const cut = useDerivedValue(() => geometry.get().cut);
  const cutOpacity = useDerivedValue(() => 0);
  const rollOpacity = useDerivedValue(() => {
    const roll = geometry.get().model.roll;
    // The cached face view is not a valid edge-on image: hide its long hood as it turns behind the cup.
    // The continuous material edge remains in the body mesh; this cannot create a second paper owner.
    return roll && roll.foreground === foreground
      ? roll.opacity * geometry.get().model.meshOpacity * smooth((roll.width - 3) / 6) : 0;
  });
  const rollTransform = useDerivedValue(() => {
    const roll = geometry.get().model.roll;
    if (!roll) return [{ translateX: 0 }, { translateY: 0 }, { rotate: 0 }, { scaleX: 0 }, { scaleY: 0 }];
    return [{ translateX: roll.center.x }, { translateY: roll.center.y },
      { rotate: Math.atan2(roll.tangent.y, roll.tangent.x) },
      { scaleX: geometry.get().model.tipLength / 36 }, { scaleY: roll.width / 34 }];
  });
  return (
    <Canvas pointerEvents="none" accessible={false} style={styles.canvas}>
      {!foreground && <Path path={coil} color={PINK} opacity={coilOpacity} style="stroke" strokeWidth={coilWidth} strokeCap="round" />}
      <Group opacity={opacity}>
        {foreground && <Group clip={BOWL_CLIP}><Group transform={[{ translateY: 1.2 }]}>
          <Vertices vertices={vertices} indices={indices} mode="triangles" color="rgba(92,41,31,0.13)" />
        </Group></Group>}
        <Vertices vertices={vertices} colors={colors} indices={indices} mode="triangles" />
        <Path path={cut} color="#E595AD" opacity={cutOpacity} style="stroke" strokeWidth={0.7} strokeCap="butt" />
      </Group>
      <Group transform={rollTransform} opacity={rollOpacity}>
        {rollImage && <SkiaImage image={rollImage} x={-16} y={-25} width={64} height={64} fit="fill" />}
      </Group>
    </Canvas>
  );
}

const styles = StyleSheet.create({ canvas: { position: 'absolute', left: 0, top: 0, width: 393, height: 350 } });
