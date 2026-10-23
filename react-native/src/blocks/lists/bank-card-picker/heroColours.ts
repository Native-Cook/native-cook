// Colour field behind the hero, read from the pin: per row the colour at the left and right
// edges (left under the plates estimated from the gaps between them), blended across with the
// profile measured along the thumbnail row. Drawn smoothly interpolated.
export const HERO_GRID = { cols: 8, rows: 14 };

export const HERO_FIELD: string[][] = [
  ['#0b1313', '#0b1213', '#0b1212', '#0a1112', '#0a1112', '#0a1011', '#0a1011', '#0a1011'],
  ['#0f1e1d', '#0f1d1c', '#0e1c1c', '#0e1b1b', '#0e1b1a', '#0d1a1a', '#0d1919', '#0d1919'],
  ['#162d2c', '#152c2b', '#152b2a', '#142a29', '#142827', '#132726', '#132625', '#132625'],
  ['#1f4241', '#1e4140', '#1e403f', '#1d3e3d', '#1c3d3c', '#1c3b3a', '#1b3a39', '#1b3a39'],
  ['#245f59', '#245d58', '#255c57', '#255a56', '#255854', '#265653', '#265552', '#265452'],
  ['#1f6b5d', '#226c60', '#256d63', '#296f66', '#2c7069', '#30726b', '#32736d', '#33736e'],
  ['#1e7666', '#24796b', '#2a7d70', '#318176', '#37857b', '#3e8980', '#428c83', '#448d85'],
  ['#1f7d6c', '#288373', '#31897a', '#3b9082', '#45978a', '#4e9e92', '#54a297', '#57a499'],
  ['#207c6b', '#2c8575', '#388f7f', '#46998a', '#53a395', '#60ada0', '#68b4a7', '#6cb7aa'],
  ['#217470', '#30807b', '#3f8d87', '#509b94', '#60a9a0', '#70b6ac', '#7abfb3', '#7fc3b7'],
  ['#256466', '#357473', '#468582', '#589792', '#6aa9a1', '#7bbab0', '#86c5b9', '#8ccbbe'],
  ['#2b6063', '#3a7071', '#4a827f', '#5c948f', '#6da69f', '#7eb8ad', '#89c3b7', '#8ec9bc'],
  ['#2b6063', '#3a7070', '#4a817f', '#5a938f', '#6ba59e', '#7bb6ad', '#85c1b6', '#8ac7bb'],
  ['#326368', '#407275', '#4e8283', '#5e9391', '#6ea4a0', '#7cb4ae', '#86bfb6', '#8bc4bb'],
];
