import { clamp } from './pjg-motion';
import type { VesselTable } from './pjg-vessel';
import { ref } from './pjg-theme';
export function bubblesAt(time: number, meanY: number, table: VesselTable, heights: number[]) {
  'worklet';
  const data: number[] = [];
  for (let i = 0; i < 12; i++) {
    const seed = i + 1,
      pose = ref.material.bubbleSeeds[i],
      radius = pose[2];
    const range = Math.max(table.height - meanY - 20, 10);
    let x = pose[0] * table.width;
    const col = clamp(Math.round((x / table.width) * 63), 1, 62);
    const slope = (heights[col + 1] - heights[col - 1]) / ((table.width / 63) * 2);
    const y = table.height - 18 - ((pose[1] * range + time * (10 + radius * 5)) % range);
    x += (Math.sin(seed * 3.1 + time * 2) - Math.sin(seed * 3.1)) * 2 + slope * time * 2;
    const row = clamp(Math.floor((y / table.height) * 128), 0, 127);
    const lens = Math.min(x - table.left[row], table.right[row] - x) < 30 ? 0.85 : 1;
    data.push(x, y, radius, lens);
  }
  return data;
}
