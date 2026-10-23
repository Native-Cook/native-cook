import { d, tgpu } from 'typegpu';
import { frameLayout } from './schemas';

const uniforms = frameLayout.$;
const varyings = { uv: d.vec2f, energy: d.f32, ring: d.f32, radius: d.f32, warmth: d.f32, screenY: d.f32 };
// Analytic debris is independent of rain density, so all quality tiers retain the strike.
export const strikeVertex = tgpu.vertexFn({
  in: { vertexIndex: d.builtin.vertexIndex, instanceIndex: d.builtin.instanceIndex },
  out: { position: d.builtin.position, ...varyings },
})(`{
  let corners = array<vec2f, 6>(vec2f(-1.0,-1.0), vec2f(1.0,-1.0), vec2f(-1.0,1.0),
    vec2f(-1.0,1.0), vec2f(1.0,-1.0), vec2f(1.0,1.0));
  let corner = corners[vertexIndex];
  let age = uniforms.frame.strike.w;
  let id = f32(instanceIndex);
  let seed = fract(sin(id * 127.1 + 19.7) * 43758.5453);
  let seed2 = fract(sin(id * 311.7 + 71.3) * 22578.1459);
  let t = max(0.0, age - seed * 0.06);
  let life = 0.45 + seed2 * 0.8;
  var energy = pow(max(0.0, 1.0 - t / life), 2.0);
  if (age < 0.0 || age < seed * 0.06 || age > life + 0.06) { energy = 0.0; }
  let angle = id * 2.399963 + seed * 0.7;
  let direction = vec2f(cos(angle), sin(angle));
  let speed = (420.0 + 980.0 * seed2 * seed2) / 160.0;
  let run = speed * (1.0 - exp(-3.4 * t)) / 3.4;
  let velocity = direction * speed * exp(-3.4 * t) + vec2f(0.0, 3.25 * t);
  let along = normalize(velocity);
  let across = vec2f(-along.y, along.x);
  var p = direction * run + vec2f(0.0, 0.10 + 1.625 * t * t);
  p += along * corner.x * (length(velocity) * 0.014 + 0.003)
    + across * corner.y * (0.0028 + seed * 0.0035);
  var ring = 0.0;
  var radius = 1.0;
  var depth = 0.54;
  if (instanceIndex == 0u) {
    ring = 1.0;
    let k = clamp(age / 0.75, 0.0, 1.0);
    radius = (70.0 + 250.0 * (1.0 - pow(1.0 - k, 3.0))) / 160.0;
    p = corner * (radius + 0.06) + vec2f(0.0, 0.10);
    energy = select(0.0, 0.6 * pow(1.0 - k, 2.0), age >= 0.0 && age < 0.75);
    depth = 0.98;
  }
  let screen = uniforms.frame.centre.xy + p * uniforms.frame.viewport.z;
  let ndc = screen / uniforms.frame.viewport.xy * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0);
  return Out(vec4f(ndc, depth, 1.0), corner, energy, ring, radius, clamp(t / life, 0.0, 1.0), screen.y);
}`).$uses({ uniforms }).$name('strikeDebrisVertex');

export const strikeFragment = tgpu.fragmentFn({ in: varyings, out: d.vec4f })(`{
  var edge = (1.0 - smoothstep(0.45, 1.0, abs(varying.uv.y)))
    * (1.0 - smoothstep(0.65, 1.0, abs(varying.uv.x)));
  if (varying.ring > 0.5) {
    let distance = abs(length(varying.uv) * (varying.radius + 0.06) - varying.radius);
    edge = exp(-distance * distance / 0.00018);
  }
  let stage = (1.0 - smoothstep(uniforms.frame.leaf.x - 24.0 * uniforms.frame.viewport.w,
    uniforms.frame.leaf.x, varying.screenY)) * smoothstep(uniforms.frame.leaf.y + 48.0 * uniforms.frame.viewport.w,
    uniforms.frame.leaf.y + 80.0 * uniforms.frame.viewport.w, varying.screenY);
  let energy = varying.energy * edge * stage;
  let colour = mix(vec3f(1.0, 0.87, 0.59), vec3f(1.0, 0.16, 0.015), varying.warmth);
  let alpha = energy * 0.65;
  return vec4f(colour * energy * 7.0, alpha);
}`).$uses({ uniforms, varying: tgpu['~unstable'].rawCodeSnippet('in', d.struct(varyings)) }).$name('strikeDebrisFragment');
