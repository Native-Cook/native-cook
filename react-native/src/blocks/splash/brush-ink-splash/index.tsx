import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BrushInkSplash } from './BrushInkSplash';

export default function BrushInkSplashDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f3eee4' }}>
      <StatusBar hidden />
      <BrushInkSplash autoPlay />
    </View>
  );
}
