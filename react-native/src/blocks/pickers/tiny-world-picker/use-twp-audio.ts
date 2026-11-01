import {
  type AudioPlayer,
  createAudioPlayer,
  setAudioModeAsync,
} from "expo-audio";
import { useCallback, useEffect, useRef } from "react";

// Three short cues synthesised for this block: the clay reshaping, the new world settling, a jelly
// tap. Only touch plays them (autoplay stays silent), and the ringer switch is respected.
const SOURCES = {
  morph: require("./assets/sounds/twp-morph.wav"),
  commit: require("./assets/sounds/twp-commit.wav"),
  tap: require("./assets/sounds/twp-tap.wav"),
};

export type Cue = keyof typeof SOURCES;
export const CUES: Cue[] = ["morph", "commit", "tap"];

export function useTwpAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of CUES) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === "morph" ? 0.45 : 0.6;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({
      playsInSilentMode: false,
      interruptionMode: "mixWithOthers",
    }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  return useCallback((cue: Cue) => {
    const p = players.current[cue];
    if (!p) return;
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
}
