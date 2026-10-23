// Shared serrated geometry: the renderer and offline proofs trace exactly the same paths.
import { PACK, TEAR } from './tear-contract';

export type TearPathBuilder = {
  moveTo(x: number, y: number): unknown;
  lineTo(x: number, y: number): unknown;
  close(): unknown;
};

export function tearYAt(line: number[], xLocal: number): number {
  'worklet';
  const x = Math.max(0, Math.min(PACK.width, xLocal));
  const column = Math.min(TEAR.columns - 2, Math.floor(x / TEAR.step));
  const fraction = (x - column * TEAR.step) / TEAR.step;
  const offset = (line[column] ?? 0) * (1 - fraction) + (line[column + 1] ?? 0) * fraction;
  const tooth = 1 - Math.abs(fraction * 2 - 1);
  const depth = 1.25 + 0.65 * Math.sin(column * 7.13 + 0.8);
  return TEAR.y + offset + tooth * depth;
}

export function traceTornEdge(builder: TearPathBuilder, line: number[], front: number, mouth = 0, back = false): void {
  'worklet';
  const end = Math.max(0, Math.min(PACK.width, front));
  builder.moveTo(0, tearYAt(line, 0));
  const count = Math.ceil(end / 1.5);
  for (let i = 1; i <= count; i += 1) {
    const x = Math.min(i * 1.5, end);
    const opening = Math.pow(Math.max(0, Math.sin(Math.PI * x / PACK.width)), 0.32) * mouth * (back ? -5 : 2);
    builder.lineTo(x, tearYAt(line, x) + opening);
  }
}

export function tracePack(builder: TearPathBuilder, line: number[], front: number, mouth: number): void {
  'worklet';
  if (front > 0.01) {
    traceTornEdge(builder, line, front, mouth);
    if (front < PACK.width) {
      builder.lineTo(front, -1);
      builder.lineTo(PACK.width + 1, -1);
    }
  } else {
    builder.moveTo(-1, -1);
    builder.lineTo(PACK.width + 1, -1);
  }
  builder.lineTo(PACK.width + 1, PACK.height + 1);
  builder.lineTo(-1, PACK.height + 1);
  if (front <= 0.01) {
    builder.lineTo(-1, TEAR.y + 3);
    builder.lineTo(TEAR.notch, TEAR.y);
    builder.lineTo(-1, TEAR.y - 3);
  }
  builder.close();
}

export function traceMouth(builder: TearPathBuilder, line: number[], front: number, mouth: number): void {
  'worklet';
  traceTornEdge(builder, line, front, mouth, true);
  const count = Math.ceil(front / 1.5);
  for (let i = count; i >= 0; i -= 1) {
    const x = Math.min(front, i * 1.5);
    builder.lineTo(x, tearYAt(line, x) + 2 * mouth * Math.pow(Math.max(0, Math.sin(Math.PI * x / PACK.width)), 0.32));
  }
  builder.close();
}
