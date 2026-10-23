import type { StripFingerprint } from './face-data';

/** Read actual uploaded texels, so decoder and upload conversions are both checked. */
export async function verifyStrip(device: GPUDevice, texture: GPUTexture, expected: StripFingerprint): Promise<void> {
  if (texture.width !== expected.width || texture.height !== expected.height
    || !['r8unorm', 'rgba8unorm'].includes(texture.format)) {
    throw new Error('Invalid medal strip dimensions or format.');
  }
  const channels = texture.format === 'r8unorm' ? 1 : 4;
  // Each one-pixel copy has its own aligned row; only 10 KiB per strip is mapped.
  const readback = device.createBuffer({ label: 'Medal strip verification',
    size: expected.samples.length * 256, usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ });
  try {
    const encoder = device.createCommandEncoder();
    expected.samples.forEach(([x, y], i) => {
      encoder.copyTextureToBuffer({ texture, origin: [x, y] },
        { buffer: readback, offset: i * 256, bytesPerRow: 256 }, [1, 1]);
    });
    device.queue.submit([encoder.finish()]);
    await readback.mapAsync(GPUMapMode.READ);
    const bytes = new Uint8Array(readback.getMappedRange());
    for (let i = 0; i < expected.samples.length; i++) {
      const value = expected.samples[i]![2];
      for (let c = 0; c < channels; c++) {
        if (Math.abs(bytes[i * 256 + c]! - (c === 3 ? 255 : value)) > 1) {
          throw new Error('strips were colour-converted');
        }
      }
    }
  } finally {
    if (readback.mapState === 'mapped') readback.unmap();
    readback.destroy();
  }
}
