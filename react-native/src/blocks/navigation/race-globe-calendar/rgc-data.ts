// Race Globe Calendar: the invented season (assets/rounds.json, shared with the Swift and Kotlin ports) turned into
// the plain numbers the worklets read: each venue's point on the unit sphere and the sun's direction at its local
// start time.

import ROUNDS_JSON from './assets/rounds.json';

type RoundJson = {
  round: number;
  place: string;
  country: string;
  latitude: number;
  longitude: number;
  date: string;
  localStart: string;
  title: string;
  dateLine: string;
  circuitPath: string;
  chip: string;
};

const DEG = Math.PI / 180;

/** World frame: y to the north pole, longitude 0 on +z, 90° E on +x (so east is to the right from outside). */
export const vecOf = (latDeg: number, lonDeg: number) => {
  'worklet';
  const la = latDeg * DEG;
  const lo = lonDeg * DEG;
  return [Math.cos(la) * Math.sin(lo), Math.sin(la), Math.cos(la) * Math.cos(lo)];
};

const dayOfYear = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return Math.round((Date.UTC(y, m - 1, d) - Date.UTC(y, 0, 1)) / 86400000) + 1;
};

const rounds = ROUNDS_JSON as RoundJson[];

export type Round = {
  n: number;
  lat: number;
  lon: number;
  /** local start, hours */
  hour: number;
  /** solar declination on race day (deg) */
  decl: number;
  title: string;
  where: string;
  chip: string;
  /** date line split round the start time so the time can be set in fixed digit cells */
  datePre: string;
  dateSuf: string;
  circuit: string;
  past: boolean;
  night: boolean;
};

export const SELECTED_REST = 14; // R15 Zandvoort, the loop's rest

export const ROUNDS: Round[] = rounds.map((r) => {
  const [hh, mm] = r.localStart.split(':').map(Number);
  const i = r.dateLine.indexOf(r.localStart);
  const doy = dayOfYear(r.date);
  return {
    n: r.round,
    lat: r.latitude,
    lon: r.longitude,
    hour: hh + mm / 60,
    decl: 23.44 * Math.sin((2 * Math.PI * (284 + doy)) / 365),
    title: r.title,
    where: `${r.place}, ${r.country}`,
    chip: r.chip,
    datePre: i >= 0 ? r.dateLine.slice(0, i) : r.dateLine,
    dateSuf: i >= 0 ? r.dateLine.slice(i + r.localStart.length) : '',
    circuit: r.circuitPath,
    past: r.round <= SELECTED_REST + 1,
    night: hh >= 19 || r.dateLine.includes('Night'),
  };
});

export const COUNT = ROUNDS.length;
/** Venue points on the unit sphere. */
export const W = ROUNDS.map((r) => vecOf(r.lat, r.lon));
export const LAT = ROUNDS.map((r) => r.lat);
export const LON = ROUNDS.map((r) => r.lon);
export const HOUR = ROUNDS.map((r) => r.hour);
export const DECL = ROUNDS.map((r) => r.decl);
export const NIGHT = ROUNDS.map((r) => (r.night ? 1 : 0));
export const PAST = ROUNDS.map((r) => (r.past ? 1 : 0));

/**
 * The sun at a venue's local start: the subsolar point sits at the race day's declination and 15° per hour west of
 * the venue for each hour after noon (local solar time), so the venue's own sky is right.
 */
export const sunOf = (i: number, hourOffset: number) => {
  'worklet';
  return vecOf(DECL[i], LON[i] - 15 * (HOUR[i] + hourOffset - 12));
};
export const SUN = ROUNDS.map((_, i) => sunOf(i, 0));
