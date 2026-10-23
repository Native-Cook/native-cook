import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { IslandToastStack } from './IslandToastStack';

export default function IslandToastStackDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#07080e' }}>
      {/* On Android the status bar stays, and the island sits under it. */}
      <StatusBar hidden={process.env.EXPO_OS !== 'android'} style="light" />
      <IslandToastStack autoPlay />
    </View>
  );
}
