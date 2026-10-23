import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { InkBleedReveal } from './InkBleedReveal';

export default function InkBleedRevealDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f3efe7' }}>
      <StatusBar hidden />
      <InkBleedReveal autoPlay />
    </View>
  );
}
