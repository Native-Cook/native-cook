import type { RNCanvasContext } from 'react-native-webgpu';

import { DIAMOND_WGSL } from './shader';
import type { DiamondGpuFrame } from './types';

export type DiamondResources = {
  device: GPUDevice;
  pipeline: GPURenderPipeline;
  bindGroup: GPUBindGroup;
  uniform: GPUBuffer;
  texture: GPUTexture;
  fitX: number;
  fitY: number;
  logicalSize: number;
  pixels: number;
};
export type DiamondCommands = Omit<DiamondResources, 'texture'> & { context: RNCanvasContext };

type DrawResources = Omit<DiamondResources, 'texture'>;

export function encodeDiamond(resources: DrawResources, target: GPUTextureView, frame: DiamondGpuFrame, material: boolean) {
  'worklet';
  const progress = material && !frame.reduced ? Math.min(1, Math.max(0, frame.progress)) : 0;
  const values = new Float32Array([
    resources.pixels, resources.pixels, progress, frame.phase,
    frame.lightX, frame.lightY, resources.fitX, resources.fitY,
    resources.logicalSize, 0, 0, 0,
  ]);
  resources.device.queue.writeBuffer(resources.uniform, 0, values);
  const encoder = resources.device.createCommandEncoder({ label: 'Prismatic diamond frame' });
  const pass = encoder.beginRenderPass({ colorAttachments: [{ view: target, loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0] }] });
  pass.setPipeline(resources.pipeline);
  pass.setBindGroup(0, resources.bindGroup);
  pass.draw(3);
  pass.end();
  resources.device.queue.submit([encoder.finish()]);
}

export function presentDiamond(commands: DiamondCommands, frame: DiamondGpuFrame, material: boolean) {
  'worklet';
  encodeDiamond(commands, commands.context.getCurrentTexture().createView(), frame, material);
  commands.context.present();
}

/** Setup/probe runs on the JS-created device before the UI runtime receives the surface. */
export async function createDiamondResources(device: GPUDevice, bitmap: ImageBitmap, format: GPUTextureFormat, size: number, pixels: number): Promise<DiamondResources> {
  const texture = device.createTexture({ label: 'Prismatic diamond source', size: [bitmap.width, bitmap.height], format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT });
  let uniform: GPUBuffer | undefined;
  try {
    device.queue.copyExternalImageToTexture({ source: bitmap }, { texture, premultipliedAlpha: false }, [bitmap.width, bitmap.height]);
    const module = device.createShaderModule({ label: 'Prismatic diamond optical material', code: DIAMOND_WGSL });
    const compilation = await module.getCompilationInfo();
    const errors = compilation.messages.filter(message => message.type === 'error');
    if (errors.length) throw new Error(errors.map(message => message.message).join('; '));
    const pipeline = await device.createRenderPipelineAsync({ label: 'Prismatic diamond material', layout: 'auto', vertex: { module, entryPoint: 'vertexMain' }, fragment: { module, entryPoint: 'fragmentMain', targets: [{ format }] }, primitive: { topology: 'triangle-list' } });
    uniform = device.createBuffer({ label: 'Prismatic diamond uniforms', size: 48, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
    const sampler = device.createSampler({ magFilter: 'linear', minFilter: 'linear', addressModeU: 'clamp-to-edge', addressModeV: 'clamp-to-edge' });
    const bindGroup = device.createBindGroup({ layout: pipeline.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: uniform } }, { binding: 1, resource: texture.createView() }, { binding: 2, resource: sampler }] });
    const aspect = bitmap.width / bitmap.height;
    const resources = { device, pipeline, bindGroup, uniform, texture, fitX: aspect < 1 ? 1 / aspect : 1, fitY: aspect > 1 ? aspect : 1, logicalSize: size, pixels };
    const probe = device.createTexture({ label: 'Prismatic one-pixel capability probe', size: [1, 1], format, usage: GPUTextureUsage.RENDER_ATTACHMENT });
    device.pushErrorScope('validation');
    let error: GPUError | null = null;
    try {
      encodeDiamond(resources, probe.createView(), { phase: 0, progress: 0, lightX: 0, lightY: 0, visible: false, active: false, reduced: false }, false);
      await device.queue.onSubmittedWorkDone();
    } finally { error = await device.popErrorScope(); probe.destroy(); }
    if (error) throw new Error(error.message);
    return resources;
  } catch (error) {
    texture.destroy(); uniform?.destroy(); throw error;
  }
}
