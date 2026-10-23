export const PLAYLIST = 'Low Tide';
export const TRACKS = [
  { title: 'Glass Hours', artist: 'Ivory Kessel', seconds: 222 },
  { title: 'Night Ferry', artist: 'Mara Voss', seconds: 245 },
  { title: 'Slow Voltage', artist: 'Okano & Reed', seconds: 198 },
  { title: 'Paper Moons', artist: 'The Halden Set', seconds: 236 },
  { title: 'Undertow', artist: 'Sable Arc', seconds: 261 },
  { title: 'Kerosene Sky', artist: 'Juno Vale', seconds: 213 },
  { title: 'Soft Machines', artist: 'Calder Lune', seconds: 227 },
  { title: 'Harbor Lights', artist: 'Wren Atlas', seconds: 252 },
  { title: 'After the Rain', artist: 'Nilo Ferreira', seconds: 189 },
] as const;

export function playlistSubtitle(count: number = TRACKS.length): string {
  'worklet';
  const n = Math.max(0, Math.min(TRACKS.length, Math.floor(count)));
  let seconds = 0;
  for (let i = 0; i < n; i++) seconds += TRACKS[i].seconds;
  return `${n} tracks · ${Math.round(seconds / 60)} min`;
}

export function trackDuration(seconds: number): string {
  'worklet';
  const remainder = seconds % 60;
  return `${Math.floor(seconds / 60)}:${remainder < 10 ? '0' : ''}${remainder}`;
}
