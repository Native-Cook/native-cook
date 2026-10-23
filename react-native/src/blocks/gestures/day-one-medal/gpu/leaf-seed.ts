export type LeafSeed = { position: [number, number, number, number]; velocity: [number, number, number, number]; shape: [number, number, number, number]; fall: [number, number, number, number]; travel: [number, number, number, number] };

/** Fixed seed: identical rain on the phone and in the headless proof. */
export function seedParticles(count: number, width: number, height: number, half: number, centreY: number, pixelRatio: number, centreX = width / 2): LeafSeed[] {
  let seed = 20261013;
  const next = () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) | 0;
    return (seed >>> 0) / 4294967296;
  };
  const pointsPerUnit = half / pixelRatio;
  return Array.from({ length: count }, (_, index) => {
    const depth = next();
    const z = index < 4 ? 1.45 + depth * 0.2 : (depth - 0.5) * 1.7;
    const perspective = (6.9 - z) / 6.9;
    const phase = next() * Math.PI * 2;
    // Most pieces must survive phone-size downsampling, with a ten-percent foil tail.
    const population = next();
    const size = population < 0.1 ? 8 + next() * 3 : population < 0.16 ? 3 + next() * 2 : 4 + next() * 4;
    return {
      position: [((next() - 0.5) * width * 1.15 + width / 2 - centreX) / half * perspective,
        (index < count * 0.55 ? -1.1 + next() * 2.2 : centreY / half + 0.20 + next() * height / half * 0.9) * perspective, z, next()],
      velocity: [0, 0, 0, 0],
      shape: [size / pointsPerUnit, size * (0.65 + next() * 0.3) / pointsPerUnit,
        phase, (next() < 0.5 ? -1 : 1) * (1.2 + 3.4 * next())],
      fall: [(62 + 88 * next()) * (0.7 + 0.5 * depth) / pointsPerUnit,
        (6 + 14 * next()) / pointsPerUnit, 0.6 + 1.3 * next(), 2.2 + 4.6 * next()],
      travel: [0, 0, 0, 0],
    };
  });
}
