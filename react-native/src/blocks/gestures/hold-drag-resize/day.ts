/**
 * Thursday's plan. Meetings are other people's time and never move; tasks and focus blocks are yours, so a block
 * that grows into them shoves them later, and they go back to their own time when it shrinks again.
 * Times are minutes since midnight, in order.
 */
export const KIND = { meeting: 0, task: 1, focus: 2 } as const;
export type Kind = (typeof KIND)[keyof typeof KIND];

export type PlanItem = { id: string; kind: Kind; start: number; end: number; title: string; note: string };

const h = (hours: number, minutes = 0) => hours * 60 + minutes;

export const PLAN: PlanItem[] = [
  { id: 'standup', kind: KIND.meeting, start: h(9), end: h(9, 30), title: 'Standup', note: 'Payments squad' },
  { id: 'memo', kind: KIND.focus, start: h(10), end: h(11), title: 'Pricing memo, first draft', note: 'Focus' },
  { id: 'review', kind: KIND.task, start: h(11, 15), end: h(11, 45), title: 'Review Ada’s checkout PR', note: '' },
  { id: 'dentist', kind: KIND.task, start: h(12), end: h(12, 30), title: 'Call the dentist', note: '' },
  { id: 'lunch', kind: KIND.meeting, start: h(13), end: h(14), title: 'Lunch with Maya', note: 'Brindisa, Borough Market' },
  { id: 'crit', kind: KIND.meeting, start: h(14, 30), end: h(15, 30), title: 'Design crit', note: 'Room 4B · 8 people' },
  { id: 'plan', kind: KIND.task, start: h(16), end: h(16, 30), title: 'Plan next week', note: '' },
  { id: 'climb', kind: KIND.meeting, start: h(17), end: h(17, 45), title: 'Climbing with Jo', note: 'The Castle, Green Lanes' },
];

export const DAY_TITLE = 'Thursday';
export const DAY_DATE = '16 October';

/** The week around it: weekday, date, and the kinds of the day's plans (a dot each, in the kinds' colours). */
export const WEEK: { day: string; date: number; dots: number[] }[] = [
  { day: 'M', date: 13, dots: [KIND.meeting, KIND.task] },
  { day: 'T', date: 14, dots: [KIND.focus, KIND.meeting, KIND.task] },
  { day: 'W', date: 15, dots: [KIND.meeting] },
  { day: 'T', date: 16, dots: [KIND.focus, KIND.task, KIND.meeting] },
  { day: 'F', date: 17, dots: [KIND.focus, KIND.meeting] },
  { day: 'S', date: 18, dots: [KIND.task] },
  { day: 'S', date: 19, dots: [] },
];
export const TODAY = 3;

/** "09:30", "12:00". */
export function clock(min: number): string {
  'worklet';
  const m = Math.round(min);
  const hh = Math.floor(m / 60);
  const mm = m % 60;
  return `${hh < 10 ? '0' : ''}${hh}:${mm < 10 ? '0' : ''}${mm}`;
}

/** "1 h", "45 min", "1 h 45". */
export function length(min: number): string {
  'worklet';
  const m = Math.round(min);
  const hh = Math.floor(m / 60);
  const mm = m % 60;
  if (hh === 0) return `${mm} min`;
  return mm === 0 ? `${hh} h` : `${hh} h ${mm}`;
}
