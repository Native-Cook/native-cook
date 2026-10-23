import { AlphaType, ColorType, Skia, TileMode, type SkImage } from '@shopify/react-native-skia';

// Everything the Cosmic Drift splash draws that is not tied to a frame: the glossy sphere
// sprites (shaded pixel by pixel once, then blurred into depth-of-field levels), the seeded
// scene (spheres, dust streaks, stars) and the Orrery mark's geometry. Same numbers as the
// Swift version (CosmicDriftSplashArt.swift).

/** Sphere diameter inside a sprite, in pixels, and the sprite's side (2.1 diameters). */
export const DIAMETER = 168;
export const SIDE = 352;
export const SPRITE_SPAN = SIDE / DIAMETER;
/** Blur of each level, as a fraction of the sphere's diameter (Gaussian sigma). */
export const BLUR_LEVELS = [0, 0.02, 0.04, 0.07, 0.11, 0.17];

type V3 = [number, number, number];
type Palette = { base: V3; deep: V3; band: V3; rim: V3; bandFrom: number; bandStrength: number; rimStrength: number; trail: string };

function rgb(hex: number): V3 { return [((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255]; }
function palette(base: number, deep: number, band: number, rim: number, bandFrom: number, bandStrength: number, rimStrength: number): Palette {
  return { base: rgb(base), deep: rgb(deep), band: rgb(band), rim: rgb(rim), bandFrom, bandStrength, rimStrength, trail: `#${base.toString(16).padStart(6, '0')}` };
}

/** Ember with a teal cap, cream, teal with a cream band, dusty blue, ochre with a teal cap, charcoal. */
export const PALETTES: Palette[] = [
  palette(0xd5532f, 0x2a0a07, 0x4dbdb5, 0x6fd8ce, 0.64, 1, 0.55),
  palette(0xe8dec6, 0x2b2720, 0xe8dec6, 0xf3c48e, 2, 0, 0.35),
  palette(0x2f8e89, 0x051817, 0xe6d9bc, 0xa6e8da, 0.72, 0.95, 0.4),
  palette(0x5d7894, 0x0c131c, 0x5d7894, 0xf2a46c, 2, 0, 0.38),
  palette(0xcb972f, 0x281805, 0x3fa19c, 0x7fd9d1, 0.68, 1, 0.45),
  palette(0x5e6364, 0x0a0b0c, 0x5e6364, 0xaaccce, 2, 0, 0.42),
];
export const TRAILS = PALETTES.map(p => p.trail);

function fract(v: number) { return v - Math.floor(v); }
function hash2(x: number, y: number) { return fract(Math.sin(x * 127.1 + y * 311.7) * 43758.5453); }
function hash(i: number, k: number) { return hash2(i * 1.618 + 0.37, k * 2.71 + 0.11); }
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = Math.max(0, Math.min(1, (x - a) / (b - a))); return u * u * (3 - 2 * u);
}
function noise(x: number, y: number) {
  const ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy;
  const ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
  const a = hash2(ix, iy), b = hash2(ix + 1, iy), c = hash2(ix, iy + 1), d = hash2(ix + 1, iy + 1);
  return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy;
}
function norm(v: V3): V3 { const l = Math.hypot(v[0], v[1], v[2]); return [v[0] / l, v[1] / l, v[2] / l]; }

/**
 * One sphere shaded per pixel: soft key light from the upper left, a tight specular spot and a
 * broad sheen, a coloured rim light on the upper right, a tilted colour cap, grain and glints.
 * Premultiplied RGBA, SIDE × SIDE.
 */
function shade(p: Palette, index: number): Uint8Array {
  const out = new Uint8Array(SIDE * SIDE * 4);
  const r = DIAMETER / 2, c = SIDE / 2;
  const light = norm([-0.55, -0.68, 0.48]);
  const half = norm([light[0], light[1], light[2] + 1]);
  const axis = norm([0.45, -0.75, 0.48]);
  const rimX = 0.95, rimY = -0.31;
  const lo = Math.trunc(c - r - 2), hi = Math.trunc(c + r + 2);
  const seed = index;
  for (let py = lo; py < hi; py++) for (let px = lo; px < hi; px++) {
    const x = (px + 0.5 - c) / r, y = (py + 0.5 - c) / r;
    const d2 = x * x + y * y;
    const alpha = Math.min(1, Math.max(0, r * (1 - Math.sqrt(d2)) + 0.5));
    if (alpha <= 0) continue;
    const z = Math.sqrt(Math.max(0, 1 - d2));
    const len0 = Math.max(1, Math.sqrt(d2));
    const nx = x / len0, ny = y / len0, nz = z / len0;
    const diff = smoothstep(-0.28, 1, nx * light[0] + ny * light[1] + nz * light[2]);
    const cap = smoothstep(p.bandFrom - 0.12, p.bandFrom + 0.12, nx * axis[0] + ny * axis[1] + nz * axis[2]) * p.bandStrength;
    const grain = 0.6 * noise(x * 5 + seed * 7, y * 5) + 0.4 * noise(x * 11, y * 11 + seed * 3);
    const g = 0.9 + 0.12 * grain;
    const nh = Math.max(0, nx * half[0] + ny * half[1] + nz * half[2]);
    const spec = Math.pow(nh, 46) * 0.65 + Math.pow(nh, 9) * 0.11;
    const edge = 1 - z;
    const len = Math.max(1e-4, Math.sqrt(x * x + y * y));
    const rim = Math.pow(edge, 2.6) * smoothstep(-0.15, 0.9, (x * rimX + y * rimY) / len) * p.rimStrength;
    const fill = Math.pow(edge, 2) * smoothstep(0.2, 1, y) * 0.12;
    const dark = (1 - 0.28 * Math.pow(edge, 4) * (1 - diff)) * 0.86;
    const glint = hash2(px * 1.37 + seed * 19, py * 0.91) > 0.9972 ? 0.55 * (0.3 + 0.7 * diff) : 0;
    const lit = Math.pow(diff, 0.9), boost = 0.82 + 0.36 * diff;
    const sw = [1, 0.97, 0.92];
    const i = (py * SIDE + px) * 4;
    for (let ch = 0; ch < 3; ch++) {
      const albedo = (p.base[ch] + (p.band[ch] - p.base[ch]) * cap) * g;
      let col = (p.deep[ch] + (albedo - p.deep[ch]) * lit) * boost;
      col += sw[ch] * spec + p.rim[ch] * rim + albedo * fill;
      col = col * dark + glint;
      out[i + ch] = Math.trunc(Math.min(1, Math.max(0, col)) * alpha * 255);
    }
    out[i + 3] = Math.trunc(alpha * 255);
  }
  return out;
}

/**
 * Every palette at every blur level. The shading runs on the JS thread (a few hundred ms) and the
 * blur on an offscreen Skia surface; the images are copied to the CPU so the UI thread can draw them.
 */
export function buildSprites(): SkImage[][] | null {
  const result: SkImage[][] = [];
  for (let index = 0; index < PALETTES.length; index++) {
    const pixels = shade(PALETTES[index], index);
    const sharp = Skia.Image.MakeImage(
      { width: SIDE, height: SIDE, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul },
      Skia.Data.fromBytes(pixels), SIDE * 4,
    );
    if (!sharp) return null;
    const levels: SkImage[] = [];
    for (const level of BLUR_LEVELS) {
      const sigma = level * DIAMETER;
      if (sigma < 0.5) { levels.push(sharp); continue; }
      const surface = Skia.Surface.MakeOffscreen(SIDE, SIDE);
      if (!surface) return null;
      const canvas = surface.getCanvas();
      canvas.clear(Skia.Color('transparent'));
      const paint = Skia.Paint();
      paint.setImageFilter(Skia.ImageFilter.MakeBlur(sigma, sigma, TileMode.Decal, null));
      canvas.drawImage(sharp, 0, 0, paint);
      surface.flush();
      const snapshot = surface.makeImageSnapshot();
      levels.push(snapshot.makeNonTextureImage() ?? snapshot);
    }
    result.push(levels);
  }
  return result;
}

// Scene

export type Sphere = { z: number; angle: number; distance: number; radius: number; palette: number };
export type Speck = { x: number; y: number; z: number; size: number; rate: number; phase: number };
export type Star = { u: number; v: number; size: number; brightness: number; blue: boolean; rate: number; phase: number; twinkles: boolean };

const WEIGHTS = [0, 0, 1, 2, 2, 3, 4, 5, 0, 1]; // ember and teal a little more often

export const SPHERES: Sphere[] = Array.from({ length: 56 }, (_, i) => ({
  z: i * 0.56 + hash(i, 1) * 0.4,
  angle: i * 2.39996 + hash(i, 2) * 0.6,
  distance: 0.75 + 1.5 * hash(i, 3),
  radius: 0.45 + 0.35 * hash(i, 4),
  palette: WEIGHTS[Math.trunc(hash(i, 5) * 10) % 10],
}));

/** Fourteen streaks of dust, 44 glints each, drifting with the spheres. */
export const SPECKS: Speck[] = Array.from({ length: 14 }).flatMap((_, j) => {
  const zc = j * 2.2 + hash(j, 21) * 1.5;
  const ac = hash(j, 22) * 6.283, dc = 0.7 + 1.2 * hash(j, 23);
  const cx = dc * Math.cos(ac), cy = dc * Math.sin(ac), tx = -Math.sin(ac), ty = Math.cos(ac);
  return Array.from({ length: 44 }, (__, k) => {
    const s = j * 44 + k;
    const u = hash(s, 24) * 2 - 1;
    const along = u * 1.2, across = (hash(s, 25) - 0.5) * 0.16 * (1 - Math.abs(u) * 0.5);
    return {
      x: cx + tx * along + Math.cos(ac) * across, y: cy + ty * along + Math.sin(ac) * across, z: zc + u * 0.6,
      size: 0.5 + hash(s, 26), rate: 14 + Math.trunc(hash(s, 27) * 20), phase: hash(s, 28) * 6.283,
    };
  });
});

export const STARS: Star[] = Array.from({ length: 260 }, (_, i) => ({
  u: hash(i, 31), v: hash(i, 32), size: 0.35 + 0.75 * Math.pow(hash(i, 33), 3),
  brightness: 0.22 + 0.7 * Math.pow(hash(i, 34), 2), blue: hash(i, 35) > 0.82,
  rate: 4 + Math.trunc(hash(i, 36) * 9), phase: hash(i, 37) * 6.283, twinkles: hash(i, 38) > 0.62,
}));

// Mark (in a 100-unit box; the planet sits at its centre)

export const PLANET_RADIUS = 17;
export const STROKE = 7.5;
/** Inner and outer orbit: radius, start and end angle in degrees (y down, clockwise). */
export const ORBITS: [number, number, number][] = [[30, 120, 330], [43.5, 160, 345]];
export const MOON_ANGLE = 8;
export const MOON_RADIUS = 5.5;
/** The planet's offset from the mark's visual centre, in mark units. */
export const VISUAL_OFFSET = { x: -1, y: 9 };
