// Frost Chest Opening — the burst's ice chips and the light dust of the reveal, with WebGPU.
//
// Ice chips: the video throws small cyan chips out of the opened chest next to the stars. Here each
// is a faceted solid (an eight-face bipyramid, squat so it reads as a chip) tumbling about its own
// axis, lit with a fresnel rim and a specular glint that travel across its faces as it turns.
// Light dust: 180 soft motes rise in the beam the card is born in, swaying, twinkling, with a depth
// of field (near ones big and dim, far ones small and sharp). Both are one instanced draw, every
// value a function of the burst time, so it is the same every loop; the Skia fallbacks are
// drawChips and drawMotes in fco-draw.ts.
//
// The canvas is transparent (premultiplied alpha) and sits over the Skia canvas without touches.
// It only renders while the burst runs; outside it, it is cleared once and left idle. When the
// native module is missing (Expo Go) or anything fails, it reports it and the Skia versions stay.

import { useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, TurboModuleRegistry, View } from 'react-native';
import { type SharedValue, useAnimatedReaction } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import type { RNCanvasContext } from 'react-native-webgpu';

import { frameAt, T } from './fco-motion';

type WebGPU = typeof import('react-native-webgpu');

export const GPU_SHARDS = 30;
/** Light dust: soft glowing motes rising in the beam, with depth of field. */
export const GPU_MOTES = 180;

const WGSL = /* wgsl */ `
struct U {
  size: vec2f,    // canvas size in points
  origin: vec2f,  // burst origin in points (the chest mouth)
  tau: f32,       // seconds since the burst started
  unit: f32,      // points per design point (screen width / 402)
  fade: f32,      // overall opacity
  pad: f32,
};
@group(0) @binding(0) var<uniform> u: U;

struct V {
  @builtin(position) pos: vec4f,
  @location(0) n: vec3f,
  @location(1) a: f32,
  @location(2) tint: f32,
  @location(3) uv: vec2f,
  @location(4) kind: f32, // 0 = ice chip, 1 = light mote
};

fn hash(i: f32, s: f32) -> f32 {
  return fract(sin(i * 127.1 + s * 311.7) * 43758.5453);
}

fn rot(axis: vec3f, ang: f32, v: vec3f) -> vec3f {
  let c = cos(ang);
  let s = sin(ang);
  return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

// One shard: a four-sided bipyramid, eight triangles. Tip up at +y.
fn corner(k: u32, w: f32, h: f32) -> vec3f {
  let tri = k / 3u;
  let j = k % 3u;
  let side = tri % 4u;
  let top = tri < 4u;
  if (j == 0u) {
    return select(vec3f(0.0, -h * 0.55, 0.0), vec3f(0.0, h, 0.0), top);
  }
  var idx = side;
  if (j == 2u) { idx = (side + 1u) % 4u; }
  if (!top) {
    if (j == 1u) { idx = (side + 1u) % 4u; } else { idx = side; }
  }
  let ang = f32(idx) * 1.5707963 + 0.35;
  return vec3f(cos(ang) * w, h * 0.08, sin(ang) * w * 0.6);
}

// A light mote: a camera-facing quad (the first 6 vertices; the rest collapse) with a soft disc.
fn mote(vi: u32, ii: u32) -> V {
  var out: V;
  out.kind = 1.0;
  out.n = vec3f(0.0, 0.0, 1.0);
  out.tint = 0.0;
  if (vi >= 6u) {
    out.pos = vec4f(2.0, 2.0, 0.0, 1.0);
    out.a = 0.0;
    out.uv = vec2f(0.0);
    return out;
  }
  let corners = array<vec2f, 6>(vec2f(-1.0, -1.0), vec2f(1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, 1.0));
  let c = corners[vi];
  let i = f32(ii) + 100.0;
  let born = 0.03 + 0.9 * pow(hash(i, 21.0), 1.4);
  let life = 0.9 + 0.8 * hash(i, 22.0);
  let t = u.tau - born;
  let k = clamp(t / life, 0.0, 1.0);
  let depth = hash(i, 23.0);              // 0 near, 1 far
  // They rise either side of the card (few cross its face), the far ones spread wider.
  let side = select(-1.0, 1.0, hash(i, 28.0) > 0.5);
  let x = side * (38.0 + (60.0 + 80.0 * depth) * hash(i, 24.0)) + 16.0 * sin(t * (1.6 + hash(i, 25.0)) + i);
  let y = -20.0 * hash(i, 26.0) - (60.0 + 140.0 * hash(i, 27.0)) * max(t, 0.0);
  // Out of focus near the camera: bigger and dimmer; far ones are small, sharp points.
  let size = mix(5.5, 1.3, depth) * u.unit;
  let centre = u.origin + vec2f(x, y) * u.unit;
  let screen = centre + c * size * 2.2;
  out.pos = vec4f(screen.x / u.size.x * 2.0 - 1.0, 1.0 - screen.y / u.size.y * 2.0, 0.5, 1.0);
  out.uv = c;
  let twinkle = 0.65 + 0.35 * sin(t * 9.0 + i * 3.0);
  out.a = step(0.0, t) * sin(3.14159 * k) * twinkle * mix(0.55, 1.0, depth) * u.fade;
  return out;
}

@vertex
fn vs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> V {
  if (ii >= ${GPU_SHARDS}u) {
    return mote(vi, ii);
  }
  let i = f32(ii);
  let w = 0.5 + 0.2 * hash(i, 4.0);
  let h = 1.0;
  let tri = vi / 3u;
  let p0 = corner(tri * 3u, w, h);
  let p1 = corner(tri * 3u + 1u, w, h);
  let p2 = corner(tri * 3u + 2u, w, h);
  let p = corner(vi, w, h);
  var n = normalize(cross(p1 - p0, p2 - p0));

  // Trajectory (the video's chips): born over the chest mouth within 0.3 s, drifting outwards and a
  // little up with drag, each living 0.35–0.57 s; depth only adds perspective.
  let born0 = 0.3 * hash(i, 12.0);
  let life = 0.35 + 0.22 * hash(i, 13.0);
  let t = max(u.tau - born0, 0.0);
  let start = vec2f((hash(i, 1.0) - 0.5) * 250.0, (hash(i, 2.0) - 0.6) * 90.0) * u.unit;
  let away = start - vec2f(0.0, 75.0) * u.unit;
  let dir = normalize(away + vec2f(0.0, 0.001));
  let speed = (40.0 + 90.0 * hash(i, 11.0)) * u.unit;
  let travel = speed * (1.0 - exp(-2.5 * t)) / 2.5;
  let grav = -18.0 * u.unit * t;
  let depth = (hash(i, 3.0) - 0.5) * 1.4;
  let persp = 1.0 / (1.0 - depth * 0.35);
  let len = (3.4 + 3.2 * hash(i, 5.0)) * u.unit;

  // Tumble about a random axis; the shard points along its flight first.
  let axis = normalize(vec3f(hash(i, 6.0) - 0.5, hash(i, 7.0) - 0.5, hash(i, 8.0) - 0.5) + vec3f(0.0, 0.0, 0.001));
  let spin = t * (6.0 + 10.0 * hash(i, 9.0)) + hash(i, 10.0) * 6.28;
  var q = rot(axis, spin, p);
  n = rot(axis, spin, n);
  let lean = atan2(dir.x, -dir.y);
  let cl = cos(lean);
  let sl = sin(lean);
  q = vec3f(q.x * cl - q.y * sl, q.x * sl + q.y * cl, q.z);
  n = vec3f(n.x * cl - n.y * sl, n.x * sl + n.y * cl, n.z);

  let centre = u.origin + start + (dir * travel + vec2f(0.0, grav)) * persp;
  let screen = centre + vec2f(q.x, -q.y) * len * persp;
  let ndc = vec2f(screen.x / u.size.x * 2.0 - 1.0, 1.0 - screen.y / u.size.y * 2.0);

  let k = t / life;
  let born = step(born0, u.tau) * smoothstep(0.0, 0.12, k);
  let dies = 1.0 - smoothstep(0.6, 1.0, k);
  var out: V;
  out.pos = vec4f(ndc, 0.5 + depth * 0.2 + q.z * 0.01, 1.0);
  out.n = n;
  out.a = born * dies * u.fade;
  out.tint = hash(i, 14.0);
  out.uv = vec2f(0.0);
  out.kind = 0.0;
  return out;
}

@fragment
fn fs(v: V) -> @location(0) vec4f {
  if (v.kind > 0.5) {
    let d = dot(v.uv, v.uv);
    let glow = exp(-d * 4.0);
    let core = exp(-d * 18.0);
    let col = mix(vec3f(0.6, 0.9, 1.0), vec3f(1.0), core);
    let a = clamp(glow * v.a, 0.0, 1.0);
    return vec4f(col * a * 0.8, a * 0.6);
  }
  let n = normalize(v.n);
  let view = vec3f(0.0, 0.0, 1.0);
  let facing = abs(dot(n, view));
  let fres = pow(1.0 - facing, 2.0);
  let light = normalize(vec3f(-0.45, 0.65, 0.6));
  let spec = pow(max(dot(reflect(-light, n), view), 0.0), 18.0);
  let diff = 0.55 + 0.45 * abs(dot(n, light));
  let ice = mix(vec3f(0.33, 0.80, 0.96), vec3f(0.50, 0.88, 0.99), v.tint);
  var col = ice * diff;
  col = mix(col, vec3f(0.92, 0.98, 1.0), fres * 0.7);
  col = col + vec3f(spec);
  let a = clamp((0.7 + 0.3 * fres + spec) * v.a * 0.88, 0.0, 1.0);
  return vec4f(min(col, vec3f(1.0)) * a, a);
}
`;

type Props = {
  time: SharedValue<number>;
  width: number;
  height: number;
  origin: { x: number; y: number };
  unit: number;
  onAvailable: (ok: boolean) => void;
};

export function FcoShardBurst(props: Props) {
  const [webgpu, setWebgpu] = useState<WebGPU | null>(null);
  const report = useRef(props.onAvailable);
  useEffect(() => {
    report.current = props.onAvailable;
  });
  useEffect(() => {
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        if (!TurboModuleRegistry.get('WebGPUModule')) {
          report.current(false);
          return;
        }
        // Expo Go has no native module, so the package is only loaded behind this check.
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        setWebgpu(require('react-native-webgpu') as WebGPU);
      } catch {
        report.current(false);
      }
    });
    return () => {
      live = false;
    };
  }, []);
  if (!webgpu) return null;
  return <BurstCanvas {...props} webgpu={webgpu} />;
}

function BurstCanvas({ webgpu, time, width, height, origin, unit, onAvailable }: Props & { webgpu: WebGPU }) {
  const { Canvas, useCanvasRef } = webgpu;
  const ref = useCanvasRef();
  const [running, setRunning] = useState(false);
  const gpu = useRef<{ device: GPUDevice; context: RNCanvasContext; pipeline: GPURenderPipeline; buffer: GPUBuffer; group: GPUBindGroup } | null>(null);
  const report = useRef(onAvailable);
  useEffect(() => {
    report.current = onAvailable;
  });

  useAnimatedReaction(
    () => time.get() >= T.sparkle[0] && time.get() < T.motes[1],
    (now, before) => {
      if (now !== before) scheduleOnRN(setRunning, now);
    },
  );

  // Set-up: adapter, device, pipeline. Any failure hands the burst back to Skia.
  useEffect(() => {
    let live = true;
    const start = async () => {
      const nav = globalThis.navigator as Navigator & { gpu?: GPU };
      const adapter = await nav.gpu?.requestAdapter();
      if (!adapter) throw new Error('No WebGPU adapter.');
      const device = await adapter.requestDevice();
      if (!live) {
        device.destroy();
        return;
      }
      const context = ref.current?.getContext('webgpu') ?? null;
      if (!context) throw new Error('No WebGPU context.');
      const ratio = PixelRatio.get();
      context.canvas.width = Math.round(width * ratio);
      context.canvas.height = Math.round(height * ratio);
      const format = nav.gpu!.getPreferredCanvasFormat();
      context.configure({ device, format, alphaMode: 'premultiplied' });
      const module = device.createShaderModule({ code: WGSL });
      const pipeline = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module, entryPoint: 'vs' },
        fragment: {
          module,
          entryPoint: 'fs',
          targets: [{ format, blend: { color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' }, alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' } } }],
        },
        primitive: { topology: 'triangle-list', cullMode: 'none' },
        depthStencil: undefined,
      });
      const buffer = device.createBuffer({ size: 32, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const group = device.createBindGroup({ layout: pipeline.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer } }] });
      gpu.current = { device, context, pipeline, buffer, group };
      report.current(true);
    };
    start().catch(() => {
      if (live) report.current(false);
    });
    return () => {
      live = false;
      const g = gpu.current;
      gpu.current = null;
      try {
        g?.context.unconfigure();
        g?.buffer.destroy();
        g?.device.destroy();
      } catch {
        // Already lost.
      }
    };
  }, [ref, width, height]);

  // Render while the burst runs; clear once when it ends.
  useEffect(() => {
    let frame = 0;
    const draw = (clear: boolean) => {
      const g = gpu.current;
      if (!g) return;
      const tau = time.get() - T.sparkle[0];
      g.device.queue.writeBuffer(g.buffer, 0, new Float32Array([width, height, origin.x, origin.y, tau, unit, 1 - frameAt(time.get(), 0).flash, 0]));
      const encoder = g.device.createCommandEncoder();
      const pass = encoder.beginRenderPass({
        colorAttachments: [{ view: g.context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }],
      });
      if (!clear) {
        pass.setPipeline(g.pipeline);
        pass.setBindGroup(0, g.group);
        pass.draw(24, GPU_SHARDS + GPU_MOTES);
      }
      pass.end();
      g.device.queue.submit([encoder.finish()]);
      g.context.present();
    };
    if (!running) {
      draw(true);
      return;
    }
    const loop = () => {
      draw(false);
      frame = requestAnimationFrame(loop);
    };
    loop();
    return () => cancelAnimationFrame(frame);
  }, [running, time, width, height, origin.x, origin.y, unit]);

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <Canvas ref={ref} style={StyleSheet.absoluteFill} opaque={false} />
    </View>
  );
}
