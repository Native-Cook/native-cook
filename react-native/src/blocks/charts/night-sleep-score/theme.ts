import type { NightSleepScoreStage } from './motion';

/** Day colours are the pin's (measured on ref-07); night colours are ours, for text and marks on the night sky. */
export const NIGHT_SLEEP_SCORE_COLORS = Object.freeze({
  day: {
    ink: '#101113', secondary: '#8D8D92', divider: '#E4E3EB', border: 'rgba(255,255,255,0.75)', accent: '#128CF0',
    highlight: 'rgba(18,140,240,1)',
    stages: { awake: '#EF806B', rem: '#32B4D5', core: '#128CF0', deep: '#4934A2' } as Record<NightSleepScoreStage, string>,
  },
  night: {
    ink: '#F4F5FF', secondary: 'rgba(214,220,255,0.62)', divider: 'rgba(255,255,255,0.13)', border: 'rgba(255,255,255,0.16)', accent: '#7CC0FF',
    highlight: 'rgba(186,210,255,1)',
    // Brighter stage colours that read on the sky; the aurora and the night ring are drawn in these.
    stages: { awake: '#FF8F7A', rem: '#5CE2F6', core: '#3F9DFF', deep: '#A58CFF' } as Record<NightSleepScoreStage, string>,
    trackRing: 'rgba(255,255,255,0.09)', playhead: '#FFFFFF',
  },
});

/** Geometry of the sleep card in design points (402 pt wide screen). Everything else scales with the screen width. */
export const NIGHT_SLEEP_SCORE_CARD = Object.freeze({
  width: 370, height: 317, radius: 28,
  // Hypnogram, measured on ref-07: bars 14 pt (radius 4), dotted guides between the levels, pale connectors.
  chart: { left: 207.8, top: 152, width: 145.5, height: 82, touchInset: 12, barHeight: 14, barRadius: 4, barGap: .7,
    levels: { awake: 7.7, rem: 28.8, core: 52.5, deep: 73.4 } as Record<NightSleepScoreStage, number>,
    grid: [18.7, 40.3, 63], gridColor: '#D3D6E2', gridDash: '1.2 2.4', connectorColor: '#A9CCF3', connectorOpacity: .62, connectorWidth: 3.2, coreTrack: '#D2E4F8' },
  ring: { cx: 304.8, cy: 66, radius: 39, stroke: 16 }, // measured on ref-07: outer Ø 94, centre (320.7, 361.2) on screen
  // Card heights (0 top, 1 bottom) where each layer meets the night front.
  at: { label: 28 / 317, title: 47 / 317, duration: 80 / 317, durationValue: 100 / 317, ring: 66 / 317, divider1: 134 / 317,
    legend: [163 / 317, 185 / 317, 207 / 317, 229 / 317], chart: 193 / 317, divider2: 247 / 317, readout: 276 / 317, readoutSub: 298 / 317 },
  // A shooting star crosses the gap between the title and the ring.
  meteor: { fromX: 252, fromY: 16, toX: 160, toY: 64, tail: 44 },
});

/** Aurora drawing values (design points). */
export const NIGHT_SLEEP_SCORE_AURORA = Object.freeze({
  transitionHalfWidth: 1.4, // max half-width of a stage change on the ribbon; narrow segments use 45 % of their width
  core: 2.2, hotCore: .8, hotCoreOpacity: .55,
  glow: { width: 6, blur: 3.5, opacity: .75 },
  curtain: [{ width: 14, lift: 8, blur: 6, opacity: .55 }, { width: 28, lift: 19, blur: 11, opacity: .3 }],
  head: { glow: 7, glowBlur: 5, core: 2.4 },
  playheadWidth: 1,
});

/** Header rail, measured on ref-07 (pt; x from the screen's left, y from the tile top). */
export const NIGHT_SLEEP_SCORE_RAIL = Object.freeze({
  top: .7, height: 57.5, radius: 20, icon: 30.7, iconCentre: 28.3, textLeft: 54, titleTop: 10.3, valueTop: 29.3, textSize: 15, valueSize: 14.5, textLine: 18,
  heart: { left: 16.7, width: 61.6, heartSize: 17.5, heartCentre: 22.3, lineLeft: 20, lines: [{ top: 33.8, width: 21.7 }, { top: 40.5, width: 16 }], lineHeight: 1.7 },
  chips: { sleep: { left: 82.7, width: 109 }, readiness: { left: 195, width: 140.3 }, fitness: { left: 340.7, width: 112 } },
  colors: { tile: '#FFFFFF', chip: '#EFEEF3', chipBorder: '#CBCAD1', chipRim: 'rgba(255,255,255,0.85)', line: '#CFCFD5', title: '#8D8D92', value: '#101113',
    heart: '#EE3D83', track: '#DEDDE4', gaugeLight: '#B9D6EE', gaugeBlue: '#4A9BE0', fitnessRed: '#EE2D6E', fitnessPink: '#F7B3CB', fitnessGreen: '#55D45A', fitnessCyan: '#52C7EE' },
});

/** Floating tab bar, measured on ref-07: 290 × 60 pt, its bottom 22 pt above the screen's (same as the family's pulse-heart-card). */
export const NIGHT_SLEEP_SCORE_TAB = Object.freeze({
  width: 290, height: 60, radius: 30, bottom: 22, padding: 4, item: 94, itemHeight: 52, icon: 26.5, iconCentre: 20.5, labelSize: 10.2, labelLine: 13, labelTop: 35.5,
  // iOS 26 scroll-edge effect seen on the pin: content blurs progressively into the bar.
  edgeBlur: { height: 118, solidFrom: .36, intensity: 34 },
  // Selection lens: glass tinted to the pin's #E2E2E9 pill, a white rim and a sheen over its top half catching the light.
  lens: { glassTint: 'rgba(214,214,224,0.55)', blur: 24, fallback: 'rgba(226,226,233,0.72)', sheen: 'rgba(255,255,255,0.55)', sheenTo: .5, rim: 'rgba(255,255,255,0.9)', rimWidth: 1 },
  colors: { outline: 'rgba(60,60,90,0.12)', glassTint: 'rgba(222,228,250,0.6)', fallback: 'rgba(248,248,252,0.72)', border: 'rgba(255,255,255,0.9)', active: '#167FE8', ink: '#101113', card: '#FFFFFF', shadow: 'rgba(0,0,0,0.12)' },
});
