import type MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import type { ComponentProps } from 'react';

export type VitalChart =
  /** One bar per hour from its low to its high. */
  | { kind: 'bars'; low: number[]; high: number[] }
  /** One or two lines with a dot at every hour. */
  | { kind: 'lines'; first: number[]; second?: number[] };

export type Vital = {
  key: string;
  name: string;
  icon: ComponentProps<typeof MaterialCommunityIcons>['name'];
  iconColor: string;
  /** The latest reading as shown on the tile, its unit, and when it was taken. */
  value: string;
  unit: string;
  time: string;
  /** The tile's value in red, with a warning mark. */
  warning?: boolean;
  /** The unit written after the peak and the low on the chart. */
  chartUnit: string;
  /** Three axis values, bottom to top, at fixed heights of the chart. */
  ticks: [number, number, number];
  /** Readings above `above` or below `below` are drawn in red. */
  above?: number;
  below?: number;
  /** The bars' colour, or the first and second line's. */
  colors: [string, string];
  chart: VitalChart;
};

const GOLD = '#ffc83d';
const TEAL = '#29d3c0';
const ROSE = '#f2748a';

export const VITALS: Vital[] = [
  {
    key: 'heart',
    name: 'Heart Rate',
    icon: 'heart-pulse',
    iconColor: GOLD,
    value: '146',
    unit: 'BPM',
    time: '9:32 AM',
    warning: true,
    chartUnit: 'BPM',
    ticks: [60, 120, 180],
    above: 140,
    colors: [GOLD, GOLD],
    chart: {
      kind: 'bars',
      low: [68, 62, 64, 72, 72, 82, 64, 72, 78, 66, 62, 70, 86, 52, 64, 74, 82, 60, 70, 66, 74, 92, 98, 86],
      high: [92, 128, 116, 112, 124, 128, 116, 110, 150, 122, 104, 128, 120, 96, 116, 128, 172, 92, 112, 126, 136, 122, 146, 152],
    },
  },
  {
    key: 'spo2',
    name: 'SpO2',
    icon: 'water-percent',
    iconColor: '#3d8bff',
    value: '93',
    unit: '%',
    time: '9:34 AM',
    chartUnit: '%',
    ticks: [88, 94, 100],
    below: 90,
    colors: ['#3d8bff', '#3d8bff'],
    chart: {
      kind: 'lines',
      first: [97, 98, 97, 96, 95, 93, 91, 89, 90, 94, 96, 97, 98, 97, 96, 97, 98, 97, 95, 94, 96, 97, 95, 93],
    },
  },
  {
    key: 'fever',
    name: 'Fever',
    icon: 'thermometer',
    iconColor: '#ff7a45',
    value: '97.8',
    unit: '°F',
    time: '9:40 AM',
    chartUnit: '°F',
    ticks: [97, 99, 101],
    above: 99.5,
    colors: ['#ff9a5c', '#ff9a5c'],
    chart: {
      kind: 'lines',
      first: [97.6, 97.5, 97.4, 97.3, 97.4, 97.6, 97.8, 98, 98.3, 98.6, 98.9, 99.2, 99.6, 99.8, 99.5, 99.1, 98.8, 98.5, 98.3, 98.1, 97.9, 97.8, 97.8, 97.8],
    },
  },
  {
    key: 'pressure',
    name: 'Blood Pressure',
    icon: 'gauge',
    iconColor: TEAL,
    value: '145/95',
    unit: 'mmHg',
    time: '9:46 AM',
    chartUnit: 'mmHg',
    ticks: [60, 110, 160],
    above: 140,
    below: 60,
    colors: [TEAL, ROSE],
    chart: {
      kind: 'lines',
      first: [124, 128, 122, 131, 126, 119, 134, 129, 138, 127, 121, 133, 142, 148, 136, 129, 124, 131, 137, 128, 122, 126, 133, 145],
      second: [84, 86, 79, 88, 83, 76, 62, 58, 55, 66, 78, 85, 89, 92, 86, 80, 74, 81, 87, 83, 78, 80, 85, 95],
    },
  },
  {
    key: 'glucose',
    name: 'Blood Glucose',
    icon: 'water',
    iconColor: '#34c759',
    value: '5.5',
    unit: 'mmol/L',
    time: '9:41 AM',
    chartUnit: 'mmol/L',
    ticks: [4, 7, 10],
    above: 7.8,
    colors: ['#34c759', '#34c759'],
    chart: {
      kind: 'bars',
      low: [4.6, 4.4, 4.3, 4.2, 4.4, 4.8, 5.2, 5, 4.8, 5.4, 5, 4.7, 5.6, 5.2, 4.9, 4.8, 5, 5.8, 5.4, 5, 4.8, 4.6, 4.5, 4.9],
      high: [5.2, 4.9, 4.8, 4.7, 5.1, 6.2, 7.4, 6.8, 5.9, 8.2, 6.6, 5.6, 8.6, 7, 5.8, 5.6, 6.4, 8, 7.2, 6, 5.6, 5.2, 5, 5.5],
    },
  },
  {
    key: 'breath',
    name: 'Respiratory Rate',
    icon: 'lungs',
    iconColor: '#9b6bff',
    value: '18',
    unit: 'br/min',
    time: '9:48 AM',
    chartUnit: 'br/min',
    ticks: [12, 18, 24],
    above: 22,
    colors: ['#9b6bff', '#9b6bff'],
    chart: {
      kind: 'lines',
      first: [15, 14, 14, 15, 14, 15, 16, 17, 18, 17, 16, 18, 19, 20, 23, 22, 19, 18, 17, 17, 16, 16, 15, 18],
    },
  },
];

/**
 * A chart as the UI thread draws it: per hour the two dots it is made of (a bar's ends, or the two
 * lines' points; one line repeats itself), the axis it is drawn on, and its red limits.
 */
export type Plot = {
  bars: boolean;
  lines: number;
  a: number[];
  b: number[];
  min: number;
  max: number;
  above: number;
  below: number;
  colors: [string, string];
  /** Where the peak and the low labels go, by hour, and whether each is in red. */
  peak: number;
  trough: number;
  peakAbove: boolean;
  troughBelow: boolean;
};

// A limit far off the axis, for a vital without one.
const NONE = 1e6;

export function toPlot(vital: Vital): Plot {
  const [t0, , t2] = vital.ticks;
  // The ticks sit a tenth of the way in from the top and the bottom.
  const pad = (t2 - t0) / 8;
  const { chart } = vital;
  const a = chart.kind === 'bars' ? chart.low : chart.first;
  const b = chart.kind === 'bars' ? chart.high : (chart.second ?? chart.first);
  const tops = chart.kind === 'bars' ? chart.high : chart.first;
  const bottoms = chart.kind === 'bars' ? chart.low : (chart.second ?? chart.first);
  const peak = tops.indexOf(Math.max(...tops));
  const trough = bottoms.indexOf(Math.min(...bottoms));
  return {
    bars: chart.kind === 'bars',
    lines: chart.kind === 'bars' ? 0 : chart.second ? 2 : 1,
    a,
    b,
    min: t0 - pad,
    max: t2 + pad,
    above: vital.above ?? NONE,
    below: vital.below ?? -NONE,
    colors: vital.colors,
    peak,
    trough,
    peakAbove: vital.above !== undefined && tops[peak] > vital.above,
    troughBelow: vital.below !== undefined && bottoms[trough] < vital.below,
  };
}

/** The peak's and the low's label text. */
export function extremes(vital: Vital) {
  const plot = toPlot(vital);
  const tops = vital.chart.kind === 'bars' ? vital.chart.high : vital.chart.first;
  const bottoms = vital.chart.kind === 'bars' ? vital.chart.low : (vital.chart.second ?? vital.chart.first);
  return [`${tops[plot.peak]} ${vital.chartUnit}`, `${bottoms[plot.trough]} ${vital.chartUnit}`];
}
