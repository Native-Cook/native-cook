import {
  Blur,
  Canvas,
  Group,
  Image as SkiaImage,
  LinearGradient,
  Paint,
  Path,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  useImage,
  vec,
  Vertices,
  type DataSourceParam,
  type SkPath,
  type SkImage,
  type SkRect,
} from '@shopify/react-native-skia';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { paperFrame, paperOn, type Geo, type PaperFrame, type Pt } from './fold';
import { GOO_SKSL, gooFrame, gooOn, type GooFrame, type GooGeo } from './goo';
import { CONTRAIL, GOO, WARM } from './motion';
import { colors } from './theme';

/** Paper colour: the button's own gradient, printed on both sides of the sheet. */
const STOP_COLORS = colors.inviteStops.map((s) => s[1]);
const STOP_POS = colors.inviteStops.map((s) => s[0]);

/** Contrail: the ribbon is cut into segments, each a little more opaque toward the dart. */
const TRAIL_SEGS = 6;

function poly(points: number[][]): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(points[0][0], points[0][1]);
  for (let i = 1; i < points.length; i++) b.lineTo(points[i][0], points[i][1]);
  b.close();
  return b.build();
}

type Img = { image: DataSourceParam; cx: number; cy: number; d: number; head: number; dy: number };
/** A droplet's face: the head image, its scale in a circle (× diameter) and offset, the droplet's radius. */
export type DropImg = { image: DataSourceParam; head: number; dy: number; mirror: boolean; r: number };

type Props = {
  tInv: SharedValue<number>;
  tScan: SharedValue<number>;
  reduced: boolean;
  geo: Geo;
  pill: number;
  goo: GooGeo;
  gooColors: number[][];
  faces: Img[];
  drops: DropImg[];
  width: number;
  height: number;
};

/**
 * The shape of one paper face. The two halves of the sheet are the button's rounded rectangle (its
 * corner radius animates) clipped to the left or right half, so no path is rebuilt per frame; the
 * dart's faces are fixed polygons.
 */
type FaceShape = { path: SkPath } | { clip: SkRect | SharedValue<SkRect>; r: SharedValue<number>; W: number; H: number };

function FaceBody({ shape, color, opacity, children }: { shape: FaceShape; color?: string; opacity?: SharedValue<number>; children?: ReactNode }) {
  if ('path' in shape)
    return (
      <Path path={shape.path} color={color} opacity={opacity}>
        {children}
      </Path>
    );
  return (
    <RoundedRect x={0} y={0} width={shape.W} height={shape.H} r={shape.r} color={color} opacity={opacity}>
      {children}
    </RoundedRect>
  );
}

// Matrices are passed as plain 3 × 3 arrays (Skia reads number[9]), so no SkMatrix is made per frame.
function Face({ i, frame, shape, W }: { i: number; frame: SharedValue<PaperFrame>; shape: FaceShape; W: number }) {
  const matrix = useDerivedValue(() => frame.get().mats[i]);
  const opacity = useDerivedValue(() => frame.get().vis[i]);
  const dark = useDerivedValue(() => frame.get().dark[i]);
  const bright = useDerivedValue(() => frame.get().bright[i]);
  return (
    <Group matrix={matrix} opacity={opacity} clip={'clip' in shape ? shape.clip : undefined}>
      <FaceBody shape={shape}>
        <LinearGradient start={vec(0, 0)} end={vec(W, 0)} colors={STOP_COLORS} positions={STOP_POS} />
      </FaceBody>
      <FaceBody shape={shape} color="#000000" opacity={dark} />
      <FaceBody shape={shape} color="#FFFFFF" opacity={bright} />
    </Group>
  );
}

function FaceShadow({ i, frame, shape }: { i: number; frame: SharedValue<PaperFrame>; shape: FaceShape }) {
  const matrix = useDerivedValue(() => frame.get().shadows[i]);
  const opacity = useDerivedValue(() => frame.get().vis[i]);
  return (
    <Group matrix={matrix} clip={'clip' in shape ? shape.clip : undefined}>
      <FaceBody shape={shape} color="#000000" opacity={opacity} />
    </Group>
  );
}

/** Three points on one spot: an empty contrail draws nothing. */
const NO_TRAIL: Pt[] = [
  { x: 0, y: 0 },
  { x: 0, y: 0 },
  { x: 0, y: 0 },
];

/** One stretch of the contrail ribbon: its edge points as a triangle strip (plain points, no path per frame). */
function TrailSegment({ j, frame }: { j: number; frame: SharedValue<PaperFrame> }) {
  const K = CONTRAIL.samples;
  const a = Math.floor((j * (K - 1)) / TRAIL_SEGS);
  const b = Math.floor(((j + 1) * (K - 1)) / TRAIL_SEGS);
  const vertices = useDerivedValue(() => {
    const pts = frame.get().trail;
    return pts.length === 0 ? NO_TRAIL : pts.slice(2 * a, 2 * b + 2);
  });
  const alpha = (CONTRAIL.opacityHead * (j + 1)) / TRAIL_SEGS;
  return <Vertices vertices={vertices} mode="triangleStrip" color={colors.planeInk} opacity={alpha} />;
}

function AvatarFace({ i, frame, f, image }: { i: number; frame: SharedValue<GooFrame>; f: Img; image: SkImage | null }) {
  const transform = useDerivedValue(() => {
    const s = frame.get().faces[i];
    return [{ translateX: s.tx }, { translateY: s.ty }, { scale: s.s }];
  });
  const opacity = useDerivedValue(() => (frame.get().on ? frame.get().faces[i].o : 0));
  const r = f.d / 2;
  const clip = useMemo(() => Skia.RRectXY(Skia.XYWHRect(f.cx - r, f.cy - r, f.d, f.d), r, r), [f.cx, f.cy, f.d, r]);
  const img = f.d * f.head;
  if (!image) return null;
  return (
    <Group transform={transform} origin={vec(f.cx, f.cy)} opacity={opacity} clip={clip}>
      <SkiaImage image={image} x={f.cx - img / 2} y={f.cy - img / 2 + f.dy * f.d} width={img} height={img} fit="cover" />
    </Group>
  );
}

/** A contact's face riding in droplet j: clipped to the droplet, scaled with it, faded as it merges. */
function DropFace({ j, frame, f, image }: { j: number; frame: SharedValue<GooFrame>; f: DropImg; image: SkImage | null }) {
  const transform = useDerivedValue(() => {
    const d = frame.get().drops[j];
    return [{ translateX: d.x }, { translateY: d.y }, { scale: d.s }];
  });
  const opacity = useDerivedValue(() => frame.get().drops[j].o);
  const clip = useMemo(() => Skia.RRectXY(Skia.XYWHRect(-f.r, -f.r, 2 * f.r, 2 * f.r), f.r, f.r), [f.r]);
  const img = 2 * f.r * f.head;
  if (!image) return null;
  return (
    <Group transform={transform} opacity={opacity} clip={clip}>
      <Group transform={f.mirror ? MIRROR : undefined}>
        <SkiaImage image={image} x={-img / 2} y={-img / 2 + f.dy * 2 * f.r} width={img} height={img} fit="cover" />
      </Group>
    </Group>
  );
}
const MIRROR = [{ scaleX: -1 }];

/** Everything that leaves the layout: the folded paper and its flight, and the liquid contacts. */
export function Overlay({ tInv, tScan, reduced, geo, pill, goo, gooColors, faces, drops, width, height }: Props) {
  const W = geo.W;
  const H = geo.H;
  const c = H / 2;

  // The four heads, decoded once and shared by the avatars and the droplets (one texture each).
  const heads = [useImage(faces[0].image), useImage(faces[1].image), useImage(faces[2].image), useImage(faces[3].image)];
  const headOf = (src: DataSourceParam) => heads[Math.max(0, faces.findIndex((f) => f.image === src))];
  const headsReady = heads.every((h) => h !== null);

  // Warm-up (WARM): once the heads are decoded, a few frames of a full pose at an invisible alpha,
  // then the real clocks.
  const [warm, setWarm] = useState(true);
  useEffect(() => {
    if (!headsReady) return;
    let n = 0;
    let id = requestAnimationFrame(function tick() {
      n += 1;
      if (n >= WARM.frames) setWarm(false);
      else id = requestAnimationFrame(tick);
    });
    return () => cancelAnimationFrame(id);
  }, [headsReady]);

  // The clocks the canvas sees are pinned to 0 while it would draw nothing (a primitive that does
  // not change does not propagate), so the canvas stops redrawing at rest and during the press.
  const inv = useDerivedValue(() => {
    if (warm) return WARM.inv;
    const t = reduced ? 0 : tInv.get();
    return paperOn(t) ? t : 0;
  });
  const scan = useDerivedValue(() => {
    if (warm) return WARM.scan;
    const s = reduced ? 0 : tScan.get();
    return gooOn(s) ? s : 0;
  });
  const frame = useDerivedValue(() => paperFrame(inv.get(), geo, pill));

  // Faces 0 and 1 are the sheet's halves: its rounded rectangle (radius from the frame) clipped to the
  // left half (the whole sheet while it is flat) or the right half. 2…5 are the folded dart's polygons.
  const radius = useDerivedValue(() => Math.min(frame.get().radius, c));
  const halves = useMemo(
    () => ({ full: Skia.XYWHRect(0, 0, W, H), left: Skia.XYWHRect(0, 0, W / 2, H), right: Skia.XYWHRect(W / 2, 0, W / 2, H) }),
    [W, H],
  );
  const leftClip = useDerivedValue<SkRect>(() => (frame.get().flat ? halves.full : halves.left));
  // The dart in the left half's own coordinates (it lies mirrored on the right half): nose at x 0.
  const dart = useMemo(
    () => [
      poly([
        [W / 2, 0],
        [0, c],
        [W / 2, c],
      ]),
      poly([
        [W / 2, c],
        [0, c],
        [W / 2, H],
      ]),
      poly([
        [W / 2, 0],
        [0, 0],
        [0, c],
      ]),
      poly([
        [W / 2, H],
        [0, H],
        [0, c],
      ]),
    ],
    [W, H, c],
  );
  const shapes: FaceShape[] = [
    { clip: leftClip, r: radius, W, H },
    { clip: halves.right, r: radius, W, H },
    ...dart.map((path) => ({ path })),
  ];
  const order = [1, 0, 4, 5, 2, 3];

  const shadowOpacity = useDerivedValue(() => frame.get().shadowOpacity);
  const shadowBlur = useDerivedValue(() => frame.get().shadowBlur);
  const trailOpacity = useDerivedValue(() => frame.get().trailOpacity);

  /* ----- liquid contacts ----- */
  const effect = useMemo(() => Skia.RuntimeEffect.Make(GOO_SKSL), []);
  const gf = useDerivedValue(() => gooFrame(scan.get(), goo, gooColors));
  const uniforms = useDerivedValue(() => gf.get().uniforms);
  let top = Infinity;
  let bottom = -Infinity;
  for (let i = 0; i < goo.rest.length; i++) {
    top = Math.min(top, goo.rest[i].y - goo.rest[i].r);
    bottom = Math.max(bottom, goo.rest[i].y + goo.rest[i].r);
  }
  const bandY = top - GOO.bandPad;
  const bandH = bottom - top + 2 * GOO.bandPad;
  const gooH = useDerivedValue(() => (gf.get().on ? bandH : 0));

  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Group opacity={warm ? WARM.alpha : 1}>
      <Group
        layer={
          <Paint opacity={shadowOpacity}>
            <Blur blur={shadowBlur} />
          </Paint>
        }
      >
        {order.map((i) => (
          <FaceShadow key={i} i={i} frame={frame} shape={shapes[i]} />
        ))}
      </Group>
      <Group
        layer={
          <Paint opacity={trailOpacity}>
            <Blur blur={CONTRAIL.blur} />
          </Paint>
        }
      >
        {Array.from({ length: TRAIL_SEGS }, (_, j) => (
          <TrailSegment key={j} j={j} frame={frame} />
        ))}
      </Group>
      {order.map((i) => (
        <Face key={i} i={i} frame={frame} shape={shapes[i]} W={W} />
      ))}
      {effect ? (
        <Rect x={0} y={bandY} width={width} height={gooH}>
          <Shader source={effect} uniforms={uniforms} />
        </Rect>
      ) : null}
      {drops.map((f, j) => (
        <DropFace key={`d${j}`} j={j} frame={gf} f={f} image={headOf(f.image)} />
      ))}
      {faces.map((f, i) => (
        <AvatarFace key={i} i={i} frame={gf} f={f} image={heads[i]} />
      ))}
      </Group>
    </Canvas>
  );
}
