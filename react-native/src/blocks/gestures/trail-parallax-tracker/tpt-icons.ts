// Trail Parallax Tracker: line glyphs on a 24 grid, 2 pt strokes (Pro bar).

const svg = (body: string, color: string) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;

/** A six-lobed flower gear: r(θ) = 7 + 1.6 cos 6θ round a 2.6 pt hub. */
const flower = (() => {
  const pts: string[] = [];
  for (let i = 0; i <= 96; i++) {
    const a = (i / 96) * Math.PI * 2;
    const r = 7 + 1.6 * Math.cos(6 * a);
    pts.push(`${(12 + r * Math.cos(a)).toFixed(2)} ${(12 + r * Math.sin(a)).toFixed(2)}`);
  }
  return `M${pts.join(' L')} Z`;
})();

export const GEAR = svg(`<path d="${flower}"/><circle cx="12" cy="12" r="2.6"/>`, '#FFFFFF');

export const TAB_TRAIL = (c: string) => svg('<path d="M5 6.5l5.5 5.5L5 17.5"/><path d="M12.5 6.5l5.5 5.5-5.5 5.5"/>', c);
export const TAB_MAP = (c: string) => svg('<path d="M3.5 6.5L9 4l6 2.5L20.5 4v13.5L15 20l-6-2.5-5.5 2.5z"/><path d="M9 4v13.5"/><path d="M15 6.5V20"/>', c);
export const TAB_BADGE = (c: string) => svg('<path d="M12 3l7.8 4.5v9L12 21l-7.8-4.5v-9z"/><path d="M8.6 12.6L12 15l3.4-2.4"/>', c);

export const PIN = svg('<path d="M12 21s-6.5-6.1-6.5-11a6.5 6.5 0 0 1 13 0c0 4.9-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>', '#FFFFFF');
