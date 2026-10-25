import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { DarkroomTrayDevelop } from './DarkroomTrayDevelop';

export default function DarkroomTrayDevelopDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0E0606' }}>
      <StatusBar hidden />
      <DarkroomTrayDevelop autoPlay />
    </View>
  );
}
