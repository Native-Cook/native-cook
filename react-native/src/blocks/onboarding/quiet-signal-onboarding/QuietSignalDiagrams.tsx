import { Blur, Canvas, Circle, Group, LinearGradient, Path, RadialGradient, Skia, vec } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { ease, segment } from './motion';

// Five monoline diagrams drawn in Skia on a 300-unit square, scaled to the screen.
// Every moving line is rebuilt on the UI thread from one continuous clock, so curves
// glide instead of stepping; a diagram that is off screen stops rebuilding its paths.

const TAU = Math.PI * 2;
const INK = '#F2F2F7';
const DIM = '#8E8E99';

const svg = (d: string) => Skia.Path.MakeFromSVGString(d)!;
// A face in profile looking right: crown, brow, nose, lips, chin, then the neck.
const HEAD = svg('M146 30C204 26 244 60 245 108L246 122C247 130 251 137 257 146L267 160C269 164 266 167 260 167L254 168C256 172 256 176 252 179C255 183 254 187 249 189C251 195 252 201 247 207C239 215 222 214 211 214L207 290');
const HEAD_FILL = svg('M146 30C204 26 244 60 245 108L246 122C247 130 251 137 257 146L267 160C269 164 266 167 260 167L254 168C256 172 256 176 252 179C255 183 254 187 249 189C251 195 252 201 247 207C239 215 222 214 211 214L207 290L120 290C125 250 118 230 104 214C70 176 80 40 146 30Z');
const PILL = svg('M44 54H130A10 10 0 0 1 130 74H44A10 10 0 0 1 44 54Z');
const DOC = svg('M40 156H150V272H40ZM40 168H150');
const PHONE_BODY = 'M104 26H196A20 20 0 0 1 216 46V254A20 20 0 0 1 196 274H104A20 20 0 0 1 84 254V46A20 20 0 0 1 104 26Z';
const PHONE = svg(`${PHONE_BODY}M132 26V30Q132 36 138 36H162Q168 36 168 30V26`);
const PHONE_CLIP = svg(PHONE_BODY);
const CROSS = svg('M150 10V290M10 150H290');
// Two long faint curves that sweep through the phone behind its waves.
const FAINT = svg('M70 250C110 230 130 120 160 80S200 40 230 30M80 260C150 240 170 120 200 90S240 60 260 50');
const EYE = svg('M228 138Q238 130 248 138Q238 146 228 138ZM238 135.5A2.5 2.5 0 1 1 237.9 135.5');
const KEY = svg('M3 3L10 10M8 12L12 8');
const EMBLEM = svg('M66 150H110C122 150 126 146 130 136L142 104C148 90 166 88 172 100C178 112 170 122 164 132L150 154C144 166 150 178 162 180C176 182 182 170 186 160L196 134C200 124 208 118 222 118');
const RING = svg('M150 66A84 84 0 1 1 149.99 66Z');

function empty() {
  'worklet';
  return Skia.PathBuilder.Make().build();
}

function wave(x0: number, x1: number, y: number, amp: number, length: number, phase: number, tapered = true) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  for (let x = x0; x <= x1; x += 4) {
    const taper = tapered ? Math.sin(((x - x0) / (x1 - x0)) * Math.PI) : 1;
    const v = y + Math.sin((x / length) * TAU + phase) * amp * taper;
    if (x === x0) p.moveTo(x, v); else p.lineTo(x, v);
  }
  return p.build();
}

function bars(t: number, x0: number, count: number, gap: number, y: number, max: number, seed: number) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  for (let i = 0; i < count; i++) {
    const centre = 1 - Math.abs(i - (count - 1) / 2) / ((count - 1) / 2 + 1);
    const h = 3 + max * centre * (0.45 + 0.55 * (0.5 + 0.25 * Math.sin(t * 5.1 + i * 1.7 + seed) + 0.25 * Math.sin(t * 3.3 + i * 0.9 + seed * 2)));
    p.moveTo(x0 + i * gap, y - h / 2); p.lineTo(x0 + i * gap, y + h / 2);
  }
  return p.build();
}

/** Outer half circle facing `angle`, closed by an inner arc pulled back toward the centre: a moon. */
function crescent(cx: number, cy: number, r: number, angle: number, thickness: number) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  const a0 = angle - Math.PI / 2, dx = Math.cos(angle), dy = Math.sin(angle);
  for (let i = 0; i <= 28; i++) {
    const a = a0 + Math.PI * (i / 28);
    const x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
    if (i === 0) p.moveTo(x, y); else p.lineTo(x, y);
  }
  for (let i = 28; i >= 0; i--) {
    const s = i / 28, a = a0 + Math.PI * s, pull = Math.sin(s * Math.PI) * thickness;
    p.lineTo(cx + Math.cos(a) * r - dx * pull, cy + Math.sin(a) * r - dy * pull);
  }
  return p.build();
}

function arc(cx: number, cy: number, r: number, from: number, to: number) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  const steps = Math.max(2, Math.ceil(Math.abs(to - from) / 0.08));
  for (let i = 0; i <= steps; i++) {
    const a = from + (to - from) * (i / steps);
    if (i === 0) p.moveTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r); else p.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
  }
  return p.build();
}

type Props = { time: SharedValue<number>; live: SharedValue<boolean>; reduced: boolean };

/** Stage 0 · a busy mind: moons swing through the head, a notification hums, a page keeps filling. */
function BusyMind({ time, live, reduced }: Props) {
  const moons = useDerivedValue(() => {
    if (!live.get() && !reduced) return empty();
    const v = reduced ? 2 : time.get();
    const p = Skia.PathBuilder.Make();
    p.addPath(crescent(168, 116, 50, 0.3 + Math.sin(v * 0.9) * 0.5, 24));
    p.addPath(crescent(160, 152, 36, 3.5 + Math.sin(v * 0.7 + 1.2) * 0.55, 14));
    return p.build();
  });
  const ripples = useDerivedValue(() => {
    if (!live.get() && !reduced) return empty();
    const v = reduced ? 2 : time.get();
    const p = Skia.PathBuilder.Make();
    for (let i = 0; i < 3; i++) { const a = v * (0.5 + i * 0.18) + i * 2; p.addPath(arc(160, 150, 34 + i * 12, a, a + 1.6 + i * 0.4)); }
    return p.build();
  });
  const hum = useDerivedValue(() => (!live.get() && !reduced) ? empty() : wave(54, 122, 64, 3.2, 12, -(reduced ? 2 : time.get()) * 7));
  const lines = useDerivedValue(() => {
    const v = reduced ? 2 : time.get();
    const p = Skia.PathBuilder.Make();
    for (let i = 0; i < 5; i++) {
      const grow = reduced ? 1 : 0.5 + 0.5 * Math.sin(v * 1.3 - i * 0.7);
      p.moveTo(52, 192 + i * 13); p.lineTo(52 + [86, 74, 86, 58, 70][i] * (0.35 + 0.65 * grow), 192 + i * 13);
    }
    return p.build();
  });
  return <Group>
    <Path path={HEAD} style="stroke" strokeWidth={1.5} color={INK} opacity={0.9} />
    <Path path={PILL} style="stroke" strokeWidth={1.3} color={DIM} />
    <Path path={hum} style="stroke" strokeWidth={1.3} color={INK} opacity={0.85} />
    <Path path={DOC} style="fill" color="#050507" />
    <Path path={DOC} style="stroke" strokeWidth={1.4} color={INK} opacity={0.9} />
    <Path path={lines} style="stroke" strokeWidth={1.2} color={DIM} strokeCap="round" />
    <Path path={ripples} style="stroke" strokeWidth={1} color={DIM} opacity={0.7} />
    <Path path={moons} style="stroke" strokeWidth={1.5} color={INK} strokeJoin="round" />
    {[0, 1, 2].map(i => <ThoughtDot key={i} index={i} time={time} reduced={reduced} />)}
  </Group>;
}

function ThoughtDot({ index, time, reduced }: { index: number; time: SharedValue<number>; reduced: boolean }) {
  // The three dots light one after another, like someone about to reply.
  const fill = useDerivedValue(() => { if (reduced) return index === 0 ? 1 : 0; const k = (time.get() * 0.8 + 1 - index / 3) % 1; return k < 0.33 ? 1 : 0; });
  return <Group>
    <Circle cx={202 + index * 13} cy={146} r={4.2} style="stroke" strokeWidth={1.3} color={INK} opacity={0.85} />
    <Circle cx={202 + index * 13} cy={146} r={3.6} color={INK} opacity={fill} />
  </Group>;
}

/** Stage 1 · attention: a lit profile, two waves crossing in a slow figure of eight, a voice between them. */
function Attention({ time, live, reduced }: Props) {
  const waves = useDerivedValue(() => {
    if (!live.get() && !reduced) return empty();
    const v = reduced ? 1.5 : time.get();
    // Two long sines in antiphase cross twice across the frame, slowly breathing.
    const amp = 64 + Math.sin(v * 0.8) * 8;
    const p = Skia.PathBuilder.Make();
    p.addPath(wave(14, 286, 150, amp, 248, Math.PI / 2 + v * 0.5, false));
    p.addPath(wave(14, 286, 150, amp, 248, -Math.PI / 2 + v * 0.5, false));
    return p.build();
  });
  const voice = useDerivedValue(() => (!live.get() && !reduced) ? empty() : bars(reduced ? 1.5 : time.get(), 120, 13, 5, 150, 30, 0));
  return <Group>
    <Circle cx={205} cy={150} r={120}><RadialGradient c={vec(205, 150)} r={120} colors={['#FFFFFF30', '#FFFFFF14', '#FFFFFF00']} positions={[0, 0.7, 1]} /></Circle>
    <Group opacity={0.22}>
      <Path path={CROSS} style="stroke" strokeWidth={0.8} color={DIM} />
      {Array.from({ length: 49 }, (_, i) => <Circle key={i} cx={30 + (i % 7) * 40} cy={30 + Math.floor(i / 7) * 40} r={1.1} color={INK} />)}
    </Group>
    <Path path={HEAD_FILL}><LinearGradient start={vec(150, 0)} end={vec(258, 0)} colors={['#00000000', '#FFFFFF10', '#FFFFFFD8']} positions={[0, 0.45, 1]} /></Path>
    <Path path={HEAD} style="stroke" strokeWidth={1.4} color={INK} opacity={0.75} />
    <Path path={waves} style="stroke" strokeWidth={1.5} color={INK} opacity={0.9} />
    <Path path={voice} style="stroke" strokeWidth={2} color={INK} strokeCap="round" />
  </Group>;
}

function Ripple({ side, index, time, reduced }: { side: number; index: number; time: SharedValue<number>; reduced: boolean }) {
  // Soft rings of light swell out of each speaker and fade, like sound in a dark room.
  const k = useDerivedValue(() => reduced ? 0.3 + index * 0.25 : ((time.get() * 0.5 + index / 3) % 1));
  const r = useDerivedValue(() => 16 + k.get() * 40);
  const opacity = useDerivedValue(() => (reduced ? 0.35 : 0.75 * Math.sin(Math.min(1, k.get() * 1.1) * Math.PI)));
  const cx = side < 0 ? 46 : 254;
  return <Group clip={Skia.XYWHRect(side < 0 ? 0 : 254, 0, 46, 300)}><Circle cx={cx} cy={150} r={r} opacity={opacity}><RadialGradient c={vec(cx, 150)} r={60} colors={['#FFFFFF00', '#FFFFFF24', '#FFFFFF00']} positions={[0.25, 0.62, 1]} /></Circle></Group>;
}

/** Stage 2 · anywhere: a phone carries the waves; sound ripples out on both sides. */
function Anywhere({ time, live, reduced }: Props) {
  const waves = useDerivedValue(() => {
    if (!live.get() && !reduced) return empty();
    const v = reduced ? 1 : time.get();
    const p = Skia.PathBuilder.Make();
    for (let i = 0; i < 3; i++) {
      // The swell slowly trades shallow ripples for a broad S, as on the reference.
      const broad = 0.5 + 0.5 * Math.sin(v * 0.55 + i * 0.9);
      p.addPath(wave(84, 216, 150, 18 + 10 * broad, 66 + 30 * broad, v * 1.2 + i * (TAU / 3), false));
    }
    return p.build();
  });
  const left = useDerivedValue(() => (!live.get() && !reduced) ? empty() : bars(reduced ? 1 : time.get(), 28, 8, 5, 150, 26, 1));
  const right = useDerivedValue(() => (!live.get() && !reduced) ? empty() : bars(reduced ? 1 : time.get(), 236, 8, 5, 150, 26, 4));
  return <Group>
    {[-1, 1].map(side => [0, 1, 2].map(i => <Ripple key={`${side}${i}`} side={side} index={i} time={time} reduced={reduced} />))}
    <Path path={PHONE} style="fill" color="#060608" />
    <Path path={PHONE} style="stroke" strokeWidth={1.6} color={INK} opacity={0.9} />
    <Group clip={PHONE_CLIP}>
      <Path path={FAINT} style="stroke" strokeWidth={1} color={INK} opacity={0.16} />
      <Path path={waves} style="stroke" strokeWidth={1.4} color={INK} opacity={0.85} />
    </Group>
    <Path path={left} style="stroke" strokeWidth={2} color={INK} strokeCap="round" />
    <Path path={right} style="stroke" strokeWidth={2} color={INK} strokeCap="round" />
  </Group>;
}

/** Stage 3 · rhythm: a soft pulse of rings in the mind, and streams of light passing through. */
function Rhythm({ time, live, reduced }: Props) {
  const pulse = useDerivedValue(() => [{ translateX: 132 }, { translateY: 158 }, { scale: reduced ? 1 : 1 + Math.sin(time.get() * 1.6) * 0.06 }]);
  const streams = useDerivedValue(() => {
    if (!live.get() && !reduced) return empty();
    const v = reduced ? 2 : time.get();
    const p = Skia.PathBuilder.Make();
    for (let i = 0; i < 4; i++) {
      const y = 60 + i * 30, sway = Math.sin(v * 0.7 + i) * 8;
      p.moveTo(20, y + 70); p.cubicTo(80, y + 20 + sway, 150, y + 10 - sway, 230, y - 30 + sway);
    }
    // Arcs that wrap the back of the head, as on the reference.
    for (let i = 0; i < 4; i++) { const a = -2.2 + Math.sin(v * 0.4 + i) * 0.1; p.addPath(arc(150, 150, 96 + i * 12, a, a + 1.5 - i * 0.2)); }
    return p.build();
  });
  const flow = useDerivedValue(() => (reduced ? 0 : (time.get() * 0.18) % 1));
  const flowEnd = useDerivedValue(() => Math.min(1, flow.get() + 0.45));
  return <Group>
    <Group transform={pulse}>
      {[0, 1, 2, 3, 4].map(i => <Circle key={i} cx={0} cy={0} r={70 - i * 12} opacity={0.4 + i * 0.12}><RadialGradient c={vec(0, 0)} r={70 - i * 12} colors={['#FFFFFF02', '#FFFFFF22', '#FFFFFF0C']} positions={[0.3, 0.88, 1]} /></Circle>)}
    </Group>
    <Group transform={[{ translateX: 132 }, { translateY: 158 }]}>
      <Circle cx={0} cy={0} r={11} style="stroke" strokeWidth={1.4} color={INK} />
      <Path path={KEY} style="stroke" strokeWidth={1.4} color={INK} strokeCap="round" />
    </Group>
    <Path path={streams} style="stroke" strokeWidth={1} color={INK} opacity={0.16} />
    <Path path={streams} style="stroke" strokeWidth={1.2} color={INK} opacity={0.5} start={flow} end={flowEnd}><Blur blur={0.4} /></Path>
    <Path path={HEAD} style="stroke" strokeWidth={1.5} color={INK} opacity={0.9} />
    <Path path={EYE} style="stroke" strokeWidth={1.3} color={INK} />
  </Group>;
}

const ARC_KNOTS = [28.333, 28.5, 28.667, 28.833, 29, 29.167, 29.333, 29.45];
function knot(t: number, values: number[]) {
  'worklet';
  let i = 0;
  while (i < ARC_KNOTS.length - 2 && t > ARC_KNOTS[i + 1]) i++;
  const k = Math.min(1, Math.max(0, (t - ARC_KNOTS[i]) / (ARC_KNOTS[i + 1] - ARC_KNOTS[i])));
  return values[i] + (values[i + 1] - values[i]) * k;
}

/** Stage 4 · welcome: an arc sweeps round, closes into a ring, and the signal line draws itself inside. */
function Emblem({ time, reduced }: Props) {
  const arcPath = useDerivedValue(() => {
    const v = time.get();
    return arc(150, 150, 84, knot(v, [180, 215, 280, 325, 405, 500, 595, 650]) * Math.PI / 180, knot(v, [180.1, 325, 415, 490, 560, 605, 630, 650.1]) * Math.PI / 180);
  });
  const arcAlpha = useDerivedValue(() => reduced || time.get() < 28.333 || time.get() > 29.5 ? 0 : Math.min(1, segment(time.get(), 28.333, 28.6) * 1.6));
  const ringAlpha = useDerivedValue(() => reduced ? 1 : ease(segment(time.get(), 29.88, 30.167)));
  const draw = useDerivedValue(() => reduced ? 1 : ease(segment(time.get(), 30.5, 31.8)));
  const drawAlpha = useDerivedValue(() => (draw.get() > 0.001 ? 1 : 0));
  return <Group>
    <Path path={arcPath} style="stroke" strokeWidth={6} strokeCap="round" color={INK} opacity={arcAlpha} />
    <Path path={RING} style="stroke" strokeWidth={6} color={INK} opacity={ringAlpha} />
    <Path path={EMBLEM} style="stroke" strokeWidth={6} strokeCap="round" strokeJoin="round" color={INK} end={draw} opacity={drawAlpha} />
  </Group>;
}

const STAGES = [BusyMind, Attention, Anywhere, Rhythm, Emblem];

export function QuietSignalDiagram({ stage, time, live, reduced, size }: { stage: number; time: SharedValue<number>; live: SharedValue<boolean>; reduced: boolean; size: number }) {
  const Stage = STAGES[stage];
  return <Canvas style={{ width: size, height: size }} pointerEvents="none">
    <Group transform={[{ scale: size / 300 }]}><Stage time={time} live={live} reduced={reduced} /></Group>
  </Canvas>;
}
