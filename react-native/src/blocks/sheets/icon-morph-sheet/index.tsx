import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { IconMorphSheet } from './IconMorphSheet';

export default function IconMorphSheetDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden />
    <IconMorphSheet autoPlay />
  </View>;
}
