/**
 * The Skia layers: the page with its type, the pendant and the ball chain. They take plain numbers or animated
 * values, so the same elements draw on screen and in the offline renders.
 *
 * The pendant is drawn in its own frame (origin at the top of the cap, y down the cord, 1 unit = 1 pt at 402 pt
 * wide): green enamel outside, white enamel inside, a globe bulb hanging into the opening. Day and Night versions of
 * the shell are stacked and cross-faded by the theme front; the inside and the bulb follow the filament's heat.
 */
import {
  BlurMask, Circle, Group, LinearGradient, Oval, Paragraph, Path, RadialGradient, Rect, RoundedRect, Shader, Skia, vec,
  type SkPath, type Transforms3d, type Uniforms,
} from '@shopify/react-native-skia';
import type { DerivedValue, SharedValue } from 'react-native-reanimated';

import type { PageText, Role } from './page-text';
import { SHADERS } from './shaders';
import { CHAIN, LAMP, SHADE, SWITCH_BODY } from './theme';

type Anim<T> = T | SharedValue<T> | DerivedValue<T>;

const L = LAMP;
const RIM_TOP = L.rimY - L.rimRy;

// The shell: a deep enamel dome flaring from the cap to the rim.
const DOME = Skia.Path.MakeFromSVGString(
  `M -8 ${L.capHeight} C -11 28 -${L.rimHalf * 0.75} ${L.rimY * 0.52} -${L.rimHalf} ${L.rimY} L ${L.rimHalf} ${L.rimY} C ${L.rimHalf * 0.75} ${L.rimY * 0.52} 11 28 8 ${L.capHeight} Z`,
)!;
// A window reflected down the left of the dome.
const SPEC = Skia.Path.MakeFromSVGString(`M -6 16 C -10 30 -${L.rimHalf * 0.48} ${L.rimY * 0.56} -${L.rimHalf * 0.62} ${L.rimY - 6}`)!;
// The rolled rim's near half.
const LIP = (() => {
  const b = Skia.PathBuilder.Make();
  // Inset by half the stroke, so the lip never pokes past the shell's silhouette.
  const i = L.lip / 2;
  b.addArc({ x: -L.rimHalf + i, y: RIM_TOP + i, width: 2 * (L.rimHalf - i), height: 2 * (L.rimRy - i) }, 0, 180);
  return b.build();
})();
// The bulb shows through the opening and below the rim, never through the shell.
const BULB_CLIP = (() => {
  const b = Skia.PathBuilder.Make();
  b.addOval({ x: -L.rimHalf + 2, y: RIM_TOP + 1, width: 2 * L.rimHalf - 4, height: 2 * L.rimRy - 2 });
  b.addRect({ x: -L.rimHalf, y: L.rimY, width: 2 * L.rimHalf, height: 60 });
  return b.build();
})();
// The nickel bell pull, hanging from the chain's end (origin), pointing down.
const PULL = Skia.Path.MakeFromSVGString(
  `M -1.6 0 L 1.6 0 L 1.8 4 C 2.6 8 ${CHAIN.pullWidth / 2} 13 ${CHAIN.pullWidth / 2} 19 Q ${CHAIN.pullWidth / 2} ${CHAIN.pullHeight} 0 ${CHAIN.pullHeight} Q -${CHAIN.pullWidth / 2} ${CHAIN.pullHeight} -${CHAIN.pullWidth / 2} 19 C -${CHAIN.pullWidth / 2} 13 -2.6 8 -1.8 4 Z`,
)!;
const PULL_SPEC = Skia.Path.MakeFromSVGString('M -1.2 5 C -1.8 9 -3 13 -3 18')!;
// A window caught low on the cold glass, following its curve.
const GLASS_SPEC = Skia.Path.MakeFromSVGString(`M -${L.bulbR * 0.72} ${L.bulbY + 2} Q -${L.bulbR * 0.7} ${L.bulbY + L.bulbR * 0.62} -${L.bulbR * 0.3} ${L.bulbY + L.bulbR * 0.86}`)!;

function Shell({ c }: { c: typeof SHADE.day }) {
  return (
    <Group>
      <Path path={DOME}>
        <LinearGradient start={vec(-L.rimHalf, 0)} end={vec(L.rimHalf, 0)} colors={[c.hi, c.mid, c.mid, c.low]} positions={[0, 0.3, 0.62, 1]} />
      </Path>
      <Path path={DOME}>
        <LinearGradient
          start={vec(0, L.capHeight)}
          end={vec(0, L.rimY)}
          colors={['rgba(0,0,0,0.32)', 'rgba(0,0,0,0)', 'rgba(255,255,255,0.06)']}
          positions={[0, 0.5, 1]}
        />
      </Path>
      <Path path={SPEC} style="stroke" strokeWidth={3} strokeCap="round" color={c.spec}>
        <BlurMask blur={1.4} style="normal" />
      </Path>
      <RoundedRect x={-L.capHalf} y={0} width={2 * L.capHalf} height={L.capHeight + 1} r={2}>
        <LinearGradient start={vec(-L.capHalf, 0)} end={vec(L.capHalf, 0)} colors={[c.hi, c.mid, c.low]} />
      </RoundedRect>
    </Group>
  );
}

const SB = SWITCH_BODY;
const SB_LEFT = SB.x - SB.width / 2;
const SB_FOOT = SB.top + SB.height;

/** The pull switch's knurled barrel; the chain leaves through the hole in its foot. */
function Housing({ c }: { c: readonly string[] }) {
  const knurls = [];
  for (let i = 0; i < SB.knurls; i++) {
    const y = SB.top + 6 + i * 2;
    knurls.push(<Rect key={i} x={SB_LEFT} y={y} width={SB.width} height={0.8} color="rgba(0,0,0,0.28)" />);
  }
  return (
    <Group>
      <RoundedRect x={SB_LEFT} y={SB.top} width={SB.width} height={SB.height} r={2.5}>
        <LinearGradient start={vec(SB_LEFT, 0)} end={vec(SB_LEFT + SB.width, 0)} colors={[...c]} positions={[0, 0.35, 0.65, 1]} />
      </RoundedRect>
      {knurls}
      {/* The foot: a turned collar and its hole. */}
      <RoundedRect x={SB_LEFT - 0.6} y={SB_FOOT - 4} width={SB.width + 1.2} height={4} r={1.6}>
        <LinearGradient start={vec(SB_LEFT, 0)} end={vec(SB_LEFT + SB.width, 0)} colors={[...c]} positions={[0, 0.3, 0.6, 1]} />
      </RoundedRect>
      <Oval x={SB.x - 1.6} y={SB_FOOT - 1.2} width={3.2} height={1.6} color="rgba(0,0,0,0.75)" />
    </Group>
  );
}

export type LampLook = {
  front: Anim<number>; // 0 dark .. 1 lit: the shell's lighting, from the theme front and the filament
  inside: Anim<number>; // opacity of the lit inside and bulb
  bloom: Anim<number>;
  insideColors: Anim<string[]>;
  bulbColors: Anim<string[]>;
  lampColor: Anim<string>;
};

export function Lamp({ transform, look }: { transform: Anim<Transforms3d>; look: LampLook }) {
  return (
    <Group transform={transform}>
      {/* The fabric cord up to the ceiling. */}
      <Rect x={-1.1} y={-L.cord} width={2.2} height={L.cord + 1} color={SHADE.night.cord} />
      <Group opacity={look.front}>
        <Rect x={-1.1} y={-L.cord} width={2.2} height={L.cord + 1} color={SHADE.day.cord} />
      </Group>
      <Shell c={SHADE.night} />
      <Group opacity={look.front}>
        <Shell c={SHADE.day} />
      </Group>
      {/* The lit page's warm light reaching the dome's lower outside. */}
      <Path path={DOME} opacity={look.bloom}>
        <LinearGradient start={vec(0, L.rimY * 0.45)} end={vec(0, L.rimY)} colors={['rgba(255,204,150,0)', SHADE.bounce]} />
      </Path>
      {/* Inside: cold enamel, then the lit enamel over it. */}
      <Oval x={-L.rimHalf + 1} y={RIM_TOP + 0.5} width={2 * L.rimHalf - 2} height={2 * L.rimRy - 1}>
        <RadialGradient c={vec(0, L.rimY)} r={L.rimHalf} colors={[SHADE.coldCentre, SHADE.coldEdge]} />
      </Oval>
      <Oval x={-L.rimHalf + 1} y={RIM_TOP + 0.5} width={2 * L.rimHalf - 2} height={2 * L.rimRy - 1} opacity={look.inside}>
        <RadialGradient c={vec(0, L.rimY)} r={L.rimHalf} colors={look.insideColors} />
      </Oval>
      {/* The switch hangs inside the shade, behind its near lip. */}
      <Group clip={BULB_CLIP}>
        <Housing c={SWITCH_BODY.night} />
        <Group opacity={look.front}>
          <Housing c={SWITCH_BODY.day} />
        </Group>
      </Group>
      <Path path={LIP} style="stroke" strokeWidth={L.lip} color={SHADE.night.lip} />
      <Group opacity={look.front}>
        <Path path={LIP} style="stroke" strokeWidth={L.lip} color={SHADE.day.lip} />
      </Group>
      {/* The lip's inner edge catching the bulb. */}
      <Path path={LIP} style="stroke" strokeWidth={L.lip * 0.6} color={SHADE.lipLit} opacity={look.inside} />
      <Group clip={BULB_CLIP}>
        <Circle cx={0} cy={L.bulbY} r={L.bulbR}>
          <RadialGradient c={vec(-6, L.bulbY + 6)} r={L.bulbR * 1.3} colors={[SHADE.glassCold, SHADE.glassColdEdge]} />
        </Circle>
        <Path path={GLASS_SPEC} style="stroke" strokeWidth={1} strokeCap="round" color="rgba(255,255,255,0.13)" />
        <Circle cx={0} cy={L.bulbY} r={L.bulbR} opacity={look.inside}>
          <RadialGradient c={vec(0, L.bulbY + 5)} r={L.bulbR} colors={look.bulbColors} positions={[0, 0.35, 0.8, 1]} />
        </Circle>
        {/* The hot core seen through the frosted glass. */}
        <Circle cx={0} cy={L.bulbY + 5} r={L.bulbR * 0.55} color="white" opacity={look.bloom}>
          <BlurMask blur={5} style="normal" />
        </Circle>
      </Group>
      {/* The bulb's bloom, only below the rim (the shell hides the rest). */}
      <Group clip={{ x: -L.rimHalf, y: RIM_TOP + 2, width: 2 * L.rimHalf, height: 80 }} opacity={look.bloom}>
        <Circle cx={0} cy={L.bulbY + 8} r={L.bulbR + 6} color={look.lampColor}>
          <BlurMask blur={12} style="normal" />
        </Circle>
      </Group>
    </Group>
  );
}

export type ChainLook = {
  rims: Anim<SkPath>;
  bodies: Anim<SkPath>;
  specs: Anim<SkPath>;
  wire: Anim<SkPath>;
  pull: Anim<Transforms3d>;
  front: Anim<number>;
};

function Nickel({ c, rims, bodies, specs, wire, pull }: { c: typeof CHAIN.day } & Omit<ChainLook, 'front'>) {
  return (
    <Group>
      <Path path={wire} style="stroke" strokeWidth={CHAIN.wire} color={c.wire} />
      <Path path={rims} color={c.rim} />
      <Path path={bodies} color={c.body} />
      <Path path={specs} color={c.spec} />
      <Group transform={pull}>
        <Path path={PULL}>
          <LinearGradient
            start={vec(-CHAIN.pullWidth / 2, 0)}
            end={vec(CHAIN.pullWidth / 2, 0)}
            colors={[c.rim, c.body, c.spec, c.body, c.rim]}
            positions={[0, 0.22, 0.36, 0.62, 1]}
          />
        </Path>
        <Path path={PULL_SPEC} style="stroke" strokeWidth={0.9} strokeCap="round" color={c.spec} />
      </Group>
    </Group>
  );
}

export function Chain({ look }: { look: ChainLook }) {
  const { front, ...parts } = look;
  return (
    <Group>
      <Nickel c={CHAIN.night} {...parts} />
      <Group opacity={front}>
        <Nickel c={CHAIN.day} {...parts} />
      </Group>
    </Group>
  );
}

const SOURCE = { ink: SHADERS.ink, mute: SHADERS.mute, accent: SHADERS.accent } as const;

/** The haze and dust in the light under the shade, screened over the page. */
export function Air({ w, h, uniforms }: { w: number; h: number; uniforms: Anim<Uniforms> }) {
  return (
    <Rect x={0} y={0} width={w} height={h} blendMode="screen">
      <Shader source={SHADERS.air} uniforms={uniforms} />
    </Rect>
  );
}

/** The paper, then each colour role's glyphs as a mask, coloured by the light field. */
export function Page({ w, h, page, uniforms }: { w: number; h: number; page: PageText | null; uniforms: Anim<Uniforms> }) {
  const roles: Role[] = ['mute', 'ink', 'accent'];
  return (
    <Group>
      <Rect x={0} y={0} width={w} height={h}>
        <Shader source={SHADERS.paper} uniforms={uniforms} />
      </Rect>
      {page
        ? roles.map((role) => (
            <Group key={role} layer>
              {page.lines.filter((l) => l.role === role).map((l, i) => (
                <Paragraph key={i} paragraph={l.p} x={l.x} y={l.y} width={l.w} />
              ))}
              {page.bars.filter((b) => b.role === role).map((b, i) => (
                <Rect key={`b${i}`} x={b.x} y={b.y} width={b.w} height={b.h} color={`rgba(0,0,0,${b.alpha})`} />
              ))}
              <Rect x={0} y={0} width={w} height={h} blendMode="srcIn">
                <Shader source={SOURCE[role]} uniforms={uniforms} />
              </Rect>
            </Group>
          ))
        : null}
    </Group>
  );
}

/**
 * The chain as drawn: every simulated ball where it is (a dark rim, a lit body offset toward the light, a specular
 * point), and the wire between balls, which shows in the gaps. `p` holds the balls, then the housing's foot: balls
 * pulled out of the housing by the switch's travel are drawn between the foot and ball 0; inside it, none are.
 */
export function chainPaths(p: readonly number[], count: number, s: number) {
  'worklet';
  const rims = Skia.PathBuilder.Make();
  const bodies = Skia.PathBuilder.Make();
  const specs = Skia.PathBuilder.Make();
  const wire = Skia.PathBuilder.Make();
  const r = CHAIN.ball * s;
  const pitch = CHAIN.pitch * s;
  const ball = (x: number, y: number) => {
    rims.addCircle(x, y, r);
    bodies.addCircle(x - r * 0.16, y - r * 0.18, r * 0.74);
    specs.addCircle(x - r * 0.36, y - r * 0.4, r * 0.28);
  };
  const fx = p[count * 2];
  const fy = p[count * 2 + 1];
  const dx = p[0] - fx;
  const dy = p[1] - fy;
  const out = Math.sqrt(dx * dx + dy * dy);
  wire.moveTo(fx, fy);
  // Balls already out of the housing, from the foot down to ball 0.
  for (let k = Math.floor(out / pitch); k >= 1; k--) {
    const d = out - k * pitch;
    if (d > r) ball(fx + (dx / out) * d, fy + (dy / out) * d);
  }
  if (out > r) ball(p[0], p[1]);
  wire.lineTo(p[0], p[1]);
  for (let i = 1; i < count; i++) {
    const x = p[i * 2];
    const y = p[i * 2 + 1];
    wire.lineTo(x, y);
    ball(x, y);
  }
  return { rims: rims.build(), bodies: bodies.build(), specs: specs.build(), wire: wire.build() };
}
