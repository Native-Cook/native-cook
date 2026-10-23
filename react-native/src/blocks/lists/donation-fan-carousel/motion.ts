export const LOOP_MS = 5366;
export const RESUME_MS = 3500;

export const cue = {
  firstCoinIn: 700,
  firstCoinOut: 1480,
  advanceStart: 980,
  advanceEnd: 1680,
  heartsIn: 1880,
  heartsOut: 3150,
  secondCoinIn: 3380,
  secondCoinOut: 4320,
  returnStart: 3670,
  returnEnd: 4460,
};

function clamp01(value: number) {
  'worklet';
  return value < 0 ? 0 : value > 1 ? 1 : value;
}

function smoother(value: number) {
  'worklet';
  const x = clamp01(value);
  return x * x * x * (x * (x * 6 - 15) + 10);
}

function window(t: number, start: number, end: number, edge = 130) {
  'worklet';
  return smoother((t - start) / edge) * (1 - smoother((t - (end - edge)) / edge));
}

export function pageAt(input: number) {
  'worklet';
  const t = ((input % LOOP_MS) + LOOP_MS) % LOOP_MS;
  if (t < cue.advanceStart) return 0;
  if (t < cue.advanceEnd) return 3 * smoother((t - cue.advanceStart) / (cue.advanceEnd - cue.advanceStart));
  if (t < cue.returnStart) return 3;
  if (t < cue.returnEnd) return 3 * (1 - smoother((t - cue.returnStart) / (cue.returnEnd - cue.returnStart)));
  return 0;
}

export function coinAt(input: number) {
  'worklet';
  const t = ((input % LOOP_MS) + LOOP_MS) % LOOP_MS;
  return Math.max(
    window(t, cue.firstCoinIn, cue.firstCoinOut, 100),
    window(t, cue.secondCoinIn, cue.secondCoinOut, 110),
  );
}

export function heartsAt(input: number) {
  'worklet';
  const t = ((input % LOOP_MS) + LOOP_MS) % LOOP_MS;
  return window(t, cue.heartsIn, cue.heartsOut, 180);
}

export function borderAt(input: number) {
  'worklet';
  const t = ((input % LOOP_MS) + LOOP_MS) % LOOP_MS;
  const advancing = t >= cue.advanceStart && t <= cue.advanceEnd;
  const returning = t >= cue.returnStart && t <= cue.returnEnd;
  return advancing || returning ? 0 : 1;
}

export const snap = { duration: 430, dampingRatio: 0.86 };
export const projectSeconds = 0.14;
export const dragPointsPerPage = 112;
