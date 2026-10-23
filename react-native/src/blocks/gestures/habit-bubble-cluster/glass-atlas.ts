/**
 * The glass sprites in `assets/glass-atlas.jpg`: one per habit, sampled from the source's own bubbles (12 sectors ×
 * 30 rings, the icon and the neighbours' glow taken out), on black so a 'lighten' blend adds only light.
 * Each tile is `size` px square with the bubble's radius at `size / 2 / reach`.
 */
export const ATLAS = { size: 360, cols: 5, reach: 1.45 } as const;

const ORDER = ['wave', 'star', 'gift', 'ball', 'friend', 'art', 'host', 'magic', 'home', 'tea', 'heart', 'wallet', 'ride', 'note', 'journal'];

// The mail bubble overlaps the star in the source, so it borrows the clean glass of the Personal bubble.
const ALIAS: Record<string, string> = { note: 'journal', quiet: 'journal' };

export function spriteIndex(id: string): number {
  const i = ORDER.indexOf(ALIAS[id] ?? id);
  return i < 0 ? ORDER.indexOf('journal') : i;
}
