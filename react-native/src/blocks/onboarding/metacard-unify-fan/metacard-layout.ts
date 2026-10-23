export const D = {
  width: 402, height: 874, fanX: 201, fanY: 356, heroX: 201, heroY: 522,
  cardWidth: 144, cardHeight: 91, cardRadius: 7.3, radius: 132, lateral: 0,
  hubApothem: 62, hubRadius: 5.5, slabWidth: 184, slabHeight: 116.6, slabRadius: 9.6, rosetteScale: 176 / 144,
  heroWidth: 304, heroHeight: 192, heroRadius: 13, thickness: 3.2, camera: 555,
  yaw: -26, pitch: 13, roll: -1.5, spin: -49, drift: 7.5,
  markSize: 34, heroMarkSize: 36, heroMarkX: 262, heroMarkY: 42,
  chipX: 26, chipY: 58, chipWidth: 80, chipHeight: 40,
  titleSize: 28, titleTracking: -0.4, lineHeight: 34,
  title1: [616, 650], title2: [326, 360], wordY: 256, wordSize: 24,
  wordTracking: -0.6, wordMark: 22, wordGap: 8,
  hintY: 806, hintSize: 13, hintTracking: 0.2, hintMark: 10, hintGap: 8, hintOpacity: 0.46,
  fanHitRadius: 226, hubHitRadius: 66,
  shadowX: -1.8, shadowY: 3.56, shadowMargin: 24,
  shadowWidth: 176 * 144 / 128, shadowHeight: 129 * 91 / 81,
} as const;
export function designFrame(width: number, height: number) {
  'worklet';
  const k = Math.min(width / D.width, height / D.height);
  return { k, x: (width - D.width * k) / 2, y: (height - D.height * k) / 2 };
}
export const COPY = ['Unify the limit of', 'all your cards', 'Meet the first', 'Metacard', 'onefold', 'Tap the hub to unify', 'Tap the card to split'] as const;
export function markContours() {
  const a = 10.5, b = a * Math.tan(Math.PI / 8);
  return [[-b, -a], [b, -a], [a, -b], [a, b], [b, a], [-b, a], [-a, b], [-a, -b]];
}

/** Path commands: 0 move, 1 line, 2 quadratic, 3 close. No platform imports. */
export function markCommands(): number[][] {
  const points = markContours(), commands: number[][] = [];
  const trim = Math.tan(Math.PI / 8);
  for (let i = 0; i < 8; i++) {
    const p = points[i], previous = points[(i + 7) % 8], next = points[(i + 1) % 8];
    const before = Math.hypot(previous[0] - p[0], previous[1] - p[1]), after = Math.hypot(next[0] - p[0], next[1] - p[1]);
    commands.push([i === 0 ? 0 : 1, p[0] + (previous[0] - p[0]) / before * trim, p[1] + (previous[1] - p[1]) / before * trim]);
    commands.push([2, p[0], p[1], p[0] + (next[0] - p[0]) / after * trim, p[1] + (next[1] - p[1]) / after * trim]);
  }
  commands.push([3]);
  const slot = [[0, -4.75, -3.95], [1, 4.75, -3.95], [2, 6.25, -3.95, 6.25, -2.45],
    [1, 6.25, 2.45], [2, 6.25, 3.95, 4.75, 3.95], [1, -4.75, 3.95],
    [2, -6.25, 3.95, -6.25, 2.45], [1, -6.25, -2.45], [2, -6.25, -3.95, -4.75, -3.95], [3]];
  const a = -22 * Math.PI / 180;
  for (const c of slot) {
    const command = [c[0]];
    for (let j = 1; j < c.length; j += 2) command.push(c[j] * Math.cos(a) - c[j + 1] * Math.sin(a), c[j] * Math.sin(a) + c[j + 1] * Math.cos(a));
    commands.push(command);
  }
  return commands;
}
