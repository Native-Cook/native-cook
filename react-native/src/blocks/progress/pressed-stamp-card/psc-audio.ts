import { createAudioPlayer, type AudioPlayer, setAudioModeAsync } from 'expo-audio';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useRef } from 'react';

export type Cue = 'grab' | 'magnet' | 'scan' | 'impact' | 'rigid' | 'gold' | 'success' | 'cancel';
export function useStampFeedback() {
  const players = useRef<AudioPlayer[]>([]);
  useEffect(() => {
    players.current = [createAudioPlayer(require('./assets/ps-thud.wav')), createAudioPlayer(require('./assets/ps-foil.wav'))];
    void setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => { players.current.forEach(p => p.remove()); players.current = []; };
  }, []);
  return useCallback((cue: Cue) => {
    if (cue === 'gold') { /* Foil friction starts with the wave; completion haptic is later. */ }
    else if (cue === 'magnet') void Haptics.selectionAsync().catch(() => {});
    else if (cue === 'success') void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else void Haptics.impactAsync(cue === 'grab' ? Haptics.ImpactFeedbackStyle.Soft : cue === 'rigid' ? Haptics.ImpactFeedbackStyle.Rigid : cue === 'impact' ? Haptics.ImpactFeedbackStyle.Heavy : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    const p = cue === 'impact' || cue === 'rigid' ? players.current[0] : cue === 'gold' ? players.current[1] : undefined;
    if (p) { void p.seekTo(0).then(() => p.play()).catch(() => {}); }
  }, []);
}
