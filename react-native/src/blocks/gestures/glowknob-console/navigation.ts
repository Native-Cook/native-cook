// Glowknob is a single screen, so the console page is always focused.
// Background/foreground is still handled through AppState in the console.
export function useIsFocused() {
  return true;
}
