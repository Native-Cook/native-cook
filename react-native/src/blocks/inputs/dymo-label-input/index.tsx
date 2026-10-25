import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { DymoLabelInput } from './DymoLabelInput';

export default function DymoLabelInputDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#EDE4D7' }}>
      <StatusBar hidden />
      <DymoLabelInput autoPlay />
    </View>
  );
}
