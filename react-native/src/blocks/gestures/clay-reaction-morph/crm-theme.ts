// Clay Reaction Morph — the chat's colours, type, messages and the sizes of the reaction.

export const C = {
  page: '#0B0B10',
  head: '#F2F2F7',
  mute: '#8D8D99',
  inBubble: '#26262E',
  inInk: '#F2F2F7',
  outBubble: '#5B5BD6',
  outInk: '#FFFFFF',
  field: '#18181F',
  fieldLine: '#2A2A33',
  avatar: '#E8A23A',
  avatarInk: '#2A1A05',
  tray: 'rgba(32,32,40,0.92)',
  trayLine: 'rgba(255,255,255,0.08)',
} as const;

/** The page's luminance in the shader's seat ring (sRGB 0…1 of C.page). */
export const PAGE_LUMA = 0.043;

export const FONT = {
  regular: 'CrmInter-Regular',
  medium: 'CrmInter-Medium',
  semibold: 'CrmInter-SemiBold',
} as const;

export type Message = { from: 'in' | 'out'; text: string };

export const MESSAGES: Message[] = [
  { from: 'in', text: 'Morning! Still on for the coast?' },
  { from: 'out', text: 'Obviously. Bringing the camera' },
  { from: 'in', text: 'Ferry or the early train on Saturday?' },
  { from: 'out', text: 'Ferry. Sunrise over the bay' },
  { from: 'in', text: 'Then I booked us two window seats for 6:40' },
  { from: 'out', text: 'Best news all week' },
];
/** The message that carries the reaction. */
export const TARGET = 4;

export const BUBBLE_R = 20;
export const SEAT_R = 17; // the reaction's seat on the bubble
export const SEAT_S = 11; // the clay reaction's size when it sits there
export const STEP = 72; // a reaction per 72 pt of finger travel, the tray's item width
export const TRAY_H = 44;
export const LONG_PRESS_MS = 280;

/** Where the lifted reaction stands and how big it is, above the held bubble. */
export function heroOf(w: number, bubbleTop: number, top: number) {
  const size = Math.min(132, w * 0.33);
  const trayTop = bubbleTop - 16 - TRAY_H;
  const y = Math.max(top + 24 + size, trayTop - 28 - size);
  return { x: w / 2, y, size, trayTop };
}
