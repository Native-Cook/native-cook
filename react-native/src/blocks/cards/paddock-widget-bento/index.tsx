import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import PaddockWidgetBento from './PaddockWidgetBento';

/** Full-screen sample with its 9-second loop (pit stop, sticker peel, quick-card arrangement) enabled. */
export default function PaddockWidgetBentoDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar style="light" />
      <PaddockWidgetBento autoPlay />
    </View>
  );
}

export { default as PaddockWidgetBento } from './PaddockWidgetBento';
