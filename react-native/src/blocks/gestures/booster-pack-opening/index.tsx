import { StatusBar } from 'expo-status-bar';
import { StyleSheet, View } from 'react-native';

import { BoosterPackOpening } from './booster-pack-experience';

// The block has the whole screen, with the status bar hidden: the scene paints
// its own sky, dims it while the pack is torn and lays a pale backdrop behind
// the cards, so no one status bar style suits all of it. The dark page shows
// only until the first frame. The pack opens by itself until it is touched.
//
// What it needs from the app, beyond its packages: a development build (Skia 3
// is not in Expo Go), the gesture-handler root and the safe-area provider
// around it, `wav` among the asset extensions in metro.config.js, and the
// expo-audio and expo-asset plugins in app.json. This repository's
// react-native folder shows each.
export default function BoosterPackOpeningDemo() {
  return (
    <View style={styles.root}>
      <StatusBar hidden />
      <BoosterPackOpening autoPlay />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#07090D' },
});
