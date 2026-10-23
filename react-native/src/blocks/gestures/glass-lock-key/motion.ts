// Every motion value of the block. Seconds unless the name ends in _MS; points unless noted.

export const STEP = 1 / 120;
export const MAX_STEPS = 30; // per frame: the sim keeps wall-clock time through dropped frames, so the loop keeps its length

export const CARRY = {
  lift: 1.04, // key scale while held
  magnet: 72, // tip to keyhole distance where the keyhole starts pulling
  magnetPull: 0.55, // share of the remaining gap closed at contact range
  seat: 12, // tip within this of the keyhole: the key goes in
  home: { stiffness: 220, damping: 22 },
  bob: 3, // idle float, points
  bobPeriod: 2.5, // seconds: divides the 10 s loop
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

// Autoplay: 10 s. Finger offsets in points; the carry is relative to the key's tip at rest, the turn to the bow.
// Carry the key up into the keyhole (0.8–1.6 s), turn it through three detents (1.95–3.0 s), the shackle springs and
// the vault rises; Lock vault at 6.6 s closes it in order (sheet, shackle, key back, key out) by about 8.4 s; rest.
export const LOOP_MS = 10_000;
export const RESUME_MS = 2_000;
export const SCRIPT = {
  carry: [0.8, 1.6] as const, // drag the key up into the keyhole
  carryTo: [0, -100] as const,
  turn: [1.95, 3.0] as const, // drag right across the bow
  turnBy: 100,
  lockAt: 6.6, // taps Lock vault
  resumeOpen: 6.2, // a touch left the vault open: the demo picks up just before it locks
};
