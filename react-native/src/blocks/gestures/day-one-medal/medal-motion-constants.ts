// Seconds, points and radians throughout; the reference turn's degree values are converted here.
export const RAD = Math.PI / 180;

export const M = {
  // A common integration step makes 60 Hz and 120 Hz use the same turn equations.
  step: 1 / 120,
  // A stalled frame must not fling the medal across the sheet on resume.
  maxFrame: 0.034,
  // Milliseconds from the frame callback become seconds at the boundary.
  milliseconds: 1000,
  // Floating-point roundoff must not delay a deadline by a whole frame.
  epsilon: 1e-9,
  // The reference cylinder tracks a finger immediately, at 0.6 degrees per point.
  dragRatio: 0.6 * RAD,
  // The reference's speed cap prevents an accidental launch.
  maxVelocity: 1800 * RAD,
  // The reference's travel and time distinguish a tap without delaying a drag.
  tap: { travel: 8, time: 0.35 },
  // Ordinary turns retain the reference's drag, handoff speed and landing prediction.
  coast: { tau: 0.85, handoff: 260 * RAD, lookahead: 0.22 },
  // The reference's heavy spring overshoots once, then stops at a face.
  settle: { stiffness: 110, damping: 13, speed: RAD, distance: 0.08 * RAD },
  // A tap has the reference's small initial push.
  flipKick: 220 * RAD,
  // Opening retains the reference's arrival, but never stamps a blank automatically.
  enter: { from: -40 * RAD, velocity: 350 * RAD, stiffness: 30, damping: 7 },
  // A lap keeps the reference's slower full turn.
  lap: { kick: 520 * RAD, stiffness: 38, damping: 10.5 },
  // The demo drifts as slowly as the reference finger-led turn.
  drift: { stiffness: 26, damping: 8 },
  // Rest alone carries the original roll-free breathing motion.
  sway: { amplitude: 8 * RAD, period: 5.2, delay: 0.9, rise: 1.1, tip: 0.03 },
  // The resting lean lets the low lamp graze the numeral.
  pitch: 0.035,
  // The displayed angular speed has the reference's 60 ms smoothing.
  spinTau: 0.06,
  // The reference press lasts 120 ms and releases one and a half times more slowly.
  press: { in: 0.12, out: 0.18 },
  // Crossing an edge should not buzz faster than the original 20 Hz cap.
  edgeTick: 0.05,
  // A landed face gets the reference's travelling band of light.
  sweep: { from: -1.15, to: 1.15, duration: 0.62, heatLimit: 0.05 },
  // A forge takes deliberate effort; a concave curve slows towards the stamp.
  forge: { duration: 1.4, power: 1.6, release: 0.85, stiffness: 150, damping: 18 },
  // Charging trembles within 1.5 pt and 0.6 degrees, with two incommensurate tones.
  tremble: { points: 1.5, roll: 0.6 * RAD, fast: 31, slow: 19, blend: 0.3 },
  // The tool chatters faster as the metal gets hotter.
  forgeTick: { low: 4, high: 14 },
  // The original heat envelope surrounds a fast, hard stamp and a recoil away from the viewer.
  strike: { rise: 0.11, cool: 1.5, stamp: 0.26, pitch: -9 * RAD, stiffness: 110, damping: 13 },
  // The existing canvas uses this much extra key light at full heat.
  glint: 1.2,
  // A deliberately fast release earns several revolutions before the coin starts to shudder.
  coin: { threshold: 1400 * RAD, tau: 1.6, handoff: 420 * RAD, demo: 1600 * RAD },
  // Inverse-square amplitude tracks the accelerating precession; the final taper makes it stop dead.
  wobble: { rise: 0.15, duration: 1.3, amplitude: 14 * RAD, low: 2.5, high: 9, taper: 0.8, tick: 1 / 30 },
  // Light reaches a finger 180 pt away; a critical spring is effectively home in half a second.
  light: { reach: 180, rate: 12 },
  // The ribbon catches just beyond halfway down and withdraws more quickly than it arrives.
  wear: { in: 0.7, out: 0.45, catch: 0.55, drop: 10, stiffness: 110, damping: 13 },
  // A small-angle gravitational pendulum loses most of its amplitude in four or five swings.
  pendulum: { gravity: 980, damping: 0.5, minLength: 80, kick: 0.34, flip: 0.14 },
  // Pulling beyond 28 degrees offers progressively less travel, with no hard corner.
  pull: { limit: 28 * RAD, rubber: 8 * RAD },
  // Engraving spends 2.04 s cutting and 360 ms lifting between four lines.
  engrave: { duration: 2.4, pause: 0.12, spitting: 0.5 },
  // These are the order-map windows specified by the asset contract.
  lines: [[0.05, 0.34], [0.36, 0.44], [0.48, 0.74], [0.78, 1]] as const,
  // The face rectangle follows the existing slab's 190 by 355 proportions.
  aspect: 190 / 355,
  // A climbing spark stays inside the numeral area when no front path is supplied.
  forgeSpot: { foot: 0.78, head: 0.22 },
  // A clear vertical intention changes the milestone without delaying sideways movement.
  swipe: { travel: 36, speed: 500 },
  // Three milestones are the full range of the supplied texture set.
  lastDay: 2,
  // Molten metal peaks halfway through the eased transition.
  morph: { duration: 0.9, heat: 0.55 },
  // At an end stop the medal resists vertically, then returns with the same heavy spring.
  nudge: { points: 5, stiffness: 110, damping: 13 },
  // Reduced motion uses short optical transitions, never inertial motion.
  reduced: { out: 0.1, in: 0.14, strike: 0.2, engrave: 0.4, morph: 0.25, wear: 0.2 },
  // An unset strike must be older than any particle lifetime.
  unstruck: -10,
} as const;

// Discrete events are the only reasons the simulation asks the RN thread to do work.
export const EVENT = {
  tick: 1 << 0,
  land: 1 << 1,
  success: 1 << 2,
  heavy: 1 << 3,
  rigid: 1 << 4,
  soft: 1 << 5,
  warning: 1 << 6,
  changed: 1 << 7,
  textures: 1 << 8,
} as const;
