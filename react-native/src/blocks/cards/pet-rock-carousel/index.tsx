import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PetRockCarousel } from './PetRockCarousel';

export default function PetRockCarouselDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#edeae4' }}>
      <StatusBar hidden />
      <PetRockCarousel autoPlay />
    </View>
  );
}
