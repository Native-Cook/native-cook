import { FlipFocusScreen } from './FlipFocusScreen';

/** The catalog's demo: the pin's screen playing its loop; a touch takes over (a session runs 24× fast so it can be watched). */
export function FlipFocusBadge({ autoPlay = true }: { autoPlay?: boolean }) {
  return <FlipFocusScreen autoPlay={autoPlay} timeScale={24} />;
}
