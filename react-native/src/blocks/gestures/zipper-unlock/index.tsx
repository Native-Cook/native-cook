import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ZipperUnlock } from './ZipperUnlock';

export default function ZipperUnlockDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#17243F' }}>
      <StatusBar hidden />
      <ZipperUnlock autoPlay />
    </View>
  );
}
