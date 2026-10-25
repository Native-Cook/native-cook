import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PaintSmearPhoto } from './PaintSmearPhoto';

export default function PaintSmearPhotoDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1a1613' }}>
      <StatusBar hidden />
      <PaintSmearPhoto autoPlay />
    </View>
  );
}
