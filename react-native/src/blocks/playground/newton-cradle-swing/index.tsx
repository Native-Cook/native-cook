import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { NewtonCradleSwing } from './NewtonCradleSwing';

export default function NewtonCradleSwingDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#120e0b' }}>
      <StatusBar hidden />
      <NewtonCradleSwing autoPlay />
    </View>
  );
}
