// Beam Control — the haptics map (no sound: a light makes none). Only touches play them; autoplay is silent.
//
//   finger lands on the capsule ............. soft (onset: glass)
//   brightness crosses a 10 % step ........... selection (at most every 60 ms)
//   the half-angle crosses a 5° step ......... selection (shares the gap: 60 ms, 35 ms above 60°/s)
//   switched on or off (tap, or pulled under 0)  medium (the click)
//   focus released and caught by its spring . rigid (the focus lock, 0.26 s after release, near the overshoot)
//   the pencil first lands on the ceiling .... rigid (reach ≥ 205 pt; re-arms below 190 pt)
//   capsule opens from / closes into the island  soft (reveal)
// `haptics={false}` swaps the whole map for silence.

import * as Haptics from 'expo-haptics';

export const haptic = {
  onset: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
  tick: () => void Haptics.selectionAsync(),
  click: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium),
  focusLock: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
  ceiling: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
  reveal: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
};

const quiet = () => undefined;
export const silence: typeof haptic = { onset: quiet, tick: quiet, click: quiet, focusLock: quiet, ceiling: quiet, reveal: quiet };

export const FOCUS_LOCK_DELAY_MS = 260;
