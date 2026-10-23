/** Invented to-dos. `note` shows the small note glyph after the title. */
export type Task = { id: string; title: string; note: boolean };

// The source's own list; only names of real companies and products are changed (WHOOP, Zovi, Chicago Tribune).
const TITLES: [string, boolean][] = [
  ['Return Halo band', false],
  ['Set up Simplify PWA', false],
  ['Product Manager (North America)', true],
  ['Interactive SVG Animations is ready', true],
  ['Try voice cloning', true],
  ['Restaurant List', true],
  ['Pata sala', true],
  ['Kerala', true],
  ['Train', true],
  ['Tetto', true],
  ['Mockups', true],
  ['Glossy Icon Tutorial', true],
  ['Zeno Hoodie', true],
  ['Harbor Tribune Article', true],
  ['China City', true],
  ['Check out Cmd + G', false],
  ['Steddy select time', false],
  ['Renew passport', false],
  ['Plant the basil', true],
  ['Call the landlord', false],
  ['Sort the receipts', true],
  ['Book the dentist', false],
  ['Fix the bike light', false],
  ['Gift for Ines', true],
];

export const TASKS: Task[] = TITLES.map(([title, note], i) => ({ id: `t${i}`, title, note }));
