/**
 * An invented podcast, Second Draft: writers on the book they almost quit. Newest first. Lengths are chosen so the
 * demo tells its story: #48 to #44 are 283 min, 42 short of the 325 min flight, and #43 makes it 330 (5 to spare).
 */
export type Episode = { id: string; number: number; title: string; date: string; minutes: number; guest: string };

const LIST: [number, string, string, number, string][] = [
  [48, 'The chapter that wouldn’t end', 'Oct 17', 64, 'Mara Okafor'],
  [47, 'Writing in a second language', 'Oct 10', 52, 'Tomasz Wilk'],
  [46, 'Notebooks, not outlines', 'Oct 3', 71, 'Inés Calderón'],
  [45, 'The red pen, live in Brooklyn', 'Sep 26', 38, 'Live'],
  [44, 'Starting over at sixty', 'Sep 19', 58, 'Ruth Abernathy'],
  [43, 'How to translate a joke', 'Sep 12', 47, 'Kenji Sato'],
  [42, 'Q&A: when to quit a draft', 'Sep 5', 41, 'Listener questions'],
  [41, 'Down the research hole', 'Aug 29', 66, 'Priya Natarajan'],
  [40, 'Short stories as sketches', 'Aug 22', 44, 'Leo Brandt'],
  [39, 'Writing the city you left', 'Aug 15', 55, 'Amara Diallo'],
  [38, 'Endings', 'Aug 8', 49, 'Sofia Marchetti'],
  [37, 'What the first reader said', 'Aug 1', 53, 'Jonah Reyes'],
];

export const EPISODES: Episode[] = LIST.map(([number, title, date, minutes, guest]) => ({
  id: `e${number}`, number, title, date, minutes, guest,
}));
export const MINUTES = EPISODES.map((e) => e.minutes);

export const FLIGHT = { from: 'SFO', to: 'JFK', minutes: 325 } as const;
/** Megabytes per minute of audio at 128 kbit/s. */
const MB_PER_MIN = 0.94;

export function formatMinutes(min: number): string {
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m === 0 ? `${h} hr` : `${h} hr ${m} min`;
}

export function formatSize(min: number): string {
  const mb = Math.round(min * MB_PER_MIN);
  return mb >= 1000 ? `${(mb / 1000).toFixed(1)} GB` : `${mb} MB`;
}

export function spokenMinutes(min: number): string {
  const h = Math.floor(min / 60);
  const m = min % 60;
  const hs = h === 0 ? '' : `${h} hour${h === 1 ? '' : 's'}`;
  const ms = m === 0 ? '' : `${m} minute${m === 1 ? '' : 's'}`;
  return [hs, ms].filter(Boolean).join(' ') || '0 minutes';
}
