export const LOOP = 10;
export const MOODS = [
  { name: "MELANCHOLY", color: 0x6A49D4, smile: -1, tired: 0, radii: [0.7275, 0.7732, 0.8395, 0.932, 1.0414, 1.1443, 1.2136, 1.237, 1.2127, 1.1555, 1.0875, 1.0259, 0.978, 0.943, 0.9195, 0.9059, 0.9018, 0.9059, 0.9195, 0.943, 0.978, 1.0259, 1.0907, 1.1625, 1.2295, 1.2543, 1.2345, 1.1636, 1.0566, 0.9511, 0.8611, 0.7968, 0.7491, 0.7182, 0.6948, 0.6855, 0.6818, 0.6818, 0.6818, 0.6827, 0.6868, 0.6995, 0.7236, 0.7643, 0.817, 0.883, 0.9398, 0.9732, 0.978, 0.9566, 0.9155, 0.8548, 0.7995, 0.7491, 0.7116, 0.6909, 0.6832, 0.6818, 0.6818, 0.6818, 0.6818, 0.6832, 0.6877, 0.7018] },
  { name: "TIRED", color: 0x6D9AE8, smile: -0.75, tired: 1, radii: [0.938, 0.9261, 0.9166, 0.9114, 0.91, 0.92, 0.9377, 0.9586, 0.9795, 0.9959, 0.9968, 0.9898, 0.9759, 0.9591, 0.9411, 0.9284, 0.9236, 0.9284, 0.9411, 0.9591, 0.9727, 0.9805, 0.975, 0.9714, 0.9555, 0.9425, 0.9209, 0.9039, 0.8959, 0.8923, 0.8959, 0.9034, 0.9155, 0.9241, 0.9305, 0.9273, 0.915, 0.8961, 0.8793, 0.8645, 0.8514, 0.8418, 0.8359, 0.8407, 0.8477, 0.8725, 0.8861, 0.8966, 0.8973, 0.8966, 0.8911, 0.8825, 0.8682, 0.852, 0.8405, 0.8382, 0.84, 0.8609, 0.8832, 0.9127, 0.937, 0.952, 0.955, 0.948] },
  { name: "BORED", color: 0x63CCEA, smile: -0.4, tired: 0, radii: [0.9009, 0.9055, 0.9168, 0.9302, 0.9405, 0.9448, 0.947, 0.9375, 0.9195, 0.8995, 0.8873, 0.8798, 0.8891, 0.8986, 0.9168, 0.9341, 0.9414, 0.9398, 0.9257, 0.9055, 0.8864, 0.8795, 0.8757, 0.8873, 0.9086, 0.9293, 0.9505, 0.9584, 0.9582, 0.9448, 0.9355, 0.9264, 0.9223, 0.9218, 0.9264, 0.9423, 0.9609, 0.9714, 0.9673, 0.9548, 0.9409, 0.9245, 0.9191, 0.918, 0.9261, 0.9393, 0.9568, 0.9711, 0.9805, 0.978, 0.9614, 0.9416, 0.9257, 0.9184, 0.92, 0.9291, 0.9432, 0.9561, 0.9673, 0.9714, 0.9609, 0.94, 0.9193, 0.9055] },
  { name: "CALM", color: 0xECE2D9, smile: 0, tired: 0, radii: [0.9691, 0.9643, 0.957, 0.9468, 0.9452, 0.9455, 0.9548, 0.9589, 0.9636, 0.9552, 0.9475, 0.9418, 0.9434, 0.9468, 0.9568, 0.9639, 0.9659, 0.9639, 0.9568, 0.9468, 0.9452, 0.9455, 0.9548, 0.9589, 0.9664, 0.9634, 0.9664, 0.9657, 0.9689, 0.9691, 0.9757, 0.9777, 0.9841, 0.9868, 0.9848, 0.9825, 0.9802, 0.9845, 0.9905, 0.995, 1.0036, 1.0052, 1.0, 0.992, 0.9857, 0.988, 0.9923, 0.9995, 0.9945, 0.9918, 0.985, 0.9825, 0.9802, 0.9845, 0.9905, 0.992, 0.9973, 0.99, 0.9864, 0.9741, 0.9716, 0.9691, 0.9736, 0.9714] },
  { name: "HAPPY", color: 0x71DD6B, smile: 0.55, tired: 0, radii: [0.9525, 0.9241, 0.898, 0.8805, 0.8836, 0.9011, 0.9307, 0.9564, 0.9795, 0.9984, 1.0034, 1.0052, 0.9918, 0.9739, 0.9452, 0.9266, 0.9155, 0.9266, 0.9452, 0.9739, 0.9943, 1.0102, 1.0166, 1.0127, 1.0034, 0.9848, 0.9627, 0.937, 0.915, 0.9109, 0.9234, 0.9484, 0.9725, 0.9845, 0.9873, 0.9855, 0.9807, 0.9616, 0.9364, 0.9059, 0.8827, 0.8657, 0.8602, 0.8761, 0.9075, 0.9341, 0.9568, 0.9689, 0.9734, 0.9639, 0.9468, 0.9291, 0.9073, 0.8782, 0.8643, 0.8618, 0.8809, 0.9052, 0.928, 0.9536, 0.9684, 0.9782, 0.9782, 0.9689] },
  { name: "SUPER HAPPY", color: 0xFAC576, smile: 0.9, tired: 0, radii: [0.7948, 0.808, 0.8461, 0.8861, 0.9223, 0.9327, 0.9259, 0.8943, 0.8495, 0.802, 0.7802, 0.773, 0.7973, 0.8291, 0.8718, 0.9066, 0.9227, 0.9123, 0.8832, 0.8423, 0.8036, 0.7759, 0.7739, 0.7989, 0.845, 0.8898, 0.9143, 0.9232, 0.91, 0.8811, 0.8436, 0.808, 0.7948, 0.803, 0.8361, 0.8839, 0.927, 0.9486, 0.9445, 0.9184, 0.8814, 0.8427, 0.8141, 0.8023, 0.8168, 0.8545, 0.9041, 0.9425, 0.9518, 0.9357, 0.8995, 0.8523, 0.8159, 0.8005, 0.8105, 0.8432, 0.885, 0.9275, 0.9491, 0.9509, 0.927, 0.8839, 0.8361, 0.803] },
  { name: "AWESOME", color: 0xF38B9D, smile: 1, tired: 0, radii: [0.9441, 0.9236, 0.8816, 0.8457, 0.8391, 0.8586, 0.8993, 0.9275, 0.9336, 0.9127, 0.8766, 0.8364, 0.8223, 0.8361, 0.872, 0.9066, 0.9227, 0.9102, 0.8816, 0.8468, 0.8382, 0.8509, 0.8839, 0.9152, 0.9336, 0.93, 0.9043, 0.8707, 0.85, 0.86, 0.893, 0.9316, 0.9527, 0.95, 0.9232, 0.8864, 0.8695, 0.8789, 0.9114, 0.945, 0.965, 0.9593, 0.9282, 0.8873, 0.8723, 0.8864, 0.9255, 0.9568, 0.9686, 0.9523, 0.9164, 0.8818, 0.8677, 0.8827, 0.9191, 0.9548, 0.96, 0.9377, 0.8968, 0.8661, 0.8609, 0.8777, 0.9095, 0.9364] }
];
export type Shown = { chooser: number; panel: number; m: number; clock: number; closing: boolean; origin: number };
export type Anim = { from: number; to: number; start: number; duration: number; curve: number };
export function clamp(m: number) { 'worklet'; return Math.min(6, Math.max(0, m)); }
export function nearest(m: number) { 'worklet'; return Math.floor(clamp(m) + 0.5); }
export function segment(t: number, a: number, b: number) { 'worklet'; return Math.min(1, Math.max(0, (t - a) / (b - a))); }
export function ease(p: number) { 'worklet'; return p * p * (3 - 2 * p); }
export function mix(a: number, b: number, p: number) { 'worklet'; return a + (b - a) * p; }
export function itemWidth(mood: number) { 'worklet'; return mix(200, 256, ease(segment(mood, 4, 5))); }
export function menuTrack(progress: number, first: number, middle: number, last: number) {
  'worklet'; return progress <= 0.355 ? mix(first, middle, segment(progress, 0, 0.355)) : mix(middle, last, segment(progress, 0.355, 1));
}
export function normalized(t: number) { 'worklet'; return ((t % LOOP) + LOOP) % LOOP; }
export function blend(m: number) {
  'worklet'; const value = clamp(m), i = Math.floor(value); return { from: MOODS[i], to: MOODS[Math.min(i + 1, 6)], f: ease(value - i) };
}
export function radius(radii: number[], theta: number) {
  'worklet'; const p = theta / (2 * Math.PI) * radii.length, i = Math.floor(p) % radii.length;
  return mix(radii[i], radii[(i + 1) % radii.length], p - Math.floor(p));
}
export function title(m: number, step: number, ghost = false, still = false) {
  'worklet'; const name = MOODS[nearest(m)].name, d = Math.abs(m - Math.floor(m + 0.5)), reveal = 1 - ease(segment(d, 0.02, 0.42));
  if (still || reveal >= 1) return ghost ? '' : name;
  let text = '';
  for (let i = 0; i < name.length; i++) {
    if (name[i] === ' ' || (!ghost && (i + 1) / name.length <= reveal)) { text += name[i]; continue; }
    const hash = Math.abs(Math.sin(i * 12.9898 + (step + (ghost ? 7 : 0)) * 78.233) * 43758.5453);
    text += 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'[Math.floor((hash % 1) * 26) % 26];
  }
  return text;
}
export const MOOD_KEYS = [[0,5],[1.12,5],[1.88,0],[2.50,0],[3.02,2],[3.38,3],[3.64,4],[3.90,5],[4.12,6],[4.55,6],[4.88,5],[5.18,5],[5.40,4],[6.15,4],[6.65,2],[6.90,2],[7.25,3],[7.60,3],[7.88,4],[8.42,4],[8.70,3],[9.30,3],[9.50,4],[17.2,4]];
export function moodAt(t: number) {
  'worklet'; for (let i = 1; i < MOOD_KEYS.length; i++) if (t <= MOOD_KEYS[i][0]) {
    const a = MOOD_KEYS[i - 1], b = MOOD_KEYS[i]; return mix(a[1], b[1], ease(segment(t, a[0], b[0])));
  }
  return 4;
}
export const EXPANSION_KEYS = [[0.20,0],[0.233,0.015],[0.267,0.155],[0.300,0.355],[0.333,0.51],[0.367,0.64],[0.400,0.73],[0.433,0.82],[0.467,0.865],[0.500,0.915],[0.533,0.955],[0.567,0.98],[0.600,0.99],[0.633,1]];
export function expansion(source: number) {
  'worklet'; for (let i = 1; i < EXPANSION_KEYS.length; i++) if (source <= EXPANSION_KEYS[i][0]) {
    const a = EXPANSION_KEYS[i - 1], b = EXPANSION_KEYS[i]; return mix(a[1], b[1], segment(source, a[0], b[0]));
  }
  return 1;
}
export function scripted(time: number): Shown {
  'worklet'; const t = normalized(time);
  if (t >= 8.8) { const p = ease(segment(t, 8.8, 9.45)); return { chooser: p, panel: 0, m: mix(4, 5, p), clock: t, closing: false, origin: 0 }; }
  const source = t * 1.4, exit = ease(segment(source, 9.58, 9.95));
  return { chooser: 1 - ease(segment(source, 10.08, 10.40)), panel: expansion(source) * (1 - exit), m: moodAt(source), clock: t, closing: source >= 9.58 && source < 9.95, origin: 0 };
}
export function still(value: number): Anim { 'worklet'; return { from: value, to: value, start: 0, duration: 0, curve: 0 }; }
export function animation(from: number, to: number, start: number, duration: number, curve = 0): Anim { 'worklet'; return { from, to, start, duration, curve }; }
export function at(a: Anim, now: number) {
  'worklet'; if (a.duration <= 0) return a.to;
  const p = segment(now - a.start, 0, a.duration), e = a.curve === 1 ? 1 - Math.pow(1 - p, 3) : a.curve === 2 ? Math.pow(p, 3) : ease(p);
  return mix(a.from, a.to, e);
}
