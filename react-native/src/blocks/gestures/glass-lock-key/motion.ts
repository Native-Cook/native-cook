// Every motion value of the block. Seconds unless the name ends in _MS; points unless noted.

export const STEP = 1 / 120;
export const MAX_STEPS = 30; // per frame: the sim keeps wall-clock time through dropped frames, so the loop stays 12 s

export const CARRY = {
  lift: 1.04, // key scale while held
  magnet: 72, // tip to keyhole distance where the keyhole starts pulling
  magnetPull: 0.55, // share of the remaining gap closed at contact range
  seat: 12, // tip within this of the keyhole: the key goes in
  home: { stiffness: 220, damping: 22 },
  bob: 3, // idle float, points
  bobPeriod: 3, // seconds
  pullOut: 34, // drag down this far from a seated key at 0° to take it out
};

export const INSERT = { duration: 0.22, outDuration: 0.26 };

export const TURN = {
  radius: 60, // horizontal drag per radian
  open: Math.PI / 2,
  detents: [Math.PI / 6, Math.PI / 3, Math.PI / 2],
  wrong: 0.21, // rubber limit the wrong way (12°)
  visual: 0.83, // drawn turn per real turn: open shows the bow at 75°, a narrow ellipse, not an edge
  back: { stiffness: 260, damping: 24 }, // released short of open: the key springs back
  close: { stiffness: 180, damping: 22 },
};

export const SHACKLE = { stiffness: 320, damping: 14, closeStiffness: 260, closeDamping: 26 };
export const BOUNCE = { stiffness: 420, damping: 16, detentKick: -40, openKick: -120 };
export const SHAKE = { duration: 0.36, amp: 6, cycles: 3 };
export const SHEET_MOTION = { delay: 0.3, open: 0.5, close: 0.38, stagger: 0.04, reducedFade: 0.2, shackleAfter: 0.3 };
export const GLINT = { duration: 0.7 };

export const HAPTIC = { grab: 1, seat: 2, detent: 3, open: 4, wrong: 5 } as const;

// Autoplay: 12 s. Finger offsets in points; the carry is relative to the key's tip at rest, the turn to the bow.
export const LOOP_MS = 12_000;
export const RESUME_MS = 3_500;
export const SCRIPT = {
  carry: [0.8, 1.6] as const, // drag the key up into the keyhole
  carryTo: [0, -100] as const,
  turn: [1.95, 3.0] as const, // drag right across the bow
  turnBy: 100,
  lockAt: 8.4, // taps Lock vault
};

// Replica loop (the source, 6 s): the key alone turning, then the padlock with the key in, the shackle pops open,
// holds, closes, and back to the key.
export const REPLICA_LOOP_MS = 6000;
export const REPLICA_T = {
  keySpin: [0, 1.0] as const, // the lone key turns once about its long axis
  toLock: [1.0, 1.35] as const, // cross-fade key → padlock with the key in
  open: [1.9, 2.15] as const, // shackle pops up (spring-like overshoot)
  close: [4.2, 4.45] as const,
  toKey: [5.4, 5.75] as const,
};
export const REPLICA_HOLD = -1; // QA only: pins the clock (seconds) for reference screenshots

// Upgrade (on the replica's Blender layers).
export const GM = {
  magnet: 72, // pt: within this of the keyhole the key is pulled in
  magnetPull: 0.55,
  seat: 14,
  flick: 900, // pt/s toward the keyhole also seats it (velocity OR distance)
  keyHitW: 50,
  keyHitH: 90,
  ringHit: 64,
  grabBelow: 40, // the scripted finger holds the key this far below its centre
  insert: 0.28, // s: the key turns into the lock (key-front → key-in), ease-in-out (it moves on screen)
  turnPx: 110, // pt of horizontal drag for a full turn to open
  wrong: 0.12, // rubber limit the wrong way
  pullOut: 34,
  outPull: 40, // after locking, the key comes out this far and springs home
  outSpeed: 300,
  detents: [1 / 3, 2 / 3, 1],
  back: { k: 260, c: 24 },
  home: { k: 220, c: 22 },
  shackle: { k: 320, c: 14, kClose: 260, cClose: 26 },
  shackleKick: 4,
  shackleAfter: 0.3,
  sheetDelay: 0.3,
  sheetIn: 9, // per second (exponential approach: no ease-in)
  sheetOut: 12,
  glint: 0.7,
  tick: { duration: 0.16, alpha: 0.35 }, // each detent: a light flash at the keyhole, out in 160 ms (ease-out)
  recoil: { amp: 3, decay: 12, hz: 7 }, // pt: the body drops and settles as the shackle springs free
  appear: 0.96, // the lock materialises from this scale (never from nothing)
  rows: { stagger: 0.08, rise: 8, span: 0.5 }, // vault rows: each 8 pt up and in, 0.08 of the sheet's travel after the last
  shake: { duration: 0.36, amp: 6, cycles: 3 },
  spin: { every: 4, turn: 1 }, // s: at rest the lone key turns once about its long axis every 4 s, as in the source
  show: { in: 10, out: 4, hold: 1.5 }, // the lock materialises around the keyhole when the key is taken (per s), fades after hold s idle
  rise: 150, // pt the lock rises when the vault opens
  shrink: 0.72,
};
// QA only: slows the upgrade for slow-motion proof captures (1 = real time).
export const G_QA_SPEED = 1;
export const G_HAPTIC = { grab: 1, seat: 2, detent: 3, open: 4, wrong: 5 } as const;
export const G_LOOP_MS = 12_000;
export const G_SCRIPT = { carry: [0.8, 1.6] as const, turn: [2.0, 3.0] as const, turnBy: 120, lockAt: 8.4, resumeOpen: 5 };
