import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { JellySheet } from './JellySheet';

export default function JellySheetDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <JellySheet autoPlay />
    </View>
  );
}
