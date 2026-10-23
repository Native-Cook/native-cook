export const COLORS = {
  page: '#06080C',
  lime: '#A8F03C',
  text: '#DDE3E8',
  caption: '#A9B0B8'
};
export const MONTHS = ['Mar 2024',
  'Apr 2024',
  'May 2024',
  'Jun 2024',
  'July 2024',
  'Aug 2024',
  'Sept 2024',
  'Oct 2024',
  'Nov 2024',
  'Dec 2024',
  'Jan 2025',
  'Feb 2025',
  'Mar 2025'];
export const FULL_MONTHS = ['March 2024',
  'April 2024',
  'May 2024',
  'June 2024',
  'July 2024',
  'August 2024',
  'September 2024',
  'October 2024',
  'November 2024',
  'December 2024',
  'January 2025',
  'February 2025',
  'March 2025'];
export const VALUES = [3000, 3280, 3640, 4150, 4560, 5000, 5720, 6510, 7280, 8050, 9150, 9640, 10000];
// Geometry is in points; normalized alpha/focus values are dimensionless.
export const MONTH_SPACING = 64;
export const SPACING = MONTH_SPACING;
const CENTER_GRID_PT = 4;
const CENTER_DIVISOR = 8;
const RAIL_WIDTH_FRACTION = 0.318;
const COLUMN_OFFSET_PT = 80;
const KNOB_OFFSET_PT = 34;
const NOTCH_DEPTH_PT = 14;
const NOTCH_HALF_HEIGHT_PT = 44;
const LABEL_FOCUS_RADIUS_PT = 52;
const LABEL_EDGE_FADE_PT = 72;
const LABEL_RAIL_GAP_PT = 36;
const LABEL_NOTCH_PUSH_PT = 16.1;
const LABEL_IDLE_ALPHA = 0.72;
const LABEL_FOCUS_ALPHA = 0.28;
export function metrics(width: number, height: number) {
  const cy = Math.round(height / CENTER_DIVISOR) * CENTER_GRID_PT;
  const railX = Math.round(width * RAIL_WIDTH_FRACTION);
  return {
    width,
    height,
    cy,
    railX,
    x0: railX + COLUMN_OFFSET_PT,
    knobX: railX + KNOB_OFFSET_PT
  };
}
export type Metrics = ReturnType<typeof metrics>;
export function bell(u: number) {
  'worklet';
  return Math.abs(u) < 1 ? 0.5 + 0.5 * Math.cos(Math.PI * u) : 0;
}

export function railAt(y: number, layout: Metrics) {
  'worklet';
  return layout.railX - NOTCH_DEPTH_PT * bell((y - layout.cy) / NOTCH_HALF_HEIGHT_PT);
}

export function labelAt(index: number, p: number, layout: Metrics) {
  'worklet';
  const y = layout.cy + (index - p) * SPACING;
  const focus = bell((y - layout.cy) / LABEL_FOCUS_RADIUS_PT);
  const fade = Math.max(0, Math.min(1, y / LABEL_EDGE_FADE_PT, (layout.height - y) / LABEL_EDGE_FADE_PT));
  return {
    y,
    focus,
    right: layout.railX - LABEL_RAIL_GAP_PT - LABEL_NOTCH_PUSH_PT * focus,
    alpha: (LABEL_IDLE_ALPHA + LABEL_FOCUS_ALPHA * focus) * fade
  };
}
