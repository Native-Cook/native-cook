import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { TruchetWaveTiles } from './TruchetWaveTiles';

export default function TruchetWaveTilesDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#101a2e' }}>
      <StatusBar hidden />
      <TruchetWaveTiles autoPlay />
    </View>
  );
}
