import { MEDAL_METALS } from '../medal-orientation';
import { seedParticles } from './leaf-seed';
import shaderCode from './shaders.generated.json';
import type { SetupSlice } from './setup';
import type { MedalFrame, MedalSceneOptions } from './authoring-scene';
export type { MedalFrame, MedalSceneOptions } from './authoring-scene';

/** Native runtime uses pre-resolved WGSL. TypeGPU remains the authoring source;
 * shader resolution must never run on the phone's interaction thread. */
export function createMedalScene(device: GPUDevice, format: GPUTextureFormat, options: MedalSceneOptions) {
  const { width, height, pixelRatio, centre, half } = options;
  if (![width, height, pixelRatio, half, ...centre].every(Number.isFinite)
    || width <= 0 || height <= 0 || half <= 0 || pixelRatio <= 0
    || !Number.isInteger(width) || !Number.isInteger(height)) throw new Error('Invalid medal canvas dimensions');
  const count = options.flakes ?? 900;
  if (!Number.isInteger(count) || count < 0 || count > 100000) throw new Error('Invalid medal flake count');
  const owned: { destroy(): void }[] = [];
  let destroyed = false;
  let prepared = false;
  let activeCount = Math.min(count, options.quality === 'low' ? 360 : options.quality === 'medium' ? 600 : 900);
  let uniform: GPUBuffer;
  let particles: GPUBuffer;
  let initial: Float32Array;
  let hdr: GPUTextureView;
  let emission: GPUTextureView;
  let depth: GPUTextureView;
  const down: GPUTextureView[] = [], up: GPUTextureView[] = [], blurred: GPUTextureView[] = [];
  const layouts: GPUBindGroupLayout[] = [];
  const modules: GPUShaderModule[] = [];
  const pipelines: (GPURenderPipeline | GPUComputePipeline)[] = [];
  const groups: GPUBindGroup[][] = [];
  const faces: GPUBindGroup[] = [];
  const post: GPUBindGroup[] = [];
  let linear: GPUSampler;
  let lastTime = -Infinity, lastHeat = 0, struckAt = -Infinity;
  const data = new Float32Array(48);
  const destroy = () => {
    if (destroyed) return;
    destroyed = true;
    for (const resource of owned) resource.destroy();
    owned.length = 0;
    modules.length = pipelines.length = groups.length = faces.length = post.length = 0;
  };
  const texture = (w: number, h: number, textureFormat: GPUTextureFormat = 'rgba16float') => {
    const t = device.createTexture({ size: [w, h], format: textureFormat,
      usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING });
    owned.push(t); return t.createView();
  };
  const buffer = (size: number, usage: GPUBufferUsageFlags) => {
    const b = device.createBuffer({ size, usage: usage | GPUBufferUsage.COPY_DST }); owned.push(b); return b;
  };
  const steps: { name: string; run: (async: boolean) => void | Promise<void> }[] = [];
  const add = (name: string, run: () => void) => steps.push({ name, run });
  add('HDR target', () => { hdr = texture(width, height); });
  add('leaf emission target', () => { emission = texture(width, height); });
  add('depth target', () => { depth = texture(width, height, 'depth24plus'); });
  for (let i = 0; i < 4; i++) add(`bloom targets ${i}`, () => {
    const w = Math.ceil(width / 2 ** (i + 1)), h = Math.ceil(height / 2 ** (i + 1));
    down.push(texture(w, h)); if (i < 3) up.push(texture(w, h));
    else { blurred.push(texture(w, h), texture(w, h)); }
  });
  add('leaf seed', () => {
    const seeds = seedParticles(Math.max(1, count), width, height, half, centre[1], pixelRatio, centre[0]);
    initial = new Float32Array(Math.max(1, count) * 20);
    for (let i = 0; i < seeds.length; i++) {
      const p = seeds[i];
      initial.set(p.position, i * 20); initial.set(p.velocity, i * 20 + 4);
      initial.set(p.shape, i * 20 + 8); initial.set(p.fall, i * 20 + 12); initial.set(p.travel, i * 20 + 16);
    }
  });
  add('buffers', () => {
    uniform = buffer(192, GPUBufferUsage.UNIFORM);
    particles = buffer(initial.byteLength, GPUBufferUsage.STORAGE);
    device.queue.writeBuffer(particles, 0, initial);
    linear = device.createSampler({ magFilter: 'linear', minFilter: 'linear' });
    const all = GPUShaderStage.VERTEX | GPUShaderStage.FRAGMENT | GPUShaderStage.COMPUTE;
    layouts.push(device.createBindGroupLayout({ entries: [{ binding: 0, visibility: all, buffer: { type: 'uniform' } }] }));
    // The face layout also carries its filtering sampler.
    layouts.push(device.createBindGroupLayout({ entries: [
      { binding: 0, visibility: GPUShaderStage.FRAGMENT, texture: {} },
      { binding: 1, visibility: GPUShaderStage.FRAGMENT, texture: {} },
      { binding: 2, visibility: GPUShaderStage.FRAGMENT, texture: {} },
      { binding: 3, visibility: GPUShaderStage.FRAGMENT, sampler: {} },
    ] }));
    layouts.push(device.createBindGroupLayout({ entries: [{ binding: 0, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'storage' } }] }));
    layouts.push(device.createBindGroupLayout({ entries: [{ binding: 0, visibility: GPUShaderStage.VERTEX, buffer: { type: 'read-only-storage' } }] }));
    layouts.push(device.createBindGroupLayout({ entries: [
      { binding: 0, visibility: GPUShaderStage.FRAGMENT, texture: {} },
      { binding: 1, visibility: GPUShaderStage.FRAGMENT, texture: {} },
      { binding: 2, visibility: GPUShaderStage.FRAGMENT, sampler: {} },
    ] }));
  });
  for (let i = 0; i < shaderCode.length; i++) {
    steps.push({ name: `shader module ${i}`, run: asyncMode => {
      let code = shaderCode[i];
      if (i === 13 && format.endsWith('-srgb')) code = code.replace('return vec4f(colour, alpha);',
        'colour = select(colour / 12.92, pow((colour + 0.055) / 1.055, vec3f(2.4)), colour > vec3f(0.04045)); return vec4f(colour, alpha);');
      if (asyncMode && options.compileShader) return options.compileShader(device, code).then(module => { modules[i] = module; });
      modules[i] = device.createShaderModule({ code });
    } });
    steps.push({ name: `pipeline ${i}`, run: asyncMode => {
      const bindGroupLayouts = i === 0 ? [layouts[0], layouts[1]] : i === 1 ? [layouts[0], layouts[2]]
        : (i === 2 || i === 14) ? [layouts[3], layouts[0]] : i === 3 ? [layouts[0]] : [layouts[4]];
      const layout = device.createPipelineLayout({ bindGroupLayouts });
      if (i === 1) {
        const descriptor: GPUComputePipelineDescriptor = { layout, compute: { module: modules[i] } };
        if (asyncMode) return device.createComputePipelineAsync(descriptor).then(p => { pipelines[i] = p; });
        pipelines[i] = device.createComputePipeline(descriptor);
      } else {
        const blend: GPUBlendState = { color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha' },
          alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha' } };
        const descriptor: GPURenderPipelineDescriptor = { layout, vertex: { module: modules[i] },
          fragment: { module: modules[i], targets: [{ format: i === 13 ? format : 'rgba16float',
            ...(i === 2 || i === 3 ? { blend } : i === 14 ? { blend: { color: { srcFactor: 'one', dstFactor: 'one' }, alpha: { srcFactor: 'one', dstFactor: 'one' } } } : {}) }] },
          ...(i < 4 || i === 14 ? { depthStencil: { format: 'depth24plus' as const, depthWriteEnabled: i === 0, depthCompare: 'less' as const } } : {}) };
        if (asyncMode) return device.createRenderPipelineAsync(descriptor).then(p => { pipelines[i] = p; });
        pipelines[i] = device.createRenderPipeline(descriptor);
      }
    } });
  }
  add('bind groups', () => {
    const frame = device.createBindGroup({ layout: layouts[0], entries: [{ binding: 0, resource: { buffer: uniform } }] });
    const compute = device.createBindGroup({ layout: layouts[2], entries: [{ binding: 0, resource: { buffer: particles } }] });
    const leaf = device.createBindGroup({ layout: layouts[3], entries: [{ binding: 0, resource: { buffer: particles } }] });
    groups[0] = [frame]; groups[1] = [frame, compute]; groups[2] = [leaf, frame]; groups[3] = [frame];
    const views = options.fronts.map(t => t.createView()), back = options.back.createView();
    for (const front of views) for (const next of views) faces.push(device.createBindGroup({ layout: layouts[1], entries: [
      { binding: 0, resource: front }, { binding: 1, resource: next }, { binding: 2, resource: back }, { binding: 3, resource: linear },
    ] }));
    const bind = (source: GPUTextureView, lower: GPUTextureView) => device.createBindGroup({ layout: layouts[4], entries: [
      { binding: 0, resource: source }, { binding: 1, resource: lower }, { binding: 2, resource: linear },
    ] });
    for (let i = 0; i < 4; i++) post[4 + i] = bind(i === 0 ? hdr : down[i - 1], emission);
    post[8] = bind(down[3], hdr); post[9] = bind(blurred[0], hdr);
    for (let i = 0; i < 3; i++) post[10 + i] = bind(down[i], i === 2 ? blurred[1] : up[i + 1]);
    post[13] = bind(hdr, up[0]);
  });
  try {
    if (!options.deferred) { for (const step of steps) step.run(false); prepared = true; }
  } catch (error) { destroy(); throw error; }
  const updateFrame = (target: GPUTextureView, frame: MedalFrame): RenderCommands | undefined => {
    if (destroyed || !prepared) return;
    const heat = Math.max(0, Math.min(1, frame.heat));
    if (frame.time < lastTime || frame.struck !== struckAt) {
      device.queue.writeBuffer(particles, 0, initial); lastHeat = 0; struckAt = frame.struck;
    }
    const alive = frame.struck >= 0 && frame.time >= frame.struck;
    data.set([width, height, frame.half * pixelRatio, pixelRatio, frame.centre.x * pixelRatio, frame.centre.y * pixelRatio,
      !alive || frame.time === lastTime ? 0 : Math.max(0, Math.min(frame.dt, 1 / 30)), activeCount]);
    data[10] = frame.spin; data[11] = alive ? frame.time - frame.struck : 0;
    data[12] = heat; data[13] = frame.sweep;
    data[14] = alive && frame.time - frame.struck < 0.3 ? Math.max(0, heat - lastHeat) : 0;
    data[15] = alive ? frame.time - frame.struck : -1;
    for (let column = 0; column < 3; column++) for (let row = 0; row < 3; row++) data[16 + column * 4 + row] = frame.rotation[column * 3 + row];
    data[28] = frame.light.x; data[29] = frame.light.y; data[30] = frame.glint;
    data.set([frame.charge, frame.stamp, frame.morph, frame.engrave], 32);
    data.set(MEDAL_METALS[frame.from], 36); data.set(MEDAL_METALS[frame.to], 40);
    data[44] = options.stageBottom * pixelRatio; data[45] = options.sheetTop * pixelRatio; data[46] = alive ? 1 : 0;
    device.queue.writeBuffer(uniform, 0, data);
    lastTime = frame.time; lastHeat = heat;
    return { device, target, hdr, emission, depth, pipelines, groups, faces, post, down, up, blurred,
      activeCount, alive, face: frame.from * 3 + frame.to,
      strike: !!options.strikeSparks && data[15] >= 0 && data[15] < 1.31 };
  };
  return {
    async prepare(slice: SetupSlice) {
      if (prepared) return;
      try { for (const step of steps) await slice(step.name, () => step.run(true)); prepared = true; }
      catch (error) { destroy(); throw error; }
    },
    setQuality(tier: 'high' | 'medium' | 'low') { activeCount = Math.min(count, tier === 'high' ? 900 : tier === 'medium' ? 600 : 360); },
    resetParticles() { device.queue.writeBuffer(particles, 0, initial); lastTime = -Infinity; lastHeat = 0; struckAt = -Infinity; },
    render(target: GPUTextureView, frame: MedalFrame) {
      const commands = updateFrame(target, frame);
      if (commands) encodeMedalScene(commands);
    },
    async probe(target: GPUTextureView, frame: MedalFrame, dispatch?: (encode: typeof encodeMedalScene, commands: RenderCommands) => Promise<void>) {
      const commands = updateFrame(target, frame);
      if (!commands) return;
      if (dispatch) await dispatch(encodeMedalScene, commands);
      else encodeMedalScene(commands);
    },
    destroy,
  };
}

export type RenderCommands = {
  device: GPUDevice; target: GPUTextureView; hdr: GPUTextureView; emission: GPUTextureView; depth: GPUTextureView;
  pipelines: (GPURenderPipeline | GPUComputePipeline)[]; groups: GPUBindGroup[][];
  faces: GPUBindGroup[]; post: GPUBindGroup[];
  down: GPUTextureView[]; up: GPUTextureView[]; blurred: GPUTextureView[];
  activeCount: number; alive: boolean; face: number; strike: boolean;
};

function renderPost(encoder: GPUCommandEncoder, pipeline: GPURenderPipeline, group: GPUBindGroup, target: GPUTextureView) {
  'worklet';
  const pass = encoder.beginRenderPass({ colorAttachments: [{ view: target, loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0] }] });
  pass.setPipeline(pipeline); pass.setBindGroup(0, group); pass.draw(3); pass.end();
}

/** Only GPU host objects and primitives cross into the setup worker. No scene closure. */
export function encodeMedalScene(commands: RenderCommands) {
  'worklet';
  const { device, target, hdr, emission, depth, pipelines, groups, faces, post, down, up, blurred,
    activeCount, alive, face, strike } = commands;
  const encoder = device.createCommandEncoder();
  if (activeCount > 0 && alive) {
    const pass = encoder.beginComputePass(); pass.setPipeline(pipelines[1] as GPUComputePipeline);
    pass.setBindGroup(0, groups[1][0]); pass.setBindGroup(1, groups[1][1]);
    pass.dispatchWorkgroups(Math.ceil(activeCount / 64)); pass.end();
  }
  const pass = encoder.beginRenderPass({
    colorAttachments: [{ view: hdr, clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }],
    depthStencilAttachment: { view: depth, depthClearValue: 1, depthLoadOp: 'clear', depthStoreOp: 'store' },
  });
  pass.setPipeline(pipelines[0] as GPURenderPipeline); pass.setBindGroup(0, groups[0][0]);
  pass.setBindGroup(1, faces[face]); pass.draw(3);
  if (activeCount > 0 && alive) {
    pass.setPipeline(pipelines[2] as GPURenderPipeline); pass.setBindGroup(0, groups[2][0]); pass.setBindGroup(1, groups[2][1]); pass.draw(18, activeCount);
  }
  if (strike) {
    pass.setPipeline(pipelines[3] as GPURenderPipeline); pass.setBindGroup(0, groups[3][0]); pass.draw(6, 73);
  }
  pass.end();
  const glow = encoder.beginRenderPass({
    colorAttachments: [{ view: emission, clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }],
    depthStencilAttachment: { view: depth, depthLoadOp: 'load', depthStoreOp: 'store' },
  });
  if (activeCount > 0 && alive) {
    glow.setPipeline(pipelines[14] as GPURenderPipeline);
    glow.setBindGroup(0, groups[2][0]); glow.setBindGroup(1, groups[2][1]); glow.draw(18, activeCount);
  }
  glow.end();
  for (let i = 0; i < 4; i++) renderPost(encoder, pipelines[4 + i] as GPURenderPipeline, post[4 + i], down[i]);
  renderPost(encoder, pipelines[8] as GPURenderPipeline, post[8], blurred[0]); renderPost(encoder, pipelines[9] as GPURenderPipeline, post[9], blurred[1]);
  for (let i = 2; i >= 0; i--) renderPost(encoder, pipelines[10 + i] as GPURenderPipeline, post[10 + i], up[i]);
  renderPost(encoder, pipelines[13] as GPURenderPipeline, post[13], target); device.queue.submit([encoder.finish()]);
}
