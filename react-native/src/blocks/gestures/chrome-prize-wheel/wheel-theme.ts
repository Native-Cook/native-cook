// Daily Spin — measured composition and pure placement maths shared by the screen and offline proofs.
import { ASSETS, PHASE, POINTER, PRIZE_RADIUS, PRIZE_SWAY, PRIZE_TILE, SEGMENT_ANGLE } from './wheel-contract';

export const WHEEL_FONT = 'ChromePrizeWheelArchivo';
export const tone = { page: '#03030E', title: '#E4E6FF', subtitle: 'rgba(205,209,255,0.72)',
  chrome: ['#F4F5FF', '#AEB5EC', '#F4F5FF', '#3A4190', '#AEB5EC', '#F4F5FF'],
  stops: [0, 0.42, 0.5, 0.54, 0.78, 1] };
export const COPY = { title: 'Daily Spin', idle: 'Swipe the wheel. Every slice wins.',
  jackpot: 'Jackpot. The whole chest is yours.', win: 'Nice spin. This one is yours.' };

export function wheelLayout(width: number, height: number, safeTop: number, safeBottom: number) {
  const k = Math.min(width / 402, height / 874);
  const fontScale = Math.max(k, 0.82);
  const exact = width === 402 && height === 874 && safeTop === 62 && safeBottom === 34;
  const cx = width / 2;
  const cy = exact ? 424 : safeTop + (height - safeTop - safeBottom) * 0.465;
  const radius = 164 * k;
  const buttonHeight = height < 700 ? 48 : 56;
  const buttonY = height - safeBottom - 24 - buttonHeight / 2;
  const buttonTop = buttonY - buttonHeight / 2;
  const captionHeight = 88 * fontScale + 4;
  const captionTop = exact ? 628 : (cy + radius + buttonTop - captionHeight) / 2;
  const backdropScale = Math.max(width / 804, height / 1748,
    cy / 848, (height - cy) / 900);
  return { width, height, k, fontScale, cx, cy, radius,
    titleTop: safeTop + 16 * k, titleSize: 34 * fontScale, titleLine: 40 * fontScale,
    subtitleTop: safeTop + 16 * k + 48 * fontScale, bodySize: 17 * fontScale, bodyLine: 24 * fontScale,
    pointerX: cx - POINTER.pivot * radius, pointerY: cy,
    heroX: cx, heroY: cy - 20 * k, heroSize: 216 * k,
    captionTop, numeralSize: 56 * fontScale, numeralLine: 64 * fontScale,
    labelTop: captionTop + 64 * fontScale + 4,
    captionY: captionTop + 32 * fontScale,
    buttonX: cx, buttonY, buttonTop, buttonHeight, buttonWidth: width - 48,
    hintWidth: 196 * Math.max(k, 0.9), hintHeight: 48,
    backdrop: { x: (width - 804 * backdropScale) / 2, y: cy - 848 * backdropScale,
      width: 804 * backdropScale, height: 1748 * backdropScale } };
}
export type ScreenLayout = ReturnType<typeof wheelLayout>;
export function clamp01(v: number) { 'worklet'; return Math.max(0, Math.min(1, v)); }
export function blend(a: number, b: number, t: number) { 'worklet'; return a + (b - a) * t; }

// Take over the decal during the anticipation crouch, before the motion hook launches it.
// Both paths coincide at zero squash; the separate badge stays registered to the wheel.
export function printedPrizeOpacity(phase: number, squash: number, printed: number) {
  'worklet';
  return phase === PHASE.reveal && squash < -0.00001 ? 0 : printed;
}

export type PrizePoseInput = { winner: number; theta: number; lift: number; wheelScale: number;
  swayA: number; swayB: number; tiltX: number; tiltY: number; hero: number; heroSquash: number;
  heroX: number; heroY: number; heroTilt: number; collect: number; time: number; reduced: boolean };

/** The shader rotates each tile around its top centre, then foreshortens the entire wheel plane. */
export function prizePose(l: ScreenLayout, v: PrizePoseInput) {
  'worklet';
  const index = Math.max(0, Math.min(5, v.winner));
  const h = v.reduced ? 1 : v.hero;
  const hc = clamp01(h);
  const a = index * SEGMENT_ANGLE + v.theta;
  const sway = v.swayA * PRIZE_SWAY[index][0] + v.swayB * PRIZE_SWAY[index][1];
  const tile = PRIZE_TILE * l.radius * (1 + 0.07 * v.lift) * v.wheelScale;
  const orbit = PRIZE_RADIUS * l.radius * (1 + 0.07 * v.lift) * v.wheelScale;
  const fx = Math.cos(v.tiltX + 0.008 * Math.sin(v.theta));
  const fy = Math.cos(v.tiltY + 0.008 * Math.cos(v.theta));
  const startX = l.cx + (Math.cos(a) * orbit - Math.sin(sway) * tile / 2) * fx;
  const startY = l.cy + (Math.sin(a) * orbit + (Math.cos(sway) - 1) * tile / 2) * fy;
  const floating = v.reduced ? 0 : Math.sin(v.time * Math.PI * 2 / 2.4);
  const x = blend(startX, l.heroX + v.heroX, h);
  const y = blend(startY, l.heroY + v.heroY + floating * 4 * l.k, h) - 42 * l.k * Math.sin(Math.PI * hc);
  const side = tile * Math.pow(l.heroSize / tile, h);
  const q = v.reduced ? 0 : v.heroSquash;
  const take = v.reduced ? 0 : v.collect;
  const angle = blend(sway, v.heroTilt + floating * Math.PI / 120, hc)
    + (v.reduced ? 0 : 0.11 * Math.sin(hc * Math.PI)) - take * 0.35;
  return { x: blend(x, l.buttonX, take) + Math.sin(take * Math.PI) * 42 * l.k,
    y: blend(y, l.buttonY, take) - Math.sin(take * Math.PI) * 38 * l.k,
    side: side * Math.pow(0.18, take), angle,
    sx: (1 - q * 0.18) * blend(fx, 1, hc), sy: (1 + q * 0.22) * blend(fy, 1, hc),
    // Squash about the base rather than the tile centre.
    baseShift: -side * q * 0.11,
    opacity: v.reduced ? clamp01(v.hero) * (1 - v.collect) : 1 - clamp01((take - 0.75) * 4),
    startX, startY, tile, sway, fx, fy };
}

export function badgePose(l: ScreenLayout, v: PrizePoseInput, progress: number) {
  'worklet';
  const p = prizePose(l, { ...v, hero: 0, heroSquash: 0, collect: 0, reduced: false });
  const dx = (ASSETS.prizes.badge.x - 0.5) * p.tile;
  const dy = (ASSETS.prizes.badge.y - 0.5) * p.tile;
  const x = p.startX + (dx * Math.cos(p.sway) - dy * Math.sin(p.sway)) * p.fx;
  const y = p.startY + (dx * Math.sin(p.sway) + dy * Math.cos(p.sway)) * p.fy;
  const t = clamp01(progress);
  const open = clamp01((t - 0.67) / 0.33);
  return { x: blend(x, l.cx, t), y: blend(y, l.captionY, t) - 24 * l.k * Math.sin(t * Math.PI),
    size: blend(p.tile * ASSETS.prizes.badge.size / 0.92, 76 * l.fontScale, t) * (1 + open * 0.38),
    angle: p.sway * (1 - t), sx: blend(p.fx, 1, t), sy: blend(p.fy, 1, t), opacity: 1 - open };
}

// Hand placed, relative to the wheel; peripheral lower stars leave the caption's entire line clear.
export const SKY_STARS = [
  [-143,-217,44,-0.3,0], [111,-217,35,0.23,0], [-48,-244,16,0.28,1],
  [32,-207,19,-0.2,1], [-170,-129,23,0.38,0], [174,-113,16,-0.3,1],
  [183,-42,13,0.32,0], [179,102,13,-0.3,1], [-175,139,15,0.1,1],
  [-147,225,40,-0.25,1], [143,244,32,0.2,0], [-126,300,16,0.3,1],
  [165,188,17,-0.14,0], [-68,183,15,0.2,1],
] as const;
export const SKY_DOTS = [
  [-113,-243,13], [-64,-205,7], [147,-255,11], [61,-247,6], [5,-229,10],
  [-169,-178,7], [160,-171,9], [-187,-79,10], [191,26,6], [-187,69,6],
  [-181,197,8], [-106,211,11], [176,288,7], [88,301,8], [-30,301,5],
  [65,179,9], [159,140,7], [-162,270,5],
] as const;
export function skyPose(l: ScreenLayout, i: number, time: number, swirl: number, burst: number,
  tiltX: number, tiltY: number, reduced: boolean) {
  'worklet';
  const star = i < SKY_STARS.length;
  const s = star ? SKY_STARS[i] : SKY_DOTS[i - SKY_STARS.length];
  const dx = s[0] * l.k;
  const dy = s[1] * l.k;
  const r = Math.sqrt(dx * dx + dy * dy);
  const falloff = Math.min(1, l.radius * l.radius / (r * r));
  const turn = reduced ? 0 : Math.max(-0.35, Math.min(0.35, swirl * 0.35)) * falloff;
  const kick = reduced || burst < 0 ? 0 : 14 * l.k * Math.exp(-3.5 * burst) * Math.sin(burst * 13) * falloff;
  const t = reduced ? 0 : time;
  let x = l.cx + (dx * Math.cos(turn) - dy * Math.sin(turn)) * (1 + kick / r);
  let y = l.cy + (dx * Math.sin(turn) + dy * Math.cos(turn)) * (1 + kick / r);
  x += reduced ? 0 : tiltX * 28 * falloff;
  y += reduced ? 0 : Math.sin(t * (0.45 + i * 0.018) + i) * 2 * l.k + tiltY * 28 * falloff;
  // Keep the copy lanes clear on short screens too. Decorations fade before reaching a reserved line.
  const titleBottom = l.subtitleTop + l.bodyLine + 12;
  const half = s[2] * l.k / 2;
  const clearTop = clamp01((y - half - titleBottom) / (12 * l.k));
  const clearBottom = clamp01((l.buttonTop - 16 - y - half) / (12 * l.k));
  const inCaption = y + half > l.captionTop - 8 && y - half < l.labelTop + l.bodyLine + 8;
  const clearCaption = inCaption && Math.abs(x - l.cx) < 116 * l.fontScale + half ? 0 : 1;
  return { x, y, size: s[2] * l.k, angle: (star ? SKY_STARS[i][3] : i) + (reduced ? 0 : 0.06 * Math.sin(t * 0.4 + i)),
    tile: star ? SKY_STARS[i][4] : (i % 4 === 0 ? 2 : 3),
    opacity: clearTop * clearBottom * clearCaption * (star ? 0.72 : 0.3 + 0.18 * Math.sin(t * 1.3 + i)) };
}

/** Stateless seeds, so jumping to any age gives exactly the same burst. */
export function particlePose(l: ScreenLayout, i: number, age: number, power: number) {
  'worklet';
  const wave = i >= 64;
  const n = i % 64;
  const seed = Math.sin(i * 127.1 + 19.7) * 43758.5453;
  const random = seed - Math.floor(seed);
  const near = n % 3 !== 0;
  const t = age - (wave ? 0.22 : 0) - random * 0.025;
  const life = 1.2 + random;
  const active = age >= 0 && t > 0 && t < life && n < Math.floor(64 * power)
    && (!wave || (power >= 0.99 && n < 24));
  const a = n * 2.399963 + (wave ? 0.7 : 0);
  const speed = (210 + random * 350) * (wave ? 0.7 : 1) * power;
  const travel = speed * (1 - Math.exp(-2.1 * Math.max(0, t))) / 2.1;
  const depth = near ? 1 + Math.max(0, t) * (0.6 + random * 2.2) : 0.7;
  const progress = Math.max(0, t) / life;
  const x = l.heroX + Math.cos(a) * travel * l.k * depth;
  const y = l.heroY + ((Math.sin(a) - 0.25) * travel * depth + 90 * t * t) * l.k;
  const size = (8 + random * 17) * l.k * (near ? depth * depth : 0.65);
  // Fade a star before its visible silhouette crosses copy or the collection control.
  // The large foreground tiles can otherwise obscure the title on a short phone.
  const half = size * 0.4;
  const topClear = clamp01((y - half - l.subtitleTop - l.bodyLine - 12) / (24 * l.k));
  const bottomClear = clamp01((l.buttonTop - 12 - y - half) / (24 * l.k));
  const captionDistance = Math.max(l.captionTop - 8 - y - half,
    y - half - l.labelTop - l.bodyLine - 8, Math.abs(x - l.cx) - 116 * l.fontScale - half);
  const copyClear = topClear * bottomClear * clamp01(captionDistance / (16 * l.k));
  return { near, x, y, size,
    angle: a + t * (random - 0.5) * 8, tile: n % 7 < 4 ? n % 2 : n % 7 === 4 ? 2 : 3,
    opacity: active ? copyClear * (near ? 0.92 : 0.43) * clamp01(t / 0.045) * Math.pow(1 - clamp01(progress), 1.2) : 0 };
}

export function buttonPose(l: ScreenLayout, button: number, press: number, nudge: number) {
  'worklet';
  const t = clamp01(button);
  return { width: blend(l.hintWidth, l.buttonWidth, t), height: blend(l.hintHeight, l.buttonHeight, t),
    scale: press >= 0 ? 1 - press * 0.03 : 1 - press * 0.04, x: 6 * nudge };
}

// Uneven narrow beams with a broad scattering lobe. Premultiplied, additive light, never opaque wedges.
export const RAYS_SKSL = `
uniform float2 centre; uniform float radius; uniform float presence; uniform float clock;
half4 main(float2 p) {
  float2 q=(p-centre)/(radius*(0.6+0.4*presence));
  float radial=length(q); float ang=atan(q.y,q.x)-clock*0.2;
  float beams=0.0;
  for(int i=0;i<24;i++) {
    float fi=float(i); float axis=fi*2.399963+sin(fi*17.3)*0.19;
    float sharpness=90.0+110.0*(0.5+0.5*sin(fi*7.7));
    float beam=pow(max(0.0,cos(ang-axis)),sharpness);
    beams+=beam*(0.18+0.24*(0.5+0.5*sin(fi*13.1+clock*0.7)));
  }
  float edge=1.0-smoothstep(0.25,1.7,radial);
  float hot=exp(-radial*radial*15.0)*0.35;
  float power=(beams*0.16+hot)*edge*presence;
  float3 tint=mix(float3(0.9,0.86,1.0),float3(0.65,0.12,0.9),clamp(radial/1.7,0.0,1.0));
  return half4(tint*power,power);
}`;

export const HERO_SKSL = `
uniform shader picture; uniform float clock; uniform float gloss;
half4 main(float2 p) {
  half4 ink=picture.eval((p+0.5)*768.0);
  float sweep=fract(clock/1.8)*2.1-1.0;
  float band=(p.x+p.y*0.28-sweep)*17.0;
  float stripe=exp(-band*band)*gloss*0.24;
  return half4(ink.rgb+float3(0.8,0.85,1.0)*stripe*ink.a,ink.a);
}`;
