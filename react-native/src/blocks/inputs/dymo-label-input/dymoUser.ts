import { mixRGB } from './dymoArt';
import { type Pose, STEP, TAPES, WHEEL, easeInOut, easeOut, seg, squash } from './dymoMotion';

/* Dymo Label Input — the user's tape on the UI thread: what is on it, when each letter was struck, the cut strip. */

export type UserTape = {
  text: string;
  e: number[];
  colour: number;
  colourFrom: number;
  colourAt: number;
  strip: string;
  stripColour: number;
  cutAt: number;
};

export const FRESH: UserTape = { text: '', e: [], colour: 0, colourFrom: 0, colourAt: -100, strip: '', stripColour: 0, cutAt: -100 };

export function userPose(u: UserTape, now: number, wheel: number, reduced: boolean): Pose {
  'worklet';
  let trigger = 0;
  let advance = 0;
  const whiten: number[] = [];
  const pop: number[] = [];
  for (let i = 0; i < u.e.length; i += 1) {
    const e = u.e[i];
    trigger = Math.max(trigger, easeOut(seg(now, e - 0.08, e)) * (1 - easeInOut(seg(now, e, e + 0.2))));
    whiten.push(reduced ? 1 : easeOut(seg(now, e, e + 0.12)));
    pop.push(reduced ? 0 : Math.sin(Math.PI * seg(now, e + 0.02, e + 0.26)));
    advance += reduced ? 1 : easeInOut(seg(now, e + 0.22, e + 0.42));
  }
  const c = u.cutAt;
  const live = c > 0 && now < c + 3.6;
  const tapeRGB = mixRGB(TAPES[u.colourFrom].rgb, TAPES[u.colour].rgb, seg(now, u.colourAt, u.colourAt + 0.25));
  return {
    wheel,
    trigger,
    tape: u.text,
    whiten,
    pop,
    advance,
    lead: u.text.length > 0 || c < 0 ? 18 : 4 + 14 * easeInOut(seg(now, c + 0.6, c + 1.3)),
    strip: live ? u.strip : '',
    fly: reduced ? 1 : easeInOut(seg(now, c + 0.25, c + 1.25)),
    squash: reduced ? 0 : squash(seg(now, c + 1.2, c + 1.7)),
    cutter: easeOut(seg(now, c, c + 0.1)) * (1 - easeInOut(seg(now, c + 0.1, c + 0.3))),
    slide: live ? easeInOut(seg(now, c + 2.6, c + 3.6)) : 0,
    tapeRGB,
    stripRGB: TAPES[u.stripColour].rgb,
  };
}

/** The wheel angle (deg) nearest `from` that puts `slot` under the head. */
export function angleFor(slot: number, from: number) {
  'worklet';
  const base = -slot * STEP;
  const k = Math.round((from - base) / 360);
  return base + k * 360;
}

export function slotAt(angle: number) {
  'worklet';
  const n = WHEEL.length;
  return ((Math.round(-angle / STEP) % n) + n) % n;
}
