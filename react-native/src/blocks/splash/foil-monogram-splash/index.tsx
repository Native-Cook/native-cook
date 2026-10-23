import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FoilMonogramSplash } from './FoilMonogramSplash';

export default function FoilMonogramSplashDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#130d0a' }}>
      <StatusBar hidden />
      <FoilMonogramSplash autoPlay />
    </View>
  );
}
