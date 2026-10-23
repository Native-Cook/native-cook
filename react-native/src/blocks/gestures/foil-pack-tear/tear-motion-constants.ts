/** Motion is measured in stage points, radians and seconds; masses are one. */
export const MOTION = {
  step: 1 / 600,
  maxElapsed: 0.1,
  loop: 13,
  idleSlow: 6.5,
  idleFast: 13 / 3,
  resumeDelay: 3.5,
  catchSpeed: 1400,
  tearTick: 18,
  coastTau: 0.18,
  lightTau: 0.06,
  tapTravel: 8,
  tapTime: 0.3,
  peekDelay: 0.14,
  newPackAt: 0.38,
  // First clear the mouth; only then enlarge, descend and turn the card.
  extractTime: 0.2,
  extractLift: 20,
  revealTravel: 0.58,
  // The card stays on screen while it is pulled clear (its top stops 55 pt from the top edge),
  // and the wrapper has faded by 380 ms, before the turn shows the face.
  revealPackFade: 0.18,
  // The turn starts once the card's foot is above the falling mouth and runs through the descent.
  flipAt: 0.34,
  revealDeadline: 1.5,
  peekFadeIn: 0.3,
  peekFadeOut: 0.12,
  stageKick: { stiffness: 260, damping: 16 },
  tug: { stiffness: 300, damping: 24 },
  mouth: { stiffness: 220, damping: 20 },
  rise: { stiffness: 150, damping: 15 },
  sink: { stiffness: 180, damping: 20 },
  pullReturn: { stiffness: 260, damping: 22 },
  scale: { stiffness: 170, damping: 17 },
  flip: { stiffness: 62, damping: 10 },
  tilt: { stiffness: 140, damping: 12 },
  landing: { stiffness: 190, damping: 19 },
} as const;

/** Each entry is an event, not a frame-level flag; multiple crossed teeth are retained. */
export const HAPTIC = { grab: 1, tooth: 2, open: 3, peek: 4, reveal: 5, face: 6, collect: 7, land: 8 } as const;
