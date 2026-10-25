import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SandPinCode } from './SandPinCode';

export default function SandPinCodeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#cdb38c' }}>
      <StatusBar hidden />
      <SandPinCode autoPlay />
    </View>
  );
}
