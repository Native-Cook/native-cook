/**
 * The replica of the source ad: the lilac room and machine (one generated plate), the fruit inside the globe as
 * sprites in a settled pile that jostles when the knob turns, the glass's reflections over them, and the pot coming
 * out of the door. Nothing that the source does not show.
 */
import {
  Blur, BlurMask, Circle, Canvas, ColorMatrix, Group, Image, LinearGradient, Paint, Path, PathOp, RadialGradient, Rect, RoundedRect, Skia, useImage, vec,
  type Transforms3d,
} from '@shopify/react-native-skia';
import { useMemo, useState } from 'react';
import { StyleSheet, useWindowDimensions } from 'react-native';
import { useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';

import { PILE, REPLICA_CAMERA, REPLICA_HOLD, REPLICA_LOOP_MS, REPLICA_PARALLAX as PX, REPLICA_T, STEP } from './motion';
import { REPLICA_ART as A } from './theme';

type Pile = { x: number[]; y: number[]; vx: number[]; vy: number[]; hx: number[]; hy: number[]; r: number[] };

function clamp(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** One position-based step: springs to home (or gravity while settling), then circle and globe constraints. */
function stepPile(p: Pile, s: number, settling: boolean) {
  'worklet';
  const n = p.r.length;
  const nx: number[] = [];
  const ny: number[] = [];
  const damp = Math.exp(-PILE.damping * STEP);
  for (let i = 0; i < n; i++) {
    if (settling) p.vy[i] += PILE.settleGravity * s * STEP;
    else {
      p.vx[i] += -PILE.home * (p.x[i] - p.hx[i]) * STEP;
      p.vy[i] += -PILE.home * (p.y[i] - p.hy[i]) * STEP;
    }
    p.vx[i] *= damp;
    p.vy[i] *= damp;
    nx.push(p.x[i] + p.vx[i] * STEP);
    ny.push(p.y[i] + p.vy[i] * STEP);
  }
  const cx = A.globe.x * s;
  const cy = A.globe.y * s;
  const R = A.globe.r * s;
  for (let k = 0; k < PILE.passes; k++) {
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        const dx = nx[j] - nx[i];
        const dy = ny[j] - ny[i];
        const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
        const min = (p.r[i] + p.r[j]) * s * A.fruitPack;
        if (d >= min) continue;
        const c = (min - d) / d / 2;
        nx[i] -= dx * c;
        ny[i] -= dy * c;
        nx[j] += dx * c;
        ny[j] += dy * c;
      }
    }
    for (let i = 0; i < n; i++) {
      const dx = nx[i] - cx;
      const dy = ny[i] - cy;
      const d = Math.sqrt(dx * dx + dy * dy) || 1e-6;
      const lim = R - p.r[i] * s;
      if (d > lim) {
        nx[i] = cx + (dx / d) * lim;
        ny[i] = cy + (dy / d) * lim;
      }
      if (ny[i] > A.globe.floor * s - p.r[i] * s) ny[i] = A.globe.floor * s - p.r[i] * s;
    }
  }
  for (let i = 0; i < n; i++) {
    p.vx[i] = (nx[i] - p.x[i]) / STEP;
    p.vy[i] = (ny[i] - p.y[i]) / STEP;
    p.x[i] = nx[i];
    p.y[i] = ny[i];
  }
}

function settledPile(s: number): Pile {
  const p: Pile = { x: [], y: [], vx: [], vy: [], hx: [], hy: [], r: [] };
  A.fruit.forEach((f, i) => {
    p.r.push(f[1]);
    p.x.push((A.globe.x + ((i % 5) - 2) * 56 + (Math.floor(i / 5) % 2) * 14) * s);
    p.y.push((A.globe.y - 140 + Math.floor(i / 5) * 52) * s);
    p.vx.push(0);
    p.vy.push(0);
  });
  for (let k = 0; k < PILE.settleSteps; k++) stepPile(p, s, true);
  p.hx = p.x.slice();
  p.hy = p.y.slice();
  p.vx = p.vx.map(() => 0);
  p.vy = p.vy.map(() => 0);
  return p;
}

function easeOutBack(u: number) {
  'worklet';
  const c = 1.4;
  return 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
}

function smooth01(u: number) {
  'worklet';
  return u * u * (3 - 2 * u);
}
type Parallax = { a: readonly [number, number]; b: readonly [number, number] };
function parallaxT(o: Parallax, w: [number, number], width: number): Transforms3d {
  'worklet';
  const sc = 1 + (REPLICA_CAMERA.a[0] - 1) * w[0] + (REPLICA_CAMERA.b[0] - 1) * w[1];
  const k = width / 1206 / sc;
  return [{ translateX: (o.a[0] * w[0] + o.b[0] * w[1]) * k }, { translateY: (o.a[1] * w[0] + o.b[1] * w[1]) * k }];
}

export function GumballReplica() {
  const { width, height } = useWindowDimensions();
  const s = width / 402;
  const room = useImage(require('./assets/room.jpg'));
  const glass = useImage(require('./assets/globe-glass.png'));
  const fill = useImage(require('./assets/globe-fill.png'));
  const knobImg = useImage(require('./assets/knob.png'));
  const pot = useImage(require('./assets/pot.png'));
  const potAImg = useImage(require('./assets/pot-a.png'));
  // The source's push-in exposure and grade at poses a and b (baked from the reference, low frequency).
  const gradeMulA = useImage(require('./assets/grade-mul-a.png'));
  const gradeAddA = useImage(require('./assets/grade-add-a.png'));
  const gradeMulB = useImage(require('./assets/grade-mul-b.png'));
  const gradeAddB = useImage(require('./assets/grade-add-b.png'));
  const [initial] = useState(() => settledPile(s));
  const pile = useSharedValue(initial);
  const pos = useSharedValue<number[]>([]);
  const clock = useSharedValue(0);
  const kicked = useSharedValue(false);

  useFrameCallback((f) => {
    'worklet';
    const loop = REPLICA_LOOP_MS / 1000;
    const t = REPLICA_HOLD >= 0 ? REPLICA_HOLD : (clock.get() + (f.timeSincePreviousFrame ?? 0) / 1000) % loop;
    const p = pile.get();
    if (t < clock.get()) {
      // Loop seam: the pile is back in its slots; snap the remainder.
      for (let i = 0; i < p.r.length; i++) {
        p.x[i] = p.hx[i];
        p.y[i] = p.hy[i];
        p.vx[i] = 0;
        p.vy[i] = 0;
      }
      kicked.set(false);
    }
    if (!kicked.get() && t >= REPLICA_T.kick && REPLICA_HOLD < 0) {
      kicked.set(true);
      for (let i = 0; i < p.r.length; i++) {
        const low = p.y[i] > A.globe.y * s;
        p.vx[i] += (low ? 180 : 70) * s * (i % 2 === 0 ? 1 : -1);
        p.vy[i] -= (low ? 140 : 50) * s;
      }
    }
    const steps = Math.min(30, Math.max(1, Math.round((f.timeSincePreviousFrame ?? 16) / 1000 / STEP)));
    for (let k = 0; k < steps; k++) stepPile(p, s, false);
    pile.set(p);
    const out: number[] = [];
    for (let i = 0; i < p.r.length; i++) out.push(p.x[i], p.y[i]);
    pos.set(out);
    clock.set(t);
  });

  const knob = useDerivedValue<Transforms3d>(() => {
    const u = clamp((clock.value - REPLICA_T.turn[0]) / (REPLICA_T.turn[1] - REPLICA_T.turn[0]));
    const e = u * u * (3 - 2 * u);
    const c = A.knob.size / 2;
    return [{ translateX: (A.knob.x + c) * s }, { translateY: (A.knob.y + c) * s }, { rotate: A.knob.tilt + (Math.PI - A.knob.tilt) * e }];
  });
  // The tub comes out of the door to its pose in ref a (tilted, 4.3 s), then settles to ref b's (5.5 s).
  const potT = useDerivedValue<Transforms3d>(() => {
    const t = clock.value;
    const T = REPLICA_CAMERA.t;
    const P = A.potA;
    let x = A.exit.x;
    let y = A.exit.y;
    let sc = 0.3;
    let rot = 0;
    if (t >= T[0] && t < REPLICA_T.back[0]) {
      const u = easeOutBack(clamp((t - T[0]) / (T[1] - T[0])));
      x = A.exit.x + (P.cx - A.exit.x) * u; y = A.exit.y + (P.cy - A.exit.y) * u; sc = 0.3 + (P.scale - 0.3) * u; rot = P.rot * u;
      if (t >= T[1]) {
        const v = clamp((t - T[1]) / (T[2] - T[1]));
        const e = v * v * (3 - 2 * v);
        x = P.cx + (A.pot.cx - P.cx) * e; y = P.cy + (A.pot.cy - P.cy) * e; sc = P.scale + (1 - P.scale) * e; rot = P.rot * (1 - e);
      }
    } else if (t >= REPLICA_T.back[0] && t < REPLICA_T.back[1]) {
      const u = 1 - clamp((t - REPLICA_T.back[0]) / (REPLICA_T.back[1] - REPLICA_T.back[0]));
      x = A.exit.x + (A.pot.cx - A.exit.x) * u; y = A.exit.y + (A.pot.cy - A.exit.y) * u; sc = 0.3 + 0.7 * u;
    }
    return [{ translateX: x * s }, { translateY: y * s }, { rotate: rot }, { scale: sc }];
  });
  // The pile (one generated image) nudges and settles when the knob turns.
  const fillT = useDerivedValue<Transforms3d>(() => {
    const d = clock.value - REPLICA_T.kick;
    const k = d > 0 ? Math.exp(-A.jostle.decay * d) * Math.sin(2 * Math.PI * A.jostle.freq * d) : 0;
    return [{ translateX: A.jostle.amp * s * k * 0.6 }, { translateY: -A.jostle.amp * s * Math.abs(k) }];
  });
  // How far the camera is into its two poses: [weight of pose a, weight of pose b] (0, 0 at rest).
  const camW = useDerivedValue<[number, number]>(() => {
    const t = clock.value;
    const T = REPLICA_CAMERA.t;
    if (t >= T[0] && t < T[1]) return [smooth01((t - T[0]) / (T[1] - T[0])), 0];
    if (t >= T[1] && t < REPLICA_T.back[0]) {
      const u = smooth01(Math.min(1, (t - T[1]) / (T[2] - T[1])));
      return [1 - u, u];
    }
    if (t >= REPLICA_T.back[0] && t < REPLICA_T.back[1]) return [0, 1 - smooth01((t - REPLICA_T.back[0]) / (REPLICA_T.back[1] - REPLICA_T.back[0]))];
    return [0, 0];
  });
  // Parallax: what lies nearer or farther than the machine moves a little more or less than the camera's fit.
  // Offsets are screen px at each pose; inside the camera group they are divided by its scale.
  const wA = useDerivedValue(() => camW.value[0]);
  const wB = useDerivedValue(() => camW.value[1]);
  const addA = useDerivedValue(() => wA.value / 4); // the add maps are stored ×4
  const addB = useDerivedValue(() => wB.value / 4);
  const globeP = useDerivedValue<Transforms3d>(() => parallaxT(PX.globe, camW.value, width));
  const fruitRP = useDerivedValue<Transforms3d>(() => parallaxT(PX.fruitR, camW.value, width));
  const fruitLP = useDerivedValue<Transforms3d>(() => parallaxT(PX.fruitL, camW.value, width));
  // The source's camera push-in (screen px affines scaled to points).
  const camera = useDerivedValue<Transforms3d>(() => {
    const t = clock.value;
    const T = REPLICA_CAMERA.t;
    const P = REPLICA_CAMERA.a;
    const Q = REPLICA_CAMERA.b;
    let sc = 1;
    let tx = 0;
    let ty = 0;
    let u = 0;
    if (t >= T[0] && t < T[1]) {
      u = (t - T[0]) / (T[1] - T[0]);
      u = u * u * (3 - 2 * u);
      sc = 1 + (P[0] - 1) * u; tx = P[1] * u; ty = P[2] * u;
    } else if (t >= T[1] && t < REPLICA_T.back[0]) {
      u = Math.min(1, (t - T[1]) / (T[2] - T[1]));
      u = u * u * (3 - 2 * u);
      sc = P[0] + (Q[0] - P[0]) * u; tx = P[1] + (Q[1] - P[1]) * u; ty = P[2] + (Q[2] - P[2]) * u;
    } else if (t >= REPLICA_T.back[0] && t < REPLICA_T.back[1]) {
      u = (t - REPLICA_T.back[0]) / (REPLICA_T.back[1] - REPLICA_T.back[0]);
      u = 1 - u * u * (3 - 2 * u);
      sc = 1 + (Q[0] - 1) * u; tx = Q[1] * u; ty = Q[2] * u;
    }
    const k = width / 1206; // screen px → pt
    return [{ translateX: tx * k }, { translateY: ty * k }, { scale: sc }];
  });
  const defocus = useDerivedValue(() => {
    const t = clock.value;
    const T = REPLICA_CAMERA.t;
    if (t < T[0] || t >= REPLICA_T.back[1]) return 0;
    if (t < T[1]) return (t - T[0]) / (T[1] - T[0]);
    if (t < REPLICA_T.back[0]) return 1;
    return 1 - (t - REPLICA_T.back[0]) / (REPLICA_T.back[1] - REPLICA_T.back[0]);
  });
  const potO = useDerivedValue(() => (clock.value >= REPLICA_T.out[0] && clock.value < REPLICA_T.back[1] ? 1 : 0));
  // Two traced views of the tub: as it rises (ref a, front-on) and settled (ref b, from above); cross-faded as it settles.
  const settle = useDerivedValue(() => {
    const T = REPLICA_CAMERA.t;
    const t = clock.value;
    if (t >= REPLICA_T.back[0]) return 1;
    const v = clamp((t - T[1]) / (T[2] - T[1]));
    return v * v * (3 - 2 * v);
  });
  const potAO = useDerivedValue(() => 1 - settle.value);
  const glarePath = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    const r = A.glare.r * s;
    b.addArc({ x: A.globe.x * s - r, y: A.globe.y * s - r, width: r * 2, height: r * 2 }, A.glare.from, A.glare.sweep);
    return b.build();
  }, [s]);
  const globeClip = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    b.addCircle(A.globe.x * s, A.globe.y * s, (A.globe.r - 4) * s);
    const floor = Skia.PathBuilder.Make();
    floor.addRect({ x: 0, y: 0, width: width, height: A.globe.floor * s });
    return Skia.Path.MakeFromOp(b.build(), floor.build(), PathOp.Intersect) ?? b.build();
  }, [s, width]);

  return (
    <Canvas style={StyleSheet.absoluteFill}>
      {/* Under the camera push-in: the wall's colour, so no edge of the room plate shows. */}
      <Rect x={0} y={0} width={width} height={height} color={A.wallFill} />
      <Group transform={camera} origin={vec(0, 0)}>
      <Image image={room} x={0} y={0} width={width} height={height} fit="fill" />
      {/* The table's fruit sits nearer than the machine, out of focus: feathered, softened patches of the plate, moved by parallax. */}
      <Group transform={fruitRP} layer>
        <Image image={room} x={0} y={0} width={width} height={height} fit="fill">
          <Blur blur={(PX.fruitR.blur / 1206) * width} />
        </Image>
        <Rect x={0} y={0} width={width} height={height} blendMode="dstIn">
          <RadialGradient c={vec((PX.fruitR.c[0] / 1206) * width, (PX.fruitR.c[1] / 1206) * width)} r={(PX.fruitR.r / 1206) * width} colors={['#000', '#000', 'rgba(0,0,0,0)']} positions={[0, 0.6, 1]} />
        </Rect>
      </Group>
      <Group transform={fruitLP} layer>
        <Image image={room} x={0} y={0} width={width} height={height} fit="fill">
          <Blur blur={(PX.fruitL.blur / 1206) * width} />
        </Image>
        <Rect x={0} y={0} width={width} height={height} blendMode="dstIn">
          <RadialGradient c={vec((PX.fruitL.c[0] / 1206) * width, (PX.fruitL.c[1] / 1206) * width)} r={(PX.fruitL.r / 1206) * width} colors={['#000', '#000', 'rgba(0,0,0,0)']} positions={[0, 0.6, 1]} />
        </Rect>
      </Group>
      {/* The door: a light lavender recessed panel. */}
      <RoundedRect x={A.door.x * s} y={A.door.y * s} width={A.door.w * s} height={A.door.h * s} r={A.door.r * s}>
        <LinearGradient start={vec(0, A.door.y * s)} end={vec(0, (A.door.y + A.door.h) * s)} colors={[A.door.top, A.door.bottom]} />
      </RoundedRect>
      <RoundedRect x={A.door.x * s} y={A.door.y * s} width={A.door.w * s} height={A.door.h * s} r={A.door.r * s} style="stroke" strokeWidth={6 * s} color={A.door.shade}>
        <BlurMask blur={5 * s} style="inner" />
      </RoundedRect>
      {/* Fruit inside the globe only, a little soft behind the glass; then the glass's haze and reflections. */}
      <Group transform={globeP}>
      <Group clip={globeClip}>
        <Group transform={fillT}>
          <Image image={fill} x={0} y={0} width={width} height={height} fit="fill">
            <Blur blur={A.fillBlur * s} />
          </Image>
        </Group>
        <Rect x={0} y={0} width={width} height={height} color={A.haze} />
      </Group>
      <Image image={glass} x={0} y={0} width={width} height={height} fit="fill" />
      <Path path={glarePath} style="stroke" strokeWidth={A.glare.width * s} strokeCap="round" color={A.glare.color}>
        <BlurMask blur={A.glare.blur * s} style="normal" />
      </Path>
      <Circle cx={A.globe.x * s} cy={A.globe.y * s} r={(A.globe.r - 2) * s} style="stroke" strokeWidth={A.rim.width * s} color={A.rim.color} />
      </Group>
      <Group transform={knob}>
        <Image image={knobImg} x={(-A.knob.size / 2) * s} y={(-A.knob.size / 2) * s} width={A.knob.size * s} height={A.knob.size * s} fit="fill">
          <ColorMatrix matrix={A.knobMatrix} />
          <Blur blur={A.knobBlur * s} />
        </Image>
      </Group>
      </Group>
      {/* The source refocuses on the pot while the camera pushes in: a pre-blurred copy, crossfaded. */}
      <Group opacity={defocus} layer={<Paint><Blur blur={A.defocus * s} /><ColorMatrix matrix={A.pushGrade} /></Paint>}>
        <Group transform={camera} origin={vec(0, 0)}>
        <Image image={room} x={0} y={0} width={width} height={height} fit="fill" />
      {/* The table's fruit sits nearer than the machine, out of focus: feathered, softened patches of the plate, moved by parallax. */}
      <Group transform={fruitRP} layer>
        <Image image={room} x={0} y={0} width={width} height={height} fit="fill">
          <Blur blur={(PX.fruitR.blur / 1206) * width} />
        </Image>
        <Rect x={0} y={0} width={width} height={height} blendMode="dstIn">
          <RadialGradient c={vec((PX.fruitR.c[0] / 1206) * width, (PX.fruitR.c[1] / 1206) * width)} r={(PX.fruitR.r / 1206) * width} colors={['#000', '#000', 'rgba(0,0,0,0)']} positions={[0, 0.6, 1]} />
        </Rect>
      </Group>
      <Group transform={fruitLP} layer>
        <Image image={room} x={0} y={0} width={width} height={height} fit="fill">
          <Blur blur={(PX.fruitL.blur / 1206) * width} />
        </Image>
        <Rect x={0} y={0} width={width} height={height} blendMode="dstIn">
          <RadialGradient c={vec((PX.fruitL.c[0] / 1206) * width, (PX.fruitL.c[1] / 1206) * width)} r={(PX.fruitL.r / 1206) * width} colors={['#000', '#000', 'rgba(0,0,0,0)']} positions={[0, 0.6, 1]} />
        </Rect>
      </Group>
        {/* The door: a light lavender recessed panel. */}
        <RoundedRect x={A.door.x * s} y={A.door.y * s} width={A.door.w * s} height={A.door.h * s} r={A.door.r * s}>
          <LinearGradient start={vec(0, A.door.y * s)} end={vec(0, (A.door.y + A.door.h) * s)} colors={[A.door.top, A.door.bottom]} />
        </RoundedRect>
        <RoundedRect x={A.door.x * s} y={A.door.y * s} width={A.door.w * s} height={A.door.h * s} r={A.door.r * s} style="stroke" strokeWidth={6 * s} color={A.door.shade}>
          <BlurMask blur={5 * s} style="inner" />
        </RoundedRect>
        {/* Fruit inside the globe only, a little soft behind the glass; then the glass's haze and reflections. */}
        <Group transform={globeP}>
      <Group clip={globeClip}>
          <Group transform={fillT}>
            <Image image={fill} x={0} y={0} width={width} height={height} fit="fill">
              <Blur blur={A.fillBlur * s} />
            </Image>
          </Group>
          <Rect x={0} y={0} width={width} height={height} color={A.haze} />
        </Group>
        <Image image={glass} x={0} y={0} width={width} height={height} fit="fill" />
        <Path path={glarePath} style="stroke" strokeWidth={A.glare.width * s} strokeCap="round" color={A.glare.color}>
          <BlurMask blur={A.glare.blur * s} style="normal" />
        </Path>
        <Circle cx={A.globe.x * s} cy={A.globe.y * s} r={(A.globe.r - 2) * s} style="stroke" strokeWidth={A.rim.width * s} color={A.rim.color} />
      </Group>
        <Group transform={knob}>
          <Image image={knobImg} x={(-A.knob.size / 2) * s} y={(-A.knob.size / 2) * s} width={A.knob.size * s} height={A.knob.size * s} fit="fill">
            <ColorMatrix matrix={A.knobMatrix} />
          </Image>
        </Group>
        </Group>
      </Group>
      {/* The push-in's light over the scene (not the tub): multiply, then add. */}
      <Image image={gradeMulA} x={0} y={0} width={width} height={height} fit="fill" blendMode="multiply" opacity={wA} />
      <Image image={gradeMulB} x={0} y={0} width={width} height={height} fit="fill" blendMode="multiply" opacity={wB} />
      <Image image={gradeAddA} x={0} y={0} width={width} height={height} fit="fill" blendMode="plus" opacity={addA} />
      <Image image={gradeAddB} x={0} y={0} width={width} height={height} fit="fill" blendMode="plus" opacity={addB} />
      <Group transform={potT} opacity={potO}>
        <Group opacity={potAO}>
          <Image image={potAImg} x={(-A.pot.w / 2) * s} y={(-A.pot.h / 2) * s} width={A.pot.w * s} height={A.pot.h * s} fit="fill" />
        </Group>
        <Group opacity={settle}>
          <Image image={pot} x={(-A.pot.w / 2) * s} y={(-A.pot.h / 2) * s} width={A.pot.w * s} height={A.pot.h * s} fit="fill" />
        </Group>
      </Group>
    </Canvas>
  );
}
