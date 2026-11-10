// Wind Chime Tones — the music and the loop. Helpers first: a worklet captures only what is declared above it.
//
// The speaker plays an 8 s piece of wind chimes (assets/sounds/wct-chimes.wav, synthesised for the
// block). NOTES lists its chimes (s into the piece, loudness 0 … 1, pitch Hz): each one kicks the
// speaker's cone and sends a ring of sound across the screen, and the cards bounce as it reaches them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

/** the piece's chimes: s into it, loudness, pitch (Hz) */
export const NOTES: [number, number, number][] = [
  [0.180, 0.58, 587],
  [0.730, 0.36, 880],
  [1.090, 0.43, 740],
  [1.820, 0.65, 659],
  [2.160, 0.32, 988],
  [2.910, 0.40, 1175],
  [3.370, 0.53, 880],
  [3.660, 0.30, 740],
  [4.420, 0.72, 587],
  [5.070, 0.39, 988],
  [5.480, 0.46, 659],
  [6.230, 0.61, 880],
  [6.890, 0.34, 1175],
  [7.570, 0.49, 740],
];
export const PIECE = 8;
/** how fast a ring of sound crosses the screen, pt/s */
export const SPEED = 420;
/** the rings the shader draws at once */
export const RINGS = 8;

// t is s since the piece started; only chimes up to cut s into it sound (the rest were paused away);
// laps 2 also hears the end of the lap before, so a ring carries across the piece's own loop point

/**
 * A card's kick, pt along the line from the speaker (out is positive), and its speed, pt/s. Each chime's
 * ring of sound reaches the card dist / SPEED s after it sounds and gives it an impulse; the card is a
 * mass on a damped spring, so its answer to each is the spring's impulse response, and the answers add.
 * A heavier card (mass > 1) moves less and swings slower: amplitude and frequency fall with √mass.
 */
export function kickAt(t: number, dist: number, cut: number, laps: number, mass = 1) {
  'worklet';
  const m = Math.sqrt(mass);
  const w = 17 / m;
  const a = 5 / m;
  let x = 0;
  let v = 0;
  for (let i = 0; i < NOTES.length; i++) {
    if (NOTES[i][0] > cut) continue;
    // this lap of the piece and the one before, so a ring carries across the loop point
    for (let lap = 0; lap < laps; lap++) {
      const tau = t - (NOTES[i][0] - lap * PIECE) - dist / SPEED;
      if (tau > 0 && tau < 1.2 * m) {
        const amp = (NOTES[i][1] * 11) / m;
        const e = Math.exp(-a * tau);
        x += amp * e * Math.sin(w * tau);
        v += amp * e * (w * Math.cos(w * tau) - a * Math.sin(w * tau));
      }
    }
  }
  return { x, v };
}
/** the cone's push, 0 … 1, from the chimes heard so far */
export function pushAt(t: number, cut: number, laps: number) {
  'worklet';
  let p = 0;
  for (let i = 0; i < NOTES.length; i++) {
    if (NOTES[i][0] > cut) continue;
    for (let lap = 0; lap < laps; lap++) {
      const tau = t - (NOTES[i][0] - lap * PIECE);
      if (tau > 0 && tau < 1.5) p += NOTES[i][1] * Math.exp(-6 * tau) * Math.cos(28 * tau);
    }
  }
  return p;
}
/** the RINGS most recent chimes as (age s, loudness) pairs, for the shader; no chime: age −1 */
export function ringsAt(t: number, cut: number, laps: number) {
  'worklet';
  const out: number[] = [];
  if (t >= 0) {
    for (let lap = 0; lap < laps && out.length < RINGS * 2; lap++) {
      for (let i = NOTES.length - 1; i >= 0 && out.length < RINGS * 2; i--) {
        if (NOTES[i][0] > cut) continue;
        const age = t - (NOTES[i][0] - lap * PIECE);
        if (age >= 0 && age < 2.2) out.push(age, NOTES[i][1]);
      }
    }
  }
  while (out.length < RINGS * 2) out.push(-1, 0);
  return out;
}

export const LOOP = 10;

/* ---------------------------------------------------------------- the loop (10 s)
 0.7  a finger taps play: the piece starts at 0.8 and the speaker sings
 8.6  a tap on pause: the last rings run out, the speaker settles      10 = 0 */
export const T = { play: 0.7, start: 0.8, pause: 8.6 } as const;

export type Frame = {
  playing: number; // 1 while the piece plays
  music: number; // s since the piece started (−1 before it starts)
  cut: number; // the last chime that sounds, s into the piece
  finger: number;
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const playing = t >= T.play && t < T.pause ? 1 : 0;
  // after the pause only the rings already on their way run on
  const music = t >= T.start ? t - T.start : -1;
  const cut = T.pause - T.start;
  const tap = (at: number) => easeOut(seg(t, at - 0.18, at - 0.03)) * (1 - easeOut(seg(t, at + 0.08, at + 0.24)));
  const finger = Math.max(tap(T.play), tap(T.pause));
  return { playing, music, cut, finger, press: finger };
}
