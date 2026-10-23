export type CardRarity = 'common' | 'rare' | 'epic';

export type CardAudioCue = CardRarity | 'next-card';

/** The cue an arriving card plays: one per rarity. */
export function cardRevealCue(rarity: CardRarity): CardAudioCue {
  return rarity;
}

export function cardAudioCues(rarities: readonly CardRarity[]): CardAudioCue[] {
  return rarities.length ? [...new Set<CardAudioCue>(['next-card', ...rarities.map(cardRevealCue)])] : [];
}

export const NO_CARD_RARITIES: readonly CardRarity[] = [];
