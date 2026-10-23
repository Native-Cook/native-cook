import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { InkSignaturePad } from './InkSignaturePad';

export default function InkSignaturePadDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#15120e' }}>
      <StatusBar hidden />
      <InkSignaturePad autoPlay />
    </View>
  );
}
