import { d, tgpu } from 'typegpu';

// vec4 groups keep the frequently written frame block compact and unambiguous on both runtimes.
export const Uniforms = d.struct({
  viewport: d.vec4f, // width, height, half-height, pixel ratio
  centre: d.vec4f, // x, y, dt, particle count
  motion: d.vec4f, // yaw, pitch, angular velocity, elapsed time
  strike: d.vec4f, // heat, sweep, positive heat change, seconds since strike (-1 when inactive)
  rot0: d.vec4f,
  rot1: d.vec4f,
  rot2: d.vec4f,
  light: d.vec4f, // xy lamp, z glint
  forge: d.vec4f, // charge, stamp, morph, engrave
  metal: d.vec4f,
  metalNext: d.vec4f,
  leaf: d.vec4f, // stage bottom, sheet top, strike enabled, reserved
}).$name('MedalUniforms');

export const Particle = d.struct({
  position: d.vec4f, // world xyz, stable seed
  velocity: d.vec4f, // world xyz, residual air angular velocity
  shape: d.vec4f, // width, height, rotation phase, rotation rate
  fall: d.vec4f, // fall speed, drift amplitude, drift rate, tumble rate
  travel: d.vec4f, // measured world velocity, capture strength (for projected shutter blur)
}).$name('GoldLeaf');

export const frameLayout = tgpu.bindGroupLayout({ frame: { uniform: Uniforms } });
export const faceLayout = tgpu.bindGroupLayout({
  front: { texture: d.texture2d(d.f32) },
  frontNext: { texture: d.texture2d(d.f32) },
  back: { texture: d.texture2d(d.f32) },
  linear: { sampler: 'filtering' },
});
export const simulationLayout = tgpu.bindGroupLayout({
  particles: { storage: d.arrayOf(Particle), access: 'mutable' },
});
export const leafLayout = tgpu.bindGroupLayout({
  particles: { storage: d.arrayOf(Particle), access: 'readonly' },
});
export const postLayout = tgpu.bindGroupLayout({
  source: { texture: d.texture2d(d.f32) },
  lower: { texture: d.texture2d(d.f32) },
  linear: { sampler: 'filtering' },
});

export const fullscreen = tgpu.vertexFn({
  in: { vertexIndex: d.builtin.vertexIndex },
  out: { position: d.builtin.position, uv: d.vec2f },
})(`{
  let uv = vec2f(f32((vertexIndex << 1u) & 2u), f32(vertexIndex & 2u));
  return Out(vec4f(uv * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0), 0.0, 1.0), uv);
}`).$name('fullscreen');
