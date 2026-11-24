// Plain date math on day serials (days since 1970-01-01 UTC), usable from worklets once built.

export type CalendarMonth = {
  key: string;
  title: string;
  short: string;
  first: number; // serial of the 1st
  offset: number; // weekday of the 1st, Sunday = 0
  days: number;
};

const NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

export const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

export function serial(year: number, month: number, day: number) {
  return Math.round(Date.UTC(year, month, day) / 86400000);
}

export function makeMonths(year: number, month: number, count: number): CalendarMonth[] {
  const out: CalendarMonth[] = [];
  for (let i = 0; i < count; i += 1) {
    const date = new Date(Date.UTC(year, month + i, 1));
    const y = date.getUTCFullYear();
    const m = date.getUTCMonth();
    out.push({
      key: `${y}-${m}`,
      title: `${NAMES[m]} ${y}`,
      short: NAMES[m].slice(0, 3),
      first: serial(y, m, 1),
      offset: date.getUTCDay(),
      days: new Date(Date.UTC(y, m + 1, 0)).getUTCDate(),
    });
  }
  return out;
}

export function formatDay(value: number) {
  const date = new Date(value * 86400000);
  return `${NAMES[date.getUTCMonth()].slice(0, 3)} ${date.getUTCDate()}`;
}

export function formatRange(start: number, end: number) {
  return `${formatDay(start)} – ${formatDay(end)}`;
}

export function dayOfMonth(value: number, month: CalendarMonth) {
  'worklet';
  return value - month.first + 1;
}
