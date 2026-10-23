import { d, tgpu } from 'typegpu';
import { faceLayout, frameLayout } from './schemas';

export const A = 0.5352;
export const H = 0.045;
export const CAM = 6.9;
export const lamp = tgpu.fn([d.vec3f, d.vec3f, d.f32], d.f32)(`(direction, towards, size) {
  return exp((dot(direction, towards) - 1.0) / (size * size * 0.5));
}`);
// This WGSL port mirrors medal-shader.ts. Display encoding happens only after HDR bloom.
const surface = tgpu['~unstable'].declare(`
const A: f32 = 0.5352;
const H: f32 = 0.045;
const BEVEL: f32 = 0.016;
const CAM: f32 = 6.9;
const RELIEF: f32 = 0.04;


fn toObject(v: vec3f) -> vec3f {
  return vec3f(dot(mat3x3f(resources.frame.rot0.xyz, resources.frame.rot1.xyz, resources.frame.rot2.xyz)[0], v), dot(mat3x3f(resources.frame.rot0.xyz, resources.frame.rot1.xyz, resources.frame.rot2.xyz)[1], v), dot(mat3x3f(resources.frame.rot0.xyz, resources.frame.rot1.xyz, resources.frame.rot2.xyz)[2], v));
}

fn toWorld(v: vec3f) -> vec3f {
  return mat3x3f(resources.frame.rot0.xyz, resources.frame.rot1.xyz, resources.frame.rot2.xyz) * v;
}


fn leanLamp(v: vec3f) -> vec3f {
  var amount: f32 = length(resources.frame.light.xy);
  if (amount < 0.00001) { return v; }
  var axis: vec3f = vec3f(-resources.frame.light.xy.y, resources.frame.light.xy.x, 0.0) / amount;
  var ang: f32 = min(amount, 1.0) * 0.959931;
  return v * cos(ang) + cross(axis, v) * sin(ang) + axis * dot(axis, v) * (1.0 - cos(ang));
}

fn squared(value: f32) -> f32 {
  return value * value;
}

fn stadium(p: vec2f) -> f32 {
  var q: vec2f = vec2f(p.x, max(abs(p.y) - (1.0 - A), 0.0));
  return length(q) - A;
}

fn faceUV(p: vec3f) -> vec2f {
  var uv: vec2f = vec2f(p.x / (2.0 * A) + 0.5, 0.5 - p.y * 0.5);

  if (p.z < 0.0) { uv.x = 1.0 - uv.x; }

  return clamp(uv * vec2f(512.0,956.0), vec2f(1.0), vec2f(512.0,956.0) - vec2f(1.0));
}


fn frontTap(uv: vec2f, tile: f32) -> f32 {
  var atUV: vec2f = uv;

  if (tile > 4.5) { atUV = floor(atUV) + vec2f(0.5); }
  var at: vec2f = vec2f(atUV.x + tile * vec2f(512.0,956.0).x, atUV.y);
  if (resources.frame.forge.z < 0.5) { return textureSampleLevel(faces.front, faces.linear, at / vec2f(3072.0,956.0), 0.0).r; }
  return textureSampleLevel(faces.frontNext, faces.linear, at / vec2f(3072.0,956.0), 0.0).r;
}

fn tap(uv: vec2f, tile: f32, front: bool) -> f32 {
  var atUV: vec2f = uv;
  if (front) { return frontTap(uv, tile); }
  if (tile > 4.5) { atUV = floor(atUV) + vec2f(0.5); }
  return textureSampleLevel(faces.back, faces.linear, vec2f(atUV.x + tile * vec2f(512.0,956.0).x, atUV.y) / vec2f(3072.0,956.0), 0.0).r;
}

fn tintNear(uv: vec2f, off: vec2f) -> f32 {
  return frontTap(clamp(uv + off, vec2f(1.0), vec2f(512.0,956.0) - vec2f(1.0)), 2.0);
}



fn fieldUV(uv: vec2f, front: bool) -> vec2f {
  return select(vec2f(90.0 + (uv.x % 80.0), 600.0 + (uv.y % 220.0)), vec2f(180.0 + (uv.x % 150.0), 750.0 + (uv.y % 65.0)), front);
}

fn existence(order: f32, front: bool) -> f32 {
  if (order < 0.001) { return 1.0; }
  if (front) {
    var formed: f32 = 1.0 - pow(1.0 - clamp(resources.frame.forge.y, 0.0, 1.0), 3.0);

    if (resources.frame.forge.z > 0.0 && resources.frame.forge.z < 1.0) {
      var level: f32 = select(1.08 * (resources.frame.forge.z - 0.54) / 0.46, 1.08 * (1.0 - resources.frame.forge.z / 0.46), resources.frame.forge.z < 0.5);
      formed *= smoothstep(order - 0.04, order + 0.04, level);
    }
    return formed;
  }
  if (resources.frame.forge.w >= 1.0) { return 1.0; }
  if (resources.frame.forge.w <= 0.0) { return 0.0; }
  return smoothstep(order - 0.02, order, resources.frame.forge.w);
}

fn heatGrain(uv: vec2f) -> f32 {
  return sin(uv.x * 0.061 + sin(uv.y * 0.047) * 2.0) * 0.024
       + sin(uv.x * 0.19 - uv.y * 0.081) * 0.012;
}

fn chargeAt(order: f32, grit: f32) -> f32 {
  if (order < 0.001 || resources.frame.forge.x <= 0.0) { return 0.0; }
  var uneven: f32 = (grit - 0.56) * 0.65;
  return smoothstep(-0.10, 0.10, resources.frame.forge.x - order + uneven);
}

fn chargeNear(uv: vec2f, off: vec2f) -> f32 {
  var at: vec2f = clamp(uv + off, vec2f(1.0), vec2f(512.0,956.0) - vec2f(1.0));
  return chargeAt(frontTap(at, 5.0), 0.56 + heatGrain(at));
}

fn slab(p: vec3f, lift: f32) -> f32 {
  var w: vec2f = vec2f(stadium(p.xy) + BEVEL, abs(p.z) - H - lift + BEVEL);
  return min(max(w.x, w.y), 0.0) + length(max(w, vec2f(0.0))) - BEVEL;
}


fn restMap(p: vec3f) -> f32 {
  var uv: vec2f = faceUV(p) + vec2f(4.0 * vec2f(512.0,956.0).x, 0.0);
  var b: f32;
  if (p.z < 0.0) { b = textureSampleLevel(faces.back, faces.linear, uv / vec2f(3072.0,956.0), 0.0).r; }
  else if (resources.frame.forge.z >= 0.5) { b = textureSampleLevel(faces.frontNext, faces.linear, uv / vec2f(3072.0,956.0), 0.0).r; }
  else { b = textureSampleLevel(faces.front, faces.linear, uv / vec2f(3072.0,956.0), 0.0).r; }
  var inside: f32 = (1.0 - smoothstep(-0.05, -0.012, stadium(p.xy)));
  return slab(p, (b - 0.25) * RELIEF * inside);
}

fn reliefMap(p: vec3f) -> f32 {
  var uv: vec2f = faceUV(p);
  var front: bool = p.z >= 0.0;
  var b: f32 = tap(uv, 4.0, front);
  if ((front && (resources.frame.forge.y < 1.0 || (resources.frame.forge.z > 0.0 && resources.frame.forge.z < 1.0))) || (!front && resources.frame.forge.w < 1.0)) {
    var order: f32 = tap(uv, 5.0, front);
    var made: f32 = existence(order, front);
    if (made < 1.0) { b = mix(tap(fieldUV(uv, front), 4.0, front), b, made); }
  }

  var inside: f32 = (1.0 - smoothstep(-0.05, -0.012, stadium(p.xy)));
  return slab(p, (b - 0.25) * RELIEF * inside);
}

fn slabNormal(p: vec3f) -> vec3f {
  const e: f32 = 0.002;
  var k: vec2f = vec2f(1.0, -1.0);
  return normalize(k.xyy * slab(p + k.xyy * e, 0.0) + k.yyx * slab(p + k.yyx * e, 0.0) +
                   k.yxy * slab(p + k.yxy * e, 0.0) + k.xxx * slab(p + k.xxx * e, 0.0));
}





fn lamp(d: vec3f, towards: vec3f, size: f32) -> f32 {
  return exp((dot(d, towards) - 1.0) / (size * size * 0.5));
}

fn room(d: vec3f, rough: f32, brushed: f32) -> vec3f {
  var fill: f32 = lamp(d, normalize(vec3f(0.06, 0.20, 0.98)), mix(0.48, 0.98, rough));
  var key: f32 = lamp(d, leanLamp(normalize(vec3f(-0.52, 0.60, 0.61))), mix(0.16, 0.80, rough));


  var low: vec3f = leanLamp(normalize(vec3f(-0.10, -0.17, 0.98)));
  var off: vec2f = d.xy - low.xy;
  var band: f32 = exp(-off.x * off.x / 0.20 - off.y * off.y / 0.0042) * step(0.0, d.z);
  var flash: f32 = mix(lamp(d, low, mix(0.085, 0.42, rough)), band, brushed);
  var rim: f32 = lamp(d, normalize(vec3f(0.86 + resources.frame.light.xy.x * 1.8, 0.12, -0.42)), mix(0.14, 0.50, rough));
  var stripWidth: f32 = 0.10 + rough * 0.45;
  var strip: f32 = exp(-squared((d.y - 0.80) / stripWidth)) * smoothstep(-0.25, 0.45, d.z);
  var sky: f32 = mix(0.006, 0.03, d.y * 0.5 + 0.5);
  return vec3f(1.0, 0.975, 0.94) * fill * 0.62 / (0.6 + rough * 1.6)
       + vec3f(1.0, 0.965, 0.91) * key * (2.3 + resources.frame.light.z) / (0.6 + rough * 1.6)
       + vec3f(1.0, 0.88, 0.66) * flash * mix(1.7 / (0.5 + rough * 2.4), 2.6, brushed)
       + vec3f(1.0, 0.98, 0.95) * strip * 0.22 / (0.7 + rough * 1.4)
       + vec3f(0.86, 0.91, 1.0) * rim * 0.30 / (0.7 + rough * 1.2)
       + vec3f(sky);
}

`).$uses({ resources: frameLayout.$, faces: faceLayout.$ });

export const medalFragment = tgpu.fragmentFn({
  in: { position: d.builtin.position },
  out: { colour: d.vec4f, depth: d.builtin.fragDepth },
})(`{
  surface
  var q: vec2f = (position.xy - resources.frame.centre.xy) / resources.frame.viewport.z;
  if (length(q) > 1.9) { discard; }

  // A warm emissive atmosphere follows the projected silhouette, behind the metal.
  // Store real premultiplied linear radiance; post applies the display transfer once.
  let projected = vec2f(q.x, -q.y);
  let spine = resources.frame.rot1.xy * (1.0 - A);
  let along = clamp(dot(projected, spine) / max(dot(spine, spine), 0.00001), -1.0, 1.0);
  let radial = projected - spine * along;
  let axis = resources.frame.rot2.xy;
  let axisLength = length(axis);
  let shortAxis = select(vec2f(1.0, 0.0), axis / max(axisLength, 0.00001), axisLength > 0.00001);
  let narrow = A * abs(resources.frame.rot2.z) + H * axisLength;
  let radialAxes = vec2f(dot(radial, shortAxis), dot(radial, vec2f(-shortAxis.y, shortAxis.x)));
  let radialLength = length(radialAxes);
  let ellipseRadius = radialLength / max(length(radialAxes / vec2f(max(narrow, H), A)), 0.00001);
  let away = max(radialLength - ellipseRadius, 0.0);
  let warmth = max(resources.frame.strike.x, resources.frame.forge.x * 0.22);
  let glow = exp(-away * mix(11.0, 5.2, warmth)) * (1.0 - smoothstep(1.4, 1.9, length(q)))
    * max(resources.frame.rot2.z, 0.0) * (0.23 + warmth * 0.55);
  let atmosphereAlpha = min(glow, 0.85);
  let atmosphere = vec4f(mix(vec3f(1.25, 0.25, 0.016), vec3f(2.0, 0.85, 0.25), warmth) * atmosphereAlpha, atmosphereAlpha);

  var eye: vec3f = vec3f(0.0, 0.0, CAM);
  var ray: vec3f = normalize(vec3f(q.x, -q.y, 0.0) - eye);
  var ro: vec3f = toObject(eye);
  var rd: vec3f = toObject(ray);


  if (length(q) > 1.18) { return Out(atmosphere, 0.99); }

  var t: f32 = CAM - 1.12;
  var nearest: f32 = 1.0;
  var hit: bool = false;
  var p: vec3f = ro;
  for (var i: i32 = 0; i < 44; i++) {
    p = ro + rd * t;
    var d: f32 = restMap(p);
    nearest = min(nearest, d);
    if (d < 0.0007) { hit = true; break; }
    t += d * 0.86;
    if (t > CAM + 1.12) { break; }
  }


  var px: f32 = resources.frame.viewport.w / resources.frame.viewport.z;
  var edge: f32 = select(1.0 - smoothstep(0.0, px * 1.25, nearest), 1.0, hit);

  if (edge <= 0.0) { return Out(atmosphere, 0.99); }



  var changing: bool = select((resources.frame.forge.w < 1.0), (resources.frame.forge.y < 1.0 || (resources.frame.forge.z > 0.0 && resources.frame.forge.z < 1.0)), p.z >= 0.0);
  if (hit && changing) {
    for (var k: i32 = 0; k < 10; k++) {
      var correction: f32 = reliefMap(p);
      if (abs(correction) < 0.00015) { break; }
      t += correction * 0.86;
      p = ro + rd * t;
    }
  }

  var n: vec3f = slabNormal(p);
  var uv: vec2f = faceUV(p);
  var facing: f32 = smoothstep(0.55, 0.92, abs(n.z)) * (1.0 - smoothstep(-0.035, -0.006, stadium(p.xy)));
  var front: bool = p.z >= 0.0;

  var tilt: vec2f = vec2f(tap(uv, 0.0, front), tap(uv, 1.0, front)) * 2.0 - vec2f(1.0);
  var nm: vec3f = vec3f(tilt, sqrt(max(1.0 - dot(tilt, tilt), 0.0)));
  var tint: f32 = tap(uv, 2.0, front);
  var grit: f32 = tap(uv, 3.0, front);
  var ink: f32 = tint;
  var order: f32 = 0.0;
  if ((front && (resources.frame.forge.y < 1.0 || resources.frame.forge.x > 0.0 || (resources.frame.forge.z > 0.0 && resources.frame.forge.z < 1.0))) || (!front && resources.frame.forge.w < 1.0)) {
    order = tap(uv, 5.0, front);
  }
  var made: f32 = existence(order, front);
  if (made < 1.0) {
    var clean: vec2f = fieldUV(uv, front);
    var grainTilt: vec2f = vec2f(tap(clean, 0.0, front), tap(clean, 1.0, front)) * 2.0 - vec2f(1.0);
    var grainNormal: vec3f = vec3f(grainTilt, sqrt(max(1.0 - dot(grainTilt, grainTilt), 0.0)));
    nm = normalize(mix(grainNormal, nm, made));
    grit = mix(tap(clean, 3.0, front), grit, made);
    tint *= made;
  }
  var moltenAmount: f32 = 0.0;
  if (front && resources.frame.forge.z > 0.0 && resources.frame.forge.z < 1.0) {
    moltenAmount = sin(3.141593 * resources.frame.forge.z) * resources.frame.forge.y;
    nm = vec3f(nm.xy * (1.0 - 0.5 * moltenAmount * made), nm.z);
    nm = normalize(nm);
  }
  var detail: vec3f = select(vec3f(-nm.x, nm.y, -nm.z), nm, front);

  detail = vec3f(detail.xy * select(1.0, mix(0.85, 1.0, tint), front), detail.z);
  detail = normalize(detail);
  n = normalize(mix(n, detail, facing));


  var iron: vec3f = vec3f(0.024, 0.024, 0.027);
  var gold: vec3f = select(resources.frame.metalNext.xyz, resources.frame.metal.xyz, resources.frame.forge.z < 0.5);
  var satin: vec3f = vec3f(0.062, 0.062, 0.066);
  var f0: vec3f = iron;
  var rough: f32 = 0.46;
  if (front) {
    f0 = mix(iron, gold, tint * facing);
    rough = mix(rough, grit, facing);
  } else {
    f0 = mix(iron, satin + vec3f(0.24) * tint, facing);
    rough = mix(rough, grit, facing);
  }

  var nw: vec3f = toWorld(n);
  var view: vec3f = -ray;
  var nv: f32 = clamp(dot(nw, view), 0.0, 1.0);
  var fresnel: vec3f = f0 + (vec3f(1.0) - f0) * pow(1.0 - nv, 5.0) * (1.0 - rough * 0.7);
  var colour: vec3f = fresnel * room(reflect(ray, nw), rough, select(0.0, tint * facing, front));

  colour *= mix(0.52, 1.0, smoothstep(-1.05, 0.55, toWorld(p).y));

  var keyDir: vec3f = leanLamp(normalize(vec3f(-0.52, 0.60, 0.61)));
  colour += f0 * 0.08 * (0.25 + max(dot(nw, keyDir), 0.0));

  colour += f0 * 0.16 * min(length(resources.frame.light.xy), 1.0);



  var stripeY: f32 = -0.12 + resources.frame.light.xy.y * 0.40;
  var fingerBand: f32 = exp(-squared((toWorld(p).y - stripeY) / 0.14));
  var rimSide: f32 = smoothstep(0.32, 0.53, abs(p.x));
  colour += facing * tint * gold * fingerBand * (0.45 + 0.7 * length(resources.frame.light.xy));
  colour += vec3f(0.065, 0.059, 0.048) * rimSide
    * (0.25 + 1.75 * max(0.0, sign(p.x) * resources.frame.light.xy.x));



  var band: f32 = exp(-squared((p.x + p.y * 0.32 - resources.frame.strike.y) / 0.085));
  colour += facing * band * vec3f(1.0, 0.96, 0.88) * (0.22 + 1.5 * (select(0.25, tint, front)));


  if (front && resources.frame.strike.x > 0.001) {
    var ember: vec3f = mix(vec3f(1.0, 0.26, 0.03), vec3f(1.0, 0.88, 0.66), resources.frame.strike.x * resources.frame.strike.x);
    colour += tint * facing * resources.frame.strike.x * ember * (0.7 + 24.0 * resources.frame.strike.x * resources.frame.strike.x * resources.frame.strike.x);
    var around: f32 = (tintNear(uv, vec2f(16.0, 0.0)) + tintNear(uv, vec2f(-16.0, 0.0)) +
                    tintNear(uv, vec2f(0.0, 16.0)) + tintNear(uv, vec2f(0.0, -16.0)) +
                    tintNear(uv, vec2f(11.0, 11.0)) + tintNear(uv, vec2f(-11.0, 11.0)) +
                    tintNear(uv, vec2f(11.0, -11.0)) + tintNear(uv, vec2f(-11.0, -11.0))) / 8.0;
    colour += (1.0 - tint) * facing * around * resources.frame.strike.x * vec3f(1.0, 0.34, 0.05) * 1.6;
  }

  if (front && resources.frame.forge.x > 0.0) {
    var drifted: f32 = order + heatGrain(uv);
    var climbed: f32 = chargeAt(order, 0.56 - heatGrain(uv));
    var behind: f32 = resources.frame.forge.x - drifted;
    var crest: f32 = exp(-squared(behind / 0.14));
    var breathing: f32 = 0.92 + 0.08 * sin(resources.frame.forge.x * 28.0 + grit * 31.0);
    var warmMetal: vec3f = mix(vec3f(0.55, 0.012, 0.001), vec3f(1.0, 0.23, 0.015), exp(-max(behind, 0.0) * 5.0));
    warmMetal = mix(warmMetal, vec3f(1.0, 0.87, 0.65), crest);
    colour += facing * climbed * warmMetal * (0.65 + crest * 1.7) * breathing * ink;
    var beside: f32 = (chargeNear(uv, vec2f(14.0, 0.0)) + chargeNear(uv, vec2f(-14.0, 0.0)) +
                    chargeNear(uv, vec2f(0.0, 14.0)) + chargeNear(uv, vec2f(0.0, -14.0))) * 0.25;
    colour += facing * (1.0 - climbed) * beside * vec3f(0.025, 0.004, 0.0005);
  }

  if (moltenAmount > 0.0) {

    var bath: f32 = exp(-squared(squared(p.x / 0.28)) - squared(squared((p.y - 0.04) / 0.62)));
    colour += facing * moltenAmount * bath * vec3f(0.48, 0.07, 0.007);
    colour += facing * tint * moltenAmount * vec3f(3.8, 2.0, 0.65);
  }

  if (!front && resources.frame.forge.w > 0.0 && resources.frame.forge.w < 1.0 && order > 0.001) {
    var age: f32 = resources.frame.forge.w - order;
    var pointHeat: f32 = exp(-squared(age / 0.012));
    var tailHeat: f32 = smoothstep(-0.02, 0.0, age) * (1.0 - smoothstep(0.0, 0.06, age));

    var stroke: f32 = 0.18 + tint * 0.82;
    colour += facing * stroke * (pointHeat * vec3f(5.0, 2.4, 0.65) + tailHeat * vec3f(0.9, 0.16, 0.018));
  }

  return Out(vec4f(colour * edge, edge) + atmosphere * (1.0 - edge), (CAM - toWorld(p).z) / 12.0);
}
`).$uses({ surface, resources: frameLayout.$ }).$name('medalFragment');

export const turn = tgpu.fn([d.vec2f, d.f32], d.vec2f)(`(v, a) {
  return vec2f(cos(a) * v.x - sin(a) * v.y, sin(a) * v.x + cos(a) * v.y);
}`);
export const toObject = tgpu.fn([d.vec3f], d.vec3f)(`(v) {
  return vec3f(dot(uniforms.frame.rot0.xyz, v), dot(uniforms.frame.rot1.xyz, v), dot(uniforms.frame.rot2.xyz, v));
}`).$uses({ uniforms: frameLayout.$ });
export const toWorld = tgpu.fn([d.vec3f], d.vec3f)(`(v) {
  return mat3x3f(uniforms.frame.rot0.xyz, uniforms.frame.rot1.xyz, uniforms.frame.rot2.xyz) * v;
}`).$uses({ uniforms: frameLayout.$ });
export const stadium = tgpu.fn([d.vec2f], d.f32)(`(p) {
  return length(vec2f(p.x, max(abs(p.y) - (1.0 - A), 0.0))) - A;
}`).$uses({ A });
