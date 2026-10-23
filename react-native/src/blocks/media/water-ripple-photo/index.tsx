import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { WaterRipplePhoto } from './WaterRipplePhoto';

export default function WaterRipplePhotoDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0d0c11' }}>
      <StatusBar hidden />
      <WaterRipplePhoto autoPlay />
    </View>
  );
}
