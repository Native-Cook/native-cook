import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PerspectiveRolodex } from './PerspectiveRolodex';

export default function PerspectiveRolodexDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <PerspectiveRolodex autoPlay />
    </View>
  );
}
