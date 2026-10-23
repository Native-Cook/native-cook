import type { CardAudioCue } from './card-rarity';

// Bundled, pre-trimmed PCM effects. Only cues present in this pack are preloaded.
export const CARD_AUDIO_ASSETS: Record<CardAudioCue, number> = {
  'next-card': require('./assets/booster-card-next.wav'),
  common: require('./assets/booster-reveal-common.wav'),
  rare: require('./assets/booster-reveal-rare.wav'),
  epic: require('./assets/booster-reveal-epic.wav'),
};
