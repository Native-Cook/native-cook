// Wave Field Pad: vector glyphs from the asset job (assets/*.svg; 24 × 24 box, 2-unit round strokes). Drawn in
// `currentColor`, so SvgXml's `color` sets the ink.

const glyph = (d: string) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="${d}" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>`;

export const CLOSE_ICON = glyph('M7 7L17 17M17 7L7 17');
export const TAB_ICONS: [string, string, string] = [
  glyph('M3.5 8.5V6.5A3 3 0 0 1 6.5 3.5H8.5M15.5 3.5H17.5A3 3 0 0 1 20.5 6.5V8.5M20.5 15.5V17.5A3 3 0 0 1 17.5 20.5H15.5M8.5 20.5H6.5A3 3 0 0 1 3.5 17.5V15.5M12 9.25A2.75 2.75 0 1 1 12 14.75A2.75 2.75 0 1 1 12 9.25Z'),
  glyph('M4 9.75H7.25L12 5.5V18.5L7.25 14.25H4Z M15.5 9.2A4.1 4.1 0 0 1 15.5 14.8 M18.4 6.6A8 8 0 0 1 18.4 17.4'),
  glyph('M14.6 3.6A8.8 8.8 0 1 0 20.4 11.4 M12 7.8V12.3L15 14.2 M19.3 2.6Q19.6 4.5 21.5 4.8Q19.6 5.1 19.3 7Q19 5.1 17.1 4.8Q19 4.5 19.3 2.6Z'),
];
