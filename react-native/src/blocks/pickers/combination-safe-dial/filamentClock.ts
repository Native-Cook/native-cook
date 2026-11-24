import { Platform } from "react-native";

const IOS = Platform.OS === "ios";

/**
 * The UI thread's frame time (ms, what Reanimated's `useFrameCallback` hands over as `frame.timestamp`) of the frame
 * the Filament render callback is drawing, from that callback's own `FrameInfo`.
 *
 * Both run on the same vsync clock. iOS: RNF stamps a frame with its CADisplayLink's `timestamp` in ns, Reanimated with
 * the display link's `targetTimestamp` in ms (one frame later: 1/60 s, or 1/120 s on ProMotion). Android: both pass
 * Choreographer's `frameTimeNanos` (RNF in ns, Reanimated in ms), so there is no frame between them.
 */
export function filamentFrameMs(timestampNs: number, sinceLastFrameS: number): number {
  "worklet";
  if (!IOS) return timestampNs / 1e6;
  const step = sinceLastFrameS > 0.004 && sinceLastFrameS < 0.0125 ? 1000 / 120 : 1000 / 60;
  return timestampNs / 1e6 + step;
}

/** The film's time (s, 0..loop) at a UI frame time `atMs`, for a film that started at `startMs`. */
export function filmTimeAt(atMs: number, startMs: number, loop: number): number {
  "worklet";
  const t = (atMs - startMs) / 1000;
  return t <= 0 ? 0 : t % loop;
}
