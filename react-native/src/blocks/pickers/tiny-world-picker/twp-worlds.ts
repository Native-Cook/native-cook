// The four worlds of the picker: copy, status and the colours that follow each one around the page.

export type World = {
  name: string;
  status: string;
  done: boolean;
  cta: string;
  /** halo behind the world and the pill's tint */
  halo: string;
  /** the theme bubble's glyph */
  glyph: "cloud" | "flag" | "burger" | "arch";
  /** best run, tasks, chests */
  stats: [string, string, string];
};

export const WORLDS: World[] = [
  {
    name: "Cloud Nest",
    status: "COMPLETED",
    done: true,
    cta: "Replay world",
    halo: "#2FB9D3",
    glyph: "cloud",
    stats: ["2:41", "24/24", "3/3"],
  },
  {
    name: "Kart Loop",
    status: "COMPLETED",
    done: true,
    cta: "Replay world",
    halo: "#7BD13A",
    glyph: "flag",
    stats: ["1:58", "18/18", "2/2"],
  },
  {
    name: "Bun Summit",
    status: "3 / 5 STARS",
    done: false,
    cta: "Continue",
    halo: "#F29A3D",
    glyph: "burger",
    stats: ["4:12", "11/20", "1/3"],
  },
  {
    name: "Old Arena",
    status: "NEW WORLD",
    done: false,
    cta: "Start world",
    halo: "#E8BE84",
    glyph: "arch",
    stats: ["--:--", "0/16", "0/4"],
  },
];

export const HUD = {
  level: 20,
  energy: "17,963",
  energyFill: 0.72,
  coins: "13,401,935",
  stars: "18,313",
  starsMax: "20,000",
};
