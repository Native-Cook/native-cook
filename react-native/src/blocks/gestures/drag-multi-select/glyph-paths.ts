/** Glyph outlines on a 24 pt grid, shared by the screen (react-native-svg) and the offline renders. */
export const GLYPH = {
  back: 'M15 5 L8 12 L15 19',
  check: 'M5.5 12.5 L10 17 L18.5 7.5',
  /** An arrow into a tray: "download". */
  download: 'M12 4.5 V14 M8 10.25 L12 14.25 L16 10.25 M5.5 15.5 V18 Q5.5 19.5 7 19.5 H17 Q18.5 19.5 18.5 18 V15.5',
  /** A plane seen from above, nose to the right (filled). */
  plane:
    'M21 12 C21 11.2 20.3 10.6 19.5 10.6 H14.6 L10 3.5 H8.2 L10.5 10.6 H6 L4.4 8.6 H3 L4 12 L3 15.4 H4.4 L6 13.4 H10.5 L8.2 20.5 H10 L14.6 13.4 H19.5 C20.3 13.4 21 12.8 21 12 Z',
} as const;
