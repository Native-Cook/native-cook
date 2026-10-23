import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { TangramSnap } from './TangramSnap';

export default function TangramSnapDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0c0a08' }}>
      <StatusBar hidden />
      <TangramSnap autoPlay />
    </View>
  );
}
