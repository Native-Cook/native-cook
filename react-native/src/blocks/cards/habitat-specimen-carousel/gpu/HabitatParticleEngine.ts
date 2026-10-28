import type { RNCanvasContext } from 'react-native-webgpu';

import { HABITAT_PARTICLE_WGSL, PARTICLE_COUNT, PARTICLE_FRAME, PARTICLE_STRIDE } from './HabitatParticleShader';

export type ParticleResources = {
  device: GPUDevice;
  compute: GPUComputePipeline;
  render: GPURenderPipeline;
  computeGroup: GPUBindGroup;
  /** Behind the creatures, then in front of them. */
  layerGroups: readonly [GPUBindGroup, GPUBindGroup];
  frame: GPUBuffer;
  buffers: readonly GPUBuffer[];
  /** The frame uniform, written in place every frame (PARTICLE_FRAME floats). */
  values: Float32Array;
  pixelWidth: number;
  pixelHeight: number;
};
export type ParticleEngine = ParticleResources & {
  back: RNCanvasContext;
  front: RNCanvasContext;
  generation: number;
};

const BLEND: GPUBlendState = {
  color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha' },
  alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha' },
};

export async function createParticleResources(device: GPUDevice, format: GPUTextureFormat, width: number, height: number, scale: number): Promise<ParticleResources> {
  const module = device.createShaderModule({ label: 'Habitat particles', code: HABITAT_PARTICLE_WGSL });
  const compilation = await module.getCompilationInfo();
  const errors = compilation.messages.filter(item => item.type === 'error');
  if (errors.length) throw new Error(errors.map(item => `${item.lineNum}:${item.linePos} ${item.message}`).join('; '));
  const compute = await device.createComputePipelineAsync({ label: 'Habitat particle step', layout: 'auto', compute: { module, entryPoint: 'simulate' } });
  const render = await device.createRenderPipelineAsync({
    label: 'Habitat particle draw', layout: 'auto',
    vertex: { module, entryPoint: 'moteVertex' },
    fragment: { module, entryPoint: 'moteFragment', targets: [{ format, blend: BLEND }] },
    primitive: { topology: 'triangle-list' },
  });
  const created: GPUBuffer[] = [];
  const buffer = (descriptor: GPUBufferDescriptor) => { const made = device.createBuffer(descriptor); created.push(made); return made; };
  try {
    // Every particle starts dead (span 0), so the first step spawns the ambient ones where they belong.
    const particles = buffer({ label: 'Habitat particles', size: PARTICLE_COUNT * PARTICLE_STRIDE * 4, usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST });
    device.queue.writeBuffer(particles, 0, new Float32Array(PARTICLE_COUNT * PARTICLE_STRIDE));
    const frame = buffer({ label: 'Habitat particle frame', size: PARTICLE_FRAME * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
    const layers = [0, 1].map(value => {
      const layer = buffer({ label: `Habitat particle layer ${value}`, size: 16, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      device.queue.writeBuffer(layer, 0, new Float32Array([value, 0, 0, 0]));
      return layer;
    });
    const computeGroup = device.createBindGroup({ layout: compute.getBindGroupLayout(0), entries: [
      { binding: 0, resource: { buffer: frame } }, { binding: 1, resource: { buffer: particles } },
    ] });
    const layerGroup = (layer: GPUBuffer) => device.createBindGroup({ layout: render.getBindGroupLayout(0), entries: [
      { binding: 0, resource: { buffer: frame } }, { binding: 2, resource: { buffer: layer } }, { binding: 3, resource: { buffer: particles } },
    ] });
    return {
      device, compute, render, computeGroup, layerGroups: [layerGroup(layers[0]), layerGroup(layers[1])], frame, buffers: created,
      values: new Float32Array(PARTICLE_FRAME),
      pixelWidth: Math.max(1, Math.round(width * scale)),
      pixelHeight: Math.max(1, Math.round(height * scale)),
    };
  } catch (error) {
    for (const made of created) made.destroy();
    throw error;
  }
}

/** One frame: step every particle, then draw the far ones behind the creatures and the near ones in front. */
export function particleFrame(engine: ParticleEngine) {
  'worklet';
  const { device } = engine;
  device.queue.writeBuffer(engine.frame, 0, engine.values);
  const encoder = device.createCommandEncoder({ label: 'Habitat particle frame' });
  const step = encoder.beginComputePass();
  step.setPipeline(engine.compute);
  step.setBindGroup(0, engine.computeGroup);
  step.dispatchWorkgroups(Math.ceil(PARTICLE_COUNT / 64));
  step.end();
  const targets = [engine.back, engine.front];
  for (let layer = 0; layer < 2; layer++) {
    const pass = encoder.beginRenderPass({ colorAttachments: [{
      view: targets[layer].getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store',
    }] });
    pass.setPipeline(engine.render);
    pass.setBindGroup(0, engine.layerGroups[layer]);
    pass.draw(6, PARTICLE_COUNT);
    pass.end();
  }
  device.queue.submit([encoder.finish()]);
  engine.back.present();
  engine.front.present();
}
