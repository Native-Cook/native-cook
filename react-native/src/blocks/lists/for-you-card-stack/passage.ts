// The passage read aloud in the player, written for this block; line breaks follow the pin's measure.
export const LINES = [
  'Colour is the most restless medium',
  'on paper. Trial, then error.',
  'Our memory for colour is poor',
  'for what is in front of us. We rarely',
  'see a colour alone. Every colour',
  'depends on what stands beside it',
].map((l) => l.split(' '));

export const PASSAGE = LINES.flat();
