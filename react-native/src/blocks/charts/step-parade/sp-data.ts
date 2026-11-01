// Step Parade — the friends, their colours and how their steps add up over the day.

export type Friend = {
  name: string;
  total: number;
  coat: string;
  hat: string;
  skin: string;
  /** activity peaks: [hour, weight, width] */
  peaks: [number, number, number][];
  lane: number;
};

export const H0 = 6; // the scene's day runs 06:00 → 22:00
export const H1 = 22;
export const NOW = 21.5;
export const SAMPLES = 65; // every 15 minutes

export const FRIENDS: Friend[] = [
  {
    name: "Leo",
    total: 11240,
    coat: "#3D6BFF",
    hat: "#FF5A4E",
    skin: "#F2C3A0",
    peaks: [
      [7, 1, 0.6],
      [18, 0.6, 1.2],
    ],
    lane: 0,
  },
  {
    name: "You",
    total: 9560,
    coat: "#FF7A45",
    hat: "#FFD23F",
    skin: "#E8B48E",
    peaks: [
      [8.5, 0.5, 0.8],
      [12.5, 0.7, 0.7],
      [19, 0.8, 1],
    ],
    lane: 1,
  },
  {
    name: "Ana",
    total: 8310,
    coat: "#FFC83D",
    hat: "#3D6BFF",
    skin: "#C98E66",
    peaks: [
      [10, 0.8, 1.5],
      [16, 0.6, 1],
    ],
    lane: 2,
  },
  {
    name: "Kofi",
    total: 7120,
    coat: "#22B573",
    hat: "#1F2A44",
    skin: "#8D5A3B",
    peaks: [[13, 1, 2.5]],
    lane: 3,
  },
  {
    name: "Juno",
    total: 5480,
    coat: "#A764FF",
    hat: "#FF8FC8",
    skin: "#F5D1B5",
    peaks: [
      [17.5, 1, 1],
      [20.5, 0.6, 0.7],
    ],
    lane: 4,
  },
  {
    name: "Sam",
    total: 3940,
    coat: "#2A3142",
    hat: "#FF9F2E",
    skin: "#E3A77F",
    peaks: [
      [9, 0.4, 2],
      [15, 0.6, 2],
    ],
    lane: 5,
  },
];

/** Cumulative steps of each friend at every 15 minutes from H0 to H1 (precomputed on the JS side). */
export const CURVES: number[][] = FRIENDS.map((f) => {
  const rate: number[] = [];
  for (let i = 0; i < SAMPLES; i++) {
    const h = H0 + ((H1 - H0) * i) / (SAMPLES - 1);
    let r = 0.04;
    for (const [c, w, s] of f.peaks)
      r += w * Math.exp(-((h - c) * (h - c)) / (2 * s * s));
    rate.push(r);
  }
  const cum: number[] = [0];
  for (let i = 1; i < SAMPLES; i++)
    cum.push(cum[i - 1] + (rate[i - 1] + rate[i]) / 2);
  const end = cum[SAMPLES - 1];
  return cum.map((v) => (v / end) * f.total);
});

export const WEEK = [
  { d: "M", steps: 8120, win: 0 },
  { d: "T", steps: 10340, win: 1 },
  { d: "W", steps: 6400, win: 3 },
  { d: "T", steps: 12010, win: 0 },
  { d: "F", steps: 9870, win: 2 },
  { d: "S", steps: 4300, win: 4 },
  { d: "S", steps: 9560, win: 0 },
];
