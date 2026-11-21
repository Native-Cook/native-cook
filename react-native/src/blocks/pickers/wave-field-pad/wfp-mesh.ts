import { createVertexBuffer, meshViews, type VertexBuffer } from 'react-native-tgfx';

import { clamp01, type Frame, mix, smooth, waveAmp, waveSpread } from './wfp-motion';
import { C, G } from './wfp-theme';

export const WAVE_GAP = 0.105;
const STRANDS = [
  { k: -2, alpha: 0.14, width: 1.5 },
  { k: 2, alpha: 0.22, width: 1.5 },
  { k: -1, alpha: 0.32, width: 1.5 },
  { k: 1, alpha: 0.48, width: 1.5 },
  { k: 0, alpha: 1, width: 2.5 },
] as const;
const SAMPLES = Array.from({ length: G.wave.points + 1 }, (_, sample) => sample)
  .filter(sample => Math.abs(sample / G.wave.points - 0.5) >= WAVE_GAP);
const VERTICES_PER_STRAND = SAMPLES.length * 4;
const INDEX_COUNT = (SAMPLES.length - 2) * 18 * STRANDS.length;
const SAMPLE_STEP = (G.wave.x1 - G.wave.x0) / G.wave.points;

/** Each strand has an opaque ribbon between two feathered edges. The index buffer leaves the frequency readout gap open. */
export function createWaveMesh(): VertexBuffer {
  const buffer = createVertexBuffer({
    maxVertices: VERTICES_PER_STRAND * STRANDS.length,
    maxIndices: INDEX_COUNT,
    colors: true,
  });
  const teal = Number.parseInt(C.teal.slice(1), 16);
  const red = (teal >>> 16) / 255;
  const green = ((teal >>> 8) & 255) / 255;
  const blue = (teal & 255) / 255;
  let index = 0;
  for (let strand = 0; strand < STRANDS.length; strand++) {
    const base = strand * VERTICES_PER_STRAND;
    for (let column = 0; column < SAMPLES.length; column++) {
      const vertex = base + 4 * column;
      for (let edge = 0; edge < 4; edge++) {
        const color = (vertex + edge) * 4;
        buffer.colors[color] = red;
        buffer.colors[color + 1] = green;
        buffer.colors[color + 2] = blue;
        buffer.colors[color + 3] = edge === 0 || edge === 3 ? 0 : STRANDS[strand].alpha;
      }
      if (column + 1 === SAMPLES.length || SAMPLES[column + 1] !== SAMPLES[column] + 1) continue;
      for (let edge = 0; edge < 3; edge++) {
        const v = vertex + edge;
        buffer.indices[index++] = v;
        buffer.indices[index++] = v + 1;
        buffer.indices[index++] = v + 4;
        buffer.indices[index++] = v + 1;
        buffer.indices[index++] = v + 5;
        buffer.indices[index++] = v + 4;
      }
    }
  }
  return buffer;
}

/** Write only the changing positions to shared memory. Topology and color are constant across the loop and live drag. */
export function updateWaveMesh(buffer: VertexBuffer, f: Frame, pixelSize: number) {
  'worklet';
  const { positions } = meshViews(buffer);
  const loopCount = Math.round(1 + 3 * clamp01(f.u));
  const spread = mix(waveSpread(f.du), 3, f.settle) * (1 - f.lock);
  const cycles = 2.6 + 0.35 * f.hz;
  const standing = Math.cos(2 * Math.PI * f.beat);
  const modulation = 0.78 + 0.22 * standing;
  const feather = pixelSize * 0.75;
  for (let strand = 0; strand < STRANDS.length; strand++) {
    const { k, width } = STRANDS[strand];
    const amplitude = waveAmp(f.dv) * (1 - 0.1 * Math.abs(k));
    const offset = (k * spread) / 90 - 0.5 * f.beat;
    const base = strand * VERTICES_PER_STRAND * 2;
    for (let column = 0; column < SAMPLES.length; column++) {
      const x = SAMPLES[column] / G.wave.points;
      const taper = smooth(WAVE_GAP, WAVE_GAP + 0.1, Math.abs(x - 0.5));
      const envelope = Math.pow(Math.sin(Math.PI * x), 1.3) * taper;
      const travel = envelope * modulation * Math.sin(2 * Math.PI * (cycles * x + offset));
      const stand = taper * standing * Math.sin(Math.PI * loopCount * x);
      positions[base + column * 8 + 1] = G.wave.y + amplitude * mix(travel, stand, f.lock);
    }

    let previousY = positions[base + 1];
    for (let column = 0; column < SAMPLES.length; column++) {
      const sample = SAMPLES[column];
      const start = base + column * 8;
      const x = G.wave.x0 + sample * SAMPLE_STEP;
      const y = positions[start + 1];
      const hasPrevious = column > 0 && SAMPLES[column - 1] === sample - 1;
      const hasNext = column + 1 < SAMPLES.length && SAMPLES[column + 1] === sample + 1;
      const nextY = hasNext ? positions[start + 9] : y;
      const dx = SAMPLE_STEP * (Number(hasPrevious) + Number(hasNext));
      const dy = nextY - (hasPrevious ? previousY : y);
      const length = Math.hypot(dx, dy) || 1;
      const nx = -dy / length;
      const ny = dx / length;
      const half = width * 0.5;
      for (let edge = 0; edge < 4; edge++) {
        const distance = (edge < 2 ? -1 : 1) * (half + (edge === 0 || edge === 3 ? feather : 0));
        positions[start + edge * 2] = x + nx * distance;
        positions[start + edge * 2 + 1] = y + ny * distance;
      }
      previousY = y;
    }
  }
}
