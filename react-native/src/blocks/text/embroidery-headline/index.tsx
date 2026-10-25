import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { EmbroideryHeadline } from './EmbroideryHeadline';

export default function EmbroideryHeadlineDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1E2822' }}>
      <StatusBar hidden />
      <EmbroideryHeadline autoPlay />
    </View>
  );
}
