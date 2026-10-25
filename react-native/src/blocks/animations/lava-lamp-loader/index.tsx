import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { LavaLampLoader } from './LavaLampLoader';

export default function LavaLampLoaderDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1A0D10' }}>
      <StatusBar hidden />
      <LavaLampLoader autoPlay />
    </View>
  );
}
