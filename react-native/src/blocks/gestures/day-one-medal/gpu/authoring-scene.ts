import type { SetupSlice } from './setup';
import { d, tgpu } from 'typegpu';
import { MEDAL_METALS } from '../medal-orientation';
import { medalFragment } from './medal-shading';
import { leafBloomFragment, leafFragment, leafVertex, seedParticles, simulate } from './particles';
import { strikeFragment, strikeVertex } from './strike';
import { blur, composite, downsample, upsample } from './post';
import { faceLayout, frameLayout, fullscreen, leafLayout, Particle, postLayout, simulationLayout, Uniforms } from './schemas';

export type MedalFrame = {
  centre: { x: number; y: number }; // logical points, matching MedalCanvas
  half: number;
  rotation: number[];
  light: { x: number; y: number };
  glint: number;
  charge: number;
  stamp: number;
  morph: number;
  engrave: number;
  from: number;
  to: number;
  spin: number;
  time: number;
  struck: number;
  dt: number;
  heat: number;
  sweep: number;
};

export type MedalSceneOptions = {
  width: number; // device pixels
  height: number; // device pixels
  pixelRatio: number;
  centre: [number, number]; // device pixels
  half: number; // half the medal's height, device pixels
  front: GPUTexture; // linear data strips, r8unorm or rgba8unorm
  back: GPUTexture;
  fronts: GPUTexture[];
  stageBottom: number; // logical points
  sheetTop: number;
  strikeSparks?: boolean;
  flakes?: number;
  compileShader?: (device: GPUDevice, code: string) => Promise<GPUShaderModule>;
  deferred?: boolean;
  quality?: 'high' | 'medium' | 'low';
};

/** Owns its buffers and intermediate targets, but never the caller's device or face textures. */
export function createMedalScene(device: GPUDevice, format: GPUTextureFormat, options: MedalSceneOptions) {
  const { width, height, pixelRatio, centre, half, back, quality = 'high' } = options;
  if (![width, height, pixelRatio, half, ...centre].every(Number.isFinite)
    || width <= 0 || height <= 0 || pixelRatio <= 0 || half <= 0
    || !Number.isInteger(width) || !Number.isInteger(height)) {
    throw new Error('Invalid medal canvas dimensions.');
  }
  const count = options.flakes ?? (quality === 'high' ? 900 : quality === 'medium' ? 600 : 360);
  if (!Number.isInteger(count) || count < 0 || count > 100_000) throw new Error('Invalid medal flake count.');
  const levels = quality === 'low' ? 0 : quality === 'medium' ? 2 : 4;
  const root = tgpu.initFromDevice({ device });
  const ownedTextures: GPUTexture[] = [];
  const ownedBuffers: { destroy(): void }[] = [];
  let destroyed = false;
  const destroy = () => {
    if (destroyed) return;
    destroyed = true;
    for (const buffer of ownedBuffers) buffer.destroy();
    for (const texture of ownedTextures) texture.destroy();
    root.destroy();
  };
  try {
    const texture = (label: string, w: number, h: number, textureFormat: GPUTextureFormat = 'rgba16float') => {
      const result = device.createTexture({ label, size: [w, h], format: textureFormat,
        usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING });
      ownedTextures.push(result);
      return { texture: result, view: result.createView() };
    };
    const hdr = texture('Medal HDR', width, height);
    const emission = texture('Soft leaf emission', width, height);
    const depth = texture('Medal occlusion', width, height, 'depth24plus');
    const pyramid = Array.from({ length: levels }, (_, i) => texture(`Bloom down ${i}`,
      Math.max(1, Math.ceil(width / 2 ** (i + 1))), Math.max(1, Math.ceil(height / 2 ** (i + 1)))));
    const expanded = pyramid.slice(0, -1).map((level, i) => texture(`Bloom up ${i}`, level.texture.width, level.texture.height));
    const smallest = pyramid.at(-1);
    const blurred = smallest ? [0, 1].map(i => texture(`Bloom blur ${i}`, smallest.texture.width, smallest.texture.height)) : [];
    const linear = device.createSampler({ magFilter: 'linear', minFilter: 'linear', addressModeU: 'clamp-to-edge', addressModeV: 'clamp-to-edge' });
    const uniform = root.createBuffer(Uniforms).$usage('uniform');
    ownedBuffers.push(uniform);
    const initial = seedParticles(Math.max(1, count), width, height, half, centre[1], pixelRatio, centre[0]);
    const particles = root.createBuffer(d.arrayOf(Particle, Math.max(1, count)), initial).$usage('storage');
    ownedBuffers.push(particles);
    const frameGroup = root.createBindGroup(frameLayout, { frame: uniform });
    const views = options.fronts.map(item => item.createView());
    const backView = back.createView();
    const faces = views.flatMap(view => views.map(next => root.createBindGroup(faceLayout,
      { front: view, frontNext: next, back: backView, linear })));
    const computeGroup = root.createBindGroup(simulationLayout, { particles });
    const leaves = root.createBindGroup(leafLayout, { particles });
    const medal = root.createRenderPipeline({ vertex: fullscreen, fragment: medalFragment,
      targets: { colour: { format: 'rgba16float' } },
      depthStencil: { format: 'depth24plus', depthWriteEnabled: true, depthCompare: 'less' },
    }).with(frameGroup);
    const compute = root.createComputePipeline({ compute: simulate }).with(frameGroup).with(computeGroup);
    const gold = root.createRenderPipeline({ vertex: leafVertex, fragment: leafFragment,
      targets: { format: 'rgba16float', blend: {
        color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
      } },
      depthStencil: { format: 'depth24plus', depthWriteEnabled: false, depthCompare: 'less' },
    }).with(frameGroup).with(leaves);
    const leafGlow = root.createRenderPipeline({ vertex: leafVertex, fragment: leafBloomFragment,
      targets: { format: 'rgba16float', blend: {
        color: { srcFactor: 'one', dstFactor: 'one', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one', operation: 'add' },
      } },
      depthStencil: { format: 'depth24plus', depthWriteEnabled: false, depthCompare: 'less' },
    }).with(frameGroup).with(leaves);
    const debris = root.createRenderPipeline({ vertex: strikeVertex, fragment: strikeFragment,
      targets: { format: 'rgba16float', blend: {
        color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
      } },
      depthStencil: { format: 'depth24plus', depthWriteEnabled: false, depthCompare: 'less' },
    }).with(frameGroup);
    const postGroup = (source: GPUTextureView, lower: GPUTextureView) => root.createBindGroup(postLayout, { source, lower, linear });
    const downs = pyramid.map((_, i) => root.createRenderPipeline({ vertex: fullscreen,
      fragment: downsample(i === 0), targets: { format: 'rgba16float' },
    }).with(postGroup(i === 0 ? hdr.view : pyramid[i - 1]!.view, emission.view)));
    const blurs = blurred.map((_, i) => root.createRenderPipeline({ vertex: fullscreen,
      fragment: blur(i === 0, quality === 'high' ? 32 : 8), targets: { format: 'rgba16float' },
    }).with(postGroup(i === 0 ? smallest!.view : blurred[0]!.view, hdr.view)));
    const ups = expanded.map((_, i) => root.createRenderPipeline({ vertex: fullscreen,
      fragment: upsample, targets: { format: 'rgba16float' },
    }).with(postGroup(pyramid[i]!.view, i === expanded.length - 1 ? blurred[1]!.view : expanded[i + 1]!.view)));
    const output = root.createRenderPipeline({ vertex: fullscreen,
      fragment: composite(levels > 0, format.endsWith('-srgb')), targets: { format },
    }).with(postGroup(hdr.view, expanded[0]?.view ?? pyramid[0]?.view ?? hdr.view));
    // Resolve/validate every typed shader at setup, not on an arbitrary animation frame.
    const pipelines = [medal, compute, gold, debris, ...downs, ...blurs, ...ups, output, leafGlow];
    if (!options.deferred) for (const pipeline of pipelines) pipeline.initSync();
    let activeCount = count;

    const data = new Float32Array(48);
    data.set([width, height, half, pixelRatio, centre[0], centre[1], 0, count]);
    let lastTime = -Infinity;
    let lastHeat = 0;
    let struckAt = -Infinity;
    return {
      async prepare(slice: SetupSlice) {
        for (let i = 0; i < pipelines.length; i++) {
          await slice(`pipeline ${i}`, () => pipelines[i].initAsync());
        }
      },
      setQuality(tier: 'high' | 'medium' | 'low') {
        activeCount = Math.min(count, tier === 'high' ? 900 : tier === 'medium' ? 600 : 360);
      },
      resetParticles() { particles.write(initial); lastTime = -Infinity; lastHeat = 0; struckAt = -Infinity; },
      render(target: GPUTextureView, frame: MedalFrame) {
        if (destroyed) return;
        const heat = Math.max(0, Math.min(1, frame.heat));
        if (frame.time < lastTime || frame.struck !== struckAt) {
          particles.write(initial);
          lastHeat = 0;
          struckAt = frame.struck;
        }
        const alive = frame.struck >= 0 && frame.time >= frame.struck;
        data[7] = activeCount;
        data[2] = frame.half * pixelRatio;
        data[4] = frame.centre.x * pixelRatio; data[5] = frame.centre.y * pixelRatio;
        data[6] = !alive || frame.time === lastTime ? 0 : Math.max(0, Math.min(frame.dt, 1 / 30));
        data[10] = frame.spin; data[11] = alive ? frame.time - frame.struck : 0;
        data[12] = heat; data[13] = frame.sweep;
        data[14] = alive && frame.time - frame.struck < 0.3 ? Math.max(0, heat - lastHeat) : 0;
        data[15] = alive ? frame.time - frame.struck : -1;
        for (let column = 0; column < 3; column++) {
          for (let row = 0; row < 3; row++) data[16 + column * 4 + row] = frame.rotation[column * 3 + row];
        }
        data[28] = frame.light.x; data[29] = frame.light.y; data[30] = frame.glint;
        data.set([frame.charge, frame.stamp, frame.morph, frame.engrave], 32);
        data.set(MEDAL_METALS[frame.from], 36); data.set(MEDAL_METALS[frame.to], 40);
        data[44] = options.stageBottom * pixelRatio; data[45] = options.sheetTop * pixelRatio;
        data[46] = alive ? 1 : 0;
        uniform.write(data.buffer);
        lastTime = frame.time;
        lastHeat = heat;
        const encoder = root['~unstable'].createCommandEncoder();
        if (count > 0 && alive) {
          const pass = encoder.beginComputePass();
          compute.with(pass).dispatchWorkgroups(Math.ceil(activeCount / 64));
          pass.end();
        }
        const scene = encoder.beginRenderPass({
          colorAttachments: [{ view: hdr.view, clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }],
          depthStencilAttachment: { view: depth.view, depthClearValue: 1, depthLoadOp: 'clear', depthStoreOp: 'store' },
        });
        medal.with(faces[frame.from * 3 + frame.to]!).with(scene).draw(3);
        if (count > 0 && alive) gold.with(scene).draw(18, activeCount);
        if (options.strikeSparks && data[15]! >= 0 && data[15]! < 1.31) debris.with(scene).draw(6, 73);
        scene.end();
        const glow = encoder.beginRenderPass({
          colorAttachments: [{ view: emission.view, clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }],
          depthStencilAttachment: { view: depth.view, depthLoadOp: 'load', depthStoreOp: 'store' },
        });
        if (count > 0 && alive) leafGlow.with(glow).draw(18, activeCount);
        glow.end();
        for (let i = 0; i < downs.length; i++) {
          const pass = encoder.beginRenderPass({ colorAttachments: [{ view: pyramid[i]!.view }] });
          downs[i]!.with(pass).draw(3); pass.end();
        }
        for (let i = 0; i < blurs.length; i++) {
          const pass = encoder.beginRenderPass({ colorAttachments: [{ view: blurred[i]!.view }] });
          blurs[i]!.with(pass).draw(3); pass.end();
        }
        for (let i = ups.length - 1; i >= 0; i--) {
          const pass = encoder.beginRenderPass({ colorAttachments: [{ view: expanded[i]!.view }] });
          ups[i]!.with(pass).draw(3); pass.end();
        }
        const final = encoder.beginRenderPass({ colorAttachments: [{ view: target, clearValue: [0, 0, 0, 0] }] });
        output.with(final).draw(3); final.end();
        encoder.submit();
      },
      destroy,
    };
  } catch (error) {
    destroy();
    throw error;
  }
}
