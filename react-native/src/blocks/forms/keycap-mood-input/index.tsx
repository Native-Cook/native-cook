import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { KeycapMoodInput } from './KeycapMoodInput';

export default function KeycapMoodInputDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000' }}>
    <StatusBar hidden />
    <KeycapMoodInput autoPlay />
  </View>;
}
