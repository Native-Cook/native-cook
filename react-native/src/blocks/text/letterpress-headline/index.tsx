import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { LetterpressHeadline } from './LetterpressHeadline';

export default function LetterpressHeadlineDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#efe8dc' }}>
      <StatusBar hidden />
      <LetterpressHeadline autoPlay />
    </View>
  );
}
