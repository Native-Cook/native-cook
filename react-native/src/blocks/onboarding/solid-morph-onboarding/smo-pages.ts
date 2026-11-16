import { comp, COPY, ref, type SolidLayout, type SolidMorphTokens, type SolidSpec } from './smo-theme';

/** Built-in marks, drawn in one 100-unit box and stroked at GLYPH_STROKE with round ends, then engraved into the solid. */
export const GLYPH_STROKE = 6;
export const DEFAULT_GLYPHS = [
  // Infinity: one closed lemniscate, 68 units wide.
  'M50 50 C44 41 37 35 29 35 C21 35 16 42 16 50 C16 58 21 65 29 65 C37 65 44 59 50 50 C56 41 63 35 71 35 C79 35 84 42 84 50 C84 58 79 65 71 65 C63 65 56 59 50 50 Z',
  // Tick, optically centred and wide enough to read on the wedge.
  'M22 51 L41 70 L79 30',
  // Clock: dial ring and two hands at 12 and a little after 4.
  'M80 50 A30 30 0 1 1 20 50 A30 30 0 1 1 80 50 Z M50 50 L50 33 M50 50 L62 57',
] as const;
export const DEFAULT_PAGES = [
  {
    solid: { sides: 0, roundness: 1, base: 'dish' as const },
    glyph: DEFAULT_GLYPHS[0],
    theme: 'dark' as const,
    chip: 'Go global',
    title: 'Your money,\nwherever you go',
    body: 'Hold, send and receive money across currencies,\nall from one account.',
  },
  {
    solid: { sides: 0, roundness: 1, split: 'wedge' as const, base: 'dish' as const },
    glyph: DEFAULT_GLYPHS[1],
    theme: 'light' as const,
    chip: 'Built to protect',
    title: 'Your money,\nprotected',
    body: 'Secure technology keeps your account and every\ntransaction protected around the clock.',
  },
  {
    solid: { sides: 7, roundness: 0.15, base: 'match' as const },
    glyph: DEFAULT_GLYPHS[2],
    theme: 'dark' as const,
    chip: 'Zero delays',
    title: 'Money moves,\nwhen you do',
    body: 'Send, receive and access your money quickly\nwhenever you need it.',
  },
];
export type PageContent = { solid: SolidSpec; theme: 'dark' | 'light'; chip?: string; title: string; body?: string };
export type DrawCommand =
  | { kind: 'rect'; x: number; y: number; w: number; h: number; r: number; color: string; stroke?: number }
  | {
      kind: 'text';
      x: number;
      y: number;
      w: number;
      h: number;
      text: string;
      size: number;
      family: string;
      color: string;
      align?: 'center';
      valign?: 'center';
      tracking?: number;
      lineHeight?: number;
    };
/** One command list for both native Skia and the offline CanvasKit evidence. No independently recreated UI. */
export function pageCommands(
  page: PageContent,
  index: number,
  count: number,
  l: SolidLayout,
  tokens: SolidMorphTokens = {},
  primary = COPY.primary as string,
  secondary: string | null = COPY.secondary,
  footer = true,
): DrawCommand[] {
  const { S, W, fontScale: F } = l;
  const ink = tokens.ink ?? ref.color.ink;
  const paper = tokens.paper ?? ref.color.paper;
  const dark = page.theme === 'dark';
  const fg = dark ? paper : ink;
  const x = comp.margin * S;
  const out: DrawCommand[] = [];
  const text = (c: Omit<Extract<DrawCommand, { kind: 'text' }>, 'kind'>) => out.push({ kind: 'text', ...c });
  const rect = (a: number, b: number, w: number, h: number, r: number, color: string, stroke?: number) =>
    out.push({ kind: 'rect', x: a, y: b, w, h, r, color, stroke });
  const segW = Math.min(comp.progress.w * S, (W - l.skipWidth - x * 2) / count - 4 * S);
  for (let i = 0; i < count; i++) rect(x + i * (segW + 4 * S), l.progressY, segW, 4 * S, 0, i <= index ? fg : dark ? '#515154' : '#D0D0D2');
  text({
    x: W - l.skipWidth,
    y: l.progressY - 8 * S,
    w: l.skipWidth,
    h: 32 * S * F,
    text: COPY.skip,
    size: 16.5 * S * F,
    family: 'SmoRegular',
    color: fg,
  });
  if (page.chip) {
    const chipH = comp.chip.h * S * F;
    const chipW = Math.min(W - 2 * x, Math.round(((page.chip.length * 6.8 + 2 * comp.chip.pad) * S * F) / 4) * 4);
    rect(x, l.chipY, chipW, chipH, ref.radius.chip * S, dark ? '#262627' : '#ECECEE');
    text({
      x: x + comp.chip.pad * S,
      y: l.chipY,
      w: chipW - comp.chip.pad * S,
      h: chipH,
      text: page.chip,
      size: 13.5 * S * F,
      family: 'SmoRegular',
      color: fg,
      valign: 'center',
    });
  }
  text({
    x,
    y: l.titleY,
    w: W - x * 2,
    h: l.titleHeight,
    text: page.title,
    size: (tokens.titleSize ?? l.titleSize / S / F) * S * F,
    family: tokens.font ?? 'SmoSemibold',
    color: fg,
    tracking: -0.6 * S,
    lineHeight: ref.type.display.lineHeight / ref.type.display.size,
  });
  if (page.body)
    text({
      x,
      y: l.bodyY,
      w: W - 2 * x,
      h: l.bodyHeight,
      text: page.body,
      size: l.bodySize,
      family: 'SmoRegular',
      color: dark ? ref.color.bodyDark : ref.color.bodyLight,
      lineHeight: ref.type.body.lineHeight / ref.type.body.size,
    });
  if (!footer) return out;
  const w = secondary === null || l.largeText ? W - 2 * x : (W - 2 * x - comp.button.gap * S) / 2;
  const r = (tokens.radius ?? comp.button.r) * S;
  rect(x, l.footerY, w, l.buttonHeight, r, fg);
  const secondX = l.largeText ? x : x + w + comp.button.gap * S;
  const secondY = l.largeText ? l.footerY + l.buttonHeight + comp.button.gap * S : l.footerY;
  if (secondary !== null) rect(secondX, secondY, w, l.buttonHeight, r, dark ? '#1D1D1F' : '#F2F2F3');
  if (dark && secondary !== null) rect(secondX, secondY, w, l.buttonHeight, r, '#555558', 0.65 * S);
  (secondary === null ? [primary] : [primary, secondary]).forEach((label, i) =>
    text({
      x: i === 0 ? x : secondX,
      y: i === 0 ? l.footerY : secondY,
      w,
      h: l.buttonHeight,
      text: label,
      size: 17 * F * S,
      family: 'SmoRegular',
      color: i === 0 ? (dark ? ink : paper) : fg,
      align: 'center',
      valign: 'center',
    }),
  );
  return out;
}
