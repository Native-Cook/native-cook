// Reeded Glass Reveal — the haptic cue map (DESIGN-SYSTEM section 9). Autoplay is silent; only a real finger or a
// real button press makes a cue. Ticks may come 16 times a second, every other cue keeps 40 ms from the one before.
import * as Haptics from 'expo-haptics';

let enabled = true;
let lastCueAt = -Infinity;
let lastTickAt = -Infinity;

/** The component's `haptics` prop. */
export function setHapticsEnabled(on: boolean) {
  enabled = on;
}

function cue(effect: () => Promise<void>, gap: number, isTick = false): Promise<void> {
  if (!enabled) return Promise.resolve();
  const now = Date.now();
  if (now - (isTick ? lastTickAt : lastCueAt) < gap) return Promise.resolve();
  if (isTick) lastTickAt = now;
  else lastCueAt = now;
  // Haptics may be unavailable on a browser or simulator. A cue must never break the interaction.
  return effect().catch(() => undefined);
}

/** onset: the finger touches the glass and the flutes under it dip. Light. */
export const onset = () => cue(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light), 40);
/** detent: the front crosses a flute while the finger drags it (<= 16 a second). Selection. */
export const ribTick = () => cue(() => Haptics.selectionAsync(), 60, true);
/** the front reaches the last flute at either end. Rigid. */
export const edge = () => cue(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid), 40);
/** success: the glass is flat and the number is shown. The success notification. */
export const revealed = () => cue(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success), 40);
/** the flutes have risen again and the number is hidden. Soft. */
export const masked = () => cue(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft), 40);
/** a button is pressed. Light. */
export const press = () => cue(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light), 40);
