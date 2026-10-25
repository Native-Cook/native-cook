import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SlideProjectorGallery } from './SlideProjectorGallery';

export default function SlideProjectorGalleryDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1A1411' }}>
      <StatusBar hidden />
      <SlideProjectorGallery autoPlay />
    </View>
  );
}
