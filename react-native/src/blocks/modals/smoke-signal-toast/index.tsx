import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SmokeSignalToast } from './SmokeSignalToast';

export default function SmokeSignalToastDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0B0E1A' }}>
      <StatusBar hidden />
      <SmokeSignalToast autoPlay />
    </View>
  );
}
