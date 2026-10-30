// Great Wave Morning — layout, colour and copy, measured on the pin (an app screen 736 px across).
// Drawn in the pin's pixels: one point of the 402-point screen is 1 / S pixels, and the pin's y = 0
// sits DY points down (the pin has no status bar).

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 736;
export const DY = 40;
export const PIN_W = 736;

export const C = {
  sky: '#133157',
  skyLow: '#1B3D69',
  pill: '#3D6BAE',
  cloud: '#E4D1B0',
  cloudShade: '#CDB48C',
  bands: ['#2A4F82', '#3B67A2', '#24497C', '#4F7FBA', '#2D578F', '#5A8BC6', '#264C85', '#4373B0'],
  river: '#7AA6D6',
  riverLow: '#5A8CC2',
  riverEdge: '#3A6BA0',
  crest: '#EFE9DF',
  crestShade: '#C9C2B6',
  page: '#F2ECDF',
  card: '#EEE5CF',
  cardEdge: '#C9B99B',
  navy: '#0F2347',
  blue: '#2F5E9E',
  tan: '#C39E67',
  green: '#21704A',
  grey: '#6B6457',
  time: '#A08F70',
  line: '#C2AE8E',
  row: '#FAF6EF',
  tabBg: '#F7F0E4',
  tabIcon: '#B49E7C',
  avatar: '#FAF6EE',
  avatarRing: '#C9A56A',
  boat: '#F3EEE6',
  sail: '#FBF8F2',
  sailShade: '#D9D2C6',
};

export const COPY = {
  hello: 'Good morning, Yuna',
  short: 'Today',
  initials: 'YS',
  sprint: ['Sprint 8', '·', '14 tasks due this week'],
  stats: [
    { label: 'Open', value: '38', sub: ['', 'across 6 projects'], color: C.blue, wave: '#4E7FBE' },
    { label: 'Due today', value: '6', sub: ['3', ' high priority'], color: C.tan, wave: '#C9A86E' },
    { label: 'Done this sprint', value: '94', sub: ['', 'of 147 total'], color: C.green, wave: '#5E9A55' },
  ],
  focus: "Today's Focus",
  rows: [
    { title: 'Review new component specs with dev', time: '09:30', status: 'Due now', bar: '#133F7A', pill: '#D8B477', ink: '#0F2347' },
    { title: 'Update sprint board before standup', time: '11:00', status: 'In progress', bar: '#5689C0', pill: '#4C82BF', ink: '#FFFFFF' },
    { title: 'Approve Q2 contractor invoices', time: '13:30', status: 'Pending', bar: '#D9B579', pill: '#E6CFA6', ink: '#0F2347' },
    { title: 'QA walkthrough for Tide app v2.3', time: '15:00', status: 'Not started', bar: '#133F7A', pill: '#D9D7D1', ink: '#3B4256' },
    { title: 'Resolve blocked API auth issue', time: '17:00', status: 'Blocked', bar: '#133F7A', pill: '#102A4F', ink: '#FFFFFF' },
  ],
  done: { status: 'Done', bar: '#21704A', pill: '#CFE3D3', ink: '#1B5A3B' },
  tabs: ['Home', 'Tasks', 'Projects', 'Employees', 'Reports'],
};

export const UI = {
  hello: { x: 42, y: 78 },
  avatar: { x: 663, y: 77, r: 40 },
  pill: { x: 40, y: 113, h: 42 },
  header: { bottom: 508, collapsed: 168 },
  cards: { y: 538, h: 192, x: [33, 261, 488], w: 212, r: 10 },
  dots: { y: 762, x: [331, 361, 391] },
  focus: { y: 814, lineY: 821 },
  rows: { y0: 845, h: 84, pitch: 100, x: 33, w: 678 },
  tab: { y: 1366, h: 106, x: [80, 222, 366, 512, 660], tile: 84 },
  boat: { x: 368, y: 400 },
};

/** How far the content scrolls (px) before the header has fully collapsed. */
export const COLLAPSE = 340;
export const MAX_SCROLL = 430;
