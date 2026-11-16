/**
 * Pure-TypeScript solar position (NOAA / Meeus low-precision series, accurate to about a minute for sunrise and sunset).
 * No dependencies and no side effects; every function is also a worklet so host apps can call it on the UI thread.
 * Angles in degrees, times in minutes. Longitude is positive east, latitude positive north.
 */
export type SolarDay = {
  /** Local minutes since midnight. */
  sunrise: number;
  noon: number;
  sunset: number;
  /** Elevation of the sun at solar noon, degrees. */
  maxElevation: number;
};

const RAD = Math.PI / 180;
const DEG = 180 / Math.PI;

type Sun = { declination: number; equationOfTime: number };

function sunAt(julianDay: number): Sun {
  'worklet';
  const t = (julianDay - 2451545) / 36525;
  const l0 = (((280.46646 + t * (36000.76983 + t * 0.0003032)) % 360) + 360) % 360;
  const m = 357.52911 + t * (35999.05029 - 0.0001537 * t);
  const e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
  const mr = m * RAD;
  const c =
    Math.sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) + Math.sin(2 * mr) * (0.019993 - 0.000101 * t) + Math.sin(3 * mr) * 0.000289;
  const omega = (125.04 - 1934.136 * t) * RAD;
  const lambda = (l0 + c - 0.00569 - 0.00478 * Math.sin(omega)) * RAD;
  const eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60;
  const eps = (eps0 + 0.00256 * Math.cos(omega)) * RAD;
  const declination = Math.asin(Math.sin(eps) * Math.sin(lambda)) * DEG;
  const y = Math.tan(eps / 2) * Math.tan(eps / 2);
  const l0r = l0 * RAD;
  const equationOfTime =
    4 *
    DEG *
    (y * Math.sin(2 * l0r) -
      2 * e * Math.sin(mr) +
      4 * e * y * Math.sin(mr) * Math.cos(2 * l0r) -
      0.5 * y * y * Math.sin(4 * l0r) -
      1.25 * e * e * Math.sin(2 * mr));
  return { declination, equationOfTime };
}

/** Where the sun is for an instant and a place: elevation above the horizon and azimuth clockwise from north. */
export function solarPosition(date: Date, latitude: number, longitude: number): { elevation: number; azimuth: number } {
  'worklet';
  const ms = date.getTime();
  const sun = sunAt(ms / 86400000 + 2440587.5);
  const utcMinutes = (((ms / 60000) % 1440) + 1440) % 1440;
  const trueSolarTime = (((utcMinutes + sun.equationOfTime + 4 * longitude) % 1440) + 1440) % 1440;
  const hourAngle = trueSolarTime / 4 - 180;
  const lat = latitude * RAD;
  const dec = sun.declination * RAD;
  const ha = hourAngle * RAD;
  const cosZenith = Math.min(1, Math.max(-1, Math.sin(lat) * Math.sin(dec) + Math.cos(lat) * Math.cos(dec) * Math.cos(ha)));
  const zenith = Math.acos(cosZenith);
  const sinZ = Math.sin(zenith);
  let azimuth = 180;
  if (sinZ > 1e-6) {
    const cosAz = Math.min(1, Math.max(-1, (Math.sin(lat) * cosZenith - Math.sin(dec)) / (Math.cos(lat) * sinZ)));
    const a = Math.acos(cosAz) * DEG;
    azimuth = hourAngle > 0 ? (a + 180) % 360 : (540 - a) % 360;
  }
  return { elevation: 90 - zenith * DEG, azimuth };
}

function sunOn(day0: number, minutesUtc: number): Sun {
  'worklet';
  return sunAt((day0 + minutesUtc * 60000) / 86400000 + 2440587.5);
}
/** Hour angle of the sunrise in degrees, or -1 where the sun does not cross the horizon (polar day or night). */
function sunriseHourAngle(latitude: number, sun: Sun) {
  'worklet';
  const lat = latitude * RAD;
  const dec = sun.declination * RAD;
  const argument = Math.cos(90.833 * RAD) / (Math.cos(lat) * Math.cos(dec)) - Math.tan(lat) * Math.tan(dec);
  return argument < -1 || argument > 1 ? -1 : Math.acos(argument) * DEG;
}

/**
 * Sunrise, solar noon, sunset and the noon elevation for the calendar day of `date` at a place, in minutes of that place's local day.
 * `utcOffsetMinutes` is the place's offset from UTC on that day (daylight saving included); it defaults to the device's.
 * Returns null where the sun does not rise or set that day (polar day or night): pass `solar` yourself there.
 */
export function solarDay(date: Date, latitude: number, longitude: number, utcOffsetMinutes?: number): SolarDay | null {
  'worklet';
  const offset = utcOffsetMinutes ?? -date.getTimezoneOffset();
  const local = new Date(date.getTime() + offset * 60000);
  // UTC midnight of the place's calendar date; the sun's events for that date fall within a day of it.
  const day0 = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate());
  const noonSun = sunOn(day0, 720 - 4 * longitude);
  const noon = 720 - 4 * longitude - noonSun.equationOfTime;
  const h0 = sunriseHourAngle(latitude, noonSun);
  if (h0 < 0) return null;
  // Declination and the equation of time drift a little over six hours: evaluate each event at its own estimate.
  const riseSun = sunOn(day0, noon - 4 * h0);
  const setSun = sunOn(day0, noon + 4 * h0);
  const hRise = sunriseHourAngle(latitude, riseSun);
  const hSet = sunriseHourAngle(latitude, setSun);
  if (hRise < 0 || hSet < 0) return null;
  const sunriseUtc = 720 - 4 * longitude - riseSun.equationOfTime - 4 * hRise;
  const sunsetUtc = 720 - 4 * longitude - setSun.equationOfTime + 4 * hSet;
  const noonLocal = (((noon + offset) % 1440) + 1440) % 1440;
  return {
    sunrise: Math.max(1, noonLocal - (noon - sunriseUtc)),
    noon: noonLocal,
    sunset: Math.min(1439, noonLocal + (sunsetUtc - noon)),
    maxElevation: Math.min(89, Math.max(1, 90 - Math.abs(latitude - noonSun.declination))),
  };
}
