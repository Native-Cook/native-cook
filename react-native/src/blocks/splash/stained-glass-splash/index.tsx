import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { StainedGlassSplash } from './StainedGlassSplash';

export default function StainedGlassSplashDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#15110E' }}>
      <StatusBar hidden />
      <StainedGlassSplash autoPlay />
    </View>
  );
}
