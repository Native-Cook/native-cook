// Lens Volume Slider: the speaker glyph in parts, so they can animate (24 × 24 view box, filled). The paths are the
// Codex art in assets/speaker-body.svg, speaker-arc-near.svg, speaker-arc-far.svg and mute-slash.svg, drawn white here
// because the shader uses only their coverage and inks them #DFDFDF.

export const GLYPH = {
  body: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="#fff" fill-rule="evenodd" d="M4 8.25 H6.9 L10.55 5.15 Q12 4.05 12 5.85 V18.15 Q12 19.95 10.55 18.85 L6.9 15.75 H4 Q3 15.75 3 14.75 V9.25 Q3 8.25 4 8.25 Z"/></svg>',
  arc1: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="#fff" fill-rule="evenodd" d="M 15.6889 7.3654 A 6.05 6.05 0 0 1 15.6889 16.6346 Q 14.4914 16.5298 14.5961 15.3323 A 4.3500000000000005 4.3500000000000005 0 0 0 14.5961 8.6677 Q 14.4914 7.4702 15.6889 7.3654 Z"/></svg>',
  arc2: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="#fff" fill-rule="evenodd" d="M 17.9386 4.6843 A 9.549999999999999 9.549999999999999 0 0 1 17.9386 19.3157 Q 16.7411 19.2110 16.8459 18.0134 A 7.85 7.85 0 0 0 16.8459 5.9866 Q 16.7411 4.7890 17.9386 4.6843 Z"/></svg>',
  slash: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="#fff" fill-rule="evenodd" d="M4.64 3.36 A.9 .9 0 0 0 3.36 4.64 L19.36 20.64 A.9 .9 0 0 0 20.64 19.36 Z"/></svg>',
  /** both arcs are centred on (11.8, 12) and span ±50° (plus their round ends); they are trimmed by angle round it */
  arcCentre: [11.8, 12] as const,
  arcSpan: 0.98,
  /** the mute slash runs from the upper left to the lower right */
  slashFrom: [3.4, 3.4] as const,
  slashTo: [20.6, 20.6] as const,
};
