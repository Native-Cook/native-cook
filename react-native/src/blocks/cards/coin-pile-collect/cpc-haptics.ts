// Coin Pile Collect — the haptics map (DESIGN-SYSTEM §9). Only touches play them; autoplay is silent.
//
//   thumb down on Collect ........................ medium (the pill gives under the thumb)
//   a coin goes through the mouth (every 3rd) .... light, at most one every 120 ms
//   a coin flicked into the mouth ................ light
//   a contact above 200 pt/s ..................... soft, at most one every 85 ms (the clink)
//   the tile is empty ............................ success (the low thunk)
//   the valve latches open on a tap .............. light

import * as Haptics from 'expo-haptics';

export const haptic = {
  press: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium),
  coin: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  click: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
  latch: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  empty: () => void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
};

/** the cue kinds the frame callback sends to the JS thread */
export const CUE = { press: 1, coin: 2, click: 3, empty: 4, refill: 5, latch: 6, collect: 7 } as const;
