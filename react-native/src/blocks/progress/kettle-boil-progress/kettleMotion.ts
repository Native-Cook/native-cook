// Kettle Boil Progress — the loop's script and the hand-driven kettle, as pure worklets.
//
// LOOP = 9 s:
//   0.0 – 1.4  rest: still water at 21 °C, the ring dark, "Ready".
//   1.3 – 1.45 the switch is pressed down; the ring lights (0.2 s ease-out).
//   1.4 – 5.2  heat: 21 → 80 °C (ease-in-out), the ring fills with it; micro bubbles from ~30 °C,
//              rising streams from ~50 °C, the surface rolls near the end, steam starts at 4.6.
//   5.2        click: the switch springs back up, the ring flashes and dims (0.6 s).
//   5.2 – 6.8  steam pours from the spout and the lid; the boil settles (bubbles die by 6.8).
//   6.8 – 8.4  cool reset: the steam fades, the temperature rolls back to 21, the ring empties.
//   8.4 – 9.0  rest; the frame at 9 s is the frame at 0 s.
// The status line and the button roll through four states (Ready, Heating, Ready to steep,
// Cooling) in one direction: the index only grows, and 4 shows the same text as 0.

export const LOOP = 9;
export const T_COLD = 21;
export const T_TARGET = 80;

export type KettleState = {
  temp: number;
  heat: number;
  ringFill: number;
  ringOn: number;
  steam: number;
  lever: number;
  /** 0 Ready, 1 Heating, 2 Ready to steep, 3 Cooling. */
  status: number;
};

export type Layout = { ox: number; oy: number; k: number; counter: number };

export function makeLayout(width: number, height: number, insetTop: number, insetBottom: number): Layout {
  const counter = height - insetBottom - 196;
  const k = Math.max(0.6, Math.min(1.12, (width - 40) / 330, (counter - insetTop - 200) / 300));
  return { ox: width / 2 + 8 * k, oy: counter - 52 * k, k, counter };
}

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return 1 - Math.pow(1 - c, 3);
}

/** How hard the water works at a temperature while the element is on (0 still … 1 rolling boil). */
export function activityAt(temp: number) {
  'worklet';
  return Math.pow(clamp01((temp - 28) / 50), 1.15);
}

export function makeState(): KettleState {
  'worklet';
  return { temp: T_COLD, heat: 0, ringFill: 0, ringOn: 0, steam: 0, lever: 0, status: 0 };
}

/** The kettle at loop time t, written into s. */
export function loopState(t: number, s: KettleState) {
  'worklet';
  const heating = easeInOut(seg(t, 1.4, 5.2));
  const cooling = easeInOut(seg(t, 6.8, 8.4));
  s.temp = T_COLD + (T_TARGET - T_COLD) * heating * (1 - cooling);
  const fill = (s.temp - T_COLD) / (T_TARGET - T_COLD);
  s.ringFill = fill;
  // On from the press, a flash at the click, then dim.
  const on = easeOut(seg(t, 1.3, 1.5));
  const click = seg(t, 5.2, 5.8);
  s.ringOn = t < 5.2 ? on : 1.35 * Math.exp(-click * 4) * (1 - click) + 0.0;
  // The boil: follows the temperature while heating, then settles once the element is off.
  const settle = t < 5.2 ? 1 : 1 - easeOut(seg(t, 5.2, 6.8));
  s.heat = t < 5.2 ? activityAt(s.temp) : activityAt(T_TARGET) * settle;
  const steamIn = easeOut(seg(t, 4.6, 5.6));
  s.steam = steamIn * (0.4 + 0.6 * easeOut(seg(t, 5.2, 5.6))) * (1 - easeInOut(seg(t, 6.8, 8.0)));
  // The switch: pressed at 1.3 (0.15 s), back up at the click with a little overshoot.
  const up = seg(t, 5.2, 5.5);
  s.lever = t < 5.2 ? easeOut(seg(t, 1.3, 1.45)) : Math.max(0, 1 - up) - 0.12 * Math.sin(Math.PI * up) * (up < 1 ? 1 : 0);
  s.status = t < 1.4 ? 0 : t < 5.2 ? 1 : t < 6.8 ? 2 : t < 8.4 ? 3 : 4;
}

/** The temperature shown: whole degrees, each clicking in over 0.35 of a degree's change. */
export function shownTemp(temp: number) {
  'worklet';
  const whole = Math.floor(temp);
  return whole + easeOut((temp - whole) / 0.35);
}

/** One step of the kettle under a hand: heats while on and clicks off at 80, cools when off. */
export function stepUser(s: KettleState, on: number, dt: number) {
  'worklet';
  if (on) s.temp = Math.min(T_TARGET, s.temp + (12 + 4 * (1 - (s.temp - T_COLD) / (T_TARGET - T_COLD))) * dt);
  else s.temp += -(s.temp - T_COLD) * 0.07 * dt;
  s.ringFill = clamp01((s.temp - T_COLD) / (T_TARGET - T_COLD));
  const act = on ? activityAt(s.temp) : 0;
  s.heat += (act - s.heat) * Math.min(1, dt * (on ? 2.5 : 0.9));
  const steamTo = s.temp > 74 ? clamp01((s.temp - 74) / 6) * (on ? 0.4 : 1) : 0;
  s.steam += (steamTo - s.steam) * Math.min(1, dt * 1.6);
  s.ringOn += ((on ? 1 : 0) - s.ringOn) * Math.min(1, dt * (on ? 8 : 3.5));
  s.lever += ((on ? 1 : 0) - s.lever) * Math.min(1, dt * 18);
}

/** s = a + (b - a) · e, field by field. */
export function blendState(a: KettleState, b: KettleState, e: number, s: KettleState) {
  'worklet';
  s.temp = a.temp + (b.temp - a.temp) * e;
  s.heat = a.heat + (b.heat - a.heat) * e;
  s.ringFill = a.ringFill + (b.ringFill - a.ringFill) * e;
  s.ringOn = a.ringOn + (b.ringOn - a.ringOn) * e;
  s.steam = a.steam + (b.steam - a.steam) * e;
  s.lever = a.lever + (b.lever - a.lever) * e;
}

export function copyState(a: KettleState): KettleState {
  'worklet';
  return { temp: a.temp, heat: a.heat, ringFill: a.ringFill, ringOn: a.ringOn, steam: a.steam, lever: a.lever, status: a.status };
}

/** The next status index at or after `from` whose state (index mod 4) is `state`: labels only roll forward. */
export function nextStatus(from: number, state: number) {
  'worklet';
  const base = Math.floor(from + 1e-3);
  for (let i = 0; i < 4; i += 1) if ((((base + i) % 4) + 4) % 4 === state) return base + i;
  return base;
}
