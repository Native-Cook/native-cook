import { Easing } from 'react-native-reanimated';

/** Every motion value of the block. Angles in degrees, times in ms unless named in seconds. */

/** QA only: true draws the source's lever without the upgrade layers, for the pixel gate. Ship false. */
export const REPLICA_ONLY = false;

export const PULL = {
  restAngle: 45, // at rest the lever hangs down to the right with its knob (f086–f088)
  maxAngle: 42, // lever angle when full (f098): it rises a little as it fills
  leverPull: 90, // finger travel on the artboard that fills the lever
  overAngle: 22, // upgrade: past full the lever rubber-bands down, at most this many degrees
  overSoftness: 70, // finger travel past full that gives half of overAngle
  overTick: 6, // a rigid tick per this many degrees of stretch
  degPerPt: 0.335, // finger velocity (pt/s) to the knob's angular velocity (deg/s): 180 / (π · length)
  throwBoost: 0.6, // share of the release velocity handed to the throw
  maxThrow: 900, // deg/s cap
} as const;

export const SPRING = {
  home: { damping: 18, stiffness: 300, mass: 1 }, // early release: back to rest with the source's small dip past it
  throwUp: { damping: 16, stiffness: 240, mass: 1 }, // armed release: up to −44° (f056–f058)
  fall: { damping: 12, stiffness: 260, mass: 1 }, // drained: drops back to rest (f075)
  bloom: { damping: 9, stiffness: 160, mass: 0.8 }, // upgrade: the relit cap swells once
} as const;

/** Light and material (upgrade). */
export const LIGHT = {
  direction: -135, // light from the top left, degrees on screen
  highlightOffset: 0.18,
  highlightSize: 0.55,
  highlightRamp: 8, // degrees away from rest before the knob's specular is fully on
  restAngle: 45,
  knobSoft: 1.5, // pt: the knob's soft edge (the source's touch disc is not crisp)
  haloSize: 34, // the loading halo: about twice the cap's radius, plus its blur
  haloBlur: 18,
  haloMin: 0.25, // breathing opacity while loading
  haloMax: 0.6,
  haloBreath: 0.9, // s per breath
  cometHead: 12,
  cometBlur: 14,
  cometTrail: 7, // dots behind the head
  cometTrailPt: 52, // the trail's length
  shaftLight: 0.55, // the shaft lights up behind the comet
  ripple: 160, // the knob's ring grows to this diameter (≈ 80 pt out from the knob)
  rippleMs: 620,
  shadow: { x: 7, y: 11, blur: 10, opacity: 0.55 }, // the lever's cast shadow, screen space
  arc: { radius: 34 /* = LAYOUT.arcRadius, two lever thicknesses */, dots: 7, from: 100, to: 168, size: 4 }, // the tick read-out, degrees round the pivot
} as const;

export const TIMING = {
  dotMs: 250, // the fill drains one dot at a time
  upAngle: -44,
  relaxAngle: -30, // the lever eases off before it falls (f070–f074)
  relaxMs: 300,
  dimMs: 800, // after the fall the white goes out, then the cap lights again (f076–f087)
  lightMs: 160,
  swayAmplitude: 2.5, // upgrade: degrees the thrown lever sways while the request runs
  swayHalfPeriod: 550,
  swayFade: 240,
  bloomScale: 1.7,
  glowRise: 1800, // the cap's glow builds while loading
  pulse: 640, // the light runs down the shaft
  easeInOut: Easing.bezier(0.77, 0, 0.175, 1), // on-screen movement (animate-expo: never ease-in on UI)
  easeOut: Easing.bezier(0.23, 1, 0.32, 1),
  knobFlash: 140,
  knobFade: 520,
  shake: [5, -4, 2.6, -1.3, 0] as const, // payoff: degrees, a dust-free shake when the cap relights
  shakeStep: 70,
  charIn: 220, // caption morph: per character
  charOut: 160,
  charStagger: 18,
  idleAmplitude: 1.2, // ambient: degrees of breathing at rest
  idlePeriod: 2.75, // seconds; four periods per loop so the loop stays seamless
  caption: 180,
  doneLabel: 1600, // how long "Updated just now" stays
  reduced: 180, // Reduce Motion returns and cross-fades
} as const;

/** The demo request; replace with your own call. */
export const FETCH_MS = 2300;

/** Autoplay: one scripted finger on one clock, seconds. */
export const LOOP_MS = 11_000;
export const RESUME_MS = 3_500;
export const DEMO = {
  full: { down: 0.6, moveEnd: 1.75, up: 2.1, depth: 1.55 }, // pull past full into the rubber band and let go
  resolve: 4.4, // the scripted request resolves
  partial: { down: 6.4, moveEnd: 7.0, up: 7.2, depth: 0.5 }, // a short pull that steps back down
} as const;
