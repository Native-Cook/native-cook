import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { VelocityMarquee } from './VelocityMarquee';

export default function VelocityMarqueeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f2efe8' }}>
      <StatusBar hidden />
      <VelocityMarquee autoPlay />
    </View>
  );
}
