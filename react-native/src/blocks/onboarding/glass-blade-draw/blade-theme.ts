export const BLADE_FONT = { regular: 'VaneInter-Regular', bold: 'VaneInter-Bold' } as const;
export const BLADE_COPY = {
  wordmark: 'vane',
  headline: 'Nine blades. One lights.\nNobody loses.',
  subtitle: 'Every stake comes back. Only the prize moves.',
  cta: 'Draw a blade',
} as const;
export const BLADE_COLOR = {
  page: '#000000',
  ink: '#F6F7F2',
  wordmark: 'rgba(246,247,242,0.72)',
  subtitle: 'rgba(246,247,242,0.62)',
  labelLit: '#0B0C02',
  labelDark: '#E6FF6A',
} as const;
export const BLADE_SPACING = { horizontal: 24, mark: 28, wordmarkGap: 8, headlineGap: 24, subtitleGap: 16 } as const;

export function headerMetrics(width: number, topInset: number) {
  const compact = width < 390;
  return { top: Math.max(topInset, 20) + 12, fontSize: compact ? 28 : 30, lineHeight: compact ? 32 : 34 };
}
