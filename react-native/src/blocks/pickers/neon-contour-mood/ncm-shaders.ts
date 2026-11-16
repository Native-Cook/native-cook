import { comp, ref, type Mood } from './ncm-theme';

const f = (v: number) => (Number.isInteger(v) ? `${v}.0` : `${v}`);
const NOTCH_REF = ref.material.notch;
const STRIDE = 8;
const RAMP_STRIDE = 12;

/** Moods as flat numbers the UI runtime can read: shapes (8 per mood) and ramps (4 colours x 3 channels per mood, sRGB 0..1). */
export type MoodPack = { n: number; shapes: number[]; ramps: number[] };
export function packMoods(moods: readonly Mood[]): MoodPack {
  const shapes: number[] = [];
  const ramps: number[] = [];
  for (const m of moods) {
    const s = m.shape;
    const widthY = s.notchWidth ?? 50;
    shapes.push(s.w, s.h, s.corner, s.notchY ?? 0, widthY, s.notchX ?? 0, s.notchXWidth ?? widthY, m.warmth ?? 0);
    for (const h of m.ramp) for (const i of [1, 3, 5]) ramps.push(parseInt(h.slice(i, i + 2), 16) / 255);
  }
  return { n: moods.length, shapes, ramps };
}
/** Position of each ring on the colour ramp, from its offset: hot ridge at 1, mid band at 2, outer glow at 3. */
const RING_STOP = comp.rings.offsets.map((off) => {
  const [, , hot, , mid, , outer] = comp.rings.offsets;
  return off < hot ? off / hot : off < mid ? 1 + (off - hot) / (mid - hot) : 2 + (off - mid) / (outer - mid);
});

function lin(n: number) {
  'worklet';
  return n <= 0.04045 ? n / 12.92 : Math.pow((n + 0.055) / 1.055, 2.4);
}
/** The ramp colour at `stop` for a (fractional) mood, blended like the shape: written to out[o..o+2] in linear light or sRGB. */
export function rampAt(out: number[], o: number, pack: MoodPack, mood: number, stop: number, linear: boolean) {
  'worklet';
  const last = pack.n - 1;
  const m = mood < 0 ? 0 : mood > last ? last : mood;
  const i = Math.floor(m);
  const j = Math.min(last, i + 1);
  const fm = m - i;
  const u = fm * fm * (3 - 2 * fm);
  const k = stop < 0 ? 0 : stop > 3 ? 3 : stop;
  const a = Math.floor(k);
  const b = Math.min(3, a + 1);
  const v = k - a;
  const r = pack.ramps;
  for (let c = 0; c < 3; c++) {
    const l = r[i * RAMP_STRIDE + a * 3 + c] + (r[i * RAMP_STRIDE + b * 3 + c] - r[i * RAMP_STRIDE + a * 3 + c]) * v;
    const h = r[j * RAMP_STRIDE + a * 3 + c] + (r[j * RAMP_STRIDE + b * 3 + c] - r[j * RAMP_STRIDE + a * 3 + c]) * v;
    const s = l + (h - l) * u;
    out[o + c] = linear ? lin(s) : s;
  }
}

/** Uniform block of the contour shader; one object, mutated in place every frame. */
export type FieldUniforms = {
  center: number[];
  scale: number;
  shapeA: number[];
  shapeB: number[];
  tints: number[];
  lowerTints: number[];
  coreTint: number[];
  ground: number[];
  coreCol: number[];
  lean: number[];
  wake: number;
  breath: number;
  radiate: number;
  warm: number;
  pulse: number;
  cutoff: number;
  fade: number;
  spacing: number;
  rings: number;
  p3: number;
};
const rgbArray = (hex: string) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
export function newField(ground: string, core: string): FieldUniforms {
  return {
    center: [0, 0],
    scale: 1,
    // valid (the rest shape) from the first draw, so a frame drawn before the first fill never divides by zero
    shapeA: [0, 1, 2, 3, 4, 5, 6].flatMap(() => [ref.material.coreWidth, ref.material.coreHeight, ref.material.corner, ref.material.notch]),
    shapeB: [0, 1, 2, 3, 4, 5, 6].flatMap(() => [ref.material.notchWidth, 0, ref.material.notchWidth, 0]),
    tints: new Array<number>(21).fill(0),
    lowerTints: new Array<number>(21).fill(0),
    coreTint: [0, 0, 0],
    ground: rgbArray(ground),
    coreCol: rgbArray(core),
    lean: [0, 0],
    wake: 0,
    breath: 0,
    radiate: 0,
    warm: 0,
    pulse: -1,
    cutoff: 1e6,
    fade: 1,
    spacing: 26,
    rings: 7,
    p3: 0,
  };
}
/** Per-ring shape parameters and tints from the seven ring moods. Palette work is uniform across the screen: seven times a frame, not per pixel. */
export function packField(u: FieldUniforms, echoes: number[], pack: MoodPack) {
  'worklet';
  const last = pack.n - 1;
  const sh = pack.shapes;
  for (let k = 0; k < 7; k++) {
    const e = echoes[k];
    const m = e < 0 ? 0 : e > last ? last : e;
    const a = Math.floor(m);
    const b = Math.min(last, a + 1);
    const fm = m - a;
    const w = fm * fm * (3 - 2 * fm);
    const ia = a * STRIDE;
    const ib = b * STRIDE;
    const o = k * 4;
    for (let c = 0; c < 4; c++) u.shapeA[o + c] = sh[ia + c] + (sh[ib + c] - sh[ia + c]) * w;
    u.shapeB[o] = sh[ia + 4] + (sh[ib + 4] - sh[ia + 4]) * w;
    u.shapeB[o + 1] = sh[ia + 5] + (sh[ib + 5] - sh[ia + 5]) * w;
    u.shapeB[o + 2] = sh[ia + 6] + (sh[ib + 6] - sh[ia + 6]) * w;
    u.shapeB[o + 3] = 0;
    rampAt(u.tints, k * 3, pack, e, RING_STOP[k], true);
    rampAt(u.lowerTints, k * 3, pack, e, Math.min(RING_STOP[k], 2), true);
    if (k === 0) {
      rampAt(u.coreTint, 0, pack, e, 0, true);
      u.warm = sh[ia + 7] + (sh[ib + 7] - sh[ia + 7]) * w;
    }
  }
}

/** One field. All distances are reference pt. p3 = 1 only on iOS Graphite; CanvasKit uses 0.
 * shapeA[k] = (half-width, half-height, corner, y notch), shapeB[k] = (y notch width, x notch, x notch width) of ring k's mood;
 * tints/lowerTints: ring colours in linear light; lean: core offset toward the finger (pt), ring k takes lean * (1 - k / 7);
 * radiate: outward travel of ring 6 (pt); pulse: seconds since a tap (-1 = none); wake/breath/warm/spacing: see the packer.
 * The rest SDF uses a notched rounded box because four circles cannot reproduce the source's long straight sides.
 */
export const CONTOUR_SKSL = `
uniform float2 center;
uniform float scale;
uniform float4 shapeA[7];
uniform float4 shapeB[7];
uniform float3 tints[7];
uniform float3 lowerTints[7];
uniform float3 coreTint;
uniform float3 ground;
uniform float3 coreCol;
uniform float2 lean;
uniform float wake;
uniform float breath;
uniform float radiate;
uniform float warm;
uniform float pulse;
uniform float cutoff;
uniform float fade;
uniform float spacing;
uniform float rings;
uniform float p3;

float distanceAt(float2 p, float4 a, float4 b) {
  float2 q0=abs(p);
  float2 pp=float2(q0.x+b.y*exp(-q0.y*q0.y/(b.z*b.z)), q0.y+a.w*exp(-q0.x*q0.x/(b.x*b.x)));
  float2 q=pp-a.xy+float2(a.z);
  return length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-a.z;
}
float3 linearize(float3 c) { return mix(c/12.92,pow((c+0.055)/1.055,float3(2.4)),step(float3(0.04045),c)); }
float3 encode(float3 c) { c=max(c,float3(0.0)); return mix(c*12.92,1.055*pow(c,float3(1.0/2.4))-0.055,step(float3(0.0031308),c)); }
float3 display(float3 c, float mode) {
  if(mode < 0.5) return c;
  float3 l=linearize(c);
  float3 p=float3(dot(l,float3(0.8225929,0.177534,0.0)),dot(l,float3(0.0331995,0.9667835,0.0)),dot(l,float3(0.0170853,0.0723957,0.9103015)));
  return mix(c,encode(p),mode);
}
half4 main(float2 xy) {
  float2 p=(xy-center)/max(scale,0.01);
  if (xy.y > cutoff) return half4(display(ground,p3),1.0);
  float2 p0=p-lean;
  float d0=distanceAt(p0,shapeA[0],shapeB[0])-breath;
  float core=1.0-smoothstep(-8.0,2.0,d0);
  float3 col=mix(ground,coreCol,core);
  float3 energy=float3(0.0);
  for(int k=0;k<7;k++) {
    float fk=float(k); float off=0.0; float strength=0.0; float vr=0.0;
    ${comp.rings.offsets.map((o, i) => `if(k==${i}) { off=${f(o)}; strength=${f(comp.rings.strength[i])}; vr=${f(comp.field.verticalRatios[i])}; }`).join('\n    ')}
    float4 A=shapeA[k]; float4 B=shapeB[k];
    float2 pk=p-lean*(1.0-fk/7.0);
    float ny=max(0.0,A.w)/${f(NOTCH_REF)}; float nx=max(0.0,B.y)/${f(NOTCH_REF)};
    float hw=60.0+off*${f(comp.field.hollowWidthGain)}; float hw2=hw*hw;
    float hollow=ny*off*${f(comp.field.hollowBase)}*exp(-(pk.x*pk.x)/hw2)*smoothstep(20.0,95.0,abs(pk.y))
      +nx*off*${f(comp.field.hollowBase)}*exp(-(pk.y*pk.y)/hw2)*smoothstep(20.0,95.0,abs(pk.x));
    float vs=1.0+ny*off*vr/(${f(ref.material.coreHeight)}+off);
    float hs=1.0+nx*off*vr/(${f(ref.material.coreWidth)}+off);
    float dk=distanceAt(float2(pk.x/hs,pk.y/vs),A,B)-breath;
    float travel=radiate*fk/6.0;
    float d=dk+hollow-off*(spacing/26.0)*(1.0+0.10*wake)-travel;
    float zone=smoothstep(-9.0,6.0,d0);
    float line=exp(-d*d/0.52)*strength;
    float halo=exp(-abs(d)/${f(comp.rings.halo)})*strength;
    float softness=k==2 ? 145.0 : (k==6 ? 1000.0 : 420.0);
    float band=exp(-d*d/softness);
    float lower=smoothstep(-10.0,150.0,p.y);
    float3 light=mix(tints[k],lowerTints[k],lower*0.85);
    float weight=k==2 ? ${f(comp.field.hotWeight)} : (k==4 ? ${f(comp.field.midWeight)} : (k==6 ? ${f(comp.field.outerWeight)} : ${f(comp.field.otherWeight)}));
    float enabled=1.0-step(rings,fk);
    float3 lineTint=mix(light,float3(1.0),k==2 ? 0.12 : (k==4 ? 0.06 : 0.035));
    float pq=(pulse-${f(comp.touch.pulseStep)}*fk-0.06)/0.045;
    float boost=1.0+(pulse<0.0 ? 0.0 : 1.4*exp(-(pq*pq)));
    energy+=(light*(halo*0.32+band*weight*(1.0+lower*0.3))+lineTint*line*(0.8+lower*0.3)/${f(comp.field.gain)})*enabled*zone*(1.0+0.20*wake)*boost;
  }
  // A warm, broad inner shoulder is present in the reference, rather than seven uniformly bright hairlines.
  float3 inner=coreTint*exp(-d0*d0/40.0)*0.9;
  energy+=inner;
  float notchPhase=(abs(p0.y)-${f(comp.field.warmY)})/${f(comp.field.warmSpread)};
  float notchWarm=exp(-(p0.x*p0.x)/${f(comp.field.warmWidth * comp.field.warmWidth)})*exp(-(notchPhase*notchPhase))*(1.0-smoothstep(180.0,220.0,abs(p0.y)));
  energy+=coreTint*notchWarm*${f(comp.field.warmStrength)}*(max(0.0,shapeA[0].w)/${f(NOTCH_REF)})*warm*smoothstep(-9.0,6.0,d0);
  float quiet=1.0-smoothstep(cutoff-fade,cutoff,xy.y);
  float3 lin=linearize(col)+energy*quiet*${f(comp.field.gain)};
  float3 shoulder=lin/(1.0+lin*0.38);
  col=encode(shoulder);
  float grain=fract(sin(dot(floor(xy*3.0),float2(12.9898,78.233)))*43758.5453)-0.5;
  col+=grain/510.0;
  return half4(display(clamp(col,0.0,1.0),p3),1.0);
}`;

/** The thumb is another part of the same emitter: dark glass centre, reflected pool in the mood's colour and a fine rim. */
export const THUMB_SKSL = `
uniform float2 center;
uniform float radius;
uniform float3 hue;
uniform float3 ground;
uniform float press;
uniform float p3;
${CONTOUR_SKSL.slice(CONTOUR_SKSL.indexOf('float3 linearize'), CONTOUR_SKSL.indexOf('half4 main'))}
half4 main(float2 xy) {
 float2 p=(xy-center)/radius; float r=length(p);
 float3 col=ground;
 col+=hue*(exp(-dot(p,p)/0.045)*0.65+exp(-r*r/0.5)*0.11);
 float rim=(r-0.975)/0.018;
 float edge=exp(-(rim*rim));
 col+=mix(hue,float3(0.65),0.4)*edge*(0.25+0.10*p.x+0.15*p.y+press*0.3);
 return half4(display(clamp(col,0.0,1.0),p3),1.0-smoothstep(0.98,1.02,r));
}`;
