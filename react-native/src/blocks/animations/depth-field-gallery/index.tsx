import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { DepthFieldGallery } from './DepthFieldGallery';

export default function DepthFieldGalleryDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#ffffff' }}>
      <StatusBar hidden />
      <DepthFieldGallery autoPlay />
    </View>
  );
}
