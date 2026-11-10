// Rain Window Forecast — the shaders' uniforms from the block's state.

import { type Weather } from './rwf-motion';
import { HOURS } from './rwf-theme';

/** the places of the icons on this device, worked out once from the layout */
export type Geo = { w: number; h: number; hero: [number, number, number]; cards: [number, number, number][]; tab: [number, number, number, number] };

export function makeUniforms(g: Geo, s: { weather: Weather; t: number; loop: number; scroll: number }, mode: 0 | 1) {
  'worklet';
  const icons: number[] = [];
  for (let i = 0; i < 4; i++) {
    const c = g.cards[i];
    const H = HOURS[i];
    icons.push(c[0] - s.scroll, c[1], c[2], H.sun, H.moon, H.rain);
  }
  return {
    uRes: [g.w, g.h],
    uIcons: icons,
    uHero: [g.hero[0], g.hero[1], g.hero[2], s.weather.sun, s.weather.moon, s.weather.rain],
    uT: s.t,
    uLoop: s.loop,
    uGlow: s.weather.glow,
    uMode: mode,
    uTab: g.tab,
  };
}

export function makeWetUniforms(g: Geo, s: { t: number; loop: number; wet: number; wipe: number[] }) {
  'worklet';
  return { uRes: [g.w, g.h], uT: s.t, uLoop: s.loop, uWet: s.wet, uWipe: s.wipe, uWipeR: 44 };
}
