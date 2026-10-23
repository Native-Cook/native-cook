export const INK = '#F4F4F2';
export const COLORS = ['#FF6B1A', '#EA1179', '#3D6BE0', '#39C2B4', '#F4F4F2', '#F2B24A', '#7A2BE2', '#35D07F'];
export const MATERIALS = [
  { name: 'ember', gloss: 0.22, brushed: 0 }, { name: 'Luma', gloss: 0.34, brushed: 0 },
  { name: 'tide', gloss: 0.28, brushed: 0.6 }, { name: 'orbit', gloss: 0.10, brushed: 0 },
  { name: 'ridge', gloss: 0.16, brushed: 0 }, { name: 'noir', gloss: 0.40, brushed: 0 },
  { name: 'vio', gloss: 0.24, brushed: 0 }, { name: 'mint', gloss: 0.10, brushed: 0 },
];
export const COLOR_RGB = COLORS.map((c, i) => [parseInt(c.slice(1, 3), 16) / 255, parseInt(c.slice(3, 5), 16) / 255, parseInt(c.slice(5, 7), 16) / 255].map(channel => channel * (i === 4 ? 0.55 : 1)));
