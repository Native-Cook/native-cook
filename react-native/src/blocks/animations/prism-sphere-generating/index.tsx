import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PrismSphereGenerating } from './PrismSphereGenerating';

export default function PrismSphereGeneratingDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <PrismSphereGenerating autoPlay />
    </View>
  );
}
