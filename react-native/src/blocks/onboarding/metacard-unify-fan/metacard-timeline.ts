import { D } from './metacard-layout';
import { MATERIALS, COLOR_RGB } from './metacard-theme';

export function clamp(x: number, lo = 0, hi = 1) { 'worklet'; return Math.max(lo, Math.min(hi, x)); }
export function lerp(a: number, b: number, p: number) { 'worklet'; return a + (b - a) * p; }
export function phase(t: number, a: number, b: number) { 'worklet'; return clamp((t - a) / (b - a)); }
export function radians(x: number) { 'worklet'; return x * Math.PI / 180; }
export function cubic(p: number, a: number, b: number) { 'worklet'; return 3 * (1 - p) * (1 - p) * p * a + 3 * (1 - p) * p * p * b + p * p * p; }
export function bezier(p: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  if (p <= 0 || p >= 1) return clamp(p);
  let lo = 0, hi = 1;
  for (let i = 0; i < 16; i++) { const mid = (lo + hi) / 2; if (cubic(mid, x1, x2) < p) lo = mid; else hi = mid; }
  return cubic((lo + hi) / 2, y1, y2);
}
export function ease(p: number) { 'worklet'; return bezier(p, 0.23, 1, 0.32, 1); }
export function fold(p: number) { 'worklet'; return bezier(p, 0.77, 0, 0.175, 1); }
export function spring(seconds: number) {
  'worklet';
  const t = Math.max(0, seconds), z = 0.62, w = 9.5, wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + z * w / wd * Math.sin(wd * t));
}
export function liftState(t: number) {
  'worklet';
  // Remove the last 0.6% spring residual smoothly before the idle boundary.
  return lerp(spring((t - 1060) / 1000), 1, ease(phase(t, 1800, 1900)));
}
export function loopState(seconds: number) {
  'worklet';
  const t = ((seconds % 12) + 12) % 12;
  const u = t < 3 ? 0 : t < 4.9 ? (t - 3) / 1.9 : t < 9.4 ? 1 : t < 10.8 ? 1 - (t - 9.4) / 1.4 : 0;
  return { u: clamp(u), forward: t < 9.4 ? 1 : 0, spin: D.spin + D.drift * (t < 7 ? t : t - 12) };
}
export function reducedLoopState(seconds: number) {
  'worklet';
  const t = ((seconds % 12) + 12) % 12;
  const u = t < 3.12 ? 0 : t < 4.9 ? phase(t, 3.12, 3.44) : t < 9.52 ? 1 : 1 - phase(t, 9.52, 9.84);
  const press = t < 9.4 ? phase(t, 3, 3.12) * (1 - phase(t, 3.12, 3.30))
    : phase(t, 9.4, 9.52) * (1 - phase(t, 9.52, 9.70));
  return { u, press };
}
export function fanCardPose(i: number, spin: number, u: number, _forward: number, anchorSpin = spin) {
  'worklet';
  // 24 ms stagger / 500 ms flight; each leaf rises with its spiral radius curve.
  const t = u * 1900, start = 260 + 24 * i, end = start + 500;
  const p = fold(phase(t, start, end)), breath = ease(phase(t, 0, 260));
  const angle = spin + 45 * i - 5 * breath, orbit = radians(angle + 135 * p);
  const radius = (D.radius + 8 * breath) * (1 - p) * lerp(1, D.rosetteScale, p), lateral = D.lateral * (1 - p);
  const initial = angle - 90;
  const target = Math.ceil((anchorSpin + 45 * i) / 180) * 180;
  const arrival = phase(t, end, end + 160);
  const square = fold(phase(t, 1025, 1110));
  const residual = (i - 3.5) * 11 * lerp(1, 0.85, phase(t, end, 1025));
  const overshoot = 7 * p * (1 - ease(arrival));
  const spread = fold(phase(t, end - 150, end));
  const rotation = lerp(lerp(initial, target, p) + residual * spread + overshoot, target, square);
  // Keep the 116% spread above the common rise, then square up beneath the slab.
  // A reversible spread preserves two substantial tips even for those middle leaves.
  const scale = lerp(1, D.rosetteScale, p) * (1 + 0.16 * spread * (1 - square));
  return { x: D.fanX + Math.sin(orbit) * radius + Math.cos(orbit) * lateral,
    y: D.fanY - Math.cos(orbit) * radius + Math.sin(orbit) * lateral + (D.heroY - D.fanY) * liftState(t),
    rotation, scale, opacity: 1 - phase(t, 1100, 1110), progress: p };
}
export function hubState(u: number, spin: number, forward = 1, pressed = 0) {
  'worklet';
  const t = u * 1900, morph = fold(phase(t, 300, 760));
  const press = t < 140 ? ease(phase(t, 0, 140)) : 1 - ease(phase(t, 140, 300));
  let bumps = 0;
  if (forward) for (let i = 0; i < 8; i++) {
    const b = phase(t, 760 + 24 * i, 850 + 24 * i);
    bumps += Math.sin(Math.PI * b) * (1 - b) * 0.024;
  }
  const lock = forward ? Math.sin(Math.PI * phase(t, 1025, 1110)) * 0.03 : 0;
  const turn = spin - 5 * ease(phase(t, 0, 260));
  const nearest = Math.round(turn / 180) * 180;
  return { morph, scale: (1 - 0.06 * press + bumps - lock) * (1 - lerp(0.06, 0.03, morph) * pressed),
    bumpY: -bumps * 75, roll: (turn - nearest) * (1 - morph), mark: fold(phase(t, 620, 1000)) };
}
export function heroPose(u: number, time: number, dragYaw = 0, dragPitch = 0, reduced = false) {
  'worklet';
  const t = u * 1900, s = liftState(t), idle = ease(phase(t, 1800, 1900));
  return { x: D.heroX, y: lerp(D.fanY, D.heroY, s) + (reduced ? 0 : 3 * Math.sin(time * Math.PI * 2 / 5.8) * idle),
    width: lerp(lerp(134, D.slabWidth, fold(phase(t, 260, 760))), D.heroWidth, s),
    height: lerp(lerp(85, D.slabHeight, fold(phase(t, 260, 760))), D.heroHeight, s),
    yaw: D.yaw * s + (dragYaw + (reduced ? 0 : 6 * Math.sin(time * Math.PI * 2 / 6.4))) * idle,
    pitch: D.pitch * s + (dragPitch + (reduced ? 0 : 2 * Math.sin(time * Math.PI * 2 / 5.2 + 1))) * idle,
    roll: D.roll * s, lift: s };
}
export function textState(u: number, reduced = false) {
  'worklet';
  const t = u * 1900;
  const hintOut = ease(phase(t, 100, 340)), hintIn = ease(phase(t, 1470, 1920));
  const out = ease(phase(t, 60, 300)), word = ease(phase(t, 1350, 1800)), title = ease(phase(t, 1430, 1880));
  return { fan: reduced ? 1 - u : 1 - out, fanY: reduced ? 0 : -6 * out, fanBlur: reduced ? 0 : 6 * out,
    word: reduced ? u : word, wordY: reduced ? 0 : 10 * (1 - word), wordBlur: reduced ? 0 : 6 * (1 - word),
    hero: reduced ? u : title, heroY: reduced ? 0 : 10 * (1 - title), heroBlur: reduced ? 0 : 6 * (1 - title),
    hintFan: reduced ? 1 - u : 1 - hintOut, hintFanY: reduced ? 0 : -6 * hintOut, hintFanBlur: reduced ? 0 : 6 * hintOut,
    hintHero: reduced ? u : hintIn, hintHeroY: reduced ? 0 : 10 * (1 - hintIn), hintHeroBlur: reduced ? 0 : 6 * (1 - hintIn) };
}
export function effectsState(u: number, forward: number, time: number, reduced = false) {
  'worklet';
  const t = 1900 * u, one = forward && !reduced ? 1 : 0;
  return { pageMix: clamp(liftState(t)), darkness: 1 - phase(t, 300, 900),
    bloom: one * 0.8 * ease(phase(t, 1025, 1040)) * (1 - phase(t, 1040, 1500)) ** 2
      * lerp(1, 0.45, ease(phase(t, 1040, 1110))),
    bloomReach: lerp(46, 150, phase(t, 1040, 1500)),
    inner: one * 0.85 * ease(phase(t, 1025, 1040)) * (1 - phase(t, 1040, 1180)),
    lamination: one * (t >= 1060 ? 1 - ease(phase(t, 1300, 1900)) : 0),
    sweep: phase(t, 1080, 1700), tint: one * ease(phase(t, 1080, 1110)) * (1 - phase(t, 1240, 1700)),
    chip: ease(phase(t, 1150, 1500)), halo: u === 0 && !reduced ? 0.035 + 0.019 * Math.sin(time * Math.PI * 2 / 2.4) : 0 };
}
export function rotationMatrix(yaw: number, pitch: number, roll: number) {
  'worklet';
  const y = radians(yaw), p = radians(pitch), r = radians(roll);
  const cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p), cr = Math.cos(r), sr = Math.sin(r);
  return [cr * cy, sr * cy, -sy, cr * sy * sp - sr * cp, sr * sy * sp + cr * cp, cy * sp,
    cr * sy * cp + sr * sp, sr * sy * cp - cr * sp, cy * cp];
}
export function fanUniforms(i: number, spin: number, u: number, forward: number, press: number, seam = false, anchorSpin = spin) {
  'worklet';
  const p = fanCardPose(i, spin, u, forward, anchorSpin), last = fanCardPose(7, spin, u, forward, anchorSpin);
  const first = fanCardPose(0, spin, u, forward, anchorSpin);
  return { centre: [p.x, p.y], angle: radians(p.rotation), scale: p.scale, opacity: p.opacity, gloss: MATERIALS[i].gloss,
    brushed: MATERIALS[i].brushed, ao: 1 - p.progress, press, seam: seam ? 1 : 0, isLast: i === 7 ? 1 : 0,
    first: [first.x, first.y, radians(first.rotation)], firstScale: first.scale, lastScale: last.scale,
    shadowHandover: 1 - ease(phase(u * 1900, 495, 505)),
    // At 505 ms the 0/7 faces are disjoint for every starting rotation (see geometry proof).
    last: [last.x, last.y, radians(last.rotation)], handover: u * 1900 < 505 ? 1 : 0 };
}
/** Sample the same reflected-ray environment used by SkSL, at a point on the face. */
export function reflectionAt(rot: number[], x: number, y: number) {
  'worklet';
  const wx = rot[0] * x + rot[3] * y, wy = rot[1] * x + rot[4] * y;
  const wz = rot[2] * x + rot[5] * y - D.camera, len = Math.hypot(wx, wy, wz);
  const dot = (wx * rot[6] + wy * rot[7] + wz * rot[8]) / len;
  return wy / len - 2 * dot * rot[7] + 0.9 * (wx / len - 2 * dot * rot[6]);
}
export function studioReflection(yaw: number, rot: number[], hx: number, hy: number, morph: number) {
  'worklet';
  // A tracking studio box keeps its diagonal boundary on the face at either sway end.
  // Pose, not an idle-only clock, drives it: lift and idle share exactly the same light.
  const fraction = 0.46 - 0.16 * Math.sin(clamp((yaw - D.yaw) / 6, -1, 1) * Math.PI / 2);
  const diagonal = Math.hypot(hx * 2, hy * 2);
  const along = (fraction * 2 - 1), lead = 80 / diagonal;
  const broad = reflectionAt(rot, hx * along, hy * along);
  const coat = reflectionAt(rot, hx * (along + lead), hy * (along + lead));
  return [lerp(-1.1, broad, morph), lerp(-1.06, coat, morph)];
}
export function bounceUniforms(u: number, spin: number, forward: number, anchorSpin: number, reduced = false) {
  'worklet';
  const pools: number[][] = [], progress = reduced ? 0 : u;
  const amount = reduced ? 1 - u : 1 - phase(u * 1900, 1025, 1110);
  for (let i = 0; i < 8; i++) {
    const p = fanCardPose(i, spin, progress, forward, anchorSpin);
    const orbit = radians(spin + 45 * i - 5 * ease(phase(progress * 1900, 0, 260)) + 135 * p.progress);
    const offset = (D.cardWidth / 2 + 12) * (1 - p.progress);
    pools.push([p.x + Math.sin(orbit) * offset, p.y - Math.cos(orbit) * offset]);
  }
  return { pools, amount, darkness: reduced ? 1 : 1 - phase(u * 1900, 300, 900), colors: COLOR_RGB };
}
export function heroUniforms(u: number, spin: number, time: number, forward: number, press = 0, dragYaw = 0, dragPitch = 0, reduced = false) {
  'worklet';
  const h = hubState(u, spin, forward, press), p = heroPose(u, time, dragYaw, dragPitch, reduced), e = effectsState(u, forward, time, reduced);
  const size = p.width / D.heroWidth, markSize = lerp(D.markSize, D.heroMarkSize * size, h.mark), roll = h.roll + p.roll;
  const rot = rotationMatrix(p.yaw, p.pitch, roll);
  // Perspective pulls the near edge outward: keep the projected silhouette centred on H.
  let left = Infinity, right = -Infinity, top = Infinity, bottom = -Infinity;
  for (let i = 0; i < 4; i++) {
    const x = (i < 2 ? -1 : 1) * p.width / 2 * h.scale;
    const y = (i % 2 ? -1 : 1) * p.height / 2 * h.scale;
    const projection = D.camera / (D.camera - rot[2] * x - rot[5] * y);
    const px = (rot[0] * x + rot[3] * y) * projection;
    const py = (rot[1] * x + rot[4] * y) * projection;
    left = Math.min(left, px); right = Math.max(right, px);
    top = Math.min(top, py); bottom = Math.max(bottom, py);
  }
  return { centre: [p.x - (left + right) / 2, p.y + h.bumpY - (top + bottom) / 2], halfSize: [p.width / 2 * h.scale, p.height / 2 * h.scale],
    octagon: D.hubApothem * h.scale, morph: h.morph, corner: lerp(D.slabRadius, D.heroRadius, clamp(p.lift)) * h.scale,
    thickness: D.thickness, camera: D.camera, rot,
    mark: [(D.heroMarkX - D.heroWidth / 2) * size * h.mark * h.scale, (D.heroMarkY - D.heroHeight / 2) * size * h.mark * h.scale, markSize * h.scale],
    markTurn: -radians(roll) * (1 - h.mark), chipAlpha: e.chip, lamination: e.lamination,
    sweep: e.sweep, tint: e.tint, colors: COLOR_RGB,
    studio: studioReflection(p.yaw, rot, p.width / 2 * h.scale, p.height / 2 * h.scale, clamp(p.lift)),
    shadowBlur: lerp(10, 36, clamp(p.lift)), inner: e.inner, lightAngle: radians(spin) };
}

export function fanBounds(uniforms: ReturnType<typeof fanUniforms>, shadow = false) {
  'worklet';
  const c = Math.abs(Math.cos(uniforms.angle)), s = Math.abs(Math.sin(uniforms.angle));
  const hw = shadow ? D.shadowWidth / 2 : D.cardWidth / 2 + 0.5;
  const hh = shadow ? D.shadowHeight / 2 : D.cardHeight / 2 + 0.5;
  const x = (c * hw + s * hh) * uniforms.scale, y = (s * hw + c * hh) * uniforms.scale;
  const hidden = uniforms.opacity === 0 || (uniforms.seam > 0 && uniforms.handover === 0);
  return { x: uniforms.centre[0] - x + (shadow ? D.shadowX : 0), y: uniforms.centre[1] - y + (shadow ? D.shadowY : 0),
    width: hidden ? 0 : x * 2, height: hidden ? 0 : y * 2 };
}
export function heroBounds(h: ReturnType<typeof heroUniforms>) {
  'worklet';
  const x = lerp(h.octagon, h.halfSize[0], h.morph), y = lerp(h.octagon, h.halfSize[1], h.morph), r = h.rot;
  const z = Math.abs(r[2]) * x + Math.abs(r[5]) * y + h.thickness;
  const projection = D.camera / (D.camera - z);
  const rx = (Math.abs(r[0]) * x + Math.abs(r[3]) * y + h.thickness) * projection + 2;
  const ry = (Math.abs(r[1]) * x + Math.abs(r[4]) * y + h.thickness) * projection + 2;
  return { x: h.centre[0] - rx, y: h.centre[1] - ry, width: rx * 2, height: ry * 2 };
}
