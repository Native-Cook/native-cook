// Hero flash lights the digits; the slower surge expands the halo. Both share the landing clock.
import { clamp, smooth } from './growth-math';
import { type GrowthState } from './growth-state';
import { chevronPhase } from './growth-autoplay';
import {
  HERO_ENVELOPE_END_SECONDS,
  HERO_ATTACK_SECONDS,
  HERO_HOLD_END_SECONDS,
  REDUCED_SWAP_SECONDS,
  REDUCED_FLASH_SECONDS,
  HERO_FLASH_DECAY_SECONDS,
  REDUCED_SURGE_SCALE,
  HERO_SURGE_DECAY_SECONDS,
  TICK_FLASH_SECONDS,
  REDUCED_NUMBER_FADE_DEPTH,
  DT
} from './growth-physics';
export function heroEnvelope(heroAge: number, decay: number) {
  'worklet';
  if (heroAge < 0 || heroAge > HERO_ENVELOPE_END_SECONDS) return 0;
  if (heroAge < HERO_ATTACK_SECONDS) return 1 - Math.pow(1 - heroAge / HERO_ATTACK_SECONDS, 3);
  return heroAge < HERO_HOLD_END_SECONDS ? 1 : Math.exp(-(heroAge - HERO_HOLD_END_SECONDS) / decay);
}

export function visuals(wheel: GrowthState) {
  'worklet';
  const heroAge = wheel.clock - wheel.heroStart;
  const time = wheel.reduced ? 0 : wheel.auto && !wheel.paused ? wheel.loopStep * DT : wheel.clock;
  const flash = wheel.reduced
    ? 1 - smooth(0, REDUCED_FLASH_SECONDS, heroAge)
    : heroEnvelope(heroAge, HERO_FLASH_DECAY_SECONDS);
  const fadeAge = wheel.clock - wheel.fadeStart;
  const numberAlpha = wheel.reduced
    ? 1 - REDUCED_NUMBER_FADE_DEPTH * (fadeAge < REDUCED_SWAP_SECONDS
      ? smooth(0, REDUCED_SWAP_SECONDS, fadeAge)
      : 1 - smooth(REDUCED_SWAP_SECONDS, TICK_FLASH_SECONDS, fadeAge))
    : 1;
  return {
    time,
    age: heroAge,
    flash,
    surge: wheel.reduced ? flash * REDUCED_SURGE_SCALE : heroEnvelope(heroAge, HERO_SURGE_DECAY_SECONDS),
    tick: 1 - smooth(0, TICK_FLASH_SECONDS, wheel.clock - wheel.tickStart),
    lead: clamp(wheel.drums[4].theta, 0, 1),
    numberAlpha,
    phase: chevronPhase(wheel),
  };
}
