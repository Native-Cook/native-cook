import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { KineticTypeSplash } from './KineticTypeSplash';

export default function KineticTypeSplashDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0a0a0b' }}>
      <StatusBar hidden />
      <KineticTypeSplash autoPlay />
    </View>
  );
}
