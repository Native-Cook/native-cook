// Named timing, physics and emission values shared by the simulator and its proof renderer.
export const M = {
  step: 1 / 240, maxFrame: 1 / 30, epsilon: 1e-9, seed: 0x51cafe,
  chargeDuration: 2.4, chargeKnee: 0.85, chargeLinearEnd: 0.85,
  charging: { glow: 0.18, glyphPower: 0.8, flickerUntil: 0.5 },
  press: { sink: 6, scale: 0.985, stiffness: 520, damping: 34 },
  release: { stiffness: 260, damping: 20 },
  strike: { leader: 0.1, end: 1.6, ringAt: 0.17, treeDuration: 0.11,
    flash: 0.92, flashHold: 1 / 30, flashTau: 0.07, drop: 5, dipScale: 0.97, kick: 14,
    rotation: 1.4 * Math.PI / 180, glyph: 1.8, glyphScale: 1.14, glyphCool: 0.6,
    stiffness: 420, damping: 18, glowRise: 0.22, stampAt: 0.23, stampDuration: 0.2,
    branchEnd: 0.16, channelEnd: 0.43, treeHot: 1.6, treeCold: 0.3, treeTau: 0.7,
    ringHot: 1.6, ringSettle: 0.5, shock: 0.52, dim: 0.35, dimDuration: 0.06, glyphScaleTau: 0.12,
    leaderJump: 0.02, leaderSteps: 5, leaderLevel: 0.3, branchLevel: 0.8,
    pulseEnds: [0.15, 0.19, 0.245, 0.295], pulseLevels: [1, 0.25, 0.8, 0.15, 0.5],
    shimmerInterval: 0.04, shimmerVariants: 3, shimmerPoints: 0.38, shaft: 1.5 },
  drain: { energy: 0.9, glow: 0.22, ring: 0.3, type: 0.2, end: 1.2, rate: 0.6, acceleration: 2.2,
    glyph: 0.9, glyphPower: 0.7, flickerLevel: 0.45, bezierX1: 0.77, bezierX2: 0.175, flickers: [0.16, 0.205, 0.39, 0.445] },
  touch: { rise: 0.09, fall: 0.06, inset: 10, bottom: 440, hitSlop: 16, holdSink: 4, tapTime: 0.25, tapTravel: 12 },
  tick: { slow: 0.22, fast: 0.045, light: 0.4, medium: 0.75 },
  shake: { base: 0.3, gain: 3.4, rotation: 0.5 * Math.PI / 180, interval: 1 / 60 },
  ember: { base: 0.12, amplitude: 0.02, period: 4, tickPeriod: 4.5, tickDuration: 0.05, tickLevel: 0.4 },
  flicker: { period: 0.19, duration: 0.045, depth: 0.55 },
  charged: { breath: 0.06, period: 3.2, pulsePeriod: 2.4, pulseDuration: 0.7 },
  filament: { interval: 0.07, fastInterval: 0.055, fastAt: 0.8, thirdAt: 0.6, base: 0.35, gain: 0.65 },
  arc: { threshold: 0.15, slowPause: 0.26, fastPause: 0.04, lifeMin: 0.05, lifeMax: 0.11, chargedPeriod: 1.4 },
  corona: { threshold: 0.35, period: 0.12, lifeMin: 0.04, lifeMax: 0.09, chargedPeriod: 1.8 },
  spark: { ringCount: 30, boltCount: 14, groundCount: 12, drainCount: 6, minSpeed: 120, maxSpeed: 760,
    gravity: 900, drag: 0.65, minLife: 0.35, maxLife: 1.3, birthSpan: 0.05, streak: 0.022, minLength: 0.6 },
  reduced: { flash: 0.2, channel: 0.3, channelLevel: 0.6 },
  script: { period: 12, firstDown: 0.8, firstUp: 1.9, secondDown: 2.9, release: 5.8,
    firstPoint: [98, 330], secondPoint: [300, 410], playFrom: [96, 312], playTo: [312, 420],
    playDown: 8.3, playUp: 9.3, tap: 10, resume: 3.5, wanderX: 46, wanderY: 22, frequencyX: 0.43, frequencyY: 0.61 },
  print: { dotPop: 0.12, stampDrop: 6, stampScale: 0.15, bodyRise: 4, captionRise: 4,
    captionOut: 0.09, captionIn: 0.24, captionAt: 1, captionDuration: 0.16, releaseHz: 2, stampDamping: 6, stampFrequency: 10 },
  fadeMs: 260, accessibilityInterval: 0.1, settleTolerance: 0.001,
} as const;
export const EVENT = { selection: 1, light: 2, medium: 4, rigid: 8, soft: 16, heavy: 32, success: 64 } as const;
export const LIGHTNING_PAINT = {
  impact: { widths: [22, 8, 2.8] },
  main: { widths: [16, 5.5, 2], blurs: [12, 3, 0], alpha: [0.42, 0.9, 1] },
  branch: { widths: [7, 2.2, 0.9], blurs: [9, 2.2, 0], alpha: [0.24, 0.7, 1] },
  twig: { widths: [4.5, 1.4, 0.55], blurs: [6, 1.5, 0], alpha: [0.18, 0.6, 1] },
  colors: ['#FFC400', '#FFF27A', '#FFFFFF'],
} as const;

// A blank caption is a destination, never a route through the other labels.
export function captionOpacity(from: number, to: number, elapsed: number, index: number) {
  'worklet';
  if (from === to || elapsed < 0) return index === to ? 1 : 0;
  if (to === -1) return index === from ? Math.max(0, 1 - elapsed / M.print.captionOut) : 0;
  if (from === -1) return index === to ? Math.min(1, elapsed / M.print.captionIn) : 0;
  const t = Math.min(1, elapsed / M.print.captionDuration);
  return index === from ? Math.max(0, 1 - 2 * t) : index === to ? Math.max(0, 2 * t - 1) : 0;
}
