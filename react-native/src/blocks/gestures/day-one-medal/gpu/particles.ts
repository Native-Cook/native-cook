import { d, tgpu } from 'typegpu';
import { A, CAM, H, lamp, stadium, toObject, toWorld, turn } from './medal-shading';
import { frameLayout, leafLayout, simulationLayout } from './schemas';

const uniforms = frameLayout.$;

export { seedParticles } from './leaf-seed';

export const simulate = tgpu.computeFn({
  in: { gid: d.builtin.globalInvocationId }, workgroupSize: [64],
})(`{
  let id = gid.x;
  if (id >= u32(uniforms.frame.centre.w)) { return; }
  var leaf = simulation.particles[id];
  let dt = uniforms.frame.centre.z;
  if (dt == 0.0 && uniforms.frame.strike.z == 0.0) { return; }
  let clock = uniforms.frame.motion.w;
  let old = leaf.position.xyz;
  var p = old;
  let radius = max(length(p.xz), 0.001);
  // A broad intake feeds a narrow orbit. The nearest leaves acquire the most momentum.
  let proximity = exp(-radius * radius / 5.0 - p.y * p.y / 5.0);
  let spin = uniforms.frame.motion.z;
  var air = leaf.velocity.w * exp(-dt / 1.2);
  air = clamp(air + spin * proximity * dt * 6.5, -24.0, 24.0);
  let capture = smoothstep(1.2, 8.0, abs(air));
  let powered = smoothstep(0.0, 6.0, abs(spin));
  let radial = p.xz / radius;
  // Slightly tilted helical lanes keep the front/back orbit readable under projection.
  // This changes actual positions, not a painted screen-space halo.
  let orbitRadius = 1.03 + 0.30 * leaf.position.w;
  let inward = (orbitRadius - radius) * 4.0 * powered;
  let release = 0.55 * (1.0 - powered);
  p = vec3f(p.xz.x + radial.x * (inward + release) * capture * dt, p.y,
    p.xz.y + radial.y * (inward + release) * capture * dt);
  let orbit = turn(p.zx, air * dt / (0.65 + radius * 0.4));
  p = vec3f(orbit.y, p.y, orbit.x);
  let tiltChange = 0.27 * (p.x - old.x) + 0.42 * (p.z - old.z);
  p.y += tiltChange * capture;
  let lane = floor(leaf.position.w * 3.0) - 1.0;
  let laneY = lane * 0.72 + 0.27 * p.x + 0.42 * p.z + 0.17 * sin(leaf.shape.z);
  p.y += (laneY - p.y) * (1.0 - exp(-dt * 4.5 * capture * powered));
  let drift = leaf.fall.y * leaf.fall.z * cos(clock * leaf.fall.z + leaf.shape.z);
  // A rising heat edge launches a shell. Holding/cooling heat never adds momentum.
  var kick = leaf.velocity.xyz * exp(-dt / 0.48);
  let fromCentre = p + vec3f(0.0001);
  kick += normalize(fromCentre) * uniforms.frame.strike.z * 9.0
    * exp(-dot(p, p) / 4.0);
  let rain = vec3f(drift, -leaf.fall.x, 0.0) * (1.0 - capture * 0.94 * powered);
  p += (rain + kick) * dt;
  let previousLocal = toObject(old);
  var local = toObject(p);
  let clearance = max(leaf.shape.x, leaf.shape.y) * 0.65 + 0.015;
  let thickness = H + 0.035 + clearance;
  let crossed = previousLocal.z * local.z < 0.0;
  let fraction = clamp(abs(previousLocal.z) / max(abs(local.z - previousLocal.z), 0.00001), 0.0, 1.0);
  let crossing = mix(previousLocal, local, fraction);
  let sweptHit = crossed && stadium(crossing.xy) < clearance;
  if ((abs(local.z) < thickness && stadium(local.xy) < clearance) || sweptHit) {
    if (sweptHit) { local = vec3f(crossing.xy, local.z); }
    let cap = vec2f(0.0, clamp(local.y, -(1.0 - A), 1.0 - A));
    var outward = local.xy - cap;
    if (length(outward) < 0.0001) { outward = vec2f(select(-1.0, 1.0, leaf.position.w > 0.5), 0.0); }
    outward = normalize(outward);
    let side = select(-1.0, 1.0, local.x + (leaf.position.w - 0.5) * 0.01 >= 0.0);
    outward = turn(outward, -side * dt * 0.65);
    local = vec3f(cap + outward * (A + clearance), local.z);
    p = toWorld(local);
  }
  if (abs(p.z) > 2.4) { p.z = clamp(p.z, -2.4, 2.4); kick.z = 0.0; }
  // Record travel before wrapping, so crossing a canvas edge never makes a giant streak.
  let travel = (p - old) / max(dt, 0.0001);
  let perspective = (CAM - p.z) / CAM;
  let bottom = -(uniforms.frame.viewport.y - uniforms.frame.centre.y) / uniforms.frame.viewport.z * perspective - 0.22;
  let top = uniforms.frame.centre.y / uniforms.frame.viewport.z * perspective + 0.22;
  if (p.y < bottom) { p.y += top - bottom; }
  let span = uniforms.frame.viewport.x / uniforms.frame.viewport.z * perspective * 1.15;
  let offset = (uniforms.frame.viewport.x * 0.5 - uniforms.frame.centre.x) / uniforms.frame.viewport.z * perspective;
  if (p.x > offset + span * 0.5) { p.x -= span; }
  if (p.x < offset - span * 0.5) { p.x += span; }
  leaf.position = vec4f(p, leaf.position.w);
  leaf.velocity = vec4f(kick, air);
  leaf.travel = vec4f(travel, capture);
  simulation.particles[id] = leaf;
}`).$uses({ simulation: simulationLayout.$, uniforms, turn, toObject, toWorld, stadium, A, H, CAM })
  .$name('simulateGoldLeaf');

const varyings = {
  uv: d.vec2f,
  radiance: d.vec3f,
  softness: d.f32,
  opacity: d.f32,
  bokeh: d.f32,
  seed: d.f32,
  trail: d.f32,
  projected: d.vec2f,
};
export const leafVertex = tgpu.vertexFn({
  in: { vertexIndex: d.builtin.vertexIndex, instanceIndex: d.builtin.instanceIndex },
  out: { position: d.builtin.position, ...varyings },
})(`{
  let leaf = leaves.particles[instanceIndex];
  let clock = uniforms.frame.motion.w;
  let corners = array<vec2f, 6>(vec2f(-1.0,-1.0), vec2f(1.0,-1.0), vec2f(-1.0,1.0),
    vec2f(-1.0,1.0), vec2f(1.0,-1.0), vec2f(1.0,1.0));
  let corner = corners[vertexIndex % 6u];
  let piece = vertexIndex / 6u;
  let isTrail = piece < 2u;
  let rotation = leaf.shape.z + leaf.shape.w * clock;
  let tumble = leaf.position.w * 6.283185 + leaf.fall.w * clock;
  let twist = leaf.shape.z * 1.7 + clock * 0.43;
  let axisU = vec3f(cos(rotation), sin(rotation), 0.0);
  let axisV = vec3f(-sin(rotation) * cos(tumble), cos(rotation) * cos(tumble), sin(tumble));
  let normalBase = cross(axisU, axisV);
  let u = axisU * cos(twist) + normalBase * sin(twist);
  let v = axisV;
  let n = normalize(cross(u, v));
  let centre = leaf.position.xyz;
  let w = (CAM - centre.z) / CAM;
  let centreScreen = vec2f(centre.x, -centre.y) * uniforms.frame.viewport.z / w;
  let bokeh = select(0.0, smoothstep(0.95, 1.65, centre.z), instanceIndex < 4u);
  // Project the true tumbling plane. Defocused foreground pieces expand into low-alpha discs.
  let irregular = corner * vec2f(1.0 - 0.12 * leaf.position.w * corner.y,
    1.0 + 0.09 * sin(leaf.shape.z) * corner.x);
  let local = (u * irregular.x * leaf.shape.x + v * irregular.y * leaf.shape.y) * 0.5;
  var offset = vec2f(local.x, -local.y) * uniforms.frame.viewport.z / w;
  let discRadius = leaf.shape.x * uniforms.frame.viewport.z / w * 1.15;
  offset = mix(offset, corner * discRadius, bokeh);
  // Two capped shutter segments curve along actual travel; the final quad is sharp foil.
  let worldVelocity = leaf.travel.xyz;
  let projectedVelocity = vec2f(worldVelocity.x + centre.x * worldVelocity.z / (CAM - centre.z),
    -worldVelocity.y - centre.y * worldVelocity.z / (CAM - centre.z)) * uniforms.frame.viewport.z / w;
  let speed = length(projectedVelocity);
  let direction = projectedVelocity / max(speed, 0.001);
  let shutter = min(0.016, 1.5 * leaf.shape.x * uniforms.frame.viewport.z / w / max(speed, 0.001))
    * max(leaf.travel.w, smoothstep(0.5, 2.0, length(leaf.velocity.xyz)));
  let blur = speed * shutter;
  var trail = 0.0;
  var depth = centre.z + local.z * (1.0 - bokeh);
  if (isTrail) {
    trail = (f32(piece) + (corner.x + 1.0) * 0.5) * 0.5;
    let age = shutter * trail;
    let omega = leaf.velocity.w / (0.65 + length(centre.xz) * 0.4);
    let acceleration = -omega * omega * vec3f(centre.x, 0.27 * centre.x + 0.42 * centre.z, centre.z);
    let previous = centre - worldVelocity * age + acceleration * age * age * 0.5;
    let previousW = (CAM - previous.z) / CAM;
    let previousScreen = vec2f(previous.x, -previous.y) * uniforms.frame.viewport.z / previousW;
    let halfWidth = leaf.shape.y * uniforms.frame.viewport.z * 0.40 / w;
    offset = previousScreen - centreScreen + vec2f(-direction.y, direction.x) * corner.y
      * halfWidth * (1.0 - 0.30 * trail);
    depth = previous.z;
  }
  let centreY = uniforms.frame.centre.y + centreScreen.y;
  let below = smoothstep(uniforms.frame.leaf.x - 24.0 * uniforms.frame.viewport.w,
    uniforms.frame.leaf.x, centreY);
  let header = 1.0 - smoothstep(uniforms.frame.leaf.y + 48.0 * uniforms.frame.viewport.w,
    uniforms.frame.leaf.y + 80.0 * uniforms.frame.viewport.w, centreY);
  let chip = smoothstep(uniforms.frame.viewport.x - 84.0 * uniforms.frame.viewport.w,
    uniforms.frame.viewport.x - 64.0 * uniforms.frame.viewport.w, uniforms.frame.centre.x + centreScreen.x)
    * (1.0 - smoothstep(uniforms.frame.leaf.y + 125.0 * uniforms.frame.viewport.w,
      uniforms.frame.leaf.y + 145.0 * uniforms.frame.viewport.w, centreY));
  let quietArea = max(below, max(header, chip));
  offset *= mix(1.0, 0.38, below);
  let screen = uniforms.frame.centre.xy + centreScreen + offset;
  let ndc = screen / uniforms.frame.viewport.xy * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0);
  let viewDirection = normalize(vec3f(0.0, 0.0, CAM) - centre);
  let reflected = reflect(-viewDirection, n);
  let keyDirection = normalize(vec3f(-0.52, 0.60, 0.61));
  let key = lamp(reflected, keyDirection, 0.150);
  let low = lamp(reflected, normalize(vec3f(-0.10, -0.17, 0.98)), 0.130);
  let diffuse = abs(dot(n, keyDirection));
  let gold = mix(vec3f(0.58, 0.245, 0.045), vec3f(0.92, 0.64, 0.30), leaf.position.w);
  let colour = gold * (0.35 + pow(diffuse, 0.65) * 1.10)
    + vec3f(1.0, 0.89, 0.66) * (key * 24.0 + low * 28.0);
  let heat = uniforms.frame.strike.x;
  let centreLight = heat * heat * 5.0 / (0.30 + dot(centre, centre))
    * (0.2 + 0.8 * abs(dot(n, normalize(-centre + vec3f(0.0001)))));
  let bright = colour + vec3f(1.0, 0.72, 0.30) * centreLight;
  let lit = mix(bright, min(bright, vec3f(0.12, 0.065, 0.025)), quietArea);
  let softness = mix(0.035, 0.12, smoothstep(0.35, 1.7, abs(centre.z)));
  let projected = (screen - uniforms.frame.centre.xy) / uniforms.frame.viewport.z;
  let speck = smoothstep(1.5, 3.0, leaf.shape.x * uniforms.frame.viewport.z / uniforms.frame.viewport.w);
  var opacity = mix(0.30, 0.92, speck) * mix(1.0, 0.16, bokeh);
  opacity *= mix(1.0, 0.18, quietArea);
  opacity *= smoothstep(uniforms.frame.leaf.y, uniforms.frame.leaf.y + 16.0 * uniforms.frame.viewport.w, screen.y);
  if (isTrail) { opacity *= 0.30 * smoothstep(0.5, 3.0, blur / uniforms.frame.viewport.w) * (1.0 - bokeh); }
  return Out(vec4f(ndc * w, (CAM - depth) / 12.0 * w, w), corner, lit, softness, opacity, bokeh, leaf.position.w, trail, projected);
}`).$uses({ leaves: leafLayout.$, uniforms, CAM, lamp }).$name('goldLeafVertex');

function foilFragment(bloom: boolean) {
  return tgpu.fragmentFn({ in: varyings, out: d.vec4f })(`{
  // Mostly rectangular torn foil: unequal cut corners, never a capsule silhouette.
  let uv = varying.uv;
  let cut = abs(uv.x + uv.y * (0.78 + varying.seed * 0.24)) / 1.78;
  let rectangle = max(max(abs(uv.x), abs(uv.y)), cut);
  let edge = mix(rectangle, length(uv), varying.bokeh);
  let aa = max(fwidth(edge), mix(varying.softness, 0.70, varying.bokeh));
  let quiet = varying.projected / vec2f(0.58, 1.06);
  let quietSquared = quiet * quiet;
  let subject = exp(-dot(quietSquared, quietSquared));
  let tail = 1.0 - smoothstep(0.05, 1.0, varying.trail) * 0.94;
  let foil = 1.0 - smoothstep(1.0 - aa, 1.0, edge);
  let gaussian = exp(-dot(uv, uv) * 4.5) * (1.0 - smoothstep(0.7, 1.0, length(uv)));
  // Absolutely no foreground bokeh over the face; ordinary foil fades before the numeral.
  let faceClear = smoothstep(0.08, 0.60, 1.0 - subject);
  let alpha = select(foil, gaussian, varying.bokeh > 0.0) * varying.opacity * faceClear * tail;
  let fold = 0.91 + 0.09 * smoothstep(-0.15, 0.15, uv.x + uv.y * 0.35);
  let radiance = varying.radiance * fold;
  // Scale every channel together: the foil keeps its hue below the SDR shoulder.
  // Excess energy goes to a separate, blurred light target, never the sharp quad.
  let peak = max(radiance.r, max(radiance.g, radiance.b));
  let core = radiance * min(1.0, 0.72 / max(peak, 0.0001));
  ${bloom ? `let excess = max(radiance - core, vec3f(0.0));
  return vec4f(excess * alpha * gaussian, 0.0);` : 'return vec4f(core * alpha, alpha);'}
}`).$uses({ varying: tgpu['~unstable'].rawCodeSnippet('in', d.struct(varyings)) })
    .$name(bloom ? 'goldLeafBloomFragment' : 'goldLeafFragment');
}

export const leafFragment = foilFragment(false);
export const leafBloomFragment = foilFragment(true);
