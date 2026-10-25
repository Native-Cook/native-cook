import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { AquariumFeedList } from './AquariumFeedList';

export default function AquariumFeedListDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#083648' }}>
      <StatusBar hidden />
      <AquariumFeedList autoPlay />
    </View>
  );
}
