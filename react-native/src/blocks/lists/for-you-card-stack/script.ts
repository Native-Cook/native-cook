import { Easing, interpolate } from 'react-native-reanimated';

import { EASE_PIN, EASE_SHEET, LOOP_MS, POP_PROGRESS, POP_TIMES, type Span, T } from './motion';
import { PLAYER, READING } from './theme';

export type Scene = {
  /** 0 = the first cover in front of the deck, 1 = the next one pulled out. */
  deck: number;
  /** 0 = For you, 1 = player pushed over it. */
  push: number;
  /** Play button: 0 = play glyph, 1 = pause glyph. */
  playing: number;
  /** Reading opens (0..1) and then closes (0..1); see `look()`. */
  open: number;
  close: number;
  /** Lines of the passage read so far (fractional). */
  words: number;
};

const pin = Easing.bezierFn(...EASE_PIN);
const sheet = Easing.bezierFn(...EASE_SHEET);

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, s: Span) {
  'worklet';
  return clamp01((t - s[0]) / (s[1] - s[0]));
}

/** The autoplay as a pure function of the loop clock (the pin's own timeline). */
export function sampleScene(tIn: number): Scene {
  'worklet';
  const t = ((tIn % LOOP_MS) + LOOP_MS) % LOOP_MS;
  const deck = pin(seg(t, T.deck)) * (1 - pin(seg(t, T.deckBack)));
  const pop = interpolate(t, POP_TIMES, POP_PROGRESS, 'clamp');
  const push = sheet(seg(t, T.push)) * (1 - pop);
  const playing = t >= T.playTap && t < T.pauseTap ? 1 : 0;
  let open = seg(t, T.open);
  let close = seg(t, T.close);
  if (close >= 1) {
    open = 0;
    close = 0;
  }
  const words = t < T.pauseTap ? interpolate(t, T.readT, T.readLines, 'clamp') : T.readLines[T.readLines.length - 1];
  return { deck, push, playing, open, close, words };
}

/** Sub-phases as fractions of the open (1500 ms) and scripted close (1875 ms), stepped from the pin. */
export const OPEN_PHASES = {
  neighbours: [0, 0.133] as Span,
  chapter: [0.033, 0.167] as Span,
  shrink: [0, 0.333] as Span,
  wave: [0.067, 0.4] as Span,
  title: [0.067, 0.333] as Span,
  leaf0: [0.333, 0.5] as Span,
  leaf1: [0.6, 0.8] as Span,
  leaf2: [0.833, 1] as Span,
  text: [0.333, 0.5] as Span,
};
export const CLOSE_PHASES = {
  text: [0, 0.125] as Span,
  wave: [0, 0.1875] as Span,
  grow: [0.0625, 0.594] as Span,
  leaf2: [0.094, 0.25] as Span,
  leaf1: [0.3125, 0.5] as Span,
  leaf0: [0.625, 0.9375] as Span,
  title: [0.406, 0.656] as Span,
  neighbours: [0.3125, 0.47] as Span,
  chapter: [0.406, 0.656] as Span,
};

export type Look = {
  neighbours: number;
  chapter: number;
  wave: number;
  title: number;
  text: number;
  /** Book: scale against full size, spine x, centre y, and the three leaves' flip (0 = on the right, 1 = turned). */
  scale: number;
  spineX: number;
  centerY: number;
  leaf0: number;
  leaf1: number;
  leaf2: number;
};

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Visual amounts from the open and close progress. */
export function look(open: number, close: number): Look {
  'worklet';
  const o = (s: Span) => pin(seg(open, s));
  const c = (s: Span) => pin(seg(close, s));
  const B = READING.book;
  if (close > 0) {
    const grow = c(CLOSE_PHASES.grow);
    const shut = c(CLOSE_PHASES.leaf0);
    return {
      neighbours: c(CLOSE_PHASES.neighbours),
      chapter: c(CLOSE_PHASES.chapter),
      wave: 1 - c(CLOSE_PHASES.wave),
      title: 1 - c(CLOSE_PHASES.title),
      text: 1 - c(CLOSE_PHASES.text),
      scale: mix(B.scale, 1, grow),
      spineX: mix(READING.spreadSpineX, PLAYER.spineX, shut),
      centerY: mix(B.centerY, PLAYER.centerY, grow),
      leaf0: 1 - shut,
      leaf1: 1 - c(CLOSE_PHASES.leaf1),
      leaf2: 1 - c(CLOSE_PHASES.leaf2),
    };
  }
  const shrink = o(OPEN_PHASES.shrink);
  return {
    neighbours: 1 - o(OPEN_PHASES.neighbours),
    chapter: 1 - o(OPEN_PHASES.chapter),
    wave: o(OPEN_PHASES.wave),
    title: o(OPEN_PHASES.title),
    text: o(OPEN_PHASES.text),
    scale: mix(1, B.scale, shrink),
    spineX: mix(PLAYER.spineX, B.spineX, shrink),
    centerY: mix(PLAYER.centerY, B.centerY, shrink),
    leaf0: o(OPEN_PHASES.leaf0),
    leaf1: o(OPEN_PHASES.leaf1),
    leaf2: o(OPEN_PHASES.leaf2),
  };
}
