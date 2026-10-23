// The wallet's line marks, on a 24-unit grid. The settings mark is a soft
// hexagon, and the two swap shafts read as separate, opposing directions.
export const AURA_ICON_GRID = 24;

export const AURA_ICONS = {
  settings:
    "M10 3.2 Q12 2.1 14 3.2 L19 6.1 Q21 7.2 21 9.5 V14.5 Q21 16.8 19 17.9 L14 20.8 Q12 21.9 10 " +
    "20.8 L5 17.9 Q3 16.8 3 14.5 V9.5 Q3 7.2 5 6.1 Z M15.4 12 A3.4 3.4 0 1 1 8.6 12 A3.4 3.4 0 " +
    "1 1 15.4 12 Z",
  swap: "M8 17 V3 M3 8 L8 3 L13 8 M16 7 V21 M11 16 L16 21 L21 16",
  receive: "M12 4 V20 M6 14 L12 20 L18 14",
  send: "M12 20 V4 M6 10 L12 4 L18 10",
  info: "M12 11 V17 M12 7 V7.1",
  orbit:
    "M17 12 A5 5 0 1 1 7 12 A5 5 0 1 1 17 12 Z M8 9.1 C3.5 10.8 1.9 13.4 3.2 14.8 C4.9 16.8 11 " +
    "16 16.2 13.3 C20.6 11 22 8.8 20.6 7.6 C19.8 6.9 17.7 7 15.6 7.6",
} as const;
