// Glass Pour Menu — the row glyphs, drawn for this block on a 24 × 24 grid (stroked, round caps and joins).
// Drawn at comp.icon pt with comp.stroke pt lines; matched to the pin's 10 px glyphs at 2.25 s.

export const ICONS = {
  // a tray open at the top with an arrow leaving it
  share:
    'M12 3.5V14.5 M8.5 7L12 3.5L15.5 7 M8.5 10.5H7C5.9 10.5 5 11.4 5 12.5V18.5C5 19.6 5.9 20.5 7 20.5H17C18.1 20.5 19 19.6 19 18.5V12.5C19 11.4 18.1 10.5 17 10.5H15.5',
  // two rounded squares, the copy in front, lower right
  duplicate:
    'M15.5 8.5V6.5C15.5 5.4 14.6 4.5 13.5 4.5H6.5C5.4 4.5 4.5 5.4 4.5 6.5V13.5C4.5 14.6 5.4 15.5 6.5 15.5H8.5 M10.5 8.5H17.5C18.6 8.5 19.5 9.4 19.5 10.5V17.5C19.5 18.6 18.6 19.5 17.5 19.5H10.5C9.4 19.5 8.5 18.6 8.5 17.5V10.5C8.5 9.4 9.4 8.5 10.5 8.5Z',
  // a pencil, tip down-left
  rename: 'M14.5 6.5L17.5 9.5 M5 19L5.8 15.2L15.8 5.2C16.6 4.4 17.9 4.4 18.8 5.2C19.6 6.1 19.6 7.4 18.8 8.2L8.8 18.2L5 19Z',
  // a bin with a lid and two ribs
  delete:
    'M4.5 7H19.5 M9.5 7V5.5C9.5 4.9 9.9 4.5 10.5 4.5H13.5C14.1 4.5 14.5 4.9 14.5 5.5V7 M6.5 7L7.3 18.6C7.4 19.7 8.3 20.5 9.3 20.5H14.7C15.7 20.5 16.6 19.7 16.7 18.6L17.5 7 M10.5 10.5V16.5 M13.5 10.5V16.5',
} as const;

export type IconName = keyof typeof ICONS;
