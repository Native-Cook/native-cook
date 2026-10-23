/**
 * Canvas: the paper, the picker card with its five beads, and the jar, all drawn in code. The jar is clear soda-lime
 * glass with a thick base: its back and body behind the beads, the beads (squeezed toward the walls as a cylinder of
 * glass bends them), then the front: the wall's thickness, the window's highlights, the base's slab, and the light it
 * focuses on the paper inside its shadow. A brushed steel lid lifts when a bead is in the air.
 */
import {
  BlurMask, Circle, Group, LinearGradient, Line, Oval, Path, PathOp, Rect, RoundedRect, Skia, vec,
  type SkPath, type Transforms3d,
} from '@shopify/react-native-skia';
import { useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';

import { Bead } from './bead';
import { POOL, type Geometry } from './jar-sim';
import { JAR_KNOCK, RIPPLE, THROW, TRAIL } from './motion';
import { COLORS, LAYOUT, MOODS } from './theme';

export type MoodValues = {
  orbs: SharedValue<number[]>; // per orb: x, y, spin, scale, opacity, mood, squash, vx, vy, flying, in front of the glass
  slots: SharedValue<number[]>; // picker orb scales
  lid: DerivedValue<Transforms3d>;
  ghost: SharedValue<number[]>;
  ripple: SharedValue<number[]>;
  aim: SharedValue<number[]>;
  tilt: SharedValue<number>;
};

type Props = { g: Geometry; v: MoodValues };

/** Glass thickness of the wall and the base (pt, before scale). */
const WALL = 5;
/** Numbers per orb in `orbs`. */
const STRIDE = 11;
const BASE = 14;

/** The jar's outline, grown outward by `d` (0: the inner surface the beads touch), its bottom by `db`. */
function jarPath(g: Geometry, d: number, db: number, neck: number): SkPath {
  const H = (g.right - g.left) / 2 + d;
  const m = g.mouthHalf + d;
  const cx = g.cx;
  const top = g.mouthY - neck;
  const bottom = g.floor + db;
  const c = g.corner + d;
  const sh = g.shoulderEnd;
  const k = (sh - g.mouthY) * 0.55;
  return Skia.PathBuilder.Make()
    .moveTo(cx - m, top)
    .lineTo(cx - m, g.mouthY)
    .cubicTo(cx - m, g.mouthY + k, cx - H, sh - k, cx - H, sh)
    .lineTo(cx - H, bottom - c)
    .quadTo(cx - H, bottom, cx - H + c, bottom)
    .lineTo(cx + H - c, bottom)
    .quadTo(cx + H, bottom, cx + H, bottom - c)
    .lineTo(cx + H, sh)
    .cubicTo(cx + H, sh - k, cx + m, g.mouthY + k, cx + m, g.mouthY)
    .lineTo(cx + m, top)
    .build();
}

export function MoodScene({ g, v }: Props) {
  const { w, h, s } = g;
  const rippleC = useDerivedValue(() => vec(v.ripple.value[0], v.ripple.value[1]));
  const rippleR = useDerivedValue(() => (LAYOUT.orb + RIPPLE.radius * v.ripple.value[2]) * s);
  const rippleO = useDerivedValue(() => v.ripple.value[4] * (1 - v.ripple.value[2]) * 0.55);
  const jarShake = useDerivedValue<Transforms3d>(() => {
    const on = v.ripple.value[4] ?? 0;
    const t = (v.ripple.value[2] ?? 1) * RIPPLE.duration;
    return [{ translateX: on > 0 ? JAR_KNOCK.amp * s * Math.exp(-JAR_KNOCK.decay * t) * Math.sin(2 * Math.PI * JAR_KNOCK.hz * t) : 0 }];
  });
  const rippleColor = useDerivedValue(() => MOODS[v.ripple.value[3]]?.light ?? '#FFFFFF');
  const aimPath = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    const a = v.aim.value;
    for (let i = 0; i + 1 < a.length; i += 2) b.addCircle(a[i], a[i + 1], (2.2 - i * 0.04) * s);
    return b.build();
  });
  // The window's reflection drifts a little with the phone's tilt.
  const shine = useDerivedValue<Transforms3d>(() => [{ translateX: v.tilt.value * 6 * s }]);

  const cardY = g.top + LAYOUT.pickerTop * s;
  const cardX = LAYOUT.side * s;
  const H = (g.right - g.left) / 2;
  const inner = jarPath(g, 0, 0, 8 * s);
  const outer = jarPath(g, WALL * s, BASE * s, 8 * s);
  const wallBand = Skia.Path.MakeFromOp(outer, inner, PathOp.Difference) ?? outer;
  const groundY = g.floor + BASE * s;
  const sideTop = g.shoulderEnd + 10 * s;
  const sideH = g.floor - g.corner - sideTop;
  const lidW = LAYOUT.lidWidth * s;
  const lidH = LAYOUT.lidHeight * s;

  return (
    <Group>
      <Rect x={0} y={0} width={w} height={h}>
        <LinearGradient start={vec(0, 0)} end={vec(w * 0.3, h)} colors={[COLORS.bg, COLORS.bgBottom]} />
      </Rect>

      {/* Picker card with its five beads. */}
      <RoundedRect x={cardX} y={cardY + 6 * s} width={w - cardX * 2} height={LAYOUT.pickerHeight * s} r={LAYOUT.cardRadius * s} color={COLORS.cardShadow}>
        <BlurMask blur={12} style="normal" />
      </RoundedRect>
      <RoundedRect x={cardX} y={cardY} width={w - cardX * 2} height={LAYOUT.pickerHeight * s} r={LAYOUT.cardRadius * s} color={COLORS.card} />
      <RoundedRect x={cardX + 0.5} y={cardY + 0.5} width={w - cardX * 2 - 1} height={LAYOUT.pickerHeight * s - 1} r={LAYOUT.cardRadius * s} color={COLORS.cardEdge} style="stroke" strokeWidth={1} />
      {MOODS.map((m, i) => (
        <SlotOrb key={m.name} index={i} x={g.slotX[i]} y={g.slotY} r={LAYOUT.pickerOrb * s} slots={v.slots} />
      ))}

      {/* A landing knocks the jar: it rocks a point or two and settles (glass has mass). */}
      <Group transform={jarShake}>
        {/* Its shadow falls down and right of the light; inside it, the light the base focuses. */}
        <Oval x={g.cx - H * 1.02} y={groundY - 9 * s} width={H * 2.3} height={22 * s} color={COLORS.floorShadow}>
          <BlurMask blur={10 * s} style="normal" />
        </Oval>
        <Oval x={g.cx - H * 0.35} y={groundY - 2 * s} width={H * 1.05} height={9 * s} color={COLORS.caustic}>
          <BlurMask blur={5 * s} style="normal" />
        </Oval>

        {/* Back: the far wall's tint and the far half of the mouth's rim. */}
        <Path path={outer} color={COLORS.glassBody} />
        <Oval x={g.cx - g.mouthHalf} y={g.mouthY - 5 * s} width={g.mouthHalf * 2} height={10 * s} color={COLORS.glassEdgeSoft} style="stroke" strokeWidth={1.5} />

        {Array.from({ length: POOL }, (_, i) => (
          <PoolOrb key={i} index={i} r={LAYOUT.orb * s} orbs={v.orbs} g={g} front={false} />
        ))}
        <Circle c={rippleC} r={rippleR} style="stroke" strokeWidth={2 * s} color={rippleColor} opacity={rippleO} />

        {/* Front: the wall's thickness, darker where you look through more glass. */}
        <Path path={wallBand} color={COLORS.glassEdgeSoft} />
        <Path path={outer} color={COLORS.glassEdge} style="stroke" strokeWidth={1.2} />
        <Path path={inner} color="rgba(255, 255, 255, 0.5)" style="stroke" strokeWidth={0.8} />
        {/* The base: a slab of glass, its top edge catching the light. */}
        <Group clip={outer}>
          <Rect x={g.cx - H - WALL * s} y={g.floor} width={(H + WALL * s) * 2} height={BASE * s}>
            <LinearGradient start={vec(0, g.floor)} end={vec(0, groundY)} colors={['rgba(255,255,255,0.55)', 'rgba(150,172,166,0.26)', 'rgba(110,132,126,0.36)']} positions={[0, 0.25, 1]} />
          </Rect>
        </Group>
        <Line p1={vec(g.cx - H + g.corner, g.floor + 0.5)} p2={vec(g.cx + H - g.corner, g.floor + 0.5)} color="rgba(255,255,255,0.8)" strokeWidth={1} />

        {/* The window's reflection on the wall: a tall soft streak, a hairline beside it, a faint one on the far side. */}
        <Group transform={shine}>
          <RoundedRect x={g.cx - H + 10 * s} y={sideTop} width={8 * s} height={sideH} r={4 * s}>
            <LinearGradient start={vec(0, sideTop)} end={vec(0, sideTop + sideH)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.78)', 'rgba(255,255,255,0.5)', 'rgba(255,255,255,0)']} positions={[0, 0.18, 0.7, 1]} />
            <BlurMask blur={1.5} style="normal" />
          </RoundedRect>
          <RoundedRect x={g.cx - H + 22 * s} y={sideTop + 30 * s} width={2 * s} height={sideH * 0.6} r={1 * s} color="rgba(255,255,255,0.55)" />
          <RoundedRect x={g.cx + H - 15 * s} y={sideTop + 20 * s} width={4 * s} height={sideH * 0.75} r={2 * s}>
            <LinearGradient start={vec(0, sideTop)} end={vec(0, sideTop + sideH)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.32)', 'rgba(255,255,255,0)']} />
          </RoundedRect>
        </Group>
        {/* The near half of the mouth's rim, in front of everything inside. */}
        <Path
          path={Skia.PathBuilder.Make().addArc({ x: g.cx - g.mouthHalf - WALL * s, y: g.mouthY - 6 * s, width: (g.mouthHalf + WALL * s) * 2, height: 12 * s }, 0, 180).build()}
          color="rgba(255,255,255,0.85)" style="stroke" strokeWidth={1.5}
        />

        <Path path={aimPath} color={COLORS.aim} />
        {Array.from({ length: POOL }, (_, i) => (
          <PoolOrb key={i} index={i} r={LAYOUT.orb * s} orbs={v.orbs} g={g} front />
        ))}

        {/* Lid: brushed steel, knurled, lit from the left; it lifts and tips while a bead is in the air. */}
        <Group transform={v.lid}>
          <RoundedRect x={g.lidX + 2 * s} y={g.lidY + lidH - 2 * s} width={lidW - 4 * s} height={6 * s} r={3 * s} color="rgba(40, 30, 20, 0.18)">
            <BlurMask blur={3} style="normal" />
          </RoundedRect>
          <RoundedRect x={g.lidX} y={g.lidY} width={lidW} height={lidH} r={7 * s}>
            <LinearGradient start={vec(g.lidX, 0)} end={vec(g.lidX + lidW, 0)} colors={COLORS.lid} positions={COLORS.lidStops} />
          </RoundedRect>
          {Array.from({ length: Math.floor(lidW / (4 * s)) - 3 }, (_, k) => (
            <Line key={k} p1={vec(g.lidX + (8 + k * 4) * s, g.lidY + 8 * s)} p2={vec(g.lidX + (8 + k * 4) * s, g.lidY + lidH - 5 * s)} color={COLORS.lidRidge} strokeWidth={0.75} />
          ))}
          <Line p1={vec(g.lidX + 6 * s, g.lidY + 1.5)} p2={vec(g.lidX + lidW - 6 * s, g.lidY + 1.5)} color="rgba(255,255,255,0.7)" strokeWidth={1} />
          <RoundedRect x={g.lidX} y={g.lidY} width={lidW} height={lidH} r={7 * s} color="rgba(30, 24, 16, 0.25)" style="stroke" strokeWidth={0.75} />
        </Group>
      </Group>
    </Group>
  );
}

function SlotOrb({ index, x, y, r, slots }: { index: number; x: number; y: number; r: number; slots: SharedValue<number[]> }) {
  // Regrows from 0.9 with a fade, never from scale(0).
  const t = useDerivedValue<Transforms3d>(() => [{ translateX: x }, { translateY: y }, { scale: 0.9 + 0.1 * (slots.value[index] ?? 1) }]);
  const o = useDerivedValue(() => slots.value[index] ?? 1);
  return (
    <Group transform={t} opacity={o}>
      <Oval x={-r * 0.8} y={r * 0.78} width={r * 1.6} height={r * 0.36} color="rgba(60, 45, 25, 0.16)">
        <BlurMask blur={2} style="normal" />
      </Oval>
      <Bead r={r} mood={index} />
    </Group>
  );
}

/**
 * One orb of the pool; it is reused, so its mood is read from the sim each frame. Inside the jar the glass bends it:
 * near a wall it narrows and moves out a little, as things do seen through a cylinder of glass.
 */
function PoolOrb({ index, r, orbs, g, front }: { index: number; r: number; orbs: SharedValue<number[]>; g: Geometry; front: boolean }) {
  const k = index * STRIDE;
  const half = (g.right - g.left) / 2;
  const t = useDerivedValue<Transforms3d>(() => {
    const o = orbs.value;
    const x = o[k] ?? -100;
    const y = o[k + 1] ?? -100;
    const q = o[k + 6] ?? 0; // soft-body squash: wider and shorter, or the spring's stretch
    const inside = !front && y > g.mouthY + r ? 1 : 0;
    const u = Math.max(-1, Math.min(1, (x - g.cx) / half));
    const lens = inside * (1 - 0.22 * u * u);
    return [
      { translateX: x + inside * 0.05 * (x - g.cx) }, { translateY: y }, { translateY: q * r }, { scaleX: inside ? lens : 1 },
      // No spin: a bead is round and the room's light does not turn with it.
      { scale: o[k + 3] ?? 1 }, { scaleX: 1 + q }, { scaleY: 1 - q },
    ];
  });
  const opacity = useDerivedValue(() => ((orbs.value[k + 10] ?? 0) === (front ? 1 : 0) ? orbs.value[k + 4] ?? 0 : 0));
  const mood = useDerivedValue(() => orbs.value[k + 5] ?? 0);
  return (
    <>
      {/* Motion trail while flying: copies where the orb was a few hundredths of a second ago. */}
      {front && TRAIL.alpha.map((a, n) => <TrailCopy key={n} orbs={orbs} k={k} tau={(n + 1) * TRAIL.gap} alpha={a} mood={mood} r={r} s={g.s} />)}
      <Group transform={t} opacity={opacity}>
        <Bead r={r} mood={mood} />
      </Group>
    </>
  );
}

function TrailCopy({ orbs, k, tau, alpha, mood, r, s }: {
  orbs: SharedValue<number[]>; k: number; tau: number; alpha: number; mood: DerivedValue<number>; r: number; s: number;
}) {
  const t = useDerivedValue<Transforms3d>(() => {
    const o = orbs.value;
    const gr = THROW.gravity * s;
    return [
      { translateX: (o[k] ?? 0) - (o[k + 7] ?? 0) * tau },
      { translateY: (o[k + 1] ?? 0) - (o[k + 8] ?? 0) * tau + 0.5 * gr * tau * tau },
      { scale: 1 - tau * 3 },
    ];
  });
  const op = useDerivedValue(() => ((orbs.value[k + 9] ?? 0) === 1 ? alpha : 0));
  const color = useDerivedValue(() => MOODS[Math.max(0, Math.min(MOODS.length - 1, Math.round(mood.value)))].base);
  return (
    <Group transform={t} opacity={op}>
      <Circle cx={0} cy={0} r={r} color={color} />
    </Group>
  );
}
