// Raffle Drum Spin — the 3D layer: the chrome drum, the baseballs and the foil confetti, drawn with
// WebGPU (data layouts and shader resolution by TypeGPU, see rds-wgsl.ts).
//
// The canvas is transparent (premultiplied alpha), full screen and untouchable, over the Skia page.
// It renders every frame from the same frameAt() the page uses, so the 2D card icons hand their
// balls to this layer without a seam. When the native module is missing (Expo Go) or anything
// fails it reports it, and the page draws a Skia drum instead.

import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Image, PixelRatio, StyleSheet, TurboModuleRegistry, View } from 'react-native';
import type { SharedValue } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { d, tgpu } from 'typegpu';

import { CAM, type Driver, frameAt, storyOf } from '../rds-motion';
import { L } from '../rds-theme';
import { buildDrum } from './rds-geometry';
import { BALLS, BallU, CONFETTI, FrameU, resolveShaders } from './rds-wgsl';

const GHOSTS = 5;

type WebGPU = typeof import('react-native-webgpu');

type Props = {
  /** Drawn between the drum layer and the foil layer (the rack-focus blur). */
  under?: ReactNode;
  /** Drawn above the foil (the drawn ball and the golden ticket). */
  children?: ReactNode;
  time: SharedValue<number>;
  driver: SharedValue<Driver>;
  width: number;
  height: number;
  onAvailable: (ok: boolean) => void;
  /** Reduced motion: no GPU layer, only the children. */
  disabled?: boolean;
};

export function RdsDrumGpu(props: Props) {
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
  if (!webgpu || props.disabled) return <>{props.under}{props.children}</>;
  return <DrumCanvas {...props} webgpu={webgpu} />;
}

function DrumCanvas({ webgpu, time, driver, width, height, onAvailable, under, children }: Props & { webgpu: WebGPU }) {
  const { Canvas, useCanvasRef } = webgpu;
  const ref = useCanvasRef();
  const foilRef = useCanvasRef();
  const report = useRef(onAvailable);
  useEffect(() => {
    report.current = onAvailable;
  });

  useEffect(() => {
    let live = true;
    let frame = 0;
    let cleanup = () => {};
    const start = async () => {
      const nav = globalThis.navigator as Navigator & { gpu?: GPU };
      const adapter = await nav.gpu?.requestAdapter();
      if (!adapter) throw new Error('No WebGPU adapter.');
      const device = await adapter.requestDevice();
      if (!live) {
        device.destroy();
        return;
      }
      const context = ref.current?.getContext('webgpu') as RNCanvasContext | null;
      const foilContext = foilRef.current?.getContext('webgpu') as RNCanvasContext | null;
      if (!context || !foilContext) throw new Error('No WebGPU context.');
      const pr = PixelRatio.get();
      const W = Math.round(width * pr);
      const H = Math.round(height * pr);
      const format = nav.gpu!.getPreferredCanvasFormat();
      for (const ctx of [context, foilContext]) {
        ctx.canvas.width = W;
        ctx.canvas.height = H;
        ctx.configure({ device, format, alphaMode: 'premultiplied' });
      }

      const root = tgpu.initFromDevice({ device });
      const frameBuf = root.createBuffer(FrameU).$usage('uniform');
      const ballBuf = root.createBuffer(d.arrayOf(BallU, BALLS)).$usage('uniform');
      const module = device.createShaderModule({ code: resolveShaders() });

      const mesh = buildDrum();
      const vbuf = device.createBuffer({ size: mesh.byteLength, usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST });
      device.queue.writeBuffer(vbuf, 0, mesh);
      const vertexCount = mesh.length / 8;

      const samples = 4;
      const color = device.createTexture({ size: [W, H], sampleCount: samples, format, usage: GPUTextureUsage.RENDER_ATTACHMENT });
      const depth = device.createTexture({ size: [W, H], sampleCount: samples, format: 'depth24plus', usage: GPUTextureUsage.RENDER_ATTACHMENT });
      const blend: GPUBlendState = {
        color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
      };
      const multisample = { count: samples };
      const drumPipe = device.createRenderPipeline({
        layout: 'auto',
        vertex: {
          module,
          entryPoint: 'drumVs',
          buffers: [{
            arrayStride: 32,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x3' },
              { shaderLocation: 1, offset: 12, format: 'float32x3' },
              { shaderLocation: 2, offset: 24, format: 'float32' },
              { shaderLocation: 3, offset: 28, format: 'float32' },
            ],
          }],
        },
        fragment: { module, entryPoint: 'drumFs', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list', cullMode: 'none' },
        depthStencil: { format: 'depth24plus', depthWriteEnabled: true, depthCompare: 'less' },
        multisample,
      });
      const ghostPipe = device.createRenderPipeline({
        layout: 'auto',
        vertex: {
          module,
          entryPoint: 'drumVs',
          buffers: [{
            arrayStride: 32,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x3' },
              { shaderLocation: 1, offset: 12, format: 'float32x3' },
              { shaderLocation: 2, offset: 24, format: 'float32' },
              { shaderLocation: 3, offset: 28, format: 'float32' },
            ],
          }],
        },
        fragment: { module, entryPoint: 'drumFs', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list', cullMode: 'none' },
        depthStencil: { format: 'depth24plus', depthWriteEnabled: false, depthCompare: 'less' },
        multisample,
      });
      const ballPipe = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module, entryPoint: 'ballVs' },
        fragment: { module, entryPoint: 'ballFs', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list' },
        depthStencil: { format: 'depth24plus', depthWriteEnabled: true, depthCompare: 'less' },
        multisample,
      });
      const foilPipe = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module, entryPoint: 'foilVs' },
        fragment: { module, entryPoint: 'foilFs', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list', cullMode: 'none' },
        depthStencil: { format: 'depth24plus', depthWriteEnabled: false, depthCompare: 'always' },
        multisample,
      });
      const frameRes = { buffer: root.unwrap(frameBuf) };
      const ballRes = { buffer: root.unwrap(ballBuf) };
      // The environment map: a 1×1 stand-in until the panorama has loaded.
      const sampler = device.createSampler({ magFilter: 'linear', minFilter: 'linear', addressModeU: 'repeat', addressModeV: 'clamp-to-edge' });
      let envTex = device.createTexture({ size: [1, 1], format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST });
      let envLoaded = false;
      const envEntries = () => [{ binding: 0, resource: frameRes }, { binding: 2, resource: envTex.createView() }, { binding: 3, resource: sampler }];
      let drumGroup = device.createBindGroup({ layout: drumPipe.getBindGroupLayout(0), entries: envEntries() });
      const ballGroup = device.createBindGroup({ layout: ballPipe.getBindGroupLayout(0), entries: [{ binding: 0, resource: frameRes }, { binding: 1, resource: ballRes }] });
      // The confetti atlas: a 1×1 stand-in until it has loaded (then its five mip levels, which the
      // file packs side by side: 1024 at the left, 512 / 256 / 128 / 64 beside it).
      const confSampler = device.createSampler({ magFilter: 'linear', minFilter: 'linear', mipmapFilter: 'linear', addressModeU: 'clamp-to-edge', addressModeV: 'clamp-to-edge' });
      let confTex = device.createTexture({ size: [1, 1], format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST });
      let confLoaded = false;
      const foilEntries = () => [{ binding: 0, resource: frameRes }, { binding: 4, resource: confTex.createView() }, { binding: 5, resource: confSampler }];
      let foilGroup = device.createBindGroup({ layout: foilPipe.getBindGroupLayout(0), entries: foilEntries() });
      let ghostGroup = device.createBindGroup({ layout: ghostPipe.getBindGroupLayout(0), entries: envEntries() });
      void (async () => {
        try {
          const src = Image.resolveAssetSource(require('../assets/env-studio.jpg'));
          const buf = await (await fetch(src.uri)).arrayBuffer();
          // react-native-webgpu decodes encoded bytes directly.
          const bitmap = await createImageBitmap(buf as unknown as ImageBitmapSource);
          if (!live) return;
          const tex = device.createTexture({ size: [bitmap.width, bitmap.height], format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT });
          device.queue.copyExternalImageToTexture({ source: bitmap }, { texture: tex }, [bitmap.width, bitmap.height]);
          envTex.destroy();
          envTex = tex;
          drumGroup = device.createBindGroup({ layout: drumPipe.getBindGroupLayout(0), entries: envEntries() });
          ghostGroup = device.createBindGroup({ layout: ghostPipe.getBindGroupLayout(0), entries: envEntries() });
          envLoaded = true;
        } catch {
          // Keep the procedural studio.
        }
      })();
      void (async () => {
        try {
          const src = Image.resolveAssetSource(require('../assets/confetti-atlas.webp'));
          const buf = await (await fetch(src.uri)).arrayBuffer();
          const bitmap = await createImageBitmap(buf as unknown as ImageBitmapSource, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' });
          if (!live) return;
          const levels: [number, number, number][] = [[0, 0, 1024], [1024, 0, 512], [1024, 512, 256], [1280, 512, 128], [1408, 512, 64]];
          const tex = device.createTexture({ size: [1024, 1024], mipLevelCount: levels.length, format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT });
          levels.forEach(([x, y, size], mipLevel) => {
            device.queue.copyExternalImageToTexture({ source: bitmap, origin: [x, y] }, { texture: tex, mipLevel, premultipliedAlpha: false }, [size, size]);
          });
          confTex.destroy();
          confTex = tex;
          foilGroup = device.createBindGroup({ layout: foilPipe.getBindGroupLayout(0), entries: foilEntries() });
          confLoaded = true;
        } catch {
          // Keep the plain slips.
        }
      })();
      const foilColor = device.createTexture({ size: [W, H], sampleCount: samples, format, usage: GPUTextureUsage.RENDER_ATTACHMENT });
      let foilShown = false;

      const sx = width / 402;
      const sy = height / 874;
      const v4 = (a: number, b: number, c: number, e: number) => d.vec4f(a, b, c, e);
      const render = () => {
        const drv = driver.get();
        const f = frameAt(time.get(), sx, sy, storyOf(drv), drv.spin, Math.min(1, Math.abs(drv.spinV) / 14), drv.idle);
        const winner = f.balls[0];
        frameBuf.write({
          viewport: v4(W, H, pr, f.t),
          drum: v4(L.drumX * sx * pr, L.drumY * sy * pr, L.drumR * CAM.dist * sx * pr, f.theta),
          cam: v4(Math.cos(CAM.yaw), Math.sin(CAM.yaw), Math.cos(CAM.pitch), Math.sin(CAM.pitch)),
          misc: v4(CAM.dist, f.speed, f.glow, f.burst),
          burst: v4((L.revealX) * sx * pr, (L.revealY) * sy * pr, 56 * sx * pr, CONFETTI),
          // Ghosts spread over one 1/60 s frame of the cage's turn, only when it turns fast.
          ghost: v4(Math.abs(f.omega) / 60 / GHOSTS, Math.abs(f.omega) > 4 ? GHOSTS : 0, envLoaded ? 1 : 0, confLoaded ? 1 : 0),
        });
        void winner;
        const list = [];
        for (let k = 0; k < BALLS; k++) {
          const b = f.balls[k];
          list.push(b
            ? { pos: v4(b.x * pr, b.y * pr, b.r * pr, b.z), rot: v4(b.rx, b.ry, b.rz, b.a), fx: v4(b.glow, 0, 0, 0) }
            : { pos: v4(0, 0, 1, 5), rot: v4(0, 0, 0, 0), fx: v4(0, 0, 0, 0) });
        }
        ballBuf.write(list);
        const encoder = device.createCommandEncoder();
        const pass = encoder.beginRenderPass({
          colorAttachments: [{ view: color.createView(), resolveTarget: context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'discard' }],
          depthStencilAttachment: { view: depth.createView(), depthClearValue: 1, depthLoadOp: 'clear', depthStoreOp: 'discard' },
        });
        pass.setPipeline(drumPipe);
        pass.setBindGroup(0, drumGroup);
        pass.setVertexBuffer(0, vbuf);
        pass.draw(vertexCount, 1, 0, 0);
        pass.setPipeline(ballPipe);
        pass.setBindGroup(0, ballGroup);
        pass.draw(6, BALLS);
        if (Math.abs(f.omega) > 4) {
          pass.setPipeline(ghostPipe);
          pass.setBindGroup(0, ghostGroup);
          pass.setVertexBuffer(0, vbuf);
          pass.draw(vertexCount, GHOSTS, 0, 1);
        }
        pass.end();
        // The foil is its own canvas, above the rack-focus blur, so it stays sharp.
        const foilOn = f.burst >= 0;
        if (foilOn || foilShown) {
          const fpass = encoder.beginRenderPass({
            colorAttachments: [{ view: foilColor.createView(), resolveTarget: foilContext.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'discard' }],
            depthStencilAttachment: { view: depth.createView(), depthClearValue: 1, depthLoadOp: 'clear', depthStoreOp: 'discard' },
          });
          if (foilOn) {
            fpass.setPipeline(foilPipe);
            fpass.setBindGroup(0, foilGroup);
            fpass.draw(6, CONFETTI);
          }
          fpass.end();
        }
        device.queue.submit([encoder.finish()]);
        context.present();
        if (foilOn || foilShown) foilContext.present();
        foilShown = foilOn;
      };
      // Development builds: JS-thread frame report for the drum (read through __raffleDrum.stats()).
      const stat = { frames: 0, sum: 0, worst: 0, hitches: 0, render: 0, last: 0 };
      const loop = () => {
        if (!live) return;
        try {
          const t0 = performance.now();
          render();
          if (__DEV__) {
            const dt = stat.last ? t0 - stat.last : 16;
            stat.last = t0;
            stat.frames += 1;
            stat.sum += dt;
            stat.worst = Math.max(stat.worst, dt);
            if (dt > 20) stat.hitches += 1;
            stat.render += performance.now() - t0;
            (globalThis as { __rdsGpu?: unknown }).__rdsGpu = { frames: stat.frames, avgMs: +(stat.sum / stat.frames).toFixed(2), worstMs: +stat.worst.toFixed(1), hitches: stat.hitches, renderMs: +(stat.render / stat.frames).toFixed(2) };
            if (stat.frames >= 600) Object.assign(stat, { frames: 0, sum: 0, worst: 0, hitches: 0, render: 0 });
          }
        } catch {
          live = false;
          report.current(false);
          return;
        }
        frame = requestAnimationFrame(loop);
      };
      report.current(true);
      loop();
      cleanup = () => {
        try {
          context.unconfigure();
          foilContext.unconfigure();
          foilColor.destroy();
          envTex.destroy();
          confTex.destroy();
          vbuf.destroy();
          color.destroy();
          depth.destroy();
          root.destroy();
          device.destroy();
        } catch {
          // Already lost.
        }
      };
    };
    start().catch(() => {
      if (live) report.current(false);
    });
    return () => {
      live = false;
      cancelAnimationFrame(frame);
      cleanup();
    };
  }, [ref, foilRef, width, height, time, driver]);

  return (
    <>
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Canvas ref={ref} style={StyleSheet.absoluteFill} opaque={false} />
      </View>
      {under}
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Canvas ref={foilRef} style={StyleSheet.absoluteFill} opaque={false} />
      </View>
      {children}
    </>
  );
}
