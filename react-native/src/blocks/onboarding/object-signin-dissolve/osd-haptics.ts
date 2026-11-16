// Object Sign-In Dissolve — the haptics map. Only touches play them; autoplay is silent. The scan texture replaces a tick
// per 45 deg of yaw (two tick systems feel like noise); the theme toggle is a toggle, not an impact.
//
//   finger lands on the pear ............. light (onset)
//   the plane travels 25 pt (5 scan lines) selection, at most 10 a second
//   the skin closes at the stalk ......... rigid
//   spin dissolve crosses 0.5 (either way) soft
//   theme toggle ........................ light
//   sign-in button ...................... success is the app's event, not ours: light impact only
import * as Haptics from 'expo-haptics';

export const haptic = {
  onset: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  tick: () => void Haptics.selectionAsync(),
  close: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
  spin: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
  toggle: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  press: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
};
