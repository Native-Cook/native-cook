import * as Haptics from "expo-haptics";

// Only changes of amount, the empty Send nudge and a landing speak to the
// hand. These wrappers stay on RN; a worklet schedules them at the crossing.
export function auraTick() {
  void Haptics.selectionAsync().catch(() => {});
}

export function auraNudgeHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function auraLandingHaptic() {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}
