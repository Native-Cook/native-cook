/** Pure, reversible formation shared by material, native actors and gesture bounds. */
export type PeelFormation = {
  body: number;
  portrait: number;
  name: number;
  handle: number;
  skills: number;
  score: number;
  seal: number;
  curl: number;
};

export type PeelSurfaceBounds = {
  x: number;
  y: number;
  width: number;
  height: number;
  radius: number;
};

function peelStep(progress: number, start: number, end: number) {
  'worklet';
  const t = Math.max(0, Math.min(1, (progress - start) / (end - start)));
  return t * t * (3 - 2 * t);
}

/** C1 monotone actor gathers; only the blank material curl rises and falls. */
export function peelFormation(progress: number, reduced = false): PeelFormation {
  'worklet';
  const p = Math.max(0, Math.min(1, progress));
  if (reduced) {
    const endpoint = p > 0 ? 1 : 0;
    return { body: endpoint, portrait: endpoint, name: endpoint, handle: endpoint,
      skills: endpoint, score: endpoint, seal: endpoint, curl: 0 };
  }
  const remainder = 1 - peelStep(p, 0, 0.8);
  return {
    body: 1 - remainder * remainder * remainder,
    portrait: peelStep(p, 0.18, 0.82),
    name: peelStep(p, 0.24, 0.88),
    handle: peelStep(p, 0.28, 0.9),
    skills: peelStep(p, 0.3, 0.94),
    score: peelStep(p, 0.34, 0.96),
    seal: peelStep(p, 0.08, 0.97),
    curl: peelStep(p, 0, 0.14) * (1 - peelStep(p, 0.7, 0.97)),
  };
}

/** Same body envelope for shader and the partial-card gesture hit region. */
export function peelSurfaceBounds(
  progress: number,
  sourceX: number,
  sourceY: number,
  sourceSize: number,
  reduced = false,
  width = 320,
  height = 590,
  radius = 34,
): PeelSurfaceBounds {
  'worklet';
  const body = peelFormation(progress, reduced).body;
  const size = Math.max(1, sourceSize);
  const x = sourceX - size * 0.5;
  const y = sourceY - size * 0.5;
  return {
    x: x + ((393 - width) * 0.5 - x) * body,
    y: y + (85 - y) * body,
    width: size + (width - size) * body,
    height: size + (height - size) * body,
    radius: size * 0.5 + (radius - size * 0.5) * body,
  };
}

/** Rigid portrait group: lateral alignment precedes uniform enlargement. */
export function peelAvatarFormation(progress: number, reduced = false) {
  'worklet';
  const p = Math.max(0, Math.min(1, progress));
  if (reduced) {
    const endpoint = p > 0 ? 1 : 0;
    return { x: endpoint, y: endpoint, scale: endpoint };
  }
  return { x: peelStep(p, 0.3, 0.56), y: peelStep(p, 0.3, 0.8),
    scale: peelStep(p, 0.43, 0.8) };
}

/** Exact native portrait clip envelope, also supplied to the blank-lip renderer. */
export function peelAvatarBounds(
  progress: number,
  sourceX: number,
  sourceY: number,
  sourceDiameter: number,
  targetY: number,
  targetScale: number,
  reduced = false,
) {
  'worklet';
  const phase = peelAvatarFormation(progress, reduced);
  const x = sourceX + (196.5 - sourceX) * phase.x;
  const y = sourceY + (targetY - sourceY) * phase.y;
  const sourceScale = (sourceDiameter + 6) / 140;
  const scale = sourceScale + (targetScale - sourceScale) * phase.scale;
  return { x: x - 59 * scale, y: y - 61 * scale, width: 118 * scale, height: 124 * scale };
}
