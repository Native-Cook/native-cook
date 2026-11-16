// Woven Speaker Player — the demo screen of Woven Speaker: the shot's page (the dome, the display, the headline) with the loop playing.
// The component is `WovenSpeaker` (WovenSpeaker.tsx); this file is only the screen around it.
import { StatusBar } from 'expo-status-bar';
import { StyleSheet, View } from 'react-native';

import { WovenSpeaker } from './WovenSpeaker';
import { ref } from './wsp-theme';

export function WovenSpeakerPlayer({ autoPlay = false }: { autoPlay?: boolean }) {
  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <WovenSpeaker autoPlay={autoPlay} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: ref.color.page },
});
