export const SHEET_TONE = {
  page: '#0E0E12', text: '#F4F5F8', muted: '#9A9CA6', yellow: '#FFE23A',
  sheetTop: '#2A2C33', sheetBottom: '#24262C', ink: '#17181D',
} as const;
export const SHEET_FONT = { bold: 'IconMorphInter-Bold', regular: 'IconMorphInter-Regular' } as const;
export const SHEET_COPY = [
  { title: 'Send with zero fees', subtitle: 'Friends or your own bank, always free.', image: 'A zero with gold coins' },
  { title: 'We keep every send safe', subtitle: 'We check the recipient for reported fraud\nbefore your money moves.', image: 'A siren with a shield' },
  { title: 'Say it with an envelope', subtitle: 'Wrap a gift or a thank-you in a note.', image: 'An open envelope with a note' },
  { title: 'Link your bank and go', subtitle: 'Running low? We top up from your account\nthe moment you send.', image: 'A bank passbook with a check' },
] as const;
export const SHEET_LABELS = ['Send $24.00', 'Next', 'Got it'] as const;

/** Bottom anchors stay in physical points; safe areas are never scaled. */
export function sheetLayout(width: number, height: number, insets: { top: number; bottom: number }) {
  const compact = height < 780;
  const buttonTop = height - insets.bottom - (compact ? 16 : 8) - 56;
  const stageHeight = compact ? 208 : 264;
  const stageTop = buttonTop - 32 - stageHeight;
  const headerY = insets.top + (compact ? 20 : 22);
  const titleY = headerY + (compact ? 88 : 142);
  const titleLine = compact ? 28 : 36;
  const titleBottom = titleY + titleLine * (compact ? 1.5 : 2.5);
  const balanceTop = Math.round(((titleBottom + buttonTop) / 2 - 24) / 4) * 4;
  return {
    width, height, compact, headerY, avatarSize: compact ? 36 : 44,
    avatarY: headerY + (compact ? 44 : 66), nameY: headerY + 104,
    titleY, titleSize: compact ? 22 : 28, titleLine,
    balance: { x: 16, y: balanceTop, width: width - 32, height: 48,
      visible: balanceTop >= titleBottom + 16 && balanceTop + 48 <= buttonTop - 16 },
    sheetShort: compact ? 466 : 522, sheetTall: compact ? 486 : 542,
    copyBottom: stageTop - 8, stageTop, stageHeight, centreX: width / 2,
    centreY: stageHeight / 2, unit: compact ? 66 : 84,
    dotsTop: buttonTop - 30, button: { x: 16, y: buttonTop, width: width - 32, height: 56 },
    fog: { top: buttonTop - 40, height: height - buttonTop + 40, fadeStart: 20, fadeHeight: 16 },
  };
}
export type SheetLayout = ReturnType<typeof sheetLayout>;
